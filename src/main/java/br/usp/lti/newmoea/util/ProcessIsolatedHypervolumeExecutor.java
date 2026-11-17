package br.usp.lti.newmoea.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Supervisor de cálculo de hipervolume com isolamento estrito de processo JVM worker,
 * deadline de parede (wall-clock) configurável, cancelamento forçado real via
 * {@link Process#destroyForcibly()} e fallback determinístico para Monte Carlo.
 *
 * <p>Resolve a vulnerabilidade crítica de algoritmos de HV combinatórios (PISA e WFG)
 * no jMetal, onde {@link Thread#interrupt()} ou {@code Future.cancel(true)} são ignorados
 * por laços recursivos não-cooperativos, deixando threads zumbis consumindo 100% de CPU.</p>
 */
public final class ProcessIsolatedHypervolumeExecutor {

    public static final long DEFAULT_TIMEOUT_MS = 60_000L; // 60 segundos por padrão
    public static final long DEFAULT_GRACE_PERIOD_MS = 1_000L; // 1 segundo para destroy() antes de destroyForcibly()
    public static final long DEFAULT_MC_SAMPLES = MonteCarloHypervolume.DEFAULT_SAMPLE_COUNT;

    private final long timeoutMs;
    private final long gracePeriodMs;
    private final long mcSampleCount;
    private final String javaExecutable;
    private final String classPath;

    public ProcessIsolatedHypervolumeExecutor() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_GRACE_PERIOD_MS, DEFAULT_MC_SAMPLES);
    }

    public ProcessIsolatedHypervolumeExecutor(long timeoutMs, long gracePeriodMs, long mcSampleCount) {
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        this.gracePeriodMs = gracePeriodMs > 0 ? gracePeriodMs : DEFAULT_GRACE_PERIOD_MS;
        this.mcSampleCount = mcSampleCount > 0 ? mcSampleCount : DEFAULT_MC_SAMPLES;

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + (isWindows() ? "java.exe" : "java");
        this.javaExecutable = new File(javaBin).exists() ? javaBin : "java";
        this.classPath = System.getProperty("java.class.path");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Executa o cálculo de hipervolume com isolamento de processo e fallback para Monte Carlo.
     *
     * @param front matriz de pontos da frente (N x m)
     * @param lowerBound vetor de limites inferiores l (tamanho m, pode ser null para zeros)
     * @param referencePoint vetor de referência r (tamanho m)
     * @param algorithm algoritmo exato desejado ("PISA" ou "WFG")
     * @param mcSeed semente determinística para fallback de Monte Carlo
     * @return resultado estruturado com garantias estatísticas e diagnóstico
     */
    public HypervolumeResult compute(
            double[][] front,
            double[] lowerBound,
            double[] referencePoint,
            String algorithm,
            long mcSeed) {

        Objects.requireNonNull(referencePoint, "referencePoint cannot be null");
        int m = referencePoint.length;
        if (m == 0) {
            throw new IllegalArgumentException("referencePoint cannot be empty");
        }

        if (front == null || front.length == 0) {
            return HypervolumeResult.emptyFront(referencePoint);
        }

        double[] lower = lowerBound != null ? lowerBound.clone() : new double[m];

        Path tempDir = null;
        File frontFile = null;
        File outputFile = null;
        Process process = null;
        Thread stdoutDrainer = null;
        Thread stderrDrainer = null;

        long startWall = System.currentTimeMillis();

        try {
            tempDir = Files.createTempDirectory("hype_hv_worker_");
            frontFile = tempDir.resolve("front.csv").toFile();
            outputFile = tempDir.resolve("output.properties").toFile();

            // Gravação da frente em arquivo temporário
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(frontFile))) {
                for (double[] point : front) {
                    if (point == null || point.length != m) {
                        continue;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int d = 0; d < m; d++) {
                        if (d > 0) sb.append(",");
                        sb.append(Double.toString(point[d]));
                    }
                    writer.write(sb.toString());
                    writer.newLine();
                }
            }

            StringBuilder refSb = new StringBuilder();
            for (int d = 0; d < m; d++) {
                if (d > 0) refSb.append(",");
                refSb.append(Double.toString(referencePoint[d]));
            }

            ProcessBuilder pb = new ProcessBuilder(
                    javaExecutable,
                    "-cp",
                    classPath,
                    ExactHypervolumeWorker.class.getName(),
                    "--front-file", frontFile.getAbsolutePath(),
                    "--ref-point", refSb.toString(),
                    "--algorithm", algorithm != null ? algorithm : "PISA",
                    "--output-file", outputFile.getAbsolutePath()
            );

            process = pb.start();

            // Drenagem assíncrona de stdout e stderr para prevenir bloqueio de pipe do SO
            stdoutDrainer = drainStream(process.getInputStream());
            stderrDrainer = drainStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                // Timeout estourado! Processo não cooperativo encerrou o deadline
                long timeoutWall = System.currentTimeMillis() - startWall;

                // Tentativa de encerramento suave
                process.destroy();
                boolean softKilled = process.waitFor(gracePeriodMs, TimeUnit.MILLISECONDS);

                if (!softKilled || process.isAlive()) {
                    // Força término incondicional via kernel do SO
                    process.destroyForcibly();
                    process.waitFor(2_000, TimeUnit.MILLISECONDS);
                }

                // Fallback validado para Monte Carlo com Wilson CI
                HypervolumeResult mcResult = MonteCarloHypervolume.compute(front, lower, referencePoint, mcSampleCount, mcSeed);

                return new HypervolumeResult(
                        mcResult.getValue(),
                        mcResult.getStandardError(),
                        mcResult.getCiLower(),
                        mcResult.getCiUpper(),
                        mcResult.getConfidenceLevel(),
                        HypervolumeResult.MethodType.MONTE_CARLO_WILSON,
                        HypervolumeResult.ExecutionStatus.TIMEOUT_FALLBACK_MC,
                        timeoutWall,
                        timeoutWall,
                        mcResult.getSampleCount(),
                        mcResult.getHitCount(),
                        mcSeed,
                        lower,
                        referencePoint,
                        "Exact " + algorithm + " timed out after " + timeoutWall + " ms (deadline=" + timeoutMs + " ms). " +
                                "Worker killed successfully (isAlive=" + process.isAlive() + "). Fallback to Monte Carlo Wilson."
                );
            }

            // Processo terminou dentro do prazo
            int exitCode = process.exitValue();
            if (exitCode == 0 && outputFile.exists()) {
                Map<String, String> props = readProperties(outputFile);
                String status = props.getOrDefault("status", "UNKNOWN");

                if ("SUCCESS".equalsIgnoreCase(status)) {
                    double val = Double.parseDouble(props.get("value"));
                    long wallMs = Long.parseLong(props.getOrDefault("wallTimeMs", "0"));
                    long cpuMs = Long.parseLong(props.getOrDefault("cpuTimeMs", "0"));
                    HypervolumeResult.MethodType methodType = "WFG".equalsIgnoreCase(algorithm) ?
                            HypervolumeResult.MethodType.EXACT_WFG : HypervolumeResult.MethodType.EXACT_PISA;

                    return HypervolumeResult.exactSuccess(val, methodType, wallMs, cpuMs, referencePoint);
                }
            }

            // Se exitCode != 0 ou status não foi sucesso, aciona fallback
            long elapsed = System.currentTimeMillis() - startWall;
            HypervolumeResult mcResult = MonteCarloHypervolume.compute(front, lower, referencePoint, mcSampleCount, mcSeed);

            return new HypervolumeResult(
                    mcResult.getValue(),
                    mcResult.getStandardError(),
                    mcResult.getCiLower(),
                    mcResult.getCiUpper(),
                    mcResult.getConfidenceLevel(),
                    HypervolumeResult.MethodType.MONTE_CARLO_WILSON,
                    HypervolumeResult.ExecutionStatus.CRASH_FALLBACK_MC,
                    elapsed,
                    elapsed,
                    mcResult.getSampleCount(),
                    mcResult.getHitCount(),
                    mcSeed,
                    lower,
                    referencePoint,
                    "Exact worker failed with exit code " + exitCode + ". Fallback to Monte Carlo Wilson."
            );

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startWall;
            HypervolumeResult mcResult = MonteCarloHypervolume.compute(front, lower, referencePoint, mcSampleCount, mcSeed);

            return new HypervolumeResult(
                    mcResult.getValue(),
                    mcResult.getStandardError(),
                    mcResult.getCiLower(),
                    mcResult.getCiUpper(),
                    mcResult.getConfidenceLevel(),
                    HypervolumeResult.MethodType.MONTE_CARLO_WILSON,
                    HypervolumeResult.ExecutionStatus.CRASH_FALLBACK_MC,
                    elapsed,
                    elapsed,
                    mcResult.getSampleCount(),
                    mcResult.getHitCount(),
                    mcSeed,
                    lower,
                    referencePoint,
                    "Exception in isolated execution: " + e.getMessage() + ". Fallback to Monte Carlo Wilson."
            );

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (stdoutDrainer != null) {
                stdoutDrainer.interrupt();
            }
            if (stderrDrainer != null) {
                stderrDrainer.interrupt();
            }
            // Limpeza de arquivos temporários
            if (frontFile != null && frontFile.exists()) {
                frontFile.delete();
            }
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Thread drainStream(InputStream in) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                while (r.readLine() != null) {
                    // Drena e descarta
                }
            } catch (Exception ignored) {
            }
        }, "StreamDrainer");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static Map<String, String> readProperties(File file) {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (eq > 0) {
                    map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}


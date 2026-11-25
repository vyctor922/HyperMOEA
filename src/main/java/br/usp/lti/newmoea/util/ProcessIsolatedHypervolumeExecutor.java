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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supervisor de cálculo de hipervolume com isolamento estrito de processo JVM
 * worker,
 * deadline de parede (wall-clock) configurável, cancelamento forçado recursivo
 * de árvore de
 * processos ({@link ProcessHandle#destroyForcibly()}) e suporte tanto ao modo
 * autônomo com fallback
 * Monte Carlo quanto ao modo estritamente exato ({@link #computeExactOnly})
 * para orquestração de
 * múltiplos hipervolumes pesados sem recomputação redundante (T20 / T35).
 */
public final class ProcessIsolatedHypervolumeExecutor {

    public static final long DEFAULT_TIMEOUT_MS = 60_000L; // 60 segundos por padrão na API isolada
    public static final long DEFAULT_GRACE_PERIOD_MS = 1_000L; // 1 segundo para destroy() antes de destroyForcibly()
    public static final long DEFAULT_MC_SAMPLES = MonteCarloHypervolume.DEFAULT_SAMPLE_COUNT;
    public static final String WORKER_JVM_ARGS_PROPERTY = "study.workerJvmArgs";

    private static final long HARD_MIN_WORKER_XMX_BYTES = 64L * 1024L * 1024L;
    private static final long MIN_RECOMMENDED_WORKER_XMX_BYTES = 384L * 1024L * 1024L;
    private static final Pattern HEAP_SIZE_FLAG_PATTERN = Pattern.compile("^-Xm([sx])(\\d+)([kKmMgG]?)$");
    private static final AtomicReference<String> LAST_LOW_XMX_WARNING = new AtomicReference<>();

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
        this.classPath = resolveEffectiveRuntimeClassPath();
    }

    /**
     * Constrói o classpath efetivo combinando {@code java.class.path} com todos os
     * JARs e
     * diretórios carregados em {@link java.net.URLClassLoader} (essencial para
     * {@code mvn exec:java},
     * onde {@code exec-maven-plugin} isola as dependências do projeto fora de
     * {@code java.class.path}).
     */
    public static String resolveEffectiveRuntimeClassPath() {
        java.util.LinkedHashSet<String> entries = new java.util.LinkedHashSet<>();
        String sysCp = System.getProperty("java.class.path", "");
        for (String part : sysCp.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (part != null && !part.isBlank()) {
                entries.add(part.trim());
            }
        }
        collectUrlClassLoaderEntries(Thread.currentThread().getContextClassLoader(), entries);
        collectUrlClassLoaderEntries(ProcessIsolatedHypervolumeExecutor.class.getClassLoader(), entries);
        try {
            var codeSrc = ProcessIsolatedHypervolumeExecutor.class.getProtectionDomain().getCodeSource();
            if (codeSrc != null && codeSrc.getLocation() != null) {
                entries.add(Path.of(codeSrc.getLocation().toURI()).toAbsolutePath().toString());
            }
        } catch (Exception ignored) {
        }
        return String.join(File.pathSeparator, entries);
    }

    private static void collectUrlClassLoaderEntries(
            ClassLoader loader, java.util.Set<String> entries) {
        ClassLoader cur = loader;
        while (cur != null) {
            if (cur instanceof java.net.URLClassLoader ucl) {
                for (java.net.URL url : ucl.getURLs()) {
                    try {
                        if ("file".equalsIgnoreCase(url.getProtocol())) {
                            entries.add(Path.of(url.toURI()).toAbsolutePath().toString());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            cur = cur.getParent();
        }
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public long getMcSampleCount() {
        return mcSampleCount;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Executa apenas o cálculo exato isolado em subprocesso ({@code "PISA"} ou
     * {@code "WFG"}) sobre
     * a caixa \([\mathbf{l}, \mathbf{r}]\), sem disparar
     * {@link MonteCarloHypervolume} em caso de
     * timeout ou falha (retornando {@code value = Double.NaN} com
     * {@link HypervolumeResult.ExecutionStatus#TIMEOUT_NO_FALLBACK} ou
     * {@link HypervolumeResult.ExecutionStatus#CRASH_NO_FALLBACK}).
     *
     * <p>
     * Permite ao pipeline do estudo orquestrar múltiplos hipervolumes pesados e
     * reutilizar um
     * resultado Monte Carlo já computado (ou disparar o fallback uma única vez
     * quando todos os
     * métodos pesados configurados atingirem timeout/falha) (T35).
     */
    public HypervolumeResult computeExactOnly(
            double[][] front,
            double[] lowerBound,
            double[] referencePoint,
            String algorithm) {
        Objects.requireNonNull(referencePoint, "referencePoint cannot be null");
        int m = referencePoint.length;
        if (m == 0) {
            throw new IllegalArgumentException("referencePoint cannot be empty");
        }

        double[] lower = lowerBound != null ? lowerBound.clone() : new double[m];
        if (lower.length != m) {
            throw new IllegalArgumentException(
                    "lowerBound length (" + lower.length + ") != referencePoint length (" + m + ")");
        }
        for (int j = 0; j < m; j++) {
            if (!Double.isFinite(lower[j]) || !Double.isFinite(referencePoint[j])) {
                throw new IllegalArgumentException("Bounding box coordinates must be finite");
            }
            if (referencePoint[j] <= lower[j]) {
                return HypervolumeResult.degenerateBox(lower, referencePoint);
            }
        }

        if (front == null || front.length == 0) {
            return HypervolumeResult.emptyFront(referencePoint);
        }

        String effectiveAlg = (algorithm != null && !algorithm.isBlank()) ? algorithm.trim() : "PISA";
        HypervolumeResult.MethodType attemptedMethod = "WFG".equalsIgnoreCase(effectiveAlg)
                ? HypervolumeResult.MethodType.EXACT_WFG
                : HypervolumeResult.MethodType.EXACT_PISA;

        List<String> workerJvmArgs = resolveConfiguredWorkerJvmArgs();

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

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(frontFile))) {
                for (int i = 0; i < front.length; i++) {
                    double[] point = front[i];
                    if (point == null || point.length != m) {
                        throw new IllegalArgumentException(
                                "Invalid front row dimension at index " + i + " (expected m=" + m + ")");
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int d = 0; d < m; d++) {
                        if (!Double.isFinite(point[d])) {
                            throw new IllegalArgumentException(
                                    "Non-finite objective value at row " + i + ", col " + d + ": " + point[d]);
                        }
                        if (d > 0) {
                            sb.append(",");
                        }
                        sb.append(Double.toString(point[d]));
                    }
                    writer.write(sb.toString());
                    writer.newLine();
                }
            }

            StringBuilder lowSb = new StringBuilder();
            StringBuilder refSb = new StringBuilder();
            for (int d = 0; d < m; d++) {
                if (d > 0) {
                    lowSb.append(",");
                    refSb.append(",");
                }
                lowSb.append(Double.toString(lower[d]));
                refSb.append(Double.toString(referencePoint[d]));
            }

            List<String> cmd = new ArrayList<>();
            cmd.add(javaExecutable);
            cmd.addAll(workerJvmArgs);
            String simCrash = System.getProperty("hype.hvWorkerSimulateCrash");
            if (simCrash != null && !simCrash.isBlank()) {
                cmd.add("-Dhype.hvWorkerSimulateCrash=" + simCrash);
            }
            String simHang = System.getProperty("hype.hvWorkerSimulateHangMs");
            if (simHang != null && !simHang.isBlank()) {
                cmd.add("-Dhype.hvWorkerSimulateHangMs=" + simHang);
            }
            cmd.add("-cp");
            cmd.add(classPath);
            cmd.add(ExactHypervolumeWorker.class.getName());
            cmd.add("--front-file");
            cmd.add(frontFile.getAbsolutePath());
            cmd.add("--lower-bound");
            cmd.add(lowSb.toString());
            cmd.add("--ref-point");
            cmd.add(refSb.toString());
            cmd.add("--algorithm");
            cmd.add(effectiveAlg);
            cmd.add("--output-file");
            cmd.add(outputFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            process = pb.start();

            stdoutDrainer = drainStream(process.getInputStream());
            stderrDrainer = drainStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                long timeoutWall = Math.max(1L, System.currentTimeMillis() - startWall);
                terminateProcessTree(process, gracePeriodMs);
                return new HypervolumeResult(
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        0.0,
                        attemptedMethod,
                        HypervolumeResult.ExecutionStatus.TIMEOUT_NO_FALLBACK,
                        timeoutWall,
                        timeoutWall,
                        0L,
                        0L,
                        0L,
                        lower,
                        referencePoint,
                        "Exact "
                                + effectiveAlg
                                + " timed out after "
                                + timeoutWall
                                + " ms (deadline="
                                + timeoutMs
                                + " ms). Worker killed (isAlive="
                                + process.isAlive()
                                + ").");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0 && outputFile.exists()) {
                Map<String, String> props = readProperties(outputFile);
                String status = props.getOrDefault("status", "UNKNOWN");
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    double val = Double.parseDouble(props.get("value"));
                    long wallMs = Long.parseLong(props.getOrDefault("wallTimeMs", "0"));
                    long cpuMs = Long.parseLong(props.getOrDefault("cpuTimeMs", "0"));
                    return new HypervolumeResult(
                            val,
                            0.0,
                            val,
                            val,
                            1.0,
                            attemptedMethod,
                            HypervolumeResult.ExecutionStatus.SUCCESS_EXACT,
                            wallMs,
                            cpuMs,
                            0L,
                            0L,
                            0L,
                            lower,
                            referencePoint,
                            "Exact calculation completed successfully");
                }
                if ("EMPTY_FRONT".equalsIgnoreCase(status)) {
                    return HypervolumeResult.emptyFront(referencePoint);
                }
                if ("DEGENERATE_BOX".equalsIgnoreCase(status)) {
                    return HypervolumeResult.degenerateBox(lower, referencePoint);
                }
            }

            long elapsed = Math.max(0L, System.currentTimeMillis() - startWall);
            return new HypervolumeResult(
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    0.0,
                    attemptedMethod,
                    HypervolumeResult.ExecutionStatus.CRASH_NO_FALLBACK,
                    elapsed,
                    elapsed,
                    0L,
                    0L,
                    0L,
                    lower,
                    referencePoint,
                    "Exact " + effectiveAlg + " worker failed with exit code " + exitCode + ".");
        } catch (Exception e) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - startWall);
            return new HypervolumeResult(
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    0.0,
                    attemptedMethod,
                    HypervolumeResult.ExecutionStatus.CRASH_NO_FALLBACK,
                    elapsed,
                    elapsed,
                    0L,
                    0L,
                    0L,
                    lower,
                    referencePoint,
                    "Exception in isolated exact execution (" + effectiveAlg + "): " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                terminateProcessTree(process, 200L);
            }
            if (stdoutDrainer != null) {
                stdoutDrainer.interrupt();
            }
            if (stderrDrainer != null) {
                stderrDrainer.interrupt();
            }
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

    /**
     * Executa o cálculo de hipervolume com isolamento de processo e fallback
     * automático para Monte Carlo.
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
        double[] lower = lowerBound != null ? lowerBound.clone() : new double[m];

        HypervolumeResult exactResult = computeExactOnly(front, lower, referencePoint, algorithm);
        if (exactResult.getStatus() == HypervolumeResult.ExecutionStatus.SUCCESS_EXACT
                || exactResult.getStatus() == HypervolumeResult.ExecutionStatus.EMPTY_FRONT
                || exactResult.getStatus() == HypervolumeResult.ExecutionStatus.DEGENERATE_BOX) {
            return exactResult;
        }

        HypervolumeResult mcResult = MonteCarloHypervolume.compute(front, lower, referencePoint, mcSampleCount, mcSeed);
        HypervolumeResult.ExecutionStatus fallbackStatus = (exactResult
                .getStatus() == HypervolumeResult.ExecutionStatus.TIMEOUT_NO_FALLBACK)
                        ? HypervolumeResult.ExecutionStatus.TIMEOUT_FALLBACK_MC
                        : HypervolumeResult.ExecutionStatus.CRASH_FALLBACK_MC;
        return wrapFallbackResult(exactResult, mcResult, fallbackStatus);
    }

    /**
     * Associa um resultado Monte Carlo já calculado (ou recém-computado no
     * fallback) ao diagnóstico
     * de falha/timeout de um método exato, sem recomputação desnecessária.
     */
    public static HypervolumeResult wrapFallbackResult(
            HypervolumeResult failedExactResult,
            HypervolumeResult mcResult,
            HypervolumeResult.ExecutionStatus fallbackStatus) {
        long wallMs = failedExactResult != null ? failedExactResult.getExecutionWallTimeMs()
                : mcResult.getExecutionWallTimeMs();
        String baseDiag = failedExactResult != null ? failedExactResult.getDiagnosticMessage() : "Exact HV failed.";
        return new HypervolumeResult(
                mcResult.getValue(),
                mcResult.getStandardError(),
                mcResult.getCiLower(),
                mcResult.getCiUpper(),
                mcResult.getConfidenceLevel(),
                HypervolumeResult.MethodType.MONTE_CARLO_WILSON,
                fallbackStatus,
                wallMs,
                wallMs,
                mcResult.getSampleCount(),
                mcResult.getHitCount(),
                mcResult.getSeed(),
                mcResult.getBoundingBoxLower(),
                mcResult.getBoundingBoxUpper(),
                baseDiag + " Fallback to Monte Carlo Wilson.");
    }

    /**
     * Métodos de hipervolume suportados pelo executor ({@code NHV} é omitido do
     * padrão {@code PISA,MC}, permanecendo aceito quando explicitamente
     * solicitado).
     */
    public enum HvMethod {
        PISA("HV", true),
        WFG("HV_WFG", true),
        MC("HV_MC", false),
        NHV("NHV", true);

        private final String indicatorTag;
        private final boolean heavyExact;

        HvMethod(String indicatorTag, boolean heavyExact) {
            this.indicatorTag = indicatorTag;
            this.heavyExact = heavyExact;
        }

        public String indicatorTag() {
            return indicatorTag;
        }

        public boolean isHeavyExact() {
            return heavyExact;
        }
    }

    /**
     * Resultado consolidado e auditável da orquestração multi-método de hipervolume
     * sobre uma
     * frente normalizada com caixa comum \([\mathbf{l}, \tilde{\mathbf{r}}]\)
     * (T35).
     *
     * @param configuredMethods     métodos configurados pelo operador (ex.:
     *                              {@code [PISA, MC]})
     * @param fallbackToggleEnabled estado da opção desligável
     *                              {@code study.hvFallbackToMcOnAllExactTimeout}
     * @param pisaResult            resultado de {@code PISAHypervolume} (ou
     *                              {@code null} se não solicitado)
     * @param wfgResult             resultado de {@code WFGHypervolume} (ou
     *                              {@code null} se não solicitado)
     * @param mcResult              resultado de {@code MonteCarloHypervolume} (da
     *                              série {@code MC} ou do fallback único, ou
     *                              {@code null})
     * @param mcComputationCount    número exato de vezes que
     *                              {@link MonteCarloHypervolume#compute} foi
     *                              invocado ({@code 0} ou {@code 1})
     * @param effectiveHvValue      valor efetivo de hipervolume (exato se ao menos
     *                              um pesado teve sucesso, MC se fallback/MC
     *                              direto, ou {@code NaN} se todos falharam com
     *                              fallback desligado)
     * @param effectiveHvSource     origem explícita do valor efetivo
     *                              ({@code EXACT_PISA}, {@code EXACT_WFG},
     *                              {@code REUSED_MC_FALLBACK},
     *                              {@code FALLBACK_MC_SINGLE_SHOT},
     *                              {@code DIRECT_MC},
     *                              {@code NONE_FALLBACK_DISABLED})
     * @param fallbackTriggered     {@code true} quando todos os hipervolumes
     *                              pesados configurados falharam/sofreram timeout e
     *                              o valor aproximado MC precisou assumir como
     *                              reserva
     * @param fallbackWarning       mensagem explícita de aviso/diagnóstico quando
     *                              houve timeout/falha em qualquer método pesado
     */
    public record MultiMethodHypervolumeEvaluation(
            java.util.Set<HvMethod> configuredMethods,
            boolean fallbackToggleEnabled,
            HypervolumeResult pisaResult,
            HypervolumeResult wfgResult,
            HypervolumeResult mcResult,
            int mcComputationCount,
            double effectiveHvValue,
            String effectiveHvSource,
            boolean fallbackTriggered,
            String fallbackWarning) {
    }

    /**
     * Faz o parse da especificação de métodos de hipervolume
     * ({@code -Dstudy.hvMethods} ou alias {@code -Dstudy.hvPolicy}). Padrão
     * {@code PISA,MC}.
     */
    public static java.util.Set<HvMethod> parseConfiguredHvMethods(
            String hvMethodsSpec, String hvPolicyAlias) {
        String effectiveSpec = hvMethodsSpec;
        if (effectiveSpec == null || effectiveSpec.isBlank()) {
            if (hvPolicyAlias != null && !hvPolicyAlias.isBlank()) {
                String p = hvPolicyAlias.trim().toUpperCase(java.util.Locale.ROOT);
                effectiveSpec = switch (p) {
                    case "DUAL", "EXACT_AND_MC" -> "PISA,MC";
                    case "EXACT_ONLY", "PISA_ONLY" -> "PISA";
                    case "WFG_ONLY" -> "WFG";
                    case "HEAVY_DUAL", "PISA_AND_WFG" -> "PISA,WFG";
                    case "MC_ONLY" -> "MC";
                    default -> throw new IllegalArgumentException(
                            "Valor inválido em study.hvPolicy: '" + hvPolicyAlias
                                    + "' (esperado DUAL, EXACT_ONLY, WFG_ONLY, HEAVY_DUAL ou MC_ONLY).");
                };
            } else {
                effectiveSpec = "PISA,MC";
            }
        }

        java.util.Set<HvMethod> methods = java.util.EnumSet.noneOf(HvMethod.class);
        for (String rawToken : effectiveSpec.split(",")) {
            String tok = rawToken.trim().toUpperCase(java.util.Locale.ROOT);
            if (tok.isEmpty()) {
                continue;
            }
            switch (tok) {
                case "PISA", "HV", "PISAHYPERVOLUME" -> methods.add(HvMethod.PISA);
                case "WFG", "HV_WFG", "WFGHYPERVOLUME" -> methods.add(HvMethod.WFG);
                case "MC", "HV_MC", "MONTE_CARLO", "MONTECARLO", "MONTECARLOHYPERVOLUME" ->
                    methods.add(HvMethod.MC);
                case "NHV", "NORMALIZEDHYPERVOLUME" -> methods.add(HvMethod.NHV);
                default -> throw new IllegalArgumentException(
                        "Método de hipervolume desconhecido em study.hvMethods: '" + rawToken
                                + "' (suportados: PISA, WFG, MC, NHV).");
            }
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nenhum método de hipervolume válido configurado em: '" + effectiveSpec + "'.");
        }
        return java.util.Collections.unmodifiableSet(methods);
    }

    /**
     * Regra ordenada de seleção de métodos de hipervolume por dimensão {@code m} em
     * {@code -Dstudy.hvMethodsByM}.
     */
    public record HvMethodsRule(
            String conditionSpec,
            java.util.function.IntPredicate predicate,
            java.util.Set<HvMethod> methods) {

        public HvMethodsRule {
            Objects.requireNonNull(conditionSpec, "conditionSpec não pode ser nulo.");
            Objects.requireNonNull(predicate, "predicate não pode ser nulo.");
            Objects.requireNonNull(methods, "methods não pode ser nulo.");
        }

        public boolean matches(int m) {
            return m >= 1 && predicate.test(m);
        }
    }

    /**
     * Faz o parse de regras ordenadas {@code <condicao>:<metodos>;...} em
     * {@code -Dstudy.hvMethodsByM}. As regras são avaliadas em ordem e a primeira
     * que casar com
     * {@code m} determina o conjunto de métodos daquela dimensão.
     */
    public static List<HvMethodsRule> parseHvMethodsByMRules(String hvMethodsByMSpec) {
        if (hvMethodsByMSpec == null || hvMethodsByMSpec.isBlank()) {
            return List.of();
        }
        String[] clauses = hvMethodsByMSpec.split(";", -1);
        List<HvMethodsRule> rules = new ArrayList<>(clauses.length);
        for (String rawClause : clauses) {
            String clause = rawClause.trim();
            if (clause.isEmpty()) {
                throw new IllegalArgumentException(
                        "Cláusula vazia em study.hvMethodsByM: '" + hvMethodsByMSpec + "'.");
            }
            int colonIdx = clause.indexOf(':');
            if (colonIdx <= 0 || colonIdx >= clause.length() - 1) {
                throw new IllegalArgumentException(
                        "Regra inválida em study.hvMethodsByM (esperado '<condicao>:<metodos>'): '"
                                + clause
                                + "'.");
            }
            String condSpec = clause.substring(0, colonIdx).trim();
            String methodsSpec = clause.substring(colonIdx + 1).trim();
            java.util.Set<HvMethod> methods = parseConfiguredHvMethods(methodsSpec, null);
            java.util.function.IntPredicate predicate = parseDimensionConditionList(condSpec);
            rules.add(new HvMethodsRule(condSpec, predicate, methods));
        }
        return java.util.Collections.unmodifiableList(rules);
    }

    /**
     * Resolve o conjunto efetivo de {@link HvMethod} para cada dimensão {@code m}
     * em
     * {@code objectives}, avaliando {@code -Dstudy.hvMethodsByM} em ordem de
     * declaração.
     */
    public static Map<Integer, java.util.Set<HvMethod>> resolveHvMethodsByObjectiveCount(
            int[] objectives,
            String hvMethodsByMSpec,
            java.util.Set<HvMethod> fallbackMethods,
            boolean hasExplicitFallback) {
        if (objectives == null || objectives.length == 0) {
            throw new IllegalArgumentException("objectives não pode ser nulo ou vazio.");
        }
        List<HvMethodsRule> rules = parseHvMethodsByMRules(hvMethodsByMSpec);
        Map<Integer, java.util.Set<HvMethod>> resolved = new java.util.LinkedHashMap<>();
        for (int m : objectives) {
            if (m <= 0) {
                throw new IllegalArgumentException("Dimensão de objetivos deve ser positiva: " + m);
            }
            if (resolved.containsKey(m)) {
                continue;
            }
            if (rules.isEmpty()) {
                Objects.requireNonNull(fallbackMethods, "fallbackMethods não pode ser nulo.");
                resolved.put(m, fallbackMethods);
                continue;
            }
            java.util.Set<HvMethod> matched = null;
            for (HvMethodsRule rule : rules) {
                if (rule.matches(m)) {
                    matched = rule.methods();
                    break;
                }
            }
            if (matched == null) {
                if (hasExplicitFallback && fallbackMethods != null) {
                    matched = fallbackMethods;
                } else {
                    throw new IllegalArgumentException(
                            "Nenhuma regra em study.hvMethodsByM correspondeu à dimensão m="
                                    + m
                                    + " ('"
                                    + hvMethodsByMSpec
                                    + "'). Cubra todas as dimensões ativas (ex.: ';*:MC') ou informe -Dstudy.hvMethods.");
                }
            }
            resolved.put(m, matched);
        }
        return java.util.Collections.unmodifiableMap(resolved);
    }

    /**
     * Formata o rótulo canônico de métodos de hipervolume (ex.: {@code "PISA+MC"})
     * ordenado pela
     * declaração de {@link HvMethod}.
     */
    public static String formatHvMethodsLabel(java.util.Set<HvMethod> methods) {
        Objects.requireNonNull(methods, "methods não pode ser nulo.");
        java.util.EnumSet<HvMethod> canonical = java.util.EnumSet.copyOf(methods);
        return canonical.stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining("+"));
    }

    private static java.util.function.IntPredicate parseDimensionConditionList(String condSpec) {
        String[] parts = condSpec.split(",", -1);
        java.util.function.IntPredicate combined = null;
        for (String part : parts) {
            java.util.function.IntPredicate single = parseSingleDimensionCondition(part);
            combined = (combined == null) ? single : combined.or(single);
        }
        if (combined == null) {
            throw new IllegalArgumentException(
                    "Condição de dimensão vazia em study.hvMethodsByM.");
        }
        return combined;
    }

    private static java.util.function.IntPredicate parseSingleDimensionCondition(String rawTerm) {
        String term = rawTerm.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
        if (term.isEmpty()) {
            throw new IllegalArgumentException(
                    "Termo de condição vazio em study.hvMethodsByM: '" + rawTerm + "'.");
        }
        if ("*".equals(term) || "default".equals(term) || "else".equals(term) || "all".equals(term)) {
            return m -> m >= 1;
        }
        if (term.startsWith("<=") || term.startsWith("le")) {
            int k = parsePositiveDimensionBound(term.substring(2), rawTerm);
            return m -> m <= k;
        }
        if (term.startsWith(">=") || term.startsWith("ge")) {
            int k = parsePositiveDimensionBound(term.substring(2), rawTerm);
            return m -> m >= k;
        }
        if (term.startsWith("==") || term.startsWith("eq")) {
            int k = parsePositiveDimensionBound(term.substring(2), rawTerm);
            return m -> m == k;
        }
        if (term.startsWith("<") || term.startsWith("lt")) {
            int k = parsePositiveDimensionBound(
                    term.startsWith("<") ? term.substring(1) : term.substring(2), rawTerm);
            return m -> m < k;
        }
        if (term.startsWith(">") || term.startsWith("gt")) {
            int k = parsePositiveDimensionBound(
                    term.startsWith(">") ? term.substring(1) : term.substring(2), rawTerm);
            return m -> m > k;
        }
        if (term.startsWith("=")) {
            int k = parsePositiveDimensionBound(term.substring(1), rawTerm);
            return m -> m == k;
        }
        int dashIdx = term.indexOf('-');
        if (dashIdx > 0) {
            int min = parsePositiveDimensionBound(term.substring(0, dashIdx), rawTerm);
            int max = parsePositiveDimensionBound(term.substring(dashIdx + 1), rawTerm);
            if (min > max) {
                throw new IllegalArgumentException(
                        "Faixa de dimensão invertida em study.hvMethodsByM: '" + rawTerm + "'.");
            }
            return m -> m >= min && m <= max;
        }
        int exact = parsePositiveDimensionBound(term, rawTerm);
        return m -> m == exact;
    }

    private static int parsePositiveDimensionBound(String digits, String originalTerm) {
        try {
            int val = Integer.parseInt(digits);
            if (val <= 0) {
                throw new IllegalArgumentException(
                        "Dimensão deve ser >= 1 em study.hvMethodsByM: '" + originalTerm + "'.");
            }
            return val;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "Condição de dimensão inválida em study.hvMethodsByM: '" + originalTerm + "'.",
                    nfe);
        }
    }

    /**
     * Deriva de forma determinística, isolada e livre de colisões a semente do
     * estimador Monte Carlo
     * para a tupla {@code (baseSeed, problemId, m, algTag, runId)}.
     */
    public static long deriveDeterministicMcSeed(
            long baseSeed, String problemId, int m, String algTag, int runId) {
        long h = baseSeed ^ 0x4856_4D43_5345_4544L; // "HVMCSEED"
        h = mix64(h ^ Objects.hashCode(problemId));
        h = mix64(h ^ ((long) m * 0x9E3779B97F4A7C15L));
        h = mix64(h ^ Objects.hashCode(algTag));
        h = mix64(h ^ ((long) runId * 0xBF58476D1CE4E5B9L));
        return h;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Avalia sobre a mesma frente e mesma caixa \([\mathbf{l}, \mathbf{r}]\) os
     * métodos de
     * hipervolume configurados, aplicando a política generalizada aprovada na T35:
     * <ul>
     * <li>Se {@link HvMethod#MC} faz parte de {@code configuredMethods}, calcula
     * {@code MC} uma
     * única vez e, caso todos os métodos pesados configurados ({@code PISA} e/ou
     * {@code WFG})
     * atinjam timeout/falha, reutiliza o resultado {@code MC} já calculado sem
     * recomputar;</li>
     * <li>Se apenas hipervolumes pesados ({@code PISA}, {@code WFG} ou
     * {@code PISA,WFG}, sem
     * {@code MC}) estão configurados e <b>todos</b> atingirem timeout/falha,
     * executa
     * <b>exatamente 1 vez</b> o fallback {@link MonteCarloHypervolume} quando
     * {@code fallbackToMcOnAllExactTimeout == true} (ou mantém apenas {@code NA}
     * quando
     * {@code fallbackToMcOnAllExactTimeout == false}).</li>
     * </ul>
     */
    public MultiMethodHypervolumeEvaluation evaluateConfiguredHypervolumes(
            double[][] normalizedFront,
            double[] lowerBound,
            double[] referencePoint,
            java.util.Set<HvMethod> configuredMethods,
            boolean fallbackToMcOnAllExactTimeout,
            long mcSeed) {
        Objects.requireNonNull(configuredMethods, "configuredMethods cannot be null");
        if (configuredMethods.isEmpty()) {
            throw new IllegalArgumentException("configuredMethods cannot be empty");
        }
        Objects.requireNonNull(referencePoint, "referencePoint cannot be null");
        int m = referencePoint.length;
        double[] lower = lowerBound != null ? lowerBound.clone() : new double[m];

        int mcCalls = 0;
        HypervolumeResult mcRes = null;
        if (configuredMethods.contains(HvMethod.MC)) {
            mcRes = MonteCarloHypervolume.compute(
                    normalizedFront, lower, referencePoint, mcSampleCount, mcSeed);
            mcCalls++;
        }

        HypervolumeResult pisaRes = null;
        if (configuredMethods.contains(HvMethod.PISA)) {
            pisaRes = computeExactOnly(normalizedFront, lower, referencePoint, "PISA");
        }

        HypervolumeResult wfgRes = null;
        if (configuredMethods.contains(HvMethod.WFG)) {
            wfgRes = computeExactOnly(normalizedFront, lower, referencePoint, "WFG");
        }

        boolean hasHeavyConfigured = configuredMethods.contains(HvMethod.PISA)
                || configuredMethods.contains(HvMethod.WFG);
        if (!hasHeavyConfigured) {
            double val = mcRes != null ? mcRes.getValue() : Double.NaN;
            return new MultiMethodHypervolumeEvaluation(
                    configuredMethods,
                    fallbackToggleEnabled(fallbackToMcOnAllExactTimeout),
                    null,
                    null,
                    mcRes,
                    mcCalls,
                    val,
                    "DIRECT_MC",
                    false,
                    "");
        }

        boolean pisaSucceeded = isExactSuccessOrDeterministicEmpty(pisaRes);
        boolean wfgSucceeded = isExactSuccessOrDeterministicEmpty(wfgRes);
        boolean anyHeavySucceeded = pisaSucceeded || wfgSucceeded;

        List<String> failedHeavyDetails = new ArrayList<>(2);
        if (pisaRes != null && !pisaSucceeded) {
            failedHeavyDetails.add("PISA:" + pisaRes.getStatus());
        }
        if (wfgRes != null && !wfgSucceeded) {
            failedHeavyDetails.add("WFG:" + wfgRes.getStatus());
        }
        String failedSummary = String.join(";", failedHeavyDetails);

        if (anyHeavySucceeded) {
            double effVal = pisaSucceeded ? pisaRes.getValue() : wfgRes.getValue();
            String effSource = pisaSucceeded ? "EXACT_PISA" : "EXACT_WFG";
            String partialWarn = failedHeavyDetails.isEmpty()
                    ? ""
                    : ("AVISO_PARCIAL_HV: Metodo(s) pesado(s) sem sucesso ("
                            + failedSummary
                            + "), mas "
                            + effSource
                            + " concluiu com exito.");
            return new MultiMethodHypervolumeEvaluation(
                    configuredMethods,
                    fallbackToMcOnAllExactTimeout,
                    pisaRes,
                    wfgRes,
                    mcRes,
                    mcCalls,
                    effVal,
                    effSource,
                    false,
                    partialWarn);
        }

        // Todos os hipervolumes pesados configurados falharam/atingiram timeout!
        if (mcRes != null) {
            // MC já estava na série configurada -> reutiliza sem recomputar!
            String warn = "AVISO_FALLBACK_HV: Todos os hipervolumes pesados configurados falharam/atingiram timeout ("
                    + failedSummary
                    + "); reutilizado HV_MC aproximado ja calculado (sem recomputacao).";
            return new MultiMethodHypervolumeEvaluation(
                    configuredMethods,
                    fallbackToMcOnAllExactTimeout,
                    pisaRes,
                    wfgRes,
                    mcRes,
                    mcCalls,
                    mcRes.getValue(),
                    "REUSED_MC_FALLBACK",
                    true,
                    warn);
        }

        if (fallbackToMcOnAllExactTimeout) {
            // Somente pesados configurados e todos em timeout/falha + opção ligada ->
            // calcula MC 1 única vez!
            mcRes = MonteCarloHypervolume.compute(
                    normalizedFront, lower, referencePoint, mcSampleCount, mcSeed);
            mcCalls++;
            String warn = "AVISO_FALLBACK_HV: Todos os hipervolumes pesados configurados ("
                    + failedSummary
                    + ") atingiram timeout/falha; calculado 1 unica vez MonteCarloHypervolume aproximado como reserva.";
            return new MultiMethodHypervolumeEvaluation(
                    configuredMethods,
                    true,
                    pisaRes,
                    wfgRes,
                    mcRes,
                    mcCalls,
                    mcRes.getValue(),
                    "FALLBACK_MC_SINGLE_SHOT",
                    true,
                    warn);
        }

        // Opção de fallback explicitamente desligada pelo operador
        String warnDisabled = "AVISO_HV_INDISPONIVEL: Todos os hipervolumes pesados configurados ("
                + failedSummary
                + ") atingiram timeout/falha e study.hvFallbackToMcOnAllExactTimeout=false.";
        return new MultiMethodHypervolumeEvaluation(
                configuredMethods,
                false,
                pisaRes,
                wfgRes,
                null,
                mcCalls,
                Double.NaN,
                "NONE_FALLBACK_DISABLED",
                false,
                warnDisabled);
    }

    private static boolean fallbackToggleEnabled(boolean flag) {
        return flag;
    }

    private static boolean isExactSuccessOrDeterministicEmpty(HypervolumeResult res) {
        if (res == null) {
            return false;
        }
        return res.getStatus() == HypervolumeResult.ExecutionStatus.SUCCESS_EXACT
                || res.getStatus() == HypervolumeResult.ExecutionStatus.EMPTY_FRONT;
    }

    private static void terminateProcessTree(Process process, long graceMs) {
        try {
            ProcessHandle handle = process.toHandle();
            List<ProcessHandle> descendants = handle.descendants().toList();
            for (ProcessHandle child : descendants) {
                try {
                    child.destroy();
                } catch (Exception ignored) {
                }
            }
            process.destroy();
            boolean softKilled = process.waitFor(Math.max(50L, graceMs), TimeUnit.MILLISECONDS);
            if (!softKilled || process.isAlive()) {
                for (ProcessHandle child : handle.descendants().toList()) {
                    try {
                        child.destroyForcibly();
                    } catch (Exception ignored) {
                    }
                }
                process.destroyForcibly();
                process.waitFor(2_000L, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static Thread drainStream(InputStream stream) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
                while (br.readLine() != null) {
                    // Consome stream para evitar deadlock de pipe cheio no SO
                }
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static Map<String, String> readProperties(File file) throws Exception {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    /**
     * Lê e valida os argumentos JVM adicionais configurados em
     * {@code -Dstudy.workerJvmArgs} para repasse exclusivo aos subprocessos workers
     * ({@code IsolatedJobExecutor --worker} e {@code ExactHypervolumeWorker}).
     */
    public static List<String> resolveConfiguredWorkerJvmArgs() {
        return parseWorkerJvmArgs(System.getProperty(WORKER_JVM_ARGS_PROPERTY));
    }

    /**
     * Faz o parsing seguro de {@code -Dstudy.workerJvmArgs} respeitando aspas
     * simples/duplas (caminhos Windows com espaços) e bloqueando flags perigosas ou
     * limites de heap inviáveis ({@code -Xmx < 64m} ou {@code -Xms > -Xmx}).
     */
    public static List<String> parseWorkerJvmArgs(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenizeRespectingQuotes(rawArgs);
        long xmsBytes = -1L;
        long xmxBytes = -1L;

        for (String token : tokens) {
            if (!token.startsWith("-")) {
                throw new IllegalArgumentException(
                        "Argumento inválido em "
                                + WORKER_JVM_ARGS_PROPERTY
                                + " (toda opção JVM deve iniciar com '-'): '"
                                + token
                                + "'.");
            }
            String lower = token.toLowerCase(java.util.Locale.ROOT);
            if ("-cp".equals(lower)
                    || "-classpath".equals(lower)
                    || "--class-path".equals(lower)
                    || lower.startsWith("--class-path=")
                    || "-jar".equals(lower)
                    || "-m".equals(lower)
                    || "--module".equals(lower)
                    || lower.startsWith("--module=")
                    || "-p".equals(lower)
                    || "--module-path".equals(lower)
                    || lower.startsWith("--module-path=")
                    || "--upgrade-module-path".equals(lower)
                    || "-version".equals(lower)
                    || "--version".equals(lower)
                    || "-showversion".equals(lower)
                    || "--show-version".equals(lower)
                    || "-help".equals(lower)
                    || "--help".equals(lower)
                    || "-?".equals(lower)) {
                throw new IllegalArgumentException(
                        "Flag proibida em "
                                + WORKER_JVM_ARGS_PROPERTY
                                + " (o supervisor controla classpath, módulo e entrypoint): '"
                                + token
                                + "'.");
            }
            if (token.startsWith("-Xms") || token.startsWith("-Xmx")) {
                Matcher matcher = HEAP_SIZE_FLAG_PATTERN.matcher(token);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException(
                            "Formato inválido para flag de heap em "
                                    + WORKER_JVM_ARGS_PROPERTY
                                    + ": '"
                                    + token
                                    + "' (use ex.: -Xms64m, -Xmx768m, -Xmx2g).");
                }
                long bytes = parseJvmHeapBytes(matcher.group(2), matcher.group(3), token);
                if ("s".equals(matcher.group(1))) {
                    xmsBytes = bytes;
                } else {
                    xmxBytes = bytes;
                }
            }
        }

        if (xmxBytes > 0L && xmxBytes < HARD_MIN_WORKER_XMX_BYTES) {
            throw new IllegalArgumentException(
                    "Heap máximo em "
                            + WORKER_JVM_ARGS_PROPERTY
                            + " muito baixo (-Xmx < 64m): '"
                            + rawArgs
                            + "'.");
        }
        if (xmsBytes > 0L && xmxBytes > 0L && xmsBytes > xmxBytes) {
            throw new IllegalArgumentException(
                    "Configuração incoerente em "
                            + WORKER_JVM_ARGS_PROPERTY
                            + ": -Xms ("
                            + xmsBytes
                            + " bytes) não pode exceder -Xmx ("
                            + xmxBytes
                            + " bytes).");
        }
        if (xmxBytes > 0L && xmxBytes < MIN_RECOMMENDED_WORKER_XMX_BYTES) {
            String prevWarned = LAST_LOW_XMX_WARNING.getAndSet(rawArgs);
            if (!rawArgs.equals(prevWarned)) {
                System.err.println(
                        "[AVISO] "
                                + WORKER_JVM_ARGS_PROPERTY
                                + " define -Xmx abaixo de 384 MiB ("
                                + rawArgs
                                + "); workers de AGE-MOEA/II em m=10 podem exigir ~395 MB de working set.");
            }
        }
        return List.copyOf(tokens);
    }

    private static long parseJvmHeapBytes(String digits, String unit, String token) {
        try {
            long base = Long.parseLong(digits);
            if (base <= 0L) {
                throw new IllegalArgumentException(
                        "Tamanho de heap deve ser positivo em " + WORKER_JVM_ARGS_PROPERTY + ": '" + token + "'.");
            }
            long multiplier = switch (unit.toLowerCase(java.util.Locale.ROOT)) {
                case "k" -> 1024L;
                case "m" -> 1024L * 1024L;
                case "g" -> 1024L * 1024L * 1024L;
                default -> 1L;
            };
            return Math.multiplyExact(base, multiplier);
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Valor numérico inválido para heap em " + WORKER_JVM_ARGS_PROPERTY + ": '" + token + "'.",
                    ex);
        }
    }

    private static List<String> tokenizeRespectingQuotes(String rawArgs) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean tokenStarted = false;

        for (int i = 0; i < rawArgs.length(); i++) {
            char c = rawArgs.charAt(i);
            if (c == '\\' && !inSingle && i + 1 < rawArgs.length()) {
                char next = rawArgs.charAt(i + 1);
                if (next == '"' || (next == '\'' && !inDouble)) {
                    current.append(next);
                    tokenStarted = true;
                    i++;
                    continue;
                }
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                tokenStarted = true;
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                tokenStarted = true;
            } else if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (tokenStarted) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
            } else {
                current.append(c);
                tokenStarted = true;
            }
        }
        if (inSingle || inDouble) {
            throw new IllegalArgumentException(
                    "Aspas não fechadas em " + WORKER_JVM_ARGS_PROPERTY + ": " + rawArgs);
        }
        if (tokenStarted) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}

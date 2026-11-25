package br.usp.lti.newmoea.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume;

/**
 * Worker CLI autônomo executado em processo isolado da JVM pelo
 * {@link ProcessIsolatedHypervolumeExecutor}.
 *
 * <p>
 * Garante:
 * <ol>
 * <li>Término forçado real via árvore de processos ({@code destroyForcibly})
 * sem travar a JVM
 * hospedeira em recursões combinatórias não-cooperativas;</li>
 * <li>Paridade exata da caixa de integração \([\mathbf{l}, \mathbf{r}]\) entre
 * {@link PISAHypervolume}, {@link WFGHypervolume} e
 * {@link MonteCarloHypervolume}: como no
 * jMetal 7.8 {@code PISAHypervolume.compute} ignora {@code refPoint} e trunca
 * coordenadas em
 * \([0, 1]^m\), este worker reescala afim e bijetivamente \([\mathbf{l},
 * \mathbf{r}] \mapsto
 * [0, 1]^m\) e multiplica pelo determinante Jacobiano \(\prod_{j=1}^m (r_j -
 * l_j)\) (T35).</li>
 * </ol>
 */
public final class ExactHypervolumeWorker {

    public static void main(String[] args) {
        String frontFilePath = null;
        String lowerBoundStr = null;
        String refPointStr = null;
        String algorithm = "PISA";
        String outputFilePath = null;

        for (int i = 0; i < args.length; i++) {
            if ("--front-file".equals(args[i]) && i + 1 < args.length) {
                frontFilePath = args[++i];
            } else if ("--lower-bound".equals(args[i]) && i + 1 < args.length) {
                lowerBoundStr = args[++i];
            } else if ("--ref-point".equals(args[i]) && i + 1 < args.length) {
                refPointStr = args[++i];
            } else if ("--algorithm".equals(args[i]) && i + 1 < args.length) {
                algorithm = args[++i];
            } else if ("--output-file".equals(args[i]) && i + 1 < args.length) {
                outputFilePath = args[++i];
            }
        }

        if (frontFilePath == null || refPointStr == null || outputFilePath == null) {
            System.err.println(
                    "Usage: ExactHypervolumeWorker --front-file <path> [--lower-bound <l0,l1,...>] "
                            + "--ref-point <p0,p1,...> --algorithm <PISA|WFG> --output-file <path>");
            System.exit(2);
        }

        File outputFile = new File(outputFilePath);

        try {
            // Hooks controlados exclusivos para testes determinísticos de timeout/crash do
            // supervisor
            if (Boolean.getBoolean("hype.hvWorkerSimulateCrash")) {
                throw new IllegalStateException("Simulated worker crash for isolated supervisor test");
            }
            long simulatedHangMs = Long.getLong("hype.hvWorkerSimulateHangMs", 0L);
            if (simulatedHangMs > 0L) {
                Thread.sleep(simulatedHangMs);
            }

            String[] refParts = refPointStr.split(",");
            int m = refParts.length;
            double[] refPoint = new double[m];
            for (int i = 0; i < m; i++) {
                refPoint[i] = Double.parseDouble(refParts[i].trim());
                if (!Double.isFinite(refPoint[i])) {
                    throw new IllegalArgumentException("Non-finite reference point coordinate at index " + i);
                }
            }

            double[] lowerBound = new double[m];
            if (lowerBoundStr != null && !lowerBoundStr.isBlank()) {
                String[] lowParts = lowerBoundStr.split(",");
                if (lowParts.length != m) {
                    throw new IllegalArgumentException(
                            "lowerBound dimension (" + lowParts.length + ") != refPoint dimension (" + m + ")");
                }
                for (int i = 0; i < m; i++) {
                    lowerBound[i] = Double.parseDouble(lowParts[i].trim());
                    if (!Double.isFinite(lowerBound[i])) {
                        throw new IllegalArgumentException("Non-finite lowerBound coordinate at index " + i);
                    }
                }
            }

            double boxVolume = 1.0;
            double[] span = new double[m];
            for (int j = 0; j < m; j++) {
                if (refPoint[j] <= lowerBound[j]) {
                    writeOutput(outputFile, 0.0, 0L, 0L, algorithm, "DEGENERATE_BOX", "Degenerate box r_j <= l_j");
                    System.exit(0);
                }
                span[j] = refPoint[j] - lowerBound[j];
                boxVolume *= span[j];
            }

            List<double[]> points = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(frontFilePath))) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] tokens = line.split("[,\\s]+");
                    if (tokens.length != m) {
                        throw new IllegalArgumentException(
                                "Dimension mismatch at line " + lineNo + ": expected " + m + ", got " + tokens.length);
                    }
                    double[] pt = new double[m];
                    for (int d = 0; d < m; d++) {
                        double v = Double.parseDouble(tokens[d]);
                        if (!Double.isFinite(v)) {
                            throw new IllegalArgumentException(
                                    "Non-finite objective value at line " + lineNo + ", col " + d + ": " + tokens[d]);
                        }
                        pt[d] = v;
                    }
                    points.add(pt);
                }
            }

            if (points.isEmpty()) {
                writeOutput(outputFile, 0.0, 0L, 0L, algorithm, "EMPTY_FRONT", "Front is empty");
                System.exit(0);
            }

            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long startCpu = (threadBean.isCurrentThreadCpuTimeSupported()) ? threadBean.getCurrentThreadCpuTime() : -1L;
            long startWall = System.currentTimeMillis();

            // Filtra pontos que dominam estritamente o ponto de referência r (pt[j] <
            // refPoint[j] para todo j)
            List<double[]> active = new ArrayList<>(points.size());
            for (double[] pt : points) {
                boolean dominatesRef = true;
                for (int j = 0; j < m; j++) {
                    if (pt[j] >= refPoint[j]) {
                        dominatesRef = false;
                        break;
                    }
                }
                if (dominatesRef) {
                    active.add(pt);
                }
            }

            double calculatedHv;
            if (active.isEmpty()) {
                calculatedHv = 0.0;
            } else if ("WFG".equalsIgnoreCase(algorithm)) {
                double[][] shifted = new double[active.size()][m];
                for (int i = 0; i < active.size(); i++) {
                    double[] pt = active.get(i);
                    for (int j = 0; j < m; j++) {
                        shifted[i][j] = Math.max(0.0, pt[j] - lowerBound[j]);
                    }
                }
                calculatedHv = new WFGHypervolume(span).compute(shifted);
            } else {
                // PISA: reescala [lowerBound, refPoint] -> [0, 1)^m e multiplica pelo Jacobiano
                // boxVolume
                double[][] unitScaled = new double[active.size()][m];
                for (int i = 0; i < active.size(); i++) {
                    double[] pt = active.get(i);
                    for (int j = 0; j < m; j++) {
                        unitScaled[i][j] = Math.max(0.0, pt[j] - lowerBound[j]) / span[j];
                    }
                }
                double[] unitRef = new double[m];
                Arrays.fill(unitRef, 1.0);
                calculatedHv = new PISAHypervolume(unitRef).compute(unitScaled) * boxVolume;
            }

            long endWall = System.currentTimeMillis();
            long endCpu = (threadBean.isCurrentThreadCpuTimeSupported()) ? threadBean.getCurrentThreadCpuTime() : -1L;

            long wallMs = Math.max(0L, endWall - startWall);
            long cpuMs = (startCpu >= 0 && endCpu >= 0) ? Math.max(0L, (endCpu - startCpu) / 1_000_000L) : wallMs;

            writeOutput(outputFile, calculatedHv, wallMs, cpuMs, algorithm, "SUCCESS", "Exact calculation completed");
            System.exit(0);

        } catch (Throwable t) {
            System.err.println("Worker execution failed: " + t.getMessage());
            t.printStackTrace(System.err);
            try {
                writeOutput(
                        outputFile,
                        Double.NaN,
                        -1L,
                        -1L,
                        algorithm,
                        "ERROR",
                        t.getClass().getName() + ": " + t.getMessage());
            } catch (Exception ignored) {
            }
            System.exit(1);
        }
    }

    private static void writeOutput(
            File file,
            double value,
            long wallMs,
            long cpuMs,
            String algorithm,
            String status,
            String msg) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("value=" + value + "\n");
            writer.write("wallTimeMs=" + wallMs + "\n");
            writer.write("cpuTimeMs=" + cpuMs + "\n");
            writer.write("algorithm=" + algorithm + "\n");
            writer.write("status=" + status + "\n");
            writer.write("message=" + (msg != null ? msg.replace("\n", " ") : "") + "\n");
        }
    }
}

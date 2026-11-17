package br.usp.lti.newmoea.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import org.uma.jmetal.qualityindicator.impl.hypervolume.Hypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume;

/**
 * Worker CLI autônomo executado em processo isolado da JVM pelo
 * {@link ProcessIsolatedHypervolumeExecutor}.
 *
 * <p>Permite execução com garantia de término forçado via {@link Process#destroyForcibly()},
 * evitando que recursões combinatórias ou laços não-cooperativos do jMetal travem
 * a JVM hospedeira ou o executor do pipeline.</p>
 */
public final class ExactHypervolumeWorker {

    public static void main(String[] args) {
        String frontFilePath = null;
        String refPointStr = null;
        String algorithm = "PISA";
        String outputFilePath = null;

        for (int i = 0; i < args.length; i++) {
            if ("--front-file".equals(args[i]) && i + 1 < args.length) {
                frontFilePath = args[++i];
            } else if ("--ref-point".equals(args[i]) && i + 1 < args.length) {
                refPointStr = args[++i];
            } else if ("--algorithm".equals(args[i]) && i + 1 < args.length) {
                algorithm = args[++i];
            } else if ("--output-file".equals(args[i]) && i + 1 < args.length) {
                outputFilePath = args[++i];
            }
        }

        if (frontFilePath == null || refPointStr == null || outputFilePath == null) {
            System.err.println("Usage: ExactHypervolumeWorker --front-file <path> --ref-point <p0,p1,...> --algorithm <PISA|WFG> --output-file <path>");
            System.exit(2);
        }

        File outputFile = new File(outputFilePath);

        try {
            // Parse do ponto de referência
            String[] parts = refPointStr.split(",");
            double[] refPoint = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                refPoint[i] = Double.parseDouble(parts[i].trim());
            }

            // Leitura da frente
            List<double[]> points = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(frontFilePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] tokens = line.split("[,\\s]+");
                    if (tokens.length >= refPoint.length) {
                        double[] pt = new double[refPoint.length];
                        for (int d = 0; d < refPoint.length; d++) {
                            pt[d] = Double.parseDouble(tokens[d]);
                        }
                        points.add(pt);
                    }
                }
            }

            if (points.isEmpty()) {
                writeOutput(outputFile, 0.0, 0L, 0L, algorithm, "EMPTY_FRONT", "Front is empty");
                System.exit(0);
            }

            double[][] frontMatrix = points.toArray(new double[0][]);

            Hypervolume hvIndicator;
            if ("WFG".equalsIgnoreCase(algorithm)) {
                hvIndicator = new WFGHypervolume(refPoint);
            } else {
                hvIndicator = new PISAHypervolume(refPoint);
            }

            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long startCpu = (threadBean.isCurrentThreadCpuTimeSupported()) ? threadBean.getCurrentThreadCpuTime() : -1L;
            long startWall = System.currentTimeMillis();

            double calculatedHv = hvIndicator.compute(frontMatrix);

            long endWall = System.currentTimeMillis();
            long endCpu = (threadBean.isCurrentThreadCpuTimeSupported()) ? threadBean.getCurrentThreadCpuTime() : -1L;

            long wallMs = endWall - startWall;
            long cpuMs = (startCpu >= 0 && endCpu >= 0) ? (endCpu - startCpu) / 1_000_000L : wallMs;

            writeOutput(outputFile, calculatedHv, wallMs, cpuMs, algorithm, "SUCCESS", "Exact calculation completed");
            System.exit(0);

        } catch (Throwable t) {
            System.err.println("Worker execution failed: " + t.getMessage());
            t.printStackTrace(System.err);
            try {
                writeOutput(outputFile, Double.NaN, -1L, -1L, algorithm, "ERROR", t.getClass().getName() + ": " + t.getMessage());
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
            writer.write("message=" + msg.replace("\n", " ") + "\n");
        }
    }
}

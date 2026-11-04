package br.usp.lti.newmoea.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilitário para validação estrutural, dimensional e analítico-geométrica das frentes de Pareto
 * de referência utilizadas pelo pipeline experimental ({@code pareto_fronts/csv/}).
 *
 * <p>As frentes de Pareto são subconjuntos do espaço de objetivos \(\mathbb{R}^m\) e não devem ser
 * confundidas com vetores de pesos de decomposição (MOEA/DD) nem com pontos de referência no
 * simplex unitário de Das &amp; Dennis (NSGA-III).
 */
public final class ParetoFrontValidator {

    /** Tolerância padrão para detecção de pontos quase-duplicados (\(\|\mathbf{u} - \mathbf{v}\|_\infty \le 10^{-9}\)). */
    public static final double DEFAULT_DUPLICATE_TOLERANCE = 1e-9;

    private ParetoFrontValidator() {
        // Classe utilitária
    }

    /**
     * Resumo estatístico e de integridade de um arquivo de frente de Pareto de referência.
     *
     * @param totalPoints número total de linhas válidas (pontos) na frente
     * @param uniquePoints número de pontos distintos segundo a tolerância {@code 1e-9}
     * @param duplicatePoints número de pontos duplicados ({@code totalPoints - uniquePoints})
     * @param dimension dimensionalidade verificada (\(m\))
     * @param minPerObjective valor mínimo observado em cada objetivo \(f_1, \dots, f_m\)
     * @param maxPerObjective valor máximo observado em cada objetivo \(f_1, \dots, f_m\)
     * @param hasNegativeValues {@code true} se houver ao menos uma coordenada estritamente negativa
     * @param points matriz de pontos carregada (\(\text{totalPoints} \times m\))
     */
    public record FrontValidationSummary(
            int totalPoints,
            int uniquePoints,
            int duplicatePoints,
            int dimension,
            double[] minPerObjective,
            double[] maxPerObjective,
            boolean hasNegativeValues,
            double[][] points) {}

    /**
     * Valida a integridade estrutural, dimensionalidade e finitude de um arquivo CSV de frente de
     * Pareto de referência e retorna seu resumo estatístico.
     *
     * @param csvFile caminho para o arquivo CSV da frente de Pareto
     * @param expectedObjectives número esperado de objetivos (\(m \ge 2\))
     * @return {@link FrontValidationSummary} contendo estatísticas e os pontos carregados
     * @throws IllegalArgumentException se o arquivo não existir, não for regular, estiver vazio,
     *     possuir linha com dimensionalidade diferente de {@code expectedObjectives}, ou contiver
     *     valores não numéricos, {@code NaN} ou {@code Infinity}
     */
    public static FrontValidationSummary validateParetoFrontFile(Path csvFile, int expectedObjectives) {
        if (expectedObjectives < 2) {
            throw new IllegalArgumentException(
                    "O número esperado de objetivos deve ser >= 2, mas foi: " + expectedObjectives);
        }
        if (csvFile == null || !Files.exists(csvFile) || !Files.isRegularFile(csvFile)) {
            throw new IllegalArgumentException(
                    "Arquivo de frente de Pareto inexistente ou inválido: " + csvFile);
        }

        List<String> rawLines;
        try {
            rawLines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Falha ao ler arquivo de frente de Pareto: " + csvFile, e);
        }

        List<double[]> parsedPoints = new ArrayList<>();
        int lineNumber = 0;
        for (String rawLine : rawLines) {
            lineNumber++;
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split(",");
            if (tokens.length != expectedObjectives) {
                throw new IllegalArgumentException(
                        String.format(
                                "Dimensionalidade incorreta em '%s' na linha %d: esperado %d objetivos, encontrado %d.",
                                csvFile.getFileName(), lineNumber, expectedObjectives, tokens.length));
            }
            double[] point = new double[expectedObjectives];
            for (int j = 0; j < expectedObjectives; j++) {
                double val;
                try {
                    val = Double.parseDouble(tokens[j].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Valor numérico inválido '%s' em '%s' (linha %d, coluna %d).",
                                    tokens[j], csvFile.getFileName(), lineNumber, j + 1),
                            e);
                }
                if (!Double.isFinite(val)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Valor não finito (%s) em '%s' (linha %d, coluna %d).",
                                    tokens[j], csvFile.getFileName(), lineNumber, j + 1));
                }
                point[j] = val;
            }
            parsedPoints.add(point);
        }

        if (parsedPoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Arquivo de frente de Pareto está vazio (nenhum ponto válido): " + csvFile);
        }

        int totalPoints = parsedPoints.size();
        double[][] points = parsedPoints.toArray(new double[0][]);
        double[] minPerObj = new double[expectedObjectives];
        double[] maxPerObj = new double[expectedObjectives];
        Arrays.fill(minPerObj, Double.POSITIVE_INFINITY);
        Arrays.fill(maxPerObj, Double.NEGATIVE_INFINITY);
        boolean hasNegative = false;

        for (double[] pt : points) {
            for (int j = 0; j < expectedObjectives; j++) {
                double v = pt[j];
                if (v < minPerObj[j]) {
                    minPerObj[j] = v;
                }
                if (v > maxPerObj[j]) {
                    maxPerObj[j] = v;
                }
                if (v < 0.0) {
                    hasNegative = true;
                }
            }
        }

        int uniquePoints = countUniquePoints(points, DEFAULT_DUPLICATE_TOLERANCE);
        int duplicatePoints = totalPoints - uniquePoints;

        return new FrontValidationSummary(
                totalPoints,
                uniquePoints,
                duplicatePoints,
                expectedObjectives,
                minPerObj,
                maxPerObj,
                hasNegative,
                points);
    }

    /**
     * Conta o número de pontos distintos na frente considerando a distância de Chebyshev
     * (\(\|\mathbf{u} - \mathbf{v}\|_\infty \le \text{tolerance}\)).
     *
     * <p>Para frentes grandes (como {@code DTLZ5.3D.csv} com 166.500 linhas), utiliza indexação por
     * buckets quantizados para evitar custo quadrático.
     *
     * @param front matriz de pontos da frente
     * @param tolerance tolerância por coordenada para considerar dois pontos idênticos
     * @return quantidade de pontos únicos
     */
    public static int countUniquePoints(double[][] front, double tolerance) {
        validateNonEmptyMatrix(front);
        if (tolerance <= 0.0) {
            List<double[]> uniqueExact = new ArrayList<>();
            for (double[] pt : front) {
                boolean found = false;
                for (double[] u : uniqueExact) {
                    if (Arrays.equals(pt, u)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    uniqueExact.add(pt);
                }
            }
            return uniqueExact.size();
        }

        double bucketWidth = tolerance * 2.0;
        java.util.Map<Long, List<double[]>> buckets = new java.util.HashMap<>();
        int uniqueCount = 0;

        for (double[] candidate : front) {
            long baseHash = computeGridHash(candidate, bucketWidth);
            List<double[]> bucket = buckets.get(baseHash);
            boolean isDuplicate = false;
            if (bucket != null) {
                for (double[] existing : bucket) {
                    if (arePointsEqualWithinTolerance(candidate, existing, tolerance)) {
                        isDuplicate = true;
                        break;
                    }
                }
            }
            if (!isDuplicate) {
                // Verifica também o bucket deslocado por meia largura para cobrir fronteiras de célula
                long shiftedHash = computeShiftedGridHash(candidate, bucketWidth);
                List<double[]> shiftedBucket = buckets.get(shiftedHash);
                if (shiftedBucket != null) {
                    for (double[] existing : shiftedBucket) {
                        if (arePointsEqualWithinTolerance(candidate, existing, tolerance)) {
                            isDuplicate = true;
                            break;
                        }
                    }
                }
            }
            if (!isDuplicate) {
                uniqueCount++;
                buckets.computeIfAbsent(baseHash, k -> new ArrayList<>()).add(candidate);
                long shiftedHash = computeShiftedGridHash(candidate, bucketWidth);
                if (shiftedHash != baseHash) {
                    buckets.computeIfAbsent(shiftedHash, k -> new ArrayList<>()).add(candidate);
                }
            }
        }
        return uniqueCount;
    }

    private static long computeGridHash(double[] point, double bucketWidth) {
        long h = 1469598103934665603L;
        for (double v : point) {
            long cell = (long) Math.floor(v / bucketWidth);
            h ^= cell;
            h *= 1099511628211L;
        }
        return h;
    }

    private static long computeShiftedGridHash(double[] point, double bucketWidth) {
        long h = 1469598103934665603L ^ 0x5555555555555555L;
        double half = bucketWidth * 0.5;
        for (double v : point) {
            long cell = (long) Math.floor((v + half) / bucketWidth);
            h ^= cell;
            h *= 1099511628211L;
        }
        return h;
    }

    private static boolean arePointsEqualWithinTolerance(double[] a, double[] b, double tolerance) {
        for (int j = 0; j < a.length; j++) {
            if (Math.abs(a[j] - b[j]) > tolerance) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valida analiticamente a frente de Pareto do problema DTLZ1: hiperplano linear no primeiro
     * ortante dado por \(f_j \ge 0\) e \(\sum_{j=1}^{m} f_j = 0.5\) (Deb et al., 2002).
     *
     * @param front matriz de pontos da frente (\(\text{N} \times m\))
     * @param tolerance tolerância máxima admitida para \(|\sum_{j=1}^m f_j - 0.5|\) e não-negatividade
     * @return maior erro absoluto \(\max_{\mathbf{f}} \left|\sum_{j=1}^m f_j - 0.5\right|\) observado
     * @throws IllegalArgumentException se algum ponto possuir coordenada negativa além da tolerância
     *     ou se o erro da equação do hiperplano exceder {@code tolerance}
     */
    public static double validateDtlz1Analytical(double[][] front, double tolerance) {
        validateNonEmptyMatrix(front);
        double maxError = 0.0;
        for (int i = 0; i < front.length; i++) {
            double[] pt = front[i];
            double sum = 0.0;
            for (int j = 0; j < pt.length; j++) {
                if (pt[j] < -tolerance) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "DTLZ1 violado no ponto %d: coordenada f_%d = %g é negativa.",
                                    i, j + 1, pt[j]));
                }
                sum += pt[j];
            }
            double err = Math.abs(sum - 0.5);
            if (err > maxError) {
                maxError = err;
            }
            if (err > tolerance) {
                throw new IllegalArgumentException(
                        String.format(
                                "DTLZ1 violado no ponto %d: |sum(f_j) - 0.5| = %g excede tolerância %g.",
                                i, err, tolerance));
            }
        }
        return maxError;
    }

    /**
     * Valida analiticamente a frente de Pareto na esfera unitária do primeiro ortante (aplicável a
     * DTLZ2, DTLZ3, DTLZ4, DTLZ5 e DTLZ6): \(f_j \ge 0\) e \(\sum_{j=1}^{m} f_j^2 = 1.0\) (Deb et
     * al., 2002).
     *
     * @param front matriz de pontos da frente (\(\text{N} \times m\))
     * @param tolerance tolerância máxima admitida para \(|\sum_{j=1}^m f_j^2 - 1.0|\)
     * @return maior erro absoluto \(\max_{\mathbf{f}} \left|\sum_{j=1}^m f_j^2 - 1.0\right|\)
     * @throws IllegalArgumentException se algum ponto violar não-negatividade ou a equação da esfera
     */
    public static double validateUnitSphereAnalytical(double[][] front, double tolerance) {
        validateNonEmptyMatrix(front);
        double maxError = 0.0;
        for (int i = 0; i < front.length; i++) {
            double[] pt = front[i];
            double sumSq = 0.0;
            for (int j = 0; j < pt.length; j++) {
                if (pt[j] < -tolerance) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Esfera unitária violada no ponto %d: coordenada f_%d = %g é negativa.",
                                    i, j + 1, pt[j]));
                }
                sumSq += pt[j] * pt[j];
            }
            double err = Math.abs(sumSq - 1.0);
            if (err > maxError) {
                maxError = err;
            }
            if (err > tolerance) {
                throw new IllegalArgumentException(
                        String.format(
                                "Esfera unitária violada no ponto %d: |sum(f_j^2) - 1.0| = %g excede tolerância %g.",
                                i, err, tolerance));
            }
        }
        return maxError;
    }

    /**
     * Valida analiticamente a frente degenerada de DTLZ5 e DTLZ6: além de pertencer à esfera
     * unitária (\(\sum_{j=1}^m f_j^2 = 1.0, f_j \ge 0\)), os primeiros \(m - 1\) objetivos são
     * colineares (\(f_1 = f_2 = \dots = f_{m-1}\)) porque \(\theta_2 = \dots = \theta_{m-1} = \pi/4\)
     * na frente Pareto-ótima (\(g(\mathbf{x}_M) = 0\)).
     *
     * @param front matriz de pontos da frente (\(\text{N} \times m\))
     * @param tolerance tolerância máxima admitida para a esfera unitária e colinearidade
     * @return maior erro observado entre \(\left|\sum_{j=1}^m f_j^2 - 1.0\right|\) e
     *     \(\max_{1 \le j < m-1} |f_j - f_{j+1}|\)
     * @throws IllegalArgumentException se a condição esférica ou de degenerescência for violada
     */
    public static double validateDtlz5And6DegenerateAnalytical(double[][] front, double tolerance) {
        double maxSphereError = validateUnitSphereAnalytical(front, tolerance);
        double maxCollinearError = 0.0;
        for (int i = 0; i < front.length; i++) {
            double[] pt = front[i];
            for (int j = 0; j < pt.length - 2; j++) {
                double diff = Math.abs(pt[j] - pt[j + 1]);
                if (diff > maxCollinearError) {
                    maxCollinearError = diff;
                }
                if (diff > tolerance) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Degenerescência DTLZ5/6 violada no ponto %d: |f_%d - f_%d| = %g excede tolerância %g.",
                                    i, j + 1, j + 2, diff, tolerance));
                }
            }
        }
        return Math.max(maxSphereError, maxCollinearError);
    }

    /**
     * Valida analiticamente a frente desconexa do problema DTLZ7 (Deb et al., 2002): na frente
     * Pareto-ótima (\(g(\mathbf{x}_M) = 0 \implies 1 + g^* = 2\)), tem-se \(f_j \in [0, 1]\) para
     * \(j = 1, \dots, m-1\) e
     * \[
     *   f_m = 2 \left( m - \sum_{j=1}^{m-1} \frac{f_j}{2} \bigl(1 + \sin(3\pi f_j)\bigr) \right).
     * \]
     *
     * @param front matriz de pontos da frente (\(\text{N} \times m\))
     * @param tolerance tolerância máxima admitida para \(|f_m - f_m^{\text{analítico}}|\)
     * @return maior erro absoluto \(\max_{\mathbf{f}} |f_m - f_m^{\text{analítico}}|\)
     * @throws IllegalArgumentException se \(f_j \notin [0, 1]\) (\(j < m\)) ou se o erro de \(f_m\)
     *     exceder {@code tolerance}
     */
    public static double validateDtlz7Analytical(double[][] front, double tolerance) {
        validateNonEmptyMatrix(front);
        double maxError = 0.0;
        for (int i = 0; i < front.length; i++) {
            double[] pt = front[i];
            int m = pt.length;
            double hSum = 0.0;
            for (int j = 0; j < m - 1; j++) {
                double fj = pt[j];
                if (fj < -tolerance || fj > 1.0 + tolerance) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "DTLZ7 violado no ponto %d: f_%d = %g fora de [0, 1].",
                                    i, j + 1, fj));
                }
                hSum += (fj / 2.0) * (1.0 + Math.sin(3.0 * Math.PI * fj));
            }
            double expectedFm = 2.0 * (m - hSum);
            double err = Math.abs(pt[m - 1] - expectedFm);
            if (err > maxError) {
                maxError = err;
            }
            if (err > tolerance) {
                throw new IllegalArgumentException(
                        String.format(
                                "DTLZ7 violado no ponto %d: |f_%d - expected| = %g excede tolerância %g.",
                                i, m, err, tolerance));
            }
        }
        return maxError;
    }

    /**
     * Valida analiticamente a frente de Pareto côncava dos problemas WFG4 a WFG9 (Huband et al.,
     * 2006): hiper-elipsoide no primeiro ortante com semi-eixos \(S_j = 2j\) (\(j = 1, \dots, m\)):
     * \[
     *   \sum_{j=1}^{m} \left(\frac{f_j}{2j}\right)^2 = 1, \quad f_j \ge 0.
     * \]
     *
     * @param front matriz de pontos da frente (\(\text{N} \times m\))
     * @param tolerance tolerância máxima admitida para \(\left|\sum_{j=1}^m (f_j / (2j))^2 - 1.0\right|\)
     * @return maior erro absoluto \(\max_{\mathbf{f}} \left|\sum_{j=1}^m (f_j / (2j))^2 - 1.0\right|\)
     * @throws IllegalArgumentException se algum ponto possuir coordenada negativa além da tolerância
     *     ou violar a equação do hiper-elipsoide
     */
    public static double validateWfgConcaveEllipsoid(double[][] front, double tolerance) {
        validateNonEmptyMatrix(front);
        double maxError = 0.0;
        for (int i = 0; i < front.length; i++) {
            double[] pt = front[i];
            double sumNormalizedSq = 0.0;
            for (int j = 0; j < pt.length; j++) {
                double radius = 2.0 * (j + 1);
                if (pt[j] < -tolerance) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Hiper-elipsoide WFG violado no ponto %d: f_%d = %g é negativo.",
                                    i, j + 1, pt[j]));
                }
                double ratio = pt[j] / radius;
                sumNormalizedSq += ratio * ratio;
            }
            double err = Math.abs(sumNormalizedSq - 1.0);
            if (err > maxError) {
                maxError = err;
            }
            if (err > tolerance) {
                throw new IllegalArgumentException(
                        String.format(
                                "Hiper-elipsoide WFG violado no ponto %d: |sum((f_j/2j)^2) - 1.0| = %g excede tolerância %g.",
                                i, err, tolerance));
            }
        }
        return maxError;
    }

    private static void validateNonEmptyMatrix(double[][] front) {
        if (front == null || front.length == 0 || front[0] == null || front[0].length < 2) {
            throw new IllegalArgumentException(
                    "A matriz da frente de Pareto não pode ser nula, vazia ou ter dimensão < 2.");
        }
    }
}

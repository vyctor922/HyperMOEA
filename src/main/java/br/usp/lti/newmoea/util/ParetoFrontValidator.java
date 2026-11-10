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

    // =========================================================================
    // EXTENSÕES T07: Geração matemática e empírica opcional de frentes DTLZ
    // =========================================================================

    /** Nome da propriedade JVM para habilitar geração completa de frentes analíticas (desativada por padrão). */
    public static final String GENERATE_ALL_REFERENCE_FRONTS_PROPERTY = "generateAllReferenceFronts";

    /** Nome da propriedade JVM para habilitar geração empírica controlada de frentes (desativada por padrão). */
    public static final String GENERATE_EMPIRICAL_REFERENCE_FRONTS_PROPERTY =
            "generateEmpiricalReferenceFronts";

    /** Diretório padrão de saída para frentes analíticas derivadas em T07 (nunca escreve em {@code pareto_fronts/csv/}). */
    public static final Path DEFAULT_ANALYTICAL_PF_GEN_DIR =
            Path.of("resources", "referenceFronts", "pf_gen", "analytical");

    /** Diretório padrão de saída para frentes empíricas derivadas em T07 (nunca escreve em {@code pareto_fronts/csv/}). */
    public static final Path DEFAULT_EMPIRICAL_PF_GEN_DIR =
            Path.of("resources", "referenceFronts", "pf_gen", "empirical");

    /** Limite superior de \(f_j\) no primeiro componente desconexo de DTLZ7 (\(x_1^* \approx 0.251411836088917\)). */
    public static final double DTLZ7_INTERVAL_1_MAX = 0.251411836088917;

    /** Limite inferior de \(f_j\) no segundo componente desconexo de DTLZ7 (\(x_{\text{jump}} \approx 0.631626530700061\)). */
    public static final double DTLZ7_INTERVAL_2_MIN = 0.631626530700061;

    /** Limite superior de \(f_j\) no segundo componente desconexo de DTLZ7 (\(x_3^* \approx 0.859400856644724\)). */
    public static final double DTLZ7_INTERVAL_2_MAX = 0.859400856644724;

    /** Valor máximo de \(\psi(x) = \frac{x}{2}(1 + \sin(3\pi x))\) em \([0,1]\), atingido em \(x_3^*\). */
    public static final double DTLZ7_PSI_GLOBAL_MAX = 0.846497817249211;

    /**
     * Verifica se a geração analítica completa de frentes de referência está habilitada via
     * propriedade JVM {@code -DgenerateAllReferenceFronts=true}. Por padrão, retorna {@code false}.
     *
     * @return {@code true} se habilitada, {@code false} por padrão
     */
    public static boolean isGenerateAllReferenceFrontsEnabled() {
        return Boolean.parseBoolean(
                System.getProperty(GENERATE_ALL_REFERENCE_FRONTS_PROPERTY, "false"));
    }

    /**
     * Verifica se a geração empírica controlada de frentes de referência está habilitada via
     * propriedade JVM {@code -DgenerateEmpiricalReferenceFronts=true}. Por padrão, retorna {@code false}.
     *
     * @return {@code true} se habilitada, {@code false} por padrão
     */
    public static boolean isGenerateEmpiricalReferenceFrontsEnabled() {
        return Boolean.parseBoolean(
                System.getProperty(GENERATE_EMPIRICAL_REFERENCE_FRONTS_PROPERTY, "false"));
    }

    /**
     * Conta o número de pontos dominados em uma matriz de objetivos sob convenção de minimização:
     * \(\mathbf{u}\) domina \(\mathbf{v}\) sse \(\forall j, u_j \le v_j + \text{tol}\) e
     * \(\exists j, u_j < v_j - \text{tol}\).
     *
     * @param front matriz de pontos (\(\text{N} \times m\))
     * @param tolerance tolerância numérica para comparação de dominância
     * @return número de linhas dominadas por ao menos outra linha da matriz
     */
    public static int countDominatedPoints(double[][] front, double tolerance) {
        validateNonEmptyMatrix(front);
        int n = front.length;
        int dominatedCount = 0;
        for (int i = 0; i < n; i++) {
            double[] candidate = front[i];
            boolean dominated = false;
            for (int k = 0; k < n; k++) {
                if (i == k) {
                    continue;
                }
                if (dominates(front[k], candidate, tolerance)) {
                    dominated = true;
                    break;
                }
            }
            if (dominated) {
                dominatedCount++;
            }
        }
        return dominatedCount;
    }

    /**
     * Valida que nenhum ponto da frente é dominado por outro ponto do mesmo conjunto.
     *
     * @param front matriz de pontos (\(\text{N} \times m\))
     * @param tolerance tolerância numérica
     * @throws IllegalArgumentException se houver qualquer ponto dominado
     */
    public static void assertNonDominatedFront(double[][] front, double tolerance) {
        int dominated = countDominatedPoints(front, tolerance);
        if (dominated > 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "A frente contém %d ponto(s) dominado(s) (total=%d).",
                            dominated, front.length));
        }
    }

    /**
     * Filtra uma matriz de pontos mantendo apenas as soluções mutuamente não-dominadas e únicas.
     *
     * @param candidates matriz de pontos candidatos (\(\text{N} \times m\))
     * @param duplicateTolerance tolerância para remoção de duplicatas
     * @return submatriz contendo apenas pontos não-dominados e distintos
     */
    public static double[][] filterNonDominatedAndUnique(
            double[][] candidates, double duplicateTolerance) {
        validateNonEmptyMatrix(candidates);
        List<double[]> uniqueList = new ArrayList<>();
        for (double[] pt : candidates) {
            boolean dup = false;
            for (double[] existing : uniqueList) {
                if (arePointsEqualWithinTolerance(pt, existing, duplicateTolerance)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                uniqueList.add(pt.clone());
            }
        }
        List<double[]> nonDominated = new ArrayList<>();
        for (int i = 0; i < uniqueList.size(); i++) {
            double[] u = uniqueList.get(i);
            boolean isDom = false;
            for (int j = 0; j < uniqueList.size(); j++) {
                if (i == j) {
                    continue;
                }
                if (dominates(uniqueList.get(j), u, duplicateTolerance)) {
                    isDom = true;
                    break;
                }
            }
            if (!isDom) {
                nonDominated.add(u);
            }
        }
        return nonDominated.toArray(double[][]::new);
    }

    private static boolean dominates(double[] a, double[] b, double tol) {
        boolean strictlyBetterInAtLeastOne = false;
        for (int j = 0; j < a.length; j++) {
            if (a[j] > b[j] + tol) {
                return false;
            }
            if (a[j] < b[j] - tol) {
                strictlyBetterInAtLeastOne = true;
            }
        }
        return strictlyBetterInAtLeastOne;
    }

    /**
     * Retorna o valor mínimo analítico exato do último objetivo \(f_m\) na frente de Pareto de DTLZ7:
     * \(f_m^{\min}(m) = 2 \cdot (m - (m - 1) \cdot \psi(x_3^*))\), onde \(x_3^* \approx 0.859400856644724\).
     *
     * @param m número de objetivos (\(m \ge 2\))
     * @return valor mínimo analítico de \(f_m\)
     */
    public static double dtlz7AnalyticalMinLastObjective(int m) {
        if (m < 2) {
            throw new IllegalArgumentException("m deve ser >= 2, mas foi: " + m);
        }
        return 2.0 * (m - (m - 1) * DTLZ7_PSI_GLOBAL_MAX);
    }

    /**
     * Retorna o valor máximo analítico exato do último objetivo \(f_m\) na frente de Pareto de DTLZ7:
     * \(f_m^{\max}(m) = 2m\), atingido quando \(f_1 = \dots = f_{m-1} = 0\).
     *
     * @param m número de objetivos (\(m \ge 2\))
     * @return valor máximo analítico de \(f_m = 2m\)
     */
    public static double dtlz7AnalyticalMaxLastObjective(int m) {
        if (m < 2) {
            throw new IllegalArgumentException("m deve ser >= 2, mas foi: " + m);
        }
        return 2.0 * m;
    }

    /**
     * Verifica se um valor \(f_j\) (\(j \in \{1, \dots, m-1\}\)) pertence aos intervalos Pareto-admissíveis
     * dos dois ramos desconexos de DTLZ7: \(I_0 = [0, x_1^*]\) ou \(I_1 = [x_{\text{jump}}, x_3^*]\).
     *
     * @param fj coordenada do objetivo \(j < m\)
     * @param tolerance tolerância nas extremidades dos intervalos
     * @return {@code true} se \(f_j \in I_0 \cup I_1\)
     */
    public static boolean isDtlz7CoordinateInParetoIntervals(double fj, double tolerance) {
        boolean inFirst = fj >= -tolerance && fj <= DTLZ7_INTERVAL_1_MAX + tolerance;
        boolean inSecond =
                fj >= DTLZ7_INTERVAL_2_MIN - tolerance && fj <= DTLZ7_INTERVAL_2_MAX + tolerance;
        return inFirst || inSecond;
    }

    /**
     * Valida analiticamente a frente desconexa de DTLZ7 exigindo, além da equação de \(f_m\), que todas
     * as coordenadas \(f_1, \dots, f_{m-1}\) pertençam estritamente aos intervalos Pareto-admissíveis
     * \(I_0 \cup I_1\) e que nenhum ponto seja dominado.
     *
     * @param front matriz de pontos (\(\text{N} \times m\))
     * @param tolerance tolerância numérica
     * @return erro máximo da equação de \(f_m\)
     */
    public static double validateDtlz7StrictParetoFront(double[][] front, double tolerance) {
        double eqErr = validateDtlz7Analytical(front, tolerance);
        for (int i = 0; i < front.length; i++) {
            double[] pt = front[i];
            for (int j = 0; j < pt.length - 1; j++) {
                if (!isDtlz7CoordinateInParetoIntervals(pt[j], tolerance)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Ponto %d de DTLZ7 possui coordenada f_%d = %.8f fora das regiões Pareto-admissíveis ([0, %.6f] U [%.6f, %.6f]).",
                                    i,
                                    j + 1,
                                    pt[j],
                                    DTLZ7_INTERVAL_1_MAX,
                                    DTLZ7_INTERVAL_2_MIN,
                                    DTLZ7_INTERVAL_2_MAX));
                }
            }
        }
        assertNonDominatedFront(front, 1e-9);
        return eqErr;
    }

    /**
     * Gera matematicamente uma frente de Pareto analítica validada para qualquer problema
     * {@code DTLZ1} a {@code DTLZ7} e número de objetivos \(m \in \{2, 3, 5, 8, 10, 15\}\):
     * <ul>
     *   <li><b>DTLZ1</b>: pontos do simplex de Das &amp; Dennis / Deb &amp; Jain multiplicados por \(0.5\)
     *       (\(\sum_{j=1}^m f_j = 0.5\));</li>
     *   <li><b>DTLZ2, DTLZ3, DTLZ4</b>: projeção radial dos pontos do simplex sobre a esfera unitária
     *       positiva (\(\mathbf{f} = \mathbf{w} / \|\mathbf{w}\|_2 \implies \sum_{j=1}^m f_j^2 = 1\));</li>
     *   <li><b>DTLZ5, DTLZ6</b>: curva unidimensional degenerada exata da implementação do jMetal 7.8
     *       em que \(\theta_1 \in [0, \pi/2]\) e \(\theta_2 = \dots = \theta_{m-1} = \pi/4\) quando \(g = 0\),
     *       produzindo \(N\) pontos distintos e mutuamente não-dominados com \(\sum_{j=1}^m f_j^2 = 1\);</li>
     *   <li><b>DTLZ7</b>: amostragem determinística sobre os componentes desconexos admissíveis
     *       \((I_0 \cup I_1)^{m-1}\) com \(f_m = 2(m - \sum_{j=1}^{m-1} \psi(f_j))\).</li>
     * </ul>
     * Todos os conjuntos gerados passam por verificação de equação analítica e não-dominância antes de
     * serem retornados.
     *
     * @param problemName nome do problema ({@code "DTLZ1"} a {@code "DTLZ7"})
     * @param m número de objetivos (\(m \ge 2\))
     * @param outerDivisions divisões externas \(H_1\) (para DTLZ1–DTLZ4)
     * @param innerDivisions divisões internas \(H_2\) (para DTLZ1–DTLZ4)
     * @return matriz \(\text{N} \times m\) com a frente de Pareto matematicamente validada
     */
    public static double[][] generateAnalyticalDtlzFront(
            String problemName, int m, int outerDivisions, int innerDivisions) {
        if (problemName == null || problemName.isBlank()) {
            throw new IllegalArgumentException("O nome do problema DTLZ não pode ser nulo ou vazio.");
        }
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        String normalized = problemName.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("_")) {
            normalized = normalized.substring(0, normalized.indexOf('_'));
        }

        double[][] front =
                switch (normalized) {
                    case "DTLZ1" -> generateAnalyticalDtlz1(m, outerDivisions, innerDivisions);
                    case "DTLZ2", "DTLZ3", "DTLZ4" ->
                            generateAnalyticalDtlz2To4(m, outerDivisions, innerDivisions);
                    case "DTLZ5", "DTLZ6" -> {
                        int targetPoints =
                                Math.max(
                                        200,
                                        ReferenceSetValidator.expectedTwoLayerPoints(
                                                m, outerDivisions, innerDivisions));
                        yield generateAnalyticalDtlz5And6Curve(m, targetPoints);
                    }
                    case "DTLZ7" -> {
                        int targetPoints =
                                Math.max(
                                        200,
                                        ReferenceSetValidator.expectedTwoLayerPoints(
                                                m, outerDivisions, innerDivisions));
                        yield generateAnalyticalDtlz7Front(m, targetPoints);
                    }
                    default ->
                            throw new IllegalArgumentException(
                                    "Problema DTLZ não reconhecido para geração analítica: "
                                            + problemName);
                };

        validateAnalyticalDtlzFrontByProblem(normalized, front, 1e-6);
        return front;
    }

    /**
     * Valida uma frente de Pareto segundo a equação analítica específica do problema DTLZ1–DTLZ7,
     * além de verificar ausência de duplicatas e ausência de pontos dominados.
     *
     * @param problemName nome do problema ({@code "DTLZ1"} a {@code "DTLZ7"})
     * @param front matriz de pontos (\(\text{N} \times m\))
     * @param tolerance tolerância numérica
     */
    public static void validateAnalyticalDtlzFrontByProblem(
            String problemName, double[][] front, double tolerance) {
        validateNonEmptyMatrix(front);
        String normalized = problemName.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("_")) {
            normalized = normalized.substring(0, normalized.indexOf('_'));
        }
        switch (normalized) {
            case "DTLZ1" -> validateDtlz1Analytical(front, tolerance);
            case "DTLZ2", "DTLZ3", "DTLZ4" -> validateUnitSphereAnalytical(front, tolerance);
            case "DTLZ5", "DTLZ6" -> validateDtlz5And6JMetalCurveAnalytical(front, tolerance);
            case "DTLZ7" -> validateDtlz7StrictParetoFront(front, tolerance);
            default ->
                    throw new IllegalArgumentException(
                            "Problema DTLZ não suportado na validação analítica: " + problemName);
        }
        int unique = countUniquePoints(front, DEFAULT_DUPLICATE_TOLERANCE);
        if (unique != front.length) {
            throw new IllegalArgumentException(
                    String.format(
                            "Frente analítica de %s contém %d duplicata(s).",
                            normalized, front.length - unique));
        }
        assertNonDominatedFront(front, DEFAULT_DUPLICATE_TOLERANCE);
    }

    /**
     * Valida analiticamente a curva degenerada exata de {@code DTLZ5.java} e {@code DTLZ6.java} no
     * jMetal 7.8 para qualquer \(m \ge 2\):
     * <ul>
     *   <li>Todos os pontos pertencem à esfera unitária positiva: \(\sum_{j=1}^m f_j^2 = 1, f_j \ge 0\);</li>
     *   <li>Os dois primeiros objetivos são iguais: \(f_1 = f_2\) (quando \(m \ge 3\));</li>
     *   <li>Para cada \(j \in \{3, \dots, m-1\}\), como \(\theta_2 = \dots = \theta_{m-1} = \pi/4\),
     *       tem-se \(f_j = f_1 \cdot (\sqrt{2})^{j-2}\).</li>
     * </ul>
     *
     * @param front matriz de pontos (\(\text{N} \times m\))
     * @param tolerance tolerância numérica
     * @return maior erro observado entre a equação da esfera e as relações de proporcionalidade
     */
    public static double validateDtlz5And6JMetalCurveAnalytical(
            double[][] front, double tolerance) {
        double maxErr = validateUnitSphereAnalytical(front, tolerance);
        double sqrt2 = Math.sqrt(2.0);
        for (int i = 0; i < front.length; i++) {
            double[] pt = front[i];
            int m = pt.length;
            if (m >= 3) {
                double diff12 = Math.abs(pt[0] - pt[1]);
                maxErr = Math.max(maxErr, diff12);
                if (diff12 > tolerance) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "DTLZ5/6 violado no ponto %d: |f_1 - f_2| = %g > %g.",
                                    i, diff12, tolerance));
                }
                double expected = pt[0];
                for (int j = 2; j < m - 1; j++) {
                    expected *= sqrt2;
                    double diffJ = Math.abs(pt[j] - expected);
                    maxErr = Math.max(maxErr, diffJ);
                    if (diffJ > tolerance) {
                        throw new IllegalArgumentException(
                                String.format(
                                        "DTLZ5/6 violado no ponto %d: |f_%d - f_1*(sqrt(2))^%d| = %g > %g.",
                                        i, j + 1, j - 1, diffJ, tolerance));
                    }
                }
            }
        }
        return maxErr;
    }

    private static double[][] generateAnalyticalDtlz1(
            int m, int outerDivisions, int innerDivisions) {
        double[][] simplex =
                ReferenceSetValidator.generateCanonicalTwoLayers(m, outerDivisions, innerDivisions);
        double[][] front = new double[simplex.length][m];
        for (int i = 0; i < simplex.length; i++) {
            for (int j = 0; j < m; j++) {
                front[i][j] = 0.5 * simplex[i][j];
            }
        }
        return front;
    }

    private static double[][] generateAnalyticalDtlz2To4(
            int m, int outerDivisions, int innerDivisions) {
        double[][] simplex =
                ReferenceSetValidator.generateCanonicalTwoLayers(m, outerDivisions, innerDivisions);
        double[][] front = new double[simplex.length][m];
        for (int i = 0; i < simplex.length; i++) {
            double normSq = 0.0;
            for (int j = 0; j < m; j++) {
                normSq += simplex[i][j] * simplex[i][j];
            }
            double norm = Math.sqrt(normSq);
            for (int j = 0; j < m; j++) {
                front[i][j] = simplex[i][j] / norm;
            }
        }
        return front;
    }

    /**
     * Gera a curva degenerada 1D exata de {@code DTLZ5} e {@code DTLZ6} conforme implementada em
     * {@code org.uma.jmetal.problem.multiobjective.dtlz.DTLZ5} e {@code DTLZ6} no jMetal 7.8 quando
     * \(g(\mathbf{x}_M) = 0\): \(\theta_0 = \frac{\pi}{2} x_0 \in [0, \pi/2]\) e
     * \(\theta_1 = \dots = \theta_{m-2} = \frac{\pi}{4}\).
     *
     * @param m número de objetivos (\(m \ge 2\))
     * @param numPoints quantidade de pontos distintos a amostrar ao longo da curva (\(numPoints \ge 2\))
     * @return matriz \(\text{numPoints} \times m\) na esfera unitária positiva
     */
    public static double[][] generateAnalyticalDtlz5And6Curve(int m, int numPoints) {
        if (m < 2 || numPoints < 2) {
            throw new IllegalArgumentException(
                    String.format("Parâmetros inválidos para DTLZ5/6: m=%d, numPoints=%d", m, numPoints));
        }
        double[][] front = new double[numPoints][m];
        double invSqrt2 = Math.cos(Math.PI / 4.0);
        for (int i = 0; i < numPoints; i++) {
            double x0 = (double) i / (double) (numPoints - 1);
            double theta0 = 0.5 * Math.PI * x0;
            double cos0 = Math.cos(theta0);
            double sin0 = Math.sin(theta0);

            for (int j = 0; j < m; j++) {
                double val = 1.0;
                int cosCount = m - j - 1;
                if (cosCount >= 1) {
                    val *= cos0;
                    for (int c = 1; c < cosCount; c++) {
                        val *= invSqrt2;
                    }
                }
                if (j > 0) {
                    int sinIndex = m - j - 1;
                    val *= (sinIndex == 0) ? sin0 : invSqrt2;
                }
                front[i][j] = val;
            }
        }
        return front;
    }

    /**
     * Gera matematicamente uma frente de Pareto desconexa para {@code DTLZ7} amostrada exclusivamente
     * dentro dos intervalos Pareto-admissíveis \(I_0 = [0, x_1^*]\) e \(I_1 = [x_{\text{jump}}, x_3^*]\)
     * para cada \(f_1, \dots, f_{m-1}\), cobrindo múltiplos componentes desconexos e garantindo
     * não-dominância mútua estrita.
     *
     * @param m número de objetivos (\(m \ge 2\))
     * @param targetPoints quantidade aproximada de pontos desejada
     * @return matriz de pontos na frente desconexa de DTLZ7
     */
    public static double[][] generateAnalyticalDtlz7Front(int m, int targetPoints) {
        if (m < 2 || targetPoints < 2) {
            throw new IllegalArgumentException(
                    String.format("Parâmetros inválidos para DTLZ7: m=%d, targetPoints=%d", m, targetPoints));
        }
        List<double[]> points = new ArrayList<>(targetPoints);
        int d = m - 1;

        if (m <= 3) {
            // Amostragem em grade cartesiana equilibrada sobre os 2^(m-1) componentes desconexos
            int pointsPerAxis = Math.max(4, (int) Math.ceil(Math.pow(targetPoints, 1.0 / d)));
            int halfA = pointsPerAxis / 2;
            int halfB = pointsPerAxis - halfA;
            double[] axisValues = new double[pointsPerAxis];
            for (int i = 0; i < halfA; i++) {
                axisValues[i] =
                        (halfA == 1)
                                ? 0.0
                                : (DTLZ7_INTERVAL_1_MAX * i) / (double) (halfA - 1);
            }
            for (int i = 0; i < halfB; i++) {
                axisValues[halfA + i] =
                        (halfB == 1)
                                ? DTLZ7_INTERVAL_2_MAX
                                : DTLZ7_INTERVAL_2_MIN
                                        + ((DTLZ7_INTERVAL_2_MAX - DTLZ7_INTERVAL_2_MIN) * i)
                                                / (double) (halfB - 1);
            }
            double[] current = new double[d];
            buildDtlz7CartesianRecursive(m, 0, axisValues, current, points);
        } else {
            // Para m >= 5 (2^(m-1) >= 16 componentes), combina os extremos canônicos e pontos
            // estratificados de baixa discrepância (Halton) sobre os componentes desconexos
            double[] minCorner = new double[m];
            Arrays.fill(minCorner, 0.0);
            minCorner[m - 1] = evaluateDtlz7LastObjective(minCorner, m);
            points.add(minCorner);

            double[] maxPsiCorner = new double[m];
            for (int j = 0; j < d; j++) {
                maxPsiCorner[j] = DTLZ7_INTERVAL_2_MAX;
            }
            maxPsiCorner[m - 1] = evaluateDtlz7LastObjective(maxPsiCorner, m);
            points.add(maxPsiCorner);

            int totalComponents = 1 << Math.min(d, 14);
            int[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47};
            for (int idx = 0; idx < targetPoints - 2; idx++) {
                int compMask = (int) (((long) idx * 2654435761L) & (totalComponents - 1));
                double[] pt = new double[m];
                for (int j = 0; j < d; j++) {
                    boolean secondBranch = ((compMask >> (j % 14)) & 1) == 1;
                    double u = radicalInverse(idx + 1, primes[j % primes.length]);
                    if (!secondBranch) {
                        pt[j] = u * DTLZ7_INTERVAL_1_MAX;
                    } else {
                        pt[j] =
                                DTLZ7_INTERVAL_2_MIN
                                        + u * (DTLZ7_INTERVAL_2_MAX - DTLZ7_INTERVAL_2_MIN);
                    }
                }
                pt[m - 1] = evaluateDtlz7LastObjective(pt, m);
                points.add(pt);
            }
        }

        double[][] raw = points.toArray(double[][]::new);
        return filterNonDominatedAndUnique(raw, DEFAULT_DUPLICATE_TOLERANCE);
    }

    private static void buildDtlz7CartesianRecursive(
            int m, int depth, double[] axisValues, double[] current, List<double[]> out) {
        if (depth == m - 1) {
            double[] pt = new double[m];
            System.arraycopy(current, 0, pt, 0, m - 1);
            pt[m - 1] = evaluateDtlz7LastObjective(pt, m);
            out.add(pt);
            return;
        }
        for (double v : axisValues) {
            current[depth] = v;
            buildDtlz7CartesianRecursive(m, depth + 1, axisValues, current, out);
        }
    }

    private static double evaluateDtlz7LastObjective(double[] pt, int m) {
        double hSum = 0.0;
        for (int j = 0; j < m - 1; j++) {
            double fj = pt[j];
            hSum += (fj / 2.0) * (1.0 + Math.sin(3.0 * Math.PI * fj));
        }
        return 2.0 * (m - hSum);
    }

    private static double radicalInverse(int index, int base) {
        double result = 0.0;
        double f = 1.0 / base;
        int i = index;
        while (i > 0) {
            result += f * (i % base);
            i /= base;
            f /= base;
        }
        return result;
    }

    /**
     * Relatório da geração e persistência controlada de uma frente de Pareto derivada (analítica ou empírica).
     *
     * @param outputFile arquivo CSV gerado (ou {@code null} quando desativado)
     * @param metadataFile arquivo de metadados gerado para frentes empíricas (ou {@code null})
     * @param mode modo de geração ({@code "ANALYTICAL"} ou {@code "EMPIRICAL"})
     * @param enabled verdadeiro se a opção estava habilitada
     * @param written verdadeiro se um novo arquivo foi gravado
     * @param summary resumo de validação estrutural da frente
     */
    public record GeneratedFrontReport(
            Path outputFile,
            Path metadataFile,
            String mode,
            boolean enabled,
            boolean written,
            FrontValidationSummary summary) {}

    /**
     * Gera e persiste uma frente analítica validada para um problema DTLZ em {@code outputDir}
     * (ex.: {@code resources/referenceFronts/pf_gen/analytical/DTLZ1.5D.csv}) sem jamais modificar
     * ou sobrescrever {@code pareto_fronts/csv/}.
     *
     * @param problemName nome do problema ({@code "DTLZ1"} a {@code "DTLZ7"})
     * @param m número de objetivos
     * @param outerDivisions divisões externas H1
     * @param innerDivisions divisões internas H2
     * @param outputDir diretório de saída (proibido ser {@code pareto_fronts/csv/})
     * @param enabled se falso, não grava arquivos
     * @return {@link GeneratedFrontReport}
     */
    public static GeneratedFrontReport generateAndSaveAnalyticalDtlzFront(
            String problemName,
            int m,
            int outerDivisions,
            int innerDivisions,
            Path outputDir,
            boolean enabled) {
        if (!enabled) {
            return new GeneratedFrontReport(null, null, "ANALYTICAL", false, false, null);
        }
        assertNotProtectedParetoFrontsDir(outputDir);
        String normalized = problemName.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("_")) {
            normalized = normalized.substring(0, normalized.indexOf('_'));
        }
        double[][] front = generateAnalyticalDtlzFront(normalized, m, outerDivisions, innerDivisions);
        String fileName = String.format("%s.%dD.csv", normalized, m);
        Path targetFile = writeFrontCsvWithoutOverwrite(outputDir, fileName, front);
        FrontValidationSummary summary = validateParetoFrontFile(targetFile, m);
        validateAnalyticalDtlzFrontByProblem(normalized, summary.points(), 1e-5);
        return new GeneratedFrontReport(targetFile, null, "ANALYTICAL", true, true, summary);
    }

    /**
     * Consolida de forma controlada uma frente de Pareto empírica a partir de arquivos de aproximação
     * de execuções prévias ({@code FUN*.csv}), filtrando apenas soluções não-dominadas e únicas e
     * gravando o resultado com identificação explícita {@code .empirical.csv} e arquivo de metadados
     * em {@code resources/referenceFronts/pf_gen/empirical/}.
     * <p>
     * Regra metodológica estrita: {@code evaluationFrontFiles} não pode compartilhar nenhum arquivo
     * com {@code sourceFunFiles} (nunca use a mesma execução simultaneamente para criar e avaliar a
     * referência empírica).
     *
     * @param problemName nome do problema (ex.: {@code "DTLZ1"})
     * @param m número de objetivos
     * @param sourceFunFiles arquivos {@code FUN*.csv} de execuções prévias independentes
     * @param evaluationFrontFiles arquivos de avaliação (devem ser disjuntos de {@code sourceFunFiles})
     * @param metadataDescription descrição de algoritmos, runs, sementes e parâmetros de origem
     * @param outputDir diretório de saída (ex.: {@code resources/referenceFronts/pf_gen/empirical/})
     * @param enabled se falso, retorna sem gravar
     * @return {@link GeneratedFrontReport}
     */
    public static GeneratedFrontReport generateEmpiricalReferenceFront(
            String problemName,
            int m,
            List<Path> sourceFunFiles,
            List<Path> evaluationFrontFiles,
            String metadataDescription,
            Path outputDir,
            boolean enabled) {
        if (!enabled) {
            return new GeneratedFrontReport(null, null, "EMPIRICAL", false, false, null);
        }
        assertNotProtectedParetoFrontsDir(outputDir);
        if (sourceFunFiles == null || sourceFunFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "A lista de arquivos de origem para frente empírica não pode ser nula ou vazia.");
        }
        if (evaluationFrontFiles != null && !evaluationFrontFiles.isEmpty()) {
            for (Path src : sourceFunFiles) {
                Path normSrc = src.toAbsolutePath().normalize();
                for (Path eval : evaluationFrontFiles) {
                    if (eval != null && normSrc.equals(eval.toAbsolutePath().normalize())) {
                        throw new IllegalArgumentException(
                                "Violação metodológica: proibido usar o mesmo arquivo de execução ("
                                        + src
                                        + ") simultaneamente para construir e avaliar a frente empírica.");
                    }
                }
            }
        }

        List<double[]> allCandidates = new ArrayList<>();
        for (Path funFile : sourceFunFiles) {
            FrontValidationSummary part = validateParetoFrontFile(funFile, m);
            for (double[] pt : part.points()) {
                allCandidates.add(pt);
            }
        }
        double[][] consolidated =
                filterNonDominatedAndUnique(
                        allCandidates.toArray(double[][]::new), DEFAULT_DUPLICATE_TOLERANCE);
        assertNonDominatedFront(consolidated, DEFAULT_DUPLICATE_TOLERANCE);

        String normalized = problemName.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("_")) {
            normalized = normalized.substring(0, normalized.indexOf('_'));
        }
        String fileName = String.format("%s.%dD.empirical.csv", normalized, m);
        Path targetFile = writeFrontCsvWithoutOverwrite(outputDir, fileName, consolidated);

        Path metaFile =
                outputDir.resolve(targetFile.getFileName().toString() + ".meta.txt");
        String metaContent =
                String.format(
                        java.util.Locale.US,
                        "type=empirical%nproblem=%s%nobjectives=%d%ntotalCandidates=%d%nnonDominatedPoints=%d%nsourceFiles=%s%nmetadata=%s%n",
                        normalized,
                        m,
                        allCandidates.size(),
                        consolidated.length,
                        sourceFunFiles,
                        metadataDescription == null ? "unspecified" : metadataDescription);
        try {
            Files.writeString(metaFile, metaContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao gravar metadados da frente empírica: " + metaFile, e);
        }

        FrontValidationSummary summary = validateParetoFrontFile(targetFile, m);
        return new GeneratedFrontReport(targetFile, metaFile, "EMPIRICAL", true, true, summary);
    }

    private static void assertNotProtectedParetoFrontsDir(Path outputDir) {
        if (outputDir == null) {
            throw new IllegalArgumentException("O diretório de saída não pode ser nulo.");
        }
        Path normalizedOut = outputDir.toAbsolutePath().normalize();
        Path protectedDir = Path.of("pareto_fronts", "csv").toAbsolutePath().normalize();
        if (normalizedOut.startsWith(protectedDir)) {
            throw new IllegalArgumentException(
                    "Proibido gravar ou sobrescrever arquivos no diretório protegido pareto_fronts/csv/: "
                            + outputDir);
        }
    }

    private static Path writeFrontCsvWithoutOverwrite(
            Path outputDir, String baseFileName, double[][] front) {
        StringBuilder sb = new StringBuilder();
        for (double[] row : front) {
            for (int j = 0; j < row.length; j++) {
                if (j > 0) {
                    sb.append(',');
                }
                sb.append(String.format(java.util.Locale.US, "%.15f", row[j]));
            }
            sb.append(System.lineSeparator());
        }
        String content = sb.toString();
        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve(baseFileName);
            if (Files.exists(target)) {
                String existing = Files.readString(target, StandardCharsets.UTF_8);
                if (existing.equals(content)) {
                    return target;
                }
                int suffix = 1;
                String stem =
                        baseFileName.endsWith(".csv")
                                ? baseFileName.substring(0, baseFileName.length() - 4)
                                : baseFileName;
                Path candidate;
                do {
                    candidate = outputDir.resolve(String.format("%s_v%d.csv", stem, suffix));
                    if (Files.exists(candidate)
                            && Files.readString(candidate, StandardCharsets.UTF_8).equals(content)) {
                        return candidate;
                    }
                    suffix++;
                } while (Files.exists(candidate));
                Files.writeString(
                        candidate,
                        content,
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE);
                return candidate;
            }
            Files.writeString(
                    target,
                    content,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao gravar frente derivada em: " + outputDir, e);
        }
    }
}

package br.usp.lti.newmoea.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilitário independente para validação matemática de conjuntos de pontos de referência
 * (Das & Dennis / Deb & Jain), simulação de {@code reference-calc.java}, comparação de
 * conjuntos independente da ordem das linhas e validação estrita de arquivos de pesos do MOEA/DD.
 */
public final class ReferenceSetValidator {

    /** Tolerância numérica padrão para validações de simplex e equivalência de conjuntos. */
    public static final double DEFAULT_TOLERANCE = 1e-6;

    /** Limite de iterações para proteger a simulação de {@code reference-calc} contra laços infinitos. */
    private static final int MAX_SIMULATION_STEPS = 50_000_000;

    private ReferenceSetValidator() {
        // Classe utilitária
    }

    /** Veredito da comparação entre dois conjuntos de vetores. */
    public enum Verdict {
        EQUIVALENT,
        NOT_EQUIVALENT,
        INCONCLUSIVE
    }

    /**
     * Relatório de validação intrínseca de um conjunto de vetores no simplex.
     *
     * @param vectorCount quantidade de vetores
     * @param dimension dimensionalidade uniforme (ou -1 se inconsistente)
     * @param allFinite verdadeiro se todas as coordenadas forem finitas
     * @param allNonNegative verdadeiro se todas as coordenadas forem >= -tolerance
     * @param allStrictlyPositive verdadeiro se todas as coordenadas forem > 0
     * @param minCoordinate menor coordenada observada
     * @param maxCoordinate maior coordenada observada
     * @param minSum menor soma de componentes observada entre os vetores
     * @param maxSum maior soma de componentes observada entre os vetores
     * @param maxSumDeviationFromOne maior desvio absoluto |sum - 1.0|
     * @param duplicateCount quantidade de vetores duplicados (distância euclidiana <= duplicateTolerance)
     * @param minPairwiseEuclideanDistance menor distância euclidiana entre dois vetores distintos
     * @param validSimplex verdadeiro se satisfizer todas as propriedades de simplex dentro das tolerâncias
     */
    public record SetValidationReport(
            int vectorCount,
            int dimension,
            boolean allFinite,
            boolean allNonNegative,
            boolean allStrictlyPositive,
            double minCoordinate,
            double maxCoordinate,
            double minSum,
            double maxSum,
            double maxSumDeviationFromOne,
            int duplicateCount,
            double minPairwiseEuclideanDistance,
            boolean validSimplex) {}

    /**
     * Relatório de comparação entre dois conjuntos de vetores, independente da ordem das linhas.
     *
     * @param countA número de vetores no conjunto A
     * @param countB número de vetores no conjunto B
     * @param dimensionA dimensionalidade do conjunto A
     * @param dimensionB dimensionalidade do conjunto B
     * @param duplicatesA número de duplicatas internas em A
     * @param duplicatesB número de duplicatas internas em B
     * @param matchedCount número de pares casados dentro da tolerância
     * @param unmatchedFromA número de vetores de A sem correspondência em B dentro da tolerância
     * @param unmatchedFromB número de vetores de B sem correspondência em A dentro da tolerância
     * @param maxCoordinateError maior erro absoluto por coordenada na bijeção entre A e B
     * @param maxEuclideanError maior distância euclidiana na bijeção entre A e B
     * @param tolerance tolerância utilizada para o veredito
     * @param verdict veredito da comparação ({@link Verdict#EQUIVALENT}, {@link Verdict#NOT_EQUIVALENT}, {@link Verdict#INCONCLUSIVE})
     */
    public record SetComparisonReport(
            int countA,
            int countB,
            int dimensionA,
            int dimensionB,
            int duplicatesA,
            int duplicatesB,
            int matchedCount,
            int unmatchedFromA,
            int unmatchedFromB,
            double maxCoordinateError,
            double maxEuclideanError,
            double tolerance,
            Verdict verdict) {}

    /**
     * Calcula o coeficiente binomial C(n, k).
     *
     * @param n total de elementos (n >= 0)
     * @param k elementos escolhidos (0 <= k <= n)
     * @return valor exato de C(n, k)
     */
    public static long binomialCoefficient(int n, int k) {
        if (n < 0 || k < 0 || k > n) {
            throw new IllegalArgumentException(
                    String.format("Parâmetros inválidos para coeficiente binomial: n=%d, k=%d", n, k));
        }
        int kk = Math.min(k, n - k);
        long result = 1L;
        for (int i = 1; i <= kk; i++) {
            result = Math.multiplyExact(result, n - kk + i) / i;
        }
        return result;
    }

    /**
     * Calcula a quantidade canônica de pontos no simplex-lattice de Das & Dennis (1998) para uma camada:
     * |H| = C(H + m - 1, m - 1).
     *
     * @param m número de objetivos (m >= 2)
     * @param divisions número de divisões H (divisions >= 1)
     * @return quantidade esperada de pontos
     */
    public static int expectedSimplexPoints(int m, int divisions) {
        validateSimplexParameters(m, divisions, 0);
        return Math.toIntExact(binomialCoefficient(divisions + m - 1, m - 1));
    }

    /**
     * Calcula a quantidade canônica de pontos no simplex-lattice de duas camadas (Deb & Jain, 2014).
     *
     * @param m número de objetivos (m >= 2)
     * @param outerDivisions divisões da camada externa H1 (H1 >= 1)
     * @param innerDivisions divisões da camada interna H2 (H2 >= 0; 0 indica apenas camada externa)
     * @return quantidade total esperada de pontos
     */
    public static int expectedTwoLayerPoints(int m, int outerDivisions, int innerDivisions) {
        validateSimplexParameters(m, outerDivisions, innerDivisions);
        int outer = expectedSimplexPoints(m, outerDivisions);
        int inner = innerDivisions > 0 ? expectedSimplexPoints(m, innerDivisions) : 0;
        return Math.addExact(outer, inner);
    }

    /**
     * Valida os parâmetros de configuração de camadas de Das & Dennis / Deb & Jain.
     *
     * @param m número de objetivos (deve ser >= 2)
     * @param outerDivisions divisões externas H1 (deve ser >= 1)
     * @param innerDivisions divisões internas H2 (deve ser >= 0)
     */
    public static void validateSimplexParameters(int m, int outerDivisions, int innerDivisions) {
        if (m < 2) {
            throw new IllegalArgumentException(
                    "O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        if (outerDivisions < 1) {
            throw new IllegalArgumentException(
                    "O número de divisões externas (H1) deve ser >= 1, mas foi: " + outerDivisions);
        }
        if (innerDivisions < 0) {
            throw new IllegalArgumentException(
                    "O número de divisões internas (H2) deve ser >= 0, mas foi: " + innerDivisions);
        }
    }

    /**
     * Gera canonicamente a camada única do simplex-lattice de Das & Dennis (1998) com H divisões.
     * Cada ponto possui coordenadas em {0/H, 1/H, ..., H/H} cuja soma exata é 1.0.
     *
     * @param m número de objetivos (m >= 2)
     * @param divisions número de divisões H (H >= 1)
     * @return matriz de pontos [N][m]
     */
    public static double[][] generateCanonicalSingleLayer(int m, int divisions) {
        validateSimplexParameters(m, divisions, 0);
        List<double[]> points = new ArrayList<>(expectedSimplexPoints(m, divisions));
        int[] current = new int[m];
        generateLatticeRecursive(m, divisions, divisions, 0, current, points);
        return points.toArray(double[][]::new);
    }

    private static void generateLatticeRecursive(
            int m, int divisions, int remaining, int index, int[] current, List<double[]> out) {
        if (index == m - 1) {
            current[index] = remaining;
            double[] point = new double[m];
            for (int i = 0; i < m; i++) {
                point[i] = (double) current[i] / (double) divisions;
            }
            out.add(point);
            return;
        }
        for (int v = remaining; v >= 0; v--) {
            current[index] = v;
            generateLatticeRecursive(m, divisions, remaining - v, index + 1, current, out);
        }
    }

    /**
     * Gera canonicamente a camada interna contraída segundo Deb & Jain (2014):
     * x_i^{inner} = 0.5 * x_i^{(H2)} + 1 / (2 * m).
     *
     * @param m número de objetivos (m >= 2)
     * @param innerDivisions divisões internas H2 (H2 >= 1)
     * @return matriz de pontos [N_inner][m]
     */
    public static double[][] generateCanonicalInnerLayer(int m, int innerDivisions) {
        if (innerDivisions < 1) {
            throw new IllegalArgumentException(
                    "O número de divisões internas (H2) deve ser >= 1 para gerar camada interna, mas foi: "
                            + innerDivisions);
        }
        double[][] base = generateCanonicalSingleLayer(m, innerDivisions);
        double shift = 1.0 / (2.0 * m);
        double[][] inner = new double[base.length][m];
        for (int i = 0; i < base.length; i++) {
            for (int j = 0; j < m; j++) {
                inner[i][j] = 0.5 * base[i][j] + shift;
            }
        }
        return inner;
    }

    /**
     * Gera o conjunto canônico de uma ou duas camadas segundo Das & Dennis (1998) e Deb & Jain (2014).
     * Se {@code innerDivisions == 0}, retorna apenas a camada externa; se {@code innerDivisions > 0},
     * concatena a camada externa (H1) e a camada interna contraída (H2).
     *
     * @param m número de objetivos (m >= 2)
     * @param outerDivisions divisões da camada externa H1 (H1 >= 1)
     * @param innerDivisions divisões da camada interna H2 (H2 >= 0)
     * @return matriz de pontos [N][m]
     */
    public static double[][] generateCanonicalTwoLayers(int m, int outerDivisions, int innerDivisions) {
        validateSimplexParameters(m, outerDivisions, innerDivisions);
        double[][] outer = generateCanonicalSingleLayer(m, outerDivisions);
        if (innerDivisions == 0) {
            return outer;
        }
        double[][] inner = generateCanonicalInnerLayer(m, innerDivisions);
        double[][] combined = new double[outer.length + inner.length][m];
        System.arraycopy(outer, 0, combined, 0, outer.length);
        System.arraycopy(inner, 0, combined, outer.length, inner.length);
        return combined;
    }

    /**
     * Gera a variante de 2 camadas implementada internamente pela classe {@code NSGAIII} do jMetal 7.8
     * (linhas 58–71 de {@code org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII}), na qual a
     * camada interna sofre a transformação {@code (x_i + 1.0) / (m + 1.0)} em vez de {@code 0.5 * x_i + 1/(2m)}.
     *
     * @param m número de objetivos (m >= 2)
     * @param outerDivisions divisões da camada externa H1 (H1 >= 1)
     * @param innerDivisions divisões da camada interna H2 (H2 >= 0)
     * @return matriz de pontos [N][m]
     */
    public static double[][] generateJMetalNsgaIIITwoLayersFormula(
            int m, int outerDivisions, int innerDivisions) {
        validateSimplexParameters(m, outerDivisions, innerDivisions);
        double[][] outer = generateCanonicalSingleLayer(m, outerDivisions);
        if (innerDivisions == 0) {
            return outer;
        }
        double[][] innerBase = generateCanonicalSingleLayer(m, innerDivisions);
        double[][] combined = new double[outer.length + innerBase.length][m];
        System.arraycopy(outer, 0, combined, 0, outer.length);
        for (int i = 0; i < innerBase.length; i++) {
            for (int j = 0; j < m; j++) {
                combined[outer.length + i][j] = (innerBase[i][j] + 1.0) / (m + 1.0);
            }
        }
        return combined;
    }

    /**
     * Gera o conjunto de Das & Dennis / Deb & Jain substituindo coordenadas zero (0.0) por
     * {@code zeroReplacement} (ex.: {@code 1e-5}) no simplex base antes da contração da camada interna:
     * <ul>
     *   <li>Camada externa: {@code w_i = (x_i == 0.0) ? zeroReplacement : x_i}</li>
     *   <li>Camada interna: {@code w_i = 0.5 * ((x_i == 0.0) ? zeroReplacement : x_i) + 1.0 / (2.0 * m)}</li>
     * </ul>
     * Esta é a fórmula exata que gerou os arquivos em {@code MOEAD_Weights/}.
     *
     * @param m número de objetivos (m >= 2)
     * @param outerDivisions divisões externas H1 (H1 >= 1)
     * @param innerDivisions divisões internas H2 (H2 >= 0)
     * @param zeroReplacement valor positivo substituto para coordenadas zero (ex.: 1e-5)
     * @return matriz de vetores [N][m]
     */
    public static double[][] generateZeroReplacedCanonical(
            int m, int outerDivisions, int innerDivisions, double zeroReplacement) {
        validateSimplexParameters(m, outerDivisions, innerDivisions);
        if (!Double.isFinite(zeroReplacement) || zeroReplacement < 0.0) {
            throw new IllegalArgumentException(
                    "zeroReplacement deve ser finito e não negativo, mas foi: " + zeroReplacement);
        }
        double[][] outer = generateCanonicalSingleLayer(m, outerDivisions);
        for (int i = 0; i < outer.length; i++) {
            for (int j = 0; j < m; j++) {
                if (outer[i][j] == 0.0) {
                    outer[i][j] = zeroReplacement;
                }
            }
        }
        if (innerDivisions == 0) {
            return outer;
        }
        double[][] innerBase = generateCanonicalSingleLayer(m, innerDivisions);
        double shift = 1.0 / (2.0 * m);
        double[][] combined = new double[outer.length + innerBase.length][m];
        System.arraycopy(outer, 0, combined, 0, outer.length);
        for (int i = 0; i < innerBase.length; i++) {
            for (int j = 0; j < m; j++) {
                double val = innerBase[i][j] == 0.0 ? zeroReplacement : innerBase[i][j];
                combined[outer.length + i][j] = 0.5 * val + shift;
            }
        }
        return combined;
    }

    /**
     * Reproduz fielmente em memória o algoritmo de {@code C:\Users\vyctor922\Desktop\reference-calc.java},
     * incluindo {@code arredondar} com {@code fator = 100000}, {@code margemErro = 1 - 0.9999},
     * {@code limInf}, {@code limSup} e {@code intervalo}.
     *
     * @param dimensoes número de dimensões (m)
     * @param divisoes número de divisões (H)
     * @param layers número de camadas
     * @return matriz de vetores impressos por {@code reference-calc}
     */
    public static double[][] simulateReferenceCalc(int dimensoes, int divisoes, int layers) {
        if (dimensoes < 1 || divisoes < 1 || layers < 1) {
            throw new IllegalArgumentException(
                    String.format(
                            "Parâmetros inválidos para simulateReferenceCalc: dimensoes=%d, divisoes=%d, layers=%d",
                            dimensoes, divisoes, layers));
        }
        double margemErro = 1.0 - 0.9999;
        List<double[]> output = new ArrayList<>();

        for (int l = 0; l < layers; l++) {
            double limInf;
            double limSup;
            if (l > 0) {
                limInf = arredondarReferenceCalc(1.0 / (dimensoes + l));
                limSup = arredondarReferenceCalc(1.0 - limInf);
            } else {
                limInf = 0.0;
                limSup = 1.0;
            }

            double intervalo = arredondarReferenceCalc((1.0 - (dimensoes * limInf)) / (double) divisoes);
            if (intervalo <= 0.0) {
                throw new IllegalStateException(
                        "Intervalo não positivo em simulateReferenceCalc: " + intervalo);
            }
            limSup += margemErro;

            double[] vetor = new double[dimensoes];
            Arrays.fill(vetor, limInf);

            int steps = 0;
            while (vetor[0] < limSup) {
                while (vetor[vetor.length - 1] < limSup) {
                    if (++steps > MAX_SIMULATION_STEPS) {
                        throw new IllegalStateException(
                                "Limite de iterações excedido em simulateReferenceCalc");
                    }
                    double somaCol = 0.0;
                    for (int j = 0; j < vetor.length; j++) {
                        somaCol += vetor[j];
                    }

                    if (somaCol > 1.0 - margemErro && somaCol < 1.0 + margemErro) {
                        double[] printed = new double[vetor.length];
                        for (int j = 0; j < vetor.length; j++) {
                            printed[j] = arredondarReferenceCalc(vetor[j]);
                        }
                        output.add(printed);
                    }

                    vetor[vetor.length - 1] += intervalo;
                }

                for (int i = vetor.length - 1; i > 0; i--) {
                    double somaCol = 0.0;
                    for (int j = 0; j < vetor.length; j++) {
                        somaCol += vetor[j];
                    }

                    if (vetor[i] >= limSup || somaCol > 1.0 + margemErro) {
                        vetor[i] = limInf;
                        vetor[i - 1] += intervalo;
                    }
                }
            }
        }
        return output.toArray(double[][]::new);
    }

    /** Função exata {@code arredondar(double)} de {@code reference-calc.java}. */
    public static double arredondarReferenceCalc(double valor) {
        int fator = 100000;
        return (Math.round(valor * fator)) / (double) fator;
    }

    /**
     * Avalia as propriedades matemáticas de um conjunto de vetores no simplex (contagem, dimensão,
     * finitude, não negatividade, soma unitária e ausência de duplicatas).
     *
     * @param vectors matriz de vetores [N][m]
     * @param expectedCount quantidade esperada de vetores
     * @param expectedDimension dimensionalidade esperada (m)
     * @param sumTolerance tolerância para |sum(x_i) - 1.0|
     * @param duplicateTolerance tolerância de distância euclidiana abaixo da qual dois vetores são duplicatas
     * @return {@link SetValidationReport} detalhado
     */
    public static SetValidationReport validateSimplexSet(
            double[][] vectors,
            int expectedCount,
            int expectedDimension,
            double sumTolerance,
            double duplicateTolerance) {
        if (vectors == null || vectors.length == 0) {
            return new SetValidationReport(
                    0, 0, false, false, false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, Double.NaN, false);
        }
        int count = vectors.length;
        int dim = vectors[0] == null ? -1 : vectors[0].length;
        boolean consistentDim = dim > 0;
        boolean allFinite = true;
        boolean allNonNegative = true;
        boolean allStrictlyPositive = true;
        double minCoord = Double.POSITIVE_INFINITY;
        double maxCoord = Double.NEGATIVE_INFINITY;
        double minSum = Double.POSITIVE_INFINITY;
        double maxSum = Double.NEGATIVE_INFINITY;
        double maxSumDev = 0.0;

        for (double[] row : vectors) {
            if (row == null || row.length != dim) {
                consistentDim = false;
                allFinite = false;
                continue;
            }
            double sum = 0.0;
            for (double v : row) {
                if (!Double.isFinite(v)) {
                    allFinite = false;
                } else {
                    if (v < -sumTolerance) {
                        allNonNegative = false;
                    }
                    if (v <= 0.0) {
                        allStrictlyPositive = false;
                    }
                    minCoord = Math.min(minCoord, v);
                    maxCoord = Math.max(maxCoord, v);
                    sum += v;
                }
            }
            if (Double.isFinite(sum)) {
                minSum = Math.min(minSum, sum);
                maxSum = Math.max(maxSum, sum);
                maxSumDev = Math.max(maxSumDev, Math.abs(sum - 1.0));
            }
        }

        int effectiveDim = consistentDim ? dim : -1;
        int duplicates = 0;
        double minPairwiseDist = Double.POSITIVE_INFINITY;
        if (consistentDim && allFinite) {
            boolean[] isDuplicate = new boolean[count];
            for (int i = 0; i < count; i++) {
                for (int j = i + 1; j < count; j++) {
                    double dist = euclideanDistance(vectors[i], vectors[j]);
                    minPairwiseDist = Math.min(minPairwiseDist, dist);
                    if (dist <= duplicateTolerance) {
                        if (!isDuplicate[j]) {
                            isDuplicate[j] = true;
                            duplicates++;
                        }
                    }
                }
            }
        }

        boolean valid = count == expectedCount
                && effectiveDim == expectedDimension
                && allFinite
                && allNonNegative
                && maxSumDev <= sumTolerance
                && duplicates == 0;

        return new SetValidationReport(
                count,
                effectiveDim,
                allFinite,
                allNonNegative,
                allStrictlyPositive,
                minCoord,
                maxCoord,
                minSum,
                maxSum,
                maxSumDev,
                duplicates,
                minPairwiseDist,
                valid);
    }

    /**
     * Valida estritamente um conjunto de vetores no simplex, lançando {@link IllegalArgumentException}
     * caso qualquer propriedade falhe.
     *
     * @param vectors matriz de vetores [N][m]
     * @param expectedCount quantidade esperada de vetores
     * @param expectedDimension dimensionalidade esperada (m)
     * @param tolerance tolerância numérica
     */
    public static void assertValidSimplexSet(
            double[][] vectors, int expectedCount, int expectedDimension, double tolerance) {
        if (vectors == null) {
            throw new IllegalArgumentException("Conjunto de vetores não pode ser nulo.");
        }
        SetValidationReport report =
                validateSimplexSet(vectors, expectedCount, expectedDimension, tolerance, tolerance);
        if (!report.validSimplex()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Conjunto inválido no simplex: count=%d (esperado=%d), dim=%d (esperado=%d), "
                                    + "allFinite=%b, allNonNegative=%b, maxSumDev=%.3e (tol=%.3e), duplicates=%d",
                            report.vectorCount(),
                            expectedCount,
                            report.dimension(),
                            expectedDimension,
                            report.allFinite(),
                            report.allNonNegative(),
                            report.maxSumDeviationFromOne(),
                            tolerance,
                            report.duplicateCount()));
        }
    }

    /**
     * Conta duplicatas internas em um conjunto de vetores usando distância euclidiana <= duplicateTolerance.
     *
     * @param set matriz de vetores
     * @param duplicateTolerance tolerância para considerar dois vetores duplicados
     * @return quantidade de vetores duplicados
     */
    public static int countDuplicates(double[][] set, double duplicateTolerance) {
        if (set == null || set.length <= 1) {
            return 0;
        }
        int duplicates = 0;
        boolean[] flagged = new boolean[set.length];
        for (int i = 0; i < set.length; i++) {
            if (set[i] == null) {
                continue;
            }
            for (int j = i + 1; j < set.length; j++) {
                if (set[j] == null || set[i].length != set[j].length) {
                    continue;
                }
                if (euclideanDistance(set[i], set[j]) <= duplicateTolerance) {
                    if (!flagged[j]) {
                        flagged[j] = true;
                        duplicates++;
                    }
                }
            }
        }
        return duplicates;
    }

    /**
     * Compara dois conjuntos de vetores independentemente da ordem das linhas.
     * Realiza casamento bijetivo guloso por menor distância euclidiana global (ordenando todos os pares
     * ou buscando iterativamente o par não casado de menor distância euclidiana), garantindo invariância
     * completa à permutação das linhas de A e B.
     *
     * @param setA primeiro conjunto de vetores
     * @param setB segundo conjunto de vetores
     * @param tolerance tolerância numérica para erro máximo por coordenada
     * @return {@link SetComparisonReport} com métricas detalhadas e veredito
     */
    public static SetComparisonReport compareSetsOrderIndependent(
            double[][] setA, double[][] setB, double tolerance) {
        if (setA == null || setB == null) {
            return new SetComparisonReport(
                    setA == null ? 0 : setA.length,
                    setB == null ? 0 : setB.length,
                    -1,
                    -1,
                    0,
                    0,
                    0,
                    setA == null ? 0 : setA.length,
                    setB == null ? 0 : setB.length,
                    Double.NaN,
                    Double.NaN,
                    tolerance,
                    Verdict.INCONCLUSIVE);
        }

        int countA = setA.length;
        int countB = setB.length;
        int dimA = extractUniformDimension(setA);
        int dimB = extractUniformDimension(setB);

        if (dimA < 0 || dimB < 0 || !allFinite(setA) || !allFinite(setB)) {
            return new SetComparisonReport(
                    countA,
                    countB,
                    dimA,
                    dimB,
                    0,
                    0,
                    0,
                    countA,
                    countB,
                    Double.NaN,
                    Double.NaN,
                    tolerance,
                    Verdict.INCONCLUSIVE);
        }

        double dupTolPow = Math.min(tolerance, 1e-7);
        int duplicatesA = countDuplicates(setA, dupTolPow);
        int duplicatesB = countDuplicates(setB, dupTolPow);

        if (countA == 0 || countB == 0) {
            Verdict v = (countA == countB) ? Verdict.EQUIVALENT : Verdict.NOT_EQUIVALENT;
            return new SetComparisonReport(
                    countA,
                    countB,
                    dimA,
                    dimB,
                    duplicatesA,
                    duplicatesB,
                    0,
                    countA,
                    countB,
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    tolerance,
                    v);
        }

        if (dimA != dimB) {
            return new SetComparisonReport(
                    countA,
                    countB,
                    dimA,
                    dimB,
                    duplicatesA,
                    duplicatesB,
                    0,
                    countA,
                    countB,
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    tolerance,
                    Verdict.NOT_EQUIVALENT);
        }

        int maxPairs = Math.min(countA, countB);
        boolean[] usedA = new boolean[countA];
        boolean[] usedB = new boolean[countB];

        double[][] distMatrix = new double[countA][countB];
        double[][] coordErrMatrix = new double[countA][countB];
        for (int i = 0; i < countA; i++) {
            for (int j = 0; j < countB; j++) {
                distMatrix[i][j] = euclideanDistance(setA[i], setB[j]);
                coordErrMatrix[i][j] = chebyshevDistance(setA[i], setB[j]);
            }
        }

        int matchedWithinTolerance = 0;
        double maxCoordErr = 0.0;
        double maxEuclidErr = 0.0;

        for (int pair = 0; pair < maxPairs; pair++) {
            int bestI = -1;
            int bestJ = -1;
            double bestDist = Double.POSITIVE_INFINITY;
            double bestCoord = Double.POSITIVE_INFINITY;

            for (int i = 0; i < countA; i++) {
                if (usedA[i]) {
                    continue;
                }
                for (int j = 0; j < countB; j++) {
                    if (usedB[j]) {
                        continue;
                    }
                    double d = distMatrix[i][j];
                    double c = coordErrMatrix[i][j];
                    if (d < bestDist || (d == bestDist && c < bestCoord)) {
                        bestDist = d;
                        bestCoord = c;
                        bestI = i;
                        bestJ = j;
                    }
                }
            }

            if (bestI < 0 || bestJ < 0) {
                break;
            }
            usedA[bestI] = true;
            usedB[bestJ] = true;
            maxCoordErr = Math.max(maxCoordErr, bestCoord);
            maxEuclidErr = Math.max(maxEuclidErr, bestDist);
            if (bestCoord <= tolerance) {
                matchedWithinTolerance++;
            }
        }

        int unmatchedFromA = countA - matchedWithinTolerance;
        int unmatchedFromB = countB - matchedWithinTolerance;

        Verdict verdict =
                (countA == countB
                                && duplicatesA == 0
                                && duplicatesB == 0
                                && unmatchedFromA == 0
                                && unmatchedFromB == 0
                                && maxCoordErr <= tolerance)
                        ? Verdict.EQUIVALENT
                        : Verdict.NOT_EQUIVALENT;

        return new SetComparisonReport(
                countA,
                countB,
                dimA,
                dimB,
                duplicatesA,
                duplicatesB,
                matchedWithinTolerance,
                unmatchedFromA,
                unmatchedFromB,
                maxCoordErr,
                maxEuclidErr,
                tolerance,
                verdict);
    }

    /**
     * Valida rigorosamente um arquivo de vetores de pesos do MOEA/DD (ex.: {@code MOEAD_Weights/W{m}D_{N}.dat}).
     * Verifica existência, legibilidade, quantidade de linhas igual à população esperada, dimensionalidade
     * igual ao número de objetivos, finitude, não negatividade, soma próxima de 1.0 dentro de {@code sumTolerance}
     * e ausência de duplicatas dentro de {@code duplicateTolerance}.
     *
     * @param weightFile caminho do arquivo {@code .dat}
     * @param expectedObjectives número esperado de objetivos (colunas)
     * @param expectedPopulationSize tamanho esperado da população (linhas)
     * @param sumTolerance tolerância para |sum(w_i) - 1.0| (ex.: 1e-3 devido à substituição 0 -> 1e-5)
     * @param duplicateTolerance tolerância euclidiana para detecção de vetores duplicados (ex.: 1e-7)
     * @return matriz {@code double[expectedPopulationSize][expectedObjectives]} com os pesos validados
     * @throws IllegalArgumentException se o arquivo for ausente, ilegível ou violar qualquer critério
     */
    public static double[][] validateMoeadWeightFile(
            Path weightFile,
            int expectedObjectives,
            int expectedPopulationSize,
            double sumTolerance,
            double duplicateTolerance) {
        if (weightFile == null) {
            throw new IllegalArgumentException("O caminho do arquivo de pesos não pode ser nulo.");
        }
        if (expectedObjectives < 2) {
            throw new IllegalArgumentException(
                    "O número esperado de objetivos deve ser >= 2, mas foi: " + expectedObjectives);
        }
        if (expectedPopulationSize < 1) {
            throw new IllegalArgumentException(
                    "O tamanho esperado da população deve ser >= 1, mas foi: " + expectedPopulationSize);
        }
        if (!Files.exists(weightFile) || !Files.isRegularFile(weightFile) || !Files.isReadable(weightFile)) {
            throw new IllegalArgumentException(
                    "Arquivo de pesos do MOEA/DD inexistente ou ilegível: " + weightFile);
        }

        List<String> rawLines;
        try {
            rawLines = Files.readAllLines(weightFile);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Falha ao ler arquivo de pesos do MOEA/DD: " + weightFile, e);
        }

        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : rawLines) {
            if (line != null && !line.trim().isEmpty()) {
                nonEmptyLines.add(line.trim());
            }
        }

        if (nonEmptyLines.size() != expectedPopulationSize) {
            throw new IllegalArgumentException(
                    String.format(
                            "Quantidade incorreta de vetores em '%s': esperado %d linhas, mas encontrado %d.",
                            weightFile.getFileName(), expectedPopulationSize, nonEmptyLines.size()));
        }

        double[][] weights = new double[expectedPopulationSize][expectedObjectives];
        for (int i = 0; i < nonEmptyLines.size(); i++) {
            String[] tokens = nonEmptyLines.get(i).split("\\s+");
            if (tokens.length != expectedObjectives) {
                throw new IllegalArgumentException(
                        String.format(
                                "Dimensionalidade incorreta em '%s' na linha %d: esperado %d colunas, mas encontrado %d.",
                                weightFile.getFileName(), i + 1, expectedObjectives, tokens.length));
            }
            double sum = 0.0;
            for (int j = 0; j < expectedObjectives; j++) {
                double val;
                try {
                    val = Double.parseDouble(tokens[j]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Valor não numérico '%s' em '%s' (linha %d, coluna %d).",
                                    tokens[j], weightFile.getFileName(), i + 1, j + 1),
                            e);
                }
                if (!Double.isFinite(val)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Valor não finito (%s) em '%s' (linha %d, coluna %d).",
                                    tokens[j], weightFile.getFileName(), i + 1, j + 1));
                }
                if (val < 0.0) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Peso negativo (%.6e) em '%s' (linha %d, coluna %d).",
                                    val, weightFile.getFileName(), i + 1, j + 1));
                }
                weights[i][j] = val;
                sum += val;
            }
            if (Math.abs(sum - 1.0) > sumTolerance) {
                throw new IllegalArgumentException(
                        String.format(
                                "Soma dos pesos inválida (%.8f, desvio=%.3e > tol=%.3e) em '%s' na linha %d.",
                                sum, Math.abs(sum - 1.0), sumTolerance, weightFile.getFileName(), i + 1));
            }
        }

        for (int i = 0; i < weights.length; i++) {
            for (int j = i + 1; j < weights.length; j++) {
                double dist = euclideanDistance(weights[i], weights[j]);
                if (dist <= duplicateTolerance) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Vetores de pesos duplicados ou quase duplicados em '%s' nas linhas %d e %d (distância=%.3e <= tol=%.3e).",
                                    weightFile.getFileName(), i + 1, j + 1, dist, duplicateTolerance));
                }
            }
        }

        return weights;
    }

    /** Calcula a distância euclidiana (norma L2) entre dois vetores de mesma dimensão. */
    public static double euclideanDistance(double[] a, double[] b) {
        double sumSq = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq);
    }

    /** Calcula a distância de Chebyshev (norma L-infinito / erro máximo por coordenada) entre dois vetores. */
    public static double chebyshevDistance(double[] a, double[] b) {
        double maxDiff = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = Math.abs(a[i] - b[i]);
            if (diff > maxDiff) {
                maxDiff = diff;
            }
        }
        return maxDiff;
    }

    private static int extractUniformDimension(double[][] set) {
        if (set.length == 0) {
            return 0;
        }
        if (set[0] == null || set[0].length == 0) {
            return -1;
        }
        int dim = set[0].length;
        for (double[] row : set) {
            if (row == null || row.length != dim) {
                return -1;
            }
        }
        return dim;
    }

    private static boolean allFinite(double[][] set) {
        for (double[] row : set) {
            if (row == null) {
                return false;
            }
            for (double v : row) {
                if (!Double.isFinite(v)) {
                    return false;
                }
            }
        }
        return true;
    }
}

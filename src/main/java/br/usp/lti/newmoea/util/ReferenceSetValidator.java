package br.usp.lti.newmoea.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Utilitário independente para validação matemática de conjuntos de pontos de referência
 * (Das & Dennis / Deb & Jain), simulação de {@code reference-calc.java}, comparação de
 * conjuntos independente da ordem das linhas, validação estrita de arquivos de pesos do MOEA/DD
 * e geração controlada de variantes com substituição opcional de zeros (T11).
 */
public final class ReferenceSetValidator {

    /** Tolerância numérica padrão para validações de simplex e equivalência de conjuntos. */
    public static final double DEFAULT_TOLERANCE = 1e-6;

    /** Nome da propriedade JVM para habilitar substituição opcional de zeros (desativada por padrão). */
    public static final String REPLACE_ZERO_WEIGHTS_PROPERTY = "replaceZeroWeights";

    /** Nome da propriedade JVM para habilitar persistência opcional de pontos do NSGA-III (desativada por padrão). */
    public static final String PERSIST_REFERENCE_POINTS_PROPERTY = "persistReferencePoints";

    /** Nome da propriedade JVM para habilitar persistência opcional de direções do RVEA (desativada por padrão). */
    public static final String PERSIST_REFERENCE_DIRECTIONS_PROPERTY = "persistReferenceDirections";

    /** Diretório padrão para persistência opcional de pontos de referência do NSGA-III (T07). */
    public static final Path DEFAULT_NSGAIII_PERSIST_DIR =
            Path.of("resources", "referencePoints", "nsgaiii");

    /** Diretório padrão para persistência opcional de direções de referência do RVEA (T07). */
    public static final Path DEFAULT_RVEA_PERSIST_DIR =
            Path.of("resources", "referencePoints", "rvea");

    /** Valor substituto padrão para coordenadas zero quando habilitado (1.0e-5). */
    public static final double DEFAULT_ZERO_REPLACEMENT = 1.0e-5;

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
     * Relatório da substituição opcional e controlada de coordenadas zero ({@code 0.0 -> epsilon})
     * em arquivo de pesos (T11).
     *
     * @param sourceFile arquivo original inspecionado (intocado)
     * @param outputFile arquivo gerado em {@code weights_gen/} (ou {@code null} quando a geração está desativada)
     * @param enabled verdadeiro se a substituição estava habilitada
     * @param generated verdadeiro se o arquivo de saída foi efetivamente gravado
     * @param vectorCount quantidade de vetores preservada
     * @param dimension dimensionalidade preservada (m)
     * @param zeroReplacement valor substituto aplicado (ex.: 1.0e-5)
     * @param replacedComponentCount número total de componentes iguais a 0.0 substituídos por {@code zeroReplacement}
     * @param vectorSumsAfterReplacement soma exata de cada vetor após a substituição
     * @param minSum menor soma observada após a substituição
     * @param maxSum maior soma observada após a substituição
     * @param validationReport relatório de validação intrínseca do conjunto resultante
     */
    public record ZeroReplacementReport(
            Path sourceFile,
            Path outputFile,
            boolean enabled,
            boolean generated,
            int vectorCount,
            int dimension,
            double zeroReplacement,
            int replacedComponentCount,
            double[] vectorSumsAfterReplacement,
            double minSum,
            double maxSum,
            SetValidationReport validationReport) {}

    /**
     * Relatório de persistência opcional de pontos de referência (NSGA-III) ou direções (RVEA) em T07.
     *
     * @param outputFile caminho do arquivo CSV persistido (ou {@code null} quando desativado)
     * @param enabled verdadeiro se a persistência estava habilitada
     * @param written verdadeiro se um novo arquivo foi gravado nesta chamada
     * @param reusedIdenticalExisting verdadeiro se o arquivo já existia com conteúdo idêntico e foi preservado sem sobrescrita
     * @param suffixedDueToConflict verdadeiro se o arquivo base já existia com conteúdo distinto e foi gravado com sufixo de proteção
     * @param vectorCount quantidade de vetores
     * @param dimension dimensionalidade dos vetores (m)
     */
    public record ReferencePersistenceReport(
            Path outputFile,
            boolean enabled,
            boolean written,
            boolean reusedIdenticalExisting,
            boolean suffixedDueToConflict,
            int vectorCount,
            int dimension) {}

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
     * Gera o conjunto de duas camadas não contraídas exatamente como nos arquivos oficiais do jMetal 7.8
     * para {@code W8D_156.dat} e {@code W10D_275.dat}, onde a segunda camada {@code H2} é concatenada
     * diretamente sem a contração centrípeta {@code 0.5 * x_i + 1/(2m)} de Deb & Jain (2014), ocasionando
     * repetição exata dos {@code m} vértices extremos {@code e_1, ..., e_m}.
     *
     * @param m número de objetivos (m >= 2)
     * @param outerDivisions divisões da camada externa H1 (H1 >= 1)
     * @param innerDivisions divisões da camada interna H2 (H2 >= 0)
     * @return matriz de pontos [N][m]
     */
    public static double[][] generateUncontractedTwoLayers(int m, int outerDivisions, int innerDivisions) {
        validateSimplexParameters(m, outerDivisions, innerDivisions);
        double[][] outer = generateCanonicalSingleLayer(m, outerDivisions);
        if (innerDivisions == 0) {
            return outer;
        }
        double[][] innerUncontracted = generateCanonicalSingleLayer(m, innerDivisions);
        double[][] combined = new double[outer.length + innerUncontracted.length][m];
        System.arraycopy(outer, 0, combined, 0, outer.length);
        System.arraycopy(innerUncontracted, 0, combined, outer.length, innerUncontracted.length);
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
     * Esta é a fórmula exata que gerou os arquivos com substituição de zero em {@code MOEAD_Weights/}.
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
     * e ausência de duplicatas arbitrárias dentro de {@code duplicateTolerance}.
     * <p>
     * Nos arquivos oficiais de duas camadas do jMetal 7.8 ({@code W8D_156.dat} e {@code W10D_275.dat}),
     * a camada interna {@code H2=2} foi gerada pelos autores do jMetal sem contração centrípeta, o que faz
     * com que os {@code m} vértices extremos unitários apareçam tanto na camada externa quanto na camada
     * interna. O validador reconhece especificamente essa assinatura estrutural oficial do jMetal, mantendo
     * rejeição estrita para quaisquer outras duplicatas.
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
            String[] tokens = nonEmptyLines.get(i).split("[\\s,]+");
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

        int outerCount = isOfficialJMetalUncontractedTwoLayerSize(expectedObjectives, expectedPopulationSize);
        for (int i = 0; i < weights.length; i++) {
            for (int j = i + 1; j < weights.length; j++) {
                double dist = euclideanDistance(weights[i], weights[j]);
                if (dist <= duplicateTolerance) {
                    if (outerCount > 0 && i < outerCount && j >= outerCount && isUnitAxisVertex(weights[i], duplicateTolerance)) {
                        // Vértice extremo repetido entre a camada externa (H1=3) e a camada interna não contraída (H2=2)
                        // nos arquivos oficiais do jMetal 7.8 (W8D_156.dat e W10D_275.dat)
                        continue;
                    }
                    throw new IllegalArgumentException(
                            String.format(
                                    "Vetores de pesos duplicados ou quase duplicados em '%s' nas linhas %d e %d (distância=%.3e <= tol=%.3e).",
                                    weightFile.getFileName(), i + 1, j + 1, dist, duplicateTolerance));
                }
            }
        }

        return weights;
    }

    private static int isOfficialJMetalUncontractedTwoLayerSize(int m, int popSize) {
        if (m == 8 && popSize == 156) {
            return 120; // C(3+8-1, 7) = 120 na camada externa, C(2+8-1, 7) = 36 na camada interna
        }
        if (m == 10 && popSize == 275) {
            return 220; // C(3+10-1, 9) = 220 na camada externa, C(2+10-1, 9) = 55 na camada interna
        }
        return -1;
    }

    private static boolean isUnitAxisVertex(double[] vec, double tol) {
        int ones = 0;
        int zeros = 0;
        for (double v : vec) {
            if (Math.abs(v - 1.0) <= tol) {
                ones++;
            } else if (Math.abs(v) <= tol) {
                zeros++;
            } else {
                return false;
            }
        }
        return ones == 1 && zeros == vec.length - 1;
    }

    /**
     * Verifica se a substituição opcional de zeros em vetores de pesos está habilitada via propriedade JVM
     * {@code -DreplaceZeroWeights=true}. Por padrão, retorna {@code false}.
     *
     * @return {@code true} se {@code -DreplaceZeroWeights=true}, {@code false} caso contrário
     */
    public static boolean isReplaceZeroWeightsEnabled() {
        return Boolean.parseBoolean(System.getProperty(REPLACE_ZERO_WEIGHTS_PROPERTY, "false"));
    }

    /**
     * Executa o procedimento controlado de substituição de coordenadas zero ({@code 0.0 -> zeroReplacement})
     * respeitando a propriedade JVM {@code -DreplaceZeroWeights=false} (desativada por padrão).
     * Se a propriedade estiver desativada ({@code false}), não gera arquivo em {@code outputDir}.
     *
     * @param sourceFile arquivo de pesos original (nunca modificado)
     * @param outputDir diretório de saída para variantes geradas (ex.: {@code resources/weightVectorFiles/weights_gen})
     * @param zeroReplacement valor positivo substituto para coordenadas zero (ex.: {@code 1.0e-5})
     * @return {@link ZeroReplacementReport} detalhado
     */
    public static ZeroReplacementReport replaceZeroWeightsIfEnabled(
            Path sourceFile, Path outputDir, double zeroReplacement) {
        return replaceZeroWeights(sourceFile, outputDir, zeroReplacement, isReplaceZeroWeightsEnabled());
    }

    /**
     * Executa de forma controlada a inspeção e substituição opcional de coordenadas zero
     * ({@code 0.0 -> zeroReplacement}) a partir de {@code sourceFile}, gravando a variante em
     * {@code outputDir} somente quando {@code enabled == true}, sem jamais modificar {@code sourceFile}.
     * Preserva dimensionalidade, cardinalidade e ordem dos vetores, registra a quantidade de componentes
     * alterados e a soma de cada vetor após a substituição, e valida finitude, não negatividade e
     * ausência de duplicatas inesperadas.
     *
     * @param sourceFile arquivo de pesos de origem (somente leitura)
     * @param outputDir diretório de destino para o arquivo gerado (ex.: {@code resources/weightVectorFiles/weights_gen})
     * @param zeroReplacement valor positivo substituto para coordenadas zero (ex.: {@code 1.0e-5})
     * @param enabled se {@code true}, grava o arquivo com zeros substituídos em {@code outputDir}; se {@code false}, não grava
     * @return {@link ZeroReplacementReport} com todas as métricas e relatório de validação
     */
    public static ZeroReplacementReport replaceZeroWeights(
            Path sourceFile, Path outputDir, double zeroReplacement, boolean enabled) {
        if (sourceFile == null || !Files.exists(sourceFile) || !Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException(
                    "Arquivo de origem inexistente ou inválido para substituição de zeros: " + sourceFile);
        }
        if (!Double.isFinite(zeroReplacement) || zeroReplacement <= 0.0) {
            throw new IllegalArgumentException(
                    "O valor substituto zeroReplacement deve ser finito e estritamente positivo, mas foi: "
                            + zeroReplacement);
        }

        List<String> rawLines;
        try {
            rawLines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Falha ao ler arquivo de origem: " + sourceFile, e);
        }

        List<String> nonEmptyLines = new ArrayList<>();
        for (String line : rawLines) {
            if (line != null && !line.trim().isEmpty()) {
                nonEmptyLines.add(line.trim());
            }
        }
        if (nonEmptyLines.isEmpty()) {
            throw new IllegalArgumentException("Arquivo de pesos vazio: " + sourceFile);
        }

        int vectorCount = nonEmptyLines.size();
        String[] firstTokens = nonEmptyLines.get(0).split("[\\s,]+");
        int dimension = firstTokens.length;
        double[][] processedVectors = new double[vectorCount][dimension];
        double[] vectorSums = new double[vectorCount];
        int replacedCount = 0;
        double minSum = Double.POSITIVE_INFINITY;
        double maxSum = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < vectorCount; i++) {
            String[] tokens = nonEmptyLines.get(i).split("[\\s,]+");
            if (tokens.length != dimension) {
                throw new IllegalArgumentException(
                        String.format(
                                "Dimensionalidade inconsistente em '%s' na linha %d: esperado %d, encontrado %d.",
                                sourceFile.getFileName(), i + 1, dimension, tokens.length));
            }
            double sum = 0.0;
            for (int j = 0; j < dimension; j++) {
                double val = Double.parseDouble(tokens[j]);
                if (!Double.isFinite(val) || val < 0.0) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Coordenada inválida (%.6e) em '%s' (linha %d, coluna %d).",
                                    val, sourceFile.getFileName(), i + 1, j + 1));
                }
                if (enabled && val == 0.0) {
                    val = zeroReplacement;
                    replacedCount++;
                }
                processedVectors[i][j] = val;
                sum += val;
            }
            vectorSums[i] = sum;
            minSum = Math.min(minSum, sum);
            maxSum = Math.max(maxSum, sum);
        }

        double sumTol = Math.max(2e-4, dimension * zeroReplacement + 1e-5);
        SetValidationReport validationReport =
                validateSimplexSet(processedVectors, vectorCount, dimension, sumTol, 1e-7);

        Path outputFile = null;
        boolean generated = false;
        if (enabled) {
            if (outputDir == null) {
                throw new IllegalArgumentException(
                        "O diretório de saída (outputDir) não pode ser nulo quando enabled=true.");
            }
            try {
                Files.createDirectories(outputDir);
                outputFile = outputDir.resolve(sourceFile.getFileName());
                if (outputFile.toAbsolutePath().normalize().equals(sourceFile.toAbsolutePath().normalize())) {
                    throw new IllegalArgumentException(
                            "Proibido sobrescrever o arquivo de pesos original: " + sourceFile);
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < vectorCount; i++) {
                    for (int j = 0; j < dimension; j++) {
                        if (j > 0) {
                            sb.append(' ');
                        }
                        sb.append(String.format(Locale.US, "%.14e", processedVectors[i][j]));
                    }
                    sb.append(System.lineSeparator());
                }
                Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
                generated = true;
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Falha ao gravar arquivo de pesos com substituição de zeros em: " + outputDir, e);
            }
        }

        return new ZeroReplacementReport(
                sourceFile,
                outputFile,
                enabled,
                generated,
                vectorCount,
                dimension,
                zeroReplacement,
                replacedCount,
                vectorSums,
                minSum,
                maxSum,
                validationReport);
    }

    /**
     * Calcula o hash SHA-256 em hexadecimal minúsculo para um arquivo em disco.
     *
     * @param file caminho do arquivo
     * @return hash SHA-256 (64 caracteres hexadecimais minúsculos)
     */
    public static String computeSha256Hex(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao calcular SHA-256 para: " + file, e);
        }
    }

    /**
     * Verifica se a persistência opcional de pontos de referência do NSGA-III está habilitada via
     * propriedade JVM {@code -DpersistReferencePoints=true}. Por padrão, retorna {@code false}.
     *
     * @return {@code true} se {@code -DpersistReferencePoints=true}, {@code false} caso contrário
     */
    public static boolean isPersistReferencePointsEnabled() {
        return Boolean.parseBoolean(System.getProperty(PERSIST_REFERENCE_POINTS_PROPERTY, "false"));
    }

    /**
     * Verifica se a persistência opcional de direções de referência do RVEA está habilitada via
     * propriedade JVM {@code -DpersistReferenceDirections=true}. Por padrão, retorna {@code false}.
     *
     * @return {@code true} se {@code -DpersistReferenceDirections=true}, {@code false} caso contrário
     */
    public static boolean isPersistReferenceDirectionsEnabled() {
        return Boolean.parseBoolean(
                System.getProperty(PERSIST_REFERENCE_DIRECTIONS_PROPERTY, "false"));
    }

    /**
     * Extrai uma cópia profunda (somente leitura) dos pontos de referência efetivamente instanciados
     * dentro de uma instância de {@code NSGAIII}, sem alterar o estado interno do algoritmo.
     *
     * @param nsgaiii instância de {@code org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII}
     * @param m número de objetivos esperado
     * @return matriz {@code double[N][m]} com cópia independente das posições dos pontos
     */
    @SuppressWarnings("unchecked")
    public static double[][] extractReferencePointsFromNsgaIII(Object nsgaiii, int m) {
        if (nsgaiii == null) {
            throw new IllegalArgumentException("A instância de NSGAIII não pode ser nula.");
        }
        try {
            java.lang.reflect.Field field = nsgaiii.getClass().getDeclaredField("referencePoints");
            field.setAccessible(true);
            List<?> refPoints = (List<?>) field.get(nsgaiii);
            double[][] result = new double[refPoints.size()][m];
            for (int i = 0; i < refPoints.size(); i++) {
                Object rp = refPoints.get(i);
                java.lang.reflect.Field posField = rp.getClass().getDeclaredField("position");
                posField.setAccessible(true);
                List<Double> pos = (List<Double>) posField.get(rp);
                if (pos.size() != m) {
                    throw new IllegalStateException(
                            String.format(
                                    "Dimensão do ReferencePoint (%d) difere de m=%d.",
                                    pos.size(), m));
                }
                for (int j = 0; j < m; j++) {
                    result[i][j] = pos.get(j);
                }
            }
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Falha ao inspecionar referencePoints de NSGAIII para auditoria.", e);
        }
    }

    /**
     * Persiste opcionalmente os pontos de referência do NSGA-III caso {@code -DpersistReferencePoints=true},
     * usando o diretório padrão {@code resources/referencePoints/nsgaiii/}.
     *
     * @param vectors matriz de pontos de referência (não é modificada)
     * @param m número de objetivos
     * @param outerDivisions divisões externas H1
     * @param innerDivisions divisões internas H2 (use 0 para 1 camada)
     * @return {@link ReferencePersistenceReport}
     */
    public static ReferencePersistenceReport persistNsgaIIIReferencePointsIfEnabled(
            double[][] vectors, int m, int outerDivisions, int innerDivisions) {
        return persistReferenceSetCsv(
                vectors,
                m,
                outerDivisions,
                innerDivisions,
                "NSGAIII",
                DEFAULT_NSGAIII_PERSIST_DIR,
                isPersistReferencePointsEnabled(),
                false);
    }

    /**
     * Persiste opcionalmente as direções de referência do RVEA caso {@code -DpersistReferenceDirections=true},
     * usando o diretório padrão {@code resources/referencePoints/rvea/}.
     *
     * @param vectors lista de vetores de direção (não é modificada)
     * @param m número de objetivos
     * @param outerDivisions divisões externas H1
     * @param innerDivisions divisões internas H2 (use 0 para 1 camada)
     * @return {@link ReferencePersistenceReport}
     */
    public static ReferencePersistenceReport persistRveaReferenceDirectionsIfEnabled(
            List<double[]> vectors, int m, int outerDivisions, int innerDivisions) {
        double[][] copy = deepCopyVectors(vectors);
        return persistReferenceSetCsv(
                copy,
                m,
                outerDivisions,
                innerDivisions,
                "RVEA",
                DEFAULT_RVEA_PERSIST_DIR,
                isPersistReferenceDirectionsEnabled(),
                false);
    }

    /**
     * Persiste de forma controlada um conjunto de pontos/direções de referência em formato CSV:
     * cabeçalho {@code index,f1,f2,...,fm}, separador vírgula e precisão de 15 casas decimais
     * ({@code %.15f} em {@link Locale#US}).
     * <p>
     * Regras de proteção:
     * <ul>
     *   <li>Opera sobre uma cópia profunda dos vetores, garantindo que o array original jamais seja alterado.</li>
     *   <li>Se {@code enabled == false}, retorna imediatamente sem tocar no sistema de arquivos.</li>
     *   <li>Nunca sobrescreve um arquivo existente automaticamente: se o arquivo alvo já existir com
     *       conteúdo idêntico (ex.: múltiplas runs da mesma configuração), reutiliza-o sem regravar
     *       quando {@code failIfDifferentExists == false}; se o arquivo existente tiver conteúdo
     *       distinto, lança {@link IllegalStateException} (quando {@code failIfDifferentExists == true})
     *       ou grava em um novo arquivo com sufixo incremental {@code _run1.csv}, {@code _run2.csv}, etc.,
     *       preservando intacto o arquivo preexistente.</li>
     * </ul>
     *
     * @param vectors matriz de vetores [N][m]
     * @param m número de objetivos
     * @param outerDivisions divisões da camada externa H1
     * @param innerDivisions divisões da camada interna H2 (0 para camada única)
     * @param algorithmPrefix prefixo do algoritmo ({@code "NSGAIII"} ou {@code "RVEA"})
     * @param outputDir diretório de destino
     * @param enabled se verdadeiro, executa a persistência
     * @param failIfDifferentExists se verdadeiro, lança exceção caso já exista arquivo com conteúdo diferente;
     *     se falso, cria arquivo com sufixo {@code _runN.csv} sem sobrescrever o existente
     * @return {@link ReferencePersistenceReport}
     */
    public static ReferencePersistenceReport persistReferenceSetCsv(
            double[][] vectors,
            int m,
            int outerDivisions,
            int innerDivisions,
            String algorithmPrefix,
            Path outputDir,
            boolean enabled,
            boolean failIfDifferentExists) {
        if (vectors == null || vectors.length == 0) {
            throw new IllegalArgumentException(
                    "Conjunto de vetores para persistência não pode ser nulo ou vazio.");
        }
        int dim = extractUniformDimension(vectors);
        if (dim != m) {
            throw new IllegalArgumentException(
                    String.format(
                            "Dimensão dos vetores (%d) difere do número de objetivos esperado (m=%d).",
                            dim, m));
        }
        double[][] safeCopy = deepCopyVectors(vectors);
        if (!enabled) {
            return new ReferencePersistenceReport(
                    null, false, false, false, false, safeCopy.length, m);
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("Diretório de saída não pode ser nulo.");
        }

        String baseFileName =
                String.format(
                        "%s_m%d_H1%d_H2%d.csv",
                        algorithmPrefix, m, outerDivisions, Math.max(0, innerDivisions));
        String csvContent = formatReferenceSetCsv(safeCopy, m);

        try {
            Files.createDirectories(outputDir);
            Path targetFile = outputDir.resolve(baseFileName);
            if (Files.exists(targetFile)) {
                String existingContent = Files.readString(targetFile, StandardCharsets.UTF_8);
                if (existingContent.equals(csvContent)) {
                    return new ReferencePersistenceReport(
                            targetFile, true, false, true, false, safeCopy.length, m);
                }
                if (failIfDifferentExists) {
                    throw new IllegalStateException(
                            "Arquivo de referência já existe e não pode ser sobrescrito automaticamente: "
                                    + targetFile);
                }
                int suffix = 1;
                String stem = baseFileName.substring(0, baseFileName.length() - 4);
                Path candidate;
                do {
                    candidate = outputDir.resolve(String.format("%s_run%d.csv", stem, suffix));
                    if (Files.exists(candidate)) {
                        String candContent = Files.readString(candidate, StandardCharsets.UTF_8);
                        if (candContent.equals(csvContent)) {
                            return new ReferencePersistenceReport(
                                    candidate, true, false, true, true, safeCopy.length, m);
                        }
                    }
                    suffix++;
                } while (Files.exists(candidate));

                Files.writeString(
                        candidate,
                        csvContent,
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE);
                return new ReferencePersistenceReport(
                        candidate, true, true, false, true, safeCopy.length, m);
            }

            Files.writeString(
                    targetFile,
                    csvContent,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE);
            return new ReferencePersistenceReport(
                    targetFile, true, true, false, false, safeCopy.length, m);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao persistir conjunto de referência em: " + outputDir, e);
        }
    }

    /** Formata uma matriz de vetores [N][m] como CSV com cabeçalho {@code index,f1,...,fm} e 15 casas decimais. */
    public static String formatReferenceSetCsv(double[][] vectors, int m) {
        StringBuilder sb = new StringBuilder();
        sb.append("index");
        for (int j = 1; j <= m; j++) {
            sb.append(",f").append(j);
        }
        sb.append(System.lineSeparator());
        for (int i = 0; i < vectors.length; i++) {
            sb.append(i);
            for (int j = 0; j < m; j++) {
                sb.append(',').append(String.format(Locale.US, "%.15f", vectors[i][j]));
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /** Realiza cópia profunda de uma lista de vetores {@code List<double[]>} para matriz {@code double[][]}. */
    public static double[][] deepCopyVectors(List<double[]> vectors) {
        if (vectors == null) {
            return new double[0][0];
        }
        double[][] copy = new double[vectors.size()][];
        for (int i = 0; i < vectors.size(); i++) {
            double[] row = vectors.get(i);
            copy[i] = row == null ? null : row.clone();
        }
        return copy;
    }

    /** Realiza cópia profunda de uma matriz {@code double[][]}. */
    public static double[][] deepCopyVectors(double[][] vectors) {
        if (vectors == null) {
            return new double[0][0];
        }
        double[][] copy = new double[vectors.length][];
        for (int i = 0; i < vectors.length; i++) {
            double[] row = vectors[i];
            copy[i] = row == null ? null : row.clone();
        }
        return copy;
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

package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.util.ReferenceSetValidator;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetComparisonReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetValidationReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Testes de validação matemática (T03) para os arquivos de vetores de pesos do MOEA/DD
 * em {@code MOEAD_Weights/}, verificação de procedência contra o simplex com substituição
 * {@code 0 -> 1e-5} e testes negativos de rejeição controlada de arquivos corrompidos ou incompatíveis.
 */
class MoeaddWeightValidationTest {

    private static final Path WEIGHTS_DIR = Paths.get("MOEAD_Weights");

    /**
     * Como os arquivos em {@code MOEAD_Weights/} substituem coordenadas zero por {@code 1e-5},
     * a soma das coordenadas de uma linha na camada externa é {@code 1.0 + z * 1e-5}, onde {@code z <= m - 1}
     * é a quantidade de zeros (máximo em m=15: 1 + 14e-5 = 1.00014). Portanto, {@code sumTolerance = 2e-4}
     * acolhe exatamente essa convenção de decomposição sem aceitar linhas não normalizadas.
     */
    private static final double SUM_TOLERANCE = 2e-4;

    private static final double DUPLICATE_TOLERANCE = 1e-7;
    private static final double EQUIVALENCE_TOLERANCE = 1e-6;
    private static final double ZERO_REPLACEMENT = 1e-5;

    static Stream<Arguments> activeMoeadWeightFiles() {
        return Stream.of(
                Arguments.of(2, 100, 99, 0, "W2D_100.dat"),
                Arguments.of(3, 91, 12, 0, "W3D_91.dat"),
                Arguments.of(5, 210, 6, 0, "W5D_210.dat"),
                Arguments.of(8, 156, 3, 2, "W8D_156.dat"),
                Arguments.of(10, 275, 3, 2, "W10D_275.dat"),
                Arguments.of(15, 135, 2, 1, "W15D_135.dat"));
    }

    @ParameterizedTest(name = "m={0}, N={1}, file={4}")
    @MethodSource("activeMoeadWeightFiles")
    @DisplayName("T03.1: Validação estrutural e matemática dos arquivos ativos em MOEAD_Weights/")
    void testActiveMoeadWeightFilesIntegrity(
            int m, int expectedPopSize, int outerDivisions, int innerDivisions, String fileName) {
        assertThat(StaticParams.getPopulationSize(m)).isEqualTo(expectedPopSize);

        Path filePath = WEIGHTS_DIR.resolve(fileName);
        double[][] weights = ReferenceSetValidator.validateMoeadWeightFile(
                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        assertThat(weights.length).isEqualTo(expectedPopSize);
        assertThat(weights[0].length).isEqualTo(m);

        SetValidationReport report = ReferenceSetValidator.validateSimplexSet(
                weights, expectedPopSize, m, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        System.out.printf(
                "[T03-Integrity] %s (m=%d, N=%d) | finite=%b | strictlyPos=%b | minCoord=%.10f | maxCoord=%.10f | minSum=%.8f | maxSum=%.8f | maxSumDev=%.3e | dup=%d | minDist=%.6f%n",
                fileName,
                m,
                expectedPopSize,
                report.allFinite(),
                report.allStrictlyPositive(),
                report.minCoordinate(),
                report.maxCoordinate(),
                report.minSum(),
                report.maxSum(),
                report.maxSumDeviationFromOne(),
                report.duplicateCount(),
                report.minPairwiseEuclideanDistance());

        assertThat(report.validSimplex()).isTrue();
        assertThat(report.allFinite()).isTrue();
        assertThat(report.allNonNegative()).isTrue();
        // Todos os pesos em MOEAD_Weights possuem substituição 0 -> 1e-5, logo são estritamente positivos
        assertThat(report.allStrictlyPositive()).isTrue();
        assertThat(report.minCoordinate()).isCloseTo(ZERO_REPLACEMENT, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(report.duplicateCount()).isZero();
        assertThat(report.minSum()).isGreaterThanOrEqualTo(1.0 - 1e-6);
        assertThat(report.maxSum()).isLessThanOrEqualTo(1.0 + (m - 1) * ZERO_REPLACEMENT + 1e-6);
    }

    @ParameterizedTest(name = "m={0}, N={1}, H1={2}, H2={3}, file={4}")
    @MethodSource("activeMoeadWeightFiles")
    @DisplayName("T03.2: Equivalência exata com gerador canônico zero-replaced (0 -> 1e-5) e comparação com Das & Dennis puro")
    void testMoeadWeightsVsZeroReplacedAndPureCanonical(
            int m, int expectedPopSize, int outerDivisions, int innerDivisions, String fileName) {
        Path filePath = WEIGHTS_DIR.resolve(fileName);
        double[][] fileWeights = ReferenceSetValidator.validateMoeadWeightFile(
                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        // 1. Comparação com o gerador canônico com substituição 0 -> 1e-5 antes da contração da camada interna
        double[][] zeroReplacedCanonical = ReferenceSetValidator.generateZeroReplacedCanonical(
                m, outerDivisions, innerDivisions, ZERO_REPLACEMENT);

        // Embaralha para garantir invariância à ordem das linhas
        List<double[]> shuffledList = new ArrayList<>(Arrays.asList(zeroReplacedCanonical));
        Collections.shuffle(shuffledList, new Random(100L + m));
        double[][] shuffledZeroReplaced = shuffledList.toArray(double[][]::new);

        SetComparisonReport vsZeroReplaced = ReferenceSetValidator.compareSetsOrderIndependent(
                fileWeights, shuffledZeroReplaced, EQUIVALENCE_TOLERANCE);

        // 2. Comparação com o Das & Dennis / Deb & Jain canônico puro (sem substituição 0 -> 1e-5)
        double[][] pureCanonical = ReferenceSetValidator.generateCanonicalTwoLayers(
                m, outerDivisions, innerDivisions);
        SetComparisonReport vsPureCanonicalStrict = ReferenceSetValidator.compareSetsOrderIndependent(
                fileWeights, pureCanonical, EQUIVALENCE_TOLERANCE);
        SetComparisonReport vsPureCanonicalRelaxed = ReferenceSetValidator.compareSetsOrderIndependent(
                fileWeights, pureCanonical, 1.5e-5);

        System.out.printf(
                "[T03-VsGenerators] %s (m=%d) | vsZeroReplaced(1e-5): maxCoordErr=%.3e, maxEuclidErr=%.3e, verdict=%s | vsPureCanonical: maxCoordErr=%.3e, maxEuclidErr=%.3e, verdict(1e-6)=%s, verdict(1.5e-5)=%s%n",
                fileName,
                m,
                vsZeroReplaced.maxCoordinateError(),
                vsZeroReplaced.maxEuclideanError(),
                vsZeroReplaced.verdict(),
                vsPureCanonicalStrict.maxCoordinateError(),
                vsPureCanonicalStrict.maxEuclideanError(),
                vsPureCanonicalStrict.verdict(),
                vsPureCanonicalRelaxed.verdict());

        // Deve ser EQUIVALENT dentro de 1e-6 (e erro ~1e-15 da precisão de 15 dígitos do arquivo) ao gerador zero-replaced
        assertThat(vsZeroReplaced.verdict()).isEqualTo(Verdict.EQUIVALENT);
        assertThat(vsZeroReplaced.unmatchedFromA()).isZero();
        assertThat(vsZeroReplaced.unmatchedFromB()).isZero();
        assertThat(vsZeroReplaced.maxCoordinateError()).isLessThan(1e-13);

        // Contra o Das & Dennis puro, a diferença máxima por coordenada é exatamente 1e-5 (devido a 0 -> 1e-5)
        assertThat(vsPureCanonicalStrict.maxCoordinateError())
                .isCloseTo(ZERO_REPLACEMENT, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(vsPureCanonicalStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
        assertThat(vsPureCanonicalRelaxed.verdict()).isEqualTo(Verdict.EQUIVALENT);
    }

    @ParameterizedTest(name = "m={0}, N={1}, H1={2}, H2={3}, file={4}")
    @MethodSource("activeMoeadWeightFiles")
    @DisplayName("T03.3: Comparação entre arquivos de MOEAD_Weights/ e saída de reference-calc.java")
    void testMoeadWeightsVsReferenceCalc(
            int m, int expectedPopSize, int outerDivisions, int innerDivisions, String fileName) {
        Path filePath = WEIGHTS_DIR.resolve(fileName);
        double[][] fileWeights = ReferenceSetValidator.validateMoeadWeightFile(
                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        int layers = (innerDivisions == 0) ? 1 : 2;
        double[][] refCalcOutput = ReferenceSetValidator.simulateReferenceCalc(m, outerDivisions, layers);

        SetComparisonReport reportStrict = ReferenceSetValidator.compareSetsOrderIndependent(
                fileWeights, refCalcOutput, EQUIVALENCE_TOLERANCE);

        System.out.printf(
                "[T03-VsRefCalc] %s (m=%d) | fileCount=%d vs refCalcCount=%d | unmatchedA=%d | maxCoordErr=%.6e | maxEuclidErr=%.6e | verdict(1e-6)=%s%n",
                fileName,
                m,
                reportStrict.countA(),
                reportStrict.countB(),
                reportStrict.unmatchedFromA(),
                reportStrict.maxCoordinateError(),
                reportStrict.maxEuclideanError(),
                reportStrict.verdict());

        // Em 1e-6 nenhum arquivo é equivalente ao reference-calc (pois reference-calc arredonda a 1e-5 e imprime 0.0 em vez de 1e-5)
        assertThat(reportStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);

        if (m == 2) {
            // Para m=2, H=99, reference-calc gera 0 pontos
            assertThat(reportStrict.countB()).isZero();
        } else if (innerDivisions > 0) {
            // Para m=8, 10, 15 (2 camadas), reference-calc gera o dobro da camada externa (2*120=240, 2*220=440, 2*120=240)
            assertThat(reportStrict.countB()).isNotEqualTo(expectedPopSize);
        }
    }

    @Test
    @DisplayName("T03.4: Testes negativos — rejeição controlada de arquivos ausentes, malformados ou incompatíveis")
    void testNegativeInvalidWeightFiles(@TempDir Path tempDir) throws IOException {
        // 1. Arquivo inexistente
        Path missingFile = tempDir.resolve("W3D_91_inexistente.dat");
        assertThatThrownBy(() -> ReferenceSetValidator.validateMoeadWeightFile(
                        missingFile, 3, 91, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistente ou ilegível");

        // 2. Arquivo com quantidade incorreta de linhas (esperado 91, mas tem 3 linhas)
        Path wrongCountFile = tempDir.resolve("W3D_wrong_count.dat");
        Files.writeString(
                wrongCountFile,
                "1.0 0.0 0.0\n0.0 1.0 0.0\n0.0 0.0 1.0\n");
        assertThatThrownBy(() -> ReferenceSetValidator.validateMoeadWeightFile(
                        wrongCountFile, 3, 91, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantidade incorreta de vetores")
                .hasMessageContaining("esperado 91 linhas, mas encontrado 3");

        // 3. Arquivo com dimensionalidade incorreta (esperado 3 colunas, mas linha 2 possui 4 colunas)
        Path wrongDimFile = tempDir.resolve("W3D_wrong_dim.dat");
        Files.writeString(
                wrongDimFile,
                "1.0 0.0 0.0\n0.25 0.25 0.25 0.25\n0.0 0.0 1.0\n");
        assertThatThrownBy(() -> ReferenceSetValidator.validateMoeadWeightFile(
                        wrongDimFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dimensionalidade incorreta")
                .hasMessageContaining("linha 2: esperado 3 colunas, mas encontrado 4");

        // 4. Arquivo contendo NaN ou Infinity
        Path nanFile = tempDir.resolve("W3D_nan.dat");
        Files.writeString(
                nanFile,
                "1.0 0.0 0.0\n0.5 NaN 0.5\n0.0 0.0 1.0\n");
        assertThatThrownBy(() -> ReferenceSetValidator.validateMoeadWeightFile(
                        nanFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valor não finito (NaN)");

        // 5. Arquivo contendo coordenada negativa
        Path negativeWeightFile = tempDir.resolve("W3D_negative.dat");
        Files.writeString(
                negativeWeightFile,
                "1.0 0.0 0.0\n1.2 -0.2 0.0\n0.0 0.0 1.0\n");
        assertThatThrownBy(() -> ReferenceSetValidator.validateMoeadWeightFile(
                        negativeWeightFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Peso negativo");

        // 6. Arquivo contendo soma incompatível com o simplex
        Path badSumFile = tempDir.resolve("W3D_bad_sum.dat");
        Files.writeString(
                badSumFile,
                "1.0 0.0 0.0\n0.5 0.5 0.5\n0.0 0.0 1.0\n");
        assertThatThrownBy(() -> ReferenceSetValidator.validateMoeadWeightFile(
                        badSumFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Soma dos pesos inválida");

        // 7. Arquivo contendo vetores duplicados
        Path duplicateWeightFile = tempDir.resolve("W3D_duplicates.dat");
        Files.writeString(
                duplicateWeightFile,
                "1.0 0.0 0.0\n0.5 0.5 0.0\n0.5 0.5 0.0\n");
        assertThatThrownBy(() -> ReferenceSetValidator.validateMoeadWeightFile(
                        duplicateWeightFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vetores de pesos duplicados ou quase duplicados");
    }
}

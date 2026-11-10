package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import br.usp.lti.newmoea.util.ReferenceSetValidator;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetComparisonReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetValidationReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.Verdict;
import br.usp.lti.newmoea.util.ReferenceSetValidator.ZeroReplacementReport;
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
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

/**
 * Testes de validação matemática e auditoria de pesos do MOEA/DD (T03 e T11):
 * <ul>
 *   <li>Validação estrutural e matemática dos 6 arquivos ativos em {@code MOEAD_Weights/};</li>
 *   <li>Verificação de equivalência contra geradores de referência (canônico Das & Dennis / Deb & Jain,
 *       duas camadas não contraídas oficiais do jMetal 7.8 e variante com substituição {@code 0 -> 1e-5});</li>
 *   <li>Verificação de cópia fiel byte a byte e hash SHA-256 em {@code resources/weightVectorFiles/};</li>
 *   <li>Testes do procedimento opcional {@code -DreplaceZeroWeights=false} (desativado por padrão) e
 *       geração controlada em {@code resources/weightVectorFiles/weights_gen/};</li>
 *   <li>Teste de 1 execução reduzida do MOEA/DD consumindo os novos diretórios sem alterar o diretório
 *       padrão {@code MOEAD_Weights/} em {@link WFGStudy};</li>
 *   <li>Testes negativos de rejeição controlada de arquivos ausentes, malformados ou com duplicatas arbitrárias.</li>
 * </ul>
 */
class MoeaddWeightValidationTest {

    private static final Path WEIGHTS_DIR = Paths.get("MOEAD_Weights");
    private static final Path COPIED_ROOT_DIR = Paths.get("resources", "weightVectorFiles");
    private static final Path COPIED_MOEAD_DIR = Paths.get("resources", "weightVectorFiles", "moead");
    private static final Path GENERATED_WEIGHTS_DIR = Paths.get("resources", "weightVectorFiles", "weights_gen");

    /**
     * Como os arquivos com substituição {@code 0 -> 1e-5} em {@code MOEAD_Weights/} possuem soma
     * {@code 1.0 + z * 1e-5} (máximo em m=15: 1 + 14e-5 = 1.00014), {@code sumTolerance = 2e-4}
     * acolhe tanto os arquivos exatos do jMetal (soma = 1.0) quanto os arquivos com {@code 1e-5}.
     */
    private static final double SUM_TOLERANCE = 2e-4;

    private static final double DUPLICATE_TOLERANCE = 1e-7;
    private static final double EQUIVALENCE_TOLERANCE = 1e-6;
    private static final double ZERO_REPLACEMENT = 1e-5;

    /**
     * Parâmetros dos 6 arquivos ativos do pipeline:
     * (m, expectedPopSize, outerDivisions, innerDivisions, fileName, isOfficialJMetalWithZeros, expectedVertexDuplicates).
     */
    static Stream<Arguments> activeMoeadWeightFiles() {
        return Stream.of(
                Arguments.of(2, 100, 99, 0, "W2D_100.dat", false, 0),
                Arguments.of(3, 91, 12, 0, "W3D_91.dat", true, 0),
                Arguments.of(5, 210, 6, 0, "W5D_210.dat", false, 0),
                Arguments.of(8, 156, 3, 2, "W8D_156.dat", true, 8),
                Arguments.of(10, 275, 3, 2, "W10D_275.dat", true, 10),
                Arguments.of(15, 135, 2, 1, "W15D_135.dat", false, 0));
    }

    @ParameterizedTest(name = "m={0}, N={1}, file={4}, officialJMetal={5}")
    @MethodSource("activeMoeadWeightFiles")
    @DisplayName("T03.1 / T11.1: Validação estrutural e matemática dos 6 arquivos ativos em MOEAD_Weights/")
    void testActiveMoeadWeightFilesIntegrity(
            int m,
            int expectedPopSize,
            int outerDivisions,
            int innerDivisions,
            String fileName,
            boolean isOfficialJMetalWithZeros,
            int expectedVertexDuplicates) {
        assertThat(StaticParams.getPopulationSize(m)).isEqualTo(expectedPopSize);

        Path filePath = WEIGHTS_DIR.resolve(fileName);
        double[][] weights = ReferenceSetValidator.validateMoeadWeightFile(
                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        assertThat(weights.length).isEqualTo(expectedPopSize);
        assertThat(weights[0].length).isEqualTo(m);

        SetValidationReport report = ReferenceSetValidator.validateSimplexSet(
                weights, expectedPopSize, m, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        System.out.printf(
                "[T11-Integrity] %s (m=%d, N=%d) | officialJMetal=%b | finite=%b | strictlyPos=%b | minCoord=%.10f | maxCoord=%.10f | minSum=%.8f | maxSum=%.8f | maxSumDev=%.3e | dup=%d%n",
                fileName,
                m,
                expectedPopSize,
                isOfficialJMetalWithZeros,
                report.allFinite(),
                report.allStrictlyPositive(),
                report.minCoordinate(),
                report.maxCoordinate(),
                report.minSum(),
                report.maxSum(),
                report.maxSumDeviationFromOne(),
                report.duplicateCount());

        assertThat(report.allFinite()).isTrue();
        assertThat(report.allNonNegative()).isTrue();
        assertThat(report.duplicateCount()).isEqualTo(expectedVertexDuplicates);

        if (isOfficialJMetalWithZeros) {
            // Arquivos oficiais do jMetal 7.8 (W3D_91, W8D_156, W10D_275) possuem coordenadas 0.0 exatas e soma 1.0
            assertThat(report.allStrictlyPositive()).isFalse();
            assertThat(report.minCoordinate()).isEqualTo(0.0);
            assertThat(report.maxCoordinate()).isEqualTo(1.0);
            assertThat(report.maxSumDeviationFromOne()).isLessThanOrEqualTo(1e-15);
            if (expectedVertexDuplicates == 0) {
                assertThat(report.validSimplex()).isTrue();
            }
        } else {
            // Arquivos sem contraparte no jMetal (W2D_100, W5D_210, W15D_135) possuem substituição 0 -> 1e-5
            assertThat(report.validSimplex()).isTrue();
            assertThat(report.allStrictlyPositive()).isTrue();
            assertThat(report.minCoordinate()).isCloseTo(ZERO_REPLACEMENT, org.assertj.core.data.Offset.offset(1e-12));
            assertThat(report.minSum()).isGreaterThanOrEqualTo(1.0 - 1e-6);
            assertThat(report.maxSum()).isLessThanOrEqualTo(1.0 + (m - 1) * ZERO_REPLACEMENT + 1e-6);
        }
    }

    @ParameterizedTest(name = "m={0}, N={1}, H1={2}, H2={3}, file={4}")
    @MethodSource("activeMoeadWeightFiles")
    @DisplayName("T03.2 / T11.1: Equivalência exata com o gerador matemático correspondente de cada arquivo ativo")
    void testMoeadWeightsVsZeroReplacedAndPureCanonical(
            int m,
            int expectedPopSize,
            int outerDivisions,
            int innerDivisions,
            String fileName,
            boolean isOfficialJMetalWithZeros,
            int expectedVertexDuplicates) {
        Path filePath = WEIGHTS_DIR.resolve(fileName);
        double[][] fileWeights = ReferenceSetValidator.validateMoeadWeightFile(
                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        double[][] pureCanonical = ReferenceSetValidator.generateCanonicalTwoLayers(
                m, outerDivisions, innerDivisions);
        double[][] zeroReplacedCanonical = ReferenceSetValidator.generateZeroReplacedCanonical(
                m, outerDivisions, innerDivisions, ZERO_REPLACEMENT);

        if (!isOfficialJMetalWithZeros) {
            // W2D_100, W5D_210, W15D_135: gerados por Das & Dennis / Deb & Jain contraído com 0 -> 1e-5
            List<double[]> shuffledList = new ArrayList<>(Arrays.asList(zeroReplacedCanonical));
            Collections.shuffle(shuffledList, new Random(100L + m));
            double[][] shuffledZeroReplaced = shuffledList.toArray(double[][]::new);

            SetComparisonReport vsZeroReplaced = ReferenceSetValidator.compareSetsOrderIndependent(
                    fileWeights, shuffledZeroReplaced, EQUIVALENCE_TOLERANCE);
            SetComparisonReport vsPureCanonicalStrict = ReferenceSetValidator.compareSetsOrderIndependent(
                    fileWeights, pureCanonical, EQUIVALENCE_TOLERANCE);
            SetComparisonReport vsPureCanonicalRelaxed = ReferenceSetValidator.compareSetsOrderIndependent(
                    fileWeights, pureCanonical, 1.5e-5);

            assertThat(vsZeroReplaced.verdict()).isEqualTo(Verdict.EQUIVALENT);
            assertThat(vsZeroReplaced.unmatchedFromA()).isZero();
            assertThat(vsZeroReplaced.unmatchedFromB()).isZero();
            assertThat(vsZeroReplaced.maxCoordinateError()).isLessThan(1e-13);

            assertThat(vsPureCanonicalStrict.maxCoordinateError())
                    .isCloseTo(ZERO_REPLACEMENT, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(vsPureCanonicalStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
            assertThat(vsPureCanonicalRelaxed.verdict()).isEqualTo(Verdict.EQUIVALENT);
        } else if (innerDivisions == 0) {
            // W3D_91.dat: arquivo oficial do jMetal 7.8, equivalente ao Das & Dennis canônico puro (1 camada)
            SetComparisonReport vsPureCanonical = ReferenceSetValidator.compareSetsOrderIndependent(
                    fileWeights, pureCanonical, EQUIVALENCE_TOLERANCE);
            assertThat(vsPureCanonical.verdict()).isEqualTo(Verdict.EQUIVALENT);
            assertThat(vsPureCanonical.maxCoordinateError()).isLessThan(1e-15);
        } else {
            // W8D_156.dat e W10D_275.dat: arquivos oficiais do jMetal 7.8 com 2 camadas não contraídas
            double[][] uncontractedTwoLayers = ReferenceSetValidator.generateUncontractedTwoLayers(
                    m, outerDivisions, innerDivisions);
            SetComparisonReport vsUncontracted = ReferenceSetValidator.compareSetsOrderIndependent(
                    fileWeights, uncontractedTwoLayers, EQUIVALENCE_TOLERANCE);
            assertThat(vsUncontracted.matchedCount()).isEqualTo(expectedPopSize);
            assertThat(vsUncontracted.unmatchedFromA()).isZero();
            assertThat(vsUncontracted.unmatchedFromB()).isZero();
            assertThat(vsUncontracted.maxCoordinateError()).isLessThan(1e-15);
            assertThat(vsUncontracted.duplicatesA()).isEqualTo(expectedVertexDuplicates);
            assertThat(vsUncontracted.duplicatesB()).isEqualTo(expectedVertexDuplicates);
        }
    }

    @ParameterizedTest(name = "m={0}, N={1}, H1={2}, H2={3}, file={4}")
    @MethodSource("activeMoeadWeightFiles")
    @DisplayName("T03.3: Comparação entre arquivos de MOEAD_Weights/ e saída de reference-calc.java")
    void testMoeadWeightsVsReferenceCalc(
            int m,
            int expectedPopSize,
            int outerDivisions,
            int innerDivisions,
            String fileName,
            boolean isOfficialJMetalWithZeros,
            int expectedVertexDuplicates) {
        Path filePath = WEIGHTS_DIR.resolve(fileName);
        double[][] fileWeights = ReferenceSetValidator.validateMoeadWeightFile(
                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

        int layers = (innerDivisions == 0) ? 1 : 2;
        double[][] refCalcOutput = ReferenceSetValidator.simulateReferenceCalc(m, outerDivisions, layers);

        SetComparisonReport reportStrict = ReferenceSetValidator.compareSetsOrderIndependent(
                fileWeights, refCalcOutput, EQUIVALENCE_TOLERANCE);

        // Em 1e-6 nenhum arquivo é equivalente ao reference-calc (pois reference-calc arredonda a 1e-5)
        assertThat(reportStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);

        if (m == 2) {
            assertThat(reportStrict.countB()).isZero();
        } else if (innerDivisions > 0) {
            assertThat(reportStrict.countB()).isNotEqualTo(expectedPopSize);
        }
    }

    @Test
    @DisplayName("T11.2: Verificação de cópia fiel e integridade SHA-256 dos 29 arquivos oficiais do jMetal em resources/weightVectorFiles/")
    void testOfficialJMetalCopiesAndSha256Integrity() throws IOException {
        List<String> officialFiles = List.of(
                "W2D_300.dat", "W2D_400.dat", "W2D_500.dat", "W2D_600.dat", "W2D_800.dat", "W2D_1000.dat",
                "W3D_91.dat", "W3D_100.dat", "W3D_136.dat", "W3D_300.dat", "W3D_500.dat", "W3D_600.dat",
                "W3D_800.dat", "W3D_1000.dat", "W3D_1200.dat",
                "W4D_165.dat",
                "W5D_495.dat", "W5D_1000.dat", "W5D_1200.dat", "W5D_1500.dat", "W5D_1800.dat", "W5D_2000.dat", "W5D_2500.dat",
                "W8D_156.dat",
                "W10D_220.dat", "W10D_275.dat", "W10D_715.dat",
                "W15D_120.dat", "W15D_680.dat");

        assertThat(officialFiles).hasSize(29);

        for (String fileName : officialFiles) {
            Path inMoeadWeights = WEIGHTS_DIR.resolve(fileName);
            Path inCopiedRoot = COPIED_ROOT_DIR.resolve(fileName);
            Path inCopiedMoead = COPIED_MOEAD_DIR.resolve(fileName);

            assertThat(inMoeadWeights).exists().isRegularFile();
            assertThat(inCopiedRoot).exists().isRegularFile();
            assertThat(inCopiedMoead).exists().isRegularFile();

            String shaWeights = ReferenceSetValidator.computeSha256Hex(inMoeadWeights);
            String shaRoot = ReferenceSetValidator.computeSha256Hex(inCopiedRoot);
            String shaMoead = ReferenceSetValidator.computeSha256Hex(inCopiedMoead);

            assertThat(shaRoot)
                    .as("SHA-256 de %s em resources/weightVectorFiles/ deve ser idêntico ao oficial", fileName)
                    .isEqualTo(shaWeights);
            assertThat(shaMoead)
                    .as("SHA-256 de %s em resources/weightVectorFiles/ deve ser idêntico ao oficial", fileName)
                    .isEqualTo(shaWeights);
            assertThat(Files.readAllBytes(inCopiedMoead)).isEqualTo(Files.readAllBytes(inMoeadWeights));
        }
    }

    @Test
    @DisplayName("T11.3: Substituição opcional de zeros (-DreplaceZeroWeights=false por padrão; gera em weights_gen quando true sem tocar no original)")
    void testOptionalZeroReplacementProcedure(@TempDir Path tempGenDir) throws IOException {
        // 1. Verifica que a propriedade JVM está desativada por padrão
        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled()).isFalse();

        Path sourceFile = COPIED_MOEAD_DIR.resolve("W3D_91.dat");
        String originalSha = ReferenceSetValidator.computeSha256Hex(sourceFile);

        // 2. Com -DreplaceZeroWeights=false (padrão), não deve gerar arquivo no diretório de saída
        ZeroReplacementReport disabledReport = ReferenceSetValidator.replaceZeroWeightsIfEnabled(
                sourceFile, tempGenDir, ZERO_REPLACEMENT);
        assertThat(disabledReport.enabled()).isFalse();
        assertThat(disabledReport.generated()).isFalse();
        assertThat(disabledReport.outputFile()).isNull();
        assertThat(disabledReport.replacedComponentCount()).isZero();
        assertThat(tempGenDir.resolve("W3D_91.dat")).doesNotExist();

        // 3. Quando habilitado explicitamente (ou via -DreplaceZeroWeights=true), gera em weights_gen/
        Files.createDirectories(GENERATED_WEIGHTS_DIR);
        ZeroReplacementReport enabledReport = ReferenceSetValidator.replaceZeroWeights(
                sourceFile, GENERATED_WEIGHTS_DIR, ZERO_REPLACEMENT, true);

        assertThat(enabledReport.enabled()).isTrue();
        assertThat(enabledReport.generated()).isTrue();
        assertThat(enabledReport.outputFile()).isEqualTo(GENERATED_WEIGHTS_DIR.resolve("W3D_91.dat"));
        assertThat(enabledReport.outputFile()).exists().isRegularFile();
        assertThat(enabledReport.vectorCount()).isEqualTo(91);
        assertThat(enabledReport.dimension()).isEqualTo(3);
        assertThat(enabledReport.replacedComponentCount()).isEqualTo(39);
        assertThat(enabledReport.vectorSumsAfterReplacement()).hasSize(91);
        assertThat(enabledReport.minSum()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(enabledReport.maxSum()).isCloseTo(1.0 + 2 * ZERO_REPLACEMENT, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(enabledReport.validationReport().validSimplex()).isTrue();
        assertThat(enabledReport.validationReport().allFinite()).isTrue();
        assertThat(enabledReport.validationReport().allNonNegative()).isTrue();
        assertThat(enabledReport.validationReport().allStrictlyPositive()).isTrue();
        assertThat(enabledReport.validationReport().duplicateCount()).isZero();

        // Garante que o arquivo original permaneceu 100% intocado
        assertThat(ReferenceSetValidator.computeSha256Hex(sourceFile)).isEqualTo(originalSha);

        // Garante que o arquivo gerado em weights_gen/ é aceito pelo validador do MOEA/DD
        double[][] validatedGen = ReferenceSetValidator.validateMoeadWeightFile(
                enabledReport.outputFile(), 3, 91, SUM_TOLERANCE, DUPLICATE_TOLERANCE);
        assertThat(validatedGen).hasDimensions(91, 3);
    }

    @Test
    @DisplayName("T11.4: Teste de 1 execução reduzida do MOEA/DD com resources/weightVectorFiles/ e weights_gen sem alterar WFGStudy")
    void testSingleRunWithCopiedAndGeneratedWeightDirectories() {
        Problem<DoubleSolution> problem = new WFG1(4, 20, 3);
        int popSize = StaticParams.getPopulationSize(3); // 91
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        // Garante que a variante gerada para W3D_91.dat existe em weights_gen
        ReferenceSetValidator.replaceZeroWeights(
                COPIED_MOEAD_DIR.resolve("W3D_91.dat"), GENERATED_WEIGHTS_DIR, ZERO_REPLACEMENT, true);

        for (String dataDir : List.of(COPIED_MOEAD_DIR.toString(), GENERATED_WEIGHTS_DIR.toString())) {
            double[][] weights = WFGStudy.validateMoeadWeightFile(dataDir, problem, popSize);
            assertThat(weights).hasDimensions(popSize, 3);

            Algorithm<List<DoubleSolution>> algorithm = new MOEADBuilder(problem, MOEADBuilder.Variant.MOEADD)
                    .setCrossover(crossover)
                    .setMutation(mutation)
                    .setMaxEvaluations(popSize * 2)
                    .setPopulationSize(popSize)
                    .setResultPopulationSize(popSize)
                    .setNeighborhoodSelectionProbability(0.9)
                    .setMaximumNumberOfReplacedSolutions(2)
                    .setNeighborSize(20)
                    .setDataDirectory(dataDir)
                    .setFunctionType(AbstractMOEAD.FunctionType.PBI)
                    .build();

            algorithm.run();
            List<DoubleSolution> result = algorithm.result();
            assertThat(result).isNotEmpty();
            for (DoubleSolution sol : result) {
                for (double obj : sol.objectives()) {
                    assertThat(obj).isFinite();
                }
            }
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

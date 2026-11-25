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
import br.usp.lti.newmoea.util.ResolvedWeightArtifact;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Testes de validação matemática, resolução canônica e auditoria forense de
 * pesos do MOEA/DD
 * (T03, T11 e reconciliação T32):
 *
 * <ul>
 * <li>Validação estrutural e matemática dos 6 artefatos canônicos efetivamente
 * resolvidos pelo
 * pipeline ativo ({@code resources/weightVectorFiles/} e
 * {@code resources/weightVectorFiles/weights_gen/});</li>
 * <li>Auditoria forense somente leitura dos 43 arquivos históricos imutáveis em
 * {@code MOEAD_Weights/} (reconciliados após reversão no commit
 * {@code 2d6f257}: camada
 * interna contraída Deb &amp; Jain com substituição {@code 0 -> 1e-5});</li>
 * <li>Verificação de integridade SHA-256 e estrutura matemática das 29 cópias
 * oficiais do
 * jMetal 7.8 em {@code resources/weightVectorFiles/} contra o manifesto
 * auditado
 * {@code docs/moead-weights-manifest.csv} (sem jamais consultar cópias locais
 * externas do
 * jMetal ou comparar contra {@code MOEAD_Weights/});</li>
 * <li>Testes do procedimento opcional {@code -DreplaceZeroWeights=false}
 * (desativado por padrão)
 * com isolamento em {@code weights_lowzero_gen/} e proteção de
 * {@code weights_gen/};</li>
 * <li>Testes negativos em fixtures temporárias próprias
 * ({@code @TempDir}).</li>
 * </ul>
 */
class MoeaddWeightValidationTest {

        // Diretório legado protegido inspecionado apenas em modo somente leitura para
        // auditoria histórica (T03/T11).
        private static final Path LEGACY_PROTECTED_WEIGHTS_DIR = Paths.get("MOEAD_Weights");
        private static final Path OFFICIAL_WEIGHTS_DIR = Paths.get("resources", "weightVectorFiles");
        private static final Path GENERATED_WEIGHTS_DIR = Paths.get("resources", "weightVectorFiles", "weights_gen");
        private static final Path AUDITED_MANIFEST_CSV = Paths.get("docs", "moead-weights-manifest.csv");

        private static final double SUM_TOLERANCE = 2e-4;
        private static final double DUPLICATE_TOLERANCE = 1e-7;
        private static final double EQUIVALENCE_TOLERANCE = 1e-6;
        private static final double ZERO_REPLACEMENT = 1e-5;

        /**
         * Parâmetros dos 6 arquivos canônicos ativos do pipeline em
         * {@code resources/weightVectorFiles/}
         * e {@code resources/weightVectorFiles/weights_gen/}:
         * {@code (m, expectedPopSize, outerDivisions, innerDivisions, fileName, expectedCategory, expectedVertexDuplicates)}.
         */
        static Stream<Arguments> activeCanonicalWeightSpecs() {
                return Stream.of(
                                Arguments.of(2, 100, 99, 0, "W2D_100.dat", "WEIGHTS_GEN", 0),
                                Arguments.of(3, 91, 12, 0, "W3D_91.dat", "OFFICIAL", 0),
                                Arguments.of(5, 210, 6, 0, "W5D_210.dat", "WEIGHTS_GEN", 0),
                                Arguments.of(8, 156, 3, 2, "W8D_156.dat", "OFFICIAL", 8),
                                Arguments.of(10, 275, 3, 2, "W10D_275.dat", "OFFICIAL", 10),
                                Arguments.of(15, 135, 2, 1, "W15D_135.dat", "WEIGHTS_GEN", 0));
        }

        /**
         * Parâmetros dos 6 arquivos correspondentes no diretório histórico imutável
         * {@code MOEAD_Weights/}
         * (todos gerados com contração Deb &amp; Jain e substituição {@code 0 -> 1e-5},
         * sem duplicatas).
         */
        static Stream<Arguments> legacyProtectedSixWeightSpecs() {
                return Stream.of(
                                Arguments.of(2, 100, 99, 0, "W2D_100.dat"),
                                Arguments.of(3, 91, 12, 0, "W3D_91.dat"),
                                Arguments.of(5, 210, 6, 0, "W5D_210.dat"),
                                Arguments.of(8, 156, 3, 2, "W8D_156.dat"),
                                Arguments.of(10, 275, 3, 2, "W10D_275.dat"),
                                Arguments.of(15, 135, 2, 1, "W15D_135.dat"));
        }

        @ParameterizedTest(name = "m={0}, N={1}, file={4}, category={5}")
        @MethodSource("activeCanonicalWeightSpecs")
        @DisplayName("T32 / T29: Validação estrutural e matemática dos 6 artefatos canônicos ativos (OFFICIAL + WEIGHTS_GEN)")
        void testActiveCanonicalMoeadWeightFilesIntegrity(
                        int m,
                        int expectedPopSize,
                        int outerDivisions,
                        int innerDivisions,
                        String fileName,
                        String expectedCategory,
                        int expectedVertexDuplicates) {
                assertThat(StaticParams.getPopulationSize(m)).isEqualTo(expectedPopSize);

                String prevZeroFlag = System.getProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);
                try {
                        System.clearProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);

                        ResolvedWeightArtifact artifact = ReferenceSetValidator.resolveCanonicalWeightFile(m,
                                        expectedPopSize, null, "MOEADD");
                        assertThat(artifact.path()).isNotNull().exists().isRegularFile();
                        assertThat(artifact.path().getFileName().toString()).isEqualTo(fileName);
                        assertThat(artifact.path().toString().replace('\\', '/'))
                                        .doesNotContain("MOEAD_Weights");
                        assertThat(artifact.sourceCategory())
                                        .isIn(expectedCategory, "GENERATED_ON_DEMAND");

                        double[][] weights = ReferenceSetValidator.validateMoeadWeightFile(
                                        artifact.path(), m, expectedPopSize, 1e-6, DUPLICATE_TOLERANCE);
                        assertThat(weights).hasDimensions(expectedPopSize, m);

                        SetValidationReport report = ReferenceSetValidator.validateSimplexSet(
                                        weights, expectedPopSize, m, 1e-6, DUPLICATE_TOLERANCE);

                        assertThat(report.allFinite()).isTrue();
                        assertThat(report.allNonNegative()).isTrue();
                        // Tanto os 3 arquivos oficiais do jMetal (m=3,8,10) quanto os 3 gerados
                        // analíticos em weights_gen/ (m=2,5,15)
                        // preservam coordenadas 0.0 exatas e soma unitária exata.
                        assertThat(report.allStrictlyPositive()).isFalse();
                        assertThat(report.minCoordinate()).isEqualTo(0.0);
                        assertThat(report.maxSumDeviationFromOne()).isLessThanOrEqualTo(1e-12);
                        assertThat(report.duplicateCount()).isEqualTo(expectedVertexDuplicates);

                        if (expectedVertexDuplicates == 0) {
                                assertThat(report.validSimplex()).isTrue();
                                double[][] pureCanonical = ReferenceSetValidator.generateCanonicalTwoLayers(
                                                m, outerDivisions, innerDivisions);
                                SetComparisonReport vsCanonical = ReferenceSetValidator.compareSetsOrderIndependent(
                                                weights, pureCanonical, EQUIVALENCE_TOLERANCE);
                                assertThat(vsCanonical.verdict()).isEqualTo(Verdict.EQUIVALENT);
                                assertThat(vsCanonical.maxCoordinateError()).isLessThan(1e-13);
                        } else {
                                // W8D_156.dat e W10D_275.dat oficiais do jMetal 7.8 possuem 2 camadas não
                                // contraídas
                                double[][] uncontracted = ReferenceSetValidator.generateUncontractedTwoLayers(
                                                m, outerDivisions, innerDivisions);
                                SetComparisonReport vsUncontracted = ReferenceSetValidator.compareSetsOrderIndependent(
                                                weights, uncontracted, EQUIVALENCE_TOLERANCE);
                                assertThat(vsUncontracted.matchedCount()).isEqualTo(expectedPopSize);
                                assertThat(vsUncontracted.unmatchedFromA()).isZero();
                                assertThat(vsUncontracted.unmatchedFromB()).isZero();
                                assertThat(vsUncontracted.maxCoordinateError()).isLessThan(1e-15);
                                assertThat(vsUncontracted.duplicatesA()).isEqualTo(expectedVertexDuplicates);
                        }
                } finally {
                        if (prevZeroFlag == null) {
                                System.clearProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);
                        } else {
                                System.setProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, prevZeroFlag);
                        }
                }
        }

        @ParameterizedTest(name = "legacy m={0}, N={1}, H1={2}, H2={3}, file={4}")
        @MethodSource("legacyProtectedSixWeightSpecs")
        @DisplayName("T03.1 / T03.2: Auditoria forense somente leitura dos 6 arquivos históricos em MOEAD_Weights/ (0 -> 1e-5, contraídos)")
        void testProtectedLegacyMoeadWeightsForensicAudit(
                        int m,
                        int expectedPopSize,
                        int outerDivisions,
                        int innerDivisions,
                        String fileName) throws IOException {
                try (Stream<Path> list = Files.list(LEGACY_PROTECTED_WEIGHTS_DIR)) {
                        assertThat(list.filter(Files::isRegularFile).count())
                                        .as("MOEAD_Weights/ protegido deve preservar seus 43 arquivos históricos")
                                        .isEqualTo(43L);
                }

                Path filePath = LEGACY_PROTECTED_WEIGHTS_DIR.resolve(fileName);
                double[][] fileWeights = ReferenceSetValidator.validateMoeadWeightFile(
                                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);
                SetValidationReport report = ReferenceSetValidator.validateSimplexSet(
                                fileWeights, expectedPopSize, m, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

                assertThat(report.allFinite()).isTrue();
                assertThat(report.allNonNegative()).isTrue();
                assertThat(report.validSimplex()).isTrue();
                assertThat(report.duplicateCount()).isZero();
                assertThat(report.allStrictlyPositive()).isTrue();
                assertThat(report.minCoordinate())
                                .isCloseTo(ZERO_REPLACEMENT, org.assertj.core.data.Offset.offset(1e-12));
                assertThat(report.minSum()).isGreaterThanOrEqualTo(1.0 - 1e-6);
                assertThat(report.maxSum()).isLessThanOrEqualTo(1.0 + (m - 1) * ZERO_REPLACEMENT + 1e-6);

                double[][] pureCanonical = ReferenceSetValidator.generateCanonicalTwoLayers(m, outerDivisions,
                                innerDivisions);
                double[][] zeroReplacedCanonical = ReferenceSetValidator.generateZeroReplacedCanonical(
                                m, outerDivisions, innerDivisions, ZERO_REPLACEMENT);

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

                assertThat(vsPureCanonicalStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
                assertThat(vsPureCanonicalRelaxed.verdict()).isEqualTo(Verdict.EQUIVALENT);
        }

        @ParameterizedTest(name = "m={0}, N={1}, H1={2}, H2={3}, file={4}")
        @MethodSource("legacyProtectedSixWeightSpecs")
        @DisplayName("T03.3: Comparação forense entre arquivos históricos de MOEAD_Weights/ e saída de reference-calc.java")
        void testMoeadWeightsVsReferenceCalc(
                        int m,
                        int expectedPopSize,
                        int outerDivisions,
                        int innerDivisions,
                        String fileName) {
                Path filePath = LEGACY_PROTECTED_WEIGHTS_DIR.resolve(fileName);
                double[][] fileWeights = ReferenceSetValidator.validateMoeadWeightFile(
                                filePath, m, expectedPopSize, SUM_TOLERANCE, DUPLICATE_TOLERANCE);

                int layers = (innerDivisions == 0) ? 1 : 2;
                double[][] refCalcOutput = ReferenceSetValidator.simulateReferenceCalc(m, outerDivisions, layers);

                SetComparisonReport reportStrict = ReferenceSetValidator.compareSetsOrderIndependent(
                                fileWeights, refCalcOutput, EQUIVALENCE_TOLERANCE);

                assertThat(reportStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
                if (m == 2) {
                        assertThat(reportStrict.countB()).isZero();
                } else if (innerDivisions > 0) {
                        assertThat(reportStrict.countB()).isNotEqualTo(expectedPopSize);
                }
        }

        private record ManifestOfficialEntry(String sha256, int m, int vectors) {
        }

        @Test
        @DisplayName("T11.2 / T32: Verificação de integridade SHA-256 e geometria das 29 cópias oficiais do jMetal em resources/weightVectorFiles/")
        void testOfficialJMetalCopiesAndSha256Integrity() throws IOException {
                assertThat(AUDITED_MANIFEST_CSV).exists().isRegularFile();
                List<String> lines = Files.readAllLines(AUDITED_MANIFEST_CSV, StandardCharsets.UTF_8);

                Map<String, ManifestOfficialEntry> expectedOfficialByFile = new LinkedHashMap<>();
                for (int i = 1; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (line.isEmpty()) {
                                continue;
                        }
                        String[] cols = line.split(",", -1);
                        assertThat(cols).hasSize(6);
                        String status = cols[5].trim();
                        if (status.startsWith("IDENTICAL_TO_JMETAL_")) {
                                String origPath = cols[1].trim();
                                String fileName = origPath.substring(origPath.lastIndexOf('/') + 1);
                                expectedOfficialByFile.put(
                                                fileName,
                                                new ManifestOfficialEntry(
                                                                cols[0].trim(),
                                                                Integer.parseInt(cols[3].trim()),
                                                                Integer.parseInt(cols[4].trim())));
                        }
                }

                assertThat(expectedOfficialByFile).hasSize(29);

                for (Map.Entry<String, ManifestOfficialEntry> entry : expectedOfficialByFile.entrySet()) {
                        String fileName = entry.getKey();
                        ManifestOfficialEntry expected = entry.getValue();
                        Path copiedFile = OFFICIAL_WEIGHTS_DIR.resolve(fileName);

                        assertThat(copiedFile)
                                        .as("Arquivo oficial %s deve existir em resources/weightVectorFiles/", fileName)
                                        .exists()
                                        .isRegularFile();

                        assertThat(ReferenceSetValidator.matchesExpectedSha256CrossPlatform(
                                        copiedFile, expected.sha256()))
                                        .as(
                                                        "SHA-256 de %s em resources/weightVectorFiles/ deve coincidir com o manifesto oficial do jMetal 7.8 (LF/CRLF portável)",
                                                        fileName)
                                        .isTrue();

                        // Valida também estrutura numérica real (m, contagem de linhas, finitude,
                        // não-negatividade e soma no simplex).
                        // Nota: alguns arquivos pré-computados legados do jMetal (ex.: W3D_100.dat)
                        // possuem valores arredondados a 6 casas decimais
                        // cuja soma difere de 1.0 em até ~1e-5, logo usa-se tolerância 5e-5.
                        List<String> rawLines = Files.readAllLines(copiedFile, StandardCharsets.UTF_8);
                        List<double[]> rows = new ArrayList<>();
                        for (String raw : rawLines) {
                                if (raw != null && !raw.trim().isEmpty()) {
                                        String[] toks = raw.trim().split("[\\s,]+");
                                        assertThat(toks)
                                                        .as("Dimensão de %s deve ser m=%d", fileName, expected.m())
                                                        .hasSize(expected.m());
                                        double[] vec = new double[expected.m()];
                                        for (int j = 0; j < expected.m(); j++) {
                                                vec[j] = Double.parseDouble(toks[j]);
                                        }
                                        rows.add(vec);
                                }
                        }
                        assertThat(rows)
                                        .as("Contagem de vetores de %s deve ser %d", fileName, expected.vectors())
                                        .hasSize(expected.vectors());
                        SetValidationReport report = ReferenceSetValidator.validateSimplexSet(
                                        rows.toArray(double[][]::new),
                                        expected.vectors(),
                                        expected.m(),
                                        5e-5,
                                        DUPLICATE_TOLERANCE);
                        assertThat(report.allFinite()).isTrue();
                        assertThat(report.allNonNegative()).isTrue();
                        assertThat(report.maxSumDeviationFromOne()).isLessThanOrEqualTo(5e-5);
                }
        }

        @Test
        @DisplayName("T11.3: Substituição opcional de zeros (-DreplaceZeroWeights=false por padrão; isola em weights_lowzero_gen e protege weights_gen)")
        void testOptionalZeroReplacementProcedure(@TempDir Path tempDir) throws IOException {
                assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled()).isFalse();

                Path sourceFile = OFFICIAL_WEIGHTS_DIR.resolve("W3D_91.dat");
                String originalSha = ReferenceSetValidator.computeSha256Hex(sourceFile);

                Path tempLowZeroDir = tempDir.resolve("weights_lowzero_gen");

                ZeroReplacementReport disabledReport = ReferenceSetValidator.replaceZeroWeightsIfEnabled(
                                sourceFile, tempLowZeroDir, ZERO_REPLACEMENT);
                assertThat(disabledReport.enabled()).isFalse();
                assertThat(disabledReport.generated()).isFalse();
                assertThat(disabledReport.outputFile()).isNull();
                assertThat(disabledReport.replacedComponentCount()).isZero();
                assertThat(tempLowZeroDir.resolve("W3D_91.dat")).doesNotExist();

                assertThatThrownBy(
                                () -> ReferenceSetValidator.replaceZeroWeights(
                                                sourceFile, GENERATED_WEIGHTS_DIR, ZERO_REPLACEMENT, true))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining(
                                                "Proibido gravar variantes com substituição de zero em weights_gen/");

                ZeroReplacementReport enabledReport = ReferenceSetValidator.replaceZeroWeights(
                                sourceFile, tempLowZeroDir, ZERO_REPLACEMENT, true);

                assertThat(enabledReport.enabled()).isTrue();
                assertThat(enabledReport.generated()).isTrue();
                assertThat(enabledReport.outputFile()).isEqualTo(tempLowZeroDir.resolve("W3D_91.dat"));
                assertThat(enabledReport.outputFile()).exists().isRegularFile();
                assertThat(enabledReport.vectorCount()).isEqualTo(91);
                assertThat(enabledReport.dimension()).isEqualTo(3);
                assertThat(enabledReport.replacedComponentCount()).isEqualTo(39);
                assertThat(enabledReport.vectorSumsAfterReplacement()).hasSize(91);
                assertThat(enabledReport.minSum())
                                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
                assertThat(enabledReport.maxSum())
                                .isCloseTo(1.0 + 2 * ZERO_REPLACEMENT, org.assertj.core.data.Offset.offset(1e-12));
                assertThat(enabledReport.validationReport().validSimplex()).isTrue();
                assertThat(enabledReport.validationReport().allFinite()).isTrue();
                assertThat(enabledReport.validationReport().allNonNegative()).isTrue();
                assertThat(enabledReport.validationReport().allStrictlyPositive()).isTrue();
                assertThat(enabledReport.validationReport().duplicateCount()).isZero();

                assertThat(ReferenceSetValidator.computeSha256Hex(sourceFile)).isEqualTo(originalSha);

                double[][] validatedGen = ReferenceSetValidator.validateMoeadWeightFile(
                                enabledReport.outputFile(), 3, 91, SUM_TOLERANCE, DUPLICATE_TOLERANCE);
                assertThat(validatedGen).hasDimensions(91, 3);
        }

        @Test
        @DisplayName("T11.4: Teste de 1 execução reduzida do MOEA/DD com resources/weightVectorFiles/ e weights_lowzero_gen")
        void testSingleRunWithCopiedAndGeneratedWeightDirectories(@TempDir Path tempDir) {
                Problem<DoubleSolution> problem = new WFG1(4, 20, 3);
                int popSize = StaticParams.getPopulationSize(3); // 91
                CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
                MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(),
                                20.0);

                Path tempLowZeroDir = tempDir.resolve("weights_lowzero_gen");
                ReferenceSetValidator.replaceZeroWeights(
                                OFFICIAL_WEIGHTS_DIR.resolve("W3D_91.dat"), tempLowZeroDir, ZERO_REPLACEMENT, true);

                for (String dataDir : List.of(OFFICIAL_WEIGHTS_DIR.toString(), tempLowZeroDir.toString())) {
                        double[][] weights = WFGStudy.validateMoeadWeightFile(dataDir, problem, popSize);
                        assertThat(weights).hasDimensions(popSize, 3);

                        Algorithm<List<DoubleSolution>> algorithm = new MOEADBuilder(problem,
                                        MOEADBuilder.Variant.MOEADD)
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
                Path missingFile = tempDir.resolve("W3D_91_inexistente.dat");
                assertThatThrownBy(
                                () -> ReferenceSetValidator.validateMoeadWeightFile(
                                                missingFile, 3, 91, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("inexistente ou ilegível");

                Path wrongCountFile = tempDir.resolve("W3D_wrong_count.dat");
                Files.writeString(wrongCountFile, "1.0 0.0 0.0\n0.0 1.0 0.0\n0.0 0.0 1.0\n");
                assertThatThrownBy(
                                () -> ReferenceSetValidator.validateMoeadWeightFile(
                                                wrongCountFile, 3, 91, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Quantidade incorreta de vetores")
                                .hasMessageContaining("esperado 91 linhas, mas encontrado 3");

                Path wrongDimFile = tempDir.resolve("W3D_wrong_dim.dat");
                Files.writeString(wrongDimFile, "1.0 0.0 0.0\n0.25 0.25 0.25 0.25\n0.0 0.0 1.0\n");
                assertThatThrownBy(
                                () -> ReferenceSetValidator.validateMoeadWeightFile(
                                                wrongDimFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Dimensionalidade incorreta")
                                .hasMessageContaining("linha 2: esperado 3 colunas, mas encontrado 4");

                Path nanFile = tempDir.resolve("W3D_nan.dat");
                Files.writeString(nanFile, "1.0 0.0 0.0\n0.5 NaN 0.5\n0.0 0.0 1.0\n");
                assertThatThrownBy(
                                () -> ReferenceSetValidator.validateMoeadWeightFile(
                                                nanFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Valor não finito (NaN)");

                Path negativeWeightFile = tempDir.resolve("W3D_negative.dat");
                Files.writeString(negativeWeightFile, "1.0 0.0 0.0\n1.2 -0.2 0.0\n0.0 0.0 1.0\n");
                assertThatThrownBy(
                                () -> ReferenceSetValidator.validateMoeadWeightFile(
                                                negativeWeightFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Peso negativo");

                Path badSumFile = tempDir.resolve("W3D_bad_sum.dat");
                Files.writeString(badSumFile, "1.0 0.0 0.0\n0.5 0.5 0.5\n0.0 0.0 1.0\n");
                assertThatThrownBy(
                                () -> ReferenceSetValidator.validateMoeadWeightFile(
                                                badSumFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Soma dos pesos inválida");

                Path duplicateWeightFile = tempDir.resolve("W3D_duplicates.dat");
                Files.writeString(duplicateWeightFile, "1.0 0.0 0.0\n0.5 0.5 0.0\n0.5 0.5 0.0\n");
                assertThatThrownBy(
                                () -> ReferenceSetValidator.validateMoeadWeightFile(
                                                duplicateWeightFile, 3, 3, SUM_TOLERANCE, DUPLICATE_TOLERANCE))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Vetores de pesos duplicados ou quase duplicados");
        }
}

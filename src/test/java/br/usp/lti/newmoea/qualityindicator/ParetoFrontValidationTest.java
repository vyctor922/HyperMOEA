package br.usp.lti.newmoea.qualityindicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.util.ParetoFrontValidator;
import br.usp.lti.newmoea.util.ParetoFrontValidator.FrontValidationSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.util.NormalizeUtils;
import org.uma.jmetal.util.VectorUtils;

/**
 * Suite de validação das frentes de Pareto de referência (T04) em {@code pareto_fronts/csv/}.
 *
 * <p>Cobre:
 * <ol>
 *   <li>Existência, dimensionalidade e finitude das 68 frentes referenciadas em {@code WFGStudy.java}
 *       (54 arquivos WFG1–WFG9 para \(m \in \{2, 3, 5, 8, 10, 15\}\) e 14 arquivos DTLZ1–DTLZ7 para
 *       \(m \in \{2, 3\}\));</li>
 *   <li>Validação analítica exata de DTLZ1–DTLZ7 (\(m \in \{2, 3\}\));</li>
 *   <li>Validação analítica do hiper-elipsoide côncavo de WFG4–WFG9 (\(m \in \{2, 3, 5, 8, 10, 15\}\));</li>
 *   <li>Auditoria programática das características e anomalias dos arquivos distribuídos pelo jMetal
 *       (WFG1–WFG3, duplicatas em \(m \ge 5\), valores negativos em {@code WFG3.2D.csv} e ausência de
 *       arquivos DTLZ para \(m \in \{5, 8, 10, 15\}\));</li>
 *   <li>Compatibilidade com os indicadores do pipeline ({@code PISAHypervolume},
 *       {@code NormalizedHypervolume}, {@code IGD}, {@code IGD+} e {@code R2});</li>
 *   <li>Testes negativos comprovando rejeição controlada de frentes inválidas ou malformadas.</li>
 * </ol>
 */
class ParetoFrontValidationTest {

    private static final Path PARETO_FRONTS_DIR = Paths.get("pareto_fronts", "csv");
    private static final int[] WFG_OBJECTIVES = {2, 3, 5, 8, 10, 15};
    private static final int[] DTLZ_OBJECTIVES = {2, 3};

    static Stream<Arguments> all68ReferencedFrontsProvider() {
        List<Arguments> args = new ArrayList<>();
        for (int p = 1; p <= 9; p++) {
            for (int m : WFG_OBJECTIVES) {
                args.add(Arguments.of("WFG" + p, m, String.format("WFG%d.%dD.csv", p, m)));
            }
        }
        for (int p = 1; p <= 7; p++) {
            for (int m : DTLZ_OBJECTIVES) {
                args.add(Arguments.of("DTLZ" + p, m, String.format("DTLZ%d.%dD.csv", p, m)));
            }
        }
        return args.stream();
    }

    static Stream<Arguments> wfgConcaveEllipsoidFrontsProvider() {
        List<Arguments> args = new ArrayList<>();
        for (int p = 4; p <= 9; p++) {
            for (int m : WFG_OBJECTIVES) {
                args.add(Arguments.of("WFG" + p, m, String.format("WFG%d.%dD.csv", p, m)));
            }
        }
        return args.stream();
    }

    // =========================================================================
    // TESTE 1: Existência, dimensionalidade e finitude das 68 frentes do estudo
    // =========================================================================

    @Test
    @DisplayName("T04.1a: Contagem exata das 68 frentes referenciadas em WFGStudy (54 WFG + 14 DTLZ)")
    void testTotalReferencedFrontsCount() {
        long count = all68ReferencedFrontsProvider().count();
        assertThat(count).isEqualTo(68L);
    }

    @ParameterizedTest(name = "[{index}] {2} (m={1})")
    @MethodSource("all68ReferencedFrontsProvider")
    @DisplayName("T04.1b: Todas as 68 frentes existem, têm dimensão m exata e valores 100% finitos")
    void testAll68FrontsExistHaveCorrectDimensionAndFiniteValues(
            String problemName, int m, String fileName) {
        Path csvPath = PARETO_FRONTS_DIR.resolve(fileName);
        FrontValidationSummary summary = ParetoFrontValidator.validateParetoFrontFile(csvPath, m);

        assertThat(summary.dimension()).isEqualTo(m);
        assertThat(summary.totalPoints()).isGreaterThan(0);
        assertThat(summary.uniquePoints()).isGreaterThan(0);
        assertThat(summary.points().length).isEqualTo(summary.totalPoints());
        assertThat(summary.minPerObjective()).hasSize(m);
        assertThat(summary.maxPerObjective()).hasSize(m);

        for (int j = 0; j < m; j++) {
            assertThat(summary.maxPerObjective()[j])
                    .as("Objetivo %d de %s deve ter max > min para permitir normalização", j + 1, fileName)
                    .isGreaterThan(summary.minPerObjective()[j]);
        }
    }

    // =========================================================================
    // TESTE 2: Validação analítica exata de DTLZ1 a DTLZ7 (m in {2, 3})
    // =========================================================================

    @Test
    @DisplayName("T04.2a: DTLZ1 (2D e 3D) satisfaz hiperplano linear sum(f_j) = 0.5 e f_j >= 0")
    void testDtlz1AnalyticalHyperplane() {
        for (int m : DTLZ_OBJECTIVES) {
            Path file = PARETO_FRONTS_DIR.resolve(String.format("DTLZ1.%dD.csv", m));
            FrontValidationSummary summary = ParetoFrontValidator.validateParetoFrontFile(file, m);
            assertThat(summary.hasNegativeValues()).isFalse();

            double maxErr = ParetoFrontValidator.validateDtlz1Analytical(summary.points(), 1e-4);
            assertThat(maxErr).isLessThanOrEqualTo(1e-8);
        }
    }

    @Test
    @DisplayName("T04.2b: DTLZ2, DTLZ3 e DTLZ4 (2D e 3D) satisfazem esfera unitária sum(f_j^2) = 1.0 e f_j >= 0")
    void testDtlz2To4AnalyticalUnitSphere() {
        for (int p = 2; p <= 4; p++) {
            for (int m : DTLZ_OBJECTIVES) {
                Path file = PARETO_FRONTS_DIR.resolve(String.format("DTLZ%d.%dD.csv", p, m));
                FrontValidationSummary summary = ParetoFrontValidator.validateParetoFrontFile(file, m);
                assertThat(summary.hasNegativeValues()).isFalse();
                assertThat(summary.duplicatePoints()).isZero();

                double maxErr = ParetoFrontValidator.validateUnitSphereAnalytical(summary.points(), 1e-3);
                assertThat(maxErr).isLessThanOrEqualTo(2e-6);
            }
        }
    }

    @Test
    @DisplayName("T04.2c: DTLZ5 e DTLZ6 (2D e 3D) satisfazem esfera unitária sum(f_j^2) = 1.0 e colinearidade f_1 = f_2 em 3D")
    void testDtlz5And6AnalyticalDegenerateCurve() {
        for (int p = 5; p <= 6; p++) {
            for (int m : DTLZ_OBJECTIVES) {
                Path file = PARETO_FRONTS_DIR.resolve(String.format("DTLZ%d.%dD.csv", p, m));
                FrontValidationSummary summary = ParetoFrontValidator.validateParetoFrontFile(file, m);
                assertThat(summary.hasNegativeValues()).isFalse();

                double maxErr =
                        ParetoFrontValidator.validateDtlz5And6DegenerateAnalytical(
                                summary.points(), 1e-3);
                assertThat(maxErr).isLessThanOrEqualTo(1e-12);
            }
        }
    }

    @Test
    @DisplayName("T04.2d: DTLZ7 (2D e 3D) satisfaz equação analítica desconexa de f_m (Deb et al., 2002)")
    void testDtlz7AnalyticalDisconnectedFront() {
        for (int m : DTLZ_OBJECTIVES) {
            Path file = PARETO_FRONTS_DIR.resolve(String.format("DTLZ7.%dD.csv", m));
            FrontValidationSummary summary = ParetoFrontValidator.validateParetoFrontFile(file, m);
            assertThat(summary.hasNegativeValues()).isFalse();
            assertThat(summary.duplicatePoints()).isZero();

            double maxErr = ParetoFrontValidator.validateDtlz7Analytical(summary.points(), 1e-4);
            assertThat(maxErr).isLessThanOrEqualTo(5e-6);
        }
    }

    // =========================================================================
    // TESTE 3: Validação analítica de WFG4 a WFG9 (m in {2, 3, 5, 8, 10, 15})
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {2} (m={1})")
    @MethodSource("wfgConcaveEllipsoidFrontsProvider")
    @DisplayName("T04.3: WFG4–WFG9 satisfazem hiper-elipsoide côncavo sum((f_j / 2j)^2) = 1.0 e f_j >= 0")
    void testWfg4To9AnalyticalConcaveEllipsoid(String problemName, int m, String fileName) {
        Path file = PARETO_FRONTS_DIR.resolve(fileName);
        FrontValidationSummary summary = ParetoFrontValidator.validateParetoFrontFile(file, m);

        assertThat(summary.hasNegativeValues())
                .as("%s não deve possuir valores negativos", fileName)
                .isFalse();

        double maxEllipsoidError =
                ParetoFrontValidator.validateWfgConcaveEllipsoid(summary.points(), 1e-4);
        assertThat(maxEllipsoidError)
                .as("Erro máximo do hiper-elipsoide em %s", fileName)
                .isLessThanOrEqualTo(1e-5);
    }

    // =========================================================================
    // TESTE 4: Auditoria de anomalias conhecidas nos arquivos WFG do jMetal
    // =========================================================================

    @Test
    @DisplayName("T04.4a: Auditoria de WFG3.2D.csv (extrapolação linear com f_2 < 0 para f_1 in (2, 3] satisfazendo f_1/2 + f_2/4 = 1)")
    void testAuditWfg32dNegativeValuesAndLinearEquation() {
        Path file = PARETO_FRONTS_DIR.resolve("WFG3.2D.csv");
        FrontValidationSummary summary = ParetoFrontValidator.validateParetoFrontFile(file, 2);

        assertThat(summary.totalPoints()).isEqualTo(916);
        assertThat(summary.uniquePoints()).isEqualTo(916);
        assertThat(summary.hasNegativeValues()).isTrue();
        assertThat(summary.minPerObjective()[0]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(summary.maxPerObjective()[0]).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(summary.minPerObjective()[1]).isCloseTo(-2.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(summary.maxPerObjective()[1]).isCloseTo(4.0, org.assertj.core.data.Offset.offset(1e-5));

        // Todos os 916 pontos de WFG3.2D.csv satisfazem a reta f_1/2 + f_2/4 = 1
        double maxLinearErr = 0.0;
        int negativeRowCount = 0;
        for (double[] pt : summary.points()) {
            double err = Math.abs((pt[0] / 2.0) + (pt[1] / 4.0) - 1.0);
            maxLinearErr = Math.max(maxLinearErr, err);
            if (pt[1] < 0.0) {
                negativeRowCount++;
            }
        }
        assertThat(maxLinearErr).isLessThanOrEqualTo(1e-7);
        assertThat(negativeRowCount).isEqualTo(120);
    }

    @Test
    @DisplayName("T04.4b: Auditoria de contagens e duplicatas nos arquivos WFG de m in {5, 8, 10, 15}")
    void testAuditWfgManyObjectivePointCountsAndDuplicates() {
        int[][] expectedCountsByM = {
            {5, 210},
            {8, 156},
            {10, 275},
            {15, 135}
        };

        for (int p = 1; p <= 9; p++) {
            for (int[] pair : expectedCountsByM) {
                int m = pair[0];
                int expectedTotal = pair[1];
                String fileName = String.format("WFG%d.%dD.csv", p, m);
                FrontValidationSummary summary =
                        ParetoFrontValidator.validateParetoFrontFile(
                                PARETO_FRONTS_DIR.resolve(fileName), m);

                // As frentes WFG de m >= 5 do jMetal têm exatamente N linhas (210, 156, 275, 135)
                // mas possuem duplicatas internas em todas as combinações
                assertThat(summary.totalPoints())
                        .as("Contagem total de linhas de %s", fileName)
                        .isEqualTo(expectedTotal);
                assertThat(summary.duplicatePoints())
                        .as("%s contém duplicatas documentadas na distribuição do jMetal", fileName)
                        .isGreaterThan(0);
                assertThat(summary.uniquePoints()).isLessThan(summary.totalPoints());
            }
        }
    }

    @Test
    @DisplayName("T04.4c: Auditoria de ausência de arquivos DTLZ para m in {5, 8, 10, 15} em pareto_fronts/csv/")
    void testAuditAbsenceOfManyObjectiveDtlzFronts() {
        int[] missingM = {5, 8, 10, 15};
        for (int p = 1; p <= 7; p++) {
            for (int m : missingM) {
                Path path = PARETO_FRONTS_DIR.resolve(String.format("DTLZ%d.%dD.csv", p, m));
                assertThat(Files.exists(path))
                        .as("Arquivo %s não existe no repositório oficial do jMetal nem localmente", path)
                        .isFalse();
            }
        }
    }

    // =========================================================================
    // TESTE 5: Compatibilidade com os indicadores do pipeline do jMetal 7.8
    // =========================================================================

    @Test
    @DisplayName("T04.5: Frentes normalizadas são consumidas corretamente por PISAHypervolume, NormalizedHypervolume, IGD, IGD+ e R2")
    void testIndicatorsCompatibilityWithNormalizedReferenceFronts() throws IOException {
        String[] sampleFiles = {"WFG1.2D.csv", "WFG4.3D.csv", "DTLZ1.2D.csv", "DTLZ2.3D.csv", "DTLZ7.3D.csv"};

        for (String fileName : sampleFiles) {
            Path path = PARETO_FRONTS_DIR.resolve(fileName);
            double[][] referenceFront = VectorUtils.readVectors(path.toString(), ",");
            double[][] normalizedReferenceFront = NormalizeUtils.normalize(referenceFront);

            // Subconjunto dos primeiros 25 pontos como frente de aproximação de teste
            int subsetSize = Math.min(25, normalizedReferenceFront.length);
            double[][] approxFront = new double[subsetSize][];
            for (int i = 0; i < subsetSize; i++) {
                approxFront[i] = normalizedReferenceFront[i].clone();
            }

            PISAHypervolume hv = new PISAHypervolume(normalizedReferenceFront);
            NormalizedHypervolume nhv = new NormalizedHypervolume(normalizedReferenceFront);
            InvertedGenerationalDistance igd =
                    new InvertedGenerationalDistance(normalizedReferenceFront);
            InvertedGenerationalDistancePlus igdPlus =
                    new InvertedGenerationalDistancePlus(normalizedReferenceFront);
            R2 r2 = new R2(normalizedReferenceFront);

            double hvVal = hv.compute(approxFront);
            double nhvVal = nhv.compute(approxFront);
            double igdExact = igd.compute(normalizedReferenceFront);
            double igdPlusExact = igdPlus.compute(normalizedReferenceFront);
            double r2Val = r2.compute(approxFront);

            assertThat(hvVal).as("HV para %s", fileName).isFinite().isGreaterThan(0.0);
            assertThat(nhvVal).as("NHV para %s", fileName).isFinite();
            assertThat(igdExact)
                    .as("IGD exato para %s", fileName)
                    .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(igdPlusExact)
                    .as("IGD+ exato para %s", fileName)
                    .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(r2Val).as("R2 para %s", fileName).isFinite().isGreaterThanOrEqualTo(0.0);
        }
    }

    // =========================================================================
    // TESTE 6: Testes negativos obrigatórios (rejeição controlada)
    // =========================================================================

    @Test
    @DisplayName("T04.6a: Rejeita arquivo de frente ausente com IllegalArgumentException")
    void testRejectMissingFrontFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("NAO_EXISTE.3D.csv");
        assertThatThrownBy(() -> ParetoFrontValidator.validateParetoFrontFile(missing, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistente");
    }

    @Test
    @DisplayName("T04.6b: Rejeita arquivo vazio ou apenas com espaços em branco com IllegalArgumentException")
    void testRejectEmptyFrontFile(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("empty.2D.csv");
        Files.writeString(emptyFile, "   \n  \n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ParetoFrontValidator.validateParetoFrontFile(emptyFile, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    @DisplayName("T04.6c: Rejeita arquivo com dimensionalidade incorreta (ex.: linha com 2 colunas quando m=3)")
    void testRejectWrongDimensionFrontFile(@TempDir Path tempDir) throws IOException {
        Path badDimFile = tempDir.resolve("bad_dim.3D.csv");
        Files.writeString(
                badDimFile,
                "0.1,0.2,0.7\n0.3,0.7\n0.2,0.3,0.5\n",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ParetoFrontValidator.validateParetoFrontFile(badDimFile, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dimensionalidade incorreta")
                .hasMessageContaining("linha 2");
    }

    @Test
    @DisplayName("T04.6d: Rejeita arquivo com NaN, Infinity ou texto não numérico com IllegalArgumentException")
    void testRejectNonFiniteOrMalformedValues(@TempDir Path tempDir) throws IOException {
        Path nanFile = tempDir.resolve("nan.2D.csv");
        Files.writeString(nanFile, "0.1,0.4\n0.2,NaN\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ParetoFrontValidator.validateParetoFrontFile(nanFile, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não finito");

        Path infFile = tempDir.resolve("inf.2D.csv");
        Files.writeString(infFile, "0.1,Infinity\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ParetoFrontValidator.validateParetoFrontFile(infFile, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não finito");

        Path textFile = tempDir.resolve("text.2D.csv");
        Files.writeString(textFile, "0.1,abc\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ParetoFrontValidator.validateParetoFrontFile(textFile, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valor numérico inválido");
    }

    @Test
    @DisplayName("T04.6e: Validador analítico rejeita frentes que violam a geometria analítica (DTLZ1, DTLZ2, DTLZ7, WFG4)")
    void testRejectAnalyticalGeometryViolations() {
        // Ponto fora do hiperplano soma = 0.5 de DTLZ1 (ex.: soma = 1.0 como em Das & Dennis)
        double[][] badDtlz1 = {{0.5, 0.5}, {0.2, 0.3}};
        assertThatThrownBy(() -> ParetoFrontValidator.validateDtlz1Analytical(badDtlz1, 1e-4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DTLZ1 violado");

        // Ponto fora da esfera unitária de DTLZ2
        double[][] badSphere = {{1.0, 0.0}, {0.5, 0.5}};
        assertThatThrownBy(() -> ParetoFrontValidator.validateUnitSphereAnalytical(badSphere, 1e-3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Esfera unitária violada");

        // Ponto violando colinearidade f1 = f2 de DTLZ5 em 3D (embora na esfera unitária)
        double[][] badDtlz5 = {{0.6, 0.8, 0.0}};
        assertThatThrownBy(
                        () -> ParetoFrontValidator.validateDtlz5And6DegenerateAnalytical(badDtlz5, 1e-3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Degenerescência DTLZ5/6 violada");

        // Ponto violando equação analítica de DTLZ7
        double[][] badDtlz7 = {{0.2, 1.0}};
        assertThatThrownBy(() -> ParetoFrontValidator.validateDtlz7Analytical(badDtlz7, 1e-4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DTLZ7 violado");

        // Ponto fora do hiper-elipsoide côncavo de WFG4–WFG9
        double[][] badWfgEllipsoid = {{2.0, 4.0}}; // (2/2)^2 + (4/4)^2 = 2 != 1
        assertThatThrownBy(
                        () -> ParetoFrontValidator.validateWfgConcaveEllipsoid(badWfgEllipsoid, 1e-4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hiper-elipsoide WFG violado");
    }
}

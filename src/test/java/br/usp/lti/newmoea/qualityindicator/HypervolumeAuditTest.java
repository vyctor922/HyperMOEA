package br.usp.lti.newmoea.qualityindicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.HypervolumeContribution2D;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.Hypervolume3D;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume;
import org.uma.jmetal.util.NormalizeUtils;
import org.uma.jmetal.util.ReferencePointUtils;
import org.uma.jmetal.util.VectorUtils;

/**
 * Auditoria científica e técnica de Hipervolume e Normalização (T08).
 *
 * <p>Cobre a verificação dos pontos de referência por problema e dimensão \(m\), dominância estrita
 * sobre as 68 frentes de referência ativas em {@code pareto_fronts/csv/}, comportamento em bytecode
 * de {@link PISAHypervolume}, {@link WFGHypervolume}, {@link Hypervolume3D} e
 * {@link NormalizedHypervolume}, e proteção contra divisão por zero em {@link NormalizeUtils}.
 */
class HypervolumeAuditTest {

    private static final int[] WFG_OBJECTIVES = {2, 3, 5, 8, 10, 15};
    private static final int[] DTLZ_CSV_OBJECTIVES = {2, 3};

    @ParameterizedTest(name = "m={0}")
    @ValueSource(ints = {2, 3, 5, 8, 10, 15})
    @DisplayName("T08: Pontos de referência por problema (WFG1..9 e DTLZ1..7) possuem dimensão m, valores finitos e limites superiores teóricos consistentes")
    void testProblemSpecificReferencePointsAndTheoreticalBounds(int m) {
        // WFG1..WFG9: S_j = 2j, z_j = 2.2 * j
        for (int p = 1; p <= 9; p++) {
            String problem = "WFG" + p;
            double[] upperBounds = StaticParams.getParetoFrontUpperBounds(problem, m);
            double[] refPoint = StaticParams.getHypervolumeReferencePoint(problem, m);
            double[] refPointFromTag = StaticParams.getHypervolumeReferencePoint(problem + "_" + m, m);

            assertThat(upperBounds).hasSize(m);
            assertThat(refPoint).hasSize(m).containsExactly(refPointFromTag);

            for (int j = 0; j < m; j++) {
                double expectedBound = 2.0 * (j + 1);
                double expectedRef = ("WFG3".equals(problem) && m == 2 && j == 0) ? 3.1 : 2.2 * (j + 1);
                assertThat(upperBounds[j]).isFinite().isCloseTo(expectedBound, Offset.offset(1e-12));
                assertThat(refPoint[j]).isFinite().isCloseTo(expectedRef, Offset.offset(1e-12));
                assertThat(refPoint[j]).isGreaterThan(upperBounds[j]);
            }
        }

        // DTLZ1: f_j in [0, 0.5], z_j = 0.6
        double[] dtlz1Bounds = StaticParams.getParetoFrontUpperBounds("DTLZ1", m);
        double[] dtlz1Ref = StaticParams.getHypervolumeReferencePoint("DTLZ1", m);
        assertThat(dtlz1Bounds).hasSize(m);
        assertThat(dtlz1Ref).hasSize(m);
        for (int j = 0; j < m; j++) {
            assertThat(dtlz1Bounds[j]).isCloseTo(0.5, Offset.offset(1e-12));
            assertThat(dtlz1Ref[j]).isCloseTo(0.6, Offset.offset(1e-12));
            assertThat(dtlz1Ref[j]).isGreaterThan(dtlz1Bounds[j]);
        }

        // DTLZ2..DTLZ6: f_j <= 1.0, z_j = 1.1
        for (int p = 2; p <= 6; p++) {
            String problem = "DTLZ" + p;
            double[] bounds = StaticParams.getParetoFrontUpperBounds(problem, m);
            double[] ref = StaticParams.getHypervolumeReferencePoint(problem, m);
            assertThat(bounds).hasSize(m);
            assertThat(ref).hasSize(m);
            for (int j = 0; j < m; j++) {
                assertThat(bounds[j]).isCloseTo(1.0, Offset.offset(1e-12));
                assertThat(ref[j]).isCloseTo(1.1, Offset.offset(1e-12));
                assertThat(ref[j]).isGreaterThan(bounds[j]);
            }
        }

        // DTLZ7: f_1..f_{m-1} in [0, 1.0], f_m in [0, 2m], z = (1.1, ..., 1.1, 2.1 * m)
        double[] dtlz7Bounds = StaticParams.getParetoFrontUpperBounds("DTLZ7", m);
        double[] dtlz7Ref = StaticParams.getHypervolumeReferencePoint("DTLZ7", m);
        assertThat(dtlz7Bounds).hasSize(m);
        assertThat(dtlz7Ref).hasSize(m);
        for (int j = 0; j < m - 1; j++) {
            assertThat(dtlz7Bounds[j]).isCloseTo(1.0, Offset.offset(1e-12));
            assertThat(dtlz7Ref[j]).isCloseTo(1.1, Offset.offset(1e-12));
            assertThat(dtlz7Ref[j]).isGreaterThan(dtlz7Bounds[j]);
        }
        assertThat(dtlz7Bounds[m - 1]).isCloseTo(2.0 * m, Offset.offset(1e-12));
        assertThat(dtlz7Ref[m - 1]).isCloseTo(2.1 * m, Offset.offset(1e-12));
        assertThat(dtlz7Ref[m - 1]).isGreaterThan(dtlz7Bounds[m - 1]);

        // Regime normalizado [0, 1]^m
        double[] normRef = StaticParams.getNormalizedHypervolumeReferencePoint(m);
        assertThat(normRef).hasSize(m);
        for (int j = 0; j < m; j++) {
            assertThat(normRef[j]).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("T08: Validação negativa estrita para identificadores de problemas inválidos ou m < 2")
    void testStrictValidationRejectsInvalidProblemsAndDimensions() {
        String[] invalidNames = {
            null, "", "   ", "WFG", "WFG0", "WFG10", "DTLZ", "DTLZ0", "DTLZ8", "UF1", "ZDT1"
        };
        for (String invalid : invalidNames) {
            assertThatThrownBy(() -> StaticParams.getHypervolumeReferencePoint(invalid, 3))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StaticParams.getParetoFrontUpperBounds(invalid, 3))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StaticParams.normalizeProblemId(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        int[] invalidM = {-5, 0, 1};
        for (int m : invalidM) {
            assertThatThrownBy(() -> StaticParams.getHypervolumeReferencePoint("WFG1", m))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StaticParams.getParetoFrontUpperBounds("DTLZ2", m))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StaticParams.getNormalizedHypervolumeReferencePoint(m))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("T08: Ponto de referência domina estritamente (f_j < z_j) 100% dos pontos das 68 frentes ativas em pareto_fronts/csv/")
    void testReferencePointStrictlyDominatesAll68ActiveReferenceFronts() throws IOException {
        int validatedFiles = 0;

        // 54 frentes WFG: WFG1..WFG9 em m in {2, 3, 5, 8, 10, 15}
        for (int p = 1; p <= 9; p++) {
            String problem = "WFG" + p;
            for (int m : WFG_OBJECTIVES) {
                Path csvPath = Path.of("pareto_fronts", "csv", problem + "." + m + "D.csv");
                assertThat(csvPath).exists();
                double[] refPoint = StaticParams.getHypervolumeReferencePoint(problem, m);
                assertAllPointsStrictlyInsideReferenceBox(csvPath, refPoint, m);
                validatedFiles++;
            }
        }

        // 14 frentes DTLZ: DTLZ1..DTLZ7 em m in {2, 3}
        for (int p = 1; p <= 7; p++) {
            String problem = "DTLZ" + p;
            for (int m : DTLZ_CSV_OBJECTIVES) {
                Path csvPath = Path.of("pareto_fronts", "csv", problem + "." + m + "D.csv");
                assertThat(csvPath).exists();
                double[] refPoint = StaticParams.getHypervolumeReferencePoint(problem, m);
                assertAllPointsStrictlyInsideReferenceBox(csvPath, refPoint, m);
                validatedFiles++;
            }
        }

        assertThat(validatedFiles).isEqualTo(68);
    }

    @Test
    @DisplayName("T08: Evidência empírica de que (2,...,2) para WFG e (1,...,1) para DTLZ7 falhavam no espaço bruto")
    void testUniformProvisionalReferencePointsFailInRawObjectiveSpace() throws IOException {
        // WFG4.3D.csv possui max = [2.0, 4.0, 6.0]; logo (2.0, 2.0, 2.0) falha para f_2 e f_3
        double[][] wfg4Front3D = readCsvMatrix(Path.of("pareto_fronts", "csv", "WFG4.3D.csv"));
        int pointsOutsideUniform2 = 0;
        for (double[] point : wfg4Front3D) {
            if (point[0] >= 2.0 || point[1] >= 2.0 || point[2] >= 2.0) {
                pointsOutsideUniform2++;
            }
        }
        assertThat(pointsOutsideUniform2).isGreaterThan(0);

        // DTLZ7.3D.csv possui f_3 in [2.614, 6.0]; logo (1.0, 1.0, 1.0) falha para 100% dos pontos em f_3
        double[][] dtlz7Front3D = readCsvMatrix(Path.of("pareto_fronts", "csv", "DTLZ7.3D.csv"));
        int pointsOutsideUniform1 = 0;
        for (double[] point : dtlz7Front3D) {
            if (point[2] > 1.0) {
                pointsOutsideUniform1++;
            }
        }
        assertThat(pointsOutsideUniform1).isEqualTo(dtlz7Front3D.length);
    }

    @Test
    @DisplayName("T08: Auditoria de bytecode do PISAHypervolume vs WFGHypervolume vs Hypervolume3D e exatidão analítica")
    void testExactHypervolumeImplementationsAndBytecodeBehavior() {
        // Frente 2D no quadrado unitário: A = {(0.2, 0.8), (0.5, 0.4), (0.9, 0.1)} com ref = (1.0, 1.0)
        // Volume analítico exato:
        // (1.0 - 0.2)*(1.0 - 0.8) + (1.0 - 0.5)*(0.8 - 0.4) + (1.0 - 0.9)*(0.4 - 0.1)
        // = 0.8*0.2 + 0.5*0.4 + 0.1*0.3 = 0.16 + 0.20 + 0.03 = 0.39
        double[][] front2D = {
            {0.2, 0.8},
            {0.5, 0.4},
            {0.9, 0.1}
        };
        double[] ref2D = {1.0, 1.0};
        double expectedHv2D = 0.39;

        PISAHypervolume pisaHv2D = new PISAHypervolume(ref2D);
        WFGHypervolume wfgHv2D = new WFGHypervolume(ref2D);
        HypervolumeContribution2D hvContrib2D = new HypervolumeContribution2D();
        double[] contributions2D = hvContrib2D.compute(front2D, ref2D);

        assertThat(pisaHv2D.compute(front2D)).isCloseTo(expectedHv2D, Offset.offset(1e-12));
        assertThat(wfgHv2D.compute(front2D)).isCloseTo(expectedHv2D, Offset.offset(1e-12));
        assertThat(contributions2D).hasSize(3);
        for (double c : contributions2D) {
            assertThat(c).isFinite().isGreaterThan(0.0);
        }

        // Frente 3D com 1 ponto (0.2, 0.3, 0.5) e ref = (1.0, 1.0, 1.0) -> HV = 0.8 * 0.7 * 0.5 = 0.28
        double[][] front3D = {{0.2, 0.3, 0.5}};
        double[] ref3D = {1.0, 1.0, 1.0};
        double expectedHv3D = 0.8 * 0.7 * 0.5;

        PISAHypervolume pisaHv3D = new PISAHypervolume(ref3D);
        WFGHypervolume wfgHv3D = new WFGHypervolume(ref3D);
        Hypervolume3D hv3D = new Hypervolume3D(ref3D);

        assertThat(pisaHv3D.compute(front3D)).isCloseTo(expectedHv3D, Offset.offset(1e-12));
        assertThat(wfgHv3D.compute(front3D)).isCloseTo(expectedHv3D, Offset.offset(1e-12));
        assertThat(hv3D.compute(front3D)).isCloseTo(expectedHv3D, Offset.offset(1e-12));

        // Comportamento de ReferencePointUtils no bytecode do jMetal 7.8:
        double[][] diagMatrix = ReferencePointUtils.createReferenceFrontFromReferencePoint(new double[] {2.2, 4.4});
        assertThat(diagMatrix).isDeepEqualTo(new double[][] {{2.2, 0.0}, {0.0, 4.4}});
        assertThat(ReferencePointUtils.extractReferencePointFromReferenceFront(diagMatrix))
                .containsExactly(2.2, 4.4);

        // Evidência de bytecode: VectorUtils.getInvertedFront inverte em relação a (1.0, ..., 1.0)
        // truncando coordenadas > 1.0 para 0.0 e coordenadas < 0.0 para 1.0
        double[][] rawPoints = {{-0.5, 0.25, 1.5}};
        double[][] inverted = VectorUtils.getInvertedFront(rawPoints);
        assertThat(inverted[0]).containsExactly(1.0, 0.75, 0.0);
    }

    @Test
    @DisplayName("T08: NormalizedHypervolume segue NHV(A) = 1 - HV(A)/HV(R), minimização e admite valores negativos se HV(A) > HV(R)")
    void testNormalizedHypervolumeFormulaAndNegativeBehavior() {
        double[][] referenceFront = {
            {0.2, 0.8},
            {0.8, 0.2}
        };
        PISAHypervolume hv = new PISAHypervolume(referenceFront);
        NormalizedHypervolume nhv = new NormalizedHypervolume(referenceFront);

        assertThat(hv.isTheLowerTheIndicatorValueTheBetter()).isFalse();
        assertThat(nhv.isTheLowerTheIndicatorValueTheBetter()).isTrue();

        // NHV da própria frente de referência é 0.0
        assertThat(nhv.compute(referenceFront)).isCloseTo(0.0, Offset.offset(1e-12));

        // Frente dominada (pior): NHV > 0
        double[][] worseFront = {{0.6, 0.6}};
        double expectedNhvWorse = 1.0 - (hv.compute(worseFront) / hv.compute(referenceFront));
        assertThat(nhv.compute(worseFront)).isGreaterThan(0.0).isCloseTo(expectedNhvWorse, Offset.offset(1e-12));

        // Frente que domina a frente de referência amostrada: HV(A) > HV(R) => NHV(A) < 0
        double[][] dominatingFront = {{0.05, 0.05}};
        double expectedNhvDominating = 1.0 - (hv.compute(dominatingFront) / hv.compute(referenceFront));
        assertThat(nhv.compute(dominatingFront))
                .isLessThan(0.0)
                .isCloseTo(expectedNhvDominating, Offset.offset(1e-12));
    }

    @Test
    @DisplayName("T08: NormalizeUtils normaliza em [0,1]^m a partir da frente de referência e protege contra divisão por zero (min == max)")
    void testNormalizeUtilsBehaviorAndZeroRangeProtection() {
        double[][] referenceFront = {
            {0.0, 2.0, 5.0},
            {2.0, 4.0, 5.0} // 3ª coluna constante (min == max == 5.0)
        };

        double[] minValues = NormalizeUtils.getMinValuesOfTheColumnsOfAMatrix(referenceFront);
        double[] maxValues = NormalizeUtils.getMaxValuesOfTheColumnsOfAMatrix(referenceFront);

        assertThat(minValues).containsExactly(0.0, 2.0, 5.0);
        assertThat(maxValues).containsExactly(2.0, 4.0, 5.0);

        double[][] normalizedRef = NormalizeUtils.normalize(referenceFront, minValues, maxValues);
        // Em jMetal 7.8, quando max == min, NormalizeUtils.normalize retorna (minRange + maxRange) / 2.0 = 0.5 (sem NaN/Infinity)
        assertThat(normalizedRef[0]).containsExactly(0.0, 0.0, 0.5);
        assertThat(normalizedRef[1]).containsExactly(1.0, 1.0, 0.5);
    }

    private static void assertAllPointsStrictlyInsideReferenceBox(Path csvPath, double[] refPoint, int expectedM)
            throws IOException {
        double[][] matrix = readCsvMatrix(csvPath);
        assertThat(matrix.length).as("Frente não vazia: %s", csvPath).isGreaterThan(0);
        for (int i = 0; i < matrix.length; i++) {
            double[] row = matrix[i];
            assertThat(row.length).as("Dimensão da linha %d em %s", i, csvPath).isEqualTo(expectedM);
            for (int j = 0; j < expectedM; j++) {
                assertThat(row[j])
                        .as("Coordenada j=%d da linha %d em %s deve ser estritamente menor que refPoint[%d]=%f",
                                j, i, csvPath.getFileName(), j, refPoint[j])
                        .isFinite()
                        .isLessThan(refPoint[j]);
            }
        }
    }

    private static double[][] readCsvMatrix(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        double[][] matrix = new double[lines.size()][];
        for (int i = 0; i < lines.size(); i++) {
            String[] tokens = lines.get(i).split(",");
            double[] row = new double[tokens.length];
            for (int j = 0; j < tokens.length; j++) {
                row[j] = Double.parseDouble(tokens[j].trim());
            }
            matrix[i] = row;
        }
        return matrix;
    }
}

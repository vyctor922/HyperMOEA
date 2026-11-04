package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.util.ReferenceSetValidator;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetComparisonReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetValidationReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.Verdict;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.util.ReferencePoint;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.referencepoint.ReferencePointGenerator;

/**
 * Testes de validação matemática (T02) para os pontos de referência do NSGA-III e
 * avaliação de {@code reference-calc.java} contra o simplex-lattice de Das & Dennis (1998)
 * e duas camadas de Deb & Jain (2014).
 */
class NsgaIIIReferencePointsValidationTest {

    private static final double TOLERANCE = 1e-6;

    static Stream<Arguments> canonicalConfigurations() {
        return Stream.of(
                Arguments.of(2, 99, 0, 100, 0, 100),
                Arguments.of(3, 12, 0, 91, 0, 91),
                Arguments.of(5, 6, 0, 210, 0, 210),
                Arguments.of(8, 3, 2, 120, 36, 156),
                Arguments.of(10, 3, 2, 220, 55, 275),
                Arguments.of(15, 2, 1, 120, 15, 135));
    }

    @ParameterizedTest(name = "m={0}, H1={1}, H2={2} -> outer={3}, inner={4}, total={5}")
    @MethodSource("canonicalConfigurations")
    @DisplayName("T02.1: Contagem canônica de Das & Dennis / Deb & Jain e propriedades de simplex")
    void testCanonicalDasDennisCountsAndSimplexProperties(
            int m, int outerDivisions, int innerDivisions, int expectedOuter, int expectedInner, int expectedTotal) {
        assertThat(StaticParams.getPopulationSize(m)).isEqualTo(expectedTotal);
        assertThat(ReferenceSetValidator.expectedSimplexPoints(m, outerDivisions)).isEqualTo(expectedOuter);
        if (innerDivisions > 0) {
            assertThat(ReferenceSetValidator.expectedSimplexPoints(m, innerDivisions)).isEqualTo(expectedInner);
        }
        assertThat(ReferenceSetValidator.expectedTwoLayerPoints(m, outerDivisions, innerDivisions))
                .isEqualTo(expectedTotal);

        // Valida camada externa isoladamente
        double[][] outerLayer = ReferenceSetValidator.generateCanonicalSingleLayer(m, outerDivisions);
        SetValidationReport outerReport = ReferenceSetValidator.validateSimplexSet(
                outerLayer, expectedOuter, m, TOLERANCE, TOLERANCE);
        assertThat(outerReport.validSimplex()).isTrue();
        assertThat(outerReport.vectorCount()).isEqualTo(expectedOuter);
        assertThat(outerReport.dimension()).isEqualTo(m);
        assertThat(outerReport.allFinite()).isTrue();
        assertThat(outerReport.allNonNegative()).isTrue();
        assertThat(outerReport.maxSumDeviationFromOne()).isLessThanOrEqualTo(TOLERANCE);
        assertThat(outerReport.duplicateCount()).isZero();

        // Se houver camada interna, valida isoladamente incluindo o deslocamento >= 1/(2m)
        if (innerDivisions > 0) {
            double[][] innerLayer = ReferenceSetValidator.generateCanonicalInnerLayer(m, innerDivisions);
            SetValidationReport innerReport = ReferenceSetValidator.validateSimplexSet(
                    innerLayer, expectedInner, m, TOLERANCE, TOLERANCE);
            assertThat(innerReport.validSimplex()).isTrue();
            assertThat(innerReport.vectorCount()).isEqualTo(expectedInner);
            assertThat(innerReport.dimension()).isEqualTo(m);
            assertThat(innerReport.duplicateCount()).isZero();
            double expectedMinCoord = 1.0 / (2.0 * m);
            double expectedMaxCoord = 0.5 + expectedMinCoord;
            assertThat(innerReport.minCoordinate()).isCloseTo(expectedMinCoord, org.assertj.core.data.Offset.offset(1e-12));
            assertThat(innerReport.maxCoordinate()).isCloseTo(expectedMaxCoord, org.assertj.core.data.Offset.offset(1e-12));
        }

        // Valida conjunto completo combinado
        double[][] combined = ReferenceSetValidator.generateCanonicalTwoLayers(m, outerDivisions, innerDivisions);
        SetValidationReport combinedReport = ReferenceSetValidator.validateSimplexSet(
                combined, expectedTotal, m, TOLERANCE, TOLERANCE);
        assertThat(combinedReport.validSimplex()).isTrue();
        assertThat(combinedReport.vectorCount()).isEqualTo(expectedTotal);
        assertThat(combinedReport.duplicateCount()).isZero();
    }

    @ParameterizedTest(name = "m={0}, H1={1}, H2={2}, total={5}")
    @MethodSource("canonicalConfigurations")
    @DisplayName("T02.2: Equivalência independente de ordem com jMetal 7.8 ReferencePointGenerator")
    void testEquivalenceWithJMetalReferencePointGenerator(
            int m, int outerDivisions, int innerDivisions, int expectedOuter, int expectedInner, int expectedTotal) {
        double[][] canonical = ReferenceSetValidator.generateCanonicalTwoLayers(m, outerDivisions, innerDivisions);

        List<double[]> jmetalList = (innerDivisions == 0)
                ? ReferencePointGenerator.generateSingleLayer(m, outerDivisions)
                : ReferencePointGenerator.generateTwoLayers(m, outerDivisions, innerDivisions);

        // Embaralha a saída do jMetal para provar que a comparação independe da ordem das linhas
        List<double[]> shuffled = new ArrayList<>(jmetalList);
        Collections.shuffle(shuffled, new Random(42L + m));
        double[][] jmetalArray = shuffled.toArray(double[][]::new);

        SetComparisonReport report = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical, jmetalArray, TOLERANCE);

        System.out.printf(
                "[T02-RefPointGen] m=%d | countA=%d, countB=%d | dupA=%d, dupB=%d | unmatched=%d/%d | maxCoordErr=%.3e | maxEuclidErr=%.3e | verdict=%s%n",
                m,
                report.countA(),
                report.countB(),
                report.duplicatesA(),
                report.duplicatesB(),
                report.unmatchedFromA(),
                report.unmatchedFromB(),
                report.maxCoordinateError(),
                report.maxEuclideanError(),
                report.verdict());

        assertThat(report.verdict()).isEqualTo(Verdict.EQUIVALENT);
        assertThat(report.countA()).isEqualTo(expectedTotal);
        assertThat(report.countB()).isEqualTo(expectedTotal);
        assertThat(report.unmatchedFromA()).isZero();
        assertThat(report.unmatchedFromB()).isZero();
        assertThat(report.maxCoordinateError()).isLessThan(1e-15);
        assertThat(report.maxEuclideanError()).isLessThan(1e-15);
    }

    @ParameterizedTest(name = "m={0}, H1={1}, H2={2}, total={5}")
    @MethodSource("canonicalConfigurations")
    @DisplayName("T02.3: Avaliação dos pontos efetivamente gerados por NSGAIIIBuilder no jMetal 7.8")
    void testNsgaIIIBuilderActualReferencePoints(
            int m, int outerDivisions, int innerDivisions, int expectedOuter, int expectedInner, int expectedTotal)
            throws Exception {
        Problem<DoubleSolution> problem = new WFG1(2 * (m - 1), 20, m);
        NSGAIIIBuilder<DoubleSolution> builder = new NSGAIIIBuilder<>(problem)
                .setCrossoverOperator(new SBXCrossover(1.0, 30.0))
                .setMutationOperator(new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0))
                .setSelectionOperator(new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>()))
                .setMaxIterations(1)
                .setNumberOfDivisions(outerDivisions);

        if (innerDivisions > 0) {
            builder.setSecondLayerDivisions(innerDivisions);
        }

        NSGAIII<DoubleSolution> nsgaiii = builder.build();
        double[][] actualBuilderPoints = extractReferencePointsFromNsgaIII(nsgaiii, m);

        // Verifica propriedades de simplex dos pontos do NSGAIIIBuilder (sempre somam 1 e são >= 0)
        SetValidationReport validation = ReferenceSetValidator.validateSimplexSet(
                actualBuilderPoints, expectedTotal, m, TOLERANCE, TOLERANCE);
        assertThat(validation.validSimplex()).isTrue();
        assertThat(validation.vectorCount()).isEqualTo(expectedTotal);

        double[][] canonicalDebJain = ReferenceSetValidator.generateCanonicalTwoLayers(
                m, outerDivisions, innerDivisions);
        SetComparisonReport vsCanonical = ReferenceSetValidator.compareSetsOrderIndependent(
                canonicalDebJain, actualBuilderPoints, TOLERANCE);

        System.out.printf(
                "[T02-NSGAIIIBuilder] m=%d (H1=%d, H2=%d) vs Deb&Jain | count=%d/%d | unmatched=%d | maxCoordErr=%.6e | maxEuclidErr=%.6e | verdict=%s%n",
                m,
                outerDivisions,
                innerDivisions,
                vsCanonical.countA(),
                vsCanonical.countB(),
                vsCanonical.unmatchedFromA(),
                vsCanonical.maxCoordinateError(),
                vsCanonical.maxEuclideanError(),
                vsCanonical.verdict());

        if (innerDivisions == 0) {
            // Para 1 camada (m = 2, 3, 5), NSGAIIIBuilder é 100% equivalente ao Das & Dennis canônico
            assertThat(vsCanonical.verdict()).isEqualTo(Verdict.EQUIVALENT);
            assertThat(vsCanonical.maxCoordinateError()).isLessThan(1e-15);
        } else {
            // Para 2 camadas (m = 8, 10, 15), NSGAIII usa (x_i + 1)/(m + 1) na camada interna em vez de 0.5*x_i + 1/(2m)
            assertThat(vsCanonical.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
            assertThat(vsCanonical.unmatchedFromA()).isEqualTo(expectedInner);

            double[][] expectedJMetalFormula =
                    ReferenceSetValidator.generateJMetalNsgaIIITwoLayersFormula(m, outerDivisions, innerDivisions);
            SetComparisonReport vsJMetalFormula = ReferenceSetValidator.compareSetsOrderIndependent(
                    expectedJMetalFormula, actualBuilderPoints, TOLERANCE);
            assertThat(vsJMetalFormula.verdict()).isEqualTo(Verdict.EQUIVALENT);
            assertThat(vsJMetalFormula.maxCoordinateError()).isLessThan(1e-15);
        }
    }

    @Test
    @DisplayName("T02.4: Validação de reference-calc.java contra Das & Dennis (1 camada e 2 camadas)")
    void testReferenceCalcVsCanonicalDasDennis() {
        // 1. Configuração hardcoded no main de reference-calc.java: m=9, H=4, layers=1 -> C(12, 8) = 495 pontos
        double[][] refCalc9D = ReferenceSetValidator.simulateReferenceCalc(9, 4, 1);
        double[][] canonical9D = ReferenceSetValidator.generateCanonicalSingleLayer(9, 4);
        SetComparisonReport report9D = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical9D, refCalc9D, 1e-4);
        System.out.printf(
                "[T02-RefCalc] m=9, H=4, layers=1 | count=%d/%d | maxCoordErr=%.6e | maxEuclidErr=%.6e | verdict(1e-4)=%s%n",
                report9D.countA(),
                report9D.countB(),
                report9D.maxCoordinateError(),
                report9D.maxEuclideanError(),
                report9D.verdict());
        assertThat(report9D.countB()).isEqualTo(495);
        assertThat(report9D.verdict()).isEqualTo(Verdict.EQUIVALENT);
        assertThat(report9D.maxCoordinateError()).isZero();

        // 2. m=2, H=99, layers=1 -> falha crítica por arredondamento (99 * 0.01010 = 0.99990, gera 0 pontos)
        double[][] refCalc2D = ReferenceSetValidator.simulateReferenceCalc(2, 99, 1);
        double[][] canonical2D = ReferenceSetValidator.generateCanonicalSingleLayer(2, 99);
        SetComparisonReport report2D = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical2D, refCalc2D, TOLERANCE);
        System.out.printf(
                "[T02-RefCalc] m=2, H=99, layers=1 | count=%d/%d | verdict=%s%n",
                report2D.countA(), report2D.countB(), report2D.verdict());
        assertThat(refCalc2D.length).isZero();
        assertThat(report2D.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);

        // 3. m=3 (H=12, layers=1) e m=5 (H=6, layers=1): gera 91 e 210 pontos, mas truncados/arredondados a 1e-5
        double[][] refCalc3D = ReferenceSetValidator.simulateReferenceCalc(3, 12, 1);
        double[][] canonical3D = ReferenceSetValidator.generateCanonicalSingleLayer(3, 12);
        SetComparisonReport report3DStrict = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical3D, refCalc3D, TOLERANCE);
        SetComparisonReport report3DRelaxed = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical3D, refCalc3D, 5e-5);
        System.out.printf(
                "[T02-RefCalc] m=3, H=12, layers=1 | count=%d/%d | maxCoordErr=%.6e | maxEuclidErr=%.6e | verdict(1e-6)=%s | verdict(5e-5)=%s%n",
                report3DStrict.countA(),
                report3DStrict.countB(),
                report3DStrict.maxCoordinateError(),
                report3DStrict.maxEuclideanError(),
                report3DStrict.verdict(),
                report3DRelaxed.verdict());
        assertThat(report3DStrict.countB()).isEqualTo(91);
        // 1/3 = 0.333333... arredondado para 0.33333 tem erro 3.333e-6 > 1e-6
        assertThat(report3DStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
        assertThat(report3DRelaxed.verdict()).isEqualTo(Verdict.EQUIVALENT);

        double[][] refCalc5D = ReferenceSetValidator.simulateReferenceCalc(5, 6, 1);
        double[][] canonical5D = ReferenceSetValidator.generateCanonicalSingleLayer(5, 6);
        SetComparisonReport report5DStrict = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical5D, refCalc5D, TOLERANCE);
        SetComparisonReport report5DRelaxed = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical5D, refCalc5D, 5e-5);
        System.out.printf(
                "[T02-RefCalc] m=5, H=6, layers=1 | count=%d/%d | maxCoordErr=%.6e | maxEuclidErr=%.6e | verdict(1e-6)=%s | verdict(5e-5)=%s%n",
                report5DStrict.countA(),
                report5DStrict.countB(),
                report5DStrict.maxCoordinateError(),
                report5DStrict.maxEuclideanError(),
                report5DStrict.verdict(),
                report5DRelaxed.verdict());
        assertThat(report5DStrict.countB()).isEqualTo(210);
        assertThat(report5DStrict.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
        assertThat(report5DRelaxed.verdict()).isEqualTo(Verdict.EQUIVALENT);

        // 4. Configurações de 2 camadas (m=8, 10, 15): reference-calc usa o mesmo divisoes para ambas as camadas
        // e deslocamento 1/(m+1), não sendo equivalente ao método de Deb & Jain (2014)
        int[][] twoLayerConfigs = {{8, 3, 2}, {10, 3, 2}, {15, 2, 1}};
        for (int[] cfg : twoLayerConfigs) {
            int m = cfg[0];
            int h1 = cfg[1];
            int h2 = cfg[2];
            double[][] refCalcTwoLayers = ReferenceSetValidator.simulateReferenceCalc(m, h1, 2);
            double[][] canonicalTwoLayers = ReferenceSetValidator.generateCanonicalTwoLayers(m, h1, h2);
            SetComparisonReport cmp = ReferenceSetValidator.compareSetsOrderIndependent(
                    canonicalTwoLayers, refCalcTwoLayers, 1e-4);
            System.out.printf(
                    "[T02-RefCalc-2Layers] m=%d, H1=%d, H2=%d | canonicalCount=%d vs refCalcCount=%d | maxCoordErr=%.6e | verdict=%s%n",
                    m, h1, h2, cmp.countA(), cmp.countB(), cmp.maxCoordinateError(), cmp.verdict());
            assertThat(cmp.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
        }
    }

    @Test
    @DisplayName("T02.5: Testes negativos — rejeição controlada de parâmetros e conjuntos inválidos")
    void testNegativeInvalidSimplexConfigurationsAndSets() {
        // Parâmetros inválidos de geração
        assertThatThrownBy(() -> ReferenceSetValidator.generateCanonicalSingleLayer(1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("m deve ser >= 2");

        assertThatThrownBy(() -> ReferenceSetValidator.generateCanonicalSingleLayer(3, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("H1");

        assertThatThrownBy(() -> ReferenceSetValidator.generateCanonicalTwoLayers(3, 12, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("H2");

        assertThatThrownBy(() -> ReferenceSetValidator.generateCanonicalInnerLayer(8, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("H2");

        // Conjunto com coordenada negativa
        double[][] negativeCoordSet = {
            {1.1, -0.1, 0.0},
            {0.0, 0.5, 0.5}
        };
        assertThatThrownBy(() -> ReferenceSetValidator.assertValidSimplexSet(negativeCoordSet, 2, 3, TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allNonNegative=false");

        // Conjunto com soma != 1
        double[][] badSumSet = {
            {0.5, 0.5, 0.5},
            {0.0, 0.5, 0.5}
        };
        assertThatThrownBy(() -> ReferenceSetValidator.assertValidSimplexSet(badSumSet, 2, 3, TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSumDev");

        // Conjunto com duplicatas
        double[][] duplicateSet = {
            {0.5, 0.5, 0.0},
            {0.5, 0.5, 0.0}
        };
        assertThatThrownBy(() -> ReferenceSetValidator.assertValidSimplexSet(duplicateSet, 2, 3, TOLERANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicates=1");
    }

    @SuppressWarnings("unchecked")
    private static double[][] extractReferencePointsFromNsgaIII(NSGAIII<DoubleSolution> nsgaiii, int m)
            throws Exception {
        Field field = NSGAIII.class.getDeclaredField("referencePoints");
        field.setAccessible(true);
        List<ReferencePoint<DoubleSolution>> refPoints =
                (List<ReferencePoint<DoubleSolution>>) field.get(nsgaiii);
        double[][] result = new double[refPoints.size()][m];
        for (int i = 0; i < refPoints.size(); i++) {
            List<Double> pos = refPoints.get(i).position;
            for (int j = 0; j < m; j++) {
                result[i][j] = pos.get(j);
            }
        }
        return result;
    }
}

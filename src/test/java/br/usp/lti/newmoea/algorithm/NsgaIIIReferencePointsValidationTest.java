package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import br.usp.lti.newmoea.util.ReferenceSetValidator;
import br.usp.lti.newmoea.util.ReferenceSetValidator.ReferencePersistenceReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetComparisonReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetValidationReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.Verdict;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    @DisplayName("T07.1: Validação separada das camadas externa (H1) e interna (H2) do NSGAIIIBuilder para m in {8, 10, 15}")
    void testT07SeparateOuterAndInnerLayersInNsgaIIIBuilder() throws Exception {
        int[][] twoLayerConfigs = {
            {8, 3, 2, 120, 36, 156},
            {10, 3, 2, 220, 55, 275},
            {15, 2, 1, 120, 15, 135}
        };

        for (int[] cfg : twoLayerConfigs) {
            int m = cfg[0];
            int h1 = cfg[1];
            int h2 = cfg[2];
            int expectedOuter = cfg[3];
            int expectedInner = cfg[4];
            int expectedTotal = cfg[5];

            Problem<DoubleSolution> problem = new WFG1(2 * (m - 1), 20, m);
            NSGAIII<DoubleSolution> nsgaiii = new NSGAIIIBuilder<>(problem)
                    .setCrossoverOperator(new SBXCrossover(1.0, 30.0))
                    .setMutationOperator(new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0))
                    .setSelectionOperator(new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>()))
                    .setMaxIterations(1)
                    .setNumberOfDivisions(h1)
                    .setSecondLayerDivisions(h2)
                    .build();

            double[][] allActualPoints = extractReferencePointsFromNsgaIII(nsgaiii, m);
            assertThat(allActualPoints.length).isEqualTo(expectedTotal);

            double[][] actualOuter = Arrays.copyOfRange(allActualPoints, 0, expectedOuter);
            double[][] actualInner = Arrays.copyOfRange(allActualPoints, expectedOuter, expectedTotal);

            // 1. Camada externa isolada vs Das & Dennis canônico: deve ser 100% equivalente
            double[][] canonicalOuter = ReferenceSetValidator.generateCanonicalSingleLayer(m, h1);
            SetComparisonReport outerCmp = ReferenceSetValidator.compareSetsOrderIndependent(
                    canonicalOuter, actualOuter, TOLERANCE);

            // 2. Camada interna isolada vs Deb & Jain canônico: todos os N2 pontos divergem
            // porque NSGAIII aplica (x_i + 1)/(m + 1) em vez de 0.5*x_i + 1/(2m)
            double[][] canonicalInnerDebJain = ReferenceSetValidator.generateCanonicalInnerLayer(m, h2);
            SetComparisonReport innerVsDebJain = ReferenceSetValidator.compareSetsOrderIndependent(
                    canonicalInnerDebJain, actualInner, TOLERANCE);

            // 3. Camada interna isolada vs fórmula analítica (x_i + 1)/(m + 1) do jMetal NSGAIII: 100% equivalente
            double[][] expectedJMetalAll =
                    ReferenceSetValidator.generateJMetalNsgaIIITwoLayersFormula(m, h1, h2);
            double[][] expectedJMetalInner = Arrays.copyOfRange(expectedJMetalAll, expectedOuter, expectedTotal);
            SetComparisonReport innerVsJMetalFormula = ReferenceSetValidator.compareSetsOrderIndependent(
                    expectedJMetalInner, actualInner, TOLERANCE);

            // Nos vértices da camada interna (x_k = 1, demais = 0):
            // Deb & Jain: 0.5 * 1 + 1/(2m) = (m + 1) / (2m)
            // NSGAIII (jMetal 7.8): (1 + 1) / (m + 1) = 2 / (m + 1)
            double expectedAnalyticalMaxCoordErr =
                    ((double) (m + 1) / (2.0 * m)) - (2.0 / (double) (m + 1));

            System.out.printf(
                    "[T07-NSGAIII-Layers] m=%d (H1=%d, H2=%d) | OUTER: count=%d/%d, unmatched=%d, maxCoordErr=%.3e, maxEuclidErr=%.3e, verdict=%s | INNER vs Deb&Jain: count=%d/%d, unmatched=%d, maxCoordErr=%.6f (teorico=%.6f), maxEuclidErr=%.6f, verdict=%s | INNER vs (x+1)/(m+1): maxCoordErr=%.3e, verdict=%s%n",
                    m,
                    h1,
                    h2,
                    outerCmp.countA(),
                    outerCmp.countB(),
                    outerCmp.unmatchedFromA(),
                    outerCmp.maxCoordinateError(),
                    outerCmp.maxEuclideanError(),
                    outerCmp.verdict(),
                    innerVsDebJain.countA(),
                    innerVsDebJain.countB(),
                    innerVsDebJain.unmatchedFromA(),
                    innerVsDebJain.maxCoordinateError(),
                    expectedAnalyticalMaxCoordErr,
                    innerVsDebJain.maxEuclideanError(),
                    innerVsDebJain.verdict(),
                    innerVsJMetalFormula.maxCoordinateError(),
                    innerVsJMetalFormula.verdict());

            assertThat(outerCmp.verdict()).isEqualTo(Verdict.EQUIVALENT);
            assertThat(outerCmp.unmatchedFromA()).isZero();
            assertThat(outerCmp.maxCoordinateError()).isLessThan(1e-15);
            assertThat(outerCmp.maxEuclideanError()).isLessThan(1e-15);

            assertThat(innerVsDebJain.verdict()).isEqualTo(Verdict.NOT_EQUIVALENT);
            assertThat(innerVsDebJain.unmatchedFromA()).isEqualTo(expectedInner);
            assertThat(innerVsDebJain.maxCoordinateError())
                    .isCloseTo(expectedAnalyticalMaxCoordErr, org.assertj.core.data.Offset.offset(1e-12));

            assertThat(innerVsJMetalFormula.verdict()).isEqualTo(Verdict.EQUIVALENT);
            assertThat(innerVsJMetalFormula.unmatchedFromA()).isZero();
            assertThat(innerVsJMetalFormula.maxCoordinateError()).isLessThan(1e-15);
        }
    }

    @Test
    @DisplayName("T07.2: Persistência opcional (-DpersistReferencePoints) desativada por padrão, ativação controlada, proteção contra sobrescrita e imutabilidade dos vetores")
    void testT07OptionalPersistenceAndNonOverwriteProtection(@TempDir Path tempDir) throws Exception {
        String prevProp = System.getProperty(ReferenceSetValidator.PERSIST_REFERENCE_POINTS_PROPERTY);
        try {
            // 1. Por padrão (ou quando false), a propriedade está desativada
            System.clearProperty(ReferenceSetValidator.PERSIST_REFERENCE_POINTS_PROPERTY);
            assertThat(ReferenceSetValidator.isPersistReferencePointsEnabled()).isFalse();

            Problem<DoubleSolution> problem = new WFG1(4, 20, 3);
            int m = 3;
            int h1 = StaticParams.getOuterDivisions(m);
            int h2 = StaticParams.getInnerDivisions(m);
            int popSize = StaticParams.getPopulationSize(m);

            @SuppressWarnings("unchecked")
            NSGAIII<DoubleSolution> nsgaiii = (NSGAIII<DoubleSolution>) WFGStudy.buildNSGAIII(
                    problem,
                    popSize,
                    new SBXCrossover(1.0, 30.0),
                    new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0));

            double[][] beforePersistence = extractReferencePointsFromNsgaIII(nsgaiii, m);
            double[][] snapshotCopy = new double[beforePersistence.length][m];
            for (int i = 0; i < beforePersistence.length; i++) {
                snapshotCopy[i] = beforePersistence[i].clone();
            }

            // Quando desativada, persistReferenceSetCsv retorna enabled=false e não grava arquivo
            ReferencePersistenceReport disabledReport =
                    ReferenceSetValidator.persistReferenceSetCsv(
                            beforePersistence,
                            m,
                            h1,
                            h2,
                            "NSGAIII",
                            tempDir,
                            ReferenceSetValidator.isPersistReferencePointsEnabled(),
                            false);
            assertThat(disabledReport.enabled()).isFalse();
            assertThat(disabledReport.written()).isFalse();
            assertThat(Files.exists(tempDir.resolve("NSGAIII_m3_H112_H20.csv"))).isFalse();

            // 2. Quando ativada (-DpersistReferencePoints=true), grava CSV com cabeçalho index,f1,f2,f3 e 15 casas decimais
            System.setProperty(ReferenceSetValidator.PERSIST_REFERENCE_POINTS_PROPERTY, "true");
            assertThat(ReferenceSetValidator.isPersistReferencePointsEnabled()).isTrue();

            // Verifica também a integração real em WFGStudy.buildNSGAIII com recursos padrão e limpa o arquivo gerado se foi recém-criado
            Path defaultTarget = ReferenceSetValidator.DEFAULT_NSGAIII_PERSIST_DIR.resolve("NSGAIII_m3_H112_H20.csv");
            boolean existedBeforeDefault = Files.exists(defaultTarget);
            try {
                WFGStudy.buildNSGAIII(
                        problem,
                        popSize,
                        new SBXCrossover(1.0, 30.0),
                        new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0));
                assertThat(Files.exists(defaultTarget)).isTrue();
            } finally {
                if (!existedBeforeDefault) {
                    Files.deleteIfExists(defaultTarget);
                }
            }

            ReferencePersistenceReport report =
                    ReferenceSetValidator.persistReferenceSetCsv(
                            beforePersistence,
                            m,
                            h1,
                            h2,
                            "NSGAIII",
                            tempDir,
                            ReferenceSetValidator.isPersistReferencePointsEnabled(),
                            false);
            assertThat(report).isNotNull();
            assertThat(report.enabled()).isTrue();
            assertThat(report.written()).isTrue();
            assertThat(report.suffixedDueToConflict()).isFalse();
            assertThat(report.outputFile().getFileName().toString()).isEqualTo("NSGAIII_m3_H112_H20.csv");
            assertThat(Files.exists(report.outputFile())).isTrue();

            List<String> lines = Files.readAllLines(report.outputFile(), StandardCharsets.UTF_8);
            assertThat(lines.get(0)).isEqualTo("index,f1,f2,f3");
            assertThat(lines).hasSize(popSize + 1);
            assertThat(lines.get(1)).matches("^0,\\d+\\.\\d{15},\\d+\\.\\d{15},\\d+\\.\\d{15}$");

            // 3. Verifica que os vetores internos do algoritmo NÃO foram alterados pela persistência
            double[][] afterPersistence = extractReferencePointsFromNsgaIII(nsgaiii, m);
            assertThat(afterPersistence).isDeepEqualTo(snapshotCopy);

            // 4. Chamada repetida com conteúdo idêntico é idempotente (não cria duplicata nem altera o arquivo)
            ReferencePersistenceReport idempotentReport =
                    ReferenceSetValidator.persistReferenceSetCsv(
                            beforePersistence, m, h1, h2, "NSGAIII", tempDir, true, false);
            assertThat(idempotentReport.written()).isFalse();
            assertThat(idempotentReport.reusedIdenticalExisting()).isTrue();
            assertThat(idempotentReport.outputFile()).isEqualTo(report.outputFile());

            // 5. Proteção contra sobrescrita quando já existe arquivo com conteúdo distinto:
            //    - Modo failIfDifferentExists=true: lança IllegalStateException sem sobrescrever o arquivo original
            //    - Modo failIfDifferentExists=false: cria arquivo com sufixo _run1.csv preservando o original intacto
            Path existingPath = report.outputFile();
            String customPreExistingContent = "index,f1,f2,f3\n0,1.000000000000000,0.000000000000000,0.000000000000000\n";
            Files.writeString(existingPath, customPreExistingContent, StandardCharsets.UTF_8);

            assertThatThrownBy(() -> ReferenceSetValidator.persistReferenceSetCsv(
                            beforePersistence, m, h1, h2, "NSGAIII", tempDir, true, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Arquivo de referência já existe e não pode ser sobrescrito automaticamente");
            assertThat(Files.readString(existingPath, StandardCharsets.UTF_8))
                    .isEqualTo(customPreExistingContent);

            ReferencePersistenceReport suffixedReport =
                    ReferenceSetValidator.persistReferenceSetCsv(
                            beforePersistence, m, h1, h2, "NSGAIII", tempDir, true, false);
            assertThat(suffixedReport.written()).isTrue();
            assertThat(suffixedReport.suffixedDueToConflict()).isTrue();
            assertThat(suffixedReport.outputFile().getFileName().toString())
                    .isEqualTo("NSGAIII_m3_H112_H20_run1.csv");
            assertThat(Files.readString(existingPath, StandardCharsets.UTF_8))
                    .isEqualTo(customPreExistingContent);
        } finally {
            if (prevProp == null) {
                System.clearProperty(ReferenceSetValidator.PERSIST_REFERENCE_POINTS_PROPERTY);
            } else {
                System.setProperty(ReferenceSetValidator.PERSIST_REFERENCE_POINTS_PROPERTY, prevProp);
            }
        }
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

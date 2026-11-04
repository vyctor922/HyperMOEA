package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder;
import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import br.usp.lti.newmoea.util.ReferenceSetValidator;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.util.ReferencePoint;
import org.uma.jmetal.algorithm.multiobjective.spea2.SPEA2Builder;
import org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.problem.multiobjective.wfg.WFG2;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;

/**
 * Testes de integração dos algoritmos evolutivos multiobjetivo (MOEAs).
 * Verifica instanciação, execução reduzida, término correto, validade das soluções,
 * padronização de camadas/população do NSGA-III e validação de pesos do MOEA/DD em T05.
 */
public class AlgorithmIntegrationTest {

    static Stream<Arguments> canonicalObjectivesAndLayers() {
        return Stream.of(
                Arguments.of(2, 99, 0, 100),
                Arguments.of(3, 12, 0, 91),
                Arguments.of(5, 6, 0, 210),
                Arguments.of(8, 3, 2, 156),
                Arguments.of(10, 3, 2, 275),
                Arguments.of(15, 2, 1, 135));
    }

    private void validateSolutionsAndIndicators(List<DoubleSolution> solutions, int expectedObjectives) {
        assertThat(solutions).isNotEmpty();

        double[][] front = new double[solutions.size()][expectedObjectives];
        for (int i = 0; i < solutions.size(); i++) {
            DoubleSolution sol = solutions.get(i);
            assertThat(sol.objectives()).hasSize(expectedObjectives);
            for (int j = 0; j < expectedObjectives; j++) {
                double val = sol.objectives()[j];
                assertThat(val).isNotNaN();
                assertThat(val).isNotInfinite();
                front[i][j] = val;
            }
        }

        // Valida consumo direto pelos indicadores de qualidade
        R2 r2 = new R2();
        double r2Val = r2.compute(front);
        assertThat(r2Val).isFinite();

        InvertedGenerationalDistance igd = new InvertedGenerationalDistance(front);
        double igdVal = igd.compute(front);
        assertThat(igdVal).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));

        InvertedGenerationalDistancePlus igdPlus = new InvertedGenerationalDistancePlus(front);
        double igdPlusVal = igdPlus.compute(front);
        assertThat(igdPlusVal).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));

        PISAHypervolume hv = new PISAHypervolume(front);
        double hvVal = hv.compute(front);
        assertThat(hvVal).isFinite();
    }

    @Test
    @DisplayName("NSGA-III: integração em DTLZ1 (3 objetivos, 12 divisões)")
    void testNSGAIIIIntegration() {
        Problem<DoubleSolution> problem = new DTLZ1(7, 3);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        SelectionOperator<List<DoubleSolution>, DoubleSolution> selection = new BinaryTournamentSelection<>(
                new RankingAndCrowdingDistanceComparator<>());

        NSGAIIIBuilder<DoubleSolution> builder = new NSGAIIIBuilder<>(problem)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setSelectionOperator(selection)
                .setMaxIterations(5)
                .setNumberOfDivisions(12);

        Algorithm<List<DoubleSolution>> algorithm = builder.build();
        algorithm.run();

        validateSolutionsAndIndicators(algorithm.result(), 3);
    }

    @ParameterizedTest(name = "m={0}, H1={1}, H2={2}, popSize={3}")
    @MethodSource("canonicalObjectivesAndLayers")
    @DisplayName("T05: WFGStudy.buildNSGAIII usa camadas explícitas corretas e valida consistência populacional")
    @SuppressWarnings("unchecked")
    void testWFGStudyBuildNSGAIIIExplicitLayersAndPopulationConsistency(
            int m, int expectedOuter, int expectedInner, int expectedPopSize) throws Exception {
        assertThat(StaticParams.getOuterDivisions(m)).isEqualTo(expectedOuter);
        assertThat(StaticParams.getInnerDivisions(m)).isEqualTo(expectedInner);
        assertThat(StaticParams.getPopulationSize(m)).isEqualTo(expectedPopSize);
        assertThat(ReferenceSetValidator.expectedTwoLayerPoints(m, expectedOuter, expectedInner))
                .isEqualTo(expectedPopSize);

        Problem<DoubleSolution> problem = new WFG1(2 * (m - 1), 20, m);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        Algorithm<List<DoubleSolution>> algorithm =
                WFGStudy.buildNSGAIII(problem, expectedPopSize, crossover, mutation);
        assertThat(algorithm).isInstanceOf(NSGAIII.class);

        NSGAIII<DoubleSolution> nsgaiii = (NSGAIII<DoubleSolution>) algorithm;

        Field outerField = NSGAIII.class.getDeclaredField("numberOfDivisions");
        outerField.setAccessible(true);
        assertThat(outerField.getInt(nsgaiii)).isEqualTo(expectedOuter);

        Field innerField = NSGAIII.class.getDeclaredField("secondLayerDivisions");
        innerField.setAccessible(true);
        assertThat(innerField.getInt(nsgaiii)).isEqualTo(expectedInner);

        Field refPointsField = NSGAIII.class.getDeclaredField("referencePoints");
        refPointsField.setAccessible(true);
        List<ReferencePoint<DoubleSolution>> refPoints =
                (List<ReferencePoint<DoubleSolution>>) refPointsField.get(nsgaiii);
        assertThat(refPoints).hasSize(expectedPopSize);

        // Verifica que buildNSGAIII rejeita populationSize inconsistente com o total de pontos de referência
        assertThatThrownBy(() -> WFGStudy.buildNSGAIII(problem, expectedPopSize + 1, crossover, mutation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inconsistência na configuração do NSGA-III");
    }

    @Test
    @DisplayName("MOEA/DD: integração em WFG1 (2 objetivos, população canônica validada)")
    void testMOEADDIntegration() {
        Problem<DoubleSolution> problem = new WFG1(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        int popSize = StaticParams.getPopulationSize(2);

        WFGStudy.validateMoeadWeightFile("MOEAD_Weights", problem, popSize);

        MOEADBuilder moead = new MOEADBuilder(problem, MOEADBuilder.Variant.MOEADD)
                .setCrossover(crossover)
                .setMutation(mutation)
                .setMaxEvaluations(popSize * 2)
                .setPopulationSize(popSize)
                .setResultPopulationSize(popSize)
                .setNeighborhoodSelectionProbability(0.9)
                .setMaximumNumberOfReplacedSolutions(2)
                .setNeighborSize(20)
                .setDataDirectory("MOEAD_Weights")
                .setFunctionType(AbstractMOEAD.FunctionType.PBI);

        Algorithm<List<DoubleSolution>> algorithm = moead.build();
        algorithm.run();

        validateSolutionsAndIndicators(algorithm.result(), 2);
    }

    @ParameterizedTest(name = "m={0}, popSize={3}")
    @MethodSource("canonicalObjectivesAndLayers")
    @DisplayName("T05: WFGStudy.buildMOEADD valida arquivo de pesos para todos os m e rejeita população sem arquivo")
    @SuppressWarnings("unchecked")
    void testWFGStudyBuildMOEADDWeightValidation(
            int m, int expectedOuter, int expectedInner, int expectedPopSize) {
        Problem<DoubleSolution> problem = new WFG1(2 * (m - 1), 20, m);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        double[][] weights = WFGStudy.validateMoeadWeightFile("MOEAD_Weights", problem, expectedPopSize);
        assertThat(weights).hasDimensions(expectedPopSize, m);

        Algorithm<List<DoubleSolution>> algorithm =
                WFGStudy.buildMOEADD(problem, expectedPopSize, crossover, mutation);
        assertThat(algorithm).isNotNull();

        // Verifica falha controlada quando o arquivo de pesos não existe para um populationSize inválido
        assertThatThrownBy(() -> WFGStudy.buildMOEADD(problem, 13, crossover, mutation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inexistente ou ilegível");
    }

    @Test
    @DisplayName("FDEA: integração com adaptador próprio em WFG2 (2 objetivos)")
    void testFDEAIntegration() {
        Problem<DoubleSolution> problem = new WFG2(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        SelectionOperator<List<DoubleSolution>, DoubleSolution> selection = new BinaryTournamentSelection<>(
                new RankingAndCrowdingDistanceComparator<>());

        Algorithm<List<DoubleSolution>> fdea = new FDEABuilder(problem)
                .setPopulationSize(20)
                .setMaxGenerations(5)
                .setMaxEvaluations(100)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setSelectionOperator(selection)
                .build();

        fdea.run();
        validateSolutionsAndIndicators(fdea.result(), 2);
    }

    @Test
    @DisplayName("RVEA: integração nativa jMetal 7.8 em DTLZ2 (3 objetivos)")
    void testRVEAIntegration() {
        Problem<DoubleSolution> problem = new DTLZ2(12, 3);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        int divisions = 12;
        List<double[]> referenceVectors = org.uma.jmetal.util.referencepoint.ReferencePointGenerator.generateSingleLayer(3, divisions);
        int popSize = referenceVectors.size();
        int maxEvaluations = popSize * 2;

        RVEABuilder<DoubleSolution> builder = new RVEABuilder<>(
                problem,
                popSize,
                maxEvaluations,
                crossover,
                mutation,
                2.0,
                0.1,
                referenceVectors
        );

        Algorithm<List<DoubleSolution>> rvea = builder.build();
        rvea.run();

        validateSolutionsAndIndicators(rvea.result(), 3);
    }

    @Test
    @DisplayName("AGE-MOEA (padrão): integração nativa jMetal 7.8 em WFG1 (2 objetivos)")
    void testAGEMOEAIntegration() {
        Problem<DoubleSolution> problem = new WFG1(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        AGEMOEABuilder<DoubleSolution> builder = new AGEMOEABuilder<>(problem)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setPopulationSize(20)
                .setMaxIterations(5);

        Algorithm<List<DoubleSolution>> age = builder.build();
        age.run();

        validateSolutionsAndIndicators(age.result(), 2);
    }

    @Test
    @DisplayName("AGE-MOEA-II: integração nativa jMetal 7.8 em WFG2 (2 objetivos)")
    void testAGEMOEAIIIntegration() {
        Problem<DoubleSolution> problem = new WFG2(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        AGEMOEAIIBuilder<DoubleSolution> builder = new AGEMOEAIIBuilder<>(problem)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setPopulationSize(20)
                .setMaxIterations(5);

        Algorithm<List<DoubleSolution>> age2 = builder.build();
        age2.run();

        validateSolutionsAndIndicators(age2.result(), 2);
    }

    @Test
    @DisplayName("Algoritmos complementares antigos: NSGA-II e SPEA2 executam e produzem soluções válidas")
    void testComplementsIntegration() {
        Problem<DoubleSolution> problem = new WFG1(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        Algorithm<List<DoubleSolution>> nsga2 = new NSGAIIBuilder<>(problem, crossover, mutation, 20)
                .setMaxEvaluations(100)
                .build();
        nsga2.run();
        validateSolutionsAndIndicators(nsga2.result(), 2);

        Algorithm<List<DoubleSolution>> spea2 = new SPEA2Builder<>(problem, crossover, mutation)
                .setMaxIterations(5)
                .setPopulationSize(20)
                .build();
        spea2.run();
        validateSolutionsAndIndicators(spea2.result(), 2);
    }
}

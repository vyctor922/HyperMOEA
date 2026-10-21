package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
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
 * Verifica instanciação, execução reduzida, término correto, validade das soluções
 * e capacidade de consumo pela infraestrutura de indicadores de qualidade.
 */
public class AlgorithmIntegrationTest {

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
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
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

    @Test
    @DisplayName("MOEA/DD: integração em WFG1 (2 objetivos)")
    void testMOEADDIntegration() {
        Problem<DoubleSolution> problem = new WFG1(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        int popSize = 20;

        MOEADBuilder moead = new MOEADBuilder(problem, MOEADBuilder.Variant.MOEADD)
                .setCrossover(crossover)
                .setMutation(mutation)
                .setMaxEvaluations(100)
                .setPopulationSize(popSize)
                .setResultPopulationSize(popSize)
                .setNeighborhoodSelectionProbability(0.9)
                .setMaximumNumberOfReplacedSolutions(2)
                .setNeighborSize(10)
                .setDataDirectory("MOEAD_Weights")
                .setFunctionType(AbstractMOEAD.FunctionType.PBI);

        Algorithm<List<DoubleSolution>> algorithm = moead.build();
        algorithm.run();

        validateSolutionsAndIndicators(algorithm.result(), 2);
    }

    @Test
    @DisplayName("FDEA: integração com adaptador próprio em WFG2 (2 objetivos)")
    void testFDEAIntegration() {
        Problem<DoubleSolution> problem = new WFG2(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
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
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
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
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
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
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
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
    @DisplayName("Baselines: NSGA-II e SPEA2 executam e produzem soluções válidas")
    void testBaselinesIntegration() {
        Problem<DoubleSolution> problem = new WFG1(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
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

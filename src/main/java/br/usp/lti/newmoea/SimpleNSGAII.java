package br.usp.lti.newmoea;

import br.usp.lti.newmoea.study.StaticParams;
import java.util.Arrays;
import java.util.List;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.archive.impl.NonDominatedSolutionListArchive;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;

public class SimpleNSGAII {

    public static void main(String[] args) {
        int numObjectives = 2;
        //params from papers
        int getMaxGenerations = StaticParams.getMaxGenerations(numObjectives, "WFG1");
        int populationSize = StaticParams.getPopulationSize(numObjectives);
        int maxEvaluations = getMaxGenerations * populationSize;
        int k = 2 * (numObjectives - 1);
        int l = 20;
        int matingPoolSize = populationSize;
        int offspringPopulationSize = populationSize;
        //creating a WFG1 problem
        WFG1 problem = new WFG1(k, l, numObjectives);

        //crossover, mutation, evaluator and selection
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 20.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        SolutionListEvaluator<DoubleSolution> evaluator = new SequentialSolutionListEvaluator<>();
        SelectionOperator<List<DoubleSolution>, DoubleSolution> selectionOperator = new BinaryTournamentSelection<DoubleSolution>(new RankingAndCrowdingDistanceComparator<DoubleSolution>());

        System.out.println("Creating and runnign NSGAII");
        Algorithm nsgaII = new NSGAII(problem, maxEvaluations, populationSize, matingPoolSize, offspringPopulationSize, crossover, mutation, selectionOperator, evaluator);

        nsgaII.run();

        List<DoubleSolution> population = ((List<DoubleSolution>) nsgaII.result());
        System.out.println("Final population: ");

        //get non dominated
        NonDominatedSolutionListArchive<DoubleSolution> nondominated = new NonDominatedSolutionListArchive<>();
        for (DoubleSolution solution : population) {
            nondominated.add(solution);
        }

        //print objective functions
        System.out.println("Objective functions");
        for (DoubleSolution solution : nondominated.solutions()) {
            System.out.println(Arrays.toString(solution.objectives()));
        }
        
        //print decision variables
        System.out.println("Decision variables");
        for (DoubleSolution solution : nondominated.solutions()) {
            System.out.println(solution.variables().toString());
        }
    }
}

package br.usp.lti.newmoea.algorithm.fdea;

import java.util.List;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.AlgorithmBuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

/**
 * Construtor (Builder) para o algoritmo FDEA adaptado para jMetal 7.8.
 * Segue o padrão de design adotado pelos algoritmos da biblioteca.
 */
public class FDEABuilder implements AlgorithmBuilder<Algorithm<List<DoubleSolution>>> {

    private final Problem<DoubleSolution> problem;
    private int populationSize = 100;
    private int maxGenerations = 250;
    private int maxEvaluations = 0;
    private CrossoverOperator<DoubleSolution> crossoverOperator;
    private MutationOperator<DoubleSolution> mutationOperator;
    private SelectionOperator<List<DoubleSolution>, DoubleSolution> selectionOperator;

    public FDEABuilder(Problem<DoubleSolution> problem) {
        this.problem = problem;
    }

    public FDEABuilder setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
        return this;
    }

    public FDEABuilder setMaxGenerations(int maxGenerations) {
        this.maxGenerations = maxGenerations;
        return this;
    }

    public FDEABuilder setMaxEvaluations(int maxEvaluations) {
        this.maxEvaluations = maxEvaluations;
        return this;
    }

    public FDEABuilder setCrossoverOperator(CrossoverOperator<DoubleSolution> crossoverOperator) {
        this.crossoverOperator = crossoverOperator;
        return this;
    }

    public FDEABuilder setMutationOperator(MutationOperator<DoubleSolution> mutationOperator) {
        this.mutationOperator = mutationOperator;
        return this;
    }

    public FDEABuilder setSelectionOperator(SelectionOperator<List<DoubleSolution>, DoubleSolution> selectionOperator) {
        this.selectionOperator = selectionOperator;
        return this;
    }

    public Problem<DoubleSolution> getProblem() {
        return problem;
    }

    public int getPopulationSize() {
        return populationSize;
    }

    public int getMaxGenerations() {
        return maxGenerations;
    }

    public int getMaxEvaluations() {
        return maxEvaluations;
    }

    public CrossoverOperator<DoubleSolution> getCrossoverOperator() {
        return crossoverOperator;
    }

    public MutationOperator<DoubleSolution> getMutationOperator() {
        return mutationOperator;
    }

    public SelectionOperator<List<DoubleSolution>, DoubleSolution> getSelectionOperator() {
        return selectionOperator;
    }

    @Override
    public Algorithm<List<DoubleSolution>> build() {
        return new FDEA(
                problem,
                populationSize,
                maxGenerations,
                maxEvaluations,
                crossoverOperator,
                mutationOperator,
                selectionOperator
        );
    }
}

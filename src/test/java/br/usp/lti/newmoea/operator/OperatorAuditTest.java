package br.usp.lti.newmoea.operator;

import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.component.algorithm.EvolutionaryAlgorithm;
import org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.DifferentialEvolutionCrossover;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import org.uma.jmetal.problem.doubleproblem.impl.AbstractDoubleProblem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.solution.doublesolution.repairsolution.RepairDoubleSolution;
import org.uma.jmetal.solution.doublesolution.repairsolution.impl.RepairDoubleSolutionWithBoundValue;
import org.uma.jmetal.util.bounds.Bounds;
import org.uma.jmetal.util.errorchecking.exception.InvalidConditionException;
import org.uma.jmetal.util.errorchecking.exception.InvalidProbabilityValueException;
import org.uma.jmetal.util.pseudorandom.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de auditoria dos operadores geneticos (T06) sobre jMetal 7.8.
 * Verifica assinaturas de construtores, probabilidade efetiva de mutacao (1/n vs 1.0),
 * indice de distribuicao, respeito aos limites das variaveis e integracao leve.
 */
class OperatorAuditTest {

    @Test
    void polynomialMutationConstructorsMatchJMetal78Bytecode() throws NoSuchMethodException {
        Constructor<?>[] constructors = PolynomialMutation.class.getConstructors();
        assertEquals(7, constructors.length, "PolynomialMutation no jMetal 7.8 deve expor exatamente 7 construtores publicos");

        assertNotNull(PolynomialMutation.class.getConstructor());
        assertNotNull(PolynomialMutation.class.getConstructor(double.class));
        assertNotNull(PolynomialMutation.class.getConstructor(DoubleProblem.class, double.class, RandomGenerator.class));
        assertNotNull(PolynomialMutation.class.getConstructor(double.class, double.class));
        assertNotNull(PolynomialMutation.class.getConstructor(double.class, double.class, RandomGenerator.class));
        assertNotNull(PolynomialMutation.class.getConstructor(double.class, double.class, RepairDoubleSolution.class));
        assertNotNull(PolynomialMutation.class.getConstructor(
                double.class, double.class, RepairDoubleSolution.class, RandomGenerator.class));

        assertThrows(NoSuchMethodException.class,
                () -> PolynomialMutation.class.getConstructor(double.class, int.class, double.class),
                "Nao existe construtor (double, int, double) em PolynomialMutation no jMetal 7.8");
    }

    @Test
    void sbxCrossoverConstructorsMatchJMetal78Bytecode() throws NoSuchMethodException {
        Constructor<?>[] constructors = SBXCrossover.class.getConstructors();
        assertEquals(5, constructors.length, "SBXCrossover no jMetal 7.8 deve expor exatamente 5 construtores publicos");

        assertNotNull(SBXCrossover.class.getConstructor(double.class));
        assertNotNull(SBXCrossover.class.getConstructor(double.class, double.class));
        assertNotNull(SBXCrossover.class.getConstructor(double.class, double.class, RandomGenerator.class));
        assertNotNull(SBXCrossover.class.getConstructor(double.class, double.class, RepairDoubleSolution.class));
        assertNotNull(SBXCrossover.class.getConstructor(
                double.class, double.class, RepairDoubleSolution.class, RandomGenerator.class));
    }

    @Test
    void wfgStudyPolynomialMutationConfigurationUsesOneOverNAndDistributionIndex20() {
        DoubleProblem problem = new WFG1(4, 20, 3); // n = 24 variaveis
        int n = problem.numberOfVariables();
        assertEquals(24, n);

        PolynomialMutation wfgStudyMutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        assertEquals(1.0 / 24.0, wfgStudyMutation.mutationProbability(), 1e-15);
        assertEquals(20.0, wfgStudyMutation.getDistributionIndex(), 1e-15);

        PolynomialMutation problemBasedMutation = new PolynomialMutation(problem, 20.0, () -> 0.5);
        assertEquals(wfgStudyMutation.mutationProbability(), problemBasedMutation.mutationProbability(), 1e-15);
        assertEquals(wfgStudyMutation.getDistributionIndex(), problemBasedMutation.getDistributionIndex(), 1e-15);

        assertThrows(InvalidProbabilityValueException.class, () -> new PolynomialMutation(-0.01, 20.0));
        assertThrows(InvalidProbabilityValueException.class, () -> new PolynomialMutation(1.01, 20.0));
        assertThrows(InvalidConditionException.class, () -> new PolynomialMutation(0.1, -1.0));
    }

    @Test
    void empiricalMutationFrequencyMatchesOneOverNWhereasOneMutatesAllVariables() {
        DoubleProblem problem = new WFG1(4, 20, 2); // n = 24 variaveis, limites [0, 2*(i+1)]
        int n = problem.numberOfVariables();
        double expectedProbability = 1.0 / n;

        Random seededRandomForOneOverN = new Random(20260411L);
        PolynomialMutation mutationOneOverN = new PolynomialMutation(
                expectedProbability,
                20.0,
                seededRandomForOneOverN::nextDouble);

        int trials = 2000;
        int mutatedCountOneOverN = 0;
        int totalVariablesChecked = trials * n;

        for (int t = 0; t < trials; t++) {
            DoubleSolution solution = createMidpointSolution(problem);
            List<Double> before = new ArrayList<>(solution.variables());
            mutationOneOverN.execute(solution);

            for (int i = 0; i < n; i++) {
                if (Double.compare(before.get(i), solution.variables().get(i)) != 0) {
                    mutatedCountOneOverN++;
                }
            }
        }

        double empiricalRateOneOverN = (double) mutatedCountOneOverN / totalVariablesChecked;
        assertEquals(expectedProbability, empiricalRateOneOverN, 0.006,
                "A frequencia empirica de mutacao com 1.0/n deve convergir para 1/n (~0.04167)");

        Random seededRandomForOne = new Random(20260411L);
        PolynomialMutation mutationProbabilityOne = new PolynomialMutation(
                1.0,
                20.0,
                seededRandomForOne::nextDouble);

        int mutatedCountProbabilityOne = 0;
        int trialsProbOne = 200;
        for (int t = 0; t < trialsProbOne; t++) {
            DoubleSolution solution = createMidpointSolution(problem);
            List<Double> before = new ArrayList<>(solution.variables());
            mutationProbabilityOne.execute(solution);

            for (int i = 0; i < n; i++) {
                if (Double.compare(before.get(i), solution.variables().get(i)) != 0) {
                    mutatedCountProbabilityOne++;
                }
            }
        }

        double empiricalRateProbabilityOne = (double) mutatedCountProbabilityOne / (trialsProbOne * n);
        assertEquals(1.0, empiricalRateProbabilityOne, 1e-12,
                "Passar 1.0 sem dividir por n faz PolynomialMutation mutar 100% das variaveis");
    }

    @Test
    void polynomialMutationAndRepairRespectVariableBoundsIncludingExtremesAndDegenerateBounds() {
        DoubleProblem problem = new WFG1(4, 20, 3);
        int n = problem.numberOfVariables();
        Random rng = new Random(42L);
        PolynomialMutation mutation = new PolynomialMutation(1.0 / n, 20.0, rng::nextDouble);

        for (int t = 0; t < 500; t++) {
            DoubleSolution solution = problem.createSolution();
            if (t % 3 == 0) {
                for (int i = 0; i < n; i++) {
                    Bounds<Double> bounds = solution.getBounds(i);
                    solution.variables().set(i, bounds.getLowerBound());
                }
            } else if (t % 3 == 1) {
                for (int i = 0; i < n; i++) {
                    Bounds<Double> bounds = solution.getBounds(i);
                    solution.variables().set(i, bounds.getUpperBound());
                }
            }

            mutation.execute(solution);
            assertSolutionWithinBounds(solution);
        }

        RepairDoubleSolutionWithBoundValue repair = new RepairDoubleSolutionWithBoundValue();
        assertEquals(0.0, repair.repairSolutionVariableValue(-5.0, 0.0, 10.0), 1e-15);
        assertEquals(10.0, repair.repairSolutionVariableValue(15.0, 0.0, 10.0), 1e-15);
        assertEquals(3.5, repair.repairSolutionVariableValue(3.5, 0.0, 10.0), 1e-15);

        DoubleProblem degenerateProblem = new AbstractDoubleProblem() {
            {
                numberOfObjectives(2);
                numberOfConstraints(0);
                variableBounds(List.of(5.0, 0.0), List.of(5.0, 1.0));
            }

            @Override
            public DoubleSolution evaluate(DoubleSolution solution) {
                solution.objectives()[0] = solution.variables().get(0);
                solution.objectives()[1] = solution.variables().get(1);
                return solution;
            }
        };

        DoubleSolution degenerateSolution = degenerateProblem.createSolution();
        degenerateSolution.variables().set(0, 5.0);
        degenerateSolution.variables().set(1, 0.5);
        PolynomialMutation alwaysMutate = new PolynomialMutation(1.0, 20.0, new Random(7L)::nextDouble);
        alwaysMutate.execute(degenerateSolution);
        assertEquals(5.0, degenerateSolution.variables().get(0), 1e-15,
                "Quando lowerBound == upperBound, PolynomialMutation deve preservar yl");
        assertTrue(degenerateSolution.variables().get(1) >= 0.0 && degenerateSolution.variables().get(1) <= 1.0);
    }

    @Test
    void sbxCrossoverDeAndBinaryTournamentSelectionBehaveAsExpectedInWfgStudy() {
        DoubleProblem problem = new WFG1(2, 4, 2); // n = 6 variaveis
        Random rng = new Random(12345L);
        SBXCrossover sbx = new SBXCrossover(1.0, 30.0, rng::nextDouble);

        assertEquals(1.0, sbx.crossoverProbability(), 1e-15);
        assertEquals(30.0, sbx.distributionIndex(), 1e-15);
        assertEquals(2, sbx.numberOfRequiredParents());
        assertEquals(2, sbx.numberOfGeneratedChildren());

        boolean producedDifferentOffspring = false;
        for (int t = 0; t < 100; t++) {
            DoubleSolution p1 = problem.createSolution();
            DoubleSolution p2 = problem.createSolution();
            List<DoubleSolution> offspring = sbx.execute(Arrays.asList(p1, p2));
            assertEquals(2, offspring.size());
            assertSolutionWithinBounds(offspring.get(0));
            assertSolutionWithinBounds(offspring.get(1));

            for (int i = 0; i < problem.numberOfVariables(); i++) {
                if (Double.compare(offspring.get(0).variables().get(i), p1.variables().get(i)) != 0
                        || Double.compare(offspring.get(1).variables().get(i), p2.variables().get(i)) != 0) {
                    producedDifferentOffspring = true;
                }
            }
        }
        assertTrue(producedDifferentOffspring, "SBXCrossover(1.0, 30.0) deve recombinar variaveis distintas dos pais");

        DifferentialEvolutionCrossover de = new DifferentialEvolutionCrossover();
        assertEquals(0.5, de.getCr(), 1e-15);
        assertEquals(0.5, de.getF(), 1e-15);
        assertEquals(3, de.numberOfRequiredParents());
        assertEquals(1, de.numberOfGeneratedChildren());

        DoubleSolution current = problem.createSolution();
        de.setCurrentSolution(current);
        List<DoubleSolution> deOffspring = de.execute(Arrays.asList(
                problem.createSolution(), problem.createSolution(), problem.createSolution()));
        assertEquals(1, deOffspring.size());
        assertSolutionWithinBounds(deOffspring.get(0));

        BinaryTournamentSelection<DoubleSolution> selection = new BinaryTournamentSelection<>();
        DoubleSolution dominating = problem.createSolution();
        dominating.objectives()[0] = 0.1;
        dominating.objectives()[1] = 0.1;
        DoubleSolution dominated = problem.createSolution();
        dominated.objectives()[0] = 5.0;
        dominated.objectives()[1] = 5.0;
        DoubleSolution selected = selection.execute(Arrays.asList(dominating, dominated));
        assertEquals(dominating, selected, "BinaryTournamentSelection deve selecionar a solucao dominante");
    }

    @Test
    void lightweightIntegrationWith2And3ObjectiveProblemsProducesValidSolutions() {
        DoubleProblem wfg2Obj = new WFG1(2, 4, 2);
        CrossoverOperator<DoubleSolution> crossover2 = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation2 = new PolynomialMutation(1.0 / wfg2Obj.numberOfVariables(), 20.0);

        Algorithm<List<DoubleSolution>> agemoea = new AGEMOEABuilder<>(wfg2Obj)
                .setCrossoverOperator(crossover2)
                .setMutationOperator(mutation2)
                .setMaxIterations(3)
                .setPopulationSize(12)
                .build();
        agemoea.run();
        assertValidPopulation(agemoea.result());

        Algorithm<List<DoubleSolution>> fdea = new FDEABuilder(wfg2Obj)
                .setCrossoverOperator(crossover2)
                .setMutationOperator(mutation2)
                .setMaxGenerations(3)
                .setMaxEvaluations(36)
                .setPopulationSize(12)
                .build();
        fdea.run();
        assertValidPopulation(fdea.result());

        DoubleProblem dtlz3Obj = new DTLZ2(12, 3);
        CrossoverOperator<DoubleSolution> crossover3 = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation3 = new PolynomialMutation(1.0 / dtlz3Obj.numberOfVariables(), 20.0);

        Algorithm<List<DoubleSolution>> nsgaiii = new NSGAIIIBuilder<>(dtlz3Obj)
                .setCrossoverOperator(crossover3)
                .setMutationOperator(mutation3)
                .setSelectionOperator(new BinaryTournamentSelection<>())
                .setMaxIterations(3)
                .setNumberOfDivisions(4)
                .build();
        nsgaiii.run();
        assertValidPopulation(nsgaiii.result());

        Algorithm<List<DoubleSolution>> agemoeaii = new AGEMOEAIIBuilder<>(dtlz3Obj)
                .setCrossoverOperator(crossover3)
                .setMutationOperator(mutation3)
                .setMaxIterations(3)
                .setPopulationSize(16)
                .build();
        agemoeaii.run();
        assertValidPopulation(agemoeaii.result());

        EvolutionaryAlgorithm<DoubleSolution> rvea = new RVEABuilder<>(
                dtlz3Obj, 15, 45, crossover3, mutation3, 2.0, 0.1, 4)
                .build();
        rvea.run();
        assertValidPopulation(rvea.result());
    }

    private static DoubleSolution createMidpointSolution(DoubleProblem problem) {
        DoubleSolution solution = problem.createSolution();
        for (int i = 0; i < problem.numberOfVariables(); i++) {
            Bounds<Double> bounds = solution.getBounds(i);
            double mid = 0.5 * (bounds.getLowerBound() + bounds.getUpperBound());
            solution.variables().set(i, mid);
        }
        return solution;
    }

    private static void assertSolutionWithinBounds(DoubleSolution solution) {
        for (int i = 0; i < solution.variables().size(); i++) {
            double val = solution.variables().get(i);
            Bounds<Double> bounds = solution.getBounds(i);
            assertFalse(Double.isNaN(val) || Double.isInfinite(val),
                    "Variavel " + i + " nao pode ser NaN ou infinita");
            assertTrue(val >= bounds.getLowerBound() && val <= bounds.getUpperBound(),
                    "Variavel " + i + " fora dos limites [" + bounds.getLowerBound() + ", "
                            + bounds.getUpperBound() + "]: " + val);
        }
    }

    private static void assertValidPopulation(List<DoubleSolution> population) {
        assertNotNull(population);
        assertFalse(population.isEmpty(), "Populacao resultante nao deve ser vazia");
        for (DoubleSolution s : population) {
            assertSolutionWithinBounds(s);
            for (double obj : s.objectives()) {
                assertFalse(Double.isNaN(obj) || Double.isInfinite(obj), "Objetivo invalido: " + obj);
            }
        }
    }
}

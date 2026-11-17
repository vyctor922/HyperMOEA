package br.usp.lti.newmoea.algorithm;

import br.usp.lti.newmoea.algorithm.fdea.FDEA;
import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEAD;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADD;
import org.uma.jmetal.algorithm.multiobjective.spea2.SPEA2Builder;
import org.uma.jmetal.algorithm.multiobjective.wasfga.util.WeightVectors;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.problem.multiobjective.wfg.WFG2;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.densityestimator.impl.CrowdingDistanceDensityEstimator;
import org.uma.jmetal.util.fileinput.VectorFileUtils;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;
import org.uma.jmetal.util.ranking.impl.FastNonDominatedSortRanking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de auditoria de identidade algorítmica do FDEA, parâmetros internos do MOEA/DD
 * e comportamento dos algoritmos legados (Tarefa T14).
 */
class FdeaIdentityAndLegacyAuditTest {

    /**
     * Wrapper de Problem que contabiliza cada chamada real a evaluate(solution).
     */
    private static final class CountingDoubleProblem implements Problem<DoubleSolution> {
        private final Problem<DoubleSolution> delegate;
        private final AtomicInteger evaluateCount = new AtomicInteger(0);

        CountingDoubleProblem(Problem<DoubleSolution> delegate) {
            this.delegate = delegate;
        }

        @Override
        public int numberOfVariables() {
            return delegate.numberOfVariables();
        }

        @Override
        public int numberOfObjectives() {
            return delegate.numberOfObjectives();
        }

        @Override
        public int numberOfConstraints() {
            return delegate.numberOfConstraints();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public DoubleSolution evaluate(DoubleSolution solution) {
            evaluateCount.incrementAndGet();
            return delegate.evaluate(solution);
        }

        @Override
        public DoubleSolution createSolution() {
            return delegate.createSolution();
        }

        int getEvaluateCount() {
            return evaluateCount.get();
        }
    }

    @Test
    void testFdeaVersionIBitForBitDeterminismWithFixedSeed() {
        long seed = 20261109L;

        List<DoubleSolution> run1 = executeFdeaWithSeed(seed, FDEA.VERSION.I, true);
        List<DoubleSolution> run2 = executeFdeaWithSeed(seed, FDEA.VERSION.I, true);

        assertSolutionsBitForBitIdentical(run1, run2);
    }

    @Test
    void testFdeaVersionIIBitForBitDeterminismWithFixedSeed() {
        long seed = 987654321L;

        List<DoubleSolution> run1 = executeFdeaWithSeed(seed, FDEA.VERSION.II, true);
        List<DoubleSolution> run2 = executeFdeaWithSeed(seed, FDEA.VERSION.II, true);

        assertSolutionsBitForBitIdentical(run1, run2);
    }

    @Test
    void testFdeaRandomSelectionFallbackDeterminismWhenSelectionOperatorIsNull() {
        long seed = 424242L;

        List<DoubleSolution> run1 = executeFdeaWithSeed(seed, FDEA.VERSION.I, false);
        List<DoubleSolution> run2 = executeFdeaWithSeed(seed, FDEA.VERSION.I, false);

        assertSolutionsBitForBitIdentical(run1, run2);
    }

    @Test
    void testFdeaStrictBudgetComplianceIncludingOddPopulationAndBoundaryBudgets() {
        int oddPopSize = 91; // Tamanho populacional canônico para m=3
        int[] budgets = new int[] {
            oddPopSize,             // Exatamente N (apenas initPopulation)
            oddPopSize + 1,         // N + 1 (1 avaliação na 1ª geração)
            (2 * oddPopSize) - 1,   // 2N - 1 (geração parcial)
            2 * oddPopSize          // 2N (duas gerações completas)
        };

        for (int budget : budgets) {
            JMetalRandom.getInstance().setSeed(12345L);
            CountingDoubleProblem countingProblem = new CountingDoubleProblem(new WFG1(4, 20, 3));
            CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
            MutationOperator<DoubleSolution> mutation =
                    new PolynomialMutation(1.0 / countingProblem.numberOfVariables(), 20.0);
            SelectionOperator<List<DoubleSolution>, DoubleSolution> selection =
                    new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>());

            FDEA fdea = (FDEA) new FDEABuilder(countingProblem)
                    .setPopulationSize(oddPopSize)
                    .setMaxEvaluations(budget)
                    .setCrossoverOperator(crossover)
                    .setMutationOperator(mutation)
                    .setSelectionOperator(selection)
                    .setVersion(FDEA.VERSION.I)
                    .build();

            fdea.run();

            assertEquals(budget, fdea.getEvaluations(),
                    "FDEA.getEvaluations() deve respeitar estritamente o orçamento B=" + budget);
            assertEquals(budget, countingProblem.getEvaluateCount(),
                    "Número real de chamadas a problem.evaluate() deve ser exatamente B=" + budget);
            assertNotNull(fdea.result());
            assertFalse(fdea.result().isEmpty());
        }
    }

    @Test
    void testFdeaBuilderAutomaticallyDerivesMaxGenerationsFromMaxEvaluations() {
        CountingDoubleProblem countingProblem = new CountingDoubleProblem(new WFG1(2, 4, 2));
        int popSize = 10;
        // Default maxGenerations no FDEABuilder era 250; testamos se apenas setMaxEvaluations(35)
        // ajusta maxGenerations adequadamente e para exatamente em 35 avaliações.
        FDEA fdea = (FDEA) new FDEABuilder(countingProblem)
                .setPopulationSize(popSize)
                .setMaxEvaluations(35)
                .setCrossoverOperator(new SBXCrossover(1.0, 30.0))
                .setMutationOperator(new PolynomialMutation(1.0 / countingProblem.numberOfVariables(), 20.0))
                .build();

        assertEquals(4, fdea.getMaxGenerations());
        fdea.run();
        assertEquals(35, fdea.getEvaluations());
        assertEquals(35, countingProblem.getEvaluateCount());
    }

    @Test
    void testFdeaPopulatesRankingAndCrowdingAttributesBeforeSelectionOperator() {
        JMetalRandom.getInstance().setSeed(777L);
        Problem<DoubleSolution> problem = new WFG2(4, 20, 3);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        FastNonDominatedSortRanking<DoubleSolution> rankingReader = new FastNonDominatedSortRanking<>();
        CrowdingDistanceDensityEstimator<DoubleSolution> crowdingReader = new CrowdingDistanceDensityEstimator<>();
        AtomicInteger selectionCalls = new AtomicInteger(0);

        SelectionOperator<List<DoubleSolution>, DoubleSolution> auditingSelection = population -> {
            selectionCalls.incrementAndGet();
            for (DoubleSolution sol : population) {
                Integer rank = rankingReader.getRank(sol);
                Double crowding = crowdingReader.value(sol);
                assertNotNull(rank, "Rank attribute não pode ser nulo durante a seleção");
                assertTrue(rank >= 0, "Rank deve ter sido computado (>= 0), mas foi: " + rank);
                assertNotNull(crowding, "Crowding distance não pode ser nulo durante a seleção");
                assertFalse(Double.isNaN(crowding), "Crowding distance não pode ser NaN");
            }
            return new BinaryTournamentSelection<DoubleSolution>(
                    new RankingAndCrowdingDistanceComparator<>()).execute(population);
        };

        Algorithm<List<DoubleSolution>> fdea = new FDEABuilder(problem)
                .setPopulationSize(20)
                .setMaxGenerations(3)
                .setMaxEvaluations(60)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setSelectionOperator(auditingSelection)
                .build();

        fdea.run();
        assertTrue(selectionCalls.get() > 0, "O operador de seleção deveria ter sido invocado na reprodução");
    }

    @Test
    void testMoeaddInternalParametersAndTwoObjectiveWeightGenerationBehavior() throws Exception {
        // 1. Verifica m=2, N=100: AbstractMOEAD.initializeUniformWeight() ignora o arquivo .dat
        // quando m=2 e N <= 300, gerando pesos em memória com 0.0 nos extremos.
        Problem<DoubleSolution> wfg1M2 = new WFG1(2, 20, 2);
        MOEADD<DoubleSolution> moeaddM2 = (MOEADD<DoubleSolution>) new MOEADBuilder(wfg1M2, MOEADBuilder.Variant.MOEADD)
                .setCrossover(new SBXCrossover(1.0, 30.0))
                .setMutation(new PolynomialMutation(1.0 / wfg1M2.numberOfVariables(), 20.0))
                .setMaxEvaluations(200)
                .setPopulationSize(100)
                .setResultPopulationSize(100)
                .setNeighborhoodSelectionProbability(0.9)
                .setMaximumNumberOfReplacedSolutions(2)
                .setNeighborSize(20)
                .setFunctionType(MOEAD.FunctionType.PBI)
                .setDataDirectory("MOEAD_Weights")
                .build();

        assertEquals(20, getFieldValue(moeaddM2, AbstractMOEAD.class, "neighborSize"));
        assertEquals(0.9, (Double) getFieldValue(moeaddM2, AbstractMOEAD.class, "neighborhoodSelectionProbability"), 1e-12);
        assertEquals(2, getFieldValue(moeaddM2, AbstractMOEAD.class, "maximumNumberOfReplacedSolutions"));
        assertEquals(MOEAD.FunctionType.PBI, getFieldValue(moeaddM2, AbstractMOEAD.class, "functionType"));
        assertEquals("MOEAD_Weights", getFieldValue(moeaddM2, AbstractMOEAD.class, "dataDirectory"));

        Method initWeightMethod = AbstractMOEAD.class.getDeclaredMethod("initializeUniformWeight");
        initWeightMethod.setAccessible(true);
        initWeightMethod.invoke(moeaddM2);

        double[][] lambdaM2 = (double[][]) getFieldValue(moeaddM2, AbstractMOEAD.class, "lambda");
        assertEquals(100, lambdaM2.length);
        assertEquals(0.0, lambdaM2[0][0], 0.0, "Para m=2 e N<=300, initializeUniformWeight gera 0.0 exato em memória");
        assertEquals(1.0, lambdaM2[0][1], 0.0);
        assertEquals(1.0, lambdaM2[99][0], 0.0);
        assertEquals(0.0, lambdaM2[99][1], 0.0);

        // 2. Verifica m=3, N=91: carrega o arquivo MOEAD_Weights/W3D_91.dat (simplex unitário, soma = 1.0)
        Problem<DoubleSolution> wfg1M3 = new WFG1(4, 20, 3);
        MOEADD<DoubleSolution> moeaddM3 = (MOEADD<DoubleSolution>) new MOEADBuilder(wfg1M3, MOEADBuilder.Variant.MOEADD)
                .setCrossover(new SBXCrossover(1.0, 30.0))
                .setMutation(new PolynomialMutation(1.0 / wfg1M3.numberOfVariables(), 20.0))
                .setMaxEvaluations(182)
                .setPopulationSize(91)
                .setResultPopulationSize(91)
                .setNeighborhoodSelectionProbability(0.9)
                .setMaximumNumberOfReplacedSolutions(2)
                .setNeighborSize(20)
                .setFunctionType(MOEAD.FunctionType.PBI)
                .setDataDirectory("MOEAD_Weights")
                .build();

        initWeightMethod.invoke(moeaddM3);
        double[][] lambdaM3 = (double[][]) getFieldValue(moeaddM3, AbstractMOEAD.class, "lambda");
        assertEquals(91, lambdaM3.length);
        for (double[] row : lambdaM3) {
            assertEquals(3, row.length);
            double rowSum = 0.0;
            for (double v : row) {
                assertTrue(v >= 0.0 && v <= 1.0, "Componente de peso deve estar em [0, 1], mas foi: " + v);
                rowSum += v;
            }
            assertEquals(1.0, rowSum, 1e-3, "Cada vetor de peso em W3D_91.dat deve somar aproximadamente 1.0");
        }

        // 3. Verifica que PBI usa theta = 5.0 hardcoded em AbstractMOEAD.fitnessFunction
        DoubleSolution dummySol = wfg1M3.createSolution();
        dummySol.objectives()[0] = 1.0;
        dummySol.objectives()[1] = 2.0;
        dummySol.objectives()[2] = 3.0;
        org.uma.jmetal.util.point.impl.IdealPoint idealPoint =
                (org.uma.jmetal.util.point.impl.IdealPoint) getFieldValue(moeaddM3, AbstractMOEAD.class, "idealPoint");
        idealPoint.update(new double[] {0.0, 0.0, 0.0});

        Method fitnessFunction = AbstractMOEAD.class.getDeclaredMethod("fitnessFunction",
                org.uma.jmetal.solution.Solution.class, double[].class);
        fitnessFunction.setAccessible(true);
        double[] unitAxis = new double[] {1.0, 0.0, 0.0};
        double pbiValue = (Double) fitnessFunction.invoke(moeaddM3, dummySol, unitAxis);
        // Para f=(1,2,3), z*=(0,0,0) e lambda=(1,0,0): d1 = 1.0, d2 = sqrt(0^2 + 2^2 + 3^2) = sqrt(13)
        // Com theta = 5.0: g_pbi = 1.0 + 5.0 * sqrt(13)
        double expectedPbi = 1.0 + 5.0 * Math.sqrt(13.0);
        assertEquals(expectedPbi, pbiValue, 1e-9, "AbstractMOEAD.fitnessFunction deve usar theta = 5.0 hardcoded no PBI");
    }

    @Test
    void testLegacyGwasfgaWeightInversionProducesNaNOnZeroWeightsAndFiniteOnZeroReplacedWeights() {
        // Vetores oficiais com zeros em resources/weightVectorFiles/W3D_91.dat
        double[][] officialWeightsWithZeros = VectorFileUtils.readVectors("resources/weightVectorFiles/W3D_91.dat");
        double[][] invertedOfficial = WeightVectors.invert(officialWeightsWithZeros, true);

        boolean foundNaNInOfficial = false;
        for (double[] row : invertedOfficial) {
            for (double val : row) {
                if (Double.isNaN(val) || Double.isInfinite(val)) {
                    foundNaNInOfficial = true;
                    break;
                }
            }
        }
        assertTrue(foundNaNInOfficial,
                "WeightVectors.invert(weights, true) deve produzir NaN/Infinity quando há pesos 0.0 exatos");

        // Simula a substituição 0.0 -> 1e-5 que era usada para evitar NaN no GWASFGA
        double[][] zeroReplacedWeights = new double[officialWeightsWithZeros.length][];
        for (int i = 0; i < officialWeightsWithZeros.length; i++) {
            zeroReplacedWeights[i] = new double[officialWeightsWithZeros[i].length];
            for (int j = 0; j < officialWeightsWithZeros[i].length; j++) {
                zeroReplacedWeights[i][j] = officialWeightsWithZeros[i][j] == 0.0 ? 1.0e-5 : officialWeightsWithZeros[i][j];
            }
        }
        double[][] invertedModified = WeightVectors.invert(zeroReplacedWeights, true);

        for (double[] row : invertedModified) {
            for (double val : row) {
                assertTrue(Double.isFinite(val),
                        "WeightVectors.invert com piso 1e-5 deve produzir apenas valores finitos");
            }
        }
    }

    @Test
    void testLegacySpea2BuilderDefaultPopulationSizeIs100WhenNotExplicitlySet() {
        Problem<DoubleSolution> problem = new WFG1(4, 20, 3);
        SPEA2Builder<DoubleSolution> builder = new SPEA2Builder<>(
                problem,
                new SBXCrossover(1.0, 30.0),
                new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0));
        builder.setMaxIterations(400);
        // Reproduz a omissão de WFGStudy.buildSPEA2: não chamar builder.setPopulationSize(populationSize)
        assertEquals(100, builder.getPopulationSize(),
                "SPEA2Builder mantém populationSize default de 100 quando setPopulationSize não é chamado");
    }

    private static List<DoubleSolution> executeFdeaWithSeed(long seed, FDEA.VERSION version, boolean useTournamentSelection) {
        JMetalRandom.getInstance().setSeed(seed);
        Problem<DoubleSolution> problem = new WFG1(4, 20, 3);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        SelectionOperator<List<DoubleSolution>, DoubleSolution> selection = useTournamentSelection
                ? new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>())
                : null;

        FDEA fdea = (FDEA) new FDEABuilder(problem)
                .setPopulationSize(30)
                .setMaxGenerations(5)
                .setMaxEvaluations(150)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setSelectionOperator(selection)
                .setVersion(version)
                .build();

        fdea.run();
        return new ArrayList<>(fdea.getPopulation());
    }

    private static void assertSolutionsBitForBitIdentical(List<DoubleSolution> pop1, List<DoubleSolution> pop2) {
        assertEquals(pop1.size(), pop2.size(), "Tamanhos das populações devem ser idênticos");
        for (int i = 0; i < pop1.size(); i++) {
            DoubleSolution s1 = pop1.get(i);
            DoubleSolution s2 = pop2.get(i);

            long[] vars1 = s1.variables().stream().mapToLong(Double::doubleToLongBits).toArray();
            long[] vars2 = s2.variables().stream().mapToLong(Double::doubleToLongBits).toArray();
            assertArrayEquals(vars1, vars2, "Variáveis de decisão diferem no indivíduo " + i);

            long[] objs1 = new long[s1.objectives().length];
            long[] objs2 = new long[s2.objectives().length];
            for (int j = 0; j < objs1.length; j++) {
                objs1[j] = Double.doubleToLongBits(s1.objectives()[j]);
                objs2[j] = Double.doubleToLongBits(s2.objectives()[j]);
            }
            assertArrayEquals(objs1, objs2, "Objetivos diferem bit-a-bit no indivíduo " + i);
        }
    }

    private static Object getFieldValue(Object instance, Class<?> declaringClass, String fieldName) throws Exception {
        Field field = declaringClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }
}

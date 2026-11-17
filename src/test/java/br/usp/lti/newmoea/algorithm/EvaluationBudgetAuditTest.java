package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import br.usp.lti.newmoea.util.EvaluationBudgetProblem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEA;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAII;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII;
import org.uma.jmetal.component.algorithm.EvolutionaryAlgorithm;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

/**
 * Suíte de auditoria da contabilidade real de avaliações ({@code Problem.evaluate}), tamanho
 * efetivo da população e condições de parada para os 6 algoritmos principais em todas as dimensões
 * \(m \in \{2, 3, 5, 8, 10, 15\}\) (T13).
 */
class EvaluationBudgetAuditTest {

    private static final int REDUCED_GENERATIONS = 3;

    private static DoubleProblem createFastBenchmarkProblem(int m) {
        // DTLZ2 com n = m + 5 variáveis é extremamente leve para testes de contabilidade em todas as dimensões
        return new DTLZ2(m + 5, m);
    }

    private static CrossoverOperator<DoubleSolution> createCrossover() {
        return new SBXCrossover(1.0, 30.0);
    }

    private static MutationOperator<DoubleSolution> createMutation(DoubleProblem problem) {
        return new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
    }

    @ParameterizedTest(name = "m = {0} objetivos: auditoria populacional e de orçamento em G=3 gerações")
    @ValueSource(ints = {2, 3, 5, 8, 10, 15})
    @DisplayName("Deve confirmar N_config, N_alloc e B_real de todos os 6 algoritmos em m in {2,3,5,8,10,15}")
    void shouldVerifyPopulationAndActualEvaluationsAcrossAllDimensions(int m) {
        int nConfig = EvaluationBudgetProblem.getConfiguredPopulationSize(m);
        int targetBudget = nConfig * REDUCED_GENERATIONS;
        DoubleProblem baseProblem = createFastBenchmarkProblem(m);

        for (String algName : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
            JMetalRandom.getInstance().setSeed(12345L + m);
            EvaluationBudgetProblem auditProblem =
                    new EvaluationBudgetProblem(baseProblem, targetBudget, false);
            Algorithm<List<DoubleSolution>> algorithm =
                    EvaluationBudgetProblem.buildAuditedAlgorithm(
                            algName,
                            auditProblem,
                            REDUCED_GENERATIONS,
                            createCrossover(),
                            createMutation(auditProblem));

            int expectedAlloc = EvaluationBudgetProblem.getAllocatedPopulationSize(algName, m);
            if (algorithm instanceof AbstractGeneticAlgorithm<?, ?> aga) {
                assertThat(aga.getMaxPopulationSize())
                        .as("N_alloc interno de %s em m=%d", algName, m)
                        .isEqualTo(expectedAlloc);
            }

            algorithm.run();
            List<DoubleSolution> result = algorithm.result();

            long actualEvaluations = auditProblem.getActualProblemEvaluations();
            long expectedEvaluations =
                    EvaluationBudgetProblem.predictUnconstrainedEvaluations(
                            algName, m, REDUCED_GENERATIONS, targetBudget);

            assertThat(actualEvaluations)
                    .as("Contagem real de Problem.evaluate para %s em m=%d (G=%d)", algName, m, REDUCED_GENERATIONS)
                    .isEqualTo(expectedEvaluations);

            assertThat(result)
                    .as("Resultado final de %s em m=%d não deve ser nulo nem vazio", algName, m)
                    .isNotNull()
                    .isNotEmpty();
            assertThat(result.size())
                    .as("Cardinalidade de result() de %s em m=%d não deve exceder N_alloc=%d", algName, m, expectedAlloc)
                    .isLessThanOrEqualTo(expectedAlloc);
        }
    }

    @Test
    @DisplayName("Deve verificar condições de fronteira B in {N, N+1, 2N-1, 2N} em N par (m=2, N=100) e N ímpar (m=3, N=91)")
    void shouldVerifyBoundaryBudgetsForEvenAndOddPopulations() {
        int[] testDimensions = {2, 3};

        for (int m : testDimensions) {
            int nConfig = EvaluationBudgetProblem.getConfiguredPopulationSize(m);
            int[] boundaryBudgets = {nConfig, nConfig + 1, 2 * nConfig - 1, 2 * nConfig};
            int[] correspondingGenerations = {1, 2, 2, 2};
            DoubleProblem baseProblem = createFastBenchmarkProblem(m);

            for (int idx = 0; idx < boundaryBudgets.length; idx++) {
                int budget = boundaryBudgets[idx];
                int generations = correspondingGenerations[idx];

                for (String algName : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
                    JMetalRandom.getInstance().setSeed(777L + idx);
                    EvaluationBudgetProblem auditProblem =
                            new EvaluationBudgetProblem(baseProblem, budget, false);
                    Algorithm<List<DoubleSolution>> algorithm =
                            EvaluationBudgetProblem.buildAuditedAlgorithm(
                                    algName,
                                    auditProblem,
                                    generations,
                                    createCrossover(),
                                    createMutation(auditProblem));

                    algorithm.run();

                    long actualEvals = auditProblem.getActualProblemEvaluations();
                    long predictedEvals =
                            EvaluationBudgetProblem.predictUnconstrainedEvaluations(
                                    algName, m, generations, budget);

                    assertThat(actualEvals)
                            .as(
                                    "Fronteira B=%d (G=%d) para %s em m=%d (N=%d)",
                                    budget, generations, algName, m, nConfig)
                            .isEqualTo(predictedEvals);
                }
            }
        }
    }

    @ParameterizedTest(name = "m = {0} objetivos: contrato estrito impõe B_real == B_target exato")
    @ValueSource(ints = {2, 3, 5, 8, 10, 15})
    @DisplayName("Deve garantir B_real == B_target exato para todos os 6 algoritmos sob modo de contrato estrito")
    void shouldEnforceExactEvaluationBudgetAcrossAllAlgorithmsAndDimensions(int m) {
        int nConfig = EvaluationBudgetProblem.getConfiguredPopulationSize(m);
        int targetBudget = nConfig * REDUCED_GENERATIONS;
        DoubleProblem baseProblem = createFastBenchmarkProblem(m);

        for (String algName : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
            JMetalRandom.getInstance().setSeed(9999L + m);
            EvaluationBudgetProblem strictProblem =
                    new EvaluationBudgetProblem(baseProblem, targetBudget, true);
            Algorithm<List<DoubleSolution>> algorithm =
                    EvaluationBudgetProblem.buildAuditedAlgorithm(
                            algName,
                            strictProblem,
                            REDUCED_GENERATIONS,
                            createCrossover(),
                            createMutation(strictProblem));

            algorithm.run();
            List<DoubleSolution> result = algorithm.result();

            assertThat(strictProblem.getActualProblemEvaluations())
                    .as(
                            "Orçamento real estrito (actualProblemEvaluations) de %s em m=%d",
                            algName, m)
                    .isEqualTo(targetBudget);

            assertThat(strictProblem.getRemainingBudget())
                    .as("Orçamento restante de %s em m=%d após parada", algName, m)
                    .isZero();

            assertThat(result)
                    .as("População final de %s sob contrato estrito em m=%d", algName, m)
                    .isNotNull()
                    .isNotEmpty();

            for (DoubleSolution sol : result) {
                for (double obj : sol.objectives()) {
                    assertThat(obj)
                            .as("Objetivo finito em %s (m=%d)", algName, m)
                            .isFinite();
                }
            }
        }
    }

    @Test
    @DisplayName("Deve impor exatamente B em orçamentos não múltiplos do lote (B = N+1 e B = 2N-1) sem corromper o algoritmo")
    void shouldEnforceStrictBudgetOnPartialBatchBoundaries() {
        int m = 3; // N_config = 91 (ímpar)
        int nConfig = EvaluationBudgetProblem.getConfiguredPopulationSize(m);
        int[] nonMultipleBudgets = {nConfig + 1, 2 * nConfig - 1};
        DoubleProblem baseProblem = createFastBenchmarkProblem(m);

        for (int budget : nonMultipleBudgets) {
            for (String algName : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
                JMetalRandom.getInstance().setSeed(20261109L);
                EvaluationBudgetProblem strictProblem =
                        new EvaluationBudgetProblem(baseProblem, budget, true);
                Algorithm<List<DoubleSolution>> algorithm =
                        EvaluationBudgetProblem.buildAuditedAlgorithm(
                                algName,
                                strictProblem,
                                2,
                                createCrossover(),
                                createMutation(strictProblem));

                algorithm.run();
                List<DoubleSolution> result = algorithm.result();

                assertThat(strictProblem.getActualProblemEvaluations())
                        .as("B_real exato em fronteira parcial B=%d para %s (m=%d)", budget, algName, m)
                        .isEqualTo(budget);
                assertThat(result).isNotNull().isNotEmpty();
            }
        }
    }

    @Test
    @DisplayName("Deve provar que EvaluationBudgetProblem não introduz avaliações extras nem altera a trajetória determinística")
    void shouldNotIntroduceExtraEvaluationsOrAlterDeterministicTrajectory() {
        int m = 3;
        int nConfig = EvaluationBudgetProblem.getConfiguredPopulationSize(m);
        int generations = StaticParams.getMaxGenerations(m, "WFG1");
        int targetBudget = nConfig * generations;

        DoubleProblem problemDirect = new WFG1(2 * (m - 1), 20, m);
        DoubleProblem problemWrappedBase = new WFG1(2 * (m - 1), 20, m);
        EvaluationBudgetProblem wrappedProblem =
                new EvaluationBudgetProblem(problemWrappedBase, targetBudget, false);

        JMetalRandom.getInstance().setSeed(424242L);
        Algorithm<List<DoubleSolution>> algDirect =
                WFGStudy.buildRVEA(
                        problemDirect,
                        nConfig,
                        new SBXCrossover(1.0, 30.0),
                        new PolynomialMutation(1.0 / problemDirect.numberOfVariables(), 20.0));
        algDirect.run();
        List<DoubleSolution> directResult = algDirect.result();

        JMetalRandom.getInstance().setSeed(424242L);
        Algorithm<List<DoubleSolution>> algWrapped =
                WFGStudy.buildRVEA(
                        wrappedProblem,
                        nConfig,
                        new SBXCrossover(1.0, 30.0),
                        new PolynomialMutation(1.0 / wrappedProblem.numberOfVariables(), 20.0));
        algWrapped.run();
        List<DoubleSolution> wrappedResult = algWrapped.result();

        assertThat(wrappedProblem.getActualProblemEvaluations()).isEqualTo(targetBudget);
        assertThat(wrappedResult).hasSameSizeAs(directResult);
        for (int i = 0; i < directResult.size(); i++) {
            assertThat(wrappedResult.get(i).objectives())
                    .containsExactly(directResult.get(i).objectives());
        }
    }

    @Test
    @DisplayName("Deve validar consistência do arquivo docs/auditoria/budget-audit.csv contra as fórmulas e medições")
    void shouldValidateGeneratedBudgetAuditCsvConsistency() throws IOException {
        Path csvPath = Paths.get("docs", "auditoria", "budget-audit.csv");
        assertThat(csvPath).exists();

        List<String> lines = Files.readAllLines(csvPath);
        // 1 cabeçalho + 6 algoritmos * 6 dimensões = 37 linhas
        assertThat(lines).hasSize(37);
        assertThat(lines.get(0))
                .isEqualTo(
                        "algorithm,m,n_config,n_alloc_current,g_max_wfg,b_target_wfg,b_real_current_wfg,delta_evals_wfg,pct_deviation_wfg,boundary_N,boundary_N_plus_1,boundary_2N_minus_1,boundary_2N,stopping_mechanism");

        int rowIndex = 1;
        for (int m : EvaluationBudgetProblem.CANONICAL_OBJECTIVES) {
            int nConfig = EvaluationBudgetProblem.getConfiguredPopulationSize(m);
            int gMaxWfg = StaticParams.getMaxGenerations(m, "WFG1");
            int bTargetWfg = EvaluationBudgetProblem.getTargetBudget(m, "WFG1");

            for (String alg : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
                int nAlloc = EvaluationBudgetProblem.getAllocatedPopulationSize(alg, m);
                int bRealWfg =
                        EvaluationBudgetProblem.predictUnconstrainedEvaluations(
                                alg, m, gMaxWfg, bTargetWfg);
                int deltaWfg = bRealWfg - bTargetWfg;
                double pctWfg = (100.0 * deltaWfg) / bTargetWfg;

                int bAtN = EvaluationBudgetProblem.predictUnconstrainedEvaluations(alg, m, 1, nConfig);
                int bAtNPlus1 =
                        EvaluationBudgetProblem.predictUnconstrainedEvaluations(
                                alg, m, 2, nConfig + 1);
                int bAt2NMinus1 =
                        EvaluationBudgetProblem.predictUnconstrainedEvaluations(
                                alg, m, 2, 2 * nConfig - 1);
                int bAt2N =
                        EvaluationBudgetProblem.predictUnconstrainedEvaluations(
                                alg, m, 2, 2 * nConfig);

                String expectedPrefix =
                        String.format(
                                Locale.US,
                                "%s,%d,%d,%d,%d,%d,%d,%d,%.4f,%d,%d,%d,%d,",
                                alg,
                                m,
                                nConfig,
                                nAlloc,
                                gMaxWfg,
                                bTargetWfg,
                                bRealWfg,
                                deltaWfg,
                                pctWfg,
                                bAtN,
                                bAtNPlus1,
                                bAt2NMinus1,
                                bAt2N);
                assertThat(lines.get(rowIndex))
                        .as("Linha %d do CSV (%s, m=%d)", rowIndex, alg, m)
                        .startsWith(expectedPrefix);
                rowIndex++;
            }
        }
    }

    @Test
    @DisplayName("Deve rejeitar parâmetros inválidos no construtor e nos métodos utilitários")
    void shouldRejectInvalidArguments() {
        DoubleProblem base = createFastBenchmarkProblem(2);
        assertThatThrownBy(() -> new EvaluationBudgetProblem(null, 100))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvaluationBudgetProblem(base, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvaluationBudgetProblem.getConfiguredPopulationSize(4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvaluationBudgetProblem.normalizeAlgorithmName("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

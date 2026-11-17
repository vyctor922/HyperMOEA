package br.usp.lti.newmoea.util;

import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder;
import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder;
import org.uma.jmetal.component.catalogue.common.evaluation.Evaluation;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.bounds.Bounds;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.referencepoint.ReferencePointGenerator;

/**
 * Wrapper thread-safe sobre {@link DoubleProblem} para auditoria e imposição de contrato de
 * orçamento real de avaliações ({@link Problem#evaluate(Object)}) na Fase 3 (T13).
 *
 * <p>Contabiliza atomicamente cada chamada real a {@code evaluate(DoubleSolution)} de forma
 * independente dos contadores internos dos algoritmos e suporta dois modos operacionais:
 *
 * <ul>
 *   <li><b>Modo Auditoria ({@code enforceHardBudget = false})</b>: executa 100% das chamadas a
 *       {@link #evaluate(DoubleSolution)} feitas pelo algoritmo até sua parada natural,
 *       contabilizando avaliações efetivas, excedentes ({@code evaluations > maxBudget}) e a última
 *       solução válida avaliada dentro do orçamento.
 *   <li><b>Modo Contrato Estrito ({@code enforceHardBudget = true} + {@link BudgetContractEvaluator})</b>:
 *       impõe o teto exato {@code maxBudget} de avaliações reais ao problema subjacente sem lançar
 *       exceção arbitrária que corrompa o estado do algoritmo. Quando acoplado via
 *       {@link #createSolutionListEvaluator()} (para {@code NSGA-III}, {@code AGE-MOEA},
 *       {@code AGE-MOEA-II}) ou {@link #createComponentEvaluation()} (para {@code RVEA}), trunca de
 *       forma segura o lote final parcial para exatamente as soluções avaliadas dentro do orçamento
 *       restante. Caso uma chamada residual a {@link #evaluate(DoubleSolution)} ocorra diretamente
 *       após o esgotamento do orçamento, realiza fallback determinístico sem invocar o problema
 *       subjacente, registrando separadamente {@link #getActualProblemEvaluations()} e
 *       {@link #getAttemptedEvaluations()}.
 * </ul>
 */
public class EvaluationBudgetProblem implements DoubleProblem {

    private static final long serialVersionUID = 1L;

    /** Identificadores canônicos dos 6 algoritmos principais do estudo. */
    public static final List<String> MAIN_ALGORITHMS =
            List.of("NSGA-III", "MOEA/DD", "FDEA", "RVEA", "AGE-MOEA", "AGE-MOEA-II");

    /** Dimensões de objetivos canônicas do protocolo experimental. */
    public static final int[] CANONICAL_OBJECTIVES = {2, 3, 5, 8, 10, 15};

    private final DoubleProblem delegate;
    private final long maxBudget;
    private final boolean enforceHardBudget;

    private final AtomicLong attemptedEvaluations = new AtomicLong(0L);
    private final AtomicLong actualProblemEvaluations = new AtomicLong(0L);
    private final AtomicLong suppressedOvershootEvaluations = new AtomicLong(0L);
    private final AtomicInteger evaluatorBatchCalls = new AtomicInteger(0);
    private final AtomicReference<double[]> lastValidObjectives = new AtomicReference<>(null);
    private final AtomicReference<List<DoubleSolution>> lastValidBatchSnapshot =
            new AtomicReference<>(Collections.emptyList());

    /**
     * Cria um wrapper em modo auditoria ({@code enforceHardBudget = false}) com orçamento alvo
     * informado.
     *
     * @param delegate problema {@link DoubleProblem} subjacente
     * @param maxBudget orçamento alvo \(B_{\text{target}}\) para auditoria de excedente
     */
    public EvaluationBudgetProblem(DoubleProblem delegate, long maxBudget) {
        this(delegate, maxBudget, false);
    }

    /**
     * Cria um wrapper configurável sobre {@link DoubleProblem}.
     *
     * @param delegate problema {@link DoubleProblem} subjacente (não nulo)
     * @param maxBudget orçamento alvo \(B_{\text{target}} > 0\)
     * @param enforceHardBudget {@code true} para bloquear chamadas reais ao problema subjacente
     *     após atingir {@code maxBudget}; {@code false} para apenas auditar todas as chamadas
     */
    public EvaluationBudgetProblem(
            DoubleProblem delegate, long maxBudget, boolean enforceHardBudget) {
        this.delegate = Objects.requireNonNull(delegate, "O problema delegate não pode ser nulo.");
        if (maxBudget <= 0) {
            throw new IllegalArgumentException(
                    "O orçamento máximo (maxBudget) deve ser positivo: " + maxBudget);
        }
        this.maxBudget = maxBudget;
        this.enforceHardBudget = enforceHardBudget;
    }

    /**
     * Avalia uma solução contabilizando atomicamente a chamada.
     *
     * @param solution solução a avaliar
     * @return a própria solução avaliada (ou preenchida com objetivos finitos de fallback caso
     *     {@code enforceHardBudget == true} e o orçamento já esteja esgotado)
     */
    @Override
    public DoubleSolution evaluate(DoubleSolution solution) {
        Objects.requireNonNull(solution, "A solução a avaliar não pode ser nula.");
        long attemptIndex = attemptedEvaluations.incrementAndGet();

        if (!enforceHardBudget || attemptIndex <= maxBudget) {
            DoubleSolution evaluated = delegate.evaluate(solution);
            actualProblemEvaluations.incrementAndGet();
            if (attemptIndex <= maxBudget) {
                double[] objs = evaluated.objectives();
                if (objs != null) {
                    lastValidObjectives.set(objs.clone());
                }
            } else {
                suppressedOvershootEvaluations.incrementAndGet();
            }
            return evaluated;
        }

        // Hard-stop ativo e tentativa excede maxBudget: não invoca delegate.evaluate(solution)
        suppressedOvershootEvaluations.incrementAndGet();
        double[] fallback = lastValidObjectives.get();
        double[] targetObjectives = solution.objectives();
        if (targetObjectives != null) {
            if (fallback != null && fallback.length == targetObjectives.length) {
                System.arraycopy(fallback, 0, targetObjectives, 0, targetObjectives.length);
            } else {
                for (int i = 0; i < targetObjectives.length; i++) {
                    if (!Double.isFinite(targetObjectives[i])) {
                        targetObjectives[i] = 0.0;
                    }
                }
            }
        }
        return solution;
    }

    /** Reinicia todos os contadores atômicos para reutilização limpa em testes controlados. */
    public void resetCounters() {
        attemptedEvaluations.set(0L);
        actualProblemEvaluations.set(0L);
        suppressedOvershootEvaluations.set(0L);
        evaluatorBatchCalls.set(0);
        lastValidObjectives.set(null);
        lastValidBatchSnapshot.set(Collections.emptyList());
    }

    /** Retorna o problema {@link DoubleProblem} subjacente. */
    public DoubleProblem getDelegate() {
        return delegate;
    }

    /** Retorna o orçamento máximo configurado (\(B_{\text{target}}\)). */
    public long getMaxBudget() {
        return maxBudget;
    }

    /** Indica se o modo de imposição estrita de orçamento está ativo. */
    public boolean isEnforceHardBudget() {
        return enforceHardBudget;
    }

    /**
     * Retorna o número total de chamadas a {@link #evaluate(DoubleSolution)} tentadas pelo
     * algoritmo (incluindo eventuais chamadas suprimidas no modo estrito).
     */
    public long getAttemptedEvaluations() {
        return attemptedEvaluations.get();
    }

    /**
     * Retorna o número exato de chamadas efetivamente repassadas a {@code delegate.evaluate(...)}.
     * No modo auditoria, coincide com {@link #getAttemptedEvaluations()}; no modo estrito, é
     * garantidamente \(\le \text{maxBudget}\).
     */
    public long getActualProblemEvaluations() {
        return actualProblemEvaluations.get();
    }

    /**
     * Alias semântico para {@link #getActualProblemEvaluations()} (total de avaliações reais da
     * função objetivo).
     */
    public long getEvaluationCount() {
        return actualProblemEvaluations.get();
    }

    /**
     * Retorna a quantidade de avaliações acima de {@code maxBudget} (tentadas no modo auditoria ou
     * suprimidas no modo estrito).
     */
    public long getOvershootEvaluations() {
        long attempted = attemptedEvaluations.get();
        return Math.max(0L, attempted - maxBudget);
    }

    /** Indica se o algoritmo tentou avaliar mais soluções do que {@code maxBudget}. */
    public boolean hasOvershoot() {
        return attemptedEvaluations.get() > maxBudget;
    }

    /** Retorna o número de lotes avaliados via avaliador de lista/componente. */
    public int getEvaluatorBatchCalls() {
        return evaluatorBatchCalls.get();
    }

    /** Retorna cópia imutável do último lote avaliado dentro do orçamento. */
    public List<DoubleSolution> getLastValidBatchSnapshot() {
        return lastValidBatchSnapshot.get();
    }

    /**
     * Retorna quantas avaliações ainda restam no orçamento (\(\max(0, B_{\text{target}} -
     * B_{\text{real}})\)).
     */
    public long getRemainingBudget() {
        return Math.max(0L, maxBudget - actualProblemEvaluations.get());
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
    public DoubleSolution createSolution() {
        return delegate.createSolution();
    }

    @Override
    public List<Bounds<Double>> variableBounds() {
        return delegate.variableBounds();
    }

    /**
     * Cria um {@link SolutionListEvaluator} compatível com {@code NSGAIIIBuilder},
     * {@code AGEMOEABuilder} e {@code AGEMOEAIIBuilder}.
     *
     * <p>Quando {@code enforceHardBudget == true}, avalia apenas as soluções do lote até atingir
     * {@code maxBudget} e trunca a lista de descendentes retornada ao algoritmo para conter apenas
     * os filhos efetivamente avaliados dentro do orçamento (evitando seleção ambiental sobre
     * indivíduos não avaliados).
     */
    public SolutionListEvaluator<DoubleSolution> createSolutionListEvaluator() {
        return new BudgetContractEvaluator();
    }

    /**
     * Cria um componente {@link Evaluation} compatível com {@code RVEABuilder.setEvaluation(...)}.
     *
     * <p>Quando {@code enforceHardBudget == true}, avalia no máximo as soluções necessárias para
     * completar {@code maxBudget}, truncando o lote final parcial caso restem menos avaliações do
     * que o tamanho do lote.
     */
    public Evaluation<DoubleSolution> createComponentEvaluation() {
        return new BudgetComponentEvaluation();
    }

    /**
     * Avaliador de listas de soluções consciente do orçamento para algoritmos baseados em
     * {@code AbstractGeneticAlgorithm} ({@code NSGA-III}, {@code AGE-MOEA}, {@code AGE-MOEA-II}).
     */
    public final class BudgetContractEvaluator implements SolutionListEvaluator<DoubleSolution> {

        private static final long serialVersionUID = 1L;

        @Override
        public List<DoubleSolution> evaluate(
                List<DoubleSolution> solutionList, Problem<DoubleSolution> problem) {
            Objects.requireNonNull(solutionList, "A lista de soluções não pode ser nula.");
            evaluatorBatchCalls.incrementAndGet();

            if (!enforceHardBudget) {
                for (DoubleSolution solution : solutionList) {
                    EvaluationBudgetProblem.this.evaluate(solution);
                }
                recordValidSnapshot(solutionList);
                return solutionList;
            }

            List<DoubleSolution> evaluatedWithinBudget = new ArrayList<>(solutionList.size());
            for (DoubleSolution solution : solutionList) {
                if (getRemainingBudget() <= 0L) {
                    // Registra tentativa excedente sem avaliar no problema real e descarta do lote
                    attemptedEvaluations.incrementAndGet();
                    suppressedOvershootEvaluations.incrementAndGet();
                } else {
                    EvaluationBudgetProblem.this.evaluate(solution);
                    evaluatedWithinBudget.add(solution);
                }
            }
            if (!evaluatedWithinBudget.isEmpty()) {
                recordValidSnapshot(evaluatedWithinBudget);
                solutionList.clear();
                solutionList.addAll(evaluatedWithinBudget);
            }
            return solutionList;
        }

        @Override
        public void shutdown() {
            // No-op para avaliação sequencial
        }
    }

    /**
     * Componente {@link Evaluation} consciente do orçamento para algoritmos do módulo
     * {@code jmetal-component} ({@code RVEA}).
     */
    public final class BudgetComponentEvaluation implements Evaluation<DoubleSolution> {

        private int computedEvaluations = 0;

        @Override
        public List<DoubleSolution> evaluate(List<DoubleSolution> solutionList) {
            Objects.requireNonNull(solutionList, "A lista de soluções não pode ser nula.");
            evaluatorBatchCalls.incrementAndGet();

            if (!enforceHardBudget) {
                for (DoubleSolution solution : solutionList) {
                    EvaluationBudgetProblem.this.evaluate(solution);
                }
                computedEvaluations = solutionList.size();
                recordValidSnapshot(solutionList);
                return solutionList;
            }

            List<DoubleSolution> evaluatedWithinBudget = new ArrayList<>(solutionList.size());
            for (DoubleSolution solution : solutionList) {
                if (getRemainingBudget() <= 0L) {
                    attemptedEvaluations.incrementAndGet();
                    suppressedOvershootEvaluations.incrementAndGet();
                } else {
                    EvaluationBudgetProblem.this.evaluate(solution);
                    evaluatedWithinBudget.add(solution);
                }
            }
            computedEvaluations = evaluatedWithinBudget.size();
            if (!evaluatedWithinBudget.isEmpty()) {
                recordValidSnapshot(evaluatedWithinBudget);
                solutionList.clear();
                solutionList.addAll(evaluatedWithinBudget);
            }
            return solutionList;
        }

        @Override
        public int computedEvaluations() {
            return computedEvaluations;
        }

        @Override
        public Problem<DoubleSolution> problem() {
            return EvaluationBudgetProblem.this;
        }
    }

    private void recordValidSnapshot(List<DoubleSolution> solutions) {
        List<DoubleSolution> copy = new ArrayList<>(solutions.size());
        for (DoubleSolution s : solutions) {
            copy.add((DoubleSolution) s.copy());
        }
        lastValidBatchSnapshot.set(Collections.unmodifiableList(copy));
    }

    // =========================================================================
    // MÉTODOS UTILITÁRIOS DE CÁLCULO DE POPULAÇÃO E ORÇAMENTO (CONTRATO T13)
    // =========================================================================

    /**
     * Retorna o tamanho de população configurado canonicamente (\(N_{\text{config}}(m)\)) em
     * {@link StaticParams#getPopulationSize(int)}.
     *
     * @param m número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
     * @return \(N_{\text{config}}(m) \in \{100, 91, 210, 156, 275, 135\}\)
     */
    public static int getConfiguredPopulationSize(int m) {
        validateSupportedObjectives(m);
        return StaticParams.getPopulationSize(m);
    }

    /**
     * Retorna o tamanho real da população interna alocada (\(N_{\text{alloc}}(\text{alg}, m)\)) por
     * cada um dos 6 algoritmos principais no estado atual de {@code WFGStudy.java}:
     *
     * <ul>
     *   <li>{@code NSGA-III}: arredonda o número de pontos de referência para o próximo múltiplo de
     *       4 em {@code NSGAIII.<init>} ({@code while (popSize % 4 > 0) popSize++}) \(\implies
     *       \{100, 92, 212, 156, 276, 136\}\);
     *   <li>{@code AGE-MOEA} e {@code AGE-MOEA-II}: incrementam \(N\) ímpar em
     *       {@code WFGStudy.buildAGEMOEA/buildAGE2} ({@code if (populationSize % 2 != 0)
     *       populationSize++}) \(\implies \{100, 92, 210, 156, 276, 136\}\);
     *   <li>{@code MOEA/DD}, {@code FDEA} e {@code RVEA}: preservam exatamente \(N_{\text{config}}(m)
     *       \in \{100, 91, 210, 156, 275, 135\}\).
     * </ul>
     *
     * @param algorithmName nome canônico do algoritmo
     * @param m número de objetivos
     * @return população interna alocada \(N_{\text{alloc}}(\text{alg}, m)\)
     */
    public static int getAllocatedPopulationSize(String algorithmName, int m) {
        validateSupportedObjectives(m);
        String canonical = normalizeAlgorithmName(algorithmName);
        int nConfig = StaticParams.getPopulationSize(m);
        return switch (canonical) {
            case "NSGA-III" -> roundUpToMultipleOfFour(nConfig);
            case "AGE-MOEA", "AGE-MOEA-II" -> (nConfig % 2 != 0) ? (nConfig + 1) : nConfig;
            case "MOEA/DD", "FDEA", "RVEA" -> nConfig;
            default -> throw new IllegalArgumentException(
                    "Algoritmo não suportado: " + algorithmName);
        };
    }

    /**
     * Retorna o tamanho de lote de descendentes gerados e avaliados por geração completa em regime
     * estacionário (sem truncamento de fronteira) por algoritmo e \(m\).
     */
    public static int getOffspringBatchSize(String algorithmName, int m) {
        validateSupportedObjectives(m);
        String canonical = normalizeAlgorithmName(algorithmName);
        return switch (canonical) {
            case "MOEA/DD" -> 1; // steady-state: 1 filho avaliado por passo interno (N passos por permutação)
            case "NSGA-III", "AGE-MOEA", "AGE-MOEA-II", "FDEA", "RVEA" ->
                    getAllocatedPopulationSize(canonical, m);
            default -> throw new IllegalArgumentException(
                    "Algoritmo não suportado: " + algorithmName);
        };
    }

    /**
     * Retorna o orçamento alvo canônico \(B_{\text{target}}(m, \text{problem}) = N_{\text{config}}(m)
     * \times G_{\max}(m, \text{problem})\).
     *
     * @param m número de objetivos
     * @param problemName nome do problema (ex.: {@code "WFG1"}, {@code "DTLZ1"})
     * @return orçamento alvo \(B_{\text{target}}\)
     */
    public static int getTargetBudget(int m, String problemName) {
        validateSupportedObjectives(m);
        int nConfig = StaticParams.getPopulationSize(m);
        int gMax = StaticParams.getMaxGenerations(m, problemName);
        return nConfig * gMax;
    }

    /**
     * Calcula o número de gerações/iterações inteiras \(\lfloor B / N_{\text{alloc}} \rfloor\) que
     * cabem dentro de um orçamento alvo \(B\) sem excedê-lo quando cada geração (incluindo a
     * inicialização como iteração 1) consome \(N_{\text{alloc}}\) avaliações.
     *
     * @param targetBudget orçamento alvo \(B\)
     * @param effectivePopulationSize tamanho efetivo da população \(N_{\text{alloc}}\)
     * @return \(\max(1, \lfloor B / N_{\text{alloc}} \rfloor)\)
     */
    public static int computeMaxFullGenerationsWithinBudget(
            int targetBudget, int effectivePopulationSize) {
        if (targetBudget <= 0 || effectivePopulationSize <= 0) {
            throw new IllegalArgumentException(
                    "targetBudget e effectivePopulationSize devem ser positivos.");
        }
        return Math.max(1, targetBudget / effectivePopulationSize);
    }

    /**
     * Calcula o número de iterações (\(\lceil B / N_{\text{alloc}} \rceil\)) necessárias para que um
     * algoritmo baseado em {@code maxIterations} (onde {@code iterations} inicia em 1 após a
     * população inicial) alcance pelo menos {@code targetBudget} avaliações antes que o
     * {@link BudgetContractEvaluator} trunque o lote final parcial em exatamente {@code targetBudget}.
     *
     * @param targetBudget orçamento alvo \(B\)
     * @param effectivePopulationSize tamanho efetivo da população \(N_{\text{alloc}}\)
     * @return \(\max(1, \lceil B / N_{\text{alloc}} \rceil)\)
     */
    public static int computeExactGenerationsForBudget(
            int targetBudget, int effectivePopulationSize) {
        if (targetBudget <= 0 || effectivePopulationSize <= 0) {
            throw new IllegalArgumentException(
                    "targetBudget e effectivePopulationSize devem ser positivos.");
        }
        return Math.max(
                1, (targetBudget + effectivePopulationSize - 1) / effectivePopulationSize);
    }

    /**
     * Calcula matematicamente o total exato de chamadas a {@code Problem.evaluate} que cada
     * algoritmo realiza para um dado orçamento solicitado \(B_{\text{req}}\) e número de gerações
     * configurado \(G_{\text{req}}\) **sem** imposição externa de orçamento (comportamento nativo do
     * jMetal 7.8 + {@code WFGStudy.java} atual):
     *
     * <ul>
     *   <li>{@code NSGA-III}: avalia \(N_{\text{alloc}} = 4\lceil N_{\text{config}}/4 \rceil\) na
     *       inicialização ({@code iterations = 1}) e mais \(N_{\text{alloc}}\) por iteração até
     *       {@code iterations >= maxIterations}, resultando em \(N_{\text{alloc}} \times
     *       \max(1, G_{\text{req}})\).
     *   <li>{@code AGE-MOEA} e {@code AGE-MOEA-II}: avaliam \(N_{\text{alloc}} = 2\lceil
     *       N_{\text{config}}/2 \rceil\) na inicialização ({@code iterations = 1}) e mais
     *       \(N_{\text{alloc}}\) por iteração até {@code iterations >= maxIterations}, resultando em
     *       \(N_{\text{alloc}} \times \max(1, G_{\text{req}})\).
     *   <li>{@code MOEA/DD}: avalia \(N_{\text{config}}\) na inicialização e depois executa um laço
     *       {@code do-while (evaluations < maxEvaluations)} onde cada passo externo percorre uma
     *       permutação completa de \(N_{\text{config}}\) subproblemas (1 avaliação por subproblema)
     *       e só verifica {@code evaluations < maxEvaluations} ao final do lote de
     *       \(N_{\text{config}}\) subproblemas! Portanto, avalia sempre pelo menos \(2
     *       N_{\text{config}}\) soluções e arredonda qualquer \(B_{\text{req}} > 2 N_{\text{config}}\)
     *       para o próximo múltiplo de \(N_{\text{config}}\): \(\max(2 N_{\text{config}}, \lceil
     *       B_{\text{req}} / N_{\text{config}} \rceil \times N_{\text{config}})\).
     *   <li>{@code FDEA}: avalia \(N_{\text{config}}\) na inicialização e depois, enquanto
     *       {@code evaluations < maxEvaluations && generations < maxGenerations}, gera filhos
     *       individualmente verificando {@code evaluations < maxEvaluations} a cada filho; logo,
     *       realiza exatamente \(\max(N_{\text{config}}, \min(B_{\text{req}}, G_{\text{req}} \times
     *       N_{\text{config}}))\) avaliações.
     *   <li>{@code RVEA}: avalia \(N_{\text{config}}\) na inicialização ({@code evaluations = N}) e,
     *       enquanto {@code evaluations < maxEvaluations}, gera e avalia um lote inteiro de
     *       \(N_{\text{config}}\) filhos por geração antes de verificar a terminação; logo, realiza
     *       \(\max(N_{\text{config}}, \lceil B_{\text{req}} / N_{\text{config}} \rceil \times
     *       N_{\text{config}})\) avaliações.
     * </ul>
     *
     * @param algorithmName nome do algoritmo
     * @param m número de objetivos
     * @param requestedGenerations número de gerações configurado (\(G_{\text{req}}\))
     * @param requestedMaxEvaluations orçamento de avaliações configurado (\(B_{\text{req}}\))
     * @return número exato de chamadas a {@code Problem.evaluate} no estado nativo atual
     */
    public static int predictUnconstrainedEvaluations(
            String algorithmName, int m, int requestedGenerations, int requestedMaxEvaluations) {
        validateSupportedObjectives(m);
        String canonical = normalizeAlgorithmName(algorithmName);
        int nConfig = getConfiguredPopulationSize(m);
        int nAlloc = getAllocatedPopulationSize(canonical, m);
        int gEff = Math.max(1, requestedGenerations);

        return switch (canonical) {
            case "NSGA-III", "AGE-MOEA", "AGE-MOEA-II" -> nAlloc * gEff;
            case "MOEA/DD" -> {
                if (requestedMaxEvaluations <= 2 * nConfig) {
                    yield 2 * nConfig;
                }
                int batches = (requestedMaxEvaluations + nConfig - 1) / nConfig;
                yield batches * nConfig;
            }
            case "FDEA" -> {
                int capByGen = gEff * nConfig;
                int target = Math.min(requestedMaxEvaluations, capByGen);
                yield Math.max(nConfig, target);
            }
            case "RVEA" -> {
                if (requestedMaxEvaluations <= nConfig) {
                    yield nConfig;
                }
                int batches = (requestedMaxEvaluations + nConfig - 1) / nConfig;
                yield batches * nConfig;
            }
            default -> throw new IllegalArgumentException(
                    "Algoritmo não suportado: " + algorithmName);
        };
    }

    /**
     * Constrói uma instância de qualquer um dos 6 algoritmos principais reproduzindo fielmente a
     * configuração atual de {@link WFGStudy} (Modo Auditoria) ou aplicando o contrato estrito de
     * orçamento \(B_{\text{target}}\) (Modo Contrato Estrito).
     *
     * @param algorithmName nome do algoritmo ({@code "NSGA-III"}, {@code "MOEA/DD"}, {@code "FDEA"},
     *     {@code "RVEA"}, {@code "AGE-MOEA"}, {@code "AGE-MOEA-II"})
     * @param budgetProblem instância de {@link EvaluationBudgetProblem} envolvendo o problema real
     * @param requestedGenerations número de gerações desejado (ex.: {@code StaticParams.getMaxGenerations})
     * @param crossover operador de cruzamento
     * @param mutation operador de mutação
     * @return instância configurada de {@link Algorithm}
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Algorithm<List<DoubleSolution>> buildAuditedAlgorithm(
            String algorithmName,
            EvaluationBudgetProblem budgetProblem,
            int requestedGenerations,
            CrossoverOperator<DoubleSolution> crossover,
            MutationOperator<DoubleSolution> mutation) {
        Objects.requireNonNull(budgetProblem, "budgetProblem não pode ser nulo.");
        Objects.requireNonNull(crossover, "crossover não pode ser nulo.");
        Objects.requireNonNull(mutation, "mutation não pode ser nulo.");

        String canonical = normalizeAlgorithmName(algorithmName);
        int m = budgetProblem.numberOfObjectives();
        int nConfig = getConfiguredPopulationSize(m);
        int targetBudget = Math.toIntExact(budgetProblem.getMaxBudget());
        boolean strict = budgetProblem.isEnforceHardBudget();

        return switch (canonical) {
            case "NSGA-III" -> {
                int outerDivisions = StaticParams.getOuterDivisions(m);
                int innerDivisions = StaticParams.getInnerDivisions(m);
                int nAlloc = getAllocatedPopulationSize("NSGA-III", m);
                int iterations =
                        strict
                                ? computeExactGenerationsForBudget(targetBudget, nAlloc)
                                : Math.max(1, requestedGenerations);

                SelectionOperator selection =
                        new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>());
                NSGAIIIBuilder<DoubleSolution> builder =
                        new NSGAIIIBuilder<>(budgetProblem)
                                .setCrossoverOperator(crossover)
                                .setMutationOperator(mutation)
                                .setSelectionOperator(selection)
                                .setMaxIterations(iterations)
                                .setNumberOfDivisions(outerDivisions)
                                .setSolutionListEvaluator(budgetProblem.createSolutionListEvaluator());
                if (innerDivisions > 0) {
                    builder.setSecondLayerDivisions(innerDivisions);
                }
                yield builder.build();
            }
            case "AGE-MOEA" -> {
                int nAlloc = getAllocatedPopulationSize("AGE-MOEA", m);
                int iterations =
                        strict
                                ? computeExactGenerationsForBudget(targetBudget, nAlloc)
                                : Math.max(1, requestedGenerations);
                AGEMOEABuilder<DoubleSolution> builder = new AGEMOEABuilder<>(budgetProblem);
                builder.setMaxIterations(iterations);
                builder.setCrossoverOperator(crossover);
                builder.setMutationOperator(mutation);
                builder.setPopulationSize(nAlloc);
                builder.setSolutionListEvaluator(budgetProblem.createSolutionListEvaluator());
                yield builder.build();
            }
            case "AGE-MOEA-II" -> {
                int nAlloc = getAllocatedPopulationSize("AGE-MOEA-II", m);
                int iterations =
                        strict
                                ? computeExactGenerationsForBudget(targetBudget, nAlloc)
                                : Math.max(1, requestedGenerations);
                AGEMOEAIIBuilder<DoubleSolution> builder = new AGEMOEAIIBuilder<>(budgetProblem);
                builder.setMaxIterations(iterations);
                builder.setCrossoverOperator(crossover);
                builder.setMutationOperator(mutation);
                builder.setPopulationSize(nAlloc);
                builder.setSolutionListEvaluator(budgetProblem.createSolutionListEvaluator());
                yield builder.build();
            }
            case "MOEA/DD" -> {
                WFGStudy.validateMoeadWeightFile("MOEAD_Weights", budgetProblem, nConfig);
                MOEADBuilder builder =
                        new MOEADBuilder(budgetProblem, MOEADBuilder.Variant.MOEADD)
                                .setCrossover(crossover)
                                .setMutation(mutation)
                                .setMaxEvaluations(targetBudget)
                                .setPopulationSize(nConfig)
                                .setResultPopulationSize(nConfig)
                                .setNeighborhoodSelectionProbability(0.9)
                                .setMaximumNumberOfReplacedSolutions(2)
                                .setNeighborSize(20)
                                .setDataDirectory("MOEAD_Weights")
                                .setFunctionType(AbstractMOEAD.FunctionType.PBI);
                yield builder.build();
            }
            case "FDEA" -> {
                int generations =
                        strict
                                ? computeExactGenerationsForBudget(targetBudget, nConfig)
                                : Math.max(1, requestedGenerations);
                SelectionOperator selection =
                        new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>());
                yield new FDEABuilder(budgetProblem)
                        .setPopulationSize(nConfig)
                        .setMaxGenerations(generations)
                        .setMaxEvaluations(targetBudget)
                        .setCrossoverOperator(crossover)
                        .setMutationOperator(mutation)
                        .setSelectionOperator(selection)
                        .build();
            }
            case "RVEA" -> {
                int outerDivisions = StaticParams.getOuterDivisions(m);
                int innerDivisions = StaticParams.getInnerDivisions(m);
                List<double[]> referenceVectors =
                        (innerDivisions > 0)
                                ? ReferencePointGenerator.generateTwoLayers(
                                        m, outerDivisions, innerDivisions)
                                : ReferencePointGenerator.generateSingleLayer(m, outerDivisions);
                int rveaMaxEvals = Math.max(nConfig, targetBudget);
                RVEABuilder<DoubleSolution> builder =
                        new RVEABuilder<>(
                                budgetProblem,
                                referenceVectors.size(),
                                rveaMaxEvals,
                                crossover,
                                mutation,
                                2.0,
                                0.1,
                                referenceVectors);
                builder.setEvaluation(budgetProblem.createComponentEvaluation());
                yield builder.build();
            }
            default -> throw new IllegalArgumentException(
                    "Algoritmo não suportado: " + algorithmName);
        };
    }

    /**
     * Normaliza as diferentes grafias de nomes dos 6 algoritmos principais para o identificador
     * canônico usado nos relatórios da Fase 3.
     */
    public static String normalizeAlgorithmName(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) {
            throw new IllegalArgumentException("O nome do algoritmo não pode ser nulo ou vazio.");
        }
        String normalized = algorithmName.trim().toUpperCase().replace("_", "-");
        return switch (normalized) {
            case "NSGA-III", "NSGAIII" -> "NSGA-III";
            case "MOEA/DD", "MOEADD" -> "MOEA/DD";
            case "FDEA" -> "FDEA";
            case "RVEA" -> "RVEA";
            case "AGE-MOEA", "AGEMOEA" -> "AGE-MOEA";
            case "AGE-MOEA-II", "AGEMOEAII", "AGE2" -> "AGE-MOEA-II";
            default -> throw new IllegalArgumentException(
                    "Algoritmo desconhecido (esperado um dos 6 principais): " + algorithmName);
        };
    }

    private static int roundUpToMultipleOfFour(int value) {
        int r = value % 4;
        return (r == 0) ? value : (value + (4 - r));
    }

    private static void validateSupportedObjectives(int m) {
        for (int supported : CANONICAL_OBJECTIVES) {
            if (supported == m) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "Número de objetivos m não faz parte do protocolo {2,3,5,8,10,15}: m=" + m);
    }
}

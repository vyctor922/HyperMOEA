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
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADD;
import org.uma.jmetal.algorithm.multiobjective.moead.util.MOEADUtils;
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
import org.uma.jmetal.util.point.impl.IdealPoint;
import org.uma.jmetal.util.point.impl.NadirPoint;
import org.uma.jmetal.util.referencepoint.ReferencePointGenerator;

/**
 * Wrapper thread-safe sobre {@link DoubleProblem} para auditoria e imposição de
 * contrato de orçamento real de avaliações ({@link Problem#evaluate(Object)})
 * (T13).
 *
 * <p>
 * Contabiliza atomicamente cada chamada real a {@code evaluate(DoubleSolution)}
 * de forma
 * independente dos contadores internos dos algoritmos e suporta dois modos
 * operacionais:
 *
 * <ul>
 * <li><b>Modo Auditoria ({@code enforceHardBudget = false})</b>: executa 100%
 * das chamadas a
 * {@link #evaluate(DoubleSolution)} feitas pelo algoritmo até sua parada
 * natural,
 * contabilizando avaliações efetivas, excedentes
 * ({@code evaluations > maxBudget}) e a última
 * solução válida avaliada dentro do orçamento.
 * <li><b>Modo Contrato Estrito ({@code enforceHardBudget = true} +
 * {@link BudgetContractEvaluator})</b>:
 * impõe o teto exato {@code maxBudget} de avaliações reais ao problema
 * subjacente sem lançar
 * exceção arbitrária que corrompa o estado do algoritmo. Quando acoplado via
 * {@link #createSolutionListEvaluator()} (para {@code NSGA-III},
 * {@code AGE-MOEA},
 * {@code AGE-MOEA-II}) ou {@link #createComponentEvaluation()} (para
 * {@code RVEA}), trunca de
 * forma segura o lote final parcial para exatamente as soluções avaliadas
 * dentro do orçamento
 * restante. Caso uma chamada residual a {@link #evaluate(DoubleSolution)}
 * ocorra diretamente
 * após o esgotamento do orçamento, realiza fallback determinístico sem invocar
 * o problema
 * subjacente, registrando separadamente {@link #getActualProblemEvaluations()}
 * e
 * {@link #getAttemptedEvaluations()}.
 * </ul>
 */
public class EvaluationBudgetProblem implements DoubleProblem {

    private static final long serialVersionUID = 1L;

    /** Identificadores canônicos dos 6 algoritmos principais do estudo. */
    public static final List<String> MAIN_ALGORITHMS = List.of("NSGA-III", "MOEA/DD", "FDEA", "RVEA", "AGE-MOEA",
            "AGE-MOEA-II");

    /** Dimensões de objetivos canônicas do protocolo experimental. */
    public static final int[] CANONICAL_OBJECTIVES = { 2, 3, 5, 8, 10, 15 };

    private final DoubleProblem delegate;
    private final long maxBudget;
    private final boolean enforceHardBudget;

    private final AtomicLong reservedEvaluations = new AtomicLong(0L);
    private final AtomicLong attemptedEvaluations = new AtomicLong(0L);
    private final AtomicLong actualProblemEvaluations = new AtomicLong(0L);
    private final AtomicLong suppressedOvershootEvaluations = new AtomicLong(0L);
    private final AtomicInteger evaluatorBatchCalls = new AtomicInteger(0);
    private final AtomicReference<List<DoubleSolution>> lastValidBatchSnapshot = new AtomicReference<>(
            Collections.emptyList());
    private volatile java.util.function.IntConsumer preBatchEvaluationListener;

    /**
     * Registra um ouvinte passivo invocado no início de cada chamada de lote em
     * {@link BudgetContractEvaluator#evaluate} (recebendo o índice 1-based do
     * lote), utilizado
     * exclusivamente quando {@code -Dstudy.enableR2Observer=true} estiver
     * habilitado (opt-in) para
     * coletar a curva geracional de {@code R2} sobre a população sobrevivente sem
     * adicionar
     * avaliações de objetivos.
     */
    public void setPreBatchEvaluationListener(java.util.function.IntConsumer listener) {
        this.preBatchEvaluationListener = listener;
    }

    /**
     * Cria um wrapper em modo auditoria ({@code enforceHardBudget = false}) com
     * orçamento alvo
     * informado.
     *
     * @param delegate  problema {@link DoubleProblem} subjacente
     * @param maxBudget orçamento alvo \(B_{\text{target}}\) para auditoria de
     *                  excedente
     */
    public EvaluationBudgetProblem(DoubleProblem delegate, long maxBudget) {
        this(delegate, maxBudget, false);
    }

    /**
     * Cria um wrapper configurável sobre {@link DoubleProblem}.
     *
     * @param delegate          problema {@link DoubleProblem} subjacente (não nulo)
     * @param maxBudget         orçamento alvo \(B_{\text{target}} > 0\)
     * @param enforceHardBudget {@code true} para bloquear chamadas reais ao
     *                          problema subjacente
     *                          após atingir {@code maxBudget}; {@code false} para
     *                          apenas auditar todas as chamadas
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
     * Reserva atomicamente uma vaga no orçamento de avaliações antes de invocar o
     * problema
     * subjacente, garantindo segurança de threads mesmo sob avaliação paralela.
     *
     * @return {@code true} se a avaliação está autorizada dentro de
     *         {@code maxBudget};
     *         {@code false} se {@code enforceHardBudget == true} e
     *         {@code maxBudget} já foi atingido
     */
    private boolean tryReserveBudgetSlot() {
        while (true) {
            long current = reservedEvaluations.get();
            if (enforceHardBudget && current >= maxBudget) {
                return false;
            }
            if (reservedEvaluations.compareAndSet(current, current + 1L)) {
                return true;
            }
        }
    }

    /**
     * Tenta avaliar uma solução de lote dentro do saldo restante do orçamento.
     *
     * <p>
     * Se não houver saldo suficiente sob {@code enforceHardBudget == true}, não
     * invoca o
     * problema subjacente nem preenche objetivos fictícios/herdados, retornando
     * {@code false}
     * para que o avaliador descarte o candidato antes da seleção de sobreviventes.
     */
    private boolean tryEvaluateCandidateWithinBudget(DoubleSolution solution) {
        Objects.requireNonNull(solution, "A solução a avaliar não pode ser nula.");
        if (!tryReserveBudgetSlot()) {
            attemptedEvaluations.incrementAndGet();
            suppressedOvershootEvaluations.incrementAndGet();
            return false;
        }
        long attemptIndex = attemptedEvaluations.incrementAndGet();
        delegate.evaluate(solution);
        actualProblemEvaluations.incrementAndGet();
        if (!enforceHardBudget && attemptIndex > maxBudget) {
            suppressedOvershootEvaluations.incrementAndGet();
        }
        return true;
    }

    /**
     * Avalia uma solução contabilizando atomicamente a chamada.
     *
     * <p>
     * Conforme decisão de governança (T33 / A2), são estritamente proibidos
     * objetivos fictícios ou herdados/copiados de outros candidatos: quando
     * {@code enforceHardBudget == true}, o controle de lote parcial deve ocorrer no
     * fluxo de
     * reprodução/avaliação do algoritmo; qualquer invocação direta a
     * {@code evaluate(solution)}
     * após o esgotamento de {@code maxBudget} lança {@link IllegalStateException}.
     *
     * @param solution solução a avaliar
     * @return a própria solução avaliada pelo problema subjacente
     * @throws IllegalStateException se {@code enforceHardBudget == true} e
     *                               {@code maxBudget} já estiver esgotado
     */
    @Override
    public DoubleSolution evaluate(DoubleSolution solution) {
        Objects.requireNonNull(solution, "A solução a avaliar não pode ser nula.");
        if (!tryReserveBudgetSlot()) {
            attemptedEvaluations.incrementAndGet();
            suppressedOvershootEvaluations.incrementAndGet();
            throw new IllegalStateException(
                    "Violação do contrato estrito de orçamento: Problem.evaluate invocado após esgotamento de maxBudget="
                            + maxBudget
                            + " no problema "
                            + delegate.name());
        }
        long attemptIndex = attemptedEvaluations.incrementAndGet();
        DoubleSolution evaluated = delegate.evaluate(solution);
        actualProblemEvaluations.incrementAndGet();
        if (!enforceHardBudget && attemptIndex > maxBudget) {
            suppressedOvershootEvaluations.incrementAndGet();
        }
        return evaluated;
    }

    /**
     * Reinicia todos os contadores atômicos para reutilização limpa em testes
     * controlados.
     */
    public void resetCounters() {
        reservedEvaluations.set(0L);
        attemptedEvaluations.set(0L);
        actualProblemEvaluations.set(0L);
        suppressedOvershootEvaluations.set(0L);
        evaluatorBatchCalls.set(0);
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
     * Retorna o número total de chamadas a {@link #evaluate(DoubleSolution)}
     * tentadas pelo
     * algoritmo (incluindo eventuais chamadas suprimidas no modo estrito).
     */
    public long getAttemptedEvaluations() {
        return attemptedEvaluations.get();
    }

    /**
     * Retorna o número exato de chamadas efetivamente repassadas a
     * {@code delegate.evaluate(...)}.
     * No modo auditoria, coincide com {@link #getAttemptedEvaluations()}; no modo
     * estrito, é
     * garantidamente \(\le \text{maxBudget}\).
     */
    public long getActualProblemEvaluations() {
        return actualProblemEvaluations.get();
    }

    /**
     * Alias semântico para {@link #getActualProblemEvaluations()} (total de
     * avaliações reais da
     * função objetivo).
     */
    public long getEvaluationCount() {
        return actualProblemEvaluations.get();
    }

    /**
     * Retorna a quantidade de avaliações acima de {@code maxBudget} (tentadas no
     * modo auditoria ou
     * suprimidas no modo estrito).
     */
    public long getOvershootEvaluations() {
        long attempted = attemptedEvaluations.get();
        return Math.max(0L, attempted - maxBudget);
    }

    /**
     * Indica se o algoritmo tentou avaliar mais soluções do que {@code maxBudget}.
     */
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
     * Retorna quantas avaliações ainda restam no orçamento (\(\max(0,
     * B_{\text{target}} -
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
     * <p>
     * Quando {@code enforceHardBudget == true}, avalia apenas as soluções do lote
     * até atingir
     * {@code maxBudget} e trunca a lista de descendentes retornada ao algoritmo
     * para conter apenas
     * os filhos efetivamente avaliados dentro do orçamento (evitando seleção
     * ambiental sobre
     * indivíduos não avaliados).
     */
    public SolutionListEvaluator<DoubleSolution> createSolutionListEvaluator() {
        return new BudgetContractEvaluator();
    }

    /**
     * Cria um componente {@link Evaluation} compatível com
     * {@code RVEABuilder.setEvaluation(...)}.
     *
     * <p>
     * Quando {@code enforceHardBudget == true}, avalia no máximo as soluções
     * necessárias para
     * completar {@code maxBudget}, truncando o lote final parcial caso restem menos
     * avaliações do
     * que o tamanho do lote.
     */
    public Evaluation<DoubleSolution> createComponentEvaluation() {
        return new BudgetComponentEvaluation();
    }

    /**
     * Avaliador de listas de soluções consciente do orçamento para algoritmos
     * baseados em
     * {@code AbstractGeneticAlgorithm} ({@code NSGA-III}, {@code AGE-MOEA},
     * {@code AGE-MOEA-II}).
     */
    public final class BudgetContractEvaluator implements SolutionListEvaluator<DoubleSolution> {

        private static final long serialVersionUID = 1L;

        @Override
        public List<DoubleSolution> evaluate(
                List<DoubleSolution> solutionList, Problem<DoubleSolution> problem) {
            Objects.requireNonNull(solutionList, "A lista de soluções não pode ser nula.");
            int batchNo = evaluatorBatchCalls.incrementAndGet();
            java.util.function.IntConsumer preHook = preBatchEvaluationListener;
            if (preHook != null) {
                preHook.accept(batchNo);
            }

            if (!enforceHardBudget) {
                for (DoubleSolution solution : solutionList) {
                    EvaluationBudgetProblem.this.evaluate(solution);
                }
                recordValidSnapshot(solutionList);
                return solutionList;
            }

            List<DoubleSolution> evaluatedWithinBudget = new ArrayList<>(solutionList.size());
            for (DoubleSolution solution : solutionList) {
                if (tryEvaluateCandidateWithinBudget(solution)) {
                    evaluatedWithinBudget.add(solution);
                }
            }
            if (!evaluatedWithinBudget.isEmpty()) {
                recordValidSnapshot(evaluatedWithinBudget);
            }
            solutionList.clear();
            solutionList.addAll(evaluatedWithinBudget);
            return solutionList;
        }

        @Override
        public void shutdown() {
            // No-op para avaliação sequencial
        }
    }

    /**
     * Componente {@link Evaluation} consciente do orçamento para algoritmos do
     * módulo
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
                if (tryEvaluateCandidateWithinBudget(solution)) {
                    evaluatedWithinBudget.add(solution);
                }
            }
            computedEvaluations = evaluatedWithinBudget.size();
            if (!evaluatedWithinBudget.isEmpty()) {
                recordValidSnapshot(evaluatedWithinBudget);
            }
            solutionList.clear();
            solutionList.addAll(evaluatedWithinBudget);
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
     * Retorna o tamanho de população configurado canonicamente
     * (\(N_{\text{config}}(m)\)) em
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
     * Retorna o tamanho real da população interna alocada
     * (\(N_{\text{alloc}}(\text{alg}, m)\)) por
     * cada um dos 6 algoritmos principais no estado atual de {@code WFGStudy.java}:
     *
     * <ul>
     * <li>{@code NSGA-III}: arredonda o número de pontos de referência para o
     * próximo múltiplo de
     * 4 em {@code NSGAIII.<init>} ({@code while (popSize % 4 > 0) popSize++})
     * \(\implies
     * \{100, 92, 212, 156, 276, 136\}\);
     * <li>{@code AGE-MOEA} e {@code AGE-MOEA-II}: incrementam \(N\) ímpar em
     * {@code WFGStudy.buildAGEMOEA/buildAGE2} ({@code if (populationSize % 2 != 0)
     *       populationSize++}) \(\implies \{100, 92, 210, 156, 276, 136\}\);
     * <li>{@code MOEA/DD}, {@code FDEA} e {@code RVEA}: preservam exatamente
     * \(N_{\text{config}}(m)
     * \in \{100, 91, 210, 156, 275, 135\}\).
     * </ul>
     *
     * @param algorithmName nome canônico do algoritmo
     * @param m             número de objetivos
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
     * Retorna o tamanho de lote de descendentes gerados e avaliados por geração
     * completa em regime
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
     * Retorna o orçamento alvo canônico \(B_{\text{target}}(m, \text{problem}) =
     * N_{\text{config}}(m)
     * \times G_{\max}(m, \text{problem})\).
     *
     * @param m           número de objetivos
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
     * Calcula o número de gerações/iterações inteiras \(\lfloor B /
     * N_{\text{alloc}} \rfloor\) que
     * cabem dentro de um orçamento alvo \(B\) sem excedê-lo quando cada geração
     * (incluindo a
     * inicialização como iteração 1) consome \(N_{\text{alloc}}\) avaliações.
     *
     * @param targetBudget            orçamento alvo \(B\)
     * @param effectivePopulationSize tamanho efetivo da população
     *                                \(N_{\text{alloc}}\)
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
     * Calcula o número de iterações (\(\lceil B / N_{\text{alloc}} \rceil\))
     * necessárias para que um
     * algoritmo baseado em {@code maxIterations} (onde {@code iterations} inicia em
     * 1 após a
     * população inicial) alcance pelo menos {@code targetBudget} avaliações antes
     * que o
     * {@link BudgetContractEvaluator} trunque o lote final parcial em exatamente
     * {@code targetBudget}.
     *
     * @param targetBudget            orçamento alvo \(B\)
     * @param effectivePopulationSize tamanho efetivo da população
     *                                \(N_{\text{alloc}}\)
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
     * Calcula matematicamente o total exato de chamadas a {@code Problem.evaluate}
     * que cada
     * algoritmo realiza para um dado orçamento solicitado \(B_{\text{req}}\) e
     * número de gerações
     * configurado \(G_{\text{req}}\) **sem** imposição externa de orçamento
     * (comportamento nativo do
     * jMetal 7.8 + {@code WFGStudy.java} atual):
     *
     * <ul>
     * <li>{@code NSGA-III}: avalia \(N_{\text{alloc}} = 4\lceil N_{\text{config}}/4
     * \rceil\) na
     * inicialização ({@code iterations = 1}) e mais \(N_{\text{alloc}}\) por
     * iteração até
     * {@code iterations >= maxIterations}, resultando em \(N_{\text{alloc}} \times
     * \max(1, G_{\text{req}})\).
     * <li>{@code AGE-MOEA} e {@code AGE-MOEA-II}: avaliam \(N_{\text{alloc}} =
     * 2\lceil
     * N_{\text{config}}/2 \rceil\) na inicialização ({@code iterations = 1}) e mais
     * \(N_{\text{alloc}}\) por iteração até {@code iterations >= maxIterations},
     * resultando em
     * \(N_{\text{alloc}} \times \max(1, G_{\text{req}})\).
     * <li>{@code MOEA/DD}: avalia \(N_{\text{config}}\) na inicialização e depois
     * executa um laço
     * {@code do-while (evaluations < maxEvaluations)} onde cada passo externo
     * percorre uma
     * permutação completa de \(N_{\text{config}}\) subproblemas (1 avaliação por
     * subproblema)
     * e só verifica {@code evaluations < maxEvaluations} ao final do lote de
     * \(N_{\text{config}}\) subproblemas! Portanto, avalia sempre pelo menos \(2
     * N_{\text{config}}\) soluções e arredonda qualquer \(B_{\text{req}} > 2
     * N_{\text{config}}\)
     * para o próximo múltiplo de \(N_{\text{config}}\): \(\max(2 N_{\text{config}},
     * \lceil
     * B_{\text{req}} / N_{\text{config}} \rceil \times N_{\text{config}})\).
     * <li>{@code FDEA}: avalia \(N_{\text{config}}\) na inicialização e depois,
     * enquanto
     * {@code evaluations < maxEvaluations && generations < maxGenerations}, gera
     * filhos
     * individualmente verificando {@code evaluations < maxEvaluations} a cada
     * filho; logo,
     * realiza exatamente \(\max(N_{\text{config}}, \min(B_{\text{req}},
     * G_{\text{req}} \times
     * N_{\text{config}}))\) avaliações.
     * <li>{@code RVEA}: avalia \(N_{\text{config}}\) na inicialização
     * ({@code evaluations = N}) e,
     * enquanto {@code evaluations < maxEvaluations}, gera e avalia um lote inteiro
     * de
     * \(N_{\text{config}}\) filhos por geração antes de verificar a terminação;
     * logo, realiza
     * \(\max(N_{\text{config}}, \lceil B_{\text{req}} / N_{\text{config}} \rceil
     * \times
     * N_{\text{config}})\) avaliações.
     * </ul>
     *
     * @param algorithmName           nome do algoritmo
     * @param m                       número de objetivos
     * @param requestedGenerations    número de gerações configurado
     *                                (\(G_{\text{req}}\))
     * @param requestedMaxEvaluations orçamento de avaliações configurado
     *                                (\(B_{\text{req}}\))
     * @return número exato de chamadas a {@code Problem.evaluate} no estado nativo
     *         atual
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
     * Valida explicitamente que o orçamento alvo \(B\) comporta a inicialização
     * completa da
     * população interna \(N_{\text{alloc}}(\text{alg}, m)\) exigida pelo algoritmo
     * (T33 / A2).
     *
     * @param algorithmName identificador do algoritmo
     * @param m             número de objetivos
     * @param targetBudget  orçamento máximo de avaliações \(B\)
     * @throws IllegalArgumentException se \(B < N_{\text{alloc}}(\text{alg}, m)\)
     */
    public static void validateMinimumInitializationBudget(
            String algorithmName, int m, long targetBudget) {
        String canonical = normalizeAlgorithmName(algorithmName);
        int nAlloc = getAllocatedPopulationSize(canonical, m);
        if (targetBudget < nAlloc) {
            throw new IllegalArgumentException(
                    String.format(
                            "Orçamento B=%d insuficiente para inicialização válida de %s em m=%d (exige pelo menos N_alloc=%d avaliações).",
                            targetBudget, canonical, m, nAlloc));
        }
    }

    /**
     * Adaptador steady-state do {@link MOEADD} (T33 / A2) que
     * preserva integralmente as estruturas internas de arquivo, ranking,
     * sub-regiões, pontos ideal
     * e nadir do {@code MOEADD} no jMetal 7.8, substituindo apenas o laço
     * {@code do-while}
     * pós-verificado em lote por verificação prévia e passo a passo
     * {@code evaluations < maxEvaluations}.
     *
     * <p>
     * Garante que:
     * <ul>
     * <li>Para \(B = N_{\text{alloc}}\), o algoritmo encerra imediatamente após a
     * inicialização
     * completa sem executar um segundo lote obrigatório;</li>
     * <li>Para \(B = k N_{\text{alloc}} + r\) (\(1 \le r < N_{\text{alloc}}\)),
     * apenas \(r\)
     * descendentes são gerados, avaliados e integrados via {@code updateArchive} na
     * última
     * permutação;</li>
     * <li>Para \(B = G N_{\text{alloc}}\) com \(G \ge 2\) (todas as configurações
     * canônicas da
     * campanha), a sequência de chamadas ao RNG e transições de estado é idêntica
     * bit a bit
     * ao {@link MOEADD} padrão do jMetal 7.8.</li>
     * </ul>
     */
    public static final class BudgetCompliantMOEADD extends MOEADD<DoubleSolution> {

        private static final long serialVersionUID = 1L;
        private java.util.function.BiConsumer<Long, List<DoubleSolution>> epochPopulationListener;

        public void setEpochPopulationListener(
                java.util.function.BiConsumer<Long, List<DoubleSolution>> listener) {
            this.epochPopulationListener = listener;
        }

        public BudgetCompliantMOEADD(
                Problem<DoubleSolution> problem,
                int populationSize,
                int resultPopulationSize,
                int maxEvaluations,
                CrossoverOperator<DoubleSolution> crossover,
                MutationOperator<DoubleSolution> mutation,
                AbstractMOEAD.FunctionType functionType,
                String dataDirectory,
                double neighborhoodSelectionProbability,
                int maximumNumberOfReplacedSolutions,
                int neighborSize) {
            super(
                    problem,
                    populationSize,
                    resultPopulationSize,
                    maxEvaluations,
                    crossover,
                    mutation,
                    functionType,
                    dataDirectory,
                    neighborhoodSelectionProbability,
                    maximumNumberOfReplacedSolutions,
                    neighborSize);
        }

        @Override
        public void run() {
            evaluations = 0;
            population = new ArrayList<>(populationSize);
            neighborhood = new int[populationSize][neighborSize];
            lambda = new double[populationSize][problem.numberOfObjectives()];
            idealPoint = new IdealPoint(problem.numberOfObjectives());
            nadirPoint = new NadirPoint(problem.numberOfObjectives());
            rankIdx = new int[populationSize][populationSize];
            subregionIdx = new int[populationSize][populationSize];
            subregionDist = new double[populationSize][populationSize];

            initializeUniformWeight();
            initializeNeighborhood();
            initPopulation();

            idealPoint.update(population);
            nadirPoint.update(population);

            for (int i = 0; i < populationSize; i++) {
                double distance = calculateDistance2(
                        population.get(i),
                        lambda[i],
                        idealPoint.values(),
                        nadirPoint.values());
                subregionDist[i][i] = distance;
            }

            ranking = computeRanking(population);
            for (int curRank = 0; curRank < ranking.getNumberOfSubFronts(); curRank++) {
                List<DoubleSolution> front = ranking.getSubFront(curRank);
                for (DoubleSolution s : front) {
                    int position = population.indexOf(s);
                    rankIdx[curRank][position] = 1;
                }
            }
            if (epochPopulationListener != null) {
                epochPopulationListener.accept((long) evaluations, population);
            }

            while (evaluations < maxEvaluations) {
                int[] permutation = new int[populationSize];
                MOEADUtils.randomPermutation(permutation, populationSize);
                for (int i = 0; i < populationSize && evaluations < maxEvaluations; i++) {
                    int cid = permutation[i];
                    int type = (randomGenerator.nextDouble() < neighborhoodSelectionProbability)
                            ? 1
                            : 2;
                    List<DoubleSolution> parents = matingSelection(cid, type);
                    List<DoubleSolution> children = crossoverOperator.execute(parents);
                    DoubleSolution child = children.get(0);
                    mutationOperator.execute(child);
                    problem.evaluate(child);
                    evaluations++;
                    idealPoint.update(child.objectives());
                    nadirPoint.update(child.objectives());
                    updateArchive(child);
                }
                if (epochPopulationListener != null) {
                    epochPopulationListener.accept((long) evaluations, population);
                }
            }
        }
    }

    /**
     * Constrói uma instância de qualquer um dos 6 algoritmos principais
     * reproduzindo fielmente a
     * configuração atual de {@link WFGStudy} (Modo Auditoria) ou aplicando o
     * contrato estrito de
     * orçamento \(B_{\text{target}}\) (Modo Contrato Estrito).
     */
    public static Algorithm<List<DoubleSolution>> buildAuditedAlgorithm(
            String algorithmName,
            EvaluationBudgetProblem budgetProblem,
            int requestedGenerations,
            CrossoverOperator<DoubleSolution> crossover,
            MutationOperator<DoubleSolution> mutation) {
        return buildAuditedAlgorithm(
                algorithmName, budgetProblem, requestedGenerations, crossover, mutation, null);
    }

    /**
     * Sobrecarga de {@link #buildAuditedAlgorithm} que aceita diretório de pesos do
     * {@code MOEA/DD}
     * previamente resolvido no preflight/contexto do estudo, evitando re-resolução
     * repetida de
     * artefatos durante a execução dos jobs (T33 / T34).
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Algorithm<List<DoubleSolution>> buildAuditedAlgorithm(
            String algorithmName,
            EvaluationBudgetProblem budgetProblem,
            int requestedGenerations,
            CrossoverOperator<DoubleSolution> crossover,
            MutationOperator<DoubleSolution> mutation,
            String preResolvedMoeadWeightsDir) {
        Objects.requireNonNull(budgetProblem, "budgetProblem não pode ser nulo.");
        Objects.requireNonNull(crossover, "crossover não pode ser nulo.");
        Objects.requireNonNull(mutation, "mutation não pode ser nulo.");

        String canonical = normalizeAlgorithmName(algorithmName);
        int m = budgetProblem.numberOfObjectives();
        int nConfig = getConfiguredPopulationSize(m);
        int targetBudget = Math.toIntExact(budgetProblem.getMaxBudget());
        boolean strict = budgetProblem.isEnforceHardBudget();
        if (strict) {
            validateMinimumInitializationBudget(canonical, m, targetBudget);
        }

        return switch (canonical) {
            case "NSGA-III" -> {
                int outerDivisions = StaticParams.getOuterDivisions(m);
                int innerDivisions = StaticParams.getInnerDivisions(m);
                int nAlloc = getAllocatedPopulationSize("NSGA-III", m);
                int iterations = strict
                        ? computeExactGenerationsForBudget(targetBudget, nAlloc)
                        : Math.max(1, requestedGenerations);

                SelectionOperator selection = new BinaryTournamentSelection<>(
                        new RankingAndCrowdingDistanceComparator<>());
                NSGAIIIBuilder<DoubleSolution> builder = new NSGAIIIBuilder<>(budgetProblem)
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
                int iterations = strict
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
                int iterations = strict
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
                String effectiveDataDir;
                if (preResolvedMoeadWeightsDir != null && !preResolvedMoeadWeightsDir.isBlank()) {
                    effectiveDataDir = preResolvedMoeadWeightsDir;
                } else {
                    effectiveDataDir = WFGStudy.resolveMoeadDataDirectory(
                            WFGStudy.getWeightsDirectory(), budgetProblem.numberOfObjectives(), nConfig, "MOEADD");
                    WFGStudy.validateMoeadWeightFile(effectiveDataDir, budgetProblem, nConfig, "MOEADD");
                }
                if (strict) {
                    yield new BudgetCompliantMOEADD(
                            budgetProblem,
                            nConfig,
                            nConfig,
                            targetBudget,
                            crossover,
                            mutation,
                            AbstractMOEAD.FunctionType.PBI,
                            effectiveDataDir,
                            0.9,
                            2,
                            20);
                }
                MOEADBuilder builder = new MOEADBuilder(budgetProblem, MOEADBuilder.Variant.MOEADD)
                        .setCrossover(crossover)
                        .setMutation(mutation)
                        .setMaxEvaluations(targetBudget)
                        .setPopulationSize(nConfig)
                        .setResultPopulationSize(nConfig)
                        .setNeighborhoodSelectionProbability(0.9)
                        .setMaximumNumberOfReplacedSolutions(2)
                        .setNeighborSize(20)
                        .setDataDirectory(effectiveDataDir)
                        .setFunctionType(AbstractMOEAD.FunctionType.PBI);
                yield builder.build();
            }
            case "FDEA" -> {
                int generations = strict
                        ? computeExactGenerationsForBudget(targetBudget, nConfig)
                        : Math.max(1, requestedGenerations);
                SelectionOperator selection = new BinaryTournamentSelection<>(
                        new RankingAndCrowdingDistanceComparator<>());
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
                List<double[]> referenceVectors = (innerDivisions > 0)
                        ? ReferencePointGenerator.generateTwoLayers(
                                m, outerDivisions, innerDivisions)
                        : ReferencePointGenerator.generateSingleLayer(m, outerDivisions);
                int rveaMaxEvals = strict ? targetBudget : Math.max(nConfig, targetBudget);
                RVEABuilder<DoubleSolution> builder = new RVEABuilder<>(
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
     * Retorna a tag de diretório/relatório usada pelos componentes {@code jMetal}
     * ({@code algorithm.name()}) correspondente ao algoritmo informado.
     */
    public static String toJMetalAlgorithmTag(String algorithmName) {
        return switch (normalizeAlgorithmName(algorithmName)) {
            case "NSGA-III" -> "NSGAIII";
            case "MOEA/DD" -> "MOEADD";
            case "FDEA" -> "FDEA";
            case "RVEA" -> "RVEA";
            case "AGE-MOEA" -> "AGE-MOEA";
            case "AGE-MOEA-II" -> "AGE-MOEA-II";
            default -> throw new IllegalArgumentException("Algoritmo não suportado: " + algorithmName);
        };
    }

    /**
     * Normaliza as diferentes grafias de nomes dos 6 algoritmos principais.
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

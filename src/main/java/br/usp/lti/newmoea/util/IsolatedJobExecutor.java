package br.usp.lti.newmoea.util;

import br.usp.lti.newmoea.study.StaticParams;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ3;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ4;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ5;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ6;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ7;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.problem.multiobjective.wfg.WFG2;
import org.uma.jmetal.problem.multiobjective.wfg.WFG3;
import org.uma.jmetal.problem.multiobjective.wfg.WFG4;
import org.uma.jmetal.problem.multiobjective.wfg.WFG5;
import org.uma.jmetal.problem.multiobjective.wfg.WFG6;
import org.uma.jmetal.problem.multiobjective.wfg.WFG7;
import org.uma.jmetal.problem.multiobjective.wfg.WFG8;
import org.uma.jmetal.problem.multiobjective.wfg.WFG9;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.SolutionListUtils;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

/**
 * Executor determinístico, lazy e estritamente isolado para jobs experimentais do estudo
 * {@code hypeMOEA} (Fase 3 — T15: Auditoria de Isolamento de Processos, Sementes e Estado
 * Compartilhado).
 *
 * <p>Resolve as três falhas estruturais identificadas na arquitetura padrão de {@code WFGStudy} e
 * {@code ExecuteAlgorithms} do jMetal 7.8:
 *
 * <ol>
 *   <li><b>Corrida de dados sobre o singleton global {@link JMetalRandom#getInstance()}</b>:
 *       serializa o acesso a {@link JMetalRandom} na JVM via {@link #JMETAL_RANDOM_LOCK} (ou isola
 *       via processo filho dedicado) e configura explicitamente {@code JMetalRandom.getInstance().setSeed(effectiveSeed)}
 *       imediatamente antes da instanciação e execução de cada job;
 *   <li><b>Derivação determinística de semente por tupla</b>: calcula {@code effectiveSeed}
 *       exclusivamente como função pura da tupla canônica {@code (baseSeed, problemName, m,
 *       algorithmName, runId)} via SHA-256 + avalanching SplitMix64, garantindo 100% de
 *       independência em relação à ordem de execução dos jobs;
 *   <li><b>Instanciação sob demanda (lazy) sem estado mutável compartilhado</b>: cria instâncias
 *       novas e exclusivas de {@link DoubleProblem}, {@link EvaluationBudgetProblem},
 *       {@link SBXCrossover}, {@link PolynomialMutation} e {@link Algorithm} apenas no momento de
 *       executar o job e descarta as referências ao término, eliminando a retenção simultânea de
 *       dezenas de milhares de algoritmos na memória heap.
 * </ol>
 */
public final class IsolatedJobExecutor {

    /** Semente base canônica padrão do protocolo experimental auditado. */
    public static final long DEFAULT_BASE_SEED = 20260409L;

    /** Probabilidade de cruzamento SBX padrão do estudo ({@code WFGStudy}). */
    public static final double DEFAULT_CROSSOVER_PROBABILITY = 1.0;

    /** Índice de distribuição de cruzamento SBX padrão do estudo ({@code WFGStudy}). */
    public static final double DEFAULT_CROSSOVER_DISTRIBUTION_INDEX = 30.0;

    /** Índice de distribuição de mutação polinomial padrão do estudo ({@code WFGStudy}). */
    public static final double DEFAULT_MUTATION_DISTRIBUTION_INDEX = 20.0;

    /** Número total de variáveis dos problemas DTLZ no estudo ({@code WFGStudy}). */
    public static final int DEFAULT_DTLZ_VARIABLES = 20;

    /** Número de variáveis de distância (\(l\)) dos problemas WFG no estudo ({@code WFGStudy}). */
    public static final int DEFAULT_WFG_DISTANCE_VARIABLES = 20;

    /** Cabeçalho canônico de 16 colunas do manifesto CSV de reprodutibilidade (T15). */
    public static final String MANIFEST_CSV_HEADER =
            "job_id,problem,m,algorithm,run_id,base_seed,effective_seed,n_config,max_generations,"
                    + "target_evaluations,actual_evaluations,raw_size,nd_size,"
                    + "objectives_sha256,variables_sha256,status";

    /**
     * Lock global intraprocesso que garante isolamento estrito de {@link JMetalRandom#getInstance()}
     * durante toda a janela {@code setSeed -> build -> run} de um job executado na mesma JVM.
     */
    private static final ReentrantLock JMETAL_RANDOM_LOCK = new ReentrantLock(true);

    private IsolatedJobExecutor() {
        // Classe utilitária estática
    }

    /**
     * Especificação imutável e autocontida de um job experimental individual.
     *
     * @param problemName identificador canônico do problema (ex.: {@code "WFG1"}, {@code "DTLZ2"})
     * @param m número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
     * @param algorithmName identificador canônico do algoritmo ({@code "NSGA-III"},
     *     {@code "MOEA/DD"}, {@code "FDEA"}, {@code "RVEA"}, {@code "AGE-MOEA"},
     *     {@code "AGE-MOEA-II"})
     * @param runId índice da execução independente (\(\text{runId} \ge 0\))
     * @param baseSeed semente mestre do estudo experimental
     * @param effectiveSeed semente efetiva de 64 bits derivada deterministicamente da tupla
     * @param populationSize tamanho configurado da população (\(N_{\text{config}}(m)\))
     * @param maxGenerations número máximo de gerações configurado (\(G_{\max}\))
     * @param targetEvaluations orçamento alvo de avaliações (\(B_{\text{target}}\))
     * @param enforceHardBudget {@code true} para impor o teto estrito {@code targetEvaluations} via
     *     {@link EvaluationBudgetProblem}; {@code false} para modo auditoria
     */
    public record JobSpec(
            String problemName,
            int m,
            String algorithmName,
            int runId,
            long baseSeed,
            long effectiveSeed,
            int populationSize,
            int maxGenerations,
            int targetEvaluations,
            boolean enforceHardBudget) {

        public JobSpec {
            problemName = StaticParams.normalizeProblemId(problemName);
            algorithmName = EvaluationBudgetProblem.normalizeAlgorithmName(algorithmName);
            EvaluationBudgetProblem.getConfiguredPopulationSize(m);
            if (runId < 0) {
                throw new IllegalArgumentException("runId deve ser não-negativo: " + runId);
            }
            if (populationSize <= 0) {
                throw new IllegalArgumentException(
                        "populationSize deve ser positivo: " + populationSize);
            }
            if (maxGenerations <= 0) {
                throw new IllegalArgumentException(
                        "maxGenerations deve ser positivo: " + maxGenerations);
            }
            if (targetEvaluations <= 0) {
                throw new IllegalArgumentException(
                        "targetEvaluations deve ser positivo: " + targetEvaluations);
            }
        }

        /**
         * Cria uma {@link JobSpec} canônica com os parâmetros de população e gerações definidos em
         * {@link StaticParams} e semente derivada automaticamente via
         * {@link #computeDeterministicSeed(long, String, int, String, int)}.
         */
        public static JobSpec ofCanonical(
                String problemName,
                int m,
                String algorithmName,
                int runId,
                long baseSeed,
                boolean enforceHardBudget) {
            String normProblem = StaticParams.normalizeProblemId(problemName);
            String normAlg = EvaluationBudgetProblem.normalizeAlgorithmName(algorithmName);
            int nConfig = StaticParams.getPopulationSize(m);
            int maxGen = StaticParams.getMaxGenerations(m, normProblem);
            int targetEvals = nConfig * maxGen;
            long seed = computeDeterministicSeed(baseSeed, normProblem, m, normAlg, runId);
            return new JobSpec(
                    normProblem,
                    m,
                    normAlg,
                    runId,
                    baseSeed,
                    seed,
                    nConfig,
                    maxGen,
                    targetEvals,
                    enforceHardBudget);
        }

        /**
         * Cria uma {@link JobSpec} com número customizado de gerações e orçamento alvo (útil para
         * testes determinísticos rápidos de fumaça e auditoria), mantendo \(N_{\text{config}}(m)\)
         * e a derivação canônica de semente.
         */
        public static JobSpec ofCustomBudget(
                String problemName,
                int m,
                String algorithmName,
                int runId,
                long baseSeed,
                int maxGenerations,
                int targetEvaluations,
                boolean enforceHardBudget) {
            String normProblem = StaticParams.normalizeProblemId(problemName);
            String normAlg = EvaluationBudgetProblem.normalizeAlgorithmName(algorithmName);
            int nConfig = StaticParams.getPopulationSize(m);
            long seed = computeDeterministicSeed(baseSeed, normProblem, m, normAlg, runId);
            return new JobSpec(
                    normProblem,
                    m,
                    normAlg,
                    runId,
                    baseSeed,
                    seed,
                    nConfig,
                    maxGenerations,
                    targetEvaluations,
                    enforceHardBudget);
        }

        /**
         * Retorna o identificador textual canônico do job no formato
         * {@code <PROBLEM>_m<M>_<ALG>_run<RUN>}.
         */
        public String jobId() {
            String sanitizedAlg = algorithmName.replace("/", "");
            return String.format(
                    Locale.ROOT, "%s_m%d_%s_run%d", problemName, m, sanitizedAlg, runId);
        }
    }

    /**
     * Resultado imutável e auditável da execução isolada de um {@link JobSpec}.
     *
     * @param spec especificação original do job executado
     * @param actualEvaluations número real de avaliações da função objetivo contabilizadas
     * @param rawResultSize cardinalidade da população bruta retornada por {@code algorithm.result()}
     * @param nonDominatedResultSize cardinalidade da frente não-dominada extraída do resultado
     * @param wallTimeMs tempo de relógio decorrido em milissegundos
     * @param cpuTimeMs tempo de CPU da thread executora em milissegundos
     * @param rawObjectives matriz profunda de objetivos da população bruta ({@code rawResultSize x m})
     * @param rawVariables matriz profunda de variáveis de decisão da população bruta
     *     ({@code rawResultSize x nVars})
     * @param objectivesSha256 hash SHA-256 canônico (64 hex chars) dos bits IEEE 754 de
     *     {@code rawObjectives}
     * @param variablesSha256 hash SHA-256 canônico (64 hex chars) dos bits IEEE 754 de
     *     {@code rawVariables}
     * @param status status final da execução (ex.: {@code "SUCCESS"})
     */
    public record JobExecutionResult(
            JobSpec spec,
            int actualEvaluations,
            int rawResultSize,
            int nonDominatedResultSize,
            long wallTimeMs,
            long cpuTimeMs,
            double[][] rawObjectives,
            double[][] rawVariables,
            String objectivesSha256,
            String variablesSha256,
            String status) {

        public JobExecutionResult {
            Objects.requireNonNull(spec, "spec não pode ser nulo.");
            Objects.requireNonNull(rawObjectives, "rawObjectives não pode ser nulo.");
            Objects.requireNonNull(rawVariables, "rawVariables não pode ser nulo.");
            Objects.requireNonNull(objectivesSha256, "objectivesSha256 não pode ser nulo.");
            Objects.requireNonNull(variablesSha256, "variablesSha256 não pode ser nulo.");
            Objects.requireNonNull(status, "status não pode ser nulo.");
            rawObjectives = deepCopyMatrix(rawObjectives);
            rawVariables = deepCopyMatrix(rawVariables);
        }

        @Override
        public double[][] rawObjectives() {
            return deepCopyMatrix(rawObjectives);
        }

        @Override
        public double[][] rawVariables() {
            return deepCopyMatrix(rawVariables);
        }

        /** Converte este resultado em uma entrada de manifesto CSV {@link ManifestEntry}. */
        public ManifestEntry toManifestEntry() {
            return ManifestEntry.fromResult(this);
        }
    }

    /**
     * Registro imutável correspondente a uma linha do manifesto de reprodutibilidade CSV (T15).
     */
    public record ManifestEntry(
            String jobId,
            String problem,
            int m,
            String algorithm,
            int runId,
            long baseSeed,
            long effectiveSeed,
            int nConfig,
            int maxGenerations,
            int targetEvaluations,
            int actualEvaluations,
            int rawSize,
            int ndSize,
            String objectivesSha256,
            String variablesSha256,
            String status) {

        public ManifestEntry {
            Objects.requireNonNull(jobId, "jobId não pode ser nulo.");
            Objects.requireNonNull(problem, "problem não pode ser nulo.");
            Objects.requireNonNull(algorithm, "algorithm não pode ser nulo.");
            Objects.requireNonNull(objectivesSha256, "objectivesSha256 não pode ser nulo.");
            Objects.requireNonNull(variablesSha256, "variablesSha256 não pode ser nulo.");
            Objects.requireNonNull(status, "status não pode ser nulo.");
        }

        /** Cria um {@link ManifestEntry} a partir de um {@link JobExecutionResult}. */
        public static ManifestEntry fromResult(JobExecutionResult result) {
            Objects.requireNonNull(result, "result não pode ser nulo.");
            JobSpec s = result.spec();
            return new ManifestEntry(
                    s.jobId(),
                    s.problemName(),
                    s.m(),
                    s.algorithmName(),
                    s.runId(),
                    s.baseSeed(),
                    s.effectiveSeed(),
                    s.populationSize(),
                    s.maxGenerations(),
                    s.targetEvaluations(),
                    result.actualEvaluations(),
                    result.rawResultSize(),
                    result.nonDominatedResultSize(),
                    result.objectivesSha256(),
                    result.variablesSha256(),
                    result.status());
        }

        /** Serializa a entrada como linha CSV compatível com {@link #MANIFEST_CSV_HEADER}. */
        public String toCsvRow() {
            return String.format(
                    Locale.ROOT,
                    "%s,%s,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%s",
                    jobId,
                    problem,
                    m,
                    algorithm,
                    runId,
                    baseSeed,
                    effectiveSeed,
                    nConfig,
                    maxGenerations,
                    targetEvaluations,
                    actualEvaluations,
                    rawSize,
                    ndSize,
                    objectivesSha256,
                    variablesSha256,
                    status);
        }

        /** Reconstrói um {@link ManifestEntry} a partir de uma linha CSV do manifesto. */
        public static ManifestEntry fromCsvRow(String csvLine) {
            Objects.requireNonNull(csvLine, "csvLine não pode ser nula.");
            String[] parts = csvLine.trim().split(",", -1);
            if (parts.length != 16) {
                throw new IllegalArgumentException(
                        "Linha CSV inválida no manifesto (esperadas 16 colunas, encontradas "
                                + parts.length
                                + "): "
                                + csvLine);
            }
            return new ManifestEntry(
                    parts[0].trim(),
                    parts[1].trim(),
                    Integer.parseInt(parts[2].trim()),
                    parts[3].trim(),
                    Integer.parseInt(parts[4].trim()),
                    Long.parseLong(parts[5].trim()),
                    Long.parseLong(parts[6].trim()),
                    Integer.parseInt(parts[7].trim()),
                    Integer.parseInt(parts[8].trim()),
                    Integer.parseInt(parts[9].trim()),
                    Integer.parseInt(parts[10].trim()),
                    Integer.parseInt(parts[11].trim()),
                    Integer.parseInt(parts[12].trim()),
                    parts[13].trim(),
                    parts[14].trim(),
                    parts[15].trim());
        }
    }

    /**
     * Deriva deterministicamente a semente de 64 bits para uma tupla experimental
     * {@code (baseSeed, problemName, m, algorithmName, runId)}.
     *
     * <p>A derivação aplica:
     * <ol>
     *   <li>Normalização canônica de {@code problemName} ({@link StaticParams#normalizeProblemId})
     *       e {@code algorithmName} ({@link EvaluationBudgetProblem#normalizeAlgorithmName});
     *   <li>Codificação UTF-8 da string canônica sem ambiguidade de delimitador:
     *       {@code "hypeMOEA:v1|baseSeed=<B>|problem=<P>|m=<M>|algorithm=<A>|run=<R>"};
     *   <li>Resumo criptográfico SHA-256 (256 bits) e extração dos primeiros 64 bits em formato
     *       big-endian (\(H_{64}\)), combinados por XOR com {@code baseSeed} e finalizados pelo
     *       misturador bijetivo {@code SplitMix64} (Stafford Variant 13).
     * </ol>
     *
     * <p>Propriedades garantidas:
     * <ul>
     *   <li>100% independente da ordem de execução ou do histórico de jobs anteriores;
     *   <li>Invariante a variações superficiais de grafia (ex.: {@code "NSGAIII"} vs
     *       {@code "NSGA-III"}, {@code "wfg1"} vs {@code "WFG1"});
     *   <li>Efeito avalanche completo (~50% dos bits invertidos para qualquer alteração em
     *       {@code baseSeed}, {@code problemName}, {@code m}, {@code algorithmName} ou
     *       {@code runId}).
     * </ul>
     *
     * @param baseSeed semente mestre do estudo
     * @param problemName nome do problema
     * @param m número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
     * @param algorithmName nome do algoritmo
     * @param runId identificador da run independente (\(\text{runId} \ge 0\))
     * @return semente efetiva determinística de 64 bits
     */
    public static long computeDeterministicSeed(
            long baseSeed, String problemName, int m, String algorithmName, int runId) {
        String canonicalProblem = StaticParams.normalizeProblemId(problemName);
        String canonicalAlgorithm = EvaluationBudgetProblem.normalizeAlgorithmName(algorithmName);
        EvaluationBudgetProblem.getConfiguredPopulationSize(m);
        if (runId < 0) {
            throw new IllegalArgumentException("O índice da run (runId) não pode ser negativo: " + runId);
        }

        String canonicalTuple =
                String.format(
                        Locale.ROOT,
                        "hypeMOEA:v1|baseSeed=%d|problem=%s|m=%d|algorithm=%s|run=%d",
                        baseSeed,
                        canonicalProblem,
                        m,
                        canonicalAlgorithm,
                        runId);

        byte[] digest = sha256Bytes(canonicalTuple.getBytes(StandardCharsets.UTF_8));
        long high64 = 0L;
        for (int i = 0; i < 8; i++) {
            high64 = (high64 << 8) | (digest[i] & 0xFFL);
        }
        long low64 = 0L;
        for (int i = 8; i < 16; i++) {
            low64 = (low64 << 8) | (digest[i] & 0xFFL);
        }
        return splitMix64(high64 ^ Long.rotateLeft(low64, 31) ^ baseSeed);
    }

    /**
     * Instancia sob demanda (lazy) uma nova instância limpa de {@link DoubleProblem} para o
     * problema e dimensão de objetivos especificados, seguindo exatamente a parametrização do
     * protocolo {@code WFGStudy}:
     *
     * <ul>
     *   <li>{@code WFG1..WFG9}: \(k = 2(m - 1)\) variáveis de posição, \(l = 20\) variáveis de
     *       distância, \(m\) objetivos;
     *   <li>{@code DTLZ1..DTLZ7}: \(n = 20\) variáveis de decisão totais, \(m\) objetivos.
     * </ul>
     *
     * @param problemName nome do problema ({@code WFG1..WFG9} ou {@code DTLZ1..DTLZ7})
     * @param m número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
     * @return nova instância isolada de {@link DoubleProblem}
     */
    public static DoubleProblem createFreshProblemInstance(String problemName, int m) {
        EvaluationBudgetProblem.getConfiguredPopulationSize(m);
        String canonical = StaticParams.normalizeProblemId(problemName);
        int k = 2 * (m - 1);
        int l = DEFAULT_WFG_DISTANCE_VARIABLES;
        int nVarsDtlz = DEFAULT_DTLZ_VARIABLES;

        return switch (canonical) {
            case "WFG1" -> new WFG1(k, l, m);
            case "WFG2" -> new WFG2(k, l, m);
            case "WFG3" -> new WFG3(k, l, m);
            case "WFG4" -> new WFG4(k, l, m);
            case "WFG5" -> new WFG5(k, l, m);
            case "WFG6" -> new WFG6(k, l, m);
            case "WFG7" -> new WFG7(k, l, m);
            case "WFG8" -> new WFG8(k, l, m);
            case "WFG9" -> new WFG9(k, l, m);
            case "DTLZ1" -> new DTLZ1(nVarsDtlz, m);
            case "DTLZ2" -> new DTLZ2(nVarsDtlz, m);
            case "DTLZ3" -> new DTLZ3(nVarsDtlz, m);
            case "DTLZ4" -> new DTLZ4(nVarsDtlz, m);
            case "DTLZ5" -> new DTLZ5(nVarsDtlz, m);
            case "DTLZ6" -> new DTLZ6(nVarsDtlz, m);
            case "DTLZ7" -> new DTLZ7(nVarsDtlz, m);
            default -> throw new IllegalArgumentException(
                    "Problema não suportado na suíte WFG/DTLZ: " + problemName);
        };
    }

    /**
     * Instancia sob demanda (lazy) uma nova instância limpa do algoritmo e de todos os seus
     * operadores ({@link SBXCrossover}, {@link PolynomialMutation}, operadores de seleção e
     * avaliadores), sem compartilhar nenhum objeto mutável com outros jobs.
     *
     * <p><b>Importante</b>: este método deve ser chamado após configurar
     * {@code JMetalRandom.getInstance().setSeed(spec.effectiveSeed())}, pois construtores de
     * algoritmos/operadores no jMetal 7.8 vinculam referências ao gerador singleton.
     *
     * @param spec especificação do job
     * @param problem instância de {@link Problem} (pode ser um {@link EvaluationBudgetProblem} já
     *     configurado ou um {@link DoubleProblem} bruto)
     * @return instância nova e isolada de {@link Algorithm} pronta para {@link Algorithm#run()}
     */
    public static Algorithm<List<DoubleSolution>> createFreshAlgorithmInstance(
            JobSpec spec, Problem<DoubleSolution> problem) {
        Objects.requireNonNull(spec, "spec não pode ser nulo.");
        Objects.requireNonNull(problem, "problem não pode ser nulo.");
        if (!(problem instanceof DoubleProblem doubleProblem)) {
            throw new IllegalArgumentException(
                    "O problema deve implementar DoubleProblem: " + problem.getClass().getName());
        }

        EvaluationBudgetProblem budgetProblem =
                (doubleProblem instanceof EvaluationBudgetProblem existing)
                        ? existing
                        : new EvaluationBudgetProblem(
                                doubleProblem, spec.targetEvaluations(), spec.enforceHardBudget());

        CrossoverOperator<DoubleSolution> freshCrossover =
                new SBXCrossover(
                        DEFAULT_CROSSOVER_PROBABILITY, DEFAULT_CROSSOVER_DISTRIBUTION_INDEX);
        double mutationProbability = 1.0 / budgetProblem.numberOfVariables();
        MutationOperator<DoubleSolution> freshMutation =
                new PolynomialMutation(mutationProbability, DEFAULT_MUTATION_DISTRIBUTION_INDEX);

        return EvaluationBudgetProblem.buildAuditedAlgorithm(
                spec.algorithmName(),
                budgetProblem,
                spec.maxGenerations(),
                freshCrossover,
                freshMutation);
    }

    /**
     * Executa um {@link JobSpec} de forma estritamente isolada e determinística dentro do processo
     * atual.
     *
     * <p>Adquire {@link #JMETAL_RANDOM_LOCK} durante toda a janela crítica:
     * <ol>
     *   <li>Configura {@code JMetalRandom.getInstance().setSeed(spec.effectiveSeed())};
     *   <li>Instancia sob demanda um novo {@link DoubleProblem} e um novo
     *       {@link EvaluationBudgetProblem};
     *   <li>Instancia sob demanda os operadores e o algoritmo via
     *       {@link #createFreshAlgorithmInstance(JobSpec, Problem)};
     *   <li>Executa {@code algorithm.run()} medindo tempos de relógio e de CPU;
     *   <li>Extrai cópias profundas de objetivos e variáveis da população final, calcula os hashes
     *       SHA-256 bit-a-bit de {@code FUN} e {@code VAR} e libera todas as referências ao
     *       algoritmo antes de liberar o lock.
     * </ol>
     *
     * @param spec especificação imutável do job
     * @return {@link JobExecutionResult} contendo métricas, matrizes e hashes criptográficos
     */
    public static JobExecutionResult executeJobInProcess(JobSpec spec) {
        Objects.requireNonNull(spec, "spec não pode ser nulo.");

        JMETAL_RANDOM_LOCK.lock();
        try {
            JMetalRandom.getInstance().setSeed(spec.effectiveSeed());

            DoubleProblem rawProblem = createFreshProblemInstance(spec.problemName(), spec.m());
            EvaluationBudgetProblem budgetProblem =
                    new EvaluationBudgetProblem(
                            rawProblem, spec.targetEvaluations(), spec.enforceHardBudget());

            Algorithm<List<DoubleSolution>> algorithm =
                    createFreshAlgorithmInstance(spec, budgetProblem);

            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            boolean cpuTimeSupported =
                    threadBean.isCurrentThreadCpuTimeSupported()
                            && threadBean.isThreadCpuTimeEnabled();
            long startWallNanos = System.nanoTime();
            long startCpuNanos = cpuTimeSupported ? threadBean.getCurrentThreadCpuTime() : 0L;

            algorithm.run();

            long elapsedWallMs = Math.max(0L, (System.nanoTime() - startWallNanos) / 1_000_000L);
            long elapsedCpuMs =
                    cpuTimeSupported
                            ? Math.max(
                                    0L,
                                    (threadBean.getCurrentThreadCpuTime() - startCpuNanos)
                                            / 1_000_000L)
                            : 0L;

            List<DoubleSolution> rawPopulation = algorithm.result();
            if (rawPopulation == null) {
                rawPopulation = List.of();
            }
            List<DoubleSolution> nonDominated =
                    rawPopulation.isEmpty()
                            ? List.of()
                            : SolutionListUtils.getNonDominatedSolutions(rawPopulation);

            double[][] rawObjectives = extractObjectivesMatrix(rawPopulation);
            double[][] rawVariables = extractVariablesMatrix(rawPopulation);
            String objectivesHash = computeMatrixSha256(rawObjectives);
            String variablesHash = computeMatrixSha256(rawVariables);
            int actualEvals = Math.toIntExact(budgetProblem.getActualProblemEvaluations());

            return new JobExecutionResult(
                    spec,
                    actualEvals,
                    rawPopulation.size(),
                    nonDominated.size(),
                    elapsedWallMs,
                    elapsedCpuMs,
                    rawObjectives,
                    rawVariables,
                    objectivesHash,
                    variablesHash,
                    "SUCCESS");
        } finally {
            JMETAL_RANDOM_LOCK.unlock();
        }
    }

    /**
     * Exporta uma lista de {@link ManifestEntry} para um arquivo CSV com cabeçalho canônico
     * {@link #MANIFEST_CSV_HEADER}.
     */
    public static void exportManifestToCsv(Path csvPath, List<ManifestEntry> entries)
            throws IOException {
        Objects.requireNonNull(csvPath, "csvPath não pode ser nulo.");
        Objects.requireNonNull(entries, "entries não pode ser nulo.");
        Path parent = csvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write(MANIFEST_CSV_HEADER);
            writer.newLine();
            for (ManifestEntry entry : entries) {
                writer.write(entry.toCsvRow());
                writer.newLine();
            }
        }
    }

    /**
     * Lê todas as entradas {@link ManifestEntry} de um arquivo CSV gerado por
     * {@link #exportManifestToCsv(Path, List)}.
     */
    public static List<ManifestEntry> readManifestFromCsv(Path csvPath) throws IOException {
        Objects.requireNonNull(csvPath, "csvPath não pode ser nulo.");
        List<ManifestEntry> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            boolean headerChecked = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!headerChecked) {
                    if (!trimmed.equals(MANIFEST_CSV_HEADER)) {
                        throw new IllegalArgumentException(
                                "Cabeçalho CSV inesperado no manifesto: " + trimmed);
                    }
                    headerChecked = true;
                    continue;
                }
                result.add(ManifestEntry.fromCsvRow(trimmed));
            }
        }
        return result;
    }

    /**
     * Calcula o hash SHA-256 hexadecimal (minúsculo, 64 caracteres) da representação binária exata
     * IEEE 754 ({@link Double#doubleToLongBits(double)}) de uma matriz {@code double[][]}.
     *
     * <p>Diferencia inclusive dimensões da matriz (linhas e colunas codificadas no cabeçalho do
     * digest) e preserva determinismo bit-a-bit entre execuções.
     */
    public static String computeMatrixSha256(double[][] matrix) {
        Objects.requireNonNull(matrix, "matrix não pode ser nula.");
        MessageDigest md = newSha256Digest();
        updateIntBigEndian(md, matrix.length);
        for (double[] row : matrix) {
            if (row == null) {
                updateIntBigEndian(md, -1);
                continue;
            }
            updateIntBigEndian(md, row.length);
            for (double value : row) {
                long bits = Double.doubleToLongBits(value);
                updateLongBigEndian(md, bits);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static double[][] extractObjectivesMatrix(List<DoubleSolution> solutions) {
        double[][] matrix = new double[solutions.size()][];
        for (int i = 0; i < solutions.size(); i++) {
            double[] objs = solutions.get(i).objectives();
            matrix[i] = (objs != null) ? objs.clone() : new double[0];
        }
        return matrix;
    }

    private static double[][] extractVariablesMatrix(List<DoubleSolution> solutions) {
        double[][] matrix = new double[solutions.size()][];
        for (int i = 0; i < solutions.size(); i++) {
            List<Double> vars = solutions.get(i).variables();
            double[] row = new double[vars.size()];
            for (int j = 0; j < vars.size(); j++) {
                row[j] = vars.get(j);
            }
            matrix[i] = row;
        }
        return matrix;
    }

    private static double[][] deepCopyMatrix(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = (source[i] != null) ? source[i].clone() : null;
        }
        return copy;
    }

    private static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static byte[] sha256Bytes(byte[] input) {
        MessageDigest md = newSha256Digest();
        return md.digest(input);
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível no ambiente Java.", e);
        }
    }

    private static void updateIntBigEndian(MessageDigest md, int value) {
        md.update((byte) (value >>> 24));
        md.update((byte) (value >>> 16));
        md.update((byte) (value >>> 8));
        md.update((byte) value);
    }

    private static void updateLongBigEndian(MessageDigest md, long value) {
        md.update((byte) (value >>> 56));
        md.update((byte) (value >>> 48));
        md.update((byte) (value >>> 40));
        md.update((byte) (value >>> 32));
        md.update((byte) (value >>> 24));
        md.update((byte) (value >>> 16));
        md.update((byte) (value >>> 8));
        md.update((byte) value);
    }
}

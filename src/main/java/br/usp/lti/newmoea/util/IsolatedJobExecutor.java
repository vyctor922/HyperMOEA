package br.usp.lti.newmoea.util;

import br.usp.lti.newmoea.feedback.R2ExecutionObserver;
import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
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
 * Executor determinístico, lazy e estritamente isolado para jobs experimentais
 * do estudo
 * {@code hypeMOEA} (Auditoria de Isolamento de Processos, Sementes e Estado
 * Compartilhado).
 *
 * <p>
 * Resolve as três falhas estruturais identificadas na arquitetura padrão de
 * {@code WFGStudy} e
 * {@code ExecuteAlgorithms} do jMetal 7.8:
 *
 * <ol>
 * <li><b>Corrida de dados sobre o singleton global
 * {@link JMetalRandom#getInstance()}</b>:
 * serializa o acesso a {@link JMetalRandom} na JVM via
 * {@link #JMETAL_RANDOM_LOCK} (ou isola
 * via processo filho dedicado) e configura explicitamente
 * {@code JMetalRandom.getInstance().setSeed(effectiveSeed)}
 * imediatamente antes da instanciação e execução de cada job;
 * <li><b>Derivação determinística de semente por tupla</b>: calcula
 * {@code effectiveSeed}
 * exclusivamente como função pura da tupla canônica
 * {@code (baseSeed, problemName, m,
 *       algorithmName, runId)} via SHA-256 + avalanching SplitMix64, garantindo
 * 100% de
 * independência em relação à ordem de execução dos jobs;
 * <li><b>Instanciação sob demanda (lazy) sem estado mutável compartilhado</b>:
 * cria instâncias
 * novas e exclusivas de {@link DoubleProblem}, {@link EvaluationBudgetProblem},
 * {@link SBXCrossover}, {@link PolynomialMutation} e {@link Algorithm} apenas
 * no momento de
 * executar o job e descarta as referências ao término, eliminando a retenção
 * simultânea de
 * dezenas de milhares de algoritmos na memória heap.
 * </ol>
 */
public final class IsolatedJobExecutor {

    /** Semente base canônica padrão do protocolo experimental auditado. */
    public static final long DEFAULT_BASE_SEED = 20260409L;

    /** Probabilidade de cruzamento SBX padrão do estudo ({@code WFGStudy}). */
    public static final double DEFAULT_CROSSOVER_PROBABILITY = 1.0;

    /**
     * Índice de distribuição de cruzamento SBX padrão do estudo ({@code WFGStudy}).
     */
    public static final double DEFAULT_CROSSOVER_DISTRIBUTION_INDEX = 30.0;

    /**
     * Índice de distribuição de mutação polinomial padrão do estudo
     * ({@code WFGStudy}).
     */
    public static final double DEFAULT_MUTATION_DISTRIBUTION_INDEX = 20.0;

    /**
     * Número total de variáveis dos problemas DTLZ no estudo ({@code WFGStudy}).
     */
    public static final int DEFAULT_DTLZ_VARIABLES = 20;

    /**
     * Número de variáveis de distância (\(l\)) dos problemas WFG no estudo
     * ({@code WFGStudy}).
     */
    public static final int DEFAULT_WFG_DISTANCE_VARIABLES = 20;

    /**
     * Cabeçalho canônico de 16 colunas do manifesto CSV de reprodutibilidade (T15).
     */
    public static final String MANIFEST_CSV_HEADER = "job_id,problem,m,algorithm,run_id,base_seed,effective_seed,n_config,max_generations,"
            + "target_evaluations,actual_evaluations,raw_size,nd_size,"
            + "objectives_sha256,variables_sha256,status";

    /**
     * Propriedade JVM para habilitar/desabilitar ordenação LPT
     * ({@code Longest Processing Time first}).
     */
    public static final String LPT_SCHEDULING_PROPERTY = "study.lptScheduling";

    /**
     * Propriedade JVM para intervalo de consolidação do {@code run_manifest.csv} no
     * checkpointing incremental.
     */
    public static final String CHECKPOINT_INTERVAL_PROPERTY = "study.checkpointInterval";

    /**
     * Intervalo padrão de consolidação atômica do {@code run_manifest.csv} entre
     * gravações no journal.
     */
    public static final int DEFAULT_CHECKPOINT_INTERVAL = 25;

    private static final String CHECKPOINT_JOURNAL_FILENAME = "run_manifest.checkpoint.csv";
    private static final double[][] EMPTY_MATRIX = new double[0][];

    /**
     * Lock global intraprocesso que garante isolamento estrito de
     * {@link JMetalRandom#getInstance()}
     * durante toda a janela {@code setSeed -> build -> run} de um job executado na
     * mesma JVM.
     */
    private static final ReentrantLock JMETAL_RANDOM_LOCK = new ReentrantLock(true);

    private IsolatedJobExecutor() {
        // Classe utilitária estática
    }

    /**
     * Especificação imutável e autocontida de um job experimental individual.
     *
     * @param problemName       identificador canônico do problema (ex.:
     *                          {@code "WFG1"}, {@code "DTLZ2"})
     * @param m                 número de objetivos (\(m \in \{2, 3, 5, 8, 10,
     *                          15\}\))
     * @param algorithmName     identificador canônico do algoritmo
     *                          ({@code "NSGA-III"},
     *                          {@code "MOEA/DD"}, {@code "FDEA"}, {@code "RVEA"},
     *                          {@code "AGE-MOEA"},
     *                          {@code "AGE-MOEA-II"})
     * @param runId             índice da execução independente (\(\text{runId} \ge
     *                          0\))
     * @param baseSeed          semente mestre do estudo experimental
     * @param effectiveSeed     semente efetiva de 64 bits derivada
     *                          deterministicamente da tupla
     * @param populationSize    tamanho configurado da população
     *                          (\(N_{\text{config}}(m)\))
     * @param maxGenerations    número máximo de gerações configurado (\(G_{\max}\))
     * @param targetEvaluations orçamento alvo de avaliações (\(B_{\text{target}}\))
     * @param enforceHardBudget {@code true} para impor o teto estrito
     *                          {@code targetEvaluations} via
     *                          {@link EvaluationBudgetProblem}; {@code false} para
     *                          modo auditoria
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
         * Cria uma {@link JobSpec} canônica com os parâmetros de população e gerações
         * definidos em
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
         * Cria uma {@link JobSpec} com número customizado de gerações e orçamento alvo
         * (útil para
         * testes determinísticos rápidos de fumaça e auditoria), mantendo
         * \(N_{\text{config}}(m)\)
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
     * @param spec                   especificação original do job executado
     * @param actualEvaluations      número real de avaliações da função objetivo
     *                               contabilizadas
     * @param rawResultSize          cardinalidade da população bruta retornada por
     *                               {@code algorithm.result()}
     * @param nonDominatedResultSize cardinalidade da frente não-dominada extraída
     *                               do resultado
     * @param wallTimeMs             tempo de relógio decorrido em milissegundos
     * @param cpuTimeMs              tempo de CPU da thread executora em
     *                               milissegundos
     * @param rawObjectives          matriz profunda de objetivos da população bruta
     *                               ({@code rawResultSize x m})
     * @param rawVariables           matriz profunda de variáveis de decisão da
     *                               população bruta
     *                               ({@code rawResultSize x nVars})
     * @param objectivesSha256       hash SHA-256 canônico (64 hex chars) dos bits
     *                               IEEE 754 de
     *                               {@code rawObjectives}
     * @param variablesSha256        hash SHA-256 canônico (64 hex chars) dos bits
     *                               IEEE 754 de
     *                               {@code rawVariables}
     * @param status                 status final da execução (ex.:
     *                               {@code "SUCCESS"})
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

        /**
         * Converte este resultado em uma entrada de manifesto CSV
         * {@link ManifestEntry}.
         */
        public ManifestEntry toManifestEntry() {
            return ManifestEntry.fromResult(this);
        }

        /**
         * Retorna uma versão compactada contendo apenas metadados e hashes SHA-256 para
         * retenção
         * segura em memória após a persistência em disco das matrizes
         * {@code FUN}/{@code VAR}
         * (Checkpointing Incremental).
         */
        public JobExecutionResult compactRetainingMetadataOnly() {
            return new JobExecutionResult(
                    spec,
                    actualEvaluations,
                    rawResultSize,
                    nonDominatedResultSize,
                    wallTimeMs,
                    cpuTimeMs,
                    EMPTY_MATRIX,
                    EMPTY_MATRIX,
                    objectivesSha256,
                    variablesSha256,
                    status);
        }
    }

    /**
     * Registro imutável correspondente a uma linha do manifesto de
     * reprodutibilidade CSV (T15).
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

        /**
         * Serializa a entrada como linha CSV compatível com
         * {@link #MANIFEST_CSV_HEADER}.
         */
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

        /**
         * Reconstrói um {@link ManifestEntry} a partir de uma linha CSV do manifesto.
         */
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
     * <p>
     * A derivação aplica:
     * <ol>
     * <li>Normalização canônica de {@code problemName}
     * ({@link StaticParams#normalizeProblemId})
     * e {@code algorithmName}
     * ({@link EvaluationBudgetProblem#normalizeAlgorithmName});
     * <li>Codificação UTF-8 da string canônica sem ambiguidade de delimitador:
     * {@code "hypeMOEA:v1|baseSeed=<B>|problem=
     * 
    <P>
     * |m=<M>|algorithm=<A>|run=<R>"};
     * <li>Resumo criptográfico SHA-256 (256 bits) e extração dos primeiros 64 bits
     * em formato
     * big-endian (\(H_{64}\)), combinados por XOR com {@code baseSeed} e
     * finalizados pelo
     * misturador bijetivo {@code SplitMix64} (Stafford Variant 13).
     * </ol>
     *
     * <p>
     * Propriedades garantidas:
     * <ul>
     * <li>100% independente da ordem de execução ou do histórico de jobs
     * anteriores;
     * <li>Invariante a variações superficiais de grafia (ex.: {@code "NSGAIII"} vs
     * {@code "NSGA-III"}, {@code "wfg1"} vs {@code "WFG1"});
     * <li>Efeito avalanche completo (~50% dos bits invertidos para qualquer
     * alteração em
     * {@code baseSeed}, {@code problemName}, {@code m}, {@code algorithmName} ou
     * {@code runId}).
     * </ul>
     *
     * @param baseSeed      semente mestre do estudo
     * @param problemName   nome do problema
     * @param m             número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
     * @param algorithmName nome do algoritmo
     * @param runId         identificador da run independente (\(\text{runId} \ge
     *                      0\))
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

        String canonicalTuple = String.format(
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
     * Instancia sob demanda (lazy) uma nova instância limpa de
     * {@link DoubleProblem} para o
     * problema e dimensão de objetivos especificados, seguindo exatamente a
     * parametrização do
     * protocolo {@code WFGStudy}:
     *
     * <ul>
     * <li>{@code WFG1..WFG9}: \(k = 2(m - 1)\) variáveis de posição, \(l = 20\)
     * variáveis de
     * distância, \(m\) objetivos;
     * <li>{@code DTLZ1..DTLZ7}: \(n = 20\) variáveis de decisão totais, \(m\)
     * objetivos.
     * </ul>
     *
     * @param problemName nome do problema ({@code WFG1..WFG9} ou
     *                    {@code DTLZ1..DTLZ7})
     * @param m           número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
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
     * Instancia sob demanda (lazy) uma nova instância limpa do algoritmo e de todos
     * os seus
     * operadores ({@link SBXCrossover}, {@link PolynomialMutation}, operadores de
     * seleção e
     * avaliadores), sem compartilhar nenhum objeto mutável com outros jobs.
     *
     * <p>
     * <b>Importante</b>: este método deve ser chamado após configurar
     * {@code JMetalRandom.getInstance().setSeed(spec.effectiveSeed())}, pois
     * construtores de
     * algoritmos/operadores no jMetal 7.8 vinculam referências ao gerador
     * singleton.
     *
     * @param spec    especificação do job
     * @param problem instância de {@link Problem} (pode ser um
     *                {@link EvaluationBudgetProblem} já
     *                configurado ou um {@link DoubleProblem} bruto)
     * @return instância nova e isolada de {@link Algorithm} pronta para
     *         {@link Algorithm#run()}
     */
    public static Algorithm<List<DoubleSolution>> createFreshAlgorithmInstance(
            JobSpec spec, Problem<DoubleSolution> problem) {
        Objects.requireNonNull(spec, "spec não pode ser nulo.");
        Objects.requireNonNull(problem, "problem não pode ser nulo.");
        if (!(problem instanceof DoubleProblem doubleProblem)) {
            throw new IllegalArgumentException(
                    "O problema deve implementar DoubleProblem: " + problem.getClass().getName());
        }

        EvaluationBudgetProblem budgetProblem = (doubleProblem instanceof EvaluationBudgetProblem existing)
                ? existing
                : new EvaluationBudgetProblem(
                        doubleProblem, spec.targetEvaluations(), spec.enforceHardBudget());

        CrossoverOperator<DoubleSolution> freshCrossover = new SBXCrossover(
                DEFAULT_CROSSOVER_PROBABILITY, DEFAULT_CROSSOVER_DISTRIBUTION_INDEX);
        double mutationProbability = 1.0 / budgetProblem.numberOfVariables();
        MutationOperator<DoubleSolution> freshMutation = new PolynomialMutation(mutationProbability,
                DEFAULT_MUTATION_DISTRIBUTION_INDEX);

        return EvaluationBudgetProblem.buildAuditedAlgorithm(
                spec.algorithmName(),
                budgetProblem,
                spec.maxGenerations(),
                freshCrossover,
                freshMutation);
    }

    /**
     * Sobrecarga de {@link #createFreshAlgorithmInstance(JobSpec, Problem)} que
     * injeta diretório
     * de pesos previamente resolvido no preflight para evitar re-resolução em tempo
     * de execução.
     */
    public static Algorithm<List<DoubleSolution>> createFreshAlgorithmInstance(
            JobSpec spec, Problem<DoubleSolution> problem, String preResolvedMoeadWeightsDir) {
        Objects.requireNonNull(spec, "spec não pode ser nulo.");
        Objects.requireNonNull(problem, "problem não pode ser nulo.");
        if (!(problem instanceof DoubleProblem doubleProblem)) {
            throw new IllegalArgumentException(
                    "O problema deve implementar DoubleProblem: " + problem.getClass().getName());
        }

        EvaluationBudgetProblem budgetProblem = (doubleProblem instanceof EvaluationBudgetProblem existing)
                ? existing
                : new EvaluationBudgetProblem(
                        doubleProblem, spec.targetEvaluations(), spec.enforceHardBudget());

        CrossoverOperator<DoubleSolution> freshCrossover = new SBXCrossover(
                DEFAULT_CROSSOVER_PROBABILITY, DEFAULT_CROSSOVER_DISTRIBUTION_INDEX);
        double mutationProbability = 1.0 / budgetProblem.numberOfVariables();
        MutationOperator<DoubleSolution> freshMutation = new PolynomialMutation(mutationProbability,
                DEFAULT_MUTATION_DISTRIBUTION_INDEX);

        return EvaluationBudgetProblem.buildAuditedAlgorithm(
                spec.algorithmName(),
                budgetProblem,
                spec.maxGenerations(),
                freshCrossover,
                freshMutation,
                preResolvedMoeadWeightsDir);
    }

    /**
     * Executa um {@link JobSpec} de forma estritamente isolada e determinística
     * dentro do processo
     * atual.
     *
     * <p>
     * Adquire {@link #JMETAL_RANDOM_LOCK} durante toda a janela crítica:
     * <ol>
     * <li>Configura
     * {@code JMetalRandom.getInstance().setSeed(spec.effectiveSeed())};
     * <li>Instancia sob demanda um novo {@link DoubleProblem} e um novo
     * {@link EvaluationBudgetProblem};
     * <li>Instancia sob demanda os operadores e o algoritmo via
     * {@link #createFreshAlgorithmInstance(JobSpec, Problem)};
     * <li>Executa {@code algorithm.run()} medindo tempos de relógio e de CPU;
     * <li>Extrai cópias profundas de objetivos e variáveis da população final,
     * calcula os hashes
     * SHA-256 bit-a-bit de {@code FUN} e {@code VAR} e libera todas as referências
     * ao
     * algoritmo antes de liberar o lock.
     * </ol>
     *
     * @param spec especificação imutável do job
     * @return {@link JobExecutionResult} contendo métricas, matrizes e hashes
     *         criptográficos
     */
    public static JobExecutionResult executeJobInProcess(JobSpec spec) {
        return executeJobInProcess(spec, null);
    }

    /**
     * Propriedade opt-in (desativada por padrão) para coleta geracional de R2 por
     * run.
     */
    public static final String ENABLE_R2_OBSERVER_PROPERTY = "study.enableR2Observer";

    public static boolean isR2ObserverEnabled() {
        return Boolean.getBoolean(ENABLE_R2_OBSERVER_PROPERTY);
    }

    /**
     * Executa um {@link JobSpec} no processo atual com diretório de pesos
     * opcionalmente pré-resolvido.
     */
    public static JobExecutionResult executeJobInProcess(
            JobSpec spec, String preResolvedMoeadWeightsDir) {
        return executeJobInProcess(spec, preResolvedMoeadWeightsDir, null);
    }

    /**
     * Executa um {@link JobSpec} no processo atual, exportando opcionalmente a
     * trajetória geracional
     * isolada de {@link R2ExecutionObserver} para {@code r2TraceCsvOut} somente
     * quando informada
     * (opt-in {@code -Dstudy.enableR2Observer=true}, sem acrescentar avaliações de
     * objetivos).
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static JobExecutionResult executeJobInProcess(
            JobSpec spec, String preResolvedMoeadWeightsDir, Path r2TraceCsvOut) {
        Objects.requireNonNull(spec, "spec não pode ser nulo.");
        if (spec.enforceHardBudget()) {
            EvaluationBudgetProblem.validateMinimumInitializationBudget(
                    spec.algorithmName(), spec.m(), spec.targetEvaluations());
        }

        JMETAL_RANDOM_LOCK.lock();
        try {
            JMetalRandom.getInstance().setSeed(spec.effectiveSeed());

            DoubleProblem rawProblem = createFreshProblemInstance(spec.problemName(), spec.m());
            EvaluationBudgetProblem budgetProblem = new EvaluationBudgetProblem(
                    rawProblem, spec.targetEvaluations(), spec.enforceHardBudget());

            Algorithm<List<DoubleSolution>> algorithm = createFreshAlgorithmInstance(spec, budgetProblem,
                    preResolvedMoeadWeightsDir);

            R2ExecutionObserver perRunR2Observer = null;
            ObjectiveNormalizationContract.NormalizationBounds r2Bounds = null;
            if (r2TraceCsvOut != null) {
                try {
                    Path refPath = PerformanceProfiler.resolveReferenceFrontPath(
                            spec.problemName(), spec.m(), spec.algorithmName());
                    double[][] rawRef = org.uma.jmetal.util.VectorUtils.readVectors(refPath.toString(), ",");
                    r2Bounds = ObjectiveNormalizationContract.resolveBounds(
                            spec.problemName(),
                            spec.m(),
                            ObjectiveNormalizationContract.NormalizationMode.THEORETICAL_PARETO_BOUNDS,
                            rawRef);
                    double[][] normRef = ObjectiveNormalizationContract.normalizeFront(rawRef, r2Bounds);
                    org.uma.jmetal.qualityindicator.impl.R2 deterministicR2 = ObjectiveNormalizationContract
                            .createDeterministicR2(normRef, true);
                    perRunR2Observer = new R2ExecutionObserver(deterministicR2);
                    attachNonInvasiveR2ObserverHook(
                            algorithm, budgetProblem, perRunR2Observer, r2Bounds);
                } catch (IOException ioe) {
                    throw new java.io.UncheckedIOException(
                            "Falha ao inicializar frente de referência para R2ExecutionObserver", ioe);
                }
            }

            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            boolean cpuTimeSupported = threadBean.isCurrentThreadCpuTimeSupported()
                    && threadBean.isThreadCpuTimeEnabled();
            long startWallNanos = System.nanoTime();
            long startCpuNanos = cpuTimeSupported ? threadBean.getCurrentThreadCpuTime() : 0L;

            algorithm.run();

            long elapsedWallMs = Math.max(0L, (System.nanoTime() - startWallNanos) / 1_000_000L);
            long elapsedCpuMs = cpuTimeSupported
                    ? Math.max(
                            0L,
                            (threadBean.getCurrentThreadCpuTime() - startCpuNanos)
                                    / 1_000_000L)
                    : 0L;

            List<DoubleSolution> rawPopulation = algorithm.result();
            if (rawPopulation == null) {
                rawPopulation = List.of();
            }

            if (perRunR2Observer != null && r2Bounds != null && !rawPopulation.isEmpty()) {
                long finalStep = budgetProblem.getActualProblemEvaluations();
                var latest = perRunR2Observer.getLatestObservation();
                if (latest == null || latest.step() < finalStep) {
                    observeNormalizedPopulationPassively(
                            perRunR2Observer, r2Bounds, finalStep, rawPopulation);
                }
                try {
                    perRunR2Observer.exportToCsv(r2TraceCsvOut);
                } catch (IOException ioe) {
                    throw new java.io.UncheckedIOException(
                            "Falha ao exportar trajetória CSV de R2ExecutionObserver", ioe);
                }
            }
            List<DoubleSolution> nonDominated = rawPopulation.isEmpty()
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
     * Conecta de modo 100% não-invasivo uma instância isolada por run de
     * {@link R2ExecutionObserver}
     * respeitando a hierarquia real de cada algoritmo no jMetal 7.8 (sem presumir
     * que todos herdem
     * de {@code AbstractGeneticAlgorithm}):
     * <ul>
     * <li>{@code RVEA} ({@code EvolutionaryAlgorithm}): registra observador em
     * {@code observable()};</li>
     * <li>{@code MOEA/DD} ({@code BudgetCompliantMOEADD}): registra ouvinte ao fim
     * de cada permutação;</li>
     * <li>{@code NSGA-III}, {@code FDEA}, {@code AGE-MOEA}, {@code AGE-MOEA-II}
     * ({@code AbstractGeneticAlgorithm}): observa {@code getPopulation()} na
     * fronteira de lote.</li>
     * </ul>
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void attachNonInvasiveR2ObserverHook(
            Algorithm<List<DoubleSolution>> algorithm,
            EvaluationBudgetProblem budgetProblem,
            R2ExecutionObserver observer,
            ObjectiveNormalizationContract.NormalizationBounds bounds) {
        if (algorithm instanceof org.uma.jmetal.component.algorithm.EvolutionaryAlgorithm ea) {
            ea.observable()
                    .register(
                            (observable, data) -> {
                                if (data instanceof Map map) {
                                    Object popObj = map.get("POPULATION");
                                    Object evalObj = map.get("EVALUATIONS");
                                    if (popObj instanceof List popList && !popList.isEmpty()) {
                                        long step = (evalObj instanceof Number n)
                                                ? n.longValue()
                                                : budgetProblem.getActualProblemEvaluations();
                                        observeNormalizedPopulationPassively(
                                                observer, bounds, step, popList);
                                    }
                                }
                            });
        } else if (algorithm instanceof EvaluationBudgetProblem.BudgetCompliantMOEADD moead) {
            moead.setEpochPopulationListener(
                    (evalStep, pop) -> observeNormalizedPopulationPassively(observer, bounds, evalStep, pop));
        } else if (algorithm instanceof br.usp.lti.newmoea.algorithm.fdea.FDEA fdea) {
            fdea.setGenerationPopulationListener(
                    (evalStep, pop) -> observeNormalizedPopulationPassively(observer, bounds, evalStep, pop));
        } else if (algorithm instanceof org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm aga) {
            budgetProblem.setPreBatchEvaluationListener(
                    batchNo -> {
                        if (batchNo >= 2) {
                            List<DoubleSolution> survivors = aga.getPopulation();
                            if (survivors != null && !survivors.isEmpty()) {
                                observeNormalizedPopulationPassively(
                                        observer,
                                        bounds,
                                        budgetProblem.getActualProblemEvaluations(),
                                        survivors);
                            }
                        }
                    });
        }
    }

    private static void observeNormalizedPopulationPassively(
            R2ExecutionObserver observer,
            ObjectiveNormalizationContract.NormalizationBounds bounds,
            long step,
            List<DoubleSolution> population) {
        if (population == null || population.isEmpty()) {
            return;
        }
        double[][] rawMat = extractObjectivesMatrix(population);
        double[][] normMat = ObjectiveNormalizationContract.normalizeFront(rawMat, bounds);
        observer.observe(step, normMat);
    }

    /**
     * Exceção tipada lançada quando um artefato de run existente em disco apresenta
     * corrupção,
     * interrupção parcial (ex.: {@code FUN} sem {@code VAR}), falha de alinhamento,
     * divergência
     * de hash SHA-256 ou incompatibilidade de protocolo, impedindo retomada
     * silenciosa sem jamais
     * sobrescrever resultados íntegros (T33).
     */
    public static final class CorruptedRunArtifactException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private final String jobId;
        private final String reasonCode;
        private final Path offendingPath;

        public CorruptedRunArtifactException(
                String jobId, String reasonCode, Path offendingPath, String message) {
            super(message);
            this.jobId = Objects.requireNonNull(jobId, "jobId não pode ser nulo.");
            this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode não pode ser nulo.");
            this.offendingPath = offendingPath;
        }

        public String getJobId() {
            return jobId;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public Path getOffendingPath() {
            return offendingPath;
        }
    }

    /**
     * Configuração de execução em lote e persistência/retomada de jobs do estudo
     * (T33).
     *
     * @param maxWorkers                 número máximo de workers concorrentes
     *                                   (\(\ge 1\))
     * @param subprocessIsolation        {@code true} para isolar cada job em
     *                                   subprocesso JVM dedicado;
     *                                   {@code false} para execução na mesma JVM
     *                                   protegida por {@link #JMETAL_RANDOM_LOCK}
     * @param jobTimeoutMs               timeout máximo de parede por job em modo
     *                                   subprocesso (ms)
     * @param persistRawFiles            {@code true} para persistir
     *                                   {@code FUN<r>.raw.csv} e
     *                                   {@code VAR<r>.raw.csv}
     *                                   junto aos arquivos canônicos
     *                                   {@code FUN<r>.csv} e {@code VAR<r>.csv}
     * @param preResolvedMoeadWeightsDir diretório opcional de pesos do
     *                                   {@code MOEA/DD} previamente resolvido
     * @param lptScheduling              {@code true} para ordenar jobs pendentes em
     *                                   ordem LPT (mais pesados primeiro)
     * @param checkpointManifestInterval número de jobs concluídos entre
     *                                   consolidações completas de
     *                                   {@code run_manifest.csv}
     */
    public record BatchExecutionConfig(
            int maxWorkers,
            boolean subprocessIsolation,
            long jobTimeoutMs,
            boolean persistRawFiles,
            String preResolvedMoeadWeightsDir,
            boolean lptScheduling,
            int checkpointManifestInterval) {

        public static final long DEFAULT_JOB_TIMEOUT_MS = 3_600_000L;

        public BatchExecutionConfig {
            if (maxWorkers < 1) {
                throw new IllegalArgumentException(
                        "maxWorkers deve ser >= 1, recebido: " + maxWorkers);
            }
            if (jobTimeoutMs <= 0L) {
                throw new IllegalArgumentException(
                        "jobTimeoutMs deve ser positivo, recebido: " + jobTimeoutMs);
            }
            if (checkpointManifestInterval < 1) {
                throw new IllegalArgumentException(
                        "checkpointManifestInterval deve ser >= 1, recebido: "
                                + checkpointManifestInterval);
            }
        }

        public BatchExecutionConfig(
                int maxWorkers,
                boolean subprocessIsolation,
                long jobTimeoutMs,
                boolean persistRawFiles,
                String preResolvedMoeadWeightsDir) {
            this(
                    maxWorkers,
                    subprocessIsolation,
                    jobTimeoutMs,
                    persistRawFiles,
                    preResolvedMoeadWeightsDir,
                    isLptSchedulingEnabled(maxWorkers),
                    getConfiguredCheckpointInterval());
        }

        public static BatchExecutionConfig defaultSequential() {
            return new BatchExecutionConfig(
                    1, false, DEFAULT_JOB_TIMEOUT_MS, true, null, false, DEFAULT_CHECKPOINT_INTERVAL);
        }
    }

    /**
     * Indica se a fila LPT ({@code -Dstudy.lptScheduling}) deve ser aplicada aos
     * jobs pendentes.
     * Ativa automaticamente quando {@code maxWorkers > 1} (e desativável com
     * {@code false}).
     */
    public static boolean isLptSchedulingEnabled(int maxWorkers) {
        String raw = System.getProperty(LPT_SCHEDULING_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return maxWorkers > 1;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(norm)) {
            return true;
        }
        if ("false".equals(norm)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Valor inválido em "
                        + LPT_SCHEDULING_PROPERTY
                        + " (esperado true ou false): "
                        + raw);
    }

    /**
     * Retorna o intervalo configurado em {@code -Dstudy.checkpointInterval} entre
     * consolidações
     * atômicas do {@code run_manifest.csv} (padrão
     * {@link #DEFAULT_CHECKPOINT_INTERVAL}).
     */
    public static int getConfiguredCheckpointInterval() {
        int interval = Integer.getInteger(CHECKPOINT_INTERVAL_PROPERTY, DEFAULT_CHECKPOINT_INTERVAL);
        if (interval < 1) {
            throw new IllegalArgumentException(
                    "A propriedade '" + CHECKPOINT_INTERVAL_PROPERTY + "' deve ser >= 1: " + interval);
        }
        return interval;
    }

    /**
     * Estima o custo computacional relativo de um {@link JobSpec} para
     * escalonamento LPT
     * ({@code Longest Processing Time first}), priorizando combinações pesadas
     * (ex.: {@code m=10}
     * {@code AGE-MOEA}/{@code AGE-MOEA-II}, {@code m=15} e {@code FDEA}) para
     * evitar núcleos ociosos
     * na cauda do lote sem alterar sementes nem resultados determinísticos.
     */
    public static double estimateRelativeJobCost(JobSpec spec) {
        Objects.requireNonNull(spec, "spec não pode ser nulo.");
        int nAlloc = EvaluationBudgetProblem.getAllocatedPopulationSize(spec.algorithmName(), spec.m());
        String canonicalAlg = EvaluationBudgetProblem.normalizeAlgorithmName(spec.algorithmName());
        double algWeight = switch (canonicalAlg) {
            case "AGE-MOEA", "AGE-MOEA-II" -> 4.0;
            case "FDEA" -> 2.5;
            case "NSGA-III" -> 1.5;
            case "MOEA/DD" -> 1.3;
            case "RVEA" -> 1.0;
            default -> 1.0;
        };
        return (double) spec.targetEvaluations() * (double) nAlloc * (double) spec.m() * algWeight;
    }

    /**
     * Ordena deterministicamente uma cópia dos {@link JobSpec} pendentes em ordem
     * decrescente de
     * custo estimado (LPT).
     */
    public static List<JobSpec> orderJobSpecsLpt(List<JobSpec> specs) {
        Objects.requireNonNull(specs, "specs não pode ser nulo.");
        List<JobSpec> ordered = new ArrayList<>(specs);
        ordered.sort(
                Comparator.comparingDouble(IsolatedJobExecutor::estimateRelativeJobCost)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(JobSpec::m).reversed())
                        .thenComparing(Comparator.comparingInt(JobSpec::targetEvaluations).reversed())
                        .thenComparing(JobSpec::problemName)
                        .thenComparing(JobSpec::algorithmName)
                        .thenComparingInt(JobSpec::runId));
        return ordered;
    }

    /**
     * Relatório consolidado da execução/retomada de um lote de jobs no diretório do
     * estudo (T33).
     */
    public record StudyBatchReport(
            int totalRequestedJobs,
            int resumedJobsCount,
            int executedJobsCount,
            List<ManifestEntry> consolidatedManifest,
            List<JobExecutionResult> newlyExecutedResults,
            Path manifestPath) {
    }

    /**
     * Executa um {@link JobSpec} em um subprocesso JVM dedicado e estritamente
     * isolado (sem
     * compartilhamento de {@link JMetalRandom} com outros processos), drenando
     * stdout/stderr em
     * threads daemon e garantindo ausência total de processos órfãos em caso de
     * timeout ou falha.
     */
    public static JobExecutionResult executeJobInSubprocess(JobSpec spec, long timeoutMs) {
        return executeJobInSubprocess(spec, timeoutMs, null);
    }

    /**
     * Sobrecarga de {@link #executeJobInSubprocess(JobSpec, long)} com diretório de
     * pesos pré-resolvido.
     */
    public static JobExecutionResult executeJobInSubprocess(
            JobSpec spec, long timeoutMs, String preResolvedMoeadWeightsDir) {
        return executeJobInSubprocess(spec, timeoutMs, preResolvedMoeadWeightsDir, null);
    }

    /**
     * Sobrecarga de {@link #executeJobInSubprocess} com exportação opt-in da
     * trajetória geracional
     * de {@link R2ExecutionObserver} quando {@code r2TraceCsvOut != null}.
     */
    public static JobExecutionResult executeJobInSubprocess(
            JobSpec spec,
            long timeoutMs,
            String preResolvedMoeadWeightsDir,
            Path r2TraceCsvOut) {
        Objects.requireNonNull(spec, "spec não pode ser nulo.");
        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("timeoutMs deve ser positivo: " + timeoutMs);
        }
        if (spec.enforceHardBudget()) {
            EvaluationBudgetProblem.validateMinimumInitializationBudget(
                    spec.algorithmName(), spec.m(), spec.targetEvaluations());
        }
        List<String> workerJvmArgs = ProcessIsolatedHypervolumeExecutor.resolveConfiguredWorkerJvmArgs();

        Path workerDir = null;
        Process process = null;
        try {
            workerDir = Files.createTempDirectory("hypemoea-job-worker-");
            String javaBin = resolveJavaExecutable();
            String classPath = ProcessIsolatedHypervolumeExecutor.resolveEffectiveRuntimeClassPath();

            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            cmd.addAll(workerJvmArgs);
            propagateSystemPropertyIfSet(cmd, "study.weightsDirectory");
            propagateSystemPropertyIfSet(cmd, "study.referenceFrontDirectory");
            propagateSystemPropertyIfSet(cmd, "replaceZeroWeights");
            propagateSystemPropertyIfSet(cmd, "replaceZeroFronts");
            propagateSystemPropertyIfSet(cmd, "study.allowMoeadWeightsInMemory");
            propagateSystemPropertyIfSet(cmd, ENABLE_R2_OBSERVER_PROPERTY);
            cmd.add("-cp");
            cmd.add(classPath);
            cmd.add(IsolatedJobExecutor.class.getName());
            cmd.add("--worker");
            cmd.add("--problem");
            cmd.add(spec.problemName());
            cmd.add("--m");
            cmd.add(Integer.toString(spec.m()));
            cmd.add("--algorithm");
            cmd.add(spec.algorithmName());
            cmd.add("--run");
            cmd.add(Integer.toString(spec.runId()));
            cmd.add("--base-seed");
            cmd.add(Long.toString(spec.baseSeed()));
            cmd.add("--effective-seed");
            cmd.add(Long.toString(spec.effectiveSeed()));
            cmd.add("--pop-size");
            cmd.add(Integer.toString(spec.populationSize()));
            cmd.add("--max-generations");
            cmd.add(Integer.toString(spec.maxGenerations()));
            cmd.add("--target-evaluations");
            cmd.add(Integer.toString(spec.targetEvaluations()));
            cmd.add("--strict");
            cmd.add(Boolean.toString(spec.enforceHardBudget()));
            cmd.add("--output-dir");
            cmd.add(workerDir.toAbsolutePath().toString());
            if (preResolvedMoeadWeightsDir != null && !preResolvedMoeadWeightsDir.isBlank()) {
                cmd.add("--weights-dir");
                cmd.add(preResolvedMoeadWeightsDir);
            }
            if (r2TraceCsvOut != null) {
                cmd.add("--r2-trace-out");
                cmd.add(r2TraceCsvOut.toAbsolutePath().toString());
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(Paths.get(System.getProperty("user.dir")).toFile());
            process = pb.start();

            StringBuilder stdoutBuf = new StringBuilder();
            StringBuilder stderrBuf = new StringBuilder();
            Thread outDrainer = startStreamDrainer(process.getInputStream(), stdoutBuf, "job-out");
            Thread errDrainer = startStreamDrainer(process.getErrorStream(), stderrBuf, "job-err");

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                terminateProcessTree(process);
                outDrainer.join(1_000L);
                errDrainer.join(1_000L);
                throw new IllegalStateException(
                        "Timeout de execução ("
                                + timeoutMs
                                + " ms) excedido no subprocesso do job "
                                + spec.jobId()
                                + ". Worker encerrado sem processos órfãos.");
            }

            outDrainer.join(2_000L);
            errDrainer.join(2_000L);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Subprocesso do job "
                                + spec.jobId()
                                + " falhou com exitCode="
                                + exitCode
                                + ". stderr: "
                                + stderrBuf);
            }

            Path propsFile = workerDir.resolve("result.properties");
            Path funBin = workerDir.resolve("raw_fun.bin");
            Path varBin = workerDir.resolve("raw_var.bin");
            if (!Files.isRegularFile(propsFile)
                    || !Files.isRegularFile(funBin)
                    || !Files.isRegularFile(varBin)) {
                throw new IllegalStateException(
                        "Subprocesso do job "
                                + spec.jobId()
                                + " terminou sem gravar artefatos completos.");
            }

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(propsFile)) {
                props.load(in);
            }
            double[][] rawObjectives = readBinaryMatrix(funBin);
            double[][] rawVariables = readBinaryMatrix(varBin);
            String expectedObjSha = props.getProperty("objectivesSha256", "");
            String expectedVarSha = props.getProperty("variablesSha256", "");
            String actualObjSha = computeMatrixSha256(rawObjectives);
            String actualVarSha = computeMatrixSha256(rawVariables);
            if (!actualObjSha.equals(expectedObjSha) || !actualVarSha.equals(expectedVarSha)) {
                throw new IllegalStateException(
                        "Divergência SHA-256 na transferência binária do job " + spec.jobId());
            }

            return new JobExecutionResult(
                    spec,
                    Integer.parseInt(props.getProperty("actualEvaluations")),
                    Integer.parseInt(props.getProperty("rawResultSize")),
                    Integer.parseInt(props.getProperty("nonDominatedResultSize")),
                    Long.parseLong(props.getProperty("wallTimeMs")),
                    Long.parseLong(props.getProperty("cpuTimeMs")),
                    rawObjectives,
                    rawVariables,
                    actualObjSha,
                    actualVarSha,
                    props.getProperty("status", "SUCCESS"));
        } catch (InterruptedException ie) {
            if (process != null) {
                terminateProcessTree(process);
            }
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Execução interrompida aguardando subprocesso do job " + spec.jobId(), ie);
        } catch (IOException ioe) {
            if (process != null) {
                terminateProcessTree(process);
            }
            throw new IllegalStateException(
                    "Erro de I/O no subprocesso do job " + spec.jobId() + ": " + ioe.getMessage(),
                    ioe);
        } finally {
            if (process != null && process.isAlive()) {
                terminateProcessTree(process);
            }
            deleteDirectoryQuietly(workerDir);
        }
    }

    /**
     * Entrypoint CLI dedicado ao subprocesso worker invocado por
     * {@link #executeJobInSubprocess(JobSpec, long, String)}.
     */
    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0 || !"--worker".equals(args[0])) {
            throw new IllegalArgumentException(
                    "IsolatedJobExecutor.main requer modo '--worker'.");
        }
        String problem = null;
        int m = -1;
        String algorithm = null;
        int run = -1;
        long baseSeed = DEFAULT_BASE_SEED;
        long effectiveSeed = 0L;
        int popSize = -1;
        int maxGen = -1;
        int targetEvals = -1;
        boolean strict = true;
        Path outDir = null;
        String weightsDir = null;
        Path r2TraceOut = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--problem" -> problem = args[++i];
                case "--m" -> m = Integer.parseInt(args[++i]);
                case "--algorithm" -> algorithm = args[++i];
                case "--run" -> run = Integer.parseInt(args[++i]);
                case "--base-seed" -> baseSeed = Long.parseLong(args[++i]);
                case "--effective-seed" -> effectiveSeed = Long.parseLong(args[++i]);
                case "--pop-size" -> popSize = Integer.parseInt(args[++i]);
                case "--max-generations" -> maxGen = Integer.parseInt(args[++i]);
                case "--target-evaluations" -> targetEvals = Integer.parseInt(args[++i]);
                case "--strict" -> strict = Boolean.parseBoolean(args[++i]);
                case "--output-dir" -> outDir = Paths.get(args[++i]);
                case "--weights-dir" -> weightsDir = args[++i];
                case "--r2-trace-out" -> r2TraceOut = Paths.get(args[++i]);
                default -> throw new IllegalArgumentException("Argumento desconhecido: " + args[i]);
            }
        }
        Objects.requireNonNull(outDir, "--output-dir obrigatório.");
        Files.createDirectories(outDir);
        JobSpec spec = new JobSpec(
                problem,
                m,
                algorithm,
                run,
                baseSeed,
                effectiveSeed,
                popSize,
                maxGen,
                targetEvals,
                strict);
        JobExecutionResult result = executeJobInProcess(spec, weightsDir, r2TraceOut);
        writeBinaryMatrix(outDir.resolve("raw_fun.bin"), result.rawObjectives());
        writeBinaryMatrix(outDir.resolve("raw_var.bin"), result.rawVariables());

        Properties props = new Properties();
        props.setProperty("actualEvaluations", Integer.toString(result.actualEvaluations()));
        props.setProperty("rawResultSize", Integer.toString(result.rawResultSize()));
        props.setProperty(
                "nonDominatedResultSize", Integer.toString(result.nonDominatedResultSize()));
        props.setProperty("wallTimeMs", Long.toString(result.wallTimeMs()));
        props.setProperty("cpuTimeMs", Long.toString(result.cpuTimeMs()));
        props.setProperty("objectivesSha256", result.objectivesSha256());
        props.setProperty("variablesSha256", result.variablesSha256());
        props.setProperty("status", result.status());
        try (var out = Files.newOutputStream(outDir.resolve("result.properties"))) {
            props.store(out, "IsolatedJobExecutor worker result");
        }
    }

    /**
     * Executa ou retoma de forma segura e auditável um lote de {@link JobSpec}
     * persistindo os
     * arquivos {@code FUN<runId>.csv}, {@code VAR<runId>.csv} (e irmãos
     * {@code .raw.csv}) na
     * hierarquia {@code <studyOutputDir>/data/<algTag>/<problemTag>/} e
     * consolidando por chave
     * canônica {@code (problem, m, algorithm, runId)} o manifesto
     * {@code run_manifest.csv} (T33).
     *
     * <p>
     * Regras estritas de retomada e preservação:
     * <ul>
     * <li>Runs anteriores íntegras e compatíveis com o protocolo atual são
     * preservadas intocadas
     * e jamais reexecutadas ou sobrescritas;</li>
     * <li>Qualquer estado parcial/corrompido (ex.: {@code FUN} sem {@code VAR},
     * arquivo de 0
     * bytes, desalinhamento {@code VAR -> FUN}, hash SHA-256 adulterado ou
     * protocolo
     * incompatível como mudança de {@code baseSeed}, {@code maxGenerations} ou
     * {@code targetEvaluations}) lança {@link CorruptedRunArtifactException}
     * imediatamente
     * antes de sobrescrever qualquer arquivo;</li>
     * <li>Entradas de lotes anteriores (outros intervalos de {@code runId}, outros
     * problemas ou
     * outras dimensões \(m\)) em {@code run_manifest.csv} são integralmente
     * preservadas sem
     * duplicação.</li>
     * </ul>
     */
    public static StudyBatchReport executeAndPersistBatch(
            List<JobSpec> jobs, Path studyOutputDir, BatchExecutionConfig config)
            throws IOException {
        Objects.requireNonNull(jobs, "jobs não pode ser nulo.");
        Objects.requireNonNull(studyOutputDir, "studyOutputDir não pode ser nulo.");
        Objects.requireNonNull(config, "config não pode ser nulo.");

        Files.createDirectories(studyOutputDir);
        Path manifestPath = studyOutputDir.resolve("run_manifest.csv");
        Path checkpointJournalPath = studyOutputDir.resolve(CHECKPOINT_JOURNAL_FILENAME);
        boolean manifestExistedOnDisk = Files.exists(manifestPath);

        Map<String, ManifestEntry> manifestByKey = new LinkedHashMap<>();
        if (manifestExistedOnDisk) {
            if (Files.size(manifestPath) == 0L) {
                throw new CorruptedRunArtifactException(
                        "STUDY_MANIFEST",
                        "EMPTY_MANIFEST",
                        manifestPath,
                        "Manifesto run_manifest.csv existe mas está vazio (0 bytes): "
                                + manifestPath);
            }
            for (ManifestEntry existing : readManifestFromCsv(manifestPath)) {
                manifestByKey.put(
                        canonicalJobKey(
                                existing.problem(),
                                existing.m(),
                                existing.algorithm(),
                                existing.runId()),
                        existing);
            }
        }
        manifestExistedOnDisk = reconcileCheckpointJournalIfPresent(
                checkpointJournalPath, manifestPath, manifestByKey, manifestExistedOnDisk);

        List<String> existingOrderedKeys = new ArrayList<>(manifestByKey.keySet());
        List<JobSpec> pendingExecution = new ArrayList<>();
        int resumedCount = 0;

        for (JobSpec spec : jobs) {
            if (spec.enforceHardBudget()) {
                EvaluationBudgetProblem.validateMinimumInitializationBudget(
                        spec.algorithmName(), spec.m(), spec.targetEvaluations());
            }
            Path cellDir = resolveCellDirectory(studyOutputDir, spec);
            Path funFile = cellDir.resolve("FUN" + spec.runId() + ".csv");
            Path varFile = cellDir.resolve("VAR" + spec.runId() + ".csv");
            Path rawFunFile = ResultFrontContract.deriveRawSiblingPath(funFile);
            Path rawVarFile = ResultFrontContract.deriveRawSiblingPath(varFile);
            String key = canonicalJobKey(
                    spec.problemName(), spec.m(), spec.algorithmName(), spec.runId());
            ManifestEntry recordedEntry = manifestByKey.get(key);

            if (verifyExistingRunForResume(
                    spec,
                    funFile,
                    varFile,
                    rawFunFile,
                    rawVarFile,
                    recordedEntry,
                    manifestExistedOnDisk)) {
                resumedCount++;
            } else {
                pendingExecution.add(spec);
            }
        }

        List<JobExecutionResult> executedResults = List.of();
        if (!pendingExecution.isEmpty()) {
            if (config.subprocessIsolation()) {
                ProcessIsolatedHypervolumeExecutor.resolveConfiguredWorkerJvmArgs();
            }
            executedResults = runPendingJobsIncremental(
                    pendingExecution,
                    jobs,
                    existingOrderedKeys,
                    manifestByKey,
                    manifestPath,
                    checkpointJournalPath,
                    studyOutputDir,
                    config);
        } else if (!manifestExistedOnDisk && !manifestByKey.isEmpty()) {
            exportManifestAtomically(
                    manifestPath, buildOrderedManifestEntries(manifestByKey, existingOrderedKeys, jobs));
        }

        List<ManifestEntry> consolidated = buildOrderedManifestEntries(manifestByKey, existingOrderedKeys, jobs);
        return new StudyBatchReport(
                jobs.size(),
                resumedCount,
                pendingExecution.size(),
                consolidated,
                List.copyOf(executedResults),
                manifestPath);
    }

    private static boolean reconcileCheckpointJournalIfPresent(
            Path checkpointJournalPath,
            Path manifestPath,
            Map<String, ManifestEntry> manifestByKey,
            boolean manifestExistedOnDisk)
            throws IOException {
        if (!Files.isRegularFile(checkpointJournalPath)) {
            return manifestExistedOnDisk;
        }
        List<String> lines = Files.readAllLines(checkpointJournalPath, StandardCharsets.UTF_8);
        boolean recoveredAny = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.equals(MANIFEST_CSV_HEADER)) {
                continue;
            }
            try {
                ManifestEntry entry = ManifestEntry.fromCsvRow(trimmed);
                String key = canonicalJobKey(
                        entry.problem(), entry.m(), entry.algorithm(), entry.runId());
                manifestByKey.put(key, entry);
                recoveredAny = true;
            } catch (IllegalArgumentException invalidLine) {
                boolean isLastLine = (i == lines.size() - 1);
                if (isLastLine) {
                    // Queda abrupta durante a gravação da última linha do journal: ignora linha
                    // truncada
                    break;
                }
                throw new IOException(
                        "Linha corrompida no journal de checkpoint ("
                                + checkpointJournalPath
                                + ") na linha "
                                + (i + 1)
                                + ": "
                                + trimmed,
                        invalidLine);
            }
        }
        if (recoveredAny) {
            exportManifestAtomically(manifestPath, new ArrayList<>(manifestByKey.values()));
            manifestExistedOnDisk = true;
        }
        Files.deleteIfExists(checkpointJournalPath);
        return manifestExistedOnDisk;
    }

    private static List<ManifestEntry> buildOrderedManifestEntries(
            Map<String, ManifestEntry> manifestByKey,
            List<String> existingOrderedKeys,
            List<JobSpec> requestedJobs) {
        if (manifestByKey.isEmpty()) {
            return List.of();
        }
        Set<String> emittedKeys = new LinkedHashSet<>();
        List<ManifestEntry> ordered = new ArrayList<>(manifestByKey.size());
        for (String key : existingOrderedKeys) {
            ManifestEntry entry = manifestByKey.get(key);
            if (entry != null && emittedKeys.add(key)) {
                ordered.add(entry);
            }
        }
        for (JobSpec spec : requestedJobs) {
            String key = canonicalJobKey(
                    spec.problemName(), spec.m(), spec.algorithmName(), spec.runId());
            ManifestEntry entry = manifestByKey.get(key);
            if (entry != null && emittedKeys.add(key)) {
                ordered.add(entry);
            }
        }
        for (Map.Entry<String, ManifestEntry> entry : manifestByKey.entrySet()) {
            if (emittedKeys.add(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return List.copyOf(ordered);
    }

    private static ManifestEntry persistSingleJobResult(
            JobExecutionResult result, Path studyOutputDir, BatchExecutionConfig config)
            throws IOException {
        JobSpec spec = result.spec();
        Path cellDir = resolveCellDirectory(studyOutputDir, spec);
        Files.createDirectories(cellDir);
        Path funFile = cellDir.resolve("FUN" + spec.runId() + ".csv");
        Path varFile = cellDir.resolve("VAR" + spec.runId() + ".csv");

        DoubleProblem freshProblem = createFreshProblemInstance(spec.problemName(), spec.m());
        List<DoubleSolution> reconstructedRaw = reconstructSolutions(freshProblem, result.rawObjectives(),
                result.rawVariables());
        ResultFrontContract.CanonicalResultExtraction extraction = ResultFrontContract.extractCanonicalResultSet(
                reconstructedRaw,
                freshProblem,
                ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);
        ResultFrontContract.writeCanonicalAndRawFiles(
                extraction, funFile, varFile, config.persistRawFiles());
        return result.toManifestEntry();
    }

    private static final class IncrementalCheckpointSession {
        private final Path studyOutputDir;
        private final BatchExecutionConfig config;
        private final Path manifestPath;
        private final Path checkpointJournalPath;
        private final Map<String, ManifestEntry> manifestByKey;
        private final List<String> existingOrderedKeys;
        private final List<JobSpec> requestedJobs;
        private final Map<String, Integer> pendingIndexByKey;
        private final JobExecutionResult[] orderedSummaries;
        private BufferedWriter journalWriter;
        private int pendingSinceSync = 0;
        private long totalPersisted = 0L;

        IncrementalCheckpointSession(
                Path studyOutputDir,
                BatchExecutionConfig config,
                Path manifestPath,
                Path checkpointJournalPath,
                Map<String, ManifestEntry> manifestByKey,
                List<String> existingOrderedKeys,
                List<JobSpec> requestedJobs,
                List<JobSpec> pendingExecution) {
            this.studyOutputDir = studyOutputDir;
            this.config = config;
            this.manifestPath = manifestPath;
            this.checkpointJournalPath = checkpointJournalPath;
            this.manifestByKey = manifestByKey;
            this.existingOrderedKeys = existingOrderedKeys;
            this.requestedJobs = requestedJobs;
            this.orderedSummaries = new JobExecutionResult[pendingExecution.size()];
            this.pendingIndexByKey = new LinkedHashMap<>(pendingExecution.size() * 2);
            for (int i = 0; i < pendingExecution.size(); i++) {
                JobSpec s = pendingExecution.get(i);
                this.pendingIndexByKey.put(
                        canonicalJobKey(s.problemName(), s.m(), s.algorithmName(), s.runId()), i);
            }
        }

        void onJobCompleted(JobExecutionResult result) throws IOException {
            ManifestEntry entry = persistSingleJobResult(result, studyOutputDir, config);
            JobSpec spec = result.spec();
            String key = canonicalJobKey(
                    spec.problemName(), spec.m(), spec.algorithmName(), spec.runId());
            manifestByKey.put(key, entry);
            Integer slot = pendingIndexByKey.get(key);
            if (slot != null) {
                orderedSummaries[slot] = result.compactRetainingMetadataOnly();
            }

            if (journalWriter == null) {
                journalWriter = Files.newBufferedWriter(
                        checkpointJournalPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
            journalWriter.write(entry.toCsvRow());
            journalWriter.newLine();
            journalWriter.flush();

            pendingSinceSync++;
            totalPersisted++;
            if (pendingSinceSync >= config.checkpointManifestInterval()) {
                syncConsolidatedManifest();
            }
        }

        private void syncConsolidatedManifest() throws IOException {
            closeJournalQuietly();
            exportManifestAtomically(
                    manifestPath,
                    buildOrderedManifestEntries(manifestByKey, existingOrderedKeys, requestedJobs));
            Files.deleteIfExists(checkpointJournalPath);
            pendingSinceSync = 0;
        }

        private void closeJournalQuietly() throws IOException {
            if (journalWriter != null) {
                try {
                    journalWriter.close();
                } finally {
                    journalWriter = null;
                }
            }
        }

        void finishAndFlush(Throwable primaryFailure) throws IOException {
            try {
                closeJournalQuietly();
                if (totalPersisted > 0L && (pendingSinceSync > 0 || !Files.exists(manifestPath))) {
                    exportManifestAtomically(
                            manifestPath,
                            buildOrderedManifestEntries(manifestByKey, existingOrderedKeys, requestedJobs));
                    Files.deleteIfExists(checkpointJournalPath);
                    pendingSinceSync = 0;
                }
            } catch (IOException ioe) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(ioe);
                } else {
                    throw ioe;
                }
            }
        }

        List<JobExecutionResult> compactedResultsInOrder() {
            List<JobExecutionResult> out = new ArrayList<>(orderedSummaries.length);
            for (JobExecutionResult item : orderedSummaries) {
                if (item != null) {
                    out.add(item);
                }
            }
            return List.copyOf(out);
        }
    }

    private static List<JobExecutionResult> runPendingJobsIncremental(
            List<JobSpec> pendingJobs,
            List<JobSpec> requestedJobs,
            List<String> existingOrderedKeys,
            Map<String, ManifestEntry> manifestByKey,
            Path manifestPath,
            Path checkpointJournalPath,
            Path studyOutputDir,
            BatchExecutionConfig config)
            throws IOException {
        boolean r2OptIn = isR2ObserverEnabled();
        List<JobSpec> dispatchOrder = config.lptScheduling() ? orderJobSpecsLpt(pendingJobs) : List.copyOf(pendingJobs);
        IncrementalCheckpointSession session = new IncrementalCheckpointSession(
                studyOutputDir,
                config,
                manifestPath,
                checkpointJournalPath,
                manifestByKey,
                existingOrderedKeys,
                requestedJobs,
                pendingJobs);

        Throwable primaryFailure = null;
        if (config.maxWorkers() == 1 && !config.subprocessIsolation()) {
            try {
                for (JobSpec spec : dispatchOrder) {
                    Path r2Out = r2OptIn
                            ? resolveCellDirectory(studyOutputDir, spec)
                                    .resolve("R2_trace_run" + spec.runId() + ".csv")
                            : null;
                    JobExecutionResult res = executeJobInProcess(
                            spec, config.preResolvedMoeadWeightsDir(), r2Out);
                    session.onJobCompleted(res);
                }
                return session.compactedResultsInOrder();
            } catch (Throwable t) {
                primaryFailure = t;
                throw t;
            } finally {
                session.finishAndFlush(primaryFailure);
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(config.maxWorkers());
        CompletionService<JobExecutionResult> completionService = new ExecutorCompletionService<>(pool);
        List<Future<JobExecutionResult>> futures = new ArrayList<>(dispatchOrder.size());
        try {
            for (JobSpec spec : dispatchOrder) {
                Path r2Out = r2OptIn
                        ? resolveCellDirectory(studyOutputDir, spec)
                                .resolve("R2_trace_run" + spec.runId() + ".csv")
                        : null;
                Callable<JobExecutionResult> task = config.subprocessIsolation()
                        ? () -> executeJobInSubprocess(
                                spec,
                                config.jobTimeoutMs(),
                                config.preResolvedMoeadWeightsDir(),
                                r2Out)
                        : () -> executeJobInProcess(
                                spec, config.preResolvedMoeadWeightsDir(), r2Out);
                futures.add(completionService.submit(task));
            }
            for (int completed = 0; completed < dispatchOrder.size(); completed++) {
                Future<JobExecutionResult> finishedFuture = completionService.take();
                JobExecutionResult res = finishedFuture.get();
                session.onJobCompleted(res);
            }
            return session.compactedResultsInOrder();
        } catch (InterruptedException ie) {
            for (Future<JobExecutionResult> f : futures) {
                f.cancel(true);
            }
            Thread.currentThread().interrupt();
            IllegalStateException wrapped = new IllegalStateException("Execução de lote interrompida.", ie);
            primaryFailure = wrapped;
            throw wrapped;
        } catch (ExecutionException ee) {
            for (Future<JobExecutionResult> f : futures) {
                f.cancel(true);
            }
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) {
                primaryFailure = re;
                throw re;
            }
            IllegalStateException wrapped = new IllegalStateException(
                    "Falha em worker de lote: " + cause.getMessage(), cause);
            primaryFailure = wrapped;
            throw wrapped;
        } catch (Throwable t) {
            for (Future<JobExecutionResult> f : futures) {
                f.cancel(true);
            }
            primaryFailure = t;
            throw t;
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            session.finishAndFlush(primaryFailure);
        }
    }

    private static boolean verifyExistingRunForResume(
            JobSpec spec,
            Path funFile,
            Path varFile,
            Path rawFunFile,
            Path rawVarFile,
            ManifestEntry recordedEntry,
            boolean manifestExistedOnDisk)
            throws IOException {
        boolean funExists = Files.exists(funFile);
        boolean varExists = Files.exists(varFile);
        boolean rawFunExists = Files.exists(rawFunFile);
        boolean rawVarExists = Files.exists(rawVarFile);

        if (!funExists && !varExists && !rawFunExists && !rawVarExists) {
            if (recordedEntry != null) {
                throw new CorruptedRunArtifactException(
                        spec.jobId(),
                        "MISSING_FILES_FOR_MANIFEST_ENTRY",
                        funFile,
                        "O manifesto registra "
                                + spec.jobId()
                                + " como concluída, mas FUN/VAR estão ausentes em disco: "
                                + funFile);
            }
            return false;
        }

        if (funExists != varExists) {
            Path orphan = funExists ? funFile : varFile;
            throw new CorruptedRunArtifactException(
                    spec.jobId(),
                    "ORPHAN_FILE_CRASH",
                    orphan,
                    "Interrupção/crash entre FUN e VAR detectado para "
                            + spec.jobId()
                            + " (apenas um dos arquivos existe): "
                            + orphan);
        }

        if (rawFunExists != rawVarExists) {
            Path orphanRaw = rawFunExists ? rawFunFile : rawVarFile;
            throw new CorruptedRunArtifactException(
                    spec.jobId(),
                    "ORPHAN_RAW_FILE_CRASH",
                    orphanRaw,
                    "Interrupção/crash entre arquivos brutos .raw.csv detectado para "
                            + spec.jobId()
                            + ": "
                            + orphanRaw);
        }

        if (!funExists) {
            throw new CorruptedRunArtifactException(
                    spec.jobId(),
                    "ORPHAN_RAW_WITHOUT_CANONICAL",
                    rawFunFile,
                    "Arquivos .raw.csv existem sem FUN/VAR canônicos para " + spec.jobId());
        }

        if (Files.size(funFile) == 0L || Files.size(varFile) == 0L) {
            Path emptyPath = (Files.size(funFile) == 0L) ? funFile : varFile;
            throw new CorruptedRunArtifactException(
                    spec.jobId(),
                    "EMPTY_FILE",
                    emptyPath,
                    "Arquivo FUN ou VAR vazio (0 bytes) detectado para "
                            + spec.jobId()
                            + ": "
                            + emptyPath);
        }

        if (recordedEntry == null) {
            throw new CorruptedRunArtifactException(
                    spec.jobId(),
                    manifestExistedOnDisk ? "MISSING_MANIFEST_ENTRY" : "UNVERIFIED_UNMANIFESTED_RUN",
                    funFile,
                    "Arquivos FUN/VAR existem em disco sem registro verificável em run_manifest.csv para "
                            + spec.jobId()
                            + " (recusando sobrescrita ou retomada cega): "
                            + funFile);
        }

        if (recordedEntry.baseSeed() != spec.baseSeed()
                || recordedEntry.effectiveSeed() != spec.effectiveSeed()
                || recordedEntry.nConfig() != spec.populationSize()
                || recordedEntry.maxGenerations() != spec.maxGenerations()
                || recordedEntry.targetEvaluations() != spec.targetEvaluations()
                || (spec.enforceHardBudget()
                        && recordedEntry.actualEvaluations() != spec.targetEvaluations())
                || !"SUCCESS".equals(recordedEntry.status())) {
            throw new CorruptedRunArtifactException(
                    spec.jobId(),
                    "PROTOCOL_MISMATCH",
                    funFile,
                    String.format(
                            Locale.ROOT,
                            "Protocolo incompatível na retomada de %s: manifesto=(seed=%d,effSeed=%d,N=%d,G=%d,B_target=%d,B_actual=%d,status=%s) vs solicitado=(seed=%d,effSeed=%d,N=%d,G=%d,B_target=%d,strict=%s)",
                            spec.jobId(),
                            recordedEntry.baseSeed(),
                            recordedEntry.effectiveSeed(),
                            recordedEntry.nConfig(),
                            recordedEntry.maxGenerations(),
                            recordedEntry.targetEvaluations(),
                            recordedEntry.actualEvaluations(),
                            recordedEntry.status(),
                            spec.baseSeed(),
                            spec.effectiveSeed(),
                            spec.populationSize(),
                            spec.maxGenerations(),
                            spec.targetEvaluations(),
                            spec.enforceHardBudget()));
        }

        DoubleProblem validationProblem = createFreshProblemInstance(spec.problemName(), spec.m());
        ResultFrontContract.FunVarAlignmentReport funVarReport = ResultFrontContract.verifyFunVarAlignment(
                funFile,
                varFile,
                validationProblem,
                ResultFrontContract.DEFAULT_ALIGNMENT_TOLERANCE);
        if (!funVarReport.isAlignedAndValid()) {
            throw new CorruptedRunArtifactException(
                    spec.jobId(),
                    "INVALID_FUN_VAR_ALIGNMENT",
                    funFile,
                    "Falha na validação estrutural/alinhamento FUN/VAR de "
                            + spec.jobId()
                            + ": "
                            + funVarReport.diagnosticMessage());
        }

        if (rawFunExists) {
            ResultFrontContract.FunVarAlignmentReport rawReport = ResultFrontContract.verifyFunVarAlignment(
                    rawFunFile,
                    rawVarFile,
                    validationProblem,
                    ResultFrontContract.DEFAULT_ALIGNMENT_TOLERANCE);
            if (!rawReport.isAlignedAndValid()) {
                throw new CorruptedRunArtifactException(
                        spec.jobId(),
                        "INVALID_RAW_ALIGNMENT",
                        rawFunFile,
                        "Falha na validação de FUN/VAR brutos (.raw.csv) de "
                                + spec.jobId()
                                + ": "
                                + rawReport.diagnosticMessage());
            }
            if (!rawReport.funMatrixSha256().equals(recordedEntry.objectivesSha256())
                    || !rawReport.varMatrixSha256().equals(recordedEntry.variablesSha256())) {
                throw new CorruptedRunArtifactException(
                        spec.jobId(),
                        "HASH_MISMATCH",
                        rawFunFile,
                        "Hash SHA-256 dos resultados brutos diverge do manifesto para "
                                + spec.jobId());
            }
            double[][] rawObj = org.uma.jmetal.util.VectorUtils.readVectors(
                    rawFunFile.toString(), ResultFrontContract.CSV_SEPARATOR);
            double[][] rawVar = org.uma.jmetal.util.VectorUtils.readVectors(
                    rawVarFile.toString(), ResultFrontContract.CSV_SEPARATOR);
            List<DoubleSolution> rawSols = reconstructSolutions(validationProblem, rawObj, rawVar);
            ResultFrontContract.CanonicalResultExtraction expectedCanonical = ResultFrontContract
                    .extractCanonicalResultSet(
                            rawSols,
                            validationProblem,
                            ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);
            if (!funVarReport
                    .funMatrixSha256()
                    .equals(expectedCanonical.canonicalObjectivesSha256())
                    || !funVarReport
                            .varMatrixSha256()
                            .equals(expectedCanonical.canonicalVariablesSha256())) {
                throw new CorruptedRunArtifactException(
                        spec.jobId(),
                        "HASH_MISMATCH",
                        funFile,
                        "Hash SHA-256 de FUN/VAR canônicos diverge da extração determinística dos brutos para "
                                + spec.jobId());
            }
        } else {
            if (!funVarReport.funMatrixSha256().equals(recordedEntry.objectivesSha256())
                    || !funVarReport.varMatrixSha256().equals(recordedEntry.variablesSha256())) {
                throw new CorruptedRunArtifactException(
                        spec.jobId(),
                        "HASH_MISMATCH",
                        funFile,
                        "Hash SHA-256 de FUN/VAR diverge do manifesto para " + spec.jobId());
            }
        }

        return true;
    }

    public static Path resolveCellDirectory(Path studyOutputDir, JobSpec spec) {
        String algTag = EvaluationBudgetProblem.toJMetalAlgorithmTag(spec.algorithmName());
        String problemTag = spec.problemName() + "_" + spec.m();
        return studyOutputDir.resolve("data").resolve(algTag).resolve(problemTag);
    }

    public static String canonicalJobKey(String problem, int m, String algorithm, int runId) {
        return StaticParams.normalizeProblemId(problem)
                + "|m="
                + m
                + "|alg="
                + EvaluationBudgetProblem.normalizeAlgorithmName(algorithm)
                + "|run="
                + runId;
    }

    private static List<DoubleSolution> reconstructSolutions(
            DoubleProblem problem, double[][] objectives, double[][] variables) {
        List<DoubleSolution> list = new ArrayList<>(objectives.length);
        for (int i = 0; i < objectives.length; i++) {
            // Reconstrói solução sem tocar JMetalRandom para preservar isolamento estrito
            // sob concorrência
            DoubleSolution s = ResultFrontContract.createUnseededSolution(problem);
            for (int j = 0; j < variables[i].length; j++) {
                s.variables().set(j, normalizeZero(variables[i][j]));
            }
            double[] targetObjs = s.objectives();
            for (int j = 0; j < objectives[i].length; j++) {
                targetObjs[j] = normalizeZero(objectives[i][j]);
            }
            list.add(s);
        }
        return list;
    }

    private static void exportManifestAtomically(Path manifestPath, List<ManifestEntry> entries)
            throws IOException {
        Path parent = manifestPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = manifestPath.resolveSibling(manifestPath.getFileName() + ".tmp");
        exportManifestToCsv(tempFile, entries);
        try {
            Files.move(
                    tempFile,
                    manifestPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tempFile, manifestPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeBinaryMatrix(Path target, double[][] matrix) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            out.writeInt(matrix.length);
            for (double[] row : matrix) {
                out.writeInt(row.length);
                for (double v : row) {
                    out.writeLong(Double.doubleToLongBits(normalizeZero(v)));
                }
            }
        }
    }

    private static double[][] readBinaryMatrix(Path source) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            int rows = in.readInt();
            double[][] matrix = new double[rows][];
            for (int i = 0; i < rows; i++) {
                int cols = in.readInt();
                double[] row = new double[cols];
                for (int j = 0; j < cols; j++) {
                    row[j] = Double.longBitsToDouble(in.readLong());
                }
                matrix[i] = row;
            }
            return matrix;
        }
    }

    private static String resolveJavaExecutable() {
        return ProcessHandle.current()
                .info()
                .command()
                .orElseGet(
                        () -> Paths.get(System.getProperty("java.home"), "bin", "java")
                                .toString());
    }

    private static void propagateSystemPropertyIfSet(List<String> cmd, String propKey) {
        String val = System.getProperty(propKey);
        if (val != null) {
            cmd.add("-D" + propKey + "=" + val);
        }
    }

    private static Thread startStreamDrainer(
            InputStream stream, StringBuilder sink, String threadName) {
        Thread t = new Thread(
                () -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            synchronized (sink) {
                                if (sink.length() < 16_384) {
                                    sink.append(line).append('\n');
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // Stream fechado no encerramento do subprocesso
                    }
                },
                threadName);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void terminateProcessTree(Process process) {
        try {
            process.toHandle()
                    .descendants()
                    .forEach(
                            ph -> {
                                try {
                                    ph.destroyForcibly();
                                } catch (Exception ignored) {
                                }
                            });
        } catch (Exception ignored) {
        }
        process.destroy();
        try {
            if (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(2_000L, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ie) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
        }
    }

    private static double normalizeZero(double value) {
        return (value == 0.0) ? 0.0 : value;
    }

    /**
     * Exporta uma lista de {@link ManifestEntry} para um arquivo CSV com cabeçalho
     * canônico
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
     * Calcula o hash SHA-256 hexadecimal (minúsculo, 64 caracteres) da
     * representação binária exata
     * IEEE 754 ({@link Double#doubleToLongBits(double)}) de uma matriz
     * {@code double[][]}.
     *
     * <p>
     * Diferencia inclusive dimensões da matriz (linhas e colunas codificadas no
     * cabeçalho do
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
            if (objs == null) {
                matrix[i] = new double[0];
            } else {
                double[] row = new double[objs.length];
                for (int j = 0; j < objs.length; j++) {
                    row[j] = normalizeZero(objs[j]);
                }
                matrix[i] = row;
            }
        }
        return matrix;
    }

    private static double[][] extractVariablesMatrix(List<DoubleSolution> solutions) {
        double[][] matrix = new double[solutions.size()][];
        for (int i = 0; i < solutions.size(); i++) {
            List<Double> vars = solutions.get(i).variables();
            double[] row = new double[vars.size()];
            for (int j = 0; j < vars.size(); j++) {
                row[j] = normalizeZero(vars.get(j));
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

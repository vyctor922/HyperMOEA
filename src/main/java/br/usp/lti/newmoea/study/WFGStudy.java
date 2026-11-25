package br.usp.lti.newmoea.study;

import br.usp.lti.newmoea.util.ArtifactManifestEntry;
import br.usp.lti.newmoea.util.EvaluationBudgetProblem;
import br.usp.lti.newmoea.util.HypervolumeResult;
import br.usp.lti.newmoea.util.IsolatedJobExecutor;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.BatchExecutionConfig;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.JobExecutionResult;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.JobSpec;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.StudyBatchReport;
import br.usp.lti.newmoea.util.MonteCarloHypervolume;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.FrontBoxDiagnostics;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.NormalizationBounds;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.NormalizationMode;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor.HvMethod;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor.MultiMethodHypervolumeEvaluation;
import br.usp.lti.newmoea.util.ResolvedFrontArtifact;
import br.usp.lti.newmoea.util.ResolvedWeightArtifact;
import br.usp.lti.newmoea.util.ResultFrontContract;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.espea.ESPEABuilder;
import org.uma.jmetal.algorithm.multiobjective.gwasfga.GWASFGA;
import org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD;
import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.spea2.SPEA2Builder;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder;
import org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder; //
import org.uma.jmetal.util.referencepoint.ReferencePointGenerator;
import org.uma.jmetal.lab.experiment.Experiment;
import org.uma.jmetal.lab.experiment.ExperimentBuilder;
import org.uma.jmetal.lab.experiment.component.impl.ComputeQualityIndicators;
import org.uma.jmetal.lab.experiment.component.impl.ExecuteAlgorithms;
import org.uma.jmetal.lab.experiment.component.impl.GenerateBoxplotsWithR;
import org.uma.jmetal.lab.experiment.component.impl.GenerateFriedmanTestTables;
import org.uma.jmetal.lab.experiment.component.impl.GenerateLatexTablesWithStatistics;
import org.uma.jmetal.lab.experiment.component.impl.GenerateWilcoxonTestTablesWithR;
import org.uma.jmetal.lab.experiment.util.ExperimentAlgorithm;
import org.uma.jmetal.lab.experiment.util.ExperimentProblem;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.DifferentialEvolutionCrossover;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
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
import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder; // implementação
import br.usp.lti.newmoea.statistics.StudyStatisticalAnalyzer;
import br.usp.lti.newmoea.util.ParetoFrontValidator;
import br.usp.lti.newmoea.util.PerformanceProfiler;
import br.usp.lti.newmoea.util.ReferenceSetValidator;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.R2; //
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.evaluator.impl.SequentialSolutionListEvaluator;

/**
 * Example of experimental study based on solving the problems (configured with
 * 3 objectives) with the algorithms NSGAII, SPEA2, and SMPSO
 * <p>
 * This org.uma.jmetal.experiment assumes that the reference Pareto front are
 * known and stored in files whose names are different from the default name
 * expected for every problem. While the default would be "problem_name.pf"
 * (e.g. DTLZ1.pf), the references are stored in files following the
 * nomenclature "problem_name.3D.pf" (e.g. DTLZ1.3D.pf). This is indicated when
 * creating the ExperimentProblem instance of each of the evaluated poblems by
 * using the method changeReferenceFrontTo()
 * <p>
 * Six quality indicators are used for performance assessment.
 * <p>
 * The steps to carry out the org.uma.jmetal.experiment are: 1. Configure the
 * org.uma.jmetal.experiment 2. Execute the algorithms 3. Compute que quality
 * indicators 4. Generate Latex tables reporting means and medians 5. Generate R
 * scripts to produce latex tables with the result of applying the Wilcoxon Rank
 * Sum Test 6. Generate Latex tables with the ranking obtained by applying the
 * Friedman test 7. Generate R scripts to obtain boxplots
 */
public class WFGStudy {

    // Permite configurar o número de execuções via JVM: -Druns=5
    public static int getIndependentRuns() {
        int runs = Integer.getInteger("runs", 30);

        if (runs <= 0) {
            throw new IllegalArgumentException(
                    "A propriedade 'runs' deve ser um inteiro positivo.");
        }

        return runs;
    }

    public static int getRunStart() {
        int runStart = Integer.getInteger("runStart", 0);
        if (runStart < 0) {
            throw new IllegalArgumentException(
                    "A propriedade 'runStart' deve ser um inteiro não-negativo: " + runStart);
        }
        return runStart;
    }

    public static long getBaseSeed() {
        return Long.getLong("study.baseSeed", IsolatedJobExecutor.DEFAULT_BASE_SEED);
    }

    public static final String INDICATOR_WORKERS_PROPERTY = "study.indicatorWorkers";
    public static final String EXACT_HV_PARALLEL_WORKERS_PROPERTY = "study.exactHvParallelWorkers";

    public static int getParallelWorkers() {
        int workers = Integer.getInteger("study.parallelWorkers", 1);
        if (workers < 1) {
            throw new IllegalArgumentException(
                    "A propriedade 'study.parallelWorkers' deve ser >= 1: " + workers);
        }
        return workers;
    }

    /**
     * Número de threads concorrentes para a etapa de cálculo de indicadores
     * ({@code -Dstudy.indicatorWorkers}). Quando omitida, reutiliza
     * {@link #getParallelWorkers()}.
     */
    public static int getIndicatorWorkers() {
        Integer raw = Integer.getInteger(INDICATOR_WORKERS_PROPERTY);
        if (raw == null) {
            return getParallelWorkers();
        }
        if (raw < 1) {
            throw new IllegalArgumentException(
                    "A propriedade '" + INDICATOR_WORKERS_PROPERTY + "' deve ser >= 1: " + raw);
        }
        return raw;
    }

    /**
     * Limite defensivo de subprocessos simultâneos {@code ExactHypervolumeWorker}
     * ({@code PISA}/{@code WFG}/{@code NHV}) em
     * {@code -Dstudy.exactHvParallelWorkers}:
     * quando omitido, usa até {@code indicatorWorkers} para \(m \le 5\) e limita
     * a no máximo {@code 2} simultâneos para \(m \ge 8\) contra explosão de RAM.
     */
    public static int getExactHvParallelWorkers(int indicatorWorkers, int m) {
        Integer configured = Integer.getInteger(EXACT_HV_PARALLEL_WORKERS_PROPERTY);
        if (configured != null) {
            if (configured < 1) {
                throw new IllegalArgumentException(
                        "A propriedade '"
                                + EXACT_HV_PARALLEL_WORKERS_PROPERTY
                                + "' deve ser >= 1: "
                                + configured);
            }
            return Math.min(indicatorWorkers, configured);
        }
        return (m >= 8) ? Math.min(indicatorWorkers, 2) : indicatorWorkers;
    }

    public static boolean isProcessIsolationEnabled() {
        return Boolean.getBoolean("study.processIsolation");
    }

    public static long getJobTimeoutMs() {
        long timeout = Long.getLong("study.jobTimeoutMs", BatchExecutionConfig.DEFAULT_JOB_TIMEOUT_MS);
        if (timeout <= 0L) {
            throw new IllegalArgumentException(
                    "A propriedade 'study.jobTimeoutMs' deve ser positiva: " + timeout);
        }
        return timeout;
    }

    /**
     * Retorna o override opcional de orçamento máximo de avaliações
     * ({@code -Dstudy.maxEvaluationsOverride})
     * destinado exclusivamente a smoke tests rápidos do entrypoint sem alterar o
     * orçamento canônico
     * \(N_{\text{config}}(m) \times G_{\max}(m, \text{problema})\) quando omitido
     * ({@code null}).
     */
    public static Integer getMaxEvaluationsOverride() {
        String raw = System.getProperty("study.maxEvaluationsOverride");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int parsed = Integer.parseInt(raw.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException(
                    "A propriedade 'study.maxEvaluationsOverride' deve ser positiva: " + parsed);
        }
        return parsed;
    }

    public static final int L = 20;
    public static final int[] M = getObjectives();

    public static int[] getObjectives() {
        String prop = System.getProperty("objectives");
        if (prop != null && !prop.isBlank()) {
            return Arrays.stream(prop.split(","))
                    .map(String::trim)
                    .mapToInt(Integer::parseInt)
                    .toArray();
        }
        return new int[] { 2, 3, 5, 8, 10, 15 };
    }

    private static boolean shouldIncludeProblem(String problemName) {
        String filter = System.getProperty("problem");
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return problemName.equalsIgnoreCase(filter) || problemName.startsWith(filter + "_");
    } //

    private static void addIfAllowed(List<ExperimentProblem<DoubleSolution>> problemList,
            ExperimentProblem<DoubleSolution> expProblem) {
        if (shouldIncludeProblem(expProblem.getTag())) {
            problemList.add(expProblem);
        }
    }

    // Diretório base dinâmico do projeto (portável para Windows, Linux, macOS)
    public static final String experimentBaseDirectory = System.getProperty("user.dir");
    public static final String StudyName = "WFGStudy";
    public static double epsilon = 0.01;

    /**
     * Constante legada mantida apenas para retrocompatibilidade e testes de
     * verificação de tarefas específicas (T03/T05/T11/T14/T19); não deve ser usada
     * como referência para execuções finais (que utilizam
     * {@link #DEFAULT_WEIGHTS_DIRECTORY}).
     */
    public static final String DEFAULT_LEGACY_WEIGHTS_DIRECTORY = "MOEAD_Weights";
    public static final String DEFAULT_WEIGHTS_DIRECTORY = "resources/weightVectorFiles";
    public static final String DEFAULT_REFERENCE_FRONT_DIRECTORY = "resources/referenceFronts";
    public static final String ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY = "study.allowMoeadWeightsInMemory";
    public static final String HV_METHODS_PROPERTY = "study.hvMethods";
    public static final String HV_METHODS_BY_M_PROPERTY = "study.hvMethodsByM";
    public static final String HV_POLICY_PROPERTY = "study.hvPolicy";
    public static final String HV_FALLBACK_ON_ALL_EXACT_TIMEOUT_PROPERTY = "study.hvFallbackToMcOnAllExactTimeout";
    public static final String HV_MC_SAMPLES_PROPERTY = "study.hvMcSamples";
    public static final String NORMALIZATION_MODE_PROPERTY = "study.normalizationMode";

    public static boolean shouldIncludeLegacyAlgorithms() {
        return Boolean.getBoolean("study.includeLegacyAlgorithms");
    }

    /**
     * Indica se a política opcional de dispensa de arquivo físico de pesos em
     * memória para o {@code MOEA/DD} (\(m = 2, 2 \le N \le 300\)) está ativa via
     * {@code -Dstudy.allowMoeadWeightsInMemory=true}. Desativada ({@code false})
     * por padrão.
     */
    public static boolean isAllowMoeadWeightsInMemoryEnabled() {
        return Boolean.getBoolean(ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY);
    }

    /**
     * Retorna o conjunto de métodos de hipervolume configurados para o perfil ativo
     * (padrão {@code PISA,MC};).
     */
    public static java.util.Set<HvMethod> getConfiguredHvMethods() {
        return ProcessIsolatedHypervolumeExecutor.parseConfiguredHvMethods(
                System.getProperty(HV_METHODS_PROPERTY),
                System.getProperty(HV_POLICY_PROPERTY));
    }

    /**
     * Indica se {@code -Dstudy.hvMethodsByM} foi informada (opt-in; desativada por
     * padrão).
     */
    public static boolean isHvMethodsByMEnabled() {
        String raw = System.getProperty(HV_METHODS_BY_M_PROPERTY);
        return raw != null && !raw.isBlank();
    }

    /**
     * Resolve o mapa por dimensão {@code m -> Set<HvMethod>} respeitando
     * {@code -Dstudy.hvMethodsByM} em ordem de avaliação (quando definida) e caindo
     * para
     * {@link #getConfiguredHvMethods()} quando opt-out ou fallback explícito.
     */
    public static Map<Integer, Set<HvMethod>> resolveConfiguredHvMethodsByM(int[] activeObjectives) {
        Set<HvMethod> defaultMethods = getConfiguredHvMethods();
        String byMRaw = System.getProperty(HV_METHODS_BY_M_PROPERTY);
        String methodsProp = System.getProperty(HV_METHODS_PROPERTY);
        String policyProp = System.getProperty(HV_POLICY_PROPERTY);
        boolean hasExplicitDefault = (methodsProp != null && !methodsProp.isBlank())
                || (policyProp != null && !policyProp.isBlank());
        return ProcessIsolatedHypervolumeExecutor.resolveHvMethodsByObjectiveCount(
                activeObjectives, byMRaw, defaultMethods, hasExplicitDefault);
    }

    /**
     * Indica se a opção desligável de fallback único para
     * {@link MonteCarloHypervolume} quando
     * apenas hipervolumes pesados ({@code PISA} e/ou {@code WFG}, sem {@code MC})
     * estão ativos e
     * <b>todos</b> atingem timeout/falha está habilitada ({@code true} por padrão).
     */
    public static boolean isHvFallbackToMcOnAllExactTimeoutEnabled() {
        String raw = System.getProperty(HV_FALLBACK_ON_ALL_EXACT_TIMEOUT_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return true;
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
                        + HV_FALLBACK_ON_ALL_EXACT_TIMEOUT_PROPERTY
                        + " (esperado true ou false): "
                        + raw);
    }

    public static long getHvTimeoutMs() {
        long timeout = Long.getLong(
                PerformanceProfiler.HV_TIMEOUT_MS_PROPERTY,
                PerformanceProfiler.DEFAULT_HV_TIMEOUT_MS);
        if (timeout <= 0L) {
            throw new IllegalArgumentException(
                    "A propriedade '"
                            + PerformanceProfiler.HV_TIMEOUT_MS_PROPERTY
                            + "' deve ser positiva: "
                            + timeout);
        }
        return timeout;
    }

    public static long getHvMcSampleCount() {
        long samples = Long.getLong(HV_MC_SAMPLES_PROPERTY, MonteCarloHypervolume.DEFAULT_SAMPLE_COUNT);
        if (samples <= 0L) {
            throw new IllegalArgumentException(
                    "A propriedade '" + HV_MC_SAMPLES_PROPERTY + "' deve ser positiva: " + samples);
        }
        return samples;
    }

    public static NormalizationMode getNormalizationMode() {
        String raw = System.getProperty(NORMALIZATION_MODE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return NormalizationMode.THEORETICAL_PARETO_BOUNDS;
        }
        return NormalizationMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public static String getStudyName() {
        return System.getProperty("study.outputDirectory", StudyName);
    }

    public static String getWeightsDirectory() {
        return System.getProperty("study.weightsDirectory", DEFAULT_WEIGHTS_DIRECTORY);
    }

    public static String getReferenceFrontDirectory() {
        return System.getProperty("study.referenceFrontDirectory", DEFAULT_REFERENCE_FRONT_DIRECTORY);
    }

    /**
     * Tolerância de soma para validação dos arquivos de pesos do MOEA/DD (acolhe a
     * substituição
     * \(0 \mapsto 10^{-5}\) presente em {@code MOEAD_Weights/}, cujo desvio máximo
     * é \(14 \times 10^{-5}\) em \(m=15\)).
     */
    public static final double MOEAD_WEIGHT_SUM_TOLERANCE = 2e-4;

    /**
     * Tolerância euclidiana para detecção de vetores duplicados nos arquivos de
     * pesos do MOEA/DD.
     */
    public static final double MOEAD_WEIGHT_DUPLICATE_TOLERANCE = 1e-7;

    /**
     * Layout resolvido do diretório de saída do estudo compatível com o contrato
     * real de
     * {@link ExperimentBuilder#setExperimentBaseDirectory(String)} e
     * {@link GenerateLatexTablesWithStatistics} no jMetal 7.8:
     * <ul>
     * <li>{@code ExperimentBuilder} concatena internamente
     * {@code parentBaseDirectory + "/" + experimentName} para formar
     * {@code experiment.getExperimentBaseDirectory()};</li>
     * <li>{@code GenerateLatexTablesWithStatistics} grava o arquivo LaTeX em
     * {@code experiment.getExperimentBaseDirectory() + "/latex/" + experiment.getExperimentName() + ".tex"},
     * exigindo que {@code experimentName} seja estritamente o identificador simples
     * (folha)
     * sem separadores de diretório ({@code /} ou {@code \}).</li>
     * </ul>
     *
     * @param outputDirectory     diretório completo normalizado onde {@code data/},
     *                            {@code latex/} e {@code R/} serão gravados
     * @param parentBaseDirectory diretório pai passado a
     *                            {@code ExperimentBuilder.setExperimentBaseDirectory(...)}
     * @param experimentName      nome simples (último segmento) passado ao
     *                            construtor
     *                            {@code new ExperimentBuilder<>(experimentName)}
     */
    public record StudyOutputLayout(
            Path outputDirectory,
            Path parentBaseDirectory,
            String experimentName) {

        public StudyOutputLayout {
            Objects.requireNonNull(outputDirectory, "outputDirectory não pode ser nulo.");
            Objects.requireNonNull(parentBaseDirectory, "parentBaseDirectory não pode ser nulo.");
            if (experimentName == null
                    || experimentName.isBlank()
                    || experimentName.contains("/")
                    || experimentName.contains("\\")) {
                throw new IllegalArgumentException(
                        "experimentName deve ser um identificador simples sem separadores de caminho: "
                                + experimentName);
            }
        }

        /**
         * Retorna {@code parentBaseDirectory} com barras normais para consumo pelo
         * {@link ExperimentBuilder}.
         */
        public String builderBaseDirectoryString() {
            return parentBaseDirectory.toString().replace('\\', '/');
        }
    }

    /**
     * Resolve o diretório de saída configurado em {@code study.outputDirectory}
     * (relativo simples,
     * relativo aninhado como {@code WFGStudy/tests/smoke} ou absoluto) contra
     * {@code workspaceBaseDir},
     * separando o diretório pai e o identificador simples exigidos pelo jMetal 7.8
     * e bloqueando
     * gravação nos diretórios legados protegidos ({@code MOEAD_Weights/},
     * {@code pareto_fronts/}, {@code pareto/}).
     *
     * @param workspaceBaseDir    diretório base de resolução para caminhos
     *                            relativos (se {@code null}, usa {@code user.dir})
     * @param configuredOutputDir caminho configurado (ex.: {@code "WFGStudy"},
     *                            {@code "WFGStudy/tests/smoke"} ou absoluto)
     * @return {@link StudyOutputLayout} validado
     */
    public static StudyOutputLayout resolveStudyOutputLayout(
            Path workspaceBaseDir, String configuredOutputDir) {
        if (configuredOutputDir == null || configuredOutputDir.isBlank()) {
            throw new IllegalArgumentException(
                    "A propriedade study.outputDirectory não pode ser nula ou vazia.");
        }
        Path base = (workspaceBaseDir != null
                ? workspaceBaseDir
                : Paths.get(experimentBaseDirectory))
                .toAbsolutePath()
                .normalize();
        Path rawPath = Paths.get(configuredOutputDir.trim().replace('\\', '/'));
        Path resolvedOutputDir = (rawPath.isAbsolute() ? rawPath : base.resolve(rawPath))
                .toAbsolutePath()
                .normalize();

        Path[] protectedLegacyDirs = new Path[] {
                base.resolve("MOEAD_Weights").normalize(),
                base.resolve("pareto_fronts").normalize(),
                base.resolve("pareto").normalize(),
                Paths.get("MOEAD_Weights").toAbsolutePath().normalize(),
                Paths.get("pareto_fronts").toAbsolutePath().normalize(),
                Paths.get("pareto").toAbsolutePath().normalize()
        };
        for (Path protectedDir : protectedLegacyDirs) {
            if (resolvedOutputDir.startsWith(protectedDir)) {
                throw new IllegalArgumentException(
                        "Proibido usar diretório legado protegido como study.outputDirectory: "
                                + configuredOutputDir);
            }
        }

        Path parentDir = resolvedOutputDir.getParent();
        Path leafName = resolvedOutputDir.getFileName();
        if (parentDir == null || leafName == null || leafName.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "study.outputDirectory deve possuir diretório pai e nome simples válido: "
                            + configuredOutputDir);
        }
        return new StudyOutputLayout(resolvedOutputDir, parentDir, leafName.toString());
    }

    public static void main(String[] args) throws IOException {
        StudyOutputLayout outputLayout = resolveStudyOutputLayout(
                Paths.get(experimentBaseDirectory), getStudyName());
        Files.createDirectories(outputLayout.outputDirectory());

        int[] activeObjectives = getObjectives();
        int independentRuns = getIndependentRuns();
        int runStart = getRunStart();
        long baseSeed = getBaseSeed();
        Integer budgetOverride = getMaxEvaluationsOverride();

        List<ExperimentProblem<DoubleSolution>> problemList = new ArrayList<>();
        for (int m : activeObjectives) {
            int k = 2 * (m - 1);
            addToProblemList(problemList, m, k);
        }

        PreflightResolutionContext preflightContext = runPreflightValidationAndResolveArtifacts(problemList, null);

        // Exporta o manifesto de artefatos efetivos a partir dos objetos resolvidos
        // ANTES da execução (T34)
        Path studyArtifactManifestPath = outputLayout.outputDirectory().resolve("artifact-manifest.csv");
        ReferenceSetValidator.exportManifestFromResolvedArtifacts(
                studyArtifactManifestPath,
                Paths.get(experimentBaseDirectory),
                preflightContext.resolvedWeights(),
                preflightContext.resolvedFronts(),
                "PREFLIGHT_PLANNED");

        // Snapshot não-destrutivo de resultados/sumários já existentes de outros lotes
        // ou células (T33 / T32 / T35)
        Path summaryCsvPath = outputLayout.outputDirectory().resolve("QualityIndicatorSummary.csv");
        Map<String, String> preservedSummaryRows = readSummaryRowsByKey(summaryCsvPath);
        Path hvAuditCsvPath = outputLayout.outputDirectory().resolve("hv_audit_manifest.csv");
        Map<String, String> preservedHvAuditRows = readHvAuditRowsByKey(hvAuditCsvPath);

        // Valida antecipadamente propriedades de indicadores antes de disparar os
        // algoritmos
        Map<Integer, Set<HvMethod>> hvMethodsByM = resolveConfiguredHvMethodsByM(activeObjectives);
        EnumSet<HvMethod> unionConfiguredHvMethods = EnumSet.noneOf(HvMethod.class);
        for (Set<HvMethod> perM : hvMethodsByM.values()) {
            unionConfiguredHvMethods.addAll(perM);
        }
        boolean hvFallbackToggle = isHvFallbackToMcOnAllExactTimeoutEnabled();
        long hvTimeoutMs = getHvTimeoutMs();
        long hvMcSamples = getHvMcSampleCount();
        NormalizationMode normalizationMode = getNormalizationMode();
        int parallelWorkers = getParallelWorkers();
        boolean processIsolation = isProcessIsolationEnabled();
        boolean lptScheduling = IsolatedJobExecutor.isLptSchedulingEnabled(parallelWorkers);
        int checkpointInterval = IsolatedJobExecutor.getConfiguredCheckpointInterval();
        int indicatorWorkers = getIndicatorWorkers();
        boolean usesHeavyExactHv = unionConfiguredHvMethods.stream().anyMatch(HvMethod::isHeavyExact);
        if (processIsolation || usesHeavyExactHv) {
            ProcessIsolatedHypervolumeExecutor.resolveConfiguredWorkerJvmArgs();
        }
        getExactHvParallelWorkers(indicatorWorkers, 2);

        // Execução isolada, determinística e consciente de orçamento/retomada via
        // IsolatedJobExecutor (LPT + checkpointing incremental + workerJvmArgs)
        List<JobSpec> mainJobs = buildStudyJobSpecs(
                problemList, runStart, independentRuns, baseSeed, budgetOverride);
        BatchExecutionConfig batchConfig = new BatchExecutionConfig(
                parallelWorkers,
                processIsolation,
                getJobTimeoutMs(),
                true,
                null,
                lptScheduling,
                checkpointInterval);
        StudyBatchReport batchReport;
        try {
            batchReport = IsolatedJobExecutor.executeAndPersistBatch(
                    mainJobs, outputLayout.outputDirectory(), batchConfig);

            // Quando algoritmos legados secundários estiverem explicitamente habilitados,
            // executa apenas os legados faltantes
            if (shouldIncludeLegacyAlgorithms()) {
                executeLegacyAlgorithmsOnly(outputLayout, problemList, runStart, independentRuns);
            }
        } catch (RuntimeException | IOException execError) {
            ReferenceSetValidator.exportManifestFromResolvedArtifacts(
                    studyArtifactManifestPath,
                    Paths.get(experimentBaseDirectory),
                    preflightContext.resolvedWeights(),
                    preflightContext.resolvedFronts(),
                    "OBSERVED_EXECUTION_FAILED");
            throw execError;
        }

        // Registra o consumo observado após a conclusão bem-sucedida da execução (sem
        // chamar planejamento de observação)
        ReferenceSetValidator.exportManifestFromResolvedArtifacts(
                studyArtifactManifestPath,
                Paths.get(experimentBaseDirectory),
                preflightContext.resolvedWeights(),
                preflightContext.resolvedFronts(),
                "OBSERVED_CONSUMED");

        // Exporta telemetria real de parede/CPU/frente completa por job e libera
        // batchReport da heap
        // antes de iniciar o cálculo paralelo de indicadores e estatística
        if (batchReport != null && !batchReport.newlyExecutedResults().isEmpty()) {
            PerformanceProfiler.exportStudyExecutionProfileCsv(
                    outputLayout.outputDirectory(),
                    batchReport.newlyExecutedResults(),
                    parallelWorkers,
                    processIsolation);
        }
        batchReport = null;

        List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> algorithmList = configureAlgorithmList(
                problemList, runStart, independentRuns, baseSeed, budgetOverride);

        int contiguousRunsFromZero = countContiguousCompletedRunsFromZero(
                outputLayout.outputDirectory(),
                problemList,
                algorithmList,
                runStart + independentRuns);

        // Cálculo contratual unificado de indicadores THEORETICAL_PARETO_BOUNDS,
        // isolamento de processo para hipervolumes pesados (PISA/WFG), série aproximada
        // própria (HV_MC), fallback único desligável.
        int evalLowerRun = (contiguousRunsFromZero >= 1) ? 0 : runStart;
        int evalUpperRunExclusive = Math.max(contiguousRunsFromZero, runStart + independentRuns);
        computeContractualIndicatorsAndExport(
                outputLayout.outputDirectory(),
                problemList,
                algorithmList,
                evalLowerRun,
                evalUpperRunExclusive,
                contiguousRunsFromZero,
                baseSeed,
                normalizationMode,
                unionConfiguredHvMethods,
                hvMethodsByM,
                hvFallbackToggle,
                hvTimeoutMs,
                hvMcSamples,
                indicatorWorkers,
                preservedSummaryRows,
                preservedHvAuditRows);

        if (!preservedSummaryRows.isEmpty()) {
            writeSummaryRowsAtomically(summaryCsvPath, preservedSummaryRows);
        }
        if (!preservedHvAuditRows.isEmpty()) {
            writeHvAuditRowsAtomically(hvAuditCsvPath, preservedHvAuditRows);
            writeHvFallbackWarningsReportIfAny(
                    outputLayout.outputDirectory().resolve("hv_fallback_warnings.md"),
                    preservedHvAuditRows);
        }

        // Pós-processamento estatístico integrado Kruskal–Wallis + Dunn–Holm + A12 por
        // célula, Friedman por blocos entre problemas e validação cruzada automática
        // contra Rscript (resiliente quando ausente).
        StudyStatisticalAnalyzer.analyzeAndExportStudy(outputLayout.outputDirectory(), null);

        if (contiguousRunsFromZero >= 1) {
            List<org.uma.jmetal.qualityindicator.QualityIndicator> completeNumericIndicators = selectCompleteFiniteIndicatorsForJMetalTables(
                    problemList,
                    algorithmList,
                    contiguousRunsFromZero,
                    unionConfiguredHvMethods,
                    preservedSummaryRows);

            if (!completeNumericIndicators.isEmpty()) {
                Experiment<DoubleSolution, List<DoubleSolution>> experiment = new ExperimentBuilder<DoubleSolution, List<DoubleSolution>>(
                        outputLayout.experimentName())
                        .setAlgorithmList(algorithmList)
                        .setProblemList(problemList)
                        .setReferenceFrontDirectory(getReferenceFrontDirectory())
                        .setExperimentBaseDirectory(outputLayout.builderBaseDirectoryString())
                        .setOutputParetoFrontFileName("FUN")
                        .setOutputParetoSetFileName("VAR")
                        .setIndicatorList(completeNumericIndicators)
                        .setIndependentRuns(contiguousRunsFromZero)
                        .setNumberOfCores(1)
                        .build();

                new GenerateLatexTablesWithStatistics(experiment).run();
                new GenerateWilcoxonTestTablesWithR<>(experiment).run();
                new GenerateFriedmanTestTables<>(experiment).run();
                new GenerateBoxplotsWithR<>(experiment).setRows(3).setColumns(3).setDisplayNotch().run();
            }
        }

        if (PerformanceProfiler.isProfilePerformanceEnabled()) {
            int warmup = Integer.getInteger("perf.warmup", 1);
            int perfRuns = Integer.getInteger("perf.runs", independentRuns);
            PerformanceProfiler.runStandardBenchmarkSuite(
                    PerformanceProfiler.DEFAULT_RESULTS_CSV_PATH, warmup, perfRuns);
        }
    }

    private static final String HV_AUDIT_CSV_HEADER = "algorithm,problem,m,run_id,normalization_mode,ref_point_norm,jacobian_det,"
            + "configured_hv_methods,fallback_toggle_enabled,"
            + "pisa_status,pisa_value,pisa_wall_ms,"
            + "wfg_status,wfg_value,wfg_wall_ms,"
            + "mc_computed_count,mc_status,mc_value,mc_se,mc_ci_lower,mc_ci_upper,mc_samples,mc_hits,mc_seed,mc_wall_ms,"
            + "effective_hv_value,effective_hv_source,fallback_triggered,fallback_warning,"
            + "points_outside_unit_box,points_outside_ref_point";

    private record IndicatorCalculators(
            InvertedGenerationalDistance igd2,
            InvertedGenerationalDistancePlus igdPlus,
            R2 r2) {
        static IndicatorCalculators createIsolated(double[][] normRefFront) {
            return new IndicatorCalculators(
                    new InvertedGenerationalDistance(normRefFront, 2.0),
                    new InvertedGenerationalDistancePlus(normRefFront),
                    ObjectiveNormalizationContract.createDeterministicR2(normRefFront, false));
        }
    }

    private static final class ProblemIndicatorContext {
        final String problemTag;
        final String canonicalProblemId;
        final int m;
        final Set<HvMethod> effectiveHvMethods;
        final String effectiveMethodsSpecLabel;
        final NormalizationBounds bounds;
        final double[][] normRefFront;
        final String normRefFormatted;
        final double[] unitOrigin;
        final double[] unitOnes;
        final ThreadLocal<IndicatorCalculators> calculators;
        double hvRefUnit = Double.NaN;

        ProblemIndicatorContext(
                String problemTag,
                String canonicalProblemId,
                int m,
                Set<HvMethod> effectiveHvMethods,
                String effectiveMethodsSpecLabel,
                NormalizationBounds bounds,
                double[][] normRefFront,
                String normRefFormatted,
                double[] unitOrigin,
                double[] unitOnes) {
            this.problemTag = problemTag;
            this.canonicalProblemId = canonicalProblemId;
            this.m = m;
            this.effectiveHvMethods = effectiveHvMethods;
            this.effectiveMethodsSpecLabel = effectiveMethodsSpecLabel;
            this.bounds = bounds;
            this.normRefFront = normRefFront;
            this.normRefFormatted = normRefFormatted;
            this.unitOrigin = unitOrigin;
            this.unitOnes = unitOnes;
            this.calculators = ThreadLocal.withInitial(
                    () -> IndicatorCalculators.createIsolated(this.normRefFront));
        }
    }

    private record PendingIndicatorTask(
            int originalIndex,
            ProblemIndicatorContext ctx,
            String algTag,
            int runId,
            Path funFile,
            double estimatedWeight) {
    }

    private record EvaluatedIndicatorOutput(
            int originalIndex,
            ProblemIndicatorContext ctx,
            String algTag,
            int runId,
            double igdVal,
            double igdPlusVal,
            double r2Val,
            MultiMethodHypervolumeEvaluation hvEval,
            HypervolumeResult nhvSolUnitRes,
            FrontBoxDiagnostics diag) {
    }

    private static void computeContractualIndicatorsAndExport(
            Path outputDir,
            List<ExperimentProblem<DoubleSolution>> problemList,
            List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> algorithmList,
            int runStartInclusive,
            int runEndExclusive,
            int contiguousRunsFromZero,
            long baseSeed,
            NormalizationMode normalizationMode,
            Set<HvMethod> unionConfiguredHvMethods,
            Map<Integer, Set<HvMethod>> hvMethodsByM,
            boolean hvFallbackToggle,
            long hvTimeoutMs,
            long hvMcSamples,
            int indicatorWorkers,
            Map<String, String> summaryRowsByKey,
            Map<String, String> hvAuditRowsByKey) throws IOException {
        List<String> distinctAlgTags = algorithmList.stream()
                .map(ExperimentAlgorithm::getAlgorithmTag)
                .distinct()
                .toList();

        ProcessIsolatedHypervolumeExecutor hvExecutor = new ProcessIsolatedHypervolumeExecutor(
                hvTimeoutMs,
                ProcessIsolatedHypervolumeExecutor.DEFAULT_GRACE_PERIOD_MS,
                hvMcSamples);

        Path refDir = Paths.get(getReferenceFrontDirectory());
        List<ProblemIndicatorContext> contexts = new ArrayList<>(problemList.size());
        List<PendingIndicatorTask> pendingTasks = new ArrayList<>();

        for (ExperimentProblem<DoubleSolution> expProblem : problemList) {
            int m = expProblem.getProblem().numberOfObjectives();
            Set<HvMethod> effectiveHvMethods = hvMethodsByM.getOrDefault(m, unionConfiguredHvMethods);
            String effectiveMethodsSpecLabel = ProcessIsolatedHypervolumeExecutor
                    .formatHvMethodsLabel(effectiveHvMethods);
            String canonicalProblemId = StaticParams.normalizeProblemId(expProblem.getProblem().name());
            Path refPath = refDir.resolve(expProblem.getReferenceFront());
            if (!Files.isRegularFile(refPath)) {
                refPath = Paths.get(expProblem.getReferenceFront());
            }
            double[][] rawRefFront = readAndValidateObjectiveMatrixCsv(refPath, m);
            NormalizationBounds bounds = ObjectiveNormalizationContract.resolveBounds(
                    canonicalProblemId, m, normalizationMode, rawRefFront);
            double[][] normRefFront = ObjectiveNormalizationContract.normalizeFront(rawRefFront, bounds);
            String normRefFormatted = formatDoubleVectorSemicolon(bounds.normalizedHvReferencePoint());

            boolean nhvEnabled = effectiveHvMethods.contains(HvMethod.NHV);
            double[] unitOrigin = nhvEnabled ? new double[m] : null;
            double[] unitOnes = nhvEnabled ? new double[m] : null;
            if (nhvEnabled) {
                Arrays.fill(unitOnes, 1.0);
            }
            ProblemIndicatorContext ctx = new ProblemIndicatorContext(
                    expProblem.getTag(),
                    canonicalProblemId,
                    m,
                    effectiveHvMethods,
                    effectiveMethodsSpecLabel,
                    bounds,
                    normRefFront,
                    normRefFormatted,
                    unitOrigin,
                    unitOnes);
            contexts.add(ctx);

            boolean hasHeavyExact = effectiveHvMethods.stream().anyMatch(HvMethod::isHeavyExact);
            double estWeight = (hasHeavyExact ? 20.0 : 1.0)
                    * m
                    * StaticParams.getPopulationSize(m);
            boolean anyPendingForProblem = false;

            for (String algTag : distinctAlgTags) {
                Path cellDir = outputDir.resolve("data").resolve(algTag).resolve(expProblem.getTag());
                for (int r = runStartInclusive; r < runEndExclusive; r++) {
                    Path funFile = cellDir.resolve("FUN" + r + ".csv");
                    if (!Files.isRegularFile(funFile)) {
                        continue;
                    }
                    if (canReuseExistingEvaluatedRunIndicators(
                            algTag,
                            expProblem.getTag(),
                            r,
                            normalizationMode,
                            effectiveMethodsSpecLabel,
                            hvFallbackToggle,
                            summaryRowsByKey,
                            hvAuditRowsByKey)) {
                        ensureInactiveHvSummaryRowsNa(
                                summaryRowsByKey,
                                algTag,
                                expProblem.getTag(),
                                r,
                                unionConfiguredHvMethods,
                                effectiveHvMethods);
                        continue;
                    }
                    anyPendingForProblem = true;
                    pendingTasks.add(new PendingIndicatorTask(
                            pendingTasks.size(), ctx, algTag, r, funFile, estWeight));
                }
            }

            if (nhvEnabled && anyPendingForProblem) {
                HypervolumeResult refUnitRes = hvExecutor.computeExactOnly(
                        normRefFront, unitOrigin, unitOnes, "PISA");
                if (refUnitRes != null
                        && (refUnitRes.getStatus() == HypervolumeResult.ExecutionStatus.SUCCESS_EXACT
                                || refUnitRes.getStatus() == HypervolumeResult.ExecutionStatus.EMPTY_FRONT)
                        && Double.isFinite(refUnitRes.getValue())
                        && refUnitRes.getValue() > 0.0) {
                    ctx.hvRefUnit = refUnitRes.getValue();
                }
            }
        }

        if (!pendingTasks.isEmpty()) {
            EvaluatedIndicatorOutput[] orderedOutputs = evaluatePendingIndicatorTasks(
                    pendingTasks,
                    hvExecutor,
                    baseSeed,
                    hvFallbackToggle,
                    indicatorWorkers);
            for (EvaluatedIndicatorOutput out : orderedOutputs) {
                applyEvaluatedIndicatorOutput(
                        out,
                        normalizationMode,
                        unionConfiguredHvMethods,
                        hvFallbackToggle,
                        summaryRowsByKey,
                        hvAuditRowsByKey);
            }
        }
        for (ProblemIndicatorContext ctx : contexts) {
            ctx.calculators.remove();
        }

        // Sincroniza os arquivos por célula em data/<algTag>/<problemTag>/<INDICATOR>
        // para runs contíguas 0..K-1 em ordem determinística
        if (contiguousRunsFromZero >= 1) {
            List<String> cellIndicators = new ArrayList<>();
            cellIndicators.add("IGD");
            cellIndicators.add("IGD+");
            cellIndicators.add("R2");
            if (unionConfiguredHvMethods.contains(HvMethod.PISA)) {
                cellIndicators.add("HV");
            }
            if (unionConfiguredHvMethods.contains(HvMethod.WFG)) {
                cellIndicators.add("HV_WFG");
            }
            if (unionConfiguredHvMethods.contains(HvMethod.MC)) {
                cellIndicators.add("HV_MC");
            }
            if (unionConfiguredHvMethods.contains(HvMethod.NHV)) {
                cellIndicators.add("NHV");
            }
            for (ExperimentProblem<DoubleSolution> expProblem : problemList) {
                for (String algTag : distinctAlgTags) {
                    Path cellDir = outputDir.resolve("data").resolve(algTag).resolve(expProblem.getTag());
                    for (String indName : cellIndicators) {
                        writePerCellIndicatorSeriesFile(
                                cellDir.resolve(indName),
                                algTag,
                                expProblem.getTag(),
                                indName,
                                contiguousRunsFromZero,
                                summaryRowsByKey);
                    }
                }
            }
        }
    }

    private static EvaluatedIndicatorOutput[] evaluatePendingIndicatorTasks(
            List<PendingIndicatorTask> pendingTasks,
            ProcessIsolatedHypervolumeExecutor hvExecutor,
            long baseSeed,
            boolean hvFallbackToggle,
            int indicatorWorkers) throws IOException {
        EvaluatedIndicatorOutput[] orderedOutputs = new EvaluatedIndicatorOutput[pendingTasks.size()];
        Semaphore lowDimExactPermits = new Semaphore(getExactHvParallelWorkers(indicatorWorkers, 2));
        Semaphore highDimExactPermits = new Semaphore(getExactHvParallelWorkers(indicatorWorkers, 8));

        if (indicatorWorkers <= 1 || pendingTasks.size() <= 1) {
            try {
                for (PendingIndicatorTask task : pendingTasks) {
                    orderedOutputs[task.originalIndex()] = evaluateSingleIndicatorTask(
                            task, hvExecutor, baseSeed, hvFallbackToggle, lowDimExactPermits, highDimExactPermits);
                }
                return orderedOutputs;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Cálculo de indicadores interrompido.", ie);
            }
        }

        List<PendingIndicatorTask> dispatchTasks = new ArrayList<>(pendingTasks);
        dispatchTasks.sort(
                Comparator.comparingDouble(PendingIndicatorTask::estimatedWeight)
                        .reversed()
                        .thenComparing(Comparator.comparingInt((PendingIndicatorTask t) -> t.ctx().m).reversed())
                        .thenComparing(PendingIndicatorTask::algTag)
                        .thenComparing(t -> t.ctx().problemTag)
                        .thenComparingInt(PendingIndicatorTask::runId));

        ExecutorService pool = Executors.newFixedThreadPool(indicatorWorkers);
        CompletionService<EvaluatedIndicatorOutput> completionService = new ExecutorCompletionService<>(pool);
        List<Future<EvaluatedIndicatorOutput>> futures = new ArrayList<>(dispatchTasks.size());
        try {
            for (PendingIndicatorTask task : dispatchTasks) {
                Callable<EvaluatedIndicatorOutput> callable = () -> evaluateSingleIndicatorTask(
                        task, hvExecutor, baseSeed, hvFallbackToggle, lowDimExactPermits, highDimExactPermits);
                futures.add(completionService.submit(callable));
            }
            for (int completed = 0; completed < dispatchTasks.size(); completed++) {
                EvaluatedIndicatorOutput out = completionService.take().get();
                orderedOutputs[out.originalIndex()] = out;
            }
            return orderedOutputs;
        } catch (InterruptedException ie) {
            for (Future<EvaluatedIndicatorOutput> f : futures) {
                f.cancel(true);
            }
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cálculo paralelo de indicadores interrompido.", ie);
        } catch (ExecutionException ee) {
            for (Future<EvaluatedIndicatorOutput> f : futures) {
                f.cancel(true);
            }
            Throwable cause = ee.getCause();
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(
                    "Falha em worker de indicador: " + cause.getMessage(), cause);
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5L, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static EvaluatedIndicatorOutput evaluateSingleIndicatorTask(
            PendingIndicatorTask task,
            ProcessIsolatedHypervolumeExecutor hvExecutor,
            long baseSeed,
            boolean hvFallbackToggle,
            Semaphore lowDimExactPermits,
            Semaphore highDimExactPermits) throws IOException, InterruptedException {
        ProblemIndicatorContext ctx = task.ctx();
        int m = ctx.m;
        double[][] rawFrontFromDisk = readAndValidateObjectiveMatrixCsv(task.funFile(), m);
        double[][] canonicalRawFront = extractNonDominatedUniqueObjectiveMatrix(rawFrontFromDisk);
        double[][] normFront = ObjectiveNormalizationContract.normalizeFront(canonicalRawFront, ctx.bounds);
        FrontBoxDiagnostics diag = ObjectiveNormalizationContract.diagnoseFront(canonicalRawFront, ctx.bounds);

        IndicatorCalculators calcs = ctx.calculators.get();
        double igdVal = calcs.igd2().compute(normFront);
        double igdPlusVal = calcs.igdPlus().compute(normFront);
        double r2Val = calcs.r2().compute(normFront);

        double[] boxOriginNorm = new double[m];
        for (int i = 0; i < normFront.length; i++) {
            for (int j = 0; j < m; j++) {
                if (normFront[i][j] < boxOriginNorm[j]) {
                    boxOriginNorm[j] = normFront[i][j];
                }
            }
        }

        long mcSeed = ProcessIsolatedHypervolumeExecutor.deriveDeterministicMcSeed(
                baseSeed, ctx.canonicalProblemId, m, task.algTag(), task.runId());

        boolean needsExactSemaphore = ctx.effectiveHvMethods.stream().anyMatch(HvMethod::isHeavyExact);
        Semaphore permitPool = needsExactSemaphore
                ? (m >= 8 ? highDimExactPermits : lowDimExactPermits)
                : null;
        MultiMethodHypervolumeEvaluation hvEval;
        HypervolumeResult nhvSolUnitRes = null;

        if (permitPool != null) {
            permitPool.acquire();
        }
        try {
            hvEval = hvExecutor.evaluateConfiguredHypervolumes(
                    normFront,
                    boxOriginNorm,
                    ctx.bounds.normalizedHvReferencePoint(),
                    ctx.effectiveHvMethods,
                    hvFallbackToggle,
                    mcSeed);
            if (ctx.effectiveHvMethods.contains(HvMethod.NHV)
                    && Double.isFinite(ctx.hvRefUnit)
                    && ctx.hvRefUnit > 0.0) {
                nhvSolUnitRes = hvExecutor.computeExactOnly(
                        normFront, ctx.unitOrigin, ctx.unitOnes, "PISA");
            }
        } finally {
            if (permitPool != null) {
                permitPool.release();
            }
        }

        return new EvaluatedIndicatorOutput(
                task.originalIndex(),
                ctx,
                task.algTag(),
                task.runId(),
                igdVal,
                igdPlusVal,
                r2Val,
                hvEval,
                nhvSolUnitRes,
                diag);
    }

    private static void applyEvaluatedIndicatorOutput(
            EvaluatedIndicatorOutput out,
            NormalizationMode normalizationMode,
            Set<HvMethod> unionConfiguredHvMethods,
            boolean hvFallbackToggle,
            Map<String, String> summaryRowsByKey,
            Map<String, String> hvAuditRowsByKey) {
        ProblemIndicatorContext ctx = out.ctx();
        String algTag = out.algTag();
        String problemTag = ctx.problemTag;
        int r = out.runId();
        Set<HvMethod> effectiveHvMethods = ctx.effectiveHvMethods;
        MultiMethodHypervolumeEvaluation hvEval = out.hvEval();

        putNumericSummaryRow(summaryRowsByKey, algTag, problemTag, "IGD", r, out.igdVal());
        putNumericSummaryRow(summaryRowsByKey, algTag, problemTag, "IGD+", r, out.igdPlusVal());
        putNumericSummaryRow(summaryRowsByKey, algTag, problemTag, "R2", r, out.r2Val());

        // 1. Série PISA exata (HV): somente valor exato ou NA (jamais 0.0 em
        // timeout/crash!)
        if (unionConfiguredHvMethods.contains(HvMethod.PISA)) {
            recordExactHvSummarySeriesRow(
                    summaryRowsByKey,
                    algTag,
                    problemTag,
                    "HV",
                    r,
                    effectiveHvMethods.contains(HvMethod.PISA),
                    hvEval.pisaResult());
        }

        // 2. Série WFG exata (HV_WFG): somente valor exato ou NA
        if (unionConfiguredHvMethods.contains(HvMethod.WFG)) {
            recordExactHvSummarySeriesRow(
                    summaryRowsByKey,
                    algTag,
                    problemTag,
                    "HV_WFG",
                    r,
                    effectiveHvMethods.contains(HvMethod.WFG),
                    hvEval.wfgResult());
        }

        // 3. Série secundária Monte Carlo uniforme (HV_MC)
        if (unionConfiguredHvMethods.contains(HvMethod.MC)) {
            if (effectiveHvMethods.contains(HvMethod.MC) && hvEval.mcResult() != null) {
                putNumericSummaryRow(
                        summaryRowsByKey, algTag, problemTag, "HV_MC", r, hvEval.mcResult().getValue());
            } else {
                putRawSummaryRow(summaryRowsByKey, algTag, problemTag, "HV_MC", r, "NA");
            }
        }

        // 4. Série NHV (opcional, não chamada no padrão PISA,MC; calculada apenas se
        // solicitada)
        if (unionConfiguredHvMethods.contains(HvMethod.NHV)) {
            HypervolumeResult solUnitRes = out.nhvSolUnitRes();
            if (effectiveHvMethods.contains(HvMethod.NHV)
                    && Double.isFinite(ctx.hvRefUnit)
                    && ctx.hvRefUnit > 0.0
                    && solUnitRes != null
                    && (solUnitRes.getStatus() == HypervolumeResult.ExecutionStatus.SUCCESS_EXACT
                            || solUnitRes.getStatus() == HypervolumeResult.ExecutionStatus.EMPTY_FRONT)) {
                double solUnitHv = (solUnitRes.getStatus() == HypervolumeResult.ExecutionStatus.EMPTY_FRONT)
                        ? 0.0
                        : solUnitRes.getValue();
                double nhvVal = ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1(
                        solUnitHv, ctx.hvRefUnit);
                putNumericSummaryRow(summaryRowsByKey, algTag, problemTag, "NHV", r, nhvVal);
            } else {
                putRawSummaryRow(summaryRowsByKey, algTag, problemTag, "NHV", r, "NA");
            }
        }

        // 5. Fallback único para configurações sem MC explícita
        if (!effectiveHvMethods.contains(HvMethod.MC)
                && hvEval.fallbackTriggered()
                && hvEval.mcResult() != null) {
            putNumericSummaryRow(
                    summaryRowsByKey,
                    algTag,
                    problemTag,
                    "HV_FALLBACK_MC",
                    r,
                    hvEval.mcResult().getValue());
        } else {
            summaryRowsByKey.remove(summaryRowKey(algTag, problemTag, "HV_FALLBACK_MC", r));
        }

        String auditKey = hvAuditRowKey(algTag, problemTag, r);
        String auditRow = formatHvAuditCsvRow(
                algTag,
                problemTag,
                ctx.m,
                r,
                normalizationMode,
                ctx.normRefFormatted,
                ctx.bounds.jacobianDeterminant(),
                ctx.effectiveMethodsSpecLabel,
                hvFallbackToggle,
                hvEval,
                out.diag());
        hvAuditRowsByKey.put(auditKey, auditRow);
    }

    private static void recordExactHvSummarySeriesRow(
            Map<String, String> summaryRowsByKey,
            String algTag,
            String problemTag,
            String indicatorName,
            int runId,
            boolean activeInDimension,
            HypervolumeResult exactResult) {
        if (activeInDimension
                && exactResult != null
                && (exactResult.getStatus() == HypervolumeResult.ExecutionStatus.SUCCESS_EXACT
                        || exactResult.getStatus() == HypervolumeResult.ExecutionStatus.EMPTY_FRONT)) {
            putNumericSummaryRow(
                    summaryRowsByKey, algTag, problemTag, indicatorName, runId, exactResult.getValue());
        } else {
            putRawSummaryRow(summaryRowsByKey, algTag, problemTag, indicatorName, runId, "NA");
        }
    }

    private static void ensureInactiveHvSummaryRowsNa(
            Map<String, String> summaryRowsByKey,
            String algTag,
            String problemTag,
            int runId,
            Set<HvMethod> unionConfiguredHvMethods,
            Set<HvMethod> effectiveHvMethods) {
        for (HvMethod method : unionConfiguredHvMethods) {
            if (effectiveHvMethods.contains(method)) {
                continue;
            }
            String indName = method.indicatorTag();
            summaryRowsByKey.putIfAbsent(
                    summaryRowKey(algTag, problemTag, indName, runId),
                    String.format(
                            Locale.ROOT,
                            "%s,%s,%s,%d,NA",
                            algTag,
                            problemTag,
                            indName,
                            runId));
        }
    }

    private static List<org.uma.jmetal.qualityindicator.QualityIndicator> selectCompleteFiniteIndicatorsForJMetalTables(
            List<ExperimentProblem<DoubleSolution>> problemList,
            List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> algorithmList,
            int contiguousRuns,
            java.util.Set<HvMethod> configuredHvMethods,
            Map<String, String> summaryRowsByKey) {
        List<String> distinctAlgTags = algorithmList.stream()
                .map(ExperimentAlgorithm::getAlgorithmTag)
                .distinct()
                .toList();

        List<org.uma.jmetal.qualityindicator.QualityIndicator> candidates = new ArrayList<>();
        if (configuredHvMethods.contains(HvMethod.PISA)) {
            candidates.add(new PISAHypervolume());
        }
        if (configuredHvMethods.contains(HvMethod.WFG)) {
            candidates.add(createNamedDescriptorIndicator("HV_WFG", false));
        }
        if (configuredHvMethods.contains(HvMethod.MC)) {
            candidates.add(createNamedDescriptorIndicator("HV_MC", false));
        }
        if (configuredHvMethods.contains(HvMethod.NHV)) {
            candidates.add(createNamedDescriptorIndicator("NHV", true));
        }
        candidates.add(new InvertedGenerationalDistance());
        candidates.add(new InvertedGenerationalDistancePlus());
        candidates.add(new R2());

        List<org.uma.jmetal.qualityindicator.QualityIndicator> complete = new ArrayList<>();
        for (org.uma.jmetal.qualityindicator.QualityIndicator ind : candidates) {
            boolean allFinite = true;
            for (ExperimentProblem<DoubleSolution> prob : problemList) {
                for (String algTag : distinctAlgTags) {
                    for (int r = 0; r < contiguousRuns; r++) {
                        String row = summaryRowsByKey.get(
                                summaryRowKey(algTag, prob.getTag(), ind.name(), r));
                        if (row == null) {
                            allFinite = false;
                            break;
                        }
                        String[] parts = row.split(",", -1);
                        if (parts.length < 5) {
                            allFinite = false;
                            break;
                        }
                        try {
                            double val = Double.parseDouble(parts[4].trim());
                            if (!Double.isFinite(val)) {
                                allFinite = false;
                                break;
                            }
                        } catch (NumberFormatException nfe) {
                            allFinite = false;
                            break;
                        }
                    }
                    if (!allFinite) {
                        break;
                    }
                }
                if (!allFinite) {
                    break;
                }
            }
            if (allFinite) {
                complete.add(ind);
            }
        }
        return complete;
    }

    private static org.uma.jmetal.qualityindicator.QualityIndicator createNamedDescriptorIndicator(
            String indicatorName, boolean lowerIsBetter) {
        return new org.uma.jmetal.qualityindicator.QualityIndicator() {
            @Override
            public double compute(double[][] matrix) {
                throw new UnsupportedOperationException(
                        "Descritor nominal de pós-processamento para " + indicatorName);
            }

            @Override
            public void referenceFront(double[][] referenceFront) {
                // No-op: descritor nominal para geração de tabelas
            }

            @Override
            public double[][] referenceFront() {
                return new double[0][0];
            }

            @Override
            public String name() {
                return indicatorName;
            }

            @Override
            public String description() {
                return indicatorName;
            }

            @Override
            public boolean isTheLowerTheIndicatorValueTheBetter() {
                return lowerIsBetter;
            }

            @Override
            public org.uma.jmetal.qualityindicator.QualityIndicator newInstance() {
                return this;
            }
        };
    }

    private static double[][] readAndValidateObjectiveMatrixCsv(Path csvFile, int expectedM)
            throws IOException {
        if (!Files.isRegularFile(csvFile) || Files.size(csvFile) == 0L) {
            throw new IllegalStateException("Arquivo de frente ausente ou vazio (0 bytes): " + csvFile);
        }
        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        List<double[]> rows = new ArrayList<>(lines.size());
        for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
            String trimmed = lines.get(lineIdx).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = trimmed.split("[,\\s]+");
            if (tokens.length != expectedM) {
                throw new IllegalStateException(
                        "Dimensão inválida em "
                                + csvFile
                                + " na linha "
                                + (lineIdx + 1)
                                + ": esperado m="
                                + expectedM
                                + ", obtido="
                                + tokens.length);
            }
            double[] vec = new double[expectedM];
            for (int j = 0; j < expectedM; j++) {
                double val = Double.parseDouble(tokens[j]);
                if (!Double.isFinite(val)) {
                    throw new IllegalStateException(
                            "Valor não finito (NaN/Inf) em "
                                    + csvFile
                                    + " na linha "
                                    + (lineIdx + 1)
                                    + ", coluna "
                                    + j
                                    + ": "
                                    + tokens[j]);
                }
                vec[j] = val;
            }
            rows.add(vec);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Nenhuma linha numérica válida encontrada em " + csvFile);
        }
        return rows.toArray(new double[0][]);
    }

    private static double[][] extractNonDominatedUniqueObjectiveMatrix(double[][] rawFront) {
        int n = rawFront.length;
        int m = rawFront[0].length;
        List<double[]> nonDom = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            boolean dominated = false;
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                if (strictlyDominatesVector(rawFront[j], rawFront[i], m)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                nonDom.add(rawFront[i]);
            }
        }
        nonDom.sort((a, b) -> {
            for (int k = 0; k < m; k++) {
                int cmp = Double.compare(a[k], b[k]);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });
        List<double[]> unique = new ArrayList<>(nonDom.size());
        for (double[] cand : nonDom) {
            boolean dup = false;
            for (double[] kept : unique) {
                double linf = 0.0;
                for (int k = 0; k < m; k++) {
                    linf = Math.max(linf, Math.abs(kept[k] - cand[k]));
                }
                if (linf <= ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                unique.add(cand.clone());
            }
        }
        return unique.toArray(new double[0][]);
    }

    private static boolean strictlyDominatesVector(double[] a, double[] b, int m) {
        boolean strictlyBetter = false;
        for (int k = 0; k < m; k++) {
            if (a[k] > b[k]) {
                return false;
            }
            if (a[k] < b[k]) {
                strictlyBetter = true;
            }
        }
        return strictlyBetter;
    }

    private static boolean canReuseExistingEvaluatedRunIndicators(
            String algTag,
            String problemTag,
            int runId,
            NormalizationMode mode,
            String methodsSpecLabel,
            boolean hvFallbackToggle,
            Map<String, String> summaryRowsByKey,
            Map<String, String> hvAuditRowsByKey) {
        String existingAudit = hvAuditRowsByKey.get(hvAuditRowKey(algTag, problemTag, runId));
        if (existingAudit == null) {
            return false;
        }
        String[] cols = splitCsvLineRespectingQuotes(existingAudit);
        if (cols.length < 9) {
            return false;
        }
        boolean sameContract = mode.name().equals(cols[4])
                && methodsSpecLabel.equals(cols[7])
                && Boolean.toString(hvFallbackToggle).equalsIgnoreCase(cols[8]);
        if (!sameContract) {
            return false;
        }
        return summaryRowsByKey.containsKey(summaryRowKey(algTag, problemTag, "IGD", runId))
                && summaryRowsByKey.containsKey(summaryRowKey(algTag, problemTag, "IGD+", runId))
                && summaryRowsByKey.containsKey(summaryRowKey(algTag, problemTag, "R2", runId));
    }

    private static void putNumericSummaryRow(
            Map<String, String> summaryRowsByKey,
            String algTag,
            String problemTag,
            String indicatorName,
            int runId,
            double value) {
        putRawSummaryRow(
                summaryRowsByKey,
                algTag,
                problemTag,
                indicatorName,
                runId,
                Double.toString(value));
    }

    private static void putRawSummaryRow(
            Map<String, String> summaryRowsByKey,
            String algTag,
            String problemTag,
            String indicatorName,
            int runId,
            String rawValueToken) {
        String key = summaryRowKey(algTag, problemTag, indicatorName, runId);
        String row = String.format(
                Locale.ROOT,
                "%s,%s,%s,%d,%s",
                algTag,
                problemTag,
                indicatorName,
                runId,
                rawValueToken);
        summaryRowsByKey.put(key, row);
    }

    private static void writePerCellIndicatorSeriesFile(
            Path targetFile,
            String algTag,
            String problemTag,
            String indicatorName,
            int contiguousRuns,
            Map<String, String> summaryRowsByKey) throws IOException {
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        Path temp = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            for (int r = 0; r < contiguousRuns; r++) {
                String row = summaryRowsByKey.get(summaryRowKey(algTag, problemTag, indicatorName, r));
                String valToken = "NA";
                if (row != null) {
                    String[] parts = row.split(",", -1);
                    if (parts.length >= 5) {
                        valToken = parts[4].trim();
                    }
                }
                writer.write(valToken);
                writer.newLine();
            }
        }
        try {
            Files.move(temp, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String formatDoubleVectorSemicolon(double[] vec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(Double.toString(vec[i]));
        }
        return sb.toString();
    }

    private static String formatHvValueOrNa(HypervolumeResult res) {
        if (res == null || !Double.isFinite(res.getValue())) {
            return "NA";
        }
        return Double.toString(res.getValue());
    }

    private static String formatHvAuditCsvRow(
            String algTag,
            String problemTag,
            int m,
            int runId,
            NormalizationMode mode,
            String normRefFormatted,
            double jacobianDet,
            String methodsSpecLabel,
            boolean hvFallbackToggle,
            MultiMethodHypervolumeEvaluation hvEval,
            FrontBoxDiagnostics diag) {
        HypervolumeResult pr = hvEval.pisaResult();
        HypervolumeResult wr = hvEval.wfgResult();
        HypervolumeResult mr = hvEval.mcResult();
        String pisaStatus = pr != null ? pr.getStatus().name() : "NOT_CONFIGURED";
        String pisaVal = formatHvValueOrNa(pr);
        long pisaWall = pr != null ? pr.getExecutionWallTimeMs() : 0L;

        String wfgStatus = wr != null ? wr.getStatus().name() : "NOT_CONFIGURED";
        String wfgVal = formatHvValueOrNa(wr);
        long wfgWall = wr != null ? wr.getExecutionWallTimeMs() : 0L;

        String mcStatus = mr != null ? mr.getStatus().name() : "NOT_EXECUTED";
        String mcVal = formatHvValueOrNa(mr);
        String mcSe = (mr != null && Double.isFinite(mr.getStandardError())) ? Double.toString(mr.getStandardError())
                : "NA";
        String mcCiL = (mr != null && Double.isFinite(mr.getCiLower())) ? Double.toString(mr.getCiLower()) : "NA";
        String mcCiU = (mr != null && Double.isFinite(mr.getCiUpper())) ? Double.toString(mr.getCiUpper()) : "NA";
        long mcSamples = mr != null ? mr.getSampleCount() : 0L;
        long mcHits = mr != null ? mr.getHitCount() : 0L;
        long mcSeed = mr != null ? mr.getSeed() : 0L;
        long mcWall = mr != null ? mr.getExecutionWallTimeMs() : 0L;

        String effVal = Double.isFinite(hvEval.effectiveHvValue())
                ? Double.toString(hvEval.effectiveHvValue())
                : "NA";
        String safeWarning = (hvEval.fallbackWarning() != null ? hvEval.fallbackWarning() : "")
                .replace("\"", "'");

        return String.format(
                Locale.ROOT,
                "%s,%s,%d,%d,%s,\"%s\",%s,%s,%b,%s,%s,%d,%s,%s,%d,%d,%s,%s,%s,%s,%s,%d,%d,%d,%d,%s,%s,%b,\"%s\",%d,%d",
                algTag,
                problemTag,
                m,
                runId,
                mode.name(),
                normRefFormatted,
                Double.toString(jacobianDet),
                methodsSpecLabel,
                hvFallbackToggle,
                pisaStatus,
                pisaVal,
                pisaWall,
                wfgStatus,
                wfgVal,
                wfgWall,
                hvEval.mcComputationCount(),
                mcStatus,
                mcVal,
                mcSe,
                mcCiL,
                mcCiU,
                mcSamples,
                mcHits,
                mcSeed,
                mcWall,
                effVal,
                hvEval.effectiveHvSource(),
                hvEval.fallbackTriggered(),
                safeWarning,
                diag.pointsOutsideUnitHvBox(),
                diag.pointsOutsideRawHvReferencePoint());
    }

    private static String hvAuditRowKey(String algTag, String problemTag, int runId) {
        return algTag + "|" + problemTag + "|" + runId;
    }

    private static Map<String, String> readHvAuditRowsByKey(Path hvAuditCsvPath) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.isRegularFile(hvAuditCsvPath) || Files.size(hvAuditCsvPath) == 0L) {
            return map;
        }
        List<String> lines = Files.readAllLines(hvAuditCsvPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("algorithm,")) {
                continue;
            }
            String[] cols = splitCsvLineRespectingQuotes(trimmed);
            if (cols.length >= 4) {
                map.put(
                        hvAuditRowKey(cols[0].trim(), cols[1].trim(), Integer.parseInt(cols[3].trim())),
                        trimmed);
            }
        }
        return map;
    }

    private static void writeHvAuditRowsAtomically(
            Path hvAuditCsvPath, Map<String, String> rowsByKey) throws IOException {
        Path parent = hvAuditCsvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = hvAuditCsvPath.resolveSibling(hvAuditCsvPath.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writer.write(HV_AUDIT_CSV_HEADER);
            writer.newLine();
            for (String row : rowsByKey.values()) {
                writer.write(row);
                writer.newLine();
            }
        }
        try {
            Files.move(temp, hvAuditCsvPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, hvAuditCsvPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeHvFallbackWarningsReportIfAny(
            Path warningReportPath, Map<String, String> hvAuditRowsByKey) throws IOException {
        List<String[]> warningRows = new ArrayList<>();
        for (String row : hvAuditRowsByKey.values()) {
            String[] cols = splitCsvLineRespectingQuotes(row);
            if (cols.length >= 29) {
                boolean fallbackTrig = Boolean.parseBoolean(cols[27].trim());
                String warnText = cols[28].trim();
                if (fallbackTrig || !warnText.isEmpty()) {
                    warningRows.add(cols);
                }
            }
        }
        if (warningRows.isEmpty()) {
            Files.deleteIfExists(warningReportPath);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Aviso de Fallback / Indisponibilidade de Hipervolume Exato").append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("| Algoritmo | Problema | Run | Métodos Configurados | Origem Efetiva | Valor Efetivo | Aviso |")
                .append(System.lineSeparator());
        sb.append("|---|---|---:|---|---|---:|---|").append(System.lineSeparator());
        for (String[] cols : warningRows) {
            sb.append("| ")
                    .append(cols[0]).append(" | ")
                    .append(cols[1]).append(" | ")
                    .append(cols[3]).append(" | ")
                    .append(cols[7]).append(" | ")
                    .append(cols[26]).append(" | ")
                    .append(cols[25]).append(" | ")
                    .append(cols[28]).append(" |")
                    .append(System.lineSeparator());
        }
        Path temp = warningReportPath.resolveSibling(warningReportPath.getFileName() + ".tmp");
        Files.writeString(temp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, warningReportPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, warningReportPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String[] splitCsvLineRespectingQuotes(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Constrói a lista determinística de {@link JobSpec} (com
     * {@code enforceHardBudget = true})
     * para os 6 algoritmos principais do estudo.
     */
    public static List<JobSpec> buildStudyJobSpecs(
            List<ExperimentProblem<DoubleSolution>> problemList,
            int runStart,
            int runCount,
            long baseSeed,
            Integer maxEvaluationsOverride) {
        Objects.requireNonNull(problemList, "problemList não pode ser nula.");
        List<JobSpec> specs = new ArrayList<>(
                problemList.size() * EvaluationBudgetProblem.MAIN_ALGORITHMS.size() * runCount);
        for (int run = runStart; run < runStart + runCount; run++) {
            for (ExperimentProblem<DoubleSolution> expProblem : problemList) {
                int m = expProblem.getProblem().numberOfObjectives();
                String problemName = StaticParams.normalizeProblemId(expProblem.getProblem().name());
                for (String algName : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
                    if (maxEvaluationsOverride == null) {
                        specs.add(JobSpec.ofCanonical(
                                problemName, m, algName, run, baseSeed, true));
                    } else {
                        int nAlloc = EvaluationBudgetProblem.getAllocatedPopulationSize(algName, m);
                        int customGens = EvaluationBudgetProblem.computeExactGenerationsForBudget(
                                maxEvaluationsOverride, nAlloc);
                        specs.add(JobSpec.ofCustomBudget(
                                problemName,
                                m,
                                algName,
                                run,
                                baseSeed,
                                customGens,
                                maxEvaluationsOverride,
                                true));
                    }
                }
            }
        }
        return specs;
    }

    private static int countContiguousCompletedRunsFromZero(
            Path outputDir,
            List<ExperimentProblem<DoubleSolution>> problemList,
            List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> algorithmList,
            int maxUpperBoundExclusive) {
        if (problemList.isEmpty() || algorithmList.isEmpty() || maxUpperBoundExclusive <= 0) {
            return 0;
        }
        List<String> distinctAlgTags = algorithmList.stream()
                .map(ExperimentAlgorithm::getAlgorithmTag)
                .distinct()
                .toList();
        int contiguous = 0;
        for (int r = 0; r < maxUpperBoundExclusive; r++) {
            boolean allExist = true;
            for (String algTag : distinctAlgTags) {
                for (ExperimentProblem<DoubleSolution> prob : problemList) {
                    Path fun = outputDir.resolve("data")
                            .resolve(algTag)
                            .resolve(prob.getTag())
                            .resolve("FUN" + r + ".csv");
                    Path var = outputDir.resolve("data")
                            .resolve(algTag)
                            .resolve(prob.getTag())
                            .resolve("VAR" + r + ".csv");
                    if (!Files.isRegularFile(fun) || !Files.isRegularFile(var)) {
                        allExist = false;
                        break;
                    }
                }
                if (!allExist) {
                    break;
                }
            }
            if (!allExist) {
                break;
            }
            contiguous++;
        }
        return contiguous;
    }

    private static Map<String, String> readSummaryRowsByKey(Path summaryCsvPath) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.isRegularFile(summaryCsvPath) || Files.size(summaryCsvPath) == 0L) {
            return map;
        }
        List<String> lines = Files.readAllLines(summaryCsvPath, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Algorithm,")) {
                continue;
            }
            String[] parts = trimmed.split(",", -1);
            if (parts.length >= 5) {
                String key = summaryRowKey(
                        parts[0].trim(),
                        parts[1].trim(),
                        parts[2].trim(),
                        Integer.parseInt(parts[3].trim()));
                map.put(key, trimmed);
            }
        }
        return map;
    }

    private static void writeSummaryRowsAtomically(
            Path summaryCsvPath, Map<String, String> rowsByKey) throws IOException {
        Path parent = summaryCsvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = summaryCsvPath.resolveSibling(summaryCsvPath.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writer.write("Algorithm,Problem,IndicatorName,ExecutionId,IndicatorValue");
            writer.newLine();
            for (String row : rowsByKey.values()) {
                writer.write(row);
                writer.newLine();
            }
        }
        try {
            Files.move(temp, summaryCsvPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, summaryCsvPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String summaryRowKey(
            String algTag, String problemTag, String indicatorName, int runId) {
        return algTag + "|" + problemTag + "|" + indicatorName + "|" + runId;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void executeLegacyAlgorithmsOnly(
            StudyOutputLayout outputLayout,
            List<ExperimentProblem<DoubleSolution>> problemList,
            int runStart,
            int independentRuns) {
        List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> legacyList = new ArrayList<>();
        for (int run = runStart; run < runStart + independentRuns; run++) {
            for (ExperimentProblem<DoubleSolution> expProblem : problemList) {
                int m = expProblem.getProblem().numberOfObjectives();
                String pName = StaticParams.normalizeProblemId(expProblem.getProblem().name());
                int popSize = StaticParams.getPopulationSize(m);
                Problem p1 = IsolatedJobExecutor.createFreshProblemInstance(pName, m);
                Problem p2 = IsolatedJobExecutor.createFreshProblemInstance(pName, m);
                Problem p3 = IsolatedJobExecutor.createFreshProblemInstance(pName, m);
                Problem p4 = IsolatedJobExecutor.createFreshProblemInstance(pName, m);
                Problem p5 = IsolatedJobExecutor.createFreshProblemInstance(pName, m);
                legacyList.add(new ExperimentAlgorithm<>(
                        buildNSGAII(p1, popSize, new SBXCrossover(1.0, 30.0),
                                new PolynomialMutation(1.0 / p1.numberOfVariables(), 20.0)),
                        expProblem, run));
                legacyList.add(new ExperimentAlgorithm<>(
                        buildSPEA2(p2, popSize, new SBXCrossover(1.0, 30.0),
                                new PolynomialMutation(1.0 / p2.numberOfVariables(), 20.0)),
                        expProblem, run));
                legacyList.add(new ExperimentAlgorithm<>(
                        buildMOEADDRA(p3, popSize, new DifferentialEvolutionCrossover(),
                                new PolynomialMutation(1.0 / p3.numberOfVariables(), 20.0)),
                        expProblem, run));
                legacyList.add(new ExperimentAlgorithm<>(
                        buildESPEA(p4, popSize, new SBXCrossover(1.0, 30.0),
                                new PolynomialMutation(1.0 / p4.numberOfVariables(), 20.0)),
                        expProblem, run));
                legacyList.add(new ExperimentAlgorithm<>(
                        buildGWASFGA(p5, popSize, new SBXCrossover(1.0, 30.0),
                                new PolynomialMutation(1.0 / p5.numberOfVariables(), 20.0)),
                        expProblem, run));
            }
        }
        Experiment<DoubleSolution, List<DoubleSolution>> legacyExp = new ExperimentBuilder<DoubleSolution, List<DoubleSolution>>(
                outputLayout.experimentName())
                .setAlgorithmList(legacyList)
                .setProblemList(problemList)
                .setReferenceFrontDirectory(getReferenceFrontDirectory())
                .setExperimentBaseDirectory(outputLayout.builderBaseDirectoryString())
                .setOutputParetoFrontFileName("FUN")
                .setOutputParetoSetFileName("VAR")
                .setIndependentRuns(runStart + independentRuns)
                .setNumberOfCores(1)
                .build();
        new ExecuteAlgorithms<>(legacyExp).run();
    }

    // static void addToProblemList(List problemList, int m, int k) {
    // problemList.add(new ExperimentProblem<>(new WFG1(k, L, m), "WFG1_" +
    // m).setReferenceFront("WFG1." + m + "D.csv"));

    // problemList.add(new ExperimentProblem<>(new WFG2(k, L, m), "WFG2_" +
    // m).setReferenceFront("WFG2." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new WFG3(k, L, m), "WFG3_" +
    // m).setReferenceFront("WFG3." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new WFG5(k, L, m), "WFG5_" +
    // m).setReferenceFront("WFG5." + m + "D.csv"));

    // problemList.add(new ExperimentProblem<>(new WFG4(k, L, m), "WFG4_" +
    // m).setReferenceFront("WFG4." + m + "D.csv"));

    // problemList.add(new ExperimentProblem<>(new WFG6(k, L, m), "WFG6_" +
    // m).setReferenceFront("WFG6." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new WFG7(k, L, m), "WFG7_" +
    // m).setReferenceFront("WFG7." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new WFG8(k, L, m), "WFG8_" +
    // m).setReferenceFront("WFG8." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new WFG9(k, L, m), "WFG9_" +
    // m).setReferenceFront("WFG9." + m + "D.csv"));

    // // problemList.add(new ExperimentProblem<>(new UF1(k, L, m), "UF1_" +
    // m).setReferenceFront("UF1." + m + "D.csv"));
    // // problemList.add(new ExperimentProblem<>(new ZDT1(k, L, m), "ZDT1_" +
    // m).setReferenceFront("ZDT1." + m + "D.csv"));

    // if (m <= 3) {
    // problemList.add(new ExperimentProblem<>(new DTLZ1(L, m), "DTLZ1_" +
    // m).setReferenceFront("DTLZ1." + m + "D.csv"));

    // problemList.add(new ExperimentProblem<>(new DTLZ2(L, m), "DTLZ2_" +
    // m).setReferenceFront("DTLZ2." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new DTLZ3(L, m), "DTLZ3_" +
    // m).setReferenceFront("DTLZ3." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new DTLZ4(L, m), "DTLZ4_" +
    // m).setReferenceFront("DTLZ4." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new DTLZ5(L, m), "DTLZ5_" +
    // m).setReferenceFront("DTLZ5." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new DTLZ6(L, m), "DTLZ6_" +
    // m).setReferenceFront("DTLZ6." + m + "D.csv"));
    // problemList.add(new ExperimentProblem<>(new DTLZ7(L, m), "DTLZ7_" +
    // m).setReferenceFront("DTLZ7." + m + "D.csv"));
    // }

    // }

    /**
     * Resolve o caminho relativo da frente de referência para o estudo seguindo a
     * hierarquia canônica:
     * 1. {@code resources/referenceFronts/};
     * 2. {@code resources/referenceFronts/pf_gen/};
     * 3. Geração matemática sob demanda em
     * {@code resources/referenceFronts/pf_gen/}.
     *
     * <p>
     * Caso {@code -DreplaceZeroFronts=true}, resolve a variante com substituição de
     * coordenadas zero em
     * {@code resources/referenceFronts/pf_lowzero_gen/}, isolada de
     * {@code pf_gen/}.
     *
     * @param problemName nome do problema (ex.: {@code "WFG1"}, {@code "DTLZ2"})
     * @param m           número de objetivos
     * @return caminho relativo padronizado para consumo pelo jMetal
     */
    public static String resolveReferenceFrontForStudy(String problemName, int m) {
        return resolveReferenceFrontForStudy(problemName, m, null);
    }

    /**
     * Resolve o caminho relativo da frente de referência para o estudo com suporte
     * granular por
     * algoritmo à propriedade {@code -DreplaceZeroFronts}.
     */
    public static String resolveReferenceFrontForStudy(String problemName, int m, String algorithmName) {
        return resolveReferenceFrontForStudy(problemName, m, algorithmName, null);
    }

    /**
     * Resolve o caminho relativo da frente de referência respeitando
     * simultaneamente o override {@code -Dstudy.referenceFrontDirectory}, o
     * {@code baseDir} hermético (quando informado, sem mutar {@code user.dir}) e o
     * escopo granular de {@code -DreplaceZeroFronts} (T34).
     */
    public static String resolveReferenceFrontForStudy(
            String problemName, int m, String algorithmName, Path baseDir) {
        boolean useLowZero = ParetoFrontValidator.isReplaceZeroFrontsEnabled(algorithmName);
        Path configuredDir = Paths.get(getReferenceFrontDirectory());
        Path activeRefDir = (baseDir != null && !configuredDir.isAbsolute())
                ? baseDir.resolve(configuredDir)
                : configuredDir;
        Path pfGenDir = activeRefDir.resolve("pf_gen");
        Path pfLowZeroDir = activeRefDir.resolve("pf_lowzero_gen");

        Path refFile = ParetoFrontValidator.resolveCanonicalReferenceFront(
                problemName,
                m,
                activeRefDir,
                pfGenDir,
                pfLowZeroDir,
                useLowZero,
                true);
        Path normRefFile = refFile.toAbsolutePath().normalize();
        Path normActiveDir = activeRefDir.toAbsolutePath().normalize();
        if (normRefFile.startsWith(normActiveDir)) {
            return normActiveDir.relativize(normRefFile).toString().replace('\\', '/');
        }
        return refFile.toString().replace('\\', '/');
    }

    private static void addProblemWithResolvedFrontIfAllowed(
            List<ExperimentProblem<DoubleSolution>> problemList,
            Problem<DoubleSolution> problem,
            String problemName,
            int m) {
        String tag = problemName + "_" + m;
        if (shouldIncludeProblem(tag)) {
            problemList.add(
                    new ExperimentProblem<>(problem, tag)
                            .setReferenceFront(resolveReferenceFrontForStudy(problemName, m)));
        }
    }

    static void addToProblemList(List<ExperimentProblem<DoubleSolution>> problemList, int m, int k) {
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG1(k, L, m), "WFG1", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG2(k, L, m), "WFG2", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG3(k, L, m), "WFG3", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG4(k, L, m), "WFG4", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG5(k, L, m), "WFG5", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG6(k, L, m), "WFG6", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG7(k, L, m), "WFG7", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG8(k, L, m), "WFG8", m);
        addProblemWithResolvedFrontIfAllowed(problemList, new WFG9(k, L, m), "WFG9", m);

        if (m <= 3) {
            addProblemWithResolvedFrontIfAllowed(problemList, new DTLZ1(L, m), "DTLZ1", m);
            addProblemWithResolvedFrontIfAllowed(problemList, new DTLZ2(L, m), "DTLZ2", m);
            addProblemWithResolvedFrontIfAllowed(problemList, new DTLZ3(L, m), "DTLZ3", m);
            addProblemWithResolvedFrontIfAllowed(problemList, new DTLZ4(L, m), "DTLZ4", m);
            addProblemWithResolvedFrontIfAllowed(problemList, new DTLZ5(L, m), "DTLZ5", m);
            addProblemWithResolvedFrontIfAllowed(problemList, new DTLZ6(L, m), "DTLZ6", m);
            addProblemWithResolvedFrontIfAllowed(problemList, new DTLZ7(L, m), "DTLZ7", m);
        } else if (ParetoFrontValidator.isGenerateAllReferenceFrontsEnabled()
                || (System.getProperty("problem") != null && !System.getProperty("problem").isBlank())) {
            addAnalyticalDtlzProblemsForManyObjectives(problemList, m);
        }
    }

    /**
     * Quando {@code -DgenerateAllReferenceFronts=true}, resolve matematicamente as
     * frentes
     * analíticas de {@code DTLZ1–DTLZ7} para \(m > 3\) respeitando a hierarquia
     * canônica
     * (1: {@code resources/referenceFronts/}, 2: {@code pf_gen/}, 3: geração por
     * código)
     * antes de habilitar cada combinação no estudo.
     */
    private static void addAnalyticalDtlzProblemsForManyObjectives(
            List<ExperimentProblem<DoubleSolution>> problemList, int m) {
        Problem<DoubleSolution>[] dtlzProblems = new Problem[] {
                new DTLZ1(L, m),
                new DTLZ2(L, m),
                new DTLZ3(L, m),
                new DTLZ4(L, m),
                new DTLZ5(L, m),
                new DTLZ6(L, m),
                new DTLZ7(L, m)
        };
        for (int idx = 0; idx < dtlzProblems.length; idx++) {
            int p = idx + 1;
            String problemName = "DTLZ" + p;
            String tag = problemName + "_" + m;
            if (!shouldIncludeProblem(tag)) {
                continue;
            }
            addIfAllowed(
                    problemList,
                    new ExperimentProblem<>(dtlzProblems[idx], tag)
                            .setReferenceFront(resolveReferenceFrontForStudy(problemName, m)));
        }
    }

    /**
     * The algorithm list is composed of pairs
     * {@link Algorithm} + {@link Problem} which form part of a
     * {@link ExperimentAlgorithm}, which is a decorator for class
     * {@link Algorithm}.
     */
    /**
     * Adaptador lazy e isolado de {@link Algorithm} que delega a execução sob
     * demanda a
     * {@link IsolatedJobExecutor#executeJobInProcess(JobSpec)} com semente
     * determinística e
     * {@link EvaluationBudgetProblem} estrito, evitando alocação antecipada de
     * estado mutável
     * compartilhado em {@link #configureAlgorithmList(List)}.
     */
    static final class LazyIsolatedAlgorithmAdapter implements Algorithm<List<DoubleSolution>> {

        private static final long serialVersionUID = 1L;

        private final JobSpec spec;
        private final String algorithmTag;
        private List<DoubleSolution> cachedResult = List.of();

        LazyIsolatedAlgorithmAdapter(JobSpec spec) {
            this.spec = Objects.requireNonNull(spec, "spec não pode ser nulo.");
            this.algorithmTag = EvaluationBudgetProblem.toJMetalAlgorithmTag(spec.algorithmName());
        }

        public JobSpec getJobSpec() {
            return spec;
        }

        @Override
        public void run() {
            JobExecutionResult res = IsolatedJobExecutor.executeJobInProcess(spec);
            org.uma.jmetal.problem.doubleproblem.DoubleProblem freshProblem = IsolatedJobExecutor
                    .createFreshProblemInstance(spec.problemName(), spec.m());
            double[][] objs = res.rawObjectives();
            double[][] vars = res.rawVariables();
            List<DoubleSolution> list = new ArrayList<>(objs.length);
            for (int i = 0; i < objs.length; i++) {
                DoubleSolution sol = ResultFrontContract.createUnseededSolution(freshProblem);
                for (int j = 0; j < vars[i].length; j++) {
                    sol.variables().set(j, vars[i][j]);
                }
                System.arraycopy(objs[i], 0, sol.objectives(), 0, objs[i].length);
                list.add(sol);
            }
            this.cachedResult = List.copyOf(list);
        }

        @Override
        public List<DoubleSolution> result() {
            return cachedResult;
        }

        @Override
        public String name() {
            return algorithmTag;
        }

        @Override
        public String description() {
            return "Isolated budget-enforced adapter for " + spec.algorithmName();
        }
    }

    static List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> configureAlgorithmList(
            List<ExperimentProblem<DoubleSolution>> problemList) {
        return configureAlgorithmList(
                problemList,
                getRunStart(),
                getIndependentRuns(),
                getBaseSeed(),
                getMaxEvaluationsOverride());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> configureAlgorithmList(
            List<ExperimentProblem<DoubleSolution>> problemList,
            int runStart,
            int runCount,
            long baseSeed,
            Integer maxEvaluationsOverride) {
        List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> algorithms = new ArrayList<>();
        for (int run = runStart; run < runStart + runCount; run++) {
            for (int i = 0; i < problemList.size(); i++) {
                ExperimentProblem<DoubleSolution> expProblem = problemList.get(i);
                int m = expProblem.getProblem().numberOfObjectives();
                int populationSize = StaticParams.getPopulationSize(m);
                String problemName = StaticParams.normalizeProblemId(expProblem.getProblem().name());

                if (shouldIncludeLegacyAlgorithms()) {
                    Problem p1 = IsolatedJobExecutor.createFreshProblemInstance(problemName, m);
                    Problem p2 = IsolatedJobExecutor.createFreshProblemInstance(problemName, m);
                    Problem p3 = IsolatedJobExecutor.createFreshProblemInstance(problemName, m);
                    algorithms.add(new ExperimentAlgorithm<>(
                            buildNSGAII(p1, populationSize, new SBXCrossover(1.0, 30.0),
                                    new PolynomialMutation(1.0 / p1.numberOfVariables(), 20.0)),
                            expProblem, run));
                    algorithms.add(new ExperimentAlgorithm<>(
                            buildSPEA2(p2, populationSize, new SBXCrossover(1.0, 30.0),
                                    new PolynomialMutation(1.0 / p2.numberOfVariables(), 20.0)),
                            expProblem, run));
                    algorithms.add(new ExperimentAlgorithm<>(
                            buildMOEADDRA(p3, populationSize, new DifferentialEvolutionCrossover(),
                                    new PolynomialMutation(1.0 / p3.numberOfVariables(), 20.0)),
                            expProblem, run));
                }

                for (String algName : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
                    JobSpec spec;
                    if (maxEvaluationsOverride == null) {
                        spec = JobSpec.ofCanonical(problemName, m, algName, run, baseSeed, true);
                    } else {
                        int nAlloc = EvaluationBudgetProblem.getAllocatedPopulationSize(algName, m);
                        int customGens = EvaluationBudgetProblem.computeExactGenerationsForBudget(
                                maxEvaluationsOverride, nAlloc);
                        spec = JobSpec.ofCustomBudget(
                                problemName, m, algName, run, baseSeed, customGens, maxEvaluationsOverride, true);
                    }
                    LazyIsolatedAlgorithmAdapter adapter = new LazyIsolatedAlgorithmAdapter(spec);
                    algorithms.add(new ExperimentAlgorithm<>(
                            adapter, adapter.name(), expProblem, run));
                }

                if (shouldIncludeLegacyAlgorithms()) {
                    Problem p4 = IsolatedJobExecutor.createFreshProblemInstance(problemName, m);
                    Problem p5 = IsolatedJobExecutor.createFreshProblemInstance(problemName, m);
                    algorithms.add(new ExperimentAlgorithm<>(
                            buildESPEA(p4, populationSize, new SBXCrossover(1.0, 30.0),
                                    new PolynomialMutation(1.0 / p4.numberOfVariables(), 20.0)),
                            expProblem, run));
                    algorithms.add(new ExperimentAlgorithm<>(
                            buildGWASFGA(p5, populationSize, new SBXCrossover(1.0, 30.0),
                                    new PolynomialMutation(1.0 / p5.numberOfVariables(), 20.0)),
                            expProblem, run));
                }
            }
        }
        return algorithms;
    }

    public static Algorithm buildNSGAIII(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        int m = problem.numberOfObjectives();
        int outerDivisions = StaticParams.getOuterDivisions(m);
        int innerDivisions = StaticParams.getInnerDivisions(m);

        int expectedPoints = ReferenceSetValidator.expectedTwoLayerPoints(m, outerDivisions, innerDivisions);
        if (populationSize != expectedPoints) {
            throw new IllegalArgumentException(
                    String.format(
                            "Inconsistência na configuração do NSGA-III para m=%d: populationSize=%d difere do total de pontos de referência esperados=%d (H1=%d, H2=%d).",
                            m, populationSize, expectedPoints, outerDivisions, innerDivisions));
        }

        SelectionOperator selection = new BinaryTournamentSelection<>(
                new RankingAndCrowdingDistanceComparator<>());

        NSGAIIIBuilder builder = new NSGAIIIBuilder<>(problem)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setSelectionOperator(selection)
                .setMaxIterations(StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()))
                .setNumberOfDivisions(outerDivisions);

        // para problemas com mais de 5 objetivos (m in {8, 10, 15}), usar duas camadas
        // explícitas de pontos de referência
        if (innerDivisions > 0) {
            builder.setSecondLayerDivisions(innerDivisions);
        }

        Algorithm<List<DoubleSolution>> algorithm = builder.build();
        if (ReferenceSetValidator.isPersistReferencePointsEnabled()) {
            double[][] actualPoints = ReferenceSetValidator.extractReferencePointsFromNsgaIII(algorithm, m);
            ReferenceSetValidator.persistNsgaIIIReferencePointsIfEnabled(
                    actualPoints, m, outerDivisions, innerDivisions);
        }
        return algorithm;
    }

    static Algorithm buildNSGAII(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        if (populationSize % 2 != 0) {
            populationSize++;
        }
        NSGAIIBuilder builder = new NSGAIIBuilder<>(
                problem,
                crossover,
                mutation,
                populationSize);
        builder.setMaxEvaluations(
                populationSize * StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()));
        Algorithm<List<DoubleSolution>> algorithm = builder.build();
        return algorithm;
    }

    static Algorithm buildSPEA2(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        if (populationSize % 2 != 0) {
            populationSize++;
        }
        SPEA2Builder builder = new SPEA2Builder<>(
                problem,
                crossover,
                mutation);
        builder.setMaxIterations(StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()));

        return builder.build();
    }

    static Algorithm buildMOEADDRA(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        validateMoeadWeightFile(getWeightsDirectory(), problem, populationSize);
        MOEADBuilder moead = new MOEADBuilder(problem, MOEADBuilder.Variant.MOEADDRA)
                .setCrossover(crossover)
                .setMutation(mutation)
                .setMaxEvaluations(
                        StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()) * populationSize)
                .setPopulationSize(populationSize)
                .setResultPopulationSize(populationSize)
                .setNeighborhoodSelectionProbability(0.9)
                .setMaximumNumberOfReplacedSolutions(2)
                .setNeighborSize(20)
                .setDataDirectory(getWeightsDirectory())
                .setFunctionType(AbstractMOEAD.FunctionType.PBI);

        return moead.build();
    }

    static Algorithm buildAGEMOEA(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        if (populationSize % 2 != 0) {
            populationSize++;
        }
        AGEMOEABuilder age = new AGEMOEABuilder(problem);
        age.setMaxIterations(StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()));
        age.setCrossoverOperator(crossover);
        age.setMutationOperator(mutation);
        age.setPopulationSize(populationSize);
        return age.build();
    } //

    static Algorithm buildAGE2(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        if (populationSize % 2 != 0) {
            populationSize++;
        }
        AGEMOEAIIBuilder age = new AGEMOEAIIBuilder(problem);
        age.setMaxIterations(StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()));
        age.setCrossoverOperator(crossover);
        age.setMutationOperator(mutation);
        age.setPopulationSize(populationSize);
        return age.build();
    }

    public static Algorithm buildRVEA(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        int m = problem.numberOfObjectives();
        int outerDivisions = StaticParams.getOuterDivisions(m);
        int innerDivisions = StaticParams.getInnerDivisions(m);
        int maxEvaluations = StaticParams.getMaxGenerations(m, problem.name()) * populationSize;
        List<double[]> referenceVectors;
        switch (m) {
            case 2 -> referenceVectors = ReferencePointGenerator.generateSingleLayer(2, 99);
            case 3 -> referenceVectors = ReferencePointGenerator.generateSingleLayer(3, 12);
            case 5 -> referenceVectors = ReferencePointGenerator.generateSingleLayer(5, 6);
            case 8 -> referenceVectors = ReferencePointGenerator.generateTwoLayers(8, 3, 2);
            case 10 -> referenceVectors = ReferencePointGenerator.generateTwoLayers(10, 3, 2);
            case 15 -> referenceVectors = ReferencePointGenerator.generateTwoLayers(15, 2, 1);
            default -> referenceVectors = ReferencePointGenerator.generateSingleLayer(m, 12);
        }
        if (populationSize != referenceVectors.size()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Inconsistência na configuração do RVEA para m=%d: populationSize=%d difere de referenceVectors.size()=%d.",
                            m, populationSize, referenceVectors.size()));
        }
        if (ReferenceSetValidator.isPersistReferenceDirectionsEnabled()) {
            ReferenceSetValidator.persistRveaReferenceDirectionsIfEnabled(
                    referenceVectors, m, outerDivisions, innerDivisions);
        }
        double alpha = 2.0;
        double fr = 0.1;
        RVEABuilder<DoubleSolution> builder = new RVEABuilder<>(
                problem,
                referenceVectors.size(),
                maxEvaluations,
                crossover,
                mutation,
                alpha,
                fr,
                referenceVectors);
        return builder.build();
    } //

    static Algorithm buildFDEA(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        int maxGenerations = StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name());
        int maxEvaluations = maxGenerations * populationSize;
        SelectionOperator selection = new BinaryTournamentSelection<>(
                new RankingAndCrowdingDistanceComparator<>());
        return new FDEABuilder(problem)
                .setPopulationSize(populationSize)
                .setMaxGenerations(maxGenerations)
                .setMaxEvaluations(maxEvaluations)
                .setCrossoverOperator(crossover)
                .setMutationOperator(mutation)
                .setSelectionOperator(selection)
                .build();
    } //

    /**
     * Resolve o diretório que contém o arquivo de pesos do MOEA/DD respeitando a
     * precedência canônica:
     * 1. {@code baseWeightsDir} (padrão {@code resources/weightVectorFiles});
     * 2. Subpasta {@code weights_gen/} dentro de {@code baseWeightsDir};
     * 3. Geração analítica persistida em {@code weights_gen/}.
     * Quando {@code -DreplaceZeroWeights} estiver habilitada globalmente ou para o
     * algoritmo,
     * isola e resolve exclusivamente em {@code weights_lowzero_gen/}.
     *
     * @param baseWeightsDir diretório base configurado
     * @param m              número de objetivos
     * @param populationSize tamanho da população
     * @return caminho em String do diretório que contém o arquivo
     */
    public static String resolveMoeadDataDirectory(String baseWeightsDir, int m, int populationSize) {
        return resolveMoeadDataDirectory(baseWeightsDir, m, populationSize, null);
    }

    /**
     * Resolve o diretório de pesos do MOEA/DD aplicando a regra granular de
     * {@code -DreplaceZeroWeights}
     * para {@code algorithmName}.
     */
    public static String resolveMoeadDataDirectory(
            String baseWeightsDir, int m, int populationSize, String algorithmName) {
        return resolveMoeadDataDirectory(baseWeightsDir, m, populationSize, algorithmName, null);
    }

    /**
     * Resolve o diretório de pesos do MOEA/DD com suporte a {@code baseDir}
     * explícito e à política
     * opcional {@code -Dstudy.allowMoeadWeightsInMemory} (T34).
     */
    public static String resolveMoeadDataDirectory(
            String baseWeightsDir, int m, int populationSize, String algorithmName, Path baseDir) {
        Path configured = (baseWeightsDir != null && !baseWeightsDir.isBlank())
                ? Paths.get(baseWeightsDir)
                : Paths.get(DEFAULT_WEIGHTS_DIRECTORY);
        Path effectiveBase = (baseDir != null && !configured.isAbsolute())
                ? baseDir.resolve(configured)
                : configured;
        boolean allowInMem = isAllowMoeadWeightsInMemoryEnabled();
        ResolvedWeightArtifact artifact = ReferenceSetValidator.resolveCanonicalWeightArtifactFromWeightsRoot(
                m,
                populationSize,
                effectiveBase,
                algorithmName,
                allowInMem);
        if (artifact.inMemory()) {
            return effectiveBase.toString().replace('\\', '/');
        }
        if (artifact.path() != null && artifact.path().getParent() != null) {
            return artifact.path().getParent().toString().replace('\\', '/');
        }
        return effectiveBase.toString().replace('\\', '/');
    }

    public static Algorithm buildMOEADD(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        String effectiveDataDir = resolveMoeadDataDirectory(
                getWeightsDirectory(), problem.numberOfObjectives(), populationSize, "MOEADD");
        validateMoeadWeightFile(effectiveDataDir, problem, populationSize, "MOEADD");

        // Nota sobre PBI no jMetal 7.8: AbstractMOEAD.fitnessFunction fixa internamente
        // theta = 5.0
        // para FunctionType.PBI, e MOEADBuilder não expõe setter para theta.
        MOEADBuilder moead = new MOEADBuilder(problem, MOEADBuilder.Variant.MOEADD)
                .setCrossover(crossover)
                .setMutation(mutation)
                .setMaxEvaluations(
                        StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()) * populationSize)
                .setPopulationSize(populationSize)
                .setResultPopulationSize(populationSize)
                .setNeighborhoodSelectionProbability(0.9)
                .setMaximumNumberOfReplacedSolutions(2)
                .setNeighborSize(20)
                .setDataDirectory(effectiveDataDir)
                .setFunctionType(AbstractMOEAD.FunctionType.PBI);

        return moead.build();
    }

    /**
     * Valida explicitamente a existência, quantidade de vetores, dimensionalidade,
     * finitude,
     * não-negatividade, soma no simplex e ausência de duplicatas do arquivo de
     * pesos do MOEA/DD
     * ({@code W{m}D_{populationSize}.dat}) antes de instanciar ou executar o
     * algoritmo.
     *
     * @param dataDirectory  diretório onde os arquivos {@code .dat} estão
     *                       armazenados
     * @param problem        problema multiobjetivo alvo
     * @param populationSize tamanho da população (deve coincidir com o número de
     *                       vetores de pesos)
     * @return matriz {@code double[populationSize][m]} com os vetores de pesos
     *         validados
     * @throws IllegalArgumentException se o arquivo não existir ou violar qualquer
     *                                  critério matemático
     */
    public static double[][] validateMoeadWeightFile(String dataDirectory, Problem<?> problem, int populationSize) {
        return validateMoeadWeightFile(dataDirectory, problem, populationSize, null);
    }

    /**
     * Sobrecarga com escopo explícito de algoritmo para resolução/validação de
     * pesos.
     */
    public static double[][] validateMoeadWeightFile(
            String dataDirectory, Problem<?> problem, int populationSize, String algorithmName) {
        if (problem == null) {
            throw new IllegalArgumentException("O problema não pode ser nulo na validação de pesos do MOEA/DD.");
        }
        int m = problem.numberOfObjectives();
        boolean isGwasfga = algorithmName != null && "GWASFGA".equalsIgnoreCase(algorithmName.trim());
        if (isAllowMoeadWeightsInMemoryEnabled()
                && !isGwasfga
                && !ReferenceSetValidator.isReplaceZeroWeightsEnabled(algorithmName)
                && ReferenceSetValidator.isMoeadWeightGeneratedInMemory(m, populationSize)) {
            double[][] inMem = ReferenceSetValidator.generateJMetalMoead2DInMemoryWeights(populationSize);
            ReferenceSetValidator.validateSimplexSet(
                    inMem, populationSize, m, MOEAD_WEIGHT_SUM_TOLERANCE, MOEAD_WEIGHT_DUPLICATE_TOLERANCE);
            return inMem;
        }

        String fileName = "W" + m + "D_" + populationSize + ".dat";
        Path weightPath = null;
        if (dataDirectory != null && !dataDirectory.isBlank()) {
            Path dirPath = Paths.get(dataDirectory);
            Path directCandidate = dirPath.resolve(fileName);
            boolean isBaseWeightsRoot = dirPath.normalize().equals(Paths.get(DEFAULT_WEIGHTS_DIRECTORY).normalize());
            if (!isBaseWeightsRoot && Files.isRegularFile(directCandidate)) {
                weightPath = directCandidate;
            }
        }
        if (weightPath == null) {
            String resolvedDir = resolveMoeadDataDirectory(dataDirectory, m, populationSize, algorithmName);
            weightPath = Paths.get(resolvedDir, fileName);
        }
        return ReferenceSetValidator.validateMoeadWeightFile(
                weightPath,
                m,
                populationSize,
                MOEAD_WEIGHT_SUM_TOLERANCE,
                MOEAD_WEIGHT_DUPLICATE_TOLERANCE);
    }

    /**
     * Contexto estruturado retornado pela validação de preflight da T34 contendo
     * exatamente os
     * artefatos de pesos e frentes selecionados para a execução.
     */
    public record PreflightResolutionContext(
            List<ResolvedWeightArtifact> resolvedWeights,
            List<ResolvedFrontArtifact> resolvedFronts) {
    }

    /**
     * Validação prévia de voo (preflight) estrita: verifica a integridade de todas
     * as configurações antes da instanciação e execução dos algoritmos (T23 / T34).
     *
     * @param problemList lista de problemas configurados
     */
    public static void runPreflightValidation(List<ExperimentProblem<DoubleSolution>> problemList) {
        runPreflightValidationAndResolveArtifacts(problemList, null);
    }

    /**
     * Executa o preflight estrito propagando {@code baseDir} (quando informado),
     * bloqueando
     * diretórios legados protegidos e retornando os objetos estruturados resolvidos
     * uma única vez
     * por configuração para construção do manifesto efetivo (T34).
     */
    public static PreflightResolutionContext runPreflightValidationAndResolveArtifacts(
            List<ExperimentProblem<DoubleSolution>> problemList, Path baseDir) {
        if (problemList == null || problemList.isEmpty()) {
            throw new IllegalStateException("Preflight falhou: lista de problemas está vazia.");
        }

        Path configuredWeightsDir = Paths.get(getWeightsDirectory());
        Path effectiveWeightsBase = (baseDir != null && !configuredWeightsDir.isAbsolute())
                ? baseDir.resolve(configuredWeightsDir)
                : configuredWeightsDir;
        Path configuredFrontsDir = Paths.get(getReferenceFrontDirectory());
        Path effectiveFrontsBase = (baseDir != null && !configuredFrontsDir.isAbsolute())
                ? baseDir.resolve(configuredFrontsDir)
                : configuredFrontsDir;

        Map<Integer, ResolvedWeightArtifact> weightsByM = new LinkedHashMap<>();
        List<ResolvedFrontArtifact> resolvedFronts = new ArrayList<>(problemList.size());

        for (ExperimentProblem<DoubleSolution> expProblem : problemList) {
            Problem<?> p = expProblem.getProblem();
            int m = p.numberOfObjectives();
            int popSize = StaticParams.getPopulationSize(m);

            // 1. Resolução canônica única por m dos pesos do MOEA/DD (respeita
            // -Dstudy.allowMoeadWeightsInMemory)
            if (!weightsByM.containsKey(m)) {
                ResolvedWeightArtifact rwa = ReferenceSetValidator.resolveCanonicalWeightArtifactFromWeightsRoot(
                        m,
                        popSize,
                        effectiveWeightsBase,
                        "MOEADD",
                        isAllowMoeadWeightsInMemoryEnabled());
                weightsByM.put(m, rwa);
            }
            if (shouldIncludeLegacyAlgorithms()) {
                ReferenceSetValidator.resolveCanonicalWeightArtifactFromWeightsRoot(
                        m, popSize, effectiveWeightsBase, "GWASFGA", false);
            }

            // 2. Validação dos pontos de referência do NSGA-III
            int outer = StaticParams.getOuterDivisions(m);
            int inner = StaticParams.getInnerDivisions(m);
            int expectedNsgaIII = ReferenceSetValidator.expectedTwoLayerPoints(m, outer, inner);
            if (popSize != expectedNsgaIII) {
                throw new IllegalStateException(String.format(
                        "Preflight falhou para NSGA-III em m=%d: popSize=%d difere do esperado=%d",
                        m, popSize, expectedNsgaIII));
            }

            // 3. Validação estrutural e física do arquivo da frente de referência
            // efetivamente configurada
            String refFront = expProblem.getReferenceFront();
            if (refFront == null || refFront.isBlank()) {
                throw new IllegalStateException(
                        "Preflight falhou: frente de referência não definida para " + expProblem.getTag());
            }
            Path candidateRefPath = effectiveFrontsBase.resolve(refFront);
            if (!Files.isRegularFile(candidateRefPath)) {
                Path fallbackPath = (baseDir != null) ? baseDir.resolve(refFront) : Paths.get(refFront);
                if (Files.isRegularFile(fallbackPath)) {
                    candidateRefPath = fallbackPath;
                } else {
                    throw new IllegalStateException("Preflight falhou: arquivo de frente não encontrado: " + refFront
                            + " para " + expProblem.getTag());
                }
            }
            // Valida o conteúdo da frente antes da execução!
            ParetoFrontValidator.validateParetoFrontFile(candidateRefPath, m);
            ParetoFrontValidator.PhysicalFrontAuditRow audit = ParetoFrontValidator
                    .auditPhysicalFrontFile(candidateRefPath);
            Path normCandidate = candidateRefPath.toAbsolutePath().normalize();
            Path normPfLowZero = effectiveFrontsBase.resolve("pf_lowzero_gen").toAbsolutePath().normalize();
            Path normPfGen = effectiveFrontsBase.resolve("pf_gen").toAbsolutePath().normalize();
            String category = normCandidate.startsWith(normPfLowZero)
                    ? "LOWZERO_GEN"
                    : (normCandidate.startsWith(normPfGen) ? "PF_GEN" : "OFFICIAL");
            resolvedFronts.add(new ResolvedFrontArtifact(
                    StaticParams.normalizeProblemId(p.name()),
                    m,
                    candidateRefPath,
                    audit.sha256(),
                    category,
                    audit.validPoints(),
                    audit.uniquePoints(),
                    ParetoFrontValidator.isReplaceZeroFrontsEnabled()));
        }

        return new PreflightResolutionContext(
                List.copyOf(weightsByM.values()),
                List.copyOf(resolvedFronts));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static Algorithm buildGWASFGA(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        SolutionListEvaluator evaluator = new SequentialSolutionListEvaluator<>();
        SelectionOperator<List<DoubleSolution>, DoubleSolution> selectionOperator = new BinaryTournamentSelection<>(
                new RankingAndCrowdingDistanceComparator<>());
        String effectiveDataDir = resolveMoeadDataDirectory(
                getWeightsDirectory(), problem.numberOfObjectives(), populationSize, "GWASFGA");
        String dataFileName = "W" + problem.numberOfObjectives() + "D_" + populationSize + ".dat";
        return new GWASFGA(problem, populationSize,
                StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()), crossover, mutation,
                selectionOperator, evaluator, epsilon, effectiveDataDir + "/" + dataFileName);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static Algorithm buildESPEA(Problem problem, int populationSize, CrossoverOperator crossover,
            MutationOperator mutation) {
        ESPEABuilder builder = new ESPEABuilder(problem, crossover, mutation);
        builder.setMaxEvaluations(
                StaticParams.getMaxGenerations(problem.numberOfObjectives(), problem.name()) * populationSize);
        builder.setPopulationSize(populationSize);
        return builder.build();
    }
}

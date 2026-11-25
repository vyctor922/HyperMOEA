package br.usp.lti.newmoea.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.util.EvaluationBudgetProblem;
import br.usp.lti.newmoea.util.IsolatedJobExecutor;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.BatchExecutionConfig;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.CorruptedRunArtifactException;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.JobExecutionResult;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.JobSpec;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.ManifestEntry;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.StudyBatchReport;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.bounds.Bounds;
import org.uma.jmetal.util.evaluator.SolutionListEvaluator;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

/**
 * Suíte integrada de validação da Tarefa T33 (incorporando T32 conforme decisão
 * A5):
 * <ul>
 * <li>Validação com contador externo independente nos 6 algoritmos em \(B =
 * N_{\text{alloc}}\),
 * \(B = N_{\text{alloc}} + 1\), \(B = 2N_{\text{alloc}} - 1\), orçamentos
 * divisíveis e não
 * divisíveis, rejeição explícita de \(B < N_{\text{alloc}}\), proibição estrita
 * de objetivos
 * fictícios/herdados e reserva atômica sob concorrência (decisão A2);</li>
 * <li>Isolamento estrito do RNG: execução serial intraprocesso, ordem inversa e
 * execução paralela
 * em subprocessos JVM com verificação de ausência de workers órfãos;</li>
 * <li>30 jobs independentes baratos (\(\text{runId} = 0..29\)) e reprodução
 * determinística isolada;</li>
 * <li>Invocação real de {@link WFGStudy#main(String[])}, Lote A seguido de Lote
 * B, rerun
 * idempotente, preservação de {@code QualityIndicatorSummary.csv} entre
 * problemas distintos
 * e detecção não-destrutiva de crash entre {@code FUN}/{@code VAR}, arquivo
 * vazio,
 * desalinhamento, hash adulterado e protocolo incompatível.</li>
 * </ul>
 */
class StudyBudgetIsolationAndResumeIntegrationTest {

        /**
         * Contador externo totalmente independente de {@link EvaluationBudgetProblem}
         * para provar que
         * nenhuma chamada extra é ocultada e que toda solução sobrevivente foi
         * genuinamente avaliada.
         */
        private static final class IndependentCountingProblem implements DoubleProblem {

                private static final long serialVersionUID = 1L;

                private final DoubleProblem raw;
                private final AtomicLong externalEvaluations = new AtomicLong(0L);

                IndependentCountingProblem(DoubleProblem raw) {
                        this.raw = raw;
                }

                @Override
                public DoubleSolution evaluate(DoubleSolution solution) {
                        externalEvaluations.incrementAndGet();
                        return raw.evaluate(solution);
                }

                long count() {
                        return externalEvaluations.get();
                }

                @Override
                public int numberOfVariables() {
                        return raw.numberOfVariables();
                }

                @Override
                public int numberOfObjectives() {
                        return raw.numberOfObjectives();
                }

                @Override
                public int numberOfConstraints() {
                        return raw.numberOfConstraints();
                }

                @Override
                public String name() {
                        return raw.name();
                }

                @Override
                public DoubleSolution createSolution() {
                        return raw.createSolution();
                }

                @Override
                public List<Bounds<Double>> variableBounds() {
                        return raw.variableBounds();
                }
        }

        @Test
        @DisplayName("T33/A2: Contador independente confirma B exato em B=N_alloc, B=N_alloc+1, 2N_alloc-1, 2N_alloc e protocolo ímpar, sem objetivos fictícios e rejeitando B < N_alloc")
        void shouldValidateExactPartialBatchBudgetsAndRejectUnderInitializationAcrossAllSixAlgorithms() {
                int[] dimensions = { 2, 3 }; // m=2 (N_config=100 par), m=3 (N_config=91 ímpar, N_alloc=91 ou 92)

                for (int m : dimensions) {
                        int nConfig = EvaluationBudgetProblem.getConfiguredPopulationSize(m);
                        DoubleProblem verificationOracle = new DTLZ2(m + 5, m);

                        for (String algName : EvaluationBudgetProblem.MAIN_ALGORITHMS) {
                                int nAlloc = EvaluationBudgetProblem.getAllocatedPopulationSize(algName, m);

                                // 1. Rejeição explícita quando B < N_alloc (não permite inicialização válida)
                                EvaluationBudgetProblem tooSmall = new EvaluationBudgetProblem(new DTLZ2(m + 5, m),
                                                nAlloc - 1, true);
                                assertThatThrownBy(
                                                () -> EvaluationBudgetProblem.buildAuditedAlgorithm(
                                                                algName,
                                                                tooSmall,
                                                                1,
                                                                new SBXCrossover(1.0, 30.0),
                                                                new PolynomialMutation(
                                                                                1.0 / tooSmall.numberOfVariables(),
                                                                                20.0)))
                                                .isInstanceOf(IllegalArgumentException.class)
                                                .hasMessageContaining("insuficiente para inicialização válida");

                                // 2. Validação exata em B = N_alloc, N_alloc + 1, 2*N_alloc - 1, 2*N_alloc e
                                // 3*N_config
                                int[] budgetsToTest = {
                                                nAlloc, nAlloc + 1, 2 * nAlloc - 1, 2 * nAlloc, 3 * nConfig
                                };

                                for (int budget : budgetsToTest) {
                                        IndependentCountingProblem independentCounter = new IndependentCountingProblem(
                                                        new DTLZ2(m + 5, m));
                                        EvaluationBudgetProblem strictProblem = new EvaluationBudgetProblem(
                                                        independentCounter, budget, true);

                                        JMetalRandom.getInstance().setSeed(20261118L + m * 100L + budget);
                                        int gens = EvaluationBudgetProblem.computeExactGenerationsForBudget(budget,
                                                        nAlloc);
                                        Algorithm<List<DoubleSolution>> algorithm = EvaluationBudgetProblem
                                                        .buildAuditedAlgorithm(
                                                                        algName,
                                                                        strictProblem,
                                                                        gens,
                                                                        new SBXCrossover(1.0, 30.0),
                                                                        new PolynomialMutation(
                                                                                        1.0 / strictProblem
                                                                                                        .numberOfVariables(),
                                                                                        20.0));

                                        algorithm.run();
                                        List<DoubleSolution> survivors = algorithm.result();

                                        assertThat(independentCounter.count())
                                                        .as("Contador independente para %s em m=%d com B=%d", algName,
                                                                        m, budget)
                                                        .isEqualTo(budget);
                                        assertThat(strictProblem.getActualProblemEvaluations())
                                                        .isEqualTo(budget);
                                        assertThat(survivors).isNotNull().isNotEmpty();

                                        // Reavalia cada solução sobrevivente no oráculo limpo: zero objetivos fictícios
                                        // ou herdados!
                                        for (DoubleSolution sol : survivors) {
                                                DoubleSolution check = verificationOracle.createSolution();
                                                for (int v = 0; v < verificationOracle.numberOfVariables(); v++) {
                                                        check.variables().set(v, sol.variables().get(v));
                                                }
                                                verificationOracle.evaluate(check);
                                                assertThat(sol.objectives())
                                                                .as("Objetivos reais vs oráculo em %s (m=%d, B=%d)",
                                                                                algName, m, budget)
                                                                .containsExactly(check.objectives());
                                        }

                                        // Qualquer chamada direta a Problem.evaluate após esgotar B deve falhar
                                        // explicitamente (sem cópia de fallback)
                                        DoubleSolution extraCandidate = strictProblem.createSolution();
                                        assertThatThrownBy(() -> strictProblem.evaluate(extraCandidate))
                                                        .isInstanceOf(IllegalStateException.class)
                                                        .hasMessageContaining(
                                                                        "Violação do contrato estrito de orçamento");
                                        assertThat(independentCounter.count()).isEqualTo(budget);
                                }
                        }
                }
        }

        @Test
        @DisplayName("T33/A2: Reserva atômica de orçamento sob avaliações concorrentes impede estouro e descarta não-avaliados")
        void shouldReserveBudgetAtomicallyUnderConcurrentEvaluations() throws Exception {
                IndependentCountingProblem independentCounter = new IndependentCountingProblem(new WFG1(2, 4, 2));
                int maxBudget = 25;
                EvaluationBudgetProblem strictProblem = new EvaluationBudgetProblem(independentCounter, maxBudget,
                                true);
                SolutionListEvaluator<DoubleSolution> evaluator = strictProblem.createSolutionListEvaluator();

                int threadCount = 8;
                int batchPerThread = 10; // 80 candidatos disputando simultaneamente apenas 25 vagas
                ExecutorService pool = Executors.newFixedThreadPool(threadCount);
                try {
                        List<Callable<Integer>> tasks = new ArrayList<>();
                        for (int t = 0; t < threadCount; t++) {
                                tasks.add(
                                                () -> {
                                                        List<DoubleSolution> localBatch = new ArrayList<>(
                                                                        batchPerThread);
                                                        for (int i = 0; i < batchPerThread; i++) {
                                                                localBatch.add(strictProblem.createSolution());
                                                        }
                                                        List<DoubleSolution> evaluated = evaluator.evaluate(localBatch,
                                                                        strictProblem);
                                                        return evaluated.size();
                                                });
                        }
                        List<Future<Integer>> futures = pool.invokeAll(tasks);
                        int totalKeptAcrossThreads = 0;
                        for (Future<Integer> f : futures) {
                                totalKeptAcrossThreads += f.get();
                        }
                        assertThat(totalKeptAcrossThreads).isEqualTo(maxBudget);
                        assertThat(independentCounter.count()).isEqualTo(maxBudget);
                        assertThat(strictProblem.getActualProblemEvaluations()).isEqualTo(maxBudget);
                } finally {
                        pool.shutdownNow();
                }
        }

        @Test
        @DisplayName("T33: Execução serial, ordem inversa e execução paralela em subprocessos JVM produzem hashes idênticos sem workers órfãos")
        void shouldMatchSerialReverseAndParallelSubprocessExecutionWithoutOrphanProcesses(
                        @TempDir Path tempDir) throws IOException {
                long seed = IsolatedJobExecutor.DEFAULT_BASE_SEED;
                List<JobSpec> jobs = List.of(
                                JobSpec.ofCustomBudget("WFG1", 2, "NSGA-III", 0, seed, 2, 150, true),
                                JobSpec.ofCustomBudget("WFG1", 2, "MOEA/DD", 1, seed, 2, 150, true),
                                JobSpec.ofCustomBudget("DTLZ2", 3, "FDEA", 2, seed, 2, 140, true),
                                JobSpec.ofCustomBudget("DTLZ2", 3, "RVEA", 3, seed, 2, 140, true),
                                JobSpec.ofCustomBudget("WFG4", 2, "AGE-MOEA", 4, seed, 2, 150, true),
                                JobSpec.ofCustomBudget("WFG4", 3, "AGE-MOEA-II", 5, seed, 2, 140, true));

                // 1. Execução serial na ordem direta
                Map<String, JobExecutionResult> serialResults = new HashMap<>();
                for (JobSpec spec : jobs) {
                        serialResults.put(spec.jobId(), IsolatedJobExecutor.executeJobInProcess(spec));
                }

                // 2. Execução serial na ordem inversa
                List<JobSpec> reversed = new ArrayList<>(jobs);
                Collections.reverse(reversed);
                for (JobSpec spec : reversed) {
                        JobExecutionResult revRes = IsolatedJobExecutor.executeJobInProcess(spec);
                        JobExecutionResult ref = serialResults.get(spec.jobId());
                        assertThat(revRes.objectivesSha256()).isEqualTo(ref.objectivesSha256());
                        assertThat(revRes.variablesSha256()).isEqualTo(ref.variablesSha256());
                        assertThat(revRes.actualEvaluations()).isEqualTo(spec.targetEvaluations());
                }

                // 3. Execução paralela em subprocessos isolados (maxWorkers = 2)
                long childProcessesBefore = countChildProcessesOfCurrentJvm();
                Path subprocStudyDir = tempDir.resolve("subproc_batch");
                BatchExecutionConfig parallelSubprocessConfig = new BatchExecutionConfig(2, true, 60_000L, true, null);
                StudyBatchReport report = IsolatedJobExecutor.executeAndPersistBatch(
                                jobs, subprocStudyDir, parallelSubprocessConfig);

                assertThat(report.executedJobsCount()).isEqualTo(jobs.size());
                assertThat(report.resumedJobsCount()).isZero();
                for (ManifestEntry entry : report.consolidatedManifest()) {
                        JobExecutionResult ref = serialResults.get(entry.jobId());
                        assertThat(entry.objectivesSha256()).isEqualTo(ref.objectivesSha256());
                        assertThat(entry.variablesSha256()).isEqualTo(ref.variablesSha256());
                        assertThat(entry.actualEvaluations()).isEqualTo(entry.targetEvaluations());
                }
                assertThat(countChildProcessesOfCurrentJvm()).isLessThanOrEqualTo(childProcessesBefore);

                // 4. Encerramento forçado de subprocesso em timeout sem deixar worker órfão
                JobSpec heavySpec = JobSpec.ofCanonical("WFG4", 8, "MOEA/DD", 0, seed, true);
                assertThatThrownBy(() -> IsolatedJobExecutor.executeJobInSubprocess(heavySpec, 5L))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Timeout de execução");
                assertThat(countChildProcessesOfCurrentJvm()).isLessThanOrEqualTo(childProcessesBefore);
        }

        @Test
        @DisplayName("T33: Trinta jobs baratos (runId = 0..29) geram seeds distintas e permitem reprodução isolada exata de qualquer runId")
        void shouldExecuteThirtyCheapJobsWithDistinctSeedsAndExactSingleRunReplay(
                        @TempDir Path tempDir) throws IOException {
                long baseSeed = IsolatedJobExecutor.DEFAULT_BASE_SEED;
                List<JobSpec> thirtyJobs = new ArrayList<>(30);
                Set<Long> effectiveSeeds = new HashSet<>();

                for (int runId = 0; runId < 30; runId++) {
                        JobSpec spec = JobSpec.ofCustomBudget("WFG2", 2, "MOEA/DD", runId, baseSeed, 2, 110, true);
                        assertThat(effectiveSeeds.add(spec.effectiveSeed())).isTrue();
                        thirtyJobs.add(spec);
                }

                StudyBatchReport batchReport = IsolatedJobExecutor.executeAndPersistBatch(
                                thirtyJobs, tempDir, BatchExecutionConfig.defaultSequential());
                assertThat(batchReport.executedJobsCount()).isEqualTo(30);
                assertThat(batchReport.consolidatedManifest()).hasSize(30);

                // Reproduz isoladamente apenas a runId = 17 e compara com o manifesto das 30
                // runs
                JobSpec targetRun17 = thirtyJobs.get(17);
                JobExecutionResult isolatedReplay17 = IsolatedJobExecutor.executeJobInProcess(targetRun17);
                ManifestEntry manifest17 = batchReport.consolidatedManifest().get(17);

                assertThat(manifest17.runId()).isEqualTo(17);
                assertThat(isolatedReplay17.actualEvaluations()).isEqualTo(110);
                assertThat(isolatedReplay17.objectivesSha256()).isEqualTo(manifest17.objectivesSha256());
                assertThat(isolatedReplay17.variablesSha256()).isEqualTo(manifest17.variablesSha256());
        }

        @Test
        @DisplayName("T33/T32: WFGStudy.main() executa Lote A -> Lote B, rerun idempotente e consolida múltiplos problemas sem sobrescrever nem perder resultados")
        void shouldExecuteEntrypointAcrossBatchesAndPreserveAllRunsAndSummaries(@TempDir Path tempDir)
                        throws IOException {
                Path studyOut = tempDir.resolve("nested/T33_study");
                Map<String, String> savedProps = snapshotProperties(
                                "study.outputDirectory",
                                "problem",
                                "objectives",
                                "runStart",
                                "runs",
                                "study.baseSeed",
                                "study.maxEvaluationsOverride",
                                "study.parallelWorkers",
                                "study.processIsolation");

                try {
                        System.setProperty("study.outputDirectory", studyOut.toString());
                        System.setProperty("problem", "WFG1");
                        System.setProperty("objectives", "2");
                        System.setProperty("study.baseSeed", "20260409");
                        System.setProperty("study.maxEvaluationsOverride", "120");
                        System.setProperty("study.parallelWorkers", "1");
                        System.setProperty("study.processIsolation", "false");

                        // 1. Lote A: runs 0..1 (2 runs x 6 algoritmos = 12 jobs)
                        System.setProperty("runStart", "0");
                        System.setProperty("runs", "2");
                        WFGStudy.main(new String[0]);

                        Path fun0Nsgaiii = studyOut.resolve("data/NSGAIII/WFG1_2/FUN0.csv");
                        Path var0Nsgaiii = studyOut.resolve("data/NSGAIII/WFG1_2/VAR0.csv");
                        Path manifestCsv = studyOut.resolve("run_manifest.csv");
                        Path summaryCsv = studyOut.resolve("QualityIndicatorSummary.csv");

                        assertThat(fun0Nsgaiii).exists().isNotEmptyFile();
                        assertThat(var0Nsgaiii).exists().isNotEmptyFile();
                        assertThat(manifestCsv).exists();
                        assertThat(IsolatedJobExecutor.readManifestFromCsv(manifestCsv)).hasSize(12);
                        assertThat(studyOut.resolve("latex/T33_study.tex")).exists();

                        String fun0ContentBatchA = Files.readString(fun0Nsgaiii, StandardCharsets.UTF_8);
                        FileTime fun0MtimeBatchA = Files.getLastModifiedTime(fun0Nsgaiii);

                        // 2. Lote B: runs 2..3 (adiciona +12 jobs preservando intactos 0..1)
                        System.setProperty("runStart", "2");
                        System.setProperty("runs", "2");
                        WFGStudy.main(new String[0]);

                        assertThat(Files.readString(fun0Nsgaiii, StandardCharsets.UTF_8))
                                        .isEqualTo(fun0ContentBatchA);
                        assertThat(Files.getLastModifiedTime(fun0Nsgaiii)).isEqualTo(fun0MtimeBatchA);
                        assertThat(studyOut.resolve("data/NSGAIII/WFG1_2/FUN2.csv")).exists().isNotEmptyFile();
                        assertThat(studyOut.resolve("data/MOEADD/WFG1_2/FUN3.csv")).exists().isNotEmptyFile();

                        List<ManifestEntry> manifestAfterBatchB = IsolatedJobExecutor.readManifestFromCsv(manifestCsv);
                        assertThat(manifestAfterBatchB).hasSize(24);
                        for (ManifestEntry e : manifestAfterBatchB) {
                                assertThat(e.actualEvaluations()).isEqualTo(120);
                        }

                        // 3. Rerun idempotente (runs 0..3): zero reescritas de FUN/VAR
                        FileTime fun3Mtime = Files.getLastModifiedTime(studyOut.resolve("data/MOEADD/WFG1_2/FUN3.csv"));
                        System.setProperty("runStart", "0");
                        System.setProperty("runs", "4");
                        WFGStudy.main(new String[0]);
                        assertThat(Files.getLastModifiedTime(studyOut.resolve("data/MOEADD/WFG1_2/FUN3.csv")))
                                        .isEqualTo(fun3Mtime);

                        // 4. Lote C em problema diferente (WFG2_2, runs 0..1): deve preservar linhas de
                        // WFG1_2 em QualityIndicatorSummary.csv!
                        System.setProperty("problem", "WFG2");
                        System.setProperty("runStart", "0");
                        System.setProperty("runs", "2");
                        WFGStudy.main(new String[0]);

                        String mergedSummary = Files.readString(summaryCsv, StandardCharsets.UTF_8);
                        assertThat(mergedSummary).contains(",WFG1_2,");
                        assertThat(mergedSummary).contains(",WFG2_2,");
                        assertThat(IsolatedJobExecutor.readManifestFromCsv(manifestCsv)).hasSize(36);
                } finally {
                        restoreProperties(savedProps);
                }
        }

        @Test
        @DisplayName("T33: Detecta explicitamente crash entre FUN/VAR, arquivo vazio, FUN/VAR inválido, hash alterado e protocolo incompatível sem sobrescrever runs íntegras")
        void shouldDetectCorruptedInterruptedAndIncompatibleRunsWithoutOverwritingValidRuns(
                        @TempDir Path tempDir) throws IOException {
                long seed = IsolatedJobExecutor.DEFAULT_BASE_SEED;
                JobSpec run0 = JobSpec.ofCustomBudget("WFG1", 2, "NSGA-III", 0, seed, 2, 120, true);
                JobSpec run1 = JobSpec.ofCustomBudget("WFG1", 2, "NSGA-III", 1, seed, 2, 120, true);

                IsolatedJobExecutor.executeAndPersistBatch(
                                List.of(run0, run1), tempDir, BatchExecutionConfig.defaultSequential());

                Path cellDir = IsolatedJobExecutor.resolveCellDirectory(tempDir, run0);
                Path fun0 = cellDir.resolve("FUN0.csv");
                String intactFun0 = Files.readString(fun0, StandardCharsets.UTF_8);

                // Caso 1: Crash entre FUN e VAR na run 2 (FUN2.csv órfão sem VAR2.csv)
                JobSpec run2 = JobSpec.ofCustomBudget("WFG1", 2, "NSGA-III", 2, seed, 2, 120, true);
                Path fun2 = cellDir.resolve("FUN2.csv");
                Files.writeString(fun2, "0.5,0.5\n", StandardCharsets.UTF_8);

                assertThatThrownBy(
                                () -> IsolatedJobExecutor.executeAndPersistBatch(
                                                List.of(run0, run1, run2),
                                                tempDir,
                                                BatchExecutionConfig.defaultSequential()))
                                .isInstanceOf(CorruptedRunArtifactException.class)
                                .satisfies(
                                                ex -> assertThat(((CorruptedRunArtifactException) ex).getReasonCode())
                                                                .isEqualTo("ORPHAN_FILE_CRASH"));
                assertThat(Files.readString(fun0, StandardCharsets.UTF_8)).isEqualTo(intactFun0);
                Files.deleteIfExists(fun2);

                // Caso 2: Arquivo vazio (0 bytes) em FUN1.csv
                Path fun1 = cellDir.resolve("FUN1.csv");
                String savedFun1 = Files.readString(fun1, StandardCharsets.UTF_8);
                Files.writeString(fun1, "", StandardCharsets.UTF_8);
                assertThatThrownBy(
                                () -> IsolatedJobExecutor.executeAndPersistBatch(
                                                List.of(run0, run1),
                                                tempDir,
                                                BatchExecutionConfig.defaultSequential()))
                                .isInstanceOf(CorruptedRunArtifactException.class)
                                .satisfies(
                                                ex -> assertThat(((CorruptedRunArtifactException) ex).getReasonCode())
                                                                .isEqualTo("EMPTY_FILE"));
                Files.writeString(fun1, savedFun1, StandardCharsets.UTF_8);

                // Caso 3: FUN1.csv adulterado (desalinhado de VAR1.csv)
                Files.writeString(fun1, "0.123456,0.654321\n", StandardCharsets.UTF_8);
                assertThatThrownBy(
                                () -> IsolatedJobExecutor.executeAndPersistBatch(
                                                List.of(run0, run1),
                                                tempDir,
                                                BatchExecutionConfig.defaultSequential()))
                                .isInstanceOf(CorruptedRunArtifactException.class)
                                .satisfies(
                                                ex -> assertThat(((CorruptedRunArtifactException) ex).getReasonCode())
                                                                .isEqualTo("INVALID_FUN_VAR_ALIGNMENT"));
                Files.writeString(fun1, savedFun1, StandardCharsets.UTF_8);

                // Caso 4: Hash adulterado em FUN1.raw.csv (mesmo mantendo par VAR/FUN
                // consistente de outra solução válida)
                Path rawFun1 = cellDir.resolve("FUN1.raw.csv");
                Path rawVar1 = cellDir.resolve("VAR1.raw.csv");
                String savedRawFun1 = Files.readString(rawFun1, StandardCharsets.UTF_8);
                String savedRawVar1 = Files.readString(rawVar1, StandardCharsets.UTF_8);
                Files.writeString(
                                rawFun1, Files.readString(cellDir.resolve("FUN0.raw.csv")), StandardCharsets.UTF_8);
                Files.writeString(
                                rawVar1, Files.readString(cellDir.resolve("VAR0.raw.csv")), StandardCharsets.UTF_8);
                assertThatThrownBy(
                                () -> IsolatedJobExecutor.executeAndPersistBatch(
                                                List.of(run0, run1),
                                                tempDir,
                                                BatchExecutionConfig.defaultSequential()))
                                .isInstanceOf(CorruptedRunArtifactException.class)
                                .satisfies(
                                                ex -> assertThat(((CorruptedRunArtifactException) ex).getReasonCode())
                                                                .isEqualTo("HASH_MISMATCH"));
                Files.writeString(rawFun1, savedRawFun1, StandardCharsets.UTF_8);
                Files.writeString(rawVar1, savedRawVar1, StandardCharsets.UTF_8);

                // Caso 5: Protocolo incompatível (orçamento ou baseSeed alterado para run1 já
                // existente)
                JobSpec incompatibleRun1 = JobSpec.ofCustomBudget("WFG1", 2, "NSGA-III", 1, seed, 3, 200, true);
                assertThatThrownBy(
                                () -> IsolatedJobExecutor.executeAndPersistBatch(
                                                List.of(run0, incompatibleRun1),
                                                tempDir,
                                                BatchExecutionConfig.defaultSequential()))
                                .isInstanceOf(CorruptedRunArtifactException.class)
                                .satisfies(
                                                ex -> assertThat(((CorruptedRunArtifactException) ex).getReasonCode())
                                                                .isEqualTo("PROTOCOL_MISMATCH"));

                // Confirma que FUN0.csv permaneceu 100% intocado após todas as rejeições
                assertThat(Files.readString(fun0, StandardCharsets.UTF_8)).isEqualTo(intactFun0);
        }

        @Test
        @DisplayName("Ordenação LPT determinística, validação de propriedades e aplicação segura de -Dstudy.workerJvmArgs em subprocesso")
        void shouldOrderPendingJobsByLptValidatePhase6PropertiesAndApplyWorkerJvmArgs(
                        @TempDir Path tempDir) {
                Map<String, String> savedProps = snapshotProperties(
                                IsolatedJobExecutor.LPT_SCHEDULING_PROPERTY,
                                IsolatedJobExecutor.CHECKPOINT_INTERVAL_PROPERTY,
                                ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY);
                try {
                        System.clearProperty(IsolatedJobExecutor.LPT_SCHEDULING_PROPERTY);
                        assertThat(IsolatedJobExecutor.isLptSchedulingEnabled(1)).isFalse();
                        assertThat(IsolatedJobExecutor.isLptSchedulingEnabled(4)).isTrue();

                        System.setProperty(IsolatedJobExecutor.LPT_SCHEDULING_PROPERTY, "false");
                        assertThat(IsolatedJobExecutor.isLptSchedulingEnabled(4)).isFalse();

                        System.setProperty(IsolatedJobExecutor.LPT_SCHEDULING_PROPERTY, "true");
                        assertThat(IsolatedJobExecutor.isLptSchedulingEnabled(1)).isTrue();

                        System.setProperty(IsolatedJobExecutor.LPT_SCHEDULING_PROPERTY, "invalido");
                        assertThatThrownBy(() -> IsolatedJobExecutor.isLptSchedulingEnabled(2))
                                        .isInstanceOf(IllegalArgumentException.class);
                        System.clearProperty(IsolatedJobExecutor.LPT_SCHEDULING_PROPERTY);

                        System.clearProperty(IsolatedJobExecutor.CHECKPOINT_INTERVAL_PROPERTY);
                        assertThat(IsolatedJobExecutor.getConfiguredCheckpointInterval())
                                        .isEqualTo(IsolatedJobExecutor.DEFAULT_CHECKPOINT_INTERVAL);
                        System.setProperty(IsolatedJobExecutor.CHECKPOINT_INTERVAL_PROPERTY, "2");
                        assertThat(IsolatedJobExecutor.getConfiguredCheckpointInterval()).isEqualTo(2);
                        System.setProperty(IsolatedJobExecutor.CHECKPOINT_INTERVAL_PROPERTY, "0");
                        assertThatThrownBy(IsolatedJobExecutor::getConfiguredCheckpointInterval)
                                        .isInstanceOf(IllegalArgumentException.class);
                        System.clearProperty(IsolatedJobExecutor.CHECKPOINT_INTERVAL_PROPERTY);

                        long baseSeed = IsolatedJobExecutor.DEFAULT_BASE_SEED;
                        JobSpec light2D = JobSpec.ofCustomBudget("WFG4", 2, "RVEA", 0, baseSeed, 2, 120, true);
                        JobSpec medium5D = JobSpec.ofCustomBudget("WFG4", 5, "NSGA-III", 0, baseSeed, 2, 240, true);
                        JobSpec heavy10D = JobSpec.ofCustomBudget("WFG4", 10, "AGE-MOEA", 0, baseSeed, 2, 320, true);
                        JobSpec heavy15D = JobSpec.ofCustomBudget("WFG4", 15, "FDEA", 0, baseSeed, 2, 200, true);

                        List<JobSpec> originalOrder = List.of(light2D, medium5D, heavy15D, heavy10D);
                        List<JobSpec> lptOrder = IsolatedJobExecutor.orderJobSpecsLpt(originalOrder);
                        assertThat(originalOrder).containsExactly(light2D, medium5D, heavy15D, heavy10D);
                        assertThat(lptOrder).hasSize(4);
                        for (int i = 0; i < lptOrder.size() - 1; i++) {
                                assertThat(IsolatedJobExecutor.estimateRelativeJobCost(lptOrder.get(i)))
                                                .isGreaterThanOrEqualTo(
                                                                IsolatedJobExecutor.estimateRelativeJobCost(
                                                                                lptOrder.get(i + 1)));
                        }
                        assertThat(lptOrder.get(0)).isEqualTo(heavy10D);
                        assertThat(lptOrder.get(lptOrder.size() - 1)).isEqualTo(light2D);

                        // Fail-fast antes de abrir processo filho com heap inviável
                        long childrenBefore = countChildProcessesOfCurrentJvm();
                        System.setProperty(
                                        ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY, "-Xmx32m");
                        assertThatThrownBy(() -> IsolatedJobExecutor.executeJobInSubprocess(light2D, 30_000L))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("-Xmx < 64m");
                        assertThat(countChildProcessesOfCurrentJvm()).isLessThanOrEqualTo(childrenBefore);

                        // Execução real em subprocesso com JVM args customizados produz exatamente os
                        // mesmos objetivos
                        System.clearProperty(ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY);
                        JobExecutionResult defaultSubprocess = IsolatedJobExecutor.executeJobInSubprocess(light2D,
                                        60_000L);

                        System.setProperty(
                                        ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY,
                                        "-Xms64m -Xmx512m -XX:+UseG1GC");
                        JobExecutionResult tunedSubprocess = IsolatedJobExecutor.executeJobInSubprocess(light2D,
                                        60_000L);

                        assertThat(tunedSubprocess.objectivesSha256())
                                        .isEqualTo(defaultSubprocess.objectivesSha256());
                        assertThat(tunedSubprocess.variablesSha256())
                                        .isEqualTo(defaultSubprocess.variablesSha256());
                        assertThat(tunedSubprocess.rawObjectives())
                                        .isDeepEqualTo(defaultSubprocess.rawObjectives());
                        assertThat(countChildProcessesOfCurrentJvm()).isLessThanOrEqualTo(childrenBefore);
                        assertThat(tempDir).exists();
                } finally {
                        restoreProperties(savedProps);
                }
        }

        @Test
        @DisplayName("Checkpointing incremental tolerante a queda no journal, liberação de matrizes e preservação da ordem canônica sob LPT")
        void shouldPersistIncrementalCheckpointReleaseMatricesAndPreserveCanonicalManifestOrder(
                        @TempDir Path tempDir) throws IOException {
                long baseSeed = IsolatedJobExecutor.DEFAULT_BASE_SEED;
                List<JobSpec> specs = List.of(
                                JobSpec.ofCustomBudget("WFG4", 2, "RVEA", 0, baseSeed, 2, 120, true),
                                JobSpec.ofCustomBudget("WFG4", 2, "RVEA", 1, baseSeed, 2, 120, true),
                                JobSpec.ofCustomBudget("WFG4", 2, "RVEA", 2, baseSeed, 2, 120, true),
                                JobSpec.ofCustomBudget("WFG4", 5, "NSGA-III", 0, baseSeed, 2, 240, true),
                                JobSpec.ofCustomBudget("WFG4", 5, "NSGA-III", 1, baseSeed, 2, 240, true),
                                JobSpec.ofCustomBudget("WFG4", 5, "NSGA-III", 2, baseSeed, 2, 240, true));

                BatchExecutionConfig lptCheckpointCfg = new BatchExecutionConfig(3, false, 60_000L, true, null, true,
                                2);
                StudyBatchReport firstReport = IsolatedJobExecutor.executeAndPersistBatch(specs, tempDir,
                                lptCheckpointCfg);

                assertThat(firstReport.executedJobsCount()).isEqualTo(6);
                assertThat(firstReport.resumedJobsCount()).isZero();
                assertThat(firstReport.consolidatedManifest()).hasSize(6);

                // Mesmo com despacho LPT (5D NSGA-III antes de 2D RVEA), manifesto final
                // preserva exatamente a ordem canônica
                for (int i = 0; i < specs.size(); i++) {
                        assertThat(firstReport.consolidatedManifest().get(i).jobId())
                                        .isEqualTo(specs.get(i).jobId());
                }

                // Após persistência em disco, matrizes pesadas em memória são liberadas
                // mantendo escalares e hashes
                for (JobExecutionResult compacted : firstReport.newlyExecutedResults()) {
                        assertThat(compacted.rawObjectives()).isEmpty();
                        assertThat(compacted.rawVariables()).isEmpty();
                        assertThat(compacted.actualEvaluations()).isPositive();
                        assertThat(compacted.rawResultSize()).isPositive();
                        assertThat(compacted.nonDominatedResultSize()).isPositive();
                        assertThat(compacted.objectivesSha256()).hasSize(64);
                        assertThat(compacted.variablesSha256()).hasSize(64);
                }

                Path manifestCsv = tempDir.resolve("run_manifest.csv");
                Path checkpointCsv = tempDir.resolve("run_manifest.checkpoint.csv");
                assertThat(manifestCsv).exists();
                assertThat(checkpointCsv).doesNotExist();
                assertThat(Files.readAllLines(manifestCsv, StandardCharsets.UTF_8)).hasSize(7); // cabeçalho + 6 jobs

                // Simula queda com gravação incompleta antes da consolidação do
                // run_manifest.csv:
                // remove run_manifest.csv e os artefatos do último job, grava os 5 primeiros no
                // journal
                // + uma linha truncada no final para o 6o job.
                Files.delete(manifestCsv);
                JobSpec interruptedLast = specs.get(5);
                Path interruptedCellDir = IsolatedJobExecutor.resolveCellDirectory(tempDir, interruptedLast);
                Files.deleteIfExists(interruptedCellDir.resolve("FUN" + interruptedLast.runId() + ".csv"));
                Files.deleteIfExists(interruptedCellDir.resolve("VAR" + interruptedLast.runId() + ".csv"));
                Files.deleteIfExists(interruptedCellDir.resolve("FUN" + interruptedLast.runId() + ".raw.csv"));
                Files.deleteIfExists(interruptedCellDir.resolve("VAR" + interruptedLast.runId() + ".raw.csv"));

                List<String> journalRows = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                        journalRows.add(firstReport.consolidatedManifest().get(i).toCsvRow());
                }
                journalRows.add("WFG4__m5__NSGA-III__run2,WFG4,5"); // última linha truncada pela queda
                Files.writeString(checkpointCsv, String.join(System.lineSeparator(), journalRows),
                                StandardCharsets.UTF_8);

                StudyBatchReport resumedReport = IsolatedJobExecutor.executeAndPersistBatch(specs, tempDir,
                                lptCheckpointCfg);
                assertThat(resumedReport.resumedJobsCount()).isEqualTo(5);
                assertThat(resumedReport.executedJobsCount()).isEqualTo(1);
                assertThat(resumedReport.consolidatedManifest()).isEqualTo(firstReport.consolidatedManifest());
                assertThat(manifestCsv).exists();
                assertThat(checkpointCsv).doesNotExist();
                assertThat(Files.readAllLines(manifestCsv, StandardCharsets.UTF_8)).hasSize(7);

                // Journal positivo jamais contorna detecção de corrupção física no disco
                Path cellDir5D = tempDir.resolve("data/NSGAIII/WFG4_5");
                Path fun1 = cellDir5D.resolve("FUN1.csv");
                String savedFun1 = Files.readString(fun1, StandardCharsets.UTF_8);
                try {
                        Files.writeString(fun1, "", StandardCharsets.UTF_8);
                        assertThatThrownBy(
                                        () -> IsolatedJobExecutor.executeAndPersistBatch(
                                                        specs, tempDir, lptCheckpointCfg))
                                        .isInstanceOf(CorruptedRunArtifactException.class)
                                        .satisfies(
                                                        ex -> assertThat(((CorruptedRunArtifactException) ex)
                                                                        .getReasonCode())
                                                                        .isEqualTo("EMPTY_FILE"));
                } finally {
                        Files.writeString(fun1, savedFun1, StandardCharsets.UTF_8);
                }
        }

        private static long countChildProcessesOfCurrentJvm() {
                return ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).count();
        }

        private static Map<String, String> snapshotProperties(String... keys) {
                Map<String, String> snap = new HashMap<>();
                for (String k : keys) {
                        snap.put(k, System.getProperty(k));
                }
                return snap;
        }

        private static void restoreProperties(Map<String, String> snap) {
                for (Map.Entry<String, String> e : snap.entrySet()) {
                        if (e.getValue() == null) {
                                System.clearProperty(e.getKey());
                        } else {
                                System.setProperty(e.getKey(), e.getValue());
                        }
                }
        }
}

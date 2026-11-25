package br.usp.lti.newmoea.study;

import static org.assertj.core.api.Assertions.assertThat;

import br.usp.lti.newmoea.statistics.StudyStatisticalAnalyzer;
import br.usp.lti.newmoea.statistics.StudyStatisticalAnalyzer.CellAvailabilityStatus;
import br.usp.lti.newmoea.statistics.StudyStatisticalAnalyzer.StudyStatisticalReport;
import br.usp.lti.newmoea.util.IsolatedJobExecutor;
import br.usp.lti.newmoea.util.PerformanceProfiler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Suíte de testes de integração da Tarefa T36:
 * inferência por célula (KW + Dunn–Holm + orientação de A12, estados de
 * disponibilidade e grupos
 * degenerados), teste global de Friedman por blocos (sem pseudo-replicação),
 * validação cruzada ao
 * vivo com R 4.6.2 ({@code scripts/kruskal_dunn_holm.R}), resiliência quando
 * Rscript está ausente,
 * profiling real CPU/parede/memória e coleta opt-in isolada de
 * {@code R2ExecutionObserver}.
 */
class StudyStatisticalAndProfilingIntegrationTest {

        @Test
        @DisplayName("T36.1: Orientação de A12 (HV maximização vs IGD minimização), grupos iguais degenerados e separação estrita de HV exato parcial (NA) vs HV_MC")
        void testDirectionOrientationDegenerateAndPartialNaSeparation(@TempDir Path tempDir)
                        throws Exception {
                // Verifica orientação de A12
                assertThat(StudyStatisticalAnalyzer.isLowerBetter("HV")).isFalse();
                assertThat(StudyStatisticalAnalyzer.isLowerBetter("HV_MC")).isFalse();
                assertThat(StudyStatisticalAnalyzer.isLowerBetter("IGD")).isTrue();
                assertThat(StudyStatisticalAnalyzer.isLowerBetter("IGD+")).isTrue();
                assertThat(StudyStatisticalAnalyzer.isLowerBetter("R2")).isTrue();

                // Para IGD (minimização): se g1 tem valores MENORES que g2, rawGreaterA12 = 0.1
                // -> betterA12 = 0.9!
                assertThat(StudyStatisticalAnalyzer.orientVarghaDelaneyBetter(0.1, true)).isEqualTo(0.9);
                assertThat(StudyStatisticalAnalyzer.orientVarghaDelaneyBetter(0.9, false)).isEqualTo(0.9);

                // Constrói estudo sintético controlado com:
                // - WFG1_2 em HV com 1 NA em MOEADD (PARTIAL_MISSING_EXACT) + HV_MC completo
                // (COMPLETE)
                // - WFG1_2 em IGD com grupos idênticos degenerados (C=0 -> H=0, p=1)
                Path synthStudy = tempDir.resolve("synth_study");
                Files.createDirectories(synthStudy);
                StringBuilder sb = new StringBuilder("Algorithm,Problem,IndicatorName,ExecutionId,IndicatorValue\n");
                String[] algs = { "NSGAIII", "MOEADD", "RVEA" };
                for (int r = 0; r < 4; r++) {
                        sb.append("NSGAIII,WFG1_2,HV,").append(r).append(",").append(0.80 + 0.01 * r).append("\n");
                        sb.append("MOEADD,WFG1_2,HV,").append(r).append(",").append(r == 3 ? "NA" : (0.60 + 0.01 * r))
                                        .append("\n");
                        sb.append("RVEA,WFG1_2,HV,").append(r).append(",").append(0.40 + 0.01 * r).append("\n");

                        sb.append("NSGAIII,WFG1_2,HV_MC,").append(r).append(",").append(0.80 + 0.01 * r).append("\n");
                        sb.append("MOEADD,WFG1_2,HV_MC,").append(r).append(",").append(0.60 + 0.01 * r).append("\n");
                        sb.append("RVEA,WFG1_2,HV_MC,").append(r).append(",").append(0.40 + 0.01 * r).append("\n");

                        for (String alg : algs) {
                                sb.append(alg).append(",WFG1_2,IGD,").append(r).append(",0.2500\n");
                                // NHV presente no CSV é analisado normalmente (MINIMIZE) sem bloqueio via
                                // código; apenas HV_FALLBACK_MC fica separado!
                                sb.append(alg).append(",WFG1_2,NHV,").append(r).append(",0.5000\n");
                                sb.append(alg).append(",WFG1_2,HV_FALLBACK_MC,").append(r).append(",0.6200\n");
                        }
                }
                Files.writeString(
                                synthStudy.resolve("QualityIndicatorSummary.csv"),
                                sb.toString(),
                                StandardCharsets.UTF_8);

                StudyStatisticalReport rep = StudyStatisticalAnalyzer.analyzeAndExportStudy(synthStudy, null);
                assertThat(rep.cellRecords()).hasSize(4); // HV, HV_MC, IGD e NHV (sem HV_FALLBACK_MC)

                var hvRec = rep.cellRecords().stream()
                                .filter(c -> "HV".equals(c.indicator()))
                                .findFirst()
                                .orElseThrow();
                assertThat(hvRec.availabilityStatus())
                                .isEqualTo(CellAvailabilityStatus.PARTIAL_MISSING_EXACT);
                assertThat(hvRec.totalMissingNaRuns()).isEqualTo(1);
                assertThat(hvRec.validCountByAlgorithm().get("MOEADD")).isEqualTo(3);

                var hvMcRec = rep.cellRecords().stream()
                                .filter(c -> "HV_MC".equals(c.indicator()))
                                .findFirst()
                                .orElseThrow();
                assertThat(hvMcRec.availabilityStatus()).isEqualTo(CellAvailabilityStatus.COMPLETE);
                assertThat(hvMcRec.bestAlgorithmByMedian()).isEqualTo("NSGAIII");

                var igdRec = rep.cellRecords().stream()
                                .filter(c -> "IGD".equals(c.indicator()))
                                .findFirst()
                                .orElseThrow();
                assertThat(igdRec.availabilityStatus()).isEqualTo(CellAvailabilityStatus.COMPLETE);
                assertThat(igdRec.kwResult().getH()).isEqualTo(0.0);
                assertThat(igdRec.kwResult().getPValue()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("T36.2: Fixture de 60 jobs em 2 lotes (WFG1_2 + WFG4_2, 5 runs), Friedman global por blocos, paridade ao vivo com R 4.6.2 e resiliência sem Rscript")
        void testMultiBatchStudyFixtureLiveR462ValidationAndRscriptFallback(@TempDir Path tempDir)
                        throws Exception {
                // 1. Confirma modo --fixture preservado em scripts/kruskal_dunn_holm.R quando
                // Rscript está presente
                Path rExe = StudyStatisticalAnalyzer.resolveRscriptExecutable();
                if (rExe != null) {
                        Process p = new ProcessBuilder(
                                        rExe.toString(),
                                        Path.of("scripts", "kruskal_dunn_holm.R")
                                                        .toAbsolutePath()
                                                        .toString(),
                                        "--fixture")
                                        .redirectErrorStream(true)
                                        .start();
                        StringBuilder fixtureOut = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(
                                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = br.readLine()) != null) {
                                        fixtureOut.append(line).append('\n');
                                }
                        }
                        assertThat(p.waitFor()).isZero();
                        assertThat(fixtureOut.toString())
                                        .contains("DUNN_IMPLEMENTATION=BASE_R_ANALYTICAL_EXACT")
                                        .contains("9.855396");
                }

                // 2. Executa 2 lotes em WFGStudy.main() (Lote 1: runs 0..2 em WFG1_2; Lote 2:
                // runs 3..4 em WFG1_2 + runs 0..4 em WFG4_2 -> 60 jobs reais baratos!)
                Path studyDir = tempDir.resolve("T36_live_study");
                String prevOut = System.getProperty("study.outputDirectory");
                String prevObj = System.getProperty("objectives");
                String prevProb = System.getProperty("problem");
                String prevRunStart = System.getProperty("runStart");
                String prevRuns = System.getProperty("runs");
                String prevBudget = System.getProperty("study.maxEvaluationsOverride");
                String prevMcSamples = System.getProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY);
                String prevRPath = System.getProperty(StudyStatisticalAnalyzer.RSCRIPT_PATH_PROPERTY);
                try {
                        System.setProperty("study.outputDirectory", studyDir.toString());
                        System.setProperty("objectives", "2");
                        System.setProperty("study.maxEvaluationsOverride", "100");
                        System.setProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY, "30000");
                        System.clearProperty(StudyStatisticalAnalyzer.RSCRIPT_PATH_PROPERTY);

                        // Lote A: WFG1_2 runs 0..2 (18 jobs)
                        System.setProperty("problem", "WFG1_2");
                        System.setProperty("runStart", "0");
                        System.setProperty("runs", "3");
                        WFGStudy.main(new String[0]);

                        // Lote B: WFG1_2 runs 3..4 (12 jobs, preservando 0..2!)
                        System.setProperty("runStart", "3");
                        System.setProperty("runs", "2");
                        WFGStudy.main(new String[0]);

                        // Lote C: WFG4_2 runs 0..4 (30 jobs, preservando WFG1_2!)
                        System.setProperty("problem", "WFG4_2");
                        System.setProperty("runStart", "0");
                        System.setProperty("runs", "5");
                        WFGStudy.main(new String[0]);

                        // Verifica consolidação de 60 jobs no run_manifest.csv e
                        // job_execution_profile.csv
                        List<String> runManifestLines = Files.readAllLines(studyDir.resolve("run_manifest.csv"),
                                        StandardCharsets.UTF_8);
                        assertThat(runManifestLines).hasSize(61); // cabeçalho + 60 jobs

                        Path profileCsv = studyDir.resolve("profiling/job_execution_profile.csv");
                        assertThat(profileCsv).exists();
                        String profileContent = Files.readString(profileCsv, StandardCharsets.UTF_8);
                        assertThat(profileContent)
                                        .contains("FULL_CANONICAL_FRONT")
                                        .contains("JVM_PROCESS_SHARED_HEAP");

                        // Reexecuta análise consolidada sobre todo o QualityIndicatorSummary.csv (ambos
                        // os problemas WFG1_2 e WFG4_2)
                        StudyStatisticalReport fullReport = StudyStatisticalAnalyzer.analyzeAndExportStudy(studyDir,
                                        null);
                        // 2 problemas x 5 indicadores ativos (HV, HV_MC, IGD, IGD+, R2; sem NHV) = 10
                        // células COMPLETE
                        assertThat(fullReport.cellRecords()).hasSize(10);
                        assertThat(fullReport.friedmanResults()).hasSize(5); // 5 indicadores, cada um com
                                                                             // blocks_count=2!
                        for (var f : fullReport.friedmanResults()) {
                                assertThat(f.blockCount()).isEqualTo(2);
                                assertThat(f.algorithmCount()).isEqualTo(6);
                        }
                        assertThat(fullReport.latexSummaryPath()).exists();

                        if (rExe != null) {
                                assertThat(fullReport.rValidationReport().executed()).isTrue();
                                assertThat(fullReport.rValidationReport().passed())
                                                .as("Validação cruzada ao vivo contra R 4.6.2 deve passar: %s",
                                                                fullReport.rValidationReport().diagnosticMessage())
                                                .isTrue();
                                assertThat(fullReport.rValidationReport().statusCode())
                                                .isEqualTo("R_CROSS_VALIDATION_PASSED");
                                assertThat(fullReport.rValidationReport().validatedCellsCount()).isEqualTo(10);
                                assertThat(fullReport.rValidationReport().validatedPairsCount()).isEqualTo(150); // 10
                                                                                                                 // células
                                                                                                                 // x
                                                                                                                 // C(6,2)=15
                                                                                                                 // pares
                                assertThat(fullReport.rValidationReport().maxAbsDiffH()).isLessThanOrEqualTo(1e-6);
                                assertThat(fullReport.rValidationReport().maxAbsDiffPHolm()).isLessThanOrEqualTo(1e-5);
                        }

                        // 3. Resiliência quando Rscript está indisponível: pós-processamento Java NÃO
                        // falha e registra R_NOT_AVAILABLE_PENDING
                        System.setProperty(
                                        StudyStatisticalAnalyzer.RSCRIPT_PATH_PROPERTY,
                                        tempDir.resolve("inexistente_Rscript.exe").toString());
                        StudyStatisticalReport noRReport = StudyStatisticalAnalyzer.analyzeAndExportStudy(studyDir,
                                        null);
                        assertThat(noRReport.rValidationReport().executed()).isFalse();
                        assertThat(noRReport.rValidationReport().statusCode())
                                        .isEqualTo("R_NOT_AVAILABLE_PENDING");
                        assertThat(noRReport.kruskalCsvPath()).exists();
                        assertThat(noRReport.latexSummaryPath()).exists();
                } finally {
                        restoreProp("study.outputDirectory", prevOut);
                        restoreProp("objectives", prevObj);
                        restoreProp("problem", prevProb);
                        restoreProp("runStart", prevRunStart);
                        restoreProp("runs", prevRuns);
                        restoreProp("study.maxEvaluationsOverride", prevBudget);
                        restoreProp(WFGStudy.HV_MC_SAMPLES_PROPERTY, prevMcSamples);
                        restoreProp(StudyStatisticalAnalyzer.RSCRIPT_PATH_PROPERTY, prevRPath);
                }
        }

        @Test
        @DisplayName("T36.3: R2ExecutionObserver opt-in (desativado por padrão vs habilitado nos 6 algoritmos sem adicionar avaliações de objetivos)")
        void testOptInR2ExecutionObserverDefaultDisabledAndZeroExtraEvaluations(@TempDir Path tempDir)
                        throws Exception {
                Path outDefault = tempDir.resolve("r2_default_off");
                Path outOptIn = tempDir.resolve("r2_opt_in_on");

                String prevOut = System.getProperty("study.outputDirectory");
                String prevObj = System.getProperty("objectives");
                String prevProb = System.getProperty("problem");
                String prevRuns = System.getProperty("runs");
                String prevBudget = System.getProperty("study.maxEvaluationsOverride");
                String prevMcSamples = System.getProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY);
                String prevR2Obs = System.getProperty(IsolatedJobExecutor.ENABLE_R2_OBSERVER_PROPERTY);
                try {
                        System.setProperty("objectives", "2");
                        System.setProperty("problem", "WFG2_2");
                        System.setProperty("runs", "1");
                        System.setProperty("study.maxEvaluationsOverride", "300"); // 3 gerações de 100
                        System.setProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY, "20000");

                        // 1. Default (false): nenhuma curva geracional R2_trace_run0.csv gerada, mas
                        // indicador R2 final permanece ativo!
                        System.clearProperty(IsolatedJobExecutor.ENABLE_R2_OBSERVER_PROPERTY);
                        assertThat(IsolatedJobExecutor.isR2ObserverEnabled()).isFalse();
                        System.setProperty("study.outputDirectory", outDefault.toString());
                        WFGStudy.main(new String[0]);

                        assertThat(outDefault.resolve("data/NSGAIII/WFG2_2/R2")).exists();
                        assertThat(outDefault.resolve("data/NSGAIII/WFG2_2/R2_trace_run0.csv")).doesNotExist();

                        // 2. Opt-in explícito (-Dstudy.enableR2Observer=true): coleta isolada por run
                        // nos 6 algoritmos com ZERO avaliações extras!
                        System.setProperty(IsolatedJobExecutor.ENABLE_R2_OBSERVER_PROPERTY, "true");
                        System.setProperty("study.outputDirectory", outOptIn.toString());
                        WFGStudy.main(new String[0]);

                        for (String algTag : List.of("NSGAIII", "MOEADD", "FDEA", "RVEA", "AGE-MOEA", "AGE-MOEA-II")) {
                                Path traceCsv = outOptIn.resolve("data").resolve(algTag)
                                                .resolve("WFG2_2/R2_trace_run0.csv");
                                assertThat(traceCsv).as("Trajetória R2 opt-in deve existir para %s", algTag).exists();
                                List<String> lines = Files.readAllLines(traceCsv, StandardCharsets.UTF_8);
                                assertThat(lines.get(0)).isEqualTo("step,r2,delta_r2,timestamp_ms");
                                assertThat(lines.size()).as("Deve registrar >= 2 pontos geracionais em %s", algTag)
                                                .isGreaterThanOrEqualTo(3);
                        }

                        // Verifica no run_manifest.csv que NENHUMA avaliação extra de função objetivo
                        // foi adicionada (actual == 300 exato!)
                        List<String> manifestLines = Files.readAllLines(outOptIn.resolve("run_manifest.csv"),
                                        StandardCharsets.UTF_8);
                        assertThat(manifestLines).hasSize(7);
                        for (int i = 1; i < manifestLines.size(); i++) {
                                String[] cols = manifestLines.get(i).split(",", -1);
                                int targetEvals = Integer.parseInt(cols[9].trim());
                                int actualEvals = Integer.parseInt(cols[10].trim());
                                assertThat(actualEvals).isEqualTo(targetEvals).isEqualTo(300);
                        }

                        // Verifica também snapshot de recursos do PerformanceProfiler
                        PerformanceProfiler.ProcessResourceSnapshot snap = PerformanceProfiler
                                        .captureProcessAndJvmResourceSnapshot();
                        assertThat(snap.availableProcessors()).isGreaterThanOrEqualTo(1);
                        assertThat(snap.memoryScope()).isEqualTo("JVM_PROCESS_SHARED_HEAP");
                        assertThat(snap.heapUsedMb()).isGreaterThanOrEqualTo(0.0);
                } finally {
                        restoreProp("study.outputDirectory", prevOut);
                        restoreProp("objectives", prevObj);
                        restoreProp("problem", prevProb);
                        restoreProp("runs", prevRuns);
                        restoreProp("study.maxEvaluationsOverride", prevBudget);
                        restoreProp(WFGStudy.HV_MC_SAMPLES_PROPERTY, prevMcSamples);
                        restoreProp(IsolatedJobExecutor.ENABLE_R2_OBSERVER_PROPERTY, prevR2Obs);
                }
        }

        private static void restoreProp(String key, String prev) {
                if (prev == null) {
                        System.clearProperty(key);
                } else {
                        System.setProperty(key, prev);
                }
        }
}

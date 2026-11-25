package br.usp.lti.newmoea.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.util.HypervolumeResult;
import br.usp.lti.newmoea.util.MonteCarloHypervolume;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.NormalizationBounds;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.NormalizationMode;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor.HvMethod;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor.MultiMethodHypervolumeEvaluation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Suíte de testes de integração científica e numérica (T35):
 * paridade exata entre {@code PISA}, {@code WFG} e
 * {@code MonteCarloHypervolume} na mesma caixa
 * \([\mathbf{0}, \tilde{\mathbf{r}}]\) (2D manual e 15D analítico), suspensão
 * de {@code NHV}, reuso sem recomputação de {@code HV_MC} e fallback
 * generalizado único ligar/desligar quando apenas hipervolumes pesados são
 * solicitados.
 */
class StudyIndicatorPipelineIntegrationTest {

        @Test
        @DisplayName("T35.1: Paridade exata 2D à mão e 15D analítica entre PISA isolado, WFG isolado, Jacobiano e Monte Carlo na mesma caixa")
        void testExact2DAnd15DBoxParityBetweenIsolatedPisaWfgAndMonteCarlo() {
                ProcessIsolatedHypervolumeExecutor executor = new ProcessIsolatedHypervolumeExecutor(15_000L, 500L,
                                400_000L);

                // 1. Oráculo 2D calculado à mão para WFG4 (a=(0,0), b=(2,4), r_raw=(2.2, 4.4),
                // r_norm=(1.1, 1.1), det(J)=8.0)
                double[][] rawFront2D = {
                                { 1.0, 2.0 },
                                { 0.4, 3.2 },
                                { 1.8, 0.8 }
                };
                NormalizationBounds bounds2D = ObjectiveNormalizationContract.resolveBounds(
                                "WFG4", 2, NormalizationMode.THEORETICAL_PARETO_BOUNDS, null);
                double[][] normFront2D = ObjectiveNormalizationContract.normalizeFront(rawFront2D, bounds2D);

                HypervolumeResult pisaNorm2D = executor.computeExactOnly(
                                normFront2D, new double[2], bounds2D.normalizedHvReferencePoint(), "PISA");
                HypervolumeResult wfgNorm2D = executor.computeExactOnly(
                                normFront2D, new double[2], bounds2D.normalizedHvReferencePoint(), "WFG");
                HypervolumeResult pisaRaw2D = executor.computeExactOnly(
                                rawFront2D, bounds2D.lowerBounds(), bounds2D.rawHvReferencePoint(), "PISA");
                HypervolumeResult mcNorm2D = MonteCarloHypervolume.compute(
                                normFront2D, new double[2], bounds2D.normalizedHvReferencePoint(), 400_000L, 2026L);

                assertThat(pisaNorm2D.getStatus()).isEqualTo(HypervolumeResult.ExecutionStatus.SUCCESS_EXACT);
                assertThat(wfgNorm2D.getStatus()).isEqualTo(HypervolumeResult.ExecutionStatus.SUCCESS_EXACT);
                assertThat(pisaNorm2D.getValue()).isCloseTo(0.51, Offset.offset(1e-12));
                assertThat(wfgNorm2D.getValue()).isCloseTo(0.51, Offset.offset(1e-12));
                assertThat(pisaRaw2D.getValue()).isCloseTo(4.08, Offset.offset(1e-12));
                assertThat(pisaNorm2D.getValue() * bounds2D.jacobianDeterminant())
                                .isCloseTo(pisaRaw2D.getValue(), Offset.offset(1e-12));
                assertThat(mcNorm2D.getValue()).isCloseTo(0.51, Offset.offset(5e-3));
                assertThat(0.51).isBetween(mcNorm2D.getCiLower() - 2e-3, mcNorm2D.getCiUpper() + 2e-3);

                // 2. Oráculo geométrico fechado em m=15 com caixa r_norm = (1.1, ..., 1.1):
                // Dois pontos não-dominados P_A = (0.2, 0.5, ..., 0.5) e P_B = (0.5, 0.2, 0.5,
                // ..., 0.5)
                // Volume exato por inclusão-exclusão:
                // V(A) = 0.9 * 0.6^14, V(B) = 0.6 * 0.9 * 0.6^13 = 0.9 * 0.6^14
                // V(A inter B) = 0.6 * 0.6 * 0.6^13 = 0.6^15
                // HV_exato = 2 * (0.9 * 0.6^14) - 0.6^15 = (1.8 - 0.6) * 0.6^14 = 1.2 * 0.6^14
                int m15 = 15;
                double[] ref15 = new double[m15];
                Arrays.fill(ref15, 1.1);
                double[] pA = new double[m15];
                double[] pB = new double[m15];
                Arrays.fill(pA, 0.5);
                Arrays.fill(pB, 0.5);
                pA[0] = 0.2;
                pB[1] = 0.2;
                double[][] front15 = { pA, pB };
                double expectedExact15D = 1.2 * Math.pow(0.6, 14);

                HypervolumeResult pisa15 = executor.computeExactOnly(front15, new double[m15], ref15, "PISA");
                HypervolumeResult wfg15 = executor.computeExactOnly(front15, new double[m15], ref15, "WFG");
                HypervolumeResult mc15 = MonteCarloHypervolume.compute(front15, new double[m15], ref15, 1_000_000L,
                                777L);

                assertThat(pisa15.getStatus()).isEqualTo(HypervolumeResult.ExecutionStatus.SUCCESS_EXACT);
                assertThat(wfg15.getStatus()).isEqualTo(HypervolumeResult.ExecutionStatus.SUCCESS_EXACT);
                assertThat(pisa15.getValue()).isCloseTo(expectedExact15D, Offset.offset(1e-12));
                assertThat(wfg15.getValue()).isCloseTo(expectedExact15D, Offset.offset(1e-12));
                assertThat(mc15.getValue()).isCloseTo(expectedExact15D, Offset.offset(3e-4));

                // 3. Zero acertos em caixa esparsa em 15D -> estimativa pontual 0.0, mas limite
                // superior de Wilson > 0 (não equivale a volume zero!)
                double[] tinyCorner = new double[m15];
                Arrays.fill(tinyCorner, 1.099);
                HypervolumeResult zeroHitMc = MonteCarloHypervolume.compute(
                                new double[][] { tinyCorner }, new double[m15], ref15, 1_000L, 42L);
                assertThat(zeroHitMc.getHitCount()).isZero();
                assertThat(zeroHitMc.getValue()).isEqualTo(0.0);
                assertThat(zeroHitMc.getCiUpper()).isStrictlyBetween(0.0, Math.pow(1.1, 15));
        }

        @Test
        @DisplayName("T35.2: Orquestração multi-método generalizada — reuso de MC em DUAL e fallback único ligar/desligar para pesados (PISA, WFG, PISA+WFG)")
        void testGeneralizedMultiMethodFallbackReuseAndToggle() {
                double[][] front = {
                                { 0.3, 0.7 },
                                { 0.7, 0.3 }
                };
                double[] lower = { 0.0, 0.0 };
                double[] ref = { 1.1, 1.1 };
                String prevHang = System.getProperty("hype.hvWorkerSimulateHangMs");
                String prevCrash = System.getProperty("hype.hvWorkerSimulateCrash");
                try {
                        // Força timeout controlado (hang de 5s contra deadline de 120ms)
                        System.setProperty("hype.hvWorkerSimulateHangMs", "5000");
                        ProcessIsolatedHypervolumeExecutor timeoutExec = new ProcessIsolatedHypervolumeExecutor(120L,
                                        100L, 50_000L);

                        // Caso A: PISA + MC (perfil padrão DUAL) em timeout -> MC foi calculado 1 vez e
                        // REUTILIZADO sem recomputar!
                        MultiMethodHypervolumeEvaluation dualEval = timeoutExec.evaluateConfiguredHypervolumes(
                                        front,
                                        lower,
                                        ref,
                                        EnumSet.of(HvMethod.PISA, HvMethod.MC),
                                        true,
                                        99L);
                        assertThat(dualEval.pisaResult().getStatus())
                                        .isEqualTo(HypervolumeResult.ExecutionStatus.TIMEOUT_NO_FALLBACK);
                        assertThat(dualEval.pisaResult().getValue()).isNaN();
                        assertThat(dualEval.mcComputationCount()).isEqualTo(1);
                        assertThat(dualEval.fallbackTriggered()).isTrue();
                        assertThat(dualEval.effectiveHvSource()).isEqualTo("REUSED_MC_FALLBACK");
                        assertThat(dualEval.effectiveHvValue()).isEqualTo(dualEval.mcResult().getValue())
                                        .isGreaterThan(0.4);

                        // Caso B: DOIS hipervolumes pesados (PISA + WFG, sem MC) e AMBOS entram em
                        // timeout + toggle LIGADO -> fallback MC roda EXATAMENTE 1 VEZ!
                        MultiMethodHypervolumeEvaluation heavyBothTimeoutOn = timeoutExec
                                        .evaluateConfiguredHypervolumes(
                                                        front,
                                                        lower,
                                                        ref,
                                                        EnumSet.of(HvMethod.PISA, HvMethod.WFG),
                                                        true,
                                                        99L);
                        assertThat(heavyBothTimeoutOn.pisaResult().getValue()).isNaN();
                        assertThat(heavyBothTimeoutOn.wfgResult().getValue()).isNaN();
                        assertThat(heavyBothTimeoutOn.mcComputationCount())
                                        .as("Fallback deve ocorrer 1 única vez mesmo quando múltiplos HVs pesados sofrem timeout")
                                        .isEqualTo(1);
                        assertThat(heavyBothTimeoutOn.fallbackTriggered()).isTrue();
                        assertThat(heavyBothTimeoutOn.effectiveHvSource()).isEqualTo("FALLBACK_MC_SINGLE_SHOT");
                        assertThat(heavyBothTimeoutOn.effectiveHvValue()).isEqualTo(dualEval.mcResult().getValue());
                        assertThat(heavyBothTimeoutOn.fallbackWarning()).contains("1 unica vez");

                        // Caso C: DOIS hipervolumes pesados (PISA + WFG, sem MC) em timeout + toggle
                        // DESLIGADO (false) -> 0 chamadas a MC, valor efetivo NaN (nunca 0.0!)
                        MultiMethodHypervolumeEvaluation heavyBothTimeoutOff = timeoutExec
                                        .evaluateConfiguredHypervolumes(
                                                        front,
                                                        lower,
                                                        ref,
                                                        EnumSet.of(HvMethod.PISA, HvMethod.WFG),
                                                        false,
                                                        99L);
                        assertThat(heavyBothTimeoutOff.mcComputationCount()).isZero();
                        assertThat(heavyBothTimeoutOff.mcResult()).isNull();
                        assertThat(heavyBothTimeoutOff.fallbackTriggered()).isFalse();
                        assertThat(heavyBothTimeoutOff.effectiveHvValue()).isNaN();
                        assertThat(heavyBothTimeoutOff.effectiveHvSource()).isEqualTo("NONE_FALLBACK_DISABLED");

                        // Caso D: Crash do worker exato em modo apenas WFG (sem MC) + toggle LIGADO ->
                        // fallback único acionado sem derrubar o processo!
                        System.clearProperty("hype.hvWorkerSimulateHangMs");
                        System.setProperty("hype.hvWorkerSimulateCrash", "true");
                        ProcessIsolatedHypervolumeExecutor crashExec = new ProcessIsolatedHypervolumeExecutor(5_000L,
                                        100L, 50_000L);
                        MultiMethodHypervolumeEvaluation wfgCrashFallback = crashExec.evaluateConfiguredHypervolumes(
                                        front, lower, ref, Set.of(HvMethod.WFG), true, 99L);
                        assertThat(wfgCrashFallback.wfgResult().getStatus())
                                        .isEqualTo(HypervolumeResult.ExecutionStatus.CRASH_NO_FALLBACK);
                        assertThat(wfgCrashFallback.wfgResult().getValue()).isNaN();
                        assertThat(wfgCrashFallback.mcComputationCount()).isEqualTo(1);
                        assertThat(wfgCrashFallback.fallbackTriggered()).isTrue();
                        assertThat(wfgCrashFallback.effectiveHvSource()).isEqualTo("FALLBACK_MC_SINGLE_SHOT");
                } finally {
                        restoreProp("hype.hvWorkerSimulateHangMs", prevHang);
                        restoreProp("hype.hvWorkerSimulateCrash", prevCrash);
                }

                assertThat(ProcessIsolatedHypervolumeExecutor.parseConfiguredHvMethods("NHV", null))
                                .containsExactly(HvMethod.NHV);
                assertThat(ProcessIsolatedHypervolumeExecutor.parseConfiguredHvMethods("PISA,MC,NHV", null))
                                .containsExactlyInAnyOrder(HvMethod.PISA, HvMethod.MC, HvMethod.NHV);
        }

        @Test
        @DisplayName("Parser e resolução em ordem de regras por dimensão (-Dstudy.hvMethodsByM)")
        void testParseAndResolveHvMethodsByObjectiveCountRules() {
                int[] allObjectives = new int[] { 2, 3, 5, 8, 10, 15 };

                // Ordem de avaliação (primeira regra que casa vence): <=4 -> PISA+MC, <8 ->
                // PISA+WFG+MC (casa 5), >=8 -> MC
                Map<Integer, Set<HvMethod>> tiered = ProcessIsolatedHypervolumeExecutor
                                .resolveHvMethodsByObjectiveCount(
                                                allObjectives,
                                                "<=4:PISA,MC;<8:PISA,MC,WFG;>=8:MC",
                                                EnumSet.of(HvMethod.PISA, HvMethod.MC),
                                                false);
                assertThat(tiered.get(2)).containsExactlyInAnyOrder(HvMethod.PISA, HvMethod.MC);
                assertThat(tiered.get(3)).containsExactlyInAnyOrder(HvMethod.PISA, HvMethod.MC);
                assertThat(tiered.get(5))
                                .containsExactlyInAnyOrder(HvMethod.PISA, HvMethod.WFG, HvMethod.MC);
                assertThat(tiered.get(8)).containsExactly(HvMethod.MC);
                assertThat(tiered.get(10)).containsExactly(HvMethod.MC);
                assertThat(tiered.get(15)).containsExactly(HvMethod.MC);

                // Aliases amigáveis a shell (lt, le, gt, ge, eq), faixas, listas e wildcard
                Map<Integer, Set<HvMethod>> shellSafe = ProcessIsolatedHypervolumeExecutor
                                .resolveHvMethodsByObjectiveCount(
                                                allObjectives,
                                                "2,3:PISA,MC;eq5:WFG,MC;8-10:MC;gt10:MC",
                                                EnumSet.of(HvMethod.PISA, HvMethod.MC),
                                                false);
                assertThat(shellSafe.get(2)).containsExactlyInAnyOrder(HvMethod.PISA, HvMethod.MC);
                assertThat(shellSafe.get(5)).containsExactlyInAnyOrder(HvMethod.WFG, HvMethod.MC);
                assertThat(shellSafe.get(8)).containsExactly(HvMethod.MC);
                assertThat(shellSafe.get(15)).containsExactly(HvMethod.MC);

                // Fallback explícito quando study.hvMethods também foi informado pelo operador
                Map<Integer, Set<HvMethod>> withFallback = ProcessIsolatedHypervolumeExecutor
                                .resolveHvMethodsByObjectiveCount(
                                                allObjectives,
                                                "<=5:PISA,MC",
                                                EnumSet.of(HvMethod.MC),
                                                true);
                assertThat(withFallback.get(5)).containsExactlyInAnyOrder(HvMethod.PISA, HvMethod.MC);
                assertThat(withFallback.get(8)).containsExactly(HvMethod.MC);
                assertThat(withFallback.get(15)).containsExactly(HvMethod.MC);

                // Falha rápida quando alguma dimensão não casa sem fallback explícito
                assertThatThrownBy(
                                () -> ProcessIsolatedHypervolumeExecutor
                                                .resolveHvMethodsByObjectiveCount(
                                                                allObjectives,
                                                                "<=5:PISA,MC",
                                                                EnumSet.of(HvMethod.PISA, HvMethod.MC),
                                                                false))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("m=8");

                // Regras explícitas com NHV funcionam normalmente (sem bloqueio em código), e
                // validações sintáticas continuam falhando rápido
                var withNhv = ProcessIsolatedHypervolumeExecutor.resolveHvMethodsByObjectiveCount(
                                allObjectives,
                                "<=5:PISA,NHV;*:MC",
                                EnumSet.of(HvMethod.PISA, HvMethod.MC),
                                false);
                assertThat(withNhv.get(2)).containsExactlyInAnyOrder(HvMethod.PISA, HvMethod.NHV);
                assertThat(withNhv.get(8)).containsExactly(HvMethod.MC);

                assertThatThrownBy(
                                () -> ProcessIsolatedHypervolumeExecutor.parseHvMethodsByMRules(
                                                "<=5:INVALIDO;*:MC"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("INVALIDO");
                assertThatThrownBy(
                                () -> ProcessIsolatedHypervolumeExecutor.parseHvMethodsByMRules(
                                                "8-5:MC"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("invertida");
                assertThatThrownBy(
                                () -> ProcessIsolatedHypervolumeExecutor.parseHvMethodsByMRules(
                                                "sem_dois_pontos"))
                                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Integração ponta a ponta com -Dstudy.hvMethodsByM: PISA+MC até m=2 e apenas MC para m>=3")
        void testEndToEndWfgStudyHvMethodsByObjectiveCount(@TempDir Path tempDir) throws Exception {
                Path outDir = tempDir.resolve("study_hv_by_m");

                String prevOut = System.getProperty("study.outputDirectory");
                String prevObj = System.getProperty("objectives");
                String prevProb = System.getProperty("problem");
                String prevRuns = System.getProperty("runs");
                String prevBudget = System.getProperty("study.maxEvaluationsOverride");
                String prevHvMethods = System.getProperty(WFGStudy.HV_METHODS_PROPERTY);
                String prevHvMethodsByM = System.getProperty(WFGStudy.HV_METHODS_BY_M_PROPERTY);
                String prevMcSamples = System.getProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY);
                String prevHvTimeout = System.getProperty("perf.hvTimeoutMs");
                String prevSkipR = System.getProperty("study.skipRValidation");

                try {
                        System.setProperty("study.outputDirectory", outDir.toString());
                        System.setProperty("objectives", "2,3");
                        System.setProperty("problem", "WFG4");
                        System.setProperty("runs", "2");
                        System.setProperty("study.maxEvaluationsOverride", "200");
                        System.clearProperty(WFGStudy.HV_METHODS_PROPERTY);
                        System.setProperty(WFGStudy.HV_METHODS_BY_M_PROPERTY, "<=2:PISA,MC;>=3:MC");
                        System.setProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY, "50000");
                        System.setProperty("perf.hvTimeoutMs", "15000");
                        System.setProperty("study.skipRValidation", "true");

                        assertThat(WFGStudy.isHvMethodsByMEnabled()).isTrue();
                        WFGStudy.main(new String[0]);

                        Path cell2d = outDir.resolve("data/NSGAIII/WFG4_2");
                        Path cell3d = outDir.resolve("data/NSGAIII/WFG4_3");
                        List<String> hv2dLines = Files.readAllLines(cell2d.resolve("HV"), StandardCharsets.UTF_8);
                        List<String> hvMc2dLines = Files.readAllLines(cell2d.resolve("HV_MC"), StandardCharsets.UTF_8);
                        assertThat(hv2dLines).hasSize(2);
                        assertThat(hvMc2dLines).hasSize(2);
                        assertThat(Double.parseDouble(hv2dLines.get(0).trim())).isGreaterThan(0.0);
                        assertThat(Double.parseDouble(hvMc2dLines.get(0).trim())).isGreaterThan(0.0);

                        List<String> hv3dLines = Files.readAllLines(cell3d.resolve("HV"), StandardCharsets.UTF_8);
                        List<String> hvMc3dLines = Files.readAllLines(cell3d.resolve("HV_MC"), StandardCharsets.UTF_8);
                        assertThat(hv3dLines).containsExactly("NA", "NA");
                        assertThat(hvMc3dLines).hasSize(2);
                        assertThat(Double.parseDouble(hvMc3dLines.get(0).trim())).isGreaterThan(0.0);

                        String summary = Files.readString(
                                        outDir.resolve("QualityIndicatorSummary.csv"), StandardCharsets.UTF_8);
                        assertThat(summary)
                                        .contains("NSGAIII,WFG4_3,HV,0,NA")
                                        .contains("NSGAIII,WFG4_3,HV,1,NA")
                                        .contains("NSGAIII,WFG4_2,HV,0,");

                        String audit = Files.readString(
                                        outDir.resolve("hv_audit_manifest.csv"), StandardCharsets.UTF_8);
                        assertThat(audit)
                                        .contains("WFG4_2,2,0,THEORETICAL_PARETO_BOUNDS,")
                                        .contains("PISA+MC,true,SUCCESS_EXACT,")
                                        .contains("WFG4_3,3,0,THEORETICAL_PARETO_BOUNDS,")
                                        .contains(",MC,true,NOT_CONFIGURED,NA,");

                        String kwCsv = Files.readString(
                                        outDir.resolve("statistics/kruskal_cells.csv"), StandardCharsets.UTF_8);
                        assertThat(kwCsv)
                                        .contains("WFG4_2,2,HV,MAXIMIZE,COMPLETE,")
                                        .contains("WFG4_3,3,HV,MAXIMIZE,UNAVAILABLE_ALL_NA,")
                                        .contains("WFG4_3,3,HV_MC,MAXIMIZE,COMPLETE,");

                        // Segunda execução com os mesmos parâmetros reaproveita indicadores sem falhar
                        WFGStudy.main(new String[0]);
                        assertThat(Files.readAllLines(cell3d.resolve("HV"), StandardCharsets.UTF_8))
                                        .containsExactly("NA", "NA");
                } finally {
                        restoreProp("study.outputDirectory", prevOut);
                        restoreProp("objectives", prevObj);
                        restoreProp("problem", prevProb);
                        restoreProp("runs", prevRuns);
                        restoreProp("study.maxEvaluationsOverride", prevBudget);
                        restoreProp(WFGStudy.HV_METHODS_PROPERTY, prevHvMethods);
                        restoreProp(WFGStudy.HV_METHODS_BY_M_PROPERTY, prevHvMethodsByM);
                        restoreProp(WFGStudy.HV_MC_SAMPLES_PROPERTY, prevMcSamples);
                        restoreProp("perf.hvTimeoutMs", prevHvTimeout);
                        restoreProp("study.skipRValidation", prevSkipR);
                }
        }

        @Test
        @DisplayName("T35.3: Integração ponta a ponta pelo entrypoint WFGStudy.main() — ausência de NHV, dupla série HV+HV_MC e resiliência a NA em timeout")
        void testEndToEndWfgStudyIndicatorPipelineAndTimeoutFallback(@TempDir Path tempDir)
                        throws Exception {
                Path outDual = tempDir.resolve("study_dual");
                Path outHeavyTimeout = tempDir.resolve("study_heavy_timeout");

                String prevOut = System.getProperty("study.outputDirectory");
                String prevObj = System.getProperty("objectives");
                String prevProb = System.getProperty("problem");
                String prevRuns = System.getProperty("runs");
                String prevBudget = System.getProperty("study.maxEvaluationsOverride");
                String prevHvMethods = System.getProperty(WFGStudy.HV_METHODS_PROPERTY);
                String prevFallback = System.getProperty(WFGStudy.HV_FALLBACK_ON_ALL_EXACT_TIMEOUT_PROPERTY);
                String prevMcSamples = System.getProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY);
                String prevHvTimeout = System.getProperty("perf.hvTimeoutMs");
                String prevHang = System.getProperty("hype.hvWorkerSimulateHangMs");
                String prevSkipR = System.getProperty("study.skipRValidation");

                try {
                        // 1. Execução normal no perfil ativo padrão (PISA,MC): sem NHV, com HV e HV_MC
                        // consistentes
                        System.setProperty("study.outputDirectory", outDual.toString());
                        System.setProperty("objectives", "2");
                        System.setProperty("problem", "WFG4_2");
                        System.setProperty("runs", "1");
                        System.setProperty("study.maxEvaluationsOverride", "100");
                        System.setProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY, "100000");
                        System.clearProperty(WFGStudy.HV_METHODS_PROPERTY);
                        System.clearProperty(WFGStudy.HV_FALLBACK_ON_ALL_EXACT_TIMEOUT_PROPERTY);
                        System.setProperty("perf.hvTimeoutMs", "15000");
                        System.clearProperty("hype.hvWorkerSimulateHangMs");

                        WFGStudy.main(new String[0]);

                        Path cellNsgaiii = outDual.resolve("data/NSGAIII/WFG4_2");
                        assertThat(cellNsgaiii.resolve("NHV")).doesNotExist();
                        assertThat(cellNsgaiii.resolve("HV")).exists();
                        assertThat(cellNsgaiii.resolve("HV_MC")).exists();
                        assertThat(cellNsgaiii.resolve("IGD")).exists();
                        assertThat(cellNsgaiii.resolve("IGD+")).exists();
                        assertThat(cellNsgaiii.resolve("R2")).exists();

                        String summaryDual = Files.readString(
                                        outDual.resolve("QualityIndicatorSummary.csv"), StandardCharsets.UTF_8);
                        assertThat(summaryDual)
                                        .doesNotContain(",NHV,")
                                        .contains(",HV,")
                                        .contains(",HV_MC,")
                                        .contains(",IGD,")
                                        .contains(",IGD+,")
                                        .contains(",R2,");

                        double hvExactVal = Double.parseDouble(
                                        Files.readString(cellNsgaiii.resolve("HV"), StandardCharsets.UTF_8).trim());
                        double hvMcVal = Double.parseDouble(
                                        Files.readString(cellNsgaiii.resolve("HV_MC"), StandardCharsets.UTF_8).trim());
                        assertThat(hvExactVal).isGreaterThan(0.0);
                        assertThat(hvMcVal).isCloseTo(hvExactVal, Offset.offset(0.02));

                        // 2. Execução com APENAS hipervolumes pesados (PISA,WFG) e timeout forçado +
                        // fallback ligado:
                        // HV e HV_WFG gravam NA (nunca 0.0!), HV_FALLBACK_MC é gravado 1 vez por
                        // algoritmo e LaTeX/R não quebram!
                        System.setProperty("study.outputDirectory", outHeavyTimeout.toString());
                        System.setProperty(WFGStudy.HV_METHODS_PROPERTY, "PISA,WFG");
                        System.setProperty(WFGStudy.HV_FALLBACK_ON_ALL_EXACT_TIMEOUT_PROPERTY, "true");
                        System.setProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY, "25000");
                        System.setProperty("perf.hvTimeoutMs", "80");
                        System.setProperty("hype.hvWorkerSimulateHangMs", "4000");

                        WFGStudy.main(new String[0]);

                        Path cellTimeout = outHeavyTimeout.resolve("data/NSGAIII/WFG4_2");
                        assertThat(Files.readString(cellTimeout.resolve("HV"), StandardCharsets.UTF_8).trim())
                                        .isEqualTo("NA");
                        assertThat(Files.readString(cellTimeout.resolve("HV_WFG"), StandardCharsets.UTF_8).trim())
                                        .isEqualTo("NA");

                        String summaryTimeout = Files.readString(
                                        outHeavyTimeout.resolve("QualityIndicatorSummary.csv"),
                                        StandardCharsets.UTF_8);
                        assertThat(summaryTimeout)
                                        .contains("NSGAIII,WFG4_2,HV,0,NA")
                                        .contains("NSGAIII,WFG4_2,HV_WFG,0,NA")
                                        .contains("NSGAIII,WFG4_2,HV_FALLBACK_MC,0,");

                        List<String> auditLines = Files.readAllLines(
                                        outHeavyTimeout.resolve("hv_audit_manifest.csv"),
                                        StandardCharsets.UTF_8);
                        assertThat(auditLines).hasSize(7); // cabeçalho + 6 algoritmos
                        for (int i = 1; i < auditLines.size(); i++) {
                                assertThat(auditLines.get(i))
                                                .contains("PISA+WFG,true,TIMEOUT_NO_FALLBACK,NA")
                                                .contains(",FALLBACK_MC_SINGLE_SHOT,true,");
                        }
                        assertThat(outHeavyTimeout.resolve("hv_fallback_warnings.md")).exists();
                        assertThat(outHeavyTimeout.resolve("latex/study_heavy_timeout.tex")).exists();

                        // 3. Sem bloqueio em código para NHV: quando explicitamente solicitado
                        // (-Dstudy.hvMethods=PISA,NHV), calcula e exporta NHV normalmente
                        Path outWithNhv = tempDir.resolve("study_with_nhv");
                        System.setProperty("study.outputDirectory", outWithNhv.toString());
                        System.setProperty("runs", "2");
                        System.setProperty("study.maxEvaluationsOverride", "200");
                        System.setProperty(WFGStudy.HV_METHODS_PROPERTY, "PISA,NHV");
                        System.setProperty("perf.hvTimeoutMs", "15000");
                        System.clearProperty("hype.hvWorkerSimulateHangMs");
                        System.setProperty("study.skipRValidation", "true");

                        WFGStudy.main(new String[0]);

                        Path cellNhv = outWithNhv.resolve("data/NSGAIII/WFG4_2");
                        List<String> nhvLines = Files.readAllLines(cellNhv.resolve("NHV"), StandardCharsets.UTF_8);
                        assertThat(nhvLines).hasSize(2);
                        double nhv0 = Double.parseDouble(nhvLines.get(0).trim());
                        assertThat(nhv0).isFinite().isGreaterThan(0.0);
                        String kwNhv = Files.readString(
                                        outWithNhv.resolve("statistics/kruskal_cells.csv"),
                                        StandardCharsets.UTF_8);
                        assertThat(kwNhv).contains("WFG4_2,2,NHV,MINIMIZE,COMPLETE,");
                } finally {
                        restoreProp("study.skipRValidation", prevSkipR);
                        restoreProp("study.outputDirectory", prevOut);
                        restoreProp("objectives", prevObj);
                        restoreProp("problem", prevProb);
                        restoreProp("runs", prevRuns);
                        restoreProp("study.maxEvaluationsOverride", prevBudget);
                        restoreProp(WFGStudy.HV_METHODS_PROPERTY, prevHvMethods);
                        restoreProp(WFGStudy.HV_FALLBACK_ON_ALL_EXACT_TIMEOUT_PROPERTY, prevFallback);
                        restoreProp(WFGStudy.HV_MC_SAMPLES_PROPERTY, prevMcSamples);
                        restoreProp("perf.hvTimeoutMs", prevHvTimeout);
                        restoreProp("hype.hvWorkerSimulateHangMs", prevHang);
                }
        }

        @Test
        @DisplayName("T37: resolveEffectiveRuntimeClassPath inclui entradas de URLClassLoader de contexto (cenário mvn exec:java)")
        void testResolveEffectiveRuntimeClassPathExtractsUrlClassLoaderEntries(@TempDir Path tempDir)
                        throws Exception {
                Path syntheticJar = tempDir.resolve("synthetic-exec-plugin-dep.jar").toAbsolutePath();
                Files.writeString(syntheticJar, "stub", StandardCharsets.UTF_8);

                ClassLoader prevLoader = Thread.currentThread().getContextClassLoader();
                try (java.net.URLClassLoader customLoader = new java.net.URLClassLoader(
                                new java.net.URL[] { syntheticJar.toUri().toURL() }, prevLoader)) {
                        Thread.currentThread().setContextClassLoader(customLoader);
                        String resolvedCp = ProcessIsolatedHypervolumeExecutor.resolveEffectiveRuntimeClassPath();
                        assertThat(resolvedCp).contains(syntheticJar.toString());
                } finally {
                        Thread.currentThread().setContextClassLoader(prevLoader);
                }
        }

        @Test
        @DisplayName("Validação defensiva de -Dstudy.workerJvmArgs (aspas, caminhos Windows, flags proibidas, heap) e execução isolada de HV")
        void testParseWorkerJvmArgsDefensesAndComputeExactOnlyExecution() {
                String prevArgs = System.getProperty(ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY);
                try {
                        assertThat(ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs(null)).isEmpty();
                        assertThat(ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs("   ")).isEmpty();

                        List<String> valid = ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs(
                                        "-Xms64m -Xmx512m -XX:+UseG1GC '-XX:MaxGCPauseMillis=100' \"-Dlog.dir=C:\\Program Files\\hypeMOEA\\logs\"");
                        assertThat(valid).containsExactly(
                                        "-Xms64m",
                                        "-Xmx512m",
                                        "-XX:+UseG1GC",
                                        "-XX:MaxGCPauseMillis=100",
                                        "-Dlog.dir=C:\\Program Files\\hypeMOEA\\logs");

                        // Aspas abertas e tokens sem '-' inicial são rejeitados
                        assertThatThrownBy(() -> ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs(
                                        "-Xmx512m \"-Dfoo=unclosed"))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Aspas não fechadas");
                        assertThatThrownBy(() -> ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs(
                                        "-Xmx512m MainClass"))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("iniciar com '-'");

                        // Flags controladas pelo supervisor são estritamente bloqueadas
                        for (String forbidden : List.of("-cp foo.jar", "-classpath foo.jar", "--class-path=foo.jar",
                                        "-jar worker.jar", "-m mod", "--module=mod", "-p mods", "--module-path=mods",
                                        "-version", "--help")) {
                                assertThatThrownBy(
                                                () -> ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs(forbidden))
                                                .isInstanceOf(IllegalArgumentException.class)
                                                .hasMessageContaining("Flag proibida");
                        }

                        // Defesas contra sintaxe inválida e heap inviável/incoerente
                        assertThatThrownBy(() -> ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs("-Xmxabc"))
                                        .isInstanceOf(IllegalArgumentException.class);
                        assertThatThrownBy(() -> ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs("-Xmx32m"))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("-Xmx < 64m");
                        assertThatThrownBy(() -> ProcessIsolatedHypervolumeExecutor
                                        .parseWorkerJvmArgs("-Xms512m -Xmx256m"))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("não pode exceder -Xmx");

                        // Fail-fast antes de abrir processo worker em computeExactOnly
                        ProcessIsolatedHypervolumeExecutor executor = new ProcessIsolatedHypervolumeExecutor(
                                        15_000L, 500L, 100_000L);
                        double[][] front = { { 1.0, 2.0 }, { 0.4, 3.2 }, { 1.8, 0.8 } };
                        NormalizationBounds bounds2D = ObjectiveNormalizationContract.resolveBounds(
                                        "WFG4", 2, NormalizationMode.THEORETICAL_PARETO_BOUNDS, null);
                        double[][] normFront = ObjectiveNormalizationContract.normalizeFront(front, bounds2D);

                        System.setProperty(ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY, "-Xmx16m");
                        assertThatThrownBy(() -> executor.computeExactOnly(
                                        normFront, new double[2], bounds2D.normalizedHvReferencePoint(), "PISA"))
                                        .isInstanceOf(IllegalArgumentException.class);

                        // Execução real com JVM args seguros produz o valor exato 0.51 na caixa
                        // normalizada [0, 1.1]^2
                        System.setProperty(
                                        ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY,
                                        "-Xms64m -Xmx512m -XX:+UseG1GC");
                        HypervolumeResult tuned = executor.computeExactOnly(
                                        normFront, new double[2], bounds2D.normalizedHvReferencePoint(), "PISA");
                        assertThat(tuned.getStatus()).isEqualTo(HypervolumeResult.ExecutionStatus.SUCCESS_EXACT);
                        assertThat(tuned.getValue()).isCloseTo(0.51, Offset.offset(1e-12));
                } finally {
                        restoreProp(ProcessIsolatedHypervolumeExecutor.WORKER_JVM_ARGS_PROPERTY, prevArgs);
                }
        }

        @Test
        @DisplayName("Paralelismo seguro de indicadores (R2/IGD/IGD+ isolados por thread, teto exactHv em m>=8 e paridade bit-a-bit 1 vs 4 workers)")
        void testParallelIndicatorWorkersDefensesAndBitExactParityAcrossBatches(@TempDir Path tempDir)
                        throws Exception {
                String prevSkipR = System.getProperty("study.skipRValidation");
                String prevOut = System.getProperty("study.outputDirectory");
                String prevObj = System.getProperty("objectives");
                String prevProb = System.getProperty("problem");
                String prevRuns = System.getProperty("runs");
                String prevBudget = System.getProperty("study.maxEvaluationsOverride");
                String prevParallel = System.getProperty("study.parallelWorkers");
                String prevIndWorkers = System.getProperty(WFGStudy.INDICATOR_WORKERS_PROPERTY);
                String prevExactHvWorkers = System.getProperty(WFGStudy.EXACT_HV_PARALLEL_WORKERS_PROPERTY);
                String prevHvMethods = System.getProperty(WFGStudy.HV_METHODS_PROPERTY);
                String prevHvByM = System.getProperty(WFGStudy.HV_METHODS_BY_M_PROPERTY);
                String prevMcSamples = System.getProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY);
                String prevLpt = System.getProperty("study.lptScheduling");
                String prevCheckpoint = System.getProperty("study.checkpointInterval");

                try {
                        // 1. Valida políticas e tetos defensivos das propriedades
                        System.clearProperty("study.parallelWorkers");
                        System.clearProperty(WFGStudy.INDICATOR_WORKERS_PROPERTY);
                        assertThat(WFGStudy.getIndicatorWorkers()).isEqualTo(1);

                        System.setProperty("study.parallelWorkers", "3");
                        assertThat(WFGStudy.getIndicatorWorkers()).isEqualTo(3);

                        System.setProperty(WFGStudy.INDICATOR_WORKERS_PROPERTY, "4");
                        assertThat(WFGStudy.getIndicatorWorkers()).isEqualTo(4);

                        System.setProperty(WFGStudy.INDICATOR_WORKERS_PROPERTY, "0");
                        assertThatThrownBy(WFGStudy::getIndicatorWorkers)
                                        .isInstanceOf(IllegalArgumentException.class);
                        System.setProperty(WFGStudy.INDICATOR_WORKERS_PROPERTY, "4");

                        System.clearProperty(WFGStudy.EXACT_HV_PARALLEL_WORKERS_PROPERTY);
                        assertThat(WFGStudy.getExactHvParallelWorkers(4, 2)).isEqualTo(4);
                        assertThat(WFGStudy.getExactHvParallelWorkers(4, 5)).isEqualTo(4);
                        assertThat(WFGStudy.getExactHvParallelWorkers(4, 8)).isEqualTo(2);
                        assertThat(WFGStudy.getExactHvParallelWorkers(4, 15)).isEqualTo(2);

                        System.setProperty(WFGStudy.EXACT_HV_PARALLEL_WORKERS_PROPERTY, "1");
                        assertThat(WFGStudy.getExactHvParallelWorkers(4, 2)).isEqualTo(1);
                        System.setProperty(WFGStudy.EXACT_HV_PARALLEL_WORKERS_PROPERTY, "0");
                        assertThatThrownBy(() -> WFGStudy.getExactHvParallelWorkers(4, 2))
                                        .isInstanceOf(IllegalArgumentException.class);
                        System.clearProperty(WFGStudy.EXACT_HV_PARALLEL_WORKERS_PROPERTY);

                        // 2. Paridade end-to-end (indicatorWorkers=1 sequencial vs indicatorWorkers=4 +
                        // parallelWorkers=2 + LPT + checkpoint=2) em WFG4 (m=2,5)
                        Path seqOut = tempDir.resolve("parity_seq");
                        Path parOut = tempDir.resolve("parity_par");

                        System.setProperty("study.skipRValidation", "true");
                        System.setProperty("problem", "WFG4");
                        System.setProperty("objectives", "2,5");
                        System.setProperty("runs", "2");
                        System.setProperty("study.maxEvaluationsOverride", "220");
                        System.clearProperty(WFGStudy.HV_METHODS_PROPERTY);
                        System.setProperty(WFGStudy.HV_METHODS_BY_M_PROPERTY, "<=5:PISA,MC;>=8:MC");
                        System.setProperty(WFGStudy.HV_MC_SAMPLES_PROPERTY, "20000");

                        System.setProperty("study.outputDirectory", seqOut.toString());
                        System.setProperty("study.parallelWorkers", "1");
                        System.setProperty(WFGStudy.INDICATOR_WORKERS_PROPERTY, "1");
                        System.setProperty("study.lptScheduling", "false");
                        System.clearProperty("study.checkpointInterval");
                        WFGStudy.main(new String[0]);

                        System.setProperty("study.outputDirectory", parOut.toString());
                        System.setProperty("study.parallelWorkers", "2");
                        System.setProperty(WFGStudy.INDICATOR_WORKERS_PROPERTY, "4");
                        System.setProperty("study.lptScheduling", "true");
                        System.setProperty("study.checkpointInterval", "2");
                        WFGStudy.main(new String[0]);

                        assertThat(Files.readString(parOut.resolve("run_manifest.csv"), StandardCharsets.UTF_8))
                                        .isEqualTo(Files.readString(seqOut.resolve("run_manifest.csv"),
                                                        StandardCharsets.UTF_8));
                        assertThat(Files.readString(parOut.resolve("QualityIndicatorSummary.csv"),
                                        StandardCharsets.UTF_8))
                                        .isEqualTo(Files.readString(
                                                        seqOut.resolve("QualityIndicatorSummary.csv"),
                                                        StandardCharsets.UTF_8));

                        for (String dim : List.of("WFG4_2", "WFG4_5")) {
                                for (String alg : List.of("NSGAIII", "MOEADD", "FDEA", "RVEA", "AGE-MOEA",
                                                "AGE-MOEA-II")) {
                                        for (int r = 0; r < 2; r++) {
                                                Path seqFun = seqOut.resolve("data").resolve(alg).resolve(dim)
                                                                .resolve("FUN" + r + ".csv");
                                                Path parFun = parOut.resolve("data").resolve(alg).resolve(dim)
                                                                .resolve("FUN" + r + ".csv");
                                                assertThat(Files.readString(parFun, StandardCharsets.UTF_8))
                                                                .as("Paridade exata em %s/%s/FUN%d.csv", alg, dim, r)
                                                                .isEqualTo(Files.readString(seqFun,
                                                                                StandardCharsets.UTF_8));
                                        }
                                        for (String ind : List.of("IGD", "IGD+", "R2", "HV", "HV_MC")) {
                                                Path seqFile = seqOut.resolve("data").resolve(alg).resolve(dim)
                                                                .resolve(ind);
                                                Path parFile = parOut.resolve("data").resolve(alg).resolve(dim)
                                                                .resolve(ind);
                                                assertThat(Files.readString(parFile, StandardCharsets.UTF_8))
                                                                .as("Paridade exata em %s/%s/%s", alg, dim, ind)
                                                                .isEqualTo(Files.readString(seqFile,
                                                                                StandardCharsets.UTF_8));
                                        }
                                }
                        }

                        List<String> seqAudit = Files.readAllLines(
                                        seqOut.resolve("hv_audit_manifest.csv"), StandardCharsets.UTF_8);
                        List<String> parAudit = Files.readAllLines(
                                        parOut.resolve("hv_audit_manifest.csv"), StandardCharsets.UTF_8);
                        assertThat(parAudit).hasSameSizeAs(seqAudit);
                        assertThat(parAudit.get(0)).isEqualTo(seqAudit.get(0));
                        String[] auditHeaders = seqAudit.get(0).split(",", -1);
                        for (int i = 1; i < seqAudit.size(); i++) {
                                // Compara todas as colunas determinísticas (exceto durações *_wall_ms)
                                String[] sCols = seqAudit.get(i).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                                String[] pCols = parAudit.get(i).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                                assertThat(pCols.length).isEqualTo(sCols.length).isEqualTo(auditHeaders.length);
                                for (int c = 0; c < sCols.length; c++) {
                                        if (auditHeaders[c].endsWith("_wall_ms")) {
                                                continue;
                                        }
                                        assertThat(pCols[c])
                                                        .as("Coluna %s de hv_audit_manifest.csv linha %d",
                                                                        auditHeaders[c], i)
                                                        .isEqualTo(sCols[c]);
                                }
                        }
                } finally {
                        restoreProp("study.skipRValidation", prevSkipR);
                        restoreProp("study.outputDirectory", prevOut);
                        restoreProp("objectives", prevObj);
                        restoreProp("problem", prevProb);
                        restoreProp("runs", prevRuns);
                        restoreProp("study.maxEvaluationsOverride", prevBudget);
                        restoreProp("study.parallelWorkers", prevParallel);
                        restoreProp(WFGStudy.INDICATOR_WORKERS_PROPERTY, prevIndWorkers);
                        restoreProp(WFGStudy.EXACT_HV_PARALLEL_WORKERS_PROPERTY, prevExactHvWorkers);
                        restoreProp(WFGStudy.HV_METHODS_PROPERTY, prevHvMethods);
                        restoreProp(WFGStudy.HV_METHODS_BY_M_PROPERTY, prevHvByM);
                        restoreProp(WFGStudy.HV_MC_SAMPLES_PROPERTY, prevMcSamples);
                        restoreProp("study.lptScheduling", prevLpt);
                        restoreProp("study.checkpointInterval", prevCheckpoint);
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

package br.usp.lti.newmoea.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;

/**
 * Suíte de testes automatizados substitutos para validação canônica de pesos,
 * frentes e geração do manifesto consolidado (Tarefa T30/T31).
 *
 * <p>
 * <strong>Nota:</strong> As menções às pastas legadas protegidas
 * ({@code MOEAD_Weights},
 * {@code pareto_fronts}, {@code pareto}) referem-se exclusivamente a testes
 * desta tarefa específica
 * para verificação de exclusão e proteção contra escrita, não devendo ser
 * usadas como referência para
 * execuções finais.
 */
class CanonicalArtifactResolutionAndManifestTest {

        @Test
        @DisplayName("Validação exata das fronteiras da regra em memória do jMetal 7.8 (m=2, 2<=N<=300)")
        void testIsMoeadWeightGeneratedInMemory() {
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(2, 0)).isFalse();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(2, 1)).isFalse();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(2, 2)).isTrue();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(2, 100)).isTrue();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(2, 300)).isTrue();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(2, 301)).isFalse();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(2, 400)).isFalse();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(3, 91)).isFalse();
                assertThat(ReferenceSetValidator.isMoeadWeightGeneratedInMemory(15, 135)).isFalse();
        }

        @Test
        @DisplayName("Resolução canônica de pesos com retornos estruturados para todas as dimensões ativas")
        void testCanonicalWeightResolutionAllDimensions() {
                int[] dims = new int[] { 2, 3, 5, 8, 10, 15 };
                int[] pops = new int[] { 100, 91, 210, 156, 275, 135 };

                for (int i = 0; i < dims.length; i++) {
                        int m = dims[i];
                        int pop = pops[i];
                        ResolvedWeightArtifact rwa = ReferenceSetValidator.resolveCanonicalWeightFile(m, pop, null);
                        assertThat(rwa).isNotNull();
                        assertThat(rwa.numberOfObjectives()).isEqualTo(m);
                        assertThat(rwa.populationSize()).isEqualTo(pop);
                        assertThat(rwa.path()).exists();
                        assertThat(rwa.sha256()).isNotBlank();
                }
        }

        @Test
        @DisplayName("Exportação de manifesto canônico consolidado sem acesso a pastas legadas")
        void testExportActiveArtifactManifest(@TempDir Path tempDir) throws IOException {
                Path manifestPath = tempDir.resolve("canonical-manifest.csv");
                List<ArtifactManifestEntry> entries = ReferenceSetValidator
                                .exportActiveArtifactManifestCsv(manifestPath, null);

                assertThat(entries).isNotEmpty();
                assertThat(manifestPath).exists();

                List<String> lines = Files.readAllLines(manifestPath);
                assertThat(lines).hasSize(entries.size() + 1);
                assertThat(lines.get(0)).isEqualTo(ArtifactManifestEntry.CSV_HEADER);

                for (ArtifactManifestEntry entry : entries) {
                        assertThat(entry.canonicalPath()).doesNotContain("MOEAD_Weights");
                        assertThat(entry.canonicalPath()).doesNotContain("pareto_fronts/csv");
                        assertThat(entry.canonicalPath()).doesNotContain("/pareto/");
                        assertThat(entry.sha256()).matches("^[0-9a-f]{64}$");
                }

                // Bloqueio de gravação em pastas legadas protegidas (MOEAD_Weights/,
                // pareto_fronts/, pareto/)
                assertThatThrownBy(() -> ReferenceSetValidator.exportActiveArtifactManifestCsv(
                                Path.of("MOEAD_Weights", "manifest.csv"), null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Proibido gravar manifesto dentro de diretórios protegidos");
                assertThatThrownBy(() -> ReferenceSetValidator.exportActiveArtifactManifestCsv(
                                Path.of("pareto_fronts", "manifest.csv"), null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Proibido gravar manifesto dentro de diretórios protegidos");
                assertThatThrownBy(() -> ReferenceSetValidator.exportActiveArtifactManifestCsv(
                                Path.of("pareto", "manifest.csv"), null))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Proibido gravar manifesto dentro de diretórios protegidos");
        }

        @Test
        @DisplayName("Parametrização granular de -DreplaceZeroWeights e -DreplaceZeroFronts por algoritmo e estado padrão false")
        void testGranularZeroReplacementFlags() {
                String prevWeights = System.getProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);
                String prevFronts = System.getProperty(ParetoFrontValidator.REPLACE_ZERO_FRONTS_PROPERTY);
                try {
                        // 1. Padrão: ambas desativadas
                        System.clearProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);
                        System.clearProperty(ParetoFrontValidator.REPLACE_ZERO_FRONTS_PROPERTY);
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled()).isFalse();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("MOEADD")).isFalse();
                        assertThat(ParetoFrontValidator.isReplaceZeroFrontsEnabled()).isFalse();
                        assertThat(ParetoFrontValidator.isReplaceZeroFrontsEnabled("FDEA")).isFalse();

                        // 2. Lista granular por algoritmo
                        System.setProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, "MOEADD, FDEA");
                        System.setProperty(ParetoFrontValidator.REPLACE_ZERO_FRONTS_PROPERTY, "GWASFGA, AGE-MOEA-II");

                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled()).isFalse();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("MOEADD")).isTrue();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("MOEA/DD")).isTrue();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("FDEA")).isTrue();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("RVEA")).isFalse();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("GWASFGA")).isFalse();

                        assertThat(ParetoFrontValidator.isReplaceZeroFrontsEnabled()).isFalse();
                        assertThat(ParetoFrontValidator.isReplaceZeroFrontsEnabled("GWASFGA")).isTrue();
                        assertThat(ParetoFrontValidator.isReplaceZeroFrontsEnabled("AGE2")).isTrue();
                        assertThat(ParetoFrontValidator.isReplaceZeroFrontsEnabled("AGE-MOEA-II")).isTrue();
                        assertThat(ParetoFrontValidator.isReplaceZeroFrontsEnabled("MOEADD")).isFalse();

                        // 3. Ativação global true e desativação explícita false
                        System.setProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, "true");
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled()).isTrue();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("ANY")).isTrue();

                        System.setProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, "false");
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled()).isFalse();
                        assertThat(ReferenceSetValidator.isReplaceZeroWeightsEnabled("MOEADD")).isFalse();
                } finally {
                        restoreProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, prevWeights);
                        restoreProperty(ParetoFrontValidator.REPLACE_ZERO_FRONTS_PROPERTY, prevFronts);
                }
        }

        @Test
        @DisplayName("Pureza analítica de weights_gen/ (preserva 0.0) e isolamento em weights_lowzero_gen/ apenas quando flag ativa")
        void testWeightsGenAnalyticalPurityAndWeightsLowZeroGenIsolation(@TempDir Path tempDir) throws IOException {
                String prevWeights = System.getProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);
                try {
                        System.clearProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);

                        // 1. Sem flag ativa: gera em weights_gen/ de forma puramente analítica
                        // (preservando 0.0)
                        for (int m : new int[] { 2, 5, 15 }) {
                                int pop = StaticParams.getPopulationSize(m);
                                ResolvedWeightArtifact pureArtifact = ReferenceSetValidator
                                                .resolveCanonicalWeightFile(m, pop, tempDir, "MOEADD");
                                assertThat(pureArtifact.path().toString().replace('\\', '/'))
                                                .contains("/weights_gen/W" + m + "D_" + pop + ".dat")
                                                .doesNotContain("weights_lowzero_gen");

                                double[][] pureWeights = ReferenceSetValidator.validateMoeadWeightFile(
                                                pureArtifact.path(), m, pop, 1e-6, 1e-7);
                                ReferenceSetValidator.SetValidationReport pureReport = ReferenceSetValidator
                                                .validateSimplexSet(pureWeights, pop, m, 1e-6, 1e-7);
                                assertThat(pureReport.allStrictlyPositive())
                                                .as("weights_gen/ deve preservar 0.0 exato para m=%d", m)
                                                .isFalse();
                                assertThat(pureReport.minCoordinate()).isEqualTo(0.0);
                                assertThat(pureReport.maxSumDeviationFromOne()).isLessThanOrEqualTo(1e-12);
                        }
                        assertThat(tempDir.resolve("resources/weightVectorFiles/weights_lowzero_gen")).doesNotExist();

                        // 2. Com -DreplaceZeroWeights=GWASFGA: somente GWASFGA usa
                        // weights_lowzero_gen/; MOEADD continua em weights_gen/
                        System.setProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, "GWASFGA");

                        ResolvedWeightArtifact moeadArtifact = ReferenceSetValidator.resolveCanonicalWeightFile(5, 210,
                                        tempDir, "MOEADD");
                        assertThat(moeadArtifact.path().toString().replace('\\', '/'))
                                        .contains("/weights_gen/W5D_210.dat");

                        ResolvedWeightArtifact gwasfgaArtifact = ReferenceSetValidator.resolveCanonicalWeightFile(5,
                                        210, tempDir, "GWASFGA");
                        assertThat(gwasfgaArtifact.sourceCategory()).isEqualTo("WEIGHTS_LOWZERO_GEN");
                        assertThat(gwasfgaArtifact.path().toString().replace('\\', '/'))
                                        .contains("/weights_lowzero_gen/W5D_210.dat");

                        double[][] lowZeroWeights = ReferenceSetValidator.validateMoeadWeightFile(
                                        gwasfgaArtifact.path(), 5, 210, 2e-4, 1e-7);
                        ReferenceSetValidator.SetValidationReport lowZeroReport = ReferenceSetValidator
                                        .validateSimplexSet(lowZeroWeights, 210, 5, 2e-4, 1e-7);
                        assertThat(lowZeroReport.allStrictlyPositive())
                                        .as("weights_lowzero_gen/ não pode conter coordenadas 0.0")
                                        .isTrue();
                        assertThat(lowZeroReport.minCoordinate()).isGreaterThanOrEqualTo(1.0e-5 - 1e-12);

                        // Confirma que weights_gen/ permaneceu intacto com 0.0
                        double[][] stillPure = ReferenceSetValidator.validateMoeadWeightFile(
                                        moeadArtifact.path(), 5, 210, 1e-6, 1e-7);
                        assertThat(ReferenceSetValidator.validateSimplexSet(stillPure, 210, 5, 1e-6, 1e-7)
                                        .minCoordinate())
                                        .isEqualTo(0.0);
                } finally {
                        restoreProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, prevWeights);
                }
        }

        @Test
        @DisplayName("Escopo granular em WFGStudy, PerformanceProfiler e bloqueio de lowzero dentro de pf_gen/")
        void testGranularFrontFlagInStudyAndProfilerAndPfGenProtection(@TempDir Path tempDir) {
                String prevFronts = System.getProperty(ParetoFrontValidator.REPLACE_ZERO_FRONTS_PROPERTY);
                String prevWeights = System.getProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY);
                try {
                        // Proíbe gravar frente lowzero dentro de pf_gen/
                        Path tempPfGen = tempDir.resolve("pf_gen");
                        assertThatThrownBy(() -> ParetoFrontValidator.generateAndSaveLowZeroDtlzFront(
                                        "DTLZ1", 3, 12, 0, 1.0e-5, tempPfGen, true))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("pf_gen/");

                        // -DreplaceZeroFronts=FDEA ativa pf_lowzero_gen apenas para FDEA (em
                        // PerformanceProfiler / consulta escopada)
                        System.setProperty(ParetoFrontValidator.REPLACE_ZERO_FRONTS_PROPERTY, "FDEA");
                        assertThat(WFGStudy.resolveReferenceFrontForStudy("WFG1", 2, "MOEADD"))
                                        .isEqualTo("WFG1.2D.csv");
                        assertThat(PerformanceProfiler.resolveReferenceFrontPath("WFG1", 2, "MOEADD").toString()
                                        .replace('\\', '/'))
                                        .doesNotContain("pf_lowzero_gen");
                        assertThat(ParetoFrontValidator
                                        .resolveCanonicalFrontArtifact("DTLZ1", 3, tempDir, "MOEADD", true)
                                        .sourceCategory()).isEqualTo("PF_GEN");
                        assertThat(ParetoFrontValidator.resolveCanonicalFrontArtifact("DTLZ1", 3, tempDir, "FDEA", true)
                                        .sourceCategory()).isEqualTo("LOWZERO_GEN");
                        // Garante que consulta sem algoritmo não ativa globalmente quando lista é
                        // restrita a FDEA
                        assertThat(WFGStudy.resolveReferenceFrontForStudy("WFG1", 2)).isEqualTo("WFG1.2D.csv");

                        // Verifica resolução granular de diretórios de pesos em WFGStudy
                        System.setProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, "MOEADD");
                        assertThat(WFGStudy.resolveMoeadDataDirectory(WFGStudy.DEFAULT_WEIGHTS_DIRECTORY, 5, 210,
                                        "FDEA"))
                                        .isEqualTo("resources/weightVectorFiles/weights_gen");
                } finally {
                        restoreProperty(ParetoFrontValidator.REPLACE_ZERO_FRONTS_PROPERTY, prevFronts);
                        restoreProperty(ReferenceSetValidator.REPLACE_ZERO_WEIGHTS_PROPERTY, prevWeights);
                }
        }

        @Test
        @DisplayName("T34: Política opcional -Dstudy.allowMoeadWeightsInMemory (desativada vs ativada, sem path fictício e sem dispensar GWASFGA/m>=3)")
        void testAllowMoeadWeightsInMemoryPolicy(@TempDir Path tempDir) {
                String prevInMem = System.getProperty(WFGStudy.ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY);
                String prevWeightsDir = System.getProperty("study.weightsDirectory");
                try {
                        Path emptyWeightsDir = tempDir.resolve("custom_empty_weights");
                        System.setProperty("study.weightsDirectory", emptyWeightsDir.toString());

                        // 1. Default (false): N=13 sem arquivo físico deve falhar
                        System.clearProperty(WFGStudy.ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY);
                        assertThat(WFGStudy.isAllowMoeadWeightsInMemoryEnabled()).isFalse();
                        WFG1 wfg1_2 = new WFG1(2, 20, 2);
                        assertThatThrownBy(() -> WFGStudy.validateMoeadWeightFile(
                                        emptyWeightsDir.toString(), wfg1_2, 13, "MOEADD"))
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("inexistente ou ilegível");

                        // 2. Ativada (true): m=2, N=13 e N=100 dispensam arquivo para MOEA/DD sem path
                        // fictício
                        System.setProperty(WFGStudy.ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY, "true");
                        assertThat(WFGStudy.isAllowMoeadWeightsInMemoryEnabled()).isTrue();

                        ResolvedWeightArtifact dispensed = ReferenceSetValidator
                                        .resolveCanonicalWeightArtifactFromWeightsRoot(
                                                        2, 13, emptyWeightsDir, "MOEADD", true);
                        assertThat(dispensed.inMemory()).isTrue();
                        assertThat(dispensed.path()).isNull();
                        assertThat(dispensed.sha256()).isEqualTo("NONE_IN_MEMORY");
                        assertThat(dispensed.sourceCategory()).isEqualTo("IN_MEMORY");
                        assertThat(emptyWeightsDir).doesNotExist();

                        double[][] inMem13 = WFGStudy.validateMoeadWeightFile(
                                        emptyWeightsDir.toString(), wfg1_2, 13, "MOEADD");
                        assertThat(inMem13).hasDimensions(13, 2);
                        assertThat(inMem13[0]).containsExactly(0.0, 1.0);
                        assertThat(inMem13[12]).containsExactly(1.0, 0.0);

                        // GWASFGA lê disco incondicionalmente -> nunca pode ser dispensado em memória!
                        ResolvedWeightArtifact gwasfga100 = ReferenceSetValidator
                                        .resolveCanonicalWeightArtifactFromWeightsRoot(
                                                        2, 100, emptyWeightsDir, "GWASFGA", true);
                        assertThat(gwasfga100.inMemory()).isFalse();
                        assertThat(gwasfga100.path()).exists();

                        // m=3 também exige arquivo físico
                        ResolvedWeightArtifact m3Artifact = ReferenceSetValidator
                                        .resolveCanonicalWeightArtifactFromWeightsRoot(
                                                        3, 91, emptyWeightsDir, "MOEADD", true);
                        assertThat(m3Artifact.inMemory()).isFalse();
                        assertThat(m3Artifact.path()).exists();
                } finally {
                        restoreProperty(WFGStudy.ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY, prevInMem);
                        restoreProperty("study.weightsDirectory", prevWeightsDir);
                }
        }

        @Test
        @DisplayName("T34: Prioridade por validade matemática (não apenas existência), bloqueio sem gerador e proteção de oficial corrompido")
        void testPriorityByValidityAndBlockWhenGenerationDisabled(@TempDir Path tempDir) throws IOException {
                Path customRefRoot = tempDir.resolve("custom_ref_fronts");
                Path customPfGen = customRefRoot.resolve("pf_gen");
                Path customPfLowZero = customRefRoot.resolve("pf_lowzero_gen");
                Files.createDirectories(customRefRoot);

                // Cria arquivo oficial corrompido (NaN / dimensão errada) na Prioridade 1
                Path corruptedOfficial = customRefRoot.resolve("WFG1.2D.csv");
                String corruptedContent = "0.1,NaN\n0.5,0.5\n";
                Files.writeString(corruptedOfficial, corruptedContent, StandardCharsets.UTF_8);

                // 1. Com allowOnDemandGeneration=false e sem pf_gen válido -> bloqueia
                // explicitamente!
                assertThatThrownBy(() -> ParetoFrontValidator.resolveCanonicalReferenceFront(
                                "WFG1", 2, customRefRoot, customPfGen, customPfLowZero, false, false))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Frente de Pareto válida não encontrada")
                                .hasMessageContaining("rejeitado por conteúdo inválido");

                // Confirma que o arquivo da Prioridade 1 não foi sobrescrito silenciosamente
                assertThat(Files.readString(corruptedOfficial, StandardCharsets.UTF_8))
                                .isEqualTo(corruptedContent);

                // 2. Com allowOnDemandGeneration=true -> rejeita Prioridade 1 inválida,
                // preserva-a intacta e gera analiticamente em pf_gen (Prioridade 3)!
                Path resolved = ParetoFrontValidator.resolveCanonicalReferenceFront(
                                "WFG1", 2, customRefRoot, customPfGen, customPfLowZero, false, true);
                assertThat(resolved).isEqualTo(customPfGen.resolve("WFG1.2D.csv"));
                assertThat(resolved).exists();
                assertThat(Files.readString(corruptedOfficial, StandardCharsets.UTF_8))
                                .isEqualTo(corruptedContent);

                double[][] validPts = ParetoFrontValidator.validateParetoFrontFile(resolved, 2).points();
                assertThat(validPts.length).isGreaterThanOrEqualTo(50);

                // Problema desconhecido sem fonte e sem gerador -> bloqueia com diagnóstico
                // explícito
                assertThatThrownBy(() -> ParetoFrontValidator.resolveCanonicalReferenceFront(
                                "ZDT99", 2, customRefRoot, customPfGen, customPfLowZero, false, true))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("sem gerador analítico");
        }

        @Test
        @DisplayName("T34: Geradores analíticos de WFG1–WFG9 respeitam as geometrias distintas de Huband et al. (2006) sem generalizar esfera")
        void testAnalyticalWfg1ToWfg9DistinctGeometries(@TempDir Path tempDir) throws IOException {
                Path wfgGenDir = tempDir.resolve("pf_gen_wfg");

                // 1. WFG1 (convex + mixed), WFG2 (convex + disconnected não-dominada), WFG3
                // (linear degenerada 1D) e WFG4–WFG9 (esfera côncava S_j=2j) em m=3
                for (int k = 1; k <= 9; k++) {
                        String problem = "WFG" + k;
                        Path saved = ParetoFrontValidator.generateAndSaveAnalyticalWfgFront(
                                        problem, 3, 12, 0, wfgGenDir, true).outputFile();
                        assertThat(saved).exists();
                        double[][] front = ParetoFrontValidator.validateParetoFrontFile(saved, 3).points();
                        assertThat(front.length).isGreaterThan(10);

                        for (double[] pt : front) {
                                for (int j = 0; j < 3; j++) {
                                        double maxBound = 2.0 * (j + 1);
                                        assertThat(pt[j])
                                                        .as("%s f_%d dentro de [0, %f]", problem, j + 1, maxBound)
                                                        .isBetween(-1e-12, maxBound + 1e-9);
                                }
                        }
                }

                // 2. Verifica equações geométricas distintas:
                double[][] wfg1 = ParetoFrontValidator.validateParetoFrontFile(
                                wfgGenDir.resolve("WFG1.3D.csv"), 3).points();
                double[][] wfg2 = ParetoFrontValidator.validateParetoFrontFile(
                                wfgGenDir.resolve("WFG2.3D.csv"), 3).points();
                double[][] wfg3 = ParetoFrontValidator.validateParetoFrontFile(
                                wfgGenDir.resolve("WFG3.3D.csv"), 3).points();
                double[][] wfg4 = ParetoFrontValidator.validateParetoFrontFile(
                                wfgGenDir.resolve("WFG4.3D.csv"), 3).points();

                // WFG4..WFG9 satisfazem identicamente sum((f_j / (2j))^2) == 1 (hiperesfera
                // côncava)
                for (double[] pt : wfg4) {
                        double normSq = Math.pow(pt[0] / 2.0, 2)
                                        + Math.pow(pt[1] / 4.0, 2)
                                        + Math.pow(pt[2] / 6.0, 2);
                        assertThat(normSq).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
                }

                // WFG3 é linha 1D degenerada: para m=3 (x_2 = 0.5), f_1/2 + f_2/4 == 0.5 e
                // f_3/6 == 1 - 2*(f_2/4)
                for (double[] pt : wfg3) {
                        double u2 = pt[1] / 4.0; // 0.5 * x_1
                        double x1 = 2.0 * u2;
                        assertThat(pt[0]).isCloseTo(2.0 * x1 * 0.5, org.assertj.core.data.Offset.offset(1e-9));
                        assertThat(pt[2]).isCloseTo(6.0 * (1.0 - x1), org.assertj.core.data.Offset.offset(1e-9));
                }

                // WFG1 (convexa) não fica sobre a hiperesfera côncava no interior!
                boolean foundStrictInteriorConvexPoint = false;
                for (double[] pt : wfg1) {
                        if (pt[0] > 0.05 && pt[1] > 0.05 && pt[2] > 0.05) {
                                double sphereNormSq = Math.pow(pt[0] / 2.0, 2)
                                                + Math.pow(pt[1] / 4.0, 2)
                                                + Math.pow(pt[2] / 6.0, 2);
                                if (Math.abs(sphereNormSq - 1.0) > 0.05) {
                                        foundStrictInteriorConvexPoint = true;
                                        break;
                                }
                        }
                }
                assertThat(foundStrictInteriorConvexPoint)
                                .as("WFG1 não pode ser generalizada como hiperesfera côncava")
                                .isTrue();
                // WFG2 desconexa em 2D exibe saltos estritos em f_3/f_2 após filtrar vales
                // dominados!
                Path wfg2In2D = ParetoFrontValidator.generateAndSaveAnalyticalWfgFront(
                                "WFG2", 2, 99, 0, wfgGenDir, true).outputFile();
                double[][] wfg2Pts2D = ParetoFrontValidator.validateParetoFrontFile(wfg2In2D, 2).points();
                assertThat(wfg2Pts2D.length).isGreaterThan(20);
                double maxGapF1 = 0.0;
                for (int i = 1; i < wfg2Pts2D.length; i++) {
                        maxGapF1 = Math.max(maxGapF1, Math.abs(wfg2Pts2D[i][0] - wfg2Pts2D[i - 1][0]));
                }
                assertThat(maxGapF1)
                                .as("WFG2 deve conter lacunas desconexas reais entre lóbulos não-dominados")
                                .isGreaterThan(0.15);
        }

        @Test
        @DisplayName("T34: Publicação concorrente segura sob demanda e ciclo completo PREFLIGHT_PLANNED -> OBSERVED_CONSUMED em WFGStudy")
        void testConcurrentGenerationAndEndToEndStudyArtifactManifest(@TempDir Path tempDir) throws Exception {
                Path concurrentBase = tempDir.resolve("concurrent_ws");
                ExecutorService pool = Executors.newFixedThreadPool(4);
                try {
                        List<Callable<ResolvedFrontArtifact>> frontTasks = new ArrayList<>();
                        List<Callable<ResolvedWeightArtifact>> weightTasks = new ArrayList<>();
                        for (int t = 0; t < 6; t++) {
                                frontTasks.add(() -> ParetoFrontValidator.resolveCanonicalFrontArtifact(
                                                "WFG4", 5, concurrentBase, "MOEADD", true));
                                weightTasks.add(() -> ReferenceSetValidator.resolveCanonicalWeightFile(
                                                5, 210, concurrentBase, "MOEADD"));
                        }
                        List<Future<ResolvedFrontArtifact>> frontFutures = pool.invokeAll(frontTasks);
                        List<Future<ResolvedWeightArtifact>> weightFutures = pool.invokeAll(weightTasks);
                        String expectedFrontSha = frontFutures.get(0).get().sha256();
                        String expectedWeightSha = weightFutures.get(0).get().sha256();
                        for (Future<ResolvedFrontArtifact> f : frontFutures) {
                                assertThat(f.get().sha256()).isEqualTo(expectedFrontSha);
                        }
                        for (Future<ResolvedWeightArtifact> w : weightFutures) {
                                assertThat(w.get().sha256()).isEqualTo(expectedWeightSha);
                        }
                } finally {
                        pool.shutdownNow();
                }

                // Verifica propagação ponta a ponta de overrides + manifesto efetivo em
                // WFGStudy.main()
                Path customWeights = tempDir.resolve("override_weights");
                Path customFronts = tempDir.resolve("override_fronts");
                Path customStudyOut = tempDir.resolve("study_out");
                Path historicalAuditManifest = Path.of("docs", "auditoria", "artifact-manifest.csv");
                byte[] historicalBytesBefore = Files.exists(historicalAuditManifest)
                                ? Files.readAllBytes(historicalAuditManifest)
                                : null;

                String prevOut = System.getProperty("study.outputDirectory");
                String prevWeights = System.getProperty("study.weightsDirectory");
                String prevFronts = System.getProperty("study.referenceFrontDirectory");
                String prevObjectives = System.getProperty("objectives");
                String prevProblem = System.getProperty("problem");
                String prevRuns = System.getProperty("runs");
                String prevBudget = System.getProperty("study.maxEvaluationsOverride");
                String prevUserDir = System.getProperty("user.dir");
                try {
                        System.setProperty("study.outputDirectory", customStudyOut.toString());
                        System.setProperty("study.weightsDirectory", customWeights.toString());
                        System.setProperty("study.referenceFrontDirectory", customFronts.toString());
                        System.setProperty("objectives", "2");
                        System.setProperty("problem", "WFG1_2");
                        System.setProperty("runs", "1");
                        System.setProperty("study.maxEvaluationsOverride", "100");

                        WFGStudy.main(new String[0]);

                        assertThat(System.getProperty("user.dir")).isEqualTo(prevUserDir);
                        assertThat(customWeights.resolve("weights_gen/W2D_100.dat")).exists();
                        assertThat(customFronts.resolve("pf_gen/WFG1.2D.csv")).exists();
                        // Confirma que WFG2..WFG9 não foram geradas desnecessariamente quando
                        // -Dproblem=WFG1_2
                        assertThat(customFronts.resolve("pf_gen/WFG2.2D.csv")).doesNotExist();

                        Path effectiveManifest = customStudyOut.resolve("artifact-manifest.csv");
                        assertThat(effectiveManifest).exists();
                        List<String> manifestLines = Files.readAllLines(effectiveManifest, StandardCharsets.UTF_8);
                        assertThat(manifestLines).hasSize(3); // cabeçalho + 1 peso + 1 frente
                        assertThat(String.join("\n", manifestLines))
                                        .contains("OBSERVED_CONSUMED:FILE_VALIDATED_PREFLIGHT__JMETAL_MOEAD_IN_MEMORY")
                                        .contains("OBSERVED_CONSUMED:DISK_FILE_READ");

                        // Também valida PREFLIGHT_PLANNED e IN_MEMORY_DISPENSED_NO_FILE com
                        // allowMoeadWeightsInMemory=true
                        Path customStudyOutInMem = tempDir.resolve("study_out_in_mem");
                        Path emptyWeightsInMem = tempDir.resolve("empty_weights_in_mem");
                        String prevInMem = System.getProperty(WFGStudy.ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY);
                        try {
                                System.setProperty(WFGStudy.ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY, "true");
                                System.setProperty("study.weightsDirectory", emptyWeightsInMem.toString());
                                System.setProperty("study.outputDirectory", customStudyOutInMem.toString());

                                // Teste de falha controlada de orçamento mínimo (< N_alloc) -> grava
                                // OBSERVED_EXECUTION_FAILED
                                System.setProperty("study.maxEvaluationsOverride", "10");
                                assertThatThrownBy(() -> WFGStudy.main(new String[0]))
                                                .isInstanceOf(IllegalArgumentException.class);
                                String failedManifest = Files.readString(
                                                customStudyOutInMem.resolve("artifact-manifest.csv"),
                                                StandardCharsets.UTF_8);
                                assertThat(failedManifest)
                                                .contains("OBSERVED_EXECUTION_FAILED:IN_MEMORY_DISPENSED_NO_FILE")
                                                .contains("NONE_IN_MEMORY");

                                // Agora com orçamento válido -> avança para
                                // OBSERVED_CONSUMED:IN_MEMORY_DISPENSED_NO_FILE sem criar arquivo .dat!
                                System.setProperty("study.maxEvaluationsOverride", "100");
                                WFGStudy.main(new String[0]);
                                assertThat(emptyWeightsInMem).doesNotExist();
                                String okInMemManifest = Files.readString(
                                                customStudyOutInMem.resolve("artifact-manifest.csv"),
                                                StandardCharsets.UTF_8);
                                assertThat(okInMemManifest)
                                                .contains("OBSERVED_CONSUMED:IN_MEMORY_DISPENSED_NO_FILE")
                                                .contains("NONE_IN_MEMORY");
                        } finally {
                                restoreProperty(WFGStudy.ALLOW_MOEAD_WEIGHTS_IN_MEMORY_PROPERTY, prevInMem);
                        }

                        // Manifesto histórico da Fase 3 preservado intacto!
                        if (historicalBytesBefore != null) {
                                assertThat(Files.readAllBytes(historicalAuditManifest))
                                                .isEqualTo(historicalBytesBefore);
                        }
                } finally {
                        restoreProperty("study.outputDirectory", prevOut);
                        restoreProperty("study.weightsDirectory", prevWeights);
                        restoreProperty("study.referenceFrontDirectory", prevFronts);
                        restoreProperty("objectives", prevObjectives);
                        restoreProperty("problem", prevProblem);
                        restoreProperty("runs", prevRuns);
                        restoreProperty("study.maxEvaluationsOverride", prevBudget);
                }
        }

        @Test
        @DisplayName("T34: Fallback opcional -Dstudy.restoreOfficialJMetalResources (false por padrão; restaura pesos e frentes oficiais quando true)")
        void testOptionalRestoreOfficialJMetalResourcesFallback(@TempDir Path tempDir) throws Exception {
                String prevRestore = System.getProperty(
                                ReferenceSetValidator.RESTORE_OFFICIAL_JMETAL_RESOURCES_PROPERTY);
                String prevBaseUrl = System.getProperty(
                                ReferenceSetValidator.OFFICIAL_JMETAL_RAW_BASE_URL_PROPERTY);

                byte[] officialWfg1Bytes = Files.readAllBytes(
                                Path.of("resources", "referenceFronts", "WFG1.2D.csv"));
                byte[] officialW3d91Bytes = Files.readAllBytes(
                                Path.of("resources", "weightVectorFiles", "W3D_91.dat"));

                com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer
                                .create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/referenceFrontsCSV/WFG1.2D.csv", exchange -> {
                        exchange.sendResponseHeaders(200, officialWfg1Bytes.length);
                        try (var os = exchange.getResponseBody()) {
                                os.write(officialWfg1Bytes);
                        }
                });
                server.createContext("/weightVectorFiles/moead/W3D_91.dat", exchange -> {
                        exchange.sendResponseHeaders(200, officialW3d91Bytes.length);
                        try (var os = exchange.getResponseBody()) {
                                os.write(officialW3d91Bytes);
                        }
                });
                server.start();
                String localServerBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

                try {
                        // 1. Default: desativado (false) -> não restaura na Prioridade 1; usa geração
                        // analítica na Prioridade 3
                        System.clearProperty(ReferenceSetValidator.RESTORE_OFFICIAL_JMETAL_RESOURCES_PROPERTY);
                        System.setProperty(
                                        ReferenceSetValidator.OFFICIAL_JMETAL_RAW_BASE_URL_PROPERTY,
                                        localServerBaseUrl);
                        assertThat(ReferenceSetValidator.isRestoreOfficialJMetalResourcesEnabled()).isFalse();

                        Path defaultFrontRoot = tempDir.resolve("default_off_fronts");
                        ResolvedFrontArtifact genFront = ParetoFrontValidator
                                        .resolveCanonicalFrontArtifactFromFrontRoot(
                                                        "WFG1", 2, defaultFrontRoot, false, true);
                        assertThat(genFront.sourceCategory()).isEqualTo("PF_GEN");
                        assertThat(defaultFrontRoot.resolve("WFG1.2D.csv")).doesNotExist();
                        assertThat(defaultFrontRoot.resolve("pf_gen/WFG1.2D.csv")).exists();

                        // 2. Ativado (true): restaura frente oficial WFG1.2D.csv diretamente na
                        // Prioridade 1 (OFFICIAL)
                        System.setProperty(
                                        ReferenceSetValidator.RESTORE_OFFICIAL_JMETAL_RESOURCES_PROPERTY,
                                        "true");
                        assertThat(ReferenceSetValidator.isRestoreOfficialJMetalResourcesEnabled()).isTrue();

                        Path restoreFrontRoot = tempDir.resolve("restore_on_fronts");
                        ResolvedFrontArtifact restoredFront = ParetoFrontValidator
                                        .resolveCanonicalFrontArtifactFromFrontRoot(
                                                        "WFG1", 2, restoreFrontRoot, false, true);
                        assertThat(restoredFront.sourceCategory()).isEqualTo("OFFICIAL");
                        assertThat(restoreFrontRoot.resolve("WFG1.2D.csv")).exists();
                        assertThat(Files.readAllBytes(restoreFrontRoot.resolve("WFG1.2D.csv")))
                                        .isEqualTo(officialWfg1Bytes);
                        assertThat(restoreFrontRoot.resolve("pf_gen/WFG1.2D.csv")).doesNotExist();

                        // 3. Ativado (true): restaura arquivo oficial de pesos W3D_91.dat via HTTP (tag
                        // jmetal-7.8) com verificação SHA-256
                        Path restoreWeightRootHttp = tempDir.resolve("restore_on_weights_http");
                        ResolvedWeightArtifact restoredWeightHttp = ReferenceSetValidator
                                        .resolveCanonicalWeightArtifactFromWeightsRoot(
                                                        3, 91, restoreWeightRootHttp, "MOEADD", false);
                        assertThat(restoredWeightHttp.sourceCategory()).isEqualTo("OFFICIAL");
                        assertThat(restoreWeightRootHttp.resolve("W3D_91.dat")).exists();
                        assertThat(Files.readAllBytes(restoreWeightRootHttp.resolve("W3D_91.dat")))
                                        .isEqualTo(officialW3d91Bytes);
                        assertThat(restoreWeightRootHttp.resolve("weights_gen/W3D_91.dat")).doesNotExist();

                        // 4. Ativado (true) e sem servidor HTTP: restaura W3D_91.dat a partir do backup
                        // oficial local moead/W3D_91.dat
                        System.setProperty(
                                        ReferenceSetValidator.OFFICIAL_JMETAL_RAW_BASE_URL_PROPERTY,
                                        "http://127.0.0.1:1");
                        Path restoreWeightRootMirror = tempDir.resolve("restore_on_weights_mirror");
                        Files.createDirectories(restoreWeightRootMirror.resolve("moead"));
                        Files.write(restoreWeightRootMirror.resolve("moead/W3D_91.dat"), officialW3d91Bytes);
                        ResolvedWeightArtifact restoredWeightMirror = ReferenceSetValidator
                                        .resolveCanonicalWeightArtifactFromWeightsRoot(
                                                        3, 91, restoreWeightRootMirror, "MOEADD", false);
                        assertThat(restoredWeightMirror.sourceCategory()).isEqualTo("OFFICIAL");
                        assertThat(restoreWeightRootMirror.resolve("W3D_91.dat")).exists();
                        assertThat(Files.readAllBytes(restoreWeightRootMirror.resolve("W3D_91.dat")))
                                        .isEqualTo(officialW3d91Bytes);

                        // 5. Bloqueio estrito contra uso da cópia externa local modificada do jMetal ou
                        // pastas legadas
                        System.setProperty(
                                        ReferenceSetValidator.OFFICIAL_JMETAL_RAW_BASE_URL_PROPERTY,
                                        "file:///C:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/jMetal/resources");
                        assertThatThrownBy(ReferenceSetValidator::getOfficialJMetalRawBaseUrl)
                                        .isInstanceOf(IllegalArgumentException.class)
                                        .hasMessageContaining("Proibido usar a cópia local modificada do jMetal");
                } finally {
                        server.stop(0);
                        restoreProperty(
                                        ReferenceSetValidator.RESTORE_OFFICIAL_JMETAL_RESOURCES_PROPERTY,
                                        prevRestore);
                        restoreProperty(
                                        ReferenceSetValidator.OFFICIAL_JMETAL_RAW_BASE_URL_PROPERTY,
                                        prevBaseUrl);
                }
        }

        private static void restoreProperty(String key, String previousValue) {
                if (previousValue == null) {
                        System.clearProperty(key);
                } else {
                        System.setProperty(key, previousValue);
                }
        }
}

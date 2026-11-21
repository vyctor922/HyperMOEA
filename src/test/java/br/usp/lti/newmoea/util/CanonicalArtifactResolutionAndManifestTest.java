package br.usp.lti.newmoea.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        @DisplayName("Validação da regra em memória do jMetal 7.8 (m=2, N<=300)")
        void testIsMoeadWeightGeneratedInMemory() {
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

        private static void restoreProperty(String key, String previousValue) {
                if (previousValue == null) {
                        System.clearProperty(key);
                } else {
                        System.setProperty(key, previousValue);
                }
        }
}

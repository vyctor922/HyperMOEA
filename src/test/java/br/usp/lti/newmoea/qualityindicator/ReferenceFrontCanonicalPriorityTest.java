package br.usp.lti.newmoea.qualityindicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import br.usp.lti.newmoea.util.ParetoFrontValidator;
import br.usp.lti.newmoea.util.ResolvedFrontArtifact;
import br.usp.lti.newmoea.util.ParetoFrontValidator;
import br.usp.lti.newmoea.util.ParetoFrontValidator.FrontValidationSummary;
import br.usp.lti.newmoea.util.ParetoFrontValidator.GeneratedFrontReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Suíte de testes para validação da prioridade canônica das frentes de Pareto de referência
 * (resources/referenceFronts/ -> pf_gen/ -> geração por código) e da estrita separação
 * entre pf_gen/ e pf_lowzero_gen/.
 */
class ReferenceFrontCanonicalPriorityTest {

    @Test
    @DisplayName("1. Prioridade 1: Arquivos existentes em resources/referenceFronts/ são consumidos diretamente sem geração")
    void testPriority1ResourcesReferenceFrontsDirectConsumption() {
        // WFG1 em 2D existe diretamente em resources/referenceFronts/
        ResolvedFrontArtifact wfg1_2d =
                ParetoFrontValidator.resolveCanonicalFrontArtifact("WFG1", 2, null, false, true);
        assertThat(wfg1_2d.sourceCategory()).isEqualTo("OFFICIAL");
        assertThat(wfg1_2d.path().toString().replace('\\', '/'))
                .contains("resources/referenceFronts/WFG1.2D.csv")
                .doesNotContain("pf_gen");

        // DTLZ1 em 2D existe diretamente em resources/referenceFronts/
        ResolvedFrontArtifact dtlz1_2d =
                ParetoFrontValidator.resolveCanonicalFrontArtifact("DTLZ1", 2, null, false, true);
        assertThat(dtlz1_2d.sourceCategory()).isEqualTo("OFFICIAL");
        assertThat(dtlz1_2d.path().toString().replace('\\', '/'))
                .contains("resources/referenceFronts/DTLZ1.2D.csv")
                .doesNotContain("pf_gen");
    }

    @Test
    @DisplayName("2. Prioridade 2: Frentes analíticas existentes em pf_gen/ são consumidas sem regeneração desnecessária")
    void testPriority2PfGenConsumptionWithoutRegeneration() {
        // DTLZ1 em 5D não existe na raiz de resources/referenceFronts/, mas existe em pf_gen/
        Path canonicalRoot = Path.of("resources", "referenceFronts", "DTLZ1.5D.csv");
        Path pfGenFile = Path.of("resources", "referenceFronts", "pf_gen", "DTLZ1.5D.csv");
        assertThat(canonicalRoot).doesNotExist();
        assertThat(pfGenFile).exists();

        ResolvedFrontArtifact dtlz1_5d =
                ParetoFrontValidator.resolveCanonicalFrontArtifact("DTLZ1", 5, null, false, true);
        assertThat(dtlz1_5d.sourceCategory()).isEqualTo("PF_GEN");
        assertThat(dtlz1_5d.path().toString().replace('\\', '/'))
                .contains("resources/referenceFronts/pf_gen/DTLZ1.5D.csv");
    }

    @Test
    @DisplayName("3. Prioridade 3: Geração por código ocorre SOMENTE quando ausente em resources/ e pf_gen/")
    void testPriority3CodeGenerationOnlyWhenAbsentInBoth(@TempDir Path tempDir) throws IOException {
        Path tempCanonical = tempDir.resolve("referenceFronts");
        Path tempPfGen = tempCanonical.resolve("pf_gen");
        Path tempLowZero = tempCanonical.resolve("pf_lowzero_gen");
        Files.createDirectories(tempPfGen);
        Files.createDirectories(tempLowZero);

        // Instância isolada sem o arquivo DTLZ2.5D.csv
        Path generatedTarget = tempPfGen.resolve("DTLZ2.5D.csv");
        assertThat(generatedTarget).doesNotExist();

        // Resolução com geração permitida
        Path resolved =
                ParetoFrontValidator.resolveCanonicalReferenceFront(
                        "DTLZ2", 5, tempCanonical, tempPfGen, tempLowZero, false, true);
        assertThat(resolved).exists().isEqualTo(generatedTarget);

        // Segunda resolução agora encontra na prioridade 2 (pf_gen/) sem gerar novamente
        long lastModified = Files.getLastModifiedTime(generatedTarget).toMillis();
        Path secondResolved =
                ParetoFrontValidator.resolveCanonicalReferenceFront(
                        "DTLZ2", 5, tempCanonical, tempPfGen, tempLowZero, false, false);
        assertThat(secondResolved).isEqualTo(generatedTarget);
        assertThat(Files.getLastModifiedTime(generatedTarget).toMillis()).isEqualTo(lastModified);
    }

    @Test
    @DisplayName("4. Separação estrita: pf_gen/ contém zeros puros e pf_lowzero_gen/ contém zeros substituídos")
    void testStrictSeparationBetweenPfGenAndPfLowZeroGen(@TempDir Path tempDir) {
        Path tempCanonical = tempDir.resolve("referenceFronts");
        Path tempPfGen = tempCanonical.resolve("pf_gen");
        Path tempLowZero = tempCanonical.resolve("pf_lowzero_gen");

        // 1. Gera frente pura em pf_gen/
        GeneratedFrontReport pureReport =
                ParetoFrontValidator.generateAndSaveAnalyticalDtlzFront(
                        "DTLZ1", 3, 12, 0, tempPfGen, true);
        assertThat(pureReport.outputFile().getParent()).isEqualTo(tempPfGen);

        FrontValidationSummary pureSummary =
                ParetoFrontValidator.validateParetoFrontFile(pureReport.outputFile(), 3);
        boolean hasExactZeroInPure = false;
        for (double[] point : pureSummary.points()) {
            for (double val : point) {
                if (val == 0.0) {
                    hasExactZeroInPure = true;
                    break;
                }
            }
        }
        assertThat(hasExactZeroInPure)
                .as("pf_gen/ deve conter coordenadas zero exatas do simplex de Das & Dennis")
                .isTrue();

        // 2. Gera variante com substituição de zero em pf_lowzero_gen/
        GeneratedFrontReport lowZeroReport =
                ParetoFrontValidator.generateAndSaveLowZeroDtlzFront(
                        "DTLZ1", 3, 12, 0, 1.0e-5, tempLowZero, true);
        assertThat(lowZeroReport.outputFile().getParent()).isEqualTo(tempLowZero);

        FrontValidationSummary lowZeroSummary =
                ParetoFrontValidator.validateParetoFrontFile(lowZeroReport.outputFile(), 3);
        boolean hasExactZeroInLowZero = false;
        boolean hasEpsilonInLowZero = false;
        for (double[] point : lowZeroSummary.points()) {
            for (double val : point) {
                if (val == 0.0) {
                    hasExactZeroInLowZero = true;
                }
                if (Math.abs(val - 1.0e-5) < 1e-12) {
                    hasEpsilonInLowZero = true;
                }
            }
        }
        assertThat(hasExactZeroInLowZero)
                .as("pf_lowzero_gen/ não pode conter coordenadas exatamente 0.0")
                .isFalse();
        assertThat(hasEpsilonInLowZero)
                .as("pf_lowzero_gen/ deve conter coordenadas substituídas por epsilon (1e-5)")
                .isTrue();
    }

    @Test
    @DisplayName("5. WFGStudy resolve frentes com caminhos relativos limpos para o jMetal")
    void testWfgStudyRelativePathsClean() {
        // WFG1 2D -> WFG1.2D.csv (sem prefixo pf_gen/)
        String wfgRel = WFGStudy.resolveReferenceFrontForStudy("WFG1", 2);
        assertThat(wfgRel).isEqualTo("WFG1.2D.csv");

        // DTLZ1 5D -> pf_gen/DTLZ1.5D.csv (prefixo limpo, sem '../../')
        String dtlzRel = WFGStudy.resolveReferenceFrontForStudy("DTLZ1", 5);
        assertThat(dtlzRel).isEqualTo("pf_gen/DTLZ1.5D.csv").doesNotContain("..");
    }

    @Test
    @DisplayName("6. WFGStudy resolve diretório de pesos do MOEA/DD para todas as 6 dimensões ativas")
    void testWfgStudyWeightsDirectoryResolutionAllDimensions() {
        String baseWeights = WFGStudy.DEFAULT_WEIGHTS_DIRECTORY;

        // m=3, N=91 -> resources/weightVectorFiles
        String dir3 = WFGStudy.resolveMoeadDataDirectory(baseWeights, 3, 91);
        assertThat(dir3).isEqualTo("resources/weightVectorFiles");

        // m=5, N=210 -> resources/weightVectorFiles/weights_gen (resolução canônica via gerado/on-demand, sem fallback legado)
        String dir5 = WFGStudy.resolveMoeadDataDirectory(baseWeights, 5, 210);
        assertThat(dir5).isEqualTo("resources/weightVectorFiles/weights_gen");

        // m=8, N=156 -> resources/weightVectorFiles
        String dir8 = WFGStudy.resolveMoeadDataDirectory(baseWeights, 8, 156);
        assertThat(dir8).isEqualTo("resources/weightVectorFiles");

        // m=10, N=275 -> resources/weightVectorFiles
        String dir10 = WFGStudy.resolveMoeadDataDirectory(baseWeights, 10, 275);
        assertThat(dir10).isEqualTo("resources/weightVectorFiles");

        // m=15, N=135 -> resources/weightVectorFiles/weights_gen (resolução canônica via gerado/on-demand, sem fallback legado)
        String dir15 = WFGStudy.resolveMoeadDataDirectory(baseWeights, 15, 135);
        assertThat(dir15).isEqualTo("resources/weightVectorFiles/weights_gen");
    }
}


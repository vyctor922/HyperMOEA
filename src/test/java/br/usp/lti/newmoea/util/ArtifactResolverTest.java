package br.usp.lti.newmoea.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.util.ArtifactResolver.ArtifactManifestEntry;
import br.usp.lti.newmoea.util.ArtifactResolver.FrontSourcePolicy;
import br.usp.lti.newmoea.util.ArtifactResolver.ResolvedFrontArtifact;
import br.usp.lti.newmoea.util.ArtifactResolver.ResolvedWeightArtifact;
import br.usp.lti.newmoea.util.ArtifactResolver.WeightSourcePolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Suíte de testes automatizados para {@link ArtifactResolver} (T18):
 * <ul>
 *   <li>Resolução canônica dos 6 arquivos ativos de vetores de pesos sob as 3 políticas
 *       ({@link WeightSourcePolicy#LEGACY_MOEAD_WEIGHTS},
 *       {@link WeightSourcePolicy#CANONICAL_ZERO_REPLACED} e
 *       {@link WeightSourcePolicy#OFFICIAL_JMETAL_RAW});</li>
 *   <li>Verificação explícita de {@code usedInMemoryByMoead} (\(m = 2, N \le 300\) vs. \(m \ge 3\)
 *       ou \(N > 300\));</li>
 *   <li>Resolução canônica das 96 instâncias de frentes de Pareto ({@code WFG1..9} e {@code DTLZ1..7}
 *       em \(m \in \{2, 3, 5, 8, 10, 15\}\)) sob as políticas
 *       {@link FrontSourcePolicy#OFFICIAL_CSV_ONLY},
 *       {@link FrontSourcePolicy#OFFICIAL_THEN_ANALYTICAL} e
 *       {@link FrontSourcePolicy#ANALYTICAL_PREFERRED_FOR_DTLZ};</li>
 *   <li>Exportação do manifesto consolidado em {@code docs/auditoria/artifact-manifest.csv} e
 *       verificação de integridade SHA-256;</li>
 *   <li>Testes de diretórios isolados ({@code @TempDir}), precedência e falhas controladas para
 *       arquivos ausentes ou malformados.</li>
 * </ul>
 */
class ArtifactResolverTest {

    static Stream<Arguments> activeWeightConfigurations() {
        return Stream.of(
                Arguments.of(
                        2,
                        100,
                        "8bdd2bd030389f3667bd15bb9d282095aa69d0c7e3eb5222aaafdac136caa8b9",
                        false,
                        true),
                Arguments.of(
                        3,
                        91,
                        "efa21ba6729b44e4c0f5eb83fb77276607736ec68700c71c3fe72871508c0b95",
                        true,
                        false),
                Arguments.of(
                        5,
                        210,
                        "f3e4ca3ad0f95808d5b9736eeeda9383864f8aff397698b6585499e5f2bd3825",
                        false,
                        false),
                Arguments.of(
                        8,
                        156,
                        "42f0ab3142b3fce0680fbb6505ab9d24dd27f91c1fdded47c16cb6773ec853db",
                        true,
                        false),
                Arguments.of(
                        10,
                        275,
                        "91cca614fe043c8952f383da37134d108089e14b52815a68f3f0b7231d717583",
                        true,
                        false),
                Arguments.of(
                        15,
                        135,
                        "52d429f53ce81b4ab0c33468a6974e1a027c502a631aba9f8e49047f5d2fbcc1",
                        false,
                        false));
    }

    @ParameterizedTest(name = "m={0}, N={1}, hasJMetalOfficial={3}, inMemory={4}")
    @MethodSource("activeWeightConfigurations")
    @DisplayName("T18.1: Resolução dos 6 arquivos ativos de pesos em LEGACY_MOEAD_WEIGHTS e OFFICIAL_JMETAL_RAW")
    void testResolveActiveWeightVectors(
            int m,
            int popSize,
            String expectedSha256,
            boolean existsInOfficialJMetalDir,
            boolean expectedInMemory) {
        // 1. Política LEGACY_MOEAD_WEIGHTS
        ResolvedWeightArtifact legacy =
                ArtifactResolver.resolveWeightVectorFile(
                        m, popSize, WeightSourcePolicy.LEGACY_MOEAD_WEIGHTS);
        assertThat(legacy.m()).isEqualTo(m);
        assertThat(legacy.populationSize()).isEqualTo(popSize);
        assertThat(legacy.sha256()).isEqualTo(expectedSha256);
        assertThat(legacy.usedInMemoryByMoead()).isEqualTo(expectedInMemory);
        assertThat(legacy.sourceCategory()).isEqualTo("LEGACY_MOEAD_WEIGHTS");
        assertThat(legacy.validatedWeights()).hasDimensions(popSize, m);
        assertThat(legacy.path()).exists().isRegularFile();

        // 2. Política OFFICIAL_JMETAL_RAW
        ResolvedWeightArtifact official =
                ArtifactResolver.resolveWeightVectorFile(
                        m, popSize, WeightSourcePolicy.OFFICIAL_JMETAL_RAW);
        assertThat(official.m()).isEqualTo(m);
        assertThat(official.populationSize()).isEqualTo(popSize);
        assertThat(official.sha256()).isEqualTo(expectedSha256);
        assertThat(official.usedInMemoryByMoead()).isEqualTo(expectedInMemory);
        assertThat(official.validatedWeights()).hasDimensions(popSize, m);

        if (existsInOfficialJMetalDir) {
            assertThat(official.sourceCategory()).isEqualTo("OFFICIAL_JMETAL_RAW");
            assertThat(official.path().toString().replace('\\', '/'))
                    .contains("resources/weightVectorFiles/W" + m + "D_" + popSize + ".dat");
        } else {
            assertThat(official.sourceCategory())
                    .isEqualTo("FALLBACK_LEGACY_NO_JMETAL_COUNTERPART");
            assertThat(official.path().toString().replace('\\', '/'))
                    .contains("MOEAD_Weights/W" + m + "D_" + popSize + ".dat");
        }
    }

    @Test
    @DisplayName("T18.2: Política CANONICAL_ZERO_REPLACED prefere weights_gen quando existente e faz fallback para MOEAD_Weights")
    void testCanonicalZeroReplacedPolicyPrecedence() {
        // Garante que W3D_91.dat existe em resources/weightVectorFiles/weights_gen/
        Path officialW3D = Path.of("resources", "weightVectorFiles", "W3D_91.dat");
        Path weightsGenDir = Path.of("resources", "weightVectorFiles", "weights_gen");
        ReferenceSetValidator.replaceZeroWeights(officialW3D, weightsGenDir, 1e-5, true);

        ResolvedWeightArtifact w3d =
                ArtifactResolver.resolveWeightVectorFile(
                        3, 91, WeightSourcePolicy.CANONICAL_ZERO_REPLACED);
        assertThat(w3d.sourceCategory()).isEqualTo("WEIGHTS_GEN_ZERO_REPLACED");
        assertThat(w3d.path().toString().replace('\\', '/'))
                .contains("resources/weightVectorFiles/weights_gen/W3D_91.dat");
        assertThat(w3d.usedInMemoryByMoead()).isFalse();
        assertThat(w3d.validatedWeights()).hasDimensions(91, 3);

        // Para m=5, N=210 (que não está em weights_gen por padrão), faz fallback para MOEAD_Weights/W5D_210.dat
        Path genW5D = weightsGenDir.resolve("W5D_210.dat");
        if (!Files.exists(genW5D)) {
            ResolvedWeightArtifact w5d =
                    ArtifactResolver.resolveWeightVectorFile(
                            5, 210, WeightSourcePolicy.CANONICAL_ZERO_REPLACED);
            assertThat(w5d.sourceCategory()).isEqualTo("LEGACY_MOEAD_WEIGHTS_FALLBACK");
            assertThat(w5d.path().toString().replace('\\', '/'))
                    .contains("MOEAD_Weights/W5D_210.dat");
        }
    }

    @Test
    @DisplayName("T18.3: Verificação de isMoeadWeightGeneratedInMemory (m=2, N<=300 vs N>300 e m>=3)")
    void testMoeadInMemoryRule() {
        assertThat(ArtifactResolver.isMoeadWeightGeneratedInMemory(2, 100)).isTrue();
        assertThat(ArtifactResolver.isMoeadWeightGeneratedInMemory(2, 300)).isTrue();
        assertThat(ArtifactResolver.isMoeadWeightGeneratedInMemory(2, 301)).isFalse();
        assertThat(ArtifactResolver.isMoeadWeightGeneratedInMemory(2, 400)).isFalse();
        assertThat(ArtifactResolver.isMoeadWeightGeneratedInMemory(3, 91)).isFalse();
        assertThat(ArtifactResolver.isMoeadWeightGeneratedInMemory(15, 135)).isFalse();
    }

    @Test
    @DisplayName("T18.4: Resolução de frentes de referência sob OFFICIAL_CSV_ONLY, OFFICIAL_THEN_ANALYTICAL e ANALYTICAL_PREFERRED_FOR_DTLZ")
    void testResolveReferenceFrontPolicies() {
        // 1. WFG em todas as dimensões existe em pareto_fronts/csv
        for (int m : ArtifactResolver.ACTIVE_OBJECTIVE_DIMENSIONS) {
            ResolvedFrontArtifact wfg =
                    ArtifactResolver.resolveReferenceFront(
                            "WFG1", m, FrontSourcePolicy.OFFICIAL_CSV_ONLY);
            assertThat(wfg.sourceCategory()).isEqualTo("OFFICIAL_PARETO_CSV");
            assertThat(wfg.problemName()).isEqualTo("WFG1");
            assertThat(wfg.m()).isEqualTo(m);
            assertThat(wfg.lineCount()).isPositive();
            assertThat(wfg.sha256()).hasSize(64);
            assertThat(wfg.path().toString()).doesNotContain("..");
        }

        // 2. DTLZ em m=2,3 resolve em pareto_fronts/csv com OFFICIAL_CSV_ONLY e OFFICIAL_THEN_ANALYTICAL
        for (int m : new int[] {2, 3}) {
            ResolvedFrontArtifact dtlzOfficial =
                    ArtifactResolver.resolveReferenceFront(
                            "DTLZ2_" + m, m, FrontSourcePolicy.OFFICIAL_CSV_ONLY);
            assertThat(dtlzOfficial.sourceCategory()).isEqualTo("OFFICIAL_PARETO_CSV");
            assertThat(dtlzOfficial.problemName()).isEqualTo("DTLZ2");
            assertThat(dtlzOfficial.path().toString().replace('\\', '/'))
                    .contains("pareto_fronts/csv/DTLZ2." + m + "D.csv");
        }

        // 3. DTLZ em m > 3 deve falhar claramente com OFFICIAL_CSV_ONLY
        for (int m : new int[] {5, 8, 10, 15}) {
            final int dim = m;
            assertThatThrownBy(
                            () ->
                                    ArtifactResolver.resolveReferenceFront(
                                            "DTLZ1", dim, FrontSourcePolicy.OFFICIAL_CSV_ONLY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("OFFICIAL_CSV_ONLY")
                    .hasMessageContaining("DTLZ1");
        }

        // 4. DTLZ em m in {5, 8, 10, 15} resolve sem relativize ('..') em resources/referenceFronts/pf_gen/
        for (String dtlz : ArtifactResolver.DTLZ_PROBLEMS) {
            for (int m : new int[] {5, 8, 10, 15}) {
                ResolvedFrontArtifact resolved =
                        ArtifactResolver.resolveReferenceFront(
                                dtlz, m, FrontSourcePolicy.OFFICIAL_THEN_ANALYTICAL);
                assertThat(resolved.sourceCategory()).isEqualTo("ANALYTICAL_PF_GEN_DTLZ");
                assertThat(resolved.problemName()).isEqualTo(dtlz);
                assertThat(resolved.m()).isEqualTo(m);
                assertThat(resolved.lineCount()).isEqualTo(resolved.uniquePointCount());
                assertThat(resolved.path().toString().replace('\\', '/'))
                        .contains(
                                "resources/referenceFronts/pf_gen/"
                                        + dtlz
                                        + "."
                                        + m
                                        + "D.csv")
                        .doesNotContain("..");
            }
        }

        // 5. ANALYTICAL_PREFERRED_FOR_DTLZ usa analítica até mesmo para m=2,3 em DTLZ, e oficial para WFG
        ResolvedFrontArtifact dtlz2Analytical =
                ArtifactResolver.resolveReferenceFront(
                        "DTLZ1", 2, FrontSourcePolicy.ANALYTICAL_PREFERRED_FOR_DTLZ);
        assertThat(dtlz2Analytical.sourceCategory()).isEqualTo("ANALYTICAL_PF_GEN_DTLZ");
        assertThat(dtlz2Analytical.lineCount()).isEqualTo(StaticParams.getPopulationSize(2));

        ResolvedFrontArtifact wfgAnalyticalPref =
                ArtifactResolver.resolveReferenceFront(
                        "WFG4", 3, FrontSourcePolicy.ANALYTICAL_PREFERRED_FOR_DTLZ);
        assertThat(wfgAnalyticalPref.sourceCategory()).isEqualTo("OFFICIAL_PARETO_CSV");
    }

    @Test
    @DisplayName("T18.5: Exportação e verificação do manifesto consolidado docs/auditoria/artifact-manifest.csv")
    void testExportActiveArtifactManifestCsv() throws IOException {
        Path manifestPath = Path.of("docs", "auditoria", "artifact-manifest.csv");
        List<ArtifactManifestEntry> entries =
                ArtifactResolver.exportActiveArtifactManifestCsv(manifestPath);

        // 6 dimensões * 3 políticas de pesos (18) + 54 WFG + 42 DTLZ = 114 entradas
        assertThat(entries).hasSize(18 + 54 + 42);
        assertThat(manifestPath).exists().isRegularFile();

        List<String> lines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(1 + entries.size());
        assertThat(lines.get(0)).isEqualTo(ArtifactManifestEntry.CSV_HEADER);

        long weightCount =
                entries.stream().filter(e -> "WEIGHT_VECTOR".equals(e.artifactType())).count();
        long frontCount =
                entries.stream().filter(e -> "REFERENCE_FRONT".equals(e.artifactType())).count();
        long officialFronts =
                entries.stream()
                        .filter(e -> "OFFICIAL_PARETO_CSV".equals(e.sourceCategory()))
                        .count();
        long analyticalFronts =
                entries.stream()
                        .filter(e -> "ANALYTICAL_PF_GEN_DTLZ".equals(e.sourceCategory()))
                        .count();

        assertThat(weightCount).isEqualTo(18);
        assertThat(frontCount).isEqualTo(96);
        assertThat(officialFronts).isEqualTo(68);
        assertThat(analyticalFronts).isEqualTo(28);
    }

    @Test
    @DisplayName("T18.6: Testes negativos de isolamento, arquivos ausentes e proteção de diretórios originais")
    void testIsolatedBaseDirAndNegativeCases(@TempDir Path tempDir) {
        ArtifactResolver emptyResolver = new ArtifactResolver(tempDir);

        // 1. Diretório isolado sem arquivos deve falhar com mensagem clara
        assertThatThrownBy(
                        () ->
                                emptyResolver.resolveWeightVector(
                                        3, 91, WeightSourcePolicy.LEGACY_MOEAD_WEIGHTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrado");

        assertThatThrownBy(
                        () ->
                                emptyResolver.resolveFront(
                                        "WFG1", 2, FrontSourcePolicy.OFFICIAL_CSV_ONLY, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ausente");

        assertThatThrownBy(
                        () ->
                                emptyResolver.resolveFront(
                                        "DTLZ1",
                                        5,
                                        FrontSourcePolicy.OFFICIAL_THEN_ANALYTICAL,
                                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("geração sob demanda está desativada");

        // 2. Proibição de gravar manifesto dentro de MOEAD_Weights/ ou pareto_fronts/csv/
        ArtifactResolver defaultResolver = new ArtifactResolver();
        assertThatThrownBy(
                        () ->
                                defaultResolver.exportManifestCsv(
                                        Path.of("MOEAD_Weights", "manifest.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proibido gravar manifesto");

        assertThatThrownBy(
                        () ->
                                defaultResolver.exportManifestCsv(
                                        Path.of("pareto_fronts", "csv", "manifest.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proibido gravar manifesto");
    }
}

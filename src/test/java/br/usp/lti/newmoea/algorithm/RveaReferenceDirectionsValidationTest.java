package br.usp.lti.newmoea.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import br.usp.lti.newmoea.util.ReferenceSetValidator;
import br.usp.lti.newmoea.util.ReferenceSetValidator.ReferencePersistenceReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetComparisonReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.SetValidationReport;
import br.usp.lti.newmoea.util.ReferenceSetValidator.Verdict;
import java.lang.reflect.Field;
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
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.component.algorithm.EvolutionaryAlgorithm;
import org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder;
import org.uma.jmetal.component.catalogue.ea.replacement.impl.RVEAReplacement;
import org.uma.jmetal.component.catalogue.ea.replacement.impl.rvea.RVEAEnvironmentalSelection;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.referencepoint.ReferencePointGenerator;

/**
 * Testes dedicados de revalidação matemática das direções de referência do RVEA (T07 — Parte 2 e Parte 3).
 *
 * <p>Valida:
 * <ol>
 *   <li>Cardinalidade, dimensionalidade, soma=1, não-negatividade, ausência de duplicatas e finitude
 *       dos vetores gerados por {@link ReferencePointGenerator} em {@code WFGStudy.buildRVEA} para
 *       \(m \in \{2, 3, 5, 8, 10, 15\}\);</li>
 *   <li>Passagem de {@code referenceVectors.size()} como {@code populationSize}, {@code alpha = 2.0},
 *       {@code fr = 0.1} e conversão interna em direções unitárias (\(\|\mathbf{v}\|_2 = 1\)) dentro de
 *       {@link RVEAEnvironmentalSelection};</li>
 *   <li>Validações de pré-condições de {@link RVEABuilder} (tamanho da população incompatível com
 *       {@code referenceVectors.size()}, {@code alpha < 0}, {@code fr} fora de \((0, 1]\));</li>
 *   <li>Persistência opcional via {@code -DpersistReferenceDirections=false|true} em
 *       {@code resources/referencePoints/rvea/RVEA_m{m}_H1{H1}_H2{H2}.csv}, proteção contra
 *       sobrescrita e imutabilidade dos vetores consumidos pelo algoritmo.</li>
 * </ol>
 */
class RveaReferenceDirectionsValidationTest {

    private static final double TOLERANCE = 1e-6;

    static Stream<Arguments> rveaCanonicalConfigurations() {
        return Stream.of(
                Arguments.of(2, 99, 0, 100),
                Arguments.of(3, 12, 0, 91),
                Arguments.of(5, 6, 0, 210),
                Arguments.of(8, 3, 2, 156),
                Arguments.of(10, 3, 2, 275),
                Arguments.of(15, 2, 1, 135));
    }

    @ParameterizedTest(name = "m={0}, H1={1}, H2={2} -> N={3}")
    @MethodSource("rveaCanonicalConfigurations")
    @DisplayName("T07-RVEA.1: Validação matemática dos vetores de referência de entrada (simplex) e direções unitárias internas (L2=1) do RVEA")
    void testRveaReferenceVectorsAndInternalUnitDirections(
            int m, int outerDivisions, int innerDivisions, int expectedCount) throws Exception {
        assertThat(StaticParams.getPopulationSize(m)).isEqualTo(expectedCount);
        assertThat(StaticParams.getOuterDivisions(m)).isEqualTo(outerDivisions);
        assertThat(StaticParams.getInnerDivisions(m)).isEqualTo(innerDivisions);

        List<double[]> referenceVectors = (innerDivisions == 0)
                ? ReferencePointGenerator.generateSingleLayer(m, outerDivisions)
                : ReferencePointGenerator.generateTwoLayers(m, outerDivisions, innerDivisions);

        double[][] simplexVectors = referenceVectors.toArray(double[][]::new);

        // 1. Valida propriedades de simplex dos vetores gerados por ReferencePointGenerator
        SetValidationReport simplexReport = ReferenceSetValidator.validateSimplexSet(
                simplexVectors, expectedCount, m, TOLERANCE, TOLERANCE);
        assertThat(simplexReport.validSimplex()).isTrue();
        assertThat(simplexReport.vectorCount()).isEqualTo(expectedCount);
        assertThat(simplexReport.dimension()).isEqualTo(m);
        assertThat(simplexReport.allFinite()).isTrue();
        assertThat(simplexReport.allNonNegative()).isTrue();
        assertThat(simplexReport.maxSumDeviationFromOne()).isLessThanOrEqualTo(1e-15);
        assertThat(simplexReport.duplicateCount()).isZero();

        // 2. Equivalência 100% com o gerador canônico de Das & Dennis / Deb & Jain
        double[][] canonical = ReferenceSetValidator.generateCanonicalTwoLayers(m, outerDivisions, innerDivisions);
        SetComparisonReport cmp = ReferenceSetValidator.compareSetsOrderIndependent(
                canonical, simplexVectors, TOLERANCE);
        assertThat(cmp.verdict()).isEqualTo(Verdict.EQUIVALENT);
        assertThat(cmp.maxCoordinateError()).isLessThan(1e-15);

        // 3. Instancia o RVEA via WFGStudy.buildRVEA e inspeciona o RVEAEnvironmentalSelection interno
        Problem<DoubleSolution> problem = new WFG1(2 * (m - 1), 20, m);
        @SuppressWarnings("unchecked")
        Algorithm<List<DoubleSolution>> algorithm = WFGStudy.buildRVEA(
                problem,
                expectedCount,
                new SBXCrossover(1.0, 30.0),
                new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0));

        assertThat(algorithm).isInstanceOf(EvolutionaryAlgorithm.class);
        EvolutionaryAlgorithm<DoubleSolution> ea = (EvolutionaryAlgorithm<DoubleSolution>) algorithm;

        Field replacementField = EvolutionaryAlgorithm.class.getDeclaredField("replacement");
        replacementField.setAccessible(true);
        Object replacement = replacementField.get(ea);
        assertThat(replacement).isInstanceOf(RVEAReplacement.class);

        Field envSelField = RVEAReplacement.class.getDeclaredField("environmentalSelection");
        envSelField.setAccessible(true);
        RVEAEnvironmentalSelection<DoubleSolution> rveaSelection =
                (RVEAEnvironmentalSelection<DoubleSolution>) envSelField.get(replacement);

        Field alphaField = RVEAEnvironmentalSelection.class.getDeclaredField("alpha");
        alphaField.setAccessible(true);
        double actualAlpha = alphaField.getDouble(rveaSelection);
        assertThat(actualAlpha).isEqualTo(2.0);

        Field maxGenField = RVEAEnvironmentalSelection.class.getDeclaredField("maxGenerations");
        maxGenField.setAccessible(true);
        int actualMaxGenerations = maxGenField.getInt(rveaSelection);
        // Em RVEABuilder.maxGenerations(populationSize, maxEvaluations), desconta-se a geração inicial:
        // ceil((maxEvaluations - populationSize) / populationSize) = maxGenerations - 1
        int expectedMaxGenerations = StaticParams.getMaxGenerations(m, problem.name()) - 1;
        assertThat(actualMaxGenerations).isEqualTo(expectedMaxGenerations);

        Field adaptFreqField = RVEAEnvironmentalSelection.class.getDeclaredField("adaptationFrequency");
        adaptFreqField.setAccessible(true);
        int actualAdaptationFrequency = adaptFreqField.getInt(rveaSelection);
        int expectedAdaptationFrequency = Math.max(1, (int) Math.round(expectedMaxGenerations * 0.1));
        assertThat(actualAdaptationFrequency).isEqualTo(expectedAdaptationFrequency);

        Field initialRefField = RVEAEnvironmentalSelection.class.getDeclaredField("initialReferenceVectors");
        initialRefField.setAccessible(true);
        double[][] unitDirections = (double[][]) initialRefField.get(rveaSelection);

        assertThat(unitDirections).hasDimensions(expectedCount, m);
        double maxUnitNormError = 0.0;
        double maxDirectionCoordDiff = 0.0;
        for (int i = 0; i < expectedCount; i++) {
            double[] dir = unitDirections[i];
            double[] orig = simplexVectors[i];
            assertThat(dir).hasSize(m);

            double origNorm = 0.0;
            for (double v : orig) {
                origNorm += v * v;
            }
            origNorm = Math.sqrt(origNorm);

            double dirNormSq = 0.0;
            for (int j = 0; j < m; j++) {
                assertThat(Double.isFinite(dir[j])).isTrue();
                assertThat(dir[j]).isGreaterThanOrEqualTo(0.0);
                dirNormSq += dir[j] * dir[j];
                double expectedUnitCoord = orig[j] / origNorm;
                maxDirectionCoordDiff = Math.max(maxDirectionCoordDiff, Math.abs(dir[j] - expectedUnitCoord));
            }
            double dirNorm = Math.sqrt(dirNormSq);
            maxUnitNormError = Math.max(maxUnitNormError, Math.abs(dirNorm - 1.0));
        }

        System.out.printf(
                "[T07-RVEA] m=%d (H1=%d, H2=%d) | N=%d | simplexMaxSumDev=%.3e | unitMaxNormErr=%.3e | maxDirCoordDiff=%.3e | alpha=%.1f, adaptFreq=%d/%d%n",
                m,
                outerDivisions,
                innerDivisions,
                expectedCount,
                simplexReport.maxSumDeviationFromOne(),
                maxUnitNormError,
                maxDirectionCoordDiff,
                actualAlpha,
                actualAdaptationFrequency,
                actualMaxGenerations);

        assertThat(maxUnitNormError).isLessThanOrEqualTo(1e-15);
        assertThat(maxDirectionCoordDiff).isLessThanOrEqualTo(1e-15);
    }

    @Test
    @DisplayName("T07-RVEA.2: Rejeição controlada quando populationSize diverge de referenceVectors.size() ou parâmetros do RVEABuilder são inválidos")
    void testRveaBuilderPreconditionValidations() {
        Problem<DoubleSolution> problem = new WFG1(4, 20, 3);
        SBXCrossover crossover = new SBXCrossover(1.0, 30.0);
        PolynomialMutation mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        List<double[]> refVectors = ReferencePointGenerator.generateSingleLayer(3, 12); // 91 vetores

        // 1. WFGStudy.buildRVEA rejeita populationSize != referenceVectors.size()
        assertThatThrownBy(() -> WFGStudy.buildRVEA(problem, 100, crossover, mutation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("populationSize=100 difere de referenceVectors.size()=91");

        // 2. RVEABuilder rejeita populationSize != referenceVectors.size() via Check.that (InvalidConditionException)
        assertThatThrownBy(() -> new RVEABuilder<>(
                        problem, 50, 1000, crossover, mutation, 2.0, 0.1, refVectors))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Population size must match the number of reference vectors");

        // 3. RVEABuilder rejeita alpha < 0
        assertThatThrownBy(() -> new RVEABuilder<>(
                        problem, 91, 1000, crossover, mutation, -0.5, 0.1, refVectors))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("alpha");

        // 4. RVEABuilder rejeita fr <= 0 ou fr > 1
        assertThatThrownBy(() -> new RVEABuilder<>(
                        problem, 91, 1000, crossover, mutation, 2.0, 0.0, refVectors))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fr");
    }

    @Test
    @DisplayName("T07-RVEA.3: Persistência opcional (-DpersistReferenceDirections) desativada por padrão, ativação controlada, proteção contra sobrescrita e imutabilidade")
    void testRveaOptionalPersistenceAndNonOverwriteProtection(@TempDir Path tempDir) throws Exception {
        String prevProp = System.getProperty(ReferenceSetValidator.PERSIST_REFERENCE_DIRECTIONS_PROPERTY);
        try {
            // 1. Desativada por padrão
            System.clearProperty(ReferenceSetValidator.PERSIST_REFERENCE_DIRECTIONS_PROPERTY);
            assertThat(ReferenceSetValidator.isPersistReferenceDirectionsEnabled()).isFalse();

            int m = 8;
            int h1 = StaticParams.getOuterDivisions(m);
            int h2 = StaticParams.getInnerDivisions(m);
            int expectedCount = StaticParams.getPopulationSize(m);
            List<double[]> refVectors = ReferencePointGenerator.generateTwoLayers(m, h1, h2);

            double[][] snapshotBefore = new double[refVectors.size()][m];
            for (int i = 0; i < refVectors.size(); i++) {
                snapshotBefore[i] = refVectors.get(i).clone();
            }

            double[][] matrixInput = ReferenceSetValidator.deepCopyVectors(refVectors);
            ReferencePersistenceReport disabledReport =
                    ReferenceSetValidator.persistReferenceSetCsv(
                            matrixInput,
                            m,
                            h1,
                            h2,
                            "RVEA",
                            tempDir,
                            ReferenceSetValidator.isPersistReferenceDirectionsEnabled(),
                            false);
            assertThat(disabledReport.enabled()).isFalse();
            assertThat(disabledReport.written()).isFalse();
            assertThat(Files.exists(tempDir.resolve("RVEA_m8_H13_H22.csv"))).isFalse();

            // 2. Ativada (-DpersistReferenceDirections=true)
            System.setProperty(ReferenceSetValidator.PERSIST_REFERENCE_DIRECTIONS_PROPERTY, "true");
            assertThat(ReferenceSetValidator.isPersistReferenceDirectionsEnabled()).isTrue();

            Path defaultTarget = ReferenceSetValidator.DEFAULT_RVEA_PERSIST_DIR.resolve("RVEA_m8_H13_H22.csv");
            boolean existedBeforeDefault = Files.exists(defaultTarget);
            try {
                Problem<DoubleSolution> p8 = new WFG1(14, 20, 8);
                WFGStudy.buildRVEA(
                        p8,
                        expectedCount,
                        new SBXCrossover(1.0, 30.0),
                        new PolynomialMutation(1.0 / p8.numberOfVariables(), 20.0));
                assertThat(Files.exists(defaultTarget)).isTrue();
            } finally {
                if (!existedBeforeDefault) {
                    Files.deleteIfExists(defaultTarget);
                }
            }

            ReferencePersistenceReport enabledReport =
                    ReferenceSetValidator.persistReferenceSetCsv(
                            matrixInput,
                            m,
                            h1,
                            h2,
                            "RVEA",
                            tempDir,
                            ReferenceSetValidator.isPersistReferenceDirectionsEnabled(),
                            false);
            assertThat(enabledReport).isNotNull();
            assertThat(enabledReport.enabled()).isTrue();
            assertThat(enabledReport.written()).isTrue();
            assertThat(enabledReport.suffixedDueToConflict()).isFalse();
            assertThat(enabledReport.outputFile().getFileName().toString())
                    .isEqualTo("RVEA_m8_H13_H22.csv");
            assertThat(Files.exists(enabledReport.outputFile())).isTrue();

            List<String> lines = Files.readAllLines(enabledReport.outputFile(), StandardCharsets.UTF_8);
            assertThat(lines.get(0)).isEqualTo("index,f1,f2,f3,f4,f5,f6,f7,f8");
            assertThat(lines).hasSize(expectedCount + 1);

            // 3. Verifica que a lista original refVectors permanece 100% inalterada
            for (int i = 0; i < refVectors.size(); i++) {
                assertThat(refVectors.get(i)).containsExactly(snapshotBefore[i]);
            }

            // 4. Proteção contra sobrescrita destrutiva quando o arquivo existente possui conteúdo diferente
            Path existingFile = enabledReport.outputFile();
            String customContent = "index,f1,f2,f3,f4,f5,f6,f7,f8\n0,1,0,0,0,0,0,0,0\n";
            Files.writeString(existingFile, customContent, StandardCharsets.UTF_8);

            assertThatThrownBy(() -> ReferenceSetValidator.persistReferenceSetCsv(
                            matrixInput, m, h1, h2, "RVEA", tempDir, true, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Arquivo de referência já existe e não pode ser sobrescrito automaticamente");
            assertThat(Files.readString(existingFile, StandardCharsets.UTF_8)).isEqualTo(customContent);

            ReferencePersistenceReport suffixedReport =
                    ReferenceSetValidator.persistReferenceSetCsv(
                            matrixInput, m, h1, h2, "RVEA", tempDir, true, false);
            assertThat(suffixedReport.written()).isTrue();
            assertThat(suffixedReport.suffixedDueToConflict()).isTrue();
            assertThat(suffixedReport.outputFile().getFileName().toString())
                    .isEqualTo("RVEA_m8_H13_H22_run1.csv");
            assertThat(Files.readString(existingFile, StandardCharsets.UTF_8)).isEqualTo(customContent);
        } finally {
            if (prevProp == null) {
                System.clearProperty(ReferenceSetValidator.PERSIST_REFERENCE_DIRECTIONS_PROPERTY);
            } else {
                System.setProperty(ReferenceSetValidator.PERSIST_REFERENCE_DIRECTIONS_PROPERTY, prevProp);
            }
        }
    }
}

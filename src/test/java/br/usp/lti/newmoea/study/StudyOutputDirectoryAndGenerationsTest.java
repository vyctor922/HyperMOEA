package br.usp.lti.newmoea.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.WFGStudy.StudyOutputLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder;
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
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;
import org.uma.jmetal.util.referencepoint.ReferencePointGenerator;

/**
 * Testes da Tarefa T32:
 * <ul>
 * <li>Resolução de {@code study.outputDirectory} relativo simples, relativo
 * aninhado, absoluto
 * simples e absoluto aninhado, incluindo execução real de
 * {@code ExecuteAlgorithms},
 * {@code ComputeQualityIndicators}, {@code GenerateLatexTablesWithStatistics},
 * {@code GenerateWilcoxonTestTablesWithR}, {@code GenerateFriedmanTestTables} e
 * {@code GenerateBoxplotsWithR};</li>
 * <li>Rejeição de caminhos vazios e proteção dos diretórios legados
 * ({@code MOEAD_Weights/},
 * {@code pareto_fronts/}, {@code pareto/});</li>
 * <li>Caracterização completa da tabela
 * {@link StaticParams#getMaxGenerations(int, String)} para
 * todos os pares {@code (problema, m)} explicitamente aprovados e isolamento
 * documental do
 * item pendente de decisão para {@code DTLZ5}/{@code DTLZ6}.</li>
 * </ul>
 */
class StudyOutputDirectoryAndGenerationsTest {

        @Test
        @DisplayName("T32: resolveStudyOutputLayout separa parentBaseDirectory e experimentName simples para caminhos relativos, absolutos e aninhados")
        void testResolveStudyOutputLayoutVariants(@TempDir Path tempDir) {
                // 1. Relativo simples
                StudyOutputLayout simpleRel = WFGStudy.resolveStudyOutputLayout(tempDir, "WFGStudy");
                assertThat(simpleRel.outputDirectory())
                                .isEqualTo(tempDir.resolve("WFGStudy").toAbsolutePath().normalize());
                assertThat(simpleRel.parentBaseDirectory()).isEqualTo(tempDir.toAbsolutePath().normalize());
                assertThat(simpleRel.experimentName()).isEqualTo("WFGStudy");

                // 2. Relativo aninhado com barras normais e invertidas
                StudyOutputLayout nestedRel = WFGStudy.resolveStudyOutputLayout(tempDir, "WFGStudy/tests/smoke_nested");
                assertThat(nestedRel.outputDirectory())
                                .isEqualTo(tempDir.resolve("WFGStudy/tests/smoke_nested").toAbsolutePath().normalize());
                assertThat(nestedRel.parentBaseDirectory())
                                .isEqualTo(tempDir.resolve("WFGStudy/tests").toAbsolutePath().normalize());
                assertThat(nestedRel.experimentName()).isEqualTo("smoke_nested");

                StudyOutputLayout nestedWinSep = WFGStudy.resolveStudyOutputLayout(tempDir,
                                "WFGStudy\\tests\\win_smoke");
                assertThat(nestedWinSep.experimentName()).isEqualTo("win_smoke");
                assertThat(nestedWinSep.builderBaseDirectoryString()).doesNotContain("\\");

                // 3. Absoluto simples e absoluto aninhado
                Path absTarget = tempDir.resolve("deep/level/abs_study").toAbsolutePath().normalize();
                StudyOutputLayout nestedAbs = WFGStudy.resolveStudyOutputLayout(Paths.get("ignored_when_absolute"),
                                absTarget.toString());
                assertThat(nestedAbs.outputDirectory()).isEqualTo(absTarget);
                assertThat(nestedAbs.parentBaseDirectory()).isEqualTo(absTarget.getParent());
                assertThat(nestedAbs.experimentName()).isEqualTo("abs_study");

                // 4. Entradas inválidas e pastas legadas protegidas
                assertThatThrownBy(() -> WFGStudy.resolveStudyOutputLayout(tempDir, ""))
                                .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> WFGStudy.resolveStudyOutputLayout(tempDir, "   "))
                                .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> WFGStudy.resolveStudyOutputLayout(tempDir, "MOEAD_Weights/sub"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("legado protegido");
                assertThatThrownBy(() -> WFGStudy.resolveStudyOutputLayout(tempDir, "pareto_fronts/csv"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("legado protegido");
                assertThatThrownBy(() -> WFGStudy.resolveStudyOutputLayout(tempDir, "pareto/out"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("legado protegido");
        }

        @Test
        @DisplayName("T32: Pipeline completo de relatórios jMetal 7.8 funciona em study.outputDirectory aninhado absoluto (@TempDir) e relativo (WFGStudy/tests/...)")
        void testEndToEndJMetalExperimentComponentsWithNestedOutputDirectories(@TempDir Path tempDir)
                        throws IOException {
                // Caso A: Diretório absoluto profundamente aninhado em @TempDir
                Path absNested = tempDir.resolve("level1/level2/T32_abs_smoke");
                StudyOutputLayout absLayout = WFGStudy.resolveStudyOutputLayout(tempDir, absNested.toString());
                runTinyExperimentAndAssertAllOutputs(absLayout);

                // Caso B: Diretório relativo aninhado em WFGStudy/tests/T32_rel_smoke (limpo ao
                // final)
                Path workspaceRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
                StudyOutputLayout relLayout = WFGStudy.resolveStudyOutputLayout(workspaceRoot,
                                "WFGStudy/tests/T32_rel_smoke");
                try {
                        runTinyExperimentAndAssertAllOutputs(relLayout);
                } finally {
                        deleteDirectoryRecursivelyIfExists(relLayout.outputDirectory());
                }
        }

        private static void runTinyExperimentAndAssertAllOutputs(StudyOutputLayout layout)
                        throws IOException {
                Files.createDirectories(layout.outputDirectory());

                WFG1 problem = new WFG1(2, 4, 2);
                ExperimentProblem<DoubleSolution> expProblem = new ExperimentProblem<>(problem, "WFG1_2")
                                .setReferenceFront(WFGStudy.resolveReferenceFrontForStudy("WFG1", 2));
                List<ExperimentProblem<DoubleSolution>> problemList = List.of(expProblem);

                SBXCrossover crossover = new SBXCrossover(1.0, 30.0);
                PolynomialMutation mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

                List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> algorithmList = new ArrayList<>();
                for (int run = 0; run < 2; run++) {
                        Algorithm<List<DoubleSolution>> nsgaiii = new NSGAIIIBuilder<>(problem)
                                        .setCrossoverOperator(crossover)
                                        .setMutationOperator(mutation)
                                        .setSelectionOperator(
                                                        new BinaryTournamentSelection<>(
                                                                        new RankingAndCrowdingDistanceComparator<>()))
                                        .setMaxIterations(2)
                                        .setNumberOfDivisions(9)
                                        .build();
                        List<double[]> dirs = ReferencePointGenerator.generateSingleLayer(2, 9);
                        Algorithm<List<DoubleSolution>> rvea = new RVEABuilder<>(problem, dirs.size(), 20, crossover,
                                        mutation, 2.0, 0.1, dirs)
                                        .build();
                        algorithmList.add(new ExperimentAlgorithm<>(nsgaiii, "NSGAIII", expProblem, run));
                        algorithmList.add(new ExperimentAlgorithm<>(rvea, "RVEA", expProblem, run));
                }

                Experiment<DoubleSolution, List<DoubleSolution>> experiment = new ExperimentBuilder<DoubleSolution, List<DoubleSolution>>(
                                layout.experimentName())
                                .setAlgorithmList(algorithmList)
                                .setProblemList(problemList)
                                .setReferenceFrontDirectory(WFGStudy.getReferenceFrontDirectory())
                                .setExperimentBaseDirectory(layout.builderBaseDirectoryString())
                                .setOutputParetoFrontFileName("FUN")
                                .setOutputParetoSetFileName("VAR")
                                .setIndicatorList(
                                                List.of(
                                                                new InvertedGenerationalDistance(),
                                                                new InvertedGenerationalDistancePlus()))
                                .setIndependentRuns(2)
                                .setNumberOfCores(1)
                                .build();

                new ExecuteAlgorithms<>(experiment).run();
                new ComputeQualityIndicators<>(experiment).run();
                new GenerateLatexTablesWithStatistics(experiment).run();
                new GenerateWilcoxonTestTablesWithR<>(experiment).run();
                new GenerateFriedmanTestTables<>(experiment).run();
                new GenerateBoxplotsWithR<>(experiment).setRows(1).setColumns(1).setDisplayNotch().run();

                Path out = layout.outputDirectory();
                assertThat(out.resolve("data/NSGAIII/WFG1_2/FUN0.csv")).exists().isRegularFile();
                assertThat(out.resolve("data/RVEA/WFG1_2/VAR1.csv")).exists().isRegularFile();
                assertThat(out.resolve("data/NSGAIII/WFG1_2/IGD")).exists().isRegularFile();
                assertThat(out.resolve("latex/" + layout.experimentName() + ".tex"))
                                .exists()
                                .isRegularFile();
                assertThat(out.resolve("latex/FriedmanTestIGD.tex")).exists().isRegularFile();
                assertThat(out.resolve("R/IGD.Wilcoxon.R")).exists().isRegularFile();
                assertThat(Files.readString(out.resolve("R/IGD.Wilcoxon.R")))
                                .contains("IGD.Wilcoxon.tex");
                assertThat(out.resolve("R/IGD+.Wilcoxon.R")).exists().isRegularFile();
                assertThat(out.resolve("R/IGD.Boxplot.R")).exists().isRegularFile();
        }

        @Test
        @DisplayName("T32/T33: Verifica tabela completa StaticParams.getMaxGenerations após decisão A1 para DTLZ5 e DTLZ6 ({1000, 1000, 1000, 1000, 1500, 2000})")
        void testMaxGenerationsTableCharacterization() {
                int[] mValues = { 2, 3, 5, 8, 10, 15 };
                int[] expectedWfg = { 250, 400, 750, 1500, 2000, 3000 };
                int[] expectedDtlz1 = { 400, 400, 600, 750, 1000, 1500 };
                int[] expectedDtlz2 = { 250, 250, 350, 500, 750, 1000 };
                int[] expectedDtlz3 = { 1000, 1000, 1000, 1000, 1500, 2000 };
                int[] expectedDtlz4 = { 600, 600, 1000, 1250, 2000, 3000 };
                int[] expectedDtlz5And6 = { 1000, 1000, 1000, 1000, 1500, 2000 };
                int[] expectedDtlz7 = { 1000, 1000, 1000, 1000, 1500, 2000 };

                for (int idx = 0; idx < mValues.length; idx++) {
                        int m = mValues[idx];
                        for (int w = 1; w <= 9; w++) {
                                assertThat(StaticParams.getMaxGenerations(m, "WFG" + w)).isEqualTo(expectedWfg[idx]);
                                assertThat(StaticParams.getMaxGenerations(m, "WFG" + w + "_" + m))
                                                .isEqualTo(expectedWfg[idx]);
                        }
                        assertThat(StaticParams.getMaxGenerations(m, "DTLZ1")).isEqualTo(expectedDtlz1[idx]);
                        assertThat(StaticParams.getMaxGenerations(m, "DTLZ2")).isEqualTo(expectedDtlz2[idx]);
                        assertThat(StaticParams.getMaxGenerations(m, "DTLZ3")).isEqualTo(expectedDtlz3[idx]);
                        assertThat(StaticParams.getMaxGenerations(m, "DTLZ4")).isEqualTo(expectedDtlz4[idx]);
                        assertThat(StaticParams.getMaxGenerations(m, "DTLZ5")).isEqualTo(expectedDtlz5And6[idx]);
                        assertThat(StaticParams.getMaxGenerations(m, "DTLZ6")).isEqualTo(expectedDtlz5And6[idx]);
                        assertThat(StaticParams.getMaxGenerations(m, "DTLZ7")).isEqualTo(expectedDtlz7[idx]);
                }
        }

        private static void deleteDirectoryRecursivelyIfExists(Path dir) throws IOException {
                if (dir == null || !Files.exists(dir)) {
                        return;
                }
                try (Stream<Path> walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder())
                                        .map(Path::toFile)
                                        .forEach(File::delete);
                }
        }
}

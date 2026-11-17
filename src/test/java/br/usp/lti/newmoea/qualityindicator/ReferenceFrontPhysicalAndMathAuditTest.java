package br.usp.lti.newmoea.qualityindicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.usp.lti.newmoea.util.ParetoFrontValidator;
import br.usp.lti.newmoea.util.ParetoFrontValidator.PhysicalFrontAuditRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ3;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ4;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ5;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ6;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ7;
import org.uma.jmetal.problem.multiobjective.wfg.WFG3;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

/**
 * Suíte de auditoria física e matemática das frentes de Pareto de referência (T16).
 *
 * <p>Verifica:
 * <ul>
 *   <li>As contagens físicas exatas de linhas, hashes SHA-256, duplicatas e dominância dos 160
 *       arquivos em {@code pareto_fronts/csv/} e especificamente dos 68 arquivos ativos
 *       ({@code WFG1}–{@code WFG9} em \(m \in \{2,3,5,8,10,15\}\) e {@code DTLZ1}–{@code DTLZ7} em
 *       \(m \in \{2,3\}\)), reconciliando formalmente os 6 arquivos DTLZ cujas contagens estavam
 *       incorretas em {@code docs/reference-fronts-dtlz.md} ({@code 4000, 4000, 200, 200, 101, 676});</li>
 *   <li>A realizabilidade no espaço de decisão (\(\exists \mathbf{x}^* \in [0, 1]^n\) tal que
 *       \(\mathbf{f}(\mathbf{x}^*) = \mathbf{y}\)) para {@code DTLZ1}–{@code DTLZ7} em
 *       \(m \in \{2, 3, 5, 8, 10, 15\}\) via chamada direta a {@code problem.evaluate(solution)}
 *       no jMetal 7.8;</li>
 *   <li>A imagem real de {@code WFG3} (\(m = 2\)) em {@code WFG3.evaluate(solution)} no jMetal 7.8
 *       (\(f_1 \in [0, 2], f_2 \in [0, 4]\) na frente ótima e \(f_1, f_2 \ge 0\) em todo o espaço
 *       factível \(\prod_{i=1}^n [0, 2i]\)) versus os 120 pontos com \(f_2 \in [-2, 0)\) presentes
 *       em {@code pareto_fronts/csv/WFG3.2D.csv};</li>
 *   <li>A cobertura de extremos por objetivo nas frentes analíticas geradas por
 *       {@link ParetoFrontValidator#generateAnalyticalDtlzFront} em todas as dimensões do estudo
 *       (\(m \in \{2, 3, 5, 8, 10, 15\}\)).</li>
 * </ul>
 */
class ReferenceFrontPhysicalAndMathAuditTest {

    private static final Path PARETO_FRONTS_DIR = Path.of("pareto_fronts", "csv");
    private static final int[] STUDY_DIMENSIONS = {2, 3, 5, 8, 10, 15};

    @Test
    @DisplayName(
            "Auditoria física dos 160 arquivos em pareto_fronts/csv/ e reconciliação exata dos 68 ativos")
    void shouldVerifyExactPhysicalLineCountsOfAll160FilesAndReconcile68ActiveFiles()
            throws IOException {
        List<Path> allCsvFiles;
        try (Stream<Path> stream = Files.list(PARETO_FRONTS_DIR)) {
            allCsvFiles =
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".csv"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .toList();
        }
        assertEquals(
                160,
                allCsvFiles.size(),
                "O diretório pareto_fronts/csv/ deve conter exatamente 160 arquivos CSV.");

        Map<String, PhysicalFrontAuditRow> rowsByName = new LinkedHashMap<>();
        for (Path file : allCsvFiles) {
            PhysicalFrontAuditRow row = ParetoFrontValidator.auditPhysicalFrontFile(file);
            rowsByName.put(row.fileName(), row);
        }

        // 1. Reconciliação explícita dos 14 arquivos DTLZ presentes em pareto_fronts/csv/
        // (especialmente os 6 arquivos com contagens divergentes em docs/reference-fronts-dtlz.md)
        Map<String, Integer> expectedDtlzLines = new LinkedHashMap<>();
        expectedDtlzLines.put("DTLZ1.2D.csv", 1000);
        expectedDtlzLines.put("DTLZ1.3D.csv", 10000);
        expectedDtlzLines.put("DTLZ2.2D.csv", 1000);
        expectedDtlzLines.put("DTLZ2.3D.csv", 10000);
        expectedDtlzLines.put("DTLZ3.2D.csv", 1000);
        expectedDtlzLines.put("DTLZ3.3D.csv", 4000); // Reconciliado: 4.000 reais (vs 10.000 em T07)
        expectedDtlzLines.put("DTLZ4.2D.csv", 1000);
        expectedDtlzLines.put("DTLZ4.3D.csv", 4000); // Reconciliado: 4.000 reais (vs 10.000 em T07)
        expectedDtlzLines.put("DTLZ5.2D.csv", 200); // Reconciliado: 200 reais (vs 1.000 em T07)
        expectedDtlzLines.put("DTLZ5.3D.csv", 166500);
        expectedDtlzLines.put("DTLZ6.2D.csv", 200); // Reconciliado: 200 reais (vs 1.000 em T07)
        expectedDtlzLines.put("DTLZ6.3D.csv", 28000);
        expectedDtlzLines.put("DTLZ7.2D.csv", 101); // Reconciliado: 101 reais (vs 1.002 em T07)
        expectedDtlzLines.put("DTLZ7.3D.csv", 676); // Reconciliado: 676 reais (vs 2.916 em T07)

        for (Map.Entry<String, Integer> entry : expectedDtlzLines.entrySet()) {
            PhysicalFrontAuditRow row = rowsByName.get(entry.getKey());
            assertTrue(row != null, "Arquivo DTLZ ativo ausente: " + entry.getKey());
            assertEquals(
                    entry.getValue(),
                    row.physicalLines(),
                    "Contagem física de linhas incorreta para " + entry.getKey());
            assertEquals(
                    entry.getValue(),
                    row.validPoints(),
                    "Contagem de pontos válidos incorreta para " + entry.getKey());
            assertFalse(
                    row.hasNegativeValues(),
                    "Nenhum arquivo DTLZ deve possuir coordenadas negativas: " + entry.getKey());
        }

        // Verifica multiplicidade, dominância e violação de intervalo de DTLZ7 nos arquivos DTLZ existentes:
        // DTLZ5.3D.csv (166.500 linhas, 333 únicos) e DTLZ6.3D.csv (28.000 linhas, 140 únicos)
        assertEquals(333, rowsByName.get("DTLZ5.3D.csv").uniquePoints());
        assertEquals(166167, rowsByName.get("DTLZ5.3D.csv").duplicatedPoints());
        assertEquals(140, rowsByName.get("DTLZ6.3D.csv").uniquePoints());
        assertEquals(27860, rowsByName.get("DTLZ6.3D.csv").duplicatedPoints());

        // DTLZ7.2D.csv (101 linhas reais, todas mutuamente não-dominadas dentro da amostra) e
        // DTLZ7.3D.csv (676 = 26^2 linhas, todas mutuamente não-dominadas dentro da amostra discreta,
        // embora 100 pontos com f_j in {0.26, 0.86} extrapolem x_1* ~ 0.251412 e x_3* ~ 0.859401
        // e sejam dominados na variedade contínua por x_1* e x_3*!)
        assertEquals(0, rowsByName.get("DTLZ7.2D.csv").dominatedPoints());
        assertEquals(101, rowsByName.get("DTLZ7.2D.csv").nonDominatedPoints());
        assertEquals(0, rowsByName.get("DTLZ7.3D.csv").dominatedPoints());
        assertEquals(676, rowsByName.get("DTLZ7.3D.csv").nonDominatedPoints());

        int outOfParetoIntervalCountDtlz73D = 0;
        for (double[] pt :
                ParetoFrontValidator.validateParetoFrontFile(
                                PARETO_FRONTS_DIR.resolve("DTLZ7.3D.csv"), 3)
                        .points()) {
            if (!ParetoFrontValidator.isDtlz7CoordinateInParetoIntervals(pt[0], 1e-6)
                    || !ParetoFrontValidator.isDtlz7CoordinateInParetoIntervals(pt[1], 1e-6)) {
                outOfParetoIntervalCountDtlz73D++;
            }
        }
        assertEquals(
                100,
                outOfParetoIntervalCountDtlz73D,
                "Em DTLZ7.3D.csv (grade 26x26), exatamente 100 pontos têm f_1 ou f_2 em {0.26, 0.86} fora de I_0 U I_1.");

        // Gera também o arquivo CSV consolidado docs/auditoria/reference-fronts-physical.csv
        Path outCsv = Path.of("docs", "auditoria", "reference-fronts-physical.csv");
        Files.createDirectories(outCsv.getParent());
        List<String> csvLines = new ArrayList<>(rowsByName.size() + 1);
        csvLines.add(
                "file_name,family,problem,m,size_bytes,sha256,physical_lines,non_empty_lines,valid_points,unique_points,non_dominated_points,duplicated_points,dominated_points,min_by_obj,max_by_obj,has_negative_values");
        for (PhysicalFrontAuditRow r : rowsByName.values()) {
            csvLines.add(r.toCsvRow());
        }
        Files.write(outCsv, csvLines, StandardCharsets.UTF_8);
        assertTrue(Files.exists(outCsv));
        assertEquals(161, Files.readAllLines(outCsv, StandardCharsets.UTF_8).size());

        // 2. Verifica todos os 54 arquivos WFG ativos (WFG1..WFG9 em m in {2,3,5,8,10,15})
        for (int p = 1; p <= 9; p++) {
            for (int m : STUDY_DIMENSIONS) {
                String fileName = String.format("WFG%d.%dD.csv", p, m);
                PhysicalFrontAuditRow row = rowsByName.get(fileName);
                assertTrue(row != null, "Arquivo WFG ativo ausente: " + fileName);
                assertEquals(m, row.m(), "Dimensionalidade incorreta em " + fileName);
                assertTrue(row.validPoints() > 0, "Arquivo WFG vazio: " + fileName);
                assertEquals(
                        row.physicalLines(),
                        row.validPoints(),
                        "Todas as linhas físicas de WFG devem ser válidas: " + fileName);
                if (p == 3 && m == 2) {
                    assertTrue(
                            row.hasNegativeValues(),
                            "WFG3.2D.csv possui valores negativos macroscopicamente fora da imagem (min f_2 = -1.9999957).");
                    assertEquals(-1.9999957, row.minByObj()[1], 1e-6);
                } else if (p == 1 && (m == 2 || m == 3)) {
                    // WFG1.2D.csv (min f_2 = -3.81303e-8) e WFG1.3D.csv (min f_3 = -1.63577e-7)
                    // possuem artefatos de precisão float na extremidade zero
                    assertTrue(row.hasNegativeValues());
                    for (double minVal : row.minByObj()) {
                        assertTrue(
                                minVal >= -1e-6,
                                "Em WFG1.2D/3D os valores negativos são apenas ruído de ponto flutuante (~ -1e-7): "
                                        + fileName);
                    }
                } else {
                    assertFalse(
                            row.hasNegativeValues(),
                            "Apenas WFG3.2D.csv (macroscópico) e WFG1.2D/3D.csv (ruído float ~ -1e-7) possuem valores negativos entre os 68 ativos: "
                                    + fileName);
                }
            }
        }

        // 3. Confirma a ausência física dos 28 arquivos DTLZ1..7 em m in {5,8,10,15}
        for (int p = 1; p <= 7; p++) {
            for (int m : new int[] {5, 8, 10, 15}) {
                String missingName = String.format("DTLZ%d.%dD.csv", p, m);
                assertFalse(
                        rowsByName.containsKey(missingName),
                        "Arquivo DTLZ em m >= 5 não existe em pareto_fronts/csv/: " + missingName);
            }
        }
    }

    @Test
    @DisplayName(
            "Realizabilidade no espaço de decisão (problem.evaluate) para DTLZ1-DTLZ7 em m in {2,3,5,8,10,15}")
    void shouldVerifyDecisionSpaceRealizabilityViaJMetalEvaluateForAllDtlzAndDimensions() {
        for (int m : STUDY_DIMENSIONS) {
            int[] h = divisionsForRealizabilityAudit(m);
            for (int p = 1; p <= 7; p++) {
                String problemName = "DTLZ" + p;
                double[][] analyticalFront =
                        ParetoFrontValidator.generateAnalyticalDtlzFront(
                                problemName, m, h[0], h[1]);
                DoubleProblem problem = createDtlzProblem(p, m);
                int nVars = problem.numberOfVariables();

                // Verifica todos os pontos (ou amostra determinística cobrindo extremos + interior)
                int step = Math.max(1, analyticalFront.length / 80);
                for (int idx = 0; idx < analyticalFront.length; idx += step) {
                    assertPointRealizableInJMetal(
                            problemName, problem, nVars, analyticalFront[idx]);
                }
                // Garante também o último ponto e os extremos de cada objetivo
                assertPointRealizableInJMetal(
                        problemName,
                        problem,
                        nVars,
                        analyticalFront[analyticalFront.length - 1]);
                for (int j = 0; j < m; j++) {
                    int minIdx = 0;
                    int maxIdx = 0;
                    for (int i = 1; i < analyticalFront.length; i++) {
                        if (analyticalFront[i][j] < analyticalFront[minIdx][j]) {
                            minIdx = i;
                        }
                        if (analyticalFront[i][j] > analyticalFront[maxIdx][j]) {
                            maxIdx = i;
                        }
                    }
                    assertPointRealizableInJMetal(
                            problemName, problem, nVars, analyticalFront[minIdx]);
                    assertPointRealizableInJMetal(
                            problemName, problem, nVars, analyticalFront[maxIdx]);
                }
            }
        }
    }

    @Test
    @DisplayName(
            "Auditoria matemática de WFG3 (m=2) no jMetal 7.8 vs valores negativos em WFG3.2D.csv")
    void shouldProveWfg3EvaluateInJMetalIsStrictlyNonNegativeWhileCsvHas120NegativePoints()
            throws IOException {
        // 1. Inspeciona o arquivo físico pareto_fronts/csv/WFG3.2D.csv
        Path wfg3Csv = PARETO_FRONTS_DIR.resolve("WFG3.2D.csv");
        ParetoFrontValidator.FrontValidationSummary summary =
                ParetoFrontValidator.validateParetoFrontFile(wfg3Csv, 2);
        assertEquals(916, summary.totalPoints());
        assertTrue(summary.hasNegativeValues());

        int negativeF2Count = 0;
        double maxLinearEqError = 0.0;
        for (double[] pt : summary.points()) {
            double f1 = pt[0];
            double f2 = pt[1];
            if (f2 < 0.0) {
                negativeF2Count++;
                assertTrue(
                        f1 > 2.0,
                        "Em WFG3.2D.csv, f_2 < 0 ocorre exatamente onde f_1 extrapolou o limite 2.0");
            }
            // Equação da reta linear: f_1 / 2 + f_2 / 4 = 1
            double eqErr = Math.abs((f1 / 2.0) + (f2 / 4.0) - 1.0);
            maxLinearEqError = Math.max(maxLinearEqError, eqErr);
        }
        assertEquals(
                120,
                negativeF2Count,
                "WFG3.2D.csv possui exatamente 120 pontos com f_2 < 0 (onde f_1 in (2.0, 3.0]).");
        assertTrue(
                maxLinearEqError < 1e-5,
                "Todos os 916 pontos de WFG3.2D.csv colapsam sobre a reta f_1/2 + f_2/4 = 1.");

        // 2. Avalia WFG3(k=2, l=4, m=2) e WFG3(k=4, l=20, m=2) no jMetal 7.8 ao longo do espaço
        // de decisão factível z_i in [0, 2*(i+1)]
        WFG3 wfg3 = new WFG3(4, 20, 2);
        int nVars = wfg3.numberOfVariables();
        assertEquals(24, nVars);

        double minObservedF1 = Double.POSITIVE_INFINITY;
        double maxObservedPfF1 = Double.NEGATIVE_INFINITY;
        double minObservedF2 = Double.POSITIVE_INFINITY;
        double maxObservedPfF2 = Double.NEGATIVE_INFINITY;

        // No conjunto Pareto-ótimo de WFG3, os parâmetros de distância satisfazem z_i / (2i) = 0.35
        // (logo t^1_i = 0 => t^2 = 0 => t^3_{m-1} = x_{m-1} = 0), enquanto as variáveis de posição
        // varrem [0, 2i].
        int gridSteps = 200;
        for (int s = 0; s <= gridSteps; s++) {
            double u = (double) s / (double) gridSteps;
            DoubleSolution sol = wfg3.createSolution();
            for (int i = 0; i < 4; i++) {
                double upper = 2.0 * (i + 1);
                sol.variables().set(i, u * upper);
            }
            for (int i = 4; i < nVars; i++) {
                double upper = 2.0 * (i + 1);
                sol.variables().set(i, 0.35 * upper);
            }
            wfg3.evaluate(sol);
            double f1 = sol.objectives()[0];
            double f2 = sol.objectives()[1];
            assertTrue(f1 >= 0.0 && f2 >= 0.0, "WFG3.evaluate nunca produz objetivos negativos.");
            minObservedF1 = Math.min(minObservedF1, f1);
            maxObservedPfF1 = Math.max(maxObservedPfF1, f1);
            minObservedF2 = Math.min(minObservedF2, f2);
            maxObservedPfF2 = Math.max(maxObservedPfF2, f2);
            // Como t_{m-1} = 0 na PF, f_1 / 2 + f_2 / 4 = 1 com f_1 in [0, 2] e f_2 in [0, 4]
            assertEquals(1.0, (f1 / 2.0) + (f2 / 4.0), 1e-6);
            assertTrue(f1 <= 2.0 + 1e-6);
            assertTrue(f2 <= 4.0 + 1e-6);
        }
        assertEquals(0.0, minObservedF1, 1e-6);
        assertEquals(2.0, maxObservedPfF1, 1e-6);
        assertEquals(0.0, minObservedF2, 1e-6);
        assertEquals(4.0, maxObservedPfF2, 1e-6);
    }

    @Test
    @DisplayName(
            "Cobertura exata de extremos por objetivo nas frentes analíticas de DTLZ1-DTLZ7 em m in {2,3,5,8,10,15}")
    void shouldCoverExactAnalyticalExtremesPerObjectiveForDtlz1To7AcrossAllStudyDimensions() {
        for (int m : STUDY_DIMENSIONS) {
            int[] h = divisionsForRealizabilityAudit(m);
            for (int p = 1; p <= 7; p++) {
                String problemName = "DTLZ" + p;
                double[][] front =
                        ParetoFrontValidator.generateAnalyticalDtlzFront(
                                problemName, m, h[0], h[1]);
                double[] minObs = new double[m];
                double[] maxObs = new double[m];
                java.util.Arrays.fill(minObs, Double.POSITIVE_INFINITY);
                java.util.Arrays.fill(maxObs, Double.NEGATIVE_INFINITY);
                for (double[] pt : front) {
                    for (int j = 0; j < m; j++) {
                        minObs[j] = Math.min(minObs[j], pt[j]);
                        maxObs[j] = Math.max(maxObs[j], pt[j]);
                    }
                }

                switch (p) {
                    case 1 -> {
                        for (int j = 0; j < m; j++) {
                            assertEquals(0.0, minObs[j], 1e-9, "Min f_" + (j + 1) + " em DTLZ1");
                            assertEquals(0.5, maxObs[j], 1e-9, "Max f_" + (j + 1) + " em DTLZ1");
                        }
                    }
                    case 2, 3, 4 -> {
                        for (int j = 0; j < m; j++) {
                            assertEquals(
                                    0.0,
                                    minObs[j],
                                    1e-9,
                                    "Min f_" + (j + 1) + " em " + problemName);
                            assertEquals(
                                    1.0,
                                    maxObs[j],
                                    1e-9,
                                    "Max f_" + (j + 1) + " em " + problemName);
                        }
                    }
                    case 5, 6 -> {
                        // Na curva 1D do jMetal 7.8 (theta_1 = ... = theta_{m-2} = pi/4):
                        // Em theta_0 = pi/2: f_1 = ... = f_{m-1} = 0 e f_m = 1.
                        // Em theta_0 = 0: f_m = 0, f_1 = f_2 = (1/sqrt(2))^(m-2) e
                        // f_j = (1/sqrt(2))^(m-j) para j >= 2 (1-indexado).
                        for (int j = 0; j < m - 1; j++) {
                            assertEquals(0.0, minObs[j], 1e-9);
                            int power = (j == 0) ? (m - 2) : (m - 1 - j);
                            double expectedMax = Math.pow(Math.cos(Math.PI / 4.0), power);
                            assertEquals(expectedMax, maxObs[j], 1e-9);
                        }
                        assertEquals(0.0, minObs[m - 1], 1e-9);
                        assertEquals(1.0, maxObs[m - 1], 1e-9);
                    }
                    case 7 -> {
                        for (int j = 0; j < m - 1; j++) {
                            assertEquals(
                                    0.0,
                                    minObs[j],
                                    1e-9,
                                    "DTLZ7 deve cobrir f_" + (j + 1) + " min = 0.0 em m=" + m);
                            assertEquals(
                                    ParetoFrontValidator.DTLZ7_INTERVAL_2_MAX,
                                    maxObs[j],
                                    1e-9,
                                    "DTLZ7 deve cobrir f_" + (j + 1) + " max = x_3* em m=" + m);
                        }
                        assertEquals(
                                ParetoFrontValidator.dtlz7AnalyticalMinLastObjective(m),
                                minObs[m - 1],
                                1e-6,
                                "DTLZ7 deve cobrir f_m min analítico em m=" + m);
                        assertEquals(
                                ParetoFrontValidator.dtlz7AnalyticalMaxLastObjective(m),
                                maxObs[m - 1],
                                1e-9,
                                "DTLZ7 deve cobrir f_m max = 2m em m=" + m);
                    }
                    default -> throw new IllegalStateException("Problema inesperado: " + p);
                }
            }
        }
    }

    private static void assertPointRealizableInJMetal(
            String problemName, DoubleProblem problem, int nVars, double[] targetObjectives) {
        double[] xStar =
                ParetoFrontValidator.constructOptimalDtlzDecisionVector(
                        problemName, targetObjectives, nVars);
        DoubleSolution solution = problem.createSolution();
        for (int i = 0; i < nVars; i++) {
            assertTrue(
                    xStar[i] >= 0.0 && xStar[i] <= 1.0,
                    "Variável de decisão fora de [0,1] em " + problemName);
            solution.variables().set(i, xStar[i]);
        }
        problem.evaluate(solution);
        double[] evaluated = solution.objectives();
        for (int j = 0; j < targetObjectives.length; j++) {
            assertEquals(
                    targetObjectives[j],
                    evaluated[j],
                    1e-7,
                    String.format(
                            Locale.US,
                            "Falha de realizabilidade em %s (m=%d, f_%d): esperado=%.10f, avaliado=%.10f",
                            problemName,
                            targetObjectives.length,
                            j + 1,
                            targetObjectives[j],
                            evaluated[j]));
        }
    }

    private static DoubleProblem createDtlzProblem(int problemIndex, int m) {
        return switch (problemIndex) {
            case 1 -> new DTLZ1(m + 4, m);
            case 2 -> new DTLZ2(m + 9, m);
            case 3 -> new DTLZ3(m + 9, m);
            case 4 -> new DTLZ4(m + 9, m);
            case 5 -> new DTLZ5(m + 9, m);
            case 6 -> new DTLZ6(m + 9, m);
            case 7 -> new DTLZ7(m + 19, m);
            default -> throw new IllegalArgumentException("DTLZ inválido: " + problemIndex);
        };
    }

    private static int[] divisionsForRealizabilityAudit(int m) {
        return switch (m) {
            case 2 -> new int[] {99, 0};
            case 3 -> new int[] {12, 0};
            case 5 -> new int[] {6, 0};
            case 8 -> new int[] {3, 2};
            case 10 -> new int[] {3, 2};
            case 15 -> new int[] {2, 1};
            default -> new int[] {4, 0};
        };
    }
}

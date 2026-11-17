package br.usp.lti.newmoea.study;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.usp.lti.newmoea.algorithm.fdea.FDEA;
import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder;
import br.usp.lti.newmoea.util.EvaluationBudgetProblem;
import br.usp.lti.newmoea.util.IsolatedJobExecutor;
import br.usp.lti.newmoea.util.ResultFrontContract;
import br.usp.lti.newmoea.util.ResultFrontContract.CanonicalResultExtraction;
import br.usp.lti.newmoea.util.ResultFrontContract.FunVarAlignmentReport;
import br.usp.lti.newmoea.util.ResultFrontContract.IndicatorSensitivityReport;
import br.usp.lti.newmoea.util.ResultFrontContract.PopulationAuditReport;
import br.usp.lti.newmoea.util.ResultFrontContract.WrittenResultFilesReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEA;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAII;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD;
import org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.component.algorithm.EvolutionaryAlgorithm;
import org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.problem.multiobjective.wfg.WFG2;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.ConstraintHandling;
import org.uma.jmetal.util.VectorUtils;
import org.uma.jmetal.util.pseudorandom.JMetalRandom;

/**
 * Testes de contrato da população de saída ({@code algorithm.result()}), filtragem canônica
 * não-dominada sincronizada e alinhamento 1:1 de {@code FUN{run}.csv} e {@code VAR{run}.csv}
 * (Tarefa T19 / G1-3 / R9 / R16).
 */
class ResultFrontContractTest {

    @TempDir
    Path tempDir;

    /**
     * Audita o comportamento real de {@code algorithm.result()} nos 6 algoritmos principais
     * ({@code NSGA-III}, {@code MOEA/DD}, {@code FDEA}, {@code RVEA}, {@code AGE-MOEA},
     * {@code AGE-MOEA-II}) em {@code WFG1} ({@code m=3}) e verifica que a extração canônica
     * produz um conjunto 100% não-dominado, sem duplicatas e com alinhamento 1:1 FUN/VAR.
     */
    @Test
    void shouldAuditAllSixMainAlgorithmsAndFilterDominatedSolutionsFromMoeaddAndRvea() throws IOException {
        int m = 3;
        int k = 4;
        int l = 20;
        int popSize = 91; // H(3, 12) = C(14, 2) = 91
        int maxIterations = 12;
        int maxEvaluations = popSize * maxIterations;

        System.out.println("=== [T19] AUDITORIA REAL DE algorithm.result() EM WFG1 (m=3, N=91, budget="
                + maxEvaluations + ") ===");

        // 1. NSGA-III
        WFG1 pNsga3 = new WFG1(k, l, m);
        JMetalRandom.getInstance().setSeed(19001L);
        NSGAIII<DoubleSolution> nsga3 = new NSGAIIIBuilder<>(pNsga3)
                .setCrossoverOperator(new SBXCrossover(1.0, 30.0))
                .setMutationOperator(new PolynomialMutation(1.0 / pNsga3.numberOfVariables(), 20.0))
                .setSelectionOperator(new BinaryTournamentSelection<>())
                .setMaxIterations(maxIterations)
                .setNumberOfDivisions(12)
                .build();
        assertAndLogAlgorithmResult("NSGA-III", nsga3, pNsga3, 0);

        // 2. MOEA/DD (retorna this.population inteira sem filtrar soluções dominadas!)
        WFG1 pMoeadd = new WFG1(k, l, m);
        JMetalRandom.getInstance().setSeed(19001L);
        AbstractMOEAD<DoubleSolution> moeadd = new MOEADBuilder(pMoeadd, MOEADBuilder.Variant.MOEADD)
                .setCrossover(new SBXCrossover(1.0, 30.0))
                .setMutation(new PolynomialMutation(1.0 / pMoeadd.numberOfVariables(), 20.0))
                .setMaxEvaluations(maxEvaluations)
                .setPopulationSize(popSize)
                .setResultPopulationSize(popSize)
                .setNeighborhoodSelectionProbability(0.9)
                .setMaximumNumberOfReplacedSolutions(1)
                .setNeighborSize(20)
                .setFunctionType(AbstractMOEAD.FunctionType.PBI)
                .setDataDirectory("MOEAD_Weights")
                .build();
        CanonicalResultExtraction moeaddExt = assertAndLogAlgorithmResult("MOEA/DD", moeadd, pMoeadd, 1);
        assertEquals(
                popSize,
                moeaddExt.auditReport().rawSize(),
                "MOEA/DD.result() retorna this.population inteira (N_config = 91)");
        assertTrue(
                moeaddExt.auditReport().dominatedCount() > 0,
                "MOEA/DD.result() deve conter soluções dominadas na população bruta (F_1, F_2, ...)");

        // 3. FDEA
        WFG1 pFdea = new WFG1(k, l, m);
        JMetalRandom.getInstance().setSeed(19001L);
        Algorithm<List<DoubleSolution>> fdea = new FDEABuilder(pFdea)
                .setPopulationSize(popSize)
                .setMaxEvaluations(maxEvaluations)
                .setCrossoverOperator(new SBXCrossover(1.0, 20.0))
                .setMutationOperator(new PolynomialMutation(1.0 / pFdea.numberOfVariables(), 20.0))
                .setSelectionOperator(new BinaryTournamentSelection<>())
                .setVersion(FDEA.VERSION.I)
                .build();
        CanonicalResultExtraction fdeaExt = assertAndLogAlgorithmResult("FDEA", fdea, pFdea, 2);
        assertEquals(
                0,
                fdeaExt.auditReport().dominatedCount(),
                "FDEA.result() já retorna getSubFront(0) sem soluções dominadas");

        // 4. RVEA (retorna this.population selecionada por APD sem filtrar dominadas entre cones!)
        WFG1 pRvea = new WFG1(k, l, m);
        EvaluationBudgetProblem rveaBudgetProblem =
                new EvaluationBudgetProblem(pRvea, maxEvaluations, false);
        JMetalRandom.getInstance().setSeed(19001L);
        EvolutionaryAlgorithm<DoubleSolution> rvea = new RVEABuilder(
                        rveaBudgetProblem,
                        popSize,
                        maxEvaluations,
                        new SBXCrossover(1.0, 30.0),
                        new PolynomialMutation(1.0 / pRvea.numberOfVariables(), 20.0),
                        2.0,
                        0.1,
                        12)
                .build();
        CanonicalResultExtraction rveaExt = assertAndLogAlgorithmResult("RVEA", rvea, pRvea, 3);
        assertTrue(
                rveaExt.auditReport().dominatedCount() > 0,
                "RVEA.result() retorna seleção por APD que retém soluções mutuamente dominadas entre cones");

        // 5. AGE-MOEA (exige tamanho par devido ao SBXCrossover em AbstractGeneticAlgorithm)
        int agePopSize = (popSize % 2 != 0) ? (popSize + 1) : popSize;
        WFG1 pAge1 = new WFG1(k, l, m);
        JMetalRandom.getInstance().setSeed(19001L);
        AGEMOEA<DoubleSolution> age1 = (AGEMOEA<DoubleSolution>) new AGEMOEABuilder<>(pAge1)
                .setCrossoverOperator(new SBXCrossover(0.9, 30.0))
                .setMutationOperator(new PolynomialMutation(1.0 / pAge1.numberOfVariables(), 20.0))
                .setMaxIterations(maxIterations)
                .setPopulationSize(agePopSize)
                .build();
        CanonicalResultExtraction age1Ext = assertAndLogAlgorithmResult("AGE-MOEA", age1, pAge1, 4);
        assertEquals(
                0,
                age1Ext.auditReport().dominatedCount(),
                "AGEMOEA.result() chama SolutionListUtils.getNonDominatedSolutions");

        // 6. AGE-MOEA-II
        WFG1 pAge2 = new WFG1(k, l, m);
        JMetalRandom.getInstance().setSeed(19001L);
        AGEMOEAII<DoubleSolution> age2 = (AGEMOEAII<DoubleSolution>) new AGEMOEAIIBuilder<>(pAge2)
                .setCrossoverOperator(new SBXCrossover(0.9, 30.0))
                .setMutationOperator(new PolynomialMutation(1.0 / pAge2.numberOfVariables(), 20.0))
                .setMaxIterations(maxIterations)
                .setPopulationSize(agePopSize)
                .build();
        CanonicalResultExtraction age2Ext = assertAndLogAlgorithmResult("AGE-MOEA-II", age2, pAge2, 5);
        assertEquals(
                0,
                age2Ext.auditReport().dominatedCount(),
                "AGEMOEAII.result() herda AGEMOEA.result() (SolutionListUtils.getNonDominatedSolutions)");
    }

    private CanonicalResultExtraction assertAndLogAlgorithmResult(
            String algName,
            Algorithm<List<DoubleSolution>> algorithm,
            Problem<DoubleSolution> problem,
            int runId) throws IOException {
        algorithm.run();
        List<DoubleSolution> rawResult = algorithm.result();
        assertNotNull(rawResult);
        assertFalse(rawResult.isEmpty());

        CanonicalResultExtraction extraction = ResultFrontContract.extractCanonicalResultSet(
                rawResult, problem, ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);
        PopulationAuditReport audit = extraction.auditReport();

        System.out.printf(
                "[T19] %-12s | rawSize=%3d | validFeasible=%3d | nonDominated=%3d | dominated=%3d | "
                        + "exactObjDup=%2d | tolObjDup=%2d | canonicalUnique=%3d%n",
                algName,
                audit.rawSize(),
                audit.validFeasibleCount(),
                audit.nonDominatedCount(),
                audit.dominatedCount(),
                audit.exactObjectiveDuplicateCount(),
                audit.toleranceObjectiveDuplicateCount(),
                audit.uniqueCanonicalCount());

        // Verifica que o conjunto canônico extraído é 100% limpo e mutuamente não-dominado
        PopulationAuditReport canonicalReaudit = ResultFrontContract.auditPopulation(
                extraction.canonicalSolutions(),
                problem,
                ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);
        assertTrue(canonicalReaudit.isCleanNonDominatedSet());
        assertEquals(0, canonicalReaudit.dominatedCount());
        assertEquals(0, canonicalReaudit.toleranceObjectiveDuplicateCount());
        assertEquals(audit.uniqueCanonicalCount(), canonicalReaudit.rawSize());

        // Grava FUN{run}.csv, VAR{run}.csv, FUN{run}.raw.csv e VAR{run}.raw.csv e verifica alinhamento 1:1
        Path algDir = tempDir.resolve(algName.replace("/", ""));
        Path funFile = algDir.resolve("FUN" + runId + ".csv");
        Path varFile = algDir.resolve("VAR" + runId + ".csv");

        WrittenResultFilesReport written = ResultFrontContract.writeCanonicalAndRawFiles(
                extraction, funFile, varFile, true);
        assertTrue(Files.exists(written.funFile()));
        assertTrue(Files.exists(written.varFile()));
        assertTrue(Files.exists(written.rawFunFile()));
        assertTrue(Files.exists(written.rawVarFile()));

        FunVarAlignmentReport canonicalAlign = ResultFrontContract.verifyFunVarAlignment(
                funFile, varFile, problem, ResultFrontContract.DEFAULT_ALIGNMENT_TOLERANCE);
        assertTrue(canonicalAlign.isAlignedAndValid(), canonicalAlign.diagnosticMessage());
        assertEquals(audit.uniqueCanonicalCount(), canonicalAlign.funRowCount());
        assertEquals(0.0, canonicalAlign.maxObjectiveDiscrepancy(), 1e-12);

        FunVarAlignmentReport rawAlign = ResultFrontContract.verifyFunVarAlignment(
                written.rawFunFile(),
                written.rawVarFile(),
                problem,
                ResultFrontContract.DEFAULT_ALIGNMENT_TOLERANCE);
        assertTrue(rawAlign.isAlignedAndValid(), rawAlign.diagnosticMessage());
        assertEquals(audit.rawSize(), rawAlign.funRowCount());

        return extraction;
    }

    /**
     * Demonstra matematicamente e numericamente (contraexemplo 2D concreto) que uma solução
     * estritamente dominada deixada em {@code FUN{run}.csv} altera o {@code IGD} euclidiano padrão
     * (tanto {@code pow=2.0} do jMetal 7.8 quanto {@code pow=1.0} clássico), enquanto {@code HV},
     * {@code NHV}, {@code IGD+} e {@code R2} permanecem 100% invariantes ({@code delta == 0.0}).
     */
    @Test
    void shouldProve2DCounterexampleWhereDominatedSolutionChangesEuclideanIgdButNotParetoCompliantMetrics() {
        IndicatorSensitivityReport report = ResultFrontContract.evaluateCanonical2DCounterexample();

        System.out.printf(
                "[T19-Counterexample2D] HV:   nd=%.12f | raw=%.12f | delta=%.12e%n",
                report.hvNonDominated(), report.hvWithDominated(), report.hvDelta());
        System.out.printf(
                "[T19-Counterexample2D] NHV:  nd=%.12f | raw=%.12f | delta=%.12e%n",
                report.nhvNonDominated(), report.nhvWithDominated(), report.nhvDelta());
        System.out.printf(
                "[T19-Counterexample2D] IGD+: nd=%.12f | raw=%.12f | delta=%.12e%n",
                report.igdPlusNonDominated(), report.igdPlusWithDominated(), report.igdPlusDelta());
        System.out.printf(
                "[T19-Counterexample2D] R2:   nd=%.12f | raw=%.12f | delta=%.12e%n",
                report.r2NonDominated(), report.r2WithDominated(), report.r2Delta());
        System.out.printf(
                "[T19-Counterexample2D] IGD (jMetal pow=2): nd=%.12f | raw=%.12f | delta=%.12e%n",
                report.igdJmetalPow2NonDominated(),
                report.igdJmetalPow2WithDominated(),
                report.igdJmetalPow2Delta());
        System.out.printf(
                "[T19-Counterexample2D] IGD (classic pow=1): nd=%.12f | raw=%.12f | delta=%.12e%n",
                report.igdClassicalPow1NonDominated(),
                report.igdClassicalPow1WithDominated(),
                report.igdClassicalPow1Delta());

        // 1. Invariância exata de HV, NHV, IGD+ e R2 à presença da solução dominada (0.5, 0.95)
        assertEquals(0.0, report.hvDelta(), 1e-15, "HV deve ser 100% invariante a soluções dominadas");
        assertEquals(0.0, report.nhvDelta(), 1e-15, "NHV deve ser 100% invariante a soluções dominadas");
        assertEquals(0.0, report.igdPlusDelta(), 1e-15, "IGD+ deve ser 100% invariante a soluções dominadas");
        assertEquals(0.0, report.r2Delta(), 1e-15, "R2 deve ser 100% invariante a soluções dominadas");

        // 2. Verificação analítica fechada para IGD:
        // R = {(0,1), (0.5,0.5), (1,0)}
        // A_nd = {(0.1,0.9), (0.9,0.1)}
        // d_1 = sqrt(0.1^2 + (-0.1)^2) = sqrt(0.02)
        // d_2(A_nd) = sqrt((-0.4)^2 + 0.4^2) = sqrt(0.32)
        // d_3 = sqrt((-0.1)^2 + 0.1^2) = sqrt(0.02)
        // Com a_dom = (0.5, 0.95): d_2(A_raw) = sqrt(0^2 + 0.45^2) = 0.45 < sqrt(0.32) ≈ 0.5656854249!
        double d1 = Math.sqrt(0.02);
        double d2Nd = Math.sqrt(0.32);
        double d2Raw = 0.45;
        double d3 = Math.sqrt(0.02);

        double expectedIgdPow2Nd = Math.sqrt(d1 * d1 + d2Nd * d2Nd + d3 * d3) / 3.0;
        double expectedIgdPow2Raw = Math.sqrt(d1 * d1 + d2Raw * d2Raw + d3 * d3) / 3.0;
        double expectedIgdPow1Nd = (d1 + d2Nd + d3) / 3.0;
        double expectedIgdPow1Raw = (d1 + d2Raw + d3) / 3.0;

        assertEquals(expectedIgdPow2Nd, report.igdJmetalPow2NonDominated(), 1e-15);
        assertEquals(expectedIgdPow2Raw, report.igdJmetalPow2WithDominated(), 1e-15);
        assertEquals(expectedIgdPow1Nd, report.igdClassicalPow1NonDominated(), 1e-15);
        assertEquals(expectedIgdPow1Raw, report.igdClassicalPow1WithDominated(), 1e-15);

        // A solução dominada reduz artificialmente o IGD euclidiano!
        assertTrue(report.igdJmetalPow2WithDominated() < report.igdJmetalPow2NonDominated());
        assertTrue(report.igdClassicalPow1WithDominated() < report.igdClassicalPow1NonDominated());
        assertTrue(report.igdJmetalPow2Delta() > 0.03);
        assertTrue(report.igdClassicalPow1Delta() > 0.03);
    }

    /**
     * Verifica que em casos de empate de objetivos com variáveis de decisão distintas, a extração
     * canônica realiza desempate determinístico lexicográfico e produz hashes SHA-256 idênticos para
     * {@code FUN} e {@code VAR} sob qualquer permutação da lista de entrada.
     */
    @Test
    void shouldMaintainDeterministicTieBreakingAndPermutationInvarianceOnObjectiveTies() {
        WFG2 problem = new WFG2(4, 20, 3);

        DoubleSolution solA = problem.createSolution();
        DoubleSolution solB = problem.createSolution();
        DoubleSolution solC = problem.createSolution();
        DoubleSolution solDom = problem.createSolution();

        for (int j = 0; j < problem.numberOfVariables(); j++) {
            solA.variables().set(j, 0.10 * (j + 1));
            solB.variables().set(j, 0.20 * (j + 1)); // Variáveis distintas de solA
            solC.variables().set(j, 0.15 * (j + 1));
            solDom.variables().set(j, 0.25 * (j + 1));
        }

        // solA e solB possuem exatamente o mesmo vetor de objetivos, mas variáveis distintas!
        solA.objectives()[0] = 0.2;
        solA.objectives()[1] = 0.4;
        solA.objectives()[2] = 0.8;

        solB.objectives()[0] = 0.2;
        solB.objectives()[1] = 0.4;
        solB.objectives()[2] = 0.8;

        // solC é mutuamente não-dominada com solA/solB
        solC.objectives()[0] = 0.5;
        solC.objectives()[1] = 0.2;
        solC.objectives()[2] = 0.6;

        // solDom é estritamente dominada por solA/solB
        solDom.objectives()[0] = 0.3;
        solDom.objectives()[1] = 0.5;
        solDom.objectives()[2] = 0.9;

        List<DoubleSolution> baseList = List.of(solA, solB, solC, solDom);
        CanonicalResultExtraction refExtraction = ResultFrontContract.extractCanonicalResultSet(
                baseList, problem, ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);

        assertEquals(4, refExtraction.auditReport().rawSize());
        assertEquals(3, refExtraction.auditReport().nonDominatedCount());
        assertEquals(1, refExtraction.auditReport().dominatedCount());
        assertEquals(1, refExtraction.auditReport().exactObjectiveDuplicateCount());
        assertEquals(1, refExtraction.auditReport().objectiveTieDistinctVariableCount());
        assertEquals(2, refExtraction.auditReport().uniqueCanonicalCount());

        // Como solA tem variáveis lexicograficamente menores que solB (0.10 < 0.20),
        // solA deve ser sempre escolhida como representante canônico para (0.2, 0.4, 0.8),
        // independentemente da ordem em que solA e solB apareçam na lista de entrada!
        assertEquals(0.10, refExtraction.canonicalVariables()[0][0], 1e-15);

        Random rng = new Random(42L);
        for (int trial = 0; trial < 20; trial++) {
            List<DoubleSolution> permuted = new ArrayList<>(baseList);
            Collections.shuffle(permuted, rng);
            CanonicalResultExtraction permExtraction = ResultFrontContract.extractCanonicalResultSet(
                    permuted, problem, ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);

            assertEquals(
                    refExtraction.canonicalObjectivesSha256(),
                    permExtraction.canonicalObjectivesSha256(),
                    "FUN canônico deve ser invariante à permutação da entrada");
            assertEquals(
                    refExtraction.canonicalVariablesSha256(),
                    permExtraction.canonicalVariablesSha256(),
                    "VAR canônico deve ser invariante à permutação da entrada (desempate determinístico)");
            assertArrayEquals(refExtraction.canonicalObjectives()[0], permExtraction.canonicalObjectives()[0]);
            assertArrayEquals(refExtraction.canonicalVariables()[0], permExtraction.canonicalVariables()[0]);
        }
    }

    /**
     * Verifica a filtragem controlada de soluções inválidas ({@code NaN}, {@code Infinity},
     * fora dos limites de caixa {@code variableBounds}, ou infactíveis com violação de restrições)
     * e a rejeição explícita quando a frente resultante fica vazia (0 soluções).
     */
    @Test
    void shouldFilterNonFiniteOutOfBoundsAndInfeasibleSolutionsAndRejectEmptyCanonicalFront() {
        WFG1 problem = new WFG1(4, 20, 3);

        DoubleSolution validSol = problem.createSolution();
        problem.evaluate(validSol);

        DoubleSolution nanObjSol = (DoubleSolution) validSol.copy();
        nanObjSol.objectives()[1] = Double.NaN;

        DoubleSolution infVarSol = (DoubleSolution) validSol.copy();
        infVarSol.variables().set(0, Double.POSITIVE_INFINITY);

        DoubleSolution outOfBoundsSol = (DoubleSolution) validSol.copy();
        outOfBoundsSol.variables().set(0, -5.0); // Limite inferior de WFG1 é 0.0

        DoubleSolution infeasibleSol = (DoubleSolution) validSol.copy();
        ConstraintHandling.overallConstraintViolationDegree(infeasibleSol, -1.5);
        ConstraintHandling.numberOfViolatedConstraints(infeasibleSol, 1);

        List<DoubleSolution> mixedList = new ArrayList<>();
        mixedList.add(null);
        mixedList.add(nanObjSol);
        mixedList.add(infVarSol);
        mixedList.add(outOfBoundsSol);
        mixedList.add(infeasibleSol);
        mixedList.add(validSol);

        CanonicalResultExtraction extraction = ResultFrontContract.extractCanonicalResultSet(
                mixedList, problem, ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);
        PopulationAuditReport report = extraction.auditReport();

        assertEquals(6, report.rawSize());
        assertEquals(1, report.nullEntryCount());
        assertEquals(1, report.nonFiniteObjectiveCount());
        assertEquals(1, report.nonFiniteVariableCount());
        assertEquals(1, report.outOfBoundsVariableCount());
        assertEquals(1, report.infeasibleConstraintCount());
        assertEquals(1, report.validFeasibleCount());
        assertEquals(1, report.uniqueCanonicalCount());
        assertTrue(report.hasInvalidOrInfeasibleSolutions());

        // Se todas as soluções forem inválidas, a extração produz 0 soluções canônicas
        // e writeCanonicalAndRawFiles deve recusar gravar arquivos vazios de 0 bytes!
        List<DoubleSolution> allInvalid = List.of(nanObjSol, outOfBoundsSol, infeasibleSol);
        CanonicalResultExtraction emptyExtraction = ResultFrontContract.extractCanonicalResultSet(
                allInvalid, problem, ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);
        assertEquals(0, emptyExtraction.auditReport().uniqueCanonicalCount());

        Path funEmpty = tempDir.resolve("FUN_empty.csv");
        Path varEmpty = tempDir.resolve("VAR_empty.csv");
        assertThrows(
                IllegalStateException.class,
                () -> ResultFrontContract.writeCanonicalAndRawFiles(
                        emptyExtraction, funEmpty, varEmpty, false));
        assertFalse(Files.exists(funEmpty), "Não deve criar arquivo FUN vazio (0 bytes)");
        assertFalse(Files.exists(varEmpty), "Não deve criar arquivo VAR vazio (0 bytes)");
    }

    /**
     * Verifica que {@link ResultFrontContract#verifyFunVarAlignment} detecta imediatamente
     * desalinhamento entre linhas de {@code FUN} e {@code VAR}, contagem divergente de linhas
     * ou arquivos vazios de 0 bytes.
     */
    @Test
    void shouldDetectMisalignedFunVarRowsAndEmptyFiles() throws IOException {
        WFG1 problem = new WFG1(4, 20, 3);
        List<DoubleSolution> pop = new ArrayList<>();
        JMetalRandom.getInstance().setSeed(777L);
        for (int i = 0; i < 10; i++) {
            DoubleSolution s = problem.createSolution();
            problem.evaluate(s);
            pop.add(s);
        }

        CanonicalResultExtraction extraction = ResultFrontContract.extractCanonicalResultSet(
                pop, problem, ResultFrontContract.DEFAULT_DUPLICATE_OBJ_TOLERANCE);
        Path funFile = tempDir.resolve("FUN0.csv");
        Path varFile = tempDir.resolve("VAR0.csv");
        ResultFrontContract.writeCanonicalAndRawFiles(extraction, funFile, varFile, false);

        // 1. Alinhamento legítimo deve passar com discrepância 0.0
        FunVarAlignmentReport okReport = ResultFrontContract.verifyFunVarAlignment(
                funFile, varFile, problem, 1e-12);
        assertTrue(okReport.isAlignedAndValid());
        assertEquals(0, okReport.misalignedRowCount());
        assertEquals(0.0, okReport.maxObjectiveDiscrepancy(), 1e-15);

        // 2. Permutar duas linhas de VAR sem permutar FUN deve acusar desalinhamento!
        double[][] varMatrix = VectorUtils.readVectors(varFile.toString(), ",");
        double[] row0 = varMatrix[0];
        varMatrix[0] = varMatrix[1];
        varMatrix[1] = row0;
        Path swappedVarFile = tempDir.resolve("VAR0_swapped.csv");
        ResultFrontContract.writeMatrixCsvAtomically(varMatrix, swappedVarFile);

        FunVarAlignmentReport swappedReport = ResultFrontContract.verifyFunVarAlignment(
                funFile, swappedVarFile, problem, 1e-12);
        assertFalse(swappedReport.isAlignedAndValid());
        assertFalse(swappedReport.reevaluationMatchesFun());
        assertEquals(2, swappedReport.misalignedRowCount());
        assertTrue(swappedReport.maxObjectiveDiscrepancy() > 1e-4);

        // 3. Arquivo vazio (0 bytes) deve ser rejeitado
        Path zeroByteFun = tempDir.resolve("FUN_zero.csv");
        Files.writeString(zeroByteFun, "", StandardCharsets.UTF_8);
        FunVarAlignmentReport zeroReport = ResultFrontContract.verifyFunVarAlignment(
                zeroByteFun, varFile, problem, 1e-12);
        assertFalse(zeroReport.isAlignedAndValid());
        assertTrue(zeroReport.diagnosticMessage().contains("0 bytes"));
    }
}

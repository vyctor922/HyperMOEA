package br.usp.lti.newmoea.study;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.usp.lti.newmoea.util.IsolatedJobExecutor;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.JobExecutionResult;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.JobSpec;
import br.usp.lti.newmoea.util.IsolatedJobExecutor.ManifestEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Suíte de testes de isolamento, determinismo de sementes e reprodutibilidade bit-a-bit (Fase 3 —
 * T15).
 *
 * <p>Valida experimentalmente:
 *
 * <ol>
 *   <li>Derivação determinística, livre de colisões e invariante à grafia em
 *       {@link IsolatedJobExecutor#computeDeterministicSeed(long, String, int, String, int)};
 *   <li>Reprodutibilidade bit-a-bit ({@code objectivesSha256}, {@code variablesSha256} e bits IEEE
 *       754) para todos os 6 algoritmos principais ({@code NSGA-III}, {@code MOEA/DD},
 *       {@code FDEA}, {@code RVEA}, {@code AGE-MOEA}, {@code AGE-MOEA-II});
 *   <li>Independência estrita da ordem de execução entre múltiplos jobs ({@code [A, B, C]} vs
 *       {@code [C, A, B]});
 *   <li>Sensibilidade à semente (\(\text{runId} = 0\) vs \(\text{runId} = 1\));
 *   <li>Ausência de vazamento de estado entre problemas e dimensões distintas (\(m=2\) seguido de
 *       \(m=3\));
 *   <li>Exportação e leitura do manifesto de reprodutibilidade CSV de 16 colunas.
 * </ol>
 */
class IsolationAndReproducibilityTest {

    private static final long BASE_SEED = IsolatedJobExecutor.DEFAULT_BASE_SEED;

    @Test
    void testDeterministicSeedDerivationAndAvalanche() {
        long s1 = IsolatedJobExecutor.computeDeterministicSeed(BASE_SEED, "WFG1", 3, "NSGA-III", 0);
        long s2 = IsolatedJobExecutor.computeDeterministicSeed(BASE_SEED, "wfg1", 3, "NSGAIII", 0);
        assertEquals(s1, s2, "A semente deve ser invariante à capitalização/hifenização canônica.");

        long diffRun =
                IsolatedJobExecutor.computeDeterministicSeed(BASE_SEED, "WFG1", 3, "NSGA-III", 1);
        long diffAlg =
                IsolatedJobExecutor.computeDeterministicSeed(BASE_SEED, "WFG1", 3, "MOEA/DD", 0);
        long diffM =
                IsolatedJobExecutor.computeDeterministicSeed(BASE_SEED, "WFG1", 5, "NSGA-III", 0);
        long diffProb =
                IsolatedJobExecutor.computeDeterministicSeed(BASE_SEED, "WFG2", 3, "NSGA-III", 0);
        long diffBase =
                IsolatedJobExecutor.computeDeterministicSeed(
                        BASE_SEED + 1L, "WFG1", 3, "NSGA-III", 0);

        assertNotEquals(s1, diffRun, "Alterar runId deve alterar effectiveSeed.");
        assertNotEquals(s1, diffAlg, "Alterar algorithmName deve alterar effectiveSeed.");
        assertNotEquals(s1, diffM, "Alterar m deve alterar effectiveSeed.");
        assertNotEquals(s1, diffProb, "Alterar problemName deve alterar effectiveSeed.");
        assertNotEquals(s1, diffBase, "Alterar baseSeed deve alterar effectiveSeed.");

        // Verifica ausência de colisões em grade representativa (16 problemas x 6 m x 6 algs x 30 runs = 17.280 tuplas)
        String[] problems = {
            "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9",
            "DTLZ1", "DTLZ2", "DTLZ3", "DTLZ4", "DTLZ5", "DTLZ6", "DTLZ7"
        };
        int[] mValues = {2, 3, 5, 8, 10, 15};
        String[] algorithms = {"NSGA-III", "MOEA/DD", "FDEA", "RVEA", "AGE-MOEA", "AGE-MOEA-II"};

        Set<Long> uniqueSeeds = new HashSet<>(20_000);
        int totalTuples = 0;
        for (String p : problems) {
            for (int m : mValues) {
                for (String alg : algorithms) {
                    for (int run = 0; run < 30; run++) {
                        long seed =
                                IsolatedJobExecutor.computeDeterministicSeed(
                                        BASE_SEED, p, m, alg, run);
                        assertTrue(
                                uniqueSeeds.add(seed),
                                "Colisão de semente detectada para tupla: "
                                        + p
                                        + ", m="
                                        + m
                                        + ", alg="
                                        + alg
                                        + ", run="
                                        + run);
                        totalTuples++;
                    }
                }
            }
        }
        assertEquals(17_280, totalTuples);
        assertEquals(17_280, uniqueSeeds.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NSGA-III", "MOEA/DD", "FDEA", "RVEA", "AGE-MOEA", "AGE-MOEA-II"})
    void testBitForBitReproducibilityAcrossAllSixMainAlgorithms(String algorithmName) {
        // m=2 => N_config = 100; 3 gerações => targetBudget = 300 avaliações
        JobSpec spec =
                JobSpec.ofCustomBudget("WFG1", 2, algorithmName, 0, BASE_SEED, 3, 300, true);

        JobExecutionResult firstRun = IsolatedJobExecutor.executeJobInProcess(spec);
        JobExecutionResult secondRun = IsolatedJobExecutor.executeJobInProcess(spec);

        assertEquals("SUCCESS", firstRun.status());
        assertEquals("SUCCESS", secondRun.status());
        assertEquals(300, firstRun.actualEvaluations());
        assertEquals(300, secondRun.actualEvaluations());

        assertEquals(
                firstRun.objectivesSha256(),
                secondRun.objectivesSha256(),
                "SHA-256 de FUN deve ser 100% idêntico para " + algorithmName);
        assertEquals(
                firstRun.variablesSha256(),
                secondRun.variablesSha256(),
                "SHA-256 de VAR deve ser 100% idêntico para " + algorithmName);
        assertEquals(firstRun.rawResultSize(), secondRun.rawResultSize());
        assertEquals(firstRun.nonDominatedResultSize(), secondRun.nonDominatedResultSize());

        assertMatricesBitForBitIdentical(firstRun.rawObjectives(), secondRun.rawObjectives());
        assertMatricesBitForBitIdentical(firstRun.rawVariables(), secondRun.rawVariables());
    }

    @Test
    void testOrderIndependenceAcrossPermutedJobQueues() {
        JobSpec jobA = JobSpec.ofCustomBudget("WFG1", 2, "NSGA-III", 0, BASE_SEED, 3, 300, true);
        JobSpec jobB = JobSpec.ofCustomBudget("DTLZ2", 3, "MOEA/DD", 1, BASE_SEED, 3, 273, true);
        JobSpec jobC = JobSpec.ofCustomBudget("WFG4", 2, "FDEA", 2, BASE_SEED, 3, 300, true);
        JobSpec jobD = JobSpec.ofCustomBudget("DTLZ1", 2, "RVEA", 3, BASE_SEED, 3, 300, true);
        JobSpec jobE = JobSpec.ofCustomBudget("WFG2", 3, "AGE-MOEA", 4, BASE_SEED, 3, 273, true);
        JobSpec jobF =
                JobSpec.ofCustomBudget("WFG3", 2, "AGE-MOEA-II", 5, BASE_SEED, 3, 300, true);

        List<JobSpec> order1 = List.of(jobA, jobB, jobC, jobD, jobE, jobF);
        List<JobSpec> order2 = List.of(jobF, jobD, jobB, jobE, jobC, jobA);

        Map<String, JobExecutionResult> resultsOrder1 = new HashMap<>();
        for (JobSpec job : order1) {
            resultsOrder1.put(job.jobId(), IsolatedJobExecutor.executeJobInProcess(job));
        }

        Map<String, JobExecutionResult> resultsOrder2 = new HashMap<>();
        for (JobSpec job : order2) {
            resultsOrder2.put(job.jobId(), IsolatedJobExecutor.executeJobInProcess(job));
        }

        for (JobSpec job : order1) {
            JobExecutionResult r1 = resultsOrder1.get(job.jobId());
            JobExecutionResult r2 = resultsOrder2.get(job.jobId());
            assertEquals(
                    r1.objectivesSha256(),
                    r2.objectivesSha256(),
                    "Ordem de execução alterou FUN do job " + job.jobId());
            assertEquals(
                    r1.variablesSha256(),
                    r2.variablesSha256(),
                    "Ordem de execução alterou VAR do job " + job.jobId());
            assertEquals(r1.actualEvaluations(), r2.actualEvaluations());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NSGA-III", "MOEA/DD", "FDEA", "RVEA", "AGE-MOEA", "AGE-MOEA-II"})
    void testSeedSensitivityProducesDistinctTrajectories(String algorithmName) {
        JobSpec run0 =
                JobSpec.ofCustomBudget("WFG1", 2, algorithmName, 0, BASE_SEED, 3, 300, true);
        JobSpec run1 =
                JobSpec.ofCustomBudget("WFG1", 2, algorithmName, 1, BASE_SEED, 3, 300, true);

        assertNotEquals(run0.effectiveSeed(), run1.effectiveSeed());

        JobExecutionResult res0 = IsolatedJobExecutor.executeJobInProcess(run0);
        JobExecutionResult res1 = IsolatedJobExecutor.executeJobInProcess(run1);

        assertNotEquals(
                res0.objectivesSha256(),
                res1.objectivesSha256(),
                "Runs com sementes distintas devem produzir populações FUN distintas para "
                        + algorithmName);
        assertNotEquals(
                res0.variablesSha256(),
                res1.variablesSha256(),
                "Runs com sementes distintas devem produzir populações VAR distintas para "
                        + algorithmName);
    }

    @Test
    void testNoStateLeakageAcrossDimensionsAndProblems(@TempDir Path tempDir) throws IOException {
        JobSpec targetSpec =
                JobSpec.ofCustomBudget("DTLZ2", 3, "MOEA/DD", 7, BASE_SEED, 3, 273, true);

        // Executa targetSpec após um job m=2 (WFG1)
        JobSpec predecessorM2 =
                JobSpec.ofCustomBudget("WFG1", 2, "NSGA-III", 0, BASE_SEED, 3, 300, true);
        JobExecutionResult resM2 = IsolatedJobExecutor.executeJobInProcess(predecessorM2);
        JobExecutionResult targetAfterM2 = IsolatedJobExecutor.executeJobInProcess(targetSpec);

        // Executa targetSpec após um job m=5 (WFG9 com FDEA)
        JobSpec predecessorM5 =
                JobSpec.ofCustomBudget("WFG9", 5, "FDEA", 9, BASE_SEED, 2, 420, true);
        JobExecutionResult resM5 = IsolatedJobExecutor.executeJobInProcess(predecessorM5);
        JobExecutionResult targetAfterM5 = IsolatedJobExecutor.executeJobInProcess(targetSpec);

        assertEquals(targetAfterM2.objectivesSha256(), targetAfterM5.objectivesSha256());
        assertEquals(targetAfterM2.variablesSha256(), targetAfterM5.variablesSha256());

        // Verifica também exportação e releitura fiel do manifesto CSV de 16 colunas
        Path manifestFile = tempDir.resolve("reproducibility_manifest.csv");
        List<ManifestEntry> expectedEntries =
                List.of(
                        resM2.toManifestEntry(),
                        targetAfterM2.toManifestEntry(),
                        resM5.toManifestEntry());

        IsolatedJobExecutor.exportManifestToCsv(manifestFile, expectedEntries);
        List<ManifestEntry> loadedEntries = IsolatedJobExecutor.readManifestFromCsv(manifestFile);

        assertEquals(expectedEntries, loadedEntries);
    }

    private static void assertMatricesBitForBitIdentical(double[][] expected, double[][] actual) {
        assertEquals(expected.length, actual.length, "Número de linhas divergente.");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i].length, actual[i].length, "Número de colunas divergente.");
            long[] expectedBits = new long[expected[i].length];
            long[] actualBits = new long[actual[i].length];
            for (int j = 0; j < expected[i].length; j++) {
                expectedBits[j] = Double.doubleToLongBits(expected[i][j]);
                actualBits[j] = Double.doubleToLongBits(actual[i][j]);
            }
            assertArrayEquals(
                    expectedBits, actualBits, "Divergência IEEE 754 na linha " + i + " da matriz.");
        }
    }
}

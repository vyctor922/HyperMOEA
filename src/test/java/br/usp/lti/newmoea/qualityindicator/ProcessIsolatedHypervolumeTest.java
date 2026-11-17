package br.usp.lti.newmoea.qualityindicator;

import br.usp.lti.newmoea.util.HypervolumeResult;
import br.usp.lti.newmoea.util.ProcessIsolatedHypervolumeExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProcessIsolatedHypervolumeTest {

    @Test
    @DisplayName("Exact calculation succeeds in isolated process and matches theoretical value")
    void testExactProcessIsolatedSuccess() {
        // Ponto [0.5, 0.5] com ref [1.0, 1.0] -> HV = 0.25
        double[][] front = new double[][] {
                {0.5, 0.5}
        };
        double[] lower = new double[] {0.0, 0.0};
        double[] ref = new double[] {1.0, 1.0};

        ProcessIsolatedHypervolumeExecutor executor = new ProcessIsolatedHypervolumeExecutor(
                15_000L, // 15s timeout
                1_000L,  // 1s grace period
                100_000L // 100k MC samples
        );

        HypervolumeResult result = executor.compute(front, lower, ref, "PISA", 42L);

        assertEquals(HypervolumeResult.ExecutionStatus.SUCCESS_EXACT, result.getStatus());
        assertTrue(result.isExact());
        assertEquals(0.25, result.getValue(), 1e-6);
        assertEquals(0.0, result.getStandardError());
        assertTrue(result.getExecutionWallTimeMs() >= 0);
    }

    @Test
    @DisplayName("Timeout terminates uncooperative worker with destroyForcibly and falls back to Monte Carlo")
    void testTimeoutTerminationAndFallback() {
        // Criar uma frente com pontos para timeout
        double[][] front = new double[20][5];
        for (int i = 0; i < 20; i++) {
            for (int d = 0; d < 5; d++) {
                front[i][d] = 0.1 * (d + 1) + 0.01 * i;
            }
        }
        double[] ref = new double[] {1.0, 1.0, 1.0, 1.0, 1.0};

        // Timeout intencionalmente irrealizável de 1 ms para forçar disparo de timeout
        ProcessIsolatedHypervolumeExecutor executor = new ProcessIsolatedHypervolumeExecutor(
                1L,      // 1 ms timeout
                200L,    // 200 ms grace period
                50_000L  // 50k MC samples
        );

        HypervolumeResult result = executor.compute(front, null, ref, "PISA", 12345L);

        // Deve ter estourado timeout e caído para Monte Carlo com Wilson CI
        assertEquals(HypervolumeResult.ExecutionStatus.TIMEOUT_FALLBACK_MC, result.getStatus());
        assertFalse(result.isExact());
        assertEquals(HypervolumeResult.MethodType.MONTE_CARLO_WILSON, result.getMethod());
        assertTrue(result.getValue() > 0.0);
        assertTrue(result.getStandardError() > 0.0);
        assertTrue(result.getCiUpper() >= result.getValue());
        assertTrue(result.getCiLower() <= result.getValue());
        assertTrue(result.getDiagnosticMessage().contains("timed out"));
    }

    @Test
    @DisplayName("Empty front returns empty result without spawning worker")
    void testEmptyFrontDoesNotSpawnWorker() {
        double[][] front = new double[0][0];
        double[] ref = new double[] {1.0, 1.0};

        ProcessIsolatedHypervolumeExecutor executor = new ProcessIsolatedHypervolumeExecutor();
        HypervolumeResult result = executor.compute(front, null, ref, "PISA", 1L);

        assertEquals(HypervolumeResult.ExecutionStatus.EMPTY_FRONT, result.getStatus());
        assertEquals(0.0, result.getValue());
    }
}


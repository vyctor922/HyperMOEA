package br.usp.lti.newmoea.qualityindicator;

import br.usp.lti.newmoea.util.HypervolumeResult;
import br.usp.lti.newmoea.util.MonteCarloHypervolume;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloHypervolumeTest {

    @Test
    @DisplayName("Single point 2D box hypervolume matches theoretical value within Wilson CI")
    void testSinglePoint2DTheoreticalHypervolume() {
        // Ponto [0.5, 0.5], ref [1.0, 1.0], lower [0.0, 0.0]
        // Área dominada por [0.5, 0.5] na caixa [0,1]^2 é [0.5, 1.0] x [0.5, 1.0] = 0.5 * 0.5 = 0.25
        double[][] front = new double[][] {
                {0.5, 0.5}
        };
        double[] lower = new double[] {0.0, 0.0};
        double[] ref = new double[] {1.0, 1.0};
        long samples = 500_000L;
        long seed = 42L;

        HypervolumeResult result = MonteCarloHypervolume.compute(front, lower, ref, samples, seed);

        assertEquals(HypervolumeResult.ExecutionStatus.DIRECT_MC, result.getStatus());
        assertEquals(0.25, result.getValue(), 0.005);
        assertTrue(result.getStandardError() > 0.0);
        assertTrue(result.getCiLower() <= 0.25);
        assertTrue(result.getCiUpper() >= 0.25);
    }

    @Test
    @DisplayName("Two points 2D union hypervolume matches analytical calculation")
    void testTwoPoints2DHypervolume() {
        // Pontos: [0.2, 0.8] e [0.8, 0.2] com ref [1.0, 1.0]
        // Volume 1: [0.2, 1.0] x [0.8, 1.0] -> 0.8 * 0.2 = 0.16
        // Volume 2: [0.8, 1.0] x [0.2, 1.0] -> 0.2 * 0.8 = 0.16
        // Interseção: [0.8, 1.0] x [0.8, 1.0] -> 0.2 * 0.2 = 0.04
        // União: 0.16 + 0.16 - 0.04 = 0.28
        double[][] front = new double[][] {
                {0.2, 0.8},
                {0.8, 0.2}
        };
        double[] lower = new double[] {0.0, 0.0};
        double[] ref = new double[] {1.0, 1.0};
        long samples = 500_000L;
        long seed = 12345L;

        HypervolumeResult result = MonteCarloHypervolume.compute(front, lower, ref, samples, seed);

        assertEquals(0.28, result.getValue(), 0.005);
        assertTrue(result.getCiLower() <= 0.28);
        assertTrue(result.getCiUpper() >= 0.28);
    }

    @Test
    @DisplayName("Empty front results in identically 0.0 HV with zero SE")
    void testEmptyFront() {
        double[][] front = new double[0][0];
        double[] ref = new double[] {1.0, 1.0};

        HypervolumeResult result = MonteCarloHypervolume.compute(front, null, ref, 10_000L, 1L);

        assertEquals(0.0, result.getValue());
        assertEquals(0.0, result.getStandardError());
        assertEquals(HypervolumeResult.ExecutionStatus.EMPTY_FRONT, result.getStatus());
    }

    @Test
    @DisplayName("Degenerate bounding box is correctly detected and handled")
    void testDegenerateBox() {
        double[][] front = new double[][] {{1.0, 1.0}};
        double[] lower = new double[] {1.0, 2.0};
        double[] ref = new double[] {1.0, 2.0}; // ref <= lower

        HypervolumeResult result = MonteCarloHypervolume.compute(front, lower, ref, 10_000L, 1L);

        assertEquals(0.0, result.getValue());
        assertEquals(HypervolumeResult.ExecutionStatus.DEGENERATE_BOX, result.getStatus());
    }

    @Test
    @DisplayName("When points are outside the reference point, X=0 and Wilson upper bound is strictly positive")
    void testZeroHitsYieldsNonZeroWilsonUpperBound() {
        // Ponto [2.0, 2.0] é pior que ref [1.0, 1.0], logo não domina nenhuma amostra na caixa [0,1]^2
        double[][] front = new double[][] {
                {2.0, 2.0}
        };
        double[] lower = new double[] {0.0, 0.0};
        double[] ref = new double[] {1.0, 1.0};
        long samples = 100_000L;

        HypervolumeResult result = MonteCarloHypervolume.compute(front, lower, ref, samples, 999L);

        assertEquals(0.0, result.getValue(), 1e-12);
        assertEquals(0L, result.getHitCount());
        // Critério T20: X=0 não prova HV=0; Wilson upper bound deve ser > 0
        assertTrue(result.getCiUpper() > 0.0, "Wilson upper bound must be strictly positive when X=0");
        assertTrue(result.getCiUpper() < 0.001, "Wilson upper bound for 100k samples should be small");
    }

    @Test
    @DisplayName("Deterministic seed produces bit-exact identical results")
    void testDeterministicReproducibility() {
        double[][] front = new double[][] {
                {0.3, 0.7},
                {0.6, 0.4}
        };
        double[] lower = new double[] {0.0, 0.0};
        double[] ref = new double[] {1.0, 1.0};

        HypervolumeResult r1 = MonteCarloHypervolume.compute(front, lower, ref, 100_000L, 777L);
        HypervolumeResult r2 = MonteCarloHypervolume.compute(front, lower, ref, 100_000L, 777L);

        assertEquals(r1.getValue(), r2.getValue());
        assertEquals(r1.getHitCount(), r2.getHitCount());
        assertEquals(r1.getStandardError(), r2.getStandardError());
        assertEquals(r1.getCiLower(), r2.getCiLower());
        assertEquals(r1.getCiUpper(), r2.getCiUpper());
    }
}


package br.usp.lti.newmoea.statistics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class KruskalWallisTestTest {

    @Test
    @DisplayName("Kruskal-Wallis omnibus test matches analytical derivation and standard oracle on R1 fixture")
    void testKruskalWallisBenchmarkFixture() {
        // Fixture oficial R1:
        // G1: 2.9, 3.0, 2.5, 2.6, 3.2 (n=5)
        // G2: 3.8, 3.7, 4.0, 3.8, 3.9 (n=5)
        // G3: 2.8, 3.4, 3.7, 3.2, 2.8 (n=5)
        Map<String, List<Double>> groups = new LinkedHashMap<>();
        groups.put("G1", Arrays.asList(2.9, 3.0, 2.5, 2.6, 3.2));
        groups.put("G2", Arrays.asList(3.8, 3.7, 4.0, 3.8, 3.9));
        groups.put("G3", Arrays.asList(2.8, 3.4, 3.7, 3.2, 2.8));

        KruskalWallisTest.KruskalWallisResult result = KruskalWallisTest.test(groups);

        // H0 teórico = 9.785
        assertEquals(9.785, result.getH0(), 1e-4);

        // Fator de correção de empates C = 1 - 24/3360 = 0.99285714...
        assertEquals(0.992857, result.getTieCorrection(), 1e-5);

        // H corrigido teórico = 9.855395...
        assertEquals(9.8554, result.getH(), 1e-3);
        assertEquals(2, result.getDf());

        // p-valor teórico com df=2: exp(-H/2) = 0.007243...
        assertEquals(0.007243, result.getPValue(), 1e-4);
        assertTrue(result.isSignificant(0.05));
        assertTrue(result.isSignificant(0.01));

        // Rank sums teóricos: G1=21.5, G2=64.5, G3=34.0
        assertEquals(21.5, result.getGroupSummaries().get("G1").getRankSum(), 1e-6);
        assertEquals(64.5, result.getGroupSummaries().get("G2").getRankSum(), 1e-6);
        assertEquals(34.0, result.getGroupSummaries().get("G3").getRankSum(), 1e-6);

        // Pós-teste de Dunn: 3 comparações
        assertEquals(3, result.getComparisons().size());

        // Comparação G1 vs G2 deve ser altamente significativa (G2 > G1)
        DunnPostHocTest.DunnComparison g1vsg2 = result.getComparisons().stream()
                .filter(c -> (c.getGroup1().equals("G1") && c.getGroup2().equals("G2")) ||
                             (c.getGroup1().equals("G2") && c.getGroup2().equals("G1")))
                .findFirst().orElseThrow();

        assertTrue(g1vsg2.isSignificant(0.05));
        assertEquals(0.0, g1vsg2.getVarghaDelaneyA12(), 1e-6); // Nenhum ponto de G1 supera G2
        assertEquals(-1.0, g1vsg2.getCliffsDelta(), 1e-6);
    }

    @Test
    @DisplayName("Degenerate case where all values across all groups are identical yields C=0, H=0, p=1 without NaN")
    void testAllIdenticalValuesDegenerateCase() {
        Map<String, List<Double>> groups = new LinkedHashMap<>();
        groups.put("AlgoA", Arrays.asList(5.0, 5.0, 5.0, 5.0));
        groups.put("AlgoB", Arrays.asList(5.0, 5.0, 5.0, 5.0));
        groups.put("AlgoC", Arrays.asList(5.0, 5.0, 5.0, 5.0));

        KruskalWallisTest.KruskalWallisResult result = KruskalWallisTest.test(groups);

        assertEquals(0.0, result.getH());
        assertEquals(1.0, result.getPValue());
        assertFalse(result.isSignificant(0.05));

        for (DunnPostHocTest.DunnComparison comp : result.getComparisons()) {
            assertEquals(0.0, comp.getZStatistic());
            assertEquals(1.0, comp.getUnadjustedPValue());
            assertEquals(1.0, comp.getAdjustedPValue());
            assertEquals(0.5, comp.getVarghaDelaneyA12(), 1e-6);
            assertEquals(0.0, comp.getCliffsDelta(), 1e-6);
            assertFalse(comp.isSignificant(0.05));
        }
    }

    @Test
    @DisplayName("Monotonic affine transformation NHV = 1 - HV / c inverts ranks and preserves exact H and p-value")
    void testNhvTransformationInvariance() {
        // HV original
        Map<String, List<Double>> hvGroups = new LinkedHashMap<>();
        hvGroups.put("A", Arrays.asList(0.85, 0.88, 0.82, 0.87));
        hvGroups.put("B", Arrays.asList(0.65, 0.70, 0.68, 0.72));
        hvGroups.put("C", Arrays.asList(0.40, 0.45, 0.42, 0.38));

        // NHV normalizado: NHV = 1.0 - HV / 2.0 (escala constante)
        double c = 2.0;
        Map<String, List<Double>> nhvGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> e : hvGroups.entrySet()) {
            List<Double> nhvVals = new ArrayList<>();
            for (Double v : e.getValue()) {
                nhvVals.add(1.0 - (v / c));
            }
            nhvGroups.put(e.getKey(), nhvVals);
        }

        KruskalWallisTest.KruskalWallisResult resHv = KruskalWallisTest.test(hvGroups);
        KruskalWallisTest.KruskalWallisResult resNhv = KruskalWallisTest.test(nhvGroups);

        // H e p-valor do Kruskal-Wallis devem ser idênticos
        assertEquals(resHv.getH(), resNhv.getH(), 1e-9);
        assertEquals(resHv.getPValue(), resNhv.getPValue(), 1e-9);
        assertEquals(resHv.getTieCorrection(), resNhv.getTieCorrection(), 1e-9);
        assertEquals(resHv.getEpsilonSquared(), resNhv.getEpsilonSquared(), 1e-9);

        // Pós-teste de Dunn: p-valores ajustados de Holm devem ser idênticos,
        // com o z-statistic invertendo o sinal exato (já que a ordem de minimização inverteu)
        for (int i = 0; i < resHv.getComparisons().size(); i++) {
            DunnPostHocTest.DunnComparison dHv = resHv.getComparisons().get(i);
            DunnPostHocTest.DunnComparison dNhv = resNhv.getComparisons().get(i);

            assertEquals(dHv.getGroup1(), dNhv.getGroup1());
            assertEquals(dHv.getGroup2(), dNhv.getGroup2());
            assertEquals(dHv.getZStatistic(), -dNhv.getZStatistic(), 1e-9);
            assertEquals(dHv.getAdjustedPValue(), dNhv.getAdjustedPValue(), 1e-9);
        }
    }

    @Test
    @DisplayName("Dunn-Holm step-down procedure guarantees p-value monotonicity and FWER control")
    void testHolmMonotonicity() {
        Map<String, List<Double>> groups = new LinkedHashMap<>();
        groups.put("G1", Arrays.asList(10.0, 11.0, 12.0, 13.0));
        groups.put("G2", Arrays.asList(20.0, 21.0, 22.0, 23.0));
        groups.put("G3", Arrays.asList(30.0, 31.0, 32.0, 33.0));
        groups.put("G4", Arrays.asList(40.0, 41.0, 42.0, 43.0));

        KruskalWallisTest.KruskalWallisResult result = KruskalWallisTest.test(groups);

        List<DunnPostHocTest.DunnComparison> sorted = new ArrayList<>(result.getComparisons());
        Collections.sort(sorted); // Ordena por p-valor bruto

        double prevAdj = 0.0;
        for (DunnPostHocTest.DunnComparison comp : sorted) {
            assertTrue(comp.getAdjustedPValue() >= comp.getUnadjustedPValue(),
                    "Adjusted p-value must be >= unadjusted p-value");
            assertTrue(comp.getAdjustedPValue() >= prevAdj,
                    "Holm step-down must maintain monotonicity");
            assertTrue(comp.getAdjustedPValue() <= 1.0,
                    "Adjusted p-value cannot exceed 1.0");
            prevAdj = comp.getAdjustedPValue();
        }
    }

    @Test
    @DisplayName("Input validation properly rejects empty, single-group or invalid inputs")
    void testInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> KruskalWallisTest.test(null));

        Map<String, List<Double>> single = new HashMap<>();
        single.put("A", Arrays.asList(1.0, 2.0));
        assertThrows(IllegalArgumentException.class, () -> KruskalWallisTest.test(single));

        Map<String, List<Double>> emptyGroup = new HashMap<>();
        emptyGroup.put("A", Arrays.asList(1.0, 2.0));
        emptyGroup.put("B", Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> KruskalWallisTest.test(emptyGroup));
    }
}


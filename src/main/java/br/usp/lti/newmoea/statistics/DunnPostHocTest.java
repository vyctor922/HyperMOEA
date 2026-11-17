package br.usp.lti.newmoea.statistics;

import java.io.Serializable;
import java.util.*;

/**
 * Implementação do pós-teste não-paramétrico de Dunn para comparações múltiplas par-a-par
 * com correção para empates, controle de FWER via procedimento sequencial de Holm (Dunn-Holm)
 * e tamanho de efeito de Vargha-Delaney ($\hat{A}_{12}$) e Cliff's Delta ($\delta$).
 *
 * <p>Referências:
 * <ul>
 *   <li>Dunn, O. J. (1964). Multiple comparisons using rank sums. Technometrics, 6(3), 241-252.</li>
 *   <li>Holm, S. (1979). A simple sequentially rejective multiple test procedure. Scandinavian Journal of Statistics, 6(2), 65-70.</li>
 *   <li>Vargha, A., & Delaney, H. D. (2000). A critique and improvement of the CL common language effect size statistics of McGraw and Wong.
 *       Journal of Educational and Behavioral Statistics, 25(2), 101-132.</li>
 * </ul>
 */
public final class DunnPostHocTest {

    private DunnPostHocTest() {
    }

    public static final class DunnComparison implements Serializable, Comparable<DunnComparison> {
        private static final long serialVersionUID = 1L;

        private final String group1;
        private final String group2;
        private final double zStatistic;
        private final double unadjustedPValue;
        private double adjustedPValue;
        private final double varghaDelaneyA12;
        private final double cliffsDelta;

        public DunnComparison(
                String group1,
                String group2,
                double zStatistic,
                double unadjustedPValue,
                double adjustedPValue,
                double varghaDelaneyA12,
                double cliffsDelta) {
            this.group1 = group1;
            this.group2 = group2;
            this.zStatistic = zStatistic;
            this.unadjustedPValue = unadjustedPValue;
            this.adjustedPValue = adjustedPValue;
            this.varghaDelaneyA12 = varghaDelaneyA12;
            this.cliffsDelta = cliffsDelta;
        }

        public String getGroup1() { return group1; }
        public String getGroup2() { return group2; }
        public double getZStatistic() { return zStatistic; }
        public double getUnadjustedPValue() { return unadjustedPValue; }
        public double getAdjustedPValue() { return adjustedPValue; }
        public double getVarghaDelaneyA12() { return varghaDelaneyA12; }
        public double getCliffsDelta() { return cliffsDelta; }

        public void setAdjustedPValue(double p) {
            this.adjustedPValue = p;
        }

        public boolean isSignificant(double alpha) {
            return adjustedPValue < alpha;
        }

        @Override
        public int compareTo(DunnComparison other) {
            return Double.compare(this.unadjustedPValue, other.unadjustedPValue);
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "%s vs %s: z=%.4f, p_raw=%.6e, p_holm=%.6e, A12=%.4f, delta=%.4f",
                    group1, group2, zStatistic, unadjustedPValue, adjustedPValue, varghaDelaneyA12, cliffsDelta);
        }
    }

    public static List<DunnComparison> compute(
            Map<String, List<Double>> rawGroups,
            Map<String, KruskalWallisTest.GroupSummary> summaries,
            int nTotal,
            long tieSum) {

        List<String> groupNames = new ArrayList<>(summaries.keySet());
        int k = groupNames.size();
        List<DunnComparison> comparisons = new ArrayList<>();

        if (k < 2) {
            return comparisons;
        }

        // Fator de variância comum com correção para empates:
        // sigma_base^2 = (N*(N+1)/12) - sum(t^3 - t) / (12*(N-1))
        double n = (double) nTotal;
        double sigmaBaseSq;
        if (nTotal > 1) {
            sigmaBaseSq = (n * (n + 1.0) / 12.0) - ((double) tieSum / (12.0 * (n - 1.0)));
        } else {
            sigmaBaseSq = 0.0;
        }
        if (sigmaBaseSq < 0.0) {
            sigmaBaseSq = 0.0;
        }

        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                String g1 = groupNames.get(i);
                String g2 = groupNames.get(j);

                KruskalWallisTest.GroupSummary s1 = summaries.get(g1);
                KruskalWallisTest.GroupSummary s2 = summaries.get(g2);

                double meanR1 = s1.getMeanRank();
                double meanR2 = s2.getMeanRank();
                int n1 = s1.getN();
                int n2 = s2.getN();

                double se = Math.sqrt(sigmaBaseSq * ((1.0 / n1) + (1.0 / n2)));

                double z;
                double pRaw;
                if (se <= 1e-15) {
                    z = 0.0;
                    pRaw = 1.0;
                } else {
                    z = (meanR1 - meanR2) / se;
                    pRaw = 2.0 * (1.0 - standardNormalCdf(Math.abs(z)));
                    if (pRaw > 1.0) pRaw = 1.0;
                    if (pRaw < 0.0) pRaw = 0.0;
                }

                // Cálculo de Vargha-Delaney A12 e Cliff's Delta entre os grupos brutos
                List<Double> v1 = rawGroups.get(g1);
                List<Double> v2 = rawGroups.get(g2);
                double a12 = computeVarghaDelaneyA12(v1, v2);
                double delta = 2.0 * a12 - 1.0;

                comparisons.add(new DunnComparison(g1, g2, z, pRaw, pRaw, a12, delta));
            }
        }

        // Ajuste sequencial de Holm (Dunn-Holm)
        applyHolmAdjustment(comparisons);

        return comparisons;
    }

    private static void applyHolmAdjustment(List<DunnComparison> comparisons) {
        int m = comparisons.size();
        if (m == 0) return;

        // Ordena por p-valor bruto crescente
        List<DunnComparison> sorted = new ArrayList<>(comparisons);
        Collections.sort(sorted);

        double maxAdjusted = 0.0;
        for (int i = 0; i < m; i++) {
            DunnComparison comp = sorted.get(i);
            int remainingHypotheses = m - i;
            double adjusted = comp.getUnadjustedPValue() * remainingHypotheses;
            // Garantia de monotonicidade do procedimento step-down de Holm
            if (adjusted < maxAdjusted) {
                adjusted = maxAdjusted;
            } else {
                maxAdjusted = adjusted;
            }
            if (adjusted > 1.0) {
                adjusted = 1.0;
            }
            comp.setAdjustedPValue(adjusted);
        }
    }

    public static double computeVarghaDelaneyA12(List<Double> x, List<Double> y) {
        int nX = x.size();
        int nY = y.size();
        if (nX == 0 || nY == 0) {
            return 0.5;
        }

        double count = 0.0;
        for (Double xi : x) {
            for (Double yj : y) {
                if (xi > yj) {
                    count += 1.0;
                } else if (xi.equals(yj) || Math.abs(xi - yj) < 1e-15) {
                    count += 0.5;
                }
            }
        }
        return count / ((double) nX * nY);
    }

    /**
     * Função de distribuição acumulada da normal padrão Phi(z).
     * Aproximação racional de Hart / Abramowitz & Stegun de alta precisão (erro máximo < 1.5e-7).
     */
    public static double standardNormalCdf(double z) {
        if (Double.isNaN(z)) return Double.NaN;
        if (z < -8.0) return 0.0;
        if (z > 8.0) return 1.0;

        // Usando a função erro: Phi(z) = 0.5 * (1 + erf(z / sqrt(2)))
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    /**
     * Função erro erf(x) via aproximação de Chebyshev / Winitzki.
     * Precisão analítica < 1.2e-7.
     */
    public static double erf(double x) {
        if (x == 0.0) return 0.0;
        double sign = x < 0.0 ? -1.0 : 1.0;
        x = Math.abs(x);

        // Constantes da aproximação de Abramowitz & Stegun 7.1.26
        double p = 0.3275911;
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;

        double t = 1.0 / (1.0 + p * x);
        double poly = ((((a5 * t + a4) * t + a3) * t + a2) * t + a1) * t;
        double res = 1.0 - poly * Math.exp(-x * x);

        return sign * res;
    }
}


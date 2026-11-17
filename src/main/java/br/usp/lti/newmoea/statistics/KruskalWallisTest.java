package br.usp.lti.newmoea.statistics;

import java.io.Serializable;
import java.util.*;

/**
 * Implementação rigorosa do teste não-paramétrico de Kruskal-Wallis com correção
 * para empates (midranks), tamanho de efeito epsilon-quadrado ($\varepsilon^2$) e
 * tratamento robusto para o caso degenerado de empates totais ($C = 0$).
 *
 * <p>Referências:
 * <ul>
 *   <li>Kruskal, W. H., & Wallis, W. A. (1952). Use of ranks in one-criterion variance analysis.
 *       Journal of the American Statistical Association, 47(260), 583-621.</li>
 *   <li>Tomczak, M., & Tomczak, E. (2014). The need to report effect size estimates revisited:
 *       An overview of some recommended measures of effect size. Trends in Sport Sciences, 1(21), 19-25.</li>
 * </ul>
 */
public final class KruskalWallisTest {

    private KruskalWallisTest() {
    }

    public static final class GroupSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int n;
        private final double rankSum;
        private final double meanRank;
        private final double median;
        private final double iqr;

        public GroupSummary(String name, int n, double rankSum, double meanRank, double median, double iqr) {
            this.name = name;
            this.n = n;
            this.rankSum = rankSum;
            this.meanRank = meanRank;
            this.median = median;
            this.iqr = iqr;
        }

        public String getName() { return name; }
        public int getN() { return n; }
        public double getRankSum() { return rankSum; }
        public double getMeanRank() { return meanRank; }
        public double getMedian() { return median; }
        public double getIqr() { return iqr; }

        @Override
        public String toString() {
            return String.format(Locale.US, "%s (n=%d, R=%.1f, meanR=%.2f, med=%.6e, IQR=%.6e)",
                    name, n, rankSum, meanRank, median, iqr);
        }
    }

    public static final class KruskalWallisResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private final double h0;
        private final double tieCorrection;
        private final double h;
        private final int df;
        private final double pValue;
        private final double epsilonSquared;
        private final Map<String, GroupSummary> groupSummaries;
        private final List<DunnPostHocTest.DunnComparison> comparisons;

        public KruskalWallisResult(
                double h0,
                double tieCorrection,
                double h,
                int df,
                double pValue,
                double epsilonSquared,
                Map<String, GroupSummary> groupSummaries,
                List<DunnPostHocTest.DunnComparison> comparisons) {
            this.h0 = h0;
            this.tieCorrection = tieCorrection;
            this.h = h;
            this.df = df;
            this.pValue = pValue;
            this.epsilonSquared = epsilonSquared;
            this.groupSummaries = Collections.unmodifiableMap(groupSummaries);
            this.comparisons = Collections.unmodifiableList(comparisons);
        }

        public double getH0() { return h0; }
        public double getTieCorrection() { return tieCorrection; }
        public double getH() { return h; }
        public int getDf() { return df; }
        public double getPValue() { return pValue; }
        public double getEpsilonSquared() { return epsilonSquared; }
        public Map<String, GroupSummary> getGroupSummaries() { return groupSummaries; }
        public List<DunnPostHocTest.DunnComparison> getComparisons() { return comparisons; }

        public boolean isSignificant(double alpha) {
            return pValue < alpha;
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "Kruskal-Wallis: H=%.4f (H0=%.4f, C=%.6f), df=%d, p=%.6e, eps2=%.4f",
                    h, h0, tieCorrection, df, pValue, epsilonSquared);
        }
    }

    private static final class Observation {
        final String group;
        final double value;
        double rank;

        Observation(String group, double value) {
            this.group = group;
            this.value = value;
        }
    }

    /**
     * Executa o teste de Kruskal-Wallis com pós-teste de Dunn-Holm sobre grupos de dados.
     *
     * @param groups mapa de nome do grupo para lista de valores double observados
     * @return resultado estruturado KruskalWallisResult
     */
    public static KruskalWallisResult test(Map<String, List<Double>> groups) {
        if (groups == null || groups.size() < 2) {
            throw new IllegalArgumentException("At least 2 groups are required for Kruskal-Wallis test");
        }

        List<Observation> allObs = new ArrayList<>();
        Map<String, List<Double>> validGroups = new LinkedHashMap<>();

        for (Map.Entry<String, List<Double>> entry : groups.entrySet()) {
            String name = entry.getKey();
            List<Double> raw = entry.getValue();
            if (raw == null || raw.isEmpty()) {
                throw new IllegalArgumentException("Group '" + name + "' is empty or null");
            }
            List<Double> clean = new ArrayList<>();
            for (Double d : raw) {
                if (d != null && !Double.isNaN(d) && !Double.isInfinite(d)) {
                    clean.add(d);
                    allObs.add(new Observation(name, d));
                }
            }
            if (clean.isEmpty()) {
                throw new IllegalArgumentException("Group '" + name + "' contains no valid finite numbers");
            }
            validGroups.put(name, clean);
        }

        int k = validGroups.size();
        int nTotal = allObs.size();
        if (k < 2 || nTotal < k) {
            throw new IllegalArgumentException("Insufficient data points: k=" + k + ", N=" + nTotal);
        }

        // Ordenação global para cálculo de midranks e detecção de empates
        allObs.sort(Comparator.comparingDouble(o -> o.value));

        long tieSum = 0L;
        int i = 0;
        while (i < nTotal) {
            int j = i;
            while (j + 1 < nTotal && Double.compare(allObs.get(j + 1).value, allObs.get(i).value) == 0) {
                j++;
            }
            int tieCount = j - i + 1;
            // Ranks baseados em 1-index: posições de (i + 1) até (j + 1)
            double midRank = ( (i + 1) + (j + 1) ) / 2.0;
            for (int idx = i; idx <= j; idx++) {
                allObs.get(idx).rank = midRank;
            }
            if (tieCount > 1) {
                tieSum += (long) tieCount * tieCount * tieCount - tieCount;
            }
            i = j + 1;
        }

        // Soma de postos por grupo
        Map<String, Double> rankSums = new LinkedHashMap<>();
        for (String g : validGroups.keySet()) {
            rankSums.put(g, 0.0);
        }
        for (Observation obs : allObs) {
            rankSums.put(obs.group, rankSums.get(obs.group) + obs.rank);
        }

        // H0 não corrigido: (12 / (N * (N + 1))) * sum(R_g^2 / n_g) - 3 * (N + 1)
        double sumR2OverN = 0.0;
        for (Map.Entry<String, List<Double>> entry : validGroups.entrySet()) {
            String g = entry.getKey();
            int nG = entry.getValue().size();
            double rG = rankSums.get(g);
            sumR2OverN += (rG * rG) / nG;
        }

        double h0 = (12.0 / ((double) nTotal * (nTotal + 1))) * sumR2OverN - 3.0 * (nTotal + 1);

        // Fator de correção de empates C = 1 - sum(t^3 - t) / (N^3 - N)
        double denomC = (double) nTotal * nTotal * nTotal - nTotal;
        double c = 1.0 - ((double) tieSum / denomC);

        double h;
        double pValue;
        int df = k - 1;

        if (c <= 1e-15) {
            // Caso degenerado: todas as observações são exatamente iguais (C = 0)
            h = 0.0;
            pValue = 1.0;
        } else {
            h = h0 / c;
            // Garantia contra pequenas imprecisões numéricas IEEE 754
            if (h < 0.0 && h > -1e-12) {
                h = 0.0;
            }
            pValue = 1.0 - chiSquareCdf(h, df);
        }

        // Tamanho de efeito epsilon-quadrado: eps2 = (H - k + 1) / (N - k)
        // ou formula de Kelley / Tomczak & Tomczak: epsR2 = H / (N - 1)
        double eps2 = (nTotal > 1) ? Math.max(0.0, Math.min(1.0, h / (nTotal - 1))) : 0.0;

        // Resumos dos grupos
        Map<String, GroupSummary> summaries = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> entry : validGroups.entrySet()) {
            String g = entry.getKey();
            List<Double> vals = entry.getValue();
            int nG = vals.size();
            double rG = rankSums.get(g);
            double meanR = rG / nG;

            List<Double> sortedVals = new ArrayList<>(vals);
            Collections.sort(sortedVals);
            double median = computeQuantile(sortedVals, 0.50);
            double q1 = computeQuantile(sortedVals, 0.25);
            double q3 = computeQuantile(sortedVals, 0.75);
            double iqr = q3 - q1;

            summaries.put(g, new GroupSummary(g, nG, rG, meanR, median, iqr));
        }

        // Pós-teste de Dunn-Holm
        List<DunnPostHocTest.DunnComparison> comparisons = DunnPostHocTest.compute(validGroups, summaries, nTotal, tieSum);

        return new KruskalWallisResult(h0, c, h, df, pValue, eps2, summaries, comparisons);
    }

    private static double computeQuantile(List<Double> sorted, double p) {
        int n = sorted.size();
        if (n == 1) return sorted.get(0);
        double pos = p * (n - 1);
        int low = (int) Math.floor(pos);
        int high = (int) Math.ceil(pos);
        double weight = pos - low;
        return sorted.get(low) * (1.0 - weight) + sorted.get(high) * weight;
    }

    /**
     * Função de distribuição acumulada Qui-Quadrado (CDF) com `df` graus de liberdade,
     * calculada via função Gama incompleta regularizada P(df/2, x/2).
     */
    public static double chiSquareCdf(double x, int df) {
        if (x <= 0.0 || df <= 0) {
            return 0.0;
        }
        return regularizedGammaP(df / 2.0, x / 2.0);
    }

    /**
     * Função Gama incompleta regularizada P(a, x) = gamma(a, x) / Gamma(a).
     * Implementação numérica de alta precisão (Abramowitz & Stegun / Numerical Recipes).
     */
    public static double regularizedGammaP(double a, double x) {
        if (x < 0.0 || a <= 0.0) {
            return 0.0;
        }
        if (x == 0.0) {
            return 0.0;
        }
        if (x < a + 1.0) {
            // Expansão em série de Taylor
            double sum = 1.0 / a;
            double term = sum;
            double n = 1.0;
            while (n < 200.0) {
                term *= x / (a + n);
                sum += term;
                if (Math.abs(term) < Math.abs(sum) * 1e-15) {
                    break;
                }
                n += 1.0;
            }
            return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
        } else {
            // Fração contínua (Legendre / Gauss) para Q(a, x) = 1 - P(a, x)
            double b = x + 1.0 - a;
            double c = 1.0 / 1e-30;
            double d = 1.0 / b;
            double h = d;
            int i = 1;
            while (i < 200) {
                double an = -i * (i - a);
                b += 2.0;
                d = an * d + b;
                if (Math.abs(d) < 1e-30) d = 1e-30;
                c = b + an / c;
                if (Math.abs(c) < 1e-30) c = 1e-30;
                d = 1.0 / d;
                double del = d * c;
                h *= del;
                if (Math.abs(del - 1.0) < 1e-15) {
                    break;
                }
                i++;
            }
            double q = Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
            return Math.max(0.0, Math.min(1.0, 1.0 - q));
        }
    }

    /**
     * Algoritmo de Lanczos para log(Gamma(x)).
     */
    public static double logGamma(double x) {
        double[] c = {
                57.1562356658629235,
                -59.5979603554754912,
                14.1360979747417471,
                -0.491913816097620199,
                0.339946499848118887e-4,
                0.465236289270075418e-4,
                -0.983744753048795646e-4,
                0.158088703224378714e-3,
                -0.210264441772428613e-3,
                0.217439618115212643e-3,
                -0.164318106536763890e-3,
                0.844182239838527433e-4,
                -0.261908384015814087e-4,
                0.368991826595316234e-5
        };
        double y = x;
        double tmp = x + 5.2421875;
        tmp = (x + 0.5) * Math.log(tmp) - tmp;
        double ser = 0.999999999999997092;
        for (int j = 0; j < c.length; j++) {
            y += 1.0;
            ser += c[j] / y;
        }
        return tmp + Math.log(2.5066282746310005 * ser / x);
    }
}


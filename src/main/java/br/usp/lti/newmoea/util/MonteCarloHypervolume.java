package br.usp.lti.newmoea.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Estimador de Hipervolume por Amostragem Monte Carlo i.i.d. uniforme com semente
 * determinística reproduzível, cálculo exato de variância amostral, erro padrão (SE)
 * e Intervalo de Confiança Binomial de Wilson a 95%.
 *
 * <p>Propriedades estatísticas comprovadas:
 * <ul>
 *   <li>Caixa delimitadora canônica: $[\mathbf{l}, \mathbf{r}]$ com volume $V = \prod (r_i - l_i)$.</li>
 *   <li>Variável de Bernoulli: $I_k = 1$ se o ponto amostrado $\mathbf{u}_k$ for dominado por
 *       algum ponto da frente; $0$ caso contrário.</li>
 *   <li>Estimador não-viesado: $\hat{p} = X / S \implies \mathbb{E}[\widehat{HV}] = V \cdot p = HV$.</li>
 *   <li>Variância amostral e erro padrão: $\text{Var}(\widehat{HV}) = V^2 \frac{p(1-p)}{S}$,
 *       $\text{SE} = V \sqrt{\frac{\hat{p}(1-\hat{p})}{S}}$.</li>
 *   <li>Intervalo de Wilson Score ($1 - \alpha = 0.95$, $z = 1.95996398454$):
 *       quando $X = 0$, o limite superior de Wilson é estritamente positivo:
 *       $CI_{upper} = V \frac{z^2}{S + z^2} > 0$, prevenindo falsos zeros absolutos.</li>
 * </ul>
 */
public final class MonteCarloHypervolume {

    public static final double Z_95 = 1.959963984540054; // z para 95% bicaudal
    public static final long DEFAULT_SAMPLE_COUNT = 1_000_000L; // 10^6 amostras

    private MonteCarloHypervolume() {
        // Utilitário estático
    }

    /**
     * Calcula o hipervolume por Monte Carlo com caixa delimitadora explícita [lower, upper].
     *
     * @param front matriz de pontos da frente (N x m)
     * @param lowerBound vetor de limites inferiores l (tamanho m)
     * @param referencePoint vetor de referência r (tamanho m, upper bound)
     * @param sampleCount quantidade de amostras S (deve ser > 0)
     * @param seed semente pseudo-aleatória determinística
     * @return HypervolumeResult estruturado com métricas e IC Wilson
     */
    public static HypervolumeResult compute(
            double[][] front,
            double[] lowerBound,
            double[] referencePoint,
            long sampleCount,
            long seed) {

        long startWall = System.currentTimeMillis();

        if (referencePoint == null || referencePoint.length == 0) {
            throw new IllegalArgumentException("Reference point cannot be null or empty");
        }
        int m = referencePoint.length;

        double[] lower = lowerBound != null ? lowerBound.clone() : new double[m];
        if (lower.length != m) {
            throw new IllegalArgumentException("lowerBound length (" + lower.length + ") does not match referencePoint length (" + m + ")");
        }

        // Validação da caixa delimitadora
        double boxVolume = 1.0;
        double[] span = new double[m];
        for (int i = 0; i < m; i++) {
            if (Double.isNaN(referencePoint[i]) || Double.isInfinite(referencePoint[i]) ||
                Double.isNaN(lower[i]) || Double.isInfinite(lower[i])) {
                throw new IllegalArgumentException("Coordinates of bounding box must be finite");
            }
            if (referencePoint[i] <= lower[i]) {
                return HypervolumeResult.degenerateBox(lower, referencePoint);
            }
            span[i] = referencePoint[i] - lower[i];
            boxVolume *= span[i];
        }

        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be strictly positive: " + sampleCount);
        }

        // Se a frente for nula ou vazia, HV é 0
        if (front == null || front.length == 0) {
            return HypervolumeResult.emptyFront(referencePoint);
        }

        // Filtrar pontos válidos que estritamente dominam ao menos parte do espaço delimitado por r
        List<double[]> contributingPointsList = new ArrayList<>(front.length);
        for (double[] point : front) {
            if (point == null || point.length != m) {
                continue;
            }
            boolean valid = true;
            boolean dominatesBoxPart = true;
            for (int i = 0; i < m; i++) {
                if (Double.isNaN(point[i]) || Double.isInfinite(point[i])) {
                    valid = false;
                    break;
                }
                // Se point[i] >= referencePoint[i], o ponto não pode dominar nenhum ponto no interior de [l, r]
                if (point[i] >= referencePoint[i]) {
                    dominatesBoxPart = false;
                    break;
                }
            }
            if (valid && dominatesBoxPart) {
                contributingPointsList.add(point);
            }
        }

        if (contributingPointsList.isEmpty()) {
            return calculateZeroHitResult(boxVolume, sampleCount, seed, lower, referencePoint, startWall);
        }

        double[][] activePoints = contributingPointsList.toArray(new double[0][]);
        int nPoints = activePoints.length;

        // Amostragem determinística com SplittableRandom
        SplittableRandom rng = new SplittableRandom(seed);
        long hitCount = 0;

        double[] sample = new double[m];
        for (long s = 0; s < sampleCount; s++) {
            for (int i = 0; i < m; i++) {
                sample[i] = lower[i] + rng.nextDouble() * span[i];
            }

            // Teste de dominância: sample é dominado se existe p tal que p[i] <= sample[i] para todo i
            for (int p = 0; p < nPoints; p++) {
                double[] pt = activePoints[p];
                boolean dominated = true;
                for (int i = 0; i < m; i++) {
                    if (pt[i] > sample[i]) {
                        dominated = false;
                        break;
                    }
                }
                if (dominated) {
                    hitCount++;
                    break; // Amostra já dominada, passa para a próxima amostra
                }
            }
        }

        long elapsedWall = System.currentTimeMillis() - startWall;

        double pHat = (double) hitCount / (double) sampleCount;
        double hvHat = boxVolume * pHat;

        // Erro padrão da estimativa de proporção
        double seP = Math.sqrt((pHat * (1.0 - pHat)) / (double) sampleCount);
        double seHv = boxVolume * seP;

        // Intervalo de Confiança de Wilson Score a 95%
        double z = Z_95;
        double z2 = z * z;
        double nTilde = (double) sampleCount + z2;
        double pTilde = ((double) hitCount + 0.5 * z2) / nTilde;
        double margin = (z / nTilde) * Math.sqrt(
                ((double) hitCount * (double) (sampleCount - hitCount)) / (double) sampleCount + (z2 / 4.0));

        double ciLowerP = Math.max(0.0, pTilde - margin);
        double ciUpperP = Math.min(1.0, pTilde + margin);

        double ciLowerHv = boxVolume * ciLowerP;
        double ciUpperHv = boxVolume * ciUpperP;

        return new HypervolumeResult(
                hvHat,
                seHv,
                ciLowerHv,
                ciUpperHv,
                0.95,
                HypervolumeResult.MethodType.MONTE_CARLO_WILSON,
                HypervolumeResult.ExecutionStatus.DIRECT_MC,
                elapsedWall,
                elapsedWall, // wall time aproximado como CPU
                sampleCount,
                hitCount,
                seed,
                lower,
                referencePoint,
                String.format(java.util.Locale.US,
                        "Monte Carlo S=%d, X=%d, pHat=%.6e, SE=%.6e, Wilson 95%%=[%.6e, %.6e]",
                        sampleCount, hitCount, pHat, seHv, ciLowerHv, ciUpperHv)
        );
    }

    private static HypervolumeResult calculateZeroHitResult(
            double boxVolume,
            long sampleCount,
            long seed,
            double[] lower,
            double[] referencePoint,
            long startWall) {
        long elapsedWall = System.currentTimeMillis() - startWall;
        double z = Z_95;
        double z2 = z * z;
        double nTilde = (double) sampleCount + z2;
        double pUpper = z2 / nTilde; // Limite superior Wilson para X = 0
        double ciUpperHv = boxVolume * pUpper;

        return new HypervolumeResult(
                0.0,
                0.0,
                0.0,
                ciUpperHv,
                0.95,
                HypervolumeResult.MethodType.MONTE_CARLO_WILSON,
                HypervolumeResult.ExecutionStatus.DIRECT_MC,
                elapsedWall,
                elapsedWall,
                sampleCount,
                0L,
                seed,
                lower,
                referencePoint,
                String.format(java.util.Locale.US,
                        "Zero hits in Monte Carlo: S=%d, X=0, Wilson upper bound=%.6e (non-zero)",
                        sampleCount, ciUpperHv)
        );
    }
}


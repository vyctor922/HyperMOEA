package br.usp.lti.newmoea.util;

import br.usp.lti.newmoea.study.StaticParams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume;
import org.uma.jmetal.util.NormalizeUtils;
import org.uma.jmetal.util.VectorUtils;

/**
 * Contrato unificado e verificável de normalização de objetivos, hipervolume exato ({@code HV} e
 * {@code NHV}), invariância de escala pelo determinante Jacobiano e semântica de {@code IGD},
 * {@code IGD+} e {@code R2} no projeto {@code hypeMOEA} (Tarefa {@code T17}).
 *
 * <p>Resolve e documenta formalmente:
 * <ol>
 *   <li><b>Três modos explícitos de normalização</b> ({@link NormalizationMode}):
 *       <ul>
 *         <li>{@link NormalizationMode#EMPIRICAL_REFERENCE_MIN_MAX}: extremos amostrais observados
 *             na matriz da frente de referência (comportamento padrão de
 *             {@code ComputeQualityIndicators} + {@link NormalizeUtils#normalize(double[][])}, que
 *             colapsa em frentes de referência esparsas como {@code WFG4.15D.csv});</li>
 *         <li>{@link NormalizationMode#THEORETICAL_PARETO_BOUNDS}: extremos analíticos exatos da
 *             frente de Pareto verdadeira do problema (\(a_j = z_j^{\text{ideal}}\),
 *             \(b_j = z_j^{\text{nadir}}\)), independentes da amostragem do CSV e constantes entre
 *             execuções e algoritmos;</li>
 *         <li>{@link NormalizationMode#HYBRID_ENVELOPE_BOUNDS}: envoltória conservadora
 *             \(a_j = \min(a_j^{\text{theo}}, \min(R_j))\) e
 *             \(b_j = \max(b_j^{\text{theo}}, \max(R_j))\), garantindo que mesmo frentes de
 *             referência legadas com extrapolação (como {@code WFG3.2D.csv}, onde \(f_1 \in [0, 3]\)
 *             e \(f_2 \in [-2, 4]\), ou {@code WFG1.5D.csv} com \(\max f_5 = 10.137\)) fiquem
 *             integralmente contidas em \([0, 1]^m\).</li>
 *       </ul>
 *   </li>
 *   <li><b>Relação exata via determinante Jacobiano</b>
 *       \(\det(J_T^{-1}) = \prod_{j=1}^m (b_j - a_j)\) entre o hipervolume no espaço normalizado
 *       com ponto de referência \(\tilde{\mathbf{r}} = T(\mathbf{r}_{\text{raw}})\) e o hipervolume
 *       no espaço bruto com ponto de referência \(\mathbf{r}_{\text{raw}}\).</li>
 *   <li><b>Derivação em \(O(1)\) de {@code NHV} a partir de {@code HV(A)} e {@code HV(R)}</b>:
 *       \(\text{NHV}(A) = 1.0 - \frac{\text{HV}(A)}{\text{HV}(R)}\), eliminando a recomputação
 *       redundante de {@code PISAHypervolume.compute(front)} e {@code PISAHypervolume.compute(ref)}
 *       em {@link NormalizedHypervolume#compute(double[][])} sem perder equivalência numérica de
 *       ponto flutuante IEEE 754 (\(\le 10^{-15}\)) e preservando invariância exata da estatística
 *       \(H\) de Kruskal-Wallis.</li>
 *   <li><b>Contrato de {@code R2}, {@code IGD} e {@code IGD+}</b>: sem truncamento/clipping
 *       silencioso de soluções fora de \([a_j, b_j]\) em indicadores baseados em distância/utilidade,
 *       e com ponto ideal \(\mathbf{z}^* = (0, \dots, 0)\) e vetores de peso de {@code R2} fixos por
 *       dimensão \(m\).</li>
 * </ol>
 */
public final class ObjectiveNormalizationContract {

    /** Tolerância mínima para detecção de intervalo degenerado (\(b_j - a_j \le \varepsilon\)). */
    public static final double DEGENERACY_EPSILON = 1e-12;

    /**
     * Máximo global de \(x_j \in [0, 1]\) (\(j < m\)) na frente de Pareto desconexa de {@code DTLZ7}
     * (\(\psi'(x_3^*) = 0\), onde \(\psi(x) = \frac{x}{2}(1 + \sin(3\pi x))\)).
     */
    public static final double DTLZ7_PARETO_MAX_XJ = 0.8594008566447238;

    /**
     * Valor máximo de \(\psi(x) = \frac{x}{2}(1 + \sin(3\pi x))\) atingido em
     * \(x_3^* \approx 0.8594008566447238\), utilizado para o limite inferior exato de \(f_m\) em
     * {@code DTLZ7}: \(\min f_m^* = 2(m - (m - 1)\psi(x_3^*))\).
     */
    public static final double DTLZ7_MAX_PSI =
            0.5 * DTLZ7_PARETO_MAX_XJ * (1.0 + Math.sin(3.0 * Math.PI * DTLZ7_PARETO_MAX_XJ));

    private ObjectiveNormalizationContract() {
        // Classe utilitária
    }

    /**
     * Modos de definição dos limites de normalização afim \(T_j(f_j) = \frac{f_j - a_j}{b_j - a_j}\).
     */
    public enum NormalizationMode {
        /**
         * Usa exclusivamente os extremos amostrais observados na matriz da frente de referência
         * (\(a_j = \min_i R_{i,j}\), \(b_j = \max_i R_{i,j}\)). Reproduz o comportamento nativo de
         * {@code ComputeQualityIndicators} no jMetal 7.8.
         */
        EMPIRICAL_REFERENCE_MIN_MAX,

        /**
         * Usa exclusivamente os limites teóricos analíticos da frente de Pareto verdadeira do
         * problema (\(a_j = z_j^{\text{ideal}}\), \(b_j = z_j^{\text{nadir}}\)), garantindo que a
         * régua de normalização seja constante e imune à subamostragem de extremos em CSVs esparsos
         * (como {@code WFG4.15D.csv}).
         */
        THEORETICAL_PARETO_BOUNDS,

        /**
         * Usa a envoltória \(a_j = \min(a_j^{\text{theo}}, \min_i R_{i,j})\) e
         * \(b_j = \max(b_j^{\text{theo}}, \max_i R_{i,j})\), cobrindo simultaneamente o domínio
         * teórico da frente de Pareto e eventuais pontos extrapolados em arquivos históricos (como
         * {@code WFG3.2D.csv} e {@code WFG1.5D.csv}).
         */
        HYBRID_ENVELOPE_BOUNDS
    }

    /**
     * Especificação imutável de normalização afim por coordenada:
     * \(T_j(f_j) = \frac{f_j - a_j}{b_j - a_j}\).
     *
     * @param problemId identificador canônico do problema (ex.: {@code "WFG4"}, {@code "DTLZ7"})
     * @param m número de objetivos (\(m \ge 2\))
     * @param mode modo de normalização utilizado
     * @param lowerBounds vetor \(\mathbf{a} = (a_1, \dots, a_m)\) de limites inferiores
     * @param upperBounds vetor \(\mathbf{b} = (b_1, \dots, b_m)\) de limites superiores (\(b_j > a_j\))
     * @param rawHvReferencePoint ponto de referência de hipervolume no espaço bruto \(\mathbf{r}_{\text{raw}}\)
     * @param normalizedHvReferencePoint imagem do ponto de referência no espaço normalizado \(\tilde{\mathbf{r}} = T(\mathbf{r}_{\text{raw}})\)
     * @param jacobianDeterminant determinante Jacobiano da transformação inversa \(\prod_{j=1}^m (b_j - a_j) > 0\)
     */
    public record NormalizationBounds(
            String problemId,
            int m,
            NormalizationMode mode,
            double[] lowerBounds,
            double[] upperBounds,
            double[] rawHvReferencePoint,
            double[] normalizedHvReferencePoint,
            double jacobianDeterminant) {

        public NormalizationBounds {
            Objects.requireNonNull(problemId, "problemId não pode ser nulo.");
            Objects.requireNonNull(mode, "mode não pode ser nulo.");
            Objects.requireNonNull(lowerBounds, "lowerBounds não pode ser nulo.");
            Objects.requireNonNull(upperBounds, "upperBounds não pode ser nulo.");
            Objects.requireNonNull(rawHvReferencePoint, "rawHvReferencePoint não pode ser nulo.");
            Objects.requireNonNull(
                    normalizedHvReferencePoint, "normalizedHvReferencePoint não pode ser nulo.");
            if (m < 2) {
                throw new IllegalArgumentException("O número de objetivos m deve ser >= 2: " + m);
            }
            if (lowerBounds.length != m
                    || upperBounds.length != m
                    || rawHvReferencePoint.length != m
                    || normalizedHvReferencePoint.length != m) {
                throw new IllegalArgumentException(
                        "Todos os vetores de limites e pontos de referência devem ter dimensão m=" + m);
            }
            validateNonDegenerateBounds(lowerBounds, upperBounds);
            if (!Double.isFinite(jacobianDeterminant) || jacobianDeterminant <= 0.0) {
                throw new IllegalArgumentException(
                        "O determinante Jacobiano deve ser finito e estritamente positivo: "
                                + jacobianDeterminant);
            }
            lowerBounds = lowerBounds.clone();
            upperBounds = upperBounds.clone();
            rawHvReferencePoint = rawHvReferencePoint.clone();
            normalizedHvReferencePoint = normalizedHvReferencePoint.clone();
        }

        @Override
        public double[] lowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public double[] upperBounds() {
            return upperBounds.clone();
        }

        @Override
        public double[] rawHvReferencePoint() {
            return rawHvReferencePoint.clone();
        }

        @Override
        public double[] normalizedHvReferencePoint() {
            return normalizedHvReferencePoint.clone();
        }
    }

    /**
     * Diagnóstico de soluções da frente aproximada em relação à caixa de normalização \([a_j, b_j]\)
     * e ao ponto de referência de hipervolume \(\mathbf{r}\).
     *
     * @param totalPoints total de pontos na frente avaliada
     * @param pointsBelowLowerBound pontos com alguma coordenada \(f_j < a_j\) (\(\tilde{f}_j < 0\))
     * @param pointsAboveUpperBound pontos com alguma coordenada \(f_j > b_j\) (\(\tilde{f}_j > 1\))
     * @param pointsOutsideUnitHvBox pontos que não dominam estritamente \(\tilde{\mathbf{r}}_{\text{unit}} = (1, \dots, 1)\)
     *     (ou têm \(\tilde{f}_j \ge 1.0\), cuja contribuição em {@link PISAHypervolume} é truncada a zero)
     * @param pointsOutsideRawHvReferencePoint pontos que não dominam estritamente \(\mathbf{r}_{\text{raw}}\)
     *     (isto é, possuem \(f_j \ge r_{\text{raw}, j}\) em pelo menos um objetivo)
     * @param minNormalizedByObjective menor coordenada normalizada observada em cada objetivo
     * @param maxNormalizedByObjective maior coordenada normalizada observada em cada objetivo
     */
    public record FrontBoxDiagnostics(
            int totalPoints,
            int pointsBelowLowerBound,
            int pointsAboveUpperBound,
            int pointsOutsideUnitHvBox,
            int pointsOutsideRawHvReferencePoint,
            double[] minNormalizedByObjective,
            double[] maxNormalizedByObjective) {

        public FrontBoxDiagnostics {
            minNormalizedByObjective =
                    minNormalizedByObjective != null ? minNormalizedByObjective.clone() : new double[0];
            maxNormalizedByObjective =
                    maxNormalizedByObjective != null ? maxNormalizedByObjective.clone() : new double[0];
        }

        @Override
        public double[] minNormalizedByObjective() {
            return minNormalizedByObjective.clone();
        }

        @Override
        public double[] maxNormalizedByObjective() {
            return maxNormalizedByObjective.clone();
        }
    }

    /**
     * Resultado unificado e auditável dos 5 indicadores de qualidade sobre uma frente aproximada,
     * juntamente com o hipervolume da referência, o hipervolume no espaço bruto via Jacobiano e o
     * diagnóstico de domínio.
     *
     * @param hvPisaUnitBox hipervolume {@link PISAHypervolume} no espaço normalizado com ponto de
     *     referência unitário \((1, \dots, 1)\) (maximização)
     * @param referenceHvPisaUnitBox hipervolume da frente de referência \(R\) na mesma caixa unitária
     *     (\(c = \text{HV}(R) > 0\))
     * @param nhvDerivedO1 hipervolume normalizado derivado em \(O(1)\):
     *     \(\text{NHV}(A) = 1.0 - \frac{\text{HV}(A)}{\text{HV}(R)}\) (minimização)
     * @param hvNormalizedAtScaledRef hipervolume exato no espaço normalizado integrado até
     *     \(\tilde{\mathbf{r}} = T(\mathbf{r}_{\text{raw}})\)
     * @param hvRawAtReferencePoint hipervolume exato no espaço bruto integrado até
     *     \(\mathbf{r}_{\text{raw}}\), igual a
     *     \(\det(J_T^{-1}) \cdot \text{HV}_{\text{norm}}(\tilde{A}; \tilde{\mathbf{r}})\)
     * @param igdL2Average {@link InvertedGenerationalDistance} padrão do jMetal 7.8
     *     (\(\text{pow} = 2.0\): \(\frac{1}{|R|} \sqrt{\sum_{\mathbf{r} \in R} d(\mathbf{r}, A)^2}\))
     * @param igdMeanEuclidean distância geracional invertida clássica (\(\text{pow} = 1.0\):
     *     \(\frac{1}{|R|} \sum_{\mathbf{r} \in R} d(\mathbf{r}, A)\))
     * @param igdPlus {@link InvertedGenerationalDistancePlus} (\(\frac{1}{|R|} \sum_{\mathbf{r} \in R} d^+(\mathbf{r}, A)\))
     * @param r2 indicador {@link R2} com ponto ideal fixo \(\mathbf{z}^*\) e vetores de peso uniformes fixos para \(m\)
     * @param diagnostics diagnóstico de pontos dentro/fora de \([a_j, b_j]\) e \(\mathbf{r}\)
     */
    public record UnifiedIndicatorEvaluation(
            double hvPisaUnitBox,
            double referenceHvPisaUnitBox,
            double nhvDerivedO1,
            double hvNormalizedAtScaledRef,
            double hvRawAtReferencePoint,
            double igdL2Average,
            double igdMeanEuclidean,
            double igdPlus,
            double r2,
            FrontBoxDiagnostics diagnostics) {}

    /**
     * Manifesto reprodutível do contrato de normalização e semântica dos indicadores para uma
     * configuração \((P, m)\).
     *
     * @param contractVersion versão do contrato ({@code "T17-1.0"})
     * @param problemId identificador canônico do problema
     * @param m número de objetivos
     * @param normalizationMode modo de normalização adotado
     * @param lowerBounds limites inferiores \(\mathbf{a}\)
     * @param upperBounds limites superiores \(\mathbf{b}\)
     * @param rawHvReferencePoint ponto de referência \(\mathbf{r}_{\text{raw}}\)
     * @param normalizedHvReferencePoint ponto de referência transformado \(\tilde{\mathbf{r}} = T(\mathbf{r}_{\text{raw}})\)
     * @param jacobianDeterminant determinante Jacobiano \(\prod_{j=1}^m (b_j - a_j)\)
     * @param referenceFrontSha256 hash SHA-256 do arquivo da frente de referência (ou {@code "IN_MEMORY"} se em memória)
     * @param referenceFrontCardinality número de linhas da frente de referência
     * @param referenceHvUnitBox valor pré-computado \(c = \text{HV}(R)\) na caixa unitária
     * @param igdEffectiveFormula descrição explícita da fórmula de IGD em vigor
     */
    public record IndicatorContractManifest(
            String contractVersion,
            String problemId,
            int m,
            NormalizationMode normalizationMode,
            double[] lowerBounds,
            double[] upperBounds,
            double[] rawHvReferencePoint,
            double[] normalizedHvReferencePoint,
            double jacobianDeterminant,
            String referenceFrontSha256,
            int referenceFrontCardinality,
            double referenceHvUnitBox,
            String igdEffectiveFormula) {}

    /**
     * Resultado da verificação de invariância de postos e da estatística \(H\) de Kruskal-Wallis
     * entre {@code HV} (maximização) e {@code NHV} (minimização).
     *
     * @param algorithmCount número de grupos/algoritmos comparados (\(K\))
     * @param totalObservations total de observações (\(N\))
     * @param kruskalWallisHForHv estatística \(H\) de Kruskal-Wallis calculada sobre {@code HV}
     * @param kruskalWallisHForNhv estatística \(H\) de Kruskal-Wallis calculada sobre {@code NHV}
     * @param absoluteHDifference diferença absoluta \(|H_{\text{HV}} - H_{\text{NHV}}|\)
     * @param ranksExactlyInverted {@code true} se para toda observação \(i\) vale
     *     \(R_i^{\text{NHV}} = N + 1 - R_i^{\text{HV}}\)
     */
    public record KruskalWallisInvarianceReport(
            int algorithmCount,
            int totalObservations,
            double kruskalWallisHForHv,
            double kruskalWallisHForNhv,
            double absoluteHDifference,
            boolean ranksExactlyInverted) {}

    /**
     * Valida que os vetores de limites inferiores \(\mathbf{a}\) e superiores \(\mathbf{b}\) possuem
     * coordenadas finitas e satisfazem estritamente \(b_j - a_j > \varepsilon\) em todos os objetivos.
     *
     * @param lowerBounds limites inferiores \(\mathbf{a}\)
     * @param upperBounds limites superiores \(\mathbf{b}\)
     * @throws IllegalArgumentException se os vetores forem nulos, de tamanhos distintos, não finitos
     *     ou degenerados (\(b_j - a_j \le \varepsilon\))
     */
    public static void validateNonDegenerateBounds(double[] lowerBounds, double[] upperBounds) {
        if (lowerBounds == null || upperBounds == null) {
            throw new IllegalArgumentException("Os vetores de limites não podem ser nulos.");
        }
        if (lowerBounds.length == 0 || lowerBounds.length != upperBounds.length) {
            throw new IllegalArgumentException(
                    "Dimensões inválidas para lowerBounds ("
                            + lowerBounds.length
                            + ") e upperBounds ("
                            + upperBounds.length
                            + ").");
        }
        for (int j = 0; j < lowerBounds.length; j++) {
            double a = lowerBounds[j];
            double b = upperBounds[j];
            if (!Double.isFinite(a) || !Double.isFinite(b)) {
                throw new IllegalArgumentException(
                        String.format(
                                Locale.US,
                                "Limites não finitos no objetivo j=%d: a=%s, b=%s",
                                j,
                                a,
                                b));
            }
            if (b - a <= DEGENERACY_EPSILON) {
                throw new IllegalArgumentException(
                        String.format(
                                Locale.US,
                                "Intervalo de normalização degenerado no objetivo j=%d: a=%.12g, b=%.12g (b - a = %.12g <= %.1e)",
                                j,
                                a,
                                b,
                                b - a,
                                DEGENERACY_EPSILON));
            }
        }
    }

    /**
     * Retorna os limites inferiores teóricos exatos (\(a_j = z_j^{\text{ideal}}\)) da frente de
     * Pareto verdadeira no espaço bruto de objetivos para {@code WFG1}–{@code WFG9} e
     * {@code DTLZ1}–{@code DTLZ7}.
     *
     * <ul>
     *   <li>{@code WFG1}–{@code WFG9} e {@code DTLZ1}–{@code DTLZ6}: \(a_j = 0.0\) para todo
     *       \(j \in \{1, \dots, m\}\);</li>
     *   <li>{@code DTLZ7}: \(a_j = 0.0\) para \(j \in \{1, \dots, m-1\}\) (escala de caixa padrão é
     *       \(0.0\) também para \(j = m\) no envelope \([0, 2m]\), enquanto o mínimo analítico na PF
     *       é \(2(m - (m - 1)\psi(x_3^*))\); este método retorna \(0.0\) como origem canônica do
     *       ortante positivo \([0, 1]^{m-1} \times [0, 2m]\), e
     *       {@link #getExactParetoFrontMinimums(String, int)} fornece o mínimo exato na variedade).</li>
     * </ul>
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor {@code double[m]} com os limites inferiores teóricos de escala
     */
    public static double[] getTheoreticalLowerBounds(String problemName, int m) {
        StaticParams.normalizeProblemId(problemName);
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2: " + m);
        }
        return new double[m];
    }

    /**
     * Retorna o mínimo analítico exato atingido sobre a variedade da frente de Pareto verdadeira
     * (\(\min_{\mathbf{y} \in PF} y_j\)), distinguindo-o da origem do ortante para {@code DTLZ7} no
     * último objetivo \(f_m\), onde \(\min f_m^* = 2(m - (m - 1)\psi(x_3^*)) > 0\).
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor {@code double[m]} com \(\min_{\mathbf{y} \in PF} y_j\)
     */
    public static double[] getExactParetoFrontMinimums(String problemName, int m) {
        String canonical = StaticParams.normalizeProblemId(problemName);
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2: " + m);
        }
        double[] mins = new double[m];
        if ("DTLZ7".equals(canonical)) {
            mins[m - 1] = 2.0 * (m - (m - 1) * DTLZ7_MAX_PSI);
        }
        return mins;
    }

    /**
     * Retorna os limites superiores teóricos de escala (\(b_j\)) da caixa de Pareto no espaço bruto
     * de objetivos para {@code WFG1}–{@code WFG9} e {@code DTLZ1}–{@code DTLZ7}, consistentes com
     * {@link StaticParams#getParetoFrontUpperBounds(String, int)}.
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor {@code double[m]} com os limites superiores teóricos \(b_j\)
     */
    public static double[] getTheoreticalUpperBounds(String problemName, int m) {
        return StaticParams.getParetoFrontUpperBounds(problemName, m);
    }

    /**
     * Retorna os máximos analíticos exatos na variedade \(g(\mathbf{x}_M) = 0\) para cada problema,
     * explicitando a contração exponencial \(2^{-\max(0, m - 1 - j)/2}\) de {@code DTLZ5} e
     * {@code DTLZ6} (\(j < m\)) e o limite \(x_3^* \approx 0.85940086\) de {@code DTLZ7} (\(j < m\)).
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor {@code double[m]} com \(\max_{\mathbf{y} \in PF_{g=0}} y_j\)
     */
    public static double[] getExactZeroGManifoldMaximums(String problemName, int m) {
        String canonical = StaticParams.normalizeProblemId(problemName);
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2: " + m);
        }
        if ("DTLZ5".equals(canonical) || "DTLZ6".equals(canonical)) {
            double[] maxs = new double[m];
            for (int j = 1; j <= m; j++) {
                if (j == m) {
                    maxs[j - 1] = 1.0;
                } else {
                    int exp = Math.max(0, m - 1 - Math.max(j - 1, 1));
                    maxs[j - 1] = Math.pow(2.0, -0.5 * exp);
                }
            }
            return maxs;
        }
        if ("DTLZ7".equals(canonical)) {
            double[] maxs = new double[m];
            Arrays.fill(maxs, 0, m - 1, DTLZ7_PARETO_MAX_XJ);
            maxs[m - 1] = 2.0 * m;
            return maxs;
        }
        return StaticParams.getParetoFrontUpperBounds(canonical, m);
    }

    /**
     * Constrói o contrato de limites {@link NormalizationBounds} para um problema, dimensão \(m\),
     * modo de normalização e frente de referência opcional.
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos (\(m \ge 2\))
     * @param mode modo de normalização ({@link NormalizationMode})
     * @param referenceFront matriz da frente de referência (obrigatória para
     *     {@link NormalizationMode#EMPIRICAL_REFERENCE_MIN_MAX} e
     *     {@link NormalizationMode#HYBRID_ENVELOPE_BOUNDS}; pode ser {@code null} em
     *     {@link NormalizationMode#THEORETICAL_PARETO_BOUNDS})
     * @return {@link NormalizationBounds} validado
     */
    public static NormalizationBounds resolveBounds(
            String problemName, int m, NormalizationMode mode, double[][] referenceFront) {
        String canonical = StaticParams.normalizeProblemId(problemName);
        Objects.requireNonNull(mode, "O modo de normalização não pode ser nulo.");
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2: " + m);
        }

        double[] lower;
        double[] upper;

        switch (mode) {
            case EMPIRICAL_REFERENCE_MIN_MAX -> {
                validateMatrixDimensions(referenceFront, m, "referenceFront");
                lower = NormalizeUtils.getMinValuesOfTheColumnsOfAMatrix(referenceFront);
                upper = NormalizeUtils.getMaxValuesOfTheColumnsOfAMatrix(referenceFront);
            }
            case THEORETICAL_PARETO_BOUNDS -> {
                lower = getTheoreticalLowerBounds(canonical, m);
                upper = getTheoreticalUpperBounds(canonical, m);
            }
            case HYBRID_ENVELOPE_BOUNDS -> {
                validateMatrixDimensions(referenceFront, m, "referenceFront");
                double[] theoLower = getTheoreticalLowerBounds(canonical, m);
                double[] theoUpper = getTheoreticalUpperBounds(canonical, m);
                double[] empLower = NormalizeUtils.getMinValuesOfTheColumnsOfAMatrix(referenceFront);
                double[] empUpper = NormalizeUtils.getMaxValuesOfTheColumnsOfAMatrix(referenceFront);
                lower = new double[m];
                upper = new double[m];
                for (int j = 0; j < m; j++) {
                    lower[j] = Math.min(theoLower[j], empLower[j]);
                    upper[j] = Math.max(theoUpper[j], empUpper[j]);
                }
            }
            default -> throw new IllegalArgumentException("Modo não suportado: " + mode);
        }

        validateNonDegenerateBounds(lower, upper);
        double[] rawRef = StaticParams.getHypervolumeReferencePoint(canonical, m);
        double[] normRef = transformVector(rawRef, lower, upper);
        double jacobian = computeJacobianDeterminant(lower, upper);

        return new NormalizationBounds(
                canonical, m, mode, lower, upper, rawRef, normRef, jacobian);
    }

    /**
     * Calcula o determinante Jacobiano da transformação afim inversa
     * \(T^{-1}(\tilde{\mathbf{y}}) = \mathbf{a} + \operatorname{diag}(\mathbf{b} - \mathbf{a})\tilde{\mathbf{y}}\):
     *
     * $$\det(J_{T^{-1}}) = \prod_{j=1}^m (b_j - a_j).$$
     *
     * @param lowerBounds limites inferiores \(\mathbf{a}\)
     * @param upperBounds limites superiores \(\mathbf{b}\)
     * @return produto \(\prod_{j=1}^m (b_j - a_j) > 0\)
     */
    public static double computeJacobianDeterminant(double[] lowerBounds, double[] upperBounds) {
        validateNonDegenerateBounds(lowerBounds, upperBounds);
        double det = 1.0;
        for (int j = 0; j < lowerBounds.length; j++) {
            det *= (upperBounds[j] - lowerBounds[j]);
        }
        return det;
    }

    /**
     * Aplica a transformação afim coordenada a coordenada
     * \(T_j(y_j) = \frac{y_j - a_j}{b_j - a_j}\) a um único vetor \(\mathbf{y} \in \mathbb{R}^m\),
     * <b>sem</b> truncar/clipar valores fora de \([0, 1]\).
     *
     * @param vector vetor no espaço bruto
     * @param lowerBounds limites inferiores \(\mathbf{a}\)
     * @param upperBounds limites superiores \(\mathbf{b}\)
     * @return novo vetor transformado \(\tilde{\mathbf{y}} = T(\mathbf{y})\)
     */
    public static double[] transformVector(
            double[] vector, double[] lowerBounds, double[] upperBounds) {
        Objects.requireNonNull(vector, "O vetor a transformar não pode ser nulo.");
        validateNonDegenerateBounds(lowerBounds, upperBounds);
        if (vector.length != lowerBounds.length) {
            throw new IllegalArgumentException(
                    "Dimensão do vetor ("
                            + vector.length
                            + ") difere dos limites ("
                            + lowerBounds.length
                            + ").");
        }
        double[] out = new double[vector.length];
        for (int j = 0; j < vector.length; j++) {
            double val = vector[j];
            if (!Double.isFinite(val)) {
                throw new IllegalArgumentException(
                        "Valor não finito na coordenada j=" + j + ": " + val);
            }
            out[j] = (val - lowerBounds[j]) / (upperBounds[j] - lowerBounds[j]);
        }
        return out;
    }

    /**
     * Aplica a transformação afim \(T_j(y_j) = \frac{y_j - a_j}{b_j - a_j}\) a todos os pontos de
     * uma frente \(A \in \mathbb{R}^{N \times m}\), retornando uma nova matriz sem mutar a entrada e
     * sem aplicar clipping silencioso em coordenadas \(< 0\) ou \(> 1\).
     *
     * @param front matriz da frente no espaço bruto (pode ter \(N = 0\) linhas)
     * @param bounds contrato de limites {@link NormalizationBounds}
     * @return matriz normalizada \(\tilde{A}\)
     */
    public static double[][] normalizeFront(double[][] front, NormalizationBounds bounds) {
        Objects.requireNonNull(bounds, "bounds não pode ser nulo.");
        return normalizeFront(front, bounds.lowerBounds, bounds.upperBounds);
    }

    /**
     * Aplica a transformação afim \(T_j(y_j) = \frac{y_j - a_j}{b_j - a_j}\) a todos os pontos de
     * uma frente \(A \in \mathbb{R}^{N \times m}\).
     *
     * @param front matriz no espaço bruto
     * @param lowerBounds limites inferiores \(\mathbf{a}\)
     * @param upperBounds limites superiores \(\mathbf{b}\)
     * @return nova matriz normalizada \(\tilde{A}\)
     */
    public static double[][] normalizeFront(
            double[][] front, double[] lowerBounds, double[] upperBounds) {
        Objects.requireNonNull(front, "A matriz da frente não pode ser nula.");
        validateNonDegenerateBounds(lowerBounds, upperBounds);
        int m = lowerBounds.length;
        if (front.length == 0) {
            return new double[0][m];
        }
        double[][] normalized = new double[front.length][m];
        for (int i = 0; i < front.length; i++) {
            double[] row = front[i];
            if (row == null || row.length != m) {
                throw new IllegalArgumentException(
                        "Linha "
                                + i
                                + " da frente possui dimensão inválida (esperado m="
                                + m
                                + ").");
            }
            for (int j = 0; j < m; j++) {
                double v = row[j];
                if (!Double.isFinite(v)) {
                    throw new IllegalArgumentException(
                            "Coordenada não finita na linha " + i + ", coluna " + j + ": " + v);
                }
                normalized[i][j] = (v - lowerBounds[j]) / (upperBounds[j] - lowerBounds[j]);
            }
        }
        return normalized;
    }

    /**
     * Inspeciona uma frente no espaço bruto e sua imagem normalizada, contabilizando quantas soluções
     * caem abaixo de \(a_j\), acima de \(b_j\), fora da caixa unitária \((1, \dots, 1)\) do
     * {@link PISAHypervolume} e fora do ponto de referência bruto \(\mathbf{r}_{\text{raw}}\).
     *
     * @param rawFront frente no espaço bruto
     * @param bounds contrato de normalização
     * @return {@link FrontBoxDiagnostics}
     */
    public static FrontBoxDiagnostics diagnoseFront(
            double[][] rawFront, NormalizationBounds bounds) {
        Objects.requireNonNull(rawFront, "rawFront não pode ser nulo.");
        Objects.requireNonNull(bounds, "bounds não pode ser nulo.");
        int m = bounds.m();
        double[][] normFront = normalizeFront(rawFront, bounds);
        double[] rawRef = bounds.rawHvReferencePoint;

        double[] minNorm = new double[m];
        double[] maxNorm = new double[m];
        if (normFront.length == 0) {
            Arrays.fill(minNorm, Double.NaN);
            Arrays.fill(maxNorm, Double.NaN);
            return new FrontBoxDiagnostics(0, 0, 0, 0, 0, minNorm, maxNorm);
        }
        Arrays.fill(minNorm, Double.POSITIVE_INFINITY);
        Arrays.fill(maxNorm, Double.NEGATIVE_INFINITY);

        int belowLower = 0;
        int aboveUpper = 0;
        int outsideUnitHv = 0;
        int outsideRawRef = 0;

        for (int i = 0; i < normFront.length; i++) {
            boolean rowBelow = false;
            boolean rowAbove = false;
            boolean rowOutsideUnit = false;
            boolean rowOutsideRaw = false;
            for (int j = 0; j < m; j++) {
                double nv = normFront[i][j];
                double rv = rawFront[i][j];
                if (nv < minNorm[j]) {
                    minNorm[j] = nv;
                }
                if (nv > maxNorm[j]) {
                    maxNorm[j] = nv;
                }
                if (nv < 0.0) {
                    rowBelow = true;
                }
                if (nv > 1.0) {
                    rowAbove = true;
                }
                // Em PISAHypervolume, VectorUtils.getInvertedFront mapeia nv >= 1.0 para 0.0,
                // e reduceNondominatedSet descarta qualquer ponto com coordenada invertida <= 0.0
                if (nv >= 1.0) {
                    rowOutsideUnit = true;
                }
                if (rv >= rawRef[j]) {
                    rowOutsideRaw = true;
                }
            }
            if (rowBelow) {
                belowLower++;
            }
            if (rowAbove) {
                aboveUpper++;
            }
            if (rowOutsideUnit) {
                outsideUnitHv++;
            }
            if (rowOutsideRaw) {
                outsideRawRef++;
            }
        }

        return new FrontBoxDiagnostics(
                normFront.length,
                belowLower,
                aboveUpper,
                outsideUnitHv,
                outsideRawRef,
                minNorm,
                maxNorm);
    }

    /**
     * Calcula o hipervolume exato de uma frente arbitrária \(A \in \mathbb{R}^{N \times m}\) em
     * relação a um ponto de referência explícito \(\mathbf{r} \in \mathbb{R}^m\) (\(r_j > a_j\)),
     * reduzindo o domínio \([\mathbf{a}, \mathbf{r}]\) à caixa unitária \([0, 1]^m\) exigida pelo
     * algoritmo de varredura recursiva de {@link PISAHypervolume} e multiplicando pelo determinante
     * Jacobiano \(\prod_{j=1}^m (r_j - a_j)\) quando {@code scaleByBoxVolume = true}.
     *
     * <p><b>Nota de bytecode sobre {@code PISAHypervolume(double[])} no jMetal 7.8</b>:
     * O construtor {@code new PISAHypervolume(referencePoint)} armazena uma matriz diagonal
     * \(m \times m\) em {@code this.referenceFront}, mas {@code PISAHypervolume.hypervolume} lê
     * apenas {@code referenceFront[0].length} e chama {@link VectorUtils#getInvertedFront(double[][])},
     * que sempre inverte em relação a \((1.0, \dots, 1.0)\) e trunca coordenadas \(< 0\) para \(1.0\)
     * e \(> 1.0\) para \(0.0\). Portanto, para integrar exatamente sobre a caixa delimitada por
     * \(\mathbf{r}\) sem truncamento espúrio em \(1.0\), cada coordenada deve ser reescalada por
     * \(\hat{y}_j = \frac{y_j - a_j}{r_j - a_j}\) (filtrando previamente pontos que não satisfazem
     * \(y_j < r_j \; \forall j\)).
     *
     * @param front matriz de pontos
     * @param originLowerBounds origem \(\mathbf{a}\) da caixa (ex.: \(\mathbf{0}\) no espaço normalizado ou \(\mathbf{a}\) no espaço bruto)
     * @param referencePoint ponto de referência explícito \(\mathbf{r}\) (\(r_j > a_j\))
     * @param scaleByBoxVolume se {@code true}, multiplica o hipervolume unitário pelo volume da caixa
     *     \(\prod_{j=1}^m (r_j - a_j)\), retornando o hipervolume na escala original de {@code front}
     * @return hipervolume exato \(\text{HV}(A; \mathbf{r})\)
     */
    public static double computeExactHypervolumeWithExplicitReferencePoint(
            double[][] front,
            double[] originLowerBounds,
            double[] referencePoint,
            boolean scaleByBoxVolume) {
        Objects.requireNonNull(front, "A frente não pode ser nula.");
        validateNonDegenerateBounds(originLowerBounds, referencePoint);
        int m = referencePoint.length;
        if (front.length == 0) {
            return 0.0;
        }

        // Filtra apenas os pontos que dominam estritamente o ponto de referência (y_j < r_j para todo j)
        // e reescala para a caixa [0, 1]^m onde r corresponde a (1, ..., 1)
        double[][] validUnitPoints = new double[front.length][];
        int validCount = 0;
        for (int i = 0; i < front.length; i++) {
            double[] row = front[i];
            if (row == null || row.length != m) {
                throw new IllegalArgumentException(
                        "Dimensão inválida na linha " + i + " (esperado m=" + m + ").");
            }
            boolean strictlyDominatesRef = true;
            for (int j = 0; j < m; j++) {
                if (!Double.isFinite(row[j])) {
                    throw new IllegalArgumentException(
                            "Valor não finito na linha " + i + ", coluna " + j + ": " + row[j]);
                }
                if (row[j] >= referencePoint[j]) {
                    strictlyDominatesRef = false;
                    break;
                }
            }
            if (strictlyDominatesRef) {
                double[] scaled = new double[m];
                for (int j = 0; j < m; j++) {
                    double u =
                            (row[j] - originLowerBounds[j])
                                    / (referencePoint[j] - originLowerBounds[j]);
                    if (u < 0.0) {
                        throw new IllegalArgumentException(
                                String.format(
                                        Locale.US,
                                        "Ponto na linha %d possui coordenada y[%d]=%.12g abaixo da origem da caixa a[%d]=%.12g; "
                                                + "use HYBRID_ENVELOPE_BOUNDS ou origem <= min(front) para evitar clipping em 0.",
                                        i,
                                        j,
                                        row[j],
                                        j,
                                        originLowerBounds[j]));
                    }
                    scaled[j] = u;
                }
                validUnitPoints[validCount++] = scaled;
            }
        }

        if (validCount == 0) {
            return 0.0;
        }
        double[][] trimmed = Arrays.copyOf(validUnitPoints, validCount);
        PISAHypervolume pisa = new PISAHypervolume( StaticParams.getNormalizedHypervolumeReferencePoint(m));
        double unitHv = pisa.compute(trimmed);
        if (!scaleByBoxVolume) {
            return unitHv;
        }
        return unitHv * computeJacobianDeterminant(originLowerBounds, referencePoint);
    }

    /**
     * Calcula de forma independente o hipervolume 2D exato no espaço bruto ou normalizado por
     * ordenação lexicográfica e soma direta de retângulos adjacentes (\(O(N \log N)\)), servindo
     * como oráculo geométrico fechado para validar {@link PISAHypervolume}, {@link WFGHypervolume}
     * e a identidade do determinante Jacobiano.
     *
     * @param front2D matriz \(N \times 2\) de pontos
     * @param referencePoint2D ponto de referência \(\mathbf{r} = (r_1, r_2)\)
     * @return hipervolume 2D exato \(\mathcal{L}_2\left(\bigcup_{\mathbf{y} \in A, \mathbf{y} \prec \mathbf{r}} [y_1, r_1] \times [y_2, r_2]\right)\)
     */
    public static double computeAnalyticalHypervolume2D(
            double[][] front2D, double[] referencePoint2D) {
        Objects.requireNonNull(front2D, "front2D não pode ser nulo.");
        Objects.requireNonNull(referencePoint2D, "referencePoint2D não pode ser nulo.");
        if (referencePoint2D.length != 2) {
            throw new IllegalArgumentException("referencePoint2D deve ter dimensão 2.");
        }
        double r1 = referencePoint2D[0];
        double r2 = referencePoint2D[1];
        if (!Double.isFinite(r1) || !Double.isFinite(r2)) {
            throw new IllegalArgumentException("referencePoint2D deve conter valores finitos.");
        }

        // 1. Filtra pontos que dominam estritamente (r1, r2)
        double[][] candidates = new double[front2D.length][];
        int count = 0;
        for (int i = 0; i < front2D.length; i++) {
            double[] p = front2D[i];
            if (p == null || p.length != 2) {
                throw new IllegalArgumentException("Cada ponto em front2D deve ter dimensão 2.");
            }
            if (!Double.isFinite(p[0]) || !Double.isFinite(p[1])) {
                throw new IllegalArgumentException("Coordenada não finita em front2D na linha " + i);
            }
            if (p[0] < r1 && p[1] < r2) {
                candidates[count++] = new double[] {p[0], p[1]};
            }
        }
        if (count == 0) {
            return 0.0;
        }

        // 2. Ordena por f1 crescente e, em caso de empate, por f2 crescente
        Arrays.sort(
                candidates,
                0,
                count,
                (a, b) -> {
                    int c = Double.compare(a[0], b[0]);
                    return c != 0 ? c : Double.compare(a[1], b[1]);
                });

        // 3. Varredura 2D acumulando fatias verticais [f1_i, r1] * (boundY2 - f2_i)
        double volume = 0.0;
        double currentY2Upper = r2;
        for (int i = 0; i < count; i++) {
            double f1 = candidates[i][0];
            double f2 = candidates[i][1];
            if (f2 < currentY2Upper) {
                volume += (r1 - f1) * (currentY2Upper - f2);
                currentY2Upper = f2;
            }
        }
        return volume;
    }

    /**
     * Deriva o indicador {@code NHV} em \(O(1)\) a partir do hipervolume já calculado da frente
     * aproximada \(\text{HV}(A)\) e do hipervolume pré-computado da frente de referência
     * \(c = \text{HV}(R) > 0\):
     *
     * $$\text{NHV}(A) = 1.0 - \frac{\text{HV}(A)}{\text{HV}(R)}.$$
     *
     * @param frontHv hipervolume \(\text{HV}(A) \ge 0\)
     * @param referenceFrontHv hipervolume da frente de referência \(c = \text{HV}(R) > 0\)
     * @return valor exato de \(\text{NHV}(A)\), idêntico ao retornado por {@link NormalizedHypervolume#compute(double[][])}
     * @throws IllegalArgumentException se {@code frontHv} não for finito/for negativo, ou se
     *     {@code referenceFrontHv} não for finito ou for \(\le 0\)
     */
    public static double deriveNormalizedHypervolumeO1(double frontHv, double referenceFrontHv) {
        if (!Double.isFinite(frontHv) || frontHv < 0.0) {
            throw new IllegalArgumentException(
                    "O hipervolume da frente aproximada deve ser finito e >= 0, mas foi: " + frontHv);
        }
        if (!Double.isFinite(referenceFrontHv) || referenceFrontHv <= 0.0) {
            throw new IllegalArgumentException(
                    "O hipervolume da frente de referência HV(R) deve ser finito e estritamente positivo (> 0), mas foi: "
                            + referenceFrontHv);
        }
        return 1.0 - (frontHv / referenceFrontHv);
    }

    /**
     * Cria uma instância de {@link R2} com estado determinístico e imune ao bug de retenção de
     * {@code weightVectors} e {@code idealPoint} quando {@link R2#referenceFront(double[][])} é
     * invocado para problemas de dimensões \(m\) diferentes, permitindo também fixar explicitamente
     * o ponto ideal \(\mathbf{z}^* = (0, \dots, 0)\) do espaço normalizado.
     *
     * @param normalizedReferenceFront frente de referência já normalizada
     * @param fixZeroIdealPoint se {@code true}, fixa \(\mathbf{z}^* = (0, \dots, 0) \in \mathbb{R}^m\);
     *     se {@code false}, usa \(\min_i \tilde{R}_{i,j}\) (comportamento padrão de {@link R2})
     * @return instância configurada de {@link R2}
     */
    public static R2 createDeterministicR2(
            double[][] normalizedReferenceFront, boolean fixZeroIdealPoint) {
        Objects.requireNonNull(
                normalizedReferenceFront, "normalizedReferenceFront não pode ser nulo.");
        if (normalizedReferenceFront.length == 0 || normalizedReferenceFront[0].length < 2) {
            throw new IllegalArgumentException("normalizedReferenceFront não pode ser vazia.");
        }
        int m = normalizedReferenceFront[0].length;
        R2 r2 =
                new R2(normalizedReferenceFront) {
                    @Override
                    public void referenceFront(double[][] refFront) {
                        super.referenceFront(refFront);
                        setWeightVectors(null);
                        if (fixZeroIdealPoint && refFront != null && refFront.length > 0) {
                            setIdealPoint(new double[refFront[0].length]);
                        } else {
                            setIdealPoint(null);
                        }
                    }
                };
        if (fixZeroIdealPoint) {
            r2.setIdealPoint(new double[m]);
        }
        return r2;
    }

    /**
     * Executa o cálculo unificado dos indicadores sobre uma frente aproximada bruta \(A\) e uma
     * frente de referência bruta \(R\) sob o modo de normalização escolhido, sem dupla normalização
     * e derivando {@code NHV} em \(O(1)\) a partir de {@code HV(A)} e {@code HV(R)}.
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos
     * @param mode modo de normalização ({@link NormalizationMode})
     * @param rawReferenceFront frente de referência no espaço bruto
     * @param rawApproximationFront frente aproximada obtida pelo algoritmo no espaço bruto
     * @return {@link UnifiedIndicatorEvaluation}
     */
    public static UnifiedIndicatorEvaluation evaluateIndicators(
            String problemName,
            int m,
            NormalizationMode mode,
            double[][] rawReferenceFront,
            double[][] rawApproximationFront) {
        validateMatrixDimensions(rawReferenceFront, m, "rawReferenceFront");
        Objects.requireNonNull(rawApproximationFront, "rawApproximationFront não pode ser nulo.");

        NormalizationBounds bounds = resolveBounds(problemName, m, mode, rawReferenceFront);
        double[][] normRef = normalizeFront(rawReferenceFront, bounds);
        double[][] normSol = normalizeFront(rawApproximationFront, bounds);

        FrontBoxDiagnostics diagnostics = diagnoseFront(rawApproximationFront, bounds);

        // 1. HV na caixa unitária [0, 1]^m (padrão PISAHypervolume) e HV(R) pré-computado uma única vez
        PISAHypervolume pisaHv = new PISAHypervolume(normRef);
        double hvRefUnit = pisaHv.compute(normRef);
        double hvSolUnit = (normSol.length == 0) ? 0.0 : pisaHv.compute(normSol);

        // 2. NHV derivado em O(1) a partir de hvSolUnit e hvRefUnit (sem segunda chamada a PISAHypervolume)
        double nhvO1 =
                (hvRefUnit > 0.0)
                        ? deriveNormalizedHypervolumeO1(hvSolUnit, hvRefUnit)
                        : Double.NaN;

        // 3. HV com ponto de referência explícito r_raw (com margem 10% sobre os extremos da PF) e conversão via Jacobiano
        // Usa min(bounds.lowerBounds, min(rawApproximationFront)) caso alguma coordenada seja < a_j
        double[] boxOriginNorm = new double[m];
        double[] boxOriginRaw = bounds.lowerBounds();
        for (int i = 0; i < normSol.length; i++) {
            for (int j = 0; j < m; j++) {
                if (normSol[i][j] < boxOriginNorm[j]) {
                    boxOriginNorm[j] = normSol[i][j];
                }
                if (rawApproximationFront[i][j] < boxOriginRaw[j]) {
                    boxOriginRaw[j] = rawApproximationFront[i][j];
                }
            }
        }
        double hvNormScaledRef =
                computeExactHypervolumeWithExplicitReferencePoint(
                        normSol, boxOriginNorm, bounds.normalizedHvReferencePoint, true);
        double hvRawFromJacobian = hvNormScaledRef * bounds.jacobianDeterminant();

        // 4. IGD (padrão jMetal pow=2.0 vs clássico pow=1.0), IGD+ e R2
        double igdPow2;
        double igdPow1;
        double igdPlusVal;
        double r2Val;
        if (normSol.length == 0) {
            igdPow2 = Double.POSITIVE_INFINITY;
            igdPow1 = Double.POSITIVE_INFINITY;
            igdPlusVal = Double.POSITIVE_INFINITY;
            r2Val = Double.POSITIVE_INFINITY;
        } else {
            InvertedGenerationalDistance igd2 = new InvertedGenerationalDistance(normRef, 2.0);
            InvertedGenerationalDistance igd1 = new InvertedGenerationalDistance(normRef, 1.0);
            InvertedGenerationalDistancePlus igdPlus = new InvertedGenerationalDistancePlus(normRef);
            R2 r2 = createDeterministicR2(normRef, false);
            igdPow2 = igd2.compute(normSol);
            igdPow1 = igd1.compute(normSol);
            igdPlusVal = igdPlus.compute(normSol);
            r2Val = r2.compute(normSol);
        }

        return new UnifiedIndicatorEvaluation(
                hvSolUnit,
                hvRefUnit,
                nhvO1,
                hvNormScaledRef,
                hvRawFromJacobian,
                igdPow2,
                igdPow1,
                igdPlusVal,
                r2Val,
                diagnostics);
    }

    /**
     * Gera o manifesto auditável {@link IndicatorContractManifest} para uma configuração \((P, m)\),
     * incluindo o hash SHA-256 da frente de referência quando o caminho do arquivo é fornecido.
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos
     * @param mode modo de normalização
     * @param rawReferenceFront matriz da frente de referência
     * @param referenceFrontPath caminho opcional do arquivo CSV da frente de referência
     * @return {@link IndicatorContractManifest}
     */
    public static IndicatorContractManifest buildManifest(
            String problemName,
            int m,
            NormalizationMode mode,
            double[][] rawReferenceFront,
            Path referenceFrontPath) {
        validateMatrixDimensions(rawReferenceFront, m, "rawReferenceFront");
        NormalizationBounds bounds = resolveBounds(problemName, m, mode, rawReferenceFront);
        double[][] normRef = normalizeFront(rawReferenceFront, bounds);
        PISAHypervolume pisa = new PISAHypervolume(normRef);
        double refHvUnit = pisa.compute(normRef);
        String sha256 =
                (referenceFrontPath != null && Files.exists(referenceFrontPath))
                        ? computeFileSha256(referenceFrontPath)
                        : "IN_MEMORY";

        return new IndicatorContractManifest(
                "T17-1.0",
                bounds.problemId(),
                m,
                mode,
                bounds.lowerBounds(),
                bounds.upperBounds(),
                bounds.rawHvReferencePoint(),
                bounds.normalizedHvReferencePoint(),
                bounds.jacobianDeterminant(),
                sha256,
                rawReferenceFront.length,
                refHvUnit,
                "jMetal-7.8 default: IGD_pow2(A,R) = (sum_{r in R} d_2(r,A)^2)^(1/2) / |R|; classical pow=1: sum_{r in R} d_2(r,A) / |R|");
    }

    /**
     * Calcula os postos médios (*mid-ranks*, de \(1\) a \(N\), com empates recebendo a média dos
     * postos ocupados) de uma amostra concatenada de \(K\) grupos e avalia a estatística \(H\) de
     * Kruskal-Wallis com correção para empates (Hollander &amp; Wolfe, 1973; R {@code kruskal.test}):
     *
     * $$H = \frac{\frac{12}{N(N+1)} \sum_{k=1}^K \frac{R_k^2}{n_k} - 3(N+1)}{1 - \frac{\sum (t_g^3 - t_g)}{N^3 - N}}.$$
     *
     * @param groups matriz \(K \times n_k\) com as observações de cada algoritmo/grupo
     * @return estatística \(H \ge 0\) de Kruskal-Wallis
     */
    public static double computeKruskalWallisH(double[][] groups) {
        Objects.requireNonNull(groups, "groups não pode ser nulo.");
        if (groups.length < 2) {
            throw new IllegalArgumentException("Kruskal-Wallis requer pelo menos K >= 2 grupos.");
        }
        int totalN = 0;
        for (int k = 0; k < groups.length; k++) {
            if (groups[k] == null || groups[k].length == 0) {
                throw new IllegalArgumentException("O grupo k=" + k + " não pode ser vazio.");
            }
            totalN += groups[k].length;
        }
        double[] flat = new double[totalN];
        int[] groupIndex = new int[totalN];
        int idx = 0;
        for (int k = 0; k < groups.length; k++) {
            for (int i = 0; i < groups[k].length; i++) {
                double v = groups[k][i];
                if (!Double.isFinite(v)) {
                    throw new IllegalArgumentException("Observação não finita no grupo " + k);
                }
                flat[idx] = v;
                groupIndex[idx] = k;
                idx++;
            }
        }

        double[] ranks = computeMidRanks(flat);
        double[] rankSums = new double[groups.length];
        for (int i = 0; i < totalN; i++) {
            rankSums[groupIndex[i]] += ranks[i];
        }

        double sumR2OverN = 0.0;
        for (int k = 0; k < groups.length; k++) {
            sumR2OverN += (rankSums[k] * rankSums[k]) / groups[k].length;
        }
        double hUnadjusted =
                (12.0 / (totalN * (totalN + 1.0))) * sumR2OverN - 3.0 * (totalN + 1.0);

        // Correção de empates: 1 - sum(t^3 - t) / (N^3 - N)
        double[] sorted = flat.clone();
        Arrays.sort(sorted);
        double tieSum = 0.0;
        int i = 0;
        while (i < totalN) {
            int j = i + 1;
            while (j < totalN && Double.compare(sorted[j], sorted[i]) == 0) {
                j++;
            }
            long t = j - i;
            if (t > 1) {
                tieSum += (double) (t * t * t - t);
            }
            i = j;
        }
        double denom = (double) totalN * totalN * totalN - totalN;
        double tieCorrection = 1.0 - (tieSum / denom);
        if (tieCorrection <= 0.0) {
            return 0.0;
        }
        return hUnadjusted / tieCorrection;
    }

    /**
     * Calcula os postos médios (*mid-ranks* \(1 \dots N\)) de um vetor numérico.
     *
     * @param values vetor de observações
     * @return vetor de postos \(R_i \in [1, N]\)
     */
    public static double[] computeMidRanks(double[] values) {
        Objects.requireNonNull(values, "values não pode ser nulo.");
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i + 1;
            while (j < n && Double.compare(values[order[j]], values[order[i]]) == 0) {
                j++;
            }
            // Postos de (i + 1) até j -> média = (i + 1 + j) / 2.0
            double avgRank = 0.5 * ((i + 1.0) + j);
            for (int r = i; r < j; r++) {
                ranks[order[r]] = avgRank;
            }
            i = j;
        }
        return ranks;
    }

    /**
     * Verifica analítica e numericamente que, dado \(c = \text{HV}(R) > 0\) constante para todos os
     * \(K\) algoritmos em uma mesma instância \((P, m)\), transformar \(\text{HV}\) em
     * \(\text{NHV} = 1 - \text{HV}/c\) inverte simetricamente todos os postos
     * (\(R_i^{\text{NHV}} = N + 1 - R_i^{\text{HV}}\)) e preserva exatamente a estatística \(H\) de
     * Kruskal-Wallis (\(H_{\text{HV}} = H_{\text{NHV}}\)).
     *
     * @param hvGroups amostras de {@code HV} para os \(K\) algoritmos
     * @param referenceFrontHv constante \(c = \text{HV}(R) > 0\)
     * @return {@link KruskalWallisInvarianceReport}
     */
    public static KruskalWallisInvarianceReport verifyKruskalWallisInvariance(
            double[][] hvGroups, double referenceFrontHv) {
        if (!Double.isFinite(referenceFrontHv) || referenceFrontHv <= 0.0) {
            throw new IllegalArgumentException(
                    "referenceFrontHv deve ser finito e > 0: " + referenceFrontHv);
        }
        Objects.requireNonNull(hvGroups, "hvGroups não pode ser nulo.");
        double[][] nhvGroups = new double[hvGroups.length][];
        int totalN = 0;
        for (int k = 0; k < hvGroups.length; k++) {
            nhvGroups[k] = new double[hvGroups[k].length];
            for (int i = 0; i < hvGroups[k].length; i++) {
                nhvGroups[k][i] = deriveNormalizedHypervolumeO1(hvGroups[k][i], referenceFrontHv);
                totalN++;
            }
        }

        double[] flatHv = new double[totalN];
        double[] flatNhv = new double[totalN];
        int idx = 0;
        for (int k = 0; k < hvGroups.length; k++) {
            for (int i = 0; i < hvGroups[k].length; i++) {
                flatHv[idx] = hvGroups[k][i];
                flatNhv[idx] = nhvGroups[k][i];
                idx++;
            }
        }

        double[] ranksHv = computeMidRanks(flatHv);
        double[] ranksNhv = computeMidRanks(flatNhv);
        boolean inverted = true;
        for (int i = 0; i < totalN; i++) {
            if (Math.abs((ranksHv[i] + ranksNhv[i]) - (totalN + 1.0)) > 1e-12) {
                inverted = false;
                break;
            }
        }

        double hHv = computeKruskalWallisH(hvGroups);
        double hNhv = computeKruskalWallisH(nhvGroups);
        return new KruskalWallisInvarianceReport(
                hvGroups.length, totalN, hHv, hNhv, Math.abs(hHv - hNhv), inverted);
    }

    private static void validateMatrixDimensions(double[][] matrix, int m, String name) {
        if (matrix == null || matrix.length == 0) {
            throw new IllegalArgumentException(name + " não pode ser nula ou vazia.");
        }
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] == null || matrix[i].length != m) {
                throw new IllegalArgumentException(
                        name + " possui dimensão inválida na linha " + i + " (esperado m=" + m + ").");
            }
        }
    }

    private static String computeFileSha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Não foi possível calcular SHA-256 de " + path, e);
        }
    }
}

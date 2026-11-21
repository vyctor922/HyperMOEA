package br.usp.lti.newmoea.qualityindicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.FrontBoxDiagnostics;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.IndicatorContractManifest;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.KruskalWallisInvarianceReport;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.NormalizationBounds;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.NormalizationMode;
import br.usp.lti.newmoea.util.ObjectiveNormalizationContract.UnifiedIndicatorEvaluation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.Hypervolume3D;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume;
import org.uma.jmetal.util.NormalizeUtils;
import org.uma.jmetal.util.VectorUtils;

/**
 * Suíte de auditoria científica e testes automatizados para a Tarefa T17
 * ({@link ObjectiveNormalizationContract}): normalização única de objetivos, semântica dos
 * indicadores ({@code HV}, {@code NHV}, {@code IGD}, {@code IGD+}, {@code R2}), truncamento em
 * frentes de referência esparsas ({@code WFG4.15D.csv}), extrapolação legada ({@code WFG3.2D.csv}),
 * identidade exata pelo determinante Jacobiano e invariância estatística de Kruskal-Wallis entre
 * {@code HV} e {@code NHV}.
 *
 * <p><strong>Nota:</strong> Estes são testes de uma tarefa específica (T17) para verificação de
 * contrato/normalização em frentes legadas protegidas e não devem ser usados como referência para
 * execuções finais.
 */
class NormalizationAndHvContractTest {

    @Test
    @DisplayName("T17.1: Reproduz o truncamento severo em WFG4.15D.csv sob normalização amostral vs preservação sob normalização teórica")
    void testWfg4In15DSparseReferenceFrontTruncationVsTheoreticalBounds() throws IOException {
        int m = 15;
        // Uso exclusivo de pasta legada protegida para teste de verificação da tarefa T17 (não usar em execuções finais).
        Path wfg4Path = Path.of("pareto_fronts", "csv", "WFG4.15D.csv");
        double[][] rawRef = VectorUtils.readVectors(wfg4Path.toString(), ",");
        assertThat(rawRef).hasDimensions(135, m);

        // 1. Limites amostrais reais de WFG4.15D.csv
        NormalizationBounds empiricalBounds =
                ObjectiveNormalizationContract.resolveBounds(
                        "WFG4", m, NormalizationMode.EMPIRICAL_REFERENCE_MIN_MAX, rawRef);
        assertThat(empiricalBounds.upperBounds()[0]).isCloseTo(0.007593, Offset.offset(1e-5));
        assertThat(empiricalBounds.lowerBounds()[14]).isCloseTo(0.1681, Offset.offset(1e-3));

        // 2. Constrói pontos legítimos da frente de Pareto verdadeira de WFG4 (hiper-elipsoide sum (f_j / (2j))^2 = 1):
        // Ponto P1 com f_1 = 1.0 (metade do semi-eixo S_1 = 2.0) e demais coordenadas distribuídas no elipsoide
        double[] p1 = new double[m];
        p1[0] = 1.0; // (1.0 / 2.0)^2 = 0.25
        double remWeight = Math.sqrt((1.0 - 0.25) / (m - 1));
        for (int j = 1; j < m; j++) {
            p1[j] = 2.0 * (j + 1) * remWeight;
        }
        // Ponto P2 próximo ao extremo analítico de f_1 (f_1 = 1.8 < 2.0)
        double[] p2 = new double[m];
        p2[0] = 1.8; // (1.8 / 2.0)^2 = 0.81
        double remWeight2 = Math.sqrt((1.0 - 0.81) / (m - 1));
        for (int j = 1; j < m; j++) {
            p2[j] = 2.0 * (j + 1) * remWeight2;
        }
        double[][] trueParetoFrontSample = {p1, p2};

        // 3. Avalia sob EMPIRICAL_REFERENCE_MIN_MAX (comportamento atual do ComputeQualityIndicators):
        // Como max_emp(f_1) = 0.007593, f_1 = 1.0 é normalizado para ~131.7 > 1.0 e f_1 = 1.8 para ~237.1 > 1.0!
        double[][] normEmpirical =
                ObjectiveNormalizationContract.normalizeFront(
                        trueParetoFrontSample, empiricalBounds);
        assertThat(normEmpirical[0][0]).isGreaterThan(130.0);
        assertThat(normEmpirical[1][0]).isGreaterThan(235.0);

        // VectorUtils.getInvertedFront clampa > 1.0 para 0.0, e reduceNondominatedSet descarta 100% dos pontos -> HV = 0.0!
        UnifiedIndicatorEvaluation empEval =
                ObjectiveNormalizationContract.evaluateIndicators(
                        "WFG4",
                        m,
                        NormalizationMode.EMPIRICAL_REFERENCE_MIN_MAX,
                        rawRef,
                        trueParetoFrontSample);
        assertThat(empEval.diagnostics().pointsAboveUpperBound()).isEqualTo(2);
        assertThat(empEval.diagnostics().pointsOutsideUnitHvBox()).isEqualTo(2);
        assertThat(empEval.hvPisaUnitBox()).isEqualTo(0.0);
        assertThat(empEval.nhvDerivedO1()).isEqualTo(1.0);

        // 4. Avalia a MESMA frente sob THEORETICAL_PARETO_BOUNDS (a_j = 0, b_j = 2j):
        // Agora tilde(f_1) = 0.5 e 0.9 in [0, 1], preservando contribuição estritamente positiva ao HV!
        NormalizationBounds theoBounds =
                ObjectiveNormalizationContract.resolveBounds(
                        "WFG4", m, NormalizationMode.THEORETICAL_PARETO_BOUNDS, rawRef);
        double[][] normTheo =
                ObjectiveNormalizationContract.normalizeFront(trueParetoFrontSample, theoBounds);
        assertThat(normTheo[0][0]).isCloseTo(0.5, Offset.offset(1e-12));
        assertThat(normTheo[1][0]).isCloseTo(0.9, Offset.offset(1e-12));

        UnifiedIndicatorEvaluation theoEval =
                ObjectiveNormalizationContract.evaluateIndicators(
                        "WFG4",
                        m,
                        NormalizationMode.THEORETICAL_PARETO_BOUNDS,
                        rawRef,
                        trueParetoFrontSample);
        assertThat(theoEval.diagnostics().pointsBelowLowerBound()).isZero();
        assertThat(theoEval.diagnostics().pointsAboveUpperBound()).isZero();
        assertThat(theoEval.diagnostics().pointsOutsideUnitHvBox()).isZero();
        assertThat(theoEval.hvPisaUnitBox()).isGreaterThan(0.0);
        assertThat(theoEval.hvRawAtReferencePoint()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("T17.2: Bytecode de PISAHypervolume(double[]) ignora os valores de referencePoint em compute() e sempre integra em [0,1]^m")
    void testPisaHypervolumeDoubleArrayConstructorIgnoresReferencePointScaleInCompute() {
        // Demonstração empírica da hipótese da T17:
        // PISAHypervolume(double[] refPoint) armazena a matriz diagonal, mas compute() chama
        // VectorUtils.getInvertedFront(front), que clampa > 1.0 para 0.0 independentemente de refPoint!
        double[][] rawFront2D = {{1.5, 2.5}}; // Ponto estritamente melhor que r = (2.2, 4.4), mas > (1.0, 1.0)
        double[] rawRef2D = {2.2, 4.4};

        PISAHypervolume pisaWithRawRef = new PISAHypervolume(rawRef2D);
        WFGHypervolume wfgWithRawRef = new WFGHypervolume(rawRef2D);

        // PISAHypervolume retorna 0.0 erroneamente quando chamado direto no espaço bruto > 1.0!
        assertThat(pisaWithRawRef.getReferencePoint()).containsExactly(2.2, 4.4);
        assertThat(pisaWithRawRef.compute(rawFront2D)).isEqualTo(0.0);

        // Já WFGHypervolume e o contrato explícito de T17 calculam (2.2 - 1.5) * (4.4 - 2.5) = 0.7 * 1.9 = 1.33!
        double expectedRawHv = (2.2 - 1.5) * (4.4 - 2.5);
        assertThat(wfgWithRawRef.compute(rawFront2D)).isCloseTo(expectedRawHv, Offset.offset(1e-12));
        double contractRawHv =
                ObjectiveNormalizationContract.computeExactHypervolumeWithExplicitReferencePoint(
                        rawFront2D, new double[] {0.0, 0.0}, rawRef2D, true);
        assertThat(contractRawHv).isCloseTo(expectedRawHv, Offset.offset(1e-12));
    }

    @Test
    @DisplayName("T17.3: Igualdade exata 2D à mão e m-D pelo determinante Jacobiano entre HV bruto e HV normalizado")
    void testHandCalculated2DAndMultiDimensionalJacobianHypervolumeIdentity() {
        // Caso 2D calculado à mão em escala não-unitária (WFG em m=2: a = (0, 0), b = (2, 4), r_raw = (2.2, 4.4)):
        // Frente bruta A = {(0.4, 3.2), (1.0, 2.0), (1.8, 0.8)}
        // Ordenada por f1:
        // p1 = (0.4, 3.2): retângulo [0.4, 2.2] x [3.2, 4.4] -> (2.2 - 0.4) * (4.4 - 3.2) = 1.8 * 1.2 = 2.16
        // p2 = (1.0, 2.0): retângulo [1.0, 2.2] x [2.0, 3.2] -> (2.2 - 1.0) * (3.2 - 2.0) = 1.2 * 1.2 = 1.44
        // p3 = (1.8, 0.8): retângulo [1.8, 2.2] x [0.8, 2.0] -> (2.2 - 1.8) * (2.0 - 0.8) = 0.4 * 1.2 = 0.48
        // Soma analítica bruta exata: 2.16 + 1.44 + 0.48 = 4.08
        double[][] rawFront2D = {
            {1.0, 2.0},
            {0.4, 3.2},
            {1.8, 0.8}
        };
        double[] rawRef2D = {2.2, 4.4};
        double expectedHandHvRaw = 4.08;

        double analytical2D =
                ObjectiveNormalizationContract.computeAnalyticalHypervolume2D(rawFront2D, rawRef2D);
        assertThat(analytical2D).isCloseTo(expectedHandHvRaw, Offset.offset(1e-12));

        // No espaço normalizado por a = (0, 0), b = (2, 4):
        // det(J) = (2 - 0) * (4 - 0) = 8.0
        // tilde(A) = {(0.2, 0.8), (0.5, 0.5), (0.9, 0.2)}, tilde(r) = (1.1, 1.1)
        // HV_norm(tilde(A); (1.1, 1.1)) = (1.1 - 0.2)*(1.1 - 0.8) + (1.1 - 0.5)*(0.8 - 0.5) + (1.1 - 0.9)*(0.5 - 0.2)
        //                               = 0.9*0.3 + 0.6*0.3 + 0.2*0.3 = 0.27 + 0.18 + 0.06 = 0.51
        // E det(J) * HV_norm = 8.0 * 0.51 = 4.08!
        NormalizationBounds bounds2D =
                ObjectiveNormalizationContract.resolveBounds(
                        "WFG4", 2, NormalizationMode.THEORETICAL_PARETO_BOUNDS, null);
        assertThat(bounds2D.jacobianDeterminant()).isCloseTo(8.0, Offset.offset(1e-12));
        assertThat(bounds2D.normalizedHvReferencePoint()).containsExactly(1.1, 1.1);

        double[][] normFront2D = ObjectiveNormalizationContract.normalizeFront(rawFront2D, bounds2D);
        double hvNorm =
                ObjectiveNormalizationContract.computeExactHypervolumeWithExplicitReferencePoint(
                        normFront2D,
                        new double[] {0.0, 0.0},
                        bounds2D.normalizedHvReferencePoint(),
                        true);
        double hvRawViaContract =
                ObjectiveNormalizationContract.computeExactHypervolumeWithExplicitReferencePoint(
                        rawFront2D,
                        bounds2D.lowerBounds(),
                        bounds2D.rawHvReferencePoint(),
                        true);

        assertThat(hvNorm).isCloseTo(0.51, Offset.offset(1e-12));
        assertThat(hvNorm * bounds2D.jacobianDeterminant())
                .isCloseTo(expectedHandHvRaw, Offset.offset(1e-12));
        assertThat(hvRawViaContract).isCloseTo(expectedHandHvRaw, Offset.offset(1e-12));

        // Verificação 3D com Hypervolume3D e WFGHypervolume em DTLZ7 (m=3: b = (1, 1, 6), r_raw = (1.1, 1.1, 6.3))
        NormalizationBounds dtlz7Bounds3D =
                ObjectiveNormalizationContract.resolveBounds(
                        "DTLZ7", 3, NormalizationMode.THEORETICAL_PARETO_BOUNDS, null);
        double[][] rawFront3D = {
            {0.2, 0.2, 4.0},
            {0.7, 0.1, 3.2},
            {0.1, 0.8, 3.0}
        };
        double[][] normFront3D =
                ObjectiveNormalizationContract.normalizeFront(rawFront3D, dtlz7Bounds3D);
        double hvNorm3D =
                ObjectiveNormalizationContract.computeExactHypervolumeWithExplicitReferencePoint(
                        normFront3D,
                        new double[] {0.0, 0.0, 0.0},
                        dtlz7Bounds3D.normalizedHvReferencePoint(),
                        true);
        double hvRaw3D =
                ObjectiveNormalizationContract.computeExactHypervolumeWithExplicitReferencePoint(
                        rawFront3D,
                        dtlz7Bounds3D.lowerBounds(),
                        dtlz7Bounds3D.rawHvReferencePoint(),
                        true);
        Hypervolume3D jmetalHv3D = new Hypervolume3D(dtlz7Bounds3D.rawHvReferencePoint());
        WFGHypervolume jmetalWfg3D = new WFGHypervolume(dtlz7Bounds3D.rawHvReferencePoint());

        assertThat(hvNorm3D * dtlz7Bounds3D.jacobianDeterminant())
                .isCloseTo(hvRaw3D, Offset.offset(1e-12))
                .isCloseTo(jmetalHv3D.compute(rawFront3D), Offset.offset(1e-12))
                .isCloseTo(jmetalWfg3D.compute(rawFront3D), Offset.offset(1e-12));
    }

    @Test
    @DisplayName("T17.4: Identidade numérica NHV(A) = 1 - HV(A)/HV(R) em O(1) e invariância exata de Kruskal-Wallis entre HV e NHV")
    void testNhvO1IdentityAndKruskalWallisRankAndStatisticInvariance() throws IOException {
        // Uso exclusivo de pasta legada protegida para teste de verificação da tarefa T17 (não usar em execuções finais).
        Path refPath = Path.of("pareto_fronts", "csv", "DTLZ1.2D.csv");
        double[][] rawRef = VectorUtils.readVectors(refPath.toString(), ",");
        double[][] normRef = NormalizeUtils.normalize(rawRef);

        PISAHypervolume pisa = new PISAHypervolume(normRef);
        NormalizedHypervolume jmetalNhv = new NormalizedHypervolume(normRef);
        double hvRef = pisa.compute(normRef);
        assertThat(hvRef).isGreaterThan(0.0);

        // Simula 6 algoritmos (K = 6) com 10 runs cada (N = 60), incluindo empates exatos (ties)
        double[][] hvGroups = new double[6][10];
        for (int k = 0; k < 6; k++) {
            for (int r = 0; r < 10; r++) {
                double shift = 0.04 * (k + 1) + 0.01 * (r / 2); // empates intencionais para r par/ímpar
                double[][] approxFront = {
                    {0.1 + shift, 0.7 - 0.5 * shift},
                    {0.35 + shift, 0.4 + 0.2 * shift},
                    {0.65 - 0.3 * shift, 0.15 + shift}
                };
                double hvA = pisa.compute(approxFront);
                double nhvFromJMetal = jmetalNhv.compute(approxFront);
                double nhvFromO1 =
                        ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1(hvA, hvRef);

                // Equivalência numérica exata (diferença <= 1e-15) sem recomputar PISAHypervolume
                assertThat(nhvFromO1).isCloseTo(nhvFromJMetal, Offset.offset(1e-15));
                hvGroups[k][r] = hvA;
            }
        }

        // Verifica invariância de postos (R_i^NHV = N + 1 - R_i^HV) e igualdade de H_KW
        KruskalWallisInvarianceReport kwReport =
                ObjectiveNormalizationContract.verifyKruskalWallisInvariance(hvGroups, hvRef);
        assertThat(kwReport.algorithmCount()).isEqualTo(6);
        assertThat(kwReport.totalObservations()).isEqualTo(60);
        assertThat(kwReport.ranksExactlyInverted()).isTrue();
        assertThat(kwReport.kruskalWallisHForHv()).isGreaterThan(0.0);
        assertThat(kwReport.absoluteHDifference()).isLessThanOrEqualTo(1e-12);

        // Verifica erro explícito quando o denominador HV(R) é zero, negativo ou NaN
        assertThatThrownBy(
                        () -> ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1(0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HV(R)");
        assertThatThrownBy(
                        () ->
                                ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1(
                                        0.5, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1(
                                        0.5, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T17.5: Tratamento explícito de WFG3.2D.csv (extrapolado com f_2 < 0) sob os 3 modos de normalização")
    void testWfg3In2DExtrapolatedReferenceFrontHandling() throws IOException {
        Path wfg3Path = Path.of("pareto_fronts", "csv", "WFG3.2D.csv");
        double[][] rawWfg3Ref = VectorUtils.readVectors(wfg3Path.toString(), ",");
        assertThat(rawWfg3Ref).hasDimensions(916, 2);

        // 1. Modo EMPIRICAL_REFERENCE_MIN_MAX: min = (0, -2), max = (3, 4)
        NormalizationBounds empBounds =
                ObjectiveNormalizationContract.resolveBounds(
                        "WFG3", 2, NormalizationMode.EMPIRICAL_REFERENCE_MIN_MAX, rawWfg3Ref);
        assertThat(empBounds.lowerBounds()[0]).isCloseTo(0.0, Offset.offset(1e-5));
        assertThat(empBounds.lowerBounds()[1]).isCloseTo(-2.0, Offset.offset(1e-4));
        assertThat(empBounds.upperBounds()[0]).isCloseTo(3.0, Offset.offset(1e-4));
        assertThat(empBounds.upperBounds()[1]).isCloseTo(4.0, Offset.offset(1e-4));

        // 2. Modo THEORETICAL_PARETO_BOUNDS: a = (0, 0), b = (2, 4) (imagem real de WFG3.evaluate no jMetal 7.8)
        NormalizationBounds theoBounds =
                ObjectiveNormalizationContract.resolveBounds(
                        "WFG3", 2, NormalizationMode.THEORETICAL_PARETO_BOUNDS, rawWfg3Ref);
        assertThat(theoBounds.lowerBounds()).containsExactly(0.0, 0.0);
        assertThat(theoBounds.upperBounds()).containsExactly(2.0, 4.0);

        // Os 120 pontos extrapolados de WFG3.2D.csv (com f_1 in (2, 3] e f_2 in [-2, 0)) são diagnosticados exatamente!
        FrontBoxDiagnostics refDiagUnderTheo =
                ObjectiveNormalizationContract.diagnoseFront(rawWfg3Ref, theoBounds);
        assertThat(refDiagUnderTheo.pointsBelowLowerBound()).isEqualTo(120);
        assertThat(refDiagUnderTheo.pointsAboveUpperBound()).isEqualTo(120);

        // 3. Modo HYBRID_ENVELOPE_BOUNDS: acomoda tanto os limites teóricos quanto os extremos físicos do CSV
        NormalizationBounds hybridBounds =
                ObjectiveNormalizationContract.resolveBounds(
                        "WFG3", 2, NormalizationMode.HYBRID_ENVELOPE_BOUNDS, rawWfg3Ref);
        FrontBoxDiagnostics refDiagUnderHybrid =
                ObjectiveNormalizationContract.diagnoseFront(rawWfg3Ref, hybridBounds);
        assertThat(refDiagUnderHybrid.pointsBelowLowerBound()).isZero();
        assertThat(refDiagUnderHybrid.pointsAboveUpperBound()).isZero();
    }

    @Test
    @DisplayName("T17.6: Auditoria de IGD (pow=2.0 vs pow=1.0), IGD+ e R2 (reset de weightVectors/idealPoint e ausência de clipping)")
    void testIgdVariantsIgdPlusAndR2StateResetContract() {
        double[][] ref2D = {
            {0.0, 1.0},
            {0.5, 0.5},
            {1.0, 0.0}
        };
        // Frente aproximada contendo coordenada < 0, = 1 e > 1 (fora da caixa unitária, sem clipping em IGD/IGD+/R2)
        double[][] front2D = {
            {-0.1, 1.2},
            {0.5, 0.6},
            {1.3, -0.05}
        };

        // 1. Em jMetal 7.8, new InvertedGenerationalDistance(ref) usa pow = 2.0:
        // IGD_jMetal(A, R) = sqrt(sum_{r in R} d(r, A)^2) / |R| != sum_{r in R} d(r, A) / |R| (pow = 1.0)
        InvertedGenerationalDistance igdDefaultPow2 = new InvertedGenerationalDistance(ref2D);
        InvertedGenerationalDistance igdClassicalPow1 =
                new InvertedGenerationalDistance(ref2D, 1.0);

        double d0 = Math.hypot(0.0 - (-0.1), 1.0 - 1.2); // sqrt(0.01 + 0.04) = sqrt(0.05)
        double d1 = Math.hypot(0.5 - 0.5, 0.5 - 0.6); // 0.1
        double d2 = Math.hypot(1.0 - 1.3, 0.0 - (-0.05)); // sqrt(0.09 + 0.0025) = sqrt(0.0925)

        double expectedPow2 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2) / 3.0;
        double expectedPow1 = (d0 + d1 + d2) / 3.0;

        assertThat(igdDefaultPow2.compute(front2D)).isCloseTo(expectedPow2, Offset.offset(1e-12));
        assertThat(igdClassicalPow1.compute(front2D)).isCloseTo(expectedPow1, Offset.offset(1e-12));
        assertThat(expectedPow1).isGreaterThan(expectedPow2);

        // 2. IGD+ usa DominanceDistanceBetweenVectors: max(a_j - r_j, 0) sem clipar valores > 1.0
        InvertedGenerationalDistancePlus igdPlus = new InvertedGenerationalDistancePlus(ref2D);
        double dPlus0 = Math.hypot(Math.max(-0.1 - 0.0, 0.0), Math.max(1.2 - 1.0, 0.0)); // 0.2
        double dPlus1 = Math.hypot(Math.max(0.5 - 0.5, 0.0), Math.max(0.6 - 0.5, 0.0)); // 0.1
        double dPlus2 = Math.hypot(Math.max(1.3 - 1.0, 0.0), Math.max(-0.05 - 0.0, 0.0)); // 0.3
        double expectedIgdPlus = (dPlus0 + dPlus1 + dPlus2) / 3.0;
        assertThat(igdPlus.compute(front2D)).isCloseTo(expectedIgdPlus, Offset.offset(1e-12));

        // 3. Bug de retenção de estado em R2 padrão do jMetal 7.8 vs createDeterministicR2:
        double[][] ref3D = {
            {0.0, 0.0, 1.0},
            {0.0, 1.0, 0.0},
            {1.0, 0.0, 0.0}
        };
        double[][] front3D = {
            {0.2, 0.3, 0.5},
            {0.4, 0.4, 0.2}
        };

        // Em R2 nativo sem override de referenceFront(), mudar de 2D para 3D mantém weightVectors 2D!
        R2 rawJMetalR2 = new R2(ref2D);
        assertThat(rawJMetalR2.getWeightVectors()[0]).hasSize(2);
        rawJMetalR2.referenceFront(ref3D);
        // Bug confirmado no bytecode: weightVectors continua com dimensão 2 mesmo após referenceFront(ref3D)!
        assertThat(rawJMetalR2.getWeightVectors()[0]).hasSize(2);

        // Já createDeterministicR2 reseta weightVectors e idealPoint ao trocar referenceFront:
        R2 safeR2 = ObjectiveNormalizationContract.createDeterministicR2(ref2D, true);
        assertThat(safeR2.getIdealPoint()).containsExactly(0.0, 0.0);
        double val2DFirst = safeR2.compute(front2D);
        double val2DSecond = safeR2.compute(front2D);
        assertThat(val2DFirst).isEqualTo(val2DSecond);

        safeR2.referenceFront(ref3D);
        assertThat(safeR2.getIdealPoint()).containsExactly(0.0, 0.0, 0.0);
        double val3D = safeR2.compute(front3D);
        assertThat(val3D).isFinite().isGreaterThan(0.0);
        assertThat(safeR2.getWeightVectors()[0]).hasSize(3);
    }

    @ParameterizedTest(name = "m={0}")
    @ValueSource(ints = {2, 3, 5, 8, 10, 15})
    @DisplayName("T17.7: Limites teóricos, máximos da variedade g=0 (DTLZ5/6, DTLZ7) e manifesto auditável para todas as dimensões m")
    void testTheoreticalBoundsManifoldExtremaAndManifestAcrossAllDimensions(int m) {
        for (int p = 1; p <= 9; p++) {
            String wfg = "WFG" + p;
            NormalizationBounds b =
                    ObjectiveNormalizationContract.resolveBounds(
                            wfg, m, NormalizationMode.THEORETICAL_PARETO_BOUNDS, null);
            assertThat(b.lowerBounds()).hasSize(m).containsOnly(0.0);
            for (int j = 0; j < m; j++) {
                assertThat(b.upperBounds()[j]).isCloseTo(2.0 * (j + 1), Offset.offset(1e-12));
            }
        }

        // DTLZ5/6: máximo na curva g=0 encolhe como 2^(-(m-2)/2) nos primeiros objetivos
        double[] dtlz5CurveMax =
                ObjectiveNormalizationContract.getExactZeroGManifoldMaximums("DTLZ5", m);
        assertThat(dtlz5CurveMax[0]).isCloseTo(Math.pow(2.0, -0.5 * (m - 2)), Offset.offset(1e-12));
        assertThat(dtlz5CurveMax[1]).isCloseTo(Math.pow(2.0, -0.5 * (m - 2)), Offset.offset(1e-12));
        assertThat(dtlz5CurveMax[m - 1]).isCloseTo(1.0, Offset.offset(1e-12));

        // DTLZ7: min f_m = 2 * (m - (m - 1) * psi(x_3*)) e max f_j = x_3* (j < m), max f_m = 2m
        double[] dtlz7Min = ObjectiveNormalizationContract.getExactParetoFrontMinimums("DTLZ7", m);
        double[] dtlz7Max =
                ObjectiveNormalizationContract.getExactZeroGManifoldMaximums("DTLZ7", m);
        for (int j = 0; j < m - 1; j++) {
            assertThat(dtlz7Min[j]).isEqualTo(0.0);
            assertThat(dtlz7Max[j])
                    .isCloseTo(
                            ObjectiveNormalizationContract.DTLZ7_PARETO_MAX_XJ,
                            Offset.offset(1e-12));
        }
        assertThat(dtlz7Min[m - 1]).isGreaterThan(2.0).isLessThan(2.0 * m);
        assertThat(dtlz7Max[m - 1]).isCloseTo(2.0 * m, Offset.offset(1e-12));

        // Manifesto completo com frente sintética de referência
        double[][] syntheticRef = new double[2][m];
        Arrays.fill(syntheticRef[0], 0.1);
        Arrays.fill(syntheticRef[1], 0.4);
        IndicatorContractManifest manifest =
                ObjectiveNormalizationContract.buildManifest(
                        "DTLZ2",
                        m,
                        NormalizationMode.THEORETICAL_PARETO_BOUNDS,
                        syntheticRef,
                        null);
        assertThat(manifest.contractVersion()).isEqualTo("T17-1.0");
        assertThat(manifest.problemId()).isEqualTo("DTLZ2");
        assertThat(manifest.m()).isEqualTo(m);
        assertThat(manifest.referenceHvUnitBox()).isGreaterThan(0.0);
        assertThat(manifest.referenceFrontSha256()).isEqualTo("IN_MEMORY");
    }

    @Test
    @DisplayName("T17.8: Casos de borda — frentes vazias, pontos duplicados, pontos dominados e rejeição de intervalos degenerados")
    void testEdgeCasesEmptyFrontDuplicatesDominatedAndDegenerateBounds() {
        double[][] ref2D = {
            {0.0, 4.0},
            {1.0, 2.0},
            {2.0, 0.0}
        };
        double[][] emptyFront = new double[0][2];

        UnifiedIndicatorEvaluation emptyEval =
                ObjectiveNormalizationContract.evaluateIndicators(
                        "WFG1", 2, NormalizationMode.THEORETICAL_PARETO_BOUNDS, ref2D, emptyFront);
        assertThat(emptyEval.hvPisaUnitBox()).isEqualTo(0.0);
        assertThat(emptyEval.nhvDerivedO1()).isEqualTo(1.0);
        assertThat(emptyEval.hvRawAtReferencePoint()).isEqualTo(0.0);
        assertThat(emptyEval.diagnostics().totalPoints()).isZero();

        // Duplicatas e pontos dominados não alteram o hipervolume, mas podem alterar IGD euclidiano
        double[][] cleanFront = {
            {0.4, 3.0},
            {1.6, 0.8}
        };
        double[][] frontWithDuplicatesAndDominated = {
            {0.4, 3.0},
            {0.4, 3.0}, // duplicata exata
            {1.6, 0.8},
            {1.8, 3.5} // estritamente dominado por (0.4, 3.0) e (1.6, 0.8)
        };

        UnifiedIndicatorEvaluation evalClean =
                ObjectiveNormalizationContract.evaluateIndicators(
                        "WFG1", 2, NormalizationMode.THEORETICAL_PARETO_BOUNDS, ref2D, cleanFront);
        UnifiedIndicatorEvaluation evalNoisy =
                ObjectiveNormalizationContract.evaluateIndicators(
                        "WFG1",
                        2,
                        NormalizationMode.THEORETICAL_PARETO_BOUNDS,
                        ref2D,
                        frontWithDuplicatesAndDominated);

        assertThat(evalNoisy.hvPisaUnitBox())
                .isCloseTo(evalClean.hvPisaUnitBox(), Offset.offset(1e-12));
        assertThat(evalNoisy.nhvDerivedO1())
                .isCloseTo(evalClean.nhvDerivedO1(), Offset.offset(1e-12));
        assertThat(evalNoisy.hvRawAtReferencePoint())
                .isCloseTo(evalClean.hvRawAtReferencePoint(), Offset.offset(1e-12));

        // Rejeição de limites degenerados (b_j <= a_j)
        assertThatThrownBy(
                        () ->
                                ObjectiveNormalizationContract.validateNonDegenerateBounds(
                                        new double[] {0.0, 2.0}, new double[] {1.0, 2.0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("degenerado");
    }
}

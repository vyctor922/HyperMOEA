package br.usp.lti.newmoea.qualityindicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;

/**
 * Testes unitários para os indicadores de qualidade utilizados no projeto.
 * Valida valores finitos, frentes conhecidas, monotonicidade, tratamento de limites,
 * compatibilidade dimensional no jMetal 7.8 e padronização de PISAHypervolume (primário)
 * e NormalizedHypervolume (secundário) com ponto de referência comum em T05.
 */
public class QualityIndicatorUnitTest {

    @Test
    @DisplayName("R2: calcula valor finito e respeita a propriedade de minimização")
    void testR2IndicatorProperties() {
        double[][] referenceFront = {
                {0.0, 1.0},
                {0.5, 0.5},
                {1.0, 0.0}
        };

        double[][] weightVectors = {
                {0.0, 1.0},
                {0.5, 0.5},
                {1.0, 0.0}
        };

        R2 r2 = new R2(referenceFront);
        r2.setWeightVectors(weightVectors);
        r2.setIdealPoint(new double[]{0.0, 0.0});

        // Frente idêntica à referência
        double valueIdeal = r2.compute(referenceFront);
        assertThat(valueIdeal).isFinite();
        assertThat(valueIdeal).isGreaterThanOrEqualTo(0.0);

        // Frente dominada (mais distante do ponto ideal)
        double[][] dominatedFront = {
                {1.0, 2.0},
                {1.5, 1.5},
                {2.0, 1.0}
        };
        double valueDominated = r2.compute(dominatedFront);

        // Em R², menor é melhor: frente ideal deve ter valor menor que a dominada
        assertThat(valueIdeal).isLessThan(valueDominated);
        assertThat(r2.isTheLowerTheIndicatorValueTheBetter()).isTrue();
        assertThat(r2.name()).isEqualTo("R2");
    }

    @Test
    @DisplayName("R2: lança exceção ou rejeita frente vazia")
    void testR2EmptyFront() {
        R2 r2 = new R2();
        double[][] emptyFront = new double[0][0];
        assertThatThrownBy(() -> r2.compute(emptyFront))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("IGD (padrão): retorna 0.0 para frente idêntica à referência e valor positivo para frente deslocada")
    void testIGDStandard() {
        double[][] referenceFront = {
                {0.0, 1.0},
                {0.5, 0.5},
                {1.0, 0.0}
        };

        InvertedGenerationalDistance igd = new InvertedGenerationalDistance(referenceFront);
        double valExact = igd.compute(referenceFront);
        assertThat(valExact).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));

        double[][] shiftedFront = {
                {0.2, 1.2},
                {0.7, 0.7},
                {1.2, 0.2}
        };
        double valShifted = igd.compute(shiftedFront);
        assertThat(valShifted).isGreaterThan(0.0);
        assertThat(igd.isTheLowerTheIndicatorValueTheBetter()).isTrue();
    }

    @Test
    @DisplayName("IGD+: retorna 0.0 para frente idêntica e respeita dominância")
    void testIGDPlus() {
        double[][] referenceFront = {
                {0.0, 1.0},
                {0.5, 0.5},
                {1.0, 0.0}
        };

        InvertedGenerationalDistancePlus igdPlus = new InvertedGenerationalDistancePlus(referenceFront);
        double valExact = igdPlus.compute(referenceFront);
        assertThat(valExact).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));

        double[][] worseFront = {
                {0.1, 1.1},
                {0.6, 0.6},
                {1.1, 0.1}
        };
        double valWorse = igdPlus.compute(worseFront);
        assertThat(valWorse).isGreaterThan(0.0);
        assertThat(igdPlus.isTheLowerTheIndicatorValueTheBetter()).isTrue();
    }

    @Test
    @DisplayName("Hipervolume (PISAHypervolume): calcula volume finito e maior para frentes melhores")
    void testHypervolume() {
        double[][] referenceFront = {
                {0.0, 1.0},
                {1.0, 0.0}
        };

        PISAHypervolume hv = new PISAHypervolume(referenceFront);

        double[][] frontA = {
                {0.1, 0.1}
        };
        double[][] frontB = {
                {0.8, 0.8}
        };

        double hvA = hv.compute(frontA);
        double hvB = hv.compute(frontB);

        assertThat(hvA).isFinite();
        assertThat(hvB).isFinite();
        // Em minimização com PISAHypervolume, uma solução próxima de (0,0) domina maior volume
        assertThat(hvA).isGreaterThan(hvB);
        assertThat(hv.isTheLowerTheIndicatorValueTheBetter()).isFalse();
    }

    @ParameterizedTest(name = "m={0}")
    @ValueSource(ints = {2, 3, 5, 8, 10, 15})
    @DisplayName("T05: Ponto de referência comum de hipervolume para WFG (2,...,2) e DTLZ (1,...,1) em todas as dimensões")
    void testCommonHypervolumeReferencePointsAndIndicators(int m) {
        double[] wfgRef = StaticParams.getHypervolumeReferencePoint("WFG1", m);
        double[] dtlzRef = StaticParams.getHypervolumeReferencePoint("DTLZ2", m);

        assertThat(wfgRef).hasSize(m);
        assertThat(dtlzRef).hasSize(m);
        for (int i = 0; i < m; i++) {
            assertThat(wfgRef[i]).isEqualTo(2.0);
            assertThat(dtlzRef[i]).isEqualTo(1.0);
        }

        // Todas as variantes de WFG1..WFG9 e DTLZ1..DTLZ7 compartilham o mesmo ponto para o mesmo m
        for (int p = 1; p <= 9; p++) {
            assertThat(StaticParams.getHypervolumeReferencePoint("WFG" + p, m)).containsExactly(wfgRef);
        }
        for (int p = 1; p <= 7; p++) {
            assertThat(StaticParams.getHypervolumeReferencePoint("DTLZ" + p, m)).containsExactly(dtlzRef);
        }

        PISAHypervolume primaryWfgHv = StaticParams.createPrimaryHypervolume("WFG1", m);
        NormalizedHypervolume secondaryWfgNhv = StaticParams.createSecondaryNormalizedHypervolume("WFG1", m);

        assertThat(primaryWfgHv.name()).isEqualTo("HV");
        assertThat(primaryWfgHv.isTheLowerTheIndicatorValueTheBetter()).isFalse();
        assertThat(primaryWfgHv.getReferencePoint()).containsExactly(wfgRef);

        assertThat(secondaryWfgNhv.name()).isEqualTo("NHV");
        assertThat(secondaryWfgNhv.isTheLowerTheIndicatorValueTheBetter()).isTrue();

        // Verifica cálculo consistente de HV e NHV com frente de referência comum normalizada
        double[][] normalizedReferenceFront = new double[2][m];
        Arrays.fill(normalizedReferenceFront[0], 0.2);
        Arrays.fill(normalizedReferenceFront[1], 0.4);
        normalizedReferenceFront[0][0] = 0.1;
        normalizedReferenceFront[1][m - 1] = 0.1;

        double[][] betterApproximation = new double[1][m];
        Arrays.fill(betterApproximation[0], 0.15);

        double[][] worseApproximation = new double[1][m];
        Arrays.fill(worseApproximation[0], 0.85);

        PISAHypervolume hvIndicator = new PISAHypervolume();
        NormalizedHypervolume nhvIndicator = new NormalizedHypervolume();
        hvIndicator.referenceFront(normalizedReferenceFront);
        nhvIndicator.referenceFront(normalizedReferenceFront);

        double hvBetter = hvIndicator.compute(betterApproximation);
        double hvWorse = hvIndicator.compute(worseApproximation);
        double nhvBetter = nhvIndicator.compute(betterApproximation);
        double nhvWorse = nhvIndicator.compute(worseApproximation);

        assertThat(hvBetter).isFinite().isGreaterThan(hvWorse);
        assertThat(nhvBetter).isFinite().isLessThan(nhvWorse);
        // Relação matemática exata entre NHV e HV: NHV(A) = 1 - HV(A) / HV(R)
        double hvRef = hvIndicator.compute(normalizedReferenceFront);
        assertThat(nhvBetter).isCloseTo(1.0 - (hvBetter / hvRef), org.assertj.core.data.Offset.offset(1e-12));
        assertThat(nhvWorse).isCloseTo(1.0 - (hvWorse / hvRef), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    @DisplayName("T05: Validação negativa de parâmetros inválidos para ponto de referência de hipervolume")
    void testInvalidHypervolumeReferencePointParameters() {
        assertThatThrownBy(() -> StaticParams.getHypervolumeReferencePoint(null, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StaticParams.getHypervolumeReferencePoint("  ", 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StaticParams.getHypervolumeReferencePoint("WFG1", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StaticParams.getHypervolumeReferencePoint("UNKNOWN_PROBLEM", 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

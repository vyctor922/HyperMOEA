package br.usp.lti.newmoea.qualityindicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;

/**
 * Testes unitários para os indicadores de qualidade utilizados no projeto.
 * Valida valores finitos, frentes conhecidas, monotonicidade, tratamento de limites
 * e compatibilidade dimensional no jMetal 7.8.
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
}

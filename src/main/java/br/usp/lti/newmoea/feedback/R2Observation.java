package br.usp.lti.newmoea.feedback;

import java.io.Serializable;

/**
 * Representa um registro pontual do indicador R² coletado durante a execução de um MOEA.
 * Mantém o passo/avaliação, o valor escalar do R², o delta em relação à observação anterior
 * e o timestamp em milissegundos.
 *
 * Convenção de minimização:
 * Menor R² indica maior proximidade e convergência em direção à fronteira ideal.
 * Portanto, deltaR2 = R²(t-1) - R²(t) > 0 indica melhoria de qualidade.
 */
public record R2Observation(
        long step,
        double r2Value,
        double deltaR2,
        long timestampMs
) implements Serializable {
}

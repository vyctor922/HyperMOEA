package br.usp.lti.newmoea.feedback;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.solution.Solution;

/**
 * Observador de execução para coleta periódica do indicador R² durante a evolução de MOEAs.
 *
 * Utiliza estritamente a implementação nativa de R² do jMetal 7.8 (org.uma.jmetal.qualityindicator.impl.R2)
 * sem duplicar cálculos ou alterar a dinâmica interna dos algoritmos.
 *
 * Registra o histórico de evolução do R² para análise a posteriori e para disponibilização
 * futura como sinal de feedback (ΔR²) para as hiper-heurísticas de seleção (AdapHH, EA-ILS, LAST-RL).
 */
public class R2ExecutionObserver {

    private final R2 nativeR2;
    private final List<R2Observation> history;
    private Double lastR2Value;

    public R2ExecutionObserver() {
        this(new R2());
    }

    public R2ExecutionObserver(double[][] referenceFront) {
        this(new R2(referenceFront));
    }

    public R2ExecutionObserver(double[][] referenceFront, double[][] weightVectors) {
        this.nativeR2 = new R2(referenceFront);
        if (weightVectors != null && weightVectors.length > 0) {
            this.nativeR2.setWeightVectors(weightVectors);
        }
        this.history = new ArrayList<>();
        this.lastR2Value = null;
    }

    public R2ExecutionObserver(R2 nativeR2) {
        this.nativeR2 = Objects.requireNonNull(nativeR2, "Instância nativa de R2 não pode ser nula");
        this.history = new ArrayList<>();
        this.lastR2Value = null;
    }

    /**
     * Coleta a observação de R² para uma dada população de soluções no passo especificado.
     *
     * @param step Número da iteração ou contagem de avaliações.
     * @param population Lista de soluções da população corrente.
     * @return Registro da observação ou null se a população for nula ou vazia.
     */
    public synchronized R2Observation observe(long step, List<? extends Solution<?>> population) {
        if (population == null || population.isEmpty()) {
            return null;
        }

        int size = population.size();
        int numberOfObjectives = population.get(0).objectives().length;
        double[][] front = new double[size][numberOfObjectives];
        for (int i = 0; i < size; i++) {
            double[] obj = population.get(i).objectives();
            System.arraycopy(obj, 0, front[i], 0, numberOfObjectives);
        }

        return observe(step, front);
    }

    /**
     * Coleta a observação de R² diretamente a partir de uma matriz de objetivos.
     *
     * @param step Número do passo ou avaliação.
     * @param front Matriz de objetivos [N][M].
     * @return Registro da observação calculada.
     */
    public synchronized R2Observation observe(long step, double[][] front) {
        if (front == null || front.length == 0) {
            return null;
        }

        double r2 = nativeR2.compute(front);
        double deltaR2;
        if (lastR2Value == null) {
            deltaR2 = 0.0;
        } else {
            // Em minimização, menor R² é melhor.
            // ΔR² = R²(t-1) - R²(t). Se positivo, houve progresso/melhoria.
            deltaR2 = lastR2Value - r2;
        }

        this.lastR2Value = r2;
        R2Observation observation = new R2Observation(step, r2, deltaR2, System.currentTimeMillis());
        history.add(observation);
        return observation;
    }

    public synchronized List<R2Observation> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized R2Observation getLatestObservation() {
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    public synchronized Double getLatestR2() {
        return lastR2Value;
    }

    public synchronized double getLatestDeltaR2() {
        R2Observation latest = getLatestObservation();
        return latest != null ? latest.deltaR2() : 0.0;
    }

    public synchronized void reset() {
        history.clear();
        lastR2Value = null;
    }

    public R2 getNativeR2() {
        return nativeR2;
    }

    /**
     * Exporta o histórico de observações para um arquivo CSV estruturado.
     */
    public synchronized void exportToCsv(Path filePath) throws IOException {
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("step,r2,delta_r2,timestamp_ms");
            writer.newLine();
            for (R2Observation obs : history) {
                writer.write(String.format("%d,%.10e,%.10e,%d",
                        obs.step(), obs.r2Value(), obs.deltaR2(), obs.timestampMs()));
                writer.newLine();
            }
        }
    }
}

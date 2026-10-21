package br.usp.lti.newmoea.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

/**
 * Testes unitários para a coleta e observação em tempo de execução do indicador R².
 * Verifica a não-invasividade, o cálculo de ΔR², a retenção de histórico e a exportação em CSV.
 */
public class R2ExecutionObserverTest {

    @Test
    @DisplayName("Coleta de R² registra histórico e calcula ΔR² corretamente")
    void testObserverCollectionAndDelta() {
        R2ExecutionObserver observer = new R2ExecutionObserver();

        double[][] front1 = {
                {1.0, 2.0},
                {2.0, 1.0}
        };

        double[][] front2 = {
                {0.5, 1.0},
                {1.0, 0.5}
        };

        R2Observation obs1 = observer.observe(1, front1);
        assertThat(obs1).isNotNull();
        assertThat(obs1.step()).isEqualTo(1);
        assertThat(obs1.deltaR2()).isEqualTo(0.0);
        assertThat(obs1.r2Value()).isPositive();

        R2Observation obs2 = observer.observe(2, front2);
        assertThat(obs2).isNotNull();
        assertThat(obs2.step()).isEqualTo(2);
        // Como front2 converge melhor para (0,0), r2Value2 < r2Value1, logo ΔR² = r2(1) - r2(2) > 0 (melhoria)
        assertThat(obs2.deltaR2()).isGreaterThan(0.0);
        assertThat(observer.getLatestDeltaR2()).isEqualTo(obs2.deltaR2());

        List<R2Observation> history = observer.getHistory();
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Exportação em formato CSV gera arquivo legível e estruturado")
    void testCsvExport(@TempDir Path tempDir) throws IOException {
        R2ExecutionObserver observer = new R2ExecutionObserver();
        observer.observe(100, new double[][]{{1.0, 2.0}, {2.0, 1.0}});
        observer.observe(200, new double[][]{{0.8, 1.6}, {1.6, 0.8}});

        Path csvPath = tempDir.resolve("r2_history.csv");
        observer.exportToCsv(csvPath);

        assertThat(Files.exists(csvPath)).isTrue();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("step,r2,delta_r2,timestamp_ms");
        assertThat(lines.get(1)).startsWith("100,");
        assertThat(lines.get(2)).startsWith("200,");
    }

    @Test
    @DisplayName("Observação a partir de List<DoubleSolution> sem avaliar o problema adicionalmente")
    void testObserveFromDoubleSolutionList() {
        DTLZ1 problem = new DTLZ1(7, 3);
        DoubleSolution sol1 = problem.createSolution();
        DoubleSolution sol2 = problem.createSolution();
        problem.evaluate(sol1);
        problem.evaluate(sol2);

        R2ExecutionObserver observer = new R2ExecutionObserver();
        R2Observation obs = observer.observe(10, List.of(sol1, sol2));

        assertThat(obs).isNotNull();
        assertThat(obs.r2Value()).isFinite();
        assertThat(observer.getLatestObservation()).isEqualTo(obs);

        observer.reset();
        assertThat(observer.getHistory()).isEmpty();
        assertThat(observer.getLatestR2()).isNull();
    }
}

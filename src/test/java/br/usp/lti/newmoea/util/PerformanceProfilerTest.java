package br.usp.lti.newmoea.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.util.PerformanceProfiler.DescriptiveStats;
import br.usp.lti.newmoea.util.PerformanceProfiler.HypervolumeMethodComparison;
import br.usp.lti.newmoea.util.PerformanceProfiler.InstrumentationOverheadReport;
import br.usp.lti.newmoea.util.PerformanceProfiler.MemorySnapshot;
import br.usp.lti.newmoea.util.PerformanceProfiler.PerformanceRecord;
import br.usp.lti.newmoea.util.PerformanceProfiler.TimedMeasurement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;

/**
 * Testes automatizados para o utilitário de instrumentação e desempenho {@link PerformanceProfiler} (T09).
 *
 * <p>Cobre:
 * <ul>
 *   <li>Medição diferenciada de wall-clock time vs CPU time e snapshots de memória (heap usada,
 *       pico de heap, non-heap e limite máximo de heap);</li>
 *   <li>Limite padrão de timeout de 1 hora ({@code 3_600_000} ms) para análises de hipervolume e
 *       cancelamento seguro quando uma operação excede o timeout configurado;</li>
 *   <li>Cálculo de estatísticas descritivas (média, desvio padrão, mediana, mínimo e máximo sem
 *       remoção de outliers);</li>
 *   <li>Formatação, escrita, leitura e validação estrutural do CSV {@code docs/performance/performance-results.csv},
 *       incluindo a presença de medições para \(m = 15\);</li>
 *   <li>Teste de integração leve ({@code runs=1}) medindo separadamente algoritmo e todas as 7 etapas
 *       de pós-processamento/indicadores;</li>
 *   <li>Comparação entre {@code PISAHypervolume}, {@code WFGHypervolume} e {@code Hypervolume3D} sobre
 *       a mesma frente, dimensão e ponto de referência.</li>
 * </ul>
 */
class PerformanceProfilerTest {

    @Test
    @DisplayName("T09: Captura diferenciada de memória (heap usada, pico de heap, non-heap e limite máximo) e distinção entre wall time e CPU time")
    void testMemorySnapshotAndTimedMeasurementDistinction() {
        PerformanceProfiler.resetMemoryPeakUsage();
        MemorySnapshot mem = PerformanceProfiler.captureMemorySnapshot();

        assertThat(mem.heapUsedMb()).isGreaterThan(0.0);
        assertThat(mem.heapCommittedMb()).isGreaterThanOrEqualTo(mem.heapUsedMb());
        assertThat(mem.heapPeakMb()).isGreaterThanOrEqualTo(mem.heapUsedMb());
        assertThat(mem.heapMaxMb()).isGreaterThanOrEqualTo(mem.heapCommittedMb());
        assertThat(mem.nonHeapUsedMb()).isGreaterThan(0.0);
        assertThat(mem.nonHeapPeakMb()).isGreaterThanOrEqualTo(mem.nonHeapUsedMb());

        TimedMeasurement<Double> timed =
                PerformanceProfiler.measureOperation(
                        () -> {
                            double acc = 0.0;
                            double[] tempAllocation = new double[50_000];
                            for (int i = 0; i < tempAllocation.length; i++) {
                                tempAllocation[i] = Math.sin(i * 0.001);
                                acc += tempAllocation[i];
                            }
                            return acc;
                        },
                        false);

        assertThat(timed.result()).isFinite();
        assertThat(timed.wallTimeMs()).isGreaterThan(0.0);
        assertThat(timed.cpuTimeMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(timed.memoryAfter().heapPeakMb())
                .isGreaterThanOrEqualTo(timed.memoryAfter().heapUsedMb());
    }

    @Test
    @DisplayName("T09: Limite padrão de timeout de HV é de 1 hora (3.600.000 ms) e measureOperationWithTimeout encerra com segurança em caso de estouro")
    void testDefaultOneHourHypervolumeTimeoutAndSafeCancellation() {
        assertThat(PerformanceProfiler.DEFAULT_HV_TIMEOUT_MS).isEqualTo(3_600_000L);
        String previousProp = System.getProperty(PerformanceProfiler.HV_TIMEOUT_MS_PROPERTY);
        try {
            System.clearProperty(PerformanceProfiler.HV_TIMEOUT_MS_PROPERTY);
            assertThat(PerformanceProfiler.getHypervolumeTimeoutMs()).isEqualTo(3_600_000L);

            System.setProperty(PerformanceProfiler.HV_TIMEOUT_MS_PROPERTY, "120000");
            assertThat(PerformanceProfiler.getHypervolumeTimeoutMs()).isEqualTo(120_000L);
        } finally {
            if (previousProp == null) {
                System.clearProperty(PerformanceProfiler.HV_TIMEOUT_MS_PROPERTY);
            } else {
                System.setProperty(PerformanceProfiler.HV_TIMEOUT_MS_PROPERTY, previousProp);
            }
        }

        // Verifica que uma operação lenta que excede o timeout retorna o valor de fallback sem bloquear a JVM
        TimedMeasurement<Double> timedOut =
                PerformanceProfiler.measureOperationWithTimeout(
                        () -> {
                            try {
                                Thread.sleep(5_000L);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                            return 42.0;
                        },
                        50L,
                        Double.NaN);

        assertThat(timedOut.result()).isNaN();
        assertThat(timedOut.wallTimeMs()).isGreaterThanOrEqualTo(30.0).isLessThan(2_000.0);
    }

    @Test
    @DisplayName("T09: Cálculo de estatísticas descritivas (média, DP, mediana, mín, máx) preserva todos os dados sem remover outliers")
    void testDescriptiveStatsWithoutOutlierRemoval() {
        List<Double> sampleWithOutlier = List.of(10.0, 12.0, 11.0, 13.0, 104.0);
        DescriptiveStats stats = PerformanceProfiler.computeStats(sampleWithOutlier);

        assertThat(stats.count()).isEqualTo(5);
        assertThat(stats.rawValues()).containsExactly(10.0, 12.0, 11.0, 13.0, 104.0);
        assertThat(stats.min()).isEqualTo(10.0);
        assertThat(stats.max()).isEqualTo(104.0);
        assertThat(stats.median()).isEqualTo(12.0);
        assertThat(stats.mean()).isCloseTo(30.0, Offset.offset(1e-9));
        assertThat(stats.stdDev()).isGreaterThan(40.0);

        assertThatThrownBy(() -> PerformanceProfiler.computeStats(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T09: Avaliação quantitativa de overhead da instrumentação retorna valores finitos e baixos")
    void testInstrumentationOverheadEvaluation() {
        InstrumentationOverheadReport report =
                PerformanceProfiler.evaluateInstrumentationOverhead(100);
        assertThat(report.iterations()).isEqualTo(100);
        assertThat(report.perCallWallMicros()).isFinite().isGreaterThan(0.0);
        assertThat(report.perCallCpuMicros()).isFinite().isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("T09: Formatação, escrita e leitura de CSV preservam o cabeçalho obrigatório e os campos numéricos")
    void testCsvFormattingWriteAndRead(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("test-performance.csv");
        PerformanceRecord rec1 =
                new PerformanceRecord(
                        "2026-11-04T15:00:00Z",
                        "WFG4",
                        "NSGA-III",
                        15,
                        135,
                        405000,
                        1,
                        15407.0112,
                        15156.2500,
                        PerformanceProfiler.STAGE_PISA_HV,
                        25.2263,
                        15.6250,
                        53.48,
                        81.09,
                        135,
                        135);
        PerformanceRecord rec2 =
                new PerformanceRecord(
                        "2026-11-04T15:00:01Z",
                        "DTLZ2",
                        "MOEA/DD",
                        3,
                        91,
                        22750,
                        1,
                        272.0300,
                        281.2500,
                        PerformanceProfiler.STAGE_NORMALIZED_HV,
                        62.3618,
                        59.3750,
                        131.47,
                        217.06,
                        91,
                        10000);

        PerformanceProfiler.writeRecordsToCsv(csvFile, List.of(rec1), false);
        PerformanceProfiler.writeRecordsToCsv(csvFile, List.of(rec2), true);

        List<String> rawLines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        assertThat(rawLines).hasSize(3);
        assertThat(rawLines.get(0)).isEqualTo(PerformanceProfiler.CSV_HEADER);

        List<PerformanceRecord> parsed = PerformanceProfiler.readRecordsFromCsv(csvFile);
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).problem()).isEqualTo("WFG4");
        assertThat(parsed.get(0).algorithm()).isEqualTo("NSGA-III");
        assertThat(parsed.get(0).m()).isEqualTo(15);
        assertThat(parsed.get(0).indicator()).isEqualTo(PerformanceProfiler.STAGE_PISA_HV);
        assertThat(parsed.get(1).problem()).isEqualTo("DTLZ2");
        assertThat(parsed.get(1).algorithm()).isEqualTo("MOEA/DD");
        assertThat(parsed.get(1).referencePoints()).isEqualTo(10000);
    }

    @Test
    @DisplayName("T09: Validação do arquivo versionado docs/performance/performance-results.csv (cabeçalho, 7 etapas separadas e medições para m=15)")
    void testVersionedPerformanceResultsCsvIntegrity() throws IOException {
        Path csvPath = PerformanceProfiler.DEFAULT_RESULTS_CSV_PATH;
        assertThat(csvPath).exists().isRegularFile();

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).isEqualTo(PerformanceProfiler.CSV_HEADER);

        List<PerformanceRecord> records = PerformanceProfiler.readRecordsFromCsv(csvPath);
        assertThat(records.size()).isGreaterThanOrEqualTo(140);

        Set<String> indicators =
                records.stream().map(PerformanceRecord::indicator).collect(Collectors.toSet());
        assertThat(indicators)
                .contains(
                        PerformanceProfiler.STAGE_FRONT_LOAD,
                        PerformanceProfiler.STAGE_NORMALIZATION,
                        PerformanceProfiler.STAGE_PISA_HV,
                        PerformanceProfiler.STAGE_NORMALIZED_HV,
                        PerformanceProfiler.STAGE_IGD,
                        PerformanceProfiler.STAGE_IGD_PLUS,
                        PerformanceProfiler.STAGE_R2);

        Set<Integer> dimensions =
                records.stream().map(PerformanceRecord::m).collect(Collectors.toSet());
        assertThat(dimensions).contains(3, 8, 15);

        long m15Count = records.stream().filter(r -> r.m() == 15).count();
        assertThat(m15Count).isGreaterThanOrEqualTo(140L);

        for (PerformanceRecord r : records) {
            assertThat(r.algorithmWallMs()).isGreaterThan(0.0);
            assertThat(r.algorithmCpuMs()).isGreaterThanOrEqualTo(0.0);
            assertThat(r.indicatorWallMs()).isGreaterThanOrEqualTo(0.0);
            assertThat(r.indicatorCpuMs()).isGreaterThanOrEqualTo(0.0);
            assertThat(r.heapUsedMb()).isGreaterThan(0.0);
            assertThat(r.heapPeakMb()).isGreaterThanOrEqualTo(r.heapUsedMb());
            assertThat(r.solutions()).isGreaterThan(0);
            assertThat(r.referencePoints()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("T09: Integração leve (runs=1) mede separadamente algoritmo e cada uma das 7 etapas pós-algoritmo")
    void testLightweightSingleRunIntegration() {
        int m = 3;
        int popSize = StaticParams.getPopulationSize(m);
        Problem<DoubleSolution> problem = new DTLZ2(12, m);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation =
                new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
        SelectionOperator<List<DoubleSolution>, DoubleSolution> selection =
                new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>());

        Algorithm<List<DoubleSolution>> fastAlg =
                new NSGAIIIBuilder<>(problem)
                        .setCrossoverOperator(crossover)
                        .setMutationOperator(mutation)
                        .setSelectionOperator(selection)
                        .setMaxIterations(5)
                        .setNumberOfDivisions(StaticParams.getOuterDivisions(m))
                        .build();

        Path refPath = PerformanceProfiler.resolveReferenceFrontPath("DTLZ2", m);
        List<PerformanceRecord> records =
                PerformanceProfiler.profileCustomAlgorithmRun(
                        "DTLZ2", "NSGA-III", m, popSize, popSize * 5, 1, fastAlg, refPath);

        assertThat(records).hasSize(7);
        assertThat(records.stream().map(PerformanceRecord::indicator).toList())
                .containsExactly(
                        PerformanceProfiler.STAGE_FRONT_LOAD,
                        PerformanceProfiler.STAGE_NORMALIZATION,
                        PerformanceProfiler.STAGE_PISA_HV,
                        PerformanceProfiler.STAGE_NORMALIZED_HV,
                        PerformanceProfiler.STAGE_IGD,
                        PerformanceProfiler.STAGE_IGD_PLUS,
                        PerformanceProfiler.STAGE_R2);

        for (PerformanceRecord rec : records) {
            assertThat(rec.algorithmWallMs()).isGreaterThan(0.0);
            assertThat(rec.indicatorWallMs()).isGreaterThan(0.0);
            assertThat(rec.solutions()).isGreaterThan(0);
            assertThat(rec.referencePoints()).isEqualTo(10000);
        }
    }

    @Test
    @DisplayName("T09: Comparação entre PISAHypervolume, WFGHypervolume e Hypervolume3D confirma exatidão e reprodutibilidade")
    void testCompareHypervolumeMethodsExactAgreement() {
        double[][] sampleFront3D = {
            {0.2, 0.3, 0.8},
            {0.4, 0.5, 0.4},
            {0.7, 0.2, 0.6}
        };
        List<HypervolumeMethodComparison> comps =
                PerformanceProfiler.compareHypervolumeMethods("WFG4", sampleFront3D, 3, 1, 3);

        assertThat(comps).hasSize(3);
        assertThat(comps.stream().map(HypervolumeMethodComparison::method).toList())
                .containsExactly("PISAHypervolume", "WFGHypervolume", "Hypervolume3D");

        for (HypervolumeMethodComparison c : comps) {
            assertThat(c.exact()).isTrue();
            assertThat(c.hvValue()).isGreaterThan(0.0);
            assertThat(c.absError()).isLessThan(1e-12);
            assertThat(c.relError()).isLessThan(1e-12);
            assertThat(c.maxSpreadAcrossRuns()).isEqualTo(0.0);
            assertThat(c.wallStats().mean()).isGreaterThan(0.0);
        }
    }
}

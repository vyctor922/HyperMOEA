package br.usp.lti.newmoea.util;

import br.usp.lti.newmoea.algorithm.fdea.FDEABuilder;
import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.study.WFGStudy;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder;
import org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.crossover.impl.SBXCrossover;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.mutation.impl.PolynomialMutation;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.operator.selection.impl.BinaryTournamentSelection;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ2;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ3;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ4;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ5;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ6;
import org.uma.jmetal.problem.multiobjective.dtlz.DTLZ7;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.problem.multiobjective.wfg.WFG2;
import org.uma.jmetal.problem.multiobjective.wfg.WFG3;
import org.uma.jmetal.problem.multiobjective.wfg.WFG4;
import org.uma.jmetal.problem.multiobjective.wfg.WFG5;
import org.uma.jmetal.problem.multiobjective.wfg.WFG6;
import org.uma.jmetal.problem.multiobjective.wfg.WFG7;
import org.uma.jmetal.problem.multiobjective.wfg.WFG8;
import org.uma.jmetal.problem.multiobjective.wfg.WFG9;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.Hypervolume3D;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.NormalizeUtils;
import org.uma.jmetal.util.VectorUtils;
import org.uma.jmetal.util.archive.impl.NonDominatedSolutionListArchive;
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator;

/**
 * Utilitário de instrumentação e avaliação reprodutível de desempenho (T09).
 *
 * <p>Emprega exclusivamente recursos nativos da JVM ({@link System#nanoTime()},
 * {@link ThreadMXBean}, {@link MemoryMXBean}, {@link MemoryPoolMXBean} e {@link Runtime})
 * para medir separadamente o tempo decorrido (wall-clock time), o tempo de CPU (CPU time)
 * e o uso de memória (heap usada, pico de heap, non-heap usada e limite máximo de heap)
 * na execução dos algoritmos e de cada etapa/indicador de qualidade, sem alterar a
 * semântica científica do experimento.
 */
public final class PerformanceProfiler {

    /** Nome da propriedade JVM para habilitar perfilamento opcional em {@link WFGStudy} (desativado por padrão). */
    public static final String PROFILE_PERFORMANCE_PROPERTY = "profilePerformance";

    /** Nome da propriedade JVM para configurar o timeout máximo de uma análise de hipervolume em ms. */
    public static final String HV_TIMEOUT_MS_PROPERTY = "perf.hvTimeoutMs";

    /**
     * Limite máximo padrão de 1 hora ({@code 3_600_000} ms = {@code Duration.ofHours(1)}) por
     * análise/cálculo individual de hipervolume ({@link PISAHypervolume},
     * {@link NormalizedHypervolume} ou {@link WFGHypervolume}), prevenindo bloqueios indefinidos
     * em alta dimensionalidade.
     */
    public static final long DEFAULT_HV_TIMEOUT_MS = 3_600_000L;

    /** Caminho padrão para exportação dos resultados de desempenho em CSV. */
    public static final Path DEFAULT_RESULTS_CSV_PATH =
            Path.of("docs", "performance", "performance-results.csv");

    /** Cabeçalho mínimo obrigatório exigido em {@code tasks/task9.md}. */
    public static final String CSV_HEADER =
            "timestamp,problem,algorithm,m,population,max_evaluations,run,"
                    + "algorithm_wall_ms,algorithm_cpu_ms,indicator,indicator_wall_ms,"
                    + "indicator_cpu_ms,heap_used_mb,heap_peak_mb,solutions,reference_points";

    /** Fator de conversão de bytes para megabytes (MiB, \(1024^2\)). */
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    /** Fator de conversão de nanossegundos para milissegundos (\(10^6\)). */
    private static final double NANOS_PER_MS = 1_000_000.0;

    /** Identificadores padronizados das etapas/indicadores medidos separadamente. */
    public static final String STAGE_FRONT_LOAD = "FRONT_LOAD";
    public static final String STAGE_NORMALIZATION = "NORMALIZATION";
    public static final String STAGE_PISA_HV = "PISAHypervolume";
    public static final String STAGE_NORMALIZED_HV = "NormalizedHypervolume";
    public static final String STAGE_IGD = "IGD";
    public static final String STAGE_IGD_PLUS = "IGD+";
    public static final String STAGE_R2 = "R2";

    private PerformanceProfiler() {
        // Classe utilitária
    }

    /**
     * Verifica se o perfilamento opcional de desempenho está habilitado via propriedade de sistema
     * {@code -DprofilePerformance=true} (desativado por padrão).
     *
     * @return {@code true} somente se {@code -DprofilePerformance=true}
     */
    public static boolean isProfilePerformanceEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROFILE_PERFORMANCE_PROPERTY, "false"));
    }

    /**
     * Instantâneo diferenciado de memória da JVM, separando memória heap usada atual,
     * pico de memória heap observado nos pools de heap, memória non-heap usada e limite
     * máximo configurado de heap.
     *
     * @param heapUsedMb memória heap em uso no momento da coleta (MB)
     * @param heapCommittedMb memória heap comprometida pela JVM (MB)
     * @param heapPeakMb soma dos picos de memória usada ({@link MemoryPoolMXBean#getPeakUsage()}) nos pools {@link MemoryType#HEAP} (MB)
     * @param heapMaxMb limite máximo de memória heap configurado na JVM (MB)
     * @param nonHeapUsedMb memória non-heap (Metaspace, CodeCache, Compressed Class Space) em uso (MB)
     * @param nonHeapPeakMb soma dos picos de memória usada nos pools {@link MemoryType#NON_HEAP} (MB)
     */
    public record MemorySnapshot(
            double heapUsedMb,
            double heapCommittedMb,
            double heapPeakMb,
            double heapMaxMb,
            double nonHeapUsedMb,
            double nonHeapPeakMb) {}

    /**
     * Resultado de uma medição cronometrada isolada (com ou sem valor de retorno).
     *
     * @param <T> tipo do resultado produzido pela operação medida
     * @param result valor retornado pela operação medida
     * @param wallTimeMs tempo decorrido (wall-clock time) em milissegundos (\(\ge 0\))
     * @param cpuTimeMs tempo de CPU consumido em milissegundos (\(\ge 0\))
     * @param memoryBefore instantâneo de memória imediatamente antes da execução
     * @param memoryAfter instantâneo de memória imediatamente após a execução (com pico atualizado)
     */
    public record TimedMeasurement<T>(
            T result,
            double wallTimeMs,
            double cpuTimeMs,
            MemorySnapshot memoryBefore,
            MemorySnapshot memoryAfter) {}

    /**
     * Linha estruturada correspondente ao formato CSV obrigatório de {@code tasks/task9.md}.
     *
     * @param timestamp carimbo de tempo ISO-8601 da medição
     * @param problem nome do problema (ex.: {@code WFG4}, {@code DTLZ2})
     * @param algorithm nome canônico do algoritmo (ex.: {@code NSGA-III}, {@code MOEA/DD}, {@code RVEA}, {@code AGE-MOEA}, {@code FDEA})
     * @param m número de objetivos
     * @param population tamanho da população configurado
     * @param maxEvaluations orçamento máximo de avaliações configurado
     * @param run índice da repetição (\(1 \dots R\))
     * @param algorithmWallMs tempo de parede (wall-clock) da execução do algoritmo em ms
     * @param algorithmCpuMs tempo de CPU da execução do algoritmo em ms
     * @param indicator nome da etapa/indicador medido ({@code FRONT_LOAD}, {@code NORMALIZATION}, {@code PISAHypervolume}, {@code NormalizedHypervolume}, {@code IGD}, {@code IGD+}, {@code R2})
     * @param indicatorWallMs tempo de parede da etapa/indicador em ms
     * @param indicatorCpuMs tempo de CPU da etapa/indicador em ms
     * @param heapUsedMb memória heap usada após a etapa/indicador em MB
     * @param heapPeakMb pico de memória heap observado durante a etapa/indicador em MB
     * @param solutions número de soluções não dominadas avaliadas na frente produzida
     * @param referencePoints tamanho (cardinalidade) da frente de referência utilizada
     */
    public record PerformanceRecord(
            String timestamp,
            String problem,
            String algorithm,
            int m,
            int population,
            int maxEvaluations,
            int run,
            double algorithmWallMs,
            double algorithmCpuMs,
            String indicator,
            double indicatorWallMs,
            double indicatorCpuMs,
            double heapUsedMb,
            double heapPeakMb,
            int solutions,
            int referencePoints) {

        /** Converte o registro em uma linha CSV formatada com ponto decimal invariante ({@link Locale#US}). */
        public String toCsvLine() {
            return String.format(
                    Locale.US,
                    "%s,%s,%s,%d,%d,%d,%d,%.4f,%.4f,%s,%.4f,%.4f,%.2f,%.2f,%d,%d",
                    escapeCsvToken(timestamp),
                    escapeCsvToken(problem),
                    escapeCsvToken(algorithm),
                    m,
                    population,
                    maxEvaluations,
                    run,
                    algorithmWallMs,
                    algorithmCpuMs,
                    escapeCsvToken(indicator),
                    indicatorWallMs,
                    indicatorCpuMs,
                    heapUsedMb,
                    heapPeakMb,
                    solutions,
                    referencePoints);
        }

        /** Faz o parse de uma linha CSV no formato padrão para {@link PerformanceRecord}. */
        public static PerformanceRecord fromCsvLine(String csvLine) {
            if (csvLine == null || csvLine.isBlank()) {
                throw new IllegalArgumentException("Linha CSV de desempenho não pode ser nula ou vazia.");
            }
            String[] parts = csvLine.trim().split(",", -1);
            if (parts.length < 16) {
                throw new IllegalArgumentException(
                        "Linha CSV inválida (esperadas pelo menos 16 colunas, encontradas "
                                + parts.length
                                + "): "
                                + csvLine);
            }
            return new PerformanceRecord(
                    parts[0].trim(),
                    parts[1].trim(),
                    parts[2].trim(),
                    Integer.parseInt(parts[3].trim()),
                    Integer.parseInt(parts[4].trim()),
                    Integer.parseInt(parts[5].trim()),
                    Integer.parseInt(parts[6].trim()),
                    Double.parseDouble(parts[7].trim()),
                    Double.parseDouble(parts[8].trim()),
                    parts[9].trim(),
                    Double.parseDouble(parts[10].trim()),
                    Double.parseDouble(parts[11].trim()),
                    Double.parseDouble(parts[12].trim()),
                    Double.parseDouble(parts[13].trim()),
                    Integer.parseInt(parts[14].trim()),
                    Integer.parseInt(parts[15].trim()));
        }
    }

    /**
     * Resumo estatístico descritivo completo (sem remoção automática de outliers).
     *
     * @param count número de observações (\(n \ge 1\))
     * @param mean média aritmética
     * @param stdDev desvio padrão amostral (\(s\), ou \(0.0\) se \(n = 1\))
     * @param median mediana
     * @param min valor mínimo observado
     * @param max valor máximo observado
     * @param rawValues cópia imutável dos valores brutos medidos (preservando outliers)
     */
    public record DescriptiveStats(
            int count,
            double mean,
            double stdDev,
            double median,
            double min,
            double max,
            List<Double> rawValues) {}

    /**
     * Registro comparativo entre métodos de cálculo de hipervolume sobre a mesma frente de entrada,
     * mesmo número de objetivos e mesmo ponto de referência.
     *
     * @param problem problema de origem da frente
     * @param m número de objetivos
     * @param frontSize número de pontos na frente avaliada
     * @param method identificador do método (ex.: {@code PISAHypervolume}, {@code WFGHypervolume}, {@code Hypervolume3D})
     * @param exact verdadeiro se o algoritmo for matematicamente exato
     * @param hvValue valor de hipervolume calculado
     * @param referenceExactHv valor de hipervolume de referência exato ({@code PISAHypervolume})
     * @param absError erro absoluto \(|\text{HV} - \text{HV}_{\text{ref}}|\)
     * @param relError erro relativo \(|\text{HV} - \text{HV}_{\text{ref}}| / \max(|\text{HV}_{\text{ref}}|, 10^{-15})\)
     * @param wallStats estatísticas de tempo de parede (ms) nas repetições
     * @param cpuStats estatísticas de tempo de CPU (ms) nas repetições
     * @param heapPeakMb pico de memória heap observado (MB)
     * @param maxSpreadAcrossRuns maior diferença entre execuções repetidas do mesmo método (estabilidade/reprodutibilidade)
     */
    public record HypervolumeMethodComparison(
            String problem,
            int m,
            int frontSize,
            String method,
            boolean exact,
            double hvValue,
            double referenceExactHv,
            double absError,
            double relError,
            DescriptiveStats wallStats,
            DescriptiveStats cpuStats,
            double heapPeakMb,
            double maxSpreadAcrossRuns) {}

    /**
     * Avaliação quantitativa do overhead introduzido pela instrumentação JMX/nanoTime.
     *
     * @param iterations número de invocações medidas
     * @param perCallWallMicros tempo médio de parede por captura completa de tempo + memória (em microssegundos)
     * @param perCallCpuMicros tempo médio de CPU por captura completa de tempo + memória (em microssegundos)
     */
    public record InstrumentationOverheadReport(
            int iterations,
            double perCallWallMicros,
            double perCallCpuMicros) {}

    /**
     * Reinicia os contadores de pico de uso de todos os pools de memória gerenciados pela JVM
     * ({@link MemoryPoolMXBean#resetPeakUsage()}).
     */
    public static void resetMemoryPeakUsage() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            try {
                pool.resetPeakUsage();
            } catch (SecurityException ignored) {
                // Ignorado caso o SecurityManager restrinja resetPeakUsage
            }
        }
    }

    /**
     * Captura um instantâneo diferenciado de memória da JVM (heap usada, heap comprometida,
     * pico de heap nos pools {@link MemoryType#HEAP}, limite máximo de heap configurado,
     * non-heap usada e pico de non-heap nos pools {@link MemoryType#NON_HEAP}).
     *
     * @return {@link MemorySnapshot} com valores em MB (MiB)
     */
    public static MemorySnapshot captureMemorySnapshot() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        Runtime runtime = Runtime.getRuntime();

        long heapUsedBytes = Math.max(0L, heapUsage.getUsed());
        long heapCommittedBytes = Math.max(0L, heapUsage.getCommitted());
        long heapMaxBytes = heapUsage.getMax() > 0 ? heapUsage.getMax() : runtime.maxMemory();
        long nonHeapUsedBytes = Math.max(0L, nonHeapUsage.getUsed());

        long heapPeakBytes = 0L;
        long nonHeapPeakBytes = 0L;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage peak = pool.getPeakUsage();
            if (peak != null && peak.getUsed() > 0) {
                if (pool.getType() == MemoryType.HEAP) {
                    heapPeakBytes += peak.getUsed();
                } else if (pool.getType() == MemoryType.NON_HEAP) {
                    nonHeapPeakBytes += peak.getUsed();
                }
            }
        }
        heapPeakBytes = Math.max(heapPeakBytes, heapUsedBytes);
        nonHeapPeakBytes = Math.max(nonHeapPeakBytes, nonHeapUsedBytes);

        return new MemorySnapshot(
                heapUsedBytes / BYTES_PER_MB,
                heapCommittedBytes / BYTES_PER_MB,
                heapPeakBytes / BYTES_PER_MB,
                heapMaxBytes / BYTES_PER_MB,
                nonHeapUsedBytes / BYTES_PER_MB,
                nonHeapPeakBytes / BYTES_PER_MB);
    }

    /**
     * Retorna o tempo de CPU acumulado da thread atual em nanossegundos
     * ({@link ThreadMXBean#getCurrentThreadCpuTime()}), ou {@code 0L} se não suportado.
     *
     * @return tempo de CPU da thread atual em nanossegundos
     */
    public static long readCurrentThreadCpuTimeNanos() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!threadBean.isThreadCpuTimeSupported()) {
            return 0L;
        }
        if (!threadBean.isThreadCpuTimeEnabled()) {
            try {
                threadBean.setThreadCpuTimeEnabled(true);
            } catch (SecurityException | UnsupportedOperationException ignored) {
                return 0L;
            }
        }
        long cpuNanos = threadBean.getCurrentThreadCpuTime();
        return Math.max(0L, cpuNanos);
    }

    /**
     * Retorna um mapa com o tempo de CPU (em nanossegundos) de todas as threads vivas da JVM.
     * Permite medir o consumo total de CPU mesmo quando uma tarefa utiliza múltiplas threads.
     *
     * @return mapa {@code threadId -> cpuTimeNanos}
     */
    public static Map<Long, Long> snapshotAllThreadsCpuTimeNanos() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!threadBean.isThreadCpuTimeSupported()) {
            return Collections.emptyMap();
        }
        if (!threadBean.isThreadCpuTimeEnabled()) {
            try {
                threadBean.setThreadCpuTimeEnabled(true);
            } catch (SecurityException | UnsupportedOperationException ignored) {
                return Collections.emptyMap();
            }
        }
        long[] ids = threadBean.getAllThreadIds();
        Map<Long, Long> snapshot = new LinkedHashMap<>(ids.length * 2);
        for (long id : ids) {
            long time = threadBean.getThreadCpuTime(id);
            if (time >= 0L) {
                snapshot.put(id, time);
            }
        }
        return snapshot;
    }

    /**
     * Calcula o tempo total de CPU (em nanossegundos) consumido entre dois snapshots de threads,
     * garantindo que o valor nunca seja inferior ao delta de CPU da thread atual.
     *
     * @param before snapshot antes da execução
     * @param after snapshot após a execução
     * @param currentThreadDeltaNanos delta de CPU da thread principal da medição
     * @return tempo de CPU total em nanossegundos (\(\ge 0\))
     */
    public static long computeMultiThreadCpuDeltaNanos(
            Map<Long, Long> before, Map<Long, Long> after, long currentThreadDeltaNanos) {
        long totalDelta = 0L;
        for (Map.Entry<Long, Long> entry : after.entrySet()) {
            long threadId = entry.getKey();
            long afterTime = entry.getValue();
            long beforeTime = before.getOrDefault(threadId, 0L);
            if (afterTime >= beforeTime) {
                totalDelta += (afterTime - beforeTime);
            }
        }
        return Math.max(Math.max(0L, currentThreadDeltaNanos), totalDelta);
    }

    /**
     * Mede separadamente o tempo decorrido (wall-clock time), o tempo de CPU e o uso/pico de
     * memória de uma operação síncrona ou multi-thread fornecida via {@link Supplier}.
     *
     * @param <T> tipo do resultado da operação
     * @param task operação a ser executada e medida
     * @param includeAllJvmThreads se {@code true}, soma o delta de CPU de todas as threads ativas;
     *     se {@code false}, mede estritamente {@link ThreadMXBean#getCurrentThreadCpuTime()}
     * @return {@link TimedMeasurement} contendo resultado, tempos e snapshots de memória
     */
    public static <T> TimedMeasurement<T> measureOperation(
            Supplier<T> task, boolean includeAllJvmThreads) {
        Objects.requireNonNull(task, "A operação a ser medida não pode ser nula.");
        resetMemoryPeakUsage();
        MemorySnapshot memBefore = captureMemorySnapshot();

        Map<Long, Long> allThreadsBefore =
                includeAllJvmThreads ? snapshotAllThreadsCpuTimeNanos() : Collections.emptyMap();
        long cpuStart = readCurrentThreadCpuTimeNanos();
        long wallStart = System.nanoTime();

        T result = task.get();

        long wallEnd = System.nanoTime();
        long cpuEnd = readCurrentThreadCpuTimeNanos();
        Map<Long, Long> allThreadsAfter =
                includeAllJvmThreads ? snapshotAllThreadsCpuTimeNanos() : Collections.emptyMap();

        MemorySnapshot memAfter = captureMemorySnapshot();

        long wallNanos = Math.max(0L, wallEnd - wallStart);
        long currentCpuNanos = Math.max(0L, cpuEnd - cpuStart);
        long effectiveCpuNanos =
                includeAllJvmThreads
                        ? computeMultiThreadCpuDeltaNanos(
                                allThreadsBefore, allThreadsAfter, currentCpuNanos)
                        : currentCpuNanos;

        double PeakHeapMb = Math.max(memBefore.heapPeakMb(), memAfter.heapPeakMb());
        double PeakNonHeapMb = Math.max(memBefore.nonHeapPeakMb(), memAfter.nonHeapPeakMb());
        MemorySnapshot resolvedAfter =
                new MemorySnapshot(
                        memAfter.heapUsedMb(),
                        memAfter.heapCommittedMb(),
                        PeakHeapMb,
                        memAfter.heapMaxMb(),
                        memAfter.nonHeapUsedMb(),
                        PeakNonHeapMb);

        return new TimedMeasurement<>(
                result,
                wallNanos / NANOS_PER_MS,
                effectiveCpuNanos / NANOS_PER_MS,
                memBefore,
                resolvedAfter);
    }

    /**
     * Retorna o limite máximo de tempo (timeout em milissegundos) configurado para cada cálculo
     * individual de hipervolume ({@link PISAHypervolume}, {@link NormalizedHypervolume} ou
     * {@link WFGHypervolume}). Por padrão é {@link #DEFAULT_HV_TIMEOUT_MS} (1 hora = {@code 3_600_000} ms).
     *
     * @return timeout máximo em milissegundos (\(\ge 1\))
     */
    public static long getHypervolumeTimeoutMs() {
        long timeout = Long.getLong(HV_TIMEOUT_MS_PROPERTY, DEFAULT_HV_TIMEOUT_MS);
        return Math.max(1L, timeout);
    }

    /**
     * Mede separadamente o tempo decorrido (wall-clock time), o tempo de CPU da thread trabalhadora
     * e o uso/pico de memória de uma operação sujeita a um limite máximo de tempo ({@code timeoutMs}),
     * como o cálculo recursivo de {@link PISAHypervolume}, {@link NormalizedHypervolume} ou
     * {@link WFGHypervolume}. Caso o tempo exceda {@code timeoutMs}, a execução é interrompida com
     * segurança sem bloquear a JVM indefinidamente.
     *
     * @param <T> tipo do resultado da operação
     * @param task operação a ser executada e medida
     * @param timeoutMs limite máximo de espera em milissegundos
     * @param fallbackValue valor retornado caso ocorra timeout
     * @return {@link TimedMeasurement} contendo resultado (ou {@code fallbackValue}), tempos e memória
     */
    public static <T> TimedMeasurement<T> measureOperationWithTimeout(
            Supplier<T> task, long timeoutMs, T fallbackValue) {
        Objects.requireNonNull(task, "A operação a ser medida não pode ser nula.");
        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("O timeoutMs deve ser positivo, mas foi: " + timeoutMs);
        }
        resetMemoryPeakUsage();
        MemorySnapshot memBefore = captureMemorySnapshot();

        AtomicLong workerThreadId = new AtomicLong(-1L);
        AtomicLong workerCpuStart = new AtomicLong(0L);
        AtomicLong workerCpuEnd = new AtomicLong(-1L);
        long wallStart = System.nanoTime();

        CompletableFuture<T> future = new CompletableFuture<>();
        Thread worker =
                new Thread(
                        () -> {
                            workerThreadId.set(Thread.currentThread().threadId());
                            workerCpuStart.set(readCurrentThreadCpuTimeNanos());
                            try {
                                T val = task.get();
                                workerCpuEnd.set(readCurrentThreadCpuTimeNanos());
                                future.complete(val);
                            } catch (Throwable t) {
                                workerCpuEnd.set(readCurrentThreadCpuTimeNanos());
                                future.completeExceptionally(t);
                            }
                        },
                        "PerformanceProfiler-HV-Worker");
        worker.setDaemon(true);
        worker.start();

        T result;
        try {
            result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            worker.interrupt();
            result = fallbackValue;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.interrupt();
            result = fallbackValue;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Falha durante operação medida com timeout.", cause);
        }

        long wallEnd = System.nanoTime();
        long endCpu = workerCpuEnd.get();
        if (endCpu < 0L) {
            long tid = workerThreadId.get();
            if (tid >= 0L) {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                if (bean.isThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled()) {
                    long tCpu = bean.getThreadCpuTime(tid);
                    if (tCpu >= 0L) {
                        endCpu = tCpu;
                    }
                }
            }
        }
        long cpuNanos = (endCpu >= workerCpuStart.get()) ? (endCpu - workerCpuStart.get()) : 0L;
        long wallNanos = Math.max(0L, wallEnd - wallStart);

        MemorySnapshot memAfter = captureMemorySnapshot();
        double peakHeapMb = Math.max(memBefore.heapPeakMb(), memAfter.heapPeakMb());
        double peakNonHeapMb = Math.max(memBefore.nonHeapPeakMb(), memAfter.nonHeapPeakMb());
        MemorySnapshot resolvedAfter =
                new MemorySnapshot(
                        memAfter.heapUsedMb(),
                        memAfter.heapCommittedMb(),
                        peakHeapMb,
                        memAfter.heapMaxMb(),
                        memAfter.nonHeapUsedMb(),
                        peakNonHeapMb);

        return new TimedMeasurement<>(
                result,
                wallNanos / NANOS_PER_MS,
                cpuNanos / NANOS_PER_MS,
                memBefore,
                resolvedAfter);
    }

    /**
     * Mede o overhead intrínseco de chamadas consecutivas à instrumentação
     * ({@link #measureOperation(Supplier, boolean)} com operação vazia).
     *
     * @param iterations número de iterações (ex.: {@code 1000})
     * @return {@link InstrumentationOverheadReport} com o custo médio por chamada em microssegundos
     */
    public static InstrumentationOverheadReport evaluateInstrumentationOverhead(int iterations) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("O número de iterações deve ser positivo.");
        }
        // Warm-up
        for (int i = 0; i < Math.min(200, iterations); i++) {
            measureOperation(() -> 0, false);
        }
        long wallStart = System.nanoTime();
        long cpuStart = readCurrentThreadCpuTimeNanos();
        for (int i = 0; i < iterations; i++) {
            final int idx = i;
            measureOperation(() -> idx, false);
        }
        long wallElapsedNanos = Math.max(0L, System.nanoTime() - wallStart);
        long cpuElapsedNanos = Math.max(0L, readCurrentThreadCpuTimeNanos() - cpuStart);

        double perCallWallMicros = (wallElapsedNanos / 1_000.0) / iterations;
        double perCallCpuMicros = (cpuElapsedNanos / 1_000.0) / iterations;
        return new InstrumentationOverheadReport(iterations, perCallWallMicros, perCallCpuMicros);
    }

    /**
     * Calcula estatísticas descritivas completas (média, desvio padrão amostral, mediana,
     * mínimo e máximo) sem remover outliers.
     *
     * @param values lista de valores observados (não nula e não vazia)
     * @return {@link DescriptiveStats}
     */
    public static DescriptiveStats computeStats(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(
                    "A lista de valores para cálculo estatístico não pode ser nula ou vazia.");
        }
        List<Double> rawCopy = List.copyOf(values);
        double[] sorted = new double[rawCopy.size()];
        double sum = 0.0;
        for (int i = 0; i < rawCopy.size(); i++) {
            double v = rawCopy.get(i);
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("Valor não finito encontrado nas medições: " + v);
            }
            sorted[i] = v;
            sum += v;
        }
        Arrays.sort(sorted);
        int n = sorted.length;
        double mean = sum / n;
        double varianceSum = 0.0;
        for (double v : sorted) {
            double diff = v - mean;
            varianceSum += diff * diff;
        }
        double stdDev = (n > 1) ? Math.sqrt(varianceSum / (n - 1)) : 0.0;
        double median =
                (n % 2 == 1)
                        ? sorted[n / 2]
                        : 0.5 * (sorted[(n / 2) - 1] + sorted[n / 2]);
        double min = sorted[0];
        double max = sorted[n - 1];
        return new DescriptiveStats(n, mean, stdDev, median, min, max, rawCopy);
    }

    /**
     * Instancia o problema multiobjetivo correspondente ao nome canônico ({@code WFG1}–{@code WFG9}
     * ou {@code DTLZ1}–{@code DTLZ7}) e número de objetivos \(m\), seguindo exatamente a parametrização
     * de {@link WFGStudy} (\(k = 2(m - 1)\) e \(L = 20\)).
     *
     * @param problemName nome do problema (ex.: {@code "WFG4"}, {@code "DTLZ2"})
     * @param m número de objetivos (\(m \ge 2\))
     * @return instância de {@link Problem}
     */
    public static Problem<DoubleSolution> createProblem(String problemName, int m) {
        String canonical = StaticParams.normalizeProblemId(problemName);
        int k = 2 * (m - 1);
        int l = WFGStudy.L;
        return switch (canonical) {
            case "WFG1" -> new WFG1(k, l, m);
            case "WFG2" -> new WFG2(k, l, m);
            case "WFG3" -> new WFG3(k, l, m);
            case "WFG4" -> new WFG4(k, l, m);
            case "WFG5" -> new WFG5(k, l, m);
            case "WFG6" -> new WFG6(k, l, m);
            case "WFG7" -> new WFG7(k, l, m);
            case "WFG8" -> new WFG8(k, l, m);
            case "WFG9" -> new WFG9(k, l, m);
            case "DTLZ1" -> new DTLZ1(l, m);
            case "DTLZ2" -> new DTLZ2(l, m);
            case "DTLZ3" -> new DTLZ3(l, m);
            case "DTLZ4" -> new DTLZ4(l, m);
            case "DTLZ5" -> new DTLZ5(l, m);
            case "DTLZ6" -> new DTLZ6(l, m);
            case "DTLZ7" -> new DTLZ7(l, m);
            default -> throw new IllegalArgumentException("Problema não suportado: " + canonical);
        };
    }

    /**
     * Instancia um algoritmo do estudo para o problema e número de objetivos informados,
     * utilizando exatamente os operadores canônicos (\(\text{SBX}(1.0, 30.0)\) e
     * \(\text{PM}(1/n, 20.0)\)) e o orçamento padrão de {@link StaticParams#getMaxGenerations(int, String)}
     * (via builders de {@link WFGStudy}).
     *
     * @param algorithmName nome do algoritmo ({@code "NSGA-III"}, {@code "MOEA/DD"}, {@code "RVEA"}, {@code "AGE-MOEA"}, {@code "AGE-MOEA-II"} ou {@code "FDEA"})
     * @param problem problema multiobjetivo
     * @return instância configurada de {@link Algorithm}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Algorithm<List<DoubleSolution>> createStandardAlgorithm(
            String algorithmName, Problem<DoubleSolution> problem) {
        Objects.requireNonNull(algorithmName, "O nome do algoritmo não pode ser nulo.");
        Objects.requireNonNull(problem, "O problema não pode ser nulo.");
        int m = problem.numberOfObjectives();
        int populationSize = StaticParams.getPopulationSize(m);
        CrossoverOperator<DoubleSolution> crossover = new SBXCrossover(1.0, 30.0);
        MutationOperator<DoubleSolution> mutation =
                new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);

        String normalizedAlg = algorithmName.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedAlg) {
            case "NSGA-III", "NSGAIII" ->
                    WFGStudy.buildNSGAIII(problem, populationSize, crossover, mutation);
            case "MOEA/DD", "MOEADD" ->
                    WFGStudy.buildMOEADD(problem, populationSize, crossover, mutation);
            case "RVEA" ->
                    WFGStudy.buildRVEA(problem, populationSize, crossover, mutation);
            case "AGE-MOEA", "AGEMOEA" -> {
                int evenPop = (populationSize % 2 != 0) ? populationSize + 1 : populationSize;
                AGEMOEABuilder builder = new AGEMOEABuilder(problem);
                builder.setMaxIterations(StaticParams.getMaxGenerations(m, problem.name()));
                builder.setCrossoverOperator(crossover);
                builder.setMutationOperator(mutation);
                builder.setPopulationSize(evenPop);
                yield builder.build();
            }
            case "AGE-MOEA-II", "AGEMOEAII", "AGE2" -> {
                int evenPop = (populationSize % 2 != 0) ? populationSize + 1 : populationSize;
                AGEMOEAIIBuilder builder = new AGEMOEAIIBuilder(problem);
                builder.setMaxIterations(StaticParams.getMaxGenerations(m, problem.name()));
                builder.setCrossoverOperator(crossover);
                builder.setMutationOperator(mutation);
                builder.setPopulationSize(evenPop);
                yield builder.build();
            }
            case "FDEA" -> {
                int maxGenerations = StaticParams.getMaxGenerations(m, problem.name());
                int maxEvaluations = maxGenerations * populationSize;
                SelectionOperator selection =
                        new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>());
                yield new FDEABuilder(problem)
                        .setPopulationSize(populationSize)
                        .setMaxGenerations(maxGenerations)
                        .setMaxEvaluations(maxEvaluations)
                        .setCrossoverOperator(crossover)
                        .setMutationOperator(mutation)
                        .setSelectionOperator(selection)
                        .build();
            }
            default -> throw new IllegalArgumentException(
                    "Algoritmo não suportado para perfilamento: " + algorithmName);
        };
    }

    /**
     * Filtra uma lista de soluções mantendo apenas as soluções mutuamente não dominadas
     * (mesmo filtro aplicado pelo {@code ExecuteAlgorithms} / {@code ExperimentAlgorithm} do jMetal 7.8)
     * e converte seus vetores de objetivos para uma matriz {@code double[N][m]}.
     *
     * @param solutions lista de soluções retornada por {@code algorithm.result()}
     * @param m número esperado de objetivos
     * @return matriz {@code double[N_nd][m]} contendo as soluções não dominadas
     */
    public static double[][] extractNonDominatedFrontMatrix(
            List<DoubleSolution> solutions, int m) {
        if (solutions == null || solutions.isEmpty()) {
            return new double[0][m];
        }
        NonDominatedSolutionListArchive<DoubleSolution> archive =
                new NonDominatedSolutionListArchive<>();
        for (DoubleSolution sol : solutions) {
            archive.add(sol);
        }
        List<DoubleSolution> nonDominated = archive.solutions();
        double[][] matrix = new double[nonDominated.size()][m];
        for (int i = 0; i < nonDominated.size(); i++) {
            double[] obj = nonDominated.get(i).objectives();
            System.arraycopy(obj, 0, matrix[i], 0, m);
        }
        return matrix;
    }

    /**
     * Resolve o caminho do arquivo CSV da frente de referência oficial em {@code pareto_fronts/csv/}
     * para o problema e dimensão \(m\).
     *
     * @param problemName nome do problema (ex.: {@code "WFG4"}, {@code "DTLZ2"})
     * @param m número de objetivos
     * @return {@link Path} para {@code pareto_fronts/csv/<PROBLEM>.<m>D.csv}
     */
    public static Path resolveReferenceFrontPath(String problemName, int m) {
        String canonical = StaticParams.normalizeProblemId(problemName);
        Path path = Path.of("pareto_fronts", "csv", canonical + "." + m + "D.csv");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "Frente de referência não encontrada em pareto_fronts/csv para "
                            + canonical
                            + " com m="
                            + m
                            + ": "
                            + path);
        }
        return path;
    }

    /**
     * Executa e mede separadamente uma única repetição (`run`) de uma configuração
     * problema–algoritmo–\(m\), registrando:
     * <ol>
     *   <li>Execução do algoritmo ({@code algorithm_wall_ms}, {@code algorithm_cpu_ms});</li>
     *   <li>Leitura da frente de referência ({@link #STAGE_FRONT_LOAD});</li>
     *   <li>Normalização da frente de referência e da frente aproximada ({@link #STAGE_NORMALIZATION});</li>
     *   <li>Cálculo de {@link #STAGE_PISA_HV};</li>
     *   <li>Cálculo de {@link #STAGE_NORMALIZED_HV};</li>
     *   <li>Cálculo de {@link #STAGE_IGD};</li>
     *   <li>Cálculo de {@link #STAGE_IGD_PLUS};</li>
     *   <li>Cálculo de {@link #STAGE_R2}.</li>
     * </ol>
     *
     * @param problemName nome do problema (ex.: {@code "WFG4"}, {@code "DTLZ2"})
     * @param algorithmName nome do algoritmo (ex.: {@code "NSGA-III"}, {@code "MOEA/DD"}, {@code "RVEA"}, {@code "AGE-MOEA"}, {@code "FDEA"})
     * @param m número de objetivos
     * @param runIndex índice da repetição (\(1 \dots R\))
     * @return lista de 7 {@link PerformanceRecord} (um por etapa/indicador, compartilhando a medição daquela execução do algoritmo)
     */
    public static List<PerformanceRecord> profileSingleConfigurationRun(
            String problemName, String algorithmName, int m, int runIndex) {
        String canonicalProblem = StaticParams.normalizeProblemId(problemName);
        Problem<DoubleSolution> problem = createProblem(canonicalProblem, m);
        int populationSize = StaticParams.getPopulationSize(m);
        int maxGenerations = StaticParams.getMaxGenerations(m, problem.name());
        int maxEvaluations = populationSize * maxGenerations;
        Path refFrontPath = resolveReferenceFrontPath(canonicalProblem, m);

        Algorithm<List<DoubleSolution>> algorithm =
                createStandardAlgorithm(algorithmName, problem);

        return profileCustomAlgorithmRun(
                canonicalProblem,
                algorithmName,
                m,
                populationSize,
                maxEvaluations,
                runIndex,
                algorithm,
                refFrontPath);
    }

    /**
     * Executa e mede separadamente uma instância já construída de {@link Algorithm} e todas as
     * 7 etapas de pós-processamento/indicadores sobre a frente resultante.
     *
     * @param problemName identificador do problema
     * @param algorithmName identificador do algoritmo
     * @param m número de objetivos
     * @param populationSize tamanho da população
     * @param maxEvaluations orçamento máximo de avaliações
     * @param runIndex índice da repetição (\(\ge 1\))
     * @param algorithm instância configurada do algoritmo a executar
     * @param refFrontPath arquivo CSV da frente de referência
     * @return lista imutável de 7 {@link PerformanceRecord}
     */
    public static List<PerformanceRecord> profileCustomAlgorithmRun(
            String problemName,
            String algorithmName,
            int m,
            int populationSize,
            int maxEvaluations,
            int runIndex,
            Algorithm<List<DoubleSolution>> algorithm,
            Path refFrontPath) {
        Objects.requireNonNull(algorithm, "A instância do algoritmo não pode ser nula.");
        Objects.requireNonNull(refFrontPath, "O caminho da frente de referência não pode ser nulo.");

        String timestamp = Instant.now().toString();

        // 1. Execução isolada do algoritmo
        TimedMeasurement<List<DoubleSolution>> algMeasurement =
                measureOperation(
                        () -> {
                            algorithm.run();
                            return algorithm.result();
                        },
                        false);

        double algWallMs = algMeasurement.wallTimeMs();
        double algCpuMs = algMeasurement.cpuTimeMs();
        double[][] nonDominatedFront =
                extractNonDominatedFrontMatrix(algMeasurement.result(), m);
        int solutionCount = nonDominatedFront.length;

        // 2. Etapa FRONT_LOAD: leitura da frente de referência a partir do disco
        TimedMeasurement<double[][]> frontLoadMeasurement =
                measureOperation(
                        () -> {
                            try {
                                return VectorUtils.readVectors(refFrontPath.toString(), ",");
                            } catch (IOException e) {
                                throw new IllegalStateException(
                                        "Falha ao ler frente de referência: " + refFrontPath, e);
                            }
                        },
                        false);

        double[][] referenceFront = frontLoadMeasurement.result();
        int referencePointCount = referenceFront.length;

        // 3. Etapa NORMALIZATION: normalização linear min-max da frente de referência e da frente obtida
        record NormalizedFrontPair(double[][] normalizedRef, double[][] normalizedSolutionFront) {}
        TimedMeasurement<NormalizedFrontPair> normMeasurement =
                measureOperation(
                        () -> {
                            double[][] normRef = NormalizeUtils.normalize(referenceFront);
                            double[] minValues =
                                    NormalizeUtils.getMinValuesOfTheColumnsOfAMatrix(referenceFront);
                            double[] maxValues =
                                    NormalizeUtils.getMaxValuesOfTheColumnsOfAMatrix(referenceFront);
                            double[][] normSol =
                                    (nonDominatedFront.length > 0)
                                            ? NormalizeUtils.normalize(
                                                    nonDominatedFront, minValues, maxValues)
                                            : new double[0][m];
                            return new NormalizedFrontPair(normRef, normSol);
                        },
                        false);

        double[][] normalizedReferenceFront = normMeasurement.result().normalizedRef();
        double[][] normalizedSolutionFront = normMeasurement.result().normalizedSolutionFront();

        // Em m >= 15, o algoritmo recursivo exato de PISAHypervolume apresenta complexidade O(N^{m/2});
        // permite limitar opcionalmente o subconjunto da frente aproximada para HV quando -Dperf.maxHvFrontSize15D > 0,
        // mantendo a frente de referência completa (135 pontos em WFG4.15D.csv, que é avaliada em ~50 ms e tem HV > 0)
        int maxHvFront15D = Integer.getInteger("perf.maxHvFrontSize15D", 0);
        double[][] hvSolutionFront = normalizedSolutionFront;
        if (m >= 15 && maxHvFront15D > 0 && hvSolutionFront.length > maxHvFront15D) {
            hvSolutionFront = Arrays.copyOf(hvSolutionFront, maxHvFront15D);
        }
        final double[][] effectiveHvSolFront = hvSolutionFront;
        final double[][] effectiveHvRefFront = normalizedReferenceFront;
        final long hvTimeoutMs = getHypervolumeTimeoutMs();

        // 4. Etapa PISAHypervolume (protegida pelo limite máximo hvTimeoutMs, padrão 1h = 3.600.000 ms)
        TimedMeasurement<Double> pisaHvMeasurement =
                measureOperationWithTimeout(
                        () -> {
                            PISAHypervolume pisaHv = new PISAHypervolume();
                            pisaHv.referenceFront(effectiveHvRefFront);
                            return pisaHv.compute(effectiveHvSolFront);
                        },
                        hvTimeoutMs,
                        Double.NaN);

        // 5. Etapa NormalizedHypervolume (protegida pelo mesmo limite máximo hvTimeoutMs, pois chama PISAHypervolume internamente)
        TimedMeasurement<Double> nhvMeasurement =
                measureOperationWithTimeout(
                        () -> {
                            NormalizedHypervolume nhv = new NormalizedHypervolume();
                            nhv.referenceFront(effectiveHvRefFront);
                            return nhv.compute(effectiveHvSolFront);
                        },
                        hvTimeoutMs,
                        Double.NaN);

        // 6. Etapa IGD
        TimedMeasurement<Double> igdMeasurement =
                measureOperation(
                        () -> {
                            InvertedGenerationalDistance igd = new InvertedGenerationalDistance();
                            igd.referenceFront(normalizedReferenceFront);
                            return igd.compute(normalizedSolutionFront);
                        },
                        false);

        // 7. Etapa IGD+
        TimedMeasurement<Double> igdPlusMeasurement =
                measureOperation(
                        () -> {
                            InvertedGenerationalDistancePlus igdPlus =
                                    new InvertedGenerationalDistancePlus();
                            igdPlus.referenceFront(normalizedReferenceFront);
                            return igdPlus.compute(normalizedSolutionFront);
                        },
                        false);

        // 8. Etapa R2 (com reset de weightVectors/idealPoint idêntico ao WFGStudy)
        TimedMeasurement<Double> r2Measurement =
                measureOperation(
                        () -> {
                            R2 r2 =
                                    new R2() {
                                        @Override
                                        public void referenceFront(double[][] refFront) {
                                            super.referenceFront(refFront);
                                            setWeightVectors(null);
                                            setIdealPoint(null);
                                        }
                                    };
                            r2.referenceFront(normalizedReferenceFront);
                            return r2.compute(normalizedSolutionFront);
                        },
                        false);

        List<PerformanceRecord> records = new ArrayList<>(7);
        records.add(
                buildRecord(
                        timestamp,
                        problemName,
                        algorithmName,
                        m,
                        populationSize,
                        maxEvaluations,
                        runIndex,
                        algWallMs,
                        algCpuMs,
                        STAGE_FRONT_LOAD,
                        frontLoadMeasurement,
                        solutionCount,
                        referencePointCount));
        records.add(
                buildRecord(
                        timestamp,
                        problemName,
                        algorithmName,
                        m,
                        populationSize,
                        maxEvaluations,
                        runIndex,
                        algWallMs,
                        algCpuMs,
                        STAGE_NORMALIZATION,
                        normMeasurement,
                        solutionCount,
                        referencePointCount));
        records.add(
                buildRecord(
                        timestamp,
                        problemName,
                        algorithmName,
                        m,
                        populationSize,
                        maxEvaluations,
                        runIndex,
                        algWallMs,
                        algCpuMs,
                        STAGE_PISA_HV,
                        pisaHvMeasurement,
                        solutionCount,
                        referencePointCount));
        records.add(
                buildRecord(
                        timestamp,
                        problemName,
                        algorithmName,
                        m,
                        populationSize,
                        maxEvaluations,
                        runIndex,
                        algWallMs,
                        algCpuMs,
                        STAGE_NORMALIZED_HV,
                        nhvMeasurement,
                        solutionCount,
                        referencePointCount));
        records.add(
                buildRecord(
                        timestamp,
                        problemName,
                        algorithmName,
                        m,
                        populationSize,
                        maxEvaluations,
                        runIndex,
                        algWallMs,
                        algCpuMs,
                        STAGE_IGD,
                        igdMeasurement,
                        solutionCount,
                        referencePointCount));
        records.add(
                buildRecord(
                        timestamp,
                        problemName,
                        algorithmName,
                        m,
                        populationSize,
                        maxEvaluations,
                        runIndex,
                        algWallMs,
                        algCpuMs,
                        STAGE_IGD_PLUS,
                        igdPlusMeasurement,
                        solutionCount,
                        referencePointCount));
        records.add(
                buildRecord(
                        timestamp,
                        problemName,
                        algorithmName,
                        m,
                        populationSize,
                        maxEvaluations,
                        runIndex,
                        algWallMs,
                        algCpuMs,
                        STAGE_R2,
                        r2Measurement,
                        solutionCount,
                        referencePointCount));

        return Collections.unmodifiableList(records);
    }

    /**
     * Executa uma bateria de medições para uma configuração problema–algoritmo–\(m\), precedida de
     * execuções de warm-up (descartadas) e seguida de {@code measuredRuns} repetições medidas.
     *
     * @param problemName nome do problema
     * @param algorithmName nome do algoritmo
     * @param m número de objetivos
     * @param warmupRuns número de execuções de aquecimento da JVM (\(\ge 0\))
     * @param measuredRuns número de repetições medidas (\(\ge 1\))
     * @return lista consolidada de {@link PerformanceRecord} das repetições medidas
     */
    public static List<PerformanceRecord> profileConfigurationSeries(
            String problemName,
            String algorithmName,
            int m,
            int warmupRuns,
            int measuredRuns) {
        if (warmupRuns < 0) {
            throw new IllegalArgumentException("warmupRuns deve ser >= 0.");
        }
        if (measuredRuns <= 0) {
            throw new IllegalArgumentException("measuredRuns deve ser >= 1.");
        }
        for (int w = 1; w <= warmupRuns; w++) {
            profileSingleConfigurationRun(problemName, algorithmName, m, w);
        }
        List<PerformanceRecord> allRecords = new ArrayList<>(measuredRuns * 7);
        for (int r = 1; r <= measuredRuns; r++) {
            allRecords.addAll(profileSingleConfigurationRun(problemName, algorithmName, m, r));
        }
        return allRecords;
    }

    /**
     * Compara os métodos de cálculo de hipervolume disponíveis no jMetal 7.8
     * ({@link PISAHypervolume}, {@link WFGHypervolume} e, quando \(m = 3\), {@link Hypervolume3D})
     * sobre exatamente a mesma frente normalizada em \([0, 1]^m\), mesmo número de objetivos \(m\)
     * e mesmo ponto de referência \((1.0, \dots, 1.0) \in \mathbb{R}^m\).
     *
     * @param problem identificador do problema de origem da frente
     * @param normalizedFront frente de entrada normalizada em \([0, 1]^m\) (não vazia)
     * @param m número de objetivos
     * @param warmupRuns número de repetições de warm-up (\(\ge 0\))
     * @param repetitions número de repetições medidas (\(\ge 1\))
     * @return lista de {@link HypervolumeMethodComparison} para cada método avaliado
     */
    public static List<HypervolumeMethodComparison> compareHypervolumeMethods(
            String problem,
            double[][] normalizedFront,
            int m,
            int warmupRuns,
            int repetitions) {
        if (normalizedFront == null || normalizedFront.length == 0) {
            throw new IllegalArgumentException("A frente para comparação de HV não pode ser nula ou vazia.");
        }
        if (repetitions <= 0) {
            throw new IllegalArgumentException("O número de repetições deve ser >= 1.");
        }
        double[] refPoint = StaticParams.getNormalizedHypervolumeReferencePoint(m);
        int frontSize = normalizedFront.length;

        // Warm-up
        for (int w = 0; w < warmupRuns; w++) {
            new PISAHypervolume(refPoint).compute(normalizedFront);
            new WFGHypervolume(refPoint).compute(normalizedFront);
            if (m == 3) {
                new Hypervolume3D(refPoint).compute(normalizedFront);
            }
        }

        // 1. PISAHypervolume (referência exata do pipeline)
        HypervolumeMethodComparison pisaComp =
                benchmarkSingleHvMethod(
                        problem,
                        m,
                        frontSize,
                        "PISAHypervolume",
                        true,
                        () -> new PISAHypervolume(refPoint).compute(normalizedFront),
                        Double.NaN,
                        repetitions);

        double referenceExactHv = pisaComp.hvValue();
        HypervolumeMethodComparison resolvedPisaComp =
                new HypervolumeMethodComparison(
                        pisaComp.problem(),
                        pisaComp.m(),
                        pisaComp.frontSize(),
                        pisaComp.method(),
                        pisaComp.exact(),
                        pisaComp.hvValue(),
                        referenceExactHv,
                        0.0,
                        0.0,
                        pisaComp.wallStats(),
                        pisaComp.cpuStats(),
                        pisaComp.heapPeakMb(),
                        pisaComp.maxSpreadAcrossRuns());

        List<HypervolumeMethodComparison> results = new ArrayList<>();
        results.add(resolvedPisaComp);

        // 2. WFGHypervolume (inclusão-exclusão exata de While et al., 2012)
        results.add(
                benchmarkSingleHvMethod(
                        problem,
                        m,
                        frontSize,
                        "WFGHypervolume",
                        true,
                        () -> new WFGHypervolume(refPoint).compute(normalizedFront),
                        referenceExactHv,
                        repetitions));

        // 3. Hypervolume3D (varredura plana 3D exata, quando m == 3)
        if (m == 3) {
            results.add(
                    benchmarkSingleHvMethod(
                            problem,
                            m,
                            frontSize,
                            "Hypervolume3D",
                            true,
                            () -> new Hypervolume3D(refPoint).compute(normalizedFront),
                            referenceExactHv,
                            repetitions));
        }

        return results;
    }

    private static HypervolumeMethodComparison benchmarkSingleHvMethod(
            String problem,
            int m,
            int frontSize,
            String methodName,
            boolean exact,
            Supplier<Double> hvSupplier,
            double referenceExactHv,
            int repetitions) {
        List<Double> wallTimes = new ArrayList<>(repetitions);
        List<Double> cpuTimes = new ArrayList<>(repetitions);
        List<Double> hvValues = new ArrayList<>(repetitions);
        double maxPeakHeapMb = 0.0;
        long hvTimeoutMs = getHypervolumeTimeoutMs();

        for (int r = 0; r < repetitions; r++) {
            TimedMeasurement<Double> timed =
                    measureOperationWithTimeout(hvSupplier, hvTimeoutMs, Double.NaN);
            wallTimes.add(timed.wallTimeMs());
            cpuTimes.add(timed.cpuTimeMs());
            hvValues.add(timed.result());
            maxPeakHeapMb = Math.max(maxPeakHeapMb, timed.memoryAfter().heapPeakMb());
        }

        DescriptiveStats hvStats = computeStats(hvValues);
        double hvMean = hvStats.mean();
        double maxSpread = hvStats.max() - hvStats.min();
        double effectiveRef = Double.isNaN(referenceExactHv) ? hvMean : referenceExactHv;
        double absError = Math.abs(hvMean - effectiveRef);
        double relError = absError / Math.max(Math.abs(effectiveRef), 1e-15);

        return new HypervolumeMethodComparison(
                problem,
                m,
                frontSize,
                methodName,
                exact,
                hvMean,
                effectiveRef,
                absError,
                relError,
                computeStats(wallTimes),
                computeStats(cpuTimes),
                maxPeakHeapMb,
                maxSpread);
    }

    /**
     * Escreve uma lista de {@link PerformanceRecord} em arquivo CSV UTF-8 com o cabeçalho mínimo
     * exigido em {@code tasks/task9.md}.
     *
     * @param csvPath caminho de destino do arquivo CSV
     * @param records registros a exportar
     * @param append se {@code true} e o arquivo já existir e não estiver vazio, anexa ao final sem repetir o cabeçalho
     * @throws IOException se houver erro de I/O
     */
    public static void writeRecordsToCsv(
            Path csvPath, List<PerformanceRecord> records, boolean append) throws IOException {
        Objects.requireNonNull(csvPath, "O caminho do CSV não pode ser nulo.");
        Objects.requireNonNull(records, "A lista de registros não pode ser nula.");

        Path parent = csvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean fileExistsAndNonEmpty = Files.exists(csvPath) && Files.size(csvPath) > 0;
        List<String> lines = new ArrayList<>(records.size() + 1);
        if (!append || !fileExistsAndNonEmpty) {
            lines.add(CSV_HEADER);
        }
        for (PerformanceRecord record : records) {
            lines.add(record.toCsvLine());
        }

        if (append && fileExistsAndNonEmpty) {
            Files.write(csvPath, lines, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } else {
            Files.write(
                    csvPath,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /**
     * Lê e valida todos os registros de um arquivo CSV de desempenho gerado por
     * {@link #writeRecordsToCsv(Path, List, boolean)}.
     *
     * @param csvPath caminho do arquivo CSV
     * @return lista imutável de {@link PerformanceRecord}
     * @throws IOException se o arquivo não puder ser lido
     */
    public static List<PerformanceRecord> readRecordsFromCsv(Path csvPath) throws IOException {
        if (csvPath == null || !Files.exists(csvPath)) {
            throw new IllegalArgumentException("Arquivo CSV de desempenho inexistente: " + csvPath);
        }
        List<String> rawLines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (rawLines.isEmpty()) {
            throw new IllegalArgumentException("Arquivo CSV de desempenho está vazio: " + csvPath);
        }
        String header = rawLines.get(0).trim();
        if (!header.startsWith(CSV_HEADER)) {
            throw new IllegalArgumentException(
                    "Cabeçalho CSV inválido em "
                            + csvPath
                            + ". Esperado prefixo: "
                            + CSV_HEADER
                            + ", encontrado: "
                            + header);
        }
        List<PerformanceRecord> records = new ArrayList<>(Math.max(0, rawLines.size() - 1));
        for (int i = 1; i < rawLines.size(); i++) {
            String line = rawLines.get(i).trim();
            if (!line.isEmpty()) {
                records.add(PerformanceRecord.fromCsvLine(line));
            }
        }
        return Collections.unmodifiableList(records);
    }

    /**
     * Executa o protocolo experimental padrão de avaliação de desempenho de T09 e salva os
     * resultados em {@code docs/performance/performance-results.csv}:
     * <ul>
     *   <li>Algoritmos: {@code NSGA-III}, {@code MOEA/DD}, {@code RVEA} e {@code AGE-MOEA} (baseline);</li>
     *   <li>Problemas e dimensões: {@code WFG4} (\(m \in \{3, 8, 15\}\)) e {@code DTLZ2} (\(m = 3\));</li>
     *   <li>Warm-up prévio na JVM e {@code runs} repetições por configuração.</li>
     * </ul>
     *
     * @param outputCsvPath caminho do CSV de saída
     * @param warmupRuns número de execuções de warm-up por configuração
     * @param measuredRuns número de repetições medidas por configuração (\(\ge 5\) no protocolo padrão)
     * @return todos os registros gerados
     * @throws IOException se houver falha ao gravar o arquivo CSV
     */
    public static List<PerformanceRecord> runStandardBenchmarkSuite(
            Path outputCsvPath, int warmupRuns, int measuredRuns) throws IOException {
        String algProp = System.getProperty("perf.algorithms", "NSGA-III,MOEA/DD,RVEA,AGE-MOEA");
        String[] algorithms =
                Arrays.stream(algProp.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
        record ProblemDim(String problem, int m) {}
        String cfgProp = System.getProperty("perf.configs", "WFG4:3,WFG4:8,WFG4:15,DTLZ2:3");
        List<ProblemDim> configs = new ArrayList<>();
        for (String item : cfgProp.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                String[] parts = trimmed.split(":");
                configs.add(new ProblemDim(parts[0].trim(), Integer.parseInt(parts[1].trim())));
            }
        }

        List<PerformanceRecord> allRecords = new ArrayList<>();
        boolean appendMode = Boolean.getBoolean("perf.append");
        boolean firstBatch = !appendMode;
        for (ProblemDim cfg : configs) {
            for (String alg : algorithms) {
                long t0 = System.nanoTime();
                System.out.printf(
                        Locale.US,
                        "[PerformanceProfiler] Executando %s | %s | m=%d (warmup=%d, runs=%d)...%n",
                        cfg.problem(),
                        alg,
                        cfg.m(),
                        warmupRuns,
                        measuredRuns);
                List<PerformanceRecord> series =
                        profileConfigurationSeries(
                                cfg.problem(), alg, cfg.m(), warmupRuns, measuredRuns);
                allRecords.addAll(series);
                writeRecordsToCsv(outputCsvPath, series, !firstBatch);
                firstBatch = false;
                PerformanceRecord sample = series.get(0);
                double pHvMs =
                        series.stream()
                                .filter(r -> STAGE_PISA_HV.equals(r.indicator()))
                                .mapToDouble(PerformanceRecord::indicatorWallMs)
                                .average()
                                .orElse(0.0);
                double elapsedSec = (System.nanoTime() - t0) / 1_000_000_000.0;
                System.out.printf(
                        Locale.US,
                        "[PerformanceProfiler] Concluido %s | %s | m=%d em %.2f s (algWall=%.1f ms, PISAHv=%.1f ms, sols=%d)%n",
                        cfg.problem(),
                        alg,
                        cfg.m(),
                        elapsedSec,
                        sample.algorithmWallMs(),
                        pHvMs,
                        sample.solutions());
            }
        }
        return allRecords;
    }

    /**
     * Seleciona um subconjunto representativo uniformemente espaçado de uma frente ordenada
     * (incluindo pontos interiores e extremos), evitando que todos os pontos selecionados
     * pertençam à borda do hiperplano onde \(f_j = 1.0\) (o que anularia o volume \(1.0 - f_j\)).
     *
     * @param front frente normalizada em \([0, 1]^m\)
     * @param targetSize tamanho desejado do subconjunto
     * @return subconjunto de tamanho \(\min(\text{targetSize}, \text{front.length})\) com coordenadas internas estritamente \(< 1.0\)
     */
    public static double[][] selectInteriorRepresentativeSubset(double[][] front, int targetSize) {
        if (front == null || front.length == 0) {
            return new double[0][0];
        }
        int n = Math.min(targetSize, front.length);
        int m = front[0].length;
        double[][] subset = new double[n][m];
        for (int i = 0; i < n; i++) {
            int idx = (n == 1) ? (front.length / 2) : (int) Math.round(i * (front.length - 1.0) / (n - 1.0));
            for (int j = 0; j < m; j++) {
                // Contrai em 1% os pontos exatamente sobre a borda 1.0 para que todos contribuam positivamente ao volume 3D..15D
                subset[i][j] = Math.min(0.99, front[idx][j]);
            }
        }
        return subset;
    }

    /**
     * Executa uma bateria escalonada de comparação entre os métodos de hipervolume disponíveis no
     * jMetal 7.8 ({@link PISAHypervolume}, {@link WFGHypervolume} e {@link Hypervolume3D} para \(m=3\))
     * sobre subconjuntos das frentes de referência normalizadas de {@code WFG4} em
     * \(m \in \{3, 8, 10, 15\}\), sob exatamente a mesma frente, mesma dimensão e mesmo ponto de
     * referência \((1.0, \dots, 1.0) \in \mathbb{R}^m\).
     *
     * @param warmupRuns número de execuções de aquecimento por método
     * @param repetitions número de repetições medidas por método (\(\ge 5\))
     * @return lista consolidada de {@link HypervolumeMethodComparison}
     * @throws IOException se houver falha na leitura das frentes
     */
    public static List<HypervolumeMethodComparison> runHypervolumeScalingComparison(
            int warmupRuns, int repetitions) throws IOException {
        record HvCase(int m, int[] sizes) {}
        List<HvCase> cases =
                List.of(
                        new HvCase(3, new int[] {30, 60, 91}),
                        new HvCase(8, new int[] {10, 20, 30}),
                        new HvCase(10, new int[] {10, 15, 20}),
                        new HvCase(15, new int[] {8, 12, 16}));

        List<HypervolumeMethodComparison> results = new ArrayList<>();
        for (HvCase c : cases) {
            Path refPath = resolveReferenceFrontPath("WFG4", c.m());
            double[][] rawFront = VectorUtils.readVectors(refPath.toString(), ",");
            double[][] normFront = NormalizeUtils.normalize(rawFront);
            for (int size : c.sizes()) {
                double[][] subset = selectInteriorRepresentativeSubset(normFront, size);
                List<HypervolumeMethodComparison> comp =
                        compareHypervolumeMethods("WFG4", subset, c.m(), warmupRuns, repetitions);
                results.addAll(comp);
            }
        }
        return results;
    }

    /**
     * Imprime no console o resumo estatístico completo (média, desvio padrão, mediana, mínimo e máximo,
     * sem remover outliers) de todos os algoritmos e etapas/indicadores presentes em
     * {@code docs/performance/performance-results.csv}.
     *
     * @param csvPath caminho do CSV
     * @throws IOException se houver erro de leitura
     */
    public static void printSummaryFromCsv(Path csvPath) throws IOException {
        List<PerformanceRecord> records = readRecordsFromCsv(csvPath);
        Map<String, List<PerformanceRecord>> byConfig = new LinkedHashMap<>();
        for (PerformanceRecord r : records) {
            String key = r.problem() + "|" + r.algorithm() + "|" + r.m();
            byConfig.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        System.out.println("=== RESUMO ALGORITMOS ===");
        for (Map.Entry<String, List<PerformanceRecord>> entry : byConfig.entrySet()) {
            List<PerformanceRecord> list = entry.getValue();
            PerformanceRecord first = list.get(0);
            List<Double> algWalls = new ArrayList<>();
            List<Double> algCpus = new ArrayList<>();
            List<Double> sols = new ArrayList<>();
            for (PerformanceRecord r : list) {
                if (STAGE_FRONT_LOAD.equals(r.indicator())) {
                    algWalls.add(r.algorithmWallMs());
                    algCpus.add(r.algorithmCpuMs());
                    sols.add((double) r.solutions());
                }
            }
            DescriptiveStats w = computeStats(algWalls);
            DescriptiveStats c = computeStats(algCpus);
            DescriptiveStats s = computeStats(sols);
            System.out.printf(
                    Locale.US,
                    "ALG|%s|%s|m=%d|pop=%d|evals=%d|runs=%d|wall=%.2f±%.2f (med=%.2f, min=%.2f, max=%.2f)|cpu=%.2f±%.2f (med=%.2f, min=%.2f, max=%.2f)|sols=%.1f (min=%.0f, max=%.0f)|ref=%d%n",
                    first.problem(),
                    first.algorithm(),
                    first.m(),
                    first.population(),
                    first.maxEvaluations(),
                    w.count(),
                    w.mean(),
                    w.stdDev(),
                    w.median(),
                    w.min(),
                    w.max(),
                    c.mean(),
                    c.stdDev(),
                    c.median(),
                    c.min(),
                    c.max(),
                    s.mean(),
                    s.min(),
                    s.max(),
                    first.referencePoints());
        }

        System.out.println("=== RESUMO INDICADORES ===");
        Map<String, List<PerformanceRecord>> byConfigAndInd = new LinkedHashMap<>();
        for (PerformanceRecord r : records) {
            String key = r.problem() + "|" + r.algorithm() + "|" + r.m() + "|" + r.indicator();
            byConfigAndInd.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<PerformanceRecord>> entry : byConfigAndInd.entrySet()) {
            List<PerformanceRecord> list = entry.getValue();
            PerformanceRecord first = list.get(0);
            List<Double> indWalls = list.stream().map(PerformanceRecord::indicatorWallMs).toList();
            List<Double> indCpus = list.stream().map(PerformanceRecord::indicatorCpuMs).toList();
            List<Double> peaks = list.stream().map(PerformanceRecord::heapPeakMb).toList();
            DescriptiveStats w = computeStats(indWalls);
            DescriptiveStats c = computeStats(indCpus);
            DescriptiveStats p = computeStats(peaks);
            System.out.printf(
                    Locale.US,
                    "IND|%s|%s|m=%d|%s|wall=%.4f±%.4f (med=%.4f, min=%.4f, max=%.4f)|cpu=%.4f±%.4f|peakHeap=%.2f (max=%.2f)%n",
                    first.problem(),
                    first.algorithm(),
                    first.m(),
                    first.indicator(),
                    w.mean(),
                    w.stdDev(),
                    w.median(),
                    w.min(),
                    w.max(),
                    c.mean(),
                    c.stdDev(),
                    p.mean(),
                    p.max());
        }
    }

    /**
     * Ponto de entrada CLI para execução reprodutível do protocolo de avaliação de desempenho (T09).
     *
     * <p>Aceita propriedades de sistema opcionais:
     * <ul>
     *   <li>{@code -Dperf.warmup=1} (padrão: {@code 1});</li>
     *   <li>{@code -Dperf.runs=5} (padrão: {@code 5});</li>
     *   <li>{@code -Dperf.output=docs/performance/performance-results.csv}.</li>
     * </ul>
     *
     * @param args argumentos de linha de comando
     * @throws IOException se houver erro de escrita do CSV
     */
    public static void main(String[] args) throws IOException {
        int warmup = Integer.getInteger("perf.warmup", 1);
        int runs = Integer.getInteger("perf.runs", 5);
        Path outputPath =
                Path.of(
                        System.getProperty(
                                "perf.output", DEFAULT_RESULTS_CSV_PATH.toString()));

        System.out.printf(
                Locale.US,
                "[PerformanceProfiler] Iniciando benchmark T09 (warmup=%d, runs=%d, output=%s)%n",
                warmup,
                runs,
                outputPath);

        InstrumentationOverheadReport overhead = evaluateInstrumentationOverhead(2000);
        System.out.printf(
                Locale.US,
                "[PerformanceProfiler] Overhead medio da instrumentacao (%d iteracoes): wall=%.3f us/chamada, cpu=%.3f us/chamada%n",
                overhead.iterations(),
                overhead.perCallWallMicros(),
                overhead.perCallCpuMicros());

        MemorySnapshot initialMem = captureMemorySnapshot();
        System.out.printf(
                Locale.US,
                "[PerformanceProfiler] Memoria inicial JVM: heapUsed=%.2f MB, heapCommitted=%.2f MB, heapMax=%.2f MB, nonHeapUsed=%.2f MB%n",
                initialMem.heapUsedMb(),
                initialMem.heapCommittedMb(),
                initialMem.heapMaxMb(),
                initialMem.nonHeapUsedMb());

        if (Boolean.getBoolean("perf.summarizeOnly")) {
            printSummaryFromCsv(outputPath);
            return;
        }

        if (Boolean.getBoolean("perf.compareHvOnly")) {
            List<HypervolumeMethodComparison> hvComps =
                    runHypervolumeScalingComparison(warmup, runs);
            for (HypervolumeMethodComparison hc : hvComps) {
                System.out.printf(
                        Locale.US,
                        "[HV-COMPARE] problem=%s m=%d N=%d method=%s exact=%b hv=%.10f absErr=%.3e relErr=%.3e wallMean=%.4f wallStd=%.4f wallMed=%.4f wallMin=%.4f wallMax=%.4f cpuMean=%.4f cpuStd=%.4f heapPeak=%.2f spread=%.3e%n",
                        hc.problem(),
                        hc.m(),
                        hc.frontSize(),
                        hc.method(),
                        hc.exact(),
                        hc.hvValue(),
                        hc.absError(),
                        hc.relError(),
                        hc.wallStats().mean(),
                        hc.wallStats().stdDev(),
                        hc.wallStats().median(),
                        hc.wallStats().min(),
                        hc.wallStats().max(),
                        hc.cpuStats().mean(),
                        hc.cpuStats().stdDev(),
                        hc.heapPeakMb(),
                        hc.maxSpreadAcrossRuns());
            }
            return;
        }

        List<PerformanceRecord> records = runStandardBenchmarkSuite(outputPath, warmup, runs);
        System.out.printf(
                Locale.US,
                "[PerformanceProfiler] Benchmark concluido com %d registros salvos em %s%n",
                records.size(),
                outputPath);
    }

    private static PerformanceRecord buildRecord(
            String timestamp,
            String problem,
            String algorithm,
            int m,
            int population,
            int maxEvaluations,
            int run,
            double algWallMs,
            double algCpuMs,
            String indicatorName,
            TimedMeasurement<?> indMeasurement,
            int solutions,
            int referencePoints) {
        return new PerformanceRecord(
                timestamp,
                problem,
                algorithm,
                m,
                population,
                maxEvaluations,
                run,
                algWallMs,
                algCpuMs,
                indicatorName,
                indMeasurement.wallTimeMs(),
                indMeasurement.cpuTimeMs(),
                indMeasurement.memoryAfter().heapUsedMb(),
                indMeasurement.memoryAfter().heapPeakMb(),
                solutions,
                referencePoints);
    }

    private static String escapeCsvToken(String token) {
        if (token == null) {
            return "";
        }
        return token.replace(",", ";").trim();
    }
}

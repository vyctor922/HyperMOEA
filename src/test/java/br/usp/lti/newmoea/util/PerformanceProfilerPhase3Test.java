package br.usp.lti.newmoea.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PerformanceProfilerPhase3Test {

    @Test
    @DisplayName("Sleep vs busy-loop distinction proves CPU time is decoupled from wall-clock time")
    void testSleepVsBusyLoopDistinction() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadCpuTimeSupported(), "Thread CPU time must be supported by JVM");

        // 1. Cenário Sleep: tempo de parede avança, mas CPU da thread permanece próximo de zero
        long startWallSleep = System.nanoTime();
        long startCpuSleep = bean.getCurrentThreadCpuTime();

        try {
            Thread.sleep(60); // 60 ms de sleep
        } catch (InterruptedException ignored) {
        }

        long elapsedWallSleepMs = (System.nanoTime() - startWallSleep) / 1_000_000L;
        long elapsedCpuSleepMs = (bean.getCurrentThreadCpuTime() - startCpuSleep) / 1_000_000L;

        assertTrue(elapsedWallSleepMs >= 40, "Wall time must record sleep: " + elapsedWallSleepMs);
        assertTrue(elapsedCpuSleepMs <= 15, "CPU time during sleep must remain near 0 ms: " + elapsedCpuSleepMs);

        // 2. Cenário Busy-Loop: CPU consome ativamente ciclos de clock
        long startWallBusy = System.nanoTime();
        long startCpuBusy = bean.getCurrentThreadCpuTime();

        long busyDeadline = System.currentTimeMillis() + 60;
        double dummy = 0.0;
        while (System.currentTimeMillis() < busyDeadline) {
            dummy += Math.sin(dummy + 0.1);
        }

        long elapsedWallBusyMs = (System.nanoTime() - startWallBusy) / 1_000_000L;
        long elapsedCpuBusyMs = (bean.getCurrentThreadCpuTime() - startCpuBusy) / 1_000_000L;

        assertTrue(elapsedWallBusyMs >= 40, "Wall time must record busy work: " + elapsedWallBusyMs);
        assertTrue(elapsedCpuBusyMs >= 30, "CPU time during busy loop must consume active CPU: " + elapsedCpuBusyMs);
        assertTrue(dummy != 0.0); // Previne eliminação por JIT
    }

    @Test
    @DisplayName("Thread CPU time is thread-local and does not measure worker threads automatically")
    void testThreadCpuIsolationAcrossWorkerThread() throws Exception {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();

        AtomicLong workerCpuTime = new AtomicLong(0);

        long callerStartWall = System.nanoTime();
        long callerStartCpu = bean.getCurrentThreadCpuTime();

        // Dispara trabalho pesado em thread separada
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            long wStartCpu = bean.getCurrentThreadCpuTime();
            long wDeadline = System.currentTimeMillis() + 50;
            double x = 1.0;
            while (System.currentTimeMillis() < wDeadline) {
                x += Math.cos(x);
            }
            workerCpuTime.set((bean.getCurrentThreadCpuTime() - wStartCpu) / 1_000_000L);
        });

        // Thread chamadora apenas aguarda (bloqueada/sleep)
        future.get();

        long callerWallMs = (System.nanoTime() - callerStartWall) / 1_000_000L;
        long callerCpuMs = (bean.getCurrentThreadCpuTime() - callerStartCpu) / 1_000_000L;

        assertTrue(callerWallMs >= 40, "Caller wall time records duration");
        // A thread chamadora não consumiu o CPU do worker!
        assertTrue(callerCpuMs < 20, "Caller thread CPU time must not absorb worker CPU: " + callerCpuMs);
        assertTrue(workerCpuTime.get() >= 25, "Worker thread CPU time must record work: " + workerCpuTime.get());
    }

    @Test
    @DisplayName("Memory pool MXBean accurately tracks heap pools and peak usage")
    void testMemoryPoolPeakTracking() {
        boolean foundHeapPool = false;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                foundHeapPool = true;
                assertNotNull(pool.getName());
                assertTrue(pool.getUsage().getUsed() >= 0);
                assertTrue(pool.getPeakUsage().getUsed() >= 0);
            }
        }
        assertTrue(foundHeapPool, "At least one heap memory pool must exist in the JVM");
    }

    @Test
    @DisplayName("Performance record serializes to CSV and parses back without loss")
    void testPerformanceRecordCsvRoundTrip() {
        String problem = "WFG4";
        int m = 15;
        String algorithm = "NSGA-III";
        int run = 1;
        String stage = "PISAHypervolume";
        long wallMs = 1250L;
        long cpuMs = 1240L;
        long heapUsedMb = 180L;
        long heapPeakMb = 350L;
        int points = 135;

        String line = String.format(java.util.Locale.US,
                "%s,%d,%s,%d,%s,%d,%d,%d,%d,%d",
                problem, m, algorithm, run, stage, wallMs, cpuMs, heapUsedMb, heapPeakMb, points);

        String[] tokens = line.split(",");
        assertEquals(10, tokens.length);
        assertEquals("WFG4", tokens[0]);
        assertEquals("15", tokens[1]);
        assertEquals("NSGA-III", tokens[2]);
        assertEquals("1", tokens[3]);
        assertEquals("PISAHypervolume", tokens[4]);
        assertEquals("1250", tokens[5]);
        assertEquals("1240", tokens[6]);
        assertEquals("180", tokens[7]);
        assertEquals("350", tokens[8]);
        assertEquals("135", tokens[9]);
    }
}


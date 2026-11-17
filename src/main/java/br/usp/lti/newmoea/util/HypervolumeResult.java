package br.usp.lti.newmoea.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Registro de resultado de cálculo de hipervolume, contendo metadados completos
 * de execução, incerteza estatística (para métodos estocásticos como Monte Carlo)
 * e diagnóstico de término / isolamento de processo.
 */
public final class HypervolumeResult implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MethodType {
        EXACT_PISA,
        EXACT_WFG,
        MONTE_CARLO_WILSON,
        UNDEFINED
    }

    public enum ExecutionStatus {
        SUCCESS_EXACT,
        TIMEOUT_FALLBACK_MC,
        CRASH_FALLBACK_MC,
        DIRECT_MC,
        DEGENERATE_BOX,
        EMPTY_FRONT,
        FAILED
    }

    private final double value;
    private final double standardError;
    private final double ciLower;
    private final double ciUpper;
    private final double confidenceLevel;
    private final MethodType method;
    private final ExecutionStatus status;
    private final long executionWallTimeMs;
    private final long executionCpuTimeMs;
    private final long sampleCount;
    private final long hitCount;
    private final long seed;
    private final double[] boundingBoxLower;
    private final double[] boundingBoxUpper;
    private final String diagnosticMessage;

    public HypervolumeResult(
            double value,
            double standardError,
            double ciLower,
            double ciUpper,
            double confidenceLevel,
            MethodType method,
            ExecutionStatus status,
            long executionWallTimeMs,
            long executionCpuTimeMs,
            long sampleCount,
            long hitCount,
            long seed,
            double[] boundingBoxLower,
            double[] boundingBoxUpper,
            String diagnosticMessage) {
        this.value = value;
        this.standardError = standardError;
        this.ciLower = ciLower;
        this.ciUpper = ciUpper;
        this.confidenceLevel = confidenceLevel;
        this.method = Objects.requireNonNull(method, "method cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.executionWallTimeMs = executionWallTimeMs;
        this.executionCpuTimeMs = executionCpuTimeMs;
        this.sampleCount = sampleCount;
        this.hitCount = hitCount;
        this.seed = seed;
        this.boundingBoxLower = boundingBoxLower != null ? boundingBoxLower.clone() : new double[0];
        this.boundingBoxUpper = boundingBoxUpper != null ? boundingBoxUpper.clone() : new double[0];
        this.diagnosticMessage = diagnosticMessage != null ? diagnosticMessage : "";
    }

    public static HypervolumeResult exactSuccess(
            double value,
            MethodType method,
            long wallTimeMs,
            long cpuTimeMs,
            double[] refPoint) {
        return new HypervolumeResult(
                value,
                0.0,
                value,
                value,
                1.0,
                method,
                ExecutionStatus.SUCCESS_EXACT,
                wallTimeMs,
                cpuTimeMs,
                0L,
                0L,
                0L,
                new double[refPoint.length],
                refPoint,
                "Exact calculation completed successfully"
        );
    }

    public static HypervolumeResult emptyFront(double[] refPoint) {
        return new HypervolumeResult(
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                MethodType.EXACT_PISA,
                ExecutionStatus.EMPTY_FRONT,
                0L,
                0L,
                0L,
                0L,
                0L,
                new double[refPoint.length],
                refPoint,
                "Empty front: HV is identically 0.0"
        );
    }

    public static HypervolumeResult degenerateBox(double[] lower, double[] upper) {
        return new HypervolumeResult(
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                MethodType.UNDEFINED,
                ExecutionStatus.DEGENERATE_BOX,
                0L,
                0L,
                0L,
                0L,
                0L,
                lower,
                upper,
                "Degenerate bounding box: volume is 0.0"
        );
    }

    public double getValue() {
        return value;
    }

    public double getStandardError() {
        return standardError;
    }

    public double getCiLower() {
        return ciLower;
    }

    public double getCiUpper() {
        return ciUpper;
    }

    public double getConfidenceLevel() {
        return confidenceLevel;
    }

    public MethodType getMethod() {
        return method;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public long getExecutionWallTimeMs() {
        return executionWallTimeMs;
    }

    public long getExecutionCpuTimeMs() {
        return executionCpuTimeMs;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getSeed() {
        return seed;
    }

    public double[] getBoundingBoxLower() {
        return boundingBoxLower.clone();
    }

    public double[] getBoundingBoxUpper() {
        return boundingBoxUpper.clone();
    }

    public String getDiagnosticMessage() {
        return diagnosticMessage;
    }

    public boolean isExact() {
        return method == MethodType.EXACT_PISA || method == MethodType.EXACT_WFG;
    }

    public static String getCsvHeader() {
        return "value,standard_error,ci_lower,ci_upper,confidence_level,method,status,wall_time_ms,cpu_time_ms,sample_count,hit_count,seed,diagnostic";
    }

    public String toCsvRow() {
        return String.format(java.util.Locale.US,
                "%.12e,%.12e,%.12e,%.12e,%.4f,%s,%s,%d,%d,%d,%d,%d,\"%s\"",
                value, standardError, ciLower, ciUpper, confidenceLevel,
                method.name(), status.name(), executionWallTimeMs, executionCpuTimeMs,
                sampleCount, hitCount, seed, diagnosticMessage.replace("\"", "'"));
    }

    @Override
    public String toString() {
        return "HypervolumeResult{" +
                "value=" + value +
                ", se=" + standardError +
                ", ci=[" + ciLower + ", " + ciUpper + "]" +
                ", method=" + method +
                ", status=" + status +
                ", wallMs=" + executionWallTimeMs +
                ", cpuMs=" + executionCpuTimeMs +
                ", S=" + sampleCount +
                ", X=" + hitCount +
                ", seed=" + seed +
                ", diagnostic='" + diagnosticMessage + '\'' +
                '}';
    }
}


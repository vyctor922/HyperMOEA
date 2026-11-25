package br.usp.lti.newmoea.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance;
import org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.R2;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;
import org.uma.jmetal.solution.AbstractSolution;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.ConstraintHandling;
import org.uma.jmetal.util.VectorUtils;
import org.uma.jmetal.util.bounds.Bounds;
import org.uma.jmetal.util.errorchecking.JMetalException;

/**
 * Contrato formal de auditoria da população de saída ({@code algorithm.result()}),
 * extração canônica não-dominada sincronizada e gravação/verificação alinhada 1:1
 * de {@code FUN{run}.csv} e {@code VAR{run}.csv} (Tarefa T19 / G1-3 / R9 / R16).
 *
 * <p>No jMetal 7.8, {@code ExperimentAlgorithm.runAlgorithm} invoca {@code algorithm.result()}
 * e grava a lista retornada diretamente em {@code FUN{run}.csv} e {@code VAR{run}.csv} via
 * {@code SolutionListOutput}, sem qualquer filtragem de dominância ou desduplicação:
 * <ul>
 *   <li>{@code NSGAIII.result()}, {@code AGEMOEA.result()} e {@code AGEMOEAII.result()} chamam
 *       {@code SolutionListUtils.getNonDominatedSolutions(getPopulation())}, que utiliza
 *       {@code NonDominatedSolutionListArchive} (filtrando dominadas e duplicatas exatas no
 *       espaço de objetivos via {@code EqualSolutionsComparator}).</li>
 *   <li>{@code FDEA.result()} utiliza {@code FastNonDominatedSortRanking.getSubFront(0)},
 *       filtrando soluções dominadas, mas mantendo duplicatas no espaço de objetivos.</li>
 *   <li>{@code AbstractMOEAD.result()} ({@code MOEA/DD} e {@code MOEA/D-DRA}) retorna
 *       {@code this.population} inteira ({@code |R| = N_config}) <b>sem filtrar soluções
 *       dominadas nem duplicadas</b>.</li>
 *   <li>{@code EvolutionaryAlgorithm.result()} ({@code RVEA}) retorna a população selecionada
 *       por {@code RVEAEnvironmentalSelection.selectByApd} <b>sem filtrar soluções mutuamente
 *       dominadas</b> pertencentes a cones/sub-regiões adjacentes.</li>
 * </ul>
 *
 * <p>Esta classe garante:
 * <ol>
 *   <li>Inspeção completa e não-destrutiva da população bruta ({@link PopulationAuditReport});</li>
 *   <li>Extração canônica sincronizada ({@link CanonicalResultExtraction}) preservando a
 *       população bruta original e extraindo apenas soluções finitas, dentro dos limites de caixa,
 *       factíveis, mutuamente não-dominadas e livres de duplicatas no espaço de objetivos, com
 *       desempate determinístico (invariante à permutação da ordem de entrada) e alinhamento
 *       estrito 1:1 entre {@code FUN} e {@code VAR};</li>
 *   <li>Gravação atômica e verificação pós-gravação ({@link FunVarAlignmentReport}) compatível
 *       com {@code VectorUtils.readVectors} do jMetal 7.8, recusando arquivos vazios (0 bytes);</li>
 *   <li>Demonstração analítica e numérica ({@link IndicatorSensitivityReport}) da sensibilidade
 *       do {@code IGD} euclidiano padrão a soluções dominadas vs. invariância de {@code IGD+},
 *       {@code R2}, {@code HV} e {@code NHV}.</li>
 * </ol>
 */
public final class ResultFrontContract {

    /** Tolerância padrão em norma L-infinito no espaço de objetivos para desduplicação canônica. */
    public static final double DEFAULT_DUPLICATE_OBJ_TOLERANCE = 1e-12;

    /** Tolerância zero para comparação exata em ponto flutuante IEEE 754. */
    public static final double EXACT_DUPLICATE_TOLERANCE = 0.0;

    /** Tolerância padrão para verificação de reavaliação VAR -> FUN. */
    public static final double DEFAULT_ALIGNMENT_TOLERANCE = 1e-9;

    /** Tolerância numérica para validação de limites de caixa (bounds). */
    public static final double DEFAULT_BOUNDS_TOLERANCE = 1e-12;

    /** Separador CSV padrão compatível com {@code SolutionListOutput} e {@code VectorUtils.readVectors}. */
    public static final String CSV_SEPARATOR = ",";

    private ResultFrontContract() {
        // Classe utilitária estática
    }

    /**
     * Relatório de auditoria não-destrutiva de uma lista {@code List<DoubleSolution>}
     * retornada por {@code algorithm.result()}.
     */
    public record PopulationAuditReport(
            int rawSize,
            int nullEntryCount,
            int inconsistentDimensionCount,
            int nonFiniteObjectiveCount,
            int nonFiniteVariableCount,
            int outOfBoundsVariableCount,
            int infeasibleConstraintCount,
            int validFeasibleCount,
            int nonDominatedCount,
            int dominatedCount,
            int exactObjectiveDuplicateCount,
            int toleranceObjectiveDuplicateCount,
            int exactVariableDuplicateCount,
            int objectiveTieDistinctVariableCount,
            int uniqueCanonicalCount,
            double duplicateObjTolerance,
            boolean hasDominatedSolutions,
            boolean hasInvalidOrInfeasibleSolutions) {

        public boolean isCleanNonDominatedSet() {
            return rawSize > 0
                    && nullEntryCount == 0
                    && inconsistentDimensionCount == 0
                    && nonFiniteObjectiveCount == 0
                    && nonFiniteVariableCount == 0
                    && outOfBoundsVariableCount == 0
                    && infeasibleConstraintCount == 0
                    && dominatedCount == 0
                    && toleranceObjectiveDuplicateCount == 0;
        }
    }

    /**
     * Resultado imutável da extração canônica sincronizada a partir de {@code rawPopulation}.
     *
     * <p>Mantém tanto a população bruta defensivamente copiada ({@code rawSolutions}) quanto
     * a população canônica ({@code canonicalSolutions}), garantindo alinhamento estrito 1:1
     * linha a linha entre {@code canonicalObjectives} ({@code FUN}) e {@code canonicalVariables}
     * ({@code VAR}).
     */
    public record CanonicalResultExtraction(
            List<DoubleSolution> rawSolutions,
            List<DoubleSolution> canonicalSolutions,
            int[] selectedRawIndices,
            PopulationAuditReport auditReport,
            double[][] rawObjectives,
            double[][] rawVariables,
            double[][] canonicalObjectives,
            double[][] canonicalVariables,
            String rawObjectivesSha256,
            String rawVariablesSha256,
            String canonicalObjectivesSha256,
            String canonicalVariablesSha256) {

        public CanonicalResultExtraction {
            Objects.requireNonNull(rawSolutions, "rawSolutions must not be null");
            Objects.requireNonNull(canonicalSolutions, "canonicalSolutions must not be null");
            Objects.requireNonNull(selectedRawIndices, "selectedRawIndices must not be null");
            Objects.requireNonNull(auditReport, "auditReport must not be null");
            Objects.requireNonNull(rawObjectives, "rawObjectives must not be null");
            Objects.requireNonNull(rawVariables, "rawVariables must not be null");
            Objects.requireNonNull(canonicalObjectives, "canonicalObjectives must not be null");
            Objects.requireNonNull(canonicalVariables, "canonicalVariables must not be null");
            Objects.requireNonNull(rawObjectivesSha256, "rawObjectivesSha256 must not be null");
            Objects.requireNonNull(rawVariablesSha256, "rawVariablesSha256 must not be null");
            Objects.requireNonNull(canonicalObjectivesSha256, "canonicalObjectivesSha256 must not be null");
            Objects.requireNonNull(canonicalVariablesSha256, "canonicalVariablesSha256 must not be null");

            rawSolutions = Collections.unmodifiableList(new ArrayList<>(rawSolutions));
            canonicalSolutions = Collections.unmodifiableList(new ArrayList<>(canonicalSolutions));
            selectedRawIndices = selectedRawIndices.clone();
            rawObjectives = deepCopyMatrix(rawObjectives);
            rawVariables = deepCopyMatrix(rawVariables);
            canonicalObjectives = deepCopyMatrix(canonicalObjectives);
            canonicalVariables = deepCopyMatrix(canonicalVariables);
        }

        @Override
        public int[] selectedRawIndices() {
            return selectedRawIndices.clone();
        }

        @Override
        public double[][] rawObjectives() {
            return deepCopyMatrix(rawObjectives);
        }

        @Override
        public double[][] rawVariables() {
            return deepCopyMatrix(rawVariables);
        }

        @Override
        public double[][] canonicalObjectives() {
            return deepCopyMatrix(canonicalObjectives);
        }

        @Override
        public double[][] canonicalVariables() {
            return deepCopyMatrix(canonicalVariables);
        }
    }

    /**
     * Relatório de gravação de arquivos {@code FUN{run}.csv} e {@code VAR{run}.csv}
     * (e opcionalmente {@code FUN{run}.raw.csv} e {@code VAR{run}.raw.csv}).
     */
    public record WrittenResultFilesReport(
            Path funFile,
            Path varFile,
            Path rawFunFile,
            Path rawVarFile,
            int canonicalRowCount,
            int rawRowCount,
            String funFileSha256,
            String varFileSha256,
            String canonicalObjectivesMatrixSha256,
            String canonicalVariablesMatrixSha256) {}

    /**
     * Relatório de verificação de alinhamento 1:1 entre {@code FUN{run}.csv} e {@code VAR{run}.csv}.
     */
    public record FunVarAlignmentReport(
            Path funFile,
            Path varFile,
            int funRowCount,
            int varRowCount,
            int objectiveDimension,
            int variableDimension,
            boolean rowCountMatches,
            boolean dimensionsMatchProblem,
            boolean allValuesFinite,
            boolean allVariablesWithinBounds,
            boolean reevaluationMatchesFun,
            int misalignedRowCount,
            double maxObjectiveDiscrepancy,
            double tolerance,
            String funMatrixSha256,
            String varMatrixSha256,
            String diagnosticMessage) {

        public boolean isAlignedAndValid() {
            return funRowCount > 0
                    && rowCountMatches
                    && dimensionsMatchProblem
                    && allValuesFinite
                    && allVariablesWithinBounds
                    && reevaluationMatchesFun
                    && misalignedRowCount == 0;
        }
    }

    /**
     * Relatório da demonstração analítica e numérica da sensibilidade de indicadores
     * de qualidade ({@code HV}, {@code NHV}, {@code IGD}, {@code IGD+}, {@code R2})
     * à presença de soluções dominadas na aproximação avaliada.
     */
    public record IndicatorSensitivityReport(
            double[][] referenceFront,
            double[][] nonDominatedFront,
            double[][] rawFrontWithDominated,
            double hvNonDominated,
            double hvWithDominated,
            double nhvNonDominated,
            double nhvWithDominated,
            double igdPlusNonDominated,
            double igdPlusWithDominated,
            double r2NonDominated,
            double r2WithDominated,
            double igdJmetalPow2NonDominated,
            double igdJmetalPow2WithDominated,
            double igdClassicalPow1NonDominated,
            double igdClassicalPow1WithDominated) {

        public double hvDelta() {
            return Math.abs(hvWithDominated - hvNonDominated);
        }

        public double nhvDelta() {
            return Math.abs(nhvWithDominated - nhvNonDominated);
        }

        public double igdPlusDelta() {
            return Math.abs(igdPlusWithDominated - igdPlusNonDominated);
        }

        public double r2Delta() {
            return Math.abs(r2WithDominated - r2NonDominated);
        }

        public double igdJmetalPow2Delta() {
            return Math.abs(igdJmetalPow2WithDominated - igdJmetalPow2NonDominated);
        }

        public double igdClassicalPow1Delta() {
            return Math.abs(igdClassicalPow1WithDominated - igdClassicalPow1NonDominated);
        }
    }

    /**
     * Inspeciona e audita uma população retornada por {@code algorithm.result()} sem modificá-la.
     *
     * @param rawPopulation lista bruta retornada por {@code algorithm.result()}
     * @param duplicateObjTolerance tolerância em norma L-infinito para considerar dois vetores de
     *                              objetivos duplicados (ex.: {@link #DEFAULT_DUPLICATE_OBJ_TOLERANCE})
     * @return relatório detalhado {@link PopulationAuditReport}
     */
    public static PopulationAuditReport auditPopulation(
            List<DoubleSolution> rawPopulation,
            double duplicateObjTolerance) {
        return auditPopulation(rawPopulation, null, duplicateObjTolerance);
    }

    /**
     * Inspeciona e audita uma população retornada por {@code algorithm.result()} validando também
     * os limites de caixa do problema se {@code problem != null}.
     *
     * @param rawPopulation lista bruta retornada por {@code algorithm.result()}
     * @param problem instância opcional do problema para validação de dimensionalidade e bounds
     * @param duplicateObjTolerance tolerância em norma L-infinito para duplicatas no espaço de objetivos
     * @return relatório detalhado {@link PopulationAuditReport}
     */
    public static PopulationAuditReport auditPopulation(
            List<DoubleSolution> rawPopulation,
            Problem<DoubleSolution> problem,
            double duplicateObjTolerance) {
        CanonicalResultExtraction extraction = extractCanonicalResultSetInternal(
                rawPopulation, problem, duplicateObjTolerance, false);
        return extraction.auditReport();
    }

    /**
     * Extrai o conjunto canônico de soluções (finitas, factíveis, mutuamente não-dominadas e
     * sem duplicatas no espaço de objetivos) preservando a população bruta e garantindo
     * alinhamento 1:1 estrito entre objetivos ({@code FUN}) e variáveis de decisão ({@code VAR}).
     *
     * @param rawPopulation população bruta retornada por {@code algorithm.result()}
     * @param duplicateObjTolerance tolerância L-infinito no espaço de objetivos
     * @return {@link CanonicalResultExtraction} com listas, matrizes, índices e hashes SHA-256
     */
    public static CanonicalResultExtraction extractCanonicalResultSet(
            List<DoubleSolution> rawPopulation,
            double duplicateObjTolerance) {
        return extractCanonicalResultSet(rawPopulation, null, duplicateObjTolerance);
    }

    /**
     * Extrai o conjunto canônico de soluções validando também os limites de caixa do problema
     * (quando {@code problem != null}) e ordenando canonicamente as soluções resultantes para
     * garantir invariância total à ordem de entrada.
     *
     * @param rawPopulation população bruta retornada por {@code algorithm.result()}
     * @param problem problema opcional para validação de bounds e dimensões
     * @param duplicateObjTolerance tolerância L-infinito no espaço de objetivos
     * @return {@link CanonicalResultExtraction} com listas, matrizes, índices e hashes SHA-256
     */
    public static CanonicalResultExtraction extractCanonicalResultSet(
            List<DoubleSolution> rawPopulation,
            Problem<DoubleSolution> problem,
            double duplicateObjTolerance) {
        return extractCanonicalResultSetInternal(rawPopulation, problem, duplicateObjTolerance, true);
    }

    /**
     * Extrai o conjunto canônico preservando a ordem relativa de aparição na população bruta
     * (ou ordenando canonicamente quando {@code sortCanonically == true}).
     */
    public static CanonicalResultExtraction extractCanonicalResultSet(
            List<DoubleSolution> rawPopulation,
            Problem<DoubleSolution> problem,
            double duplicateObjTolerance,
            boolean sortCanonically) {
        return extractCanonicalResultSetInternal(
                rawPopulation, problem, duplicateObjTolerance, sortCanonically);
    }

    private record IndexedCandidate(
            int rawIndex,
            DoubleSolution solutionCopy,
            double[] objectives,
            double[] variables) {}

    private static CanonicalResultExtraction extractCanonicalResultSetInternal(
            List<DoubleSolution> rawPopulation,
            Problem<DoubleSolution> problem,
            double duplicateObjTolerance,
            boolean sortCanonically) {
        Objects.requireNonNull(rawPopulation, "rawPopulation must not be null");
        if (!Double.isFinite(duplicateObjTolerance) || duplicateObjTolerance < 0.0) {
            throw new IllegalArgumentException(
                    "duplicateObjTolerance must be finite and >= 0.0, got: " + duplicateObjTolerance);
        }

        int rawSize = rawPopulation.size();
        int expectedObjDim = problem != null ? problem.numberOfObjectives() : -1;
        int expectedVarDim = problem != null ? problem.numberOfVariables() : -1;
        List<Bounds<Double>> variableBounds = (problem instanceof DoubleProblem dp)
                ? dp.variableBounds()
                : null;

        List<DoubleSolution> defensiveRawList = new ArrayList<>(rawSize);
        List<double[]> rawObjRows = new ArrayList<>(rawSize);
        List<double[]> rawVarRows = new ArrayList<>(rawSize);

        int nullEntryCount = 0;
        int inconsistentDimensionCount = 0;
        int nonFiniteObjectiveCount = 0;
        int nonFiniteVariableCount = 0;
        int outOfBoundsVariableCount = 0;
        int infeasibleConstraintCount = 0;

        List<IndexedCandidate> validFeasibleCandidates = new ArrayList<>(rawSize);

        for (int i = 0; i < rawSize; i++) {
            DoubleSolution s = rawPopulation.get(i);
            if (s == null) {
                nullEntryCount++;
                continue;
            }
            DoubleSolution copy = (DoubleSolution) s.copy();
            defensiveRawList.add(copy);

            double[] objs = extractObjectivesArray(copy);
            double[] vars = extractVariablesArray(copy);
            rawObjRows.add(objs);
            rawVarRows.add(vars);

            if (expectedObjDim == -1) {
                expectedObjDim = objs.length;
            }
            if (expectedVarDim == -1) {
                expectedVarDim = vars.length;
            }

            if (objs.length == 0
                    || vars.length == 0
                    || objs.length != expectedObjDim
                    || vars.length != expectedVarDim) {
                inconsistentDimensionCount++;
                continue;
            }

            if (!allFinite(objs)) {
                nonFiniteObjectiveCount++;
                continue;
            }

            if (!allFinite(vars)) {
                nonFiniteVariableCount++;
                continue;
            }

            if (!withinVariableBounds(copy, vars, variableBounds)) {
                outOfBoundsVariableCount++;
                continue;
            }

            if (!isFeasibleSolution(copy)) {
                infeasibleConstraintCount++;
                continue;
            }

            validFeasibleCandidates.add(new IndexedCandidate(i, copy, objs, vars));
        }

        int validFeasibleCount = validFeasibleCandidates.size();

        // Contagem de duplicatas na população factível válida (antes da filtragem de dominância)
        int exactVarDupInValid = countDuplicates(
                validFeasibleCandidates, EXACT_DUPLICATE_TOLERANCE, false);

        // Passo 1: Filtrar soluções estritamente dominadas entre as candidatas factíveis válidas
        List<IndexedCandidate> nonDominatedCandidates = new ArrayList<>(validFeasibleCount);
        int dominatedCount = 0;
        for (int i = 0; i < validFeasibleCount; i++) {
            IndexedCandidate candidateI = validFeasibleCandidates.get(i);
            boolean dominated = false;
            for (int j = 0; j < validFeasibleCount; j++) {
                if (i == j) {
                    continue;
                }
                IndexedCandidate candidateJ = validFeasibleCandidates.get(j);
                if (dominates(candidateJ.objectives(), candidateI.objectives())) {
                    dominated = true;
                    break;
                }
            }
            if (dominated) {
                dominatedCount++;
            } else {
                nonDominatedCandidates.add(candidateI);
            }
        }

        int nonDominatedCount = nonDominatedCandidates.size();
        int exactObjDupCount = countDuplicates(
                nonDominatedCandidates, EXACT_DUPLICATE_TOLERANCE, true);

        // Passo 2: Desduplicação no espaço de objetivos com desempate determinístico
        // Para ser 100% invariante à permutação da entrada quando há empate de objetivos
        // com variáveis de decisão distintas, ordenamos uma cópia de trabalho pela ordem
        // lexicográfica canônica (objetivos -> variáveis -> rawIndex) antes de agrupar clusters.
        List<IndexedCandidate> workingOrder = new ArrayList<>(nonDominatedCandidates);
        workingOrder.sort(ResultFrontContract::compareCandidatesCanonically);

        List<IndexedCandidate> uniqueCanonicalCandidates = new ArrayList<>(nonDominatedCount);
        int toleranceObjDupCount = 0;
        int objectiveTieDistinctVariableCount = 0;

        for (IndexedCandidate candidate : workingOrder) {
            IndexedCandidate matchingRepresentative = null;
            for (IndexedCandidate kept : uniqueCanonicalCandidates) {
                if (linfDistance(kept.objectives(), candidate.objectives()) <= duplicateObjTolerance) {
                    matchingRepresentative = kept;
                    break;
                }
            }
            if (matchingRepresentative == null) {
                uniqueCanonicalCandidates.add(candidate);
            } else {
                toleranceObjDupCount++;
                if (linfDistance(matchingRepresentative.variables(), candidate.variables())
                        > EXACT_DUPLICATE_TOLERANCE) {
                    objectiveTieDistinctVariableCount++;
                }
            }
        }

        if (!sortCanonically) {
            // Preserva a ordem relativa original (pelos índices em rawPopulation) dos representantes
            // canônicos eleitos deterministicamente
            uniqueCanonicalCandidates.sort((a, b) -> Integer.compare(a.rawIndex(), b.rawIndex()));
        }

        int uniqueCanonicalCount = uniqueCanonicalCandidates.size();
        List<DoubleSolution> canonicalSolutions = new ArrayList<>(uniqueCanonicalCount);
        int[] selectedRawIndices = new int[uniqueCanonicalCount];
        double[][] canonicalObjectives = new double[uniqueCanonicalCount][];
        double[][] canonicalVariables = new double[uniqueCanonicalCount][];

        for (int i = 0; i < uniqueCanonicalCount; i++) {
            IndexedCandidate c = uniqueCanonicalCandidates.get(i);
            canonicalSolutions.add((DoubleSolution) c.solutionCopy().copy());
            selectedRawIndices[i] = c.rawIndex();
            canonicalObjectives[i] = c.objectives().clone();
            canonicalVariables[i] = c.variables().clone();
        }

        double[][] rawObjectives = rawObjRows.toArray(new double[0][]);
        double[][] rawVariables = rawVarRows.toArray(new double[0][]);

        PopulationAuditReport report = new PopulationAuditReport(
                rawSize,
                nullEntryCount,
                inconsistentDimensionCount,
                nonFiniteObjectiveCount,
                nonFiniteVariableCount,
                outOfBoundsVariableCount,
                infeasibleConstraintCount,
                validFeasibleCount,
                nonDominatedCount,
                dominatedCount,
                exactObjDupCount,
                toleranceObjDupCount,
                exactVarDupInValid,
                objectiveTieDistinctVariableCount,
                uniqueCanonicalCount,
                duplicateObjTolerance,
                dominatedCount > 0,
                (nullEntryCount
                        + inconsistentDimensionCount
                        + nonFiniteObjectiveCount
                        + nonFiniteVariableCount
                        + outOfBoundsVariableCount
                        + infeasibleConstraintCount) > 0);

        return new CanonicalResultExtraction(
                defensiveRawList,
                canonicalSolutions,
                selectedRawIndices,
                report,
                rawObjectives,
                rawVariables,
                canonicalObjectives,
                canonicalVariables,
                IsolatedJobExecutor.computeMatrixSha256(rawObjectives),
                IsolatedJobExecutor.computeMatrixSha256(rawVariables),
                IsolatedJobExecutor.computeMatrixSha256(canonicalObjectives),
                IsolatedJobExecutor.computeMatrixSha256(canonicalVariables));
    }

    /**
     * Grava os arquivos canônicos {@code FUN{run}.csv} e {@code VAR{run}.csv} (e opcionalmente
     * {@code FUN{run}.raw.csv} e {@code VAR{run}.raw.csv}) garantindo escrita atômica via arquivo
     * temporário, alinhamento 1:1 estrito entre linhas e rejeição de frentes vazias (0 linhas).
     *
     * @param extraction resultado da extração canônica obtido por {@link #extractCanonicalResultSet}
     * @param funFile caminho de destino para {@code FUN{run}.csv}
     * @param varFile caminho de destino para {@code VAR{run}.csv}
     * @param persistRawFiles se {@code true}, grava também {@code FUN{run}.raw.csv} e {@code VAR{run}.raw.csv}
     * @return {@link WrittenResultFilesReport} com caminhos, contagens de linhas e hashes SHA-256
     * @throws IOException em caso de erro de I/O
     * @throws IllegalStateException se a frente canônica estiver vazia ({@code uniqueCanonicalCount == 0})
     */
    public static WrittenResultFilesReport writeCanonicalAndRawFiles(
            CanonicalResultExtraction extraction,
            Path funFile,
            Path varFile,
            boolean persistRawFiles) throws IOException {
        Objects.requireNonNull(extraction, "extraction must not be null");
        Objects.requireNonNull(funFile, "funFile must not be null");
        Objects.requireNonNull(varFile, "varFile must not be null");

        double[][] canonicalObjectives = extraction.canonicalObjectives();
        double[][] canonicalVariables = extraction.canonicalVariables();

        if (canonicalObjectives.length == 0 || canonicalVariables.length == 0) {
            throw new IllegalStateException(
                    "Refusing to write empty canonical front (0 solutions) to "
                            + funFile
                            + " / "
                            + varFile
                            + ". Audit report: "
                            + extraction.auditReport());
        }
        if (canonicalObjectives.length != canonicalVariables.length) {
            throw new IllegalStateException(
                    "Row count mismatch between canonicalObjectives ("
                            + canonicalObjectives.length
                            + ") and canonicalVariables ("
                            + canonicalVariables.length
                            + ")");
        }

        writeMatrixCsvAtomically(canonicalObjectives, funFile);
        writeMatrixCsvAtomically(canonicalVariables, varFile);

        Path rawFunFile = null;
        Path rawVarFile = null;
        double[][] rawObjectives = extraction.rawObjectives();
        double[][] rawVariables = extraction.rawVariables();

        if (persistRawFiles && rawObjectives.length > 0 && rawObjectives.length == rawVariables.length) {
            rawFunFile = deriveRawSiblingPath(funFile);
            rawVarFile = deriveRawSiblingPath(varFile);
            writeMatrixCsvAtomically(rawObjectives, rawFunFile);
            writeMatrixCsvAtomically(rawVariables, rawVarFile);
        }

        return new WrittenResultFilesReport(
                funFile,
                varFile,
                rawFunFile,
                rawVarFile,
                canonicalObjectives.length,
                rawObjectives.length,
                computeFileSha256(funFile),
                computeFileSha256(varFile),
                extraction.canonicalObjectivesSha256(),
                extraction.canonicalVariablesSha256());
    }

    /**
     * Deriva o caminho do arquivo bruto correspondente (ex.: {@code FUN0.csv -> FUN0.raw.csv}).
     */
    public static Path deriveRawSiblingPath(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String rawFileName = (dotIndex > 0)
                ? fileName.substring(0, dotIndex) + ".raw" + fileName.substring(dotIndex)
                : fileName + ".raw.csv";
        Path parent = file.getParent();
        return parent != null ? parent.resolve(rawFileName) : Path.of(rawFileName);
    }

    /**
     * Lê {@code FUN{run}.csv} e {@code VAR{run}.csv} via {@link VectorUtils#readVectors(String, String)}
     * (compatível com jMetal 7.8) e verifica o alinhamento 1:1 estrito entre cada linha {@code i}
     * de {@code VAR} e a linha {@code i} correspondente de {@code FUN}, reavaliando cada vetor de
     * variáveis no {@code problem} fornecido.
     *
     * @param funFile arquivo CSV de objetivos ({@code FUN{run}.csv})
     * @param varFile arquivo CSV de variáveis de decisão ({@code VAR{run}.csv})
     * @param problem problema para validação de bounds e reavaliação {@code problem.evaluate(sol)}
     * @param tolerance tolerância máxima permitida entre o objetivo lido de {@code FUN} e o
     *                  objetivo reavaliado a partir de {@code VAR}
     * @return relatório {@link FunVarAlignmentReport}
     * @throws IOException se houver falha na leitura de disco
     */
    public static FunVarAlignmentReport verifyFunVarAlignment(
            Path funFile,
            Path varFile,
            Problem<DoubleSolution> problem,
            double tolerance) throws IOException {
        Objects.requireNonNull(funFile, "funFile must not be null");
        Objects.requireNonNull(varFile, "varFile must not be null");
        Objects.requireNonNull(problem, "problem must not be null");
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and >= 0.0, got: " + tolerance);
        }

        if (!Files.exists(funFile) || !Files.exists(varFile)) {
            return new FunVarAlignmentReport(
                    funFile,
                    varFile,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0,
                    Double.POSITIVE_INFINITY,
                    tolerance,
                    "",
                    "",
                    "FUN or VAR file does not exist");
        }

        if (Files.size(funFile) == 0L || Files.size(varFile) == 0L) {
            return new FunVarAlignmentReport(
                    funFile,
                    varFile,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0,
                    Double.POSITIVE_INFINITY,
                    tolerance,
                    "",
                    "",
                    "FUN or VAR file is empty (0 bytes)");
        }

        double[][] funMatrix;
        double[][] varMatrix;
        try {
            funMatrix = VectorUtils.readVectors(funFile.toString(), CSV_SEPARATOR);
            varMatrix = VectorUtils.readVectors(varFile.toString(), CSV_SEPARATOR);
        } catch (JMetalException ex) {
            return new FunVarAlignmentReport(
                    funFile,
                    varFile,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0,
                    Double.POSITIVE_INFINITY,
                    tolerance,
                    "",
                    "",
                    "Failed to parse FUN/VAR with VectorUtils.readVectors: " + ex.getMessage());
        }

        int funRows = funMatrix.length;
        int varRows = varMatrix.length;
        int objDim = funRows > 0 ? funMatrix[0].length : 0;
        int varDim = varRows > 0 ? varMatrix[0].length : 0;

        boolean rowCountMatches = (funRows > 0) && (funRows == varRows);
        boolean dimensionsMatchProblem = (objDim == problem.numberOfObjectives())
                && (varDim == problem.numberOfVariables());

        for (double[] row : funMatrix) {
            if (row.length != objDim) {
                dimensionsMatchProblem = false;
                break;
            }
        }
        for (double[] row : varMatrix) {
            if (row.length != varDim) {
                dimensionsMatchProblem = false;
                break;
            }
        }

        boolean allValuesFinite = isMatrixFinite(funMatrix) && isMatrixFinite(varMatrix);
        List<Bounds<Double>> boundsList = (problem instanceof DoubleProblem dp)
                ? dp.variableBounds()
                : null;

        boolean allVariablesWithinBounds = true;
        if (boundsList != null && varDim == boundsList.size()) {
            for (double[] row : varMatrix) {
                for (int j = 0; j < varDim; j++) {
                    Bounds<Double> b = boundsList.get(j);
                    double val = row[j];
                    if (val < b.getLowerBound() - DEFAULT_BOUNDS_TOLERANCE
                            || val > b.getUpperBound() + DEFAULT_BOUNDS_TOLERANCE) {
                        allVariablesWithinBounds = false;
                        break;
                    }
                }
                if (!allVariablesWithinBounds) {
                    break;
                }
            }
        }

        double maxDiscrepancy = 0.0;
        int misalignedRowCount = 0;
        boolean reevaluationMatchesFun = false;

        if (rowCountMatches && dimensionsMatchProblem && allValuesFinite) {
            for (int i = 0; i < funRows; i++) {
                // Evita problem.createSolution() para não consumir nem disputar JMetalRandom fora de lock
                DoubleSolution sol = createUnseededSolution(problem);
                for (int j = 0; j < varDim; j++) {
                    sol.variables().set(j, varMatrix[i][j]);
                }
                problem.evaluate(sol);
                double rowMaxDiff = 0.0;
                for (int m = 0; m < objDim; m++) {
                    double evalVal = sol.objectives()[m];
                    double funVal = funMatrix[i][m];
                    double diff = Math.abs(evalVal - funVal);
                    if (Double.isNaN(diff) || diff > rowMaxDiff) {
                        rowMaxDiff = diff;
                    }
                }
                if (rowMaxDiff > maxDiscrepancy || Double.isNaN(rowMaxDiff)) {
                    maxDiscrepancy = rowMaxDiff;
                }
                if (Double.isNaN(rowMaxDiff) || rowMaxDiff > tolerance) {
                    misalignedRowCount++;
                }
            }
            reevaluationMatchesFun = (misalignedRowCount == 0);
        }

        String funSha = IsolatedJobExecutor.computeMatrixSha256(funMatrix);
        String varSha = IsolatedJobExecutor.computeMatrixSha256(varMatrix);
        String diag = (rowCountMatches
                && dimensionsMatchProblem
                && allValuesFinite
                && allVariablesWithinBounds
                && reevaluationMatchesFun)
                ? "OK: 1:1 FUN/VAR alignment verified"
                : String.format(
                        "Alignment check failed: rowCountMatches=%s, dimensionsMatchProblem=%s, "
                                + "allValuesFinite=%s, allVariablesWithinBounds=%s, "
                                + "reevaluationMatchesFun=%s, misalignedRowCount=%d, maxDiscrepancy=%.6e",
                        rowCountMatches,
                        dimensionsMatchProblem,
                        allValuesFinite,
                        allVariablesWithinBounds,
                        reevaluationMatchesFun,
                        misalignedRowCount,
                        maxDiscrepancy);

        return new FunVarAlignmentReport(
                funFile,
                varFile,
                funRows,
                varRows,
                objDim,
                varDim,
                rowCountMatches,
                dimensionsMatchProblem,
                allValuesFinite,
                allVariablesWithinBounds,
                reevaluationMatchesFun,
                misalignedRowCount,
                maxDiscrepancy,
                tolerance,
                funSha,
                varSha,
                diag);
    }

    /**
     * Avalia o contraexemplo canônico 2D que demonstra matematicamente e numericamente que:
     * <ul>
     *   <li>{@code IGD} euclidiano padrão (não-Pareto-compliant, tanto na variante jMetal 7.8
     *       {@code pow=2.0} quanto na formulação clássica {@code pow=1.0}) <b>muda artificialmente</b>
     *       quando uma solução estritamente dominada permanece em {@code FUN{run}.csv};</li>
     *   <li>{@code IGD+}, {@code R2} (Tchebycheff), {@code HV} e {@code NHV} permanecem
     *       <b>estritamente invariantes</b> ({@code delta == 0.0}) à remoção de soluções dominadas.</li>
     * </ul>
     *
     * <p>Construção do contraexemplo 2D:
     * <ul>
     *   <li>Frente de referência Pareto-ótima {@code R = {(0.0, 1.0), (0.5, 0.5), (1.0, 0.0)}};</li>
     *   <li>Conjunto aproximado não-dominado {@code A_nd = {(0.1, 0.9), (0.9, 0.1)}};</li>
     *   <li>Solução estritamente dominada {@code a_dom = (0.5, 0.95)}, que é estritamente
     *       dominada por {@code a_1 = (0.1, 0.9)} pois {@code 0.1 < 0.5} e {@code 0.9 < 0.95};</li>
     *   <li>Conjunto bruto {@code A_raw = A_nd U {a_dom}}.</li>
     * </ul>
     * Para o ponto de referência central {@code r_2 = (0.5, 0.5)}:
     * <ul>
     *   <li>Distância euclidiana a {@code a_1 = (0.1, 0.9)}:
     *       {@code sqrt((0.1 - 0.5)^2 + (0.9 - 0.5)^2) = sqrt(0.32) ≈ 0.5656854249};</li>
     *   <li>Distância euclidiana a {@code a_2 = (0.9, 0.1)}: {@code sqrt(0.32) ≈ 0.5656854249};</li>
     *   <li>Distância euclidiana ao ponto dominado {@code a_dom = (0.5, 0.95)}:
     *       {@code sqrt((0.5 - 0.5)^2 + (0.95 - 0.5)^2) = 0.45 < 0.5656854249}.</li>
     * </ul>
     * Logo, a inclusão do ponto dominado {@code a_dom} reduz artificialmente o {@code IGD} euclidiano!
     */
    public static IndicatorSensitivityReport evaluateCanonical2DCounterexample() {
        double[][] referenceFront = new double[][] {
            {0.0, 1.0},
            {0.5, 0.5},
            {1.0, 0.0}
        };
        double[][] nonDominatedFront = new double[][] {
            {0.1, 0.9},
            {0.9, 0.1}
        };
        double[][] rawFrontWithDominated = new double[][] {
            {0.1, 0.9},
            {0.9, 0.1},
            {0.5, 0.95} // Estritamente dominado por (0.1, 0.9)
        };
        double[][] weightVectors2D = new double[][] {
            {1.0, 0.0},
            {0.75, 0.25},
            {0.5, 0.5},
            {0.25, 0.75},
            {0.0, 1.0}
        };
        return evaluateDominatedPointSensitivity(
                referenceFront, nonDominatedFront, rawFrontWithDominated, weightVectors2D);
    }

    /**
     * Avalia a sensibilidade dos 5 indicadores de qualidade ({@code HV}, {@code NHV}, {@code IGD},
     * {@code IGD+}, {@code R2}) comparando uma frente não-dominada ({@code nonDominatedFront})
     * contra uma frente bruta contendo soluções dominadas ({@code rawFrontWithDominated}).
     */
    public static IndicatorSensitivityReport evaluateDominatedPointSensitivity(
            double[][] referenceFront,
            double[][] nonDominatedFront,
            double[][] rawFrontWithDominated,
            double[][] weightVectors) {
        Objects.requireNonNull(referenceFront, "referenceFront must not be null");
        Objects.requireNonNull(nonDominatedFront, "nonDominatedFront must not be null");
        Objects.requireNonNull(rawFrontWithDominated, "rawFrontWithDominated must not be null");
        Objects.requireNonNull(weightVectors, "weightVectors must not be null");

        PISAHypervolume hvIndicator = new PISAHypervolume(referenceFront);
        NormalizedHypervolume nhvIndicator = new NormalizedHypervolume(referenceFront);
        InvertedGenerationalDistancePlus igdPlusIndicator =
                new InvertedGenerationalDistancePlus(referenceFront);
        R2 r2Indicator = new R2(referenceFront);
        r2Indicator.setWeightVectors(deepCopyMatrix(weightVectors));
        r2Indicator.setIdealPoint(new double[referenceFront[0].length]);
        InvertedGenerationalDistance igdPow2Indicator =
                new InvertedGenerationalDistance(referenceFront, 2.0);
        InvertedGenerationalDistance igdPow1Indicator =
                new InvertedGenerationalDistance(referenceFront, 1.0);

        double hvNd = hvIndicator.compute(deepCopyMatrix(nonDominatedFront));
        double hvRaw = hvIndicator.compute(deepCopyMatrix(rawFrontWithDominated));

        double nhvNd = nhvIndicator.compute(deepCopyMatrix(nonDominatedFront));
        double nhvRaw = nhvIndicator.compute(deepCopyMatrix(rawFrontWithDominated));

        double igdPlusNd = igdPlusIndicator.compute(deepCopyMatrix(nonDominatedFront));
        double igdPlusRaw = igdPlusIndicator.compute(deepCopyMatrix(rawFrontWithDominated));

        double r2Nd = r2Indicator.compute(deepCopyMatrix(nonDominatedFront));
        double r2Raw = r2Indicator.compute(deepCopyMatrix(rawFrontWithDominated));

        double igdPow2Nd = igdPow2Indicator.compute(deepCopyMatrix(nonDominatedFront));
        double igdPow2Raw = igdPow2Indicator.compute(deepCopyMatrix(rawFrontWithDominated));

        double igdPow1Nd = igdPow1Indicator.compute(deepCopyMatrix(nonDominatedFront));
        double igdPow1Raw = igdPow1Indicator.compute(deepCopyMatrix(rawFrontWithDominated));

        return new IndicatorSensitivityReport(
                deepCopyMatrix(referenceFront),
                deepCopyMatrix(nonDominatedFront),
                deepCopyMatrix(rawFrontWithDominated),
                hvNd,
                hvRaw,
                nhvNd,
                nhvRaw,
                igdPlusNd,
                igdPlusRaw,
                r2Nd,
                r2Raw,
                igdPow2Nd,
                igdPow2Raw,
                igdPow1Nd,
                igdPow1Raw);
    }

    /**
     * Cópia profunda defensiva de uma matriz 2D.
     */
    public static double[][] deepCopyMatrix(double[][] matrix) {
        if (matrix == null) {
            return new double[0][0];
        }
        double[][] copy = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = matrix[i] == null ? new double[0] : matrix[i].clone();
        }
        return copy;
    }

    /**
     * Calcula o hash SHA-256 dos bytes exatos de um arquivo em disco.
     */
    public static String computeFileSha256(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Escreve uma matriz 2D em formato CSV compatível com {@code SolutionListOutput} e
     * {@link VectorUtils#readVectors(String, String)} do jMetal 7.8, usando escrita atômica via
     * arquivo temporário no mesmo diretório.
     */
    public static void writeMatrixCsvAtomically(double[][] matrix, Path targetFile) throws IOException {
        Objects.requireNonNull(matrix, "matrix must not be null");
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        if (matrix.length == 0) {
            throw new IllegalArgumentException("Cannot write an empty matrix (0 rows) to " + targetFile);
        }

        Path parent = targetFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = (parent != null)
                ? Files.createTempFile(parent, targetFile.getFileName().toString() + ".", ".tmp")
                : Files.createTempFile(targetFile.getFileName().toString() + ".", ".tmp");

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                for (int i = 0; i < matrix.length; i++) {
                    double[] row = matrix[i];
                    if (row == null || row.length == 0) {
                        throw new IllegalArgumentException("Row " + i + " is null or empty");
                    }
                    for (int j = 0; j < row.length; j++) {
                        if (!Double.isFinite(row[j])) {
                            throw new IllegalArgumentException(
                                    "Non-finite value at (" + i + ", " + j + "): " + row[j]);
                        }
                        if (j > 0) {
                            writer.write(CSV_SEPARATOR);
                        }
                        writer.write(Double.toString(row[j]));
                    }
                    writer.newLine();
                }
            }
            try {
                Files.move(
                        tempFile,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static double[] extractObjectivesArray(DoubleSolution solution) {
        double[] raw = solution.objectives();
        return raw != null ? raw.clone() : new double[0];
    }

    private static double[] extractVariablesArray(DoubleSolution solution) {
        List<Double> vars = solution.variables();
        if (vars == null) {
            return new double[0];
        }
        double[] arr = new double[vars.size()];
        for (int i = 0; i < vars.size(); i++) {
            Double v = vars.get(i);
            arr[i] = v != null ? v : Double.NaN;
        }
        return arr;
    }

    private static boolean allFinite(double[] values) {
        for (double v : values) {
            if (!Double.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMatrixFinite(double[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            return false;
        }
        for (double[] row : matrix) {
            if (row == null || row.length == 0 || !allFinite(row)) {
                return false;
            }
        }
        return true;
    }

    private static boolean withinVariableBounds(
            DoubleSolution solution,
            double[] vars,
            List<Bounds<Double>> problemBounds) {
        int n = vars.length;
        for (int i = 0; i < n; i++) {
            Bounds<Double> bounds = null;
            if (problemBounds != null && i < problemBounds.size()) {
                bounds = problemBounds.get(i);
            } else {
                try {
                    bounds = solution.getBounds(i);
                } catch (RuntimeException ignored) {
                    bounds = null;
                }
            }
            if (bounds != null) {
                double lower = bounds.getLowerBound();
                double upper = bounds.getUpperBound();
                double val = vars[i];
                if (val < lower - DEFAULT_BOUNDS_TOLERANCE || val > upper + DEFAULT_BOUNDS_TOLERANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isFeasibleSolution(DoubleSolution solution) {
        double[] constraints = solution.constraints();
        if (constraints != null) {
            for (double c : constraints) {
                if (!Double.isFinite(c)) {
                    return false;
                }
                if (c < 0.0) {
                    return false;
                }
            }
        }
        double overallViolation = ConstraintHandling.overallConstraintViolationDegree(solution);
        if (!Double.isFinite(overallViolation) || overallViolation < 0.0) {
            return false;
        }
        return ConstraintHandling.isFeasible(solution);
    }

    /**
     * Retorna {@code true} se e somente se {@code a} domina estritamente {@code b} no sentido de
     * Pareto (minimização): {@code a[m] <= b[m]} para todo {@code m} e {@code a[m] < b[m]} para
     * pelo menos um {@code m}. Delega para o comparador canônico em {@link ParetoFrontValidator#dominates(double[], double[])}.
     */
    public static boolean dominates(double[] a, double[] b) {
        return ParetoFrontValidator.dominates(a, b);
    }

    /**
     * Distância em norma L-infinito {@code max_i |a[i] - b[i]|}.
     */
    public static double linfDistance(double[] a, double[] b) {
        double maxDiff = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = Math.abs(a[i] - b[i]);
            if (diff > maxDiff) {
                maxDiff = diff;
            }
        }
        return maxDiff;
    }

    private static int countDuplicates(
            List<IndexedCandidate> candidates,
            double tolerance,
            boolean compareObjectives) {
        int n = candidates.size();
        boolean[] isDuplicate = new boolean[n];
        int duplicateCount = 0;
        for (int i = 0; i < n; i++) {
            if (isDuplicate[i]) {
                continue;
            }
            double[] vecI = compareObjectives
                    ? candidates.get(i).objectives()
                    : candidates.get(i).variables();
            for (int j = i + 1; j < n; j++) {
                if (isDuplicate[j]) {
                    continue;
                }
                double[] vecJ = compareObjectives
                        ? candidates.get(j).objectives()
                        : candidates.get(j).variables();
                boolean match = (tolerance == 0.0)
                        ? Arrays.equals(vecI, vecJ)
                        : (linfDistance(vecI, vecJ) <= tolerance);
                if (match) {
                    isDuplicate[j] = true;
                    duplicateCount++;
                }
            }
        }
        return duplicateCount;
    }

    private static int compareCandidatesCanonically(IndexedCandidate a, IndexedCandidate b) {
        int objCmp = compareLexicographically(a.objectives(), b.objectives());
        if (objCmp != 0) {
            return objCmp;
        }
        int varCmp = compareLexicographically(a.variables(), b.variables());
        if (varCmp != 0) {
            return varCmp;
        }
        return Integer.compare(a.rawIndex(), b.rawIndex());
    }

    private static int compareLexicographically(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            // Normaliza -0.0 para +0.0 para comparação determinística
            double va = (a[i] == 0.0) ? 0.0 : a[i];
            double vb = (b[i] == 0.0) ? 0.0 : b[i];
            int cmp = Double.compare(va, vb);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    /**
     * Instancia uma {@link DoubleSolution} compatível com {@code problem} sem invocar
     * {@code JMetalRandom}, garantindo segurança estrita em reconstrução/auditoria concorrente.
     */
    public static DoubleSolution createUnseededSolution(Problem<DoubleSolution> problem) {
        Objects.requireNonNull(problem, "problem não pode ser nulo.");
        if (problem instanceof DoubleProblem dp) {
            return new UnseededDoubleSolution(
                    dp.variableBounds(),
                    dp.numberOfObjectives(),
                    dp.numberOfConstraints());
        }
        return problem.createSolution();
    }

    private static final class UnseededDoubleSolution extends AbstractSolution<Double>
            implements DoubleSolution {

        private static final long serialVersionUID = 1L;
        private final List<Bounds<Double>> bounds;

        private UnseededDoubleSolution(
                List<Bounds<Double>> boundsList,
                int numberOfObjectives,
                int numberOfConstraints) {
            super(boundsList.size(), numberOfObjectives, numberOfConstraints);
            this.bounds = boundsList;
        }

        private UnseededDoubleSolution(UnseededDoubleSolution other) {
            super(
                    other.variables().size(),
                    other.objectives().length,
                    other.constraints().length);
            for (int i = 0; i < other.variables().size(); i++) {
                variables().set(i, other.variables().get(i));
            }
            System.arraycopy(other.objectives(), 0, objectives(), 0, other.objectives().length);
            System.arraycopy(other.constraints(), 0, constraints(), 0, other.constraints().length);
            this.bounds = other.bounds;
            this.attributes = new HashMap<>(other.attributes);
        }

        @Override
        public Bounds<Double> getBounds(int index) {
            return bounds.get(index);
        }

        @Override
        public DoubleSolution copy() {
            return new UnseededDoubleSolution(this);
        }
    }
}

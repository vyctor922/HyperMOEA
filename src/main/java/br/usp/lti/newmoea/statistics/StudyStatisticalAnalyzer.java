package br.usp.lti.newmoea.statistics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Analisador e exportador estatístico integrado do estudo experimental da Fase
 * 5 (Tarefa T36):
 * <ul>
 * <li><b>Unidade experimental estrita por célula</b>
 * {@code (problemTag, m, indicator)}: cada
 * observação é uma execução independente ({@code runId}) de um algoritmo;
 * jamais mistura
 * dimensões ou indicadores como pseudo-replicações;</li>
 * <li><b>Estados explícitos de disponibilidade</b>: consome separadamente a
 * série aproximada
 * homogênea ({@code HV_MC}) e as séries exatas ({@code HV}, {@code HV_WFG}) e
 * {@code NHV}
 * e sem jamais preencher {@code NA} do exato com valores de Monte Carlo ou
 * {@code 0.0};</li>
 * <li><b>Orientação explícita da direção de otimização</b>: distingue
 * maximização ({@code HV},
 * {@code HV_WFG}, {@code HV_MC}) de minimização ({@code IGD}, {@code IGD+},
 * {@code R2}) no
 * tamanho de efeito \(\hat{A}_{12}^{\text{better}}\) e na eleição de
 * vencedor;</li>
 * <li><b>Comparação global separada da análise por célula</b>: teste de
 * Friedman por blocos (onde
 * cada bloco é a mediana de uma célula {@code problemTag} completa) com
 * correção exata de
 * empates e estatística \(F_F\) de Iman–Davenport;</li>
 * <li><b>Validação cruzada ao vivo opcional/automática contra R base</b>
 * ({@code scripts/kruskal_dunn_holm.R}), resiliente quando {@code Rscript} não
 * estiver
 * instalado na máquina.</li>
 * </ul>
 */
public final class StudyStatisticalAnalyzer {

    public static final String RSCRIPT_PATH_PROPERTY = "study.rscriptPath";
    public static final String SKIP_R_VALIDATION_PROPERTY = "study.skipRValidation";
    public static final double DEFAULT_ALPHA = 0.05;
    public static final double DEFAULT_R_TOLERANCE_H = 1e-6;
    public static final double DEFAULT_R_TOLERANCE_P = 1e-5;

    private static final Set<String> MAXIMIZATION_INDICATORS = Set.of("HV", "HV_WFG", "HV_MC", "HV_FALLBACK_MC");

    private StudyStatisticalAnalyzer() {
    }

    public enum CellAvailabilityStatus {
        COMPLETE,
        PARTIAL_MISSING_EXACT,
        UNAVAILABLE_ALL_NA,
        INSUFFICIENT_RUNS_FOR_INFERENCE
    }

    public record CellAnalysisRecord(
            String problemTag,
            int m,
            String indicator,
            boolean lowerIsBetter,
            CellAvailabilityStatus availabilityStatus,
            int algorithmCount,
            int totalValidRuns,
            int totalMissingNaRuns,
            KruskalWallisTest.KruskalWallisResult kwResult,
            Map<String, Integer> validCountByAlgorithm,
            Map<String, Integer> missingCountByAlgorithm,
            Map<String, Double> medianByAlgorithm,
            Map<String, Double> iqrByAlgorithm,
            String bestAlgorithmByMedian) {
    }

    public record FriedmanBlockResult(
            String indicator,
            boolean lowerIsBetter,
            int blockCount,
            int algorithmCount,
            double chiSquareFriedman,
            int dfChiSquare,
            double pValueChiSquare,
            double imanDavenportF,
            Map<String, Double> meanRankByAlgorithm,
            String topRankedAlgorithm) {
    }

    public record RValidationReport(
            boolean executed,
            boolean passed,
            String rExecutablePath,
            String rVersionString,
            int validatedCellsCount,
            int validatedPairsCount,
            double maxAbsDiffH,
            double maxAbsDiffPKw,
            double maxAbsDiffZ,
            double maxAbsDiffPHolm,
            double maxAbsDiffA12,
            String statusCode,
            String diagnosticMessage) {
    }

    public record StudyStatisticalReport(
            List<CellAnalysisRecord> cellRecords,
            List<FriedmanBlockResult> friedmanResults,
            RValidationReport rValidationReport,
            Path kruskalCsvPath,
            Path dunnCsvPath,
            Path friedmanCsvPath,
            Path latexSummaryPath,
            Path rValidationStatusPath) {
    }

    public static boolean isLowerBetter(String indicatorName) {
        if (indicatorName == null) {
            return true;
        }
        return !MAXIMIZATION_INDICATORS.contains(indicatorName.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Orienta \(\hat{A}_{12}\) para representar sempre \(P(\text{group1 é
     * estritamente melhor que group2}) + 0.5 P(\text{empate})\).
     */
    public static double orientVarghaDelaneyBetter(double rawGreaterA12, boolean lowerIsBetter) {
        return lowerIsBetter ? (1.0 - rawGreaterA12) : rawGreaterA12;
    }

    /**
     * Executa toda a cadeia estatística sobre
     * {@code <studyOutputDir>/QualityIndicatorSummary.csv},
     * exportando CSVs estruturados, tabelas LaTeX e validação cruzada contra
     * {@code scripts/kruskal_dunn_holm.R}
     * (quando {@code Rscript} estiver acessível, sem falhar caso ausente).
     */
    public static StudyStatisticalReport analyzeAndExportStudy(
            Path studyOutputDir, Path rScriptFile) throws IOException {
        Objects.requireNonNull(studyOutputDir, "studyOutputDir não pode ser nulo.");
        Path summaryCsv = studyOutputDir.resolve("QualityIndicatorSummary.csv");
        Path statsDir = studyOutputDir.resolve("statistics");
        Path latexDir = studyOutputDir.resolve("latex");
        Files.createDirectories(statsDir);
        Files.createDirectories(latexDir);

        Path kwCsvPath = statsDir.resolve("kruskal_cells.csv");
        Path dunnCsvPath = statsDir.resolve("dunn_holm_pairs.csv");
        Path friedmanCsvPath = statsDir.resolve("friedman_global_blocks.csv");
        Path latexSummaryPath = latexDir.resolve("Phase5StatisticalSummary.tex");
        Path rCompCsvPath = statsDir.resolve("r_vs_java_validation.csv");
        Path rStatusTxtPath = statsDir.resolve("r_validation_status.txt");

        if (!Files.isRegularFile(summaryCsv) || Files.size(summaryCsv) == 0L) {
            RValidationReport emptyR = new RValidationReport(
                    false,
                    false,
                    "",
                    "",
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "NO_SUMMARY_DATA",
                    "QualityIndicatorSummary.csv ausente ou vazio.");
            writeStringAtomically(rStatusTxtPath, emptyR.statusCode() + ": " + emptyR.diagnosticMessage() + "\n");
            return new StudyStatisticalReport(
                    List.of(),
                    List.of(),
                    emptyR,
                    kwCsvPath,
                    dunnCsvPath,
                    friedmanCsvPath,
                    latexSummaryPath,
                    rStatusTxtPath);
        }

        // Estrutura: indicator -> problemTag -> algorithm -> Map<runId, rawToken>
        Map<String, Map<String, Map<String, Map<Integer, String>>>> rawData = parseSummaryCsvByCell(summaryCsv);

        List<CellAnalysisRecord> cellRecords = new ArrayList<>();
        for (var indEntry : rawData.entrySet()) {
            String indicator = indEntry.getKey();
            // Mantém HV_FALLBACK_MC separado das séries homogêneas
            if ("HV_FALLBACK_MC".equalsIgnoreCase(indicator)) {
                continue;
            }
            boolean lowerBetter = isLowerBetter(indicator);
            for (var probEntry : indEntry.getValue().entrySet()) {
                String problemTag = probEntry.getKey();
                int m = parseDimensionFromProblemTag(problemTag);
                cellRecords.add(
                        analyzeSingleCell(problemTag, m, indicator, lowerBetter, probEntry.getValue()));
            }
        }

        List<FriedmanBlockResult> friedmanResults = computeFriedmanAcrossCompleteCells(cellRecords);

        exportKruskalCellsCsv(kwCsvPath, cellRecords);
        exportDunnPairsCsv(dunnCsvPath, cellRecords);
        exportFriedmanCsv(friedmanCsvPath, friedmanResults);
        exportPhase5LatexSummary(latexSummaryPath, cellRecords, friedmanResults);

        RValidationReport rReport = runOptionalRCrossValidation(
                summaryCsv, kwCsvPath, dunnCsvPath, rCompCsvPath, rScriptFile, cellRecords);
        writeRValidationStatusFile(rStatusTxtPath, rReport);

        return new StudyStatisticalReport(
                Collections.unmodifiableList(cellRecords),
                Collections.unmodifiableList(friedmanResults),
                rReport,
                kwCsvPath,
                dunnCsvPath,
                friedmanCsvPath,
                latexSummaryPath,
                rStatusTxtPath);
    }

    private static Map<String, Map<String, Map<String, Map<Integer, String>>>> parseSummaryCsvByCell(
            Path summaryCsv) throws IOException {
        Map<String, Map<String, Map<String, Map<Integer, String>>>> map = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(summaryCsv, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Algorithm,")) {
                continue;
            }
            String[] parts = trimmed.split(",", -1);
            if (parts.length < 5) {
                continue;
            }
            String alg = parts[0].trim();
            String prob = parts[1].trim();
            String ind = parts[2].trim();
            int runId = Integer.parseInt(parts[3].trim());
            String valToken = parts[4].trim();

            map.computeIfAbsent(ind, k -> new LinkedHashMap<>())
                    .computeIfAbsent(prob, k -> new LinkedHashMap<>())
                    .computeIfAbsent(alg, k -> new LinkedHashMap<>())
                    .put(runId, valToken);
        }
        return map;
    }

    private static int parseDimensionFromProblemTag(String problemTag) {
        int idx = problemTag.lastIndexOf('_');
        if (idx > 0 && idx + 1 < problemTag.length()) {
            try {
                return Integer.parseInt(problemTag.substring(idx + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static CellAnalysisRecord analyzeSingleCell(
            String problemTag,
            int m,
            String indicator,
            boolean lowerBetter,
            Map<String, Map<Integer, String>> byAlg) {
        Map<String, List<Double>> finiteByAlg = new LinkedHashMap<>();
        Map<String, Integer> validCount = new LinkedHashMap<>();
        Map<String, Integer> missingCount = new LinkedHashMap<>();
        Map<String, Double> medianByAlg = new LinkedHashMap<>();
        Map<String, Double> iqrByAlg = new LinkedHashMap<>();

        int totalValid = 0;
        int totalMissing = 0;
        int minValidPerAlg = Integer.MAX_VALUE;

        for (var entry : byAlg.entrySet()) {
            String alg = entry.getKey();
            List<Double> vals = new ArrayList<>();
            int naCount = 0;
            for (String tok : entry.getValue().values()) {
                if (tok == null || tok.isEmpty() || "NA".equalsIgnoreCase(tok) || "NaN".equalsIgnoreCase(tok)) {
                    naCount++;
                } else {
                    double v = Double.parseDouble(tok);
                    if (Double.isFinite(v)) {
                        vals.add(v);
                    } else {
                        naCount++;
                    }
                }
            }
            validCount.put(alg, vals.size());
            missingCount.put(alg, naCount);
            totalValid += vals.size();
            totalMissing += naCount;
            minValidPerAlg = Math.min(minValidPerAlg, vals.size());

            if (!vals.isEmpty()) {
                finiteByAlg.put(alg, vals);
                List<Double> sorted = new ArrayList<>(vals);
                Collections.sort(sorted);
                medianByAlg.put(alg, quantileLinear(sorted, 0.50));
                iqrByAlg.put(alg, quantileLinear(sorted, 0.75) - quantileLinear(sorted, 0.25));
            } else {
                medianByAlg.put(alg, Double.NaN);
                iqrByAlg.put(alg, Double.NaN);
            }
        }

        String bestAlg = selectBestAlgorithmByMedian(medianByAlg, lowerBetter);

        if (totalValid == 0) {
            return new CellAnalysisRecord(
                    problemTag,
                    m,
                    indicator,
                    lowerBetter,
                    CellAvailabilityStatus.UNAVAILABLE_ALL_NA,
                    byAlg.size(),
                    0,
                    totalMissing,
                    null,
                    validCount,
                    missingCount,
                    medianByAlg,
                    iqrByAlg,
                    "NA");
        }

        if (totalMissing > 0) {
            // Existem NAs na série exata: reporta disponibilidade parcial explicitamente
            // (sem preencher NA com MC e sem misturar desbalanceamento silencioso!)
            KruskalWallisTest.KruskalWallisResult partialKw = (finiteByAlg.size() >= 2
                    && totalValid > finiteByAlg.size())
                            ? KruskalWallisTest.test(finiteByAlg)
                            : null;
            return new CellAnalysisRecord(
                    problemTag,
                    m,
                    indicator,
                    lowerBetter,
                    CellAvailabilityStatus.PARTIAL_MISSING_EXACT,
                    byAlg.size(),
                    totalValid,
                    totalMissing,
                    partialKw,
                    validCount,
                    missingCount,
                    medianByAlg,
                    iqrByAlg,
                    bestAlg);
        }

        if (byAlg.size() < 2 || minValidPerAlg < 2) {
            return new CellAnalysisRecord(
                    problemTag,
                    m,
                    indicator,
                    lowerBetter,
                    CellAvailabilityStatus.INSUFFICIENT_RUNS_FOR_INFERENCE,
                    byAlg.size(),
                    totalValid,
                    0,
                    null,
                    validCount,
                    missingCount,
                    medianByAlg,
                    iqrByAlg,
                    bestAlg);
        }

        KruskalWallisTest.KruskalWallisResult kw = KruskalWallisTest.test(finiteByAlg);
        return new CellAnalysisRecord(
                problemTag,
                m,
                indicator,
                lowerBetter,
                CellAvailabilityStatus.COMPLETE,
                byAlg.size(),
                totalValid,
                0,
                kw,
                validCount,
                missingCount,
                medianByAlg,
                iqrByAlg,
                bestAlg);
    }

    /**
     * Comparação global entre problemas por indicador via Teste de Friedman por
     * blocos (Demsar, 2006;
     * Conover, 1999), onde cada bloco \(b \in \{1, \dots, B\}\) é a mediana da
     * célula
     * {@code problemTag} completa (sem empilhar runs individuais entre problemas
     * distintos).
     */
    public static List<FriedmanBlockResult> computeFriedmanAcrossCompleteCells(
            List<CellAnalysisRecord> cellRecords) {
        Map<String, List<CellAnalysisRecord>> byInd = new LinkedHashMap<>();
        for (CellAnalysisRecord rec : cellRecords) {
            if (rec.availabilityStatus() == CellAvailabilityStatus.COMPLETE
                    || (rec.availabilityStatus() == CellAvailabilityStatus.INSUFFICIENT_RUNS_FOR_INFERENCE
                            && rec.totalMissingNaRuns() == 0
                            && rec.totalValidRuns() == rec.algorithmCount())) {
                byInd.computeIfAbsent(rec.indicator(), k -> new ArrayList<>()).add(rec);
            }
        }

        List<FriedmanBlockResult> results = new ArrayList<>();
        for (var entry : byInd.entrySet()) {
            String ind = entry.getKey();
            List<CellAnalysisRecord> blocks = entry.getValue();
            if (blocks.size() < 2) {
                continue;
            }
            Set<String> commonAlgs = new LinkedHashSet<>(blocks.get(0).medianByAlgorithm().keySet());
            for (int b = 1; b < blocks.size(); b++) {
                commonAlgs.retainAll(blocks.get(b).medianByAlgorithm().keySet());
            }
            if (commonAlgs.size() < 2) {
                continue;
            }
            List<String> algList = new ArrayList<>(commonAlgs);
            int k = algList.size();
            int bCount = blocks.size();
            boolean lowerBetter = blocks.get(0).lowerIsBetter();

            double[] rankSums = new double[k];
            double tieCorrectionTerm = 0.0;

            for (CellAnalysisRecord block : blocks) {
                double[] blockScores = new double[k];
                for (int j = 0; j < k; j++) {
                    double med = block.medianByAlgorithm().get(algList.get(j));
                    // Posto 1 = MELHOR algoritmo no bloco!
                    blockScores[j] = lowerBetter ? med : -med;
                }
                Integer[] order = new Integer[k];
                for (int j = 0; j < k; j++) {
                    order[j] = j;
                }
                java.util.Arrays.sort(order, Comparator.comparingDouble(idx -> blockScores[idx]));
                int p = 0;
                while (p < k) {
                    int q = p + 1;
                    while (q < k && Double.compare(blockScores[order[q]], blockScores[order[p]]) == 0) {
                        q++;
                    }
                    double midRank = 0.5 * ((p + 1.0) + q);
                    for (int idx = p; idx < q; idx++) {
                        rankSums[order[idx]] += midRank;
                    }
                    int t = q - p;
                    if (t > 1) {
                        tieCorrectionTerm += ((double) t * t * t - t);
                    }
                    p = q;
                }
            }

            double sumRjSq = 0.0;
            Map<String, Double> meanRanks = new LinkedHashMap<>();
            String bestAlg = algList.get(0);
            double bestMeanRank = Double.POSITIVE_INFINITY;
            for (int j = 0; j < k; j++) {
                sumRjSq += rankSums[j] * rankSums[j];
                double mr = rankSums[j] / bCount;
                meanRanks.put(algList.get(j), mr);
                if (mr < bestMeanRank) {
                    bestMeanRank = mr;
                    bestAlg = algList.get(j);
                }
            }

            double num = (12.0 / ((double) bCount * k * (k + 1.0))) * sumRjSq
                    - 3.0 * bCount * (k + 1.0);
            double denomTie = 1.0 - (tieCorrectionTerm / ((double) bCount * k * (k * k - 1.0)));
            double chiSq;
            double pVal;
            double imanF;
            if (denomTie <= 1e-15) {
                chiSq = 0.0;
                pVal = 1.0;
                imanF = 0.0;
            } else {
                chiSq = Math.max(0.0, num / denomTie);
                pVal = 1.0 - KruskalWallisTest.chiSquareCdf(chiSq, k - 1);
                double denomIman = bCount * (k - 1.0) - chiSq;
                imanF = (denomIman <= 1e-15)
                        ? Double.POSITIVE_INFINITY
                        : ((bCount - 1.0) * chiSq) / denomIman;
            }

            results.add(
                    new FriedmanBlockResult(
                            ind,
                            lowerBetter,
                            bCount,
                            k,
                            chiSq,
                            k - 1,
                            pVal,
                            imanF,
                            meanRanks,
                            bestAlg));
        }
        return results;
    }

    /**
     * Localiza de forma determinística o executável {@code Rscript} sem exigir que
     * a IDE tenha sido
     * reiniciada após a instalação do R (verifica {@code -Dstudy.rscriptPath},
     * {@code PATH} e
     * instalações padrão em {@code C:\Program Files\R\*\bin\Rscript.exe}).
     */
    public static Path resolveRscriptExecutable() {
        String configured = System.getProperty(RSCRIPT_PATH_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path p = Paths.get(configured.trim());
            return Files.isRegularFile(p) ? p : null;
        }

        // 1. Verifica caminhos explícitos conhecidos no Windows / Linux / macOS
        // (incluindo R-4.6.2)
        Path explicitWin462 = Paths.get("C:\\Program Files\\R\\R-4.6.2\\bin\\Rscript.exe");
        if (Files.isRegularFile(explicitWin462)) {
            return explicitWin462;
        }
        Path programFilesR = Paths.get("C:\\Program Files\\R");
        if (Files.isDirectory(programFilesR)) {
            try (Stream<Path> dirs = Files.list(programFilesR)) {
                List<Path> candidates = dirs.filter(Files::isDirectory)
                        .sorted(Comparator.reverseOrder())
                        .map(d -> d.resolve("bin").resolve("Rscript.exe"))
                        .filter(Files::isRegularFile)
                        .toList();
                if (!candidates.isEmpty()) {
                    return candidates.get(0);
                }
            } catch (IOException ignored) {
            }
        }

        for (String unixCandidate : new String[] { "/usr/bin/Rscript", "/usr/local/bin/Rscript" }) {
            Path p = Paths.get(unixCandidate);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }

        // 2. Tenta resolver via PATH do processo
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            String exeName = isWin ? "Rscript.exe" : "Rscript";
            for (String entry : pathEnv.split(java.io.File.pathSeparator)) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                try {
                    Path cand = Paths.get(entry.trim()).resolve(exeName);
                    if (Files.isRegularFile(cand)) {
                        return cand;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static RValidationReport runOptionalRCrossValidation(
            Path summaryCsv,
            Path kwCsvPath,
            Path dunnCsvPath,
            Path rCompCsvPath,
            Path rScriptFile,
            List<CellAnalysisRecord> cellRecords) {
        if (Boolean.getBoolean(SKIP_R_VALIDATION_PROPERTY)) {
            return new RValidationReport(
                    false,
                    false,
                    "",
                    "",
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "SKIPPED_BY_PROPERTY",
                    "Validação cruzada em R desativada por -Dstudy.skipRValidation=true.");
        }

        long completeCount = cellRecords.stream()
                .filter(c -> c.availabilityStatus() == CellAvailabilityStatus.COMPLETE)
                .count();
        if (completeCount == 0L) {
            return new RValidationReport(
                    false,
                    true,
                    "",
                    "",
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "NO_COMPLETE_CELLS_FOR_INFERENCE",
                    "Nenhuma célula com >=2 runs finitas completas por grupo para inferência KW/Dunn.");
        }

        Path script = (rScriptFile != null) ? rScriptFile : Paths.get("scripts", "kruskal_dunn_holm.R");
        if (!Files.isRegularFile(script)) {
            return new RValidationReport(
                    false,
                    false,
                    "",
                    "",
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "R_SCRIPT_NOT_FOUND",
                    "Script R não encontrado em: " + script);
        }

        Path rExe = resolveRscriptExecutable();
        if (rExe == null) {
            return new RValidationReport(
                    false,
                    false,
                    "",
                    "",
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "R_NOT_AVAILABLE_PENDING",
                    "Executável Rscript não encontrado no PATH nem em diretórios padrão; pós-processamento Java concluído normalmente e validação cruzada em R registrada como pendente de ambiente.");
        }

        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    rExe.toAbsolutePath().toString(),
                    script.toAbsolutePath().toString(),
                    "--summary-csv",
                    summaryCsv.toAbsolutePath().toString(),
                    "--java-kw-csv",
                    kwCsvPath.toAbsolutePath().toString(),
                    "--java-dunn-csv",
                    dunnCsvPath.toAbsolutePath().toString(),
                    "--output-comparison-csv",
                    rCompCsvPath.toAbsolutePath().toString(),
                    "--tol-h",
                    Double.toString(DEFAULT_R_TOLERANCE_H),
                    "--tol-p",
                    Double.toString(DEFAULT_R_TOLERANCE_P));
            pb.redirectErrorStream(true);
            proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            boolean finished = proc.waitFor(60L, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return new RValidationReport(
                        true,
                        false,
                        rExe.toString(),
                        "",
                        0,
                        0,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        Double.NaN,
                        "R_VALIDATION_TIMEOUT",
                        "Execução do Rscript excedeu 60s.");
            }
            int code = proc.exitValue();
            String stdout = out.toString();
            String rVer = extractPrefixedValue(stdout, "R_VERSION=");
            int valCells = parseIntSafe(extractPrefixedValue(stdout, "VALIDATED_CELLS="), 0);
            int valPairs = parseIntSafe(extractPrefixedValue(stdout, "VALIDATED_PAIRS="), 0);
            double maxH = parseDoubleSafe(extractPrefixedValue(stdout, "MAX_DIFF_H="), 0.0);
            double maxPKw = parseDoubleSafe(extractPrefixedValue(stdout, "MAX_DIFF_P_KW="), 0.0);
            double maxZ = parseDoubleSafe(extractPrefixedValue(stdout, "MAX_DIFF_Z="), 0.0);
            double maxPHolm = parseDoubleSafe(extractPrefixedValue(stdout, "MAX_DIFF_P_HOLM="), 0.0);
            double maxA12 = parseDoubleSafe(extractPrefixedValue(stdout, "MAX_DIFF_A12="), 0.0);

            boolean passed = (code == 0) && Files.isRegularFile(rCompCsvPath);
            return new RValidationReport(
                    true,
                    passed,
                    rExe.toString(),
                    rVer,
                    valCells,
                    valPairs,
                    maxH,
                    maxPKw,
                    maxZ,
                    maxPHolm,
                    maxA12,
                    passed ? "R_CROSS_VALIDATION_PASSED" : "R_CROSS_VALIDATION_FAILED",
                    passed
                            ? "Paridade Java vs R Base verificada com sucesso dentro das tolerâncias."
                            : ("Divergência ou erro no Rscript (exit=" + code + "): " + stdout.trim()));
        } catch (Exception e) {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
            return new RValidationReport(
                    false,
                    false,
                    rExe.toString(),
                    "",
                    0,
                    0,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    "R_EXECUTION_ERROR",
                    "Exceção ao invocar Rscript: " + e.getMessage());
        }
    }

    private static String extractPrefixedValue(String text, String prefix) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return (s == null || s.isEmpty()) ? fallback : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDoubleSafe(String s, double fallback) {
        try {
            return (s == null || s.isEmpty()) ? fallback : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String selectBestAlgorithmByMedian(
            Map<String, Double> medianByAlg, boolean lowerBetter) {
        String best = "NA";
        double bestVal = lowerBetter ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (var e : medianByAlg.entrySet()) {
            double v = e.getValue();
            if (!Double.isFinite(v)) {
                continue;
            }
            if ((lowerBetter && v < bestVal) || (!lowerBetter && v > bestVal)) {
                bestVal = v;
                best = e.getKey();
            }
        }
        return best;
    }

    private static double quantileLinear(List<Double> sorted, double p) {
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }
        double pos = p * (n - 1);
        int low = (int) Math.floor(pos);
        int high = (int) Math.ceil(pos);
        double w = pos - low;
        return sorted.get(low) * (1.0 - w) + sorted.get(high) * w;
    }

    private static void exportKruskalCellsCsv(Path path, List<CellAnalysisRecord> records)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "problem,m,indicator,direction,availability_status,k_groups,n_valid_total,n_missing_na_total,"
                        + "h_stat,tie_correction_c,df,p_value,epsilon_squared,significant_005,best_median_algorithm\n");
        for (CellAnalysisRecord r : records) {
            KruskalWallisTest.KruskalWallisResult kw = r.kwResult();
            String dir = r.lowerIsBetter() ? "MINIMIZE" : "MAXIMIZE";
            sb.append(
                    String.format(
                            Locale.US,
                            "%s,%d,%s,%s,%s,%d,%d,%d,%s,%s,%s,%s,%s,%s,%s\n",
                            r.problemTag(),
                            r.m(),
                            r.indicator(),
                            dir,
                            r.availabilityStatus().name(),
                            r.algorithmCount(),
                            r.totalValidRuns(),
                            r.totalMissingNaRuns(),
                            kw != null ? String.format(Locale.US, "%.12e", kw.getH()) : "NA",
                            kw != null ? String.format(Locale.US, "%.12e", kw.getTieCorrection()) : "NA",
                            kw != null ? Integer.toString(kw.getDf()) : "NA",
                            kw != null ? String.format(Locale.US, "%.12e", kw.getPValue()) : "NA",
                            kw != null ? String.format(Locale.US, "%.12e", kw.getEpsilonSquared()) : "NA",
                            kw != null ? Boolean.toString(kw.isSignificant(DEFAULT_ALPHA)) : "NA",
                            r.bestAlgorithmByMedian()));
        }
        writeStringAtomically(path, sb.toString());
    }

    private static void exportDunnPairsCsv(Path path, List<CellAnalysisRecord> records)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "problem,m,indicator,direction,availability_status,group1,group2,n1,n2,mean_rank1,mean_rank2,"
                        + "z_stat,p_raw,p_holm,significant_005,raw_a12_g1_gt_g2,better_a12_g1_over_g2,cliffs_delta_better,winner\n");
        for (CellAnalysisRecord r : records) {
            if (r.kwResult() == null) {
                continue;
            }
            String dir = r.lowerIsBetter() ? "MINIMIZE" : "MAXIMIZE";
            Map<String, KruskalWallisTest.GroupSummary> sums = r.kwResult().getGroupSummaries();
            for (DunnPostHocTest.DunnComparison c : r.kwResult().getComparisons()) {
                KruskalWallisTest.GroupSummary s1 = sums.get(c.getGroup1());
                KruskalWallisTest.GroupSummary s2 = sums.get(c.getGroup2());
                double rawA12 = c.getVarghaDelaneyA12();
                double betterA12 = orientVarghaDelaneyBetter(rawA12, r.lowerIsBetter());
                double cliffsBetter = 2.0 * betterA12 - 1.0;
                boolean sig = c.isSignificant(DEFAULT_ALPHA);
                String winner;
                if (!sig || Math.abs(betterA12 - 0.5) <= 1e-12) {
                    winner = "NS_OR_TIE";
                } else {
                    winner = (betterA12 > 0.5) ? c.getGroup1() : c.getGroup2();
                }
                sb.append(
                        String.format(
                                Locale.US,
                                "%s,%d,%s,%s,%s,%s,%s,%d,%d,%.10f,%.10f,%.12e,%.12e,%.12e,%b,%.12e,%.12e,%.12e,%s\n",
                                r.problemTag(),
                                r.m(),
                                r.indicator(),
                                dir,
                                r.availabilityStatus().name(),
                                c.getGroup1(),
                                c.getGroup2(),
                                s1.getN(),
                                s2.getN(),
                                s1.getMeanRank(),
                                s2.getMeanRank(),
                                c.getZStatistic(),
                                c.getUnadjustedPValue(),
                                c.getAdjustedPValue(),
                                sig,
                                rawA12,
                                betterA12,
                                cliffsBetter,
                                winner));
            }
        }
        writeStringAtomically(path, sb.toString());
    }

    private static void exportFriedmanCsv(Path path, List<FriedmanBlockResult> results)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "indicator,direction,blocks_count,algorithms_count,chi_sq_friedman,df,p_value_chi_sq,iman_davenport_f,top_ranked_algorithm,mean_ranks\n");
        for (FriedmanBlockResult f : results) {
            StringBuilder mrSb = new StringBuilder();
            for (var e : f.meanRankByAlgorithm().entrySet()) {
                if (mrSb.length() > 0) {
                    mrSb.append(';');
                }
                mrSb.append(e.getKey())
                        .append('=')
                        .append(String.format(Locale.US, "%.4f", e.getValue()));
            }
            sb.append(
                    String.format(
                            Locale.US,
                            "%s,%s,%d,%d,%.12e,%d,%.12e,%.12e,%s,\"%s\"\n",
                            f.indicator(),
                            f.lowerIsBetter() ? "MINIMIZE" : "MAXIMIZE",
                            f.blockCount(),
                            f.algorithmCount(),
                            f.chiSquareFriedman(),
                            f.dfChiSquare(),
                            f.pValueChiSquare(),
                            f.imanDavenportF(),
                            f.topRankedAlgorithm(),
                            mrSb));
        }
        writeStringAtomically(path, sb.toString());
    }

    private static void exportPhase5LatexSummary(
            Path latexPath,
            List<CellAnalysisRecord> cellRecords,
            List<FriedmanBlockResult> friedmanResults) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\\documentclass{article}\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        sb.append("\\usepackage{booktabs}\n");
        sb.append("\\begin{document}\n");
        sb.append("\\section*{Phase 5 Statistical Summary (Kruskal--Wallis, Dunn--Holm \\& Friedman Blocks)}\n\n");

        sb.append("\\subsection*{Per-Cell Omnibus Availability \\& Kruskal--Wallis}\n");
        sb.append("\\begin{tabular}{llrrrl}\n\\toprule\n");
        sb.append("Problem & Indicator & Status & $H$ & $p$-value & Best Median \\\\\n\\midrule\n");
        for (CellAnalysisRecord c : cellRecords) {
            String hStr = c.kwResult() != null
                    ? String.format(Locale.US, "%.4f", c.kwResult().getH())
                    : "NA";
            String pStr = c.kwResult() != null
                    ? String.format(Locale.US, "%.3e", c.kwResult().getPValue())
                    : "NA";
            sb.append(escapeLatex(c.problemTag())).append(" & ")
                    .append(escapeLatex(c.indicator())).append(" & ")
                    .append(escapeLatex(c.availabilityStatus().name())).append(" & ")
                    .append(hStr).append(" & ")
                    .append(pStr).append(" & ")
                    .append(escapeLatex(c.bestAlgorithmByMedian())).append(" \\\\\n");
        }
        sb.append("\\bottomrule\n\\end{tabular}\n\n");

        if (!friedmanResults.isEmpty()) {
            sb.append("\\subsection*{Global Cross-Problem Friedman Test on Cell Medians}\n");
            sb.append("\\begin{tabular}{lrrrl}\n\\toprule\n");
            sb.append("Indicator & Blocks & $\\chi^2_F$ & $p$-value & Top Ranked \\\\\n\\midrule\n");
            for (FriedmanBlockResult f : friedmanResults) {
                sb.append(escapeLatex(f.indicator())).append(" & ")
                        .append(f.blockCount()).append(" & ")
                        .append(String.format(Locale.US, "%.4f", f.chiSquareFriedman())).append(" & ")
                        .append(String.format(Locale.US, "%.3e", f.pValueChiSquare())).append(" & ")
                        .append(escapeLatex(f.topRankedAlgorithm())).append(" \\\\\n");
            }
            sb.append("\\bottomrule\n\\end{tabular}\n");
        }
        sb.append("\\end{document}\n");
        writeStringAtomically(latexPath, sb.toString());
    }

    private static String escapeLatex(String s) {
        return s == null ? "" : s.replace("_", "\\_").replace("%", "\\%");
    }

    private static void writeRValidationStatusFile(Path path, RValidationReport rep)
            throws IOException {
        String content = String.format(
                Locale.US,
                "status=%s\nexecuted=%b\npassed=%b\nr_executable=%s\nr_version=%s\n"
                        + "dunn_implementation=BASE_R_ANALYTICAL_EXACT_NO_EXTERNAL_CRAN\n"
                        + "validated_cells=%d\nvalidated_pairs=%d\n"
                        + "max_abs_diff_H=%.12e\nmax_abs_diff_p_kw=%.12e\n"
                        + "max_abs_diff_z=%.12e\nmax_abs_diff_p_holm=%.12e\nmax_abs_diff_a12=%.12e\n"
                        + "message=%s\n",
                rep.statusCode(),
                rep.executed(),
                rep.passed(),
                rep.rExecutablePath(),
                rep.rVersionString(),
                rep.validatedCellsCount(),
                rep.validatedPairsCount(),
                rep.maxAbsDiffH(),
                rep.maxAbsDiffPKw(),
                rep.maxAbsDiffZ(),
                rep.maxAbsDiffPHolm(),
                rep.maxAbsDiffA12(),
                rep.diagnosticMessage());
        writeStringAtomically(path, content);
    }

    private static void writeStringAtomically(Path target, String content) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

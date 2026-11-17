package br.usp.lti.newmoea.util;

import br.usp.lti.newmoea.study.StaticParams;
import br.usp.lti.newmoea.util.ParetoFrontValidator.FrontValidationSummary;
import br.usp.lti.newmoea.util.ParetoFrontValidator.GeneratedFrontReport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolvedor canônico, explícito e auditável de artefatos de referência (vetores de pesos do
 * MOEA/DD e frentes de Pareto de referência para WFG e DTLZ) (T18).
 *
 * <p>Objetivos principais:
 * <ul>
 *   <li>Centralizar a resolução de caminhos sem remover nem modificar diretórios legados
 *       ({@code MOEAD_Weights/} e {@code pareto_fronts/csv/});</li>
 *   <li>Distinguir explicitamente entre diretório físico no sistema de arquivos e recursos de
 *       classpath (uma vez que {@code resources/} na raiz do projeto não faz parte de
 *       {@code src/main/resources} nem dos JARs do jMetal 7.8);</li>
 *   <li>Verificar integridade por hash SHA-256, dimensionalidade (\(m\)) e cardinalidade (\(N\))
 *       no momento da resolução (preflight = consumo);</li>
 *   <li>Documentar explicitamente em {@link ResolvedWeightArtifact#usedInMemoryByMoead()} quando o
 *       {@code AbstractMOEAD.initializeUniformWeight()} do jMetal 7.8 ignora o arquivo {@code .dat}
 *       em disco e gera os vetores em memória (\(m = 2\) e \(N \le 300\));</li>
 *   <li>Eliminar o acoplamento frágil via {@code Path.relativize} ({@code ../../resources/...})
 *       para frentes analíticas DTLZ em \(m > 3\);</li>
 *   <li>Exportar o manifesto consolidado de artefatos ativos em CSV ({@code artifact-manifest.csv}).</li>
 * </ul>
 */
public final class ArtifactResolver {

    /** Diretório legado padrão de vetores de pesos do MOEA/DD ({@code MOEAD_Weights/}). */
    public static final Path DEFAULT_LEGACY_WEIGHTS_DIR = Path.of("MOEAD_Weights");

    /** Diretório canônico dos 29 arquivos oficiais copiados do jMetal 7.8 ({@code resources/weightVectorFiles/}). */
    public static final Path DEFAULT_OFFICIAL_WEIGHTS_DIR = Path.of("resources", "weightVectorFiles");

    /** Diretório canônico para variantes de pesos com substituição controlada de zeros ({@code resources/weightVectorFiles/weights_gen/}). */
    public static final Path DEFAULT_GENERATED_WEIGHTS_DIR =
            Path.of("resources", "weightVectorFiles", "weights_gen");

    /** Diretório padrão de frentes de Pareto oficiais em CSV ({@code pareto_fronts/csv/}). */
    public static final Path DEFAULT_OFFICIAL_FRONTS_DIR = Path.of("pareto_fronts", "csv");

    /** Diretório canônico principal de frentes de Pareto de referência oficiais ({@code resources/referenceFronts/}). */
    public static final Path DEFAULT_CANONICAL_FRONTS_DIR =
            ParetoFrontValidator.DEFAULT_CANONICAL_REFERENCE_FRONTS_DIR;

    /** Diretório canônico para frentes de Pareto analíticas derivadas ({@code resources/referenceFronts/pf_gen/}). */
    public static final Path DEFAULT_ANALYTICAL_FRONTS_DIR =
            ParetoFrontValidator.DEFAULT_ANALYTICAL_PF_GEN_DIR;

    /** Diretório exclusivo para variantes de frentes com substituição de coordenadas zero ({@code resources/referenceFronts/pf_lowzero_gen/}). */
    public static final Path DEFAULT_LOWZERO_FRONTS_DIR =
            ParetoFrontValidator.DEFAULT_LOWZERO_PF_GEN_DIR;

    /** Diretório canônico para frentes de Pareto empíricas derivadas ({@code resources/referenceFronts/pf_gen/empirical/}). */
    public static final Path DEFAULT_EMPIRICAL_FRONTS_DIR =
            ParetoFrontValidator.DEFAULT_EMPIRICAL_PF_GEN_DIR;

    /** Tolerância de soma padrão para validação dos arquivos de pesos do MOEA/DD (\(2 \times 10^{-4}\)). */
    public static final double DEFAULT_WEIGHT_SUM_TOLERANCE = 2e-4;

    /** Tolerância de distância euclidiana para detecção de duplicatas em vetores de pesos (\(10^{-7}\)). */
    public static final double DEFAULT_WEIGHT_DUPLICATE_TOLERANCE = 1e-7;

    /** Dimensões canônicas de objetivos do estudo experimental. */
    public static final int[] ACTIVE_OBJECTIVE_DIMENSIONS = {2, 3, 5, 8, 10, 15};

    /** Problemas ativos da família WFG. */
    public static final String[] WFG_PROBLEMS = {
        "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9"
    };

    /** Problemas ativos da família DTLZ. */
    public static final String[] DTLZ_PROBLEMS = {
        "DTLZ1", "DTLZ2", "DTLZ3", "DTLZ4", "DTLZ5", "DTLZ6", "DTLZ7"
    };

    private final Path baseDir;
    private final Path legacyWeightsDir;
    private final Path officialWeightsDir;
    private final Path generatedWeightsDir;
    private final Path officialFrontsDir;
    private final Path canonicalFrontsDir;
    private final Path analyticalFrontsDir;
    private final Path lowZeroFrontsDir;

    /**
     * Políticas explícitas de resolução de arquivos de vetores de pesos ({@code W{m}D_{N}.dat}).
     */
    public enum WeightSourcePolicy {
        /**
         * Resolve diretamente em {@code MOEAD_Weights/W{m}D_{N}.dat} (padrão legado 100% compatível
         * com {@code WFGStudy}).
         */
        LEGACY_MOEAD_WEIGHTS,

        /**
         * Prefere a variante com substituição controlada de zeros em
         * {@code resources/weightVectorFiles/weights_gen/W{m}D_{N}.dat} quando existente, com
         * fallback verificado para {@code MOEAD_Weights/W{m}D_{N}.dat}.
         */
        CANONICAL_ZERO_REPLACED,

        /**
         * Prefere o arquivo oficial bruto do jMetal 7.8 em
         * {@code resources/weightVectorFiles/W{m}D_{N}.dat} (com zeros exatos quando aplicável),
         * com fallback documentado para {@code MOEAD_Weights/W{m}D_{N}.dat} quando a cardinalidade
         * (\(N \in \{100, 210, 135\}\)) não pertence aos 29 arquivos oficiais distribuídos pelo jMetal.
         */
        OFFICIAL_JMETAL_RAW
    }

    /**
     * Políticas explícitas de resolução de arquivos de frentes de Pareto de referência
     * ({@code {PROBLEM}.{m}D.csv}).
     */
    public enum FrontSourcePolicy {
        /**
         * Resolve exclusivamente em {@code pareto_fronts/csv/{PROBLEM}.{m}D.csv}, lançando exceção
         * clara quando a frente não existe nesse diretório (ex.: {@code DTLZ1..7} para \(m > 3\)).
         */
        OFFICIAL_CSV_ONLY,

        /**
         * Resolve em {@code pareto_fronts/csv/{PROBLEM}.{m}D.csv} quando existe ({@code WFG1..9}
         * em todas as dimensões e {@code DTLZ1..7} em \(m \in \{2, 3\}\)) e, para {@code DTLZ1..7}
         * em \(m \in \{5, 8, 10, 15\}\), resolve em
         * {@code resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv} (lendo do disco se
         * já gerado ou gerando de forma controlada quando autorizado) sem usar {@code relativize} frágil.
         */
        OFFICIAL_THEN_ANALYTICAL,

        /**
         * Prefere frentes analíticas matematicamente geradas em
         * {@code resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv} para toda a família
         * {@code DTLZ1..7} (em todas as dimensões \(m\)) e mantém as frentes oficiais em
         * {@code pareto_fronts/csv/} para a família {@code WFG1..9}.
         */
        ANALYTICAL_PREFERRED_FOR_DTLZ,

        /**
         * Hierarquia canônica em 3 níveis (Item 1):
         * 1. {@code resources/referenceFronts/{PROBLEM}.{m}D.csv};
         * 2. {@code resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv};
         * 3. Geração matemática sob demanda em {@code resources/referenceFronts/pf_gen/} exclusivamente quando ausente.
         */
        CANONICAL_RESOURCE_PRIORITY
    }

    /**
     * Artefato de vetores de pesos resolvido e validado matematicamente.
     *
     * @param path caminho físico normalizado do arquivo {@code .dat} resolvido
     * @param policyUsed política solicitada na resolução
     * @param sourceCategory categoria de procedência efetiva ({@code "LEGACY_MOEAD_WEIGHTS"},
     *     {@code "WEIGHTS_GEN_ZERO_REPLACED"}, {@code "OFFICIAL_JMETAL_RAW"} ou
     *     {@code "FALLBACK_LEGACY_NO_JMETAL_COUNTERPART"})
     * @param m número de objetivos (\(m \ge 2\))
     * @param populationSize tamanho da população / quantidade de vetores (\(N\))
     * @param sha256 hash SHA-256 hexadecimal em minúsculas do arquivo resolvido
     * @param usedInMemoryByMoead {@code true} se {@code AbstractMOEAD.initializeUniformWeight()} do
     *     jMetal 7.8 ignora o arquivo em disco e gera os vetores uniformes em memória (\(m = 2\) e
     *     \(N \le 300\)); {@code false} quando o jMetal lê efetivamente o arquivo {@code .dat} do disco
     * @param validatedWeights matriz {@code double[populationSize][m]} validada
     */
    public record ResolvedWeightArtifact(
            Path path,
            WeightSourcePolicy policyUsed,
            String sourceCategory,
            int m,
            int populationSize,
            String sha256,
            boolean usedInMemoryByMoead,
            double[][] validatedWeights) {

        /**
         * Retorna o diretório pai que deve ser passado a {@code MOEADBuilder.setDataDirectory(String)}
         * para que o {@code AbstractMOEAD} consuma exatamente este arquivo (quando
         * {@code !usedInMemoryByMoead}).
         *
         * @return caminho do diretório pai em formato string
         */
        public String dataDirectoryForMoeadBuilder() {
            Path parent = path.getParent();
            return parent == null ? "." : parent.toString();
        }
    }

    /**
     * Artefato de frente de Pareto de referência resolvido e validado.
     *
     * @param path caminho físico normalizado do arquivo {@code .csv} resolvido
     * @param policyUsed política solicitada na resolução
     * @param sourceCategory categoria de procedência efetiva ({@code "OFFICIAL_PARETO_CSV"} ou
     *     {@code "ANALYTICAL_PF_GEN_DTLZ"})
     * @param problemName identificador canônico do problema (ex.: {@code "WFG1"}, {@code "DTLZ2"})
     * @param m número de objetivos (\(m \ge 2\))
     * @param lineCount quantidade total de linhas válidas (pontos) no arquivo CSV
     * @param uniquePointCount quantidade de pontos distintos segundo a tolerância \(10^{-9}\)
     * @param sha256 hash SHA-256 hexadecimal em minúsculas do arquivo resolvido
     */
    public record ResolvedFrontArtifact(
            Path path,
            FrontSourcePolicy policyUsed,
            String sourceCategory,
            String problemName,
            int m,
            int lineCount,
            int uniquePointCount,
            String sha256) {}

    /**
     * Linha estruturada do manifesto consolidado de artefatos ativos ({@code artifact-manifest.csv}).
     *
     * @param artifactType tipo do artefato ({@code "WEIGHT_VECTOR"} ou {@code "REFERENCE_FRONT"})
     * @param identifier identificador canônico (ex.: {@code "W3D_91"} ou {@code "WFG1_2D"})
     * @param problemOrScope problema ou escopo algorítmico ({@code "MOEADD"} ou {@code "WFG1"}..{@code "DTLZ7"})
     * @param m número de objetivos
     * @param policy política de resolução utilizada
     * @param sourceCategory categoria de origem confirmada
     * @param canonicalPath caminho relativo padronizado (com barras {@code /}) a partir da raiz do projeto
     * @param sizeBytes tamanho do arquivo em bytes
     * @param lineCount quantidade de linhas/vetores válidos
     * @param uniqueCount quantidade de vetores/pontos únicos
     * @param sha256 hash SHA-256 em minúsculas
     * @param runtimeConsumptionMode modo real de consumo em tempo de execução ({@code "IN_MEMORY_GENERATED_BY_JMETAL_MOEAD"}
     *     ou {@code "DISK_FILE_READ"})
     */
    public record ArtifactManifestEntry(
            String artifactType,
            String identifier,
            String problemOrScope,
            int m,
            String policy,
            String sourceCategory,
            String canonicalPath,
            long sizeBytes,
            int lineCount,
            int uniqueCount,
            String sha256,
            String runtimeConsumptionMode) {

        /** Cabeçalho CSV do manifesto de artefatos. */
        public static final String CSV_HEADER =
                "artifact_type,identifier,problem_or_scope,m,policy,source_category,canonical_path,size_bytes,line_count,unique_count,sha256,runtime_consumption_mode";

        /** Formata a entrada como linha CSV compatível com RFC 4180. */
        public String toCsvRow() {
            return String.format(
                    Locale.US,
                    "%s,%s,%s,%d,%s,%s,%s,%d,%d,%d,%s,%s",
                    artifactType,
                    identifier,
                    problemOrScope,
                    m,
                    policy,
                    sourceCategory,
                    canonicalPath,
                    sizeBytes,
                    lineCount,
                    uniqueCount,
                    sha256,
                    runtimeConsumptionMode);
        }
    }

    /**
     * Cria um resolvedor ancorado no diretório de trabalho atual ({@code user.dir}) com a estrutura
     * canônica padrão do projeto.
     */
    public ArtifactResolver() {
        this(Path.of(System.getProperty("user.dir", ".")));
    }

    /**
     * Cria um resolvedor ancorado em um diretório base explícito, permitindo execução determinística
     * mesmo fora da raiz do workspace ou em ambientes de teste isolados ({@code @TempDir}).
     *
     * @param baseDir diretório raiz do projeto onde residem {@code MOEAD_Weights/}, {@code pareto_fronts/csv/} e {@code resources/}
     */
    public ArtifactResolver(Path baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("O diretório base (baseDir) não pode ser nulo.");
        }
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.legacyWeightsDir = this.baseDir.resolve(DEFAULT_LEGACY_WEIGHTS_DIR).normalize();
        this.officialWeightsDir = this.baseDir.resolve(DEFAULT_OFFICIAL_WEIGHTS_DIR).normalize();
        this.generatedWeightsDir = this.baseDir.resolve(DEFAULT_GENERATED_WEIGHTS_DIR).normalize();
        this.officialFrontsDir = this.baseDir.resolve(DEFAULT_OFFICIAL_FRONTS_DIR).normalize();
        this.canonicalFrontsDir = this.baseDir.resolve(DEFAULT_CANONICAL_FRONTS_DIR).normalize();
        this.analyticalFrontsDir = this.baseDir.resolve(DEFAULT_ANALYTICAL_FRONTS_DIR).normalize();
        this.lowZeroFrontsDir = this.baseDir.resolve(DEFAULT_LOWZERO_FRONTS_DIR).normalize();
    }

    /**
     * Cria um resolvedor com diretórios totalmente customizáveis para testes de precedência e colisão.
     *
     * @param baseDir diretório base de referência para relativização de caminhos de manifesto
     * @param legacyWeightsDir diretório legado de pesos ({@code MOEAD_Weights})
     * @param officialWeightsDir diretório de pesos oficiais do jMetal ({@code resources/weightVectorFiles})
     * @param generatedWeightsDir diretório de pesos derivados ({@code resources/weightVectorFiles/weights_gen})
     * @param officialFrontsDir diretório de frentes oficiais ({@code pareto_fronts/csv})
     * @param analyticalFrontsDir diretório de frentes analíticas ({@code resources/referenceFronts/pf_gen})
     */
    public ArtifactResolver(
            Path baseDir,
            Path legacyWeightsDir,
            Path officialWeightsDir,
            Path generatedWeightsDir,
            Path officialFrontsDir,
            Path analyticalFrontsDir) {
        this(
                baseDir,
                legacyWeightsDir,
                officialWeightsDir,
                generatedWeightsDir,
                officialFrontsDir,
                baseDir != null ? baseDir.resolve(DEFAULT_CANONICAL_FRONTS_DIR) : null,
                analyticalFrontsDir,
                baseDir != null ? baseDir.resolve(DEFAULT_LOWZERO_FRONTS_DIR) : null);
    }

    /**
     * Construtor completo com todos os diretórios explícitos.
     */
    public ArtifactResolver(
            Path baseDir,
            Path legacyWeightsDir,
            Path officialWeightsDir,
            Path generatedWeightsDir,
            Path officialFrontsDir,
            Path canonicalFrontsDir,
            Path analyticalFrontsDir,
            Path lowZeroFrontsDir) {
        if (baseDir == null
                || legacyWeightsDir == null
                || officialWeightsDir == null
                || generatedWeightsDir == null
                || officialFrontsDir == null
                || canonicalFrontsDir == null
                || analyticalFrontsDir == null
                || lowZeroFrontsDir == null) {
            throw new IllegalArgumentException(
                    "Nenhum diretório de configuração do ArtifactResolver pode ser nulo.");
        }
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.legacyWeightsDir = legacyWeightsDir.toAbsolutePath().normalize();
        this.officialWeightsDir = officialWeightsDir.toAbsolutePath().normalize();
        this.generatedWeightsDir = generatedWeightsDir.toAbsolutePath().normalize();
        this.officialFrontsDir = officialFrontsDir.toAbsolutePath().normalize();
        this.canonicalFrontsDir = canonicalFrontsDir.toAbsolutePath().normalize();
        this.analyticalFrontsDir = analyticalFrontsDir.toAbsolutePath().normalize();
        this.lowZeroFrontsDir = lowZeroFrontsDir.toAbsolutePath().normalize();
    }

    /** Retorna o diretório base normalizado deste resolvedor. */
    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Informa se o {@code AbstractMOEAD.initializeUniformWeight()} do jMetal 7.8 gera os vetores de
     * pesos uniformes diretamente em memória (ignorando qualquer arquivo {@code .dat} em
     * {@code dataDirectory}) para a combinação informada de objetivos e tamanho populacional.
     *
     * <p>Evidência de bytecode ({@code AbstractMOEAD.class}, offsets 0–72):
     * {@code if ((problem.numberOfObjectives() == 2) && (populationSize <= 300))} gera vetores
     * \((a/(N-1), (N-1-a)/(N-1))\) em memória sem abrir arquivo do disco.
     *
     * @param m número de objetivos
     * @param populationSize tamanho da população
     * @return {@code true} se \(m = 2\) e \(N \le 300\); {@code false} caso contrário
     */
    public static boolean isMoeadWeightGeneratedInMemory(int m, int populationSize) {
        return m == 2 && populationSize <= 300;
    }

    /**
     * Resolve e valida matematicamente o arquivo de vetores de pesos {@code W{m}D_{populationSize}.dat}
     * usando a instância padrão ancorada no diretório do projeto.
     *
     * @param m número de objetivos (\(m \ge 2\))
     * @param populationSize tamanho esperado da população (\(N \ge 1\))
     * @param policy política de resolução de pesos ({@link WeightSourcePolicy})
     * @return {@link ResolvedWeightArtifact} validado com hash SHA-256 e matriz de pesos
     */
    public static ResolvedWeightArtifact resolveWeightVectorFile(
            int m, int populationSize, WeightSourcePolicy policy) {
        return new ArtifactResolver().resolveWeightVector(m, populationSize, policy);
    }

    /**
     * Resolve e valida matematicamente o arquivo de vetores de pesos {@code W{m}D_{populationSize}.dat}
     * segundo a política informada.
     *
     * @param m número de objetivos (\(m \ge 2\))
     * @param populationSize tamanho esperado da população (\(N \ge 1\))
     * @param policy política de resolução de pesos ({@link WeightSourcePolicy})
     * @return {@link ResolvedWeightArtifact} validado com hash SHA-256 e matriz de pesos
     * @throws IllegalArgumentException se os parâmetros forem inválidos ou se o arquivo não existir / falhar na validação
     */
    public ResolvedWeightArtifact resolveWeightVector(
            int m, int populationSize, WeightSourcePolicy policy) {
        if (m < 2) {
            throw new IllegalArgumentException(
                    "O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        if (populationSize < 1) {
            throw new IllegalArgumentException(
                    "O tamanho da população deve ser >= 1, mas foi: " + populationSize);
        }
        if (policy == null) {
            throw new IllegalArgumentException("A política WeightSourcePolicy não pode ser nula.");
        }

        String fileName = String.format("W%dD_%d.dat", m, populationSize);
        Path selectedPath;
        String sourceCategory;

        switch (policy) {
            case LEGACY_MOEAD_WEIGHTS -> {
                selectedPath = legacyWeightsDir.resolve(fileName);
                sourceCategory = "LEGACY_MOEAD_WEIGHTS";
                if (!Files.isRegularFile(selectedPath)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Arquivo de pesos legado não encontrado para m=%d, N=%d em: %s",
                                    m, populationSize, selectedPath));
                }
            }
            case CANONICAL_ZERO_REPLACED -> {
                Path genCandidate = generatedWeightsDir.resolve(fileName);
                Path legacyCandidate = legacyWeightsDir.resolve(fileName);
                if (Files.isRegularFile(genCandidate)) {
                    selectedPath = genCandidate;
                    sourceCategory = "WEIGHTS_GEN_ZERO_REPLACED";
                } else if (Files.isRegularFile(legacyCandidate)) {
                    selectedPath = legacyCandidate;
                    sourceCategory = "LEGACY_MOEAD_WEIGHTS_FALLBACK";
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Arquivo de pesos não encontrado para política CANONICAL_ZERO_REPLACED (m=%d, N=%d). Verificados: '%s' e '%s'.",
                                    m, populationSize, genCandidate, legacyCandidate));
                }
            }
            case OFFICIAL_JMETAL_RAW -> {
                Path officialCandidate = officialWeightsDir.resolve(fileName);
                Path legacyCandidate = legacyWeightsDir.resolve(fileName);
                if (Files.isRegularFile(officialCandidate)) {
                    selectedPath = officialCandidate;
                    sourceCategory = "OFFICIAL_JMETAL_RAW";
                } else if (Files.isRegularFile(legacyCandidate)) {
                    selectedPath = legacyCandidate;
                    sourceCategory = "FALLBACK_LEGACY_NO_JMETAL_COUNTERPART";
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Arquivo de pesos não encontrado para política OFFICIAL_JMETAL_RAW (m=%d, N=%d). Verificados: '%s' e '%s'.",
                                    m, populationSize, officialCandidate, legacyCandidate));
                }
            }
            default -> throw new IllegalArgumentException("Política desconhecida: " + policy);
        }

        Path normalizedPath = selectedPath.toAbsolutePath().normalize();
        double[][] validatedWeights =
                ReferenceSetValidator.validateMoeadWeightFile(
                        normalizedPath,
                        m,
                        populationSize,
                        DEFAULT_WEIGHT_SUM_TOLERANCE,
                        DEFAULT_WEIGHT_DUPLICATE_TOLERANCE);
        String sha256 = ReferenceSetValidator.computeSha256Hex(normalizedPath);
        boolean inMemory = isMoeadWeightGeneratedInMemory(m, populationSize);

        return new ResolvedWeightArtifact(
                normalizedPath,
                policy,
                sourceCategory,
                m,
                populationSize,
                sha256,
                inMemory,
                validatedWeights);
    }

    /**
     * Resolve e valida a frente de Pareto de referência para o problema e dimensão \(m\) usando a
     * instância padrão ancorada no diretório do projeto (permitindo geração sob demanda em
     * {@code resources/referenceFronts/pf_gen/} para frentes analíticas DTLZ quando a
     * política inclui geração analítica).
     *
     * @param problemName nome ou tag do problema (ex.: {@code "WFG1"}, {@code "DTLZ2"}, {@code "WFG4_15"})
     * @param m número de objetivos (\(m \ge 2\))
     * @param policy política de resolução de frentes ({@link FrontSourcePolicy})
     * @return {@link ResolvedFrontArtifact} validado com hash SHA-256, contagem de pontos e pontos únicos
     */
    public static ResolvedFrontArtifact resolveReferenceFront(
            String problemName, int m, FrontSourcePolicy policy) {
        return new ArtifactResolver().resolveFront(problemName, m, policy, true);
    }

    /**
     * Resolve a frente de Pareto de referência seguindo rigorosamente a ordem canônica:
     * 1. {@code resources/referenceFronts/};
     * 2. {@code resources/referenceFronts/pf_gen/};
     * 3. Geração por código em {@code resources/referenceFronts/pf_gen/}.
     *
     * @param problemName nome ou tag do problema
     * @param m número de objetivos
     * @return {@link ResolvedFrontArtifact} validado
     */
    public static ResolvedFrontArtifact resolveReferenceFrontCanonical(String problemName, int m) {
        return resolveReferenceFront(problemName, m, FrontSourcePolicy.CANONICAL_RESOURCE_PRIORITY);
    }

    /**
     * Resolve e valida a frente de Pareto de referência para o problema e dimensão \(m\) segundo a
     * política informada, permitindo gerar frentes analíticas DTLZ sob demanda quando aplicável.
     *
     * @param problemName nome ou tag do problema ({@code WFG1..WFG9} ou {@code DTLZ1..DTLZ7})
     * @param m número de objetivos (\(m \ge 2\))
     * @param policy política de resolução de frentes ({@link FrontSourcePolicy})
     * @return {@link ResolvedFrontArtifact} validado
     */
    public ResolvedFrontArtifact resolveFront(
            String problemName, int m, FrontSourcePolicy policy) {
        return resolveFront(problemName, m, policy, true);
    }

    /**
     * Resolve e valida a frente de Pareto de referência para o problema e dimensão \(m\) segundo a
     * política informada.
     *
     * @param problemName nome ou tag do problema ({@code WFG1..WFG9} ou {@code DTLZ1..DTLZ7})
     * @param m número de objetivos (\(m \ge 2\))
     * @param policy política de resolução de frentes ({@link FrontSourcePolicy})
     * @param allowOnDemandAnalyticalGeneration se {@code true}, gera e persiste a frente analítica
     *     DTLZ em {@code analyticalFrontsDir} caso ainda não exista em disco e a política permita
     *     frente analítica; se {@code false}, exige que o arquivo já exista em disco
     * @return {@link ResolvedFrontArtifact} validado
     * @throws IllegalArgumentException se o problema/dimensão for inválido ou se o arquivo não puder ser resolvido pela política
     */
    public ResolvedFrontArtifact resolveFront(
            String problemName,
            int m,
            FrontSourcePolicy policy,
            boolean allowOnDemandAnalyticalGeneration) {
        if (m < 2) {
            throw new IllegalArgumentException(
                    "O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        if (policy == null) {
            throw new IllegalArgumentException("A política FrontSourcePolicy não pode ser nula.");
        }
        String canonicalProblem = StaticParams.normalizeProblemId(problemName);
        boolean isDtlz = canonicalProblem.startsWith("DTLZ");
        String fileName = String.format("%s.%dD.csv", canonicalProblem, m);

        Path officialCandidate = officialFrontsDir.resolve(fileName);
        Path analyticalCandidate = analyticalFrontsDir.resolve(fileName);

        Path selectedPath;
        String sourceCategory;

        switch (policy) {
            case OFFICIAL_CSV_ONLY -> {
                if (!Files.isRegularFile(officialCandidate)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Frente de Pareto oficial ausente em pareto_fronts/csv para problema=%s, m=%d (política OFFICIAL_CSV_ONLY): %s",
                                    canonicalProblem, m, officialCandidate));
                }
                selectedPath = officialCandidate;
                sourceCategory = "OFFICIAL_PARETO_CSV";
            }
            case OFFICIAL_THEN_ANALYTICAL -> {
                if (Files.isRegularFile(officialCandidate)) {
                    selectedPath = officialCandidate;
                    sourceCategory = "OFFICIAL_PARETO_CSV";
                } else if (isDtlz) {
                    selectedPath =
                            ensureAnalyticalDtlzFront(
                                    canonicalProblem,
                                    m,
                                    analyticalCandidate,
                                    allowOnDemandAnalyticalGeneration);
                    sourceCategory = "ANALYTICAL_PF_GEN_DTLZ";
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Frente de Pareto oficial não encontrada para %s em m=%d (e geração analítica só é suportada para DTLZ1..7): %s",
                                    canonicalProblem, m, officialCandidate));
                }
            }
            case ANALYTICAL_PREFERRED_FOR_DTLZ -> {
                if (isDtlz) {
                    selectedPath =
                            ensureAnalyticalDtlzFront(
                                    canonicalProblem,
                                    m,
                                    analyticalCandidate,
                                    allowOnDemandAnalyticalGeneration);
                    sourceCategory = "ANALYTICAL_PF_GEN_DTLZ";
                } else if (Files.isRegularFile(officialCandidate)) {
                    selectedPath = officialCandidate;
                    sourceCategory = "OFFICIAL_PARETO_CSV";
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Frente de Pareto oficial não encontrada para %s em m=%d: %s",
                                    canonicalProblem, m, officialCandidate));
                }
            }
            case CANONICAL_RESOURCE_PRIORITY -> {
                Path canonicalCandidate = canonicalFrontsDir.resolve(fileName);
                if (Files.isRegularFile(canonicalCandidate)) {
                    selectedPath = canonicalCandidate;
                    sourceCategory = "CANONICAL_RESOURCE_FRONT";
                } else if (Files.isRegularFile(analyticalCandidate)) {
                    selectedPath = analyticalCandidate;
                    sourceCategory = "ANALYTICAL_PF_GEN_DTLZ";
                } else if (isDtlz) {
                    selectedPath =
                            ensureAnalyticalDtlzFront(
                                    canonicalProblem,
                                    m,
                                    analyticalCandidate,
                                    allowOnDemandAnalyticalGeneration);
                    sourceCategory = "ANALYTICAL_PF_GEN_DTLZ";
                } else if (Files.isRegularFile(officialCandidate)) {
                    selectedPath = officialCandidate;
                    sourceCategory = "OFFICIAL_PARETO_CSV";
                } else {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Frente de Pareto não encontrada na hierarquia canônica (1: %s, 2: %s, 3: geração analítica) para problema=%s, m=%d.",
                                    canonicalCandidate, analyticalCandidate, canonicalProblem, m));
                }
            }
            default -> throw new IllegalArgumentException("Política desconhecida: " + policy);
        }

        Path normalizedPath = selectedPath.toAbsolutePath().normalize();
        FrontValidationSummary summary =
                ParetoFrontValidator.validateParetoFrontFile(normalizedPath, m);
        String sha256 = ReferenceSetValidator.computeSha256Hex(normalizedPath);

        return new ResolvedFrontArtifact(
                normalizedPath,
                policy,
                sourceCategory,
                canonicalProblem,
                m,
                summary.totalPoints(),
                summary.uniquePoints(),
                sha256);
    }

    private Path ensureAnalyticalDtlzFront(
            String canonicalProblem,
            int m,
            Path analyticalCandidate,
            boolean allowOnDemandGeneration) {
        if (Files.isRegularFile(analyticalCandidate)) {
            return analyticalCandidate;
        }
        if (!allowOnDemandGeneration) {
            throw new IllegalArgumentException(
                    String.format(
                            "Frente analítica DTLZ não encontrada em disco para %s (m=%d) e geração sob demanda está desativada: %s",
                            canonicalProblem, m, analyticalCandidate));
        }
        int h1 = StaticParams.getOuterDivisions(m);
        int h2 = StaticParams.getInnerDivisions(m);
        GeneratedFrontReport report =
                ParetoFrontValidator.generateAndSaveAnalyticalDtlzFront(
                        canonicalProblem, m, h1, h2, analyticalFrontsDir, true);
        return report.outputFile();
    }

    /**
     * Constrói a lista completa de entradas do manifesto de artefatos ativos do estudo:
     * <ul>
     *   <li>Os 6 arquivos ativos de vetores de pesos (\(m \in \{2, 3, 5, 8, 10, 15\}\)) sob as
     *       políticas {@link WeightSourcePolicy#LEGACY_MOEAD_WEIGHTS} e
     *       {@link WeightSourcePolicy#OFFICIAL_JMETAL_RAW};</li>
     *   <li>As 96 instâncias de frentes de Pareto de referência ({@code WFG1..9} em
     *       \(m \in \{2, 3, 5, 8, 10, 15\}\) = 54 oficiais, {@code DTLZ1..7} em \(m \in \{2, 3\}\)
     *       = 14 oficiais, e {@code DTLZ1..7} em \(m \in \{5, 8, 10, 15\}\) = 28 analíticas) sob
     *       a política {@link FrontSourcePolicy#OFFICIAL_THEN_ANALYTICAL}.</li>
     * </ul>
     *
     * @return lista imutável de {@link ArtifactManifestEntry}
     */
    public List<ArtifactManifestEntry> collectActiveArtifactManifestEntries() {
        List<ArtifactManifestEntry> entries = new ArrayList<>();

        // 1. Vetores de pesos ativos para m in {2, 3, 5, 8, 10, 15}
        for (int m : ACTIVE_OBJECTIVE_DIMENSIONS) {
            int popSize = StaticParams.getPopulationSize(m);
            String id = String.format("W%dD_%d", m, popSize);

            for (WeightSourcePolicy policy :
                    List.of(
                            WeightSourcePolicy.LEGACY_MOEAD_WEIGHTS,
                            WeightSourcePolicy.OFFICIAL_JMETAL_RAW,
                            WeightSourcePolicy.CANONICAL_ZERO_REPLACED)) {
                ResolvedWeightArtifact rw = resolveWeightVector(m, popSize, policy);
                long sizeBytes = getFileSizeSafe(rw.path());
                int dupCount =
                        ReferenceSetValidator.countDuplicates(
                                rw.validatedWeights(), DEFAULT_WEIGHT_DUPLICATE_TOLERANCE);
                int uniqueCount = rw.populationSize() - dupCount;
                String relPath = toPortableRelativePath(rw.path());
                String consumptionMode =
                        rw.usedInMemoryByMoead()
                                ? "IN_MEMORY_GENERATED_BY_JMETAL_MOEAD"
                                : "DISK_FILE_READ";

                entries.add(
                        new ArtifactManifestEntry(
                                "WEIGHT_VECTOR",
                                id,
                                "MOEADD",
                                m,
                                policy.name(),
                                rw.sourceCategory(),
                                relPath,
                                sizeBytes,
                                rw.populationSize(),
                                uniqueCount,
                                rw.sha256(),
                                consumptionMode));
            }
        }

        // 2. Frentes de referência WFG1..9 em m in {2, 3, 5, 8, 10, 15} (54 oficiais)
        for (String wfg : WFG_PROBLEMS) {
            for (int m : ACTIVE_OBJECTIVE_DIMENSIONS) {
                ResolvedFrontArtifact rf =
                        resolveFront(wfg, m, FrontSourcePolicy.OFFICIAL_CSV_ONLY, false);
                entries.add(toFrontManifestEntry(rf));
            }
        }

        // 3. Frentes de referência DTLZ1..7 em m in {2, 3, 5, 8, 10, 15} (14 oficiais + 28 analíticas)
        for (String dtlz : DTLZ_PROBLEMS) {
            for (int m : ACTIVE_OBJECTIVE_DIMENSIONS) {
                ResolvedFrontArtifact rf =
                        resolveFront(dtlz, m, FrontSourcePolicy.OFFICIAL_THEN_ANALYTICAL, true);
                entries.add(toFrontManifestEntry(rf));
            }
        }

        return List.copyOf(entries);
    }

    private ArtifactManifestEntry toFrontManifestEntry(ResolvedFrontArtifact rf) {
        String id = String.format("%s.%dD", rf.problemName(), rf.m());
        long sizeBytes = getFileSizeSafe(rf.path());
        String relPath = toPortableRelativePath(rf.path());
        return new ArtifactManifestEntry(
                "REFERENCE_FRONT",
                id,
                rf.problemName(),
                rf.m(),
                rf.policyUsed().name(),
                rf.sourceCategory(),
                relPath,
                sizeBytes,
                rf.lineCount(),
                rf.uniquePointCount(),
                rf.sha256(),
                "DISK_FILE_READ");
    }

    /**
     * Exporta o manifesto consolidado de artefatos ativos em formato CSV para o caminho indicado
     * (ex.: {@code docs/auditoria/artifact-manifest.csv}).
     *
     * @param outputCsv caminho de destino do arquivo CSV
     * @return lista de entradas exportadas
     */
    public static List<ArtifactManifestEntry> exportActiveArtifactManifestCsv(Path outputCsv) {
        return new ArtifactResolver().exportManifestCsv(outputCsv);
    }

    /**
     * Exporta o manifesto consolidado de artefatos ativos desta instância para {@code outputCsv}.
     *
     * @param outputCsv caminho de destino do arquivo CSV
     * @return lista de entradas exportadas
     */
    public List<ArtifactManifestEntry> exportManifestCsv(Path outputCsv) {
        if (outputCsv == null) {
            throw new IllegalArgumentException("O caminho outputCsv não pode ser nulo.");
        }
        Path normalizedOut = outputCsv.toAbsolutePath().normalize();
        if (normalizedOut.startsWith(legacyWeightsDir)
                || normalizedOut.startsWith(officialFrontsDir)) {
            throw new IllegalArgumentException(
                    "Proibido gravar manifesto dentro dos diretórios protegidos MOEAD_Weights/ ou pareto_fronts/csv/: "
                            + outputCsv);
        }

        List<ArtifactManifestEntry> entries = collectActiveArtifactManifestEntries();
        StringBuilder sb = new StringBuilder();
        sb.append(ArtifactManifestEntry.CSV_HEADER).append(System.lineSeparator());
        for (ArtifactManifestEntry entry : entries) {
            sb.append(entry.toCsvRow()).append(System.lineSeparator());
        }

        try {
            Path parent = normalizedOut.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(normalizedOut, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao exportar manifesto de artefatos em: " + normalizedOut, e);
        }
        return entries;
    }

    /**
     * Converte um caminho para representação relativa portável (com separador {@code '/'}) em
     * relação ao {@code baseDir} deste resolvedor quando pertencer à mesma árvore/raiz.
     *
     * @param targetPath caminho alvo
     * @return representação relativa normalizada com barras {@code '/'}
     */
    public String toPortableRelativePath(Path targetPath) {
        Path normTarget = targetPath.toAbsolutePath().normalize();
        if (normTarget.getRoot() != null
                && baseDir.getRoot() != null
                && normTarget.getRoot().equals(baseDir.getRoot())) {
            return baseDir.relativize(normTarget).toString().replace('\\', '/');
        }
        return normTarget.toString().replace('\\', '/');
    }

    private static long getFileSizeSafe(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler tamanho do arquivo: " + path, e);
        }
    }
}

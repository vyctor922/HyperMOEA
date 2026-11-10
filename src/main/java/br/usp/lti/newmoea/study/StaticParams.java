package br.usp.lti.newmoea.study;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume;
import org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume;

/**
 *
 * @author vinixnan
 */
public class StaticParams {

    public static int getMaxGenerations(int m, String problem) {
        switch (m) {
            case 2:
                switch (problem) {
                    case "DTLZ1" -> {
                        return 400; // DTLZ1
                    }
                    case "DTLZ2" -> {
                        return 250; // DTLZ2
                    }
                    case "DTLZ3" -> {
                        return 1000; // DTLZ3
                    }
                    case "DTLZ4" -> {
                        return 600; // DTLZ4
                    }
                    case "DTLZ7" -> {
                        return 1000; // DTLZ7
                    }
                    case "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9" -> {
                        return 250; // WFG 2-obj
                    }
                    default -> {
                        return 1000;
                    }
                }
            case 3:
                switch (problem) {
                    case "DTLZ1" -> {
                        return 400; // DTLZ1
                    }
                    case "DTLZ2" -> {
                        return 250; // DTLZ2
                    }
                    case "DTLZ3" -> {
                        return 1000; // DTLZ3
                    }
                    case "DTLZ4" -> {
                        return 600; // DTLZ4
                    }
                    case "DTLZ7" -> {
                        return 1000; // DTLZ7
                    }
                    case "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9" -> {
                        return 400; // WFG
                    }
                    default -> {
                        return 1000;
                    }
                }

            case 5:
                switch (problem) {
                    case "DTLZ1" -> {
                        return 600; // DTLZ1
                    }
                    case "DTLZ2" -> {
                        return 350; // DTLZ2
                    }
                    case "DTLZ3" -> {
                        return 1000; // DTLZ3
                    }
                    case "DTLZ4" -> {
                        return 1000; // DTLZ4
                    }
                    case "DTLZ7" -> {
                        return 1000; // DTLZ7
                    }
                    case "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9" -> {
                        return 750; // WFG
                    }
                    default -> {
                        return 1000;
                    }
                }
            case 8:
                switch (problem) {
                    case "DTLZ1" -> {
                        return 750; // DTLZ1
                    }
                    case "DTLZ2" -> {
                        return 500; // DTLZ2
                    }
                    case "DTLZ3" -> {
                        return 1000; // DTLZ3
                    }
                    case "DTLZ4" -> {
                        return 1250; // DTLZ4
                    }
                    case "DTLZ7" -> {
                        return 1000; // DTLZ7
                    }
                    case "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9" -> {
                        return 1500; // WFG
                    }
                    default -> {
                        return 1000;
                    }
                }
            case 10:
                switch (problem) {
                    case "DTLZ1" -> {
                        return 1000;  // DTLZ1
                    }
                    case "DTLZ2" -> {
                        return 750;   // DTLZ2
                    }
                    case "DTLZ3" -> {
                        return 1500;  // DTLZ3
                    }
                    case "DTLZ4" -> {
                        return 2000;  // DTLZ4
                    }
                    case "DTLZ7" -> {
                        return 1500; // DTLZ7
                    }
                    case "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9" -> {
                        return 2000; // WFG
                    }

                }
            case 15:
                switch (problem) {
                    case "DTLZ1" -> {
                        return 1500;  // DTLZ1
                    }
                    case "DTLZ2" -> {
                        return 1000;  // DTLZ2
                    }
                    case "DTLZ3" -> {
                        return 2000;  // DTLZ3
                    }
                    case "DTLZ4" -> {
                        return 3000;  // DTLZ4
                    }
                    case "DTLZ7" -> {
                        return 2000; // DTLZ7
                    }
                    case "WFG1", "WFG2", "WFG3", "WFG4", "WFG5", "WFG6", "WFG7", "WFG8", "WFG9" -> {
                        return 3000; // WFG
                    }
                }
            default:
                return 1000;
        }
    }

    public static int getPopulationSize(int qtdObj) {
        switch (qtdObj) {
            case 2:
                return 100;
            case 3:
                return 91;
            case 5:
                return 210;
            case 8:
                return 156;
            //9
            case 10:
                return 275;
            case 15:
                return 135;
        }
        return 100;
    }

    /**
     * Retorna o número canônico de divisões da camada externa (\(H_1\)) de Das &amp; Dennis (1998)
     * e Deb &amp; Jain (2014) para o número de objetivos informado.
     *
     * @param m número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
     * @return divisões da camada externa \(H_1\)
     * @throws IllegalArgumentException se \(m\) não for suportado pelo protocolo experimental
     */
    public static int getOuterDivisions(int m) {
        return switch (m) {
            case 2 -> 99;
            case 3 -> 12;
            case 5 -> 6;
            case 8 -> 3;
            case 10 -> 3;
            case 15 -> 2;
            default -> throw new IllegalArgumentException(
                    "Número de objetivos não suportado para divisões externas (Das & Dennis): m=" + m);
        };
    }

    /**
     * Retorna o número canônico de divisões da camada interna (\(H_2\)) de Deb &amp; Jain (2014)
     * para o número de objetivos informado (\(0\) indica uso exclusivo de camada única).
     *
     * @param m número de objetivos (\(m \in \{2, 3, 5, 8, 10, 15\}\))
     * @return divisões da camada interna \(H_2\)
     * @throws IllegalArgumentException se \(m\) não for suportado pelo protocolo experimental
     */
    public static int getInnerDivisions(int m) {
        return switch (m) {
            case 2, 3, 5 -> 0;
            case 8, 10 -> 2;
            case 15 -> 1;
            default -> throw new IllegalArgumentException(
                    "Número de objetivos não suportado para divisões internas (Deb & Jain): m=" + m);
        };
    }

    private static final Pattern PROBLEM_ID_PATTERN =
            Pattern.compile("^(WFG[1-9]|DTLZ[1-7])(?:[_\\.].*)?$");

    /**
     * Extrai e valida o identificador canônico do problema ({@code WFG1}–{@code WFG9} ou
     * {@code DTLZ1}–{@code DTLZ7}) a partir do nome ou tag fornecida (ex.: {@code "WFG1"},
     * {@code "WFG1_2"}, {@code "DTLZ7.3D.csv"}).
     *
     * @param problemName nome ou tag do problema
     * @return identificador canônico em letras maiúsculas (ex.: {@code "WFG1"}, {@code "DTLZ7"})
     * @throws IllegalArgumentException se {@code problemName} for nulo, vazio ou não corresponder a WFG1–9 / DTLZ1–7
     */
    public static String normalizeProblemId(String problemName) {
        if (problemName == null || problemName.isBlank()) {
            throw new IllegalArgumentException("O nome do problema não pode ser nulo ou vazio.");
        }
        Matcher matcher = PROBLEM_ID_PATTERN.matcher(problemName.trim().toUpperCase());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Problema não reconhecido para configuração de hipervolume (esperado WFG1..WFG9 ou DTLZ1..DTLZ7): "
                            + problemName);
        }
        return matcher.group(1);
    }

    /**
     * Retorna os limites superiores teóricos da frente de Pareto verdadeira no espaço bruto de
     * objetivos para o problema e número de objetivos \(m\):
     * <ul>
     *   <li>{@code WFG1}–{@code WFG9}: \(S_j = 2j\) para \(j = 1, \dots, m\) (Huband et al., 2006);</li>
     *   <li>{@code DTLZ1}: \(0.5\) em cada coordenada (\(\sum_{j=1}^m f_j = 0.5\));</li>
     *   <li>{@code DTLZ2}–{@code DTLZ4}: \(1.0\) em cada coordenada (\(\sum_{j=1}^m f_j^2 = 1.0\));</li>
     *   <li>{@code DTLZ5}–{@code DTLZ6}: \(2^{-(m - 1 - \max(j-1, 1))/2} \le 1.0\) para \(j < m\) e \(1.0\) para \(j = m\);</li>
     *   <li>{@code DTLZ7}: \(1.0\) para \(j = 1, \dots, m-1\) e \(2m\) para \(j = m\).</li>
     * </ul>
     *
     * @param problemName nome ou tag do problema ({@code WFG1}–{@code WFG9} ou {@code DTLZ1}–{@code DTLZ7})
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor {@code double[m]} com o limite superior teórico da frente de Pareto em cada objetivo
     * @throws IllegalArgumentException se {@code problemName} for inválido ou se \(m < 2\)
     */
    public static double[] getParetoFrontUpperBounds(String problemName, int m) {
        String canonicalProblem = normalizeProblemId(problemName);
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        double[] bounds = new double[m];
        if (canonicalProblem.startsWith("WFG")) {
            for (int j = 0; j < m; j++) {
                bounds[j] = 2.0 * (j + 1);
            }
        } else {
            switch (canonicalProblem) {
                case "DTLZ1" -> Arrays.fill(bounds, 0.5);
                case "DTLZ2", "DTLZ3", "DTLZ4", "DTLZ5", "DTLZ6" -> Arrays.fill(bounds, 1.0);
                case "DTLZ7" -> {
                    Arrays.fill(bounds, 0, m - 1, 1.0);
                    bounds[m - 1] = 2.0 * m;
                }
                default -> throw new IllegalArgumentException("Problema não suportado: " + canonicalProblem);
            }
        }
        return bounds;
    }

    /**
     * Retorna o ponto de referência comum padronizado no espaço bruto de objetivos para cálculo de
     * hipervolume por problema e número de objetivos \(m\) (T08):
     * <ul>
     *   <li>{@code WFG1}–{@code WFG9}: \(z_j = 2.2 \times j = 1.1 \times S_j\) para \(j = 1, \dots, m\)
     *       (com \(S_j = 2j\), garantindo \(z_j > \max f_j^* = 2j\) em todas as coordenadas, inclusive
     *       para {@code WFG1.5D.csv} e {@code WFG3.2D.csv});</li>
     *   <li>{@code DTLZ1}: \((0.6, \dots, 0.6) \in \mathbb{R}^m\) (estritamente pior que o hiperplano
     *       \(\sum f_j = 0.5, f_j \in [0, 0.5]\));</li>
     *   <li>{@code DTLZ2}–{@code DTLZ6}: \((1.1, \dots, 1.1) \in \mathbb{R}^m\) (estritamente pior que
     *       \(f_j \in [0, 1.0]\));</li>
     *   <li>{@code DTLZ7}: \((1.1, \dots, 1.1, 2.1 \times m) \in \mathbb{R}^m\) (estritamente pior que
     *       \(f_j \in [0, 1.0]\) para \(j < m\) e \(f_m \in [2.308, 2m]\)).</li>
     * </ul>
     * Todos os algoritmos comparados em um mesmo problema e \(m\) utilizam exatamente o mesmo
     * ponto de referência.
     *
     * @param problemName nome ou tag do problema (ex.: {@code "WFG1"}, {@code "WFG1_2"}, {@code "DTLZ7"})
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor {@code double[m]} com as coordenadas do ponto de referência no espaço bruto
     * @throws IllegalArgumentException se {@code problemName} for nulo/vazio, desconhecido, ou se \(m < 2\)
     */
    public static double[] getHypervolumeReferencePoint(String problemName, int m) {
        String canonicalProblem = normalizeProblemId(problemName);
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        double[] refPoint = new double[m];
        if (canonicalProblem.startsWith("WFG")) {
            for (int j = 0; j < m; j++) {
                refPoint[j] = 2.2 * (j + 1);
            }
            if ("WFG3".equals(canonicalProblem) && m == 2) {
                // WFG3.2D.csv legado estende a reta degenerada f_2 = 4 - 2*f_1 até f_1 = 3.0
                refPoint[0] = 3.1;
            }
        } else {
            switch (canonicalProblem) {
                case "DTLZ1" -> Arrays.fill(refPoint, 0.6);
                case "DTLZ2", "DTLZ3", "DTLZ4", "DTLZ5", "DTLZ6" -> Arrays.fill(refPoint, 1.1);
                case "DTLZ7" -> {
                    Arrays.fill(refPoint, 0, m - 1, 1.1);
                    refPoint[m - 1] = 2.1 * m;
                }
                default -> throw new IllegalArgumentException("Problema não suportado: " + canonicalProblem);
            }
        }
        return refPoint;
    }

    /**
     * Retorna o ponto de referência efetivo no espaço normalizado \([0, 1]^m\) utilizado pelo
     * pipeline {@code ComputeQualityIndicators} do jMetal 7.8 quando a frente de referência é
     * normalizada via {@code NormalizeUtils.normalize(referenceFront)} e invertida por
     * {@code VectorUtils.getInvertedFront(front)} em {@link PISAHypervolume}.
     *
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor \((1.0, \dots, 1.0) \in \mathbb{R}^m\)
     * @throws IllegalArgumentException se \(m < 2\)
     */
    public static double[] getNormalizedHypervolumeReferencePoint(int m) {
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        double[] refPoint = new double[m];
        Arrays.fill(refPoint, 1.0);
        return refPoint;
    }

    /**
     * Cria uma instância da métrica primária de hipervolume bruto ({@link PISAHypervolume})
     * configurada com o ponto de referência comum do problema e número de objetivos \(m\).
     *
     * @param problemName nome do problema (WFG ou DTLZ)
     * @param m número de objetivos (\(m \ge 2\))
     * @return instância de {@link PISAHypervolume} (maximização, {@code name() = "HV"})
     */
    public static PISAHypervolume createPrimaryHypervolume(String problemName, int m) {
        return new PISAHypervolume(getHypervolumeReferencePoint(problemName, m));
    }

    /**
     * Cria uma instância da métrica secundária de hipervolume normalizado ({@link NormalizedHypervolume})
     * configurada com o ponto de referência comum do problema e número de objetivos \(m\).
     *
     * @param problemName nome do problema (WFG ou DTLZ)
     * @param m número de objetivos (\(m \ge 2\))
     * @return instância de {@link NormalizedHypervolume} (minimização, {@code name() = "NHV"})
     */
    public static NormalizedHypervolume createSecondaryNormalizedHypervolume(String problemName, int m) {
        double[] referencePoint = getHypervolumeReferencePoint(problemName, m);
        return new NormalizedHypervolume(new double[][]{referencePoint});
    }

    /**
     * Cria uma instância da métrica secundária de hipervolume normalizado ({@link NormalizedHypervolume})
     * a partir de uma frente de referência explícita.
     *
     * @param referenceFront matriz da frente de referência
     * @return instância de {@link NormalizedHypervolume} (minimização, {@code name() = "NHV"})
     */
    public static NormalizedHypervolume createSecondaryNormalizedHypervolume(double[][] referenceFront) {
        if (referenceFront == null || referenceFront.length == 0) {
            throw new IllegalArgumentException("A frente de referência não pode ser nula ou vazia.");
        }
        return new NormalizedHypervolume(referenceFront);
    }

}

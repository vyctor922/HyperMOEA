package br.usp.lti.newmoea.study;

import java.util.Arrays;
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

    /**
     * Retorna o ponto de referência comum padronizado para cálculo de hipervolume por família de
     * problema e número de objetivos \(m\):
     * <ul>
     *   <li>Problemas WFG ({@code WFG1}–{@code WFG9}): \((2.0, \dots, 2.0) \in \mathbb{R}^m\);</li>
     *   <li>Problemas DTLZ ({@code DTLZ1}–{@code DTLZ7}): \((1.0, \dots, 1.0) \in \mathbb{R}^m\).</li>
     * </ul>
     * Todos os algoritmos comparados em um mesmo problema e \(m\) utilizam exatamente o mesmo
     * ponto de referência.
     *
     * @param problemName nome ou tag do problema (ex.: {@code "WFG1"}, {@code "WFG1_2"}, {@code "DTLZ2"})
     * @param m número de objetivos (\(m \ge 2\))
     * @return vetor {@code double[m]} com as coordenadas do ponto de referência comum
     * @throws IllegalArgumentException se {@code problemName} for nulo/vazio, não pertencer a WFG/DTLZ, ou se \(m < 2\)
     */
    public static double[] getHypervolumeReferencePoint(String problemName, int m) {
        if (problemName == null || problemName.isBlank()) {
            throw new IllegalArgumentException("O nome do problema não pode ser nulo ou vazio.");
        }
        if (m < 2) {
            throw new IllegalArgumentException("O número de objetivos m deve ser >= 2, mas foi: " + m);
        }
        String normalized = problemName.trim().toUpperCase();
        double refValue;
        if (normalized.startsWith("WFG")) {
            refValue = 2.0;
        } else if (normalized.startsWith("DTLZ")) {
            refValue = 1.0;
        } else {
            throw new IllegalArgumentException(
                    "Família de problema não reconhecida para ponto de referência de hipervolume: "
                            + problemName);
        }
        double[] refPoint = new double[m];
        Arrays.fill(refPoint, refValue);
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

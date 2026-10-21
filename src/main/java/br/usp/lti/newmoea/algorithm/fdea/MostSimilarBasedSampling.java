package br.usp.lti.newmoea.algorithm.fdea;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedimento de amostragem baseada em máxima similaridade (MSBS - Most Similar Based Sampling)
 * do algoritmo FDEA original (Liu et al., 2022).
 *
 * Realiza poda iterativa dos pares de soluções mais similares no espaço de objetivos traduzidos,
 * retendo targetSize soluções de referência no subconjunto 0 e alocando as podadas no subconjunto 1.
 */
public class MostSimilarBasedSampling {

    private final List<FDEAIndividual> solutionSet;
    private final int numberOfObjectives;

    public MostSimilarBasedSampling(List<FDEAIndividual> solutionSet, int numberOfObjectives) {
        this.solutionSet = solutionSet;
        this.numberOfObjectives = numberOfObjectives;
    }

    @SuppressWarnings("unchecked")
    public List<FDEAIndividual>[] getIdealPointOrientedPopulation(int targetSize) {
        int solutionSize = solutionSet.size();
        for (int p = 0; p < solutionSize; p++) {
            solutionSet.get(p).setRemoved(false);
        }

        double minDist = Double.MAX_VALUE;
        double[] minDists = new double[solutionSize];
        int[] minIndexs = new int[solutionSize];
        int[] index = new int[4];
        index[0] = index[1] = index[2] = index[3] = -1;

        for (int i = 0; i < solutionSize; i++) {
            double min = Double.MAX_VALUE;
            for (int j = 0; j < solutionSize; j++) {
                if (i != j) {
                    double dist = computeDistance(solutionSet.get(i), solutionSet.get(j));
                    if (dist < min) {
                        min = dist;
                        index[0] = i;
                        index[1] = j;
                    }
                }
            }
            minDists[i] = min;
            minIndexs[i] = index[1];
            if (minDists[i] < minDist) {
                minDist = minDists[i];
                index[2] = index[0];
                index[3] = index[1];
            }
        }

        int[] secIndex = new int[2];
        double[] secMinDists = new double[solutionSize];
        int[] secMinIndexs = new int[solutionSize];
        for (int i = 0; i < solutionSize; i++) {
            double min = Double.MAX_VALUE;
            for (int j = 0; j < solutionSize; j++) {
                if (i != j && minIndexs[i] != j) {
                    double dist = computeDistance(solutionSet.get(i), solutionSet.get(j));
                    if (dist < min) {
                        min = dist;
                        secIndex[0] = i;
                        secIndex[1] = j;
                    }
                }
            }
            secMinDists[i] = min;
            secMinIndexs[i] = secIndex[1];
        }

        int remainSize = solutionSize;
        while (remainSize > targetSize) {
            double fitness1 = secMinDists[index[2]];
            double fitness2 = secMinDists[index[3]];
            int toRemove = (fitness1 < fitness2) ? index[2] : index[3];

            solutionSet.get(toRemove).setRemoved(true);

            // Atualiza secMin para indivíduos que apontavam para toRemove
            for (int i = 0; i < solutionSize; i++) {
                if (secMinIndexs[i] == toRemove && !solutionSet.get(i).isRemoved()) {
                    double sbmin = Double.MAX_VALUE;
                    int sb = -1;
                    for (int j = 0; j < solutionSize; j++) {
                        if (!solutionSet.get(j).isRemoved() && i != j && minIndexs[i] != j) {
                            double sbDist = computeDistance(solutionSet.get(i), solutionSet.get(j));
                            if (sbDist < sbmin) {
                                sbmin = sbDist;
                                sb = j;
                            }
                        }
                    }
                    secMinDists[i] = sbmin;
                    secMinIndexs[i] = sb;
                }
            }

            // Atualiza minDists para indivíduos que apontavam para toRemove
            for (int i = 0; i < solutionSize; i++) {
                if (minIndexs[i] == toRemove && !solutionSet.get(i).isRemoved()) {
                    minDists[i] = secMinDists[i];
                    minIndexs[i] = secMinIndexs[i];

                    double sbmin = Double.MAX_VALUE;
                    int sb = -1;
                    for (int j = 0; j < solutionSize; j++) {
                        if (!solutionSet.get(j).isRemoved() && i != j && minIndexs[i] != j) {
                            double sbDist = computeDistance(solutionSet.get(i), solutionSet.get(j));
                            if (sbDist < sbmin) {
                                sbmin = sbDist;
                                sb = j;
                            }
                        }
                    }
                    secMinDists[i] = sbmin;
                    secMinIndexs[i] = sb;
                }
            }

            // Localiza o próximo par de mínima distância entre os restantes
            double fDist = Double.MAX_VALUE;
            for (int p = 0; p < solutionSize; p++) {
                if (!solutionSet.get(p).isRemoved()) {
                    if (minDists[p] < fDist) {
                        fDist = minDists[p];
                        index[2] = p;
                        index[3] = minIndexs[p];
                    }
                }
            }

            remainSize--;
        }

        List<FDEAIndividual>[] subSets = new List[2];
        subSets[0] = new ArrayList<>();
        subSets[1] = new ArrayList<>();
        for (FDEAIndividual ind : solutionSet) {
            if (!ind.isRemoved()) {
                subSets[0].add(ind);
            } else {
                subSets[1].add(ind);
            }
        }
        return subSets;
    }

    public double computeDistance(FDEAIndividual s1, FDEAIndividual s2) {
        double sum = 0.0;
        for (int i = 0; i < numberOfObjectives; i++) {
            double diff = s1.getIthTranslatedObjective(i) - s2.getIthTranslatedObjective(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}

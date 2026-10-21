package br.usp.lti.newmoea.algorithm.fdea;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedimento de amostragem baseada em mínima similaridade (LSBS - Least Similar Based Sampling)
 * do algoritmo FDEA original (Liu et al., 2022).
 *
 * Seleciona um subconjunto de soluções extremas e de máxima diversidade no espaço
 * de objetivos traduzidos para atuar como direções de referência na autodecomposição.
 */
public class LeastSimilarBasedSampling {

    private final List<FDEAIndividual> solutionSet;
    private final int numberOfObjectives;

    public LeastSimilarBasedSampling(List<FDEAIndividual> solutionSet, int numberOfObjectives) {
        this.solutionSet = solutionSet;
        this.numberOfObjectives = numberOfObjectives;
    }

    @SuppressWarnings("unchecked")
    public List<FDEAIndividual>[] getIdealPointOrientedPopulation(int targetSize) {
        int solutionSize = solutionSet.size();
        for (int p = 0; p < solutionSize; p++) {
            solutionSet.get(p).setRemoved(false);
        }

        List<FDEAIndividual> subSet = new ArrayList<>();

        if (solutionSize <= 2) {
            List<FDEAIndividual>[] res = new List[2];
            res[0] = new ArrayList<>(solutionSet);
            res[1] = new ArrayList<>();
            return res;
        }

        // Identifica o par inicial com máxima distância entre si
        int idMax1 = 0;
        int idMax2 = 1;
        double maxDist = computeDistance(solutionSet.get(0), solutionSet.get(1));
        for (int k = 0; k < solutionSize; k++) {
            for (int s = k + 1; s < solutionSize; s++) {
                double dist = computeDistance(solutionSet.get(k), solutionSet.get(s));
                if (dist > maxDist) {
                    maxDist = dist;
                    idMax1 = k;
                    idMax2 = s;
                }
            }
        }

        solutionSet.get(idMax1).setRemoved(true);
        solutionSet.get(idMax2).setRemoved(true);
        subSet.add(solutionSet.get(idMax1));
        subSet.add(solutionSet.get(idMax2));

        double[] angles = new double[solutionSize];
        int[] index = new int[solutionSize];

        for (int i = 0; i < solutionSize; i++) {
            angles[i] = -1.0;
            FDEAIndividual sol2 = solutionSet.get(i);
            if (!sol2.isRemoved()) {
                double minAngle = computeDistance(sol2, subSet.get(0));
                int minIndex = 0;
                for (int j = 1; j < subSet.size(); j++) {
                    FDEAIndividual sol3 = subSet.get(j);
                    double dist = computeDistance(sol2, sol3);
                    if (dist < minAngle) {
                        minAngle = dist;
                        minIndex = j;
                    }
                }
                angles[i] = minAngle;
                index[i] = minIndex;
            }
        }

        int remain = targetSize - 2;
        while (remain > 0) {
            double maxAngle = -1.0e+30;
            int maxAngleId = -1;
            for (int a = 0; a < solutionSize; a++) {
                if (!solutionSet.get(a).isRemoved()) {
                    if (angles[a] > maxAngle) {
                        maxAngle = angles[a];
                        maxAngleId = a;
                    }
                }
            }

            if (maxAngleId < 0) {
                break;
            }

            solutionSet.get(maxAngleId).setRemoved(true);
            subSet.add(solutionSet.get(maxAngleId));

            for (int b = 0; b < solutionSize; b++) {
                FDEAIndividual sol4 = solutionSet.get(b);
                if (!sol4.isRemoved()) {
                    FDEAIndividual sol5 = solutionSet.get(maxAngleId);
                    double dist = computeDistance(sol4, sol5);
                    if (dist < angles[b]) {
                        angles[b] = dist;
                        index[b] = subSet.size() - 1;
                    }
                }
            }
            remain--;
        }

        List<FDEAIndividual>[] subSets = new List[2];
        subSets[0] = new ArrayList<>(subSet);
        subSets[1] = new ArrayList<>();
        for (int i = 0; i < solutionSet.size(); i++) {
            if (!solutionSet.get(i).isRemoved()) {
                subSets[1].add(solutionSet.get(i));
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

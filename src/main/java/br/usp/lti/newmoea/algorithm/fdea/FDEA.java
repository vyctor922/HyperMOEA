package br.usp.lti.newmoea.algorithm.fdea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.uma.jmetal.algorithm.Algorithm;
import org.uma.jmetal.operator.crossover.CrossoverOperator;
import org.uma.jmetal.operator.mutation.MutationOperator;
import org.uma.jmetal.operator.selection.SelectionOperator;
import org.uma.jmetal.problem.Problem;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.util.ranking.impl.FastNonDominatedSortRanking;

/**
 * Adaptador do algoritmo FDEA (Fuzzy Decomposition-based Evolutionary Algorithm)
 * portado para a API do jMetal 7.8 e JDK 21 a partir da implementação original de Liu et al. (2022).
 *
 * Preserva estritamente a fundamentação científica:
 * 1. Estimativa adaptativa de curvatura p da fronteira de Pareto.
 * 2. Autodecomposição do espaço de busca via MSBS (Most Similar Based Sampling)
 *    e LSBS (Least Similar Based Sampling).
 * 3. Estimativa robusta do ponto ideal (z*) e ponto Nadir (znad) via hiperplano extremo (ASF).
 * 4. Seleção ambiental balanceada entre convergência e diversidade.
 */
public class FDEA implements Algorithm<List<DoubleSolution>> {

    private final Problem<DoubleSolution> problem;
    private final int populationSize;
    private final int maxGenerations;
    private final int maxEvaluations;
    private final CrossoverOperator<DoubleSolution> crossover;
    private final MutationOperator<DoubleSolution> mutation;
    private final SelectionOperator<List<DoubleSolution>, DoubleSolution> selection;

    private List<DoubleSolution> population;
    private int evaluations;
    private int generations;

    private double[] zideal;
    private double[] znadir;
    private double[][] extremePoints;

    private final Random random = new Random();

    public FDEA(Problem<DoubleSolution> problem,
                int populationSize,
                int maxGenerations,
                int maxEvaluations,
                CrossoverOperator<DoubleSolution> crossover,
                MutationOperator<DoubleSolution> mutation,
                SelectionOperator<List<DoubleSolution>, DoubleSolution> selection) {
        this.problem = problem;
        this.populationSize = populationSize;
        this.maxGenerations = maxGenerations;
        this.maxEvaluations = maxEvaluations > 0 ? maxEvaluations : maxGenerations * populationSize;
        this.crossover = crossover;
        this.mutation = mutation;
        this.selection = selection;
        this.evaluations = 0;
        this.generations = 0;
    }

    @Override
    public void run() {
        initPopulation();
        initIdealPoint();
        initNadirPoint();
        initExtremePoints();

        int numberOfObjectives = problem.numberOfObjectives();

        while (evaluations < maxEvaluations && generations < maxGenerations) {
            List<DoubleSolution> offspring = reproduction();
            List<DoubleSolution> union = new ArrayList<>(population.size() + offspring.size());
            union.addAll(population);
            union.addAll(offspring);

            FastNonDominatedSortRanking<DoubleSolution> ranking = new FastNonDominatedSortRanking<>();
            ranking.compute(union);

            List<DoubleSolution> st0 = ranking.getSubFront(0);
            List<DoubleSolution> st1 = new ArrayList<>();
            int remaining = populationSize;
            int frontIndex = 0;

            while (frontIndex < ranking.getNumberOfSubFronts() && remaining > 0) {
                List<DoubleSolution> currentFront = ranking.getSubFront(frontIndex);
                if (currentFront.size() <= remaining) {
                    st1.addAll(currentFront);
                    remaining -= currentFront.size();
                } else {
                    st1.addAll(currentFront);
                    break;
                }
                frontIndex++;
            }

            if (st1.size() == populationSize) {
                population = new ArrayList<>(st1);
            } else {
                updateIdealPoint(st0);
                if (st0.size() < numberOfObjectives) {
                    updateNadirPoint(st1);
                } else {
                    updateNadirPoint(st0);
                }

                List<FDEAIndividual> pool = wrapSolutions(st1);
                normalizeObjectives(pool);

                double p = estimateCurvature(wrapSolutions(st0));
                mapping(pool, p);

                List<FDEAIndividual>[] msbsSubSets = new MostSimilarBasedSampling(pool, numberOfObjectives)
                        .getIdealPointOrientedPopulation(populationSize);

                List<DoubleSolution> nextPop = selectNextPopulation(msbsSubSets, p);
                population = nextPop;
            }

            generations++;
        }
    }

    private void initPopulation() {
        population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            DoubleSolution solution = problem.createSolution();
            problem.evaluate(solution);
            evaluations++;
            population.add(solution);
        }
    }

    private List<DoubleSolution> reproduction() {
        List<DoubleSolution> offspring = new ArrayList<>(populationSize);
        while (offspring.size() < populationSize && evaluations < maxEvaluations) {
            DoubleSolution p1;
            DoubleSolution p2;

            if (selection != null) {
                p1 = selection.execute(population);
                p2 = selection.execute(population);
            } else {
                p1 = population.get(random.nextInt(populationSize));
                p2 = population.get(random.nextInt(populationSize));
            }

            List<DoubleSolution> parents = List.of(p1, p2);
            List<DoubleSolution> children = crossover.execute(parents);
            DoubleSolution child = children.get(0);
            mutation.execute(child);
            problem.evaluate(child);
            evaluations++;
            offspring.add(child);
        }
        return offspring;
    }

    private void initIdealPoint() {
        int m = problem.numberOfObjectives();
        zideal = new double[m];
        Arrays.fill(zideal, Double.MAX_VALUE);
        for (DoubleSolution sol : population) {
            for (int j = 0; j < m; j++) {
                if (sol.objectives()[j] < zideal[j]) {
                    zideal[j] = sol.objectives()[j];
                }
            }
        }
    }

    private void initNadirPoint() {
        int m = problem.numberOfObjectives();
        znadir = new double[m];
        Arrays.fill(znadir, -Double.MAX_VALUE);
        for (DoubleSolution sol : population) {
            for (int j = 0; j < m; j++) {
                if (sol.objectives()[j] > znadir[j]) {
                    znadir[j] = sol.objectives()[j];
                }
            }
        }
    }

    private void initExtremePoints() {
        int m = problem.numberOfObjectives();
        extremePoints = new double[m][m];
        for (int i = 0; i < m; i++) {
            Arrays.fill(extremePoints[i], 1.0e+30);
        }
    }

    private void updateIdealPoint(List<DoubleSolution> front) {
        int m = problem.numberOfObjectives();
        for (DoubleSolution sol : front) {
            for (int j = 0; j < m; j++) {
                if (sol.objectives()[j] < zideal[j]) {
                    zideal[j] = sol.objectives()[j];
                }
            }
        }
    }

    private void updateNadirPoint(List<DoubleSolution> front) {
        int m = problem.numberOfObjectives();
        for (DoubleSolution sol : front) {
            updateExtremePoints(sol.objectives());
        }

        double[][] ex = new double[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                ex[i][j] = extremePoints[i][j] - zideal[j];
            }
        }

        double[] u = new double[m];
        Arrays.fill(u, 1.0);
        double[] al = solveLinearSystem(ex, u);

        boolean success = (al != null);
        if (success) {
            for (int j = 0; j < m; j++) {
                double aj = (1.0 / al[j]) + zideal[j];
                if (aj > zideal[j] && !Double.isInfinite(aj) && !Double.isNaN(aj)) {
                    znadir[j] = aj;
                } else {
                    success = false;
                    break;
                }
            }
        }

        if (!success) {
            for (int j = 0; j < m; j++) {
                double maxVal = -Double.MAX_VALUE;
                for (DoubleSolution sol : front) {
                    if (sol.objectives()[j] > maxVal) {
                        maxVal = sol.objectives()[j];
                    }
                }
                znadir[j] = Math.max(maxVal, zideal[j] + 1e-6);
            }
        }
    }

    private void updateExtremePoints(double[] obj) {
        int m = problem.numberOfObjectives();
        for (int i = 0; i < m; i++) {
            double asf1 = asfFunction(obj, i);
            double asf2 = asfFunction(extremePoints[i], i);
            if (asf1 < asf2) {
                System.arraycopy(obj, 0, extremePoints[i], 0, m);
            }
        }
    }

    private double asfFunction(double[] ref, int j) {
        double max = -Double.MAX_VALUE;
        double epsilon = 1.0e-6;
        int m = problem.numberOfObjectives();

        for (int i = 0; i < m; i++) {
            double denom = znadir[i] - zideal[i];
            if (Math.abs(denom) < 1e-12) {
                denom = 1e-12;
            }
            double val = Math.abs((ref[i] - zideal[i]) / denom);
            if (j != i) {
                val = val / epsilon;
            }
            if (val > max) {
                max = val;
            }
        }
        return max;
    }

    private List<FDEAIndividual> wrapSolutions(List<DoubleSolution> solutions) {
        int m = problem.numberOfObjectives();
        List<FDEAIndividual> wrapped = new ArrayList<>(solutions.size());
        for (DoubleSolution sol : solutions) {
            wrapped.add(new FDEAIndividual(sol, m));
        }
        return wrapped;
    }

    private void normalizeObjectives(List<FDEAIndividual> pool) {
        int m = problem.numberOfObjectives();
        for (FDEAIndividual ind : pool) {
            for (int j = 0; j < m; j++) {
                double denom = znadir[j] - zideal[j];
                if (Math.abs(denom) < 1e-12) {
                    denom = 1e-12;
                }
                double val = (ind.getSolution().objectives()[j] - zideal[j]) / denom;
                ind.setNormalizedObjective(j, Math.max(0.0, val));
            }
        }
    }

    private double estimateCurvature(List<FDEAIndividual> front) {
        int m = problem.numberOfObjectives();
        normalizeObjectives(front);

        List<FDEAIndividual> valid = new ArrayList<>();
        for (FDEAIndividual ind : front) {
            boolean allLeqOne = true;
            for (int j = 0; j < m; j++) {
                if (ind.getNormalizedObjective(j) > 1.0) {
                    allLeqOne = false;
                    break;
                }
            }
            if (allLeqOne) {
                valid.add(ind);
            }
        }

        if (valid.isEmpty()) {
            valid.addAll(front);
        }

        int size = valid.size();
        double sum = 0.0;
        double[] dis = new double[size];
        double sqrtM = Math.sqrt(m);

        for (int i = 0; i < size; i++) {
            double objSum = 0.0;
            for (int j = 0; j < m; j++) {
                objSum += valid.get(i).getNormalizedObjective(j);
            }
            dis[i] = (objSum - 1.0) / sqrtM;
            sum += dis[i];
        }

        double mean = sum / size;
        double varSum = 0.0;
        for (int i = 0; i < size; i++) {
            varSum += Math.pow(dis[i] - mean, 2);
        }
        double var = size > 1 ? varSum / (size - 1) : varSum / size;
        double cv = Math.sqrt(var) / (Math.abs(mean) > 1e-12 ? Math.abs(mean) : 1e-12);

        double c;
        if (mean >= 0) {
            int t2 = 51;
            double[] p1 = new double[t2];
            for (int k = 0; k < t2; k++) {
                p1[k] = 1.0 + 0.05 * k;
            }
            double[] e1 = new double[t2];
            for (int i = 0; i < t2; i++) {
                double ss = 0.0;
                for (int n = 0; n < size; n++) {
                    double sumV = 0.0;
                    for (int j = 0; j < m; j++) {
                        sumV += Math.pow(valid.get(n).getNormalizedObjective(j), p1[i]);
                    }
                    ss += sumV;
                }
                e1[i] = ss / size;
            }

            int minId = 0;
            double minDiff = Math.abs(1.0 - e1[0]);
            for (int i = 1; i < t2; i++) {
                double diff = Math.abs(1.0 - e1[i]);
                if (diff < minDiff) {
                    minDiff = diff;
                    minId = i;
                }
            }
            c = p1[minId];
        } else {
            int t1 = 17;
            double[] p2 = new double[t1];
            for (int k = 0; k < t1; k++) {
                p2[k] = 1.0 - 0.03 * k;
            }
            double[] e2 = new double[t1];
            for (int i = 0; i < t1; i++) {
                double ss = 0.0;
                for (int n = 0; n < size; n++) {
                    double sumV = 0.0;
                    for (int j = 0; j < m; j++) {
                        sumV += Math.pow(valid.get(n).getNormalizedObjective(j), p2[i]);
                    }
                    ss += Math.pow(sumV, 1.0 / p2[i]);
                }
                e2[i] = ss / size;
            }

            int minId = 0;
            double minDiff = Math.abs(1.0 - e2[0]);
            for (int i = 1; i < t1; i++) {
                double diff = Math.abs(1.0 - e2[i]);
                if (diff < minDiff) {
                    minDiff = diff;
                    minId = i;
                }
            }
            c = p2[minId];
        }

        if (c != 1.0 && cv < 0.15 && random.nextDouble() < 0.95) {
            c = mean < 0 ? 1.0 - cv : 1.0 + cv;
        }

        return Math.max(0.4, Math.min(4.0, c));
    }

    private void mapping(List<FDEAIndividual> pool, double p) {
        int m = problem.numberOfObjectives();
        for (FDEAIndividual ind : pool) {
            double normDistance = 0.0;
            double sumValue = 0.0;
            double euclidDist = 0.0;

            for (int j = 0; j < m; j++) {
                double val = ind.getNormalizedObjective(j);
                normDistance += Math.pow(val, p);
                sumValue += val;
                euclidDist += val * val;
            }

            normDistance = Math.pow(normDistance, 1.0 / p);
            euclidDist = Math.sqrt(euclidDist);

            if (normDistance < 1e-12) {
                normDistance = 1e-3;
            }
            if (euclidDist < 1e-12) {
                euclidDist = 1e-3;
            }

            ind.setDistanceToIdealPoint(normDistance);
            ind.setDistanceToNadirPoint(euclidDist);
            ind.setSumValue(sumValue);

            for (int j = 0; j < m; j++) {
                ind.setIthTranslatedObjective(j, ind.getNormalizedObjective(j) / normDistance);
                ind.setUnitHypersphereObjective(j, ind.getNormalizedObjective(j) / euclidDist);
            }
        }
    }

    private List<DoubleSolution> selectNextPopulation(List<FDEAIndividual>[] msbsSubSets, double p) {
        int m = problem.numberOfObjectives();
        List<FDEAIndividual> refPool = msbsSubSets[0];
        List<FDEAIndividual> discarded = msbsSubSets[1];

        List<FDEAIndividual>[] lsbsResult = new LeastSimilarBasedSampling(refPool, m)
                .getIdealPointOrientedPopulation(m);

        List<FDEAIndividual> extremeRefs = lsbsResult[0];
        List<FDEAIndividual> otherRefs = lsbsResult[1];

        List<FDEAIndividual> allReferences = new ArrayList<>(populationSize);
        allReferences.addAll(extremeRefs);
        allReferences.addAll(otherRefs);

        // Se por arredondamento faltar referências para completar populationSize
        while (allReferences.size() < populationSize && !discarded.isEmpty()) {
            allReferences.add(discarded.remove(0));
        }

        @SuppressWarnings("unchecked")
        List<FDEAIndividual>[] clusters = new List[populationSize];
        for (int i = 0; i < populationSize; i++) {
            clusters[i] = new ArrayList<>();
            if (i < allReferences.size()) {
                clusters[i].add(allReferences.get(i));
            }
        }

        // Associa indivíduos descartados ao cluster de referência mais próximo
        for (FDEAIndividual cand : discarded) {
            double minDist = Double.MAX_VALUE;
            int minIndex = 0;
            for (int j = 0; j < allReferences.size(); j++) {
                double dist = computeDistance(cand, allReferences.get(j));
                if (dist < minDist) {
                    minDist = dist;
                    minIndex = j;
                }
            }
            clusters[minIndex].add(cand);
        }

        // Calcula centros e vetor médio de pesos re
        double[] re = new double[m];
        for (int i = 0; i < populationSize; i++) {
            if (clusters[i].isEmpty()) {
                continue;
            }
            for (int obj = 0; obj < m; obj++) {
                double sumObj = 0.0;
                for (FDEAIndividual ind : clusters[i]) {
                    sumObj += ind.getUnitHypersphereObjective(obj);
                }
                re[obj] += (sumObj / clusters[i].size());
            }
        }

        double reSum = 0.0;
        for (int obj = 0; obj < m; obj++) {
            reSum += re[obj];
        }
        if (reSum < 1e-12) {
            reSum = 1e-6;
        }
        for (int obj = 0; obj < m; obj++) {
            re[obj] /= reSum;
        }

        // Seleciona a melhor solução de cada cluster
        List<DoubleSolution> nextPop = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            if (clusters[i].isEmpty()) {
                continue;
            }
            if (i < m) {
                // Preserva o ponto extremo no cluster de objetivo correspondente
                nextPop.add(clusters[i].get(0).getSolution());
            } else {
                double minScore = Double.MAX_VALUE;
                int bestIdx = 0;
                for (int k = 0; k < clusters[i].size(); k++) {
                    double score = computeWeightedSum(clusters[i].get(k), re);
                    if (score < minScore) {
                        minScore = score;
                        bestIdx = k;
                    }
                }
                nextPop.add(clusters[i].get(bestIdx).getSolution());
            }
        }

        while (nextPop.size() < populationSize && !refPool.isEmpty()) {
            nextPop.add(refPool.remove(0).getSolution());
        }

        return nextPop;
    }

    private double computeWeightedSum(FDEAIndividual ind, double[] weights) {
        double val = 0.0;
        for (int i = 0; i < problem.numberOfObjectives(); i++) {
            val += ind.getNormalizedObjective(i) * weights[i];
        }
        return val;
    }

    private double computeDistance(FDEAIndividual s1, FDEAIndividual s2) {
        double sum = 0.0;
        for (int i = 0; i < problem.numberOfObjectives(); i++) {
            double diff = s1.getIthTranslatedObjective(i) - s2.getIthTranslatedObjective(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    public static double[] solveLinearSystem(double[][] a, double[] b) {
        int n = b.length;
        double[][] aCopy = new double[n][n];
        double[] bCopy = new double[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, aCopy[i], 0, n);
            bCopy[i] = b[i];
        }

        for (int p = 0; p < n; p++) {
            int max = p;
            for (int i = p + 1; i < n; i++) {
                if (Math.abs(aCopy[i][p]) > Math.abs(aCopy[max][p])) {
                    max = i;
                }
            }

            double[] temp = aCopy[p];
            aCopy[p] = aCopy[max];
            aCopy[max] = temp;
            double t = bCopy[p];
            bCopy[p] = bCopy[max];
            bCopy[max] = t;

            if (Math.abs(aCopy[p][p]) <= 1e-12) {
                return null;
            }

            for (int i = p + 1; i < n; i++) {
                double alpha = aCopy[i][p] / aCopy[p][p];
                bCopy[i] -= alpha * bCopy[p];
                for (int j = p; j < n; j++) {
                    aCopy[i][j] -= alpha * aCopy[p][j];
                }
            }
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < n; j++) {
                sum += aCopy[i][j] * x[j];
            }
            x[i] = (bCopy[i] - sum) / aCopy[i][i];
        }
        return x;
    }

    @Override
    public List<DoubleSolution> result() {
        FastNonDominatedSortRanking<DoubleSolution> ranking = new FastNonDominatedSortRanking<>();
        ranking.compute(population);
        return ranking.getSubFront(0);
    }

    @Override
    public String name() {
        return "FDEA";
    }

    @Override
    public String description() {
        return "Fuzzy Decomposition-Based Evolutionary Algorithm";
    }

    public List<DoubleSolution> getPopulation() {
        return population;
    }
}

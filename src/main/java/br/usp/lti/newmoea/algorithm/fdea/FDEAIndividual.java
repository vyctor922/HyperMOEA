package br.usp.lti.newmoea.algorithm.fdea;

import org.uma.jmetal.solution.doublesolution.DoubleSolution;

/**
 * Estrutura auxiliar para armazenar atributos geométricos e de autodecomposição
 * utilizados pelo FDEA (Fuzzy Decomposition-based Evolutionary Algorithm),
 * sem poluir ou depender de classes legadas do jMetal 4.x.
 */
public class FDEAIndividual {

    private final DoubleSolution solution;
    private final double[] normalizedObjectives;
    private final double[] translatedObjectives;
    private final double[] unitHypersphereObjectives;
    private double distanceToIdealPoint;
    private double distanceToNadirPoint;
    private double sumValue;
    private boolean removed;

    public FDEAIndividual(DoubleSolution solution, int numberOfObjectives) {
        this.solution = solution;
        this.normalizedObjectives = new double[numberOfObjectives];
        this.translatedObjectives = new double[numberOfObjectives];
        this.unitHypersphereObjectives = new double[numberOfObjectives];
        this.removed = false;
    }

    public DoubleSolution getSolution() {
        return solution;
    }

    public double[] getNormalizedObjectives() {
        return normalizedObjectives;
    }

    public double getNormalizedObjective(int index) {
        return normalizedObjectives[index];
    }

    public void setNormalizedObjective(int index, double value) {
        this.normalizedObjectives[index] = value;
    }

    public double[] getTranslatedObjectives() {
        return translatedObjectives;
    }

    public double getIthTranslatedObjective(int index) {
        return translatedObjectives[index];
    }

    public void setIthTranslatedObjective(int index, double value) {
        this.translatedObjectives[index] = value;
    }

    public double[] getUnitHypersphereObjectives() {
        return unitHypersphereObjectives;
    }

    public double getUnitHypersphereObjective(int index) {
        return unitHypersphereObjectives[index];
    }

    public void setUnitHypersphereObjective(int index, double value) {
        this.unitHypersphereObjectives[index] = value;
    }

    public double getDistanceToIdealPoint() {
        return distanceToIdealPoint;
    }

    public void setDistanceToIdealPoint(double distanceToIdealPoint) {
        this.distanceToIdealPoint = distanceToIdealPoint;
    }

    public double getDistanceToNadirPoint() {
        return distanceToNadirPoint;
    }

    public void setDistanceToNadirPoint(double distanceToNadirPoint) {
        this.distanceToNadirPoint = distanceToNadirPoint;
    }

    public double getSumValue() {
        return sumValue;
    }

    public void setSumValue(double sumValue) {
        this.sumValue = sumValue;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }
}

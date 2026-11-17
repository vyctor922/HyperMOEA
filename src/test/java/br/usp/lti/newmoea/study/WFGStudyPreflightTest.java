package br.usp.lti.newmoea.study;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.uma.jmetal.lab.experiment.util.ExperimentAlgorithm;
import org.uma.jmetal.lab.experiment.util.ExperimentProblem;
import org.uma.jmetal.problem.multiobjective.wfg.WFG1;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WFGStudyPreflightTest {

    @Test
    @DisplayName("By default, legacy algorithms are excluded and exactly 6 main algorithms are configured")
    void testMainAlgorithmsConfiguredByDefault() {
        assertFalse(WFGStudy.shouldIncludeLegacyAlgorithms(), "Legacy algorithms must be disabled by default");

        List<ExperimentProblem<DoubleSolution>> problemList = new ArrayList<>();
        problemList.add(new ExperimentProblem<>(new WFG1(2, 20, 2), "WFG1_2").setReferenceFront("WFG1.2D.csv"));

        List<ExperimentAlgorithm<DoubleSolution, List<DoubleSolution>>> algorithms =
                WFGStudy.configureAlgorithmList(problemList);

        // 1 run, 1 problem -> 6 algorithms
        int runs = Integer.getInteger("runs", 30);
        assertEquals(6 * runs, algorithms.size(), "Must configure exactly 6 algorithms per problem per run by default");
    }

    @Test
    @DisplayName("Preflight validation succeeds on standard problems with verified weights and fronts")
    void testPreflightValidationSuccess() {
        List<ExperimentProblem<DoubleSolution>> problemList = new ArrayList<>();
        problemList.add(new ExperimentProblem<>(new WFG1(2, 20, 2), "WFG1_2").setReferenceFront("WFG1.2D.csv"));
        problemList.add(new ExperimentProblem<>(new WFG1(4, 20, 3), "WFG1_3").setReferenceFront("WFG1.3D.csv"));

        assertDoesNotThrow(() -> WFGStudy.runPreflightValidation(problemList));
    }

    @Test
    @DisplayName("Preflight validation fails immediately if reference front does not exist")
    void testPreflightValidationFailsOnMissingFront() {
        List<ExperimentProblem<DoubleSolution>> problemList = new ArrayList<>();
        problemList.add(new ExperimentProblem<>(new WFG1(2, 20, 2), "WFG1_2").setReferenceFront("NON_EXISTENT_FRONT.csv"));

        assertThrows(IllegalStateException.class, () -> WFGStudy.runPreflightValidation(problemList));
    }
}


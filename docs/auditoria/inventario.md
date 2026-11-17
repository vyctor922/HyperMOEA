# Inventário Forense e Matriz de APIs (T12)

- **Data da auditoria**: 09/11/2026
- **Tarefa**: [`tasks/task12.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task12.md)
- **Inventário completo em CSV (SHA-256)**: [`docs/auditoria/inventario.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/inventario.csv) (304 arquivos inventariados)

---

## 1. Resumo do Inventário Físico do Repositório

O inventário recursivo com hashes SHA-256, tamanhos em bytes e contagem física de linhas foi gerado em [`docs/auditoria/inventario.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/inventario.csv), cobrindo 304 arquivos nas categorias exigidas:

| Categoria / Diretório | Quantidade de Arquivos | Observações Forenses |
|---|---:|---|
| Raiz (`pom.xml`, `README.md`, `AGENTS.md`, `HANDLER.md`, `TASK_COORDINATOR.md`) | 5 | Governança e configuração Maven |
| `tasks/` (`task1.md` a `task24.md`) | 24 | Sem colisão de numeração: Fase 1 (`task1..5`), Fase 2 (`task6..11`), Fase 3 (`task12..24`) |
| `docs/` (relatórios Fase 2, `auditoria/`, `performance/`, ``) | 14 | Inclui `DIAGNOSTICO.md`, `FONTES.md` e relatórios de T06–T11 |
| `src/` (`src/main/java` = 13, `src/test/java` = 10) | 23 | 13 classes de produção e 10 classes de teste |
| `MOEAD_Weights/` | 48 | 43 arquivos `W*.dat` (sendo 6 ativos do estudo: `W2D_100`, `W3D_91`, `W5D_210`, `W8D_156`, `W10D_275`, `W15D_135.dat`), 3 duplicados e 2 scripts `.m` |
| `resources/` (`resources/weightVectorFiles/`) | 30 | 29 arquivos oficiais do jMetal 7.8 em `resources/weightVectorFiles/` + 1 variante `weights_gen/W3D_91.dat`. **Nota**: O subdiretório `resources/weightVectorFiles/moead/` foi removido/consolidado no staging, causando falha em 3 testes de `MoeaddWeightValidationTest` que ainda apontavam para `COPIED_MOEAD_DIR = resources/weightVectorFiles/moead` |
| `pareto_fronts/csv/` | 160 | 160 arquivos `.csv` (54 `WFG1..9.{2,3,5,8,10,15}D.csv`, 14 `DTLZ1..7.{2,3}D.csv` e 92 de outras famílias). Nenhum `DTLZ*.{5,8,10,15}D.csv` existe em `pareto_fronts/csv/` |
| Diretórios adicionais não rastreados na raiz | — | `Fuzzy-Decomposition-Based-Evolutionary-Algorithm-RepositoryGITHUB/` (repositório original de referência do FDEA) e `Main-Hipervolume.java` |

---

## 2. Verificação de Ambiente e Dependências Reais (`pom.xml`)

- **Sistema Operacional**: Windows (`powershell`)
- **Java (`java -version` / `java.home`)**:
  - `openjdk version "21.0.12.1" 2026-08-18 LTS` (`Eclipse Adoptium`, `Temurin-21.0.12+10`)
  - `java.home`: `C:\Program Files\Java\jdk-21.0.12.1`
- **Maven (`mvn -v`)**: `Apache Maven 3.8.8` (`maven-compiler-plugin:3.13.0`, `maven-surefire-plugin:3.2.5`, `jacoco-maven-plugin:0.8.10`)
- **Árvore de Dependências (`mvn dependency:tree`)**:
  - `org.uma.jmetal:jmetal-core:jar:7.8:compile`
  - `org.uma.jmetal:jmetal-algorithm:jar:7.8:compile`
  - `org.uma.jmetal:jmetal-problem:jar:7.8:compile`
  - `org.uma.jmetal:jmetal-lab:jar:7.8:compile`
  - `org.uma.jmetal:jmetal-component:jar:7.8:compile`
  - `org.junit.jupiter:junit-jupiter:jar:5.10.2:test`
  - `org.assertj:assertj-core:jar:3.25.3:test`
  - `org.projectlombok:lombok:jar:1.18.30:provided`

---

## 3. Matriz de APIs Nativas do jMetal 7.8 (Inspecionadas via `javap`)

> [!IMPORTANT]
> **Alerta de Governança (Versão 7.8 vs. Latest)**: A documentação web atual do jMetal (`master`/`latest`, 6.x/7.x recentes) reorganizou pacotes (como `jmetal-component` vs `jmetal-algorithm`). Todas as assinaturas abaixo foram extraídas diretamente dos JARs locais `7.8` em `~/.m2/repository/org/uma/jmetal/*-7.8.jar` via `javap -cp ... -p`.

| Componente / Funcionalidade | Módulo JAR (7.8) | Pacote e Classe Exata no JAR 7.8 | Construtores e Métodos Principais | Observações Técnicas Comprovadas em Bytecode |
|---|---|---|---|---|
| **Pontos de referência (NSGA-III interno)** | `jmetal-algorithm-7.8.jar` | `org.uma.jmetal.algorithm.multiobjective.nsgaiii.util.ReferencePoint<S>` | `ReferencePoint(int)`, `void generateReferencePoints(List<ReferencePoint<S>>, int numberOfObjectives, int numberOfDivisions)` | Usado internamente por `NSGAIII`. |
| **Gerador simplex canônico (1 e 2 camadas)** | `jmetal-core-7.8.jar` | `org.uma.jmetal.util.referencepoint.ReferencePointGenerator` | `static List<double[]> generateSingleLayer(int m, int H)`, `static List<double[]> generateTwoLayers(int m, int H1, int H2)`, `static int calculateNumberOfReferencePoints(int m, int H)` | Implementa exatamente Das & Dennis (1998) e a contração centrípeta de Deb & Jain (2014): $x_j' = \frac{1}{2}x_j + \frac{1}{2m}$ na camada interna. Usado por `RVEABuilder`. |
| **Builder e Algoritmo NSGA-III** | `jmetal-algorithm-7.8.jar` | `org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder<S>` / `NSGAIII<S>` | `setMaxIterations(int)`, `setNumberOfDivisions(int)`, `setSecondLayerDivisions(int)`, `build()` | No construtor `NSGAIII(NSGAIIIBuilder)`: 1) Para `secondLayerDivisions > 0`, transforma a 2ª camada com `(v + 1.0) / (m + 1.0)` em vez de `0.5 * v + 0.5 / m`; 2) Arredonda `maxPopulationSize` para cima até múltiplo de 4 (`while (popSize % 4 > 0) popSize++`); 3) Para por `iterations >= maxIterations` (avaliando $N_{\text{alloc}} \times \text{maxIterations}$ soluções!). |
| **Builder e Algoritmo RVEA** | `jmetal-component-7.8.jar` | `org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder<S>` (retorna `EvolutionaryAlgorithm<S>`) | `RVEABuilder(Problem<S>, int populationSize, int maxEvaluations, CrossoverOperator<S>, MutationOperator<S>, double alpha, double fr, List<double[]> referenceVectors)`, `setTermination(Termination)` | **Não** existe `org.uma.jmetal.algorithm.multiobjective.rvea.RVEA` em `jmetal-algorithm-7.8.jar`; o RVEA reside em `jmetal-component-7.8.jar` e usa `TerminationByEvaluations(maxEvaluations)` + `RVEAReplacement` / `RVEAEnvironmentalSelection`. |
| **MOEA/D e MOEA/DD** | `jmetal-algorithm-7.8.jar` | `org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD<S>` / `MOEADD<S>` / `MOEADBuilder` | `AbstractMOEAD.initializeUniformWeight()`, `MOEADBuilder(Problem, Variant.MOEADD)`, `setMaxEvaluations(int)`, `setPopulationSize(int)`, `setDataDirectory(String)` | Em `MOEADD.run()`: avalia $N$ soluções iniciais (`evaluations = N`) e executa `do { ... evaluations++; } while (evaluations < maxEvaluations)`. Para $m=2$ e $N \le 300$, `MOEADD.initializeUniformWeight` **ignora** o arquivo em disco e gera $(i/(N-1), 1 - i/(N-1))$ em memória; para $m \ge 3$, lê `dataDirectory + "/W" + m + "D_" + N + ".dat"`. `theta = 5.0` é constante local em `fitnessFunction`. |
| **AGE-MOEA e AGE-MOEA-II** | `jmetal-algorithm-7.8.jar` | `org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEA<S>` / `agemoeaii.AGEMOEAII<S>` | `AGEMOEABuilder(Problem)`, `AGEMOEAIIBuilder(Problem)`, `setMaxIterations(int)`, `setPopulationSize(int)` | Em `AGEMOEA.run()`: `initProgress()` define `iterations = 1`; o laço `while (!isStoppingConditionReached())` (`iterations >= maxIterations`) executa `maxIterations - 1` gerações pós-população inicial, totalizando $\text{maxIterations} \times N_{\text{efetivo}}$ avaliações. |
| **Ponto Ideal e Nadir** | `jmetal-core-7.8.jar` | `org.uma.jmetal.util.point.impl.IdealPoint` / `NadirPoint` | `IdealPoint(int)`, `update(double[])`, `update(List<? extends Solution<?>>)` | Nota: o pacote em 7.8 é `org.uma.jmetal.util.point.impl.*` (e não `util.referencePoint.*`). |
| **Execução de Experimentos** | `jmetal-lab-7.8.jar` | `org.uma.jmetal.lab.experiment.component.impl.ExecuteAlgorithms<S, Result>` e `ExperimentAlgorithm<S, Result>` | `ExecuteAlgorithms.run()`, `ExperimentAlgorithm.runAlgorithm(Experiment)` | `ExecuteAlgorithms` usa `parallelStream()`. Em `ExperimentAlgorithm.runAlgorithm`: chama `algorithm.run()`, obtém `Result population = algorithm.result()`, e grava `FUN{runId}.csv` e `VAR{runId}.csv` diretamente via `SolutionListOutput` **sem filtrar soluções dominadas**! |
| **Geração Empírica de Frente de Referência** | `jmetal-lab-7.8.jar` | `org.uma.jmetal.lab.experiment.component.impl.GenerateReferenceParetoSetAndFrontFromDoubleSolutions` | `GenerateReferenceParetoSetAndFrontFromDoubleSolutions(Experiment)`, `void run()` | Lê `FUN*.csv` e `VAR*.csv` gerados pelos algoritmos do experimento, filtra via `NonDominatedSolutionListArchive` e sobrescreve `<referenceFrontDirectory>/<problemTag>.csv`. **Não deve ser usado sobre frentes oficiais**. |
| **Cálculo de Indicadores em Lote** | `jmetal-lab-7.8.jar` | `org.uma.jmetal.lab.experiment.component.impl.ComputeQualityIndicators<S, Result>` | `ComputeQualityIndicators(Experiment)`, `void run()` | Lê a frente de referência, extrai `minValues` e `maxValues` amostrais (`NormalizeUtils`), normaliza a referência em $[0,1]^m$ e normaliza cada `FUN{run}.csv` com os extremos da referência. **Não possui timeout** e reescreve os arquivos de indicadores a cada chamada. |
| **Hipervolume Exato (PISA, WFG, 3D)** | `jmetal-core-7.8.jar` | `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume`, `WFGHypervolume`, `Hypervolume3D` | `PISAHypervolume()`, `PISAHypervolume(double[] referencePoint)`, `PISAHypervolume(double[][] referenceFront)`, `double compute(double[][] front)` | Todos residem em `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.*`. `PISAHypervolume.hypervolume` inverte a frente via `VectorUtils.getInvertedFront` (`1.0 - f_j` após clip em `[0,1]`? Não: `getInvertedFront` faz `1.0 - front[i][j]` diretamente!) e chama `calculateHypervolume` sem verificar `Thread.interrupted()`. |
| **Hipervolume Normalizado (NHV)** | `jmetal-core-7.8.jar` | `org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume` | `NormalizedHypervolume()`, `NormalizedHypervolume(double[])`, `NormalizedHypervolume(double[][])`, `double compute(double[][])` | Internamente instancia `new PISAHypervolume(referenceFront)` e calcula `1.0 - (hypervolume.compute(front) / referenceFrontHypervolume)`. Logo, recalcula `PISAHypervolume.compute(front)` do zero (dobrando o tempo de HV!) e é transformação afim decrescente exata de `HV`. |
| **IGD, IGD+ e R2** | `jmetal-core-7.8.jar` | `org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance`, `InvertedGenerationalDistancePlus`, `R2` | `compute(double[][] front)`, `referenceFront(double[][] referenceFront)` | Em `R2`: `referenceFront(refFront)` na superclasse `QualityIndicator` **não** limpa `weightVectors` nem `idealPoint`, exigindo override explícito (já presente em `WFGStudy.java:155-162`) ou nova instância por problema/$m$. |

---

## 4. Resultado Real da Suíte de Testes Baseline (Pré-Correções da Fase 3)

Comando executado:
```powershell
mvn test "-Dperf.hvTimeoutMs=10000" "-Dperf.maxHvFrontSize15D=16"
```
- **Resultado**: `Tests run: 277, Failures: 1, Errors: 2, Skipped: 0` (Tempo total: `35.429 s`).
- **9 de 10 classes de teste passaram 100%**:
  - `AlgorithmIntegrationTest` (21 testes, 0 falhas)
  - `NsgaIIIReferencePointsValidationTest` (25 testes, 0 falhas)
  - `RveaReferenceDirectionsValidationTest` (11 testes, 0 falhas)
  - `R2ExecutionObserverTest` (3 testes, 0 falhas)
  - `OperatorAuditTest` (10 testes, 0 falhas)
  - `HypervolumeAuditTest` (12 testes, 0 falhas)
  - `ParetoFrontValidationTest` (164 testes, 0 falhas)
  - `QualityIndicatorUnitTest` (12 testes, 0 falhas)
  - `PerformanceProfilerTest` (8 testes, 0 falhas)
- **Causa única das 3 falhas em `MoeaddWeightValidationTest` (19 testes, 1 falha, 2 erros)**:
  - O usuário consolidou os arquivos copiados em `resources/weightVectorFiles/W*.dat` (removendo a duplicação `resources/weightVectorFiles/moead/W*.dat` e editando `HANDLER.md` e `TASK_COORDINATOR.md`), mas `MoeaddWeightValidationTest.java` ainda tinha a constante `COPIED_MOEAD_DIR = Paths.get("resources", "weightVectorFiles", "moead")`. Essa reconciliação faz parte direta da migração/resolução de artefatos (`T18`).

---

## 5. Contagem Dry-Run de Configurações Experimentais

Sem instanciar os 22.440 objetos de algoritmos nem iniciar a campanha:
- **Se 11 algoritmos (estado atual de `WFGStudy.configureAlgorithmList`) $\times$ 30 runs**:
  - Apenas $m \in \{2,3\}$ para DTLZ (14 instâncias) + $m \in \{2,3,5,8,10,15\}$ para WFG (54 instâncias) = **68 instâncias de problema–dimensão**:
    $$68 \times 11 \times 30 = 22.440 \text{ execuções}$$
  - Todas as 16 famílias (`WFG1..9` + `DTLZ1..7`) em todas as 6 dimensões ($m \in \{2,3,5,8,10,15\}$) = **96 instâncias de problema–dimensão**:
    $$96 \times 11 \times 30 = 31.680 \text{ execuções}$$
- **Escopo Principal da Fase 3 (6 algoritmos principais: `NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`) $\times$ 30 runs**:
  - Com 68 instâncias (`WFG1..9` em 6 dimensões + `DTLZ1..7` em $m \in \{2,3\}$):
    $$68 \times 6 \times 30 = 12.240 \text{ execuções}$$
  - Com 84 instâncias (`WFG1..9` + `DTLZ1..4,7` em 6 dimensões + `DTLZ5..6` apenas em $m \in \{2,3\}$):
    $$84 \times 6 \times 30 = 15.120 \text{ execuções}$$
  - Com 96 instâncias (`WFG1..9` + `DTLZ1..7` em todas as 6 dimensões $m \in \{2,3,5,8,10,15\}$):
    $$96 \times 6 \times 30 = 17.280 \text{ execuções}$$


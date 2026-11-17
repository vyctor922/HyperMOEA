# Relatório de Auditoria Forense de Isolamento de Processos, Sementes (RNG) e Estado Compartilhado (T15)

- **Tarefa**: `T15` — Auditoria de Isolamento de Processos, Sementes (RNG) e Estado Compartilhado
- **Agente Responsável**: Agente E (Isolation, RNG & Reproducibility Auditor)
- **Fase**: Fase 3 — Auditoria Científica e Validação Metodológica dos Algoritmos
- **Data da Auditoria**: 09/11/2026
- **Escopo**: `JMetalRandom`, `JavaRandomGenerator`, `ExecuteAlgorithms`, `ExperimentAlgorithm`, `ComputeQualityIndicators`, `R2`, `R2ExecutionObserver`, `WFGStudy.configureAlgorithmList`, operadores (`SBXCrossover`, `PolynomialMutation`, `BinaryTournamentSelection`, `RandomSelection`), `IsolatedJobExecutor` e `IsolationAndReproducibilityTest`.

---

## 1. Diagnóstico Forense de Concorrência, PRNG e Estado Compartilhado no jMetal 7.8

### 1.1. Singleton Global `JMetalRandom` e Inicialização por Relógio de Parede

A inspeção do bytecode de `org.uma.jmetal.util.pseudorandom.JMetalRandom` e `org.uma.jmetal.util.pseudorandom.impl.JavaRandomGenerator` (`jmetal-core-7.8.jar` via `javap -c -p`) comprova que:

1. **Singleton estático não sincronizado**:
   - Em `JMetalRandom`, o campo `private static JMetalRandom instance;` é inicializado preguiçosamente em `getInstance()` (offsets `0..19`) sem `synchronized` nem `volatile`:
     ```text
     0: getstatic     #7  // Field instance:Lorg/uma/jmetal/util/pseudorandom/JMetalRandom;
     3: ifnonnull     16
     6: new           #8  // class org/uma/jmetal/util/pseudorandom/JMetalRandom
     9: dup
     10: invokespecial #9  // Method "<init>":()V
     13: putstatic     #7  // Field instance:Lorg/uma/jmetal/util/pseudorandom/JMetalRandom;
     16: getstatic     #7  // Field instance:Lorg/uma/jmetal/util/pseudorandom/JMetalRandom;
     19: areturn
     ```
2. **Semente padrão dependente de `System.currentTimeMillis()`**:
   - O construtor padrão `JavaRandomGenerator.<init>()` (offsets `0..8`) invoca `this(System.currentTimeMillis())`:
     ```text
     0: aload_0
     1: invokestatic  #1  // Method java/lang/System.currentTimeMillis:()J
     4: invokespecial #2  // Method "<init>":(J)V
     7: return
     ```
   - Em `WFGStudy.java`, **nenhuma chamada a `JMetalRandom.getInstance().setSeed(...)` é realizada** em nenhum ponto do fluxo `main` ou de `configureAlgorithmList`.

### 1.2. Mapa Completo de Consumo de `JMetalRandom.getInstance()` nos 6 Algoritmos Principais

Todos os 6 algoritmos principais (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`), seus operadores e a criação da população inicial consomem números pseudoaleatórios exclusivamente do mesmo singleton global `JMetalRandom.getInstance()`:

| Componente / Classe | Ponto de Consumo no Bytecode / Código-Fonte | Método de `JMetalRandom` Invocado |
| :--- | :--- | :--- |
| `DefaultDoubleSolution.<init>` | Offsets `37..90` (`initializeDoubleVariables`) | `JMetalRandom.getInstance().nextDouble(lowerBound, upperBound)` para cada variável $j \in \{0, \dots, n-1\}$ |
| `SBXCrossover` | Construtor `(double, double)` $\to$ `lambda$new$86fa97ed$1` (offsets `0..9`) | `JMetalRandom.getInstance().nextDouble()` (probabilidade de cruzamento, troca de variáveis e cálculo de $\beta_q$) |
| `PolynomialMutation` | Construtor `(double, double)` $\to$ `lambda$new$86fa97ed$1` (offsets `0..9`) | `JMetalRandom.getInstance().nextDouble()` (probabilidade por variável e cálculo de $\delta_q$) |
| `BinaryTournamentSelection` / `NaryTournamentSelection` | `execute(List<S>)` (offset `22`) $\to$ `ListUtils.randomSelectionWithoutReplacement` (offset `21`) $\to$ `PermutationFactory.createIntegerPermutation` | `JMetalRandom.getInstance().nextInt(0, max)` |
| `NSGA-III` (`EnvironmentalSelection`) | `selectRandomMember(ReferencePoint)` e `EnvironmentalSelection.execute` (offset `95`) | `JMetalRandom.getInstance().nextInt(0, size - 1)` |
| `MOEA/DD` (`AbstractMOEAD` + `MOEADUtils`) | `AbstractMOEAD.<init>` (offset `68`: `this.randomGenerator = JMetalRandom.getInstance()`), `chooseNeighborType` e `MOEADUtils.randomPermutation` (offset `0`) | `JMetalRandom.getInstance().nextDouble()` e `nextInt(0, n - 1)` |
| `FDEA` (`br.usp.lti.newmoea.algorithm.fdea.FDEA`) | `reproduction()` (embaralhamento de pais) e `estimateCurvature()` (amostragem quando $N > 120$) | `JMetalRandom.getInstance().nextInt(0, i)` |
| `RVEA` (`RandomSelection` / `ListUtils`) | `RandomSelection.<init>` (offset `3`: `JMetalRandom.getInstance()::nextInt`) $\to$ `ListUtils.randomSelectionWithReplacement` | `JMetalRandom.getInstance().nextInt(0, size - 1)` |
| `AGE-MOEA` / `AGE-MOEA-II` | Inicialização (`DefaultDoubleSolution`), `BinaryTournamentSelection`, `SBXCrossover` e `PolynomialMutation` | `JMetalRandom.getInstance().nextDouble(...)` e `nextInt(...)` |

### 1.3. Corrida de Dados (*Data Race* Lógica) em `ExecuteAlgorithms.run()`

Em `WFGStudy.java` (linha 185), o estudo configura `.setNumberOfCores(8)` (ou valor $> 1$). O bytecode de `org.uma.jmetal.lab.experiment.component.impl.ExecuteAlgorithms.run()` (`jmetal-lab-7.8.jar`, offsets `12..29` e `84..97`) executa:

```text
12: ldc           #9   // String java.util.concurrent.ForkJoinPool.common.parallelism
14: aload_0
15: getfield      #2   // Field experiment:Lorg/uma/jmetal/lab/experiment/Experiment;
18: invokevirtual #10  // Method Experiment.getNumberOfCores:()I
21: invokedynamic #11, 0 // InvokeDynamic #0:makeConcatWithConstants:(I)Ljava/lang/String;
26: invokestatic  #12  // Method java/lang/System.setProperty:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
...
84: invokevirtual #4   // Method Experiment.getAlgorithmList:()Ljava/util/List;
87: invokeinterface #17, 1 // InterfaceMethod java/util/List.parallelStream:()Ljava/util/stream/Stream;
92: aload_0
93: invokedynamic #18, 0 // InvokeDynamic #1:accept:(LExecuteAlgorithms;)Ljava/util/function/Consumer;
98: invokeinterface #19, 2 // InterfaceMethod java/util/stream/Stream.forEach:(Ljava/util/function/Consumer;)V
```

**Consequências diretas**:
1. Quando múltiplas threads do `ForkJoinPool.commonPool()` executam `algorithm.runAlgorithm(experiment)` simultaneamente na mesma JVM, todas as threads disputam chamadas concorrentes a `JMetalRandom.getInstance().nextDouble()` / `nextInt()`.
2. A sequência exata de números aleatórios entregue a cada job passa a depender do escalonador de threads do sistema operacional, de pausas de GC e de flutuações de I/O, tornando **impossível reproduzir qualquer execução** mesmo se uma semente fixa fosse definida no início do processo.
3. Mesmo que cada `ExperimentAlgorithm` chamasse `JMetalRandom.getInstance().setSeed(seed)` no início de `runAlgorithm()`, fazê-lo com `parallelStream()` ativo reiniciaria a semente global no meio da evolução das demais threads concorrentes!

---

## 2. Pré-Instanciação Ansiosa (*Eager Pre-Instantiation*) e Retenção de Memória em `WFGStudy.configureAlgorithmList`

### 2.1. Grafo de Propriedade (*Ownership Graph*) e Compartilhamento Indevido de Instâncias

Em `WFGStudy.configureAlgorithmList` (linhas 308–345):

```java
for (int run = 0; run < INDEPENDENT_RUNS; run++) {
    for (int i = 0; i < problemList.size(); i++) {
        Problem<DoubleSolution> problem = problemList.get(i).getProblem();
        double mutationProbability = 1.0 / problem.numberOfVariables();
        SBXCrossover crossover = new SBXCrossover(1.0, 30.0);
        PolynomialMutation mutation = new PolynomialMutation(mutationProbability, 20.0);
        // ... constrói até 11 algoritmos simultaneamente passando o MESMO problem, crossover e mutation!
    }
}
```

A auditoria identificou três problemas arquiteturais graves nesse padrão:

1. **Compartilhamento da mesma instância de `Problem`, `SBXCrossover` e `PolynomialMutation` entre todos os algoritmos de uma mesma dupla `(run, problem)`**:
   - Na configuração original com `parallelStream()`, múltiplos algoritmos do mesmo `(run, problem)` executam simultaneamente sobre a mesma instância de `Problem<DoubleSolution>`, `SBXCrossover` e `PolynomialMutation`.
   - Embora `WFG1..WFG9` e `DTLZ1..DTLZ7` não mutem campos de instância durante `evaluate()`, qualquer wrapper de orçamento (`EvaluationBudgetProblem`) compartilhado entre algoritmos somaria as avaliações de todos os algoritmos na mesma variável atômica se não for instanciado separadamente por job!
2. **Alocação ansiosa de todos os jobs antes da primeira execução**:
   - Com 16 problemas $\times$ 6 dimensões ($m \in \{2,3,5,8,10,15\}$) = 96 problemas, 6 algoritmos principais e 30 runs independentes, `configureAlgorithmList` instancia **$96 \times 6 \times 30 = 17.280$ objetos `Algorithm`** (ou **$22.440$ objetos** na configuração anterior com 11 algoritmos e 68 problemas) **antes de executar o primeiro job**.
   - A inspeção dos construtores mostra que cada instância pré-aloca estruturas pesadas já no `build()`:
     - `MOEADD` (`AbstractMOEAD.<init>` offsets `74..156` + `MOEADD.<init>`): aloca `population` (`ArrayList(N)`), `indArray[m]`, `neighborhood[N][20]`, `lambda[N][m]`, `z[m]`, `nz[m]` no `build()`, além de manter `rankIdx`, `subregionIdx` e `subregionDist` após `run()`.
     - `NSGAIII.<init>`: gera e retém `List<ReferencePoint<DoubleSolution>>` (cópias completas dos pontos de referência para cada uma das milhares de instâncias).
     - `RVEABuilder.build()`: aloca `RVEAEnvironmentalSelection` contendo matrizes `initialReferenceVectors[N][m]` e `referenceVectors[N][m]` para cada instância.
     - `FDEA.<init>`: pré-aloca a lista `population` e hiperplanos/vetores auxiliares.
3. **Retenção pós-execução (`Memory Leak` estrutural de `ExperimentAlgorithm`)**:
   - Em `ExperimentAlgorithm.runAlgorithm(Experiment)` (`jmetal-lab-7.8.jar`, offset `82`: `algorithm.run()`), o objeto `Algorithm` continua fortemente referenciado pelo campo `private final Algorithm<Result> algorithm;` dentro de `Experiment.algorithmList` até o encerramento completo do `WFGStudy.main`.
   - Como cada algoritmo armazena sua `population` final de $N$ objetos `DefaultDoubleSolution` em um campo de instância (`AbstractEvolutionaryAlgorithm.population`, `AbstractMOEAD.population`, `FDEA.population`), **todas as $17.280$ populações finais (~$2,78 \times 10^6$ objetos `DefaultDoubleSolution` somados) permanecem retidas na heap simultaneamente**, causando pressão severa no Garbage Collector e risco de `OutOfMemoryError`.

### 2.2. Estado Mutável nos Indicadores `R2` e `R2ExecutionObserver`

1. **Indicador `org.uma.jmetal.qualityindicator.impl.R2` (`jmetal-core-7.8.jar`)**:
   - Possui dois campos mutáveis de instância: `private double[][] weightVectors;` e `private double[] idealPoint;`.
   - No bytecode de `R2.compute(double[][] front)` (offsets `19..89`), se `weightVectors == null`, o indicador gera uma matriz aleatória `new double[numberOfWeights][nObj]` usando `Math.random()` e inicializa `idealPoint` a partir da frente de referência!
   - Em `WFGStudy.java` (linhas 174–182), uma única instância anônima de `R2` é compartilhada na lista `indicatorList`. Embora `WFGStudy` sobrescreva `referenceFront(double[][])` para chamar `setWeightVectors(null); setIdealPoint(null);`, qualquer uso concorrente da mesma instância `R2` entre threads causa corrida de dados direta sobre `weightVectors` e `idealPoint`.
2. **`br.usp.lti.newmoea.feedback.R2ExecutionObserver`**:
   - Mantém estado mutável por execução (`private final List<Double> history`, `private double lastR2Value` e `private final R2 nativeR2`).
   - Embora seus métodos sejam `synchronized` e exista `reset()`, um objeto `R2ExecutionObserver` **jamais pode ser compartilhado entre jobs concorrentes**; cada job que utilize observação de convergência deve instanciar seu próprio `R2ExecutionObserver` isolado.

---

## 3. Especificação Matemática da Política Determinística de Sementes por Tupla

Para garantir que a semente efetiva $S_{\text{eff}}$ de qualquer job dependa **exclusivamente** de sua identidade experimental e seja **100% independente da ordem de execução**, foi projetada e implementada em [`IsolatedJobExecutor.computeDeterministicSeed`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java#L316-L353) a função:

$$S_{\text{eff}} = f\bigl(S_{\text{base}},\, P,\, m,\, A,\, r\bigr)$$

onde:
- $S_{\text{base}} \in \mathbb{Z}_{2^{64}}$ é a semente mestre do estudo (`DEFAULT_BASE_SEED = 20260409L`);
- $P = \text{normalizeProblemId}(\text{problemName}) \in \{\text{WFG1}, \dots, \text{WFG9}, \text{DTLZ1}, \dots, \text{DTLZ7}\}$;
- $m \in \{2, 3, 5, 8, 10, 15\}$ é o número de objetivos;
- $A = \text{normalizeAlgorithmName}(\text{algorithmName}) \in \{\text{NSGA-III}, \text{MOEA/DD}, \text{FDEA}, \text{RVEA}, \text{AGE-MOEA}, \text{AGE-MOEA-II}\}$;
- $r \in \mathbb{Z}_{\ge 0}$ é o índice da execução independente (`runId`).

### 3.1. Etapas Formais da Derivação

1. **Serialização Canônica Livre de Ambiguidade**:
   Constrói-se a string UTF-8 com prefixo de domínio e delimitadores explícitos chave-valor:
   $$\tau(S_{\text{base}}, P, m, A, r) = \texttt{"hypeMOEA:v1|baseSeed="} \,\|\, S_{\text{base}} \,\|\, \texttt{"|problem="} \,\|\, P \,\|\, \texttt{"|m="} \,\|\, m \,\|\, \texttt{"|algorithm="} \,\|\, A \,\|\, \texttt{"|run="} \,\|\, r$$

2. **Resumo Criptográfico SHA-256**:
   Calcula-se o vetor de 32 bytes $D = \text{SHA-256}(\text{UTF8}(\tau))$ e extraem-se os dois primeiros blocos de 64 bits em formato big-endian:
   $$H_{\text{high}} = \sum_{i=0}^{7} (D_i \bmod 256) \cdot 2^{8(7-i)}, \qquad H_{\text{low}} = \sum_{i=8}^{15} (D_i \bmod 256) \cdot 2^{8(15-i)}$$

3. **Avalanching Bijetivo SplitMix64 (Stafford Variant 13)**:
   Combina-se $Z_0 = H_{\text{high}} \oplus \text{rotl}_{64}(H_{\text{low}}, 31) \oplus S_{\text{base}}$ e aplica-se a permutação bijetiva de 64 bits:
   $$\begin{aligned}
   Z_1 &= (Z_0 \oplus (Z_0 \ggg 30)) \cdot \texttt{0xBF58476D1CE4E5B9}_{16} \pmod{2^{64}} \\
   Z_2 &= (Z_1 \oplus (Z_1 \ggg 27)) \cdot \texttt{0x94D049BB133111EB}_{16} \pmod{2^{64}} \\
   S_{\text{eff}} &= Z_2 \oplus (Z_2 \ggg 31)
   \end{aligned}$$

### 3.2. Verificação de Ausência de Colisões na Grade Completa do Protocolo

No teste `IsolationAndReproducibilityTest#testDeterministicSeedDerivationAndAvalanche`, foram geradas todas as sementes para a grade completa do estudo:
- $16 \text{ problemas} \times 6 \text{ valores de } m \times 6 \text{ algoritmos} \times 30 \text{ runs} = \mathbf{17.280 \text{ tuplas}}$.
- **Resultado real medido**: $17.280$ sementes únicas geradas ($\mathbf{0}$ colisões).

---

## 4. Arquitetura Implementada em `IsolatedJobExecutor`

A classe [`IsolatedJobExecutor`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) implementa o contrato de isolamento e reprodutibilidade para o estudo:

1. **`JobSpec`**: record imutável contendo `(problemName, m, algorithmName, runId, baseSeed, effectiveSeed, populationSize, maxGenerations, targetEvaluations, enforceHardBudget)` com validação de invariantes no construtor compacto e métodos fábrica `ofCanonical(...)` e `ofCustomBudget(...)`.
2. **`JobExecutionResult`**: record imutável com cópia defensiva profunda (`deepCopyMatrix`) das matrizes `rawObjectives` e `rawVariables`, contadores reais (`actualEvaluations`, `rawResultSize`, `nonDominatedResultSize`), tempos (`wallTimeMs`, `cpuTimeMs`) e hashes criptográficos SHA-256 (`objectivesSha256`, `variablesSha256`) calculados sobre os bits IEEE 754 exatos (`Double.doubleToLongBits`).
3. **`createFreshProblemInstance` e `createFreshAlgorithmInstance` (Instanciação Lazy)**:
   - Cria uma nova instância de `WFG1..WFG9` ($k = 2(m-1), l = 20$) ou `DTLZ1..DTLZ7` ($n = 20$) exclusiva para o job.
   - Envolve o problema em um novo `EvaluationBudgetProblem` dedicado.
   - Cria novas instâncias exclusivas de `SBXCrossover(1.0, 30.0)`, `PolynomialMutation(1.0 / nVars, 20.0)` e do algoritmo via `EvaluationBudgetProblem.buildAuditedAlgorithm(...)` **após** configurar a semente de `JMetalRandom`.
4. **`executeJobInProcess(JobSpec)`**:
   - Adquire `JMETAL_RANDOM_LOCK` (`ReentrantLock(true)`), impedindo qualquer interferência concorrente sobre `JMetalRandom.getInstance()` dentro da JVM.
   - Configura `JMetalRandom.getInstance().setSeed(spec.effectiveSeed())`.
   - Instancia problema, wrapper de orçamento, operadores e algoritmo sob demanda, executa `algorithm.run()`, extrai resultados e hashes e descarta imediatamente a instância do algoritmo ao sair do escopo, liberando a população da memória heap.
5. **`ManifestEntry`, `exportManifestToCsv` e `readManifestFromCsv`**:
   - Exporta e valida o manifesto de reprodutibilidade CSV com as 16 colunas canônicas:
     `job_id,problem,m,algorithm,run_id,base_seed,effective_seed,n_config,max_generations,target_evaluations,actual_evaluations,raw_size,nd_size,objectives_sha256,variables_sha256,status`.

---

## 5. Prova Experimental de Reprodutibilidade Bit-a-Bit e Independência de Ordem

A suíte [`IsolationAndReproducibilityTest`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/IsolationAndReproducibilityTest.java) foi executada via `mvn test -Dtest=IsolationAndReproducibilityTest` e validou todos os 6 algoritmos principais.

### 5.1. Resultados Reais da Execução de Testes

- **Comando executado**: `mvn test -Dtest=IsolationAndReproducibilityTest`
- **Status**: `BUILD SUCCESS` (`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.259 s`)

| Teste | Escopo Verificado | Resultado |
| :--- | :--- | :--- |
| `testDeterministicSeedDerivationAndAvalanche` | Invariância de grafia (`NSGAIII` vs `NSGA-III`, `wfg1` vs `WFG1`), sensibilidade a cada componente da tupla e ausência de colisões em $17.280$ tuplas | **PASS** (17.280 / 17.280 sementes únicas) |
| `testBitForBitReproducibilityAcrossAllSixMainAlgorithms` (6 parâmetros) | `NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II` executados duas vezes com a mesma `JobSpec` (`WFG1`, $m=2$, $B=300$) | **PASS** (100% de identidade em `objectivesSha256`, `variablesSha256` e `Double.doubleToLongBits`) |
| `testOrderIndependenceAcrossPermutedJobQueues` | Fila `[JobA, JobB, JobC, JobD, JobE, JobF]` vs fila permutada `[JobF, JobD, JobB, JobE, JobC, JobA]` cobrindo os 6 algoritmos, problemas WFG/DTLZ e $m \in \{2, 3\}$ | **PASS** (hashes SHA-256 de `FUN` e `VAR` 100% idênticos em ambas as ordens) |
| `testSeedSensitivityProducesDistinctTrajectories` (6 parâmetros) | `runId = 0` vs `runId = 1` para cada um dos 6 algoritmos principais | **PASS** (`effectiveSeed`, `objectivesSha256` e `variablesSha256` distintos) |
| `testNoStateLeakageAcrossDimensionsAndProblems` | Execução de `DTLZ2` ($m=3$, `MOEA/DD`) precedida por `WFG1` ($m=2$, `NSGA-III`) vs precedida por `WFG9` ($m=5$, `FDEA`), mais round-trip completo do manifesto CSV de 16 colunas | **PASS** (zero vazamento de estado + CSV validado) |

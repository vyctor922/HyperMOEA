# Auditoria de Operadores Genéticos e Mutação (T06) — jMetal 7.8

## 1. Objetivo e Escopo

Este documento registra a auditoria técnica dos operadores genéticos instanciados em [`WFGStudy.java`](../src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) e utilizados pelos algoritmos avaliados no projeto `hypeMOEA` sobre **Java 21** e **jMetal 7.8** (`org.uma.jmetal:jmetal-core:7.8`, `org.uma.jmetal:jmetal-algorithm:7.8`, `org.uma.jmetal:jmetal-component:7.8`).

A auditoria foi conduzida mediante inspeção direta do código-fonte do projeto e decompilação do bytecode real dos artefatos Maven do jMetal 7.8 (`javap -c -p`), respondendo com evidência verificável às questões da tarefa [`tasks/task6.md`](../tasks/task6.md):
1. Quais construtores existem em `PolynomialMutation` e `SBXCrossover` no jMetal 7.8?
2. Qual a diferença entre a chamada histórica mencionada na descrição da tarefa (`new PolynomialMutation(1.0, problem.numberOfVariables(), 20.0)`) e a chamada efetivamente presente em `WFGStudy.java` (`new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)`)?
3. Qual a probabilidade efetiva de mutação por variável (`mutationProbability`) e o índice de distribuição (`distributionIndex`)?
4. Como `PolynomialMutation.doMutation` e `SBXCrossover.doCrossover` operam internamente sobre variáveis e limites (`[lowerBound, upperBound]`)?
5. Como cada algoritmo configurado em `WFGStudy.configureAlgorithmList` recebe e aplica os operadores de seleção, cruzamento e mutação?

---

## 2. Configuração Atual em `WFGStudy.java`

Em [`WFGStudy.java`](../src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) (linhas 262–264), dentro de `configureAlgorithmList(List<ExperimentProblem<DoubleSolution>> problemList)`, os operadores comuns são instanciados por problema e repetição como:

```java
CrossoverOperator crossover = new SBXCrossover(1.0, 30.0);
MutationOperator mutation = new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
CrossoverOperator de = new DifferentialEvolutionCrossover();
```

### Conclusão imediata sobre `WFGStudy.java`
A linha 263 de `WFGStudy.java` **já utiliza a assinatura e os parâmetros corretos** do jMetal 7.8:
- `mutationProbability = 1.0 / problem.numberOfVariables()` (probabilidade por variável igual a $1/n$);
- `distributionIndex = 20.0` ($\eta_m = 20.0$);
- `SBXCrossover(1.0, 30.0)` com `crossoverProbability = 1.0` ($p_c = 1.0$) e `distributionIndex = 30.0` ($\eta_c = 30.0$).

Portanto, **nenhuma alteração em `WFGStudy.java` foi necessária**.

---

## 3. Auditoria de `PolynomialMutation` no jMetal 7.8

Artefato inspecionado: `~/.m2/repository/org/uma/jmetal/jmetal-core/7.8/jmetal-core-7.8.jar`  
Classe: `org.uma.jmetal.operator.mutation.impl.PolynomialMutation`

### 3.1. Campos estáticos e de instância (bytecode)

```text
private static final double DEFAULT_PROBABILITY = 0.01d;
private static final double DEFAULT_DISTRIBUTION_INDEX = 20.0d;
private double distributionIndex;
private double mutationProbability;
private RepairDoubleSolution solutionRepair;
private RandomGenerator<Double> randomGenerator;
```

### 3.2. Todos os construtores disponíveis no jMetal 7.8

A inspeção via `javap -c -p org.uma.jmetal.operator.mutation.impl.PolynomialMutation` comprova a existência de **exatamente 7 construtores**:

| # | Assinatura do Construtor | Comportamento em Bytecode |
|---|---|---|
| 1 | `public PolynomialMutation()` | Delega para `this(0.01d, 20.0d)`. |
| 2 | `public PolynomialMutation(double mutationProbability)` | Delega para `this(mutationProbability, 20.0d)`. |
| 3 | `public PolynomialMutation(DoubleProblem problem, double distributionIndex, RandomGenerator<Double> randomGenerator)` | Calcula `1.0 / (double) problem.numberOfVariables()` (`dconst_1`, `invokeinterface DoubleProblem.numberOfVariables:()I`, `i2d`, `ddiv`), delega para `this(1.0 / n, distributionIndex)` e atribui `this.randomGenerator = randomGenerator`. |
| 4 | `public PolynomialMutation(double mutationProbability, double distributionIndex)` | Delega para `this(mutationProbability, distributionIndex, new RepairDoubleSolutionWithBoundValue())`. |
| 5 | `public PolynomialMutation(double mutationProbability, double distributionIndex, RandomGenerator<Double> randomGenerator)` | Delega para `this(mutationProbability, distributionIndex, new RepairDoubleSolutionWithBoundValue(), randomGenerator)`. |
| 6 | `public PolynomialMutation(double mutationProbability, double distributionIndex, RepairDoubleSolution solutionRepair)` | Delega para o construtor (7) com `() -> JMetalRandom.getInstance().nextDouble()`. |
| 7 | `public PolynomialMutation(double mutationProbability, double distributionIndex, RepairDoubleSolution solutionRepair, RandomGenerator<Double> randomGenerator)` | Construtor canônico: executa `Check.that(distributionIndex >= 0.0d, "Distribution index is negative: " + distributionIndex)`, `Check.probabilityIsValid(mutationProbability)`, e armazena `mutationProbability`, `distributionIndex`, `solutionRepair` e `randomGenerator`. |

### 3.3. Getters e Setters

- `public double mutationProbability()` — retorna `this.mutationProbability` (observação de API do jMetal 7.8: este método não possui prefixo `get`).
- `public double getDistributionIndex()` — retorna `this.distributionIndex`.
- `public PolynomialMutation setMutationProbability(double probability)` — atualiza `this.mutationProbability` e retorna `this`.
- `public PolynomialMutation setDistributionIndex(double distributionIndex)` — atualiza `this.distributionIndex` e retorna `this`.

### 3.4. Análise da chamada histórica mencionada em `tasks/task6.md` vs `WFGStudy.java`

O texto de contexto da tarefa `tasks/task6.md` menciona a expressão `new PolynomialMutation(1.0, problem.numberOfVariables(), 20.0)` e questiona se ela aplica probabilidade $1/n$ ou $1.0$. A auditoria de bytecode esclarece três fatos fundamentais:

1. **Inexistência de construtor `(double, int, double)` no jMetal 7.8**:
   Não existe nenhum construtor `(double, int, double)` nem `(double, double, double)` em `PolynomialMutation`. Os únicos construtores de 3 argumentos são:
   - `(DoubleProblem, double, RandomGenerator<Double>)`
   - `(double, double, RandomGenerator<Double>)`
   - `(double, double, RepairDoubleSolution)`
   Portanto, `new PolynomialMutation(1.0, problem.numberOfVariables(), 20.0)` sequer compila contra `jmetal-core:7.8` (a expressão `1.0, problem.numberOfVariables()` no texto da tarefa representa uma transcrição textual sem a barra `/` da expressão real `1.0 / problem.numberOfVariables()`).

2. **`PolynomialMutation(double, double)` NÃO divide internamente por `numberOfVariables()`**:
   No construtor `PolynomialMutation(double mutationProbability, double distributionIndex)`, o primeiro argumento é gravado diretamente em `this.mutationProbability` após `Check.probabilityIsValid(mutationProbability)`.
   - Se fosse invocado `new PolynomialMutation(1.0, 20.0)`, a probabilidade efetiva por variável seria `1.0` (**100% das variáveis de decisão seriam mutadas em todo indivíduo**), descaracterizando completamente a mutação polinomial padrão.
   - Como `WFGStudy.java` (linha 263) invoca `new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)`, a divisão `1.0 / n` é calculada em ponto flutuante (`double`) antes da chamada do construtor `(double, double)`, resultando exatamente em:
     - $\text{mutationProbability} = \frac{1}{n}$ (média esperada de 1 variável mutada por solução);
     - $\text{distributionIndex} = 20.0$.

3. **Equivalência com o construtor baseado em `DoubleProblem`**:
   O construtor #3 (`PolynomialMutation(DoubleProblem problem, double distributionIndex, RandomGenerator<Double> randomGenerator)`) executa no bytecode exatamente a mesma divisão:
   ```text
   0: aload_0
   1: dconst_1
   2: aload_1
   3: invokeinterface #17,  1  // InterfaceMethod org/uma/jmetal/problem/doubleproblem/DoubleProblem.numberOfVariables:()I
   8: i2d
   9: ddiv
   10: dload_2
   11: invokespecial #7         // Method "<init>":(DD)V
   ```
   Isso confirma que a convenção oficial do próprio jMetal 7.8 para probabilidade baseada no problema é `1.0 / (double) problem.numberOfVariables()`.

### 3.5. Semântica interna de `doMutation(DoubleSolution)` e reparo de limites

O método `execute(DoubleSolution solution)` verifica `Check.notNull(solution)`, chama `doMutation(solution)` e retorna a própria referência mutada in-place.

A decompilação de `doMutation(DoubleSolution)` comprova o seguinte fluxo exato para cada variável $i \in \{0, \dots, n - 1\}$:

1. **Teste de probabilidade por variável** (offsets 2–37):
   - Para cada índice $i$ (`0 <= i < solution.variables().size()`), sorteia $r_1 = \text{randomGenerator.getRandomValue()} \in [0, 1)$.
   - Compara `r1 <= mutationProbability` (`dcmpg`, `ifgt 314`).
   - Somente quando $r_1 \le p_m$ a variável $i$ sofre mutação.

2. **Leitura de valor e limites** (offsets 40–109):
   - $y = \text{solution.variables().get}(i)$
   - $\text{bounds} = \text{solution.getBounds}(i)$
   - $y_l = \text{bounds.getLowerBound()}$, $y_u = \text{bounds.getUpperBound()}$
   - Caso degenerado: se $y_l == y_u$ (`dcmpl`, `ifne 112`), define $y = y_l$ e pula o cálculo polinomial.

3. **Cálculo da perturbação polinomial de Deb** (offsets 112–280):
   - $\Delta_1 = \frac{y - y_l}{y_u - y_l}$, $\Delta_2 = \frac{y_u - y}{y_u - y_l}$
   - Sorteia $\text{rnd} = \text{randomGenerator.getRandomValue()}$
   - $\text{mutPow} = \frac{1.0}{\eta_m + 1.0}$ onde $\eta_m = \text{distributionIndex} = 20.0$
   - Se $\text{rnd} \le 0.5$:
     $$xy = 1.0 - \Delta_1, \quad \text{val} = 2.0 \cdot \text{rnd} + (1.0 - 2.0 \cdot \text{rnd}) \cdot xy^{\eta_m + 1}, \quad \Delta_q = \text{val}^{\text{mutPow}} - 1.0$$
   - Se $\text{rnd} > 0.5$:
     $$xy = 1.0 - \Delta_2, \quad \text{val} = 2.0 \cdot (1.0 - \text{rnd}) + 2.0 \cdot (\text{rnd} - 0.5) \cdot xy^{\eta_m + 1}, \quad \Delta_q = 1.0 - \text{val}^{\text{mutPow}}$$
   - Atualiza $y \leftarrow y + \Delta_q \cdot (y_u - y_l)$.

4. **Reparo de limites de caixa (`RepairDoubleSolutionWithBoundValue`)** (offsets 282–311):
   - Invoca `y = solutionRepair.repairSolutionVariableValue(y, yl, yu)` e grava `solution.variables().set(i, y)`.
   - A decompilação de `org.uma.jmetal.solution.doublesolution.repairsolution.impl.RepairDoubleSolutionWithBoundValue` confirma que o reparo padrão:
     - Valida `Check.that(lowerBound < upperBound, ...)`
     - Se `value < lowerBound`, retorna `lowerBound`;
     - Se `value > upperBound`, retorna `upperBound`;
     - Caso contrário, retorna `value`.
   - **Nota sobre restrições**: `PolynomialMutation` trata exclusivamente restrições de caixa (limites inferior e superior de cada variável de decisão) via `solutionRepair`. Ele não avalia funções objetivo nem restrições de desigualdade do problema.

---

## 4. Auditoria de `SBXCrossover` no jMetal 7.8

Artefato inspecionado: `~/.m2/repository/org/uma/jmetal/jmetal-core/7.8/jmetal-core-7.8.jar`  
Classe: `org.uma.jmetal.operator.crossover.impl.SBXCrossover`

### 4.1. Constantes e Campos

```text
private static final double DEFAULT_DISTRIBUTION_INDEX = 20.0d;
private static final double EPS = 1.0E-14d;
private double distributionIndex;
private double crossoverProbability;
private RepairDoubleSolution solutionRepair;
private RandomGenerator<Double> randomGenerator;
```

### 4.2. Construtores disponíveis no jMetal 7.8

A inspeção via `javap -c -p org.uma.jmetal.operator.crossover.impl.SBXCrossover` comprova **5 construtores**:

| # | Assinatura do Construtor | Comportamento em Bytecode |
|---|---|---|
| 1 | `public SBXCrossover(double crossoverProbability)` | Delega para `this(crossoverProbability, 20.0d)`. |
| 2 | `public SBXCrossover(double crossoverProbability, double distributionIndex)` | Delega para `this(crossoverProbability, distributionIndex, new RepairDoubleSolutionWithBoundValue())`. |
| 3 | `public SBXCrossover(double crossoverProbability, double distributionIndex, RandomGenerator<Double> randomGenerator)` | Delega para `this(crossoverProbability, distributionIndex, new RepairDoubleSolutionWithBoundValue(), randomGenerator)`. |
| 4 | `public SBXCrossover(double crossoverProbability, double distributionIndex, RepairDoubleSolution solutionRepair)` | Delega para o construtor (5) com `() -> JMetalRandom.getInstance().nextDouble()`. |
| 5 | `public SBXCrossover(double crossoverProbability, double distributionIndex, RepairDoubleSolution solutionRepair, RandomGenerator<Double> randomGenerator)` | Construtor canônico: valida `Check.probabilityIsValid(crossoverProbability)` e `Check.that(distributionIndex >= 0.0, ...)`, atribuindo os 4 campos. |

### 4.3. Getters, Setters e Metadados de Aridade

- `public double crossoverProbability()` — retorna `this.crossoverProbability` (`1.0` em `WFGStudy`).
- `public double distributionIndex()` — retorna `this.distributionIndex` (`30.0` em `WFGStudy`).
- `public int numberOfRequiredParents()` — retorna `2`.
- `public int numberOfGeneratedChildren()` — retorna `2`.

### 4.4. Semântica interna de `doCrossover`

1. Cria cópias dos dois pais: `offspring.add((DoubleSolution) parent1.copy())` e `offspring.add((DoubleSolution) parent2.copy())`.
2. Sorteia `randomGenerator.getRandomValue() <= probability`. Como `probability = 1.0` em `WFGStudy`, o bloco de cruzamento é sempre executado para cada par de pais.
3. Para cada variável $i \in \{0, \dots, n-1\}$:
   - Obtém $x_1$ e $x_2$ dos pais e verifica se `randomGenerator.getRandomValue() <= 0.5` (probabilidade de 50% de cruzar cada variável individualmente).
   - Verifica se $|x_1 - x_2| > 10^{-14}$ (`EPS`). Se os valores nos dois pais forem praticamente idênticos, mantém os valores originais na variável $i$.
   - Caso $|x_1 - x_2| > 10^{-14}$, define $y_1 = \min(x_1, x_2)$ e $y_2 = \max(x_1, x_2)$, lê os limites $[y_l, y_u]$ de `parent1.getBounds(i)` e calcula os dois filhos $c_1, c_2$ usando o índice $\eta_c = 30.0$.
   - Caso $c_1$ ou $c_2$ resulte em `Double.isNaN` (por exemplo, quando $y_1$ ou $y_2$ coincide com o limite e a base da potência fica negativa por imprecisão de ponto flutuante), o código emite um `JMetalLogger.logger.warning` e substitui apenas o valor `NaN` pelo limite correspondente (`yl` ou `yu`). Em seguida, aplica `solutionRepair.repairSolutionVariableValue(c1, yl, yu)` e `solutionRepair.repairSolutionVariableValue(c2, yl, yu)`.
   - Com probabilidade `0.5` (`randomGenerator.getRandomValue() <= 0.5`), atribui $(c_1 \to \text{filho}_0, c_2 \to \text{filho}_1)$ ou $(c_2 \to \text{filho}_0, c_1 \to \text{filho}_1)$.

---

## 5. Auditoria de `DifferentialEvolutionCrossover` e `BinaryTournamentSelection`

### 5.1. `DifferentialEvolutionCrossover` (`jmetal-core-7.8.jar`)
- Construtor padrão utilizado em `WFGStudy.java:264`: `new DifferentialEvolutionCrossover()`
- Delega para `this(0.5d, 0.5d, DE_VARIANT.RAND_1_BIN)` com `DEFAULT_CR = 0.5`, `DEFAULT_F = 0.5` e variante `RAND_1_BIN`.
- Exige 3 pais (`numberOfRequiredParents() = 3`) + `currentSolution` e gera 1 filho (`numberOfGeneratedChildren() = 1`), reparando variáveis com `RepairDoubleSolutionWithBoundValue`.
- Em `WFGStudy.java`, esta instância `de` é utilizada exclusivamente no método auxiliar `buildMOEADDRA` (algoritmo complementar fora do conjunto principal de 6 algoritmos).

### 5.2. `BinaryTournamentSelection` (`jmetal-core-7.8.jar`)
- Estende `NaryTournamentSelection<S>` com `numberOfSolutionsToBeReturned = 2`.
- Construtor padrão: `new BinaryTournamentSelection<>()` usa `new DominanceWithConstraintsComparator<>()`.
- Construtor parametrizado: `new BinaryTournamentSelection<>(Comparator<S> comparator)`.

---

## 6. Matriz de Aplicação dos Operadores por Algoritmo em `WFGStudy`

A inspeção conjunta de [`WFGStudy.java`](../src/main/java/br/usp/lti/newmoea/study/WFGStudy.java), [`FDEABuilder.java`](../src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEABuilder.java), [`FDEA.java`](../src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEA.java) e dos builders/algoritmos em `jmetal-algorithm-7.8.jar` e `jmetal-component-7.8.jar` confirma como cada algoritmo consome os operadores:

### 6.1. Algoritmos Principais do Experimento (ativos em `configureAlgorithmList`)

| Algoritmo | Método em `WFGStudy` | Operador de Seleção | Operador de Crossover | Operador de Mutação | Consumo de Filhos por Reprodução (Bytecode/Fonte) |
|---|---|---|---|---|---|
| **NSGA-III** | `buildNSGAIII` | `BinaryTournamentSelection<>(RankingAndCrowdingDistanceComparator)` | `SBXCrossover(1.0, 30.0)` | `PolynomialMutation(1.0 / n, 20.0)` | Em `NSGAIII.reproduction`: agrupa pais 2 a 2, executa `crossoverOperator.execute(parents)` e aplica `mutationOperator.execute(...)` nos **2 filhos** gerados, adicionando ambos à prole. |
| **MOEA/DD** | `buildMOEADD` | Seleção interna por vizinhança/mating (`matingSelection` no `MOEADD`) | `SBXCrossover(1.0, 30.0)` | `PolynomialMutation(1.0 / n, 20.0)` | Em `MOEADD.run` (offsets 405–445): executa `crossover.execute(parents)`, extrai **1 filho** (`children.get(0)`), aplica `mutation.execute(child)` e avalia 1 solução por iteração (`evaluations++`). |
| **FDEA** | `buildFDEA` | `BinaryTournamentSelection<>(RankingAndCrowdingDistanceComparator)` | `SBXCrossover(1.0, 30.0)` | `PolynomialMutation(1.0 / n, 20.0)` | Em `FDEA.reproduction` (`FDEA.java:151-156`): para cada par selecionado, executa `crossoverOperator.execute(parents)`, aplica `mutationOperator.execute(offspring.get(0))` e adiciona **1 filho** (`offspring.get(0)`) até completar `maxPopulationSize`. |
| **RVEA** | `buildRVEA` | `NaryTournamentSelection` interno do `RVEABuilder` com `CrossoverAndMutationVariation` (`offspringPopulationSize = populationSize`) | `SBXCrossover(1.0, 30.0)` | `PolynomialMutation(1.0 / n, 20.0)` | Em `CrossoverAndMutationVariation.variate`: executa `crossover.execute(parents)` e aplica `mutation.execute(...)` nos **2 filhos** (`numberOfGeneratedChildren = 2`). |
| **AGE-MOEA** | `buildAGEMOEA` | `BinaryTournamentSelection<>(RankingAndSurvivalScoreComparator)` | `SBXCrossover(1.0, 30.0)` | `PolynomialMutation(1.0 / n, 20.0)` | Em `AGEMOEA.reproduction`: executa `crossoverOperator.execute(parents)` e aplica `mutationOperator.execute(...)` nos **2 filhos** gerados. |
| **AGE-MOEA-II** | `buildAGE2` | `BinaryTournamentSelection<>(RankingAndSurvivalScoreComparator)` | `SBXCrossover(1.0, 30.0)` | `PolynomialMutation(1.0 / n, 20.0)` | Em `AGEMOEAII.reproduction`: herda `AGEMOEA.reproduction`, aplicando crossover e mutação nos **2 filhos** gerados. |

### 6.2. Métodos Auxiliares Complementares em `WFGStudy`

Os métodos auxiliares `buildNSGAII`, `buildSPEA2`, `buildESPEA`, `buidGWASFGA` (todos com `SBXCrossover(1.0, 30.0)` e `PolynomialMutation(1.0 / n, 20.0)`) e `buildMOEADDRA` (com `DifferentialEvolutionCrossover()` e `PolynomialMutation(1.0 / n, 20.0)`) também foram inspecionados em `WFGStudy.configureAlgorithmList` e são compatíveis com as assinaturas do jMetal 7.8.

---

## 7. Validação Automatizada (`OperatorAuditTest`)

Foi criada a suíte de testes [`OperatorAuditTest.java`](../src/test/java/br/usp/lti/newmoea/operator/OperatorAuditTest.java) validando:
1. **Assinaturas de construtores via reflexão**: confirma os 7 construtores de `PolynomialMutation` e os 5 construtores de `SBXCrossover` no jMetal 7.8, além de provar a inexistência de um construtor `(double, int, double)`.
2. **Parâmetros efetivos**: verifica que `new PolynomialMutation(1.0 / n, 20.0)` define `mutationProbability() == 1.0 / n` e `getDistributionIndex() == 20.0`, equivalendo ao construtor `new PolynomialMutation(problem, 20.0, randomGenerator)`.
3. **Frequência empírica de mutação**: demonstra estatisticamente sobre um problema com $n = 24$ variáveis (como na suíte WFG padrão com $k=4, l=20$) que `new PolynomialMutation(1.0 / n, 20.0)` altera em média $\approx 1/24 \approx 4{,}17\%$ das variáveis por indivíduo ($\approx 1$ variável por solução), enquanto `new PolynomialMutation(1.0, 20.0)` altera $100\%$ das variáveis.
4. **Respeito estrito aos limites (`[lowerBound, upperBound]`)**: verifica que `PolynomialMutation` e `SBXCrossover` mantêm todas as variáveis dentro dos limites de caixa em milhares de execuções, inclusive para soluções nos extremos dos limites e em limites degenerados ($y_l == y_u$).
5. **Integração leve com problemas de 2 e 3 objetivos**: executa ciclo curto de reprodução e algoritmos com os operadores auditados em problemas de 2 e 3 objetivos (`WFG1(2, 4, 2)` e `DTLZ2(12, 3)`), verificando geração de soluções válidas dentro dos limites.

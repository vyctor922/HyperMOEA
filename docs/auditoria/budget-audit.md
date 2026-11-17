# Relatório de Auditoria — Orçamento Real de Avaliações, Compatibilidade Populacional e Condições de Parada (T13)

- **Data da auditoria**: 09/11/2026
- **Tarefa**: [`tasks/task13.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task13.md)
- **Responsável**: Agente B (Evaluation Budget & Population Auditor)
- **Artefatos entregues**:
  - [`src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java)
  - [`src/test/java/br/usp/lti/newmoea/algorithm/EvaluationBudgetAuditTest.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/EvaluationBudgetAuditTest.java)
  - [`docs/auditoria/budget-audit.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/budget-audit.csv)
  - [`docs/auditoria/budget-audit.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/budget-audit.md)

---

## 1. Fundamentação e Distinção Conceitual

Conforme estabelecido em [`TASK_COORDINATOR.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/TASK_COORDINATOR.md) (§§ 2.5, 2.6 e 8.2) e [`tasks/task13.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task13.md), o custo computacional científico em otimização multiobjetivo evolutiva é determinado pelo **número real de chamadas à função objetivo** (`Problem.evaluate`), incluindo a avaliação da população inicial, filhos gerados e avaliados (mesmo que descartados na seleção ambiental) e eventuais lotes excedentes.

Para evitar qualquer confusão conceitual, esta auditoria distingue rigorosamente seis grandezas:

1. **$|\mathcal{R}(m)|$ (Cardinalidade das referências geométricas/pesos)**: número de pontos de referência (NSGA-III), direções unitárias (RVEA) ou vetores de peso (MOEA/DD) gerados pela grade simplex de Das & Dennis (1998) / Deb & Jain (2014): $\{100, 91, 210, 156, 275, 135\}$ para $m \in \{2, 3, 5, 8, 10, 15\}$.
2. **$N_{\text{config}}(m)$ (População solicitada)**: valor retornado por [`StaticParams.getPopulationSize`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L162-L179), idêntico a $|\mathcal{R}(m)| \in \{100, 91, 210, 156, 275, 135\}$.
3. **$N_{\text{alloc}}(\text{alg}, m)$ (População efetiva alocada internamente)**: tamanho real da lista `population` criada na inicialização e mantida a cada geração pelo algoritmo após arredondamentos internos do construtor ou do builder.
4. **$N_{\text{offspring}}(\text{alg}, m)$ (Filhos gerados e avaliados por geração)**: número de soluções filhas efetivamente submetidas a `Problem.evaluate` a cada ciclo evolutivo.
5. **$N_{\text{result}}(\text{alg}, m)$ (Cardinalidade de `algorithm.result()`)**: tamanho da lista retornada ao final da execução (que depende de o algoritmo filtrar ou não a frente não-dominada de posto 0 em `result()`).
6. **$B_{\text{target}}(m, \text{problem})$ vs. $B_{\text{real}}(\text{alg}, m, \text{problem})$**: orçamento alvo canônico $B_{\text{target}} = N_{\text{config}}(m) \times G_{\max}(m, \text{problem})$ versus a contagem física real de chamadas a `Problem.evaluate(solution)`.

---

## 2. Inspeção Forense em Bytecode dos 6 Algoritmos Principais (jMetal 7.8)

Todas as classes foram inspecionadas diretamente nos binários resolvidos pelo Maven (`jmetal-core-7.8.jar`, `jmetal-algorithm-7.8.jar`, `jmetal-component-7.8.jar`, `jmetal-problem-7.8.jar`) via `javap -c -p` e confrontadas com o código local:

### 2.1. `NSGA-III` (`org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII`)
- **Arredondamento populacional**: No construtor `NSGAIII(NSGAIIIBuilder)` (bytecode offsets `405..429`), após gerar `referencePoints.size()` pontos ($100, 91, 210, 156, 275, 135$), executa:
  ```java
  int popSize = this.referencePoints.size();
  while (popSize % 4 > 0) {
      popSize++;
  }
  setMaxPopulationSize(popSize);
  ```
  Logo, $N_{\text{alloc}}(\text{NSGA-III}, m) = 4 \lceil N_{\text{config}}(m)/4 \rceil \in \{100, \mathbf{92}, \mathbf{212}, 156, \mathbf{276}, \mathbf{136}\}$.
- **Nota sobre a validação em `WFGStudy.buildNSGAIII`**: [`WFGStudy.buildNSGAIII`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L352-L358) valida `populationSize == expectedPoints` **antes** de chamar `builder.build()`, mas não verifica `algorithm.getMaxPopulationSize()` após `build()`, que passa silenciosamente a $92, 212, 276, 136$ para $m \in \{3, 5, 10, 15\}$.
- **Ciclo de reprodução e parada**: Herda `AbstractEvolutionaryAlgorithm.run()`:
  1. `createInitialPopulation()` cria $N_{\text{alloc}}$ soluções;
  2. `evaluatePopulation(population)` avalia $N_{\text{alloc}}$ soluções via `evaluator.evaluate(...)`;
  3. `initProgress()` define `iterations = 1`;
  4. `while (!isStoppingConditionReached())` verifica `iterations >= maxIterations` (executa `maxIterations - 1` gerações evolutivas);
  5. Em cada geração, `NSGAIII.reproduction` executa um laço `for (int i = 0; i < maxPopulationSize; i += 2)` (offsets `14..176`), gerando e mutando os **2 filhos** de cada `SBXCrossover` e adicionando ambos a `offspringPopulation` (totalizando $N_{\text{alloc}}$ filhos avaliados por geração).
- **Total de avaliações reais no estado atual**:
  $$B_{\text{real}}(\text{NSGA-III}, m) = N_{\text{alloc}}(\text{NSGA-III}, m) \times G_{\max}(m, \text{problem})$$
- **Retorno `result()`**: Chama `SolutionListUtils.getNonDominatedSolutions(getPopulation())`, retornando apenas soluções não-dominadas ($1 \le N_{\text{result}} \le N_{\text{alloc}}$).

### 2.2. `AGE-MOEA` e `AGE-MOEA-II` (`org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEA` / `agemoeaii.AGEMOEAII`)
- **Arredondamento populacional**: Em [`WFGStudy.buildAGEMOEA`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L428-L438) e [`WFGStudy.buildAGE2`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L440-L450), o código incrementa localmente `if (populationSize % 2 != 0) populationSize++;` porque `AGEMOEA.reproduction` invoca `checkNumberOfParents(matingPopulation, 2)` (herdada de `AbstractGeneticAlgorithm`), que lança `JMetalException` se `matingPopulation.size() % 2 != 0`.
  Logo, $N_{\text{alloc}}(\text{AGE}, m) = 2 \lceil N_{\text{config}}(m)/2 \rceil \in \{100, \mathbf{92}, 210, 156, \mathbf{276}, \mathbf{136}\}$.
- **Ciclo de reprodução e parada**: Em `AGEMOEA.run()` (offsets `0..91`):
  1. Cria e avalia $N_{\text{alloc}}$ soluções iniciais;
  2. Executa `replacement(population, emptyList)` para atribuir `SurvivalScore` inicial (0 avaliações adicionais);
  3. `initProgress()` define `iterations = 1`;
  4. Enquanto `iterations < maxIterations` (`maxIterations - 1` gerações), seleciona $N_{\text{alloc}}$ pais, gera $N_{\text{alloc}}$ filhos em pares via SBX+PM, avalia todos os $N_{\text{alloc}}$ filhos e incrementa `iterations++`.
- **Total de avaliações reais no estado atual**:
  $$B_{\text{real}}(\text{AGE-MOEA / II}, m) = N_{\text{alloc}}(\text{AGE}, m) \times G_{\max}(m, \text{problem})$$
- **Retorno `result()`**: Chama `SolutionListUtils.getNonDominatedSolutions(getPopulation())` ($1 \le N_{\text{result}} \le N_{\text{alloc}}$).

### 2.3. `MOEA/DD` (`org.uma.jmetal.algorithm.multiobjective.moead.MOEADD`)
- **População interna**: Usa exatamente $N_{\text{alloc}} = N_{\text{config}}(m) \in \{100, 91, 210, 156, 275, 135\}$ associada aos vetores de peso $\Lambda$.
- **Ciclo de reprodução e parada**: Em `MOEADD.run()` (offsets `0..519`):
  1. `evaluations = 0`; `initPopulation()` avalia $N_{\text{config}}$ soluções iniciais (`evaluations = N_config`);
  2. Entra em um laço **`do { ... } while (evaluations < maxEvaluations)`** (offsets `339..516`), dentro do qual executa um laço `for (int i = 0; i < populationSize; i++)` (offsets `354..505`) sobre uma permutação aleatória dos $N_{\text{config}}$ subproblemas;
  3. Para cada subproblema $i$, `matingSelection` escolhe 2 pais, `SBXCrossover` gera 2 filhos, mas o algoritmo **utiliza, muta e avalia apenas `children.get(0)`** (`evaluations++`, descartando `children.get(1)` **antes** de avaliar);
  4. **Achado Crítico de Fronteira**: O teste `if (evaluations < maxEvaluations)` (offset `516`) ocorre **somente ao final do laço `for` de $N_{\text{config}}$ subproblemas** e dentro de um `do-while`! Consequentemente:
     - Se $B_{\text{req}} = N_{\text{config}}$ (apenas população inicial), o `do-while` executa obrigatoriamente uma geração inteira extra, realizando **$2 N_{\text{config}}$ avaliações** (100% de excedente em $B = N$);
     - Para qualquer $B_{\text{req}} > N_{\text{config}}$ que não seja múltiplo de $N_{\text{config}}$ (como $B = N+1$ ou $B = 2N-1$), avalia sempre o lote completo de $N_{\text{config}}$ subproblemas, resultando em:
       $$B_{\text{real}}(\text{MOEA/DD}, m) = \max\!\left(2 N_{\text{config}}, \left\lceil \frac{B_{\text{req}}}{N_{\text{config}}} \right\rceil N_{\text{config}}\right)$$
     - Quando $B_{\text{req}} = G_{\max} \times N_{\text{config}}$ com $G_{\max} \ge 2$ (caso de todos os problemas em `WFGStudy`), $B_{\text{real}} = B_{\text{target}}$ exato.
- **Retorno `result()`**: Herda `AbstractMOEAD.result()`, que retorna a lista `population` completa de tamanho $N_{\text{result}} = N_{\text{config}}$ (sem filtragem de dominância).

### 2.4. `FDEA` ([`br.usp.lti.newmoea.algorithm.fdea.FDEA`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEA.java))
- **População interna**: Usa exatamente $N_{\text{alloc}} = N_{\text{config}}(m) \in \{100, 91, 210, 156, 275, 135\}$ (sem exigir paridade).
- **Verificação contra o repositório original (`Fuzzy-Decomposition-Based-Evolutionary-Algorithm-RepositoryGITHUB/FDEA/src/jmetal/metaheuristics/fdea/FDEA.java`)**:
  - Confirmado nas linhas 203–221 do `FDEA.java` original de Liu et al. que `reproduction` executava `for (int i = 0; i < populationSize_; i++)`, gerava `offSpring = crossover_.execute(parents)`, mutava e avaliava **apenas `offSpring[0]`** (1 avaliação de `problem_.evaluate` por iteração do laço `for`, descartando `offSpring[1]` sem avaliá-lo).
  - Na implementação local [`FDEA.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEA.java#L136-L159), `initPopulation()` avalia $N_{\text{config}}$ soluções (`evaluations = N_config`), e o laço `while (evaluations < maxEvaluations && generations < maxGenerations)` chama `reproduction()`, cujo laço interno `while (offspring.size() < populationSize && evaluations < maxEvaluations)` verifica `evaluations < maxEvaluations` **a cada filho individual**.
- **Total de avaliações reais**:
  $$B_{\text{real}}(\text{FDEA}, m) = \max\!\left(N_{\text{config}}, \min(B_{\text{req}}, G_{\text{req}} \times N_{\text{config}})\right)$$
  Portanto, `FDEA` é o único algoritmo que nativamente respeita $B \in \{N, N+1, 2N-1, 2N\}$ com precisão de 1 avaliação!
- **Retorno `result()`**: Calcula `FastNonDominatedSortRanking` sobre `population` e retorna `getSubFront(0)` ($1 \le N_{\text{result}} \le N_{\text{config}}$).

### 2.5. `RVEA` (`org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder` / `EvolutionaryAlgorithm`)
- **População interna e geração de filhos**: `RVEABuilder` instancia `RandomSolutionsCreation(problem, N_config)` e `CrossoverAndMutationVariation(N_config, crossover, mutation)`.
  - Em `CrossoverAndMutationVariation` (bytecode offsets `19..41` e `32..186`), `matingPoolSize = 2 * ceil(N_config / 2)`. Durante `variate`, quando $N_{\text{config}}$ é ímpar ($91, 275, 135$), o último cruzamento gera 2 filhos, mas o laço interno verifica `if (offspringPopulation.size() == offspringPopulationSize) break;` (offset `168`) **antes** de avaliar! Assim, o filho excedente é descartado antes da avaliação, e `SequentialEvaluation.evaluate` avalia exatamente $N_{\text{config}}$ filhos por geração.
- **Condição de parada e `maxGenerations` do APD**:
  - `RVEABuilder.build()` calcula `maxGenerations = Math.max(1, (int) Math.ceil((double)(maxEvaluations - populationSize) / offspringPopulationSize))` para parametrizar a penalidade angular $\text{APD}$ (`(currentGeneration / maxGenerations)^alpha`) e `adaptationFrequency = Math.max(1, (int) Math.ceil(maxGenerations * fr))`.
  - Em `EvolutionaryAlgorithm.run()` (offsets `37..122`), `initProgress()` define `evaluations = N_config`. O laço `while (!termination.isMet(attributes))` verifica `evaluations >= maxEvaluations` **apenas no início de cada geração**.
  - Consequentemente:
    - Para $B_{\text{req}} = N_{\text{config}}$, `termination.isMet` retorna `true` imediatamente após `initProgress()`, realizando exatamente **$N_{\text{config}}$ avaliações**;
    - Para $B_{\text{req}} \in \{N_{\text{config}}+1, 2N_{\text{config}}-1\}$, executa 1 geração completa de $N_{\text{config}}$ filhos, totalizando **$2 N_{\text{config}}$ avaliações**:
      $$B_{\text{real}}(\text{RVEA}, m) = \max\!\left(N_{\text{config}}, \left\lceil \frac{B_{\text{req}}}{N_{\text{config}}} \right\rceil N_{\text{config}}\right)$$
- **Retorno `result()`**: Retorna `population` selecionada por `RVEAEnvironmentalSelection.selectByApd`. Como cada sub-região ativa seleciona no máximo 1 solução, $1 \le N_{\text{result}} \le N_{\text{config}}$.

---

## 3. Tabela Consolidada de Gerações ($G_{\max}$) e Orçamentos Alvo ($B_{\text{target}}$) em `StaticParams.java`

### 3.1. Auditoria de `DTLZ` e `WFG` em `StaticParams.getMaxGenerations(m, problem)` e `WFGStudy.java`
1. **Número de variáveis de decisão ($n$) em `WFGStudy.addToProblemList`**:
   - Para `WFG1`–`WFG9`: `new WFGk(k, L, m)` com $k = 2(m-1)$ variáveis de posição e $L = 20$ variáveis de distância $\implies n = 2(m-1) + 20$.
   - Para `DTLZ1`–`DTLZ7`: `WFGStudy.java` (linhas 245–251 e 267–273) instancia `new DTLZp(L, m)` passando a constante `L = 20` como o primeiro argumento do construtor `DTLZp(Integer numberOfVariables, Integer numberOfObjectives)`. Conforme comprovado no bytecode de `DTLZ1`, `DTLZ2` e `DTLZ5` (`javap -c`), o primeiro argumento do construtor DTLZ no jMetal 7.8 é o **número total de variáveis $n = 20$** (e **não** o parâmetro $k$), de modo que internamente cada classe DTLZ calcula $k = n - m + 1 = 21 - m$.
2. **Anomalia em `StaticParams.getMaxGenerations` para `DTLZ5` e `DTLZ6`**:
   - Nas linhas 18–156 de [`StaticParams.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L15-L160), os blocos `switch (problem)` listam explicitamente `DTLZ1`, `DTLZ2`, `DTLZ3`, `DTLZ4`, `DTLZ7` e `WFG1..WFG9`, mas **omitem `DTLZ5` e `DTLZ6`**!
   - Em $m \in \{2, 3, 5, 8\}$, `DTLZ5` e `DTLZ6` caem no `default -> return 1000;` do `switch (problem)` interno.
   - Em $m \in \{10, 15\}$, os blocos `switch (problem)` internos **não possuem ramo `default`**, fazendo a execução cair(`fall-through`) para o `default: return 1000;` externo (linha 158)! Isso faz com que `DTLZ5` e `DTLZ6` recebam $G_{\max} = 1000$ em todas as dimensões ($m=2$ a $m=15$), inclusive recebendo 4 vezes mais gerações que `DTLZ2` em $m=2,3$ ($1000$ vs. $250$).

| Dimensão $m$ | $N_{\text{config}}(m)$ | Família / Problema | $G_{\max}$ Atual (`StaticParams`) | $B_{\text{target}} = N_{\text{config}} \times G_{\max}$ | $B_{\text{real}}$ Atual (`MOEA/DD`, `FDEA`, `RVEA`) | $B_{\text{real}}$ Atual (`AGE-MOEA`, `AGE-MOEA-II`) | $B_{\text{real}}$ Atual (`NSGA-III`) |
|---:|---:|---|---:|---:|---:|---:|---:|
| **2** | **100** | `WFG1`–`WFG9`, `DTLZ2` | 250 | 25.000 | 25.000 | 25.000 | 25.000 |
| **2** | **100** | `DTLZ1` | 400 | 40.000 | 40.000 | 40.000 | 40.000 |
| **2** | **100** | `DTLZ4` | 600 | 60.000 | 60.000 | 60.000 | 60.000 |
| **2** | **100** | `DTLZ3`, `DTLZ7` (e `DTLZ5`/`6` via default) | 1.000 | 100.000 | 100.000 | 100.000 | 100.000 |
| **3** | **91** | `DTLZ2` | 250 | 22.750 | 22.750 | **23.000** (+250) | **23.000** (+250) |
| **3** | **91** | `WFG1`–`WFG9`, `DTLZ1` | 400 | 36.400 | 36.400 | **36.800** (+400) | **36.800** (+400) |
| **3** | **91** | `DTLZ4` | 600 | 54.600 | 54.600 | **55.200** (+600) | **55.200** (+600) |
| **3** | **91** | `DTLZ3`, `DTLZ7` (e `DTLZ5`/`6` via default) | 1.000 | 91.000 | 91.000 | **92.000** (+1.000) | **92.000** (+1.000) |
| **5** | **210** | `DTLZ2` | 350 | 73.500 | 73.500 | 73.500 | **74.200** (+700) |
| **5** | **210** | `DTLZ1` | 600 | 126.000 | 126.000 | 126.000 | **127.200** (+1.200) |
| **5** | **210** | `WFG1`–`WFG9` | 750 | 157.500 | 157.500 | 157.500 | **159.000** (+1.500) |
| **5** | **210** | `DTLZ3`, `DTLZ4`, `DTLZ7` (e `DTLZ5`/`6` default) | 1.000 | 210.000 | 210.000 | 210.000 | **212.000** (+2.000) |
| **8** | **156** | `DTLZ2` | 500 | 78.000 | 78.000 | 78.000 | 78.000 |
| **8** | **156** | `DTLZ1` | 750 | 117.000 | 117.000 | 117.000 | 117.000 |
| **8** | **156** | `DTLZ3`, `DTLZ7` (e `DTLZ5`/`6` default) | 1.000 | 156.000 | 156.000 | 156.000 | 156.000 |
| **8** | **156** | `DTLZ4` | 1.250 | 195.000 | 195.000 | 195.000 | 195.000 |
| **8** | **156** | `WFG1`–`WFG9` | 1.500 | 234.000 | 234.000 | 234.000 | 234.000 |
| **10** | **275** | `DTLZ2` | 750 | 206.250 | 206.250 | **207.000** (+750) | **207.000** (+750) |
| **10** | **275** | `DTLZ1` (e `DTLZ5`/`6` fall-through default) | 1.000 | 275.000 | 275.000 | **276.000** (+1.000) | **276.000** (+1.000) |
| **10** | **275** | `DTLZ3`, `DTLZ7` | 1.500 | 412.500 | 412.500 | **414.000** (+1.500) | **414.000** (+1.500) |
| **10** | **275** | `WFG1`–`WFG9`, `DTLZ4` | 2.000 | 550.000 | 550.000 | **552.000** (+2.000) | **552.000** (+2.000) |
| **15** | **135** | `DTLZ2` (e `DTLZ5`/`6` fall-through default) | 1.000 | 135.000 | 135.000 | **136.000** (+1.000) | **136.000** (+1.000) |
| **15** | **135** | `DTLZ1` | 1.500 | 202.500 | 202.500 | **204.000** (+1.500) | **204.000** (+1.500) |
| **15** | **135** | `DTLZ3`, `DTLZ7` | 2.000 | 270.000 | 270.000 | **272.000** (+2.000) | **272.000** (+2.000) |
| **15** | **135** | `WFG1`–`WFG9`, `DTLZ4` | 3.000 | 405.000 | 405.000 | **408.000** (+3.000) | **408.000** (+3.000) |

---

## 4. Auditoria Completa por Algoritmo e Dimensão $m \in \{2, 3, 5, 8, 10, 15\}$ (Família WFG e Limites de Fronteira)

A tabela abaixo reproduz os dados exatos de [`docs/auditoria/budget-audit.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/budget-audit.csv), incluindo os testes de fronteira solicitados em [`tasks/task13.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task13.md) ($B \in \{N, N+1, 2N-1, 2N\}$):

| Algoritmo | $m$ | $N_{\text{config}}$ | $N_{\text{alloc}}$ Atual | $G_{\max}$ (WFG) | $B_{\text{target}}$ (WFG) | $B_{\text{real}}$ Atual (WFG) | $\Delta B$ (WFG) | Desvio (%) | Fronteira $B=N$ ($G=1$) | Fronteira $B=N+1$ ($G=2$) | Fronteira $B=2N-1$ ($G=2$) | Fronteira $B=2N$ ($G=2$) | Mecanismo Nativo de Parada |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `NSGA-III` | 2 | 100 | 100 | 250 | 25.000 | 25.000 | 0 | 0,0000% | 100 | 200 | 200 | 200 | `iterations >= maxIterations` (`init=1`, lote $4\lceil N/4\rceil$) |
| `MOEA/DD` | 2 | 100 | 100 | 250 | 25.000 | 25.000 | 0 | 0,0000% | **200** | 200 | 200 | 200 | `do { for(N) eval++; } while (eval < maxEvals)` |
| `FDEA` | 2 | 100 | 100 | 250 | 25.000 | 25.000 | 0 | 0,0000% | 100 | **101** | **199** | 200 | `while (eval < maxEvals && gen < maxGen)` (por filho) |
| `RVEA` | 2 | 100 | 100 | 250 | 25.000 | 25.000 | 0 | 0,0000% | 100 | 200 | 200 | 200 | `TerminationByEvaluations` verificado após lote $N$ |
| `AGE-MOEA` | 2 | 100 | 100 | 250 | 25.000 | 25.000 | 0 | 0,0000% | 100 | 200 | 200 | 200 | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `AGE-MOEA-II` | 2 | 100 | 100 | 250 | 25.000 | 25.000 | 0 | 0,0000% | 100 | 200 | 200 | 200 | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `NSGA-III` | 3 | 91 | **92** | 400 | 36.400 | **36.800** | **+400** | **+1,0989%** | **92** | **184** | **184** | **184** | `iterations >= maxIterations` (`init=1`, lote $4\lceil N/4\rceil$) |
| `MOEA/DD` | 3 | 91 | 91 | 400 | 36.400 | 36.400 | 0 | 0,0000% | **182** | 182 | 182 | 182 | `do { for(N) eval++; } while (eval < maxEvals)` |
| `FDEA` | 3 | 91 | 91 | 400 | 36.400 | 36.400 | 0 | 0,0000% | 91 | **92** | **181** | 182 | `while (eval < maxEvals && gen < maxGen)` (por filho) |
| `RVEA` | 3 | 91 | 91 | 400 | 36.400 | 36.400 | 0 | 0,0000% | 91 | 182 | 182 | 182 | `TerminationByEvaluations` verificado após lote $N$ |
| `AGE-MOEA` | 3 | 91 | **92** | 400 | 36.400 | **36.800** | **+400** | **+1,0989%** | **92** | **184** | **184** | **184** | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `AGE-MOEA-II` | 3 | 91 | **92** | 400 | 36.400 | **36.800** | **+400** | **+1,0989%** | **92** | **184** | **184** | **184** | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `NSGA-III` | 5 | 210 | **212** | 750 | 157.500 | **159.000** | **+1.500** | **+0,9524%** | **212** | **424** | **424** | **424** | `iterations >= maxIterations` (`init=1`, lote $4\lceil N/4\rceil$) |
| `MOEA/DD` | 5 | 210 | 210 | 750 | 157.500 | 157.500 | 0 | 0,0000% | **420** | 420 | 420 | 420 | `do { for(N) eval++; } while (eval < maxEvals)` |
| `FDEA` | 5 | 210 | 210 | 750 | 157.500 | 157.500 | 0 | 0,0000% | 210 | **211** | **419** | 420 | `while (eval < maxEvals && gen < maxGen)` (por filho) |
| `RVEA` | 5 | 210 | 210 | 750 | 157.500 | 157.500 | 0 | 0,0000% | 210 | 420 | 420 | 420 | `TerminationByEvaluations` verificado após lote $N$ |
| `AGE-MOEA` | 5 | 210 | 210 | 750 | 157.500 | 157.500 | 0 | 0,0000% | 210 | 420 | 420 | 420 | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `AGE-MOEA-II` | 5 | 210 | 210 | 750 | 157.500 | 157.500 | 0 | 0,0000% | 210 | 420 | 420 | 420 | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `NSGA-III` | 8 | 156 | 156 | 1.500 | 234.000 | 234.000 | 0 | 0,0000% | 156 | 312 | 312 | 312 | `iterations >= maxIterations` (`init=1`, lote $4\lceil N/4\rceil$) |
| `MOEA/DD` | 8 | 156 | 156 | 1.500 | 234.000 | 234.000 | 0 | 0,0000% | **312** | 312 | 312 | 312 | `do { for(N) eval++; } while (eval < maxEvals)` |
| `FDEA` | 8 | 156 | 156 | 1.500 | 234.000 | 234.000 | 0 | 0,0000% | 156 | **157** | **311** | 312 | `while (eval < maxEvals && gen < maxGen)` (por filho) |
| `RVEA` | 8 | 156 | 156 | 1.500 | 234.000 | 234.000 | 0 | 0,0000% | 156 | 312 | 312 | 312 | `TerminationByEvaluations` verificado após lote $N$ |
| `AGE-MOEA` | 8 | 156 | 156 | 1.500 | 234.000 | 234.000 | 0 | 0,0000% | 156 | 312 | 312 | 312 | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `AGE-MOEA-II` | 8 | 156 | 156 | 1.500 | 234.000 | 234.000 | 0 | 0,0000% | 156 | 312 | 312 | 312 | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `NSGA-III` | 10 | 275 | **276** | 2.000 | 550.000 | **552.000** | **+2.000** | **+0,3636%** | **276** | **552** | **552** | **552** | `iterations >= maxIterations` (`init=1`, lote $4\lceil N/4\rceil$) |
| `MOEA/DD` | 10 | 275 | 275 | 2.000 | 550.000 | 550.000 | 0 | 0,0000% | **550** | 550 | 550 | 550 | `do { for(N) eval++; } while (eval < maxEvals)` |
| `FDEA` | 10 | 275 | 275 | 2.000 | 550.000 | 550.000 | 0 | 0,0000% | 275 | **276** | **549** | 550 | `while (eval < maxEvals && gen < maxGen)` (por filho) |
| `RVEA` | 10 | 275 | 275 | 2.000 | 550.000 | 550.000 | 0 | 0,0000% | 275 | 550 | 550 | 550 | `TerminationByEvaluations` verificado após lote $N$ |
| `AGE-MOEA` | 10 | 275 | **276** | 2.000 | 550.000 | **552.000** | **+2.000** | **+0,3636%** | **276** | **552** | **552** | **552** | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `AGE-MOEA-II` | 10 | 275 | **276** | 2.000 | 550.000 | **552.000** | **+2.000** | **+0,3636%** | **276** | **552** | **552** | **552** | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `NSGA-III` | 15 | 135 | **136** | 3.000 | 405.000 | **408.000** | **+3.000** | **+0,7407%** | **136** | **272** | **272** | **272** | `iterations >= maxIterations` (`init=1`, lote $4\lceil N/4\rceil$) |
| `MOEA/DD` | 15 | 135 | 135 | 3.000 | 405.000 | 405.000 | 0 | 0,0000% | **270** | 270 | 270 | 270 | `do { for(N) eval++; } while (eval < maxEvals)` |
| `FDEA` | 15 | 135 | 135 | 3.000 | 405.000 | 405.000 | 0 | 0,0000% | 135 | **136** | **269** | 270 | `while (eval < maxEvals && gen < maxGen)` (por filho) |
| `RVEA` | 15 | 135 | 135 | 3.000 | 405.000 | 405.000 | 0 | 0,0000% | 135 | 270 | 270 | 270 | `TerminationByEvaluations` verificado após lote $N$ |
| `AGE-MOEA` | 15 | 135 | **136** | 3.000 | 405.000 | **408.000** | **+3.000** | **+0,7407%** | **136** | **272** | **272** | **272** | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |
| `AGE-MOEA-II` | 15 | 135 | **136** | 3.000 | 405.000 | **408.000** | **+3.000** | **+0,7407%** | **136** | **272** | **272** | **272** | `iterations >= maxIterations` (`init=1`, lote $2\lceil N/2\rceil$) |

---

## 5. Comparação de Alternativas de Engenharia para o Gate G1 (Procedimentos 5–7 de `tasks/task13.md`)

Conforme exigido nos itens 5, 6 e 7 de [`tasks/task13.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task13.md), comparam-se as quatro alternativas para harmonizar populações e orçamento de avaliações sem descaracterizar a identidade dos algoritmos:

| Alternativa | Descrição Técnica | Impacto sobre a Identidade dos Métodos | Viabilidade Experimental | Veredicto |
|---|---|---|---|---|
| **A1 — Forçar mesma população $N$ divisível por 4 para todos os algoritmos** | Alterar $N(m)$ de $\{91, 210, 275, 135\}$ para $\{92, 212, 276, 136\}$ em todos os algoritmos | **Inaceitável**: rompe a bijeção exata $N = \binom{H_1+m-1}{m-1} + \binom{H_2+m-1}{m-1}$ das grades simplex de Das & Dennis / Deb & Jain em `MOEA/DD` (`W3D_91.dat`, `W5D_210.dat`, `W10D_275.dat`, `W15D_135.dat`) e `RVEA` (`Population size must match the number of reference vectors`). | Exigiria regenerar pesos não-canônicos fora de Das & Dennis. | **Rejeitada** |
| **A2 — Orçamento comum pelo $\text{MMC}(N_{\text{config}}, N_{\text{alloc}})$** | Definir $B$ como múltiplo do mínimo múltiplo comum entre $N_{\text{config}}$ e $N_{\text{alloc}}$ (ex.: $\text{MMC}(275, 276) = 75.900$ por ciclo) | Preserva lotes inteiros, mas exige alterar $G_{\max}$ em ordens de grandeza ou usar $G_{\max}$ incompatíveis com a literatura WFG/DTLZ. | Em $m=10$, $\text{MMC}(275, 276) = 75.900$ não divide $550.000$, inviabilizando os orçamentos canônicos da literatura. | **Rejeitada** |
| **A3 — Reduzir `maxIterations` para $\lfloor B_{\text{target}} / N_{\text{alloc}} \rfloor$ sem lote parcial** | Fixar $G_{\text{int}} = \lfloor B_{\text{target}} / N_{\text{alloc}} \rfloor$ para `NSGA-III`, `AGE-MOEA` e `AGE-MOEA-II` | Evita exceder $B_{\text{target}}$, mas deixa um déficit residual de $B_{\text{target}} \bmod N_{\text{alloc}}$ avaliações (ex.: em $m=3$, $36.400 = 395 \times 92 + 60$, perdendo 60 avaliações). | Viável caso se deseje evitar lotes parciais, mas entrega $B_{\text{real}} < B_{\text{target}}$ por até $N_{\text{alloc}}-1$ avaliações. | **Secundária (sub-orçamento)** |
| **A4 (Recomendada e Implementada) — Preservar $N_{\text{alloc}}$ nativo de cada método e impor $B_{\text{target}} = N_{\text{config}}(m) \times G_{\max}(m, \text{problem})$ exato via `EvaluationBudgetProblem` + `BudgetContractEvaluator`** | Mantém $|\mathcal{R}(m)| = N_{\text{config}}(m)$ para referências/pesos, preserva $N_{\text{alloc}}$ exigido por cada algoritmo (`NSGA-III` múltiplo de 4, `AGE-MOEA`/`II` par) e trunca limpa e deterministicamente o último lote de filhos em `BudgetContractEvaluator` / `BudgetComponentEvaluation` quando o orçamento restante $r = B_{\text{target}} - B_{\text{atual}} < N_{\text{alloc}}$, configurando `maxIterations` $= \lceil B_{\text{target}} / N_{\text{alloc}} \rceil$. | **Preserva 100% a identidade algorítmica**: `NSGA-III.replacement` e `AGEMOEA.replacement` operam sobre a união $|P_t \cup Q_t| = N_{\text{alloc}} + r \ge N_{\text{alloc}}$ contendo exclusivamente soluções avaliadas de forma real dentro do orçamento $B_{\text{target}}$, selecionando $N_{\text{alloc}}$ sobreviventes sem avaliar nenhuma solução extra e sem lançar exceções que abortem o algoritmo. | **100% compatível** com os orçamentos canônicos da literatura e comprovado empiricamente para todos os 6 algoritmos e todas as 6 dimensões $m \in \{2,3,5,8,10,15\}$. | **Aprovada para integração na T23 (Gate G1)** |

---

## 6. Detalhamento da Solução Técnica em `EvaluationBudgetProblem.java`

A classe [`EvaluationBudgetProblem`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java) foi implementada seguindo rigorosamente os requisitos da `T13`:

1. **Implementação completa de `DoubleProblem`**:
   - Delega `numberOfVariables()`, `numberOfObjectives()`, `numberOfConstraints()`, `name()`, `createSolution()` e `variableBounds()` para o `DoubleProblem` subjacente, sendo aceita sem restrições por todos os builders do jMetal 7.8.
2. **Contabilidade Atômica Independente (`Thread-Safe`)**:
   - `attemptedEvaluations` (`AtomicLong`): total de invocações solicitadas pelo algoritmo.
   - `actualProblemEvaluations` (`AtomicLong`): total de chamadas efetivamente executadas em `delegate.evaluate(solution)`.
   - `suppressedOvershootEvaluations` (`AtomicLong`): chamadas excedentes suprimidas quando `enforceHardBudget == true`.
   - `evaluatorBatchCalls` (`AtomicInteger`): lotes processados por `SolutionListEvaluator` ou `Evaluation`.
3. **Truncamento Seguro do Lote Final Parcial (`BudgetContractEvaluator` e `BudgetComponentEvaluation`)**:
   - Nos algoritmos baseados em lotes (`NSGA-III`, `AGE-MOEA`, `AGE-MOEA-II`, `RVEA`), a última geração evolutiva recebe apenas os $r = B_{\text{target}} - B_{\text{atual}}$ filhos efetivamente avaliados dentro do orçamento (removendo do lote de descendentes quaisquer filhos que ultrapassariam $B_{\text{target}}$ **antes** da etapa `replacement(population, offspringPopulation)`).
   - Como $|P_t| = N_{\text{alloc}}$ já está completamente avaliada e $r \ge 1$, a população combinada $R_t = P_t \cup Q_t$ tem tamanho $N_{\text{alloc}} + r > N_{\text{alloc}}$ com 100% dos indivíduos legitimamente avaliados, permitindo que a seleção ambiental (`EnvironmentalSelection`, `AGEMOEAEnvironmentalSelection`, `AGEMOEA2EnvironmentalSelection`, `RVEAEnvironmentalSelection`) conclua normalmente a última geração sem jamais consumir avaliações extras nem selecionar soluções não avaliadas.

---

## 7. Testes Executados e Evidências Reais (`EvaluationBudgetAuditTest`)

Comando executado (isolado apenas na classe de teste da T13, conforme instrução de isolamento):
```powershell
mvn test -Dtest=EvaluationBudgetAuditTest
```
- **Exit code**: `0` (`BUILD SUCCESS`)
- **Resultados**: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` (Tempo: `4.191 s`)
  1. `shouldVerifyPopulationAndActualEvaluationsAcrossAllDimensions(int)` (6 execuções parametrizadas para $m \in \{2, 3, 5, 8, 10, 15\}$): confirma $N_{\text{config}}, N_{\text{alloc}}, N_{\text{result}}$ e a igualdade exata entre a contagem física de `Problem.evaluate` e a fórmula analítica para todos os 6 algoritmos.
  2. `shouldVerifyBoundaryBudgetsForEvenAndOddPopulations()`: verifica empiricamente os limites de fronteira $B \in \{N, N+1, 2N-1, 2N\}$ tanto para população par ($m=2, N=100$) quanto para população ímpar ($m=3, N=91$).
  3. `shouldEnforceExactEvaluationBudgetAcrossAllAlgorithmsAndDimensions(int)` (6 execuções parametrizadas para $m \in \{2, 3, 5, 8, 10, 15\}$): comprova que, no modo de contrato estrito (`enforceHardBudget = true`), **todos os 6 algoritmos** realizam exatamente $B_{\text{real}} == B_{\text{target}}$ avaliações reais, com $0$ avaliações restantes e objetivos 100% finitos.
  4. `shouldEnforceStrictBudgetOnPartialBatchBoundaries()`: comprova $B_{\text{real}} == B_{\text{target}}$ inclusive em fronteiras de lote parcial ($B = N+1$ e $B = 2N-1$ em $m=3, N=91$).
  5. `shouldNotIntroduceExtraEvaluationsOrAlterDeterministicTrajectory()`: comprova que `EvaluationBudgetProblem` em modo auditoria produz exatamente os mesmos vetores objetivos bit-a-bit que o problema desencapsulado sob a mesma semente de `JMetalRandom` no orçamento completo de `WFG1` ($m=3, B = 36.400$), sem adicionar nenhuma avaliação extra.
  6. `shouldValidateGeneratedBudgetAuditCsvConsistency()`: valida linha a linha e coluna a coluna as 37 linhas de `docs/auditoria/budget-audit.csv`.
  7. `shouldRejectInvalidArguments()`: valida tratamento defensivo de argumentos nulos ou fora do protocolo.

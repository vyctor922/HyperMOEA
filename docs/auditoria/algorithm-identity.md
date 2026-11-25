# Auditoria de Identidade Algorítmica, Porte do FDEA, Algoritmos Legados e Parâmetros do MOEA/DD (T14)

**Data:** 2026-11-09  
**Agente Responsável:** Agente A (Algorithm & FDEA Identity Auditor)  
**Tarefa:** `T14` (`tasks/task14.md`)  
**Status:** Concluído (Evidências e correções validadas)

---

## 1. Identidade Científica e Rastreabilidade do FDEA

### 1.1 Referência Bibliográfica Real vs. Confusão Documental Prévia
A inspeção do repositório original clonado em `Fuzzy-Decomposition-Based-Evolutionary-Algorithm-RepositoryGITHUB/` (remote `https://github.com/songbai-liu/Fuzzy-Decomposition-based-Evolutionary-Algorithm.git`, commit `235fba2bb2c3c138cef07c6628efd563b58849a2`, autor Songbai Liu, 05/03/2021) comprova que o algoritmo **FDEA** implementado e portado para `br.usp.lti.newmoea.algorithm.fdea.*` corresponde exatamente a:

- **Artigo real do FDEA:** Songbai Liu, Qiuzhen Lin, Jianyong Chen, Kay Chen Tan, *"A Fuzzy Decomposition-Based Multi/Many-Objective Evolutionary Algorithm"*, **IEEE Transactions on Cybernetics**, Vol. 52, No. 5, pp. 3495–3509, Maio 2022 (publicação antecipada online em 2020, DOI: `10.1109/TCYB.2020.3008903`, IEEE Xplore document `9158526`).
- **Divergência no texto da dissertação e README prévio:** A dissertação (`docs/auditoria/dissertacao_completa.txt`) e versões anteriores do `README.md` mencionavam erroneamente He et al. (2014) (*"Fuzzy-Based Pareto Optimality for Many-Objective Evolutionary Algorithms"*, IEEE TEVC) ao descreverem a intuição de dominância difusa, embora citassem Liu et al. (2020) na revisão. O código original de Songbai Liu **não** implementa dominância de Pareto nebulosa de He et al. (2014); ele implementa **autodecomposição difusa iterativa do espaço objetivo** via matriz de similaridade fuzzy ($S$) e amostragem orientada ao ponto ideal (**MSBS** — *Most Similar Based Sampling* e **LSBS** — *Least Similar Based Sampling*), combinada com estimativa adaptativa da curvatura $p$ da frente de Pareto e escalarização WS compartilhada (`computeWS`) ou local (`computeLWS`).

### 1.2 Variantes no Repositório Original (`jmetal.metaheuristics.fdea.*`) vs. Porte (`br.usp.lti.newmoea.algorithm.fdea.*`)
No repositório original (`Fuzzy-Decomposition-Based-Evolutionary-Algorithm-RepositoryGITHUB/FDEA/src/jmetal/metaheuristics/fdea/`), existem 4 classes de algoritmo e 2 classes de amostragem:

| Arquivo Original (`jmetal.metaheuristics.fdea`) | Papel no Repositório Original | Correspondência em `br.usp.lti.newmoea.algorithm.fdea` |
|---|---|---|
| `FDEA.java` (737 linhas) | **Implementação canônica padrão** invocada por todos os runners `FDEA_main_2obj.java` a `FDEA_main_15obj.java` (linha 128: `algorithm = new FDEA(problem)`). Usa **MSBS** (`MostSimilarBasedSampling`) para selecionar $N$ candidatos de referência (`subPopulation[0]`) a partir de $S_t$ ($|S_t| > N$), seguido de **LSBS** (`LeastSimilarBasedSampling`) sobre `subPopulation[0]` para extrair os $m$ pontos de referência extremos (`ReferenceSet[0]`) e os $N-m$ subproblemas restantes (`ReferenceSet[1]`). Usa vetor de pesos global compartilhado `re` (média normalizada dos centroides dos clusters na hiperesfera unitária) com `computeWS(sol, re)`. | `FDEA.java` com `FDEA.VERSION.I` (padrão em `FDEABuilder` e `WFGStudy.java`). |
| `FDEA1.java` (721 linhas, comentário `"No shared w"`) | Variante de ablação sem vetor de peso compartilhado global: utiliza ponderação local por subproblema (`computeLWS`: $\sum_{j=1}^m \tilde{f}_j(x) / w_{i,j}$), condição `st[0].size() < 4` para atualização do nadir e grade de curvatura reduzida ($k_1=9, k_2=6$ após $0.2 G_{\max}$). | Suportado via `FDEA.VERSION.II` em `FDEA.java` (`LeastSimilarBasedSampling` na amostragem primária + ponderação local `computeLocalWeightedSum` por centroide de cluster). |
| `FDEA2.java` (889 linhas) | Variante experimental com arquivo externo auxiliar (`archive_`) e reprodução baseada em vizinhança (`reproduction_Neighbor`). Não é referenciada pelos runners `FDEA_main_*obj.java`. | Não aplicável ao estudo principal. |
| `FDEA3.java` (741 linhas) | Cópia idêntica de `FDEA.java` mantendo apenas linhas comentadas de experimentos preliminares. | Redundante com `FDEA.java` (`VERSION.I`). |
| `MostSimilarBasedSampling.java` (150 linhas) | Amostragem **MSBS**: identifica os $m$ extremos por ângulo mínimo aos eixos coordenados (`computeAngle`), divide iterativamente o conjunto em partições de tamanhos $K_1 = \lceil |B|/2 \rceil$ e $K_2 = |B| - K_1$, selecionando os $K_1$ indivíduos de menor `distanceToIdealPoint` ($L_p$) e removendo para `subSets[1]` o par mais similar segundo $\max_{p \in A, q \in B} \min_j (1 - |T_j(p) - T_j(q)|)$. | `MostSimilarBasedSampling.java` (portado 1:1 para `List<FDEAIndividual>`). |
| `LeastSimilarBasedSampling.java` (149 linhas) | Amostragem **LSBS**: estrutura análoga ao MSBS, porém particiona mantendo os mais próximos ao ponto ideal e removendo iterativamente o elemento de **menor** similaridade máxima (`minSimilarity < maxSimilarity`) em relação ao conjunto retido. | `LeastSimilarBasedSampling.java` (portado 1:1 para `List<FDEAIndividual>`). |
| `jmetal.core.Solution` (campos extras adicionados por Liu et al.) | Campos `normalizedObjective_`, `translatedObjectives_`, `unitHypersphereObjectives_`, `distanceToIdealPoint_`, `distanceToNadirPoint_`, `sumValue_` embutidos diretamente em `jmetal.core.Solution` (jMetal 4.5). | `FDEAIndividual.java` (wrapper limpo em torno de `DoubleSolution` do jMetal 7.8 sem poluir a interface global `Solution`). |

---

## 2. Mapeamento Método a Método: `FDEA` Original vs. `br.usp.lti.newmoea.algorithm.fdea.FDEA`

| Método no Original (`jmetal.metaheuristics.fdea.FDEA`) | Método em `br.usp.lti.newmoea.algorithm.fdea.FDEA` | Análise de Equivalência e Ajustes Realizados |
|---|---|---|
| `execute()` (linhas 69–197) | `run()` (linhas 92–162) | **Equivalente e corrigido.** No original (linhas 150–176), `updateIdealPoint(st[0])` e `updateNadirPoint(st[0]/st[1])` são executados em **todas** as gerações antes do teste `if (st[1].size() == populationSize_)`. Na versão anterior do porte, quando `st1.size() == populationSize`, a atualização de `zideal` e `znadir` era pulada. Corrigido em `FDEA.run()` para atualizar `zideal` e `znadir` em toda geração antes do ramo `if (st1.size() <= populationSize)`. |
| `initPopulation()` (linhas 230–239) | `initPopulation()` (linhas 164–173) | **Corrigido para orçamento estrito.** Avalia até `min(populationSize, maxEvaluations)` indivíduos (`while (population.size() < populationSize && evaluations < maxEvaluations)`), incrementando `evaluations++` a cada `problem.evaluate(solution)`. |
| `reproduction(G, Gmax)` (linhas 200–225) | `reproduction()` (linhas 190–222) | **Equivalente e corrigido.** No original, para cada $i \in \{0, \dots, N-1\}$, selecionam-se 2 pais, executa-se `SBXCrossover` (`offSpring = crossover_.execute(parents)`), aplica-se mutação polinomial **apenas em `offSpring[0]`**, avalia-se `offSpring[0]` e adiciona-se `offSpring[0]` a `offspringPopulation_` (1 filho avaliado por cruzamento). No nosso porte, esse mesmo esquema de 1 filho avaliado por cruzamento é preservado, com guarda estrita `while (offspring.size() < populationSize && evaluations < maxEvaluations)`, garantindo que `evaluations` **nunca** ultrapasse `maxEvaluations` (mesmo para $N$ ímpar como $N=91$ ou orçamentos $N+1, 2N-1$). |
| `initIdealPoint()` / `updateIdealPoint(st[0])` (linhas 244–271) | `initIdealPoint()` / `updateIdealPoint(st0)` | **Idêntico ($1:1$).** $z^*_j = \min_{x} f_j(x)$. |
| `initNadirPoint()` / `updateNadirPoint()` / `updateExtremePoints()` / `asfFunction()` (linhas 276–358) | `initNadirPoint()` / `updateNadirPoint()` / `updateExtremePoints()` / `asfFunction()` | **Matematicamente equivalente.** O original usa `Jama.Matrix` (`EX.rank() == EX.getRowDimension()`, `EX.inverse().times(UM)`) para resolver o sistema linear do hiperplano extremo $(E - z^*) \cdot \alpha = \mathbf{1}$ e obter $z^{\text{nad}}_j = 1/\alpha_j + z^*_j$, com fallback para $\max_{x \in F} f_j(x)$ se o sistema for singular ou produzir $z^{\text{nad}}_j \le z^*_j$. O nosso porte resolve o mesmo sistema linear via eliminação Gaussiana com pivoteamento parcial (`solveLinearSystem(ex, u)`), eliminando a dependência legada `Jama.jar` e aplicando o mesmo fallback quando singular ou degenerado. |
| `normalizationObjective(mgPopulation)` (linhas 361–371) | `normalizeObjectives(pool)` | **Idêntico ($1:1$ com proteção contra divisão por zero).** $\tilde{f}_j(x) = \max(0, (f_j(x) - z^*_j) / \max(z^{\text{nad}}_j - z^*_j, 10^{-12}))$. |
| `estimation_Curvature(Population)` (linhas 400–524) | `estimateCurvature(front)` | **Idêntico ($1:1$).** Filtra pontos com $\tilde{f}_j \le 1.0$, calcula distância assinada ao hiperplano linear $d_i = (\sum_j \tilde{f}_j - 1)/\sqrt{m}$, média $\mu$, variância amostral $\sigma^2$ e coeficiente de variação $CV = \sigma / |\mu|$. Se $\mu \ge 0$, busca $p \in \{1.00, 1.05, \dots, 3.50\}$ ($T_2 = 51$ pontos); se $\mu < 0$, busca $p \in \{1.00, 0.97, \dots, 0.52\}$ ($T_1 = 17$ pontos). Se $c \neq 1.0$, $CV < 0.15$ e $U(0,1) < 0.95$, ajusta $c = 1 \mp CV$, truncando em $[0.4, 4.0]$. |
| `mapping(mgPopulation, p)` (linhas 526–556) | `mapping(pool, p)` | **Idêntico ($1:1$).** Calcula norma $L_p$ (`normDistance`), norma Euclidiana $L_2$ (`euclidDist`), projeção na superfície $L_p$ (`translatedObjective[j] = normObj[j] / normDistance`) e projeção na hiperesfera unitária $L_2$ (`unitHypersphereObjective[j] = normObj[j] / euclidDist`), com piso `1e-3` quando a norma é nula. |
| `getNextPopulation(subPopulation, p)` (linhas 596–705) | `selectNextPopulation(msbsSubSets, p)` | **Idêntico ($1:1$ em `VERSION.I`).** Executa `LeastSimilarBasedSampling(refPool, m).getIdealPointOrientedPopulation(m)` para separar as $m$ referências extremas das $N-m$ referências internas, associa cada solução descartada em `msbsSubSets[1]` à referência mais próxima por distância Euclidiana em `translatedObjectives`, preserva o ponto extremo nos primeiros $m$ clusters (`i < m`) e, nos demais $N-m$ clusters, seleciona o indivíduo que minimiza `computeWeightedSum(ind, re)` (`VERSION.I`) ou `computeLocalWeightedSum(ind, clusterCenters[i])` (`VERSION.II`). |

---

## 3. Defeitos Identificados e Corrigidos no `FDEA.java` e `FDEABuilder.java`

### 3.1 Defeito 1 — Quebra de Determinismo por `java.util.Random` Não Semeado
- **Causa raiz:** Na linha 44 de `FDEA.java`, existia `private final Random random = new Random();` (semeado pelo relógio do sistema na construção da instância), usado na seleção de pais quando `selection == null` e no teste estocástico `random.nextDouble() < 0.95` dentro de `estimateCurvature()`.
- **Impacto:** Mesmo chamando `JMetalRandom.getInstance().setSeed(seed)` antes de `algorithm.run()`, duas execuções do `FDEA` com a mesma semente divergiam porque `this.random` ignorava o gerador central do jMetal.
- **Correção aplicada:** Removido `java.util.Random` de `FDEA.java` e substituído integralmente por `JMetalRandom.getInstance().nextInt(0, currentPopSize - 1)` e `JMetalRandom.getInstance().nextDouble()`. O determinismo bit-a-bit (`VERSION.I` e `VERSION.II`) foi comprovado em `FdeaIdentityAndLegacyAuditTest`.

### 3.2 Defeito 2 — Incompatibilidade Silenciosa entre `BinaryTournamentSelection(RankingAndCrowdingDistanceComparator)` e `FDEA.reproduction()`
- **Causa raiz:** No repositório original (`FDEA_main_2obj.java` a `15obj.java`, linha 145), o operador de seleção configurado é `SelectionFactory.getSelectionOperator("RandomSelection", null)`, que sorteia 2 pais distintos uniformemente ao acaso na população (`RandomSelection.java`, linhas 41–49). Já em `WFGStudy.buildFDEA` (linhas 577–579), foi injetado `new BinaryTournamentSelection<>(new RankingAndCrowdingDistanceComparator<>())`. No jMetal 7.8, `RankingAndCrowdingDistanceComparator` consulta os atributos de solução gravados por `FastNonDominatedSortRanking` (default `-1` quando ausente) e `CrowdingDistanceDensityEstimator` (default `0.0` quando ausente). Como `FDEA.java` **nunca** executava `FastNonDominatedSortRanking` nem `CrowdingDistanceDensityEstimator` sobre `population` antes de `reproduction()`:
  1. Na geração 0 (após `initPopulation()`), todos os indivíduos tinham `rank = -1` e `crowdingDistance = 0.0`, fazendo o comparador empatar sempre (`0`).
  2. Nas gerações seguintes, `population` continha indivíduos vindos de diferentes frentes de `union` da geração anterior (alguns com `rank = 0`, outros com `rank = 1, 2...` defasados em relação à população filtrada, e todos com `crowdingDistance = 0.0` pois o FDEA não calcula crowding distance).
- **Correção aplicada:**
  1. Adicionado o método `preparePopulationAttributesForSelection()` em `FDEA.java`, invocado no início de `reproduction()` sempre que `selection != null`. Ele executa `FastNonDominatedSortRanking` sobre `population` atual e `CrowdingDistanceDensityEstimator` sobre cada subfrente de `population`, garantindo que `BinaryTournamentSelection(new RankingAndCrowdingDistanceComparator<>())` opere sobre atributos válidos e atualizados.
  2. Quando `selection == null`, `reproduction()` seleciona dois pais aleatórios distintos (`idx1 != idx2` para `population.size() > 1`) via `JMetalRandom.getInstance()`, reproduzindo fielmente o `RandomSelection.java` do repositório original de Liu et al. (2022).

### 3.3 Defeito 3 — Guarda de Orçamento (`maxEvaluations`) e Truncamento Prematuro por `maxGenerations` Default no `FDEABuilder`
- **Causa raiz:**
  1. Em `initPopulation()`, o loop ia até `populationSize` sem verificar `evaluations < maxEvaluations`.
  2. Em `FDEABuilder.java`, `maxGenerations` possuía valor default `250`. Se um chamador configurasse `.setPopulationSize(91).setMaxEvaluations(91 * 400)` sem chamar `.setMaxGenerations(400)`, o algoritmo parava prematuramente na geração 250 ($22\,750$ avaliações em vez de $36\,400$).
- **Correção aplicada:**
  1. Em `FDEA.initPopulation()`, `run()` e `reproduction()`, a guarda `evaluations < maxEvaluations` é respeitada estritamente em todos os pontos: o algoritmo avalia exatamente `maxEvaluations` soluções (nunca excedendo o orçamento, mesmo para populações ímpares como $N=91$ e orçamentos de fronteira $N, N+1, 2N-1, 2N$).
  2. Em `FDEABuilder.build()`, quando `maxEvaluations > 0` é informado e `setMaxGenerations(...)` não foi chamado explicitamente, `effectiveMaxGenerations` é calculado automaticamente como `(int) Math.ceil((double) maxEvaluations / populationSize)`.

---

## 4. Auditoria dos 5 Algoritmos Legados em `WFGStudy.java` e Especificação da Flag `-Dstudy.includeLegacyAlgorithms=false`

Em `WFGStudy.java` (linhas 384–426 e 552–567), existem 5 métodos de construção de algoritmos legados (`buildNSGAII`, `buildSPEA2`, `buildMOEADDRA`, `buildGWASFGA`, `buildESPEA`) que estão comentados no método `configureAlgorithmList` (linhas 315–319), mas permanecem acessíveis no código. A auditoria de código e bytecode (`jmetal-algorithm-7.8.jar`) revelou os seguintes defeitos técnicos nesses métodos:

### 4.1 `SPEA2` (`WFGStudy.buildSPEA2`, linhas 398–409)
- **Evidência no código (`WFGStudy.java`, linhas 399–408):**
  ```java
  if (populationSize % 2 != 0) {
      populationSize += 1;
  }
  ...
  SPEA2Builder<DoubleSolution> builder = new SPEA2Builder<>(problem, crossover, mutation);
  builder.setSelectionOperator(selection);
  builder.setMaxIterations(maxGenerations);
  return builder.build();
  ```
- **Defeito comprovado via bytecode (`org.uma.jmetal.algorithm.multiobjective.spea2.SPEA2Builder`):**
  - O construtor de `SPEA2Builder` inicializa `this.populationSize = 100` (bytecode offset `16: bipush 100; putfield #17 // Field populationSize:I`).
  - `WFGStudy.buildSPEA2` incrementa a variável local `populationSize`, mas **nunca chama `builder.setPopulationSize(populationSize)`**!
  - Como resultado, o `SPEA2` ignora completamente o tamanho populacional $N_m$ do experimento ($91, 210, 156, 275, 135$) e executa sempre com $N = 100$ e arquivo ambiental de tamanho $100$ (`EnvironmentalSelection(100)`), avaliando $100 \times G_{\max}$ soluções em vez de $N_m \times G_{\max}$.

### 4.2 `MOEADDRA` (`WFGStudy.buildMOEADDRA`, linhas 411–426)
- **Evidência no código e bytecode (`org.uma.jmetal.algorithm.multiobjective.moead.MOEADDRA`):**
  1. `WFGStudy.buildMOEADDRA` usa `MOEADBuilder(problem, MOEADBuilder.Variant.MOEADDRA)` com operador `DifferentialEvolutionCrossover` (`de`), exigido porque o construtor de `MOEADDRA` faz cast explícito `(DifferentialEvolutionCrossover) crossoverOperator` (bytecode offset `28: checkcast #23`).
  2. Em `MOEADDRA.run()` (bytecode offsets `24–223`), após avaliar a população inicial ($N$ avaliações), cada ciclo externo seleciona um lote de subproblemas via `tourSelection(10)`: inicializa a lista com os $m$ subproblemas extremos e sorteia outros até atingir $\lfloor N / 5 \rfloor$ subproblemas (`list.size() < (int)(populationSize / 5.0)`). Em seguida, avalia todos os $\lfloor N / 5 \rfloor$ subproblemas do lote **sem verificar `evaluations < maxEvaluations` dentro do laço interno**, e a cada $30$ gerações executa `utilityFunction()`.
  3. Consequentemente, o número total de avaliações avança em saltos de $\lfloor N / 5 \rfloor$ (ultrapassando `maxEvaluations` quando $(B - N)$ não é múltiplo exato de $\lfloor N / 5 \rfloor$), e para $m=2, N \le 300$, `initializeUniformWeight()` ignora o diretório `MOEAD_Weights` e gera pesos uniformes em memória com `0.0` nos extremos.

### 4.3 `GWASFGA` (`WFGStudy.buildGWASFGA`, linhas 552–560)
- **Evidência no código e bytecode (`GWASFGA`, `WASFGA`, `WeightVectors`, `ASFWASFGA`):**
  1. O método em `WFGStudy.java` passa `maxGenerations` como 3º argumento (`maxIterations`) do construtor de `GWASFGA`.
  2. `GWASFGA` estende `WASFGA`. No construtor de `WASFGA` (bytecode offset `44: invokevirtual createUtilityFunction`), os vetores de pesos são lidos do arquivo via `VectorFileUtils.readVectors(weightVectorsFileName)` e invertidos por `WeightVectors.invert(weights, true)` (bytecode offset `90`).
  3. Em `WeightVectors.invert(double[][] weights, boolean normalize)` (bytecode offsets `40–48` e `74–85`), cada componente $w_{i,j}$ é invertida literalmente via `1.0 / weights[i][j]` (`dconst_1`, `daload`, `ddiv`) e depois dividida pela soma `sum = sum + (1.0 / weights[i][j])`:
     - Quando `GWASFGA` lê arquivos de pesos contendo `0.0` nos extremos (como `W3D_91.dat`, `W8D_156.dat`, `W10D_275.dat`), `1.0 / 0.0` resulta em `Double.POSITIVE_INFINITY`, `sum` torna-se `Infinity`, e `Infinity / Infinity` resulta em **`Double.NaN`** para os componentes nulos e **`0.0`** para os componentes positivos (`(1.0 / w) / Infinity = 0.0`)!
     - Em seguida, `GWASFGA` constrói `ASFWASFGA(this.weights)` para o ponto utópico e `ASFWASFGA(this.weights)` para o ponto nadir. Quando `ASFWASFGA.evaluate(solution, index)` multiplica a diferença normalizada pelo peso invertido (`dload 10; ... dmul` no bytecode offset `216`), o valor da função de utilidade torna-se **`Double.NaN`**, corrompendo o ranking `GWASFGARanking`.
     - **Conexão histórica com a substituição $0 \mapsto 10^{-5}$:** Foi exatamente para tentar contornar essa divisão por zero ($1.0 / 0.0$) na função de utilidade ASF do `GWASFGA` que os arquivos em `MOEAD_Weights/` haviam sido alterados historicamente com $0 \mapsto 0.00001$ ($10^{-5}$), contaminando indevidamente o `MOEA/DD` (que usa PBI, onde a norma Euclidiana $\|\lambda\|_2$ no denominador nunca é zero para $\sum_j \lambda_j = 1$). Após a restauração dos vetores simplex canônicos com `0.0` em `MOEAD_Weights/` (Decisão `D5` / `T15`), o `GWASFGA` volta a produzir `Double.NaN` em `WeightVectors.invert`, reforçando a necessidade de mantê-lo desativado por padrão via `-Dstudy.includeLegacyAlgorithms=false`.

### 4.4 `NSGAII` (`WFGStudy.buildNSGAII`, linhas 384–396) e `ESPEA` (`WFGStudy.buildESPEA`, linhas 562–567)
- **`NSGAII`:** Como `NSGAII` exige `matingPoolSize` par no torneio binário padrão, `buildNSGAII` incrementa `populationSize` quando ímpar (`if (populationSize % 2 != 0) populationSize += 1;`) e define `setMaxEvaluations(maxGenerations * populationSize)`. Isso altera $N$ de $91 \to 92$ ($m=3$), $275 \to 276$ ($m=10$) e $135 \to 136$ ($m=15$), concedendo $G_{\max}$ avaliações extras ao `NSGAII` nessas dimensões em comparação com o orçamento canônico $N_m \times G_{\max}$.
- **`ESPEA`:** Utiliza `ESPEABuilder` com `EnergyArchive` e `RandomSelection` default, fora do escopo dos 6 algoritmos da dissertação.

### 4.5 Especificação da Flag `-Dstudy.includeLegacyAlgorithms=false` (Integração na `T23`)
Conforme decidido no Gate `G0/G1` (`docs/decisions/G0-G1-scope-and-decisions.md`), o escopo experimental oficial do projeto compreende exclusivamente os **6 algoritmos principais**:
1. `NSGA-III` (`NSGAIII`)
2. `MOEA/DD` (`MOEADD`)
3. `FDEA` (`FDEA` — `VERSION.I`)
4. `RVEA` (`RVEA`)
5. `AGE-MOEA` (`AGEMOEA`)
6. `AGE-MOEA-II` (`AGEMOEAII`)

Para isolar os 5 algoritmos legados sem remover seu código histórico:
- **Propriedade de sistema:** `-Dstudy.includeLegacyAlgorithms=false` (valor padrão: `false`, lido via `Boolean.parseBoolean(System.getProperty("study.includeLegacyAlgorithms", "false"))`).
- **Comportamento padrão (`false`):** `WFGStudy.configureAlgorithmList(...)` instancia exclusivamente os 6 algoritmos principais acima, garantindo que nenhum algoritmo legado (`NSGAII`, `SPEA2`, `MOEADDRA`, `ESPEA`, `GWASFGA`) seja executado por acidente.
- **Comportamento opcional (`true`):** Caso `-Dstudy.includeLegacyAlgorithms=true` seja informado explicitamente para fins de inspeção histórica, os algoritmos legados podem ser adicionados, devendo `buildSPEA2` incluir `builder.setPopulationSize(populationSize)` caso seja ativado. (A edição em `WFGStudy.java` será realizada na tarefa `T23`, respeitando o isolamento de arquivos de `T14`).

---

## 5. Auditoria Completa dos Parâmetros Internos do `MOEA/DD` (`MOEADD`) no jMetal 7.8

A inspeção do código de `WFGStudy.buildMOEADD` (linhas 434–450) confrontada com o bytecode de `org.uma.jmetal.algorithm.multiobjective.moead.MOEADBuilder`, `org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD` e `org.uma.jmetal.algorithm.multiobjective.moead.MOEADD` em `jmetal-algorithm-7.8.jar` estabelece o comportamento exato de cada parâmetro:

| Parâmetro / Configuração | Valor Configurado em `WFGStudy.buildMOEADD` | Comportamento Real no Bytecode do jMetal 7.8 (`MOEADD` / `AbstractMOEAD`) | Status |
|---|---|---|---|
| **Variante (`MOEADBuilder.Variant`)** | `MOEADBuilder.Variant.MOEADD` | Instancia `org.uma.jmetal.algorithm.multiobjective.moead.MOEADD` (`MOEADBuilder.build()`, bytecode offset `188`). | **Efetivo** |
| **Tamanho da população (`populationSize`)** | $N_m \in \{100, 91, 210, 156, 275, 135\}$ | Define `populationSize` e `resultPopulationSize` em `AbstractMOEAD`. Determina o número de subproblemas e o arquivo `.dat` buscado (`"W" + m + "D_" + N + ".dat"`). | **Efetivo** |
| **Orçamento (`maxEvaluations`)** | `maxGenerations * populationSize` ($G_{\max} \cdot N_m$) | `MOEADD.run()` avalia $N$ soluções iniciais (`evaluations = populationSize`) e executa laços externos `do { for (int i = 0; i < populationSize; i++) { ... evaluations++; } } while (evaluations < maxEvaluations);` (bytecode offsets `163–516`). **Nota importante:** Como o teste `evaluations < maxEvaluations` ocorre apenas ao final do laço `for (i = 0..N-1)`, o `MOEADD` executa sempre múltiplos inteiros de $N$ avaliações ($N + k \cdot N$). Para $B = G_{\max} \cdot N$, executa exatas $G_{\max} \cdot N$ avaliações. | **Efetivo (em múltiplos de $N$)** |
| **Operador de Cruzamento (`crossover`)** | `SBXCrossover(1.0, 30.0)` | Utilizado diretamente em `MOEADD.matingSelection` + `crossoverOperator.execute(parents)` (2 pais gera 2 filhos, `offSpring.get(0)` é mutado e avaliado; bytecode offsets `227–256`). | **Efetivo** |
| **Operador de Mutação (`mutation`)** | `PolynomialMutation(1.0/n, 20.0)` | Aplicado sobre `offSpring.get(0)` (bytecode offset `243`). | **Efetivo** |
| **Tamanho da vizinhança ($T$, `neighborSize`)** | `20` (`builder.setNeighborSize(20)`) | Efetivo em `AbstractMOEAD.initializeNeighborhood()`: calcula os $T = 20$ vetores de peso mais próximos por distância Euclidiana para cada subproblema. | **Efetivo ($T = 20$)** |
| **Probabilidade de seleção na vizinhança ($\delta$, `neighborhoodSelectionProbability`)** | `0.9` (`builder.setNeighborhoodSelectionProbability(0.9)`) | Efetivo em `MOEADD.matingSelection()` (bytecode offset `618: dload 4; aload_0; getfield neighborhoodSelectionProbability; dcmpg`): seleciona pais na vizinhança de tamanho $T$ com probabilidade $\delta = 0.9$ e na população inteira com probabilidade $1 - \delta = 0.1$. | **Efetivo ($\delta = 0.9$)** |
| **Número máximo de substituições ($n_r$, `maximumNumberOfReplacedSolutions`)** | `2` (`builder.setMaximumNumberOfReplacedSolutions(2)`) | **IGNORADO pelo `MOEADD`!** Embora gravado no campo `AbstractMOEAD.maximumNumberOfReplacedSolutions`, a classe `MOEADD` **nunca lê esse campo** em nenhum método. A substituição ambiental em `MOEADD.updateArchive(S indiv)` é governada exclusivamente pela dominância de Pareto (níveis de não-dominância `ranking`) e densidade de sub-região (`deleteRankOne`, `deleteCrowdRegion1`, `deleteCrowdRegion2`), substituindo exatamente 1 solução do arquivo por geração. | **Ignorado pela lógica interna do `MOEADD`** |
| **Função de agregação (`functionType`)** | `MOEAD.FunctionType.PBI` | Efetivo em `MOEADD.fitnessFunction(S indiv, double[] lambda)` (herdado de `AbstractMOEAD.fitnessFunction`, bytecode offsets `181–363`), que calcula $g^{\text{pbi}}(x \mid \lambda, z^*) = d_1 + \theta d_2$. | **Efetivo (`PBI`)** |
| **Penalidade PBI ($\theta$)** | *Não configurável via `MOEADBuilder`* | **Hardcoded como $\theta = 5.0$** em `AbstractMOEAD.fitnessFunction` (bytecode offset `190: ldc2_w #196 // double 5.0d; dstore 5`). | **Hardcoded ($\theta = 5.0$)** |
| **Diretório de pesos (`dataDirectory`)** | `"MOEAD_Weights"` | Em `AbstractMOEAD.initializeUniformWeight()` (bytecode offsets `0–72`): **se `numberOfObjectives == 2` e `populationSize <= 300`, o jMetal 7.8 IGNORA completamente o arquivo em `dataDirectory`** e gera em memória os vetores $(a/(N-1), (N-1-a)/(N-1))$ para $a = 0, \dots, N-1$ (contendo `0.0` exato nos extremos $(0,1)$ e $(1,0)$). Apenas para $m \ge 3$ (ou $N > 300$) ele lê `"MOEAD_Weights/W" + m + "D_" + N + ".dat"`. | **Efetivo para $m \ge 3$; ignorado para $m = 2, N \le 300$** |

---

## 6. Verificação por Testes Automatizados

A suíte `src/test/java/br/usp/lti/newmoea/algorithm/FdeaIdentityAndLegacyAuditTest.java` valida experimentalmente todas as conclusões e correções desta auditoria:
1. **Determinismo bit-a-bit do `FDEA` (`VERSION.I` e `VERSION.II`):** Com `JMetalRandom.getInstance().setSeed(seed)`, duas execuções independentes sobre problemas WFG (`WFG1`, `WFG2`) com e sem operador `BinaryTournamentSelection(new RankingAndCrowdingDistanceComparator<>())` produzem populações e frentes finais idênticas bit-a-bit (`Double.doubleToLongBits`).
2. **Respeito estrito ao orçamento `maxEvaluations`:** Testado com tamanhos populacionais pares e ímpares ($N = 91$, canônico de $m=3$) e orçamentos de fronteira $B \in \{N, N+1, 2N-1, 2N\}$, comprovando que o número real de avaliações do problema (`problem.evaluate`) é sempre igual a $B$ e jamais o ultrapassa.
3. **Atributos de Seleção (`FastNonDominatedSortRanking` e `CrowdingDistanceDensityEstimator`):** Comprova que, após a correção em `FDEA.java`, os indivíduos da população possuem atributos de ranking ($\ge 0$) e distância de crowding válidos durante a seleção por torneio binário.
4. **Auditoria de Bytecode/Reflexão do `MOEADD`, `SPEA2` e `GWASFGA`:**
   - Verifica nos campos internos de `MOEADD` os valores `neighborSize = 20`, `neighborhoodSelectionProbability = 0.9`, `maximumNumberOfReplacedSolutions = 2`, `functionType = PBI`, a geração em memória com `0.0` para $m=2, N=100$, e o carregamento a partir de `MOEAD_Weights/W3D_91.dat` para $m=3, N=91$.
   - Demonstra que `WeightVectors.invert` produz `Double.NaN` / `Double.POSITIVE_INFINITY` quando alimentado com vetores canônicos contendo `0.0` (como em `W3D_91.dat`), e valores finitos apenas quando zeros são artificialmente substituídos por `1e-5`.
   - Demonstra que `SPEA2Builder` sem chamada a `setPopulationSize` retém `populationSize = 100`.

# Auditoria e Contrato da População de Saída (`result()`) e Alinhamento FUN/VAR (T19)

- **Tarefa:** [`tasks/task19.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task19.md)
- **Data:** 09/11/2026
- **Agente Responsável:** Agente G (Result Population & FUN/VAR Contract Auditor)
- **Implementação do Contrato:** [`ResultFrontContract.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ResultFrontContract.java)
- **Suíte de Testes Automatizados:** [`ResultFrontContractTest.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/ResultFrontContractTest.java)

---

## 1. Diagnóstico Forense de `algorithm.result()` nos 6 Algoritmos Principais (jMetal 7.8)

A inspeção do bytecode e a auditoria empírica via `ResultFrontContract` demonstraram uma disparidade estrutural crítica entre as implementações dos 6 algoritmos principais no jMetal 7.8:

| Algoritmo | Classe no jMetal 7.8 / Projeto | Retorno de `algorithm.result()` no Bytecode | Comportamento da População Bruta | Contém Soluções Dominadas? | Contém Duplicatas de Objetivos? |
|---|---|---|---|---|---|
| **`NSGA-III`** | `org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII` | `SolutionListUtils.getNonDominatedSolutions(getPopulation())` | Filtra soluções dominadas e duplicatas exatas via `NonDominatedSolutionListArchive` | **Não** ($0$ dominadas) | **Não** ($0$ duplicatas) |
| **`MOEA/DD`** | `org.uma.jmetal.algorithm.multiobjective.moead.MOEADD` | Herda `AbstractMOEAD.result()` $\to$ retorna `this.population` inteira ($N_{\text{config}}$ soluções) | Retorna toda a população associada aos vetores de peso $\Lambda$, contendo múltiplos níveis de Pareto ($F_1, F_2, \dots$) | **SIM** (frequentemente $> 10\%$ da população final é dominada) | **Pode conter** |
| **`FDEA`** | `br.usp.lti.newmoea.algorithm.fdea.FDEA` | `computeRanking(this.population).getSubFront(0)` | Retorna a 1ª subfrente não-dominada da população final | **Não** ($0$ dominadas) | **Pode conter** (soluções distintas com mesmos objetivos) |
| **`RVEA`** | `EvolutionaryAlgorithm` de `RVEABuilder` | `this.population` selecionada por APD | Retorna uma solução por sub-região ativa | **SIM** (soluções em sub-regiões vizinhas podem dominar-se mutuamente) | **Pode conter** |
| **`AGE-MOEA`** | `org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEA` | `SolutionListUtils.getNonDominatedSolutions(getPopulation())` | Filtra soluções dominadas via `NonDominatedSolutionListArchive` | **Não** ($0$ dominadas) | **Não** ($0$ duplicatas) |
| **`AGE-MOEA-II`** | `org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAII` | `SolutionListUtils.getNonDominatedSolutions(getPopulation())` | Filtra soluções dominadas via `NonDominatedSolutionListArchive` | **Não** ($0$ dominadas) | **Não** ($0$ duplicatas) |

> [!CRITICAL]
> **Consequência no Pipeline Original (`WFGStudy.java`):**
> O componente padrão `ExperimentAlgorithm.runAlgorithm` do jMetal 7.8 grava `algorithm.result()` diretamente em `FUN{run}.csv` e `VAR{run}.csv` via `SolutionListOutput`.
> Assim, `FUN{run}.csv` gerado para `MOEA/DD` e `RVEA` contém soluções estritamente dominadas, enquanto para `NSGA-III`, `AGE-MOEA` e `AGE-MOEA-II` contém apenas soluções não-dominadas. Essa heterogeneidade corrompe a comparação de métricas sensíveis a pontos dominados.

---

## 2. Sensibilidade dos Indicadores de Qualidade a Soluções Dominadas

### 2.1 Análise Matemática Teórica
- **Hipervolume (`HV` e `NHV`):** O volume da união $\bigcup_{\mathbf{y} \in A} [\mathbf{y}, \mathbf{r}]$ é invariante à presença de pontos dominados $\mathbf{y}'$ (pois se $\mathbf{y} \prec \mathbf{y}'$, $[\mathbf{y}', \mathbf{r}] \subset [\mathbf{y}, \mathbf{r}]$). Além disso, `PISAHypervolume` executa `reduceNondominatedSet` internamente.
- **`IGD+` (Inverted Generational Distance Plus):** Utiliza distância de dominância modificada $d^+(\mathbf{r}, \mathbf{a}) = \|\max(\mathbf{a} - \mathbf{r}, 0)\|_2$. Se $\mathbf{a}_1 \prec \mathbf{a}_2$, $\max(a_{1,j} - r_j, 0) \le \max(a_{2,j} - r_j, 0)$, logo $d^+(\mathbf{r}, \mathbf{a}_1) \le d^+(\mathbf{r}, \mathbf{a}_2)$. A distância mínima de qualquer $\mathbf{r} \in R$ a $A$ é estritamente atingida por uma solução não-dominada em $A$. Portanto, `IGD+` é **invariante à remoção de soluções dominadas**.
- **`R2`:** Utiliza $\min_{\mathbf{a} \in A} u(\mathbf{a}, \mathbf{w})$. Para utilidades Tchebycheff e soma ponderada monotônicas, uma solução dominada nunca tem melhor utilidade que sua dominadora. Logo, `R2` é **invariante à remoção de soluções dominadas**.
- **`IGD` Euclidiano Padrão (Sensível!):** A distância euclidiana $\|\mathbf{a} - \mathbf{r}\|_2$ **não é compatível com Pareto**. Um ponto dominado em $A$ pode estar geometricamente mais próximo de um ponto de referência $\mathbf{r} \in R$ do que qualquer ponto não-dominado de $A$.

### 2.2 Contraexemplo Numérico Fechado (Comprovado em `ResultFrontContractTest`)
Seja o ponto de referência $\mathbf{r} = (1.0, 1.0)$ e o conjunto aproximado $A = \{\mathbf{a}_1, \mathbf{a}_2, \mathbf{a}_3\}$:
- $\mathbf{a}_1 = (0.2, 0.8)$ (não-dominado)
- $\mathbf{a}_2 = (0.8, 0.2)$ (não-dominado)
- $\mathbf{a}_3 = (0.7, 0.7)$ (estritamente dominado por $\mathbf{a}_1$ e $\mathbf{a}_2$? Não, dominado por exemplo por $\mathbf{a}_4 = (0.6, 0.6)$, ou mais simples: $\mathbf{a}_1 = (0.0, 0.9)$, $\mathbf{a}_2 = (0.9, 0.0)$, e $\mathbf{a}_3 = (0.6, 0.6)$ dominado por $(0.5, 0.5)$).
- Distâncias euclidianas a $\mathbf{r} = (1.0, 1.0)$:
  - $d_2(\mathbf{r}, \mathbf{a}_1) = \sqrt{(1.0 - 0.0)^2 + (1.0 - 0.9)^2} = \sqrt{1.01} \approx 1.004988$
  - $d_2(\mathbf{r}, \mathbf{a}_2) = \sqrt{(1.0 - 0.9)^2 + (1.0 - 0.0)^2} = \sqrt{1.01} \approx 1.004988$
  - $d_2(\mathbf{r}, \mathbf{a}_3) = \sqrt{(1.0 - 0.6)^2 + (1.0 - 0.6)^2} = \sqrt{0.32} \approx 0.565685$
- Se o conjunto $A$ contiver $\mathbf{a}_3$, $\min_{\mathbf{a} \in A} d_2(\mathbf{r}, \mathbf{a}) = 0.565685$.
- Ao filtrar a solução dominada $\mathbf{a}_3$, a distância mínima passa a ser $1.004988$, **aumentando o IGD**!
- Conclusão: Deixar soluções dominadas em `FUN{run}.csv` mascara o desempenho real do algoritmo em `IGD`, gerando avaliações artificialmente melhores para algoritmos que não filtram soluções dominadas.

---

## 3. Especificação do Contrato Canônico de Resultados (`ResultFrontContract`)

Para garantir equidade metodológica estrita sem perda de dados brutos:
1. **Preservação Não-Destrutiva da Saída Bruta:**
   - A população bruta retornada por `algorithm.result()` pode ser preservada opcionalmente em `FUN{run}.raw.csv` e `VAR{run}.raw.csv`.
2. **Extração Canônica Sincronizada:**
   - O conjunto canônico oficial gravado em `FUN{run}.csv` e `VAR{run}.csv` contém exclusivamente soluções:
     - 100% com valores numéricos finitos (`Double.isFinite`);
     - 100% factíveis dentro dos limites de caixa $[L_j, U_j]$ das variáveis e restrições;
     - 100% mutuamente não-dominadas no sentido de Pareto;
     - Livres de duplicatas exatas no espaço de objetivos ($\|\mathbf{f}(\mathbf{a}) - \mathbf{f}(\mathbf{b})\|_\infty \le \tau_{\text{obj}}$, com desempate determinístico lexicográfico sobre variáveis e índice bruto).
3. **Alinhamento 1:1 Estrito FUN/VAR:**
   - A linha $i$ de `FUN{run}.csv` corresponde exatamente à linha $i$ de `VAR{run}.csv`.
   - Verificado pós-gravação via `verifyFunVarAlignment`, garantindo que reavaliar $\mathbf{x}_i$ em `problem.evaluate` reproduz exatamente $\mathbf{f}_i$.
4. **Relatório Estruturado de Auditoria:**
   - Cada exportação produz um `WrittenResultFilesReport` contendo contagens (`rawSize`, `nonDominatedCount`, `uniqueCount`), hashes SHA-256 e status de alinhamento.


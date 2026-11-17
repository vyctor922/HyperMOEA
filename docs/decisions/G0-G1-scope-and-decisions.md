# Decisões de Governança e Escopo Experimental — Gates G0 e G1 (T12)

- **Data**: 09/11/2026
- **Tarefa**: [`tasks/task12.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task12.md)
- **Coordenador**: [`TASK_COORDINATOR.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/TASK_COORDINATOR.md) (Seções 1–4, 5 e 6)

---

## 1. Matriz Comparativa de Escopo: Dissertação Pré-Experimento vs. Coordenador vs. HANDLER vs. `WFGStudy.java`

Conforme exigido em [`tasks/task12.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task12.md) e [`TASK_COORDINATOR.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/TASK_COORDINATOR.md) (Seção 2.1), a tabela abaixo confronta as quatro fontes para cada eixo metodológico:

| Eixo Metodológico | Dissertação Pré-Experimento (`Mestrado - Texto (pré-experimento).pdf`) | `TASK_COORDINATOR.md` (Fase 3) | `HANDLER.md` (Fases 1–3) | `WFGStudy.java` (Código Atual no Repositório) | Divergência e Ação de Reconciliação |
|---|---|---|---|---|---|
| **Algoritmos Principais da Campanha** | 5 algoritmos previstos: `NSGA-III`, `RVEA`, `FDEA`, `AGE-MOEA` e **`HypE`** | **6 algoritmos principais**: `NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA` e `AGE-MOEA-II` | Lista 6 principais (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`) + 5 legados (`NSGA-II`, `SPEA2`, `MOEA/D-DRA`, `ESPEA`, `GWASFGA`) | Instancia **11 algoritmos incondicionalmente** em `configureAlgorithmList` (`NSGAII`, `SPEA2`, `MOEADDRA`, `NSGAIII`, `MOEADD`, `FDEA`, `RVEA`, `AGEMOEA`, `AGEMOEAII`, `ESPEA`, `GWASFGA`) | **Reconciliado (G1)**: O estudo principal avalia os **6 algoritmos principais** (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`). `HypE` foi substituído por `MOEA/DD` e `AGE-MOEA-II` foi incluído (documentado em `T24` para atualizar a dissertação). Os 5 algoritmos legados devem ficar isolados atrás da flag `-Dstudy.includeLegacyAlgorithms=false` (padrão `false`) em `T14`/`T23`. |
| **Famílias de Problemas e Dimensões $m$** | `DTLZ1`–`DTLZ7` e `WFG1`–`WFG9` para $m \in \{2, 3, 5, 8, 10, 15\}$ | `WFG1`–`WFG9` e `DTLZ1`–`DTLZ7` em $m \in \{2, 3, 5, 8, 10, 15\}$, com tratamento científico explícito de `DTLZ5`/`DTLZ6` degenerados em $m > 3$ | `WFG1..9` em $m \in \{2,3,5,8,10,15\}$; `DTLZ1..7` em $m \in \{2,3\}$ por padrão até validação de frentes para $m > 3$ | `WFG1..9` habilitados para todo $m$; `DTLZ1..7` habilitados por padrão apenas para $m \le 3$ (e para $m > 3$ apenas se `-DgenerateAllReferenceFronts=true` ou `-DgenerateEmpiricalReferenceFronts=true`) | **Reconciliado (T16/G1)**: `WFG1..9` e `DTLZ1..4, 7` possuem frentes bem definidas para $m \in \{2,3,5,8,10,15\}$; `DTLZ5` e `DTLZ6` para $m \ge 4$ são documentados como benchmark complementar degenerado (Huband et al., 2006) com frente analítica 1D no jMetal 7.8. |
| **Variáveis de Decisão ($k, l, n$)** | WFG: $k = 2(m-1)$ posição, $l = 20$ distância, $n = k + l$; DTLZ: valores canônicos | WFG: $k = 2(m-1), l = 20, n = k + l$; DTLZ1: $n = m + 4$ ($k=5$); DTLZ2–6: $n = m + 9$ ($k=10$); DTLZ7: $n = m + 19$ ($k=20$) | Consistente com `StaticParams` | `WFGStudy.createProblemList`: WFG usa $k = 2(m-1), l = 20$; DTLZ1 usa $m+4$; DTLZ2–6 usam $m+9$; DTLZ7 usa $m+19$ | **100% Consistente** entre todas as fontes. |
| **Tamanhos de População ($N_m$) e Divisões ($H_1, H_2$)** | $m=2: 100$; $m=3: 91$; $m=5: 210$; $m=8: 156$; $m=10: 275$; $m=15: 135$ | $N_m \in \{100, 91, 210, 156, 275, 135\}$ com $(H_1, H_2) = (99,0), (12,0), (6,0), (3,2), (3,2), (2,1)$ | Idêntico a `StaticParams` | `StaticParams` define $N_m \in \{100, 91, 210, 156, 275, 135\}$, mas: `AGEMOEA`/`AGEMOEAII` incrementam $N$ ímpar (`92, 276, 136`); `NSGAIII` arredonda para múltiplo de 4 (`92, 212, 276, 136`); `SPEA2` usa default 100 | **A auditar e corrigir em T13**: medir e equalizar o orçamento de avaliações $B$ e documentar $N_{\text{config}}$ vs. $N_{\text{efetivo}}$. |
| **Gerações ($G_{\max}$) e Orçamento ($B$)** | $G_{\max}(m)$ por família/dimensão; $B = N_m \times G_{\max}$ | $B = N_m \times G_{\max}(m, \text{família})$ com contrato estrito de parada por avaliações | $B = \text{maxGenerations} \times \text{populationSize}$ | `StaticParams.getMaxGenerations`: WFG ($m=2: 250, m=3: 400, m=5: 750, m=8: 1500, m=10: 2000, m=15: 3000$); DTLZ1 ($400..2500$); DTLZ2/4/5/6 ($250..2000$); DTLZ3/7 ($1000..3000$). Porém `NSGA-III`, `AGE-MOEA`, `AGE-MOEA-II` e `FDEA` não param exatamente em $B$ | **A auditar e corrigir em T13**: garantir contrato de orçamento comum $B$ para todos os 6 algoritmos. |
| **Operadores Genéticos** | SBX ($p_c = 1.0, \eta_c = 30.0$) e Mutação Polinomial ($p_m = 1/n, \eta_m = 20.0$) | SBX ($1.0, 30.0$) e Mutação Polinomial ($1/n, 20.0$) | SBX ($1.0, 30.0$) e Mutação Polinomial ($1/n, 20.0$) | `new SBXCrossover(1.0, 30.0)` e `new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)` | **Consistente**, exceto que `FDEA.reproduction()` descarta o 2º filho de cada crossover e usa torneio sem calcular ranking/crowding (corrigir em `T13`/`T14`). |
| **Indicadores de Qualidade e Normalização** | `HV`, `IGD`, `IGD+`, `R2` | `HV` (primário), `NHV` (secundário/derivado), `IGD`, `IGD+`, `R2`, com contrato explícito de normalização e referência | `HV`, `NHV`, `IGD`, `IGD+`, `R2` separados | `ComputeQualityIndicators` normaliza por min/max amostral do CSV de referência e roda sem timeout; `StaticParams.getHypervolumeReferencePoint` está desconectado do `WFGStudy.main` | **A auditar e corrigir em T17 e T20**: unificar o contrato de normalização/referência e proteger HV com worker isolado + fallback Monte Carlo auditável. |
| **Inferência Estatística** | Kruskal-Wallis ($\alpha = 0,05$) com pós-teste de Dunn e ajuste de Holm | Kruskal-Wallis ($\alpha = 0,05$) + pós-teste de Dunn-Holm + tamanhos de efeito ($\varepsilon^2$, $\hat{A}_{12}$ de Vargha-Delaney, Cliff's $\delta$) | Apenas scripts padrão do jMetal (`GenerateWilcoxonTestTablesWithR`, `GenerateFriedmanTestTables`) | `WFGStudy.main` chama `GenerateWilcoxonTestTablesWithR` e `GenerateFriedmanTestTables` (não executa Kruskal-Wallis nem Dunn-Holm) | **A implementar em T21**: pipeline estatístico verificável (Kruskal-Wallis + Dunn-Holm + tamanhos de efeito) validado contra oráculo R. |

---

## 2. Classificação Obrigatória de Evidências (Seção 1 de `TASK_COORDINATOR.md`)

| Categoria | Itens Classificados nesta Auditoria (T12) |
|---|---|
| **1. Fato confirmado por código/bytecode/execução** | - Java `21.0.12.1`, Maven `3.8.8`, jMetal `7.8` confirmados.<br>- `WFGStudy.java` adiciona 11 algoritmos incondicionalmente.<br>- `StaticParams.getHypervolumeReferencePoint` e `createPrimaryHypervolume` nunca são chamados em `WFGStudy.main`.<br>- `docs/reference-fronts-dtlz.md` tinha 6 contagens de linhas erradas (`DTLZ3.3D`, `DTLZ4.3D`, `DTLZ5.2D`, `DTLZ6.2D`, `DTLZ7.2D`, `DTLZ7.3D`), já comprovadas fisicamente em `docs/reference-fronts-directory-investigation.md`.<br>- `FDEA.java` usa `new Random()` sem semente (`linha 44`), descarta `children.get(1)` (`linha 154`) e recebe `RankingAndCrowdingDistanceComparator` em `WFGStudy.java:495-496` sem computar esses atributos antes de `reproduction()`.<br>- `NSGAIII` no jMetal 7.8 arredonda `maxPopulationSize` para múltiplo de 4 e usa `(v + 1.0)/(m + 1.0)` na camada interna.<br>- `AGEMOEA` e `AGEMOEAII` incrementam populações ímpares em `WFGStudy.java:430, 444`.<br>- `PerformanceProfiler.measureOperationWithTimeout` usa `worker.interrupt()` em thread daemon, mas `PISAHypervolume` não verifica interrupção.<br>- 3 testes em `MoeaddWeightValidationTest` falhavam porque apontavam para `resources/weightVectorFiles/moead` após remoção dessa subpasta duplicada. |
| **2. Afirmação de relatório anterior ainda não verificada** | - Comportamento exato do repositório original do FDEA em `Fuzzy-Decomposition-Based-Evolutionary-Algorithm-RepositoryGITHUB/` vs. implementação local (será verificado linha a linha em `T14`).<br>- Contagem real de chamadas a `Problem.evaluate` dos 6 algoritmos em todas as dimensões $m \in \{2,3,5,8,10,15\}$ (será medida em `T13`).<br>- Determinismo bit-a-bit sob controle de `JMetalRandom` e ausência de vazamento de estado entre runs (será verificado em `T15`). |
| **3. Hipótese a testar** | - Hipótese de que `MOEADD` com $N$ vetores e `maxEvaluations = B` realiza exatamente $B$ avaliações.<br>- Hipótese de que `RVEA` (`RVEABuilder` em `jmetal-component-7.8`) respeita exatamente `TerminationByEvaluations(B)` mesmo quando a última geração excederia $B$. |
| **4. Inferência matemática** | - Para qualquer frente aproximada $A$ e frente de referência $R$ com $c = \text{HV}(R) > 0$, $\text{NHV}(A) = 1 - \text{HV}(A)/c$ é transformação estritamente decrescente de $\text{HV}(A)$, invertendo os postos ($r_i^{\text{NHV}} = N + 1 - r_i^{\text{HV}}$) e produzindo exatamente a mesma estatística $H$ de Kruskal-Wallis e os mesmos $p$-valores bilaterais de Dunn-Holm (será provado e testado numericamente em `T17` e `T21`). |
| **5. Recomendação técnica** | - Isolar algoritmos legados com `-Dstudy.includeLegacyAlgorithms=false` (padrão `false`).<br>- Adotar `ArtifactResolver` canônico com precedência explícita e manifestos SHA-256 (`T18`).<br>- Adotar hipervolume híbrido com processo isolado (`ProcessBuilder` + `destroyForcibly`) para HV exato e fallback Monte Carlo com semente fixa e IC binomial quando houver timeout (`T20`). |
| **6. Experimento pendente** | - Piloto controlado da Fase 3 (`T22`) e campanha experimental completa de 30 runs (que **não** será iniciada automaticamente sem aprovação no Gate G3). |

---

## 3. Deliberação do Gate G0 e Preparação do Gate G1

### Status do Gate G0: **APROVADO E LIBERADO**
Todos os critérios de aceitação de `T12` foram atendidos:
1. Ambiente Java 21, Maven 3.8.8 e jMetal 7.8 verificados.
2. Inventário recursivo com SHA-256 gerado em [`docs/auditoria/inventario.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/inventario.csv) (304 arquivos) e documentado em [`docs/auditoria/inventario.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/inventario.md).
3. Matriz completa de APIs reais do jMetal 7.8 extraída via `javap` e documentada em [`docs/auditoria/inventario.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/inventario.md).
4. Tarefas `T01` a `T11` reconciliadas individualmente em [`docs/auditoria/estado-tarefas.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/estado-tarefas.md), preservando o histórico em [`HANDLER.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/HANDLER.md).
5. Suíte baseline executada com proteção de timeout e diagnóstico exato registrado.
6. Escopo experimental e distinção entre os 6 fatos/categorias formalizados neste documento.

### Diretrizes para as Tarefas Seguintes (Rumo ao Gate G1 e Gate G2)
Com o Gate G0 liberado, as auditorias e implementações modulares de `T13` a `T22` podem prosseguir respeitando o isolamento de arquivos:
- **Grupo 1 (Auditorias e Contratos Independentes após G0)**:
  - `T14`: Auditoria de identidade do `FDEA` (comparando com `Fuzzy-Decomposition-Based-Evolutionary-Algorithm-RepositoryGITHUB/`), algoritmos legados e parâmetros do `MOEA/DD`.
  - `T13`: Auditoria do orçamento real $B$, tamanho efetivo de população $N_{\text{efetivo}}$ e wrapper `EvaluationBudgetProblem`.
  - `T16`: Auditoria física de todos os 160 CSVs em `pareto_fronts/csv/` e validação matemática/geração controlada de DTLZ1–7 para $m \in \{2,3,5,8,10,15\}$.
  - `T17`: Auditoria e contrato de normalização, hipervolume exato, `NHV`, `IGD`, `IGD+` e `R2`.
- **Grupo 2 (Dependentes do Grupo 1)**:
  - `T15`: Isolamento de execução, `JMetalRandom` e reprodutibilidade (`JobSpec`).
  - `T18`: Resolução canônica de artefatos (`ArtifactResolver`) e correção de `MoeaddWeightValidationTest` para a estrutura atual de `resources/weightVectorFiles/`.
  - `T19`: Contrato da população final (`result()`) e arquivos `FUN`/`VAR`.
  - `T20`: Executor híbrido de hipervolume com isolamento de processo (`destroyForcibly`) e Monte Carlo auditável.
  - `T21`: Plano e scripts de inferência estatística (Kruskal-Wallis + Dunn-Holm + tamanhos de efeito).
  - `T22`: Reauditoria do `PerformanceProfiler` e piloto controlado.
  - `T23` e `T24`: Integração final no `WFGStudy`, documentação metodológica (`docs/methodology/`) e atualização do `README.md`.


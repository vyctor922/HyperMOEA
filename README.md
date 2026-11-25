# hypeMOEA — Framework Experimental de Otimização Multi-objetivo e Hiper-heurísticas

Este projeto consiste em um ambiente experimental construído sobre o ecossistema do **jMetal 7.8** em **Java 21 (LTS)**, voltado à avaliação empírica de Algoritmos Evolutivos Multi-objetivo (MOEAs), problemas com muitos objetivos (MaOOPs) e à **adaptação e integração de mecanismos de hiper-heurísticas mono-objetivo para o gerenciamento de otimizadores no contexto multi-objetivo**.

O projeto reflete e operacionaliza a metodologia descrita na proposta de dissertação:
> **"Adaptação de Hiper-heurísticas Mono para Otimização Multi-objetivo"**  
> *Programa de Pós-Graduação em Ciência da Computação (PPGCC) — Universidade Federal do Acre (UFAC)*  
> *Autor: Victor Alexandre Lima Ribeiro | Orientador: Prof. Dr. Olacir Rodrigues Castro Junior (2026)*

---

## 1. Fundamentação Teórica: Entrada Linear da Hiper-heurística e o Indicador $R^2$

### 1.1 O Desafio Estrutural da Entrada Linear
Hiper-heurísticas de seleção concebidas originalmente para cenários mono-objetivo (notadamente **AdapHH**, **EA-ILS** e **LAST-RL**) foram projetadas sobre a premissa de um **sinal de retroalimentação escalar unidimensional**:
* Em problemas mono-objetivo, cada solução $x$ possui um custo direto $f(x) \in \mathbb{R}$. A melhoria de fitness é linear e imediata:
  $$\Delta f = f(x_{t-1}) - f(x_t)$$
* Os mecanismos de aprendizado por reforço (como no LAST-RL e AdapHH), roletas adaptativas e funções de escolha utilizam esse $\Delta f$ para atribuir recompensas ou penalidades às Heurísticas de Baixo Nível (LLHs).
* Os mecanismos Tabu baseiam-se em $\Delta f \le 0$ para aplicar penalidades de permanência (*tabu tenure*) a heurísticas ineficazes.
* Os critérios de aceitação adaptativos (como o AILLA) avaliam limiares numéricos escalares para decidir se aceitam soluções estagnadas.

No contexto multi-objetivo e de muitos objetivos (MaOO), a saída gerada a cada iteração de um MOEA não é um único número real, mas um conjunto populacional de vetores de objetivos mutuamente não-dominados ($PF_k \subset \mathbb{R}^m$). Como o espaço $\mathbb{R}^m$ não possui uma ordem total natural, a hiper-heurística requer um mecanismo que converta a qualidade desse conjunto em um sinal escalar representativo.

### 1.2 O Papel do Indicador $R^2$ como Mecanismo de Projeção Escalar
Para viabilizar a utilização direta das hiper-heurísticas mono-objetivo no gerenciamento de meta-heurísticas multi-objetivo sem descaracterizar sua lógica interna de controle, adota-se o **indicador de qualidade $R^2$** como função de projeção dimensional:

$$PF_k \longmapsto R^2(PF_k, W, z^*) \in \mathbb{R}$$

Assumindo a função escalar de Tchebycheff com um ponto de referência ideal $z^*$ e um conjunto de vetores de peso uniformemente distribuídos $W$, o indicador $R^2$ é definido como:

$$R^2(PF_k, W, z^*) = \frac{1}{|W|} \sum_{w \in W} \min_{u \in PF_k} \left\{ \max_{j \in \{1,\dots,m\}} \{ w_j \cdot |z_j^* - u_j| \} \right\}$$

em que $u$ representa o vetor de objetivos de uma solução na aproximação $PF_k$, $m$ denota o número de objetivos e $w_j$ o peso associado à $j$-ésima dimensão.

### 1.3 Mapeamento do Sinal de Recompensa
Valores menores de $R^2$ indicam maior proximidade e melhor distribuição do conjunto de soluções em relação ao ponto ideal de referência. Dessa forma, a recompensa escalar $\Delta R^2$ fornecida à camada de alto nível da hiper-heurística a cada ciclo de avaliação $t$ é dada por:

$$\Delta R^2 = R^2(PF_{t-1}) - R^2(PF_t)$$

* Se $\Delta R^2 > 0$: Houve **melhoria** na convergência e/ou diversidade da população $\to$ Atribuição de recompensa positiva à LLH aplicada, atualização de matriz de transição/Q-valores e reforço da estratégia.
* Se $\Delta R^2 \le 0$: Houve **estagnação ou piora** $\to$ Penalização, inclusão temporária da LLH na lista Tabu ou submissão ao critério adaptativo de aceitação por limiar (AILLA).

### 1.4 Coleta Durante a Execução via `R2ExecutionObserver`
* **Opt-in desativado por padrão na campanha de MOEAs isolados**: Ativável via `-Dstudy.enableR2Observer=true` (padrão `false`, com intervalo configurável em `-Dstudy.r2ObserverIntervalGenerations=1`), enquanto o indicador escalar final `R2` pós-execução é sempre calculado e exportado em todas as execuções.
* **Estritamente passivo (zero avaliações extras)**: Observa a população corrente ao término de cada geração/lote nos 6 algoritmos canônicos (`EvolutionaryAlgorithm.observable()` no `RVEA`, `BudgetCompliantMOEADD` no `MOEA/DD`, `FDEA.setGenerationPopulationListener` no `FDEA` e fronteira de lote de `AbstractGeneticAlgorithm` em `NSGA-III`, `AGE-MOEA` e `AGE-MOEA-II`), sem invocar `problem.evaluate(...)` nem consumir o orçamento $B_{\text{target}}$.
* **Histórico e Exportação**: Registra instâncias de `R2Observation(step, r2Value, deltaR2, timestampMs)` e grava trajetórias em `data/<Algoritmo>/<Problema_m>/r2_trajectory_run<run>.csv`.

---

## 2. Conjunto de Algoritmos do Estudo

O portfólio de meta-heurísticas de baixo nível (LLHs) implementado no projeto reflete a taxonomia estabelecida na proposta de dissertação, dividindo-se em duas vertentes fundamentais, com a **substituição do HypE pelo MOEA/DD** por motivos técnicos:

### 2.1 Vertente 1: Algoritmos Orientados à Qualidade do Conjunto
Esta vertente atua restaurando a pressão seletiva em problemas de alta dimensionalidade por meio de indicadores explícitos, pontos de referência ou relações fortalecidas:
* **NSGA-III** (*Deb & Jain, 2014*): Estende o NSGA-II substituindo a distância de aglomeração por nichamento baseado em pontos de referência estruturados sobre um simplex unitário (método de Das & Dennis). Reutilizado nativamente do jMetal 7.8 (`NSGAIIIBuilder`).
* **MOEA/DD** (*Li et al., 2015*): Combina a decomposição do espaço em subproblemas direcionados (baseados em vizinhança PBI) com a ordenação por dominância de Pareto, equilibrando convergência e diversidade de forma robusta. **Utilizado no projeto em substituição ao HypE**. Executado via `EvaluationBudgetProblem.BudgetCompliantMOEADD` (subclasse compatível de `MOEADD` no jMetal 7.8 para respeito estrito ao orçamento $B_{\text{target}}$).
* **FDEA** (*Liu et al., 2022*): Fuzzy-based Decomposition Evolutionary Algorithm. Introduz graus contínuos de dominância fuzzy entre soluções não-dominadas clássicas e autodecomposição do espaço de busca via amostragem por máxima similaridade (MSBS). Portado para a API do jMetal 7.8 como adaptador desacoplado (`br.usp.lti.newmoea.algorithm.fdea.FDEA`) com uso exclusivo de `JMetalRandom` semeado deterministicamente.

### 2.2 Vertente 2: Algoritmos Orientados à Diversidade Geométrica
Esta vertente preserva a diversidade recorrendo a grandezas geométricas contínuas (distâncias angulares, projeções e curvaturas):
* **RVEA** (*Cheng et al., 2016*): Reference Vector Guided Evolutionary Algorithm. Emprega a Distância Penalizada por Ângulo (APD) para avaliar convergência e alinhamento a vetores unitários, contando com mecanismo de adaptação periódica dos vetores para acomodar frentes com geometrias irregulares. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder`).
* **AGE-MOEA** (*Panichella, 2019*): Adaptive Geometry Estimation-based Many-objective Evolutionary Algorithm. Estima adaptativamente o formato e a curvatura $p$ da Fronteira de Pareto durante a busca a partir de pontos extremos, calculando escores de sobrevivência com base em proximidade geodésica. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder`).
* **AGE-MOEA-II** (*Panichella, 2020*): Variante refinada do AGE-MOEA que aprimora o cálculo de proximidade e diversidade em variedades não-euclidianas de alta dimensão. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder`).

### 2.3 Algoritmos Complementares, Orçamento Equitativo e Configuração Comum
Para fins de controle experimental de experimentos passados que já terminaram (legados) e validação estatística, o ambiente mantém:
* **MOEA/D-DRA**: MOEA/D com alocação dinâmica de recursos baseada em histórico de sucesso por subproblema.
* **NSGA-II** e **SPEA2**: Referências clássicas de ordenação por dominância e distância de aglomeração/força de Pareto.
* **ESPEA**: Algoritmo baseado no conceito de equilíbrio termodinâmico e entropia de Pareto.
* **GWASFGA**: Weighting Assessment Strategy with Reference Points.

Os algoritmos legados permanecem desativados por padrão (`-Dstudy.includeLegacyAlgorithms=false`), mantendo-se disponíveis exclusivamente para reauditoria histórica quando explicitamente habilitados.

**Parâmetros Comuns e Contratos de Execução Auditados no jMetal 7.8 (`docs/operator-audit.md`)**:
* **Cruzamento SBX (`SBXCrossover(1.0, 30.0)`)**: Probabilidade $p_c = 1.0$, índice de distribuição $\eta_c = 30.0$ e reparo de limites via `RepairDoubleSolutionWithBoundValue`.
* **Mutação Polinomial (`PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)`)**: Probabilidade efetiva por variável $p_m = 1/n$ e índice de distribuição $\eta_m = 20.0$.
* **Orçamento Equitativo Estrito (`EvaluationBudgetProblem`)**: Todos os 6 algoritmos canônicos recebem exatamente o mesmo orçamento alvo $B_{\text{target}} = N_{\text{config}}(m) \times G_{\max}(m, \text{problema})$ (rejeitando $B_{\text{target}} < N_{\text{alloc}}$). Mesmo quando `AGE-MOEA` / `AGE-MOEA-II` ajustam internamente populações ímpares para par ($N_{\text{alloc}} = N_{\text{config}} + 1$), o último lote avalia exclusivamente o saldo residual permitido e integra ao `replacement` apenas candidatos efetivamente avaliados (sendo estritamente proibido atribuir objetivos fictícios ou copiados de pais).
* **Isolamento Determinístico de Sementes e Execuções (`IsolatedJobExecutor`)**: Cada tupla `(problema, m, algoritmo, runId)` deriva uma semente determinística de 64 bits via `SHA-256` UTF-8 + `SplitMix64` a partir de `-Dstudy.baseSeed` (independente da ordem dos jobs ou de `-Dstudy.parallelWorkers`), com opção de isolamento total por subprocesso JVM (`-Dstudy.processIsolation=true`), travamento `JMetalRandom` e preservação integral de `FUN<run>.csv`/`VAR<run>.csv` entre lotes (`-DrunStart`, `-Druns`) verificada via `run_manifest.csv` (SHA-256).
* **Configuração específica dos algoritmos principais**:
  - **NSGA-III**: Camadas explícitas $(H_1, H_2)$ de Das & Dennis / Deb & Jain configuradas via `StaticParams.getOuterDivisions(m)` e `StaticParams.getInnerDivisions(m)` com validação prévia `populationSize == expectedTwoLayerPoints(m, H1, H2)`.
  - **MOEA/DD**: `MOEADBuilder.Variant.MOEADD`, escalarização `PBI` ($\theta = 5.0$ fixo em `AbstractMOEAD.fitnessFunction`), vizinhança $T = 20$, $\delta = 0.9$, $nr = 2$, com resolução e validação canônica em `resources/weightVectorFiles/` (ou `weights_gen/`) via `ReferenceSetValidator.resolveCanonicalWeightArtifactFromWeightsRoot` (sem acesso em runtime a pastas legadas).
  - **RVEA**: Direções unitárias geradas por `ReferencePointGenerator` e normalizadas na esfera $\ell_2$ ($\|\mathbf{v}_i\|_2 = 1$), com $\alpha = 2.0$ e $f_r = 0.1$.

---

## 3. Indicadores de Qualidade, Regimes de Hipervolume e Inferência Estatística

No **perfil experimental ativo homologado na Fase 5** (`WFGStudy.computeStudyIndicatorsContractually`), cada aproximação não-dominada filtrada canonicamente (`ResultFrontContract`) é avaliada sobre os seguintes indicadores em arquivos separados:

1. **$R^2$ (`org.uma.jmetal.qualityindicator.impl.R2`)** — arquivo `R2`:
   - Função utilitária de Tchebycheff com vetores de peso canônicos e ponto ideal normalizado $\mathbf{0} \in \mathbb{R}^m$. **Minimização** (menor é melhor).
2. **IGD Padrão (`org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance`)** — arquivo `IGD`:
   - Distância média Euclidiana da fronteira de referência normalizada à aproximação avaliada (reportado com a ressalva teórica de não-compatibilidade fraca de Pareto). **Minimização** (menor é melhor).
3. **IGD+ (`org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus`)** — arquivo `IGD+`:
   - Distância modificada fracamente compatível com a dominância de Pareto. **Minimização** (menor é melhor).
4. **Hipervolume Exato Isolado — `HV` (`ExactHypervolumeWorker` / `PISAHypervolume` e opcionalmente `HV_WFG` / `WFGHypervolume`)**:
   - Calculado em subprocesso JVM isolado (`ProcessIsolatedHypervolumeExecutor.computeExactOnly`) sob limite estrito de tempo de parede (`-Dperf.hvTimeoutMs=3600000` por padrão = 60 min) e encerramento recursivo de árvore de processos (`ProcessHandle.descendants()` + `destroyForcibly()`).
   - **Maximização** (maior é melhor). Em caso de *timeout* ou falha do subprocesso, grava explicitamente **`NA`** no arquivo do indicador exato e em `QualityIndicatorSummary.csv` (**jamais convertendo falha ou *timeout* em `0.0`**).
5. **Hipervolume Normalizado — `NHV` (`org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume`)**:
   - Calcula a diferença relativa em relação ao hipervolume da frente de referência $R$:
     $$\text{NHV}(A, R) = 1.0 - \frac{\text{HV}(A)}{\text{HV}(R)}$$
   - **Minimização** (`isTheLowerTheIndicatorValueTheBetter() = true`): menor valor indica melhor desempenho ($\text{NHV}(R, R) = 0.0$; assume valores negativos quando $\text{HV}(A) > \text{HV}(R)$).
6. **Hipervolume Aproximado Uniforme — `HV_MC` (`MonteCarloHypervolume`)**:
   - Série secundária homogênea calculada em todas as execuções do perfil padrão (`-Dstudy.hvMethods=PISA,MC`) com $N_{\text{MC}} = 10^6$ amostras uniformes por padrão (`-Dstudy.hvMcSamples=1000000`), semente determinística derivada por `(baseSeed, problemTag, algoritmo, m, runId)` via `SplitMix64` e intervalo de confiança binomial de Wilson a 95% registrado em `hv_audit_manifest.csv`. **Maximização** (maior é melhor).

> **Nota sobre a suspensão temporária de `NHV` no perfil ativo:** O indicador `NormalizedHypervolume` (`NHV`) encontra-se **temporariamente suspenso no perfil ativo de campanha** (não é calculado, exportado em disco nem incluído nas tabelas/análises). As classes reutilizáveis (`NormalizedHypervolume`, `ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1`), os testes matemáticos unitários e todos os artefatos históricos permanecem integralmente preservados para eventual reativação futura por decisão do operador.

### 3.1 Normalização Analítica (`THEORETICAL_PARETO_BOUNDS`) e Paridade de Caixa de Hipervolume
* **Sem *clipping* silencioso e sem dependência empírica de amostras finitas**: No perfil ativo (`-Dstudy.normalizationMode=THEORETICAL_PARETO_BOUNDS`), `ObjectiveNormalizationContract` normaliza linearmente tanto a frente de referência quanto cada `FUN<run>.csv` pelos limites analíticos exatos $[\mathbf{z}^{\text{ideal}}, \mathbf{z}^{\text{nadir}}]$ da variedade de Pareto do problema (`[0, 2j]` para `WFG1–9`, `[0, 0.5]` para `DTLZ1`, `[0, 1]` para `DTLZ2–6` e `[0, 1]^{m-1} \times [f_m^{\min}(m), 2m]` para `DTLZ7`), preservando valores fora de $[0, 1]^m$ sem truncamento artificial e auditando `points_outside_unit_box` e `points_outside_ref_point` em `hv_audit_manifest.csv`.
* **Caixa comum $[\mathbf{0}, \tilde{\mathbf{r}}]$ idêntica entre `PISA`, `WFG` e `MonteCarloHypervolume`**: A partir dos pontos de referência brutos de `StaticParams.getHypervolumeReferencePoint(problemName, m)`, o ponto de referência escalado $\tilde{\mathbf{r}}$ domina estritamente 100% da variedade ótima ($\tilde{\mathbf{r}} = (1.1, \dots, 1.1)$ em geral, $(1.55, 1.1)$ em `WFG3` $m=2$ e $(1.1, \dots, 1.1, 1.05)$ em `DTLZ7`). Em `ExactHypervolumeWorker`, o `PISAHypervolume` reescala afimmente $[\mathbf{l}, \tilde{\mathbf{r}}] \mapsto [0, 1)^m$ e multiplica pelo volume da caixa $\prod_{j=1}^m (\tilde{r}_j - l_j)$, garantindo identidade numérica ($\le 10^{-12}$) com `WFGHypervolume` e `MonteCarloHypervolume` em todas as dimensões ($m=2$ a $m=15$).

### 3.2 Política Configurável de Hipervolume e Fallback Único Generalizado (`-Dstudy.hvFallbackToMcOnAllExactTimeout`)
* A propriedade `-Dstudy.hvMethods` define os métodos avaliados (padrão `PISA,MC`; aceita combinações de `PISA`, `WFG`, `MC` e `NHV` — este último não é chamado nas execuções padrão da Fase 5, mas não é bloqueado via código caso seja solicitado explicitamente).
* Opcionalmente (*opt-in*, desativada por padrão), `-Dstudy.hvMethodsByM` permite regras ordenadas por dimensão `m` no formato `<condicao>:<metodos>;...` (ex.: `<=5:PISA,MC;>=8:MC`), mantendo `NA` explícito nos indicadores exatos omitidos acima do corte e preservando intacto o comportamento padrão quando omitida.
* **Reuso sem recomputação quando `MC` já está ativo (`PISA,MC`)**: `MonteCarloHypervolume` é executado uma única vez (`mcComputationCount == 1`); se todos os métodos exatos configurados sofrerem *timeout*/falha, o valor já obtido em `HV_MC` é reutilizado como estimativa efetiva auditada (`REUSED_MC_FALLBACK`) sem disparar segundo sorteio Monte Carlo.
* **Fallback único generalizado para configurações apenas com hipervolumes pesados (`PISA`, `WFG` ou `PISA,WFG`, sem `MC`)**: Controlado pela chave ligar/desligar `-Dstudy.hvFallbackToMcOnAllExactTimeout` (`true` por padrão):
  - Quando `true` e **todos** os hipervolumes pesados configurados atingirem o limite de tempo ou falharem na mesma execução, `MonteCarloHypervolume` é acionado **exatamente 1 vez** (`FALLBACK_MC_SINGLE_SHOT`), gravando a série separada `HV_FALLBACK_MC` e emitindo aviso explícito em `hv_fallback_warnings.md` e `hv_audit_manifest.csv` (mantendo `HV`/`HV_WFG` como `NA`).
  - Quando `false`, nenhum cálculo aproximado de contingência é executado (`NONE_FALLBACK_DISABLED`).

### 3.3 Pós-processamento Estatístico Não-Paramétrico e Validação Cruzada em R Base (`StudyStatisticalAnalyzer`)
Ao término do cálculo de indicadores, `WFGStudy.main()` executa automaticamente `StudyStatisticalAnalyzer.analyzeAndExport`:
* **Inferência por célula `(problemTag, m, indicator)` (`statistics/kruskal_wallis_cells.csv` e `statistics/dunn_holm_posthoc_pairs.csv`)**:
  - Teste global de **Kruskal–Wallis** (`KruskalWallisTest`) com *midranks*, correção exata para empates ($C = 1 - \sum (t^3 - t)/(N^3 - N)$) e tamanho de efeito $\varepsilon^2 = H / (N - 1)$.
  - Pós-teste par-a-par de **Dunn com ajuste sequencial de Holm** (`DunnPostHocTest`) para controle da taxa de erro familiar (FWER, $\alpha = 0.05$) e tamanho de efeito não-paramétrico **$\hat{A}_{12}$ de Vargha–Delaney** orientado explicitamente pela direção de otimização de cada métrica (maximização para `HV`/`HV_WFG`/`HV_MC`; minimização para `IGD`/`IGD+`/`R2`).
  - Rastreamento explícito de disponibilidade por célula (`COMPLETE`, `PARTIAL_MISSING_EXACT`, `UNAVAILABLE_ALL_NA`, `INSUFFICIENT_RUNS_FOR_INFERENCE`), sem jamais misturar valores aproximados na série exata `HV`.
* **Comparação global separada entre problemas (`statistics/friedman_global_blocks.csv` e `latex/Phase5StatisticalSummary.tex`)**: Teste de **Friedman por blocos** sobre as medianas das células `COMPLETE` (tratando cada `problemTag` como bloco independente, sem pseudo-replicação de runs individuais), acompanhado da estatística $F_F$ de Iman–Davenport.
* **Validação cruzada automática contra R 4.6.2 Base (`scripts/kruskal_dunn_holm.R`)**: Descobre automaticamente `Rscript` via `-Dstudy.rscriptPath`, `PATH`, `R_HOME` ou instalação padrão Windows (`C:\Program Files\R\R-*\bin\Rscript.exe`), executando validação cruzada 100% autocontida em **R base nativo** (`stats::kruskal.test`, Dunn analítico exato com correção de empates, `stats::p.adjust(..., method = "holm")` e $\hat{A}_{12}$, sem dependência de pacotes externos do CRAN) e gravando `statistics/r_vs_java_validation.csv` e `statistics/r_validation_status.txt`. Caso `Rscript` não esteja instalado em outro ambiente, o pipeline Java conclui normalmente registrando `RSCRIPT_NOT_AVAILABLE`.

---

## 4. Problemas de Benchmark, Resolução Canônica e Governança de Artefatos

### 4.1 Suítes Utilizadas e Hierarquia Canônica de 3 Níveis (`resources/`)
O estudo abrange as **famílias de problemas (`WFG1–WFG9` e `DTLZ1–DTLZ7`) nas 6 dimensões $m \in \{2, 3, 5, 8, 10, 15\}$**.

* **Suíte WFG (`WFG1` a `WFG9`)**: Instâncias escaláveis com fronteiras não-separáveis, deceptivas, multimodalidade, viés paramétrico e formatos mistos (convexas, côncavas, lineares e desconexas).
* **Suíte DTLZ (`DTLZ1` a `DTLZ7`)**: Problemas escaláveis com geometrias lineares (`DTLZ1`), côncavas esféricas (`DTLZ2`–`DTLZ4`), curvas 1D degeneradas (`DTLZ5`–`DTLZ6`) e $2^{m-1}$ regiões desconexas (`DTLZ7`).
  - **Comportamento padrão**: Habilitados em $m \in \{2, 3\}$ (`14` frentes oficiais validadas em `pareto_fronts/csv/DTLZ{1..7}.{2,3}D.csv`), totalizando **68 configurações de problemas ativas por padrão**.
  - **Extensão analítica opcional para $m \in \{5, 8, 10, 15\}$**: Como o repositório oficial do jMetal 7.8 não distribui frentes DTLZ para $m \in \{5, 10, 15\}$ (nem `DTLZ5/6` para $m=8$), `ParetoFrontValidator` implementa a geração matemática analítica validada para `DTLZ1–DTLZ7` em qualquer $m \in \{2, 3, 5, 8, 10, 15\}$ (`resources/referenceFronts/pf_gen/`), ativável sob demanda via `-DgenerateAllReferenceFronts=true`.

Tanto frentes de referência quanto vetores de peso obedecem à precedência estrita validada por conteúdo a seguir:
1. **Prioridade 1 — Oficial consolidado em `resources/`**: `resources/referenceFronts/{PROBLEM}.{m}D.csv` (92 frentes oficiais verificadas) e `resources/weightVectorFiles/W{m}D_{N}.dat` (29 arquivos oficiais verificados do jMetal 7.8). Candidatos fisicamente existentes mas vazios, truncados ou matematicamente inválidos são rejeitados antes da seleção.
2. **Prioridade 2 — Subdiretório de gerados validados (`_gen/`)**: `resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv` e `resources/weightVectorFiles/weights_gen/W{m}D_{N}.dat`.
3. **Prioridade 3 — Geração analítica determinística sob demanda com escrita atômica**: Acionada exclusivamente quando ausente/inválido nas prioridades anteriores, gravando com arquivo temporário + `ATOMIC_MOVE` sob trava por caminho (`FRONT_GENERATION_LOCKS` / `WEIGHT_GENERATION_LOCKS`):
   - **Geradores analíticos `WFG1`–`WFG9` (`ParetoFrontValidator.generateAndSaveAnalyticalWfgFront`)**: Implementam as geometrias exatas de Huband et al. (2006) — convexa/mista em `WFG1`, convexa desconexa filtrada por não-dominância estrita em `WFG2`, reta 1D degenerada em `WFG3` e hiper-elipsoide côncavo $\sum (f_j/(2j))^2 = 1$ em `WFG4–WFG9`.
   - **Geradores analíticos `DTLZ1`–`DTLZ7` (`ParetoFrontValidator.generateAndSaveAnalyticalDtlzFront`)**: Hiperplano linear (`DTLZ1`), esfera unitária (`DTLZ2–4`), curva 1D degenerada (`DTLZ5–6`) e variedade desconexa (`DTLZ7`).
   - **Gerador canônico de pesos Das & Dennis / Deb & Jain (`ReferenceSetValidator`)**: Gera em `weights_gen/` os vetores necessários (incluindo $m=2/N=100$, $m=5/N=210$ e $m=15/N=135$). A dispensa de arquivo em memória do jMetal 7.8 ($m = 2$ e $2 \le N \le 300$) permanece desativada por padrão (`-Dstudy.allowMoeadWeightsInMemory=false`) e tudo o que é planejado e efetivamente consumido é registrado em `<outputDir>/artifact-manifest.csv` (`PREFLIGHT_PLANNED` $\to$ `OBSERVED_CONSUMED` / `OBSERVED_EXECUTION_FAILED`).

### 4.2 Diretrizes Permanentes de Proveniência e Proteção de Artefatos
* **Imutabilidade e reversão de `MOEAD_Weights/` e proteção de pastas legadas**: Os diretórios `MOEAD_Weights/`, `pareto_fronts/` (incluindo `pareto_fronts/csv/`) e `pareto/` são **estritamente protegidos e imutáveis**. A migração anterior que havia tocado `MOEAD_Weights/` foi revertida porque se constatou que alterações haviam ocorrido naquela pasta antes de sua classificação formal como diretório legado intocável, violando a imutabilidade de artefatos históricos. O código ativo do pipeline **não lê, não busca e não faz fallback** para `MOEAD_Weights/` nem `pareto_fronts/csv/`.
* **Proibição estrita de uso da cópia externa local modificada do jMetal**: O diretório externo `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal` sofreu modificações locais e **não reflete nem a versão oficial `7.8` nem o repositório upstream atual do jMetal**. Seu uso é terminantemente proibido para extração de recursos, comparação de código ou testes; toda verificação ou referência deve basear-se exclusivamente nos artefatos Maven oficiais `7.8` e nas tags/commits oficiais do repositório GitHub (`jMetal/jMetal`).

### 4.3 Dimensionalidade dos Objetivos ($m$), Camadas e Orçamentos (`StaticParams.java`)
O estudo contempla as dimensões $m \in \{2, 3, 5, 8, 10, 15\}$, com orçamento equitativo $B_{\text{target}} = \text{maxGenerations} \times \text{populationSize}$ homologado na decisão `A1`/`A2` (incluindo `1500` e `2000` gerações para `DTLZ5`, `DTLZ6` e `DTLZ7` em $m=10$ e $m=15$):

| Objetivos ($m$) | População ($N_{\text{config}}$) | Camada Externa ($H_1$) | Camada Interna ($H_2$) | Gerações WFG1–9 | Gerações DTLZ1 / DTLZ2 / DTLZ3 / DTLZ4 / DTLZ5–7 |
|---:|---:|---:|---:|---:|---:|
| **2** | 100 | 99 | 0 | 250 | 400 / 250 / 1000 / 600 / 1000 |
| **3** | 91 | 12 | 0 | 400 | 400 / 250 / 1000 / 600 / 1000 |
| **5** | 210 | 6 | 0 | 750 | 600 / 350 / 1000 / 1000 / 1000 |
| **8** | 156 | 3 | 2 | 1500 | 750 / 500 / 1000 / 1250 / 1000 |
| **10** | 275 | 3 | 2 | 2000 | 1000 / 750 / 1500 / 2000 / 1500 |
| **15** | 135 | 2 | 1 | 3000 | 1500 / 1000 / 2000 / 3000 / 2000 |

### 4.4 Parâmetros e Propriedades JVM do Pipeline (`WFGStudy`)

| Propriedade JVM | Padrão | Domínio / Efeito no Pipeline |
|---|---|---|
| `-Dstudy.outputDirectory` | `"WFGStudy"` | Diretório raiz do estudo (suporta subcaminhos aninhados como `WFGStudy/tests/smoke_01` via `WFGStudy.resolveStudyOutputLayout`). |
| `-DrunStart` / `-Druns` | `0` / `30` | Índice inicial (`>= 0`) e quantidade (`> 0`) de repetições independentes do lote atual, preservando runs anteriores via `run_manifest.csv`. |
| `-Dstudy.baseSeed` | `20260301L` | Semente mestra de 64 bits da qual derivam deterministicamente as sementes das runs e do Monte Carlo. |
| `-Dstudy.parallelWorkers` | `1` | Número de *workers* concorrentes no `IsolatedJobExecutor` (`>= 1`). |
| `-Dstudy.processIsolation` | `false` | Quando `true`, executa cada job `(problema, m, algoritmo, runId)` em uma JVM filha isolada (`IsolatedJobExecutor --worker`). Necessário para paralelismo real sem serialização em `JMETAL_RANDOM_LOCK`. |
| `-Dstudy.workerJvmArgs` | *(ausente)* | Flags JVM exclusivas para os subprocessos *workers* (`IsolatedJobExecutor --worker` e `ExactHypervolumeWorker`, ex.: `"-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1"`) sem afetar a JVM Pai; rejeita `-Xmx < 64m`, `-Xms > -Xmx` e flags de classpath/módulo, alertando se `-Xmx < 384m`. |
| `-Dstudy.lptScheduling` | Auto (`true` se `parallelWorkers > 1`) | Ativa/desativa a fila LPT (*Longest Processing Time first*) para despachar jobs mais pesados primeiro sem alterar sementes nem ordem final do manifesto. |
| `-Dstudy.checkpointInterval` | `25` | Intervalo de consolidação atômica de `run_manifest.csv` sobre o journal `run_manifest.checkpoint.csv` durante o *checkpointing* incremental job-a-job. |
| `-Dstudy.indicatorWorkers` | `study.parallelWorkers` | Número de threads paralelas na etapa de indicadores (`computeContractualIndicatorsAndExport`), com `IGD`/`IGD+`/`R2` isolados por thread via `ThreadLocal`. |
| `-Dstudy.exactHvParallelWorkers` | `indicatorWorkers` ($m \le 5$); `min(w, 2)` ($m \ge 8$) | Limite defensivo de subprocessos simultâneos `ExactHypervolumeWorker` (`PISA`/`WFG`/`NHV`) para evitar explosão de RAM em $m \ge 8$. |
| `-Dstudy.jobTimeoutMs` | `3600000` (60 min) | *Deadline* de parede por job individual de otimização. |
| `-Dobjectives` / `-Dproblem` | Todos | Filtros opcionais por lista de dimensões (ex.: `"2,3"`) ou prefixo/tag de problema (ex.: `"WFG4"` ou `"WFG4_2"`). |
| `-Dstudy.maxEvaluationsOverride` | *(ausente)* | Sobrescreve $B_{\text{target}}$ exclusivamente para *smoke tests* rápidos em `WFGStudy/tests/`. |
| `-Dstudy.hvMethods` | `"PISA,MC"` | Conjunto de métodos de hipervolume ativos (`PISA`, `WFG`, `MC` e, se solicitada, `NHV` — sem bloqueio em código, apenas não chamada por padrão). |
| `-Dstudy.hvMethodsByM` | *(ausente)* | Roteamento opt-in de métodos de hipervolume por dimensão (ex.: `<=5:PISA,MC;>=8:MC`), preservando `NA` explícito onde métodos exatos forem omitidos. |
| `-Dstudy.hvFallbackToMcOnAllExactTimeout` | `true` | Chave ligar/desligar do fallback único `MonteCarloHypervolume` quando apenas HVs pesados são configurados e **todos** sofrem *timeout*/falha. |
| `-Dstudy.hvMcSamples` | `1000000` | Número de amostras uniformes de `MonteCarloHypervolume` ($10^6$ por padrão). |
| `-Dperf.hvTimeoutMs` | `3600000` (60 min) | *Deadline* estrito do subprocesso `ExactHypervolumeWorker` por cálculo individual de HV exato. |
| `-Dstudy.normalizationMode` | `"THEORETICAL_PARETO_BOUNDS"` | Modo do `ObjectiveNormalizationContract` (`THEORETICAL_PARETO_BOUNDS` ou `EMPIRICAL_FRONT_BOUNDS`). |
| `-Dstudy.allowMoeadWeightsInMemory` | `false` | Quando `true`, permite ao `MOEA/DD` gerar pesos em memória para $m=2, 2 \le N \le 300$ sem exigir `.dat` físico. |
| `-Dstudy.restoreOfficialJMetalResources` | `false` | Quando `true`, caso um arquivo oficial de `resources/referenceFronts/` ou `resources/weightVectorFiles/` tenha sido apagado/corrompido, tenta restaurá-lo atomicamente (a partir do backup oficial local `moead/` ou da tag oficial `jmetal-7.8` no GitHub `jMetal/jMetal`, validando conteúdo e SHA-256) antes de cair para `pf_gen/` / `weights_gen/`. |
| `-Dstudy.enableR2Observer` | `false` | Quando `true`, ativa o `R2ExecutionObserver` passivo (`0` avaliações extras) a cada `-Dstudy.r2ObserverIntervalGenerations` (padrão `1`). |
| `-Dstudy.rscriptPath` / `-Dstudy.skipRValidation` | *(auto)* / `false` | Caminho explícito opcional para `Rscript` ou desativação explícita da validação cruzada em R. |
| `-Dstudy.includeLegacyAlgorithms` | `false` | Quando `true`, inclui os 5 algoritmos legados (`NSGA-II`, `SPEA2`, `MOEA/D-DRA`, `ESPEA`, `GWASFGA`). |
| `-DprofilePerformance` | `false` | Quando `true`, executa a bateria de benchmark do `PerformanceProfiler` ao término do estudo. |

---

## 5. Estrutura de Diretórios do Projeto

```text
hypeMOEA/
├── pom.xml                                     ← Configuração Maven (Java 21, jMetal 7.8, JUnit 5, AssertJ, JaCoCo)
├── README.md                                   ← Documentação técnica e científica consolidada do projeto
├── AGENTS.md                                   ← Regras obrigatórias de governança e proteção de dados
├── TASK_COORDINATOR.md                         ← Planejamento, decisões, reservas e rastreabilidade multiagente
├── HANDLER.md                                  ← Estado operacional e histórico consolidado das Fases 1 a 5
├── tasks/                                      ← Especificações de tarefas (task1.md..task37.md)
├── scripts/
│   └── kruskal_dunn_holm.R                     ← Validação estatística autocontida em R base nativo (--fixture e estudo real)
├── docs/                                       ← Dossiê metodológico, auditorias de bytecode, protocolos e manifestos
├── MOEAD_Weights/                              ← [PROTEGIDO / SOMENTE LEITURA] Vetores históricos legados (não lido em runtime)
├── pareto_fronts/                              ← [PROTEGIDO / SOMENTE LEITURA] Frentes históricas legadas (não lido em runtime)
├── pareto/                                     ← [PROTEGIDO / SOMENTE LEITURA] Código/artefatos históricos de referência
├── WFGStudy/                                   ← Reservado a execuções finais; subpasta WFGStudy/tests/ para validações/smokes
├── resources/                                  ← Fontes canônicas ativas do pipeline (3 níveis com validação de conteúdo)
│   ├── referenceFronts/                        ← 92 CSVs oficiais WFG/DTLZ + subpastas pf_gen/ e pf_lowzero_gen/
│   ├── weightVectorFiles/                      ← 29 .dat oficiais jMetal 7.8 + subpasta weights_gen/
│   └── referencePoints/                        ← Persistência opcional de pontos NSGA-III e direções RVEA
└── src/
    ├── main/java/br/usp/lti/newmoea/           ← 27 classes de produção
    │   ├── SimpleNSGAII.java
    │   ├── algorithm/fdea/                     ← FDEA, FDEABuilder, FDEAIndividual, Most/LeastSimilarBasedSampling
    │   ├── feedback/                           ← R2ExecutionObserver e R2Observation (coleta passiva opt-in)
    │   ├── statistics/                         ← KruskalWallisTest, DunnPostHocTest e StudyStatisticalAnalyzer
    │   ├── study/                              ← StaticParams e WFGStudy (entrypoint integrado)
    │   └── util/                               ← EvaluationBudgetProblem, IsolatedJobExecutor, ResultFrontContract,
    │                                             ObjectiveNormalizationContract, ProcessIsolatedHypervolumeExecutor,
    │                                             ExactHypervolumeWorker, MonteCarloHypervolume, HypervolumeResult,
    │                                             ParetoFrontValidator, ReferenceSetValidator, PerformanceProfiler,
    │                                             ArtifactManifestEntry, ResolvedFrontArtifact, ResolvedWeightArtifact
    └── test/java/br/usp/lti/newmoea/           ← Suíte automatizada JUnit 5 (27 classes, 394 testes, 0 exclusões)
```

---

## 6. Como Rodar o Projeto

### Pré-requisitos
1. **Java Development Kit (JDK) 21 (LTS)** instalado e configurado no `JAVA_HOME`.
2. **Apache Maven 3.8+** configurado e disponível no `PATH` (`mvn -v`).
3. *(Opcional, auto-detectado)* **R 4.x+** (`Rscript`, ex.: `C:\Program Files\R\R-4.6.2\bin\Rscript.exe`) usando apenas pacotes nativos da instalação base (`stats`, `utils`), sem necessidade de pacotes externos do CRAN.

#### Checagem Rápida do Ambiente (1 Linha)
* **`Linux Bash` / `Git Bash` (Windows):**
  ```bash
  echo "=== OS & CPU ==="; uname -a; nproc; lscpu 2>/dev/null | grep -E "Model name|CPU\(s\)|Thread|Core" || true; echo "=== RAM & DISCO ==="; free -h 2>/dev/null || true; df -h . /dev/shm 2>/dev/null || df -h .; echo "=== JAVA ==="; echo "JAVA_HOME=${JAVA_HOME:-NAO_DEFINIDO}"; java -version 2>&1 || echo "java: AUSENTE"; echo "=== MAVEN ==="; mvn -v 2>&1 || echo "mvn: AUSENTE"; echo "=== R ==="; which Rscript 2>/dev/null && Rscript --version 2>&1 || echo "Rscript: AUSENTE (opcional para validacao R)"; echo "=== GIT & TMUX ==="; git --version 2>&1 || echo "git: AUSENTE"; tmux -V 2>&1 || echo "tmux: AUSENTE"
  ```
* **Windows `CMD` (`cmd.exe`):**
  ```cmd
  echo === CPU & RAM === && powershell -NoProfile -Command "$cs=Get-CimInstance Win32_ComputerSystem; $os=Get-CimInstance Win32_OperatingSystem; Write-Output ('Cores/Threads: ' + $cs.NumberOfProcessors + '/' + $cs.NumberOfLogicalProcessors + ' | RAM Livre/Total (GB): ' + [math]::Round($os.FreePhysicalMemory/1MB,2) + '/' + [math]::Round($cs.TotalPhysicalMemory/1GB,2))" && echo === JAVA === && echo JAVA_HOME=%JAVA_HOME% && java -version && echo === MAVEN === && call mvn -v && echo === R & GIT === && (where Rscript >nul 2>&1 && Rscript --version || echo Rscript: AUSENTE) && git --version
  ```

#### Instalação Rápida (Caso `java` ou `mvn` Estejam Ausentes)
* **Linux — Opção A (com `sudo`):**
  ```bash
  sudo apt update && sudo apt install -y openjdk-21-jdk-headless maven r-base-core tmux
  ```
* **Linux — Opção B (sem `sudo`, instalando JDK 21 + Maven 3.9.9 em `~/tools`):**
  ```bash
  mkdir -p ~/tools && cd ~/tools && \
  curl -fsSL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" && \
  mkdir -p jdk21 && tar -xzf jdk21.tar.gz -C jdk21 --strip-components=1 && rm jdk21.tar.gz && \
  curl -fsSL -o maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" && \
  mkdir -p maven && tar -xzf maven.tar.gz -C maven --strip-components=1 && rm maven.tar.gz && \
  echo 'export JAVA_HOME="$HOME/tools/jdk21"' >> ~/.bashrc && \
  echo 'export M2_HOME="$HOME/tools/maven"' >> ~/.bashrc && \
  echo 'export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"' >> ~/.bashrc && \
  source ~/.bashrc && java -version && mvn -v
  ```
* **Windows (`CMD` ou `Git Bash`) — Opção A (via `winget`):**
  ```cmd
  winget install EclipseAdoptium.Temurin.21.JDK && winget install Apache.Maven
  ```
* **Windows (`CMD` / PowerShell) — Opção B (sem instalador/admin, em `%USERPROFILE%\tools`):**
  ```cmd
  powershell -NoProfile -Command "$t=\"$env:USERPROFILE\tools\"; New-Item -ItemType Directory -Force -Path \"$t\jdk21\",\"$t\maven\" | Out-Null; Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile \"$t\jdk21.zip\"; Expand-Archive \"$t\jdk21.zip\" -DestinationPath \"$t\jdk21_tmp\" -Force; Move-Item \"$t\jdk21_tmp\*\*\" \"$t\jdk21\" -Force; Remove-Item \"$t\jdk21.zip\",\"$t\jdk21_tmp\" -Recurse -Force; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip' -OutFile \"$t\maven.zip\"; Expand-Archive \"$t\maven.zip\" -DestinationPath \"$t\maven_tmp\" -Force; Move-Item \"$t\maven_tmp\*\*\" \"$t\maven\" -Force; Remove-Item \"$t\maven.zip\",\"$t\maven_tmp\" -Recurse -Force" && setx JAVA_HOME "%USERPROFILE%\tools\jdk21" && setx PATH "%USERPROFILE%\tools\jdk21\bin;%USERPROFILE%\tools\maven\bin;%PATH%"
  ```
  *(Após `setx`, abra um novo terminal para carregar as variáveis).*

### Passo 1: Compilar e Empacotar o Projeto (**Windows CMD / PowerShell / Git Bash / Linux Bash**)
```bash
mvn clean compile
mvn package -DskipTests
```

### Passo 2: Executar a Suíte Integral de Testes Automatizados — Sem Exclusões (**Windows CMD / PowerShell / Git Bash / Linux Bash**)
O projeto conta com **27 classes de teste** cobrindo operadores, pontos/direções de referência, pesos do `MOEA/DD`, frentes analíticas WFG/DTLZ, orçamento equitativo $B_{\text{target}}$, isolamento de RNG/processos e retomada em lotes, normalização teórica, hipervolume isolado + fallback Monte Carlo, inferência estatística com validação cruzada em R 4.6.2 e perfilador de desempenho:
```bash
mvn clean test
```
*Saída verificada após as alterações da Fase 6*: **400 testes executados em 27 classes, 0 falhas, 0 erros e 0 ignorados (`BUILD SUCCESS`)**.

### Passo 3: Execução de Testes Rápidos de Fumaça — Sempre Isolados em `WFGStudy/tests/`
> **Importante (`AGENTS.md`)**: Nunca direcione *smoke tests*, testes exploratórios ou validações intermediárias para a raiz de `WFGStudy/`; utilize sempre subpastas de `WFGStudy/tests/` combinadas com `-Dstudy.maxEvaluationsOverride` e *deadlines* reduzidos.

* **Universal em linha única (funciona igualmente no Windows `CMD`, `PowerShell`, `Git Bash` e no `Linux Bash`)**:
```bash
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/tests/smoke_wfg4_2d" "-Dproblem=WFG4" "-Dobjectives=2" "-Dstudy.maxEvaluationsOverride=240" "-DrunStart=0" "-Druns=2" "-Dstudy.parallelWorkers=2" "-Dperf.hvTimeoutMs=30000"
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/tests/smoke_wfg4_2d" "-Dproblem=WFG4" "-Dobjectives=2" "-Dstudy.maxEvaluationsOverride=240" "-DrunStart=2" "-Druns=1" "-Dstudy.parallelWorkers=2" "-Dperf.hvTimeoutMs=30000"
```

* **Multi-linha para Windows `CMD` (`cmd.exe` — continuação com `^`)**:
```bash
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" ^
  "-Dstudy.outputDirectory=WFGStudy/tests/smoke_wfg4_2d" ^
  "-Dproblem=WFG4" "-Dobjectives=2" "-Dstudy.maxEvaluationsOverride=240" ^
  "-DrunStart=0" "-Druns=2" "-Dstudy.parallelWorkers=2" "-Dperf.hvTimeoutMs=30000"
```

* **Multi-linha para `Linux Bash` e `Git Bash` no Windows (continuação com `\`)**:
```bash
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/tests/smoke_wfg4_2d" \
  "-Dproblem=WFG4" "-Dobjectives=2" "-Dstudy.maxEvaluationsOverride=240" \
  "-DrunStart=0" "-Druns=2" "-Dstudy.parallelWorkers=2" "-Dperf.hvTimeoutMs=30000"
```

### Passo 4: Executar o Perfilador de Desempenho — `PerformanceProfiler` (**Windows CMD / PowerShell / Git Bash / Linux Bash**)
```bash
mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.summarizeOnly=true"
mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.compareHvOnly=true" "-Dperf.warmup=1" "-Dperf.runs=5"
```

### Passo 5: Campanha Experimental Completa
Para executar a campanha científica completa (6 algoritmos canônicos $\times$ 96 problemas/dimensões $\times$ 30 runs independentes = 17.280 jobs, ou particionada em lotes seguros via `-DrunStart`, `-Druns` e `-Dobjectives` em um diretório dedicado de campanha):

* **No Windows `CMD` (`cmd.exe`)**:
```bash
set MAVEN_OPTS=-Xms6g -Xmx14g
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" ^
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" ^
  "-DrunStart=0" "-Druns=30" ^
  "-Dobjectives=2,3,5,8,10,15" ^
  "-DgenerateAllReferenceFronts=true" ^
  "-Dstudy.parallelWorkers=4" ^
  "-Dstudy.processIsolation=true" ^
  "-Dperf.hvTimeoutMs=3600000" ^
  "-Dstudy.hvMethods=PISA,MC" ^
  "-Dstudy.hvMcSamples=1000000" ^
  "-DreplaceZeroWeights=false" ^
  "-DreplaceZeroFronts=false"
```

* **No `Linux Bash` ou `Git Bash` (Windows)**:
```bash
export MAVEN_OPTS="-Xms6g -Xmx14g"
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" \
  "-DrunStart=0" "-Druns=30" \
  "-Dobjectives=2,3,5,8,10,15" \
  "-DgenerateAllReferenceFronts=true" \
  "-Dstudy.parallelWorkers=4" \
  "-Dstudy.processIsolation=true" \
  "-Dperf.hvTimeoutMs=3600000" \
  "-Dstudy.hvMethods=PISA,MC" \
  "-Dstudy.hvMcSamples=1000000" \
  "-DreplaceZeroWeights=false" \
  "-DreplaceZeroFronts=false"
```

* **Alternativa recomendada em 1 linha (`-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC`, `-Dstudy.workerJvmArgs`, `-Dstudy.indicatorWorkers`, LPT e *checkpointing* incremental ativos)**:
  - *Universal (quando `MAVEN_OPTS` já estiver definido no ambiente):*
    ```bash
    mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.indicatorWorkers=10" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
    ```
  - *Windows `CMD` (`cmd.exe` — 1 linha, recomendado para notebook com `< 6 GB` livres):*
    ```cmd
    set "MAVEN_OPTS=-Xms256m -Xmx1536m -XX:+UseG1GC" && mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.indicatorWorkers=10" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
    ```
  - *`Linux Bash` ou `Git Bash` (Windows — 1 linha):*
    ```bash
    MAVEN_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC" mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.indicatorWorkers=10" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
    ```
  > *Nota de segurança:* evite configurar `-Xmx < 384m` em `-Dstudy.workerJvmArgs` (`AGE-MOEA` em $m=10$ consome até ~`395 MB`); veja o guia detalhado e o perfil de servidor Linux (`128 GB`) em [`docs/guia-execucao-otimizacao-recursos.md`](docs/guia-execucao-otimizacao-recursos.md).

### Passo 6: Localização e Interpretação dos Artefatos Gerados
Em `<outputDirectory>/` (ex.: `WFGStudy/campaign_phase6/`):
* `artifact-manifest.csv`: Manifesto auditável de frentes e pesos resolvidos (`PREFLIGHT_PLANNED` / `OBSERVED_CONSUMED`, com SHA-256).
* `run_manifest.csv` *(com journal incremental `run_manifest.checkpoint.csv` conciliado automaticamente em caso de retomada)* e `profiling/job_execution_profile.csv`: Manifesto de integridade por `runId` (orçamento real, semente, hashes `FUN`/`VAR`, *wall-clock*, CPU e RSS/Heap).
* `data/<Algoritmo>/<Problema_m>/`:
  - `FUN<run>.csv` e `VAR<run>.csv`: Fronteira final filtrada por não-dominância estrita e alinhada 1:1.
  - `HV`: Hipervolume exato (`PISAHypervolume`, maximização; `NA` se *timeout*/falha, nunca `0.0`).
  - `HV_MC`: Série secundária homogênea de hipervolume por Monte Carlo ($10^6$ amostras, maximização).
  - `HV_WFG` *(quando `WFG` incluído em `study.hvMethods`)* e `HV_FALLBACK_MC` *(quando apenas HVs pesados foram configurados e todos sofreram timeout com fallback ativo)*.
  - `IGD`, `IGD+`, `R2`: Indicadores escalares de convergência/diversidade (minimização).
* `QualityIndicatorSummary.csv`, `hv_audit_manifest.csv` e *(se aplicável)* `hv_fallback_warnings.md`.
* `statistics/`: `kruskal_wallis_cells.csv`, `dunn_holm_posthoc_pairs.csv`, `friedman_global_blocks.csv`, `r_vs_java_validation.csv` e `r_validation_status.txt`.
* `latex/` e `R/`: Tabelas LaTeX (`Phase5StatisticalSummary.tex` e resumo por indicador) e scripts R auxiliares.

---

## 7. Histórico de Alterações

Abaixo estão registradas todas as intervenções de engenharia de software e ajustes metodológicos implementados no projeto:

### Alterações 20/10/2026 - 17:00

1. **Correção de Portabilidade e Diretório Base**:
   * Removido o caminho estático macOS (`/Users/viniciusdecarvalho/PJT/NewMOEA`) de `WFGStudy.java`, substituindo-o por `System.getProperty("user.dir")`, garantindo funcionamento sem modificações manuais em qualquer ambiente (Windows, Linux, macOS).

2. **Inclusão do Algoritmo NSGA-III**:
   * Apenas o NSGA-II clássico estava instanciado na suíte então foi implementado o método `buildNSGAIII(...)` com cálculo sistemático das divisões de hiperplano ($p$) de Das & Dennis para 2, 3, 5, 8, 10 e 15 objetivos, alinhando-se à metodologia de muitos objetivos da proposta.

3. **Substituição de HypE por MOEA/DD**:
   * Atendendo à especificação temporária da pesquisa, o algoritmo **MOEA/DD** (`MOEADBuilder.Variant.MOEADD`).

4. **Expansão dos Objetivos e Parâmetros Estáticos**:
   * O estudo estava limitado a $M = \{2, 3, 5\}$ e faltava a configuração de gerações para 2 objetivos em `StaticParams.java`, foi então incluído o tratamento de $m=2$ em `StaticParams.getMaxGenerations` e expandida a lista de objetivos para $M \in \{2, 3, 5, 8, 10, 15\}$.

5. **Adequação ao Padrão Estatístico (30 Runs)**:
   * O padrão foi mudado de 5 para 30 execuções independentes (`INDEPENDENT_RUNS = 30`), com suporte a sobreposição transparente via linha de comando (`-Druns=N`).

## Estado do Projeto na Fase 1
- Pipeline principal: `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`.
- Parâmetros estáticos e fábricas de referência: `src/main/java/br/usp/lti/newmoea/study/StaticParams.java`.
- Validadores matemáticos e analíticos:
  - `src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java`
  - `src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java`
- Indicadores configurados: `PISAHypervolume` (`HV`, métrica primária de maximização), `NormalizedHypervolume` (`NHV`, métrica secundária de minimização), `InvertedGenerationalDistance` (`IGD`), `InvertedGenerationalDistancePlus` (`IGD+`) e `R2`.

## Decisões técnicas consolidadas na Fase 1
1. Populações canônicas por $m$: 100 ($m=2$), 91 ($m=3$), 210 ($m=5$)

### Alterações 21/10/2026 - 09:00

1. **Migração do Ambiente para JDK 21 e jMetal 7.8**:
   - `pom.xml` atualizado com `<maven.compiler.source>21</maven.compiler.source>`, `<maven.compiler.target>21</maven.compiler.target>`, plugin `maven-compiler-plugin:3.13.0` e dependências `jmetal-core`, `jmetal-algorithm`, `jmetal-problem`, `jmetal-lab`, `jmetal-component` elevadas para a versão estável oficial `7.8`.
   - Adicionado `maven-surefire-plugin:3.2.5` e dependência `assertj-core:3.25.3` para suíte de testes em JUnit 5.
2. **Reutilização Estrita de APIs Nativas do jMetal 7.8**:
   - Adotado `org.uma.jmetal.qualityindicator.impl.R2` nativo da biblioteca. O pipeline agora calcula automaticamente os arquivos `R2`, `HV` e `IGD+` para cada algoritmo e problema.
   - Adotado `org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder` nativo com vetores gerados por `org.uma.jmetal.util.referencepoint.ReferencePointGenerator`.
   - Adotados nativamente `AGEMOEABuilder` (versão padrão) e `AGEMOEAIIBuilder` (versão estendida não-euclidiana).
   - Adotado `org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance` nativo para IGD padrão.
3. **Porte do FDEA como Adaptador Separado para jMetal 7.8**:
   - Criado o pacote `br.usp.lti.newmoea.algorithm.fdea` contendo `FDEA`, `FDEABuilder`, `FDEAIndividual`, `MostSimilarBasedSampling` e `LeastSimilarBasedSampling`.
4. **Criação do `R2ExecutionObserver`**:
   - Criada a classe `R2ExecutionObserver` em `br.usp.lti.newmoea.feedback` com histórico de `R2Observation` e suporte a exportação CSV.
   - Resolvida a incompatibilidade do jMetal 4.x antigo e Jama, preservando a formulação matemática original de Liu et al. (2022).
5. **Suíte Completa de Testes Criada e Validada**:
   - 15 testes cobrindo integração dos algoritmos (`AlgorithmIntegrationTest`), indicadores unitários (`QualityIndicatorUnitTest`) e coleta de R² (`R2ExecutionObserverTest`), todos aprovados via `mvn test`.
6. **Suporte a Filtros de Problema e Objetivos no WFGStudy**:
   - Inclusão dos parâmetros `-Dobjectives` e `-Dproblem` para permitir execuções reduzidas e rápidas de validação experimental. 

## Alterações 03/11/2026 - 22:00

1. **Consolidação da Fase 1 (`T01`–`T05`) — Validação Matemática de Conjuntos de Referência, Pesos e Frentes de Pareto**:
   - Criados os validadores [`ReferenceSetValidator.java`](src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java) e [`ParetoFrontValidator.java`](src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java) e as suítes `NsgaIIIReferencePointsValidationTest`, `MoeaddWeightValidationTest` e `ParetoFrontValidationTest`.
   - Padronizadas as camadas explícitas $(H_1, H_2)$ de Das & Dennis / Deb & Jain em `StaticParams` e `WFGStudy.buildNSGAIII`, a validação prévia de pesos em `WFGStudy.validateMoeadWeightFile` e a separação entre `PISAHypervolume` (`HV`) e `NormalizedHypervolume` (`NHV`).
2. **Auditoria de Operadores Genéticos e Mutação Polinomial no jMetal 7.8 (`T06`)**:
   - Criados [`docs/operator-audit.md`](docs/operator-audit.md) e [`OperatorAuditTest.java`](src/test/java/br/usp/lti/newmoea/operator/OperatorAuditTest.java) (`7` testes).
   - Confirmados em bytecode os 7 construtores de `PolynomialMutation` e os 5 construtores de `SBXCrossover` em `jmetal-core-7.8.jar`, validando a probabilidade efetiva de mutação por variável $p_m = 1/n$, $\eta_m = 20.0$, $p_c = 1.0$, $\eta_c = 30.0$ e o reparo de limites `RepairDoubleSolutionWithBoundValue`.
3. **Auditoria Completa, Manifesto SHA-256 e Sincronização Fiel dos Pesos do MOEA/DD (`T11`)**:
   - Criados [`docs/moead-weights-audit.md`](docs/moead-weights-audit.md) e [`docs/moead-weights-manifest.csv`](docs/moead-weights-manifest.csv), e copiados fielmente os 29 arquivos oficiais de `jMetal/resources/weightVectorFiles/moead/` para `resources/weightVectorFiles/` e `resources/weightVectorFiles/`.
   - Provado em bytecode que `MOEA/DD` (`PBI`, `TCHE`, `AGG`) e `R2` não dividem por zero quando $w_i = 0$, enquanto `GWASFGA` (`WeightVectors.invert`) divide por zero (`1.0 / w[i][j]`), explicando a origem histórica da substituição $0 \mapsto 10^{-5}$.
   - Implementada a opção `-DreplaceZeroWeights=false` (desativada por padrão) em `ReferenceSetValidator`, quando `true`, habilita a geração de variantes de vetores de peso com substituição $0 \mapsto 10^{-5}$ em `resources/weightVectorFiles/weights_gen/`.
4. **Revalidação de NSGA-III e RVEA, Persistência Opcional e Extensão Analítica/Empírica de Frentes DTLZ (`T07`)**:
   - Criados [`docs/reference-points-audit.md`](docs/reference-points-audit.md), [`docs/reference-fronts-dtlz.md`](docs/reference-fronts-dtlz.md) e [`RveaReferenceDirectionsValidationTest.java`](src/test/java/br/usp/lti/newmoea/algorithm/RveaReferenceDirectionsValidationTest.java) (`8` testes).
   - Validada separadamente a camada externa ($H_1$) e a camada interna ($H_2$) do `NSGA-III` para $m \in \{8, 10, 15\}$ e a normalização unitária $\ell_2$ das direções do `RVEA`.
   - Derivadas e implementadas as geradoras matemáticas de `DTLZ1–DTLZ7` para $m \in \{2, 3, 5, 8, 10, 15\}$ (incluindo a curva 1D de `DTLZ5/6` e as $2^{m-1}$ regiões desconexas de `DTLZ7`) e as opções desativadas por padrão `-DpersistReferencePoints=false`, quando `true`, persiste os pontos de referência do `NSGA-III` em `resources/referencePoints/nsgaiii/`; `-DpersistReferenceDirections=false`, quando `true`, persiste as direções de referência do `RVEA` em `resources/referencePoints/rvea/`; `-DgenerateAllReferenceFronts=false`, quando `true`, gera e valida matematicamente as frentes analíticas de `DTLZ1–DTLZ7` para $m > 3$ em `resources/referenceFronts/pf_gen/` e as habilita no estudo; `-DgenerateEmpiricalReferenceFronts=false`, quando `true`, habilita a geração controlada de frentes empíricas não-dominadas em `resources/referenceFronts/pf_gen/empirical/`.

Todas as opções experimentais permanecem **desativadas (`false`) por padrão** para preservar a configuração canônica do estudo e a integridade dos diretórios originais.

5. **Auditoria de Hipervolume, Normalização e Pontos de Referência por Problema e $m$ (`T08`)**:
   - Criados [`docs/hypervolume-reference-points.md`](docs/hypervolume-reference-points.md) e [`HypervolumeAuditTest.java`](src/test/java/br/usp/lti/newmoea/qualityindicator/HypervolumeAuditTest.java) (`12` testes), e atualizados `StaticParams.java` e `QualityIndicatorUnitTest.java` (`12` testes).
   - Documentados e validados os dois regimes de hipervolume: regime normalizado em $[0, 1]^m$ no `ComputeQualityIndicators` com $\mathbf{z}_{\text{norm}} = (1.0, \dots, 1.0)$ e regime no espaço bruto em `StaticParams.getHypervolumeReferencePoint(problemName, m)` ($z_j = 2.2j$ para `WFG1–9` com $z_1 = 3.1$ em `WFG3` $m=2$, $0.6$ para `DTLZ1`, $1.1$ para `DTLZ2–6` e $(1.1,\dots,1.1,2.1m)$ para `DTLZ7`), dominando estritamente 100% dos pontos das 68 frentes ativas.

## Alterações 04/11/2026 - 10:15

1. **Instrumentação e Avaliação de Desempenho Computacional do Pipeline e Indicadores (`T09`)**:
   - Criados [`PerformanceProfiler.java`](src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java), [`PerformanceProfilerTest.java`](src/test/java/br/usp/lti/newmoea/util/PerformanceProfilerTest.java) (`8` testes), [`docs/performance/performance-protocol.md`](docs/performance/performance-protocol.md) e [`docs/performance/performance-results.csv`](docs/performance/performance-results.csv) (`630` registros = `90` execuções $\times 7$ etapas: `FRONT_LOAD`, `NORMALIZATION`, `PISAHypervolume`, `NormalizedHypervolume`, `IGD`, `IGD+`, `R2`), além da flag opcional `-DprofilePerformance=false` em `WFGStudy.java`.
   - Implementado timeout de segurança padrão de 1 hora (`DEFAULT_HV_TIMEOUT_MS = 3_600_000L` ms, configurável via `-Dperf.hvTimeoutMs`) por análise de hipervolume.
   - Documentada a sobrecarga de recálculo da frente de referência em `NormalizedHypervolume` ($m=3$), a explosão combinatória de `PISAHypervolume` em `WFG4 | MOEA/DD | m=15` ($> 40.863\text{ s}$ de CPU com $N=135$ pontos interiores) e a comparação escalonada exata entre `PISAHypervolume`, `WFGHypervolume` e `Hypervolume3D` em $m \in \{3, 8, 10, 15\}$.
2. **Validação Final, Relatório Consolidado e Atualização da Documentação (`T10`)**:
   - Criado o relatório final [`docs/final-validation-report.md`](docs/final-validation-report.md) consolidando todos os 13 itens da Seção 12 de `TASK_COORDINATOR.md`, o estado final de `T06`–`T11` e a lista explícita de limitações científicas e próximas ações.
   - Executados e validados `mvn clean compile`, `mvn clean test` (**277 testes executados em 10 classes, 0 falhas, 0 erros, 0 ignorados**) e os dois testes de fumaça fim a fim (`WFG1` em $m=2$ e `DTLZ2` em $m=3$ com `-Druns=1`), confirmando a gravação separada de `HV` e `NHV` e preservando integralmente os diretórios `MOEAD_Weights/`, `pareto_fronts/csv/` e `WFGStudy/`.
   - Atualizadas as seções 1 a 7 deste `README.md`.

## Alterações 09/11/2026 - 10:50

1. **Consolidação da Fase 3 (`T12`–`T24`) — Auditoria Forense, Orçamento, Isolamento, Hipervolume Híbrido, Contratos e Inferência Estatística**:
   - **`T12` (Governança e Inventário Forense)**: Inventário exato de 304 arquivos (`docs/auditoria/inventario.csv`), matriz de bytecode do jMetal 7.8 via `javap` e conciliação de tarefas T01–T11.
   - **`T13` (Contrato de Orçamento e População)**: Implementado [`EvaluationBudgetProblem.java`](src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java) assegurando contagem determinística do orçamento $B = \text{maxGenerations} \times N$ e parada estrita sem estouro (`17` testes em `EvaluationBudgetAuditTest`).
   - **`T14` (Identidade Algorítmica do FDEA)**: Auditado contra o repositório de Wang et al. (2016), eliminando `new Random()` não-semeado em `FDEA.java`, corrigindo torneio binário sem ranking/crowding e loop guard estrito (`28` testes em `FdeaIdentityAndLegacyAuditTest`).
   - **`T15` (Isolamento de Execução e RNG)**: Implementado [`IsolatedJobExecutor.java`](src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) com gerador determinístico SplitMix64 de 64 bits (0 colisões em 17.280 tuplas) e trava `ReentrantLock` contra condições de corrida do `JMetalRandom` (`15` testes em `IsolationAndReproducibilityTest`).
   - **`T16` (Auditoria Física e Matemática de Frentes de Pareto)**: Reconciliação dos 160 arquivos CSV de referência, validação analítica com erro máximo $< 10^{-5}$ e geração exata para DTLZ $m>3$ (`168` testes em `ReferenceFrontPhysicalAndMathAuditTest`).
   - **`T17` (Normalização e Invariância de Hipervolume)**: Prova analítica da invariância de postos de Kruskal-Wallis/Dunn entre HV e NHV, preservação de escala por jacobiano constante e cálculo em tempo constante $O(1)$ de NHV via [`ObjectiveNormalizationContract.java`](src/main/java/br/usp/lti/newmoea/util/ObjectiveNormalizationContract.java) (`37` testes em `NormalizationAndHvContractTest`).
   - **`T18` (Resolução Canônica de Recursos)**: Implementado [`ArtifactResolver.java`](src/main/java/br/usp/lti/newmoea/util/ArtifactResolver.java) com políticas canônicas de resolução de pesos e frentes, auditando 114 artefatos ativos sem quebrar o repositório (`33` testes em `ArtifactResolverTest`).
   - **`T19` (Contrato da População Final e FUN/VAR)**: Implementado [`ResultFrontContract.java`](src/main/java/br/usp/lti/newmoea/util/ResultFrontContract.java) com filtragem canônica de soluções não-dominadas, garantia de alinhamento estrito 1:1 entre FUN e VAR, remoção de duplicatas e contra-exemplo matemático provando a não-compatibilidade de Pareto do IGD euclidiano (`5` testes em `ResultFrontContractTest`).
   - **`T20` (Hipervolume Híbrido, Isolamento de Processo e Fallback Monte Carlo)**: Implementados [`ProcessIsolatedHypervolumeExecutor.java`](src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java), [`ExactHypervolumeWorker.java`](src/main/java/br/usp/lti/newmoea/util/ExactHypervolumeWorker.java) e [`MonteCarloHypervolume.java`](src/main/java/br/usp/lti/newmoea/util/MonteCarloHypervolume.java). O encerramento forçado via `destroyForcibly()` elimina threads zumbis do jMetal, e o fallback de Monte Carlo calcula o intervalo de confiança binomial de Wilson a 95% garantindo limite superior positivo para $X=0$ (`9` testes em `MonteCarloHypervolumeTest` e `ProcessIsolatedHypervolumeTest`).
   - **`T21` (Inferência Estatística Não-Paramétrica)**: Implementados [`KruskalWallisTest.java`](src/main/java/br/usp/lti/newmoea/statistics/KruskalWallisTest.java) com midranks para empates e proteção contra $C=0$, pós-teste [`DunnPostHocTest.java`](src/main/java/br/usp/lti/newmoea/statistics/DunnPostHocTest.java) com controle de FWER por Holm (Dunn-Holm), tamanhos de efeito $\varepsilon^2$ e $\hat{A}_{12}$ de Vargha-Delaney, suite em JUnit 5 (`5` testes) e script de validação [`scripts/kruskal_dunn_holm.R`](scripts/kruskal_dunn_holm.R).
   - **`T22` (Reauditoria de Desempenho e Piloto em 15 Objetivos)**: Testes de distinção entre sleep e busy-loop, isolamento de CPU de workers assíncronos e monitoramento de picos de heap em [`PerformanceProfilerPhase3Test.java`](src/test/java/br/usp/lti/newmoea/util/PerformanceProfilerPhase3Test.java) (`4` testes), com medição completa em $m=15$ sem truncamento populacional arbitrário.
   - **`T23` (Integração e Homologação)**: Atualizado [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) com ativação padrão exclusiva dos 6 algoritmos canônicos, controle de legados via `-Dstudy.includeLegacyAlgorithms=false`, suporte a diretório de saída dinâmico (`-Dstudy.outputDirectory`), preflight offline rigoroso (`WFGStudyPreflightTest.java`, `3` testes) e testes de fumaça ponta a ponta com sucesso.
   - **`T24` (Consolidação Metodológica)**: Criação do dossiê científico completo em [`docs/methodology/`](docs/methodology/) (escopo, parâmetros, benchmarks, normalização, estatística, reprodutibilidade, limitações, matriz de evidências com 20 itens auditados e proposta de retificação do texto da dissertação).

---

## Alterações 09/11/2026 - 17:00

1. **Migração, Consolidação e Priorização de Recursos em `resources/`**:
   - **Autorização Explícita e Hierarquia de 3 Níveis**: Estabelecida a precedência de carregamento: (1) `resources/`, (2) subpastas `_gen/`, (3) geração analítica/matemática por código existente.
   - **Vetores de Pesos (`resources/weightVectorFiles/`)**: Migrados e sincronizados todos os 19 arquivos complementares faltantes de `MOEAD_Weights/` (incluindo `W2D_100.dat`, `W5D_210.dat` e `W15D_135.dat`), totalizando 48 arquivos `.dat` canônicos consolidados diretamente no diretório oficial de recursos.
   - **Frentes de Pareto de Referência (`resources/referenceFronts/`)**: Consolidadas todas as 160 frentes de Pareto em CSV de `pareto_fronts/csv/` e as frentes analíticas de DTLZ em alta dimensão ($m \in \{5, 8, 10, 15\}$) de `pf_gen/`, totalizando 188 arquivos `.csv` canônicos na raiz de `resources/referenceFronts/`.
2. **Atualização do Pipeline Principal `WFGStudy.java`**:
   - Definidos `DEFAULT_WEIGHTS_DIRECTORY = "resources/weightVectorFiles"` e `DEFAULT_REFERENCE_FRONT_DIRECTORY = "resources/referenceFronts"`, configuráveis dinamicamente via `-Dstudy.weightsDirectory` e `-Dstudy.referenceFrontDirectory`.
   - Eliminado integralmente o acoplamento frágil via `Path.relativize` (`../../resources/...`): todas as frentes de WFG1–9 e DTLZ1–7 em todas as dimensões $m \in \{2, 3, 5, 8, 10, 15\}$ são registradas como nomes de arquivo diretos (`{PROBLEM}.{m}D.csv`).
   - Pré-validação estrita (`runPreflightValidation`) atualizada para verificar a integridade física e matemática dos vetores de pesos e frentes de Pareto diretamente em `resources/`.
3. **Resolução Canônica de Artefatos (`ArtifactResolver.java`)**:
   - Adicionadas as políticas `WeightSourcePolicy.RESOURCES_CONSOLIDATED` e `FrontSourcePolicy.RESOURCES_CONSOLIDATED`, com suporte à hierarquia de 3 níveis (`resources/` $\to$ `_gen/` $\to$ fallback/geração).
   - Atualizado e exportado o manifesto canônico em [`docs/auditoria/artifact-manifest.csv`](docs/auditoria/artifact-manifest.csv) contendo 120 entradas ativas auditadas com hash SHA-256 e semântica de consumo em runtime.
4. **Auditoria Forense Integral das Tasks 12 a 24 e Validação Completa**:
   - Auditoria minuciosa de cada critério de aceitação de T12 a T24 confrontado diretamente com código-fonte, testes e documentação.
   - Execução e aprovação com sucesso de toda a suíte de testes do projeto: **372 testes executados em 14 classes, 0 falhas, 0 erros, 0 ignorados** (`mvn test` com BUILD SUCCESS).
   - Validações de fumaça fim a fim aprovadas com sucesso via `WFGStudy` em modo de recursos consolidados para WFG1 ($m=2$) e DTLZ2 ($m=5$).

## Alterações 09/11/2026 - 17:35

1. **Reversão da Migração e Restauração de Diretórios Legados como Somente Leitura**:
   - **Contrato de Leitura Exclusiva**: Revertida integralmente a cópia física de arquivos de `MOEAD_Weights/` e `pareto_fronts/csv/` para `resources/weightVectorFiles/` e `resources/referenceFronts/`. Os diretórios legados `MOEAD_Weights/` e `pareto_fronts/csv/` permanecem estritamente intactos e são consumidos em modo somente leitura (*read-only*).
   - **Limpeza de `resources/`**:
     - `resources/weightVectorFiles/`: Contém exclusivamente as cópias oficiais do repositório jMetal 7.8 (29 arquivos `.dat`) e o gerado analiticamente `weights_gen/W3D_91.dat`.
     - `resources/referenceFronts/`: Contém exclusivamente a subpasta `pf_gen/` com as frentes analíticas de DTLZ em alta dimensão ($m \in \{5, 8, 10, 15\}$), geradas pelo código matemático do projeto (`ParetoFrontValidator`), sem duplicações de arquivos de `pareto_fronts/csv/`.
2. **Reversão e Ajustes no Pipeline Principal `WFGStudy.java`**:
   - Restaurados os diretórios padrão de execução: `DEFAULT_WEIGHTS_DIRECTORY = "MOEAD_Weights"` e `DEFAULT_REFERENCE_FRONT_DIRECTORY = "pareto_fronts/csv"`.
   - Preservada a geração sob demanda de frentes analíticas de DTLZ para $m > 3$ via `ParetoFrontValidator.generateAndSaveAnalyticalDtlzFront` gravando em `resources/referenceFronts/pf_gen/` e referenciadas dinamicamente via caminho relativo a partir de `pareto_fronts/csv`.
3. **Resolução Canônica de Recursos (`ArtifactResolver.java` e Testes)**:
   - Removidas as políticas de consolidação física que mesclavam pastas legadas em `resources/`. O resolver opera com auditoria de 114 manifestos canônicos ativos.
   - Atualizados [`ArtifactResolverTest.java`](src/test/java/br/usp/lti/newmoea/util/ArtifactResolverTest.java) e [`WFGStudyPreflightTest.java`](src/test/java/br/usp/lti/newmoea/study/WFGStudyPreflightTest.java) validando a consistência dos caminhos e a integridade de preflight.
4. **Validação e Homologação dos Testes**:
   - Suíte de testes unitários e de integração aprovada sem falhas nem erros.
   - Testes de fumaça ponta a ponta (`-Druns=1`) aprovados com sucesso para WFG1 ($m=2$) e DTLZ2 ($m=5$, com geração analítica da frente).

## Alterações 09/11/2026 - 23:00

1. **Auditoria Forense e Sincronização de Recursos do jMetal 7.8 e Versão Recente (7.11)**:
   - **Frentes de Pareto em CSV (`resources/referenceFronts/`)**: Copiadas as 365 frentes de Pareto oficiais em formato CSV diretamente de `jMetal/resources/referenceFrontsCSV/` para `resources/referenceFronts/`, garantindo cobertura nativa oficial de WFG1–9 ($m \in \{2, 3, 5, 8, 10, 15\}$), DTLZ1–7 ($m \in \{2, 3, 4, 6, 8\}$), ZDT1–6, UF1–10, CF1–16, ZCAT1–20, etc., preservando integralmente o subdiretório `pf_gen/` com as frentes analíticas de alta dimensão.
   - **Vetores de Pesos (`resources/weightVectorFiles/`)**: Preservados os 29 arquivos oficiais distribuídos pelo Git do jMetal 7.8/7.11 e adicionada a pasta `resources/weightVectorFiles/mombi2/` (20 itens) para suporte ao MOMBI2.
   - **Recursos Complementares do jMetal Copiados**: Sincronizadas as pastas `REnadirandidealpoints/` (64 arquivos para problemas com restrições RE21–RE91), `referenceFrontsRSG/` (86 arquivos), `referenceFrontsTSP/` (4 arquivos), `tspInstances/` (9 arquivos), `mokpInstances/` (47 arquivos), `ebes/` (5 arquivos), `fapInstances/` (1 arquivo) e `unitTestsData/` (5 arquivos).
2. **Diagnóstico da Hierarquia de Resolução (`pasta -> subpasta_gen -> gerar e salvar em **_gen`)**:
   - Mapeado o comportamento atual do código: `WFGStudy.java` ainda utiliza caminhos padrão legados (`MOEAD_Weights` e `pareto_fronts/csv`) e acoplamento frágil com `relativize` para DTLZ $m > 3$.
   - Diagnosticada a necessidade de unificação do fluxo no pipeline para seguir nativamente a precedência: (1) pasta raiz em `resources/`, (2) subpasta `_gen/` equivalente, (3) geração matemática sob demanda gravando em `**_gen`.

## Alterações 09/11/2026 - 23:55

1. **Prioridade Canônica de 3 Níveis das Frentes de Pareto de Referência**:
   - Implementada e validada em [`ParetoFrontValidator.java`](src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java), [`ArtifactResolver.java`](src/main/java/br/usp/lti/newmoea/util/ArtifactResolver.java) e [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) a hierarquia estrita:
     1. `resources/referenceFronts/{PROBLEM}.{m}D.csv` (fonte canônica prioritária);
     2. `resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv` (frentes analíticas puras complementares para dimensões não distribuídas oficialmente);
     3. Geração matemática sob demanda em `resources/referenceFronts/pf_gen/`, acionada **exclusivamente** quando o recurso estiver ausente nas fontes anteriores, prevenindo regenerações desnecessárias de frentes já existentes.
   - Eliminado qualquer acoplamento frágil com `relativize` (`../../`), fornecendo caminhos limpos relativos ao diretório configurado.

2. **Auditoria e Limpeza Forense dos Recursos em `resources/`**:
   - Removidos 9 diretórios de benchmarks e algoritmos comprovadamente fora do escopo do projeto (otimização multiobjetivo contínua sobre WFG e DTLZ): `resources/ebes/`, `resources/fapInstances/`, `resources/mokpInstances/`, `resources/referenceFrontsRSG/`, `resources/referenceFrontsTSP/`, `resources/REnadirandidealpoints/`, `resources/tspInstances/`, `resources/unitTestsData/` e `resources/weightVectorFiles/mombi2/`.
   - Removidos 273 arquivos CSV de problemas fora do escopo em `resources/referenceFronts/` (ZDT1–6, UF1–10, CF1–16, ZCAT1–20, MaF01–15, LZ09, LIRCMOP, LSMOP, RE, RWA, TSP, problemas de engenharia pontuais, etc.).
   - Preservados integralmente os recursos relevantes para o escopo:
     - As 92 frentes de referência oficiais de WFG1–9 e DTLZ1–7 em `resources/referenceFronts/`;
     - Os 29 arquivos oficiais de vetores de peso do jMetal 7.8 em `resources/weightVectorFiles/` e a subpasta `weights_gen/`;
     - Os pontos e direções de referência dos algoritmos do escopo em `resources/referencePoints/` (`nsgaiii` e `rvea`).

3. **Separação Estrita entre `pf_gen/` e `pf_lowzero_gen/` (Variantes com Substituição de Zero)**:
   - `resources/referenceFronts/pf_gen/`: reservado **exclusivamente** para frentes analíticas sem substituição de coordenadas zero (zeros matematicamente puros do simplex).
   - `resources/referenceFronts/pf_lowzero_gen/`: criado e reservado **exclusivamente** para variantes de frentes com coordenadas zero substituídas por $\epsilon = 1.0 \times 10^{-5}$ (`applyZeroReplacement`, `generateAndSaveLowZeroDtlzFront` e `createLowZeroVariantFromFile`).
   - Propriedade JVM configurável `-DreplaceZeroFronts=true` (desativada por padrão).

4. **Atualização do Pipeline Experimental `WFGStudy.java`**:
   - Atualizados os diretórios padrão de execução: `DEFAULT_REFERENCE_FRONT_DIRECTORY = "resources/referenceFronts"` e `DEFAULT_WEIGHTS_DIRECTORY = "resources/weightVectorFiles"`.
   - `resolveReferenceFrontForStudy` e `resolveMoeadDataDirectory` garantem resolução canônica em 3 níveis e compatibilidade com todas as 6 dimensões ativas ($m \in \{2, 3, 5, 8, 10, 15\}$).

5. **Evidências e Homologação de Testes**:
   - Criada a suíte de testes [`ReferenceFrontCanonicalPriorityTest.java`](src/test/java/br/usp/lti/newmoea/qualityindicator/ReferenceFrontCanonicalPriorityTest.java) (6 testes, 100% aprovados).
   - Executada a suíte de testes completa do projeto via `mvn test`: **378 testes executados, 0 falhas, 0 erros** (BUILD SUCCESS).

## Alterações 11/11/2026 - 14:55

1. **Refatoração Arquitetural e Resolução Canônica de Artefatos (Fase 4 / T25–T31)**:
   - **Eliminação Completa de Fallbacks Operacionais**:
     - Removido qualquer acesso ou tentativa de busca aos diretórios legados protegidos (`MOEAD_Weights/` e `pareto_fronts/csv/`) em [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java), [`ParetoFrontValidator.java`](src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java), [`PerformanceProfiler.java`](src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java) e [`EvaluationBudgetProblem.java`](src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java).
     - Os arquivos físicos históricos permanecem estritamente preservados e protegidos contra modificações.
   - **Resolução e Geração Canônica de Pesos do MOEA/DD**:
     - Implementada a resolução canônica em [`ReferenceSetValidator.resolveCanonicalWeightFile`](src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java): (1) oficial em `resources/weightVectorFiles/`, (2) gerado em `weights_gen/`, (3) geração programática sob demanda gravando em `weights_gen/` e validando os vetores segundo Das & Dennis zero-replaced ($0 \mapsto 10^{-5}$) para as dimensões $m=5$ ($N=210$) e $m=15$ ($N=135$).
   - **Retornos Estruturados e Hermeticidade**:
     - Criados os records imutáveis [`ResolvedFrontArtifact.java`](src/main/java/br/usp/lti/newmoea/util/ResolvedFrontArtifact.java) e [`ResolvedWeightArtifact.java`](src/main/java/br/usp/lti/newmoea/util/ResolvedWeightArtifact.java).
     - Adicionado suporte a injeção explícita de `Path baseDir` para hermeticidade de testes unitários sem dependência de mutação global de `user.dir`.
   - **Centralização de Primitivas Matemáticas e Manifesto**:
     - Centralizada a comparação de dominância pura de Pareto em [`ParetoFrontValidator.dominanceComparison`](src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java), com delegação direta a partir de [`ResultFrontContract.java`](src/main/java/br/usp/lti/newmoea/util/ResultFrontContract.java).
     - Migrada a regra de geração em memória jMetal 7.8 (`isMoeadWeightGeneratedInMemory`) e a exportação do manifesto consolidado de artefatos ([`ArtifactManifestEntry.java`](src/main/java/br/usp/lti/newmoea/util/ArtifactManifestEntry.java)) para [`ReferenceSetValidator.java`](src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java).
   - **Exclusão Segura e Condicionada do `ArtifactResolver`**:
     - Comprovada a ausência de referências reflexivas ou imports de produção. Criada a suíte substituta [`CanonicalArtifactResolutionAndManifestTest.java`](src/test/java/br/usp/lti/newmoea/util/CanonicalArtifactResolutionAndManifestTest.java).
     - Excluídos definitivamente `ArtifactResolver.java` e `ArtifactResolverTest.java`.
   - **Homologação e Testes**:
     - Execução limpa completa via `mvn clean test`: **370 testes executados em 23 classes com 100% de aprovação (0 falhas, 0 erros, 0 ignorados)**.
     - Campanha experimental mantida desativada, respeitando a integridade do protocolo.

## Alterações 18/11/2026 - 03:40

1. **Integração Pré-Campanha, Tratamento da Auditoria e Homologação da Fase 5 (`T32`–`T37`, absorvendo `T32` e `T33`)**:
   - **`T32` (Reconciliação de Baseline, Saída Aninhada e Testes)**:
     - Implementado `WFGStudy.resolveStudyOutputLayout` em [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) separando diretório pai e nome simples de experimento para suportar `-Dstudy.outputDirectory` em caminhos aninhados (como `WFGStudy/tests/...`) sem quebrar os geradores de LaTeX e R do jMetal 7.8.
     - Reconciliados os testes de pesos em [`MoeaddWeightValidationTest.java`](src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java) distinguindo fontes canônicas ativas (`resources/weightVectorFiles/`), auditoria forense somente leitura e manifesto SHA-256 oficial, e criada [`StudyOutputDirectoryAndGenerationsTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyOutputDirectoryAndGenerationsTest.java).
   - **`T33` + `T32` (Orçamento Equitativo $B_{\text{target}}$, Isolamento Determinístico e Preservação de Lotes)**:
     - Ajustadas as gerações de `DTLZ5` e `DTLZ6` em [`StaticParams.java`](src/main/java/br/usp/lti/newmoea/study/StaticParams.java) para `{1000, 1000, 1000, 1000, 1500, 2000}` em $m \in \{2, 3, 5, 8, 10, 15\}$ (decisão `A1`).
     - Integrado [`EvaluationBudgetProblem.java`](src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java) (`BudgetCompliantMOEADD`, reserva atômica, proibição de objetivos fictícios/copiados e rejeição de $B < N_{\text{alloc}}$) e [`IsolatedJobExecutor.java`](src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) (`executeAndPersistBatch`, semente determinística `SHA-256` + `SplitMix64`, isolamento opcional por subprocesso `-Dstudy.processIsolation` e retomada segura entre lotes `-DrunStart`/`-Druns` verificada via `run_manifest.csv` sem sobrescrever runs anteriores), cobertos por [`StudyBudgetIsolationAndResumeIntegrationTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyBudgetIsolationAndResumeIntegrationTest.java).
   - **`T34` (Geradores Analíticos `WFG1–WFG9`, Validação de Conteúdo, Travas Concorrentes e Manifesto Efetivo)**:
     - Implementados os geradores analíticos exatos para `WFG1–WFG9` na Prioridade 3 de [`ParetoFrontValidator.java`](src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java) (convexa/mista em `WFG1`, desconexa filtrada por não-dominância em `WFG2`, linear 1D degenerada em `WFG3` e hiper-elipsoide côncavo em `WFG4–WFG9`), validação de conteúdo prévia à seleção nas Prioridades 1 e 2 e travas por caminho canônico (`FRONT_GENERATION_LOCKS` / `WEIGHT_GENERATION_LOCKS`) com escrita atômica.
     - Corrigida a fronteira $m = 2 \land 2 \le N \le 300$ em [`ReferenceSetValidator.java`](src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java), implementada `-Dstudy.allowMoeadWeightsInMemory=false` e exportação pré/pós-execução de `artifact-manifest.csv` (`PREFLIGHT_PLANNED` / `OBSERVED_CONSUMED`).
     - Documentadas permanentemente neste `README.md` (Seção 4.2) a justificativa da reversão e imutabilidade estrita de `MOEAD_Weights/` e a proibição de uso da cópia externa modificada `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`.
   - **`T35` (Normalização Analítica, Paridade Exata de Hipervolume, Suspensão Temporária de `NHV` e Fallback Único Configurável)**:
     - Integrado o cálculo contratual de indicadores em [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) sobre `ResultFrontContract` + `ObjectiveNormalizationContract` (`THEORETICAL_PARETO_BOUNDS`), com **suspensão temporária de `NHV` no perfil ativo** (decisão `A3`, preservando classes, testes e histórico).
     - Corrigida em [`ExactHypervolumeWorker.java`](src/main/java/br/usp/lti/newmoea/util/ExactHypervolumeWorker.java) a paridade exata de caixa $[\mathbf{l}, \tilde{\mathbf{r}}]$ entre `PISAHypervolume`, `WFGHypervolume` e `MonteCarloHypervolume` em todas as dimensões, eliminação recursiva de subprocessos em [`ProcessIsolatedHypervolumeExecutor.java`](src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java) (incluindo extração de `URLClassLoader` para `mvn exec:java`), gravação estrita de `NA` em *timeout*/falha (nunca `0.0`), reuso sem recomputação de `HV_MC` e opção ligar/desligar `-Dstudy.hvFallbackToMcOnAllExactTimeout` para disparo único (`FALLBACK_MC_SINGLE_SHOT`) quando apenas HVs pesados atingem *timeout*, validados em [`StudyIndicatorPipelineIntegrationTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyIndicatorPipelineIntegrationTest.java).
   - **`T36` (Inferência Estatística Integrada, Validação Cruzada em R 4.6.2 Base e Observador $R^2$ Passivo)**:
     - Criado [`StudyStatisticalAnalyzer.java`](src/main/java/br/usp/lti/newmoea/statistics/StudyStatisticalAnalyzer.java) (Kruskal–Wallis + Dunn–Holm + $\hat{A}_{12}$ orientado por célula com estados de disponibilidade + Friedman por blocos entre problemas + `Phase5StatisticalSummary.tex`) e adaptado [`scripts/kruskal_dunn_holm.R`](scripts/kruskal_dunn_holm.R) em R 4.6.2 base nativo (`stats`, sem pacotes CRAN externos).
     - Integrados o `R2ExecutionObserver` passivo opt-in (`0` avaliações extras nos 6 algoritmos, incluindo `FDEA.setGenerationPopulationListener`) e o perfilamento de recursos por job (`logs/execution_profile_runs.csv`), cobertos por [`StudyStatisticalAndProfilingIntegrationTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyStatisticalAndProfilingIntegrationTest.java).
   - **`T37` + `T33` (Reauditoria Zero-Trust Reversa e Aceite Fim-a-Fim)**:
     - Reauditoria reversa (`T36` $\to$ `T32` e `T32`/`T31` $\to$ `T01`), empacotamento JAR, *smokes* reais via CLI (`mvn exec:java`) em `WFGStudy/tests/` (2D multi-lote com isolamento de processo + 15D sob *deadline*) e suíte integral sem exclusões aprovada via `mvn clean test`: **393 testes executados em 27 classes, 0 falhas, 0 erros, 0 ignorados (`BUILD SUCCESS`)**, com `0` alterações nos diretórios protegidos e campanha completa mantida sob autorização humana explícita.

## Alterações 18/11/2026 - 10:15

1. **Fallback Opcional de Restauração de Recursos Oficiais do jMetal 7.8 (`-Dstudy.restoreOfficialJMetalResources=false`)**:
   - Implementado em [`ReferenceSetValidator.java`](src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java) (`tryRestoreOfficialJMetalWeightFile`) e [`ParetoFrontValidator.java`](src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java) (`tryRestoreOfficialJMetalReferenceFront`) o mecanismo opcional — **desativado (`false`) por padrão** — para restaurar atomicamente arquivos oficiais de `resources/weightVectorFiles/` (a partir do espelho local verificado `moead/` ou da tag oficial `jmetal-7.8` no GitHub `jMetal/jMetal`, validando o hash SHA-256 contra [`docs/moead-weights-manifest.csv`](docs/moead-weights-manifest.csv)) e de `resources/referenceFronts/` (a partir da tag oficial `jmetal-7.8` no GitHub `jMetal/jMetal`, validando a estrutura matemática antes da publicação atômica), bloqueando terminantemente qualquer acesso às pastas legadas protegidas (`MOEAD_Weights/`, `pareto_fronts/`, `pareto/`) ou à cópia externa local modificada `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`.
   - Adicionado o teste hermético `testOptionalRestoreOfficialJMetalResourcesFallback` em [`CanonicalArtifactResolutionAndManifestTest.java`](src/test/java/br/usp/lti/newmoea/util/CanonicalArtifactResolutionAndManifestTest.java) (`11/11` testes aprovados na classe; suíte completa do projeto com **394 testes em 27 classes, 0 falhas, 0 erros**).

## Alterações 18/11/2026 - 11:45

1. **Portabilidade Multiplataforma Windows/Linux (`.gitattributes`, `LF`/`CRLF` SHA-256 e Comandos de Terminal)**:
   - Criado [`.gitattributes`](.gitattributes) na raiz do repositório fixando `eol=crlf` nos artefatos científicos auditados por SHA-256 (`resources/weightVectorFiles/**/*.dat`, `resources/referenceFronts/**/*.csv`, `MOEAD_Weights/**/*.dat`, `pareto_fronts/**/*`) e `eol=lf` em scripts `*.R` e `*.sh`.
   - Adicionado `ReferenceSetValidator.matchesExpectedSha256CrossPlatform` em [`ReferenceSetValidator.java`](src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java) e [`MoeaddWeightValidationTest.java`](src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java), garantindo verificação de integridade SHA-256 invariante entre `LF` e `CRLF` tanto em `git clone` no Linux quanto em downloads diretos do GitHub raw.
   - Padronizados os comandos de terminal neste `README.md`, em [`docs/pipeline-pos_processamento.md`](docs/pipeline-pos_processamento.md), [`docs/relatorio_auditoria-pre_execucao_final.md`](docs/relatorio_auditoria-pre_execucao_final.md) e [`docs/relatorio_auditoria_zerotrust_pos_fase5.md`](docs/relatorio_auditoria_zerotrust_pos_fase5.md) com exemplos explícitos para **Windows `CMD` (`cmd.exe`)** e **`Linux Bash` / `Git Bash`**.

## Alterações 20/11/2026 - 12:35

1. **Roteamento Opt-in de Métodos de Hipervolume por Dimensão (`-Dstudy.hvMethodsByM`)**:
   - Implementados [`HvMethodsRule`](src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java), `parseHvMethodsByMRules`, `resolveHvMethodsByObjectiveCount` e `formatHvMethodsLabel` em [`ProcessIsolatedHypervolumeExecutor.java`](src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java), suportando regras ordenadas `<condicao>:<metodos>;...` em ordem de avaliação (`<=`, `<`, `>=`, `>`, `=`, `==`, `a-b`, listas `a,b`, prefixos `le`/`lt`/`ge`/`gt`/`eq` e curingas `*`/`default`/`else`/`all`), falhando antecipadamente em regras malformadas, tokens desconhecidos ou dimensões descobertas sem fallback explícito.
   - Integrada a propriedade opt-in `WFGStudy.HV_METHODS_BY_M_PROPERTY` (`-Dstudy.hvMethodsByM`, desativada por padrão mantendo integralmente `-Dstudy.hvMethods`/`-Dstudy.hvPolicy` e `-Dperf.hvTimeoutMs=3600000` inalterados) em [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) (`resolveConfiguredHvMethodsByM`, `computeContractualIndicatorsAndExport`, `recordExactHvSummarySeriesRow` e `ensureInactiveHvSummaryRowsNa`). Nos indicadores exatos omitidos em dimensões superiores (ex.: `<=5:PISA,MC;>=8:MC`), registra explicitamente `NA` em `HV`/`HV_WFG`, `NOT_CONFIGURED` em `hv_audit_manifest.csv` e preserva a análise por disponibilidade em [`StudyStatisticalAnalyzer.java`](src/main/java/br/usp/lti/newmoea/statistics/StudyStatisticalAnalyzer.java).
2. **Remoção dos Bloqueios via Código sobre `NHV` (Decisão do Operador em 20/11/2026)**:
   - Removido qualquer bloqueio ou exclusão forçada via código para `NHV`: adicionada a opção `HvMethod.NHV` em [`ProcessIsolatedHypervolumeExecutor.java`](src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java) (sem lançar exceção quando solicitada), removidas a exclusão automática de `NHV` (`Files.deleteIfExists` / `summaryRowsByKey.remove`) e adicionada a derivação opcional via `ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1` em [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) exclusivamente quando `NHV` for solicitada pelo operador, e removida a filtragem forçada de `NHV` em [`StudyStatisticalAnalyzer.java`](src/main/java/br/usp/lti/newmoea/statistics/StudyStatisticalAnalyzer.java).
   - Na fase atual, `NHV` permanece inativo apenas por **não ser chamado** no padrão (`PISA,MC`) nem nos comandos da campanha (`<=5:PISA,MC;>=8:MC`).
3. **Documentação de Comandos em 1 Linha e Análise Empírica de `m >= 8` com `PISA`**:
   - Adicionadas alternativas em linha única usando `-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC` neste `README.md`, em [`docs/guia-execucao-otimizacao-recursos.md`](docs/guia-execucao-otimizacao-recursos.md), [`docs/pipeline-pos_processamento.md`](docs/pipeline-pos_processamento.md) e [`docs/relatorio_auditoria_zerotrust_pos_fase5.md`](docs/relatorio_auditoria_zerotrust_pos_fase5.md).
   - Registrada em [`docs/guia-execucao-otimizacao-recursos.md`](docs/guia-execucao-otimizacao-recursos.md) (Seção 1, item 5 e Seção 2.4) a análise empírica observada na execução `WFGStudy/campaign_phase6` (`19–20/11/2026`): tempos por dimensão abaixo de `8D` (`2D`: `15 min`, máx. `10,51 s`/célula; `3D`: `16 min`, máx. `14,34 s`/célula; `5D`: `34 min`, máx. `47,35 s`/célula em `MOEADD/WFG7_5`) vs. explosão em `8D` (`NSGAIII/WFG1_8`: `11m42s`; `MOEADD/WFG1_8`: `~38 min` e `89,5 min` em runs isoladas), fundamentando por que manter `PISA` em `m >= 8` sob `hvTimeoutMs=3600000` sequencial é proibitivo.
4. **Validação e Testes**:
   - Atualizados e adicionados testes em [`StudyIndicatorPipelineIntegrationTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyIndicatorPipelineIntegrationTest.java) (`6/6` aprovados, cobrindo `-Dstudy.hvMethodsByM`, ausência de `NHV` no padrão `PISA,MC` e cálculo completo quando `NHV` é solicitada) e [`StudyStatisticalAndProfilingIntegrationTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyStatisticalAndProfilingIntegrationTest.java) (`3/3` aprovados).
   - Executada a suíte integral sem exclusões via `mvn clean test`: **396 testes executados em 27 classes, 0 falhas, 0 erros, 0 ignorados (`BUILD SUCCESS`)**.

## Alterações 20/11/2026 - 18:30

1. **Fase 6 (Pré-Execução Final antes das Hiper-heurísticas) — Opção 1 (*Checkpointing* Incremental Job-a-Job e Compactação de Heap)**:
   - Implementada em [`IsolatedJobExecutor.java`](src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) a coleta incremental via `ExecutorCompletionService<JobExecutionResult>` (`runPendingJobsIncremental`, `IncrementalCheckpointSession`, `persistSingleJobResult`, `buildOrderedManifestEntries`, `reconcileCheckpointJournalIfPresent`), gravando imediatamente `FUN<r>.csv`, `VAR<r>.csv`, `.raw.csv` e o journal `run_manifest.checkpoint.csv` com consolidação atômica em `run_manifest.csv` a cada `-Dstudy.checkpointInterval` jobs (`25` por padrão).
   - Adicionado `JobExecutionResult.compactRetainingMetadataOnly()` para substituir imediatamente matrizes pesadas em RAM por `EMPTY_MATRIX` mantendo escalares e SHA-256 para `profiling/job_execution_profile.csv`, além de liberar `batchReport = null` em [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) antes da etapa de indicadores.
   - **Defesas e correção crítica de concorrência:** tolerância restrita à eventual última linha truncada do journal sem jamais dispensar `verifyExistingRunForResume` (hashes SHA-256/`VAR -> FUN`); e criação de [`ResultFrontContract.createUnseededSolution`](src/main/java/br/usp/lti/newmoea/util/ResultFrontContract.java) (`UnseededDoubleSolution`), substituindo `problem.createSolution()` em `reconstructSolutions`, `verifyFunVarAlignment` e `LazyIsolatedAlgorithmAdapter` para impedir chamadas fora do `JMETAL_RANDOM_LOCK` ao gerador global sob concorrência.
2. **Fase 6 — Opção 3 (Fila LPT Determinística e Paralelização Segura da Etapa de Indicadores)**:
   - Adicionado escalonamento determinístico LPT (`orderJobSpecsLpt`, `estimateRelativeJobCost`) via `-Dstudy.lptScheduling` (padrão automático `true` quando `maxWorkers > 1`) sem alterar sementes nem a ordem canônica do manifesto consolidado.
   - Paralelizada `computeContractualIndicatorsAndExport` em [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) via `-Dstudy.indicatorWorkers` (padrão igual a `study.parallelWorkers`), com despacho *heaviest-first* e reconstrução em ordem canônica em `QualityIndicatorSummary.csv`, `hv_audit_manifest.csv` e arquivos por célula.
   - **Defesas:** instâncias isoladas por thread via `ThreadLocal<IndicatorCalculators>` (evitando data race em `R2.compute` do jMetal 7.8, que muta `weightVectors` e `idealPoint`) com `.remove()` no `finally`; avaliação única de `hvRefUnit` por problema antes do despacho; e semáforos `lowDimExactPermits`/`highDimExactPermits` via `-Dstudy.exactHvParallelWorkers` (padrão máximo `min(w, 2)` para $m \ge 8$) contra pressão de memória em HVs exatos.
3. **Fase 6 — Opção 5a (`-Dstudy.workerJvmArgs` Exclusivo para Subprocessos Workers)**:
   - Implementados `parseWorkerJvmArgs` e `resolveConfiguredWorkerJvmArgs` em [`ProcessIsolatedHypervolumeExecutor.java`](src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java), integrados em `computeExactOnly`, `IsolatedJobExecutor.executeJobInSubprocess` e na validação antecipada de [`WFGStudy.main()`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java).
   - **Defesas:** parsing com suporte a aspas e caminhos Windows (`\`), rejeição *fail-fast* de aspas abertas, tokens sem `-`, flags controladas pelo supervisor (`-cp`, `-classpath`, `--class-path`, `-jar`, `-m`, `--module`, `-p`, `--module-path`, `-version`, `--help`), `-Xmx < 64m` ou `-Xms > -Xmx`, e aviso preventivo quando `-Xmx < 384 MiB`.
4. **Atualização Documental e Status de Itens Adiados**:
   - Atualizados este `README.md`, [`HANDLER.md`](HANDLER.md), [`docs/guia-execucao-otimizacao-recursos.md`](docs/guia-execucao-otimizacao-recursos.md) e [`docs/pipeline-pos_processamento.md`](docs/pipeline-pos_processamento.md) detalhando defesas e recomendações operacionais/científicas (permanecendo adiadas as opções `2`, `4` e `5b` da Seção 4.2, bem como o disparo completo da campanha e a etapa futura de hiper-heurísticas).
5. **Validação e Testes**:
   - Adicionados testes integrados da Fase 6 em [`StudyBudgetIsolationAndResumeIntegrationTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyBudgetIsolationAndResumeIntegrationTest.java) (`8/8` aprovados) e [`StudyIndicatorPipelineIntegrationTest.java`](src/test/java/br/usp/lti/newmoea/study/StudyIndicatorPipelineIntegrationTest.java) (`8/8` aprovados, comprovando defesas de `workerJvmArgs`, limites de `exactHvParallelWorkers`, recuperação tolerante no journal e paridade bit-a-bit entre execução sequencial e paralela com `parallelWorkers=2, lptScheduling=true, checkpointInterval=2, indicatorWorkers=4`).
   - Executada a suíte integral sem exclusões via `mvn clean test`: **400 testes executados em 27 classes, 0 falhas, 0 erros, 0 ignorados (`BUILD SUCCESS`)**.

## Alterações 21/11/2026 - 07:06

1. **Documentação de Checagem e Instalação do Ambiente (Linux e Windows)**:
   - Adicionados em `README.md` (Seção `Pré-requisitos`) e em [`docs/guia-execucao-otimizacao-recursos.md`](docs/guia-execucao-otimizacao-recursos.md) (Seção `3.0`) os comandos de checagem em 1 linha (`Linux Bash`/`Git Bash` e `Windows CMD`) e as opções de instalação do JDK 21 + Maven 3.9.9 **com** (`apt` / `winget`) e **sem** privilégios de administrador (`~/tools` no Linux e `%USERPROFILE%\tools` no Windows).

## 8. Atividades Reservadas para as Próximas Etapas
1. **Execução da Campanha Experimental Completa dos MOEAs Isolados**
2. **Adaptação e Integração das Hiper-heurísticas de Seleção (`AdapHH`, `EA-ILS` e `LAST-RL`)**:
   - Conexão entre o sinal escalar $\Delta R^2$ fornecido por `R2ExecutionObserver` e os mecanismos de recompensa/aceitação das hiper-heurísticas;
   - Orquestração adaptativa sobre o portfólio de MOEAs / operadores;
   - Comparação estatística sistemática *a posteriori* frente à campanha homologada dos MOEAs isolados.
3. **Deliberação Futura Opcional sobre Reativação de `NHV`** (bastando incluir `NHV` explicitamente em `-Dstudy.hvMethods` ou `-Dstudy.hvMethodsByM`, talvez refatorando-o  reutilizando `ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1`).
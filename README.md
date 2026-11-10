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
* **Não-invasivo**: Apenas observa o conjunto populacional corrente em intervalos configurados, sem realizar avaliações adicionais da função objetivo do problema.
* **Histórico e Exportação**: Mantém instâncias de `R2Observation(step, r2Value, deltaR2, timestampMs)` e suporta exportação direta para CSV.

---

## 2. Conjunto de Algoritmos do Estudo

O portfólio de meta-heurísticas de baixo nível (LLHs) implementado no projeto reflete a taxonomia estabelecida na proposta de dissertação, dividindo-se em duas vertentes fundamentais, com a **substituição do HypE pelo MOEA/DD** por motivos técnicos:

### 2.1 Vertente 1: Algoritmos Orientados à Qualidade do Conjunto
Esta vertente atua restaurando a pressão seletiva em problemas de alta dimensionalidade por meio de indicadores explícitos, pontos de referência ou relações fortalecidas:
* **NSGA-III** (*Deb & Jain, 2014*): Estende o NSGA-II substituindo a distância de aglomeração por nichamento baseado em pontos de referência estruturados sobre um simplex unitário (método de Das & Dennis). Reutilizado nativamente do jMetal 7.8 (`NSGAIIIBuilder`).
* **MOEA/DD** (*Li et al., 2015*): Combina a decomposição do espaço em subproblemas direcionados (baseados em vizinhança PBI) com a ordenação por dominância de Pareto, equilibrando convergência e diversidade de forma robusta. **Utilizado no projeto em substituição ao HypE**. Reutilizado nativamente do jMetal 7.8 (`MOEADBuilder.Variant.MOEADD`).
* **FDEA** (*Liu et al., 2022*): Fuzzy-based Pareto Optimality for Many-objective Evolutionary Algorithms. Introduz graus contínuos de dominância fuzzy entre soluções não-dominadas clássicas e autodecomposição do espaço de busca via amostragem por máxima similaridade (MSBS). Fuzzy-based Decomposition Evolutionary Algorithm. Portado do repositório original para a API do jMetal 7.8 como adaptador desacoplado (`br.usp.lti.newmoea.algorithm.fdea.FDEA`).

### 2.2 Vertente 2: Algoritmos Orientados à Diversidade Geométrica
Esta vertente preserva a diversidade recorrendo a grandezas geométricas contínuas (distâncias angulares, projeções e curvaturas):
* **RVEA** (*Cheng et al., 2016*): Reference Vector Guided Evolutionary Algorithm. Emprega a Distância Penalizada por Ângulo (APD) para avaliar convergência e alinhamento a vetores unitários, contando com mecanismo de adaptação periódica dos vetores para acomodar frentes com geometrias irregulares. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder`).
* **AGE-MOEA** (*Panichella, 2019*): Adaptive Geometry Estimation-based Many-objective Evolutionary Algorithm. Estima adaptativamente o formato e a curvatura $p$ da Fronteira de Pareto durante a busca a partir de pontos extremos, calculando escores de sobrevivência com base em proximidade geodésica. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder`).
* **AGE-MOEA-II** (*Panichella, 2020*): Variante refinada do AGE-MOEA que aprimora o cálculo de proximidade e diversidade em variedades não-euclidianas de alta dimensão. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder`).

### 2.3 Algoritmos Complementares e Configuração Comum de Operadores
Para fins de controle experimental de experimentos passados que já terminaram e validação estatística, o ambiente mantém:
* **MOEA/D-DRA**: MOEA/D com alocação dinâmica de recursos baseada em histórico de sucesso por subproblema.
* **NSGA-II** e **SPEA2**: Referências clássicas de ordenação por dominância e distância de aglomeração/força de Pareto.
* **ESPEA**: Algoritmo baseado no conceito de equilíbrio termodinâmico e entropia de Pareto.
* **GWASFGA**: Weighting Assessment Strategy with Reference Points.

**Parâmetros Comuns Auditados no jMetal 7.8 (`docs/operator-audit.md`)**:
* **Cruzamento SBX (`SBXCrossover(1.0, 30.0)`)**: Probabilidade $p_c = 1.0$, índice de distribuição $\eta_c = 30.0$ e reparo de limites via `RepairDoubleSolutionWithBoundValue`.
* **Mutação Polinomial (`PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)`)**: Probabilidade efetiva por variável $p_m = 1/n$ e índice de distribuição $\eta_m = 20.0$.
* **Configuração específica dos algoritmos principais**:
  - **NSGA-III**: Camadas explícitas $(H_1, H_2)$ de Das & Dennis / Deb & Jain configuradas via `StaticParams.getOuterDivisions(m)` e `StaticParams.getInnerDivisions(m)` com validação prévia `populationSize == expectedTwoLayerPoints(m, H1, H2)`.
  - **MOEA/DD**: `MOEADBuilder.Variant.MOEADD`, escalarização `PBI` ($\theta = 5.0$ fixo em `AbstractMOEAD.fitnessFunction`), vizinhança $T = 20$, $\delta = 0.9$, $nr = 2$, com pré-validação obrigatória do arquivo `MOEAD_Weights/W{m}D_{populationSize}.dat` via `WFGStudy.validateMoeadWeightFile`.
  - **RVEA**: Direções unitárias geradas por `ReferencePointGenerator` e normalizadas na esfera $\ell_2$ ($\|\mathbf{v}_i\|_2 = 1$), com $\alpha = 2.0$ e $f_r = 0.1$.

---

## 3. Indicadores de Qualidade e Regimes de Hipervolume

O pipeline de pós-processamento (`ComputeQualityIndicators`) e a suíte de validação avaliam as aproximações de Pareto por meio de **cinco indicadores** (gravando arquivos independentes `HV`, `NHV`, `IGD`, `IGD+` e `R2` para cada algoritmo e problema):

1. **$R^2$ (`org.uma.jmetal.qualityindicator.impl.R2`)**:
   - Função utilitária de Tchebycheff com vetores de peso uniformes e ponto ideal $z^*$ (com reinicialização automática de `weightVectors` e `idealPoint` quando a dimensão $m$ muda entre problemas).
   - **Minimização**: menor valor indica melhor aproximação.
   - Disponível tanto pós-execução no pipeline quanto em tempo de execução via `R2ExecutionObserver`.
2. **IGD Padrão (`org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance`)**:
   - Distância média Euclidiana de cada ponto da fronteira de referência ao ponto mais próximo da população gerada.
   - **Minimização**: menor valor indica melhor aproximação.
3. **IGD+ (`org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus`)**:
   - Distância modificada que penaliza apenas soluções dominadas ou piores que a referência, garantindo monotonicidade fraca com a dominância de Pareto.
   - **Minimização**: menor valor indica melhor aproximação.
4. **Hipervolume Bruto / Primário — `HV` (`org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume`)**:
   - Calcula de forma **exata e determinística** (varredura recursiva por dimensão de Zitzler / Fonseca et al.) o volume do espaço de objetivos dominado pela frente avaliada.
   - **Maximização** (`isTheLowerTheIndicatorValueTheBetter() = false`): maior valor indica melhor desempenho. Gravado no arquivo `HV`.
5. **Hipervolume Normalizado / Secundário — `NHV` (`org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume`)**:
   - Calcula a diferença relativa em relação ao hipervolume da frente de referência $R$:
     $$\text{NHV}(A, R) = 1.0 - \frac{\text{HV}(A)}{\text{HV}(R)}$$
   - **Minimização** (`isTheLowerTheIndicatorValueTheBetter() = true`): menor valor indica melhor desempenho ($\text{NHV}(R, R) = 0.0$; assume valores negativos quando $\text{HV}(A) > \text{HV}(R)$). Gravado separadamente no arquivo `NHV`.

### 3.1 Dois Regimes de Referência para o Hipervolume (`docs/hypervolume-reference-points.md`)
* **Regime 1 — Espaço Normalizado no Pipeline (`ComputeQualityIndicators`)**:
  - `ComputeQualityIndicators.run()` normaliza linearmente a frente de referência $R$ em $[0, 1]^m$ via `NormalizeUtils.normalize(referenceFront)` e normaliza cada frente aproximada `FUN<run>.csv` usando exclusivamente os limites $\min(R)$ e $\max(R)$.
  - Em seguida, `PISAHypervolume` aplica `VectorUtils.getInvertedFront(front)`, adotando no espaço normalizado o ponto de referência unitário:
    $$\mathbf{z}_{\text{norm}} = (1.0, 1.0, \dots, 1.0) \in \mathbb{R}^m \quad (\text{via } \texttt{StaticParams.getNormalizedHypervolumeReferencePoint(m)})$$
* **Regime 2 — Espaço Bruto de Objetivos (`StaticParams.getHypervolumeReferencePoint(problemName, m)`)**:
  - Para avaliações no espaço original dos objetivos, o ponto de referência é definido **por problema e número de objetivos $m$**, dominando estritamente ($f_j < z_j$) 100% dos pontos das 68 frentes de referência ativas em `pareto_fronts/csv/`:
    - **`WFG1` a `WFG9`**: $z_j = 2.2 \times j = 1.1 \times S_j$ para $j = 1, \dots, m$ (ex.: $(2.2, 4.4)$ em $m=2$, com ajuste $(3.1, 4.4)$ em `WFG3` para $m=2$ devido à extensão até $f_1 = 3.0$ em `WFG3.2D.csv`);
    - **`DTLZ1`**: $(0.6, 0.6, \dots, 0.6) \in \mathbb{R}^m$ (frente em $\sum f_j = 0.5, f_j \in [0, 0.5]$);
    - **`DTLZ2` a `DTLZ6`**: $(1.1, 1.1, \dots, 1.1) \in \mathbb{R}^m$ (frente em $\sum f_j^2 = 1.0, f_j \in [0, 1.0]$);
    - **`DTLZ7`**: $(1.1, \dots, 1.1, 2.1m) \in \mathbb{R}^m$ (frente desconexa com $f_j \in [0, 1.0]$ para $j < m$ e $f_m \in [f_m^{\min}(m), 2m]$).

### 3.2 Limitação Computacional em Alta Dimensão ($m = 15$) e Timeout de Segurança
* O cálculo exato de hipervolume é um problema `#P-hard` cuja árvore de recursão em `PISAHypervolume` cresce exponencialmente com $m$. Em $m=15$ ($N=135$), frentes aproximadas com muitos pontos interiores mutuamente não-dominados em $[0, 1)^{15}$ (como no `MOEA/DD` em `WFG4`) podem ultrapassar $40.863\text{ s}$ ($> 11{,}35\text{ h}$) de CPU.
* O utilitário `PerformanceProfiler` impõe por padrão um **timeout de segurança de 1 hora (`DEFAULT_HV_TIMEOUT_MS = 3_600_000L` ms, configurável via `-Dperf.hvTimeoutMs`)** por cálculo individual de hipervolume, enquanto os indicadores polinomiais (`R2`, `IGD+` e `IGD`) avaliam frentes completas de $135$ soluções em $m=15$ em menos de $2{,}5\text{ ms}$.

---

## 4. Problemas de Benchmark e Opções Experimentais

### 4.1 Suítes Utilizadas e Cobertura de Frentes de Referência
* **Suíte WFG (`WFG1` a `WFG9`)**: Instâncias escaláveis com fronteiras não-separáveis, deceptivas, multimodalidade, viés paramétrico e formatos mistos (convexas, côncavas, lineares e desconexas). Habilitadas por padrão em todas as 6 dimensões $m \in \{2, 3, 5, 8, 10, 15\}$ (`54` frentes oficiais validadas em `pareto_fronts/csv/WFG{1..9}.{2,3,5,8,10,15}D.csv`).
* **Suíte DTLZ (`DTLZ1` a `DTLZ7`)**: Problemas escaláveis com geometrias lineares (`DTLZ1`), côncavas esféricas (`DTLZ2`–`DTLZ4`), curvas 1D degeneradas (`DTLZ5`–`DTLZ6`) e $2^{m-1}$ regiões desconexas (`DTLZ7`).
  - **Comportamento padrão**: Habilitados em $m \in \{2, 3\}$ (`14` frentes oficiais validadas em `pareto_fronts/csv/DTLZ{1..7}.{2,3}D.csv`), totalizando **68 configurações de problemas ativas por padrão**.
  - **Extensão analítica opcional para $m \in \{5, 8, 10, 15\}$**: Como o repositório oficial do jMetal 7.8 não distribui frentes DTLZ para $m \in \{5, 10, 15\}$ (nem `DTLZ5/6` para $m=8$), `ParetoFrontValidator` implementa a geração matemática analítica validada para `DTLZ1–DTLZ7` em qualquer $m \in \{2, 3, 5, 8, 10, 15\}$ (`resources/referenceFronts/pf_gen/analytical/`), ativável sob demanda via `-DgenerateAllReferenceFronts=true`.

### 4.2 Dimensionalidade dos Objetivos ($m$), Camadas e Orçamentos (`StaticParams.java`)
O estudo contempla as dimensões $m \in \{2, 3, 5, 8, 10, 15\}$, com orçamento comum $\text{maxEvaluations} = \text{maxGenerations} \times \text{populationSize}$:

| Objetivos ($m$) | População ($N$) | Camada Externa ($H_1$) | Camada Interna ($H_2$) | Gerações WFG1–9 | Gerações DTLZ1 / DTLZ2 / DTLZ3 / DTLZ4 / DTLZ5–7 |
|---:|---:|---:|---:|---:|---:|
| **2** | 100 | 99 | 0 | 250 | 400 / 250 / 1000 / 600 / 1000 |
| **3** | 91 | 12 | 0 | 400 | 400 / 250 / 1000 / 600 / 1000 |
| **5** | 210 | 6 | 0 | 750 | 600 / 350 / 1000 / 1000 / 1000 |
| **8** | 156 | 3 | 2 | 1500 | 750 / 500 / 1000 / 1250 / 1000 |
| **10** | 275 | 3 | 2 | 2000 | 1000 / 750 / 1500 / 2000 / 1500 |
| **15** | 135 | 2 | 1 | 3000 | 1500 / 1000 / 2000 / 3000 / 2000 |

### 4.3 Propriedades JVM Opcionais (Desativadas por Padrão)
Todas as opções experimentais da Fase 2 permanecem **desativadas (`false`) por padrão** para preservar a configuração canônica do estudo e a integridade dos diretórios originais:
* `-DpersistReferencePoints=false`: Quando `true`, persiste os pontos de referência do `NSGA-III` em `resources/referencePoints/nsgaiii/`.
* `-DpersistReferenceDirections=false`: Quando `true`, persiste as direções de referência do `RVEA` em `resources/referencePoints/rvea/`.
* `-DgenerateAllReferenceFronts=false`: Quando `true`, gera e valida matematicamente as frentes analíticas de `DTLZ1–DTLZ7` para $m > 3$ em `resources/referenceFronts/pf_gen/analytical/` e as habilita no estudo.
* `-DgenerateEmpiricalReferenceFronts=false`: Quando `true`, habilita a geração controlada de frentes empíricas não-dominadas com arquivo `.meta.txt` em `resources/referenceFronts/pf_gen/empirical/`.
* `-DreplaceZeroWeights=false`: Quando `true`, habilita a geração de variantes de vetores de peso com substituição $0 \mapsto 10^{-5}$ em `resources/weightVectorFiles/weights_gen/`.
* `-DprofilePerformance=false`: Quando `true`, executa a bateria padronizada de avaliação de desempenho ao final de `WFGStudy.main`.

---

## 5. Estrutura de Diretórios do Projeto

```text
hypeMOEA/
├── pom.xml                                     ← Configuração Maven (Java 21, jMetal 7.8, JUnit 5, JaCoCo)
├── README.md                                   ← Documentação principal do projeto
├── AGENTS.md                                   ← Regras de governança e restrições do repositório
├── TASK_COORDINATOR.md                         ← Especificação central de orquestração multiagente (Fases 1 e 2)
├── HANDLER.md                                  ← Registro compartilhado de estado, decisões e evidências
├── tasks/                                      ← Especificações individuais das tarefas (task1.md a task11.md)
├── docs/                                       ← Relatórios técnicos, auditorias de bytecode e protocolos
│   ├── operator-audit.md                       ← Auditoria de PolynomialMutation, SBXCrossover e DE (T06)
│   ├── moead-weights-audit.md                  ← Auditoria e sincronização dos pesos do MOEA/DD (T11)
│   ├── moead-weights-manifest.csv              ← Manifesto SHA-256 dos arquivos de pesos (T11)
│   ├── reference-points-audit.md               ← Revalidação matemática de NSGA-III e RVEA (T07)
│   ├── reference-fronts-dtlz.md                ← Inventário e derivação analítica de DTLZ1–DTLZ7 (T07)
│   ├── hypervolume-reference-points.md         ← Auditoria de HV, NHV, normalização e pontos de referência (T08)
│   ├── final-validation-report.md              ← Relatório consolidado de validação final (T10)
│   └── performance/                            ← Avaliação de desempenho computacional (T09)
│       ├── performance-protocol.md             ← Protocolo, ambiente e estatísticas de tempo/memória
│       └── performance-results.csv             ← 630 registros de medição (90 runs × 7 etapas)
├── MOEAD_Weights/                              ← Diretório ativo de vetores de peso (.dat) para MOEA/DD (48 arquivos)
├── resources/                                  ← Recursos oficiais sincronizados e saídas opcionais isoladas
│   ├── weightVectorFiles/                      ← 29 cópias fiéis oficiais do jMetal 7.8
│   │   ├── moead/                              ← 29 cópias fiéis em subdiretório homônimo ao jMetal
│   │   └── weights_gen/                        ← Variantes opcionais geradas com 0 -> 1e-5
│   ├── referencePoints/                        ← Saída opcional de pontos NSGA-III e direções RVEA
│   └── referenceFronts/pf_gen/                 ← Saída opcional de frentes DTLZ analíticas e empíricas
├── pareto_fronts/csv/                          ← 160 frentes de referência oficiais em CSV (68 ativas no pipeline)
└── src/
    ├── main/java/br/usp/lti/newmoea/
    │   ├── SimpleNSGAII.java                   ← Exemplo simples de execução individual
    │   ├── algorithm/fdea/                     ← Adaptador FDEA para jMetal 7.8 (Liu et al., 2022)
    │   │   ├── FDEA.java
    │   │   ├── FDEABuilder.java
    │   │   ├── FDEAIndividual.java
    │   │   ├── MostSimilarBasedSampling.java
    │   │   └── LeastSimilarBasedSampling.java
    │   ├── feedback/                           ← Infraestrutura de observação R² durante execução
    │   │   ├── R2Observation.java
    │   │   └── R2ExecutionObserver.java
    │   ├── study/                              ← Pipeline experimental e parametrização estática
    │   │   ├── StaticParams.java               ← Populações, gerações, divisões H1/H2 e pontos de HV
    │   │   └── WFGStudy.java                   ← Orquestrador principal do estudo experimental
    │   └── util/                               ← Validadores matemáticos e perfilador de desempenho
    │       ├── ReferenceSetValidator.java      ← Validação de simplex, camadas NSGA-III, RVEA e pesos MOEA/DD
    │       ├── ParetoFrontValidator.java       ← Validação e geração analítica/empírica de frentes de Pareto
    │       └── PerformanceProfiler.java        ← Instrumentação de CPU, Wall-Clock, Heap e timeout de HV
    └── test/java/br/usp/lti/newmoea/           ← Suíte automatizada JUnit 5 (10 classes, 277 testes)
        ├── algorithm/
        │   ├── AlgorithmIntegrationTest.java               ← 19 testes de integração dos algoritmos
        │   ├── MoeaddWeightValidationTest.java             ← 22 testes de pesos MOEA/DD, SHA-256 e divisão por zero
        │   ├── NsgaIIIReferencePointsValidationTest.java   ← 22 testes de NSGA-III e camadas H1/H2
        │   └── RveaReferenceDirectionsValidationTest.java  ← 8 testes de direções unitárias L2 do RVEA
        ├── feedback/
        │   └── R2ExecutionObserverTest.java                ← 3 testes de coleta runtime de R² e exportação CSV
        ├── operator/
        │   └── OperatorAuditTest.java                      ← 7 testes de auditoria de PolynomialMutation e SBX
        ├── qualityindicator/
        │   ├── HypervolumeAuditTest.java                   ← 12 testes de auditoria de HV, NHV e pontos de referência
        │   ├── ParetoFrontValidationTest.java              ← 164 testes de validação das frentes WFG e DTLZ
        │   └── QualityIndicatorUnitTest.java               ← 12 testes unitários de R2, IGD, IGD+, HV e NHV
        └── util/
            └── PerformanceProfilerTest.java                ← 8 testes do perfilador de desempenho e timeout
```

---

## 6. Como Rodar o Projeto

### Pré-requisitos
1. **Java Development Kit (JDK) 21 (LTS)** instalado e configurado no `JAVA_HOME`.
2. **Apache Maven 3.8+** configurado e disponível no PATH (`mvn -v`).
3. *(Opcional)* **R** com pacote `knitr` instalado se desejar compilar os scripts R e gráficos gerados.

### Passo 1: Compilar o Projeto
Abra o terminal no diretório raiz do projeto (`hypeMOEA`).

Para limpar artefatos anteriores e compilar todo o código-fonte em Java 21 com jMetal 7.8 execute:
```bash
mvn clean compile
```

### Passo 2: Executar a Suíte Completa de Testes Automatizados
O projeto conta com **10 classes de teste** abrangendo operadores, pontos e direções de referência, pesos do MOEA/DD, frentes de Pareto, indicadores de qualidade, observador $R^2$ e perfilador de desempenho:
```bash
mvn clean test
```
*Saída esperada*: **277 testes executados com 0 falhas e 0 erros (`BUILD SUCCESS`)**.

### Passo 3: Execução de Teste Rápido de Validação (Smoke Test)
Para verificar rapidamente a integridade do pipeline sem aguardar as 30 repetições completas de todos os problemas, utilize `-Druns=1` combinado com `-Dobjectives` e `-Dproblem`:

```bash
# Validação de fumaça em WFG1 com 2 objetivos (1 repetição)
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Druns=1" "-Dobjectives=2" "-Dproblem=WFG1"

# Validação de fumaça em DTLZ2 com 3 objetivos (1 repetição)
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Druns=1" "-Dobjectives=3" "-Dproblem=DTLZ2"
```
Esses comandos validam previamente os pesos do MOEA/DD e as camadas do NSGA-III, executam os algoritmos, gravam `FUN0.csv` e `VAR0.csv`, calculam separadamente `HV`, `NHV`, `IGD`, `IGD+` e `R2`, e geram `QualityIndicatorSummary.csv`, tabelas LaTeX e scripts R em `WFGStudy/`.

### Passo 4: Executar o Perfilador de Desempenho (`PerformanceProfiler`)
Para reproduzir as medições de tempo decorrido, tempo de CPU e memória Heap ou consultar o resumo estatístico dos 630 registros versionados em `docs/performance/performance-results.csv`:
```bash
# Imprimir resumo estatístico do CSV versionado
mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.summarizeOnly=true"

# Executar comparação escalonada de métodos exatos de HV (PISAHypervolume, WFGHypervolume, Hypervolume3D)
mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.compareHvOnly=true" "-Dperf.warmup=1" "-Dperf.runs=5"

# Executar bateria completa de desempenho com timeout de segurança de 1h (3.600.000 ms) para HV
mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.warmup=1" "-Dperf.runs=5" "-Dperf.maxHvFrontSize15D=35" "-Dperf.hvTimeoutMs=3600000"
```

### Passo 5: Execução Experimental Completa
Para iniciar o experimento científico completo (30 repetições independentes, problemas WFG/DTLZ e dimensões $m \in \{2, 3, 5, 8, 10, 15\}$ com paralelização em 8 núcleos de CPU):
```bash
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy"
```

### Passo 6: Localização e Interpretação dos Resultados
Os arquivos gerados são organizados em `WFGStudy/`:
* `WFGStudy/data/<Algoritmo>/<Problema_m>/`:
  - `FUN<run>.csv`: Valores das funções objetivo obtidos na fronteira de Pareto final.
  - `VAR<run>.csv`: Vetores das variáveis de decisão correspondentes.
  - `HV`: Hipervolume bruto/primário (`PISAHypervolume`, maximização) de cada repetição.
  - `NHV`: Hipervolume normalizado/secundário (`NormalizedHypervolume`, minimização) de cada repetição.
  - `IGD`, `IGD+`, `R2`: Valores escalares dos demais indicadores de cada repetição.
* `WFGStudy/QualityIndicatorSummary.csv`: Resumo consolidado de todos os 5 indicadores calculados (`HV`, `NHV`, `IGD`, `IGD+`, `R2`).
* `WFGStudy/latex/`: Tabelas prontas em LaTeX com médias, medianas e desvios padrão.
* `WFGStudy/R/`: Scripts em R para execução dos testes de Wilcoxon, Friedman e renderização de Boxplots.

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
   - Implementada a opção `-DreplaceZeroWeights=false` (desativada por padrão) em `ReferenceSetValidator` com geração isolada em `resources/weightVectorFiles/weights_gen/` (`22` testes em `MoeaddWeightValidationTest`).
4. **Revalidação de NSGA-III e RVEA, Persistência Opcional e Extensão Analítica/Empírica de Frentes DTLZ (`T07`)**:
   - Criados [`docs/reference-points-audit.md`](docs/reference-points-audit.md), [`docs/reference-fronts-dtlz.md`](docs/reference-fronts-dtlz.md) e [`RveaReferenceDirectionsValidationTest.java`](src/test/java/br/usp/lti/newmoea/algorithm/RveaReferenceDirectionsValidationTest.java) (`8` testes).
   - Validada separadamente a camada externa ($H_1$) e a camada interna ($H_2$) do `NSGA-III` para $m \in \{8, 10, 15\}$ e a normalização unitária $\ell_2$ das direções do `RVEA`.
   - Derivadas e implementadas as geradoras matemáticas de `DTLZ1–DTLZ7` para $m \in \{2, 3, 5, 8, 10, 15\}$ (incluindo a curva 1D de `DTLZ5/6` e as $2^{m-1}$ regiões desconexas de `DTLZ7`) e as opções desativadas por padrão `-DpersistReferencePoints=false`, `-DpersistReferenceDirections=false`, `-DgenerateAllReferenceFronts=false` e `-DgenerateEmpiricalReferenceFronts=false`.
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

---

## 8. Atividades Reservadas para a Próxima Etapa
Conforme definido no planejamento:
1. Ajustar os paramêtros e hiperparâmetros para aqueles definidos pela bibliografia, sejam específicos de algoritmos, como pontos de referências/divisões a detalhes comuns de algoritmos geneticos como população (os comuns devem ser baseados no algoritmo mais recente, já os especificos devem ser aqueles que seus artigos usaram).
2. Escolher hipervolume a ser implementado tanto o aproximado como o real, utilizando aqueles mais indicados ou mais utilizados por aqueles que fizeram artigos (para facilitar a justificativa do seu uso preterindo outros).
3. Planejamento e execução dos experimentos em larga escala (Execução Completa).
4. Realizar análise estatística e pós-processamento dos dados (após execução completa)
<br><br>
5. Conexão entre o sinal de retroalimentação escalar ($\Delta R^2$ fornecido por `R2ExecutionObserver`) e a lógica de seleção de heurísticas de baixo nível das hiper-heurísticas (AdapHH, EA-ILS e LAST-RL).
6. Construção do pipeline de orquestração adaptativa sobre os operadores dos MOEAs conforme as decisões da hiper-heurística.
7. Comparação sistemática a posteriori do desempenho dos algoritmos guiados por hiper-heurísticas frente aos MOEAs isolados.

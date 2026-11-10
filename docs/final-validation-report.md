# Relatório Final de Validação e Consolidação Científica (T10) — Projeto `hypeMOEA`

## Identificação
- **Projeto**: `hypeMOEA` (`br.usp.lti:newmoea:1.0-SNAPSHOT`)
- **Plataforma**: Java 21 LTS (`21.0.12.1`), Apache Maven (`3.8.8`), jMetal `7.8`, JUnit Jupiter `5.10.2`, AssertJ `3.25.3`
- **Tarefa**: `T10` (`tasks/task10.md`) — Validação final, documentação e consolidação
- **Agente Responsável**: Agente F (Validação Final e Documentação)
- **Data da Consolidação**: `04/11/2026`
- **Referências de Governança**: [`TASK_COORDINATOR.md`](../TASK_COORDINATOR.md) (Seções 4, 10, 11 e 12), [`AGENTS.md`](../AGENTS.md), [`HANDLER.md`](../HANDLER.md) e [`tasks/task10.md`](../tasks/task10.md).

---

## Sumário Executivo

Este documento consolida todas as evidências técnicas, matemáticas e experimentais produzidas na **Fase 1 (`T01`–`T05`)** e na **Fase 2 (`T06`–`T11`)** do projeto `hypeMOEA`, estruturado exatamente de acordo com os **13 itens obrigatórios da Seção 12 de `TASK_COORDINATOR.md`**.

Todas as verificações de compilação (`mvn clean compile`), execução integral da suíte automatizada (`mvn clean test`, totalizando **277 testes em 10 classes com 0 falhas, 0 erros e 0 ignorados**) e validações de fumaça fim a fim (`WFG1` em $m=2$ e `DTLZ2` em $m=3$ com `-Druns=1`) foram executadas e aprovadas, preservando-se integralmente os diretórios de artefatos originais (`MOEAD_Weights/`, `pareto_fronts/csv/` e `WFGStudy/`).

---

## 1. Relatório da Auditoria de Operadores (T06)

- **Documento detalhado**: [`docs/operator-audit.md`](operator-audit.md)
- **Classe de teste**: [`OperatorAuditTest.java`](../src/test/java/br/usp/lti/newmoea/operator/OperatorAuditTest.java) (`7` testes aprovados)

### Principais Evidências e Conclusões
1. **Inspeção em Bytecode (`jmetal-core-7.8.jar`)**:
   - `org.uma.jmetal.operator.mutation.impl.PolynomialMutation` possui **7 construtores** no jMetal 7.8. Comprovou-se que **não existe** construtor `(double, int, double)`.
   - O construtor `PolynomialMutation(double mutationProbability, double distributionIndex)` armazena diretamente o primeiro argumento em `this.mutationProbability` sem dividi-lo internamente por `numberOfVariables()`.
   - Em [`WFGStudy.java`](../src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) (linha 319), a chamada efetiva é `new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)`, que realiza a divisão em ponto flutuante antes da invocação do construtor, definindo com exatidão:
     - Probabilidade de mutação por variável: $p_m = 1/n$ (equivalente ao construtor `PolynomialMutation(DoubleProblem, double, RandomGenerator)` do próprio jMetal 7.8);
     - Índice de distribuição de mutação: $\eta_m = 20.0$.
2. **Cruzamento SBX (`SBXCrossover`)**:
   - `org.uma.jmetal.operator.crossover.impl.SBXCrossover` possui **5 construtores** no jMetal 7.8.
   - Em `WFGStudy.java` (linha 318), `new SBXCrossover(1.0, 30.0)` configura probabilidade de cruzamento $p_c = 1.0$ e índice de distribuição $\eta_c = 30.0$.
3. **Reparo de Limites de Caixa**:
   - Tanto `PolynomialMutation` quanto `SBXCrossover` delegam por padrão para `RepairDoubleSolutionWithBoundValue`, truncando qualquer variável fora de $[y_l, y_u]$ exatamente para a fronteira correspondente.
4. **Aplicação Consistente entre os Algoritmos do Estudo**:
   - Os 6 algoritmos principais (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA` e `AGE-MOEA-II`) recebem os mesmos operadores `SBXCrossover(1.0, 30.0)` e `PolynomialMutation(1.0 / n, 20.0)`.
   - Nenhuma alteração em `WFGStudy.java` foi necessária na T06.

---

## 2. Relatório de Validação de NSGA-III e RVEA (T07)

- **Documento detalhado**: [`docs/reference-points-audit.md`](reference-points-audit.md)
- **Classes de validação e teste**:
  - [`ReferenceSetValidator.java`](../src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java)
  - [`NsgaIIIReferencePointsValidationTest.java`](../src/test/java/br/usp/lti/newmoea/algorithm/NsgaIIIReferencePointsValidationTest.java) (`22` testes aprovados)
  - [`RveaReferenceDirectionsValidationTest.java`](../src/test/java/br/usp/lti/newmoea/algorithm/RveaReferenceDirectionsValidationTest.java) (`8` testes aprovados)

### Principais Evidências e Conclusões
1. **Configuração Canônica de Camadas ($H_1, H_2$)**:
   - $m=2$: $H_1=99, H_2=0 \implies N=100$;
   - $m=3$: $H_1=12, H_2=0 \implies N=91$;
   - $m=5$: $H_1=6, H_2=0 \implies N=210$;
   - $m=8$: $H_1=3, H_2=2 \implies N=120 + 36 = 156$;
   - $m=10$: $H_1=3, H_2=2 \implies N=220 + 55 = 275$;
   - $m=15$: $H_1=2, H_2=1 \implies N=120 + 15 = 135$.
2. **NSGA-III (Validação Global e Separada por Camada)**:
   - `ReferencePointGenerator` (`jmetal-core-7.8.jar`) implementa com erro $0.0$ ($\le 1.11 \times 10^{-16}$) a geração de Das & Dennis (1998) e a contração de 2 camadas de Deb & Jain (2014) ($x_i^{\text{inner}} = \frac{1}{2}x_i + \frac{1}{2m}$) para todos os $m \in \{2, 3, 5, 8, 10, 15\}$.
   - Em `NSGAIII` (`jmetal-algorithm-7.8.jar`), a **camada externa ($H_1$)** é `EQUIVALENT` (erro $0.0$) ao gerador canônico em todas as dimensões. Já a **camada interna ($H_2$)** em $m \in \{8, 10, 15\}$ aplica internamente no construtor de `NSGAIII` a fórmula $x_i^{\text{inner}} = \frac{x_i + 1}{m + 1}$ (com diferença máxima analítica $\Delta_\infty(m) = \frac{(m-1)^2}{2m(m+1)}$, igual a $0.340278$ em $m=8$, $0.368182$ em $m=10$ e $0.408333$ em $m=15$). Os pontos internos permanecem estritamente positivos, somam $1.0$ e não possuem duplicatas; conforme diretriz de governança, o gerador interno do jMetal 7.8 é preservado e a característica está documentada.
3. **RVEA (Direções Unitárias na Esfera $\ell_2$)**:
   - `WFGStudy.buildRVEA` alimenta `RVEABuilder` com `ReferencePointGenerator` ($\alpha = 2.0, f_r = 0.1$).
   - Internamente, `RVEAGeometry.directions` normaliza cada vetor do simplex para a esfera unitária $\ell_2$ ($\|\mathbf{v}_i\|_2 = 1$ com erro máximo $\le 6.66 \times 10^{-16}$), com $0$ duplicatas, $100\%$ de finitude e frequência de adaptação $f_{\text{adapt}} = \text{round}(f_r \cdot (G_{\max} - 1))$.
4. **Persistência Opcional Controlada**:
   - Implementadas as propriedades `-DpersistReferencePoints=false` e `-DpersistReferenceDirections=false` (desativadas por padrão), gravando em `resources/referencePoints/nsgaiii/` e `resources/referencePoints/rvea/` sobre cópias profundas sem mutar os vetores internos dos algoritmos.

---

## 3. Inventário e Documentação das Frentes de Pareto (T07)

- **Documento detalhado**: [`docs/reference-fronts-dtlz.md`](reference-fronts-dtlz.md)
- **Classes de validação e teste**:
  - [`ParetoFrontValidator.java`](../src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java)
  - [`ParetoFrontValidationTest.java`](../src/test/java/br/usp/lti/newmoea/qualityindicator/ParetoFrontValidationTest.java) (`164` testes aprovados)

### Principais Evidências e Conclusões
1. **Inventário e Proveniência Oficial**:
   - Os JARs binários do jMetal 7.8 no Maven Central não empacotam frentes CSV; elas provêm de `jMetal/resources/referenceFrontsCSV/`.
   - O diretório `pareto_fronts/csv/` contém **160 arquivos** no total, dos quais **68 arquivos** são consumidos ativamente pelo pipeline padrão:
     - `54` arquivos `WFG1..9.{2,3,5,8,10,15}D.csv`;
     - `14` arquivos `DTLZ1..7.{2,3}D.csv`.
   - Todos os 68 arquivos ativos possuem **hash SHA-256 100% idêntico** aos arquivos homônimos de `jMetal/resources/referenceFrontsCSV/`.
2. **Validação Analítica e Anomalias Originais Documentadas**:
   - Equações analíticas validadas para `DTLZ1` ($\sum f_j = 0.5$, erro $0.0$), `DTLZ2–4` ($\sum f_j^2 = 1$, erro $\le 1.39 \times 10^{-6}$), `DTLZ5–6` (curva 1D degenerada com $f_1 = f_2$ e $\sum f_j^2 = 1$, erro $\le 6.66 \times 10^{-16}$), `DTLZ7` ($2^{m-1}$ regiões desconexas com $f_m \in [f_m^{\min}(m), 2m]$, erro $\le 4.98 \times 10^{-6}$) e `WFG4–9` ($\sum (f_j / (2j))^2 = 1$, erro $\le 7.83 \times 10^{-6}$).
   - Documentadas as características originais dos arquivos do jMetal: duplicatas exatas em `DTLZ1.3D.csv` ($99$), `DTLZ5.3D.csv` ($166.167$ duplicatas, $333$ únicos) e `DTLZ6.3D.csv` ($27.860$ duplicatas, $140$ únicos); $109$ pontos dominados em `DTLZ7.3D.csv`; e $f_2 \in [-2, 4]$ em `WFG3.2D.csv`.
3. **Geração Analítica e Empírica Opcional para `DTLZ1–DTLZ7` ($m > 3$)**:
   - Implementada a geração matemática exata (`-DgenerateAllReferenceFronts=false`, desativada por padrão) em `resources/referenceFronts/pf_gen/analytical/` e a consolidação empírica não-dominada com arquivo `.meta.txt` e guarda anti-vazamento (`-DgenerateEmpiricalReferenceFronts=false`, desativada por padrão) em `resources/referenceFronts/pf_gen/empirical/`.

---

## 4. Relatório de Auditoria e Manifesto dos Pesos do MOEA/DD (T11)

- **Documentos detalhados**:
  - [`docs/moead-weights-audit.md`](moead-weights-audit.md)
  - [`docs/moead-weights-manifest.csv`](moead-weights-manifest.csv) (`50` linhas = cabeçalho + `48` arquivos de `MOEAD_Weights/` + `1` variante opcional de referência)
- **Classe de teste**: [`MoeaddWeightValidationTest.java`](../src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java) (`22` testes aprovados)

### Principais Evidências e Conclusões
1. **Inventário Completo e Sincronização Fiel**:
   - Os 29 arquivos oficiais de `jMetal/resources/weightVectorFiles/moead/` foram copiados fielmente (100% idênticos em SHA-256) para `resources/weightVectorFiles/` e `resources/weightVectorFiles/`.
   - O diretório ativo `MOEAD_Weights/` contém 48 arquivos preservados e catalogados por hash SHA-256 no manifesto `docs/moead-weights-manifest.csv`.
2. **Validação dos 6 Arquivos Ativos do Pipeline**:
   - `W2D_100.dat` ($m=2, N=100$), `W5D_210.dat` ($m=5, N=210$) e `W15D_135.dat` ($m=15, N=135$): oriundos do acervo do projeto (sem contraparte no jMetal oficial), com substituição $0 \mapsto 10^{-5}$, 0 duplicatas, equivalentes ao gerador canônico de Das & Dennis / Deb & Jain.
   - `W3D_91.dat` ($m=3, N=91$): idêntico ao jMetal oficial, com zeros exatos (`0.0`) e 0 duplicatas.
   - `W8D_156.dat` ($m=8, N=156$) e `W10D_275.dat` ($m=10, N=275$): idênticos ao jMetal oficial; documentou-se que no jMetal 7.8 esses dois arquivos concatenam a camada externa ($H_1=3$) e a camada interna ($H_2=2$) sem contração centrípeta, produzindo $8$ e $10$ pares de vértices extremos duplicados, respectivamente.
3. **Auditoria de Bytecode sobre Divisão por Zero ($w_i = 0$)**:
   - Provado em bytecode que `MOEA/DD`, `MOEA/D`, `MOEA/D-DRA` (`AbstractMOEAD.fitnessFunction` com `PBI`, `TCHE`, `AGG` e `MOEADD.calculateDistance2`) e o indicador `R2` (`ASFUtilityFunctionSet`) **não dividem por zero** quando $w_i = 0$.
   - Apenas `WASFGA` / `GWASFGA` (`WeightVectors.invert`, que calcula $1.0 / w_{i,j}$) divide por zero quando $w_{i,j} = 0$, gerando `Infinity` e `NaN`.
4. **Opção `-DreplaceZeroWeights=false`**:
   - Implementada em `ReferenceSetValidator` (desativada por padrão), gerando variantes com $0 \mapsto 10^{-5}$ exclusivamente em `resources/weightVectorFiles/weights_gen/` sem alterar arquivos originais.

---

## 5. Documentação dos Pontos de Referência do Hipervolume (T08)

- **Documento detalhado**: [`docs/hypervolume-reference-points.md`](hypervolume-reference-points.md)
- **Implementação**: [`StaticParams.java`](../src/main/java/br/usp/lti/newmoea/study/StaticParams.java)

### Principais Evidências e Conclusões
1. **Dois Regimes de Hipervolume Claramente Separados**:
   - **Regime 1 — Espaço Normalizado do Pipeline (`ComputeQualityIndicators`)**: As frentes são normalizadas linearmente pelos extremos da frente de referência em $[0, 1]^m$, e `PISAHypervolume` aplica `VectorUtils.getInvertedFront(front)`, utilizando o ponto de referência unitário:
     $$\mathbf{z}_{\text{norm}} = (1.0, 1.0, \dots, 1.0) \in \mathbb{R}^m \quad (\text{via } \texttt{StaticParams.getNormalizedHypervolumeReferencePoint(m)})$$
   - **Regime 2 — Espaço Bruto de Objetivos (`StaticParams.getHypervolumeReferencePoint(problemName, m)`)**: Definido por problema e dimensão $m$, dominando estritamente ($f_j < z_j$) **100% dos pontos das 68 frentes ativas** em `pareto_fronts/csv/`:
     - `WFG1`–`WFG9`: $z_j = 2.2 \times j = 1.1 \times S_j$ para $j = 1, \dots, m$ (com ajuste $z_1 = 3.1$ em `WFG3` para $m=2$ devido à extensão até $f_1 = 3.0$ em `WFG3.2D.csv`);
     - `DTLZ1`: $(0.6, 0.6, \dots, 0.6) \in \mathbb{R}^m$ (frente em $\sum f_j = 0.5, f_j \in [0, 0.5]$);
     - `DTLZ2`–`DTLZ6`: $(1.1, 1.1, \dots, 1.1) \in \mathbb{R}^m$ (frente em $\sum f_j^2 = 1.0, f_j \in [0, 1.0]$);
     - `DTLZ7`: $(1.1, \dots, 1.1, 2.1m) \in \mathbb{R}^m$ (frente com $f_j \in [0, 1.0]$ para $j < m$ e $f_m \in [f_m^{\min}(m), 2m]$).
2. **Prova de Inadequação dos Pontos Uniformes Ingênuos no Espaço Bruto**:
   - Demonstrado analítica e empiricamente que $(2, \dots, 2)$ falha em `WFG` para todo $j \ge 2$ ($\max f_j^* = 2j \ge 4 > 2$) e que $(1, \dots, 1)$ falha em 100% dos pontos de `DTLZ7` ($f_m^* \ge 2.307 > 1$), zerando indevidamente o hipervolume no espaço bruto.

---

## 6. Auditoria de Normalização e dos Métodos de Cálculo de HV (T08)

- **Documento detalhado**: [`docs/hypervolume-reference-points.md`](hypervolume-reference-points.md)
- **Classes de teste**:
  - [`HypervolumeAuditTest.java`](../src/test/java/br/usp/lti/newmoea/qualityindicator/HypervolumeAuditTest.java) (`12` testes aprovados)
  - [`QualityIndicatorUnitTest.java`](../src/test/java/br/usp/lti/newmoea/qualityindicator/QualityIndicatorUnitTest.java) (`12` testes aprovados)

### Principais Evidências e Conclusões
1. **Exatidão Matemática dos Métodos no jMetal 7.8**:
   - Todas as 4 implementações presentes em `jmetal-core-7.8.jar` são **exatas e determinísticas**:
     - `PISAHypervolume` (`name = "HV"`, maximização): varredura recursiva por dimensão (Zitzler / Fonseca et al.);
     - `WFGHypervolume` (`name = "HV"`, maximização): inclusão-exclusão e *slicing* com casos base `inclhv`..`inclhv4` (While et al., 2012);
     - `Hypervolume3D` (`name = "HV3D"`, maximização): varredura de plano 3D $O(N \log N)$;
     - `HypervolumeContribution2D` (`name = "HV"`, maximização): ordenação 2D $O(N \log N)$.
   - O utilitário externo legado `Main-Hipervolume.java` (Monte Carlo com `new Random(System.nanoTime())`, sem normalização e com ponto escalar único) permanece isolado e fora do pipeline.
2. **Separação Estrita entre `HV` e `NHV`**:
   - `PISAHypervolume` grava o arquivo `HV` (maximização: quanto maior, melhor).
   - `NormalizedHypervolume` grava o arquivo `NHV` (minimização: $\text{NHV}(A, R) = 1 - \text{HV}(A)/\text{HV}(R)$, quanto menor, melhor; pode assumir valores negativos quando $\text{HV}(A) > \text{HV}(R)$).
3. **Auditoria de `NormalizeUtils` e `ComputeQualityIndicators`**:
   - `ComputeQualityIndicators` normaliza todas as frentes aproximadas usando exclusivamente os vetores `minValues` e `maxValues` extraídos da frente de referência comum $R$.
   - `NormalizeUtils.normalize` possui guarda explícita para `max == min`, retornando o ponto médio `0.5` sem produzir `NaN` ou `Infinity`.

---

## 7. Protocolo e Resultados de Desempenho (T09)

- **Documentos detalhados**:
  - [`docs/performance/performance-protocol.md`](performance/performance-protocol.md)
  - [`docs/performance/performance-results.csv`](performance/performance-results.csv) (`630` registros = `90` execuções $\times 7$ etapas)
- **Classes de instrumentação e teste**:
  - [`PerformanceProfiler.java`](../src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java)
  - [`PerformanceProfilerTest.java`](../src/test/java/br/usp/lti/newmoea/util/PerformanceProfilerTest.java) (`8` testes aprovados)

### Principais Evidências e Conclusões
1. **Instrumentação de Baixo Overhead e Separação Estrita de Métricas**:
   - Medição separada do tempo decorrido (`System.nanoTime()`) e do tempo de CPU (`ThreadMXBean`) para o algoritmo (`algorithm_wall_ms`, `algorithm_cpu_ms`) e para cada uma das 7 etapas pós-execução (`FRONT_LOAD`, `NORMALIZATION`, `PISAHypervolume`, `NormalizedHypervolume`, `IGD`, `IGD+`, `R2`).
   - Medição separada de `heap_used_mb` e `heap_peak_mb` (via `MemoryPoolMXBean.resetPeakUsage()`), sem confundir memória Heap com RSS do processo.
   - Overhead médio medido experimentalmente: $\approx 47\ \mu\text{s}$ por captura ($< 0.08\%$ do tempo de uma run em $m=3$ e $< 0.003\%$ em $m \ge 8$).
2. **Desempenho dos Algoritmos (`runs = 5`, Orçamento Completo)**:
   - Em $m=3$ (`DTLZ2`, $22.750$ avaliações; `WFG4`, $36.400$ avaliações): `NSGA-III` ($273.9\text{ ms}$ / $476.4\text{ ms}$), `MOEA/DD` ($303.9\text{ ms}$ / $486.7\text{ ms}$) e `RVEA` ($311.5\text{ ms}$ / $561.3\text{ ms}$) são os mais rápidos, seguidos por `FDEA` ($466.7\text{ ms}$ / $866.7\text{ ms}$) e `AGE-MOEA` ($983.2\text{ ms}$ / $1.978.0\text{ ms}$).
   - Em $m=8$ (`WFG4`, $234.000$ avaliações) e $m=15$ (`WFG4`, $405.000$ avaliações): `MOEA/DD` ($6.58\text{ s}$ em $m=8$; $11.61\text{ s}$ em $m=15$) e `NSGA-III` ($7.51\text{ s}$ em $m=8$; $15.42\text{ s}$ em $m=15$) escalam melhor que `RVEA` ($11.22\text{ s}$ / $27.44\text{ s}$) e `AGE-MOEA` ($31.49\text{ s}$ / $58.02\text{ s}$).
3. **Descobertas Críticas sobre os Indicadores e Limite de Segurança de 1 Hora**:
   - **Recálculo em `NormalizedHypervolume` ($m=3$)**: `PISAHypervolume` leva $\approx 0.18\text{ ms}$ na frente aproximada ($N=91$), mas `NormalizedHypervolume` leva $\approx 62\text{--}77\text{ ms}$ porque recalcula a cada chamada o HV dos $10.000$ pontos da frente de referência.
   - **Gargalo Exponencial em $m=8$ e Explosão Combinatória em $m=15$**: Em $m=8$ ($N=156$), `PISAHypervolume` leva $3.6\text{--}6.4\text{ s}$ e `NormalizedHypervolume` leva $6.0\text{--}8.9\text{ s}$ por run. Em $m=15$ ($N=135$), enquanto a frente completa do `NSGA-III` foi processada em $64.55\text{ s}$ totais, a frente completa do `MOEA/DD` (com $135$ pontos interiores em $[0,1)^{15}$) entrou em explosão combinatória $\mathcal{O}(N^{m/2})$, ultrapassando **$40.863\text{ s}$ ($> 11.35\text{ h}$) de CPU** sem concluir.
   - **Timeout de Segurança Padrão (`DEFAULT_HV_TIMEOUT_MS = 3_600_000L`)**: Implementado limite padrão de **1 hora ($3.600.000\text{ ms}$)** por cálculo de HV no `PerformanceProfiler` (configurável via `-Dperf.hvTimeoutMs`), enquanto `IGD`, `IGD+` e `R2` avaliam a frente completa de $135$ soluções em $m=15$ em menos de $2.5\text{ ms}$.

---

## 8. Lista de Arquivos Criados, Modificados e Preservados

### 8.1. Arquivos de Documentação e Manifestos Criados na Fase 2 (`docs/`)
- [`docs/operator-audit.md`](operator-audit.md) — Relatório da auditoria de operadores genéticos (T06).
- [`docs/moead-weights-audit.md`](moead-weights-audit.md) — Relatório da auditoria completa e sincronização dos pesos do MOEA/DD (T11).
- [`docs/moead-weights-manifest.csv`](moead-weights-manifest.csv) — Manifesto SHA-256 dos 48 arquivos em `MOEAD_Weights/`, das 29 cópias oficiais do jMetal e da variante opcional (T11).
- [`docs/reference-points-audit.md`](reference-points-audit.md) — Relatório de revalidação matemática de NSGA-III e RVEA e persistência opcional (T07).
- [`docs/reference-fronts-dtlz.md`](reference-fronts-dtlz.md) — Inventário, derivação analítica e validação das frentes de Pareto DTLZ1–DTLZ7 (T07).
- [`docs/hypervolume-reference-points.md`](hypervolume-reference-points.md) — Auditoria de hipervolume, normalização e pontos de referência por problema e $m$ (T08).
- [`docs/performance/performance-protocol.md`](performance/performance-protocol.md) — Protocolo experimental e análise estatística de desempenho computacional (T09).
- [`docs/performance/performance-results.csv`](performance/performance-results.csv) — Base de dados versionada com 630 registros de desempenho (T09).
- [`docs/final-validation-report.md`](final-validation-report.md) — Este relatório final de validação e consolidação (T10).

### 8.2. Classes de Produção e Teste Criadas ou Atualizadas (`src/`)
- **Produção (`src/main/java/br/usp/lti/newmoea/`)**:
  - [`study/WFGStudy.java`](../src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) — Modificado de forma controlada (T05, T07, T09) para validação prévia dos pesos do MOEA/DD, camadas explícitas do NSGA-III, separação de `HV` e `NHV`, e integração das flags opcionais desativadas por padrão (`-DpersistReferencePoints`, `-DpersistReferenceDirections`, `-DgenerateAllReferenceFronts`, `-DprofilePerformance`).
  - [`study/StaticParams.java`](../src/main/java/br/usp/lti/newmoea/study/StaticParams.java) — Modificado (T05, T08) para incluir divisões canônicas `getOuterDivisions(m)` e `getInnerDivisions(m)`, limites teóricos `getParetoFrontUpperBounds(problemName, m)` e pontos de referência de hipervolume nos regimes bruto (`getHypervolumeReferencePoint(problemName, m)`) e normalizado (`getNormalizedHypervolumeReferencePoint(m)`).
  - [`util/ReferenceSetValidator.java`](../src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java) — Criado na Fase 1 (T02/T03) e expandido na Fase 2 (T11/T07) com validação de camadas separadas do NSGA-III, direções unitárias do RVEA, persistência opcional em CSV e substituição opcional `-DreplaceZeroWeights=false`.
  - [`util/ParetoFrontValidator.java`](../src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java) — Criado na Fase 1 (T04) e expandido na Fase 2 (T07) com geradores analíticos validados de `DTLZ1–DTLZ7` para $m \in \{2,3,5,8,10,15\}$ e consolidação empírica controlada com metadados.
  - [`util/PerformanceProfiler.java`](../src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java) — Criado na Fase 2 (T09) para medição de tempo wall/CPU, memória Heap/Non-Heap, timeout de 1h para HV e comparação de escalabilidade de métodos de hipervolume.
- **Testes (`src/test/java/br/usp/lti/newmoea/`)**:
  - [`operator/OperatorAuditTest.java`](../src/test/java/br/usp/lti/newmoea/operator/OperatorAuditTest.java) — Criado em T06 (`7` testes).
  - [`algorithm/RveaReferenceDirectionsValidationTest.java`](../src/test/java/br/usp/lti/newmoea/algorithm/RveaReferenceDirectionsValidationTest.java) — Criado em T07 (`8` testes).
  - [`qualityindicator/HypervolumeAuditTest.java`](../src/test/java/br/usp/lti/newmoea/qualityindicator/HypervolumeAuditTest.java) — Criado em T08 (`12` testes).
  - [`util/PerformanceProfilerTest.java`](../src/test/java/br/usp/lti/newmoea/util/PerformanceProfilerTest.java) — Criado em T09 (`8` testes).
  - [`algorithm/MoeaddWeightValidationTest.java`](../src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java) — Expandido em T11 (`22` testes).
  - [`algorithm/NsgaIIIReferencePointsValidationTest.java`](../src/test/java/br/usp/lti/newmoea/algorithm/NsgaIIIReferencePointsValidationTest.java) — Expandido em T07 (`22` testes).
  - [`qualityindicator/ParetoFrontValidationTest.java`](../src/test/java/br/usp/lti/newmoea/qualityindicator/ParetoFrontValidationTest.java) — Expandido em T07 (`164` testes).
  - [`qualityindicator/QualityIndicatorUnitTest.java`](../src/test/java/br/usp/lti/newmoea/qualityindicator/QualityIndicatorUnitTest.java) — Atualizado em T08 (`12` testes).
  - [`algorithm/AlgorithmIntegrationTest.java`](../src/test/java/br/usp/lti/newmoea/algorithm/AlgorithmIntegrationTest.java) — Mantido e verificado (`19` testes).
  - [`feedback/R2ExecutionObserverTest.java`](../src/test/java/br/usp/lti/newmoea/feedback/R2ExecutionObserverTest.java) — Mantido e verificado (`3` testes).

### 8.3. Recursos Copiados e Artefatos Originais Preservados
- **Copiados fielmente em T11**:
  - `resources/weightVectorFiles/` (`29` arquivos `.dat` oficiais do jMetal 7.8);
  - `resources/weightVectorFiles/weights_gen/W3D_91.dat` (`1` variante derivada isolada, gerada sob teste controlado da opção `replaceZeroWeights`).
- **Preservados sem nenhuma modificação não autorizada**:
  - `MOEAD_Weights/` (`48` arquivos `.dat`, `0` arquivos modificados no `git status`);
  - `pareto_fronts/csv/` (`160` arquivos `.csv`, `0` arquivos modificados no `git status`);
  - `WFGStudy/` (resultados preexistentes integralmente preservados via backup e restauração após os testes de fumaça);
  - `src/main/java/br/usp/lti/newmoea/algorithm/fdea/**` e `src/main/java/br/usp/lti/newmoea/feedback/**` (100% preservados).

---

## 9. Lista de Testes Efetivamente Executados e Respectivos Resultados

### 9.1. Compilação Limpa (`mvn clean compile`)
- **Comando**: `mvn clean compile`
- **Resultado**: **`BUILD SUCCESS`** (`13` classes de produção compiladas com `javac [debug release 21]`, tempo total `3.488 s`).

### 9.2. Suíte Completa de Testes Automatizados (`mvn clean test`)
- **Comando**: `mvn clean test`
- **Resultado Global**: **`BUILD SUCCESS`** — **277 testes executados, 0 falhas, 0 erros, 0 ignorados** (tempo total `14.729 s`, cobertura analisada pelo JaCoCo sobre 31 classes):

| # | Classe de Teste | Pacote | Tarefa(s) | Testes Executados | Falhas | Erros | Ignorados | Tempo (s) |
|---:|---|---|---|---:|---:|---:|---:|---:|
| 1 | `AlgorithmIntegrationTest` | `br.usp.lti.newmoea.algorithm` | T05 | **19** | 0 | 0 | 0 | 0.810 |
| 2 | `MoeaddWeightValidationTest` | `br.usp.lti.newmoea.algorithm` | T03 / T11 | **22** | 0 | 0 | 0 | 0.450 |
| 3 | `NsgaIIIReferencePointsValidationTest` | `br.usp.lti.newmoea.algorithm` | T02 / T07 | **22** | 0 | 0 | 0 | 0.252 |
| 4 | `RveaReferenceDirectionsValidationTest` | `br.usp.lti.newmoea.algorithm` | T07 | **8** | 0 | 0 | 0 | 0.104 |
| 5 | `R2ExecutionObserverTest` | `br.usp.lti.newmoea.feedback` | Fase 1 | **3** | 0 | 0 | 0 | 0.033 |
| 6 | `OperatorAuditTest` | `br.usp.lti.newmoea.operator` | T06 | **7** | 0 | 0 | 0 | 0.087 |
| 7 | `HypervolumeAuditTest` | `br.usp.lti.newmoea.qualityindicator` | T08 | **12** | 0 | 0 | 0 | 0.558 |
| 8 | `ParetoFrontValidationTest` | `br.usp.lti.newmoea.qualityindicator` | T04 / T07 | **164** | 0 | 0 | 0 | 4.089 |
| 9 | `QualityIndicatorUnitTest` | `br.usp.lti.newmoea.qualityindicator` | T05 / T08 | **12** | 0 | 0 | 0 | 0.024 |
| 10 | `PerformanceProfilerTest` | `br.usp.lti.newmoea.util` | T09 | **8** | 0 | 0 | 0 | 0.236 |
| **Total** | **10 classes de teste** | — | **Fase 1 + Fase 2** | **277** | **0** | **0** | **0** | **6.643** |

### 9.3. Validações de Fumaça Fim a Fim (`-Druns=1`)
Executadas com backup prévio e restauração integral posterior do diretório `WFGStudy/`:

1. **Execução de Fumaça 1 (`WFG1`, $m=2$, `runs=1`)**:
   - **Comando**: `mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Druns=1" "-Dobjectives=2" "-Dproblem=WFG1"`
   - **Resultado**: **`BUILD SUCCESS`**.
   - **Evidências verificadas**:
     - `NSGAIII` inicializado com `rpssize: 100`.
     - Geração completa para os 11 algoritmos configurados no pipeline (`NSGAII`, `SPEA2`, `MOEADDRA`, `NSGAIII`, `MOEADD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`, `ESPEA`, `GWASFGA`) em `WFGStudy/data/<Algoritmo>/WFG1_2/`.
     - Arquivos `FUN0.csv`, `VAR0.csv`, `HV`, `NHV`, `IGD`, `IGD+`, `R2`, `BEST_*` e `MEDIAN_*` gravados separadamente.
     - Exemplo de saída em `WFGStudy/data/NSGAIII/WFG1_2/`: `HV = 0.2458790327094975`, `NHV = 0.6137520159079025`, `IGD = 0.014287734057703203`, `IGD+ = 0.41303518028036085`, `R2 = 0.33538565424606176` (confirmando a identidade $1 - \text{HV}/\text{HV}(R) = \text{NHV}$).
     - `WFGStudy/QualityIndicatorSummary.csv`, `WFGStudy/latex/` e `WFGStudy/R/` gerados sem erros.

2. **Execução de Fumaça 2 (`DTLZ2`, $m=3$, `runs=1`)**:
   - **Comando**: `mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Druns=1" "-Dobjectives=3" "-Dproblem=DTLZ2"`
   - **Resultado**: **`BUILD SUCCESS`**.
   - **Evidências verificadas**:
     - `NSGAIII` inicializado com `rpssize: 91`.
     - Todos os 5 indicadores (`HV`, `NHV`, `IGD`, `IGD+`, `R2`) calculados e gravados em arquivos separados para todos os algoritmos em `WFGStudy/data/<Algoritmo>/DTLZ2_3/`.
     - Exemplo de saída em `WFGStudy/data/NSGAIII/DTLZ2_3/`: `HV = 0.4114171683088568`, `NHV = 0.12059501174019938`, `IGD = 5.959921673676715E-4`, `IGD+ = 0.024066840462241147`, `R2 = 0.07368310331696205`.
     - Diretório `WFGStudy/` original restaurado integralmente após a inspeção.

---

## 10. Estado Final das Tarefas da Fase 2 (`T06`–`T11`)

| ID | Tarefa | Agente | Estado Final | Entregáveis Principais Verificados |
|---|---|---|---|---|
| **T06** | Auditoria de operadores comuns e mutação polinomial | Agente A | `completed` | `docs/operator-audit.md`, `OperatorAuditTest.java` (`7` testes passando) |
| **T11** | Auditoria completa e sincronização dos pesos do MOEA/DD | Agente B | `completed` | `docs/moead-weights-audit.md`, `docs/moead-weights-manifest.csv`, 29 cópias oficiais em `resources/weightVectorFiles/`, opção `-DreplaceZeroWeights=false`, `MoeaddWeightValidationTest.java` (`22` testes passando) |
| **T07** | Revalidação de NSGA-III/RVEA, persistência opcional e frentes de Pareto | Agente C | `completed` | `docs/reference-points-audit.md`, `docs/reference-fronts-dtlz.md`, `RveaReferenceDirectionsValidationTest.java` (`8` testes), `NsgaIIIReferencePointsValidationTest.java` (`22` testes), `ParetoFrontValidationTest.java` (`164` testes), flags opcionais desativadas por padrão |
| **T08** | Hipervolume, pontos de referência e normalização | Agente D | `completed` | `docs/hypervolume-reference-points.md`, atualização de `StaticParams.java`, `HypervolumeAuditTest.java` (`12` testes), `QualityIndicatorUnitTest.java` (`12` testes) |
| **T09** | Instrumentação e avaliação de desempenho | Agente E | `completed` | `PerformanceProfiler.java` (com `DEFAULT_HV_TIMEOUT_MS = 3_600_000L`), `PerformanceProfilerTest.java` (`8` testes), `docs/performance/performance-protocol.md`, `docs/performance/performance-results.csv` (`630` registros) |
| **T10** | Validação final, documentação e consolidação | Agente F | `completed` | `docs/final-validation-report.md`, atualização completa de `README.md` e consolidação de `HANDLER.md` (`277` testes + 2 execuções de fumaça aprovadas) |

---

## 11. Classificação Explícita: Implementações, Validações, Opções, Limitações e Trabalhos Futuros

Em conformidade com a Seção 12 de `TASK_COORDINATOR.md`, distingue-se explicitamente cada categoria:

### 11.1. Implementações Concluídas
1. Validador matemático de conjuntos de referência e vetores de peso ([`ReferenceSetValidator.java`](../src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java)) com geração canônica de 1 e 2 camadas (Das & Dennis / Deb & Jain), validação separada por camada do NSGA-III, validação das direções unitárias $\ell_2$ do RVEA, persistência opcional em CSV e substituição opcional $0 \mapsto 10^{-5}$.
2. Validador e gerador matemático/empírico de frentes de Pareto ([`ParetoFrontValidator.java`](../src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java)) cobrindo `WFG1–WFG9` e `DTLZ1–DTLZ7` em $m \in \{2, 3, 5, 8, 10, 15\}$.
3. Parametrização estática de pontos de referência de hipervolume por problema e dimensão $m$ no espaço bruto e no espaço normalizado ([`StaticParams.java`](../src/main/java/br/usp/lti/newmoea/study/StaticParams.java)).
4. Perfilador de desempenho computacional ([`PerformanceProfiler.java`](../src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java)) com separação entre algoritmo e indicadores, medição de CPU/Wall/Heap e timeout de segurança de 1 hora (`3.600.000 ms`) para cálculos de hipervolume.
5. Cópia fiel dos 29 arquivos oficiais de pesos do jMetal 7.8 para `resources/weightVectorFiles/` e `resources/weightVectorFiles/` e manifesto SHA-256 completo.

### 11.2. Validações Aprovadas
1. **277 testes automatizados** distribuídos em 10 classes JUnit 5 (`0` falhas, `0` erros, `0` ignorados).
2. **2 execuções de fumaça fim a fim** (`WFG1` em $m=2$ e `DTLZ2` em $m=3$ com `-Druns=1`), confirmando geração e separação de `HV` e `NHV`.
3. **Verificação de integridade de artefatos**: 100% dos arquivos de `MOEAD_Weights/` e `pareto_fronts/csv/` íntegros e sem alterações não rastreadas.

### 11.3. Validações Reprovadas (Artefatos Externos Legados Rejeitados)
1. `reference-calc.java` (script externo fora do repositório): reprovado na Fase 1 (T02) por gerar `0` pontos em $m=2, H=99$, apresentar erro de truncamento de $4 \times 10^{-5}$ em $m=3,5$ e reutilizar $H_1$ com translação incorreta $1/(m+l)$ em 2 camadas.
2. `Main-Hipervolume.java` (utilitário externo fora do repositório): reprovado na Fase 1 (T05) e Fase 2 (T08/T09) por usar semente não determinística (`System.nanoTime()`), ponto de referência escalar único sem normalização e direção contraditória.

### 11.4. Tarefas Bloqueadas
- **Nenhuma tarefa bloqueada** (`0` bloqueios ativos ao final da Fase 2; todas as tarefas `T01`–`T11` estão concluídas).

### 11.5. Funcionalidades Opcionais Desativadas por Padrão
Todas as 6 opções experimentais introduzidas na Fase 2 permanecem estritamente desativadas (`false`) por padrão no código e durante a execução normal do pipeline:

| Propriedade JVM | Valor Padrão | Método de Verificação no Código | Comportamento quando `false` (Padrão) |
|---|:---:|---|---|
| `-DpersistReferencePoints` | `false` | `ReferenceSetValidator.isPersistReferencePointsEnabled()` | Não grava arquivos em `resources/referencePoints/nsgaiii/` |
| `-DpersistReferenceDirections` | `false` | `ReferenceSetValidator.isPersistReferenceDirectionsEnabled()` | Não grava arquivos em `resources/referencePoints/rvea/` |
| `-DgenerateAllReferenceFronts` | `false` | `ParetoFrontValidator.isGenerateAllReferenceFrontsEnabled()` | Mantém DTLZ habilitado apenas para $m \le 3$ em `WFGStudy` e não gera arquivos em `resources/referenceFronts/pf_gen/analytical/` |
| `-DgenerateEmpiricalReferenceFronts` | `false` | `ParetoFrontValidator.isGenerateEmpiricalReferenceFrontsEnabled()` | Não gera frentes empíricas em `resources/referenceFronts/pf_gen/empirical/` |
| `-DreplaceZeroWeights` | `false` | `ReferenceSetValidator.isReplaceZeroWeightsEnabled()` | Não gera arquivos derivados em `resources/weightVectorFiles/weights_gen/` |
| `-DprofilePerformance` | `false` | `PerformanceProfiler.isProfilePerformanceEnabled()` | Não executa a bateria de benchmark ao final de `WFGStudy.main` |

### 11.6. Limitações Científicas e Técnicas Intrínsecas Documentadas
1. **Camada interna do `NSGAIII` no jMetal 7.8**: O construtor de `org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII` calcula a camada interna via $\frac{x_i+1}{m+1}$ em vez da contração $\frac{1}{2}x_i + \frac{1}{2m}$ de Deb & Jain (2014). O comportamento nativo foi preservado para manter fidelidade estrita ao jMetal 7.8.
2. **Duplicatas nos arquivos oficiais `W8D_156.dat` e `W10D_275.dat` do jMetal 7.8**: Os arquivos oficiais do jMetal para $m=8$ e $m=10$ concatenam as camadas $H_1=3$ e $H_2=2$ sem contração centrípeta, produzindo $8$ e $10$ vértices unitários duplicados.
3. **Divisão por zero em `GWASFGA` quando $w_i = 0$**: Enquanto `MOEA/DD` (`PBI`, `TCHE`, `AGG`) e `R2` operam com segurança sobre pesos com $w_i = 0$, o algoritmo complementar `GWASFGA` invoca `WeightVectors.invert` ($1/w_{i,j}$), exigindo pesos estritamente positivos ($w_{i,j} \ge 10^{-5}$) caso seja executado em $m \in \{3, 8, 10\}$ (nos quais `W3D_91.dat`, `W8D_156.dat` e `W10D_275.dat` contêm zeros exatos após sincronização com o jMetal oficial).
4. **Número fixo de variáveis $L = 20$ em `DTLZ1–DTLZ7` no `WFGStudy.java`**: A chamada `new DTLZ*(L, m)` com $L = 20$ define $n = 20$ variáveis de decisão totais, fazendo com que o número de variáveis de distância $k = n - m + 1 = 21 - m$ varie de $k=19$ ($m=2$) a $k=6$ ($m=15$). Embora $k \ge 6 \ge 1$ preserve a frente de Pareto teórica $g(\mathbf{x}_M) = 0$ em todas as dimensões do estudo, difere da convenção clássica de manter $k \in \{5, 10, 20\}$ constante.
5. **Anomalias nas frentes de referência originais do jMetal (`pareto_fronts/csv/`)**: Presença de $f_2 \in [-2, 4]$ em `WFG3.2D.csv`, duplicatas massivas em `DTLZ5.3D.csv` e `DTLZ6.3D.csv`, $109$ pontos dominados em `DTLZ7.3D.csv` e duplicatas em `WFG` para $m \ge 5$.
6. **Custo exponencial `#P-hard` do Hipervolume Exato em $m=15$ e recálculo em `NormalizedHypervolume`**:
   - Em $m=3$, `NormalizedHypervolume.compute` recalcula o HV dos $10.000$ pontos da frente de referência a cada invocação ($\approx 68\text{ ms}$ vs $0.18\text{ ms}$ do `PISAHypervolume`).
   - Em $m=15$, frentes aproximadas com $N=135$ pontos mutuamente não dominados no interior de $[0,1)^{15}$ (como as geradas pelo `MOEA/DD` em `WFG4`) provocam explosão combinatória em `PISAHypervolume` ($> 40.863\text{ s}$ de CPU), exigindo o uso do timeout de segurança (`3.600.000 ms`) ou priorização de `R2`, `IGD+` e `IGD` em $m=15$.
7. **Algoritmos complementares legados em `WFGStudy.configureAlgorithmList`**: Além dos 6 algoritmos principais do estudo (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`), o método `configureAlgorithmList` ainda instancia os 5 algoritmos complementares de controle (`NSGA-II`, `SPEA2`, `MOEA/D-DRA`, `ESPEA` e `GWASFGA`).

### 11.7. Propostas de Trabalhos Futuros (Próxima Etapa da Pesquisa)
1. **Integração do Sinal $\Delta R^2$ com as Hiper-Heurísticas de Seleção**: Conectar `R2ExecutionObserver` (`R2Observation(step, r2Value, deltaR2, timestampMs)`) aos mecanismos de crédito e seleção de `AdapHH`, `EA-ILS` e `LAST-RL`.
2. **Foco nos 6 Algoritmos Principais nas Execuções de Larga Escala**: Isolar mediante flag ou configuração dedicada os 6 algoritmos principais do estudo (desabilitando os 5 algoritmos legados `NSGA-II`, `SPEA2`, `MOEA/D-DRA`, `ESPEA` e `GWASFGA`, especialmente porque `GWASFGA` sofre `NaN` com zeros exatos em `W3D_91`, `W8D_156` e `W10D_275`).
3. **Mitigação do Gargalo de Hipervolume em $m \ge 10$**: Em baterias completas de 30 repetições para $m \in \{10, 15\}$, utilizar `R2`, `IGD+` e `IGD` como indicadores primários em alta dimensão ou empregar cache do HV da frente de referência e estimador Monte Carlo determinístico (com semente fixa controlada por `JMetalRandom`) apenas quando `PISAHypervolume` atingir o timeout de segurança.

---

## 12. Evidências de Preservação e Rastreabilidade dos Artefatos Originais

1. **Verificação via Git (`git status --short`)**:
   - Nenhum arquivo dentro de `MOEAD_Weights/` (48 arquivos) ou `pareto_fronts/csv/` (160 arquivos) consta como modificado ou excluído.
2. **Verificação Criptográfica SHA-256**:
   - Os 29 arquivos oficiais em `resources/weightVectorFiles/` possuem hash SHA-256 bit a bit idêntico aos originais de `jMetal/resources/weightVectorFiles/moead/` (verificado automaticamente em `MoeaddWeightValidationTest.testOfficialJMetalCopiesAndSha256Integrity`).
   - Os 68 arquivos ativos em `pareto_fronts/csv/` possuem hash SHA-256 bit a bit idêntico aos originais de `jMetal/resources/referenceFrontsCSV/` (documentado em `docs/reference-fronts-dtlz.md`).
3. **Preservação de `WFGStudy/`**:
   - Os arquivos preexistentes em `WFGStudy/` foram copiados antes dos testes de fumaça e integralmente restaurados ao término da inspeção.

---

## 13. Recomendação da Próxima Ação do Projeto

Com **100% das tarefas da Fase 1 (`T01`–`T05`) e da Fase 2 (`T06`–`T11`) concluídas e validadas** (`277` testes automatizados passando e 2 testes de fumaça aprovados), recomenda-se:

1. **Encerrar formalmente a Fase 2** no `HANDLER.md`, marcando `T10` como `completed`.
2. **Iniciar a Fase 3 (Integração das Hiper-Heurísticas e Execução Experimental)**:
   - Acoplar o sinal de recompensa escalar $\Delta R^2$ fornecido por [`R2ExecutionObserver.java`](../src/main/java/br/usp/lti/newmoea/feedback/R2ExecutionObserver.java) aos controladores de alto nível (`AdapHH`, `EA-ILS`, `LAST-RL`) para seleção adaptativa entre os 6 MOEAs auditados (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA` e `AGE-MOEA-II`).
   - Configurar a execução experimental completa (`-Druns=30`) priorizando os 6 algoritmos principais do estudo e aplicando o protocolo de desempenho e segurança de hipervolume estabelecido em `T08` e `T09`.

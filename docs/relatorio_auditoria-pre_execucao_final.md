# Relatório de Auditoria Zero-Trust — Projeto `hypeMOEA`

- **Data da Auditoria:** 17/11/2026
- **Escopo:** Auditoria completa e re-teste empírico em Ordem Cronológica Reversa (`T31` $\to$ `T01`) sob abordagem *Zero-Trust* (desconsiderando relatórios prévios de agentes e validando diretamente contra o código-fonte, artefatos físicos em disco, histórico de commits e execução real da suíte Maven e do pipeline `WFGStudy`).
- **Ambiente Verificado:**
  - **SO:** Windows 10 (`10.0`, `amd64`)
  - **Java:** OpenJDK `21.0.9` (`Eclipse Adoptium 21.0.9+10-LTS`)
  - **Maven:** Apache Maven `3.9.9`
  - **Dependência Principal:** `org.uma.jmetal:jmetal-core:7.8`, `jmetal-algorithm:7.8`, `jmetal-problem:7.8`, `jmetal-lab:7.8` (confirmado em [pom.xml](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/pom.xml#L24-L45))

---

## 1. Sumário Executivo da Auditoria Zero-Trust

A execução independente da suíte de testes (`mvn clean test`) e de um *smoke test* real do estudo (`mvn exec:java -Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy -Dstudy.outputDirectory=WFGStudy/tests/audit_smoke -Druns=1 -Dobjectives=2 -Dproblem=WFG1`) revelou divergências críticas entre o estado declarado nos relatórios anteriores ([HANDLER.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/HANDLER.md) e [README.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/README.md), que afirmavam *"370 testes executados, 0 falhas"* e *"prontidão total"*) e a realidade do repositório:

1. **Suíte de Testes Quebrada (`373` testes executados, `366` aprovados, `7` FALHAS):**
   - Todas as 7 falhas ocorrem em [MoeaddWeightValidationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java).
   - **Causa-raiz:** No commit `f5b1eef`, um agente havia copiado 29 arquivos oficiais do jMetal para dentro do diretório `MOEAD_Weights/` (elevando de 43 para 48 arquivos) e escreveu `MoeaddWeightValidationTest` assumindo esse estado. Posteriormente, no commit `2d6f257` (*"Update weight validation and reference front handling; remove obsolete files"*), a pasta `MOEAD_Weights/` foi revertida para seus **43 arquivos originais** do commit `6a6ecc2` porque **foi constatado que alterações anteriores haviam modificado indevidamente arquivos dentro de `MOEAD_Weights/` (antes mesmo de a pasta passar a ser tratada estritamente como legada), violando a regra de imutabilidade desses artefatos**, enquanto os 29 arquivos oficiais permaneceram em `resources/weightVectorFiles/`. Contudo, `MoeaddWeightValidationTest` **não foi atualizado** após a reversão e continua exigindo que `MOEAD_Weights/` possua os hashes SHA-256 e as propriedades (zeros exatos e duplicatas da camada interna) de `resources/weightVectorFiles/`.
2. **Desconexão Arquitetural entre Utilitários das Fases 3/4 e o Pipeline Real ([WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java)):**
   - As tarefas `T13`, `T15`, `T17`, `T19`, `T20`, `T21` e `T30` foram implementadas como classes utilitárias isoladas em `src/main/java/br/usp/lti/newmoea/util/` e `statistics/`, com testes unitários que testam apenas as próprias classes utilitárias.
   - **Nenhuma dessas classes foi integrada ao fluxo de execução real de `WFGStudy.main()`**, que continua utilizando os componentes padrão do jMetal (`ExecuteAlgorithms`, `ComputeQualityIndicators`, `GenerateLatexTablesWithStatistics`, `GenerateWilcoxonTestTablesWithR`, `GenerateFriedmanTestTables`) sem wrapper de orçamento (`EvaluationBudgetProblem`), sem executor isolado por seed (`IsolatedJobExecutor`), sem filtragem de dominadas (`ResultFrontContract`), sem normalização analítica (`ObjectiveNormalizationContract`), sem timeout/fallback Monte Carlo para HV em $m \ge 10$ (`ProcessIsolatedHypervolumeExecutor` / `MonteCarloHypervolume`) e sem invocação automática de `KruskalWallisTest` / `DunnPostHocTest` ou regeneração de `artifact-manifest.csv`.
3. **Falha de Execução (`FileNotFoundException`) ao Redirecionar `study.outputDirectory` para Subpastas (`WFGStudy/tests/...`):**
   - Ao usar `-Dstudy.outputDirectory=WFGStudy/tests/audit_smoke` (conforme exigido por `AGENTS.md` e testado apenas no pré-flight por `WFGStudyPreflightTest`), as etapas `ExecuteAlgorithms` e `ComputeQualityIndicators` funcionam, mas `GenerateLatexTablesWithStatistics` ([WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L228)) **quebra com `FileNotFoundException`** porque o jMetal 7.8 concatena `experimentName` no nome do arquivo `.tex` (`WFGStudy/tests/audit_smoke/latex/WFGStudy/tests/audit_smoke.tex`).
4. **Bug Silencioso no Número de Gerações de `DTLZ5` e `DTLZ6` para $m=10$ e $m=15$:**
   - Em [StaticParams.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L101-L152), os blocos `case 10:` e `case 15:` de `getMaxGenerations` possuem cases apenas para `"DTLZ1"`, `"DTLZ2"`, `"DTLZ3"`, `"DTLZ4"`, `"DTLZ7"` e `"WFG"`, omitindo `"DTLZ5"` e `"DTLZ6"`. Como não há `default` interno nos switches de `case 10` e `case 15`, a execução cai no `default` externo (linha 151) e retorna **`1000` gerações** em vez das **`1500` gerações ($m=10$)** e **`2000` gerações ($m=15$)** especificadas no [README.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/README.md#L121-L130).

---

## 2. Matriz Consolidada de Validação (Ordem Cronológica Reversa: `T31` $\to$ `T01`)

| ID | Fase | Descrição Resumida | Status na Auditoria | Testes Executados | Veredito Técnico |
| :--- | :---: | :--- | :---: | :---: | :--- |
| **T33** | Pós-Ajustes | Re-Auditoria Zero-Trust Completa em Ordem Cronológica Reversa (`T32` $\to$ `T01`) | **PENDENTE (PÓS-CORREÇÕES)** | Execução real tarefa a tarefa | Quando todas as pendências forem resolvidas, reexecutar toda a verificação/auditoria em ordem cronológica reversa (`T32` $\to$ `T01`), testando tarefa por tarefa do zero sem assumir nada como concluído. |
| **T32** | Pré-Campanha | Preservação de Runs Anteriores, Independência Estocástica e Reprodutibilidade Exata das 30 Execuções | **PARCIAL (REQUER INTEGRAÇÃO)** | `IsolationAndReproducibilityTest` (4/4 pass) + Auditoria de Bytecode `ExecuteAlgorithms` / `ComputeQualityIndicators` | `ExecuteAlgorithms` preserva `FUNx.csv` existentes da mesma configuração, mas `ComputeQualityIndicators.resetIndicatorFiles()` apaga indicadores de problemas não incluídos no lote atual se `problemList` for filtrado; falta integrar `IsolatedJobExecutor` em `WFGStudy.main()` para garantir independência real sem corrida em `JMetalRandom` e reprodutibilidade bit-a-bit por `runId`. |
| **T31** | Fase 4 | Validação Final da Fase 4 e Sincronização Documental | **REPROVADA (PARCIAL)** | `mvn test` (`366/373` pass) | Documentação declarou `370/370` verdes, mas há 7 falhas em `MoeaddWeightValidationTest` e `artifact-manifest.csv` desatualizado. |
| **T30** | Fase 4 | Atualização do Manifesto e Rastreabilidade para Caminhos Canônicos | **PARCIAL** | `CanonicalArtifactResolutionAndManifestTest` (5/5 pass) | Método `exportActiveArtifactManifestCsv` funciona em teste, mas `docs/auditoria/artifact-manifest.csv` em disco não foi atualizado (ainda lista `MOEAD_Weights/` e `pareto_fronts/csv/`). |
| **T29** | Fase 4 | Atualização dos Testes para Conjuntos Canônicos | **REPROVADA (PARCIAL)** | `ReferenceFrontCanonicalPriorityTest` (4/4 pass), `MoeaddWeightValidationTest` (5/12 pass) | Novos testes de frentes/pesos canônicos passam, mas `MoeaddWeightValidationTest` falhou em 7 testes após reversão de `MOEAD_Weights/`. |
| **T28** | Fase 4 | Refatoração da Resolução e Pré-voo de Frentes de Referência | **PARCIAL** | `ReferenceFrontCanonicalPriorityTest` (4/4 pass) | `ParetoFrontValidator` prioriza `resources/referenceFronts/` $\to$ `pf_gen/`, mas falta extração de arquivos oficiais do jMetal ausentes, falta gerador analítico por código para WFG na Prioridade 3 e `WFGStudy.resolveReferenceFrontForStudy` ignora `-Dstudy.referenceFrontDirectory`. |
| **T27** | Fase 4 | Política `replaceZeroFronts` (Default `false`) | **APROVADA** | `CanonicalArtifactResolutionAndManifestTest` (pass) | `ParetoFrontValidator` e `WFGStudy` aplicam `replaceZeroFronts=false` por padrão e geram variante `0 -> 1e-5` isolada em `resources/referenceFronts/pf_lowzero_gen/` quando `true`. |
| **T26** | Fase 4 | Resolução Canônica de Pesos `MOEA/DD` e `replaceZeroWeights` | **PARCIAL** | `CanonicalArtifactResolutionAndManifestTest` (pass) | `ReferenceSetValidator` resolve `resources/weightVectorFiles/` $\to$ `weights_gen/` (e `weights_lowzero_gen/` quando `replaceZeroWeights=true`); falta flag `-Dstudy.allowMoeadWeightsInMemory` em `WFGStudy`. |
| **T25** | Fase 4 | Auditoria de Referências Legadas e Flags `replaceZero*` | **APROVADA** | Inspeção documental e código | Relatórios `analise_flags_lowzero.md` e `auditoria_referencias_legadas.md` coerentes com o código real. |
| **T24** | Fase 3 | Governança, Reconciliação e Relatório Final Fase 3 | **REPROVADA (PARCIAL)** | Inspeção documental vs código | Relatórios declararam integração completa da Fase 3, omitindo que `WFGStudy.main()` não invoca os novos componentes. |
| **T23** | Fase 3 | Desacoplamento de `WFGStudy`, CLI/Flags e Proteção de `WFGStudy/` | **PARCIAL** | `WFGStudyPreflightTest` (4/4 pass) + Smoke Test (FALHOU no LaTeX) | Flags CLI funcionam no preflight, mas `-Dstudy.outputDirectory=WFGStudy/tests/...` quebra `GenerateLatexTablesWithStatistics` com `FileNotFoundException`. |
| **T22** | Fase 3 | Profiling Empírico sob Condições Reais da Fase 3 | **PARCIAL** | `PerformanceProfilerPhase3Test` (2/2 pass) | Teste e matriz CSV gerados com sucesso, porém o profiling de HV em `m=15` confirma gargalo de `40.863 s` (`11,35 h`) para 1 frente em `PISAHypervolume`. |
| **T21** | Fase 3 | Inferência Não-Paramétrica (`Kruskal-Wallis`, `Dunn-Holm`, $A_{12}$) | **PARCIAL** | `KruskalWallisTestTest` (4/4 pass) | Classes `KruskalWallisTest` e `DunnPostHocTest` matematicamente corretas e testadas contra R, mas não integradas ao `WFGStudy.main()`. |
| **T20** | Fase 3 | Mitigação de Gargalo HV (`m >= 10`) e Isolamento de Processo | **PARCIAL** | `MonteCarloHypervolumeTest` (4/4), `ProcessIsolatedHypervolumeTest` (4/4) | `MonteCarloHypervolume` e `ProcessIsolatedHypervolumeExecutor` funcionam isoladamente, mas `WFGStudy` ainda usa `PISAHypervolume` direto sem timeout. |
| **T19** | Fase 3 | Contrato de Frentes de Saída (`ResultFrontContract`) | **PARCIAL** | `ResultFrontContractTest` (4/4 pass) | `ResultFrontContract` filtra dominadas e epsilon-duplicatas, mas `WFGStudy` grava `algorithm.result()` bruto nos CSVs. |
| **T18** | Fase 3 | Migração Canônica e Manifesto SHA-256 | **APROVADA (HISTÓRICA)** | `ReferenceFrontPhysicalAndMathAuditTest` (147/147 pass) | Artefatos canônicos migrados e verificados; sucedido na Fase 4 por `T26`–`T30`. |
| **T17** | Fase 3 | Contrato de Normalização e Ponto de Referência Explícito | **PARCIAL** | `NormalizationAndHvContractTest` (31/31 pass) | `ObjectiveNormalizationContract` e `StaticParams.getHypervolumeReferencePoint` passam nos testes, mas `WFGStudy` usa `ComputeQualityIndicators` padrão do jMetal. |
| **T16** | Fase 3 | Auditoria Física e Matemática das Frentes de Referência | **APROVADA** | `ReferenceFrontPhysicalAndMathAuditTest` (147/147 pass) | Todas as 90 frentes (54 WFG + 36 DTLZ) auditadas quanto a NaN/Inf, dominância e limites geométricos. |
| **T15** | Fase 3 | Isolamento de Execução e Sementes Determinísticas | **PARCIAL** | `IsolationAndReproducibilityTest` (4/4 pass) | `IsolatedJobExecutor` e `R2ExecutionObserver` thread-safe passam nos testes, mas `WFGStudy.main()` usa `ExecuteAlgorithms` concorrente do jMetal (8 cores compartilhando `JMetalRandom`). |
| **T14** | Fase 3 | Reconciliação Científica do `FDEA` e Remoção de Código Morto | **APROVADA** | `FdeaIdentityAndLegacyAuditTest` (5/5 pass) | Código morto removido (`InvertedGenerationalDistance`, `Hypervolume2`, `shiftValues`, `g1`/`g2`) e variante `FDEA` documentada. |
| **T13** | Fase 3 | Reconciliação do Orçamento de Avaliações (`EvaluationBudgetProblem`) | **PARCIAL** | `EvaluationBudgetAuditTest` (6/6 pass) | `EvaluationBudgetProblem` trunca avaliações em $B$, mas **não é utilizado** em `WFGStudy.configureAlgorithmList()`, mantendo estouro de orçamento no `NSGA-III` e `AGE-MOEA`/`II`. |
| **T12** | Fase 3 | Gate G0/G1: Escopo Científico e Decisões Metodológicas | **APROVADA** | Inspeção documental | `docs/decisions/G0-G1-scope-and-decisions.md` formaliza G0 e G1. |
| **T11** | Fase 2 | Sincronização de `MOEAD_Weights` e Validação (`MoeaddWeightValidationTest`) | **REPROVADA** | `MoeaddWeightValidationTest` (`12` runs, `7` FALHAS) | Reversão de `MOEAD_Weights/` no commit `2d6f257` quebrou 7 asserções que comparavam `MOEAD_Weights/` com `resources/weightVectorFiles/`. |
| **T10** | Fase 2 | Saneamento Arquitetural e Mitigação de Dívida Técnica | **APROVADA** | `mvn test-compile` + Inspeção | Planos e análises documentados em `docs/resolucao_duplicatas_arquitetura.md` e `docs/plano_mitigacao_divida_tecnica.md`. |
| **T09** | Fase 2 | Consolidação da Fase 1 em `HANDLER.md` e `README.md` | **APROVADA** | Inspeção documental | Histórico consolidado em `HANDLER.md` e `README.md`. |
| **T08** | Fase 2 | Investigação de Duplicatas e Código Legado | **APROVADA** | Inspeção documental | `docs/duplicatas_analise-10do11.md` detalha classes duplicadas e código morto. |
| **T07** | Fase 2 | Benchmark e Profiling Inicial de Performance | **APROVADA** | `PerformanceProfilerTest` (2/2 pass) | `PerformanceProfiler` implementado e testado em `PerformanceProfilerTest`. |
| **T06** | Fase 2 | Validação Pré-Voo das Frentes de Referência (`ParetoFrontValidator`) | **APROVADA** | `ParetoFrontValidationTest` (74/74 pass) | Validação estrutural e numérica das frentes WFG1–WFG9 e DTLZ1–DTLZ7. |
| **T05** | Fase 1 | Validação dos Indicadores (`HV`, `NHV`, `IGD`, `IGD+`, `R2`) | **APROVADA** | `QualityIndicatorUnitTest` (14/14 pass) | Testes analíticos de indicadores em 2D/3D aprovados. |
| **T04** | Fase 1 | Testes de Integração dos 6 Algoritmos e `R2ExecutionObserver` | **APROVADA** | `AlgorithmIntegrationTest` (36/36), `R2ExecutionObserverTest` (2/2) | Os 6 MOEAs executam sem exceções em WFG1 e DTLZ1 ($m \in \{2,3,5\}$) e `R2ExecutionObserver` registra histórico. |
| **T03** | Fase 1 | Auditoria dos Vetores de Pesos do `MOEA/DD` | **APROVADA (COM RESSALVA EM T11)** | Inspeção de `docs/moead-weights-audit.md` | Auditoria original identificou corretamente zeros (`1e-5`) e contração em `MOEAD_Weights/`; regressão ocorreu apenas no teste de `T11`. |
| **T02** | Fase 1 | Validação dos Pontos de Referência do `NSGA-III` | **APROVADA** | `NsgaIIIReferencePointsValidationTest` (18/18 pass) | Contagem binomial $\binom{H_1+m-1}{m-1} + \binom{H_2+m-1}{m-1}$ e soma unitária validadas para $m \in \{2,3,5,8,10,15\}$. |
| **T01** | Fase 1 | Auditoria de Parâmetros e Conformidade com a Literatura | **PARCIAL** | Inspeção de `StaticParams.java` e `WFGStudy.java` | Parâmetros conferidos, mas `StaticParams.getMaxGenerations` omite `"DTLZ5"` e `"DTLZ6"` em $m=10$ e $m=15$ (retornando `1000` em vez de `1500`/`2000`). |

---

## 3. Análise Detalhada em Ordem Cronológica Reversa (`T33` $\to$ `T01`)

### 3.0. Tarefas de Garantia de Execução e Re-Auditoria Pós-Correções (`T33` $\to$ `T32`)

#### **Tarefa `T33` — Re-Auditoria Zero-Trust Completa em Ordem Cronológica Reversa Pós-Resolução (`T32` $\to$ `T01`)**
- **Status:** **PENDENTE (A SER EXECUTADA APÓS A RESOLUÇÃO DE TODAS AS PENDÊNCIAS)**
- **Objetivo e Escopo:**
  - Assim que todas as correções arquiteturais, integrações no `WFGStudy.java` e ajustes de testes listados neste documento forem concluídos, **executar novamente uma bateria completa de verificação e auditoria em Ordem Cronológica Reversa (`T32` $\to$ `T01`)**, exatamente sob a mesma metodologia *Zero-Trust* aplicada neste relatório.
- **Critérios Obrigatórios de Execução:**
  1. **Desconfiança Total de Relatórios Prévios:** Nenhum item ou tarefa poderá ser considerado concluído apenas porque foi descrito como "pronto" ou "validado" por um agente durante a etapa de correção.
  2. **Re-teste Empírico Tarefa por Tarefa (da mais recente para a mais antiga):** Como correções nas tarefas mais recentes (`T32` $\to$ `T25`) e no `WFGStudy.java` podem introduzir regressões silenciosas nas garantias das fases anteriores (`T24` $\to$ `T01`), cada tarefa deverá ser revalidada individualmente com execução real de código, inspeção de arquivos gerados em disco, verificação de hashes SHA-256 e *smoke tests* ponta a ponta antes de autorizar o disparo da campanha experimental definitiva de 30 runs.

---

#### **Tarefa `T32` — Verificação de Preservação de Resultados sem Sobrescrita, Independência Real e Reprodutibilidade Determinística das Execuções Independentes (`runs`)**
- **Status:** **PARCIAL (Mecanismo pronto em `IsolatedJobExecutor`, mas ainda desconectado de `WFGStudy.main()`)**
- **Evidências Inspecionadas:** Bytecode de `ExecuteAlgorithms.class`, `ExperimentAlgorithm.class` e `ComputeQualityIndicators.class` (`jmetal-lab-7.8.jar`), [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L196-L231) e [IsolatedJobExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java#L427-L630).
- **Análise Técnica Detalhada dos 3 Requisitos:**
  1. **As execuções independentes são respeitadas e seus arquivos NÃO são sobrescritos em uma nova execução, mantendo todos os resultados?**
     - **Para `FUN{runId}.csv` e `VAR{runId}.csv` (`ExecuteAlgorithms`):** No bytecode de `ExecuteAlgorithms.checkTaskStatus()` do jMetal 7.8, antes de rodar qualquer job `(algoritmo, problema, runId)`, o jMetal verifica `new File("<baseDir>/data/<ALG>/<PROB>/FUN<runId>.csv").exists()`. Se o arquivo `FUN<runId>.csv` **já existir**, o job **não é adicionado à fila de execução** e o arquivo existente é preservado sem ser reescrito. Assim, se você rodar `-Druns=5` (gerando `FUN0.csv` a `FUN4.csv`) e depois rodar `-Druns=30`, ele manterá intactos `FUN0.csv`..`FUN4.csv` e executará apenas `runId = 5` até `29`.
     - **Risco Crítico Identificado em `ComputeQualityIndicators` e `QualityIndicatorSummary.csv`:** No bytecode de `ComputeQualityIndicators.run()`, o jMetal chama `resetIndicatorFiles()` e `writeSummaryFile()`, que **apagam e recriam do zero** os arquivos de indicadores (`HV`, `NHV`, `IGD`, `IGD+`, `R2`) e o arquivo mestre `QualityIndicatorSummary.csv` **apenas para os problemas e `INDEPENDENT_RUNS` configurados na invocação atual**!
       - **Consequência prática:** Se você rodar primeiro `-Dobjectives=2,3` e depois rodar `-Dobjectives=5` apontando para o mesmo diretório `WFGStudy`, os arquivos `FUNx.csv` e `VARx.csv` de `m=2,3` continuarão salvos nas pastas `WFGStudy/data/<ALG>/<PROB>_2/`, **mas o arquivo `WFGStudy/QualityIndicatorSummary.csv` e as tabelas `.tex`/`.R` serão sobrescritos contendo apenas `m=5`** (a menos que ao final seja rodada a consolidação com todas as dimensões `-Dobjectives=2,3,5,8,10,15`, ou que o pipeline seja ajustado para preservar/mesclar o `QualityIndicatorSummary.csv` e não perder execuções caso um arquivo `FUN0.csv` falhe ou fique com `0 bytes`).
  2. **As execuções (`run = 0 .. 29`) são realmente independentes entre si?**
     - **No estado atual de `WFGStudy.main()` (`new ExecuteAlgorithms<>(experiment).run()`): NÃO há garantia limpa de independência nem de isolamento.** Como `WFGStudy.configureAlgorithmList()` instancia todas as `ExperimentAlgorithm` de uma só vez e `ExecuteAlgorithms` dispara `parallelStream()` com 8 threads compartilhando a mesma instância global não-sincronizada `JMetalRandom.getInstance()`, ocorre condição de corrida (*race condition*) no gerador pseudoaleatório entre threads concorrentes, acoplando o fluxo de números aleatórios de diferentes runs e algoritmos à ordem de escalonamento das threads do sistema operacional.
     - **Com `IsolatedJobExecutor` (`T15`):** A independência estatística genuína entre as runs $r \in \{0, \dots, 29\}$ é garantida porque cada tupla `(baseSeed, problemName, m, algorithmName, runId)` recebe uma semente única de 64 bits derivada via **SHA-256 + avalanching SplitMix64** (`IsolatedJobExecutor.computeDeterministicSeed`), onde a alteração de `runId` (ex.: `run=0` vs `run=1`) inverte ~50% dos bits da semente inicial sem qualquer sobreposição de sequência e instanciando novos operadores e problemas do zero (*lazy instantiation*).
  3. **Tem como, mesmo sendo execuções independentes, reproduzi-las exatamente?**
     - **No estado atual de `WFGStudy.main()`: NÃO**, pois `WFGStudy` não define semente por `runId` e sofre interferência do `parallelStream()` sobre `JMetalRandom.getInstance()`.
     - **Com a integração de `IsolatedJobExecutor` em `WFGStudy.main()`: SIM, 100% reproduzível individualmente ou em lote!** Como a semente `effectiveSeed` de cada execução independente é uma função matemática pura de `(baseSeed, problemName, m, algorithmName, runId)` (ex.: `"hypeMOEA:v1|baseSeed=20260409|problem=WFG1|m=2|algorithm=NSGA-III|run=7"`), qualquer execução `runId` é simultaneamente **independente** das demais ($r \ne r'$) e **100% determinística e reproduzível bit-a-bit** (gerando exatamente o mesmo `FUN<runId>.csv` e `VAR<runId>.csv` com o mesmo hash SHA-256, quer você rode todas as 30 runs juntas, quer reexecute isoladamente apenas a `runId = 7`).
- **Correção Necessária:**
  1. Integrar `IsolatedJobExecutor` (e o registro do manifesto de sementes/hashes por `runId`) diretamente na execução de `WFGStudy.main()`, garantindo que cada `runId` configure sua `effectiveSeed` determinística antes de rodar e não sofra condição de corrida em `JMetalRandom`.
  2. Garantir que a verificação de retomada (*resume*) cheque não apenas `File.exists()`, mas também que `FUN<runId>.csv` e `VAR<runId>.csv` são não-vazios (`Files.size > 0`) e válidos (`ResultFrontContract.verifyFunVarAlignment`), jamais sobrescrevendo execuções já concluídas com sucesso, e garantindo que a consolidação de indicadores (`QualityIndicatorSummary.csv`) preserve todos os resultados existentes no diretório do estudo.

---

### 3.1. Fase 4 — Rastreabilidade Canônica e Políticas `replaceZero*` (`T31` $\to$ `T25`)

#### **Tarefa `T31` — Validação Final da Fase 4 e Sincronização Documental**
- **Especificação:** [task31.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task31.md)
- **Status:** **REPROVADA (PARCIAL)**
- **Testes Executados:** `mvn clean test`
- **Resultado Obtido:** `Tests run: 373, Failures: 7, Errors: 0, Skipped: 0`.
- **Explicação Técnica (Por que falhou):**
  - O critério de aceitação de `T31` exigia que `mvn test` passasse integralmente sem falhas e que a documentação (`README.md`, `HANDLER.md`, `docs/auditoria/artifact-manifest.csv`) refletisse fielmente o estado real do código.
  - Embora `README.md` e `HANDLER.md` tenham sido atualizados afirmando que `mvn test` passou com `370` testes e `0` falhas, a execução real da suíte completa possui `373` testes e **falha em 7 testes** da classe [MoeaddWeightValidationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java).
  - Além disso, o arquivo físico [artifact-manifest.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-manifest.csv) nunca foi atualizado em disco para refletir os caminhos canônicos da Fase 4 (`resources/weightVectorFiles/` e `resources/referenceFronts/`).

---

#### **Tarefa `T30` — Atualização do Manifesto e Rastreabilidade para Caminhos Canônicos**
- **Especificação:** [task30.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task30.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `CanonicalArtifactResolutionAndManifestTest` (`5/5` aprovados).
- **Evidências no Código:**
  - [ArtifactManifestEntry.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ArtifactManifestEntry.java#L11-L162) foi estendido com os campos `activePath`, `legacyPath`, `sourceRole`, `zeroReplacementApplied`, `replacedZeroCount` e `selectionReason`.
  - [ReferenceSetValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java#L417-L567) implementa `buildActiveArtifactManifestEntries` e `exportActiveArtifactManifestCsv`.
- **Explicação Técnica (Por que passou nos testes mas ficou parcial no projeto):**
  - O teste unitário [CanonicalArtifactResolutionAndManifestTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/util/CanonicalArtifactResolutionAndManifestTest.java#L139-L176) exporta o manifesto para um diretório temporário (`@TempDir`) e valida o cabeçalho de 15 colunas.
  - Entretanto, o método `exportActiveArtifactManifestCsv` **nunca foi executado contra o arquivo real** [docs/auditoria/artifact-manifest.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-manifest.csv) nem é chamado em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java). Como consequência, [docs/auditoria/artifact-manifest.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-manifest.csv#L1-L20) ainda possui o formato antigo de 9 colunas da `T18` e aponta para `MOEAD_Weights/W2D_100.dat` e `pareto_fronts/csv/WFG1.2D.csv`.

---

#### **Tarefa `T29` — Atualização dos Testes Automatizados para Conjuntos Canônicos**
- **Especificação:** [task29.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task29.md)
- **Status:** **REPROVADA (PARCIAL)**
- **Testes Executados:**
  - [ReferenceFrontCanonicalPriorityTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/qualityindicator/ReferenceFrontCanonicalPriorityTest.java): `4/4` aprovados.
  - [CanonicalArtifactResolutionAndManifestTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/util/CanonicalArtifactResolutionAndManifestTest.java): `5/5` aprovados.
  - [WFGStudyPreflightTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/WFGStudyPreflightTest.java): `4/4` aprovados.
  - [MoeaddWeightValidationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java): `12` executados, `5` aprovados, **`7` REPROVADOS**.
- **Explicação Técnica (Por que falhou):**
  - `T29` exigia que os testes de pesos do `MOEA/DD` validassem a prioridade de `resources/weightVectorFiles/` e tratassem `MOEAD_Weights/` como legado protegido (com zeros substituídos por `1e-5` e camadas internas contraídas).
  - Contudo, em [MoeaddWeightValidationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java#L47-L49), a constante `WEIGHTS_DIR` continua apontando para `"MOEAD_Weights"`, enquanto os métodos `testActiveMoeadWeightFilesIntegrity`, `testMoeadWeightsVsZeroReplacedAndPureCanonical` e `testOfficialJMetalCopiesAndSha256Integrity` exigem que `WEIGHTS_DIR` contenha os arquivos puros do jMetal (`resources/weightVectorFiles/`).

---

#### **Tarefa `T28` — Refatoração da Resolução e Pré-voo de Frentes de Referência**
- **Especificação:** [task28.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task28.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `ReferenceFrontCanonicalPriorityTest` (`4/4` aprovados).
- **Explicação Técnica:**
  - [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1503-L1589) implementa a hierarquia canônica:
    1. **Prioridade 1:** `resources/referenceFronts/` (`OFFICIAL` / jMetal nativo)
    2. **Prioridade 2:** `resources/referenceFronts/pf_gen/` (`PF_GEN` / gerado puro)
    3. **Prioridade 3:** Geração analítica por código (somente quando ausente nas prioridades 1 e 2)
  - **Ressalva 1 (`study.referenceFrontDirectory` ignorado):** Em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L316-L331), o método `resolveReferenceFrontForStudy` passa a constante estática `DEFAULT_REFERENCE_FRONT_DIRECTORY` (`"resources/referenceFronts"`) em vez de `getReferenceFrontDirectory()`. Isso significa que se o usuário passar `-Dstudy.referenceFrontDirectory=outro_diretorio` na linha de comando, `resolveReferenceFrontForStudy` ignorará a propriedade de sistema.
  - **Ressalva 2 (Ausência de extração/cópia de arquivos nativos do jMetal e ausência de gerador por código para WFG na Prioridade 3):**
    - Como os JARs compilados do jMetal 7.8 no Maven Central (`jmetal-core-7.8.jar`, `jmetal-problem-7.8.jar`, etc.) **não embutem** os arquivos `.csv` de `referenceFronts/` nem os `.dat` de `weightVectorFiles/`, se qualquer arquivo nativo do jMetal for removido ou estiver ausente nas pastas locais `resources/referenceFronts/` ou `resources/weightVectorFiles/`, o código atual **não possui mecanismo para copiá-lo/restaurá-lo do jMetal**; em vez disso, ele cai diretamente para `pf_gen/` ou `weights_gen/`.
    - Pior ainda: na Prioridade 3 de [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1576-L1588), existe a trava `if (isDtlz && allowOnDemandGeneration)`. Assim, enquanto `DTLZ1`–`DTLZ7` possuem gerador analítico por código (`generateAndSaveAnalyticalDtlzFront`), **não foi implementado gerador analítico por código para `WFG1`–`WFG9`**. Caso um arquivo de frente WFG esteja ausente tanto em `resources/referenceFronts/` quanto em `pf_gen/`, a execução aborta com `IllegalArgumentException`.
  - **Correção Necessária:**
    - Implementar o fallback de extração/restauração dos arquivos oficiais do jMetal (via cópia do repositório Git/cache canônico ou alternativa equivalente) para `resources/referenceFronts/` e `resources/weightVectorFiles/` caso algum arquivo oficial do jMetal esteja ausente em disco; e
    - Implementar o gerador analítico por código para `WFG1`–`WFG9` na Prioridade 3 de `ParetoFrontValidator.java`, exatamente como já existe para `DTLZ1`–`DTLZ7`.

---

#### **Tarefa `T27` — Implementação da Política `replaceZeroFronts` (Default `false`)**
- **Especificação:** [task27.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task27.md)
- **Status:** **APROVADA**
- **Testes Executados:** `CanonicalArtifactResolutionAndManifestTest` (`testCanonicalFrontResolutionAndReplaceZeroFrontsFlag` aprovado) e `ReferenceFrontCanonicalPriorityTest` (`testStrictSeparationBetweenPfGenAndPfLowZeroGen` aprovado).
- **Explicação Técnica (Por que passou):**
  - Em [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L562-L648), `REPLACE_ZERO_FRONTS_PROPERTY = "replaceZeroFronts"` tem default `false` (`isReplaceZeroFrontsEnabled()`).
  - Quando `replaceZeroFronts=false`, os arquivos canônicos de `resources/referenceFronts/` e `resources/referenceFronts/pf_gen/` são utilizados preservando coordenadas `0.0` exatas nas bordas da variedade de Pareto.
  - Quando `-DreplaceZeroFronts=true` (globalmente ou por algoritmo, ex.: `-DreplaceZeroFronts=MOEADD`), [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1549-L1562) gera e persiste uma cópia com substituição `0.0 -> 1e-5` no diretório isolado `resources/referenceFronts/pf_lowzero_gen/`, sem contaminar `pf_gen/` e sem tocar nas pastas legadas protegidas.

---

#### **Tarefa `T26` — Resolução Canônica de Pesos `MOEA/DD` e Política `replaceZeroWeights` (Default `false`)**
- **Especificação:** [task26.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task26.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `CanonicalArtifactResolutionAndManifestTest` (`testCanonicalWeightResolutionAndReplaceZeroWeightsFlag` e `testMoeaddConstructorLoadsCanonicalAndSanitizedWeights` aprovados).
- **Explicação Técnica:**
  - [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L151-L164) define `DEFAULT_WEIGHTS_DIRECTORY = "resources/weightVectorFiles"` e [ReferenceSetValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java#L40-L41) define `-DreplaceZeroWeights=false` por padrão.
  - Quando `replaceZeroWeights=false`, `ReferenceSetValidator.resolveCanonicalWeightFile` busca primeiro em `resources/weightVectorFiles/` (`OFFICIAL`, disponível para $m \in \{2, 3, 8, 10\}$) e depois em `resources/weightVectorFiles/weights_gen/` (`WEIGHTS_GEN` / geração analítica pura on-demand, usada para $m=5, N=210$ e $m=15, N=135$).
  - Quando `-DreplaceZeroWeights=true`, `ReferenceSetValidator.resolveCanonicalWeightFile` gera e resolve a variante com substituição `0.0 -> 1e-5` na pasta isolada `resources/weightVectorFiles/weights_lowzero_gen/`.
  - **Omissão identificada:** A especificação de `T29` previa a propriedade `-Dstudy.allowMoeadWeightsInMemory=false` (ou `true`) em `WFGStudy.validateMoeadWeightFile` para controlar explicitamente a dispensa do arquivo `.dat` em $m=2, N \le 300$ (onde `AbstractMOEAD.initializeUniformWeight()` calcula os pesos em memória e ignora o arquivo em disco). Atualmente, `WFGStudy.validateMoeadWeightFile` exige e valida o arquivo em disco mesmo para $m=2, N=100$.

---

#### **Tarefa `T25` — Auditoria de Referências Legadas e Análise das Flags `replaceZero*`**
- **Especificação:** [task25.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task25.md)
- **Status:** **APROVADA**
- **Evidências:** [analise_flags_lowzero.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/analise_flags_lowzero.md) e [auditoria_referencias_legadas.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria_referencias_legadas.md).
- **Explicação Técnica (Por que passou):** Documentou com precisão matemática o impacto da constante legada `LOWZERO = 1e-5` (herdada de `pf_gen/gerador/generate_pareto.py`) no `MOEA/DD` e nos indicadores `HV`, `NHV`, `IGD`, `IGD+` e `R2`, além de mapear todas as referências hardcoded aos diretórios legados.

---

### 3.2. Fase 3 — Reconciliação Científica, Isolamento e Escalabilidade (`T24` $\to$ `T12`)

#### **Tarefa `T24` — Governança, Reconciliação Documental e Relatório Final de Validação (Fase 3)**
- **Especificação:** [task24.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task24.md)
- **Status:** **REPROVADA (PARCIAL)**
- **Evidências:** [final-validation-report.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/final-validation-report.md), [validation-phase3.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/validation-phase3.md), [evidence-matrix.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/methodology/evidence-matrix.csv).
- **Explicação Técnica (Por que falhou na abordagem Zero-Trust):**
  - Os documentos gerados em `T24` declararam que todas as correções da Fase 3 (`T13` a `T23`) estavam integradas ao estudo experimental.
  - A inspeção direta de [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) comprova que os componentes `EvaluationBudgetProblem` (`T13`), `IsolatedJobExecutor` (`T15`), `ObjectiveNormalizationContract` (`T17`), `ResultFrontContract` (`T19`), `ProcessIsolatedHypervolumeExecutor` (`T20`), `MonteCarloHypervolume` (`T20`), `KruskalWallisTest` e `DunnPostHocTest` (`T21`) **não foram conectados ao método `WFGStudy.main()`**, existindo apenas como classes utilitárias invocadas por seus próprios testes unitários.

---

#### **Tarefa `T23` — Desacoplamento de `WFGStudy`, Flags CLI e Proteção de `WFGStudy/`**
- **Especificação:** [task23.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task23.md)
- **Status:** **PARCIAL**
- **Testes Executados:**
  1. Teste unitário `WFGStudyPreflightTest`: `4/4` aprovados.
  2. Smoke test real via CLI:
     ```powershell
     mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/tests/audit_smoke" "-Druns=1" "-Dobjectives=2" "-Dproblem=WFG1"
     ```
- **Resultado do Smoke Test Real:** **FALHOU na etapa `GenerateLatexTablesWithStatistics` (`WFGStudy.java:228`)**.
- **Explicação Técnica (Por que passou no teste unitário mas falhou na execução real):**
  - O teste unitário [WFGStudyPreflightTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/WFGStudyPreflightTest.java) testa apenas métodos auxiliares estáticos (`resolveObjectivesFromProperties`, `validateReferenceFronts`, `configureAlgorithmList`), **sem executar o pipeline completo de `WFGStudy.main()`**.
  - Na execução real do smoke test, os 6 algoritmos rodaram 1 run em `WFG1` ($m=2$) e `ComputeQualityIndicators` calculou `HV`, `NHV`, `IGD`, `IGD+` e `R2`, gravando `QualityIndicatorSummary.csv`.
  - Porém, na linha 228 de [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L204-L231), `ExperimentBuilder` é instanciado com `new ExperimentBuilder<DoubleSolution, List<DoubleSolution>>(outputDirectory)`, onde `outputDirectory = "WFGStudy/tests/audit_smoke"`.
  - No jMetal 7.8, `GenerateLatexTablesWithStatistics.printHeaderLatexCommands` cria o diretório `<experimentBaseDirectory>/latex` e tenta abrir o arquivo `<experimentBaseDirectory>/latex/<experimentName>.tex`. Como `experimentName` contém barras (`WFGStudy/tests/audit_smoke`), ele tenta criar o arquivo `WFGStudy\tests\audit_smoke\latex\WFGStudy\tests\audit_smoke.tex`, cujo subdiretório intermediário não existe, lançando:
    ```text
    java.io.FileNotFoundException: ...\WFGStudy\tests\audit_smoke\latex\WFGStudy\tests\audit_smoke.tex (O sistema não pode encontrar o caminho especificado)
        at org.uma.jmetal.lab.experiment.component.impl.GenerateLatexTablesWithStatistics.printHeaderLatexCommands (GenerateLatexTablesWithStatistics.java:185)
        at br.usp.lti.newmoea.study.WFGStudy.main (WFGStudy.java:228)
    ```
  - Além disso, ao falhar, 2 threads do `ForkJoinPool.commonPool` (usadas internamente pelo `AGE-MOEA`/`AGE-MOEA-II` via `parallelStream()`) permaneceram vivas por 15 segundos até o timeout do `exec-maven-plugin`.

---

#### **Tarefa `T22` — Profiling Empírico sob Condições Reais da Fase 3**
- **Especificação:** [task22.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task22.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `PerformanceProfilerPhase3Test` (`2/2` aprovados).
- **Evidências:** [PerformanceProfiler.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java) e [phase3_Indicator_Runtime_Matrix.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/phase3_Indicator_Runtime_Matrix.csv).
- **Explicação Técnica:**
  - O profiler mediu os tempos reais e comprovou que em $m=15$ (`WFG4`, 135 soluções do `MOEA/DD`), o `PISAHypervolume` exato levou **`40.863,31 segundos` (`11,35 horas`) de CPU para avaliar uma única frente**, enquanto o `MonteCarloHypervolume` ($10^5$ amostras) levou **`18,16 ms`**.
  - Apesar dessa evidência contundente gerada pela própria `T22`, o pipeline `WFGStudy.main()` não foi alterado para substituir o `PISAHypervolume` em $m \ge 10$.

---

#### **Tarefa `T21` — Inferência Estatística Não-Paramétrica (`Kruskal-Wallis`, `Dunn-Holm`, $A_{12}$)**
- **Especificação:** [task21.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task21.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `KruskalWallisTestTest` (`4/4` aprovados).
- **Evidências:** [KruskalWallisTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/statistics/KruskalWallisTest.java), [DunnPostHocTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/statistics/DunnPostHocTest.java) e [kruskal_dunn_holm.R](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/scripts/kruskal_dunn_holm.R).
- **Explicação Técnica:**
  - As implementações Java de Kruskal-Wallis (com correção de empates $1 - \sum(t_i^3 - t_i)/(N^3 - N)$), Dunn Post-Hoc com ajuste step-down de Holm e tamanho de efeito $\hat{A}_{12}$ de Vargha-Delaney estão matematicamente exatas e reproduzem os valores do R até $10^{-6}$.
  - Contudo:
    1. [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L228-L231) nunca invoca `KruskalWallisTest` ou `DunnPostHocTest`.
    2. O script [scripts/kruskal_dunn_holm.R](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/scripts/kruskal_dunn_holm.R) apenas roda sobre um vetor estático de exemplo (`g1`, `g2`, `g3` de 5 elementos nas linhas 25–27) e **não lê `QualityIndicatorSummary.csv`** do experimento.

---

#### **Tarefa `T20` — Mitigação do Gargalo de Hipervolume ($m \ge 10$) e Isolamento de Processo**
- **Especificação:** [task20.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task20.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `MonteCarloHypervolumeTest` (`4/4` aprovados) e `ProcessIsolatedHypervolumeTest` (`4/4` aprovados).
- **Evidências:** [MonteCarloHypervolume.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/MonteCarloHypervolume.java) e [ProcessIsolatedHypervolumeExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java).
- **Explicação Técnica:**
  - `ProcessIsolatedHypervolumeExecutor` executa o cálculo exato de WFG/PISA HV em uma JVM filha via `ProcessBuilder`, aplicando timeout configurável e `destroyForcibly()` em caso de estouro, com fallback explícito (`EXACT_COMPLETED`, `TIMED_OUT_FALLBACK_MC`, `OOM_FALLBACK_MC`).
  - Porém, [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L207-L227) continua passando `new PISAHypervolume()` e `new NormalizedHypervolume()` diretamente para `ComputeQualityIndicators` do jMetal. Assim, numa execução real em $m=10$ ou $m=15$, `WFGStudy` travará no cálculo de `HV`/`NHV` em memória sem timeout.
  - **Correção Necessária para o Pipeline Principal (`WFGStudy.java`):**
    1. No pipeline principal devem estar configurados tanto o **`PISAHypervolume` (exato, com isolamento de processo e limite máximo de 60 minutos / `3_600_000 ms`)** quanto o **Hipervolume Aproximado (`MonteCarloHypervolume`)**, de modo que, caso não seja possível concluir o cálculo exato com o `PISAHypervolume` dentro do tempo limite, o experimento já disponha do indicador de Hipervolume Aproximado calculado.
    2. Além disso, mesmo quando o usuário optar por rodar **apenas 1 indicador de Hipervolume** (ex.: somente `PISAHypervolume` ou outro método exato sujeito ao limite de 60 minutos), se a execução atingir o timeout de 60 minutos, **deve ser executado automaticamente o fallback para o cálculo do Hipervolume Aproximado**, registrando um **aviso explícito nos arquivos de saída e no relatório/tabela** que identifique claramente quais instâncias/execuções foram avaliadas com `PISAHypervolume` exato e quais precisaram utilizar o Hipervolume Aproximado por estouro de tempo.

---

#### **Tarefa `T19` — Contrato de Frentes de Saída (`ResultFrontContract`)**
- **Especificação:** [task19.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task19.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `ResultFrontContractTest` (`4/4` aprovados).
- **Evidências:** [ResultFrontContract.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ResultFrontContract.java).
- **Explicação Técnica:**
  - `ResultFrontContract.filterAndSanitize` remove soluções NaN/Inf, filtra soluções Pareto-dominadas e elimina duplicatas com tolerância $\epsilon = 10^{-12}$.
  - Nos testes (`ResultFrontContractTest`), demonstrou-se que `MOEA/DD` e `RVEA` retornam populações brutas com soluções dominadas em `algorithm.result()`, o que distorce o indicador `IGD` euclidiano.
  - Entretanto, `WFGStudy` usa `ExperimentAlgorithm` padrão do jMetal, que chama `algorithm.result()` diretamente sem passar por `ResultFrontContract.filterAndSanitize`.

---

#### **Tarefa `T18` — Migração Canônica de Artefatos e Manifesto SHA-256**
- **Especificação:** [task18.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task18.md)
- **Status:** **APROVADA (HISTÓRICA — Sucedida pelas Tarefas `T26`–`T30`)**
- **Explicação Técnica:** Criou a estrutura inicial de manifesto SHA-256 e relatório de migração ([artifact-migration.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-migration.md)). Na Fase 4, a resolução foi aprimorada para manter `MOEAD_Weights/` e `pareto_fronts/` intocados e usar `resources/weightVectorFiles/` e `resources/referenceFronts/` + `pf_gen/`.

---

#### **Tarefa `T17` — Contrato de Normalização de Objetivos e Ponto de Referência Explícito**
- **Especificação:** [task17.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task17.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `NormalizationAndHvContractTest` (`31/31` aprovados).
- **Evidências:** [ObjectiveNormalizationContract.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ObjectiveNormalizationContract.java) e [StaticParams.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L162-L214).
- **Explicação Técnica:**
  - `StaticParams` define limites analíticos exatos ($z_i^* = 0$, $z_i^{nad} = 2i$ para WFG; $0.5$ para DTLZ1; $1.0$ para DTLZ2–4) e ponto de referência $r = (1.1, \dots, 1.1)^T$ no espaço normalizado via `ObjectiveNormalizationContract`.
  - No teste `NormalizationAndHvContractTest`, provou-se que em `WFG4.15D.csv` o máximo empírico de $f_1$ na frente de 6.000 pontos é `0.007593` (contra o limite analítico $2.0$), fazendo com que a normalização empírica min-max do jMetal (`NormalizeUtils.normalize`) distorça severamente $f_1$ em mais de $260\times$.
  - Apesar disso, `WFGStudy.main()` continua delegando o cálculo para `ComputeQualityIndicators` do jMetal, que usa `NormalizeUtils.normalize` empírico sobre o CSV da frente de referência e ponto de referência implícito $(1.0, \dots, 1.0)^T$.

---

#### **Tarefa `T16` — Auditoria Física e Matemática das Frentes de Referência**
- **Especificação:** [task16.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task16.md)
- **Status:** **APROVADA**
- **Testes Executados:** `ReferenceFrontPhysicalAndMathAuditTest` (`147/147` aprovados).
- **Explicação Técnica (Por que passou):** Auditou todas as 54 frentes WFG e 36 frentes DTLZ em todas as pastas (`resources/referenceFronts/`, `pf_gen/`, `pareto_fronts/csv/`, `pareto/`), confirmando ausência de NaN/Inf, não-dominância mútua e aderência às equações analíticas ($h_m$ de DTLZ7, hiperplano $\sum f_i = 0.5$ de DTLZ1, hipersuperfície $\sum f_i^2 = 1$ de DTLZ2–4 e elipsoides $\sum (f_i/2i)^2 = 1$ de WFG4–9).

---

#### **Tarefa `T15` — Isolamento de Execução e Sementes Determinísticas**
- **Especificação:** [task15.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task15.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `IsolationAndReproducibilityTest` (`4/4` aprovados).
- **Evidências:** [IsolatedJobExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) e [R2ExecutionObserver.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/feedback/R2ExecutionObserver.java).
- **Explicação Técnica:**
  - `R2ExecutionObserver` foi corrigido para usar estruturas thread-safe (`CopyOnWriteArrayList`, `AtomicReference`, `AtomicInteger`) e limpar vetores de pesos após `buildR2Indicator()`.
  - `IsolatedJobExecutor` implementa factories lazy por `(algorithm, problem, runId)` e atribui sementes determinísticas `baseSeed + runId * 10_007L + problemId * 101L`.
  - Contudo, `WFGStudy.main()` **não usa `IsolatedJobExecutor`**: continua instanciando todos os `ExperimentAlgorithm` avidamente em `configureAlgorithmList` e executando `new ExecuteAlgorithms<>(experiment).run()` com `setNumberOfCores(8)`, onde 8 threads concorrentes compartilham o singleton global não-sincronizado `JMetalRandom.getInstance()`.

---

#### **Tarefa `T14` — Reconciliação Científica do `FDEA` e Remoção de Código Morto**
- **Especificação:** [task14.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task14.md)
- **Status:** **APROVADA**
- **Testes Executados:** `FdeaIdentityAndLegacyAuditTest` (`5/5` aprovados).
- **Explicação Técnica (Por que passou):**
  - As classes mortas `InvertedGenerationalDistance.java` e `Hypervolume2.java`, bem como métodos órfãos (`shiftValues`, `g1`, `g2`, `getExtremePoints` duplicado) em [FDEA.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEA.java), foram removidos com sucesso.
  - A identidade algorítmica da implementação local de `FDEA` (`FDEA_I` vs variante com matriz de similaridade Gaussiana, hyperplane intercept update com guarda de condicionamento e seleção por projeção ortogonal / distância ao ideal) foi formalmente auditada e validada por testes de reflexão e execução.

---

#### **Tarefa `T13` — Reconciliação do Orçamento de Avaliações (`EvaluationBudgetProblem`)**
- **Especificação:** [task13.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task13.md)
- **Status:** **PARCIAL**
- **Testes Executados:** `EvaluationBudgetAuditTest` (`6/6` aprovados).
- **Evidências:** [EvaluationBudgetProblem.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java).
- **Explicação Técnica:**
  - `NSGA-III` arredonda o tamanho da população para cima até um múltiplo de 4 (`91 -> 92` em $m=3$, `210 -> 212` em $m=5$, `275 -> 276` em $m=10$, `135 -> 136` em $m=15$).
  - `AGE-MOEA` e `AGE-MOEA-II` incrementam populações ímpares em +1 (`91 -> 92`, `275 -> 276`, `135 -> 136`) porque o loop `while (offspringPopulation.size() < getMaxPopulationSize())` adiciona 2 filhos do SBX por iteração.
  - A classe `EvaluationBudgetProblem` foi criada para envelopar `DoubleProblem` e truncar estritamente em $B = N_{\text{config}} \times G_{\max}$ avaliações. Porém, em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L106-L157), os problemas `WFG1`–`WFG9` e `DTLZ1`–`DTLZ7` são instanciados diretamente sem `EvaluationBudgetProblem`, mantendo o estouro de orçamento na campanha real.

---

#### **Tarefa `T12` — Gate G0/G1: Escopo Científico e Decisões Metodológicas**
- **Especificação:** [task12.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task12.md)
- **Status:** **APROVADA**
- **Evidências:** [G0-G1-scope-and-decisions.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/decisions/G0-G1-scope-and-decisions.md), [scope.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/methodology/scope.md), [limitations.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/methodology/limitations.md).
- **Explicação Técnica (Por que passou):** Formalizou os gates metodológicos G0 e G1 sobre orçamento, identidade do `FDEA`, sementes, normalização, contrato de frente de saída, HV em alta dimensão e testes estatísticos.

---

### 3.3. Fase 2 — Saneamento Arquitetural, Profiling e Pré-Voo (`T11` $\to$ `T06`)

#### **Tarefa `T11` — Sincronização de `MOEAD_Weights/` e Validação (`MoeaddWeightValidationTest`)**
- **Especificação:** [task11.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task11.md)
- **Status:** **REPROVADA (`7` FALHAS em `mvn test`)**
- **Testes Executados:** `mvn test -Dtest=MoeaddWeightValidationTest` (`Tests run: 12, Failures: 7, Errors: 0`).
- **Detalhamento Exato das 7 Falhas:**
  1. `testActiveMoeadWeightFilesIntegrity(int, int, int, boolean)[2]` (`m=3, N=91`):
     - `AssertionFailedError: Active MOEAD_Weights/W3D_91.dat must contain exact 0.0 boundary coordinates (no 1e-5 replacement) ==> expected: <false> but was: <true>` (linha 157).
  2. `testActiveMoeadWeightFilesIntegrity(int, int, int, boolean)[4]` (`m=8, N=156`):
     - `AssertionFailedError: Duplicate vectors count for W8D_156.dat ==> expected: <8> but was: <0>` (linha 152).
  3. `testActiveMoeadWeightFilesIntegrity(int, int, int, boolean)[5]` (`m=10, N=275`):
     - `AssertionFailedError: Duplicate vectors count for W10D_275.dat ==> expected: <10> but was: <0>` (linha 152).
  4. `testMoeadWeightsVsZeroReplacedAndPureCanonical(int, int, String, int, int)[2]` (`W3D_91.dat`):
     - `AssertionFailedError: Active MOEAD_Weights/W3D_91.dat must have 0 strictly positive vectors when m >= 3... ==> expected: <false> but was: <true>` (linha 224).
  5. `testMoeadWeightsVsZeroReplacedAndPureCanonical(int, int, String, int, int)[4]` (`W8D_156.dat`):
     - `AssertionFailedError: Active MOEAD_Weights/W8D_156.dat has official jMetal uncontracted inner layer (8 duplicates) ==> expected: <8> but was: <0>` (linha 233).
  6. `testMoeadWeightsVsZeroReplacedAndPureCanonical(int, int, String, int, int)[5]` (`W10D_275.dat`):
     - `AssertionFailedError: Active MOEAD_Weights/W10D_275.dat has official jMetal uncontracted inner layer (10 duplicates) ==> expected: <10> but was: <0>` (linha 233).
  7. `testOfficialJMetalCopiesAndSha256Integrity`:
     - `AssertionFailedError: SHA-256 mismatch for official jMetal file W2D_300.dat ==> expected: <a6173b12c38f824ef011ccfd650c7a5dd47f955dc5f62daed5bcdd7d7b89c61d> but was: <f2a45d8d39be55f234b65ff8b758e6609446c8e0f698f5be4be6bc318193d07c>` (linha 307).
- **Explicação Técnica da Causa-Raiz:**
  - `AGENTS.md` proíbe alterar, substituir ou sobrescrever qualquer arquivo em `MOEAD_Weights/`.
  - Quando `T11` foi executada no commit `f5b1eef`, o agente violou essa regra copiando 29 arquivos de `Jmetal/resources/weightVectorFiles/` para dentro de `MOEAD_Weights/` e escreveu `MoeaddWeightValidationTest` para validar que `MOEAD_Weights/` continha esses 29 arquivos puros do jMetal.
  - No commit `2d6f257`, `MOEAD_Weights/` foi revertido para seus 43 arquivos originais do commit `6a6ecc2` (onde os zeros são `1e-5` e as camadas internas de $m=8, 10$ são contraídas com 0 duplicatas), e `WFGStudy` passou a ler de `resources/weightVectorFiles/`.
  - Como `MoeaddWeightValidationTest` continuou apontando `WEIGHTS_DIR = "MOEAD_Weights"` em vez de `"resources/weightVectorFiles"` nos testes dos arquivos canônicos do jMetal, os 7 testes falham.
  - **Adendo Importante 1 (Motivo da Reversão de `MOEAD_Weights/`):** A reversão de `MOEAD_Weights/` ocorreu porque foi constatado que alterações anteriores (feitas antes mesmo de a pasta passar a ser formalmente classificada como legada) haviam modificado arquivos dentro dela que deveriam ser estritamente inalteráveis.
  - **Adendo Importante 2 (Proibição de Uso da Cópia Local do jMetal no Computador):** **NÃO deve ser utilizada a cópia local do jMetal presente no computador (`C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`)**, pois essa pasta local sofreu alterações e não reflete nem a versão atual do jMetal nem a versão oficial `7.8`. Ela não deve ser usada em nenhuma hipótese — mesmo que testes ou scripts antigos a tenham referenciado. Nos testes e mecanismos de extração/validação que precisem comparar ou obter artefatos do jMetal, deve-se utilizar uma alternativa que compare/extraia diretamente a partir do **repositório oficial do jMetal no GitHub** (tag/release oficial compatível com a versão utilizada ou artefatos oficiais imutáveis verificados do GitHub).
  - **Adendo de Recomendação Documental:** Recomenda-se registrar posteriormente essas duas diretrizes (o histórico/motivo da reversão e imutabilidade de `MOEAD_Weights/` e a proibição expressa de uso do diretório local `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal` em favor do repositório oficial do jMetal no GitHub) diretamente no README.md e em outros arquivos relevantes do projeto para documentação permanente.

---

#### **Tarefa `T10` — Saneamento Arquitetural e Plano de Mitigação de Dívida Técnica**
- **Especificação:** [task10.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task10.md)
- **Status:** **APROVADA**
- **Evidências:** [resolucao_duplicatas_arquitetura.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/resolucao_duplicatas_arquitetura.md) e [plano_mitigacao_divida_tecnica.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/plano_mitigacao_divida_tecnica.md).
- **Explicação Técnica (Por que passou):** Mapeou e planejou a remoção das classes duplicadas (`InvertedGenerationalDistance`, `Hypervolume2`), correção de `R2ExecutionObserver` e mitigação do gargalo de hipervolume.

---

#### **Tarefa `T09` — Consolidação da Fase 1 em `HANDLER.md` e `README.md`**
- **Especificação:** [task9.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task9.md)
- **Status:** **APROVADA**
- **Explicação Técnica (Por que passou):** Consolidou os resultados da Fase 1 (`T01`–`T05`) nos documentos principais do projeto.

---

#### **Tarefa `T08` — Investigação de Duplicatas e Código Legado**
- **Especificação:** [task8.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task8.md)
- **Status:** **APROVADA**
- **Evidências:** [duplicatas_analise-10do11.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/duplicatas_analise-10do11.md).
- **Explicação Técnica (Por que passou):** Identificou com precisão todas as classes duplicadas entre `br.usp.lti.newmoea` e `org.uma.jmetal` (7.8), incluindo `R2`, `Hypervolume2`, `InvertedGenerationalDistance`, `HypE` vs `HypEFitnessAssignment`, `FDEA` vs `FDEA_I` e `ShiftDensity`.

---

#### **Tarefa `T07` — Benchmark e Profiling Inicial de Performance**
- **Especificação:** [task7.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task7.md)
- **Status:** **APROVADA**
- **Testes Executados:** `PerformanceProfilerTest` (`2/2` aprovados).
- **Explicação Técnica (Por que passou):** Implementou `PerformanceProfiler` usando `ManagementFactory.getThreadMXBean()` e `MemoryMXBean` e identificou pela primeira vez a explosão combinatória do `PISAHypervolume` em $m \ge 10$ e o vazamento de memória potencial em `R2ExecutionObserver`.

---

#### **Tarefa `T06` — Validação Pré-Voo das Frentes de Referência (`ParetoFrontValidator`)**
- **Especificação:** [task6.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task6.md)
- **Status:** **APROVADA**
- **Testes Executados:** `ParetoFrontValidationTest` (`74/74` aprovados).
- **Explicação Técnica (Por que passou):** Valida que todas as frentes WFG1–WFG9 ($m \in \{2,3,5,8,10,15\}$) e DTLZ1–DTLZ7 ($m \in \{2,3\}$ padrão e $m \in \{5,8,10,15\}$ em `pf_gen/`) possuem dimensionalidade correta, valores finitos não-negativos e cardinalidade consistente.

---

### 3.4. Fase 1 — Validação de Base, Parâmetros e Indicadores (`T05` $\to$ `T01`)

#### **Tarefa `T05` — Testes Unitários dos Indicadores de Qualidade (`HV`, `NHV`, `IGD`, `IGD+`, `R2`)**
- **Especificação:** [task5.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task5.md)
- **Status:** **APROVADA**
- **Testes Executados:** `QualityIndicatorUnitTest` (`14/14` aprovados).
- **Explicação Técnica (Por que passou):** Verifica cálculos analíticos exatos em 2D/3D para frentes triviais, frentes dominadas, frentes idênticas à referência e monotonicidade de Pareto para `PISAHypervolume`, `NormalizedHypervolume`, `InvertedGenerationalDistance`, `InvertedGenerationalDistancePlus` e `R2`.

---

#### **Tarefa `T04` — Testes de Integração dos 6 Algoritmos e `R2ExecutionObserver`**
- **Especificação:** [task4.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task4.md)
- **Status:** **APROVADA**
- **Testes Executados:** `AlgorithmIntegrationTest` (`36/36` aprovados) e `R2ExecutionObserverTest` (`2/2` aprovados).
- **Explicação Técnica (Por que passou):** Executa cada um dos 6 algoritmos (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`) em `WFG1` e `DTLZ1` para $m \in \{2, 3, 5\}$, verificando tamanho da população de saída, ausência de `NaN`/`Infinity` e coleta da série temporal pelo `R2ExecutionObserver`.

---

#### **Tarefa `T03` — Auditoria dos Vetores de Pesos do `MOEA/DD`**
- **Especificação:** [task3.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task3.md)
- **Status:** **APROVADA (COM RESSALVA NA REGRESSÃO DO TESTE EM `T11`)**
- **Evidências:** [moead-weights-audit.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/moead-weights-audit.md) e [moead-weights-manifest.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/moead-weights-manifest.csv).
- **Explicação Técnica:** A auditoria original de `T03` diagnosticou com exatidão que os arquivos em `MOEAD_Weights/` possuíam substituição `0 -> 1e-5` e contração da camada interna ($m=8, 10$), enquanto `W15D_135.dat` era ausente em `resources/weightVectorFiles/` e precisava ser gerado em memória (`initializeUniformWeight()`) ou lido do legado.

---

#### **Tarefa `T02` — Validação dos Pontos de Referência do `NSGA-III`**
- **Especificação:** [task2.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task2.md)
- **Status:** **APROVADA**
- **Testes Executados:** `NsgaIIIReferencePointsValidationTest` (`18/18` aprovados).
- **Explicação Técnica (Por que passou):** Comprova que `ReferencePoint.generateReferencePoints` gera exatamente $\{100, 91, 210, 156, 275, 135\}$ pontos em una/duas camadas (`numberOfDivisions` de `StaticParams`), com coordenadas não-negativas somando $1.0$ ($\pm 10^{-9}$) e contração centrípeta de $0.5$ na segunda camada para $m \in \{8, 10, 15\}$.

---

#### **Tarefa `T01` — Auditoria de Parâmetros e Conformidade com a Literatura**
- **Especificação:** [task1.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task1.md)
- **Status:** **PARCIAL (Bug identificado em `StaticParams.getMaxGenerations` para `DTLZ5` e `DTLZ6` em $m=10$ e $m=15$)**
- **Evidências no Código:** [StaticParams.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L101-L153).
- **Explicação Técnica (Por que é parcial):**
  - Os tamanhos de população $N \in \{100, 91, 210, 156, 275, 135\}$, divisões de Das & Dennis $H_1/H_2$, probabilidades e índices de distribuição SBX ($p_c = 1.0, \eta_c = 30.0$) e Mutação Polinomial ($p_m = 1/n, \eta_m = 20.0$) estão corretos em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L233-L287).
  - **Bug encontrado na auditoria Zero-Trust:** Em [StaticParams.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L101-L152):
    - Para $m \in \{2, 3, 5, 8\}$, cada `switch (problemName)` possui um `default: return 750;` (ou `1000` em $m=8$) que captura `"DTLZ5"` e `"DTLZ6"`.
    - Porém, para **`case 10:`** (linhas 101–124) e **`case 15:`** (linhas 125–149), os `switch (problemName)` internos listam apenas `"DTLZ1"`, `"DTLZ2"`, `"DTLZ3"`, `"DTLZ4"`, `"DTLZ7"` e `"WFG"`, **sem cláusula `default:` interna**.
    - Quando `problemName` é `"DTLZ5"` ou `"DTLZ6"` com $m=10$ ou $m=15$ (por exemplo, ao rodar com `-DgenerateAllReferenceFronts=true`), a execução sai do `switch (problemName)` interno e cai no `default:` externo do `switch (m)` na linha 151 (`return 1000;`), executando **1.000 gerações** em vez de **1.500 gerações ($m=10$)** e **2.000 gerações ($m=15$)**!

---

## 4. Avaliação de Prontidão para o Experimento Final (Pré-Hiper-Heurísticas)

Com base na verificação cruzada entre o [README.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/README.md) (Seções 4, 6 e 8) e o código real de [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java), **o projeto NÃO está 100% pronto para uma execução completa desassistida ($m \in \{2, 3, 5, 8, 10, 15\}$, 30 runs)**.

### Resumo dos Impeditivos e Pendências Identificadas:
1. **Bloqueio de Teste Automatizado (`MoeaddWeightValidationTest`):** 7 testes falham em `mvn test` devido à dessincronização do teste com a reversão de `MOEAD_Weights/` no commit `2d6f257`.
2. **Bloqueio Crítico de Tempo de Execução em $m \ge 10$ (`PISAHypervolume`) e Integração do Hipervolume Aproximado:** `WFGStudy.main()` ainda usa `PISAHypervolume` síncrono em `ComputeQualityIndicators`. Em $m=15$, uma única frente de 135 pontos consome $> 11,3\text{ horas}$ de CPU. O pipeline principal deve incluir tanto o `PISAHypervolume` (com limite de 60 minutos) quanto o Hipervolume Aproximado (`MonteCarloHypervolume`), e mesmo quando configurado apenas 1 indicador de HV exato (ex.: `PISAHypervolume`), caso estoure os 60 minutos, deve acionar automaticamente o fallback para o Hipervolume Aproximado emitindo aviso explícito no relatório/tabela sobre qual método gerou cada valor.
3. **Ausência de Extração de Arquivos Oficiais do jMetal e de Gerador Analítico por Código para WFG (`ParetoFrontValidator` / `ReferenceSetValidator`):**
   - Implementar o fallback de extração dos arquivos oficiais do jMetal (via cópia do Git ou alternativa equivalente) para `resources/referenceFronts/` e `resources/weightVectorFiles/` caso algum arquivo oficial esteja ausente em disco; e
   - Implementar o gerador analítico por código para `WFG1`–`WFG9` na Prioridade 3 de `ParetoFrontValidator.java`, exatamente como já existe para `DTLZ1`–`DTLZ7`.
4. **Bloqueio em Subdiretórios de Saída (`GenerateLatexTablesWithStatistics`):** Qualquer execução com `-Dstudy.outputDirectory` contendo barras (ex.: `WFGStudy/tests/smoke`) falha com `FileNotFoundException` ao gerar a tabela LaTeX.
5. **Desconexão dos Contratos da Fase 3 em `WFGStudy.main()`:**
   - Falta envolver os problemas com `EvaluationBudgetProblem` (`T13`);
   - Falta substituir `ExecuteAlgorithms` ou isolar o RNG global `JMetalRandom` (`T15`);
   - Falta aplicar `ObjectiveNormalizationContract` (`T17`) e `ResultFrontContract` (`T19`) antes do cálculo dos indicadores;
   - Falta invocar `KruskalWallisTest` / `DunnPostHocTest` (`T21`) e `exportActiveArtifactManifestCsv` (`T30`).
6. **Bug de Gerações em `DTLZ5`/`DTLZ6` ($m=10, 15$):** Falta adicionar `"DTLZ5"` e `"DTLZ6"` (ou `default`) nos blocos `case 10` e `case 15` de `StaticParams.getMaxGenerations`.
7. **Persistência em Disco dos Históricos do `R2ExecutionObserver`:** O `R2ExecutionObserver` é acoplado apenas quando um observador é registrado manualmente; em `WFGStudy.configureAlgorithmList()`, nenhum `R2ExecutionObserver` é anexado aos algoritmos nem salvo em CSV por geração.
8. **Proibição de Uso da Cópia Local do jMetal (`C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`) e Atualização Futura do `README.md`:**
   - **Adendo sobre `MOEAD_Weights/`:** A reversão de `MOEAD_Weights/` ocorreu porque foi identificado que alguma alteração anterior (antes de a pasta ser classificada como legada) havia modificado arquivos que deveriam ser estritamente inalteráveis.
   - **Adendo sobre a cópia local do jMetal:** **Não deve ser usada em nenhuma hipótese a cópia local `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`**, pois ela sofreu alterações locais e não reflete nem a versão atual do jMetal nem a versão `7.8` oficial. Mesmo que testes antigos a tenham utilizado, qualquer validação ou restauração de arquivos oficiais deve comparar/obter a partir do **jMetal oficial do GitHub**.
   - **Recomendação para o `README.md`:** Adicionar posteriormente no [README.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/README.md) essas duas informações (o motivo da reversão/imutabilidade de `MOEAD_Weights/` e a proibição de uso da cópia local do jMetal em favor do repositório oficial no GitHub) para documentação permanente do projeto.
9. **Garantia de Preservação, Independência Real e Reprodutibilidade das Execuções Independentes:**
   - Verificar e garantir no pipeline principal (`WFGStudy.main()`) que as execuções independentes (`run = 0 .. 29`) não tenham seus arquivos `FUNx.csv`/`VARx.csv` nem o `QualityIndicatorSummary.csv` sobrescritos indevidamente em novas execuções parciais/retomadas, que sejam genuinamente independentes (sem condição de corrida no `JMetalRandom`) e que, mesmo sendo independentes entre si, cada `runId` possa ser exatamente reproduzida via semente determinística (`IsolatedJobExecutor.computeDeterministicSeed`).
10. **Re-Auditoria Zero-Trust em Ordem Cronológica Reversa Pós-Correções (`T33`):**
    - Quando todas as pendências acima estiverem resolvidas, executar novamente a verificação/auditoria completa em **Ordem Cronológica Reversa (`T32` $\to$ `T01`)**, testando tarefa por tarefa empiricamente sem assumir como concluído nada do que foi declarado nos relatórios de correção.


> [!IMPORTANT]
> As instruções completas de como corrigir ou contornar essas pendências e o passo a passo exato para executar o experimento estão detalhados em [Guia de Execução do Experimento Final e Pendências](#guia-de-execução-do-experimento-final-e-pendências-hypemoea) abaixo, e o fluxo atualizado de pós-processamento encontra-se em [pipeline-pos_processamento.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/pipeline-pos_processamento.md).


# Guia de Execução do Experimento Final e Pendências (`hypeMOEA`)

> [!CAUTION]
> ## 🚨 PENDÊNCIAS E BLOQUEIOS CRÍTICOS ANTES DO EXPERIMENTO FINAL
>
> A auditoria *Zero-Trust* consolidada na primeira parte deste documento constatou que **o projeto NÃO está 100% pronto para rodar a campanha experimental completa ($m \in \{2, 3, 5, 8, 10, 15\}$, 30 runs) de ponta a ponta sem intervenção**. Embora todas as classes utilitárias das Fases 3 e 4 tenham sido criadas e passem em seus testes unitários individuais, elas **não foram conectadas ao pipeline principal em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java)**.
>
> Abaixo está a lista exata do que precisa ser corrigido antes de iniciar a campanha definitiva (ou do que acontecerá se for executado no estado atual):
>
> ### 1. Bloqueios Imediatos de Execução e Build
> 1. **Falha em 7 testes de [MoeaddWeightValidationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java) durante `mvn test`:**
>    - **O que falta:** Atualizar `MoeaddWeightValidationTest` para validar os arquivos puros do jMetal em `resources/weightVectorFiles/` (diretório ativo de `WFGStudy`) e validar `MOEAD_Weights/` como diretório legado protegido (com `0 -> 1e-5` e 0 duplicatas nas camadas internas de $m=8, 10$), alinhando o teste à reversão realizada no commit `2d6f257` (sem jamais usar a cópia local `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`, comparando apenas com os artefatos oficiais do GitHub do jMetal).
> 2. **Travamento Computacional (`PISAHypervolume`) para $m=10$ e $m=15$ em `WFGStudy.main()` e Inclusão do Hipervolume Aproximado:**
>    - **O que falta:** Configurar no pipeline principal de [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L207-L227) tanto o **`PISAHypervolume` (exato, com isolamento de processo via [ProcessIsolatedHypervolumeExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java) e limite de 60 minutos)** quanto o **Hipervolume Aproximado ([MonteCarloHypervolume.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/MonteCarloHypervolume.java))**. Além disso, caso o usuário escolha rodar apenas o `PISAHypervolume` (ou outro método exato sujeito ao limite de 60 minutos) e este atinja o limite de 60 minutos, deve ser calculado automaticamente o Hipervolume Aproximado como fallback, emitindo um aviso explícito no relatório/tabela indicando qual valor veio do `PISAHypervolume` e qual precisou usar o Hipervolume Aproximado.
> 3. **Fallback de Extração de Arquivos Oficiais do jMetal e Gerador Analítico por Código para WFG (`ParetoFrontValidator` / `ReferenceSetValidator`):**
>    - **O que falta:** Implementar o fallback de extração/cópia dos arquivos oficiais do jMetal (a partir do repositório oficial do GitHub ou alternativa canônica, nunca da pasta local `..\jMetal`) para `resources/referenceFronts/` e `resources/weightVectorFiles/` caso algum arquivo oficial esteja ausente em disco; e implementar o gerador analítico por código para `WFG1`–`WFG9` na Prioridade 3 de [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1576-L1588), exatamente como já existe para `DTLZ1`–`DTLZ7`.
> 4. **Crash (`FileNotFoundException`) em `GenerateLatexTablesWithStatistics` quando `-Dstudy.outputDirectory` possui subpastas (ex.: `WFGStudy/tests/smoke`):**
>    - **O que falta:** Em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L199-L231), `ExperimentBuilder` recebe `outputDir` como `experimentName`. No jMetal 7.8, `GenerateLatexTablesWithStatistics` grava o arquivo `<experimentBaseDirectory>/latex/<experimentName>.tex`. Se `outputDir` contiver barras (`WFGStudy/tests/smoke`), o jMetal tenta abrir `WFGStudy/tests/smoke/latex/WFGStudy/tests/smoke.tex` sem criar os subdiretórios intermediários, lançando `FileNotFoundException`.
>    - **Como corrigir:** Usar apenas o *basename* (`Paths.get(outputDir).getFileName().toString()`) no construtor `new ExperimentBuilder<>(experimentName)` e configurar o caminho pai em `setExperimentBaseDirectory(...)`, ou criar os diretórios pais antes de `GenerateLatexTablesWithStatistics`.
>
> ### 2. Pendências de Rigor Científico, Reprodutibilidade e Arquitetura (Fases 3 e 4 não plugadas no `WFGStudy`)
> 5. **Bug no número de gerações de `DTLZ5` e `DTLZ6` em $m=10$ e $m=15$ ([StaticParams.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L114-L159)):**
>    - **O que falta:** Adicionar `case "DTLZ5": case "DTLZ6":` (ou `default:`) dentro dos switches internos de `case 10:` (retornando `1500`) e `case 15:` (retornando `2000`). Hoje eles caem no `default` externo e rodam apenas **`1000` gerações**.
> 6. **Integração do `EvaluationBudgetProblem` (`T13`) em `WFGStudy.configureAlgorithmList()`:**
>    - **O que falta:** Envolver cada problema instanciado em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L333-L406) com `new EvaluationBudgetProblem(problem, popSize * maxGenerations)` para impedir que `NSGA-III` ($N \in \{92, 212, 276, 136\}$) e `AGE-MOEA`/`AGE-MOEA-II` ($N \in \{92, 276, 136\}$) avaliem mais soluções que `MOEA/DD`, `FDEA` e `RVEA`.
> 7. **Preservação de Resultados sem Sobrescrita, Independência Real e Reprodutibilidade das Execuções Independentes (`T15` + `T32`):**
>    - **O que falta:** Integrar [IsolatedJobExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) em `WFGStudy.main()` para:
>      1. Garantir que execuções independentes (`run = 0 .. 29`) já concluídas e íntegras (`FUN<runId>.csv` e `VAR<runId>.csv` não-vazios) jamais sejam sobrescritas em uma nova execução/retomada, preservando também os resultados acumulados em `QualityIndicatorSummary.csv` quando executados em lotes;
>      2. Garantir independência estocástica real entre as runs (eliminando a condição de corrida das 8 threads de `ExecuteAlgorithms` sobre o singleton global `JMetalRandom.getInstance()`);
>      3. Garantir que, mesmo sendo independentes entre si, cada `runId` possa ser 100% reproduzida bit-a-bit através da semente determinística derivada da tupla `(baseSeed, problemName, m, algorithmName, runId)`.
> 8. **Integração do `ResultFrontContract` (`T19`) e `ObjectiveNormalizationContract` (`T17`):**
>    - **O que falta:** Filtrar soluções dominadas e $\epsilon$-duplicatas dos arquivos `FUNx.csv` gerados por `MOEA/DD` e `RVEA` via `ResultFrontContract.extractCanonicalResultSet` antes de rodar `ComputeQualityIndicators`, e aplicar a normalização analítica (`ObjectiveNormalizationContract` com $r = (1.1, \dots, 1.1)^T$) em vez da normalização empírica min-max do jMetal.
> 9. **Integração do Pós-Processamento `Kruskal-Wallis` + `Dunn-Holm` + $\hat{A}_{12}$ (`T21`) e Manifesto (`T30`):**
>    - **O que falta:** Invocar `KruskalWallisTest` e `DunnPostHocTest` sobre `QualityIndicatorSummary.csv` ao final de `WFGStudy.main()` (e adaptar [scripts/kruskal_dunn_holm.R](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/scripts/kruskal_dunn_holm.R) para ler `QualityIndicatorSummary.csv`), além de chamar `ReferenceSetValidator.exportActiveArtifactManifestCsv` para atualizar [docs/auditoria/artifact-manifest.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-manifest.csv).
> 10. **Acoplamento opcional do `R2ExecutionObserver` em `WFGStudy` (`README.md` Seção 8):**
>     - **O que falta:** Caso se deseje coletar curvas de convergência do indicador `R2` geração a geração durante o estudo de linha de base, é necessário registrar `R2ExecutionObserver` nos algoritmos observáveis (`AbstractGeneticAlgorithm`) e persistir `getR2History()` em CSV ao término de cada run.
> 11. **Re-Auditoria Zero-Trust Completa em Ordem Cronológica Reversa (`T33`):**
>     - **O que falta:** Após resolver os itens 1 a 10 acima, reexecutar a verificação/auditoria completa em ordem cronológica reversa (`T32` $\to$ `T01`), testando tarefa por tarefa sem confiar em status declarados.

---

## 1. Pré-Condições e Verificação do Ambiente

Antes de iniciar qualquer bateria de testes ou o experimento final (etapa de avaliação isolada dos 6 MOEAs de linha de base: `NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA` e `AGE-MOEA-II`), verifique os seguintes requisitos de ambiente:

### 1.1. Requisitos de Software e Hardware
- **Java Development Kit (JDK):** Java 21 LTS (`21.0.x`).
- **Apache Maven:** `3.8+` (verificado `3.9.9`).
- **Memória RAM Recomendada:** Mínimo de `8 GB` livres (recomendado `-Xms4g -Xmx12g` para execuções em $m \in \{8, 10, 15\}$).
- **R (Opcional para scripts R do jMetal e `kruskal_dunn_holm.R`):** R `4.x` com `Rscript` disponível no `PATH`.
- **LaTeX (Opcional para compilar tabelas `.tex`):** `pdflatex` com pacotes `colortbl`, `booktabs` e `amsmath`.

Comandos de verificação rápida no PowerShell:
```powershell
java -version
mvn -version
```

### 1.2. Verificação de Integridade dos Diretórios de Artefatos
Confirme que os diretórios canônicos e protegidos existem na raiz do projeto:
- **Pesos canônicos ativos do `MOEA/DD`:**
  - `resources/weightVectorFiles/` (prioridade 1 — contém `W2D_100.dat`, `W3D_91.dat`, `W8D_156.dat`, `W10D_275.dat`).
  - `resources/weightVectorFiles/weights_gen/` (prioridade 2 — contém ou gera analiticamente sob demanda `W5D_210.dat` e `W15D_135.dat`).
  - `resources/weightVectorFiles/weights_lowzero_gen/` (utilizado exclusivamente quando `-DreplaceZeroWeights=true`).
- **Frentes de referência canônicas ativas:**
  - `resources/referenceFronts/`: frentes oficiais do jMetal (`WFG1`–`WFG9` em $2\text{D}$ e $3\text{D}$; `DTLZ1`–`DTLZ7` em $2\text{D}$ e $3\text{D}$, além de $4\text{D}, 6\text{D}, 8\text{D}$ para `DTLZ1`–`DTLZ4`).
  - `resources/referenceFronts/pf_gen/`: frentes geradas puras (`WFG1`–`WFG9` e `DTLZ1`–`DTLZ7` para $m \in \{5, 8, 10, 15\}$).
  - `resources/referenceFronts/pf_lowzero_gen/`: utilizado exclusivamente quando `-DreplaceZeroFronts=true`.
- **Diretórios legados protegidos (somente leitura conforme [AGENTS.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/AGENTS.md)):** `MOEAD_Weights/`, `pareto_fronts/`, `pareto_fronts/csv/` e `pareto/`.
- **Proibição de uso da cópia local externa do jMetal:** Nunca utilize a pasta local `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal` (que possui modificações locais e não corresponde ao jMetal mais recente ou ao 7.8 oficial); qualquer comparação ou restauração de arquivos oficiais deve referenciar o repositório oficial do jMetal no GitHub na versão correta.

---

## 2. Compilação e Execução dos Testes Automatizados

### 2.1. Compilação Limpa do Projeto
```powershell
mvn clean test-compile
```

### 2.2. Execução da Suíte de Testes Unitários e de Pré-Voo
Para rodar todos os testes exceto o teste legado em regressão (`MoeaddWeightValidationTest`):
```powershell
mvn test "-Dtest=!MoeaddWeightValidationTest"
```
> **Nota:** Os **361 testes** das demais 22 classes de teste (`NsgaIIIReferencePointsValidationTest`, `ParetoFrontValidationTest`, `QualityIndicatorUnitTest`, `AlgorithmIntegrationTest`, `R2ExecutionObserverTest`, `EvaluationBudgetAuditTest`, `FdeaIdentityAndLegacyAuditTest`, `IsolationAndReproducibilityTest`, `ReferenceFrontPhysicalAndMathAuditTest`, `NormalizationAndHvContractTest`, `ResultFrontContractTest`, `MonteCarloHypervolumeTest`, `ProcessIsolatedHypervolumeTest`, `KruskalWallisTestTest`, `PerformanceProfilerPhase3Test`, `WFGStudyPreflightTest`, `ReferenceFrontCanonicalPriorityTest`, `CanonicalArtifactResolutionAndManifestTest`, etc.) passam com **0 falhas**.

Para inspecionar especificamente a falha atual de `MoeaddWeightValidationTest`:
```powershell
mvn test "-Dtest=MoeaddWeightValidationTest"
```

---

## 3. Parâmetros e Flags de Linha de Comando (`WFGStudy`)

A classe principal [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) aceita argumentos posicionais ou propriedades de sistema (`-D...`):

| Propriedade JVM (`-D...`) | Valor Padrão | Descrição Técnica |
| :--- | :--- | :--- |
| `-Dstudy.outputDirectory` | `WFGStudy` | Diretório base onde `data/`, `latex/`, `R/` e `QualityIndicatorSummary.csv` serão gravados. **Atenção:** no estado atual do jMetal 7.8, use um nome simples sem barras (ou apenas 1 nível na raiz) para evitar `FileNotFoundException` na etapa LaTeX. |
| `-Druns` | `30` | Número de execuções independentes (`INDEPENDENT_RUNS`) por par `(algoritmo, problema)`. |
| `-Dobjectives` | `2,3,5,8,10,15` | Lista de dimensões de objetivos ($m$) separadas por vírgula. |
| `-Dproblem` | *(todos)* | Filtra a execução para um único problema específico (ex.: `-Dproblem=WFG1`, `-Dproblem=DTLZ2`). |
| `-DgenerateAllReferenceFronts` | `false` | Se `false`, executa `WFG1`–`WFG9` em todas as dimensões $m$ selecionadas e `DTLZ1`–`DTLZ7` **apenas para $m \le 3$**. Se `true`, habilita `DTLZ1`–`DTLZ7` também para $m \in \{5, 8, 10, 15\}$ (usando as frentes de `resources/referenceFronts/pf_gen/`). |
| `-DreplaceZeroWeights` | `false` | Se `false`, usa os vetores de pesos canônicos de `resources/weightVectorFiles/` e `weights_gen/` com zeros exatos (`0.0`). Se `true` (ou lista de algoritmos, ex.: `MOEADD`), resolve/gera variante com substituição `0.0 -> 1e-5` em `resources/weightVectorFiles/weights_lowzero_gen/`. |
| `-DreplaceZeroFronts` | `false` | Se `false`, usa as frentes canônicas de `resources/referenceFronts/` e `pf_gen/` com zeros exatos (`0.0`). Se `true` (ou lista de algoritmos), resolve/gera variante com substituição `0.0 -> 1e-5` em `resources/referenceFronts/pf_lowzero_gen/`. |
| `-Dstudy.weightsDirectory` | `resources/weightVectorFiles` | Diretório base para resolução canônica dos arquivos `.dat` do `MOEA/DD` (busca em `<base>` $\to$ `<base>/weights_gen/` $\to$ geração analítica em `<base>/weights_gen/`). |
| `-Dstudy.referenceFrontDirectory` | `resources/referenceFronts` | Diretório base para resolução das frentes de referência de Pareto. |
| `-Dstudy.includeLegacyAlgorithms` | `false` | Se `true`, inclui os algoritmos secundários/legados (`NSGA-II`, `SPEA2`, `MOEA/D-DRA`, `ESPEA`, `GWASFGA`) além dos 6 principais. |
| `-DprofilePerformance` | `false` | Se `true`, executa a suíte de profiling (`PerformanceProfiler.runStandardBenchmarkSuite`) ao final do estudo. |

---

## 4. Passo a Passo para Execução do Experimento

### Etapa 1: Smoke Test Rápido (Validação Pré-Campanha)
Antes de disparar horas de processamento, execute 1 run de 1 problema (`WFG1`) em $m=2$ para validar a geração de `FUN0.csv`, `VAR0.csv` e o cálculo dos 5 indicadores (`HV`, `NHV`, `IGD`, `IGD+`, `R2`):

> [!WARNING]
> Conforme as regras de [AGENTS.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/AGENTS.md), **nunca grave testes exploratórios na raiz de `WFGStudy/`**. Use `WFGStudy/tests/...` e lembre-se de que, sem a correção do `GenerateLatexTablesWithStatistics` (Pendência #4 no topo), o comando abaixo concluirá `ExecuteAlgorithms` e `ComputeQualityIndicators` (gerando todos os dados e o `QualityIndicatorSummary.csv`), mas lançará `FileNotFoundException` apenas no passo final de geração do `.tex`.

```powershell
mvn exec:java `
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" `
  "-Dstudy.outputDirectory=WFGStudy/tests/smoke_wfg1_2d" `
  "-Druns=1" `
  "-Dobjectives=2" `
  "-Dproblem=WFG1"
```

Após verificar que `WFGStudy/tests/smoke_wfg1_2d/QualityIndicatorSummary.csv` foi gerado corretamente, remova o diretório de smoke test:
```powershell
Remove-Item -Path "WFGStudy/tests/smoke_wfg1_2d" -Recurse -Force
```

---

### Etapa 2: Execução da Campanha Experimental Final por Lotes de Dimensionalidade ($m$)

Devido ao custo exponencial do indicador de Hipervolume exato (`PISAHypervolume`) a partir de $m=8$, **recomenda-se fortemente executar a campanha final particionada por número de objetivos ($m$)** em vez de rodar todas as dimensões de uma só vez.
- **Nota sobre Preservação entre Lotes (`T32`):** `ExecuteAlgorithms` verifica se cada `FUN<runId>.csv` já existe em `WFGStudy/data/` e **não reexecuta nem sobrescreve** runs já concluídas. Porém, como `ComputeQualityIndicators` recria `QualityIndicatorSummary.csv` apenas para as dimensões passadas em `-Dobjectives` na invocação atual, ao terminar todos os lotes ($m \in \{2,3,5,8,10,15\}$) — ou após a correção da Tarefa `T32` — execute a consolidação final com `-Dobjectives=2,3,5,8,10,15` (que pulará 100% da execução dos algoritmos porque todos os `FUN0.csv`..`FUN29.csv` já existirão e consolidará todos os indicadores juntos no `QualityIndicatorSummary.csv`).

#### Lote A: Baixa/Média Dimensionalidade ($m \in \{2, 3, 5\}$) — WFG1..9 + DTLZ1..7 (30 runs)
Esta bateria é rápida e segura mesmo com o `PISAHypervolume` exato:

1. **Somente `m = 2, 3` (WFG1–WFG9 e DTLZ1–DTLZ7 padrão):**
   ```powershell
   $env:MAVEN_OPTS="-Xms4g -Xmx8g"
   mvn exec:java `
     "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" `
     "-Dstudy.outputDirectory=WFGStudy" `
     "-Druns=30" `
     "-Dobjectives=2,3" `
     "-DreplaceZeroWeights=false" `
     "-DreplaceZeroFronts=false"
   ```

2. **Dimensão `m = 5` (incluindo DTLZ1–DTLZ7 com `-DgenerateAllReferenceFronts=true`):**
   ```powershell
   $env:MAVEN_OPTS="-Xms4g -Xmx8g"
   mvn exec:java `
     "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" `
     "-Dstudy.outputDirectory=WFGStudy" `
     "-Druns=30" `
     "-Dobjectives=5" `
     "-DgenerateAllReferenceFronts=true" `
     "-DreplaceZeroWeights=false" `
     "-DreplaceZeroFronts=false"
   ```

#### Lote B: Dimensão `m = 8` (30 runs)
Em $m=8$ ($N=156$, $1.000$ a $1.500$ gerações), o tempo de execução dos algoritmos e do `PISAHypervolume` aumenta moderadamente (~35 ms por frente no WFG HV):
```powershell
$env:MAVEN_OPTS="-Xms4g -Xmx12g"
mvn exec:java `
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" `
  "-Dstudy.outputDirectory=WFGStudy" `
  "-Druns=30" `
  "-Dobjectives=8" `
  "-DgenerateAllReferenceFronts=true" `
  "-DreplaceZeroWeights=false" `
  "-DreplaceZeroFronts=false"
```

#### Lote C: Alta Dimensionalidade ($m \in \{10, 15\}$) — **REQUER RESOLUÇÃO DAS PENDÊNCIAS #2 E #5**
> [!IMPORTANT]
> **Não execute o Lote C antes de:**
> 1. Integrar `ProcessIsolatedHypervolumeExecutor` (com limite de 60 min) e `MonteCarloHypervolume` (Hipervolume Aproximado) em `WFGStudy.java` (Pendência #2 — caso contrário, `ComputeQualityIndicators` ficará travado por mais de 11 horas por frente no cálculo de `HV` e `NHV` em $m=15$).
> 2. Corrigir os cases `"DTLZ5"` e `"DTLZ6"` em `StaticParams.getMaxGenerations` para $m=10$ (`1500`) e $m=15$ (`2000`) (Pendência #5).
> 3. Nota sobre `W15D_135.dat`: A resolução canônica (`ReferenceSetValidator.resolveCanonicalWeightFile`) já localiza (ou gera analiticamente sob demanda) `resources/weightVectorFiles/weights_gen/W15D_135.dat`, sem necessidade de apontar para a pasta legada `MOEAD_Weights/`, visto que ela é protegida para não ser usada exceto em testes legados e apenas leitura, não copia de seus dados para outras pastas.

Comando para $m=10$:
```powershell
$env:MAVEN_OPTS="-Xms6g -Xmx14g"
mvn exec:java `
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" `
  "-Dstudy.outputDirectory=WFGStudy" `
  "-Druns=30" `
  "-Dobjectives=10" `
  "-DgenerateAllReferenceFronts=true" `
  "-DreplaceZeroWeights=false" `
  "-DreplaceZeroFronts=false"
```

Comando para $m=15$:
```powershell
$env:MAVEN_OPTS="-Xms6g -Xmx14g"
mvn exec:java `
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" `
  "-Dstudy.outputDirectory=WFGStudy" `
  "-Druns=30" `
  "-Dobjectives=15" `
  "-DgenerateAllReferenceFronts=true" `
  "-DreplaceZeroWeights=false" `
  "-DreplaceZeroFronts=false"
```

---

## 5. Estrutura de Saída Gerada pelo Experimento

Ao concluir a execução em `WFGStudy/`, a seguinte árvore de artefatos é produzida:

```text
WFGStudy/
├── data/
│   ├── NSGAIII/
│   │   ├── WFG1_2/
│   │   │   ├── FUN0.csv ... FUN29.csv       # Frentes de objetivos das 30 runs
│   │   │   ├── VAR0.csv ... VAR29.csv       # Variáveis de decisão das 30 runs
│   │   │   ├── HV, NHV, IGD, IGD+, R2       # Valores dos indicadores (30 linhas cada)
│   │   │   ├── BEST_HV_FUN.csv, ...         # Melhor frente por indicador
│   │   │   └── MEDIAN_HV_FUN.csv, ...       # Frente mediana por indicador
│   │   └── ... (demais problemas e dimensões)
│   ├── MOEADD/
│   ├── FDEA/
│   ├── RVEA/
│   ├── AGE-MOEA/
│   └── AGE-MOEA-II/
├── QualityIndicatorSummary.csv              # Tabela mestre (Algorithm, Problem, IndicatorName, ExecutionId, IndicatorValue)
├── latex/
│   ├── WFGStudy.tex                         # Tabelas de Média/Desvio Padrão e Mediana/IQR
│   └── FriedmanTest*.tex                    # Tabelas de rankings do teste de Friedman
└── R/
    └── *.Wilcoxon.R / *.Wilcoxon.tex        # Scripts R e tabelas LaTeX de Wilcoxon par-a-par
```

Para os procedimentos detalhados de pós-processamento estatístico (`Kruskal-Wallis`, `Dunn-Holm`, $\hat{A}_{12}$ de Vargha-Delaney, compilação de scripts R e tabelas LaTeX), consulte o documento atualizado [pipeline-pos_processamento.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/pipeline-pos_processamento.md).

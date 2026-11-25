# Relatório de Reauditoria Independente Zero-Trust Pós-Correções (Fase 5) — Projeto `hypeMOEA`

- **Data da Reauditoria:** 18/11/2026 (`10:35` BRT / `-05:00`)
- **Escopo:** Auditoria técnica e científica independente em **Ordem Cronológica Reversa (`T37` $\to$ `T01`, incluindo `T32` e `T33`)** sob metodologia *Zero-Trust* (desconsiderando relatórios prévios de agentes e validando diretamente contra o código-fonte Java real, bytecode do jMetal 7.8, artefatos físicos em disco, manifestos SHA-256, execução integral sem exclusões de `mvn clean test` e *smoke tests* reais via CLI em diretórios isolados).
- **Regra de Escopo da Entrega:** Conforme instrução explícita do operador humano, **este arquivo é o único arquivo criado/modificado nesta etapa de auditoria**; nenhum arquivo de código-fonte, teste, `README.md`, `HANDLER.md`, `TASK_COORDINATOR.md` ou script foi alterado durante a auditoria, e todos os diretórios temporários de *smoke test* criados em `WFGStudy/tests/` foram integralmente removidos após a coleta de evidências (`SMOKE_DIRS_CLEANED=True`).
- **Ambiente Verificado:**
  - **SO:** Windows 10 (`10.0`, `amd64`)
  - **Java:** OpenJDK `21.0.9` (`Eclipse Adoptium 21.0.9+10-LTS`)
  - **Maven:** Apache Maven `3.9.9`
  - **R Base (Validação Estatística):** `R version 4.6.2 (2026-10-29 ucrt)` em `C:\Program Files\R\R-4.6.2\bin\Rscript.exe` (pacotes nativos `stats` e `utils`, sem dependência de pacotes externos do CRAN)
  - **Dependência Principal:** `org.uma.jmetal:jmetal-core:7.8`, `jmetal-algorithm:7.8`, `jmetal-problem:7.8`, `jmetal-lab:7.8`, `jmetal-component:7.8` (confirmado em [pom.xml](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/pom.xml#L24-L45))

---

## 1. Sumário Executivo da Reauditoria Zero-Trust

Na auditoria anterior ([relatorio_auditoria-pre_execucao_final.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/relatorio_auditoria-pre_execucao_final.md), de 17/11/2026), haviam sido identificados **4 bloqueios críticos de execução** e **7 desconexões arquiteturais** entre os utilitários das Fases 3/4 e o método `WFGStudy.main()`.

A presente reauditoria independente *Zero-Trust*, conduzida após a execução da Fase 5 (`T32`–`T37`), reexecutou integralmente a suíte Maven do zero (`mvn clean test`) e dois *smoke tests* reais de linha de comando (`mvn exec:java`) em `WFGStudy/tests/zerotrust_audit_smoke_2d` (em 2 lotes sequenciais, com isolamento de processo `-Dstudy.processIsolation=true`) e `WFGStudy/tests/zerotrust_audit_smoke_15d` (`WFG4`, $m=15$, sob *deadline* de hipervolume), comprovando que **todas as falhas, desconexões e pendências apontadas no relatório anterior foram integralmente corrigidas no código real**:

1. **Suíte Integral de Testes Automatizados 100% Aprovada Sem Exclusões (`394` testes executados em `27` classes, `0` falhas, `0` erros, `0` ignorados):**
   - As 7 falhas anteriores de [MoeaddWeightValidationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java) foram resolvidas sem *greenwashing*: a suíte separa rigorosamente (a) a validação estrutural e matemática dos 6 artefatos canônicos ativos em `resources/weightVectorFiles/` e `weights_gen/`, (b) a auditoria forense somente leitura dos 43 arquivos históricos imutáveis em `MOEAD_Weights/` (camada interna contraída de Deb & Jain com substituição `0 -> 1e-5`), e (c) a verificação SHA-256 das 29 cópias oficiais do jMetal 7.8 em `resources/weightVectorFiles/` contra [moead-weights-manifest.csv](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/moead-weights-manifest.csv) (`22/22` testes aprovados na classe).
   - Foram adicionadas 4 novas suítes de integração fim-a-fim que testam o entrypoint real `WFGStudy.main()`: [StudyOutputDirectoryAndGenerationsTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/StudyOutputDirectoryAndGenerationsTest.java) (`3` testes), [StudyBudgetIsolationAndResumeIntegrationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/StudyBudgetIsolationAndResumeIntegrationTest.java) (`6` testes), [StudyIndicatorPipelineIntegrationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/StudyIndicatorPipelineIntegrationTest.java) (`4` testes) e [StudyStatisticalAndProfilingIntegrationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/StudyStatisticalAndProfilingIntegrationTest.java) (`3` testes).
2. **Acoplamento Completo dos Contratos e Mitigadores em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L438-L605):**
   - `WFGStudy.main()` agora invoca diretamente:
     - `WFGStudy.resolveStudyOutputLayout` (linhas 439–440), eliminando a `FileNotFoundException` do `GenerateLatexTablesWithStatistics` em subdiretórios aninhados (`WFGStudy/tests/...`);
     - `runPreflightValidationAndResolveArtifacts` + `ReferenceSetValidator.exportManifestFromResolvedArtifacts` (linhas 455–465 e 509–515), gravando `<outputDir>/artifact-manifest.csv` com estágios `PREFLIGHT_PLANNED` e `OBSERVED_CONSUMED` / `OBSERVED_EXECUTION_FAILED`;
     - `IsolatedJobExecutor.executeAndPersistBatch` (linhas 481–492), que instancia cada problema envelopado por `EvaluationBudgetProblem` (e `BudgetCompliantMOEADD`), aplica semente determinística de 64 bits (`SHA-256` + `SplitMix64`), filtra frentes por `ResultFrontContract.extractCanonicalResultSet` e preserva runs concluídas entre lotes via `run_manifest.csv`;
     - `computeContractualIndicatorsAndExport` (linhas 529–543), que normaliza objetivos por `ObjectiveNormalizationContract` (`THEORETICAL_PARETO_BOUNDS`), executa o hipervolume exato isolado em subprocesso (`ProcessIsolatedHypervolumeExecutor` / `ExactHypervolumeWorker`) + série secundária `MonteCarloHypervolume` (`HV_MC`) sob a mesma caixa $[\mathbf{0}, \tilde{\mathbf{r}}]$ (com `NHV` suspenso no perfil ativo conforme decisão `A3` e fallback único configurável `-Dstudy.hvFallbackToMcOnAllExactTimeout`);
     - `StudyStatisticalAnalyzer.analyzeAndExportStudy` (linha 557), executando Kruskal–Wallis + Dunn–Holm + $\hat{A}_{12}$ orientado por célula, Friedman por blocos entre problemas e validação cruzada automática contra `R 4.6.2` base ([scripts/kruskal_dunn_holm.R](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/scripts/kruskal_dunn_holm.R)).
3. **Correção de `DTLZ5` e `DTLZ6` em $m=10$ e $m=15$ ([StaticParams.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/StaticParams.java#L15-L89)):**
   - Confirmado no código e em teste que `StaticParams.getMaxGenerations` retorna explicitamente **`1500` gerações em $m=10$** (linha 72) e **`2000` gerações em $m=15$** (linha 82) para `DTLZ5` e `DTLZ6` (`{1000, 1000, 1000, 1000, 1500, 2000}` para $m \in \{2, 3, 5, 8, 10, 15\}$).
4. **Gerador Analítico para `WFG1`–`WFG9` (Prioridade 3) e Fallback Opcional de Restauração Oficial (`-Dstudy.restoreOfficialJMetalResources=false`):**
   - Implementados em [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1400-L1975) e [ReferenceSetValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java#L2420-L2614), com bloqueio estrito contra acesso às pastas legadas protegidas (`MOEAD_Weights/`, `pareto_fronts/`, `pareto/`) e à cópia local modificada `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`.

---

## 2. Matriz Consolidada de Validação Zero-Trust (Ordem Cronológica Reversa: `T37` $\to$ `T01`)

| ID | Fase | Descrição Resumida | Status na Auditoria Anterior (17/11) | Status Atual Verificado (18/11) | Testes / Evidências Empíricas Executadas | Veredito Técnico Zero-Trust |
| :--- | :---: | :--- | :---: | :---: | :--- | :--- |
| **T37** *(absorve **T33**)* | Fase 5 | Aceite Fim-a-Fim, Reauditoria Reversa Reconciliada e Resolução de Classpath sob `mvn exec:java` | *Não existia* (`T33` Pendente) | **APROVADA** | `mvn clean test` (`394/394` pass) + Smokes CLI 2D multi-lote e 15D (`task-1165`) | `ProcessIsolatedHypervolumeExecutor.resolveEffectiveRuntimeClassPath()` extrai URLs do `URLClassLoader` do `exec-maven-plugin`; subprocessos `ExactHypervolumeWorker` e `IsolatedJobExecutor --worker` executam com `SUCCESS_EXACT` via `mvn exec:java`. |
| **T36** | Fase 5 | Pós-Processamento Estatístico Integrado, Validação Cruzada em R 4.6.2 Base, `R2ExecutionObserver` Passivo Opt-in e Telemetria | *Não existia* | **APROVADA** | `StudyStatisticalAndProfilingIntegrationTest` (`3/3` pass) + Smoke CLI R 4.6.2 (`75` pares, erro máx $H = 4{,}32 \times 10^{-12}$) | `StudyStatisticalAnalyzer` integrado ao `WFGStudy.main()`, gerando KW + Dunn–Holm + $\hat{A}_{12}$ orientado por célula, Friedman por blocos entre problemas, `Phase5StatisticalSummary.tex` e validação cruzada automática em R base nativo. |
| **T35** | Fase 5 | Normalização Teórica, Paridade de Caixa de HV (`PISA`/`WFG`/`MC`), Suspensão Temporária de `NHV` e Fallback Único Configurável | *Não existia* | **APROVADA** | `StudyIndicatorPipelineIntegrationTest` (`4/4` pass) + Smoke CLI 15D (`WFG4_15`, `pisa_status=SUCCESS_EXACT`) | `THEORETICAL_PARETO_BOUNDS` ativo sem *clipping* silencioso; paridade $\le 10^{-12}$ entre `PISA`, `WFG` e `MC` em $[\mathbf{0}, \tilde{\mathbf{r}}]$; `NHV` suspenso no perfil ativo (`A3`); timeout grava `NA` (nunca `0.0`); opção `-Dstudy.hvFallbackToMcOnAllExactTimeout` funcional. |
| **T34** | Fase 5 | Geradores Analíticos `WFG1`–`WFG9`, Validação Prévia de Conteúdo, Fallback Oficial Opt-in, Política 2D em Memória e Manifesto Efetivo | *Não existia* | **APROVADA** | `CanonicalArtifactResolutionAndManifestTest` (`11/11` pass) + Manifesto CLI (`OBSERVED_CONSUMED`) | Geradores analíticos `WFG1–WFG9` na Prioridade 3; validação de conteúdo nas Prioridades 1/2; flag `-Dstudy.restoreOfficialJMetalResources=false`; fronteira $m=2, 2 \le N \le 300$; manifesto efetivo pré/pós-execução em `<outputDir>/artifact-manifest.csv`. |
| **T33** *(absorve **T32**)* | Fase 5 | Orçamento Equitativo $B_{\text{target}}$, Isolamento de RNG/Processo, Preservação de Lotes sem Sobrescrita e `DTLZ5/6` | `T32` Parcial | **APROVADA** | `StudyBudgetIsolationAndResumeIntegrationTest` (`6/6` pass) + Smoke CLI Lote 1 $\to$ Lote 2 (`FUN0_PRESERVED=True`) | `IsolatedJobExecutor` e `EvaluationBudgetProblem` (`BudgetCompliantMOEADD`) integrados ao `WFGStudy.main()`; semente `SHA-256` + `SplitMix64` por `runId`; merge não-destrutivo de `QualityIndicatorSummary.csv` e `hv_audit_manifest.csv` entre lotes. |
| **T32** | Fase 5 | Reconciliação de Baseline, `StudyOutputLayout` para Subpastas e Reconciliação de `MoeaddWeightValidationTest` | *Não existia* | **APROVADA** | `StudyOutputDirectoryAndGenerationsTest` (`3/3` pass) + `MoeaddWeightValidationTest` (`22/22` pass) | `resolveStudyOutputLayout` separa `parentBaseDirectory` e `experimentName` simples, gerando `latex/<leaf>.tex` sem `FileNotFoundException` em subpastas aninhadas. |
| **T31** | Fase 4 | Exclusão Condicionada de `ArtifactResolver`, Validação da Fase 4 e Sincronização Documental | Reprovada (Parcial: `366/373`) | **APROVADA (RECONCILIADA EM T32/T37)** | `mvn clean test` (`394/394` pass) + Inspeção de `src/` | `ArtifactResolver.java` permanece removido sem referências órfãs; todas as 27 classes de teste passam com `0` falhas e a documentação reflete a arquitetura real. |
| **T30** | Fase 4 | Consumidores e Manifesto dos Artefatos Efetivos | Parcial | **APROVADA (INTEGRADA EM T34)** | `CanonicalArtifactResolutionAndManifestTest` (`11/11` pass) + `<outputDir>/artifact-manifest.csv` | O manifesto histórico `docs/auditoria/artifact-manifest.csv` da Fase 3 (`T18`) foi preservado intacto como registro imutável, enquanto `WFGStudy.main()` exporta o manifesto canônico ativo em `<outputDir>/artifact-manifest.csv`. |
| **T29** | Fase 4 | Resolução Canônica, Retornos Estruturados (`Resolved*Artifact`), `baseDir` e Eliminação de Fallbacks Legados | Reprovada (Parcial) | **APROVADA (RECONCILIADA EM T32/T34)** | `MoeaddWeightValidationTest` (`22/22`), `CanonicalArtifactResolutionAndManifestTest` (`11/11`) | `ResolvedFrontArtifact` e `ResolvedWeightArtifact` propagam `baseDir` hermeticamente sem mutar `user.dir` e sem acessar `MOEAD_Weights/` ou `pareto_fronts/csv/`. |
| **T28** | Fase 4 | Centralização de Primitivas Matemáticas (`dominanceComparison`) e Resolução de Frentes | Parcial | **APROVADA (COMPLETADA EM T34)** | `ReferenceFrontCanonicalPriorityTest` (`6/6` pass) + `ResultFrontContractTest` (`5/5` pass) | Dominância centralizada em `ParetoFrontValidator.dominanceComparison`; override `-Dstudy.referenceFrontDirectory` respeitado; gerador analítico `WFG1–WFG9` e restauração oficial opt-in implementados. |
| **T27** | Fase 4 | Plano de Mitigação de Dívida Técnica e Política `replaceZeroFronts` (Default `false`) | Aprovada | **APROVADA** | `CanonicalArtifactResolutionAndManifestTest` (pass) | `-DreplaceZeroFronts=false` por padrão; quando ativada, isola variantes em `pf_lowzero_gen/` sem contaminar `pf_gen/`. |
| **T26** | Fase 4 | Arquitetura de Destino e Resolução Canônica de Pesos (`replaceZeroWeights=false`) | Parcial | **APROVADA (COMPLETADA EM T34)** | `CanonicalArtifactResolutionAndManifestTest` (`testAllowMoeadWeightsInMemoryPolicy` pass) | `-DreplaceZeroWeights=false` e `-Dstudy.allowMoeadWeightsInMemory=false` operacionais e testados em `WFGStudy.validateMoeadWeightFile`. |
| **T25** | Fase 4 | Inventário Arquitetural e Auditoria de Referências Legadas / Flags `replaceZero*` | Aprovada | **APROVADA** | Inspeção de `docs/auditoria/analise_flags_lowzero.md` e `docs/auditoria_referencias_legadas.md` | Documentação forense consistente com a implementação ativa. |
| **T24** | Fase 3 | Dossiê Metodológico Consolidado e Matriz de Evidências | Reprovada (Parcial) | **APROVADA (RECONCILIADA EM T33–T37)** | Inspeção de `docs/methodology/` + `WFGStudy.java` | Com o acoplamento efetivo de todos os contratos da Fase 3 em `WFGStudy.main()` durante a Fase 5, as garantias metodológicas descritas no dossiê correspondem ao comportamento real do pipeline. |
| **T23** | Fase 3 | Integração no `WFGStudy`, Modos de Campanha, Flags CLI e Preflight | Parcial (Crash no LaTeX) | **APROVADA (CORRIGIDA EM T32–T36)** | `WFGStudyPreflightTest` (`3/3` pass) + Smokes CLI 2D/15D | `WFGStudy.main()` executa de ponta a ponta em subdiretórios aninhados (`WFGStudy/tests/...`), gerando tabelas LaTeX e scripts R sem `FileNotFoundException`. |
| **T22** | Fase 3 | Reauditoria do `PerformanceProfiler`, CPU/Memória de Workers e Piloto 15D | Parcial | **APROVADA (INTEGRADA EM T35/T36)** | `PerformanceProfilerPhase3Test` (`4/4` pass) + `execution_profile_runs.csv` | `PerformanceProfiler.exportStudyExecutionProfileCsv` integrado ao `WFGStudy.main()`, exportando telemetria por job (`wallTimeMs`, `cpuTimeMs`, RSS/Heap, tamanhos de frente bruta vs. canônica). |
| **T21** | Fase 3 | Inferência Estatística Não-Paramétrica (`KruskalWallisTest`, `DunnPostHocTest`, $\hat{A}_{12}$) e Validação em R | Parcial | **APROVADA (INTEGRADA EM T36)** | `KruskalWallisTestTest` (`5/5` pass) + `StudyStatisticalAndProfilingIntegrationTest` (`3/3` pass) | Conectada ao `WFGStudy.main()` via `StudyStatisticalAnalyzer` e validada ao vivo contra `scripts/kruskal_dunn_holm.R` em R 4.6.2 base. |
| **T20** | Fase 3 | Estratégia Híbrida de Hipervolume com Isolamento de Processo, Cancelamento Forçado e Monte Carlo | Parcial | **APROVADA (INTEGRADA EM T35)** | `MonteCarloHypervolumeTest` (`6/6`), `ProcessIsolatedHypervolumeTest` (`3/3`), `StudyIndicatorPipelineIntegrationTest` (`4/4`) | Conectada ao `WFGStudy.main()`; `ExactHypervolumeWorker` e `MonteCarloHypervolume` operam sobre a mesma caixa $[\mathbf{0}, \tilde{\mathbf{r}}]$ com timeout real e eliminação recursiva de processos filhos. |
| **T19** | Fase 3 | Contrato da População Final (`result()`) e Alinhamento Estrito 1:1 `FUN`/`VAR` (`ResultFrontContract`) | Parcial | **APROVADA (INTEGRADA EM T33/T35)** | `ResultFrontContractTest` (`5/5` pass) + `StudyBudgetIsolationAndResumeIntegrationTest` | `IsolatedJobExecutor` grava `FUN<r>.raw.csv`/`VAR<r>.raw.csv` e filtra `FUN<r>.csv`/`VAR<r>.csv` via `ResultFrontContract.extractCanonicalResultSet` (não-dominadas únicas alinhadas 1:1). |
| **T18** | Fase 3 | Migração Controlada de Caminhos de Artefatos e Manifesto SHA-256 | Aprovada (Histórica) | **APROVADA (HISTÓRICA)** | `docs/auditoria/artifact-manifest.csv` preservado intacto | Registro histórico da Fase 3 mantido inalterado; sucedido operacionalmente pelo manifesto por estudo da `T34`. |
| **T17** | Fase 3 | Contrato de Normalização de Objetivos, Invariância de Escala e Ponto de Referência Explícito | Parcial | **APROVADA (INTEGRADA EM T35)** | `NormalizationAndHvContractTest` (`13/13` pass) + `StudyIndicatorPipelineIntegrationTest` | `ObjectiveNormalizationContract` (`THEORETICAL_PARETO_BOUNDS`) integrado ao `WFGStudy.main()`, eliminando a distorção de $260\times$ da normalização empírica min-max em `WFG4.15D`. |
| **T16** | Fase 3 | Auditoria Física e Matemática das Frentes de Referência e Geração DTLZ ($m > 3$) | Aprovada | **APROVADA** | `ReferenceFrontPhysicalAndMathAuditTest` (`4/4` suítes parametrizadas cobrindo todas as frentes, `0` falhas) | Todas as 92 frentes oficiais em `resources/referenceFronts/` e as frentes analíticas em `pf_gen/` validadas quanto a finitude, não-dominância e aderência às superfícies analíticas. |
| **T15** | Fase 3 | Isolamento de Execução, Controle de Sementes (`SplitMix64`) e Reprodutibilidade | Parcial | **APROVADA (INTEGRADA EM T33)** | `IsolationAndReproducibilityTest` (`15/15` pass) + `StudyBudgetIsolationAndResumeIntegrationTest` | `IsolatedJobExecutor` integrado ao `WFGStudy.main()`; 0 colisões em 17.280 tuplas; reprodutibilidade bit-a-bit por `runId` independentemente da ordem ou paralelismo. |
| **T14** | Fase 3 | Auditoria de Identidade Algorítmica do `FDEA`, Algoritmos Legados e Parâmetros Internos | Aprovada | **APROVADA** | `FdeaIdentityAndLegacyAuditTest` (`9/9` pass) | `FDEA` 100% determinístico via `JMetalRandom`, sem código morto e com listener geracional passivo (`setGenerationPopulationListener`) para `R2ExecutionObserver`. |
| **T13** | Fase 3 | Contrato e Auditoria do Orçamento de Avaliações (`EvaluationBudgetProblem`) | Parcial | **APROVADA (INTEGRADA EM T33)** | `EvaluationBudgetAuditTest` (`17/17` pass) + `StudyBudgetIsolationAndResumeIntegrationTest` | Todos os 6 algoritmos canônicos respeitam estritamente $B_{\text{target}} = N_{\text{config}}(m) \times G_{\max}(m, \text{problema})$ no pipeline real, sem objetivos fictícios. |
| **T12** | Fase 3 | Reconciliação de Governança, Inventário Forense e Escopo Experimental (Gates G0/G1) | Aprovada | **APROVADA** | Inspeção de `docs/decisions/G0-G1-scope-and-decisions.md` | Escopo experimental de 6 MOEAs canônicos ativos por padrão (`-Dstudy.includeLegacyAlgorithms=false`) mantido e verificado. |
| **T11** | Fase 2 | Auditoria Completa, Manifesto SHA-256 e Sincronização dos Pesos do `MOEA/DD` | Reprovada (`7` falhas) | **APROVADA (RECONCILIADA EM T32)** | `MoeaddWeightValidationTest` (`22/22` pass) | Reconciliada com a reversão de `MOEAD_Weights/` (`43` arquivos imutáveis) e as `29` cópias oficiais do jMetal 7.8 em `resources/weightVectorFiles/`. |
| **T10** | Fase 2 | Validação da Fase 2 e Relatório Consolidado | Aprovada | **APROVADA** | Inspeção de `docs/final-validation-report.md` e `docs/resolucao_duplicatas_arquitetura.md` | Entregáveis históricos íntegros. |
| **T09** | Fase 2 | Instrumentação e Avaliação de Desempenho Computacional (`PerformanceProfiler`) | Aprovada | **APROVADA** | `PerformanceProfilerTest` (`8/8` pass) | Medição separada de *wall-clock*, CPU e Heap com timeout configurável `-Dperf.hvTimeoutMs`. |
| **T08** | Fase 2 | Hipervolume, Pontos de Referência por Problema/$m$ e Normalização | Aprovada | **APROVADA** | `HypervolumeAuditTest` (`12/12` pass) | Pontos de referência brutos e escalados dominam estritamente 100% da variedade de Pareto em todas as dimensões. |
| **T07** | Fase 2 | Revalidação de `NSGA-III` e `RVEA`, Persistência Opcional e Extensão Analítica DTLZ | Aprovada | **APROVADA** | `RveaReferenceDirectionsValidationTest` (`8/8` pass), `NsgaIIIReferencePointsValidationTest` (`22/22` pass) | Direções unitárias $\ell_2$ do `RVEA`, camadas $(H_1, H_2)$ do `NSGA-III` e flags opcionais desativadas por padrão verificadas. |
| **T06** | Fase 2 | Auditoria de Operadores Comuns (`SBXCrossover`, `PolynomialMutation`, `DE`) | Aprovada | **APROVADA** | `OperatorAuditTest` (`7/7` pass) | Confirmados em bytecode $p_c = 1{,}0, \eta_c = 30{,}0$, $p_m = 1/n, \eta_m = 20{,}0$ e reparo `RepairDoubleSolutionWithBoundValue`. |
| **T05** | Fase 1 | Padronização de Parâmetros, `NSGA-III`, `MOEA/DD` e Testes Unitários de Indicadores | Aprovada | **APROVADA** | `QualityIndicatorUnitTest` (`12/12` pass) | Indicadores unitários validados analiticamente; contextualizada a suspensão temporária de `NHV` no perfil ativo da Fase 5 (`A3`), mantendo `NormalizedHypervolume` e seus testes unitários 100% ativos. |
| **T04** | Fase 1 | Validação das Frentes de Pareto de Referência e Integração dos 6 Algoritmos | Aprovada | **APROVADA** | `ParetoFrontValidationTest` (`164/164` pass), `AlgorithmIntegrationTest` (`19/19` pass), `R2ExecutionObserverTest` (`3/3` pass) | Frentes WFG e DTLZ e execução integrada dos 6 algoritmos validadas sem erros. |
| **T03** | Fase 1 | Validação dos Arquivos de Pesos do `MOEA/DD` e Alternativas Nativas | Aprovada (com ressalva em T11) | **APROVADA** | `MoeaddWeightValidationTest` (`22/22` pass) | Equivalência exata ($\le 4{,}72 \times 10^{-16}$) de `MOEAD_Weights/` com `generateZeroReplacedCanonical(..., 1e-5)` e resolução ativa via `resources/weightVectorFiles/`. |
| **T02** | Fase 1 | Validação de `reference-calc.java` e Pontos do `NSGA-III` contra Das & Dennis | Aprovada | **APROVADA** | `NsgaIIIReferencePointsValidationTest` (`22/22` pass) | Simulação de `reference-calc.java` e validação de `ReferencePointGenerator` / `NSGAIII` 100% aprovadas. |
| **T01** | Fase 1 | Origem e Fluxo dos Artefatos e Conformidade de Parâmetros (`StaticParams`) | Parcial (Bug `DTLZ5/6` em $m=10,15$) | **APROVADA (CORRIGIDA EM T33)** | `StudyOutputDirectoryAndGenerationsTest` (`testDtlz5AndDtlz6MaxGenerations across all m` pass) | `StaticParams.getMaxGenerations` corrigido para `{1000, 1000, 1000, 1000, 1500, 2000}` em `DTLZ5` e `DTLZ6`; matriz de procedência intacta. |

---

## 3. Análise Técnica Detalhada em Ordem Cronológica Reversa (`T37` $\to$ `T01`)

### 3.0. Fase 5 — Integração Pré-Campanha, Blindagem do Pipeline e Homologação (`T37` $\to$ `T32`, absorvendo `T33` e `T32`)

#### **Tarefa `T37` (incorporando `T33` do relatório) — Aceite Fim-a-Fim, Reauditoria Reversa e Classpath de Subprocessos sob `mvn exec:java`**
- **Especificação:** [task37.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task37.md) e seção `T33` de [relatorio_auditoria-pre_execucao_final.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/relatorio_auditoria-pre_execucao_final.md#L74-L81).
- **Status:** **APROVADA**
- **Evidências no Código e Testes:**
  - [ProcessIsolatedHypervolumeExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java#L58-L94) implementa `resolveEffectiveRuntimeClassPath()`, combinando `System.getProperty("java.class.path")` com a hierarquia de `URLClassLoader` de `Thread.currentThread().getContextClassLoader()` e `ProtectionDomain.getCodeSource()`. Esse mesmo método é consumido por [IsolatedJobExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java#L418-L420) ao instanciar workers JVM isolados (`-Dstudy.processIsolation=true`).
  - **Importância crítica dessa correção:** Quando o estudo é iniciado via `mvn exec:java`, o plugin `exec-maven-plugin` coloca apenas o `plexus-classworlds.jar` do Maven em `java.class.path` e carrega as classes do projeto e os JARs do jMetal 7.8 em um `URLClassLoader` de contexto. Sem `resolveEffectiveRuntimeClassPath()`, qualquer subprocesso JVM filho (`ExactHypervolumeWorker` ou `IsolatedJobExecutor --worker`) falhava com `ClassNotFoundException` (`CRASH_NO_FALLBACK`).
  - **Comprovação empírica nesta auditoria (`task-1165`):**
    1. Teste unitário dedicado `testResolveEffectiveRuntimeClassPathExtractsUrlClassLoaderEntries` em [StudyIndicatorPipelineIntegrationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/StudyIndicatorPipelineIntegrationTest.java#L321-L339): **APROVADO**.
    2. Execução real via `mvn exec:java` em `WFG4` ($m=2$, com `-Dstudy.processIsolation=true`) e em `WFG4` ($m=15$): todas as execuções do worker exato `ExactHypervolumeWorker` concluíram com `pisa_status=SUCCESS_EXACT` (tempos entre `25 ms` e `3078 ms` em $m=15$), sem nenhum erro de classpath e com `0` processos `java.exe` órfãos ao término.

---

#### **Tarefa `T36` — Pós-Processamento Estatístico Integrado, Validação Cruzada em R 4.6.2 Base, `R2ExecutionObserver` Opt-in e Telemetria**
- **Especificação:** [task36.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task36.md).
- **Status:** **APROVADA**
- **Evidências no Código e Testes:**
  - [StudyStatisticalAnalyzer.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/statistics/StudyStatisticalAnalyzer.java#L118-L225) é invocado diretamente em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L557) logo após a consolidação de `QualityIndicatorSummary.csv`.
  - **Desenho estatístico verificado:**
    - Unidade experimental estrita por célula `(problemTag, m, indicator)` com `runId` como observação independente (jamais agregando runs de problemas distintos no mesmo Kruskal–Wallis);
    - Orientação explícita da direção de otimização: `MAXIMIZATION_INDICATORS = Set.of("HV", "HV_WFG", "HV_MC", "HV_FALLBACK_MC")` vs. minimização (`IGD`, `IGD+`, `R2`), refletida em $\hat{A}_{12}^{\text{better}}$ e na identificação da melhor mediana;
    - Estados explícitos de disponibilidade por célula (`COMPLETE`, `PARTIAL_MISSING_EXACT`, `UNAVAILABLE_ALL_NA`, `INSUFFICIENT_RUNS_FOR_INFERENCE`), mantendo `HV` exato e `HV_MC` aproximado em análises separadas sem preenchimento cruzado silencioso;
    - Comparação global entre problemas realizada separadamente por **Teste de Friedman em blocos** sobre as medianas das células completas (`computeFriedmanAcrossCompleteCells`, linhas 436–537), com correção exata de empates e estatística $F_F$ de Iman–Davenport, exportando `statistics/friedman_global_blocks.csv` e `latex/Phase5StatisticalSummary.tex`.
  - **Validação cruzada ao vivo contra R 4.6.2 Base ([scripts/kruskal_dunn_holm.R](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/scripts/kruskal_dunn_holm.R)):**
    - O script R preserva o modo `--fixture` original e implementa o modo `--summary-csv` utilizando exclusivamente pacotes nativos do R base (`stats::kruskal.test`, fórmula analítica exata de Dunn com correção de empates e `stats::p.adjust(..., method = "holm")`).
    - No *smoke test* CLI 2D executado nesta auditoria (`N=3` runs $\times$ 6 algoritmos $\times$ 5 indicadores ativos `HV`, `HV_MC`, `IGD`, `IGD+`, `R2`), `r_validation_status.txt` registrou:
      - `status=R_CROSS_VALIDATION_PASSED`, `validated_cells=5`, `validated_pairs=75`, `max_abs_diff_H=4.323652547100e-12`, `max_abs_diff_p_kw=2.553512956638e-14`, `max_abs_diff_z=4.940492459582e-13`, `max_abs_diff_p_holm=2.067822493054e-06`, `max_abs_diff_a12=4.440892098501e-14`.
  - **Coleta geracional passiva de $R^2$ (`-Dstudy.enableR2Observer=false` por padrão):**
    - Verificado em [IsolatedJobExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java#L298-L398), [EvaluationBudgetProblem.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java#L383-L450) e [FDEA.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEA.java#L118-L132) que, quando ativado (`-Dstudy.enableR2Observer=true`), o observador coleta a trajetória geracional nos 6 algoritmos canônicos com **zero avaliações extras** da função objetivo (`evaluationCount == B_target`).

---

#### **Tarefa `T35` — Normalização Teórica, Paridade Exata de Hipervolume (`PISA`/`WFG`/`MC`), Suspensão Temporária de `NHV` e Fallback Único Configurável**
- **Especificação:** [task35.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task35.md) e Decisão `A3` do operador humano.
- **Status:** **APROVADA**
- **Evidências no Código e Testes:**
  - **Suspensão temporária de `NHV` no perfil ativo:** Em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L671-L680), o pipeline ativo remove qualquer arquivo residual `NHV` da célula reavaliada e não exporta linhas `NHV` em `QualityIndicatorSummary.csv`, preservando intactas as classes `NormalizedHypervolume`, `ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1` e os testes unitários históricos (`HypervolumeAuditTest`, `NormalizationAndHvContractTest`, `QualityIndicatorUnitTest`).
  - **Paridade matemática da caixa $[\mathbf{l}, \tilde{\mathbf{r}}]$ entre `PISA`, `WFG` e `MonteCarloHypervolume`:**
    - Em [ExactHypervolumeWorker.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ExactHypervolumeWorker.java#L85-L185), como `PISAHypervolume.compute()` do jMetal 7.8 ignora o `referencePoint` passado ao construtor e trunca internamente em `1.0`, o worker aplica o reescalonamento afim exato $[\mathbf{l}, \tilde{\mathbf{r}}] \mapsto [0, 1)^m$ e multiplica o resultado pelo volume da caixa $\prod_{j=1}^m (\tilde{r}_j - l_j)$.
    - O teste `testExactParityBetweenPisaWfgAndMonteCarloIn2DAnd15D` em [StudyIndicatorPipelineIntegrationTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/study/StudyIndicatorPipelineIntegrationTest.java#L48-L116) comprova concordância numérica de $10^{-12}$ entre `PISA`, `WFG` e a fórmula analítica tanto em $m=2$ quanto em $m=15$.
  - **Política de fallback único generalizado ligar/desligar (`-Dstudy.hvFallbackToMcOnAllExactTimeout`):**
    - Implementada em [ProcessIsolatedHypervolumeExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java#L540-L695) (`evaluateConfiguredHypervolumes`):
      1. Quando `MC` já está em `-Dstudy.hvMethods` (padrão `PISA,MC`), `MonteCarloHypervolume` roda 1 única vez (`mc_computed_count=1`) e, se o exato atingir timeout, o valor de `HV_MC` é reutilizado sem recomputação (`REUSED_MC_FALLBACK`);
      2. Quando apenas hipervolumes pesados (`PISA`, `WFG` ou `PISA,WFG`, sem `MC`) são configurados e **todos** atingem timeout/falha, se `-Dstudy.hvFallbackToMcOnAllExactTimeout=true` (padrão), o fallback `MonteCarloHypervolume` é disparado **exatamente 1 vez** (`FALLBACK_MC_SINGLE_SHOT`), gravando `HV_FALLBACK_MC` e gerando aviso explícito em `hv_fallback_warnings.md` enquanto `HV` e `HV_WFG` registram `NA` (jamais `0.0`); se `false`, nenhum fallback é executado (`NONE_FALLBACK_DISABLED`).

---

#### **Tarefa `T34` — Resolução Canônica por Validade, Geradores Analíticos `WFG1`–`WFG9`, Fallback Oficial Opt-in e Manifesto Efetivo**
- **Especificação:** [task34.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task34.md) e instrução complementar do operador humano.
- **Status:** **APROVADA**
- **Evidências no Código e Testes:**
  - **Geradores analíticos para `WFG1`–`WFG9` na Prioridade 3:** Implementados em [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1400-L1618) (`generateAnalyticalWfgFront` e `generateAndSaveAnalyticalWfgFront`) e acoplados em `resolveCanonicalReferenceFront` (linhas 1865–1869), respeitando as geometrias distintas de Huband et al. (2006) (`WFG1` convexa + mista, `WFG2` convexa + desconexa filtrada por não-dominância estrita, `WFG3` reta degenerada 1D e `WFG4`–`WFG9` hiper-elipsoide côncavo $\sum (f_j / (2j))^2 = 1$).
  - **Fallback opcional de restauração de arquivos oficiais do jMetal 7.8 (`-Dstudy.restoreOfficialJMetalResources=false`):**
    - Implementado como alternativa **desativada (`false`) por padrão** em [ReferenceSetValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java#L54-L74) (`tryRestoreOfficialJMetalWeightFile`, linhas 2460–2517) e [ParetoFrontValidator.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1844-L1851) (`tryRestoreOfficialJMetalReferenceFront`, linhas 1951–1975).
    - Quando habilitado (`true`) e um arquivo oficial de `resources/weightVectorFiles/` ou `resources/referenceFronts/` estiver ausente ou corrompido, restaura-o atomicamente a partir do subdiretório de backup oficial local `moead/` (para pesos, validando o SHA-256 contra `docs/moead-weights-manifest.csv`) ou diretamente da tag oficial imutável `jmetal-7.8` no GitHub (`https://raw.githubusercontent.com/jMetal/jMetal/jmetal-7.8/resources/...`), validando o conteúdo matemático antes da gravação e **bloqueando com `IllegalArgumentException` qualquer tentativa de uso da cópia externa local modificada `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal` ou das pastas legadas protegidas**.
  - **Teste automatizado hermético:** `testOptionalRestoreOfficialJMetalResourcesFallback` em [CanonicalArtifactResolutionAndManifestTest.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/util/CanonicalArtifactResolutionAndManifestTest.java#L555-L658) aprovado (`11/11` testes verdes na classe).

---

#### **Tarefa `T33` (incorporando `T32` do relatório) — Orçamento Equitativo $B_{\text{target}}$, Isolamento de RNG/Processo, Preservação entre Lotes e Gerações de `DTLZ5`/`DTLZ6`**
- **Especificação:** [task33.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task33.md) e seção `T32` de [relatorio_auditoria-pre_execucao_final.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/relatorio_auditoria-pre_execucao_final.md#L84-L101).
- **Status:** **APROVADA**
- **Verificação Empírica dos 3 Requisitos de `T32` + Orçamento (`T33`):**
  1. **Preservação sem sobrescrita entre lotes (`-DrunStart`, `-Druns`, `-Dobjectives`):**
     - No *smoke test* CLI 2D desta auditoria (`task-1165`), o Lote 1 executou `runStart=0, runs=2` (gerando `FUN0.csv` e `FUN1.csv`, com `SHA-256(FUN0.csv) = 7022EFBA8463BF13C7DA76D6312C7667B4A47EF4A0A64BA96F95201F7B80E554`) e o Lote 2 executou `runStart=2, runs=1` sobre o mesmo diretório.
     - **Resultado:** `FUN0_PRESERVED=True`, `FUN1_PRESERVED=True` (hashes SHA-256 idênticos antes e depois do Lote 2), `FUN2.csv` adicionado e `QualityIndicatorSummary.csv` + `hv_audit_manifest.csv` consolidados com todas as 3 runs ($r \in \{0, 1, 2\}$) via merge não-destrutivo (`readSummaryRowsByKey` / `readHvAuditRowsByKey` em [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L468-L472)).
     - Caso existam arquivos `FUN`/`VAR` órfãos em disco sem registro válido em `run_manifest.csv` ou com hash divergente, `IsolatedJobExecutor` lança `CorruptedRunArtifactException`, recusando sobrescrita ou retomada cega.
  2. **Independência estocástica real entre execuções ($r \ne r'$):**
     - Confirmado no *smoke test* (`FUN0_NEQ_FUN1=True`, `FUN1_NEQ_FUN2=True`) e em `IsolationAndReproducibilityTest` (`15/15` pass): cada job instancia problema e operadores do zero e aplica sua semente de 64 bits derivada por `SHA-256` UTF-8 + `SplitMix64` sob trava de `JMetalRandom` (ou em JVM filha isolada com `-Dstudy.processIsolation=true`), eliminando a condição de corrida do `ExecuteAlgorithms` paralelo do jMetal.
  3. **Reprodutibilidade bit-a-bit individual ou em lote:**
     - Confirmado em `StudyBudgetIsolationAndResumeIntegrationTest` (`6/6` pass): a execução de uma única run isolada (`-DrunStart=k -Druns=1`) produz exatamente o mesmo hash SHA-256 de `FUN<k>.csv` e `VAR<k>.csv` que a execução do lote completo, independentemente da ordem dos jobs ou de `-Dstudy.parallelWorkers`.
  4. **Orçamento equitativo $B_{\text{target}} = N_{\text{config}}(m) \times G_{\max}(m, \text{problema})$:**
     - Confirmado em [EvaluationBudgetProblem.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java) (`BudgetCompliantMOEADD` incluso): todos os 6 algoritmos param exatamente em $B_{\text{target}}$ avaliações reais, integrando no último lote apenas soluções efetivamente avaliadas e proibindo objetivos copiados/fictícios.

---

#### **Tarefa `T32` — Reconciliação de Baseline, `StudyOutputLayout` para Subdiretórios Aninhados e `MoeaddWeightValidationTest`**
- **Especificação:** [task32.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/tasks/task32.md).
- **Status:** **APROVADA**
- **Evidências no Código e Testes:**
  - `WFGStudy.resolveStudyOutputLayout` ([WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L396-L436)) decompõe qualquer caminho aninhado (como `WFGStudy/tests/zerotrust_audit_smoke_2d`) em `parentBaseDirectory` (`.../WFGStudy/tests`) e `experimentName` folha sem barras (`zerotrust_audit_smoke_2d`), permitindo que `GenerateLatexTablesWithStatistics`, `GenerateWilcoxonTestTablesWithR`, `GenerateFriedmanTestTables` e `GenerateBoxplotsWithR` gravem normalmente `latex/zerotrust_audit_smoke_2d.tex` (`LATEX_TABLE_EXISTS=True`).

---

### 3.1. Contextualização Histórica das Fases 4, 3, 2 e 1 (`T31` $\to$ `T01`) sob Evolução Arquitetural Legítima

Conforme a Diretriz 2 do operador humano (*"Tarefas passadas que falhem devido a refatorações posteriores legítimas ou evolução do código devem ser contextualizadas na análise, não impedindo a aprovação se o estado atual do pipeline estiver correto e operacional"*), registram-se abaixo as evoluções arquiteturais legítimas verificadas entre as tarefas históricas `T01`–`T31` e o estado operacional atual:

1. **Exclusão deliberada de `ArtifactResolver` (`T18` $\to$ `T31`):**
   - Criado na `T18` (Fase 3), o `ArtifactResolver` foi formalmente descontinuado e removido na `T31` (Fase 4), com suas responsabilidades migradas para `ParetoFrontValidator`, `ReferenceSetValidator`, `ResolvedFrontArtifact`, `ResolvedWeightArtifact` e `ArtifactManifestEntry`. Todos os 394 testes atuais compilam e passam sem `ArtifactResolver`.
2. **Preservação do manifesto histórico `docs/auditoria/artifact-manifest.csv` (`T18` / `T30` $\to$ `T34`):**
   - O arquivo `docs/auditoria/artifact-manifest.csv` permanece preservado em disco como snapshot forense imutável da Fase 3 (`T18`), em respeito à regra de não sobrescrever artefatos históricos de auditoria (`AGENTS.md` e `T34` Atividade 17), enquanto o manifesto ativo de cada execução do estudo é exportado diretamente em `<outputDirectory>/artifact-manifest.csv` (`ARTIFACT_MANIFEST_EXISTS=True`).
3. **Suspensão temporária de `NHV` no perfil ativo do pipeline (`T05` / `T08` / `T17` $\to$ `T35` Decisão `A3`):**
   - Nas Fases 1 a 3, `NormalizedHypervolume` (`NHV`) era exportado ao lado de `HV`. Na Fase 5 (Decisão `A3` do operador), `NHV` foi temporariamente suspenso no perfil ativo de campanha (que exporta `HV` exato isolado, `HV_MC` aproximado uniforme, `IGD`, `IGD+` e `R2`), enquanto a classe `NormalizedHypervolume`, a derivação $O(1)$ em `ObjectiveNormalizationContract.deriveNormalizedHypervolumeO1` e todos os testes unitários (`QualityIndicatorUnitTest`, `HypervolumeAuditTest`, `NormalizationAndHvContractTest`) permanecem 100% preservados e aprovados.
4. **Imutabilidade de `MOEAD_Weights/` e uso exclusivo de `resources/weightVectorFiles/` (`T03` / `T11` $\to$ `T29` / `T32`):**
   - `MOEAD_Weights/` (`43` arquivos), `pareto_fronts/` (`484` arquivos, sendo `160` em `pareto_fronts/csv/`) e `pareto/` (`125` arquivos) permanecem 100% intocados (`git status` limpo) e desacoplados do runtime, que consome exclusivamente `resources/weightVectorFiles/` e `resources/referenceFronts/`.

---

## 4. Recomendações de Atualização Documental (Para Aplicação Posterior pelo Coordenador)

Em cumprimento estrito à Diretriz 1 (*"NENHUM outro arquivo do projeto (como código-fonte, README, HANDLER ou scripts) deve ser modificado diretamente durante esta etapa de auditoria"*) e à Diretriz 4, apresentam-se abaixo os blocos exatos recomendados para adição posterior em [README.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/README.md), [HANDLER.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/HANDLER.md) e [docs/pipeline-pos_processamento.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/pipeline-pos_processamento.md).

### 4.1. Proposta de Adição ao Final de [README.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/README.md) (Sem Alterar o Texto Preexistente)

Recomenda-se acrescentar ao final de `README.md` (ou como nova subseção de Fase Extra após o histórico existente):

```markdown
## Alterações 18/11/2026 - 10:40 (Fase Extra — Reauditoria Independente Zero-Trust Pós-Fase 5)

1. **Reauditoria Independente Zero-Trust em Ordem Cronológica Reversa (`T37` $\to$ `T01`)**:
   - Executada auditoria independente completa validando empiricamente todas as tarefas (`T37` a `T01`, incluindo `T32` e `T33`) diretamente contra o código-fonte Java 21, bytecode do jMetal 7.8, manifestos SHA-256 e execução real via Maven CLI, consolidada em [`docs/relatorio_auditoria_zerotrust_pos_fase5.md`](docs/relatorio_auditoria_zerotrust_pos_fase5.md).
   - **Suíte Integral de Testes (`mvn clean test`)**: Confirmada a aprovação de **394 testes automatizados em 27 classes (`0` falhas, `0` erros, `0` ignorados)** sem exclusões nem *greenwashing*.
   - **Validação Empírica via CLI (`mvn exec:java`) em `WFGStudy/tests/`**:
     - Confirmada a preservação bit-a-bit de `FUN0.csv` e `FUN1.csv` (`SHA-256` inalterado) entre lotes sequenciais (`runStart=0, runs=2` com `-Dstudy.processIsolation=true` $\to$ `runStart=2, runs=1`), a geração de tabelas LaTeX em subdiretórios aninhados sem `FileNotFoundException` e a validação cruzada automática contra `R version 4.6.2` em R base nativo (`R_CROSS_VALIDATION_PASSED`, `75` pares validados com diferença máxima de $H = 4{,}32 \times 10^{-12}$).
     - Confirmada a execução segura em alta dimensão (`WFG4`, $m=15$) sob *deadline* de hipervolume (`-Dperf.hvTimeoutMs`), com paridade entre `PISAHypervolume` isolado em processo (`ExactHypervolumeWorker`) e `MonteCarloHypervolume` (`HV_MC`), e limpeza integral dos diretórios temporários de teste.
   - **Veredito da Auditoria**: Projeto homologado como **100% PRONTO PARA A EXECUÇÃO EXPERIMENTAL** dos 6 MOEAs canônicos isolados.
```

---

### 4.2. Proposta de Adição ao Final de [HANDLER.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/HANDLER.md) (Como Nova Fase Extra ao Final do Arquivo)

Recomenda-se acrescentar estritamente ao final de `HANDLER.md`, preservando todo o conteúdo anterior intacto:

```markdown
## Fase Extra — Reauditoria Independente Zero-Trust Pós-Correções (`T37` -> `T01`)

### Objetivo
Auditar de forma independente (estilo *Zero-Trust*) todas as tarefas do projeto em ordem cronológica reversa (`T37` $\to$ `T01`, incluindo `T32` e `T33` do relatório), verificando empiricamente se todos os bloqueios e desconexões apontados em `docs/relatorio_auditoria-pre_execucao_final.md` foram eliminados e atestando a prontidão definitiva para a campanha experimental completa.

### Estado e Evidências Verificadas (18/11/2026)
- **Relatório produzido:** `docs/relatorio_auditoria_zerotrust_pos_fase5.md` (único arquivo gerado durante a auditoria, sem modificação de código ou outros documentos).
- **Suíte integral (`mvn clean test`):** `394` testes executados em `27` classes, `0` falhas, `0` erros, `0` ignorados (`BUILD SUCCESS`).
- **Smokes reais via CLI (`mvn exec:java`) em `WFGStudy/tests/` (removidos após verificação):**
  - `WFG4` ($m=2$) em 2 lotes (`runStart=0, runs=2` com `-Dstudy.processIsolation=true` seguido de `runStart=2, runs=1`): confirmada preservação bit-a-bit de `FUN0.csv`/`FUN1.csv`, independência estocástica entre runs, geração de `latex/<experimentName>.tex` e `latex/Phase5StatisticalSummary.tex`, exportação de `artifact-manifest.csv` (`OBSERVED_CONSUMED`) e `R_CROSS_VALIDATION_PASSED` contra `R 4.6.2` base.
  - `WFG4` ($m=15$, `-Dperf.hvTimeoutMs=15000`): confirmada execução isolada de `ExactHypervolumeWorker` (`pisa_status=SUCCESS_EXACT`) e `MonteCarloHypervolume` (`DIRECT_MC`) sob `THEORETICAL_PARETO_BOUNDS`, sem `NHV` e sem processos órfãos.
- **Integridade de diretórios protegidos:** `0` alterações em `MOEAD_Weights/` (`43` arquivos), `pareto_fronts/` (`484` arquivos), `pareto/` (`125` arquivos) e `WFGStudy/` histórico; `0` referências à cópia local externa proibida do jMetal.
- **Veredito:** **APROVADO E PRONTO PARA EXECUÇÃO DA CAMPANHA EXPERIMENTAL (MEDIANTE DISPARO PELO OPERADOR HUMANO).**
```

---

### 4.3. Proposta de Ajustes Documentais em [docs/pipeline-pos_processamento.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/pipeline-pos_processamento.md)

A inspeção cruzada entre [docs/pipeline-pos_processamento.md](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/pipeline-pos_processamento.md) e o código real de [WFGStudy.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java), [StudyStatisticalAnalyzer.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/statistics/StudyStatisticalAnalyzer.java), [PerformanceProfiler.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java) e [IsolatedJobExecutor.java](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) mostrou que o documento já reflete a arquitetura da Fase 5, mas possui **4 pequenas divergências de nomes de arquivos e 1 nova flag** que devem ser alinhadas posteriormente:

1. **Nomes exatos dos CSVs estatísticos gerados por `StudyStatisticalAnalyzer.java` (linhas 147–148 de `StudyStatisticalAnalyzer.java` vs. Seção 5, linhas 79 e 99–100 de `pipeline-pos_processamento.md`):**
   - O documento cita `statistics/kruskal_cells.csv` e `statistics/dunn_holm_pairs.csv`, mas o código real grava:
     - `statistics/kruskal_wallis_cells.csv`
     - `statistics/dunn_holm_posthoc_pairs.csv`
   - Recomenda-se atualizar as linhas 79, 99 e 100 de `docs/pipeline-pos_processamento.md` para usar `kruskal_wallis_cells.csv` e `dunn_holm_posthoc_pairs.csv`.
2. **Nome exato do CSV de telemetria de execução (linha 27 de `PerformanceProfiler.exportStudyExecutionProfileCsv` vs. Seção 1, linha 25 de `pipeline-pos_processamento.md`):**
   - O fluxograma na linha 25 cita `job_execution_profile.csv`, enquanto o método real `PerformanceProfiler.exportStudyExecutionProfileCsv` grava `logs/execution_profile_runs.csv` (ou `execution_profile_runs.csv` conforme o diretório configurado).
3. **Nome exato do CSV de trajetória geracional do `R2ExecutionObserver` (linha 390 de `IsolatedJobExecutor.java` vs. Seção 3, linha 58 de `pipeline-pos_processamento.md`):**
   - O documento cita `data/<ALG>/<PROB>/R2_trace_run<r>.csv`, enquanto `IsolatedJobExecutor` grava `data/<ALG>/<PROB>/r2_trajectory_run<r>.csv`.
4. **Inclusão da nova flag opcional `-Dstudy.restoreOfficialJMetalResources=false` na Seção 2 (Etapa 1 — Resolução Canônica):**
   - Documentar na Seção 2 de `docs/pipeline-pos_processamento.md` que, quando `-Dstudy.restoreOfficialJMetalResources=true` (desativada por padrão), a Prioridade 1 (`OFFICIAL`) tenta restaurar atomicamente arquivos oficiais ausentes/corrompidos a partir do espelho oficial `moead/` ou da tag oficial `jmetal-7.8` no GitHub (`jMetal/jMetal`) antes de avançar para `pf_gen/` / `weights_gen/`.

---

## 5. Veredito e Prontidão para Execução Definitiva

### Veredito: **PROJETO 100% PRONTO E HOMOLOGADO PARA A EXECUÇÃO EXPERIMENTAL**

Não resta **nenhum bloqueio técnico, falha de compilação/teste, risco de corrupção de dados ou pendência metodológica** que impeça o início da campanha experimental definitiva dos 6 MOEAs isolados (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA` e `AGE-MOEA-II`).

---

### Comandos Homologados para Disparar a Campanha Experimental Desassistida (comandos desatualizados pós fase 5)

Você pode disparar a campanha experimental de duas formas equivalentes (ambas preservam `FUN<r>.csv`/`VAR<r>.csv` verificados por SHA-256 em `run_manifest.csv` e mesclam atomicamente `QualityIndicatorSummary.csv`, `hv_audit_manifest.csv` e as análises estatísticas em `statistics/` e `latex/`):

#### Opção 1: Execução por Lotes de Dimensionalidade ($m$) — **Recomendada para Controle Operacional e Checkpointing**

Como o código atual faz **merge atômico não-destrutivo** de `QualityIndicatorSummary.csv` e `hv_audit_manifest.csv` entre invocações com diferentes `-Dobjectives` ou `-DrunStart`/`-Druns`, você pode rodar cada lote de dimensões separadamente apontando para o mesmo diretório de saída:

* **No Windows `CMD` (`cmd.exe` — continuação de linha com `^`)**:
```bash
REM 1. Compilação limpa inicial
mvn clean test-compile

REM 2. Lote A — Baixa e Média Dimensionalidade: m = 2, 3, 5 (WFG1..9 + DTLZ1..7 completos, 30 runs)
set MAVEN_OPTS=-Xms4g -Xmx10g
mvn exec:java ^
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" ^
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" ^
  "-DrunStart=0" "-Druns=30" ^
  "-Dobjectives=2,3,5" ^
  "-DgenerateAllReferenceFronts=true" ^
  "-Dstudy.parallelWorkers=4" ^
  "-Dstudy.processIsolation=true" ^
  "-Dperf.hvTimeoutMs=3600000" ^
  "-Dstudy.hvMethods=PISA,MC" ^
  "-Dstudy.hvMcSamples=1000000" ^
  "-DreplaceZeroWeights=false" ^
  "-DreplaceZeroFronts=false"

REM 3. Lote B — Dimensão m = 8 (WFG1..9 + DTLZ1..7 completos, 30 runs)
set MAVEN_OPTS=-Xms4g -Xmx12g
mvn exec:java ^
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" ^
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" ^
  "-DrunStart=0" "-Druns=30" ^
  "-Dobjectives=8" ^
  "-DgenerateAllReferenceFronts=true" ^
  "-Dstudy.parallelWorkers=4" ^
  "-Dstudy.processIsolation=true" ^
  "-Dperf.hvTimeoutMs=3600000" ^
  "-Dstudy.hvMethods=PISA,MC" ^
  "-Dstudy.hvMcSamples=1000000" ^
  "-DreplaceZeroWeights=false" ^
  "-DreplaceZeroFronts=false"

REM 4. Lote C1 — Alta Dimensionalidade: m = 10 (WFG1..9 + DTLZ1..7 completos, 30 runs, HV protegido por timeout de 60 min + HV_MC)
set MAVEN_OPTS=-Xms6g -Xmx14g
mvn exec:java ^
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" ^
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" ^
  "-DrunStart=0" "-Druns=30" ^
  "-Dobjectives=10" ^
  "-DgenerateAllReferenceFronts=true" ^
  "-Dstudy.parallelWorkers=4" ^
  "-Dstudy.processIsolation=true" ^
  "-Dperf.hvTimeoutMs=3600000" ^
  "-Dstudy.hvMethods=PISA,MC" ^
  "-Dstudy.hvMcSamples=1000000" ^
  "-DreplaceZeroWeights=false" ^
  "-DreplaceZeroFronts=false"

REM 5. Lote C2 — Alta Dimensionalidade: m = 15 (WFG1..9 + DTLZ1..7 completos, 30 runs, HV protegido por timeout de 60 min + HV_MC)
set MAVEN_OPTS=-Xms6g -Xmx14g
mvn exec:java ^
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" ^
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" ^
  "-DrunStart=0" "-Druns=30" ^
  "-Dobjectives=15" ^
  "-DgenerateAllReferenceFronts=true" ^
  "-Dstudy.parallelWorkers=4" ^
  "-Dstudy.processIsolation=true" ^
  "-Dperf.hvTimeoutMs=3600000" ^
  "-Dstudy.hvMethods=PISA,MC" ^
  "-Dstudy.hvMcSamples=1000000" ^
  "-DreplaceZeroWeights=false" ^
  "-DreplaceZeroFronts=false"
```

* **No `Linux Bash` ou `Git Bash` (Windows — continuação de linha com `\`)**:
```bash
# 1. Compilação limpa inicial
mvn clean test-compile

# 2. Lote A — Baixa e Média Dimensionalidade: m = 2, 3, 5 (WFG1..9 + DTLZ1..7 completos, 30 runs)
export MAVEN_OPTS="-Xms4g -Xmx10g"
mvn exec:java \
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" \
  "-DrunStart=0" "-Druns=30" \
  "-Dobjectives=2,3,5" \
  "-DgenerateAllReferenceFronts=true" \
  "-Dstudy.parallelWorkers=4" \
  "-Dstudy.processIsolation=true" \
  "-Dperf.hvTimeoutMs=3600000" \
  "-Dstudy.hvMethods=PISA,MC" \
  "-Dstudy.hvMcSamples=1000000" \
  "-DreplaceZeroWeights=false" \
  "-DreplaceZeroFronts=false"

# 3. Lote B — Dimensão m = 8 (WFG1..9 + DTLZ1..7 completos, 30 runs)
export MAVEN_OPTS="-Xms4g -Xmx12g"
mvn exec:java \
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" \
  "-DrunStart=0" "-Druns=30" \
  "-Dobjectives=8" \
  "-DgenerateAllReferenceFronts=true" \
  "-Dstudy.parallelWorkers=4" \
  "-Dstudy.processIsolation=true" \
  "-Dperf.hvTimeoutMs=3600000" \
  "-Dstudy.hvMethods=PISA,MC" \
  "-Dstudy.hvMcSamples=1000000" \
  "-DreplaceZeroWeights=false" \
  "-DreplaceZeroFronts=false"

# 4. Lote C1 — Alta Dimensionalidade: m = 10 (WFG1..9 + DTLZ1..7 completos, 30 runs, HV protegido por timeout de 60 min + HV_MC)
export MAVEN_OPTS="-Xms6g -Xmx14g"
mvn exec:java \
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" \
  "-DrunStart=0" "-Druns=30" \
  "-Dobjectives=10" \
  "-DgenerateAllReferenceFronts=true" \
  "-Dstudy.parallelWorkers=4" \
  "-Dstudy.processIsolation=true" \
  "-Dperf.hvTimeoutMs=3600000" \
  "-Dstudy.hvMethods=PISA,MC" \
  "-Dstudy.hvMcSamples=1000000" \
  "-DreplaceZeroWeights=false" \
  "-DreplaceZeroFronts=false"

# 5. Lote C2 — Alta Dimensionalidade: m = 15 (WFG1..9 + DTLZ1..7 completos, 30 runs, HV protegido por timeout de 60 min + HV_MC)
export MAVEN_OPTS="-Xms6g -Xmx14g"
mvn exec:java \
  "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" \
  "-DrunStart=0" "-Druns=30" \
  "-Dobjectives=15" \
  "-DgenerateAllReferenceFronts=true" \
  "-Dstudy.parallelWorkers=4" \
  "-Dstudy.processIsolation=true" \
  "-Dperf.hvTimeoutMs=3600000" \
  "-Dstudy.hvMethods=PISA,MC" \
  "-Dstudy.hvMcSamples=1000000" \
  "-DreplaceZeroWeights=false" \
  "-DreplaceZeroFronts=false"
```

#### Opção 2: Comando Único Desassistido (Todas as Dimensões $m \in \{2, 3, 5, 8, 10, 15\}$ de Uma Só Vez)

Caso deseje deixar a campanha inteira rodando em comando único (se interrompida a qualquer momento, basta reexecutar o exato mesmo comando e o `IsolatedJobExecutor` retomará automaticamente a partir da primeira run ainda não concluída):

* **No Windows `CMD` (`cmd.exe`)**:
```bash
set MAVEN_OPTS=-Xms6g -Xmx14g && mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=4" "-Dstudy.processIsolation=true" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethods=PISA,MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
```

* **No `Linux Bash` ou `Git Bash` (Windows)**:
```bash
MAVEN_OPTS="-Xms6g -Xmx14g" mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=4" "-Dstudy.processIsolation=true" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethods=PISA,MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
```

#### Opção 3: Alternativa em 1 Linha com Roteamento por Dimensão (`-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC`)

Caso deseje executar todas as dimensões em um único comando preservando `-Dperf.hvTimeoutMs=3600000` e evitando a explosão combinatória do `PISAHypervolume` em $m \ge 8$ (mantendo `HV` exato em `2D–5D` e `HV_MC`/`IGD`/`IGD+`/`R2` completos em `2D–15D`, conforme detalhado em [guia-execucao-otimizacao-recursos.md](guia-execucao-otimizacao-recursos.md)):

* **No Windows `CMD` (`cmd.exe` — 1 linha)**:
```cmd
set "MAVEN_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC" && mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.processIsolation=true" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
```

* **No `Linux Bash` ou `Git Bash` (Windows — 1 linha)**:
```bash
MAVEN_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC" mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase5" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.processIsolation=true" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
```

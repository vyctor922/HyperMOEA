# Plano de Mitigação da Dívida Técnica Baseado em Evidências

**Status:** FINALIZADO (PÓS-T31 / REQUISITOS ATENDIDOS)  
**Data:** 11 de Novembro de 2026  
**Projeto:** hypeMOEA  
**Responsável:** Coordenador & Agentes de Refatoração

---

## 1. Visão Geral e Princípios

Este documento categoriza todas as dívidas técnicas identificadas durante a auditoria forense do projeto hypeMOEA, estabelecendo para cada item a evidência concreta, o risco associado, a prioridade, a tarefa de execução e o critério objetivo de encerramento.

---

## 2. Matriz de Dívidas Técnicas Mapeadas

| ID | Descrição da Dívida | Evidência no Código | Prioridade | Impacto Técnico / Científico | Tarefa | Critério de Encerramento | Estado Final |
|---|---|---|---|---|---|---|---|
| **DT-01** | Fallback operacional para pastas legadas protegidas (`MOEAD_Weights/` e `pareto_fronts/csv/`) | `ParetoFrontValidator.java`, `WFGStudy.java`, `PerformanceProfiler.java`, `EvaluationBudgetProblem.java` | **P0** | **Crítico**: Viola a diretriz de reprodutibilidade e a proibição de uso de diretórios legados como insumo experimental. | T29 | Ausência total de referências a esses diretórios em métodos de leitura ativa e preflight; testes automatizados demonstrando resolução canônica. | **CONCLUÍDO (Resolvido na T29/T30)** |
| **DT-02** | Ausência de arquivos de pesos para $m=5$ ($N=210$) e $m=15$ ($N=135$) no `resources/weightVectorFiles/` oficial | Diretório oficial omite essas cardinalidades | **P0** | **Crítico**: MOEA/DD aborta ou recorre a pastas legadas se não houver geração canônica validada disponível. | T29 | Geração canônica zero-replaced implementada e persistida em `resources/weightVectorFiles/weights_gen/`, coberta por testes de equivalência exata. | **CONCLUÍDO (Resolvido na T29)** |
| **DT-03** | Múltiplas implementações paralelas de resolução de frentes de Pareto | 4 métodos concorrentes (`ParetoFrontValidator`, `WFGStudy`, `PerformanceProfiler`, `ArtifactResolver`) | **P0** | **Alto**: Risco de divergência em preflight, profiling e runtime ao carregar frentes. | T29, T30 | Unificação em `ParetoFrontValidator.resolveCanonicalReferenceFront`, com delegação estrita dos demais consumidores. | **CONCLUÍDO (Resolvido na T29/T30)** |
| **DT-04** | Retorno em texto puro (`String`/`Path`) sem metadados de proveniência | Métodos retornavam caminhos soltos | **P0** | **Médio**: Fragilidade de rastreabilidade e falta de hash no momento da resolução. | T29 | Introdução dos records `ResolvedFrontArtifact` e `ResolvedWeightArtifact`. | **CONCLUÍDO (Resolvido na T29)** |
| **DT-05** | Classe `ArtifactResolver.java` isolada e órfã do fluxo principal | `ArtifactResolver.java` não era chamada pelo pacote de produção | **P0** | **Médio**: Acúmulo de código morto e manutenibilidade comprometida. | T30, T31 | Absorção das funções úteis em `ReferenceSetValidator` e `ParetoFrontValidator`; exclusão definitiva de `ArtifactResolver.java` e `ArtifactResolverTest.java`. | **CONCLUÍDO (Resolvido na T31)** |
| **DT-06** | Duplicação de verificação de não-dominância de Pareto | `ParetoFrontValidator.countDominatedPoints` vs `ResultFrontContract.assertNonDominatedFront` | **P1** | **Médio**: Risco de divergências em critérios de igualdade e tolerância numérica de dominância. | T28 | Método canônico `dominanceComparison` compartilhado entre as classes. | **CONCLUÍDO (Resolvido na T28)** |
| **DT-07** | Acoplamento de testes ao diretório de trabalho global (`user.dir`) | Testes e métodos chamavam caminhos relativos ao diretório corrente | **P1** | **Médio**: Fragilidade na execução concorrente de testes e empacotamento. | T29 | Sobrecarga com injeção de `Path baseDir` em todos os métodos canônicos de resolução. | **CONCLUÍDO (Resolvido na T29)** |

---

## 3. Plano de Ação por Etapas e Rastreabilidade

### Etapa A1 (Atual): Consolidação e Aprovação da Arquitetura
- Submissão de `docs/resolucao_duplicatas_arquitetura.md` e deste plano ao operador humano.
- Nenhuma alteração em código de produção até aprovação explícita.

### Etapa A2: Resolução Canônica e Migração (T28, T29, T30)
- **T28:** Centralizar comparação de dominância pura em `ParetoFrontValidator` e delegação em `ResultFrontContract`.
- **T29:** Implementar a resolução canônica estrita (Oficial $\to$ Gerado $\to$ Geração sob demanda) com retornos estruturados (`ResolvedFrontArtifact`, `ResolvedWeightArtifact`), injeção de `baseDir` e geração validada de pesos para $m=5, 15$ em `weights_gen/`. Eliminar fallbacks legados.
- **T30:** Migrar consumidores (`WFGStudy`, `PerformanceProfiler`, `EvaluationBudgetProblem`) e transferir a exportação de manifesto consolidado para `ReferenceSetValidator`.

### Etapa A3: Exclusão Condicionada e Validação Final (T31)
- Excluir `ArtifactResolver.java` e `ArtifactResolverTest.java` somente após comprovação de cobertura por testes substitutos.
- Executar suíte completa (`mvn clean test`) e testes de fumaça.
- Atualizar este plano de dívida com os resultados finais e o risco residual.

---

## 4. Riscos Residuais e Limitações
1. **Campanha Experimental:** Esta refatoração prepara o arcabouço de artefatos, mas **NÃO** autoriza o início da campanha experimental completa de 30 runs.
2. **Arquivos Físicos Legados:** Os diretórios `MOEAD_Weights/` e `pareto_fronts/csv/` permanecem fisicamente intactos no repositório (protegidos contra exclusão ou alteração acidental), sendo apenas desvinculados do runtime ativo.

---

## 5. Dívida Técnica Opcional Identificada Pós-Fase 5 — Parametrização com `DoubleSolution` (`DT-08`, Warnings `rawtypes` / `unchecked`)

**Data do registro:** 20/11/2026  
**Estado:** **DOCUMENTADO / OPCIONAL — NÃO APLICADO AO CÓDIGO**  
**Prioridade:** **P2 (Higiene de Código / Segurança de Tipos em Tempo de Compilação)**

### 5.1 Diagnóstico e Origem dos Avisos
Uma verificação isolada com `javac --release 21 -Xlint:rawtypes,unchecked` (estado do código em 20/11/2026) identificou **103 avisos ativos** em `src/main/java` (`52` `rawtypes` + `51` `unchecked`) e **6 avisos ativos** em `src/test/java` (`1` `rawtypes` + `5` `unchecked`), sem nenhum outro erro de compilação:
- **Por que `@SuppressWarnings("rawtypes")` não remove o aviso completo na IDE:** o diagnóstico exibido pelo compilador/IDE combina duas categorias — *"References to generic type ... should be parameterized"* (`rawtypes`) e *"Type safety: The constructor/method ... belongs to the raw type ..."* (`unchecked`). Como `@SuppressWarnings("rawtypes")` oculta apenas a primeira parte, chamadas como `new GWASFGA(...)` e `new ESPEABuilder(...)` em `WFGStudy.java` continuam emitindo `[unchecked]`.
- **Causa raiz comum nos builders:** quase todo o projeto opera exclusivamente com `DoubleSolution` (`DoubleProblem` estende `Problem<DoubleSolution>`), mas os métodos `build*` de `WFGStudy.java` declaram tipos crus (`Algorithm`, `Problem`, `CrossoverOperator`, `MutationOperator`), propagando `rawtypes` e `unchecked` para os construtores, setters e chamadores.

### 5.2 Opção Recomendada: Parametrização com `DoubleSolution`
Para eliminar os avisos **sem depender de supressões amplas** e com risco mínimo:
1. Parametrizar os métodos `build*` em `WFGStudy.java` e `SimpleNSGAII.java` com `DoubleSolution` (`Problem<DoubleSolution>`, `CrossoverOperator<DoubleSolution>`, `MutationOperator<DoubleSolution>`, `Algorithm<List<DoubleSolution>>`, `SelectionOperator<List<DoubleSolution>, DoubleSolution>`, `SolutionListEvaluator<DoubleSolution>`) e usar operador diamante `<>` / `<DoubleSolution>` nas instanciações (`new NSGAIIIBuilder<>(...)`, `new ESPEABuilder<>(...)`, `new GWASFGA<>(...)`, etc.).
2. Substituir o array genérico cru `dtlzProblems` em `WFGStudy.java` por `List<Problem<DoubleSolution>>` (`List.of(...)`).
3. No pacote `fdea` (`FDEA`, `LeastSimilarBasedSampling`, `MostSimilarBasedSampling`), **preservar** os arrays `new List[...]` existentes e apenas adicionar `"rawtypes"` junto de `"unchecked"` em `@SuppressWarnings({ "rawtypes", "unchecked" })`, evitando refatorar código interno de um algoritmo oficial apenas por estética de compilação.

### 5.3 Locais Exatos a Modificar (Mapeamento de Código)

| Arquivo | Linha(s) / Símbolo(s) | Warnings Atuais (`javac`) | Alteração Prevista |
|---|---|---|---|
| `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java` | `2022–2058` (`buildNSGAIII`), `2061–2075` (`buildNSGAII`), `2077–2089` (`buildSPEA2`), `2091–2108` (`buildMOEADDRA`), `2110–2121` (`buildAGEMOEA`), `2123–2134` (`buildAGE2`), `2136–2174` (`buildRVEA`), `2176–2190` (`buildFDEA`), `2250–2273` (`buildMOEADD`), `2459–2471` (`buildGWASFGA`), `2473–2481` (`buildESPEA`) | `45 rawtypes` + `48 unchecked` | Tipar assinatura e retorno com `<DoubleSolution>` / `Algorithm<List<DoubleSolution>>` (mantendo `public static` nos 3 métodos já públicos: `buildNSGAIII`, `buildRVEA`, `buildMOEADD`), parametrizar variáveis locais (`NSGAIIIBuilder<DoubleSolution>`, `NSGAIIBuilder<DoubleSolution>`, `SPEA2Builder<DoubleSolution>`, `AGEMOEABuilder<DoubleSolution>`, `AGEMOEAIIBuilder<DoubleSolution>`, `RVEABuilder<DoubleSolution>`, `GWASFGA<DoubleSolution>`, `ESPEABuilder<DoubleSolution>`, `SelectionOperator<List<DoubleSolution>, DoubleSolution>`, `SolutionListEvaluator<DoubleSolution>`), e remover os `@SuppressWarnings("rawtypes")` das linhas `2459` e `2473`. *(Obs.: `MOEADBuilder` e `FDEABuilder` não são genéricos no projeto; seus `unchecked` somem ao tipar os parâmetros.)* |
| `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java` | `1643, 1655–1659` (`executeLegacyAlgorithmsOnly`) e `1956, 1972–1974, 2006–2007` (`configureAlgorithmList`) | Ocultados por `@SuppressWarnings({ "rawtypes", "unchecked" })` | Trocar `Problem p1..p5` por `DoubleProblem p1..p5` (ou `Problem<DoubleSolution> p1..p5`); se não restarem casts/conversões sem tipo, remover os `@SuppressWarnings` das linhas `1643` e `1956`. |
| `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java` | `1856–1876` (`addAnalyticalDtlzProblemsForManyObjectives`) | `1 rawtypes` + `1 unchecked` | Substituir `Problem<DoubleSolution>[] dtlzProblems = new Problem[] { ... };` por `List<Problem<DoubleSolution>> dtlzProblems = List.of(...);` e ajustar o laço para `dtlzProblems.size()` / `dtlzProblems.get(idx)`. |
| `src/main/java/br/usp/lti/newmoea/SimpleNSGAII.java` | `43, 47` (`main`) | `2 rawtypes` + `2 unchecked` | Alterar para `Algorithm<List<DoubleSolution>> nsgaII = new NSGAII<>(...);` e remover o cast `(List<DoubleSolution>)` em `nsgaII.result()`. |
| `src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEA.java` | `550–551` (`selectNextPopulation`) | `1 rawtypes` | Ajustar `@SuppressWarnings("unchecked")` para `@SuppressWarnings({ "rawtypes", "unchecked" })` (mantendo `new List[populationSize]` intacto). |
| `src/main/java/br/usp/lti/newmoea/algorithm/fdea/LeastSimilarBasedSampling.java` | `23, 33, 115` (`getIdealPointOrientedPopulation`) | `2 rawtypes` | Ajustar `@SuppressWarnings("unchecked")` na linha `23` para `@SuppressWarnings({ "rawtypes", "unchecked" })` (mantendo `new List[2]` intacto). |
| `src/main/java/br/usp/lti/newmoea/algorithm/fdea/MostSimilarBasedSampling.java` | `23, 140` (`getIdealPointOrientedPopulation`) | `1 rawtypes` | Ajustar `@SuppressWarnings("unchecked")` na linha `23` para `@SuppressWarnings({ "rawtypes", "unchecked" })` (mantendo `new List[2]` intacto). |
| `src/test/java/br/usp/lti/newmoea/algorithm/EvaluationBudgetAuditTest.java` | `245, 255` | `2 unchecked` | Resolvidos automaticamente após `WFGStudy.buildRVEA` retornar `Algorithm<List<DoubleSolution>>`. |
| `src/test/java/br/usp/lti/newmoea/study/ResultFrontContractTest.java` | `143, 152` | `1 rawtypes` + `2 unchecked` | Trocar `new RVEABuilder(...)` por `new RVEABuilder<>(...)` (ou `new RVEABuilder<DoubleSolution>(...)`). |
| `src/test/java/br/usp/lti/newmoea/algorithm/RveaReferenceDirectionsValidationTest.java` | `122` (e opcionalmente `104`, mais `AlgorithmIntegrationTest.java:126,198` e `NsgaIIIReferencePointsValidationTest.java:456`) | `1 unchecked` (`122`) | A linha `122` é cast via reflection (`Field.get`), não decorre de `DoubleSolution` — manter (ou isolar `@SuppressWarnings("unchecked")` apenas nessa variável). |
| `EvaluationBudgetProblem.java` e `PerformanceProfiler.java` *(opcional secundário)* | `EvaluationBudgetProblem.java:874,905,990`; `PerformanceProfiler.java:826,846,855,865` | Ocultados por `@SuppressWarnings` | Tipar `SelectionOperator<List<DoubleSolution>, DoubleSolution>`, `AGEMOEABuilder<DoubleSolution>` e `AGEMOEAIIBuilder<DoubleSolution>` se desejar eliminar também as supressões amplas nesses dois arquivos. |

### 5.4 Análise de Riscos (Científicos e Operacionais)

#### Riscos Científicos
1. **Zero impacto numérico/comportamental na parametrização pura:** devido ao *type erasure* do Java, trocar tipos crus por `<DoubleSolution>` / `<>` não altera o bytecode em runtime. Sementes (`JMetalRandom`), operadores, critérios de parada e resultados numéricos permanecem idênticos.
2. **Separação da execução principal:** na campanha oficial (`WFGStudy.run()`), os 6 algoritmos principais são montados por `EvaluationBudgetProblem.buildAuditedAlgorithm(...)`, e não pelos `WFGStudy.build*`.
3. **Preservação do FDEA:** no pacote `fdea`, manter `new List[...]` com `@SuppressWarnings({ "rawtypes", "unchecked" })` garante **zero** alteração de bytecode em um dos 6 algoritmos oficiais; já uma eventual refatoração para `List<List<FDEAIndividual>>` alteraria alocação interna sem ganho funcional.
4. **Substituição de `dtlzProblems` (`WFGStudy.java:1856`):** trocar o array por `List.of(...)` preserva exatamente a ordem `DTLZ1..DTLZ7` no laço local `1865–1876` (nenhum elemento é nulo nem reatribuído).

#### Riscos Operacionais e de Manutenção
1. **Compatibilidade interna validada:** todos os chamadores atuais em `src/main` (`PerformanceProfiler.createStandardAlgorithm`) e `src/test` já fornecem `DoubleProblem` / `Problem<DoubleSolution>`, `CrossoverOperator<DoubleSolution>` / `SBXCrossover` e `MutationOperator<DoubleSolution>` / `PolynomialMutation`.
2. **Reflection e compatibilidade binária:** não há chamadas `getDeclaredMethod` aos `build*` de `WFGStudy` no repositório e, após o apagamento de tipos, a assinatura binária em bytecode (`Problem`, `int`, `CrossoverOperator`, `MutationOperator` $\to$ `Algorithm`) permanece inalterada.
3. **Chamadores externos não versionados:** como `buildNSGAIII`, `buildMOEADD` e `buildRVEA` são `public static`, qualquer código externo ao repositório que os invoque usando variáveis cruas (`Problem p = ...`) passará a receber warning de conversão `unchecked` ao compilar contra as novas assinaturas (ou erro apenas se passar um problema incompatível que não seja de `DoubleSolution`).
4. **Concorrência no workspace durante campanhas:** não aplicar modificações de `.java` nem disparar `mvn compile`/`mvn test` no mesmo diretório enquanto alguma execução experimental estiver ativa usando `target/classes`, evitando disputas de arquivo no Windows ou divergência entre código-fonte e classes carregadas.

### 5.5 Critério de Validação Caso Seja Executada Futuramente
- Executar compilação diagnóstica com `javac --release 21 -Xlint:rawtypes,unchecked` verificando zeramento de avisos em `src/main/java` e manutenção apenas de eventuais casos explícitos de reflection em testes;
- Executar `mvn clean test` garantindo 100% de aprovação da suíte sem regressão.


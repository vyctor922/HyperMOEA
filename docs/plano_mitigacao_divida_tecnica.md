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


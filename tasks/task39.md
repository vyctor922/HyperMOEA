# T37 — Aceite fim-a-fim e reauditoria reconciliada

## Identificação

- Fase: 5.
- Prioridade: P0.
- Complexidade: alta.
- Tipo: integração, revisão e homologação.
- Estado inicial: `pending`.
- Responsável: atribuir; revisor independente quando disponível.
- Rastreabilidade: F5-01 a F5-12 do coordenador; T33 do relatório de auditoria.

## Objetivo

Homologar o perfil experimental aprovado após a integração de T32–T36, sem iniciar a campanha completa, criar outra auditoria redundante ou reimplementar funcionalidades já concluídas.

## Contexto e justificativa

Utilitários aprovados em testes unitários não demonstram, por si, que o pipeline esteja integrado. O relatório de auditoria prevê uma reauditoria reversa como T33; sua existência, numeração e escopo devem ser confirmados antes de depender dela.

A T37 é o aceite integrado da Fase 5. Ela reutiliza a T33 quando confirmada, consolida evidências de T32–T36 e decide se o perfil está apto à autorização humana para campanha.

## Diagnóstico obrigatório

Inspecionar:

- `WFGStudy.main()` e todo o fluxo transitivo.
- POM, dependências, fontes e binários do jMetal.
- Tarefas reais, especialmente T32, T33 e T32–T36.
- Suíte de testes integral.
- Manifesto, saídas, logs e artefatos gerados.
- README, evidências de documentação e alegações de prontidão.
- Reservas, diffs e processos ativos.

Não assumir que um utilitário está integrado apenas porque possui testes próprios.

## Fundamentação

Utilizar como base as evidências F5-01 a F5-12 e as decisões aprovadas no coordenador.

O relatório de auditoria é entrada de investigação, não oráculo universal. Toda afirmação deve ser classificada como `CONFIRMADA`, `PARCIALMENTE CONFIRMADA`, `CONTRADITA` ou `NÃO VERIFICÁVEL`.

## Procedimento

1. Revisar a composição de T32–T36.
2. Confirmar invocações reais e contratos no entrypoint.
3. Aplicar o mapeamento confirmado pelo operador para `T33` (seção `T33` de `docs/relatorio_auditoria-pre_execucao_final.md` incorporada diretamente na `T37`, sem criar `tasks/task33.md`).
4. Executar a reauditoria Zero-Trust em ordem cronológica reversa: revisar primeiro as integrações mais recentes (`T36` $\to$ `T32`) e depois percorrer, em ordem reversa (`T32`/`T31` $\to$ `T01`), todos os itens históricos abrangidos pela seção `T33` do relatório, validando empiricamente cada garantia sem presumir conclusão por relatórios de agentes nem tratar o relatório de auditoria como oráculo.
5. Verificar especificamente a suspensão temporária de `NHV` no perfil ativo (preservando código reutilizável, testes e histórico) e a integração separada de HV exato isolado, série secundária Monte Carlo e fallback obrigatório.
6. Executar a suíte integral sem exclusões.
7. Executar empacotamento quando necessário ao modo de execução ou carregamento de recursos.
8. Executar smokes com deadline seguro e saída aninhada (`WFGStudy/tests/...`).
9. Testar trinta jobs baratos, retomada, lotes, seed e ordem.
10. Testar orçamento efetivo e m15 com HV de geometria conhecida sob limite.
11. Classificar achados como resolvidos, refutados ou bloqueados mediante evidência reproduzida.
12. Atualizar rastreabilidade no coordenador.
13. Consolidar documentação definitiva no README pelo coordenador (incluindo o perfil ativo sem `NHV`, preservando o histórico anterior, a justificativa da imutabilidade/reversão de `MOEAD_Weights/` e a proibição de uso da cópia externa modificada do jMetal).
14. Atualizar estado curto no HANDLER pelo coordenador.
15. Não criar novo relatório de planejamento.

## README

Conferir cobertura por método/função e integração/parametrização de todas as tarefas.

Corrigir comandos, limitações e alegações de prontidão.

O relatório final deve referenciar as seções efetivamente publicadas no README.

## Aceitação

A tarefa somente será considerada concluída quando:

- A suíte real estiver aprovada sem exclusões.
- A reauditoria reversa (`T36` $\to$ `T32` e `T32`/`T31` $\to$ `T01`, conforme seção `T33` do relatório reconciliada sem criar `task33.md`) estiver executada e comprovada por evidências.
- As chamadas e saídas estiverem provadas no entrypoint real, incluindo suspensão de `NHV` no perfil ativo e exportação distinta de HV exato, HV Monte Carlo e fallback obrigatório.
- Os dados antigos e diretórios protegidos estiverem íntegros.
- A documentação estiver completa.
- Nenhum bloqueio do perfil aprovado permanecer.
- O perfil parcial não for confundido com o perfil completo.

## Testes

- Regressões das predecessoras.
- Crash e retomada.
- Erros injetados.
- Hashes.
- Latências e deadlines.
- Matriz de integração.
- Contagens reais.

Não usar 373 ou 370 como meta. Registrar contagens reais.

## Riscos

- QA cosmético.
- Testes omitidos.
- Campanha pesada inadvertida.

Esses riscos são proibidos.

## Dependências

- T32–T36.
- T33 reconciliada.
- Autorização do operador para campanha separadamente.

## Conclusão

Entregar:

- Arquivos e testes.
- Métodos e parâmetros.
- Fontes.
- README.
- Limites.
- Riscos.
- Comando validado.

Atualizar coordenador e handler.

`CONCLUÍDA` somente após revisão.
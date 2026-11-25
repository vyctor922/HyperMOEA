# T32 — Reconciliação, baseline e correções mínimas de configuração e saída

## Identificação

- Fase: 5.
- Prioridade: P0.
- Complexidade: média.
- Tipo: investigação, testes e correções localizadas.
- Estado inicial: `pending`.
- Responsável: atribuir; perfil recomendado de auditor Java/jMetal.
- Rastreabilidade: F5-01, F5-03, F5-04, F5-05 e F5-10 do coordenador.

## Objetivo

Estabelecer um baseline verificável e corrigir os impedimentos mínimos de testes/configuração/saída, sem modificar dados protegidos nem reabrir a arquitetura inteira.

## Contexto e justificativa

O coordenador conserva o planejamento T25–T31; o HANDLER registra sua conclusão; o relatório posterior usa outros títulos para alguns IDs e relata sete falhas de pesos.

O código fornecido associa `study.outputDirectory` ao nome do experimento e omite DTLZ5/6 em parte da tabela de gerações. Falhas de execução e valores científicos corretos ainda precisam de verificação.

## Escopo e diagnóstico

Inspecionar:

- Todas as tarefas reais, especialmente T23, T25–T33.
- POM efetivo, dependências e fontes/binários do jMetal.
- `MoeaddWeightValidationTest` e seus oráculos.
- `WFGStudy.main`, `getStudyName` e composição de caminhos.
- `StaticParams.getMaxGenerations` e tabelas do README.
- Evidências de aprovação e logs disponíveis.

Os arquivos individuais de tarefas e testes não foram fornecidos nesta preparação.

## Atividades

1. Conferir IDs, títulos, conteúdo, equivalências e template das tarefas.
2. Registrar no coordenador a correspondência histórica e o encaminhamento sem duplicações.
3. Confirmar existência e escopo de T32/T33 antes de depender delas.
4. Verificar ambiente, POM, dependências, modificações locais e diretórios protegidos.
5. Reproduzir o baseline integral com deadline seguro; registrar contagens reais.
6. Reproduzir isoladamente as falhas de pesos relatadas.
7. Distinguir variante legítima, oráculo incorreto e arquivo realmente inválido.
8. Corrigir fixtures/oráculos somente com evidência; não alterar o legado para satisfazer testes.
9. Separar nome simples do experimento e caminho de saída conforme o contrato real do jMetal.
10. Testar saída aninhada sem utilizar resultados finais.
11. Produzir tabela problema/m de gerações implementadas, documentadas e aprovadas.
12. Corrigir DTLZ5/6 somente após recuperar ou obter a decisão pertinente; não adotar 1500/2000 apenas para combinar com o README.

## Fundamentação

Consultar código/documentação da versão instalada, decisões G0/G1 e fontes dos algoritmos já registradas no projeto. A descrição do relatório sobre defaults de gerações diverge do snapshot: o relatório não é oráculo universal.

Não usar a cópia externa modificada do jMetal como fonte oficial.

## Arquivos autorizados e restrições

Alterações localizadas em configuração de estudo, `StaticParams` e testes pertinentes, após reserva e decisão aplicável.

Somente o coordenador edita README/HANDLER/coordenador. Nenhuma alteração física em diretórios protegidos; Git somente leitura.

## Testes e validações

- Suíte integral e testes isolados de pesos.
- Comparação por conteúdo/procedência, não somente nome.
- Saída relativa/absoluta e aninhada.
- Geração completa de relatórios, não só preflight.
- Todos os pares suportados da tabela de gerações.
- Ausência de alterações nos dados históricos.

## Critérios de aceitação

- IDs/equivalências reconciliados antes de criar tarefas conflitantes.
- Baseline registrado com comandos, versões, códigos de saída e limitações.
- Causa de cada falha tratada ou bloqueada com evidência.
- Nenhum teste obrigatório excluído para obter aprovação.
- Saída aninhada validada.
- Valores de gerações coerentes com decisão registrada.
- Documentação correspondente revisada e consolidada.

## Documentação no README

Preparar correções nas seções 4–6: configuração, dados ativos, testes, nome/diretório e comandos seguros.

Documentar individualmente os métodos relevantes, entradas/saídas, efeitos, erros e parâmetros; distinguir implementação e execução seguindo as regras do coordenador.

## Dependências, riscos e conclusão

Predecessoras novas: nenhuma. Sucessoras: T33–T36.

Riscos: confundir variante com corrupção, falsificar verde, alterar ciência incidentalmente ou iniciar teste HV ilimitado.

Entregar evento e relatório ao coordenador com arquivos, métodos/funções, parâmetros, fontes verificadas, testes/resultados, seções README e pendências. Passar por `awaiting_validation` antes de `completed_validated`.
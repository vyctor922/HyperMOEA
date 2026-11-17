# T19 — Contrato uniforme de resultados e extração não dominada

## Identificação, prioridade e dependências
Arquivo: tasks/task19.md. Prioridade: P1. Dependências: T12; contrato de métricas T17.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Comparar o mesmo tipo de conjunto sem perder população bruta, variáveis correspondentes ou observabilidade científica.

## Contexto e problema
Pipeline grava result() sem contrato uniforme explícito. Não se conhece o retorno real de cada implementação sem fontes/binaries e testes.

## Fundamentação científica e técnica
P8/P10: HV é invariante à adição de pontos dominados, mas não generalizar essa propriedade para IGD euclidiano; um ponto dominado pode ficar mais perto de um ponto de referência. Filtrar muda o objeto avaliado.

## Hipóteses a verificar
Algoritmos retornam populações completas; duplicatas; filtros alteram IGD; VAR/FUN desalinhados; número final diferente de N; conjunto vazio tratado como zero indistintamente.

## Procedimentos ordenados
1. Audite result() dos seis algoritmos e legados ativos; anote população/arquivo/não dominados e cópias. Meça antes/depois sem modificar estado interno.
2. Proponha contrato primário: aproximação final não dominada, viável quando houver restrições, sem duplicatas objetivas exatas; preservar saída bruta separada. Não forçar todos a mesma quantidade de pontos mediante descarte sem protocolo.
3. Reutilize utilitários nativos de não dominância de 7.8 quando adequados. Remova duplicatas exatas objetivas com desempate determinístico e registre múltiplas decisões equivalentes; tolerância aproximada exige decisão própria.
4. Grave FUN/VAR derivados com a mesma seleção e ordem; registre raw_count, feasible_count, nondominated_count, unique_count e hash. Não filtrar retorno usado pela hiper-heurística inadvertidamente.
5. Defina vazio e inválidos por indicador; não codificar timeout/falha como 0. Preserve IDs entre leitura e relatórios. Fixture deve demonstrar que IGD pode mudar sob filtragem, e documente decisão experimental.
6. Integre no ponto de exportação/pós-processamento, evitando sobrescrever result() de todos os algoritmos se um adaptador basta.

## Arquivos permitidos
Exportador/adaptador de resultados e testes exclusivos; leitura fontes algoritmos; sem alteração algorítmica.

## Restrições e gates
Contrato aprovado antes de regenerar métricas. Não substituir resultados antigos, nem confundir arquivo final não dominado com PF verdadeira.

## Critérios objetivos de aceitação
Seis retornos classificados; FUN/VAR alinhados; contagens/manifesto; filtros determinísticos; impacto indicadores documentado.

## Testes necessários
Pontos dominados/duplicados/NaN/vazio; empate objetivos com variáveis diferentes; índice FUN/VAR; HV invariância 2D; contraexemplo IGD; ordem de entrada.

## Entregáveis
docs/auditoria/result-contract.md, exportação uniforme e fixtures.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

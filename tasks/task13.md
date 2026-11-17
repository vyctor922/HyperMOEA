# T13 — Contabilidade real de avaliações e compatibilidade populacional

## Identificação, prioridade e dependências
Arquivo: tasks/task13.md. Prioridade: P0. Dependências: T12; decisões algorítmicas da T14 antes de patch final.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Garantir orçamento efetivo comum de avaliações, sem confundir cardinalidade de referências, população, gerações e soluções finais.

## Contexto e problema
StaticParams retorna N=100,91,210,156,275,135. Builders AGE/AGE-II incrementam N ímpar; NSGA-III valida apenas contagem esperada antes de build. Alguns builders usam iterações e outros avaliações.

## Fundamentação científica e técnica
P1–P4; ciclo de templates descrito em R7 apenas como contexto. Efetivo custo científico é a chamada real de Problem.evaluate, incluindo população inicial, reavaliações e filhos, não o valor configurado no builder.

## Hipóteses a verificar
Arredondamento NSGA-III a múltiplos de quatro; população AGE par; filhos descartados avaliados; FDEA gera dois filhos mas conta um; maxIterations inclui ou exclui inicialização; MOEADD usa parâmetros herdados de modo diferente.

## Procedimentos ordenados
1. Inspecione construtores, reprodução, atualização e parada de cada um dos seis algoritmos no binário exato. Meça N real após construção, inicialização e reprodução; não assuma N=referências.
2. Crie wrapper/contador por job para Problem.evaluate, com contagem independente do contador interno. Conte também chamadas em evaluator e qualquer avaliação direta em FDEA.
3. Teste limites B=N, N+1, 2N-1, 2N, além de orçamentos múltiplos de populações reais e N ímpar. Registre solicitadas/efetivas, inicialização, filhos gerados/avaliados/utilizados e cardinalidade de result().
4. Construa tabela G atual e B atual por problema/m; DTLZ5/6 usam defaults e precisam de justificativa, não um valor inventado. Confirme n=20 no construtor DTLZ e k=n-m+1; não confunda L de WFG com k de DTLZ.
5. Compare alternativas: mesma N viável para todos; N específicas com mesmo B; orçamento comum alinhado aos lotes; adaptação mínima para lote final parcial. LCM de populações não deve tornar o estudo inviável. Igualdade de N não é prioridade sobre identidade do método.
6. Proponha um único B por problema/m, independente do arredondamento de cada algoritmo, e conte avaliações iniciais dentro dele. Para truncamento, não execute seleção que exija filhos inexistentes; não use exceção arbitrária em evaluate para encerrar com estado inválido.
7. Submeta decisão de engenharia e mudança de identidade ao gate. Só então implemente adaptação localizada. Não permita ultrapassar B em silêncio; algoritmo incompatível bloqueia a configuração ou recebe protocolo explicitamente aprovado, sem fingir igualdade.

## Arquivos permitidos
Contador/testes próprios, fábricas/adaptadores mínimos após aprovação; WFGStudy/StaticParams somente na janela de integração T23; fontes algorítmicas reservado.

## Restrições e gates
Não recalibrar B por resultado observado, não compensar orçamento dando gerações extras a algoritmo selecionado por desempenho.

## Critérios objetivos de aceitação
Contagens reais demonstradas para seis algoritmos e seis m; B efetivo igual por configuração ou bloqueio documentado; nenhuma chamada a mais escondida; mudanças de população justificadas.

## Testes necessários
Instrumentação para problemas baratos; orçamentos de fronteira; repetição por seed; detecção intencional de overshoot; validar que wrapper não aumenta avaliações.

## Entregáveis
docs/auditoria/budget-audit.csv e .md, contrato B/N/G e testes de contabilidade.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

# T23 — Integração mínima, preflight e homologação da campanha

## Identificação, prioridade e dependências
Arquivo: tasks/task23.md. Prioridade: P0. Dependências: T12–T22 com gates científicos resolvidos.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Integrar apenas decisões aprovadas e provar prontidão da primeira parte dos experimentos, sem iniciar automaticamente a campanha completa.

## Contexto e problema
WFGStudy centraliza lista, indicadores, caminhos e estatísticas. Tarefas paralelas devem fornecer componentes/patches sem alterar esse arquivo simultaneamente.

## Fundamentação científica e técnica
Contratos das T13–T22; R1–R7 para comportamento técnico. Testes verdes históricos não substituem contagem real B, isolamento e cobertura de referências.

## Hipóteses a verificar
Filtros ignorados; resultados antigos misturados; perfil principal inclui legados; dry-run instancia milhares; falha HV bloqueia geração de outros indicadores; opção parcial mascara falta de DTLZ.

## Procedimentos ordenados
1. Tome propriedade exclusiva de WFGStudy/StaticParams/pom se necessário. Integre patches em ordem: configuração/escopo, contador B, job/RNG, resolvedor, resultado, normalização, HV deadline, estatística, profiler. Evite reescrita arquitetural não necessária.
2. Preflight sem criar Algorithm: lista explícita de pares problema/m ativos/bloqueados, seis algoritmos, runs, B, referências, sementes, dependências R, licença/versão e custos estimados. Parcial padrão e geração all=false preservados; campanha completa explicitamente falha se algum par solicitado está inválido.
3. Preflight deve detectar combinações inválidas n/m/k/l. DT LZ n=20 não significa k=20; WFG k=2(m-1), l=20. Se configurar n por problema para reprodução canônica, submeter ao protocolo, não alterar silenciosamente.
4. Run ID/protocol hash único; diretório de resultados novo; FUN/VAR e indicadores com manifestos. Retomar apenas jobs com mesmo hash e saída íntegra; falha fica status, não resultado numérico fictício.
5. Execute mvn clean test e mvn package se empacotamento/recursos for caminho real. Rode smoke WFG1 m2, DTLZ2 m3, DTLZ7 m2/3 aprovado, e um caso m15 com deadline/MC e B reduzido explícito; não apresente smoke como campanha de 30 runs.
6. Valide 30 runs em fixture barata para cardinalidade, IDs, sementes, relatórios estatísticos e retomada, sem exigir HV pesado. Compare serial/paralelo sob sementes iguais conforme T15.
7. Gere relatório de aceite independente e lista de bloqueios. Só liberar comando completo após aprovação do pesquisador. Toda alteração do README preserva histórico no formato exigido por AGENTS.md.

## Arquivos permitidos
WFGStudy/StaticParams e configuração central reservado exclusivo; testes de integração; README na entrega final em coordenação T24; docs/auditoria/validation-phase3.md.

## Restrições e gates
Nenhuma aprovação implícita para campanha completa ou mudança científica pendente. Não sobrescrever WFGStudy histórico de resultados.

## Critérios objetivos de aceitação
Baseline vs nova suíte comparados; B/seeds/recursos/métodos corretos; timeout produção comprovado; seis algoritmos padrão; falhas e retomada testadas; aceite sem prometer conclusão de experimento não executado.

## Testes necessários
Unitários/integração/matemática/R/concorrência/IO; preflight offline; pacote fora raiz; 30-run fixture; crash/retomada; m15 com limite efetivo.

## Entregáveis
Relatório final de homologação, protocolo congelado, comandos aprovados e pendências bloqueantes.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

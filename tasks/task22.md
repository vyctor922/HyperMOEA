# T22 — Reauditoria do profiler e piloto CPU/memória em 15 objetivos

## Identificação, prioridade e dependências
Arquivo: tasks/task22.md. Prioridade: P1. Dependências: T12, T15, T17, T20; orçamento T13 antes dos benchmarks finais.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Reproduzir custo real com métricas CPU/parede/memória corretamente atribuídas e overhead conhecido.

## Contexto e problema
HANDLER relata 630 registros, 277 testes e tempos HV extremos; logs/CSV/fontes não foram anexados. main chama profiler somente após pós-processamento e tabelas.

## Fundamentação científica e técnica
R5: ThreadMXBean mede thread de plataforma, não CPU total do job ou processo automaticamente; precisão não implica acurácia. R4 permite worker isolado. Memória heap usada não é RSS nem pico exato.

## Hipóteses a verificar
CPU medido na thread que espera Future e não na que calcula; pico heap acumulado da JVM; GC/JIT/warm-up contaminam comparação; truncamento perf.maxHvFrontSize15D altera problema medido.

## Procedimentos ordenados
1. Leia PerformanceProfiler existente antes de criar outro. Reproduza comandos/CSV antigos quando custo e deadline permitirem; dados antigos ficam reported_unreproduced até confirmação. Converter separadores decimais/milhar conforme log, sem assumir que 40.863 significa 40 segundos.
2. Validar timers: System.nanoTime parede; CPU no worker que executa, suporte e habilitação; para subprocesso coletar CPU do processo quando disponível e declarar abrangência/NA. Não usar CPU da thread aguardando resultado como CPU de HV.
3. Memória: heap usado antes/depois, pools/MemoryPoolMXBean picos e limites de atribuição; MemoryMXBean sozinho não fornece pico por tarefa. JFR/per-processo pode complementar. RSS separado se disponível. Não forçar GC como protocolo científico padrão.
4. Fixe hardware, SO, Java/Maven/jMetal, flags, Xms/Xmx adequados ao hardware, GC, cores, concorrência, seed, hashes e tamanho das entradas. Separar custo de startup/JIT/aquecimento e cold/warm IO; warm-up fora das repetições medidas.
5. Proposta piloto: cinco repetições medidas de jobs baratos após warm-up justificado, WFG4 m=3,8,15 e DTLZ2 validado; não transformar cinco em garantia estatística universal. Expanda a seis algoritmos em m15 se viável. Distinguir avaliação de qualidade (30 runs) de perfil de custo.
6. Separar algoritmo, extração ND, leitura PF, normalização, HV exato/MC, IGD/IGD+/R2 e geração de relatórios. NHV retirado evita recálculo; se medido, separar derivação e custo HV(R).
7. Não reduzir frente a 35 pontos e chamar custo do conjunto N=135; microbenchmark truncado deve ser rotulado e nunca substituir custo da execução completa. Registre número real de pontos, ND, duplicatas e bytes PF.
8. Avalie overhead instrumentado vs não instrumentado em protocolo aleatorizado/repetido, com mediana/IQR e limitações. Sem remoção automática de outliers. Verifique memória após série de jobs e inexistência de workers órfãos.

## Arquivos permitidos
PerformanceProfiler e testes reservado; docs/performance/phase3/ e CSV novos. Não sobrescrever medições antigas.

## Restrições e gates
Não repetir HV15 sem deadline efetivo T20. Heap 2g/8g é exemplo, não obrigação independente da máquina.

## Critérios objetivos de aceitação
CPU e parede diferenciados com unidade/abrangência; ao menos caso m15 medido ou bloqueio registrado; conjunto completo rotulado; overhead e picos honestos; logs reais.

## Testes necessários
Sleep vs busy-loop; tarefa em outra thread; subprocesso terminado; CPU unsupported; pico de pool; CSV round-trip; entrada completa/truncada identificada.

## Entregáveis
Protocolo de desempenho corrigido, CSV versionado e relatório reprodução/limitações.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

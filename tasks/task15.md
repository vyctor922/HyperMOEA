# T15 — Isolamento de jobs, sementes e estado mutável

## Identificação, prioridade e dependências
Arquivo: tasks/task15.md. Prioridade: P0. Dependências: T12; interface de T13/T14 para integração.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Executar runs reproduzíveis com estado por job, concorrência limitada e criação sob demanda.

## Contexto e problema
configureAlgorithmList instancia algoritmos antecipadamente e reutiliza problem, crossover e mutation entre algoritmos da mesma configuração/run. O tamanho retido em memória não foi medido. R2 da lista usa reset de estado na troca de referência; isso não comprova segurança concorrente.

## Fundamentação científica e técnica
R3/R4 sustentam controle de tarefas/processos; R7 mostra liberdade do Algorithm sem promessa de thread safety. Reprodutibilidade requer fluxo aleatório não dependente do escalonamento.

## Hipóteses a verificar
Singleton JMetalRandom compartilha gerador; setSeed concorrente contamina runs; Algorithm e observadores retêm populações; HH existem ou são apenas planejadas; caches R2 atravessam problemas.

## Procedimentos ordenados
1. Faça grafo de propriedade de problem, operadores, RNG, Algorithm, indicator, observer, PF, ideal/nadir e caches. Inspecione AdapHH, EA-ILS, LAST-RL, AILLA somente se existirem; ausência é pendência de fase posterior.
2. Defina JobSpec imutável com problema, n/k/l/m, algoritmo, run, B, seed, hashes/protocolo. Faça factory criar instância imediatamente antes do run; ao terminar mantenha apenas saída e metadados necessários. Não imponha refatoração ampla se executor existente oferecer factory adequada.
3. Prefira processo JVM por job com pool limitado, se o singleton impedir isolamento seguro e não houver injeção RNG abrangente. Alternativa: execução sequencial determinística ou RNG por job comprovadamente propagado; trocar só um Random não basta.
4. Defina derivação estável de seed por masterSeed+ID canônico, sem hashCode instável/dependente do processo; registre gerador/algoritmo e seed. Fixar mesma seed numérica em algoritmos diferentes não cria por si só desenho pareado.
5. Instancie indicadores por job/problema e pesos R2/ideal imutáveis ou copiados. Não sincronize só um setter deixando compute concorrente sem proteção. Evite arrays compartilhados que bibliotecas podem ordenar in-place.
6. Observadores novos/reset verificável; históricos de recompensa/Tabu/Q-tables por run. Não codificar adaptação de HH nesta fase. Manifesto de execução deve distinguir falha, timeout e sucesso.
7. Determine limite de concorrência por RAM/CPU; teste limpeza de processos e arquivos. Não usar System.gc como garantia nem desalocação manual. Resumir retenção antes/depois com profiler/JFR quando disponível.

## Arquivos permitidos
Fábrica/executor/manifesto e testes exclusivos; observadores/RNG reservado. WFGStudy integra pela T23. Sem alterações na matemática de seleção.

## Restrições e gates
Não declarar thread safety por ausência de exceções; não usar Thread.stop; processo isolado deve drenar logs e confirmar término.

## Critérios objetivos de aceitação
Mesma seed/configuração gera mesma FUN em runs repetidos e com ordem/pool alterados, sob ambiente fixo; nenhum estado cruza jobs; pool limitado; criação lazy comprovada.

## Testes necessários
Execução serial vs concorrente; seeds diferentes; falha do worker; retentativa idempotente; vazamento de descritores/processos; mutação de arrays e caches.

## Entregáveis
docs/auditoria/isolation-rng.md, esquema JobSpec/manifesto, executor mínimo e testes.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

# T20 — Hipervolume híbrido, timeout efetivo e incerteza Monte Carlo

## Identificação, prioridade e dependências
Arquivo: tasks/task20.md. Prioridade: P0. Dependências: T12, T17, T19; arquitetura de isolamento T15.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Garantir término real do cálculo exato e fallback validado para HV de alta dimensão sem contaminar comparações.

## Contexto e problema
main roda ComputeQualityIndicators antes de PerformanceProfiler opcional. Portanto timeout anunciado no profiler não prova proteção do caminho de produção. Não estão disponíveis fonte profiler nem logs. Main-Hipervolume usa nanoTime como seed e referência escalar.

## Fundamentação científica e técnica
R3: cancel(true) tenta interromper, não garante término. R4: processo pode ser terminado com confirmação. P9 não torna estimador de HV total equivalente às contribuições HypE. Inferência matemática do estimador Bernoulli descrita nos procedimentos deve ser distinguida de recomendação de amostras universal.

## Hipóteses a verificar
Thread continua recursão após timeout; NHV duplica HV caro; MC tem volume dominado raro em 15D; caixa por algoritmo inviabiliza amostras comuns; misturar método só nos runs difíceis cria viés.

## Procedimentos ordenados
1. Audite caminho real de HV e profiler, teste interrupt ignorado sem deixar thread zumbi no runner de teste. Se backend não cooperativo, encapsule exato em processo JVM worker; deadline de parede inclui início conforme contrato explícito, com timing de cálculo separado.
2. Supervisor: waitFor(deadline), tentativa de encerramento, destroyForcibly se necessário, esperar término e verificar isAlive=false; drenar stdout/stderr, fechar streams, limitar processos/heap e limpar temporários. Future cancel sozinho não satisfaz aceitação. Se usar backend cooperativo derivado, provar checkpoints na recursão e equivalência com origem/licença; não exigir cooperação inexistente do jMetal.
3. Pré-processar contrato T17/T19: minimização, m e finitude, referência, não dominados e únicos. Rejeitar/informar inválidos; filtrar pontos fora r que não contribuem. Definir HV vazio=0; caixa degenerada=0; referências insuficientes explícitas.
4. Para MC iid uniforme, escolher l fixo da configuração com l<=todas coordenadas dos pontos integrados e l<r. Mínimos por frente são válidos para cada volume, mas não dão caixa física comum; se usados, não afirmar common random points. Preferir caixa comum com lower bounds aprovados; amostrar mesmas coordenadas e seed para todos os algoritmos para precisão de diferenças, com análise de dependência estatística.
5. Estimar V=prod(r-l), X=sum I(amostra dominada por algum ponto), p_hat=X/S, HV_hat=V*p_hat; Var estimador=V² p(1-p)/S. Registrar SE estimado e IC binomial adequado (Wilson/exato conforme protocolo), multiplicado por V. X=0 não prova HV=0: fornecer limite superior não nulo. Erro relativo explode com p pequeno; 10^6 não é garantia.
6. Planejar S e teto de custo com piloto independente. Meta de erro absoluto nas unidades normalizadas e precisão de diferenças entre algoritmos deve ser aprovada. Para parada adaptativa, não aplicar IC de tamanho fixo como garantia após múltiplas inspeções: usar S fixado pelo piloto ou método sequencial validado. QMC, se considerada, não recebe IC iid binomial sem justificativa.
7. Híbrido tenta exato sob prazo configurável e usa MC somente após confirmar término do worker. Salvar método, motivo, versão, S, X, seed, caixa, IC, SE, status, wall/CPU e hashes. Valor indisponível é NA/status, não zero.
8. Para inferência principal, predefinir método uniforme por problema/m após piloto. Se ocorrer fallback num run, recomputar MC de todos os grupos dessa célula com mesmo protocolo ou fazer análise de sensibilidade contra resultados exatos disponíveis. Não misturar exato e MC em KW automaticamente; examinar erro próximo aos empates e dados faltantes.
9. Validar escala e referências contra exato 2D/3D, conjuntos com HV conhecido até 15D, e cenas com p raro. Main-Hipervolume é apenas referência histórica, não copiar suas falhas.

## Arquivos permitidos
Worker/supervisor HV e testes próprios; profiler existente reservado apenas correções aprovadas; adaptador de métricas integra pela T23. Não editar JAR instalado.

## Restrições e gates
Proteção deve atuar no pipeline normal, não só em profiling. Sem término comprovado bloqueia HV15. Não implementar método opcional matematicamente incorreto.

## Critérios objetivos de aceitação
Teste de job não cooperativo encerra processo dentro de limite+grace aprovado e não sobra worker; MC com IC correto e seed reproduzível; esquema status sem zeros fictícios; política célula/método aprovada.

## Testes necessários
União de retângulos manual; dominados/fora r; caixa vazia; NaN; X=0/S; cobertura IC em replicações MC independentes; timeout/OOM/worker crash; concorrência e logs volumosos.

## Entregáveis
docs/auditoria/hv-hybrid.md, política erro/deadline, protótipo futuro validado, fixtures e relatório de piloto.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

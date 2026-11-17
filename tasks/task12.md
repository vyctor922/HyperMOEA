# T12 — Inventário probatório, versões e reconciliação do escopo

## Identificação, prioridade e dependências
Arquivo: tasks/task12.md. Prioridade: P0. Dependências: Nenhuma.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Criar baseline verificável e decidir quais tarefas antigas são preservadas, insuficientes ou reabertas, sem executar o experimento completo.

## Contexto e problema
Anexos atuais incluem HANDLER e pipeline, mas não pom.xml, fontes FDEA, CSVs, profiler, relatórios ou tasks individuais. T01–T11 aparecem completed no HANDLER; isso não equivale a reprodução independente. A dissertação pré-experimentos inclui HypE e não AGE-MOEA-II; o código adiciona onze algoritmos.

## Fundamentação científica e técnica
R6/R7 mostram que páginas latest não fixam 7.8. P1–P10 identificam métodos, não aprovam automaticamente parâmetros. A pergunta de pesquisa e o protocolo aprovado pelo pesquisador governam o escopo; o código determina o comportamento observado.

## Hipóteses a verificar
Identidade/versão do artefato Maven; disponibilidade de relatórios; diferenças entre código atual e logs; independência de seeds; presença de hiper-heurísticas implementadas.

## Procedimentos ordenados
1. Faça inventário recursivo de tasks/, docs/, resources/, src/, pom.xml e logs, preservando checksums e git diff. Leia todas as tasks reais antes de criar/copiar novos arquivos; se T12–T24 já existirem, realoque o lote e ajuste referências em conjunto, mantendo mapa de aliases.
2. Execute java -version, mvn -v, mvn dependency:tree e mvn help:effective-pom. Obtenha source JARs exatos ou javap do binário; registre coordenadas Maven, checksum, módulo, pacote, assinatura, trechos e limites do diagnóstico.
3. Monte matriz de APIs: ReferencePoint/generateReferencePoints, ReferencePointGenerator/generateSingleLayer/generateTwoLayers, NSGAIIIBuilder, RVEABuilder, AbstractMOEAD.initializeUniformWeight, GenerateReferenceParetoSetAndFrontFromDoubleSolutions, ComputeQualityIndicators, PISAHypervolume, NormalizedHypervolume, IGD, IGD+, R2. Distinguir nome inexistente de acesso indisponível. Registre assinaturas reais e efeito de referenceFront/setReferenceFront, sem inventar setters.
4. Classifique individualmente T01–T11 e quaisquer tarefas adicionais: pending, in_progress, blocked, completed_validated, completed_insufficient ou reopened. Preserve o status histórico declarado e acrescente status desta revisão. Não reabra T06 apenas por uma alegação antiga: a chamada atual usa 1/n.
5. Compare dissertação, coordinator, handler e pipeline; proponha perfil seis algoritmos principais, 30 runs, objetivos 2,3,5,8,10,15, DTLZ/WFG. Obtenha aceite explícito para HypE→MOEA/DD e inclusão AGE-II. Não edite a dissertação silenciosamente.
6. Obtenha baseline reduzido de testes sob deadline externo, sem HV15 ilimitado. Conte configurações por dry-run; não instancie todas para apenas contá-las.
7. Produza registro de decisões com classe: fato local, relato não reproduzido, hipótese, inferência, recomendação, experimento pendente. Libere gates de pesquisa/implementação por tarefa.

## Arquivos permitidos
Leitura de todo o repositório; escrita apenas docs/auditoria/inventario.*, docs/auditoria/estado-tarefas.md, docs/decisions/ e eventos. Não editar fontes.

## Restrições e gates
Sem Maven/pom ou JAR exato, bloquear conclusões de API e liberar somente pesquisa independente. Escopo não aceito bloqueia campanha, não o diagnóstico.

## Critérios objetivos de aceitação
100% das tarefas reais inventariadas; matriz de APIs com evidência ou AUSENTE; escopo divergente documentado; baseline com comandos e resultados reais; mapa de numeração sem sobrescrita.

## Testes necessários
Checksums reproduzíveis; teste de inventário de arquivos faltantes; execução real de testes disponíveis com timeout do processo.

## Entregáveis
Inventário, estado-tarefas, matriz API 7.8, baseline e decisão de escopo aprovada ou bloqueada.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

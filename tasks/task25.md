# T25 — Inventário arquitetural e caracterização da resolução atual

## Identificação e objetivo
- Arquivo proposto: tasks/task25.md.
- Prioridade: P0.
- Dependências: Nenhuma nova; consultar histórico T12–T24.
- Estado inicial: pending; confirmar numeração e estado na IDE.

Estabelecer evidências, consumidores e baseline para a refatoração, sem alterar produção. Concentrar diagnóstico inicial e matriz de dependências no HANDLER via coordenador; não criar relatório separado para esse diagnóstico.

## Contexto e evidências disponíveis
Na preparação foram disponibilizados WFGStudy.java, StaticParams.java, AGENTS.md, HANDLER.md, README.md e TASK_COORDINATOR.md. Não foram disponibilizados os arquivos individuais de tasks/, pom.xml, ArtifactResolver/Test, validadores, EvaluationBudgetProblem, ResultFrontContract, PerformanceProfiler nem o relatório de duplicatas. O agente local deverá inspecioná-los integralmente.

Confirmado no snapshot de WFGStudy: resolveMoeadDataDirectory testa Files.exists em pasta oficial, weights_gen e DEFAULT_LEGACY_WEIGHTS_DIRECTORY; buildMOEADD resolve e depois validateMoeadWeightFile resolve novamente; runPreflightValidation possui fallback explícito para pareto_fronts/csv. Chamadas a resolveCanonicalReferenceFront e adaptação para String/Path estão presentes. Duplicação com outras classes permanece hipótese. HANDLER registra decisões anteriores sucessivas, inclusive canônico+fallback em 09/11; o requisito atual remove o fallback.

## Fundamentação técnica
## Referências e limites da fundamentação
- Martin Fowler, Refactoring, definição e transformações pequenas com preservação de comportamento: https://refactoring.com/ . A retirada de fallback e a nova precedência são mudanças de requisito explícitas, não mera refatoração preservadora de comportamento.
- D. L. Parnas (1972), On the criteria to be used in decomposing systems into modules, Communications of the ACM 15(12), 1053–1058, DOI 10.1145/361598.361623. https://doi.org/10.1145/361598.361623 . Fundamenta discutir critérios de modularização; não determina automaticamente quais classes deste projeto devem ser fundidas.
- Oracle Java SE 21, Files: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html ; MessageDigest: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html . Existência não valida conteúdo; SHA-256 identifica bytes, não prova procedência nem correção matemática.
- Apache Maven Surefire, documentação oficial: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html . Conferir parâmetros na versão do pom real; não atualizar plugin/dependências para coincidir com documentação latest.
- JUnit: utilizar documentação da versão instalada e @TempDir se suportado. O endereço current consultado redireciona para JUnit 6; não é prova de versão do projeto nem autorização para upgrade.
- Para simplex e algoritmos: consultar Das e Dennis (1998), Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems, SIAM Journal on Optimization 8(3), 631–657; Deb e Jain (2014), NSGA-III; formulações DTLZ/WFG pertinentes e source JAR/bytecode jMetal 7.8 realmente resolvido. Nenhuma fórmula, assinatura ou regra de geração em memória do binário foi reproduzida na etapa de planejamento.


## Hipóteses a verificar
Quais duplicações são reais; como o ArtifactResolver participa do caminho efetivo; origem 7.8/7.11 dos recursos vs versão de execução; quais tarefas já cobriram partes do trabalho; quais testes apenas verificam existência; quais propriedades aceitam paths legados.

## Procedimentos ordenados
1. Leia HANDLER primeiro e depois demais documentos/tasks; inventarie numeração real, reserve IDs e identifique sobreposições com tarefas pendentes. Não declarar leitura de arquivo ausente. Proponha consolidação de tarefas equivalentes antes de duplicar trabalho.
2. Confirme Java/Maven/pom/dependency:tree/source JAR, sem upgrade. Inspecione o relatório docs/duplicatas_analise-10do11.md integralmente e classifique cada alegação: fato confirmado, aparente, refutada ou pendente.
3. Leia ArtifactResolver e teste, todos os enums/records associados e consumidores diretos/indiretos. Localize isMoeadWeightGeneratedInMemory, exportActiveArtifactManifestCsv, ArtifactManifestEntry, resolveCanonicalReferenceFront e o ciclo de vida de EvaluationBudgetProblem.
4. Levante grafo de chamadas real: resolução, validação, geração, parsing, hash, manifesto, preflight, builder, profiling, exportação e indicadores. Inclua chamadas refletivas, scripts, testes e flags por caminho. Não usar apenas grep como prova de grafo completo.
5. Para cada leitura/escrita/check de existência, anote método, linhas/assinatura, origem, paths, política de validade, efeitos colaterais, retorno e consumidor. Separe runtime, ferramenta de migração offline, fixture negativa e documentação histórica. Liste todos os acessos operacionais legados, inclusive indiretos via constantes/propriedades.
6. Caracterize comportamento atual com testes já existentes e fixtures temporárias, sem refatorar: oficial/gerado ausente, inválido, válido, prioridade, casos 2D em memória, hashes, manifesto e diretório de trabalho. Só adicionar testes de caracterização quando necessários e sem conflito; resultados antigos não substituem baseline executado.
7. Confira artefatos reais em resources: inventário de N/m/formato/bytes/hash/origem. Fontes copiadas de 7.11 não podem ser rotuladas como 7.8 só porque o runtime é 7.8. Equivalência numérica/hash não comprova autoria histórica.
8. Entregue ao coordenador diagnóstico no evento, matriz atualizada, comandos/testes e lista das fontes ausentes. O coordenador consolida no HANDLER. Não criar docs/auditoria/novo-diagnostico.md nem novo arquivo de matriz. Liberar investigação T26/T27 somente com insumos suficientes.

## Arquivos envolvidos e permissões
Leitura de todo repositório e recursos; escrita em testes de caracterização exclusivos desta tarefa e eventos temporários docs/T25-*.md. HANDLER/README somente coordenador. Nenhuma modificação em src/main nesta tarefa.

## Restrições específicas
Não criar antecipadamente os dois documentos arquiteturais finais. Não rodar campanha completa nem HV15 ilimitado. Não alterar decisões de T12–T24 só porque são antigas. Git somente leitura.

## Critérios objetivos de aceitação
Inventário de tasks/APIs/consumidores verificável; cada hipótese do relatório anterior classificada; acessos legados catalogados; baseline com logs reais ou bloqueio explícito; HANDLER com diagnóstico/matriz atualizados, sem relatório paralelo de diagnóstico.

## Testes e verificações necessários
Testes existentes de resolução e integração pertinentes; fixtures em diretório temporário; caracterização oficial/gerado/legado e erros; comando mvn clean test se seguro, com resultados reais, sem contagens antecipadas.

## Entregáveis
Eventos de inventário e baseline para consolidação no HANDLER; testes de caracterização necessários; arquivos/consumidores reservados e IDs reconciliados.

## Regras comuns e atualização do HANDLER
- Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e todas as tasks reais antes de iniciar. Verifique trabalho equivalente já concluído; preserve T01–T24 e quaisquer tarefas posteriores. Numeração T25–T31 é proposta baseada no histórico anexado, não inventário da pasta tasks/ real. Se houver colisão, o coordenador realoca o lote, atualiza referências e registra o mapa antes da execução, sem sobrescrever arquivos.
- HANDLER anexo registra T12–T24 completed_validated e 378 testes em uma execução anterior; isso é histórico, não execução desta tarefa. Confirme o estado real e aproveite evidências válidas, sem reiniciar auditorias científicas não afetadas.
- Fonte, relatório ou teste ausente: registrar AUSENTE e bloquear apenas a conclusão dependente. Não inferir duplicação pelo nome da classe. Relatório docs/duplicatas_analise-10do11.md é ponto de partida, não prova automática.
- Não alterar código antes de T26 produzir docs/resolucao_duplicatas_arquitetura.md, completar o mapa de responsabilidades e obter aprovação arquitetural explícita do operador. O coordenador consolida evidências, mas não substitui aprovação exigida por AGENTS.md. Este prompt autoriza o requisito de resolução canônica, não todas as assinaturas ainda a projetar.
- Não alterar versões, operadores, orçamento, sementes, fórmulas, referência HV ou tratamento de zeros como efeito colateral da refatoração. Mudança científica necessária exige decisão separada. Sem reescrita ampla, novas dependências injustificadas ou alteração do Git; apenas checagens de leitura no Git.
- Diretórios MOEAD_Weights/ e pareto_fronts/csv/ são fisicamente protegidos. Leitura offline estritamente para inventário/migração autorizada é distinta de acesso operacional; nenhuma busca/leitura/fallback/verificação neles permanece na resolução ativa final. Não deletar, editar ou mover arquivos históricos.
- Somente o coordenador edita HANDLER.md e README.md, inclusive em execução sequencial. Agente cria eventos exclusivos docs/<ID>-<agente>-<evento>.md ao iniciar, bloquear, retomar e entregar; incluir horário, arquivos reservados/alterados, evidências, decisões, comandos e códigos de saída. Coordenador consolida, confere, preserva evidências necessárias e só então apaga eventos temporários. Logs extensos podem ir a docs/history/.
- Testes usam diretórios temporários; resultados exploratórios somente em WFGStudy/tests/ se for necessária saída no workspace. Não usar diretórios finais WFGStudy/ nem modificar resultados existentes.
- Entregar awaiting_validation com checklist de critérios, arquivos e testes aprovados/reprovados/não executados/inconclusivos. Coordenador só promove a completed_validated após revisão; critério obrigatório não atendido => blocked/não concluída, nunca aprovação parcial escondida.

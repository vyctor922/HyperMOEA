# T26 — Investigação das duplicatas e aprovação da arquitetura de destino

## Identificação e objetivo
- Arquivo proposto: tasks/task26.md.
- Prioridade: P0.
- Dependências: T25.
- Estado inicial: pending; confirmar numeração e estado na IDE.

Produzir na IDE docs/resolucao_duplicatas_arquitetura.md com fontes de verdade, assinaturas propostas e plano de migração aprovado antes de modificar implementações.

## Contexto e evidências disponíveis
As quatro áreas obrigatórias foram apontadas pelo operador como suspeitas; somente inspeção T25 e investigação local podem confirmar duplicação. Remoção futura de ArtifactResolver é condicionada a sucessores identificados e regressões cobertas.

WFGStudy atualmente resolve pesos mais de uma vez e faz verificação de PF no preflight. Não é evidência suficiente para fundir todos os validadores. A separação de primitivas matemáticas, políticas de resolução e adaptação jMetal deve ser avaliada por responsabilidade e consumidores reais.

## Fundamentação técnica
## Referências e limites da fundamentação
- Martin Fowler, Refactoring, definição e transformações pequenas com preservação de comportamento: https://refactoring.com/ . A retirada de fallback e a nova precedência são mudanças de requisito explícitas, não mera refatoração preservadora de comportamento.
- D. L. Parnas (1972), On the criteria to be used in decomposing systems into modules, Communications of the ACM 15(12), 1053–1058, DOI 10.1145/361598.361623. https://doi.org/10.1145/361598.361623 . Fundamenta discutir critérios de modularização; não determina automaticamente quais classes deste projeto devem ser fundidas.
- Oracle Java SE 21, Files: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html ; MessageDigest: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html . Existência não valida conteúdo; SHA-256 identifica bytes, não prova procedência nem correção matemática.
- Apache Maven Surefire, documentação oficial: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html . Conferir parâmetros na versão do pom real; não atualizar plugin/dependências para coincidir com documentação latest.
- JUnit: utilizar documentação da versão instalada e @TempDir se suportado. O endereço current consultado redireciona para JUnit 6; não é prova de versão do projeto nem autorização para upgrade.
- Para simplex e algoritmos: consultar Das e Dennis (1998), Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems, SIAM Journal on Optimization 8(3), 631–657; Deb e Jain (2014), NSGA-III; formulações DTLZ/WFG pertinentes e source JAR/bytecode jMetal 7.8 realmente resolvido. Nenhuma fórmula, assinatura ou regra de geração em memória do binário foi reproduzida na etapa de planejamento.


## Hipóteses a verificar
Resolução repetida de PF entre ParetoFrontValidator/WFGStudy/PerformanceProfiler; pesos entre WFGStudy/EvaluationBudgetProblem; combinatória entre ReferenceSetValidator/ReferencePointGenerator/ParetoFrontValidator; dominância entre ParetoFrontValidator/ResultFrontContract; hashes/parsing/normalização/manifestos duplicados.

## Procedimentos ordenados
1. Confronte o relatório anterior e T25 com o corpo dos métodos. Classifique duplicação de regra, algoritmo, infraestrutura, código semelhante com semântica diferente e wrapper legítimo. Evidencie por assinatura, trechos/linhas, chamadas e testes; não copiar toda a fonte para o documento.
2. Mapeie as quatro frentes obrigatórias e investigue hashes, parsing, normalização, IO, geração, configurações e profiling. Documente também resultados negativos: ausência de duplicação é conclusão válida.
3. Proponha fonte única por responsabilidade: resolução de PF no contrato ParetoFrontValidator.resolveCanonicalReferenceFront; validação/geometria dos pesos em ReferenceSetValidator; WFGStudy.validateMoeadWeightFile como fachada da política opcional 2D em memória, delegando matemática e IO sem copiá-las; EvaluationBudgetProblem recebe dependência/resultado resolvido e não referencia o pipeline de estudo para implementar sua regra. Evite ciclo util→study→util.
4. Separe combinatória simplex, transformação em referência/direção e PF analítica. Reutilize gerador nativo apenas se comportamento 7.8 satisfizer contrato; não padronizar fórmula da camada NSGA-III para torná-la canônica como parte de refatoração. Não unificar PF com direções porque ambos usam double[].
5. Dominância: definir minimização/maximização, restrições, igualdade, duplicatas, m, NaN e tolerância. Avaliar utilitário/comparador nativo exato antes de extrair implementação própria. Compartilhar núcleo comparador, não necessariamente rotinas completas de filtragem/contagem com objetivos diferentes.
6. Inventarie CADA responsabilidade pública/privada relevante de ArtifactResolver, enums, records e testes. Para cada uma definir destino (ParetoFrontValidator, ReferenceSetValidator, WFGStudy fachada ou helper coeso aprovado), consumidor migrado, método preservado/delegado/deprecated/removido e teste substituto. Não criar ArtifactResolver2 disfarçado ou mover tudo para um validator gigante.
7. Defina retornos estruturados equivalentes a ResolvedFrontArtifact/ResolvedWeightArtifact e discriminante FILE/IN_MEMORY. Propor campos mínimos, invariantes e assinaturas Java exatas após inspeção. Estado de validação deve ter semântica precisa; DTO de sucesso não pode carregar falha ambígua. Path/hash de arquivo opcionais para IN_MEMORY; não criar arquivo fictício. Hash de representação canônica de vetor, se adotado, tem algoritmo/serialização separados do SHA-256 de bytes físicos.
8. Projete baseDir injetável em WFGStudy/ParetoFrontValidator, parâmetros por contexto e construção normal na fronteira main; nada de alterar user.dir para testar. Mostre como workers recebem contexto; resolução não varre pais/workspace nem aceita explicitamente diretório legado via override. APIs antigas podem delegar temporariamente com @Deprecated quando houver consumidor; o wrapper não mantém fallback proibido.
9. Formalize política conteúdo-antes-de-seleção e tratamento de candidatos inválidos, concorrência na geração, ausência de gerador, variantes epsilon, empírico opt-in, limites de cobertura e manifestos de consumo real. Memória 2D é opção excepcional explicitamente pedida, não nova fonte de arquivos de prioridade zero silenciosa. Defina como coexistirá com resolução FILE e com comportamento nativo inevitável da biblioteca.
10. Escolha proprietário do manifesto em ParetoFrontValidator ou ReferenceSetValidator com justificativa e helper interno somente se necessário. Documento deve conter atributos, assinaturas, fluxos atual/futuro, dependências, plano por etapas, riscos, testes e decisão pendente do operador.
11. Submeta documento ao coordenador e operador. Marque revisão como aguardando aprovação; somente após aceite explícito registrar aprovado, versão/hash e liberar T28–T30. T27 pode produzir plano em paralelo sem editar este documento sem reserva.

## Arquivos envolvidos e permissões
Criação/edição de docs/resolucao_duplicatas_arquitetura.md, eventos T26; leitura irrestrita. Não alterar código nem testes de implementação nesta tarefa. HEADINGS e assinaturas são propostas documentais, não APIs existentes.

## Restrições específicas
Não criar arquitetura genérica por estética; classes novas só com responsabilidade/evidência. Não apresentar rule memory 2D ou suporte geração WFG como confirmado sem binário/fonte. Nenhuma exclusão ArtifactResolver nesta tarefa.

## Critérios objetivos de aceitação
Quatro focos analisados e duplicações adicionais verificadas; mapa completo de responsabilidades e consumidores; assinaturas/baseDir/DTO/manifesto coerentes; nenhum ciclo util-study; testes definidos; aprovação explícita do operador registrada antes da implementação.

## Testes e verificações necessários
Revisão de grafo de dependências e rastreabilidade de cada método/teste; casos de compatibilidade e contraprovas matemáticas documentados; não alegar código compilado para uma assinatura apenas proposta.

## Entregáveis
docs/resolucao_duplicatas_arquitetura.md criado pelo agente, mapa de migração/depreciação e evento de aprovação/bloqueio para HANDLER.

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

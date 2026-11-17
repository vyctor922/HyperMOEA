# T30 — Migração dos consumidores e manifesto dos artefatos efetivos

## Identificação e objetivo
- Arquivo proposto: tasks/task30.md.
- Prioridade: P0.
- Dependências: T29 validada; T26 aprovada.
- Estado inicial: pending; confirmar numeração e estado na IDE.

Migrar consumidores e a construção/exportação do manifesto para as fontes de verdade, deixando WFGStudy como orquestrador e preparando a exclusão segura de ArtifactResolver.

## Contexto e evidências disponíveis
Preflight, factories, profiling e EvaluationBudgetProblem devem compartilhar resultado resolvido, não repetir seleção/validação com outra política. O corpo de exportActiveArtifactManifestCsv e ArtifactManifestEntry deve ser encontrado na IDE; não presumir pacote ou existência por nome.

Snapshot mostra WFGStudy construir String path de PF, resolver pesos em builder e novamente em validateMoeadWeightFile e checar existência de PF no preflight. Alterar só resolver não remove consumidores duplicados nem fallback secundário.

## Fundamentação técnica
## Referências e limites da fundamentação
- Martin Fowler, Refactoring, definição e transformações pequenas com preservação de comportamento: https://refactoring.com/ . A retirada de fallback e a nova precedência são mudanças de requisito explícitas, não mera refatoração preservadora de comportamento.
- D. L. Parnas (1972), On the criteria to be used in decomposing systems into modules, Communications of the ACM 15(12), 1053–1058, DOI 10.1145/361598.361623. https://doi.org/10.1145/361598.361623 . Fundamenta discutir critérios de modularização; não determina automaticamente quais classes deste projeto devem ser fundidas.
- Oracle Java SE 21, Files: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html ; MessageDigest: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html . Existência não valida conteúdo; SHA-256 identifica bytes, não prova procedência nem correção matemática.
- Apache Maven Surefire, documentação oficial: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html . Conferir parâmetros na versão do pom real; não atualizar plugin/dependências para coincidir com documentação latest.
- JUnit: utilizar documentação da versão instalada e @TempDir se suportado. O endereço current consultado redireciona para JUnit 6; não é prova de versão do projeto nem autorização para upgrade.
- Para simplex e algoritmos: consultar Das e Dennis (1998), Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems, SIAM Journal on Optimization 8(3), 631–657; Deb e Jain (2014), NSGA-III; formulações DTLZ/WFG pertinentes e source JAR/bytecode jMetal 7.8 realmente resolvido. Nenhuma fórmula, assinatura ou regra de geração em memória do binário foi reproduzida na etapa de planejamento.


## Hipóteses a verificar
Manifesto lista candidatos em vez de selecionados; path/hash se altera entre preflight e consumo; profiling usa política própria; jMetal aceita diretório+nome em vez de path; nome constante FINAL experiment resulta em overwrite de testes.

## Procedimentos ordenados
1. Use inventário T25 e mapa T26: migre cada consumidor, teste, worker, script ativo e reflexão identificada. WFGStudy, ParetoFrontValidator, ReferenceSetValidator, PerformanceProfiler e EvaluationBudgetProblem não podem ter ramo alternativo de resolução. Faça parsing/hash/validação numa operação coesa quando possível; revalidação de integridade justificável no consumo não é duplicação de política.
2. ParetoFrontValidator.resolveCanonicalReferenceFront retorna objeto rico; WFGStudy apenas adapta o path efetivo ao contrato jMetal comprovado, sem fallback/rootRelative manual nem frágil ../../ para outra raiz. Pesos: usar parent/filename exatos esperados por setDataDirectory e algoritmo, verificando convenção real; não re-resolver a partir do diretório de um candidato já selecionado.
3. EvaluationBudgetProblem permanece responsável por contar avaliações. Recebe resolved artifact/configuração/função de preflight injetada conforme arquitetura, sem varredura de arquivos em evaluate nem import do pipeline para decidir matemática. PerformanceProfiler consome o mesmo contexto/resultado canônico e mede etapas reais, não recria geradores.
4. Mova exportActiveArtifactManifestCsv, ArtifactManifestEntry e construção/exportação para ParetoFrontValidator OU ReferenceSetValidator conforme T26. WFGStudy apenas invoca exportação antes de iniciar algoritmos. Helpers internos de serialização/hash só se coesos e aprovados; nenhuma segunda implementação em WFGStudy ou profiler.
5. Manifesto deriva dos objetos resolvidos, não de nova busca. Campos: chave/configuração, origem, path/SHA quando FILE, tipo, m/N, estado/versão da validação, procedência, geração e flags pertinentes. Distinguir validade, integridade e origem. CSV com escaping/codificação, ordem determinística, esquema versionado e pares problema/algoritmo/run quando necessários; evitar repetir dados imutáveis em milhares de linhas sem justificativa.
6. Exportação pré-run informa seleção aprovada/planejada; informar consumo efetivo só após comprovação, sobretudo MOEA/D 2D gerado internamente. Se resultado final divergir/falhar, manter status e atualizar manifesto/finalização conforme contrato aprovado, sem transformar planejamento em observação. Registre modo memória sem Path/SHA de arquivo fictícios.
7. Garanta que os bytes validados/hasheados são os disponibilizados; se API reabre arquivo, definir rechecagem no limite ou snapshot imutável canônico para reduzir TOCTOU conforme risco, sem copiar recursos cegamente. Não realizar validação pesada a cada avaliação.
8. Remova checks legados de preflight, defaults, enums de fontes, fallback root/cwd e utilitários ativos. Testes negativos podem mencionar nomes legados para demonstrar rejeição; histórico e ferramenta offline podem citá-los sem runtime fallback. Busca estática é evidência complementar, não prova completa de não acesso.
9. Execute regressões de todos consumidores e confira plano T26; entregue lista dependências residuais de ArtifactResolver/Test. Não apagar classe/teste ainda; T31 faz gate de exclusão. Atualize documento arquitetural com realização, e eventos para coordenador atualizar HANDLER/README.

## Arquivos envolvidos e permissões
Consumidores identificados, WFGStudy, PerformanceProfiler, EvaluationBudgetProblem, exportador e entrada de manifesto no proprietário aprovado, testes. Janelas exclusivas para classes compartilhadas com T29. Documentos arquiteturais em edição designada; HANDLER/README somente coordenador.

## Restrições específicas
Não incluir legados do experimento nem mudar B/seed/normalização. Testes não gravam resultados em WFGStudy/ fora WFGStudy/tests/. Nunca declarar artefato consumido apenas por linha de manifesto prévia.

## Critérios objetivos de aceitação
Todos consumidores utilizam mesmos contratos/artefatos; manifesto sem resolução paralela e corresponde a paths/hashes reais; IO ausente do evaluate; baseDir propagado a workers; nenhum caminho operacional legado; ArtifactResolver sem responsabilidades exclusivas remanescentes.

## Testes e verificações necessários
Golden schema/escaping CSV, hash arquivo lido e zero duplicação divergente; memória sem path; exportação antes do primeiro run via spy; alteração arquivo entre preflight e consumo conforme política; modos default/flags; profiler vs pipeline selecionam mesmo recurso; contagem B inalterada e cwd alternativo.

## Entregáveis
Migração de consumidores/manifesto e testes; mapa de dependências residuais e situação pronta/não pronta para exclusão T31; atualização arquitetural e eventos.

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

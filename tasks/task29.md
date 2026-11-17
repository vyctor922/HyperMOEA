# T29 — Resolução canônica, retornos estruturados, baseDir e política 2D em memória

## Identificação e objetivo
- Arquivo proposto: tasks/task29.md.
- Prioridade: P0.
- Dependências: T26 aprovada; T28 validada.
- Estado inicial: pending; confirmar numeração e estado na IDE.

Implementar a política resources oficial válido→gerado válido→geração persistida/validada, retornos estruturados e contexto baseDir, sem acesso operacional aos diretórios legados.

## Contexto e evidências disponíveis
WFGStudy.resolveMoeadDataDirectory atual escolhe por Files.exists e recorre a MOEAD_Weights. runPreflightValidation verifica pareto_fronts/csv. O novo requisito substitui essas permissões operacionais; físicos históricos continuam protegidos.

Os retornos existentes de WFGStudy são String para resolução e double[][] para validação de pesos. O método resolveCanonicalReferenceFront é chamado com caminhos e flags; sua implementação deve ser lida na IDE. ArtifactResolver e comportamento jMetal em memória são dependências a examinar, não APIs presumidas.

## Fundamentação técnica
## Referências e limites da fundamentação
- Martin Fowler, Refactoring, definição e transformações pequenas com preservação de comportamento: https://refactoring.com/ . A retirada de fallback e a nova precedência são mudanças de requisito explícitas, não mera refatoração preservadora de comportamento.
- D. L. Parnas (1972), On the criteria to be used in decomposing systems into modules, Communications of the ACM 15(12), 1053–1058, DOI 10.1145/361598.361623. https://doi.org/10.1145/361598.361623 . Fundamenta discutir critérios de modularização; não determina automaticamente quais classes deste projeto devem ser fundidas.
- Oracle Java SE 21, Files: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html ; MessageDigest: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html . Existência não valida conteúdo; SHA-256 identifica bytes, não prova procedência nem correção matemática.
- Apache Maven Surefire, documentação oficial: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html . Conferir parâmetros na versão do pom real; não atualizar plugin/dependências para coincidir com documentação latest.
- JUnit: utilizar documentação da versão instalada e @TempDir se suportado. O endereço current consultado redireciona para JUnit 6; não é prova de versão do projeto nem autorização para upgrade.
- Para simplex e algoritmos: consultar Das e Dennis (1998), Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems, SIAM Journal on Optimization 8(3), 631–657; Deb e Jain (2014), NSGA-III; formulações DTLZ/WFG pertinentes e source JAR/bytecode jMetal 7.8 realmente resolvido. Nenhuma fórmula, assinatura ou regra de geração em memória do binário foi reproduzida na etapa de planejamento.


## Hipóteses a verificar
Artifacts oficiais incompatíveis; generated válido ignorado; path fora baseDir ou symlink em legado; hash/validação a arquivos diferentes; propriedade default falso conflita com geração nativa 2D inevitável; ausência de gerador analítico WFG.

## Procedimentos ordenados
1. Confirme contrato aprovado T26 e antes de remover acessos inventarie/migre somente artefatos necessários. Leitura offline do legado é autorizada apenas para cópia/inventário desta migração, nunca como ramo operacional. Cópia de arquivo histórico não ganha categoria OFFICIAL sem origem confirmada; classificar DERIVED/GENERATED só com história/transformação justificadas, ou bloquear.
2. Construa contexto baseDir explícito; resolva paths relativos contra ele uma vez. WFGStudy recebe contexto no caminho testável; main inicializa uso normal. ParetoFrontValidator recebe baseDir ou contexto aprovado sem dependência de user.dir/global. Preserve propriedades válidas canônicas, rejeite overrides/symlinks que reintroduzam legado ou escapes não aprovados; não fazer varredura de pais/disco.
3. Implemente fonte única para PF via ParetoFrontValidator.resolveCanonicalReferenceFront e para pesos via ReferenceSetValidator com fachada WFGStudy.validateMoeadWeightFile. Retornos ricos equivalentes a ResolvedFrontArtifact/ResolvedWeightArtifact têm path efetivo normalizado, SHA256 dos bytes validados, origem, tipo, m, N, procedência e versão de validação necessários. Campos opcionais corretos para memória; metadados imutáveis defensivos, sem duplicações e sem String path sem contexto.
4. Para cada nível valide leitura/formato/m/N/finitude/matemática/compatibilidade/procedência/hash antes de selecionar. Oficial existente inválido não vence gerado válido; registrar rejeição e seguir categoria posterior conforme contrato. Um candidato inválido não deve ser sobrescrito para reparar; falha de leitura não se transforma em arquivo válido. Todos inválidos e sem gerador adequado => erro descritivo com tentativas, sem fallback legado/empírico silencioso.
5. Na geração por código, usar gerador aprovado para problema/configuração, limites de tamanho/custo e serialização determinística; gravar apenas diretório *_gen correspondente. Validar em temporário, persistir sem publicar arquivo parcial, reler/verificar hash e validar arquivo final antes de disponibilizar. Tratar disputa de criação e suporte de atomic move sem promessa de atomicidade universal. Não sobrescrever oficial ou gerado inválido; nome/versionamento de substituto e regra de descoberta são os aprovados. Reuso não gera repetidamente.
6. Variantes weights_gen e pf_lowzero_gen são derivados explicitamente solicitados, não oficiais nem PF matemática inalterada; preservar flags desativadas. Gerar/validar variante a partir da base canônica sem escalar sua prioridade silenciosamente. Geração empírica somente opt-in e validada; não a usar automaticamente quando gerador matemático não existir. Geração de artefato necessário não habilita problema fora do escopo: documentar interação com generateAllReferenceFronts e persistência opcional de direções.
7. Inspecione bytecode/source de AbstractMOEAD.initializeUniformWeight para m=2,N<=300, incluindo limites N=1,2,300,301 e fórmula/ordem. Transfira decisão isMoeadWeightGeneratedInMemory para WFGStudy.validateMoeadWeightFile como política opcional, propriedade proposta -Dstudy.allowMoeadWeightsInMemory=false, após conferir convenções/colisões. false exige resolver arquivo canônico no preflight; true autoriza dispensar arquivo somente quando condição exata validada do framework se aplica. Não estender a outros algoritmos, GWASFGA ou dimensões por analogia.
8. Se backend gera em memória mesmo com false, a propriedade controla a dispensa do arquivo no preflight, não desabilita magia do framework. Não registrar que arquivo foi consumido quando backend o ignorou. Metadados diferenciam artefato validado/planejado, modo efetivo e condição. Ao habilitar exceção in-memory, registrar explicitamente política e geração; sem path/SHA de arquivo fictícios. Aprovação T26 define representação de vetor/hash canônico em memória, se necessário. Não retornar null/array vazio para fingir sucesso.
9. Adapte APIs antigas necessárias por delegação temporária para o novo resultado; métodos deprecated não mantêm busca legada. Prepare chamadas consumidores e tabela do que será migrado T30. Preserve a contagem de avaliações em EvaluationBudgetProblem: contador não resolve/genera recursos a cada evaluate; recebe configuração pronta.

## Arquivos envolvidos e permissões
ParetoFrontValidator, ReferenceSetValidator, DTOs/contexto aprovados, pontos de entrada WFGStudy relativos a baseDir/pesos, recursos canônicos necessários e testes. WFGStudy sob reserva exclusiva; ArtifactResolver pode ficar como adaptador temporário à nova lógica sem política própria, se aprovado. Sem remoção ainda.

## Restrições específicas
Não reutilizar diretórios históricos no caminho final, inclusive Files.exists/isRegularFile indiretos. Não alterar algoritmo para fingir que flag desliga geração nativa. Sem upgrade, rede em runtime ou provas de hash inventadas. IO/generation não entra em hot path evaluate.

## Critérios objetivos de aceitação
Precedência baseada em validade comprovada; paths legados não consultados; DTO/baseDir e modo memória coerentes; ausência de arquivo só dispensada por opção e condição comprovada; geração persiste e valida; hash/procedência correspondem ao artefato; arquivos históricos intactos.

## Testes e verificações necessários
Matriz oficial válido/ausente/inválido x gerado válido/ausente/inválido x gerador disponível/indisponível; SHA esperado/corrompido; m/N/NaN/duplicatas/soma inválidos; pasta diferente cwd e duas bases simultâneas; paths absolutos/relativos/override legado; link conforme SO; geração concorrente/publicação incompleta; memória flag off/on e 2D limites; zero N inválido; nenhum teste consome dados reais legados.

## Entregáveis
Código/DTOs/contexto e testes de resolução canônica na IDE; mapa de APIs e propriedade memória; evidências para HANDLER e arquitetura atualizada.

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

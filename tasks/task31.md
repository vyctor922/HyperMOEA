# T31 — Exclusão condicionada do ArtifactResolver e validação final

## Identificação e objetivo
- Arquivo proposto: tasks/task31.md.
- Prioridade: P0.
- Dependências: T28–T30 validadas; arquitetura T26 aprovada; plano inicial T27 disponível.
- Estado inicial: pending; confirmar numeração e estado na IDE.

Remover ArtifactResolver/Test e enums sem finalidade apenas após migração provada, executar regressões e encerrar documentação da fase sem iniciar campanha completa.

## Contexto e evidências disponíveis
Exclusão é resultado condicionado, não objetivo que dispense testes equivalentes. ArtifactResolverTest pode conter contratos essenciais que precisam ser migrados antes de apagar o arquivo.

Não se tem binário/árvore completa na etapa de planejamento; ausência de referência em um snapshot não prova remoção segura em todos os módulos. HANDLER/README podem manter nomes históricos sem serem dependências executáveis.

## Fundamentação técnica
## Referências e limites da fundamentação
- Martin Fowler, Refactoring, definição e transformações pequenas com preservação de comportamento: https://refactoring.com/ . A retirada de fallback e a nova precedência são mudanças de requisito explícitas, não mera refatoração preservadora de comportamento.
- D. L. Parnas (1972), On the criteria to be used in decomposing systems into modules, Communications of the ACM 15(12), 1053–1058, DOI 10.1145/361598.361623. https://doi.org/10.1145/361598.361623 . Fundamenta discutir critérios de modularização; não determina automaticamente quais classes deste projeto devem ser fundidas.
- Oracle Java SE 21, Files: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html ; MessageDigest: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html . Existência não valida conteúdo; SHA-256 identifica bytes, não prova procedência nem correção matemática.
- Apache Maven Surefire, documentação oficial: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html . Conferir parâmetros na versão do pom real; não atualizar plugin/dependências para coincidir com documentação latest.
- JUnit: utilizar documentação da versão instalada e @TempDir se suportado. O endereço current consultado redireciona para JUnit 6; não é prova de versão do projeto nem autorização para upgrade.
- Para simplex e algoritmos: consultar Das e Dennis (1998), Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems, SIAM Journal on Optimization 8(3), 631–657; Deb e Jain (2014), NSGA-III; formulações DTLZ/WFG pertinentes e source JAR/bytecode jMetal 7.8 realmente resolvido. Nenhuma fórmula, assinatura ou regra de geração em memória do binário foi reproduzida na etapa de planejamento.


## Hipóteses a verificar
Referências residuais em testes, scripts, manifests de build, recursos, reflexão e módulos; testes verdes por exclusão indevida; geração/IO duplicados sob novos nomes; mudança de resultados além da política aprovada.

## Procedimentos ordenados
1. Gate de exclusão: revisar matriz responsabilidade→sucessor→consumidor→teste aprovada T26 e realizada T28–T30. Para cada teste ArtifactResolverTest, mapear cenário a teste substituto efetivamente executado; se regra legada foi abolida, teste novo demonstra proibição, não preservação indevida.
2. Comprovar ausência de dependências de ArtifactResolver e enums em código compilado ativo, testes, configuração Maven, scripts/runtime e reflexão. Procura textual + análise de referências e build limpo; referências históricas em docs permanecem identificadas. Enum compartilhado ainda útil não é removido por estar no arquivo errado: migrar só com propriedade clara.
3. Se todos critérios forem satisfeitos, excluir ArtifactResolver.java, ArtifactResolverTest.java e enums exclusivos obsoletos; não manter casca vazia nem novo resolver genérico equivalente sem justificativa aprovada. Se dependência residual existir, bloquear exclusão e corrigir tarefa responsável sem forçar conclusão.
4. Executar mvn clean test e verificação de compilação/empacotamento que caminho real exigir; sem skipTests/ignorar falhas. Registrar versão, comandos, código de saída, contagens reais de executados/ignorados e relatórios. Testes direcionados não substituem suíte final; custo HV deve ter limites existentes preservados.
5. Regressões finais: recursos oficiais inválidos não vencem gerados válidos; geração necessária persiste antes de uso; diretórios históricos existentes/ausentes não alteram resultado e não são consultados no runtime; baseDir/cwd distintos, retornos estruturados e manifesto real; in-memory default false e limites comprovados; dominância e geradores sem deriva matemática.
6. Execute smoke reduzido dos consumidores com fixtures/recursos canônicos e outputs em WFGStudy/tests/; mesmos dados/seeds antes/depois quando aplicável. Separe diferenças esperadas pela nova política de fontes de alterações experimentais inesperadas. Não alegar equivalência se entradas/hash mudaram; documentar e submeter impacto científico.
7. Verifique integridade histórica por hashes conhecidos do inventário/cópia, sem atribuir ao pipeline acesso aos legados para fazer essa checagem. Não deletar artefatos físicos antigos. Registre testes instrumentados e limites da verificação de não acesso (inclusive atalhos/symlinks/overrides).
8. Atualize na IDE docs/resolucao_duplicatas_arquitetura.md com realizado/deprecated/removido/pendente; forneça ao agente T27 resultados para revisão final de docs/plano_mitigacao_divida_tecnica.md. T27 conclui após receber estes resultados, sem ciclo bloqueando T31.
9. Coordenador consolida HANDLER e README com evidências e histórico no formato AGENTS.md, registra revisão de decisões anteriores e aprovação/bloqueios. Não alterar Git. Sem documentos adicionais de diagnóstico/matriz paralelos; usar HANDLER para quadro atual e docs/history para logs.

## Arquivos envolvidos e permissões
Exclusão condicionada de ArtifactResolver/Test/enums exclusivos, ajustes finais estritamente necessários nos consumidores/testes, documentos arquitetura/plano sob coordenação. HANDLER/README só coordenador. Demais defeitos exigem retorno à task responsável.

## Restrições específicas
Não apagar testes sem substituto/justificativa; não limpar resources ou dados históricos; não tornar campanha automática; não inventar contagem de testes ou aprovação. Descoberta científica adicional não autoriza mudança silenciosa.

## Critérios objetivos de aceitação
Responsabilidades migradas e testes substitutos aprovados; ausência de dependências executáveis residuais; paths legados não consultados; suíte limpa e smokes aprovados; matemática preservada salvo decisões explícitas; documentação e contexto atualizados; se qualquer obrigatório faltar, excluir/homologar fica bloqueado.

## Testes e verificações necessários
Suíte integral, testes canônicos/negativos/geração/hash/baseDir/DTO/manifesto/dominância/memória/consumidores; verificação estática e execução instrumentada adequadas; checksums de históricos; saída só temporária ou tests/.

## Entregáveis
Exclusão segura na IDE se elegível; testes/regressões e logs reais; documentos produzidos na IDE atualizados; resumo final do coordenador no HANDLER/README e risco residual.

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

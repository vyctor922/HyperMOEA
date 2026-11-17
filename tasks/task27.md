# T27 — Plano de mitigação da dívida técnica baseado em evidências

## Identificação e objetivo
- Arquivo proposto: tasks/task27.md.
- Prioridade: P1.
- Dependências: T25; alinhar com T26; conclusão atualizada após T31.
- Estado inicial: pending; confirmar numeração e estado na IDE.

Produzir na IDE docs/plano_mitigacao_divida_tecnica.md e acompanhar sua atualização sem criar backlog de refatorações hipotéticas.

## Contexto e evidências disponíveis
HANDLER deve manter plano inicial resumido; documento detalhado é entrega posterior desta tarefa. Duplicação relatada e legacy fallback não demonstram por si só todas as dívidas da arquitetura.

O requisito novo muda política de fontes; preservar comportamento experimental implica comparar metadados e saídas, sem exigir manter fallback expressamente proibido. Escala de esforço não equivale a prazo prometido.

## Fundamentação técnica
## Referências e limites da fundamentação
- Martin Fowler, Refactoring, definição e transformações pequenas com preservação de comportamento: https://refactoring.com/ . A retirada de fallback e a nova precedência são mudanças de requisito explícitas, não mera refatoração preservadora de comportamento.
- D. L. Parnas (1972), On the criteria to be used in decomposing systems into modules, Communications of the ACM 15(12), 1053–1058, DOI 10.1145/361598.361623. https://doi.org/10.1145/361598.361623 . Fundamenta discutir critérios de modularização; não determina automaticamente quais classes deste projeto devem ser fundidas.
- Oracle Java SE 21, Files: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html ; MessageDigest: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html . Existência não valida conteúdo; SHA-256 identifica bytes, não prova procedência nem correção matemática.
- Apache Maven Surefire, documentação oficial: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html . Conferir parâmetros na versão do pom real; não atualizar plugin/dependências para coincidir com documentação latest.
- JUnit: utilizar documentação da versão instalada e @TempDir se suportado. O endereço current consultado redireciona para JUnit 6; não é prova de versão do projeto nem autorização para upgrade.
- Para simplex e algoritmos: consultar Das e Dennis (1998), Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems, SIAM Journal on Optimization 8(3), 631–657; Deb e Jain (2014), NSGA-III; formulações DTLZ/WFG pertinentes e source JAR/bytecode jMetal 7.8 realmente resolvido. Nenhuma fórmula, assinatura ou regra de geração em memória do binário foi reproduzida na etapa de planejamento.


## Hipóteses a verificar
Acoplamento excessivo, inconsistência de resolução, IO no preflight/contador, ausência de baseDir, DTOs frágeis, duplicação de hash/dominância/geração, teste dependente do workspace e manifesto de candidato em vez de consumo.

## Procedimentos ordenados
1. Leia T25 e evidências locais; não recriar inventário/diagnóstico separado. Para cada item exigir exemplo concreto, consumidor e consequência observável ou risco tecnicamente explicado.
2. Classifique dívida comprovada, defeito funcional, mudança de requisito e melhoria futura. Registrar origem conhecida ou desconhecida; não inventar histórico/culpa.
3. Para cada item preencher ID estável, evidência método/arquivo/teste, causa, classes, impacto científico/operacional/manutenção, prioridade P0/P1/P2 justificada, regressão, solução localizada, alternativa, dependências, esforço relativo S/M/L e critério verificável de encerramento.
4. Priorizar resolução canônica válida, eliminação de buscas legadas, posse de responsabilidade, testes de baseDir, manifesto real e substituição dos testes ArtifactResolver. Não propor fusão de classes matematicamente distintas para reduzir número de arquivos.
5. Alinhar cada intervenção implementável às T28–T31 e arquitetura T26 aprovada. Melhorias fora do escopo vão a backlog explícito, não se tornam dependência obrigatória da refatoração.
6. Entregar documento inicial após T25, revisão após aprovação T26 e atualização final após T31. Essa atualização final não bloqueia início de T31 nem cria ciclo de dependências. Coordenador pode homologar código T31 antes de consolidar o fechamento documental desta tarefa.
7. Registrar no evento para HANDLER resumo de prioridades, itens abertos/resolvidos, evidência de conclusão, risco residual e próxima ação. Não declarar dívida encerrada pela simples remoção de uma classe.

## Arquivos envolvidos e permissões
docs/plano_mitigacao_divida_tecnica.md e eventos T27; leitura docs/arquitetura/código/testes. HANDLER/README exclusivos do coordenador. Sem alteração de produção.

## Restrições específicas
Não ampliar escopo científico; não criar métricas artificiais de qualidade nem estimar horas sem base. Plano não substitui aprovação arquitetural T26.

## Critérios objetivos de aceitação
Todos os itens com evidência/status e distinção dívida/melhoria; prioridades justificadas; dependências acíclicas; critérios de fechamento; documento final correspondente ao código homologado e ao risco residual.

## Testes e verificações necessários
Conferência cruzada item→evidência→task→teste→status; revisão de riscos e dependências; verificar arquivos relatados realmente existentes.

## Entregáveis
docs/plano_mitigacao_divida_tecnica.md e resumo periódico para HANDLER; nenhuma implementação de refatoração nesta tarefa.

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

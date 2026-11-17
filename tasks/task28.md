# T28 — Centralização de primitivas matemáticas e validações reutilizáveis

## Identificação e objetivo
- Arquivo proposto: tasks/task28.md.
- Prioridade: P1.
- Dependências: T26 aprovada pelo operador; T25 concluída.
- Estado inicial: pending; confirmar numeração e estado na IDE.

Remover somente duplicações comprovadas de dominância, simplex e validação matemática, preservando separação entre pesos/direções/PF e comportamento experimental.

## Contexto e evidências disponíveis
Aprovação T26 define os proprietários exatos. Esta tarefa prepara dependências usadas na resolução canônica T29 sem reorganizar todas as classes de utilidades.

No planejamento não se teve acesso ao corpo de ReferenceSetValidator, ParetoFrontValidator ou ResultFrontContract. Portanto nenhuma assinatura/duplicação neles deve ser tratada como confirmada por esta task.

## Fundamentação técnica
## Referências e limites da fundamentação
- Martin Fowler, Refactoring, definição e transformações pequenas com preservação de comportamento: https://refactoring.com/ . A retirada de fallback e a nova precedência são mudanças de requisito explícitas, não mera refatoração preservadora de comportamento.
- D. L. Parnas (1972), On the criteria to be used in decomposing systems into modules, Communications of the ACM 15(12), 1053–1058, DOI 10.1145/361598.361623. https://doi.org/10.1145/361598.361623 . Fundamenta discutir critérios de modularização; não determina automaticamente quais classes deste projeto devem ser fundidas.
- Oracle Java SE 21, Files: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html ; MessageDigest: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html . Existência não valida conteúdo; SHA-256 identifica bytes, não prova procedência nem correção matemática.
- Apache Maven Surefire, documentação oficial: https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html . Conferir parâmetros na versão do pom real; não atualizar plugin/dependências para coincidir com documentação latest.
- JUnit: utilizar documentação da versão instalada e @TempDir se suportado. O endereço current consultado redireciona para JUnit 6; não é prova de versão do projeto nem autorização para upgrade.
- Para simplex e algoritmos: consultar Das e Dennis (1998), Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems, SIAM Journal on Optimization 8(3), 631–657; Deb e Jain (2014), NSGA-III; formulações DTLZ/WFG pertinentes e source JAR/bytecode jMetal 7.8 realmente resolvido. Nenhuma fórmula, assinatura ou regra de geração em memória do binário foi reproduzida na etapa de planejamento.


## Hipóteses a verificar
Núcleo combinatório compartilhável; fórmula de transformação específica; dominância com igualdade/tolerância diferente; parsing/hash repetido; normalização que compartilha nome mas muda contrato.

## Procedimentos ordenados
1. Confirme versão/hash da arquitetura aprovada e reserve classes/testes exatos. Adicione fixtures de caracterização antes da mudança necessária, sem copiar implementações duplicadas para produzir oráculos iguais.
2. Centralize o núcleo de dominância aprovado, preferindo recurso jMetal 7.8 comprovadamente adequado. ParetoFrontValidator/ResultFrontContract delegam a mesma regra; filtragem e metadados de resultado permanecem separados. Defina igualdade e não dominância sem confundir duplicata com dominância estrita.
3. Centralize enumeração combinatória simplex apenas quando semântica, camadas e precisão forem equivalentes. Mantenha transformações RVEA/NSGA-III e geometria analítica DTLZ/WFG em responsabilidades próprias. Comparação por conjuntos pode tolerar ordem diferente só se algoritmo consumidor não depender da ordem; pesos de vizinhança podem depender.
4. Centralize critérios de matriz conforme domínio: dimensionalidade, quantidade esperada, finitude, não negatividade, soma/norma, duplicatas. Reutilize infraestrutura mínima; não imponha soma=1 a PF ou norma L2=1 a todo peso. SHA/parsing helpers só se duplicação comprovada em T26.
5. Faça delegações transitórias quando há consumidores reais; marque @Deprecated com substituto e critério de remoção se necessário. Não apagar APIs por preferência pessoal nem preservar implementações paralelas sob wrappers.
6. Teste conjuntos pequenos exatos, m=2,3,5,8,10,15 e entradas inválidas sem custo exponencial irrestrito. Verifique não mutação dos arrays e não alteração de RNG. Substituir erro científico encontrado exige proposta separada, não mascarar como deduplicação.
7. Entregar testes e notas de migração; atualizar documento arquitetural apenas no trecho de realização designado pelo coordenador, sem escrita concorrente T26/T29. Liberar contratos para T29.

## Arquivos envolvidos e permissões
Classes indicadas na arquitetura entre ReferenceSetValidator, ParetoFrontValidator, ResultFrontContract, adaptador matemático mínimo aprovado e testes próprios. WFGStudy somente leitura nesta tarefa; reservar validadores compartilhados. Docs de arquitetura apenas atualização autorizada; eventos T28.

## Restrições específicas
Não alterar diretórios operacionais nem remover ArtifactResolver ainda; não modificar biblioteca jMetal/JAR; não mudar normalização/HV/contratos dos resultados como efeito lateral.

## Critérios objetivos de aceitação
Uma implementação por regra comprovadamente igual; delegações documentadas; comportamentos e ordem relevante preservados; matemática distinta separada; todos testes pertinentes aprovados ou bloqueio.

## Testes e verificações necessários
Dominância estrita/igualdade/incomparável e sentido; vazios, NaN, m divergente, tolerância aprovada; contagens simplex, camada dupla, extremos e duplicatas; testes golden de geradores e consumidores; mvn clean test na janela coordenada.

## Entregáveis
Código e testes de centralização mínima na IDE; atualização de realização arquitetural e evidências para HANDLER.

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

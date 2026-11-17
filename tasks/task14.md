# T14 — Identidade do FDEA e governança de algoritmos principais e legados

## Identificação, prioridade e dependências
Arquivo: tasks/task14.md. Prioridade: P0. Dependências: T12.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Confirmar fidelidade do FDEA e separar perfil principal dos legados sem apagá-los.

## Contexto e problema
WFGStudy.buildFDEA injeta BinaryTournamentSelection com RankingAndCrowdingDistanceComparator. O código FDEA não foi anexado; o README confunde fuzzy Pareto optimality com decomposição fuzzy. Cinco legados são adicionados incondicionalmente.

## Fundamentação científica e técnica
R8 descreve o algoritmo de decomposição fuzzy; P2 é a referência de MOEA/DD, não o trabalho de dois arquivos atribuído em respostas anteriores. P4 não confirma ano de AGE-II.

## Hipóteses a verificar
FDEA original vs outro algoritmo de mesma sigla; ranking/crowding ausentes no torneio; java.util.Random fora da seed; reprodução/filhos incorretos; SPEA2 sem população configurada; DRA necessita DE; GWASFGA inverte zeros.

## Procedimentos ordenados
1. Localize FDEA.java, FDEABuilder, FDEAIndividual, MSBS/LSBS e repositório original com licença/commit. Produza mapa função→pseudocódigo/seção do artigo correto; confirme autores e título.
2. Verifique seleção, desempates, inicialização, fitness fuzzy, reprodução, filhos e result. Compare traços pequenos com a fonte original. Trocar comparador por dominância arbitrária não é correção aceitável.
3. Audite todas as fontes aleatórias. Se usar JMetalRandom, aplique arquitetura isolada aprovada na T15; não redefina singleton por worker concorrente.
4. Proponha includeLegacyAlgorithms=false como padrão do perfil novo após aceite de escopo T12. true preserva a funcionalidade histórica, mas não deve executar combinações sabidamente inválidas.
5. Audite SPEA2: populationSize é incrementado localmente mas não passado ao builder. Audite tipo DE exigido e efetivamente entregue a MOEA/D-DRA. Em GWASFGA, confirme WeightVectors.invert; zero requer tratamento localizado ou variante de pesos derivada rastreável.
6. Confirme quais parâmetros MOEADD realmente utiliza: não deduza que nr=2 tem efeito só porque o builder aceita o setter. Liste valores efetivos, ignorados e defaults.
7. Corrija somente defeitos confirmados no FDEA e no caminho legado necessário; submetendo alterações matemáticas ao gate. Não reescreva operadores para uniformizar indiscriminadamente os algoritmos.

## Arquivos permitidos
Fontes FDEA e testes exclusivos; legados/adaptadores reservado; documentação docs/auditoria/algorithm-identity.md. Integração WFGStudy pela T23.

## Restrições e gates
Não alterar pacote por renomeação do projeto; não restaurar HypE sem aprovação; defeito em legado opcional não deve tornar obrigatório rodá-lo na campanha.

## Critérios objetivos de aceitação
Identidade FDEA verificável; teste de seleção/filhos; mapa RNG; seis algoritmos por padrão aprovado e cinco legados opcionais; falha clara em configuração inválida.

## Testes necessários
Torneio com atributos presentes/ausentes; contagem de filhos ímpar/par; seeds repetidas; teste negativo GWASFGA com zeros; perfil principal/legado sem IDs duplicados.

## Entregáveis
Relatório de identidade, matriz parâmetros efetivos e patches mínimos aprovados.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

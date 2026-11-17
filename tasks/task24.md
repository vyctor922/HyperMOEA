# T24 — Dossiê científico em português e atualização da dissertação

## Identificação, prioridade e dependências
Arquivo: tasks/task24.md. Prioridade: P1. Dependências: T12 para estrutura; T13–T23 para consolidação final.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Produzir justificativa metodológica rastreável, sem inventar fundamentação retrospectiva ou confundir implementação com método publicado.

## Contexto e problema
Dissertação anterior inclui HypE, descreve FDEA com atribuição inconsistente, iguala parâmetros indiscriminadamente e traz ponto HV como nadir. A figura 4.1 também precisa ser reconciliada com a estratégia realmente implementada.

## Fundamentação científica e técnica
P1–P11, R1–R8; cada fonte precisa ser lida para sustentar a afirmação específica, e não apenas constar na bibliografia. Resultados desta fase determinam valores finais.

## Hipóteses a verificar
LLHs são MOEAs inteiros ou operadores/componentes; implementação reinicia algoritmo em vez de continuation; recompensa R2 muda por normalização; parâmetros do método mais recente usados como justificativa universal.

## Procedimentos ordenados
1. Crie docs/methodology/ com arquivos: scope.md, algorithms-parameters.md, benchmarks-reference-artifacts.md, normalization-indicators.md, statistics.md, reproducibility-performance.md, limitations.md e evidence-matrix.csv.
2. Para cada escolha, registrar definição/função, valor/regra, fonte exata/seção/tabela, trecho de código, status (publicado/derivado/engenharia/hipótese), alternativas, motivo e limitação. Não atribuir eta_c=30 a todos os artigos sem conferir.
3. Propor patch separado da dissertação: HypE→MOEA/DD e AGE-II conforme aceite; corrigir identidade FDEA; separar orçamento igual de hiperparâmetros iguais; corrigir referência HV vs nadir e orientação minimização das fórmulas; corrigir independência/pareamento estatístico.
4. Explicitar objetivos do estudo: etapa atual compara MOEAs isolados; adaptação HH posterior não está validada só porque observer R2 existe. Definir o que será LLH, estado transferido, fatia de avaliações e crédito por custo na fase posterior; não implementar HH neste trabalho.
5. Especificar R2 temporal: pesos, ideal e escala fixos no intervalo comparado; sinal DeltaR2 não é melhoria automaticamente se referência mudou. Não prometer ganho simultâneo uniforme de diversidade e convergência a partir de um único indicador.
6. Registrar limitações: densidade PF e m15, DTLZ degenerado, escolha de r e escalas, identidades de implementações, B/filhos, custo/erro MC, multiplicidade/poder 30 runs, viés de referência empírica e ambiente.
7. Atualizar README somente após aceite T23, preservando histórico; compactar HANDLER com referências de arquivo/commit/log, mantendo última situação acionável. Publicações AGE-II/FDEA/MOEADD devem ter metadados confirmados antes de fechar dossiê.

## Arquivos permitidos
docs/methodology/, docs/decisions/, patches textuais de dissertação; README reservado após T23; eventos HANDLER. Não editar fontes algorítmicas.

## Restrições e gates
Não inserir resultados esperados como observados; revisão acadêmica requer aceite do autor/orientador; figura não deve dizer DOS/IM se não representam mecanismo LAST-RL demonstrado.

## Critérios objetivos de aceitação
Escolhas têm evidência/limite explícito; documentos coerentes com manifesto efetivo; fontes não confirmadas marcadas; pendências acionáveis; nenhum resultado inventado.

## Testes necessários
Checklist cruzado código→manifesto→texto→referências; referências resolvíveis; revisão independente de fórmulas e estatística; conferência histórico README.

## Entregáveis
Dossiê metodológico, matriz evidências, lista de correções da dissertação e resumo de aceite/bloqueios.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

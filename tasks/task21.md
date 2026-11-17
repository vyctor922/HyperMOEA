# T21 — Inferência estatística, Kruskal–Wallis e pós-testes

## Identificação, prioridade e dependências
Arquivo: tasks/task21.md. Prioridade: P1. Dependências: T12 para desenho; T13/T15/T17/T20 antes de inferência real.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Produzir análise estatística reproduzível compatível com independência, blocos e erro de medição.

## Contexto e problema
Pipeline atual gera Wilcoxon e Friedman; não contém etapa Kruskal–Wallis visível. Dissertação mistura ordem dos dados com pareamento. Trinta runs não provam independência nem poder suficiente.

## Fundamentação científica e técnica
R1: KW sobre grupos; R2: Holm FWER; P11: Dunn. KW não é universalmente teste de medianas se formatos/dispersões diferirem. Ordem das linhas não determina Friedman.

## Hipóteses a verificar
Runs independentes por algoritmo; seeds comuns geram dependência; indicadores replicados não são novos runs; aproximação MC introduz correlação; relatórios eliminam silenciosamente falhas.

## Procedimentos ordenados
1. Declare unidade: run independente por algoritmo dentro de problema/m/indicador. Audite manifesto RNG e MC. Se grupos dependentes, bloquear KW ingênuo e aprovar desenho pareado/blocado ou modelo apropriado; não decidir por posição do CSV ou mesma seed numérica apenas.
2. Pré-registre teste bilateral, alpha proposto 0.05, famílias de hipóteses e indicadores primário/secundários. Defina controle entre problemas/m/indicadores além do Holm dentro de cada célula; para conclusões globais não agregar todos os runs como amostras independentes. Friedman pode usar problema/m como blocos com resumos pré-especificados, não trinta runs arbitrariamente pareados.
3. Se KW válido: ranquear conjunto agrupado com midranks, H0=12/[n(n+1)]*sum(R_g²/n_g)-3(n+1), C=1-sum(t³-t)/(n³-n), H=H0/C. C=0 (todos iguais) deve ter tratamento explícito compatível com oráculo, não NaN silencioso. p via qui-quadrado k-1 ou permutação validada em amostras inadequadas.
4. Preferir implementação R testada como motor: stats::kruskal.test e pós-teste Dunn com correção de empates e stats::p.adjust(...,holm). Fixar pacote/versão de Dunn (por exemplo dunn.test se aprovado), não adicionar dependência sem avaliação. Java pode gerar scripts/tabelas; não é necessário reinventar distribuição qui-quadrado.
5. Definir pós-teste e gate omnibus de antemão. Reportar H, df, n por grupo, p cru/ajustado, comparações, direção melhor/pior e método. Escolher efeito omnibus com fórmula/referência verificada e efeito par-a-par rank-biserial/Cliff com IC e orientação; não alternar estimadores epsilon² e eta² chamando-os iguais.
6. Exportar dados longos, CSV resumo, LaTeX e scripts R com sessionInfo. Incluir mediana/IQR, distribuições e efeito; não ranking por p. Política para falhas/timeout/NA e runs faltantes deve ser visível; sem completar com zeros ou excluir algoritmos ruins.
7. Testar transformação NHV=1-HV/c com c fixo: KW H/p mantém-se por reversão dos postos e empates; direção dos efeitos muda. Isso não vale para c variável por run.
8. Corrigir documentação da dissertação sobre signed-rank vs rank-sum: um é pareado, outro independente. Não modificar texto acadêmico sem patch separado aprovado.

## Arquivos permitidos
Gerador estatístico em pacote próprio, scripts R e fixtures exclusivos; relatórios docs/statistics/. Sem tocar algoritmos e métricas.

## Restrições e gates
Nenhuma inferência de 30 seeds sem verificar manifesto; C=0 ou NA não mascarados; multiplicidade aprovada antes dos resultados.

## Critérios objetivos de aceitação
Tabelas reprodutíveis, H/empates/p e Holm conferidos contra R; unidade/famílias documentadas; IC/efeito com referência; sem pseudo-replicação.

## Testes necessários
Exemplo oficial R1 (2.9,3.0,2.5,2.6,3.2 etc.); empates, todos iguais, grupos diferentes/NA, ordem trocada, um grupo ausente, NHV com c fixo/variável; fixture Dunn oracle versionado.

## Entregáveis
docs/statistics/analysis-plan.md, scripts R, CSV/LaTeX, fixtures e adaptador GenerateKruskalWallisTestTables somente se necessário.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

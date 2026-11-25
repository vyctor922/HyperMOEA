# TASK_COORDINATOR — ORQUESTRAÇÃO MULTIAGENTE DO PROJETO hypeMOEA

## Papel do coordenador

Você é o coordenador de agentes de pesquisa e implementação do projeto hypeMOEA, executado no Antigravity IDE.

Atue como Engenheiro de Software Sênior e pesquisador especializado em Java, arquitetura, refatoração, testes automatizados, otimização multiobjetivo e jMetal.

Sua responsabilidade é:

- Compreender o estado real do projeto.
- Distribuir tarefas e controlar dependências.
- Definir ownership e evitar conflitos de edição.
- Prevenir alterações concorrentes conflitantes.
- Exigir evidências técnicas e científicas.
- Preservar o funcionamento experimental.
- Coordenar a migração das responsabilidades.
- Consolidar o contexto operacional da fase no `HANDLER.md`.
- Atualizar o `README.md` quando houver alteração efetiva ou quando alterações anteriores não tiverem sido registradas.
- Verificar critérios de aceitação, testes de validação e documentação antes de concluir tarefas.

O foco é a Fase 5: integrar ao pipeline real os componentes já produzidos, corrigir os impedimentos demonstrados e preparar a homologação da primeira campanha de MOEAs isolados.

Não reinicie fases anteriores. Não repita auditorias concluídas sem evidência ou solicitação nova. Não inicie a campanha experimental completa nem a adaptação das hiper-heurísticas nesta fase.

Quando houver suporte real a subagentes, distribua atividades independentes. Quando esse suporte não estiver disponível, execute sequencialmente, respeitando as mesmas dependências e critérios.

A existência de arquivos Markdown não ativa automaticamente a execução multiagente.

---

## Documentos e responsabilidades

| Documento | Função |
|---|---|
| `AGENTS.md` | Regras obrigatórias do projeto |
| `TASK_COORDINATOR.md` | Planejamento, decisões, tarefas, dependências e rastreabilidade da fase atual |
| `HANDLER.md` | Histórico, estado operacional, evidências e continuidade entre agentes |
| `tasks/taskX.md` | Especificação técnica e critérios de aceitação de cada tarefa |
| `README.md` | Documentação técnica e científica definitiva |
| Código, testes, recursos e logs reais | Evidência do comportamento efetivamente implementado |

Respeite a ordem de prioridade de ordens estabelecida no `AGENTS.md`.

Este coordenador não autoriza ignorar regras superiores nem transfere ao coordenador decisões que dependam do operador.

Em caso de conflito:

1. Identifique a divergência.
2. Consulte as tarefas, o código e os registros relacionados.
3. Suspenda somente a operação afetada.
4. Apresente ao operador a divergência, suas implicações e uma recomendação.
5. Registre a decisão no `HANDLER.md`.
6. Permita a continuidade das atividades independentes.

Não invente arquivos, APIs, resultados, referências ou decisões.

Não utilize documentos históricos como autorização para contrariar uma instrução atual explícita do operador.

---

## Protocolo multiagente

Todos os agentes devem:

1. Ler `AGENTS.md`, `HANDLER.md`, `README.md`, este coordenador e as tarefas pertinentes.
2. Confirmar atribuição, dependências e arquivos autorizados.
3. Registrar início, bloqueio, retomada e entrega.
4. Produzir evidências e resultados verificáveis.
5. Submeter entregáveis à validação do coordenador.

Somente o coordenador edita:

- `HANDLER.md`;
- `README.md`.

Os agentes produzem conteúdo e evidências para documentação, mas não editam diretamente esses arquivos.

Cada agente registra eventos em arquivos temporários exclusivos:

```text
docs/<ID_tarefa>-<agente>-<evento>.md
```

O coordenador consolida os eventos, preserva evidências relevantes e só então remove os arquivos temporários.

O encerramento da participação de um agente não equivale à aprovação da tarefa.

---

## Estado inicial da Fase 5

A Fase 5 foi planejada em 17/11/2026. Nenhum código, teste Maven/Java, alteração funcional ou documentação técnica definitiva do README foi produzido nesta preparação.

O HANDLER registra a conclusão das Fases 1–4 e de T25–T31. O relatório de auditoria posterior relata falhas e lacunas de integração que precisam ser reproduzidas e delimitadas; ele não substitui o histórico nem autoriza apagar conclusões anteriores.

T32 e T33 são mencionadas pelo relatório de auditoria. A existência, numeração e escopo das respectivas tarefas devem ser confirmados antes de depender delas.

As tarefas T32–T36 são propostas para esta fase. Antes de criá-las, confira os arquivos reais de `tasks/` para evitar colisão, duplicação ou sobreposição com T32/T33 e demais tarefas existentes.

### Materiais de referência

Consulte, quando disponíveis:

- `AGENTS.md`;
- `HANDLER.md`;
- `README.md`;
- `TASK_COORDINATOR.md`;
- arquivos de `tasks/`;
- `WFGStudy.java`;
- `StaticParams.java`;
- `relatorio_auditoria-pre_execucao_final.md`;
- POM, dependências, fontes, testes, scripts, recursos, logs e manifestos reais.

Não presuma que todos os arquivos mencionados existem. Registre ausências e limite as conclusões ao material efetivamente verificado.

### Ambiente e regras

Confirme Java 21, Maven e jMetal 7.8 no ambiente e no POM.

Não atualize versões, dependências ou plugins sem autorização explícita.

Utilize Git somente para leitura e checagens. Não crie commits, branches, worktrees ou altere histórico.

Preserve os diretórios protegidos:

- `MOEAD_Weights/`;
- `pareto_fronts/`;
- `pareto_fronts/csv/`;
- `pareto/`.

Não modifique, mova, renomeie, reformate ou sobrescreva arquivos históricos.

O código ativo não deve ler, buscar, verificar existência ou fazer fallback para `MOEAD_Weights/` ou `pareto_fronts/csv/`. A leitura offline estritamente necessária ao inventário e migração é atividade separada do runtime.

Não use a cópia externa modificada do jMetal como fonte oficial. Fixe versão, commit e hash da fonte oficial quando necessário.

---

## Evidências e decisões ativas

Classifique cada constatação como:

- `CONFIRMADA`: evidência direta e suficiente.
- `PARCIALMENTE CONFIRMADA`: evidência incompleta.
- `CONTRADITA`: incompatível com evidência disponível.
- `NÃO VERIFICÁVEL`: sem elementos suficientes.

Classifique a afirmação exata, não um módulo inteiro. Código inspecionado demonstra implementação estática, não execução. Resultado relatado permanece atribuído ao relatório até reprodução.

| ID | Constatação | Classificação | Decisão / encaminhamento | Tarefa |
|---|---|---|---|---|
| F5-01 | Coordenador anterior mantém planejamento T25–T31; HANDLER registra conclusão; relatório usa títulos diferentes para alguns IDs | CONFIRMADA e RESOLVIDA na T32/A5 | IDs `T01`–`T37` reconciliados; operador confirmou mapeamento `T32 -> T33` e `T33 -> T37` sem criar `task32.md` nem `task33.md` (`A5`) | T32/T33/T37 |
| F5-02 | `WFGStudy.main()` usa componentes padrão do jMetal e não demonstra chamada direta aos contratos/executor/HV híbrido/estatística/manifesto | CONFIRMADA e RESOLVIDA em T33–T37 | Integrado e provado no entrypoint real via `mvn test` e `mvn exec:java` (`IsolatedJobExecutor`, `EvaluationBudgetProblem`, `ResultFrontContract`, `ObjectiveNormalizationContract`, `ProcessIsolatedHypervolumeExecutor`, `StudyStatisticalAnalyzer`, manifestos pré/pós) | T33–T37 |
| F5-03 | `study.outputDirectory` é usado como nome do experimento; falha em subdiretórios é relatada | CONFIRMADA em bytecode e RESOLVIDA na T32 | Implementado `WFGStudy.resolveStudyOutputLayout` separando `parentBaseDirectory` e `experimentName` simples; validado em testes e CLI (`WFGStudy/tests/...`) | T32/T37 |
| F5-04 | Relatório informa 373 testes/7 falhas; HANDLER informa 370/0 | CONFIRMADA (`373` testes / `7` falhas reproduzidas) e RESOLVIDA na T32 | `MoeaddWeightValidationTest` corrigido separando artefatos canônicos ativos, auditoria forense imutável e manifesto SHA-256 oficial; suíte final `mvn clean test` = `394/0` | T32/T37 |
| F5-05 | `StaticParams` omite DTLZ5/6 em 10/15 objetivos e retorna 1000; README indica 1500/2000 | CONFIRMADA e RESOLVIDA na T33 (`A1`) | Aprovada pelo operador (`A1`) e implementada em `StaticParams.getMaxGenerations`: `{1000, 1000, 1000, 1000, 1500, 2000}` em `m={2,3,5,8,10,15}` | T33 |
| F5-06 | Construção antecipada de algoritmos e uso de 8 cores podem afetar orçamento e RNG | CONFIRMADA e RESOLVIDA na T33 (`A2`) | Implementada Opção 1 com lote parcial válido nos 6 algoritmos ($B_{\text{target}} = N_{\text{config}} \times G_{\text{config}}$, proibidos objetivos fictícios/herdados, rejeição de $B < N_{\text{alloc}}$) e integração de `IsolatedJobExecutor` | T33 |
| F5-07 | `resolveReferenceFrontForStudy` usa diretório padrão na resolução e propriedade apenas depois | CONFIRMADA e RESOLVIDA na T34 | Overrides propagados hermeticamente com `baseDir`, conteúdo validado antes da seleção nas Prioridades 1/2 e fronteira corrigida para `m == 2 && 2 <= N <= 300` | T34 |
| F5-08 | Manifesto desatualizado, reset entre lotes e gerador WFG ausente são relatados | CONFIRMADA e RESOLVIDA em T33/T34 | Preservação entre lotes verificada por SHA-256 (`T33`), geradores analíticos `WFG1–WFG9` implementados na Prioridade 3, fallback opcional de restauração oficial `-Dstudy.restoreOfficialJMetalResources=false` implementado e manifesto efetivo pré/pós-execução exportado (`T34`) | T33/T34 |
| F5-09 | Relatório descreve duas fórmulas diferentes de sementes | CONFIRMADA inconsistência interna do relatório; RESOLVIDA na T33 | Função canônica única verificada e documentada em `IsolatedJobExecutor.computeDeterministicSeed` (`SHA-256` UTF-8 + `SplitMix64`) | T33 |
| F5-10 | Relatório descreve defaults 750 para DTLZ5/6; código fornecido retorna 1000 | CONTRADITA pelo código e teste real (`1000` confirmado antes do ajuste `A1`) | Divergência registrada e coberta por `StudyOutputDirectoryAndGenerationsTest` | T32 |
| F5-11 | Indicadores são construídos diretamente no main; recomendações de r=1.1 e epsilon-PF não são automaticamente adequadas | CONFIRMADA e RESOLVIDA em T35/T37 (`A3`) | `NHV` suspenso temporariamente no perfil ativo; caixa comum $[\mathbf{0}, \tilde{\mathbf{r}}]$ sob `THEORETICAL_PARETO_BOUNDS` com paridade exata entre `PISA`, `WFG` e `MC`; reuso de `MC` e fallback único configurável `-Dstudy.hvFallbackToMcOnAllExactTimeout` (`NA` estrito, nunca `0.0`); `resolveEffectiveRuntimeClassPath()` para `mvn exec:java` | T35/T37 |
| F5-12 | README descreve R2 e estatística; main não as conecta diretamente | CONFIRMADA e RESOLVIDA em T36/T37 (`A4` + Opção A) | `StudyStatisticalAnalyzer` integrado ao `main()`, validação cruzada ao vivo aprovada contra `R 4.6.2` base (`scripts/kruskal_dunn_holm.R`), `R2ExecutionObserver` passivo opt-in (`0` avaliações extras nos 6 algoritmos) e `README.md` consolidado | T36/T37 |

Hash comprova identidade de bytes, não procedência nem validade matemática. Não declare corrupção apenas porque um arquivo difere de outra variante legítima.

Não repita validação matemática de todas as frentes sem vínculo com um achado, alteração ou risco de regressão. O gate final reutiliza evidências e executa os testes pertinentes.

### Fundamentação e alternativas

Consulte fontes primárias dos algoritmos, benchmarks e decisões já aprovadas no projeto. Não escolha parâmetros comuns apenas porque aparecem no algoritmo mais recente; justifique comparabilidade sem descaracterizar métodos.

Para cada decisão afetada, registre:

- Problema e evidência.
- Alternativas.
- Fundamentação técnica ou científica.
- Impacto experimental.
- Custo computacional.
- Reprodutibilidade.
- Risco de regressão.
- Testes necessários.
- Decisão ou pendência.

Diretrizes:

- Reutilize componentes validados antes de criar novos.
- Conte avaliações reais, incluindo inicialização e soluções descartadas.
- Não use exceção arbitrária, objetivos fictícios ou filhos não avaliados para controlar orçamento.
- Aprove lote parcial válido ou orçamento comum viável quando necessário.
- Isole o RNG integralmente; sementes distintas não provam independência estatística.
- Não prometa reprodutibilidade bit-a-bit fora de ambiente, versão e ordem controlados.
- Use referência comum por problema e número de objetivos.
- Diferencie limites da PF de limites de todas as soluções viáveis.
- Não aplique clipping silencioso ou PF diferente por algoritmo.
- Valide HV exato, aproximado, precisão, IC, seed e política de mistura antes de integrar.
- Não transforme falha, timeout ou ausência em zero.
- Defina unidade, independência, empates, direção e multiplicidade antes da análise estatística.

Referências técnicas consultadas nesta preparação:

- Oracle Java SE 21, `Process`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Process.html
- Oracle Java SE 21, `Future.cancel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html
- R `stats::p.adjust`: https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html

A documentação R consultada é R-devel. Fixe versão e pacotes locais ao reproduzir.

---

## Matriz operacional

| Tarefa | Responsabilidade | Dependências | Estado atual | Responsável |
|---|---|---|---|---|
| T32 | Reconciliação, baseline e correções mínimas de configuração/saída | Nenhuma nova | `completed_validated` | `agentA` (validado pelo Coordenador) |
| T33 | Orçamento, isolamento, sementes e preservação de runs/lotes (incorpora T32) | T32; decisões `A1`, `A2` e `A5` recebidas | `completed_validated` | `agentB` (validado pelo Coordenador) |
| T34 | Referências, overrides, memória e manifesto efetivo | T32; interface de T33 | `completed_validated` | `agentC` (validado pelo Coordenador) |
| T35 | Resultado, normalização e HV seguro no pipeline | T32; interfaces T33/T34; decisão `A3` + confirmação do delta com ressalva de fallback generalizado ligar/desligar recebida | `completed_validated` | `agentD` (validado pelo Coordenador) |
| T36 | Estatística, profiling e documentação integrada | T32 para pesquisa; T33–T35 concluídas; decisão `A4` + Opção A (R 4.6.2 base nativo em `C:\Program Files\R\R-4.6.2\bin\Rscript.exe`) aprovadas | `completed_validated` | `agentE` (validado pelo Coordenador) |
| T37 | Aceite fim-a-fim e reauditoria reconciliada (incorpora T33) | T32–T36 concluídas; mapeamento `A5` confirmado | `completed_validated` | Coordenador / Revisor |

### Decisões do operador recebidas em 18/11/2026 (`A1`–`A5`)

1. **`A1` (`DTLZ5`/`DTLZ6` em `StaticParams.getMaxGenerations`):** Aprovada a tabela do `README.md`: `{1000, 1000, 1000, 1000, 1500, 2000}` para $m \in \{2, 3, 5, 8, 10, 15\}$.
2. **`A2` (Orçamento `B` e lote parcial na `T33`):** Aprovada $B_{\text{target}} = N_{\text{config}}(m) \times G_{\text{config}}(\text{problema}, m)$ incluindo inicialização, mantendo $N_{\text{alloc}}$ interno e avaliando no último lote apenas o saldo permitido, integrando somente soluções efetivamente avaliadas ao `replacement` (proibidos objetivos fictícios/herdados ou avaliações ocultadas; rejeitar $B < N_{\text{alloc}}$; validar os 6 algoritmos inclusive em $B = N_{\text{alloc}}$ e $B = N_{\text{alloc}} + 1$).
3. **`A3` (Ajuste de escopo na `T35` — suspensão temporária de `NHV` e dupla saída HV exato + Monte Carlo):**
   - Manter `PISAHypervolume` exato isolado em processo (`ProcessIsolatedHypervolumeExecutor`) com deadline efetivo como referência primária e exportar também `MonteCarloHypervolume` em saída secundária própria em todas as execuções do perfil ativo; reutilizar resultado MC válido existente em caso de timeout do exato, e exigir fallback MC obrigatório mesmo quando apenas o exato for solicitado (nunca converter timeout/falha em zero nem apresentar MC como exato ou `NHV`).
   - Suspender temporariamente `NHV` no perfil ativo desta fase (não calcular, não exportar, não exigir derivação em $O(1)$ nesta entrega e não incluir nas novas tabelas/análises deste perfil), preservando código reutilizável, testes matemáticos, documentação histórica e resultados anteriores, e registrando eventual reativação como decisão futura sem criar tarefa redundante.
   - Normalização e ponto de referência comum permanecem regidos pelo contrato científico reconciliado na `T35`.
   - Encaminhamento para `T36`/`T37`: `T36` consome a série Monte Carlo homogênea e a série exata com estados explícitos de disponibilidade (sem exigir `NHV` e sem misturar métodos silenciosamente); `T37` verifica a suspensão de `NHV` e a integração correta de exato/MC/fallback.
   - Condição antes de retomar o código de indicadores da `T35`: reconciliar separadamente modo de normalização (`THEORETICAL_PARETO_BOUNDS` vs `HYBRID_ENVELOPE_BOUNDS`), nomes de propriedades e contagem de amostras com os contratos existentes e apresentar ao operador um resumo curto do delta.
4. **`A4` (`T36` — script R e `R2ExecutionObserver`):** Preservar e adaptar `scripts/kruskal_dunn_holm.R` (mantendo modo fixture e adicionando leitura de dados reais + comparação automática contra Java), inspecionar `WFGStudy/R/` e `WFGStudy/latex/`, manter pós-processamento Java resiliente à ausência de `Rscript` no `PATH`, separar análise por célula de análise global e manter `R2ExecutionObserver` opt-in e desativado por padrão (preservando `R2` final, sem criar Fase 6).
5. **`A5` (Mapeamento `T32 -> T33` e `T33 -> T37`):** Confirmado pelo operador sem criar `tasks/task32.md` nem `tasks/task33.md`; ausência física desses arquivos não bloqueia mais a execução.

### Reconciliação oficial de identificadores e arquivos em `tasks/` (Executada na T32 — 17/11/2026)

Inventário físico de `tasks/`: existem exatamente **37 arquivos** (`tasks/task1.md`..`tasks/task31.md` e `tasks/task32.md`..`tasks/task37.md`).
- **`tasks/task32.md` e `tasks/task33.md` NÃO existem fisicamente em `tasks/`**: ambos os identificadores existem apenas como seções internas ("Tarefa Extra `T32`" e "Tarefa Extra `T33`") em `docs/relatorio_auditoria-pre_execucao_final.md`.
- **Encaminhamento sem duplicação**:
  - `T32` (preservação de execuções independentes, não-sobrescrita entre lotes e reprodutibilidade determinística): escopo absorvido integralmente pela **`T33`** (`tasks/task33.md`), reutilizando e integrando `IsolatedJobExecutor` (`T15`). Não criar arquivo `task32.md` nem segundo executor.
  - `T33` (reauditoria Zero-Trust em ordem cronológica reversa pós-correções): escopo reconciliado e absorvido pela **`T37`** (`tasks/task37.md`), que verificará tarefa por tarefa em ordem reversa após o fechamento de `T32`–`T36`, sem criar auditoria paralela.
- **Reconciliação de títulos divergentes entre `tasks/task*.md` reais e `docs/relatorio_auditoria-pre_execucao_final.md` (`F5-01`)**:
  - Fase 1 (`tasks/task1.md`–`task5.md`): `T01` (Investigar origem, finalidade e fluxo dos artefatos de referência), `T02` (Validar `reference-calc` e pontos do NSGA-III contra Das & Dennis), `T03` (Validar arquivos de pesos do MOEA/DD e alternativas nativas), `T04` (Validar frentes de Pareto de referência e recursos do jMetal), `T05` (Padronizar parâmetros, NSGA-III, MOEA/DD e hipervolume). *(O relatório resumiu/trocou rótulos entre `T01`–`T05`; prevalecem os arquivos reais em `tasks/`).*
  - Fase 2 (`tasks/task6.md`–`task11.md`): `T06` (Auditoria de operadores comuns e mutação polinomial), `T07` (Revalidação de NSGA-III/RVEA, persistência opcional e frentes de Pareto), `T08` (Hipervolume, pontos de referência e normalização), `T09` (Instrumentação e avaliação de desempenho), `T10` (Validação final, documentação e consolidação), `T11` (Auditoria completa e sincronização dos pesos do MOEA/DD). *(O relatório trocou descrições de `T06`–`T11`; prevalecem os arquivos reais em `tasks/`).*
  - Fase 3 (`tasks/task12.md`–`task24.md`): títulos e escopos alinhados (`T12` inventário/versões, `T13` orçamento `B`, `T14` FDEA/legados, `T15` isolamento/RNG, `T16` frentes físicas/DTLZ, `T17` normalização/indicadores/HV, `T18` manifestos/migração, `T19` contrato de resultado, `T20` HV híbrido/timeout/MC, `T21` Kruskal–Wallis/Dunn–Holm, `T22` reauditoria profiler/15D, `T23` integração/preflight, `T24` dossiê metodológico).
  - Fase 4 (`tasks/task25.md`–`task31.md`): `T25` (Inventário arquitetural e caracterização da resolução atual), `T26` (Investigação das duplicatas e aprovação da arquitetura de destino), `T27` (Plano de mitigação da dívida técnica baseado em evidências), `T28` (Centralização de primitivas matemáticas e validações reutilizáveis), `T29` (Resolução canônica, retornos estruturados, `baseDir` e política 2D em memória), `T30` (Migração dos consumidores e manifesto dos artefatos efetivos), `T31` (Exclusão condicionada do `ArtifactResolver` e validação final). *(O relatório resumiu subtarefas de zero-replacement dentro de `T25`–`T31`; prevalecem os arquivos reais em `tasks/` e o histórico consolidado no `HANDLER.md`, sem recriar `ArtifactResolver`).*
  - Fase 5 (`tasks/task32.md`–`tasks/task37.md`): arquivos já materializados e verificados sem colisão de IDs.

Arquivos propostos:

```text
tasks/task32.md
tasks/task33.md
tasks/task34.md
tasks/task35.md
tasks/task36.md
tasks/task37.md
```

Confira a numeração e o template real antes de materializar os arquivos. Não reutilize identificadores existentes.

T32 e T33 permanecem reservadas ao que o relatório já menciona. Não crie outra auditoria equivalente nem uma tarefa redundante.

T33 é integração complementar às garantias de T15/T32. Se T32 já cobrir a mesma correção, atribua o item à T32 e mantenha aqui apenas o escopo residual.

T33 é a reauditoria reversa pós-correções mencionada no relatório. T37 é o aceite integrado da Fase 5: reutiliza a T33 quando confirmada, consolida as evidências de T32–T36 e decide se o perfil aprovado está apto à autorização humana para campanha. Não criar outra auditoria equivalente nem sobrescrever IDs.

Mesmo procedimento para T23, T29, T30 e demais atividades equivalentes: registre o encaminhamento e não execute dois patches.

### Estados

| Estado | Significado |
|---|---|
| `pending` | Ainda não iniciada |
| `in_progress` | Em execução |
| `blocked` | Impedida de prosseguir |
| `awaiting_validation` | Entregáveis apresentados, aguardando verificação |
| `completed_validated` | Critérios obrigatórios atendidos e evidências verificadas |
| `completed_insufficient` | Conclusão histórica com validação insuficiente |
| `reopened` | Reaberta com justificativa |

`completed_insufficient` não representa aprovação da tarefa atual. Registre o bloqueio ou a reabertura necessária.

A retomada deve ser registrada como evento e seguida do estado apropriado.

Não converta estados históricos automaticamente.

### Atribuição

Cada atribuição deve informar:

- Objetivo.
- Dependências.
- Arquivos autorizados.
- Arquivos somente para leitura.
- Entregáveis.
- Testes.
- Critérios de aceitação.
- Restrições.
- Forma de registro dos eventos.

Um agente pode executar várias tarefas sequencialmente.

---

## Sequência e reservas

```text
T32 — Reconciliação e baseline
  |
  +--> T33 — Execução, orçamento e preservação
  |       |
  |       +--> T34 — Referências e manifesto
  |               |
  |               +--> T35 — Resultado e HV seguro
  |                       |
  |                       +--> T36 — Estatística e documentação
  |                               |
  |                               +--> T37 — Aceite fim-a-fim e reauditoria reconciliada
  |
  +--> T33/T34/T35/T36 — investigações independentes em arquivos próprios
```

- T32 estabelece o baseline antes dos patches dependentes.
- T33 estabiliza contrato de job/run/IO.
- T34 entrega resolução e manifesto.
- T35 integra métricas com esses contratos.
- T36 consome dados validados.
- T37 executa o aceite fim-a-fim após T32–T36 e a reconciliação da T33.

O gate final reutiliza T33 se sua especificação for confirmada e coerente. Se ausente ou incompatível, registre bloqueio e encaminhamento ao operador.

Reserve arquivos exatos, não diretórios amplos sem necessidade.

| Arquivo | Tarefa | Responsável | Situação | Próxima ação |
|---|---|---|---|---|
| `src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java` | T34 | `agentC` | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java` | T34 | `agentC` | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java` | T32–T37 | Coordenador / `agentA..E` | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java` | T34/T36 | `agentC` / `agentE` | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/study/StaticParams.java` | T33 | `agentB` | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java` | T33/T36 | `agentB` / `agentE` | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java` | T33/T36/T37 | `agentB` / `agentE` / Coordenador | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java` | T35/T37 | `agentD` / Coordenador | `released` (`completed_validated`) | Manter na suíte de regressão |
| `src/main/java/br/usp/lti/newmoea/statistics/StudyStatisticalAnalyzer.java` | T36 | `agentE` | `released` (`completed_validated`) | Manter na suíte de regressão |

Uma reserva aparentemente abandonada não autoriza sobrescrita.

Antes de transferi-la:

1. Verifique o estado do agente.
2. Inspecione as alterações.
3. Consulte o diff.
4. Registre a transferência.
5. Preserve o trabalho existente.

---

## Testes, documentação e aceite

A reprodução do baseline pode ter falhas; o aceite final exige suíte integral sem excluir `MoeaddWeightValidationTest` ou remover asserções para obter verde.

Registre comandos, versões, hashes, logs, contagens reais e limitações.

Não use contagens históricas como metas.

Smokes somente temporários ou em `WFGStudy/tests/`, com orçamento e deadline seguros.

`-Druns=1` não garante custo reduzido.

Inclua testes de:

- Lote A seguido de B.
- Rerun idempotente.
- Crash entre arquivos.
- FUN/VAR inválidos.
- Protocolo incompatível.
- Hash alterado.
- Repetição por runId e ordem.
- Trinta jobs baratos.
- HV de geometria conhecida em m15 sob limite.
- Ausência de acesso operacional legado.

Preserve resultados brutos e rastrear derivados/índices FUN–VAR.

Falha, timeout ou ausência não vira zero.

O README é o destino principal da documentação definitiva. Não crie `docs/scientific-rationale.md` nem relatório separado de planejamento.

Para cada método/função relevante, documente:

- Assinatura real.
- Finalidade.
- Entradas e tipos.
- Saídas.
- Pré/pós-condições.
- Efeitos colaterais.
- Dependências.
- Exceções.
- Invariantes.
- Limitações.
- Complexidade demonstrável.

Explicar também fluxo integrado e parâmetros/hiperparâmetros: default, valor efetivo, domínio, unidade, validação, justificativa, interações, impactos esperados/observados, trade-offs e fonte verificada.

Diferenciar teoria, implementação e observação.

Corrigir/complementar seções existentes do README sem duplicar conteúdo correto. Preservar histórico.

Executor entrega conteúdo e mapa método/parâmetro→seção→evidência. Coordenador publica.

---

## Bloqueio e retomada

Interrompa a ação afetada quando houver:

- Arquitetura ou ciência não aprovada.
- API ou comportamento presumido.
- Orçamento desigual.
- RNG compartilhado.
- Perda de dados.
- PF inválida.
- MC sem precisão.
- Teste obrigatório reprovado.
- Conflito de arquivo.
- Alteração fora do escopo.
- Risco aos dados históricos.

Registre causa, evidência, impacto e condição de retomada.

A falta de paralelismo seguro pode ser resolvida com execução sequencial.

Uma tarefa bloqueada não impede atividades independentes.

Na retomada, consulte HANDLER, decisões, reservas, agentes ativos, arquivos/artefatos e processos ativos. Registre a retomada.

Não repita tarefas concluídas sem motivo e não apague alterações para resolver conflitos.

---

## Critério de encerramento

A Fase 5 somente poderá ser validada quando:

1. T32–T37 estiverem concluídas ou bloqueadas com justificativa.
2. A integração estiver demonstrada pelo entrypoint real.
3. As referências e o orçamento estiverem válidos.
4. Os resultados e lotes estiverem preservados.
5. O RNG e as sementes estiverem demonstrados.
6. O HV estiver protegido por deadline real.
7. A análise estatística estiver coerente com o desenho.
8. A documentação estiver atualizada.
9. O aceite fim-a-fim da T37 estiver concluído.
10. O gate final estiver reconciliado.
11. Nenhum acesso operacional legado permanecer.
12. Nenhum dado histórico estiver alterado.
13. As limitações e pendências estiverem visíveis.

Se houver dependência residual, bloqueie a exclusão correspondente.

Se um critério obrigatório não for atendido, não declare a tarefa concluída.

Não apresente uma entrega parcial como validação integral.

A conclusão desta fase não equivale à execução ou autorização da campanha experimental completa.

---

## Registro consolidado da Reauditoria Zero-Trust Reversa e Homologação (`T37` + `T33` — 18/11/2026)

Executada pelo Coordenador sem presumir conclusões prévias, combinando inspeção de código/bytecode, execução integral sem exclusões (`mvn clean test`: **394 testes em 27 classes, 0 falhas, 0 erros, 0 ignorados**), empacotamento (`mvn package -DskipTests`) e smokes reais via CLI (`mvn exec:java`) em `WFGStudy/tests/`:

1. **`T36` $\to$ `T32` (Fase 5, ordem reversa)**:
   - **`T36` (`CONFIRMADA` e `VALIDADA`)**: `StudyStatisticalAnalyzer` conectado diretamente a `WFGStudy.main()`; paridade ao vivo com `R version 4.6.2 (2026-10-29 ucrt)` em R base nativo (`validated_cells=5`, `validated_pairs=75`, `max_abs_diff_H=3.38e-12`, `max_abs_diff_p_holm=2.07e-6` no smoke CLI; `150` pares validados na suíte); Friedman por blocos separado da análise por célula; `R2ExecutionObserver` passivo (`0` avaliações extras nos 6 algoritmos); resiliência comprovada sem `Rscript`.
   - **`T35` (`CONFIRMADA` e `VALIDADA`, com patch complementar na `T37`)**: `NHV` suspenso no perfil ativo (`NHV_2D_EXISTS=False`, `NHV_15D_EXISTS=False`); paridade numérica $\le 10^{-12}$ entre `PISA`, `WFG` e `MonteCarloHypervolume` sob `THEORETICAL_PARETO_BOUNDS`; `NA` estrito em timeout/falha (nunca `0.0`); reuso sem recomputação quando `MC` está ativo e fallback único configurável (`-Dstudy.hvFallbackToMcOnAllExactTimeout=true|false`) quando apenas HVs pesados são configurados. *Achado capturado e corrigido na `T37`*: `mvn exec:java` isolava dependências em `URLClassLoader` fora de `java.class.path`; implementado `ProcessIsolatedHypervolumeExecutor.resolveEffectiveRuntimeClassPath()` (também usado em `IsolatedJobExecutor`) e validado no CLI real com `pisa_status=SUCCESS_EXACT` e `-Dstudy.processIsolation=true`.
   - **`T34` (`CONFIRMADA` e `VALIDADA`)**: Geradores analíticos `WFG1–WFG9` e `DTLZ1–DTLZ7` ativos na Prioridade 3 com validação prévia de conteúdo nas Prioridades 1/2, fallback opcional de restauração oficial do jMetal 7.8 desativado por padrão (`-Dstudy.restoreOfficialJMetalResources=false`, via backup `moead/` ou tag oficial `jmetal-7.8` no GitHub, bloqueando pastas legadas e a cópia externa modificada do jMetal), escrita atômica sob travas por path (`FRONT_GENERATION_LOCKS`, `WEIGHT_GENERATION_LOCKS`), fronteira `m=2, 2<=N<=300` e `artifact-manifest.csv` com estágios pré/pós-execução.
   - **`T33` + `T32` (`CONFIRMADA` e `VALIDADA`)**: `DTLZ5/6` = `{1000,1000,1000,1000,1500,2000}`; orçamento equitativo $B_{\text{target}}$ sem objetivos fictícios nos 6 algoritmos (`BudgetCompliantMOEADD` incluso); semente determinística `SHA-256` + `SplitMix64` invariante à ordem/paralelismo; retomada em lotes (`runStart=0,runs=2` $\to$ `runStart=2,runs=1`) com `FUN0_HASH_PRESERVED=True` e recusa de sobrescrita cega em diretórios sem `run_manifest.csv`.
   - **`T32` (`CONFIRMADA` e `VALIDADA`)**: `WFGStudy.resolveStudyOutputLayout` funcional em subdiretórios aninhados e `MoeaddWeightValidationTest` reconciliado sem exclusões.
2. **`T31` $\to$ `T01` (Fases 4 $\to$ 1, ordem reversa conforme seção `T33`)**:
   - **`T31`–`T25` (Fase 4)**: `ArtifactResolver` permanece removido sem referências residuais; `0` acessos operacionais a `MOEAD_Weights/` e `pareto_fronts/csv/`; primitivas de dominância centralizadas em `ParetoFrontValidator.dominanceComparison`; `ResolvedFrontArtifact`/`ResolvedWeightArtifact` imutáveis com injeção hermética de `baseDir`.
   - **`T24`–`T12` (Fase 3)**: Dossiê metodológico preservado; 6 algoritmos canônicos ativos por padrão (`-Dstudy.includeLegacyAlgorithms=false`); profiler Phase 3, `KruskalWallisTest`/`DunnPostHocTest`, `ExactHypervolumeWorker`/`MonteCarloHypervolume` (IC Wilson 95%), `ResultFrontContract` (1:1 `FUN`/`VAR`, não-dominância estrita), `ObjectiveNormalizationContract`, auditoria física/matemática de frentes, `FDEA` 100% determinístico via `JMetalRandom` e `EvaluationBudgetAuditTest` aprovados na suíte integral.
   - **`T11`–`T01` (Fases 2 e 1)**: `MoeaddWeightValidationTest`, `PerformanceProfilerTest`, `HypervolumeAuditTest`, `RveaReferenceDirectionsValidationTest`, `OperatorAuditTest`, `AlgorithmIntegrationTest`, `QualityIndicatorUnitTest`, `ParetoFrontValidationTest` e `NsgaIIIReferencePointsValidationTest` todos executados e aprovados (`0` falhas), com diretórios protegidos 100% intactos (`MOEAD_Weights`: 43 arquivos; `pareto_fronts`: 484 arquivos; `pareto_fronts/csv`: 160 arquivos; `pareto`: 125 arquivos; `WFGStudy/` histórico inalterado) e `0` referências à cópia externa proibida do jMetal.

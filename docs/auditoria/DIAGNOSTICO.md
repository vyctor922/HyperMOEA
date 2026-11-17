# Diagnóstico científico e técnico — preparação da Fase 3

## 1. Escopo e nível de certeza
Esta é uma auditoria documental de snapshots anexados e investigação técnica parcial. Não se teve a árvore do repositório completo, pom.xml, JAR 7.8 instalado, fontes FDEA, validadores, profiler, frentes CSV, tasks individuais, operator-audit.md, final-validation-report.md, task-253.log ou output.txt. Não se executou Maven/Java nem benchmarks. Fontes públicas consultadas estão em FONTES.md. Falhas de recuperação raw GitHub não provam inexistência da API. GitHub/Drive disponíveis como conectores estavam desconectados.

Foram lidos os documentos atuais anexados e código completo disponível. Versões anteriores foram usadas apenas como histórico. Dissertação é pré-experimentos, não descrição do estado atual. Documentos gerados são planos futuros; não alteram fontes nem arquivos originais do projeto.

Classes de evidência: C = confirmado no snapshot; R = relato histórico não reproduzido; H = hipótese; I = inferência a partir do código/matemática; P = proposta; E = experimento adicional necessário.

## 2. Estado de tarefas
HANDLER declara T01–T11 completed, com T10 relatando 277 testes, 10 classes, dois smokes e T09 relatando 630 registros/tempos HV. Essas contagens e tempos são R, não C de execução nesta análise. Não repetir T06 como se pendente: chamada atual confirma 1/n no código. T07 tem persistência/geração chamada no pipeline, mas corpo dos geradores não disponível; completude matemática permanece sem comprovação independente. T08 precisa revisão de integração/semântica; T09 precisa revisão de proteção de produção; T10 precisa homologação com novos critérios. T11 tem cópia relatada, mas migração de consumidores não demonstrada. T12 reclassificará individualmente sem apagar histórico.

Tasks individuais não anexadas: planejamento T12–T24 preserva IDs T01–T11 conhecidos, mas não afirma inexistência de IDs posteriores no repositório. Instalação condicionada a inventário e renumeração se necessário.

## 3. Divergências de escopo
C: WFGStudy.configureAlgorithmList adiciona onze algoritmos (linhas 322–340), enquanto coordenador define seis principais. Dissertação §§3.3/4.1 inclui NSGA-III, HypE, FDEA, RVEA, AGE-MOEA; README explica substituição HypE por MOEA/DD e inclui AGE-II. Decisão proposta: protocolo de escopo aprovado, perfil seis principais e legados opcionais false. Mudança não pode ser justificada retroativamente apenas pelo README.
C: defaults objetivos seis, runs 30; DTLZ m<=3 (linha 244), m>3 apenas generateAllReferenceFronts; WFG k=2(m-1), l=20; DTLZ construtor recebe L=20 como número de variáveis, não o mesmo k da WFG. Derivado da lista padrão, sem execução: 54 pares WFG +14 DTLZ=68 configurações problema/m; 11*30*68=22.440 jobs antecipáveis. Com seis principais=12.240; cobertura completa de 96 pares e seis principais=17.280. Isso não comprova memória usada: objetos podem inicializar populações só em run.

## 4. Auditoria das cinco inconsistências
| Item | Resultado e consequência | Evidência | Tarefa |
|---|---|---|---|
| Centralização resources | C: caminhos legados continuam; R: cópias oficiais existem; não é obrigatório migrar só por estética | WFGStudy 165/411–423/510–523 e HANDLER | T18 |
| Caminhos legados | C: PF diretório pareto_fronts/csv; pesos MOEAD_Weights; DTLZ gerado via relativize. Integração mistura resolução lógica e caminho físico | WFGStudy main/addAnalytical/buildMOEADD | T18/T23 |
| Contagens DTLZ | H: nenhuma das seis frentes físicas foi anexada; números desconhecidos, não estimados | ausência no inventário desta entrega | T16 |
| WFG esparsa e normalização | H: WFG4.15D não inspecionado fisicamente; C: limites WFG 2j existem em StaticParams mas não são passados ao main; R: pós-processamento usa extremos amostrais | StaticParams 261; WFGStudy 165–189 | T17 |
| Tempo HV e timeout | C: ComputeQualityIndicators ocorre linha 189; profiler opcional só depois de tabelas. I: timeout do profiler não protege automaticamente caminho anterior; não se sabe se há intervenção interna | WFGStudy main; fonte profiler ausente | T20/T22 |

## 5. Onze frentes: veredicto por item
1. Estatística: P KW+Dunn-Holm somente após comprovar independência. Friedman é análise em blocos, não porque a ordem do CSV importa. R1/R2, T21.
2. População/orçamento: C AGE/AGE-II N ímpar→N+1 (430/442); NSGA-III valida referências, não N interno pós-builder. B real desconhecido. P contagem evaluate e contrato B independente, T13.
3. Principais/legados: C todos adicionados; SPEA2 não recebe N local (396–408); DRA recebe DE no código atual, portanto hipótese de SBX errado não está confirmada. GWASFGA inversão zero é R sem fonte local, T14.
4. DTLZ completo: C geração opcional chamada; H correção dos geradores, especialmente DTLZ5/6 muitos objetivos e DTLZ7. P validar completude, factibilidade, cobertura e densidade, T16.
5. Indicadores/NHV/R2: C HV+NHV+IGD+IGD+ +R2 na lista; R fórmula NHV. I se c positivo fixo, NHV=1-HV/c é transformação decrescente, mantém KW H/p e inverte ranking de qualidade; comparar pesos/ideal do R2 por job, T17/T15.
6. FDEA: C seleção ranking/crowding injetada; H seleção interna/RNG/filhos. R8 mostra artigo de decomposição fuzzy, não a atribuição textual inconsistente da dissertação. T14.
7. Não dominados: H retornos reais não inspecionados; P contrato uniforme mantendo brutos. HV ignora pontos dominados, mas IGD euclidiano pode mudar; decisão precisa ser explícita, T19.
8. Normalização/migração: C fábricas StaticParams não são usadas por main; H PISA(double[]) realmente usa ponto no compute. P contrato testado de coordenadas e resolvedor, T17/T18.
9. Estado/runs: C lista antecipada e operadores/problem reutilizados; H retenção efetiva e singleton RNG no artefato. P factory/job e isolamento por processo se necessário, T15.
10. HV híbrido: P aceitável com deadline efetivo, caixa válida, IC, política de erro e método uniforme por célula. Não mistura automática por run. T20.
11. Documentação: P dossiê português de valores efetivos e limites; não justificativas retrospectivas nem afirmação de HH implementadas só por README, T24.

## 6. Novos problemas e correções de análises anteriores
- Respostas anteriores confundiram título/artigo de MOEA/DD com algoritmo de dois arquivos; usar P2 e conferir metadados no editor. Não perpetuar DOI incorreto.
- Dissertação atribui FDEA a He/Yen enquanto referencia Liu e um título de decomposição fuzzy; identidade do porte precisa ser demonstrada. R8 sustenta distinção, não valida o código próprio ausente.
- AGE-II ano 2020 consta no README mas não foi confirmado; não usar como referência bibliográfica definitiva.
- StaticParams.getParetoFrontUpperBounds Javadoc anuncia limites DTLZ5/6 específicos; corpo retorna vetor de uns. Conservador pode ser válido como escala, mas não corresponde ao suposto nadir exato documentado. Verificar contra a PF verdadeira.
- Exceção WFG3 m2 r1=3.1 acomoda arquivo alegadamente com f1=3/f2 negativo. C é a exceção no código; conteúdo físico ainda R. Não redefinir referência matemática para legitimar extensão não factível da reta.
- Ponto HV não é nadir por definição. O ponto deve ser geometricamente consistente no espaço integrado; referencial minimização dos benchmarks deve corresponder às fórmulas da dissertação, que apresenta maximização.
- Frentes DTLZ5/6 m>3: não declarar automaticamente que todo PF é a curva g=0; auditar casos especiais e literatura de degeneração. Uma equação de hiperesfera pode ser verdadeira para pontos amostrados sem provar PF completa.
- Grade de pontos orientadores não pode definir silenciosamente a PF de avaliação com cardinalidade N e favorecer algoritmo que usa a mesma grade. Amostragem radial do simplex para esfera não é uniforme em área.
- Igualdade de N/G/configuração não garante igualdade de avaliações. Não transformar truncamento de filhos em correção automática sem verificar semântica de sobrevivência.
- JMetalRandom pode ser seguro contra corrida interna mas ainda produzir resultados dependentes do escalonamento; sincronizar singleton não equivale a stream independente por run.

## 7. Decisões recomendadas, condições e alternativas
| Decisão proposta | Alternativa considerada | Motivo / limite | Status |
|---|---|---|---|
| Seis principais; legados false | remover legados | preserva histórico/futuro sem contaminar protocolo | depende aceite |
| B real como controle principal | N/G idênticos | respeita custo efetivo e particularidades estruturais | T13 |
| Escalas fixas por problema/m | extremos de cada run ou PF esparsa | evita mudança de régua entre algoritmos; escolha de escala ainda é preferência experimental | T17 |
| Recurso oficial só após validação matemática | cópia cega por procedência | fonte oficial pode ter duplicatas/pontos inválidos | T16/T18 |
| NHV opcional, fora principal se redundante | recalcular ambos | economiza HV(R), evita duplicação de hipótese; fórmula e c>0 devem ser provados | T17 |
| Worker processo exato se não cooperativo | Future.cancel sozinho | controle de término real, custo startup registrado | T20 |
| MC por célula após piloto | fallback misto sem análise | reduz efeito de erro dependente da dificuldade do algoritmo | T20/T21 |
| Jobs lazy e estado isolado | milhares instâncias antecipadas | reduz retenção provável sem refatoração gratuita; medir RAM antes/depois | T15/T22 |

## 8. Contrato matemático mínimo para HV e Monte Carlo
Para minimização: HV(A;r) = volume da união dos retângulos [a_1,r_1]×...×[a_m,r_m] dos pontos que satisfazem a<=r. Valores fora r não criam volume negativo; contabilizar pontos não contribuintes. Pontos dominados/duplicatas podem ser removidos sem mudar HV. Para outros indicadores, registrar efeito do mesmo filtro.
Escolher caixa comum [l,r] contendo a região integrada. Mínimos por frente geram caixa válida para aquela frente se a<=r, mas não a mesma caixa física para amostras comuns entre algoritmos. Amostras iid uniformes: X~Binomial(S,p), estimador V*X/S não viciado e Var=V²*p*(1-p)/S. Isso é derivação matemática sob iid e caixa válida, não confirmação de implementação.
Com X=0 e S finito, usar limite superior binomial/IC, não alegar HV=0 exato. Quantidade amostral fixa por piloto ou regra sequencial validada; IC de tamanho fixo inspecionado repetidamente não mantém cobertura nominal automaticamente. Mesmas amostras reduzem ruído de diferenças em certas situações, mas introduzem dependência de medição e devem entrar no plano estatístico. Não prometer precisão relativa com p muito pequeno em 15D.
Transformação T_j(f)=(f-a_j)/(b_j-a_j) implica HV bruto=HV transformado*prod(b_j-a_j), se r também transformado e domínio não truncado indevidamente. Não afirmar que PISA construtor r implementa essa regra sem teste.

## 9. Estatística
R1 permite motor R oficial KW para observações independentes. Interpretar diferenças em postos/distribuições; leitura puramente de medianas precisa hipóteses adicionais de forma. Alpha 0.05 é proposta convencional, não obrigação científica. Dunn-Holm é proposta de pós-teste; verificar implementação/empates e família de hipóteses. Holm (R2) controla FWER mesmo com dependência entre os p-valores, mas não corrige violação da independência das observações no teste primário.
Trinta runs são tamanho preliminar, não garantia de poder. Não agregar valores de problemas em escalas diferentes como observações intercambiáveis; análise entre problemas pode usar blocos/sínteses pré-especificadas. Mesma seed numérica não prova pareamento. Precisão MC e falhas precisam ser consideradas antes de testes de postos.

## 10. Desempenho e viabilidade
R5 limita CPU ao thread medido, com suporte/habilitação. Usar timer na thread aguardando Future mede espera, não cálculo do worker. Memória de Runtime/MemoryMXBean não é pico por tarefa nem RSS; pools/JFR/worker isolado são alternativas com limites declarados. Controlar heap adequado ao hardware, GC, JIT/warm-up, IO cold/warm, seeds e concorrência. Não estimar HV de frente truncada e chamar tempo do conjunto completo.
Deadline de produção é bloqueante, antes de qualquer cálculo exato HV15. R3 não garante término; R4 oferece encerramento de processo com espera/verificação e cuidado com I/O. Não usar Thread.stop.

## 11. Estado da entrega
Arquivos task12–task24 são prompts completos com fundamentos, hipóteses, procedimentos, arquivos, dependências, gates, critérios/testes/entregáveis e protocolo HANDLER. Nenhum código implementado. Apenas pacote documental gerado. Novos relatórios de execução descritos nas tasks ainda não existem nem foram preenchidos com resultados fictícios.
Antes de iniciar, disponibilizar repositório local completo no IDE. Para acesso remoto por esta interface, conectar GitHub/Drive em Configurações > Connectors; não necessário para agente que já tenha workspace local.

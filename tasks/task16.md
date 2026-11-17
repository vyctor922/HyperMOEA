# T16 — Frentes físicas, DTLZ completo e auditoria dos geradores

## Identificação, prioridade e dependências
Arquivo: tasks/task16.md. Prioridade: P0. Dependências: T12.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Validar arquivos reais e métodos matemáticos sem aceitar contagens ou completude apenas por equações locais.

## Contexto e problema
Pipeline atual habilita DTLZ m<=3, e para m>3 usa generateAllReferenceFronts=true e gerador ParetoFrontValidator; corpo do gerador e frentes não foram anexados. HANDLER relata geração exata, mas isso precisa de verificação.

## Fundamentação científica e técnica
P5/P6/P7: fórmula de suporte não garante uniformidade, cobertura ou Pareto-optimalidade. PF*, pontos de referência NSGA-III, direções RVEA e pesos MOEA/DD são artefatos diferentes.

## Hipóteses a verificar
Contagens divergentes em seis CSVs; DTLZ5/6 curva é apenas subconjunto da PF em certas dimensões; DTLZ7 omite componentes; WFG3 oficial contém pontos fora da imagem factível; amostragem usa mesma grade do algoritmo favorecendo-o.

## Procedimentos ordenados
1. Inventarie primeiro referências oficiais do artefato exato e repositório oficial fixado por commit quando não empacotadas no JAR. Não afirmar que recurso do repositório vem dentro do JAR sem inspecioná-lo.
2. Conte fisicamente DTLZ7.2D, DTLZ7.3D, DTLZ3.3D, DTLZ4.3D, DTLZ5.2D e DTLZ6.2D: bytes, linhas físicas, linhas numéricas, vazias/comentários, m, únicos exatos, quase duplicados com tolerância justificada, inválidos, hash e origem. Ausentes permanecem sem número.
3. Audite DTLZ1: suporte sum f=0.5; DTLZ2/3/4: ortante positivo norma L2=1. Normalizar grade simplex em L2 é uma parametrização, não prova de densidade uniforme de área. Escolha densidade de referência independente da população do algoritmo e faça sensibilidade de IGD/IGD+.
4. Para DTLZ5/6 m>3, não declare curva g=0 como PF completa sem demonstrar completude e não dominância. Compare formulação, fonte original/correções científicas e avaliação de soluções factíveis fora da curva. Se não resolvido, não habilitar uma referência incompleta como verdade; bloquear ou rotular aproximação com análise de sensibilidade aprovada.
5. Para DTLZ7, derive em g=1: f_m=2m-sum_{i<m} f_i(1+sin(3*pi*f_i)). Regiões não dominadas dependem dos intervalos admissíveis de cada coordenada, não de todos os valores de [0,1]. Verifique extremos e transições por análise unidimensional e contraprovas numéricas. Cobertura de até 2^(m-1) componentes exige orçamento explícito; não enumerar malhas exponenciais sem limite.
6. Audite WFG1–9 individualmente, funções de forma, degenerescência WFG3, desconexão WFG2, escalas S_j=2j no PF. Não validar WFG3 apenas pela reta prolongada; exija factibilidade pelo problema. Frente oficial inválida deve ser quarantinada logicamente e preservada historicamente, sem expandir limites para acomodar erro.
7. Reutilize geradores existentes aprovados; opção generateAllReferenceFronts=false continua padrão. true significa todos os pares do escopo aprovados ou falha explícita, não sucesso parcial silencioso. Não confundir gerar artefatos com incluir problemas no experimento; mantenha seleção explícita e preflight.
8. Preserve geração empírica opcional false. Audite API nativa de união não dominada, compatibilidade FUN/VAR, procedência, sementes e estudos independentes. Permitir como experimento de sensibilidade explicitamente rotulado, nunca substituto silencioso de PF matemática. Não usar referência construída dos mesmos runs para alegar IGD contra verdade.

## Arquivos permitidos
ParetoFrontValidator e testes exclusivos reservado; CSVs somente derivados novos; relatório docs/auditoria/reference-fronts.md e manifestos. Não editar originais.

## Restrições e gates
DTLZ7 e DTLZ5/6 não liberam por simples norma unitária. Limitar tamanho/tempo do teste de dominância; não executar O(n²) irrestrito para frentes massivas.

## Critérios objetivos de aceitação
Inventário físico e contagens reproduzidas; cada um dos 42 pares DTLZ tem status válido/bloqueado; WFG por problema auditado; testes de factibilidade e cobertura além da equação; modos opcionais testados.

## Testes necessários
Fixtures analíticas 2D/3D; forward evaluation de pontos; contraexemplos dominados/fora domínio; componentes DTLZ7; densidades distintas; parsing e round-trip; geração desativada não escreve.

## Entregáveis
Matriz PF por problema/m, contagens CSV, patches mínimos de geradores se necessários e critérios de cobertura.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

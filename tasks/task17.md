# T17 — Normalização única, semântica dos indicadores e ponto HV

## Identificação, prioridade e dependências
Arquivo: tasks/task17.md. Prioridade: P0. Dependências: T12; conclusão matemática T16 antes da decisão final.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Definir coordenadas e semântica inequívocas para HV, IGD, IGD+ e R2, sem normalização dupla ou ajuste por algoritmo.

## Contexto e problema
WFGStudy usa ComputeQualityIndicators diretamente com HV e NHV defaults; fábricas de StaticParams não são chamadas no main. getParetoFrontUpperBounds documenta limites específicos DTLZ5/6, mas retorna 1 para todos; WFG3 recebe exceção 3.1 para acomodar CSV histórico.

## Fundamentação científica e técnica
R6 mostra normalização por min/max da referência como padrão de documentação, não comprovação 7.8. P8/P10 sustentam estudo de indicadores; ponto nadir não é sinônimo do ponto externo HV. Transformação afim positiva é decisão de escala, não neutralidade de preferências entre problemas.

## Hipóteses a verificar
ComputeQualityIndicators usa limites amostrais; PISA inverte/clampa fora [0,1]; construtor double[] guarda referenceFront sem usar referência geométrica como esperado; NHV recalcula HV(R); R2 reestima ideal por chamada; IGD implementação difere da fórmula da dissertação.

## Procedimentos ordenados
1. Inspecione código/bytecode 7.8 de ComputeQualityIndicators, NormalizeUtils, VectorUtils.getInvertedFront, HV, NHV, IGD, IGD+ e R2. Registre call graph, cópias/mutação, tratamento de vazio/NaN/outliers e setters por problema.
2. Reproduza WFG4.15D: limites amostrais reais vs limites teóricos do PF. Teste todos os m. Use limites teóricos aprovados da PF, ou parâmetros externos fixos aprovados, independentes dos algoritmos/runs. Não derive limites de cada saída.
3. Defina T_j(f)=(f-a_j)/(b_j-a_j) com a,b versionados por problema/m e b>a. Proposta WFG PF: a=0,b_j=2j, sujeita à formulação aprovada. Fora da PF, f pode exceder b; não clipar IGD/IGD+/R2 em silêncio. Domínio PF não é limite global das soluções.
4. DTLZ1 pode usar a=0,b=0.5; DTLZ2–4 a=0,b=1; DTLZ5/6: avaliar escala teórica comum conservadora vs extremos corretos confirmados; DTLZ7: último objetivo tem escala própria e limite analítico, não impor zero/nadir amostral sem justificativa. Não exigir referência HV simétrica.
5. Defina r bruto e T(r) consistentes. Margem 10% é escolha experimental, não teorema; aprovar antes dos dados e testar sensibilidade. r precisa limitar o domínio integrado: soluções com alguma coordenada >r não contribuem, mas não tornam todo experimento inválido. Registrar descartes; não aumentar r por algoritmo.
6. Teste HV geométrico 2D à mão em escala não unitária. Verifique construtor PISA(double[]) antes de chamá-lo de HV bruto: guardar referência não prova translação/escalonamento. Se backend exige caixa unitária, aplicar transformação única documentada e Jacobiano para HV bruto. Refatore apenas adaptador pós-processamento mínimo se pipeline nativo não aceitar limites corretos.
7. Confirme NHV=1-HV(A)/c, c=HV(R)>0 e fixo. Se confirmado, retirar NHV do perfil principal por redundância e custo; deixar opcional para compatibilidade, derivado do HV já calculado. Provar rankings invertidos/estatística H e p de KW preservados salvo ties numéricos. Denominador zero/NaN é erro explícito.
8. R2: manter pesos e ideal constantes para comparar tempos/recompensas; mudança de escala/ideal não pode ser creditada à LLH. Conferir geração m>2 e custo de número de pesos. Não confundir seus pesos com MOEA/DD.
9. Defina único contrato de entrada bruto→normalizado→indicador; proíba double-normalization no ComputeQualityIndicators. Salve manifestos de limites, r, hash PF, fórmula IGD efetiva, direção e versão. Comparações entre m/problemas exigem ranks/sínteses apropriadas, não médias de HV bruto.

## Arquivos permitidos
Adaptadores indicadores, testes exclusivos; StaticParams reservado; WFGStudy integra pela T23. Não alterar PF nem código interno da biblioteca sem gate.

## Restrições e gates
Sem fonte binária e testes não fixar semântica PISA. Não transformar normalização por frente incompleta em padrão opcional válido.

## Critérios objetivos de aceitação
Métrica 2D concorda com cálculo independente; invariância sob transformação com Jacobiano; limites constantes entre runs; NHV removido ou mantido com evidência; fórmulas e clipping explícitos.

## Testes necessários
Coordenadas <a, =b, >b, >r; pontos vazios/duplicados/dominados; denominador zero NHV; mesmo H/p sob transformação; R2 repetido com referência fixa; IGD p=1 vs outra variante.

## Entregáveis
docs/auditoria/normalization-hv-contract.md, tabela limites/r, testes de indicadores e decisão NHV.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

# T18 — Manifestos, recursos oficiais e migração compatível

## Identificação, prioridade e dependências
Arquivo: tasks/task18.md. Prioridade: P1. Dependências: T12; inventário T16 e contrato T17 antes de migração.
Estado inicial: pending; validar colisão de numeração na T12 antes de instalar.

## Objetivo
Centralizar resolução de artefatos sem apagar legado, confundir recurso de classpath com diretório físico ou mudar dados silenciosamente.

## Contexto e problema
Código atual lê pareto_fronts/csv e MOEAD_Weights; frentes geradas são alcançadas via relativize com caminho ../../resources. Handler relata cópias oficiais, mas arquivos não foram disponibilizados nesta análise.

## Fundamentação científica e técnica
R6 ilustra leitor CSV; P6/P7 distinguem PF de pesos. Procedência requer checksum e commit/artefato; equivalência geométrica não prova quem criou um arquivo.

## Hipóteses a verificar
Recursos duplicados; cópias ausentes; nomes incompatíveis W{m}D_{N}.dat; 2D N<=300 gerado em memória enquanto preflight exige arquivo inutilizado; GWASFGA com inversão.

## Procedimentos ordenados
1. Inventarie todos os arquivos pareto/, pareto_fronts/csv/, MOEAD_Weights/, resources/. Compare cada um com fonte oficial fixada e registre hashes, origem confirmada/desconhecida, consumidor real e formato. Não repetir afirmação de origem comprovada apenas por equivalência numérica.
2. Preserve estrutura proposta: resources/referenceFronts/ para referências versionadas; pf_gen e pf_gen/empirical para derivadas; resources/weightVectorFiles/ para oficiais e weights_gen/ para derivados. Adicionar subpasta de origem só se não duplicar estrutura existente. Resultados vão ao diretório de campanha, não resources.
3. Reutilize o mecanismo T11 existente se válido. Não copiar novamente arquivos idênticos nem supor todos os seis pesos ativos existem no jMetal. Arquivo ausente oficial: gerar apenas após verificar cardinalidade C(H+m-1,m-1), camadas e compatibilidade; não arredondar/truncar grade sem método aprovado.
4. Audite caminho de carga AbstractMOEAD: disco/classpath, convenção nome, geração 2D, erros. Documente se o arquivo validado é exatamente o consumido. Regra zero: PBI admite componentes nulos se vetor inteiro não nulo; eps em inversão somente onde necessário confirmado. Preserve oficiais; replaceZeroWeights=false padrão; variantes registram epsilon, renormalização ou não, número alterado e hash.
5. Projete resolvedor com caminhos canônicos configuráveis, precedência explícita e erro em colisão conflitante. Legacy fallback somente quando manifesto aprova o mesmo artefato; nunca fallback silencioso para PF diferente.
6. Migrar consumidores em patch único com testes. Não apagar legado ou renomear pacotes. resources/ na raiz não é automaticamente src/main/resources nem classpath Maven: ou carregar Path explícito ou configurar recursos com justificativa.
7. Persistência NSGA/RVEA opcional false já existente deve capturar conjunto realmente usado (incluindo fase de normalização RVEA), não reconstrução que não participa do algoritmo. Compare antes/depois de persistir; nome/hash/versionamento evita escrita concorrente e sobrescrita.

## Arquivos permitidos
Resolvedor/manifestos e testes novos; ReferenceSetValidator reservado se defeito confirmado; recursos derivados. Alterar consumidores via T23.

## Restrições e gates
Não excluir originais; sem migração só por estética. Falta de contraparte oficial não autoriza substituir peso válido por população diferente.

## Critérios objetivos de aceitação
Matriz todos os consumidores; sem arquivos duplicados desnecessários; preflight resolução→hash→consumo igual; execução fora da raiz funciona ou falha clara; zeros não alteram oficiais.

## Testes necessários
Arquivos ausentes/conflitantes; paths Windows/Linux; empacotamento se classpath; round-trip/hash; persistência off/on; original com zeros e sem zeros; geração 2D sem arquivo dispensável.

## Entregáveis
docs/auditoria/artifact-migration.md, manifestos, resolvedor mínimo e plano de compatibilidade.

## Protocolo obrigatório de execução
1. Leia AGENTS.md, HANDLER.md, TASK_COORDINATOR.md e esta tarefa integralmente. Localize tarefas antigas equivalentes; não as sobrescreva nem repita resultados corretamente demonstrados.
2. Confirme commit, alterações do usuário, Java, Maven e dependency:tree. Onde não houver fonte, registre AUSENTE, não invente.
3. Registre início, proprietário e arquivos pretendidos por evento exclusivo em docs/TXX-<agente>-<evento>.md; substitua TXX pelo ID. O coordenador aplica o evento ao HANDLER reservado. Em execução sequencial, o próprio agente pode atualizar HANDLER sob a mesma regra. Registre bloqueios imediatamente.
4. Referencie docs/auditoria/FONTES.md e os relatórios desta fase; consulte a fonte primária antes de modificar uma decisão científica. Relatórios antigos são evidência declarada, não prova reproduzida.
5. Não altere produção antes do gate de decisão previsto na tarefa. Mudança de método original exige aprovação do coordenador e do pesquisador quando afetar a identidade do algoritmo ou a comparação.
6. Use arquivos de teste/relatório exclusivos da tarefa. Arquivos compartilhados só podem ser alterados com propriedade concedida pelo coordenador. Preserve arquivos originais, resultados históricos e alterações do usuário. Sem novas dependências injustificadas.
7. Entregue patch mínimo, relatório de comandos reais/exit codes/artefatos, limitações e próxima ação. Não confunda compilação com validade científica nem testes de fumaça com validação de 30 runs.
8. Submeta status awaiting_validation; somente o coordenador, após revisão independente, promove a completed_validated. Ausência de evidência gera blocked ou completed_insufficient, nunca conclusão fictícia.

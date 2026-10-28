# Regras do projeto NewMOEA

- A raiz do workspace contém `pom.xml`, `README.md`, `src/` e `tasks/`.
- Antes de qualquer trabalho, consulte `ORCHESTRATOR.md`, `HANDLER.md` e as tarefas pertinentes em `tasks/`.
- O projeto utiliza Java 21, Maven e jMetal 7.8. Confirme essas versões nos arquivos reais antes de alterar o código.
- Preserve a arquitetura e as funcionalidades existentes, salvo quando uma tarefa justificar explicitamente a alteração.
- Não invente resultados experimentais, referências científicas, APIs ou resultados de testes.
- Não substitua arquivos em `MOEAD_Weights/` ou `pareto_fronts/csv/` sem evidência, justificativa documentada e validação.
- Não confunda vetores de pesos do MOEA/DD, pontos de referência do NSGA-III e frentes de Pareto.
- Atualize `HANDLER.md` ao iniciar, bloquear, retomar ou concluir uma tarefa.
- Não permita alterações concorrentes conflitantes nos mesmos arquivos.
- Execute os testes pertinentes e registre os resultados reais.
- Nunca declare uma tarefa concluída sem verificar seus critérios de aceitação.
- Preserve alterações preexistentes do usuário e não descarte mudanças sem autorização.
# T10 — Validação final, documentação e consolidação

## Identificação
- Arquivo: `tasks/task10.md`
- Tarefa: T10
- Título: Validação final, documentação e consolidação

## Objetivo
Consolidar as decisões e evidências de T06–T09 e T11, executar a validação final e atualizar a documentação permanente do projeto.

## Contexto
Esta tarefa não deve introduzir novas decisões científicas. Ela deve verificar rastreabilidade, testes, documentação e coerência entre código, handler e README.

## Procedimentos
1. Leia `HANDLER.md`, `TASK_COORDINATOR.md`, `AGENTS.md`, `README.md` e todas
   as tarefas.
2. Confirme o estado de T06–T09 e T11.
3. Execute:
```bash
mvn clean compile
mvn clean test
```

4. Execute fumaça:
```bash
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=1 -Dobjectives=2 -Dproblem=WFG1
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=1 -Dobjectives=3 -Dproblem=DTLZ2
```

5. Se DTLZ tiver sido estendido, execute uma validação adicional com um novo
   \(m\) habilitado.
6. Confirme que HV bruto e NHV são gravados separadamente.
7. Confirme que nenhuma frente ou peso foi alterado sem rastreabilidade.
8. Verifique os entregáveis da T11:
   - relatório de auditoria dos pesos;
   - manifesto de hashes;
   - cópias oficiais preservadas;
   - variantes com epsilon, se justificadas;
   - confirmação de que nenhum arquivo original foi alterado.
9. Verifique se o diretório ativo de pesos do MOEA/DD corresponde à decisão documentada, sem mistura silenciosa entre pesos originais e variantes.
10. Atualize o README nas seções 1 a 6.
11. Adicione entrada em “Histórico de Alterações” no formato:
```text
### Alterações DD/MM/AAAA - HH:MM
```
12. Atualize `HANDLER.md` com estado final, evidências e pendências.

## Arquivos envolvidos
- `README.md`
- `HANDLER.md`
- `MOEAD_Weights/` (somente leitura)
- `resources/weightVectorFiles/` (somente leitura)
- `resources/weightVectorFiles/weights_gen/` (somente leitura)
- `docs/moead-weights-audit.md`
- `docs/moead-weights-manifest.csv`
- `docs/**`
- `src/test/**`
- `WFGStudy/` apenas para leitura de resultados de validação

## Restrições
- Não altere algoritmos, parâmetros científicos, frentes ou pesos.
- Não remova entradas anteriores do histórico do README.
- Não declare validação que não tenha sido executada.
- Não oculte falhas, limitações ou pendências.

## Critérios de aceitação
- `mvn clean compile` passa.
- `mvn clean test` passa.
- Execuções de fumaça são executadas ou seu impedimento está documentado.
- README e HANDLER estão coerentes com o código.
- Toda decisão importante possui evidência e referência.
- Pendências remanescentes estão explicitamente listadas.

## Testes
- Suíte completa: `mvn clean test`.
- Testes de integração existentes.
- Execuções de fumaça registradas.

## Entregáveis
- README atualizado.
- HANDLER finalizado.
- Relatório de validação final.
- Lista de pendências.

## Dependências
- T06, T07, T08, T09 e T11.
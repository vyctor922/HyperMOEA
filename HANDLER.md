# HANDLER — Registro compartilhado do projeto NewMOEA

## Objetivo geral
Estabelecer uma configuração experimental consistente, reprodutível e
cientificamente justificável para comparação de MOEAs em WFG e DTLZ.

## Estado atual
- Pipeline principal: `WFGStudy.java`.
- jMetal 7.8, Java 21 e Maven.
- Execuções padrão: 30.
- Objetivos: 2, 3, 5, 8, 10 e 15.
- Algoritmos ativos: NSGA-III, MOEA/DD, FDEA, RVEA, AGE-MOEA e AGE-MOEA-II.
- Indicadores: PISAHypervolume, NormalizedHypervolume, IGD, IGD+ e R2.

## Decisões técnicas
1. Populações canônicas por m: 100, 91, 210, 156, 275 e 135.
2. Orçamento comum: gerações × população.
3. SBX: probabilidade 1.0 e índice de distribuição 30.0.
4. Mutação polinomial: probabilidade 1/n e índice 20.0, após auditoria da API.
5. NSGA-III deve usar camadas explícitas de Das & Dennis.
6. MOEA/DD deve validar arquivos de pesos antes da execução.
7. PISAHypervolume é a métrica primária.
8. NormalizedHypervolume é métrica secundária.
9. `Main-Hipervolume` não deve ser usado como métrica principal sem correção de
   semente, normalização, direção e ponto de referência.

## Matriz de procedência
| Artefato | Finalidade | Origem | Evidência | Status |
|---|---|---|---|---|
| `reference-calc.java` | A confirmar | A confirmar | A investigar | Pendente |
| `MOEAD_Weights/` | Vetores de pesos do MOEA/DD | A confirmar | A investigar | Pendente |
| `pareto_fronts/csv/` | Frentes de Pareto de referência | A confirmar | A investigar | Pendente |
| Gerador nativo NSGA-III | Pontos de referência no simplex | jMetal 7.8 | A confirmar | Pendente |

## Tarefas
- [ ] T01 — Investigar origem, finalidade e fluxo dos artefatos.
- [ ] T02 — Validar `reference-calc` e pontos do NSGA-III contra Das & Dennis.
- [ ] T03 — Validar pesos do MOEA/DD e alternativas nativas.
- [ ] T04 — Validar frentes de Pareto e recursos do jMetal.
- [ ] T05 — Padronizar parâmetros, NSGA-III, MOEA/DD e hipervolume.

## Critérios de validação
1. Procedência documentada ou explicitamente incerta.
2. Comparação automatizada entre geradores e arquivos utilizados.
3. Comparação independente da ordem das linhas.
4. Tolerância numérica explícita e justificada.
5. Relatório de quantidade, dimensionalidade, duplicatas, finitude, não
   negatividade e soma, quando aplicável.
6. Validação contra Das & Dennis somente para conjuntos que devem seguir esse
   método.
7. Validação de frentes de Pareto contra a fonte apropriada.
8. Testes negativos para arquivos incompatíveis.
9. Nenhuma alteração em arquivos de referência sem evidência e rastreabilidade.
10. `mvn clean test` bem-sucedido antes de concluir qualquer tarefa.

## Regras para agentes
1. Leia este arquivo antes de iniciar.
2. Atualize o status ao iniciar: `in_progress`, data, agente e arquivos alvo.
3. Não altere simultaneamente os mesmos arquivos sem coordenação.
4. Ao concluir, registre arquivos alterados, testes e resultados.
5. Não marque tarefa como concluída sem evidência verificável.
6. Registre limitações e pendências explicitamente.

## Pendências
- Confirmar se `reference-calc.java` gerou os arquivos de `MOEAD_Weights/`.
- Confirmar a origem e a validade das frentes em `pareto_fronts/csv/`.
- Confirmar as APIs nativas do jMetal 7.8 para pesos, pontos e frentes.
- Confirmar a assinatura exata de `PolynomialMutation` no jMetal 7.8.
- Confirmar se `MOEADBuilder` expõe \(\theta\) do PBI.
- Confirmar o ponto de referência definitivo para DTLZ.
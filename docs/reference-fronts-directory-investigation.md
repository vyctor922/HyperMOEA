# Investigação: por que as frentes de referência ativas estão em `pareto_fronts/csv/` e não em `resources/`

Data: 06/11/2026. Todos os fatos abaixo foram verificados no repositório nesta sessão (comandos executados, não inferidos de documentos de agentes).

## 1. Resposta curta

**Foi uma falha de especificação, não uma decisão científica.** Nenhum documento, teste ou relatório apresenta justificativa científica para manter as frentes de referência em `pareto_fronts/csv/`. O diretório foi mantido por herança do código (T01–T05) e depois foi protegido como "somente leitura" pelo `TASK_COORDINATOR.md`. Em nenhum ponto a especificação mandou copiar as frentes para `resources/` nem migrar o pipeline para lá.

Há ainda uma inconsistência de tratamento entre pesos e frentes (seção 4), que provavelmente é a origem da sua expectativa.

## 2. Evidências

### 2.1 O que o código faz hoje
- [`WFGStudy.java` linha 165](../src/main/java/br/usp/lti/newmoea/study/WFGStudy.java#L165): `.setReferenceFrontDirectory("pareto_fronts/csv")`.
- `WFGStudy.java` linhas 412, 422, 508, 521, 558: pesos do MOEA/DD e do GWASFGA ainda são lidos de `"MOEAD_Weights"`, não de `resources/weightVectorFiles/`.
- Nenhum código de produção lê frentes de `resources/`. O único uso de `resources/referenceFronts/pf_gen/` é como destino opcional de escrita (flags desligadas por padrão).

### 2.2 O que a especificação dizia
| Fonte | Trecho | Efeito |
|---|---|---|
| `TASK_COORDINATOR.md` linhas 370, 664, 703 | `pareto_fronts/csv/` "exclusivamente para leitura e inventário"; "deve permanecer em modo de leitura nesta fase" | Congelou o diretório antigo como fonte ativa. |
| `TASK_COORDINATOR.md` linhas 369, 385, 663, 707 | `resources/referenceFronts/pf_gen/` só para frentes "derivadas ou empíricas" geradas na fase | `resources/` foi definido como destino de **frentes novas**, não de cópia oficial. |
| `tasks/task7.md` linhas 116–125 | Inventariar frentes do jMetal e "comparar com os arquivos existentes em `pareto_fronts/csv/`"; "não copie arquivos sem validar" | Pediu comparação, não migração. |
| `tasks/task11.md` linhas 32–35, 127–128 | Pesos: copiar oficiais para `resources/weightVectorFiles/`, mas manter `MOEAD_Weights/` ativo até aprovação explícita | Previu migração futura **só para pesos**. |
| `AGENTS.md` linha 7 | Não substituir arquivos de `MOEAD_Weights/` ou `pareto_fronts/csv/` sem justificativa | Reforçou a proteção do diretório antigo. |
| `HANDLER.md` linha 81 | Decisão da T04: "Manter `pareto_fronts/csv/` inalterado" | Registrou a decisão como definitiva. |

Não existe nenhuma tarefa `tasks/task*.md` que mande copiar frentes oficiais para `resources/referenceFronts/` ou apontar o pipeline para lá.

### 2.3 Estado real dos arquivos
- `pareto_fronts/csv/` tem 160 CSVs, todos do commit inicial `6a6ecc2` (sem alterações posteriores).
- Comparei por hash SHA-256 com `jMetal/resources/referenceFrontsCSV/`: 159 dos 160 arquivos são idênticos. A única diferença é `ConstrEx.csv`, que não é usado no experimento.
- Portanto o conteúdo de `pareto_fronts/csv/` é, na prática, a cópia oficial do jMetal. O problema é o **local**, não o conteúdo.
- `resources/referenceFronts/` contém apenas `pf_gen/analytical/`, vazio (geração desligada por padrão). Não há cópia oficial de frentes em `resources/`.

## 3. Justificativa científica?
Não encontrei nenhuma. Os documentos só afirmam que as frentes são idênticas ao jMetal (verdadeiro) e que não se deve alterar artefatos históricos sem evidência (regra de preservação). Isso justifica **não sobrescrever** `pareto_fronts/csv/`, mas não justifica que ele seja a fonte oficial ativa. Copiar para `resources/` e apontar o pipeline para a cópia seria compatível com a regra de preservação.

## 4. Inconsistência entre pesos e frentes
- **Pesos:** o T11 criou `resources/weightVectorFiles/` (29 arquivos oficiais) e `weights_gen/`, e o relatório deixa explícito que o pipeline segue em `MOEAD_Weights/` até aprovação. A migração está prevista, mas **não foi feita**.
- **Frentes:** o T07 nem sequer criou uma cópia oficial em `resources/`. Isso é uma lacuna maior do que a dos pesos.
- Em ambos os casos o pipeline ainda lê os diretórios legados. Se a sua intenção era que `resources/` fosse a origem oficial para os dois, a especificação não refletiu isso.

## 5. Problema adicional encontrado: contagens erradas na documentação do T07
Contei as linhas reais dos CSVs (e os tamanhos em bytes batem com os do jMetal). [`docs/reference-fronts-dtlz.md`](reference-fronts-dtlz.md) cita valores diferentes:

| Arquivo | Linhas reais | Valor no doc do T07 |
|---|---:|---:|
| `DTLZ3.3D.csv` | 4000 | 10000 |
| `DTLZ4.3D.csv` | 4000 | 10000 |
| `DTLZ5.2D.csv` | 200 | 1000 |
| `DTLZ6.2D.csv` | 200 | 1000 |
| `DTLZ7.2D.csv` | 101 | 1002 |
| `DTLZ7.3D.csv` | 676 | 2916 |

`DTLZ1.2D`, `DTLZ1.3D`, `DTLZ2.*`, `DTLZ3.2D`, `DTLZ4.2D`, `DTLZ5.3D` e `DTLZ6.3D` batem com o doc. Esses números errados também afetam a tabela de hashes e contagens do relatório; ela deve ser tratada como não confiável até ser regerada. Não investiguei como o agente chegou a esses valores. Como a verificação do T07 aprovou a tabela, isso indica que o critério de aceitação foi checado contra o próprio texto do relatório, não contra os arquivos.

## 6. Conclusão
- Causa: especificação (`TASK_COORDINATOR.md`, `task7.md`) tratou `pareto_fronts/csv/` como fonte ativa e `resources/` apenas como destino de frentes geradas. Os agentes cumpriram a especificação.
- Sem justificativa científica registrada.
- Impacto científico: nenhum no conteúdo das frentes (idênticas ao jMetal), mas a rastreabilidade pedida (origem oficial em `resources/`) não existe, e há contagens erradas no relatório do T07.

## 7. Correções possíveis (nenhuma foi aplicada)
1. Copiar as frentes ativas (68 arquivos) para `resources/referenceFronts/official/`, com manifesto SHA-256, sem tocar em `pareto_fronts/csv/`.
2. Apontar `setReferenceFrontDirectory` para a nova pasta, com teste de integração e sem fallback silencioso.
3. Decidir a mesma coisa para os pesos (`MOEAD_Weights/` vs `resources/weightVectorFiles/`).
4. Regerar a tabela de inventário de `docs/reference-fronts-dtlz.md` a partir dos arquivos reais.

Os passos 1 a 3 alteram o pipeline e exigem sua autorização explícita (`AGENTS.md`, `TASK_COORDINATOR.md` seção 3).


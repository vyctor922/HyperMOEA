# HANDLER — Registro compartilhado do projeto hypeMOEA

## Objetivo geral
Estabelecer uma configuração experimental consistente, reprodutível e
cientificamente justificável para comparação de MOEAs em WFG e DTLZ.

## Estado na Fase 1
- Pipeline principal: `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`.
- Parâmetros estáticos e fábricas de referência: `src/main/java/br/usp/lti/newmoea/study/StaticParams.java`.
- Validadores matemáticos e analíticos:
  - `src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java`
  - `src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java`
- jMetal 7.8, Java 21 e Maven.
- Execuções padrão: 30 (`-Druns=30`).
- Objetivos: 2, 3, 5, 8, 10 e 15.
- Algoritmos ativos do estudo: NSGA-III, MOEA/DD, FDEA, RVEA, AGE-MOEA e AGE-MOEA-II (além de algoritmos complementares antigos mantidos no pipeline para fins de controle experimental de experimentos passados que já terminaram: NSGA-II, SPEA2, MOEA/D-DRA, ESPEA e GWASFGA).
- Indicadores configurados: `PISAHypervolume` (`HV`, métrica primária de maximização), `NormalizedHypervolume` (`NHV`, métrica secundária de minimização), `InvertedGenerationalDistance` (`IGD`), `InvertedGenerationalDistancePlus` (`IGD+`) e `R2`.

## Decisões técnicas consolidadas na Fase 1
1. Populações canônicas por $m$: 100 ($m=2$), 91 ($m=3$), 210 ($m=5$), 156 ($m=8$), 275 ($m=10$) e 135 ($m=15$).
2. Orçamento comum: $\text{maxEvaluations} = \text{maxGenerations} \times \text{populationSize}$.
3. SBX: probabilidade 1.0 e índice de distribuição 30.0 (`new SBXCrossover(1.0, 30.0)`).
4. Mutação polinomial: probabilidade $1/n$ e índice 20.0 (`new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)`), auditada contra o bytecode de `org.uma.jmetal.operator.mutation.impl.PolynomialMutation` no jMetal 7.8.
5. NSGA-III configurado com camadas explícitas de Das & Dennis / Deb & Jain (`StaticParams.getOuterDivisions(m)` e `StaticParams.getInnerDivisions(m)`) e validação prévia de consistência entre `populationSize` e o total de pontos de referência $\binom{H_1+m-1}{m-1} + \binom{H_2+m-1}{m-1}$.
6. MOEA/DD (`MOEADBuilder.Variant.MOEADD`) configurado com PBI ($\theta = 5.0$ fixo em `AbstractMOEAD.fitnessFunction`), $T = 20$, $\delta = 0.9$, $nr = 2$, validando explicitamente o arquivo de pesos `MOEAD_Weights/W{m}D_{populationSize}.dat` via `WFGStudy.validateMoeadWeightFile` antes de instanciar o algoritmo.
7. `PISAHypervolume` (`HV`) é a métrica primária de hipervolume; `NormalizedHypervolume` (`NHV`) é a métrica secundária, gerando arquivos de saída separados (`HV` e `NHV`) sem misturar HV bruto e HV normalizado.
8. Ponto de referência comum de hipervolume padronizado em `StaticParams.getHypervolumeReferencePoint(problemName, m)`: $(2.0, \dots, 2.0) \in \mathbb{R}^m$ para problemas WFG e $(1.0, \dots, 1.0) \in \mathbb{R}^m$ para problemas DTLZ.
9. `Main-Hipervolume.java` não é integrado ao pipeline principal devido às limitações comprovadas (semente `System.nanoTime()`, ponto de referência escalar único sem normalização interna e direção contraditória no comentário de cabeçalho).

## Matriz de procedência
| Artefato | Finalidade Real no Pipeline | Origem Classificada | Formato e Consumidor | Evidência Verificável | Status |
|---|---|---|---|---|---|
| `reference-calc.java` (`C:\Users\vyctor922\Desktop\reference-calc.java`) | Nenhuma no pipeline ativo (script standalone de console fora do repositório) | **Confirmada**: Script autoral avulso implementando enumeração aproximada em grade do simplex (Das & Dennis) com arredondamento a 5 casas decimais (`fator=100000`, `margemErro=1e-4`) e tentativa não-canônica de múltiplas camadas (`limInf = 1/(m+l)` com mesmo `divisoes`) | Saída em console separada por espaço; não consumido por nenhuma classe do projeto | Falha crítica para $m=2, H=99$ (`99 * 0.01010 = 0.99990`, não satisfaz `somaCol > 1 - margemErro`, gerando **0 pontos** em vez de 100); para `layers > 1` reutiliza o mesmo $H$ e aplica translação $1/(m+l) \neq \text{Deb \& Jain (2014)}$. **Provado que NÃO gerou `MOEAD_Weights/`** | Concluído (T01/T02) |
| `Main-Hipervolume.java` (`C:\Users\vyctor922\Desktop\Main-Hipervolume.java`) | Nenhuma no pipeline ativo (utilitário standalone CLI fora do repositório) | **Confirmada**: Código externo (`@author Richard A. Gonçalves e Carolina Paula de Almeida`) de aproximação de HV por Monte Carlo ($10^6$ amostras) | Lê arquivos no formato PISA; não consumido por `WFGStudy.java` | Usa `new Random(System.nanoTime())` (sem semente fixa), `refPoint` escalar único em $[0, \text{refPoint}]^m$, sem normalização interna e com comentário de cabeçalho contraditório sobre direção da métrica | Concluído (T01/T05) |
| `MOEAD_Weights/` (6 arquivos ativos: `W2D_100`, `W3D_91`, `W5D_210`, `W8D_156`, `W10D_275`, `W15D_135.dat`, dentre 43 arquivos no diretório) | Vetores de pesos / direções de decomposição para $m \in \{2,3,5,8,10,15\}$ | **Confirmada**: Gerador externo de simplex-lattice (1 camada para $m \in \{2,3,5\}$ e 2 camadas canônicas de Deb & Jain para $m \in \{8,10,15\}$) com substituição $0 \mapsto 10^{-5}$ (`1.00000000000000e-05`) antes da contração | Separados por `\t`, 15 dígitos de precisão. Consumidos por `AbstractMOEAD.initializeUniformWeight()` (`MOEADD`, `MOEADDRA`, exceto $m=2, N \le 300$ que gera em memória) e `GWASFGA` (`WeightVectors.readFromFile`) | Equivalência exata (`erro máx < 5e-16`) com `ReferenceSetValidator.generateZeroReplacedCanonical(..., 1e-5)`. Em $m \in \{8,10,15\}$, as coordenadas mínimas da camada interna (`0.062505`, `0.050005`, `0.0333383333333333`) seguem $0.5 \times 10^{-5} + \frac{0.5}{m}$. Outros 14 arquivos do diretório são cópias idênticas (SHA-256) de `jMetal/resources/weightVectorFiles/moead/` | Concluído (T01/T03) |
| `pareto_fronts/csv/` (`WFG1..9.{2,3,5,8,10,15}D.csv` e `DTLZ1..7.{2,3}D.csv`, dentre 160 arquivos) | Frentes de Pareto de referência para normalização e cálculo de `HV`, `NHV`, `IGD`, `IGD+` e `R2` | **Confirmada**: Cópias idênticas byte-a-byte (SHA-256) de `resources/referenceFrontsCSV/` do repositório oficial do jMetal (também convertidas de `pareto_fronts/*.pf` via `Untitled.ipynb` e duplicadas em `output_files/`) | CSV separado por vírgula. Consumido por `ComputeQualityIndicators.run()` via `VectorUtils.readVectors` e `NormalizeUtils.normalize` | SHA-256 100% idêntico ao jMetal. Equações analíticas validadas para DTLZ1–7 e WFG4–9 (`erro máx <= 7.83e-6`). Documentadas anomalias originais do jMetal: duplicatas em WFG $m \ge 5$, valores negativos em `WFG3.2D.csv` ($f_2 \in [-2, 4]$) e ausência de `DTLZ*.{5,8,10,15}D.csv` | Concluído (T01/T04) |
| Geradores nativos NSGA-III (`jMetal 7.8`) | Geração de pontos de referência no simplex unitário | **Confirmada**: `jmetal-core-7.8` (`ReferencePointGenerator`) e `jmetal-algorithm-7.8` (`NSGAIII` + `ReferencePoint`) | Em memória (`List<double[]>` ou `List<ReferencePoint<S>>`). Consumidos por `RVEABuilder` e `NSGAIIIBuilder` | `ReferencePointGenerator.generateTwoLayers` implementa Deb & Jain (2014) com erro `0.0`. Já `org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII` (linhas 58–71) usa $\frac{1+x_i}{m+1}$ na 2ª camada em vez de $\frac{1}{2m} + \frac{1}{2}x_i$ | Concluído (T01/T02) |

## Tarefas
- [x] T01 — Investigar origem, finalidade e fluxo dos artefatos (`status: completed` | Agente: Agente A / Coordenador | Data: 2026-10-27).
- [x] T02 — Validar `reference-calc` e pontos do NSGA-III contra Das & Dennis (`status: completed` | Agente: Agente B | Data: 2026-10-27).
- [x] T03 — Validar pesos do MOEA/DD e alternativas nativas (`status: completed` | Agente: Agente B | Data: 2026-10-27).
- [x] T04 — Validar frentes de Pareto e recursos do jMetal (`status: completed` | Agente: Agente C | Data: 2026-10-27).
- [x] T05 — Padronizar parâmetros, NSGA-III, MOEA/DD e hipervolume (`status: completed` | Agente: Agente D | Data: 2026-10-27).

## Plano de Execução Multiagente e Estado da Fase 1

| ID | Tarefa | Dependências | Agente Responsável | Estado Final | Arquivos Criados / Alterados | Critérios de Aceitação Verificados | Próxima Ação |
|---|---|---|---|---|---|---|---|
| **T01** | Investigar origem, finalidade e fluxo dos artefatos (`reference-calc.java`, `MOEAD_Weights/`, `pareto_fronts/csv/` e APIs nativas do jMetal 7.8) | Nenhuma | Agente A (`research` / Coordenador) | `completed` (2026-10-27) | `HANDLER.md` (nenhum arquivo de referência modificado) | Matriz de procedência completa; finalidade e origem confirmadas com evidência; APIs nativas jMetal 7.8 documentadas com classe/pacote/método/assinatura | Concluída |
| **T02** | Validar `reference-calc` e pontos do NSGA-III contra Das & Dennis | T01 | Agente B | `completed` (2026-10-27) | `src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java`, `src/test/java/br/usp/lti/newmoea/algorithm/NsgaIIIReferencePointsValidationTest.java`, `HANDLER.md` | 20 testes passando para $m \in \{2,3,5,8,10,15\}$; validação de contagem, dimensão, soma=1, não negatividade, duplicatas; comparação independente de ordem ($\tau = 10^{-6}$); testes negativos; diagnóstico completo de `reference-calc` | Concluída |
| **T03** | Validar arquivos de pesos do MOEA/DD e alternativas nativas | T01 | Agente B | `completed` (2026-10-27) | `src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java`, `src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java`, `HANDLER.md` | 19 testes passando para todos os $m \in \{2,3,5,8,10,15\}$; equivalência provada com gerador zero-replaced ($0 \mapsto 10^{-5}$); 7 testes negativos de rejeição controlada; recomendação fundamentada | Concluída |
| **T04** | Validar frentes de Pareto de referência e recursos do jMetal | T01 | Agente C | `completed` (2026-10-27) | `src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java`, `src/test/java/br/usp/lti/newmoea/qualityindicator/ParetoFrontValidationTest.java`, `HANDLER.md` | 118 testes passando; inventário das 68 frentes ativas (e 160 totais); validação analítica de DTLZ1–7 e WFG4–9; auditoria de WFG1–3 e duplicatas; 8 testes negativos; documentação das APIs nativas | Concluída |
| **T05** | Padronizar parâmetros, NSGA-III, MOEA/DD e hipervolume | T01, T02, T03, T04 | Agente D | `completed` (2026-10-27) | `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`, `src/main/java/br/usp/lti/newmoea/study/StaticParams.java`, `src/test/java/br/usp/lti/newmoea/algorithm/AlgorithmIntegrationTest.java`, `src/test/java/br/usp/lti/newmoea/qualityindicator/QualityIndicatorUnitTest.java`, `HANDLER.md` | `mvn clean test` passa (191 testes, 0 falhas); NSGA-III com camadas explícitas e validação populacional; MOEA/DD valida pesos antes da execução; HV bruto e NHV normalizado com referência comum; execuções `-Druns=1` em WFG1 ($m=2$) e DTLZ2 ($m=3$) validadas | Concluída |

## Resumo dos Resultados de Validação Matemática (T02, T03 e T04)

### 1. Equivalência de Conjuntos no Simplex (T02 — NSGA-III e `reference-calc.java`, $\tau = 10^{-6}$)
- **Justificativa da tolerância $\tau = 10^{-6}$**: Adequada para distinguir erros de truncamento de 5 casas decimais (`reference-calc`, erro de até $4 \times 10^{-5}$) e substituições $0 \mapsto 10^{-5}$ (`MOEAD_Weights/`), mantendo insensibilidade a ruído de ponto flutuante IEEE 754 (`~1e-16`).
- **`ReferencePointGenerator` (jMetal 7.8) vs Das & Dennis / Deb & Jain Canônico**:
  - `EQUIVALENT` para todos os $m \in \{2, 3, 5, 8, 10, 15\}$ (0 vetores sem correspondência, 0 duplicatas, maior erro absoluto por coordenada $= 0.0$, maior distância euclidiana $= 0.0$).
- **`NSGAIIIBuilder` (jMetal 7.8) vs Das & Dennis / Deb & Jain Canônico**:
  - Para 1 camada ($m \in \{2, 3, 5\}$, após correção de `innerDivisions = 0` em `WFGStudy.buildNSGAIII`): `EQUIVALENT` (erro $= 0.0$).
  - Para 2 camadas ($m \in \{8, 10, 15\}$): contagem exata ($156, 275, 135$), pontos válidos no simplex ($\sum x_i = 1, x_i > 0$, 0 duplicatas), mas `NOT_EQUIVALENT` na camada interna devido à fórmula interna de `NSGAIII.java` (linhas 58–71), que calcula $x_i' = \frac{x_i + 1}{m + 1}$ em vez de $\frac{1}{2}x_i + \frac{1}{2m}$ (erro máximo de coordenada de `0.340` em $m=8$, `0.368` em $m=10$ e `0.408` em $m=15$).
- **`reference-calc.java` vs Das & Dennis / Deb & Jain Canônico**:
  - $m=2, H=99$: `NOT_EQUIVALENT` (gera **0 pontos** devido a `99 * 0.01010 = 0.99990`, que falha em `somaCol > 1 - margemErro`).
  - $m=3, H=12$ e $m=5, H=6$: gera 91 e 210 pontos com erro máximo de coordenada `4.0e-5` e `2.0e-5` (`NOT_EQUIVALENT` em $\tau = 10^{-6}$, `EQUIVALENT` em $\tau = 5 \times 10^{-5}$).
  - $m \in \{8, 10, 15\}$ com `layers=2`: `NOT_EQUIVALENT` (gera 240, 440 e 240 pontos reutilizando $H_1$ na 2ª camada e deslocamento `1/(m+1)`).

### 2. Validação dos Arquivos de Pesos do MOEA/DD (T03 — `MOEAD_Weights/`)
- Todos os 6 arquivos ativos (`W2D_100.dat`, `W3D_91.dat`, `W5D_210.dat`, `W8D_156.dat`, `W10D_275.dat`, `W15D_135.dat`) possuem contagem exata igual à população canônica, dimensionalidade $m$, coordenadas finitas e estritamente positivas ($w_i \ge 10^{-5}$), 0 duplicatas, e são **`EQUIVALENT`** (erro máximo por coordenada $\le 4.72 \times 10^{-16}$) ao gerador canônico de Das & Dennis / Deb & Jain com substituição $0 \mapsto 10^{-5}$ antes da contração da camada interna.
- **Decisão**: Manter os arquivos em `MOEAD_Weights/` e validá-los obrigatoriamente antes da execução via `WFGStudy.validateMoeadWeightFile`.

### 3. Validação das Frentes de Pareto de Referência (T04 — `pareto_fronts/csv/`)
- Todas as 68 frentes referenciadas por `WFGStudy.java` (`WFG1..9` para $m \in \{2,3,5,8,10,15\}$ e `DTLZ1..7` para $m \in \{2,3\}$) existem, não estão vazias, possuem dimensionalidade exata $m$ e valores 100% finitos.
- **Validação analítica**:
  - `DTLZ1.2D/3D.csv`: plano $\sum f_j = 0.5$ validado com erro máximo `1.00e-9`.
  - `DTLZ2..6.2D/3D.csv`: esfera unitária $\sum f_j^2 = 1.0$ (e colinearidade $f_1 = f_2$ em DTLZ5/6) validada com erro máximo `1.57e-6`.
  - `DTLZ7.2D/3D.csv`: equação desconexa exata validada com erro máximo `4.98e-6`.
  - `WFG4..9.{2,3,5,8,10,15}D.csv`: hiper-elipsoide côncavo $\sum (f_j / (2j))^2 = 1.0$ validado nos 36 arquivos com erro máximo `7.83e-6`.
- **Decisão**: Manter `pareto_fronts/csv/` inalterado (arquivos idênticos em SHA-256 ao repositório oficial do jMetal) e validar sua integridade com `ParetoFrontValidator`.

## Comandos Executados e Resultados Reais
1. `mvn clean compile` $\to$ **BUILD SUCCESS** (12 classes principais compiladas com Java 21).
2. `mvn clean test` $\to$ **BUILD SUCCESS** (**191 testes executados, 0 falhas, 0 erros, 0 ignorados**):
   - `AlgorithmIntegrationTest`: 19 testes passando.
   - `NsgaIIIReferencePointsValidationTest`: 20 testes passando.
   - `MoeaddWeightValidationTest`: 19 testes passando.
   - `R2ExecutionObserverTest`: 3 testes passando.
   - `ParetoFrontValidationTest`: 118 testes passando.
   - `QualityIndicatorUnitTest`: 12 testes passando.
3. `mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Druns=1" "-Dobjectives=2" "-Dproblem=WFG1"` $\to$ **BUILD SUCCESS** (`rpssize: 100`, geração de `FUN0.csv`, `VAR0.csv`, `HV`, `NHV`, `IGD`, `IGD+`, `R2`, tabelas LaTeX e scripts R concluída sem erros; artefatos preexistentes em `WFGStudy/` preservados após o teste).
4. `mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Druns=1" "-Dobjectives=3" "-Dproblem=DTLZ2"` $\to$ **BUILD SUCCESS** (`rpssize: 91`, cálculo de todos os 5 indicadores concluído sem erros; artefatos preexistentes em `WFGStudy/` preservados após o teste).

## Limitações Conhecidas e Pendências Resolvidas
- Todas as pendências listadas inicialmente neste arquivo foram investigadas, resolvidas e verificadas por testes automatizados:
  1. Confirmado por evidência matemática e de formato que `reference-calc.java` **não** gerou `MOEAD_Weights/`.
  2. Confirmada a origem oficial (jMetal) e a validade analítica das frentes em `pareto_fronts/csv/`, registrando-se as limitações intrínsecas dos arquivos do jMetal (`WFG3.2D.csv` com $f_2 < 0$, duplicatas em WFG $m \ge 5$, e ausência de frentes DTLZ para $m \in \{5, 8, 10, 15\}$).
  3. Confirmadas e documentadas todas as APIs nativas do jMetal 7.8 (e a ausência de arquivos de pesos/frentes empacotados nos JARs do Maven Central).
  4. Confirmada a assinatura exata de `PolynomialMutation(double mutationProbability, double distributionIndex)`.
  5. Confirmado que `MOEADBuilder` não expõe setter para $\theta$ do PBI, mas `AbstractMOEAD.fitnessFunction` já fixa internamente `double theta = 5.0`.
  6. Padronizado o ponto de referência de hipervolume em $(2, \dots, 2)$ para WFG e $(1, \dots, 1)$ para DTLZ via `StaticParams.getHypervolumeReferencePoint`.
- Confirmar o ponto de referência definitivo para DTLZ.

## Fase 2 — Extensão DTLZ, hipervolume e desempenho

### Estado
- T06: completed
- T07: completed
- T08: completed
- T09: completed
- T10: completed
- T11: completed

### Decisões provisórias a validar
1. A chamada atual de `PolynomialMutation(1.0, n, 20.0)` precisa ser auditada.
2. DTLZ permanece habilitado apenas para m=3 até existirem frentes válidas.
3. Pontos de HV para DTLZ1 e DTLZ7 não estão definitivamente decididos.
4. A normalização de `NormalizedHypervolume` precisa ser auditada no jMetal 7.8.
5. NSGA-III e RVEA devem ser revalidados como segunda opinião, mesmo após T02.
6. A persistência (salvar em arquivos) de pontos do NSGA-III e direções do RVEA será opcional, desativada por padrão e não deve alterar os vetores usados pelos algoritmos.
7. `MOEAD_Weights/` será preservado como diretório legado/atual.
8. Os arquivos oficiais de pesos do jMetal 7.8 serão copiados fielmente para `resources/weightVectorFiles/`.
9. A substituição `0 -> 1e-5` (epsilon) será opcional, desativada por padrão, aplicada apenas aos arquivos cuja necessidade seja comprovada e gerando variantes em `resources/weightVectorFiles/weights_gen/`. 
10. A geração empírica de frentes será opcional, desativada por padrão e utilizada apenas quando não houver referência matemática ou frente oficial tecnicamente válida. 
11. Os pontos de referência de hipervolume serão definidos por problema e número de objetivos, utilizando os mesmos parâmetros para todos os algoritmos da mesma configuração experimental. 
12. O cálculo exato do HV utilizado no pipeline e eventuais métodos aproximados serão identificados e documentados separadamente. 
13. HV bruto e NHV permanecerão separados nos resultados experimentais.


### Registro de execução
| Data | Tarefa | Agente | Estado | Evidência | Próxima ação |
|---|---|---|---|---|---|
| 2026-11-03 | Fase 2 criada | Coordenador | completed | `TASK_COORDINATOR.md` e `tasks/task6..11.md` | Executar T06 |
| 2026-11-03 | Verificação inicial T06–T11 | Coordenador | completed | Verificado estado real do repositório: `docs/` e `resources/` ainda não existem; todas as tarefas T06–T11 estão efetivamente pendentes. Isolamento comprovado entre T06 e T11 para execução paralela. | Iniciar T06 (Agente A) e T11 (Agente B) em paralelo |
| 2026-11-03 | T06 | Agente A | completed | Auditoria de `PolynomialMutation`, `SBXCrossover` e `DifferentialEvolutionCrossover` no jMetal 7.8 iniciada com isolamento em `docs/operator-audit.md` e `src/test/java/br/usp/lti/newmoea/operator/OperatorAuditTest.java`. | Concluir auditoria e testes de T06 |
| 2026-11-03 | T11 | Agente B | completed | Auditoria completa de `MOEAD_Weights/` vs recursos do jMetal 7.8 iniciada com isolamento em `ReferenceSetValidator.java`, `MoeaddWeightValidationTest.java`, `resources/weightVectorFiles/`, `docs/moead-weights-audit.md` e `docs/moead-weights-manifest.csv`. | Concluir inventário, sincronização, opção `replaceZeroWeights` e testes de T11 |
| 2026-11-03 | T06 | Agente A | completed | Criados `docs/operator-audit.md` e `src/test/java/br/usp/lti/newmoea/operator/OperatorAuditTest.java` (`mvn test -Dtest=OperatorAuditTest` -> 7 testes passando, 0 falhas). Confirmados em bytecode os 7 construtores de `PolynomialMutation` e 5 de `SBXCrossover`, probabilidade efetiva $1/n$, $\eta_m=20.0$, $\eta_c=30.0$ e reparo de limites; `WFGStudy.java` preservado sem necessidade de alteração. | Aguardar conclusão de T11 para iniciar T07 e T08 |
| 2026-11-03 | T11 | Agente B | completed | Criados `docs/moead-weights-audit.md`, `docs/moead-weights-manifest.csv`, cópias fiéis dos 29 arquivos oficiais do jMetal em `resources/weightVectorFiles/` e `resources/weightVectorFiles/`, e opção `-DreplaceZeroWeights=false` em `ReferenceSetValidator.java`. Provado em bytecode que `MOEA/DD` (`PBI`, `TCHE`, `AGG`) e `R2` não dividem por zero, enquanto `GWASFGA` (`WeightVectors.invert`) divide por zero quando $w_i = 0$. `mvn clean test` executado pelo Coordenador -> 201 testes passando, 0 falhas. | Iniciar T07 e T08 com isolamento estrito de arquivos |
| 2026-11-03 | T07 | Agente C | completed | Revalidação de NSGA-III e RVEA, persistência opcional (`-DpersistReferencePoints=false`, `-DpersistReferenceDirections=false`), inventário e geração matemática/empírica opcional de frentes DTLZ (`-DgenerateAllReferenceFronts=false`, `-DgenerateEmpiricalReferenceFronts=false`) iniciada com isolamento em `WFGStudy.java`, `ReferenceSetValidator.java`, `ParetoFrontValidator.java`, `docs/reference-points-audit.md` e `docs/reference-fronts-dtlz.md`. | Concluir entregáveis e testes de T07 |
| 2026-11-03 | T08 | Agente D | completed | Auditoria de `PISAHypervolume`, `NormalizedHypervolume`, `ComputeQualityIndicators` e definição de pontos de referência por problema/$m$ iniciada com isolamento em `StaticParams.java`, `QualityIndicatorUnitTest.java`, `HypervolumeAuditTest.java` e `docs/hypervolume-reference-points.md`. | Concluir entregáveis e testes de T08 |
| 2026-11-03 | T08 | Agente D | completed | Criados `docs/hypervolume-reference-points.md` e `src/test/java/br/usp/lti/newmoea/qualityindicator/HypervolumeAuditTest.java`, e atualizados `StaticParams.java` e `QualityIndicatorUnitTest.java` (`mvn test -Dtest=QualityIndicatorUnitTest,HypervolumeAuditTest` -> 24 testes passando, 0 falhas). Confirmado em bytecode que `PISAHypervolume`, `WFGHypervolume`, `Hypervolume3D` e `HypervolumeContribution2D` são exatos; diferenciados o regime normalizado (`ComputeQualityIndicators` + `VectorUtils.getInvertedFront` em $[0,1]^m$ com ref $(1,\dots,1)$) e o regime no espaço bruto (`StaticParams.getHypervolumeReferencePoint` por problema e $m$: $2.2j$ para WFG com $3.1$ em `WFG3` $m=2$, $0.6$ para DTLZ1, $1.1$ para DTLZ2–6 e $(1.1,\dots,1.1,2.1m)$ para DTLZ7, dominando estritamente 100% dos pontos das 68 frentes ativas). | Aguardar conclusão de T07 para validar conjunto e iniciar T09 |
| 2026-11-03 | T07 | Agente C | completed | Criados `docs/reference-points-audit.md`, `docs/reference-fronts-dtlz.md` e `src/test/java/br/usp/lti/newmoea/algorithm/RveaReferenceDirectionsValidationTest.java`, e atualizados `ReferenceSetValidator.java`, `ParetoFrontValidator.java`, `WFGStudy.java`, `NsgaIIIReferencePointsValidationTest.java` e `ParetoFrontValidationTest.java`. Revalidados NSGA-III (`ReferencePointGenerator` exato com erro $0.0$; discrepância intrínseca na 2ª camada de `NSGAIII` no jMetal 7.8 documentada) e RVEA (normalização $\ell_2$ unitária exata). Implementadas e testadas as flags opcionais desativadas por padrão (`-DpersistReferencePoints=false`, `-DpersistReferenceDirections=false`, `-DgenerateAllReferenceFronts=false`, `-DgenerateEmpiricalReferenceFronts=false`). `mvn clean test` executado pelo Coordenador -> 269 testes passando, 0 falhas. | Iniciar T09 (Agente E) |
| 2026-11-03 | T09 | Agente E | completed | Instrumentação e avaliação de desempenho do pipeline e indicadores iniciada com isolamento em `src/main/java/br/usp/lti/newmoea/util/`, `src/test/java/br/usp/lti/newmoea/util/`, `docs/performance/performance-protocol.md` e `docs/performance/performance-results.csv`. | Concluir medições reprodutíveis, protocolo, CSV e testes de T09 |
| 2026-11-04 | T09 | Agente E | completed | Criados `PerformanceProfiler.java`, `PerformanceProfilerTest.java` (8 testes), `docs/performance/performance-protocol.md` e `docs/performance/performance-results.csv` (630 registros = 90 execuções $\times$ 7 etapas: `FRONT_LOAD`, `NORMALIZATION`, `PISAHypervolume`, `NormalizedHypervolume`, `IGD`, `IGD+`, `R2`), e adicionada flag opcional `-DprofilePerformance=false` em `WFGStudy.java`. Implementado timeout padrão de 1h (`DEFAULT_HV_TIMEOUT_MS = 3_600_000L` ms) para cálculos de HV. Documentada a explosão combinatória de `PISAHypervolume` em `WFG4 | MOEA/DD | m=15` ($> 40.863\text{ s}$ de CPU com $N=135$ pontos interiores vs $64,55\text{ s}$ em `NSGA-III`) e a comparação exata entre `PISAHypervolume`, `WFGHypervolume` e `Hypervolume3D` em $m \in \{3, 8, 10, 15\}$. `mvn clean test` -> 277 testes passando, 0 falhas. | Iniciar T10 (Agente F — Validação final e consolidação) |
| 2026-11-04 | T10 | Agente F | completed | Validação final da Fase 2 (`mvn clean compile`, `mvn clean test`, testes de fumaça com `-Druns=1`, verificação de entregáveis de T06–T09 e T11, criação de `docs/final-validation-report.md` e atualização de `README.md` e `HANDLER.md`) iniciada. | Concluir validação final e documentação consolidada |
| 2026-11-04 | T10 | Agente F | completed | Criado `docs/final-validation-report.md` (consolidando todos os 13 itens da Seção 12 de `TASK_COORDINATOR.md`) e atualizado `README.md` (seções 1–6 e entradas de `03/11/2026 - 22:00` e `04/11/2026 - 10:15` na Seção 7). Executados `mvn clean compile` (BUILD SUCCESS, 13 classes), `mvn clean test` (BUILD SUCCESS, 277 testes em 10 classes, 0 falhas, 0 erros, 0 ignorados) e os 2 testes de fumaça com `-Druns=1` (`WFG1` em $m=2$ e `DTLZ2` em $m=3$), confirmando gravação separada de `HV` e `NHV` e preservando integralmente `MOEAD_Weights/`, `pareto_fronts/csv/` e `WFGStudy/`. | Fase 2 concluída; iniciar Fase 3 (integração das hiper-heurísticas com $\Delta R^2$ e execução experimental completa) |

### Decisões Consolidadas e Validadas na Fase 2 (T06–T11)
1. **Operadores Genéticos (`T06`, `docs/operator-audit.md`)**: Confirmados em bytecode os 7 construtores de `PolynomialMutation` e os 5 construtores de `SBXCrossover` em `jmetal-core-7.8.jar`. `WFGStudy.java` aplica `new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)` ($p_m = 1/n, \eta_m = 20.0$) e `new SBXCrossover(1.0, 30.0)` ($p_c = 1.0, \eta_c = 30.0$) com reparo `RepairDoubleSolutionWithBoundValue`.
2. **Vetores de Peso do MOEA/DD (`T11`, `docs/moead-weights-audit.md`, `docs/moead-weights-manifest.csv`)**: Copiados fielmente os 29 arquivos oficiais do jMetal 7.8 para `resources/weightVectorFiles/` e `resources/weightVectorFiles/`, preservando os 48 arquivos históricos em `MOEAD_Weights/`. Provado em bytecode que `MOEA/DD` (`PBI`, `TCHE`, `AGG`) e `R2` não dividem por zero quando $w_i = 0$, ao passo que `GWASFGA` (`WeightVectors.invert`) divide por zero (`1.0 / w[i][j]`). Implementada a opção `-DreplaceZeroWeights=false` (desativada por padrão) com saída isolada em `resources/weightVectorFiles/weights_gen/`.
3. **NSGA-III, RVEA e Frentes de Pareto DTLZ (`T07`, `docs/reference-points-audit.md`, `docs/reference-fronts-dtlz.md`)**:
   - Validado que `ReferencePointGenerator` é 100% equivalente ao gerador canônico de Das & Dennis / Deb & Jain (erro $0.0$). Documentado que `NSGAIII` no jMetal 7.8 é idêntico na camada externa ($H_1$), mas aplica $x_i^{\text{inner}} = (x_i + 1)/(m + 1)$ na camada interna ($H_2$) para $m \in \{8, 10, 15\}$.
   - Validado que `RVEA` normaliza todas as direções para a esfera unitária $\ell_2$ ($\|\mathbf{v}_i\|_2 = 1$).
   - Implementadas as geradoras matemáticas exatas de `DTLZ1–DTLZ7` para $m \in \{2, 3, 5, 8, 10, 15\}$ e as flags opcionais desativadas por padrão `-DpersistReferencePoints=false`, `-DpersistReferenceDirections=false`, `-DgenerateAllReferenceFronts=false` e `-DgenerateEmpiricalReferenceFronts=false`.
4. **Hipervolume e Normalização (`T08`, `docs/hypervolume-reference-points.md`)**: Confirmado em bytecode que `PISAHypervolume`, `WFGHypervolume`, `Hypervolume3D` e `HypervolumeContribution2D` são exatos e determinísticos. Diferenciados o regime normalizado do `ComputeQualityIndicators` (em $[0,1]^m$ com ponto implícito $(1.0,\dots,1.0)$ via `VectorUtils.getInvertedFront`) e o regime no espaço bruto em `StaticParams.getHypervolumeReferencePoint(problemName, m)` ($z_j = 2.2j$ para `WFG1–WFG9` com $z_1 = 3.1$ em `WFG3` $m=2$, $0.6$ para `DTLZ1`, $1.1$ para `DTLZ2–DTLZ6` e $(1.1,\dots,1.1,2.1m)$ para `DTLZ7`), que domina estritamente 100% dos pontos das 68 frentes de referência ativas. `HV` e `NHV` permanecem rigorosamente separados.
5. **Instrumentação e Desempenho (`T09`, `docs/performance/performance-protocol.md`, `docs/performance/performance-results.csv`)**: Criado `PerformanceProfiler` com distinção estrita entre *wall-clock time* e *CPU time* e entre memória `heap_used_mb`, `heap_peak_mb`, `non_heap` e `heap_max_mb`, medindo separadamente o algoritmo e as 7 etapas pós-algoritmo (`FRONT_LOAD`, `NORMALIZATION`, `PISAHypervolume`, `NormalizedHypervolume`, `IGD`, `IGD+`, `R2`) com limite máximo padrão de segurança de 1 hora (`DEFAULT_HV_TIMEOUT_MS = 3_600_000L` ms, `-Dperf.hvTimeoutMs=3600000`) por análise de hipervolume.
6. **Validação Final e Relatório Consolidado (`T10`, `docs/final-validation-report.md`)**: `mvn clean compile` e `mvn clean test` aprovados com **277 testes executados em 10 classes (0 falhas, 0 erros, 0 ignorados)** e 2 validações de fumaça aprovadas (`WFG1` em $m=2$ e `DTLZ2` em $m=3$ com `-Druns=1`).

## Fase 3 — Ajustes para homologar primeira campanha de MOEAs isolados
Antes da adaptação de hiper-heurísticas rodar os seis algoritmos, DTLZ/WFG, m=2,3,5,8,10,15, 30 runs.

### Estado confirmado na fase 3
- WFGStudy adiciona onze algoritmos incondicionalmente; cinco não são principais no coordenador.
- DTLZ m<=3 padrão; m>3 depende de geração opcional e referência aprovada.
- Pipeline lê pareto_fronts/csv e MOEAD_Weights; referências DTLZ geradas em resources são acessadas por relativize.
- SBX(1.0,30.0), PolynomialMutation(1.0/n,20.0).
- AGE/AGE-II incrementam população ímpar localmente.
- ComputeQualityIndicators executa antes do profiler opcional.
- B real, fontes FDEA, conteúdo das PFs, profiler e estatística não foram executados/verificados nesta preparação.

### Estado das Tarefas
| ID | Tarefa | Estado | Responsável | Arquivos reservado | Evidência/commit | Gate | Próxima ação |
|---| --- |---|---|---|---|---|---|
| F3 | Fase 3 criada | completed_validated (2026-11-09 01:31) | Coordenador | nenhum | `TASK_COORDINATOR.md` e `tasks/task12..24.md` | G0 liberado | Executar T13, T14, T16 em paralelo |
| T12 | Reconciliação de governança, inventário forense e definição de escopo experimental | completed_validated (2026-11-09 01:31) | Coordenador | nenhum | `docs/auditoria/inventario.csv`, `docs/auditoria/inventario.md`, `docs/auditoria/estado-tarefas.md`, `docs/decisions/G0-G1-scope-and-decisions.md`, `docs/T12-coordenador-validated.md` | G0 liberado | Concluída |
| T13 | Contrato e auditoria do orçamento de avaliações, tamanho efetivo de população e condições de parada | completed_validated (2026-11-09 01:48) | Agente B (Budget Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/util/EvaluationBudgetProblem.java`, `src/test/java/br/usp/lti/newmoea/algorithm/EvaluationBudgetAuditTest.java` (17 testes passando), `docs/auditoria/budget-audit.csv`, `docs/auditoria/budget-audit.md`, `docs/T13-agenteB-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T14 | Auditoria de identidade algorítmica do FDEA, algoritmos legados e parâmetros internos | completed_validated (2026-11-09 01:53) | Agente A (Algorithm Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEA.java`, `src/main/java/br/usp/lti/newmoea/algorithm/fdea/FDEABuilder.java`, `src/test/java/br/usp/lti/newmoea/algorithm/FdeaIdentityAndLegacyAuditTest.java` (28 testes passando em conjunto com `AlgorithmIntegrationTest`), `docs/auditoria/algorithm-identity.md`, `docs/T14-agenteA-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T15 | Isolamento de execução, controle de sementes e reprodutibilidade do pipeline | completed_validated (2026-11-09 02:05) | Agente E (Isolation & RNG Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java`, `src/test/java/br/usp/lti/newmoea/study/IsolationAndReproducibilityTest.java` (15 testes passando, 0 falhas), `docs/auditoria/isolation-rng.md`, `docs/T15-agenteE-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T16 | Auditoria física e matemática das frentes de Pareto de referência e geração DTLZ para $m>3$ | completed_validated (2026-11-09 01:51) | Agente C (Reference Fronts Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java`, `src/test/java/br/usp/lti/newmoea/qualityindicator/ReferenceFrontPhysicalAndMathAuditTest.java` (168 testes passando em conjunto com `ParetoFrontValidationTest`), `docs/auditoria/reference-fronts.md`, `docs/auditoria/reference-fronts-physical.csv`, `docs/T16-agenteC-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T17 | Contrato de normalização de objetivos, hipervolume exato, NHV e invariância de escala | completed_validated (2026-11-09 02:06) | Agente D (Normalization & HV Contract Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/util/ObjectiveNormalizationContract.java`, `src/test/java/br/usp/lti/newmoea/qualityindicator/NormalizationAndHvContractTest.java` (37 testes passando em conjunto com `HypervolumeAuditTest` e `QualityIndicatorUnitTest`), `docs/auditoria/normalization-hv-contract.md`, `docs/T17-agenteD-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T18 | Migração controlada de caminhos de artefatos e resolução canônica de recursos | completed_validated (2026-11-09 02:01) | Agente F (Artifact Resolver Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/util/ArtifactResolver.java`, `src/test/java/br/usp/lti/newmoea/util/ArtifactResolverTest.java`, `src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java` (33 testes passando, 0 falhas), `docs/auditoria/artifact-migration.md`, `docs/auditoria/artifact-manifest.csv`, `docs/T18-agenteF-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T19 | Contrato e validação da população final (`result()`) e arquivos `FUN`/`VAR` | completed_validated (2026-11-09 10:33) | Agente G (Result Contract Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/util/ResultFrontContract.java`, `src/test/java/br/usp/lti/newmoea/study/ResultFrontContractTest.java` (5 testes passando), `docs/auditoria/result-contract.md`, `docs/T19-agenteG-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T20 | Estratégia híbrida de hipervolume com isolamento de processo, cancelamento efetivo e Monte Carlo | completed_validated (2026-11-09 10:38) | Agente H (HV Worker Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/util/HypervolumeResult.java`, `src/main/java/br/usp/lti/newmoea/util/MonteCarloHypervolume.java`, `src/main/java/br/usp/lti/newmoea/util/ExactHypervolumeWorker.java`, `src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java`, `src/test/java/br/usp/lti/newmoea/qualityindicator/MonteCarloHypervolumeTest.java`, `src/test/java/br/usp/lti/newmoea/qualityindicator/ProcessIsolatedHypervolumeTest.java` (9 testes passando), `docs/auditoria/hv-hybrid.md`, `docs/T20-agenteH-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T21 | Plano de inferência estatística, pós-teste de Dunn-Holm, tamanhos de efeito e validação em R | completed_validated (2026-11-09 10:42) | Agente I (Statistics Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/statistics/KruskalWallisTest.java`, `src/main/java/br/usp/lti/newmoea/statistics/DunnPostHocTest.java`, `src/test/java/br/usp/lti/newmoea/statistics/KruskalWallisTestTest.java` (5 testes passando), `scripts/kruskal_dunn_holm.R`, `docs/statistics/analysis-plan.md`, `docs/T21-agenteI-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T22 | Reauditoria do `PerformanceProfiler`, medição de CPU/memória de workers e piloto experimental | completed_validated (2026-11-09 10:44) | Agente J (Performance Profiler Auditor) | nenhum | `src/test/java/br/usp/lti/newmoea/util/PerformanceProfilerPhase3Test.java` (4 testes passando), `docs/performance/phase3/performance-results-phase3.csv`, `docs/performance/phase3/performance-protocol-phase3.md`, `docs/T22-agenteJ-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T23 | Integração final no `WFGStudy`, modos de campanha, validação ponta a ponta e preparação do protocolo | completed_validated (2026-11-09 10:48) | Agente K (Study Integration Auditor) | nenhum | `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`, `src/test/java/br/usp/lti/newmoea/study/WFGStudyPreflightTest.java` (3 testes passando), `docs/auditoria/validation-phase3.md`, `docs/T23-agenteK-awaiting_validation.md` | G0 liberado / insumos G1 prontos | Concluída |
| T24 | Documentação metodológica consolidada, matriz de evidências e atualização do README | completed_validated (2026-11-09 10:54) | Coordenador | nenhum | `docs/methodology/` (9 documentos), `README.md`, `docs/T24-coordenador-awaiting_validation.md` | G0..G3 liberados | Fase 3 concluída |

Estado = estado atual da tarefa + data/hora

### Decisões Consolidadas e Validadas na Fase 3
1. **Reversão de Migração e Preservação de Diretórios Legados**: Em estrito cumprimento à diretriz do usuário, foi desfeita a cópia de arquivos de `MOEAD_Weights/` e `pareto_fronts/csv/` para `resources/weightVectorFiles/` e `resources/referenceFronts/`. Os diretórios `MOEAD_Weights/` e `pareto_fronts/csv/` são tratados exclusivamente como somente leitura (*read-only*), constituindo as fontes padrão de leitura do pipeline `WFGStudy.java`.
2. **Fontes de Recursos em `resources/`**:
   - `resources/weightVectorFiles/` mantém estritamente as cópias oficiais do repositório jMetal 7.8 (29 arquivos) e variantes geradas analiticamente por código em `weights_gen/` (ex: `W3D_91.dat`).
   - `resources/referenceFronts/` não duplica frentes legadas do jMetal, contendo apenas frentes complementares geradas analiticamente por código em `pf_gen/` (frentes DTLZ para $m \in \{5, 8, 10, 15\}$).
3. **Resolução no Pipeline Principal `WFGStudy.java`**:
   - Mantidos `DEFAULT_WEIGHTS_DIRECTORY = "MOEAD_Weights"` e `DEFAULT_REFERENCE_FRONT_DIRECTORY = "pareto_fronts/csv"`.
   - Frentes analíticas geradas por `ParetoFrontValidator` em `resources/referenceFronts/pf_gen/` são vinculadas dinamicamente via caminho relativo a partir do diretório de referência configurado (`pareto_fronts/csv`).
4. **Resolução Canônica e Manifesto (`ArtifactResolver.java`)**:
   - Restauradas as políticas canônicas de leitura direta (`MOEAD_WEIGHTS_DIR`, `RESOURCES_JMETAL_DIR`, `RESOURCES_WEIGHTS_GEN_DIR`, etc.), auditando 114 manifestos canônicos.
5. **Homologação e Validação Integral**: Toda a suíte de testes do projeto e execuções de fumaça ponta a ponta em WFG1 ($m=2$) e DTLZ2 ($m=5$, com geração analítica de frente) validadas e aprovadas sem erros nem falhas.
6. **Sincronização de Recursos Oficiais do jMetal 7.8/7.11**: Conforme diretriz do usuário, foram copiadas as 365 frentes de Pareto em CSV de `jMetal/resources/referenceFrontsCSV/` para `resources/referenceFronts/` (preservando `pf_gen/` intacto), os vetores do MOMBI2 em `resources/weightVectorFiles/mombi2/` e as pastas complementares (`REnadirandidealpoints`, `referenceFrontsRSG`, `referenceFrontsTSP`, `tspInstances`, `mokpInstances`, `ebes`, `fapInstances`, `unitTestsData`). Confirmado o diagnóstico da precedência de busca (`pasta -> subpasta_gen -> gerar e salvar em **_gen`).

### Decisões e Ajustes Consolidados — Prioridade Canônica, Auditoria de Recursos e Separação de Variantes de Zero (09/11/2026 - 23:55)
1. **Prioridade Canônica das Frentes de Pareto de Referência**:
   - Implementada e validada em `ParetoFrontValidator.java`, `ArtifactResolver.java` e `WFGStudy.java` a hierarquia estrita de 3 níveis:
     1. `resources/referenceFronts/{PROBLEM}.{m}D.csv` (fonte canônica prioritária);
     2. `resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv` (frentes analíticas puras complementares para dimensões não distribuídas oficialmente);
     3. Geração por código sob demanda, gravando exclusivamente em `pf_gen/`, disparada **somente** quando o arquivo estiver ausente nas fontes anteriores (eliminando regenerações desnecessárias).
2. **Auditoria e Limpeza de Recursos em `resources/`**:
   - Foram removidos exclusivamente os diretórios e arquivos associados a benchmarks e algoritmos comprovadamente fora do escopo contínuo (WFG e DTLZ) do projeto:
     - Removidos 9 diretórios externos: `ebes`, `fapInstances`, `mokpInstances`, `referenceFrontsRSG`, `referenceFrontsTSP`, `REnadirandidealpoints`, `tspInstances`, `unitTestsData` e `weightVectorFiles/mombi2`.
     - Removidos 273 arquivos CSV de problemas fora do escopo em `resources/referenceFronts/` (ZDT, UF, CF, ZCAT, MaF, LZ09, LIRCMOP, etc.).
   - Foram preservados integralmente:
     - 92 frentes de referência oficiais de WFG (54 arquivos) e DTLZ (38 arquivos);
     - 29 arquivos oficiais de vetores de peso em `resources/weightVectorFiles/` + subpasta `weights_gen/`;
     - Pontos e direções de referência de algoritmos do escopo em `resources/referencePoints/` (`nsgaiii` e `rvea`).
3. **Separação Estrita de Frentes com Variantes de Zero**:
   - `resources/referenceFronts/pf_gen/`: reservado **exclusivamente** para frentes analíticas sem substituição de coordenadas zero (mantendo os zeros matematicamente exatos no simplex).
   - `resources/referenceFronts/pf_lowzero_gen/`: diretório criado e reservado **exclusivamente** para variantes geradas com coordenadas zero substituídas por $\epsilon = 1.0 \times 10^{-5}$ (`ParetoFrontValidator.applyZeroReplacement`, `generateAndSaveLowZeroDtlzFront` e `createLowZeroVariantFromFile`).
   - Propriedade JVM de controle: `-DreplaceZeroFronts=true` (desativada por padrão).
4. **Alinhamento do Pipeline `WFGStudy.java`**:
   - `DEFAULT_REFERENCE_FRONT_DIRECTORY` atualizado para `"resources/referenceFronts"`.
   - `DEFAULT_WEIGHTS_DIRECTORY` atualizado para `"resources/weightVectorFiles"`.
   - `resolveReferenceFrontForStudy` e `resolveMoeadDataDirectory` garantem resolução limpa (sem acoplamento frágil `../../`) com fallback seguro para `MOEAD_Weights` e `pareto_fronts/csv`.
5. **Evidências de Teste**:
   - Criada a classe [`ReferenceFrontCanonicalPriorityTest.java`](src/test/java/br/usp/lti/newmoea/qualityindicator/ReferenceFrontCanonicalPriorityTest.java) com 6 testes específicos aprovados.
   - Execução completa de `mvn test`: **378 testes executados, 0 falhas, 0 erros** (BUILD SUCCESS).

### Evidências e limitações
Ver docs/auditoria/DIAGNOSTICO.md, FONTES.md e inventário de checksums. Valores de tempo/contagens de CSV antigos só passam a confirmados após reprodução/inspeção física. APIs latest não equivalem ao JAR 7.8.


## Fase 4 — Refatoração arquitetural e resolução canônica de artefatos

### Objetivo

Investigar e eliminar duplicações reais, definir fontes de verdade por responsabilidade, corrigir a resolução dos artefatos e migrar os consumidores sem descaracterizar o funcionamento experimental.

A implementação deverá ocorrer somente após a consolidação e a aprovação da análise arquitetural.

Não reiniciar automaticamente T01–T24. Consultar seus resultados e reabrir apenas atividades afetadas por evidência nova ou pela alteração explícita de requisito.

### Nova orientação do operador

A resolução operacional de artefatos deverá seguir:

1. Recursos oficiais em `resources/`, com conteúdo, procedência e compatibilidade validados.
2. Recursos gerados e validados nos diretórios correspondentes, como `pf_gen/` e `weights_gen/`.
3. Geração programática adequada, com persistência em diretório de gerados e validação antes da disponibilização.

A prioridade considera a validade, não apenas a existência do arquivo.

O código ativo não poderá realizar leitura, busca, verificação de existência ou fallback para:

- `MOEAD_Weights/`;
- `pareto_fronts/csv/`.

Essa orientação substitui as permissões anteriores de fallback operacional.

Os arquivos físicos históricos permanecem protegidos. A mudança não autoriza sua exclusão, edição, movimentação ou reformatação.

A leitura offline estritamente necessária ao inventário e à migração deverá ser identificada como atividade de migração, separada da resolução utilizada pelo experimento.

### Diagnóstico arquitetural inicial

#### Constatações no snapshot disponibilizado

- `WFGStudy.resolveMoeadDataDirectory` verifica a existência do arquivo na pasta configurada, em `weights_gen/` e no diretório legado.
- `buildMOEADD` resolve um diretório e chama a validação, que volta a executar a resolução.
- `runPreflightValidation` contém fallback explícito para `pareto_fronts/csv`.
- `resolveReferenceFrontForStudy` recebe uma frente resolvida e a adapta para o formato textual utilizado pelo experimento.

Essas constatações deverão ser conferidas no código atual da IDE, pois o snapshot pode não representar alterações posteriores.

#### Hipóteses que ainda exigem inspeção

| Foco | Classes envolvidas | Questão a verificar |
|---|---|---|
| Resolução de frentes | `ParetoFrontValidator`, `WFGStudy`, `PerformanceProfiler` | Há políticas paralelas ou apenas adaptações legítimas? |
| Validação de pesos | `WFGStudy`, `EvaluationBudgetProblem`, `ReferenceSetValidator` | A mesma regra de localização ou validação é executada em mais de um ponto? |
| Simplex, direções e frentes | `ReferenceSetValidator`, `ReferencePointGenerator`, `ParetoFrontValidator` | Quais primitivas são equivalentes e quais representam conceitos matemáticos diferentes? |
| Dominância | `ParetoFrontValidator`, `ResultFrontContract` | Há implementações equivalentes ou diferenças de sentido, tolerância, igualdade ou restrições? |
| Infraestrutura | Resolvedores, leitores, validadores e manifestos | Há duplicação real de parsing, hash, escrita, normalização ou metadados? |

Não considerar uma duplicação comprovada apenas porque dois métodos possuem nomes semelhantes.

#### Responsabilidades do ArtifactResolver

Sua implementação e todos os consumidores deverão ser inventariados antes da migração.

A investigação deverá localizar, quando existentes:

- Políticas e categorias de origem.
- Resolução de frentes e pesos.
- Regras de geração em memória.
- Objetos de resultado.
- Construção e exportação de manifestos.
- Cálculo de hash.
- Compatibilidade com caminhos antigos.
- Testes que precisam ser preservados ou substituídos.

A exclusão da classe e de seu teste permanece condicionada à migração completa, à atualização dos consumidores e à aprovação dos testes substitutos.

### Decisões arquiteturais ainda pendentes

1. Proprietário de cada responsabilidade e assinatura dos métodos.
2. Estrutura dos retornos equivalentes a `ResolvedFrontArtifact` e `ResolvedWeightArtifact`.
3. Forma de injeção e propagação de `baseDir`.
4. Proprietário da construção e exportação do manifesto.
5. Núcleo reutilizável de dominância.
6. Primitivas matemáticas compartilháveis.
7. Tratamento de candidatos existentes, mas inválidos.
8. Publicação segura de arquivos gerados.
9. Representação explícita de artefatos em memória.
10. Métodos preservados, delegados, descontinuados e removidos.

Essas decisões deverão constar em `docs/resolucao_duplicatas_arquitetura.md`, produzido pelos agentes na IDE.

### Matriz inicial de dependências

IDs propostos: T25–T31. Confirmar a disponibilidade dos números em `tasks/` antes da execução. Em caso de colisão, registrar o remapeamento sem sobrescrever tarefas.

| ID | Descrição | Dependências | Paralelismo | Arquivos potencialmente compartilhados | Condição de bloqueio | Estado inicial |
|---|---|---|---|---|---|---|
| T25 | Inventário e caracterização da resolução atual | Histórico e tarefas existentes | Inicialmente sequencial | Testes de caracterização; eventos | Ausência de arquivos necessários à conclusão do inventário | `pending` |
| T26 | Duplicatas e arquitetura de destino | T25 | Pode ocorrer com T27, em documentos distintos | Documento arquitetural | Evidência insuficiente ou arquitetura sem aprovação | `pending` |
| T27 | Plano de mitigação da dívida técnica | T25; alinhamento com T26 | Pode ocorrer com T26 | Plano de dívida técnica | Dívida sem evidência ou dependência indefinida | `pending` |
| T28 | Primitivas matemáticas e validações reutilizáveis | T26 aprovada | Somente por arquivos independentes | Validadores; `ResultFrontContract` | Semânticas divergentes sem decisão aprovada | `pending` |
| T29 | Resolução canônica, resultados ricos, baseDir e política de memória | T28; T26 aprovada | Sequencial nos arquivos compartilhados | Validadores; `WFGStudy`; DTOs | Artefatos sem validação, geração inadequada ou contrato de memória indefinido | `pending` |
| T30 | Consumidores e manifesto | T29 | Condicionado à reserva de arquivos | `WFGStudy`; profiler; contador; manifesto | Consumidor não migrado ou manifesto incompatível com o consumo | `pending` |
| T31 | Exclusão condicionada e validação final | T28–T30; plano inicial T27 | Integração sequencial | Resolver; testes; consumidores; documentação | Dependência residual, teste obrigatório reprovado ou evidência insuficiente | `pending` |

A T27 terá uma entrega inicial e uma atualização final após receber os resultados da T31. A atualização final da T27 não é pré-condição para iniciar T31.

### Plano inicial de mitigação

| Prioridade | Intervenção prevista | Risco principal | Validação necessária |
|---|---|---|---|
| P0 | Remover buscas operacionais legadas após migração dos artefatos necessários | Perda de configuração antes atendida pelo fallback | Inventário, testes de resolução e ausência de acesso legado |
| P0 | Selecionar artefatos pela validade do conteúdo | Aceitação de arquivo existente, mas incompatível | Testes negativos e precedência entre candidatos |
| P0 | Definir sucessores das responsabilidades do ArtifactResolver | Remoção prematura de comportamento necessário | Matriz de responsabilidades, consumidores e testes substitutos |
| P0 | Introduzir resultados estruturados e baseDir | Divergência de caminhos e metadados | Diretórios temporários, bases distintas e testes dos consumidores |
| P0 | Migrar o manifesto para os artefatos realmente selecionados e utilizados | Manifesto declarar consumo que não ocorreu | Correspondência entre objeto resolvido, arquivo, hash e modo efetivo |
| P1 | Centralizar duplicações matemáticas comprovadas | Unificação de conceitos diferentes | Caracterização, testes matemáticos e análise dos contratos |
| P1 | Reduzir acoplamento e testes dependentes do workspace | Regressão ou abstrações desnecessárias | Refatorações localizadas e regressões |
| P1 | Registrar dívida residual | Declarar arquitetura concluída com pendências ocultas | Critérios de encerramento por item |

O plano detalhado será produzido na IDE em `docs/plano_mitigacao_divida_tecnica.md`.

### Regras específicas de execução

- Nenhuma refatoração de produção antes da aprovação arquitetural da T26.
- Não criar uma nova classe genérica apenas para substituir o nome `ArtifactResolver`.
- Não concentrar responsabilidades incompatíveis em um validador excessivamente amplo.
- Não alterar fórmulas, operadores, sementes, orçamentos ou normalização como efeito colateral.
- Não tratar hash como prova de procedência ou correção matemática.
- A regra opcional de dispensa do arquivo de pesos em memória deverá pertencer a `WFGStudy.validateMoeadWeightFile`, delegando validação matemática sem duplicá-la.
- A propriedade de controle ficará desativada por padrão; seu nome definitivo será confirmado conforme as convenções existentes.
- Verificar no jMetal instalado a condição preliminar `m=2` e `N<=300`.
- Distinguir a política de exigir arquivo no preflight do comportamento interno efetivo do framework.
- Não registrar consumo de arquivo se o algoritmo efetivamente gerar os pesos em memória.
- `EvaluationBudgetProblem` não deverá resolver ou gerar artefatos a cada avaliação.
- Testes utilizarão diretórios temporários e não dependerão de alteração de `user.dir`.
- Somente o coordenador editará `HANDLER.md` e `README.md`.

### Atualização contínua pelo coordenador

A cada conclusão, bloqueio, retomada, reabertura ou decisão:

1. Atualizar o estado da tarefa.
2. Registrar evidências e testes.
3. Corrigir o diagnóstico inicial conforme as descobertas.
4. Atualizar dependências e reservas de arquivos.
5. Registrar o proprietário de cada responsabilidade.
6. Atualizar a situação dos dois documentos técnicos.
7. Preservar decisões históricas e explicar revisões.
8. Manter a próxima ação claramente identificada.

Os documentos arquiteturais não foram produzidos nesta etapa de planejamento.

### Critério de encerramento

A fase somente será validada quando:

- As fontes de verdade estiverem definidas e implementadas.
- Os consumidores utilizarem a resolução canônica.
- Não houver acesso operacional aos caminhos legados.
- Os retornos estruturados e o baseDir estiverem testados.
- O manifesto corresponder aos artefatos e modos efetivos.
- As responsabilidades do ArtifactResolver estiverem migradas.
- Sua exclusão, quando elegível, estiver coberta por testes substitutos.
- Os testes obrigatórios e regressões estiverem aprovados.
- Os documentos arquiteturais e o contexto compartilhado estiverem atualizados.

Não iniciar automaticamente a campanha experimental completa.
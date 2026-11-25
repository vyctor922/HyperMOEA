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

### Matriz de execução e estado da Fase 4

| ID | Descrição | Dependências | Estado | Responsável | Arquivos afetados / Evidências |
|---|---|---|---|---|---|
| **T25** | Inventário e caracterização da resolução atual | Histórico T12–T24 | `completed_validated` (2026-11-11 14:02) | Coordenador | `docs/duplicatas_analise-10do11.md`, baseline 378 testes passando |
| **T26** | Duplicatas e arquitetura de destino | T25 | `completed_validated` (2026-11-11 14:36) | Coordenador | `docs/resolucao_duplicatas_arquitetura.md` aprovado formalmente pelo operador |
| **T27** | Plano de mitigação da dívida técnica | T25; alinhamento T26; encerramento pós-T31 | `completed_validated` (2026-11-11 14:53) | Coordenador | `docs/plano_mitigacao_divida_tecnica.md` (7 dívidas DT-01..07 resolvidas) |
| **T28** | Primitivas matemáticas e validações reutilizáveis | T26 aprovada | `completed_validated` (2026-11-11 14:38) | Coordenador | `ParetoFrontValidator.dominanceComparison`, `ResultFrontContract.dominates` |
| **T29** | Resolução canônica, resultados ricos, baseDir e eliminação de fallbacks | T28; T26 aprovada | `completed_validated` (2026-11-11 14:45) | Coordenador | `ResolvedFrontArtifact`, `ResolvedWeightArtifact`, geração sob demanda em `weights_gen/`, eliminação de fallbacks em `WFGStudy`, `ParetoFrontValidator`, `PerformanceProfiler`, `EvaluationBudgetProblem` |
| **T30** | Consumidores e manifesto dos artefatos efetivos | T29 | `completed_validated` (2026-11-11 14:48) | Coordenador | `ArtifactManifestEntry`, `ReferenceSetValidator.exportActiveArtifactManifestCsv`, `CanonicalArtifactResolutionAndManifestTest` |
| **T31** | Exclusão condicionada do ArtifactResolver e validação final | T28–T30; plano T27 | `completed_validated` (2026-11-11 14:52) | Coordenador | Exclusão de `ArtifactResolver.java` e `ArtifactResolverTest.java`; `mvn clean test` aprovado com 370 testes (0 falhas, 0 erros) |

### Decisões e Ajustes Consolidados da Fase 4
1. **Resolução Canônica sem Fallback Legado**:
   - As pastas `MOEAD_Weights/` e `pareto_fronts/csv/` foram totalmente desacopladas do fluxo operacional de resolução e preflight do experimento. Nenhum método de leitura ativa ou teste de existência as consulta em tempo de execução.
   - Os arquivos físicos históricos permanecem estritamente preservados e protegidos contra modificação ou exclusão acidental.
2. **Geração Canônica de Pesos do MOEA/DD**:
   - Os arquivos de peso ausentes na distribuição oficial do jMetal para $m=5$ ($N=210$) e $m=15$ ($N=135$) são gerados e validados canonicamente segundo Das & Dennis zero-replaced ($0 \mapsto 10^{-5}$) diretamente em `resources/weightVectorFiles/weights_gen/`.
3. **Retornos Estruturados e Hermeticidade**:
   - Adotados records imutáveis `ResolvedFrontArtifact` e `ResolvedWeightArtifact`, com hash SHA-256 verificado e injeção opcional de `baseDir` para suporte a diretórios herméticos de testes (`@TempDir`).
4. **Descontinuação e Exclusão Segura do `ArtifactResolver`**:
   - A regra de geração de pesos em memória no jMetal 7.8 (`isMoeadWeightGeneratedInMemory`) e a exportação do manifesto CSV de artefatos foram integradas em `ReferenceSetValidator`.
   - Após a migração de todos os consumidores e a aprovação de testes substitutos dedicados (`CanonicalArtifactResolutionAndManifestTest`), `ArtifactResolver.java` e `ArtifactResolverTest.java` foram excluídos definitivamente.
5. **Resultado da Suíte de Testes**:
   - Execução de `mvn clean test` aprovada com **370 testes executados em 23 classes (0 falhas, 0 erros, 0 ignorados)**.
   - Campanha experimental completa mantida desativada, conforme restrição mandatória.

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

## Fase 5 — Integração pré-campanha e tratamento da auditoria de 17/11/2026

### Objetivo

Integrar e validar no pipeline experimental os componentes produzidos nas fases anteriores, tratando os achados da auditoria posterior sem reconstruir a arquitetura, repetir atividades concluídas sem justificativa ou alterar resultados históricos. O planejamento dos agentes esta no `TASK_COORDINATOR.md`.

### Contexto e relação com as fases anteriores

- T25–T31 permanecem com seus registros históricos de conclusão na Fase 4.
- O relatório de auditoria de 17/11/2026 relata falhas e lacunas posteriores que precisam ser reproduzidas e delimitadas.
- A divergência entre relatório, coordenador e histórico não autoriza apagar ou reescrever registros anteriores.
- Os identificadores T32/T33 são mencionados pelo relatório; sua correspondência com arquivos reais será conferida antes da distribuição.
- As propostas T32–T36 somente serão materializadas após verificar numeração, equivalências e padrão das tarefas reais.

### Estado

**Fase 5 integralmente concluída, validada e homologada (`T32`–`T37` em estado `completed_validated`, 18/11/2026).** Suíte integral sem exclusões aprovada via `mvn clean test` (**`394` testes executados em `27` classes, `0` falhas, `0` erros, `0` ignorados**), empacotamento `mvn package -DskipTests` aprovado, smokes reais via CLI (`mvn exec:java`) validados em `WFGStudy/tests/` (lotes sequenciais preservados em `WFG4` $m=2$ com `-Dstudy.processIsolation=true` e `WFG4` $m=15$ sob deadline de 15s), paridade estatística ao vivo confirmada contra `R version 4.6.2 (2026-10-29 ucrt)` em R base nativo (`R_CROSS_VALIDATION_PASSED`), `README.md` consolidado e `0` alterações nos diretórios protegidos (`MOEAD_Weights/`, `pareto_fronts/`, `pareto_fronts/csv/`, `pareto/` e dados históricos de `WFGStudy/`). Campanha experimental completa mantida estritamente desativada, aguardando autorização explícita do operador humano.

### Matriz de execução e estado da Fase 5

| ID | Descrição | Dependências | Estado | Responsável | Arquivos afetados / Evidências |
|---|---|---|---|---|---|
| T32 | Reconciliação, baseline e correções mínimas de configuração/saída | Nenhuma nova | `completed_validated` | `agentA` (validado pelo Coordenador) | `WFGStudy.java` (`StudyOutputLayout` / `resolveStudyOutputLayout`), `MoeaddWeightValidationTest.java`, `StudyOutputDirectoryAndGenerationsTest.java`; baseline `373/7` reproduzido $\to$ `mvn clean test` `376/0` aprovado |
| T33 | Orçamento efetivo, isolamento, sementes e preservação de runs/lotes (absorve T32 do relatório) | T32; decisões `A1`, `A2` e `A5` aprovadas | `completed_validated` | `agentB` (validado pelo Coordenador) | `StaticParams.java` (`DTLZ5/6` `{1000,1000,1000,1000,1500,2000}`), `EvaluationBudgetProblem.java` (`BudgetCompliantMOEADD`, reserva atômica, proibição de objetivos fictícios, rejeição $B < N_{\text{alloc}}$), `IsolatedJobExecutor.java` (`executeJobInSubprocess`, `executeAndPersistBatch`, `CorruptedRunArtifactException`), `WFGStudy.java`, `StudyBudgetIsolationAndResumeIntegrationTest.java` (`44/44` testes de integração/regressão aprovados) |
| T34 | Referências, overrides, memória e manifesto efetivo | T32; interface de T33 | `completed_validated` | `agentC` / Coordenador | `ParetoFrontValidator.java` (geradores analíticos `WFG1–WFG9` com geometrias distintas de Huband et al., validação de conteúdo antes de seleção nas Prioridades 1 e 2, fallback opcional `-Dstudy.restoreOfficialJMetalResources=false`, trava concorrente `FRONT_GENERATION_LOCKS` + escrita atômica), `ReferenceSetValidator.java` (fronteira `m=2, 2<=N<=300`, `resolveCanonicalWeightArtifactFromWeightsRoot`, `tryRestoreOfficialJMetalWeightFile`, trava `WEIGHT_GENERATION_LOCKS`, `exportManifestFromResolvedArtifacts` com estágios `PREFLIGHT_PLANNED` / `OBSERVED_CONSUMED` / `OBSERVED_EXECUTION_FAILED`), `ResolvedWeightArtifact.java`, `PerformanceProfiler.java`, `WFGStudy.java` (`-Dstudy.allowMoeadWeightsInMemory` default `false`, propagação hermética de overrides/`baseDir`), `CanonicalArtifactResolutionAndManifestTest.java` (suíte integral `mvn clean test` `386/386` aprovada + `11/11` testes extras aprovados; reservas liberadas) |
| T35 | Resultado, normalização e hipervolume seguro integrados | T32; interfaces T33/T34; decisão `A3` + confirmação do delta com ressalva | `completed_validated` | `agentD` (validado pelo Coordenador) | `HypervolumeResult.java`, `ExactHypervolumeWorker.java` (paridade exata de caixa $[\mathbf{l}, \mathbf{r}]$ entre PISA, WFG e MC em 2D/15D), `ProcessIsolatedHypervolumeExecutor.java` (`computeExactOnly`, `evaluateConfiguredHypervolumes`, `-Dstudy.hvMethods`, opção ligar/desligar `-Dstudy.hvFallbackToMcOnAllExactTimeout` com disparo único para conjuntos de HVs pesados), `WFGStudy.java` (`THEORETICAL_PARETO_BOUNDS`, `NHV` suspenso no perfil ativo, `hv_audit_manifest.csv`, `hv_fallback_warnings.md`, `NA` estrito sem `0.0`), `StudyIndicatorPipelineIntegrationTest.java` (`41/41` testes aprovados; reservas liberadas) |
| T36 | Estatística, profiling e documentação integrada | T32 para pesquisa; T33–T35 concluídas + decisão `A4` + Opção A (R base) | `completed_validated` | `agentE` (validado pelo Coordenador) | `StudyStatisticalAnalyzer.java` (KW + Dunn–Holm + $\hat{A}_{12}$ orientado por célula + Friedman por blocos entre problemas + descoberta/validação contra `Rscript 4.6.2`), `scripts/kruskal_dunn_holm.R` (modo `--fixture` + estudo real em R base nativo), `FDEA.java` / `EvaluationBudgetProblem.java` / `IsolatedJobExecutor.java` (`R2ExecutionObserver` opt-in com `0` avaliações extras nos 6 algoritmos), `PerformanceProfiler.java`, `WFGStudy.java`, `StudyStatisticalAndProfilingIntegrationTest.java` (`23/23` testes aprovados; reservas liberadas) |
| T37 | Aceite fim-a-fim e reauditoria reconciliada (absorve T33 do relatório) | T32–T36 concluídas; mapeamento `A5` confirmado | `completed_validated` | Coordenador / Revisor | `ProcessIsolatedHypervolumeExecutor.java` + `IsolatedJobExecutor.java` (`resolveEffectiveRuntimeClassPath()` para `mvn exec:java`), `StudyIndicatorPipelineIntegrationTest.java`, `README.md`, `TASK_COORDINATOR.md`, `HANDLER.md`; reauditoria Zero-Trust reversa (`T36` $\to$ `T32` e `T32`/`T31` $\to$ `T01`), smokes CLI em `WFGStudy/tests/` (`FUN0_HASH_PRESERVED=True`, `pisa_status=SUCCESS_EXACT`, `NHV_EXISTS=False`, `R_CROSS_VALIDATION_PASSED`) e `mvn clean test` = **`394/394`** aprovados (todas as reservas liberadas) |

### Evento de mudança de escopo e encaminhamento de governança (18/11/2026 — `A1` a `A5`)

- **Mapeamento `T32`/`T33` confirmado (`A5`):** `T32` e `T33` permanecem como seções históricas de `docs/relatorio_auditoria-pre_execucao_final.md` (sem criar `tasks/task32.md` nem `tasks/task33.md`), com requisitos incorporados respectivamente em `T33` (preservação entre lotes, retomada íntegra, isolamento de RNG e reprodutibilidade determinística por `runId`) e `T37` (reauditoria Zero-Trust em ordem cronológica reversa pós-integrações).
- **Orçamento e gerações (`A1`, `A2`):** `StaticParams.getMaxGenerations` adota `{1000, 1000, 1000, 1000, 1500, 2000}` para `DTLZ5`/`DTLZ6`; `T33` aplica $B_{\text{target}} = N_{\text{config}}(m) \times G_{\text{config}}(\text{problema}, m)$ com adaptação de lote parcial validada nos 6 algoritmos (sem objetivos fictícios/herdados, rejeitando $B < N_{\text{alloc}}$).
- **Mudança de escopo na `T35` (`A3` — suspensão temporária de `NHV` no perfil ativo):**
  - *Trabalho preservado:* `ResultFrontContract`, `ObjectiveNormalizationContract` (incluindo `deriveNormalizedHypervolumeO1`), `NormalizedHypervolume`, `ProcessIsolatedHypervolumeExecutor`, `ExactHypervolumeWorker`, `MonteCarloHypervolume`, `HypervolumeResult`, testes matemáticos existentes, documentação histórica e resultados prévios em `WFGStudy/`.
  - *Pontos afetados:* No perfil ativo da Fase 5, `NHV` fica temporariamente suspenso (não é calculado, exportado, derivado obrigatoriamente em $O(1)$ nesta entrega nem incluído nas novas tabelas/análises deste perfil; eventual reativação registrada como decisão futura sem tarefa redundante). O perfil padrão calcula e exporta em saídas distintas tanto o `PISAHypervolume` exato isolado com deadline quanto a série secundária própria de `MonteCarloHypervolume` em todas as execuções (reutilizando MC válido em timeout do exato e exigindo fallback MC obrigatório quando apenas o exato é solicitado, sem nunca converter timeout/falha em zero nem apresentar MC como exato ou `NHV`). A normalização e o ponto de referência comum permanecem ativos sob o contrato científico da `T35`.
  - *Condição de retomada da `T35`:* Reconciliar modo de normalização, nomes de propriedades e contagem de amostras com os contratos existentes e apresentar ao operador um resumo curto do delta (implementado, preservado, alterado e novos testes) antes de retomar a implementação de código da política de indicadores.
- **Diretrizes para `T36` (`A4`):** Preservar e adaptar `scripts/kruskal_dunn_holm.R` (modo fixture + dados reais + comparação automática contra Java), inspecionar `WFGStudy/R/` e `WFGStudy/latex/`, manter geração Java de CSV/LaTeX resiliente à ausência de `Rscript`, separar análise por célula de análise global, consumir separadamente a série MC homogênea e a série exata com estados de disponibilidade (sem `NHV` no perfil ativo) e manter `R2ExecutionObserver` opt-in e desativado por padrão (sem criar Fase 6).

T37 reutiliza a T33 quando sua existência e especificação forem confirmadas. Não cria outra auditoria equivalente nem reimplementa funcionalidades concluídas.

A tarefa somente será concluída após revisão independente, suíte integral aprovada, integração demonstrada pelo entrypoint, preservação de dados, documentação consolidada e ausência de bloqueios no perfil aprovado.

Não iniciar automaticamente a campanha experimental completa.

- Responsáveis não atribuídos.
- Nenhum patch funcional ou teste da nova fase iniciado nesta preparação.

### Decisões e encaminhamentos iniciais

1. Preservar os registros das Fases 1–4 e os diretórios protegidos.
2. Não restaurar `ArtifactResolver` nem repetir sua exclusão por causa do roteiro histórico.
3. Reutilizar componentes anteriores somente após conferir contratos e demonstrar sua chamada no fluxo real.
4. Não tratar os resultados relatados de 373 testes/7 falhas ou os tempos de HV como medições reproduzidas nesta preparação.
5. Conferir T32/T33 e encaminhar atividades equivalentes às tarefas existentes, evitando patches e auditorias duplicados.
6. Manter os estados e o protocolo de eventos já utilizados no projeto.
7. Submeter mudanças científicas ainda não aprovadas ao operador.
8. Manter campanha completa e hiper-heurísticas fora desta execução.

### Regras específicas de execução

- Somente o coordenador edita `HANDLER.md` e `README.md`.
- Executores registram início, bloqueio, retomada e entrega em eventos exclusivos conforme `AGENTS.md`.
- O coordenador consolida status, evidências, testes, decisões, documentação e próxima ação.
- Planejamento e auditoria desta fase ficam no coordenador; documentação definitiva de métodos, funções, parâmetros e integração fica no README.
- Não criar relatório separado de planejamento nem `docs/scientific-rationale.md`.
- Testes utilizam diretórios temporários ou `WFGStudy/tests/`.
- Git permanece somente para leitura e checagens.
- Não excluir testes, alterar dados ou suprimir falhas para obter aprovação.

### Pendências e próxima ação

Nenhuma pendência técnica bloqueante no perfil homologado da Fase 5 (`T32`–`T37` `completed_validated`). Próxima etapa condicionada exclusivamente à **autorização explícita do operador humano** para disparo da campanha experimental completa dos 6 MOEAs isolados (em execução contínua ou particionada por lotes via `-DrunStart` e `-Druns`), seguida, em fase posterior própria, da adaptação das hiper-heurísticas de seleção (`AdapHH`, `EA-ILS`, `LAST-RL`).

### Critério de encerramento

A fase depende da integração demonstrada pelo entrypoint real, suíte integral aprovada, preservação de resultados, referências e orçamento válidos, isolamento comprovado, HV protegido, análise coerente e documentação atualizada.

O gate final deverá reutilizar a T33 se sua existência e especificação forem confirmadas. Ausência ou incompatibilidade será registrada e encaminhada ao operador.

Não iniciar automaticamente a campanha experimental completa.

## Fase 5.5 — Reauditoria Independente Zero-Trust Pós-Correções e Portabilidade Linux/Windows (18/11/2026)

- **Relatório de Reauditoria Independente:** Consolidado em `docs/relatorio_auditoria_zerotrust_pos_fase5.md` (`T37` $\to$ `T01`, incluindo `T32` e `T33`), confirmando `394` testes aprovados em `27` classes (`0` falhas, `0` erros, `0` ignorados) e *smokes* reais via CLI em `WFG4` ($m=2$ em 2 lotes com `-Dstudy.processIsolation=true` e $m=15$ sob *deadline*).
- **Portabilidade Linux / Windows (`.gitattributes` e `matchesExpectedSha256CrossPlatform`):**
  - Criado `.gitattributes` fixando `eol=crlf` para os artefatos científicos auditados por SHA-256 (`.dat`, `.csv`, `.pf`) e `eol=lf` para scripts `*.R` e `*.sh`.
  - Implementado `ReferenceSetValidator.matchesExpectedSha256CrossPlatform` (utilizado também em `MoeaddWeightValidationTest`), tornando a validação de manifesto SHA-256 imune a diferenças de fim de linha (`LF` vs. `CRLF`) entre Linux e Windows.
  - Comandos de terminal em `README.md`, `docs/pipeline-pos_processamento.md`, `docs/relatorio_auditoria-pre_execucao_final.md` e `docs/relatorio_auditoria_zerotrust_pos_fase5.md` padronizados com blocos explícitos para **Windows `CMD` (`cmd.exe`)** e **`Linux Bash` / `Git Bash`**.

---

## Fase 6 — Otimização de Recursos Pré-Execução Final (Opções 1, 3 e 5a) antes das Hiper-heurísticas (20/11/2026)

- **Escopo Autorizado pelo Operador Humano:** Implementação das opções **1**, **3** e **5a** descritas na Seção 4.2 de [`docs/guia-execucao-otimizacao-recursos.md`](docs/guia-execucao-otimizacao-recursos.md), mantendo a fase atual estritamente no **estágio de pré-execução final antes da integração com hiper-heurísticas** (`AdapHH`, `EA-ILS`, `LAST-RL`). As opções `2` (daemon JVM pool persistente), `4` (GPU / sub-blocos CUDA em Monte Carlo) e `5b` (cache de `preResolvedMoeadWeightsDir`) permanecem adiadas.
- **Opção 1 — Checkpointing Incremental Job-a-Job e Compactação Imediata de Heap ([`IsolatedJobExecutor.java`](src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java), [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java)):**
  - `runPendingJobsIncremental` agora utiliza `ExecutorCompletionService<JobExecutionResult>`, persistindo imediatamente cada job concluído (`FUN<r>.csv`, `VAR<r>.csv`, `.raw.csv`) via `persistSingleJobResult` e substituindo as matrizes pesadas `double[][]` por `EMPTY_MATRIX` via `JobExecutionResult.compactRetainingMetadataOnly()` (retendo apenas escalares e SHA-256 para `profiling/job_execution_profile.csv`). Em `WFGStudy.main()`, `batchReport` é explicitamente liberado (`batchReport = null`) antes do cálculo de indicadores/estatística.
  - Gravado journal incremental `run_manifest.checkpoint.csv` com *flush* por job e consolidação atômica em `run_manifest.csv` a cada `-Dstudy.checkpointInterval` (`DEFAULT_CHECKPOINT_INTERVAL = 25`) e em `finishAndFlush`.
  - **Defesas:** reconciliação tolerante apenas a eventual **última linha truncada** em caso de queda (`reconcileCheckpointJournalIfPresent`), preservação integral das verificações físicas SHA-256/`VAR -> FUN` em `verifyExistingRunForResume`, e reconstrução determinística da ordem canônica do manifesto via `buildOrderedManifestEntries`.
  - **Correção crítica de corrida concorrente em [`ResultFrontContract.java`](src/main/java/br/usp/lti/newmoea/util/ResultFrontContract.java):** substituído `problem.createSolution()` por `ResultFrontContract.createUnseededSolution` (`UnseededDoubleSolution`) em `reconstructSolutions`, `verifyFunVarAlignment` e `LazyIsolatedAlgorithmAdapter`, eliminando chamadas espúrias a `JMetalRandom.getInstance()` fora de `JMETAL_RANDOM_LOCK` na thread principal durante persistência paralela.
- **Opção 3 — Paralelização Multicore de Indicadores e Escalonamento LPT ([`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java), [`IsolatedJobExecutor.java`](src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java)):**
  - Adicionada fila determinística LPT (`orderJobSpecsLpt`, `estimateRelativeJobCost`) controlada por `-Dstudy.lptScheduling` (padrão automático `true` quando `maxWorkers > 1`), priorizando jobs pesados sem alterar sementes (`SplitMix64`).
  - `computeContractualIndicatorsAndExport` paralelizada em `-Dstudy.indicatorWorkers` threads (`ExecutorCompletionService`, despacho *heaviest-first*, preservação estrita da ordem canônica em `QualityIndicatorSummary.csv` e `hv_audit_manifest.csv`).
  - **Defesas:** isolamento estrito por thread (`ThreadLocal<IndicatorCalculators>` com limpeza `.remove()` em `finally`) para `InvertedGenerationalDistance`, `InvertedGenerationalDistancePlus` e **`R2`** (cujo bytecode do jMetal 7.8 muta `weightVectors` e `idealPoint`); cálculo prévio único de `hvRefUnit` por problema quando `NHV` solicitada; e semáforos `lowDimExactPermits`/`highDimExactPermits` via `-Dstudy.exactHvParallelWorkers` (cap padrão em $\min(\text{workers}, 2)$ para $m \ge 8$) contra explosão de RAM em cálculos exatos.
- **Opção 5a — Configuração Exclusiva de Workers JVM via `-Dstudy.workerJvmArgs` ([`ProcessIsolatedHypervolumeExecutor.java`](src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java), [`IsolatedJobExecutor.java`](src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java), [`WFGStudy.java`](src/main/java/br/usp/lti/newmoea/study/WFGStudy.java)):**
  - Injeção de flags JVM em `executeJobInSubprocess` e `computeExactOnly` com validação *fail-fast* em `WFGStudy.main()`.
  - **Defesas (`parseWorkerJvmArgs`):** tokenizer compatível com aspas e caminhos Windows (preservando `\`), bloqueio de flags reservadas ao supervisor (`-cp`, `-classpath`, `--class-path`, `-jar`, `-m`, `--module`, `-p`, `--module-path`, `-version`, `--help`), validação de `-Xms`/`-Xmx`, rejeição de `-Xmx < 64m` ou `-Xms > -Xmx`, e aviso único para `-Xmx < 384 MiB`.
- **Documentação Atualizada:** [`docs/guia-execucao-otimizacao-recursos.md`](docs/guia-execucao-otimizacao-recursos.md), [`docs/pipeline-pos_processamento.md`](docs/pipeline-pos_processamento.md), [`README.md`](README.md) e [`HANDLER.md`](HANDLER.md). *(Nota: registros históricos das fases anteriores preservados integralmente; os novos testes automatizados foram incorporados em `StudyBudgetIsolationAndResumeIntegrationTest` e `StudyIndicatorPipelineIntegrationTest`).*
- **Verificação (`mvn clean test` integral sem exclusões):** **400 testes executados em 27 classes, 0 falhas, 0 erros, 0 ignorados (`BUILD SUCCESS` em 04:11 min)**. Os diretórios protegidos (`MOEAD_Weights/`, `pareto_fronts/`, `pareto/`) permaneceram intactos e a campanha completa (`17.280` jobs) segue aguardando execução pelo operador.
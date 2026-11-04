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
- T06: pending
- T07: pending
- T08: pending
- T09: pending
- T10: pending
- T11: pending

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
| 2026-11-03 | Fase 2 criada | Coordenador | pending | `TASK_COORDINATOR.md` e `tasks/task6..11.md` | Executar T06 |

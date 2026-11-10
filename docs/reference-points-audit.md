# Auditoria Matemática de Pontos de Referência (NSGA-III) e Direções de Referência (RVEA) — T07

## 1. Escopo e Objetivo

Este documento consolida a revalidação de segunda opinião (Tarefa **T07** — Partes 1, 2 e 3) sobre a geração, estrutura algébrica, uso interno e persistência opcional de conjuntos de referência no projeto `hypeMOEA` (Java 21, Maven, jMetal 7.8):

1. **Parte 1 — NSGA-III**: Reinspeção independente de bytecode de `org.uma.jmetal.util.referencepoint.ReferencePointGenerator` (`jmetal-core-7.8.jar`), `org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIIIBuilder` e `NSGAIII` (`jmetal-algorithm-7.8.jar`), comparação dos três conjuntos para $m \in \{2, 3, 5, 8, 10, 15\}$ e validação **separada** das camadas externa ($H_1$) e interna ($H_2$) para $m \in \{8, 10, 15\}$.
2. **Parte 2 — RVEA**: Reinspeção independente de `WFGStudy.buildRVEA` e do bytecode de `org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder`, `RVEAReplacement`, `RVEAEnvironmentalSelection` e `RVEAGeometry` (`jmetal-component-7.8.jar`), com validação de cardinalidade, dimensionalidade, soma unitária no simplex de entrada, normalização L2 unitária interna, não-negatividade, ausência de duplicatas e finitude.
3. **Distinção Semântica Obrigatória**: Diferenciação formal entre pontos de referência do NSGA-III, direções de referência do RVEA, vetores de peso do MOEA/DD e frentes de Pareto (`PF*`).
4. **Parte 3 — Persistência Opcional**: Documentação das propriedades JVM `-DpersistReferencePoints=false` e `-DpersistReferenceDirections=false` (desativadas por padrão), formato CSV de 15 casas decimais, proteção contra sobrescrita e garantia de imutabilidade dos vetores consumidos pelos algoritmos.

---

## 2. Parte 1 — Revalidação do NSGA-III (Segunda Opinião)

### 2.1. Inspeção de Bytecode no jMetal 7.8

A desmontagem via `javap -c -p` das classes dos artefatos Maven do jMetal 7.8 revelou:

1. **`ReferencePointGenerator` (`jmetal-core-7.8.jar`)**:
   - `generateSingleLayer(int numberOfObjectives, int numberOfDivisions)`: valida `numberOfObjectives >= 2` e `numberOfDivisions >= 1`, e gera recursivamente (`generateRecursive`) todas as composições inteiras $(k_1, \dots, k_m)$ com $\sum_{i=1}^m k_i = H_1$, dividindo cada coordenada por $H_1$:
     $$x_i = \frac{k_i}{H_1}, \quad k_i \in \{0, 1, \dots, H_1\}, \quad \sum_{i=1}^m x_i = 1$$
   - `generateTwoLayers(int numberOfObjectives, int outerLayerDivisions, int innerLayerDivisions)`: gera a camada externa via `generateSingleLayer(m, H_1)` e a camada interna a partir de `generateSingleLayer(m, H_2)` aplicando exatamente a contração centrada de Deb & Jain (2014):
     $$x_i^{\text{inner, DebJain}} = \frac{1}{2} x_i^{(H_2)} + \frac{1}{2m}$$
   - `ReferencePointGenerator` produz $100\%$ de equivalência numérica (erro máximo por coordenada $< 10^{-15}$) com o gerador canônico de Das & Dennis (1998) e Deb & Jain (2014) para todas as dimensões $m \in \{2, 3, 5, 8, 10, 15\}$.

2. **`NSGAIIIBuilder` e `NSGAIII` (`jmetal-algorithm-7.8.jar`)**:
   - `NSGAIIIBuilder` **não invoca** `ReferencePointGenerator`. Em vez disso, armazena `numberOfDivisions` ($H_1$) e `secondLayerDivisions` ($H_2$, padrão `0`) e instancia `new NSGAIII(this)`.
   - No construtor `NSGAIII(NSGAIIIBuilder<S> builder)` (instruções de bytecode `72` a `380`):
     - Primeiro gera a camada externa chamando `ReferencePoint.generateReferencePoints(outerReferencePoints, m, H_1)` (`offsets 75–102`), que executa a recorrência exata de Das & Dennis ($k_i / H_1$).
     - Quando `secondLayerDivisions > 0` (`offsets 109–371`), gera uma camada base `innerReferencePoints` com $H_2$ divisões e aplica a transformação (`offsets 231–328`):
       ```text
       val_i = 0.5 + 0.5 * pos.get(i)     // = 0.5 * (x_i + 1)
       sum   = sum + val_i                // = 0.5 * m + 0.5 * 1 = 0.5 * (m + 1)
       pos_i = val_i / sum                // = (x_i + 1) / (m + 1)
       ```
     - Portanto, a camada interna efetivamente gerada dentro de `NSGAIII` obedece à fórmula:
       $$x_i^{\text{inner, NSGAIII}} = \frac{x_i^{(H_2)} + 1}{m + 1}$$
       em vez da fórmula canônica de Deb & Jain (2014):
       $$x_i^{\text{inner, DebJain}} = \frac{1}{2} x_i^{(H_2)} + \frac{1}{2m} = \frac{m \cdot x_i^{(H_2)} + 1}{2m}$$

### 2.2. Comparação Global dos Três Conjuntos ($m \in \{2, 3, 5, 8, 10, 15\}$)

| $m$ | $H_1$ | $H_2$ | $N_{\text{ext}} = \binom{H_1+m-1}{m-1}$ | $N_{\text{int}} = \binom{H_2+m-1}{m-1}$ | $N_{\text{total}}$ | `ReferencePointGenerator` vs Canônico ($\tau=10^{-6}$) | `NSGAIIIBuilder` vs Canônico ($\tau=10^{-6}$) | `NSGAIIIBuilder` vs Fórmula $\frac{x_i+1}{m+1}$ |
|---:|---:|---:|---:|---:|---:|:---:|:---:|:---:|
| **2** | 99 | 0 | 100 | 0 | **100** | `EQUIVALENT` ($0.0$) | `EQUIVALENT` ($0.0$) | `EQUIVALENT` ($0.0$) |
| **3** | 12 | 0 | 91 | 0 | **91** | `EQUIVALENT` ($0.0$) | `EQUIVALENT` ($0.0$) | `EQUIVALENT` ($0.0$) |
| **5** | 6 | 0 | 210 | 0 | **210** | `EQUIVALENT` ($0.0$) | `EQUIVALENT` ($0.0$) | `EQUIVALENT` ($0.0$) |
| **8** | 3 | 2 | 120 | 36 | **156** | `EQUIVALENT` ($1.11 \times 10^{-16}$) | `NOT_EQUIVALENT` (36 int. divergem) | `EQUIVALENT` ($0.0$) |
| **10** | 3 | 2 | 220 | 55 | **275** | `EQUIVALENT` ($0.0$) | `NOT_EQUIVALENT` (55 int. divergem) | `EQUIVALENT` ($1.11 \times 10^{-16}$) |
| **15** | 2 | 1 | 120 | 15 | **135** | `EQUIVALENT` ($5.55 \times 10^{-17}$) | `NOT_EQUIVALENT` (15 int. divergem) | `EQUIVALENT` ($1.11 \times 10^{-16}$) |

### 2.3. Validação Separada das Camadas Externa e Interna para $m \in \{8, 10, 15\}$

Em cumprimento estrito à Parte 1 de T07, os $N_{\text{total}}$ pontos extraídos de `NSGAIII` foram particionados na **camada externa** (índices $0 \dots N_{\text{ext}}-1$) e na **camada interna** (índices $N_{\text{ext}} \dots N_{\text{total}}-1$) e comparados isoladamente contra o gerador canônico de Das & Dennis / Deb & Jain:

#### A. Camada Externa Isolada ($H_1$)

| $m$ | $H_1$ | Pontos esperados ($N_{\text{ext}}$) | Pontos obtidos | Sem correspondência ($\tau=10^{-6}$) | Erro máx. por coordenada ($L_\infty$) | Erro euclidiano máx. ($L_2$) | Veredito |
|---:|---:|---:|---:|---:|---:|---:|:---:|
| **8** | 3 | 120 | 120 | **0** | $0.000 \times 10^0$ | $0.000 \times 10^0$ | `EQUIVALENT` |
| **10** | 3 | 220 | 220 | **0** | $0.000 \times 10^0$ | $0.000 \times 10^0$ | `EQUIVALENT` |
| **15** | 2 | 120 | 120 | **0** | $0.000 \times 10^0$ | $0.000 \times 10^0$ | `EQUIVALENT` |

#### B. Camada Interna Isolada ($H_2$)

Nos vértices da camada interna ($x_k^{(H_2)} = 1$ e $x_j^{(H_2)} = 0$ para $j \ne k$), a coordenada principal segundo Deb & Jain vale $\frac{1}{2} + \frac{1}{2m} = \frac{m+1}{2m}$, enquanto a fórmula de `NSGAIII.java` produz $\frac{1+1}{m+1} = \frac{2}{m+1}$. Assim, a diferença analítica máxima por coordenada nos vértices é:
$$\Delta_\infty(m) = \frac{m+1}{2m} - \frac{2}{m+1} = \frac{m^2 - 2m + 1}{2m(m+1)} = \frac{(m-1)^2}{2m(m+1)}$$
e a distância euclidiana correspondente entre o ponto de `NSGAIII` e o ponto de Deb & Jain de mesma combinação inteira é:
$$\Delta_2(m) = \sqrt{\Delta_\infty(m)^2 + (m-1)\left(\frac{1}{m+1} - \frac{1}{2m}\right)^2} = \frac{(m-1)\sqrt{m(m-1)}}{2m(m+1)}$$
*(Nota: no emparelhamento guloso de `compareSetsOrderIndependent`, quando todos os $N_{\text{int}}$ pontos internos excedem $\tau = 10^{-6}$, `maxEuclideanError` reporta a distância euclidiana ao vizinho mais próximo na camada interna de Deb & Jain, que ocorre entre um vértice de Deb & Jain e o ponto mais próximo do feixe contraído de `NSGAIII`).*

| $m$ | $H_2$ | Pontos esperados ($N_{\text{int}}$) | Pontos obtidos | Sem correspondência vs Deb & Jain ($\tau=10^{-6}$) | Erro máx. por coordenada vs Deb & Jain ($L_\infty$) | Erro euclidiano máx. vs Deb & Jain ($L_2$) | Veredito vs Deb & Jain | Erro máx. vs $\frac{x_i+1}{m+1}$ | Veredito vs $\frac{x_i+1}{m+1}$ |
|---:|---:|---:|---:|---:|---:|---:|:---:|---:|:---:|
| **8** | 2 | 36 | 36 | **36 / 36** | $0.340278$ ($\frac{49}{144}$) | $0.363772$ | `NOT_EQUIVALENT` | $0.000 \times 10^0$ | `EQUIVALENT` |
| **10** | 2 | 55 | 55 | **55 / 55** | $0.368182$ ($\frac{81}{220}$) | $0.388098$ | `NOT_EQUIVALENT` | $1.110 \times 10^{-16}$ | `EQUIVALENT` |
| **15** | 1 | 15 | 15 | **15 / 15** | $0.408333$ ($\frac{196}{480}$) | $0.422953$ | `NOT_EQUIVALENT` | $1.110 \times 10^{-16}$ | `EQUIVALENT` |

> **Registro de Limitação Técnica (conforme regra da T07)**: A fórmula interna $x_i^{\text{inner}} = \frac{x_i + 1}{m + 1}$ pertence ao construtor de `org.uma.jmetal.algorithm.multiobjective.nsgaiii.NSGAIII` no artefato oficial `jmetal-algorithm-7.8.jar` (que ainda produz pontos válidos no interior do simplex unitário, com $\sum_{i=1}^m x_i = 1$, $x_i > 0$ e $0$ duplicatas). Conforme determinado nas instruções da T07, **o gerador interno do `NSGAIII` não é substituído**, mantendo-se a fidelidade comportamental ao jMetal 7.8 e documentando-se esta diferença como limitação intrínseca da biblioteca.

---

## 3. Parte 2 — Revalidação do RVEA

### 3.1. Inspeção de `WFGStudy.buildRVEA` e Bytecode de `RVEABuilder` (`jmetal-component-7.8.jar`)

1. **Em `WFGStudy.java` (`buildRVEA`)**:
   - Para $m \in \{2, 3, 5\}$, chama `ReferencePointGenerator.generateSingleLayer(m, H_1)` com $H_1 \in \{99, 12, 6\}$.
   - Para $m \in \{8, 10, 15\}$, chama `ReferencePointGenerator.generateTwoLayers(m, H_1, H_2)` com $(H_1, H_2) \in \{(3,2), (3,2), (2,1)\}$.
   - Valida explicitamente `populationSize == referenceVectors.size()`.
   - Configura `alpha = 2.0`, `fr = 0.1`, `maxEvaluations = StaticParams.getMaxGenerations(m, problem.name()) * populationSize` e instancia:
     ```java
     new RVEABuilder<>(problem, referenceVectors.size(), maxEvaluations, crossover, mutation, alpha, fr, referenceVectors)
     ```
2. **No bytecode de `RVEABuilder`, `RVEAReplacement`, `RVEAEnvironmentalSelection` e `RVEAGeometry`**:
   - O construtor de `RVEABuilder` valida:
     - `problem.numberOfConstraints() == 0` (`"RVEA only supports unconstrained problems"`);
     - `populationSize > 0` e `maxEvaluations >= populationSize`;
     - `alpha >= 0.0` e `fr > 0.0 && fr <= 1.0`;
     - `referenceVectors.size() == populationSize` (`"Population size must match the number of reference vectors"`).
   - `RVEABuilder.maxGenerations(populationSize, maxEvaluations)` calcula o número de gerações evolutivas após a avaliação da população inicial:
     $$G_{\max}^{\text{RVEA}} = \max\left(1, \left\lceil \frac{\text{maxEvaluations} - \text{populationSize}}{\text{populationSize}} \right\rceil\right) = G_{\text{total}} - 1$$
   - `RVEAEnvironmentalSelection` calcula a frequência de adaptação das direções como:
     $$f_{\text{adapt}} = \max\left(1, \text{round}(G_{\max}^{\text{RVEA}} \cdot f_r)\right)$$
   - `RVEAEnvironmentalSelection` converte os vetores do simplex $\mathbf{w}_i \in \Delta^{m-1}$ em **direções unitárias na esfera L2** por meio de `RVEAGeometry.directions(vectors, m)` -> `RVEAGeometry.unit(vector)`:
     $$\mathbf{v}_i = \frac{\mathbf{w}_i}{\|\mathbf{w}_i\|_2}, \quad \|\mathbf{v}_i\|_2 = 1$$
     armazenando cópias imutáveis em `initialReferenceVectors` (`double[N][m]`) e cópias adaptáveis em `referenceVectors` (`double[N][m]`).

### 3.2. Tabela de Validação Matemática das Direções do RVEA ($m \in \{2, 3, 5, 8, 10, 15\}$)

| $m$ | $H_1$ | $H_2$ | Cardinalidade ($N$) | Dimensão | Finitude | Não-negatividade ($w_{ij}, v_{ij} \ge 0$) | Duplicatas | Desvio máx. $\sum_j w_{ij} = 1$ (Simplex de entrada) | Erro máx. $\|\mathbf{v}_i\|_2 = 1$ (Direções internas) | `alpha` / `fr` ($f_{\text{adapt}} / G_{\max}^{\text{RVEA}}$) |
|---:|---:|---:|---:|---:|:---:|:---:|---:|---:|---:|:---:|
| **2** | 99 | 0 | **100** | 2 | 100% | 100% | **0** | $1.110 \times 10^{-16}$ | $2.220 \times 10^{-16}$ | $2.0$ / $0.1$ ($25 / 249$) |
| **3** | 12 | 0 | **91** | 3 | 100% | 100% | **0** | $1.110 \times 10^{-16}$ | $2.220 \times 10^{-16}$ | $2.0$ / $0.1$ ($40 / 399$) |
| **5** | 6 | 0 | **210** | 5 | 100% | 100% | **0** | $1.110 \times 10^{-16}$ | $3.331 \times 10^{-16}$ | $2.0$ / $0.1$ ($75 / 749$) |
| **8** | 3 | 2 | **156** | 8 | 100% | 100% | **0** | $0.000 \times 10^{0}$ | $2.220 \times 10^{-16}$ | $2.0$ / $0.1$ ($150 / 1499$) |
| **10** | 3 | 2 | **275** | 10 | 100% | 100% | **0** | $4.441 \times 10^{-16}$ | $3.331 \times 10^{-16}$ | $2.0$ / $0.1$ ($200 / 1999$) |
| **15** | 2 | 1 | **135** | 15 | 100% | 100% | **0** | $1.110 \times 10^{-16}$ | $6.661 \times 10^{-16}$ | $2.0$ / $0.1$ ($300 / 2999$) |

---

## 4. Distinção Semântica Obrigatória entre Artefatos de Referência

Para evitar confusão conceitual ou experimental no projeto `hypeMOEA`, a tabela abaixo estabelece a distinção rigorosa entre as quatro entidades geométricas utilizadas no estudo:

| Entidade | Algoritmo / Componente Consumidor | Espaço Geométrico e Invariante Algébrico | Papel Algorítmico | Origem / Localização no Projeto |
|---|---|---|---|---|
| **1. Pontos de Referência (NSGA-III)** | `NSGAIII` (`org.uma.jmetal.algorithm.multiobjective.nsgaiii`) | Simplex unitário padrão $\Delta^{m-1}$: $z_j \ge 0$, $\sum_{j=1}^m z_j = 1$ (camada interna no jMetal 7.8 usa $\frac{x_j+1}{m+1}$) | Definem linhas de referência da origem até o hiperplano interceptado; guiam o *niching* por distância perpendicular após não-dominância e normalização adaptativa | Gerados em memória dentro do construtor de `NSGAIII`; persistência opcional em `resources/referencePoints/nsgaiii/` |
| **2. Direções de Referência (RVEA)** | `RVEA` (`RVEABuilder` / `RVEAEnvironmentalSelection`) | Entrada: simplex $\Delta^{m-1}$ ($\sum_{j=1}^m w_j = 1$, camada interna de Deb & Jain $\frac{1}{2}x_j + \frac{1}{2m}$). Uso interno: esfera unitária positiva $S_+^{m-1}$ ($\mathbf{v} = \mathbf{w}/\|\mathbf{w}\|_2$, $\|\mathbf{v}\|_2 = 1$) | Particionam o espaço de objetivos em subproblemas cônicos e calculam a distância penalizada por ângulo (*Angle-Penalized Distance* — APD), sofrendo adaptação periódica a cada $f_r \cdot G_{\max}$ gerações | Geradas via `ReferencePointGenerator` em `WFGStudy.buildRVEA`; persistência opcional em `resources/referencePoints/rvea/` |
| **3. Vetores de Peso (MOEA/DD, MOEA/D-DRA, GWASFGA)** | `MOEADD`, `MOEADDRA`, `GWASFGA` e indicador `R2` | Simplex aproximado com regularização de fronteira $0 \mapsto 10^{-5}$: $w_j \ge 10^{-5}$, $\left|\sum_{j=1}^m w_j - 1\right| \le (m-1)\times 10^{-5}$ | Parametrizam funções de escalarização/agregação (PBI com $\theta = 5.0$, Tchebycheff ou Achievement Scalarizing Function) e definem vizinhanças por distância euclidiana entre pesos | Arquivos estáticos `MOEAD_Weights/W{m}D_{N}.dat` (auditados em `docs/moead-weights-audit.md`) |
| **4. Frentes de Pareto de Referência (`PF*`)** | Indicadores de qualidade (`PISAHypervolume`, `NormalizedHypervolume`, `IGD`, `IGD+`, `R2`) | Imagem Pareto-ótima no espaço de objetivos $\mathbb{R}^m$ específica da geometria de cada problema (hiperplano $\sum f_j = 0.5$ em `DTLZ1`, esfera $\sum f_j^2 = 1$ em `DTLZ2–4`, curva 1D em `DTLZ5–6`, regiões desconexas em `DTLZ7`, elipsoide $\sum (f_j/2j)^2 = 1$ em `WFG4–9`) | Representam uma amostra discreta não-dominada de $PF^*$ usada exclusivamente **após** a otimização para normalizar e avaliar a qualidade das frentes de aproximação `FUN*.csv` | Arquivos estáticos `pareto_fronts/csv/{PROBLEM}.{m}D.csv` (e geração analítica/empírica opcional em `resources/referenceFronts/pf_gen/`) |

---

## 5. Parte 3 — Persistência Opcional Controlada (Desativada por Padrão)

### 5.1. Propriedades JVM e Caminhos de Saída

Foram implementadas em `ReferenceSetValidator` e integradas em `WFGStudy.buildNSGAIII` e `WFGStudy.buildRVEA` duas propriedades JVM independentes, **ambas desativadas (`false`) por padrão**:

1. **`-DpersistReferencePoints=false`** (padrão `false`):
   - Quando `true`, extrai uma cópia somente-leitura dos pontos de referência efetivamente construídos pelo `NSGAIII` e salva em:
     `resources/referencePoints/nsgaiii/NSGAIII_m{m}_H1{H1}_H2{H2}.csv`
     *(para problemas de 1 camada, $H_2 = 0$, ex.: `NSGAIII_m3_H112_H20.csv`; para 2 camadas, ex.: `NSGAIII_m8_H13_H22.csv`).*
2. **`-DpersistReferenceDirections=false`** (padrão `false`):
   - Quando `true`, copia as direções de referência geradas para o `RVEA` e salva em:
     `resources/referencePoints/rvea/RVEA_m{m}_H1{H1}_H2{H2}.csv`
     *(ex.: `RVEA_m3_H112_H20.csv`, `RVEA_m8_H13_H22.csv`).*

### 5.2. Formato, Proteção contra Sobrescrita e Imutabilidade

- **Formato CSV**:
  - Primeira linha com cabeçalho `index,f1,f2,...,fm`;
  - Linhas subsequentes com índice inteiro base-0 (`0` a `N-1`) e coordenadas formatadas com ponto decimal (`Locale.US`) e **15 casas decimais** (`%.15f`), separadas por vírgula.
- **Proteção contra sobrescrita**:
  - Se o arquivo alvo já existir com conteúdo **idêntico** (como ocorre nas 30 runs independentes de um mesmo problema dentro de `WFGStudy`), o arquivo existente é preservado sem regravação destrutiva (`reusedIdenticalExisting = true`).
  - Se o arquivo alvo já existir com conteúdo **distinto**:
    - No modo estrito (`failIfDifferentExists = true`), lança `IllegalStateException` sem tocar no arquivo existente;
    - No modo de sufixo (`failIfDifferentExists = false`), preserva o arquivo original intacto e grava em novo arquivo com sufixo incremental (`_run1.csv`, `_run2.csv`, etc.).
- **Garantia de Imutabilidade**:
  - `ReferenceSetValidator.deepCopyVectors` e `extractReferencePointsFromNsgaIII` operam exclusivamente sobre cópias profundas independentes; nenhum vetor ou `ReferencePoint` usado pelo `NSGAIII` ou pelo `RVEA` é modificado pela rotina de persistência.

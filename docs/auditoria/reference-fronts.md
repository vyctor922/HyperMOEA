# Auditoria Física e Matemática das Frentes de Pareto de Referência (T16)

- **Tarefa:** `T16` (`tasks/task16.md`)
- **Agente:** Agente C (*Reference Fronts Physical & Mathematical Auditor*)
- **Data:** 09/11/2026
- **Artefato Físico Consolidado (CSV):** [`docs/auditoria/reference-fronts-physical.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/reference-fronts-physical.csv)
- **Suíte de Testes de Auditoria:** [`ReferenceFrontPhysicalAndMathAuditTest.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/qualityindicator/ReferenceFrontPhysicalAndMathAuditTest.java)
- **Validador e Gerador Analítico:** [`ParetoFrontValidator.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java)

---

## 1. Resumo Executivo

1. **Auditoria Física Integral dos 160 Arquivos em `pareto_fronts/csv/`:**
   - Todos os **160 arquivos `.csv`** presentes em `pareto_fronts/csv/` foram inspecionados byte a byte, com registro de `size_bytes`, `sha256`, `physical_lines`, `non_empty_lines`, `valid_points`, `unique_points` (tolerância Chebyshev $10^{-9}$), `non_dominated_points`, `duplicated_points`, `dominated_points`, `min_by_obj`, `max_by_obj` e `has_negative_values` em [`docs/auditoria/reference-fronts-physical.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/reference-fronts-physical.csv).
   - Entre os **68 arquivos ativos** da configuração padrão de [`WFGStudy.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/study/WFGStudy.java) (`WFG1`–`WFG9` em $m \in \{2,3,5,8,10,15\}$ e `DTLZ1`–`DTLZ7` em $m \in \{2,3\}$), reconciliamos formalmente as **6 contagens de linhas e hashes SHA-256 divergentes** que haviam sido reportados incorretamente em [`docs/reference-fronts-dtlz.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/reference-fronts-dtlz.md) durante a T07 (`DTLZ3.3D.csv`, `DTLZ4.3D.csv`, `DTLZ5.2D.csv`, `DTLZ6.2D.csv`, `DTLZ7.2D.csv`, `DTLZ7.3D.csv`), além dos hashes SHA-256 distintos de `DTLZ3.2D.csv` e `DTLZ4.2D.csv`.
2. **Realizabilidade Matemática no Espaço de Decisão (`problem.evaluate` no jMetal 7.8):**
   - Por meio da inversão analítica implementada em [`ParetoFrontValidator.constructOptimalDtlzDecisionVector`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L1614-L1705), demonstramos construtivamente e verificamos via chamada direta a `problem.evaluate(solution)` no bytecode do jMetal 7.8 (`org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1` a `DTLZ7`) que **todo ponto $\mathbf{y}$** gerado por [`ParetoFrontValidator.generateAnalyticalDtlzFront`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L775-L817) em $m \in \{2, 3, 5, 8, 10, 15\}$ possui pré-imagem factível explícita $\mathbf{x}^* \in [0, 1]^n$ com erro $\|\mathbf{f}(\mathbf{x}^*) - \mathbf{y}\|_\infty \le 10^{-12}$.
3. **Análise Científica de `DTLZ5` e `DTLZ6` em $m \ge 4$ (Escopo de 84 vs. 96 Instâncias):**
   - No bytecode do jMetal 7.8 (`DTLZ5.evaluate` e `DTLZ6.evaluate`), quando $g(\mathbf{x}_M) = 0$, tem-se $\theta_0 = \frac{\pi}{2} x_0 \in [0, \frac{\pi}{2}]$ e $\theta_1 = \dots = \theta_{m-2} = \frac{\pi}{4}$, o que mapeia $\mathbf{x}_M = \mathbf{x}_M^*$ sobre uma **curva unidimensional** na esfera unitária positiva $\sum_{j=1}^m f_j^2 = 1$ com $f_1 = f_2$ e $f_j = f_1 (\sqrt{2})^{j-2}$ para $3 \le j \le m-1$.
   - Conforme demonstrado por Huband et al. (2006) e Ishibuchi et al. (2016), para $m \ge 4$ o acoplamento $\theta_i = \frac{\pi}{4(1+g)}(1 + 2g x_i)$ faz com que soluções com $g(\mathbf{x}_M) > 0$ gerem pontos não-dominados fora da curva $g = 0$, tornando a verdadeira frente de Pareto de `DTLZ5` e `DTLZ6` desconhecida analiticamente para $m \ge 4$. Documentamos o impacto em `HV`, `IGD`, `IGD+` e `R2` e comparamos o escopo canônico de **84 instâncias** (`WFG1..9` + `DTLZ1..4,7` em 6 dimensões + `DTLZ5..6` em $m \in \{2,3\}$) com o escopo de **96 instâncias** (que inclui `DTLZ5..6` em $m \in \{5,8,10,15\}$ como benchmark complementar de convergência à curva 1D $g=0$).
4. **Cobertura de Extremos em `DTLZ7` e Anomalias em `WFG3.2D.csv` / `WFG4.15D.csv`:**
   - Aperfeiçoamos [`ParetoFrontValidator.generateAnalyticalDtlz7Front`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java#L983-L1067) para incluir explicitamente os vértices de fronteira por eixo ($0, x_1^*, x_{\text{jump}}, x_3^*$) em $m \ge 5$, garantindo cobertura exata dos extremos $\min f_j = 0$, $\max f_j = x_3^* \approx 0.85940086$ ($j < m$), $\min f_m = 2(m - (m-1)\psi(x_3^*))$ e $\max f_m = 2m$ em todas as dimensões $m \in \{2,3,5,8,10,15\}$.
   - Provamos no bytecode de `WFG3.java`, `WFG.java` e `Shapes.java` (jMetal 7.8) que `WFG3.evaluate` **jamais produz valores negativos** ($\forall \mathbf{z} \in \Omega, f_1(\mathbf{z}) \ge 0, f_2(\mathbf{z}) \ge 0$, e na frente ótima $f_1 \in [0, 2], f_2 \in [0, 4]$ com $f_1/2 + f_2/4 = 1$). Os **120 pontos com $f_2 \in [-1.9999957, 0)$** e $f_1 \in (2.0, 3.0]$ em `pareto_fronts/csv/WFG3.2D.csv` resultam de um bug de extrapolação ($t_0 \in [0, 1.5]$ em vez de $[0, 1]$) no gerador legado da distribuição do jMetal e estão **fora da imagem factível** de `WFG3` no jMetal 7.8.

---

## 2. Reconciliação Formal dos 14 Arquivos DTLZ em `pareto_fronts/csv/`

Durante a auditoria cruzada (T16), identificou-se que a Seção 3 de [`docs/reference-fronts-dtlz.md`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/reference-fronts-dtlz.md) reportava contagens de linhas e hashes SHA-256 incorretos para 6 arquivos DTLZ (e hashes duplicados indevidamente entre `DTLZ2`, `DTLZ3` e `DTLZ4`). A tabela abaixo estabelece a reconciliação oficial baseada na leitura física direta dos arquivos em `pareto_fronts/csv/`:

| Arquivo | Valor Reportado em T07 (`reference-fronts-dtlz.md`) | Valor Físico Real (`pareto_fronts/csv/`) | `size_bytes` Real | SHA-256 Real | `unique_points` (`tol=1e-9`) | `duplicated_points` | `dominated_points` | Status da Reconciliação |
| :--- | :---: | :---: | :---: | :--- | :---: | :---: | :---: | :--- |
| `DTLZ1.2D.csv` | `1.000` | **`1.000`** | `24.750` | `0b471c2ccabdb21d1daea0fcd2d4c8e1e82efd86f37f5ac835bcabe670d6bf6a` | `1.000` | `0` | `0` | Confirmado |
| `DTLZ1.3D.csv` | `10.000` | **`10.000`** | `204.281` | `bf531dd3e1f1f7b125f40fa14abf52c54836750904fdbfc4a6076df6f4ddf86a` | `9.901` | `99` | `0` | Confirmado (99 duplicatas com $f_1=0$) |
| `DTLZ2.2D.csv` | `1.000` | **`1.000`** | `24.720` | `b161248f67fe3ae598a110098aae5cfe65c6f27ccdb6ac1777ab9018ae87ec4a` | `1.000` | `0` | `0` | Confirmado |
| `DTLZ2.3D.csv` | `10.000` | **`10.000`** | `280.059` | `b411e177a5c8a609d6cf9396def9049d38bb749d14518695c4af1d2a9863f561` | `10.000` | `0` | `0` | Confirmado |
| `DTLZ3.2D.csv` | `1.000` *(hash errado em T07)* | **`1.000`** | `24.727` | `28b1d15a31cecb99d9c220fe03640dd06b6875aed29779981890350cad0c17dd` | `1.000` | `0` | `0` | Hash SHA-256 corrigido (distinto de `DTLZ2.2D.csv`) |
| **`DTLZ3.3D.csv`** | ~~`10.000`~~ | **`4.000`** | `232.890` | `056baef9de322cd4122abe8c54d005e6f103c6bcf56b2442726aabd4da2eb1b4` | `4.000` | `0` | `0` | **Reconciliado:** `4.000` linhas reais (não `10.000`) |
| `DTLZ4.2D.csv` | `1.000` *(hash errado em T07)* | **`1.000`** | `24.732` | `112597844f8559eafe304a323b4d5bb81481fe9695fcb2950bb4aa258d3707e4` | `1.000` | `0` | `0` | Hash SHA-256 corrigido (distinto de `DTLZ2.2D.csv`) |
| **`DTLZ4.3D.csv`** | ~~`10.000`~~ | **`4.000`** | `239.656` | `96d998f6858b058d3017c695b4f84737d2e0f433cde13d1d000e17cc509fdbee` | `4.000` | `0` | `370` | **Reconciliado:** `4.000` linhas reais (`370` dominados por arredondamento na esfera) |
| **`DTLZ5.2D.csv`** | ~~`1.000`~~ | **`200`** | `7.795` | `7c246580e142a12ba60bc61120e0ba67763a2c747333075e0e8ae296ef8b0fd6` | `200` | `0` | `0` | **Reconciliado:** `200` linhas reais (idêntico a `DTLZ6.2D.csv`) |
| `DTLZ5.3D.csv` | `166.500` | **`166.500`** | `9.773.000` | `951faf6dbc9388fda0d7ad4e594fe66668a08eabfb7665bf25da9c0ea364e6ef` | `333` | `166.167` | `0` | Confirmado ($333 \times 500$ cópias exatas) |
| **`DTLZ6.2D.csv`** | ~~`1.000`~~ | **`200`** | `7.795` | `7c246580e142a12ba60bc61120e0ba67763a2c747333075e0e8ae296ef8b0fd6` | `200` | `0` | `0` | **Reconciliado:** `200` linhas reais (idêntico a `DTLZ5.2D.csv`) |
| `DTLZ6.3D.csv` | `28.000` | **`28.000`** | `1.639.600` | `ebfee3a393d6deedc5a5eb5c549585142891ad05bd6156fdda7c190b95aad9b5` | `140` | `27.860` | `0` | Confirmado ($140 \times 200$ cópias exatas) |
| **`DTLZ7.2D.csv`** | ~~`1.002`~~ | **`101`** | `3.924` | `b40368d7d0cabb361ab9a34cd621b1ef537f3a16bc5595da33bf7f0c797c799f` | `101` | `0` | `0` | **Reconciliado:** `101` linhas reais, todas não-dominadas e dentro de $I_0 \cup I_1$ |
| **`DTLZ7.3D.csv`** | ~~`2.916`~~ | **`676`** | `12.412` | `b5db7bba77aa82c74b8876f5a7f5494ba3b3f731ae8e7a47f66574000a1b3848` | `676` | `0` | `0` *(amostra)* / `100` *(fora de $I_0 \cup I_1$)* | **Reconciliado:** grade $26 \times 26 = 676$ linhas reais (100 pontos com $f_j \in \{0.26, 0.86\}$ fora de $I_0 \cup I_1$) |

> [!IMPORTANT]
> **Esclarecimento sobre `DTLZ7.2D.csv` e `DTLZ7.3D.csv`:**
> - Em `DTLZ7.2D.csv` (101 pontos reais), **todos os 101 pontos** pertencem estritamente aos dois componentes desconexos Pareto-admissíveis $I_0 = [0, 0.251412]$ ($53$ pontos, com $f_1 \in [0.004326, 0.235691]$) e $I_1 = [0.631627, 0.859401]$ ($48$ pontos, com $f_1 \in [0.634497, 0.853225]$), com `0` pontos dominados.
> - Em `DTLZ7.3D.csv` (676 pontos reais), o arquivo foi gerado sobre a grade cartesiana $\{0.00, 0.02, \dots, 0.26\} \cup \{0.64, 0.66, \dots, 0.86\}$ ($14 + 12 = 26$ valores por eixo, $26^2 = 676$). Como a grade não inclui os pontos de mínimo local exatos $x_1^* \approx 0.251412$ e $x_3^* \approx 0.859401$ (pois $\psi(0.26) = 0.212865 > \psi(0.24) = 0.212460$ e $\psi(0.86) = 0.846490 > \psi(0.84) = 0.839170$), nenhum dos 676 pontos domina outro *dentro da amostra discreta* (`dominated_points = 0`), mas **exatamente 100 pontos** (aqueles com $f_1 \in \{0.26, 0.86\}$ ou $f_2 \in \{0.26, 0.86\}$) estão fora de $(I_0 \cup I_1)^2$ e são estritamente dominados na variedade contínua pelos pontos com $x_1^*$ ou $x_3^*$.

---

## 3. Tabela Completa dos 68 Arquivos Ativos em `pareto_fronts/csv/`

### 3.1 Família WFG (54 arquivos ativos: `WFG1`–`WFG9` em $m \in \{2, 3, 5, 8, 10, 15\}$)

| Problema | $m=2$ (`total / uniq / dom`) | $m=3$ (`total / uniq / dom`) | $m=5$ (`total / uniq / dom`) | $m=8$ (`total / uniq / dom`) | $m=10$ (`total / uniq / dom`) | $m=15$ (`total / uniq / dom`) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **`WFG1`** | `1113 / 1113 / 228` | `2000 / 2000 / 0` | `210 / 173 / 0` | `156 / 59 / 0` | `275 / 65 / 0` | `135 / 27 / 0` |
| **`WFG2`** | `119 / 111 / 0` | `2900 / 2801 / 0` | `210 / 80 / 0` | `156 / 29 / 0` | `275 / 32 / 0` | `135 / 16 / 0` |
| **`WFG3`** | `916 / 916 / 126` *(120 com $f_2 < 0$)* | `10000 / 100 / 0` | `210 / 195 / 0` | `156 / 121 / 0` | `275 / 157 / 0` | `135 / 44 / 0` |
| **`WFG4`** | `1326 / 1326 / 2` | `9997 / 9898 / 0` | `210 / 90 / 0` | `156 / 45 / 0` | `275 / 64 / 0` | `135 / 30 / 0` |
| **`WFG5`** | `837 / 837 / 55` | `10000 / 9901 / 0` | `210 / 146 / 0` | `156 / 82 / 0` | `275 / 112 / 0` | `135 / 40 / 0` |
| **`WFG6`** | `426 / 426 / 82` | `10000 / 9901 / 0` | `210 / 164 / 0` | `156 / 82 / 0` | `275 / 119 / 0` | `135 / 50 / 0` |
| **`WFG7`** | `2494 / 2494 / 6` | `10000 / 9716 / 496` | `210 / 137 / 0` | `156 / 76 / 0` | `275 / 94 / 0` | `135 / 45 / 0` |
| **`WFG8`** | `527 / 527 / 198` | `10100 / 10008 / 50` | `210 / 151 / 0` | `156 / 81 / 0` | `275 / 109 / 0` | `135 / 43 / 0` |
| **`WFG9`** | `2600 / 2600 / 0` | `10201 / 10201 / 1` | `210 / 152 / 0` | `156 / 87 / 0` | `275 / 113 / 0` | `135 / 49 / 0` |

### 3.2 Resumo dos 92 Arquivos Não-Ativos em `pareto_fronts/csv/`

A listagem integral dos 160 arquivos consta em [`docs/auditoria/reference-fronts-physical.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/reference-fronts-physical.csv). Os principais achados estruturais nos 92 arquivos não utilizados por `WFGStudy` são:
- **9 arquivos `WFG1..9.20D.csv` ($m=20$):** Todos têm `230` linhas físicas, mas alta taxa de duplicatas (apenas `15` a `71` pontos únicos).
- **2 arquivos completamente vazios (`0` bytes, `0` linhas):** `emptyD.csv` e `KernelRidgeRegressionParameterTuning.2D.csv` (SHA-256 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`).
- **9 arquivos `LZ09_F1.csv`–`LZ09_F9.csv`:** Utilizam vírgula dupla (`,,`) como separador (`500` linhas para `F1..F5, F7..F9` em 2D; `990` linhas para `F6` em 3D). Nota: `VectorUtils.readVectors(file, ",")` falha nesses arquivos com `NumberFormatException: empty String` devido ao token vazio entre as duas vírgulas.
- **Inconsistência entre sufixo do nome e número de colunas:** `Water.3D.csv` possui na verdade **5 colunas** (`5` linhas, idêntico a `MultiobjectiveTSP.5D.csv`), e `UF8.2D.csv`, `UF9.2D.csv`, `UF10.2D.csv` possuem **3 colunas** (`10.000` linhas, idênticos aos respectivos `.3D.csv`).

---

## 4. Auditoria Matemática das Formulações no Bytecode do jMetal 7.8 e Realizabilidade no Espaço de Decisão

### 4.1 Formulações Exatas de `DTLZ1`–`DTLZ7` no Bytecode (`jmetal-problem-7.8.jar`)

A descompilação via `javap -c -p` das classes `org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1` a `DTLZ7` confirma as seguintes equações exatas implementadas em `evaluate(DoubleSolution)` (com variáveis $x_0, \dots, x_{n-1} \in [0, 1]$ e $k = n - m + 1$ variáveis de distância em $\mathbf{x}_M = (x_{m-1}, \dots, x_{n-1})$):

1. **`DTLZ1`:**
   - $g(\mathbf{x}_M) = 100 \left[ k + \sum_{i=m-1}^{n-1} \left((x_i - 0.5)^2 - \cos(20\pi(x_i - 0.5))\right) \right]$.
   - Mínimo global: $g(\mathbf{x}_M^*) = 0 \iff x_i = 0.5$ ($\forall i \ge m-1$).
   - Objetivos: $f_j(\mathbf{x}) = 0.5 (1 + g(\mathbf{x}_M)) \left(\prod_{i=0}^{m-j-2} x_i\right) (1 - x_{m-j-1})$ (com o fator $(1 - x_{m-1})$ omitido para $j = 0$).
   - **Frente de Pareto ($g = 0$):** Simplex linear $\sum_{j=1}^m f_j = 0.5$, $f_j \in [0, 0.5]$.
   - **Inversão Analítica ($\mathbf{y} \mapsto \mathbf{x}^*$):** Para qualquer $\mathbf{y} \in \mathbb{R}_{\ge 0}^m$ com $\sum_{j=0}^{m-1} y_j = 0.5$, definindo $x_i = 0.5$ para $i \ge m-1$ e, para $i \in \{0, \dots, m-2\}$:
     $$x_i^* = \frac{\sum_{r=0}^{m-2-i} y_r}{\sum_{r=0}^{m-1-i} y_r} \in [0, 1] \quad (\text{com } 0/0 \mapsto 0),$$
     a avaliação `new DTLZ1(n, m).evaluate(sol)` reproduz exatamente $\mathbf{f}(\mathbf{x}^*) = \mathbf{y}$ com erro $< 10^{-15}$.

2. **`DTLZ2`, `DTLZ3` e `DTLZ4`:**
   - Em `DTLZ2` e `DTLZ4`: $g(\mathbf{x}_M) = \sum_{i=m-1}^{n-1} (x_i - 0.5)^2$, com $g(\mathbf{x}_M^*) = 0 \iff x_i = 0.5$.
   - Em `DTLZ3`: mesma $g(\mathbf{x}_M)$ multimodal de `DTLZ1`, com $g(\mathbf{x}_M^*) = 0 \iff x_i = 0.5$.
   - Ângulos: $\theta_i = \frac{\pi}{2} x_i$ em `DTLZ2`/`DTLZ3`, e $\theta_i = \frac{\pi}{2} x_i^{\alpha}$ ($\alpha = 100$) em `DTLZ4`, para $i \in \{0, \dots, m-2\}$.
   - Objetivos: $f_j(\mathbf{x}) = (1 + g(\mathbf{x}_M)) \left(\prod_{i=0}^{m-j-2} \cos\theta_i\right) \sin\theta_{m-j-1}$ (sem $\sin$ para $j = 0$).
   - **Frente de Pareto ($g = 0$):** Ortante positivo da esfera unitária $\sum_{j=1}^m f_j^2 = 1$, $f_j \in [0, 1]$.
   - **Inversão Analítica ($\mathbf{y} \mapsto \mathbf{x}^*$):** Para qualquer $\mathbf{y} \in \mathbb{R}_{\ge 0}^m$ com $\|\mathbf{y}\|_2 = 1$, definindo $x_i^* = 0.5$ ($i \ge m-1$) e, para $i \in \{0, \dots, m-2\}$:
     $$\theta_i^* = \operatorname{atan2}\!\left(y_{m-1-i},\, \sqrt{\sum_{r=0}^{m-2-i} y_r^2}\right) \in \left[0, \frac{\pi}{2}\right], \quad x_i^* = \begin{cases} \frac{2}{\pi}\theta_i^* & (\text{DTLZ2, DTLZ3}), \\ \left(\frac{2}{\pi}\theta_i^*\right)^{1/100} & (\text{DTLZ4}), \end{cases}$$
     `problem.evaluate(sol)` reproduz exatamente $\mathbf{f}(\mathbf{x}^*) = \mathbf{y}$ (erro máximo $\le 10^{-12}$ mesmo com expoente $\alpha = 100$ em `DTLZ4`).

3. **`DTLZ5` e `DTLZ6` (Curva 1D em $g = 0$ vs. Degenerescência quebrada em $m \ge 4$):**
   - No bytecode de `DTLZ5.java` e `DTLZ6.java`:
     - Em `DTLZ5`: $g(\mathbf{x}_M) = \sum_{i=m-1}^{n-1} (x_i - 0.5)^2 \implies g^* = 0 \iff x_i = 0.5$ ($i \ge m-1$).
     - Em `DTLZ6`: $g(\mathbf{x}_M) = \sum_{i=m-1}^{n-1} x_i^{0.1} \implies g^* = 0 \iff x_i = 0.0$ ($i \ge m-1$).
     - Em ambos: $\theta_0 = \frac{\pi}{2} x_0$, e para $i \in \{1, \dots, m-2\}$:
       $$\theta_i = \frac{\pi}{4(1 + g(\mathbf{x}_M))} \bigl(1 + 2 g(\mathbf{x}_M) x_i\bigr).$$
   - **Comportamento quando $g(\mathbf{x}_M) = 0$:** Substituindo $g = 0$, a expressão colapsa para $\theta_1 = \dots = \theta_{m-2} = \frac{\pi}{4}$, independentemente de $x_1, \dots, x_{m-2} \in [0, 1]$. Como $\cos(\pi/4) = \sin(\pi/4) = 2^{-1/2}$, a variedade $g(\mathbf{x}_M) = 0$ no espaço de objetivos é a **curva unidimensional** parametrizada por $\theta_0 \in [0, \pi/2]$:
     $$f_1 = f_2 = \cos(\theta_0) \cdot 2^{-\frac{m-2}{2}}, \quad f_j = \cos(\theta_0) \cdot 2^{-\frac{m-j}{2}} \;\; (3 \le j \le m-1), \quad f_m = \sin(\theta_0).$$
   - **Inversão Analítica sobre a curva $g = 0$ ($\mathbf{y} \mapsto \mathbf{x}^*$):** Dado $\mathbf{y}$ na curva $g=0$, basta definir $x_0^* = \frac{2}{\pi} \arcsin(y_{m-1}) \in [0, 1]$, $x_1^* = \dots = x_{m-2}^* = 0.5$, e $x_{m-1}^* = \dots = x_{n-1}^* = 0.5$ (`DTLZ5`) ou $0.0$ (`DTLZ6`). A avaliação `problem.evaluate(sol)` reproduz $\mathbf{y}$ com erro $< 10^{-15}$.

4. **`DTLZ7` (Frente Desconexa com $2^{m-1}$ Componentes):**
   - No bytecode de `DTLZ7.java`:
     - Para $j \in \{0, \dots, m-2\}$: $f_j(\mathbf{x}) = x_j \in [0, 1]$.
     - $g(\mathbf{x}_M) = 1 + \frac{9}{k} \sum_{i=m-1}^{n-1} x_i \implies \min g(\mathbf{x}_M) = 1 \iff x_i = 0.0$ ($\forall i \ge m-1$).
     - $h(f_1, \dots, f_{m-1}, g) = m - \sum_{j=0}^{m-2} \left[ \frac{f_j}{1 + g} \bigl(1 + \sin(3\pi f_j)\bigr) \right]$.
     - $f_{m-1}(\mathbf{x}) = (1 + g(\mathbf{x}_M)) \cdot h(f_1, \dots, f_{m-1}, g)$.
   - Quando $g(\mathbf{x}_M^*) = 1$ ($1 + g^* = 2$), tem-se $f_m = 2\left(m - \sum_{j=1}^{m-1} \psi(f_j)\right)$, onde $\psi(x) = \frac{x}{2}(1 + \sin(3\pi x))$.
   - Como minimizamos simultaneamente $(f_1, \dots, f_{m-1}, f_m)$, um ponto $(f_1, \dots, f_{m-1})$ é Pareto-ótimo se e somente se cada coordenada $f_j$ maximiza estritamente $\psi$ no acumulado de $[0, f_j]$, o que restringe cada $f_j$ à união de dois intervalos fechados disjuntos:
     $$I_0 = [0, x_1^*] \approx [0,\, 0.251411836088917], \qquad I_1 = [x_{\text{jump}}, x_3^*] \approx [0.631626530700061,\, 0.859400856644724],$$
     onde $x_1^*$ é o primeiro máximo local positivo de $\psi(x)$ ($\psi'(x_1^*) = 0$, $\psi(x_1^*) \approx 0.213161804$), $x_{\text{jump}}$ satisfaz $\psi(x_{\text{jump}}) = \psi(x_1^*)$ com $\psi'(x_{\text{jump}}) > 0$, e $x_3^*$ é o máximo global de $\psi(x)$ em $[0, 1]$ ($\psi(x_3^*) \approx 0.846497817249211$).
   - **Extremos exatos de `DTLZ7` por objetivo:**
     - Para $j \in \{1, \dots, m-1\}$: $\min f_j = 0.0$ e $\max f_j = x_3^* \approx 0.859400856644724$.
     - Para $f_m$: $\max f_m = 2m$ (atingido em $f_1 = \dots = f_{m-1} = 0$) e $\min f_m = 2\bigl(m - (m-1)\psi(x_3^*)\bigr) \approx 2m - 1.6929956345(m-1)$ (atingido em $f_1 = \dots = f_{m-1} = x_3^*$).
   - **Inversão Analítica ($\mathbf{y} \mapsto \mathbf{x}^*$):** Definindo $x_j^* = y_j$ para $j \in \{0, \dots, m-2\}$ e $x_i^* = 0.0$ para $i \ge m-1$, `new DTLZ7(n, m).evaluate(sol)` reproduz exatamente $\mathbf{f}(\mathbf{x}^*) = \mathbf{y}$ com erro $< 10^{-15}$.

---

## 5. Implicações Metodológicas de `DTLZ5` e `DTLZ6` em $m \ge 4$: Escopo de 84 vs. 96 Instâncias

### 5.1 O Fenômeno Matemático em $m \ge 4$ (Huband et al., 2006; Ishibuchi et al., 2016)

Para $m \in \{2, 3\}$, a curva 1D obtida com $g(\mathbf{x}_M) = 0$ é de fato a **verdadeira frente de Pareto global** de `DTLZ5` e `DTLZ6`. Contudo, para $m \ge 4$, quando $g(\mathbf{x}_M) > 0$, os ângulos $\theta_1, \dots, \theta_{m-2}$ deixam de ser fixos em $\pi/4$ e passam a variar no intervalo:
$$\theta_i \in \left[\frac{\pi}{4(1+g)},\; \frac{\pi(1+2g)}{4(1+g)}\right], \quad i \in \{1, \dots, m-2\}.$$
Para $m \ge 4$, existem pelo menos dois ângulos variáveis ($\theta_1, \theta_2$) que permitem reduzir simultaneamente múltiplos objetivos $f_j$ em uma proporção maior do que a penalidade radial $(1 + g)$, produzindo soluções com $g(\mathbf{x}_M) > 0$ que **não são dominadas** por nenhum ponto da curva $g(\mathbf{x}_M) = 0$ (e que inclusive dominam trechos da região vizinha).

### 5.2 Impacto sobre os Indicadores de Qualidade (`HV`, `IGD`, `IGD+`, `R2`) em $m > 3$

| Indicador | Comportamento ao usar a curva 1D ($g=0$) como Frente de Referência em $m \ge 4$ |
| :--- | :--- |
| **Normalização (`NormalizeUtils`)** | Na curva $g=0$, os extremos máximos dos primeiros objetivos encolhem exponencialmente com $m$: $\max f_1 = \max f_2 = 2^{-(m-2)/2}$ (ex.: para $m=15$, $\max f_1 = 2^{-6.5} \approx 0.0110485$). Soluções encontradas pelos MOEAs com $g > 0$ atingem valores de $f_1, f_2 \gg 2^{-(m-2)/2}$, resultando em coordenadas normalizadas $\tilde{f}_j \gg 1.0$. |
| **`PISAHypervolume` / `NormalizedHypervolume`** | Como o hipervolume no jMetal filtra ou trunca soluções com coordenadas normalizadas acima do ponto de referência $\mathbf{r} = (1, \dots, 1)^T$, soluções genuinamente não-dominadas encontradas pelo algoritmo fora da curva $g=0$ (com $\tilde{f}_j > 1$) contribuem zero para o HV, penalizando algoritmos que exploram a verdadeira frente $(m-1)$-dimensional. |
| **`IGD` e `IGD+`** | Medem exclusivamente a distância até a curva 1D ($g=0$). Portanto, em $m \ge 4$, `IGD` e `IGD+` deixam de medir a cobertura da frente de Pareto real e passam a medir apenas a proximidade ao subconjunto especial $g(\mathbf{x}_M) = 0$. |
| **`R2`** | Avalia funções de utilidade ASF sobre vetores de pesos distribuídos no simplex $\Delta^{m-1}$, enquanto a referência normalizada colapsou sobre uma linha 1D diagonal ($f_1 = f_2$), gerando forte assimetria geométrica entre as direções de busca e a referência. |

### 5.3 Comparação Objetiva entre os Escopos Experimentais (84 vs. 96 Instâncias)

| Critério | Escopo de **68 Instâncias** (Padrão Atual de `WFGStudy`) | Escopo de **84 Instâncias** (Rigor Estrito de Geometria PF Conhecida) | Escopo de **96 Instâncias** (`-DgenerateAllReferenceFronts=true`) |
| :--- | :--- | :--- | :--- |
| **Composição** | `WFG1..9` em $m \in \{2,3,5,8,10,15\}$ (54) + `DTLZ1..7` em $m \in \{2,3\}$ (14) | `WFG1..9` em 6 dim (54) + `DTLZ1..4,7` em 6 dim (30) + `DTLZ5..6` apenas em $m \in \{2,3\}$ (4) = **88** *(ou **84** se `DTLZ5..6` forem excluídos em todas as 6 dimensões: $54 + 30 = 84$)* | `WFG1..9` em 6 dim (54) + `DTLZ1..7` em 6 dim (42) = **96** |
| **Validade Analítica da PF** | 100% das frentes têm geometria analítica fechada conhecida (com ressalvas de amostragem nos CSVs do jMetal). | 100% das instâncias possuem frente de Pareto global analiticamente exata e realizável no espaço de decisão em todas as dimensões avaliadas. | 88 instâncias têm PF global exata; 8 instâncias (`DTLZ5,6` em $m \in \{5,8,10,15\}$) avaliam convergência à variedade degenerada $g(\mathbf{x}_M) = 0$. |
| **Recomendação Metodológica** | Mantém 100% de fidelidade aos arquivos originais de `pareto_fronts/csv/`. | **Mais defensável para comparação agregada principal** caso se expanda `DTLZ` para $m \ge 5$, evitando viés de normalização em `DTLZ5/6` ($m \ge 4$). | Caso utilizado, os resultados de `DTLZ5` e `DTLZ6` em $m \in \{5,8,10,15\}$ **devem ser reportados separadamente** como benchmark degenerado complementar (convergência a $g=0$). |

---

## 6. Auditoria Matemática da Família `WFG1`–`WFG9` no Bytecode do jMetal 7.8

### 6.1 Estrutura Geral e Fatores de Escala ($S_j = 2j$)

A inspeção via `javap -c -p` de `org.uma.jmetal.problem.multiobjective.wfg.WFG`, `Shapes`, `Transformations` e `WFG1`–`WFG9` confirma que para todos os problemas `WFG1`–`WFG9`:
- As variáveis de decisão pertencem a $z_i \in [0, 2(i+1)]$ para $i \in \{0, \dots, n-1\}$ ($k$ variáveis de posição e $l$ variáveis de distância).
- As constantes de escala são $S_j = 2j$ para $j \in \{1, \dots, m\}$ (`s[i] = 2 * (i + 1)`), e a constante de distância é $D = 1$.
- Toda transformação em `Transformations.java` (`bPoly`, `bFlat`, `bParam`, `sLinear`, `sDecept`, `sMulti`, `rSum`, `rNonsep`) passa pelo método `correctTo01(float a)`, garantindo que todos os vetores de transição $\mathbf{t}^1, \mathbf{t}^2, \mathbf{t}^3$ pertençam estritamente a $[0, 1]$.
- Na frente de Pareto ($t_{m-1} = 0 \implies x_{m-1} = 0$), os objetivos reduzem-se a $f_j(\mathbf{x}) = S_j \cdot h_j(x_0, \dots, x_{m-2})$ para $j \in \{1, \dots, m\}$, com $f_j^{\text{teórico}} \in [0, 2j]$.

| Problema(s) | Função de Forma (`Shapes.java`) | Equação Analítica da Frente de Pareto ($x_{m-1} = 0$) | Imagem Teórica por Objetivo |
| :--- | :--- | :--- | :--- |
| **`WFG1`** | `convex` ($j=1..m-1$) + `mixed` ($j=m$, $\alpha=1, A=5$) | Híbrida convexa/mista parametrizada por $x_0, \dots, x_{m-2} \in [0, 1]$ | $f_j \in [0, 2j]$ |
| **`WFG2`** | `convex` ($j=1..m-1$) + `disc` ($j=m$, $\alpha=1, \beta=1, A=5$) | Desconexa com regiões dominadas induzidas por $\cos^2(5\pi x_0)$ | $f_j \in [0, 2j]$ ($f_m$ descontínuo) |
| **`WFG3`** | `linear` ($j=1..m$) com $A_1 = 1, A_2 = \dots = A_{m-1} = 0$ | Segmento de reta 1D degenerado (ver Seção 6.2) | Ver Seção 6.2 |
| **`WFG4`–`WFG9`** | `concave` ($j=1..m$) com $A_1 = \dots = A_{m-1} = 1$ | Hiper-elipsoide côncavo no 1º ortante: $\sum_{j=1}^m \left(\frac{f_j}{2j}\right)^2 = 1, \; f_j \ge 0$ | $f_j \in [0, 2j]$ |

### 6.2 Prova Matemática da Anomalia de `WFG3.2D.csv` ($f_2 \in [-2, 4]$ vs. Imagem Real de `WFG3.evaluate`)

1. **O que o bytecode de `WFG3` no jMetal 7.8 calcula:**
   - Em `WFG3.java`, o vetor de degenerescência é inicializado como $A_0 = 1$ e $A_1 = \dots = A_{m-2} = 0$.
   - Em `WFG.calculateX(float[] t)`:
     $$x_i = \max(t_{m-1}, A_i) \cdot (t_i - 0.5) + 0.5 \quad (i = 0, \dots, m-2), \qquad x_{m-1} = t_{m-1}.$$
   - Como `correctTo01` garante $t_i \in [0, 1]$ para todo $\mathbf{z} \in \Omega = \prod_{i=1}^n [0, 2i]$, segue que $\max(t_{m-1}, A_i) \in [0, 1]$ e $t_i - 0.5 \in [-0.5, 0.5]$, logo **$x_i \in [0, 1]$ para todo $i \in \{0, \dots, m-1\}$ em todo o espaço de decisão factível $\Omega$**.
   - Em `Shapes.linear(float[] x, int m)`:
     $$h_1(\mathbf{x}) = \prod_{i=0}^{M-2} x_i, \qquad h_m(\mathbf{x}) = \left(\prod_{i=0}^{M-m-1} x_i\right)(1 - x_{M-m}) \;\; (m \ge 2).$$
   - Como $x_i \in [0, 1]$, temos $h_m(\mathbf{x}) \in [0, 1]$ e $f_m(\mathbf{z}) = x_{M-1} + 2m \cdot h_m(\mathbf{x}) \ge 0$ **para toda solução factível $\mathbf{z} \in \Omega$**.
   - Em particular, para $M = 2$ na frente de Pareto ($x_1 = t_1 = 0$), tem-se $x_0 = t_0 \in [0, 1]$ e:
     $$f_1 = 2 x_0 \in [0, 2], \qquad f_2 = 4(1 - x_0) \in [0, 4], \qquad \frac{f_1}{2} + \frac{f_2}{4} = 1.$$
2. **O que está armazenado em `pareto_fronts/csv/WFG3.2D.csv`:**
   - O arquivo contém `916` linhas, todas colineares sobre a reta $\frac{f_1}{2} + \frac{f_2}{4} = 1$ (erro máximo $< 10^{-5}$).
   - Porém, enquanto os primeiros `796` pontos têm $f_1 \in [0, 2.0]$ e $f_2 \in [0, 4.0]$, os últimos **`120` pontos** têm $f_1 \in (2.0, 3.0]$ e **$f_2 = 4 - 2f_1 \in [-1.9999957, -0.0041999)$** (além de `126` pontos dominados).
   - **Causa raiz:** Para $M \ge 3$, na frente de Pareto ($t_{M-1} = 0$), os parâmetros intermediários ficam travados em $x_1 = \dots = x_{M-2} = 0.5$, de modo que para $j < M$ o limite superior de $f_j / (2j)$ é $2^{-(M-1-j)}$. Já no gerador legado em C do WFG Toolkit que originou `WFG3.2D.csv`, parametrizou-se equivocadamente $x_0 \in [0, 1.5]$ (Gerando $f_1 = 2 x_0 \in [0, 3]$ e $f_2 = 4(1 - x_0) \in [-2, 4]$).
   - **Impacto na Normalização:** Como `NormalizeUtils.normalize` mapeia $[\min f_2, \max f_2] = [-2, 4] \mapsto [0, 1]$, a imagem factível real $f_2 \in [0, 4]$ produzida pelos algoritmos em `WFG3` ($m=2$) fica comprimida no subintervalo $[1/3, 1]$ ($\tilde{f}_2 = (f_2 + 2)/6$), e `f_1` real $\in [0, 2]$ fica comprimida em $[0, 2/3]$ ($\tilde{f}_1 = f_1 / 3$). Embora o ordenamento monotônico entre algoritmos seja preservado, os valores absolutos de `HV`, `IGD`, `IGD+` e `R2` em `WFG3_2` refletem essa escala linear deslocada.

### 6.3 Análise de Cobertura de Extremos em `WFG4.15D.csv` e `WFG1..9` ($m \ge 5$)

Em `pareto_fronts/csv/`, todos os arquivos `WFG1..9` para $m \in \{5, 8, 10, 15\}$ possuem exatamente `210`, `156`, `275` e `135` linhas (correspondentes ao número de vetores de Das & Dennis), mas com severa perda de pontos únicos e truncamento dos extremos nos primeiros objetivos:
- Em **`WFG4.15D.csv`** (`135` linhas, apenas **`30` pontos únicos** e `105` duplicatas), embora todos os pontos satisfaçam exatamente o hiper-elipsoide côncavo $\sum_{j=1}^{15} (f_j / (2j))^2 = 1$ (erro máximo $5.55 \times 10^{-7}$), o intervalo observado no arquivo é muito menor que o intervalo teórico $[0, 2j]$ para os primeiros objetivos:
  - $f_1 \in [1.05 \times 10^{-6},\; \mathbf{0.007593}]$ (vs. máximo teórico $2.0$: cobertura de apenas $0.38\%$ do semi-eixo!).
  - $f_2 \in [2.35 \times 10^{-6},\; \mathbf{0.030136}]$ (vs. máximo teórico $4.0$: cobertura de $0.75\%$).
  - $f_3 \in [1.30 \times 10^{-5},\; \mathbf{0.055902}]$ (vs. máximo teórico $6.0$: cobertura de $0.93\%$).
  - Apenas $f_{15}$ atinge o extremo teórico ($\max f_{15} = 30.0000$).
- Esse mesmo fenômeno ocorre em `WFG1..9` para $m \in \{8, 10, 15\}$ porque a transformação trigonométrica em cascata $\prod_{i=0}^{m-j-1} \sin(\theta_i \pi / 2)$ amostrando $\theta_i$ discretamente multiplica $(m-1)$ senos menores que $1$, amortecendo exponencialmente $f_1, f_2, f_3$ exceto quando $\theta_0 = \dots = \theta_{m-2} = 1$ (vértice que não foi incluído sem ruído nos arquivos do jMetal).

---

## 7. Cobertura de Extremos nas Frentes Analíticas Geradas por `ParetoFrontValidator`

A suíte [`ReferenceFrontPhysicalAndMathAuditTest`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/qualityindicator/ReferenceFrontPhysicalAndMathAuditTest.java) valida que `ParetoFrontValidator.generateAnalyticalDtlzFront` cobre com exatidão de ponto flutuante ($\le 10^{-9}$) todos os extremos mínimos e máximos teóricos por objetivo em todas as dimensões $m \in \{2, 3, 5, 8, 10, 15\}$:

| Problema | $\min f_j$ ($1 \le j \le m-1$) | $\max f_j$ ($1 \le j \le m-1$) | $\min f_m$ | $\max f_m$ | Verificado em $m \in \{2,3,5,8,10,15\}$? |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`DTLZ1`** | $0.0$ | $0.5$ | $0.0$ | $0.5$ | Sim (erro $< 10^{-15}$) |
| **`DTLZ2`–`DTLZ4`** | $0.0$ | $1.0$ | $0.0$ | $1.0$ | Sim (erro $< 10^{-15}$) |
| **`DTLZ5`–`DTLZ6`** *(curva $g=0$)* | $0.0$ | $2^{-\max(0, m-1-j)/2}$ ($j=1: 2^{-(m-2)/2}$) | $0.0$ | $1.0$ | Sim (erro $< 10^{-15}$) |
| **`DTLZ7`** | $0.0$ | $x_3^* \approx 0.859400856644724$ | $2(m - (m-1)\psi(x_3^*))$ | $2m$ | Sim (erro $< 10^{-12}$) |

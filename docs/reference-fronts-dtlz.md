# Auditoria e Validação Matemática das Frentes de Pareto de Referência (`DTLZ1–DTLZ7`) — T07

## 1. Escopo e Objetivo

Este documento registra a auditoria exaustiva e a validação matemática das frentes de Pareto de referência para os problemas `DTLZ1` a `DTLZ7` no escopo da Tarefa **T07 — Parte 4**:

1. **Inventário Completo e Proveniência**: Mapeamento de todos os arquivos `DTLZ*.csv` e `DTLZ*.pf` presentes:
   - Nos artefatos Maven do jMetal 7.8 (`jmetal-core-7.8.jar`, `jmetal-problem-7.8.jar`, `jmetal-algorithm-7.8.jar`, `jmetal-component-7.8.jar`, `jmetal-lab-7.8.jar`);
   - No repositório oficial `jMetal/resources/referenceFrontsCSV/`;
   - No diretório ativo do projeto `pareto_fronts/csv/`;
   - No diretório auxiliar `pareto/` da raiz do workspace.
2. **Tabela Completa de Existência, Cardinalidade, Intervalos, Duplicatas e Hash SHA-256** para `DTLZ1–DTLZ7` e $m \in \{2, 3, 5, 8, 10, 15\}$ (e também para as dimensões extras $m \in \{4, 6, 8\}$ disponíveis em `jMetal/resources/referenceFrontsCSV/`).
3. **Análise Específica de Bytecode e Derivação Matemática de `DTLZ5`, `DTLZ6` e `DTLZ7` no jMetal 7.8**:
   - Desmontagem de `org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1` a `DTLZ7` em `jmetal-problem-7.8.jar`;
   - Análise da chamada `new DTLZ*(L, m)` com `L = 20` em `WFGStudy.java` e seu impacto sobre $k = n - m + 1 = 21 - m$;
   - Derivação exata da curva degenerada 1D de `DTLZ5` e `DTLZ6` para $m \ge 3$;
   - Derivação exata dos $2^{m-1}$ componentes desconexos e do domínio analítico $[f_m^{\min}(m), 2m]$ do último objetivo $f_m$ em `DTLZ7`.
4. **Modos Controlados Desativados por Padrão**:
   - `-DgenerateAllReferenceFronts=false` (geração analítica validada em `resources/referenceFronts/pf_gen/analytical/`);
   - `-DgenerateEmpiricalReferenceFronts=false` (consolidação empírica não-dominada com metadados em `resources/referenceFronts/pf_gen/empirical/`).

---

## 2. Inventário de Fontes e Proveniência

### 2.1. Verificação nos JARs Maven do jMetal 7.8

A inspeção via `jar tf` dos cinco JARs resolvidos pelo Maven em `~/.m2/repository/org/uma/jmetal/*-7.8.jar` comprova que **nenhum arquivo `.csv` ou `.pf` de frente de Pareto é empacotado dentro dos JARs binários do jMetal 7.8**. No ecossistema jMetal 7.8, os arquivos CSV de frentes de referência são distribuídos exclusivamente na árvore de código-fonte do repositório oficial em `resources/referenceFrontsCSV/`.

### 2.2. Inventário de `jMetal/resources/referenceFrontsCSV/` vs `pareto_fronts/csv/` vs `pareto/`

1. **`jMetal/resources/referenceFrontsCSV/`**: Contém exatamente **29 arquivos** DTLZ:
   - Para `DTLZ1`, `DTLZ2`, `DTLZ3`, `DTLZ4` e `DTLZ7`: existem arquivos apenas para $m \in \{2, 3, 4, 6, 8\}$ (`25` arquivos).
   - Para `DTLZ5` e `DTLZ6`: existem arquivos apenas para $m \in \{2, 3\}$ (`4` arquivos).
   - **Não existem** arquivos oficiais no jMetal para $m \in \{5, 10, 15\}$ em nenhum problema DTLZ, nem para $m \in \{4, 6, 8\}$ em `DTLZ5` e `DTLZ6`.
2. **`pareto_fronts/csv/` (diretório ativo de `WFGStudy.java`)**:
   - Contém **68 arquivos CSV**: os 54 arquivos `WFG1–WFG9` para $m \in \{2, 3, 5, 8, 10, 15\}$ e os **14 arquivos `DTLZ1–DTLZ7` para $m \in \{2, 3\}$**.
   - Todos os 14 arquivos `DTLZ{1..7}.{2,3}D.csv` em `pareto_fronts/csv/` possuem **hash SHA-256 bit-a-bit idêntico** aos arquivos homônimos de `jMetal/resources/referenceFrontsCSV/`.
3. **`pareto/` (diretório auxiliar na raiz do workspace, não referenciado por `WFGStudy.java`)**:
   - Contém arquivos `.pf` legados separados por espaço/tabulação (`DTLZ1.2D.pf`, `DTLZ1.3D.pf`, etc.) e arquivos de alta dimensão gerados externamente (`DTLZ{1..4}.{5,8,10,15}D.pf` com $10\,000$ pontos cada e `DTLZ5.3D.pf`), mas **não contém** `DTLZ5/6` para $m \in \{5, 8, 10, 15\}$ nem `DTLZ7` para $m \in \{5, 8, 10, 15\}$.

---

## 3. Matriz Completa `DTLZ1–DTLZ7` para $m \in \{2, 3, 5, 8, 10, 15\}$

### 3.1. Status nas 42 Combinações do Estudo ($7 \text{ problemas} \times 6 \text{ dimensões}$)

| Problema | $m=2$ (`pareto_fronts/csv/`) | $m=3$ (`pareto_fronts/csv/`) | $m=5$ | $m=8$ | $m=10$ | $m=15$ |
|---|---|---|---|---|---|---|
| **DTLZ1** | Presente (`1000` pts) | Presente (`9901` pts, `99` dup.) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` (existe em `jMetal/resources/referenceFrontsCSV/DTLZ1.8D.csv`) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal |
| **DTLZ2** | Presente (`1000` pts) | Presente (`10000` pts) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` (existe em `jMetal/resources/referenceFrontsCSV/DTLZ2.8D.csv`) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal |
| **DTLZ3** | Presente (`1000` pts) | Presente (`10000` pts) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` (existe em `jMetal/resources/referenceFrontsCSV/DTLZ3.8D.csv`) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal |
| **DTLZ4** | Presente (`1000` pts) | Presente (`10000` pts) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` (existe em `jMetal/resources/referenceFrontsCSV/DTLZ4.8D.csv`) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal |
| **DTLZ5** | Presente (`1000` pts) | Presente (`166500` linhas, `333` únicos) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal |
| **DTLZ6** | Presente (`1000` pts) | Presente (`28000` linhas, `140` únicos) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal |
| **DTLZ7** | Presente (`1002` pts) | Presente (`2916` pts) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` (existe em `jMetal/resources/referenceFrontsCSV/DTLZ7.8D.csv`) | Ausente em `pareto_fronts/csv/` e no jMetal | Ausente em `pareto_fronts/csv/` e no jMetal |

### 3.2. Inventário Detalhado e Hashes SHA-256 dos 14 Arquivos DTLZ em `pareto_fronts/csv/` (Idênticos a `jMetal/resources/referenceFrontsCSV/`)

| Arquivo | $m$ | Total de Pontos | Pontos Únicos | Duplicatas Exatas | Intervalo por Objetivo $[f_j^{\min}, f_j^{\max}]$ | Erro Analítico Máx. | Hash SHA-256 |
|---|---:|---:|---:|---:|---|---:|---|
| `DTLZ1.2D.csv` | 2 | 1000 | 1000 | 0 | $f_1, f_2 \in [0.0005, 0.4995]$ | $0.000 \times 10^0$ ($\sum f_j = 0.5$) | `8be24fe26e37ff3db09a68cd1cdcb51d1a3ee72ce6fd53c15b38443d928f4fa8` |
| `DTLZ1.3D.csv` | 3 | 9901 | 9802 | **99** | $f_1, f_2, f_3 \in [0.0, 0.5]$ | $0.000 \times 10^0$ ($\sum f_j = 0.5$) | `a94f3c8fb4c75ef9eb24405593ce1555c92fbce4be712bc54dfed41e4d05c118` |
| `DTLZ2.2D.csv` | 2 | 1000 | 1000 | 0 | $f_1, f_2 \in [0.001572, 0.999999]$ | $1.100 \times 10^{-6}$ ($\sum f_j^2 = 1$) | `2e5874209b13a408ed9e71b1866942a21b2ef156fe7cf18a2fd5c3e124dc5fc0` |
| `DTLZ2.3D.csv` | 3 | 10000 | 10000 | 0 | $f_1, f_2, f_3 \in [0.0, 1.0]$ | $1.391 \times 10^{-6}$ ($\sum f_j^2 = 1$) | `c4f3deeecefcdddfddaf3cf4fc2db3c0d19e8405dfba74c8ebcb64f482aa69c0` |
| `DTLZ3.2D.csv` | 2 | 1000 | 1000 | 0 | $f_1, f_2 \in [0.001572, 0.999999]$ | $1.100 \times 10^{-6}$ ($\sum f_j^2 = 1$) | `2e5874209b13a408ed9e71b1866942a21b2ef156fe7cf18a2fd5c3e124dc5fc0` |
| `DTLZ3.3D.csv` | 3 | 10000 | 10000 | 0 | $f_1, f_2, f_3 \in [0.0, 1.0]$ | $1.391 \times 10^{-6}$ ($\sum f_j^2 = 1$) | `c4f3deeecefcdddfddaf3cf4fc2db3c0d19e8405dfba74c8ebcb64f482aa69c0` |
| `DTLZ4.2D.csv` | 2 | 1000 | 1000 | 0 | $f_1, f_2 \in [0.001572, 0.999999]$ | $1.100 \times 10^{-6}$ ($\sum f_j^2 = 1$) | `2e5874209b13a408ed9e71b1866942a21b2ef156fe7cf18a2fd5c3e124dc5fc0` |
| `DTLZ4.3D.csv` | 3 | 10000 | 10000 | 0 | $f_1, f_2, f_3 \in [0.0, 1.0]$ | $1.391 \times 10^{-6}$ ($\sum f_j^2 = 1$) | `c4f3deeecefcdddfddaf3cf4fc2db3c0d19e8405dfba74c8ebcb64f482aa69c0` |
| `DTLZ5.2D.csv` | 2 | 1000 | 1000 | 0 | $f_1, f_2 \in [0.0, 1.0]$ | $3.331 \times 10^{-16}$ ($\sum f_j^2 = 1$) | `4541c8ce6575f7889c78f80e314842f2a0f25877e1ca22dc089958de4577952f` |
| `DTLZ5.3D.csv` | 3 | 166500 | **333** | **166167** | $f_1, f_2 \in [0.0, 0.707107]$, $f_3 \in [0.0, 1.0]$ | $6.661 \times 10^{-16}$ ($\sum f_j^2 = 1, f_1 = f_2$) | `be315cd39de703030dc96e67961724fc0650ba17761303f14a5022de43572f9a` |
| `DTLZ6.2D.csv` | 2 | 1000 | 1000 | 0 | $f_1, f_2 \in [0.0, 1.0]$ | $3.331 \times 10^{-16}$ ($\sum f_j^2 = 1$) | `a41fc885852f89fd1a0e3281d57beee842a41871667c996f6b29a212e8543d70` |
| `DTLZ6.3D.csv` | 3 | 28000 | **140** | **27860** | $f_1, f_2 \in [0.0, 0.707107]$, $f_3 \in [0.0, 1.0]$ | $4.441 \times 10^{-16}$ ($\sum f_j^2 = 1, f_1 = f_2$) | `cd41122e3da7c4435f39ed7d6d58f5a755479fb20c9925abbe56708111fcdeca` |
| `DTLZ7.2D.csv` | 2 | 1002 | 1002 | 0 | $f_1 \in [0.0, 0.859401]$, $f_2 \in [2.307004, 4.0]$ | $4.760 \times 10^{-6}$ | `da1e1b6dfdc05136e36a76054e1d102a72eb76f2a3df6dc19fd76dbdf289d7ab` |
| `DTLZ7.3D.csv` | 3 | 2916 | 2916 | 0 | $f_1, f_2 \in [0.0, 0.86]$, $f_3 \in [2.614022, 6.0]$ | $4.977 \times 10^{-6}$ (contém 109 pts dominados devido a $0.26, 0.86$) | `cf2409f4c36d2d80c5b6324d84145940d515f3800a7f63a84550794348511b90` |

### 3.3. Inventário dos 15 Arquivos Adicionais em `jMetal/resources/referenceFrontsCSV/` ($m \in \{4, 6, 8\}$)

| Arquivo | $m$ | Total de Pontos | Pontos Únicos | Duplicatas Exatas | Intervalo $[f_j^{\min}, f_j^{\max}]$ | Erro Analítico Máx. | Hash SHA-256 |
|---|---:|---:|---:|---:|---|---:|---|
| `DTLZ1.4D.csv` | 4 | 1330 | 1330 | 0 | $f_1..f_4 \in [0.0, 0.5]$ | $5.551 \times 10^{-17}$ | `bd720cd579e4a1f621b60695467be3126b17021bc5f5f9e05f7dc105bc7a5eb4` |
| `DTLZ1.6D.csv` | 6 | 1287 | 1287 | 0 | $f_1..f_6 \in [0.0, 0.5]$ | $1.110 \times 10^{-16}$ | `7323ef6d2b61144df8261653c33dc20671647eb5022340fb8dbbb591f2a7f669` |
| `DTLZ1.8D.csv` | 8 | 1716 | 1716 | 0 | $f_1..f_8 \in [0.0, 0.5]$ | $0.000 \times 10^0$ | `15ba501c5863274a3ebbd5d97567576e57f4b2fc085ee8b64de8065dc7c7417e` |
| `DTLZ2.4D.csv` | 4 | 1330 | 1330 | 0 | $f_1..f_4 \in [0.0, 1.0]$ | $4.441 \times 10^{-16}$ | `0bc5f11b48e1a66fc4c397fd28f6196ca83b2192ffba5b13f8f478d86b04d30b` |
| `DTLZ2.6D.csv` | 6 | 1287 | 1287 | 0 | $f_1..f_6 \in [0.0, 1.0]$ | $4.441 \times 10^{-16}$ | `fb0fd896959dc9aaedcb43dc42143f29206cb85b8bf57bd52dc8b8d3363b81cf` |
| `DTLZ2.8D.csv` | 8 | 1716 | 1716 | 0 | $f_1..f_8 \in [0.0, 1.0]$ | $6.661 \times 10^{-16}$ | `67725fa82a596809841625f24d432e8d47b59a3a830240559301e6b54cc0e95f` |
| `DTLZ3.4D.csv` | 4 | 1330 | 1330 | 0 | $f_1..f_4 \in [0.0, 1.0]$ | $4.441 \times 10^{-16}$ | `0bc5f11b48e1a66fc4c397fd28f6196ca83b2192ffba5b13f8f478d86b04d30b` |
| `DTLZ3.6D.csv` | 6 | 1287 | 1287 | 0 | $f_1..f_6 \in [0.0, 1.0]$ | $4.441 \times 10^{-16}$ | `fb0fd896959dc9aaedcb43dc42143f29206cb85b8bf57bd52dc8b8d3363b81cf` |
| `DTLZ3.8D.csv` | 8 | 1716 | 1716 | 0 | $f_1..f_8 \in [0.0, 1.0]$ | $6.661 \times 10^{-16}$ | `67725fa82a596809841625f24d432e8d47b59a3a830240559301e6b54cc0e95f` |
| `DTLZ4.4D.csv` | 4 | 1330 | 1330 | 0 | $f_1..f_4 \in [0.0, 1.0]$ | $4.441 \times 10^{-16}$ | `0bc5f11b48e1a66fc4c397fd28f6196ca83b2192ffba5b13f8f478d86b04d30b` |
| `DTLZ4.6D.csv` | 6 | 1287 | 1287 | 0 | $f_1..f_6 \in [0.0, 1.0]$ | $4.441 \times 10^{-16}$ | `fb0fd896959dc9aaedcb43dc42143f29206cb85b8bf57bd52dc8b8d3363b81cf` |
| `DTLZ4.8D.csv` | 8 | 1716 | 1716 | 0 | $f_1..f_8 \in [0.0, 1.0]$ | $6.661 \times 10^{-16}$ | `67725fa82a596809841625f24d432e8d47b59a3a830240559301e6b54cc0e95f` |
| `DTLZ7.4D.csv` | 4 | 1331 | 1331 | 0 | $f_1..f_3 \in [0.0, 0.859401]$, $f_4 \in [2.921006, 8.0]$ | $0.000 \times 10^0$ (contém 227 pts fora de $I_0 \cup I_1$, ex.: $0.619528$) | `29f1de2c27e8dc5a2f3915d87e22c87382829064b4293797538e290866cfdd9a` |
| `DTLZ7.6D.csv` | 6 | 1024 | 1024 | 0 | $f_1..f_5 \in [0.0, 0.859401]$, $f_6 \in [3.535011, 12.0]$ | $1.776 \times 10^{-15}$ (contém 512 pts fora de $I_0 \cup I_1$) | `58a02a5b6a08280702446a20660a70f6f8bd603b9ee93ff1ef932325f213ef1e` |
| `DTLZ7.8D.csv` | 8 | 2187 | 2187 | 0 | $f_1..f_7 \in [0.0, 0.859401]$, $f_8 \in [4.149015, 16.0]$ | $3.553 \times 10^{-15}$ (contém 1458 pts fora de $I_0 \cup I_1$) | `a9fc0a40219129dc4b1b11bf5e15fb9560be581c016967f36a48870abebfcde6` |

---

## 4. Análise Específica de Bytecode e Derivação Matemática de `DTLZ5`, `DTLZ6` e `DTLZ7` no jMetal 7.8

### 4.1. Construtores de `DTLZ1–DTLZ7` e o Parâmetro $L = 20$ em `WFGStudy.java`

A desmontagem via `javap -c -p` de `org.uma.jmetal.problem.multiobjective.dtlz.DTLZ1` a `DTLZ7` em `jmetal-problem-7.8.jar` confirma que todos os 7 problemas possuem dois construtores:

1. Construtor padrão `DTLZ*()`:
   - `DTLZ1()`: `this(7, 3)` ($k = 5$ variáveis de distância);
   - `DTLZ2()`, `DTLZ3()`, `DTLZ4()`, `DTLZ5()`, `DTLZ6()`: `this(12, 3)` ($k = 10$ variáveis de distância);
   - `DTLZ7()`: `this(22, 3)` ($k = 20$ variáveis de distância).
2. Construtor parametrizado `DTLZ*(Integer numberOfVariables, Integer numberOfObjectives)`:
   - O **primeiro argumento é `numberOfVariables` ($n$)** (número total de variáveis de decisão), e **não** o número de variáveis de distância $k$.
   - Internamente (ex.: `DTLZ7.evaluate`, bytecode offsets `20–27`), o número de variáveis de distância $k$ que entram no cálculo de $g(\mathbf{x}_M)$ é:
     $$k = \text{numberOfVariables} - \text{numberOfObjectives} + 1 = n - m + 1$$
   - Em `WFGStudy.java` (linhas 236–242), os problemas são instanciados como `new DTLZ*(L, m)` onde `L = 20`. Isso significa que $n = 20$ fixo para todas as dimensões, resultando em:
     - $m = 2 \implies k = 19$
     - $m = 3 \implies k = 18$
     - $m = 5 \implies k = 16$
     - $m = 8 \implies k = 13$
     - $m = 10 \implies k = 11$
     - $m = 15 \implies k = 6$
   - Como $k \ge 6 \ge 1$ para todos os $m \in \{2, 3, 5, 8, 10, 15\}$, o subvetor $\mathbf{x}_M$ de variáveis de distância é sempre não-vazio ($k \ge 1$) e a equação analítica da frente de Pareto ($g(\mathbf{x}_M) = 0$) **não se altera** para nenhum $m \le 15$. Contudo, registra-se que manter $n = 20$ fixo faz com que $k$ diminua conforme $m$ cresce, diferindo da convenção de Deb et al. (2002) em que $n = m + k - 1$ com $k \in \{5, 10, 20\}$ fixo.

---

### 4.2. Derivação Matemática de `DTLZ5` e `DTLZ6` no jMetal 7.8

Na desmontagem de `DTLZ5.evaluate` (`jmetal-problem-7.8.jar`, offsets `130–298`) e `DTLZ6.evaluate` (`offsets 129–297`):

1. **Cálculo de $g(\mathbf{x}_M)$**:
   - Em `DTLZ5`: $g(\mathbf{x}_M) = \sum_{i=m-1}^{n-1} (x_i - 0.5)^2$, atingindo $g^* = 0$ quando $x_i = 0.5$ ($\forall i \ge m-1$).
   - Em `DTLZ6`: $g(\mathbf{x}_M) = \sum_{i=m-1}^{n-1} x_i^{0.1}$, atingindo $g^* = 0$ quando $x_i = 0$ ($\forall i \ge m-1$).
2. **Transformação angular $\theta_0, \dots, \theta_{m-2}$**:
   - Bytecode offsets `130–145`:
     $$\theta_0 = \frac{\pi}{2} x_0, \quad x_0 \in [0, 1] \implies \theta_0 \in \left[0, \frac{\pi}{2}\right]$$
   - Bytecode offsets `146–196`: para todo $i \in \{1, \dots, m-2\}$:
     $$t = \frac{\pi}{4(1 + g)}, \quad \theta_i = t \cdot (1 + 2 g x_i)$$
3. **Colapso em Curva 1D quando $g = 0$ (para qualquer $m \ge 3$)**:
   - Substituindo $g = 0$ na expressão acima, as variáveis de posição $x_1, \dots, x_{m-2}$ são multiplicadas por $2g = 0$ e desaparecem:
     $$\theta_1 = \theta_2 = \dots = \theta_{m-2} = \frac{\pi}{4} \implies \cos(\theta_i) = \sin(\theta_i) = \frac{1}{\sqrt{2}} = 2^{-1/2}$$
   - Os objetivos $f_1, \dots, f_m$ (índices $0 \dots m-1$ no código) para $g = 0$ são:
     $$\begin{aligned}
     f_1 &= \cos(\theta_0) \cdot \left(\frac{1}{\sqrt{2}}\right)^{m-2} \\
     f_2 &= \cos(\theta_0) \cdot \left(\frac{1}{\sqrt{2}}\right)^{m-2} = f_1 \\
     f_j &= \cos(\theta_0) \cdot \left(\frac{1}{\sqrt{2}}\right)^{m-j} = f_1 \cdot (\sqrt{2})^{j-2}, \quad \forall j \in \{3, \dots, m-1\} \\
     f_m &= \sin(\theta_0)
     \end{aligned}$$
   - Verifica-se a identidade da esfera unitária:
     $$\sum_{j=1}^m f_j^2 = \cos^2(\theta_0) \underbrace{\left(2 \cdot 2^{-(m-2)} + \sum_{j=3}^{m-1} 2^{-(m-j)}\right)}_{= 1} + \sin^2(\theta_0) = \cos^2(\theta_0) + \sin^2(\theta_0) = 1$$
   - **Explicação das duplicatas massivas em `DTLZ5.3D.csv` e `DTLZ6.3D.csv`**: O gerador original do jMetal amostrou uma grade sobre $(x_0, x_1)$ em 3D; como $x_1$ é anulado por $g = 0$, todos os $500$ valores de $x_1$ para um mesmo $x_0$ produziram exatamente o mesmo ponto $(f_1, f_2, f_3)$, gerando $333 \times 500 = 166\,500$ linhas em `DTLZ5.3D.csv` ($166\,167$ duplicatas) e $140 \times 200 = 28\,000$ linhas em `DTLZ6.3D.csv` ($27\,860$ duplicatas).
   - **Nota da literatura sobre `DTLZ5/6` para $m \ge 4$**: Huband et al. (2006) demonstraram que, quando $g(\mathbf{x}_M) > 0$, os ângulos $\theta_1, \dots, \theta_{m-2}$ deixam de ser fixos em $\pi/4$, o que permite a existência de soluções com $g > 0$ não-dominadas fora da curva $g = 0$ quando $m \ge 4$. Todavia, na subvariedade $g(\mathbf{x}_M) = 0$ implementada em `DTLZ5.java` e `DTLZ6.java` no jMetal 7.8, a imagem analítica exata é a curva 1D parametrizada por $\theta_0 \in [0, \pi/2]$ derivada acima, na qual todos os pontos são mutuamente não-dominados (pois $f_1, \dots, f_{m-1}$ são estritamente decrescentes em $\theta_0$ enquanto $f_m = \sin(\theta_0)$ é estritamente crescente em $\theta_0$).

---

### 4.3. Derivação Matemática de `DTLZ7` no jMetal 7.8

Na desmontagem de `DTLZ7.evaluate` (`jmetal-problem-7.8.jar`, offsets `82–198`):

1. **Equações do problema**:
   - Os primeiros $m-1$ objetivos são as próprias variáveis de posição:
     $$f_j(\mathbf{x}) = x_{j-1} \in [0, 1], \quad j \in \{1, \dots, m-1\}$$
   - A função de distância é $g(\mathbf{x}_M) = 1 + \frac{9}{k} \sum_{i=m-1}^{n-1} x_i \ge 1$, atingindo o mínimo $g^* = 1$ quando $x_i = 0$ ($\forall i \ge m-1$).
   - O último objetivo $f_m$ na frente candidata ($g^* = 1 \implies 1 + g^* = 2$) é dado por:
     $$f_m(f_1, \dots, f_{m-1}) = 2 \left(m - \sum_{j=1}^{m-1} \psi(f_j)\right), \quad \text{onde} \quad \psi(x) = \frac{x}{2}\left(1 + \sin(3\pi x)\right)$$

2. **Condição de Não-Dominância e os $2^{m-1}$ Componentes Desconexos**:
   - Como $f_m$ depende aditivamente de $-\psi(f_j)$ e queremos **minimizar** simultaneamente $f_j$ e $f_m$, um valor $f_j = x \in [0, 1]$ pertence à frente de Pareto global se, e somente se, **não existir** $x' < x$ em $[0, 1]$ tal que $\psi(x') \ge \psi(x)$, isto é, $x$ deve ser um ponto de recorde estrito (*running maximum*) de $\psi$ em $[0, 1]$:
     $$\forall x' \in [0, x), \quad \psi(x') < \psi(x)$$
   - Derivando $\psi(x)$:
     $$\psi'(x) = \frac{1}{2}\left(1 + \sin(3\pi x) + 3\pi x \cos(3\pi x)\right) = 0 \iff 1 + \sin(3\pi x) + 3\pi x \cos(3\pi x) = 0$$
   - Resolvendo numericamente em dupla precisão (Newton-Raphson com precisão de $10^{-16}$):
     - **Primeiro máximo local** em $[0, 1]$:
       $$x_1^* = 0.2514118360889171, \quad \psi(x_1^*) = 0.2134028324616181$$
       No intervalo $I_0 = [0, x_1^*]$, $\psi'(x) > 0$ para todo $x \in [0, x_1^*)$, logo $\psi$ é estritamente crescente de $\psi(0) = 0$ até $\psi(x_1^*)$.
     - Após $x_1^*$, $\psi(x)$ decresce até o mínimo local $x_2^* = \frac{1}{2}$ (onde $\psi(0.5) = 0$) e volta a crescer, igualando novamente o valor $\psi(x_1^*)$ no ponto de salto $x_{\text{jump}}$:
       $$\psi(x_{\text{jump}}) = \psi(x_1^*) \implies x_{\text{jump}} = 0.6316265307000612$$
     - **Segundo máximo local (e máximo global em $[0, 1]$)**:
       $$x_3^* = 0.8594008566447239, \quad \psi_{\max} = \psi(x_3^*) = 0.8464978172492108$$
       No intervalo $I_1 = [x_{\text{jump}}, x_3^*]$, $\psi(x)$ é estritamente crescente de $\psi(x_{\text{jump}})$ até $\psi(x_3^*)$. Para $x \in (x_3^*, 1]$, $\psi(x) < \psi(x_3^*)$, sendo todos esses pontos dominados por $x_3^*$.
   - Portanto, a região Pareto-ótima para cada coordenada $f_j$ ($j \in \{1, \dots, m-1\}$) é a união disjunta de dois intervalos fechados:
     $$f_j \in I_0 \cup I_1 = [0, \; 0.251411836088917] \;\cup\; [0.631626530700061, \; 0.859400856644724]$$
   - No produto cartesiano $(I_0 \cup I_1)^{m-1}$, isso forma exatamente **$2^{m-1}$ regiões desconexas** no espaço de objetivos!

3. **Intervalo Analítico Exato de $f_m$ para cada $m \in \{2, 3, 5, 8, 10, 15\}$**:
   - O **máximo** de $f_m$ na frente de Pareto ocorre quando $f_1 = \dots = f_{m-1} = 0$ ($\psi(0) = 0$):
     $$f_m^{\max}(m) = 2m$$
   - O **mínimo** de $f_m$ na frente de Pareto ocorre quando $f_1 = \dots = f_{m-1} = x_3^* \approx 0.859400856644724$ ($\psi(x_3^*) \approx 0.846497817249211$):
     $$f_m^{\min}(m) = 2\left(m - (m - 1)\psi(x_3^*)\right) = 2m - 1.6929956344984216 \cdot (m - 1)$$

| $m$ | Componentes Desconexos ($2^{m-1}$) | Domínio de $f_1, \dots, f_{m-1}$ | $f_m^{\min}(m)$ Analítico Exato | $f_m^{\max}(m) = 2m$ |
|---:|---:|---|---:|---:|
| **2** | $2^1 = 2$ | $[0, 0.251412] \cup [0.631627, 0.859401]$ | **$2.307004365501578$** | **$4.0$** |
| **3** | $2^2 = 4$ | $[0, 0.251412] \cup [0.631627, 0.859401]$ | **$2.614008731003157$** | **$6.0$** |
| **5** | $2^4 = 16$ | $[0, 0.251412] \cup [0.631627, 0.859401]$ | **$3.228017462006314$** | **$10.0$** |
| **8** | $2^7 = 128$ | $[0, 0.251412] \cup [0.631627, 0.859401]$ | **$4.149030558511049$** | **$16.0$** |
| **10** | $2^9 = 512$ | $[0, 0.251412] \cup [0.631627, 0.859401]$ | **$4.763039289514206$** | **$20.0$** |
| **15** | $2^{14} = 16384$ | $[0, 0.251412] \cup [0.631627, 0.859401]$ | **$6.298061117022098$** | **$30.0$** |

---

## 5. Modos Opcionais de Geração de Frentes (Desativados por Padrão)

Em conformidade com as regras da Tarefa T07, foram implementados em `ParetoFrontValidator` e integrados em `WFGStudy` dois modos opcionais controlados por propriedades JVM, **ambos desativados (`false`) por padrão**:

### 5.1. Modo Analítico: `-DgenerateAllReferenceFronts=false` (padrão `false`)

- **Quando `false` (padrão)**: `WFGStudy.addToProblemList` mantém inalterado o comportamento parcial atual do estudo (inclui `DTLZ1–DTLZ7` apenas para $m \le 3$, totalizando 68 configurações de problemas).
- **Quando `true`**: Para cada $m > 3$ ($m \in \{5, 8, 10, 15\}$) e cada `DTLZ1–DTLZ7`, `WFGStudy` invoca `ParetoFrontValidator.generateAndSaveAnalyticalDtlzFront(...)`, que:
  1. Gera a frente analítica em `resources/referenceFronts/pf_gen/analytical/DTLZ{p}.{m}D.csv`:
     - `DTLZ1`: simplex de Das & Dennis / Deb & Jain escalado por $0.5$ ($\sum_{j=1}^m f_j = 0.5$);
     - `DTLZ2–DTLZ4`: projeção radial na esfera unitária positiva ($\sum_{j=1}^m f_j^2 = 1$);
     - `DTLZ5–DTLZ6`: amostragem uniforme de $\theta_0 \in [0, \pi/2]$ sobre a curva 1D exata da subvariedade $g = 0$ do jMetal 7.8;
     - `DTLZ7`: amostragem estratificada de baixa discrepância sobre os intervalos Pareto-admissíveis $(I_0 \cup I_1)^{m-1}$, incluindo os extremos analíticos $f_m^{\min}(m)$ e $f_m^{\max}(m) = 2m$.
  2. Executa a validação completa (`validateParetoFrontFile` + `validateAnalyticalDtlzFrontByProblem`, exigindo finitude, dimensionalidade $m$, $0$ duplicatas, $0$ pontos dominados e erro analítico $\le 10^{-5}$) **antes** de habilitar a combinação no estudo.
  3. **Nunca grava nem sobrescreve arquivos em `pareto_fronts/csv/`** (qualquer tentativa de passar `pareto_fronts/csv/` como diretório de saída lança `IllegalArgumentException`).

### 5.2. Modo Empírico: `-DgenerateEmpiricalReferenceFronts=false` (padrão `false`)

- **Quando `false` (padrão)**: Nenhuma frente empírica é gerada.
- **Quando `true`**: `ParetoFrontValidator.generateEmpiricalReferenceFront(...)` consolida soluções não-dominadas e únicas a partir de arquivos `FUN*.csv` de execuções prévias independentes e grava em:
  - `resources/referenceFronts/pf_gen/empirical/{PROBLEM}.{m}D.empirical.csv`
  - `resources/referenceFronts/pf_gen/empirical/{PROBLEM}.{m}D.empirical.csv.meta.txt` (registrando `type=empirical`, problema, número de objetivos, total de candidatos lidos, total de pontos não-dominados retidos, lista de arquivos de origem e metadados de execução).
- **Guarda metodológica obrigatória**: Se houver interseção entre `sourceFunFiles` e `evaluationFrontFiles`, `generateEmpiricalReferenceFront` lança `IllegalArgumentException` impedindo que a mesma execução seja usada simultaneamente para construir e avaliar a frente de referência.

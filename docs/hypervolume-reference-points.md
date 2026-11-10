# Auditoria Científica e Técnica de Hipervolume, Normalização e Pontos de Referência (T08)

## 1. Resumo Executivo

Este documento apresenta a auditoria completa em bytecode (jMetal 7.8) e a fundamentação matemática e empírica da configuração de hipervolume (`HV` e `NHV`), normalização de objetivos e definição dos pontos de referência no projeto `hypeMOEA` (Tarefa `T08`).

### Principais Conclusões
1. **Diferenciação entre HV Bruto e NHV**:
   - **Métrica primária de hipervolume (`HV`)**: `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume` (`name() = "HV"`, `isTheLowerTheIndicatorValueTheBetter() = false`, **maximização**).
   - **Métrica secundária (`NHV`)**: `org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume` (`name() = "NHV"`, `isTheLowerTheIndicatorValueTheBetter() = true`, **minimização**), que calcula $\text{NHV}(A) = 1.0 - \frac{\text{HV}(A)}{\text{HV}(R)}$.
2. **Exatidão dos Algoritmos de Hipervolume no jMetal 7.8**:
   - Todas as classes de hipervolume presentes em `jmetal-core-7.8.jar` (`PISAHypervolume`, `WFGHypervolume`, `Hypervolume3D` e `HypervolumeContribution2D`) implementam **algoritmos determinísticos e exatos** (varredura recursiva por fatias de Zitzler/Fonseca/PISA, inclusão-exclusão de While et al. e varredura plana 3D $O(N \log N)$).
   - O utilitário externo legado `Main-Hipervolume.java` (baseado em aproximação estocástica de Monte Carlo com $10^6$ amostras e semente `System.nanoTime()`) permanece isolado e fora do pipeline oficial.
3. **Dois Regimes de Operação (Descoberta Crítica em Bytecode)**:
   - **Regime 1 — Pipeline Normalizado (`ComputeQualityIndicators`)**: `ComputeQualityIndicators.run()` normaliza a frente de referência $R$ em $[0, 1]^m$ via `NormalizeUtils.normalize(referenceFront)`, invoca `indicator.referenceFront(normalizedReferenceFront)` e normaliza cada frente aproximada `FUN{r}.csv` usando os vetores $\min(R)$ e $\max(R)$. Em `PISAHypervolume`, o método interno `hypervolume(front, referenceFront)` aplica `VectorUtils.getInvertedFront(front)`, que assume coordenadas normalizadas em $[0, 1]^m$ e adota implicitamente o ponto de referência unitário $\mathbf{z}_{\text{norm}} = (1.0, \dots, 1.0) \in \mathbb{R}^m$ (truncando coordenadas $> 1.0$ para $0.0$).
   - **Regime 2 — Espaço Bruto de Objetivos (`StaticParams.getHypervolumeReferencePoint(problemName, m)`)**: No espaço original dos objetivos, pontos uniformes como $(2, \dots, 2)$ para WFG ou $(1, \dots, 1)$ para DTLZ7 **falham** em dominar a frente de Pareto verdadeira, pois os problemas WFG possuem fatores de escala $S_j = 2j$ ($\max f_j^* = 2j$) e o problema DTLZ7 possui $\max f_m^* = 2m$. Por isso, `StaticParams.getHypervolumeReferencePoint(problemName, m)` foi atualizado para definir pontos de referência **por problema e por dimensão $m$**, garantindo dominância estrita ($f_j < z_j$) sobre 100% dos pontos das 68 frentes de referência ativas em `pareto_fronts/csv/`.

---

## 2. Inspeção em Bytecode do jMetal 7.8 (`jmetal-core-7.8.jar` e `jmetal-lab-7.8.jar`)

### 2.1 Inventário Completo de Classes em `org.uma.jmetal.qualityindicator.impl.hypervolume.**`

A inspeção do arquivo `jmetal-core-7.8.jar` com `jar tf` e `javap -c -p` identificou exatamente as seguintes classes no pacote de hipervolume:

| Classe | Tipo / Papel | Algoritmo | Direção (`isTheLower...`) | `name()` |
|---|---|---|---|---|
| `org.uma.jmetal.qualityindicator.impl.hypervolume.Hypervolume` | Superclasse abstrata de indicadores de hipervolume | Define armazenamento de `referenceFront` / `referencePoint` via `ReferencePointUtils` | `false` (maximização) | `"HV"` |
| `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume` | Implementação padrão usada no pipeline (`ComputeQualityIndicators` e `NormalizedHypervolume`) | **Exato**: varredura recursiva por dimensão (*dimension-sweep* de Zitzler, 2001 / Fonseca et al., 2005 / PISA) | `false` (maximização) | `"HV"` |
| `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume` | Implementação alternativa em `jmetal-core-7.8.jar` | **Exato**: algoritmo WFG por inclusão-exclusão e *slicing* (While, Bradstreet, Barone, *IEEE TEVC*, 2012) com casos base otimizados `inclhv`, `inclhv2`, `inclhv3`, `inclhv4`, `hv2`, `exclhv` | `false` (maximização) | `"HV"` |
| `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.Hypervolume3D` | Implementação especializada para $m = 3$ | **Exato**: varredura de plano 3D com lista de delimitadores ($O(N \log N)$) | `false` (maximização) | `"HV3D"` |
| `org.uma.jmetal.qualityindicator.impl.hypervolume.HypervolumeContribution2D` | Hipervolume e contribuição individual para $m = 2$ | **Exato**: ordenação 2D e soma de retângulos adjacentes ($O(N \log N)$) | `false` (maximização) | `"HV"` |

---

### 2.2 Análise de Bytecode: `Hypervolume`, `ReferencePointUtils` e `PISAHypervolume`

#### A. Construtores e Armazenamento do Ponto de Referência (`Hypervolume` e `ReferencePointUtils`)
Na superclasse `org.uma.jmetal.qualityindicator.impl.hypervolume.Hypervolume`:
- `PISAHypervolume()` $\to$ chama `super()`, deixando `referenceFront = null`.
- `PISAHypervolume(double[] referencePoint)` $\to$ chama `super(referencePoint)`, que invoca `ReferencePointUtils.createReferenceFrontFromReferencePoint(referencePoint)`.
  - No bytecode de `ReferencePointUtils.createReferenceFrontFromReferencePoint(double[])`, dado um vetor $\mathbf{z} = (z_1, \dots, z_m)$, é construída uma matriz diagonal $m \times m$:
    $$\text{referenceFront} = \begin{bmatrix} z_1 & 0 & \cdots & 0 \\ 0 & z_2 & \cdots & 0 \\ \vdots & \vdots & \ddots & \vdots \\ 0 & 0 & \cdots & z_m \end{bmatrix}$$
- `PISAHypervolume(double[][] referenceFront)` $\to$ verifica `Check.notNull(referenceFront)` e `Check.that(referenceFront.length > 0, ...)` e armazena a matriz em `this.referenceFront`.
- `getReferencePoint()` $\to$ invoca `ReferencePointUtils.extractReferencePointFromReferenceFront(this.referenceFront)`, que extrai o máximo de cada coluna:
  $$z_j = \max_{i} \text{referenceFront}[i][j]$$
- `referenceFront(double[][] referenceFront)` $\to$ sobrescreve `this.referenceFront` com a matriz fornecida.

#### B. Como `PISAHypervolume.compute(double[][] front)` Calcula o Volume
A desmontagem de `PISAHypervolume` (`javap -c -p`) revela exatamente a implementação de `compute` e `hypervolume`:

```java
public double compute(double[][] front) {
    Check.notNull(front);
    return hypervolume(front, this.referenceFront);
}

private double hypervolume(double[][] front, double[][] referenceFront) {
    double[][] invertedFront = VectorUtils.getInvertedFront(front);
    int numberOfObjectives = referenceFront[0].length;
    return calculateHypervolume(invertedFront, invertedFront.length, numberOfObjectives);
}
```

E a desmontagem de `VectorUtils.getInvertedFront(double[][] front)` mostra:

```java
public static double[][] getInvertedFront(double[][] front) {
    Check.notNull(front);
    int numberOfDimensions = front[0].length;
    double[][] invertedFront = new double[front.length][numberOfDimensions];
    for (int i = 0; i < front.length; i++) {
        for (int j = 0; j < numberOfDimensions; j++) {
            if (front[i][j] <= 1.0 && front[i][j] >= 0.0) {
                invertedFront[i][j] = 1.0 - front[i][j];
            } else if (front[i][j] > 1.0) {
                invertedFront[i][j] = 0.0;
            } else if (front[i][j] < 0.0) {
                invertedFront[i][j] = 1.0;
            }
        }
    }
    return invertedFront;
}
```

**Implicações Técnicas Fundamentais do Bytecode de `PISAHypervolume`**:
1. **`PISAHypervolume` pressupõe frentes normalizadas em $[0, 1]^m$**: O método `PISAHypervolume.hypervolume(front, referenceFront)` usa `referenceFront` **apenas** para ler `numberOfObjectives = referenceFront[0].length`. A inversão `VectorUtils.getInvertedFront(front)` transforma cada coordenada $f_{i,j} \in [0, 1]$ em $1.0 - f_{i,j}$, convertendo o problema de minimização com ponto de referência $(1.0, \dots, 1.0)$ em um problema de maximização em relação à origem $(0.0, \dots, 0.0)$.
2. **Tratamento de soluções fora da caixa de referência em `PISAHypervolume`**:
   - Coordenadas $f_{i,j} > 1.0$ (piores que o ponto de referência normalizado $1.0$) são truncadas para `invertedFront[i][j] = 0.0`. No algoritmo recursivo `calculateHypervolume`, como `surfaceUnchangedTo` e `distance` medem fatias positivas a partir de $0.0$, qualquer ponto com coordenada invertida igual a $0.0$ tem contribuição de volume **nula** naquela direção (ou é filtrado como dominado por `filterNondominatedSet`).
   - Coordenadas $f_{i,j} < 0.0$ (melhores que o limite mínimo da frente de referência) são truncadas para `invertedFront[i][j] = 1.0`.
3. **Contraste com `WFGHypervolume` e `Hypervolume3D`**:
   - Em `WFGHypervolume.hypervolume(double[][] front, double[][] referenceFront)`, o bytecode invoca explicitamente `double[] refPoint = getReferencePoint()`, calcula `Math.max(0.0, refPoint[j] - front[i][j])` e executa o algoritmo exato de inclusão-exclusão no espaço da caixa $[0, z_1] \times \dots \times [0, z_m]$.
   - Em `Hypervolume3D.compute(double[][] front)`, o bytecode também utiliza `this.referencePoint` explicitamente para filtrar pontos que satisfazem `dominates(referencePoint, p)` e medir o volume 3D até `referencePoint`.

---

### 2.3 Análise de Bytecode: `NormalizedHypervolume`

A desmontagem de `org.uma.jmetal.qualityindicator.impl.NormalizedHypervolume` comprova:
- **Campos internos**:
  - `private double referenceFrontHypervolume;`
  - `private Hypervolume hypervolume;` (instância de `PISAHypervolume`)
  - `private double[][] referenceFront;`
- **Construtores**:
  1. `NormalizedHypervolume()`: construtor vazio (para uso com `ComputeQualityIndicators`, que chama `referenceFront(normalizedReferenceFront)` em seguida).
  2. `NormalizedHypervolume(double[] referencePoint)`: instancia `new PISAHypervolume(new double[][]{referencePoint})`. *(Nota de auditoria: neste construtor do jMetal 7.8, `this.referenceFront` permanece `null` até que `referenceFront(double[][])` seja chamado).*
  3. `NormalizedHypervolume(double[][] referenceFront)`: armazena `this.referenceFront = referenceFront` e instancia `this.hypervolume = new PISAHypervolume(referenceFront)`.
- **Método `referenceFront(double[][] referenceFront)`**:
  ```java
  @Override
  public void referenceFront(double[][] referenceFront) {
      super.referenceFront(referenceFront);
      this.referenceFront = referenceFront;
      this.hypervolume = new PISAHypervolume(referenceFront);
  }
  ```
- **Método `compute(double[][] front)`**:
  ```java
  @Override
  public double compute(double[][] front) {
      double hypervolumeValue = this.hypervolume.compute(front);
      this.referenceFrontHypervolume = this.hypervolume.compute(this.referenceFront);
      double result = 1.0 - (hypervolumeValue / this.referenceFrontHypervolume);
      if (result < 0.0) {
          JMetalLogger.logger.info("The normalized hypervolume value is negative: " + result);
      }
      return result;
  }
  ```
- **Propriedades matemáticas de `NHV`**:
  - Fórmula exata:
    $$\text{NHV}(A, R) = 1.0 - \frac{\text{HV}(A)}{\text{HV}(R)}$$
  - Direção: `isTheLowerTheIndicatorValueTheBetter() = true` (**minimização**, quanto menor melhor).
  - Quando $A = R$, $\text{HV}(A) = \text{HV}(R) \implies \text{NHV}(R, R) = 0.0$.
  - Quando nenhuma solução de $A$ domina o ponto de referência ($\text{HV}(A) = 0$), $\text{NHV}(A, R) = 1.0$.
  - Quando a frente aproximada $A$ supera o hipervolume da frente de referência discretizada $R$ ($\text{HV}(A) > \text{HV}(R)$), $\text{NHV}(A, R) < 0.0$ (o valor negativo é retornado sem truncamento e registrado via `JMetalLogger`).

---

### 2.4 Análise de Bytecode: `ComputeQualityIndicators` e `NormalizeUtils`

No pipeline experimental (`org.uma.jmetal.lab.experiment.component.impl.ComputeQualityIndicators.run()`):
1. Para cada problema `problem` e cada indicador configurado em `experiment.getIndicators()`:
2. Lê a frente de referência em `pareto_fronts/csv/<problem.referenceFront()>`:
   ```java
   double[][] referenceFront = VectorUtils.readVectors(referenceFrontFileName, ",");
   ```
3. Normaliza a frente de referência em $[0, 1]^m$:
   ```java
   double[][] normalizedReferenceFront = NormalizeUtils.normalize(referenceFront);
   indicator.referenceFront(normalizedReferenceFront);
   ```
4. Extrai os limites mínimo e máximo de cada coluna da frente de referência original:
   ```java
   double[] minValues = NormalizeUtils.getMinValuesOfTheColumnsOfAMatrix(referenceFront);
   double[] maxValues = NormalizeUtils.getMaxValuesOfTheColumnsOfAMatrix(referenceFront);
   ```
5. Para cada execução independente `r = 0 .. independentRuns - 1`, lê `FUN{r}.csv`, normaliza usando os extremos da frente de referência original e computa o indicador:
   ```java
   double[][] front = VectorUtils.readVectors(solutionFrontFile, ",");
   double[][] normalizedFront = NormalizeUtils.normalize(front, minValues, maxValues);
   Double indicatorValue = indicator.compute(normalizedFront);
   ```

#### Proteção contra Divisão por Zero em `NormalizeUtils`
A inspeção em bytecode de `NormalizeUtils.normalize(double value, double minRangeValue, double maxRangeValue, double min, double max)` mostra:
```java
if (max == min) {
    return (maxRangeValue + minRangeValue) / 2.0; // retorna 0.5 quando [minRange, maxRange] = [0.0, 1.0]
}
return minRangeValue + (((value - min) * (maxRangeValue - minRangeValue)) / (max - min));
```
Portanto:
- **A normalização usa exclusivamente a frente de referência común** (`minValues` e `maxValues` de `referenceFront`), nunca a frente aproximada individual isoladamente — garantindo comparabilidade justa entre todos os algoritmos e execuções.
- **Não ocorre divisão por zero (`NaN` ou `Infinity`)** se uma coluna tiver `max == min`: o jMetal 7.8 retorna o ponto médio `0.5`.

---

## 3. Escalas Reais dos Problemas WFG1–WFG9 e DTLZ1–DTLZ7 no jMetal 7.8

A inspeção em bytecode de `jmetal-problem-7.8.jar` (`org.uma.jmetal.problem.multiobjective.wfg.*` e `org.uma.jmetal.problem.multiobjective.dtlz.*`) e a auditoria numérica das 68 frentes de referência ativas em `pareto_fronts/csv/` confirmam:

### 3.1 Suíte WFG (`WFG1`–`WFG9`)
Nos construtores de `WFG1` a `WFG9`, os fatores de escala $S = (S_1, \dots, S_m)$ são inicializados por:
```java
this.s = new int[this.m];
for (int i = 0; i < this.m; i++) {
    this.s[i] = 2 * (i + 1);
}
```
Na frente de Pareto verdadeira ($x_m = 0 \implies D \cdot x_m = 0$), cada objetivo é dado por:
$$f_j(\mathbf{x}) = S_j \cdot h_j(x_1, \dots, x_{m-1}) = 2j \cdot h_j(x_1, \dots, x_{m-1}), \quad h_j \in [0, 1]$$
Logo, o intervalo teórico da frente de Pareto verdadeira no objetivo $j \in \{1, \dots, m\}$ é:
$$f_j^* \in [0, 2j] \implies \max f_j^* = 2j \quad (2, 4, 6, \dots, 2m)$$

> **Por que $(2, 2, \dots, 2)$ é inválido no espaço bruto para WFG?**
> Para qualquer $j \ge 2$, $\max f_j^* = 2j \ge 4 > 2.0$. Usar $(2, 2, \dots, 2)$ no espaço bruto faz com que quase todas as soluções de Pareto com $f_j > 2.0$ ($j \ge 2$) caiam fora da caixa delimitada pelo ponto de referência, tendo contribuição de hipervolume truncada a zero!
> Além disso, na auditoria numérica dos arquivos reais `pareto_fronts/csv/WFG*.csv`, observam-se dois casos com leve extrapolação acima de $2j$ nas frentes legadas do repositório:
> - `WFG1.5D.csv`: $\max f_5 = 10.137 > 2 \times 5 = 10.0$;
> - `WFG3.2D.csv`: $\max f_1 = 3.0 > 2 \times 1 = 2.0$ (mas $< 3.0$ se $z_1 > 3.0$, exceto quando considerado com margem; ver nota sobre `WFG3.2D.csv` na Seção 4).

### 3.2 Suíte DTLZ (`DTLZ1`–`DTLZ7`)
A inspeção em bytecode de `DTLZ1` a `DTLZ7` em `jmetal-problem-7.8.jar` confirma:
1. **`DTLZ1`**: Na frente de Pareto ($g = 0$), os objetivos satisfazem o hiperplano linear:
   $$\sum_{j=1}^m f_j = 0.5, \quad f_j \in [0, 0.5]$$
2. **`DTLZ2`, `DTLZ3`, `DTLZ4`**: Na frente de Pareto ($g = 0$), os objetivos estão na hiperesfera unitária no primeiro ortante:
   $$\sum_{j=1}^m f_j^2 = 1.0, \quad f_j \in [0, 1.0]$$
3. **`DTLZ5`, `DTLZ6`**: Na frente de Pareto ($g = 0$), a transformação angular $\theta_j = \frac{\pi}{4(1+g)}(1 + 2g x_j)$ fixa $\theta_2 = \dots = \theta_{m-1} = \frac{\pi}{4}$, degenerando a frente em uma curva 1D sobre a hiperesfera $\sum_{j=1}^m f_j^2 = 1.0$, com:
   $$f_1, f_2 \in \left[0, 2^{-\frac{m-2}{2}}\right], \quad f_j \in \left[0, 2^{-\frac{m-j}{2}}\right] \le 1.0 \quad (j < m), \quad f_m \in [0, 1.0]$$
4. **`DTLZ7`**: Na frente de Pareto ($g = 1.0$), os $m-1$ primeiros objetivos são independentes ($f_j = x_j \in [0, 1.0]$ para $j = 1, \dots, m-1$), enquanto o último objetivo é:
   $$f_m(\mathbf{x}) = (1 + g)\left(m - \sum_{j=1}^{m-1} \frac{f_j}{1+g}(1 + \sin(3\pi f_j))\right) = 2m - \sum_{j=1}^{m-1} f_j(1 + \sin(3\pi f_j))$$
   Quando $f_1 = \dots = f_{m-1} = 0$, o último objetivo atinge seu máximo global:
   $$\max f_m^* = 2m$$
   Nos arquivos reais de `pareto_fronts/csv/`:
   - `DTLZ7.2D.csv` ($m=2$): $f_1 \in [0.004326, 0.853225]$, $f_2 \in [2.3085, 3.9955] \subset [0, 2m = 4.0]$.
   - `DTLZ7.3D.csv` ($m=3$): $f_1, f_2 \in [0.0, 0.86]$, $f_3 \in [2.61404, 6.0] \subset [0, 2m = 6.0]$.

> **Por que $(1, 1, \dots, 1)$ é inválido no espaço bruto para DTLZ7?**
> Como $f_m^* \ge 2.3085 > 1.0$ para $m=2$ e $f_3^* \ge 2.61404 > 1.0$ para $m=3$, **100% dos pontos da frente de Pareto verdadeira de DTLZ7** possuem $f_m > 1.0$. Com ponto de referência $(1, \dots, 1)$ no espaço bruto, o hipervolume de qualquer subconjunto da frente verdadeira de DTLZ7 seria identicamente zero!

---

## 4. Tabela Completa de Pontos de Referência por Problema e $m$ (`StaticParams`)

### 4.1 Regime Normalizado do Pipeline (`ComputeQualityIndicators`)
Quando os indicadores `PISAHypervolume` (`HV`) e `NormalizedHypervolume` (`NHV`) são executados dentro de `ComputeQualityIndicators.run()`, todas as frentes são normalizadas linearmente pelos extremos da frente de referência $R$ em $[0, 1]^m$. Nesse regime, o ponto de referência efetivo utilizado por `PISAHypervolume` (`VectorUtils.getInvertedFront`) é:
$$\mathbf{z}_{\text{norm}} = (1.0, 1.0, \dots, 1.0) \in \mathbb{R}^m$$
acessível programaticamente via `StaticParams.getNormalizedHypervolumeReferencePoint(m)`.

### 4.2 Regime no Espaço Bruto de Objetivos (`StaticParams.getHypervolumeReferencePoint(problemName, m)`)
Para cálculos no espaço original dos objetivos e validação de dominância estrita ($f_j < z_j$), `StaticParams.getHypervolumeReferencePoint(problemName, m)` define:

| Problema(s) | Intervalo Teórico na Frente de Pareto ($j = 1, \dots, m$) | Limite Superior Teórico (`getParetoFrontUpperBounds`) | Ponto de Referência no Espaço Bruto (`getHypervolumeReferencePoint`) | Exemplo para $m = 2$ | Exemplo para $m = 3$ | Exemplo para $m = 5$ |
|---|---|---|---|---|---|---|
| `WFG1`–`WFG9` (regra geral) | $f_j \in [0, 2j]$ | $(2, 4, 6, \dots, 2m)$ | $z_j = 2.2 \times j = 1.1 \times S_j$ | $(2.2, 4.4)$ | $(2.2, 4.4, 6.6)$ | $(2.2, 4.4, 6.6, 8.8, 11.0)$ |
| `WFG3` ($m = 2$, ajuste para `WFG3.2D.csv`) | $f_1 \in [0, 2]$, $f_2 \in [0, 4]$ (código jMetal) / $f_1 \in [0, 3]$, $f_2 \in [-2, 4]$ (`WFG3.2D.csv`) | $(2.0, 4.0)$ | $(3.1, 4.4)$ (garante $f_1 < 3.1$ e $f_2 < 4.4$ sobre todos os 916 pontos de `WFG3.2D.csv`) | $(3.1, 4.4)$ | $(2.2, 4.4, 6.6)$ | $(2.2, 4.4, 6.6, 8.8, 11.0)$ |
| `DTLZ1` | $f_j \in [0, 0.5]$, $\sum f_j = 0.5$ | $(0.5, 0.5, \dots, 0.5)$ | $(0.6, 0.6, \dots, 0.6)$ | $(0.6, 0.6)$ | $(0.6, 0.6, 0.6)$ | $(0.6, 0.6, 0.6, 0.6, 0.6)$ |
| `DTLZ2`–`DTLZ4` | $f_j \in [0, 1.0]$, $\sum f_j^2 = 1.0$ | $(1.0, 1.0, \dots, 1.0)$ | $(1.1, 1.1, \dots, 1.1)$ | $(1.1, 1.1)$ | $(1.1, 1.1, 1.1)$ | $(1.1, 1.1, 1.1, 1.1, 1.1)$ |
| `DTLZ5`–`DTLZ6` | $f_j \in [0, 2^{-(m-1-\max(j-1,1))/2}]$ ($j < m$), $f_m \in [0, 1.0]$ | $(1.0, 1.0, \dots, 1.0)$ | $(1.1, 1.1, \dots, 1.1)$ | $(1.1, 1.1)$ | $(1.1, 1.1, 1.1)$ | $(1.1, 1.1, 1.1, 1.1, 1.1)$ |
| `DTLZ7` | $f_j \in [0, 1.0]$ ($j < m$), $f_m \in [f_{m,\min}, 2m]$ | $(1.0, \dots, 1.0, 2.0m)$ | $(1.1, \dots, 1.1, 2.1m)$ | $(1.1, 4.2)$ | $(1.1, 1.1, 6.3)$ | $(1.1, 1.1, 1.1, 1.1, 10.5)$ |

### 4.3 Verificação Empírica nos 68 Arquivos de `pareto_fronts/csv/`
A inspeção exaustiva das 68 frentes de referência ativas no repositório (`54` frentes `WFG1..9.{2,3,5,8,10,15}D.csv` + `14` frentes `DTLZ1..7.{2,3}D.csv`) comprovou que **100% dos pontos de todas as 68 frentes** são estritamente dominados ($f_j < z_j$ para todo $j \in \{1, \dots, m\}$) pelos pontos de referência retornados por `StaticParams.getHypervolumeReferencePoint(problemName, m)`:
- Em `WFG1.5D.csv`, $\max f_5 = 10.13707 < z_5 = 11.0$.
- Na frente legada `WFG3.2D.csv` ($N = 916$ pontos), a parametrização estende a reta degenerada $f_2 = 4 - 2 f_1$ até $f_1 = 3.0$ ($f_2 = -2.0$). Com $z_1 = 3.1$ e $z_2 = 4.4$ para `WFG3` em $m = 2$, 100% dos 916 pontos de `WFG3.2D.csv` satisfazem $f_1 < z_1$ e $f_2 < z_2$.

---

## 5. Métodos Exatos vs. Aproximados para Cálculo de Hipervolume ($m \in \{2, 3, 5, 8, 10, 15\}$)

1. **Algoritmos Exatos disponíveis no jMetal 7.8**:
   - `PISAHypervolume` (varredura recursiva de Zitzler / Fonseca): exato para qualquer $m$, utilizado por padrão no pipeline `ComputeQualityIndicators` e internamente por `NormalizedHypervolume`.
   - `WFGHypervolume` (While et al., 2012): exato para qualquer $m$, com otimizações específicas para subproblemas de 1 a 4 objetivos (`inclhv`..`inclhv4`) e exclusão recursiva (`exclhv`).
   - `Hypervolume3D`: exato para $m = 3$ com complexidade $O(N \log N)$.
   - `HypervolumeContribution2D`: exato para $m = 2$ com complexidade $O(N \log N)$.
2. **Viabilidade Computacional em Alta Dimensão ($m \in \{8, 10, 15\}$)**:
   - Como a complexidade de pior caso do hipervolume exato cresce exponencialmente com $m$, a viabilidade em $m \in \{8, 10, 15\}$ depende diretamente da cardinalidade $N$ da frente avaliada.
   - No `hypeMOEA`, os tamanhos de população para $m \in \{8, 10, 15\}$ são moderados ($N = 156$ para $m=8$, $N = 275$ para $m=10$, $N = 135$ para $m=15$), e as frentes de referência correspondentes possuem exatamente essas mesmas cardinalidades ($156$, $275$ e $135$ pontos).
   - Caso em execuções futuras de alta dimensão ($m \ge 10$) o tempo de cálculo exato de `PISAHypervolume` sobre frentes densas se torne proibitivo, o cálculo exato não deve ser substituído silenciosamente por aproximações não-determinísticas sem semente fixa. Qualquer método aproximado (como estimação de Monte Carlo ou projeção R2/HypE com semente fixa controlada por `JMetalRandom`) exigiria justificativa explícita, semente determinística e registro de variância do estimador — o que **não** é necessário para os testes de validação atuais, nos quais `PISAHypervolume` é exato e determinístico.

---

## 6. Resumo das Alterações Realizadas em T08

1. **`src/main/java/br/usp/lti/newmoea/study/StaticParams.java`**:
   - Adicionado `normalizeProblemId(String problemName)` com validação estrita por expressão regular para `WFG1`–`WFG9` e `DTLZ1`–`DTLZ7` (suportando sufixos como `_2`, `.3D.csv` e rejeitando problemas inexistentes como `WFG0`, `WFG10`, `DTLZ0`, `DTLZ8`).
   - Adicionado `getParetoFrontUpperBounds(String problemName, int m)` retornando os limites superiores teóricos exatos de cada problema no espaço bruto: $(2, 4, \dots, 2m)$ para `WFG1`–`WFG9`, $(0.5, \dots, 0.5)$ para `DTLZ1`, $(1.0, \dots, 1.0)$ para `DTLZ2`–`DTLZ6` e $(1.0, \dots, 1.0, 2m)$ para `DTLZ7`.
   - Atualizado `getHypervolumeReferencePoint(String problemName, int m)` para retornar pontos de referência cientificamente justificados por problema e dimensão $m$.
   - Adicionado `getNormalizedHypervolumeReferencePoint(int m)` retornando $(1.0, \dots, 1.0) \in \mathbb{R}^m$ para o regime normalizado do pipeline `ComputeQualityIndicators`.
2. **`src/test/java/br/usp/lti/newmoea/qualityindicator/QualityIndicatorUnitTest.java`**:
   - Atualizado para refletir os pontos de referência por problema de `T08`.
3. **`src/test/java/br/usp/lti/newmoea/qualityindicator/HypervolumeAuditTest.java`**:
   - Novo conjunto de testes automatizados validando:
     - Dimensão, finitude e limites teóricos para `WFG1`–`WFG9` e `DTLZ1`–`DTLZ7` em $m \in \{2, 3, 5, 8, 10, 15\}$;
     - Rejeição de nomes inválidos e $m < 2$;
     - Dominância estrita sobre todas as frentes ativas em `pareto_fronts/csv/`;
     - Demonstração empírica da falha de $(2, \dots, 2)$ em WFG e $(1, \dots, 1)$ em DTLZ7 no espaço bruto;
     - Exatidão analítica de `PISAHypervolume`, `WFGHypervolume`, `Hypervolume3D` e `HypervolumeContribution2D`;
     - Fórmula e comportamento de `NormalizedHypervolume` (incluindo valores negativos quando $\text{HV}(A) > \text{HV}(R)$);
     - Comportamento de `NormalizeUtils` (incluindo proteção contra divisão por zero quando `min == max`).

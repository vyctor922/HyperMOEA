# Análise Arquitetural e Diagnóstico das Flags de Substituição de Zero (`-DreplaceZeroWeights` e `-DreplaceZeroFronts`)

**Projeto:** hypeMOEA  
**Data:** 16 de Novembro de 2026  
**Documento:** `docs/auditoria/analise_flags_lowzero.md`  
**Escopo:** Parametrização granular das flags JVM, isolamento entre `weights_gen/` e `weights_lowzero_gen/` (e `pf_gen/` vs. `pf_lowzero_gen/`), e análise numérica por algoritmo em bytecode do jMetal 7.8 e implementações locais.

---

## 1. Resumo Executivo e Veredito por Algoritmo

| Algoritmo / Componente | Origem no Projeto | Exige `-DreplaceZeroWeights`? | Exige `-DreplaceZeroFronts`? | Justificativa Numérica e em Bytecode |
| :--- | :--- | :---: | :---: | :--- |
| **MOEA/DD** (`PBI` em `WFGStudy`) | `jmetal-algorithm-7.8` (`MOEADD`, `AbstractMOEAD`) | **NÃO** | **NÃO** | Usa `FunctionType.PBI`, onde as divisões em `fitnessFunction`, `calculateDistance` e `calculateDistance2` utilizam exclusivamente a norma euclidiana $\|\lambda\|_2 > 0$, nunca coordenadas individuais $\lambda_j$. |
| **MOEA/DD** (`TCHE` do jMetal 7.8) | `jmetal-algorithm-7.8` (`AbstractMOEAD.fitnessFunction`) | **NÃO** | **NÃO** | Implementa Tchebycheff Padrão Multiplicativo (`diff * lambda[n]`), com guarda explícita no bytecode (`offset 63..76`): quando `lambda[n] == 0.0`, substitui por `1.0E-4 * diff`. |
| **MOEA/DD** (eventual variante *Tchebycheff Invertido* sem guarda) | Variantes externas não presentes no jMetal 7.8 | **SIM** *(apenas se usada variante invertida)* | **NÃO** | A formulação invertida $\max_j |f_j - z_j^*|/\lambda_j$ divide diretamente por $\lambda_j$; se $\lambda_j = 0.0$ sem guarda, gera `Infinity` ou `NaN` (`0/0`). |
| **FDEA** | `br.usp.lti.newmoea.algorithm.fdea.FDEA` | **NÃO** | **NÃO** | Não lê arquivos `.dat` de `weightVectorFiles/`. Possui pisos internos explícitos (`denom = 1e-12` na normalização, `normDistance = 1e-3` / `euclidDist = 1e-3` em `mapping`, `Math.max(localWeights[i], 1e-6)` em `computeLocalWeightedSum`). |
| **RVEA** | `jmetal-component-7.8` (`RVEAEnvironmentalSelection`, `RVEAGeometry`) | **NÃO** | **NÃO** | Gera vetores de referência via `ReferencePointGenerator` em memória. `RVEAGeometry.angle` divide pelas normas $\|u\|_2$ e $\|v\|_2$ (retornando `0.0` se qualquer norma for nula); `RVEAGeometry.gamma` aplica piso `Math.max(1e-12, angle)` antes da divisão no APD. |
| **NSGA-III** | `jmetal-algorithm-7.8` (`NSGAIII`, `EnvironmentalSelection`) | **NÃO** | **NÃO** | Não lê arquivos `.dat` de pesos. Em `EnvironmentalSelection.ASF`, usa piso explícito `1.0E-6` (`offset 36`) para coordenadas zero; `point2LineDistance` divide pelo produto interno/norma vetorial. |
| **AGE-MOEA / AGE-MOEA-II** | `jmetal-algorithm-7.8` (`AGEMOEA`, `AGEMOEAII`) | **NÃO** | **NÃO** | Não utilizam arquivos de pesos nem pontos de referência fixos; estimam a geometria $L_p$ adaptativamente com normalização por extremos. |
| **WASFGA / GWASFGA** *(legado)* | `jmetal-algorithm-7.8` (`WASFGA`, `WeightVectors.invert`) | **SIM** | **NÃO** | **Único algoritmo com falha real (`NaN`):** `WeightVectors.invert` calcula `1.0 / w[i][j]`, produzindo `1.0 / 0.0 = Infinity` e posteriormente `Infinity / Infinity = NaN`, contaminando todo o ranking. |
| **Indicadores** (`HV`, `NHV`, `IGD`, `IGD+`, `R2`) | `jmetal-core-7.8` / `ComputeQualityIndicators` | N/A | **NÃO** | `NormalizeUtils.normalize` divide por `max_j - min_j` (com retorno `0.5` se `max_j == min_j`). `PISAHypervolume` mapeia `0.0 -> 1.0` na inversão; `IGD`/`IGD+` usam distâncias sem divisão por coordenadas; `R2` usa guarda `(weight > 0.0) ? weight : 0.01`. |

---

## 2. Avaliação Específica: MOEA/DD e Tchebycheff

A inspeção em bytecode (`javap -c -p`) de `org.uma.jmetal.algorithm.multiobjective.moead.AbstractMOEAD` e `org.uma.jmetal.algorithm.multiobjective.moead.MOEADD` no `jmetal-algorithm-7.8.jar` comprova três casos distintos:

### 2.1. Tchebycheff Padrão Multiplicativo (`AbstractMOEAD.FunctionType.TCHE`)
No jMetal 7.8 (`AbstractMOEAD.fitnessFunction`, offsets `0..109`):
$$g^{\text{te}}(x \mid \lambda, z^*) = \max_{1 \le j \le m} \tilde{\lambda}_j \cdot |f_j(x) - z_j^*|, \quad \tilde{\lambda}_j = \begin{cases} 10^{-4}, & \text{se } \lambda_j = 0.0, \\ \lambda_j, & \text{caso contrário.} \end{cases}$$
- **Comportamento:** Multiplicativo (sem divisão por $\lambda_j$) e já protegido no bytecode (`dconst_0; dcmpl; ifne; ldc2_w 1.0E-4d`).
- **Necessidade das flags:** **Não precisa** de `-DreplaceZeroWeights`. Além disso, em $m=2, N \le 300$, `AbstractMOEAD.initializeUniformWeight()` (`offsets 0..72`) gera pesos em memória com `0.0` exato nos extremos, ignorando o arquivo em disco.

### 2.2. Tchebycheff Invertido vs. PBI (Penalidade Baseada em Fronteira)
- **Tchebycheff Invertido** ($g^{\text{te-inv}}(x \mid \lambda, z^*) = \max_{1 \le j \le m} \frac{|f_j(x) - z_j^*|}{\lambda_j}$): **não** está implementado em `AbstractMOEAD.FunctionType` do jMetal 7.8 (que possui apenas `TCHE`, `AGG` e `PBI`). Caso uma extensão externa utilize Tchebycheff Invertido sem guarda, ocorrerá divisão por zero (`Infinity` ou `0.0 / 0.0 = NaN`) quando $\lambda_j = 0.0$; nesse cenário hipotético, `-DreplaceZeroWeights=MOEADD` seria obrigatório.
- **PBI (`AbstractMOEAD.FunctionType.PBI`, ativo em `WFGStudy.buildMOEADD`):**
  - Calcula $d_1 = \frac{|(f(x) - z^*)^\top \lambda|}{\|\lambda\|_2}$ e $d_2 = \|(f(x) - z^*) - d_1 \frac{\lambda}{\|\lambda\|_2}\|_2$, retornando $d_1 + 5.0 \, d_2$ (`offsets 176..367`).
  - Todas as divisões utilizam $\|\lambda\|_2 > 0$. Zeros em coordenadas individuais $\lambda_j = 0.0$ correspondem legitimamente aos subproblemas de fronteira/eixo no simplex unitário.

### 2.3. Alocação por Ângulo / Distância Ortogonal (`MOEADD.calculateDistance` e `calculateDistance2`)
- Tanto `MOEADD.calculateDistance` quanto `MOEADD.calculateDistance2` normalizam o vetor de peso dividindo exclusivamente por `norm_vector(lambda)` ($\|\lambda\|_2$), sem divisão por componente $\lambda_j$ e sem função trigonométrica inversa sensível a coordenadas zero.
- **Conclusão para MOEA/DD:** Na configuração oficial do `hypeMOEA` (`PBI` em jMetal 7.8), o `MOEA/DD` funciona exatamente com os pesos puros (`resources/weightVectorFiles/` e `weights_gen/` preservando `0.0`). Alterar `0.0 -> 1e-5` introduziria um viés artificial de ordem $O(10^{-5})$ nos pesos extremos sem benefício numérico para `PBI` ou `TCHE`.

---

## 3. Análise Numérica dos Demais Algoritmos do Estudo

1. **FDEA (`FDEA.java`):**
   - Não consome arquivos de pesos `.dat`.
   - Já possui pisos internos para evitar divisões por zero durante normalização e decomposição:
     - `znadir[j] = Math.max(maxVal, zideal[j] + 1e-6)` no fallback de Nadir;
     - `denom = 1e-12` quando $|z_j^{\text{nadir}} - z_j^{\text{ideal}}| < 10^{-12}$ em `normalizeObjectives` e `asfFunction`;
     - `normDistance = 1e-3` / `euclidDist = 1e-3` quando $< 10^{-12}$ em `mapping`;
     - `double w = Math.max(localWeights[i], 1e-6)` em `computeLocalWeightedSum`.
2. **RVEA (`RVEAGeometry` / `RVEAEnvironmentalSelection`):**
   - No cálculo de ângulos `RVEAGeometry.angle(u, v)`, normaliza pela norma Euclidiana (`Math.hypot` iterativo) e retorna `0.0` caso qualquer vetor tenha norma nula (`offsets 11..25`), aplicando `Math.max(-1.0, Math.min(1.0, dot))` antes de `Math.acos`.
   - No cálculo dos menores ângulos entre vetores de referência `RVEAGeometry.gamma(V)`, aplica piso explícito `Math.max(1.0E-12, angle)` (`offsets 32..45`) antes de dividir no APD (`selectByApd`).
3. **NSGA-III (`EnvironmentalSelection`):**
   - Na função `ASF(S, j)`, usa explicitamente `1.0E-6` (`offset 36`) para coordenadas fora do eixo principal.
   - A distância perpendicular `point2LineDistance` divide pelo produto escalar / norma da direção de referência.
4. **GWASFGA / WASFGA (legado):**
   - `WeightVectors.invert` divide `1.0 / w[i][j]` diretamente sem piso, gerando `NaN` quando qualquer coordenada é `0.0`. Quando ativado via `-Dstudy.includeLegacyAlgorithms=true`, requer `-DreplaceZeroWeights=GWASFGA` (ou `true`).

---

## 4. Especificação das Alterações Implementadas

*(Nota arquitetural: a antiga classe `ArtifactResolver` já havia sido consolidada e removida na Tarefa T31; suas responsabilidades residem em `ReferenceSetValidator` e `ParetoFrontValidator`.)*

### 4.1. Parametrização Granular das Flags JVM
Implementada de forma centralizada em `ReferenceSetValidator.isScopingPropertyEnabledForAlgorithm(propertyName, algorithmName)` e reutilizada por `ParetoFrontValidator`:
- **Padrão (`false`):** ausente, vazia, `"false"` ou `"none"` $\implies$ desativada.
- **Global (`"true"`):** ativa substituição para todos os algoritmos.
- **Granular (lista separada por vírgulas):** ex. `-DreplaceZeroWeights=MOEADD,GWASFGA` ou `-DreplaceZeroFronts=FDEA,AGE-MOEA-II`. Normaliza delimitadores (`/`, `-`, `_`, espaços) e caixa (`"MOEA/DD"` equivale a `"MOEADD"`, `"AGE2"` equivale a `"AGEMOEAII"`). Consultas sem algoritmo (`0-arg`) não são ativadas por listas restritas.

### 4.2. Pureza Analítica de `weights_gen/` e Isolamento em `weights_lowzero_gen/`
1. **`resources/weightVectorFiles/weights_gen/`:**
   - Reservado **exclusivamente** para vetores puramente analíticos gerados por `ReferenceSetValidator.generateCanonicalTwoLayers` (preservando `0.0` exato e soma unitária com tolerância `1e-6`).
   - `ReferenceSetValidator.replaceZeroWeights(...)` bloqueia explicitamente (`IllegalArgumentException`) qualquer tentativa de gravação de variantes com substituição de zero dentro de `weights_gen/`.
   - `resolveCanonicalWeightFile` valida candidatos em `weights_gen/` com tolerância estrita `1e-6` e exige presença de `0.0` (`!report.allStrictlyPositive()`), regenerando automaticamente em formato puro caso haja artefato legado contaminado.
2. **`resources/weightVectorFiles/weights_lowzero_gen/`:**
   - Diretório exclusivo (`ReferenceSetValidator.DEFAULT_LOWZERO_WEIGHTS_DIR`) usado e populado **somente** quando `-DreplaceZeroWeights` está ativo globalmente ou para o algoritmo consultado (`GWASFGA`, `MOEADD`, etc.).
   - Análogo direto de `resources/referenceFronts/pf_lowzero_gen/` (que também protege `pf_gen/` contra gravação de frentes com zeros substituídos).

### 4.3. Consumidores Atualizados
- **`ReferenceSetValidator.java`:** parsing granular de flags, `DEFAULT_LOWZERO_WEIGHTS_DIR`, proteção de `weights_gen/`, pureza analítica em `resolveCanonicalWeightFile` (sobrecargas com `algorithmName` e `boolean useLowZero`) e manifesto atualizado.
- **`ParetoFrontValidator.java`:** delegação granular em `isReplaceZeroFrontsEnabled(algorithmName)`, proteção de `pf_gen/` em `assertValidLowZeroFrontOutputDir`, e sobrecargas com `algorithmName`.
- **`WFGStudy.java`:** `buildMOEADD` e `EvaluationBudgetProblem` consultam escopo `"MOEADD"`; `buidGWASFGA` consulta escopo `"GWASFGA"`; `runPreflightValidation` valida `"MOEADD"` (e `"GWASFGA"` se algoritmos legados estiverem ativos); `resolveReferenceFrontForStudy` ganha sobrecarga com `algorithmName`. *(Nota: no `ExperimentBuilder` do jMetal 7.8, um `ExperimentProblem` vincula uma única frente de referência compartilhada por problema; portanto, `WFGStudy.main()` utiliza o escopo global `-DreplaceZeroFronts=true` para frentes, enquanto `PerformanceProfiler` e consultas diretas aplicam o escopo por algoritmo).*
- **`PerformanceProfiler.java`:** `profileSingleConfigurationRun` resolve frentes de referência usando o escopo de `algorithmName`.

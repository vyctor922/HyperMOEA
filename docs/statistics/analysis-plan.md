# Plano Metodológico de Inferência Estatística Não-Paramétrica (T21)

- **Tarefa:** `T21` (`tasks/task21.md`) — Inferência estatística, Kruskal–Wallis e pós-testes
- **Responsável:** Agente I (*Statistics Auditor* / Coordenador)
- **Data:** 2026-11-09
- **Escopo:** Padronização da unidade de análise experimental, controle de erro por família de hipóteses (FWER), tratamento rigoroso de empates e tamanhos de efeito omnibus e par-a-par.

---

## 1. Unidade Experimental e Independência Estatística

### 1.1. Definição da Unidade de Análise
A unidade experimental elementar é a **execução estocástica independente de um algoritmo** sobre uma configuração fixa de problema e dimensionalidade $(problema, m)$:
- Cada célula experimental consiste na comparação entre os $k$ algoritmos avaliados (para a campanha principal da Fase 3: $k = 6$ algoritmos: NSGA-III, MOEA/DD, FDEA, RVEA, AGE-MOEA, AGE-MOEA-II).
- O número padrão de replicações independentes por algoritmo é $n_g = 30$, totalizando $N = k \times 30 = 180$ observações por célula $(problema, m, indicador)$.
- **Independência Estocástica:** As sementes pseudo-aleatórias de cada execução são geradas deterministicamente pelo gerador SplitMix64 auditado na T15 (`IsolatedJobExecutor`), assegurando que não haja sobreposição de sequências do gerador congruencial linear do jMetal.

### 1.2. Rejeição de Suposições Paramétricas e Pseudo-Replicação
- A distribuição dos indicadores de qualidade multiobjetivo ($HV$, $NHV$, $IGD+$, $R2$) em benchmarks como WFG e DTLZ viola frequentemente as premissas de normalidade (Gaussiana) e homocedasticidade (variâncias homogêneas), apresentando assimetria severa, multimodalidade e agrupamento em fronteiras de ótimo.
- Testes paramétricos baseados em médias (ANOVA, teste t de Student) são **inadequados e metodologicamente desaconselhados**.
- Métricas derivadas ao longo das gerações de uma mesma execução ou reavaliações do mesmo conjunto de soluções **não são observações independentes** e não podem ser agregadas para inflar artificialmente os graus de liberdade (pseudo-replicação).

---

## 2. Teste Omnibus de Kruskal-Wallis com Correção para Empates

O teste não-paramétrico de Kruskal-Wallis (1952) é utilizado como teste omnibus global para verificar a hipótese nula $H_0$ de que as distribuições populacionais das $k$ amostras de algoritmos são idênticas.

### 2.1. Atribuição de Postos e Midranks
Todas as $N = \sum_{g=1}^k n_g$ observações são reunidas em um único vetor ordenado de forma ascendente.
Em caso de valores idênticos (empates), cada observação do grupo de empates recebe a **média aritmética dos postos** (midrank).
Por exemplo, se duas observações empatam nas posições 3 e 4, ambas recebem o posto $(3 + 4)/2 = 3.5$.

### 2.2. Estatística $H_0$ Não-Corrigida
Seja $R_g$ a soma dos postos atribuídos às $n_g$ observações do grupo $g$:
$$H_0 = \frac{12}{N(N+1)} \sum_{g=1}^k \frac{R_g^2}{n_g} - 3(N+1)$$

### 2.3. Fator de Correção de Empates ($C$)
Seja $t$ o tamanho de cada bloco de observações empatadas. O fator de correção $C$ é dado por:
$$C = 1 - \frac{\sum_t (t^3 - t)}{N^3 - N}$$

A estatística corrigida de Kruskal-Wallis é:
$$H = \frac{H_0}{C}$$

### 2.4. Tratamento do Caso Degenerado ($C = 0$)
Quando todas as observações de todos os algoritmos são identicamente iguais (ex.: problemas onde todos os algoritmos falham em encontrar soluções válidas ou atingem exatamente o mesmo valor degenerado), tem-se $t = N$, implicando $C = 0$.
A implementação implementa a proteção contra divisão por zero e NaN:
$$\text{Se } C \le 10^{-15} \implies H = 0.0, \quad p\text{-valor} = 1.0$$

### 2.5. Distribuição Amostral e Graus de Liberdade
Sob $H_0$ e para tamanhos amostrais usuais ($n_g \ge 5$), $H$ converge assintoticamente para uma distribuição Qui-Quadrado com $df = k - 1$ graus de liberdade:
$$p\text{-valor} = 1 - F_{\chi^2_{k-1}}(H)$$
A função de distribuição acumulada $F_{\chi^2}$ é calculada em Java via função Gama incompleta regularizada $P((k-1)/2, H/2)$ com precisão de máquina ($< 10^{-15}$).

### 2.6. Tamanho de Efeito Omnibus ($\varepsilon^2$)
O tamanho de efeito não-paramétrico epsilon-quadrado ($\varepsilon^2$), padronizado por Kelley (1935) e Tomczak & Tomczak (2014), quantifica a proporção da variabilidade dos postos explicada pelos algoritmos:
$$\varepsilon^2 = \frac{H}{N - 1}$$
Classificação de magnitude:
- $\varepsilon^2 < 0.01$: desprezível;
- $0.01 \le \varepsilon^2 < 0.06$: pequeno;
- $0.06 \le \varepsilon^2 < 0.14$: moderado;
- $\varepsilon^2 \ge 0.14$: grande.

---

## 3. Pós-Teste de Dunn com Controle de FWER (Dunn-Holm)

Quando o teste omnibus de Kruskal-Wallis rejeita a hipótese nula ao nível $\alpha = 0.05$ ($p < 0.05$), realizam-se comparações par-a-par para identificar precisamente quais pares de algoritmos diferem estatisticamente.

### 3.1. Estatística $z$ de Dunn
Para o par de grupos $(i, j)$:
$$z_{ij} = \frac{\bar{R}_i - \bar{R}_j}{\sigma_{ij}}$$
onde $\bar{R}_i = R_i / n_i$ é o posto médio do grupo $i$, e o erro padrão comum com correção para empates é:
$$\sigma_{ij} = \sqrt{\left(\frac{N(N+1)}{12} - \frac{\sum_t (t^3 - t)}{12(N-1)}\right) \left(\frac{1}{n_i} + \frac{1}{n_j}\right)}$$

O $p$-valor bicaudal não ajustado é obtido via distribuição Normal padrão $\Phi(z)$:
$$p_{ij}^{\text{raw}} = 2 \cdot (1 - \Phi(|z_{ij}|))$$

### 3.2. Controle de FWER via Procedimento Sequencial de Holm
Para $k = 6$ algoritmos, existem $M = \binom{6}{2} = 15$ comparações par-a-par por célula.
Ajustes ingênuos sem controle de taxa de erro familiar (FWER) inflacionam drasticamente a probabilidade de falsos positivos (erro Tipo I: $1 - (1 - 0.05)^{15} \approx 53.7\%$).

Utiliza-se o procedimento *step-down* de Holm (1979), que é estritamente mais poderoso que a correção conservadora de Bonferroni mantendo controle exato de FWER:
1. Ordenam-se os $M$ $p$-valores brutos em ordem crescente: $p_{(1)}^{\text{raw}} \le p_{(2)}^{\text{raw}} \le \dots \le p_{(M)}^{\text{raw}}$.
2. Para cada rank $m \in \{1, \dots, M\}$:
   $$p_{(m)}^{\text{adj}} = \min\left(1.0, \, \max_{r \le m} \left( (M - r + 1) \cdot p_{(r)}^{\text{raw}} \right) \right)$$
3. O par $(i, j)$ é considerado estatisticamente significativo se $p_{(m)}^{\text{adj}} < \alpha$ (com $\alpha = 0.05$).

### 3.3. Tamanho de Efeito Par-a-Par: Vargha-Delaney $\hat{A}_{12}$ e Cliff's Delta ($\delta$)
Para cada par de algoritmos $(A, B)$, calcula-se a estatística de dominância probabilística $\hat{A}_{12}$ (Vargha & Delaney, 2000):
$$\hat{A}_{12} = \frac{1}{n_A n_B} \sum_{a=1}^{n_A} \sum_{b=1}^{n_B} \left( \mathbb{I}(X_{Aa} > X_{Bb}) + 0.5 \cdot \mathbb{I}(X_{Aa} = X_{Bb}) \right)$$
O Cliff's Delta correspondente é $\delta = 2\hat{A}_{12} - 1 \in [-1, 1]$.
- $\hat{A}_{12} = 0.5$ ($\delta = 0$): nenhuma diferença estocástica entre os algoritmos.
- $\hat{A}_{12} > 0.5$: probabilidade de o algoritmo $A$ superar o algoritmo $B$.
- Magnitudes de Cliff's Delta: $|\delta| < 0.147$ (desprezível), $0.147 \le |\delta| < 0.330$ (pequeno), $0.330 \le |\delta| < 0.474$ (médio), $|\delta| \ge 0.474$ (grande).

---

## 4. Teorema de Invariância de Postos entre HV e NHV

Conforme demonstrado analiticamente na T17 e validado empiricamente na T21 via `testNhvTransformationInvariance`:

**Teorema:** Seja $c > 0$ uma constante de escala estrita comum. A transformação afim decrescente:
$$NHV = 1.0 - \frac{HV}{c}$$
inverte perfeitamente a ordem de todos os elementos sem alterar a estrutura de empates. Consequentemente:
1. $H_{\text{raw}}(HV) \equiv H_{\text{norm}}(NHV)$.
2. $p\text{-valor}(HV) \equiv p\text{-valor}(NHV)$.
3. $\varepsilon^2(HV) \equiv \varepsilon^2(NHV)$.
4. Para o pós-teste de Dunn: $z_{ij}(NHV) = -z_{ij}(HV)$, e $p_{ij}^{\text{Holm}}(HV) \equiv p_{ij}^{\text{Holm}}(NHV)$.

**Conclusão Metodológica:** A comparação não-paramétrica entre algoritmos é 100% invariante entre $HV$ bruto e $NHV$ normalizado quando a normalização utiliza um ponto de referência comum constante.

---

## 5. Artefatos de Execução e Validação

1. **Java Engine:**
   - `src/main/java/br/usp/lti/newmoea/statistics/KruskalWallisTest.java`
   - `src/main/java/br/usp/lti/newmoea/statistics/DunnPostHocTest.java`
2. **Suite de Testes Unitários:**
   - `src/test/java/br/usp/lti/newmoea/statistics/KruskalWallisTestTest.java` (5 testes passando com 100% de sucesso contra oráculos analíticos e empíricos).
3. **Script de Auditoria em R:**
   - `scripts/kruskal_dunn_holm.R` (permite exportar tabelas e validar diretamente no R com `stats::kruskal.test` e pós-teste de Dunn).


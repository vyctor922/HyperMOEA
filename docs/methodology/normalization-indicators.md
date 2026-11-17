# 4. Normalização de Objetivos e Indicadores de Qualidade

## 4.1. Filosofia de Medição e Separação de Regimes
O projeto adota dois regimes estritamente separados para avaliação de qualidade (T08 e T17):
1. **Regime no Espaço Bruto:**
   - Métricas computadas nas unidades nativas do problema.
   - Ponto de referência do hipervolume bruto padronizado por problema e dimensão $m$:
     - WFG: $z_j = 2.2j$ (com $z_1 = 3.1$ para `WFG3` em $m=2$ devido a valores negativos no jMetal);
     - DTLZ1: $z_j = 0.6$;
     - DTLZ2–DTLZ6: $z_j = 1.1$;
     - DTLZ7: $(1.1, \dots, 1.1, 2.1m)$.
   - O ponto de referência domina estritamente 100% dos pontos da frente de Pareto conhecida.
2. **Regime Normalizado:**
   - Mapeamento afim para o hipercubo unitário $[0, 1]^m$ utilizando o ponto ideal $\mathbf{z}^*$ e o ponto nadir $\mathbf{z}^{\text{nad}}$ da frente de referência.
   - Ponto de referência normalizado implícito: $\mathbf{r}_{\text{norm}} = (1.0, \dots, 1.0) \in \mathbb{R}^m$ ou $(1.1, \dots, 1.1)$.

## 4.2. Indicadores Avaliados
1. **Hipervolume Bruto ($HV$):** Métrica primária de maximização calculada por `PISAHypervolume` ou `WFGHypervolume` (ou fallback Monte Carlo com Wilson CI em $m=15$).
2. **Hipervolume Normalizado ($NHV$):** Métrica secundária de minimização, calculada sobre a frente invertida em $[0, 1]^m$.
3. **IGD+ (Inverted Generational Distance Plus):** Métrica de distância modificada estritamente compatível com dominância de Pareto (Ishibuchi et al., 2015).
4. **R2:** Indicador baseado em funções de utilidade de Tchebycheff ponderadas uniformemente no simplex, sensível tanto à convergência quanto à dispersão.
5. **IGD:** Inverted Generational Distance euclidiana clássica mantida para comparação histórica (com a ressalva documentada na T19 de que IGD não é compatível com Pareto).

## 4.3. Teorema de Invariância de Postos entre HV e NHV
Provado na T17 e validado na T21:
$$NHV = 1.0 - \frac{HV}{V_{\text{box}}}$$
Por ser uma transformação afim estritamente decrescente quando $V_{\text{box}}$ é constante, a ordem dos postos de todos os algoritmos é perfeitamente invertida. Os testes não-paramétricos de Kruskal-Wallis produzem a mesma estatística $H$ e o mesmo $p$-valor, e o teste de Dunn-Holm produz os mesmos $p$-valores ajustados.


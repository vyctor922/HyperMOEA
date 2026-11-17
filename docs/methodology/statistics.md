# 5. Protocolo de Inferência Estatística Não-Paramétrica

## 5.1. Unidade Experimental e Independência
- Cada execução estocástica ($run \in \{0, \dots, 29\}$) de um algoritmo em uma célula $(problema, m)$ constitui uma observação independente.
- As sementes pseudo-aleatórias são controladas pelo gerador SplitMix64 determinístico (T15), assegurando independência entre execuções.
- Não se realizam agregações ingênuas entre problemas ou dimensões distintas como se fossem amostras independentes.

## 5.2. Teste Omnibus de Kruskal-Wallis com Correção de Empates
Para cada célula $(problema, m, indicador)$, aplica-se o teste omnibus de Kruskal-Wallis:
- **Atribuição de Postos:** Ranks ordenados globalmente com média fracionária (midranks) para valores idênticos.
- **Estatística Não-Corrigida:**
  $$H_0 = \frac{12}{N(N+1)} \sum_{g=1}^k \frac{R_g^2}{n_g} - 3(N+1)$$
- **Fator de Correção de Empates ($C$):**
  $$C = 1 - \frac{\sum_t (t^3 - t)}{N^3 - N}$$
- **Estatística Corrigida:**
  $$H = \frac{H_0}{C} \quad (\text{com } H = 0.0 \text{ e } p = 1.0 \text{ se } C \le 10^{-15})$$
- **Tamanho de Efeito Omnibus:** Epsilon-quadrado ($\varepsilon^2 = H / (N - 1)$).

## 5.3. Pós-Teste de Dunn-Holm com FWER
- Para cada um dos $M = \binom{k}{2} = 15$ pares de algoritmos:
  $$z_{ij} = \frac{\bar{R}_i - \bar{R}_j}{\sigma_{ij}}$$
- **Controle de Taxa de Erro Familiar (FWER):** Procedimento sequencial *step-down* de Holm:
  $$p_{(m)}^{\text{adj}} = \min\left(1.0, \, \max_{r \le m} \left( (M - r + 1) \cdot p_{(r)}^{\text{raw}} \right) \right)$$
- **Tamanho de Efeito Par-a-Par:** Estatística não-paramétrica de Vargha-Delaney $\hat{A}_{12}$ e Cliff's Delta ($\delta = 2\hat{A}_{12} - 1$).


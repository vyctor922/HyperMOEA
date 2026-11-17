# 1. Escopo Experimental do Estudo (hypeMOEA - Fase 3)

## 1.1. Objetivo Geral e Delimitação
O projeto `hypeMOEA` tem por finalidade estabelecer uma comparação experimental consistente, reprodutível, estatisticamente rigorosa e metodologicamente justificada de algoritmos genéticos multiobjetivo (MOEAs) de estado da arte em problemas de benchmark contínuos com alta dimensionalidade de objetivos (*Many-Objective Optimization*, MaOO).

O presente escopo delimita estritamente:
1. **Fase 3 (Fase Atual):** Avaliação experimental comparativa dos algoritmos canônicos individuais operando isoladamente com orçamento computacional idêntico, geradores pseudo-aleatórios determinísticos e protocolo de medição padronizado.
2. **Fase Futura (Adaptação de Hiper-Heurística com $\Delta R^2$):** A integração de operadores adaptativos e seleção dinâmica baseada em aprendizado por reforço (LAST-RL / $\Delta R^2$) constitui etapa posterior, dependente da homologação e estabelecimento da linha de base dos algoritmos individuais. Não foram introduzidas modificações dinâmicas ou seleções concorrentes na fase atual.

## 1.2. Dimensões e Problemas Avaliados
- **Dimensionalidade de Objetivos ($m$):**
  $$m \in \{2, 3, 5, 8, 10, 15\}$$
  cobrindo desde o regime multiobjetivo clássico ($m=2, 3$) até problemas de muitos objetivos com alta complexidade combinatória e geométrica ($m=8, 10, 15$).
- **Suítes de Benchmark:**
  - **WFG (Walking-Fish-Group):** WFG1, WFG2, WFG3, WFG4, WFG5, WFG6, WFG7, WFG8, WFG9 com parâmetros $k = 2(m - 1)$ variáveis de posição e $l = 20$ variáveis de distância ($n = k + l$).
  - **DTLZ (Deb-Thiele-Laumanns-Zitzler):** DTLZ1, DTLZ2, DTLZ3, DTLZ4, DTLZ5, DTLZ6, DTLZ7 com $l = 20$ variáveis de distância e $n = m + l - 1$ ($n = m + 4$ para DTLZ1 e $n = m + 9$ para DTLZ2–6 na convenção canônica).
- **Número de Execuções Independentes:**
  $$n_{\text{runs}} = 30$$
  garantindo poder amostral adequado para inferência estatística não-paramétrica.


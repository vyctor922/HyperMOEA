# 8. Proposta de Revisão e Correção do Texto da Dissertação

A partir dos achados forenses e matemáticos consolidados nas tarefas T01–T23, propõem-se as seguintes retificações no texto da dissertação de mestrado:

## 8.1. Substituição do Algoritmo HypE por MOEA/DD e Inclusão de AGE-MOEA-II
- **Texto Anterior:** Mencionava o algoritmo HypE como representante baseado em hipervolume.
- **Correção Proposta:** Retificar o texto para refletir a substituição do HypE pelo **MOEA/DD** (Li et al., 2015), fundamentando que o HypE original emprega amostragem estocástica de Monte Carlo desprovida de limites de confiança analíticos e com semente instável no jMetal, enquanto o MOEA/DD provê balanceamento determinístico entre dominância e decomposição.
- **Inclusão do AGE-MOEA-II:** Incorporar o **AGE-MOEA-II** (Panichella, 2022) como extensão do AGE-MOEA para geometrias complexas e de alta dimensão.

## 8.2. Correção da Identidade Algorítmica do FDEA
- **Texto Anterior:** Atribuição inconsistente de autoria ou parâmetros genéricos ao FDEA.
- **Correção Proposta:** Citar a formulação primária de Wang, Zhang, Zhang & Yen (2016) (*IEEE TEVC*), descrevendo a autodecomposição via fronteira fuzzy e explicitando os reparos realizados no torneio binário e no gerador de números aleatórios unseeded.

## 8.3. Distinção entre Ponto de Referência do Hipervolume e Ponto Nadir
- **Texto Anterior:** Identificação errônea do ponto de referência do hipervolume como sendo o "ponto nadir".
- **Correção Proposta:** Esclarecer matematicamente que o ponto de referência $\mathbf{r}$ de hipervolume deve **dominar estritamente** o ponto nadir $\mathbf{z}^{\text{nad}}$ ($\forall i, r_i > z^{\text{nad}}_i$), fixando $z_j = 2.2j$ para WFG e $1.1$ para DTLZ. Se $\mathbf{r} = \mathbf{z}^{\text{nad}}$, soluções extremas na fronteira recebem contribuição nula ($0.0$) de hipervolume.

## 8.4. Adequação dos Testes Estatísticos (Kruskal-Wallis vs Wilcoxon/Friedman)
- **Texto Anterior:** Menção a testes pareados de Wilcoxon ou Friedman aplicados linha a linha sobre execuções de algoritmos independentes.
- **Correção Proposta:** Justificar que as 30 execuções de cada algoritmo são **amostras independentes**, tornando o teste de **Kruskal-Wallis** com correção para empates (midranks) o teste omnibus canônico apropriado, seguido pelo pós-teste de **Dunn-Holm** para controle da taxa de erro por família (FWER), complementado pelo tamanho de efeito de Vargha-Delaney $\hat{A}_{12}$.

## 8.5. Separação entre a Linha de Base dos MOEAs e a Hiper-Heurística Posterior
- **Texto Anterior:** Mencionava aprendizado por reforço (LAST-RL) ou operadores $\Delta R^2$ misturados à execução dos algoritmos convencionais.
- **Correção Proposta:** Estruturar claramente o trabalho em duas etapas:
  1. *Etapa 1 (Consolidada nesta fase):* Avaliação rigorosa e homologada dos 6 algoritmos isolados;
  2. *Etapa 2 (Trabalho futuro):* Adaptação e seleção dinâmica via hiper-heurística com $\Delta R^2$.


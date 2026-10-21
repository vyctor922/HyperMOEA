# NewMOEA — Framework Experimental de Otimização Multi-objetivo e Hiper-heurísticas

Este projeto consiste em um ambiente experimental avançado construído sobre o ecossistema do **jMetal 6.9.3** em Java, voltado à avaliação empírica de Algoritmos Evolutivos Multi-objetivo (MOEAs), problemas com muitos objetivos (MaOOPs) e à **adaptação e integração de mecanismos de hiper-heurísticas mono-objetivo para o gerenciamento de otimizadores no contexto multi-objetivo**.

O projeto reflete e operacionaliza a metodologia descrita na proposta de dissertação:
> **"Adaptação de Hiper-heurísticas Mono para Otimização Multi-objetivo"**
> *Programa de Pós-Graduação em Ciência da Computação (PPGCC) — Universidade Federal do Acre (UFAC)*
> *Autor: Victor Alexandre Lima Ribeiro | Orientador: Prof. Dr. Olacir Rodrigues Castro Junior (2026)*

---

## 1. Fundamentação Teórica: Entrada Linear da Hiper-heurística e o Indicador $R^2$

### 1.1 O Desafio Estrutural da Entrada Linear
Hiper-heurísticas de seleção concebidas originalmente para cenários mono-objetivo (notadamente **AdapHH**, **EA-ILS** e **LAST-RL**) foram projetadas sobre a premissa de um **sinal de retroalimentação escalar unidimensional**:
* Em problemas mono-objetivo, cada solução $x$ possui um custo direto $f(x) \in \mathbb{R}$. A melhoria de fitness é linear e imediata:
  $$\Delta f = f(x_{t-1}) - f(x_t)$$
* Os mecanismos de aprendizado por reforço (como no LAST-RL e AdapHH), roletas adaptativas e funções de escolha utilizam esse $\Delta f$ para atribuir recompensas ou penalidades às Heurísticas de Baixo Nível (LLHs).
* Os mecanismos Tabu baseiam-se em $\Delta f \le 0$ para aplicar penalidades de permanência (*tabu tenure*) a heurísticas ineficazes.
* Os critérios de aceitação adaptativos (como o AILLA) avaliam limiares numéricos escalares para decidir se aceitam soluções estagnadas.

No contexto multi-objetivo e de muitos objetivos (MaOO), a saída gerada a cada iteração de um MOEA não é um único número real, mas um conjunto populacional de vetores de objetivos mutuamente não-dominados ($PF_k \subset \mathbb{R}^m$). Como o espaço $\mathbb{R}^m$ não possui uma ordem total natural, a hiper-heurística requer um mecanismo que converta a qualidade desse conjunto em um sinal escalar representativo.

### 1.2 O Papel do Indicador $R^2$ como Mecanismo de Projeção Escalar
Para viabilizar a utilização direta das hiper-heurísticas mono-objetivo no gerenciamento de meta-heurísticas multi-objetivo sem descaracterizar sua lógica interna de controle, adota-se o **indicador de qualidade $R^2$** como função de projeção dimensional:

$$PF_k \longmapsto R^2(PF_k, W, z^*) \in \mathbb{R}$$

Assumindo a função escalar de Tchebycheff com um ponto de referência ideal $z^*$ e um conjunto de vetores de peso uniformemente distribuídos $W$, o indicador $R^2$ é definido como:

$$R^2(PF_k, W, z^*) = \frac{1}{|W|} \sum_{w \in W} \min_{u \in PF_k} \left\{ \max_{j \in \{1,\dots,m\}} \{ w_j \cdot |z_j^* - u_j| \} \right\}$$

em que $u$ representa o vetor de objetivos de uma solução na aproximação $PF_k$, $m$ denota o número de objetivos e $w_j$ o peso associado à $j$-ésima dimensão.

### 1.3 Mapeamento do Sinal de Recompensa
Valores menores de $R^2$ indicam maior proximidade e melhor distribuição do conjunto de soluções em relação ao ponto ideal de referência. Dessa forma, a recompensa escalar $\Delta R^2$ fornecida à camada de alto nível da hiper-heurística a cada ciclo de avaliação $t$ é dada por:

$$\Delta R^2 = R^2(PF_{t-1}) - R^2(PF_t)$$

* Se $\Delta R^2 > 0$: Houve **melhoria** na convergência e/ou diversidade da população $\to$ Atribuição de recompensa positiva à LLH aplicada, atualização de matriz de transição/Q-valores e reforço da estratégia.
* Se $\Delta R^2 \le 0$: Houve **estagnação ou piora** $\to$ Penalização, inclusão temporária da LLH na lista Tabu ou submissão ao critério adaptativo de aceitação por limiar (AILLA).

### 1.4 Vantagens Computacionais do $R^2$ frente ao Hipervolume ($HV$)
1. **Escalabilidade**: O cálculo exato do Hipervolume possui complexidade que cresce exponencialmente com o número de objetivos ($O(n^{m/2})$ ou pior), tornando-se impraticável para monitoramento contínuo online em $m \in \{5, 8, 10, 15\}$.
2. **Custo Linear por Solução**: O indicador $R^2$ possui complexidade temporal $O(|W| \cdot |PF_k| \cdot m)$, sendo perfeitamente escalável para centenas de iterações em problemas de muitos objetivos.
3. **Avaliação Conjunta**: O $R^2$ sintetiza de forma rigorosa convergência e uniformidade ao longo de todo o espaço delimitado pelos vetores de peso $W$.

---

## 2. Conjunto de Algoritmos do Estudo

O portfólio de meta-heurísticas de baixo nível (LLHs) implementado no projeto reflete rigorosamente a taxonomia estabelecida na proposta de dissertação, dividindo-se em duas vertentes fundamentais, com a substituição do HypE pelo **MOEA/DD** conforme diretriz experimental:

### 2.1 Vertente 1: Algoritmos Orientados à Qualidade do Conjunto
Esta vertente atua restaurando a pressão seletiva em problemas de alta dimensionalidade por meio de indicadores explícitos, pontos de referência ou relações fortalecidas:
* **NSGA-III** (*Deb & Jain, 2014*): Estende o NSGA-II substituindo a distância de aglomeração por nichamento baseado em pontos de referência estruturados sobre um simplex unitário (método de Das & Dennis).
* **MOEA/DD** (*Li et al., 2015*): Combina a decomposição do espaço em subproblemas direcionados (baseados em vizinhança PBI) com a ordenação por dominância de Pareto, equilibrando convergência e diversidade de forma robusta. **Utilizado no projeto em substituição ao HypE**.
* **FDEA** (*Liu et al., 2022*): Fuzzy-based Pareto Optimality for Many-objective Evolutionary Algorithms. Introduz graus contínuos de dominância fuzzy entre soluções não-dominadas clássicas e autodecomposição do espaço de busca via amostragem por máxima similaridade (MSBS).

### 2.2 Vertente 2: Algoritmos Orientados à Diversidade Geométrica
Esta vertente preserva a diversidade recorrendo a grandezas geométricas contínuas (distâncias angulares, projeções e curvaturas):
* **RVEA** (*Cheng et al., 2016*): Reference Vector Guided Evolutionary Algorithm. Emprega a Distância Penalizada por Ângulo (APD) para avaliar convergência e alinhamento a vetores unitários, contando com mecanismo de adaptação periódica dos vetores para acomodar frentes com geometrias irregulares.
* **AGE-MOEA** (*Panichella, 2019*): Adaptive Geometry Estimation-based Many-objective Evolutionary Algorithm. Estima adaptativamente o formato e a curvatura $p$ da Fronteira de Pareto durante a busca a partir de pontos extremos, calculando escores de sobrevivência com base em proximidade geodésica.
* **AGE-MOEA-II** (*Panichella, 2020*): Variante refinada do AGE-MOEA que aprimora o cálculo de proximidade e diversidade em variedades não-euclidianas de alta dimensão.

### 2.3 Algoritmos Complementares de Linha de Base (Baselines)
Para fins de controle experimental e validação estatística, o ambiente mantém:
* **MOEA/D-DRA**: MOEA/D com alocação dinâmica de recursos baseada em histórico de sucesso por subproblema.
* **NSGA-II** e **SPEA2**: Referências clássicas de ordenação por dominância e distância de aglomeração/força de Pareto.
* **ESPEA**: Algoritmo baseado no conceito de equilíbrio termodinâmico e entropia de Pareto.
* **GWASFGA**: Weighting Assessment Strategy with Reference Points.

---

## 3. Metodologia Experimental e Forma de Testes

O ambiente automatiza o protocolo experimental recomendado na literatura especializada (30 repetições independentes com sementes estocásticas distintas):

### 3.1 Problemas de Benchmark
* **Suíte WFG (WFG1 a WFG9)**: Instâncias com propriedades complexas: fronteiras não-separáveis, deceptivas, multimodalidade, viés paramétrico e formatos mistos (convexas, côncavas e planas).
* **Suíte DTLZ (DTLZ1 a DTLZ7)**: Problemas escaláveis com geometrias analiticamente conhecidas (lineares em DTLZ1, esféricas côncavas em DTLZ2/DTLZ3, densidade variável em DTLZ4, degeneradas em DTLZ5/DTLZ6 e desconexas em DTLZ7).

### 3.2 Dimensionalidade dos Objetivos ($m$)
O estudo contempla todo o espectro dimensional definido na proposta:
$$m \in \{2, 3, 5, 8, 10, 15\}$$

O tamanho populacional e a alocação de gerações são automaticamente ajustados por dimensão via `StaticParams.java`:
* $m = 2$: População de 100 indivíduos (divisões = 99)
* $m = 3$: População de 91 indivíduos (divisões = 12)
* $m = 5$: População de 210 indivíduos (divisões = 6)
* $m = 8$: População de 156 indivíduos (duas camadas: 3 externa, 2 interna)
* $m = 10$: População de 275 indivíduos (duas camadas: 3 externa, 2 interna)
* $m = 15$: População de 135 indivíduos (duas camadas: 2 externa, 1 interna)

### 3.3 Indicadores de Qualidade Pós-Processamento
Para cada execução, o framework gera os arquivos de frentes (`FUN`) e variáveis de decisão (`VAR`), submetendo-os ao pipeline de indicadores:
1. **$R^2$**: Avaliação da métrica de utilidade com função de Tchebycheff e ponto ideal $z^*$.
2. **Hipervolume ($HV$)**: Cálculo do volume de espaço dominado via `PISAHypervolume`.
3. **IGD+ ($IGD^+$)**: Inverted Generational Distance Plus, sensível apenas a degradações em relação à frente verdadeira e estritamente compatível com dominância de Pareto.

### 3.4 Análise Estatística e Saídas
Ao término da execução, o módulo `jmetal-lab` integrado produz:
* **Tabelas LaTeX** com médias, medianas e desvios-padrão de cada indicador.
* **Teste não-paramétrico de Wilcoxon**: Scripts em R para geração de matrizes de significância estatística pareada com correção de Holm.
* **Teste de Friedman**: Cálculo de rankings globais médios entre os algoritmos.
* **Boxplots em R**: Scripts automatizados em `WFGStudy/R/` para renderização visual da dispersão das distribuições.

---

## 4. Estrutura de Diretórios do Projeto

```text
NewMOEA/
├── src/main/java/
│   ├── br/usp/lti/newmoea/
│   │   ├── SimpleNSGAII.java
│   │   └── study/
│   │       ├── StaticParams.java               ← Configuração de gerações e populações por problema/m
│   │       └── WFGStudy.java                   ← Classe orquestradora do estudo experimental
│   └── org/uma/jmetal/
│       ├── algorithm/multiobjective/
├── MOEAD_Weights/                              ← Arquivos de pesos (.dat) para 2, 3, 5, 8, 10 e 15 objetivos
├── pareto_fronts/csv/                          ← Frentes de referência analíticas em formato CSV
├── pom.xml                                     ← Configuração Maven e dependências jMetal
└── README.md                                   ← Documentação completa do projeto
```

---

## 5. Como Rodar o Projeto

### Pré-requisitos
1. **Java Development Kit (JDK) 17** ou superior instalado e configurado no `JAVA_HOME`.
2. **Apache Maven 3.6+** instalado e disponível no terminal (`mvn -version`).
3. *(Opcional)* **R** instalado com o pacote `knitr` caso deseje gerar os relatórios em PDF/gráficos via scripts R exportados em `WFGStudy/R/`.

### Passo 1: Limpar e Compilar o Projeto
Abra o terminal no diretório raiz do projeto (`NewMOEA`) e execute:
```bash
mvn clean compile
```
O build deve apresentar status `BUILD SUCCESS`, compilando todos os algoritmos, indicadores e utilitários.

### Passo 2: Execução de Teste Rápido de Validação (Recomendado)
Para verificar rapidamente a integridade do pipeline sem aguardar as 30 repetições completas de todos os problemas, utilize a propriedade `-Druns`:
```bash
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=1
```
*(Você também pode testar com 2 ou 5 execuções independentes usando `-Druns=2` ou `-Druns=5`)*.

### Passo 3: Execução Experimental Completa (30 Runs Oficiais)
Para rodar a bateria científica completa com as 30 repetições independentes conforme preconizado na dissertação:
```bash
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy"
```
*Dica*: O experimento utiliza execução paralela nativa em múltiplos núcleos de CPU (`.setNumberOfCores(8)`), acelerando significativamente o processamento das 30 repetições.

#### Execução de Teste Rápido (ex.: 2 ou 5 Runs)
Para validar o funcionamento rapidamente sem esperar todas as 30 execuções:
```bash
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=5
```

### Passo 4: Acessar os Resultados
Ao final da execução, os seguintes artefatos estarão disponíveis no diretório `WFGStudy/`:
* `WFGStudy/data/<Algoritmo>/<Problema>/`: Contém os arquivos `FUN<run>.csv` (valores de objetivos) e `VAR<run>.csv` (variáveis de decisão) de cada repetição.
* `WFGStudy/data/<Indicador>`: Tabelas com os valores calculados de $R^2$, Hipervolume e $IGD^+$.
* `WFGStudy/latex/`: Tabelas formatadas em LaTeX com medianas, médias e desvios-padrão.
* `WFGStudy/R/`: Scripts R prontos para gerar as análises de Wilcoxon, Friedman e gráficos Boxplot.

---

## 6. Histórico de Alterações

Abaixo estão registradas todas as intervenções de engenharia de software e ajustes metodológicos implementados no projeto:

### Alterações 20/10/2026 - 17:00

1. **Correção de Portabilidade e Diretório Base**:
   * Removido o caminho estático macOS (`/Users/viniciusdecarvalho/PJT/NewMOEA`) de `WFGStudy.java`, substituindo-o por `System.getProperty("user.dir")`, garantindo funcionamento sem modificações manuais em qualquer ambiente (Windows, Linux, macOS).

2. **Inclusão do Algoritmo NSGA-III**:
   * Apenas o NSGA-II clássico estava instanciado na suíte então foi implementado o método `buildNSGAIII(...)` com cálculo sistemático das divisões de hiperplano ($p$) de Das & Dennis para 2, 3, 5, 8, 10 e 15 objetivos, alinhando-se à metodologia de muitos objetivos da proposta.

3. **Substituição de HypE por MOEA/DD**:
   * Atendendo à especificação temporária da pesquisa, o algoritmo **MOEA/DD** (`MOEADBuilder.Variant.MOEADD`).

4. **Expansão dos Objetivos e Parâmetros Estáticos**:
   * O estudo estava limitado a $M = \{2, 3, 5\}$ e faltava a configuração de gerações para 2 objetivos em `StaticParams.java`, foi então incluído o tratamento de $m=2$ em `StaticParams.getMaxGenerations` e expandida a lista de objetivos para $M \in \{2, 3, 5, 8, 10, 15\}$.

5. **Adequação ao Padrão Estatístico (30 Runs)**:
   * O padrão foi mudado de 5 para 30 execuções independentes (`INDEPENDENT_RUNS = 30`), com suporte a sobreposição transparente via linha de comando (`-Druns=N`).

# NewMOEA — Framework Experimental de Otimização Multi-objetivo e Hiper-heurísticas

Este projeto consiste em um ambiente experimental construído sobre o ecossistema do **jMetal 7.8** em **Java 21 (LTS)**, voltado à avaliação empírica de Algoritmos Evolutivos Multi-objetivo (MOEAs), problemas com muitos objetivos (MaOOPs) e à **adaptação e integração de mecanismos de hiper-heurísticas mono-objetivo para o gerenciamento de otimizadores no contexto multi-objetivo**.

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

### 1.4 Coleta Durante a Execução via `R2ExecutionObserver`
* **Não-invasivo**: Apenas observa o conjunto populacional corrente em intervalos configurados, sem realizar avaliações adicionais da função objetivo do problema.
* **Histórico e Exportação**: Mantém instâncias de `R2Observation(step, r2Value, deltaR2, timestampMs)` e suporta exportação direta para CSV.

---

## 2. Conjunto de Algoritmos do Estudo

O portfólio de meta-heurísticas de baixo nível (LLHs) implementado no projeto reflete a taxonomia estabelecida na proposta de dissertação, dividindo-se em duas vertentes fundamentais, com a **substituição do HypE pelo MOEA/DD** por motivos técnicos:

### 2.1 Vertente 1: Algoritmos Orientados à Qualidade do Conjunto
Esta vertente atua restaurando a pressão seletiva em problemas de alta dimensionalidade por meio de indicadores explícitos, pontos de referência ou relações fortalecidas:
* **NSGA-III** (*Deb & Jain, 2014*): Estende o NSGA-II substituindo a distância de aglomeração por nichamento baseado em pontos de referência estruturados sobre um simplex unitário (método de Das & Dennis). Reutilizado nativamente do jMetal 7.8 (`NSGAIIIBuilder`).
* **MOEA/DD** (*Li et al., 2015*): Combina a decomposição do espaço em subproblemas direcionados (baseados em vizinhança PBI) com a ordenação por dominância de Pareto, equilibrando convergência e diversidade de forma robusta. **Utilizado no projeto em substituição ao HypE**. Reutilizado nativamente do jMetal 7.8 (`MOEADBuilder.Variant.MOEADD`).
* **FDEA** (*Liu et al., 2022*): Fuzzy-based Pareto Optimality for Many-objective Evolutionary Algorithms. Introduz graus contínuos de dominância fuzzy entre soluções não-dominadas clássicas e autodecomposição do espaço de busca via amostragem por máxima similaridade (MSBS). Fuzzy-based Decomposition Evolutionary Algorithm. Portado do repositório original para a API do jMetal 7.8 como adaptador desacoplado (`br.usp.lti.newmoea.algorithm.fdea.FDEA`).

### 2.2 Vertente 2: Algoritmos Orientados à Diversidade Geométrica
Esta vertente preserva a diversidade recorrendo a grandezas geométricas contínuas (distâncias angulares, projeções e curvaturas):
* **RVEA** (*Cheng et al., 2016*): Reference Vector Guided Evolutionary Algorithm. Emprega a Distância Penalizada por Ângulo (APD) para avaliar convergência e alinhamento a vetores unitários, contando com mecanismo de adaptação periódica dos vetores para acomodar frentes com geometrias irregulares. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder`).
* **AGE-MOEA** (*Panichella, 2019*): Adaptive Geometry Estimation-based Many-objective Evolutionary Algorithm. Estima adaptativamente o formato e a curvatura $p$ da Fronteira de Pareto durante a busca a partir de pontos extremos, calculando escores de sobrevivência com base em proximidade geodésica. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.algorithm.multiobjective.agemoea.AGEMOEABuilder`).
* **AGE-MOEA-II** (*Panichella, 2020*): Variante refinada do AGE-MOEA que aprimora o cálculo de proximidade e diversidade em variedades não-euclidianas de alta dimensão. Reutilizado nativamente do jMetal 7.8 (`org.uma.jmetal.algorithm.multiobjective.agemoeaii.AGEMOEAIIBuilder`).

### 2.3 Algoritmos Complementares de Linha de Base (Baselines)
Para fins de controle experimental e validação estatística, o ambiente mantém:
* **MOEA/D-DRA**: MOEA/D com alocação dinâmica de recursos baseada em histórico de sucesso por subproblema.
* **NSGA-II** e **SPEA2**: Referências clássicas de ordenação por dominância e distância de aglomeração/força de Pareto.
* **ESPEA**: Algoritmo baseado no conceito de equilíbrio termodinâmico e entropia de Pareto.
* **GWASFGA**: Weighting Assessment Strategy with Reference Points.

---

## 3 Indicadores de Qualidade

O pipeline pós-processamento e os testes unitários avaliam as aproximações de Pareto por meio de quatro indicadores:

1. **$R^2$ (`org.uma.jmetal.qualityindicator.impl.R2`)**:
   - Função utilitária de Tchebycheff com vetores de peso uniformes e ponto ideal $z^*$.
   - Menor valor indica melhor aproximação.
   - Disponível tanto pós-execução no pipeline quanto em tempo de execução via `R2ExecutionObserver`.
2. **IGD Padrão (`org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance`)**:
   - Distância média Euclidiana de cada ponto da fronteira de referência ao ponto mais próximo da população gerada.
   - Menor valor indica melhor aproximação.
3. **IGD+ (`org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistancePlus`)**:
   - Distância modificada que penaliza apenas soluções dominadas ou piores que a referência, garantindo monotonicidade com dominância de Pareto.
   - Menor valor indica melhor aproximação.
4. **Hipervolume ($HV$, via `PISAHypervolume`)**:
   - Volume do espaço de objetivos dominado pela população obtida em relação a um ponto de referência.
   - Maior valor indica melhor desempenho.
   - *Limitação Computacional*: O cálculo exato possui complexidade exponencial com o número de objetivos. Para dimensões elevadas ($m \ge 8$), prioriza-se a avaliação por $R^2$, $IGD^+$ e IGD, reservando aproximações de Monte Carlo quando o cálculo exato se torna proibitivo.

---

## 4. Problemas de Benchmark

### 4.1 Suítes Utilizadas
* **Suíte WFG (WFG1 a WFG9)**: Instâncias com propriedades complexas: fronteiras não-separáveis, deceptivas, multimodalidade, viés paramétrico e formatos mistos (convexas, côncavas e planas).
* **Suíte DTLZ (DTLZ1 a DTLZ7)**: Problemas escaláveis com geometrias lineares (DTLZ1), côncavas (DTLZ2, DTLZ3), viés de densidade (DTLZ4), degeneradas (DTLZ5, DTLZ6) e desconexas (DTLZ7).

### 4.2 Dimensionalidade dos Objetivos ($m$) e Populações
O estudo contempla as dimensões $m \in \{2, 3, 5, 8, 10, 15\}$, com parâmetros centrais padronizados em `StaticParams.java`:
* $m = 2$: População de 100 indivíduos (divisões = 99 em 1 camada)
* $m = 3$: População de 91 indivíduos (divisões = 12 em 1 camada)
* $m = 5$: População de 210 indivíduos (divisões = 6 em 1 camada)
* $m = 8$: População de 156 indivíduos (duas camadas: 3 externa, 2 interna)
* $m = 10$: População de 275 indivíduos (duas camadas: 3 externa, 2 interna)
* $m = 15$: População de 135 indivíduos (duas camadas: 2 externa, 1 interna)

---

## 5. Estrutura de Diretórios do Projeto

```text
NewMOEA/
├── pom.xml                                     ← Configuração Maven (Java 21, jMetal 7.8, JUnit 5)
├── README.md                                   ← Documentação detalhada
├── MOEAD_Weights/                              ← Vetores de peso (.dat) para MOEA/D e GWASFGA
├── pareto_fronts/csv/                          ← Frentes de referência analíticas em CSV (WFG1-9, DTLZ1-7)
└── src/
    ├── main/java/
    │   └── br/usp/lti/newmoea/
    │       ├── SimpleNSGAII.java               ← Exemplo simples de execução individual
    │       ├── algorithm/
    │       │   └── fdea/                       ← Adaptador FDEA para jMetal 7.8
    │       │       ├── FDEA.java
    │       │       ├── FDEABuilder.java
    │       │       ├── FDEAIndividual.java
    │       │       ├── MostSimilarBasedSampling.java
    │       │       └── LeastSimilarBasedSampling.java
    │       ├── feedback/                       ← Infraestrutura de observação R² durante execução
    │       │   ├── R2Observation.java
    │       │   └── R2ExecutionObserver.java
    │       │
    │       └── study/
    │           ├── StaticParams.java           ← Populações e gerações por problema/m
    │           └── WFGStudy.java               ← Orquestrador do estudo experimental jMetal
    └── test/java/
        └── br/usp/lti/newmoea/
            ├── algorithm/
            │   └── AlgorithmIntegrationTest.java ← Testes de NSGA-III, MOEA/DD, FDEA, RVEA, AGE-MOEA, AGE-MOEA-II
            ├── feedback/
            │   └── R2ExecutionObserverTest.java  ← Testes da coleta runtime de R² e exportação CSV
            └── qualityindicator/
                └── QualityIndicatorUnitTest.java ← Testes unitários de R2, IGD, IGD+ e Hipervolume
```

---

## 6. Como Rodar o Projeto

### Pré-requisitos
1. **Java Development Kit (JDK) 21 (LTS)** instalado e configurado no `JAVA_HOME`.
2. **Apache Maven 3.8+** configurado e disponível no PATH (`mvn -v`).
3. *(Opcional)* **R** com pacote `knitr` instalado se desejar gerar PDFs e gráficos de testes estatísticos.

### Passo 1: Compilar o Projeto
Abra o terminal no diretório raiz do projeto (`NewMOEA`).

Para limpar artefatos anteriores e compilar todo o código-fonte em Java 21 com jMetal 7.8 execute:
```bash
mvn clean compile
```

### Passo 2: Executar a Suíte de Testes Automatizados
O projeto conta com testes unitários e de integração abrangendo algoritmos, observadores e indicadores:
```bash
mvn test
```
*Saída esperada*: 15 testes executados com 0 falhas e 0 erros (`BUILD SUCCESS`).

### Execução de Teste Rápido de Validação
Para verificar rapidamente a integridade do pipeline sem aguardar as 30 repetições completas de todos os problemas, utilize a propriedade `-Druns`, podendo ser utilizada junto com as propriedades `-Dobjectives` e `-Dproblem`:

Para validar o pipeline completo em poucos segundos com 1 repetição, 2 objetivos e 1 problema específico (WFG1):
```bash
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=1 -Dobjectives=2 -Dproblem=WFG1
```
Esse comando instancia os algoritmos (incluindo MOEA/DD, FDEA, RVEA, AGE-MOEA, AGE-MOEA-II, NSGA-III e baselines), gera as frentes `FUN0.csv`, calcula $HV$, $IGD$, $IGD^+$, $R^2$ e cria as tabelas estatísticas em `WFGStudy/`.

Você pode variar o problema ou as dimensões via propriedades JVM:
```bash
# Executa DTLZ2 com 3 objetivos e 2 repetições
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=2 -Dobjectives=3 -Dproblem=DTLZ2

# Executa WFG3 com 5 objetivos e 5 repetições
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=5 -Dobjectives=5 -Dproblem=WFG3
```

### Passo 5: Execução Experimental
Para iniciar o experimento científico completo (30 repetições independentes com sementes estocásticas distintas, todos os problemas WFG/DTLZ e todas as dimensões $m \in \{2, 3, 5, 8, 10, 15\}$ com paralelização em 8 núcleos de CPU):
```bash
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy"
```

*Dica*: O experimento utiliza execução paralela nativa em múltiplos núcleos de CPU (`.setNumberOfCores(8)`), acelerando significativamente o processamento das 30 repetições.

### Passo 6: Localização e Interpretação dos Resultados
Os arquivos gerados são organizados em `WFGStudy/`:
* `WFGStudy/data/<Algoritmo>/<Problema_m>/`:
  - `FUN<run>.csv`: Valores das funções objetivo obtidos na fronteira de Pareto final.
  - `VAR<run>.csv`: Vetores das variáveis de decisão correspondentes.
  - `<Indicador>` (ex.: `HV`, `IGD`, `IGD+`, `R2`): Arquivos com o valor escalar de cada repetição.
* `WFGStudy/QualityIndicatorSummary.csv`: Resumo consolidado de todos os indicadores calculados.
* `WFGStudy/latex/`: Tabelas prontas em LaTeX com médias, medianas e desvios padrão.
* `WFGStudy/R/`: Scripts em R para execução dos testes de Wilcoxon, Friedman e renderização de Boxplots.

---

## 7. Histórico de Alterações

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

### Alterações 21/10/2026 - 09:00

1. **Migração do Ambiente para JDK 21 e jMetal 7.8**:
   - `pom.xml` atualizado com `<maven.compiler.source>21</maven.compiler.source>`, `<maven.compiler.target>21</maven.compiler.target>`, plugin `maven-compiler-plugin:3.13.0` e dependências `jmetal-core`, `jmetal-algorithm`, `jmetal-problem`, `jmetal-lab`, `jmetal-component` elevadas para a versão estável oficial `7.8`.
   - Adicionado `maven-surefire-plugin:3.2.5` e dependência `assertj-core:3.25.3` para suíte de testes em JUnit 5.
2. **Reutilização Estrita de APIs Nativas do jMetal 7.8**:
   - Adotado `org.uma.jmetal.qualityindicator.impl.R2` nativo da biblioteca. O pipeline agora calcula automaticamente os arquivos `R2`, `HV` e `IGD+` para cada algoritmo e problema.
   - Adotado `org.uma.jmetal.component.algorithm.multiobjective.RVEABuilder` nativo com vetores gerados por `org.uma.jmetal.util.referencepoint.ReferencePointGenerator`.
   - Adotados nativamente `AGEMOEABuilder` (versão padrão) e `AGEMOEAIIBuilder` (versão estendida não-euclidiana).
   - Adotado `org.uma.jmetal.qualityindicator.impl.InvertedGenerationalDistance` nativo para IGD padrão.
3. **Porte do FDEA como Adaptador Separado para jMetal 7.8**:
   - Criado o pacote `br.usp.lti.newmoea.algorithm.fdea` contendo `FDEA`, `FDEABuilder`, `FDEAIndividual`, `MostSimilarBasedSampling` e `LeastSimilarBasedSampling`.
4. **Criação do `R2ExecutionObserver`**:
   - Criada a classe `R2ExecutionObserver` em `br.usp.lti.newmoea.feedback` com histórico de `R2Observation` e suporte a exportação CSV.
   - Resolvida a incompatibilidade do jMetal 4.x antigo e Jama, preservando a formulação matemática original de Liu et al. (2022).
5. **Suíte Completa de Testes Criada e Validada**:
   - 15 testes cobrindo integração dos algoritmos (`AlgorithmIntegrationTest`), indicadores unitários (`QualityIndicatorUnitTest`) e coleta de R² (`R2ExecutionObserverTest`), todos aprovados via `mvn test`.
6. **Suporte a Filtros de Problema e Objetivos no WFGStudy**:
   - Inclusão dos parâmetros `-Dobjectives` e `-Dproblem` para permitir execuções reduzidas e rápidas de validação experimental. 

---

## 8. Atividades Reservadas para a Próxima Etapa
Conforme definido no planejamento:
1. Conexão entre o sinal de retroalimentação escalar ($\Delta R^2$ fornecido por `R2ExecutionObserver`) e a lógica de seleção de heurísticas de baixo nível das hiper-heurísticas (AdapHH, EA-ILS e LAST-RL).
2. Construção do pipeline de orquestração adaptativa que invoca sucessivamente os MOEAs ou seus operadores conforme as decisões da hiper-heurística.
3. Comparação sistemática a posteriori do desempenho dos algoritmos guiados por hiper-heurísticas frente aos MOEAs isolados.
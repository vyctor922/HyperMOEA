# 2. Algoritmos Avaliados e Parâmetros Canônicos

## 2.1. Seleção dos Seis Algoritmos Principais
A campanha da Fase 3 avalia seis algoritmos representativos das principais taxonomias de seleção ambiental em muitos objetivos:
1. **NSGA-III** (Deb & Jain, 2014): Baseado em hiperplano de referência no simplex unitário com decomposição estruturada em 1 camada ($m \le 5$) ou 2 camadas ($m \in \{8, 10, 15\}$).
2. **MOEA/DD** (Li, Deb, Zhang & Kwong, 2015): Baseado em decomposição e dominância de Pareto simultâneas, empregando a função de agregação PBI ($\theta = 5.0$) e vizinhança $T = 20$. Substitui o HypE por instrução do projeto e elimina os problemas de aproximação estocástica descontrolada de hipervolume.
3. **FDEA** (Wang et al., 2016): Baseado em dominância fuzzy e autodecomposição espacial com ângulos fuzzy entre soluções e vetores ideais. Auditado e corrigido na T14 para eliminar sementes não-controladas e viés em torneio binário.
4. **RVEA** (Cheng et al., 2016): Baseado em vetores de referência com distância penalizada por ângulo (APD), balanceando convergência e diversidade via parâmetro de controle de penalidade $\alpha = 2.0$ e frequência de adaptação $f_r = 0.1$.
5. **AGE-MOEA** (Panichella, 2019): Baseado em diversidade geométrica com estimação adaptativa da geometria da fronteira de Pareto (p-norm) no espaço de objetivos normalizado.
6. **AGE-MOEA-II** (Panichella, 2022): Variante refinada com projeção não-euclidiana no simplex e preservação de extremos.

## 2.2. Parâmetros Genéticos Universais
Para assegurar comparabilidade estrita, todos os algoritmos compartilham exatamente a mesma parametrização genética contínua auditada na T06 e T14:
- **Crossover SBX (Simulated Binary Crossover):**
  - Probabilidade: $p_c = 1.0$
  - Índice de distribuição: $\eta_c = 30.0$
  - Operador jMetal: `new SBXCrossover(1.0, 30.0)`
- **Mutação Polinomial (Polynomial Mutation):**
  - Probabilidade: $p_m = 1.0 / n$ (onde $n$ é o número de variáveis de decisão do problema)
  - Índice de distribuição: $\eta_m = 20.0$
  - Operador jMetal: `new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0)`
  - Operador de reparo de fronteira: `RepairDoubleSolutionWithBoundValue`
- **Tamanhos Populacionais Canônicos ($N$):**
  - $m=2$: $N = 100$ ($H_1 = 99$)
  - $m=3$: $N = 91$ ($H_1 = 12$)
  - $m=5$: $N = 210$ ($H_1 = 6$)
  - $m=8$: $N = 156$ ($H_1 = 3, H_2 = 2$)
  - $m=10$: $N = 275$ ($H_1 = 3, H_2 = 2$)
  - $m=15$: $N = 135$ ($H_1 = 2, H_2 = 1$)
- **Orçamento de Avaliações ($B$):**
  $$\text{maxEvaluations} = \text{maxGenerations}(m, \text{problema}) \times N$$
  auditado na T13 com `EvaluationBudgetProblem` para garantir término determinístico exato.


# 3. Benchmarks, Frentes de Referência e Resolução Canônica de Artefatos

## 3.1. Arquivos de Frentes de Pareto de Referência
- Todas as 68 frentes de referência ativas e os 160 arquivos em `pareto_fronts/csv/` foram integralmente auditados nas tarefas T04 e T16.
- As frentes de `WFG1–WFG9` para $m \in \{2, 3, 5, 8, 10, 15\}$ são cópias fiéis (SHA-256) das frentes oficiais do jMetal.
- Para a suíte DTLZ:
  - Frentes $m \in \{2, 3\}$ são provenientes de `pareto_fronts/csv/DTLZ*.{2,3}D.csv`.
  - Para $m \in \{5, 8, 10, 15\}$, o jMetal original não fornecia arquivos CSV; a T07/T16 implementou geradores matemáticos e empíricos exatos em `ParetoFrontValidator.generateAndSaveAnalyticalDtlzFront(...)`, satisfazendo as equações de superfície analítica:
    - DTLZ1: $\sum_{i=1}^m f_i = 0.5$;
    - DTLZ2–4: $\sum_{i=1}^m f_i^2 = 1.0$;
    - DTLZ7: $f_m = 2m - \sum_{i=1}^{m-1} f_i (1 + \sin(3\pi f_i))$.

## 3.2. Vetores de Pesos do MOEA/DD (`MOEAD_Weights/`)
- Os arquivos `W{m}D_{N}.dat` armazenados em `MOEAD_Weights/` foram gerados pelo simplex-lattice de Das & Dennis com contração de duas camadas de Deb & Jain para $m \ge 8$.
- Conforme auditado na T01, T03 e T11, o gerador original realizou a substituição numérica $0 \mapsto 10^{-5}$ (`1.00000000000000e-05`) para evitar divisões por zero em variantes clássicas de decomposição (como Tchebycheff sem offset).
- O `ReferenceSetValidator` valida a soma unitária ($\sum w_i = 1.0 \pm 2 \times 10^{-4}$), dimensionalidade, ausência de duplicatas e não-negatividade.

## 3.3. Resolução Canônica de Recursos (`ArtifactResolver.java`)
- Desenvolvido na T18, o `ArtifactResolver` padroniza o acesso aos artefatos através de políticas de resolução ordenadas (`CANONICAL_THEN_RESOURCES`, `RESOURCES_THEN_CANONICAL`), permitindo migração de diretórios sem alterar o hash nem quebrar o funcionamento do pipeline histórico.
- Nenhum arquivo em `MOEAD_Weights/` ou `pareto_fronts/csv/` foi sobrescrito ou modificado.


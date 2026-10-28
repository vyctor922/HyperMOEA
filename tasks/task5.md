# T05 — Padronizar parâmetros, NSGA-III, MOEA/DD e hipervolume

## Objetivo
Aplicar as decisões experimentais já estabelecidas, preservando os mecanismos
específicos de cada algoritmo.

## Instruções

### Operadores comuns
1. Altere SBX para probabilidade 1.0 e índice de distribuição 30.0.
2. Audite `PolynomialMutation` no jMetal 7.8.
3. Configure probabilidade de mutação \(1/n\) e índice 20.0, se a API permitir
   essa semântica.
4. Não altere a chamada se ela já tiver essa semântica.

### NSGA-III
1. Substitua `setSecondLayerDivisions(divisions / 2)` por camadas explícitas.
2. Use:
   - m=2: externa 99, interna 0;
   - m=3: externa 12, interna 0;
   - m=5: externa 6, interna 0;
   - m=8: externa 3, interna 2;
   - m=10: externa 3, interna 2;
   - m=15: externa 2, interna 1.
3. Garanta que a população do NSGA-III seja igual ao número total de pontos.
4. Adicione teste de consistência entre população e pontos.

### MOEA/DD
1. Mantenha PBI, \(T=20\), \(\delta=0.9\) e \(nr=2\).
2. Adicione validação do arquivo de pesos.
3. Verifique se a API expõe \(\theta\) do PBI.
4. Se expuser, considere \(\theta=5\), com registro em `HANDLER.md`.
5. Se não expuser, mantenha o padrão e registre a limitação.

### Hipervolume
1. Mantenha `PISAHypervolume` como métrica primária.
2. Mantenha `NormalizedHypervolume` como métrica secundária.
3. Defina ponto de referência comum por problema e \(m\).
4. Não integre `Main-Hipervolume` ao pipeline.
5. Não misture HV bruto e HV normalizado nas análises.

## Arquivos
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/main/java/br/usp/lti/newmoea/study/StaticParams.java`
- `src/test/java/br/usp/lti/newmoea/algorithm/AlgorithmIntegrationTest.java`
- `src/test/java/br/usp/lti/newmoea/qualityindicator/QualityIndicatorUnitTest.java`
- `HANDLER.md`

## Critérios de aceitação
- `mvn clean test` passa.
- NSGA-III usa camadas explícitas corretas.
- População do NSGA-III é consistente com o número de pontos.
- MOEA/DD valida pesos antes da execução.
- HV bruto e normalizado usam referência comum.
- Nenhuma funcionalidade existente é removida.
- `HANDLER.md` registra as alterações.

## Dependências
- T01 — Investigação de procedência.
- T02 — Validação contra Das & Dennis.
- T03 — Validação dos pesos do MOEA/DD.
- T04 — Validação das frentes de Pareto.

## Referências
- Deb, K., & Jain, H. (2014). An Evolutionary Many-Objective Optimization Algorithm
  Using Reference-Point-Based Nondominated Sorting Approach, Part I. IEEE TEVC.
  DOI: 10.1109/TEVC.2013.2281535.
- Li, M., Yang, S., & Liu, X. (2015). A Decomposition-Based Many-Objective
  Evolutionary Algorithm With Two Archive Sets. IEEE TEVC.
  DOI: 10.1109/TEVC.2014.2374489.
- Cheng, R., Jin, Y., Olhofer, M., & Sendhoff, B. (2016). A Reference Vector Guided
  Evolutionary Algorithm for Many-Objective Optimization. IEEE TEVC.
  DOI: 10.1109/TEVC.2016.2519378.
- Zitzler, E., & Thiele, L. (1999). Multiobjective Evolutionary Algorithms: A
  Comparative Case Study and the Strength Pareto Approach. IEEE TEVC.
  DOI: 10.1109/4233.797969.
- Documentação e código-fonte do jMetal 7.8.
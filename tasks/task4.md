# T04 — Validar frentes de Pareto de referência e recursos do jMetal

## Objetivo
Validar as frentes de Pareto em `pareto_fronts/csv/` quanto à finalidade,
formato, cobertura e compatibilidade com os indicadores do pipeline.

## Contexto técnico
As frentes de Pareto são usadas como referência para IGD, IGD+, hipervolume e
eventualmente \(R^2\). Elas não são vetores de pesos do MOEA/DD nem pontos de
referência do NSGA-III.

Portanto, não devem ser validadas contra Das & Dennis.

## Instruções
1. Inventarie os arquivos em `pareto_fronts/csv/`.
2. Para cada arquivo:
   - identifique problema, \(m\) e formato;
   - confirme o consumidor no pipeline;
   - valide dimensionalidade;
   - valide finitude;
   - identifique valores inválidos, `NaN`, infinitos ou linhas malformadas;
   - verifique se a quantidade de pontos é adequada ao uso como frente de
     referência.
3. Para WFG1–WFG9 e DTLZ1–DTLZ7:
   - identifique a fonte da frente;
   - determine se é analítica, amostrada ou gerada;
   - valide contra a definição analítica, gerador apropriado ou fonte
     científica confiável;
   - documente o critério de validação adotado.
4. Investigue se o jMetal 7.8 disponibiliza:
   - frentes pré-distribuídas;
   - utilitários para carregar frentes;
   - utilitários para gerar frentes;
   - mecanismos de validação.
5. Diferencie explicitamente:
   - carregar um arquivo de frente;
   - distribuir uma frente pronta;
   - gerar a frente correta para um problema.
6. Recomende manter, substituir ou gerar dinamicamente as frentes somente após
   evidência de equivalência e compatibilidade.
7. Não altere os arquivos de frente nesta tarefa.

## Arquivos
- `pareto_fronts/csv/`
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/test/java/br/usp/lti/newmoea/qualityindicator/QualityIndicatorUnitTest.java`
- `HANDLER.md`

## Critérios de aceitação
- Existe inventário completo das frentes utilizadas.
- Cada frente tem finalidade e consumidor identificados.
- Testes validam dimensionalidade, finitude e integridade básica.
- Testes negativos detectam arquivo ausente ou malformado.
- A investigação das alternativas nativas está documentada.
- Nenhuma frente é substituída sem evidência e rastreabilidade.

## Dependências
- T01 — Investigação de procedência.

## Referências
- Huband et al. (2006), WFG test problems.
- Deb et al. (2002), DTLZ test problems.
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
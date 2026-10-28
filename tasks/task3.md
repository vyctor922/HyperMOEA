# T03 — Validar arquivos de pesos do MOEA/DD e alternativas nativas

## Objetivo
Validar os arquivos efetivamente utilizados pelo MOEA/DD em `MOEAD_Weights/`,
verificar sua compatibilidade com o jMetal 7.8 e avaliar se existe alternativa
nativa preferível.

## Contexto técnico
O `buildMOEADD` utiliza:

```java
.setDataDirectory("MOEAD_Weights")
```

A população configurada deve corresponder ao número de vetores de pesos
carregados. Os pesos do MOEA/DD não devem ser tratados como pontos de referência
de Das & Dennis nem como frentes de Pareto.

## Instruções
1. Identifique a classe e o método do jMetal 7.8 que carrega os arquivos.
2. Para cada \(m \in \{2,3,5,8,10,15\}\):
   - localize o arquivo efetivamente utilizado;
   - confirme o padrão de nome esperado;
   - valide o número de linhas;
   - valide a dimensionalidade;
   - valide finitude;
   - valide não negatividade, quando exigido pelo formato;
   - valide soma dos componentes, quando exigido;
   - identifique duplicatas exatas e quase duplicatas.
3. Compare o conteúdo dos arquivos com o `reference-calc`, somente se T01
   comprovar ou indicar fortemente essa procedência.
4. Use comparação independente da ordem das linhas.
5. Use tolerância numérica explícita e justificada.
6. Reporte:
   - número de vetores;
   - dimensionalidade;
   - duplicatas;
   - vetores ausentes ou extras;
   - maior erro absoluto;
   - maior distância euclidiana;
   - veredito de equivalência.
7. Investigue se o jMetal 7.8 permite:
   - gerar pesos dinamicamente;
   - usar recursos pré-distribuídos;
   - carregar pesos de outro local;
   - validar pesos automaticamente.
8. Recomende manter, validar e documentar, substituir ou gerar dinamicamente os
   pesos, somente após evidência de equivalência e compatibilidade.
9. Não sobrescreva arquivos existentes nesta tarefa.

## Arquivos
- `MOEAD_Weights/`
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/test/java/br/usp/lti/newmoea/algorithm/AlgorithmIntegrationTest.java`
- `HANDLER.md`

## Critérios de aceitação
- Existe validação para todos os \(m\) suportados.
- O pipeline rejeita arquivo ausente, com dimensionalidade incorreta ou com
  quantidade incorreta.
- Testes negativos comprovam a rejeição controlada.
- A API nativa do jMetal 7.8 está documentada.
- A recomendação final está fundamentada em evidência.
- Nenhum arquivo de peso é alterado sem justificativa e validação.

## Dependências
- T01 — Investigação de procedência.

## Referências
- Zhang & Li (2007), MOEA/D, DOI: 10.1109/TEVC.2007.892059.
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
# T02 — Validar `reference-calc` e pontos do NSGA-III contra Das & Dennis

## Objetivo
Verificar se `reference-calc.java` implementa o método de Das & Dennis, uma
variante própria ou outro procedimento, e validar os pontos de referência
efetivamente utilizados pelo NSGA-III.

## Contexto técnico
O NSGA-III utiliza pontos de referência estruturados no simplex. Para múltiplas
camadas, as divisões externas e internas são parâmetros independentes.

Configurações a validar:

| m | Externa | Interna | Total esperado |
|---:|---:|---:|---:|
| 2 | 99 | 0 | 100 |
| 3 | 12 | 0 | 91 |
| 5 | 6 | 0 | 210 |
| 8 | 3 | 2 | 156 |
| 10 | 3 | 2 | 275 |
| 15 | 2 | 1 | 135 |

## Instruções
1. Implemente um validador independente do pipeline completo.
2. Gere os conjuntos canônicos de Das & Dennis para cada configuração.
3. Compare com:
   - saída do `reference-calc`;
   - gerador nativo do jMetal 7.8, se disponível;
   - pontos efetivamente utilizados pelo `NSGAIIIBuilder`.
4. A comparação não pode depender da ordem das linhas.
5. Use tolerância numérica explícita, preferencialmente \(10^{-6}\), a menos que
   a precisão dos arquivos justifique outro valor.
6. Para cada comparação, registre:
   - quantidade de vetores;
   - dimensionalidade;
   - duplicatas;
   - vetores sem correspondência;
   - maior erro absoluto por coordenada;
   - maior distância euclidiana entre vetores correspondentes;
   - veredito: equivalente, não equivalente ou inconclusivo.
7. Valide:
   - \(x_i \ge 0\);
   - \(\sum_i x_i = 1\);
   - finitude dos valores;
   - contagem canônica:
     \[
     |H| = \binom{H + m - 1}{m - 1}
     \]
8. Para duas camadas, valide externa e interna separadamente, incluindo o
   deslocamento da camada interna.
9. Não aplique esta validação a `pareto_fronts/csv/`, salvo se houver evidência
   de que algum arquivo naquele diretório é realmente um conjunto de pontos de
   Das & Dennis.

## Arquivos
- `reference-calc.java`
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/test/java/br/usp/lti/newmoea/qualityindicator/`
- `src/test/java/br/usp/lti/newmoea/algorithm/`
- `HANDLER.md`

## Critérios de aceitação
- Testes cobrem \(m \in \{2,3,5,8,10,15\}\).
- Testes validam contagem, dimensionalidade, soma, não negatividade e duplicatas.
- Testes comparam conjuntos independentemente da ordem.
- Testes reportam discrepâncias numéricas explícitas.
- Testes negativos rejeitam configurações inválidas.
- O resultado documenta se `reference-calc` é Das & Dennis canônico, variante ou
  método distinto.
- Nenhum arquivo de referência é sobrescrito nesta tarefa.

## Dependências
- T01 — Investigação de procedência.

## Referências
- Das & Dennis (1998), Normal-Boundary Intersection.
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
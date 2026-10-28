# T01 — Investigar origem, finalidade e fluxo dos artefatos de referência

## Objetivo
Identificar a origem, a finalidade, o formato e o fluxo de uso dos seguintes
artefatos:

- `reference-calc.java`;
- `MOEAD_Weights/`;
- `pareto_fronts/csv/`;
- mecanismos nativos de referência do jMetal 7.8.

## Contexto técnico
O pipeline principal é `WFGStudy.java`. Ele utiliza `MOEAD_Weights/` no builder
do MOEA/DD e `pareto_fronts/csv/` como diretório de frentes de referência.

É proibido assumir que `reference-calc.java` gerou os arquivos de
`MOEAD_Weights/`. A relação deve ser demonstrada por evidência.

## Distinções obrigatórias
1. **Vetores de pesos ou direções de referência**: usados por decomposição,
   especialmente no MOEA/D e no MOEA/DD.
2. **Pontos de referência do NSGA-III**: gerados por simplex-lattice design de
   Das & Dennis e usados no niching/seleção do NSGA-III.
3. **Frentes de Pareto de referência**: conjuntos de vetores de objetivos
   usados para avaliar aproximações por IGD, IGD+, HV e outros indicadores.

Esses três tipos de artefato não são equivalentes.

## Instruções
1. Leia `README.md`, `WFGStudy.java`, `StaticParams.java`,
   `reference-calc.java` e `pom.xml`.
2. Inspecione todos os arquivos em `MOEAD_Weights/` e `pareto_fronts/csv/`.
3. Identifique quais classes, métodos e chamadas consomem cada diretório ou
   arquivo.
4. Para `reference-calc.java`, determine:
   - o método implementado;
   - os parâmetros de entrada;
   - o formato de saída;
   - se gera pontos de referência, pesos ou outro artefato;
   - se há evidência de relação com arquivos existentes.
5. Para `MOEAD_Weights/`, determine:
   - arquivos existentes;
   - padrão de nomenclatura;
   - \(m\) e número de vetores de cada arquivo;
   - classe do jMetal responsável pelo carregamento;
   - compatibilidade com o MOEA/DD.
6. Para `pareto_fronts/csv/`, determine:
   - problemas cobertos;
   - valores de \(m\) cobertos;
   - formato;
   - indicadores que consomem cada frente;
   - se as frentes são analíticas, amostradas ou geradas por outra abordagem.
7. Investigue as APIs nativas do jMetal 7.8 para:
   - geração e carregamento de pesos do MOEA/D;
   - geração de pontos de referência do NSGA-III;
   - carregamento ou geração de frentes de Pareto.
8. Registre classe, pacote, método, assinatura, recurso e versão para cada
   funcionalidade nativa identificada.
9. Atualize `HANDLER.md` com a matriz de procedência.

## Arquivos
- `README.md`
- `pom.xml`
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/main/java/br/usp/lti/newmoea/study/StaticParams.java`
- `reference-calc.java`
- `MOEAD_Weights/`
- `pareto_fronts/csv/`
- `HANDLER.md`

## Critérios de aceitação
- Existe matriz de procedência para cada artefato.
- A finalidade de cada diretório está documentada.
- A origem está classificada como confirmada, provável, desconhecida ou
  inconclusiva.
- As APIs nativas do jMetal 7.8 estão documentadas com classe e método.
- Nenhum arquivo foi modificado.
- `HANDLER.md` foi atualizado.

## Dependências
Nenhuma. Esta tarefa deve preceder T02, T03 e T04.

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
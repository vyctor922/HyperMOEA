# T06 — Auditoria de operadores comuns e mutação polinomial

## Identificação
- Arquivo: `tasks/task6.md`
- Tarefa: T06
- Título: Auditoria de operadores comuns e mutação polinomial

## Objetivo
Confirmar a semântica real dos operadores comuns aplicados a todos os algoritmos, com prioridade para `PolynomialMutation`, e corrigir somente se houver incompatibilidade com a intenção experimental documentada.

## Contexto
O `HANDLER.md` afirma que a mutação polinomial usa probabilidade \(1/n\) e índice 20. Entretanto, `WFGStudy.configureAlgorithmList` contém:
```java
MutationOperator mutation =
    new PolynomialMutation(1.0, problem.numberOfVariables(), 20.0);
```

É necessário verificar se esse construtor realmente interpreta o primeiro argumento como probabilidade global, probabilidade por variável ou outro parâmetro. Não altere o código com base em suposição.

## Investigação
1. Localize a classe `org.uma.jmetal.operator.mutation.impl.PolynomialMutation` na dependência jMetal 7.8 declarada em `pom.xml`.
2. Inspecione o código-fonte, Javadoc ou bytecode disponível localmente.
3. Documente todas as assinaturas de construtor disponíveis.
4. Determine o significado exato de cada argumento na chamada atual.
5. Verifique se a probabilidade efetiva é:
   - 1.0;
   - \(1/n\);
   - \(1/n\) aplicada internamente;
   - outro valor.
6. Verifique se há validação de dominância de restrições ou tratamento especial de variáveis com limites.

## Procedimentos
1. Leia `HANDLER.md` e registre a tarefa como `in_progress`.
2. Inspecione `WFGStudy.java`, `StaticParams.java` e a API do jMetal 7.8.
3. Crie um teste unitário que evidencie a semântica real da mutação.
4. Se a chamada atual já produzir probabilidade \(1/n\), apenas documente isso.
5. Se a chamada atual produzir probabilidade 1.0, substitua por:
```java
MutationOperator mutation =
    new PolynomialMutation(1.0 / problem.numberOfVariables(), 20.0);
```

somente se essa for a assinatura correta para dois argumentos.

6. Se a assinatura de três argumentos tiver outro significado, ajuste a chamada para produzir probabilidade \(1/n\) usando a API correta.
7. Atualize `HANDLER.md` com a evidência encontrada.

## Arquivos envolvidos
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/test/java/br/usp/lti/newmoea/**`
- `HANDLER.md`
- `docs/operator-audit.md`

## Restrições
- Não altere NSGA-III, MOEA/DD, RVEA, FDEA, AGE-MOEA ou AGE-MOEA-II.
- Não altere pontos de referência, pesos ou frentes.
- Não altere a lista de algoritmos.
- Não introduza dependências novas.

## Critérios de aceitação
- A semântica de `PolynomialMutation` está documentada com evidência.
- Existe teste que valida a probabilidade efetiva ou o comportamento observável.
- `mvn clean test` passa.
- `HANDLER.md` registra a decisão final e a referência técnica.

## Testes
- Teste unitário do operador de mutação.
- Teste de integração leve com um problema de duas ou três objetivos.
- `mvn clean test`.

## Entregáveis
- `docs/operator-audit.md`.
- Teste automatizado.
- Atualização de `HANDLER.md`.
- Alteração mínima em `WFGStudy.java`, somente se necessária.

## Dependências
- Nenhuma.
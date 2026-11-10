# T07 — Revalidação de NSGA-III/RVEA, persistência opcional e frentes de Pareto

## Identificação
- Arquivo: `tasks/task7.md`
- Tarefa: T07
- Título: Revalidação de NSGA-III/RVEA, persistência opcional e frentes de Pareto

## Objetivo
Revalidar, como segunda opinião, os pontos de referência do NSGA-III e as direções do RVEA; implementar persistência opcional desses artefatos; inventariar as frentes oficiais do jMetal 7.8; e habilitar DTLZ somente para configurações com frente de referência tecnicamente válida.

## Contexto
O pipeline atual já configura NSGA-III com camadas explícitas e RVEA com vetores gerados por `ReferencePointGenerator`. Entretanto, a validação anterior não garante equivalência geométrica da camada interna do NSGA-III e não auditou explicitamente as direções do RVEA.

Além disso, DTLZ1–DTLZ7 só é adicionado ao experimento quando \(m=3\). A extensão para \(m \in \{5,8,10,15\}\) exige frentes de referência válidas.

## Distinção obrigatória
- **PF\***: frente de Pareto de referência, usada por IGD, IGD+, normalização e indicadores.
- **Reference points do NSGA-III**: pontos no simplex usados para niching.
- **Reference directions do RVEA**: direções unitárias ou normalizadas usadas pela APD e pela adaptação de vetores.
- **Weight vectors do MOEA/DD**: vetores de decomposição.

Esses artefatos não são intercambiáveis.

## Parte 1 — Revalidação do NSGA-III

### Investigação
1. Reinspecione `ReferencePointGenerator.generateSingleLayer` e `generateTwoLayers` no jMetal 7.8.
2. Reinspecione `NSGAIIIBuilder`, `NSGAIII` e a geração interna da segunda camada.
3. Compare três conjuntos, separadamente:
   - gerador canônico de Das & Dennis;
   - `ReferencePointGenerator`;
   - pontos efetivamente usados pelo `NSGAIIIBuilder`.
4. Para \(m \in \{2,3,5,8,10,15\}\), valide:
   - cardinalidade;
   - dimensionalidade;
   - soma igual a 1;
   - não negatividade;
   - duplicatas;
   - correspondência entre os conjuntos, desconsiderando a ordem das linhas, com tolerância numérica definida e registro dos pontos sem correspondência;
   - erro máximo por coordenada;
   - erro euclidiano máximo.
5. Para \(m \in \{8,10,15\}\), valide a camada externa e a interna separadamente.

### Decisão científica
- Se o `NSGAIIIBuilder` não for equivalente ao método canônico na camada interna, registre a diferença como limitação.
- Não substitua o gerador interno sem evidência de que a substituição é compatível com a API e com o comportamento do algoritmo.
- A revalidação deve servir de segunda opinião; ela não autoriza, por si só, alteração do algoritmo.

## Parte 2 — Revalidação do RVEA

### Investigação
1. Reinspecione `buildRVEA` em `WFGStudy.java`.
2. Confirme quais vetores são gerados para cada \(m\):
   - \(m=2\): `generateSingleLayer(2, 99)`;
   - \(m=3\): `generateSingleLayer(3, 12)`;
   - \(m=5\): `generateSingleLayer(5, 6)`;
   - \(m=8\): `generateTwoLayers(8, 3, 2)`;
   - \(m=10\): `generateTwoLayers(10, 3, 2)`;
   - \(m=15\): `generateTwoLayers(15, 2, 1)`.
3. Valide cardinalidade, dimensionalidade, soma, não negatividade, duplicatas e valores finitos.
4. Compare os vetores do RVEA com os pontos do NSGA-III apenas para verificar se são gerados pelo mesmo mecanismo; não os trate como artefatos idênticos em semântica.
5. Verifique se o `RVEABuilder` recebe exatamente `referenceVectors.size()` como número de vetores e a mesma lista gerada.

## Parte 3 — Persistência opcional

Implemente persistência **desativada por padrão** para auditoria e reprodutibilidade.

### Configuração
Use propriedades JVM:
```text
-DpersistReferencePoints=false
-DpersistReferenceDirections=false
```

Comportamento:
- `false`, padrão: o pipeline funciona exatamente como hoje, gerando tudo em
  memória.
- `true`: salva cópias somente para auditoria, sem alterar os vetores usados
  pelos algoritmos.

### Diretórios
- Pontos do NSGA-III:
```text
resources/referencePoints/nsgaiii/
```

- Direções do RVEA:
```text
resources/referencePoints/rvea/
```

### Nomenclatura
```text
NSGAIII_m{m}_H1{H1}_H2{H2}.csv
RVEA_m{m}_H1{H1}_H2{H2}.csv
```

Para uma camada, use `H2=0`.

### Formato
CSV com cabeçalho:
```text
index,f1,f2,...,fm
```

- Uma linha por vetor.
- Separador vírgula.
- Precisão suficiente para auditoria, por exemplo 15 casas decimais.
- Nunca sobrescreva arquivo existente automaticamente; use sufixo de execução ou
  falhe com mensagem clara.

## Parte 4 — Frentes de Pareto

### Procedimento obrigatório inicial
1. Inventarie as frentes distribuídas pelo jMetal 7.8 efetivamente resolvido
   pelo Maven, especialmente em `referenceFrontsCSV/`.
2. Para cada DTLZ1–DTLZ7 e \(m \in \{2,3,5,8,10,15\}\), registre:
   - existência;
   - número de pontos;
   - dimensionalidade;
   - intervalos dos objetivos;
   - duplicatas;
   - procedência;
   - hash SHA-256.
3. Compare com os arquivos existentes em `pareto_fronts/csv/`.
4. Não copie arquivos sem validar problema, \(m\), formato e integridade.

### Modo parcial — padrão
Mantenha a execução parcial atual: DTLZ habilitado somente para os \(m\) que já
possuem frente válida, hoje \(m=3\).

### Modo completo — opcional e desativado
Implemente a opção:
```text
-DgenerateAllReferenceFronts=false
```

- `false`, padrão: usa apenas as frentes disponíveis e validadas.
- `true`: tenta gerar todas as frentes DTLZ1–DTLZ7 para os \(m\) configurados.

A geração completa só pode habilitar um problema/\(m\) após validação matemática
bem-sucedida.

### Geração empírica — opcional e desativada
Implemente a opção:
```text
-DgenerateEmpiricalReferenceFronts=false
```

- `false`, padrão: não gera frentes empíricas.
- `true`: consolida soluções não dominadas de execuções prévias em:
```text
resources/referenceFronts/pf_gen/
```

Regras:
- Use somente quando não houver construção analítica ou frente oficial válida.
- Registre algoritmos, execuções, sementes, problemas e parâmetros usados.
- Nunca use a mesma execução simultaneamente para criar e avaliar a referência.
- Marque os arquivos como `empirical` na documentação e, se possível, no nome do
  diretório ou metadados.

### DTLZ — Diretrizes
| Problema | Geometria                                               | Método de geração                                        |
| -------- | ------------------------------------------------------- | -------------------------------------------------------- |
| DTLZ1    | Hiperplano                                              | Pontos no simplex multiplicados por 0,5                  |
| DTLZ2    | Hiperesfera                                             | Normalização dos pontos do simplex pela norma euclidiana |
| DTLZ3    | Hiperesfera                                             | Mesmo procedimento geométrico do DTLZ2                   |
| DTLZ4    | Hiperesfera                                             | Mesmo procedimento geométrico do DTLZ2                   |
| DTLZ5    | Geometria degenerada e possíveis componentes adicionais | Derivação da frente da formulação específica             |
| DTLZ6    | Geometria degenerada e possíveis componentes adicionais | Derivação da frente da formulação específica             |
| DTLZ7    | Superfície desconexa                                    | Amostragem dos componentes admissíveis                   |

Essa tabela é uma orientação de construção, não uma autorização para assumir que toda amostragem produz uma frente válida.

## DTLZ5/DTLZ6 - Exigências
1. Identifique a implementação exata de DTLZ5 e DTLZ6 presente no jMetal 7.8.
2. Derive as condições de Pareto-otimalidade dessa implementação.
3. Verifique se está trabalhando com a formulação original ou uma variante restrita.
4. Determine a dimensão e a geometria da frente para cada número de objetivos.
5. Gere os pontos considerando todos os componentes da frente verdadeira.
6. Teste a dominância e a consistência matemática dos pontos.

### DTLZ7 — subtarefa obrigatória
1. Inspecione a implementação exata de DTLZ7 no jMetal 7.8.
2. Identifique o número de variáveis n, o número de objetivos m e o parâmetro k utilizado.
3. Verifique a relação entre esses parâmetros na implementação e na formulação matemática.
4. Não introduza o parâmetro L sem identificar sua origem e sua necessidade.
5. Derive o domínio do último objetivo a partir da formulação efetivamente utilizada.
6. Não adote (1.1,...,1.1,2m) sem demonstrar que esse limite é coerente com a frente.
7. Implemente amostragem dos componentes admissíveis da frente desconexa.
8. Valide cobertura, ausência de valores inválidos, dominância e reprodutibilidade.
9. Registre limitações na documentação.

## Arquivos envolvidos
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/main/java/br/usp/lti/newmoea/study/StaticParams.java`
- `src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java`
- `src/main/java/br/usp/lti/newmoea/util/ParetoFrontValidator.java`
- `src/test/java/br/usp/lti/newmoea/**`
- `resources/referencePoints/`
- `resources/referenceFronts/pf_gen/`
- `pareto_fronts/csv/`
- `docs/reference-fronts-dtlz.md`
- `docs/reference-points-audit.md`
- `HANDLER.md`

## Restrições
- Não altere `MOEAD_Weights/`
- Não altere hipervolume
- Não habilite DTLZ para \(m\) sem frente validada.
- Não use geração empírica como método primário.
- Não sobrescreva frentes existentes.
- Não altere FDEA, AGE-MOEA, AGE-MOEA-II, SPEA2, ESPEA, GWASFGA ou MOEA/DD.

## Critérios de aceitação
- NSGA-III e RVEA são revalidados com relatório separado.
- A diferença da camada interna do NSGA-III, se confirmada, está documentada.
- A persistência é opcional e desativada por padrão.
- Nenhum vetor usado pelo algoritmo é alterado pela persistência.
- O inventário de frentes do jMetal 7.8 está completo.
- DTLZ7 possui análise específica.
- As opções de geração completa e empírica existem, mas estão desativadas.
- `mvn clean test` passa.
- `HANDLER.md` registra evidências, decisões e pendências.

## Testes
- Revalidação matemática do NSGA-III para todos os \(m\).
- Revalidação matemática do RVEA para todos os \(m\).
- Teste de persistência ativada e desativada.
- Teste de leitura, dimensionalidade e integridade das frentes.
- Teste específico de DTLZ7.
- Teste negativo para frente ausente, dimensionalidade incorreta ou arquivo
  inválido.
- Execução de fumaça com `-Druns=1`.

## Entregáveis
- `docs/reference-points-audit.md`.
- `docs/reference-fronts-dtlz.md`.
- Testes automatizados.
- Persistência opcional.
- Opções desativadas por padrão.
- Atualização de `HANDLER.md`.

## Dependências
- T06, para alterações no pipeline.
- Coordenação com T08 para evitar conflito em `WFGStudy.java`.
- T11 é independente, mas não deve alterar `WFGStudy.java` simultaneamente.
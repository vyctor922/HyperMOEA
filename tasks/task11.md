# T11 — Auditoria completa e sincronização dos pesos do MOEA/DD

## Identificação
- Arquivo: `tasks/task11.md`
- Tarefa: T11
- Título: Auditoria completa e sincronização dos pesos do MOEA/DD

## Objetivo
Auditar todos os arquivos de `MOEAD_Weights/` contra os recursos oficiais do
jMetal 7.8, copiar as contrapartes oficiais para um diretório canônico preservado
e oferecer, de forma opcional, a substituição controlada de componentes zero por
epsilon.


## Contexto
A validação anterior confirmou os seis arquivos efetivamente usados pelo
pipeline:
```text
W2D_100.dat
W3D_91.dat
W5D_210.dat
W8D_156.dat
W10D_275.dat
W15D_135.dat
```

Porém, `MOEAD_Weights/` contém outros arquivos que não foram todos comparados às
contrapartes do jMetal. Portanto, não é seguro afirmar que todo o diretório está
sincronizado com a fonte oficial.

## Princípios
1. `MOEAD_Weights/` é o diretório legado/atual do pipeline e deve ser preservado.
2. Os originais do jMetal devem ser copiados para:
```text
resources/weightVectorFiles/
```

3. Nenhum arquivo original do jMetal deve ser modificado.
4. Nenhum arquivo de `MOEAD_Weights/` deve ser substituído automaticamente.
5. A substituição de zeros por epsilon é opcional, deve ser usada apenas se realmente precisa de pesos estritamente maiores que zero e deve gerar artefatos novos,
   nunca alterar o original.

## Parte 1 — Inventário completo

Para **todos** os arquivos de `MOEAD_Weights/` e para todos os recursos de pesos
do jMetal 7.8:
1. Registre nome, tamanho, hash SHA-256, \(m\), número de vetores e formato.
2. Identifique correspondências por conteúdo, não apenas por nome.
3. Classifique cada arquivo como:
   - idêntico ao jMetal;
   - diferente do jMetal;
   - sem contraparte no jMetal;
   - arquivo legado sem consumo conhecido.
4. Para os seis arquivos ativos, repita a validação:
   - quantidade igual à população;
   - dimensionalidade igual a \(m\);
   - valores finitos;
   - não negatividade;
   - soma compatível com a tolerância do projeto;
   - ausência de duplicatas relevantes.
5. Produza o relatório `docs/moead-weights-audit.md`.

## Parte 2 — Cópia fiel do jMetal

1. Localize os recursos oficiais no artefato jMetal 7.8 resolvido pelo Maven,
   especialmente em:
```text
jmetal-core/src/main/resources/weightVectorFiles/moead/
```

2. Copie os arquivos originais para:
```text
resources/weightVectorFiles/
```

3. Preserve nomes, conteúdo, codificação e formato.
4. Gere manifesto com:
```text
sha256,original_jmetal_path,copied_path,m,vectors,status
```

5. Não altere nenhum byte dos arquivos copiados.

## Parte 3 — Substituição opcional de zeros

Implemente uma opção **desativada por padrão**:
```text
-DreplaceZeroWeights=false
```

Comportamento:
- `false`, padrão: não gera variantes com epsilon. Os arquivos oficiais continuam sendo copiados (caso já não estejam na pasta weightVectorFiles/) fielmente e validados na Parte 2.
- `true`: gera variantes com substituição \(0.0 \mapsto 1.0 \times 10^{-5}\) em:
```text
resources/weightVectorFiles/weights_gen/
```

Regras:
- Nunca modifique o arquivo original do jMetal.
- Nunca modifique automaticamente `MOEAD_Weights/`.
- Aplique a substituição apenas a arquivos de pesos de decomposição.
- Preserve a dimensionalidade, a cardinalidade e a ordem dos vetores.
- Registre, para cada arquivo gerado, o número de componentes alterados.
- Recalcule e registre a soma de cada vetor após a substituição.
- Valide finitude, não negatividade, ausência de duplicatas e compatibilidade com
  o formato esperado pelo jMetal.

### Validação matemática das variantes com epsilon

A substituição de zeros por epsilon não deve ser considerada automaticamente necessária ou equivalente. Mas caso o programador deseje, ele pode explicitar em código que deseja aplicar a substituição para todos os arquivos.

Antes de utilizar os pesos modificados:
1. Inspecione a função de escalarização efetiva.
2. Identifique operações que possam dividir por zero.
3. Verifique se componentes zero são matematicamente válidos para o algoritmo.
4. Identifique quais arquivos realmente precisam de pesos estritamente positivos.
5. Não aplique a transformação indiscriminadamente a todos os arquivos.
6. Avalie a necessidade de renormalização.
7. Compare as variantes com os vetores originais.

A propriedade `replaceZeroWeights=true` somente poderá gerar variantes para os arquivos identificados como necessários após a auditoria.
A geração de variantes não autoriza sua utilização automática pelo pipeline. A substituição do diretório ativo exige decisão documentada e teste de integração.

## Parte 4 — Integração com o pipeline

1. Não altere o diretório padrão do MOEA/DD nesta tarefa.
2. Mantenha `MOEAD_Weights/` como diretório ativo até que a auditoria completa seja aprovada.
3. Somente após aprovação explícita da auditoria, o coordenador poderá autorizar a migração do pipeline para `resources/weightVectorFiles/`. Caso exista justificativa técnica para utilizar variantes com epsilon, poderá autorizar separadamente o uso de `resources/weightVectorFiles/weights_gen/` nos algoritmos que os necessitarem. Os dois diretórios devem permanecer independentes. O pipeline deve ter uma origem ativa explicitamente definida, sem mistura ou fallback silencioso entre arquivos originais e modificados.
4. Se a migração ocorrer, atualize `buildMOEADD` e `buildMOEADDRA` conjuntamente, com teste de integração.


## Arquivos envolvidos
- `MOEAD_Weights/`
- `resources/weightVectorFiles/`
- `jmetal-core/src/main/resources/weightVectorFiles/moead/`
- `resources/weightVectorFiles/weights_gen/`
- `src/main/java/br/usp/lti/newmoea/util/ReferenceSetValidator.java`
- `src/test/java/br/usp/lti/newmoea/**`
- `docs/moead-weights-audit.md`
- `docs/moead-weights-manifest.csv`
- `HANDLER.md`

## Restrições
- Não altere `WFGStudy.java`, exceto se o coordenador aprovar explicitamente a
  migração de diretório.
- Não altere frentes de Pareto.
- Não altere pontos do NSGA-III ou direções do RVEA.
- Não remova arquivos de `MOEAD_Weights/`.
- Não substitua arquivos do jMetal.
- Identifique primeiro se a implementação do algoritmo x ou y realmente precisa de pesos estritamente maiores que zero.
- Não aplique epsilon de forma indiscriminada a outros artefatos.

## Critérios de aceitação
- Todos os arquivos de `MOEAD_Weights/` foram inventariados.
- Todos os recursos de pesos do jMetal 7.8 foram inventariados.
- As contrapartes oficiais estão em `resources/weightVectorFiles/`.
- O manifesto registra hashes e status de cada arquivo.
- A opção `replaceZeroWeights` existe e está desativada por padrão.
- Nenhum arquivo original foi modificado.
- `mvn clean test` passa.
- `HANDLER.md` registra o inventário, as diferenças e as pendências.

## Testes
- Teste de leitura e validação de todos os arquivos ativos.
- Teste de cópia fiel e verificação de hash.
- Teste da substituição opcional de zeros.
- Teste negativo para arquivo com dimensionalidade incorreta.
- Teste negativo para arquivo com cardinalidade incorreta.
- Teste negativo para arquivo com valores NaN ou infinitos.
- Teste de integração com `-Druns=1`, sem alterar o diretório padrão.

## Entregáveis
- `docs/moead-weights-audit.md`.
- `docs/moead-weights-manifest.csv`.
- `resources/weightVectorFiles/`.
- `resources/weightVectorFiles/weights_gen/`.
- Testes automatizados.
- Atualização de `HANDLER.md`.

## Dependências
- Nenhuma dependência obrigatória de T06.
- Não deve executar em paralelo com T07 se ambas precisarem alterar `WFGStudy.java`.
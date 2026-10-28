# Prompt — Padronização experimental, procedência de artefatos e validação matemática

## Papel
Atue como pesquisador especialista em otimização multiobjetivo, algoritmos
evolutivos, NSGA-III, MOEA/DD, RVEA, engenharia de software científico e
validação experimental reprodutível.

## Contexto do projeto
O projeto NewMOEA utiliza:
- Java 21;
- Maven;
- jMetal 7.8;
- pipeline principal em `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`;
- problemas WFG1–WFG9 e DTLZ1–DTLZ7;
- \(m \in \{2, 3, 5, 8, 10, 15\}\);
- 30 execuções independentes por padrão;
- indicadores PISAHypervolume, NormalizedHypervolume, IGD, IGD+ e R2.

Os algoritmos efetivamente adicionados ao experimento são:
1. NSGA-III;
2. MOEA/DD;
3. FDEA;
4. RVEA;
5. AGE-MOEA;
6. AGE-MOEA-II.

Outros builders existem no arquivo, mas não fazem parte da lista experimental
atual: NSGA-II, SPEA2, MOEA/DD-RA, GWASFGA e ESPEA.

## Objetivo
Padronizar a configuração experimental e validar a procedência, a finalidade e a
correção matemática dos artefatos utilizados pelo pipeline, antes de qualquer
substituição ou alteração de arquivos de referência.

## Regras obrigatórias
1. Leia `README.md`, `WFGStudy.java`, `StaticParams.java`, `reference-calc.java`,
   `Main-Hipervolume.java` e, se existir, `HANDLER.md` antes de alterar código.
2. Consulte o código-fonte e os recursos efetivamente distribuídos com o jMetal
   7.8 declarado no `pom.xml`. Não considere funcionalidades de outras versões.
3. Não presuma a origem de `MOEAD_Weights/`, `pareto_fronts/csv/` ou
   `reference-calc.java`. Investigue comentários, histórico disponível, chamadas
   do pipeline, formatos de arquivo e APIs do jMetal.
4. Não sobrescreva, regenere ou remova arquivos de referência sem evidência,
   justificativa documentada e validação prévia.
5. Não trate como equivalentes conjuntos que possuam apenas a mesma quantidade de
   linhas, dimensão ou soma aproximada.
6. Não confunda:
   - vetores de pesos ou direções de referência usados por decomposição;
   - pontos de referência do NSGA-III gerados por simplex-lattice design;
   - frentes de Pareto de referência dos problemas WFG e DTLZ.
7. Não execute o experimento completo antes de validar o pipeline com
   `-Druns=1`, um problema e um número de objetivos.
8. Preserve as decisões já estabelecidas, exceto se nova evidência verificável
   justificar revisão explícita.

## Decisões científicas já estabelecidas

### População e orçamento
Mantenha as populações canônicas por número de objetivos:

| m | População |
|---:|---:|
| 2 | 100 |
| 3 | 91 |
| 5 | 210 |
| 8 | 156 |
| 10 | 275 |
| 15 | 135 |

O orçamento comum permanece:

\[
\text{maxEvaluations} = \text{maxGenerations} \times \text{populationSize}
\]

### Operadores comuns
- SBX com probabilidade 1.0 e índice de distribuição 30.0.
- Mutação polinomial com índice de distribuição 20.0.
- Probabilidade de mutação: \(1/n\), em que \(n\) é o número de variáveis.
- Antes de alterar `PolynomialMutation`, audite a assinatura exata no jMetal 7.8.
  Se a chamada atual já tiver essa semântica, não a modifique.

### NSGA-III
Use camadas explícitas de Das & Dennis:

| m | Divisões externas | Divisões internas | Total esperado |
|---:|---:|---:|---:|
| 2 | 99 | 0 | 100 |
| 3 | 12 | 0 | 91 |
| 5 | 6 | 0 | 210 |
| 8 | 3 | 2 | 156 |
| 10 | 3 | 2 | 275 |
| 15 | 2 | 1 | 135 |

A divisão interna não é `divisions / 2`. Ela é um parâmetro independente \(H_2\).

### MOEA/DD
Mantenha:
- `MOEADBuilder.Variant.MOEADD`;
- PBI;
- \(T = 20\);
- \(\delta = 0.9\);
- \(nr = 2\);
- população igual ao número de vetores de pesos carregados.

Valide o arquivo de pesos antes da execução. Se a API do jMetal 7.8 expuser o
parâmetro \(\theta\) do PBI, considere \(\theta = 5\), registrando a decisão.
Caso não exponha, mantenha o padrão da biblioteca e registre a limitação.

### RVEA, FDEA, AGE-MOEA e AGE-MOEA-II
Não altere mecanismos internos sem evidência. Mantenha:
- RVEA com \(\alpha = 2.0\) e \(f_r = 0.1\);
- FDEA conforme o adaptador e a referência original;
- AGE-MOEA e AGE-MOEA-II com população, operadores e orçamento comuns.

### Hipervolume
- `PISAHypervolume` é a métrica primária.
- `NormalizedHypervolume` é métrica secundária.
- Use o mesmo ponto de referência para todos os algoritmos de um mesmo problema
  e número de objetivos.
- Para WFG, use \((2, \ldots, 2)\), salvo evidência contrária no protocolo.
- Para DTLZ, use \((1, \ldots, 1)\), salvo evidência contrária no protocolo.
- Não integre `Main-Hipervolume` ao pipeline sem corrigir semente, normalização,
  direção da métrica e ponto de referência.
- Não misture HV bruto e HV normalizado em uma mesma análise estatística.

## Fase 1 — Investigação de procedência

Para cada artefato, produza um registro em `HANDLER.md` contendo:

1. Finalidade real no pipeline.
2. Origem conhecida, provável ou desconhecida.
3. Formato esperado.
4. Algoritmo ou componente que o consome.
5. Parâmetros de geração, quando identificáveis.
6. Evidências encontradas.
7. Incertezas remanescentes.

### A. `reference-calc.java`
Determine:
- se implementa o método de Das & Dennis;
- se implementa uma variante própria;
- quais parâmetros utiliza;
- quais conjuntos gera;
- se gera pontos de referência, vetores de pesos ou outro artefato;
- se há correspondência com arquivos existentes em `MOEAD_Weights/`.

Não assuma que `reference-calc.java` gerou os arquivos de pesos apenas porque
ambos representam vetores numéricos.

### B. `MOEAD_Weights/`
Para cada arquivo relevante:
- identifique o padrão de nome;
- determine \(m\), número de vetores e dimensionalidade;
- valide finitude, não negatividade e soma dos componentes, quando exigido pelo
  método;
- identifique duplicatas exatas e quase duplicatas;
- verifique se a quantidade corresponde à população configurada;
- determine qual classe do jMetal carrega o arquivo;
- compare o conteúdo com o `reference-calc`, se houver evidência de relação.

### C. `pareto_fronts/csv/`
Para cada frente utilizada:
- identifique o problema, \(m\) e formato;
- confirme qual indicador ou componente a consome;
- verifique se é uma frente de Pareto de referência e não um conjunto de pesos
  ou pontos do NSGA-III;
- valide contra a definição analítica, gerador apropriado ou fonte científica
  confiável da respectiva frente;
- não compare frentes de Pareto contra Das & Dennis.

## Fase 2 — Validação matemática

### Das & Dennis
Para pontos de referência que devem seguir o simplex-lattice design, valide:

\[
x_i \ge 0, \quad \sum_{i=1}^{m} x_i = 1
\]

e a contagem canônica:

\[
|H| = \binom{H + m - 1}{m - 1}
\]

Para duas camadas, valide separadamente:
- camada externa com \(H_1\);
- camada interna com \(H_2\);
- deslocamento correto da camada interna;
- contagem total;
- ausência de duplicatas;
- equivalência de conjuntos, independentemente da ordem das linhas.

Use tolerância numérica explícita, por exemplo \(10^{-6}\), justificando-a em
relação à precisão de escrita dos arquivos e ao arredondamento do gerador.

A comparação deve reportar:
- número de vetores em cada conjunto;
- dimensionalidade;
- vetores sem correspondência;
- duplicatas;
- maior erro absoluto por coordenada;
- maior erro euclidiano entre vetores correspondentes;
- conclusão: equivalente, não equivalente ou inconclusivo.

### MOEA/DD
Não exija que os pesos sigam Das & Dennis. Verifique apenas:
- compatibilidade com o formato esperado pelo jMetal;
- quantidade igual à população;
- dimensionalidade igual a \(m\);
- restrições matemáticas exigidas pelo método de decomposição;
- ausência de duplicatas relevantes;
- correspondência com o gerador identificado, se aplicável.

## Fase 3 — Recursos nativos do jMetal 7.8

Investigue separadamente:

### A. MOEA/D e MOEA/DD
Verifique se o jMetal 7.8 oferece:
- geração dinâmica de vetores de pesos;
- carregamento de arquivos de pesos;
- recursos pré-distribuídos;
- utilitários de validação;
- alternativas ao diretório `MOEAD_Weights/`.

Registre classe, pacote, método, assinatura, recurso e forma de uso.

### B. NSGA-III
Verifique se o jMetal 7.8 fornece:
- gerador nativo baseado em Das & Dennis;
- suporte a uma camada;
- suporte a duas camadas;
- contagem e validação dos pontos;
- uso no `NSGAIIIBuilder`;
- possibilidade de usar o gerador como referência independente nos testes.

### C. Frentes de Pareto
Verifique se o jMetal 7.8 disponibiliza:
- frentes para WFG1–WFG9;
- frentes para DTLZ1–DTLZ7;
- utilitários para carregar frentes;
- utilitários para gerar frentes, quando existirem.

Diferencie explicitamente:
- API para carregar arquivo de frente;
- recurso pré-distribuído;
- gerador analítico da frente.

## Fase 4 — Implementação

Somente após as fases anteriores:

1. Corrija `buildNSGAIII`, substituindo `divisions / 2` por camadas explícitas.
2. Adicione validação do tamanho populacional do NSGA-III.
3. Adicione validação do arquivo de pesos do MOEA/DD.
4. Padronize o ponto de referência do hipervolume.
5. Mantenha `PISAHypervolume` como métrica primária.
6. Mantenha `NormalizedHypervolume` como métrica secundária.
7. Não substitua `MOEAD_Weights/` ou `pareto_fronts/csv/` sem evidência de que a
   alternativa nativa é matematicamente equivalente e compatível.

## Testes exigidos

Crie testes independentes do pipeline completo, sempre que possível.

### Testes de pesos do MOEA/DD
- quantidade correta para cada \(m\);
- dimensionalidade correta;
- valores finitos;
- valores não negativos, quando exigido;
- soma adequada, quando exigido;
- ausência de duplicatas relevantes;
- falha controlada para arquivo com dimensão incorreta;
- falha controlada para arquivo com quantidade incorreta;
- falha controlada para arquivo ausente.

### Testes de NSGA-III
- contagem correta para cada \(m\);
- soma dos componentes igual a 1 dentro da tolerância;
- não negatividade;
- ausência de duplicatas;
- equivalência com Das & Dennis;
- equivalência com o gerador nativo do jMetal, quando disponível;
- falha controlada para configuração inválida de camadas.

### Testes de frentes de Pareto
- existência do arquivo;
- dimensionalidade correta;
- ausência de valores inválidos;
- compatibilidade com o problema e \(m\);
- validação contra a fonte apropriada da frente;
- falha controlada para arquivo ausente ou incompatível.

### Testes negativos
Os testes devem provar que arquivos incompatíveis são detectados e rejeitados
com mensagem clara, em vez de aceitos silenciosamente.

## Critérios de aceitação

A tarefa somente pode ser considerada concluída se:

1. `mvn clean test` for executado com sucesso.
2. A procedência de cada artefato estiver documentada ou explicitamente marcada
   como não confirmada.
3. Os arquivos utilizados tiverem sido comparados aos geradores identificados.
4. A validação contra Das & Dennis tiver sido executada para os conjuntos aos
   quais ela se aplica.
5. As alternativas nativas do jMetal 7.8 tiverem sido investigadas e documentadas.
6. Os testes cobrirem todos os \(m \in \{2,3,5,8,10,15\}\).
7. Testes negativos comprovarem a rejeição de arquivos incompatíveis.
8. Nenhum arquivo de referência tiver sido alterado sem justificativa e
   rastreabilidade.
9. `HANDLER.md` estiver atualizado com decisões, evidências, testes, limitações
   e pendências.

## Comandos de validação

```bash
mvn clean compile
mvn test
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=1 -Dobjectives=2 -Dproblem=WFG1
mvn exec:java -Dexec.mainClass="br.usp.lti.newmoea.study.WFGStudy" -Druns=1 -Dobjectives=3 -Dproblem=DTLZ2
```

## Entrega esperada
- Código alterado apenas quando necessário.
- Testes automatizados.
- Relatório de procedência.
- Relatório de equivalência matemática.
- Recomendação fundamentada sobre manter, validar, substituir ou gerar
  dinamicamente cada artefato.
- Atualização completa do `HANDLER.md`.
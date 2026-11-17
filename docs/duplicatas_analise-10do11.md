# Relatório Forense: Análise de Relevância do ArtifactResolver, Mapeamento de Duplicatas, Fallbacks Legados e Auditoria de Recursos do jMetal

**Data da Auditoria:** 10 de Novembro de 2026  
**Projeto:** hypeMOEA  
**Objetivo:**
1. Avaliar a relevância e pertinência técnica da classe `br.usp.lti.newmoea.util.ArtifactResolver` após as últimas alterações estruturais, emitindo recomendação técnica fundamentada.
2. Mapear exaustivamente todas as implementações no projeto que realizam funções semelhantes ou redundantes (duplicações conceituais e estruturais).
3. Auditar a presença de rotinas de *fallback* direcionadas para as pastas delimitadas como somente leitura (`MOEAD_Weights/` e `pareto_fronts/csv/`).
4. Auditar e comparar minuciosamente os arquivos distribuídos pelo jMetal (versão 7.8 e última versão `upstream/main`) com a pasta `resources/` local do projeto.

---

## 1. Avaliação de Relevância do `ArtifactResolver.java`

### 1.1 Diagnóstico do Uso Atual
A classe `ArtifactResolver.java` foi originalmente concebida no contexto da **Task 18 (T18)** com o objetivo de centralizar a resolução canônica de caminhos de arquivos de pesos e frentes de Pareto, verificar integridade via SHA-256 em tempo de preflight e gerar o manifesto consolidado de artefatos (`artifact-manifest.csv`).

Entretanto, após as recentes refatorações nas quais:
1. `WFGStudy.java` passou a integrar algumas resoluções canônicas via:
   - `WFGStudy.resolveReferenceFrontForStudy(...)`
   - `WFGStudy.resolveMoeadDataDirectory(...)`
   - `ParetoFrontValidator.resolveCanonicalReferenceFront(...)`
2. `PerformanceProfiler.java` adotou seu próprio método de resolução local (`PerformanceProfiler.resolveReferenceFrontPath(...)`),

**nenhuma classe do pacote de produção (`src/main/java`) utiliza ou referencia `ArtifactResolver.java`**.

As únicas referências ativas à classe encontram-se em:
- `src/test/java/br/usp/lti/newmoea/util/ArtifactResolverTest.java` (teste unitário específico da própria classe);
- `src/test/java/br/usp/lti/newmoea/qualityindicator/ReferenceFrontCanonicalPriorityTest.java` (onde foi importada pontualmente para testar a enumeração `FrontSourcePolicy`).

### 1.2 Por que ela ainda possui valor (ou por que deve ser mantida / refatorada)?
Ao confrontar detalhadamente a implementação de [`ArtifactResolver.java`](util/ArtifactResolver.java) com [`WFGStudy.java`](study/WFGStudy.java) e [`ParetoFrontValidator.java`](util/ParetoFrontValidator.java), sob a ótica da prioridade canônica `resources/.../` $\to$ `resources/.../**_gen/` $\to$ Geração por código, identifica-se **o que já foi absorvido**, **o que é redundante** e **quais elementos específicos ainda são muito úteis para reaproveitamento**.

---

### 1.2.1. Elementos Úteis do `ArtifactResolver` para Incorporação

#### A. A regra de geração em memória do jMetal (`isMoeadWeightGeneratedInMemory`)
* **O que faz no `ArtifactResolver` (linhas 369–383):**
  Documenta e valida que o jMetal 7.8 (`AbstractMOEAD.initializeUniformWeight()`) **não lê o disco** para $m=2$ e $N \le 300$, gerando os vetores internamente via fórmula direta.
* **Utilidade para o `WFGStudy`:**
  Atualmente, em [`WFGStudy.validateMoeadWeightFile`](study/WFGStudy.java#L611-L624) e no `preflightStudy`, o código exige a existência física de `W2D_100.dat` no disco, mesmo sabendo que o jMetal descarta esse arquivo em tempo de execução. Incorporar essa checagem no `WFGStudy` tornaria o preflight mais preciso cientificamente e evitaria falhas desnecessárias caso um arquivo de 2 objetivos não esteja no disco.

#### B. Modelo de Retorno Tipado e Rico (`ResolvedWeightArtifact` e `ResolvedFrontArtifact`)
* **O que faz no `ArtifactResolver` (linhas 158–200):**
  Em vez de retornar apenas uma `String` ou `Path`, retorna um `record` imutável contendo:
  - O caminho normalizado (`path`);
  - A categoria de proveniência (`sourceCategory`: se veio da raiz de `resources`, se veio da `_gen`);
  - O hash SHA-256 já calculado;
  - A contagem de pontos e pontos únicos (`totalPoints`, `uniquePoints`);
  - Se será executado em memória ou via disco (`usedInMemoryByMoead`).
* **Utilidade para `ParetoFrontValidator` e `WFGStudy`:**
  Hoje, [`ParetoFrontValidator.resolveCanonicalReferenceFront`](util/ParetoFrontValidator.java#L1296-L1359) retorna apenas um `Path`, e `WFGStudy.resolveReferenceFrontForStudy` converte para `String`. Se o `ParetoFrontValidator` retornar um objeto estruturado (ou se `WFGStudy` aproveitasse essa estrutura), o preflight e o log de inicialização do estudo poderiam registrar com precisão forense no console qual fonte exata alimentou cada problema, além de validar o hash SHA-256 no mesmo instante da resolução.

#### C. Isolamento de Diretórios para Testes Herméticos (Injeção de `baseDir`)
* **O que faz no `ArtifactResolver` (linhas 288–361):**
  Permite instanciar o resolvedor recebendo um `Path baseDir` customizado (ex.: diretórios `@TempDir` do JUnit).
* **Utilidade para `WFGStudy`:**
  No `WFGStudy`, métodos como `resolveMoeadDataDirectory` e `resolveReferenceFrontForStudy` dependem de constantes estáticas globais (`DEFAULT_REFERENCE_FRONT_DIRECTORY`, `DEFAULT_WEIGHTS_DIRECTORY`). Permitir a passagem de diretórios base facilitaria testes isolados sem necessidade de alterar propriedades globais da JVM (`System.setProperty`).

#### D. Auditoria em Lote e Exportação de Manifesto (`ArtifactManifestEntry`)
* **O que faz no `ArtifactResolver` (linhas 218–254 e 704–840):**
  Método `exportActiveArtifactManifestCsv(Path manifestPath)`, que varre as instâncias de frentes e os arquivos de peso ativos, gerando um CSV completo de auditoria com metadados para reproducibilidade.
* **Utilidade:**
  Esse método pode ser transferido como um utilitário estático para [`ParetoFrontValidator.java`](util/ParetoFrontValidator.java) ou [`ReferenceSetValidator.java`](util/ReferenceSetValidator.java), permitindo que o `WFGStudy` emita automaticamente um manifesto de todos os recursos utilizados antes de rodar os algoritmos.

---

### 1.2.2. O que NÃO deve ser aproveitado (Redundante ou Desatualizado)

1. **Políticas em Enum (`FrontSourcePolicy.OFFICIAL_CSV_ONLY`, etc.):**
   - O `ArtifactResolver` ainda mantém as políticas legadas da T18 (`OFFICIAL_CSV_ONLY`, `OFFICIAL_THEN_ANALYTICAL`), que amarravam frentes oficiais exclusivamente à pasta `pareto_fronts/csv/` que é uma pasta legada em sem permissão para ser usada nos experimentos. Isso colide com a hierarquia canônica atual onde `resources/referenceFronts/` é a fonte prioritária.
2. **Duplicação da busca de frentes:**
   - O método `ArtifactResolver.resolveFront(...)` replica internamente os passos que [`ParetoFrontValidator.resolveCanonicalReferenceFront(...)`](util/ParetoFrontValidator.java#L1296) já faz com suporte nativo a `pf_lowzero_gen/`. Essa duplicação não deve ser mantida.
3. Utilização de fallback das pastas legadas
	- Apenas deve ser usado a prioridade para acessar caminhos de arquivos, a prioridade cânonica definida `resources/.../` $\to$ `resources/.../**_gen/` $\to$ Geração por código

---

### 1.2.3. Síntese do Reaproveitamento Recomendado

Para deixar o projeto coeso e sem classes órfãs:
1. **No `ParetoFrontValidator.java`:**
   - Absorver a capacidade de auditoria e exportação de manifesto CSV (`exportActiveArtifactManifestCsv`) e a geração do registro estruturado de artefatos com hash SHA-256.
2. **No `WFGStudy.java`:**
   - Absorver a regra `isMoeadWeightGeneratedInMemory` no preflight do MOEA/DD para tratar $m=2, N=100$ adequadamente.
3. **Descontinuação do `ArtifactResolver.java`:**
   - Uma vez transferidas essas duas funções específicas (manifesto e regra em memória), o arquivo [`ArtifactResolver.java`](util/ArtifactResolver.java) e seu teste [`ArtifactResolverTest.java`] tornam-se 100% obsoletos e podem ser removidos com segurança sem impacto no estudo.

---

## 2. Mapeamento de Implementações Duplicadas ou Concorrentes no Projeto

A inspeção detalhada de todos os arquivos do código-fonte revelou 4 pontos críticos de duplicatas conceituais e sobreposição de responsabilidades:

### 2.1 Resolução de Caminhos de Frentes de Pareto (Quatro Pontos de Implementação e Três Núcleos de Resolução)
Existem atualmente **três implementações distintas** da mesma lógica de localização hierárquica de frentes de Pareto:
1. `ParetoFrontValidator.resolveCanonicalReferenceFront(...)`:
   - Prioridade: `resources/referenceFronts/` $\to$ `pf_gen/` $\to$ Geração por código $\to$ Fallback `pareto_fronts/csv/`. (atualmente incorreta por conter fallback para pasta legada)
2. `WFGStudy.resolveReferenceFrontForStudy(...)`:
   - Envolve a chamada a `ParetoFrontValidator.resolveCanonicalReferenceFront`, mas adiciona lógica própria de `relativize` com base no `study.referenceFrontDirectory`.
3. `PerformanceProfiler.resolveReferenceFrontPath(...)`:
   - Reimplementa do zero a checagem manual: `resources/referenceFronts/` $\to$ `resources/referenceFronts/pf_gen/` $\to$ `pareto_fronts/csv/`.
4. `ArtifactResolver.resolveFront(...)`:
   - Quarta variante, baseada em enum `FrontSourcePolicy` com resolução manual por switches.

> **Impacto:** Qualquer alteração na estrutura de diretórios ou regras de carregamento precisa ser replicada em três ou quatro locais diferentes, aumentando o risco de inconsistências.

### 2.2 Resolução e Validação de Diretório de Pesos do MOEA/DD
1. `WFGStudy.resolveMoeadDataDirectory(...)`:
   - Varre: `baseWeightsDir` $\to$ `weights_gen/` $\to$ `MOEAD_Weights/`.
2. `ArtifactResolver.resolveWeightVectorFile(...)`:
   - Varre: `officialWeightsDir` $\to$ `generatedWeightsDir` $\to$ `legacyWeightsDir`.
3. `EvaluationBudgetProblem.java`:
   - Força diretamente no código strings literais `"MOEAD_Weights"` em chamadas de validação e instanciação, ignorando a resolução dinâmica de `resources/weightVectorFiles`.

### 2.3 Geração de Simplex de Das & Dennis e de Pontos de Referência
1. `ReferenceSetValidator.generateCanonicalSingleLayer(...)` e `ReferenceSetValidator.generateCanonicalTwoLayers(...)`:
   - Implementa a geração combinatorial recursiva padrão de Das & Dennis para vetores no simplex unitário.
2. `ReferencePointGenerator.java` (jMetal / projeto):
   - Métodos estáticos auxiliares com objetivos idênticos chamados em `WFGStudy.buildRVEA(...)` para gerar camadas simples e duplas de vetores de referência.
3. `ParetoFrontValidator.generateAnalyticalDtlz1(...)` e `ParetoFrontValidator.generateAnalyticalDtlz2To4(...)`:
   - Reutilizam os pontos gerados por `ReferenceSetValidator.generateCanonicalTwoLayers(...)`, mas realizam projeção geométrica própria.

### 2.4 Verificação de Não-Dominância e Filtragem de Pareto
1. `ParetoFrontValidator.countDominatedPoints(...)` e `ParetoFrontValidator.assertNonDominatedFront(...)`:
   - Implementação otimizada $O(N \cdot U)$ para contagem e assertiva de pontos dominados.
2. `ResultFrontContract.java`:
   - Implementa sua própria rotina de verificação de soluções não-dominadas e dominância estrita ao validar o arquivo de saída `FUN.csv`.

---

## 3. Pesquisa de *Fallback* nas Pastas Delimitadas como 'legadas'

As pastas `MOEAD_Weights/` e `pareto_fronts/csv/` foram expressamente delimitadas como **dados legados** do laboratório, sendo então pastas **somente leitura (*read-only*)** para preservar o histórico e proibidas como fontes de entrada dos experimentos, contudo as mesmas ainda assim são usadas no experimento.

A varredura estrita em todo o código-fonte identificou as seguintes ocorrências de leitura direta e mecanismos de *fallback* direcionados a essas pastas:

### 3.1 Fallbacks em `pareto_fronts/csv/`
1. **`ParetoFrontValidator.java` (Linhas 1349–1353):**
   ```java
   // Fallback legado em pareto_fronts/csv/ caso exista
   Path legacyFile = Path.of("pareto_fronts", "csv", fileName);
   if (Files.isRegularFile(legacyFile)) {
       return legacyFile;
   }
   ```
   *Diagnóstico:* Atua como último recurso quando o arquivo não está em `resources/referenceFronts/`, não está em `pf_gen/` e não pode ser gerado por código.
2. **`WFGStudy.java` (Linhas 661–667 - `runPreflightValidation`):**
   ```java
   Path refPath = Paths.get(getReferenceFrontDirectory(), refFront);
   if (!Files.exists(refPath)) {
       Path fallbackPath = Paths.get("pareto_fronts", "csv", refFront);
       if (!Files.exists(fallbackPath)) { ... }
   }
   ```
   *Diagnóstico:* Preflight de leitura que consulta `pareto_fronts/csv/` se o caminho configurado falhar.
3. **`PerformanceProfiler.java` (Linha 851):**
   - Consulta `pareto_fronts/csv/` como terceiro nível de busca de leitura.
4. **`ArtifactResolver.java`:**
   - Define `DEFAULT_OFFICIAL_FRONTS_DIR = Path.of("pareto_fronts", "csv")` para as políticas `OFFICIAL_CSV_ONLY` e fallback de `CANONICAL_RESOURCE_PRIORITY`.

### 3.2 Fallbacks em `MOEAD_Weights/`
1. **`WFGStudy.java` (Linhas 569–572 - `resolveMoeadDataDirectory`):**
   ```java
   // 3. Fallback legado MOEAD_Weights/
   Path p3 = Paths.get(DEFAULT_LEGACY_WEIGHTS_DIRECTORY, fileName);
   if (Files.exists(p3)) {
       return DEFAULT_LEGACY_WEIGHTS_DIRECTORY;
   }
   ```
   *Diagnóstico:* Crucial para os casos de $m=5$ ($N=210$) e $m=15$ ($N=135$). Como o jMetal 7.8 oficial distribui apenas 29 arquivos e não inclui essas cardinalidades no Git, o código recorre forçadamente à leitura de `MOEAD_Weights/` para evitar que o algoritmo aborte, descumprindo a regra de não uso de pastas legadas.
2. **`ArtifactResolver.java` (Linhas 424–430):**
   - Na política `OFFICIAL_JMETAL_RAW`, utiliza `sourceCategory = "FALLBACK_LEGACY_NO_JMETAL_COUNTERPART"` apontando para `MOEAD_Weights/`.
3. **`EvaluationBudgetProblem.java` (Linhas 673, 684):**
   - Força leitura direta em `"MOEAD_Weights"`.

---

## 4. Resumo Executivo e Próximas Ações

1. **Relevância de `ArtifactResolver.java`:**
   - **Veredito:** Atualmente desacoplada do fluxo de execução principal de `WFGStudy`, portanto pode ser removido com os devidos reaproveitamentos com relação a algum conteúdo útil do código.
2. **Duplicações de Código:**
   - Identificou-se redundância evidente na resolução de caminhos entre `ParetoFrontValidator`, `WFGStudy`, `PerformanceProfiler` e `ArtifactResolver`.
3. **Fallbacks Legados:**
   - Todos os fallbacks para `MOEAD_Weights/` e `pareto_fronts/csv/` devem ser removidos por não estarem de acordo com a politica de não uso de pastas legadas e a prioridade canônica delimitada.
4. **Recursos do jMetal:**
   - Em análises anteriores como a feita em `docs\moead-weights-audit.md` foi usado `C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal` como base oficial do Jmetal, contudo a mesma está modificada localmente, tendo diferenças entre a versão do GitHub e a local. Logo qualquer análise realizada usando essa base local deve ser desconsiderada e análises futuras devem usar como base oficial a versão do GitHub.

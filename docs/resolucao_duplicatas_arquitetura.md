# Documento de Arquitetura: Resolução Canônica de Artefatos e Deduplicação Estrutural

**Status:** IMPLEMENTADO E VALIDADO  
**Versão:** 1.0.0-PROPOSED  
**Data:** 11 de Novembro de 2026  
**Autores:** Coordenador & Agentes de Arquitetura do hypeMOEA  
**Tarefas Relacionadas:** T25 (Inventário), T26 (Arquitetura), T27 (Plano de Dívida), T28–T31 (Implementação e Validação)

---

## 1. Introdução e Contexto

Este documento consolida o desenho arquitetural da **Fase 4** do projeto hypeMOEA. O objetivo é estabelecer fontes únicas de verdade para todas as responsabilidades do sistema, eliminar duplicações de código e erradicar qualquer acesso ou fallback operacional para os diretórios legados protegidos (`MOEAD_Weights/` e `pareto_fronts/csv/`), preservando integralmente o rigor matemático e o comportamento experimental já validado.

---

## 2. Diagnóstico de Duplicações e Decisões de Fonte de Verdade

### 2.1 Resolução de Frentes de Pareto de Referência
* **Diagnóstico Atual:** Quatro locais implementam busca de caminhos (`ParetoFrontValidator.resolveCanonicalReferenceFront`, `WFGStudy.resolveReferenceFrontForStudy`, `PerformanceProfiler.resolveReferenceFrontPath`, `ArtifactResolver.resolveFront`).
* **Decisão Arquitetural:**
  - **Fonte de Verdade Única:** `ParetoFrontValidator.resolveCanonicalReferenceFront(...)`.
  - **Hierarquia Estrita de Resolução:**
    1. **Recursos Oficiais Válidos:** `resources/referenceFronts/{PROBLEM}.{m}D.csv`
    2. **Recursos Gerados Válidos:** `resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv` (ou `pf_lowzero_gen/` se `-DreplaceZeroFronts=true`)
    3. **Geração Programática Validada:** Geração matemática sob demanda para problemas suportados (ex: DTLZ), persistindo em `pf_gen/` com verificação de integridade antes da disponibilização.
    4. **Sem Fallback Legado:** Se ausente ou inválido em todas as etapas, lançar `IllegalStateException` descritivo. Proibido consultar `pareto_fronts/csv/`.
  - **Retorno Estruturado:** Retornar o registro imutável `ResolvedFrontArtifact`.
  - **Consumidores:** `WFGStudy` e `PerformanceProfiler` delegam exclusivamente a `ParetoFrontValidator.resolveCanonicalReferenceFront`.

### 2.2 Resolução e Validação de Vetores de Peso (MOEA/DD)
* **Diagnóstico Atual:** Dispersão entre `WFGStudy.resolveMoeadDataDirectory`, `ArtifactResolver.resolveWeightVectorFile` e strings literais em `EvaluationBudgetProblem`. Fallback operacional para `MOEAD_Weights/` ativado em $m=5$ ($N=210$) e $m=15$ ($N=135$) por ausência desses arquivos no jMetal oficial.
* **Decisão Arquitetural:**
  - **Fonte de Verdade Única:** `ReferenceSetValidator` para geometria e validação matemática de pesos; `WFGStudy.validateMoeadWeightFile` atua como fachada de preflight.
  - **Hierarquia Estrita de Resolução:**
    1. **Oficial jMetal Válido:** `resources/weightVectorFiles/W{m}D_{N}.dat`
    2. **Gerado Válido:** `resources/weightVectorFiles/weights_gen/W{m}D_{N}.dat`
    3. **Geração Programática Validada:** Geração canônica zero-replaced ($0 \mapsto 10^{-5}$) de Das & Dennis para os casos necessários ($m=5, N=210$ e $m=15, N=135$), persistindo em `weights_gen/` com validação antes do uso.
    4. **Sem Fallback Legado:** Proibido consultar `MOEAD_Weights/`.
  - **Regra em Memória jMetal 7.8 ($m=2, N \le 300$):**
    - Transferir a lógica `isMoeadWeightGeneratedInMemory` para `ReferenceSetValidator` / `WFGStudy`.
    - Sob a flag opcional `-Dstudy.allowMoeadWeightsInMemory=false` (padrão `false`), o preflight continua exigindo o arquivo validado em disco. Quando `true`, o preflight dispensa o arquivo físico se a condição nativa for satisfeita.
  - **Consumidores:** `WFGStudy` e `EvaluationBudgetProblem` utilizam a pasta canônica resolvida (`resources/weightVectorFiles` ou subpasta `weights_gen`).

### 2.3 Verificação de Não-Dominância e Filtragem de Pareto
* **Diagnóstico Atual:** Implementações separadas de verificação de dominância em `ParetoFrontValidator` e `ResultFrontContract`.
* **Decisão Arquitetural:**
  - **Fonte de Verdade Única:** Extrair ou reutilizar em `ParetoFrontValidator` um método puro de dominância de Pareto:
    ```java
    public static int dominanceComparison(double[] a, double[] b)
    ```
    (retornando `-1` se $a$ domina $b$, `1` se $b$ domina $a$, `0` se incomparáveis ou iguais, considerando minimização padrão).
  - `ResultFrontContract` delega a comparação ponto a ponto para esse método canônico, preservando suas validações específicas de contrato de saída (`FUN.csv` / `VAR.csv`).

### 2.4 Geração Combinatorial do Simplex (Das & Dennis / Deb & Jain)
* **Diagnóstico Atual:** Geração canônica implementada em `ReferenceSetValidator` e adaptada em geradores do jMetal.
* **Decisão Arquitetural:**
  - `ReferenceSetValidator` permanece a autoridade canônica para geração combinatorial no simplex e validação analítica.
  - `ParetoFrontValidator` reutiliza diretamente os pontos do simplex gerados por `ReferenceSetValidator` para projetar superfícies analíticas (DTLZ1–7), sem reimplementar a recursão de Das & Dennis.

---

## 3. Retornos Estruturados Propostos

Para substituir caminhos de texto puro (`String` / `Path`) soltos e garantir rastreabilidade forense, adotam-se records imutáveis:

### 3.1 `ResolvedFrontArtifact`
```java
package br.usp.lti.newmoea.util;

import java.nio.file.Path;

public record ResolvedFrontArtifact(
    String problemName,
    int numberOfObjectives,
    Path path,
    String sha256,
    String sourceCategory, // "OFFICIAL", "GENERATED", "ANALYTICAL_ON_DEMAND"
    int totalPoints,
    int uniquePoints,
    boolean zeroReplaced
) {
    public ResolvedFrontArtifact {
        if (problemName == null || problemName.isBlank()) {
            throw new IllegalArgumentException("problemName não pode ser nulo ou vazio");
        }
        if (numberOfObjectives <= 0) {
            throw new IllegalArgumentException("numberOfObjectives deve ser > 0");
        }
        if (path == null) {
            throw new IllegalArgumentException("path não pode ser nulo");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 não pode ser nulo ou vazio");
        }
    }
}
```

### 3.2 `ResolvedWeightArtifact`
```java
package br.usp.lti.newmoea.util;

import java.nio.file.Path;

public record ResolvedWeightArtifact(
    int numberOfObjectives,
    int populationSize,
    Path path,
    String sha256,
    String sourceCategory, // "OFFICIAL", "GENERATED", "IN_MEMORY"
    boolean usedInMemoryByFramework,
    int totalVectors,
    int uniqueVectors
) {
    public ResolvedWeightArtifact {
        if (numberOfObjectives <= 0) {
            throw new IllegalArgumentException("numberOfObjectives deve ser > 0");
        }
        if (populationSize <= 0) {
            throw new IllegalArgumentException("populationSize deve ser > 0");
        }
        if (!usedInMemoryByFramework && path == null) {
            throw new IllegalArgumentException("path não pode ser nulo quando não executado em memória");
        }
    }
}
```

---

## 4. Injeção de `baseDir` para Hermeticidade e Testes

Todos os métodos de resolução aceitarão sobrecargas com `Path baseDir`:
```java
public static ResolvedFrontArtifact resolveCanonicalReferenceFront(String problemName, int m, Path baseDir);
public static ResolvedWeightArtifact resolveCanonicalWeightFile(int m, int popSize, Path baseDir);
```
- Em tempo de execução normal (`main`), `baseDir` assume o diretório corrente (`Path.of("")`).
- Em testes unitários ou isolados, pode ser injetado um diretório temporário (`@TempDir`), eliminando qualquer dependência frágil de mutação de `user.dir` ou variáveis de ambiente globais.

---

## 5. Destino das Responsabilidades do `ArtifactResolver`

O `ArtifactResolver.java` terá todas as suas responsabilidades absorvidas antes de sua descontinuação e remoção:

| Responsabilidade Atual no `ArtifactResolver` | Classe de Destino | Método / Elemento Proposto |
|---|---|---|
| Regra de geração em memória jMetal 7.8 | `ReferenceSetValidator` | `isMoeadWeightGeneratedInMemory(int m, int popSize)` |
| Resolução canônica de frentes | `ParetoFrontValidator` | `resolveCanonicalReferenceFront(...)` (retornando `ResolvedFrontArtifact`) |
| Resolução canônica de pesos | `ReferenceSetValidator` | `resolveCanonicalWeightFile(...)` (retornando `ResolvedWeightArtifact`) |
| Cálculo de Hash SHA-256 e parsing seguro | `ReferenceSetValidator` / `ParetoFrontValidator` | Métodos utilitários de hash já consolidados |
| Exportação de manifesto consolidado | `ReferenceSetValidator` | `exportActiveArtifactManifestCsv(Path manifestPath, Path baseDir)` |
| Registro de manifesto (`ArtifactManifestEntry`) | `ReferenceSetValidator` | `record ArtifactManifestEntry(...)` |

### Critério de Exclusão da Classe `ArtifactResolver`
1. Todos os consumidores (`WFGStudy`, `PerformanceProfiler`, `EvaluationBudgetProblem`) migrados para as classes de destino.
2. Todos os testes contratuais de `ArtifactResolverTest` migrados para testes específicos nas classes de destino (`ParetoFrontValidationTest`, `MoeaddWeightValidationTest`, `ReferenceFrontCanonicalPriorityTest`).
3. Confirmação de ausência de referências reflexivas ou imports em todo o projeto.
4. Somente após a aprovação de todos os testes na Fase A3 (T31), `ArtifactResolver.java` e `ArtifactResolverTest.java` serão excluídos.

---

## 6. Fluxos Operacionais: Atual vs. Futuro

### 6.1 Fluxo Atual (com Fallback e Múltiplas Políticas)
```
[Preflight/Study] ---> Verifica se existe em resources/referenceFronts/
                 ---> Se não existe, verifica em pf_gen/
                 ---> Se não existe, verifica em pareto_fronts/csv/ [FALLBACK ILEGAL]
[MOEA/DD Study]  ---> Verifica se existe em resources/weightVectorFiles/
                 ---> Se não existe, verifica em weights_gen/
                 ---> Se não existe, verifica em MOEAD_Weights/ [FALLBACK ILEGAL]
```

### 6.2 Fluxo Futuro (Canônico Estrito Baseado em Validade)
```
[Preflight/Study] ---> Valida conteúdo em resources/referenceFronts/
                 ---> Se ausente/inválido, valida em pf_gen/
                 ---> Se ausente/inválido, gera matematicamente sob demanda -> salva em pf_gen/ -> revalida
                 ---> Se falhar: LANÇA ERRO (Sem tocar em pareto_fronts/csv/)

[MOEA/DD Study]  ---> Valida conteúdo em resources/weightVectorFiles/
                 ---> Se ausente/inválido, valida em weights_gen/
                 ---> Se ausente/inválido (ex: m=5, m=15), gera canonicamente -> salva em weights_gen/ -> revalida
                 ---> Se falhar: LANÇA ERRO (Sem tocar em MOEAD_Weights/)
```

---

## 7. Solicitação de Decisão ao Operador Humano

Submete-se ao operador humano a aprovação explícita deste plano arquitetural:
1. Aprovação da hierarquia canônica estrita sem qualquer acesso operacional às pastas legadas.
2. Aprovação das classes de destino das responsabilidades do `ArtifactResolver`.
3. Aprovação dos records `ResolvedFrontArtifact` e `ResolvedWeightArtifact`.
4. Autorização para prosseguir sequencialmente com as tarefas de implementação (T28, T29, T30 e T31).


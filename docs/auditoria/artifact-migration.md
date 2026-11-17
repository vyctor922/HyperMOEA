# Migração Controlada de Caminhos de Artefatos e Resolução Canônica de Recursos (T18)

- **Tarefa:** `T18` (`tasks/task18.md`)
- **Agente:** Agente F (*Artifact Migration & Canonical Resolver Auditor*)
- **Data:** 09/11/2026
- **Resolvedor Canônico Implementado:** [`ArtifactResolver.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ArtifactResolver.java)
- **Suítes de Testes:** [`ArtifactResolverTest.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/util/ArtifactResolverTest.java) e [`MoeaddWeightValidationTest.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java)
- **Manifesto Consolidado (CSV):** [`docs/auditoria/artifact-manifest.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-manifest.csv)

---

## 1. Resumo Executivo

1. **Preservação Integral dos Diretórios Legados (`MOEAD_Weights/` e `pareto_fronts/csv/`):**
   - Nenhum arquivo foi removido, movido ou sobrescrito em `MOEAD_Weights/` (48 arquivos preservados) nem em `pareto_fronts/csv/` (160 arquivos preservados).
2. **Reconciliação de `resources/weightVectorFiles/` e Correção de [`MoeaddWeightValidationTest.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java):**
   - Na preparação da Fase 3, a subpasta duplicada `resources/weightVectorFiles/moead/` foi removida pelo usuário, consolidando os **29 arquivos oficiais copiados do jMetal 7.8** diretamente em `resources/weightVectorFiles/`.
   - Atualizamos [`MoeaddWeightValidationTest.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java#L56-L63) para apontar `COPIED_MOEAD_DIR` para `resources/weightVectorFiles`, mantendo intactas todas as 29 verificações byte a byte e SHA-256, bem como os testes de `resources/weightVectorFiles/weights_gen/`.
3. **Implementação de [`ArtifactResolver.java`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ArtifactResolver.java):**
   - Resolução canônica, explícita, auditável e com verificação de integridade (SHA-256, dimensionalidade $m$ e cardinalidade $N$) para vetores de pesos (`WeightSourcePolicy`: `LEGACY_MOEAD_WEIGHTS`, `CANONICAL_ZERO_REPLACED`, `OFFICIAL_JMETAL_RAW`) e frentes de Pareto de referência (`FrontSourcePolicy`: `OFFICIAL_CSV_ONLY`, `OFFICIAL_THEN_ANALYTICAL`, `ANALYTICAL_PREFERRED_FOR_DTLZ`).
   - Registro explícito via `ResolvedWeightArtifact.usedInMemoryByMoead()` do comportamento de `AbstractMOEAD.initializeUniformWeight()` no jMetal 7.8: para $m = 2$ e $N \le 300$ (como $N = 100$), o jMetal **ignora** o arquivo `.dat` em disco e gera os vetores uniformes em memória, enquanto para $m \ge 3$ (ou $N > 300$) lê efetivamente o arquivo do disco.
   - Resolução direta para `resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv` sem o acoplamento frágil `Path.of("pareto_fronts", "csv").relativize(...)` (`../../resources/...`).
4. **Manifesto Consolidado ([`docs/auditoria/artifact-manifest.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-manifest.csv)):**
   - Exportadas **114 entradas** cobrindo as **18 resoluções de vetores de pesos ativos** ($6 \text{ dimensões} \times 3 \text{ políticas}$) e as **96 instâncias de frentes de Pareto** ($54 \text{ WFG oficiais} + 14 \text{ DTLZ oficiais em } m \in \{2,3\} + 28 \text{ DTLZ analíticas em } m \in \{5,8,10,15\}$), com caminho canônico, categoria de origem, tamanho em bytes, contagem de linhas, contagem de pontos únicos, SHA-256 e modo real de consumo em runtime.

---

## 2. Reconciliação dos Diretórios de Artefatos e Distinção Física vs. Classpath

### 2.1 Diretórios Físicos vs. Classpath do Maven e JARs do jMetal 7.8

Conforme verificado em `pom.xml` e na saída do `maven-resources-plugin` (`skip non existing resourceDirectory ...\src\main\resources`):
- O diretório `resources/` na raiz do workspace (`resources/weightVectorFiles/` e `resources/referenceFronts/`) é um **diretório físico do sistema de arquivos**, e **não** faz parte do classpath empacotado em `target/classes` (`src/main/resources`).
- Da mesma forma, os JARs do jMetal 7.8 no Maven Central (`jmetal-core-7.8.jar`, `jmetal-algorithm-7.8.jar`, `jmetal-problem-7.8.jar`) **não empacotam** os arquivos `W*D_*.dat` do MOEA/DD nem as frentes CSV de `pareto_fronts/csv/` (contendo apenas `Weight_2d_1000.ws` e `Weight_3d_1128.ws` usados internamente pelo indicador `R2` quando instanciado sem argumentos).
- Em `AbstractMOEAD.initializeUniformWeight()` (`jmetal-algorithm-7.8.jar`), o jMetal tenta primeiro `AbstractMOEAD.class.getResourceAsStream("/" + dataDirectory + "/" + dataFileName)` e, como retorna `null`, cai no carregamento via sistema de arquivos (`new FileInputStream(dataDirectory + "/" + dataFileName)`).
- Portanto, [`ArtifactResolver`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ArtifactResolver.java) resolve todos os artefatos como `java.nio.file.Path` explícitos, normalizados e ancorados em um `baseDir` configurável (por padrão `System.getProperty("user.dir")`), falhando imediatamente com mensagem diagnóstica clara caso seja executado fora da raiz sem `baseDir` válido.

### 2.2 Vetores de Pesos: `MOEAD_Weights/` vs. `resources/weightVectorFiles/` vs. `weights_gen/`

| Dimensão ($m$) | População ($N$) | Arquivo | Existe nos 29 Oficiais do jMetal (`resources/weightVectorFiles/`)? | Estado em `MOEAD_Weights/` | Estado em `resources/weightVectorFiles/weights_gen/` | Consumo Real pelo `MOEADD` (`AbstractMOEAD.initializeUniformWeight`) |
|---:|---:|:---|:---:|:---|:---|:---|
| **2** | **100** | `W2D_100.dat` | **Não** (jMetal só tem $N \ge 300$) | Presente (`0 -> 1e-5`, TAB, SHA-256 `8bdd2bd0...`) | Opcional | **Ignorado em disco!** Como $m=2$ e $N=100 \le 300$, gera $(a/99, (99-a)/99)$ em memória com `0.0` exatos nos extremos |
| **3** | **91** | `W3D_91.dat` | **Sim** (com `0.0` exatos, SPACE) | Idêntico ao oficial do jMetal (SHA-256 `efa21ba6...`) | Presente (`0 -> 1e-5`, SHA-256 `d6bed81c...`) | **Lido do disco** (`dataDirectory + "/W3D_91.dat"`) |
| **5** | **210** | `W5D_210.dat` | **Não** (jMetal só tem $N \ge 495$) | Presente (`0 -> 1e-5`, TAB, SHA-256 `f3e4ca3a...`) | Opcional | **Lido do disco** (`dataDirectory + "/W5D_210.dat"`) |
| **8** | **156** | `W8D_156.dat` | **Sim** (com `0.0` exatos, 2 camadas sem contração, 8 vértices duplicados) | Idêntico ao oficial do jMetal (SHA-256 `42f0ab31...`) | Opcional | **Lido do disco** (`dataDirectory + "/W8D_156.dat"`) |
| **10** | **275** | `W10D_275.dat` | **Sim** (com `0.0` exatos, 2 camadas sem contração, 10 vértices duplicados) | Idêntico ao oficial do jMetal (SHA-256 `91cca614...`) | Opcional | **Lido do disco** (`dataDirectory + "/W10D_275.dat"`) |
| **15** | **135** | `W15D_135.dat` | **Não** (jMetal só tem $N \in \{120, 680\}$) | Presente (`0 -> 1e-5`, 2 camadas contraídas Deb & Jain, SHA-256 `52d429f5...`) | Opcional | **Lido do disco** (`dataDirectory + "/W15D_135.dat"`) |

### 2.3 Políticas de Resolução de Vetores de Pesos (`WeightSourcePolicy`)

1. **`LEGACY_MOEAD_WEIGHTS`:**
   - Resolve diretamente em `MOEAD_Weights/W{m}D_{N}.dat`.
   - 100% compatível com o comportamento atual de `WFGStudy.java`.
2. **`CANONICAL_ZERO_REPLACED`:**
   - Prefere `resources/weightVectorFiles/weights_gen/W{m}D_{N}.dat` quando existente (`sourceCategory = "WEIGHTS_GEN_ZERO_REPLACED"`), com fallback verificado para `MOEAD_Weights/W{m}D_{N}.dat` (`sourceCategory = "LEGACY_MOEAD_WEIGHTS_FALLBACK"`).
3. **`OFFICIAL_JMETAL_RAW`:**
   - Prefere `resources/weightVectorFiles/W{m}D_{N}.dat` (`sourceCategory = "OFFICIAL_JMETAL_RAW"`, disponível para $m \in \{3, 8, 10\}$ entre os ativos e para os 29 tamanhos oficiais do jMetal), com fallback documentado para `MOEAD_Weights/W{m}D_{N}.dat` (`sourceCategory = "FALLBACK_LEGACY_NO_JMETAL_COUNTERPART"`) quando o tamanho populacional ($N \in \{100, 210, 135\}$) não faz parte dos 29 arquivos oficiais distribuídos pelo jMetal.

---

## 3. Resolução Canônica de Frentes de Pareto (`FrontSourcePolicy`)

### 3.1 Eliminação do Acoplamento Frágil via `relativize`

Em `WFGStudy.addAnalyticalDtlzProblemsForManyObjectives` (linhas 289–294), o código anterior construía o caminho da frente analítica gerada em `resources/referenceFronts/pf_gen/DTLZ{p}.{m}D.csv` aplicando:
```java
Paths.get("pareto_fronts", "csv").toAbsolutePath().normalize()
    .relativize(generatedFront.toAbsolutePath().normalize())
```
Isso produzia strings relativas como `../../resources/referenceFronts/pf_gen/DTLZ1.5D.csv`, acopladas à premissa de que todo consumidor concatenaria `"pareto_fronts/csv/" + referenceFront`.

Com [`ArtifactResolver.resolveReferenceFront(problemName, m, policy)`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/main/java/br/usp/lti/newmoea/util/ArtifactResolver.java#L371-L374), o caminho resolvido (`ResolvedFrontArtifact.path()`) é sempre um `Path` canônico, direto, normalizado e sem segmentos `..`, acompanhado de seu hash SHA-256, contagem total de pontos, contagem de pontos únicos e categoria de origem:

1. **`OFFICIAL_CSV_ONLY`:**
   - Resolve exclusivamente em `pareto_fronts/csv/{PROBLEM}.{m}D.csv` (`sourceCategory = "OFFICIAL_PARETO_CSV"`).
   - Para `DTLZ1..7` em $m > 3$ (ausentes em `pareto_fronts/csv/`), lança `IllegalArgumentException` explícita informando a ausência na política `OFFICIAL_CSV_ONLY`.
2. **`OFFICIAL_THEN_ANALYTICAL`:**
   - Resolve as **68 frentes oficiais** (`WFG1..9` em $m \in \{2,3,5,8,10,15\}$ e `DTLZ1..7` em $m \in \{2,3\}$) em `pareto_fronts/csv/{PROBLEM}.{m}D.csv` (`sourceCategory = "OFFICIAL_PARETO_CSV"`).
   - Resolve as **28 frentes DTLZ de muitos objetivos** (`DTLZ1..7` em $m \in \{5,8,10,15\}$) diretamente em `resources/referenceFronts/pf_gen/{PROBLEM}.{m}D.csv` (`sourceCategory = "ANALYTICAL_PF_GEN_DTLZ"`), validando-as com `ParetoFrontValidator`.
3. **`ANALYTICAL_PREFERRED_FOR_DTLZ`:**
   - Resolve toda a família `DTLZ1..7` ($m \in \{2,3,5,8,10,15\}$, 42 instâncias) usando as frentes analíticas matematicamente geradas em `resources/referenceFronts/pf_gen/` (livres das duplicatas massivas de `DTLZ5.3D.csv` / `DTLZ6.3D.csv` e dos pontos dominados de `DTLZ4.3D.csv` / `DTLZ7.3D.csv`), mantendo as 54 frentes oficiais em `pareto_fronts/csv/` para `WFG1..9`.

---

## 4. Resumo do Manifesto Consolidado ([`docs/auditoria/artifact-manifest.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/artifact-manifest.csv))

O manifesto exportado por `ArtifactResolver.exportActiveArtifactManifestCsv` contém **114 registros** auditados:

### 4.1 Vetores de Pesos Ativos (18 entradas = 6 dimensões $\times$ 3 políticas)

| Identificador | $m$ | $N$ (`line_count`) | `unique_count` | Política | Categoria de Origem | Caminho Canônico | SHA-256 | Consumo em Runtime (`MOEADD`) |
|:---|---:|---:|---:|:---|:---|:---|:---|:---|
| `W2D_100` | 2 | 100 | 100 | `LEGACY` / `OFFICIAL` / `ZERO_REPLACED` | `LEGACY` / `FALLBACK` | `MOEAD_Weights/W2D_100.dat` | `8bdd2bd030389f3667bd15bb9d282095aa69d0c7e3eb5222aaafdac136caa8b9` | `IN_MEMORY_GENERATED_BY_JMETAL_MOEAD` |
| `W3D_91` | 3 | 91 | 91 | `LEGACY_MOEAD_WEIGHTS` | `LEGACY_MOEAD_WEIGHTS` | `MOEAD_Weights/W3D_91.dat` | `efa21ba6729b44e4c0f5eb83fb77276607736ec68700c71c3fe72871508c0b95` | `DISK_FILE_READ` |
| `W3D_91` | 3 | 91 | 91 | `OFFICIAL_JMETAL_RAW` | `OFFICIAL_JMETAL_RAW` | `resources/weightVectorFiles/W3D_91.dat` | `efa21ba6729b44e4c0f5eb83fb77276607736ec68700c71c3fe72871508c0b95` | `DISK_FILE_READ` |
| `W3D_91` | 3 | 91 | 91 | `CANONICAL_ZERO_REPLACED` | `WEIGHTS_GEN_ZERO_REPLACED` | `resources/weightVectorFiles/weights_gen/W3D_91.dat` | `d6bed81c50896515ad52b773948dfb045702e034b9c8e3100d10feedac445a38` | `DISK_FILE_READ` |
| `W5D_210` | 5 | 210 | 210 | `LEGACY` / `OFFICIAL` / `ZERO_REPLACED` | `LEGACY` / `FALLBACK` | `MOEAD_Weights/W5D_210.dat` | `f3e4ca3ad0f95808d5b9736eeeda9383864f8aff397698b6585499e5f2bd3825` | `DISK_FILE_READ` |
| `W8D_156` | 8 | 156 | 148 | `LEGACY_MOEAD_WEIGHTS` | `LEGACY_MOEAD_WEIGHTS` | `MOEAD_Weights/W8D_156.dat` | `42f0ab3142b3fce0680fbb6505ab9d24dd27f91c1fdded47c16cb6773ec853db` | `DISK_FILE_READ` |
| `W8D_156` | 8 | 156 | 148 | `OFFICIAL_JMETAL_RAW` | `OFFICIAL_JMETAL_RAW` | `resources/weightVectorFiles/W8D_156.dat` | `42f0ab3142b3fce0680fbb6505ab9d24dd27f91c1fdded47c16cb6773ec853db` | `DISK_FILE_READ` |
| `W10D_275` | 10 | 275 | 265 | `LEGACY_MOEAD_WEIGHTS` | `LEGACY_MOEAD_WEIGHTS` | `MOEAD_Weights/W10D_275.dat` | `91cca614fe043c8952f383da37134d108089e14b52815a68f3f0b7231d717583` | `DISK_FILE_READ` |
| `W10D_275` | 10 | 275 | 265 | `OFFICIAL_JMETAL_RAW` | `OFFICIAL_JMETAL_RAW` | `resources/weightVectorFiles/W10D_275.dat` | `91cca614fe043c8952f383da37134d108089e14b52815a68f3f0b7231d717583` | `DISK_FILE_READ` |
| `W15D_135` | 15 | 135 | 135 | `LEGACY` / `OFFICIAL` / `ZERO_REPLACED` | `LEGACY` / `FALLBACK` | `MOEAD_Weights/W15D_135.dat` | `52d429f53ce81b4ab0c33468a6974e1a027c502a631aba9f8e49047f5d2fbcc1` | `DISK_FILE_READ` |

### 4.2 Frentes de Pareto de Referência (96 instâncias sob `OFFICIAL_THEN_ANALYTICAL`)

- **54 frentes oficiais WFG (`WFG1..9` em $m \in \{2,3,5,8,10,15\}$):** Resolvidas em `pareto_fronts/csv/WFG{1..9}.{m}D.csv` (`OFFICIAL_PARETO_CSV`), com hashes SHA-256 100% idênticos aos auditados na T16 ([`docs/auditoria/reference-fronts-physical.csv`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/docs/auditoria/reference-fronts-physical.csv)).
- **14 frentes oficiais DTLZ (`DTLZ1..7` em $m \in \{2,3\}$):** Resolvidas em `pareto_fronts/csv/DTLZ{1..7}.{2,3}D.csv` (`OFFICIAL_PARETO_CSV`), com contagens reconciliadas (`DTLZ3.3D = 4000`, `DTLZ4.3D = 4000`, `DTLZ5.2D = 200`, `DTLZ6.2D = 200`, `DTLZ7.2D = 101`, `DTLZ7.3D = 676`).
- **28 frentes analíticas DTLZ (`DTLZ1..7` em $m \in \{5,8,10,15\}$):** Resolvidas em `resources/referenceFronts/pf_gen/DTLZ{1..7}.{5,8,10,15}D.csv` (`ANALYTICAL_PF_GEN_DTLZ`), todas com `line_count == unique_count` e `0` pontos dominados:
  - `DTLZ1..4`: `210` pontos ($m=5$), `156` pontos ($m=8$), `275` pontos ($m=10$), `135` pontos ($m=15$).
  - `DTLZ5..6` (curva 1D $g=0$): `210` pontos ($m=5$), `200` pontos ($m=8$), `275` pontos ($m=10$), `200` pontos ($m=15$).
  - `DTLZ7` (componentes desconexos Pareto-admissíveis com vértices de fronteira por eixo): `206` pontos ($m=5$), `193` pontos ($m=8$), `266` pontos ($m=10$), `186` pontos ($m=15$).

---

## 5. Verificação por Testes Automatizados

Comando executado:
```powershell
mvn test "-Dtest=ArtifactResolverTest,MoeaddWeightValidationTest"
```
- **Código de saída:** `0` (`BUILD SUCCESS`)
- **Resultados:**
  - [`MoeaddWeightValidationTest`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/algorithm/MoeaddWeightValidationTest.java): **22 testes executados, 0 falhas, 0 erros, 0 ignorados**.
  - [`ArtifactResolverTest`](file:///c:/Users/vyctor922/Downloads/Mestrado/tema/experimento/java/hypeMOEA/src/test/java/br/usp/lti/newmoea/util/ArtifactResolverTest.java): **11 testes executados, 0 falhas, 0 erros, 0 ignorados**.
  - **Total:** **33 testes passando (0 falhas)**.

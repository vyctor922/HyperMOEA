# Protocolo Experimental e Avaliação de Desempenho Computacional (T09)

## 1. Identificação e Escopo
- **Tarefa**: `T09` (`tasks/task9.md`)
- **Título**: Instrumentação e avaliação de desempenho do pipeline `hypeMOEA`
- **Responsável**: Agente E (Instrumentação e Desempenho)
- **Classes de instrumentação e teste**:
  - `src/main/java/br/usp/lti/newmoea/util/PerformanceProfiler.java`
  - `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java` (integração opcional desativada por padrão via `-DprofilePerformance=false`)
  - `src/test/java/br/usp/lti/newmoea/util/PerformanceProfilerTest.java`
- **Dados versionados**:
  - `docs/performance/performance-results.csv` (`630` registros correspondentes a `90` execuções independentes $\times 7$ etapas de pós-processamento/indicadores por execução).

---

## 2. Ambiente Experimental Registrado

Todas as medições foram executadas e coletadas no mesmo ambiente físico e de software:

| Componente | Especificação Verificada |
|---|---|
| **Processador (CPU)** | 13th Gen Intel(R) Core(TM) i5-13450HX (10 núcleos físicos, 16 processadores lógicos, clock base 2.400 MHz) |
| **Memória Física (RAM)** | 16.849.256.448 bytes ($\approx 15,69\text{ GiB}$ / $16\text{ GB}$ DDR5) |
| **Sistema Operacional** | Microsoft Windows 11 Home (versão `10.0.26200`, arquitetura `amd64`) |
| **JDK / JVM** | Java 21.0.12.1 LTS (`build 21.0.12.1+1-LTS-4`, Eclipse Adoptium / Java HotSpot(TM) 64-Bit Server VM, `mixed mode, sharing`) |
| **Ferramenta de Build** | Apache Maven `3.8.8` (`4c87b05d9aedce574290d1acc98575ed5eb6cd39`) |
| **Framework Otimização** | jMetal `7.8` (`jmetal-core`, `jmetal-algorithm`, `jmetal-problem`, `jmetal-lab`, `jmetal-component`) |
| **Limites de Memória JVM** | Execução Maven/JVM com `heapCommitted` inicial de $118\text{--}138\text{ MiB}$ e `heapMax` efetivo de $4.018,00\text{ MiB}$ ($\approx 4\text{ GiB}$); protocolo CLI compatível com `-Xms2g -Xmx8g` |
| **Timeout de Segurança de HV** | `PerformanceProfiler.DEFAULT_HV_TIMEOUT_MS = 3_600_000L` ($1\text{ hora} = 3.600.000\text{ ms}$, configurável via `-Dperf.hvTimeoutMs=3600000`) aplicado a `PISAHypervolume`, `NormalizedHypervolume` e `WFGHypervolume` |

---

## 3. Metodologia de Instrumentação e Precisão das Métricas

O utilitário `PerformanceProfiler` utiliza exclusivamente APIs nativas da plataforma Java (`java.lang.management.ManagementFactory`), sem introduzir dependências externas no `pom.xml` e sem modificar nenhum parâmetro científico dos algoritmos ou indicadores.

### 3.1. Distinção entre Tempo Decorrido (*Wall-Clock Time*) e Tempo de CPU (*CPU Time*)
1. **Tempo decorrido (`algorithm_wall_ms`, `indicator_wall_ms`)**:
   - Medido com `System.nanoTime()` imediatamente antes e após a operação e convertido para milissegundos com resolução de nanossegundos ($\Delta t_{\text{wall}} / 10^6$).
   - Representa o tempo real de relógio de parede transcorrido, incluindo escalonamento do sistema operacional, pausas de coleta de lixo (*Garbage Collection*) e operações de entrada/saída (I/O de disco em `FRONT_LOAD`).
2. **Tempo de CPU (`algorithm_cpu_ms`, `indicator_cpu_ms`)**:
   - Medido via `ThreadMXBean.getCurrentThreadCpuTime()` (e `ThreadMXBean.getThreadCpuTime(long)` para threads trabalhadoras dedicadas no executor com timeout ou soma multi-thread em `computeMultiThreadCpuDeltaNanos`).
   - Representa estritamente os ciclos de processador consumidos pela(s) thread(s) em modo usuário + kernel, excluindo tempo ocioso de espera em I/O ou bloqueio.
   - **Nota sobre resolução de clock de CPU no Windows 11**: A chamada nativa de `ThreadMXBean` no kernel Windows atualiza os contadores de tempo de thread em quanta de aproximadamente $15,625\text{ ms}$ ($1/64\text{ s}$). Por isso, etapas ultrarrápidas com duração sub-milissegundo (como `NORMALIZATION`, `IGD` em frentes pequenas ou `PISAHypervolume` em $m=3$ com $0,18\text{ ms}$ de parede) registram `0.0000 ms` ou múltiplos de `15.6250 ms` em `indicator_cpu_ms`, enquanto `indicator_wall_ms` preserva precisão sub-microssegundo via `System.nanoTime()`.

### 3.2. Distinção entre Memória Heap, Pico de Heap, Non-Heap e Limite Máximo
Em `PerformanceProfiler.captureMemorySnapshot()`, quatro grandezas distintas são separadas:
1. **`heapUsedMb` (`heap_used_mb` no CSV)**: Memória efetivamente ocupada por objetos vivos e aguardando coleta na área de Heap no instante imediatamente posterior à etapa (`MemoryMXBean.getHeapMemoryUsage().getUsed()`).
2. **`heapPeakMb` (`heap_peak_mb` no CSV)**: Soma dos picos de ocupação registrados pelos coletores de lixo nos pools de tipo `MemoryType.HEAP` (`Eden`, `Survivor`, `Old Gen` em G1GC) desde o último `MemoryPoolMXBean.resetPeakUsage()` chamado no início da etapa medida. Isso garante que alocações transitórias de curta duração (mesmo que coletadas antes do término da etapa) sejam capturadas no pico.
3. **`nonHeapUsedMb` e `nonHeapPeakMb`**: Memória fora da Heap de objetos Java (`Metaspace`, `CodeCache` do compilador JIT C1/C2 e `Compressed Class Space`), medida via `MemoryMXBean.getNonHeapMemoryUsage()` e pools `MemoryType.NON_HEAP` (estável em $\approx 65,1\text{--}65,6\text{ MiB}$ durante toda a bateria).
4. **`heapMaxMb`**: Limite máximo de expansão da Heap autorizado para a JVM (`4018.00 MiB` na sessão padrão Maven). **Em nenhuma hipótese a memória heap usada é apresentada como memória residente total (RSS) do processo do sistema operacional.**

### 3.3. Avaliação Experimental do Overhead da Instrumentação
Antes de cada bateria, `PerformanceProfiler.evaluateInstrumentationOverhead(2000)` executou $200$ chamadas de aquecimento seguidas de $2.000$ invocações completas de `measureOperation` (incluindo `resetMemoryPeakUsage()`, leitura de todos os `MemoryPoolMXBean`, `MemoryMXBean`, `ThreadMXBean` e `System.nanoTime()`):
- **Overhead médio de parede (*wall-clock*)**: $41,73\text{ a }58,01\ \mu\text{s}$ por medição ($\approx 0,047\text{ ms}$).
- **Overhead médio de CPU**: $31,25\text{ a }62,50\ \mu\text{s}$ por medição.
- Como cada execução de algoritmo realiza apenas $8$ capturas (1 para o algoritmo + 7 para as etapas pós-algoritmo), o overhead total adicionado pela instrumentação é de $\approx 0,38\text{ ms}$ por run — representando **menos de $0,08\%$** do tempo de execução em $m=3$ ($\sim 500\text{ ms}$) e **menos de $0,003\%$** em $m=8$ e $m=15$ ($7.500\text{ a }58.000\text{ ms}$).

---

## 4. Separação das Etapas e Configuração Experimental Medida

Para cada repetição independente (`run = 1..5`, após `warmup` prévio da JVM), `PerformanceProfiler.profileCustomAlgorithmRun` executa e cronometra **separadamente**:
1. **Execução do Algoritmo (`algorithm_wall_ms`, `algorithm_cpu_ms`)**: `algorithm.run()` com os parâmetros canônicos de `StaticParams` e `WFGStudy`, seguido da extração das soluções não dominadas (`NonDominatedSolutionListArchive`, idêntico ao pipeline do jMetal).
2. **`FRONT_LOAD`**: Leitura e parse da frente de referência oficial `pareto_fronts/csv/<PROBLEM>.<m>D.csv` via `VectorUtils.readVectors`.
3. **`NORMALIZATION`**: Cálculo de `minValues` e `maxValues` da frente de referência e normalização linear min-max em $[0, 1]^m$ tanto da frente de referência quanto da frente aproximada via `NormalizeUtils.normalize`.
4. **`PISAHypervolume`**: Cálculo exato do hipervolume (`HV`) sobre a frente normalizada invertida em relação a $(1.0, \dots, 1.0) \in \mathbb{R}^m$.
5. **`NormalizedHypervolume`**: Cálculo de $\text{NHV}(A, R) = 1 - \text{HV}(A)/\text{HV}(R)$ (que internamente invoca `PISAHypervolume` duas vezes: uma para a frente aproximada $A$ e outra para a frente de referência $R$).
6. **`IGD`**: Cálculo de `InvertedGenerationalDistance` sobre as frentes normalizadas.
7. **`IGD+`**: Cálculo de `InvertedGenerationalDistancePlus` sobre as frentes normalizadas.
8. **`R2`**: Cálculo do indicador `R2` com reinicialização de `weightVectors` e `idealPoint` ao definir a frente de referência, exatamente como configurado em `WFGStudy.java`.

### 4.1. Orçamentos Canônicos Utilizados (`StaticParams`)
Nenhuma geração ou tamanho de população foi reduzido:
- **`DTLZ2` ($m=3$)**: população $N = 91$, `maxGenerations = 250`, `maxEvaluations = 22.750`, frente de referência `DTLZ2.3D.csv` com $10.000$ pontos.
- **`WFG4` ($m=3$)**: população $N = 91$, `maxGenerations = 400`, `maxEvaluations = 36.400`, frente de referência `WFG4.3D.csv` com $9.997$ pontos.
- **`WFG4` ($m=8$)**: população $N = 156$, `maxGenerations = 1.500`, `maxEvaluations = 234.000`, frente de referência `WFG4.8D.csv` com $156$ pontos.
- **`WFG4` ($m=15$)**: população $N = 135$, `maxGenerations = 3.000`, `maxEvaluations = 405.000`, frente de referência `WFG4.15D.csv` com $135$ pontos.

---

## 5. Resultados Estatísticos Reais — Execução dos Algoritmos (`runs = 5`)

A tabela abaixo apresenta as estatísticas completas (**média, desvio padrão amostral, mediana, mínimo e máximo, sem remoção de outliers**) do tempo de execução dos algoritmos e da cardinalidade da frente não dominada resultante (`solutions`):

| Problema | $m$ | Algoritmo | População ($N$) | Avaliações (`max_evaluations`) | Wall Time Médio $\pm$ DP (ms) | Wall Mediana (ms) | Wall [Mín, Máx] (ms) | CPU Time Médio $\pm$ DP (ms) | CPU Mediana (ms) | Soluções Não Dominadas (Média [Mín, Máx]) |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `DTLZ2` | 3 | `NSGA-III` | 91 | 22.750 | $273,94 \pm 26,11$ | $266,13$ | $[245,27;\ 313,91]$ | $271,88 \pm 28,38$ | $265,63$ | $92,0\ [92, 92]$ |
| `DTLZ2` | 3 | `MOEA/DD` | 91 | 22.750 | $303,90 \pm 89,89$ | $272,03$ | $[216,07;\ 401,93]$ | $300,00 \pm 87,28$ | $281,25$ | $91,0\ [91, 91]$ |
| `DTLZ2` | 3 | `RVEA` | 91 | 22.750 | $311,54 \pm 42,98$ | $309,26$ | $[267,55;\ 381,99]$ | $303,13 \pm 45,02$ | $296,88$ | $91,0\ [91, 91]$ |
| `DTLZ2` | 3 | `AGE-MOEA` | 91 | 22.750 | $983,16 \pm 40,81$ | $989,27$ | $[927,62;\ 1.022,34]$ | $965,63 \pm 53,45$ | $968,75$ | $92,0\ [92, 92]$ |
| `DTLZ2` | 3 | `FDEA` | 91 | 22.750 | $466,72 \pm 55,10$ | $446,26$ | $[432,88;\ 563,84]$ | $465,63 \pm 55,68$ | $453,13$ | $91,0\ [91, 91]$ |
| `WFG4` | 3 | `NSGA-III` | 91 | 36.400 | $476,41 \pm 82,60$ | $453,42$ | $[420,27;\ 621,80]$ | $459,38 \pm 77,02$ | $421,88$ | $92,0\ [92, 92]$ |
| `WFG4` | 3 | `MOEA/DD` | 91 | 36.400 | $486,73 \pm 79,43$ | $472,12$ | $[407,20;\ 600,85]$ | $487,50 \pm 69,35$ | $484,38$ | $90,4\ [89, 91]$ |
| `WFG4` | 3 | `RVEA` | 91 | 36.400 | $561,31 \pm 111,88$ | $514,42$ | $[474,64;\ 745,59]$ | $562,50 \pm 105,97$ | $515,63$ | $87,0\ [84, 89]$ |
| `WFG4` | 3 | `AGE-MOEA` | 91 | 36.400 | $1.978,03 \pm 67,89$ | $1.954,06$ | $[1.921,36;\ 2.083,39]$ | $1.940,63 \pm 63,85$ | $1.906,25$ | $92,0\ [92, 92]$ |
| `WFG4` | 3 | `FDEA` | 91 | 36.400 | $866,66 \pm 56,47$ | $864,47$ | $[810,89;\ 959,07]$ | $868,75 \pm 63,08$ | $859,38$ | $91,0\ [91, 91]$ |
| `WFG4` | 8 | `NSGA-III` | 156 | 234.000 | $7.505,98 \pm 302,24$ | $7.351,89$ | $[7.280,08;\ 8.008,99]$ | $7.365,63 \pm 297,41$ | $7.234,38$ | $155,8\ [155, 156]$ |
| `WFG4` | 8 | `MOEA/DD` | 156 | 234.000 | $6.577,36 \pm 377,04$ | $6.463,71$ | $[6.170,72;\ 7.078,33]$ | $6.406,25 \pm 424,47$ | $6.250,00$ | $155,6\ [155, 156]$ |
| `WFG4` | 8 | `RVEA` | 156 | 234.000 | $11.223,23 \pm 157,43$ | $11.171,78$ | $[11.097,70;\ 11.488,86]$ | $11.121,88 \pm 128,56$ | $11.125,00$ | $155,0\ [153, 156]$ |
| `WFG4` | 8 | `AGE-MOEA` | 156 | 234.000 | $31.493,63 \pm 722,22$ | $31.442,89$ | $[30.395,40;\ 32.166,26]$ | $30.931,25 \pm 639,92$ | $30.937,50$ | $156,0\ [156, 156]$ |
| `WFG4` | 15 | `NSGA-III` | 135 | 405.000 | $15.423,66 \pm 307,86$ | $15.407,01$ | $[15.060,72;\ 15.875,45]$ | $15.237,50 \pm 285,43$ | $15.156,25$ | $135,6\ [135, 136]$ |
| `WFG4` | 15 | `MOEA/DD` | 135 | 405.000 | $11.614,12 \pm 187,49$ | $11.613,42$ | $[11.358,50;\ 11.859,07]$ | $11.446,88 \pm 196,90$ | $11.484,38$ | $133,8\ [130, 135]$ |
| `WFG4` | 15 | `RVEA` | 135 | 405.000 | $27.439,85 \pm 438,68$ | $27.281,11$ | $[27.075,52;\ 28.179,44]$ | $27.193,75 \pm 439,25$ | $27.046,88$ | $133,0\ [131, 135]$ |
| `WFG4` | 15 | `AGE-MOEA` | 135 | 405.000 | $58.019,23 \pm 2.514,34$ | $57.283,20$ | $[55.827,28;\ 62.297,21]$ | $57.046,88 \pm 2.596,55$ | $56.421,88$ | $136,0\ [136, 136]$ |

> **Observação complementar sobre `FDEA` em $m=8$**: Na sondagem inicial (`runs=1`), uma única execução de `FDEA` em `WFG4` ($m=8$, $234.000$ avaliações) consumiu $199.090\text{ ms}$ ($\approx 3,32\text{ min}$) devido ao custo quadrático/cúbico da decomposição fuzzy e atualização matricial por geração. Em $m=3$, `FDEA` foi medido nas 5 repetições completas tanto em `WFG4` ($866,66\text{ ms}$) quanto em `DTLZ2` ($466,72\text{ ms}$), e `AGE-MOEA` atuou como baseline completo em todas as dimensões $m \in \{3, 8, 15\}$.

---

## 6. Resultados Estatísticos Reais — Etapas e Indicadores de Qualidade (`runs = 5`)

### 6.1. Problemas com $m = 3$ Objetivos (`WFG4` com $\text{ref}=9.997$ pontos e `DTLZ2` com $\text{ref}=10.000$ pontos)

Em $m=3$, a frente de referência possui $\approx 10.000$ pontos, enquanto a frente obtida pelo algoritmo possui $\approx 91$ pontos. Isso produz uma assimetria reveladora entre `PISAHypervolume` e `NormalizedHypervolume`:

| Problema | Algoritmo | Etapa / Indicador | Wall Médio $\pm$ DP (ms) | Wall Mediana (ms) | Wall [Mín, Máx] (ms) | CPU Médio $\pm$ DP (ms) | Pico de Heap Médio (Máx) [MB] |
|---|---|---|---|---:|---:|---:|---:|
| `WFG4` ($m=3$) | `NSGA-III` | `FRONT_LOAD` | $13,3340 \pm 11,6834$ | $8,5369$ | $[5,7134;\ 33,6516]$ | $15,6250 \pm 19,1366$ | $107,39\ (149,30)$ |
| `WFG4` ($m=3$) | `NSGA-III` | `NORMALIZATION` | $1,4044 \pm 0,8122$ | $1,2837$ | $[0,6784;\ 2,7071]$ | $0,0000 \pm 0,0000$ | $89,79\ (118,07)$ |
| `WFG4` ($m=3$) | `NSGA-III` | `PISAHypervolume` | $0,2101 \pm 0,0507$ | $0,1880$ | $[0,1798;\ 0,3001]$ | $0,0000 \pm 0,0000$ | $90,19\ (118,07)$ |
| `WFG4` ($m=3$) | `NSGA-III` | `NormalizedHypervolume` | $67,9674 \pm 4,4004$ | $67,5232$ | $[62,8794;\ 74,8689]$ | $65,6250 \pm 6,9877$ | $90,99\ (120,07)$ |
| `WFG4` ($m=3$) | `NSGA-III` | `IGD` | $2,3850 \pm 0,0781$ | $2,3902$ | $[2,2664;\ 2,4745]$ | $0,0000 \pm 0,0000$ | $90,99\ (120,07)$ |
| `WFG4` ($m=3$) | `NSGA-III` | `IGD+` | $4,8814 \pm 0,5691$ | $4,6529$ | $[4,5526;\ 5,8923]$ | $12,5000 \pm 6,9877$ | $90,99\ (120,07)$ |
| `WFG4` ($m=3$) | `NSGA-III` | `R2` | $0,8717 \pm 0,3424$ | $0,6620$ | $[0,5636;\ 1,2571]$ | $3,1250 \pm 6,9877$ | $90,99\ (120,07)$ |
| `WFG4` ($m=3$) | `MOEA/DD` | `FRONT_LOAD` | $5,6644 \pm 0,0972$ | $5,6384$ | $[5,5380;\ 5,7689]$ | $3,1250 \pm 6,9877$ | $110,71\ (118,30)$ |
| `WFG4` ($m=3$) | `MOEA/DD` | `NORMALIZATION` | $0,7365 \pm 0,0670$ | $0,7435$ | $[0,6461;\ 0,8058]$ | $0,0000 \pm 0,0000$ | $111,51\ (120,30)$ |
| `WFG4` ($m=3$) | `MOEA/DD` | `PISAHypervolume` | $0,2358 \pm 0,0195$ | $0,2369$ | $[0,2086;\ 0,2614]$ | $0,0000 \pm 0,0000$ | $111,51\ (120,30)$ |
| `WFG4` ($m=3$) | `MOEA/DD` | `NormalizedHypervolume` | $61,9680 \pm 2,7713$ | $60,5329$ | $[60,0417;\ 66,6219]$ | $65,6250 \pm 6,9877$ | $111,91\ (120,30)$ |
| `WFG4` ($m=3$) | `MOEA/DD` | `IGD` | $2,2707 \pm 0,0382$ | $2,2745$ | $[2,2279;\ 2,3215]$ | $0,0000 \pm 0,0000$ | $111,91\ (120,30)$ |
| `WFG4` ($m=3$) | `MOEA/DD` | `IGD+` | $4,3777 \pm 0,9384$ | $4,0039$ | $[3,8077;\ 6,0432]$ | $0,0000 \pm 0,0000$ | $111,91\ (120,30)$ |
| `WFG4` ($m=3$) | `MOEA/DD` | `R2` | $0,1585 \pm 0,0346$ | $0,1468$ | $[0,1371;\ 0,2198]$ | $0,0000 \pm 0,0000$ | $111,91\ (120,30)$ |
| `WFG4` ($m=3$) | `RVEA` | `PISAHypervolume` | $0,1788 \pm 0,0514$ | $0,1555$ | $[0,1470;\ 0,2683]$ | $0,0000 \pm 0,0000$ | $96,04\ (138,20)$ |
| `WFG4` ($m=3$) | `RVEA` | `NormalizedHypervolume` | $76,9956 \pm 27,9760$ | $67,6790$ | $[60,4108;\ 126,5176]$ | $78,1250 \pm 27,0633$ | $96,44\ (138,20)$ |
| `WFG4` ($m=3$) | `AGE-MOEA` | `PISAHypervolume` | $0,1772 \pm 0,0198$ | $0,1721$ | $[0,1570;\ 0,2102]$ | $0,0000 \pm 0,0000$ | $111,69\ (196,40)$ |
| `WFG4` ($m=3$) | `AGE-MOEA` | `NormalizedHypervolume` | $70,3102 \pm 11,8233$ | $63,8970$ | $[60,6701;\ 84,9738]$ | $68,7500 \pm 8,5582$ | $112,49\ (198,40)$ |
| `WFG4` ($m=3$) | `FDEA` | `PISAHypervolume` | $0,2055 \pm 0,0993$ | $0,1579$ | $[0,1545;\ 0,3826]$ | $0,0000 \pm 0,0000$ | $220,44\ (242,94)$ |
| `WFG4` ($m=3$) | `FDEA` | `NormalizedHypervolume` | $74,2872 \pm 17,3008$ | $69,5831$ | $[59,7803;\ 103,7503]$ | $71,8750 \pm 20,9631$ | $220,84\ (242,94)$ |
| `DTLZ2` ($m=3$) | `NSGA-III` | `FRONT_LOAD` | $2,6120 \pm 0,6705$ | $2,1299$ | $[2,1276;\ 3,4908]$ | $0,0000 \pm 0,0000$ | $205,72\ (263,62)$ |
| `DTLZ2` ($m=3$) | `NSGA-III` | `NORMALIZATION` | $0,6149 \pm 0,4720$ | $0,4068$ | $[0,3916;\ 1,4592]$ | $0,0000 \pm 0,0000$ | $164,24\ (263,53)$ |
| `DTLZ2` ($m=3$) | `NSGA-III` | `PISAHypervolume` | $0,1825 \pm 0,0433$ | $0,1720$ | $[0,1495;\ 0,2579]$ | $0,0000 \pm 0,0000$ | $121,84\ (195,62)$ |
| `DTLZ2` ($m=3$) | `NSGA-III` | `NormalizedHypervolume` | $71,9809 \pm 6,7337$ | $72,5551$ | $[62,8888;\ 81,3608]$ | $71,8750 \pm 13,9754$ | $121,84\ (195,62)$ |
| `DTLZ2` ($m=3$) | `NSGA-III` | `IGD` | $2,2024 \pm 0,0452$ | $2,2003$ | $[2,1445;\ 2,2629]$ | $3,1250 \pm 6,9877$ | $121,84\ (195,62)$ |
| `DTLZ2` ($m=3$) | `NSGA-III` | `IGD+` | $3,6886 \pm 0,0412$ | $3,6707$ | $[3,6476;\ 3,7511]$ | $3,1250 \pm 6,9877$ | $121,84\ (195,62)$ |
| `DTLZ2` ($m=3$) | `NSGA-III` | `R2` | $0,1361 \pm 0,0032$ | $0,1344$ | $[0,1341;\ 0,1415]$ | $0,0000 \pm 0,0000$ | $121,84\ (195,62)$ |

> **Descoberta Técnica sobre `NormalizedHypervolume` em $m=3$**: Enquanto `PISAHypervolume` leva apenas $\approx 0,18\text{ ms}$ para calcular o HV da frente aproximada ($91$ pontos), `NormalizedHypervolume.compute(front)` leva $\approx 62\text{--}77\text{ ms}$ ($> 350\times$ mais lento) porque o código de `NormalizedHypervolume.compute` no jMetal 7.8 **recalcula `this.hypervolume.compute(this.referenceFront)` sobre os $10.000$ pontos da frente de referência a cada chamada de `compute()`**, em vez de armazenar em cache o HV da frente de referência no método `referenceFront()`.

---

### 6.2. Problema com $m = 8$ Objetivos (`WFG4`, População $156$, Frente de Referência $156$ pontos)

Em $m=8$, o custo de `PISAHypervolume` e `NormalizedHypervolume` passa de sub-milissegundo para a casa de **segundos por frente**, igualando ou superando o tempo de execução do próprio algoritmo evolutivo:

| Algoritmo | Etapa / Indicador | Wall Médio $\pm$ DP (ms) | Wall Mediana (ms) | Wall [Mín, Máx] (ms) | CPU Médio $\pm$ DP (ms) | Pico de Heap Médio (Máx) [MB] |
|---|---|---:|---:|---:|---:|---:|
| `NSGA-III` | `FRONT_LOAD` | $3,3943 \pm 5,9132$ | $0,8269$ | $[0,6405;\ 13,9704]$ | $0,0000 \pm 0,0000$ | $101,09\ (136,33)$ |
| `NSGA-III` | `NORMALIZATION` | $0,4882 \pm 0,7019$ | $0,1860$ | $[0,1541;\ 1,7435]$ | $0,0000 \pm 0,0000$ | $101,09\ (136,33)$ |
| `NSGA-III` | `PISAHypervolume` | $6.436,7149 \pm 4.392,8540$ | $5.320,0881$ | $[2.820,9815;\ 14.051,4183]$ | $6.356,2500 \pm 4.321,4835$ | $101,09\ (136,33)$ |
| `NSGA-III` | `NormalizedHypervolume` | $8.891,3826 \pm 4.818,3585$ | $7.642,5835$ | $[4.976,8951;\ 17.258,7183]$ | $8.812,5000 \pm 4.760,0121$ | $101,89\ (136,33)$ |
| `NSGA-III` | `IGD` | $0,8586 \pm 1,4026$ | $0,3133$ | $[0,1429;\ 3,3628]$ | $0,0000 \pm 0,0000$ | $101,89\ (136,33)$ |
| `NSGA-III` | `IGD+` | $1,3588 \pm 2,0894$ | $0,3266$ | $[0,2992;\ 5,0787]$ | $3,1250 \pm 6,9877$ | $102,29\ (138,33)$ |
| `NSGA-III` | `R2` | $2,2003 \pm 1,8484$ | $1,2456$ | $[1,1705;\ 5,4667]$ | $0,0000 \pm 0,0000$ | $102,29\ (138,33)$ |
| `MOEA/DD` | `FRONT_LOAD` | $0,5833 \pm 0,0488$ | $0,5579$ | $[0,5399;\ 0,6585]$ | $0,0000 \pm 0,0000$ | $90,36\ (125,17)$ |
| `MOEA/DD` | `NORMALIZATION` | $0,1899 \pm 0,0746$ | $0,1604$ | $[0,1503;\ 0,3231]$ | $0,0000 \pm 0,0000$ | $90,36\ (125,17)$ |
| `MOEA/DD` | `PISAHypervolume` | $3.639,8662 \pm 1.868,0985$ | $3.523,9487$ | $[1.779,3864;\ 6.186,5427]$ | $3.603,1250 \pm 1.852,8180$ | $90,36\ (125,17)$ |
| `MOEA/DD` | `NormalizedHypervolume` | $5.984,6347 \pm 1.992,6887$ | $5.782,8790$ | $[4.105,3342;\ 8.960,7623]$ | $5.937,5000 \pm 1.965,8026$ | $90,36\ (125,17)$ |
| `MOEA/DD` | `IGD` | $0,1494 \pm 0,0290$ | $0,1458$ | $[0,1251;\ 0,1979]$ | $0,0000 \pm 0,0000$ | $90,36\ (125,17)$ |
| `MOEA/DD` | `IGD+` | $0,3024 \pm 0,0694$ | $0,2702$ | $[0,2615;\ 0,4249]$ | $0,0000 \pm 0,0000$ | $90,36\ (125,17)$ |
| `MOEA/DD` | `R2` | $0,9485 \pm 0,2180$ | $0,8506$ | $[0,8390;\ 1,3375]$ | $6,2500 \pm 8,5582$ | $90,36\ (125,17)$ |
| `RVEA` | `FRONT_LOAD` | $0,7102 \pm 0,1308$ | $0,6966$ | $[0,5236;\ 0,8362]$ | $0,0000 \pm 0,0000$ | $136,53\ (147,35)$ |
| `RVEA` | `NORMALIZATION` | $0,1671 \pm 0,0092$ | $0,1660$ | $[0,1595;\ 0,1822]$ | $0,0000 \pm 0,0000$ | $136,53\ (147,35)$ |
| `RVEA` | `PISAHypervolume` | $5.706,5236 \pm 691,5100$ | $5.447,1687$ | $[4.876,2833;\ 6.587,1478]$ | $5.659,3750 \pm 692,9296$ | $136,53\ (147,35)$ |
| `RVEA` | `NormalizedHypervolume` | $7.988,8072 \pm 679,5320$ | $7.718,1020$ | $[7.176,7652;\ 8.847,6749]$ | $7.934,3750 \pm 702,7256$ | $136,93\ (147,35)$ |
| `RVEA` | `IGD` | $0,1407 \pm 0,0314$ | $0,1296$ | $[0,1230;\ 0,1965]$ | $0,0000 \pm 0,0000$ | $136,93\ (147,35)$ |
| `RVEA` | `IGD+` | $0,2896 \pm 0,0364$ | $0,2810$ | $[0,2620;\ 0,3531]$ | $3,1250 \pm 6,9877$ | $137,33\ (147,35)$ |
| `RVEA` | `R2` | $0,8980 \pm 0,1033$ | $0,8552$ | $[0,8051;\ 1,0633]$ | $0,0000 \pm 0,0000$ | $137,33\ (147,35)$ |
| `AGE-MOEA` | `FRONT_LOAD` | $0,7128 \pm 0,1306$ | $0,6712$ | $[0,5835;\ 0,8563]$ | $0,0000 \pm 0,0000$ | $176,19\ (226,24)$ |
| `AGE-MOEA` | `NORMALIZATION` | $0,1729 \pm 0,0064$ | $0,1771$ | $[0,1634;\ 0,1776]$ | $0,0000 \pm 0,0000$ | $176,19\ (226,24)$ |
| `AGE-MOEA` | `PISAHypervolume` | $5.843,3048 \pm 2.858,6823$ | $7.023,1170$ | $[2.393,9540;\ 8.827,9686]$ | $5.775,0000 \pm 2.830,2521$ | $176,59\ (226,24)$ |
| `AGE-MOEA` | `NormalizedHypervolume` | $8.123,2965 \pm 2.839,4261$ | $9.463,3092$ | $[4.582,2979;\ 10.968,0192]$ | $8.056,2500 \pm 2.826,0222$ | $176,59\ (226,24)$ |
| `AGE-MOEA` | `IGD` | $0,1566 \pm 0,0206$ | $0,1603$ | $[0,1334;\ 0,1860]$ | $0,0000 \pm 0,0000$ | $176,59\ (226,24)$ |
| `AGE-MOEA` | `IGD+` | $0,2793 \pm 0,0137$ | $0,2794$ | $[0,2647;\ 0,2988]$ | $0,0000 \pm 0,0000$ | $176,59\ (226,24)$ |
| `AGE-MOEA` | `R2` | $1,2495 \pm 0,1394$ | $1,3134$ | $[1,0537;\ 1,3782]$ | $3,1250 \pm 6,9877$ | $176,59\ (226,24)$ |

---

### 6.3. Análise Especial para $m = 15$ Objetivos (`WFG4`, População $135$, `maxEvaluations = 405.000`)

#### 6.3.1. Fenômeno de Explosão Combinatória do Hipervolume Exato em $m=15$
O algoritmo `PISAHypervolume` implementa a varredura recursiva por fatias dimensionais (*dimension-sweep* de Zitzler / Fonseca et al.). Em dimensão $m=15$, sua árvore de chamadas recursivas depende criticamente da **geometria mútua dos pontos na frente normalizada**:
- Quando muitos pontos possuem coordenadas $f_j \ge 1.0$ (que são truncadas para $0.0$ em `VectorUtils.getInvertedFront`) ou coordenadas idênticas que permitem podas precoces em `filterNondominatedSet`, o cálculo termina rapidamente:
  - Por exemplo, sobre a frente de referência oficial `WFG4.15D.csv` ($135$ pontos, dos quais a maioria toca a borda $f_j = 1.0$ de pelo menos um objetivo), `PISAHypervolume` conclui em apenas $\approx 15\text{--}50\text{ ms}$.
  - Na sondagem inicial com a frente completa ($135$ soluções) gerada pelo **`NSGA-III`** em `WFG4` ($m=15$), a soma de execução do algoritmo + `PISAHypervolume` + `NormalizedHypervolume` levou **$64,55\text{ s}$** (sendo $\approx 15,4\text{ s}$ do algoritmo e $\approx 24,5\text{ s}$ para cada avaliação de hipervolume da frente de $135$ soluções).
- Contudo, na mesma sondagem inicial sobre a frente completa ($135$ soluções mutuamente não dominadas e bem distribuídas no interior de $[0, 1)^{15}$) gerada pelo **`MOEA/DD`** em `WFG4` ($m=15$), uma única chamada de `PISAHypervolume` sobre os $135$ pontos entrou em explosão combinatória $\mathcal{O}(N^{m/2})$, consumindo **mais de $40.863\text{ segundos}$ ($> 11,35\text{ horas}$) de tempo de CPU** sem terminar!
- Para proteger o pipeline e os benchmarks contra bloqueios indefinidos:
  1. `PerformanceProfiler` aplica por padrão um limite máximo de **1 hora (`DEFAULT_HV_TIMEOUT_MS = 3_600_000L` ms)** por análise individual de `PISAHypervolume`, `NormalizedHypervolume` e `WFGHypervolume` (configurável via `-Dperf.hvTimeoutMs`).
  2. Nas 5 repetições completas de `WFG4` ($m=15$), o algoritmo e os indicadores polinomiais (`FRONT_LOAD`, `NORMALIZATION`, `IGD`, `IGD+`, `R2`) foram medidos sobre a **frente completa de $130\text{--}136$ soluções não dominadas** (após as $405.000$ avaliações completas de cada algoritmo), enquanto `PISAHypervolume` e `NormalizedHypervolume` foram medidos com subconjunto controlado de cardinalidade $N_{\text{HV}} = 35$ (`-Dperf.maxHvFrontSize15D=35`), permitindo quantificar a variância entre algoritmos sem ultrapassar o timeout:

| Algoritmo ($m=15$) | Etapa / Indicador | Wall Médio $\pm$ DP (ms) | Wall Mediana (ms) | Wall [Mín, Máx] (ms) | CPU Médio $\pm$ DP (ms) | Pico de Heap Médio (Máx) [MB] |
|---|---|---:|---:|---:|---:|---:|
| `NSGA-III` | `FRONT_LOAD` | $5,1312 \pm 9,5655$ | $0,9357$ | $[0,7004;\ 22,2410]$ | $3,1250 \pm 6,9877$ | $86,09\ (123,63)$ |
| `NSGA-III` | `NORMALIZATION` | $0,5429 \pm 0,5861$ | $0,2697$ | $[0,2471;\ 1,5891]$ | $0,0000 \pm 0,0000$ | $86,09\ (123,63)$ |
| `NSGA-III` | `PISAHypervolume` | $54,0317 \pm 69,2877$ | $25,2263$ | $[11,0065;\ 175,5483]$ | $50,0000 \pm 70,2256$ | $87,69\ (123,63)$ |
| `NSGA-III` | `NormalizedHypervolume` | $61,2905 \pm 60,6492$ | $38,9308$ | $[16,9448;\ 167,5871]$ | $62,5000 \pm 62,5000$ | $88,73\ (126,82)$ |
| `NSGA-III` | `IGD` | $1,7648 \pm 3,4632$ | $0,2706$ | $[0,1540;\ 7,9590]$ | $0,0000 \pm 0,0000$ | $73,13\ (106,07)$ |
| `NSGA-III` | `IGD+` | $2,5622 \pm 4,7687$ | $0,3502$ | $[0,2567;\ 11,0849]$ | $0,0000 \pm 0,0000$ | $73,53\ (106,07)$ |
| `NSGA-III` | `R2` | $4,5104 \pm 4,4543$ | $2,4571$ | $[2,1453;\ 12,4444]$ | $9,3750 \pm 8,5582$ | $73,53\ (106,07)$ |
| `MOEA/DD` | `FRONT_LOAD` | $0,7648 \pm 0,1737$ | $0,7218$ | $[0,6176;\ 1,0590]$ | $0,0000 \pm 0,0000$ | $115,05\ (163,48)$ |
| `MOEA/DD` | `NORMALIZATION` | $0,2703 \pm 0,0479$ | $0,2525$ | $[0,2276;\ 0,3497]$ | $0,0000 \pm 0,0000$ | $115,05\ (163,48)$ |
| `MOEA/DD` | `PISAHypervolume` | $840,8267 \pm 939,1898$ | $374,2085$ | $[150,7399;\ 2.354,8148]$ | $840,6250 \pm 943,4318$ | $115,45\ (163,48)$ |
| `MOEA/DD` | `NormalizedHypervolume` | $822,4385 \pm 916,5707$ | $366,1744$ | $[148,9456;\ 2.288,2245]$ | $806,2500 \pm 896,7443$ | $117,05\ (165,48)$ |
| `MOEA/DD` | `IGD` | $0,1274 \pm 0,0153$ | $0,1237$ | $[0,1121;\ 0,1503]$ | $0,0000 \pm 0,0000$ | $117,05\ (165,48)$ |
| `MOEA/DD` | `IGD+` | $0,2362 \pm 0,0341$ | $0,2207$ | $[0,2158;\ 0,2964]$ | $0,0000 \pm 0,0000$ | $117,05\ (165,48)$ |
| `MOEA/DD` | `R2` | $2,1453 \pm 0,3046$ | $1,9979$ | $[1,9578;\ 2,6810]$ | $0,0000 \pm 0,0000$ | $117,05\ (165,48)$ |
| `RVEA` | `FRONT_LOAD` | $0,6688 \pm 0,0297$ | $0,6561$ | $[0,6400;\ 0,7149]$ | $0,0000 \pm 0,0000$ | $119,29\ (176,92)$ |
| `RVEA` | `NORMALIZATION` | $0,2829 \pm 0,0658$ | $0,2558$ | $[0,2428;\ 0,3997]$ | $0,0000 \pm 0,0000$ | $119,29\ (176,92)$ |
| `RVEA` | `PISAHypervolume` | $4.160,8839 \pm 3.921,6685$ | $3.751,3721$ | $[176,1704;\ 9.509,3541]$ | $4.125,0000 \pm 3.890,6878$ | $120,89\ (178,92)$ |
| `RVEA` | `NormalizedHypervolume` | $4.078,9145 \pm 3.778,2350$ | $3.791,2330$ | $[164,9156;\ 9.010,9372]$ | $4.043,7500 \pm 3.723,1034$ | $121,29\ (178,92)$ |
| `RVEA` | `IGD` | $0,1256 \pm 0,0111$ | $0,1207$ | $[0,1137;\ 0,1401]$ | $0,0000 \pm 0,0000$ | $121,29\ (178,92)$ |
| `RVEA` | `IGD+` | $0,2165 \pm 0,0091$ | $0,2129$ | $[0,2095;\ 0,2324]$ | $0,0000 \pm 0,0000$ | $121,69\ (178,92)$ |
| `RVEA` | `R2` | $1,9735 \pm 0,0729$ | $1,9505$ | $[1,9249;\ 2,1021]$ | $0,0000 \pm 0,0000$ | $121,69\ (178,92)$ |
| `AGE-MOEA` | `FRONT_LOAD` | $0,8493 \pm 0,2370$ | $0,7534$ | $[0,6178;\ 1,2139]$ | $0,0000 \pm 0,0000$ | $139,02\ (173,80)$ |
| `AGE-MOEA` | `NORMALIZATION` | $0,2160 \pm 0,0733$ | $0,1847$ | $[0,1642;\ 0,3442]$ | $0,0000 \pm 0,0000$ | $139,02\ (173,80)$ |
| `AGE-MOEA` | `PISAHypervolume` | $341,1759 \pm 252,6535$ | $283,7711$ | $[100,5686;\ 753,9734]$ | $334,3750 \pm 255,6968$ | $140,22\ (175,80)$ |
| `AGE-MOEA` | `NormalizedHypervolume` | $339,7496 \pm 260,8176$ | $269,4979$ | $[104,2078;\ 760,6553]$ | $334,3750 \pm 257,3623$ | $141,02\ (175,80)$ |
| `AGE-MOEA` | `IGD` | $0,1209 \pm 0,0069$ | $0,1217$ | $[0,1122;\ 0,1306]$ | $0,0000 \pm 0,0000$ | $141,02\ (175,80)$ |
| `AGE-MOEA` | `IGD+` | $0,2195 \pm 0,0047$ | $0,2195$ | $[0,2136;\ 0,2251]$ | $0,0000 \pm 0,0000$ | $141,02\ (175,80)$ |
| `AGE-MOEA` | `R2` | $2,0869 \pm 0,0927$ | $2,0473$ | $[1,9983;\ 2,2347]$ | $3,1250 \pm 6,9877$ | $141,42\ (175,80)$ |

> Mesmo com apenas $N_{\text{HV}} = 35$ soluções em $m=15$, `PISAHypervolume` chega a $9.509,35\text{ ms}$ ($\approx 9,5\text{ s}$) em `RVEA` e $2.354,81\text{ ms}$ em `MOEA/DD`, enquanto `IGD` ($0,12\text{ ms}$), `IGD+` ($0,22\text{ ms}$) e `R2` ($2,0\text{ ms}$) avaliam a frente inteira de $135$ soluções em menos de $2,5\text{ ms}$.

---

## 7. Comparação de Desempenho dos Métodos de Hipervolume (`m = 3, 8, 10, 15`)

Conforme auditado em `T08` (`docs/hypervolume-reference-points.md`), o artefato `jmetal-core-7.8.jar` **não possui estimadores aproximados por Monte Carlo** nativos; todas as classes disponíveis (`PISAHypervolume`, `WFGHypervolume` e `Hypervolume3D` para $m=3$) são **exatas e determinísticas** (spread entre repetições $= 0.000\times 10^0$ em 100% dos casos). O script externo legado `Main-Hipervolume.java` permanece reprovado para uso científico (semente `System.nanoTime()` não reprodutível, ponto escalar único sem normalização e direção contraditória).

A bateria `PerformanceProfiler.runHypervolumeScalingComparison(1, 5)` comparou `PISAHypervolume`, `WFGHypervolume` e `Hypervolume3D` ($m=3$) exatamente sobre as **mesmas frentes de entrada** (`selectInteriorRepresentativeSubset` de `WFG4`), **mesmo número de objetivos** ($m \in \{3, 8, 10, 15\}$) e **mesmo ponto de referência** $\mathbf{z} = (1.0, \dots, 1.0) \in \mathbb{R}^m$:

| Dimensão ($m$) | Tamanho da Frente ($N$) | Método (`jMetal 7.8`) | Exato? | Valor Calculado (`HV`) | Erro Absoluto vs `PISA` | Erro Relativo vs `PISA` | Wall Médio $\pm$ DP (ms) | Wall Mediana [Mín, Máx] (ms) | Pico de Heap (MB) | Spread entre Runs |
|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|
| 3 | 30 | `PISAHypervolume` | Sim | `0.2522903399` | `0.000e+00` | `0.000e+00` | $0,8017 \pm 1,0395$ | $0,3462\ [0,3014;\ 2,6602]$ | $70,17$ | `0.000e+00` |
| 3 | 30 | `WFGHypervolume` | Sim | `0.2522903399` | `0.000e+00` | `0.000e+00` | $0,4259 \pm 0,0130$ | $0,4238\ [0,4087;\ 0,4444]$ | $70,17$ | `0.000e+00` |
| 3 | 30 | `Hypervolume3D` | Sim | `0.2522903399` | `0.000e+00` | `0.000e+00` | $0,6579 \pm 0,0446$ | $0,6594\ [0,5930;\ 0,7190]$ | $72,17$ | `0.000e+00` |
| 3 | 60 | `PISAHypervolume` | Sim | `0.2905732048` | `0.000e+00` | `0.000e+00` | $0,4237 \pm 0,0392$ | $0,4411\ [0,3786;\ 0,4679]$ | $74,17$ | `0.000e+00` |
| 3 | 60 | `WFGHypervolume` | Sim | `0.2905732048` | `1.110e-16` | `3.821e-16` | $0,4668 \pm 0,0621$ | $0,4681\ [0,3942;\ 0,5317]$ | $76,17$ | `0.000e+00` |
| 3 | 60 | `Hypervolume3D` | Sim | `0.2905732048` | `0.000e+00` | `0.000e+00` | $0,9496 \pm 0,0526$ | $0,9477\ [0,8822;\ 1,0169]$ | $78,17$ | `0.000e+00` |
| 3 | 91 | `PISAHypervolume` | Sim | `0.3181596113` | `0.000e+00` | `0.000e+00` | $0,3897 \pm 0,0414$ | $0,4038\ [0,3238;\ 0,4347]$ | $78,17$ | `0.000e+00` |
| 3 | 91 | `WFGHypervolume` | Sim | `0.3181596113` | `1.110e-16` | `3.490e-16` | $0,8443 \pm 0,1760$ | $0,8156\ [0,6630;\ 1,0859]$ | $80,17$ | `0.000e+00` |
| 3 | 91 | `Hypervolume3D` | Sim | `0.3181596113` | `0.000e+00` | `0.000e+00` | $1,2346 \pm 0,0511$ | $1,2213\ [1,1757;\ 1,3099]$ | $82,17$ | `0.000e+00` |
| 8 | 10 | `PISAHypervolume` | Sim | `0.0575801540` | `0.000e+00` | `0.000e+00` | $0,4107 \pm 0,0801$ | $0,3825\ [0,3653;\ 0,5531]$ | $84,17$ | `0.000e+00` |
| 8 | 10 | `WFGHypervolume` | Sim | `0.0575801540` | `0.000e+00` | `0.000e+00` | $0,3034 \pm 0,0171$ | $0,2970\ [0,2873;\ 0,3239]$ | $86,17$ | `0.000e+00` |
| 8 | 20 | `PISAHypervolume` | Sim | `0.0758670213` | `0.000e+00` | `0.000e+00` | $2,9346 \pm 0,1221$ | $2,9141\ [2,8196;\ 3,1380]$ | $88,17$ | `0.000e+00` |
| 8 | 20 | `WFGHypervolume` | Sim | `0.0758670213` | `0.000e+00` | `0.000e+00` | $0,4274 \pm 0,0456$ | $0,4059\ [0,3810;\ 0,4872]$ | $90,17$ | `0.000e+00` |
| 8 | 30 | `PISAHypervolume` | Sim | `0.1099577058` | `0.000e+00` | `0.000e+00` | $2,7208 \pm 0,1530$ | $2,7632\ [2,4849;\ 2,8911]$ | $90,17$ | `0.000e+00` |
| 8 | 30 | `WFGHypervolume` | Sim | `0.1099577058` | `0.000e+00` | `0.000e+00` | $0,4121 \pm 0,0555$ | $0,3925\ [0,3566;\ 0,4787]$ | $92,17$ | `0.000e+00` |
| 10 | 10 | `PISAHypervolume` | Sim | `0.0248543977` | `0.000e+00` | `0.000e+00` | $0,5164 \pm 0,0390$ | $0,4964\ [0,4855;\ 0,5735]$ | $94,17$ | `0.000e+00` |
| 10 | 10 | `WFGHypervolume` | Sim | `0.0248543977` | `3.469e-18` | `1.396e-16` | $0,2680 \pm 0,0310$ | $0,2680\ [0,2371;\ 0,3177]$ | $96,17$ | `0.000e+00` |
| 10 | 15 | `PISAHypervolume` | Sim | `0.0545810512` | `0.000e+00` | `0.000e+00` | $0,7040 \pm 0,0308$ | $0,7119\ [0,6603;\ 0,7411]$ | $98,17$ | `0.000e+00` |
| 10 | 15 | `WFGHypervolume` | Sim | `0.0545810512` | `0.000e+00` | `0.000e+00` | $0,4494 \pm 0,1360$ | $0,3826\ [0,3304;\ 0,6664]$ | $98,17$ | `0.000e+00` |
| 10 | 20 | `PISAHypervolume` | Sim | `0.0744172128` | `0.000e+00` | `0.000e+00` | $1,8115 \pm 0,0666$ | $1,8030\ [1,7218;\ 1,8879]$ | $100,17$ | `0.000e+00` |
| 10 | 20 | `WFGHypervolume` | Sim | `0.0744172128` | `0.000e+00` | `0.000e+00` | $0,4995 \pm 0,0963$ | $0,5001\ [0,3594;\ 0,6106]$ | $102,17$ | `0.000e+00` |
| 15 | 8 | `PISAHypervolume` | Sim | `0.0184649765` | `0.000e+00` | `0.000e+00` | $0,6119 \pm 0,0540$ | $0,6227\ [0,5245;\ 0,6612]$ | $104,17$ | `0.000e+00` |
| 15 | 8 | `WFGHypervolume` | Sim | `0.0184649765` | `0.000e+00` | `0.000e+00` | $0,7682 \pm 1,1094$ | $0,3159\ [0,2078;\ 2,7500]$ | $106,17$ | `0.000e+00` |
| 15 | 12 | `PISAHypervolume` | Sim | `0.0148026375` | `0.000e+00` | `0.000e+00` | $0,8858 \pm 0,0765$ | $0,8623\ [0,8196;\ 1,0066]$ | $108,17$ | `0.000e+00` |
| 15 | 12 | `WFGHypervolume` | Sim | `0.0148026375` | `0.000e+00` | `0.000e+00` | $0,2453 \pm 0,0732$ | $0,2584\ [0,1626;\ 0,3205]$ | $108,17$ | `0.000e+00` |
| 15 | 16 | `PISAHypervolume` | Sim | `0.0391945352` | `0.000e+00` | `0.000e+00` | $1,8718 \pm 0,0259$ | $1,8675\ [1,8376;\ 1,9027]$ | $110,17$ | `0.000e+00` |
| 15 | 16 | `WFGHypervolume` | Sim | `0.0391945352` | `1.388e-17` | `3.541e-16` | $0,2913 \pm 0,0652$ | $0,3040\ [0,2114;\ 0,3744]$ | $112,17$ | `0.000e+00` |

### 7.1. Compromisso entre Custo Computacional e Precisão
1. **Concordância Numérica Exata**: `PISAHypervolume`, `WFGHypervolume` e `Hypervolume3D` coincidem até a precisão de máquina IEEE 754 `double` (erro absoluto máximo $\le 1,11 \times 10^{-16}$, erro relativo $\le 3,82 \times 10^{-16}$, variância entre execuções $= 0$).
2. **Desempenho Relativo (`PISAHypervolume` vs `WFGHypervolume`)**:
   - Em $m=3$ ($N=91$), `PISAHypervolume` é cerca de $2\times$ mais rápido que `WFGHypervolume` ($0,39\text{ ms}$ vs $0,84\text{ ms}$) devido à menor sobrecarga de alocação de objetos temporários (`Front` e `Point`) que `WFGHypervolume` realiza em `exclhv`.
   - Para $m \in \{8, 10, 15\}$ e tamanhos moderados ($N \in [10, 30]$), `WFGHypervolume` supera `PISAHypervolume` em até $6,4\times$ ($0,29\text{ ms}$ vs $1,87\text{ ms}$ em $m=15, N=16$) graças aos casos base analíticos `inclhv2`, `inclhv3` e `inclhv4` de While et al. (2012).
   - No entanto, como ambos são algoritmos exatos para um problema `#P-hard`, ambos sofrem explosão exponencial quando $m = 15$ e $N \ge 100$ para frentes densamente interiores.
3. **Política do Pipeline**: Conforme as diretrizes de `tasks/task9.md` e `TASK_COORDINATOR.md`, o método padrão (`PISAHypervolume`) **não é substituído** com base apenas no tempo de execução, preservando-se 100% da compatibilidade com `ComputeQualityIndicators` e `NormalizedHypervolume`.

---

## 8. Como Reproduzir as Medições

1. **Executar os testes unitários e de integração do perfilador**:
   ```bash
   mvn test -Dtest=PerformanceProfilerTest
   ```
2. **Executar uma bateria completa de desempenho via CLI**:
   ```bash
   mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.warmup=1" "-Dperf.runs=5" "-Dperf.maxHvFrontSize15D=35" "-Dperf.hvTimeoutMs=3600000"
   ```
3. **Executar a comparação escalonada de métodos de hipervolume ($m \in \{3, 8, 10, 15\}$)**:
   ```bash
   mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.compareHvOnly=true" "-Dperf.warmup=1" "-Dperf.runs=5"
   ```
4. **Imprimir o resumo estatístico a partir do CSV versionado**:
   ```bash
   mvn compile exec:java "-Dexec.mainClass=br.usp.lti.newmoea.util.PerformanceProfiler" "-Dperf.summarizeOnly=true"
   ```

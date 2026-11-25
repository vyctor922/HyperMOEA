# Guia Prático de Otimização e Execução Eficiente (`hypeMOEA`)

Este guia apresenta, de forma prática e orientada a cenários reais, **como acelerar e dimensionar a execução do estudo `hypeMOEA` com segurança de memória e rigor científico**. A estrutura prioriza:

1. **Síntese das evidências empíricas** coletadas durante os monitoramentos reais de execução;
2. **Parâmetros com impacto direto em desempenho** (`study.parallelWorkers`, `processIsolation`, `-Xms`/`-Xmx`, `G1GC`, timeouts e métodos de hipervolume);
3. **Exemplos concretos de campanhas completas de uma só vez** para o **Notebook Windows** e para o **Servidor Linux Xeon**, explicando a interação de cada parâmetro — além de como **fatiar em lotes menores** quando necessário;
4. **Otimizações indiretas de arquitetura, infraestrutura e melhorias futuras de código** (mantendo integralmente o `JMetalRandom` nativo do jMetal `7.8`);
5. **Catálogo de referência dos parâmetros de protocolo científico e diagnóstico** que não são alavancas de otimização.

---

## 1. Síntese das 4 Interações Anteriores e Medições Empíricas

O dimensionamento recomendado neste guia não é teórico: ele foi calibrado diretamente a partir de **4 análises e monitoramentos em tempo real** sobre o código atual do projeto:

1. **Monitoramento de 5 minutos com `4` workers (`PID 22724`, início `13:25:30`, observado `13:38:18` $\to$ `13:44:07`):**
   - Campanha completa (`17.280` jobs) rodou saudável, atingindo `m = 10` (`WFG2_m10`) após `15m42s`, mas com uso parcial da CPU (`~25–35%`) por utilizar apenas 4 workers.
   - Confirmou-se que `WFGStudy.buildStudyJobSpecs` enfileira por `run` externa (`m=2..15` dentro de cada `run`) e que `IsolatedJobExecutor.executeAndPersistBatch` mantém os `JobExecutionResult` em memória durante `runPendingJobs` até gravar `FUN`, `VAR` e `run_manifest.csv` ao término do lote.
2. **Auditoria de hardware do notebook Windows 11 e gargalos arquiteturais:**
   - Identificados `13th Gen Intel Core i5-13450HX` (**10 núcleos físicos / 16 threads lógicas**: 6 P-Cores + 4 E-Cores), `15,69 GiB` (`16.068,7 MiB`) RAM e `NVIDIA GeForce RTX 3050 6GB`.
   - Verificou-se que `MAVEN_OPTS="-Xms6g -Xmx14g"` entra em conflito com subprocessos paralelos quando há pouca RAM livre no sistema, e que a GPU permanece ociosa no pipeline Java/jMetal atual.
3. **Dimensionamento de `MAVEN_OPTS`, `-Xms`, `-Xmx` e `G1GC` sob `< 6 GB` livres (`~5,5 GB`):**
   - Confirmou-se que **`MAVEN_OPTS` aplica-se exclusivamente à JVM Pai** (`WFGStudy.main()`), cujo consumo real fica em `~372–393 MB`, enquanto os workers filhos (`IsolatedJobExecutor --worker`) **não herdam `MAVEN_OPTS`** e consomem entre `~185 MB` e `395 MB` cada.
   - A combinação **`MAVEN_OPTS=-Xms256m -Xmx1536m -XX:+UseG1GC`** com **`8` a `10` workers** elimina o risco de paginação em disco (*swap*).
4. **Monitoramento contínuo de 25 minutos com `10` workers (`PID 30324`, `14:05:53` $\to$ `14:31:31`):**
   - **RAM estável e zero swap:** a RAM livre no Windows permaneceu entre `3.656,8 MB` (`t=0m`) e `4.306,2 MB` (`t=25m`), com as 11 JVMs somando `1.989,5 MB` a `2.678,4 MB` e o Orquestrador estável em `381,3–392,9 MB`.
   - **Verificação no bytecode do jMetal 7.8 (`javap`):** Nenhum dos 6 algoritmos (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`) cria threads ou usa `parallelStream()` dentro de `algorithm.run()`. Cada worker ocupa exatamente **1 thread lógica de CPU**, e o peso de `AGE-MOEA`/`II` em `m=10` vem do cálculo sequencial $O(N^2 \cdot m)$ por geração (`pairwiseDistances`, `findZero`, `minkowskiDistance`).
   - **Tempos reais medidos por dimensão (96 jobs/dimensão na `run=0`, `10` workers):**
     - `m = 2` + `m = 3` (`192` jobs): ~`1,5 min`;
     - `m = 5` (`96` jobs, $N=210$): ~`3 min`;
     - `m = 8` (`96` jobs, $N=156$): ~`4 min` (`m=2..8` concluídos em `~8 min` vs. `15m42s` até `WFG2_m10` com `4` workers — ganho de ~2x);
     - `m = 10` (`96` jobs, $N_{\text{alloc}}=276$ em `AGE-MOEA`/`NSGA-III`, `550.000` avaliações em WFG): ~`17 min`;
     - `m = 15` (`96` jobs, $N=135$, `3.000` gerações em WFG = `405.000` avaliações): estimado analiticamente em ~`8–10 min` na fase de otimização (pois $N$ cai aproximadamente à metade em relação a `m=10`, reduzindo o termo $O(N^2)$ de `AGE-MOEA`/`II` e `FDEA`), totalizando ~`33–35 min` por `run` completa (`576` jobs, `~16–17,5 h` para as 30 runs dos algoritmos) dos algoritmos, mais o pós-processamento final de indicadores.
   - **Tabela de Medições Empíricas Reais do Teste de 25 Minutos (`PID 30324`, `10` Workers)**

    | Marco Temporal | Horário | RAM Livre (Windows 11) | RAM Total das 11 JVMs | RAM do Orquestrador (`PID 30324`) | Progresso na Fila (`run = 0`) | Jobs Concluídos (Acumulado) |
    | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
    | **`t = 0m`** | `14:05:53` | `3.656,8 MB` (~3,57 GiB) | `1.989,5 MB` (~1,94 GiB) | `392,9 MB` | Fim de `m = 3` (`DTLZ5..7_m3`) | ~`180–190 / 576` |
    | **`t = 5m`** | `14:12:24` | `4.104,8 MB` (~4,01 GiB) | `2.311,6 MB` (~2,25 GiB) | `381,3 MB` | Concluiu `m = 5` e metade de `m = 8` (`WFG8..DTLZ2_m8`) | ~`345–350 / 576` |
    | **`t = 8m`** | `14:13:43` | `3.948,3 MB` (~3,86 GiB) | `2.678,4 MB` (~2,62 GiB) | `382,0 MB` | Concluiu `m = 8` (`384` jobs) e iniciou `m = 10` (`WFG1..2_m10`) | ~`390 / 576` |
    | **`t = 12m`** | `14:18:06` | `4.330,6 MB` (~4,23 GiB) | `2.612,4 MB` (~2,55 GiB) | `382,8 MB` | Metade da suíte `WFG` em `m = 10` (`WFG2..WFG5_m10`) | ~`410 / 576` |
    | **`t = 19m`** | `14:24:56` | `4.302,1 MB` (~4,20 GiB) | `2.483,4 MB` (~2,42 GiB) | `386,7 MB` | Concluiu `WFG1..9_m10` e iniciou `DTLZ1_m10` | ~`438 / 576` |
    | **`t = 25m`** | `14:31:31` | **`4.306,2 MB` (~4,21 GiB)** | **`2.566,3 MB` (~2,51 GiB)** | **`382,7 MB`** | Concluindo `DTLZ_m10` e iniciando `WFG1_m15` | **~`480 / 576`** |

5. **Monitoramento real da campanha completa `WFGStudy/campaign_phase` (`19–20/11/2026`, `PID 21848`, `-Dstudy.hvMethods=PISA,MC` e `-Dperf.hvTimeoutMs=3600000`):**
   - **Etapa de otimização (`17.280` jobs):** concluída integralmente em `19/11/2026` às `16:02:03` (`run_manifest.csv` gravado com todas as `34.560` frentes `FUN*.csv`/`FUN*.raw.csv`).
   - **Etapa sequencial de indicadores por dimensão (tempos reais gravados em disco por célula de `30` runs):**
     - `m = 2` (`96` células / `2.880` runs): `16:02:20` $\to$ `16:17:21` (**~15 min** total; pior célula `FDEA / DTLZ7_2` em **`10,51 s`** = `0,35 s/run`).
     - `m = 3` (`96` células / `2.880` runs): `16:17:31` $\to$ `16:33:26` (**~16 min** total; pior célula `NSGAIII / DTLZ5_3` em **`14,34 s`** = `0,48 s/run`).
     - `m = 5` (`96` células / `2.880` runs): `16:33:47` $\to$ `17:07:57` (**~34 min** total; pior célula `MOEADD / WFG7_5` em **`47,35 s`** = `1,58 s/run`, seguida por `MOEADD / WFG4_5` em `46,32 s` e `MOEADD / WFG6_5` em `44,79 s`). **Nenhuma** das `8.640` runs de `2D–5D` sofreu *timeout* nem chegou perto de `60 s`.
     - `m = 8` (`NSGAIII / WFG1_8`): concluiu em **`701,75 s` (`11m42s`)** para `30` runs (`23,39 s/run`), com todas as `30` runs exatas. Já na célula seguinte (`MOEADD / WFG1_8`), duas execuções isoladas do `ExactHypervolumeWorker` (`PISA`) em `20/11/2026` consumiram **`~38 min`** (`146` pts) e **`89,5 min`** (`wallTimeMs = 5370770`, `cpuTimeMs = 5096312`, `148` pts, finalizada após o encerramento do processo pai).
   - **Diagnóstico para `m >= 8` com `PISA` sob `-Dperf.hvTimeoutMs=3600000`:**
     - Como `computeContractualIndicatorsAndExport` executa os subprocessos exatos **sequencialmente (1 por vez)**, tentar `PISA` exato nas `287` células restantes de `m ∈ {8, 10, 15}` (`8.610` runs) mantendo `hvTimeoutMs=3600000` (1 h/run) pode bloquear a execução por **dezenas a centenas de dias** (teto absoluto de `120 dias` só no `8D` e `358,75 dias` somando `8D–15D`).
     - Mesmo reduzindo `hvTimeoutMs` para `60000` ms (`1 min`) apenas em `8D`, o pior caso ainda chegaria a `~48 h` se muitas runs de `8D` sofressem *timeout*.
     - **Solução adotada sem alterar `hvTimeoutMs=3600000`:** usar a propriedade opt-in **`-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC`**, preservando `PISA` exato + `HV_MC` onde é rápido (`2D–5D` em ~`1h05m`) e avaliando `HV_MC`, `IGD`, `IGD+` e `R2` completos em `8D–15D` (registrando `NA` explícito em `HV` para `m >= 8`, sem misturar aproximado em série exata).

---

## 2. Parâmetros que Efetivamente Controlam o Desempenho

Antes de montar qualquer comando, entenda os **quatro grupos de controle** que realmente determinam velocidade, uso de CPU, consumo de RAM e tempo de parede do experimento.

### 2.1. Paralelismo Real: `-Dstudy.parallelWorkers`, `-Dstudy.processIsolation=true`, LPT, Checkpointing e Indicadores Paralelos

Estes parâmetros atuam em conjunto e determinam o **acelerador principal** tanto na execução dos algoritmos quanto no cálculo de indicadores (etapa pré-execução final antes das hiper-heurísticas):

| Parâmetro | Padrão | Valores | Impacto Real no Desempenho e Defesas |
| :--- | :---: | :--- | :--- |
| **`-Dstudy.parallelWorkers`** | `1` | Inteiro $\ge 1$ | Define quantos workers concorrentes `IsolatedJobExecutor.runPendingJobsIncremental` mantém no `ExecutorCompletionService` durante a execução dos algoritmos (`17.280` jobs no estudo completo). |
| **`-Dstudy.processIsolation`** | `false` | `true` \| `false` | **Obrigatório como `true` para haver paralelismo real entre MOEAs.** Quando `false`, os jobs rodam na mesma JVM sob o lock global `JMETAL_RANDOM_LOCK` (porque `JMetalRandom.getInstance()` é um singleton estático compartilhado no jMetal 7.8). Nota: a reconstrução de soluções na persistência usa agora `ResultFrontContract.createUnseededSolution`, eliminando corridas ocultas em `JMetalRandom` mesmo com `processIsolation=false`, mas manter `false` com `parallelWorkers > 1` continua serializando os algoritmos em `1x`. |
| **`-Dstudy.lptScheduling`** | Automático (`true` se `parallelWorkers > 1`) | `true` \| `false` | Ordena os jobs pendentes do maior custo estimado para o menor (**LPT — *Longest Processing Time first***: `m=10` `AGE-MOEA`/`AGE-MOEA-II` e `m=15` primeiro; `m=2,3` por último), reduzindo núcleos ociosos na cauda do lote sem alterar sementes (`SplitMix64` puro por tupla). Rejeita antecipadamente valores inválidos diferentes de `true`/`false`. |
| **`-Dstudy.checkpointInterval`** | `25` | Inteiro $\ge 1$ | Intervalo de consolidação atômica de `run_manifest.csv` após gravação imediata job-a-job de `FUN`/`VAR`/`.raw.csv` e append no journal `run_manifest.checkpoint.csv`. Reduz reescritas repetidas $O(K^2)$ em campanhas longas; ao fim do lote (ou em recuperação pós-queda), `run_manifest.checkpoint.csv` é conciliado ignorando no máximo uma última linha truncada e preservando integralmente a validação SHA-256/`FUN`↔`VAR` em `verifyExistingRunForResume`. |
| **`-Dstudy.indicatorWorkers`** | Igual a `study.parallelWorkers` | Inteiro $\ge 1$ | Define quantas threads concorrentes executam `WFGStudy.computeContractualIndicatorsAndExport` (ordem heaviest-first, com `IGD`/`IGD+`/`R2` isolados por thread via `ThreadLocal` porque `R2.compute` do jMetal 7.8 muta `weightVectors` e `idealPoint`). Acelera `HV_MC` (`MonteCarloHypervolume` com `SplittableRandom` local), `IGD`/`IGD+`/`R2` e hipervolumes em paralelo, reconstruindo a ordem canônica na escrita de `QualityIndicatorSummary.csv` e `hv_audit_manifest.csv`. |
| **`-Dstudy.exactHvParallelWorkers`** | `indicatorWorkers` ($m \le 5$); $\min(\text{indicatorWorkers}, 2)$ ($m \ge 8$) | Inteiro $\ge 1$ | Limita simultaneamente os subprocessos `ExactHypervolumeWorker` (`PISA`/`WFG`/`NHV`) por semáforos dedicados, evitando explosão de RAM caso métodos exatos sejam acionados em $m \ge 8$ com `indicatorWorkers` elevado. |

**Como escolher o valor ideal de `study.parallelWorkers` e `study.indicatorWorkers`:**
- Como cada worker JVM de algoritmo é **single-threaded**, o ganho escala quase linearmente até atingir o número de **núcleos/threads físicas e lógicas disponíveis** e o **limite de RAM livre** ($\approx 220\text{–}395\text{ MB}$ por worker).
- **Regra prática para CPUs híbridas / desktop / notebook:**
  - *Com sistema/IDE em uso e pouca RAM livre (`< 6 GB`):* use **`8`** a **`10`** (prioriza os P-Cores sem disputa com o sistema nem paginação). Na etapa de indicadores, mantendo `-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC`, `indicatorWorkers` herda `parallelWorkers` (`10`) com segurança (sem subprocessos exatos pesados em $m \ge 8$).
  - *Máquina dedicada (`> 11 GB` livres em CPU 16T):* use **`N_threads - 2`** = **`14`** (satura P-Cores e E-Cores mantendo folga para o SO e o Orquestrador).
- **Regra prática para servidor dedicado homogêneo (ex.: `Xeon E-2356G` 6C/12T):**
  - Use **`11`** (ou **`12`**, pois o Orquestrador fica quase `0%` de CPU enquanto aguarda `process.waitFor`). **Não ultrapasse `12`**: colocar `parallelWorkers=24` ou `32` em uma CPU de 12 threads **piora** o desempenho por disputa de cache L1/L2/L3 e *context switching* no Linux.
- **Cuidado operacional se habilitar exato (`PISA`/`WFG`) em $m \ge 8$ com `indicatorWorkers > 1`:** o teto padrão automático de `2` workers exatos em $m \ge 8$ protege contra *out-of-memory*; não eleve `-Dstudy.exactHvParallelWorkers` acima de `2–4` em `m >= 8` em notebooks com `16 GB` de RAM.

---

### 2.2. Memória da JVM: `MAVEN_OPTS`, `-Dstudy.workerJvmArgs`, `JDK_JAVA_OPTIONS`, `-Xms` e `-Xmx`

Ao disparar `mvn exec:java`, coexistem dois níveis de processos Java na máquina, com regras de herança e isolamento agora controláveis separadamente:

1. **JVM Pai (Maven + Orquestrador `WFGStudy.main()`):**
   - Lê **`MAVEN_OPTS`**.
   - **Por que não exagerar no `-Xms`?** `-Xms` trava RAM imediatamente na largada mesmo sem uso. Em 25 minutos com `10` workers, a JVM Pai usou estavelmente **`381–393 MB`** de Working Set. Usar `-Xms6g` imobiliza `6 GB` ociosos sem ganho algum.
   - **Como dimensionar `-Xmx` da JVM Pai:** como `IsolatedJobExecutor` grava e libera imediatamente as matrizes `double[][]` via `compactRetainingMetadataOnly()` e `WFGStudy.main()` descarta `batchReport = null` antes dos indicadores, o Heap do Pai não acumula os `17.280` pares de matrizes em memória; precisa apenas acomodar o preflight, os metadados leves, buffers paralelos dos indicadores (`normRefFront`, `IndicatorCalculators` por thread) e estatística.
     - Notebook com pouca RAM livre: **`-Xms256m -Xmx1536m`** (ou `-Xmx2g` se dedicado).
     - Servidor Linux com `128 GB`: **`-Xms2g -Xmx8g`**.
   - **Atenção ao custo oculto (*Non-Heap*):** toda JVM ocupa além do Heap cerca de **`~120–250 MB`** extras (*Metaspace*, *CodeCache* JIT, pilhas de threads). Um `-Xmx1536m` pode chegar a `~1,75 GB` reais no SO caso atinja o teto.

2. **JVMs Filhas (`IsolatedJobExecutor --worker` e `ExactHypervolumeWorker`) via `-Dstudy.workerJvmArgs`:**
   - **Não herdam `MAVEN_OPTS`** (o processo Pai inicia cada worker via `ProcessBuilder`). Em execução normal no `i5-13450HX`, cada worker consumiu **`~185–277 MB`**, com picos de **`395 MB`** em `AGE-MOEA` `m=10`.
   - **Preferir `-Dstudy.workerJvmArgs` em vez de `JDK_JAVA_OPTIONS` global:** `-Dstudy.workerJvmArgs` aplica flags (ex.: `"-Xms128m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1"`) **exclusivamente** aos subprocessos workers de otimização e de hipervolume exato, evitando afetar o `MAVEN_OPTS` do Pai nem restringir o `ActiveProcessorCount` da JVM Pai durante o cálculo paralelo de indicadores!
   - **Defesas automáticas no código (`ProcessIsolatedHypervolumeExecutor.parseWorkerJvmArgs`):**
     - Preserva aspas e contrabarras de caminhos Windows (ex.: `-Djava.io.tmpdir=...`);
     - Rejeita aspas não fechadas, tokens sem `-` inicial e flags reservadas ao supervisor (`-cp`, `-classpath`, `--class-path`, `-jar`, `-m`, `--module`, `-p`, `--module-path`, `-version`, `--help`);
     - Falha antecipadamente (*fail-fast* no início de `WFGStudy.main()`) se `-Xmx < 64m` ou `-Xms > -Xmx`, e emite aviso em `stderr` se `-Xmx < 384 MiB` (pelo pico observado de ~`395 MB` em `AGE-MOEA` $m=10$).
   - **Fórmula de segurança anti-paginação (Windows/Linux):**
     $$\text{RAM}_\text{pico} \approx \text{JVM Pai }(\text{tipicamente } 400\text{ MB a }\text{Xmx}_{\text{pai}}) + W \times \max(350\text{ MB}, \text{Xmx}_{\text{worker ocupado}})$$
     No notebook com `~5,5 GB` livres: $1.536\text{ MB (teto Pai)} + 10 \times 350\text{ MB} \approx 5.036\text{ MB}$; usando `-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1`, nenhum worker dispara consumo descontrolado e o G1GC devolve heap ocioso ao Windows.
   - **Nota sobre `JDK_JAVA_OPTIONS`:** evite definir `-XX:ActiveProcessorCount=1` em `JDK_JAVA_OPTIONS` global porque isso caparia também os pools multithread da etapa paralela de indicadores na JVM Pai; prefira sempre passar `-XX:ActiveProcessorCount=1` dentro de **`-Dstudy.workerJvmArgs`**.

---

### 2.3. Coletor de Lixo (`-XX:+UseG1GC` vs. `-XX:+UseParallelGC`) e Flags HotSpot

| Flag JVM | Onde Usar | Efeito Prático Medido e Recomendado |
| :--- | :--- | :--- |
| **`-XX:+UseG1GC`** | **Notebook Windows (obrigatório)** e padrão seguro no Servidor | Padrão moderno do Java 21. Coleta regiões incrementalmente com pausas curtas e **devolve regiões ociosas de heap ao sistema operacional**, mantendo a RAM livre estável sem swap (observado na prática entre `t=0m` e `t=5m`, quando a RAM livre subiu de `3,57` para `4,01 GiB`). |
| **`-XX:+UseParallelGC`** | **Opcional apenas no Servidor Linux (`128 GB`)** | Maximiza *throughput* bruto em tarefas que alocam milhões de objetos temporários por segundo (`AGE-MOEA`, `MonteCarloHypervolume`), às custas de reter mais heap sem devolver ao SO. Só vale testar se houver memória folgada (`128 GB`). |
| **`-XX:ActiveProcessorCount=1`** | **Via `-Dstudy.workerJvmArgs` nos workers *(recomendado)*** | Evita que `W` JVMs filhas simultâneas criem cada uma pools internos de threads GC/JIT dimensionados para todos os núcleos lógicos (reduz contenção desnecessária), **sem** capar a JVM Pai durante a etapa paralela de indicadores (como ocorreria se passado globalmente via `JDK_JAVA_OPTIONS`). |

---

### 2.4. Outros Parâmetros que Afetam Diretamente Tempo de Execução e Escala

| Parâmetro (`-D...`) | Padrão | Valores Aceitos | Impacto Real em Tempo e Recursos |
| :--- | :---: | :--- | :--- |
| **`-Dstudy.workerJvmArgs`** | `null` | Flags JVM separadas por espaço (aspas simples/duplas aceitas) | Injeta heap/GC/tmpdir exclusivamente nos subprocessos workers (`IsolatedJobExecutor --worker` e `ExactHypervolumeWorker`) sem afetar a JVM Pai; falha antecipadamente se `-Xmx < 64m`, `-Xms > -Xmx` ou flags reservadas (`-cp`, `-jar`, `-m`), e alerta se `-Xmx < 384m`. |
| **`-Dstudy.lptScheduling`** | Auto (`true` se `parallelWorkers > 1`) | `true` \| `false` | Prioriza os jobs de otimização mais longos primeiro (`AGE-MOEA`/`II`, `FDEA`, `m >= 8`) para eliminar núcleos ociosos no fim do lote sem alterar sementes nem a ordem final de `run_manifest.csv`. |
| **`-Dstudy.checkpointInterval`** | `25` | Inteiro `>= 1` | Define a cada quantos jobs persistidos o journal `run_manifest.checkpoint.csv` é consolidado atomicamente no `run_manifest.csv`. |
| **`-Dstudy.indicatorWorkers`** | `study.parallelWorkers` | Inteiro `>= 1` | Paraleliza `IGD`, `IGD+`, `R2`, `HV_MC` e chamadas `ExactHypervolumeWorker` entre threads na etapa de indicadores (`R2`/`IGD`/`IGD+` isolados por thread via `ThreadLocal`). |
| **`-Dstudy.exactHvParallelWorkers`** | `indicatorWorkers` ($m \le 5$); `min(w, 2)` ($m \ge 8$) | Inteiro `>= 1` | Teto defensivo de subprocessos simultâneos de hipervolume exato (`PISA`/`WFG`/`NHV`), limitando a concorrência exata em $m \ge 8$ contra pressão de memória. |
| **`-Dstudy.hvMethods`** | `PISA,MC` | Subconjunto CSV de `PISA` (`HV`), `WFG` (`HV_WFG`) e `MC` (`HV_MC`). *(Bloqueio em código de `NHV` removido; mantida inativa no padrão por não ser chamada na etapa atual).* | Controla quantos cálculos de hipervolume rodam em **cada uma das `17.280` frentes**:<br>• `PISA,MC`: 1 subprocesso `ExactHypervolumeWorker` (`PISA`) + 1 chamada `MonteCarloHypervolume`;<br>• `PISA,WFG,MC`: **dobra** os spawns de subprocessos exatos;<br>• `MC`: pula totalmente o exato (muito rápido, mas sem série exata `HV`). |
| **`-Dstudy.hvMethodsByM`** | `null` *(opt-in desativado)* | Regras `<condicao>:<metodos>;...` avaliadas em ordem (`<=`, `<`, `>=`, `>`, `=`, `a-b`, `a,b`, `le`/`lt`/`ge`/`gt`/`eq`, `*`/`default`). | **Alternativa recomendada para campanha completa (`2D–15D`) em comando único mantendo `hvTimeoutMs=3600000`.** Ex.: `<=5:PISA,MC;>=8:MC` executa `PISA+MC` até `5D` (já em paralelo via `indicatorWorkers`) e apenas `MC` (`HV_MC` + `IGD`/`IGD+`/`R2`, todos paralelizados sem semáforo exato) em `8D–15D` (gravando `NA` em `HV` para `m >= 8`). |
| **`-Dperf.hvTimeoutMs`** | `3600000` (1 h) | Inteiro `long > 0` (ms) | **Maior fator de risco no tempo de pós-processamento quando `PISA`/`WFG` são ativados em `m >= 8` (`8D`, `10D`, `15D`).** Frentes densas interiores em `m=8` (`MOEADD/WFG1_8`: `~38 min` e `89,5 min` medidos em uma única run) e `m=15` (`> 11,35 h` em `WFG4` 15D) entram em explosão combinatória $O(N^{m/2})$. Mesmo com `indicatorWorkers > 1` (limitado defensivamente a `exactHvParallelWorkers=2` para $m \ge 8$), `8.640` runs em `8D–15D` sofrendo timeouts de 1h ainda levariam centenas de dias divididas por `2`. Mantenha `3600000` combinado com **`-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC`**, ou reduza `hvTimeoutMs` apenas se insistir em testar exato em `m >= 8`. |
| **`-Dstudy.hvMcSamples`** | `1000000` ($10^6$) | Inteiro `long > 0` | Custo linear no tempo do `MonteCarloHypervolume` sequencial e precisão estatística $\propto 1/\sqrt{S}$ ($\text{SE}_{\max} \le V / (2\sqrt{S})$). Em `1000000`, oferece erro padrão máximo de `0,05%` do volume da caixa em tempo aceitável. Reduzir para `100000` acelera ~10x (com $\text{SE} \approx 3{,}16\times$ maior); aumentar para `10000000` encarece ~10x. |
| **`-Dstudy.jobTimeoutMs`** | `3600000` (1 h) | Inteiro `long > 0` (ms) | Limite máximo por job individual de algoritmo (`IsolatedJobExecutor --worker`). Com `N=276` e `2000` gerações em `m=10`, o pior job (`AGE-MOEA`/`II`) levou poucos minutos; `3600000` dá folga segura contra abortos falsos sem travar indefinidamente. |
| **`-DprofilePerformance`** | `false` | `true` \| `false` | **Mantenha `false` na campanha.** O estudo já exporta `profiling/job_execution_profile.csv` automaticamente com os tempos reais. Ligar `true` dispara ao final mais uma bateria completa de benchmarks `PerformanceProfiler`. |
| **`-Dstudy.includeLegacyAlgorithms`** | `false` | `true` \| `false` | **Mantenha `false`.** Ligar `true` executa sequencialmente mais 5 algoritmos legados (`NSGAII`, `SPEA2`, `MOEADDRA`, `ESPEA`, `GWASFGA`) em `cores=1` fora do `IsolatedJobExecutor`, multiplicando o tempo e o consumo de Heap. |
| **`-Dstudy.skipRValidation`** | `false` | `true` \| `false` | Em campanha completa, manter `false` custa apenas alguns segundos ao fim para validar Kruskal–Wallis e Dunn–Holm no `Rscript`. Só use `true` se quiser pular intencionalmente a checagem cruzada em R. |

---

## 3. Exemplos Concretos de Execução Completa (Notebook vs. Servidor Linux)

Os comandos abaixo executam a **campanha científica completa de uma só vez** (`6` algoritmos principais $\times$ `16` problemas em `6` dimensões = `96` pares $\times$ `30` runs = **`17.280` jobs**, seguidos de indicadores, análise estatística, validação em R e tabelas LaTeX).

### 3.0. Checagem de Ambiente e Instalação de Dependências (Linux e Windows)

Antes de disparar a campanha em uma máquina nova (ou servidor via SSH), verifique hardware, RAM, Java 21, Maven e R em 1 linha:

* **`Linux Bash` / `Git Bash`:**
  ```bash
  echo "=== OS & CPU ==="; uname -a; nproc; lscpu 2>/dev/null | grep -E "Model name|CPU\(s\)|Thread|Core" || true; echo "=== RAM & DISCO ==="; free -h 2>/dev/null || true; df -h . /dev/shm 2>/dev/null || df -h .; echo "=== JAVA ==="; echo "JAVA_HOME=${JAVA_HOME:-NAO_DEFINIDO}"; java -version 2>&1 || echo "java: AUSENTE"; echo "=== MAVEN ==="; mvn -v 2>&1 || echo "mvn: AUSENTE"; echo "=== R ==="; which Rscript 2>/dev/null && Rscript --version 2>&1 || echo "Rscript: AUSENTE (opcional para validacao R)"; echo "=== GIT & TMUX ==="; git --version 2>&1 || echo "git: AUSENTE"; tmux -V 2>&1 || echo "tmux: AUSENTE"
  ```
* **Windows `CMD` (`cmd.exe`):**
  ```cmd
  echo === CPU & RAM === && powershell -NoProfile -Command "$cs=Get-CimInstance Win32_ComputerSystem; $os=Get-CimInstance Win32_OperatingSystem; Write-Output ('Cores/Threads: ' + $cs.NumberOfProcessors + '/' + $cs.NumberOfLogicalProcessors + ' | RAM Livre/Total (GB): ' + [math]::Round($os.FreePhysicalMemory/1MB,2) + '/' + [math]::Round($cs.TotalPhysicalMemory/1GB,2))" && echo === JAVA === && echo JAVA_HOME=%JAVA_HOME% && java -version && echo === MAVEN === && call mvn -v && echo === R & GIT === && (where Rscript >nul 2>&1 && Rscript --version || echo Rscript: AUSENTE) && git --version
  ```

Caso `java` (JDK 21) ou `mvn` estejam ausentes:
* **Linux — Opção A (com `sudo`):**
  ```bash
  sudo apt update && sudo apt install -y openjdk-21-jdk-headless maven r-base-core tmux
  ```
* **Linux — Opção B (sem `sudo`, instalando JDK 21 Temurin + Maven 3.9.9 em `~/tools`):**
  ```bash
  mkdir -p ~/tools && cd ~/tools && \
  curl -fsSL -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" && \
  mkdir -p jdk21 && tar -xzf jdk21.tar.gz -C jdk21 --strip-components=1 && rm jdk21.tar.gz && \
  curl -fsSL -o maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" && \
  mkdir -p maven && tar -xzf maven.tar.gz -C maven --strip-components=1 && rm maven.tar.gz && \
  echo 'export JAVA_HOME="$HOME/tools/jdk21"' >> ~/.bashrc && \
  echo 'export M2_HOME="$HOME/tools/maven"' >> ~/.bashrc && \
  echo 'export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"' >> ~/.bashrc && \
  source ~/.bashrc && java -version && mvn -v
  ```
* **Windows (`CMD` ou `Git Bash`) — Opção A (via `winget`):**
  ```cmd
  winget install EclipseAdoptium.Temurin.21.JDK && winget install Apache.Maven
  ```
* **Windows (`CMD`) — Opção B (sem instalador/admin, em `%USERPROFILE%\tools`):**
  ```cmd
  powershell -NoProfile -Command "$t=\"$env:USERPROFILE\tools\"; New-Item -ItemType Directory -Force -Path \"$t\jdk21\",\"$t\maven\" | Out-Null; Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile \"$t\jdk21.zip\"; Expand-Archive \"$t\jdk21.zip\" -DestinationPath \"$t\jdk21_tmp\" -Force; Move-Item \"$t\jdk21_tmp\*\*\" \"$t\jdk21\" -Force; Remove-Item \"$t\jdk21.zip\",\"$t\jdk21_tmp\" -Recurse -Force; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip' -OutFile \"$t\maven.zip\"; Expand-Archive \"$t\maven.zip\" -DestinationPath \"$t\maven_tmp\" -Force; Move-Item \"$t\maven_tmp\*\*\" \"$t\maven\" -Force; Remove-Item \"$t\maven.zip\",\"$t\maven_tmp\" -Recurse -Force" && setx JAVA_HOME "%USERPROFILE%\tools\jdk21" && setx PATH "%USERPROFILE%\tools\jdk21\bin;%USERPROFILE%\tools\maven\bin;%PATH%"
  ```
  *(Nota: sem `tmux` no Linux, utilize `nohup ... > campaign.log 2>&1 &`; sem `Rscript`, o pipeline segue normalmente pulando apenas a validação cruzada em R).*

### 3.1. Perfil 1 — Notebook Windows 11 (`i5-13450HX` 10C/16T, `16 GB` RAM, `RTX 3050 6GB`)

#### Cenário A: Uso simultâneo com Windows/IDE abertos (`< 6 GB` de RAM livres — Testado ao vivo por 25 min)

* **Windows `CMD` (`cmd.exe`) — Linha única (referência direta `PISA,MC` em todas as dimensões):**
```cmd
set MAVEN_OPTS=-Xms256m -Xmx1536m -XX:+UseG1GC&& mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.processIsolation=true" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethods=PISA,MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
```

* **Alternativa recomendada em 1 linha (`-Dstudy.hvMethodsByM` + `-Dstudy.workerJvmArgs`, `LPT`, checkpointing incremental e indicadores paralelos ativos):**
  - *Windows `CMD` (`cmd.exe`):*
    ```cmd
    set "MAVEN_OPTS=-Xms256m -Xmx1536m -XX:+UseG1GC" && mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.indicatorWorkers=10" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
    ```
  - *`Linux Bash` / `Git Bash` (Windows):*
    ```bash
    MAVEN_OPTS="-Xms256m -Xmx1536m -XX:+UseG1GC" mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=10" "-Dstudy.indicatorWorkers=10" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
    ```

* **Windows `CMD` (`cmd.exe`) — Legível em múltiplas linhas (`^`):**
```cmd
set MAVEN_OPTS=-Xms256m -Xmx1536m -XX:+UseG1GC
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" ^
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" ^
  "-DrunStart=0" "-Druns=30" ^
  "-Dobjectives=2,3,5,8,10,15" ^
  "-DgenerateAllReferenceFronts=true" ^
  "-Dstudy.parallelWorkers=10" ^
  "-Dstudy.processIsolation=true" ^
  "-Dperf.hvTimeoutMs=3600000" ^
  "-Dstudy.hvMethods=PISA,MC" ^
  "-Dstudy.hvMcSamples=1000000" ^
  "-DreplaceZeroWeights=false" ^
  "-DreplaceZeroFronts=false"
```

* **`Git Bash` (Windows) — Mesmo perfil:**
```bash
export MAVEN_OPTS="-Xms256m -Xmx1536m -XX:+UseG1GC"
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" \
  "-DrunStart=0" "-Druns=30" \
  "-Dobjectives=2,3,5,8,10,15" \
  "-DgenerateAllReferenceFronts=true" \
  "-Dstudy.parallelWorkers=10" \
  "-Dstudy.processIsolation=true" \
  "-Dperf.hvTimeoutMs=3600000" \
  "-Dstudy.hvMethods=PISA,MC" \
  "-Dstudy.hvMcSamples=1000000" \
  "-DreplaceZeroWeights=false" \
  "-DreplaceZeroFronts=false"
```

**Por que essa combinação funciona bem sob `< 6 GB` livres?**
- **`-Xms256m -Xmx1536m -XX:+UseG1GC`**: dá folga ainda maior ao Orquestrador (cujos `JobExecutionResult` agora liberam imediatamente os arrays `double[][]` de RAM via `compactRetainingMetadataOnly()` após gravação em disco, reduzindo retenção durante o lote e na transição para indicadores), permitindo ao G1GC devolver memória ao Windows continuamente.
- **`-Dstudy.parallelWorkers=10` + `processIsolation=true` + `-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1`**: mantém até `10` processos worker simultâneos (~`2,0–2,7 GB` totais observados em campo junto com o Pai), usando fila **LPT automática** e teto `-Xmx768m` + `ActiveProcessorCount=1` exclusivo dos subprocessos sem afetar a JVM Pai na etapa paralela de indicadores. *(Se quiser margem extra de ~`700 MB`, reduza `parallelWorkers` para **`8`**).*
- **`-Dstudy.indicatorWorkers=10` (implícito se omitido, herdando `parallelWorkers`)**: paraleliza `IGD`, `IGD+`, `R2`, `HV_MC` e os workers `PISA` até `5D` (protegidos por semáforo defensivo caso `m >= 8` seja habilitado).
- **`-DgenerateAllReferenceFronts=true`**: garante a inclusão de `DTLZ1..7` em `m ∈ {5, 8, 10, 15}` (todas as 96 combinações problema×dimensão).
- **`-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC` + `-Dstudy.hvMcSamples=1000000` + `-Dperf.hvTimeoutMs=3600000`**: calcula ambos `HV` exato isolado e `HV_MC` onde é viável (`2D–5D`) e `HV_MC` + `IGD`/`IGD+`/`R2` em `8D–15D` sem bloqueios de 1 h/run.

#### Cenário B: Notebook Dedicado (Recém-reiniciado, IDE/navegadores fechados, `~11–12 GB` livres)
Altere apenas **`MAVEN_OPTS`** e **`study.parallelWorkers`** no comando acima:
- **`MAVEN_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC`**
- **`-Dstudy.parallelWorkers=14`**

Isso utiliza **14 das 16 threads** do `i5-13450HX` deixando `2` threads livres para o SO e para o Pai (`~5–7 GB` de consumo máximo total no pico, ainda com `> 4 GB` livres de segurança).

* **Alternativa em 1 linha (`-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC`, `14` workers, `-Xmx2g` + `-Dstudy.workerJvmArgs`) — Windows `CMD` e `Linux Bash` / `Git Bash`:**
  - *Windows `CMD` (`cmd.exe`):*
    ```cmd
    set "MAVEN_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC" && mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=14" "-Dstudy.indicatorWorkers=14" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
    ```
  - *`Linux Bash` / `Git Bash`:*
    ```bash
    MAVEN_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC" mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=14" "-Dstudy.indicatorWorkers=14" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms64m -Xmx768m -XX:+UseG1GC -XX:ActiveProcessorCount=1" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
    ```

---

### 3.2. Perfil 2 — Servidor Linux (`Intel Xeon E-2356G` 6C/12T @ 3.20 GHz, `128 GB DDR4-3200`, `1 TB NVMe`, `RTX 5080`)

No servidor Linux, você tem duas vantagens críticas ausentes no notebook: **(1) frequência sustentada sem *thermal throttling* nos 6 núcleos Rocket Lake (12 threads homogêneas)** e **(2) `~115–124 GB` de RAM livre** que permitem colocar os diretórios temporários em memória (`tmpfs` `/dev/shm`) e evitar desgaste/IOPS temporários no SSD NVMe.

* **`Linux Bash` — Campanha completa em execução única desassistida:**
```bash
# 1. Cria diretório temporário em RAM (/dev/shm) para os subdiretórios efêmeros de workers
mkdir -p /dev/shm/hypemoea_tmp
trap 'rm -rf /dev/shm/hypemoea_tmp' EXIT

# 2. Configura JVM Pai via MAVEN_OPTS (sem restringir ActiveProcessorCount do Pai via JDK_JAVA_OPTIONS)
export MAVEN_OPTS="-Xms2g -Xmx8g -XX:+UseG1GC"

# 3. Executa a campanha completa de uma única vez (use dentro de `tmux` ou `nohup` via SSH)
mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" \
  "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" \
  "-DrunStart=0" "-Druns=30" \
  "-Dobjectives=2,3,5,8,10,15" \
  "-DgenerateAllReferenceFronts=true" \
  "-Dstudy.parallelWorkers=11" \
  "-Dstudy.indicatorWorkers=11" \
  "-Dstudy.processIsolation=true" \
  "-Dstudy.workerJvmArgs=-Xms128m -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=1 -Djava.io.tmpdir=/dev/shm/hypemoea_tmp" \
  "-Dperf.hvTimeoutMs=3600000" \
  "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" \
  "-Dstudy.hvMcSamples=1000000" \
  "-DreplaceZeroWeights=false" \
  "-DreplaceZeroFronts=false"
```

* **Alternativa recomendada em 1 linha para Servidor Linux**
```bash
MAVEN_OPTS="-Xms2g -Xmx8g -XX:+UseG1GC" mvn exec:java "-Dexec.mainClass=br.usp.lti.newmoea.study.WFGStudy" "-Dstudy.outputDirectory=WFGStudy/campaign_phase6" "-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15" "-DgenerateAllReferenceFronts=true" "-Dstudy.parallelWorkers=11" "-Dstudy.indicatorWorkers=11" "-Dstudy.processIsolation=true" "-Dstudy.workerJvmArgs=-Xms128m -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=1 -Djava.io.tmpdir=/dev/shm/hypemoea_tmp" "-Dperf.hvTimeoutMs=3600000" "-Dstudy.hvMethodsByM=<=5:PISA,MC;>=8:MC" "-Dstudy.hvMcSamples=1000000" "-DreplaceZeroWeights=false" "-DreplaceZeroFronts=false"
```
*(Dica operacional em servidor: rode dentro de `tmux new -s hypemoea` ou grave o comando em `run_campaign.sh` e dispare com `nohup bash run_campaign.sh > campaign.log 2>&1 &` para não abortar se o SSH cair.)*

**Impacto de cada parâmetro no Servidor Linux Xeon:**
- **`-Dstudy.parallelWorkers=11` + `-Dstudy.indicatorWorkers=11`** *(ou `12` se o servidor for exclusivo da campanha)*: satura os **12 fluxos lógicos do Xeon E-2356G (`6C/12T`)** com folga de 1 thread para o kernel/I/O NVMe, agora tanto na fase evolutiva (com **fila LPT automática**) quanto na fase de indicadores (`HV_MC`, `IGD`, `IGD+`, `R2` e `PISA <= 5D`). **Não ultrapasse `12`**: usar `parallelWorkers=24` ou `32` piora por contenção de cache.
- **`MAVEN_OPTS="-Xms2g -Xmx8g -XX:+UseG1GC"`**: dá amplitude ao Orquestrador para o cálculo paralelo de indicadores (`ThreadLocal<IndicatorCalculators>`), estatística Kruskal–Wallis/Dunn–Holm/Friedman e `Rscript`. *(Graças ao uso de `-Dstudy.workerJvmArgs` em vez de `JDK_JAVA_OPTIONS`, o Pai mantém todos os núcleos visíveis na etapa de indicadores).*
- **`-Dstudy.workerJvmArgs="-Xms128m -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=1 -Djava.io.tmpdir=/dev/shm/hypemoea_tmp"`**:
  - `-Xmx2g`: garante que nenhum worker filho tenha pressão de heap (consumo máximo pior caso: $8\text{ GB Pai} + 11 \times 2\text{ GB} = 30\text{ GB} \ll 128\text{ GB}$);
  - `-XX:ActiveProcessorCount=1`: impede que 11 JVMs filhas simultâneas criem $11 \times 12 = 132$ threads ociosas de GC/JIT, preservando os pools do Pai intactos;
  - `-Djava.io.tmpdir=/dev/shm/hypemoea_tmp`: mantém os binários transitórios (`raw_fun.bin`, `raw_var.bin`, `front.csv` do HV) 100% em RAM DDR4-3200, preservando o NVMe apenas para os artefatos canônicos de `WFGStudy/campaign_phase6/`.

---

### 3.3. Como Adaptar para Dividir a Campanha em Execuções Menores (Opcional)

Com a implementação do ***checkpointing* incremental job-a-job**, o risco anterior de perder horas acumuladas se a máquina desligasse antes do fim do lote foi **eliminado**: `IsolatedJobExecutor` grava cada `FUN<r>.csv`/`VAR<r>.csv`/`.raw.csv` imediatamente no disco e faz *flush* no journal `run_manifest.checkpoint.csv`, consolidando `run_manifest.csv` atomicamente a cada `-Dstudy.checkpointInterval` jobs (`25` por padrão). Ainda assim, se preferir **dividir o mesmo estudo em lotes menores** (ex.: janelas noturnas programadas), mantenha exatamente o mesmo `-Dstudy.outputDirectory=WFGStudy/campaign_phase6` e o mesmo `-Dstudy.baseSeed`:

1. **Por dimensão (`-Dobjectives`):** execute em sequência `-Dobjectives=2,3,5`, depois `8`, depois `10`, depois `15`. Cada lote persiste seus arquivos no disco ao terminar.
2. **Por fatia de repetições (`-DrunStart` + `-Druns`):** por exemplo, 3 blocos de 10 runs (`-DrunStart=0 -Druns=10`, depois `-DrunStart=10 -Druns=10`, depois `-DrunStart=20 -Druns=10`). *Observação:* os scripts/tabelas legados do jMetal (`GenerateLatexTablesWithStatistics` e `R/`) só rodam quando há repetições contínuas a partir de `run=0` (`contiguousRunsFromZero >= 1`).
3. **Consolidação final instantânea (sem retrabalho):** após finalizar todos os lotes parciais, execute **uma última vez o comando completo** (`"-DrunStart=0" "-Druns=30" "-Dobjectives=2,3,5,8,10,15"`). O sistema verifica automaticamente os hashes SHA-256 via `verifyExistingRunForResume` e os indicadores já avaliados via `canReuseExistingEvaluatedRunIndicators`, **pula 100% das execuções e cálculos de hipervolume já concluídos** e gera em segundos os relatórios estatísticos globais (`statistics/*`, `QualityIndicatorSummary.csv`, `latex/*` e `R/*`) consolidados.

---

## 4. Arquitetura, Infraestrutura e Otimizações Estruturais (Impacto Indireto e Evoluções Futuras)

Esta seção reúne mecanismos já presentes na arquitetura, aspectos de infraestrutura (`GPU`, `NVMe`, `Rede`) e **melhorias recomendadas no código** que preservam integralmente o `JMetalRandom` e as funções oficiais do jMetal `7.8`.

### 4.1. Infraestrutura e Recursos já Otimizados na Arquitetura Atual

- **Instanciação *lazy* e preflight único:** diferentemente de `executeLegacyAlgorithmsOnly` (que instancia tudo antes de rodar), os 6 algoritmos principais só alocam população e operadores durante o job (`LazyIsolatedAlgorithmAdapter`), liberando a memória imediatamente ao encerrar cada job; pesos e frentes são validados uma única vez no preflight (`runPreflightValidationAndResolveArtifacts`).
- **Armazenamento NVMe + escrita atômica:** todas as gravações críticas usam arquivos `.tmp` + `Files.move(..., ATOMIC_MOVE)` e travas por caminho (`acquireFileLock`), aproveitando o throughput do NVMe `1 TB` na escrita dos CSVs finais e evitando corrupção em retomadas.
- **Execução direta sem Maven (`target/lib`):** o [pom.xml](../pom.xml#L134-L146) já copia as dependências para `target/lib` na fase `install`. Em execuções repetidas em servidor, rodar `mvn clean package dependency:copy-dependencies -DskipTests` e disparar diretamente via `java` elimina o tempo de inicialização do Maven:
  - *Linux Bash / Git Bash:* `java -cp "target/classes:target/lib/*" ... br.usp.lti.newmoea.study.WFGStudy`
  - *Windows `CMD`:* `java -cp "target/classes;target/lib/*" ... br.usp.lti.newmoea.study.WFGStudy`
- **Placas de Rede (`2x Intel i210 1GbE` + `2x Intel X540-T2 10GbE Cat6A`):** em execução em nó único, a rede só é acessada caso `-Dstudy.restoreOfficialJMetalResources=true` precise restaurar um artefato ausente do GitHub. Em um eventual cenário distribuído entre múltiplos servidores via armazenamento compartilhado, as interfaces `10GbE` viabilizam fatiar `-DrunStart`/`-Druns` por máquina mantendo o determinismo exato de sementes.
- **Uso atual da GPU (`RTX 3050` / `RTX 5080`):** atualmente é **`0%`** porque todo o pipeline Java/jMetal executa na CPU. Os EAs evolutivos (`NSGA-III`, `MOEA/DD`, `FDEA`, `RVEA`, `AGE-MOEA`, `AGE-MOEA-II`) manipulam populações pequenas ($N = 91\text{–}276$) com ramificações em objetos Java e dependência sequencial do `JMetalRandom`, onde o custo de transferência PCIe CPU$\leftrightarrow$GPU supera qualquer paralelismo em shader cores.

### 4.2. Melhorias Estruturais no Código (Fidelidade Científica Alta ao jMetal)

Na **Fase 6 (pré-execução final antes da integração com hiper-heurísticas)**, foram implementadas as recomendações de código **1**, **3** e **5a** — preservando integralmente o `JMetalRandom` e as funções oficiais do jMetal `7.8` — enquanto as opções **2**, **4** e **5b** permanecem documentadas como evoluções futuras opcionais:

1. **[IMPLEMENTADO NA FASE 6] *Checkpointing* incremental imediato job-a-job + liberação imediata de memória (`IsolatedJobExecutor.executeAndPersistBatch` / `WFGStudy.java`):**
   - Substituída a coleta em bloco por `ExecutorCompletionService<JobExecutionResult>` em `runPendingJobsIncremental`, persistindo `FUN<r>.csv`, `VAR<r>.csv` e `.raw.csv` imediatamente após cada job concluído e liberando os arrays `double[][]` da RAM via `JobExecutionResult.compactRetainingMetadataOnly()` (`batchReport = null` em `WFGStudy.main()` após exportar `job_execution_profile.csv`).
   - Adicionado o journal incremental `run_manifest.checkpoint.csv` (flush por job) com consolidação atômica em `run_manifest.csv` a cada `-Dstudy.checkpointInterval` jobs (`25` por padrão).
   - **Defesas e recomendações:** em eventual queda abrupta, `reconcileCheckpointJournalIfPresent` aceita ignorar apenas eventual **última linha truncada** do journal (falhando explicitamente em corrupções intermediárias) e **jamais dispensa** `verifyExistingRunForResume` (hashes SHA-256 e alinhamento 1:1 `VAR`↔`FUN`). Mesmo sob conclusão fora de ordem ou LPT, `buildOrderedManifestEntries` reconstrói `run_manifest.csv` e a lista de retorno na ordem canônica. Além disso, `ResultFrontContract.createUnseededSolution` (`UnseededDoubleSolution`) substituiu `problem.createSolution()` na reconstrução de soluções, eliminando qualquer corrida oculta sobre `JMetalRandom.getInstance()` quando `reconstructSolutions` ocorre paralelamente no Pai.

2. **[ADIADO / NÃO IMPLEMENTADO NA FASE 6] Pool persistente de JVMs workers (*Warm Worker Daemon Pool* via pipes `stdin`/`stdout`):**
   - **Motivação futura:** evita reabrir **17.280 processos `java` novos** mantendo apenas $W$ daemons JVM com `JMetalRandom` isolado por processo e JIT C2 aquecido. Mantida fora da Fase 6 por maior complexidade operacional (gestão de vazamento de estado/heap acumulado entre jobs no daemon, reinício sob timeout e protocolo IPC persistente).

3. **[IMPLEMENTADO NA FASE 6] Paralelização multicore da etapa de indicadores (`WFGStudy.computeContractualIndicatorsAndExport`) + fila LPT (`IsolatedJobExecutor.orderJobSpecsLpt`):**
   - Toda a grade pendente de indicadores `(problema, alg, run)` executa via `ExecutorCompletionService` em `-Dstudy.indicatorWorkers` threads (padrão igual a `study.parallelWorkers`), com despacho heaviest-first e reconstrução da ordem canônica antes da escrita em disco de `QualityIndicatorSummary.csv`, `hv_audit_manifest.csv` e séries por célula (`IGD`, `IGD+`, `R2`, `HV`, `HV_MC` e `NHV` quando solicitada).
   - Adicionalmente, `orderJobSpecsLpt` + `estimateRelativeJobCost` ordenam jobs de otimização pendentes do mais pesado ao mais leve quando `-Dstudy.lptScheduling` está ativo (automático quando `parallelWorkers > 1`).
   - **Defesas e riscos mitigados:** inspeção do bytecode do jMetal 7.8 comprovou que `R2.compute(double[][])` muta campos de instância (`this.weightVectors` e `this.idealPoint`); por isso, cada problema mantém `ThreadLocal<IndicatorCalculators>` criando instâncias isoladas de `InvertedGenerationalDistance`, `InvertedGenerationalDistancePlus` e `R2` por thread (com `.remove()` em `finally`). O paralelismo de `ExactHypervolumeWorker` (`PISA`/`WFG`/`NHV`) é contido por semáforos (`lowDimExactPermits` em $m \le 5$; `highDimExactPermits` limitado por padrão a `min(w, 2)` em $m \ge 8$ via `-Dstudy.exactHvParallelWorkers`) para prevenir *out-of-memory* caso alguém habilite HV exato em dimensões altas com muitos workers. *(Risco operacional residual: se forçar `exactHvParallelWorkers > 2` em `m >= 8` com pouca RAM, o consumo dos subprocessos exatos crescerá proporcionalmente).*

4. **[ADIADO / NÃO IMPLEMENTADO NA FASE 6] Uso de GPU (`RTX 3050` / `RTX 5080`) ou sub-blocos paralelos dentro de `MonteCarloHypervolume`:**
   - Mantida fora da Fase 6 porque paralelizar no nível das células/runs (Opção 3) já satura os 12–16 núcleos lógicos em Java puro usando exatamente o `SplittableRandom(mcSeed)` canônico (`deriveDeterministicMcSeed`). Caso futuramente deseje elevar `hvMcSamples` para $10^8$–$10^9$ amostras, será obrigatório adotar PRNG *counter-based* (`Philox` ou `SplitMix64(seed + k)`) em `double` (FP64) puro para manter comparabilidade exata.

5. **Ajustes pontuais de baixo esforço:**
   - **a) [IMPLEMENTADO NA FASE 6] `-Dstudy.workerJvmArgs`** em [`IsolatedJobExecutor.executeJobInSubprocess`](../src/main/java/br/usp/lti/newmoea/util/IsolatedJobExecutor.java) e [`ProcessIsolatedHypervolumeExecutor.computeExactOnly`](../src/main/java/br/usp/lti/newmoea/util/ProcessIsolatedHypervolumeExecutor.java): permite ajustar heap/GC/`ActiveProcessorCount`/`tmpdir` apenas nos subprocessos workers, com tokenizer seguro para aspas/caminhos Windows, bloqueio estrito de `-cp`/`-classpath`/`-jar`/`-m`/`-p`/`-version`/`--help`, rejeição *fail-fast* de `-Xmx < 64m` ou `-Xms > -Xmx`, e aviso preventivo para `-Xmx < 384 MiB` (risco de OOM em `AGE-MOEA` $m=10$, onde o pico medido atinge `395 MB`).
   - **b) [ADIADO / NÃO IMPLEMENTADO NA FASE 6]** Propagar o mapa `preResolvedMoeadWeightsDir` resolvido no preflight para `BatchExecutionConfig` em [WFGStudy.java](../src/main/java/br/usp/lti/newmoea/study/WFGStudy.java), evitando releitura/revalidação repetida do arquivo `.dat` a cada job `MOEA/DD` (benefício marginal frente ao custo total da campanha; mantida validação integral atual).

---

## 5. Apêndice: Catálogo Secundário (Protocolo Científico, Artefatos e Diagnóstico)

Os parâmetros abaixo controlam **regras experimentais, diretórios, reprodutibilidade, observadores científicos ou testes automatizados** — não devem ser manipulados para tentar otimizar tempo/CPU/RAM, e muitos devem permanecer em seus padrões durante a campanha oficial.

### 5.1. Escopo, Semente, Filtros e Observador Geracional

| Propriedade (`-D...`) | Padrão | Valores Aceitos | Papel Científico / Operacional |
| :--- | :--- | :--- | :--- |
| `exec.mainClass` | `br.usp.lti.newmoea.study.WFGStudy` (`pom.xml`) | FQDN da classe (`WFGStudy`, `PerformanceProfiler`) | Entrypoint passado ao `exec-maven-plugin`. |
| `study.outputDirectory` | `"WFGStudy"` | Caminho relativo, aninhado (`WFGStudy/campaign_phase6`, `WFGStudy/tests/...`) ou absoluto *(proibido diretórios protegidos legados)* | Define a pasta destino de resultados, manifestos, indicadores e relatórios. Testes exploratórios devem usar `WFGStudy/tests/...`. |
| `objectives` | `2,3,5,8,10,15` | Subconjunto CSV de `2,3,5,8,10,15` | Dimensões avaliadas (`N`: `100, 91, 210, 156, 275, 135`). |
| `problem` | `null` (todos) | `WFG1..WFG9`, `DTLZ1..DTLZ7`, ou `<PROB>_<M>` (ex.: `WFG4_8`) | Filtra problema(s). *Nota:* quando preenchida, também habilita `DTLZ1..7` em $m > 3$ mesmo se `generateAllReferenceFronts=false`. |
| `runStart` / `runs` | `0` / `30` | `runStart >= 0`, `runs > 0` | Intervalo de repetições `[runStart, runStart + runs)`. |
| `study.baseSeed` | `20260409` (`20260409L`) | `long` de 64 bits | Semente mestre canônica dos EAs e do `MonteCarloHypervolume`. Não alterar entre lotes de uma mesma campanha (`PROTOCOL_MISMATCH`). |
| `study.maxEvaluationsOverride` | `null` | Inteiro $> 0$ e $\ge N_{\text{alloc}}(\text{alg}, m)$ | Exclusivo para *smoke tests* rápidos (`240` em `m=2`). Omitir na campanha real. |
| `study.enableR2Observer` | `false` | `true` \| `false` *(propagada)* | Coleta opt-in da trajetória geracional de $R^2$ (`data/<ALG>/<PROB>/R2_trace_run<r>.csv`) sem adicionar avaliações. Desativada por padrão por quem não analisa dinâmica geracional. |

### 5.2. Resolução de Frentes de Referência, Pesos, Normalização e Fallback de Hipervolume

| Propriedade (`-D...`) | Padrão | Valores Aceitos | Papel Científico / Operacional |
| :--- | :--- | :--- | :--- |
| `generateAllReferenceFronts` | `false` | `true` \| `false` | Deve ser **`true`** na campanha completa para habilitar e gerar/resolver frentes analíticas de `DTLZ1..7` em $m \in \{5, 8, 10, 15\}$. |
| `generateEmpiricalReferenceFronts` | `false` | `true` \| `false` | Habilita helpers empíricos em `ParetoFrontValidator` (não acionada por `WFGStudy.main()`). |
| `study.weightsDirectory` | `resources/weightVectorFiles` | Caminho válido *(propagada)* | Diretório base dos pesos `W<m>D_<N>.dat`. |
| `study.referenceFrontDirectory` | `resources/referenceFronts` | Caminho válido *(propagada)* | Diretório base das frentes de referência `.csv`. |
| `replaceZeroWeights` / `replaceZeroFronts` | `false` | `false` \| `none` \| `true` \| `ALL` \| lista CSV de algoritmos *(propagadas)* | Isolam variantes `0.0 -> 1.0e-5` em `weights_lowzero_gen/` e `pf_lowzero_gen/`. Manter `false`. |
| `study.allowMoeadWeightsInMemory` | `false` | `true` \| `false` *(propagada)* | Dispensa `.dat` em disco apenas para `MOEA/DD` em $m=2, 2 \le N \le 300$. |
| `persistReferencePoints` / `persistReferenceDirections` | `false` | `true` \| `false` | Persiste pontos Das & Dennis do NSGA-III (`buildNSGAIII`) e direções do RVEA (`buildRVEA`). |
| `study.restoreOfficialJMetalResources` | `false` | `true` \| `false` | Fallback para restaurar artefato oficial faltante a partir do espelho local `moead/` ou tag `jmetal-7.8` no GitHub (validado por SHA-256). |
| `study.officialJMetalRawBaseUrl` | GitHub raw tag `jmetal-7.8` | URL HTTP/HTTPS/file oficial | Sobrescreve origem oficial na restauração (bloqueia pastas legadas e cópia externa local). |
| `study.normalizationMode` | `THEORETICAL_PARETO_BOUNDS` | `THEORETICAL_PARETO_BOUNDS` \| `HYBRID_ENVELOPE_BOUNDS` \| `EMPIRICAL_REFERENCE_MIN_MAX` | Contrato matemático de normalização dos objetivos. |
| `study.hvPolicy` | `null` | `DUAL`/`EXACT_AND_MC`, `EXACT_ONLY`/`PISA_ONLY`, `WFG_ONLY`, `HEAVY_DUAL`/`PISA_AND_WFG`, `MC_ONLY` | Alias simplificado para `study.hvMethods` (usado apenas se `study.hvMethods` estiver vazia). |
| `study.hvFallbackToMcOnAllExactTimeout` | `true` | `true` \| `false` | Quando apenas métodos pesados (`PISA`/`WFG`) estão ativos e todos falham/dão timeout, decide se aciona `MC` 1 vez (`HV_FALLBACK_MC` + `hv_fallback_warnings.md`) ou mantém `NA`. |
| `study.rscriptPath` | `null` (autodetecta) | Caminho para `Rscript` / `Rscript.exe` | Sobrescreve detecção automática do executável R para validação estatística cruzada. |

### 5.3. Perfilamento Standalone (`PerformanceProfiler`) e Hooks de Simulação de Falha (`ExactHypervolumeWorker`)

| Propriedade (`-D...`) | Padrão | Papel |
| :--- | :--- | :--- |
| `perf.warmup`, `perf.runs`, `perf.output`, `perf.algorithms`, `perf.configs`, `perf.append`, `perf.summarizeOnly`, `perf.compareHvOnly` | `1`, `5`, `docs/performance/performance-results.csv`, `NSGA-III,MOEA/DD,RVEA,AGE-MOEA`, `WFG4:3,WFG4:8,WFG4:15,DTLZ2:3`, `false`, `false`, `false` | Parâmetros exclusivos da bateria de benchmark `PerformanceProfiler` (`T09`). *(Em `WFGStudy.main()`, apenas `perf.warmup` e `perf.runs` são lidos caso `profilePerformance=true`).* |
| `perf.maxHvFrontSize15D` | `0` (ilimitado) | Trunca frente aproximada em 15D **apenas** no benchmark `PerformanceProfiler` (não afeta `WFGStudy`). |
| `hype.hvWorkerSimulateCrash`, `hype.hvWorkerSimulateHangMs` | `false`, `0` *(propagadas)* | Hooks exclusivos de testes automatizados de supervisão do `ExactHypervolumeWorker`. **Nunca usar em execução experimental.** |

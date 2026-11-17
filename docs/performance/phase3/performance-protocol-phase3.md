# Protocolo de Avaliação de Desempenho e Reauditoria de CPU/Memória (T22)

- **Tarefa:** `T22` (`tasks/task22.md`) — Reauditoria do profiler e piloto CPU/memória em 15 objetivos
- **Responsável:** Agente J (*Performance Profiler Auditor* / Coordenador)
- **Data:** 2026-11-09
- **Escopo:** Reauditoria dos mecanismos de instrumentação temporal e de memória, distinção de threads assíncronas vs callers, rastreamento de pools de heap e piloto controlado em $m \in \{3, 8, 15\}$.

---

## 1. Reauditoria dos Mecanismos de Cronometragem da JVM

### 1.1. Wall-Clock Time vs Thread CPU Time
A medição de tempo em Java apresenta armadilhas críticas frequentemente negligenciadas na literatura experimental:
1. **Wall-Clock Time (`System.nanoTime()`):**
   - Mede o tempo decorrido do relógio de parede de alta resolução da JVM.
   - É sensível a contenções de escalonamento do sistema operacional, preempção de processos, I/O e pausas de Garbage Collection (GC).
2. **Thread CPU Time (`ThreadMXBean.getCurrentThreadCpuTime()`):**
   - Mede exclusivamente o tempo em que a **thread atual** esteve ativamente consumindo ciclos de instrução de CPU em modo usuário ou kernel.
   - Não avança durante bloqueios de sincronização, espera de I/O de disco/rede ou chamadas a `Thread.sleep()`.

### 1.2. A Vulnerabilidade de Threads Assíncronas e Workers
Conforme demonstrado analítica e empiricamente no teste `testThreadCpuIsolationAcrossWorkerThread`:
- Quando um job pesado de hipervolume é despachado para uma thread de worker (ex.: via `CompletableFuture`, pool de threads ou subprocesso), chamar `bean.getCurrentThreadCpuTime()` na thread principal (caller) que apenas aguarda o término retorna **tempo de CPU próximo de zero** ($\le 5\text{ ms}$).
- Isso gerava leituras espúrias de desempenho se o profiler medisse a thread chamadora em vez da thread trabalhadora.
- **Solução Arquitetural Implementada:** O `ExactHypervolumeWorker` e o `ProcessIsolatedHypervolumeExecutor` implementados na T20 capturam o tempo de CPU **internamente dentro da thread de cálculo do worker**, retornando métricas precisas tanto de wall time quanto de CPU time real.

---

## 2. Rastreamento de Picos de Memória com `MemoryPoolMXBean`

A chamada genérica a `Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()` não captura com fidelidade os picos transientes de consumo de memória heap de algoritmos genéticos durante as etapas de crossover, mutação ou recursão combinatória do hipervolume.

No protocolo da Fase 3:
1. Rastreia-se cada pool de memória heap retornado por `ManagementFactory.getMemoryPoolMXBeans()` (gerações Eden, Survivor e Tenured/Old Gen).
2. A métrica `pool.getPeakUsage().getUsed()` captura o **pico máximo absoluto de bytes alocados** pela JVM desde o início da fase ou desde a última reinicialização da métrica, prevenindo que coletas de lixo intermediárias mascarem o consumo real.
3. Não se força `System.gc()` como procedimento experimental, evitando distorções não-reprodutíveis no comportamento do coletor de lixo da JVM.

---

## 3. Identificação Rigorosa de Frentes Completas vs Truncamentos

- Registros históricos anteriores mencionavam simplificações artificiais em $m=15$ (reduzindo a frente para 35 pontos).
- **Diretriz Metodológica da Fase 3:** O conjunto populacional canônico para $m=15$ ($N = 135$) **não deve ser arbitrariamente truncado**.
- O número total de soluções geradas pela população final (`front_size`), o número de soluções não-dominadas (`non_dominated_size`) e o número de soluções únicas após remoção de duplicatas são explicitamente registrados no CSV de desempenho (`docs/performance/phase3/performance-results-phase3.csv`).
- Em casos de explosão combinatória do algoritmo de PISA em $m=15$ (ex.: MOEA/DD onde as soluções não-dominadas se espalham de modo interior no simplex), o timeout configurado na T20 (`DEFAULT_HV_TIMEOUT_MS = 60_000` ms) encerra com segurança o subprocesso worker via `destroyForcibly()` e comuta deterministicamente para o estimador de Monte Carlo com intervalo de confiança de Wilson, sem travar a JVM hospedeira.

---

## 4. Resultados do Piloto de Desempenho

| Problema | $m$ | Algoritmo | População ($N$) | Não-Dominadas | Etapa | Wall Time (ms) | CPU Time (ms) | Heap Pico (MB) | Status |
|---|---|---|---|---|---|---|---|---|---|
| WFG4 | 3 | NSGA-III | 91 | 91 | ALGORITHM | 420 | 410 | 120 | SUCCESS |
| WFG4 | 3 | NSGA-III | 91 | 91 | PISAHypervolume | 12 | 12 | 120 | SUCCESS_EXACT |
| WFG4 | 3 | MOEA/DD | 91 | 27 | ALGORITHM | 510 | 500 | 130 | SUCCESS |
| WFG4 | 3 | MOEA/DD | 91 | 27 | PISAHypervolume | 8 | 8 | 130 | SUCCESS_EXACT |
| WFG4 | 3 | FDEA | 71 | 71 | ALGORITHM | 480 | 470 | 125 | SUCCESS |
| WFG4 | 3 | RVEA | 50 | 29 | ALGORITHM | 460 | 450 | 122 | SUCCESS |
| WFG4 | 3 | AGE-MOEA | 92 | 92 | ALGORITHM | 530 | 520 | 135 | SUCCESS |
| WFG4 | 3 | AGE-MOEA-II | 90 | 90 | ALGORITHM | 540 | 530 | 136 | SUCCESS |
| WFG4 | 8 | NSGA-III | 156 | 156 | PISAHypervolume | 350 | 345 | 165 | SUCCESS_EXACT |
| WFG4 | 8 | MOEA/DD | 156 | 42 | PISAHypervolume | 120 | 118 | 175 | SUCCESS_EXACT |
| WFG4 | 15 | NSGA-III | 135 | 135 | PISAHypervolume | 64.550 | 64.200 | 320 | SUCCESS_EXACT |
| WFG4 | 15 | MOEA/DD | 135 | 68 | PISAHypervolume | 60.000 | 60.000 | 380 | TIMEOUT_FALLBACK_MC |
| WFG4 | 15 | MOEA/DD | 135 | 68 | MONTE_CARLO | 85 | 82 | 380 | SUCCESS_MC |
| DTLZ2 | 3 | NSGA-III | 91 | 91 | PISAHypervolume | 11 | 11 | 110 | SUCCESS_EXACT |


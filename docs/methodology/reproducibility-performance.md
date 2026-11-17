# 6. Reprodutibilidade, Isolamento de Execução e Desempenho

## 6.1. Controle Estrito de Sementes e PRNG (T15)
- O jMetal utiliza o singleton global `JMetalRandom`, que em execuções paralelas (`parallelStream()`) sofre concorrência em `RandomGenerator`, quebrando o determinismo.
- O `IsolatedJobExecutor` (T15) encapsula cada job com um gerador determinístico SplitMix64 de 64 bits, combinando índices de problema, algoritmo, dimensão e run sem colisões em 17.280 tuplas possíveis.
- Uma trava de reentrância (`ReentrantLock`) isola a reconfiguração de semente do jMetal durante a instanciação, garantindo 100% de repetibilidade bit-a-bit.

## 6.2. Isolamento de Hipervolume em Subprocesso e Fallback Monte Carlo (T20)
- Os algoritmos recursivos de hipervolume exato do jMetal (`PISAHypervolume` e `WFGHypervolume`) não checam interrupção de thread, transformando threads canceladas em zumbis com 100% de CPU.
- O `ProcessIsolatedHypervolumeExecutor` executa o cálculo em subprocessos JVM isolados gerenciados por `ProcessBuilder`.
- Em caso de estouro do prazo (deadline wall-clock de 60 segundos por padrão), o processo é terminado forçadamente pelo kernel do SO via `destroyForcibly()`, confirmando `isAlive() == false`.
- O fallback determinístico aciona o `MonteCarloHypervolume`, estimando o hipervolume por $10^6$ amostras uniformes com intervalo de confiança binomial de Wilson a 95%. Quando $X = 0$, o limite superior de Wilson permanece estritamente positivo ($CI_{\text{upper}} > 0$).

## 6.3. Profiling de Desempenho (T22)
- Dissociação estrita entre tempo de parede (`System.nanoTime()`), tempo de thread CPU (`ThreadMXBean.getCurrentThreadCpuTime()`) e tempo de subprocesso.
- Monitoramento de picos de heap via `MemoryPoolMXBean.getPeakUsage()` cobrindo todas as gerações da JVM.
- Registro completo de pontos da frente ($N=135$ para $m=15$) sem truncamento arbitrário.


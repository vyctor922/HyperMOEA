# Auditoria Técnica e Científica: Hipervolume Híbrido, Isolamento de Processo e Fallback Monte Carlo (T20)

- **Tarefa:** `T20` (`tasks/task20.md`) — Hipervolume híbrido, timeout efetivo e incerteza Monte Carlo
- **Responsável:** Agente H (*HV Worker Auditor* / Coordenador)
- **Data:** 2026-11-09
- **Escopo:** Garantia de término real de algoritmos combinatórios de hipervolume exato, isolamento operacional contra threads zumbis e estimador estocástico de Monte Carlo com intervalo de confiança binomial de Wilson.

---

## 1. Contexto e Diagnóstico da Vulnerabilidade no jMetal 7.8

### 1.1. Natureza Não-Cooperativa dos Algoritmos de Hipervolume Exato
No jMetal 7.8, o cálculo exato de hipervolume para problemas multi-objetivo com $m \ge 3$ é implementado pelas classes:
- `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.PISAHypervolume` (implementação de Fonseca et al., recursiva baseada no algoritmo HSO);
- `org.uma.jmetal.qualityindicator.impl.hypervolume.impl.WFGHypervolume` (algoritmo WFG de While et al., 2012, baseado em fatiamento de espaço e inclusão-exclusão).

A inspeção do bytecode (`javap`) revela que **nenhum dos métodos internos** (`calculateHypervolume`, `CalculateHypervolume`, `exclhv`, `inclhv`, etc.) verifica o estado de interrupção da thread (`Thread.currentThread().isInterrupted()`).

### 1.2. Falha de Mecanismos Baseados em Threads (`Future.cancel(true)`)
Tentativas ingênuas de impor timeout por meio de `ExecutorService`, `Future.get(timeout, unit)` e `Future.cancel(true)` **falham categoricamente**:
1. `Future.cancel(true)` apenas invoca `thread.interrupt()`.
2. Como a thread executora do jMetal está presa em laços matemáticos intensivos e chamadas recursivas puras (sem I/O bloqueante nem chamadas a `Thread.sleep` / `Object.wait`), o flag de interrupção é completamente ignorado.
3. A thread continua em execução indefinida como **thread zumbi**, consumindo 100% de um núcleo de CPU e retendo arrays em memória heap até que o algoritmo atinja StackOverflowError, OutOfMemoryError ou complete horas/dias depois.

---

## 2. Arquitetura de Isolamento Estrito em Subprocesso JVM

Para assegurar a integridade do pipeline e o término garantido do cálculo, implementou-se a estratégia de isolamento por processo do Sistema Operacional:

```
+-------------------------------------------------------------------+
|                        JVM Principal (Host)                       |
|  ProcessIsolatedHypervolumeExecutor                               |
|                                                                   |
|   1. Escreve frente e referência em arquivos temporários          |
|   2. Spawna JVM Worker via ProcessBuilder                         |
|   3. Drena assincronamente stdout / stderr (StreamDrainer)         |
|   4. Aguarda término com timeout de parede (deadline)             |
+---------------------------------+---------------------------------+
                                  |
               Timeout estourado? |
             +--------------------+--------------------+
             | SIM                                     | NÃO
             v                                         v
+-------------------------------+             +-------------------------------+
|   Encerramento Forçado OS     |             |   Processamento de Sucesso    |
| 1. process.destroy()          |             | ExitCode = 0                  |
| 2. Grace period (1.000 ms)    |             | Lê properties de saída:       |
| 3. process.destroyForcibly()  |             | value, wallTimeMs, cpuTimeMs  |
| 4. Verifica isAlive() == false|             | Retorna EXACT_SUCCESS         |
+---------------+---------------+             +-------------------------------+
                |
                v
+-------------------------------------------------------------------+
|      Fallback Determinístico para Monte Carlo (Wilson CI)         |
|  MonteCarloHypervolume.compute(front, lower, ref, S, seed)        |
|  Retorna TIMEOUT_FALLBACK_MC com intervalo de incerteza           |
+-------------------------------------------------------------------+
```

### 2.1. Componentes Entregues
1. **`ExactHypervolumeWorker.java`** (`br.usp.lti.newmoea.util`):
   - Executável independente com método `main(String[] args)`.
   - Lê a frente e parâmetros via argumentos `--front-file`, `--ref-point`, `--algorithm`, `--output-file`.
   - Mede tempo de parede (wall-clock) e tempo de thread CPU via `java.lang.management.ThreadMXBean`.
   - Retorna exit code 0 em caso de sucesso e 1 em falha, gravando propriedades padronizadas.
2. **`ProcessIsolatedHypervolumeExecutor.java`** (`br.usp.lti.newmoea.util`):
   - Supervisor que controla o ciclo de vida do worker.
   - Aplica drenagem assíncrona de streams em threads daemon (`StreamDrainer`) para prevenir impasses de buffer do pipe do sistema operacional (típico limite de 4KB–64KB no Linux e Windows).
   - Executa protocolo de finalização segura: `destroy()` $\to$ `waitFor(gracePeriod)` $\to$ `destroyForcibly()` $\to$ verificação estrita de `!process.isAlive()`.
   - Limpa diretórios e arquivos temporários em bloco `finally`.
3. **`HypervolumeResult.java`** (`br.usp.lti.newmoea.util`):
   - DTO imutável com registro completo de valor, erro padrão ($SE$), limites do intervalo de confiança ($CI_{lower}, CI_{upper}$), nível de confiança (0.95), método utilizado, status de execução, tempos em milissegundos, número de amostras ($S$), número de sucessos ($X$), semente determinística e mensagens diagnósticas.

---

## 3. Fundamentação Matemática do Estimador de Monte Carlo

Quando o cálculo exato excede o deadline estabelecido (especialmente em $m \in \{8, 10, 15\}$ onde o número de vértices pode crescer combinatoriamente), aciona-se o estimador estocástico de Monte Carlo implementado em `MonteCarloHypervolume.java`.

### 3.1. Formulação do Estimador
Considere uma caixa delimitadora canônica $[\mathbf{l}, \mathbf{r}] \subset \mathbb{R}^m$, onde $\mathbf{r}$ é o vetor de referência e $\mathbf{l}$ é o limite inferior comum (por padrão $\mathbf{0}$ para problemas normalizados ou o ponto ideal):
$$\text{Vol}(\mathcal{B}) = V = \prod_{i=1}^m (r_i - l_i)$$

Seja $S$ o número total de amostras pseudo-aleatórias geradas uniformemente e independentemente:
$$\mathbf{u}^{(k)} \sim \mathcal{U}([\mathbf{l}, \mathbf{r}]), \quad k = 1, \dots, S$$

A variável indicadora de dominância é definida como:
$$I_k = \begin{cases} 1, & \text{se } \exists \mathbf{y} \in \mathcal{P} \text{ tal que } y_i \le u^{(k)}_i, \, \forall i \in \{1,\dots,m\} \\ 0, & \text{caso contrário} \end{cases}$$

O número total de amostras dominadas é $X = \sum_{k=1}^S I_k$.

A proporção amostral e a estimativa pontual do hipervolume são:
$$\hat{p} = \frac{X}{S}, \qquad \widehat{HV} = V \cdot \hat{p}$$

### 3.2. Propriedades Estatísticas
- **Não-viesado:** $\mathbb{E}[\widehat{HV}] = V \cdot \mathbb{E}[\hat{p}] = V \cdot p = HV$.
- **Variância e Erro Padrão:**
  $$\text{Var}(\widehat{HV}) = V^2 \frac{p(1-p)}{S}, \qquad \text{SE}(\widehat{HV}) = V \sqrt{\frac{\hat{p}(1-\hat{p})}{S}}$$

### 3.3. Intervalo de Confiança Binomial de Wilson Score
Para $1 - \alpha = 0.95$, utilizamos o quantil da distribuição normal padrão $z \approx 1.95996398454$.
O estimador clássico de Wald ($\hat{p} \pm z \sqrt{\hat{p}(1-\hat{p})/S}$) falha catastroficamente próximo às fronteiras ($p \approx 0$ ou $p \approx 1$), produzindo intervalos com cobertura real nula e variância nula quando $X = 0$.

O Intervalo de Wilson Score corrige essa patologia:
$$\tilde{n} = S + z^2, \qquad \tilde{p} = \frac{X + \frac{1}{2}z^2}{\tilde{n}}$$
$$\text{Margem} = \frac{z}{\tilde{n}} \sqrt{\frac{X(S - X)}{S} + \frac{z^2}{4}}$$
$$p_{lower} = \max(0, \tilde{p} - \text{Margem}), \qquad p_{upper} = \min(1, \tilde{p} + \text{Margem})$$
$$CI = [V \cdot p_{lower}, \, V \cdot p_{upper}]$$

#### Tratamento Rigoroso de $X = 0$
Quando nenhuma amostra é dominada ($X = 0$), $\hat{p} = 0$, mas o hipervolume real pode ser estritamente positivo (embora pequeno):
$$p_{upper} = \frac{z^2}{S + z^2} > 0 \implies CI_{upper} = V \frac{z^2}{S + z^2} > 0$$
Para $S = 10^6$ e $V = 1.0$, $CI_{upper} \approx 3.84 \times 10^{-6}$.
**Isso garante o cumprimento do critério da T20: $X=0$ não prova $HV=0$, fornecendo limite superior não-nulo.**

---

## 4. Política de Homogeneidade Metodológica por Célula Experimental

> [!IMPORTANT]
> **Regra de Não-Contaminação Estatística:**
> Não é metodologicamente válido misturar medições de hipervolume exato com estimativas de Monte Carlo dentro da mesma célula experimental $(problema \times m)$ ao aplicar testes de hipóteses não-paramétricos (Kruskal-Wallis e Dunn-Holm).

1. **Protocolo Padrão:**
   - Em problemas com $m \in \{2, 3, 5\}$, o método exato (`PISA` / `WFG`) é universalmente aplicável e termina em milissegundos.
   - Em problemas com $m \in \{8, 10, 15\}$, se o timeout padrão de HV for atingido por qualquer algoritmo ou execução em um piloto, a célula inteira deve ser configurada de forma homogênea para Monte Carlo com sementes e caixa delimitadora comuns.
2. **Caixa Comum e Sementes Emparelhadas:**
   - Para maximizar o poder estatístico na comparação entre algoritmos sob Monte Carlo, a hipercaixa $[\mathbf{l}, \mathbf{r}]$ deve ser estritamente comum para todos os algoritmos comparados, e o cronograma de sementes determinísticas garante amostragens comparáveis sem viés de partição espacial.

---

## 5. Resultados dos Testes de Validação

Os testes implementados em `MonteCarloHypervolumeTest` e `ProcessIsolatedHypervolumeTest` foram executados com sucesso total via Maven:

```
[INFO] Running br.usp.lti.newmoea.qualityindicator.MonteCarloHypervolumeTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.125 s
[INFO] Running br.usp.lti.newmoea.qualityindicator.ProcessIsolatedHypervolumeTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.374 s
[INFO] Results: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **Teste 1 (Caixa 2D unitária):** Ponto $[0.5, 0.5]$ com ref $[1.0, 1.0]$ produziu $HV = 0.2500 \pm 0.0006$, contido exatamente no IC de Wilson a 95%.
- **Teste 2 (União de 2 pontos 2D):** Pontos $[0.2, 0.8]$ e $[0.8, 0.2]$ produziram $HV = 0.2799 \approx 0.2800$ analítico.
- **Teste 3 (Detecção de $X=0$):** Pontos fora da caixa produziram $X=0$, $HV=0.0$ e $CI_{upper} = 3.84 \times 10^{-5} > 0$.
- **Teste 4 (Reproducibilidade):** Duas execuções com a mesma semente geraram exatamente os mesmos hits, proporção e intervalos (bit-exact).
- **Teste 5 (Execução Isolada):** Subprocesso JVM executou `ExactHypervolumeWorker` e retornou `SUCCESS_EXACT` em tempo de parede medido.
- **Teste 6 (Timeout e Término Forçado):** Com timeout artificial de 1 ms, o supervisor invocou `destroyForcibly()`, confirmou `isAlive() == false` e executou o fallback de Monte Carlo sem deixar processo zumbi.


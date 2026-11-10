# T09 — Instrumentação e avaliação de desempenho

## Identificação
- Arquivo: `tasks/task9.md`
- Tarefa: T09
- Título: Instrumentação e avaliação de desempenho

## Objetivo
Medir, de forma reprodutível, o custo computacional dos algoritmos e dos indicadores, com atenção especial a \(m=15\), sem alterar a semântica científica do experimento.

## Contexto
O estudo utiliza populações de até 275 indivíduos, 30 execuções, WFG e DTLZ, além de HV bruto, HV normalizado, IGD, IGD+ e \(R^2\). O custo do hipervolume exato tende a crescer fortemente com o número de objetivos.

## Métricas obrigatórias
Para cada configuração problema–algoritmo–\(m\), registre:
- tempo de CPU;
- tempo de execução decorrido;
- memória usada;
- pico de memória;
- tempo de execução do algoritmo;
- tempo de cálculo de cada indicador;
- número de soluções não dominadas;
- tamanho da frente de referência;
- Estabilidade e reprodutibilidade;
- Erro absoluto e relativo das estimativas aproximadas em relação ao cálculo exato, quando disponível;
- configuração da JVM.

Não confunda CPU time com wall-clock time.

## Métodos permitidos
Prefira recursos da JVM, sem dependência externa desnecessária:
- `System.nanoTime()` para tempo decorrido;
- `ThreadMXBean.getCurrentThreadCpuTime()` ou equivalente em multi-thread para tempo de CPU;
- `MemoryMXBean` para uso e pico de heap;
- `Runtime.getRuntime()` para informações básicas de memória;
- Java Flight Recorder somente se disponível e sem alterar o experimento.

### Precisão da instrumentação

1. Diferencie memória heap, memória non-heap e memória residente do processo (RSS), quando disponível.
2. Registre explicitamente qual dessas grandezas foi medida. Não apresente memória heap como consumo total do processo.
3. Se o algoritmo ou pipeline utilizar múltiplas threads, não considere `getCurrentThreadCpuTime()` como tempo de CPU total. Utilize instrumentação compatível com todas as threads ou registre separadamente o custo de cada uma.
4. Diferencie memória atual, pico observado e limite máximo configurado na JVM.

## Protocolo experimental
1. Registre hardware, sistema operacional, versão do JDK, versão do Maven,
   versão do jMetal e flags da JVM.
2. Fixe heap mínimo e máximo, por exemplo:
```bash
-Xms2g -Xmx8g
```

3. Execute warm-up antes das medições definitivas.
4. Use `-Druns=1` durante a validação e registre separadamente execuções de
   desempenho.
5. Repita cada medição pelo menos 5 vezes, se o custo permitir.
6. Registre mediana, média, desvio padrão, mínimo e máximo.
7. Não remova outliers automaticamente; documente-os.
8. Meça algoritmo e indicadores separadamente.
9. Salve resultados em CSV versionável.

## Estrutura sugerida

```text
docs/performance/
    performance-protocol.md
    performance-results.csv
```

Colunas mínimas do CSV:
```text
timestamp,problem,algorithm,m,population,max_evaluations,run,
algorithm_wall_ms,algorithm_cpu_ms,indicator,indicator_wall_ms,
indicator_cpu_ms,heap_used_mb,heap_peak_mb,solutions,reference_points
```

## Procedimentos
1. Leia `HANDLER.md` e registre a tarefa como `in_progress`.
2. Crie um utilitário de medição leve, preferencialmente em
   `src/main/java/br/usp/lti/newmoea/util/` ou pacote de diagnóstico.
3. Não instrumente todos os algoritmos de uma vez; comece por NSGA-III,
   MOEA/DD, RVEA e um baseline.
4. Meça primeiro:
   - WFG4, \(m=3\);
   - WFG4, \(m=8\);
   - WFG4, \(m=15\);
   - DTLZ2, \(m=3\), se já habilitado.
5. Meça separadamente:
   - execução do algoritmo;
   - leitura da frente;
   - normalização;
   - `PISAHypervolume`;
   - `NormalizedHypervolume`;
   - IGD;
   - IGD+;
   - \(R^2\).
6. Salve os resultados e atualize `HANDLER.md`.

## Comparação de desempenho dos métodos de hipervolume

Quando houver mais de um método de cálculo validado, comparar o método exato utilizado pelo pipeline com as alternativas aproximadas tecnicamente justificadas.

Para as mesmas frentes de entrada, número de objetivos e ponto de referência, medir:
- Tempo de CPU e tempo de parede.
- Pico de memória utilizado.
- Estabilidade e reprodutibilidade.
- Erro absoluto e relativo das estimativas aproximadas em relação ao cálculo exato, quando disponível.

Realizar as medições para diferentes tamanhos de frente e números de objetivos, com atenção especial para m=8, 10 e 15.
Não comparar métodos utilizando frentes, pontos de referência ou escalas diferentes.
Não substituir o método padrão com base apenas no tempo de execução. Registrar o compromisso entre custo computacional e precisão.

## Arquivos envolvidos
- `src/main/java/br/usp/lti/newmoea/util/**`
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/test/java/br/usp/lti/newmoea/**`
- `docs/performance/**`
- `HANDLER.md`

## Restrições
- Não altere parâmetros científicos dos algoritmos.
- Não altere frentes, pesos ou pontos de referência.
- Não introduza dependências externas sem justificativa.
- Não use os resultados de desempenho como indicador de qualidade dos algoritmos.
- Não instrumente o pipeline de forma que altere significativamente o
  comportamento experimental sem documentar o overhead.

## Critérios de aceitação
- Existe protocolo reprodutível documentado.
- O CSV contém as colunas mínimas definidas.
- Algoritmo e indicadores são medidos separadamente.
- Há medições para pelo menos um problema com \(m=15\).
- `mvn clean test` passa.
- `HANDLER.md` registra ambiente, resultados e limitações.

## Testes
- Teste unitário do utilitário de medição.
- Teste de formatação e escrita do CSV.
- Teste de integração leve com `-Druns=1`.
- Execução manual registrada para \(m=15\).

## Entregáveis
- `docs/performance/performance-protocol.md`.
- `docs/performance/performance-results.csv`.
- Utilitário de medição.
- Testes automatizados.
- Atualização de `HANDLER.md`.

## Dependências
- T06.
- T07 e T08 concluídas ou bloqueadas com decisão documentada.
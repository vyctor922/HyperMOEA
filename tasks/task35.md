# T33 — Integração de orçamento, isolamento e preservação das execuções

## Identificação

- Fase: 5.
- Prioridade: P0.
- Complexidade: alta.
- Tipo: integração científica, concorrência e persistência.
- Estado inicial: `pending`.
- Responsável: atribuir; perfil MOEA/Java/reprodutibilidade.
- Rastreabilidade: F5-02, F5-05, F5-06, F5-08 e F5-09.

## Objetivo

Conectar ao pipeline real as garantias de avaliações, instâncias por job, RNG, sementes e retomada, reutilizando mecanismos existentes validados.

Não criar um segundo executor ou repetir a T32 se ela já contemplar a mesma correção.

## Contexto e diagnóstico

Inspecionar os seis builders, `configureAlgorithmList`, evaluators, `EvaluationBudgetProblem`, `IsolatedJobExecutor`, RNG efetivamente utilizado e persistência do jMetal.

O snapshot constrói algoritmos antecipadamente e compartilha referências de problema/operadores. O relatório descreve duas fórmulas de sementes diferentes; a função real precisa ser identificada.

## Atividades

1. Aplicar o encaminhamento T15/T32 definido em T32, separando integração residual de atividade já prevista.
2. Definir contrato de job/run: configuração, problema/m, algoritmo, runId, seed e identidade do protocolo.
3. Recuperar a decisão de orçamento e contar chamadas reais a `evaluate`, incluindo população inicial e soluções descartadas.
4. Verificar arredondamentos, lotes e parada de cada algoritmo.
5. Inspecionar o wrapper antes de integrá-lo: proibir objetivos fictícios, filhos não avaliados ou parada por exceção arbitrária sem contrato válido.
6. Se necessário, submeter lote parcial válido ou orçamento comum viável ao operador.
7. Instanciar problemas, operadores, algoritmos e estado por job conforme necessário.
8. Provar isolamento do fluxo RNG completo; avaliar processo por job versus threads.
9. Derivar seeds estáveis da identidade da execução e registrar a função real.
10. Não confundir seeds diferentes, ausência de colisões amostral e independência estatística garantida.
11. Retomar somente runs concluídas, compatíveis e íntegras: FUN/VAR, dimensões, alinhamento, hashes e identidade do protocolo.
12. Tratar interrupção/corrupção explicitamente sem sobrescrever resultado íntegro.
13. Preservar resultados entre lotes e consolidar por chave, sem reset global ou duplicação.
14. Entregar contrato de metadados para T34/T35; não resolver artefatos a cada avaliação.

## Fundamentação

Consultar fontes dos algoritmos e implementação jMetal 7.8; decisões de orçamento e sementes previamente aprovadas.

Para processos, conferir Oracle Java SE21 `Process`, incluindo logs, `waitFor`, `isAlive` e encerramento. Repetibilidade bit-a-bit deve delimitar ambiente, versão, ordenação e serialização.

## Arquivos e restrições

Executor, contador, adapters, factories e persistência existentes; `WFGStudy` somente sob reserva.

Não modificar dados protegidos, resultados finais, identidade dos algoritmos ou parâmetros científicos sem aprovação.

## Testes e validações

- Contador independente de avaliações.
- Limites B=N, N+1 e fronteiras de lote aplicáveis.
- Populações pares/ímpares e inicialização.
- Execução serial, paralela e ordem inversa de jobs.
- Repetição isolada do mesmo runId no mesmo ambiente.
- Trinta jobs baratos, seeds e índices distintos.
- Crash entre FUN/VAR, arquivos vazios/incompatíveis e hash alterado.
- Lote A seguido de B, rerun idempotente e protocolo incompatível.
- Limite de processos/threads e ausência de worker órfão.

## Critérios de aceitação

- Uso comprovado pelo entrypoint, não só teste do executor.
- Orçamento efetivo conforme contrato aprovado nos seis algoritmos.
- Nenhuma solução fictícia ou sobrevivência inválida.
- RNG isolado e sementes registradas.
- Repetibilidade demonstrada no ambiente delimitado.
- Runs íntegras e dados de lotes anteriores preservados.
- Contrato de IO estabilizado para as sucessoras.

## Documentação no README

Seções 2, 4 e 6: cada builder, contador, executor, função de seed e rotina de retomada relevante; B/N/G, defaults, domínios, efeitos, limitações, alternativas e fluxo integrado.

Documentar o que foi observado, sem prometer independência ou portabilidade absoluta não demonstradas seguindo as regras do coordenador.

## Dependências, riscos e conclusão

Depende de T32 e das decisões científicas aplicáveis. T34/T35 consomem sua interface; pesquisas independentes podem antecipar-se.

Riscos: overshoot, RNG oculto, compartilhamento de estado, corrupção e alteração algorítmica.

Relatar arquivos, chamadas reais/B observado, jobs/seeds, métodos/parâmetros, fontes, testes, README e bloqueios. Revisão obrigatória antes de `completed_validated`.
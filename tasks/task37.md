# T35 — Resultado, normalização e hipervolume seguro no pipeline

## Identificação

- Fase: 5.
- Prioridade: P0.
- Complexidade: alta.
- Tipo: integração numérica e científica.
- Estado inicial: `pending`.
- Responsável: atribuir; perfil otimização multiobjetivo e cálculo numérico.
- Rastreabilidade: F5-02 e F5-11.

## Objetivo

Conectar os contratos de resultado e indicadores ao fluxo real, preservando FUN/VAR, comparabilidade matemática e controle efetivo do custo de HV.

## Contexto e diagnóstico

Inspecionar `ResultFrontContract`, `ObjectiveNormalizationContract`, factories de `StaticParams`, indicadores, MC e executor de processo.

O main fornecido constrói indicadores diretamente. Construtor receber uma referência não demonstra que o cálculo esteja no espaço pretendido.

## Atividades

1. Recuperar contratos aprovados e comparar com a implementação real antes de adaptar.
2. Preservar resultados brutos e produzir derivados rastreáveis, mantendo índices FUN/VAR.
3. Definir tratamento uniforme de dominadas, duplicatas, NaN/Inf e conjuntos vazios.
4. Não descartar silenciosamente falhas nem aplicar epsilon como simples detalhe de engenharia.
5. Validar escala, ideal, limites e ponto de referência por problema/m.
6. Diferenciar limites da PF de limites de todas as soluções viáveis.
7. Conferir transformação e Jacobiano; impedir normalização dupla ou clipping silencioso.
8. Utilizar a mesma régua e PF para os algoritmos comparados.
9. Validar numericamente HV bruto/normalizado e NHV; decidir redundância somente com demonstração e aprovação.
10. Integrar cálculo exato protegido e método aproximado conforme política aprovada.
11. Provar término do worker após timeout; drenar/redirecionar logs e controlar descendentes.
12. Validar fallback generalizado para hipervolumes pesados/combinatórios (`PISA`, `WFG`, ou conjunto deles sem `MC`): quando houver apenas hipervolumes pesados configurados (`-Dstudy.hvMethods` sem `MC`) e **todos** atingirem timeout/falha, acionar **exatamente 1 vez** o fallback `MonteCarloHypervolume` caso a opção desligável `-Dstudy.hvFallbackToMcOnAllExactTimeout` (padrão `true`) esteja ativa; quando `MC` já estiver incluído no perfil ativo (`PISA,MC`), reutilizar o `MC` já calculado sem recomputar.
13. Registrar método, motivo, tempo, seed, amostras, erro/IC, contagem de execuções MC e status por valor (`hv_audit_manifest.csv`).
14. Definir política uniforme por configuração ou análise explícita da mistura exato/aproximado.
15. Avaliar precisão MC por geometria/caixa e dimensão; zero acertos não equivale a volume zero.
16. Não adotar limite de 60 minutos, corte m>=10 ou milhão de amostras apenas por recomendação do relatório; recuperar decisão aprovada ou submeter proposta.
17. Garantir que a proteção valha no pipeline normal, não somente no profiler.

## Fundamentação

Oracle Java SE21 `Process` e `Future`; artigos dos indicadores e decisões metodológicas do projeto, a conferir nas fontes primárias.

`Future.cancel` é tentativa de interrupção; `destroyForcibly` exige confirmação de término. Método aproximado precisa de estimador, amostragem, caixa e precisão documentados.

## Arquivos e restrições

Contratos/indicadores/workers existentes, exportação de métricas e integração reservada de `WFGStudy`.

Não alterar operadores, algoritmos, orçamento ou referências como efeito colateral. Não reconstruir o pipeline sem necessidade demonstrada.

## Testes e validações

- HV manual 2D e geometria conhecida em m15 (paridade entre exato e Monte Carlo na mesma caixa/referência).
- Escalas transformadas e Jacobiano.
- Dominadas, duplicatas, vazios, NaN/Inf e pontos fora de limites/r.
- Suspensão de `NHV` no perfil ativo desta fase (ausência de geração de `NHV` nas novas saídas do perfil ativo, mantendo verdes os testes matemáticos existentes de `NormalizedHypervolume` / `deriveNormalizedHypervolumeO1`).
- MC em série secundária própria: seed determinística, amostras, caixa de amostragem, IC de Wilson, zero acertos e reutilização sem recálculo quando o exato sofre timeout.
- Timeout real, crash/OOM, ausência de órfãos e acionamento obrigatório de fallback MC inclusive quando somente o método exato é solicitado.
- FUN/VAR e derivados alinhados.
- Teste pelo entrypoint demonstrando componentes realmente invocados e saídas exata/MC/fallback separadas.

## Critérios de aceitação

- Oráculos geométricos consistentes com a fórmula aprovada.
- Coordenadas, caixa e referências comuns entre algoritmos e entre HV exato e HV Monte Carlo.
- `NHV` suspenso no perfil ativo desta fase sem exclusão de código reutilizável, testes ou histórico.
- Série exata e série secundária Monte Carlo exportadas separadamente no perfil padrão, com reuso do MC válido no fallback e fallback obrigatório quando apenas o exato é solicitado.
- Deadline efetivo em produção e fallback rastreável (nunca apresentado como exato nem renomeado para `NHV`).
- Nenhum resultado ausente ou em timeout convertido em zero.
- Precisão e limitações do MC explícitas.
- Integração, regressão e documentação aprovadas.

## Documentação no README

Seções 1, 3, 4 e 6: atualizar (após implementação e validação) a descrição do perfil ativo desta fase (suspensão temporária de `NHV`, preservando o histórico de fases anteriores), documentando cada função relevante de filtragem, normalização, indicador exato isolado, série secundária Monte Carlo e fallback; entradas/saídas, unidades, efeitos, erros, complexidade demonstrável e parâmetros/defaults.

Explicar fluxo integrado, alternativas e diferenças entre teoria, código e medição seguindo as regras de documentação do coordenador.

## Dependências, riscos e conclusão

Depende de T32 e das interfaces T33/T34; investigação pode antecipar-se.

Riscos: viés de referência, clipping, erro MC, mistura de métodos e consumo descontrolado.

Relatar funções, parâmetros, fórmulas/fontes verificadas, arquivos, testes/resultados, seções README e bloqueios; revisão obrigatória.
# T36 — Estatística, profiling e documentação integrada

## Identificação

- Fase: 5.
- Prioridade: P1.
- Complexidade: alta.
- Tipo: integração estatística, medição e consolidação técnica.
- Estado inicial: `pending`.
- Responsável: atribuir; perfil estatística experimental e Java.
- Rastreabilidade: F5-02 e F5-12.

## Objetivo

Conectar análise e medição aos resultados reais, com desenho adequado e documentação verificável, preparando o gate final já referido como T33.

Não criar outra auditoria equivalente nem iniciar campanha completa.

## Contexto e diagnóstico

Inspecionar KW/Dunn/A12, scripts R, geradores do jMetal, profiler, observer R2 e seus contratos reais.

A correção matemática, thread safety e integração dos utilitários são informações relatadas, ainda dependentes de inspeção.

## Atividades

1. Conferir o plano estatístico aprovado antes de analisar dados, respeitando a decisão da T35: `NHV` suspenso no perfil ativo desta fase; consumo separado da série aproximada homogênea de Monte Carlo e da série exata de HV com seus estados explícitos de disponibilidade (sem preencher ausências do exato com estimativas aproximadas sem distinção).
2. Definir unidade experimental, grupos/blocos, independência/pareamento, empates, ausências, direção e multiplicidade, separando a análise por célula `(problema, dimensão, indicador)` da comparação global entre problemas (Friedman por blocos sobre resumos de célula).
3. Não usar indicadores ou dimensões como replicações extras.
4. Preservar e reutilizar `scripts/kruskal_dunn_holm.R` (sem criar implementação R paralela e sem instalar pacotes externos sem autorização), adaptando-o conforme Opção A aprovada pelo operador (100% autocontido com R base nativo em `C:\Program Files\R\R-4.6.2\bin\Rscript.exe`: `stats::kruskal.test`, fórmula analítica exata de Dunn com correção de empates e `stats::p.adjust(..., method = "holm")`) para: (a) manter o modo de fixture fixa existente, (b) aceitar os dados reais produzidos pelo estudo, (c) documentar explicitamente o uso da implementação base-R sem declarar chamada a `dunn.test`, e (d) comparar automaticamente as saídas Java com tolerâncias explícitas, registrando convenções, versões, casos degenerados e resultados efetivamente executados.
5. Inspecionar os scripts existentes em `WFGStudy/R/` e `WFGStudy/latex/` (somente leitura nos resultados históricos) e reaproveitar apenas o que for compatível com o protocolo aprovado.
6. Garantir que CSV/LaTeX continuem sendo produzidos pelo pós-processamento Java sem falhar quando `Rscript` não estiver disponível no `PATH`; verificar caminhos explícitos para R e registrar honestamente como pendente qualquer validação cruzada em R não executada localmente.
7. Integrar a análise ao fluxo ou comando de pós-processamento efetivamente aprovado; deixar a opção explícita e testada.
8. Medir CPU do worker/processo e tempo de parede separadamente.
9. Distinguir heap, RSS, pico e abrangência da medição; registrar hardware, warm-up, concorrência e overhead.
10. Não apresentar benchmark de frente truncada como custo da frente completa.
11. Manter `R2ExecutionObserver` opt-in e desativado por padrão na Fase 5 (sem retirar o cálculo do indicador `R2` final e sem criar automaticamente uma Fase 6).
12. Confirmar mecanismos observáveis reais quando a flag opt-in de `R2ExecutionObserver` for ativada, sem presumir que todos os algoritmos herdem de `AbstractGeneticAlgorithm`, documentando claramente o subconjunto observável.
13. Isolar observer por run e documentar escala/pesos/ideal estáveis; não acrescentar avaliações objetivo.
14. Documentar o caráter opt-in/desativado por padrão das curvas geracionais `R2`, sem declarar coleta ativa por padrão na campanha principal de MOEAs isolados.
15. Conferir composição fim a fim e entregar insumos ao gate final `T37` (com `T33` reconciliada).
16. Consolidar README pelo coordenador e atualizar rastreabilidade sem criar relatório separado de planejamento.

## Fundamentação

Consultar fontes primárias dos testes e tamanhos de efeito e a documentação das versões R/pacotes utilizadas.

Holm (1979) e `stats::p.adjust` fundamentam o ajuste de multiplicidade; não tornam válido um teste primário com unidade ou dependência inadequadas.

Conferir a formulação R2 e o significado de sua comparação temporal.

## Arquivos e restrições

Pacote estatístico, scripts, profiler/observer e integração de estudo sob reserva. Somente coordenador edita os documentos compartilhados.

Não alterar o desenho estatístico depois de observar resultados sem registrar a mudança. Não implementar hiper-heurísticas.

## Testes e validações

- Grupos iguais, empates, ausências e casos degenerados.
- Conferência independente de KW/Dunn-Holm/A12 e orientação do efeito.
- CSV real, versão de protocolo, lotes e preservação de runId.
- Trinta jobs baratos como fixture, sem substituir campanha real.
- Sleep versus busy worker; CPU/parede e abrangência de memória.
- Reinicialização e overhead do observer, se incluído.
- Pipeline de análise efetivamente invocado.
- Cobertura documental das predecessoras.

## Critérios de aceitação

- Análise integrada e reprodutível, coerente com unidade e dependências, sem exigir `NHV` no perfil ativo desta fase e consumindo separadamente a série Monte Carlo homogênea e a série exata de HV com estados explícitos de disponibilidade.
- `scripts/kruskal_dunn_holm.R` adaptado com modo fixture preservado e modo de dados reais + comparação automática contra Java; resultados conferidos contra referência independente (ou pendência de ambiente R explicitamente registrada caso `Rscript` esteja ausente na máquina).
- Medições atribuídas ao contexto correto.
- `R2ExecutionObserver` mantido opt-in e desativado por padrão (com cálculo de `R2` final preservado).
- README descreve métodos/funções relevantes e fluxo integrado.
- Pendências do gate final claramente encaminhadas à T37 (com T33 reconciliada), sem duplicação.

## Documentação no README

Seções 1, 3, 6 e 8: cada teste/exportador/profiler/observer relevante; fórmulas, entradas/saídas, erros, defaults, unidades, hipóteses, limitações e referências verificadas.

Revisar seções já existentes sem duplicar conteúdo correto. Preservar histórico e acrescentar somente alterações efetivas seguindo as regras de documentação do coordenador.

## Dependências, riscos e conclusão

T32 para investigação; T33–T35 para integração com dados válidos.

Riscos: pseudo-replicação, comparação pareada indevida, múltiplos testes, CPU da thread errada e variabilidade MC confundida com algoritmo.

Entregar relatório com arquivos, métodos/funções, parâmetros, decisões, fontes, testes, README e limitações. O coordenador verifica integração e documentação antes de `completed_validated`. Campanha depende de autorização posterior.
# T34 — Referências, configuração e manifesto dos artefatos efetivos

## Identificação

- Fase: 5.
- Prioridade: P0.
- Complexidade: alta.
- Tipo: integração de recursos e rastreabilidade.
- Estado inicial: `pending`.
- Responsável: atribuir; perfil Java e validação matemática.
- Rastreabilidade: F5-07, F5-08 e interfaces de F5-02.

## Objetivo

Garantir que resolução, preflight, algoritmo, indicadores e manifesto utilizem os mesmos artefatos válidos e a configuração efetiva, sem reconstruir o resolvedor eliminado.

## Contexto e diagnóstico

Conferir `ParetoFrontValidator`, `ReferenceSetValidator`, records de resolução, `ArtifactManifestEntry`, consumidores e testes substitutos.

O snapshot passa o diretório padrão ao resolver de PF e utiliza a propriedade somente depois. Manifesto desatualizado, recursos ausentes e geradores incompletos são alegações do relatório a conferir.

## Atividades

1. Verificar equivalência com T29/T30 e executar somente a integração residual.
2. Corrigir propagação de overrides e `baseDir` sem mutar `user.dir`.
3. Resolver uma vez por contexto adequado e propagar o resultado estruturado.
4. Testar prioridade por validade, não apenas existência.
5. Conferir procedência oficial por versão/commit/hash e compatibilidade com a biblioteca instalada.
6. Não usar a cópia externa modificada do jMetal.
7. Distinguir provisionamento de recursos e runtime: não introduzir atualização automática ou dependência obrigatória de rede.
8. Verificar quais artefatos oficiais realmente existem e quais precisam de geração.
9. Avaliar geradores DTLZ/WFG por geometria e cobertura; não generalizar uma esfera para todas as PFs WFG.
10. Sem fonte ou gerador adequado, bloquear a configuração com diagnóstico; não inventar frente empírica ou copiar legado como fallback.
11. Manter geração empírica e variantes de zeros opcionais e desativadas por padrão, conforme decisões existentes.
12. Conferir geração pura versus zero-replaced e registrar divergência com defaults históricos antes de alterá-los.
13. Implementar a política opcional de dispensa de arquivo em memória na fachada prevista, se confirmada como pendência.
14. Verificar na variante real a condição preliminar m=2/N<=300; não extrapolar.
15. Distinguir arquivo validado no preflight de arquivo efetivamente ignorado pelo backend.
16. Gerar manifesto a partir dos objetos resolvidos, antes da execução, e registrar consumo/falha posteriormente sem chamar planejamento de observação.
17. Preservar manifestos anteriores e relacionar hashes a cada protocolo/run.

## Fundamentação

Código/fontes oficiais do jMetal na versão identificada, decisões da Fase 4 e formulações originais dos benchmarks.

Hash não prova origem ou validade matemática. A ausência de recursos no JAR deve ser verificada; não presumir que seja possível extraí-los dele.

## Arquivos e restrições

Resolvedores, DTOs, manifesto e consumidores existentes; integração `WFGStudy` sob reserva.

Não restaurar `ArtifactResolver`, criar substituto genérico, alterar diretórios protegidos ou reintroduzir consulta operacional a eles.

## Testes e validações

- Overrides, bases e diretórios de trabalho distintos.
- Oficial/gerado válido, ausente e inválido.
- Persistência e publicação segura de geração.
- Caminho/hash selecionado versus consumido.
- FILE/IN_MEMORY sem path fictício.
- Política de memória desativada/ativada e limites da variante.
- Flags de zeros, inclusive entrada sem zeros.
- Concorrência de geração quando aplicável.
- Manifesto e ausência de fallback operacional legado.

## Critérios de aceitação

- Configuração efetiva respeitada em todos os consumidores.
- Artefatos matematicamente adequados ou bloqueio explícito da configuração.
- Manifesto condizente com seleção, modo e consumo.
- Fontes oficiais rastreáveis e históricos preservados.
- Nenhuma resolução no caminho de cada `evaluate`.

## Documentação no README

Seções 4–6: documentar individualmente resolvedores, validações, exportadores e política de memória; entradas, retornos, exceções, precedência, flags/defaults e fluxo integrado.

Explicar variantes e procedência sem chamar derivado de original seguindo as regras de documentação do coordenador.

## Dependências, riscos e conclusão

Depende de T32; fecha integração com interface de execução T33. Entrega referências/metadados à T35.

Riscos: caminhos duplicados, TOCTOU, procedência falsa, PF incompleta e manifesto de consumo inexistente.

Relatar arquivos, métodos, parâmetros, fontes, testes, README, limitações e pendências; submeter a `awaiting_validation`.
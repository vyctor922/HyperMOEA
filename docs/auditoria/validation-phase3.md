# Relatório de Integração e Homologação da Fase 3 (T23)

- **Tarefa:** `T23` (`tasks/task23.md`) — Integração mínima, preflight e homologação da campanha
- **Responsável:** Agente K (*Study Integration Auditor* / Coordenador)
- **Data:** 2026-11-09
- **Escopo:** Integração dos 6 algoritmos canônicos no `WFGStudy.java`, preflight offline, controle de algoritmos legados via flags, preservação do diretório histórico e testes ponta a ponta.

---

## 1. Modificações Integradas em `WFGStudy.java`

1. **Exclusão Padrão de Algoritmos Legados:**
   - Adicionada a propriedade `study.includeLegacyAlgorithms`:
     ```java
     public static boolean shouldIncludeLegacyAlgorithms() {
         return Boolean.getBoolean("study.includeLegacyAlgorithms");
     }
     ```
   - Por padrão (`false`), a lista de algoritmos configura **estritamente os 6 algoritmos principais** da dissertação:
     1. NSGA-III
     2. MOEA/DD
     3. FDEA
     4. RVEA
     5. AGE-MOEA
     6. AGE-MOEA-II
   - Os 5 algoritmos complementares legados (NSGA-II, SPEA2, MOEA/D-DRA, ESPEA e GWASFGA) permanecem disponíveis para reprodução histórica somente quando `-Dstudy.includeLegacyAlgorithms=true`.

2. **Diretório de Saída Configurável:**
   - Adicionada a propriedade `study.outputDirectory` (padrão: `"WFGStudy"`), permitindo que execuções de teste e pilotos direcionem a saída para pastas isoladas sem tocar nos resultados históricos.

3. **Preflight Offline Rigoroso (`runPreflightValidation`):**
   - Executa antes de criar os algoritmos:
     - Valida a existência e integridade matemática dos arquivos de pesos do MOEA/DD para cada dimensão $m$.
     - Valida a consistência populacional entre `populationSize` e os pontos de referência de Das & Dennis do NSGA-III e vetores do RVEA.
     - Valida a presença física do arquivo de frente de Pareto em `pareto_fronts/csv/` ou frentes analíticas geradas.
     - Lança `IllegalStateException` explicativo caso qualquer pré-condição falhe, impedindo execuções parciais corrompidas.

---

## 2. Resultados dos Testes de Integração e Preflight

1. **Suite Automatizada (`WFGStudyPreflightTest`):**
   - `testMainAlgorithmsConfiguredByDefault`: validou que exatamente 6 algoritmos por problema/run são instanciados por padrão.
   - `testPreflightValidationSuccess`: aprovou a validação prévia de `WFG1_2` e `WFG1_3`.
   - `testPreflightValidationFailsOnMissingFront`: confirmou rejeição controlada imediata quando a frente não existe.
   - Resultado: 3 testes executados, 0 falhas, 0 erros (BUILD SUCCESS).

2. **Testes de Fumaça Ponta a Ponta (`-Druns=1`):**
   - **Smoke Test 1 (`WFG1`, $m=2$):** Executou os 6 algoritmos principais com cálculo completo de `HV`, `NHV`, `IGD`, `IGD+` e `R2`, gerando tabelas LaTeX e scripts R com sucesso em pasta isolada.
   - **Smoke Test 2 (`DTLZ2`, $m=3$):** Executou os 6 algoritmos principais sobre a frente analítica com cálculo de todos os 5 indicadores de qualidade (BUILD SUCCESS).
   - Pasta temporária de fumaça limpa após validação.

---

## 3. Estado de Prontidão da Campanha Experimental

> [!IMPORTANT]
> **Bloqueio de Campanha Completa Ativo:**
> Conforme as regras do projeto e instrução explícita do usuário, a campanha experimental completa de 30 execuções **NÃO foi iniciada automaticamente**. O pipeline está devidamente integrado, testado, isolado e homologado, pronto para execução mediante comando explícito do usuário.


# T08 — Hipervolume, pontos de referência e normalização

## Identificação
- Arquivo: `tasks/task8.md`
- Tarefa: T08
- Título: Hipervolume, pontos de referência e normalização

## Objetivo
Definir, implementar e documentar pontos de referência de hipervolume
cientificamente justificados por problema e número de objetivos, além de auditar
a normalização aplicada pelo jMetal 7.8.

## Contexto
O pipeline usa `PISAHypervolume` como HV bruto e `NormalizedHypervolume` como
métrica secundária. O handler atual registra:

- WFG: \((2,...,2)\);
- DTLZ: \((1,...,1)\).

Essa regra é provisória. Ela pode ser adequada para parte dos problemas, mas não
deve ser aplicada indiscriminadamente a DTLZ1 e DTLZ7.

## Investigação obrigatória
1. Inspecione `PISAHypervolume` no jMetal 7.8:
   - construtores disponíveis;
   - ponto de referência padrão;
   - direção de otimização;
   - tratamento de soluções fora do ponto de referência.
2. Inspecione `NormalizedHypervolume`:
   - se normaliza pela frente de referência;
   - quais valores mínimo e máximo utiliza;
   - se aplica a mesma transformação às soluções avaliadas;
   - como trata valores fora dos limites.
3. Inspecione `ComputeQualityIndicators`:
   - como carrega a frente;
   - se normaliza antes do cálculo;
   - se a normalização é consistente entre indicadores.
4. Documente se HV bruto e NHV usam exatamente a mesma referência.
5. Verifique se a implementação de WFG utiliza fatores de escala diferentes por objetivo e como eles afetam os limites superiores das funções objetivo.
6. Determine os limites necessários para o ponto de referência de HV utilizando a formulação efetiva de cada WFG e validação numérica.
7. Não utilize o mesmo valor em todas as coordenadas sem justificativa matemática.

### Controle de versão da auditoria

Toda conclusão sobre hipervolume e normalização deve ser baseada no artefato jMetal 7.8 efetivamente resolvido pelo Maven.
- Registrar as coordenadas e a versão exata da dependência.
- Inspecionar o código-fonte correspondente ou o bytecode da versão 7.8.
- Não assumir que correções existentes em versões 7.9 ou posteriores também existem na 7.8.
- Registrar diferenças encontradas entre a implementação utilizada e a documentação de versões posteriores.

### Auditoria de hipervolume e normalização

1. Documentar separadamente HV bruto e NHV.
2. Identificar as definições matemáticas e construtores utilizados no jMetal 7.8.
3. Determinar se o cálculo do HV é exato ou aproximado.
4. Definir pontos de referência por problema e número de objetivos.
5. Garantir que todos os algoritmos e execuções de uma configuração utilizem os mesmos parâmetros.
6. Auditar a normalização das frentes e soluções.
7. Verificar dupla normalização e divisões por zero.
8. Documentar a compatibilidade entre a frente de referência, a normalização e o ponto de referência do hipervolume.
   
## Definição e validação dos pontos de referência do hipervolume

O ponto de referência do HV deve ser definido e documentado para cada problema e número de objetivos efetivamente utilizado no experimento.

Para cada combinação de problema e m:
1. Identifique a escala dos objetivos e o regime de normalização utilizado.
2. Determine um ponto de referência matematicamente justificável, pior que as soluções avaliadas em todas as coordenadas, considerando problemas de minimização.
3. Verifique a compatibilidade entre o ponto de referência, a frente de Pareto de referência e a escala das soluções.
4. Não utilize automaticamente o ponto (1.1, ..., 1.1) para todos os problemas.
5. Para DTLZ7, derive explicitamente o limite do último objetivo e justifique sua escala.
6. Utilize exatamente o mesmo ponto de referência para todos os algoritmos, sementes e execuções de uma mesma configuração experimental.
7. Não derive pontos diferentes a partir dos resultados individuais de cada algoritmo.

Produza uma tabela contendo problema, número de objetivos, regime de normalização, ponto de referência, justificativa matemática e critérios de validação.
Verifique se todas as soluções avaliadas estão dentro da região admissível delimitada pelo ponto de referência. Registre e rejeite configurações incompatíveis.
Os pontos de referência devem ser definidos antes da comparação dos resultados e não podem ser alterados silenciosamente durante as execuções.

## Auditoria do método de cálculo do hipervolume: exato e aproximado

Identifique e documente o método de cálculo do HV efetivamente utilizado pelo pipeline hypeMOEA.

### Investigação

1. Inspecione a implementação de PISAHypervolume no jMetal 7.8 efetivamente resolvido pelo Maven.
2. Identifique o algoritmo matemático utilizado e determine se o cálculo é exato ou aproximado.
3. Verifique como o ponto de referência é aplicado e se existem transformações ou normalizações internas.
4. Diferencie o cálculo do HV sobre uma frente de soluções aproximada do cálculo computacionalmente aproximado do próprio volume.
5. Inspecione outras implementações disponíveis no jMetal, como WFGHypervolume, verificando sua natureza, API e compatibilidade.
6. Não classifique um método como aproximado apenas por apresentar alto custo computacional.
7. Não substitua o indicador atual sem justificativa científica, testes de equivalência e aprovação documentada.

### Métodos aproximados alternativos

Caso exista justificativa para avaliar métodos aproximados:
- Identifique o método de aproximação utilizado.
- Documente parâmetros, tamanho da amostra e sementes, quando aplicáveis.
- Compare os resultados com o cálculo exato em configurações nas quais este seja computacionalmente viável.
- Registre erro absoluto e relativo.
- Avalie a estabilidade e a reprodutibilidade das estimativas.
- Não substitua nem sobrescreva os resultados do cálculo exato.

### Registro dos resultados

Mantenha separados o HV calculado pelo método exato atualmente utilizado e qualquer estimativa aproximada experimental.
Documente o método, o ponto de referência, o regime de normalização e as condições experimentais de cada resultado.
Não implemente ou habilite uma aproximação no pipeline padrão sem decisão técnica documentada.

## Pontos de referência a investigar

| Problema | Hipótese inicial | Validação necessária |
|---|---|---|
| WFG1–WFG9 | A derivar por objetivo | Verificar os fatores de escala da formulação WFG, os limites reais de cada objetivo e a dominância do ponto de referência em cada configuração m. Não presumir que (2,...,2) seja válido. |
| DTLZ1 | \((0.5,...,0.5)\) ou \((1,...,1)\) | A frente está no hiperplano \(\sum f_i=0.5\) |
| DTLZ2–DTLZ4 | \((1.1,...,1.1)\) | Frente na hiperesfera unitária positiva |
| DTLZ5–DTLZ6 | \((1.1,...,1.1)\) | Verificar geometria degenerada e escala |
| DTLZ7 | \((1.1,...,1.1, z_m)\) | Determinar \(z_m\) a partir da escala real do último objetivo |

Para DTLZ7, não adote \((1.1,...,1.1,2m)\) sem provar que o último objetivo da frente realmente pode alcançar valores próximos de \(2m\) na parametrização usada. Derive o limite a partir da definição do problema e valide com a frente de referência.

## Decisão recomendada
1. Use ponto de referência **por problema**, não apenas por suíte.
2. Use o mesmo ponto para todos os algoritmos, execuções e sementes de uma mesma configuração problema–\(m\).
3. Mantenha HV bruto e NHV em arquivos e análises separados.
4. Não normalize objetivos apenas para permitir um ponto de referência simétrico.
5. Registre o ponto de referência em metadados do experimento ou documentação reprodutível.

## Procedimentos
1. Leia `HANDLER.md` e registre a tarefa como `in_progress`.
2. Crie `docs/hypervolume-reference-points.md`.
3. Implemente `StaticParams.getHypervolumeReferencePoint(problemName, m)`, caso ainda não exista, com tabela explícita por problema.
4. Garanta que `PISAHypervolume` receba ou utilize o ponto definido, conforme a API real do jMetal 7.8.
5. Se a API não permitir configurar o ponto diretamente, documente a limitação e avalie uma alternativa tecnicamente válida; não force uma API inexistente.
6. Crie testes que verifiquem:
   - dimensionalidade do ponto;
   - valores finitos;
   - dominância do ponto sobre as soluções válidas;
   - consistência entre chamadas para o mesmo problema e \(m\).
7. Atualize `HANDLER.md`.

## Arquivos envolvidos
- `src/main/java/br/usp/lti/newmoea/study/StaticParams.java`
- `src/main/java/br/usp/lti/newmoea/study/WFGStudy.java`
- `src/test/java/br/usp/lti/newmoea/**`
- `docs/hypervolume-reference-points.md`
- `HANDLER.md`

## Restrições
- Não altere `MOEAD_Weights/`.
- Não altere a lista de problemas DTLZ.
- Não misture HV bruto e NHV em uma mesma coluna estatística.
- Não use `Main-Hipervolume.java` no pipeline.
- Não implemente gerador Monte Carlo próprio sem necessidade comprovada.

## Critérios de aceitação
- Existe tabela documentada de pontos de referência por problema e \(m\).
- O ponto é consistente para todos os algoritmos.
- HV bruto e NHV continuam separados.
- Testes validam dimensionalidade, finitude e dominância.
- `mvn clean test` passa.
- `HANDLER.md` registra evidências, limitações e decisão final.

## Testes
- Teste unitário de `getHypervolumeReferencePoint`.
- Teste de HV com frentes pequenas e controladas.
- Teste negativo para ponto de referência de dimensionalidade incorreta.
- Teste de integração com `-Druns=1`.

## Entregáveis
- `docs/hypervolume-reference-points.md`.
- Código mínimo necessário.
- Testes automatizados.
- Atualização de `HANDLER.md`.

## Dependências
- T06.
- Coordenação com T07 para evitar conflito em `WFGStudy.java`.
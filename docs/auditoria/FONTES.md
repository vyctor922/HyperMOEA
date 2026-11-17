# Fontes e controle de evidência

## Fontes locais efetivamente disponíveis nesta análise
- HANDLER.md: histórico T01–T11; conclusões de execução são declarações dos agentes, não execuções reproduzidas nesta análise.
- WFGStudy.java: snapshot atual de 32.990 bytes; StaticParams.java: 16.855 bytes.
- README.md, AGENTS.md, TASK_COORDINATOR.md; Main-Hipervolume.java; versões anteriores anexadas, somente como histórico.
- dissertacao_victoralexandre.pdf, 46 páginas: proposta pré-experimentos. Seções 3.3, 3.5–3.7 e 4.1.
- Os arquivos individuais de tasks/, pom.xml, FDEA.java, validadores, PerformanceProfiler, JARs, CSVs, relatórios e logs citados não foram anexados. Não se inferiu seu conteúdo a partir do handler.

## Fontes técnicas consultadas com sucesso
R1. R Core Team. kruskal.test, manual oficial do pacote stats. https://stat.ethz.ch/R-manual/R-devel/library/stats/html/kruskal.test.html — teste de postos, aproximação qui-quadrado, referências a Hollander e Wolfe (1973), pp. 115–120. A versão R-devel consultada não fixa a versão a ser usada nos experimentos; registrar versão local.
R2. R Core Team. p.adjust, manual oficial. https://stat.ethz.ch/R-manual/R-devel/library/stats/html/p.adjust.html — Holm controla FWER e não exige independência entre os testes. Referência: Holm, S. (1979). A Simple Sequentially Rejective Multiple Test Procedure. Scandinavian Journal of Statistics 6, 65–70.
R3. Oracle. Java SE 21, Future.cancel e Future.get(timeout). https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html — cancelamento tenta interromper; não prova término do cálculo.
R4. Oracle. Java SE 21, Process. https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Process.html — waitFor(timeout), destroyForcibly e espera pelo término; drenar/redirecionar stdout/stderr para evitar bloqueios.
R5. Oracle. Java SE 21, ThreadMXBean. https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html — CPU por thread de plataforma; precisão em nanossegundos não implica acurácia; suporte/habilitação precisam ser verificados.
R6. jMetal, documentação oficial Quality indicators. https://jmetal.readthedocs.io/en/latest/qualityIndicators.html — matrizes, expectativa de normalização e exemplo com limites da referência. A página retornada apresenta versão 7.7; NÃO comprova o comportamento binário de 7.8.
R7. jMetal, documentação oficial Algorithms. https://jmetal.readthedocs.io/en/latest/algorithms.html — templates e ciclo de avaliação. Corpo retornado apresenta versão 7.11; NÃO usar para afirmar assinaturas de 7.8.
R8. IEEE, registro primário de A Fuzzy Decomposition-Based Multi/Many-Objective Evolutionary Algorithm. https://ieeexplore.ieee.org/document/9158526/ — decomposição fuzzy, predição da forma e seleção de soluções menos similares; distinto de alegações genéricas de dominância fuzzy. Registro consultado mostra publicação online em 2020; a dissertação identifica volume final 52(5), 3495–3509, 2022. Confirmar autores/DOI na auditoria bibliográfica antes da versão definitiva.

## Publicações identificadas; exigir conferência de conteúdo antes de fixar valores
Estas referências são identificáveis, mas não se obteve texto integral nesta sessão. Não usar esta lista como evidência de uma tabela de parâmetros não consultada.
P1. Deb, K.; Jain, H. (2014). An Evolutionary Many-Objective Optimization Algorithm Using Reference-Point-Based Nondominated Sorting Approach, Part I: Solving Problems With Box Constraints. IEEE TEVC 18(4), 577–601. DOI 10.1109/TEVC.2013.2281535.
P2. Li, K.; Deb, K.; Zhang, Q.; Kwong, S. (2015). An Evolutionary Many-Objective Optimization Algorithm Based on Dominance and Decomposition. IEEE TEVC 19(5), 694–716. Identificar DOI no editor; não reutilizar o título de algoritmo de dois arquivos atribuído a MOEA/DD em respostas anteriores.
P3. Cheng, R.; Jin, Y.; Olhofer, M.; Sendhoff, B. (2016). A Reference Vector Guided Evolutionary Algorithm for Many-Objective Optimization. IEEE TEVC 20(5), 773–791. DOI 10.1109/TEVC.2016.2519378.
P4. Panichella, A. (2019). An Adaptive Evolutionary Algorithm Based on Non-Euclidean Geometry for Many-Objective Optimization. GECCO, 595–603. DOI 10.1145/3321707.3321839. Identificar separadamente publicação/código originais de AGE-MOEA-II; não fixar ano 2020 sem conferência.
P5. Deb et al. (2002). Scalable Multi-Objective Optimization Test Problems. CEC, vol. 1, 825–830. Conferir formulações e comentários sobre degenerescência de DTLZ5/6 em muitos objetivos.
P6. Huband et al. (2006). A Review of Multiobjective Test Problems and a Scalable Test Problem Toolkit. IEEE TEVC 10(5), 477–506. Conferir transformações, funções de forma e escalas WFG por problema.
P7. Das, I.; Dennis, J. E. (1998). Normal-Boundary Intersection: A New Method for Generating the Pareto Surface in Nonlinear Multicriteria Optimization Problems. SIAM Journal on Optimization 8(3), 631–657. A grade no simplex não é automaticamente amostragem uniforme de uma PF curva.
P8. Brockhoff, D.; Wagner, T.; Trautmann, H. (2012). On the Properties of the R2 Indicator. GECCO, 465–472. DOI 10.1145/2330163.2330230.
P9. Bader, J.; Zitzler, E. (2011). HypE: An Algorithm for Fast Hypervolume-Based Many-Objective Optimization. Evolutionary Computation 19(1), 45–76. DOI 10.1162/EVCO_a_00009. HypE estima contribuições para seleção; não é automaticamente o estimador de HV total do projeto.
P10. Shang et al. (2021). A Survey on the Hypervolume Indicator in Evolutionary Multiobjective Optimization. IEEE TEVC 25(1), 1–20. Distinguir ponto nadir, referência HV e ideal; verificar hipóteses de monotonicidade.
P11. Dunn, O. J. (1964). Multiple Comparisons Using Rank Sums. Technometrics 6(3), 241–252. Conferir fórmula de empates e implementação R escolhida; não substituir automaticamente por Nemenyi após Kruskal–Wallis.

## Limitações de acesso
Tentativas de recuperar fontes raw GitHub para tag presumida jmetal-7.8 falharam. Isso NÃO demonstra ausência da tag, classe ou método. O agente local deve obter source JAR correspondente ao dependency:tree e comparar checksum; usar javap -c -p quando necessário. As URLs DOI de P8/P9 não retornaram texto nesta sessão. Não foi executado Maven nem Java. GitHub/Drive descobertos estavam desconectados; conectar em Configurações > Connectors se desejar auditoria do repositório remoto.

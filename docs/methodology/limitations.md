# 7. Limitações Metodológicas e Técnicas

1. **Amostragem Discreta das Frentes de Pareto em Alta Dimensão ($m=15$):**
   - As frentes empíricas e analíticas em $m=15$ contêm conjuntos discretos finitos de pontos gerados por malha simplex. Em hiper-espaços de 15 dimensões, qualquer discretização finita é necessariamente esparsa.
2. **Degenerescência e Não-Invertibilidade das Frentes DTLZ:**
   - DTLZ5 e DTLZ6 possuem frentes degeneradas de dimensão topológica reduzida (curvas em $\mathbb{R}^m$), enquanto DTLZ7 é desconexa e multimodal. A densidade de pontos no espaço de objetivos é irregular.
3. **Não-Compatibilidade de Pareto do IGD Euclidiano:**
   - Conforme provado na T19, o indicador IGD euclidiano clássico pode favorecer frentes com soluções dominadas que estejam geometricamente mais próximas dos pontos de referência, criando falsas impressões de melhoria. Por isso, $HV$, $NHV$, $IGD+$ e $R2$ são adotados como critérios de decisão confiáveis.
4. **Precisão Estocástica do Hipervolume por Monte Carlo:**
   - Para $m=15$, o hipervolume dominado por frentes muito concentradas no simplex pode representar uma fração ínfima do volume da caixa delimitadora ($p \ll 10^{-4}$). O erro padrão relativo do estimador de Monte Carlo cresce quando $p \to 0$. O intervalo de confiança de Wilson Score é reportado obrigatoriamente para expressar essa incerteza.
5. **Comparações Inter-Células:**
   - Comparações estatísticas são restritas a cada célula $(problema, m, indicador)$. Agregações de postos entre problemas com propriedades topológicas e escalas totalmente distintas não constituem inferência estatística válida.


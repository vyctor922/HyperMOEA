#!/usr/bin/env Rscript
# ==============================================================================
# Script de Inferência Estatística Não-Paramétrica: Kruskal-Wallis & Dunn-Holm
# Projeto hypeMOEA (T21)
# ==============================================================================

suppressPackageStartupMessages({
  if (!require("dunn.test", quietly = TRUE)) {
    # Se dunn.test não estiver instalado, implementamos Dunn-Holm nativamente
    use_dunn_pkg <- FALSE
  } else {
    use_dunn_pkg <- TRUE
  }
})

cat("=== hypeMOEA Statistical Inference: Kruskal-Wallis & Dunn-Holm ===\n")
cat("R version:", R.version.string, "\n\n")

# Exemplo oficial R1 para validação
# Grupos com empates:
# G1: 2.9, 3.0, 2.5, 2.6, 3.2
# G2: 3.8, 3.7, 4.0, 3.8, 3.9
# G3: 2.8, 3.4, 3.7, 3.2, 2.8

g1 <- c(2.9, 3.0, 2.5, 2.6, 3.2)
g2 <- c(3.8, 3.7, 4.0, 3.8, 3.9)
g3 <- c(2.8, 3.4, 3.7, 3.2, 2.8)

df_example <- data.frame(
  value = c(g1, g2, g3),
  group = factor(rep(c("G1", "G2", "G3"), each = 5))
)

kw <- kruskal.test(value ~ group, data = df_example)
cat("Kruskal-Wallis Omnibus Test on Benchmark Fixture:\n")
cat(sprintf("  H statistic: %.6f\n", kw$statistic))
cat(sprintf("  Degrees of freedom: %d\n", kw$parameter))
cat(sprintf("  p-value: %.6e\n", kw$p.value))

# Dunn test nativo em R com correção de empates e Holm FWER
run_dunn_holm_native <- function(values, groups) {
  groups <- as.factor(groups)
  k <- nlevels(groups)
  N <- length(values)
  ranks <- rank(values)
  
  # Empates
  tie_tab <- table(values)
  tie_sum <- sum(tie_tab^3 - tie_tab)
  sigma_base_sq <- (N * (N + 1) / 12) - (tie_sum / (12 * (N - 1)))
  
  levels_g <- levels(groups)
  mean_ranks <- tapply(ranks, groups, mean)
  n_g <- tapply(ranks, groups, length)
  
  pairs <- combn(levels_g, 2, simplify = FALSE)
  results <- list()
  
  for (pair in pairs) {
    g1 <- pair[1]
    g2 <- pair[2]
    mr1 <- mean_ranks[[g1]]
    mr2 <- mean_ranks[[g2]]
    n1 <- n_g[[g1]]
    n2 <- n_g[[g2]]
    
    se <- sqrt(sigma_base_sq * (1/n1 + 1/n2))
    z <- (mr1 - mr2) / se
    p_raw <- 2 * (1 - pnorm(abs(z)))
    
    # Vargha-Delaney A12
    v1 <- values[groups == g1]
    v2 <- values[groups == g2]
    comp <- outer(v1, v2, function(x, y) ifelse(x > y, 1, ifelse(x == y, 0.5, 0)))
    a12 <- mean(comp)
    
    results[[length(results) + 1]] <- data.frame(
      group1 = g1,
      group2 = g2,
      z = z,
      p_raw = p_raw,
      a12 = a12,
      stringsAsFactors = FALSE
    )
  }
  
  df_res <- do.call(rbind, results)
  df_res$p_holm <- p.adjust(df_res$p_raw, method = "holm")
  return(df_res)
}

cat("\nDunn Post-Hoc Test with Holm FWER Adjustment:\n")
dunn_res <- run_dunn_holm_native(df_example$value, df_example$group)
print(dunn_res)

cat("\nSession Info:\n")
sessionInfo()


#!/usr/bin/env Rscript
# ==============================================================================
# Script de Inferência Estatística Não-Paramétrica: Kruskal-Wallis & Dunn-Holm
# Projeto hypeMOEA (T21 / T36 — Opção A: 100% R Base Nativo Autocontido)
#
# Implementação adotada e documentada:
# - Kruskal-Wallis: stats::kruskal.test (R base) + tratamento explícito para
#   caso degenerado de empates totais (C == 0 -> H = 0, p = 1.0 sem NaN);
# - Pós-teste de Dunn: fórmula analítica exata de Dunn (1964) com correção de
#   empates (sigma^2 = N(N+1)/12 - sum(t^3 - t)/(12*(N-1))) e stats::pnorm;
# - Controle FWER: stats::p.adjust(..., method = "holm") (Holm, 1979);
# - Tamanho de efeito: Vargha & Delaney (2000) A12 exato.
# (Não requer nem invoca o pacote externo CRAN dunn.test.)
# ==============================================================================

run_kruskal_safe_base_r <- function(values, group_names) {
  N <- length(values)
  unique_groups <- unique(group_names)
  k <- length(unique_groups)
  ranks <- rank(values, ties.method = "average")

  tie_tab <- table(values)
  tie_sum <- sum(as.numeric(tie_tab)^3 - as.numeric(tie_tab))
  denom_c <- as.numeric(N)^3 - as.numeric(N)
  c_factor <- if (denom_c > 0) 1.0 - (tie_sum / denom_c) else 0.0

  if (c_factor <= 1e-15 || length(unique(values)) <= 1) {
    return(list(h = 0.0, df = k - 1L, p_value = 1.0, c_factor = 0.0))
  }

  f_groups <- factor(group_names, levels = unique_groups)
  kw <- stats::kruskal.test(values ~ f_groups)
  h_val <- max(0.0, as.numeric(kw$statistic))
  p_val <- as.numeric(kw$p.value)
  return(list(h = h_val, df = as.integer(kw$parameter), p_value = p_val, c_factor = c_factor))
}

# Dunn test analítico exato em R base com correção de empates e Holm FWER
run_dunn_holm_native <- function(values, group_names) {
  unique_groups <- unique(group_names)
  f_groups <- factor(group_names, levels = unique_groups)
  k <- nlevels(f_groups)
  N <- length(values)
  ranks <- rank(values, ties.method = "average")

  tie_tab <- table(values)
  tie_sum <- sum(as.numeric(tie_tab)^3 - as.numeric(tie_tab))
  sigma_base_sq <- (as.numeric(N) * (as.numeric(N) + 1.0) / 12.0) -
    (tie_sum / (12.0 * (as.numeric(N) - 1.0)))
  if (!is.finite(sigma_base_sq) || sigma_base_sq < 0.0) {
    sigma_base_sq <- 0.0
  }

  levels_g <- levels(f_groups)
  mean_ranks <- tapply(ranks, f_groups, mean)
  n_g <- tapply(ranks, f_groups, length)

  pairs <- utils::combn(levels_g, 2, simplify = FALSE)
  results <- list()

  for (pair in pairs) {
    g1 <- pair[1]
    g2 <- pair[2]
    mr1 <- as.numeric(mean_ranks[[g1]])
    mr2 <- as.numeric(mean_ranks[[g2]])
    n1 <- as.numeric(n_g[[g1]])
    n2 <- as.numeric(n_g[[g2]])

    se <- sqrt(sigma_base_sq * (1.0 / n1 + 1.0 / n2))
    if (!is.finite(se) || se <= 1e-15) {
      z <- 0.0
      p_raw <- 1.0
    } else {
      z <- (mr1 - mr2) / se
      p_raw <- 2.0 * stats::pnorm(abs(z), lower.tail = FALSE)
    }

    v1 <- values[f_groups == g1]
    v2 <- values[f_groups == g2]
    comp <- outer(v1, v2, function(x, y) ifelse(x > y, 1.0, ifelse(abs(x - y) < 1e-15, 0.5, 0.0)))
    a12 <- mean(comp)

    results[[length(results) + 1L]] <- data.frame(
      group1 = g1,
      group2 = g2,
      z = z,
      p_raw = p_raw,
      a12 = a12,
      stringsAsFactors = FALSE
    )
  }

  df_res <- do.call(rbind, results)
  df_res$p_holm <- stats::p.adjust(df_res$p_raw, method = "holm")
  return(df_res)
}

run_fixture_mode <- function() {
  cat("=== hypeMOEA Statistical Inference: Kruskal-Wallis & Dunn-Holm (Base R Exact) ===\n")
  cat("R_VERSION=", R.version.string, "\n", sep = "")
  cat("DUNN_IMPLEMENTATION=BASE_R_ANALYTICAL_EXACT\n\n")

  g1 <- c(2.9, 3.0, 2.5, 2.6, 3.2)
  g2 <- c(3.8, 3.7, 4.0, 3.8, 3.9)
  g3 <- c(2.8, 3.4, 3.7, 3.2, 2.8)

  vals <- c(g1, g2, g3)
  grps <- rep(c("G1", "G2", "G3"), each = 5)

  kw <- run_kruskal_safe_base_r(vals, grps)
  cat("Kruskal-Wallis Omnibus Test on Benchmark Fixture:\n")
  cat(sprintf("  H statistic: %.6f\n", kw$h))
  cat(sprintf("  Degrees of freedom: %d\n", kw$df))
  cat(sprintf("  p-value: %.6e\n", kw$p_value))

  cat("\nDunn Post-Hoc Test with Holm FWER Adjustment:\n")
  dunn_res <- run_dunn_holm_native(vals, grps)
  print(dunn_res)
}

run_study_validation_mode <- function(summary_csv, java_kw_csv, java_dunn_csv, out_comp_csv, tol_h, tol_p) {
  cat("R_VERSION=", R.version.string, "\n", sep = "")
  cat("DUNN_IMPLEMENTATION=BASE_R_ANALYTICAL_EXACT\n")

  raw_summary <- utils::read.csv(summary_csv, stringsAsFactors = FALSE, na.strings = c("NA", "NaN", ""))
  java_kw <- utils::read.csv(java_kw_csv, stringsAsFactors = FALSE, na.strings = c("NA", "NaN", ""))
  java_dunn <- utils::read.csv(java_dunn_csv, stringsAsFactors = FALSE, na.strings = c("NA", "NaN", ""))

  complete_cells <- java_kw[java_kw$availability_status == "COMPLETE", , drop = FALSE]
  comp_rows <- list()

  max_diff_h <- 0.0
  max_diff_p_kw <- 0.0
  max_diff_z <- 0.0
  max_diff_p_holm <- 0.0
  max_diff_a12 <- 0.0
  validated_cells <- 0L
  validated_pairs <- 0L
  all_passed <- TRUE

  for (idx in seq_len(nrow(complete_cells))) {
    cell <- complete_cells[idx, ]
    prob <- cell$problem
    ind <- cell$indicator

    sub <- raw_summary[raw_summary$Problem == prob & raw_summary$IndicatorName == ind, , drop = FALSE]
    sub$num_val <- suppressWarnings(as.numeric(sub$IndicatorValue))
    sub <- sub[is.finite(sub$num_val), , drop = FALSE]

    # Preserva exatamente a ordem de descoberta dos grupos no CSV do estudo
    r_kw <- run_kruskal_safe_base_r(sub$num_val, sub$Algorithm)
    diff_h <- abs(r_kw$h - as.numeric(cell$h_stat))
    diff_p_kw <- abs(r_kw$p_value - as.numeric(cell$p_value))
    max_diff_h <- max(max_diff_h, diff_h)
    max_diff_p_kw <- max(max_diff_p_kw, diff_p_kw)
    validated_cells <- validated_cells + 1L

    cell_ok <- (diff_h <= tol_h) && (diff_p_kw <= tol_p)
    if (!cell_ok) {
      all_passed <- FALSE
    }

    r_dunn <- run_dunn_holm_native(sub$num_val, sub$Algorithm)
    j_dunn <- java_dunn[java_dunn$problem == prob & java_dunn$indicator == ind & java_dunn$availability_status == "COMPLETE", , drop = FALSE]

    for (p_idx in seq_len(nrow(r_dunn))) {
      rp <- r_dunn[p_idx, ]
      jp <- j_dunn[j_dunn$group1 == rp$group1 & j_dunn$group2 == rp$group2, , drop = FALSE]
      if (nrow(jp) == 0L) {
        jp <- j_dunn[j_dunn$group1 == rp$group2 & j_dunn$group2 == rp$group1, , drop = FALSE]
        r_z_aligned <- -rp$z
        r_a12_aligned <- 1.0 - rp$a12
      } else {
        r_z_aligned <- rp$z
        r_a12_aligned <- rp$a12
      }

      if (nrow(jp) != 1L) {
        all_passed <- FALSE
        next
      }

      dz <- abs(r_z_aligned - as.numeric(jp$z_stat))
      dph <- abs(rp$p_holm - as.numeric(jp$p_holm))
      da12 <- abs(r_a12_aligned - as.numeric(jp$raw_a12_g1_gt_g2))

      max_diff_z <- max(max_diff_z, dz)
      max_diff_p_holm <- max(max_diff_p_holm, dph)
      max_diff_a12 <- max(max_diff_a12, da12)
      validated_pairs <- validated_pairs + 1L

      pair_ok <- (dz <= tol_h) && (dph <= tol_p) && (da12 <= tol_h)
      if (!pair_ok) {
        all_passed <- FALSE
      }

      comp_rows[[length(comp_rows) + 1L]] <- data.frame(
        problem = prob,
        indicator = ind,
        group1 = jp$group1,
        group2 = jp$group2,
        java_h = as.numeric(cell$h_stat),
        r_h = r_kw$h,
        abs_diff_h = diff_h,
        java_p_kw = as.numeric(cell$p_value),
        r_p_kw = r_kw$p_value,
        abs_diff_p_kw = diff_p_kw,
        java_z = as.numeric(jp$z_stat),
        r_z = r_z_aligned,
        abs_diff_z = dz,
        java_p_holm = as.numeric(jp$p_holm),
        r_p_holm = rp$p_holm,
        abs_diff_p_holm = dph,
        java_a12 = as.numeric(jp$raw_a12_g1_gt_g2),
        r_a12 = r_a12_aligned,
        abs_diff_a12 = da12,
        status = ifelse(cell_ok && pair_ok, "PASS", "FAIL"),
        stringsAsFactors = FALSE
      )
    }
  }

  out_df <- if (length(comp_rows) > 0L) do.call(rbind, comp_rows) else data.frame()
  utils::write.csv(out_df, out_comp_csv, row.names = FALSE)

  cat(sprintf("VALIDATED_CELLS=%d\n", validated_cells))
  cat(sprintf("VALIDATED_PAIRS=%d\n", validated_pairs))
  cat(sprintf("MAX_DIFF_H=%.12e\n", max_diff_h))
  cat(sprintf("MAX_DIFF_P_KW=%.12e\n", max_diff_p_kw))
  cat(sprintf("MAX_DIFF_Z=%.12e\n", max_diff_z))
  cat(sprintf("MAX_DIFF_P_HOLM=%.12e\n", max_diff_p_holm))
  cat(sprintf("MAX_DIFF_A12=%.12e\n", max_diff_a12))
  cat(sprintf("STATUS=%s\n", ifelse(all_passed, "PASS", "FAIL")))

  if (!all_passed) {
    quit(status = 2L)
  }
}

args <- commandArgs(trailingOnly = TRUE)
if (length(args) == 0L || "--fixture" %in% args) {
  run_fixture_mode()
} else {
  get_arg <- function(flag, default_val = NULL) {
    pos <- match(flag, args)
    if (!is.na(pos) && pos < length(args)) {
      return(args[pos + 1L])
    }
    return(default_val)
  }
  summary_csv <- get_arg("--summary-csv")
  java_kw_csv <- get_arg("--java-kw-csv")
  java_dunn_csv <- get_arg("--java-dunn-csv")
  out_comp_csv <- get_arg("--output-comparison-csv")
  tol_h <- as.numeric(get_arg("--tol-h", "1e-6"))
  tol_p <- as.numeric(get_arg("--tol-p", "1e-5"))

  if (is.null(summary_csv) || is.null(java_kw_csv) || is.null(java_dunn_csv) || is.null(out_comp_csv)) {
    stop("Missing required arguments: --summary-csv, --java-kw-csv, --java-dunn-csv, --output-comparison-csv")
  }
  run_study_validation_mode(summary_csv, java_kw_csv, java_dunn_csv, out_comp_csv, tol_h, tol_p)
}

package br.usp.lti.newmoea.util;

import java.nio.file.Path;

/**
 * Representação imutável e estruturada de uma frente de Pareto de referência resolvida
 * canonicamente pelo sistema.
 */
public record ResolvedFrontArtifact(
        String problemName,
        int numberOfObjectives,
        Path path,
        String sha256,
        String sourceCategory,
        int totalPoints,
        int uniquePoints,
        boolean zeroReplaced) {

    public ResolvedFrontArtifact {
        if (problemName == null || problemName.isBlank()) {
            throw new IllegalArgumentException("problemName não pode ser nulo ou vazio");
        }
        if (numberOfObjectives <= 0) {
            throw new IllegalArgumentException("numberOfObjectives deve ser > 0, mas foi: " + numberOfObjectives);
        }
        if (path == null) {
            throw new IllegalArgumentException("path não pode ser nulo");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 não pode ser nulo ou vazio");
        }
        if (sourceCategory == null || sourceCategory.isBlank()) {
            throw new IllegalArgumentException("sourceCategory não pode ser nula ou vazia");
        }
    }
}


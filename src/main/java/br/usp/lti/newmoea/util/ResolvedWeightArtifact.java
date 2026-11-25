package br.usp.lti.newmoea.util;

import java.nio.file.Path;

/**
 * Representação imutável e estruturada de um arquivo de vetores de pesos
 * (MOEA/DD) resolvido canonicamente pelo sistema, permitindo que os dados sejam
 * compartilhados entre várias threads sem risco de uma thread alterar os dados
 * que outra está lendo.
 */
public record ResolvedWeightArtifact(
        int numberOfObjectives,
        int populationSize,
        Path path,
        String sha256,
        String sourceCategory,
        boolean usedInMemoryByFramework,
        int totalVectors,
        int uniqueVectors) {

    public ResolvedWeightArtifact {
        if (numberOfObjectives <= 0) {
            throw new IllegalArgumentException("numberOfObjectives deve ser > 0, mas foi: " + numberOfObjectives);
        }
        if (populationSize <= 0) {
            throw new IllegalArgumentException("populationSize deve ser > 0, mas foi: " + populationSize);
        }
        if (!usedInMemoryByFramework && path == null) {
            throw new IllegalArgumentException("path não pode ser nulo quando não executado em memória");
        }
        if (sourceCategory == null || sourceCategory.isBlank()) {
            throw new IllegalArgumentException("sourceCategory não pode ser nula ou vazia");
        }
    }

    /**
     * Indica se o artefato foi efetivamente dispensado de arquivo físico em disco
     * pela política
     * explícita em memória ({@code path == null && usedInMemoryByFramework}).
     */
    public boolean inMemory() {
        return path == null && usedInMemoryByFramework;
    }
}

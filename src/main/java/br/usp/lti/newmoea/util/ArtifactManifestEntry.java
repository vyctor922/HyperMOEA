package br.usp.lti.newmoea.util;

/**
 * Linha estruturada do manifesto consolidado de artefatos ativos.
 */
public record ArtifactManifestEntry(
        String artifactType,
        String identifier,
        String problemOrScope,
        int m,
        String policy,
        String sourceCategory,
        String canonicalPath,
        long sizeBytes,
        int lineCount,
        int uniqueCount,
        String sha256,
        String runtimeConsumptionMode) {

    public static final String CSV_HEADER =
            "artifact_type,identifier,problem_or_scope,m,policy,source_category,canonical_path,size_bytes,line_count,unique_count,sha256,runtime_consumption_mode";

    public String toCsvRow() {
        return String.join(
                ",",
                artifactType,
                identifier,
                problemOrScope,
                Integer.toString(m),
                policy,
                sourceCategory,
                canonicalPath,
                Long.toString(sizeBytes),
                Integer.toString(lineCount),
                Integer.toString(uniqueCount),
                sha256,
                runtimeConsumptionMode);
    }
}


package com.example.editor.integration.compiler;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import tools.jackson.databind.json.JsonMapper;

/** Evidence obligations emitted by real HYDRA exporters, never a native-runtime certificate. */
public final class TechnologyFidelityManifestSupport {
    /** Descripción: Impide crear instancias de la utilidad de manifiestos de fidelidad. Entradas: ninguna. Salida: no crea ningún objeto accesible. */
    private TechnologyFidelityManifestSupport() {}

    /** Descripción: Documenta todos los artefactos canónicos y representaciones del editor sin atribuirles ejecución nativa no verificada. Entradas: mapper para serializar JSON, request con los artefactos originales, editor con sus vistas y technology como destino de exportación. Salida: JSON de obligaciones de fidelidad, con estado de certificación explícitamente no ejecutado. */
    public static String json(JsonMapper mapper, TechnologyCompilationRequest request,
            EditorDefinitionModel editor, String technology) {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        for(CanonicalArtifactContent artifact : request.artifacts().stream()
                .sorted(Comparator.comparing((CanonicalArtifactContent a) -> a.kind().name())
                        .thenComparing(CanonicalArtifactContent::documentId)).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", artifact.kind().name());
            item.put("documentId", artifact.documentId());
            item.put("sourceSha256", hash(artifact.content()));
            item.put("materialization", artifact.kind() == IntegrationArtifactKind.METAMODEL
                    || artifact.kind() == IntegrationArtifactKind.EDITOR_DEFINITION
                    ? "GENERATED_UNVERIFIED" : "CANONICAL_PRESERVED_OR_BRIDGED_UNVERIFIED");
            item.put("nativeRuntimeEvidence", "NOT_EXECUTED");
            artifacts.add(item);
        }
        List<Map<String, Object>> representations = new ArrayList<>();
        for(RepresentationDefinition view : editor.representations().stream()
                .sorted(Comparator.comparing(RepresentationDefinition::id)).toList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", view.id());
            item.put("kind", view.kind().name());
            item.put("generation", view.kind() == RepresentationKind.DIAGRAM
                    ? "GENERATED_DIAGRAM_UNVERIFIED" : "CANONICAL_ONLY");
            item.put("nativeRuntimeEvidence", "NOT_EXECUTED");
            representations.add(item);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("technology", technology);
        report.put("generatorVersion", request.generatorVersion());
        report.put("metamodelId", request.profile().metamodelId());
        report.put("sourceArtifacts", artifacts);
        report.put("representations", representations);
        report.put("nativeRuntimeExecuted", false);
        report.put("functionalParityCertified", false);
        report.put("certificationRequired", List.of("OFFICIAL_TOOLCHAIN", "REAL_GENERATED_PROJECT",
                "OBSERVED_BEHAVIOR", "INDEPENDENT_ORACLE", "MODEL_ROUND_TRIP"));
        return mapper.writeValueAsString(report);
    }

    /** Descripción: Calcula la huella SHA-256 de los bytes originales para vincular cada artefacto con su origen. Entradas: bytes del artefacto. Salida: digest hexadecimal SHA-256 o excepción si el algoritmo no está disponible. */
    private static String hash(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException exception) {throw new IllegalStateException(exception);}
    }
}

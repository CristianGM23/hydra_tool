package com.example.editor.integration.projection.canonical;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.integration.projection.GeneratedProjectionArtifact;
import com.example.editor.integration.projection.ProjectionGenerationResult;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Proyección PlantUML completa que conserva el JSON canónico como provenance importable. */
@Component
public class PlantUmlCanonicalProjectionAdapter implements CanonicalProjectionAdapter {
    private static final Pattern META = Pattern.compile("^' HYDRA:(kind|id|media|sha256|data)=(.*)$", Pattern.MULTILINE);
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de PlantUmlCanonicalProjectionAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public PlantUmlCanonicalProjectionAdapter(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de PlantUmlCanonicalProjectionAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public CanonicalProjectionFormat format(){return CanonicalProjectionFormat.PLANTUML;}

    /** Descripción: Crea o prepara el resultado de «generate» dentro de PlantUmlCanonicalProjectionAdapter. Entradas: artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectionGenerationResult generate(List<CanonicalArtifactContent> artifacts){
        List<GeneratedProjectionArtifact> generated = new ArrayList<>(); List<IntegrationDiagnostic> diagnostics = new ArrayList<>();
        for(CanonicalArtifactContent artifact : artifacts == null ? List.<CanonicalArtifactContent>of() : artifacts){
            try{
                String source = projection(artifact);
                String path = "projections/plantuml/" + CanonicalProjectionSupport.slug(artifact.kind().name()) + "-" + CanonicalProjectionSupport.slug(artifact.documentId()) + ".puml";
                generated.add(new GeneratedProjectionArtifact(path, "text/x-plantuml", source.getBytes(StandardCharsets.UTF_8),
                        List.of(artifact.documentId()), Map.of("fidelity", "PROJECTION_ONLY", "roundTrip", "embedded-canonical")));
            }catch(RuntimeException exception){
                diagnostics.add(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, "PLANTUML_PROJECTION_FAILED",
                        artifact.documentId(), artifact.kind().name(), exception.getMessage()));
            }
        }
        return new ProjectionGenerationResult(generated, diagnostics);
    }

    /** Descripción: Ejecuta la operación «importProjection» correspondiente a la responsabilidad de PlantUmlCanonicalProjectionAdapter. Entradas: fileName: archivo o contenido que debe procesarse; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public CanonicalProjectionImportResult importProjection(String fileName, byte[] content){
        String source = new String(content == null ? new byte[0] : content, StandardCharsets.UTF_8);
        Map<String, String> metadata = new java.util.LinkedHashMap<>(); Matcher matcher = META.matcher(source);
        while(matcher.find()){metadata.put(matcher.group(1), matcher.group(2).trim());}
        if(metadata.containsKey("kind") && metadata.containsKey("id") && metadata.containsKey("data")){
            try{
                byte[] recovered = Base64.getDecoder().decode(metadata.get("data")); String hash = CanonicalProjectionSupport.sha256(recovered);
                if(metadata.containsKey("sha256") && !metadata.get("sha256").equals(hash)){
                    return new CanonicalProjectionImportResult(IntegrationSupportLevel.UNSUPPORTED, List.of(), List.of(),
                            List.of(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, "PROJECTION_HASH_MISMATCH", metadata.get("id"), fileName, "Embedded canonical content does not match its SHA-256.")));
                }
                CanonicalProjectionRecoveredArtifact artifact = new CanonicalProjectionRecoveredArtifact(IntegrationArtifactKind.valueOf(metadata.get("kind")),
                        metadata.get("id"), metadata.getOrDefault("media", "application/json"), recovered, hash, Map.of("source", "PlantUML"));
                return new CanonicalProjectionImportResult(IntegrationSupportLevel.FULL, List.of(artifact), List.of(), List.of());
            }catch(RuntimeException exception){
                return new CanonicalProjectionImportResult(IntegrationSupportLevel.UNSUPPORTED, List.of(), List.of(),
                        List.of(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, "PLANTUML_IMPORT_FAILED", fileName, "$", exception.getMessage())));
            }
        }
        List<String> opaque = source.lines().filter(line -> line.startsWith("class ") || line.startsWith("component ") || line.startsWith("rectangle ")).limit(200).toList();
        return new CanonicalProjectionImportResult(opaque.isEmpty() ? IntegrationSupportLevel.UNSUPPORTED : IntegrationSupportLevel.PARTIAL,
                List.of(), opaque, List.of(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.WARNING, "PLANTUML_WITHOUT_HYDRA_PROVENANCE", fileName, "$", "The diagram can be analyzed visually but exact canonical recovery is unavailable.")));
    }

    /** Descripción: Ejecuta la operación «projection» correspondiente a la responsabilidad de PlantUmlCanonicalProjectionAdapter. Entradas: artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String projection(CanonicalArtifactContent artifact){
        StringBuilder result = new StringBuilder("@startuml\n");
        result.append("' HYDRA:kind=").append(artifact.kind().name()).append('\n');
        result.append("' HYDRA:id=").append(artifact.documentId()).append('\n');
        result.append("' HYDRA:media=").append(artifact.mediaType()).append('\n');
        result.append("' HYDRA:sha256=").append(CanonicalProjectionSupport.sha256(artifact.content())).append('\n');
        result.append("' HYDRA:data=").append(CanonicalProjectionSupport.base64(artifact.content())).append('\n');
        result.append("title ").append(CanonicalProjectionSupport.plant(artifact.kind() + " — " + artifact.documentId())).append("\n");
        if(artifact.kind() == IntegrationArtifactKind.METAMODEL){appendMetamodel(result, artifact);}else{appendGeneric(result, artifact);}
        result.append("note bottom\nProjection only. HYDRA canonical JSON is embedded for traceability and round-trip.\nend note\n@enduml\n");
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «appendMetamodel» correspondiente a la responsabilidad de PlantUmlCanonicalProjectionAdapter. Entradas: result: dato de entrada necesario para la operación; artifact: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendMetamodel(StringBuilder result, CanonicalArtifactContent artifact){
        try{
            MetaModel metamodel = mapper.readValue(artifact.content(), MetaModel.class);
            for(MetaClass metaClass : metamodel.classes()){
                result.append(metaClass.kind() == com.example.editor.metamodel.core.MetaClassKind.ABSTRACT_CLASS ? "abstract class \"" : metaClass.kind() == com.example.editor.metamodel.core.MetaClassKind.INTERFACE ? "interface \"" : "class \"")
                        .append(CanonicalProjectionSupport.plant(metaClass.name())).append("\" as ").append(alias(metaClass.id())).append(" {\n");
                metaClass.attributes().forEach(attribute -> result.append("  ").append(CanonicalProjectionSupport.plant(attribute.name())).append("\n"));
                metaClass.operations().forEach(operation -> result.append("  ").append(CanonicalProjectionSupport.plant(operation.name())).append("()\n"));
                result.append("}\n");
            }
            for(MetaRelation relation : metamodel.relations()){
                result.append(alias(relation.sourceTypeId())).append(relation.kind().name().contains("CONTAIN") ? " *-- " : " --> ")
                        .append(alias(relation.targetTypeId())).append(" : ").append(CanonicalProjectionSupport.plant(relation.name())).append('\n');
            }
        }catch(RuntimeException exception){appendGeneric(result, artifact);}
    }

    /** Descripción: Ejecuta la operación «appendGeneric» correspondiente a la responsabilidad de PlantUmlCanonicalProjectionAdapter. Entradas: result: dato de entrada necesario para la operación; artifact: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendGeneric(StringBuilder result, CanonicalArtifactContent artifact){
        try{
            JsonNode root = mapper.readTree(artifact.content());
            result.append("rectangle \"").append(CanonicalProjectionSupport.plant(label(root, artifact.documentId()))).append("\" as artifact_root\n");
            int index = 0;
            for(String field : List.of("rules", "definitions", "bindings", "flows", "representations", "forms", "points", "levels", "achievements", "challenges", "rewards", "entries", "profiles")){
                JsonNode values = root.path(field); if(!values.isArray()){continue;}
                for(JsonNode value : values){
                    if(index >= 120){break;}
                    String node = "n" + index++; result.append("rectangle \"").append(CanonicalProjectionSupport.plant(label(value, field))).append("\" as ").append(node).append('\n');
                    result.append("artifact_root --> ").append(node).append(" : ").append(field).append('\n');
                }
            }
        }catch(RuntimeException exception){result.append("rectangle \"").append(CanonicalProjectionSupport.plant(artifact.documentId())).append("\" as artifact_root\n");}
    }

    /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de PlantUmlCanonicalProjectionAdapter. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String label(JsonNode value, String fallback){
        if(value == null){return fallback;}
        for(String key : List.of("name", "label", "title", "id", "type")){String text = value.path(key).asText(); if(!text.isBlank()){return text;}}
        return fallback;
    }
    /** Descripción: Ejecuta la operación «alias» correspondiente a la responsabilidad de PlantUmlCanonicalProjectionAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String alias(String value){return "h_" + CanonicalProjectionSupport.slug(value).replace('.', '_').replace('-', '_');}
}

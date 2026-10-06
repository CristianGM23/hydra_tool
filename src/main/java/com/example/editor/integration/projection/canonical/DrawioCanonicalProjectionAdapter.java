package com.example.editor.integration.projection.canonical;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.integration.projection.GeneratedProjectionArtifact;
import com.example.editor.integration.projection.ProjectionGenerationResult;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Proyección Draw.io completa con metadatos HYDRA recuperables y XML seguro. */
@Component
public class DrawioCanonicalProjectionAdapter implements CanonicalProjectionAdapter {
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de DrawioCanonicalProjectionAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public DrawioCanonicalProjectionAdapter(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de DrawioCanonicalProjectionAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public CanonicalProjectionFormat format(){return CanonicalProjectionFormat.DRAWIO;}

    /** Descripción: Crea o prepara el resultado de «generate» dentro de DrawioCanonicalProjectionAdapter. Entradas: artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public ProjectionGenerationResult generate(List<CanonicalArtifactContent> artifacts){
        List<GeneratedProjectionArtifact> generated = new ArrayList<>(); List<IntegrationDiagnostic> diagnostics = new ArrayList<>();
        for(CanonicalArtifactContent artifact : artifacts == null ? List.<CanonicalArtifactContent>of() : artifacts){
            try{
                String xml = projection(artifact);
                String path = "projections/drawio/" + CanonicalProjectionSupport.slug(artifact.kind().name()) + "-" + CanonicalProjectionSupport.slug(artifact.documentId()) + ".drawio";
                generated.add(new GeneratedProjectionArtifact(path, "application/vnd.jgraph.mxfile", xml.getBytes(StandardCharsets.UTF_8),
                        List.of(artifact.documentId()), Map.of("fidelity", "PROJECTION_ONLY", "roundTrip", "embedded-canonical")));
            }catch(RuntimeException exception){diagnostics.add(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, "DRAWIO_PROJECTION_FAILED", artifact.documentId(), artifact.kind().name(), exception.getMessage()));}
        }
        return new ProjectionGenerationResult(generated, diagnostics);
    }

    /** Descripción: Ejecuta la operación «importProjection» correspondiente a la responsabilidad de DrawioCanonicalProjectionAdapter. Entradas: fileName: archivo o contenido que debe procesarse; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public CanonicalProjectionImportResult importProjection(String fileName, byte[] content){
        try{
            Document document = SecureXmlSupport.parse(content == null ? new byte[0] : content, "Draw.io projection");
            NodeList objects = document.getElementsByTagName("object"); List<CanonicalProjectionRecoveredArtifact> recovered = new ArrayList<>(); List<String> opaque = new ArrayList<>();
            for(int index = 0; index < objects.getLength(); index++){
                Element object = (Element) objects.item(index);
                if(object.hasAttribute("hydraKind") && object.hasAttribute("hydraDocumentId") && object.hasAttribute("hydraData")){
                    byte[] bytes = CanonicalProjectionSupport.decode(object.getAttribute("hydraData")); String hash = CanonicalProjectionSupport.sha256(bytes);
                    if(object.hasAttribute("hydraSha256") && !object.getAttribute("hydraSha256").equals(hash)){
                        return new CanonicalProjectionImportResult(IntegrationSupportLevel.UNSUPPORTED, List.of(), List.of(),
                                List.of(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, "PROJECTION_HASH_MISMATCH", object.getAttribute("hydraDocumentId"), fileName, "Embedded canonical content does not match its SHA-256.")));
                    }
                    recovered.add(new CanonicalProjectionRecoveredArtifact(IntegrationArtifactKind.valueOf(object.getAttribute("hydraKind")), object.getAttribute("hydraDocumentId"),
                            object.getAttribute("hydraMediaType"), bytes, hash, Map.of("source", "Draw.io")));
                }else if(object.hasAttribute("label")){opaque.add(object.getAttribute("label"));}
            }
            if(!recovered.isEmpty()){return new CanonicalProjectionImportResult(IntegrationSupportLevel.FULL, recovered, opaque, List.of());}
            NodeList cells = document.getElementsByTagName("mxCell");
            for(int index = 0; index < cells.getLength() && opaque.size() < 200; index++){
                Element cell = (Element) cells.item(index); if(cell.hasAttribute("value") && !cell.getAttribute("value").isBlank()){opaque.add(cell.getAttribute("value"));}
            }
            return new CanonicalProjectionImportResult(opaque.isEmpty() ? IntegrationSupportLevel.UNSUPPORTED : IntegrationSupportLevel.PARTIAL, List.of(), opaque,
                    List.of(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.WARNING, "DRAWIO_WITHOUT_HYDRA_PROVENANCE", fileName, "$", "The diagram is valid Draw.io XML but exact canonical recovery is unavailable.")));
        }catch(RuntimeException exception){
            return new CanonicalProjectionImportResult(IntegrationSupportLevel.UNSUPPORTED, List.of(), List.of(),
                    List.of(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, "DRAWIO_IMPORT_FAILED", fileName, "$", exception.getMessage())));
        }
    }

    /** Descripción: Ejecuta la operación «projection» correspondiente a la responsabilidad de DrawioCanonicalProjectionAdapter. Entradas: artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String projection(CanonicalArtifactContent artifact){
        String id = CanonicalProjectionSupport.slug(artifact.kind().name() + "-" + artifact.documentId());
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<mxfile host=\"HYDRA\" version=\"1.0\"><diagram id=\"").append(CanonicalProjectionSupport.xml(id)).append("\" name=\"")
                .append(CanonicalProjectionSupport.xml(artifact.kind() + " — " + artifact.documentId())).append("\"><mxGraphModel><root>")
                .append("<mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>");
        xml.append("<object id=\"hydra-artifact\" label=\"").append(CanonicalProjectionSupport.xml(artifact.documentId())).append("\" hydraKind=\"")
                .append(artifact.kind().name()).append("\" hydraDocumentId=\"").append(CanonicalProjectionSupport.xml(artifact.documentId()))
                .append("\" hydraMediaType=\"").append(CanonicalProjectionSupport.xml(artifact.mediaType())).append("\" hydraSha256=\"")
                .append(CanonicalProjectionSupport.sha256(artifact.content())).append("\" hydraData=\"").append(CanonicalProjectionSupport.base64(artifact.content())).append("\">")
                .append("<mxCell style=\"rounded=1;whiteSpace=wrap;html=1;\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"40\" y=\"40\" width=\"260\" height=\"80\" as=\"geometry\"/></mxCell></object>");
        appendGenericNodes(xml, artifact);
        xml.append("</root></mxGraphModel></diagram></mxfile>"); return xml.toString();
    }

    /** Descripción: Ejecuta la operación «appendGenericNodes» correspondiente a la responsabilidad de DrawioCanonicalProjectionAdapter. Entradas: xml: dato de entrada necesario para la operación; artifact: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendGenericNodes(StringBuilder xml, CanonicalArtifactContent artifact){
        try{
            JsonNode root = mapper.readTree(artifact.content()); int index = 0;
            for(String field : List.of("classes", "rules", "definitions", "bindings", "flows", "representations", "forms", "points", "levels", "achievements", "challenges", "rewards", "entries")){
                JsonNode values = root.path(field); if(!values.isArray()){continue;}
                for(JsonNode value : values){
                    if(index >= 80){return;}
                    String label = label(value, field); int column = index % 4; int row = index / 4; String cell = "n" + index++;
                    xml.append("<object id=\"").append(cell).append("\" label=\"").append(CanonicalProjectionSupport.xml(label)).append("\" hydraCollection=\"").append(field).append("\">")
                            .append("<mxCell style=\"rounded=1;whiteSpace=wrap;html=1;\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"").append(40 + column * 220)
                            .append("\" y=\"").append(160 + row * 100).append("\" width=\"180\" height=\"60\" as=\"geometry\"/></mxCell></object>")
                            .append("<mxCell id=\"e").append(index).append("\" edge=\"1\" parent=\"1\" source=\"hydra-artifact\" target=\"").append(cell)
                            .append("\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>");
                }
            }
        }catch(RuntimeException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/integration/projection/canonical/DrawioCanonicalProjectionAdapter.java",ignored);}
    }
    /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de DrawioCanonicalProjectionAdapter. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String label(JsonNode value, String fallback){for(String key : List.of("name", "label", "title", "id", "type")){String text = value.path(key).asText(); if(!text.isBlank()){return text;}} return fallback;}
}

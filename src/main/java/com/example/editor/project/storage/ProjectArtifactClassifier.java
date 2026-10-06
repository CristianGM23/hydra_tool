package com.example.editor.project.storage;

import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.FileOwnership;
import com.example.editor.project.ProjectLayout;

/** Clasifica archivos por sufijo y ubicación sin acoplar el explorador a los repositorios. */
@Component
public class ProjectArtifactClassifier {
    /** Descripción: Ejecuta la operación «classify» correspondiente a la responsabilidad de ProjectArtifactClassifier. Entradas: relativePath: ruta o ubicación utilizada por la operación; layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ArtifactKind classify(String relativePath,ProjectLayout layout) {
        String path = normalize(relativePath);
        String name = Path.of(path.isBlank() ? "unknown" : path).getFileName().toString().toLowerCase(Locale.ROOT);
        if(path.equals("modeling-project.json")) return ArtifactKind.PROJECT_DESCRIPTOR;
        if(path.startsWith(layout.internal() + "/") || path.equals(layout.internal())) return ArtifactKind.PROJECT_INTERNAL;
        if(name.endsWith(".metamodel-diagram.json")) return ArtifactKind.METAMODEL_DIAGRAM;
        if(name.endsWith(".metamodel.json") || name.endsWith(".ecore")) return ArtifactKind.METAMODEL;
        if(name.endsWith(".editor-session-diagram.json")) return ArtifactKind.EDITOR_SESSION_DIAGRAM;
        if(name.endsWith(".editor-session.json")) return ArtifactKind.EDITOR_MODEL_SESSION;
        if(name.endsWith(".editor.json")) return ArtifactKind.EDITOR_DEFINITION;
        if(name.endsWith(".representation.json")) return ArtifactKind.REPRESENTATION_DEFINITION;
        if(name.endsWith(".palette.json")) return ArtifactKind.PALETTE_DEFINITION;
        if(name.endsWith(".form.json")) return ArtifactKind.FORM_DEFINITION;
        if(name.endsWith(".notation.json")) return ArtifactKind.NOTATION_DEFINITION;
        if(name.endsWith(".expressions.json")) return ArtifactKind.EXPRESSION_MODEL;
        if(name.endsWith(".validations.json")) return ArtifactKind.VALIDATION_MODEL;
        if(name.endsWith(".actions.json") || name.endsWith(".quickfixes.json")) return ArtifactKind.ACTION_MODEL;
        if(name.endsWith(".operation-behaviors.json")) return ArtifactKind.OPERATION_BEHAVIOR_MODEL;
        if(name.endsWith(".event-architecture-diagram.json")) return ArtifactKind.EVENT_ARCHITECTURE_DIAGRAM;
        if(name.endsWith(".event-flow-diagram.json")) return ArtifactKind.EVENT_FLOW_DIAGRAM;
        if(name.endsWith(".event-flow-runtime.json")) return ArtifactKind.EVENT_FLOW_RUNTIME;
        if(name.endsWith(".gamification-diagram.json")) return ArtifactKind.GAMIFICATION_DIAGRAM;
        if(name.endsWith(".event-definitions.json")) return ArtifactKind.EVENT_DEFINITION_MODEL;
        if(name.endsWith(".event-bindings.json")) return ArtifactKind.EVENT_BINDING_MODEL;
        if(name.endsWith(".event-flows.json") || name.endsWith(".event-flow.json") || name.endsWith(".flow.json")) return ArtifactKind.EVENT_FLOW_MODEL;
        if(name.endsWith(".gamification-definitions.json") || name.endsWith(".gamification.json")) return ArtifactKind.GAMIFICATION_DEFINITION_MODEL;
        if(name.endsWith(".gamification-bindings.json")) return ArtifactKind.GAMIFICATION_BINDING_MODEL;
        if(name.endsWith(".integration.json")) return ArtifactKind.INTEGRATION_PROFILE;
        if(name.endsWith(".diagram.json")) return ArtifactKind.MODEL_DIAGRAM;
        if(name.endsWith(".xmi") || name.endsWith(".model.json")) return ArtifactKind.MODEL;
        if(name.endsWith(".drawio") || name.endsWith(".drawio.xml")) return ArtifactKind.GENERATED_DRAWIO;
        if(name.endsWith(".puml") || name.endsWith(".plantuml")) return ArtifactKind.GENERATED_PLANTUML;
        if(path.startsWith(layout.generated() + "/java/") || name.endsWith(".java")) return ArtifactKind.GENERATED_JAVA;
        if(path.startsWith(layout.generated() + "/python/") || name.endsWith(".py")) return ArtifactKind.GENERATED_PYTHON;
        if(path.startsWith(layout.generated() + "/ecore/")) return ArtifactKind.GENERATED_ECORE;
        if(name.endsWith(".png") || name.endsWith(".svg") || name.endsWith(".jpg") || name.endsWith(".jpeg")) return path.startsWith(layout.generated()+"/") ? ArtifactKind.GENERATED_IMAGE : ArtifactKind.ASSET;
        if(path.startsWith(layout.assets() + "/")) return ArtifactKind.ASSET;
        if(path.startsWith(layout.tests() + "/")) return ArtifactKind.TEST_RESOURCE;
        if(path.startsWith(layout.docs() + "/") || name.endsWith(".md")) return ArtifactKind.DOCUMENTATION;
        return ArtifactKind.GENERIC_FILE;
    }

    /** Descripción: Ejecuta la operación «ownership» correspondiente a la responsabilidad de ProjectArtifactClassifier. Entradas: relativePath: ruta o ubicación utilizada por la operación; layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public FileOwnership ownership(String relativePath,ProjectLayout layout) {
        String path = normalize(relativePath);
        if(path.startsWith(layout.generated()+"/")) return FileOwnership.GENERATED;
        if(path.startsWith(layout.internal()+"/") || path.equals("modeling-project.json")) return FileOwnership.OWNED;
        if(path.startsWith("external/")) return FileOwnership.EXTERNAL;
        return FileOwnership.USER_MANAGED;
    }

    /** Descripción: Ejecuta la operación «editable» correspondiente a la responsabilidad de ProjectArtifactClassifier. Entradas: kind: dato de entrada necesario para la operación; mediaType: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; maxSize: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean editable(ArtifactKind kind,String mediaType,long size,long maxSize) {
        if(size > maxSize) return false;
        if(kind == ArtifactKind.GENERATED_IMAGE || kind == ArtifactKind.ASSET) return mediaType.startsWith("text/") || mediaType.contains("json") || mediaType.contains("xml") || mediaType.contains("svg");
        return mediaType.startsWith("text/") || mediaType.contains("json") || mediaType.contains("xml") || mediaType.contains("javascript");
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de ProjectArtifactClassifier. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) { return value == null ? "" : value.replace('\\','/').replaceAll("^/+",""); }
}

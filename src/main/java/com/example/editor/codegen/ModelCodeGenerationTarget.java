package com.example.editor.codegen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.definition.CodeGenerationFeatureCatalog;
import com.example.editor.metamodel.importing.MetamodelImportFormat;

/** Describe un destino de generación disponible para un modelo y sus capacidades opcionales. */
public record ModelCodeGenerationTarget(String id, String displayName, String importCategory, String outputKind,
        String extension, String mediaType, String description, Set<ModelCodeGenerationFeature> supportedFeatures) {
    private static final List<ModelCodeGenerationTarget> CATALOG = java.util.Arrays.stream(MetamodelImportFormat.values()).map(ModelCodeGenerationTarget::fromImportFormat).toList();
    private static final Map<String,ModelCodeGenerationTarget> BY_ID = index(CATALOG);

    /** Descripción: Devuelve el catálogo cacheado manteniendo exactamente los identificadores de los formatos de importación de metamodelos. Entradas: ninguna. Salida: lista estable e inmutable de destinos. */
    /** Descripción: Ejecuta la operación «catalog» correspondiente a la responsabilidad de ModelCodeGenerationTarget. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static List<ModelCodeGenerationTarget> catalog() { return CATALOG; }

    /** Descripción: Obtiene un descriptor de destino sin reconstruir el catálogo. Entradas: identificador de destino. Salida: descriptor o error preciso. */
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static ModelCodeGenerationTarget require(String targetId) {
        ModelCodeGenerationTarget target = BY_ID.get(targetId);
        if(target == null) throw new IllegalArgumentException("Unsupported model code generation target: " + targetId);
        return target;
    }

    /** Descripción: Convierte un formato de importación en su proyección equivalente de generación de modelos. Entradas: formato registrado. Salida: descriptor de generación. */
    /** Descripción: Ejecuta la operación «fromImportFormat» correspondiente a la responsabilidad de ModelCodeGenerationTarget. Entradas: format: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ModelCodeGenerationTarget fromImportFormat(MetamodelImportFormat format) {
        return switch(format.id()) {
            case "ecore" -> target(format, "MODEL_SERIALIZATION", ".model.ecore.xml", "application/xml", "Portable Ecore-oriented model projection with traceability to the active metamodel.");
            case "emf" -> target(format, "MODEL_SERIALIZATION", ".model.emf", "text/plain", "Emfatic-oriented textual projection of the current model instances.");
            case "xmi" -> target(format, "MODEL_SERIALIZATION", ".xmi", "application/xml", "Portable XMI-like instance serialization preserving element and relation identifiers.");
            case "json" -> target(format, "MODEL_SERIALIZATION", ".model.json", "application/json", "Canonical JSON serialization of the current model.");
            case "xml" -> target(format, "MODEL_SERIALIZATION", ".model.xml", "application/xml", "Canonical XML serialization of the current model.");
            case "drawio" -> target(format, "DIAGRAM_PROJECTION", ".drawio", "application/xml", "draw.io object graph projection generated from the current model.");
            case "langium" -> target(format, "TEXTUAL_PROJECTION", ".langium.model", "text/plain", "Langium-oriented textual model projection independent of the metamodel import origin.");
            case "xtext" -> target(format, "TEXTUAL_PROJECTION", ".xtext.model", "text/plain", "Xtext-oriented textual model projection independent of the metamodel import origin.");
            case "antlr" -> target(format, "TEXTUAL_PROJECTION", ".g4.model", "text/plain", "ANTLR-oriented textual projection of model data and relations.");
            case "sql-ddl" -> target(format, "DATABASE_DATA_PROJECTION", ".sql", "text/sql", "Executable SQL script containing schema DDL, table constraints, foreign keys and the selected model-instance data in dependency-safe order.");
            case "java" -> target(format, "SOURCE_CODE", ".java", "text/x-java-source", "Java source that reconstructs the current model and can add tests and conventional helper methods.");
            case "typescript" -> target(format, "SOURCE_CODE", ".ts", "text/typescript", "TypeScript source containing the generated model graph with optional helpers and tests.");
            case "javascript" -> target(format, "SOURCE_CODE", ".js", "text/javascript", "JavaScript module containing the generated model graph with optional helpers and tests.");
            case "go" -> target(format, "SOURCE_CODE", ".go", "text/x-go", "Go source containing the generated model graph with optional helper methods and tests.");
            case "python" -> target(format, "SOURCE_CODE", ".py", "text/x-python", "Python source containing the generated model graph with optional helpers and tests.");
            case "blockly-json" -> target(format, "SOURCE_CODE", ".blockly.json", "application/json", "Blockly workspace-style JSON generated from model elements and relations.");
            case "blockly-javascript" -> target(format, "SOURCE_CODE", ".blockly.js", "text/javascript", "JavaScript module exporting a Blockly workspace-style model projection.");
            case "glsp" -> target(format, "PROJECT_PROJECTION", ".glsp.json", "application/json", "GLSP-oriented graph model projection for generated editors.");
            case "plantuml" -> target(format, "DOCUMENTATION", ".puml", "text/plain", "PlantUML object diagram generated from the current model.");
            case "markdown" -> target(format, "DOCUMENTATION", ".md", "text/markdown", "Readable Markdown documentation generated from the current model.");
            case "markdown-agent" -> target(format, "DOCUMENTATION", ".agent.md", "text/markdown", "Agent-oriented Markdown model context with stable identifiers and relations.");
            default -> throw new IllegalArgumentException("Unsupported model code generation target: " + format.id());
        };
    }

    /** Descripción: Construye un descriptor reutilizando nombre y categoría del registro de importación. Entradas: formato, tipo, extensión, MIME y explicación. Salida: descriptor inmutable. */
    /** Descripción: Ejecuta la operación «target» correspondiente a la responsabilidad de ModelCodeGenerationTarget. Entradas: format: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; extension: dato de entrada necesario para la operación; mediaType: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static ModelCodeGenerationTarget target(MetamodelImportFormat format, String kind, String extension,
            String mediaType, String description) {
        return new ModelCodeGenerationTarget(format.id(), format.displayName(), format.category(), kind,
                extension, mediaType, description, CodeGenerationFeatureCatalog.supportedFeatures(format.id()));
    }

    /** Descripción: Construye un índice inmutable de descriptores por identificador y rechaza duplicados. Entradas: catálogo ordenado. Salida: mapa por identificador. */
    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de ModelCodeGenerationTarget. Entradas: targets: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,ModelCodeGenerationTarget> index(List<ModelCodeGenerationTarget> targets) {
        Map<String,ModelCodeGenerationTarget> indexed = new LinkedHashMap<>();
        for(ModelCodeGenerationTarget target : targets) if(indexed.putIfAbsent(target.id(), target) != null) throw new IllegalStateException("Duplicate model code generation target: " + target.id());
        return Map.copyOf(indexed);
    }
}

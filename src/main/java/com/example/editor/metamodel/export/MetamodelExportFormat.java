package com.example.editor.metamodel.export;

import java.util.Locale;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Define todos los formatos de exportación disponibles.
 */
public enum MetamodelExportFormat {

    ECORE("ecore", ".ecore", "application/xml", "Ecore", MetamodelExportCategory.SEMANTIC, true),
    XML("xml", ".xml", "application/xml", "Neutral XML", MetamodelExportCategory.SEMANTIC, true),
    JSON("json", ".json", "application/json", "JSON", MetamodelExportCategory.SEMANTIC, true),
    LANGIUM("langium", ".langium", "text/plain", "Langium grammar", MetamodelExportCategory.GRAMMAR, true),
    XTEXT("xtext", ".xtext", "text/plain", "Xtext grammar", MetamodelExportCategory.GRAMMAR, true),
    ANTLR("antlr", ".g4", "text/plain", "ANTLR grammar", MetamodelExportCategory.GRAMMAR, true),
    SQL_DDL("sql-ddl", ".sql", "text/sql", "SQL DDL", MetamodelExportCategory.DATABASE, true),
    PLANTUML("plantuml", ".puml", "text/plain", "PlantUML", MetamodelExportCategory.DOCUMENTATION, true),
    MARKDOWN("markdown", ".md", "text/markdown", "Documentation Markdown", MetamodelExportCategory.DOCUMENTATION, true),
    MARKDOWN_AGENT("markdown-agent", ".agent.md", "text/markdown", "Agent Markdown", MetamodelExportCategory.DOCUMENTATION, true),
    DRAWIO("drawio", ".drawio", "application/xml", "draw.io", MetamodelExportCategory.DIAGRAM, true),
    SVG("svg", ".svg", "image/svg+xml", "SVG", MetamodelExportCategory.DIAGRAM, true),
    PNG("png", ".png", "image/png", "PNG", MetamodelExportCategory.DIAGRAM, true),
    JAVA("java", "-java.zip", "application/zip", "Java source", MetamodelExportCategory.CODE, true),
    TYPESCRIPT("typescript", "-typescript.zip", "application/zip", "TypeScript source", MetamodelExportCategory.CODE, true),
    JAVASCRIPT("javascript", "-javascript.zip", "application/zip", "JavaScript source", MetamodelExportCategory.CODE, true),
    GO("go", "-go.zip", "application/zip", "Go source", MetamodelExportCategory.CODE, true),
    PYTHON("python", "-python.zip", "application/zip", "Python source", MetamodelExportCategory.CODE, true),
    BLOCKLY_JSON("blockly-json", ".blockly.json", "application/json", "Blockly blocks (JSON)", MetamodelExportCategory.CODE, true),
    BLOCKLY_JAVASCRIPT("blockly-javascript", ".blockly.js", "text/javascript", "Blockly blocks (JavaScript)", MetamodelExportCategory.CODE, true),
    BUNDLE("bundle", "-exports.zip", "application/zip", "Complete bundle", MetamodelExportCategory.BUNDLE, false);

    private final String id;
    private final String extension;
    private final String mediaType;
    private final String displayName;
    private final MetamodelExportCategory category;
    private final boolean includedByDefault;

    /**
     * Descripción:
     * Crea una definición de formato.
     *
     * Entrada:
     * @param id contiene el identificador externo.
     * @param extension contiene la extensión.
     * @param mediaType contiene el tipo MIME.
     * @param displayName contiene el nombre visible.
     * @param category contiene la categoría.
     * @param includedByDefault indica si se añade al paquete completo.
     *
     * Salida:
     * Crea una instancia del enumerado.
     */
    /** Descripción: Inicializa una instancia de MetamodelExportFormat con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; extension: dato de entrada necesario para la operación; mediaType: dato de entrada necesario para la operación; displayName: dato de entrada necesario para la operación; category: dato de entrada necesario para la operación; includedByDefault: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ MetamodelExportFormat(String id, String extension, String mediaType, String displayName, MetamodelExportCategory category, boolean includedByDefault) {
        this.id = id;
        this.extension = extension;
        this.mediaType = mediaType;
        this.displayName = displayName;
        this.category = category;
        this.includedByDefault = includedByDefault;
    }

    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MetamodelExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String id() {
        return id;
    }

    /** Descripción: Ejecuta la operación «extension» correspondiente a la responsabilidad de MetamodelExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String extension() {
        return extension;
    }

    /** Descripción: Ejecuta la operación «mediaType» correspondiente a la responsabilidad de MetamodelExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String mediaType() {
        return mediaType;
    }

    /** Descripción: Ejecuta la operación «displayName» correspondiente a la responsabilidad de MetamodelExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String displayName() {
        return displayName;
    }

    /** Descripción: Ejecuta la operación «category» correspondiente a la responsabilidad de MetamodelExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportCategory category() {
        return category;
    }

    /** Descripción: Ejecuta la operación «includedByDefault» correspondiente a la responsabilidad de MetamodelExportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean includedByDefault() {
        return includedByDefault;
    }

    /** Descripción: Ejecuta la operación «fromId» correspondiente a la responsabilidad de MetamodelExportFormat. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelExportFormat fromId(String value) {
        if(value == null || value.isBlank()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Export format must not be empty");
        }

        String normalizedValue = value.toLowerCase(Locale.ROOT);

        for(MetamodelExportFormat format : values()) {
            if(format.id.equals(normalizedValue)) {
                return format;
            }
        }

        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unsupported metamodel export format: " + value);
    }
}

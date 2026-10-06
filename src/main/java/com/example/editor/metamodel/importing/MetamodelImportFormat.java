package com.example.editor.metamodel.importing;

import java.util.Arrays;
import java.util.List;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Registra los formatos de importación disponibles.
 */
public enum MetamodelImportFormat {
    ECORE("ecore", "Ecore", "SEMANTIC", MetamodelImportSupportLevel.EXACT, List.of(".ecore")),
    EMF("emf", "EMF / Emfatic", "SEMANTIC", MetamodelImportSupportLevel.SEMANTIC, List.of(".emf", ".emfatic")),
    XMI("xmi", "EPackage XMI", "SEMANTIC", MetamodelImportSupportLevel.EXACT, List.of(".xmi")),
    JSON("json", "Neutral JSON", "SEMANTIC", MetamodelImportSupportLevel.EXACT, List.of(".json", ".metamodel.json")),
    XML("xml", "Neutral XML", "SEMANTIC", MetamodelImportSupportLevel.SEMANTIC, List.of(".xml")),
    DRAWIO("drawio", "draw.io UML", "DIAGRAM", MetamodelImportSupportLevel.SEMANTIC, List.of(".drawio", ".drawio.xml")),
    LANGIUM("langium", "Langium grammar", "GRAMMAR", MetamodelImportSupportLevel.STRUCTURAL, List.of(".langium")),
    XTEXT("xtext", "Xtext grammar", "GRAMMAR", MetamodelImportSupportLevel.STRUCTURAL, List.of(".xtext")),
    ANTLR("antlr", "ANTLR 4 grammar", "GRAMMAR", MetamodelImportSupportLevel.STRUCTURAL, List.of(".g4")),
    SQL_DDL("sql-ddl", "SQL DDL", "DATABASE_SCHEMA", MetamodelImportSupportLevel.SEMANTIC, List.of(".sql", ".ddl")),
    JAVA("java", "Java source model", "SOURCE_CODE", MetamodelImportSupportLevel.SEMANTIC, List.of(".java", ".java.zip", ".jar", ".zip")),
    TYPESCRIPT("typescript", "TypeScript source model", "SOURCE_CODE", MetamodelImportSupportLevel.SEMANTIC, List.of(".ts", ".tsx", ".typescript.zip", ".zip")),
    JAVASCRIPT("javascript", "JavaScript source model", "SOURCE_CODE", MetamodelImportSupportLevel.STRUCTURAL, List.of(".js", ".mjs", ".cjs", ".javascript.zip", ".zip")),
    GO("go", "Go source model", "SOURCE_CODE", MetamodelImportSupportLevel.SEMANTIC, List.of(".go", ".go.zip", ".zip")),
    PYTHON("python", "Python source model", "SOURCE_CODE", MetamodelImportSupportLevel.SEMANTIC, List.of(".py", ".python.zip", ".zip")),
    BLOCKLY_JSON("blockly-json", "Blockly block definitions (JSON)", "SOURCE_CODE", MetamodelImportSupportLevel.STRUCTURAL, List.of(".blockly.json", ".blocks.json")),
    BLOCKLY_JAVASCRIPT("blockly-javascript", "Blockly block definitions (JavaScript)", "SOURCE_CODE", MetamodelImportSupportLevel.STRUCTURAL, List.of(".blockly.js", ".blocks.js")),
    GLSP("glsp", "Eclipse GLSP project", "PROJECT", MetamodelImportSupportLevel.STRUCTURAL, List.of(".glsp.zip", ".zip")),
    PLANTUML("plantuml", "PlantUML class diagram", "DOCUMENTATION", MetamodelImportSupportLevel.STRUCTURAL, List.of(".puml", ".plantuml")),
    MARKDOWN("markdown", "Metamodel Markdown", "DOCUMENTATION", MetamodelImportSupportLevel.STRUCTURAL, List.of(".md")),
    MARKDOWN_AGENT("markdown-agent", "Agent Markdown", "DOCUMENTATION", MetamodelImportSupportLevel.STRUCTURAL, List.of(".agent.md"));

    private final String id;
    private final String displayName;
    private final String category;
    private final MetamodelImportSupportLevel supportLevel;
    private final List<String> extensions;

    /** Descripción: Inicializa una instancia de MetamodelImportFormat con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; displayName: dato de entrada necesario para la operación; category: dato de entrada necesario para la operación; supportLevel: dato de entrada necesario para la operación; extensions: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ MetamodelImportFormat(String id, String displayName, String category, MetamodelImportSupportLevel supportLevel, List<String> extensions) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.supportLevel = supportLevel;
        this.extensions = extensions;
    }

    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MetamodelImportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String id() {
        return id;
    }

    /** Descripción: Ejecuta la operación «displayName» correspondiente a la responsabilidad de MetamodelImportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String displayName() {
        return displayName;
    }

    /** Descripción: Ejecuta la operación «category» correspondiente a la responsabilidad de MetamodelImportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String category() {
        return category;
    }

    /** Descripción: Ejecuta la operación «supportLevel» correspondiente a la responsabilidad de MetamodelImportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportSupportLevel supportLevel() {
        return supportLevel;
    }

    /** Descripción: Ejecuta la operación «extensions» correspondiente a la responsabilidad de MetamodelImportFormat. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> extensions() {
        return extensions;
    }

    /** Descripción: Ejecuta la operación «fromId» correspondiente a la responsabilidad de MetamodelImportFormat. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelImportFormat fromId(String id) {
        return Arrays.stream(values()).filter(item -> item.id.equalsIgnoreCase(id)).findFirst().orElseThrow(() -> new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown metamodel import format: " + id));
    }
}

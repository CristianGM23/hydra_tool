package com.example.editor.metamodel.importing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el registro estable de formatos.
 */
class MetamodelImportFormatTests {

    @Test
    void registersAllCurrentRoundTripFormats() {
        assertThat(MetamodelImportFormat.values()).extracting(MetamodelImportFormat::id).containsExactly("ecore", "emf", "xmi", "json", "xml", "drawio", "langium", "xtext", "antlr", "sql-ddl", "java", "typescript", "javascript", "go", "python", "blockly-json", "blockly-javascript", "glsp", "plantuml", "markdown", "markdown-agent");
    }

    @Test
    void marksGrammarFormatsAsStructural() {
        assertThat(MetamodelImportFormat.EMF.extensions()).containsExactly(".emf", ".emfatic");
        assertThat(MetamodelImportFormat.EMF.supportLevel()).isEqualTo(MetamodelImportSupportLevel.SEMANTIC);
        assertThat(MetamodelImportFormat.JAVA.supportLevel()).isEqualTo(MetamodelImportSupportLevel.SEMANTIC);
        assertThat(MetamodelImportFormat.TYPESCRIPT.supportLevel()).isEqualTo(MetamodelImportSupportLevel.SEMANTIC);
        assertThat(MetamodelImportFormat.JAVASCRIPT.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.GO.supportLevel()).isEqualTo(MetamodelImportSupportLevel.SEMANTIC);
        assertThat(MetamodelImportFormat.PYTHON.supportLevel()).isEqualTo(MetamodelImportSupportLevel.SEMANTIC);
        assertThat(MetamodelImportFormat.BLOCKLY_JSON.extensions()).containsExactly(".blockly.json", ".blocks.json");
        assertThat(MetamodelImportFormat.BLOCKLY_JSON.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.BLOCKLY_JAVASCRIPT.extensions()).containsExactly(".blockly.js", ".blocks.js");
        assertThat(MetamodelImportFormat.BLOCKLY_JAVASCRIPT.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.GLSP.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.LANGIUM.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.XTEXT.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.ANTLR.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.MARKDOWN.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
        assertThat(MetamodelImportFormat.MARKDOWN_AGENT.supportLevel()).isEqualTo(MetamodelImportSupportLevel.STRUCTURAL);
    }
}

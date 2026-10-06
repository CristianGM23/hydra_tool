package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.importing.importers.NeutralJsonMetamodelImporter;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba las dos variantes JSON admitidas.
 */
class NeutralJsonMetamodelImporterTests {

    private final NeutralJsonMetamodelImporter importer = new NeutralJsonMetamodelImporter(new ObjectMapper());

    @Test
    void importsRawNeutralModel() {
        String json = """
                {"schemaVersion":"2.1","id":"sample","name":"Sample","artifactName":"sample.ecore","namespaceUri":"urn:sample","namespacePrefix":"sample","classes":[],"enums":[],"dataTypes":[],"relations":[],"annotations":{}}
                """;
        var output = importer.importMetamodel(source(json), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().id()).isEqualTo("sample");
        assertThat(output.diagram()).isNull();
    }

    @Test
    void importsExportWrapperAndDiagram() {
        String json = """
                {"metamodel":{"schemaVersion":"2.1","id":"sample","name":"Sample","artifactName":"sample.ecore","namespaceUri":"urn:sample","namespacePrefix":"sample","classes":[],"enums":[],"dataTypes":[],"relations":[],"annotations":{}},"diagram":{"metamodelId":"sample","nodes":[],"edges":[]},"warnings":[]}
                """;
        var output = importer.importMetamodel(source(json), MetamodelImportOptions.defaults());
        assertThat(output.diagram()).isNotNull();
        assertThat(output.diagram().metamodelId()).isEqualTo("sample");
    }

    private MetamodelImportSource source(String json) {
        return new MetamodelImportSource("sample.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}

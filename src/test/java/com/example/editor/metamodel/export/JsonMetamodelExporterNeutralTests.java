package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que JSON usa exclusivamente el núcleo canónico. */
class JsonMetamodelExporterNeutralTests {

    @Test
    void exportsCanonicalModelWithoutProjectionConfiguration() {
        String result = new JsonMetamodelExporter(new ObjectMapper()).export(CanonicalExportTestFixtures.context());

        assertThat(result).contains("\"schemaVersion\" : \"2.1\"", "\"id\" : \"company\"", "\"name\" : \"Person\"");
        assertThat(result).doesNotContain("namespaceUri", "namespacePrefix", "fileName");
    }
}

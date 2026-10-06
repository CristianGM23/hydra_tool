package com.example.editor.metamodel.core;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba la publicación de los contratos versionados de operaciones. */
class OperationContractResourcesTests {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void publishesMetaModelTwoPointOneSchema() throws Exception {
        JsonNode schema = read("/schemas/metamodel/meta-model-2.1.schema.json");

        assertThat(schema.path("properties").path("schemaVersion").path("const").asString()).isEqualTo("2.1");
        assertThat(values(schema.path("$defs").path("metaClass").path("required"))).contains("operations");
        assertThat(values(schema.path("$defs").path("metaOperation").path("properties").path("behaviorKind").path("enum")))
                .containsExactly("UNSPECIFIED", "QUERY", "COMMAND");
        assertThat(values(schema.path("$defs").path("typeReference").path("properties").path("kind").path("enum"))).contains("CLASS");
    }

    @Test
    void publishesOperationBehaviorModelSchema() throws Exception {
        JsonNode schema = read("/schemas/metamodel/operation-behavior-model-1.0.schema.json");

        assertThat(schema.path("properties").path("schemaVersion").path("const").asString()).isEqualTo(OperationBehaviorModel.CURRENT_SCHEMA_VERSION);
        assertThat(values(schema.path("properties").path("bindings").path("items").path("properties").path("implementationKind").path("enum")))
                .containsExactly("UNIMPLEMENTED", "EXPRESSION", "ACTION", "EXTERNAL");
    }

    private java.util.List<String> values(JsonNode array) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for(JsonNode item : array) result.add(item.asString());
        return result;
    }

    private JsonNode read(String path) throws Exception {
        try(InputStream input = OperationContractResourcesTests.class.getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return mapper.readTree(input);
        }
    }
}

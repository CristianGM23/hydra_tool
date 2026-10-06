package com.example.editor.validation.contract;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationContractResourcesTests {

    private static final List<String> SCHEMAS = List.of(
        "canonical-model-document-1.0.schema.json",
        "expression-model-1.0.schema.json",
        "validation-model-1.0.schema.json",
        "action-model-1.0.schema.json",
        "validation-report-1.0.schema.json",
        "executable-validation-plan-1.0.schema.json"
    );

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void publishesWellFormedVersionedJsonSchemas() throws Exception {
        for(String schemaName : SCHEMAS) {
            JsonNode schema = readResource("/schemas/validation/" + schemaName);
            assertThat(schema.path("$schema").asString()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.path("$id").asString()).endsWith(schemaName);
            assertThat(schema.path("title").asString()).isNotBlank();
            assertThat(schema.path("type").asString()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            assertThat(schema.path("properties").path("schemaVersion").path("const").asString()).isEqualTo("1.0");
        }
    }

    @Test
    void centralVersionConstantsMatchPublishedSchemas() {
        assertThat(ValidationContractVersions.CANONICAL_MODEL_DOCUMENT).isEqualTo("1.0");
        assertThat(ValidationContractVersions.EXPRESSION_MODEL).isEqualTo("1.0");
        assertThat(ValidationContractVersions.VALIDATION_MODEL).isEqualTo("1.0");
        assertThat(ValidationContractVersions.ACTION_MODEL).isEqualTo("1.0");
        assertThat(ValidationContractVersions.VALIDATION_REPORT).isEqualTo("1.0");
        assertThat(ValidationContractVersions.EXECUTABLE_VALIDATION_PLAN).isEqualTo("1.0");
    }

    private JsonNode readResource(String path) throws Exception {
        try(InputStream input = ValidationContractResourcesTests.class.getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return jsonMapper.readTree(input);
        }
    }
}

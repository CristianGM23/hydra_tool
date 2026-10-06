package com.example.editor.validation.runtime;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class ValidationRuntimeResourcesTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void publishesPlanAndReportSchemas() throws Exception {
        JsonNode plan = resource("/schemas/validation/executable-validation-plan-1.0.schema.json");
        JsonNode report = resource("/schemas/validation/validation-report-1.0.schema.json");
        assertThat(plan.path("properties").path("sourceFingerprint").path("pattern").asString()).isEqualTo("^[0-9a-f]{64}$");
        assertThat(report.path("properties").path("mode").path("enum").size()).isEqualTo(8);
    }

    @Test
    void publishesPortableExamples() throws Exception {
        assertThat(resource("/examples/validation/people-executable-validation-plan.json").path("schemaVersion").asString()).isEqualTo("1.0");
        assertThat(resource("/examples/validation/people-validation-report.json").path("diagnostics").get(0).path("kind").asString()).isEqualTo("VIOLATION");
    }

    private JsonNode resource(String path) throws Exception {
        try(InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return mapper.readTree(input);
        }
    }
}

package com.example.editor.persistence.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class HydraSchemaEvolutionServiceTests {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test void appliesRegisteredStepsInOrder() throws Exception {
        HydraSchemaEvolutionService service = new HydraSchemaEvolutionService(List.of(step("1.0", "1.1"), step("1.1", "2.0")));
        JsonNode source = mapper.readTree("{\"schemaVersion\":\"1.0\",\"id\":\"x\"}");
        var result = service.evolve("test.document", source);
        assertThat(result.changed()).isTrue();
        assertThat(result.document().get("schemaVersion").asString()).isEqualTo("2.0");
        assertThat(result.appliedSteps()).containsExactly("1.0->1.1", "1.1->2.0");
    }

    @Test void rejectsAmbiguousStartingVersions() {
        assertThatThrownBy(() -> new HydraSchemaEvolutionService(List.of(step("1.0", "1.1"), step("1.0", "2.0"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Ambiguous");
    }

    private HydraJsonSchemaEvolution step(String from, String to) {
        return new HydraJsonSchemaEvolution() {
            public String namespace() { return "test.document"; }
            public String fromVersion() { return from; }
            public String toVersion() { return to; }
            public JsonNode evolve(JsonNode source) { ((ObjectNode) source).put("schemaVersion", to); return source; }
        };
    }
}

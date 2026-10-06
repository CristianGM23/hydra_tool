package com.example.editor.persistence.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class HydraSchemaEvolutionPlanningTests {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test void plansAndExecutesOnlyUntilTheRequestedTarget() throws Exception {
        HydraSchemaEvolutionService service = new HydraSchemaEvolutionService(List.of(step("1.0", "1.1"), step("1.1", "2.0"), step("2.0", "3.0")));
        HydraSchemaEvolutionPlan plan = service.plan("test.document", "1.0", "2.0");
        assertThat(plan.reachable()).isTrue();
        assertThat(plan.steps()).containsExactly("1.0->1.1", "1.1->2.0");
        var result = service.evolveTo("test.document", mapper.readTree("{\"schemaVersion\":\"1.0\",\"id\":\"x\"}"), "2.0");
        assertThat(result.document().get("schemaVersion").asString()).isEqualTo("2.0");
        assertThat(result.appliedSteps()).containsExactlyElementsOf(plan.steps());
    }

    @Test void reportsAnUnreachableTargetWithoutMutatingAnything() {
        HydraSchemaEvolutionService service = new HydraSchemaEvolutionService(List.of(step("1.0", "1.1")));
        HydraSchemaEvolutionPlan plan = service.plan("test.document", "1.0", "2.0");
        assertThat(plan.reachable()).isFalse();
        assertThat(plan.diagnostics()).singleElement().asString().contains("1.1");
    }

    @Test void rejectsCyclesAtStartup() {
        assertThatThrownBy(() -> new HydraSchemaEvolutionService(List.of(step("1.0", "1.1"), step("1.1", "1.0"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cyclic schema evolution");
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

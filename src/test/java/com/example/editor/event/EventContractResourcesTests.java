package com.example.editor.event;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
class EventContractResourcesTests {
    @Test void publishesCanonicalEventSchema() { assertThat(resource("/schemas/event/canonical-event-1.0.schema.json")).contains("CanonicalEvent 1.0", "correlationId", "causationId"); }
    @Test void publishesDefinitionAndBindingSchemas() { assertThat(resource("/schemas/event/event-definition-model-1.0.schema.json")).contains("EventDefinitionModel 1.0", "eventTypes"); assertThat(resource("/schemas/event/event-binding-model-1.0.schema.json")).contains("EventBindingModel 1.0", "bindings"); }
    @Test void publishesCuratedArchitectureDiagramSchema() { assertThat(resource("/schemas/event/event-architecture-diagram-1.1.schema.json")).contains("EventArchitectureDiagram 1.1", "explicitly placed events"); }
    @Test void publishesPortableExample() { assertThat(resource("/examples/event/people-events.json")).contains("core.validation.violation-resolved", "course.people.problem-resolved"); }
    private String resource(String path) { try(var stream = getClass().getResourceAsStream(path)) { if(stream == null) throw new AssertionError("Missing resource " + path); return new String(stream.readAllBytes(), StandardCharsets.UTF_8); } catch(Exception exception) { throw new AssertionError(exception); } }
}

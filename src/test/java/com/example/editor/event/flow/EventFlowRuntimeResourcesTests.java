package com.example.editor.event.flow;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
class EventFlowRuntimeResourcesTests {
    @Test void publishesRuntimeSnapshotSchema() throws IOException { assertThat(resource("/schemas/event/event-flow-runtime-snapshot-1.0.schema.json")).contains("EventFlowRuntimeSnapshot 1.0", "retryContinuations", "waitingContinuations"); }
    @Test void publishesRuntimeSnapshotExample() throws IOException { assertThat(resource("/examples/event/people-event-flow-runtime.json")).contains("runtimeState", "retryContinuations", "people"); }
    @Test void exampleConnectsEventFlowWithGamification() throws IOException {
        assertThat(resource("/examples/event/people-event-flow.json")).contains("course.people.quality-evidence");
        assertThat(resource("/examples/gamification/people-gamification.json")).contains("course.people.quality-evidence", "PENALTY");
    }
    private String resource(String path) throws IOException { try(var stream=getClass().getResourceAsStream(path)){ assertThat(stream).as(path).isNotNull(); return new String(stream.readAllBytes(),StandardCharsets.UTF_8); } }
}

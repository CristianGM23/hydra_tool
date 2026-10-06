package com.example.editor.event;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
class EventModelCheckerTests {
    private final EventModelChecker checker = new EventModelChecker();
    @Test void acceptsReservedSourceAndCompleteTargetMapping() { assertThat(checker.check(definitions(), bindings(), null).diagnostics()).isEmpty(); }
    @Test void rejectsRedefinitionOfReservedEvents() { var reserved = type("core.model.saved", List.of()); var report = checker.check(new EventDefinitionModel("1.0", "events", "people", "course.people", List.of(reserved), Map.of()), null, null); assertThat(report.diagnostics()).extracting(EventDiagnostic::code).contains("RESERVED_EVENT_TYPE"); }
    @Test void rejectsDuplicateFields() { var field = field(); var report = checker.check(new EventDefinitionModel("1.0", "events", "people", "course.people", List.of(type("course.people.problem-resolved", List.of(field, field))), Map.of()), null, null); assertThat(report.diagnostics()).extracting(EventDiagnostic::code).contains("DUPLICATE_FIELD_ID", "DUPLICATE_FIELD_NAME"); }
    @Test void rejectsUnknownSourceType() { EventBinding binding = new EventBinding("binding:1", "Binding", "unknown.event", "course.people.problem-resolved", "", List.of(mapping()), true, EventBindingErrorPolicy.REPORT, Map.of()); var report = checker.check(definitions(), bindingModel(List.of(binding)), null); assertThat(report.diagnostics()).extracting(EventDiagnostic::code).contains("UNKNOWN_SOURCE_EVENT"); }
    @Test void rejectsUnknownTargetType() { EventBinding binding = new EventBinding("binding:1", "Binding", "core.model.saved", "course.people.unknown", "", List.of(mapping()), true, EventBindingErrorPolicy.REPORT, Map.of()); var report = checker.check(definitions(), bindingModel(List.of(binding)), null); assertThat(report.diagnostics()).extracting(EventDiagnostic::code).contains("UNKNOWN_TARGET_EVENT"); }
    @Test void requiresMappingsForMandatoryFields() { EventBinding binding = new EventBinding("binding:1", "Binding", "core.model.saved", "course.people.problem-resolved", "", List.of(), true, EventBindingErrorPolicy.REPORT, Map.of()); var report = checker.check(definitions(), bindingModel(List.of(binding)), null); assertThat(report.diagnostics()).extracting(EventDiagnostic::code).contains("REQUIRED_FIELD_NOT_MAPPED"); }
    @Test void rejectsMappingsToUnknownFields() { EventBinding binding = new EventBinding("binding:1", "Binding", "core.model.saved", "course.people.problem-resolved", "", List.of(new EventPayloadMapping("field:unknown", "payload.x", "", null), mapping()), true, EventBindingErrorPolicy.REPORT, Map.of()); var report = checker.check(definitions(), bindingModel(List.of(binding)), null); assertThat(report.diagnostics()).extracting(EventDiagnostic::code).contains("UNKNOWN_TARGET_FIELD"); }
    private EventDefinitionModel definitions() { return new EventDefinitionModel("1.0", "events", "people", "course.people", List.of(type("course.people.problem-resolved", List.of(field()))), Map.of()); }
    private EventBindingModel bindings() { return bindingModel(List.of(new EventBinding("binding:1", "Binding", "core.validation.violation-resolved", "course.people.problem-resolved", "", List.of(mapping()), true, EventBindingErrorPolicy.REPORT, Map.of()))); }
    private EventBindingModel bindingModel(List<EventBinding> values) { return new EventBindingModel("1.0", "event-bindings", "people", "events", "", values, Map.of()); }
    private EventTypeDefinition type(String id, List<EventFieldDefinition> fields) { return new EventTypeDefinition(id, "ProblemResolved", "course.people", "", "VALIDATION", "1.0", fields, Set.of(), Set.of(EventSubjectKind.MODEL_ELEMENT), EventPersistencePolicy.GAMIFICATION, false, false, true, Map.of()); }
    private EventFieldDefinition field() { return new EventFieldDefinition("field:ruleId", "ruleId", EventFieldType.ELEMENT_ID, true, false, "", "", ""); }
    private EventPayloadMapping mapping() { return new EventPayloadMapping("field:ruleId", "payload.ruleId", "", null); }
}

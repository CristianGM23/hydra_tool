package com.example.editor.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditComprehensiveCaptureTests {

    @Test
    void genericHttpClassifierCoversCurrentAndFutureFunctionalOperations() {
        GenericAuditHttpActivityClassifier classifier = new GenericAuditHttpActivityClassifier();
        assertThat(classifier.classify("POST", "/api/validations/model-1/run").orElseThrow().action()).isEqualTo(AuditActions.VALIDATE);
        assertThat(classifier.classify("POST", "/api/quick-fixes/model-1/apply").orElseThrow().action()).isEqualTo(AuditActions.APPLY);
        assertThat(classifier.classify("GET", "/api/project-export/model-1").orElseThrow().action()).isEqualTo(AuditActions.EXPORT);
        assertThat(classifier.classify("POST", "/api/operations/op-1/execute").orElseThrow().action()).isEqualTo(AuditActions.EXECUTE);
        assertThat(classifier.classify("PATCH", "/api/future-module/resource-1").orElseThrow().moduleId()).isEqualTo("future-module");
    }

    @Test
    void clientActivityChannelIsBoundedDeduplicatedAndBestEffort() {
        AuditLogProperties properties = new AuditLogProperties();
        properties.setClientDedupeEntries(4);
        properties.setClientDedupeWindowMillis(10_000L);
        List<AuditEvent> events = new ArrayList<>();
        AuditEventPublisher publisher = events::add;
        AuditActorContext actor = () -> new AuditActor("local-user", "Local user", "", Map.of());
        AuditClientActivityService service = new AuditClientActivityService(publisher, actor, properties);
        AuditClientActivityRequest request = new AuditClientActivityRequest("navigation.module.changed", "navigation", "NAVIGATE", "INFO",
                "module", "EVENTS", "Events", Map.of("moduleId", "VALIDATION"), Map.of("moduleId", "EVENTS"), Map.of(), "VALIDATION->EVENTS");
        assertThat(service.publish(request)).isTrue();
        assertThat(service.publish(request)).isFalse();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).before()).isEqualTo(Map.of("moduleId", "VALIDATION"));
        assertThat(events.get(0).after()).isEqualTo(Map.of("moduleId", "EVENTS"));
    }

    @Test
    void requestContextCorrelatesHighLevelActionWithCanonicalMutations() {
        assertThat(AuditRequestContext.correlationId()).isEmpty();
        try(AuditRequestContext.Scope ignored = AuditRequestContext.begin()) {
            assertThat(AuditRequestContext.correlationId()).isNotBlank();
            assertThat(AuditRequestContext.businessMutationObserved()).isFalse();
            AuditRequestContext.markBusinessMutation();
            assertThat(AuditRequestContext.businessMutationObserved()).isTrue();
        }
        assertThat(AuditRequestContext.correlationId()).isEmpty();
    }

    @Test
    void diagramDocumentsAreNoLongerSilentlyExcluded() {
        AuditLogProperties properties = new AuditLogProperties();
        AuditCapturePolicy policy = new AuditCapturePolicy(properties);
        assertThat(policy.capturesNamespace("event.diagram")).isTrue();
        assertThat(policy.capturesNamespace("event.flow.diagram")).isTrue();
        assertThat(policy.capturesNamespace("gamification.diagram")).isTrue();
        assertThat(policy.capturesNamespace("event.flow.runtime")).isFalse();
    }
    @Test
    void grammarStyleAndContextMutationsHaveExplicitBilingualDescriptors() {
        CoreAuditActivityDescriptorProvider provider = new CoreAuditActivityDescriptorProvider();
        Map<String, AuditActivityDescriptor> descriptors = provider.descriptors().stream()
                .collect(java.util.stream.Collectors.toMap(AuditActivityDescriptor::activityType, value -> value));
        assertThat(descriptors).containsKeys("grammar.definition.created", "grammar.definition.updated", "grammar.definition.deleted",
                "textual.syntax.style.created", "textual.syntax.style.updated", "textual.syntax.style.deleted",
                "textual.editor.definition.created", "textual.editor.definition.updated", "textual.editor.definition.deleted");
        assertThat(descriptors.get("grammar.definition.updated").titleSpanish()).contains("gramática");
        assertThat(descriptors.get("textual.syntax.style.updated").titleSpanish()).contains("sintaxis");
        assertThat(descriptors.get("textual.editor.definition.updated").titleSpanish()).contains("contexto");
    }

}

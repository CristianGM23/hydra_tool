package com.example.editor.event.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventBinding;
import com.example.editor.event.EventBindingErrorPolicy;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventModelLoadResult;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventPayloadMapping;
import com.example.editor.event.EventSource;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;

class EventBindingRuntimeServiceTests {
    @Test
    void executesBindingOnGrammarSemanticScopeAndPublishesDerivedEventInFifoOrder() {
        var bus = new InMemoryCanonicalEventBus();
        EventModelApplicationService eventModels = Mockito.mock(EventModelApplicationService.class);
        ExpressionModelRepository expressions = Mockito.mock(ExpressionModelRepository.class);
        CanonicalMetamodelApplicationService metamodels = Mockito.mock(CanonicalMetamodelApplicationService.class);
        ExpressionEvaluator evaluator = Mockito.mock(ExpressionEvaluator.class);
        String scope = "grammar.main-language";
        var definitions = new EventDefinitionModel(null, "event-definitions", scope, "demo", List.of(), Map.of());
        var binding = new EventBinding("save-binding", "Save binding", "source.saved", "domain.saved", "",
                List.of(new EventPayloadMapping("origin", "", "", "binding")), true, EventBindingErrorPolicy.REPORT, Map.of());
        var bindings = new EventBindingModel(null, "event-bindings", scope, definitions.id(), "", List.of(binding), Map.of());
        when(eventModels.find(scope)).thenReturn(new EventModelLoadResult(definitions, "d", bindings, "b", null));
        when(metamodels.findById(scope)).thenReturn(new MetamodelLoadResult(EditorDefinitionTestFixtures.metamodel(), "m"));
        var runtime = new EventBindingRuntimeService(bus, eventModels, expressions, metamodels, evaluator);
        var received = new ArrayList<CanonicalEvent>();
        bus.subscribe(value -> true, received::add);

        bus.publish(event(scope, "event:source", "source.saved"));

        assertThat(received).extracting(CanonicalEvent::eventTypeId).containsExactly("source.saved", "domain.saved");
        assertThat(received.get(1).payload()).containsEntry("origin", "binding");
        assertThat(received.get(1).causationId()).isEqualTo("event:source");
        runtime.close();
    }

    @Test
    void anEventAtTheMaximumBindingDepthIsIgnoredWithoutEvenLoadingTheEventModel() {
        var bus = new InMemoryCanonicalEventBus();
        EventModelApplicationService eventModels = Mockito.mock(EventModelApplicationService.class);
        ExpressionModelRepository expressions = Mockito.mock(ExpressionModelRepository.class);
        CanonicalMetamodelApplicationService metamodels = Mockito.mock(CanonicalMetamodelApplicationService.class);
        ExpressionEvaluator evaluator = Mockito.mock(ExpressionEvaluator.class);
        var runtime = new EventBindingRuntimeService(bus, eventModels, expressions, metamodels, evaluator);
        var received = new ArrayList<CanonicalEvent>();
        bus.subscribe(value -> true, received::add);

        CanonicalEvent atDepthLimit = new CanonicalEvent(null, "event:deep", "source.saved", "1.0", Instant.now(),
                new EventSource("TEST", "", "", ""), new EventContext(EventContextKind.SYSTEM, "", "", "grammar.main-language", "", "", ""),
                null, null, "", "", "", Map.of(), Map.of("hydra.binding.depth", "32"));
        bus.publish(atDepthLimit);

        assertThat(received).extracting(CanonicalEvent::eventTypeId).containsExactly("source.saved");
        Mockito.verifyNoInteractions(eventModels);
        runtime.close();
    }

    @Test
    void aBindingConfiguredToDisableOnFailureStopsRespondingToFurtherEventsAfterARealFailure() {
        var bus = new InMemoryCanonicalEventBus();
        EventModelApplicationService eventModels = Mockito.mock(EventModelApplicationService.class);
        ExpressionModelRepository expressions = Mockito.mock(ExpressionModelRepository.class);
        CanonicalMetamodelApplicationService metamodels = Mockito.mock(CanonicalMetamodelApplicationService.class);
        ExpressionEvaluator evaluator = Mockito.mock(ExpressionEvaluator.class);
        String scope = "grammar.disable-on-failure";
        var definitions = new EventDefinitionModel(null, "event-definitions", scope, "demo", List.of(), Map.of());
        var binding = new EventBinding("fragile-binding", "Fragile binding", "source.saved", "domain.saved", "condition:missing",
                List.of(), true, EventBindingErrorPolicy.DISABLE_BINDING, Map.of());
        var bindings = new EventBindingModel(null, "event-bindings", scope, definitions.id(), "", List.of(binding), Map.of());
        when(eventModels.find(scope)).thenReturn(new EventModelLoadResult(definitions, "d", bindings, "b", null));
        when(metamodels.findById(scope)).thenReturn(new MetamodelLoadResult(EditorDefinitionTestFixtures.metamodel(), "m"));
        var runtime = new EventBindingRuntimeService(bus, eventModels, expressions, metamodels, evaluator);
        var received = new ArrayList<CanonicalEvent>();
        bus.subscribe(value -> true, received::add);

        bus.publish(event(scope, "event:first", "source.saved"));
        bus.publish(event(scope, "event:second", "source.saved"));

        assertThat(received).extracting(CanonicalEvent::eventTypeId).containsExactly("source.saved", "source.saved");
        runtime.close();
    }

    private CanonicalEvent event(String scope, String id, String type) {
        return new CanonicalEvent(null, id, type, "1.0", Instant.now(), new EventSource("TEST", "", "", ""),
                new EventContext(EventContextKind.SYSTEM, "", "", scope, "", "", ""), null, null,
                "", "", "", Map.of("origin", "source"), Map.of());
    }
}

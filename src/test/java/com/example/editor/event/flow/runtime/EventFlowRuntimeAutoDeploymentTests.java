package com.example.editor.event.flow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventSource;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowLoadResult;
import com.example.editor.event.flow.EventFlowCheckReport;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.EventFlowProperties;

class EventFlowRuntimeAutoDeploymentTests {
    @Test
    void firstGrammarEventDeploysFlowAndExplicitStopBlocksAutomaticRestart() {
        String scope = "grammar.main-language";
        EventFlowApplicationService flows = Mockito.mock(EventFlowApplicationService.class);
        EventModelApplicationService events = Mockito.mock(EventModelApplicationService.class);
        EventFlowCompiler compiler = Mockito.mock(EventFlowCompiler.class);
        var bus = new InMemoryCanonicalEventBus();
        var model = new EventFlowModel(null, "flow-model", scope, "", "", List.of(), Map.of());
        var plan = new ExecutableEventFlowPlan(null, "flow-plan", scope, model.id(), Instant.now(), List.of(), Map.of());
        when(flows.find(scope)).thenReturn(new EventFlowLoadResult(model, "f", new EventFlowCheckReport(model.id(), List.of())));
        when(events.find(scope)).thenThrow(new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND, "none"));
        when(compiler.compile(eq(model), any())).thenReturn(plan);
        var runtime = new EventFlowRuntimeService(flows, events, compiler, bus, List.of(), new EventFlowProperties());

        bus.publish(event(scope, "event:1"));
        assertThat(runtime.status(scope).active()).isTrue();
        assertThat(runtime.status(scope).processedEvents()).isEqualTo(1);

        runtime.stop(scope);
        bus.publish(event(scope, "event:2"));
        assertThat(runtime.status(scope).active()).isFalse();
        verify(compiler, times(1)).compile(eq(model), any());

        runtime.start(scope);
        bus.publish(event(scope, "event:3"));
        assertThat(runtime.status(scope).active()).isTrue();
        verify(compiler, times(2)).compile(eq(model), any());
        runtime.close();
    }

    private CanonicalEvent event(String scope, String id) {
        return new CanonicalEvent(null, id, "source.saved", "1.0", Instant.now(), new EventSource("TEST", "", "", ""),
                new EventContext(EventContextKind.SYSTEM, "", "", scope, "", "", ""), null, null,
                "", "", "", Map.of(), Map.of());
    }
}

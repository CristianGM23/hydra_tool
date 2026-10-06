package com.example.editor.event.flow.runtime;

import java.util.List;
import com.example.editor.event.CanonicalEvent;

public record EventFlowProcessResult(String metamodelId, String sourceEventId, List<CanonicalEvent> emittedEvents,
        List<FlowExecutionTrace> traces) {
    public EventFlowProcessResult {
        emittedEvents = emittedEvents == null ? List.of() : List.copyOf(emittedEvents);
        traces = traces == null ? List.of() : List.copyOf(traces);
    }
}

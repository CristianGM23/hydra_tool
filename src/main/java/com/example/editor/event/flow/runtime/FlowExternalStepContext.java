package com.example.editor.event.flow.runtime;

import java.util.Map;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;

public record FlowExternalStepContext(String metamodelId, FlowDefinition flow, FlowNode node, CanonicalEvent event,
        Map<String, Object> variables) { }

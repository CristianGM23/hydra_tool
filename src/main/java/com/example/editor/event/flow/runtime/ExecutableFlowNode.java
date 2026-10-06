package com.example.editor.event.flow.runtime;

import java.util.List;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowTransition;

/** Nodo compilado con sus transiciones ordenadas. */
public record ExecutableFlowNode(FlowNode node, List<FlowTransition> outgoing) {
    public ExecutableFlowNode { outgoing = outgoing == null ? List.of() : List.copyOf(outgoing); }
}

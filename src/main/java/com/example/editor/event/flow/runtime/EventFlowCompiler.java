package com.example.editor.event.flow.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.flow.EventFlowApplicationException;
import com.example.editor.event.flow.EventFlowCheckReport;
import com.example.editor.event.flow.EventFlowChecker;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowNodeKind;

/** Compila el contrato fuente en índices inmutables para el runtime. */
@Component
public class EventFlowCompiler {
    private final EventFlowChecker checker;
    /** Descripción: Inicializa una instancia de EventFlowCompiler con sus dependencias y datos de entrada. Entradas: checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowCompiler(EventFlowChecker checker) { this.checker = checker; }
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de EventFlowCompiler. Entradas: model: dato de entrada necesario para la operación; definitions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExecutableEventFlowPlan compile(EventFlowModel model, EventDefinitionModel definitions) {
        EventFlowCheckReport report = checker.check(model, definitions);
        if(!report.valid()) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.NOT_EXECUTABLE, "EventFlowModel contains blocking diagnostics");
        List<ExecutableFlowDefinition> flows = new ArrayList<>();
        Map<String, List<String>> triggerIndex = new LinkedHashMap<>();
        for(FlowDefinition flow : model.flows()) {
            Map<String, ExecutableFlowNode> nodes = new LinkedHashMap<>();
            for(FlowNode node : flow.nodes()) {
                List<com.example.editor.event.flow.FlowTransition> outgoing = flow.transitions().stream()
                        .filter(item -> item.sourceNodeId().equals(node.id())).sorted(Comparator.comparingInt(com.example.editor.event.flow.FlowTransition::order)).toList();
                nodes.put(node.id(), new ExecutableFlowNode(node, outgoing));
            }
            String start = flow.nodes().stream().filter(node -> node.kind() == FlowNodeKind.START || node.kind() == FlowNodeKind.SOURCE)
                    .map(FlowNode::id).findFirst().orElseThrow();
            flows.add(new ExecutableFlowDefinition(flow, start, nodes, flow.compensations()));
            for(String eventType : flow.triggerEventTypeIds()) triggerIndex.computeIfAbsent(eventType, ignored -> new ArrayList<>()).add(flow.id());
            FlowNode startNode = nodes.get(start).node();
            if(startNode.kind() == FlowNodeKind.SOURCE && !startNode.eventTypeId().isBlank()) triggerIndex.computeIfAbsent(startNode.eventTypeId(), ignored -> new ArrayList<>()).add(flow.id());
        }
        Map<String, List<String>> immutableIndex = new LinkedHashMap<>();
        triggerIndex.forEach((key, value) -> immutableIndex.put(key, List.copyOf(new java.util.LinkedHashSet<>(value))));
        return new ExecutableEventFlowPlan("1.0", model.id() + ".plan", model.metamodelId(), model.id(), Instant.now(), flows, immutableIndex);
    }
}

package com.example.editor.event.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.ReservedEventCatalog;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.validation.expression.ExpressionModel;

/** Comprueba referencias, topología y reglas visuales de EventFlowModel. */
@Component
public class EventFlowChecker {
    private final ExpressionTypeChecker expressionTypeChecker;

    /** Constructor de compatibilidad para pruebas unitarias aisladas. */
    /** Descripción: Inicializa el checker con dependencias neutrales por defecto para pruebas aisladas. Entradas: no requiere parámetros. Salida: deja el checker listo para validar. */ public EventFlowChecker() { this(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())); }

    @Autowired
    /** Descripción: Inicializa EventFlowChecker con el checker neutral compartido. Entradas: expressionTypeChecker. Salida: deja la instancia lista para validar condiciones. */ public EventFlowChecker(ExpressionTypeChecker expressionTypeChecker) {
        this.expressionTypeChecker = expressionTypeChecker;
    }
    /** Valida topología sin catálogo de expresiones; se conserva para compatibilidad de consumidores embebidos. */
    /** Descripción: Valida topología EventFlow sin catálogo de expresiones. Entradas: model y definitions. Salida: devuelve un informe de comprobación. */ public EventFlowCheckReport check(EventFlowModel model, EventDefinitionModel definitions) {
        return checkInternal(model, definitions, null, null, false);
    }

    /** Valida topología y condiciones canónicas cuando se facilita ExpressionModel/metamodelo. */
    /** Descripción: Valida topología y condiciones neutrales EventFlow. Entradas: model, definitions, expressions y metamodel. Salida: devuelve un informe de comprobación. */ public EventFlowCheckReport check(EventFlowModel model, EventDefinitionModel definitions, ExpressionModel expressions, MetaModel metamodel) {
        return checkInternal(model, definitions, expressions, metamodel, true);
    }

    /** Descripción: Ejecuta la validación interna EventFlow con control explícito del chequeo de expresiones. Entradas: documentos y validateExpressions. Salida: devuelve diagnósticos agregados. */ private EventFlowCheckReport checkInternal(EventFlowModel model, EventDefinitionModel definitions, ExpressionModel expressions, MetaModel metamodel, boolean validateExpressions) {
        List<FlowDiagnostic> diagnostics = new ArrayList<>();
        if(model == null) return new EventFlowCheckReport("", List.of(error("FLOW_MODEL_REQUIRED", "", "$", "EventFlowModel must not be null")));
        if(validateExpressions && !model.expressionModelId().isBlank()) {
            if(expressions == null) diagnostics.add(error("EXPRESSION_MODEL_NOT_FOUND", model.id(), "$.expressionModelId", "Configured expression model was not found"));
            else if(!model.expressionModelId().equals(expressions.id())) diagnostics.add(error("EXPRESSION_MODEL_MISMATCH", model.id(), "$.expressionModelId", "Configured expression model id does not match the loaded expression model"));
        }
        Set<String> eventTypes = new LinkedHashSet<>(ReservedEventCatalog.eventTypeIds());
        if(definitions != null) definitions.eventTypes().forEach(type -> eventTypes.add(type.id()));
        Set<String> flowIds = new HashSet<>();
        for(int index = 0; index < model.flows().size(); index++) {
            FlowDefinition flow = model.flows().get(index);
            String path = "$.flows[" + index + "]";
            if(!flowIds.add(flow.id())) diagnostics.add(error("DUPLICATE_FLOW_ID", flow.id(), path + ".id", "Flow id is duplicated"));
            checkFlow(flow, path, eventTypes, expressions, metamodel, validateExpressions, diagnostics);
        }
        return new EventFlowCheckReport(model.id(), diagnostics);
    }

    /** Descripción: Valida las condiciones necesarias para «checkFlow» y rechaza entradas no válidas. Entradas: flow: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; eventTypes: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkFlow(FlowDefinition flow, String path, Set<String> eventTypes, ExpressionModel expressions, MetaModel metamodel, boolean validateExpressions, List<FlowDiagnostic> diagnostics) {
        Set<String> nodeIds = new LinkedHashSet<>();
        Map<String, FlowNode> nodes = new HashMap<>();
        int startCount = 0;
        int endCount = 0;
        for(int index = 0; index < flow.nodes().size(); index++) {
            FlowNode node = flow.nodes().get(index);
            String nodePath = path + ".nodes[" + index + "]";
            if(!nodeIds.add(node.id())) diagnostics.add(error("DUPLICATE_NODE_ID", node.id(), nodePath + ".id", "Node id is duplicated"));
            nodes.put(node.id(), node);
            if(node.kind() == FlowNodeKind.START || node.kind() == FlowNodeKind.SOURCE) startCount++;
            if(node.kind() == FlowNodeKind.SUCCESS_END || node.kind() == FlowNodeKind.ERROR_END) endCount++;
            if(requiresEventType(node.kind()) && node.eventTypeId().isBlank()) diagnostics.add(error("EVENT_TYPE_REQUIRED", node.id(), nodePath + ".eventTypeId", "This node requires an event type"));
            if(!node.eventTypeId().isBlank() && !eventTypes.contains(node.eventTypeId())) diagnostics.add(error("UNKNOWN_EVENT_TYPE", node.id(), nodePath + ".eventTypeId", "Unknown event type: " + node.eventTypeId()));
            if(node.kind() == FlowNodeKind.COMPENSATION_STEP && !node.compensable()) diagnostics.add(info("COMPENSATION_NODE", node.id(), nodePath, "Compensation steps execute as compensating work"));
            if(validateExpressions) checkCondition(node.conditionExpressionId(), node.id(), nodePath + ".conditionExpressionId", expressions, metamodel, diagnostics);
        }
        if(startCount != 1) diagnostics.add(error("FLOW_START_COUNT", flow.id(), path + ".nodes", "A flow must contain exactly one START or SOURCE node"));
        if(endCount == 0) diagnostics.add(error("FLOW_END_REQUIRED", flow.id(), path + ".nodes", "A flow must contain at least one success or error end"));
        Set<String> transitionIds = new HashSet<>();
        Map<String, List<FlowTransition>> outgoing = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        for(int index = 0; index < flow.transitions().size(); index++) {
            FlowTransition transition = flow.transitions().get(index);
            String transitionPath = path + ".transitions[" + index + "]";
            if(!transitionIds.add(transition.id())) diagnostics.add(error("DUPLICATE_TRANSITION_ID", transition.id(), transitionPath + ".id", "Transition id is duplicated"));
            if(!nodes.containsKey(transition.sourceNodeId())) diagnostics.add(error("UNKNOWN_TRANSITION_SOURCE", transition.id(), transitionPath + ".sourceNodeId", "Transition source does not exist"));
            if(!nodes.containsKey(transition.targetNodeId())) diagnostics.add(error("UNKNOWN_TRANSITION_TARGET", transition.id(), transitionPath + ".targetNodeId", "Transition target does not exist"));
            outgoing.computeIfAbsent(transition.sourceNodeId(), ignored -> new ArrayList<>()).add(transition);
            incoming.merge(transition.targetNodeId(), 1, Integer::sum);
            if(validateExpressions) checkCondition(transition.conditionExpressionId(), transition.id(), transitionPath + ".conditionExpressionId", expressions, metamodel, diagnostics);
        }
        for(FlowNode node : flow.nodes()) {
            if(node.kind() != FlowNodeKind.START && node.kind() != FlowNodeKind.SOURCE && incoming.getOrDefault(node.id(), 0) == 0) diagnostics.add(error("ORPHAN_NODE", node.id(), path + ".nodes", "Node has no incoming transition"));
            List<FlowTransition> nodeOutgoing = outgoing.getOrDefault(node.id(), List.of());
            if(!isTerminal(node.kind()) && nodeOutgoing.isEmpty()) diagnostics.add(error("DEAD_END_NODE", node.id(), path + ".nodes", "Non-terminal node has no outgoing transition"));
            if(node.kind() == FlowNodeKind.DECISION || node.kind() == FlowNodeKind.ROUTE) {
                boolean trueBranch = nodeOutgoing.stream().anyMatch(item -> item.kind() == FlowTransitionKind.TRUE);
                boolean falseBranch = nodeOutgoing.stream().anyMatch(item -> item.kind() == FlowTransitionKind.FALSE);
                if(!trueBranch || !falseBranch) diagnostics.add(error("DECISION_BRANCHES", node.id(), path + ".transitions", "Decision and route nodes require TRUE and FALSE branches"));
            }
            if(node.kind() == FlowNodeKind.JOIN && incoming.getOrDefault(node.id(), 0) < 2) diagnostics.add(error("JOIN_INPUT_COUNT", node.id(), path + ".transitions", "Join requires at least two incoming transitions"));
        }
        for(int index = 0; index < flow.compensations().size(); index++) {
            CompensationLink link = flow.compensations().get(index);
            String compensationPath = path + ".compensations[" + index + "]";
            FlowNode source = nodes.get(link.sourceNodeId());
            FlowNode target = nodes.get(link.compensationNodeId());
            if(source == null) diagnostics.add(error("UNKNOWN_COMPENSATION_SOURCE", link.id(), compensationPath + ".sourceNodeId", "Compensation source does not exist"));
            else if(!source.compensable()) diagnostics.add(error("SOURCE_NOT_COMPENSABLE", link.id(), compensationPath + ".sourceNodeId", "Compensation source is not marked compensable"));
            if(target == null) diagnostics.add(error("UNKNOWN_COMPENSATION_TARGET", link.id(), compensationPath + ".compensationNodeId", "Compensation target does not exist"));
            else if(target.kind() != FlowNodeKind.COMPENSATION_STEP && target.kind() != FlowNodeKind.FALLBACK) diagnostics.add(error("INVALID_COMPENSATION_TARGET", link.id(), compensationPath + ".compensationNodeId", "Compensation target must be COMPENSATION_STEP or FALLBACK"));
        }
        checkReachability(flow, nodes, outgoing, path, diagnostics);
        for(String eventType : flow.triggerEventTypeIds()) if(!eventTypes.contains(eventType)) diagnostics.add(error("UNKNOWN_TRIGGER_EVENT", flow.id(), path + ".triggerEventTypeIds", "Unknown trigger event type: " + eventType));
    }

    /** Comprueba que una condición EventFlow exista, sea válida y produzca BOOLEAN/ANY. */
    /** Descripción: Valida una condición EventFlow contra semantic.expression. Entradas: expressionId, ownerId, path, expressions y metamodel. Salida: añade diagnósticos bloqueantes cuando procede. */ private void checkCondition(String expressionId, String ownerId, String path, ExpressionModel expressions, MetaModel metamodel,
            List<FlowDiagnostic> diagnostics) {
        if(expressionId == null || expressionId.isBlank()) return;
        if(expressions == null || metamodel == null) {
            diagnostics.add(error("EXPRESSION_MODEL_REQUIRED", ownerId, path, "Condition requires a configured expression model"));
            return;
        }
        ExpressionDefinition expression = expressions.findExpression(expressionId).orElse(null);
        if(expression == null) {
            diagnostics.add(error("UNKNOWN_CONDITION_EXPRESSION", ownerId, path, "Condition expression does not exist: " + expressionId));
            return;
        }
        var result = expressionTypeChecker.check(expression, metamodel);
        if(result.hasErrors()) diagnostics.add(error("INVALID_CONDITION_EXPRESSION", ownerId, path, "Condition expression contains semantic errors"));
        else if(result.inferredType().kind() != ExpressionValueKind.BOOLEAN && result.inferredType().kind() != ExpressionValueKind.ANY)
            diagnostics.add(error("CONDITION_NOT_BOOLEAN", ownerId, path, "EventFlow condition must return BOOLEAN"));
    }

    /** Descripción: Valida las condiciones necesarias para «checkReachability» y rechaza entradas no válidas. Entradas: flow: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; outgoing: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkReachability(FlowDefinition flow, Map<String, FlowNode> nodes, Map<String, List<FlowTransition>> outgoing,
            String path, List<FlowDiagnostic> diagnostics) {
        FlowNode start = flow.nodes().stream().filter(node -> node.kind() == FlowNodeKind.START || node.kind() == FlowNodeKind.SOURCE).findFirst().orElse(null);
        if(start == null) return;
        Set<String> reached = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start.id());
        while(!queue.isEmpty()) {
            String current = queue.removeFirst();
            if(!reached.add(current)) continue;
            for(FlowTransition transition : outgoing.getOrDefault(current, List.of())) if(nodes.containsKey(transition.targetNodeId())) queue.addLast(transition.targetNodeId());
        }
        for(FlowNode node : flow.nodes()) if(!reached.contains(node.id())) diagnostics.add(error("UNREACHABLE_NODE", node.id(), path + ".nodes", "Node cannot be reached from the flow start"));
    }

    /** Descripción: Valida las condiciones necesarias para «requiresEventType» y rechaza entradas no válidas. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean requiresEventType(FlowNodeKind kind) { return kind == FlowNodeKind.SOURCE || kind == FlowNodeKind.EVENT_STEP || kind == FlowNodeKind.WAIT_FOR_EVENT || kind == FlowNodeKind.EMIT || kind == FlowNodeKind.ABSENCE; }
    /** Descripción: Evalúa la condición «isTerminal» sin modificar el estado externo. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isTerminal(FlowNodeKind kind) { return kind == FlowNodeKind.SUCCESS_END || kind == FlowNodeKind.ERROR_END; }
    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de EventFlowChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private FlowDiagnostic error(String code, String id, String path, String message) { return new FlowDiagnostic(FlowDiagnosticSeverity.ERROR, code, id, path, message); }
    /** Descripción: Ejecuta la operación «info» correspondiente a la responsabilidad de EventFlowChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private FlowDiagnostic info(String code, String id, String path, String message) { return new FlowDiagnostic(FlowDiagnosticSeverity.INFO, code, id, path, message); }
}

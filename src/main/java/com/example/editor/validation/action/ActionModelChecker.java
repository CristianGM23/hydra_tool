package com.example.editor.validation.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.semantic.expression.ExpressionCheckResult;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Comprueba referencias, ciclos y precondiciones sin ejecutar acciones. */
@Service
public class ActionModelChecker {

    private final ExpressionTypeChecker expressionTypeChecker;

    /** Descripción: Inicializa una instancia de ActionModelChecker con sus dependencias y datos de entrada. Entradas: expressionTypeChecker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ActionModelChecker(ExpressionTypeChecker expressionTypeChecker) {
        this.expressionTypeChecker = expressionTypeChecker;
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: model: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ActionModelCheckReport check(ActionModel model, MetaModel metamodel, ExpressionModel expressions) {
        List<ActionDiagnostic> global = new ArrayList<>();
        List<ActionCheckResult> results = new ArrayList<>();
        if(model == null) {
            return new ActionModelCheckReport("", List.of(error("ACTION_MODEL_REQUIRED", "", "", "$", "Action model must not be null")), List.of());
        }
        if(metamodel == null) {
            return new ActionModelCheckReport(model.id(), List.of(error("METAMODEL_REQUIRED", "", "", "$", "Metamodel must not be null")), List.of());
        }
        if(expressions == null) {
            global.add(error("EXPRESSION_MODEL_REQUIRED", "", "", "$.expressionModelId", "Expression model must exist before actions can be checked"));
        }
        if(!model.metamodelId().equals(metamodel.id())) {
            global.add(error("METAMODEL_ID_MISMATCH", "", "", "$.metamodelId", "Action model metamodelId does not match the canonical metamodel"));
        }
        if(expressions != null && !model.expressionModelId().equals(expressions.id())) {
            global.add(error("EXPRESSION_MODEL_ID_MISMATCH", "", "", "$.expressionModelId", "Action model expressionModelId does not match the expression document"));
        }

        Set<String> ids = new HashSet<>();
        Map<String, ActionDefinition> actionIndex = new LinkedHashMap<>();
        for(int index = 0; index < model.actions().size(); index++) {
            ActionDefinition action = model.actions().get(index);
            if(!ids.add(action.id())) {
                global.add(error("DUPLICATE_ACTION_ID", action.id(), "", "$.actions[" + index + "].id", "Action id is duplicated: " + action.id()));
            }
            actionIndex.putIfAbsent(action.id(), action);
        }

        CanonicalMetamodelIndex metamodelIndex = new CanonicalMetamodelIndex(metamodel);
        for(int index = 0; index < model.actions().size(); index++) {
            results.add(checkAction(model.actions().get(index), index, metamodel, metamodelIndex, expressions, actionIndex));
        }
        detectCycles(actionIndex, global);
        return new ActionModelCheckReport(model.id(), global, results);
    }

    /** Descripción: Valida las condiciones necesarias para «checkAction» y rechaza entradas no válidas. Entradas: action: dato de entrada necesario para la operación; actionIndex: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ActionCheckResult checkAction(ActionDefinition action, int actionIndex, MetaModel metamodel, CanonicalMetamodelIndex index,
            ExpressionModel expressions, Map<String, ActionDefinition> actions) {
        List<ActionDiagnostic> diagnostics = new ArrayList<>();
        String base = "$.actions[" + actionIndex + "]";
        Set<String> parameterNames = new HashSet<>();
        for(int indexValue = 0; indexValue < action.parameters().size(); indexValue++) {
            ActionParameter parameter = action.parameters().get(indexValue);
            if(!parameterNames.add(parameter.name())) {
                diagnostics.add(error("DUPLICATE_PARAMETER", action.id(), "", base + ".parameters[" + indexValue + "].name", "Parameter is duplicated: " + parameter.name()));
            }
        }

        checkPrecondition(action, base, metamodel, expressions, diagnostics);

        Set<String> operationIds = new HashSet<>();
        Set<String> createdNames = new HashSet<>();
        for(int indexValue = 0; indexValue < action.operations().size(); indexValue++) {
            ActionOperation operation = action.operations().get(indexValue);
            String path = base + ".operations[" + indexValue + "]";
            if(!operationIds.add(operation.operationId())) {
                diagnostics.add(error("DUPLICATE_OPERATION_ID", action.id(), operation.operationId(), path + ".operationId", "Operation id is duplicated: " + operation.operationId()));
            }
            checkOperation(action.id(), operation, path, index, expressions, actions, parameterNames, createdNames, diagnostics);
            if(operation instanceof CreateElementOperation create && !createdNames.add(create.resultName())) {
                diagnostics.add(error("DUPLICATE_CREATED_ELEMENT_NAME", action.id(), operation.operationId(), path + ".resultName", "Created element name is duplicated: " + create.resultName()));
            }
        }
        if(action.transactionPolicy() == ActionTransactionPolicy.ATOMIC && action.undoPolicy() == ActionUndoPolicy.NOT_SUPPORTED) {
            diagnostics.add(error("ATOMIC_ACTION_REQUIRES_UNDO", action.id(), "", base + ".undoPolicy", "Atomic actions cannot disable undo support"));
        }
        return new ActionCheckResult(action.id(), diagnostics);
    }

    /** Descripción: Valida las condiciones necesarias para «checkPrecondition» y rechaza entradas no válidas. Entradas: action: dato de entrada necesario para la operación; base: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkPrecondition(ActionDefinition action, String base, MetaModel metamodel, ExpressionModel expressions, List<ActionDiagnostic> diagnostics) {
        if(action.preconditionExpressionId().isBlank()) return;
        ExpressionDefinition expression = expressions == null ? null : expressions.findExpression(action.preconditionExpressionId()).orElse(null);
        if(expression == null) {
            diagnostics.add(error("UNKNOWN_PRECONDITION_EXPRESSION", action.id(), "", base + ".preconditionExpressionId", "Precondition expression does not exist: " + action.preconditionExpressionId()));
            return;
        }
        ExpressionCheckResult result = expressionTypeChecker.check(expression, metamodel);
        if(result.hasErrors()) {
            diagnostics.add(error("INVALID_PRECONDITION_EXPRESSION", action.id(), "", base + ".preconditionExpressionId", "Precondition expression contains errors"));
        }
        if(result.inferredType().kind() != ExpressionValueKind.BOOLEAN && result.inferredType().kind() != ExpressionValueKind.ANY) {
            diagnostics.add(error("PRECONDITION_NOT_BOOLEAN", action.id(), "", base + ".preconditionExpressionId", "Precondition expression must return BOOLEAN"));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkOperation» y rechaza entradas no válidas. Entradas: actionId: identificador que delimita el recurso objetivo; operation: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; index: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; parameters: dato de entrada necesario para la operación; createdNames: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkOperation(String actionId, ActionOperation operation, String path, CanonicalMetamodelIndex index,
            ExpressionModel expressions, Map<String, ActionDefinition> actions, Set<String> parameters, Set<String> createdNames,
            List<ActionDiagnostic> diagnostics) {
        if(operation instanceof SetAttributeOperation item) {
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
            attribute(actionId, item.operationId(), item.attributeId(), path + ".attributeId", index, diagnostics);
            value(actionId, item.operationId(), item.value(), path + ".value", expressions, parameters, diagnostics);
        } else if(operation instanceof ClearAttributeOperation item) {
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
            attribute(actionId, item.operationId(), item.attributeId(), path + ".attributeId", index, diagnostics);
        } else if(operation instanceof CopyAttributeOperation item) {
            selector(actionId, item.operationId(), item.source(), path + ".source", parameters, createdNames, diagnostics);
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
            attribute(actionId, item.operationId(), item.sourceAttributeId(), path + ".sourceAttributeId", index, diagnostics);
            attribute(actionId, item.operationId(), item.targetAttributeId(), path + ".targetAttributeId", index, diagnostics);
        } else if(operation instanceof AddRelationOperation item) {
            relation(actionId, item.operationId(), item.relationId(), path + ".relationId", index, diagnostics);
            selector(actionId, item.operationId(), item.source(), path + ".source", parameters, createdNames, diagnostics);
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
        } else if(operation instanceof RemoveRelationOperation item) {
            relation(actionId, item.operationId(), item.relationId(), path + ".relationId", index, diagnostics);
            selector(actionId, item.operationId(), item.source(), path + ".source", parameters, createdNames, diagnostics);
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
        } else if(operation instanceof CreateElementOperation item) {
            if(!index.hasClass(item.classId())) {
                diagnostics.add(error("UNKNOWN_CLASS", actionId, item.operationId(), path + ".classId", "Class does not exist: " + item.classId()));
            }
            if(!item.containmentRelationId().isBlank()) {
                var relation = index.findRelation(item.containmentRelationId()).orElse(null);
                if(relation == null) {
                    diagnostics.add(error("UNKNOWN_RELATION", actionId, item.operationId(), path + ".containmentRelationId", "Containment relation does not exist: " + item.containmentRelationId()));
                } else if(relation.kind() != MetaRelationKind.CONTAINMENT) {
                    diagnostics.add(error("RELATION_NOT_CONTAINMENT", actionId, item.operationId(), path + ".containmentRelationId", "Configured relation is not a containment"));
                } else if(!relation.targetTypeId().equals(item.classId())) {
                    diagnostics.add(error("CONTAINMENT_TARGET_MISMATCH", actionId, item.operationId(), path + ".classId", "Created class does not match the containment target"));
                }
                selector(actionId, item.operationId(), item.container(), path + ".container", parameters, createdNames, diagnostics);
            }
        } else if(operation instanceof DeleteElementOperation item) {
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
        } else if(operation instanceof TransformTextOperation item) {
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
            attribute(actionId, item.operationId(), item.attributeId(), path + ".attributeId", index, diagnostics);
        } else if(operation instanceof ExecuteActionOperation item) {
            if(actionId.equals(item.actionId())) {
                diagnostics.add(error("DIRECT_ACTION_RECURSION", actionId, item.operationId(), path + ".actionId", "An action cannot execute itself directly"));
            } else if(!actions.containsKey(item.actionId())) {
                diagnostics.add(error("UNKNOWN_ACTION", actionId, item.operationId(), path + ".actionId", "Referenced action does not exist: " + item.actionId()));
            } else {
                ActionDefinition nested = actions.get(item.actionId());
                for(String name : item.arguments().keySet()) {
                    if(nested.parameters().stream().noneMatch(parameter -> parameter.name().equals(name))) {
                        diagnostics.add(error("UNKNOWN_NESTED_ACTION_ARGUMENT", actionId, item.operationId(), path + ".arguments." + name, "Nested action parameter does not exist: " + name));
                    }
                }
                for(ActionParameter parameter : nested.parameters()) {
                    if(parameter.required() && !item.arguments().containsKey(parameter.name())) {
                        diagnostics.add(error("MISSING_NESTED_ACTION_ARGUMENT", actionId, item.operationId(), path + ".arguments", "Required nested action argument is missing: " + parameter.name()));
                    }
                }
                item.arguments().forEach((name, source) -> value(actionId, item.operationId(), source, path + ".arguments." + name, expressions, parameters, diagnostics));
            }
        } else if(operation instanceof InvokeOperationOperation item) {
            selector(actionId, item.operationId(), item.target(), path + ".target", parameters, createdNames, diagnostics);
            MetaOperation invoked = index.findOperation(item.metamodelOperationId()).orElse(null);
            if(invoked == null) {
                diagnostics.add(error("UNKNOWN_METAMODEL_OPERATION", actionId, item.operationId(), path + ".metamodelOperationId", "Metamodel operation does not exist: " + item.metamodelOperationId()));
            } else {
                if(invoked.behaviorKind() != MetaOperationBehaviorKind.COMMAND) {
                    diagnostics.add(error("INVOKE_OPERATION_REQUIRES_COMMAND", actionId, item.operationId(), path + ".metamodelOperationId", "INVOKE_OPERATION can only call COMMAND operations"));
                }
                if(item.arguments().size() != invoked.parameters().size()) {
                    diagnostics.add(error("OPERATION_ARGUMENT_COUNT_MISMATCH", actionId, item.operationId(), path + ".arguments", "Operation " + invoked.name() + " expects " + invoked.parameters().size() + " argument(s)"));
                }
            }
            for(int argumentIndex = 0; argumentIndex < item.arguments().size(); argumentIndex++) {
                value(actionId, item.operationId(), item.arguments().get(argumentIndex), path + ".arguments[" + argumentIndex + "]", expressions, parameters, diagnostics);
            }
        }
    }

    /** Descripción: Ejecuta la operación «selector» correspondiente a la responsabilidad de ActionModelChecker. Entradas: actionId: identificador que delimita el recurso objetivo; operationId: identificador que delimita el recurso objetivo; selector: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; parameters: dato de entrada necesario para la operación; createdNames: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void selector(String actionId, String operationId, ActionSelector selector, String path, Set<String> parameters,
            Set<String> createdNames, List<ActionDiagnostic> diagnostics) {
        if(selector == null) {
            diagnostics.add(error("SELECTOR_REQUIRED", actionId, operationId, path, "Selector is required"));
            return;
        }
        if(selector.kind() == ActionSelectorKind.PARAMETER && !parameters.contains(selector.parameterName())) {
            diagnostics.add(error("UNKNOWN_PARAMETER", actionId, operationId, path + ".parameterName", "Parameter does not exist: " + selector.parameterName()));
        }
        if(selector.kind() == ActionSelectorKind.CREATED_ELEMENT && !createdNames.contains(selector.parameterName())) {
            diagnostics.add(error("UNKNOWN_CREATED_ELEMENT", actionId, operationId, path + ".parameterName", "Created element is not available yet: " + selector.parameterName()));
        }
        if(selector.kind() == ActionSelectorKind.ELEMENT_ID && selector.elementId().isBlank()) {
            diagnostics.add(error("ELEMENT_ID_REQUIRED", actionId, operationId, path + ".elementId", "ELEMENT_ID selector requires elementId"));
        }
        if(selector.kind() == ActionSelectorKind.PARAMETER && selector.parameterName().isBlank()) {
            diagnostics.add(error("PARAMETER_NAME_REQUIRED", actionId, operationId, path + ".parameterName", "PARAMETER selector requires parameterName"));
        }
        if(selector.kind() == ActionSelectorKind.CREATED_ELEMENT && selector.parameterName().isBlank()) {
            diagnostics.add(error("CREATED_ELEMENT_NAME_REQUIRED", actionId, operationId, path + ".parameterName", "CREATED_ELEMENT selector requires parameterName"));
        }
    }

    /** Descripción: Ejecuta la operación «attribute» correspondiente a la responsabilidad de ActionModelChecker. Entradas: actionId: identificador que delimita el recurso objetivo; operationId: identificador que delimita el recurso objetivo; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void attribute(String actionId, String operationId, String id, String path, CanonicalMetamodelIndex index, List<ActionDiagnostic> diagnostics) {
        if(!index.hasAttribute(id)) {
            diagnostics.add(error("UNKNOWN_ATTRIBUTE", actionId, operationId, path, "Attribute does not exist: " + id));
        }
    }

    /** Descripción: Ejecuta la operación «relation» correspondiente a la responsabilidad de ActionModelChecker. Entradas: actionId: identificador que delimita el recurso objetivo; operationId: identificador que delimita el recurso objetivo; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void relation(String actionId, String operationId, String id, String path, CanonicalMetamodelIndex index, List<ActionDiagnostic> diagnostics) {
        if(!index.hasRelation(id)) {
            diagnostics.add(error("UNKNOWN_RELATION", actionId, operationId, path, "Relation does not exist: " + id));
        }
    }

    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de ActionModelChecker. Entradas: actionId: identificador que delimita el recurso objetivo; operationId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; expressions: dato de entrada necesario para la operación; parameters: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void value(String actionId, String operationId, ActionValueSource source, String path, ExpressionModel expressions,
            Set<String> parameters, List<ActionDiagnostic> diagnostics) {
        if(source == null) {
            diagnostics.add(error("VALUE_SOURCE_REQUIRED", actionId, operationId, path, "Value source is required"));
            return;
        }
        if(!source.expressionId().isBlank() && (expressions == null || expressions.findExpression(source.expressionId()).isEmpty())) {
            diagnostics.add(error("UNKNOWN_VALUE_EXPRESSION", actionId, operationId, path + ".expressionId", "Value expression does not exist: " + source.expressionId()));
        }
        if(!source.parameterName().isBlank() && !parameters.contains(source.parameterName())) {
            diagnostics.add(error("UNKNOWN_VALUE_PARAMETER", actionId, operationId, path + ".parameterName", "Value parameter does not exist: " + source.parameterName()));
        }
    }

    /** Descripción: Ejecuta la operación «detectCycles» correspondiente a la responsabilidad de ActionModelChecker. Entradas: actions: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void detectCycles(Map<String, ActionDefinition> actions, List<ActionDiagnostic> diagnostics) {
        Map<String, Set<String>> graph = new HashMap<>();
        actions.forEach((id, action) -> graph.put(id, action.operations().stream()
                .filter(ExecuteActionOperation.class::isInstance)
                .map(ExecuteActionOperation.class::cast)
                .map(ExecuteActionOperation::actionId)
                .collect(Collectors.toCollection(HashSet::new))));
        Set<String> reported = new HashSet<>();
        for(String id : graph.keySet()) {
            if(cycle(id, graph, new HashSet<>(), new HashSet<>()) && reported.add(id)) {
                diagnostics.add(error("ACTION_EXECUTION_CYCLE", id, "", "$.actions", "Action execution cycle includes: " + id));
            }
        }
    }

    /** Descripción: Ejecuta la operación «cycle» correspondiente a la responsabilidad de ActionModelChecker. Entradas: id: identificador que delimita el recurso objetivo; graph: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; stack: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean cycle(String id, Map<String, Set<String>> graph, Set<String> visited, Set<String> stack) {
        if(stack.contains(id)) return true;
        if(!visited.add(id)) return false;
        stack.add(id);
        for(String next : graph.getOrDefault(id, Set.of())) {
            if(graph.containsKey(next) && cycle(next, graph, visited, stack)) return true;
        }
        stack.remove(id);
        return false;
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de ActionModelChecker. Entradas: code: dato de entrada necesario para la operación; actionId: identificador que delimita el recurso objetivo; operationId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ActionDiagnostic error(String code, String actionId, String operationId, String path, String message) {
        return new ActionDiagnostic(ActionDiagnosticSeverity.ERROR, code, actionId, operationId, path, message);
    }
}

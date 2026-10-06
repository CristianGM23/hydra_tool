package com.example.editor.validation.action;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionOperationInvoker;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Ejecuta ActionModel 1.0, incluidos parámetros, transacciones, undo e INVOKE_OPERATION. */
@Service
public class CanonicalActionExecutor {
    private final ExpressionEvaluator evaluator;
    private final OperationQueryRuntime queryRuntime;

    /** Descripción: Inicializa una instancia de CanonicalActionExecutor con sus dependencias y datos de entrada. Entradas: evaluator: dato de entrada necesario para la operación; queryRuntime: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalActionExecutor(ExpressionEvaluator evaluator, OperationQueryRuntime queryRuntime) {
        this.evaluator = evaluator;
        this.queryRuntime = queryRuntime;
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de MutableState. Entradas: actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; behaviors: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ActionExecutionResult execute(String actionId, ActionExecutionRequest request, MetaModel metamodel, ExpressionModel expressions,
            ActionModel actions, OperationBehaviorModel behaviors) {
        if(actions == null) throw new ActionExecutionException("Action model is required");
        ActionDefinition action = actions.findAction(actionId).orElseThrow(() -> new ActionExecutionException("Action was not found: " + actionId));
        MutableState state = new MutableState(request.document(), metamodel);
        Execution execution = new Execution(metamodel, expressions, actions, behaviors, state, request.selectionElementIds());
        Map<String, Object> arguments = validateArguments(action, request.arguments());
        try {
            execution.run(action, request.selfElementId(), arguments);
        } catch(RuntimeException exception) {
            if(action.transactionPolicy() == ActionTransactionPolicy.ATOMIC) throw exception instanceof ActionExecutionException ? exception : new ActionExecutionException(exception.getMessage(), exception);
            execution.messages.add("Action completed with errors: " + exception.getMessage());
        }
        CanonicalModelDocument result = state.toDocument();
        boolean changed = !result.equals(request.document());
        boolean undoAvailable = changed && action.undoPolicy() != ActionUndoPolicy.NOT_SUPPORTED;
        return new ActionExecutionResult(action.id(), result, undoAvailable ? request.document() : null, undoAvailable, execution.messages, execution.createdElements);
    }

    /** Descripción: Valida las condiciones necesarias para «validateArguments» y rechaza entradas no válidas. Entradas: action: dato de entrada necesario para la operación; supplied: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Object> validateArguments(ActionDefinition action, Map<String, Object> supplied) {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> known = action.parameters().stream().map(ActionParameter::name).collect(Collectors.toCollection(LinkedHashSet::new));
        for(String name : supplied.keySet()) if(!known.contains(name)) throw new ActionExecutionException("Unknown action parameter: " + name);
        for(ActionParameter parameter : action.parameters()) {
            if(parameter.required() && !supplied.containsKey(parameter.name())) throw new ActionExecutionException("Required action parameter is missing: " + parameter.name());
            if(supplied.containsKey(parameter.name())) result.put(parameter.name(), supplied.get(parameter.name()));
        }
        return result;
    }

    private final class Execution {
        private final MetaModel metamodel;
        private final ExpressionModel expressions;
        private final ActionModel actions;
        private final OperationBehaviorModel behaviors;
        private final MutableState state;
        private final List<String> selection;
        private final CanonicalMetamodelIndex index;
        private final Map<String, OperationBinding> bindings;
        private final ExpressionOperationInvoker queryInvoker;
        private final ArrayDeque<String> actionStack = new ArrayDeque<>();
        private final List<String> messages = new ArrayList<>();
        private final Map<String, String> createdElements = new LinkedHashMap<>();
        private final AtomicInteger sequence = new AtomicInteger();

        /** Descripción: Inicializa una instancia de Execution con sus dependencias y datos de entrada. Entradas: metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; behaviors: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación; selection: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Execution(MetaModel metamodel, ExpressionModel expressions, ActionModel actions, OperationBehaviorModel behaviors, MutableState state, List<String> selection) {
            this.metamodel = metamodel;
            this.expressions = expressions;
            this.actions = actions;
            this.behaviors = behaviors;
            this.state = state;
            this.selection = selection == null ? List.of() : selection;
            this.index = new CanonicalMetamodelIndex(metamodel);
            this.bindings = behaviors == null ? Map.of() : behaviors.bindings().stream().collect(Collectors.toMap(OperationBinding::operationId, item -> item, (left, right) -> left, LinkedHashMap::new));
            this.queryInvoker = queryRuntime.create(metamodel, expressions, behaviors);
        }

        /** Descripción: Procesa la operación «run» coordinando las responsabilidades de MutableState. Entradas: action: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void run(ActionDefinition action, String selfId, Map<String, Object> parameters) {
            if(!action.enabled()) throw new ActionExecutionException("Action is disabled: " + action.name());
            if(actionStack.contains(action.id())) throw new ActionExecutionException("Recursive action execution detected: " + action.id());
            actionStack.addLast(action.id());
            try {
                if(!action.preconditionExpressionId().isBlank()) {
                    Object allowed = evaluateExpression(action.preconditionExpressionId(), selfId, parameters);
                    if(!(allowed instanceof Boolean value) || !value) throw new ActionExecutionException("Action precondition is not satisfied: " + action.name());
                }
                for(ActionOperation operation : action.operations()) {
                    try { runOperation(operation, selfId, parameters); }
                    catch(RuntimeException exception) {
                        if(action.transactionPolicy() == ActionTransactionPolicy.ATOMIC) throw exception;
                        messages.add(operation.operationId() + ": " + exception.getMessage());
                    }
                }
            } finally { actionStack.removeLast(); }
        }

        /** Descripción: Procesa la operación «runOperation» coordinando las responsabilidades de MutableState. Entradas: operation: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void runOperation(ActionOperation operation, String selfId, Map<String, Object> parameters) {
            switch(operation) {
                case SetAttributeOperation item -> state.setAttribute(resolve(item.target(), selfId, parameters), item.attributeId(), resolveValue(item.value(), selfId, parameters));
                case ClearAttributeOperation item -> state.clearAttribute(resolve(item.target(), selfId, parameters), item.attributeId());
                case CopyAttributeOperation item -> state.setAttribute(resolve(item.target(), selfId, parameters), item.targetAttributeId(), state.attribute(resolve(item.source(), selfId, parameters), item.sourceAttributeId()));
                case AddRelationOperation item -> state.addRelation(item.relationId(), resolve(item.source(), selfId, parameters), resolve(item.target(), selfId, parameters), sequence.incrementAndGet());
                case RemoveRelationOperation item -> state.removeRelation(item.relationId(), resolve(item.source(), selfId, parameters), resolve(item.target(), selfId, parameters));
                case CreateElementOperation item -> create(item, selfId, parameters);
                case DeleteElementOperation item -> state.deleteElement(resolve(item.target(), selfId, parameters));
                case TransformTextOperation item -> transform(item, selfId, parameters);
                case ExecuteActionOperation item -> executeNested(item, selfId, parameters);
                case InvokeOperationOperation item -> invokeCommand(item, selfId, parameters);
            }
        }

        /** Descripción: Crea o prepara el resultado de «create» dentro de MutableState. Entradas: operation: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void create(CreateElementOperation operation, String selfId, Map<String, Object> parameters) {
            String id;
            do { id = "generated:" + operation.resultName() + ":" + sequence.incrementAndGet(); } while(state.hasElement(id));
            state.addElement(new CanonicalModelElement(id, operation.classId(), Map.of(), Map.of("createdBy", operation.operationId())));
            createdElements.put(operation.resultName(), id);
            if(!operation.containmentRelationId().isBlank()) state.addRelation(operation.containmentRelationId(), resolve(operation.container(), selfId, parameters), id, sequence.incrementAndGet());
            else state.addRoot(id);
        }

        /** Descripción: Ejecuta la operación «transform» correspondiente a la responsabilidad de MutableState. Entradas: operation: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void transform(TransformTextOperation operation, String selfId, Map<String, Object> parameters) {
            String id = resolve(operation.target(), selfId, parameters);
            Object current = state.attribute(id, operation.attributeId());
            String text = current == null ? "" : current.toString();
            String transformed = switch(operation.transformation()) {
                case UPPERCASE -> text.toUpperCase(Locale.ROOT);
                case LOWERCASE -> text.toLowerCase(Locale.ROOT);
                case CAPITALIZE -> text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
                case TRIM -> text.trim();
            };
            state.setAttribute(id, operation.attributeId(), transformed);
        }

        /** Descripción: Procesa la operación «executeNested» coordinando las responsabilidades de MutableState. Entradas: operation: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void executeNested(ExecuteActionOperation operation, String selfId, Map<String, Object> parameters) {
            ActionDefinition nested = actions.findAction(operation.actionId()).orElseThrow(() -> new ActionExecutionException("Nested action was not found: " + operation.actionId()));
            Map<String, Object> nestedArguments = new LinkedHashMap<>();
            operation.arguments().forEach((name, source) -> nestedArguments.put(name, resolveValue(source, selfId, parameters)));
            run(nested, selfId, validateArguments(nested, nestedArguments));
        }

        /** Descripción: Ejecuta la operación «invokeCommand» correspondiente a la responsabilidad de MutableState. Entradas: call: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void invokeCommand(InvokeOperationOperation call, String selfId, Map<String, Object> parameters) {
            MetaOperation operation = index.findOperation(call.metamodelOperationId()).orElseThrow(() -> new ActionExecutionException("Metamodel operation was not found: " + call.metamodelOperationId()));
            if(operation.behaviorKind() != MetaOperationBehaviorKind.COMMAND) throw new ActionExecutionException("Only COMMAND operations can be invoked from actions: " + operation.name());
            String targetId = resolve(call.target(), selfId, parameters);
            CanonicalModelElement target = state.element(targetId);
            if(!index.isSameOrSubtype(target.classId(), index.operationOwner(operation.id()))) throw new ActionExecutionException("Operation target has an incompatible class: " + target.classId());
            if(call.arguments().size() != operation.parameters().size()) throw new ActionExecutionException("Operation " + operation.name() + " expects " + operation.parameters().size() + " argument(s)");
            OperationBinding binding = bindings.get(operation.id());
            if(binding == null || binding.implementationKind() != OperationImplementationKind.ACTION) throw new ActionExecutionException("COMMAND operation requires an ACTION binding: " + operation.name());
            ActionDefinition nested = actions.findAction(binding.actionId()).orElseThrow(() -> new ActionExecutionException("Bound action was not found: " + binding.actionId()));
            Map<String, Object> operationArguments = new LinkedHashMap<>();
            for(int i = 0; i < operation.parameters().size(); i++) operationArguments.put(operation.parameters().get(i).name(), resolveValue(call.arguments().get(i), selfId, parameters));
            Map<String, Object> nestedArguments = new LinkedHashMap<>();
            for(ActionParameter parameter : nested.parameters()) if(operationArguments.containsKey(parameter.name())) nestedArguments.put(parameter.name(), operationArguments.get(parameter.name()));
            run(nested, targetId, validateArguments(nested, nestedArguments));
        }

        /** Descripción: Obtiene la información correspondiente a «resolve» dentro de MutableState. Entradas: selector: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolve(ActionSelector selector, String selfId, Map<String, Object> parameters) {
            if(selector == null) throw new ActionExecutionException("Element selector is required");
            String id = switch(selector.kind()) {
                case SELF -> selfId;
                case SELECTION -> selection.isEmpty() ? "" : selection.getFirst();
                case PARAMETER -> valueAsElementId(parameters.get(selector.parameterName()), selector.parameterName());
                case CREATED_ELEMENT -> createdElements.getOrDefault(selector.parameterName(), "");
                case ELEMENT_ID -> selector.elementId();
            };
            if(id == null || id.isBlank() || !state.hasElement(id)) throw new ActionExecutionException("Selected element was not found: " + id);
            return id;
        }

        /** Descripción: Ejecuta la operación «valueAsElementId» correspondiente a la responsabilidad de MutableState. Entradas: value: valor de entrada que debe procesarse; parameterName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String valueAsElementId(Object value, String parameterName) {
            if(value instanceof CanonicalModelElement element) return element.id();
            if(value instanceof String id) return id;
            throw new ActionExecutionException("Parameter does not contain an element id: " + parameterName);
        }

        /** Descripción: Obtiene la información correspondiente a «resolveValue» dentro de MutableState. Entradas: source: dato de entrada necesario para la operación; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object resolveValue(ActionValueSource source, String selfId, Map<String, Object> parameters) {
            if(source == null) throw new ActionExecutionException("Value source is required");
            if(!source.expressionId().isBlank()) return evaluateExpression(source.expressionId(), selfId, parameters);
            if(!source.parameterName().isBlank()) {
                if(!parameters.containsKey(source.parameterName())) throw new ActionExecutionException("Value parameter was not provided: " + source.parameterName());
                return parameters.get(source.parameterName());
            }
            return source.literal();
        }

        /** Descripción: Ejecuta la operación «evaluateExpression» correspondiente a la responsabilidad de MutableState. Entradas: expressionId: identificador que delimita el recurso objetivo; selfId: identificador que delimita el recurso objetivo; parameters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object evaluateExpression(String expressionId, String selfId, Map<String, Object> parameters) {
            ExpressionDefinition expression = expressions == null ? null : expressions.findExpression(expressionId).orElse(null);
            if(expression == null) throw new ActionExecutionException("Expression was not found: " + expressionId);
            ExpressionEvaluationContext context = new ExpressionEvaluationContext(metamodel, state.toDocument(), selfId, parameters);
            return evaluator.evaluate(expression, context, queryInvoker).value();
        }
    }

    private static final class MutableState {
        private final CanonicalModelDocument source;
        private final Map<String, CanonicalModelElement> elements = new LinkedHashMap<>();
        private final Map<String, CanonicalModelRelation> relations = new LinkedHashMap<>();
        private final LinkedHashSet<String> roots = new LinkedHashSet<>();
        private final Set<String> containmentRelationIds;
        /** Descripción: Inicializa una instancia de MutableState con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private MutableState(CanonicalModelDocument source, MetaModel metamodel) {
            this.source = source;
            this.containmentRelationIds = metamodel.relations().stream().filter(item -> item.kind() == com.example.editor.metamodel.core.MetaRelationKind.CONTAINMENT).map(item -> item.id()).collect(Collectors.toUnmodifiableSet());
            source.elements().forEach(element -> elements.put(element.id(), element));
            source.relations().forEach(relation -> relations.put(relation.id(), relation));
            roots.addAll(source.rootElementIds());
        }
        /** Descripción: Evalúa la condición «hasElement» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasElement(String id) { return elements.containsKey(id); }
        /** Descripción: Ejecuta la operación «element» correspondiente a la responsabilidad de MutableState. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private CanonicalModelElement element(String id) { CanonicalModelElement value = elements.get(id); if(value == null) throw new ActionExecutionException("Element was not found: " + id); return value; }
        /** Descripción: Ejecuta la operación «attribute» correspondiente a la responsabilidad de MutableState. Entradas: id: identificador que delimita el recurso objetivo; attributeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Object attribute(String id, String attributeId) { return element(id).attributes().get(attributeId); }
        /** Descripción: Actualiza el estado gestionado por MutableState mediante la operación «setAttribute». Entradas: id: identificador que delimita el recurso objetivo; attributeId: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void setAttribute(String id, String attributeId, Object value) { CanonicalModelElement current = element(id); Map<String, Object> attributes = new LinkedHashMap<>(current.attributes()); attributes.put(attributeId, value); elements.put(id, new CanonicalModelElement(current.id(), current.classId(), attributes, current.annotations())); }
        /** Descripción: Elimina o libera el recurso gestionado por «clearAttribute» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; attributeId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void clearAttribute(String id, String attributeId) { CanonicalModelElement current = element(id); Map<String, Object> attributes = new LinkedHashMap<>(current.attributes()); attributes.remove(attributeId); elements.put(id, new CanonicalModelElement(current.id(), current.classId(), attributes, current.annotations())); }
        /** Descripción: Ejecuta la operación «addElement» correspondiente a la responsabilidad de MutableState. Entradas: element: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addElement(CanonicalModelElement element) { if(elements.putIfAbsent(element.id(), element) != null) throw new ActionExecutionException("Element already exists: " + element.id()); }
        /** Descripción: Ejecuta la operación «addRoot» correspondiente a la responsabilidad de MutableState. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addRoot(String id) { roots.add(id); }
        /** Descripción: Ejecuta la operación «addRelation» correspondiente a la responsabilidad de MutableState. Entradas: relationId: identificador que delimita el recurso objetivo; sourceId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; sequence: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addRelation(String relationId, String sourceId, String targetId, int sequence) { element(sourceId); element(targetId); boolean exists = relations.values().stream().anyMatch(item -> item.relationId().equals(relationId) && item.sourceElementId().equals(sourceId) && item.targetElementId().equals(targetId)); if(!exists) relations.put("generated:relation:" + sequence, new CanonicalModelRelation("generated:relation:" + sequence, relationId, sourceId, targetId, Map.of())); }
        /** Descripción: Elimina o libera el recurso gestionado por «removeRelation» de forma controlada. Entradas: relationId: identificador que delimita el recurso objetivo; sourceId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void removeRelation(String relationId, String sourceId, String targetId) { relations.values().removeIf(item -> item.relationId().equals(relationId) && item.sourceElementId().equals(sourceId) && item.targetElementId().equals(targetId)); }
        /** Descripción: Elimina o libera el recurso gestionado por «deleteElement» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteElement(String id) { element(id); Set<String> deleted = new LinkedHashSet<>(); collectContained(id, deleted); deleted.forEach(elements::remove); relations.values().removeIf(item -> deleted.contains(item.sourceElementId()) || deleted.contains(item.targetElementId())); roots.removeAll(deleted); }
        /** Descripción: Ejecuta la operación «collectContained» correspondiente a la responsabilidad de MutableState. Entradas: id: identificador que delimita el recurso objetivo; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectContained(String id, Set<String> result) { if(!result.add(id)) return; List<String> children = relations.values().stream().filter(item -> item.sourceElementId().equals(id) && containmentRelationIds.contains(item.relationId())).map(CanonicalModelRelation::targetElementId).toList(); children.forEach(child -> collectContained(child, result)); }
        /** Descripción: Ejecuta la operación «toDocument» correspondiente a la responsabilidad de MutableState. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private CanonicalModelDocument toDocument() { return new CanonicalModelDocument(source.schemaVersion(), source.id(), source.metamodelId(), source.metamodelEtag(), source.name(), List.copyOf(roots), List.copyOf(elements.values()), List.copyOf(relations.values()), source.annotations()); }
    }
}

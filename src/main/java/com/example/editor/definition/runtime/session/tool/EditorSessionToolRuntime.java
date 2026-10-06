package com.example.editor.definition.runtime.session.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.editor.definition.ActionEditorTool;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.OperationEditorTool;
import com.example.editor.definition.ValidateEditorTool;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;
import com.example.editor.validation.action.ActionExecutionRequest;
import com.example.editor.validation.action.ActionExecutionResult;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.CanonicalActionExecutor;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.OperationCallExpressionNode;
import com.example.editor.semantic.expression.SelfExpressionNode;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationChangeSet;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationService;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Ejecuta operaciones, acciones y validaciones sin convertir la sesión neutral a EMF. */
@Service
public class EditorSessionToolRuntime {
    private final CanonicalMetamodelApplicationService metamodels;
    private final OperationBehaviorModelApplicationService behaviors;
    private final ExpressionModelRepository expressions;
    private final ActionModelRepository actions;
    private final OperationQueryRuntime queryRuntime;
    private final CanonicalActionExecutor actionExecutor;
    private final ValidationRuntimeApplicationService validations;

    /** Descripción: Inicializa una instancia de EditorSessionToolRuntime con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; behaviors: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; queryRuntime: dato de entrada necesario para la operación; actionExecutor: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionToolRuntime(CanonicalMetamodelApplicationService metamodels,
            OperationBehaviorModelApplicationService behaviors, ExpressionModelRepository expressions,
            ActionModelRepository actions, OperationQueryRuntime queryRuntime,
            CanonicalActionExecutor actionExecutor, ValidationRuntimeApplicationService validations) {
        this.metamodels = metamodels;
        this.behaviors = behaviors;
        this.expressions = expressions;
        this.actions = actions;
        this.queryRuntime = queryRuntime;
        this.actionExecutor = actionExecutor;
        this.validations = validations;
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de EditorSessionToolRuntime. Entradas: tool: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionToolOutcome execute(EditorTool tool, String metamodelId, CanonicalModelDocument model,
            EditorSessionToolExecutionRequest request) {
        if(tool instanceof ActionEditorTool action) return action(action, metamodelId, model, request);
        if(tool instanceof OperationEditorTool operation) return operation(operation, metamodelId, model, request);
        if(tool instanceof ValidateEditorTool validation) return validate(validation, metamodelId, model, request);
        throw new IllegalArgumentException("Tool is not executable by the neutral runtime: " + tool.id());
    }

    /** Descripción: Ejecuta la operación «action» correspondiente a la responsabilidad de EditorSessionToolRuntime. Entradas: tool: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionToolOutcome action(ActionEditorTool tool, String metamodelId, CanonicalModelDocument model,
            EditorSessionToolExecutionRequest request) {
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        ExpressionModel expressionModel = expressionModel(metamodelId);
        ActionModel actionModel = actionModel(metamodelId);
        OperationBehaviorModel behaviorModel = behaviors.findOrEmpty(metamodelId);
        String self = target(request, model);
        ActionExecutionResult result = actionExecutor.execute(tool.actionId(), new ActionExecutionRequest(model, self,
                request.selectionElementIds(), request.arguments()), metamodel, expressionModel, actionModel, behaviorModel);
        return new EditorSessionToolOutcome(result.document(), !result.document().equals(model), null,
                result.messages(), null);
    }

    /** Descripción: Ejecuta la operación «operation» correspondiente a la responsabilidad de EditorSessionToolRuntime. Entradas: tool: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionToolOutcome operation(OperationEditorTool tool, String metamodelId,
            CanonicalModelDocument model, EditorSessionToolExecutionRequest request) {
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        MetaOperation operation = index.findOperation(tool.operationId())
                .orElseThrow(() -> new IllegalArgumentException("Operation was not found: " + tool.operationId()));
        OperationBehaviorModel behaviorModel = behaviors.findOrEmpty(metamodelId);
        OperationBinding binding = behaviorModel.bindings().stream()
                .filter(item -> item.operationId().equals(operation.id())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Operation has no implementation: " + operation.name()));
        String targetId = target(request, model);
        CanonicalModelElement target = model.findElement(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target element was not found: " + targetId));
        if(!index.isSameOrSubtype(target.classId(), index.operationOwner(operation.id())))
            throw new IllegalArgumentException("Selected element is not compatible with operation: " + operation.name());
        List<Object> ordered = orderedArguments(operation, request.arguments());
        ExpressionModel expressionModel = expressionModel(metamodelId);
        if(binding.implementationKind() == OperationImplementationKind.EXPRESSION) {
            if(operation.behaviorKind() == MetaOperationBehaviorKind.COMMAND)
                throw new IllegalArgumentException("A command operation cannot use an expression-only implementation");
            OperationCallExpressionNode call = new OperationCallExpressionNode("runtime:editor-operation",
                    new SelfExpressionNode("runtime:editor-target"), operation.id(), List.of());
            Object value = queryRuntime.create(metamodel, expressionModel, behaviorModel).invoke(call, target, ordered,
                    new ExpressionEvaluationContext(metamodel, model, target.id(), request.arguments()));
            return EditorSessionToolOutcome.unchanged(model, value, List.of("Operation executed: " + operation.name()), null);
        }
        if(binding.implementationKind() != OperationImplementationKind.ACTION)
            throw new IllegalArgumentException("Operation implementation is not executable in the neutral editor: " + binding.implementationKind());
        if(operation.behaviorKind() != MetaOperationBehaviorKind.COMMAND)
            throw new IllegalArgumentException("Only command operations can execute action implementations");
        Map<String, Object> arguments = new LinkedHashMap<>();
        for(int indexValue = 0; indexValue < operation.parameters().size(); indexValue++)
            arguments.put(operation.parameters().get(indexValue).name(), ordered.get(indexValue));
        ActionExecutionResult result = actionExecutor.execute(binding.actionId(), new ActionExecutionRequest(model,
                target.id(), request.selectionElementIds(), arguments), metamodel, expressionModel,
                actionModel(metamodelId), behaviorModel);
        return new EditorSessionToolOutcome(result.document(), !result.document().equals(model), null,
                result.messages(), null);
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: tool: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionToolOutcome validate(ValidateEditorTool tool, String metamodelId,
            CanonicalModelDocument model, EditorSessionToolExecutionRequest request) {
        ValidationRunMode mode;
        try { mode = ValidationRunMode.valueOf(tool.validationMode().trim().toUpperCase()); }
        catch(IllegalArgumentException exception) { mode = ValidationRunMode.MANUAL; }
        String classId = "";
        if(mode == ValidationRunMode.CLASS && !request.targetElementId().isBlank())
            classId = model.findElement(request.targetElementId()).map(CanonicalModelElement::classId).orElse("");
        List<String> selection = request.selectionElementIds();
        if(selection.isEmpty() && !request.targetElementId().isBlank()) selection = List.of(request.targetElementId());
        ModelValidationReport report = validations.validate(metamodelId, new ValidationExecutionRequest(mode, model,
                selection, classId, ValidationChangeSet.empty(), request.arguments()));
        List<String> messages = new ArrayList<>();
        messages.add("Validation completed with " + report.diagnostics().size() + " diagnostic(s)");
        return EditorSessionToolOutcome.unchanged(model, null, messages, report);
    }

    /** Descripción: Ejecuta la operación «target» correspondiente a la responsabilidad de EditorSessionToolRuntime. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String target(EditorSessionToolExecutionRequest request, CanonicalModelDocument model) {
        if(!request.targetElementId().isBlank()) return request.targetElementId();
        if(!request.selectionElementIds().isEmpty()) return request.selectionElementIds().get(0);
        return model.rootElementIds().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select an element before executing this tool"));
    }

    /** Descripción: Ejecuta la operación «orderedArguments» correspondiente a la responsabilidad de EditorSessionToolRuntime. Entradas: operation: dato de entrada necesario para la operación; supplied: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Object> orderedArguments(MetaOperation operation, Map<String, Object> supplied) {
        var expected = operation.parameters().stream().map(MetaParameter::name).collect(Collectors.toSet());
        List<String> unknown = supplied.keySet().stream().filter(value -> !expected.contains(value)).sorted().toList();
        if(!unknown.isEmpty()) throw new IllegalArgumentException("Unknown operation argument(s): " + String.join(", ", unknown));
        List<Object> result = new ArrayList<>();
        for(MetaParameter parameter : operation.parameters()) {
            if(parameter.multiplicity().lowerBound() > 0 && !supplied.containsKey(parameter.name()))
                throw new IllegalArgumentException("Required operation argument is missing: " + parameter.name());
            result.add(supplied.get(parameter.name()));
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «expressionModel» correspondiente a la responsabilidad de EditorSessionToolRuntime. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel expressionModel(String metamodelId) {
        return expressions.findById(metamodelId).map(item -> item.expressionModel())
                .orElseThrow(() -> new IllegalArgumentException("Expression model was not found: " + metamodelId));
    }
    /** Descripción: Ejecuta la operación «actionModel» correspondiente a la responsabilidad de EditorSessionToolRuntime. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModel actionModel(String metamodelId) {
        return actions.findById(metamodelId).map(StoredCanonicalDocument::document)
                .orElseThrow(() -> new IllegalArgumentException("Action model was not found: " + metamodelId));
    }
}

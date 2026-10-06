package com.example.editor.operation.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.model.ModelSessionProperties;
import com.example.editor.runtime.BoundedHistoryStore;
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
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Catálogo y ejecución integrada de operaciones sobre las sesiones abiertas del editor. */
@Service
public class OperationRuntimeService {
    private final CanonicalMetamodelApplicationService metamodelService;
    private final OperationBehaviorModelApplicationService behaviorService;
    private final ExpressionModelRepository expressionRepository;
    private final ActionModelRepository actionRepository;
    private final OperationQueryRuntime queryRuntime;
    private final CanonicalActionExecutor actionExecutor;
    private final ModelDocumentService modelService;
    private final BoundedHistoryStore<ModelDocumentService.ModelSnapshot> undoStacks;

    /** Descripción: Inicializa una instancia de OperationRuntimeService con sus dependencias y datos de entrada. Entradas: metamodelService: servicio colaborador utilizado por la operación; behaviorService: servicio colaborador utilizado por la operación; expressionRepository: dato de entrada necesario para la operación; actionRepository: dato de entrada necesario para la operación; queryRuntime: dato de entrada necesario para la operación; actionExecutor: dato de entrada necesario para la operación; modelService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public OperationRuntimeService(CanonicalMetamodelApplicationService metamodelService, OperationBehaviorModelApplicationService behaviorService,
            ExpressionModelRepository expressionRepository, ActionModelRepository actionRepository, OperationQueryRuntime queryRuntime,
            CanonicalActionExecutor actionExecutor, ModelDocumentService modelService) {
        this(metamodelService, behaviorService, expressionRepository, actionRepository, queryRuntime, actionExecutor, modelService, new ModelSessionProperties());
    }

    @Autowired
    /** Descripción: Inicializa una instancia de OperationRuntimeService con sus dependencias y datos de entrada. Entradas: metamodelService: servicio colaborador utilizado por la operación; behaviorService: servicio colaborador utilizado por la operación; expressionRepository: dato de entrada necesario para la operación; actionRepository: dato de entrada necesario para la operación; queryRuntime: dato de entrada necesario para la operación; actionExecutor: dato de entrada necesario para la operación; modelService: servicio colaborador utilizado por la operación; sessionProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public OperationRuntimeService(CanonicalMetamodelApplicationService metamodelService, OperationBehaviorModelApplicationService behaviorService,
            ExpressionModelRepository expressionRepository, ActionModelRepository actionRepository, OperationQueryRuntime queryRuntime,
            CanonicalActionExecutor actionExecutor, ModelDocumentService modelService, ModelSessionProperties sessionProperties) {
        this.metamodelService = metamodelService;
        this.behaviorService = behaviorService;
        this.expressionRepository = expressionRepository;
        this.actionRepository = actionRepository;
        this.queryRuntime = queryRuntime;
        this.actionExecutor = actionExecutor;
        this.modelService = modelService;
        this.undoStacks = new BoundedHistoryStore<>(sessionProperties.getMaxActive(), 20);
    }

    /** Descripción: Ejecuta la operación «catalog» correspondiente a la responsabilidad de OperationRuntimeService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public OperationRuntimeCatalog catalog(String metamodelId) {
        MetaModel metamodel = metamodelService.findById(metamodelId).metamodel();
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        OperationBehaviorModel behaviors = behaviorService.findOrEmpty(metamodelId);
        Map<String, OperationBinding> bindings = behaviors.bindings().stream().collect(Collectors.toMap(OperationBinding::operationId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<OperationRuntimeDescriptor> result = new ArrayList<>();
        for(MetaClass owner : metamodel.classes()) {
            for(MetaOperation operation : owner.operations()) {
                OperationBinding binding = bindings.get(operation.id());
                OperationImplementationKind implementation = binding == null ? OperationImplementationKind.UNIMPLEMENTED : binding.implementationKind();
                boolean enabled = switch(implementation) {
                    case EXPRESSION -> binding != null && !binding.expressionId().isBlank();
                    case ACTION -> binding != null && !binding.actionId().isBlank();
                    case EXTERNAL, UNIMPLEMENTED -> false;
                };
                List<OperationRuntimeParameter> parameters = operation.parameters().stream().map(parameter -> new OperationRuntimeParameter(parameter.id(), parameter.name(), typeLabel(parameter.type()), parameter.multiplicity().lowerBound() > 0, parameter.multiplicity().upperBound() == null || parameter.multiplicity().upperBound() > 1)).toList();
                List<MetaClass> applicableClasses = metamodel.classes().stream().filter(candidate -> index.isSameOrSubtype(candidate.id(), owner.id())).toList();
                result.add(new OperationRuntimeDescriptor(operation.id(), owner.id(), owner.name(),
                        applicableClasses.stream().map(MetaClass::id).toList(), applicableClasses.stream().map(MetaClass::name).toList(),
                        operation.name(), operation.behaviorKind(), parameters, operation.returnsVoid() ? "void" : typeLabel(operation.returnType()),
                        implementation, enabled, surfaces(operation, binding)));
            }
        }
        return new OperationRuntimeCatalog(metamodelId, result);
    }

    /** Descripción: Ejecuta la operación «invoke» correspondiente a la responsabilidad de OperationRuntimeService. Entradas: modelId: identificador que delimita el recurso objetivo; operationId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public OperationInvocationResult invoke(String modelId, String operationId, OperationInvocationRequest request) {
        if(request == null || request.targetElementId().isBlank()) throw new OperationRuntimeException(OperationRuntimeException.Reason.INVALID_REQUEST, "Target element id is required");
        ModelDocumentResponse document = modelService.find(modelId);
        MetaModel metamodel = metamodelService.findById(document.metamodelId()).metamodel();
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        MetaOperation operation = index.findOperation(operationId).orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.NOT_FOUND, "Operation was not found: " + operationId));
        OperationBehaviorModel behaviors = behaviorService.findOrEmpty(document.metamodelId());
        OperationBinding binding = behaviors.bindings().stream().filter(item -> item.operationId().equals(operationId)).findFirst().orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.NOT_EXECUTABLE, "Operation has no binding: " + operation.name()));
        CanonicalModelRuntimeAdapter adapter = new CanonicalModelRuntimeAdapter(metamodel);
        CanonicalModelDocument canonical = adapter.toCanonical(document);
        CanonicalModelElement target = canonical.findElement(request.targetElementId()).orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.NOT_FOUND, "Target element was not found: " + request.targetElementId()));
        if(!index.isSameOrSubtype(target.classId(), index.operationOwner(operation.id()))) throw new OperationRuntimeException(OperationRuntimeException.Reason.INVALID_REQUEST, "Target element is not compatible with operation owner");
        List<Object> orderedArguments = orderedArguments(operation, request.arguments());
        ExpressionModel expressions = findExpressions(document.metamodelId());

        if(binding.implementationKind() == OperationImplementationKind.EXPRESSION) {
            if(operation.behaviorKind() == MetaOperationBehaviorKind.COMMAND) throw new OperationRuntimeException(OperationRuntimeException.Reason.NOT_EXECUTABLE, "COMMAND operation cannot execute an expression-only binding");
            Object value;
            try {
                OperationCallExpressionNode call = new OperationCallExpressionNode("runtime:operation-call", new SelfExpressionNode("runtime:target"), operation.id(), List.of());
                value = queryRuntime.create(metamodel, expressions, behaviors).invoke(call, target, orderedArguments, new ExpressionEvaluationContext(metamodel, canonical, target.id(), request.arguments()));
            } catch(RuntimeException exception) { throw new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, exception.getMessage(), exception); }
            return new OperationInvocationResult(operation.id(), value, document, hasUndo(modelId), List.of());
        }
        if(binding.implementationKind() != OperationImplementationKind.ACTION) throw new OperationRuntimeException(OperationRuntimeException.Reason.NOT_EXECUTABLE, "Operation binding is not executable in the integrated editor: " + binding.implementationKind());
        if(operation.behaviorKind() != MetaOperationBehaviorKind.COMMAND) throw new OperationRuntimeException(OperationRuntimeException.Reason.NOT_EXECUTABLE, "Only COMMAND operations can execute action bindings");
        ActionModel actions = findActions(document.metamodelId());
        Map<String, Object> actionArguments = new LinkedHashMap<>();
        for(int indexValue = 0; indexValue < operation.parameters().size(); indexValue++) actionArguments.put(operation.parameters().get(indexValue).name(), orderedArguments.get(indexValue));
        ModelDocumentService.ModelSnapshot snapshot = modelService.snapshot(modelId);
        try {
            ActionExecutionResult execution = actionExecutor.execute(binding.actionId(), new ActionExecutionRequest(canonical, target.id(), request.selectionElementIds(), actionArguments), metamodel, expressions, actions, behaviors);
            ModelDocumentResponse updated = adapter.apply(modelId, document, execution.document(), modelService);
            if(execution.undoAvailable()) pushUndo(modelId, snapshot);
            return new OperationInvocationResult(operation.id(), null, updated, hasUndo(modelId), execution.messages());
        } catch(RuntimeException exception) {
            modelService.restore(modelId, snapshot);
            throw new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, exception.getMessage(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «undo» correspondiente a la responsabilidad de OperationRuntimeService. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public OperationInvocationResult undo(String modelId) {
        ModelDocumentService.ModelSnapshot snapshot = undoStacks.pop(modelId).orElseThrow(() -> new OperationRuntimeException(
                OperationRuntimeException.Reason.UNDO_NOT_AVAILABLE, "There is no operation to undo for model: " + modelId));
        ModelDocumentResponse restored = modelService.restore(modelId, snapshot);
        return new OperationInvocationResult("undo", null, restored, hasUndo(modelId), List.of("Last operation was undone"));
    }

    /** Descripción: Ejecuta la operación «orderedArguments» correspondiente a la responsabilidad de OperationRuntimeService. Entradas: operation: dato de entrada necesario para la operación; supplied: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Object> orderedArguments(MetaOperation operation, Map<String, Object> supplied) {
        SetView unknown = new SetView(supplied.keySet(), operation.parameters().stream().map(MetaParameter::name).collect(Collectors.toSet()));
        if(!unknown.values().isEmpty()) throw new OperationRuntimeException(OperationRuntimeException.Reason.INVALID_REQUEST, "Unknown operation argument(s): " + String.join(", ", unknown.values()));
        List<Object> result = new ArrayList<>();
        for(MetaParameter parameter : operation.parameters()) {
            if(parameter.multiplicity().lowerBound() > 0 && !supplied.containsKey(parameter.name())) throw new OperationRuntimeException(OperationRuntimeException.Reason.INVALID_REQUEST, "Required operation argument is missing: " + parameter.name());
            result.add(supplied.get(parameter.name()));
        }
        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «findExpressions» dentro de OperationRuntimeService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel findExpressions(String metamodelId) { return expressionRepository.findById(metamodelId).map(item -> item.expressionModel()).orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.NOT_EXECUTABLE, "Expression model was not found: " + metamodelId)); }
    /** Descripción: Obtiene la información correspondiente a «findActions» dentro de OperationRuntimeService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModel findActions(String metamodelId) { return actionRepository.findById(metamodelId).map(StoredCanonicalDocument::document).orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.NOT_EXECUTABLE, "Action model was not found: " + metamodelId)); }
    /** Descripción: Ejecuta la operación «pushUndo» correspondiente a la responsabilidad de OperationRuntimeService. Entradas: modelId: identificador que delimita el recurso objetivo; snapshot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void pushUndo(String modelId, ModelDocumentService.ModelSnapshot snapshot) { undoStacks.push(modelId, snapshot); }
    /** Descripción: Evalúa la condición «hasUndo» sin modificar el estado externo. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasUndo(String modelId) { return undoStacks.has(modelId); }
    /** Descripción: Ejecuta la operación «typeLabel» correspondiente a la responsabilidad de OperationRuntimeService. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String typeLabel(MetaTypeReference type) { if(type == null) return "void"; if(type.targetId() != null && !type.targetId().isBlank()) return type.targetId(); if(type.externalName() != null && !type.externalName().isBlank()) return type.externalName(); return type.kind().name(); }
    /** Descripción: Ejecuta la operación «surfaces» correspondiente a la responsabilidad de OperationRuntimeService. Entradas: operation: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<OperationSurface> surfaces(MetaOperation operation, OperationBinding binding) { String configured = binding == null ? "" : binding.annotations().getOrDefault("ui.surfaces", ""); if(configured.isBlank()) configured = operation.annotations().getOrDefault("ui.surfaces", ""); if(configured.isBlank()) return operation.behaviorKind() == MetaOperationBehaviorKind.COMMAND ? List.of(OperationSurface.BUTTON, OperationSurface.FORM, OperationSurface.PALETTE, OperationSurface.MENU) : List.of(OperationSurface.BUTTON, OperationSurface.FORM); return Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isBlank()).map(value -> OperationSurface.valueOf(value.toUpperCase(Locale.ROOT))).distinct().toList(); }
    private record SetView(java.util.Set<String> supplied, java.util.Set<String> expected) { List<String> values() { return supplied.stream().filter(value -> !expected.contains(value)).sorted().toList(); } }
}

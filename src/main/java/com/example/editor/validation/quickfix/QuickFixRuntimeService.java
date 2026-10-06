package com.example.editor.validation.quickfix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.event.bus.CanonicalEventBus;
import com.example.editor.event.bus.EventPublicationBuffer;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.model.ModelSessionProperties;
import com.example.editor.runtime.BoundedHistoryStore;
import com.example.editor.operation.runtime.CanonicalModelRuntimeAdapter;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionExecutionRequest;
import com.example.editor.validation.action.ActionExecutionResult;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.CanonicalActionExecutor;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.orchestration.LiveValidationRequest;
import com.example.editor.validation.orchestration.OrchestratedValidationResult;
import com.example.editor.validation.orchestration.ValidationGate;
import com.example.editor.validation.orchestration.ValidationOrchestrator;
import com.example.editor.validation.orchestration.ValidationRuntimeEventFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ValidationRunMode;

/** Resuelve, previsualiza, aplica, revalida y compensa quick fixes sobre sesiones abiertas. */
@Service
public class QuickFixRuntimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuickFixRuntimeService.class);
    private final CanonicalMetamodelApplicationService metamodelService;
    private final OperationBehaviorModelApplicationService behaviorService;
    private final ExpressionModelRepository expressionRepository;
    private final ActionModelRepository actionRepository;
    private final CanonicalActionExecutor actionExecutor;
    private final ModelDocumentService modelService;
    private final ValidationOrchestrator orchestrator;
    private final CanonicalModelChangeAnalyzer changeAnalyzer;
    private final CanonicalEventBus eventBus;
    private final BoundedHistoryStore<HistoryEntry> undoStacks;
    private final BoundedHistoryStore<HistoryEntry> redoStacks;

    /** Descripción: Inicializa una instancia de QuickFixRuntimeService con sus dependencias y datos de entrada. Entradas: metamodelService: servicio colaborador utilizado por la operación; behaviorService: servicio colaborador utilizado por la operación; expressionRepository: dato de entrada necesario para la operación; actionRepository: dato de entrada necesario para la operación; actionExecutor: dato de entrada necesario para la operación; modelService: servicio colaborador utilizado por la operación; orchestrator: dato de entrada necesario para la operación; changeAnalyzer: dato de entrada necesario para la operación; eventBus: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public QuickFixRuntimeService(CanonicalMetamodelApplicationService metamodelService,
            OperationBehaviorModelApplicationService behaviorService, ExpressionModelRepository expressionRepository,
            ActionModelRepository actionRepository, CanonicalActionExecutor actionExecutor, ModelDocumentService modelService,
            ValidationOrchestrator orchestrator, CanonicalModelChangeAnalyzer changeAnalyzer, CanonicalEventBus eventBus) {
        this(metamodelService, behaviorService, expressionRepository, actionRepository, actionExecutor, modelService, orchestrator,
                changeAnalyzer, eventBus, new ModelSessionProperties());
    }

    @Autowired
    /** Descripción: Inicializa una instancia de QuickFixRuntimeService con sus dependencias y datos de entrada. Entradas: metamodelService: servicio colaborador utilizado por la operación; behaviorService: servicio colaborador utilizado por la operación; expressionRepository: dato de entrada necesario para la operación; actionRepository: dato de entrada necesario para la operación; actionExecutor: dato de entrada necesario para la operación; modelService: servicio colaborador utilizado por la operación; orchestrator: dato de entrada necesario para la operación; changeAnalyzer: dato de entrada necesario para la operación; eventBus: dato de entrada necesario para la operación; sessionProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public QuickFixRuntimeService(CanonicalMetamodelApplicationService metamodelService,
            OperationBehaviorModelApplicationService behaviorService, ExpressionModelRepository expressionRepository,
            ActionModelRepository actionRepository, CanonicalActionExecutor actionExecutor, ModelDocumentService modelService,
            ValidationOrchestrator orchestrator, CanonicalModelChangeAnalyzer changeAnalyzer, CanonicalEventBus eventBus, ModelSessionProperties sessionProperties) {
        this.metamodelService = metamodelService;
        this.behaviorService = behaviorService;
        this.expressionRepository = expressionRepository;
        this.actionRepository = actionRepository;
        this.actionExecutor = actionExecutor;
        this.modelService = modelService;
        this.orchestrator = orchestrator;
        this.changeAnalyzer = changeAnalyzer;
        this.eventBus = eventBus;
        this.undoStacks = new BoundedHistoryStore<>(sessionProperties.getMaxActive(), 20);
        this.redoStacks = new BoundedHistoryStore<>(sessionProperties.getMaxActive(), 20);
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<QuickFixDescriptor> resolve(String modelId, String diagnosticId) {
        ModelValidationDiagnostic diagnostic = diagnostic(modelId, diagnosticId);
        ActionModel actions = actions(modelService.find(modelId).metamodelId());
        List<QuickFixDescriptor> result = new ArrayList<>();
        for(String actionId : diagnostic.quickFixIds()) {
            ActionDefinition action = actions.findAction(actionId).orElse(null);
            if(action != null) result.add(descriptor(action));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixPreviewResult preview(String modelId, String diagnosticId, String actionId, QuickFixExecutionRequest request) {
        ExecutionContext context = context(modelId, diagnosticId, actionId, request);
        ActionExecutionResult execution = execute(context);
        CanonicalModelChangeSummary changes = changeAnalyzer.analyze(context.canonical(), execution.document());
        return new QuickFixPreviewResult(diagnosticId, actionId, execution.document(), changes, execution.messages());
    }

    /** Descripción: Actualiza el estado gestionado por HistoryEntry mediante la operación «apply». Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixApplicationResult apply(String modelId, String diagnosticId, String actionId, QuickFixExecutionRequest request) {
        ExecutionContext context = context(modelId, diagnosticId, actionId, request);
        String transactionId = "transaction:" + UUID.randomUUID();
        ModelDocumentService.ModelSnapshot beforeSnapshot = modelService.snapshot(modelId);
        try {
            ActionExecutionResult execution = execute(context);
            ModelDocumentResponse updated = context.adapter().apply(modelId, context.live(), execution.document(), modelService);
            CanonicalModelDocument canonicalAfter = context.adapter().toCanonical(updated);
            CanonicalModelChangeSummary changes = changeAnalyzer.analyze(context.canonical(), canonicalAfter);
            ModelDocumentService.ModelSnapshot afterSnapshot = modelService.snapshot(modelId);
            OrchestratedValidationResult validation = orchestrator.validateLive(modelId, incremental(changes));
            boolean resolved = validation.report().diagnostics().stream().noneMatch(item -> item.id().equals(diagnosticId));
            QuickFixApplicationStatus status = status(changes, execution.messages(), resolved);
            if(execution.undoAvailable() && changes.changed()) pushUndo(modelId, new HistoryEntry(actionId, diagnosticId, beforeSnapshot, afterSnapshot));
            clearRedo(modelId);
            List<String> messages = new ArrayList<>(execution.messages());
            if(!resolved) messages.add("The action was applied, but the diagnostic is still present after revalidation");
            publishApplied(context.live().metamodelId(), modelId, actionId, diagnosticId, status, changes, transactionId);
            return new QuickFixApplicationResult(diagnosticId, actionId, status, resolved, updated, changes, validation,
                    hasUndo(modelId), hasRedo(modelId), messages);
        } catch(RuntimeException exception) {
            RuntimeException failure = rollback(modelId, beforeSnapshot, exception);
            publishFailed(context.live().metamodelId(), modelId, actionId, diagnosticId, failure, transactionId);
            throw failure instanceof QuickFixRuntimeException ? failure : new QuickFixRuntimeException(QuickFixRuntimeException.Reason.EXECUTION_FAILED,
                    message(failure), failure);
        }
    }

    /** Descripción: Ejecuta la operación «undo» correspondiente a la responsabilidad de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixApplicationResult undo(String modelId) {
        ModelDocumentResponse current = modelService.find(modelId);
        ModelDocumentService.ModelSnapshot currentSnapshot = modelService.snapshot(modelId);
        HistoryEntry entry = pop(undoStacks, modelId, QuickFixRuntimeException.Reason.UNDO_NOT_AVAILABLE, "There is no quick fix to undo");
        try {
            MetaModel metamodel = metamodelService.findById(current.metamodelId()).metamodel();
            CanonicalModelRuntimeAdapter adapter = new CanonicalModelRuntimeAdapter(metamodel);
            CanonicalModelDocument before = adapter.toCanonical(current);
            ModelDocumentResponse restored = modelService.restore(modelId, entry.before());
            CanonicalModelDocument after = adapter.toCanonical(restored);
            CanonicalModelChangeSummary changes = changeAnalyzer.analyze(before, after);
            OrchestratedValidationResult validation = orchestrator.validateLive(modelId, incremental(changes));
            push(redoStacks, modelId, entry);
            publishSimple("core.quickfix.undone", current.metamodelId(), modelId, entry, QuickFixApplicationStatus.UNDONE);
            return new QuickFixApplicationResult(entry.diagnosticId(), entry.actionId(), QuickFixApplicationStatus.UNDONE,
                    validation.report().diagnostics().stream().noneMatch(item -> item.id().equals(entry.diagnosticId())), restored, changes,
                    validation, hasUndo(modelId), hasRedo(modelId), List.of("Quick fix was undone"));
        } catch(RuntimeException exception) {
            RuntimeException failure = rollback(modelId, currentSnapshot, exception);
            push(undoStacks, modelId, entry);
            throw failure instanceof QuickFixRuntimeException ? failure : new QuickFixRuntimeException(QuickFixRuntimeException.Reason.EXECUTION_FAILED,
                    "Could not undo quick fix: " + message(failure), failure);
        }
    }

    /** Descripción: Ejecuta la operación «redo» correspondiente a la responsabilidad de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixApplicationResult redo(String modelId) {
        ModelDocumentResponse current = modelService.find(modelId);
        ModelDocumentService.ModelSnapshot currentSnapshot = modelService.snapshot(modelId);
        HistoryEntry entry = pop(redoStacks, modelId, QuickFixRuntimeException.Reason.REDO_NOT_AVAILABLE, "There is no quick fix to redo");
        try {
            MetaModel metamodel = metamodelService.findById(current.metamodelId()).metamodel();
            CanonicalModelRuntimeAdapter adapter = new CanonicalModelRuntimeAdapter(metamodel);
            CanonicalModelDocument before = adapter.toCanonical(current);
            ModelDocumentResponse restored = modelService.restore(modelId, entry.after());
            CanonicalModelDocument after = adapter.toCanonical(restored);
            CanonicalModelChangeSummary changes = changeAnalyzer.analyze(before, after);
            OrchestratedValidationResult validation = orchestrator.validateLive(modelId, incremental(changes));
            push(undoStacks, modelId, entry);
            publishSimple("core.quickfix.redone", current.metamodelId(), modelId, entry, QuickFixApplicationStatus.REDONE);
            return new QuickFixApplicationResult(entry.diagnosticId(), entry.actionId(), QuickFixApplicationStatus.REDONE,
                    validation.report().diagnostics().stream().noneMatch(item -> item.id().equals(entry.diagnosticId())), restored, changes,
                    validation, hasUndo(modelId), hasRedo(modelId), List.of("Quick fix was redone"));
        } catch(RuntimeException exception) {
            RuntimeException failure = rollback(modelId, currentSnapshot, exception);
            push(redoStacks, modelId, entry);
            throw failure instanceof QuickFixRuntimeException ? failure : new QuickFixRuntimeException(QuickFixRuntimeException.Reason.EXECUTION_FAILED,
                    "Could not redo quick fix: " + message(failure), failure);
        }
    }

    /** Descripción: Ejecuta la operación «context» correspondiente a la responsabilidad de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExecutionContext context(String modelId, String diagnosticId, String actionId, QuickFixExecutionRequest request) {
        if(request == null) request = new QuickFixExecutionRequest(Map.of(), List.of());
        ModelValidationDiagnostic diagnostic = diagnostic(modelId, diagnosticId);
        if(!diagnostic.quickFixIds().contains(actionId)) throw new QuickFixRuntimeException(QuickFixRuntimeException.Reason.NOT_EXECUTABLE,
                "Action is not offered by the selected diagnostic: " + actionId);
        ModelDocumentResponse live = modelService.find(modelId);
        MetaModel metamodel = metamodelService.findById(live.metamodelId()).metamodel();
        ActionModel actions = actions(live.metamodelId());
        ActionDefinition action = actions.findAction(actionId).orElseThrow(() -> new QuickFixRuntimeException(QuickFixRuntimeException.Reason.NOT_FOUND,
                "Quick-fix action was not found: " + actionId));
        if(!action.enabled()) throw new QuickFixRuntimeException(QuickFixRuntimeException.Reason.NOT_EXECUTABLE, "Quick-fix action is disabled: " + action.name());
        ExpressionModel expressions = expressions(live.metamodelId());
        OperationBehaviorModel behaviors = behaviorService.findOrEmpty(live.metamodelId());
        CanonicalModelRuntimeAdapter adapter = new CanonicalModelRuntimeAdapter(metamodel);
        CanonicalModelDocument canonical = adapter.toCanonical(live);
        String selfId = diagnostic.targetElementId().isBlank() ? canonical.rootElementIds().stream().findFirst().orElse("") : diagnostic.targetElementId();
        return new ExecutionContext(live, metamodel, expressions, actions, behaviors, adapter, canonical, diagnostic, action,
                new QuickFixExecutionRequest(request.arguments(), request.selectionElementIds()), selfId);
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de HistoryEntry. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ActionExecutionResult execute(ExecutionContext context) {
        try {
            return actionExecutor.execute(context.action().id(), new ActionExecutionRequest(context.canonical(), context.selfId(),
                    context.request().selectionElementIds(), context.request().arguments()), context.metamodel(), context.expressions(),
                    context.actions(), context.behaviors());
        } catch(RuntimeException exception) {
            throw new QuickFixRuntimeException(QuickFixRuntimeException.Reason.EXECUTION_FAILED, message(exception), exception);
        }
    }

    /** Descripción: Ejecuta la operación «diagnostic» correspondiente a la responsabilidad de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ModelValidationDiagnostic diagnostic(String modelId, String diagnosticId) {
        if(diagnosticId == null || diagnosticId.isBlank()) throw new QuickFixRuntimeException(QuickFixRuntimeException.Reason.INVALID_REQUEST,
                "Diagnostic id must not be blank");
        if(orchestrator.latest(modelId).isEmpty()) orchestrator.validateLive(modelId, LiveValidationRequest.full());
        return orchestrator.findDiagnostic(modelId, diagnosticId).orElseThrow(() -> new QuickFixRuntimeException(QuickFixRuntimeException.Reason.NOT_FOUND,
                "Diagnostic was not found in the latest report: " + diagnosticId));
    }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de HistoryEntry. Entradas: action: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private QuickFixDescriptor descriptor(ActionDefinition action) {
        return new QuickFixDescriptor(action.id(), action.name(), action.description(), action.enabled(), action.parameters(),
                action.transactionPolicy(), action.undoPolicy());
    }

    /** Descripción: Ejecuta la operación «expressions» correspondiente a la responsabilidad de HistoryEntry. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel expressions(String metamodelId) {
        return expressionRepository.findById(metamodelId).map(item -> item.expressionModel()).orElseThrow(() -> new QuickFixRuntimeException(
                QuickFixRuntimeException.Reason.NOT_EXECUTABLE, "Expression model was not found: " + metamodelId));
    }

    /** Descripción: Ejecuta la operación «actions» correspondiente a la responsabilidad de HistoryEntry. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModel actions(String metamodelId) {
        return actionRepository.findById(metamodelId).map(StoredCanonicalDocument::document).orElseThrow(() -> new QuickFixRuntimeException(
                QuickFixRuntimeException.Reason.NOT_EXECUTABLE, "Action model was not found: " + metamodelId));
    }

    /** Descripción: Ejecuta la operación «incremental» correspondiente a la responsabilidad de HistoryEntry. Entradas: changes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private LiveValidationRequest incremental(CanonicalModelChangeSummary changes) {
        return new LiveValidationRequest(ValidationRunMode.INCREMENTAL, List.of(), "", changes.validationChangeSet(), Map.of(), ValidationGate.NONE);
    }

    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de HistoryEntry. Entradas: changes: dato de entrada necesario para la operación; messages: dato de entrada necesario para la operación; resolved: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private QuickFixApplicationStatus status(CanonicalModelChangeSummary changes, List<String> messages, boolean resolved) {
        if(!changes.changed()) return QuickFixApplicationStatus.NO_CHANGES;
        if(messages != null && !messages.isEmpty()) return QuickFixApplicationStatus.APPLIED_WITH_ERRORS;
        return resolved ? QuickFixApplicationStatus.APPLIED_RESOLVED : QuickFixApplicationStatus.APPLIED_UNRESOLVED;
    }

    /** Descripción: Ejecuta la operación «pushUndo» correspondiente a la responsabilidad de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo; entry: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void pushUndo(String modelId, HistoryEntry entry) { push(undoStacks, modelId, entry); }
    /** Descripción: Elimina o libera el recurso gestionado por «clearRedo» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void clearRedo(String modelId) { redoStacks.clear(modelId); }
    /** Descripción: Evalúa la condición «hasUndo» sin modificar el estado externo. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasUndo(String modelId) { return undoStacks.has(modelId); }
    /** Descripción: Evalúa la condición «hasRedo» sin modificar el estado externo. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasRedo(String modelId) { return redoStacks.has(modelId); }
    /** Descripción: Ejecuta la operación «push» correspondiente a la responsabilidad de HistoryEntry. Entradas: stacks: dato de entrada necesario para la operación; modelId: identificador que delimita el recurso objetivo; entry: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void push(BoundedHistoryStore<HistoryEntry> stacks, String modelId, HistoryEntry entry) { stacks.push(modelId, entry); }
    /** Descripción: Ejecuta la operación «pop» correspondiente a la responsabilidad de HistoryEntry. Entradas: stacks: dato de entrada necesario para la operación; modelId: identificador que delimita el recurso objetivo; reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HistoryEntry pop(BoundedHistoryStore<HistoryEntry> stacks, String modelId, QuickFixRuntimeException.Reason reason, String message) {
        return stacks.pop(modelId).orElseThrow(() -> new QuickFixRuntimeException(reason, message + " for model: " + modelId));
    }


    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de HistoryEntry. Entradas: modelId: identificador que delimita el recurso objetivo; snapshot: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RuntimeException rollback(String modelId, ModelDocumentService.ModelSnapshot snapshot, RuntimeException failure) {
        try {
            modelService.restore(modelId, snapshot);
            return failure;
        } catch(RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            return new QuickFixRuntimeException(QuickFixRuntimeException.Reason.EXECUTION_FAILED,
                    "Quick-fix transaction failed and its model snapshot could not be restored", failure);
        }
    }

    /** Descripción: Procesa la operación «publishApplied» coordinando las responsabilidades de HistoryEntry. Entradas: metamodelId: identificador que delimita el recurso objetivo; modelId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; status: dato de entrada necesario para la operación; changes: dato de entrada necesario para la operación; transactionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publishApplied(String metamodelId, String modelId, String actionId, String diagnosticId,
            QuickFixApplicationStatus status, CanonicalModelChangeSummary changes, String transactionId) {
        EventPublicationBuffer buffer = new EventPublicationBuffer(eventBus);
        buffer.add(ValidationRuntimeEventFactory.quickFix("core.quickfix.started", metamodelId, modelId, actionId, diagnosticId,
                Map.of("preview", false), transactionId));
        buffer.add(ValidationRuntimeEventFactory.quickFix("core.quickfix.applied", metamodelId, modelId, actionId, diagnosticId,
                Map.of("status", status.name(), "changedElementCount", changes.validationChangeSet().elementIds().size()), transactionId));
        commit(buffer, "Quick-fix events could not be delivered after commit");
    }

    /** Descripción: Procesa la operación «publishFailed» coordinando las responsabilidades de HistoryEntry. Entradas: metamodelId: identificador que delimita el recurso objetivo; modelId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; exception: dato de entrada necesario para la operación; transactionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publishFailed(String metamodelId, String modelId, String actionId, String diagnosticId, RuntimeException exception, String transactionId) {
        EventPublicationBuffer buffer = new EventPublicationBuffer(eventBus);
        buffer.add(ValidationRuntimeEventFactory.quickFix("core.quickfix.failed", metamodelId, modelId, actionId, diagnosticId,
                Map.of("message", message(exception)), transactionId));
        commit(buffer, "Quick-fix failure event could not be delivered after rollback");
    }

    /** Descripción: Procesa la operación «publishSimple» coordinando las responsabilidades de HistoryEntry. Entradas: eventTypeId: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; modelId: identificador que delimita el recurso objetivo; entry: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publishSimple(String eventTypeId, String metamodelId, String modelId, HistoryEntry entry, QuickFixApplicationStatus status) {
        EventPublicationBuffer buffer = new EventPublicationBuffer(eventBus);
        buffer.add(ValidationRuntimeEventFactory.quickFix(eventTypeId, metamodelId, modelId, entry.actionId(), entry.diagnosticId(),
                Map.of("status", status.name()), "transaction:" + UUID.randomUUID()));
        commit(buffer, "Quick-fix compensation event could not be delivered");
    }

    /** Descripción: Ejecuta la operación «commit» correspondiente a la responsabilidad de HistoryEntry. Entradas: buffer: dato de entrada necesario para la operación; warning: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void commit(EventPublicationBuffer buffer, String warning) {
        try { buffer.commit(); }
        catch(RuntimeException exception) { LOGGER.warn(warning, exception); }
    }

    /** Descripción: Ejecuta la operación «message» correspondiente a la responsabilidad de HistoryEntry. Entradas: throwable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String message(Throwable throwable) {
        String value = throwable.getMessage();
        return value == null || value.isBlank() ? throwable.getClass().getSimpleName() : value;
    }

    private record ExecutionContext(ModelDocumentResponse live, MetaModel metamodel, ExpressionModel expressions, ActionModel actions,
            OperationBehaviorModel behaviors, CanonicalModelRuntimeAdapter adapter, CanonicalModelDocument canonical,
            ModelValidationDiagnostic diagnostic, ActionDefinition action, QuickFixExecutionRequest request, String selfId) { }
    private record HistoryEntry(String actionId, String diagnosticId, ModelDocumentService.ModelSnapshot before,
            ModelDocumentService.ModelSnapshot after) { }
}

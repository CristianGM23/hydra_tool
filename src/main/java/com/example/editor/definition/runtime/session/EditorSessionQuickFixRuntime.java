package com.example.editor.definition.runtime.session;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionExecutionRequest;
import com.example.editor.validation.action.ActionExecutionResult;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.CanonicalActionExecutor;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.quickfix.CanonicalModelChangeAnalyzer;
import com.example.editor.validation.quickfix.CanonicalModelChangeSummary;
import com.example.editor.validation.quickfix.QuickFixDescriptor;
import com.example.editor.validation.quickfix.QuickFixExecutionRequest;
import com.example.editor.validation.quickfix.QuickFixPreviewResult;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ModelValidationReport;

/** Resuelve y ejecuta quick fixes directamente sobre la sesión canónica, sin pasar por EMF. */
@Service
public class EditorSessionQuickFixRuntime {
    private final CanonicalMetamodelApplicationService metamodels;
    private final OperationBehaviorModelApplicationService behaviors;
    private final ExpressionModelRepository expressions;
    private final ActionModelRepository actions;
    private final CanonicalActionExecutor executor;
    private final CanonicalModelChangeAnalyzer changes;

    /** Descripción: Inicializa una instancia de EditorSessionQuickFixRuntime con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; behaviors: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación; changes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionQuickFixRuntime(CanonicalMetamodelApplicationService metamodels,
            OperationBehaviorModelApplicationService behaviors, ExpressionModelRepository expressions,
            ActionModelRepository actions, CanonicalActionExecutor executor, CanonicalModelChangeAnalyzer changes) {
        this.metamodels = metamodels;
        this.behaviors = behaviors;
        this.expressions = expressions;
        this.actions = actions;
        this.executor = executor;
        this.changes = changes;
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de EditorSessionQuickFixRuntime. Entradas: session: dato de entrada necesario para la operación; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<QuickFixDescriptor> resolve(EditorModelSession session, String diagnosticId) {
        ModelValidationDiagnostic diagnostic = diagnostic(session, diagnosticId);
        ActionModel actionModel = actionModel(session.metamodelId());
        List<QuickFixDescriptor> result = new ArrayList<>();
        for(String actionId : diagnostic.quickFixIds()) actionModel.findAction(actionId).ifPresent(action -> result.add(descriptor(action)));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de EditorSessionQuickFixRuntime. Entradas: session: dato de entrada necesario para la operación; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixPreviewResult preview(EditorModelSession session, String diagnosticId, String actionId,
            QuickFixExecutionRequest request) {
        ExecutionContext context = context(session, diagnosticId, actionId, request);
        ActionExecutionResult result = execute(context);
        CanonicalModelChangeSummary summary = changes.analyze(session.model(), result.document());
        return new QuickFixPreviewResult(diagnosticId, actionId, result.document(), summary, result.messages());
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de EditorSessionQuickFixRuntime. Entradas: session: dato de entrada necesario para la operación; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixExecutionOutcome execute(EditorModelSession session, String diagnosticId, String actionId,
            QuickFixExecutionRequest request) {
        ExecutionContext context = context(session, diagnosticId, actionId, request);
        ActionExecutionResult result = execute(context);
        return new QuickFixExecutionOutcome(result.document(), result.messages(), result.undoAvailable(),
                changes.analyze(session.model(), result.document()));
    }

    /** Descripción: Ejecuta la operación «context» correspondiente a la responsabilidad de EditorSessionQuickFixRuntime. Entradas: session: dato de entrada necesario para la operación; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExecutionContext context(EditorModelSession session, String diagnosticId, String actionId,
            QuickFixExecutionRequest request) {
        ModelValidationDiagnostic diagnostic = diagnostic(session, diagnosticId);
        if(!diagnostic.quickFixIds().contains(actionId)) throw new EditorSessionApplicationException(
                EditorSessionApplicationException.Reason.NOT_EXECUTABLE, "Quick fix is not offered by the selected diagnostic: " + actionId);
        ActionModel actionModel = actionModel(session.metamodelId());
        ActionDefinition action = actionModel.findAction(actionId).orElseThrow(() -> new EditorSessionApplicationException(
                EditorSessionApplicationException.Reason.NOT_FOUND, "Quick-fix action was not found: " + actionId));
        QuickFixExecutionRequest effective = request == null ? new QuickFixExecutionRequest(null, null) : request;
        String self = diagnostic.targetElementId();
        if(self.isBlank() && !effective.selectionElementIds().isEmpty()) self = effective.selectionElementIds().get(0);
        if(self.isBlank()) self = session.model().rootElementIds().stream().findFirst().orElse("");
        return new ExecutionContext(session.model(), action, actionModel, expressionModel(session.metamodelId()),
                metamodels.findById(session.metamodelId()).metamodel(), behaviors.findOrEmpty(session.metamodelId()), effective, self);
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de EditorSessionQuickFixRuntime. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ActionExecutionResult execute(ExecutionContext context) {
        return executor.execute(context.action().id(), new ActionExecutionRequest(context.document(), context.selfId(),
                context.request().selectionElementIds(), context.request().arguments()), context.metamodel(),
                context.expressions(), context.actions(), context.behaviors());
    }

    /** Descripción: Ejecuta la operación «diagnostic» correspondiente a la responsabilidad de EditorSessionQuickFixRuntime. Entradas: session: dato de entrada necesario para la operación; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ModelValidationDiagnostic diagnostic(EditorModelSession session, String diagnosticId) {
        ModelValidationReport report = session.latestValidationReport();
        if(report == null) throw new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_EXECUTABLE,
                "Validate the editor session before resolving quick fixes");
        return report.diagnostics().stream().filter(item -> item.id().equals(diagnosticId)).findFirst()
                .orElseThrow(() -> new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_FOUND,
                        "Validation diagnostic was not found: " + diagnosticId));
    }

    /** Descripción: Ejecuta la operación «expressionModel» correspondiente a la responsabilidad de EditorSessionQuickFixRuntime. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel expressionModel(String metamodelId) {
        return expressions.findById(metamodelId).map(item -> item.expressionModel())
                .orElseThrow(() -> new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_EXECUTABLE,
                        "Expression model was not found: " + metamodelId));
    }
    /** Descripción: Ejecuta la operación «actionModel» correspondiente a la responsabilidad de EditorSessionQuickFixRuntime. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModel actionModel(String metamodelId) {
        return actions.findById(metamodelId).map(StoredCanonicalDocument::document)
                .orElseThrow(() -> new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_EXECUTABLE,
                        "Action model was not found: " + metamodelId));
    }
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de EditorSessionQuickFixRuntime. Entradas: action: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private QuickFixDescriptor descriptor(ActionDefinition action) {
        return new QuickFixDescriptor(action.id(), action.name(), action.description(), action.enabled(), action.parameters(),
                action.transactionPolicy(), action.undoPolicy());
    }

    public record QuickFixExecutionOutcome(CanonicalModelDocument document, List<String> messages,
            boolean undoAvailable, CanonicalModelChangeSummary changes) {
        public QuickFixExecutionOutcome {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }
    private record ExecutionContext(CanonicalModelDocument document, ActionDefinition action, ActionModel actions, ExpressionModel expressions,
            MetaModel metamodel, OperationBehaviorModel behaviors, QuickFixExecutionRequest request, String selfId) { }
}

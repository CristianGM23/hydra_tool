package com.example.editor.textual.runtime;

import java.util.*;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.textual.source.TextualSemanticSourceMapEntry;
import com.example.editor.validation.action.*;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.quickfix.*;
import com.example.editor.validation.runtime.*;

/**
 * Adapter that reuses the canonical ValidationModel/ActionModel runtime for textual sessions.
 * It never executes provider-specific quick-fix code and never mutates text before a successful
 * canonical execution + reconciliation + reparse + semantic-equivalence check.
 */
@Service
public class TextualQuickFixRuntimeService {
    private final TextualModelSessionService sessions;
    private final ValidationRuntimeApplicationService validations;
    private final CanonicalMetamodelApplicationService metamodels;
    private final OperationBehaviorModelApplicationService behaviors;
    private final ExpressionModelRepository expressions;
    private final ActionModelRepository actions;
    private final CanonicalActionExecutor executor;
    private final CanonicalModelChangeAnalyzer changeAnalyzer;
    private final GrammarCompilationService compilations;
    private final TextualCanonicalReconciliationService reconciler;
    private final TextualSharedLayerRuntimeService sharedLayers;

    /** Descripción: Inicializa una instancia de TextualQuickFixRuntimeService con sus dependencias y datos de entrada. Entradas: sessions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; behaviors: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación; changeAnalyzer: dato de entrada necesario para la operación; compilations: dato de entrada necesario para la operación; reconciler: dato de entrada necesario para la operación; sharedLayers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TextualQuickFixRuntimeService(TextualModelSessionService sessions,ValidationRuntimeApplicationService validations,
            CanonicalMetamodelApplicationService metamodels,OperationBehaviorModelApplicationService behaviors,
            ExpressionModelRepository expressions,ActionModelRepository actions,CanonicalActionExecutor executor,
            CanonicalModelChangeAnalyzer changeAnalyzer,GrammarCompilationService compilations,
            TextualCanonicalReconciliationService reconciler,TextualSharedLayerRuntimeService sharedLayers){
        this.sessions=sessions;this.validations=validations;this.metamodels=metamodels;this.behaviors=behaviors;
        this.expressions=expressions;this.actions=actions;this.executor=executor;this.changeAnalyzer=changeAnalyzer;
        this.compilations=compilations;this.reconciler=reconciler;this.sharedLayers=sharedLayers;
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TextualValidationDiagnosticView> validate(String sessionId){
        TextualModelSession session=requireCurrentValid(sessionId).document();
        ModelValidationReport report=validationReport(session);
        return report.diagnostics().stream().map(item->view(session,item)).toList();
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de ExecutionContext. Entradas: sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<QuickFixDescriptor> resolve(String sessionId,String diagnosticId){
        TextualModelSession session=requireCurrentValid(sessionId).document();
        ModelValidationDiagnostic diagnostic=diagnostic(session,diagnosticId);
        ActionModel model=actionModel(session.lastValidSemanticModel().metamodelId());
        List<QuickFixDescriptor> result=new ArrayList<>();
        for(String actionId:diagnostic.quickFixIds())model.findAction(actionId).ifPresent(action->result.add(descriptor(action)));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de ExecutionContext. Entradas: sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualQuickFixPreviewResult preview(String sessionId,String diagnosticId,String actionId,QuickFixExecutionRequest request){
        StoredCanonicalDocument<TextualModelSession> stored=requireCurrentValid(sessionId);TextualModelSession session=stored.document();
        ExecutionContext context=context(session,diagnosticId,actionId,request);ActionExecutionResult execution=execute(context);
        CanonicalModelChangeSummary changes=changeAnalyzer.analyze(session.lastValidSemanticModel(),execution.document());
        GrammarCompilationSnapshot snapshot=compilations.find(session.grammarSnapshotId());
        TextualReconciliationResult reconciliation=reconciler.reconcile(session.draftRevision()+1,session.lastValidSemanticModel(),execution.document(),session.documents(),session.lastValidSourceMap(),snapshot);
        return new TextualQuickFixPreviewResult(diagnosticId,actionId,execution.document(),changes,reconciliation.safe(),reconciliation.reason(),
                reconciliation.edits(),reconciliation.documents(),execution.messages());
    }

    /** Descripción: Actualiza el estado gestionado por ExecutionContext mediante la operación «apply». Entradas: sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualQuickFixApplicationResult apply(String sessionId,String diagnosticId,String actionId,QuickFixExecutionRequest request,String expectedEtag){
        StoredCanonicalDocument<TextualModelSession> stored=requireCurrentValid(sessionId);TextualModelSession session=stored.document();
        String etag=expectedEtag==null||expectedEtag.isBlank()?stored.etag():expectedEtag;
        ExecutionContext context=context(session,diagnosticId,actionId,request);ActionExecutionResult execution=execute(context);
        GrammarCompilationSnapshot snapshot=compilations.find(session.grammarSnapshotId());
        TextualReconciliationResult reconciliation=reconciler.reconcile(session.draftRevision()+1,session.lastValidSemanticModel(),execution.document(),session.documents(),session.lastValidSourceMap(),snapshot);
        if(!reconciliation.safe())return new TextualQuickFixApplicationResult(diagnosticId,actionId,false,false,reconciliation.reason(),session,stored.etag(),List.of(),execution.messages());

        ModelValidationReport after=validationReport(reconciliation.parsed().semanticModel());
        boolean resolved=after.diagnostics().stream().noneMatch(item->item.id().equals(diagnosticId));
        if(!resolved)return new TextualQuickFixApplicationResult(diagnosticId,actionId,false,false,"The quick-fix candidate did not resolve the selected validation diagnostic",session,stored.etag(),reconciliation.edits(),execution.messages());
        Map<String,String> evidence=Map.of("quickFixActionId",actionId,"quickFixDiagnosticId",diagnosticId,"quickFixReconciled","true","quickFixSemanticEquivalent","true");
        StoredCanonicalDocument<TextualModelSession> committed=sessions.commitReconciled(sessionId,etag,reconciliation,evidence);
        try{sharedLayers.afterQuickFix(committed.document(),diagnosticId,actionId,reconciliation.edits().size());}catch(RuntimeException ignored){/* semantic state is already committed; event delivery is intentionally fault-isolated */}
        return new TextualQuickFixApplicationResult(diagnosticId,actionId,true,true,"",committed.document(),committed.etag(),reconciliation.edits(),execution.messages());
    }

    /** Descripción: Valida las condiciones necesarias para «requireCurrentValid» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<TextualModelSession> requireCurrentValid(String id){
        StoredCanonicalDocument<TextualModelSession> stored=sessions.find(id);TextualModelSession session=stored.document();
        if(!session.draftValid()||session.lastValidSemanticModel()==null||session.lastParsedRevision()!=session.draftRevision())throw new TextualDraftNotReadyException("A shared quick fix requires the current textual draft to parse successfully");
        return stored;
    }
    /** Descripción: Ejecuta la operación «validationReport» correspondiente a la responsabilidad de ExecutionContext. Entradas: session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ModelValidationReport validationReport(TextualModelSession session){return validationReport(session.lastValidSemanticModel());}
    /** Descripción: Ejecuta la operación «validationReport» correspondiente a la responsabilidad de ExecutionContext. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ModelValidationReport validationReport(CanonicalModelDocument model){return validations.validate(model.metamodelId(),new ValidationExecutionRequest(ValidationRunMode.FULL,model,List.of(),"",ValidationChangeSet.empty(),Map.of("source","TEXTUAL_MODEL")));}
    /** Descripción: Ejecuta la operación «diagnostic» correspondiente a la responsabilidad de ExecutionContext. Entradas: session: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ModelValidationDiagnostic diagnostic(TextualModelSession session,String id){return validationReport(session).diagnostics().stream().filter(item->item.id().equals(id)).findFirst().orElseThrow(()->new NoSuchElementException("Validation diagnostic was not found: "+id));}
    /** Descripción: Ejecuta la operación «view» correspondiente a la responsabilidad de ExecutionContext. Entradas: session: dato de entrada necesario para la operación; diagnostic: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TextualValidationDiagnosticView view(TextualModelSession session,ModelValidationDiagnostic diagnostic){
        String elementId=diagnostic.targetElementId(),featureId=diagnostic.targetFeatureId();Optional<TextualSemanticSourceMapEntry> trace=Optional.empty();
        if(!elementId.isBlank()&&!featureId.isBlank())trace=session.lastValidSourceMap().stream().filter(e->e.semanticKind().equals("MODEL_FEATURE")&&e.semanticId().equals(elementId+":"+featureId)).findFirst();
        if(trace.isEmpty()&&!elementId.isBlank())trace=session.lastValidSourceMap().stream().filter(e->e.semanticKind().equals("MODEL_ELEMENT")&&e.semanticId().equals(elementId)).findFirst();
        if(trace.isPresent())return new TextualValidationDiagnosticView(diagnostic,trace.get().documentId(),trace.get().range(),true,diagnostic.quickFixIds(),Map.of("sourceMapKind",trace.get().semanticKind()));
        return new TextualValidationDiagnosticView(diagnostic,"",null,false,diagnostic.quickFixIds(),Map.of("sourceMapKind","NONE"));
    }
    /** Descripción: Ejecuta la operación «context» correspondiente a la responsabilidad de ExecutionContext. Entradas: session: dato de entrada necesario para la operación; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExecutionContext context(TextualModelSession session,String diagnosticId,String actionId,QuickFixExecutionRequest request){
        ModelValidationDiagnostic diagnostic=diagnostic(session,diagnosticId);if(!diagnostic.quickFixIds().contains(actionId))throw new IllegalStateException("Quick fix is not offered by the selected diagnostic: "+actionId);
        CanonicalModelDocument model=session.lastValidSemanticModel();ActionModel actionModel=actionModel(model.metamodelId());ActionDefinition action=actionModel.findAction(actionId).orElseThrow(()->new NoSuchElementException("Quick-fix action was not found: "+actionId));
        QuickFixExecutionRequest effective=request==null?new QuickFixExecutionRequest(null,null):request;String self=diagnostic.targetElementId();if(self.isBlank()&&!effective.selectionElementIds().isEmpty())self=effective.selectionElementIds().get(0);if(self.isBlank())self=model.rootElementIds().stream().findFirst().orElse("");
        MetaModel metamodel=metamodels.findById(model.metamodelId()).metamodel();OperationBehaviorModel behavior=behaviors.findOrEmpty(model.metamodelId());
        return new ExecutionContext(model,action,actionModel,expressionModel(model.metamodelId()),metamodel,behavior,effective,self);
    }
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de ExecutionContext. Entradas: c: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ActionExecutionResult execute(ExecutionContext c){return executor.execute(c.action().id(),new ActionExecutionRequest(c.document(),c.selfId(),c.request().selectionElementIds(),c.request().arguments()),c.metamodel(),c.expressions(),c.actions(),c.behaviors());}
    /** Descripción: Ejecuta la operación «expressionModel» correspondiente a la responsabilidad de ExecutionContext. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel expressionModel(String metamodelId){return expressions.findById(metamodelId).map(item->item.expressionModel()).orElseThrow(()->new IllegalStateException("Expression model was not found: "+metamodelId));}
    /** Descripción: Ejecuta la operación «actionModel» correspondiente a la responsabilidad de ExecutionContext. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModel actionModel(String metamodelId){return actions.findById(metamodelId).map(StoredCanonicalDocument::document).orElseThrow(()->new IllegalStateException("Action model was not found: "+metamodelId));}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de ExecutionContext. Entradas: action: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static QuickFixDescriptor descriptor(ActionDefinition action){return new QuickFixDescriptor(action.id(),action.name(),action.description(),action.enabled(),action.parameters(),action.transactionPolicy(),action.undoPolicy());}
    private record ExecutionContext(CanonicalModelDocument document,ActionDefinition action,ActionModel actions,ExpressionModel expressions,MetaModel metamodel,OperationBehaviorModel behaviors,QuickFixExecutionRequest request,String selfId){}
}

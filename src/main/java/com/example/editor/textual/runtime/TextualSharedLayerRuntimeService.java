package com.example.editor.textual.runtime;

import com.example.editor.product.HydraReleaseVersion;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import com.example.editor.event.*;
import com.example.editor.event.bus.CanonicalEventBus;
import com.example.editor.textual.TextualModelProperties;
import com.example.editor.textual.definition.TextualEditorDefinitionModel;
import com.example.editor.validation.model.*;
import com.example.editor.validation.quickfix.CanonicalModelChangeAnalyzer;
import com.example.editor.validation.quickfix.CanonicalModelChangeSummary;
import com.example.editor.validation.runtime.*;

/** Reuses validation/event-flow/gamification infrastructure after semantic commits. No layer is reimplemented for text. */
@Service
public class TextualSharedLayerRuntimeService {
    private final ValidationRuntimeApplicationService validations;
    private final CanonicalEventBus eventBus;
    private final TextualModelProperties properties;
    private final CanonicalModelChangeAnalyzer changes;
    /** Descripción: Inicializa una instancia de TextualSharedLayerRuntimeService con sus dependencias y datos de entrada. Entradas: validations: dato de entrada necesario para la operación; eventBus: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; changes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TextualSharedLayerRuntimeService(ValidationRuntimeApplicationService validations,CanonicalEventBus eventBus,TextualModelProperties properties,CanonicalModelChangeAnalyzer changes){this.validations=validations;this.eventBus=eventBus;this.properties=properties;this.changes=changes;}

    /** Runs only side-effect-free/blocking checks before a compare-and-set persistence commit. */
    /** Descripción: Valida las condiciones necesarias para «validateBeforeCommit» y rechaza entradas no válidas. Entradas: definition: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualSharedLayerExecutionReport validateBeforeCommit(TextualEditorDefinitionModel definition,TextualModelSession session,CanonicalModelDocument model){
        List<String>warnings=new ArrayList<>();boolean validationExecuted=false,validationSuccessful=true;int validationDiagnostics=0;
        if(properties.isValidateSharedLayersOnSave()&&!definition.validationModelIds().isEmpty()){
            validationExecuted=true;
            try{
                ModelValidationReport report=validations.validate(model.metamodelId(),new ValidationExecutionRequest(ValidationRunMode.FULL,model,List.of(),"",ValidationChangeSet.empty(),Map.of("source","TEXTUAL","sessionId",session.id())));
                validationDiagnostics=report.diagnostics().size();validationSuccessful=report.valid();
            }catch(RuntimeException failure){validationSuccessful=false;warnings.add("Validation layer could not execute: "+safe(failure));}
        }
        return new TextualSharedLayerExecutionReport(validationExecuted,validationSuccessful,validationDiagnostics,0,warnings,Map.of(
                "phase","PRE_COMMIT_VALIDATION","canonicalModelId",model.id(),"keystrokeEvents","false"));
    }

    /**
     * Publishes semantic events only after the repository CAS/ETag commit has succeeded.
     * Delivery is fault-isolated: a secondary event consumer can degrade without rolling back
     * the semantic model that is already durably committed.
     */
    /** Descripción: Ejecuta la operación «afterPersistedCommit» correspondiente a la responsabilidad de TextualSharedLayerRuntimeService. Entradas: definition: dato de entrada necesario para la operación; persistedSession: dato de entrada necesario para la operación; beforeCommitted: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualSharedLayerExecutionReport afterPersistedCommit(TextualEditorDefinitionModel definition,TextualModelSession persistedSession,
            CanonicalModelDocument beforeCommitted,CanonicalModelDocument model){
        List<String>warnings=new ArrayList<>();int events=0;
        if(properties.isPublishSemanticEvents()){
            try{events+=publishSemanticChanges(definition,persistedSession,beforeCommitted,model);}catch(RuntimeException failure){warnings.add("Semantic events were not fully published: "+safe(failure));}
            for(String eventType:definition.eventDefinitionIds()){
                if(eventType==null||eventType.isBlank())continue;
                try{eventBus.publish(event(eventType,EventContextKind.MODEL_EDITING,EventSubjectKind.MODEL,definition,persistedSession,model,model.id(),"",Map.of("source","TEXTUAL_MODEL")));events++;}catch(RuntimeException failure){warnings.add("Event "+eventType+" was not published: "+safe(failure));}
            }
        }
        return new TextualSharedLayerExecutionReport(false,true,0,events,warnings,Map.of(
                "phase","POST_COMMIT_EVENTS","eventFlowReuse","CanonicalEventBus","gamificationReuse","CanonicalEventBus",
                "canonicalModelId",model.id(),"keystrokeEvents","false","semanticDiffEvents","true"));
    }

    /** Descripción: Ejecuta la operación «afterQuickFix» correspondiente a la responsabilidad de TextualSharedLayerRuntimeService. Entradas: session: dato de entrada necesario para la operación; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; editCount: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void afterQuickFix(TextualModelSession session,String diagnosticId,String actionId,int editCount){
        if(!properties.isPublishSemanticEvents()||session.lastValidSemanticModel()==null)return;
        CanonicalModelDocument model=session.lastValidSemanticModel();
        eventBus.publish(event("core.quickfix.applied",EventContextKind.QUICK_FIX,EventSubjectKind.QUICK_FIX,null,session,model,actionId,"",Map.of(
                "diagnosticId",diagnosticId,"actionId",actionId,"textEditCount",editCount,"semanticEquivalent",true,"source","TEXTUAL_MODEL")));
    }

    /** Descripción: Procesa la operación «publishSemanticChanges» coordinando las responsabilidades de TextualSharedLayerRuntimeService. Entradas: definition: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación; before: dato de entrada necesario para la operación; after: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int publishSemanticChanges(TextualEditorDefinitionModel definition,TextualModelSession session,CanonicalModelDocument before,CanonicalModelDocument after){
        int count=0;
        if(before==null){
            for(CanonicalModelElement element:after.elements()){eventBus.publish(event("core.model.element-created",EventContextKind.MODEL_EDITING,EventSubjectKind.MODEL_ELEMENT,definition,session,after,element.id(),element.classId(),Map.of("source","TEXTUAL_MODEL","initialCommit",true)));count++;}
        }else{
            CanonicalModelChangeSummary summary=changes.analyze(before,after);
            Map<String,CanonicalModelElement> oldElements=elements(before),newElements=elements(after);
            for(String id:summary.createdElementIds()){CanonicalModelElement element=newElements.get(id);eventBus.publish(event("core.model.element-created",EventContextKind.MODEL_EDITING,EventSubjectKind.MODEL_ELEMENT,definition,session,after,id,element==null?"":element.classId(),Map.of("source","TEXTUAL_MODEL")));count++;}
            for(String id:summary.deletedElementIds()){CanonicalModelElement element=oldElements.get(id);eventBus.publish(event("core.model.element-deleted",EventContextKind.MODEL_EDITING,EventSubjectKind.MODEL_ELEMENT,definition,session,after,id,element==null?"":element.classId(),Map.of("source","TEXTUAL_MODEL")));count++;}
            LinkedHashSet<String> updated=new LinkedHashSet<>(summary.modifiedElementIds());updated.addAll(summary.validationChangeSet().elementIds());updated.removeAll(summary.createdElementIds());updated.removeAll(summary.deletedElementIds());
            for(String id:updated){CanonicalModelElement element=newElements.get(id);if(element==null)continue;eventBus.publish(event("core.model.element-updated",EventContextKind.MODEL_EDITING,EventSubjectKind.MODEL_ELEMENT,definition,session,after,id,element.classId(),Map.of("source","TEXTUAL_MODEL","changedAttributes",summary.attributeIds(),"changedRelations",summary.relationIds())));count++;}
        }
        eventBus.publish(event("core.model.saved",EventContextKind.MODEL_EDITING,EventSubjectKind.MODEL,definition,session,after,after.id(),"",Map.of("source","TEXTUAL_MODEL","draftRevision",session.draftRevision())));return count+1;
    }

    /** Descripción: Ejecuta la operación «elements» correspondiente a la responsabilidad de TextualSharedLayerRuntimeService. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,CanonicalModelElement> elements(CanonicalModelDocument model){Map<String,CanonicalModelElement> result=new LinkedHashMap<>();model.elements().forEach(e->result.put(e.id(),e));return result;}
    /** Descripción: Ejecuta la operación «event» correspondiente a la responsabilidad de TextualSharedLayerRuntimeService. Entradas: eventType: dato de entrada necesario para la operación; contextKind: dato de entrada necesario para la operación; subjectKind: dato de entrada necesario para la operación; definition: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; subjectId: identificador que delimita el recurso objetivo; typeId: identificador que delimita el recurso objetivo; payload: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static CanonicalEvent event(String eventType,EventContextKind contextKind,EventSubjectKind subjectKind,TextualEditorDefinitionModel definition,TextualModelSession session,CanonicalModelDocument model,String subjectId,String typeId,Map<String,Object>payload){
        String safeType=eventType!=null&&eventType.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,239}")?eventType:"core.model.saved";
        String editorId=definition==null?session.editorDefinitionId():definition.id();
        return new CanonicalEvent("1.0","event:"+UUID.randomUUID(),safeType,"1.0",Instant.now(),new EventSource("TextualModelSessionService","TEXTUAL_MODELS",session.id(),HydraReleaseVersion.VERSION),
                new EventContext(contextKind,"","",model.metamodelId(),model.id(),editorId,session.id()),null,
                new EventSubject(subjectKind,model.id(),subjectId,typeId,"",""),"","textual:"+session.id(),"",payload,
                Map.of("semanticCommit","true","keystrokeEvent","false","grammarId",session.grammarId(),"grammarSnapshotId",session.grammarSnapshotId()));
    }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de TextualSharedLayerRuntimeService. Entradas: failure: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(Throwable failure){String m=failure.getMessage();return m==null||m.isBlank()?failure.getClass().getSimpleName():m;}
}

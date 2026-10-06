package com.example.editor.definition.runtime.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.editor.definition.EdgeCreationTool;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorValidationTrigger;
import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.NodeCreationTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.runtime.EditorDefinitionCompilationResult;
import com.example.editor.definition.runtime.EditorRuntimeApplicationService;
import com.example.editor.definition.runtime.ExecutableEditorPlan;
import com.example.editor.definition.runtime.ExecutableRepresentationPlan;
import com.example.editor.definition.runtime.session.persistence.EditorModelSessionRepository;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionRequest;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionResponse;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolOutcome;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolRuntime;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.operation.runtime.CanonicalModelRuntimeAdapter;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.support.CanonicalMetamodelIndex;
import com.example.editor.validation.quickfix.CanonicalModelChangeAnalyzer;
import com.example.editor.validation.quickfix.QuickFixDescriptor;
import com.example.editor.validation.quickfix.QuickFixExecutionRequest;
import com.example.editor.validation.quickfix.QuickFixPreviewResult;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationChangeSet;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationService;

/** Ejecuta sesiones neutrales sobre un plan compilado de editor configurable. */
@Service
public class EditorModelSessionService {
    private final EditorRuntimeApplicationService runtimes;
    private final CanonicalMetamodelApplicationService metamodels;
    private final EditorModelSessionRepository sessions;
    private final ModelDocumentService emfModels;
    private final EditorSessionProperties properties;
    private final EditorSessionToolRuntime toolRuntime;
    private final ValidationRuntimeApplicationService validationRuntime;
    private final CanonicalModelChangeAnalyzer changeAnalyzer;
    private final EditorSessionQuickFixRuntime quickFixRuntime;
    private final EditorSessionEventPublisher events;
    private final EditorSessionViewAssembler viewAssembler;
    private final EditorSessionModelPolicy modelPolicy;
    private final EditorSessionHistoryStore history;
    private final EditorSessionWorkingCopyStore workingCopies;
    private final EditorSessionPendingEventBuffer pendingEventBuffer;
    /** Descripción: Inicializa una instancia de EditorModelSessionService con sus dependencias y datos de entrada. Entradas: runtimes: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; sessions: dato de entrada necesario para la operación; emfModels: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EditorModelSessionService(EditorRuntimeApplicationService runtimes,
            CanonicalMetamodelApplicationService metamodels, EditorModelSessionRepository sessions,
            ModelDocumentService emfModels, EditorSessionProperties properties) {
        this(runtimes, metamodels, sessions, emfModels, properties, null, null, null, null, null, null, new EditorSessionPendingEventBuffer(properties, null), new EditorSessionModelPolicy(), new EditorSessionHistoryStore(properties), new EditorSessionWorkingCopyStore(properties));
    }

    @Autowired
    /** Descripción: Inicializa una instancia de EditorModelSessionService con sus dependencias y datos de entrada. Entradas: runtimes: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; sessions: dato de entrada necesario para la operación; emfModels: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; toolRuntime: dato de entrada necesario para la operación; validationRuntime: dato de entrada necesario para la operación; changeAnalyzer: dato de entrada necesario para la operación; quickFixRuntime: dato de entrada necesario para la operación; events: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; pendingEventBuffer: dato de entrada necesario para la operación; modelPolicy: dato de entrada necesario para la operación; history: dato de entrada necesario para la operación; workingCopies: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorModelSessionService(EditorRuntimeApplicationService runtimes,
            CanonicalMetamodelApplicationService metamodels, EditorModelSessionRepository sessions,
            ModelDocumentService emfModels, EditorSessionProperties properties, EditorSessionToolRuntime toolRuntime,
            ValidationRuntimeApplicationService validationRuntime, CanonicalModelChangeAnalyzer changeAnalyzer,
            EditorSessionQuickFixRuntime quickFixRuntime, EditorSessionEventPublisher events,
            ExpressionModelApplicationService expressions, EditorSessionPendingEventBuffer pendingEventBuffer, EditorSessionModelPolicy modelPolicy,
            EditorSessionHistoryStore history, EditorSessionWorkingCopyStore workingCopies) {
        this.runtimes = runtimes;
        this.metamodels = metamodels;
        this.sessions = sessions;
        this.emfModels = emfModels;
        this.properties = properties;
        this.toolRuntime = toolRuntime;
        this.validationRuntime = validationRuntime;
        this.changeAnalyzer = changeAnalyzer;
        this.quickFixRuntime = quickFixRuntime;
        this.events = events;
        this.viewAssembler = new EditorSessionViewAssembler(expressions);
        this.modelPolicy = modelPolicy == null ? new EditorSessionModelPolicy() : modelPolicy;
        this.history = history == null ? new EditorSessionHistoryStore(properties) : history;
        this.workingCopies = workingCopies == null ? new EditorSessionWorkingCopyStore(properties) : workingCopies;
        this.pendingEventBuffer = pendingEventBuffer == null ? new EditorSessionPendingEventBuffer(properties, null) : pendingEventBuffer;
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView create(String metamodelId, String editorId, EditorSessionCreateRequest request) {
        requireEnabled();
        if(request == null) throw invalid("Session request is required");
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        String representationId = representationId(plan, request.representationId());
        String sessionId = required(request.sessionId(), "Session id");
        String modelId = required(request.modelId(), "Model id");
        List<CanonicalModelElement> elements = new ArrayList<>();
        List<String> roots = new ArrayList<>();
        if(request.rootClassId() != null && !request.rootClassId().isBlank()) {
            MetaClass rootClass = modelPolicy.concreteClass(metamodel, request.rootClassId());
            modelPolicy.ensureRootCompatible(plan.representationsById().get(representationId), rootClass.id(), metamodel);
            String rootId = newElementId();
            elements.add(new CanonicalModelElement(rootId, rootClass.id(), modelPolicy.normalizedAttributes(rootClass.id(), request.rootAttributes(), metamodel), Map.of()));
            roots.add(rootId);
        }
        CanonicalModelDocument model = new CanonicalModelDocument("1.0", modelId, metamodelId,
                metamodels.findById(metamodelId).etag(), blankTo(request.modelName(), modelId), roots, elements, List.of(), Map.of("editorId", editorId));
        EditorModelSession session = new EditorModelSession("1.0", sessionId, metamodelId, editorId,
                plan.sourceEtag(), representationId, model, 0, Instant.now(), Instant.now(), Map.of());
        session = automaticValidation(session, plan, EditorValidationTrigger.ON_OPEN, ValidationChangeSet.empty(), false);
        try {
            StoredCanonicalDocument<EditorModelSession> stored = sessions.create(session);
            history.clear(sessionId);
            EditorSessionView result = view(stored, plan, metamodel, "");
            publish("core.model.saved", stored.document(), "", "", Map.of("operation", "session-created"));
            return result;
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Ejecuta la operación «importEmf» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView importEmf(String metamodelId, String editorId, EditorSessionImportRequest request) {
        requireEnabled();
        if(request == null) throw invalid("Import request is required");
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        ModelDocumentResponse source = emfModels.find(required(request.sourceModelId(), "Source model id"));
        if(!metamodelId.equals(source.metamodelId())) throw invalid("Source model belongs to another metamodel");
        CanonicalModelDocument imported = new CanonicalModelRuntimeAdapter(metamodel).toCanonical(source);
        CanonicalModelDocument model = new CanonicalModelDocument(imported.schemaVersion(), imported.id(), imported.metamodelId(),
                metamodels.findById(metamodelId).etag(), imported.name(), imported.rootElementIds(), imported.elements(), imported.relations(), imported.annotations());
        String representationId = representationId(plan, request.representationId());
        EditorModelSession session = new EditorModelSession("1.0", required(request.sessionId(), "Session id"), metamodelId,
                editorId, plan.sourceEtag(), representationId, model, 0, Instant.now(), Instant.now(),
                Map.of("importedFrom", source.id(), "importAdapter", "EMF"));
        session = automaticValidation(session, plan, EditorValidationTrigger.ON_OPEN, ValidationChangeSet.empty(), false);
        try {
            StoredCanonicalDocument<EditorModelSession> stored = sessions.create(session);
            history.clear(session.id());
            EditorSessionView result = view(stored, plan, metamodel, "");
            publish("core.import.completed", stored.document(), "", "", Map.of("adapter", "EMF"));
            return result;
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; selectedElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView find(String metamodelId, String editorId, String sessionId, String selectedElementId) {
        StoredCanonicalDocument<EditorModelSession> stored = load(sessionId);
        ensurePath(stored.document(), metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        ensureFresh(stored.document(), plan);
        return view(stored, plan, metamodels.findById(metamodelId).metamodel(), selectedElementId);
    }

    /** Descripción: Ejecuta la operación «switchRepresentation» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView switchRepresentation(String metamodelId, String editorId, String sessionId,
            EditorRepresentationChangeRequest request, String etag) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        String representationId = representationId(plan, request == null ? "" : request.representationId());
        EditorModelSession next = stored.document().openRepresentation(representationId,
                plan.runtimePolicy().maximumOpenRepresentations());
        next = automaticValidation(next, plan, EditorValidationTrigger.ON_REPRESENTATION_CHANGE,
                ValidationChangeSet.empty(), false);
        return saveRuntimeMutation(stored, next, etag, plan, metamodels.findById(metamodelId).metamodel(), "", true);
    }

    /** Descripción: Crea o prepara el resultado de «createNode» dentro de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; toolId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView createNode(String metamodelId, String editorId, String sessionId, String toolId,
            EditorNodeCreationRequest request, String etag) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        ExecutableRepresentationPlan representation = representation(plan, request == null ? "" : request.representationId(), stored.document());
        requireEditable(representation);
        EditorTool raw = representation.toolsById().get(toolId);
        if(!(raw instanceof NodeCreationTool tool)) throw notExecutable("Tool does not create model elements: " + toolId);
        ensureToolAvailable(tool, stored.document(), request == null ? "" : request.containerElementId());
        MetaClass targetClass = modelPolicy.concreteClass(metamodel, tool.targetClassId());
        Map<String,Object> values = new LinkedHashMap<>(tool.defaultValues());
        if(request != null) values.putAll(request.values());
        values = modelPolicy.normalizedAttributes(targetClass.id(), values, metamodel);
        String id = newElementId();
        CanonicalModelDocument current = stored.document().model();
        List<CanonicalModelElement> elements = new ArrayList<>(current.elements());
        elements.add(new CanonicalModelElement(id, targetClass.id(), values, Map.of("createdByTool", tool.id())));
        List<String> roots = new ArrayList<>(current.rootElementIds());
        List<CanonicalModelRelation> relations = new ArrayList<>(current.relations());
        String containerId = request == null ? "" : blankTo(request.containerElementId(), "");
        if(containerId.isBlank()) {
            modelPolicy.ensureRootCompatible(representation, targetClass.id(), metamodel);
            roots.add(id);
        } else {
            CanonicalModelElement container = current.findElement(containerId).orElseThrow(() -> notFound("Container element was not found: " + containerId));
            MetaRelation containment = modelPolicy.resolveContainment(request.containmentRelationId(), container.classId(), targetClass.id(), metamodel);
            modelPolicy.ensureMultiplicity(current, containment, containerId);
            relations.add(new CanonicalModelRelation(newRelationId(), containment.id(), containerId, id, Map.of("createdByTool", tool.id())));
        }
        CanonicalModelDocument next = modelPolicy.copyModel(current, roots, elements, relations);
        EditorSessionView result = saveMutation(stored, stored.document().update(next, representation.id()), etag, plan, metamodel, id);
        publish("core.model.element-created", result.session(), id, targetClass.id(), Map.of("toolId", tool.id()));
        return result;
    }

    /** Descripción: Crea o prepara el resultado de «createEdge» dentro de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; toolId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView createEdge(String metamodelId, String editorId, String sessionId, String toolId,
            EditorEdgeCreationRequest request, String etag) {
        if(request == null) throw invalid("Edge request is required");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        ExecutableRepresentationPlan representation = representation(plan, request.representationId(), stored.document());
        requireEditable(representation);
        EditorTool raw = representation.toolsById().get(toolId);
        if(!(raw instanceof EdgeCreationTool tool)) throw notExecutable("Tool does not create relationships: " + toolId);
        ensureToolAvailable(tool, stored.document(), request.sourceElementId());
        CanonicalModelDocument current = stored.document().model();
        CanonicalModelElement source = current.findElement(required(request.sourceElementId(), "Source element id")).orElseThrow(() -> notFound("Source element was not found"));
        CanonicalModelElement target = current.findElement(required(request.targetElementId(), "Target element id")).orElseThrow(() -> notFound("Target element was not found"));
        if(tool.semanticObjectBacked()) return createSemanticEdge(stored,plan,metamodel,representation,tool,current,source,target,etag);
        MetaRelation relation = new CanonicalMetamodelIndex(metamodel).findRelation(tool.relationId()).orElseThrow(() -> notFound("Relation was not found: " + tool.relationId()));
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        if(!index.isSameOrSubtype(source.classId(), relation.sourceTypeId()) || !index.isSameOrSubtype(target.classId(), relation.targetTypeId())) throw invalid("Selected elements are incompatible with the relationship tool");
        modelPolicy.ensureMultiplicity(current, relation, source.id());
        boolean duplicate = current.relations().stream().anyMatch(item -> item.relationId().equals(relation.id()) && item.sourceElementId().equals(source.id()) && item.targetElementId().equals(target.id()));
        if(duplicate && relation.unique()) throw conflict("The relationship already exists");
        List<CanonicalModelRelation> relations = new ArrayList<>(current.relations());
        String relationInstanceId = newRelationId();
        relations.add(new CanonicalModelRelation(relationInstanceId, relation.id(), source.id(), target.id(), Map.of("createdByTool", tool.id())));
        CanonicalModelDocument next = modelPolicy.copyModel(current, current.rootElementIds(), current.elements(), relations);
        EditorSessionView result = saveMutation(stored, stored.document().update(next, representation.id()), etag, plan, metamodel, source.id());
        publish("core.model.element-updated", result.session(), source.id(), source.classId(), Map.of("relationId", relation.id(), "targetElementId", target.id()));
        return result;
    }

    /** Crea una arista cuyo significado reside en un objeto semántico con referencias source/target. */
    /** Descripción: Crea o prepara el resultado de «createSemanticEdge» dentro de EditorModelSessionService. Entradas: stored: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; tool: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionView createSemanticEdge(StoredCanonicalDocument<EditorModelSession> stored, ExecutableEditorPlan plan,
            MetaModel metamodel, ExecutableRepresentationPlan representation, EdgeCreationTool tool,
            CanonicalModelDocument current, CanonicalModelElement source, CanonicalModelElement target, String etag) {
        var semantic=tool.semanticEdge(); CanonicalMetamodelIndex index=new CanonicalMetamodelIndex(metamodel);
        MetaClass edgeClass=modelPolicy.concreteClass(metamodel,semantic.edgeClassId());
        MetaRelation sourceRelation=index.findRelation(semantic.sourceRelationId()).orElseThrow(()->notFound("Semantic edge source relation was not found: "+semantic.sourceRelationId()));
        MetaRelation targetRelation=index.findRelation(semantic.targetRelationId()).orElseThrow(()->notFound("Semantic edge target relation was not found: "+semantic.targetRelationId()));
        if(!index.isSameOrSubtype(edgeClass.id(),sourceRelation.sourceTypeId())||!index.isSameOrSubtype(edgeClass.id(),targetRelation.sourceTypeId())) throw invalid("Semantic edge endpoint relations are not owned by the edge object class");
        if(!index.isSameOrSubtype(source.classId(),sourceRelation.targetTypeId())||!index.isSameOrSubtype(target.classId(),targetRelation.targetTypeId())) throw invalid("Selected elements are incompatible with the semantic edge endpoints");
        String edgeId=newElementId();
        Map<String,Object> attributes=modelPolicy.normalizedAttributes(edgeClass.id(),semantic.defaultValues(),metamodel);
        List<CanonicalModelElement> elements=new ArrayList<>(current.elements());
        elements.add(new CanonicalModelElement(edgeId,edgeClass.id(),attributes,Map.of("createdByTool",tool.id(),"semanticEdge","true")));
        List<String> roots=new ArrayList<>(current.rootElementIds()); List<CanonicalModelRelation> relations=new ArrayList<>(current.relations());
        if(semantic.containmentRelationId().isBlank()) roots.add(edgeId);
        else {
            MetaRelation containment=index.findRelation(semantic.containmentRelationId()).orElseThrow(()->notFound("Semantic edge containment relation was not found: "+semantic.containmentRelationId()));
            if(containment.kind()!=MetaRelationKind.CONTAINMENT||!index.isSameOrSubtype(edgeClass.id(),containment.targetTypeId())) throw invalid("Semantic edge containment relation is incompatible with the edge object class");
            String containerId=resolveSemanticEdgeContainer(current,source,target,containment,index);
            modelPolicy.ensureMultiplicity(current,containment,containerId);
            relations.add(new CanonicalModelRelation(newRelationId(),containment.id(),containerId,edgeId,Map.of("createdByTool",tool.id())));
        }
        modelPolicy.ensureMultiplicity(current,sourceRelation,edgeId); modelPolicy.ensureMultiplicity(current,targetRelation,edgeId);
        relations.add(new CanonicalModelRelation(newRelationId(),sourceRelation.id(),edgeId,source.id(),Map.of("createdByTool",tool.id(),"semanticEdgeEndpoint","source")));
        relations.add(new CanonicalModelRelation(newRelationId(),targetRelation.id(),edgeId,target.id(),Map.of("createdByTool",tool.id(),"semanticEdgeEndpoint","target")));
        CanonicalModelDocument next=modelPolicy.copyModel(current,roots,elements,relations);
        EditorSessionView result=saveMutation(stored,stored.document().update(next,representation.id()),etag,plan,metamodel,source.id());
        publish("core.model.element-created",result.session(),edgeId,edgeClass.id(),Map.of("toolId",tool.id(),"semanticEdge","true","sourceElementId",source.id(),"targetElementId",target.id()));
        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «resolveSemanticEdgeContainer» dentro de EditorModelSessionService. Entradas: model: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; containment: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolveSemanticEdgeContainer(CanonicalModelDocument model, CanonicalModelElement source,
            CanonicalModelElement target, MetaRelation containment, CanonicalMetamodelIndex index) {
        Set<String> sourceContainers=model.relations().stream().filter(item->item.targetElementId().equals(source.id()))
                .filter(item->index.findRelation(item.relationId()).map(rel->rel.kind()==MetaRelationKind.CONTAINMENT).orElse(false))
                .map(CanonicalModelRelation::sourceElementId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> targetContainers=model.relations().stream().filter(item->item.targetElementId().equals(target.id()))
                .filter(item->index.findRelation(item.relationId()).map(rel->rel.kind()==MetaRelationKind.CONTAINMENT).orElse(false))
                .map(CanonicalModelRelation::sourceElementId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        sourceContainers.retainAll(targetContainers);
        List<String> compatible=sourceContainers.stream().filter(id->model.findElement(id)
                .map(element->index.isSameOrSubtype(element.classId(),containment.sourceTypeId())).orElse(false)).toList();
        if(compatible.size()!=1) throw invalid(compatible.isEmpty()
                ? "Could not find the common semantic container required to create the edge object"
                : "Several common containers can own the semantic edge object; the operation is ambiguous");
        return compatible.get(0);
    }

    /** Descripción: Actualiza el estado gestionado por EditorModelSessionService mediante la operación «updateElement». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView updateElement(String metamodelId, String editorId, String sessionId, String elementId,
            EditorElementUpdateRequest request, String etag) {
        if(request == null) throw invalid("Element update is required");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        ExecutableRepresentationPlan representation = representation(plan, request.representationId(), stored.document());
        requireEditable(representation);
        CanonicalModelDocument current = stored.document().model();
        CanonicalModelElement element = current.findElement(elementId).orElseThrow(() -> notFound("Element was not found: " + elementId));
        FormDefinition form = request.formId() == null || request.formId().isBlank()
                ? modelPolicy.formFor(element.classId(), plan, new CanonicalMetamodelIndex(metamodel))
                : plan.formsById().get(request.formId());
        if(request.formId() != null && !request.formId().isBlank() && form == null) throw notFound("Generated form was not found: " + request.formId());
        if(form != null && !new CanonicalMetamodelIndex(metamodel).isSameOrSubtype(element.classId(), form.targetClassId())) throw invalid("Generated form is incompatible with the selected element");
        Set<String> allowed = new LinkedHashSet<>();
        Set<String> readOnly = new LinkedHashSet<>();
        if(form != null) form.sections().forEach(section -> section.fields().forEach(field -> {
            allowed.add(field.featureId());
            if(field.readOnly()) readOnly.add(field.featureId());
        }));
        request.values().keySet().forEach(id -> {
            if(form != null && !allowed.contains(id)) throw invalid("Field is not exposed by the generated form: " + id);
            if(readOnly.contains(id)) throw invalid("Field is read only: " + id);
        });
        Map<String,Object> attributes = new LinkedHashMap<>(element.attributes());
        attributes.putAll(modelPolicy.normalizedAttributes(element.classId(), request.values(), metamodel));
        modelPolicy.ensureRequiredFormValues(form, attributes);
        List<CanonicalModelElement> elements = current.elements().stream().map(item -> item.id().equals(elementId) ? new CanonicalModelElement(item.id(), item.classId(), attributes, item.annotations()) : item).toList();
        CanonicalModelDocument next = modelPolicy.copyModel(current, current.rootElementIds(), elements, current.relations());
        EditorSessionView result = saveMutation(stored, stored.document().update(next, representation.id()), etag, plan, metamodel, elementId);
        publish("core.model.element-updated", result.session(), elementId, element.classId(), Map.of("changedFeatureCount", request.values().size()));
        return result;
    }

    /** Descripción: Cambia únicamente la apariencia visual compatible de una instancia. Entradas: metamodelId, editorId, sessionId, elementId, request y etag. Salida: devuelve la sesión actualizada sin modificar el modelo semántico. */ public EditorSessionView changeElementAppearance(String metamodelId,String editorId,String sessionId,String elementId,EditorElementAppearanceRequest request,String etag) {
        if(request==null) throw invalid("Appearance request is required");
        StoredCanonicalDocument<EditorModelSession> stored=checked(sessionId,metamodelId,editorId);
        ExecutableEditorPlan plan=plan(metamodelId,editorId);
        MetaModel metamodel=metamodels.findById(metamodelId).metamodel();
        ExecutableRepresentationPlan representation=representation(plan,request.representationId(),stored.document());
        CanonicalModelElement element=stored.document().model().findElement(elementId).orElseThrow(()->notFound("Element was not found: "+elementId));
        String mappingId=required(request.mappingId(),"Mapping id");
        List<NodeMapping> compatible=EditorSessionViewAssembler.compatibleMappings(element.classId(),representation,new com.example.editor.validation.support.CanonicalMetamodelIndex(metamodel));
        if(compatible.stream().noneMatch(mapping->mapping.id().equals(mappingId))) throw invalid("The selected appearance is not compatible with this element, representation and metamodel");
        EditorModelSession next=stored.document().withAnnotation(EditorSessionViewAssembler.appearanceKey(representation.id(),elementId),mappingId);
        return saveRuntimeMutation(stored,next,etag,plan,metamodel,elementId,false);
    }

    /** Descripción: Ejecuta la operación «reparentElement» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView reparentElement(String metamodelId, String editorId, String sessionId, String elementId,
            EditorElementContainmentRequest request, String etag) {
        if(request == null) throw invalid("Containment change is required");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        ExecutableRepresentationPlan representation = representation(plan, request.representationId(), stored.document());
        requireEditable(representation);
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        CanonicalModelDocument current = stored.document().model();
        CanonicalModelElement element = current.findElement(elementId).orElseThrow(() -> notFound("Element was not found: " + elementId));
        List<CanonicalModelRelation> relations = current.relations().stream().filter(item -> {
            if(!item.targetElementId().equals(elementId)) return true;
            MetaRelation relation = index.findRelation(item.relationId()).orElse(null);
            return relation == null || relation.kind() != MetaRelationKind.CONTAINMENT;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Set<String> roots = new LinkedHashSet<>(current.rootElementIds());
        String containerId = blankTo(request.containerElementId(), "");
        if(containerId.isBlank()) {
            roots.add(elementId);
        } else {
            if(containerId.equals(elementId)) throw conflict("An element cannot contain itself");
            CanonicalModelElement container = current.findElement(containerId)
                    .orElseThrow(() -> notFound("Container element was not found: " + containerId));
            if(modelPolicy.containmentClosure(current, elementId, metamodel).contains(containerId))
                throw conflict("The requested containment would create a cycle");
            MetaRelation containment = modelPolicy.resolveContainment(request.containmentRelationId(), container.classId(), element.classId(), metamodel);
            NodeMapping containerMapping = modelPolicy.mappingFor(container.classId(), representation, index);
            if(containerMapping == null || !containerMapping.container())
                throw conflict("The target element is not represented as a visual container");
            if(!containerMapping.containmentRelationIds().contains(containment.id()))
                throw conflict("The visual container does not accept the requested containment relation");
            CanonicalModelDocument withoutPrevious = modelPolicy.copyModel(current, List.copyOf(roots), current.elements(), relations);
            modelPolicy.ensureMultiplicity(withoutPrevious, containment, containerId);
            relations.add(new CanonicalModelRelation(newRelationId(), containment.id(), containerId, elementId,
                    Map.of("changedBy", "visual-drag-and-drop")));
            roots.remove(elementId);
        }
        CanonicalModelDocument next = modelPolicy.copyModel(current, List.copyOf(roots), current.elements(), relations);
        EditorSessionView result = saveMutation(stored, stored.document().update(next, representation.id()), etag, plan, metamodel, elementId);
        publish("core.model.element-reparented", result.session(), elementId, element.classId(),
                Map.of("containerElementId", containerId, "containmentRelationId", blankTo(request.containmentRelationId(), "")));
        return result;
    }

    /** Descripción: Obtiene el modelo actual de una sesión para generación tras validar metamodelo y editor. Entradas: metamodelId, editorId y sessionId. Salida: documento canónico del modelo. */ public CanonicalModelDocument modelForCodeGeneration(String metamodelId, String editorId, String sessionId) { return checked(sessionId, metamodelId, editorId).document().model(); }
    /** Elimina una selección completa mediante una sola mutación y un único control ETag. Los descendientes por contención se incluyen una sola vez. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElements» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView deleteElements(String metamodelId, String editorId, String sessionId, EditorElementSelectionRequest request, String etag) {
        if(request == null || request.elementIds().isEmpty()) throw new IllegalArgumentException("At least one model element must be selected");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        CanonicalModelDocument current = stored.document().model();
        Set<String> deleted = new LinkedHashSet<>();
        for(String elementId : request.elementIds()) {
            if(current.findElement(elementId).isEmpty()) throw notFound("Element was not found: " + elementId);
            deleted.addAll(modelPolicy.containmentClosure(current, elementId, metamodel));
        }
        long rootsAfter = current.rootElementIds().stream().filter(id -> !deleted.contains(id)).count();
        if(rootsAfter == 0 && !current.elements().isEmpty()) throw conflict("The last root element cannot be deleted");
        List<String> roots = current.rootElementIds().stream().filter(id -> !deleted.contains(id)).toList();
        List<CanonicalModelElement> elements = current.elements().stream().filter(item -> !deleted.contains(item.id())).toList();
        List<CanonicalModelRelation> relations = current.relations().stream().filter(item -> !deleted.contains(item.sourceElementId()) && !deleted.contains(item.targetElementId())).toList();
        EditorSessionView result = saveMutation(stored, stored.document().update(modelPolicy.copyModel(current, roots, elements, relations), null), etag, plan, metamodel, "");
        publish("core.model.element-deleted", result.session(), request.elementIds().get(0), "", Map.of("deletedElementCount", deleted.size(), "selectionCount", request.elementIds().size()));
        return result;
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteElement» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView deleteElement(String metamodelId, String editorId, String sessionId, String elementId, String etag) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        CanonicalModelDocument current = stored.document().model();
        if(current.findElement(elementId).isEmpty()) throw notFound("Element was not found: " + elementId);
        Set<String> deleted = modelPolicy.containmentClosure(current, elementId, metamodel);
        long rootsAfter = current.rootElementIds().stream().filter(id -> !deleted.contains(id)).count();
        if(rootsAfter == 0 && !current.elements().isEmpty()) throw conflict("The last root element cannot be deleted");
        List<String> roots = current.rootElementIds().stream().filter(id -> !deleted.contains(id)).toList();
        List<CanonicalModelElement> elements = current.elements().stream().filter(item -> !deleted.contains(item.id())).toList();
        List<CanonicalModelRelation> relations = current.relations().stream().filter(item -> !deleted.contains(item.sourceElementId()) && !deleted.contains(item.targetElementId())).toList();
        EditorSessionView result = saveMutation(stored, stored.document().update(modelPolicy.copyModel(current, roots, elements, relations), null), etag, plan, metamodel, "");
        publish("core.model.element-deleted", result.session(), elementId, "", Map.of("deletedElementCount", deleted.size()));
        return result;
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteRelation» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; relationInstanceId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView deleteRelation(String metamodelId, String editorId, String sessionId, String relationInstanceId, String etag) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        CanonicalModelDocument current = stored.document().model();
        CanonicalModelRelation removed = current.relations().stream().filter(item -> item.id().equals(relationInstanceId)).findFirst().orElse(null);
        if(removed==null) return deleteSemanticEdgeObject(stored,plan,metamodel,current,relationInstanceId,etag);
        MetaRelation relation = new CanonicalMetamodelIndex(metamodel).findRelation(removed.relationId()).orElseThrow();
        if(relation.kind() == MetaRelationKind.CONTAINMENT) throw conflict("Containment relationships are removed by deleting the contained element");
        List<CanonicalModelRelation> relations = current.relations().stream().filter(item -> !item.id().equals(relationInstanceId)).toList();
        EditorSessionView result = saveMutation(stored, stored.document().update(modelPolicy.copyModel(current, current.rootElementIds(), current.elements(), relations), null), etag, plan, metamodel, removed.sourceElementId());
        publish("core.model.element-updated", result.session(), removed.sourceElementId(), "", Map.of("removedRelationId", relation.id()));
        return result;
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteSemanticEdgeObject» de forma controlada. Entradas: stored: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación; edgeElementId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionView deleteSemanticEdgeObject(StoredCanonicalDocument<EditorModelSession> stored,
            ExecutableEditorPlan plan, MetaModel metamodel, CanonicalModelDocument current, String edgeElementId, String etag) {
        CanonicalModelElement edge=current.findElement(edgeElementId).orElseThrow(()->notFound("Relationship was not found: "+edgeElementId));
        CanonicalMetamodelIndex index=new CanonicalMetamodelIndex(metamodel);
        boolean declared=plan.representationsById().values().stream().flatMap(rep->rep.edgeMappingsBySemanticClassId().entrySet().stream())
                .anyMatch(entry->index.isSameOrSubtype(edge.classId(),entry.getKey()));
        if(!declared) throw notFound("Relationship was not found: "+edgeElementId);
        Set<String> deleted=modelPolicy.containmentClosure(current,edgeElementId,metamodel);
        List<String> roots=current.rootElementIds().stream().filter(id->!deleted.contains(id)).toList();
        List<CanonicalModelElement> elements=current.elements().stream().filter(item->!deleted.contains(item.id())).toList();
        List<CanonicalModelRelation> relations=current.relations().stream().filter(item->!deleted.contains(item.sourceElementId())&&!deleted.contains(item.targetElementId())).toList();
        EditorSessionView result=saveMutation(stored,stored.document().update(modelPolicy.copyModel(current,roots,elements,relations),null),etag,plan,metamodel,"");
        publish("core.model.element-deleted",result.session(),edgeElementId,edge.classId(),Map.of("semanticEdge","true","deletedElementCount",deleted.size()));
        return result;
    }

    /** Descripción: Procesa la operación «executeTool» coordinando las responsabilidades de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; toolId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionToolExecutionResponse executeTool(String metamodelId, String editorId, String sessionId,
            String toolId, EditorSessionToolExecutionRequest request, String expectedEtag) {
        if(toolRuntime == null) throw notExecutable("Neutral operation, action and validation runtime is not available");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ensureExpectedEtag(stored, expectedEtag);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        ExecutableRepresentationPlan representation = representation(plan,
                request == null ? "" : request.representationId(), stored.document());
        EditorTool tool = representation.toolsById().get(toolId);
        if(tool == null) throw notFound("Tool was not found in the active representation: " + toolId);
        if(tool instanceof NodeCreationTool || tool instanceof EdgeCreationTool)
            throw invalid("Creation tools must use their dedicated node or relationship endpoint");
        ensureToolAvailable(tool, stored.document(), request == null ? "" : request.targetElementId());
        EditorSessionToolExecutionRequest effective = request == null
                ? new EditorSessionToolExecutionRequest(representation.id(), "", List.of(), Map.of()) : request;
        EditorSessionToolOutcome outcome;
        try { outcome = toolRuntime.execute(tool, metamodelId, stored.document().model(), effective); }
        catch(IllegalArgumentException exception) { throw invalid(exception.getMessage()); }
        EditorSessionView nextView;
        if(outcome.changed()) {
            EditorModelSession next = stored.document().update(outcome.document(), null);
            nextView = saveMutation(stored, next, expectedEtag, plan,
                    metamodels.findById(metamodelId).metamodel(), effective.targetElementId());
        } else {
            nextView = view(stored, plan, metamodels.findById(metamodelId).metamodel(), effective.targetElementId());
        }
        return new EditorSessionToolExecutionResponse(nextView, outcome.returnValue(), outcome.messages(), outcome.validationReport());
    }

    /** Descripción: Ejecuta la operación «undo» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView undo(String metamodelId, String editorId, String sessionId, String expectedEtag) {
        StoredCanonicalDocument<EditorModelSession> current = checked(sessionId, metamodelId, editorId);
        ensureExpectedEtag(current, expectedEtag);
        EditorModelSession previous = history.takeUndo(sessionId);
        EditorModelSession restored = restoreSnapshot(current.document(), previous);
        try {
            ExecutableEditorPlan plan = plan(metamodelId, editorId);
            StoredCanonicalDocument<EditorModelSession> stored = storeState(current, restored, plan);
            history.recordRedo(sessionId,current.document());
            return view(stored, plan, metamodels.findById(metamodelId).metamodel(), "");
        } catch(RuntimeException exception) {
            history.restoreUndo(sessionId,previous);
            throw exception;
        }
    }

    /** Descripción: Ejecuta la operación «redo» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView redo(String metamodelId, String editorId, String sessionId, String expectedEtag) {
        StoredCanonicalDocument<EditorModelSession> current = checked(sessionId, metamodelId, editorId);
        ensureExpectedEtag(current, expectedEtag);
        EditorModelSession next = history.takeRedo(sessionId);
        EditorModelSession restored = restoreSnapshot(current.document(), next);
        try {
            ExecutableEditorPlan plan = plan(metamodelId, editorId);
            StoredCanonicalDocument<EditorModelSession> stored = storeState(current, restored, plan);
            history.recordUndoFromRedo(sessionId,current.document());
            return view(stored, plan, metamodels.findById(metamodelId).metamodel(), "");
        } catch(RuntimeException exception) {
            history.restoreRedo(sessionId,next);
            throw exception;
        }
    }

    /** Descripción: Ejecuta la operación «renderRepresentation» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; representationId: identificador que delimita el recurso objetivo; selectedElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView renderRepresentation(String metamodelId, String editorId, String sessionId,
            String representationId, String selectedElementId) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        representation(plan, representationId);
        return view(stored, plan, metamodels.findById(metamodelId).metamodel(), selectedElementId, representationId);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «closeRepresentation» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; representationId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView closeRepresentation(String metamodelId, String editorId, String sessionId,
            String representationId, String etag) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        representation(plan, representationId);
        return saveRuntimeMutation(stored, stored.document().closeRepresentation(representationId), etag, plan,
                metamodels.findById(metamodelId).metamodel(), "", false);
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView validate(String metamodelId, String editorId, String sessionId,
            ValidationRunMode mode, String etag) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ensureExpectedEtag(stored, etag);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        if(!plan.capabilityManifest().supports(EditorCapability.VALIDATION_RUNTIME))
            throw notExecutable("The editor does not enable VALIDATION_RUNTIME");
        EditorModelSession validated = validateNow(stored.document(), mode == null ? ValidationRunMode.FULL : mode,
                ValidationChangeSet.empty(), true);
        EditorSessionView result = saveRuntimeMutation(stored, validated, etag, plan,
                metamodels.findById(metamodelId).metamodel(), "", false);
        publish("core.validation.execution-completed", result.session(), "", "",
                Map.of("diagnosticCount", result.validationReport() == null ? 0 : result.validationReport().diagnostics().size()));
        return result;
    }

    /** Descripción: Ejecuta la operación «quickFixes» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<QuickFixDescriptor> quickFixes(String metamodelId, String editorId, String sessionId, String diagnosticId) {
        if(quickFixRuntime == null) throw notExecutable("Neutral quick-fix runtime is not available");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        return quickFixRuntime.resolve(stored.document(), diagnosticId);
    }

    /** Descripción: Ejecuta la operación «previewQuickFix» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixPreviewResult previewQuickFix(String metamodelId, String editorId, String sessionId,
            String diagnosticId, String actionId, QuickFixExecutionRequest request) {
        if(quickFixRuntime == null) throw notExecutable("Neutral quick-fix runtime is not available");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        return quickFixRuntime.preview(stored.document(), diagnosticId, actionId, request);
    }

    /** Descripción: Actualiza el estado gestionado por EditorModelSessionService mediante la operación «applyQuickFix». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionQuickFixResult applyQuickFix(String metamodelId, String editorId, String sessionId,
            String diagnosticId, String actionId, QuickFixExecutionRequest request, String etag) {
        if(quickFixRuntime == null) throw notExecutable("Neutral quick-fix runtime is not available");
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        ensureExpectedEtag(stored, etag);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        if(!plan.capabilityManifest().supports(EditorCapability.QUICK_FIX_RUNTIME))
            throw notExecutable("The editor does not enable QUICK_FIX_RUNTIME");
        EditorSessionQuickFixRuntime.QuickFixExecutionOutcome outcome = quickFixRuntime.execute(stored.document(), diagnosticId, actionId, request);
        EditorModelSession next = stored.document().update(outcome.document(), null);
        next = validateNow(next, ValidationRunMode.INCREMENTAL, outcome.changes().validationChangeSet(), true);
        EditorSessionView view = saveRuntimeMutation(stored, next, etag, plan,
                metamodels.findById(metamodelId).metamodel(), "", outcome.undoAvailable());
        boolean resolved = view.validationReport() == null || view.validationReport().diagnostics().stream()
                .noneMatch(item -> item.id().equals(diagnosticId));
        publish("core.quickfix.applied", view.session(), "", "", Map.of("diagnosticId", diagnosticId,
                "actionId", actionId, "resolved", resolved));
        return new EditorSessionQuickFixResult(view, diagnosticId, actionId, resolved, outcome.messages());
    }

    /** Descripción: Actualiza el estado gestionado por EditorModelSessionService mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView save(String metamodelId, String editorId, String sessionId, String etag) {
        StoredCanonicalDocument<EditorModelSession> current = checked(sessionId, metamodelId, editorId);
        ensureExpectedEtag(current, etag);
        PendingEditorSession pending = workingCopies.find(sessionId);
        ExecutableEditorPlan plan = plan(metamodelId, editorId);
        if(pending == null) return view(current, plan, metamodels.findById(metamodelId).metamodel(), "");
        try {
            StoredCanonicalDocument<EditorModelSession> stored = sessions.replace(sessionId, pending.document(), pending.baseEtag());
            workingCopies.remove(sessionId);
            flushPendingEvents(sessionId);
            return view(stored, plan, metamodels.findById(metamodelId).metamodel(), "");
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «discardWorkingCopy» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionView discardWorkingCopy(String metamodelId, String editorId, String sessionId) {
        workingCopies.remove(sessionId);
        pendingEventBuffer.discard(sessionId);
        try {
            StoredCanonicalDocument<EditorModelSession> stored = sessions.find(sessionId)
                    .orElseThrow(() -> notFound("Editor session was not found: " + sessionId));
            ensurePath(stored.document(), metamodelId, editorId);
            history.clear(sessionId);
            return view(stored, plan(metamodelId, editorId), metamodels.findById(metamodelId).metamodel(), "");
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String editorId, String sessionId, String etag) {
        StoredCanonicalDocument<EditorModelSession> stored = checked(sessionId, metamodelId, editorId);
        PendingEditorSession pending = workingCopies.remove(sessionId);
        pendingEventBuffer.discard(sessionId);
        String persistedEtag = pending == null ? stored.etag() : pending.baseEtag();
        try { sessions.delete(sessionId, persistedEtag); history.clear(sessionId); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Actualiza el estado gestionado por EditorModelSessionService mediante la operación «saveMutation». Entradas: before: dato de entrada necesario para la operación; next: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionView saveMutation(StoredCanonicalDocument<EditorModelSession> before, EditorModelSession next,
            String expectedEtag, ExecutableEditorPlan plan, MetaModel metamodel, String selectedElementId) {
        ValidationChangeSet changeSet = changeAnalyzer == null ? ValidationChangeSet.empty()
                : changeAnalyzer.analyze(before.document().model(), next.model()).validationChangeSet();
        EditorModelSession validated = automaticValidation(next, plan, EditorValidationTrigger.ON_CHANGE, changeSet, false);
        return saveRuntimeMutation(before, validated, expectedEtag, plan, metamodel, selectedElementId, true);
    }

    /** Descripción: Actualiza el estado gestionado por EditorModelSessionService mediante la operación «saveRuntimeMutation». Entradas: before: dato de entrada necesario para la operación; next: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo; recordUndo: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionView saveRuntimeMutation(StoredCanonicalDocument<EditorModelSession> before, EditorModelSession next,
            String expectedEtag, ExecutableEditorPlan plan, MetaModel metamodel, String selectedElementId, boolean recordUndo) {
        ensureFresh(before.document(), plan);
        ensureExpectedEtag(before, expectedEtag);
        StoredCanonicalDocument<EditorModelSession> stored = storeState(before, next, plan);
        if(recordUndo) {
            history.recordMutation(before.document().id(),before.document());
        }
        return view(stored, plan, metamodel, selectedElementId);
    }

    /** Descripción: Ejecuta la operación «view» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: stored: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionView view(StoredCanonicalDocument<EditorModelSession> stored, ExecutableEditorPlan plan,
            MetaModel metamodel, String selectedElementId) {
        return view(stored, plan, metamodel, selectedElementId, stored.document().activeRepresentationId());
    }

    /** Descripción: Ejecuta la operación «view» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: stored: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo; representationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EditorSessionView view(StoredCanonicalDocument<EditorModelSession> stored, ExecutableEditorPlan plan,
            MetaModel metamodel, String selectedElementId, String representationId) {
        ExecutableRepresentationPlan representation=representation(plan,representationId);
        return viewAssembler.assemble(stored,plan,metamodel,representation,selectedElementId,history.undoAvailable(stored.document().id()),history.redoAvailable(stored.document().id()));
    }

    /** Descripción: Valida las condiciones necesarias para «ensureToolAvailable» y rechaza entradas no válidas. Entradas: tool: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureToolAvailable(EditorTool tool, EditorModelSession session,
            String selectedElementId) {
        EditorRuntimeToolState state = viewAssembler.toolState(tool, session, selectedElementId);
        if(!state.visible()) throw notExecutable("Tool is not visible in the current context: " + tool.id());
        if(!state.enabled()) throw notExecutable("Tool is disabled in the current context: " + tool.id());
    }



    /** Descripción: Valida las condiciones necesarias para «checked» y rechaza entradas no válidas. Entradas: sessionId: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<EditorModelSession> checked(String sessionId, String metamodelId, String editorId) {
        StoredCanonicalDocument<EditorModelSession> stored = load(sessionId);
        ensurePath(stored.document(), metamodelId, editorId);
        ensureFresh(stored.document(), plan(metamodelId, editorId));
        return stored;
    }
    /** Descripción: Obtiene la información correspondiente a «load» dentro de EditorModelSessionService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<EditorModelSession> load(String id) {
        requireEnabled();
        String sessionId = required(id, "Session id");
        PendingEditorSession pending = workingCopies.find(sessionId);
        if(pending != null) return new StoredCanonicalDocument<>(pending.document(), pending.etag(), pending.path());
        try { return sessions.find(sessionId).orElseThrow(() -> notFound("Editor session was not found: " + sessionId)); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Valida las condiciones necesarias para «ensureExpectedEtag» y rechaza entradas no válidas. Entradas: current: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureExpectedEtag(StoredCanonicalDocument<EditorModelSession> current, String expectedEtag) {
        if(expectedEtag == null || expectedEtag.isBlank()) return;
        String normalized = expectedEtag.trim();
        if(!Objects.equals(current.etag(), normalized) && !Objects.equals(stripQuotes(current.etag()), stripQuotes(normalized))) {
            throw new EditorSessionApplicationException(EditorSessionApplicationException.Reason.PRECONDITION_FAILED, "Editor session was modified by another client");
        }
    }
    /** Descripción: Ejecuta la operación «stripQuotes» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String stripQuotes(String value) { return value == null ? "" : value.replace("\"", ""); }
    /** Descripción: Valida las condiciones necesarias para «ensurePath» y rechaza entradas no válidas. Entradas: session: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensurePath(EditorModelSession session, String metamodelId, String editorId) {
        if(!session.metamodelId().equals(metamodelId) || !session.editorId().equals(editorId)) throw notFound("Editor session does not belong to the requested editor");
    }
    /** Descripción: Valida las condiciones necesarias para «ensureFresh» y rechaza entradas no válidas. Entradas: session: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureFresh(EditorModelSession session, ExecutableEditorPlan plan) {
        if(!Objects.equals(session.sourceEditorEtag(), plan.sourceEtag())) throw notExecutable("The editor definition changed after this session was created. Create or migrate the session before editing it.");
    }
    /** Descripción: Ejecuta la operación «plan» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExecutableEditorPlan plan(String metamodelId, String editorId) { return runtimes.find(metamodelId, editorId).plan(); }
    /** Descripción: Ejecuta la operación «representation» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: plan: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación; session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExecutableRepresentationPlan representation(ExecutableEditorPlan plan, String requested, EditorModelSession session) { return representation(plan, blankTo(requested, session.activeRepresentationId())); }
    /** Descripción: Ejecuta la operación «representation» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: plan: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExecutableRepresentationPlan representation(ExecutableEditorPlan plan, String requested) {
        String id = representationId(plan, requested);
        ExecutableRepresentationPlan result = plan.representationsById().get(id);
        if(result == null) throw notFound("Representation was not found: " + id);
        return result;
    }
    /** Descripción: Ejecuta la operación «representationId» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: plan: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String representationId(ExecutableEditorPlan plan, String requested) {
        if(requested != null && !requested.isBlank()) {
            if(!plan.representationsById().containsKey(requested)) throw notFound("Representation was not found: " + requested);
            return requested;
        }
        return plan.representationsById().keySet().stream().findFirst().orElseThrow(() -> notExecutable("The editor has no executable representations"));
    }
    /** Descripción: Valida las condiciones necesarias para «requireEditable» y rechaza entradas no válidas. Entradas: representation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEditable(ExecutableRepresentationPlan representation) { if(!representation.editable()) throw notExecutable("The active representation is read only"); }

    /** Descripción: Ejecuta la operación «restoreSnapshot» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: current: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorModelSession restoreSnapshot(EditorModelSession current, EditorModelSession snapshot) {
        return new EditorModelSession(current.schemaVersion(), current.id(), current.metamodelId(), current.editorId(),
                current.sourceEditorEtag(), snapshot.activeRepresentationId(), snapshot.openRepresentationIds(), snapshot.model(),
                snapshot.latestValidationReport(), current.revision() + 1, current.createdAt(), Instant.now(), current.annotations());
    }

    /** Descripción: Ejecuta la operación «automaticValidation» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: session: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; trigger: dato de entrada necesario para la operación; changeSet: dato de entrada necesario para la operación; failHard: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorModelSession automaticValidation(EditorModelSession session, ExecutableEditorPlan plan,
            EditorValidationTrigger trigger, ValidationChangeSet changeSet, boolean failHard) {
        if(!plan.capabilityManifest().supports(EditorCapability.VALIDATION_RUNTIME)
                || !plan.runtimePolicy().validates(trigger)) return session;
        return validateNow(session, trigger == EditorValidationTrigger.ON_CHANGE ? ValidationRunMode.INCREMENTAL : ValidationRunMode.FULL,
                changeSet, failHard);
    }

    /** Descripción: Valida las condiciones necesarias para «validateNow» y rechaza entradas no válidas. Entradas: session: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación; changeSet: dato de entrada necesario para la operación; failHard: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorModelSession validateNow(EditorModelSession session, ValidationRunMode mode,
            ValidationChangeSet changeSet, boolean failHard) {
        if(validationRuntime == null) {
            if(failHard) throw notExecutable("Validation runtime is not available");
            return session;
        }
        try {
            ModelValidationReport report = validationRuntime.validate(session.metamodelId(), new ValidationExecutionRequest(mode,
                    session.model(), List.of(), "", changeSet == null ? ValidationChangeSet.empty() : changeSet, Map.of()));
            return session.withValidation(report);
        } catch(RuntimeException exception) {
            if(failHard) throw notExecutable(exception.getMessage() == null ? "Validation failed" : exception.getMessage());
            return session;
        }
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de EditorModelSessionService. Entradas: eventTypeId: identificador que delimita el recurso objetivo; session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; typeId: identificador que delimita el recurso objetivo; payload: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publish(String eventTypeId, EditorModelSession session, String elementId, String typeId,
            Map<String, Object> payload) {
        ExecutableEditorPlan plan = plan(session.metamodelId(), session.editorId());
        if(events == null || !plan.runtimePolicy().publishEvents()) return;
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        if(payload != null) eventPayload.putAll(payload);
        eventPayload.put("hydra.eventsEnabled", plan.runtimePolicy().publishEvents());
        eventPayload.put("hydra.eventFlowsEnabled", plan.runtimePolicy().eventFlowsEnabled());
        eventPayload.put("hydra.gamificationEnabled", plan.runtimePolicy().gamificationEnabled());
        PendingEditorEvent value = new PendingEditorEvent(eventTypeId, EditorSessionEventContext.from(session), elementId, typeId, eventPayload);
        if(!plan.runtimePolicy().autosave() && workingCopies.contains(session.id())) {
            pendingEventBuffer.enqueue(value);
            return;
        }
        events.publish(value);
    }

    /** Descripción: Ejecuta la operación «storeState» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: before: dato de entrada necesario para la operación; next: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<EditorModelSession> storeState(StoredCanonicalDocument<EditorModelSession> before,
            EditorModelSession next, ExecutableEditorPlan plan) {
        if(plan.runtimePolicy().autosave()) {
            try { return sessions.replace(before.document().id(), next, before.etag()); }
            catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
        }
        PendingEditorSession existing = workingCopies.find(before.document().id());
        String baseEtag = existing == null ? before.etag() : existing.baseEtag();
        java.nio.file.Path path = existing == null ? before.path() : existing.path();
        String draftEtag = "draft-" + next.revision() + "-" + Integer.toUnsignedString(Objects.hash(next.updatedAt(), next.model().elements().size(), next.model().relations().size()), 36);
        PendingEditorSession pending = new PendingEditorSession(next, baseEtag, draftEtag, path);
        workingCopies.put(pending);
        return new StoredCanonicalDocument<>(next, draftEtag, path);
    }

    /** Descripción: Ejecuta la operación «flushPendingEvents» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void flushPendingEvents(String sessionId) {
        if(events == null) { pendingEventBuffer.discard(sessionId); return; }
        pendingEventBuffer.flush(sessionId, events::publish);
    }

    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() { if(!properties.isEnabled()) throw new EditorSessionApplicationException(EditorSessionApplicationException.Reason.DISABLED, "Neutral editor sessions are disabled"); }
    /** Descripción: Crea o prepara el resultado de «newElementId» dentro de EditorModelSessionService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String newElementId() { return "element:" + UUID.randomUUID(); }
    /** Descripción: Crea o prepara el resultado de «newRelationId» dentro de EditorModelSessionService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String newRelationId() { return "relation-instance:" + UUID.randomUUID(); }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field) { if(value == null || value.isBlank()) throw invalid(field + " must not be blank"); return value.trim(); }
    /** Descripción: Ejecuta la operación «blankTo» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException invalid(String message) { return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.INVALID_REQUEST, message); }
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException notFound(String message) { return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_FOUND, message); }
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException conflict(String message) { return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.CONFLICT, message); }
    /** Descripción: Ejecuta la operación «notExecutable» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException notExecutable(String message) { return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_EXECUTABLE, message); }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de EditorModelSessionService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException translate(CanonicalDocumentStoreException exception) {
        EditorSessionApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> EditorSessionApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> EditorSessionApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> EditorSessionApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> EditorSessionApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> EditorSessionApplicationException.Reason.STORAGE_ERROR;
        };
        return new EditorSessionApplicationException(reason, exception.getMessage(), exception);
    }
}

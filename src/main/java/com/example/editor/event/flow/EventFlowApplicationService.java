package com.example.editor.event.flow;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.flow.persistence.EventFlowModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Orquesta persistencia, comprobación y carga de EventFlowModel. */
@Service
public class EventFlowApplicationService {
    private final EventFlowModelRepository repository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final EventModelApplicationService eventModelService;
    private final EventFlowChecker checker;
    private final ExpressionModelRepository expressionModels;
    /** Descripción: Inicializa una instancia de EventFlowApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; eventModelService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowApplicationService(EventFlowModelRepository repository, CanonicalMetamodelApplicationService metamodelService,
            EventModelApplicationService eventModelService, EventFlowChecker checker, ExpressionModelRepository expressionModels) {
        this.repository = repository;
        this.metamodelService = metamodelService;
        this.eventModelService = eventModelService;
        this.checker = checker;
        this.expressionModels = expressionModels;
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de EventFlowApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowSaveResponse create(String metamodelId, EventFlowModel model) {
        requireCompatible(metamodelId, model);
        EventFlowCheckReport report = checkModel(metamodelId, model);
        if(!report.valid()) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "Event flows contain blocking errors");
        try { return response(repository.create(model), report); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Actualiza el estado gestionado por EventFlowApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowSaveResponse replace(String metamodelId, EventFlowModel model, String etag) {
        requireCompatible(metamodelId, model);
        EventFlowCheckReport report = checkModel(metamodelId, model);
        if(!report.valid()) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "Event flows contain blocking errors");
        try { return response(repository.replace(metamodelId, model, etag), report); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventFlowApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowLoadResult find(String metamodelId) {
        return findOptional(metamodelId)
                .orElseThrow(() -> new EventFlowApplicationException(EventFlowApplicationException.Reason.NOT_FOUND, "Event flows were not found: " + metamodelId));
    }

    /** Consulta de descubrimiento: la ausencia inicial de EventFlow es un estado normal y no una excepción del servidor. */
    /** Descripción: Obtiene la información correspondiente a «findOptional» dentro de EventFlowApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<EventFlowLoadResult> findOptional(String metamodelId) {
        requireMetamodel(metamodelId);
        try {
            return repository.findByMetamodelId(metamodelId)
                    .map(stored -> new EventFlowLoadResult(stored.document(), stored.etag(), checkModel(metamodelId, stored.document())));
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowCheckReport check(String metamodelId) { return find(metamodelId).checkReport(); }
    /** Descripción: Valida las condiciones necesarias para «checkDraft» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowCheckReport checkDraft(String metamodelId, EventFlowModel model) {
        requireCompatible(metamodelId, model);
        return checkModel(metamodelId, model);
    }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de EventFlowApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<EventFlowSummary> list() {
        return repository.listMetamodelIds().stream().map(id -> {
            StoredCanonicalDocument<EventFlowModel> stored = repository.findByMetamodelId(id).orElseThrow();
            int nodes = stored.document().flows().stream().mapToInt(flow -> flow.nodes().size()).sum();
            return new EventFlowSummary(stored.document().id(), id, stored.document().flows().size(), nodes, stored.etag());
        }).toList();
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String etag) {
        requireMetamodel(metamodelId);
        try { repository.delete(metamodelId, etag); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Valida también las condiciones del flujo contra el kernel neutral de expresiones. */
    /** Descripción: Valida EventFlow incluyendo sus expresiones neutrales. Entradas: metamodelId y model. Salida: devuelve el informe agregado de comprobación. */ private EventFlowCheckReport checkModel(String metamodelId, EventFlowModel model) {
        ExpressionModel expressions = null;
        if(model != null && !model.expressionModelId().isBlank()) {
            expressions = expressionModels.findById(metamodelId).map(stored -> stored.expressionModel()).orElse(null);
        }
        var metamodel = metamodelService.findById(metamodelId).metamodel();
        return checker.check(model, definitions(metamodelId), expressions, metamodel);
    }

    /** Descripción: Ejecuta la operación «definitions» correspondiente a la responsabilidad de EventFlowApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EventDefinitionModel definitions(String metamodelId) {
        try { return eventModelService.find(metamodelId).definitions(); }
        catch(EventModelApplicationException exception) {
            if(exception.getReason() == EventModelApplicationException.Reason.NOT_FOUND) return null;
            throw new EventFlowApplicationException(EventFlowApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception);
        }
    }
    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireCompatible(String metamodelId, EventFlowModel model) {
        requireMetamodel(metamodelId);
        if(model == null) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "EventFlowModel is required");
        if(!metamodelId.equals(model.metamodelId())) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "Path metamodel id must match EventFlowModel.metamodelId");
        EventDefinitionModel definitions = definitions(metamodelId);
        if(definitions != null && !model.eventDefinitionModelId().isBlank() && !definitions.id().equals(model.eventDefinitionModelId()))
            throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "eventDefinitionModelId does not match the owning event catalog");
    }
    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireMetamodel(String id) {
        try { metamodelService.findById(id); }
        catch(MetamodelApplicationException exception) { throw new EventFlowApplicationException(EventFlowApplicationException.Reason.NOT_FOUND, "Metamodel was not found: " + id, exception); }
    }
    /** Descripción: Ejecuta la operación «response» correspondiente a la responsabilidad de EventFlowApplicationService. Entradas: stored: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowSaveResponse response(StoredCanonicalDocument<EventFlowModel> stored, EventFlowCheckReport report) {
        return new EventFlowSaveResponse(stored.document(), stored.etag(), report);
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de EventFlowApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowApplicationException translate(CanonicalDocumentStoreException exception) {
        EventFlowApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> EventFlowApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> EventFlowApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> EventFlowApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> EventFlowApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> EventFlowApplicationException.Reason.STORAGE_ERROR;
        };
        return new EventFlowApplicationException(reason, exception.getMessage(), exception);
    }
}

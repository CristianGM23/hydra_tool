package com.example.editor.event.flow.diagram;

import org.springframework.stereotype.Service;
import com.example.editor.event.flow.EventFlowApplicationException;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Mantiene geometría y semántica de flujos en documentos separados. */
@Service
public class EventFlowDiagramService {
    private final EventFlowDiagramRepository repository;
    private final EventFlowApplicationService flowService;
    /** Descripción: Inicializa una instancia de EventFlowDiagramService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; flowService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowDiagramService(EventFlowDiagramRepository repository, EventFlowApplicationService flowService) {
        this.repository = repository;
        this.flowService = flowService;
    }
    /** Descripción: Actualiza el estado gestionado por EventFlowDiagramService mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; diagram: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowDiagramLoadResult save(String metamodelId, EventFlowDiagramDocument diagram, String etag) {
        if(diagram == null || !metamodelId.equals(diagram.metamodelId())) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "Diagram metamodelId must match the path");
        String expectedModelId = flowService.find(metamodelId).eventFlowModel().id();
        if(!expectedModelId.equals(diagram.eventFlowModelId())) throw new EventFlowApplicationException(EventFlowApplicationException.Reason.INVALID_REQUEST, "Diagram must reference the current EventFlowModel");
        try {
            StoredCanonicalDocument<EventFlowDiagramDocument> stored = repository.find(metamodelId).isEmpty()
                    ? repository.create(diagram) : repository.replace(metamodelId, diagram, etag);
            return new EventFlowDiagramLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventFlowDiagramService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventFlowDiagramLoadResult find(String metamodelId) {
        flowService.find(metamodelId);
        try {
            StoredCanonicalDocument<EventFlowDiagramDocument> stored = repository.find(metamodelId)
                    .orElseThrow(() -> new EventFlowApplicationException(EventFlowApplicationException.Reason.NOT_FOUND, "Event-flow diagram was not found"));
            return new EventFlowDiagramLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String etag) {
        try { repository.delete(metamodelId, etag); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de EventFlowDiagramService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventFlowApplicationException translate(CanonicalDocumentStoreException exception) {
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

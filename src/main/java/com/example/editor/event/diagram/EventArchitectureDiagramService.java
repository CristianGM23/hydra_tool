package com.example.editor.event.diagram;

import org.springframework.stereotype.Service;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelLoadResult;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Mantiene la geometría del catálogo y los bindings separada de sus contratos canónicos. */
@Service
public class EventArchitectureDiagramService {
    private final EventArchitectureDiagramRepository repository;
    private final EventModelApplicationService applicationService;
    /** Descripción: Inicializa una instancia de EventArchitectureDiagramService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; applicationService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventArchitectureDiagramService(EventArchitectureDiagramRepository repository, EventModelApplicationService applicationService) {
        this.repository = repository;
        this.applicationService = applicationService;
    }
    /** Descripción: Actualiza el estado gestionado por EventArchitectureDiagramService mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; diagram: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventArchitectureDiagramLoadResult save(String metamodelId, EventArchitectureDiagramDocument diagram, String etag) {
        if(diagram == null || !metamodelId.equals(diagram.metamodelId())) {
            throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST,
                    "Diagram metamodelId must match the path");
        }
        EventModelLoadResult current = applicationService.find(metamodelId);
        if(!current.definitions().id().equals(diagram.eventDefinitionModelId())
                || !current.bindings().id().equals(diagram.eventBindingModelId())) {
            throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST,
                    "Diagram must reference the current event definition and binding models");
        }
        try {
            StoredCanonicalDocument<EventArchitectureDiagramDocument> stored = repository.find(metamodelId).isEmpty()
                    ? repository.create(diagram) : repository.replace(metamodelId, diagram, etag);
            return new EventArchitectureDiagramLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventArchitectureDiagramService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventArchitectureDiagramLoadResult find(String metamodelId) {
        applicationService.find(metamodelId);
        try {
            StoredCanonicalDocument<EventArchitectureDiagramDocument> stored = repository.find(metamodelId)
                    .orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND,
                            "Event architecture diagram was not found"));
            return new EventArchitectureDiagramLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String etag) {
        try { repository.delete(metamodelId, etag); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de EventArchitectureDiagramService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventModelApplicationException translate(CanonicalDocumentStoreException exception) {
        EventModelApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> EventModelApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> EventModelApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> EventModelApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> EventModelApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> EventModelApplicationException.Reason.STORAGE_ERROR;
        };
        return new EventModelApplicationException(reason, exception.getMessage(), exception);
    }
}

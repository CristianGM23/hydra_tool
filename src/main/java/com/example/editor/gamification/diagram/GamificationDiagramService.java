package com.example.editor.gamification.diagram;

import org.springframework.stereotype.Service;
import com.example.editor.gamification.GamificationApplicationException;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationLoadResult;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Mantiene la geometría visual separada de definiciones, bindings, estado y ledger. */
@Service
public class GamificationDiagramService {
    private final GamificationDiagramRepository repository;
    private final GamificationApplicationService applicationService;
    /** Descripción: Inicializa una instancia de GamificationDiagramService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; applicationService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationDiagramService(GamificationDiagramRepository repository, GamificationApplicationService applicationService) {
        this.repository = repository;
        this.applicationService = applicationService;
    }
    /** Descripción: Actualiza el estado gestionado por GamificationDiagramService mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; diagram: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationDiagramLoadResult save(String metamodelId, GamificationDiagramDocument diagram, String etag) {
        if(diagram == null || !metamodelId.equals(diagram.metamodelId())) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Diagram metamodelId must match the path");
        GamificationLoadResult current = applicationService.find(metamodelId);
        if(!current.definitions().id().equals(diagram.definitionModelId()) || !current.bindings().id().equals(diagram.bindingModelId())) {
            throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Diagram must reference the current gamification definition and binding models");
        }
        try {
            StoredCanonicalDocument<GamificationDiagramDocument> stored = repository.find(metamodelId).isEmpty()
                    ? repository.create(diagram) : repository.replace(metamodelId, diagram, etag);
            return new GamificationDiagramLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GamificationDiagramService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationDiagramLoadResult find(String metamodelId) {
        applicationService.find(metamodelId);
        try {
            StoredCanonicalDocument<GamificationDiagramDocument> stored = repository.find(metamodelId)
                    .orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification diagram was not found"));
            return new GamificationDiagramLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String etag) {
        try { repository.delete(metamodelId, etag); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de GamificationDiagramService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationApplicationException translate(CanonicalDocumentStoreException exception) {
        GamificationApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> GamificationApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> GamificationApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> GamificationApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> GamificationApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> GamificationApplicationException.Reason.STORAGE_ERROR;
        };
        return new GamificationApplicationException(reason, exception.getMessage(), exception);
    }
}

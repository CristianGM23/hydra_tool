package com.example.editor.validation.action;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepositoryException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Orquesta persistencia y comprobación estática de ActionModel 1.0. */
@Service
public class ActionModelApplicationService {
    private final ActionModelRepository repository;
    private final ExpressionModelRepository expressionRepository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final ActionModelChecker checker;

    /** Descripción: Inicializa una instancia de ActionModelApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; expressionRepository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ActionModelApplicationService(ActionModelRepository repository, ExpressionModelRepository expressionRepository,
            CanonicalMetamodelApplicationService metamodelService, ActionModelChecker checker) {
        this.repository = repository;
        this.expressionRepository = expressionRepository;
        this.metamodelService = metamodelService;
        this.checker = checker;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de ActionModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ActionModelSaveResponse create(String metamodelId, ActionModel model) {
        MetaModel metamodel = requireCompatible(metamodelId, model);
        ExpressionModel expressions = requireExpressions(metamodelId, model.expressionModelId());
        ActionModelCheckReport report = checker.check(model, metamodel, expressions);
        try {
            StoredCanonicalDocument<ActionModel> stored = repository.create(model);
            return new ActionModelSaveResponse(stored.document(), report, stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Actualiza el estado gestionado por ActionModelApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ActionModelSaveResponse replace(String metamodelId, ActionModel model, String expectedEtag) {
        MetaModel metamodel = requireCompatible(metamodelId, model);
        ExpressionModel expressions = requireExpressions(metamodelId, model.expressionModelId());
        ActionModelCheckReport report = checker.check(model, metamodel, expressions);
        try {
            StoredCanonicalDocument<ActionModel> stored = repository.replace(metamodelId, model, expectedEtag);
            return new ActionModelSaveResponse(stored.document(), report, stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de ActionModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ActionModelLoadResult find(String metamodelId) {
        requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<ActionModel> stored = repository.findById(metamodelId).orElseThrow(() -> new ActionModelApplicationException(ActionModelApplicationException.Reason.NOT_FOUND, "Action model was not found: " + metamodelId));
            return new ActionModelLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de ActionModelApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ActionModelSummary> findAll() {
        try { return repository.findAll(); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ActionModelCheckReport check(String metamodelId) {
        ActionModel model = find(metamodelId).actionModel();
        return checker.check(model, requireMetamodel(metamodelId), requireExpressions(metamodelId, model.expressionModelId()));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String expectedEtag) {
        requireMetamodel(metamodelId);
        try { repository.delete(metamodelId, expectedEtag); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireCompatible(String metamodelId, ActionModel model) {
        MetaModel metamodel = requireMetamodel(metamodelId);
        if(model == null) throw new ActionModelApplicationException(ActionModelApplicationException.Reason.INVALID_REQUEST, "Action model must not be null");
        if(!metamodelId.equals(model.metamodelId())) throw new ActionModelApplicationException(ActionModelApplicationException.Reason.INVALID_REQUEST, "Action model metamodelId must match the owning metamodel id");
        return metamodel;
    }

    /** Descripción: Valida las condiciones necesarias para «requireExpressions» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; expressionModelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel requireExpressions(String metamodelId, String expressionModelId) {
        try {
            ExpressionModel model = expressionRepository.findById(metamodelId).orElseThrow(() -> new ActionModelApplicationException(ActionModelApplicationException.Reason.INVALID_REQUEST, "Expression model must be created before the action model")).expressionModel();
            if(!expressionModelId.equals(model.id())) throw new ActionModelApplicationException(ActionModelApplicationException.Reason.INVALID_REQUEST, "Action model expressionModelId must match the owning expression model");
            return model;
        } catch(ExpressionModelRepositoryException exception) { throw new ActionModelApplicationException(ActionModelApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireMetamodel(String id) {
        if(id == null || id.isBlank()) throw new ActionModelApplicationException(ActionModelApplicationException.Reason.INVALID_REQUEST, "Metamodel id must not be blank");
        try { return metamodelService.findById(id).metamodel(); }
        catch(MetamodelApplicationException exception) {
            ActionModelApplicationException.Reason reason = switch(exception.getReason()) {
                case INVALID_REQUEST -> ActionModelApplicationException.Reason.INVALID_REQUEST;
                case NOT_FOUND -> ActionModelApplicationException.Reason.NOT_FOUND;
                case CONFLICT -> ActionModelApplicationException.Reason.CONFLICT;
                case PRECONDITION_FAILED -> ActionModelApplicationException.Reason.PRECONDITION_FAILED;
                case VALIDATION_ERROR, STORAGE_ERROR -> ActionModelApplicationException.Reason.STORAGE_ERROR;
            };
            throw new ActionModelApplicationException(reason, exception.getMessage(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de ActionModelApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModelApplicationException translate(CanonicalDocumentStoreException exception) {
        ActionModelApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> ActionModelApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> ActionModelApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> ActionModelApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> ActionModelApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> ActionModelApplicationException.Reason.STORAGE_ERROR;
        };
        return new ActionModelApplicationException(reason, exception.getMessage(), exception);
    }
}

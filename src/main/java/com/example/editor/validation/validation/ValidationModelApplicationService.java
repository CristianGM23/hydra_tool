package com.example.editor.validation.validation;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepositoryException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;

/** Orquesta persistencia y comprobación estática de ValidationModel 1.0. */
@Service
public class ValidationModelApplicationService {
    private final ValidationModelRepository repository;
    private final ExpressionModelRepository expressionRepository;
    private final ActionModelRepository actionRepository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final ValidationModelChecker checker;

    /** Descripción: Inicializa una instancia de ValidationModelApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; expressionRepository: dato de entrada necesario para la operación; actionRepository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationModelApplicationService(ValidationModelRepository repository, ExpressionModelRepository expressionRepository,
            ActionModelRepository actionRepository, CanonicalMetamodelApplicationService metamodelService, ValidationModelChecker checker) {
        this.repository = repository;
        this.expressionRepository = expressionRepository;
        this.actionRepository = actionRepository;
        this.metamodelService = metamodelService;
        this.checker = checker;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de ValidationModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationModelSaveResponse create(String metamodelId, ValidationModel model) {
        MetaModel metamodel = requireCompatible(metamodelId, model);
        ExpressionModel expressions = requireExpressions(metamodelId, model.expressionModelId());
        ActionModel actions = optionalActions(metamodelId, model.actionModelId());
        ValidationModelCheckReport report = checker.check(model, metamodel, expressions, actions);
        try {
            StoredCanonicalDocument<ValidationModel> stored = repository.create(model);
            return new ValidationModelSaveResponse(stored.document(), report, stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Actualiza el estado gestionado por ValidationModelApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationModelSaveResponse replace(String metamodelId, ValidationModel model, String expectedEtag) {
        MetaModel metamodel = requireCompatible(metamodelId, model);
        ExpressionModel expressions = requireExpressions(metamodelId, model.expressionModelId());
        ActionModel actions = optionalActions(metamodelId, model.actionModelId());
        ValidationModelCheckReport report = checker.check(model, metamodel, expressions, actions);
        try {
            StoredCanonicalDocument<ValidationModel> stored = repository.replace(metamodelId, model, expectedEtag);
            return new ValidationModelSaveResponse(stored.document(), report, stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de ValidationModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationModelLoadResult find(String metamodelId) {
        requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<ValidationModel> stored = repository.findById(metamodelId).orElseThrow(() -> new ValidationModelApplicationException(ValidationModelApplicationException.Reason.NOT_FOUND, "Validation model was not found: " + metamodelId));
            return new ValidationModelLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de ValidationModelApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ValidationModelSummary> findAll() {
        try { return repository.findAll(); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationModelCheckReport check(String metamodelId) {
        ValidationModel model = find(metamodelId).validationModel();
        return checker.check(model, requireMetamodel(metamodelId), requireExpressions(metamodelId, model.expressionModelId()), optionalActions(metamodelId, model.actionModelId()));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String expectedEtag) {
        requireMetamodel(metamodelId);
        try { repository.delete(metamodelId, expectedEtag); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireCompatible(String metamodelId, ValidationModel model) {
        MetaModel metamodel = requireMetamodel(metamodelId);
        if(model == null) throw new ValidationModelApplicationException(ValidationModelApplicationException.Reason.INVALID_REQUEST, "Validation model must not be null");
        if(!metamodelId.equals(model.metamodelId())) throw new ValidationModelApplicationException(ValidationModelApplicationException.Reason.INVALID_REQUEST, "Validation model metamodelId must match the owning metamodel id");
        return metamodel;
    }

    /** Descripción: Valida las condiciones necesarias para «requireExpressions» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; expressionModelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel requireExpressions(String metamodelId, String expressionModelId) {
        try {
            ExpressionModel model = expressionRepository.findById(metamodelId).orElseThrow(() -> new ValidationModelApplicationException(ValidationModelApplicationException.Reason.INVALID_REQUEST, "Expression model must be created before the validation model")).expressionModel();
            if(!expressionModelId.equals(model.id())) throw new ValidationModelApplicationException(ValidationModelApplicationException.Reason.INVALID_REQUEST, "Validation model expressionModelId must match the owning expression model");
            return model;
        } catch(ExpressionModelRepositoryException exception) { throw new ValidationModelApplicationException(ValidationModelApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception); }
    }

    /** Descripción: Ejecuta la operación «optionalActions» correspondiente a la responsabilidad de ValidationModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; actionModelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModel optionalActions(String metamodelId, String actionModelId) {
        if(actionModelId == null || actionModelId.isBlank()) return null;
        try {
            ActionModel model = actionRepository.findById(metamodelId).orElseThrow(() -> new ValidationModelApplicationException(ValidationModelApplicationException.Reason.INVALID_REQUEST, "Configured action model was not found")).document();
            if(!actionModelId.equals(model.id())) throw new ValidationModelApplicationException(ValidationModelApplicationException.Reason.INVALID_REQUEST, "Validation model actionModelId must match the owning action model");
            return model;
        } catch(CanonicalDocumentStoreException exception) { throw new ValidationModelApplicationException(ValidationModelApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireMetamodel(String id) {
        if(id == null || id.isBlank()) throw new ValidationModelApplicationException(ValidationModelApplicationException.Reason.INVALID_REQUEST, "Metamodel id must not be blank");
        try { return metamodelService.findById(id).metamodel(); }
        catch(MetamodelApplicationException exception) {
            ValidationModelApplicationException.Reason reason = switch(exception.getReason()) {
                case INVALID_REQUEST -> ValidationModelApplicationException.Reason.INVALID_REQUEST;
                case NOT_FOUND -> ValidationModelApplicationException.Reason.NOT_FOUND;
                case CONFLICT -> ValidationModelApplicationException.Reason.CONFLICT;
                case PRECONDITION_FAILED -> ValidationModelApplicationException.Reason.PRECONDITION_FAILED;
                case VALIDATION_ERROR, STORAGE_ERROR -> ValidationModelApplicationException.Reason.STORAGE_ERROR;
            };
            throw new ValidationModelApplicationException(reason, exception.getMessage(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de ValidationModelApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationModelApplicationException translate(CanonicalDocumentStoreException exception) {
        ValidationModelApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> ValidationModelApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> ValidationModelApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> ValidationModelApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> ValidationModelApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> ValidationModelApplicationException.Reason.STORAGE_ERROR;
        };
        return new ValidationModelApplicationException(reason, exception.getMessage(), exception);
    }
}

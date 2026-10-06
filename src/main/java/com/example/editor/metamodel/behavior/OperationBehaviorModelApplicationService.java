package com.example.editor.metamodel.behavior;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.behavior.persistence.OperationBehaviorModelRepository;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.validation.OperationBehaviorModelValidator;
import com.example.editor.metamodel.validation.ValidationIssue;
import com.example.editor.metamodel.validation.ValidationReport;
import com.example.editor.metamodel.validation.ValidationSeverity;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Orquesta persistencia, validación y referencias de los bindings de operaciones. */
@Service
public class OperationBehaviorModelApplicationService {
    private final OperationBehaviorModelRepository repository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final ExpressionModelRepository expressionRepository;
    private final ActionModelRepository actionRepository;
    private final OperationBehaviorModelValidator validator;

    /** Descripción: Inicializa una instancia de OperationBehaviorModelApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; expressionRepository: dato de entrada necesario para la operación; actionRepository: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public OperationBehaviorModelApplicationService(OperationBehaviorModelRepository repository, CanonicalMetamodelApplicationService metamodelService,
            ExpressionModelRepository expressionRepository, ActionModelRepository actionRepository, OperationBehaviorModelValidator validator) {
        this.repository = repository;
        this.metamodelService = metamodelService;
        this.expressionRepository = expressionRepository;
        this.actionRepository = actionRepository;
        this.validator = validator;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de OperationBehaviorModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public OperationBehaviorModelSaveResponse create(String metamodelId, OperationBehaviorModel model) {
        ValidationReport report = validate(metamodelId, model);
        if(report.hasErrors()) throw new OperationBehaviorModelApplicationException(OperationBehaviorModelApplicationException.Reason.VALIDATION_ERROR, firstError(report));
        try {
            StoredCanonicalDocument<OperationBehaviorModel> stored = repository.create(model);
            return new OperationBehaviorModelSaveResponse(stored.document(), report, stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Actualiza el estado gestionado por OperationBehaviorModelApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public OperationBehaviorModelSaveResponse replace(String metamodelId, OperationBehaviorModel model, String expectedEtag) {
        ValidationReport report = validate(metamodelId, model);
        if(report.hasErrors()) throw new OperationBehaviorModelApplicationException(OperationBehaviorModelApplicationException.Reason.VALIDATION_ERROR, firstError(report));
        try {
            StoredCanonicalDocument<OperationBehaviorModel> stored = repository.replace(metamodelId, model, expectedEtag);
            return new OperationBehaviorModelSaveResponse(stored.document(), report, stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de OperationBehaviorModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public OperationBehaviorModelLoadResult find(String metamodelId) {
        requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<OperationBehaviorModel> stored = repository.findById(metamodelId).orElseThrow(() -> new OperationBehaviorModelApplicationException(OperationBehaviorModelApplicationException.Reason.NOT_FOUND, "Operation behavior model was not found: " + metamodelId));
            return new OperationBehaviorModelLoadResult(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «findOrEmpty» dentro de OperationBehaviorModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public OperationBehaviorModel findOrEmpty(String metamodelId) {
        requireMetamodel(metamodelId);
        try { return repository.findById(metamodelId).map(StoredCanonicalDocument::document).orElseGet(() -> new OperationBehaviorModel("1.0", metamodelId, List.of(), java.util.Map.of())); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de OperationBehaviorModelApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<OperationBehaviorModelSummary> findAll() {
        try { return repository.findAll(); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationReport check(String metamodelId) { return validate(metamodelId, find(metamodelId).behaviorModel()); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String expectedEtag) { requireMetamodel(metamodelId); try { repository.delete(metamodelId, expectedEtag); } catch(CanonicalDocumentStoreException exception) { throw translate(exception); } }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationReport validate(String metamodelId, OperationBehaviorModel model) {
        if(model == null || !metamodelId.equals(model.metamodelId())) throw new OperationBehaviorModelApplicationException(OperationBehaviorModelApplicationException.Reason.INVALID_REQUEST, "Operation behavior metamodelId must match the owning metamodel");
        ValidationReport structural = validator.validate(requireMetamodel(metamodelId), model);
        List<ValidationIssue> issues = new ArrayList<>(structural.issues());
        Set<String> expressionIds = expressionRepository.findById(metamodelId).map(item -> item.expressionModel().expressions().stream().map(expression -> expression.id()).collect(java.util.stream.Collectors.toCollection(HashSet::new))).orElseGet(HashSet::new);
        Set<String> actionIds = actionRepository.findById(metamodelId).map(item -> item.document().actions().stream().map(action -> action.id()).collect(java.util.stream.Collectors.toCollection(HashSet::new))).orElseGet(HashSet::new);
        for(int index = 0; index < model.bindings().size(); index++) {
            OperationBinding binding = model.bindings().get(index);
            if(binding.implementationKind() == com.example.editor.metamodel.core.OperationImplementationKind.EXPRESSION && !binding.expressionId().isBlank() && !expressionIds.contains(binding.expressionId())) issues.add(new ValidationIssue(ValidationSeverity.ERROR, "OPERATION_BINDING_UNKNOWN_EXPRESSION", "$.bindings[" + index + "].expressionId", "Expression does not exist: " + binding.expressionId()));
            if(binding.implementationKind() == com.example.editor.metamodel.core.OperationImplementationKind.ACTION && !binding.actionId().isBlank() && !actionIds.contains(binding.actionId())) issues.add(new ValidationIssue(ValidationSeverity.ERROR, "OPERATION_BINDING_UNKNOWN_ACTION", "$.bindings[" + index + "].actionId", "Action does not exist: " + binding.actionId()));
        }
        return new ValidationReport(issues);
    }

    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private com.example.editor.metamodel.core.MetaModel requireMetamodel(String id) {
        if(id == null || id.isBlank()) throw new OperationBehaviorModelApplicationException(OperationBehaviorModelApplicationException.Reason.INVALID_REQUEST, "Metamodel id must not be blank");
        try { return metamodelService.findById(id).metamodel(); }
        catch(MetamodelApplicationException exception) {
            OperationBehaviorModelApplicationException.Reason reason = switch(exception.getReason()) {
                case INVALID_REQUEST -> OperationBehaviorModelApplicationException.Reason.INVALID_REQUEST;
                case NOT_FOUND -> OperationBehaviorModelApplicationException.Reason.NOT_FOUND;
                case CONFLICT -> OperationBehaviorModelApplicationException.Reason.CONFLICT;
                case PRECONDITION_FAILED -> OperationBehaviorModelApplicationException.Reason.PRECONDITION_FAILED;
                case VALIDATION_ERROR -> OperationBehaviorModelApplicationException.Reason.VALIDATION_ERROR;
                case STORAGE_ERROR -> OperationBehaviorModelApplicationException.Reason.STORAGE_ERROR;
            };
            throw new OperationBehaviorModelApplicationException(reason, exception.getMessage(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «firstError» correspondiente a la responsabilidad de OperationBehaviorModelApplicationService. Entradas: report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String firstError(ValidationReport report) { return report.errors().stream().map(ValidationIssue::message).findFirst().orElse("Operation behavior model is invalid"); }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de OperationBehaviorModelApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private OperationBehaviorModelApplicationException translate(CanonicalDocumentStoreException exception) {
        OperationBehaviorModelApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> OperationBehaviorModelApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> OperationBehaviorModelApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> OperationBehaviorModelApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> OperationBehaviorModelApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> OperationBehaviorModelApplicationException.Reason.STORAGE_ERROR;
        };
        return new OperationBehaviorModelApplicationException(reason, exception.getMessage(), exception);
    }
}

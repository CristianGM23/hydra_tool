package com.example.editor.validation.expression;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.semantic.expression.ExpressionBatchCheckResult;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluationException;
import com.example.editor.semantic.expression.ExpressionEvaluationResult;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepositoryException;
import com.example.editor.validation.expression.persistence.StoredExpressionModel;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Orquesta persistencia, tipado y evaluación de expresiones canónicas. */
@Service
public class ExpressionModelApplicationService {

    private final ExpressionModelRepository repository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final ExpressionTypeChecker typeChecker;
    private final ExpressionEvaluator evaluator;
    private final OperationBehaviorModelApplicationService behaviorService;
    private final OperationQueryRuntime operationRuntime;

    @Autowired
    /** Descripción: Inicializa una instancia de ExpressionModelApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; typeChecker: dato de entrada necesario para la operación; evaluator: dato de entrada necesario para la operación; behaviorService: servicio colaborador utilizado por la operación; operationRuntime: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExpressionModelApplicationService(ExpressionModelRepository repository, CanonicalMetamodelApplicationService metamodelService, ExpressionTypeChecker typeChecker,
            ExpressionEvaluator evaluator, OperationBehaviorModelApplicationService behaviorService, OperationQueryRuntime operationRuntime) {
        this.repository = repository;
        this.metamodelService = metamodelService;
        this.typeChecker = typeChecker;
        this.evaluator = evaluator;
        this.behaviorService = behaviorService;
        this.operationRuntime = operationRuntime;
    }

    /** Constructor compatible para pruebas y clientes anteriores a O3. */
    /** Descripción: Inicializa una instancia de ExpressionModelApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; typeChecker: dato de entrada necesario para la operación; evaluator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExpressionModelApplicationService(ExpressionModelRepository repository, CanonicalMetamodelApplicationService metamodelService,
            ExpressionTypeChecker typeChecker, ExpressionEvaluator evaluator) {
        this.repository = repository;
        this.metamodelService = metamodelService;
        this.typeChecker = typeChecker;
        this.evaluator = evaluator;
        this.behaviorService = null;
        this.operationRuntime = null;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de ExpressionModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionModelSaveResponse create(String metamodelId, ExpressionModel model) {
        MetaModel metamodel = requireCompatible(metamodelId, model);
        ExpressionModelCheckReport report = checkModel(model, metamodel);
        try {
            StoredExpressionModel stored = repository.create(model);
            return new ExpressionModelSaveResponse(stored.expressionModel(), report, stored.etag());
        } catch(ExpressionModelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por ExpressionModelApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionModelSaveResponse replace(String metamodelId, ExpressionModel model, String expectedEtag) {
        MetaModel metamodel = requireCompatible(metamodelId, model);
        ExpressionModelCheckReport report = checkModel(model, metamodel);
        try {
            StoredExpressionModel stored = repository.replace(metamodelId, model, expectedEtag);
            return new ExpressionModelSaveResponse(stored.expressionModel(), report, stored.etag());
        } catch(ExpressionModelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de ExpressionModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionModelLoadResult find(String metamodelId) {
        requireMetamodel(metamodelId);
        try {
            StoredExpressionModel stored = repository.findById(metamodelId).orElseThrow(() -> new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.NOT_FOUND, "Expression model was not found: " + metamodelId));
            return new ExpressionModelLoadResult(stored.expressionModel(), stored.etag());
        } catch(ExpressionModelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de ExpressionModelApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ExpressionModelSummary> findAll() {
        try {
            return repository.findAll();
        } catch(ExpressionModelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionModelCheckReport check(String metamodelId) {
        ExpressionModel model = find(metamodelId).expressionModel();
        return checkModel(model, requireMetamodel(metamodelId));
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de ExpressionModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; expressionId: identificador que delimita el recurso objetivo; modelDocument: dato de entrada necesario para la operación; selfElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionEvaluationResult evaluate(String metamodelId, String expressionId, CanonicalModelDocument modelDocument, String selfElementId) {
        ExpressionModel model = find(metamodelId).expressionModel();
        if(modelDocument == null || !metamodelId.equals(modelDocument.metamodelId())) {
            throw new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.INVALID_REQUEST, "Model document metamodelId must match the expression model");
        }
        ExpressionDefinition expression = model.findExpression(expressionId).orElseThrow(() -> new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.NOT_FOUND, "Expression was not found: " + expressionId));
        try {
            MetaModel metamodel = requireMetamodel(metamodelId);
            ExpressionEvaluationContext context = new ExpressionEvaluationContext(metamodel, modelDocument, selfElementId, java.util.Map.of());
            if(operationRuntime == null || behaviorService == null) return evaluator.evaluate(expression, context);
            return evaluator.evaluate(expression, context, operationRuntime.create(metamodel, model, behaviorService.findOrEmpty(metamodelId)));
        } catch(ExpressionEvaluationException exception) {
            throw new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.NOT_EXECUTABLE, exception.getMessage(), exception);
        }
    }

    /**
     * Evaluates several boolean conditions for one model element while loading the
     * expression model and query runtime only once. Individual evaluation failures
     * are fail-closed so a visual rule can never break the generated editor view.
     */
    /** Descripción: Ejecuta la operación «evaluateBooleans» correspondiente a la responsabilidad de ExpressionModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; expressionIds: dato de entrada necesario para la operación; modelDocument: dato de entrada necesario para la operación; selfElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String, Boolean> evaluateBooleans(String metamodelId, Collection<String> expressionIds,
            CanonicalModelDocument modelDocument, String selfElementId) {
        if(expressionIds == null || expressionIds.isEmpty()) return Map.of();
        ExpressionModel model = find(metamodelId).expressionModel();
        if(modelDocument == null || !metamodelId.equals(modelDocument.metamodelId()))
            throw new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.INVALID_REQUEST, "Model document metamodelId must match the expression model");
        MetaModel metamodel = requireMetamodel(metamodelId);
        ExpressionEvaluationContext context = new ExpressionEvaluationContext(metamodel, modelDocument, selfElementId == null ? "" : selfElementId, java.util.Map.of());
        var queryRuntime = operationRuntime == null || behaviorService == null ? null : operationRuntime.create(metamodel, model, behaviorService.findOrEmpty(metamodelId));
        Map<String, Boolean> result = new LinkedHashMap<>();
        expressionIds.stream().filter(id -> id != null && !id.isBlank()).distinct().limit(64).forEach(id -> {
            ExpressionDefinition expression = model.findExpression(id).orElse(null);
            if(expression == null) { result.put(id, false); return; }
            try {
                ExpressionEvaluationResult evaluated = queryRuntime == null ? evaluator.evaluate(expression, context) : evaluator.evaluate(expression, context, queryRuntime);
                result.put(id, evaluated.value() instanceof Boolean value && value);
            } catch(RuntimeException exception) { result.put(id, false); }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String expectedEtag) {
        requireMetamodel(metamodelId);
        try {
            repository.delete(metamodelId, expectedEtag);
        } catch(ExpressionModelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Adapta el resultado neutral del kernel semántico al contrato persistente histórico de ExpressionModel. Entradas: model: documento persistente; metamodel: metamodelo canónico. Salida: informe compatible con la API existente. */ private ExpressionModelCheckReport checkModel(ExpressionModel model, MetaModel metamodel) {
        ExpressionBatchCheckResult result = typeChecker.checkBatch(model == null ? "" : model.id(), model == null ? "" : model.metamodelId(), model == null ? List.of() : model.expressions(), metamodel);
        return new ExpressionModelCheckReport(result.ownerId(), result.diagnostics(), result.expressions());
    }

    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireCompatible(String metamodelId, ExpressionModel model) {
        MetaModel metamodel = requireMetamodel(metamodelId);
        if(model == null) throw new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.INVALID_REQUEST, "Expression model must not be null");
        if(!metamodelId.equals(model.id())) throw new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.INVALID_REQUEST, "Expression model id must match the owning metamodel id");
        if(!metamodelId.equals(model.metamodelId())) throw new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.INVALID_REQUEST, "Expression model metamodelId must match the owning metamodel id");
        return metamodel;
    }

    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireMetamodel(String metamodelId) {
        if(metamodelId == null || metamodelId.isBlank()) throw new ExpressionModelApplicationException(ExpressionModelApplicationException.Reason.INVALID_REQUEST, "Metamodel id must not be blank");
        try {
            return metamodelService.findById(metamodelId).metamodel();
        } catch(MetamodelApplicationException exception) {
            ExpressionModelApplicationException.Reason reason = switch(exception.getReason()) {
                case INVALID_REQUEST -> ExpressionModelApplicationException.Reason.INVALID_REQUEST;
                case NOT_FOUND -> ExpressionModelApplicationException.Reason.NOT_FOUND;
                case CONFLICT -> ExpressionModelApplicationException.Reason.CONFLICT;
                case PRECONDITION_FAILED -> ExpressionModelApplicationException.Reason.PRECONDITION_FAILED;
                case VALIDATION_ERROR, STORAGE_ERROR -> ExpressionModelApplicationException.Reason.STORAGE_ERROR;
            };
            throw new ExpressionModelApplicationException(reason, exception.getMessage(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de ExpressionModelApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModelApplicationException translate(ExpressionModelRepositoryException exception) {
        ExpressionModelApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> ExpressionModelApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> ExpressionModelApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> ExpressionModelApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> ExpressionModelApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> ExpressionModelApplicationException.Reason.STORAGE_ERROR;
        };
        return new ExpressionModelApplicationException(reason, exception.getMessage(), exception);
    }
}

package com.example.editor.definition;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.definition.persistence.EditorDefinitionRepository;
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

/** Orquesta persistencia y comprobación de editores configurables. */
@Service
public class EditorDefinitionApplicationService {
    private final EditorDefinitionRepository repository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final ExpressionModelRepository expressionRepository;
    private final ActionModelRepository actionRepository;
    private final EditorDefinitionChecker checker;
    /** Descripción: Inicializa una instancia de EditorDefinitionApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; expressionRepository: dato de entrada necesario para la operación; actionRepository: dato de entrada necesario para la operación; checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorDefinitionApplicationService(EditorDefinitionRepository repository, CanonicalMetamodelApplicationService metamodelService,
            ExpressionModelRepository expressionRepository, ActionModelRepository actionRepository, EditorDefinitionChecker checker) {
        this.repository = repository; this.metamodelService = metamodelService; this.expressionRepository = expressionRepository; this.actionRepository = actionRepository; this.checker = checker;
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de EditorDefinitionApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; definition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionSaveResponse create(String metamodelId, EditorDefinitionModel definition) {
        MetaModel metamodel = requireCompatible(metamodelId, definition);
        EditorDefinitionCheckReport report = checker.check(definition, metamodel, optionalExpressions(metamodelId), optionalActions(metamodelId));
        if(!report.valid()) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.INVALID_REQUEST, "Editor definition contains blocking errors");
        try { StoredCanonicalDocument<EditorDefinitionModel> stored = repository.create(definition); return new EditorDefinitionSaveResponse(stored.document(), stored.etag(), report); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Actualiza el estado gestionado por EditorDefinitionApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; definition: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionSaveResponse replace(String metamodelId, String editorId, EditorDefinitionModel definition, String expectedEtag) {
        MetaModel metamodel = requireCompatible(metamodelId, definition);
        if(!editorId.equals(definition.id())) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.INVALID_REQUEST, "Path editor id and document id must match");
        EditorDefinitionCheckReport report = checker.check(definition, metamodel, optionalExpressions(metamodelId), optionalActions(metamodelId));
        if(!report.valid()) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.INVALID_REQUEST, "Editor definition contains blocking errors");
        try { StoredCanonicalDocument<EditorDefinitionModel> stored = repository.replace(editorId, definition, expectedEtag); return new EditorDefinitionSaveResponse(stored.document(), stored.etag(), report); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorDefinitionApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionLoadResult find(String metamodelId, String editorId) {
        MetaModel metamodel = requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<EditorDefinitionModel> stored = repository.findById(editorId).orElseThrow(() -> new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.NOT_FOUND, "Editor definition was not found: " + editorId));
            if(!metamodelId.equals(stored.document().metamodelId())) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.NOT_FOUND, "Editor definition does not belong to metamodel: " + metamodelId);
            return new EditorDefinitionLoadResult(stored.document(), stored.etag(), checker.check(stored.document(), metamodel, optionalExpressions(metamodelId), optionalActions(metamodelId)));
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de EditorDefinitionApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<EditorDefinitionSummary> list(String metamodelId) {
        requireMetamodel(metamodelId);
        return repository.listIds().stream().map(id -> repository.findById(id).orElseThrow()).filter(item -> item.document().metamodelId().equals(metamodelId))
                .map(item -> new EditorDefinitionSummary(item.document().id(), item.document().metamodelId(), item.document().name(), item.document().representations().size(), item.document().palette().tools().size(), item.document().forms().size(), item.etag())).toList();
    }
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionCheckReport check(String metamodelId, String editorId) { return find(metamodelId, editorId).checkReport(); }
    /** Comprueba un documento sin persistirlo y con las mismas dependencias que create/replace. */
    /** Descripción: Valida las condiciones necesarias para «checkDefinition» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; definition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionCheckReport checkDefinition(String metamodelId, EditorDefinitionModel definition) {
        MetaModel metamodel = requireCompatible(metamodelId, definition);
        return checker.check(definition, metamodel, optionalExpressions(metamodelId), optionalActions(metamodelId));
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String editorId, String expectedEtag) { find(metamodelId, editorId); try { repository.delete(editorId, expectedEtag); } catch(CanonicalDocumentStoreException exception) { throw translate(exception); } }
    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; definition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireCompatible(String metamodelId, EditorDefinitionModel definition) {
        MetaModel metamodel = requireMetamodel(metamodelId);
        if(definition == null) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.INVALID_REQUEST, "Editor definition is required");
        if(!metamodelId.equals(definition.metamodelId())) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.INVALID_REQUEST, "Editor definition metamodelId must match the path");
        return metamodel;
    }
    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel requireMetamodel(String id) {
        try { return metamodelService.findById(id).metamodel(); }
        catch(MetamodelApplicationException exception) { throw new EditorDefinitionApplicationException(exception.getReason() == MetamodelApplicationException.Reason.NOT_FOUND ? EditorDefinitionApplicationException.Reason.NOT_FOUND : EditorDefinitionApplicationException.Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }
    /** Descripción: Ejecuta la operación «optionalExpressions» correspondiente a la responsabilidad de EditorDefinitionApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel optionalExpressions(String metamodelId) {
        try { return expressionRepository.findById(metamodelId).map(value -> value.expressionModel()).orElse(null); }
        catch(ExpressionModelRepositoryException exception) { throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception); }
    }
    /** Descripción: Ejecuta la operación «optionalActions» correspondiente a la responsabilidad de EditorDefinitionApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionModel optionalActions(String metamodelId) {
        try { return actionRepository.findById(metamodelId).map(value -> value.document()).orElse(null); }
        catch(CanonicalDocumentStoreException exception) { throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception); }
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de EditorDefinitionApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorDefinitionApplicationException translate(CanonicalDocumentStoreException exception) {
        EditorDefinitionApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> EditorDefinitionApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> EditorDefinitionApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> EditorDefinitionApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> EditorDefinitionApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> EditorDefinitionApplicationException.Reason.STORAGE_ERROR;
        };
        return new EditorDefinitionApplicationException(reason, exception.getMessage(), exception);
    }
}

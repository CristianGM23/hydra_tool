package com.example.editor.event;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Orquesta contratos personalizados de eventos por metamodelo. */
@Service
public class EventModelApplicationService {
    private final EventDefinitionModelRepository definitionRepository;
    private final EventBindingModelRepository bindingRepository;
    private final ExpressionModelRepository expressionRepository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final EventModelChecker checker;
    /** Descripción: Inicializa una instancia de EventModelApplicationService con sus dependencias y datos de entrada. Entradas: definitionRepository: dato de entrada necesario para la operación; bindingRepository: dato de entrada necesario para la operación; expressionRepository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventModelApplicationService(EventDefinitionModelRepository definitionRepository, EventBindingModelRepository bindingRepository,
            ExpressionModelRepository expressionRepository, CanonicalMetamodelApplicationService metamodelService, EventModelChecker checker) {
        this.definitionRepository = definitionRepository;
        this.bindingRepository = bindingRepository;
        this.expressionRepository = expressionRepository;
        this.metamodelService = metamodelService;
        this.checker = checker;
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de EventModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventModelSaveResponse create(String metamodelId, EventDefinitionModel definitions, EventBindingModel bindings) {
        requireCompatible(metamodelId, definitions, bindings);
        EventModelCheckReport report = checker.check(definitions, bindings, loadExpressions(metamodelId, bindings));
        if(!report.valid()) throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST, "Event contracts contain blocking errors");
        try {
            StoredCanonicalDocument<EventDefinitionModel> savedDefinitions = definitionRepository.create(definitions);
            try {
                StoredCanonicalDocument<EventBindingModel> savedBindings = bindingRepository.create(bindings);
                return response(savedDefinitions, savedBindings, report);
            } catch(RuntimeException exception) {
                definitionRepository.deleteIfExists(metamodelId);
                throw exception;
            }
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Actualiza el estado gestionado por EventModelApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; definitionEtag: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventModelSaveResponse replace(String metamodelId, EventDefinitionModel definitions, String definitionEtag, EventBindingModel bindings, String bindingEtag) {
        requireCompatible(metamodelId, definitions, bindings);
        EventModelCheckReport report = checker.check(definitions, bindings, loadExpressions(metamodelId, bindings));
        if(!report.valid()) throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST, "Event contracts contain blocking errors");
        try {
            StoredCanonicalDocument<EventDefinitionModel> previousDefinitions = definitionRepository.findByMetamodelId(metamodelId)
                    .orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND, "Event definitions were not found: " + metamodelId));
            StoredCanonicalDocument<EventBindingModel> previousBindings = bindingRepository.findByMetamodelId(metamodelId)
                    .orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND, "Event bindings were not found: " + metamodelId));
            requireExpectedEtag(definitionEtag, previousDefinitions.etag(), "event definitions");
            requireExpectedEtag(bindingEtag, previousBindings.etag(), "event bindings");

            boolean definitionsChanged = !previousDefinitions.document().equals(definitions);
            boolean bindingsChanged = !previousBindings.document().equals(bindings);
            if(!definitionsChanged && !bindingsChanged) return response(previousDefinitions, previousBindings, report);

            if(definitionsChanged && !bindingsChanged) {
                StoredCanonicalDocument<EventDefinitionModel> savedDefinitions = definitionRepository.replace(metamodelId, definitions, definitionEtag);
                return response(savedDefinitions, previousBindings, report);
            }
            if(!definitionsChanged) {
                StoredCanonicalDocument<EventBindingModel> savedBindings = bindingRepository.replace(metamodelId, bindings, bindingEtag);
                return response(previousDefinitions, savedBindings, report);
            }

            StoredCanonicalDocument<EventDefinitionModel> savedDefinitions = definitionRepository.replace(metamodelId, definitions, definitionEtag);
            try {
                StoredCanonicalDocument<EventBindingModel> savedBindings = bindingRepository.replace(metamodelId, bindings, bindingEtag);
                return response(savedDefinitions, savedBindings, report);
            } catch(RuntimeException exception) {
                try { definitionRepository.replace(metamodelId, previousDefinitions.document(), savedDefinitions.etag()); }
                catch(RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                throw exception;
            }
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventModelLoadResult find(String metamodelId) {
        requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<EventDefinitionModel> definitions = definitionRepository.findByMetamodelId(metamodelId).orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND, "Event definitions were not found: " + metamodelId));
            StoredCanonicalDocument<EventBindingModel> bindings = bindingRepository.findByMetamodelId(metamodelId).orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND, "Event bindings were not found: " + metamodelId));
            EventModelCheckReport report = checker.check(definitions.document(), bindings.document(), loadExpressions(metamodelId, bindings.document()));
            return new EventModelLoadResult(definitions.document(), definitions.etag(), bindings.document(), bindings.etag(), report);
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de EventModelApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<EventModelSummary> list() {
        Set<String> ids = new LinkedHashSet<>(definitionRepository.listMetamodelIds());
        ids.retainAll(bindingRepository.listMetamodelIds());
        return ids.stream().map(id -> {
            StoredCanonicalDocument<EventDefinitionModel> definitions = definitionRepository.findByMetamodelId(id).orElseThrow();
            StoredCanonicalDocument<EventBindingModel> bindings = bindingRepository.findByMetamodelId(id).orElseThrow();
            return new EventModelSummary(id, definitions.document().id(), definitions.document().eventTypes().size(), bindings.document().id(), bindings.document().bindings().size(), definitions.etag(), bindings.etag());
        }).toList();
    }
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EventModelCheckReport check(String metamodelId) { EventModelLoadResult result = find(metamodelId); return result.checkReport(); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitionEtag: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String definitionEtag, String bindingEtag) {
        requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<EventBindingModel> previousBindings = bindingRepository.findByMetamodelId(metamodelId)
                    .orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND, "Event bindings were not found: " + metamodelId));
            definitionRepository.findByMetamodelId(metamodelId)
                    .orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.NOT_FOUND, "Event definitions were not found: " + metamodelId));
            bindingRepository.delete(metamodelId, bindingEtag);
            try {
                definitionRepository.delete(metamodelId, definitionEtag);
            } catch(RuntimeException exception) {
                try { bindingRepository.create(previousBindings.document()); }
                catch(RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                throw new EventModelApplicationException(EventModelApplicationException.Reason.STORAGE_ERROR,
                        "Event contracts could not be deleted atomically", exception);
            }
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireCompatible(String metamodelId, EventDefinitionModel definitions, EventBindingModel bindings) {
        requireMetamodel(metamodelId);
        if(definitions == null || bindings == null) throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST, "Definitions and bindings are required");
        if(!metamodelId.equals(definitions.metamodelId()) || !metamodelId.equals(bindings.metamodelId())) throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST, "Path metamodel id must match both documents");
        if(!definitions.id().equals(bindings.eventDefinitionModelId())) throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST, "Binding model must reference the supplied event definition model");
    }
    /** Descripción: Obtiene la información correspondiente a «loadExpressions» dentro de EventModelApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; bindings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionModel loadExpressions(String metamodelId, EventBindingModel bindings) {
        if(bindings.expressionModelId().isBlank()) return null;
        try {
            ExpressionModel expressions = expressionRepository.findById(metamodelId).orElseThrow(() -> new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST, "Configured expression model was not found")).expressionModel();
            if(!bindings.expressionModelId().equals(expressions.id())) throw new EventModelApplicationException(EventModelApplicationException.Reason.INVALID_REQUEST, "Binding expressionModelId does not match the owning expression model");
            return expressions;
        } catch(RuntimeException exception) {
            if(exception instanceof EventModelApplicationException typed) throw typed;
            throw new EventModelApplicationException(EventModelApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception);
        }
    }
    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireMetamodel(String id) {
        try { metamodelService.findById(id); }
        catch(MetamodelApplicationException exception) { throw new EventModelApplicationException(exception.getReason() == MetamodelApplicationException.Reason.NOT_FOUND ? EventModelApplicationException.Reason.NOT_FOUND : EventModelApplicationException.Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }
    /** Verifica ambos precondicionantes antes de modificar cualquiera de los dos documentos del contrato de eventos. */
    /** Descripción: Valida las condiciones necesarias para «requireExpectedEtag» y rechaza entradas no válidas. Entradas: expected: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación; resource: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireExpectedEtag(String expected, String current, String resource) {
        if(expected == null || expected.isBlank()) throw new EventModelApplicationException(EventModelApplicationException.Reason.PRECONDITION_FAILED, "If-Match header is required for " + resource);
        String normalized = expected.trim();
        if(!"*".equals(normalized) && !current.equals(normalized)) throw new EventModelApplicationException(EventModelApplicationException.Reason.PRECONDITION_FAILED, "Event contract has changed since it was loaded: " + resource);
    }
        /** Descripción: Ejecuta la operación «response» correspondiente a la responsabilidad de EventModelApplicationService. Entradas: definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventModelSaveResponse response(StoredCanonicalDocument<EventDefinitionModel> definitions, StoredCanonicalDocument<EventBindingModel> bindings, EventModelCheckReport report) {
        return new EventModelSaveResponse(definitions.document(), definitions.etag(), bindings.document(), bindings.etag(), report);
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de EventModelApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EventModelApplicationException translate(CanonicalDocumentStoreException exception) {
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

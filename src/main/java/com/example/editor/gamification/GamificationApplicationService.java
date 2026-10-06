package com.example.editor.gamification;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.gamification.persistence.GamificationBindingRepository;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Orquesta el catálogo y los bindings de gamificación sin acoplarlos al runtime de flujos. */
@Service
public class GamificationApplicationService {
    private final GamificationDefinitionRepository definitionRepository;
    private final GamificationBindingRepository bindingRepository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final EventModelApplicationService eventModelService;
    private final GamificationChecker checker;
    private final GamificationCorruptDocumentRecovery corruptDocumentRecovery;
    @Autowired
    /** Descripción: Inicializa una instancia de GamificationApplicationService con sus dependencias y datos de entrada. Entradas: definitionRepository: dato de entrada necesario para la operación; bindingRepository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; eventModelService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación; corruptDocumentRecovery: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationApplicationService(GamificationDefinitionRepository definitionRepository,
            GamificationBindingRepository bindingRepository, CanonicalMetamodelApplicationService metamodelService,
            EventModelApplicationService eventModelService, GamificationChecker checker, GamificationCorruptDocumentRecovery corruptDocumentRecovery) {
        this.definitionRepository = definitionRepository;
        this.bindingRepository = bindingRepository;
        this.metamodelService = metamodelService;
        this.eventModelService = eventModelService;
        this.checker = checker;
        this.corruptDocumentRecovery = corruptDocumentRecovery;
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de GamificationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationSaveResponse create(String metamodelId, GamificationDefinitionModel definitions, GamificationBindingModel bindings) {
        requireCompatible(metamodelId, definitions, bindings);
        GamificationCheckReport report = checker.check(definitions, bindings, eventDefinitions(metamodelId));
        if(!report.valid()) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Gamification contracts contain blocking errors");
        try {
            StoredCanonicalDocument<GamificationDefinitionModel> savedDefinitions = definitionRepository.create(definitions);
            try {
                StoredCanonicalDocument<GamificationBindingModel> savedBindings = bindingRepository.create(bindings);
                return response(savedDefinitions, savedBindings, report);
            } catch(RuntimeException exception) {
                definitionRepository.deleteIfExists(metamodelId);
                throw exception;
            }
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Actualiza el estado gestionado por GamificationApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; definitionEtag: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationSaveResponse replace(String metamodelId, GamificationDefinitionModel definitions, String definitionEtag,
            GamificationBindingModel bindings, String bindingEtag) {
        requireCompatible(metamodelId, definitions, bindings);
        GamificationCheckReport report = checker.check(definitions, bindings, eventDefinitions(metamodelId));
        if(!report.valid()) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Gamification contracts contain blocking errors");
        try {
            StoredCanonicalDocument<GamificationDefinitionModel> previousDefinitions = definitionRepository.find(metamodelId)
                    .orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification definitions were not found"));
            bindingRepository.find(metamodelId).orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification bindings were not found"));
            StoredCanonicalDocument<GamificationDefinitionModel> savedDefinitions = definitionRepository.replace(metamodelId, definitions, definitionEtag);
            try {
                StoredCanonicalDocument<GamificationBindingModel> savedBindings = bindingRepository.replace(metamodelId, bindings, bindingEtag);
                return response(savedDefinitions, savedBindings, report);
            } catch(RuntimeException exception) {
                try { definitionRepository.replace(metamodelId, previousDefinitions.document(), savedDefinitions.etag()); }
                catch(RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                throw exception;
            }
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GamificationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationLoadResult find(String metamodelId) {
        requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<GamificationDefinitionModel> definitions = definitionRepository.find(metamodelId)
                    .orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification definitions were not found: " + metamodelId));
            StoredCanonicalDocument<GamificationBindingModel> bindings = bindingRepository.find(metamodelId)
                    .orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification bindings were not found: " + metamodelId));
            GamificationCheckReport report = checker.check(definitions.document(), bindings.document(), eventDefinitions(metamodelId));
            return new GamificationLoadResult(definitions.document(), definitions.etag(), bindings.document(), bindings.etag(), report);
        } catch(CanonicalDocumentStoreException exception) {
            if(corruptDocumentRecovery.recoverIfMalformed(metamodelId, exception))
                throw new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND,
                        "Gamification configuration was malformed and has been quarantined. A clean configuration can be created.", exception);
            throw translate(exception);
        }
    }
    /** Descripción: Valida las condiciones necesarias para «checkDraft» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationCheckReport checkDraft(String metamodelId, GamificationDefinitionModel definitions, GamificationBindingModel bindings) {
        requireCompatible(metamodelId, definitions, bindings);
        return checker.check(definitions, bindings, eventDefinitions(metamodelId));
    }
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationCheckReport check(String metamodelId) { return find(metamodelId).checkReport(); }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de GamificationApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<GamificationSummary> list() { return listWithDiagnostics().summaries(); }

    /**
     * Loads each gamification pair independently so one malformed or partially written
     * document cannot make Home readiness, project discovery or unrelated metamodels unavailable.
     */
    /** Descripción: Obtiene la información correspondiente a «listWithDiagnostics» dentro de GamificationApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationCatalogResult listWithDiagnostics() {
        List<GamificationSummary> summaries = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        List<String> ids;
        try { ids = definitionRepository.listIds(); }
        catch(RuntimeException exception) {
            return GamificationCatalogResult.empty("Could not list gamification documents: " + safeMessage(exception));
        }
        for(String id : ids) {
            try {
                StoredCanonicalDocument<GamificationDefinitionModel> definition = definitionRepository.find(id).orElse(null);
                if(definition == null) continue;
                StoredCanonicalDocument<GamificationBindingModel> binding = bindingRepository.find(definition.document().metamodelId()).orElse(null);
                if(binding == null) {
                    diagnostics.add("Gamification bindings are missing for metamodel " + definition.document().metamodelId());
                    continue;
                }
                summaries.add(new GamificationSummary(definition.document().metamodelId(), definition.document().id(),
                        binding.document().rules().size(), definition.document().levels().size(),
                        definition.document().achievements().size(), definition.etag(), binding.etag()));
            } catch(RuntimeException exception) {
                if(exception instanceof CanonicalDocumentStoreException storeException
                        && corruptDocumentRecovery.recoverIfMalformed(id, storeException)) {
                    diagnostics.add("Quarantined malformed gamification document " + id + "; a clean configuration can be created.");
                } else {
                    diagnostics.add("Skipped invalid gamification document " + id + ": " + safeMessage(exception));
                }
            }
        }
        return new GamificationCatalogResult(summaries, diagnostics);
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitionEtag: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String definitionEtag, String bindingEtag) {
        requireMetamodel(metamodelId);
        try {
            StoredCanonicalDocument<GamificationBindingModel> previousBindings = bindingRepository.find(metamodelId)
                    .orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification bindings were not found"));
            definitionRepository.find(metamodelId).orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification definitions were not found"));
            bindingRepository.delete(metamodelId, bindingEtag);
            try { definitionRepository.delete(metamodelId, definitionEtag); }
            catch(RuntimeException exception) {
                try { bindingRepository.create(previousBindings.document()); }
                catch(RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                throw exception;
            }
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }
    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireCompatible(String metamodelId, GamificationDefinitionModel definitions, GamificationBindingModel bindings) {
        requireMetamodel(metamodelId);
        if(definitions == null || bindings == null) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Definitions and bindings are required");
        if(!metamodelId.equals(definitions.metamodelId()) || !metamodelId.equals(bindings.metamodelId())) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Path metamodel id must match both documents");
        if(!definitions.id().equals(bindings.definitionModelId())) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Binding model must reference the supplied definition model");
    }
    /** Descripción: Ejecuta la operación «eventDefinitions» correspondiente a la responsabilidad de GamificationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EventDefinitionModel eventDefinitions(String metamodelId) {
        try { return eventModelService.find(metamodelId).definitions(); }
        catch(EventModelApplicationException exception) {
            if(exception.getReason() == EventModelApplicationException.Reason.NOT_FOUND) return null;
            throw new GamificationApplicationException(GamificationApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception);
        }
    }
    /** Descripción: Valida las condiciones necesarias para «requireMetamodel» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireMetamodel(String id) {
        try { metamodelService.findById(id); }
        catch(MetamodelApplicationException exception) { throw new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Metamodel was not found: " + id, exception); }
    }
    /** Descripción: Ejecuta la operación «response» correspondiente a la responsabilidad de GamificationApplicationService. Entradas: definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationSaveResponse response(StoredCanonicalDocument<GamificationDefinitionModel> definitions,
            StoredCanonicalDocument<GamificationBindingModel> bindings, GamificationCheckReport report) {
        return new GamificationSaveResponse(definitions.document(), definitions.etag(), bindings.document(), bindings.etag(), report);
    }
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de GamificationApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) {
        if(exception == null) return "Unknown storage error";
        String message = exception.getMessage();
        if(message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message;
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de GamificationApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationApplicationException translate(CanonicalDocumentStoreException exception) {
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

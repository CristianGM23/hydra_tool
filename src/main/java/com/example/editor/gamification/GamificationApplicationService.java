package com.example.editor.gamification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.editor.audit.AuditRequestContext;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.gamification.persistence.GamificationBindingRepository;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Orquesta el catálogo y los bindings de gamificación sin acoplarlos al runtime de flujos.
 *
 * <p>Reads never change the stored files: a malformed or incomplete pair is answered with CORRUPT_CONFIGURATION (409) and left as it is;
 * only {@link #quarantine} moves it, explicitly and with fingerprints. Every operation on one pair runs under the bounded per-metamodel
 * monitor of {@link GamificationCorruptDocumentRecovery#lockFor(String)}, so inside this JVM a read never observes half of a create,
 * replace or delete and a quarantine never competes with a write. The project synchronization and the deletion of a metamodel write these
 * files without that monitor (as for every other module); the quarantine re-fingerprints the documents right before moving them.
 */
@Service
public class GamificationApplicationService {
    private static final Logger LOG = LoggerFactory.getLogger(GamificationApplicationService.class);
    private static final JsonMapper RECOVERED_DOCUMENTS = JsonMapper.builder().findAndAddModules().build();
    private enum Status { VALID, MALFORMED, MISSING }
    /** One document of the pair as the store sees it: readable, corrupt or absent. */
    private record Outcome<T>(Status status, StoredCanonicalDocument<T> stored, CanonicalDocumentStoreException failure) {
        T document() { return stored == null ? null : stored.document(); }
    }
    /** Both documents of a pair, classified without changing anything. */
    private record PairState(Outcome<GamificationDefinitionModel> definitions, Outcome<GamificationBindingModel> bindings) {
        boolean absent() { return definitions.status() == Status.MISSING && bindings.status() == Status.MISSING; }
        boolean healthy() { return definitions.status() == Status.VALID && bindings.status() == Status.VALID; }
        String state() {
            if(healthy()) return "HEALTHY";
            if(absent()) return "ABSENT";
            return definitions.status() == Status.MALFORMED || bindings.status() == Status.MALFORMED ? "CORRUPT" : "INCOMPLETE";
        }
        String problems() {
            List<String> problems = new ArrayList<>();
            if(definitions.status() != Status.VALID) problems.add("definitions are " + definitions.status().name().toLowerCase(java.util.Locale.ROOT));
            if(bindings.status() != Status.VALID) problems.add("bindings are " + bindings.status().name().toLowerCase(java.util.Locale.ROOT));
            return String.join("; ", problems);
        }
    }
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
        this.corruptDocumentRecovery = corruptDocumentRecovery == null ? GamificationCorruptDocumentRecovery.disabled() : corruptDocumentRecovery;
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de GamificationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationSaveResponse create(String metamodelId, GamificationDefinitionModel definitions, GamificationBindingModel bindings) {
        requireCompatible(metamodelId, definitions, bindings);
        GamificationCheckReport report = checker.check(definitions, bindings, eventDefinitions(metamodelId));
        if(!report.valid()) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Gamification contracts contain blocking errors");
        synchronized(corruptDocumentRecovery.lockFor(metamodelId)) {
            try {
                // a corrupt or half-stored pair is never mixed with a new document: it has to be quarantined explicitly first
                PairState existing = classify(metamodelId);
                if(!existing.absent() && !existing.healthy()) throw corrupt(metamodelId, existing);
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
    }
    /** Descripción: Actualiza el estado gestionado por GamificationApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; definitionEtag: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationSaveResponse replace(String metamodelId, GamificationDefinitionModel definitions, String definitionEtag,
            GamificationBindingModel bindings, String bindingEtag) {
        requireCompatible(metamodelId, definitions, bindings);
        GamificationCheckReport report = checker.check(definitions, bindings, eventDefinitions(metamodelId));
        if(!report.valid()) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Gamification contracts contain blocking errors");
        synchronized(corruptDocumentRecovery.lockFor(metamodelId)) {
            try {
                PairState current = classify(metamodelId);
                if(current.absent()) throw new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification definitions were not found");
                if(!current.healthy()) throw corrupt(metamodelId, current);
                StoredCanonicalDocument<GamificationDefinitionModel> previousDefinitions = current.definitions().stored();
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
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GamificationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationLoadResult find(String metamodelId) {
        requireMetamodel(metamodelId);
        PairState pair;
        synchronized(corruptDocumentRecovery.lockFor(metamodelId)) {
            try { pair = classify(metamodelId); }
            catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
        }
        // 404 only when nothing is stored; a malformed or half-stored pair is a corrupt configuration and the files stay untouched
        if(pair.absent()) throw new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification definitions were not found: " + metamodelId);
        if(!pair.healthy()) throw corrupt(metamodelId, pair);
        StoredCanonicalDocument<GamificationDefinitionModel> definitions = pair.definitions().stored();
        StoredCanonicalDocument<GamificationBindingModel> bindings = pair.bindings().stored();
        GamificationCheckReport report = checker.check(definitions.document(), bindings.document(), eventDefinitions(metamodelId));
        return new GamificationLoadResult(definitions.document(), definitions.etag(), bindings.document(), bindings.etag(), report);
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
        // diagnostics are shown to the user (Home readiness, catalog): they name the metamodel and the problem, never a server path or an
        // exception message; the details go to the server log under the request correlation id
        TreeSet<String> ids = new TreeSet<>();
        try { ids.addAll(definitionRepository.listIds()); ids.addAll(bindingRepository.listIds()); }
        catch(RuntimeException exception) {
            LOG.warn("hydra.gamification_catalog_unavailable correlationId={}", AuditRequestContext.correlationId(), exception);
            return GamificationCatalogResult.empty("Gamification documents could not be listed because of a storage error; the details were recorded in the server log.");
        }
        for(String id : ids) {
            PairState pair;
            try {
                synchronized(corruptDocumentRecovery.lockFor(id)) { pair = classify(id); }
            } catch(RuntimeException exception) {
                LOG.warn("hydra.gamification_catalog_entry_unreadable metamodelId={} correlationId={}", id, AuditRequestContext.correlationId(), exception);
                diagnostics.add("Gamification configuration of metamodel " + id + " could not be read because of a storage error; the details were recorded in the server log.");
                continue;
            }
            if(pair.absent()) continue;
            if(!pair.healthy()) {
                logCorrupt(id, pair);
                diagnostics.add("Gamification configuration of metamodel " + id + " cannot be used (" + pair.problems()
                        + "); the stored files were left unchanged. Inspect its recovery status before creating a clean configuration.");
                continue;
            }
            StoredCanonicalDocument<GamificationDefinitionModel> definition = pair.definitions().stored();
            StoredCanonicalDocument<GamificationBindingModel> binding = pair.bindings().stored();
            summaries.add(new GamificationSummary(definition.document().metamodelId(), definition.document().id(),
                    binding.document().rules().size(), definition.document().levels().size(),
                    definition.document().achievements().size(), definition.etag(), binding.etag()));
        }
        return new GamificationCatalogResult(summaries, diagnostics);
    }

    /**
     * Read-only recovery status of one pair: what each document is (VALID, MALFORMED or MISSING), the SHA-256 ETag of its exact bytes,
     * the readable content and the quarantines already made. Nothing is changed; it is the input of {@link #quarantine}.
     */
    public GamificationRecoveryReport inspectRecovery(String metamodelId) {
        requireMetamodel(metamodelId);
        synchronized(corruptDocumentRecovery.lockFor(metamodelId)) {
            PairState pair;
            try { pair = classify(metamodelId); }
            catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
            GamificationCorruptDocumentRecovery.StoredFile definitions = corruptDocumentRecovery.stored(metamodelId, GamificationCorruptDocumentRecovery.DEFINITIONS);
            GamificationCorruptDocumentRecovery.StoredFile bindings = corruptDocumentRecovery.stored(metamodelId, GamificationCorruptDocumentRecovery.BINDINGS);
            String state = pair.state();
            String hint = switch(state) {
                case "HEALTHY" -> "The configuration is healthy; nothing has to be recovered.";
                case "ABSENT" -> "No configuration is stored; a new one can be created.";
                default -> "Quarantine the pair with POST /api/canonical-metamodels/" + metamodelId + "/gamification/recovery/quarantine, sending the "
                        + "ETags reported here in If-Match-Definitions and If-Match-Bindings (omit the header of a missing document); the readable "
                        + "document is returned so that a clean configuration can be created from it.";
            };
            return new GamificationRecoveryReport(metamodelId, state, document(definitions, pair.definitions().status()),
                    document(bindings, pair.bindings().status()), pair.definitions().document(), pair.bindings().document(),
                    corruptDocumentRecovery.quarantines(metamodelId), hint);
        }
    }

    /**
     * Explicit quarantine of a corrupt or incomplete pair. The client sends the ETags it inspected (If-Match-Definitions and
     * If-Match-Bindings; no header for a document that is missing, no wildcard): if any stored document is not exactly the one it
     * inspected the answer is 412 and nothing moves; a healthy pair is never quarantined (409). The pair is moved, never deleted, into
     * {@code .invalid-recovery/<id>/<quarantineId>/} with a manifest written before the first move. Repeating the request after an
     * interruption completes the pending quarantine; repeating it after it completed returns the same result (idempotent).
     */
    public GamificationQuarantineResult quarantine(String metamodelId, String expectedDefinitionEtag, String expectedBindingEtag) {
        requireMetamodel(metamodelId);
        String expectedDefinitions = normalizeEtag(expectedDefinitionEtag);
        String expectedBindings = normalizeEtag(expectedBindingEtag);
        synchronized(corruptDocumentRecovery.lockFor(metamodelId)) {
            Optional<GamificationCorruptDocumentRecovery.PendingQuarantine> pending = corruptDocumentRecovery.pending(metamodelId);
            if(pending.isPresent()) {
                if(!sameFingerprints(pending.get().record(), expectedDefinitions, expectedBindings))
                    throw new GamificationApplicationException(GamificationApplicationException.Reason.PRECONDITION_FAILED,
                            "An interrupted quarantine of other documents is pending for metamodel '" + metamodelId + "'; inspect the recovery status and repeat the request with its ETags.");
                return result(metamodelId, true, corruptDocumentRecovery.finish(metamodelId, pending.get()));
            }
            PairState pair;
            try { pair = classify(metamodelId); }
            catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
            GamificationCorruptDocumentRecovery.StoredFile definitions = corruptDocumentRecovery.stored(metamodelId, GamificationCorruptDocumentRecovery.DEFINITIONS);
            GamificationCorruptDocumentRecovery.StoredFile bindings = corruptDocumentRecovery.stored(metamodelId, GamificationCorruptDocumentRecovery.BINDINGS);
            if(!definitions.present() && !bindings.present()) {
                return corruptDocumentRecovery.quarantines(metamodelId).stream()
                        .filter(record -> GamificationQuarantineRecord.COMPLETE.equals(record.status()) && sameFingerprints(record, expectedDefinitions, expectedBindings))
                        .findFirst().map(record -> result(metamodelId, true, record))
                        .orElseThrow(() -> new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND,
                                "No gamification configuration of metamodel '" + metamodelId + "' is stored, so there is nothing to quarantine."));
            }
            requireFingerprint(definitions, "If-Match-Definitions", expectedDefinitions);
            requireFingerprint(bindings, "If-Match-Bindings", expectedBindings);
            if(pair.healthy()) throw new GamificationApplicationException(GamificationApplicationException.Reason.CONFLICT,
                    "The stored gamification configuration of metamodel '" + metamodelId + "' is healthy; there is nothing to quarantine.");
            List<GamificationQuarantineRecord.Document> documents = new ArrayList<>();
            if(definitions.present()) documents.add(quarantined(definitions, pair.definitions().status()));
            if(bindings.present()) documents.add(quarantined(bindings, pair.bindings().status()));
            GamificationCorruptDocumentRecovery.PendingQuarantine started = corruptDocumentRecovery.begin(metamodelId, documents, AuditRequestContext.correlationId());
            return result(metamodelId, false, corruptDocumentRecovery.finish(metamodelId, started));
        }
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitionEtag: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String definitionEtag, String bindingEtag) {
        requireMetamodel(metamodelId);
        synchronized(corruptDocumentRecovery.lockFor(metamodelId)) {
            try {
                PairState current = classify(metamodelId);
                if(current.absent()) throw new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Gamification bindings were not found");
                if(!current.healthy()) throw corrupt(metamodelId, current);
                StoredCanonicalDocument<GamificationBindingModel> previousBindings = current.bindings().stored();
                bindingRepository.delete(metamodelId, bindingEtag);
                try { definitionRepository.delete(metamodelId, definitionEtag); }
                catch(RuntimeException exception) {
                    try { bindingRepository.create(previousBindings.document()); }
                    catch(RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                    throw exception;
                }
            } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
        }
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
        catch(MetamodelApplicationException exception) { if(exception.getReason() == MetamodelApplicationException.Reason.STORAGE_ERROR || exception.getReason() == MetamodelApplicationException.Reason.VALIDATION_ERROR) throw new GamificationApplicationException(GamificationApplicationException.Reason.STORAGE_ERROR, exception.getMessage(), exception); throw new GamificationApplicationException(GamificationApplicationException.Reason.NOT_FOUND, "Metamodel was not found: " + id, exception); }
    }
    /** Descripción: Ejecuta la operación «response» correspondiente a la responsabilidad de GamificationApplicationService. Entradas: definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationSaveResponse response(StoredCanonicalDocument<GamificationDefinitionModel> definitions,
            StoredCanonicalDocument<GamificationBindingModel> bindings, GamificationCheckReport report) {
        return new GamificationSaveResponse(definitions.document(), definitions.etag(), bindings.document(), bindings.etag(), report);
    }
    /** Descripción: Clasifica los dos documentos del par sin modificar nada (VALID, MALFORMED o MISSING); un fallo de E/S o de permisos se propaga. Entradas: metamodelId. Salida: estado del par. */
    private PairState classify(String metamodelId) {
        return new PairState(outcome(() -> definitionRepository.find(metamodelId)), outcome(() -> bindingRepository.find(metamodelId)));
    }
    /** Descripción: Clasifica un documento: legible, corrupto (marca explícita del almacén) o ausente. Entradas: lectura del repositorio. Salida: resultado. */
    private static <T> Outcome<T> outcome(Supplier<Optional<StoredCanonicalDocument<T>>> find) {
        try { return find.get().map(stored -> new Outcome<>(Status.VALID, stored, null)).orElseGet(() -> new Outcome<>(Status.MISSING, null, null)); }
        catch(CanonicalDocumentStoreException exception) {
            if(exception.isMalformed()) return new Outcome<>(Status.MALFORMED, null, exception);
            throw exception;
        }
    }
    /** Descripción: Construye el 409 de una configuración corrupta o incompleta: mensaje público sin rutas y aviso en el log con el correlationId y la causa. Entradas: metamodelId y estado del par. Salida: la excepción. */
    private GamificationApplicationException corrupt(String metamodelId, PairState pair) {
        logCorrupt(metamodelId, pair);
        return new GamificationApplicationException(GamificationApplicationException.Reason.CORRUPT_CONFIGURATION,
                "The stored gamification configuration of metamodel '" + metamodelId + "' cannot be used: " + pair.problems()
                + ". The stored files were left unchanged. Inspect GET /api/canonical-metamodels/" + metamodelId + "/gamification/recovery and quarantine "
                + "the pair explicitly with POST /api/canonical-metamodels/" + metamodelId + "/gamification/recovery/quarantine before creating a clean configuration.");
    }
    /** Descripción: Registra en el log (no en la respuesta) qué documento está corrupto y por qué. Entradas: metamodelId y estado del par. Salida: no devuelve valor. */
    private void logCorrupt(String metamodelId, PairState pair) {
        LOG.warn("hydra.gamification_corrupt_configuration metamodelId={} correlationId={} state={} problems={} definitionsCause={} bindingsCause={}",
                metamodelId, AuditRequestContext.correlationId(), pair.state(), pair.problems(), cause(pair.definitions()), cause(pair.bindings()));
    }
    /** Descripción: Causa técnica de un documento corrupto para el log. Entradas: resultado de un documento. Salida: mensaje o vacío. */
    private static String cause(Outcome<?> outcome) {
        if(outcome.failure() == null) return "";
        Throwable root = outcome.failure().getCause();
        return outcome.failure().getMessage() + (root == null ? "" : " (" + root.getClass().getSimpleName() + ")");
    }
    /** Descripción: Vista pública de un documento para la inspección. Entradas: bytes almacenados y estado. Salida: documento sin rutas. */
    private static GamificationRecoveryReport.Document document(GamificationCorruptDocumentRecovery.StoredFile stored, Status status) {
        return new GamificationRecoveryReport.Document(stored.role(), stored.fileName(), status.name(), stored.present() ? stored.etag() : "", stored.present() ? stored.sizeBytes() : 0);
    }
    /** Descripción: Documento pendiente del manifiesto con la huella observada. Entradas: bytes almacenados y estado. Salida: documento del manifiesto. */
    private static GamificationQuarantineRecord.Document quarantined(GamificationCorruptDocumentRecovery.StoredFile stored, Status status) {
        return new GamificationQuarantineRecord.Document(stored.role(), stored.fileName(), stored.etag(), stored.sizeBytes(), status.name(),
                GamificationQuarantineRecord.Document.PENDING);
    }
    /** Descripción: Exige que la precondición del cliente describa exactamente el documento almacenado: su ETag si existe, ninguna cabecera si falta; nunca un comodín. Entradas: documento, nombre de la cabecera y valor recibido. Salida: no devuelve valor o lanza 412. */
    private static void requireFingerprint(GamificationCorruptDocumentRecovery.StoredFile stored, String header, String expected) {
        GamificationApplicationException.Reason failed = GamificationApplicationException.Reason.PRECONDITION_FAILED;
        if(!stored.present()) {
            if(expected != null) throw new GamificationApplicationException(failed, "The gamification " + stored.role() + " document is no longer stored; inspect the recovery status again.");
            return;
        }
        if(expected == null) throw new GamificationApplicationException(failed, header + " is required to quarantine the stored " + stored.role() + " document; send the ETag reported by the recovery status.");
        if("*".equals(expected)) throw new GamificationApplicationException(failed, "A wildcard is not accepted in " + header + "; send the ETag reported by the recovery status.");
        if(!expected.equals(stored.etag())) throw new GamificationApplicationException(failed, "The stored gamification " + stored.role() + " document changed after it was inspected; nothing was moved. Inspect the recovery status again.");
    }
    /** Descripción: Compara las huellas registradas en una cuarentena con las que envía el cliente. Entradas: registro y ETags esperados (null = ausente). Salida: true si coinciden en los dos papeles. */
    private static boolean sameFingerprints(GamificationQuarantineRecord record, String expectedDefinitions, String expectedBindings) {
        return java.util.Objects.equals(record.etagOf(GamificationCorruptDocumentRecovery.DEFINITIONS), expectedDefinitions)
                && java.util.Objects.equals(record.etagOf(GamificationCorruptDocumentRecovery.BINDINGS), expectedBindings);
    }
    /** Descripción: Normaliza una cabecera If-Match: vacía o ausente equivale a null. Entradas: valor recibido. Salida: valor recortado o null. */
    private static String normalizeEtag(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    /** Descripción: Resultado de la cuarentena con los documentos legibles leídos de su copia en cuarentena (así se comprueba que la copia está íntegra). Entradas: metamodelId, si es una repetición y el manifiesto. Salida: resultado público. */
    private GamificationQuarantineResult result(String metamodelId, boolean replayed, GamificationQuarantineRecord record) {
        GamificationDefinitionModel definitions = recovered(metamodelId, record, GamificationCorruptDocumentRecovery.DEFINITIONS, GamificationDefinitionModel.class);
        GamificationBindingModel bindings = recovered(metamodelId, record, GamificationCorruptDocumentRecovery.BINDINGS, GamificationBindingModel.class);
        String next = "The documents were moved to quarantine " + record.quarantineId() + " and nothing was deleted; a clean configuration can now be created with "
                + "POST /api/canonical-metamodels/" + metamodelId + "/gamification" + (definitions != null || bindings != null ? ", reusing the readable document returned here." : ".");
        return new GamificationQuarantineResult(metamodelId, replayed, record, definitions, bindings, next);
    }
    /** Descripción: Lee de la cuarentena un documento que era legible. Entradas: metamodelId, manifiesto, papel y tipo. Salida: el modelo o null. */
    private <T> T recovered(String metamodelId, GamificationQuarantineRecord record, String role, Class<T> type) {
        return record.documents().stream()
                .filter(document -> role.equals(document.role()) && Status.VALID.name().equals(document.state())
                        && GamificationQuarantineRecord.Document.MOVED.equals(document.outcome()))
                .findFirst()
                .flatMap(document -> corruptDocumentRecovery.quarantined(metamodelId, record.quarantineId(), document.fileName()))
                .map(bytes -> {
                    try { return RECOVERED_DOCUMENTS.readValue(bytes, type); }
                    catch(JacksonException exception) {
                        LOG.warn("hydra.gamification_quarantined_document_unreadable metamodelId={} quarantineId={} role={}", metamodelId, record.quarantineId(), role, exception);
                        return null;
                    }
                }).orElse(null);
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

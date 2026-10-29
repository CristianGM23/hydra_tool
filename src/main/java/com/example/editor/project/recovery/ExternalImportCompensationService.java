package com.example.editor.project.recovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.example.editor.definition.EditorDefinitionApplicationException;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.assets.EditorVisualAssetService;
import com.example.editor.io.BoundedIo;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectSession;
import com.example.editor.project.storage.ProjectDescriptorStore;
import com.example.editor.project.storage.ProjectFileOperations;
import com.example.editor.project.storage.ProjectRegistryStore;
import com.example.editor.project.storage.SafeProjectPathResolver;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationException;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationException;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelApplicationException;
import com.example.editor.validation.validation.ValidationModelApplicationService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Compensación persistente de las importaciones externas.
 *
 * <p><b>Protocolo.</b> Antes de cada efecto sobre los almacenes canónicos (que viven fuera del proyecto) se persiste en un
 * {@link ExternalImportJournal} su intención con la titularidad (creado, reutilizado, sustituido) y, para lo modificado, el
 * estado anterior. Tras el efecto se marca como aplicado. Una interrupción entre ambos pasos deja un efecto
 * {@code INTENDED} cuyo resultado real se averigua al recuperar, comparando el estado actual del recurso con lo registrado.
 *
 * <p><b>Compensación.</b> Se basa en el estado, no en un registro de acciones, y por eso es idempotente: cada efecto se
 * revierte comparando el recurso actual con lo registrado (se borra solo lo propio, se restaura lo sustituido, no se toca lo
 * reutilizado ni lo ajeno). Cada paso queda anotado en el diario; lo que no se completa se conserva con su error y se
 * reintenta al arrancar el servidor y antes de cada importación.
 *
 * <p><b>Concurrencia.</b> Las importaciones son exclusivas (cerrojo en la JVM y de fichero), de modo que ningún otro escritor
 * de esta clase de operaciones toca los mismos recursos mientras una está en curso.
 */
@Service
public class ExternalImportCompensationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalImportCompensationService.class);
    static final String OPERATION_MARKER = "recovery/external-import-operation.json";

    private final CanonicalMetamodelApplicationService metamodels;
    private final EditorDefinitionApplicationService editors;
    private final ValidationModelApplicationService validations;
    private final ActionModelApplicationService actions;
    private final ExpressionModelApplicationService expressions;
    private final MetamodelDiagramStorageService diagrams;
    private final EditorVisualAssetService assets;
    private final ExternalImportJournalStore store;
    private final ObjectMapper mapper;
    private final ProjectApplicationService projects;
    private final ProjectFileOperations files;
    private final ProjectRegistryStore registry;
    private final SafeProjectPathResolver paths;

    /** Inicializa la compensación con los almacenes canónicos, el diario durable y lo necesario para limpiar un proyecto creado por la operación. */
    /** Descripción: Inicializa una instancia de ExternalImportCompensationService con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; diagrams: dato de entrada necesario para la operación; assets: dato de entrada necesario para la operación; store: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; projects: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse; registry: dato de entrada necesario para la operación; paths: ruta o ubicación utilizada por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalImportCompensationService(CanonicalMetamodelApplicationService metamodels, EditorDefinitionApplicationService editors,
            ValidationModelApplicationService validations, ActionModelApplicationService actions, ExpressionModelApplicationService expressions,
            MetamodelDiagramStorageService diagrams, EditorVisualAssetService assets, ExternalImportJournalStore store, ObjectMapper mapper,
            ProjectApplicationService projects, ProjectFileOperations files, ProjectRegistryStore registry, SafeProjectPathResolver paths) {
        this.metamodels = metamodels;
        this.editors = editors;
        this.validations = validations;
        this.actions = actions;
        this.expressions = expressions;
        this.diagrams = diagrams;
        this.assets = assets;
        this.store = store;
        this.mapper = mapper;
        this.projects = projects;
        this.files = files;
        this.registry = registry;
        this.paths = paths;
    }

    /**
     * Abre una operación: espera la exclusividad, recupera lo que hubiera quedado incompleto y escribe el diario inicial.
     * Si el diario no se puede escribir, la operación no empieza (no se ha tocado nada).
     */
    /** Descripción: Ejecuta la operación «begin» correspondiente a la responsabilidad de CompensationReport. Entradas: inputFingerprint: dato de entrada necesario para la operación; confirmedFingerprint: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Ledger begin(String inputFingerprint, String confirmedFingerprint, String projectName) {
        ExternalImportJournalStore.Hold hold = store.acquire();
        try {
            recoverLocked();
            String operationId = "imp-" + UUID.randomUUID();
            ExternalImportJournal journal = ExternalImportJournal.start(operationId, inputFingerprint, confirmedFingerprint, projectName);
            store.save(journal);
            return new Ledger(journal, hold);
        } catch(RuntimeException exception) {
            hold.close();
            throw exception;
        }
    }

    /** Recupera las operaciones incompletas al arrancar el servidor; un fallo aquí no impide el arranque. */
    @EventListener(ApplicationReadyEvent.class)
    /** Descripción: Ejecuta la operación «recoverOnStartup» correspondiente a la responsabilidad de CompensationReport. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void recoverOnStartup() {
        try { recoverIncomplete(); }
        catch(RuntimeException exception) { LOGGER.error("Recovery of incomplete external imports failed; the journals are kept for the next attempt", exception); }
    }

    /** Recupera las operaciones incompletas si nadie más tiene la exclusividad; devuelve un informe por operación recuperada. */
    /** Descripción: Ejecuta la operación «recoverIncomplete» correspondiente a la responsabilidad de CompensationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<CompensationReport> recoverIncomplete() {
        // Sin diarios no hay nada que recuperar: no se crea el directorio ni el cerrojo (arrancar no debe escribir en el almacenamiento).
        // Una importación en curso siempre tiene su diario escrito antes de su primer efecto, así que no se pierde ninguna recuperación.
        if(store.operationIds().isEmpty()) return List.of();
        ExternalImportJournalStore.Hold hold = store.tryAcquire();
        if(hold == null) return List.of();
        try { return recoverLocked(); }
        finally { hold.close(); }
    }

    /** Descripción de lo que sigue pendiente en los diarios conservados (vacío si todo está limpio). */
    /** Descripción: Ejecuta la operación «pendingDescriptions» correspondiente a la responsabilidad de CompensationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> pendingDescriptions() {
        List<String> pending = new ArrayList<>();
        for(String id : store.operationIds()) {
            try {
                ExternalImportJournal journal = store.load(id).orElse(null);
                if(journal == null || journal.terminal()) continue;
                for(ExternalImportJournal.Effect effect : journal.effects()) {
                    if(!ExternalImportJournal.DONE.equals(effect.compensation())) pending.add(id + ":" + effect.id());
                }
                if(journal.effects().isEmpty()) pending.add(id + ":operation-without-effects");
            } catch(RuntimeException exception) { pending.add(id + ":unreadable"); }
        }
        return pending;
    }

    /** Revierte los efectos de una operación del ledger (usado cuando la importación falla en esta misma ejecución). */
    /** Descripción: Ejecuta la operación «compensate» correspondiente a la responsabilidad de CompensationReport. Entradas: ledger: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CompensationReport compensate(Ledger ledger) { return compensate(ledger.journal(), ledger); }

    /** Descripción: Ejecuta la operación «recoverLocked» correspondiente a la responsabilidad de CompensationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<CompensationReport> recoverLocked() {
        List<CompensationReport> reports = new ArrayList<>();
        for(String id : store.operationIds()) {
            try {
                ExternalImportJournal journal = store.load(id).orElse(null);
                if(journal == null) continue;
                if(journal.terminal()) { store.delete(id); continue; }
                reports.add(compensate(journal, null));
            } catch(RuntimeException exception) {
                LOGGER.error("The external import journal {} could not be recovered; it is kept for manual review", id, exception);
                reports.add(new CompensationReport(id, false, List.of(), List.of(id + ": journal unreadable or not recoverable (" + message(exception) + ")")));
            }
        }
        return reports;
    }

    /** Descripción: Ejecuta la operación «compensate» correspondiente a la responsabilidad de CompensationReport. Entradas: start: dato de entrada necesario para la operación; ledger: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CompensationReport compensate(ExternalImportJournal start, Ledger ledger) {
        ExternalImportJournal journal = start.withState(ExternalImportJournal.COMPENSATING);
        persistQuietly(journal, ledger);
        List<String> done = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<ExternalImportJournal.Effect> reversed = new ArrayList<>(journal.effects());
        java.util.Collections.reverse(reversed);
        for(ExternalImportJournal.Effect effect : reversed) {
            if(ExternalImportJournal.DONE.equals(effect.compensation())) { done.add(effect.id()); continue; }
            String error;
            try { error = compensateEffect(journal, effect); }
            catch(RuntimeException exception) { error = message(exception); }
            ExternalImportJournal.Effect updated = error == null ? effect.withCompensation(ExternalImportJournal.DONE, "")
                    : effect.withCompensation(ExternalImportJournal.FAILED, error);
            journal = journal.withEffect(updated);
            persistQuietly(journal, ledger);
            if(error == null) done.add(effect.id()); else failed.add(effect.id() + " (" + error + ")");
        }
        if(failed.isEmpty()) {
            try { store.delete(journal.operationId()); }
            catch(RuntimeException exception) { LOGGER.warn("Compensation finished but the journal {} could not be deleted; it will be deleted on the next recovery", journal.operationId(), exception); }
        } else {
            persistQuietly(journal.withState(ExternalImportJournal.COMPENSATION_FAILED).withFailures(failed), ledger);
        }
        return new CompensationReport(journal.operationId(), failed.isEmpty(), done, failed);
    }

    /** Persistencia del estado de la compensación: un fallo no la interrumpe porque la compensación se basa en el estado real y es repetible. */
    /** Descripción: Ejecuta la operación «persistQuietly» correspondiente a la responsabilidad de CompensationReport. Entradas: journal: dato de entrada necesario para la operación; ledger: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persistQuietly(ExternalImportJournal journal, Ledger ledger) {
        if(ledger != null) ledger.replace(journal);
        try { store.save(journal); }
        catch(RuntimeException exception) { LOGGER.warn("The compensation state of {} could not be persisted; the compensation is state-based and can be repeated", journal.operationId(), exception); }
    }

    /** Devuelve {@code null} si el efecto queda compensado, o el motivo de que no se pudiera. */
    /** Descripción: Ejecuta la operación «compensateEffect» correspondiente a la responsabilidad de CompensationReport. Entradas: journal: dato de entrada necesario para la operación; effect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String compensateEffect(ExternalImportJournal journal, ExternalImportJournal.Effect effect) {
        return switch(effect.kind()) {
            case ExternalImportJournal.METAMODEL -> compensateMetamodel(effect);
            case ExternalImportJournal.METAMODEL_LAYERS -> compensateLayers(effect);
            case ExternalImportJournal.ASSET -> compensateAsset(effect);
            case ExternalImportJournal.PROJECT -> compensateProject(journal, effect);
            default -> "unknown effect kind " + effect.kind();
        };
    }

    /** Un metamodelo creado por la operación se borra con sus capas solo si su contenido sigue siendo el que ella iba a crear. */
    /** Descripción: Ejecuta la operación «compensateMetamodel» correspondiente a la responsabilidad de CompensationReport. Entradas: effect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String compensateMetamodel(ExternalImportJournal.Effect effect) {
        if(!ExternalImportJournal.CREATED.equals(effect.ownership())) return null;
        String id = effect.resourceId();
        String etag = null;
        try { etag = metamodels.findById(id).etag(); }
        catch(MetamodelApplicationException exception) {
            if(exception.getReason() != MetamodelApplicationException.Reason.NOT_FOUND) throw exception;
        }
        if(etag == null) {
            if(diagrams != null) diagrams.delete(id);
            return null;
        }
        if(effect.expected() != null && !effect.expected().equals(mapper.valueToTree(metamodels.findById(id).metamodel()))) {
            return "the metamodel '" + id + "' differs from the one this operation intended to create; it was not deleted (manual review needed)";
        }
        for(var editor : editors.list(id)) editors.delete(id, editor.id(), editor.etag());
        deleteValidation(id);
        deleteAction(id);
        deleteExpression(id);
        if(diagrams != null) diagrams.delete(id);
        metamodels.delete(id, etag);
        return null;
    }

    /** Restaura el estado anterior de editores y de los modelos de validación, acciones y expresiones de un metamodelo reutilizado. */
    /** Descripción: Ejecuta la operación «compensateLayers» correspondiente a la responsabilidad de CompensationReport. Entradas: effect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String compensateLayers(ExternalImportJournal.Effect effect) {
        String id = effect.resourceId();
        JsonNode snapshot = effect.snapshot();
        if(snapshot == null || snapshot.isNull()) return "no snapshot was recorded for the layers of '" + id + "'";
        try { metamodels.findById(id); }
        catch(MetamodelApplicationException exception) {
            if(exception.getReason() == MetamodelApplicationException.Reason.NOT_FOUND) return null;
            throw exception;
        }
        JsonNode previousEditors = snapshot.get("editors");
        java.util.Set<String> keep = new java.util.LinkedHashSet<>();
        if(previousEditors != null) previousEditors.properties().forEach(entry -> keep.add(entry.getKey()));
        for(var current : editors.list(id)) {
            if(!keep.contains(current.id())) editors.delete(id, current.id(), current.etag());
        }
        if(previousEditors != null) {
            for(var entry : previousEditors.properties()) {
                EditorDefinitionModel previous = mapper.treeToValue(entry.getValue(), EditorDefinitionModel.class);
                try {
                    var loaded = editors.find(id, entry.getKey());
                    if(!loaded.editorDefinition().equals(previous)) editors.replace(id, entry.getKey(), previous, loaded.etag());
                } catch(EditorDefinitionApplicationException exception) {
                    if(exception.getReason() != EditorDefinitionApplicationException.Reason.NOT_FOUND) throw exception;
                    editors.create(id, previous);
                }
            }
        }
        restoreValidation(id, node(snapshot, "validation"), ValidationModel.class);
        restoreAction(id, node(snapshot, "action"), ActionModel.class);
        restoreExpression(id, node(snapshot, "expression"), ExpressionModel.class);
        return null;
    }

    /** Solo se borra un recurso gráfico que la operación creó; los que ya existían son compartidos. */
    /** Descripción: Ejecuta la operación «compensateAsset» correspondiente a la responsabilidad de CompensationReport. Entradas: effect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String compensateAsset(ExternalImportJournal.Effect effect) {
        if(effect.existedBefore()) return null;
        assets.deleteIfPresent(effect.resourceId());
        return null;
    }

    /**
     * Elimina el proyecto que creó la operación únicamente si lleva su marca (el diario y el fichero
     * {@code recovery/external-import-operation.json} comparten el identificador) y no es el proyecto abierto.
     */
    /** Descripción: Ejecuta la operación «compensateProject» correspondiente a la responsabilidad de CompensationReport. Entradas: journal: dato de entrada necesario para la operación; effect: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String compensateProject(ExternalImportJournal journal, ExternalImportJournal.Effect effect) {
        Path root = paths.projectRoot(effect.resourceId());
        if(!Files.exists(root)) return null;
        Path marker = root.resolve(OPERATION_MARKER);
        try {
            if(!Files.isRegularFile(marker) || !Files.isRegularFile(root.resolve(ProjectDescriptorStore.FILE_NAME))
                    || !journal.operationId().equals(mapper.readTree(BoundedIo.readInternal(marker)).path("operationId").asString(""))) {
                return "the directory " + root + " exists without this operation's ownership marker; it was not deleted (manual review needed)";
            }
            // La marca prueba que el proyecto es de esta operación. Si HYDRA lo reabrió al arrancar (último proyecto activo) se cierra antes de borrar;
            // un proyecto abierto que NO lleve la marca nunca llega aquí.
            ProjectSession session = projects.session();
            if(session != null && session.active() && session.directory() != null && !session.directory().isBlank()
                    && paths.projectRoot(session.directory()).equals(root)) {
                projects.close();
            }
            files.deleteRecursively(root);
            registry.forget(root.toString());
            return null;
        } catch(java.io.IOException exception) {
            return "could not inspect the project directory: " + message(exception);
        }
    }

    /** Descripción: Ejecuta la operación «node» correspondiente a la responsabilidad de CompensationReport. Entradas: snapshot: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static JsonNode node(JsonNode snapshot, String field) {
        JsonNode value = snapshot.get(field);
        return value == null || value.isNull() ? null : value;
    }

    /** Descripción: Ejecuta la operación «restoreValidation» correspondiente a la responsabilidad de CompensationReport. Entradas: id: identificador que delimita el recurso objetivo; previous: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void restoreValidation(String id, JsonNode previous, Class<ValidationModel> type) {
        ValidationModel model = previous == null ? null : mapper.treeToValue(previous, type);
        try {
            var current = validations.find(id);
            if(model == null) validations.delete(id, current.etag());
            else if(!current.validationModel().equals(model)) validations.replace(id, model, current.etag());
        } catch(ValidationModelApplicationException exception) {
            if(exception.getReason() != ValidationModelApplicationException.Reason.NOT_FOUND) throw exception;
            if(model != null) validations.create(id, model);
        }
    }

    /** Descripción: Ejecuta la operación «restoreAction» correspondiente a la responsabilidad de CompensationReport. Entradas: id: identificador que delimita el recurso objetivo; previous: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void restoreAction(String id, JsonNode previous, Class<ActionModel> type) {
        ActionModel model = previous == null ? null : mapper.treeToValue(previous, type);
        try {
            var current = actions.find(id);
            if(model == null) actions.delete(id, current.etag());
            else if(!current.actionModel().equals(model)) actions.replace(id, model, current.etag());
        } catch(ActionModelApplicationException exception) {
            if(exception.getReason() != ActionModelApplicationException.Reason.NOT_FOUND) throw exception;
            if(model != null) actions.create(id, model);
        }
    }

    /** Descripción: Ejecuta la operación «restoreExpression» correspondiente a la responsabilidad de CompensationReport. Entradas: id: identificador que delimita el recurso objetivo; previous: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void restoreExpression(String id, JsonNode previous, Class<ExpressionModel> type) {
        ExpressionModel model = previous == null ? null : mapper.treeToValue(previous, type);
        try {
            var current = expressions.find(id);
            if(model == null) expressions.delete(id, current.etag());
            else if(!current.expressionModel().equals(model)) expressions.replace(id, model, current.etag());
        } catch(ExpressionModelApplicationException exception) {
            if(exception.getReason() != ExpressionModelApplicationException.Reason.NOT_FOUND) throw exception;
            if(model != null) expressions.create(id, model);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteValidation» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteValidation(String id) {
        try { validations.delete(id, validations.find(id).etag()); }
        catch(ValidationModelApplicationException exception) { if(exception.getReason() != ValidationModelApplicationException.Reason.NOT_FOUND) throw exception; }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteAction» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteAction(String id) {
        try { actions.delete(id, actions.find(id).etag()); }
        catch(ActionModelApplicationException exception) { if(exception.getReason() != ActionModelApplicationException.Reason.NOT_FOUND) throw exception; }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteExpression» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteExpression(String id) {
        try { expressions.delete(id, expressions.find(id).etag()); }
        catch(ExpressionModelApplicationException exception) { if(exception.getReason() != ExpressionModelApplicationException.Reason.NOT_FOUND) throw exception; }
    }

    /** Estado previo de las capas de un metamodelo, serializado para el diario. */
    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de CompensationReport. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ObjectNode snapshot(String id) {
        ObjectNode snapshot = mapper.createObjectNode();
        ObjectNode previousEditors = snapshot.putObject("editors");
        for(var summary : editors.list(id)) previousEditors.set(summary.id(), mapper.valueToTree(editors.find(id, summary.id()).editorDefinition()));
        snapshot.set("validation", optional(() -> validations.find(id).validationModel(), ValidationModelApplicationException.class,
                value -> value.getReason() == ValidationModelApplicationException.Reason.NOT_FOUND));
        snapshot.set("action", optional(() -> actions.find(id).actionModel(), ActionModelApplicationException.class,
                value -> value.getReason() == ActionModelApplicationException.Reason.NOT_FOUND));
        snapshot.set("expression", optional(() -> expressions.find(id).expressionModel(), ExpressionModelApplicationException.class,
                value -> value.getReason() == ExpressionModelApplicationException.Reason.NOT_FOUND));
        return snapshot;
    }

    /** Descripción: Ejecuta la operación «optional» correspondiente a la responsabilidad de CompensationReport. Entradas: supplier: dato de entrada necesario para la operación; missing: dato de entrada necesario para la operación; isMissing: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <E extends RuntimeException> JsonNode optional(java.util.function.Supplier<Object> supplier, Class<E> missing, java.util.function.Predicate<E> isMissing) {
        try { return mapper.valueToTree(supplier.get()); }
        catch(RuntimeException exception) {
            if(missing.isInstance(exception) && isMissing.test(missing.cast(exception))) return mapper.nullNode();
            throw exception;
        }
    }

    /** Descripción: Ejecuta la operación «message» correspondiente a la responsabilidad de CompensationReport. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String message(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /** Resultado de una compensación: operación, si terminó, qué se revirtió y qué sigue pendiente. */
    public record CompensationReport(String operationId, boolean complete, List<String> completed, List<String> pending) { }

    /** Una operación de importación en curso: alimenta el diario durable y mantiene la exclusividad hasta cerrarse. */
    public final class Ledger implements ExternalImportListener, AutoCloseable {
        private volatile ExternalImportJournal journal;
        private final ExternalImportJournalStore.Hold hold;

        /** Descripción: Inicializa una instancia de Ledger con sus dependencias y datos de entrada. Entradas: journal: dato de entrada necesario para la operación; hold: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Ledger(ExternalImportJournal journal, ExternalImportJournalStore.Hold hold) { this.journal = journal; this.hold = hold; }

        /** Identidad estable de la operación. */
        /** Descripción: Ejecuta la operación «operationId» correspondiente a la responsabilidad de CompensationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String operationId() { return journal.operationId(); }

        /** Descripción: Ejecuta la operación «journal» correspondiente a la responsabilidad de CompensationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ ExternalImportJournal journal() { return journal; }

        /** Descripción: Actualiza el estado gestionado por CompensationReport mediante la operación «replace». Entradas: updated: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void replace(ExternalImportJournal updated) { this.journal = updated; }

        /** Actualiza el diario en memoria y lo persiste; si no se puede persistir se propaga el error (el efecto no se ejecuta o se compensa). */
        private synchronized void record(ExternalImportJournal updated) {
            this.journal = updated;
            store.save(updated);
        }

        @Override public synchronized void metamodelAboutToBeCreated(MetaModel metamodel) {
            record(journal.withEffect(ExternalImportJournal.Effect.intended(ExternalImportJournal.METAMODEL, metamodel.id(),
                    ExternalImportJournal.CREATED, false, mapper.valueToTree(metamodel), null)));
        }

        @Override public synchronized void metamodelCreated(String metamodelId) {
            ExternalImportJournal.Effect effect = journal.effect(ExternalImportJournal.Effect.idOf(ExternalImportJournal.METAMODEL, metamodelId));
            if(effect != null) record(journal.withEffect(effect.withStatus(ExternalImportJournal.APPLIED)));
        }

        @Override public synchronized void metamodelReused(String metamodelId) {
            String layers = ExternalImportJournal.Effect.idOf(ExternalImportJournal.METAMODEL_LAYERS, metamodelId);
            if(journal.effect(layers) != null || journal.effect(ExternalImportJournal.Effect.idOf(ExternalImportJournal.METAMODEL, metamodelId)) != null) return;
            ExternalImportJournal.Effect reused = ExternalImportJournal.Effect.intended(ExternalImportJournal.METAMODEL, metamodelId,
                    ExternalImportJournal.REUSED, true, null, null).withStatus(ExternalImportJournal.APPLIED);
            ExternalImportJournal.Effect replaced = ExternalImportJournal.Effect.intended(ExternalImportJournal.METAMODEL_LAYERS, metamodelId,
                    ExternalImportJournal.REPLACED, true, null, snapshot(metamodelId)).withStatus(ExternalImportJournal.APPLIED);
            record(journal.withEffect(reused).withEffect(replaced));
        }

        @Override public synchronized void assetAboutToBeStored(String portablePath, boolean existedBefore) {
            if(journal.effect(ExternalImportJournal.Effect.idOf(ExternalImportJournal.ASSET, portablePath)) != null) return;
            record(journal.withEffect(ExternalImportJournal.Effect.intended(ExternalImportJournal.ASSET, portablePath,
                    existedBefore ? ExternalImportJournal.REUSED : ExternalImportJournal.CREATED, existedBefore, null, null)));
        }

        @Override public synchronized void assetStored(String portablePath, boolean created) {
            ExternalImportJournal.Effect effect = journal.effect(ExternalImportJournal.Effect.idOf(ExternalImportJournal.ASSET, portablePath));
            if(effect != null) record(journal.withEffect(effect.withStatus(ExternalImportJournal.APPLIED)));
        }

        /** Antes de crear el proyecto de destino: registra su ruta para poder retirarlo (con su marca) tras una interrupción. */
        public synchronized void projectAboutToBeCreated(String projectDirectory) {
            record(journal.withEffect(ExternalImportJournal.Effect.intended(ExternalImportJournal.PROJECT, projectDirectory,
                    ExternalImportJournal.CREATED, false, null, null)));
        }

        /** Tras crear el proyecto: confirma la ruta real. */
        public synchronized void projectCreated(String projectDirectory) {
            ExternalImportJournal.Effect effect = journal.effects().stream().filter(value -> ExternalImportJournal.PROJECT.equals(value.kind())).findFirst().orElse(null);
            if(effect == null) return;
            record(journal.withoutEffect(effect.id()).withEffect(effect.withResource(projectDirectory).withStatus(ExternalImportJournal.APPLIED)));
        }

        /** Contenido de la marca de titularidad que se escribe dentro del proyecto creado. */
        /** Descripción: Ejecuta la operación «markerContent» correspondiente a la responsabilidad de CompensationReport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] markerContent() {
            return ("{\"operationId\":\"" + journal.operationId() + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        /** Cierra con éxito: persiste COMMITTED y borra el diario. Si el borrado falla, la recuperación lo borrará (ya está COMMITTED). */
        public synchronized void commit() {
            record(journal.withState(ExternalImportJournal.COMMITTED));
            try { store.delete(journal.operationId()); }
            catch(RuntimeException exception) { LOGGER.warn("Import {} is committed; its journal will be deleted on the next recovery", journal.operationId(), exception); }
        }

        /** Titularidad registrada (creado, reutilizado, sustituido) para el informe de la importación. */
        public synchronized java.util.Map<String, Object> ownership() {
            java.util.Map<String, List<String>> buckets = new java.util.LinkedHashMap<>();
            for(ExternalImportJournal.Effect effect : journal.effects()) {
                buckets.computeIfAbsent(effect.kind() + "." + effect.ownership(), key -> new ArrayList<>()).add(effect.resourceId());
            }
            java.util.Map<String, Object> ownership = new java.util.LinkedHashMap<>();
            ownership.put("operationId", journal.operationId());
            ownership.put("inputFingerprint", journal.inputFingerprint());
            ownership.putAll(buckets);
            return ownership;
        }

        /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void close() { hold.close(); }
    }
}

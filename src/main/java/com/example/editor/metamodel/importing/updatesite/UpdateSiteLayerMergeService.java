package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;

import com.example.editor.io.AtomicFiles;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionLoadResult;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionSaveResponse;
import com.example.editor.definition.EditorRuntimePolicy;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.PaletteGroup;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.ToolPaletteDefinition;

import tools.jackson.databind.ObjectMapper;

/** Fusiona capas recuperadas con un editor existente mediante decisiones y ETags explícitos. */
@Service
public class UpdateSiteLayerMergeService {
    private final UpdateSiteImportApplicationService importService;
    private final UpdateSiteLayerMaterializationService materializationService;
    private final EditorDefinitionApplicationService editorService;
    private final UpdateSiteImportProperties properties;
    private final ObjectMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteLayerMergeService con sus dependencias y datos de entrada. Entradas: importService: servicio colaborador utilizado por la operación; materializationService: servicio colaborador utilizado por la operación; editorService: servicio colaborador utilizado por la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteLayerMergeService(UpdateSiteImportApplicationService importService,
            UpdateSiteLayerMaterializationService materializationService,
            EditorDefinitionApplicationService editorService, UpdateSiteImportProperties properties,
            ObjectMapper mapper) {
        this.importService = importService;
        this.materializationService = materializationService;
        this.editorService = editorService;
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteLayerMergeService mediante la operación «merge». Entradas: files: archivo o contenido que debe procesarse; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMergeResult merge(List<UpdateSiteInputFile> files,
            UpdateSiteLayerMergeRequest request) {
        requireRequest(request);
        EditorDefinitionLoadResult current = editorService.find(request.targetMetamodelId(), request.targetEditorId());
        if(request.persist() && request.expectedEtag().isBlank()) {
            throw new IllegalArgumentException("expectedEtag is required when a layer merge is persisted");
        }
        if(!request.expectedEtag().isBlank() && !request.expectedEtag().equals(current.etag())) {
            throw new IllegalArgumentException("The target editor changed after it was loaded; analyze the merge again");
        }

        UpdateSiteImportOptions options = new UpdateSiteImportOptions(request.targetMetamodelId(), false,
                request.strict(), request.candidateId(), true, true, true, true);
        UpdateSiteImportAnalysis analysis = importService.analyze(files, options);
        UpdateSiteLayerMaterializationRequest materializationRequest = new UpdateSiteLayerMaterializationRequest(
                request.targetMetamodelId(), request.candidateId(), request.targetEditorId(),
                current.editorDefinition().name(), request.strict(), false, request.selection());
        UpdateSiteLayerMaterializationPreview recoveredPreview = materializationService.preview(analysis, materializationRequest);
        MergeComputation computation = compute(current.editorDefinition(), recoveredPreview.editorDefinition(), request);
        requireKnownDecisionKeys(request, computation.items());
        List<String> unresolved = unresolvedConflicts(request, computation.items());
        EditorDefinitionCheckReport report = editorService.checkDefinition(request.targetMetamodelId(), computation.editor());
        List<String> diagnostics = new ArrayList<>(recoveredPreview.diagnostics());
        diagnostics.addAll(report.diagnostics().stream().map(item -> item.code() + ": " + item.message()).toList());
        if(!unresolved.isEmpty()) diagnostics.add(unresolved.size() + " collision(s) need an explicit decision before the merge can be applied: "
                + String.join(", ", unresolved));
        String transactionId = "merge-" + UUID.randomUUID();
        UpdateSiteLayerMergePreview preview = new UpdateSiteLayerMergePreview(transactionId,
                request.targetMetamodelId(), request.targetEditorId(), current.etag(), computation.editor(), report,
                computation.items(), computation.kept(), computation.recovered(), computation.skipped(),
                recoveredPreview.applicable() && report.valid() && unresolved.isEmpty(), diagnostics, unresolved);
        if(!request.persist()) return new UpdateSiteLayerMergeResult(preview, false, "", false, diagnostics);
        if(!unresolved.isEmpty()) throw new IllegalArgumentException(unresolved.size()
                + " collision(s) need an explicit decision before the merge can be applied: " + String.join(", ", unresolved));
        if(!preview.applicable()) throw new IllegalArgumentException("The selected merge contains blocking editor references");

        EditorDefinitionSaveResponse saved = editorService.replace(request.targetMetamodelId(),
                request.targetEditorId(), computation.editor(), request.expectedEtag());
        UpdateSiteLayerMergeJournal journal = new UpdateSiteLayerMergeJournal(transactionId, Instant.now(),
                request.targetMetamodelId(), request.targetEditorId(), current.etag(), saved.etag(),
                current.editorDefinition(), appliedDecisions(request, computation.items()));
        writeJournal(journal);
        return new UpdateSiteLayerMergeResult(preview, true, saved.etag(), true, diagnostics);
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de UpdateSiteLayerMergeService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMergeRollbackResult rollback(UpdateSiteLayerMergeRollbackRequest request) {
        if(request == null || request.transactionId().isBlank()) throw new IllegalArgumentException("transactionId is required");
        UpdateSiteLayerMergeJournal journal = readJournal(request.transactionId());
        String expected = request.expectedCurrentEtag().isBlank() ? journal.appliedEtag() : request.expectedCurrentEtag();
        if(!expected.equals(journal.appliedEtag())) {
            return new UpdateSiteLayerMergeRollbackResult(false, "",
                    List.of("Rollback was rejected because the supplied ETag is not the ETag produced by the merge."));
        }
        EditorDefinitionLoadResult current = editorService.find(journal.targetMetamodelId(), journal.targetEditorId());
        if(!current.etag().equals(expected)) {
            return new UpdateSiteLayerMergeRollbackResult(false, current.etag(),
                    List.of("Rollback was rejected because the editor has changed since the merge."));
        }
        EditorDefinitionSaveResponse restored = editorService.replace(journal.targetMetamodelId(),
                journal.targetEditorId(), journal.originalEditor(), expected);
        deleteJournal(journal.transactionId());
        return new UpdateSiteLayerMergeRollbackResult(true, restored.etag(), List.of(), journal.decisions());
    }

    /** Descripción: Ejecuta la operación «compute» correspondiente a la responsabilidad de UpdateSiteLayerMergeService. Entradas: existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MergeComputation compute(EditorDefinitionModel existing, EditorDefinitionModel recovered,
            UpdateSiteLayerMergeRequest request) {
        List<UpdateSiteLayerMergeItem> items = new ArrayList<>();
        List<RepresentationDefinition> representations = mergeRepresentations(existing.representations(),
                recovered.representations(), request, items);
        List<EditorTool> tools = mergeList("tool", existing.palette().tools(), recovered.palette().tools(),
                EditorTool::id, EditorTool::label, request, items);
        Set<String> toolIds = tools.stream().map(EditorTool::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<PaletteGroup> groups = mergeList("palette-group", existing.palette().groups(), recovered.palette().groups(),
                PaletteGroup::id, PaletteGroup::name, request, items).stream()
                .map(group -> new PaletteGroup(group.id(), group.name(), group.order(),
                        group.toolIds().stream().filter(toolIds::contains).toList(), group.initiallyCollapsed()))
                .filter(group -> !group.toolIds().isEmpty()).toList();
        List<RepresentationDefinition> safeRepresentations = representations.stream()
                .map(value -> new RepresentationDefinition(value.id(), value.name(), value.kind(), value.rootClassIds(),
                        value.nodeMappings(), value.edgeMappings(), value.toolIds().stream().filter(toolIds::contains).toList(),
                        value.layers(), value.filters(), value.editable(), value.annotations())).toList();
        List<FormDefinition> forms = mergeList("form", existing.forms(), recovered.forms(), FormDefinition::id,
                FormDefinition::name, request, items);

        Set<EditorCapability> capabilities = new LinkedHashSet<>(existing.capabilityManifest().capabilities());
        capabilities.addAll(recovered.capabilityManifest().capabilities());
        Map<String, String> annotations = new LinkedHashMap<>(existing.annotations());
        recovered.annotations().forEach((key, value) -> annotations.putIfAbsent(key, value));
        annotations.put("hydra.updateSite.merge", "selective");
        EditorRuntimePolicy runtimePolicy = existing.runtimePolicy();
        EditorDefinitionModel merged = new EditorDefinitionModel(existing.schemaVersion(), existing.id(),
                existing.metamodelId(), existing.name(), safeRepresentations,
                new ToolPaletteDefinition(groups, tools), forms,
                new EditorCapabilityManifest(capabilities), runtimePolicy, annotations);
        int kept = (int) items.stream().filter(item -> item.selectedDecision() == UpdateSiteMergeDecision.KEEP_EXISTING).count();
        int recoveredCount = (int) items.stream().filter(item -> item.selectedDecision() == UpdateSiteMergeDecision.USE_RECOVERED).count();
        int skipped = (int) items.stream().filter(item -> item.selectedDecision() == UpdateSiteMergeDecision.SKIP).count();
        return new MergeComputation(merged, List.copyOf(items), kept, recoveredCount, skipped);
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteLayerMergeService mediante la operación «mergeRepresentations». Entradas: existingValues: dato de entrada necesario para la operación; recoveredValues: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; items: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<RepresentationDefinition> mergeRepresentations(List<RepresentationDefinition> existingValues,
            List<RepresentationDefinition> recoveredValues, UpdateSiteLayerMergeRequest request,
            List<UpdateSiteLayerMergeItem> items) {
        List<RepresentationDefinition> selected = mergeList("representation", existingValues, recoveredValues,
                RepresentationDefinition::id, RepresentationDefinition::name, request, items);
        Map<String, RepresentationDefinition> existing = index(existingValues, RepresentationDefinition::id);
        Map<String, RepresentationDefinition> recovered = index(recoveredValues, RepresentationDefinition::id);
        List<RepresentationDefinition> result = new ArrayList<>();
        for(RepresentationDefinition chosen : selected) {
            RepresentationDefinition oldValue = existing.get(chosen.id());
            RepresentationDefinition newValue = recovered.get(chosen.id());
            List<NodeMapping> nodes = mergeList("node-mapping:" + chosen.id(), "node-mapping",
                    oldValue == null ? List.of() : oldValue.nodeMappings(),
                    newValue == null ? List.of() : newValue.nodeMappings(), NodeMapping::id,
                    NodeMapping::representationName, request, items);
            List<EdgeMapping> edges = mergeList("edge-mapping:" + chosen.id(), "edge-mapping",
                    oldValue == null ? List.of() : oldValue.edgeMappings(),
                    newValue == null ? List.of() : newValue.edgeMappings(), EdgeMapping::id,
                    EdgeMapping::relationId, request, items);
            result.add(new RepresentationDefinition(chosen.id(), chosen.name(), chosen.kind(),
                    mergeIds(oldValue == null ? List.of() : oldValue.rootClassIds(),
                            newValue == null ? List.of() : newValue.rootClassIds()),
                    nodes, edges,
                    mergeIds(oldValue == null ? List.of() : oldValue.toolIds(),
                            newValue == null ? List.of() : newValue.toolIds()),
                    chosen.layers(), chosen.filters(), chosen.editable(), chosen.annotations()));
        }
        return List.copyOf(result);
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteLayerMergeService mediante la operación «mergeList». Entradas: kind: dato de entrada necesario para la operación; existingValues: dato de entrada necesario para la operación; recoveredValues: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; items: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private <T> List<T> mergeList(String kind, List<T> existingValues, List<T> recoveredValues,
            Function<T, String> id, Function<T, String> label, UpdateSiteLayerMergeRequest request,
            List<UpdateSiteLayerMergeItem> items) {
        return mergeList(kind, kind, existingValues, recoveredValues, id, label, request, items);
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteLayerMergeService mediante la operación «mergeList». Entradas: keyPrefix: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; existingValues: dato de entrada necesario para la operación; recoveredValues: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; items: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ <T> List<T> mergeList(String keyPrefix, String kind, List<T> existingValues,
            List<T> recoveredValues, Function<T, String> id, Function<T, String> label,
            UpdateSiteLayerMergeRequest request, List<UpdateSiteLayerMergeItem> items) {
        Map<String, T> existing = index(existingValues, id);
        Map<String, T> recovered = index(recoveredValues, id);
        Set<String> keys = new LinkedHashSet<>(); keys.addAll(existing.keySet()); keys.addAll(recovered.keySet());
        List<T> result = new ArrayList<>();
        for(String elementId : keys.stream().sorted().toList()) {
            T oldValue = existing.get(elementId); T newValue = recovered.get(elementId);
            boolean conflict = oldValue != null && newValue != null && !oldValue.equals(newValue);
            UpdateSiteMergeDecision recommended = oldValue == null ? UpdateSiteMergeDecision.USE_RECOVERED
                    : newValue == null ? UpdateSiteMergeDecision.KEEP_EXISTING
                    : conflict ? UpdateSiteMergeDecision.KEEP_EXISTING : UpdateSiteMergeDecision.KEEP_EXISTING;
            String key = keyPrefix + ":" + elementId;
            UpdateSiteMergeDecision explicit = request.decisions().get(key);
            UpdateSiteMergeDecision selected;
            if(explicit != null) {
                // Una decisión explícita nunca se reinterpreta en silencio: si no tiene sentido para el elemento, se rechaza.
                if(newValue == null && explicit == UpdateSiteMergeDecision.USE_RECOVERED) throw new IllegalArgumentException(
                        "USE_RECOVERED is not valid for '" + key + "': there is no recovered element");
                if(oldValue == null && explicit == UpdateSiteMergeDecision.KEEP_EXISTING) throw new IllegalArgumentException(
                        "KEEP_EXISTING is not valid for '" + key + "': there is no existing element");
                selected = explicit;
            } else if(conflict) {
                // Sin decisión explícita una colisión no se resuelve: se previsualiza conservando lo existente y se marca como pendiente.
                selected = UpdateSiteMergeDecision.KEEP_EXISTING;
            } else {
                selected = oldValue == null ? UpdateSiteMergeDecision.USE_RECOVERED : request.defaultDecision();
                if(newValue == null && selected == UpdateSiteMergeDecision.USE_RECOVERED) selected = UpdateSiteMergeDecision.KEEP_EXISTING;
                if(oldValue == null && selected == UpdateSiteMergeDecision.KEEP_EXISTING) selected = UpdateSiteMergeDecision.SKIP;
            }
            T chosen = switch(selected) {
                case KEEP_EXISTING -> oldValue;
                case USE_RECOVERED -> newValue;
                case SKIP -> null;
            };
            if(chosen != null) result.add(chosen);
            String displayName = label.apply(newValue != null ? newValue : oldValue);
            String explanation = conflict ? "An existing and a recovered definition share the same stable ID."
                    : oldValue == null ? "The recovered element is new in the target editor."
                    : "The element already exists and has no selected replacement.";
            items.add(new UpdateSiteLayerMergeItem(key, kind, displayName, oldValue != null, newValue != null,
                    conflict, recommended, selected, explanation));
        }
        return List.copyOf(result);
    }

    /** Colisiones (mismo ID, contenido distinto) sin decisión explícita en la solicitud. */
    /** Descripción: Ejecuta la operación «unresolvedConflicts» correspondiente a la responsabilidad de UpdateSiteLayerMergeService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; items: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ List<String> unresolvedConflicts(UpdateSiteLayerMergeRequest request, List<UpdateSiteLayerMergeItem> items) {
        return items.stream().filter(UpdateSiteLayerMergeItem::conflict).map(UpdateSiteLayerMergeItem::key)
                .filter(key -> !request.decisions().containsKey(key)).toList();
    }

    /** Rechaza decisiones que no corresponden a ningún elemento de esta fusión (obsoletas o inventadas). */
    /** Descripción: Valida las condiciones necesarias para «requireKnownDecisionKeys» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; items: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void requireKnownDecisionKeys(UpdateSiteLayerMergeRequest request, List<UpdateSiteLayerMergeItem> items) {
        Set<String> known = items.stream().map(UpdateSiteLayerMergeItem::key).collect(java.util.stream.Collectors.toSet());
        List<String> unknown = request.decisions().keySet().stream().filter(key -> !known.contains(key)).sorted().toList();
        if(!unknown.isEmpty()) throw new IllegalArgumentException("Merge decision(s) do not match any element of this merge: " + String.join(", ", unknown));
    }

    /** Decisiones que gobiernan elementos reales de la fusión (explícitas), para el diario. */
    /** Descripción: Ejecuta la operación «appliedDecisions» correspondiente a la responsabilidad de UpdateSiteLayerMergeService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; items: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, String> appliedDecisions(UpdateSiteLayerMergeRequest request, List<UpdateSiteLayerMergeItem> items) {
        Map<String, String> applied = new java.util.TreeMap<>();
        for(UpdateSiteLayerMergeItem item : items) {
            UpdateSiteMergeDecision explicit = request.decisions().get(item.key());
            if(explicit != null) applied.put(item.key(), explicit.name());
        }
        return applied;
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteLayerMergeService mediante la operación «mergeIds». Entradas: existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> mergeIds(List<String> existing, List<String> recovered) {
        Set<String> result = new LinkedHashSet<>();
        if(existing != null) result.addAll(existing);
        if(recovered != null) result.addAll(recovered);
        return result.stream().sorted().toList();
    }

    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de UpdateSiteLayerMergeService. Entradas: values: colección de elementos que debe procesarse; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private <T> Map<String, T> index(List<T> values, Function<T, String> id) {
        Map<String, T> result = new LinkedHashMap<>();
        if(values != null) values.stream().sorted(Comparator.comparing(id)).forEach(value -> result.put(id.apply(value), value));
        return result;
    }

    /** Descripción: Valida las condiciones necesarias para «requireRequest» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireRequest(UpdateSiteLayerMergeRequest request) {
        if(request == null) throw new IllegalArgumentException("Layer merge request is required");
        if(request.targetMetamodelId().isBlank()) throw new IllegalArgumentException("targetMetamodelId is required");
        if(request.targetEditorId().isBlank()) throw new IllegalArgumentException("targetEditorId is required");
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteLayerMergeService mediante la operación «writeJournal». Entradas: journal: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeJournal(UpdateSiteLayerMergeJournal journal) {
        try {
            Path target = journalPath(journal.transactionId()); AtomicFiles.writeReplacing(target,mapper.writeValueAsBytes(journal));
        } catch(JacksonException | IOException exception) {
            throw new IllegalStateException("Could not persist the layer merge journal", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readJournal» dentro de UpdateSiteLayerMergeService. Entradas: transactionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteLayerMergeJournal readJournal(String transactionId) {
        try { return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(journalPath(transactionId)), UpdateSiteLayerMergeJournal.class); }
        catch(JacksonException | IOException exception) { throw new IllegalArgumentException("Layer merge transaction was not found", exception); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteJournal» de forma controlada. Entradas: transactionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteJournal(String transactionId) {
        try { Files.deleteIfExists(journalPath(transactionId)); }
        catch(IOException exception) { throw new IllegalStateException("The editor was restored but the merge journal could not be removed", exception); }
    }

    /** Descripción: Ejecuta la operación «journalPath» correspondiente a la responsabilidad de UpdateSiteLayerMergeService. Entradas: transactionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path journalPath(String transactionId) {
        String safe = transactionId == null ? "" : transactionId.replaceAll("[^A-Za-z0-9._-]", "-");
        if(safe.isBlank()) throw new IllegalArgumentException("Unsafe transactionId");
        Path root = properties.storageRootPath().resolve("merge-journal").normalize();
        Path path = root.resolve(safe + ".json").normalize();
        if(!path.startsWith(root)) throw new IllegalArgumentException("Unsafe transactionId");
        return path;
    }

    private record MergeComputation(EditorDefinitionModel editor, List<UpdateSiteLayerMergeItem> items,
            int kept, int recovered, int skipped) { }
}

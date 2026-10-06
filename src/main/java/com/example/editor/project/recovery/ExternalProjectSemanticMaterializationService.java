package com.example.editor.project.recovery;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.definition.EditorDefinitionApplicationException;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorRuntimePolicy;
import com.example.editor.definition.EditorValidationTrigger;
import com.example.editor.definition.importing.VisualDefinitionImportResult;
import com.example.editor.definition.importing.VisualDefinitionImportService;
import com.example.editor.definition.importing.VisualImportCommitRequest;
import com.example.editor.definition.importing.VisualImportCommitResult;
import com.example.editor.definition.importing.VisualValidationAssociationService;
import com.example.editor.definition.assets.EditorVisualAssetService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportPreview;
import com.example.editor.metamodel.importing.MetamodelImportSaveResult;
import com.example.editor.metamodel.importing.MetamodelImportService;

/**
 * Materializador genérico de proyectos externos. Descubre primero el metamodelo canónico y después aplica
 * adaptadores independientes para representación, validación y quick fixes. Nunca ejecuta código del proyecto.
 */
@Service
public class ExternalProjectSemanticMaterializationService {
    private final MetamodelImportService metamodelImports;
    private final CanonicalMetamodelApplicationService metamodels;
    private final VisualDefinitionImportService visualImports;
    private final VisualValidationAssociationService visualValidations;
    private final EditorDefinitionApplicationService editors;
    private final ExternalValidationRecoveryService validationRecovery;
    private final ExternalCodeVisualRecoveryService codeVisualRecovery;
    private final EditorVisualAssetService visualAssets;

    /** Descripción: Inicializa una instancia de ExternalProjectSemanticMaterializationService con sus dependencias y datos de entrada. Entradas: metamodelImports: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; visualImports: dato de entrada necesario para la operación; visualValidations: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; validationRecovery: dato de entrada necesario para la operación; codeVisualRecovery: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalProjectSemanticMaterializationService(MetamodelImportService metamodelImports,
            CanonicalMetamodelApplicationService metamodels, VisualDefinitionImportService visualImports,
            VisualValidationAssociationService visualValidations, EditorDefinitionApplicationService editors,
            ExternalValidationRecoveryService validationRecovery, ExternalCodeVisualRecoveryService codeVisualRecovery,
            EditorVisualAssetService visualAssets) {
        this.metamodelImports = metamodelImports;
        this.metamodels = metamodels;
        this.visualImports = visualImports;
        this.visualValidations = visualValidations;
        this.editors = editors;
        this.validationRecovery = validationRecovery;
        this.codeVisualRecovery = codeVisualRecovery;
        this.visualAssets = visualAssets;
    }

    /** Descripción: Ejecuta la operación «materialize» correspondiente a la responsabilidad de VisualOutcome. Entradas: entries: colección de elementos que debe procesarse; report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ExternalProjectMaterializationResult materialize(List<ExternalProjectArchiveEntry> entries,
            ExternalProjectRecoveryReport report) {
        List<String> diagnostics = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<String> editorIds = new ArrayList<>();
        int nativeVisualValidations = 0;
        MetamodelCandidate candidate = selectMetamodel(entries, diagnostics);
        if(candidate == null) {
            diagnostics.add("No unique semantic metamodel could be selected automatically. The complete project remains preserved and no visual/validation layer was attached to an arbitrary metamodel.");
            return ExternalProjectMaterializationResult.empty(diagnostics);
        }

        MetamodelImportPreview preview;
        try {
            preview = metamodelImports.preview(leaf(candidate.entry().path()), mediaType(candidate.entry().path()), candidate.entry().bytes(),
                    new MetamodelImportOptions("auto", "", false, true, true, false));
        } catch(RuntimeException exception) {
            diagnostics.add("Metamodel adapter could not analyse " + candidate.entry().path() + ": " + safeMessage(exception));
            return ExternalProjectMaterializationResult.empty(diagnostics);
        }
        MetaModel metamodel = preview.metamodel();
        boolean created = false;
        try {
            MetaModel existing = metamodels.findById(metamodel.id()).metamodel();
            if(!existing.equals(metamodel)) {
                diagnostics.add("A different metamodel with id '" + metamodel.id() + "' already exists. HYDRA did not overwrite it or attach recovered editors to an incompatible structure.");
                return ExternalProjectMaterializationResult.empty(diagnostics);
            }
            metamodel = existing;
            diagnostics.add("Reused the existing canonical metamodel '" + metamodel.id() + "' because it is structurally identical to the imported source.");
        } catch(MetamodelApplicationException exception) {
            if(exception.getReason() != MetamodelApplicationException.Reason.NOT_FOUND) throw exception;
            MetamodelImportSaveResult saved = metamodelImports.importPrepared(preview, false);
            metamodel = saved.preview().metamodel();
            created = true;
            sources.add(candidate.entry().path());
        }

        VisualOutcome visual = materializeVisuals(metamodel, entries, diagnostics, null);
        editorIds.addAll(visual.editorIds());
        nativeVisualValidations += visual.validationsImported();
        sources.addAll(visual.sources());

        ExternalValidationRecoveryResult validation = validationRecovery.recover(metamodel.id(), metamodel, entries);
        diagnostics.addAll(validation.diagnostics());
        sources.addAll(validation.sources());
        if(validation.rulesImported() > 0 || nativeVisualValidations > 0) {
            enableRuntimeValidation(metamodel.id(), editorIds, validation.quickFixesImported() > 0, diagnostics);
        }

        Set<String> distinctSources = new LinkedHashSet<>(sources);
        if(editorIds.isEmpty() && hasSpecializedVisualEvidence(entries)) {
            diagnostics.add("Visual artifacts were detected but none could be materialized safely. They remain under external-source/ with their diagnostics instead of being flattened into a generic editor.");
        }
        if(validation.rulesImported() == 0 && hasValidationEvidence(entries)) {
            diagnostics.add("Validation sources were detected but no rule body matched a supported safe recovery pattern. The sources remain preserved for manual or future adapter recovery.");
        }
        return new ExternalProjectMaterializationResult(created, metamodel.id(), editorIds, nativeVisualValidations,
                validation.rulesImported(), validation.quickFixesImported(), validation.reviewOnlyQuickFixes(),
                List.copyOf(distinctSources), diagnostics);
    }

    /**
     * Materializa representación, validación y quick fixes sobre un metamodelo canónico ya persistido.
     * Permite reutilizar el mismo pipeline profundo desde Update Site sin ejecutar código externo.
     *
     * @param metamodel metamodelo canónico persistido.
     * @param resources recursos del proyecto indexados por ruta portable.
     * @param selectedVisualSources {@code null} para todas las fuentes visuales; un conjunto no nulo
     *        limita únicamente las representaciones a las fuentes seleccionadas. Las validaciones y fixes
     *        se recuperan del proyecto completo para que no queden documentos canónicos incompletos.
     * @return resumen de capas materializadas.
     */
    /** Descripción: Ejecuta la operación «materializeExisting» correspondiente a la responsabilidad de ExternalProjectSemanticMaterializationService. Entradas: metamodel: dato de entrada necesario para la operación; resources: dato de entrada necesario para la operación; selectedVisualSources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExternalProjectMaterializationResult materializeExisting(MetaModel metamodel, Map<String, byte[]> resources,
            Set<String> selectedVisualSources) {
        if(metamodel == null) throw new IllegalArgumentException("A persisted metamodel is required before materializing external layers");
        Map<String, byte[]> safeResources = resources == null ? Map.of() : resources;
        List<ExternalProjectArchiveEntry> entries = safeResources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    byte[] content = entry.getValue() == null ? new byte[0] : entry.getValue();
                    return new ExternalProjectArchiveEntry(entry.getKey(), content, content.length, sha256Bytes(content));
                }).toList();
        Set<String> normalizedSelection = selectedVisualSources == null ? null : selectedVisualSources.stream()
                .filter(java.util.Objects::nonNull).map(value -> value.replace('\\','/'))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> diagnostics = new ArrayList<>();
        VisualOutcome visual = materializeVisuals(metamodel, entries, diagnostics, normalizedSelection);
        ExternalValidationRecoveryResult validation = validationRecovery.recover(metamodel.id(), metamodel, entries);
        diagnostics.addAll(validation.diagnostics());
        if(validation.rulesImported() > 0 || visual.validationsImported() > 0)
            enableRuntimeValidation(metamodel.id(), visual.editorIds(), validation.quickFixesImported() > 0, diagnostics);
        List<String> sources = new ArrayList<>(visual.sources());
        sources.addAll(validation.sources());
        return new ExternalProjectMaterializationResult(false, metamodel.id(), visual.editorIds(), visual.validationsImported(),
                validation.rulesImported(), validation.quickFixesImported(), validation.reviewOnlyQuickFixes(),
                sources.stream().distinct().toList(), diagnostics);
    }

    /** Activa el runtime de validación en todos los editores recuperados una vez materializadas las reglas externas seguras. */
    /** Descripción: Ejecuta la operación «enableRuntimeValidation» correspondiente a la responsabilidad de MetamodelCandidate. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorIds: dato de entrada necesario para la operación; quickFixesAvailable: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enableRuntimeValidation(String metamodelId, List<String> editorIds, boolean quickFixesAvailable, List<String> diagnostics) {
        for(String editorId : editorIds.stream().distinct().toList()) {
            try {
                var loaded = editors.find(metamodelId, editorId);
                EditorDefinitionModel current = loaded.editorDefinition();
                Set<EditorCapability> capabilities = new LinkedHashSet<>(current.capabilityManifest().capabilities());
                capabilities.add(EditorCapability.VALIDATION_RUNTIME);
                if(quickFixesAvailable) capabilities.add(EditorCapability.QUICK_FIX_RUNTIME);
                EditorRuntimePolicy previous = current.runtimePolicy();
                Set<EditorValidationTrigger> triggers = new LinkedHashSet<>(previous.validationTriggers());
                triggers.add(EditorValidationTrigger.MANUAL);
                triggers.add(EditorValidationTrigger.ON_OPEN);
                triggers.add(EditorValidationTrigger.ON_CHANGE);
                triggers.add(EditorValidationTrigger.ON_REPRESENTATION_CHANGE);
                EditorRuntimePolicy runtime = new EditorRuntimePolicy(triggers, previous.autosave(), previous.recoverLatestSession(),
                        previous.publishEvents(), previous.eventFlowsEnabled(), previous.gamificationEnabled(),
                        previous.maximumOpenRepresentations(), previous.operationTimeoutMillis());
                EditorDefinitionModel updated = new EditorDefinitionModel(current.schemaVersion(), current.id(), current.metamodelId(), current.name(),
                        current.representations(), current.palette(), current.forms(), new EditorCapabilityManifest(capabilities), runtime,
                        current.codeGeneration(), current.annotations());
                if(!updated.equals(current)) editors.replace(metamodelId, editorId, updated, loaded.etag());
            } catch(RuntimeException exception) {
                diagnostics.add("Could not activate validation runtime on recovered editor '" + editorId + "': " + safeMessage(exception));
            }
        }
    }

    /** Descripción: Ejecuta la operación «materializeVisuals» correspondiente a la responsabilidad de VisualOutcome. Entradas: metamodel: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private VisualOutcome materializeVisuals(MetaModel metamodel, List<ExternalProjectArchiveEntry> entries,
            List<String> diagnostics, Set<String> selectedVisualSources) {
        List<String> editorIds = new ArrayList<>(); List<String> sources = new ArrayList<>(); int validations = 0;
        List<MultipartFile> javaSources = entries.stream().filter(value -> lower(value.path()).endsWith(".java"))
                .filter(value -> StaticSourceInspectionPolicy.inspectText(value).isPresent())
                .map(value -> multipart("javaSources", value)).toList();

        for(ExternalProjectArchiveEntry entry : entries.stream().filter(value -> lower(value.path()).endsWith(".odesign"))
                .filter(value -> visualSourceSelected(value.path(), selectedVisualSources))
                .sorted(Comparator.comparing(ExternalProjectArchiveEntry::path)).toList()) {
            try {
                VisualDefinitionImportResult result = visualImports.importSirius(metamodel.id(), multipart("odesign", entry), javaSources, "EXISTING", List.of());
                PersistedVisual persisted = persistVisual(result, metamodel, entry.path(), entries, diagnostics);
                if(persisted.persisted()) { editorIds.add(persisted.editorId()); sources.add(entry.path()); validations += persisted.validations(); }
            } catch(RuntimeException exception) { diagnostics.add("Sirius adapter failed for " + entry.path() + ": " + safeMessage(exception)); }
        }

        List<ExternalProjectArchiveEntry> gmf = entries.stream().filter(value -> lower(value.path()).matches(".*\\.gmf(?:graph|tool|map|gen)$"))
                .filter(value -> visualSourceSelected(value.path(), selectedVisualSources))
                .sorted(Comparator.comparing(ExternalProjectArchiveEntry::path)).toList();
        if(!gmf.isEmpty()) {
            try {
                List<MultipartFile> resources = new ArrayList<>(); gmf.forEach(value -> resources.add(multipart("resources", value)));
                entries.stream().filter(value -> lower(value.path()).endsWith(".java") || lower(value.path()).endsWith(".ocl"))
                        .filter(value -> StaticSourceInspectionPolicy.inspectText(value).isPresent())
                        .forEach(value -> resources.add(multipart("resources", value)));
                VisualDefinitionImportResult result = visualImports.importGmf(metamodel.id(), resources, "EXISTING", List.of());
                PersistedVisual persisted = persistVisual(result, metamodel, gmf.get(0).path(), entries, diagnostics);
                if(persisted.persisted()) { editorIds.add(persisted.editorId()); gmf.forEach(value -> sources.add(value.path())); validations += persisted.validations(); }
            } catch(RuntimeException exception) { diagnostics.add("GMF adapter failed: " + safeMessage(exception)); }
        }

        List<ExternalProjectArchiveEntry> drawio = entries.stream().filter(value -> {
            String path=lower(value.path()); return path.endsWith(".drawio") || path.endsWith(".drawio.xml") || path.endsWith(".mxlibrary");
        }).filter(value -> visualSourceSelected(value.path(), selectedVisualSources)).toList();
        if(!drawio.isEmpty()) {
            try {
                VisualDefinitionImportResult result = visualImports.importDrawio(metamodel.id(), drawio.stream().map(value -> multipart("resources",value)).toList(), "EXISTING", List.of());
                PersistedVisual persisted = persistVisual(result, metamodel, drawio.get(0).path(), entries, diagnostics);
                if(persisted.persisted()) { editorIds.add(persisted.editorId()); drawio.forEach(value -> sources.add(value.path())); validations += persisted.validations(); }
            } catch(RuntimeException exception) { diagnostics.add("draw.io adapter failed: " + safeMessage(exception)); }
        }

        List<ExternalProjectArchiveEntry> siriusWeb = entries.stream().filter(this::looksLikeSiriusWeb)
                .filter(value -> visualSourceSelected(value.path(), selectedVisualSources)).toList();
        if(!siriusWeb.isEmpty()) {
            try {
                List<MultipartFile> resources = new ArrayList<>(siriusWeb.stream().map(value -> multipart("resources",value)).toList());
                resources.addAll(javaSources);
                VisualDefinitionImportResult result = visualImports.importSiriusWeb(metamodel.id(), resources, "EXISTING", List.of());
                PersistedVisual persisted = persistVisual(result, metamodel, siriusWeb.get(0).path(), entries, diagnostics);
                if(persisted.persisted()) { editorIds.add(persisted.editorId()); siriusWeb.forEach(value -> sources.add(value.path())); validations += persisted.validations(); }
            } catch(RuntimeException exception) { diagnostics.add("Sirius Web adapter failed: " + safeMessage(exception)); }
        }

        for(ExternalCodeVisualRecoveryService.RecoveredCodeEditor recovered : selectedVisualSources == null
                ? codeVisualRecovery.recover(metamodel, entries) : List.<ExternalCodeVisualRecoveryService.RecoveredCodeEditor>of()) {
            diagnostics.addAll(recovered.diagnostics());
            PersistedVisual persisted = persistRecoveredEditor(recovered.editor(), metamodel, recovered.technology(), entries, diagnostics);
            if(persisted.persisted()) { editorIds.add(persisted.editorId()); sources.addAll(recovered.sources()); }
        }
        return new VisualOutcome(editorIds.stream().distinct().toList(), validations, sources.stream().distinct().toList());
    }

    /** Descripción: Ejecuta la operación «persistVisual» correspondiente a la responsabilidad de VisualOutcome. Entradas: result: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PersistedVisual persistVisual(VisualDefinitionImportResult result, MetaModel metamodel, String source,
            List<ExternalProjectArchiveEntry> entries, List<String> diagnostics) {
        if(!result.analysis().importReady()) diagnostics.add("Visual adapter analysed " + source + " with unresolved external semantics that require review. HYDRA will still materialize the structurally safe subset instead of discarding the whole visual layer. " + summary(result));
        EditorDefinitionModel analysedEditor = rebaseProjectAssets(result.editorDefinition(), entries, diagnostics);
        var preflight = editors.checkDefinition(metamodel.id(), analysedEditor);
        if(!preflight.valid()) {
            diagnostics.add("Visual adapter preserved " + source + " but the recovered canonical editor still contains blocking structural errors: " + preflight.diagnostics().stream().map(value -> value.code()+":"+value.message()).limit(12).toList());
            return new PersistedVisual(false,"",0);
        }
        VisualImportCommitResult committed = visualValidations.commit(new VisualImportCommitRequest(analysedEditor, metamodel,
                result.metamodelAssociation(), result.validationDetails()));
        EditorDefinitionModel editor = committed.editorDefinition();
        try {
            var saved = editors.create(metamodel.id(), editor);
            diagnostics.add("Materialized " + result.technology() + " editor '" + saved.editorDefinition().name() + "' with "
                    + saved.editorDefinition().representations().size() + " representation(s) and " + saved.editorDefinition().palette().tools().size() + " palette tool(s).");
            return new PersistedVisual(true, saved.editorDefinition().id(), committed.validationsImported());
        } catch(EditorDefinitionApplicationException exception) {
            if(exception.getReason() == EditorDefinitionApplicationException.Reason.CONFLICT) {
                try {
                    EditorDefinitionModel existing = editors.find(metamodel.id(), editor.id()).editorDefinition();
                    if(existing.equals(editor)) {
                        diagnostics.add("Reused identical existing editor '" + editor.id() + "'.");
                        return new PersistedVisual(true, editor.id(), committed.validationsImported());
                    }
                } catch(RuntimeException ignored) { /* Optional recovery evidence is ignored only after the canonical path has been preserved. */ }
                diagnostics.add("Editor id conflict for '" + editor.id() + "'; HYDRA kept the existing editor instead of overwriting it.");
                return new PersistedVisual(false,"",committed.validationsImported());
            }
            throw exception;
        }
    }

    /** Descripción: Ejecuta la operación «persistRecoveredEditor» correspondiente a la responsabilidad de VisualOutcome. Entradas: candidate: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PersistedVisual persistRecoveredEditor(EditorDefinitionModel candidate, MetaModel metamodel, String technology,
            List<ExternalProjectArchiveEntry> entries, List<String> diagnostics) {
        EditorDefinitionModel editor = rebaseProjectAssets(candidate, entries, diagnostics);
        var preflight = editors.checkDefinition(metamodel.id(), editor);
        if(!preflight.valid()) {
            diagnostics.add(technology + " editor recovery was preserved but not materialized because the canonical preflight reported: " + preflight.diagnostics().stream().map(value -> value.code()+":"+value.message()).limit(12).toList());
            return new PersistedVisual(false,"",0);
        }
        try {
            var saved=editors.create(metamodel.id(),editor);
            diagnostics.add("Materialized "+technology+" editor '"+saved.editorDefinition().name()+"' from static/canonical project evidence.");
            return new PersistedVisual(true,saved.editorDefinition().id(),0);
        } catch(EditorDefinitionApplicationException exception) {
            if(exception.getReason()==EditorDefinitionApplicationException.Reason.CONFLICT) {
                try { EditorDefinitionModel existing=editors.find(metamodel.id(),editor.id()).editorDefinition(); if(existing.equals(editor)){diagnostics.add("Reused identical existing "+technology+" editor '"+editor.id()+"'.");return new PersistedVisual(true,editor.id(),0);} } catch(RuntimeException ignored) { /* Optional recovery evidence is ignored only after the canonical path has been preserved. */ }
                diagnostics.add(technology+" editor id conflict for '"+editor.id()+"'; the existing editor was not overwritten.");
                return new PersistedVisual(false,"",0);
            }
            throw exception;
        }
    }

    /** Descripción: Ejecuta la operación «rebaseProjectAssets» correspondiente a la responsabilidad de VisualOutcome. Entradas: editor: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorDefinitionModel rebaseProjectAssets(EditorDefinitionModel editor, List<ExternalProjectArchiveEntry> entries, List<String> diagnostics) {
        boolean[] changed = new boolean[]{false};
        var representations = editor.representations().stream().map(rep -> {
            var nodes = rep.nodeMappings().stream().map(mapping -> {
                if(!mapping.imageBacked() || mapping.imageAssetPath().isBlank()) return mapping;
                String wanted = portableAssetReference(mapping.imageAssetPath());
                List<ExternalProjectArchiveEntry> matches = entries.stream()
                        .filter(entry -> portableAssetReference(entry.path()).endsWith(wanted)).toList();
                if(matches.size()!=1) { diagnostics.add("Project image '"+wanted+"' could not be uniquely rebound to an imported archive resource; the original workspace path remains preserved for review."); return mapping; }
                String rebound;
                try { rebound = visualAssets == null ? "external-source/" + matches.get(0).path().replace('\\','/')
                        : visualAssets.store(matches.get(0).path(), matches.get(0).bytes()); }
                catch(RuntimeException exception) { diagnostics.add("Project image '"+wanted+"' was found but could not be promoted to the canonical editor asset store: " + safeMessage(exception)); return mapping; }
                changed[0]=true;
                Map<String,String> annotations = new java.util.LinkedHashMap<>(mapping.annotations());
                annotations.put("hydra.import.reboundProjectAsset",rebound);
                annotations.put("hydra.import.assetScope", EditorVisualAssetService.canonical(rebound) ? "CANONICAL_EDITOR" : "PROJECT");
                return new com.example.editor.definition.NodeMapping(mapping.id(),mapping.targetClassId(),mapping.representationName(),mapping.shape(),mapping.defaultWidth(),mapping.defaultHeight(),mapping.minimumWidth(),mapping.minimumHeight(),mapping.labelExpressionId(),rebound,mapping.imageFit(),mapping.container(),mapping.containmentRelationIds(),mapping.containerPadding(),mapping.styles(),mapping.conditionalStyles(),annotations);
            }).toList();
            return new com.example.editor.definition.RepresentationDefinition(rep.id(),rep.name(),rep.kind(),rep.rootClassIds(),nodes,rep.edgeMappings(),rep.toolIds(),rep.layers(),rep.filters(),rep.editable(),rep.annotations());
        }).toList();
        if(!changed[0]) return editor;
        return new EditorDefinitionModel(editor.schemaVersion(),editor.id(),editor.metamodelId(),editor.name(),representations,editor.palette(),editor.forms(),editor.capabilityManifest(),editor.runtimePolicy(),editor.codeGeneration(),editor.annotations());
    }

    /** Normaliza rutas Eclipse/workspace para poder reenlazar imágenes sin depender del proyecto activo. */
    /** Descripción: Ejecuta la operación «portableAssetReference» correspondiente a la responsabilidad de ExternalProjectSemanticMaterializationService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String portableAssetReference(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\','/');
        normalized = normalized.replaceFirst("(?i)^platform:/plugin/", "")
                .replaceFirst("(?i)^platform:/resource/", "")
                .replaceFirst("^external-source/", "")
                .replaceFirst("^/+", "");
        return normalized;
    }

    /** Devuelve true cuando una fuente visual forma parte de la selección explícita de Update Site. */
    /** Descripción: Ejecuta la operación «visualSourceSelected» correspondiente a la responsabilidad de ExternalProjectSemanticMaterializationService. Entradas: path: ruta o ubicación utilizada por la operación; selectedVisualSources: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean visualSourceSelected(String path, Set<String> selectedVisualSources) {
        if(selectedVisualSources == null) return true;
        String normalized = path == null ? "" : path.replace('\\','/');
        return selectedVisualSources.stream().anyMatch(selected -> normalized.equals(selected)
                || normalized.endsWith("/" + selected) || selected.endsWith("/" + normalized));
    }

    /** Calcula el fingerprint de una entrada ya contenida por el presupuesto seguro del importador origen. */
    /** Descripción: Ejecuta la operación «sha256Bytes» correspondiente a la responsabilidad de ExternalProjectSemanticMaterializationService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256Bytes(byte[] content) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content == null ? new byte[0] : content)); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is not available", exception); }
    }

    /** Descripción: Ejecuta la operación «selectMetamodel» correspondiente a la responsabilidad de VisualOutcome. Entradas: entries: colección de elementos que debe procesarse; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelCandidate selectMetamodel(List<ExternalProjectArchiveEntry> entries, List<String> diagnostics) {
        List<ExternalProjectArchiveEntry> candidates = entries.stream().filter(this::isSemanticCandidate).toList();
        if(candidates.isEmpty()) return null;
        List<String> visualText = entries.stream().filter(this::isVisualReferenceSource)
                .map(value -> StaticSourceInspectionPolicy.inspectText(value).orElse("")).filter(value -> !value.isBlank()).toList();
        List<MetamodelCandidate> ranked = candidates.stream().map(value -> new MetamodelCandidate(value, rank(value, visualText))).sorted(
                Comparator.comparingInt(MetamodelCandidate::rank).reversed().thenComparing(value -> value.entry().path())).toList();
        int best = ranked.get(0).rank(); List<MetamodelCandidate> tied = ranked.stream().filter(value -> value.rank()==best).toList();
        if(tied.size()>1) {
            diagnostics.add("Several equally plausible metamodel sources were found (" + tied.stream().map(value -> value.entry().path()).toList()
                    + "). HYDRA refuses to guess; add an explicit cross-reference or import the desired metamodel first.");
            return null;
        }
        diagnostics.add("Selected semantic source " + ranked.get(0).entry().path() + " using cross-project and technology evidence.");
        return ranked.get(0);
    }

    /** Descripción: Ejecuta la operación «rank» correspondiente a la responsabilidad de VisualOutcome. Entradas: entry: dato de entrada necesario para la operación; visualText: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int rank(ExternalProjectArchiveEntry entry, List<String> visualText) {
        String path=lower(entry.path()); int base = path.endsWith(".ecore") ? 100 : path.endsWith(".xcore") ? 92 : path.endsWith(".emf") ? 90
                : path.endsWith(".xtext") ? 80 : path.endsWith(".langium") ? 75 : path.endsWith(".g4") ? 70 : 50;
        String leaf=leaf(entry.path()); boolean referenced=visualText.stream().anyMatch(text -> text.contains(entry.path()) || text.contains(leaf));
        return base + (referenced ? 50 : 0);
    }
    /** Descripción: Evalúa la condición «isSemanticCandidate» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isSemanticCandidate(ExternalProjectArchiveEntry value) { String path=lower(value.path()); return path.endsWith(".ecore")||path.endsWith(".xcore")||path.endsWith(".emf")||path.endsWith(".xtext")||path.endsWith(".langium")||path.endsWith(".g4"); }
    /** Descripción: Evalúa la condición «isVisualReferenceSource» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isVisualReferenceSource(ExternalProjectArchiveEntry value) { String path=lower(value.path()); return path.endsWith(".odesign")||path.matches(".*\\.gmf(?:graph|tool|map|gen)$")||path.endsWith(".json"); }
    /** Descripción: Ejecuta la operación «looksLikeSiriusWeb» correspondiente a la responsabilidad de VisualOutcome. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean looksLikeSiriusWeb(ExternalProjectArchiveEntry value) { if(!lower(value.path()).endsWith(".json")) return false; String text=StaticSourceInspectionPolicy.inspectText(value).orElse(""); return text.contains("DiagramDescription")||text.contains("NodeDescription")||text.contains("sirius-web")||text.contains("siriusWeb"); }
    /** Descripción: Evalúa la condición «hasSpecializedVisualEvidence» sin modificar el estado externo. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasSpecializedVisualEvidence(List<ExternalProjectArchiveEntry> entries) { return entries.stream().anyMatch(value -> {String p=lower(value.path());return p.endsWith(".odesign")||p.matches(".*\\.gmf(?:graph|tool|map|gen)$")||p.endsWith(".drawio")||looksLikeSiriusWeb(value)||p.contains("glsp");}); }
    /** Descripción: Evalúa la condición «hasValidationEvidence» sin modificar el estado externo. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasValidationEvidence(List<ExternalProjectArchiveEntry> entries) { return entries.stream().anyMatch(value -> {String p=lower(value.path());String text=StaticSourceInspectionPolicy.inspectText(value).orElse("");return p.endsWith(".evl")||p.endsWith(".ocl")||p.contains("validator")||p.contains("quickfix")||text.contains("ValidationAcceptor")||text.contains("@validator")||text.contains("@constraint");}); }
    /** Descripción: Ejecuta la operación «multipart» correspondiente a la responsabilidad de VisualOutcome. Entradas: field: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MultipartFile multipart(String field, ExternalProjectArchiveEntry entry) { return new InMemoryMultipartFile(field, leaf(entry.path()), mediaType(entry.path()), entry.bytes()); }
    /** Descripción: Ejecuta la operación «mediaType» correspondiente a la responsabilidad de VisualOutcome. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mediaType(String path) { String p=lower(path); if(p.endsWith(".json"))return "application/json"; if(p.endsWith(".xml")||p.endsWith(".ecore")||p.endsWith(".xcore")||p.endsWith(".odesign")||p.matches(".*\\.gmf(?:graph|tool|map|gen)$"))return "application/xml"; return "text/plain"; }
    /** Descripción: Ejecuta la operación «summary» correspondiente a la responsabilidad de VisualOutcome. Entradas: result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String summary(VisualDefinitionImportResult result) { return "unresolved="+result.analysis().unresolvedReferences()+", ambiguous="+result.analysis().ambiguousReferences()+", structural="+result.analysis().structuralProblems(); }
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de VisualOutcome. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) { String message=exception.getMessage(); return message==null||message.isBlank()?exception.getClass().getSimpleName():message; }
    /** Descripción: Ejecuta la operación «leaf» correspondiente a la responsabilidad de VisualOutcome. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String leaf(String path) { int slash=Math.max(path.lastIndexOf('/'),path.lastIndexOf('\\')); return slash<0?path:path.substring(slash+1); }
    /** Descripción: Ejecuta la operación «lower» correspondiente a la responsabilidad de VisualOutcome. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String lower(String value) { return value==null?"":value.toLowerCase(Locale.ROOT); }

    private record MetamodelCandidate(ExternalProjectArchiveEntry entry,int rank){}
    private record PersistedVisual(boolean persisted,String editorId,int validations){}
    private record VisualOutcome(List<String> editorIds,int validationsImported,List<String> sources){}
}

package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionChecker;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionSaveResponse;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.NodeCreationTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.PaletteGroup;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.ToolPaletteDefinition;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.project.recovery.ExternalProjectMaterializationResult;
import com.example.editor.project.recovery.ExternalProjectSemanticMaterializationService;
import com.example.editor.validation.action.ActionModelApplicationException;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelApplicationException;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.validation.ValidationModelApplicationException;
import com.example.editor.validation.validation.ValidationModelApplicationService;

/** Convierte borradores recuperados en contratos editables de HYDRA con preview y rollback. */
@Service
public class UpdateSiteLayerMaterializationService {
    private final UpdateSiteImportApplicationService importService;
    private final EditorDefinitionChecker checker;
    private final EditorDefinitionApplicationService editorService;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final UpdateSiteArchiveReader archiveReader;
    private final ExternalProjectSemanticMaterializationService externalMaterialization;
    private final ValidationModelApplicationService validationService;
    private final ActionModelApplicationService actionService;
    private final ExpressionModelApplicationService expressionService;
    private final MetamodelDiagramStorageService diagramStorage;

    /** Constructor de compatibilidad sin almacén de diagramas: la limpieza no elimina el diagrama del metamodelo. */
    /** Descripción: Inicializa una instancia de UpdateSiteLayerMaterializationService sin almacén de diagramas. Entradas: importService, checker, editorService, metamodelService, archiveReader, externalMaterialization, validationService, actionService y expressionService: colaboradores de la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteLayerMaterializationService(UpdateSiteImportApplicationService importService,
            EditorDefinitionChecker checker, EditorDefinitionApplicationService editorService,
            CanonicalMetamodelApplicationService metamodelService, UpdateSiteArchiveReader archiveReader,
            ExternalProjectSemanticMaterializationService externalMaterialization,
            ValidationModelApplicationService validationService, ActionModelApplicationService actionService,
            ExpressionModelApplicationService expressionService) {
        this(importService, checker, editorService, metamodelService, archiveReader, externalMaterialization,
                validationService, actionService, expressionService, null);
    }

    @Autowired
    /** Inicializa el materializador con el pipeline profundo compartido por las importaciones externas. */
    /** Descripción: Inicializa una instancia de UpdateSiteLayerMaterializationService con sus dependencias y datos de entrada. Entradas: importService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación; editorService: servicio colaborador utilizado por la operación; metamodelService: servicio colaborador utilizado por la operación; archiveReader: archivo o contenido que debe procesarse; externalMaterialization: dato de entrada necesario para la operación; validationService: servicio colaborador utilizado por la operación; actionService: servicio colaborador utilizado por la operación; expressionService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteLayerMaterializationService(UpdateSiteImportApplicationService importService,
            EditorDefinitionChecker checker, EditorDefinitionApplicationService editorService,
            CanonicalMetamodelApplicationService metamodelService, UpdateSiteArchiveReader archiveReader,
            ExternalProjectSemanticMaterializationService externalMaterialization,
            ValidationModelApplicationService validationService, ActionModelApplicationService actionService,
            ExpressionModelApplicationService expressionService, MetamodelDiagramStorageService diagramStorage) {
        this.diagramStorage = diagramStorage;
        this.importService = importService;
        this.checker = checker;
        this.editorService = editorService;
        this.metamodelService = metamodelService;
        this.archiveReader = archiveReader;
        this.externalMaterialization = externalMaterialization;
        this.validationService = validationService;
        this.actionService = actionService;
        this.expressionService = expressionService;
    }

    /** Constructor reducido para previews unitarios que no persisten ni recorren archivos externos. */
    /** Descripción: Inicializa una instancia de UpdateSiteLayerMaterializationService con sus dependencias y datos de entrada. Entradas: importService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación; editorService: servicio colaborador utilizado por la operación; metamodelService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteLayerMaterializationService(UpdateSiteImportApplicationService importService,
            EditorDefinitionChecker checker, EditorDefinitionApplicationService editorService,
            CanonicalMetamodelApplicationService metamodelService) {
        this(importService, checker, editorService, metamodelService, null, null, null, null, null);
    }

    /** Constructor de pruebas focales que ejercitan el pipeline profundo sin documentos de validación persistentes. */
    /** Descripción: Inicializa una instancia de UpdateSiteLayerMaterializationService con sus dependencias y datos de entrada. Entradas: importService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación; editorService: servicio colaborador utilizado por la operación; metamodelService: servicio colaborador utilizado por la operación; archiveReader: archivo o contenido que debe procesarse; externalMaterialization: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteLayerMaterializationService(UpdateSiteImportApplicationService importService,
            EditorDefinitionChecker checker, EditorDefinitionApplicationService editorService,
            CanonicalMetamodelApplicationService metamodelService, UpdateSiteArchiveReader archiveReader,
            ExternalProjectSemanticMaterializationService externalMaterialization) {
        this(importService, checker, editorService, metamodelService, archiveReader, externalMaterialization,
                null, null, null);
    }

    /** Materializa el metamodelo y las capas recuperadas usando los adaptadores profundos compartidos. */
    /** Descripción: Ejecuta la operación «materialize» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: files: archivo o contenido que debe procesarse; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMaterializationResult materialize(List<UpdateSiteInputFile> files,
            UpdateSiteLayerMaterializationRequest request) {
        UpdateSiteImportOptions options = new UpdateSiteImportOptions(request.targetId(), false, request.strict(),
                request.candidateId(), true, true, true, true);
        UpdateSiteImportAnalysis analysis = importService.analyze(files, options);
        UpdateSiteLayerMaterializationPreview preview = preview(analysis, request);
        if(!request.persist()) return new UpdateSiteLayerMaterializationResult(preview, "", "", preview.editorDefinition().id(), "", false, false, preview.diagnostics());
        if(!preview.applicable()) throw new IllegalArgumentException("Recovered layers contain blocking references and cannot be persisted");

        // El análisis anterior ya verificó límites, reconciliación y referencias. No se vuelve a analizar antes de guardar.
        UpdateSiteImportResult imported = importService.importPrepared(analysis, options);
        String metamodelId = imported.metamodel().id();
        String metamodelEtag = imported.metamodel().etag();
        MetaModel persistedMetamodel = imported.metamodel().preview().metamodel();
        List<String> diagnostics = new ArrayList<>(preview.diagnostics());
        try {
            ExternalProjectMaterializationResult rich = null;
            if(archiveReader != null && externalMaterialization != null) {
                UpdateSiteRepositoryContent repository = archiveReader.read(files);
                rich = externalMaterialization.materializeExisting(persistedMetamodel, repository.files(),
                        selectedVisualSources(analysis, request.selection()));
                diagnostics.addAll(rich.diagnostics());
                diagnostics.add("Layer materialization: "
                        + (rich.visualValidationsImported() + rich.validationRulesImported()) + " validation rule(s), "
                        + rich.quickFixesImported() + " quick fix(es), " + rich.editorIds().size() + " rich editor(s).");
            }

            if(rich != null && !rich.editorIds().isEmpty()) {
                String editorId = rich.editorIds().get(0);
                var loaded = editorService.find(metamodelId, editorId);
                return new UpdateSiteLayerMaterializationResult(preview, metamodelId, metamodelEtag,
                        editorId, loaded.etag(), true, true, diagnostics);
            }
            if(rich != null && requiresDeepVisualFidelity(analysis, request.selection())) {
                // Rechazo de negocio previsible (la petición no puede materializarse sin pérdida): 400 INVALID_REQUEST, no 500.
                throw new IllegalArgumentException("Selected Sirius/GMF/GLSP visual definitions could not be materialized with full fidelity; HYDRA refused to replace them with a lossy generic editor");
            }

            // Fallback solo para formatos neutrales que todavía no tienen adaptador profundo.
            EditorDefinitionModel editor = withMetamodelId(preview.editorDefinition(), metamodelId);
            EditorDefinitionSaveResponse saved = editorService.create(metamodelId, editor);
            return new UpdateSiteLayerMaterializationResult(preview, metamodelId, metamodelEtag,
                    saved.editorDefinition().id(), saved.etag(), true, true, diagnostics);
        } catch(RuntimeException exception) {
            for(RuntimeException rollbackFailure : cleanupImportedLayers(metamodelId, metamodelEtag, diagnostics))
                exception.addSuppressed(rollbackFailure);
            throw exception;
        }
    }

    /** Revierte todas las capas canónicas creadas por una materialización de Update Site. */
    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMaterializationRollbackResult rollback(UpdateSiteLayerMaterializationRollbackRequest request) {
        List<String> diagnostics = new ArrayList<>();
        List<RuntimeException> failures = cleanupImportedLayers(request.metamodelId(), request.metamodelEtag(), diagnostics);
        boolean editorDeleted = diagnostics.stream().anyMatch(value -> value.startsWith("Removed ") && value.contains(" recovered editor"));
        boolean metamodelDeleted = diagnostics.stream().anyMatch(value -> value.equals("Removed imported metamodel."));
        if(!failures.isEmpty()) diagnostics.add("Rollback completed with " + failures.size() + " cleanup failure(s); see diagnostics above.");
        return new UpdateSiteLayerMaterializationRollbackResult(editorDeleted, metamodelDeleted, diagnostics);
    }

    /**
     * Elimina documentos dependientes antes del metamodelo. Update Site importa con overwrite=false, por lo que
     * todo documento con este metamodelId pertenece a la misma materialización y puede limpiarse sin tocar otro DSL.
     */
    /** Descripción: Ejecuta la operación «cleanupImportedLayers» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; metamodelEtag: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RuntimeException> cleanupImportedLayers(String metamodelId, String metamodelEtag, List<String> diagnostics) {
        List<RuntimeException> failures = new ArrayList<>();
        if(metamodelId == null || metamodelId.isBlank()) return failures;

        if(editorService != null) {
            try {
                List<com.example.editor.definition.EditorDefinitionSummary> editors = editorService.list(metamodelId);
                for(var editor : editors) {
                    try { editorService.delete(metamodelId, editor.id(), editor.etag()); diagnostics.add("Removed recovered editor '" + editor.id() + "'."); }
                    catch(RuntimeException exception) { failures.add(exception); diagnostics.add("Editor rollback failed for '" + editor.id() + "': " + exception.getMessage()); }
                }
            } catch(RuntimeException exception) { failures.add(exception); diagnostics.add("Could not enumerate recovered editors during rollback: " + exception.getMessage()); }
        }
        deleteValidationIfPresent(metamodelId, diagnostics, failures);
        deleteActionIfPresent(metamodelId, diagnostics, failures);
        deleteExpressionIfPresent(metamodelId, diagnostics, failures);
        if(diagramStorage != null) {
            try { diagramStorage.delete(metamodelId); diagnostics.add("Removed imported metamodel diagram."); }
            catch(RuntimeException exception) { failures.add(exception); diagnostics.add("Metamodel diagram rollback failed: " + exception.getMessage()); }
        }

        try {
            metamodelService.delete(metamodelId, metamodelEtag);
            diagnostics.add("Removed imported metamodel.");
        } catch(RuntimeException exception) { failures.add(exception); diagnostics.add("Metamodel rollback failed: " + exception.getMessage()); }
        return failures;
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteValidationIfPresent» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; diagnostics: dato de entrada necesario para la operación; failures: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteValidationIfPresent(String metamodelId, List<String> diagnostics, List<RuntimeException> failures) {
        if(validationService == null) return;
        try { var loaded = validationService.find(metamodelId); validationService.delete(metamodelId, loaded.etag()); diagnostics.add("Removed recovered validation model."); }
        catch(ValidationModelApplicationException exception) { if(exception.getReason()!=ValidationModelApplicationException.Reason.NOT_FOUND) { failures.add(exception); diagnostics.add("Validation rollback failed: " + exception.getMessage()); } }
        catch(RuntimeException exception) { failures.add(exception); diagnostics.add("Validation rollback failed: " + exception.getMessage()); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteActionIfPresent» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; diagnostics: dato de entrada necesario para la operación; failures: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteActionIfPresent(String metamodelId, List<String> diagnostics, List<RuntimeException> failures) {
        if(actionService == null) return;
        try { var loaded = actionService.find(metamodelId); actionService.delete(metamodelId, loaded.etag()); diagnostics.add("Removed recovered action model."); }
        catch(ActionModelApplicationException exception) { if(exception.getReason()!=ActionModelApplicationException.Reason.NOT_FOUND) { failures.add(exception); diagnostics.add("Action rollback failed: " + exception.getMessage()); } }
        catch(RuntimeException exception) { failures.add(exception); diagnostics.add("Action rollback failed: " + exception.getMessage()); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteExpressionIfPresent» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; diagnostics: dato de entrada necesario para la operación; failures: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteExpressionIfPresent(String metamodelId, List<String> diagnostics, List<RuntimeException> failures) {
        if(expressionService == null) return;
        try { var loaded = expressionService.find(metamodelId); expressionService.delete(metamodelId, loaded.etag()); diagnostics.add("Removed recovered expression model."); }
        catch(ExpressionModelApplicationException exception) { if(exception.getReason()!=ExpressionModelApplicationException.Reason.NOT_FOUND) { failures.add(exception); diagnostics.add("Expression rollback failed: " + exception.getMessage()); } }
        catch(RuntimeException exception) { failures.add(exception); diagnostics.add("Expression rollback failed: " + exception.getMessage()); }
    }

    /** Evita convertir silenciosamente una notación especializada en un editor neutral incompleto. */
    /** Descripción: Valida las condiciones necesarias para «requiresDeepVisualFidelity» y rechaza entradas no válidas. Entradas: analysis: dato de entrada necesario para la operación; selection: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean requiresDeepVisualFidelity(UpdateSiteImportAnalysis analysis, UpdateSiteLayerSelection selection) {
        List<UpdateSiteRepresentationDraft> selected = select(analysis.recoveredLayers().representations(),
                selection == null ? List.of() : selection.representationIds(), UpdateSiteRepresentationDraft::id,
                selection != null && selection.explicit());
        return selected.stream().anyMatch(value -> {
            String technology = value.technology().toUpperCase(Locale.ROOT);
            String path = value.sourcePath().toLowerCase(Locale.ROOT);
            return technology.contains("SIRIUS") || technology.contains("GMF") || technology.contains("GLSP")
                    || technology.contains("DRAWIO") || path.endsWith(".odesign") || path.endsWith(".drawio")
                    || path.matches(".*\\.gmf(?:graph|tool|map|gen)$");
        });
    }

    /**
     * Traduce la selección del usuario a fuentes visuales reales. {@code null} significa selección automática/all;
     * un conjunto vacío significa que la selección explícita no pidió ninguna representación visual.
     */
    /** Descripción: Ejecuta la operación «selectedVisualSources» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: analysis: dato de entrada necesario para la operación; selection: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> selectedVisualSources(UpdateSiteImportAnalysis analysis, UpdateSiteLayerSelection selection) {
        if(selection == null || !selection.explicit()) return null;
        Set<String> sources = new LinkedHashSet<>();
        select(analysis.recoveredLayers().representations(), selection.representationIds(),
                UpdateSiteRepresentationDraft::id, true).stream().map(UpdateSiteRepresentationDraft::sourcePath)
                .filter(value -> value != null && !value.isBlank()).forEach(sources::add);
        select(analysis.recoveredLayers().mappings(), selection.mappingIds(), UpdateSiteMappingDraft::id, true).stream()
                .map(UpdateSiteMappingDraft::sourcePath).filter(value -> value != null && !value.isBlank()).forEach(sources::add);
        return Set.copyOf(sources);
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: analysis: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteLayerMaterializationPreview preview(UpdateSiteImportAnalysis analysis,
            UpdateSiteLayerMaterializationRequest request) {
        if(analysis.metamodelPreview() == null) throw new IllegalArgumentException("A recovered metamodel preview is required before materializing layers");
        MetaModel metamodel = analysis.metamodelPreview().metamodel();
        UpdateSiteLayerSelection selection = request.selection();
        List<UpdateSitePaletteGroupDraft> paletteDrafts = select(analysis.recoveredLayers().palettes(), selection.paletteGroupIds(), UpdateSitePaletteGroupDraft::id, selection.explicit());
        Set<String> selectedToolIds = selection.paletteToolIds().isEmpty() ? Set.of() : Set.copyOf(selection.paletteToolIds());
        List<UpdateSiteRepresentationDraft> representationDrafts = select(analysis.recoveredLayers().representations(), selection.representationIds(), UpdateSiteRepresentationDraft::id, selection.explicit());
        List<UpdateSiteMappingDraft> mappingDrafts = select(analysis.recoveredLayers().mappings(), selection.mappingIds(), UpdateSiteMappingDraft::id, selection.explicit());
        List<UpdateSiteSemanticElementDraft> semanticDrafts = select(analysis.semanticRecovery().elements(), selection.semanticElementIds(), UpdateSiteSemanticElementDraft::id, selection.explicit());
        List<String> diagnostics = new ArrayList<>();

        Map<String, MetaClass> classes = classIndex(metamodel);
        String editorId = request.editorId().isBlank() ? safeDocument("editor-" + metamodel.id() + "-recovered") : safeDocument(request.editorId());
        String editorName = request.editorName().isBlank() ? metamodel.name() + " recovered editor" : request.editorName();

        List<RepresentationDefinition> representations = new ArrayList<>();
        List<NodeCreationTool> tools = new ArrayList<>();
        List<PaletteGroup> groups = new ArrayList<>();
        Map<String, List<String>> toolsByGroup = new LinkedHashMap<>();
        Map<String, String> toolRepresentation = new LinkedHashMap<>();

        boolean paletteToolsRequested = selection.explicit()
                ? !selectedToolIds.isEmpty()
                : paletteDrafts.stream().anyMatch(group -> !group.tools().isEmpty());
        boolean needsDefaultRepresentation = representationDrafts.isEmpty()
                && (paletteToolsRequested || !mappingDrafts.isEmpty());
        List<UpdateSiteRepresentationDraft> effectiveRepresentations = needsDefaultRepresentation
                ? List.of(new UpdateSiteRepresentationDraft("recovered-default", "Recovered diagram", "HYDRA", "DIAGRAM",
                        metamodel.classes().stream().filter(value -> value.kind() == MetaClassKind.CLASS).limit(1).map(MetaClass::id).toList(),
                        "", UpdateSiteConfidence.RECOVERED, List.of("Created because no native representation draft was selected.")))
                : representationDrafts;

        Set<String> materializedToolIds = new LinkedHashSet<>();
        for(UpdateSitePaletteGroupDraft group : paletteDrafts) {
            String groupId = safe("group:" + group.id());
            List<String> groupTools = new ArrayList<>();
            for(UpdateSitePaletteToolDraft draft : group.tools()) {
                if(selection.explicit() && !selectedToolIds.contains(draft.id())) continue;
                MetaClass target = resolveClass(classes, draft.targetElement());
                if(target == null || target.kind() != MetaClassKind.CLASS) {
                    diagnostics.add("Palette tool " + draft.label() + " was skipped because its target class could not be resolved: " + draft.targetElement());
                    continue;
                }
                String toolId = safe("tool:" + draft.id());
                if(effectiveRepresentations.isEmpty()) {
                    diagnostics.add("Palette tool " + draft.label() + " was skipped because no representation was selected or generated.");
                    continue;
                }
                String representationId = safe("representation:" + effectiveRepresentations.getFirst().id());
                tools.add(new NodeCreationTool(toolId, draft.label(), "Recovered from " + draft.sourcePath(), "", List.of(representationId), "", "", target.id(), Map.of(), ""));
                groupTools.add(toolId);
                materializedToolIds.add(draft.id());
                toolRepresentation.put(toolId, representationId);
            }
            if(!groupTools.isEmpty()) {
                toolsByGroup.put(groupId, groupTools);
                groups.add(new PaletteGroup(groupId, group.label(), group.order(), groupTools, false));
            }
        }
        selectedToolIds.stream().filter(id -> !materializedToolIds.contains(id)).sorted()
                .forEach(id -> diagnostics.add("Selected palette tool was not materialized because it was absent, outside the selected groups or had an unresolved target: " + id));

        for(UpdateSiteRepresentationDraft draft : effectiveRepresentations) {
            String representationId = safe("representation:" + draft.id());
            List<NodeMapping> nodes = new ArrayList<>();
            Set<String> mappedClassIds = new LinkedHashSet<>();
            // Los mappings de nodo son únicos en todo el editor: con varias representaciones se califican con la representación.
            String scope = effectiveRepresentations.size() > 1 ? draft.id() + ":" : "";
            for(UpdateSiteMappingDraft mapping : mappingDrafts) {
                if(mapping.mappingKind().toLowerCase(Locale.ROOT).contains("edge")) continue;
                MetaClass target = resolveClass(classes, mapping.semanticElement());
                if(target == null) continue;
                if(mappedClassIds.add(target.id())) nodes.add(nodeMapping(scope + mapping.id(), target, mapping));
            }
            for(String root : draft.rootElements()) {
                MetaClass target = resolveClass(classes, root);
                if(target != null && mappedClassIds.add(target.id())) nodes.add(nodeMapping(scope + "root:" + target.id(), target, null));
            }
            if(nodes.isEmpty()) {
                metamodel.classes().stream().filter(value -> value.kind() == MetaClassKind.CLASS).limit(30)
                        .forEach(value -> nodes.add(nodeMapping(scope + "auto:" + value.id(), value, null)));
            }
            List<String> representationToolIds = toolRepresentation.entrySet().stream().filter(item -> item.getValue().equals(representationId)).map(Map.Entry::getKey).toList();
            representations.add(new RepresentationDefinition(representationId, draft.name(), representationKind(draft.kind()),
                    roots(draft.rootElements(), classes, nodes), nodes, List.of(), representationToolIds, true,
                    Map.of("hydra.recovered.source", draft.sourcePath(), "hydra.recovered.confidence", draft.confidence().name())));
        }

        if(groups.isEmpty() && !tools.isEmpty()) groups.add(new PaletteGroup("group:recovered", "Recovered tools", 0, tools.stream().map(NodeCreationTool::id).toList(), false));
        Set<EditorCapability> capabilities = new LinkedHashSet<>(Set.of(EditorCapability.MODEL_EDITING, EditorCapability.MULTIPLE_REPRESENTATIONS));
        if(semanticDrafts.stream().anyMatch(item -> item.kind() == UpdateSiteSemanticKind.COMMAND || item.kind() == UpdateSiteSemanticKind.HANDLER)) capabilities.add(EditorCapability.OPERATIONS);
        if(semanticDrafts.stream().anyMatch(item -> item.kind() == UpdateSiteSemanticKind.VALIDATION)) capabilities.add(EditorCapability.VALIDATION_RUNTIME);
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("hydra.recovered.analysisId", analysis.analysisId());
        annotations.put("hydra.recovered.semanticElements", Integer.toString(semanticDrafts.size()));
        annotations.put("hydra.recovered.materialization", "preview");
        EditorDefinitionModel editor = new EditorDefinitionModel(null, editorId, metamodel.id(), editorName,
                representations, new ToolPaletteDefinition(groups, tools.stream().map(value -> (com.example.editor.definition.EditorTool) value).toList()), List.of(),
                new EditorCapabilityManifest(capabilities), annotations);
        EditorDefinitionCheckReport report = checker.check(editor, metamodel, null, null);
        diagnostics.addAll(report.diagnostics().stream().map(item -> item.code() + ": " + item.message()).toList());
        return new UpdateSiteLayerMaterializationPreview(analysis.analysisId(), editor, report, groups.size(), tools.size(),
                representations.size(), mappingDrafts.size(), semanticDrafts.size(), report.valid(), diagnostics);
    }

    /** Descripción: Ejecuta la operación «nodeMapping» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: id: identificador que delimita el recurso objetivo; target: dato de entrada necesario para la operación; draft: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeMapping nodeMapping(String id, MetaClass target, UpdateSiteMappingDraft draft) {
        Map<String, String> annotations = draft == null ? Map.of("hydra.recovered.auto", "true")
                : Map.of("hydra.recovered.source", draft.sourcePath(), "hydra.recovered.confidence", draft.confidence().name());
        return new NodeMapping(safe("node:" + id), target.id(), target.name(), EditorNodeShape.ROUNDED_RECTANGLE, 180, 96, 100, 56, "", "", com.example.editor.definition.EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), annotations);
    }

    /** Descripción: Ejecuta la operación «representationKind» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RepresentationKind representationKind(String kind) {
        String value = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        if(value.contains("TREE")) return RepresentationKind.TREE;
        if(value.contains("TABLE")) return RepresentationKind.TABLE;
        if(value.contains("FORM")) return RepresentationKind.FORM;
        if(value.contains("MATRIX")) return RepresentationKind.MATRIX;
        if(value.contains("DASHBOARD")) return RepresentationKind.DASHBOARD;
        return RepresentationKind.DIAGRAM;
    }

    /** Descripción: Ejecuta la operación «roots» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: roots: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> roots(List<String> roots, Map<String, MetaClass> classes, List<NodeMapping> nodes) {
        List<String> resolved = roots.stream().map(value -> resolveClass(classes, value)).filter(java.util.Objects::nonNull).map(MetaClass::id).distinct().toList();
        if(!resolved.isEmpty()) return resolved;
        return nodes.stream().limit(1).map(NodeMapping::targetClassId).toList();
    }

    /** Descripción: Ejecuta la operación «classIndex» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, MetaClass> classIndex(MetaModel model) {
        Map<String, MetaClass> result = new LinkedHashMap<>();
        model.classes().forEach(value -> { result.put(normalize(value.id()), value); result.put(normalize(value.name()), value); });
        return result;
    }
    /** Descripción: Obtiene la información correspondiente a «resolveClass» dentro de UpdateSiteLayerMaterializationService. Entradas: classes: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass resolveClass(Map<String, MetaClass> classes, String value) { return classes.get(normalize(value)); }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) {
        if(value == null) return "";
        String normalized = value.replace("//", "").replaceAll(".*[:/#]", "");
        return normalized.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safe(String value) {
        String normalized = value == null ? "recovered" : value.replaceAll("[^A-Za-z0-9._:-]", "-");
        return normalized.isBlank() ? "recovered" : normalized;
    }
    /** Descripción: Ejecuta la operación «safeDocument» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeDocument(String value) {
        String normalized = value == null ? "recovered-editor" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        if(normalized.isBlank() || !Character.isLetterOrDigit(normalized.charAt(0))) normalized = "editor-" + normalized;
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }
    /** Descripción: Ejecuta la operación «withMetamodelId» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: source: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private EditorDefinitionModel withMetamodelId(EditorDefinitionModel source, String metamodelId) {
        return new EditorDefinitionModel(source.schemaVersion(), source.id(), metamodelId, source.name(), source.representations(),
                source.palette(), source.forms(), source.capabilityManifest(), source.runtimePolicy(), source.annotations());
    }
    /** Descripción: Ejecuta la operación «select» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationService. Entradas: values: colección de elementos que debe procesarse; ids: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; explicit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> List<T> select(List<T> values, List<String> ids,
            java.util.function.Function<T, String> id, boolean explicit) {
        List<T> safeValues = values == null ? List.of() : List.copyOf(values);
        if(ids == null || ids.isEmpty()) return explicit ? List.of() : safeValues;
        Set<String> selected = Set.copyOf(ids);
        return safeValues.stream().filter(value -> selected.contains(id.apply(value))).toList();
    }
}

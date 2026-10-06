package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportPreview;
import com.example.editor.metamodel.importing.MetamodelImportSaveResult;
import com.example.editor.metamodel.importing.MetamodelImportService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Orquesta p2, clasificación, recuperación, reconciliación, trazabilidad y capas neutrales. */
@Service
public class UpdateSiteImportApplicationService {
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteArchiveReader archiveReader;
    private final P2RepositoryMetadataParser metadataParser;
    private final P2BundleAnalyzer bundleAnalyzer;
    private final UpdateSiteMetamodelRecoveryService recoveryService;
    private final UpdateSiteProvenanceService provenanceService;
    private final UpdateSiteLayerRecoveryService layerRecoveryService;
    private final UpdateSiteRecoveryPackageStore packageStore;
    private final MetamodelImportService metamodelImportService;
    private final UpdateSiteMetamodelReconciliationService reconciliationService;
    private final UpdateSiteP2AdvancedAnalysisService releaseService;
    private final UpdateSiteSemanticEditorRecoveryService semanticService;
    private final UpdateSiteImportObservabilityService observabilityService;
    private final UpdateSiteAnalysisCache analysisCache;
    private final ObjectMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteImportApplicationService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; archiveReader: archivo o contenido que debe procesarse; metadataParser: componente de conversión utilizado durante el procesamiento; bundleAnalyzer: dato de entrada necesario para la operación; recoveryService: servicio colaborador utilizado por la operación; provenanceService: servicio colaborador utilizado por la operación; layerRecoveryService: servicio colaborador utilizado por la operación; packageStore: dato de entrada necesario para la operación; metamodelImportService: servicio colaborador utilizado por la operación; reconciliationService: servicio colaborador utilizado por la operación; releaseService: servicio colaborador utilizado por la operación; semanticService: servicio colaborador utilizado por la operación; observabilityService: servicio colaborador utilizado por la operación; analysisCache: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteImportApplicationService(UpdateSiteImportProperties properties,
            UpdateSiteArchiveReader archiveReader, P2RepositoryMetadataParser metadataParser,
            P2BundleAnalyzer bundleAnalyzer, UpdateSiteMetamodelRecoveryService recoveryService,
            UpdateSiteProvenanceService provenanceService, UpdateSiteLayerRecoveryService layerRecoveryService,
            UpdateSiteRecoveryPackageStore packageStore, MetamodelImportService metamodelImportService,
            UpdateSiteMetamodelReconciliationService reconciliationService,
            UpdateSiteP2AdvancedAnalysisService releaseService,
            UpdateSiteSemanticEditorRecoveryService semanticService,
            UpdateSiteImportObservabilityService observabilityService, UpdateSiteAnalysisCache analysisCache,
            ObjectMapper mapper) {
        this.properties = properties;
        this.archiveReader = archiveReader;
        this.metadataParser = metadataParser;
        this.bundleAnalyzer = bundleAnalyzer;
        this.recoveryService = recoveryService;
        this.provenanceService = provenanceService;
        this.layerRecoveryService = layerRecoveryService;
        this.packageStore = packageStore;
        this.metamodelImportService = metamodelImportService;
        this.reconciliationService = reconciliationService;
        this.releaseService = releaseService;
        this.semanticService = semanticService;
        this.observabilityService = observabilityService;
        this.analysisCache = analysisCache;
        this.mapper = mapper;
    }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: files: archivo o contenido que debe procesarse; requestedOptions: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportAnalysis analyze(List<UpdateSiteInputFile> files, UpdateSiteImportOptions requestedOptions) {
        ensureEnabled();
        long started = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        UpdateSiteImportOptions options = requestedOptions == null ? UpdateSiteImportOptions.defaults() : requestedOptions;

        long stageStarted = System.nanoTime();
        UpdateSiteRepositoryContent repository = archiveReader.read(files);
        stage(stages, "repository-read", stageStarted);

        String cacheKey = analysisId(repository, options.candidateId().isBlank() ? "auto" : options.candidateId(), options);
        stageStarted = System.nanoTime();
        java.util.Optional<UpdateSiteImportAnalysis> cached = analysisCache.find(cacheKey);
        stage(stages, "cache-lookup", stageStarted);
        if(cached.isPresent()) return withPerformance(cached.get(), true, started, repository, stages);

        stageStarted = System.nanoTime();
        P2MetadataSnapshot metadata = metadataParser.parse(repository);
        stage(stages, "p2-metadata", stageStarted);
        stageStarted = System.nanoTime();
        UpdateSiteRepositorySummary summary = bundleAnalyzer.analyze(repository, metadata);
        stage(stages, "bundle-analysis", stageStarted);
        stageStarted = System.nanoTime();
        List<UpdateSiteRecoveryCandidate> candidates = recoveryService.discover(repository, summary);
        stage(stages, "candidate-discovery", stageStarted);
        stageStarted = System.nanoTime();
        UpdateSiteRecoveredLayers layers = layerRecoveryService.recover(repository, options);
        stage(stages, "layer-recovery", stageStarted);
        stageStarted = System.nanoTime();
        UpdateSiteSemanticRecovery semantic = semanticService.recover(repository);
        stage(stages, "semantic-recovery", stageStarted);
        stageStarted = System.nanoTime();
        UpdateSiteReleaseComparison releases = releaseService.analyze(repository, summary);
        stage(stages, "release-comparison", stageStarted);
        stageStarted = System.nanoTime();
        UpdateSiteMetamodelReconciliationService.ReconciliationState reconciliationState =
                reconciliationService.analyze(candidates, repository, options.targetId());
        UpdateSiteReconciliationReport reconciliation = reconciliationState.report();
        stage(stages, "reconciliation", stageStarted);
        UpdateSiteRecoveryCandidate selected = selectCandidate(candidates, options.candidateId());
        List<String> diagnostics = new ArrayList<>(summary.diagnostics());
        diagnostics.addAll(layers.diagnostics());
        diagnostics.addAll(semantic.diagnostics());
        diagnostics.addAll(releases.diagnostics());
        diagnostics.addAll(reconciliation.diagnostics());

        if(selected == null) {
            diagnostics.add("No recoverable metamodel candidate was found in the update site");
            stageStarted = System.nanoTime();
            UpdateSiteObservability observability = observabilityService.build(repository, summary, candidates, null,
                    List.of(), layers, semantic, reconciliation, releases, diagnostics);
            stage(stages, "observability", stageStarted);
            return cache(cacheKey, withPerformance(result(repository, options, "none", summary, candidates, "", null, List.of(), layers,
                    false, diagnostics, observability, reconciliation, releases, semantic), false, started, repository, stages));
        }

        MetaModel recovered;
        stageStarted = System.nanoTime();
        try {
            recovered = recoveryService.recover(selected, repository, options.targetId());
            stage(stages, "metamodel-recovery", stageStarted);
        } catch(RuntimeException exception) {
            stage(stages, "metamodel-recovery", stageStarted);
            diagnostics.add("The selected candidate could not be recovered: " + exception.getMessage());
            stageStarted = System.nanoTime();
            UpdateSiteObservability observability = observabilityService.build(repository, summary, candidates, selected,
                    List.of(), layers, semantic, reconciliation, releases, diagnostics);
            stage(stages, "observability", stageStarted);
            return cache(cacheKey, withPerformance(result(repository, options, selected.id(), summary, candidates, selected.id(), null, List.of(), layers,
                    false, diagnostics, observability, reconciliation, releases, semantic), false, started, repository, stages));
        }

        stageStarted = System.nanoTime();
        UpdateSiteProvenanceService.ProvenancedMetamodel provenanced = provenanceService.enrich(recovered, selected);
        stage(stages, "provenance", stageStarted);
        stageStarted = System.nanoTime();
        MetamodelImportPreview preview = previewNeutral(provenanced.metamodel(), options);
        stage(stages, "neutral-preview", stageStarted);
        boolean importable = selected.importable() && preview != null;
        if(options.strict() && selected.confidence() != UpdateSiteConfidence.EXACT) {
            importable = false;
            diagnostics.add("Strict mode accepts only EXACT recovery; selected candidate is " + selected.confidence());
        }
        if(selected.confidence() == UpdateSiteConfidence.INFERRED) {
            diagnostics.add("The selected metamodel is inferred and must be reviewed before it is treated as authoritative");
        }
        stageStarted = System.nanoTime();
        UpdateSiteObservability observability = observabilityService.build(repository, summary, candidates, selected,
                provenanced.provenance(), layers, semantic, reconciliation, releases, diagnostics);
        stage(stages, "observability", stageStarted);
        return cache(cacheKey, withPerformance(result(repository, options, selected.id(), summary, candidates, selected.id(), preview,
                provenanced.provenance(), layers, importable, diagnostics, observability, reconciliation, releases, semantic), false, started, repository, stages));
    }

    /** Descripción: Ejecuta la operación «reconcile» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: files: archivo o contenido que debe procesarse; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteReconciliationResult reconcile(List<UpdateSiteInputFile> files,
            UpdateSiteReconciliationRequest request) {
        ensureEnabled();
        UpdateSiteRepositoryContent repository = archiveReader.read(files);
        P2MetadataSnapshot metadata = metadataParser.parse(repository);
        UpdateSiteRepositorySummary summary = bundleAnalyzer.analyze(repository, metadata);
        List<UpdateSiteRecoveryCandidate> candidates = recoveryService.discover(repository, summary);
        UpdateSiteMetamodelReconciliationService.ReconciliationState state =
                reconciliationService.analyze(candidates, repository, request.targetId());
        MetaModel merged = reconciliationService.merge(state, request);
        UpdateSiteImportOptions options = new UpdateSiteImportOptions(request.targetId(), false, false,
                request.primaryCandidateId(), true, true, true, true);
        MetamodelImportPreview preview = previewNeutral(merged, options);
        return new UpdateSiteReconciliationResult(state.report(), preview, preview != null);
    }

    /** Descripción: Ejecuta la operación «importAndSave» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: files: archivo o contenido que debe procesarse; requestedOptions: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportResult importAndSave(List<UpdateSiteInputFile> files, UpdateSiteImportOptions requestedOptions) {
        UpdateSiteImportOptions options = requestedOptions == null ? UpdateSiteImportOptions.defaults() : requestedOptions;
        return importPrepared(analyze(files, options), options);
    }

    /**
     * Persiste un análisis ya calculado sin volver a recorrer el update site. El materializador multicapa utiliza
     * este camino para que ZIP/JAR grandes no se descompriman y analicen dos veces antes de guardar sus capas.
     */
    /** Descripción: Ejecuta la operación «importPrepared» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: analysis: dato de entrada necesario para la operación; requestedOptions: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportResult importPrepared(UpdateSiteImportAnalysis analysis, UpdateSiteImportOptions requestedOptions) {
        UpdateSiteImportOptions options = requestedOptions == null ? UpdateSiteImportOptions.defaults() : requestedOptions;
        if(analysis == null || !analysis.importable() || analysis.metamodelPreview() == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                    "Update-site analysis is not importable: " + (analysis == null ? "missing analysis" : String.join("; ", analysis.diagnostics())));
        }
        byte[] content = serialize(analysis.metamodelPreview().metamodel());
        MetamodelImportSaveResult saved = metamodelImportService.importAndSave(
                analysis.metamodelPreview().metamodel().id() + ".metamodel.json", "application/json", content,
                new MetamodelImportOptions("json", analysis.metamodelPreview().metamodel().id(), options.overwrite(),
                        true, true, options.strict()));
        String packagePath = "";
        if(options.persistRecoveryPackage()) {
            UpdateSiteRecoveryCandidate candidate = analysis.candidates().stream()
                    .filter(item -> item.id().equals(analysis.selectedCandidateId())).findFirst().orElseThrow();
            UpdateSiteRecoveryPackage recoveryPackage = new UpdateSiteRecoveryPackage("2.0", analysis.analysisId(),
                    saved.id(), Instant.now(), analysis.repository().sourceFingerprint(), candidate,
                    analysis.provenance(), analysis.recoveredLayers(), analysis.diagnostics(), analysis.dashboard(),
                    analysis.logs(), analysis.fileOutcomes(), analysis.grammarInspections(), analysis.reconciliation(),
                    analysis.releaseComparison(), analysis.semanticRecovery());
            packagePath = packageStore.save(recoveryPackage);
        }
        return new UpdateSiteImportResult(analysis.analysisId(), saved, packagePath, analysis.recoveredLayers());
    }

    /** Descripción: Ejecuta la operación «result» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: repository: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; candidateForId: identificador que delimita el recurso objetivo; summary: dato de entrada necesario para la operación; candidates: dato de entrada necesario para la operación; selectedCandidateId: identificador que delimita el recurso objetivo; preview: dato de entrada necesario para la operación; provenance: dato de entrada necesario para la operación; layers: dato de entrada necesario para la operación; importable: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; observability: dato de entrada necesario para la operación; reconciliation: dato de entrada necesario para la operación; releases: dato de entrada necesario para la operación; semantic: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteImportAnalysis result(UpdateSiteRepositoryContent repository, UpdateSiteImportOptions options,
            String candidateForId, UpdateSiteRepositorySummary summary, List<UpdateSiteRecoveryCandidate> candidates,
            String selectedCandidateId, MetamodelImportPreview preview, List<UpdateSiteElementProvenance> provenance,
            UpdateSiteRecoveredLayers layers, boolean importable, List<String> diagnostics,
            UpdateSiteObservability observability, UpdateSiteReconciliationReport reconciliation,
            UpdateSiteReleaseComparison releases, UpdateSiteSemanticRecovery semantic) {
        return new UpdateSiteImportAnalysis(analysisId(repository, candidateForId, options), Instant.now(), summary, candidates, selectedCandidateId, preview, provenance, layers, importable, diagnostics, observability.dashboard(), observability.logs(), observability.fileOutcomes(), observability.grammarInspections(), reconciliation, releases, semantic, UpdateSiteAnalysisPerformance.empty());
    }

    /** Descripción: Ejecuta la operación «cacheMetrics» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAnalysisCacheMetrics cacheMetrics() { return analysisCache.metrics(); }

    /** Descripción: Elimina o libera el recurso gestionado por «clearAnalysisCache» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void clearAnalysisCache() { analysisCache.clear(); }

    /** Descripción: Ejecuta la operación «cache» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: cacheKey: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteImportAnalysis cache(String cacheKey, UpdateSiteImportAnalysis analysis) {
        analysisCache.put(cacheKey, analysis);
        return analysis;
    }

    /** Descripción: Ejecuta la operación «withPerformance» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: source: dato de entrada necesario para la operación; cacheHit: dato de entrada necesario para la operación; started: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación; stageMillis: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteImportAnalysis withPerformance(UpdateSiteImportAnalysis source, boolean cacheHit,
            long started, UpdateSiteRepositoryContent repository, Map<String, Long> stageMillis) {
        long elapsed = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
        UpdateSiteAnalysisPerformance performance = new UpdateSiteAnalysisPerformance(cacheHit, elapsed,
                repository.totalBytes(), repository.files().size(), analysisCache.metrics().entries(), stageMillis);
        return new UpdateSiteImportAnalysis(source.analysisId(), Instant.now(), source.repository(), source.candidates(), source.selectedCandidateId(), source.metamodelPreview(), source.provenance(), source.recoveredLayers(), source.importable(), source.diagnostics(), source.dashboard(), source.logs(), source.fileOutcomes(), source.grammarInspections(), source.reconciliation(), source.releaseComparison(), source.semanticRecovery(), performance);
    }

    /** Descripción: Ejecuta la operación «stage» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: stages: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; started: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void stage(Map<String, Long> stages, String id, long started) {
        stages.put(id, Math.max(0, (System.nanoTime() - started) / 1_000_000L));
    }

    /** Descripción: Ejecuta la operación «previewNeutral» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: metamodel: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportPreview previewNeutral(MetaModel metamodel, UpdateSiteImportOptions options) {
        byte[] content = serialize(metamodel);
        return metamodelImportService.preview(metamodel.id() + ".metamodel.json", "application/json", content,
                new MetamodelImportOptions("json", options.targetId().isBlank() ? metamodel.id() : options.targetId(),
                        false, true, true, false));
    }

    /** Descripción: Ejecuta la operación «serialize» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] serialize(MetaModel metamodel) {
        try {
            return mapper.writeValueAsBytes(metamodel);
        } catch(JacksonException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not serialize recovered neutral metamodel", exception);
        }
    }

    /** Descripción: Ejecuta la operación «selectCandidate» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: candidates: dato de entrada necesario para la operación; candidateId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteRecoveryCandidate selectCandidate(List<UpdateSiteRecoveryCandidate> candidates, String candidateId) {
        if(candidateId != null && !candidateId.isBlank()) {
            return candidates.stream().filter(item -> item.id().equals(candidateId)).findFirst().orElseThrow(() ->
                    new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown update-site recovery candidate: " + candidateId));
        }
        return candidates.stream().findFirst().orElse(null);
    }

    /** Descripción: Ejecuta la operación «analysisId» correspondiente a la responsabilidad de UpdateSiteImportApplicationService. Entradas: repository: dato de entrada necesario para la operación; candidateId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String analysisId(UpdateSiteRepositoryContent repository, String candidateId, UpdateSiteImportOptions options) {
        String material = repository.fingerprint() + ':' + candidateId + ':' + options.targetId() + ':'
                + options.strict() + ':' + options.recoverPalettes() + ':' + options.recoverRepresentations() + ':' + options.recoverMappings();
        return "p2-" + UpdateSiteHashing.sha256(material.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 20);
    }

    /** Descripción: Valida las condiciones necesarias para «ensureEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureEnabled() {
        if(!properties.isEnabled()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Update-site import is disabled by configuration");
        }
    }
}

package com.example.editor.metamodel.export.project;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.integration.InjectionPolicy;
import com.example.editor.integration.InjectionStrategy;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationArtifactSelection;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationCapability;
import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationFeature;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.compiler.TechnologyCompilationArchiveService;
import com.example.editor.integration.compiler.TechnologyCompilationEnrichmentService;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyCompilerService;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.integration.verification.TechnologyProjectValidationRequest;
import com.example.editor.integration.verification.TechnologyProjectValidationService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.export.MetamodelExportArtifact;
import com.example.editor.metamodel.validation.MetamodelValidationApplicationService;
import tools.jackson.databind.json.JsonMapper;

/** Compila workspaces completos usando el mismo SPI tecnológico e invariantes que las integraciones HYDRA. */
@Service
public class MetamodelCompleteProjectExportService {
    private final CanonicalMetamodelApplicationService metamodels; private final MetamodelValidationApplicationService validation;
    private final MetamodelProjectArtifactAssembler artifacts; private final TechnologyCompilerService compiler;
    private final TechnologyProjectValidationService projectValidation; private final TechnologyCompilationEnrichmentService enrichment;
    private final TechnologyCompilationArchiveService archives; private final JsonMapper mapper; private final MetamodelProjectExportProperties properties;
    private final MetamodelProjectGenerationSnapshotService snapshots; private final MetamodelProjectExportProviderRegistry providers;

    @Autowired
    /** Descripción: Inicializa una instancia de MetamodelCompleteProjectExportService con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; validation: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación; compiler: dato de entrada necesario para la operación; projectValidation: dato de entrada necesario para la operación; enrichment: dato de entrada necesario para la operación; archives: archivo o contenido que debe procesarse; mapper: componente de conversión utilizado durante el procesamiento; properties: configuración que gobierna el comportamiento; snapshots: dato de entrada necesario para la operación; providers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelCompleteProjectExportService(CanonicalMetamodelApplicationService metamodels, MetamodelValidationApplicationService validation,
            MetamodelProjectArtifactAssembler artifacts, TechnologyCompilerService compiler, TechnologyProjectValidationService projectValidation,
            TechnologyCompilationEnrichmentService enrichment, TechnologyCompilationArchiveService archives, JsonMapper mapper, MetamodelProjectExportProperties properties,
            MetamodelProjectGenerationSnapshotService snapshots, MetamodelProjectExportProviderRegistry providers){
        this.metamodels = metamodels; this.validation = validation; this.artifacts = artifacts; this.compiler = compiler;
        this.projectValidation = projectValidation; this.enrichment = enrichment; this.archives = archives; this.mapper = mapper; this.properties = properties; this.snapshots = snapshots; this.providers = providers;
    }

    /** Descripción: Obtiene la información correspondiente a «listFormats» dentro de MetamodelCompleteProjectExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelProjectExportDescriptor> listFormats(String metamodelId){
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        return providers.all().stream().map(provider -> provider.descriptor(metamodel)).toList();
    }

    /** Descripción: Obtiene la información correspondiente a «listArtifacts» dentro de MetamodelCompleteProjectExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelProjectArtifactCatalog listArtifacts(String metamodelId){return artifacts.catalog(metamodelId);}

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MetamodelCompleteProjectExportService. Entradas: metamodelId: identificador que delimita el recurso objetivo; kindId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportArtifact export(String metamodelId, String kindId, MetamodelProjectExportOptions options){
        MetamodelProjectExportProvider provider = providers.require(kindId); MetamodelProjectExportOptions effective = options == null ? MetamodelProjectExportOptions.defaults() : options;
        validation.requireGenerationReady(metamodelId);
        var validationStatus = validation.status(metamodelId);
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        List<CanonicalArtifactContent> canonical = artifacts.assemble(metamodelId, effective, provider.requiresEditor());
        MetamodelProjectGenerationSnapshot snapshot = snapshots.create(metamodelId, provider.id(), effective, canonical, validationStatus);
        IntegrationProfileModel profile = profile(provider, metamodel, canonical, effective.settings());
        TechnologyCompilationRequest request = new TechnologyCompilationRequest(profile, canonical, "HYDRA " + com.example.editor.product.HydraReleaseVersion.VERSION, Map.of("exportMode", "COMPLETE_METAMODEL_PROJECT", "generationSnapshot", snapshot.fingerprint()));
        TechnologyCompilationResult compiled = compiler.compile(request);
        var structural = projectValidation.validate(compiled, TechnologyProjectValidationRequest.structural());
        if(!structural.passed()){
            String failed = structural.steps().stream().filter(step -> step.status().name().equals("FAILED")).map(step -> step.description() + ": " + step.stderr()).findFirst().orElse("Generated project structure did not pass target validation.");
            throw new TechnologyCompilationException("Complete " + provider.displayName() + " generation failed structural acceptance. " + failed);
        }
        TechnologyCompilationResult withEvidence = addValidationEvidence(compiled, metamodelId, provider, structural.status().name(), snapshot);
        TechnologyCompilationResult enriched = enrichment.enrich(request, withEvidence);
        String fileName = safe(metamodel.id()) + "-" + provider.id() + "-complete.zip";
        return new MetamodelExportArtifact(fileName, "application/zip", archives.createArchive(enriched));
    }

    /** Descripción: Ejecuta la operación «addValidationEvidence» correspondiente a la responsabilidad de MetamodelCompleteProjectExportService. Entradas: result: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; provider: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyCompilationResult addValidationEvidence(TechnologyCompilationResult result, String metamodelId, MetamodelProjectExportProvider provider, String status,
            MetamodelProjectGenerationSnapshot snapshot){
        try{
            List<TechnologyGeneratedFile> files = new ArrayList<>(result.files());
            byte[] content = mapper.writeValueAsBytes(Map.of("schemaVersion", "1.1", "metamodelId", metamodelId, "projectExport", provider.id(),
                    "metamodelValidation", validation.status(metamodelId), "generatedProjectStructuralValidation", status, "generationSnapshotFingerprint", snapshot.fingerprint()));
            files.add(new TechnologyGeneratedFile("META-INF/hydra-metamodel-generation-gate.json", "application/json", content,
                    com.example.editor.integration.InjectionFileOwnership.GENERATED, true, List.of(metamodelId), Map.of("hydra:evidence", "validation-gate")));
            files.add(new TechnologyGeneratedFile("META-INF/hydra-project-generation-snapshot.json", "application/json", mapper.writeValueAsBytes(snapshot),
                    com.example.editor.integration.InjectionFileOwnership.GENERATED, true, snapshot.artifacts().stream().map(MetamodelProjectGenerationArtifactSnapshot::documentId).distinct().toList(),
                    Map.of("hydra:evidence", "generation-snapshot", "hydra:fingerprint", snapshot.fingerprint())));
            return new TechnologyCompilationResult(result.technology(), files, result.dependencies(), result.installationSteps(), result.rollbackSteps(), result.verificationSteps(), result.diagnostics(), result.metadata());
        }catch(RuntimeException exception){throw new TechnologyCompilationException("Could not record metamodel/project validation evidence", exception);}
    }

    /** Descripción: Ejecuta la operación «profile» correspondiente a la responsabilidad de MetamodelCompleteProjectExportService. Entradas: provider: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; canonical: dato de entrada necesario para la operación; overrides: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationProfileModel profile(MetamodelProjectExportProvider provider, MetaModel metamodel, List<CanonicalArtifactContent> canonical, Map<String, String> overrides){
        Map<String, String> settings = new LinkedHashMap<>(provider.defaultSettings(metamodel)); if(overrides != null){settings.putAll(overrides);}
        String projectId = safe(metamodel.id()) + "-" + provider.id(); String moduleName = safe(metamodel.id()) + "-" + provider.id();
        IntegrationTarget target = new IntegrationTarget(provider.technology(), provider.targetKind(), "", projectId, moduleName, provider.buildSystem(), List.of(), List.of(), "", settings);
        Set<IntegrationFeature> features = featuresFor(canonical);
        InjectionPolicy policy = new InjectionPolicy(injectionStrategyFor(provider), "", moduleName, true, true, true, false, false, true);
        List<IntegrationArtifactSelection> selections = canonical.stream().map(item -> new IntegrationArtifactSelection(item.kind(), item.documentId(), item.kind() == IntegrationArtifactKind.METAMODEL)).toList();
        Set<IntegrationCapability> requiredCapabilities = EnumSet.of(IntegrationCapability.PROJECT_EXPORT);
        features.stream().flatMap(feature -> feature.requiredCapabilities().stream()).forEach(requiredCapabilities::add);
        Map<String, String> annotations = Map.of(
                "hydra.completeProject", "true",
                "hydra.exportKind", provider.id(),
                "hydra.runtime.activation", "TOGGLEABLE",
                "hydra.runtime.enabledByDefault", "false");
        return new IntegrationProfileModel(null, projectId, metamodel.id(), metamodel.name() + " complete " + provider.displayName(), target,
                IntegrationDeliveryMode.GENERATED_MODULE, features, selections, List.of(), policy, requiredCapabilities, annotations);
    }

    /** Descripción: Deriva las capacidades de integración que debe declarar una exportación completa a partir de las capas canónicas realmente incluidas. Entradas: canonical: artefactos canónicos incorporados al proyecto generado. Salida: devuelve el conjunto mínimo y coherente de características runtime y de interfaz requeridas por esas capas. */ private Set<IntegrationFeature> featuresFor(List<CanonicalArtifactContent> canonical){
        Set<IntegrationArtifactKind> kinds = EnumSet.noneOf(IntegrationArtifactKind.class);
        canonical.forEach(item -> kinds.add(item.kind()));
        Set<IntegrationFeature> features = EnumSet.of(IntegrationFeature.PROJECT_EXPORT);
        if(kinds.contains(IntegrationArtifactKind.VALIDATIONS)) features.add(IntegrationFeature.VALIDATION_RUNTIME);
        if(kinds.contains(IntegrationArtifactKind.ACTIONS)) features.add(IntegrationFeature.QUICK_FIX_RUNTIME);
        if(kinds.contains(IntegrationArtifactKind.OPERATION_BEHAVIORS)) features.add(IntegrationFeature.OPERATION_RUNTIME);
        if(kinds.contains(IntegrationArtifactKind.EVENT_DEFINITIONS) || kinds.contains(IntegrationArtifactKind.EVENT_BINDINGS)){
            features.add(IntegrationFeature.EVENT_PUBLICATION);
            features.add(IntegrationFeature.EVENT_CONSUMPTION);
        }
        if(kinds.contains(IntegrationArtifactKind.EVENT_FLOWS)) features.add(IntegrationFeature.EVENT_FLOW_RUNTIME);
        if(kinds.contains(IntegrationArtifactKind.GAMIFICATION_DEFINITIONS) || kinds.contains(IntegrationArtifactKind.GAMIFICATION_BINDINGS)){
            features.add(IntegrationFeature.GAMIFICATION_RUNTIME);
            if(kinds.contains(IntegrationArtifactKind.EDITOR_DEFINITION)) features.add(IntegrationFeature.GAMIFICATION_UI);
        }
        if(kinds.contains(IntegrationArtifactKind.EDITOR_DEFINITION)){
            features.add(IntegrationFeature.EDITOR_UI_CONTRIBUTION);
            features.add(IntegrationFeature.NOTATION_CONTRIBUTION);
            features.add(IntegrationFeature.PALETTE_CONTRIBUTION);
        }
        if(kinds.contains(IntegrationArtifactKind.NOTATION)) features.add(IntegrationFeature.NOTATION_CONTRIBUTION);
        if(kinds.contains(IntegrationArtifactKind.CODE_GENERATION)) features.add(IntegrationFeature.CODE_GENERATION);
        return features;
    }

    /** Descripción: Selecciona la estrategia de inyección nativa adecuada al tipo de destino del proveedor sin acoplar el servicio a tecnologías concretas. Entradas: provider: proveedor de exportación cuyo tipo de destino determina la estrategia. Salida: devuelve la estrategia ADD_PLUGIN, ADD_PACKAGE o ADD_MODULE correspondiente. */ private InjectionStrategy injectionStrategyFor(MetamodelProjectExportProvider provider){
        if(provider.targetKind() == com.example.editor.integration.IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR
                && EnumSet.of(IntegrationBuildSystem.NPM, IntegrationBuildSystem.PNPM, IntegrationBuildSystem.YARN).contains(provider.buildSystem())){
            return InjectionStrategy.ADD_PACKAGE;
        }
        return switch(provider.targetKind()){
            case IDE_DISTRIBUTION -> InjectionStrategy.ADD_PLUGIN;
            case IDE_EXTENSION -> InjectionStrategy.ADD_PACKAGE;
            default -> InjectionStrategy.ADD_MODULE;
        };
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de MetamodelCompleteProjectExportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safe(String value){String result = value == null ? "metamodel" : value.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", ""); return result.isBlank() ? "metamodel" : result;}
}

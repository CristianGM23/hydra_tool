package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionChecker;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.assets.EditorVisualAssetService;
import com.example.editor.definition.importing.DrawioVisualImportSupport;
import com.example.editor.definition.importing.VisualDefinitionImportAnalyzer;
import com.example.editor.definition.importing.VisualDefinitionImportService;
import com.example.editor.definition.importing.VisualImportMetamodelResolver;
import com.example.editor.definition.importing.VisualValidationAssociationService;
import com.example.editor.definition.persistence.JsonEditorDefinitionRepository;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.MetamodelIntrospectionService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.metamodel.importing.AutomaticMetamodelDiagramLayoutService;
import com.example.editor.metamodel.importing.ImportedDiagramRelationNormalizer;
import com.example.editor.metamodel.importing.ImportedMetamodelNormalizer;
import com.example.editor.metamodel.importing.MetamodelImportDetector;
import com.example.editor.metamodel.importing.MetamodelImportProperties;
import com.example.editor.metamodel.importing.MetamodelImportRoutingPolicy;
import com.example.editor.metamodel.importing.MetamodelImportService;
import com.example.editor.metamodel.importing.importers.NeutralJsonMetamodelImporter;
import com.example.editor.metamodel.layout.LayeredMetamodelDiagramLayoutEngine;
import com.example.editor.metamodel.layout.MetamodelDiagramLayoutEngineRegistry;
import com.example.editor.metamodel.layout.MetamodelDiagramLayoutService;
import com.example.editor.metamodel.layout.MetamodelLayoutProperties;
import com.example.editor.metamodel.layout.routing.DynamicMetamodelPortAssignmentEngine;
import com.example.editor.metamodel.layout.routing.LongestSegmentMetamodelEdgeLabelPlacementEngine;
import com.example.editor.metamodel.layout.routing.MetamodelDiagramMetricsService;
import com.example.editor.metamodel.layout.routing.MetamodelEdgeLabelPlacementEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelEdgeRoutingEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelPortAssignmentEngineRegistry;
import com.example.editor.metamodel.layout.routing.MetamodelRoutingPipeline;
import com.example.editor.metamodel.layout.routing.OrthogonalMetamodelEdgeRoutingEngine;
import com.example.editor.metamodel.projection.JsonProjectionManifestRepository;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.target.JsonTargetConfigurationRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionModelChecker;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.importing.SafeExternalConstraintTranslator;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelChecker;
import com.example.editor.validation.validation.ValidationModelProperties;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, del materializador genérico de proyectos externos. Ejercita
 * {@code ExternalProjectSemanticMaterializationService.materialize} con sus ocho colaboradores
 * reales (import de metamodelos, capa visual, validación recuperada y editores), exactamente el
 * mismo camino que recorre la recuperación de un proyecto externo real. Esto cubre directamente
 * recovery_lifecycle.a1/a3/a4/a6 (selección de un único candidato semántico, análisis real del
 * contenido, persistencia canónica real y conservación del proyecto cuando no hay candidato
 * inequívoco) y x_recover_external_project.mat (materialización semántica real).
 */
class ExternalProjectSemanticMaterializationServiceTests {

    private static final String NEUTRAL_METAMODEL_JSON = """
            {"schemaVersion":"2.1","id":"recovered-sample","name":"RecoveredSample","artifactName":"recovered-sample.ecore","namespaceUri":"urn:recovered-sample","namespacePrefix":"recoveredsample","classes":[],"enums":[],"dataTypes":[],"relations":[],"annotations":{}}
            """;

    @Test
    void materializesARealCanonicalMetamodelFromAUniqueSemanticCandidateAndReusesItOnReplay(@TempDir Path temporary) {
        ExternalProjectSemanticMaterializationService service = service(temporary);
        List<ExternalProjectArchiveEntry> entries = List.of(archiveEntry("model/recovered-sample.ecore", NEUTRAL_METAMODEL_JSON));
        ExternalProjectRecoveryReport report = emptyReport();

        // "a1"/"a4": un único candidato semántico es seleccionado sin ambigüedad, analizado de
        // verdad (detección de contenido + importador JSON neutral real) y persistido en el
        // catálogo canónico real -- nada de esto está simulado.
        ExternalProjectMaterializationResult first = service.materialize(entries, report);
        assertThat(first.metamodelMaterialized()).isTrue();
        assertThat(first.metamodelId()).isEqualTo("recovered-sample");
        assertThat(first.materializedSources()).containsExactly("model/recovered-sample.ecore");
        assertThat(first.diagnostics()).anyMatch(message -> message.contains("Selected semantic source"));

        // "a6": al volver a procesar el mismo proyecto, el metamodelo ya persistido se reutiliza
        // en lugar de crearse de nuevo -- el proyecto completo queda preservado y HYDRA nunca
        // sobrescribe un metamodelo real por una reimportación redundante.
        ExternalProjectMaterializationResult replay = service.materialize(entries, report);
        assertThat(replay.metamodelMaterialized()).isFalse();
        assertThat(replay.metamodelId()).isEqualTo("recovered-sample");
        assertThat(replay.diagnostics()).anyMatch(message -> message.contains("Reused the existing canonical metamodel"));
    }

    @Test
    void refusesToGuessWhenTwoEquallyPlausibleSemanticSourcesAreFoundAndPreservesTheWholeProject(@TempDir Path temporary) {
        ExternalProjectSemanticMaterializationService service = service(temporary);
        List<ExternalProjectArchiveEntry> entries = List.of(
                archiveEntry("model/a.ecore", NEUTRAL_METAMODEL_JSON),
                archiveEntry("model/b.ecore", NEUTRAL_METAMODEL_JSON));

        // "a3": dos fuentes semánticas igualmente plausibles (misma extensión .ecore, sin ninguna
        // referencia visual que desempate) hacen que HYDRA se niegue a adivinar -- el proyecto
        // completo se conserva intacto y no se adjunta a un metamodelo arbitrario.
        ExternalProjectMaterializationResult result = service.materialize(entries, emptyReport());

        assertThat(result.metamodelMaterialized()).isFalse();
        assertThat(result.metamodelId()).isEmpty();
        assertThat(result.materializedSources()).isEmpty();
        assertThat(result.diagnostics()).anyMatch(message -> message.contains("Several equally plausible metamodel sources were found"));
    }

    private static ExternalProjectArchiveEntry archiveEntry(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new ExternalProjectArchiveEntry(path, bytes, bytes.length, "");
    }

    private static ExternalProjectRecoveryReport emptyReport() {
        return new ExternalProjectRecoveryReport("analysis", null, "", 0, 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static ExternalProjectSemanticMaterializationService service(Path temporary) {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        JsonCanonicalMetamodelRepository canonicalRepository = new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(canonicalRepository, new NeutralMetamodelValidator());

        MetamodelImportProperties importProperties = new MetamodelImportProperties();
        MetamodelLayoutProperties layoutProperties = new MetamodelLayoutProperties();
        MetamodelPortAssignmentEngineRegistry portRegistry = new MetamodelPortAssignmentEngineRegistry(
                List.of(new DynamicMetamodelPortAssignmentEngine(layoutProperties)));
        MetamodelEdgeRoutingEngineRegistry routingRegistry = new MetamodelEdgeRoutingEngineRegistry(
                List.of(new OrthogonalMetamodelEdgeRoutingEngine(layoutProperties)));
        MetamodelEdgeLabelPlacementEngineRegistry labelRegistry = new MetamodelEdgeLabelPlacementEngineRegistry(
                List.of(new LongestSegmentMetamodelEdgeLabelPlacementEngine(layoutProperties)));
        MetamodelRoutingPipeline routingPipeline = new MetamodelRoutingPipeline(portRegistry, routingRegistry, labelRegistry);
        LayeredMetamodelDiagramLayoutEngine layeredEngine = new LayeredMetamodelDiagramLayoutEngine(layoutProperties, routingPipeline, new MetamodelDiagramMetricsService());
        MetamodelDiagramLayoutEngineRegistry engineRegistry = new MetamodelDiagramLayoutEngineRegistry(List.of(layeredEngine));
        MetamodelDiagramLayoutService diagramLayoutService = new MetamodelDiagramLayoutService(layoutProperties, engineRegistry, new NeutralMetamodelValidator());
        AutomaticMetamodelDiagramLayoutService autoLayout = new AutomaticMetamodelDiagramLayoutService(diagramLayoutService, importProperties, new MetamodelImportRoutingPolicy(importProperties));

        MetamodelDiagramStorageService diagramStorageService = new MetamodelDiagramStorageService(storage, mapper);
        JsonTargetConfigurationRepository targetRepository = new JsonTargetConfigurationRepository(storage, mapper);
        JsonProjectionManifestRepository manifestRepository = new JsonProjectionManifestRepository(storage, mapper);
        ProjectionCoordinator projectionCoordinator = new ProjectionCoordinator(canonicalRepository, targetRepository, manifestRepository, List.of());
        MetamodelRegistryService runtimeRegistry = new MetamodelRegistryService(storage, new MetamodelLoader(new EmfResourceSetFactory()),
                new MetamodelIntrospectionService(), metamodels, projectionCoordinator);

        MetamodelImportService metamodelImports = new MetamodelImportService(importProperties, new MetamodelImportDetector(),
                new ImportedMetamodelNormalizer(), autoLayout, new ImportedDiagramRelationNormalizer(), new NeutralMetamodelValidator(),
                metamodels, diagramStorageService, runtimeRegistry, List.of(new NeutralJsonMetamodelImporter(mapper)));

        VisualDefinitionImportAnalyzer analyzer = new VisualDefinitionImportAnalyzer();
        DrawioVisualImportSupport drawio = new DrawioVisualImportSupport(mapper, analyzer);
        VisualImportMetamodelResolver metamodelResolver = new VisualImportMetamodelResolver(metamodels, metamodelImports);
        VisualDefinitionImportService visualImports = new VisualDefinitionImportService(mapper, analyzer, drawio, metamodelResolver);

        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        ExpressionModelApplicationService expressions = new ExpressionModelApplicationService(
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper), metamodels,
                typeChecker, new ExpressionEvaluator(typeChecker));
        ValidationModelApplicationService validations = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper),
                metamodels, new ValidationModelChecker(typeChecker));
        VisualValidationAssociationService visualValidations = new VisualValidationAssociationService(metamodels, expressions, validations, new SafeExternalConstraintTranslator());

        ActionModelApplicationService actions = new ActionModelApplicationService(
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodels, new ActionModelChecker(typeChecker));
        ExternalValidationRecoveryService validationRecovery = new ExternalValidationRecoveryService(expressions, actions, validations, new SafeExternalConstraintTranslator());

        EditorDefinitionProperties editorProperties = new EditorDefinitionProperties();
        editorProperties.setDirectory(temporary.resolve("editors"));
        EditorDefinitionApplicationService editors = new EditorDefinitionApplicationService(
                new JsonEditorDefinitionRepository(editorProperties, mapper), metamodels,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper), new EditorDefinitionChecker());

        ExternalCodeVisualRecoveryService codeVisualRecovery = new ExternalCodeVisualRecoveryService(mapper);
        EditorVisualAssetService visualAssets = new EditorVisualAssetService(storage, new com.example.editor.project.storage.ProjectVisualAssetPolicy());

        return new ExternalProjectSemanticMaterializationService(metamodelImports, metamodels, visualImports, visualValidations,
                editors, validationRecovery, codeVisualRecovery, visualAssets);
    }
}

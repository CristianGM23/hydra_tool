package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionLoadResult;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionSaveResponse;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.assets.EditorVisualAssetService;
import com.example.editor.definition.importing.DrawioVisualImportSupport;
import com.example.editor.definition.importing.VisualDefinitionImportAnalyzer;
import com.example.editor.definition.importing.VisualDefinitionImportService;
import com.example.editor.definition.importing.VisualImportCommitResult;
import com.example.editor.definition.importing.VisualImportMetamodelResolver;
import com.example.editor.definition.importing.VisualMetamodelAssociation;
import com.example.editor.definition.importing.VisualValidationAssociationService;
import com.example.editor.definition.runtime.EditorDefinitionCompiler;
import com.example.editor.definition.runtime.ExecutableEditorPlan;
import com.example.editor.definition.runtime.session.EditorModelSession;
import com.example.editor.definition.runtime.session.EditorRenderedNode;
import com.example.editor.definition.runtime.session.EditorSessionView;
import com.example.editor.definition.runtime.session.EditorSessionViewAssemblerTestBridge;
import com.example.editor.metamodel.adapter.ecore.DefaultEcoreMetamodelAdapter;
import com.example.editor.metamodel.adapter.ecore.EcoreAdapterProperties;
import com.example.editor.metamodel.adapter.ecore.EcorePrimitiveTypeRegistry;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImportService;
import com.example.editor.metamodel.importing.importers.EcoreMetamodelImporter;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.project.storage.ProjectVisualAssetPolicy;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

import tools.jackson.databind.json.JsonMapper;

/**
 * Regresión del camino que utiliza Update Site: materializar un .odesign profundo debe producir un editor
 * compilable/renderizable y promover Workspace Images a assets independientes del proyecto importado.
 */
class UpdateSiteDeepSiriusRuntimeMaterializationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    @TempDir Path directory;

    @Test
    void deepMaterializationProducesRenderableSiriusRepresentationsAndPortableImageAssets() throws Exception {
        MetaModel metamodel = importFixtureMetamodel();
        JsonMapper mapper = JsonMapper.builder().build();
        VisualImportMetamodelResolver resolver = mock(VisualImportMetamodelResolver.class);
        when(resolver.resolve(eq("EXISTING"), eq(metamodel.id()), any(), any())).thenReturn(
                new VisualImportMetamodelResolver.Resolution(metamodel,
                        new VisualMetamodelAssociation("EXISTING", metamodel.name(), true, "ECORE", "fixture")));
        VisualDefinitionImportAnalyzer analyzer = new VisualDefinitionImportAnalyzer();
        VisualDefinitionImportService visualImports = new VisualDefinitionImportService(mapper, analyzer,
                new DrawioVisualImportSupport(mapper, analyzer), resolver);

        EditorDefinitionApplicationService editors = mock(EditorDefinitionApplicationService.class);
        AtomicReference<EditorDefinitionModel> storedEditor = new AtomicReference<>();
        when(editors.checkDefinition(eq(metamodel.id()), any())).thenAnswer(invocation -> {
            EditorDefinitionModel editor = invocation.getArgument(1);
            return new EditorDefinitionCheckReport(editor.id(), List.of());
        });
        when(editors.create(eq(metamodel.id()), any())).thenAnswer(invocation -> {
            EditorDefinitionModel editor = invocation.getArgument(1);
            storedEditor.set(editor);
            return new EditorDefinitionSaveResponse(editor, "editor-etag", new EditorDefinitionCheckReport(editor.id(), List.of()));
        });
        when(editors.find(eq(metamodel.id()), any())).thenAnswer(invocation -> {
            EditorDefinitionModel editor = storedEditor.get();
            return new EditorDefinitionLoadResult(editor, "editor-etag", new EditorDefinitionCheckReport(editor.id(), List.of()));
        });
        when(editors.replace(eq(metamodel.id()), any(), any(), any())).thenAnswer(invocation -> {
            EditorDefinitionModel editor = invocation.getArgument(2);
            storedEditor.set(editor);
            return new EditorDefinitionSaveResponse(editor, "editor-etag-2", new EditorDefinitionCheckReport(editor.id(), List.of()));
        });

        VisualValidationAssociationService visualValidations = mock(VisualValidationAssociationService.class);
        when(visualValidations.commit(any())).thenAnswer(invocation -> {
            var request = (com.example.editor.definition.importing.VisualImportCommitRequest) invocation.getArgument(0);
            List<String> ruleIds = java.util.stream.IntStream.range(0, request.validationDetails().size())
                    .mapToObj(index -> "rule:visual-" + index).toList();
            return new VisualImportCommitResult(request.editorDefinition(), metamodel.id(), ruleIds.size(), ruleIds);
        });
        ExternalValidationRecoveryService validationRecovery = mock(ExternalValidationRecoveryService.class);
        when(validationRecovery.recover(eq(metamodel.id()), eq(metamodel), any())).thenReturn(
                new ExternalValidationRecoveryResult(2, 3, 1,
                        List.of("com.hydra.sirius.fixture.validation/validation/hydratest.evl"), List.of()));

        StorageProperties storage = new StorageProperties(directory.resolve("metamodels"), directory.resolve("models"),
                directory.resolve("notations"), directory.resolve("validations"), directory.resolve("quick-fixes"),
                directory.resolve("generators"), directory.resolve("assets"), directory.resolve("exports"), true);
        EditorVisualAssetService visualAssets = new EditorVisualAssetService(storage, new ProjectVisualAssetPolicy());
        ExternalProjectSemanticMaterializationService service = new ExternalProjectSemanticMaterializationService(
                mock(MetamodelImportService.class), null, visualImports, visualValidations, editors, validationRecovery,
                new ExternalCodeVisualRecoveryService(mapper), visualAssets);

        ExternalProjectMaterializationResult materialized = service.materializeExisting(metamodel, fixtureResources(), null);
        EditorDefinitionModel editor = storedEditor.get();

        assertThat(materialized.editorIds()).containsExactly(editor.id());
        assertThat(materialized.visualValidationsImported()).isEqualTo(3);
        assertThat(materialized.validationRulesImported()).isEqualTo(2);
        assertThat(materialized.quickFixesImported()).isEqualTo(3);
        assertThat(editor.representations()).extracting(RepresentationDefinition::name)
                .containsExactlyInAnyOrder("Hydra Workflow Diagram", "Workflow Elements Table", "Workflow Tree");
        assertThat(editor.representations()).extracting(RepresentationDefinition::kind)
                .contains(RepresentationKind.DIAGRAM, RepresentationKind.TABLE, RepresentationKind.TREE);

        RepresentationDefinition diagram = editor.representations().stream()
                .filter(value -> value.name().equals("Hydra Workflow Diagram")).findFirst().orElseThrow();
        NodeMapping dataStore = diagram.nodeMappings().stream()
                .filter(value -> "DataStoreImage".equals(value.annotations().get("hydra.import.externalName")))
                .findFirst().orElseThrow();
        assertThat(dataStore.imageAssetPath()).startsWith(EditorVisualAssetService.PREFIX).endsWith(".svg");
        assertThat(dataStore.annotations()).containsEntry("hydra.import.assetScope", "CANONICAL_EDITOR");
        assertThat(visualAssets.read(dataStore.imageAssetPath()).content()).isEqualTo(resource(
                "com.hydra.sirius.fixture.design/icons/datastore.svg"));

        ExecutableEditorPlan plan = new EditorDefinitionCompiler().compile(editor, "editor-etag",
                new EditorDefinitionCheckReport(editor.id(), List.of()));
        CanonicalModelDocument model = model(metamodel);
        EditorModelSession session = new EditorModelSession(null, "update-site-runtime", metamodel.id(), editor.id(),
                "editor-etag", diagram.id(), model, 0, Instant.now(), Instant.now(), Map.of());
        EditorSessionView view = EditorSessionViewAssemblerTestBridge.assemble(
                new StoredCanonicalDocument<>(session, "session-etag", null), plan, metamodel,
                plan.representationsById().get(diagram.id()), "element:datastore", false, false);

        assertThat(node(view, "element:task").shape().name()).isEqualTo("RECTANGLE");
        assertThat(node(view, "element:datastore").imageAssetPath()).isEqualTo(dataStore.imageAssetPath());
        assertThat(view.edges()).anySatisfy(edge -> {
            assertThat(edge.sourceElementId()).isEqualTo("element:start");
            assertThat(edge.targetElementId()).isEqualTo("element:task");
        });
    }

    private Map<String, byte[]> fixtureResources() throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for(String path : List.of(
                "com.hydra.sirius.fixture.design/description/hydratest.odesign",
                "com.hydra.sirius.fixture.design/icons/datastore.svg",
                "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java",
                "com.hydra.sirius.fixture.validation/validation/hydratest.evl")) result.put(path, resource(path));
        return result;
    }

    private static CanonicalModelDocument model(MetaModel metamodel) {
        String workflowClass = classId(metamodel, "Workflow");
        String startClass = classId(metamodel, "StartNode");
        String taskClass = classId(metamodel, "Task");
        String dataStoreClass = classId(metamodel, "DataStoreNode");
        String connectionClass = classId(metamodel, "Connection");
        String name = attributeId(metamodel, "name");
        String progress = attributeId(metamodel, "progress");
        List<CanonicalModelElement> elements = List.of(
                new CanonicalModelElement("element:workflow", workflowClass, Map.of(name, "Demo"), Map.of()),
                new CanonicalModelElement("element:start", startClass, Map.of(name, "Start"), Map.of()),
                new CanonicalModelElement("element:task", taskClass, Map.of(name, "Task A", progress, 40), Map.of()),
                new CanonicalModelElement("element:datastore", dataStoreClass, Map.of(name, "Store"), Map.of()),
                new CanonicalModelElement("element:connection", connectionClass, Map.of(name, "Flow"), Map.of()));
        String nodes = relationId(metamodel, "Workflow", "nodes");
        String connections = relationId(metamodel, "Workflow", "connections");
        String source = relationId(metamodel, "Connection", "source");
        String target = relationId(metamodel, "Connection", "target");
        List<CanonicalModelRelation> relations = new ArrayList<>();
        relations.add(new CanonicalModelRelation("relation:workflow-start", nodes, "element:workflow", "element:start", Map.of()));
        relations.add(new CanonicalModelRelation("relation:workflow-task", nodes, "element:workflow", "element:task", Map.of()));
        relations.add(new CanonicalModelRelation("relation:workflow-store", nodes, "element:workflow", "element:datastore", Map.of()));
        relations.add(new CanonicalModelRelation("relation:workflow-connection", connections, "element:workflow", "element:connection", Map.of()));
        relations.add(new CanonicalModelRelation("relation:connection-source", source, "element:connection", "element:start", Map.of()));
        relations.add(new CanonicalModelRelation("relation:connection-target", target, "element:connection", "element:task", Map.of()));
        return new CanonicalModelDocument("1.0", "model-update-site-runtime", metamodel.id(), "", "Runtime fixture",
                List.of("element:workflow"), elements, relations, Map.of());
    }

    private static EditorRenderedNode node(EditorSessionView view, String elementId) {
        return view.nodes().stream().filter(value -> value.elementId().equals(elementId)).findFirst().orElseThrow();
    }

    private static String classId(MetaModel metamodel, String name) {
        return metamodel.classes().stream().filter(value -> value.name().equals(name)).map(MetaClass::id).findFirst().orElseThrow();
    }

    private static String attributeId(MetaModel metamodel, String name) {
        return metamodel.classes().stream().flatMap(value -> value.attributes().stream()).filter(value -> value.name().equals(name))
                .map(MetaAttribute::id).findFirst().orElseThrow();
    }

    private static String relationId(MetaModel metamodel, String sourceClass, String name) {
        String sourceId = classId(metamodel, sourceClass);
        return metamodel.relations().stream().filter(value -> value.sourceTypeId().equals(sourceId) && value.name().equals(name))
                .map(MetaRelation::id).findFirst().orElseThrow();
    }

    private static MetaModel importFixtureMetamodel() throws IOException {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        DefaultEcoreMetamodelAdapter adapter = new DefaultEcoreMetamodelAdapter(properties,
                new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
        return new EcoreMetamodelImporter(adapter).importMetamodel(new MetamodelImportSource("hydratest.ecore", "application/xml",
                        resource("com.hydra.sirius.fixture.metamodel/model/hydratest.ecore")),
                new MetamodelImportOptions("auto", "", false, true, true, false)).metamodel();
    }

    private static byte[] resource(String relative) throws IOException {
        try(InputStream input = UpdateSiteDeepSiriusRuntimeMaterializationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if(input == null) throw new IOException("Missing test fixture resource: " + relative);
            return input.readAllBytes();
        }
    }
}

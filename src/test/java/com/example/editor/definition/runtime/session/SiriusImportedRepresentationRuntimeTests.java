package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.importing.DrawioVisualImportSupport;
import com.example.editor.definition.importing.VisualDefinitionImportAnalyzer;
import com.example.editor.definition.importing.VisualDefinitionImportResult;
import com.example.editor.definition.importing.VisualDefinitionImportService;
import com.example.editor.definition.importing.VisualImportMetamodelResolver;
import com.example.editor.definition.importing.VisualMetamodelAssociation;
import com.example.editor.definition.runtime.EditorDefinitionCompiler;
import com.example.editor.definition.runtime.ExecutableEditorPlan;
import com.example.editor.metamodel.adapter.ecore.DefaultEcoreMetamodelAdapter;
import com.example.editor.metamodel.adapter.ecore.EcoreAdapterProperties;
import com.example.editor.metamodel.adapter.ecore.EcorePrimitiveTypeRegistry;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.importers.EcoreMetamodelImporter;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato end-to-end: una representación importada no es solo metadata. El plan
 * compilado debe proyectar los elementos del modelo con los mappings de la
 * representación que el usuario seleccione.
 */
class SiriusImportedRepresentationRuntimeTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";

    @Test
    void selectedImportedRepresentationDrivesRuntimeRenderingAndSemanticEdges() throws Exception {
        MetaModel metamodel = importFixtureMetamodel();
        VisualDefinitionImportResult imported = importFixtureEditor(metamodel);
        EditorDefinitionModel editor = imported.editorDefinition();
        ExecutableEditorPlan plan = new EditorDefinitionCompiler().compile(editor, "editor-etag",
                new EditorDefinitionCheckReport(editor.id(), List.of()));

        RepresentationDefinition diagram = representation(editor, "Hydra Workflow Diagram");
        RepresentationDefinition table = representation(editor, "Workflow Elements Table");
        RepresentationDefinition tree = representation(editor, "Workflow Tree");
        CanonicalModelDocument model = model(metamodel);
        EditorModelSession session = new EditorModelSession(null, "session-sirius-runtime", metamodel.id(), editor.id(),
                "editor-etag", diagram.id(), model, 0, Instant.now(), Instant.now(), Map.of());
        EditorSessionViewAssembler assembler = new EditorSessionViewAssembler(null);

        EditorSessionView diagramView = assembler.assemble(new StoredCanonicalDocument<>(session, "session-etag", null), plan,
                metamodel, plan.representationsById().get(diagram.id()), "element:task", false, false);
        assertThat(diagramView.representationKind()).isEqualTo(RepresentationKind.DIAGRAM);
        assertThat(node(diagramView, "element:task").mappingId()).isEqualTo(mapping(diagram, "TaskSquare").id());
        assertThat(node(diagramView, "element:task").shape().name()).isEqualTo("RECTANGLE");
        assertThat(node(diagramView, "element:datastore").mappingId()).isEqualTo(mapping(diagram, "DataStoreImage").id());
        assertThat(node(diagramView, "element:datastore").imageAssetPath()).endsWith("icons/datastore.svg");
        assertThat(node(diagramView, "element:task").availableAppearances()).extracting(EditorNodeAppearanceView::mappingId)
                .doesNotContain(mapping(diagram, "DisabledTaskMarker").id());
        assertThat(diagramView.edges()).anySatisfy(edge -> {
            assertThat(edge.relationInstanceId()).isEqualTo("element:connection");
            assertThat(edge.sourceElementId()).isEqualTo("element:start");
            assertThat(edge.targetElementId()).isEqualTo("element:task");
        });

        EditorModelSession tableSession = session.openRepresentation(table.id(), 3);
        EditorSessionView tableView = assembler.assemble(new StoredCanonicalDocument<>(tableSession, "session-etag-2", null), plan,
                metamodel, plan.representationsById().get(table.id()), "element:task", false, false);
        assertThat(tableView.representationKind()).isEqualTo(RepresentationKind.TABLE);
        assertThat(node(tableView, "element:task").mappingId()).isEqualTo(mapping(table, "NodeLine").id());
        assertThat(tableView.edges()).isEmpty();

        EditorModelSession treeSession = tableSession.openRepresentation(tree.id(), 3);
        EditorSessionView treeView = assembler.assemble(new StoredCanonicalDocument<>(treeSession, "session-etag-3", null), plan,
                metamodel, plan.representationsById().get(tree.id()), "element:task", false, false);
        assertThat(treeView.representationKind()).isEqualTo(RepresentationKind.TREE);
        assertThat(node(treeView, "element:task").mappingId()).isEqualTo(mapping(tree, "Nodes").id());
        assertThat(treeView.representations()).filteredOn(EditorRepresentationRuntimeView::active)
                .extracting(EditorRepresentationRuntimeView::id).containsExactly(tree.id());
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
        return new CanonicalModelDocument("1.0", "model-sirius-runtime", metamodel.id(), "", "Runtime fixture",
                List.of("element:workflow"), elements, relations, Map.of());
    }

    private static VisualDefinitionImportResult importFixtureEditor(MetaModel metamodel) throws Exception {
        VisualImportMetamodelResolver resolver = mock(VisualImportMetamodelResolver.class);
        when(resolver.resolve(eq("EXISTING"), eq(metamodel.id()), anyList(), anyList())).thenReturn(
                new VisualImportMetamodelResolver.Resolution(metamodel,
                        new VisualMetamodelAssociation("EXISTING", metamodel.name(), true, "ECORE", "fixture")));
        VisualDefinitionImportAnalyzer analyzer = new VisualDefinitionImportAnalyzer();
        JsonMapper mapper = JsonMapper.builder().build();
        VisualDefinitionImportService service = new VisualDefinitionImportService(
                mapper, analyzer, new DrawioVisualImportSupport(mapper, analyzer), resolver);
        return service.importSirius(metamodel.id(), file("odesign",
                        "com.hydra.sirius.fixture.design/description/hydratest.odesign", "application/xml"),
                List.of(file("javaSources", "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java",
                        "text/x-java-source")), "EXISTING", List.of());
    }

    private static MetaModel importFixtureMetamodel() throws IOException {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        DefaultEcoreMetamodelAdapter adapter = new DefaultEcoreMetamodelAdapter(properties,
                new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
        EcoreMetamodelImporter importer = new EcoreMetamodelImporter(adapter);
        return importer.importMetamodel(new MetamodelImportSource("hydratest.ecore", "application/xml",
                        resource("com.hydra.sirius.fixture.metamodel/model/hydratest.ecore")),
                new MetamodelImportOptions("auto", "", false, true, true, false)).metamodel();
    }

    private static MockMultipartFile file(String field, String relative, String contentType) throws IOException {
        byte[] bytes = resource(relative);
        return new MockMultipartFile(field, relative.substring(relative.lastIndexOf('/') + 1), contentType, bytes);
    }
    private static byte[] resource(String relative) throws IOException {
        try(InputStream input = SiriusImportedRepresentationRuntimeTests.class.getResourceAsStream(FIXTURE + relative)) {
            if(input == null) throw new IOException("Missing test fixture resource: " + relative);
            return input.readAllBytes();
        }
    }
    private static RepresentationDefinition representation(EditorDefinitionModel editor, String name) {
        return editor.representations().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }
    private static NodeMapping mapping(RepresentationDefinition representation, String externalName) {
        return representation.nodeMappings().stream().filter(value -> externalName.equals(value.annotations().get("hydra.import.externalName"))).findFirst().orElseThrow();
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
}

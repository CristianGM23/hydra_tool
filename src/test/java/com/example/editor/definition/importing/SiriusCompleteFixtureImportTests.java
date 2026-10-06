package com.example.editor.definition.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.example.editor.definition.EdgeCreationTool;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorValidationTrigger;
import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.metamodel.adapter.ecore.DefaultEcoreMetamodelAdapter;
import com.example.editor.metamodel.adapter.ecore.EcoreAdapterProperties;
import com.example.editor.metamodel.adapter.ecore.EcorePrimitiveTypeRegistry;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.importers.EcoreMetamodelImporter;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import tools.jackson.databind.json.JsonMapper;

/**
 * Regresión del fixture completo de Sirius aportado para HYDRA. La prueba usa el
 * .ecore y el .odesign reales y verifica la recuperación estática, sin ejecutar
 * Java/AQL del proyecto importado.
 */
class SiriusCompleteFixtureImportTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";

    @Test
    void recoversAllSiriusRepresentationsStylesSemanticEdgeAndNativeValidations() throws Exception {
        MetaModel metamodel = importFixtureMetamodel();
        VisualImportMetamodelResolver resolver = mock(VisualImportMetamodelResolver.class);
        when(resolver.resolve(eq("EXISTING"), eq(metamodel.id()), anyList(), anyList())).thenReturn(
                new VisualImportMetamodelResolver.Resolution(metamodel,
                        new VisualMetamodelAssociation("EXISTING", metamodel.name(), true, "ECORE", "fixture")));

        VisualDefinitionImportAnalyzer analyzer = new VisualDefinitionImportAnalyzer();
        JsonMapper mapper = JsonMapper.builder().build();
        VisualDefinitionImportService service = new VisualDefinitionImportService(mapper, analyzer, new DrawioVisualImportSupport(mapper, analyzer), resolver);

        MockMultipartFile odesign = file("odesign", "com.hydra.sirius.fixture.design/description/hydratest.odesign", "application/xml");
        MockMultipartFile javaService = file("javaSources", "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java", "text/x-java-source");
        VisualDefinitionImportResult result = service.importSirius(metamodel.id(), odesign, List.of(javaService), "EXISTING", List.of());

        assertThat(result.editorDefinition().representations()).extracting(RepresentationDefinition::name)
                .contains("Hydra Workflow Diagram", "Workflow Elements Table", "Workflow Tree");
        assertThat(result.editorDefinition().representations()).extracting(RepresentationDefinition::kind)
                .contains(RepresentationKind.DIAGRAM, RepresentationKind.TABLE, RepresentationKind.TREE);

        RepresentationDefinition diagram = result.editorDefinition().representations().stream()
                .filter(value -> value.name().equals("Hydra Workflow Diagram")).findFirst().orElseThrow();
        RepresentationDefinition table = result.editorDefinition().representations().stream()
                .filter(value -> value.name().equals("Workflow Elements Table")).findFirst().orElseThrow();
        RepresentationDefinition tree = result.editorDefinition().representations().stream()
                .filter(value -> value.name().equals("Workflow Tree")).findFirst().orElseThrow();

        assertThat(diagram.nodeMappings()).extracting(value -> value.annotations().get("hydra.import.externalName"))
                .contains("GroupContainer", "StartNode", "EndNode", "TaskSquare", "Port", "DecisionDiamond", "DataStoreImage", "AnnotationNote", "TaskGauge", "DisabledTaskMarker");
        assertThat(table.nodeMappings()).extracting(value -> value.annotations().get("hydra.import.externalName")).contains("NodeLine");
        assertThat(tree.nodeMappings()).extracting(value -> value.annotations().get("hydra.import.externalName")).contains("Nodes", "Ports", "Connections");

        assertShape(diagram, "StartNode", "ELLIPSE");
        assertShape(diagram, "EndNode", "ELLIPSE");
        assertShape(diagram, "TaskSquare", "RECTANGLE");
        assertShape(diagram, "DecisionDiamond", "DIAMOND");
        assertShape(diagram, "AnnotationNote", "DOCUMENT");
        assertShape(diagram, "TaskGauge", "ROUNDED_RECTANGLE");
        NodeMapping image = node(diagram, "DataStoreImage");
        assertThat(image.imageAssetPath()).contains("com.hydra.sirius.fixture.design/icons/datastore.svg");
        assertThat(node(diagram, "TaskSquare").annotations()).containsKey("hydra.import.conditionalStyles");
        assertThat(node(diagram, "TaskGauge").annotations()).containsEntry("hydra.import.precondition", "aql:self.automated");

        assertThat(diagram.layers()).extracting(value -> value.name()).contains("Default", "Diagnostics");
        var diagnosticsLayer = diagram.layers().stream().filter(value -> value.name().equals("Diagnostics")).findFirst().orElseThrow();
        assertThat(diagnosticsLayer.activeByDefault()).isFalse();
        assertThat(diagnosticsLayer.userToggleable()).isTrue();
        assertThat(diagnosticsLayer.nodeMappingIds()).contains(node(diagram, "DisabledTaskMarker").id());
        assertThat(diagram.defaultActiveMappingIds()).doesNotContain(node(diagram, "DisabledTaskMarker").id());
        assertThat(diagram.filters()).extracting(value -> value.name()).contains("Hide completed tasks");
        var completedFilter = diagram.filters().stream().filter(value -> value.name().equals("Hide completed tasks")).findFirst().orElseThrow();
        assertThat(completedFilter.mappingIds()).contains(node(diagram, "TaskSquare").id());
        assertThat(completedFilter.externalCondition()).isEqualTo("aql:self.progress = 100");
        assertThat(completedFilter.executable()).isFalse();
        assertThat(completedFilter.annotations()).containsEntry("hydra.import.reviewRequired", "true");

        EdgeMapping edge = diagram.edgeMappings().stream()
                .filter(value -> "ControlConnection".equals(value.annotations().get("hydra.import.externalName"))).findFirst().orElseThrow();
        assertThat(edge.semanticObjectBacked()).isTrue();
        assertThat(className(metamodel, edge.semanticEdge().edgeClassId())).isEqualTo("Connection");
        assertThat(relationName(metamodel, edge.semanticEdge().sourceRelationId())).isEqualTo("source");
        assertThat(relationName(metamodel, edge.semanticEdge().targetRelationId())).isEqualTo("target");
        assertThat(relationName(metamodel, edge.semanticEdge().containmentRelationId())).isEqualTo("connections");
        assertThat(edge.semanticEdge().defaultValues().values()).contains("flow", "CONTROL");

        EdgeCreationTool edgeTool = result.editorDefinition().palette().tools().stream()
                .filter(EdgeCreationTool.class::isInstance).map(EdgeCreationTool.class::cast)
                .filter(EdgeCreationTool::semanticObjectBacked).findFirst().orElseThrow();
        assertThat(edgeTool.semanticEdge()).isEqualTo(edge.semanticEdge());

        assertThat(result.editorDefinition().capabilityManifest().supports(EditorCapability.VALIDATION_RUNTIME)).isTrue();
        assertThat(result.editorDefinition().runtimePolicy().validationTriggers()).contains(
                EditorValidationTrigger.ON_OPEN, EditorValidationTrigger.ON_CHANGE,
                EditorValidationTrigger.ON_REPRESENTATION_CHANGE, EditorValidationTrigger.MANUAL);

        assertThat(result.validationDetails()).hasSize(3);
        assertThat(result.validationDetails()).extracting(VisualImportedValidation::name)
                .containsExactlyInAnyOrder("NonEmptyName", "ProgressRange", "NoSelfLoop");
        assertThat(result.validationDetails()).allSatisfy(value -> {
            assertThat(value.expression()).isNotBlank();
            assertThat(value.technology()).isEqualTo("SIRIUS_DESKTOP");
            assertThat(value.contextClassId()).isNotBlank();
        });
        assertThat(result.artifacts()).contains("hydratest.odesign", "Services.java");
    }

    private static MetaModel importFixtureMetamodel() throws IOException {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        DefaultEcoreMetamodelAdapter adapter = new DefaultEcoreMetamodelAdapter(properties,
                new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
        EcoreMetamodelImporter importer = new EcoreMetamodelImporter(adapter);
        byte[] bytes = resource("com.hydra.sirius.fixture.metamodel/model/hydratest.ecore");
        return importer.importMetamodel(new MetamodelImportSource("hydratest.ecore", "application/xml", bytes),
                new MetamodelImportOptions("auto", "", false, true, true, false)).metamodel();
    }

    private static MockMultipartFile file(String field, String relative, String contentType) throws IOException {
        byte[] bytes = resource(relative);
        return new MockMultipartFile(field, relative.substring(relative.lastIndexOf('/') + 1), contentType, bytes);
    }

    private static byte[] resource(String relative) throws IOException {
        try(InputStream input = SiriusCompleteFixtureImportTests.class.getResourceAsStream(FIXTURE + relative)) {
            if(input == null) throw new IOException("Missing test fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private static NodeMapping node(RepresentationDefinition representation, String externalName) {
        return representation.nodeMappings().stream()
                .filter(value -> externalName.equals(value.annotations().get("hydra.import.externalName"))).findFirst().orElseThrow();
    }

    private static void assertShape(RepresentationDefinition representation, String externalName, String expected) {
        assertThat(node(representation, externalName).shape().name()).isEqualTo(expected);
    }

    private static String className(MetaModel metamodel, String classId) {
        return metamodel.classes().stream().filter(value -> value.id().equals(classId)).map(MetaClass::name).findFirst().orElse("");
    }

    private static String relationName(MetaModel metamodel, String relationId) {
        if(relationId == null || relationId.isBlank()) return "";
        return metamodel.relations().stream().filter(value -> value.id().equals(relationId)).map(MetaRelation::name).findFirst().orElse("");
    }
}

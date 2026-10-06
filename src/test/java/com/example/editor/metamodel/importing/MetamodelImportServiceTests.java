package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba registro, opciones, validación y modo estricto.
 */
class MetamodelImportServiceTests {

    @Test
    void normalizesTargetIdAndCreatesLayout() {
        MetamodelImportService service = service(new StubImporter(List.of()));
        MetamodelImportOptions options = new MetamodelImportOptions("json", "New Company", false, true, true, false);
        MetamodelImportPreview preview = service.preview("sample.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), options);
        assertThat(preview.metamodel().id()).isEqualTo("new-company");
        assertThat(preview.metamodel().schemaVersion()).isEqualTo(MetaModel.CURRENT_SCHEMA_VERSION);
        assertThat(preview.targets().ecore().fileName()).isEqualTo("new-company.ecore");
        assertThat(preview.targets().metamodelId()).isEqualTo("new-company");
        assertThat(preview.metamodel().annotations().keySet()).noneMatch(key -> key.startsWith("import:ecore:"));
        assertThat(preview.diagram().metamodelId()).isEqualTo("new-company");
        assertThat(preview.diagram().nodes()).hasSize(1);
    }

    @Test
    void listsOnlyConfiguredFormats() {
        MetamodelImportProperties properties = new MetamodelImportProperties();
        properties.setAllowedFormats(List.of("json"));
        MetamodelImportService service = service(properties, new StubImporter(List.of()));
        assertThat(service.listFormats()).singleElement().extracting(item -> item.id()).isEqualTo("json");
    }

    @Test
    void strictModeRejectsLossyWarnings() {
        MetamodelImportService service = service(new StubImporter(List.of(new TransformationWarning("IMPORT_LOSSY_TEST", "$", "Lossy conversion"))));
        MetamodelImportOptions options = new MetamodelImportOptions("json", "", false, true, true, true);
        assertThatThrownBy(() -> service.preview("sample.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), options)).isInstanceOf(MetamodelDesignException.class).hasMessageContaining("Strict metamodel import rejected semantic loss");
    }

    @Test
    void rejectsFilesAboveConfiguredLimit() {
        MetamodelImportProperties properties = new MetamodelImportProperties();
        properties.setMaxFileSizeBytes(2);
        MetamodelImportService service = service(properties, new StubImporter(List.of()));
        assertThatThrownBy(() -> service.preview("sample.json", "application/json", new byte[] { 1, 2, 3 }, new MetamodelImportOptions("json", "", false, true, true, false))).isInstanceOf(MetamodelDesignException.class).hasMessageContaining("size limit");
    }

    @Test
    void rejectsDisabledFormats() {
        MetamodelImportProperties properties = new MetamodelImportProperties();
        properties.setAllowedFormats(List.of("ecore"));
        MetamodelImportService service = service(properties, new StubImporter(List.of()));
        assertThatThrownBy(() -> service.preview("sample.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), new MetamodelImportOptions("json", "", false, true, true, false))).isInstanceOf(MetamodelDesignException.class).hasMessageContaining("disabled by configuration");
    }

    @Test
    void removesImportedOrthogonalRoutesWhenThereAreExactlyFourteenClasses() {
        MetamodelImportService service = service(new StubImporter(List.of(), 14, routedDiagram()));
        MetamodelImportPreview preview = service.preview("sample.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), new MetamodelImportOptions("json", "", false, true, true, false));

        assertThat(preview.diagram().edges()).singleElement().satisfies(edge -> {
            assertThat(edge.routePoints()).isEmpty();
            assertThat(edge.sourceHandle()).isEqualTo("bottom-35-source");
            assertThat(edge.targetHandle()).isEqualTo("top-65-target");
        });
    }

    @Test
    void preservesImportedOrthogonalRoutesWhenThereAreMoreThanFourteenClasses() {
        MetamodelImportService service = service(new StubImporter(List.of(), 15, routedDiagram()));
        MetamodelImportPreview preview = service.preview("sample.json", "application/json", "{}".getBytes(StandardCharsets.UTF_8), new MetamodelImportOptions("json", "", false, true, true, false));

        assertThat(preview.diagram().edges()).singleElement().satisfies(edge -> {
            assertThat(edge.routePoints()).hasSize(3);
            assertThat(edge.labelPosition()).isNotNull();
        });
    }

    private MetamodelImportService service(StubImporter importer) {
        return service(new MetamodelImportProperties(), importer);
    }

    private MetamodelImportService service(MetamodelImportProperties properties, StubImporter importer) {
        return new MetamodelImportService(properties, new MetamodelImportDetector(), new ImportedMetamodelNormalizer(), new AutomaticMetamodelDiagramLayoutService(null, properties, new MetamodelImportRoutingPolicy(properties)), new ImportedDiagramRelationNormalizer(), new NeutralMetamodelValidator(), null, null, null, List.of(importer));
    }

    private static MetamodelDiagramDocument routedDiagram() {
        var points = List.of(new MetamodelDiagramDocument.RoutePointDesign(10, 20), new MetamodelDiagramDocument.RoutePointDesign(80, 20), new MetamodelDiagramDocument.RoutePointDesign(80, 90));
        var edge = new MetamodelDiagramDocument.EdgeDesign("relation-0-1", "bottom-35-source", "top-65-target", "REFERENCE", "class-0", "class-1", "target", null, points, new MetamodelDiagramDocument.LabelPositionDesign(45, 20, 0));
        var nodes = List.of(new MetamodelDiagramDocument.NodeDesign("class-0", 10, 20, 200, 110), new MetamodelDiagramDocument.NodeDesign("class-1", 300, 180, 200, 110));
        return new MetamodelDiagramDocument("sample", nodes, List.of(edge));
    }

    private static class StubImporter implements MetamodelImporter {
        private final List<TransformationWarning> warnings;
        private final int classCount;
        private final MetamodelDiagramDocument diagram;

        private StubImporter(List<TransformationWarning> warnings) {
            this(warnings, 1, null);
        }

        private StubImporter(List<TransformationWarning> warnings, int classCount, MetamodelDiagramDocument diagram) {
            this.warnings = warnings;
            this.classCount = classCount;
            this.diagram = diagram;
        }

        @Override
        public MetamodelImportFormat format() {
            return MetamodelImportFormat.JSON;
        }

        @Override
        public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
            List<MetaClass> classes = IntStream.range(0, classCount)
                    .mapToObj(index -> new MetaClass("class-" + index, "Class" + index, MetaClassKind.CLASS, List.of(), List.of(), Map.of()))
                    .toList();
            List<MetaRelation> relations = classCount < 2 ? List.of() : List.of(new MetaRelation("relation-0-1", "target", "class-0", "class-1", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of()));
            MetaModel metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", classes, List.of(), List.of(), relations, Map.of());
            return new MetamodelImportOutput(metamodel, diagram, warnings);
        }
    }
}

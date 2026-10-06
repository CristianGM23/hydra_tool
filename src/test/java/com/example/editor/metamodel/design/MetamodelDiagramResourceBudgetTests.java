package com.example.editor.metamodel.design;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.config.StorageProperties;
import tools.jackson.databind.json.JsonMapper;

class MetamodelDiagramResourceBudgetTests {
    @TempDir Path temporary;

    @Test void rejectsNonFiniteOrOutOfBudgetGeometryBeforePersistence() {
        MetamodelDiagramStorageService service = service();
        MetamodelDiagramDocument invalid = new MetamodelDiagramDocument("sample", List.of(
                new MetamodelDiagramDocument.NodeDesign("node", Double.NaN, 0, 120, 80)), List.of());
        assertThatThrownBy(() -> service.save("sample", invalid))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("supported range");
    }

    @Test void rejectsOversizedRoutesBeforePersistence() {
        MetamodelDiagramStorageService service = service();
        List<MetamodelDiagramDocument.RoutePointDesign> route = java.util.stream.IntStream.range(0, 1001)
                .mapToObj(index -> new MetamodelDiagramDocument.RoutePointDesign(index, index)).toList();
        MetamodelDiagramDocument.EdgeDesign edge = new MetamodelDiagramDocument.EdgeDesign(
                "relation", "source", "target", null, null, null, "", null, route, null);
        assertThatThrownBy(() -> service.save("sample", new MetamodelDiagramDocument("sample", List.of(), List.of(edge))))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("point budget");
    }

    private MetamodelDiagramStorageService service() {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quick-fixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        return new MetamodelDiagramStorageService(storage, JsonMapper.builder().findAndAddModules().build());
    }
}

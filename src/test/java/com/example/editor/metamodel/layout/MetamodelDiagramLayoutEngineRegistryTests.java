package com.example.editor.metamodel.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba el registro extensible de algoritmos.
 */
class MetamodelDiagramLayoutEngineRegistryTests {

    @Test
    void findsRegisteredEngineIgnoringCase() {
        MetamodelDiagramLayoutEngine engine = engine("custom");
        MetamodelDiagramLayoutEngineRegistry registry = new MetamodelDiagramLayoutEngineRegistry(List.of(engine));
        assertThat(registry.require("CUSTOM")).isSameAs(engine);
    }

    @Test
    void rejectsDuplicateIds() {
        assertThatThrownBy(() -> new MetamodelDiagramLayoutEngineRegistry(List.of(engine("custom"), engine("CUSTOM")))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate metamodel layout engine registration");
    }

    @Test
    void listsAlgorithmsInStableOrder() {
        MetamodelDiagramLayoutEngineRegistry registry = new MetamodelDiagramLayoutEngineRegistry(List.of(engine("zeta"), engine("alpha")));
        assertThat(registry.ids()).containsExactly("alpha", "zeta");
    }

    private MetamodelDiagramLayoutEngine engine(String id) {
        return new MetamodelDiagramLayoutEngine() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public MetamodelLayoutResult layout(MetaModel metamodel, MetamodelDiagramDocument currentDiagram, MetamodelLayoutOptions options) {
                return null;
            }
        };
    }
}

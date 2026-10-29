package com.example.editor.definition.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.EditorImageFit;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, de que la Vista Estructura rechaza valores fuera de rango (por
 * ejemplo tamaños negativos) y de que los cambios válidos quedan realmente en la definición
 * guardada en disco. Cubre directamente las etapas "val" (¿Valores válidos?) y "art" (Definición
 * ajustada) de model_editor_author_structure, usando el componente real identificado en el
 * código -- NodeMapping, embebido en una RepresentationDefinition real dentro de un
 * EditorDefinitionModel real -- en vez de adivinar un nombre no citado por el modelo.
 */
class JsonEditorDefinitionRepositoryStructuralValidationTests {

    @Test
    void savingAnEditorDefinitionPersistsTheAdjustedNodeSizeExactlyAsEdited(@TempDir Path temporary) {
        EditorDefinitionProperties properties = new EditorDefinitionProperties();
        properties.setDirectory(temporary);
        JsonEditorDefinitionRepository repository = new JsonEditorDefinitionRepository(properties,
                JsonMapper.builder().findAndAddModules().build());

        NodeMapping node = validNode(260, 130);
        RepresentationDefinition representation = new RepresentationDefinition("representation-main", "Main view",
                RepresentationKind.DIAGRAM, List.of("class-person"), List.of(node), List.of(), List.of(), true, Map.of());
        EditorDefinitionModel definition = new EditorDefinitionModel(null, "editor-structure", "metamodel-structure",
                "Structure editor", List.of(representation), null, null, null, null, null, null);

        repository.create(definition);

        // "art": el tamaño ajustado por la persona queda realmente en la definición guardada en
        // disco, no solo en el objeto en memoria -- se relee un fichero real.
        EditorDefinitionModel reread = repository.findById("editor-structure").orElseThrow().document();
        NodeMapping rereadNode = reread.representations().get(0).nodeMappings().get(0);
        assertThat(rereadNode).isNotSameAs(node);
        assertThat(rereadNode.defaultWidth()).isEqualTo(260);
        assertThat(rereadNode.defaultHeight()).isEqualTo(130);
    }

    @Test
    void negativeOrZeroNodeSizesAreRejectedBeforeTheyCanReachTheSavedDefinition() {
        // "val": un tamaño por defecto negativo se rechaza de verdad, no se guarda silenciosamente
        // ni se recorta a un valor por defecto -- falla alto y claro antes de llegar al disco.
        assertThatThrownBy(() -> validNode(-10, 130))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default node size must be positive");
        assertThatThrownBy(() -> validNode(260, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default node size must be positive");
        // Un tamaño mínimo mayor que el tamaño por defecto tampoco es un valor válido.
        assertThatThrownBy(() -> new NodeMapping("node-person", "class-person", "Person", EditorNodeShape.ROUNDED_RECTANGLE,
                100, 100, 200, 200, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be smaller than the minimum size");
    }

    private static NodeMapping validNode(double width, double height) {
        return new NodeMapping("node-person", "class-person", "Person", EditorNodeShape.ROUNDED_RECTANGLE,
                width, height, 80, 40, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
    }
}

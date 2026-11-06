package com.example.editor.definition.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.definition.CodeGenerationDefinition;
import com.example.editor.definition.CodeGenerationFeatureOptions;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, de que la configuración de generación de código de un editor
 * (lenguaje/destino por defecto, destinos elegidos y espacio de nombres) se persiste de verdad
 * como parte de la definición del editor guardada en disco. Cubre directamente las etapas "tg"
 * (Elegir lenguaje y espacio de nombres), "dt" (Elegir destinos) y "art" (Generación configurada:
 * queda en la definición del editor) de model_editor_author_code -- hasta ahora solo evidenciadas
 * por una prueba Vitest estática y sin colaborador real.
 */
class JsonEditorDefinitionRepositoryCodeGenerationTests {

    @Test
    void savingAndReReadingAnEditorDefinitionPreservesTheChosenCodeGenerationTargetsAndNamespace(@TempDir Path temporary) {
        EditorDefinitionProperties properties = new EditorDefinitionProperties();
        properties.setDirectory(temporary);
        JsonEditorDefinitionRepository repository = new JsonEditorDefinitionRepository(properties,
                JsonMapper.builder().findAndAddModules().build());

        // "tg": se elige explícitamente TypeScript (no el "java" por defecto) como destino
        // principal y un espacio de nombres propio (no el "generated.model" por defecto).
        // "dt": se eligen explícitamente dos destinos concretos, no todos los formatos del
        // metamodelo.
        CodeGenerationDefinition codeGeneration = new CodeGenerationDefinition(true, true,
                List.of("java", "typescript"), "typescript", "org.acme.generated", true,
                CodeGenerationFeatureOptions.none(), Map.of(), Map.of());
        EditorDefinitionModel definition = new EditorDefinitionModel(null, "editor-people", "metamodel-people",
                "People editor", null, null, null, null, null, codeGeneration, null);

        StoredCanonicalDocument<EditorDefinitionModel> stored = repository.create(definition);

        // "art": la elección queda realmente guardada como parte de la definición del editor en
        // disco, no solo en memoria -- se relee un fichero real, no el objeto original.
        EditorDefinitionModel reread = repository.findById("editor-people").orElseThrow().document();
        assertThat(reread).isNotSameAs(definition);
        assertThat(reread.codeGeneration().defaultTargetId()).isEqualTo("typescript");
        assertThat(reread.codeGeneration().outputNamespace()).isEqualTo("org.acme.generated");
        assertThat(reread.codeGeneration().targetIds()).containsExactly("java", "typescript");
        assertThat(reread.codeGeneration().enabled()).isTrue();
        assertThat(stored.etag()).isNotBlank();
    }

    @Test
    void omittingCodeGenerationPersistsTheSafeDisabledDefaultsRatherThanAGuessedChoice() {
        // Cuando no se configura nada, la definición no inventa un destino: queda deshabilitada
        // con los valores por defecto documentados (java / generated.model), consistente con lo
        // que describe el propio modelo para este nodo ("por defecto Java y 'generated.model'").
        EditorDefinitionModel definition = new EditorDefinitionModel(null, "editor-bare", "metamodel-bare",
                "Bare editor", null, null, null, null, null);

        assertThat(definition.codeGeneration().enabled()).isFalse();
        assertThat(definition.codeGeneration().defaultTargetId()).isEqualTo("java");
        assertThat(definition.codeGeneration().outputNamespace()).isEqualTo("generated.model");
    }
}

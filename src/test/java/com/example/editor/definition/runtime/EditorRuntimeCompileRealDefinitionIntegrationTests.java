package com.example.editor.definition.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionApplicationException;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionChecker;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.EditorDefinitionSaveResponse;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.definition.persistence.JsonEditorDefinitionRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelSaveResponse;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de model_editor_author_runtime.cmp (compilar la definicion del editor sin modificarla): un
 * editor valido guardado con el servicio real de definiciones se compila con el servicio de ejecucion y el
 * compilador reales; repetir la compilacion reutiliza el plan; editar y guardar el editor (nueva version) fuerza
 * la recompilacion; y un editor que ya no supera la comprobacion bloqueante no se compila. El oraculo lee los
 * bytes del fichero del editor en disco antes y despues de compilar.
 */
class EditorRuntimeCompileRealDefinitionIntegrationTests {

    private CanonicalMetamodelApplicationService metamodels;
    private EditorDefinitionApplicationService definitions;
    private EditorRuntimeApplicationService runtime;
    private Path editorDirectory;

    private void wire(Path temporary) {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        EditorDefinitionProperties properties = new EditorDefinitionProperties();
        editorDirectory = temporary.resolve("editors");
        properties.setDirectory(editorDirectory);
        definitions = new EditorDefinitionApplicationService(new JsonEditorDefinitionRepository(properties, mapper), metamodels,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper), new EditorDefinitionChecker());
        runtime = new EditorRuntimeApplicationService(definitions, new EditorDefinitionCompiler());
    }

    private byte[] editorFileBytes() throws IOException {
        try (Stream<Path> files = Files.list(editorDirectory)) {
            List<Path> matching = files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().contains("people-editor")).toList();
            assertThat(matching).hasSize(1);
            return Files.readAllBytes(matching.get(0));
        }
    }

    @Test
    @DisplayName("model_editor_author_runtime.cmp: compilar un editor guardado, reutilizar el plan, recompilar tras editar y rechazar el editor con error bloqueante")
    void compilesTheSavedEditorReusesThePlanRecompilesAfterAnEditAndRejectsABlockingEditor(@TempDir Path temporary) throws IOException {
        wire(temporary);
        MetamodelSaveResponse savedMetamodel = metamodels.create(EditorDefinitionTestFixtures.metamodel());
        EditorDefinitionModel editor = EditorDefinitionTestFixtures.editor();
        EditorDefinitionSaveResponse saved = definitions.create("people", editor);
        byte[] before = editorFileBytes();

        EditorDefinitionCompilationResult first = runtime.compile("people", "people-editor", false);
        assertThat(first.cacheHit()).isFalse();
        assertThat(first.plan().sourceEtag()).isEqualTo(saved.etag());
        assertThat(runtime.cacheSize()).isEqualTo(1);
        assertThat(editorFileBytes()).isEqualTo(before);

        EditorDefinitionCompilationResult second = runtime.compile("people", "people-editor", false);
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.plan().sourceEtag()).isEqualTo(saved.etag());
        assertThat(runtime.cacheSize()).isEqualTo(1);

        EditorDefinitionModel changed = new EditorDefinitionModel("1.0", editor.id(), editor.metamodelId(), "Changed editor",
                editor.representations(), editor.palette(), editor.forms(), editor.capabilityManifest(), editor.annotations());
        EditorDefinitionSaveResponse replaced = definitions.replace("people", "people-editor", changed, saved.etag());
        assertThat(replaced.etag()).isNotEqualTo(saved.etag());

        EditorDefinitionCompilationResult recompiled = runtime.compile("people", "people-editor", false);
        assertThat(recompiled.cacheHit()).isFalse();
        assertThat(recompiled.plan().sourceEtag()).isEqualTo(replaced.etag());
        assertThat(runtime.cacheSize()).isEqualTo(1);

        // El metamodelo pierde la relacion que usa el editor: la definicion guardada deja de superar la comprobacion.
        MetaModel original = EditorDefinitionTestFixtures.metamodel();
        MetaModel reduced = new MetaModel(original.schemaVersion(), original.id(), original.name(), original.classes(), original.enums(),
                original.dataTypes(), List.of(), original.annotations());
        metamodels.replace("people", reduced, savedMetamodel.etag());
        byte[] beforeBlocked = editorFileBytes();

        assertThatThrownBy(() -> runtime.compile("people", "people-editor", true))
                .isInstanceOf(EditorDefinitionApplicationException.class).hasMessage("Editor definition contains blocking diagnostics");
        assertThat(runtime.cacheSize()).isEqualTo(1);
        assertThat(editorFileBytes()).isEqualTo(beforeBlocked);
    }
}

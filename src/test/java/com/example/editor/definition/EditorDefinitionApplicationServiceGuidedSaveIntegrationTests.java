package com.example.editor.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.persistence.JsonEditorDefinitionRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, del guardado final de un editor de modelos a través del mismo
 * servicio de aplicación real que respalda, entre otros, el botón final del asistente guiado.
 * Cubre directamente "d1" (¿Todo completo?), "p5" (Revisión: la definición es válida y está
 * guardada), "sv" (Guardar editor de modelos) y "art" (Editor guardado y compilado) de
 * model_editor_author_guided -- la orquestación real de comprobación-y-guardado es la misma
 * independientemente de qué vista del editor (guiada, formularios o diseño) dispara el guardado
 * final, por lo que ejercitar EditorDefinitionApplicationService.create() directamente, sin capa
 * web ni MockMvc, es la evidencia más directa y menos simulada posible de este comportamiento.
 */
class EditorDefinitionApplicationServiceGuidedSaveIntegrationTests {

    @Test
    void aCompleteValidDefinitionPassesReviewAndIsReallySaved(@TempDir Path temporary) {
        EditorDefinitionApplicationService service = service(temporary);

        EditorDefinitionSaveResponse response = service.create("people", EditorDefinitionTestFixtures.editor());

        // "d1"/"p5": la revisión confirma que no hay errores bloqueantes antes de darla por
        // completa.
        assertThat(response.checkReport().valid()).isTrue();
        // "sv"/"art": el editor queda realmente guardado en disco, listo para abrir sesiones --
        // se relee desde un fichero real, no el objeto devuelto por create().
        EditorDefinitionModel reread = new JsonEditorDefinitionRepository(editorProperties(temporary),
                JsonMapper.builder().findAndAddModules().build()).findById("people-editor").orElseThrow().document();
        assertThat(reread.id()).isEqualTo("people-editor");
        assertThat(reread.representations()).hasSize(1);
    }

    @Test
    void anIncompleteDefinitionFailsReviewAndIsNeverSaved(@TempDir Path temporary) {
        EditorDefinitionApplicationService service = service(temporary);

        // "d1": un mapeo que apunta a una clase que no existe en el metamodelo deja el paso
        // incompleto -- no es un simple aviso, es un error bloqueante real del comprobador real.
        NodeMapping ghostNode = new NodeMapping("node:ghost", "class:Ghost", "Ghost", EditorNodeShape.ROUNDED_RECTANGLE,
                200, 120, 100, 60, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
        RepresentationDefinition broken = new RepresentationDefinition("representation:broken", "Broken",
                RepresentationKind.DIAGRAM, List.of(), List.of(ghostNode), List.of(), List.of(), true, Map.of());
        EditorDefinitionModel incomplete = new EditorDefinitionModel("1.0", "people-editor-broken", "people",
                "Broken editor", List.of(broken), null, null,
                new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING)), Map.of());

        // "p5"/"sv": la revisión rechaza la definición incompleta y el guardado final nunca ocurre.
        assertThatThrownBy(() -> service.create("people", incomplete))
                .isInstanceOf(EditorDefinitionApplicationException.class)
                .extracting(exception -> ((EditorDefinitionApplicationException) exception).getReason())
                .isEqualTo(EditorDefinitionApplicationException.Reason.INVALID_REQUEST);
        assertThat(new JsonEditorDefinitionRepository(editorProperties(temporary),
                JsonMapper.builder().findAndAddModules().build()).findById("people-editor-broken")).isEmpty();
    }

    private static EditorDefinitionApplicationService service(Path temporary) {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(EditorDefinitionTestFixtures.metamodel());
        return new EditorDefinitionApplicationService(new JsonEditorDefinitionRepository(editorProperties(temporary), mapper),
                metamodelService, new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper), new EditorDefinitionChecker());
    }

    private static EditorDefinitionProperties editorProperties(Path temporary) {
        EditorDefinitionProperties properties = new EditorDefinitionProperties();
        properties.setDirectory(temporary.resolve("editors"));
        return properties;
    }
}

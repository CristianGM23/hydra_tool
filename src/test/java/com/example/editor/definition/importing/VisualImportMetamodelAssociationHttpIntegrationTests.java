package com.example.editor.definition.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * model_editor_visual_import.as - asociacion de una definicion visual importada con su metamodelo, por HTTP.
 *
 * Contexto Spring completo con controlador, resolutor, servicio de importacion y almacenes reales en un directorio
 * temporal; solo el transporte servlet es MockMvc. Se sube el fixture Sirius completo (.odesign, fuente Java y
 * .ecore) a /api/canonical-metamodels/{id}/editors/imports/sirius. Oraculos: clases esperadas y reglas nativas del
 * fichero de expectativas del fixture, y ficheros de los directorios temporales de metamodelos, validaciones y editores.
 * Los pasos estan ordenados: la vista previa no escribe nada, la confirmacion guarda el metamodelo y luego el modo
 * EXISTING lo encuentra ya persistido.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VisualImportMetamodelAssociationHttpIntegrationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final String ECORE = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore";
    private static final String ODESIGN = "com.hydra.sirius.fixture.design/description/hydratest.odesign";
    private static final String SERVICES = "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java";
    private static final String IMPORT_URL = "/api/canonical-metamodels/{id}/editors/imports/sirius";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mvc;

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = VisualImportMetamodelAssociationHttpIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            assertThat(input).as("fixture " + relative).isNotNull();
            return input.readAllBytes();
        }
    }

    private static String leaf(String relative) { return relative.substring(relative.lastIndexOf('/') + 1); }

    private static List<String> names(Path directory, String suffix) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).filter(name -> name.endsWith(suffix)).sorted().toList();
        }
    }

    private static void assertNothingPersisted(String why) throws IOException {
        assertThat(names(root.resolve("metamodels"), ".metamodel.json")).as(why + ": metamodels").isEmpty();
        assertThat(names(root.resolve("validations"), ".validations.json")).as(why + ": validations").isEmpty();
        assertThat(names(root.resolve("editors"), ".editor.json")).as(why + ": editors").isEmpty();
    }

    private static List<String> expectedClasses() throws IOException {
        JsonNode expectations = JSON.readTree(new String(resource("expected/hydra-import-expectations.json"), StandardCharsets.UTF_8));
        List<String> classes = new ArrayList<>();
        for (JsonNode name : expectations.get("metamodel").get("classes")) classes.add(name.asString());
        return classes;
    }

    private static List<String> classNames(JsonNode metamodel) {
        List<String> classes = new ArrayList<>();
        for (JsonNode metaClass : metamodel.get("classes")) classes.add(metaClass.get("name").asString());
        return classes;
    }

    private MockMultipartHttpServletRequestBuilder siriusUpload(String metamodelId, String mode, boolean withExternalMetamodel) throws IOException {
        MockMultipartHttpServletRequestBuilder builder = multipart(IMPORT_URL, metamodelId);
        builder.file(new MockMultipartFile("odesign", leaf(ODESIGN), "application/xml", resource(ODESIGN)));
        builder.file(new MockMultipartFile("javaSources", leaf(SERVICES), "text/x-java-source", resource(SERVICES)));
        if (withExternalMetamodel) builder.file(new MockMultipartFile("metamodelResources", "hydratest.ecore", "application/xml", resource(ECORE)));
        builder.param("associationMode", mode);
        return builder;
    }

    private JsonNode preview(String metamodelId, String mode, boolean withExternalMetamodel) throws Exception {
        MockHttpServletResponse response = mvc.perform(siriusUpload(metamodelId, mode, withExternalMetamodel)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private static String commitBody(JsonNode preview, boolean withValidationDetails) {
        return "{\"editorDefinition\":" + preview.get("editorDefinition") + ",\"associatedMetamodel\":" + preview.get("associatedMetamodel")
                + ",\"metamodelAssociation\":" + preview.get("metamodelAssociation") + ",\"validationDetails\":"
                + (withValidationDetails ? preview.get("validationDetails").toString() : "[]") + "}";
    }

    private JsonNode commit(JsonNode preview, boolean withValidationDetails) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/canonical-metamodels/{id}/editors/imports/commit-associations",
                preview.get("associatedMetamodel").get("id").asString()).contentType(MediaType.APPLICATION_JSON)
                .content(commitBody(preview, withValidationDetails))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @Order(1)
    @DisplayName("model_editor_visual_import.as: el modo EXTERNAL (.ecore subido) analiza el metamodelo asociado sin escribir nada")
    void externalModeAnalysesTheUploadedMetamodelWithoutWritingAnything() throws Exception {
        JsonNode result = preview("not-stored-yet", "EXTERNAL", true);

        JsonNode association = result.get("metamodelAssociation");
        assertThat(association.get("mode").asString()).isEqualTo("EXTERNAL");
        assertThat(association.get("alreadyPersisted").asBoolean()).isFalse();
        assertThat(association.get("detectedFormat").asString()).isEqualTo("ecore");
        JsonNode metamodel = result.get("associatedMetamodel");
        assertThat(classNames(metamodel)).containsAll(expectedClasses());
        assertThat(result.get("editorDefinition").get("metamodelId").asString()).isEqualTo(metamodel.get("id").asString());
        assertThat(result.get("validationDetails").size()).as("reglas nativas del .odesign").isEqualTo(3);
        assertNothingPersisted("la vista previa EXTERNAL");
    }

    @Test
    @Order(2)
    @DisplayName("model_editor_visual_import.as: sin metamodelo asociado o con un modo desconocido la peticion se rechaza y no se guarda nada")
    void aMissingMetamodelOrAnUnknownModeIsRejectedWithoutWriting() throws Exception {
        MockHttpServletResponse missing = mvc.perform(siriusUpload("not-stored-yet", "EXTERNAL", false)).andReturn().getResponse();
        assertThat(missing.getStatus()).as(missing.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(400);

        MockHttpServletResponse unknown = mvc.perform(siriusUpload("not-stored-yet", "SOMETHING-ELSE", true)).andReturn().getResponse();
        assertThat(unknown.getStatus()).as(unknown.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(400);

        assertNothingPersisted("las peticiones rechazadas");
    }

    @Test
    @Order(3)
    @DisplayName("model_editor_visual_import.as: confirmar la asociacion sin validaciones guarda el metamodelo analizado y ninguna regla")
    void confirmingWithoutValidationDetailsStoresTheMetamodelOnly() throws Exception {
        JsonNode preview = preview("not-stored-yet", "EXTERNAL", true);
        String id = preview.get("associatedMetamodel").get("id").asString();

        JsonNode committed = commit(preview, false);

        assertThat(committed.get("metamodelId").asString()).isEqualTo(id);
        assertThat(committed.get("validationsImported").asInt()).isZero();
        Path metamodelFile = root.resolve("metamodels").resolve(id + ".metamodel.json");
        assertThat(metamodelFile).isRegularFile();
        JsonNode stored = JSON.readTree(Files.readString(metamodelFile));
        assertThat(stored.get("id").asString()).isEqualTo(id);
        assertThat(classNames(stored)).containsAll(expectedClasses());
        assertThat(names(root.resolve("validations"), ".validations.json")).as("sin detalles no hay documento de validaciones").isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("model_editor_visual_import.as: confirmar con las validaciones importadas guarda su documento con las reglas nativas del fixture")
    void confirmingWithValidationDetailsStoresTheNativeRules() throws Exception {
        JsonNode preview = preview("not-stored-yet", "EXTERNAL", true);
        String id = preview.get("associatedMetamodel").get("id").asString();

        JsonNode committed = commit(preview, true);

        assertThat(committed.get("validationsImported").asInt()).isEqualTo(3);
        Path validationFile = root.resolve("validations").resolve(id + ".validations.json");
        assertThat(validationFile).isRegularFile();
        JsonNode document = JSON.readTree(Files.readString(validationFile));
        List<String> ruleNames = new ArrayList<>();
        for (JsonNode rule : document.get("rules")) ruleNames.add(rule.get("name").asString());
        assertThat(ruleNames).contains("NonEmptyName", "ProgressRange", "NoSelfLoop");
        assertThat(document.get("id").asString()).doesNotContain(":");
    }

    @Test
    @Order(5)
    @DisplayName("model_editor_visual_import.as: el modo EXISTING usa el metamodelo ya guardado y rechaza un identificador inexistente")
    void existingModeUsesTheStoredMetamodelAndRejectsAnUnknownId() throws Exception {
        String id = names(root.resolve("metamodels"), ".metamodel.json").get(0).replace(".metamodel.json", "");

        JsonNode result = preview(id, "EXISTING", false);
        JsonNode association = result.get("metamodelAssociation");
        assertThat(association.get("mode").asString()).isEqualTo("EXISTING");
        assertThat(association.get("alreadyPersisted").asBoolean()).isTrue();
        assertThat(association.get("detectedFormat").asString()).isEqualTo("HYDRA");
        assertThat(result.get("editorDefinition").get("metamodelId").asString()).isEqualTo(id);

        MockHttpServletResponse unknown = mvc.perform(siriusUpload("does-not-exist", "EXISTING", false)).andReturn().getResponse();
        assertThat(unknown.getStatus()).as(unknown.getContentAsString(StandardCharsets.UTF_8)).isBetween(400, 499);
        assertThat(names(root.resolve("editors"), ".editor.json")).as("analizar nunca guarda editores").isEmpty();
    }
}

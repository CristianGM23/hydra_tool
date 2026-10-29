package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * x_recover_external_project.pj - el resultado de la recuperacion queda en el proyecto abierto y se puede seguir editando.
 *
 * Contexto Spring real con MockMvc y todos los almacenes en un directorio temporal. Se importa el fixture Sirius
 * completo como ZIP de proyecto externo, se cierra y se reabre el proyecto por la API, se carga el metamodelo recuperado,
 * se le anade una clase y se guarda; despues se vuelve a cerrar y abrir. Oraculos: los ficheros del proyecto y el
 * documento del metamodelo leidos del disco, y las clases esperadas del fichero de expectativas del fixture. No se
 * certifica la etapa opcional de integracion (el perfil), que la importacion solo sugiere en el informe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalProjectRecoveryReopenIntegrationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final List<String> FILES = List.of(
            "com.hydra.sirius.fixture.design/.project", "com.hydra.sirius.fixture.design/build.properties",
            "com.hydra.sirius.fixture.design/description/hydratest.odesign", "com.hydra.sirius.fixture.design/icons/datastore.svg",
            "com.hydra.sirius.fixture.design/META-INF/MANIFEST.MF",
            "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java",
            "com.hydra.sirius.fixture.metamodel/.project", "com.hydra.sirius.fixture.metamodel/build.properties",
            "com.hydra.sirius.fixture.metamodel/META-INF/MANIFEST.MF", "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore",
            "com.hydra.sirius.fixture.metamodel/model/hydratest.genmodel",
            "com.hydra.sirius.fixture.sample/.project", "com.hydra.sirius.fixture.sample/representations.aird",
            "com.hydra.sirius.fixture.sample/model/invalid.hydraflow", "com.hydra.sirius.fixture.sample/model/valid.hydraflow",
            "com.hydra.sirius.fixture.validation/.project", "com.hydra.sirius.fixture.validation/build.properties",
            "com.hydra.sirius.fixture.validation/plugin.xml", "com.hydra.sirius.fixture.validation/META-INF/MANIFEST.MF",
            "com.hydra.sirius.fixture.validation/validation/hydratest.evl");
    private static final String NEW_CLASS = "ReopenAddedClass";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mvc;

    // Estado compartido entre los pasos ordenados.
    private static String projectDirectory = "";
    private static String projectId = "";
    private static String metamodelId = "";

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = ExternalProjectRecoveryReopenIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if (input == null) throw new IOException("Missing fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private static byte[] zip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String relative : FILES) {
                zip.putNextEntry(new ZipEntry(relative));
                zip.write(resource(relative));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String text(JsonNode node) { return node == null || node.isNull() ? "" : node.asString(); }

    private static String body(MockHttpServletResponse response) throws Exception { return response.getContentAsString(StandardCharsets.UTF_8); }

    private static List<String> expectedClasses() throws IOException {
        JsonNode expectations = JSON.readTree(new String(resource("expected/hydra-import-expectations.json"), StandardCharsets.UTF_8));
        List<String> classes = new ArrayList<>();
        for (JsonNode name : expectations.get("metamodel").get("classes")) classes.add(text(name));
        return classes;
    }

    private static List<String> classNames(JsonNode metamodel) {
        List<String> classes = new ArrayList<>();
        for (JsonNode metaClass : metamodel.get("classes")) classes.add(text(metaClass.get("name")));
        return classes;
    }

    private JsonNode session() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/projects/session")).andReturn().getResponse();
        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        return JSON.readTree(body(response));
    }

    private void closeProject() throws Exception {
        assertThat(mvc.perform(post("/api/projects/close")).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(session().get("active").asBoolean()).as("tras cerrar no hay proyecto activo").isFalse();
    }

    private JsonNode reopenProject() throws Exception {
        String request = "{\"projectDirectory\":" + JSON.writeValueAsString(projectDirectory) + ",\"loadIntoCurrentWorkspace\":false}";
        MockHttpServletResponse response = mvc.perform(post("/api/projects/open").contentType(MediaType.APPLICATION_JSON).content(request))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        JsonNode reopened = JSON.readTree(body(response));
        assertThat(reopened.get("active").asBoolean()).isTrue();
        assertThat(Files.isSameFile(Path.of(text(reopened.get("directory"))), Path.of(projectDirectory))).isTrue();
        assertThat(text(reopened.get("project").get("id"))).as("el mismo proyecto").isEqualTo(projectId);
        return reopened;
    }

    private Path metamodelFile() throws IOException {
        try (var walk = Files.walk(root.resolve("metamodels"))) {
            List<Path> found = walk.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals(metamodelId + ".metamodel.json")).toList();
            assertThat(found).hasSize(1);
            return found.get(0);
        }
    }

    @Test
    @Order(1)
    @DisplayName("x_recover_external_project.pj: tras importar, cerrar y reabrir el proyecto por la API, el resultado de la recuperacion sigue en el proyecto")
    void theRecoveryResultSurvivesClosingAndReopeningTheProject() throws Exception {
        Path parent = Files.createDirectories(root.resolve("ws"));
        byte[] archive = zip();
        String fingerprint = JSON.readTree(body(mvc.perform(multipart("/api/external-project-recovery/analyze")
                .file(new MockMultipartFile("file", "sirius-fixture.zip", "application/zip", archive))).andReturn().getResponse())).get("fingerprint").asString();
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/import")
                .file(new MockMultipartFile("file", "sirius-fixture.zip", "application/zip", archive))
                .param("parentDirectory", parent.toString()).param("projectName", "Sirius Reopen")
                .param("confirmedFingerprint", fingerprint)).andReturn().getResponse();
        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        JsonNode imported = JSON.readTree(body(response));
        projectDirectory = text(imported.get("session").get("directory"));
        projectId = text(imported.get("session").get("project").get("id"));
        metamodelId = text(imported.get("materialization").get("metamodelId"));
        assertThat(imported.get("materialization").get("metamodelMaterialized").asBoolean()).isTrue();
        assertThat(metamodelId).isNotBlank();
        assertThat(projectId).isNotBlank();

        closeProject();
        reopenProject();

        Path project = Path.of(projectDirectory);
        assertThat(project.resolve("recovery/external-project-materialization.json")).isNotEmptyFile();
        assertThat(project.resolve("external-source/com.hydra.sirius.fixture.metamodel/model/hydratest.ecore")).isRegularFile();
        MockHttpServletResponse tree = mvc.perform(get("/api/projects/active/tree")).andReturn().getResponse();
        assertThat(tree.getStatus()).as(body(tree)).isEqualTo(200);
        assertThat(body(tree)).as("el arbol del proyecto reabierto no esta vacio").isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("x_recover_external_project.pj: el metamodelo recuperado se carga tras reabrir, admite una clase nueva y un guardado con version obsoleta se rechaza")
    void theRecoveredMetamodelCanBeEditedAndSavedAfterReopening() throws Exception {
        assertThat(metamodelId).as("el paso anterior importo el proyecto").isNotBlank();
        MockHttpServletResponse loaded = mvc.perform(get("/api/canonical-metamodels/{id}", metamodelId)).andReturn().getResponse();
        assertThat(loaded.getStatus()).as(body(loaded)).isEqualTo(200);
        String firstEtag = loaded.getHeader("ETag");
        assertThat(firstEtag).isNotBlank();
        ObjectNode metamodel = (ObjectNode) JSON.readTree(body(loaded));
        assertThat(classNames(metamodel)).containsAll(expectedClasses()).doesNotContain(NEW_CLASS);

        // Se anade una clase copiando una existente sin atributos propios (StartNode) con otro nombre e identificador.
        ArrayNode classes = (ArrayNode) metamodel.get("classes");
        ObjectNode template = null;
        for (JsonNode metaClass : classes) if ("StartNode".equals(text(metaClass.get("name")))) template = (ObjectNode) metaClass;
        assertThat(template).isNotNull();
        String templateId = text(template.get("id"));
        assertThat(templateId).contains("StartNode");
        ObjectNode added = template.deepCopy();
        added.put("name", NEW_CLASS);
        added.put("id", templateId.replace("StartNode", NEW_CLASS));
        classes.add(added);

        MockHttpServletResponse saved = mvc.perform(put("/api/canonical-metamodels/{id}", metamodelId)
                .contentType(MediaType.APPLICATION_JSON).header("If-Match", firstEtag).content(metamodel.toString())).andReturn().getResponse();
        assertThat(saved.getStatus()).as(body(saved)).isEqualTo(200);
        assertThat(saved.getHeader("ETag")).isNotEqualTo(firstEtag);

        // Oraculo: el documento en disco tiene la clase nueva y conserva las doce clases originales.
        JsonNode onDisk = JSON.readTree(Files.readString(metamodelFile()));
        assertThat(classNames(onDisk)).contains(NEW_CLASS).containsAll(expectedClasses());

        // Negativo: guardar de nuevo con la version anterior es un conflicto y deja el documento intacto.
        byte[] before = Files.readAllBytes(metamodelFile());
        MockHttpServletResponse stale = mvc.perform(put("/api/canonical-metamodels/{id}", metamodelId)
                .contentType(MediaType.APPLICATION_JSON).header("If-Match", firstEtag).content(metamodel.toString())).andReturn().getResponse();
        assertThat(stale.getStatus()).as(body(stale)).isBetween(400, 499);
        assertThat(Files.readAllBytes(metamodelFile())).isEqualTo(before);
    }

    @Test
    @Order(3)
    @DisplayName("x_recover_external_project.pj: tras otro cierre y apertura del proyecto el metamodelo editado conserva la clase anadida")
    void theEditedMetamodelSurvivesAnotherCloseAndOpen() throws Exception {
        assertThat(metamodelId).isNotBlank();
        closeProject();
        reopenProject();

        MockHttpServletResponse loaded = mvc.perform(get("/api/canonical-metamodels/{id}", metamodelId)).andReturn().getResponse();
        assertThat(loaded.getStatus()).as(body(loaded)).isEqualTo(200);
        assertThat(classNames(JSON.readTree(body(loaded)))).contains(NEW_CLASS).containsAll(expectedClasses());
    }
}

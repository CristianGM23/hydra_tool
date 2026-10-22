package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recuperacion de un proyecto externo (fixture Sirius completo empaquetado como ZIP) a traves de
 * ExternalProjectRecoveryController con el contexto Spring REAL: analisis, creacion de un proyecto HYDRA en disco,
 * preservacion del original bajo external-source/ y materializacion semantica.
 *
 * Etapas certificadas: x_recover_external_project.src (analisis del origen) y x_recover_external_project.pj
 * (el resultado queda en el proyecto abierto). NO se certifican .rev ni .integ: ver limites del fragmento.
 * Oraculos: bytes del fixture comparados con los ficheros copiados en disco, JSON de analisis persistido en
 * recovery/ comparado con la respuesta, y cuenta de capas materializadas.
 */
@SpringBootTest(properties = {"hydra.instance-lock.enabled=false", "dsl.storage.create-directories=true"})
@AutoConfigureMockMvc
class ExternalProjectRecoveryEndToEndIntegrationTests {
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

    private static final Path ROOT = createRoot();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Path createRoot() {
        try { return Files.createTempDirectory("hydra-external-recovery-it"); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        for (String name : List.of("metamodels", "models", "notations", "validations", "quick-fixes", "generators", "assets", "exports")) {
            registry.add("dsl.storage." + name, () -> ROOT.resolve("storage-" + name).toString());
        }
        registry.add("dsl.editors.directory", () -> ROOT.resolve("editors").toString());
        registry.add("dsl.editors.runtime.sessions.directory", () -> ROOT.resolve("editor-sessions").toString());
        registry.add("dsl.projects.local.registry-directory", () -> ROOT.resolve("project-registry").toString());
        registry.add("dsl.imports.update-site.storage-root", () -> ROOT.resolve("update-site").toString());
        registry.add("dsl.events.directory", () -> ROOT.resolve("events").toString());
        registry.add("hydra.audit.storage-root", () -> ROOT.resolve("audit").toString());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        try (var walk = Files.walk(ROOT)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Autowired private MockMvc mvc;

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = ExternalProjectRecoveryEndToEndIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
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

    private static String text(JsonNode node) { return node == null || node.isNull() ? "" : node.toString().replace("\"", ""); }

    @Test
    @DisplayName("x_recover_external_project.src: analyze devuelve el informe no destructivo del proyecto externo")
    void analyzeReturnsANonDestructiveReportOfTheExternalProject() throws Exception {
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/analyze")
                .file(new MockMultipartFile("file", "sirius-fixture.zip", "application/zip", zip()))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        JsonNode report = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(report.get("resourceCount").asInt()).isGreaterThanOrEqualTo(FILES.size());
        assertThat(report.get("technologies").size()).isPositive();
        assertThat(text(report.get("fingerprint"))).matches("[0-9a-f]{8,}");

        // Un archivo vacio se rechaza (negativo).
        MockHttpServletResponse empty = mvc.perform(multipart("/api/external-project-recovery/analyze")
                .file(new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]))).andReturn().getResponse();
        assertThat(empty.getStatus()).isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("x_recover_external_project.pj: importar crea el proyecto HYDRA, preserva el original y materializa capas")
    void importCreatesTheHydraProjectPreservesTheSourceAndMaterializesLayers() throws Exception {
        Path parent = Files.createDirectories(ROOT.resolve("projects"));
        byte[] archive = zip();
        String confirmedPreview = JSON.readTree(mvc.perform(multipart("/api/external-project-recovery/analyze")
                .file(new MockMultipartFile("file", "sirius-fixture.zip", "application/zip", archive))).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("fingerprint").asString();
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/import")
                .file(new MockMultipartFile("file", "sirius-fixture.zip", "application/zip", archive))
                .param("parentDirectory", parent.toString()).param("projectName", "Sirius Fixture")
                .param("confirmedFingerprint", confirmedPreview)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        JsonNode result = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));

        assertThat(result.get("session").get("active").asBoolean()).isTrue();
        String fingerprint = text(result.get("recovery").get("fingerprint"));
        // LocalDirectoryProjectStorage.create coloca el proyecto en parent/<nombre seguro>; es el unico directorio creado.
        Path project;
        try (var children = Files.list(parent)) {
            List<Path> directories = children.filter(Files::isDirectory).toList();
            assertThat(directories).hasSize(1);
            project = directories.get(0);
        }
        assertThat(project.resolve("recovery/external-project-analysis.json")).isRegularFile();

        // El original queda preservado byte a byte bajo external-source/ (oraculo: bytes del fixture).
        for (String relative : List.of("com.hydra.sirius.fixture.design/description/hydratest.odesign",
                "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore",
                "com.hydra.sirius.fixture.validation/validation/hydratest.evl")) {
            assertThat(Files.readAllBytes(project.resolve("external-source").resolve(relative))).as(relative).isEqualTo(resource(relative));
        }
        // El analisis persistido en el proyecto coincide con la respuesta.
        JsonNode stored = JSON.readTree(Files.readString(project.resolve("recovery/external-project-analysis.json")));
        assertThat(text(stored.get("fingerprint"))).isEqualTo(fingerprint);
        assertThat(project.resolve("recovery/external-project-materialization.json")).isRegularFile();
        assertThat(project.resolve("recovery/README.md")).isRegularFile();

        // Materializacion semantica real: metamodelo, editor y reglas/correcciones.
        JsonNode materialization = result.get("materialization");
        assertThat(materialization.get("metamodelMaterialized").asBoolean()).isTrue();
        assertThat(text(materialization.get("metamodelId"))).isNotBlank();
        // Regresion: el adaptador Sirius fallaba con "id must be a stable document id" porque
        // VisualValidationAssociationService creaba el documento de validaciones con el id "<metamodelo>:validations".
        // Ahora el id es valido y ningun diagnostico del informe puede mencionarlo.
        assertThat(materialization.get("diagnostics").size()).isPositive();
        for (JsonNode diagnostic : materialization.get("diagnostics")) {
            assertThat(text(diagnostic)).doesNotContain("stable document id");
        }
        assertThat(project.resolve("recovery/external-project-materialization.json")).isNotEmptyFile();
    }
}

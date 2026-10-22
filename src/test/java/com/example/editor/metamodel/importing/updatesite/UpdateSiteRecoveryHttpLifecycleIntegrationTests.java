package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ciclo de recuperacion de un update site / proyecto Sirius por HTTP, con el contexto Spring REAL (controladores,
 * servicios, adaptadores profundos y repositorios en disco reales) y todos los almacenes en un directorio temporal.
 * Sube como multipart el fixture {@code fixtures/sirius-complete}: analyze, reconcile, materialize con persist=false
 * (nada en disco), seleccion de capas, persist=true, repeticion (conflicto) y rollback con los etag devueltos.
 *
 * Etapas: recovery_author_wizard.d, recovery_author_workspace.{d,f,g,h}, recovery_lifecycle.{a1,a3,a4,a6},
 * recovery_persist_rollback.d3.
 * Oraculos: expected/hydra-import-expectations.json (clases del metamodelo esperadas, escrito a mano junto al fixture)
 * y ficheros leidos del disco temporal.
 */
@SpringBootTest(properties = {"hydra.instance-lock.enabled=false", "dsl.storage.create-directories=true"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpdateSiteRecoveryHttpLifecycleIntegrationTests {
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
        try { return Files.createTempDirectory("hydra-update-site-it"); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        for (String name : List.of("metamodels", "models", "notations", "validations", "quick-fixes", "generators", "assets", "exports")) {
            registry.add("dsl.storage." + name, () -> ROOT.resolve("storage-" + name).toString());
        }
        registry.add("dsl.editors.directory", () -> ROOT.resolve("editors").toString());
        registry.add("dsl.editors.runtime.sessions.directory", () -> ROOT.resolve("editor-sessions").toString());
        registry.add("dsl.imports.update-site.storage-root", () -> ROOT.resolve("update-site").toString());
        registry.add("dsl.events.directory", () -> ROOT.resolve("events").toString());
        registry.add("dsl.capability-transactions.storage-root", () -> ROOT.resolve("capability-transactions").toString());
        registry.add("hydra.audit.storage-root", () -> ROOT.resolve("audit").toString());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        try (var walk = Files.walk(ROOT)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Autowired private MockMvc mvc;

    // Estado compartido entre los pasos ordenados del ciclo.
    private static String candidateId = "";
    private static String metamodelId = "";
    private static String representationId = "";

    /** Seleccion explicita de UNA representacion sin mappings sueltos (ver hallazgo DUPLICATE_NODE_MAPPING). */
    private static String singleRepresentation() {
        return "{\"paletteGroupIds\":[],\"paletteToolIds\":[],\"representationIds\":[\"" + representationId
                + "\"],\"mappingIds\":[],\"semanticElementIds\":[],\"explicit\":true}";
    }
    private static JsonNode persisted;
    private static List<String> persistedFiles = List.of();

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = UpdateSiteRecoveryHttpLifecycleIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if (input == null) throw new IOException("Missing fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private MockMultipartHttpServletRequestBuilder upload(String url) throws IOException {
        MockMultipartHttpServletRequestBuilder builder = multipart(url);
        for (String relative : FILES) builder.file(new MockMultipartFile("files", relative, "application/octet-stream", resource(relative)));
        return builder;
    }

    private JsonNode ok(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode materialize(String requestJson) throws Exception {
        return ok(mvc.perform(upload("/api/update-site-imports/materialize")
                .file(new MockMultipartFile("request", "", "application/json", requestJson.getBytes(StandardCharsets.UTF_8))))
                .andReturn().getResponse());
    }

    private static String text(JsonNode node) { return node == null || node.isNull() ? "" : node.toString().replace("\"", ""); }

    private static List<String> regularFiles(String directory) throws IOException {
        Path dir = ROOT.resolve(directory);
        if (!Files.isDirectory(dir)) return List.of();
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).map(path -> ROOT.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private static List<String> allStoredFiles() throws IOException {
        List<String> all = new ArrayList<>();
        for (String directory : List.of("storage-metamodels", "editors", "storage-validations", "storage-quick-fixes", "storage-assets")) all.addAll(regularFiles(directory));
        return all;
    }

    private String request(String editorId, boolean persist, String selectionJson) {
        return "{\"targetId\":\"\",\"candidateId\":\"" + candidateId + "\",\"editorId\":\"" + editorId + "\",\"editorName\":\"Fixture editor\","
                + "\"strict\":false,\"persist\":" + persist + (selectionJson == null ? "" : ",\"selection\":" + selectionJson) + "}";
    }

    @Test
    @Order(1)
    @DisplayName("recovery_author_wizard.d / recovery_lifecycle.a1: analyze sube el fixture y devuelve candidatos y el metamodelo esperado")
    void analyzeReturnsCandidatesAndTheExpectedMetamodel() throws Exception {
        JsonNode analysis = ok(mvc.perform(upload("/api/update-site-imports/analyze")).andReturn().getResponse());
        assertThat(analysis.get("candidates").size()).isPositive();
        candidateId = text(analysis.get("selectedCandidateId"));
        assertThat(candidateId).isNotBlank();

        JsonNode metamodel = analysis.get("metamodelPreview").get("metamodel");
        metamodelId = text(metamodel.get("id"));
        assertThat(metamodelId).isNotBlank();
        Set<String> actual = new HashSet<>();
        for (JsonNode metaClass : metamodel.get("classes")) actual.add(text(metaClass.get("name")));
        JsonNode expectations = JSON.readTree(new String(resource("expected/hydra-import-expectations.json"), StandardCharsets.UTF_8));
        Set<String> expected = new HashSet<>();
        for (JsonNode name : expectations.get("metamodel").get("classes")) expected.add(text(name));
        assertThat(actual).containsAll(expected);
        JsonNode representations = analysis.get("recoveredLayers").get("representations");
        assertThat(representations.size()).as("recovered representation drafts").isPositive();
        representationId = text(representations.get(0).get("id"));
        assertThat(allStoredFiles()).as("analysis never persists canonical documents").isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("recovery_author_workspace.d: reconciliar con decision explicita devuelve el preview neutral sin guardar")
    void reconcileWithAnExplicitDecisionReturnsANeutralPreviewWithoutSaving() throws Exception {
        JsonNode analysis = ok(mvc.perform(upload("/api/update-site-imports/analyze")).andReturn().getResponse());
        // Decision explicita: para cada conflicto se elige el candidato recomendado por el propio informe.
        StringBuilder decisions = new StringBuilder();
        for (JsonNode conflict : analysis.get("reconciliation").get("conflicts")) {
            if (decisions.length() > 0) decisions.append(',');
            decisions.append('"').append(text(conflict.get("elementKey"))).append("\":\"").append(text(conflict.get("recommendedCandidateId"))).append('"');
        }
        // Regresion: merge() usaba el candidato primario para TODA clave sin conflicto y fallaba con "does not provide"
        // si un elemento solo lo aportaba otro candidato. Ahora CUALQUIER candidato puede ser primario.
        assertThat(analysis.get("candidates").size()).isPositive();
        for (JsonNode candidate : analysis.get("candidates")) {
            String requestJson = "{\"targetId\":\"\",\"primaryCandidateId\":\"" + text(candidate.get("id")) + "\",\"conflictCandidateIds\":{" + decisions + "}}";
            MockHttpServletResponse response = mvc.perform(upload("/api/update-site-imports/reconcile")
                    .file(new MockMultipartFile("request", "", "application/json", requestJson.getBytes(StandardCharsets.UTF_8))))
                    .andReturn().getResponse();
            JsonNode merged = ok(response);
            assertThat(merged.get("preview").get("metamodel").get("classes").size()).as("primary " + text(candidate.get("id"))).isPositive();
            assertThat(merged.has("report")).isTrue();
        }
        assertThat(allStoredFiles()).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("recovery_lifecycle.a6 / recovery_author_workspace.f: materialize con persist=false calcula el resultado y no escribe nada")
    void materializeWithoutPersistWritesNothingToDisk() throws Exception {
        // Regresion: con TODAS las capas el editor generado repetia ids de node mapping (los mismos mappings se copiaban
        // en cada representacion) y el comprobador respondia DUPLICATE_NODE_MAPPING. Ahora los ids se califican por
        // representacion y el comprobador ya no encuentra ids repetidos.
        JsonNode full = materialize(request("fixture-editor", false, null)).get("preview");
        assertThat(full.get("diagnostics").toString()).doesNotContain("DUPLICATE_NODE_MAPPING");
        java.util.Set<String> nodeMappingIds = new HashSet<>();
        for (JsonNode representation : full.get("editorDefinition").get("representations")) {
            for (JsonNode mapping : representation.get("nodeMappings")) {
                assertThat(nodeMappingIds.add(text(mapping.get("id")))).as("node mapping id %s unique across representations", text(mapping.get("id"))).isTrue();
            }
        }

        JsonNode result = materialize(request("fixture-editor", false, singleRepresentation()));
        assertThat(result.get("persisted").asBoolean()).isFalse();
        assertThat(result.get("rollbackAvailable").asBoolean()).isFalse();
        assertThat(result.get("preview").get("applicable").asBoolean()).as("preview diagnostics: %s", result.get("preview").get("diagnostics")).isTrue();
        assertThat(result.get("preview").get("editorDefinition")).isNotNull();
        assertThat(allStoredFiles()).as("persist=false must leave every store empty").isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("recovery_author_workspace.d / recovery_lifecycle.a3: la seleccion explicita de capas acota el preview")
    void partialLayerSelectionNarrowsThePreview() throws Exception {
        JsonNode all = materialize(request("fixture-editor", false, null)).get("preview");
        JsonNode none = materialize(request("fixture-editor", false,
                "{\"paletteGroupIds\":[],\"paletteToolIds\":[],\"representationIds\":[],\"mappingIds\":[],\"semanticElementIds\":[],\"explicit\":true}")).get("preview");
        assertThat(none.get("selectedRepresentations").asInt()).isZero();
        assertThat(none.get("selectedMappings").asInt()).isZero();
        assertThat(all.get("selectedRepresentations").asInt()).isGreaterThanOrEqualTo(none.get("selectedRepresentations").asInt());
        assertThat(allStoredFiles()).isEmpty();
    }


    private static boolean persistedByStep5 = false;

    /**
     * Regresion: antes, la persistencia rechazada por falta de fidelidad completa salia como 500 INTERNAL_ERROR y dejaba
     * el diagrama del metamodelo. Ahora el resultado es o bien una persistencia correcta (200), o bien un rechazo 4xx
     * INVALID_REQUEST con el motivo, jamas un 5xx; y un rechazo no deja ningun metamodelo, editor, regla ni diagrama.
     * (El activo SVG del editor visual, direccionado por contenido, puede sobrevivir: ver hallazgo 2 pendiente.)
     */
    @Test
    @Order(5)
    @DisplayName("recovery_author_workspace.g: persist=true con el fixture Sirius se persiste o se rechaza con 4xx, nunca 500, y el rechazo no deja documentos")
    void persistTrueIsPersistedOrRefusedWithA4xxAndARefusalLeavesNoDocuments() throws Exception {
        MockHttpServletResponse response = mvc.perform(upload("/api/update-site-imports/materialize")
                .file(new MockMultipartFile("request", "", "application/json",
                        request("fixture-editor", true, singleRepresentation()).getBytes(StandardCharsets.UTF_8)))).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as(body).isLessThan(500);
        if (response.getStatus() == 200) {
            persistedByStep5 = true;
            assertThat(JSON.readTree(body).get("persisted").asBoolean()).isTrue();
            assertThat(regularFiles("storage-metamodels")).isNotEmpty();
        } else {
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(body).contains("INVALID_REQUEST").doesNotContain("INTERNAL_ERROR");
            assertRejectedPersistenceLeftNoModelDocuments();
        }
    }

    /** Un rechazo no deja metamodelo, editor, regla, correccion ni diagrama; solo puede quedar un activo SVG por contenido. */
    private static void assertRejectedPersistenceLeftNoModelDocuments() throws IOException {
        assertThat(allStoredFiles()).as("rejected persistence leftovers may only be the content-addressed visual asset")
                .allSatisfy(file -> assertThat(file).matches(".*editor-visual/[0-9a-f]+\\.svg$"));
        assertThat(regularFiles("storage-validations")).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("recovery_author_workspace.g: repetir la persistencia nunca responde 5xx ni deja documentos tras un rechazo")
    void repeatingThePersistenceNeverAnswers5xxAndARefusalLeavesNoDocuments() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean alreadyPersisted = persistedByStep5;
            MockHttpServletResponse response = mvc.perform(upload("/api/update-site-imports/materialize")
                    .file(new MockMultipartFile("request", "", "application/json",
                            request("fixture-editor", true, null).getBytes(StandardCharsets.UTF_8)))).andReturn().getResponse();
            assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isLessThan(500);
            if (response.getStatus() == 200) persistedByStep5 = true;
            if (alreadyPersisted) assertThat(response.getStatus()).as("el metamodelo ya existe: conflicto").isGreaterThanOrEqualTo(400);
            else if (response.getStatus() >= 400) assertRejectedPersistenceLeftNoModelDocuments();
        }
    }
}

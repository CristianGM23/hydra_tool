package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Analisis y vista previa de la recuperacion de un update site / proyecto Sirius por HTTP REAL: Tomcat en puerto
 * aleatorio y {@link HttpClient} contra /api/update-site-imports, con adaptadores profundos y almacenes reales en un
 * directorio temporal. Sube como multipart el fixture {@code fixtures/sirius-complete}.
 *
 * Etapas: recovery_author_wizard.d (analisis con candidatos por nivel y confianza y metamodelo esperado) y
 * recovery_lifecycle.a6 (vista previa de materializacion sin escritura).
 * Oraculos: expected/hydra-import-expectations.json (clases esperadas, escrito a mano junto al fixture) y un recorrido
 * recursivo del directorio temporal buscando documentos canonicos o paquetes de recuperacion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "hydra.instance-lock.enabled=false", "dsl.storage.create-directories=true", "server.tomcat.max-part-count=100"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpdateSiteRecoveryRealHttpAnalyzePreviewIntegrationTests {
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
    private static final String BOUNDARY = "----HydraAnalyzePreviewBoundary7MA4YWxk";

    private static final Path ROOT = createRoot();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Path createRoot() {
        try { return Files.createTempDirectory("hydra-update-site-http-preview"); }
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

    @Autowired private Environment environment;
    private final HttpClient client = HttpClient.newHttpClient();

    // Estado compartido entre los pasos ordenados.
    private static String candidateId = "";
    private static int draftCount;
    private static String firstRepresentationId = "";

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = UpdateSiteRecoveryRealHttpAnalyzePreviewIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if (input == null) throw new IOException("Missing fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private String base() { return "http://localhost:" + environment.getProperty("local.server.port"); }

    private static void part(ByteArrayOutputStream out, String name, String filename, String contentType, byte[] body) {
        StringBuilder header = new StringBuilder("--").append(BOUNDARY).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append('"');
        if (filename != null) header.append("; filename=\"").append(filename).append('"');
        header.append("\r\n");
        if (contentType != null) header.append("Content-Type: ").append(contentType).append("\r\n");
        header.append("\r\n");
        out.writeBytes(header.toString().getBytes(StandardCharsets.UTF_8));
        out.writeBytes(body);
        out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /** Multipart real: todos los ficheros del fixture como parte "files" y, si se indica, la parte JSON "request". */
    private HttpResponse<String> post(String path, String requestJson) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String relative : FILES) part(out, "files", relative, "application/octet-stream", resource(relative));
        if (requestJson != null) part(out, "request", null, "application/json", requestJson.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static String text(JsonNode node) { return node == null || node.isNull() ? "" : node.asString(); }

    /** Documentos canonicos o paquetes de recuperacion escritos en cualquier almacen del directorio temporal. */
    private static List<String> persistedDocuments() throws IOException {
        try (var walk = Files.walk(ROOT)) {
            return walk.filter(Files::isRegularFile).map(path -> ROOT.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(".metamodel.json") || name.endsWith(".editor.json")
                            || name.endsWith(".validations.json") || name.endsWith(".actions.json")
                            || name.endsWith(".update-site-recovery.json"))
                    .sorted().toList();
        }
    }

    private static String request(boolean persist, String selectionJson) {
        return "{\"targetId\":\"\",\"candidateId\":\"" + candidateId + "\",\"editorId\":\"fixture-editor\",\"editorName\":\"Fixture editor\","
                + "\"strict\":false,\"persist\":" + persist + (selectionJson == null ? "" : ",\"selection\":" + selectionJson) + "}";
    }

    private JsonNode preview(String selectionJson) throws Exception {
        JsonNode result = ok(post("/api/update-site-imports/materialize", request(false, selectionJson)));
        assertThat(result.get("persisted").asBoolean()).isFalse();
        assertThat(result.get("rollbackAvailable").asBoolean()).isFalse();
        return result.get("preview");
    }

    @Test
    @Order(1)
    @DisplayName("recovery_author_wizard.d: analyze por HTTP real devuelve candidatos por nivel y confianza y el metamodelo esperado, sin guardar nada")
    void analyzeOverRealHttpReturnsRankedCandidatesAndTheExpectedMetamodel() throws Exception {
        JsonNode analysis = ok(post("/api/update-site-imports/analyze", null));

        JsonNode ecoreCandidate = null;
        for (JsonNode candidate : analysis.get("candidates")) {
            if (text(candidate.get("sourcePath")).endsWith("hydratest.ecore")) ecoreCandidate = candidate;
        }
        assertThat(ecoreCandidate).as("un candidato directo con el .ecore original").isNotNull();
        assertThat(ecoreCandidate.get("level").asInt()).isEqualTo(1);
        assertThat(text(ecoreCandidate.get("confidence"))).isEqualTo("EXACT");
        candidateId = text(analysis.get("selectedCandidateId"));
        assertThat(candidateId).isEqualTo(text(ecoreCandidate.get("id")));
        assertThat(analysis.get("importable").asBoolean()).isTrue();

        Set<String> actual = new HashSet<>();
        for (JsonNode metaClass : analysis.get("metamodelPreview").get("metamodel").get("classes")) actual.add(text(metaClass.get("name")));
        JsonNode expectations = JSON.readTree(new String(resource("expected/hydra-import-expectations.json"), StandardCharsets.UTF_8));
        Set<String> expected = new HashSet<>();
        for (JsonNode name : expectations.get("metamodel").get("classes")) expected.add(text(name));
        assertThat(actual).containsAll(expected);

        JsonNode representations = analysis.get("recoveredLayers").get("representations");
        assertThat(representations.size()).as("borradores de representacion recuperados").isPositive();
        for (JsonNode draft : representations) assertThat(text(draft.get("sourcePath"))).endsWith("hydratest.odesign");
        draftCount = representations.size();
        firstRepresentationId = text(representations.get(0).get("id"));
        assertThat(persistedDocuments()).as("analizar nunca guarda documentos").isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("recovery_lifecycle.a6: la vista previa con una representacion es aplicable y no escribe en ningun almacen")
    void previewOfOneRepresentationIsApplicableAndWritesNothing() throws Exception {
        String selection = "{\"paletteGroupIds\":[],\"paletteToolIds\":[],\"representationIds\":[\"" + firstRepresentationId
                + "\"],\"mappingIds\":[],\"semanticElementIds\":[],\"explicit\":true}";

        JsonNode preview = preview(selection);

        assertThat(preview.get("applicable").asBoolean()).as("diagnosticos: %s", preview.get("diagnostics")).isTrue();
        assertThat(preview.get("selectedRepresentations").asInt()).isEqualTo(1);
        assertThat(preview.get("editorDefinition").get("representations").size()).isEqualTo(1);
        assertThat(persistedDocuments()).as("persist=false deja todos los almacenes vacios").isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("recovery_lifecycle.a6: con todas las capas la vista previa no repite identificadores de mapping de nodo y sigue sin escribir")
    void previewOfAllLayersHasUniqueNodeMappingIdsAndWritesNothing() throws Exception {
        JsonNode preview = preview(null);

        assertThat(preview.get("diagnostics").toString()).doesNotContain("DUPLICATE_NODE_MAPPING");
        assertThat(preview.get("selectedRepresentations").asInt()).isEqualTo(draftCount);
        Set<String> nodeMappingIds = new HashSet<>();
        List<String> all = new ArrayList<>();
        for (JsonNode representation : preview.get("editorDefinition").get("representations")) {
            for (JsonNode mapping : representation.get("nodeMappings")) all.add(text(mapping.get("id")));
        }
        for (String id : all) assertThat(nodeMappingIds.add(id)).as("node mapping id %s unico en todas las representaciones", id).isTrue();
        assertThat(persistedDocuments()).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("recovery_lifecycle.a6: una seleccion explicita vacia no es aplicable y la vista previa lo dice sin escribir nada")
    void anEmptyExplicitSelectionIsNotApplicable() throws Exception {
        JsonNode preview = preview("{\"paletteGroupIds\":[],\"paletteToolIds\":[],\"representationIds\":[],\"mappingIds\":[],\"semanticElementIds\":[],\"explicit\":true}");

        assertThat(preview.get("applicable").asBoolean()).isFalse();
        assertThat(preview.get("selectedRepresentations").asInt()).isZero();
        assertThat(preview.get("diagnostics").toString()).contains("NO_REPRESENTATION");
        assertThat(persistedDocuments()).isEmpty();
    }
}

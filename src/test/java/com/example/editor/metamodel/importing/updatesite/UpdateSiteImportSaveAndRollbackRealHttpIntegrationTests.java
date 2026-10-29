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
 * Guardado y reversion de una importacion de update site SIN Sirius por HTTP REAL: Tomcat en puerto aleatorio y
 * {@link HttpClient} contra /api/update-site-imports, con controladores, servicios y almacenes canonicos reales en un
 * directorio temporal. El update site es el .ecore del fixture Sirius mas un diagrama Ecore clasico (sin .odesign),
 * de modo que la materializacion pasa por el editor generico y no por el adaptador Sirius.
 *
 * Etapas: recovery_author_wizard.h (importar y guardar el metamodelo), recovery_lifecycle.a1 (el candidato y la
 * seleccion de capas del plan llegan a lo que se guarda), recovery_persist_rollback.d1 (guardar o no guardar) y
 * recovery_persist_rollback.d3 (revertir deja los almacenes como al inicio).
 * Oraculos: documentos .metamodel.json y .editor.json leidos del disco temporal antes y despues, clases esperadas de
 * expected/hydra-import-expectations.json y bytes del documento comparados tras un segundo intento. Los pasos estan
 * ordenados porque comparten el estado del disco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "hydra.instance-lock.enabled=false", "dsl.storage.create-directories=true"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpdateSiteImportSaveAndRollbackRealHttpIntegrationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final String ECORE = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore";
    private static final String DIAGRAM = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecorediag";
    private static final byte[] DIAGRAM_BYTES = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><diagram name=\"hydratest\"/>".getBytes(StandardCharsets.UTF_8);
    private static final String BOUNDARY = "----HydraImportSaveBoundary7MA4YWxk";
    private static final String EDITOR_ID = "hydratest-editor";

    private static final Path ROOT = createRoot();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Path createRoot() {
        try { return Files.createTempDirectory("hydra-update-site-http-save"); }
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
    private static String representationId = "";
    private static String metamodelId = "";
    private static String metamodelEtag = "";
    private static String editorId = "";
    private static String editorEtag = "";

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = UpdateSiteImportSaveAndRollbackRealHttpIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
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

    /** Multipart real con el .ecore y el diagrama Ecore como partes "files" y, si se indica, la parte JSON "request". */
    private HttpResponse<String> upload(String pathAndQuery, String requestJson) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part(out, "files", ECORE, "application/octet-stream", resource(ECORE));
        part(out, "files", DIAGRAM, "application/octet-stream", DIAGRAM_BYTES);
        if (requestJson != null) part(out, "request", null, "application/json", requestJson.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + pathAndQuery))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static String text(JsonNode node) { return node == null || node.isNull() ? "" : node.asString(); }

    /** Nombres de los documentos con este sufijo en cualquier almacen del directorio temporal. */
    private static List<String> documents(String suffix) throws IOException {
        try (var walk = Files.walk(ROOT)) {
            return walk.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(suffix)).sorted().toList();
        }
    }

    private static Path documentPath(String name) throws IOException {
        try (var walk = Files.walk(ROOT)) {
            List<Path> found = walk.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals(name)).toList();
            assertThat(found).as("documento %s", name).hasSize(1);
            return found.get(0);
        }
    }

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

    /** Los etag pueden llevar comillas: se serializan como cadenas JSON para no romper el cuerpo. */
    private static String rollbackBody(String metamodel, String metamodelTag, String editor, String editorTag) {
        return "{\"metamodelId\":" + JSON.writeValueAsString(metamodel) + ",\"metamodelEtag\":" + JSON.writeValueAsString(metamodelTag)
                + ",\"editorId\":" + JSON.writeValueAsString(editor) + ",\"editorEtag\":" + JSON.writeValueAsString(editorTag) + "}";
    }

    private static String selection(String representationIdOrEmpty) {
        String ids = representationIdOrEmpty.isEmpty() ? "" : "\"" + representationIdOrEmpty + "\"";
        return "{\"paletteGroupIds\":[],\"paletteToolIds\":[],\"representationIds\":[" + ids
                + "],\"mappingIds\":[],\"semanticElementIds\":[],\"explicit\":true}";
    }

    private static String materializeRequest(boolean persist, String selectionJson) {
        return "{\"targetId\":\"\",\"candidateId\":\"" + candidateId + "\",\"editorId\":\"" + EDITOR_ID + "\",\"editorName\":\"Hydratest editor\","
                + "\"strict\":false,\"persist\":" + persist + ",\"selection\":" + selectionJson + "}";
    }

    @Test
    @Order(1)
    @DisplayName("recovery_lifecycle.a1: el analisis del update site sin Sirius ofrece el candidato directo y un borrador de representacion, sin guardar nada")
    void analysisOffersTheDirectCandidateAndOneRepresentationDraft() throws Exception {
        JsonNode analysis = ok(upload("/api/update-site-imports/analyze", null));

        candidateId = text(analysis.get("selectedCandidateId"));
        assertThat(candidateId).isNotBlank();
        boolean selectedIsTheEcore = false;
        for (JsonNode candidate : analysis.get("candidates")) {
            if (text(candidate.get("id")).equals(candidateId)) selectedIsTheEcore = text(candidate.get("sourcePath")).endsWith("hydratest.ecore");
        }
        assertThat(selectedIsTheEcore).as("el candidato seleccionado es el .ecore subido").isTrue();
        JsonNode representations = analysis.get("recoveredLayers").get("representations");
        assertThat(representations.size()).isEqualTo(1);
        representationId = text(representations.get(0).get("id"));
        assertThat(representationId).isNotBlank();
        assertThat(text(representations.get(0).get("sourcePath"))).endsWith("hydratest.ecorediag");
        assertThat(documents(".metamodel.json")).isEmpty();
        assertThat(documents(".editor.json")).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("recovery_persist_rollback.d1: sin guardar, la materializacion calcula el resultado y los almacenes siguen vacios")
    void notSavingLeavesTheStoresEmpty() throws Exception {
        JsonNode result = ok(upload("/api/update-site-imports/materialize", materializeRequest(false, selection(representationId))));

        assertThat(result.get("persisted").asBoolean()).isFalse();
        assertThat(result.get("rollbackAvailable").asBoolean()).isFalse();
        assertThat(result.get("preview").get("applicable").asBoolean()).as("diagnosticos: %s", result.get("preview").get("diagnostics")).isTrue();
        assertThat(documents(".metamodel.json")).isEmpty();
        assertThat(documents(".editor.json")).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("recovery_persist_rollback.d1: guardar una seleccion vacia (no aplicable) se rechaza con 400 y no escribe documentos")
    void savingANonApplicablePreviewIsRejectedWithoutWriting() throws Exception {
        HttpResponse<String> response = upload("/api/update-site-imports/materialize", materializeRequest(true, selection("")));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(documents(".metamodel.json")).isEmpty();
        assertThat(documents(".editor.json")).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("recovery_persist_rollback.d1 / recovery_lifecycle.a1: guardar escribe el metamodelo y el editor con solo la capa elegida y ofrece la reversion")
    void savingWritesTheMetamodelAndTheEditorWithTheChosenLayerOnly() throws Exception {
        JsonNode result = ok(upload("/api/update-site-imports/materialize", materializeRequest(true, selection(representationId))));

        assertThat(result.get("persisted").asBoolean()).isTrue();
        assertThat(result.get("rollbackAvailable").asBoolean()).isTrue();
        metamodelId = text(result.get("metamodelId"));
        metamodelEtag = text(result.get("metamodelEtag"));
        editorId = text(result.get("editorId"));
        editorEtag = text(result.get("editorEtag"));
        assertThat(metamodelId).isNotBlank();
        assertThat(editorId).isNotBlank();

        assertThat(documents(".metamodel.json")).containsExactly(metamodelId + ".metamodel.json");
        JsonNode storedMetamodel = JSON.readTree(Files.readString(documentPath(metamodelId + ".metamodel.json")));
        assertThat(text(storedMetamodel.get("id"))).isEqualTo(metamodelId);
        assertThat(classNames(storedMetamodel)).containsAll(expectedClasses());

        assertThat(documents(".editor.json")).containsExactly(editorId + ".editor.json");
        JsonNode storedEditor = JSON.readTree(Files.readString(documentPath(editorId + ".editor.json")));
        assertThat(text(storedEditor.get("metamodelId"))).isEqualTo(metamodelId);
        assertThat(storedEditor.get("representations").size()).as("solo la representacion elegida").isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("recovery_persist_rollback.d3: la reversion con los identificadores devueltos deja los almacenes como al inicio")
    void rollbackRestoresTheStoresToTheirInitialState() throws Exception {
        assertThat(metamodelId).as("el paso anterior guardo el metamodelo").isNotBlank();
        String body = rollbackBody(metamodelId, metamodelEtag, editorId, editorEtag);

        JsonNode rollback = ok(postJson("/api/update-site-imports/materialize/rollback", body));

        assertThat(rollback.get("editorDeleted").asBoolean()).isTrue();
        assertThat(rollback.get("metamodelDeleted").asBoolean()).as("diagnosticos: %s", rollback.get("diagnostics")).isTrue();
        assertThat(documents(".metamodel.json")).isEmpty();
        assertThat(documents(".editor.json")).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("recovery_author_wizard.h: importar y guardar escribe solo el metamodelo y su paquete de recuperacion; repetirlo se rechaza sin tocar el documento")
    void importAndSaveWritesTheMetamodelAndRefusesToOverwriteIt() throws Exception {
        JsonNode result = ok(upload("/api/update-site-imports/import?candidateId=" + candidateId, null));

        String id = text(result.get("metamodel").get("id"));
        assertThat(id).isNotBlank();
        assertThat(documents(".metamodel.json")).containsExactly(id + ".metamodel.json");
        Path stored = documentPath(id + ".metamodel.json");
        JsonNode storedMetamodel = JSON.readTree(Files.readString(stored));
        assertThat(classNames(storedMetamodel)).containsAll(expectedClasses());
        assertThat(documents(".editor.json")).as("importar no materializa editores").isEmpty();

        String packagePath = text(result.get("recoveryPackagePath"));
        assertThat(packagePath).isNotBlank();
        Path recoveryPackage = ROOT.resolve("update-site").resolve(packagePath);
        assertThat(recoveryPackage).isRegularFile();
        assertThat(text(JSON.readTree(Files.readString(recoveryPackage)).get("metamodelId"))).isEqualTo(id);

        byte[] before = Files.readAllBytes(stored);
        HttpResponse<String> again = upload("/api/update-site-imports/import?candidateId=" + candidateId, null);
        assertThat(again.statusCode()).as(again.body()).isBetween(400, 499);
        assertThat(Files.readAllBytes(stored)).as("el documento no cambia tras el segundo intento").isEqualTo(before);

        // Limpieza con la misma reversion del servicio, para dejar el almacen como al inicio.
        String etag = text(result.get("metamodel").get("etag"));
        JsonNode rollback = ok(postJson("/api/update-site-imports/materialize/rollback", rollbackBody(id, etag, "", "")));
        assertThat(rollback.get("metamodelDeleted").asBoolean()).isTrue();
        assertThat(documents(".metamodel.json")).isEmpty();
    }
}

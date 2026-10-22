package com.example.editor.standalone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.spi.ToolProvider;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.archive.CapabilityPackageArchiveProperties;
import com.example.editor.portability.archive.CapabilityPackageArchiveService;
import com.example.editor.portability.archive.CapabilityPackageDependencyResolver;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * El editor autonomo generado se ejecuta de verdad: se genera con el generador real, se extrae a disco, se compila su
 * servidor con el compilador del JDK, se lanza como proceso Java en un puerto de bucle local libre y se usa por HTTP.
 * Solo se necesita el JDK local (sin red, sin Docker, sin Node).
 *
 * Etapas: x_language_to_published_editor.gen y x_generate_certify_distribute.gen.
 * Oraculos: bytes de los ficheros generados y de los modelos guardados leidos del disco, SHA-256 recalculado en el test y
 * literales HTTP esperados; ETag obsoleto, escrituras sin condicion y modelo demasiado grande se rechazan sin tocar el disco.
 * No se ejecutan los guiones de arranque ni los destinos de contenedor (solo se generan como texto).
 */
class GeneratedStandaloneServerRuntimeIntegrationTests {
    private static final String MODEL_V1 = "{\"id\":\"person-1\",\"name\":\"Ada\"}";
    private static final String MODEL_V2 = "{\"id\":\"person-1\",\"name\":\"Ada Lovelace\"}";

    @TempDir Path temporary;
    private final HttpClient client = HttpClient.newHttpClient();
    private Process process;
    private int port;
    private Path application;
    private Path dataRoot;

    @AfterEach
    void stopServer() throws Exception {
        if (process != null) {
            process.destroy();
            process.waitFor(15, TimeUnit.SECONDS);
        }
    }

    private void generateCompileAndStart(long maximumModelBytes) throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var archiveProperties = new CapabilityPackageArchiveProperties();
        archiveProperties.setCacheDirectory(temporary.resolve("cache"));
        var archive = new CapabilityPackageArchiveService(archiveProperties, new CapabilityPackageChecker(),
                new CapabilityPackageDependencyResolver(), mapper);
        var generator = new StandaloneEditorGeneratorService(new StandaloneEditorGenerationProperties(), archive, mapper);
        StandaloneEditorGenerationResult result = generator.generate(new StandaloneEditorGenerationRequest("people-app",
                "People editor", "people", "people-editor", 4173, "EMBEDDED_LOCAL", "", true, true, maximumModelBytes,
                List.of(), List.of(), Map.of(), packageValue(), Set.of(StandaloneDeploymentTarget.SOURCE)));

        application = temporary.resolve("generated");
        extract(result.archive(), application);
        Path source = application.resolve("backend/src/HydraStandaloneServer.java");
        Path classes = application.resolve("backend/out");
        Files.createDirectories(classes);
        ToolProvider compiler = ToolProvider.findFirst("javac").orElseThrow();
        assertThat(compiler.run(System.out, System.err, "--release", "21", "-d", classes.toString(), source.toString())).isZero();
        assertThat(classes.resolve("HydraStandaloneServer.class")).isRegularFile();

        dataRoot = temporary.resolve("data");
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        Path binary = Path.of(System.getProperty("java.home"), "bin", "java");
        if (Files.isRegularFile(Path.of(System.getProperty("java.home"), "bin", "java.exe"))) {
            binary = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        }
        process = new ProcessBuilder(binary.toString(), "-cp", classes.toString(), "HydraStandaloneServer",
                "--root=" + application, "--port=" + port, "--data-root=" + dataRoot).redirectErrorStream(true).start();
        Process started = process;
        CompletableFuture<Boolean> listening = new CompletableFuture<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) if (line.contains("listening at")) listening.complete(true);
            } catch (IOException ignored) {
                // El proceso ha terminado: el futuro se completa abajo.
            } finally {
                listening.complete(false);
            }
        }, "generated-standalone-server-output");
        reader.setDaemon(true);
        reader.start();
        assertThat(listening.get(90, TimeUnit.SECONDS)).as("the generated server announced that it is listening").isTrue();
    }

    private static void extract(byte[] archive, Path target) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path file = target.resolve(entry.getName()).normalize();
                assertThat(file.startsWith(target)).as("zip entry stays inside the target: " + entry.getName()).isTrue();
                Files.createDirectories(file.getParent());
                Files.write(file, input.readAllBytes());
            }
        }
    }

    private HttpResponse<byte[]> send(String method, String path, String contentType, byte[] body, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
        if (contentType != null) builder.header("Content-Type", contentType);
        for (int index = 0; index < headers.length; index += 2) builder.header(headers[index], headers[index + 1]);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return send("GET", path, null, null);
    }

    private HttpResponse<byte[]> put(String id, String model, String... headers) throws Exception {
        return send("PUT", "/api/models/" + id, "application/json", model.getBytes(StandardCharsets.UTF_8), headers);
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String quoted(String model) throws Exception {
        return "\"" + sha256(model.getBytes(StandardCharsets.UTF_8)) + "\"";
    }

    private Path modelFile(String id) {
        return dataRoot.resolve(id + ".model.json");
    }

    @Test
    @DisplayName("x_language_to_published_editor.gen / x_generate_certify_distribute.gen: el servidor generado compila, arranca y sirve el editor, la configuracion y la salud")
    void theGeneratedServerCompilesStartsAndServesTheGeneratedEditor() throws Exception {
        generateCompileAndStart(10L * 1024L * 1024L);

        HttpResponse<byte[]> health = get("/api/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(text(health)).contains("\"status\":\"UP\"").contains("\"applicationId\":\"people-app\"");

        HttpResponse<byte[]> index = get("/");
        assertThat(index.statusCode()).isEqualTo(200);
        assertThat(index.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
        assertThat(index.body()).isEqualTo(Files.readAllBytes(application.resolve("frontend/index.html")));
        assertThat(text(index)).contains("People editor");

        HttpResponse<byte[]> script = get("/app.js");
        assertThat(script.statusCode()).isEqualTo(200);
        assertThat(script.body()).isEqualTo(Files.readAllBytes(application.resolve("frontend/app.js")));
        assertThat(script.headers().firstValue("ETag").orElse("")).isEqualTo("\"" + sha256(script.body()) + "\"");

        HttpResponse<byte[]> config = get("/api/config");
        assertThat(config.statusCode()).isEqualTo(200);
        assertThat(config.body()).isEqualTo(Files.readAllBytes(application.resolve("standalone-editor.json")));
        assertThat(text(config)).contains("people-app").contains("people-editor");

        assertThat(get("/does-not-exist.js").statusCode()).isEqualTo(404);
        HttpResponse<byte[]> escape = get("/%2e%2e/standalone-editor.json");
        assertThat(escape.statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(text(escape)).doesNotContain("people-editor");
    }

    @Test
    @DisplayName("x_language_to_published_editor.gen: guardar, releer, actualizar y borrar modelos con ETag; un ETag obsoleto o una escritura sin condicion se rechazan")
    void modelsArePersistedWithOptimisticConcurrencyAndConflictsAreRejectedWithoutTouchingDisk() throws Exception {
        generateCompileAndStart(10L * 1024L * 1024L);

        HttpResponse<byte[]> created = put("person-1", MODEL_V1, "If-None-Match", "*");
        assertThat(created.statusCode()).isEqualTo(200);
        String firstTag = created.headers().firstValue("ETag").orElse("");
        assertThat(firstTag).isEqualTo(quoted(MODEL_V1));
        assertThat(Files.readString(modelFile("person-1"))).isEqualTo(MODEL_V1);

        HttpResponse<byte[]> reread = get("/api/models/person-1");
        assertThat(reread.statusCode()).isEqualTo(200);
        assertThat(text(reread)).isEqualTo(MODEL_V1);
        assertThat(reread.headers().firstValue("ETag").orElse("")).isEqualTo(firstTag);
        assertThat(text(get("/api/models"))).isEqualTo("[\"person-1\"]");

        HttpResponse<byte[]> updated = put("person-1", MODEL_V2, "If-Match", firstTag);
        assertThat(updated.statusCode()).isEqualTo(200);
        String secondTag = updated.headers().firstValue("ETag").orElse("");
        assertThat(secondTag).isEqualTo(quoted(MODEL_V2));
        assertThat(sha256(Files.readAllBytes(modelFile("person-1")))).isEqualTo(secondTag.replace("\"", ""));

        assertThat(put("person-1", MODEL_V1, "If-Match", firstTag).statusCode()).isEqualTo(412);
        assertThat(put("person-1", MODEL_V1).statusCode()).isEqualTo(428);
        assertThat(put("person-1", MODEL_V1, "If-None-Match", "*").statusCode()).isEqualTo(412);
        assertThat(put("person-2", "{\"id\":\"person-2\"}").statusCode()).isEqualTo(428);
        assertThat(put("person-3", "{\"id\":\"other\"}", "If-None-Match", "*").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/models/person-4", "text/plain", "{\"id\":\"person-4\"}".getBytes(StandardCharsets.UTF_8),
                "If-None-Match", "*").statusCode()).isEqualTo(415);
        assertThat(Files.readString(modelFile("person-1"))).isEqualTo(MODEL_V2);
        assertThat(modelFile("person-2")).doesNotExist();
        assertThat(modelFile("person-3")).doesNotExist();
        assertThat(modelFile("person-4")).doesNotExist();

        assertThat(send("DELETE", "/api/models/person-1", null, null).statusCode()).isEqualTo(428);
        assertThat(send("DELETE", "/api/models/person-1", null, null, "If-Match", firstTag).statusCode()).isEqualTo(412);
        assertThat(modelFile("person-1")).isRegularFile();
        assertThat(send("DELETE", "/api/models/person-1", null, null, "If-Match", secondTag).statusCode()).isEqualTo(204);
        assertThat(modelFile("person-1")).doesNotExist();
        assertThat(get("/api/models/person-1").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("x_language_to_published_editor.gen: un modelo mayor que el limite configurado en la generacion se rechaza sin escribir")
    void aModelLargerThanTheGeneratedLimitIsRejectedWithoutWriting() throws Exception {
        generateCompileAndStart(256L);
        String oversized = "{\"id\":\"big\",\"padding\":\"" + "x".repeat(600) + "\"}";

        assertThat(put("big", oversized, "If-None-Match", "*").statusCode()).isEqualTo(413);
        assertThat(modelFile("big")).doesNotExist();

        String small = "{\"id\":\"big\",\"padding\":\"ok\"}";
        assertThat(put("big", small, "If-None-Match", "*").statusCode()).isEqualTo(200);
        assertThat(Files.readString(modelFile("big"))).isEqualTo(small);
    }

    private HydraCapabilityPackage packageValue() {
        var metamodel = new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL, "metamodel.json", "2.1", true, false, Map.of());
        var editor = new CapabilitySelection("editor", HydraCapabilityKind.VISUAL_REPRESENTATION, "editor.json", "1.0", true, true, Map.of());
        var manifest = new HydraCapabilityPackageManifest("1.0", "people-editor-package", "People editor", "1.0.0", "people",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                List.of(metamodel, editor), List.of(new CapabilityDependency("editor", "metamodel", true, ">=2.1",
                        "The editor resolves metamodel types.")), List.of(), Map.of(), Map.of());
        Map<String, tools.jackson.databind.JsonNode> documents = new LinkedHashMap<>();
        ObjectNode metamodelDocument = JsonNodeFactory.instance.objectNode();
        metamodelDocument.put("schemaVersion", "2.1");
        metamodelDocument.put("id", "people");
        metamodelDocument.putArray("classes");
        metamodelDocument.putArray("relations");
        ObjectNode editorDocument = JsonNodeFactory.instance.objectNode();
        editorDocument.put("schemaVersion", "1.0");
        editorDocument.put("id", "people-editor");
        editorDocument.putObject("representationsById");
        documents.put("metamodel.json", metamodelDocument);
        documents.put("editor.json", editorDocument);
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("icons/person.svg", "<svg/>");
        return new HydraCapabilityPackage(manifest, documents, resources);
    }
}

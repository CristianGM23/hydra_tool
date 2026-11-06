package com.example.editor.remote.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.project.CreateLocalProjectRequest;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectCapability;
import com.example.editor.project.OpenLocalProjectRequest;
import com.example.editor.project.ProjectEntryCreateRequest;
import com.example.editor.project.ProjectExperienceMode;
import com.example.editor.project.ProjectPreset;
import com.example.editor.project.ProjectSession;
import com.example.editor.project.ProjectStorageTestFixture;
import com.example.editor.remote.auth.RemoteAuthProperties;
import com.example.editor.remote.storage.provider.GitHubRemoteStorageProvider;
import com.example.editor.remote.storage.provider.RemoteStorageHttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportar e importar proyectos y capas contra un servidor loopback que imita la API de contenidos de GitHub.
 *
 * <p>Etapas certificadas: {@code repos_author_transfer.a/b/c/d/r}, {@code repos_connect_sync.a5},
 * {@code repos_file_transfer.a/b/c/d/r}.</p>
 *
 * <p>Colaboradores reales: servicio de transferencia de proyectos, servicio de almacenamiento remoto, adaptador
 * HTTP de GitHub, cliente HTTP real, politica de rutas y almacenamiento local de proyectos en un {@code @TempDir}.
 * Lo unico simulado es el proveedor: un servidor HTTP en 127.0.0.1 con estado en memoria. El oraculo lee el ZIP
 * recibido por ese servidor y recalcula las versiones (SHA-1 de blob) por su cuenta.</p>
 */
class RemoteGithubLoopbackTransferIntegrationTests {
    private static final String TOKEN = "loopback-token";

    @TempDir Path temporary;

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private GithubSimulator github;
    private ProjectApplicationService projects;
    private RemoteStorageService storage;
    private RemoteProjectTransferService transfer;

    @BeforeEach
    void setUp() throws Exception {
        github = new GithubSimulator(mapper);
        String base = "http://127.0.0.1:" + github.port();
        RemoteStorageProperties properties = new RemoteStorageProperties();
        Map<String, RemoteStorageProperties.Profile> profiles = new LinkedHashMap<>();
        profiles.put("gh", githubProfile(base, 0L));
        profiles.put("small", githubProfile(base, 10L));
        properties.setProfiles(profiles);
        RemoteStorageHttpClient http = new RemoteStorageHttpClient(properties, new RemoteAuthProperties());
        RemoteStorageProviderRegistry registry = new RemoteStorageProviderRegistry(List.of(new GitHubRemoteStorageProvider(http, mapper)));
        storage = new RemoteStorageService(properties, registry, new ConfiguredRemoteStorageCredentialResolver());
        projects = new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
        transfer = new RemoteProjectTransferService(projects, storage);
    }

    @AfterEach
    void tearDown() {
        github.stop();
    }

    @Test
    @DisplayName("repos_file_transfer.a/c/r, repos_author_transfer.a/b/d/r, repos_connect_sync.a5: el proyecto completo viaja en un ZIP bajo la raiz del perfil y se importa a un proyecto nuevo")
    void aWholeProjectTravelsAsAZipUnderTheProfileRootAndImportsIntoANewProject() throws Exception {
        Path original = createProjectWithOneMetamodel();

        RemoteArtifactDescriptor exported = transfer.exportActive(new RemoteProjectExportRequest(
                "gh", "backups/alpha.zip", RemoteWriteMode.CREATE_ONLY, "", "Save alpha"));

        // Lo que ve el servidor (oraculo: bytes y versiones recalculados aqui).
        byte[] received = github.content("HYDRA/backups/alpha.zip");
        assertThat(received).isNotNull();
        assertThat(exported.created()).isTrue();
        assertThat(exported.path()).isEqualTo("HYDRA/backups/alpha.zip");
        assertThat(exported.provider()).isEqualTo(RemoteStorageProviderType.GITHUB);
        assertThat(exported.scope()).isEqualTo(RemoteArtifactScope.PROJECT);
        assertThat(exported.size()).isEqualTo(received.length);
        assertThat(exported.version()).isEqualTo(blobSha(received));
        Map<String, String> entries = unzip(received);
        assertThat(entries).containsKeys("modeling-project.json", "metamodels/people.metamodel.json");
        assertThat(entries.get("metamodels/people.metamodel.json")).isEqualTo("{\"id\":\"people\"}");
        assertThat(mapper.readTree(entries.get("modeling-project.json")).get("id").asString()).isEqualTo("p-alpha");
        GithubSimulator.Put put = github.puts().get(0);
        assertThat(put.body().get("message").asString()).isEqualTo("Save alpha");
        assertThat(put.body().get("branch").asString()).isEqualTo("main");
        assertThat(put.body().has("sha")).isFalse();
        assertThat(github.authorizations()).isNotEmpty().allMatch(("Bearer " + TOKEN)::equals);
        assertThat(github.requestLines()).noneMatch(line -> line.contains(TOKEN));

        // Importar a un proyecto nuevo: los ficheros del ZIP aparecen en disco.
        Path parent = Files.createDirectories(temporary.resolve("imports"));
        ProjectSession imported = transfer.importProject(new RemoteProjectImportRequest("gh", "backups/alpha.zip", parent.toString(), "Imported Alpha"));
        Path importedRoot = Path.of(imported.directory());
        assertThat(importedRoot.getFileName().toString()).isEqualTo("Imported-Alpha");
        assertThat(Files.readString(importedRoot.resolve("metamodels/people.metamodel.json"))).isEqualTo("{\"id\":\"people\"}");
        assertThat(imported.project().id()).isEqualTo("p-alpha");

        // Sin ruta ni mensaje el servicio usa el nombre por defecto del proyecto y su mensaje.
        projects.open(new OpenLocalProjectRequest(original.toString(), false, null));
        RemoteArtifactDescriptor defaults = transfer.exportActive(new RemoteProjectExportRequest("gh", "", RemoteWriteMode.UPSERT, "", ""));
        assertThat(defaults.path()).isEqualTo("HYDRA/p-alpha.hydra-project.zip");
        assertThat(github.puts().get(github.puts().size() - 1).body().get("message").asString()).isEqualTo("Save HYDRA project Transfer Alpha");
    }

    @Test
    @DisplayName("repos_file_transfer.a/c, repos_author_transfer.a: una sola capa viaja como fichero y vuelve con los mismos bytes")
    void aSingleLayerTravelsAsAFileAndComesBackWithTheSameBytes() throws Exception {
        byte[] layer = "{\"id\":\"people\",\"classes\":[]}".getBytes(StandardCharsets.UTF_8);

        RemoteArtifactDescriptor written = storage.write("gh", "layers/model folder/people.metamodel.json", RemoteArtifactScope.LAYER,
                layer, "application/json", RemoteWriteMode.UPSERT, "", "Save layer");

        assertThat(written.created()).isTrue();
        assertThat(written.scope()).isEqualTo(RemoteArtifactScope.LAYER);
        assertThat(github.content("HYDRA/layers/model folder/people.metamodel.json")).isEqualTo(layer);
        assertThat(written.version()).isEqualTo(blobSha(layer));
        RemoteArtifact read = storage.read("gh", "layers/model folder/people.metamodel.json", RemoteArtifactScope.LAYER);
        assertThat(read.content()).isEqualTo(layer);
        assertThat(read.descriptor().version()).isEqualTo(blobSha(layer));
        assertThat(github.requestLines()).anyMatch(line -> line.contains("/contents/HYDRA/layers/model%20folder/people.metamodel.json"));
    }

    @Test
    @DisplayName("repos_author_transfer.d, repos_file_transfer.d: una version esperada obsoleta o un cambio concurrente se rechazan sin pisar lo del servidor")
    void anObsoleteExpectedVersionOrAConcurrentChangeIsRejectedWithoutOverwritingTheServer() throws Exception {
        createProjectWithOneMetamodel();
        RemoteArtifactDescriptor first = transfer.exportActive(new RemoteProjectExportRequest(
                "gh", "backups/alpha.zip", RemoteWriteMode.UPSERT, "", "first"));
        byte[] changedOnServer = "changed by someone else".getBytes(StandardCharsets.UTF_8);
        github.store("HYDRA/backups/alpha.zip", changedOnServer);
        int putsBefore = github.puts().size();

        // Version esperada de la primera exportacion: el servidor ya tiene otra.
        assertThatThrownBy(() -> transfer.exportActive(new RemoteProjectExportRequest(
                "gh", "backups/alpha.zip", RemoteWriteMode.UPSERT, first.version(), "stale")))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.PRECONDITION_FAILED));
        assertThat(github.content("HYDRA/backups/alpha.zip")).isEqualTo(changedOnServer);
        assertThat(github.puts()).hasSize(putsBefore);

        // Con la version vigente la escritura se acepta y el servidor guarda los bytes nuevos.
        RemoteArtifactDescriptor second = transfer.exportActive(new RemoteProjectExportRequest(
                "gh", "backups/alpha.zip", RemoteWriteMode.UPSERT, blobSha(changedOnServer), "current"));
        assertThat(second.created()).isFalse();
        assertThat(second.version()).isEqualTo(blobSha(github.content("HYDRA/backups/alpha.zip")));
        assertThat(second.version()).isNotEqualTo(blobSha(changedOnServer));
        assertThat(github.puts()).hasSize(putsBefore + 1);
        assertThat(github.puts().get(putsBefore).body().get("sha").asString()).isEqualTo(blobSha(changedOnServer));

        // Carrera: el fichero cambia entre la lectura de su version y la escritura; el servidor responde con conflicto.
        byte[] raced = "changed during the transfer".getBytes(StandardCharsets.UTF_8);
        github.beforeNextPut(() -> github.store("HYDRA/backups/alpha.zip", raced));
        assertThatThrownBy(() -> transfer.exportActive(new RemoteProjectExportRequest(
                "gh", "backups/alpha.zip", RemoteWriteMode.UPSERT, "", "race")))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.CONFLICT));
        assertThat(github.content("HYDRA/backups/alpha.zip")).isEqualTo(raced);

        // Carrera de creacion: alguien crea el fichero entre comprobar que no existe y escribirlo.
        byte[] createdByOther = "created by someone else".getBytes(StandardCharsets.UTF_8);
        github.beforeNextPut(() -> github.store("HYDRA/backups/new.zip", createdByOther));
        assertThatThrownBy(() -> transfer.exportActive(new RemoteProjectExportRequest(
                "gh", "backups/new.zip", RemoteWriteMode.UPSERT, "", "race-create")))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.CONFLICT));
        assertThat(github.content("HYDRA/backups/new.zip")).isEqualTo(createdByOther);
    }

    @Test
    @DisplayName("repos_author_transfer.c, repos_file_transfer.b: los modos solo crear, solo actualizar y sobrescribir se respetan")
    void theThreeWriteModesAreRespected() throws Exception {
        byte[] v1 = "v1".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "v2".getBytes(StandardCharsets.UTF_8);
        byte[] v3 = "v3".getBytes(StandardCharsets.UTF_8);
        storage.write("gh", "modes/a.json", RemoteArtifactScope.LAYER, v1, "application/json", RemoteWriteMode.CREATE_ONLY, "", "create");
        assertThat(github.content("HYDRA/modes/a.json")).isEqualTo(v1);
        int puts = github.puts().size();

        assertThatThrownBy(() -> storage.write("gh", "modes/a.json", RemoteArtifactScope.LAYER, v2, "application/json", RemoteWriteMode.CREATE_ONLY, "", "again"))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.CONFLICT));
        assertThatThrownBy(() -> storage.write("gh", "modes/missing.json", RemoteArtifactScope.LAYER, v2, "application/json", RemoteWriteMode.UPDATE_ONLY, "", "update"))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.NOT_FOUND));
        assertThat(github.content("HYDRA/modes/a.json")).isEqualTo(v1);
        assertThat(github.content("HYDRA/modes/missing.json")).isNull();
        assertThat(github.puts()).hasSize(puts);

        RemoteArtifactDescriptor updated = storage.write("gh", "modes/a.json", RemoteArtifactScope.LAYER, v2, "application/json", RemoteWriteMode.UPDATE_ONLY, "", "update");
        assertThat(updated.created()).isFalse();
        assertThat(github.content("HYDRA/modes/a.json")).isEqualTo(v2);
        RemoteArtifactDescriptor overwritten = storage.write("gh", "modes/a.json", RemoteArtifactScope.LAYER, v3, "application/json", RemoteWriteMode.UPSERT, "", "overwrite");
        assertThat(overwritten.created()).isFalse();
        assertThat(github.content("HYDRA/modes/a.json")).isEqualTo(v3);
        RemoteArtifactDescriptor fresh = storage.write("gh", "modes/b.json", RemoteArtifactScope.LAYER, v1, "application/json", RemoteWriteMode.UPSERT, "", "new");
        assertThat(fresh.created()).isTrue();
    }

    @Test
    @DisplayName("repos_author_transfer.b, repos_file_transfer.c: la ruta se normaliza bajo la raiz y las rutas peligrosas, el exceso de tamano o lo inexistente se rechazan")
    void pathsAreNormalizedUnderTheRootAndUnsafeOversizedOrMissingArtifactsAreRejected() throws Exception {
        byte[] small = "ok".getBytes(StandardCharsets.UTF_8);
        storage.write("gh", "/nested\\dir//file.json", RemoteArtifactScope.LAYER, small, "application/json", RemoteWriteMode.UPSERT, "", "normalized");
        assertThat(github.content("HYDRA/nested/dir/file.json")).isEqualTo(small);
        int requests = github.requestLines().size();

        assertThatThrownBy(() -> storage.write("gh", "../escape.json", RemoteArtifactScope.LAYER, small, "application/json", RemoteWriteMode.UPSERT, "", "escape"))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.INVALID_REQUEST));
        assertThatThrownBy(() -> storage.write("gh", "  ", RemoteArtifactScope.LAYER, small, "application/json", RemoteWriteMode.UPSERT, "", "blank"))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.INVALID_REQUEST));
        assertThatThrownBy(() -> storage.write("small", "big.json", RemoteArtifactScope.LAYER,
                "this is more than ten bytes".getBytes(StandardCharsets.UTF_8), "application/json", RemoteWriteMode.UPSERT, "", "big"))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.FILE_TOO_LARGE));
        assertThatThrownBy(() -> storage.write("unknown", "a.json", RemoteArtifactScope.LAYER, small, "application/json", RemoteWriteMode.UPSERT, "", "x"))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.PROFILE_NOT_FOUND));
        // Ninguno de los rechazos anteriores llego al servidor.
        assertThat(github.requestLines()).hasSize(requests);

        assertThatThrownBy(() -> storage.read("gh", "does/not/exist.zip", RemoteArtifactScope.PROJECT))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.NOT_FOUND));
        assertThatThrownBy(() -> transfer.importProject(new RemoteProjectImportRequest("gh", "does/not/exist.zip", temporary.toString(), "Nothing")))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.NOT_FOUND));
        assertThat(temporary.resolve("Nothing")).doesNotExist();
    }

    private Path createProjectWithOneMetamodel() throws Exception {
        Path parent = Files.createDirectories(temporary.resolve("workspace"));
        ProjectSession created = projects.create(new CreateLocalProjectRequest(parent.toString(), "p-alpha", "Transfer Alpha", "", false,
                ProjectPreset.EMPTY, Set.of(ProjectCapability.METAMODEL_DESIGN), ProjectExperienceMode.FREE));
        projects.createEntry(new ProjectEntryCreateRequest("metamodels/people.metamodel.json", false, "{\"id\":\"people\"}"));
        return Path.of(created.directory());
    }

    private static RemoteStorageProperties.Profile githubProfile(String baseUrl, long maximumArtifactBytes) {
        RemoteStorageProperties.Profile profile = new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITHUB);
        profile.setBaseUrl(baseUrl);
        profile.setOwner("octo");
        profile.setRepository("models");
        profile.setBranch("main");
        profile.setRootPath("HYDRA");
        profile.setToken(TOKEN);
        profile.setMaximumArtifactBytes(maximumArtifactBytes);
        return profile;
    }

    private static Map<String, String> unzip(byte[] archive) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    result.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }

    private static String blobSha(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
        digest.update(content);
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Servidor HTTP en 127.0.0.1 que imita la API de contenidos de GitHub con estado en memoria. */
    private static final class GithubSimulator implements HttpHandler {
        private static final String PREFIX = "/repos/octo/models/contents/";

        record Put(String path, JsonNode body) { }

        private final ObjectMapper json;
        private final HttpServer server;
        private final Map<String, byte[]> files = new java.util.concurrent.ConcurrentHashMap<>();
        private final List<String> requestLines = Collections.synchronizedList(new ArrayList<>());
        private final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
        private final List<Put> puts = Collections.synchronizedList(new ArrayList<>());
        private volatile Runnable beforeNextPut;

        GithubSimulator(ObjectMapper json) throws IOException {
            this.json = json;
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
            this.server.createContext("/", this);
            this.server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        byte[] content(String path) {
            return files.get(path);
        }

        void store(String path, byte[] content) {
            files.put(path, content);
        }

        void beforeNextPut(Runnable action) {
            beforeNextPut = action;
        }

        List<String> requestLines() {
            return requestLines;
        }

        List<String> authorizations() {
            return authorizations;
        }

        List<Put> puts() {
            return puts;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String rawPath = exchange.getRequestURI().getRawPath();
                String query = exchange.getRequestURI().getRawQuery();
                requestLines.add(exchange.getRequestMethod() + " " + rawPath + (query == null ? "" : "?" + query));
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                authorizations.add(authorization == null ? "" : authorization);
                if (!rawPath.startsWith(PREFIX)) {
                    respond(exchange, 404, "application/json", "{\"message\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                String file = URLDecoder.decode(rawPath.substring(PREFIX.length()), StandardCharsets.UTF_8);
                if ("GET".equals(exchange.getRequestMethod())) {
                    get(exchange, file);
                } else if ("PUT".equals(exchange.getRequestMethod())) {
                    put(exchange, file);
                } else {
                    respond(exchange, 405, "application/json", "{\"message\":\"Method Not Allowed\"}".getBytes(StandardCharsets.UTF_8));
                }
            } catch (RuntimeException exception) {
                respond(exchange, 500, "application/json", ("{\"message\":\"" + exception.getClass().getSimpleName() + "\"}").getBytes(StandardCharsets.UTF_8));
            }
        }

        private void get(HttpExchange exchange, String file) throws IOException {
            byte[] content = files.get(file);
            if (content == null) {
                respond(exchange, 404, "application/json", "{\"message\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept != null && accept.contains(".raw")) {
                respond(exchange, 200, "application/octet-stream", content);
                return;
            }
            String body = "{\"sha\":\"" + sha(content) + "\",\"size\":" + content.length + ",\"html_url\":\"http://127.0.0.1:" + port() + "/blob/" + file.replace(" ", "%20") + "\"}";
            respond(exchange, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
        }

        private void put(HttpExchange exchange, String file) throws IOException {
            JsonNode body = json.readTree(exchange.getRequestBody().readAllBytes());
            puts.add(new Put(file, body));
            Runnable hook = beforeNextPut;
            if (hook != null) {
                beforeNextPut = null;
                hook.run();
            }
            byte[] current = files.get(file);
            boolean hasSha = body.has("sha");
            if (current != null && !hasSha) {
                respond(exchange, 422, "application/json", "{\"message\":\"sha wasn't supplied\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (current != null && !sha(current).equals(body.get("sha").asString())) {
                respond(exchange, 409, "application/json", "{\"message\":\"does not match\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (current == null && hasSha) {
                respond(exchange, 422, "application/json", "{\"message\":\"Invalid request\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] content = Base64.getDecoder().decode(body.get("content").asString());
            files.put(file, content);
            String reply = "{\"content\":{\"sha\":\"" + sha(content) + "\",\"html_url\":\"http://127.0.0.1:" + port() + "/blob/" + file.replace(" ", "%20") + "\"}}";
            respond(exchange, current == null ? 201 : 200, "application/json", reply.getBytes(StandardCharsets.UTF_8));
        }

        private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        }

        private static String sha(byte[] content) {
            try {
                return blobSha(content);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}

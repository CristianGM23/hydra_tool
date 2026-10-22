package com.example.editor.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
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
import com.example.editor.remote.auth.RemoteAuthProperties;
import com.example.editor.remote.storage.ConfiguredRemoteStorageCredentialResolver;
import com.example.editor.remote.storage.RemoteProjectImportRequest;
import com.example.editor.remote.storage.RemoteProjectTransferService;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderRegistry;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import com.example.editor.remote.storage.RemoteStorageService;
import com.example.editor.remote.storage.provider.GitHubRemoteStorageProvider;
import com.example.editor.remote.storage.provider.RemoteStorageHttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * x_project_lifecycle_overview.d1 - efecto de cada origen de importacion de un proyecto.
 *
 * Contexto Spring real con todos los almacenes en un directorio temporal. Un proyecto HYDRA minimo (un metamodelo) se
 * importa desde una carpeta, desde un ZIP y desde un repositorio remoto (servidor loopback en 127.0.0.1 con la forma de
 * la API de contenidos de GitHub); despues el ZIP del fixture Sirius completo se importa como proyecto externo.
 * Oraculos: listados de ficheros leidos del disco, contenido literal esperado y ausencia/presencia de la carpeta de
 * recuperacion y del metamodelo en el almacen canonico. Los tres primeros orígenes solo copian ficheros; solo el
 * proyecto externo dispara la recuperacion semantica. El contenido de partida no es el mismo en los cuatro casos porque
 * los tres primeros exigen un proyecto HYDRA (con descriptor) y el externo no puede tenerlo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectImportOriginsEffectsIntegrationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final String PEOPLE = "metamodels/people.metamodel.json";
    private static final String PEOPLE_CONTENT = "{\"id\":\"people\"}";
    private static final List<String> SIRIUS_FILES = List.of(
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

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static GithubReadSimulator github;
    private static Path sourceProject;
    private static byte[] sourceArchive;

    @AfterAll
    static void stopLoopback() {
        if (github != null) github.stop();
    }

    @Autowired private MockMvc mvc;
    @Autowired private ProjectApplicationService projects;

    /** Crea una sola vez el proyecto HYDRA de partida y su ZIP. */
    private void ensureSourceProject() throws Exception {
        if (sourceProject != null) return;
        Path parent = Files.createDirectories(root.resolve("ws-source"));
        ProjectSession created = projects.create(new CreateLocalProjectRequest(parent.toString(), "p-origin", "Origin Source", "", false,
                ProjectPreset.EMPTY, Set.of(ProjectCapability.METAMODEL_DESIGN), ProjectExperienceMode.FREE));
        projects.createEntry(new ProjectEntryCreateRequest(PEOPLE, false, PEOPLE_CONTENT));
        sourceProject = Path.of(created.directory());
        sourceArchive = projects.exportArchive();
    }

    private static List<String> filesOf(Path directory) throws IOException {
        try (var walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).map(path -> directory.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private static List<String> canonicalMetamodelFiles() throws IOException {
        Path store = root.resolve("metamodels");
        if (!Files.isDirectory(store)) return List.of();
        try (var walk = Files.walk(store)) {
            return walk.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".metamodel.json")).sorted().toList();
        }
    }

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = ProjectImportOriginsEffectsIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if (input == null) throw new IOException("Missing fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private static byte[] siriusZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String relative : SIRIUS_FILES) {
                zip.putNextEntry(new ZipEntry(relative));
                zip.write(resource(relative));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** Un proyecto importado como copia: sus ficheros son los de origen y no hay rastro de recuperacion. */
    private static void assertPlainCopy(String directory) throws IOException {
        Path imported = Path.of(directory);
        List<String> files = filesOf(imported);
        assertThat(files).contains("modeling-project.json", PEOPLE);
        assertThat(files).noneMatch(name -> name.startsWith("recovery/") || name.startsWith("external-source/"));
        assertThat(Files.readString(imported.resolve(PEOPLE))).isEqualTo(PEOPLE_CONTENT);
    }

    @Test
    @Order(1)
    @DisplayName("x_project_lifecycle_overview.d1: importar una carpeta de proyecto HYDRA solo copia ficheros, sin recuperacion ni metamodelo nuevo")
    void importingAProjectFolderOnlyCopiesFiles() throws Exception {
        ensureSourceProject();
        Path parent = Files.createDirectories(root.resolve("ws-folder"));
        String body = "{\"sourceDirectory\":\"" + sourceProject.toString().replace("\\", "\\\\") + "\",\"parentDirectory\":\""
                + parent.toString().replace("\\", "\\\\") + "\",\"projectName\":\"Folder Copy\"}";
        MockHttpServletResponse response = mvc.perform(post("/api/projects/import-directory").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        String directory = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("directory").asString();
        assertPlainCopy(directory);
        assertThat(canonicalMetamodelFiles()).as("una copia de carpeta no crea metamodelos canonicos").isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("x_project_lifecycle_overview.d1: importar el ZIP de un proyecto HYDRA solo copia ficheros; un ZIP sin descriptor se rechaza sin dejar proyecto")
    void importingAProjectZipOnlyCopiesFilesAndRejectsAnArchiveWithoutDescriptor() throws Exception {
        ensureSourceProject();
        Path parent = Files.createDirectories(root.resolve("ws-zip"));
        MockHttpServletResponse response = mvc.perform(multipart("/api/projects/import")
                .file(new MockMultipartFile("file", "origin.zip", "application/zip", sourceArchive))
                .param("parentDirectory", parent.toString()).param("projectName", "Zip Copy")).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        String directory = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("directory").asString();
        assertPlainCopy(directory);
        assertThat(canonicalMetamodelFiles()).isEmpty();

        // Negativo: el ZIP del proyecto externo no es un proyecto HYDRA; la importacion simple lo rechaza y no deja carpeta.
        MockHttpServletResponse rejected = mvc.perform(multipart("/api/projects/import")
                .file(new MockMultipartFile("file", "sirius.zip", "application/zip", siriusZip()))
                .param("parentDirectory", parent.toString()).param("projectName", "Not A Project")).andReturn().getResponse();
        assertThat(rejected.getStatus()).as(rejected.getContentAsString(StandardCharsets.UTF_8)).isGreaterThanOrEqualTo(400);
        try (var children = Files.list(parent)) {
            assertThat(children.filter(Files::isDirectory).toList()).as("solo queda la copia valida, la rechazada no deja carpeta").hasSize(1);
        }
        assertThat(canonicalMetamodelFiles()).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("x_project_lifecycle_overview.d1: importar desde un repositorio remoto (loopback) solo copia ficheros")
    void importingFromARemoteRepositoryOnlyCopiesFiles() throws Exception {
        ensureSourceProject();
        github = new GithubReadSimulator();
        github.store("HYDRA/backups/origin.zip", sourceArchive);

        RemoteStorageProperties properties = new RemoteStorageProperties();
        RemoteStorageProperties.Profile profile = new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITHUB);
        profile.setBaseUrl("http://127.0.0.1:" + github.port());
        profile.setOwner("octo");
        profile.setRepository("models");
        profile.setBranch("main");
        profile.setRootPath("HYDRA");
        profile.setToken("loopback-token");
        profile.setMaximumArtifactBytes(0L);
        Map<String, RemoteStorageProperties.Profile> profiles = new LinkedHashMap<>();
        profiles.put("gh", profile);
        properties.setProfiles(profiles);
        RemoteStorageHttpClient http = new RemoteStorageHttpClient(properties, new RemoteAuthProperties());
        RemoteStorageProviderRegistry registry = new RemoteStorageProviderRegistry(List.of(new GitHubRemoteStorageProvider(http, JSON)));
        RemoteStorageService storage = new RemoteStorageService(properties, registry, new ConfiguredRemoteStorageCredentialResolver());
        RemoteProjectTransferService transfer = new RemoteProjectTransferService(projects, storage);

        Path parent = Files.createDirectories(root.resolve("ws-remote"));
        ProjectSession imported = transfer.importProject(new RemoteProjectImportRequest("gh", "backups/origin.zip", parent.toString(), "Remote Copy"));
        assertPlainCopy(imported.directory());
        assertThat(github.requests()).as("el servidor loopback recibio la lectura").isNotEmpty();
        assertThat(canonicalMetamodelFiles()).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("x_project_lifecycle_overview.d1: solo el archivo de proyecto externo genera recuperacion semantica y metamodelo canonico")
    void onlyAnExternalProjectArchiveTriggersSemanticRecovery() throws Exception {
        Path parent = Files.createDirectories(root.resolve("ws-external"));
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/import")
                .file(new MockMultipartFile("file", "sirius-fixture.zip", "application/zip", siriusZip()))
                .param("parentDirectory", parent.toString()).param("projectName", "External Origin")).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        JsonNode result = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        Path imported = Path.of(result.get("session").get("directory").asString());
        List<String> files = filesOf(imported);

        assertThat(files).contains("recovery/external-project-analysis.json", "recovery/external-project-materialization.json", "recovery/README.md");
        for (String relative : SIRIUS_FILES) assertThat(files).as("original preservado").contains("external-source/" + relative);
        assertThat(Files.readAllBytes(imported.resolve("external-source/com.hydra.sirius.fixture.metamodel/model/hydratest.ecore")))
                .isEqualTo(resource("com.hydra.sirius.fixture.metamodel/model/hydratest.ecore"));

        String metamodelId = result.get("materialization").get("metamodelId").asString();
        assertThat(result.get("materialization").get("metamodelMaterialized").asBoolean()).isTrue();
        assertThat(metamodelId).isNotBlank();
        assertThat(canonicalMetamodelFiles()).as("el origen externo si crea el metamodelo canonico").containsExactly(metamodelId + ".metamodel.json");
        JsonNode stored = JSON.readTree(Files.readString(root.resolve("metamodels").resolve(metamodelId + ".metamodel.json")));
        List<String> classNames = new java.util.ArrayList<>();
        for (JsonNode metaClass : stored.get("classes")) classNames.add(metaClass.get("name").asString());
        assertThat(classNames).contains("Workflow", "Task", "Connection", "DecisionNode");
    }

    /** Servidor loopback minimo (solo lectura) con la forma de la API de contenidos de GitHub. */
    private static final class GithubReadSimulator implements HttpHandler {
        private static final String PREFIX = "/repos/octo/models/contents/";
        private final HttpServer server;
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();
        private final List<String> requests = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        GithubReadSimulator() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
            this.server.createContext("/", this);
            this.server.start();
        }

        int port() { return server.getAddress().getPort(); }
        void stop() { server.stop(0); }
        void store(String path, byte[] content) { files.put(path, content); }
        List<String> requests() { return requests; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String rawPath = exchange.getRequestURI().getRawPath();
            requests.add(exchange.getRequestMethod() + " " + rawPath);
            if (!rawPath.startsWith(PREFIX) || !"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 404, "application/json", "{\"message\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String file = URLDecoder.decode(rawPath.substring(PREFIX.length()), StandardCharsets.UTF_8);
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
            String body = "{\"sha\":\"" + blobSha(content) + "\",\"size\":" + content.length + ",\"html_url\":\"http://127.0.0.1:" + port()
                    + "/blob/" + file.replace(" ", "%20") + "\"}";
            respond(exchange, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
        }

        private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        }

        private static String blobSha(byte[] content) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
                digest.update(content);
                return HexFormat.of().formatHex(digest.digest());
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}

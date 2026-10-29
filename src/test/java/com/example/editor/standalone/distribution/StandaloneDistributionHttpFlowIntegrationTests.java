package com.example.editor.standalone.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Centro de distribucion independiente de editores generados: Tomcat real (puerto aleatorio), cliente
 * {@link HttpClient} real y {@code hydra.standalone-distribution.allowed-root} apuntando a un directorio temporal.
 * Cadena real controlador -> servicio de aplicacion -> planificador/materializador/validador/empaquetador.
 *
 * Etapas: publication_author_distribution.{pv,mv,pk}, publication_standalone_distribution.{pr,pl,mt,va,rd,pk,pr2},
 * x_generate_certify_distribute.{dist,sec,zip}, x_project_lifecycle_overview.r.
 * Oraculos independientes: ficheros leidos del disco, ZIP descomprimido por el propio test, SHA-256 recalculado
 * sobre los bytes descargados y codigos HTTP literales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "dsl.storage.create-directories=false", "hydra.instance-lock.enabled=false"})
class StandaloneDistributionHttpFlowIntegrationTests {
    private static final Path ROOT = createRoot();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Path createRoot() {
        try { return Files.createTempDirectory("hydra-standalone-it"); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Todos los almacenes del contexto completo (auditoria, diario de importaciones, metamodelos...) en el directorio temporal:
        // con los valores por defecto esta prueba escribia en ./data (el almacenamiento real cuando se ejecuta en el espacio de trabajo).
        com.example.editor.metamodel.web.MetamodelHttpTestEnvironment.register(registry, ROOT.resolve("storage"));
        registry.add("dsl.storage.create-directories", () -> "false");
        registry.add("hydra.standalone-distribution.allowed-root", ROOT::toString);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        try (var walk = Files.walk(ROOT)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Autowired private Environment environment;
    private final HttpClient client = HttpClient.newHttpClient();

    private String base() { return "http://localhost:" + environment.getProperty("local.server.port") + "/api/standalone-distributions"; }

    private HttpResponse<byte[]> post(String endpoint, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + endpoint)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String request(String directory, String productName) {
        return "{\"editorDirectory\":\"" + directory + "\",\"profile\":{\"productName\":\"" + productName
                + "\",\"version\":\"1.0.0\",\"targets\":[\"PWA\"],\"offlineEnabled\":true,"
                + "\"fileAssociations\":[\".xmi\"],\"updateChannel\":\"stable\"}}";
    }

    private static JsonNode tree(HttpResponse<byte[]> response) { return JSON.readTree(new String(response.body(), StandardCharsets.UTF_8)); }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static List<String> zipEntries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) names.add(entry.getName());
        }
        return names;
    }

    private static boolean hasDiagnostic(JsonNode report, String code) {
        for (JsonNode diagnostic : report.get("diagnostics")) if (diagnostic.get("code").toString().equals("\"" + code + "\"")) return true;
        return false;
    }

    /** Exige un error de cliente (400 INVALID_REQUEST) con el motivo, y no un 500 INTERNAL_ERROR (defecto #16 de HALLAZGOS_PRODUCCION). */
    private static void assertClientRejection(HttpResponse<byte[]> response, String reason) {
        String body = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(response.statusCode()).as(body).isEqualTo(400);
        assertThat(tree(response).get("code").asString()).isEqualTo("INVALID_REQUEST");
        assertThat(body).contains(reason).doesNotContain("INTERNAL_ERROR");
    }

    /** Planifica, materializa por HTTP y escribe la pagina de entrada, dejando un editor valido en ROOT/directory. */
    private Path materializedEditor(String directory, String productName, boolean withIndex) throws Exception {
        assertThat(post("/materialize", request(directory, productName)).statusCode()).isEqualTo(200);
        Path editor = ROOT.resolve(directory);
        if (withIndex) Files.writeString(editor.resolve("index.html"), "<main>HYDRA editor</main>");
        return editor;
    }

    @Test
    @DisplayName("publication_standalone_distribution.{pl,mt,va} / publication_author_distribution.{pv,mv}: plan, materializacion y validacion por HTTP")
    void planMaterializeAndValidateOverHttp() throws Exception {
        HttpResponse<byte[]> plan = post("/plan", request("flow-editor", "Flow Editor"));
        assertThat(plan.statusCode()).isEqualTo(200);
        String planText = new String(plan.body(), StandardCharsets.UTF_8);
        assertThat(planText).contains("public/manifest.webmanifest").contains("public/service-worker.js");
        assertThat(ROOT.resolve("flow-editor")).as("plan never writes").doesNotExist();

        HttpResponse<byte[]> materialized = post("/materialize", request("flow-editor", "Flow Editor"));
        assertThat(materialized.statusCode()).isEqualTo(200);
        assertThat(ROOT.resolve("flow-editor/public/manifest.webmanifest")).isRegularFile();
        assertThat(ROOT.resolve("flow-editor/public/service-worker.js")).isRegularFile();
        assertThat(Files.readString(ROOT.resolve("flow-editor/public/manifest.webmanifest"))).contains("\"standalone\"");
        // Sin pagina de entrada el editor aun NO es valido.
        JsonNode firstValidation = tree(materialized).get("validation");
        assertThat(firstValidation.get("valid").asBoolean()).isFalse();
        assertThat(hasDiagnostic(firstValidation, "APP_ENTRY")).isTrue();

        Files.writeString(ROOT.resolve("flow-editor/index.html"), "<main>Flow</main>");
        JsonNode validation = tree(post("/validate", request("flow-editor", "Flow Editor")));
        assertThat(validation.get("valid").asBoolean()).isTrue();
        assertThat(hasDiagnostic(validation, "VALIDATED")).isTrue();
    }

    @Test
    @DisplayName("publication_standalone_distribution.{pk} / x_generate_certify_distribute.{dist,zip} / x_project_lifecycle_overview.r: ZIP reproducible con X-HYDRA-Distribution-SHA256 correcto")
    void packageDownloadsAReproducibleZipWhoseHashHeaderMatchesTheBytes() throws Exception {
        Path editor = materializedEditor("pack-editor", "Pack Editor", true);
        Files.createDirectories(editor.resolve("node_modules/pkg"));
        Files.writeString(editor.resolve("node_modules/pkg/ignored.js"), "ignored");

        HttpResponse<byte[]> first = post("/package", request("pack-editor", "Pack Editor"));
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Content-Type").orElse("")).isEqualTo("application/zip");
        assertThat(first.headers().firstValue("Content-Disposition").orElse("")).contains("pack-editor-1.0.0.zip");
        String header = first.headers().firstValue("X-HYDRA-Distribution-SHA256").orElseThrow();
        assertThat(header).isEqualTo(sha256(first.body())).hasSize(64);
        assertThat(zipEntries(first.body())).contains("index.html", "public/manifest.webmanifest", "public/service-worker.js")
                .noneMatch(name -> name.contains("node_modules"));
        // El ZIP descargado es byte a byte el que quedo fuera de la carpeta del editor.
        assertThat(Files.readAllBytes(ROOT.resolve(".hydra-packages/pack-editor-1.0.0.zip"))).isEqualTo(first.body());

        HttpResponse<byte[]> second = post("/package", request("pack-editor", "Pack Editor"));
        assertThat(second.headers().firstValue("X-HYDRA-Distribution-SHA256").orElseThrow()).isEqualTo(header);
        assertThat(second.body()).isEqualTo(first.body());
    }

    @Test
    @DisplayName("x_generate_certify_distribute.sec / publication_standalone_distribution.pk: validacion con errores, secretos y enlaces simbolicos no se empaquetan")
    void invalidEditorsSecretsAndSymbolicLinksAreNotPackaged() throws Exception {
        // 1) Editor sin pagina de entrada: la validacion tiene errores y no se genera ZIP.
        materializedEditor("broken-editor", "Broken Editor", false);
        assertClientRejection(post("/package", request("broken-editor", "Broken Editor")), "invalid standalone distribution");
        assertThat(ROOT.resolve(".hydra-packages/broken-editor-1.0.0.zip")).doesNotExist();

        // 2) Editor valido con .env y clave privada: se rechaza el empaquetado.
        Path secret = materializedEditor("secret-editor", "Secret Editor", true);
        Files.writeString(secret.resolve(".env"), "SECRET_TOKEN=do-not-publish");
        assertClientRejection(post("/package", request("secret-editor", "Secret Editor")), "Sensitive file must not be packaged: .env");
        assertThat(ROOT.resolve(".hydra-packages/secret-editor-1.0.0.zip")).doesNotExist();
        Files.delete(secret.resolve(".env"));
        Files.writeString(secret.resolve("server.pem"), "-----BEGIN PRIVATE KEY-----");
        assertClientRejection(post("/package", request("secret-editor", "Secret Editor")), "Sensitive file must not be packaged: server.pem");
        assertThat(ROOT.resolve(".hydra-packages/secret-editor-1.0.0.zip")).doesNotExist();
        Files.delete(secret.resolve("server.pem"));
        // Control positivo: sin secretos el mismo editor SI se empaqueta.
        assertThat(post("/package", request("secret-editor", "Secret Editor")).statusCode()).isEqualTo(200);
        assertThat(ROOT.resolve(".hydra-packages/secret-editor-1.0.0.zip")).isRegularFile();
    }

    /** Parte que necesita enlaces simbolicos: se omite (SKIPPED) en hosts que no los permiten, sin restar valor al caso anterior. */
    @Test
    @DisplayName("publication_standalone_distribution.pk: un enlace simbolico dentro del editor impide el empaquetado (solo si el SO lo permite)")
    void symbolicLinkInsideTheEditorIsNotPackaged() throws Exception {
        // 3) Enlace simbolico dentro del editor (solo si el SO lo permite).
        Path linked = materializedEditor("linked-editor", "Linked Editor", true);
        Path target = Files.writeString(ROOT.resolve("outside-target.txt"), "outside");
        boolean symlink;
        try { Files.createSymbolicLink(linked.resolve("link.txt"), target); symlink = true; }
        catch (IOException | UnsupportedOperationException | SecurityException exception) { symlink = false; }
        if (symlink) {
            assertClientRejection(post("/package", request("linked-editor", "Linked Editor")), "Symbolic links are not portable");
            assertThat(ROOT.resolve(".hydra-packages/linked-editor-1.0.0.zip")).doesNotExist();
        }
        assumeTrue(symlink, "symbolic links not permitted on this host; symlink rejection not exercised");
    }

    @Test
    @DisplayName("publication_standalone_distribution.pr: perfil en blanco, editorDirectory con ../ o con enlace simbolico se rechazan")
    void blankProfileTraversalAndSymbolicEditorDirectoryAreRejected() throws Exception {
        assertThat(post("/plan", request("blank-editor", "   ")).statusCode()).isEqualTo(400);
        assertThat(post("/materialize", request("blank-editor", "")).statusCode()).isEqualTo(400);
        assertThat(ROOT.resolve("blank-editor")).doesNotExist();

        HttpResponse<byte[]> traversal = post("/materialize", request("../escaped-editor", "Escaped Editor"));
        assertThat(traversal.statusCode()).isEqualTo(400);
        assertThat(new String(traversal.body(), StandardCharsets.UTF_8)).contains("editorDirectory must be a safe relative directory");
        assertThat(ROOT.resolveSibling("escaped-editor")).doesNotExist();
        assertThat(post("/materialize", request("/absolute-editor", "Abs Editor")).statusCode()).isEqualTo(400);

        Path outside = Files.createTempDirectory("hydra-standalone-outside");
        try {
            boolean symlink;
            try { Files.createSymbolicLink(ROOT.resolve("symlinked-editor"), outside); symlink = true; }
            catch (IOException | UnsupportedOperationException | SecurityException exception) { symlink = false; }
            if (symlink) {
                HttpResponse<byte[]> response = post("/materialize", request("symlinked-editor", "Symlinked Editor"));
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("crosses a symbolic link");
                try (var list = Files.list(outside)) { assertThat(list.count()).as("nothing written through the link").isZero(); }
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("publication_standalone_distribution.{rd,pr2}: release-readiness invalida sobre fuentes, valida con dist/ compilado, y package-release-source solo entonces")
    void releaseReadinessAndReleaseSourcePackageRequireABuiltDist() throws Exception {
        Path editor = materializedEditor("release-editor", "Release Editor", true);
        Path releaseZip = ROOT.resolve(".hydra-packages/release-editor-1.0.0-release-source.zip");

        JsonNode sourcesOnly = tree(post("/release-readiness", request("release-editor", "Release Editor")));
        assertThat(sourcesOnly.get("valid").asBoolean()).isFalse();
        assertThat(hasDiagnostic(sourcesOnly, "BUILT_ENTRY")).isTrue();
        assertThat(hasDiagnostic(sourcesOnly, "BUILT_MANIFEST")).isTrue();
        assertThat(hasDiagnostic(sourcesOnly, "BUILT_WORKER")).isTrue();
        assertThat(sourcesOnly.get("metadata").get("nativeInstallerCertified").toString()).isEqualTo("\"false\"");
        assertThat(post("/package-release-source", request("release-editor", "Release Editor")).statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(releaseZip).doesNotExist();

        Files.createDirectories(editor.resolve("dist"));
        Files.writeString(editor.resolve("dist/index.html"), "<main>built</main>");
        Files.writeString(editor.resolve("dist/manifest.webmanifest"), "{\"name\":\"Release Editor\"}");
        Files.writeString(editor.resolve("dist/service-worker.js"),
                "const SHELL=['./'];self.addEventListener('install',e=>e.waitUntil(caches.open('v1')));self.addEventListener('fetch',e=>{});");

        JsonNode built = tree(post("/release-readiness", request("release-editor", "Release Editor")));
        assertThat(built.get("valid").asBoolean()).isTrue();
        assertThat(built.get("metadata").get("builtAssetsChecked").toString()).isEqualTo("\"true\"");

        HttpResponse<byte[]> download = post("/package-release-source", request("release-editor", "Release Editor"));
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("X-HYDRA-Package-Level").orElse("")).isEqualTo("RELEASE_SOURCE_NOT_NATIVE_INSTALLER");
        assertThat(download.headers().firstValue("X-HYDRA-Distribution-SHA256").orElseThrow()).isEqualTo(sha256(download.body()));
        assertThat(zipEntries(download.body())).contains("dist/index.html", "dist/service-worker.js", "public/manifest.webmanifest");
        assertThat(releaseZip).isRegularFile();
    }
}

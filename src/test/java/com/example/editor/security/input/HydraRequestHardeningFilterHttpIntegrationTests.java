package com.example.editor.security.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Filtro de endurecimiento de peticiones HTTP: Tomcat real en puerto aleatorio + {@link HttpClient} real contra
 * /api/..., con limites reducidos (URI 200, query 300, cabecera 1000, JSON 2048 B, multipart 4096 B).
 * Etapas certificadas: security_filter_sandbox.a (forma y tamano antes de cualquier controlador) y
 * x_package_and_move_capabilities.sec (rechazo de peticion de importacion sobredimensionada).
 * Oraculos independientes: codigos HTTP literales, cuerpo problem+json y ausencia/presencia de ficheros en disco
 * escritos por el POST con efecto /api/standalone-distributions/materialize.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "dsl.storage.create-directories=false", "hydra.instance-lock.enabled=false",
        "hydra.security.input.max-request-uri-characters=200", "hydra.security.input.max-query-characters=300",
        "hydra.security.input.max-header-characters=1000", "hydra.security.input.max-structured-request-bytes=2048",
        "hydra.security.input.max-multipart-request-bytes=4096",
        // HydraConfigurationValidator exige metamodel.max-file-size <= multipart maximo.
        "dsl.imports.metamodel.max-file-size-bytes=4096"})
class HydraRequestHardeningFilterHttpIntegrationTests {
    private static final Path ROOT = createRoot();

    private static Path createRoot() {
        try { return Files.createTempDirectory("hydra-filter-it"); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Todos los almacenes del contexto completo (auditoria incluida) en el directorio temporal: con los valores por defecto
        // esta prueba escribia el registro de auditoria en ./data (el almacenamiento real cuando se ejecuta en el espacio de trabajo).
        com.example.editor.metamodel.web.MetamodelHttpTestEnvironment.register(registry, ROOT.resolve("storage"));
        registry.add("dsl.storage.create-directories", () -> "false");
        registry.add("hydra.standalone-distribution.allowed-root", ROOT::toString);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        try (var walk = Files.walk(ROOT)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Autowired private Environment environment;
    private final HttpClient client = HttpClient.newHttpClient();

    private String base() { return "http://localhost:" + environment.getProperty("local.server.port"); }

    private HttpResponse<String> get(String pathAndQuery, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + pathAndQuery));
        if (headers.length > 0) builder.header(headers[0], headers[1]);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String materializeBody(String directory, String productName) {
        return "{\"editorDirectory\":\"" + directory + "\",\"profile\":{\"productName\":\"" + productName
                + "\",\"version\":\"1.0.0\",\"targets\":[\"PWA\"],\"offlineEnabled\":true,"
                + "\"fileAssociations\":[\".xmi\"],\"updateChannel\":\"stable\"}}";
    }

    @Test
    @DisplayName("security_filter_sandbox.a: una peticion normal a un endpoint inocuo pasa el filtro")
    void aNormalRequestPassesTheFilter() throws Exception {
        HttpResponse<String> response = get("/api/system/status");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().replaceAll("\\s", "")).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("security_filter_sandbox.a: URI, query, cabecera y parametro fuera de presupuesto reciben 400 problem+json del filtro")
    void oversizedLineHeadersAndControlCharactersAreRejectedWith400() throws Exception {
        HttpResponse<String> longUri = get("/api/system/status/" + "a".repeat(300));
        assertThat(longUri.statusCode()).isEqualTo(400);
        assertThat(longUri.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(longUri.body()).contains("\"code\":\"INVALID_INPUT\"").contains("\"field\":\"requestUri\"");

        HttpResponse<String> longQuery = get("/api/system/status?x=" + "a".repeat(400));
        assertThat(longQuery.statusCode()).isEqualTo(400);
        assertThat(longQuery.body()).contains("\"field\":\"query\"");

        HttpResponse<String> bigHeader = get("/api/system/status", "X-Big", "h".repeat(1500));
        assertThat(bigHeader.statusCode()).isEqualTo(400);
        assertThat(bigHeader.body()).contains("\"code\":\"INVALID_INPUT\"").contains("\"field\":\"X-Big\"");

        HttpResponse<String> control = get("/api/system/status?q=%01");
        assertThat(control.statusCode()).isEqualTo(400);
        assertThat(control.body()).contains("\"field\":\"q\"");
    }

    @Test
    @DisplayName("security_filter_sandbox.a: traversal codificado en la ruta se rechaza antes de llegar al controlador")
    void encodedTraversalInThePathIsRejectedBeforeAnyController() throws Exception {
        HttpResponse<String> encoded = get("/api/system/%2e%2e/status");
        assertThat(encoded.statusCode()).isEqualTo(400);
        // Tomcat o el filtro pueden cortar estas secuencias; la prueba del filtro en si esta en el test siguiente.
        assertThat(get("/api/system/status%00").statusCode()).isEqualTo(400);
        assertThat(get("/api/system/status%0d%0a").statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("security_filter_sandbox.a: el filtro real rechaza %00, %0d, %0a, %2e%2e, ../ y ..\\ y deja pasar una URI valida")
    void theRealFilterRejectsEveryUnsafePathSequenceAndPassesAValidOne() throws Exception {
        HydraInputSecurityProperties properties = new HydraInputSecurityProperties();
        HydraRequestHardeningFilter filter = new HydraRequestHardeningFilter(properties, new HydraInputPolicy(properties));
        for (String uri : List.of("/api/x/%00", "/api/x/%0D", "/api/x/%0a", "/api/x/%2E%2e/y", "/api/x/../y", "/api/x/..\\y")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            request.setRequestURI(uri);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).as(uri).isEqualTo(400);
            assertThat(response.getContentAsString()).as(uri).contains("\"field\":\"requestUri\"");
            assertThat(chain.getRequest()).as("controller chain must not run for %s", uri).isNull();
        }
        MockHttpServletRequest valid = new MockHttpServletRequest("GET", "/api/system/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(valid, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("security_filter_sandbox.a: un POST con efecto y cuerpo JSON mayor que el maximo recibe 413 y no escribe nada")
    void oversizedJsonBodyToAnEffectfulPostIsRejectedWith413AndNothingIsWritten() throws Exception {
        HttpResponse<String> rejected = postJson("/api/standalone-distributions/materialize",
                materializeBody("victim-editor", "P".repeat(3000)));
        assertThat(rejected.statusCode()).isEqualTo(413);
        assertThat(rejected.body()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
        assertThat(ROOT.resolve("victim-editor")).doesNotExist();

        // Control positivo: el mismo endpoint con cuerpo dentro del limite SI produce efecto en disco.
        HttpResponse<String> accepted = postJson("/api/standalone-distributions/materialize",
                materializeBody("ok-editor", "Filter Editor"));
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(ROOT.resolve("ok-editor/public/manifest.webmanifest")).isRegularFile();
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.sec: una importacion multipart de paquete de capacidades mayor que el limite recibe 413")
    void oversizedCapabilityPackageImportIsRejectedBeforeTheImportController() throws Exception {
        String boundary = "hydraBoundary7";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.hydra-cap\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[5000];
        java.util.Arrays.fill(payload, (byte) 'z');
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        body.write(head); body.write(payload); body.write(tail);

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base() + "/api/capability-packages/archives/import"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"REQUEST_TOO_LARGE\"").contains("\"field\":\"request\"");
    }
}

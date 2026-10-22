package com.example.editor.remote.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.remote.auth.provider.GitHubOAuthProvider;
import com.example.editor.remote.auth.provider.RemoteOAuthProviderRegistry;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conexion de una cuenta con un proveedor de identidad simulado en 127.0.0.1: flujo de codigo con PKCE y flujo
 * de dispositivo, con la credencial guardada cifrada en disco.
 *
 * <p>Etapas certificadas: {@code repos_connect_sync.a2}, {@code repos_oauth_connect.a2},
 * {@code repos_oauth_connect.a3}, {@code repos_oauth_connect.a4}, {@code repos_oauth_connect.r}.</p>
 *
 * <p>Colaboradores reales: servicio de autenticacion remota, adaptador de GitHub, cliente HTTP de autorizacion,
 * almacen de estados y boveda cifrada en un {@code @TempDir}. Lo unico simulado es el proveedor de identidad:
 * un servidor HTTP en 127.0.0.1 que comprueba por su cuenta el reto PKCE y el reparto de codigos. El paso de
 * tiempo del sondeo del dispositivo se reproduce adelantando la hora del siguiente sondeo del almacen de estados.</p>
 */
class RemoteOAuthLoopbackExchangeIntegrationTests {
    private static final String CLIENT_ID = "hydra-loopback-client";
    private static final String CLIENT_SECRET = "hydra-loopback-secret";
    private static final String ACCESS_TOKEN = "gho_loopbackaccess1234";
    private static final String REFRESH_TOKEN = "ghr_loopbackrefresh5678";
    private static final String CALLBACK = "http://127.0.0.1:8081/api/remote-auth/callback";

    @TempDir Path temporary;

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private IdentityProviderSimulator idp;
    private RemoteAuthProperties authProperties;
    private RemoteStorageProperties storageProperties;
    private RemoteAuthFlowStore flows;
    private RemoteAuthService service;

    @BeforeEach
    void setUp() throws Exception {
        idp = new IdentityProviderSimulator();
    }

    @AfterEach
    void tearDown() {
        idp.stop();
    }

    @Test
    @DisplayName("repos_connect_sync.a2, repos_oauth_connect.a3/a4/r: el codigo con PKCE se canjea en el servidor, la credencial se guarda cifrada y sobrevive a un reinicio")
    void theAuthorizationCodeIsExchangedOnTheServerAndTheCredentialIsStoredEncrypted() throws Exception {
        build(false);

        RemoteAuthStartResponse started = service.start(new RemoteAuthStartRequest("gh", "en"));

        assertThat(started.deviceFlow()).isFalse();
        assertThat(started.connectionId()).isEqualTo("oauth-gh");
        assertThat(started.state().length()).isGreaterThanOrEqualTo(43);
        assertThat(started.authorizationUrl()).startsWith(idp.base() + "/login/oauth/authorize?");
        Map<String, String> query = query(URI.create(started.authorizationUrl()));
        assertThat(query).containsEntry("client_id", CLIENT_ID).containsEntry("state", started.state())
                .containsEntry("redirect_uri", CALLBACK).containsEntry("scope", "repo").containsEntry("code_challenge_method", "S256");
        assertThat(query.get("code_challenge")).hasSize(43);
        idp.expectCode("code-123", query.get("code_challenge"));
        // Mientras no llega la confirmacion, la espera del navegador sigue pendiente y no hay credencial.
        assertThat(service.poll(new RemoteAuthPollRequest(started.state())).pending()).isTrue();
        assertThat(temporary.resolve("vault/oauth-gh.cred")).doesNotExist();

        RemoteAuthConnectionDescriptor connection = service.complete(started.state(), "code-123", null, null);

        assertThat(connection.connectionId()).isEqualTo("oauth-gh");
        assertThat(connection.provider()).isEqualTo(RemoteStorageProviderType.GITHUB);
        assertThat(connection.accountId()).isEqualTo("42");
        assertThat(connection.accountLabel()).isEqualTo("octocat");
        assertThat(connection.scopes()).containsExactly("repo");
        assertThat(connection.refreshable()).isTrue();
        assertThat(connection.persistent()).isTrue();
        assertThat(connection.protectionMode()).startsWith("AES256_GCM");
        assertThat(connection.expiresAt()).isBetween(Instant.now().plusSeconds(3000), Instant.now().plusSeconds(3700));
        // El proveedor recibio un unico canje con los datos esperados; el verificador PKCE lo comprobo el propio servidor simulado.
        assertThat(idp.tokenRequests()).hasSize(1);
        Map<String, String> exchange = idp.tokenRequests().get(0);
        assertThat(exchange).containsEntry("client_id", CLIENT_ID).containsEntry("client_secret", CLIENT_SECRET)
                .containsEntry("code", "code-123").containsEntry("redirect_uri", CALLBACK);
        assertThat(pkceChallenge(exchange.get("code_verifier"))).isEqualTo(query.get("code_challenge"));
        assertThat(idp.requestLines()).noneMatch(line -> line.contains(ACCESS_TOKEN) || line.contains(CLIENT_SECRET));

        // Guardado cifrado: el fichero existe, no contiene los secretos en claro y una boveda nueva lo lee.
        Path credentialFile = temporary.resolve("vault/oauth-gh.cred");
        assertThat(credentialFile).isRegularFile();
        String onDisk = new String(Files.readAllBytes(credentialFile), StandardCharsets.ISO_8859_1);
        assertThat(onDisk).doesNotContain(ACCESS_TOKEN).doesNotContain(REFRESH_TOKEN).doesNotContain("octocat");
        RemoteStorageProperties.Profile profile = storageProperties.getProfiles().get("gh");
        assertThat(service.connected("gh", profile)).isTrue();
        assertThat(service.credential("gh", profile).accessToken()).isEqualTo(ACCESS_TOKEN);
        EncryptedFileRemoteCredentialVault restarted = new EncryptedFileRemoteCredentialVault(authProperties, mapper);
        RemoteCredentialMaterial reloaded = restarted.load("oauth-gh").orElseThrow();
        assertThat(reloaded.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(reloaded.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(reloaded.accountLabel()).isEqualTo("octocat");
        // La confirmacion final que consulta el navegador no incluye secretos.
        RemoteAuthPollResponse confirmation = service.poll(new RemoteAuthPollRequest(started.state()));
        assertThat(confirmation.completed()).isTrue();
        assertThat(confirmation.connection().accountLabel()).isEqualTo("octocat");
        assertThat(confirmation.toString()).doesNotContain(ACCESS_TOKEN).doesNotContain(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("repos_oauth_connect.a3: un estado reutilizado, uno desconocido, un error del proveedor o una respuesta 400 no dejan credencial")
    void aReusedStateAProviderErrorOrAnIdentityProviderRejectionLeaveNoCredential() throws Exception {
        build(false);
        RemoteAuthStartResponse first = service.start(new RemoteAuthStartRequest("gh", "en"));
        idp.expectCode("code-123", query(URI.create(first.authorizationUrl())).get("code_challenge"));
        service.complete(first.state(), "code-123", null, null);
        assertThat(idp.tokenRequests()).hasSize(1);

        // El mismo estado no se puede usar dos veces y no provoca otro canje.
        assertThatThrownBy(() -> service.complete(first.state(), "code-123", null, null))
                .isInstanceOfSatisfying(RemoteAuthException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.FLOW_EXPIRED));
        assertThatThrownBy(() -> service.complete("never-issued-state", "code-123", null, null))
                .isInstanceOfSatisfying(RemoteAuthException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.FLOW_EXPIRED));
        assertThat(idp.tokenRequests()).hasSize(1);

        // Un codigo que el proveedor rechaza con HTTP 400: error de canje y ninguna credencial para ese perfil.
        RemoteAuthStartResponse rejected = service.start(new RemoteAuthStartRequest("gh2", "en"));
        idp.expectCode("another-code", query(URI.create(rejected.authorizationUrl())).get("code_challenge"));
        assertThatThrownBy(() -> service.complete(rejected.state(), "wrong-code", null, null))
                .isInstanceOfSatisfying(RemoteAuthException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED);
                    assertThat(error.getMessage()).contains("The code passed is incorrect or expired.");
                });
        assertThat(idp.tokenRequests()).hasSize(2);
        assertThat(temporary.resolve("vault/oauth-gh2.cred")).doesNotExist();
        RemoteStorageProperties.Profile second = storageProperties.getProfiles().get("gh2");
        assertThat(service.connected("gh2", second)).isFalse();
        assertThatThrownBy(() -> service.credential("gh2", second))
                .isInstanceOfSatisfying(RemoteAuthException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.CONNECTION_NOT_FOUND));
        // El navegador que sigue sondeando recibe el mismo fallo terminal.
        assertThatThrownBy(() -> service.poll(new RemoteAuthPollRequest(rejected.state())))
                .isInstanceOfSatisfying(RemoteAuthException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED));

        // El usuario cancela en la pagina del proveedor: no hay peticion de canje.
        RemoteAuthStartResponse cancelled = service.start(new RemoteAuthStartRequest("gh3", "en"));
        assertThatThrownBy(() -> service.complete(cancelled.state(), null, "access_denied", "The user denied the request"))
                .isInstanceOfSatisfying(RemoteAuthException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED);
                    assertThat(error.getMessage()).isEqualTo("Authorization was cancelled or denied on the provider page");
                });
        assertThat(idp.tokenRequests()).hasSize(2);
        assertThat(temporary.resolve("vault/oauth-gh3.cred")).doesNotExist();
    }

    @Test
    @DisplayName("repos_connect_sync.a2, repos_oauth_connect.a2/a4/r: en el flujo de dispositivo el sondeo respeta el intervalo, pasa de pendiente a autorizado y guarda la credencial")
    void theDeviceFlowRespectsThePollingIntervalAndMovesFromPendingToAuthorized() throws Exception {
        build(true);

        RemoteAuthStartResponse started = service.start(new RemoteAuthStartRequest("gh", "es"));

        assertThat(started.deviceFlow()).isTrue();
        assertThat(started.userCode()).isEqualTo("WDJB-MJHT");
        assertThat(started.authorizationUrl()).isEqualTo("https://github.example.org/login/device");
        assertThat(started.pollIntervalSeconds()).isEqualTo(5);
        assertThat(idp.deviceRequests()).hasSize(1);
        assertThat(idp.deviceRequests().get(0)).containsEntry("client_id", CLIENT_ID).containsEntry("scope", "repo");

        // Antes de cumplirse el intervalo no se pregunta al proveedor.
        RemoteAuthPollResponse early = service.poll(new RemoteAuthPollRequest(started.state()));
        assertThat(early.pending()).isTrue();
        assertThat(early.pollAfterSeconds()).isBetween(1, 5);
        assertThat(idp.tokenRequests()).isEmpty();

        // Pasa el intervalo: el proveedor responde "pendiente" y el servicio vuelve a esperar.
        rewind(started.state());
        RemoteAuthPollResponse pending = service.poll(new RemoteAuthPollRequest(started.state()));
        assertThat(pending.pending()).isTrue();
        assertThat(pending.pollAfterSeconds()).isGreaterThanOrEqualTo(5);
        assertThat(idp.tokenRequests()).hasSize(1);
        service.poll(new RemoteAuthPollRequest(started.state()));
        assertThat(idp.tokenRequests()).hasSize(1);
        assertThat(temporary.resolve("vault/oauth-gh.cred")).doesNotExist();

        // El usuario introduce el codigo en otro dispositivo y el siguiente sondeo ya autoriza.
        idp.deviceMode("approved");
        rewind(started.state());
        RemoteAuthPollResponse authorized = service.poll(new RemoteAuthPollRequest(started.state()));

        assertThat(authorized.completed()).isTrue();
        assertThat(authorized.pending()).isFalse();
        assertThat(authorized.connection().accountLabel()).isEqualTo("octocat");
        assertThat(authorized.connection().connectionId()).isEqualTo("oauth-gh");
        assertThat(idp.tokenRequests()).hasSize(2);
        assertThat(idp.tokenRequests().get(1)).containsEntry("client_id", CLIENT_ID).containsEntry("device_code", "dev-code-1")
                .containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        Path credentialFile = temporary.resolve("vault/oauth-gh.cred");
        assertThat(credentialFile).isRegularFile();
        assertThat(new String(Files.readAllBytes(credentialFile), StandardCharsets.ISO_8859_1)).doesNotContain(ACCESS_TOKEN);
        RemoteStorageProperties.Profile profile = storageProperties.getProfiles().get("gh");
        assertThat(service.credential("gh", profile).accessToken()).isEqualTo(ACCESS_TOKEN);
        // Un sondeo posterior repite el resultado sin volver a preguntar al proveedor.
        assertThat(service.poll(new RemoteAuthPollRequest(started.state())).completed()).isTrue();
        assertThat(idp.tokenRequests()).hasSize(2);
    }

    @Test
    @DisplayName("repos_oauth_connect.a2: si el usuario deniega o el codigo caduca, el sondeo termina con error y no se guarda nada")
    void aDeniedOrExpiredDeviceCodeEndsWithAnErrorAndStoresNothing() throws Exception {
        build(true);
        RemoteAuthStartResponse denied = service.start(new RemoteAuthStartRequest("gh", "en"));
        idp.deviceMode("denied");
        rewind(denied.state());

        assertThatThrownBy(() -> service.poll(new RemoteAuthPollRequest(denied.state())))
                .isInstanceOfSatisfying(RemoteAuthException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED);
                    assertThat(error.getMessage()).isEqualTo("External device authorization was denied");
                });
        int requests = idp.tokenRequests().size();
        assertThatThrownBy(() -> service.poll(new RemoteAuthPollRequest(denied.state())))
                .isInstanceOfSatisfying(RemoteAuthException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED));
        assertThat(idp.tokenRequests()).hasSize(requests);

        RemoteAuthStartResponse expired = service.start(new RemoteAuthStartRequest("gh2", "en"));
        idp.deviceMode("expired");
        rewind(expired.state());
        assertThatThrownBy(() -> service.poll(new RemoteAuthPollRequest(expired.state())))
                .isInstanceOfSatisfying(RemoteAuthException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(RemoteAuthException.Reason.FLOW_EXPIRED);
                    assertThat(error.getMessage()).isEqualTo("External device authorization expired");
                });
        assertThat(temporary.resolve("vault/oauth-gh.cred")).doesNotExist();
        assertThat(temporary.resolve("vault/oauth-gh2.cred")).doesNotExist();
    }

    private void build(boolean deviceFlow) {
        authProperties = new RemoteAuthProperties();
        authProperties.getVault().setRoot(temporary.resolve("vault"));
        authProperties.getVault().setAllowProtectedLocalMasterKey(true);
        RemoteAuthProperties.Provider github = authProperties.provider(RemoteStorageProviderType.GITHUB);
        github.setClientId(CLIENT_ID);
        github.setClientSecret(CLIENT_SECRET);
        github.setAuthorizationUrl(idp.base() + "/login/oauth/authorize");
        github.setDeviceAuthorizationUrl(idp.base() + "/login/device/code");
        github.setTokenUrl(idp.base() + "/login/oauth/access_token");
        github.setAccountUrl(idp.base() + "/user");
        github.setDeviceFlowPreferred(deviceFlow);
        storageProperties = new RemoteStorageProperties();
        Map<String, RemoteStorageProperties.Profile> profiles = new LinkedHashMap<>();
        for (String id : List.of("gh", "gh2", "gh3")) {
            RemoteStorageProperties.Profile profile = new RemoteStorageProperties.Profile();
            profile.setProvider(RemoteStorageProviderType.GITHUB);
            profile.setOwner("octo");
            profile.setRepository("models");
            profiles.put(id, profile);
        }
        storageProperties.setProfiles(profiles);
        RemoteOAuthProviderRegistry registry = new RemoteOAuthProviderRegistry(
                List.of(new GitHubOAuthProvider(new RemoteOAuthHttpClient(authProperties), mapper)));
        flows = new RemoteAuthFlowStore(authProperties);
        service = new RemoteAuthService(authProperties, storageProperties, registry,
                new EncryptedFileRemoteCredentialVault(authProperties, mapper), flows, new RemoteAuthOutcomeStore(authProperties));
    }

    /** Reproduce el paso del intervalo de sondeo adelantando la hora del siguiente sondeo en el almacen de estados. */
    private void rewind(String state) {
        PendingRemoteAuthFlow flow = flows.peek(state);
        flows.update(new PendingRemoteAuthFlow(flow.state(), flow.profileId(), flow.connectionId(), flow.provider(), flow.verifier(),
                flow.redirectUri(), flow.scopes(), flow.deviceFlow(), flow.deviceCode(), flow.pollIntervalSeconds(), Instant.EPOCH,
                flow.expiresAt(), flow.language()));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            int separator = pair.indexOf('=');
            result.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String pkceChallenge(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Proveedor de identidad en 127.0.0.1: reparto de codigos, canje con PKCE, flujo de dispositivo y cuenta. */
    private static final class IdentityProviderSimulator implements HttpHandler {
        private final HttpServer server;
        private final List<String> requestLines = Collections.synchronizedList(new java.util.ArrayList<>());
        private final List<Map<String, String>> tokenRequests = Collections.synchronizedList(new java.util.ArrayList<>());
        private final List<Map<String, String>> deviceRequests = Collections.synchronizedList(new java.util.ArrayList<>());
        private volatile String expectedCode = "";
        private volatile String expectedChallenge = "";
        private volatile String deviceMode = "pending";

        IdentityProviderSimulator() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
            server.createContext("/", this);
            server.start();
        }

        String base() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        void expectCode(String code, String challenge) {
            expectedCode = code;
            expectedChallenge = challenge;
        }

        void deviceMode(String mode) {
            deviceMode = mode;
        }

        List<String> requestLines() {
            return requestLines;
        }

        List<Map<String, String>> tokenRequests() {
            return tokenRequests;
        }

        List<Map<String, String>> deviceRequests() {
            return deviceRequests;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getRawPath();
                requestLines.add(exchange.getRequestMethod() + " " + path
                        + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()));
                if ("POST".equals(exchange.getRequestMethod()) && path.equals("/login/oauth/access_token")) {
                    token(exchange, form(exchange));
                } else if ("POST".equals(exchange.getRequestMethod()) && path.equals("/login/device/code")) {
                    deviceRequests.add(form(exchange));
                    json(exchange, 200, "{\"device_code\":\"dev-code-1\",\"user_code\":\"WDJB-MJHT\","
                            + "\"verification_uri\":\"https://github.example.org/login/device\",\"expires_in\":900,\"interval\":5}");
                } else if ("GET".equals(exchange.getRequestMethod()) && path.equals("/user")) {
                    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                    if (("Bearer " + ACCESS_TOKEN).equals(authorization)) {
                        json(exchange, 200, "{\"id\":42,\"login\":\"octocat\"}");
                    } else {
                        json(exchange, 401, "{\"message\":\"Bad credentials\"}");
                    }
                } else {
                    json(exchange, 404, "{\"message\":\"Not Found\"}");
                }
            } catch (RuntimeException exception) {
                json(exchange, 500, "{\"message\":\"" + exception.getClass().getSimpleName() + "\"}");
            }
        }

        private void token(HttpExchange exchange, Map<String, String> params) throws IOException {
            tokenRequests.add(params);
            if (!CLIENT_ID.equals(params.get("client_id"))) {
                json(exchange, 401, "{\"error\":\"incorrect_client_credentials\",\"error_description\":\"The client_id is invalid.\"}");
                return;
            }
            if (params.containsKey("device_code")) {
                switch (deviceMode) {
                    case "approved" -> json(exchange, 200, tokenJson());
                    case "denied" -> json(exchange, 200, "{\"error\":\"access_denied\"}");
                    case "expired" -> json(exchange, 200, "{\"error\":\"expired_token\"}");
                    default -> json(exchange, 200, "{\"error\":\"authorization_pending\",\"interval\":5}");
                }
                return;
            }
            boolean validClient = CLIENT_SECRET.equals(params.get("client_secret")) && CALLBACK.equals(params.get("redirect_uri"));
            boolean validCode = !expectedCode.isEmpty() && expectedCode.equals(params.get("code"));
            if (!validClient || !validCode) {
                json(exchange, 400, "{\"error\":\"bad_verification_code\",\"error_description\":\"The code passed is incorrect or expired.\"}");
                return;
            }
            String verifier = params.getOrDefault("code_verifier", "");
            String challenge;
            try {
                challenge = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
            if (verifier.isEmpty() || !challenge.equals(expectedChallenge)) {
                json(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed.\"}");
                return;
            }
            expectedCode = "";
            json(exchange, 200, tokenJson());
        }

        private static String tokenJson() {
            return "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"bearer\",\"scope\":\"repo\",\"expires_in\":3600,"
                    + "\"refresh_token\":\"" + REFRESH_TOKEN + "\"}";
        }

        private static Map<String, String> form(HttpExchange exchange) throws IOException {
            Map<String, String> result = new LinkedHashMap<>();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.isEmpty()) {
                for (String pair : body.split("&")) {
                    int separator = pair.indexOf('=');
                    result.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
                }
            }
            return result;
        }

        private static void json(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}

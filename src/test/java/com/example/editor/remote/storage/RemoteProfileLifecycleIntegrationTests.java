package com.example.editor.remote.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.remote.git.GitRemoteUrlResolver;
import com.example.editor.remote.git.NativeGitProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ciclo de vida de los perfiles de repositorios: crear uno por proveedor, reabrir con una instancia nueva,
 * listar, borrar, y decidir que proveedores admiten operaciones Git.
 *
 * <p>Etapas certificadas: {@code repos_author_connection.d}, {@code repos_author_connection.r},
 * {@code repos_connect_sync.a1}, {@code repos_connect_sync.d1}.</p>
 *
 * <p>Colaboradores reales: gestor de perfiles sobre un fichero de registro en un {@code @TempDir} y resolutor real
 * de direcciones Git. El oraculo lee el fichero de registro crudo y compara con literales.</p>
 */
class RemoteProfileLifecycleIntegrationTests {
    @TempDir Path temporary;

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private Path registryFile;

    @BeforeEach
    void setUp() {
        registryFile = temporary.resolve("registry").resolve("profiles.json");
    }

    @Test
    @DisplayName("repos_connect_sync.a1 + repos_author_connection.d: un perfil por proveedor se guarda con su URL base y su tamano maximo")
    void oneProfilePerProviderIsStoredWithItsBaseUrlAndMaximumSize() throws Exception {
        RemoteStorageProfileManager manager = newManager(new RemoteStorageProperties());

        for (Map.Entry<String, RemoteStorageProfileUpdateRequest> entry : sixProfiles().entrySet()) {
            RemoteStorageProfileConfiguration saved = manager.upsert(entry.getKey(), entry.getValue());
            assertThat(saved.id()).isEqualTo(entry.getKey());
            assertThat(saved.managedOverride()).isTrue();
        }

        // Oraculo: el fichero de registro crudo.
        JsonNode registry = mapper.readTree(Files.readString(registryFile));
        JsonNode profiles = registry.get("profiles");
        assertThat(profiles.size()).isEqualTo(6);
        assertThat(profiles.get("gh").get("provider").asString()).isEqualTo("GITHUB");
        assertThat(profiles.get("gh").get("baseUrl").asString()).isEqualTo("https://github.example.org/api/v3");
        assertThat(profiles.get("gh").get("maximumArtifactBytes").asLong()).isEqualTo(2048L);
        assertThat(profiles.get("gh").get("branch").asString()).isEqualTo("main");
        assertThat(profiles.get("gl").get("baseUrl").asString()).isEqualTo("https://gitlab.example.org/api/v4");
        assertThat(profiles.get("gl").get("maximumArtifactBytes").asLong()).isEqualTo(4096L);
        assertThat(profiles.get("gl").get("rootPath").asString()).isEqualTo("hydra");
        assertThat(profiles.get("db").get("maximumArtifactBytes").asLong()).isEqualTo(512L);
        assertThat(profiles.get("db").get("rootPath").asString()).isEqualTo("team/hydra");
        assertThat(profiles.get("od").get("oneDriveAppFolder").asBoolean()).isTrue();
        assertThat(profiles.get("od").get("branch").asString()).isEmpty();
        assertThat(profiles.get("gd").get("rootItemId").asString()).isEqualTo("folder-123");
        assertThat(profiles.get("gg").get("repositoryUrl").asString()).isEqualTo("https://git.example.org/acme/repo.git");
        assertThat(profiles.get("gg").get("branch").asString()).isEqualTo("main");
        assertThat(registry.get("deletedProfileIds").size()).isZero();
        // Las credenciales nunca se guardan en el registro.
        assertThat(Files.readString(registryFile).toLowerCase()).doesNotContain("token").doesNotContain("secret");
    }

    @Test
    @DisplayName("repos_author_connection.r: tras reiniciar, una instancia nueva lista los mismos perfiles y borrar uno persiste")
    void afterARestartANewInstanceListsTheSameProfilesAndDeletionPersists() throws Exception {
        RemoteStorageProfileManager first = newManager(new RemoteStorageProperties());
        sixProfiles().forEach(first::upsert);

        RemoteStorageProperties restartedProperties = new RemoteStorageProperties();
        RemoteStorageProfileManager restarted = newManager(restartedProperties);

        List<RemoteStorageProfileConfiguration> listed = restarted.configurations();
        assertThat(listed).extracting(RemoteStorageProfileConfiguration::id).containsExactly("db", "gd", "gg", "gh", "gl", "od");
        Map<String, RemoteStorageProfileConfiguration> byId = new LinkedHashMap<>();
        listed.forEach(item -> byId.put(item.id(), item));
        assertThat(byId.get("gh").displayName()).isEqualTo("GitHub Enterprise");
        assertThat(byId.get("gh").provider()).isEqualTo(RemoteStorageProviderType.GITHUB);
        assertThat(byId.get("gh").owner()).isEqualTo("acme");
        assertThat(byId.get("gh").repository()).isEqualTo("models");
        assertThat(byId.get("gh").baseUrl()).isEqualTo("https://github.example.org/api/v3");
        assertThat(byId.get("gh").maximumArtifactBytes()).isEqualTo(2048L);
        assertThat(byId.get("gl").projectId()).isEqualTo("platform/models");
        assertThat(byId.get("gl").branch()).isEqualTo("release");
        assertThat(byId.get("od").driveId()).isEqualTo("drive-9");
        assertThat(byId.get("od").oneDriveAppFolder()).isTrue();
        assertThat(byId.get("gd").rootItemId()).isEqualTo("folder-123");
        assertThat(byId.get("db").rootPath()).isEqualTo("team/hydra");
        assertThat(byId.get("gg").repositoryUrl()).isEqualTo("https://git.example.org/acme/repo.git");
        assertThat(byId.values()).allSatisfy(item -> assertThat(item.managedOverride()).isTrue());
        // El perfil en memoria no arrastra credenciales y recibe una conexion propia.
        assertThat(restartedProperties.getProfiles().get("gh").getToken()).isEmpty();
        assertThat(restartedProperties.getProfiles().get("gh").getConnectionId()).isEqualTo("oauth-gh");

        restarted.delete("db");

        JsonNode afterDelete = mapper.readTree(Files.readString(registryFile));
        assertThat(names(afterDelete.get("profiles"))).containsExactly("gd", "gg", "gh", "gl", "od");
        RemoteStorageProfileManager third = newManager(new RemoteStorageProperties());
        assertThat(third.configurations()).extracting(RemoteStorageProfileConfiguration::id).containsExactly("gd", "gg", "gh", "gl", "od");
    }

    @Test
    @DisplayName("repos_connect_sync.d1: solo tres proveedores admiten operaciones Git y la direccion se calcula con su URL base")
    void onlyThreeProvidersSupportGitOperations() throws Exception {
        RemoteStorageProperties properties = new RemoteStorageProperties();
        RemoteStorageProfileManager manager = newManager(properties);
        sixProfiles().forEach(manager::upsert);
        RemoteStorageProperties restartedProperties = new RemoteStorageProperties();
        newManager(restartedProperties);
        NativeGitProperties allowed = new NativeGitProperties();
        allowed.setAllowedHosts(Set.of("github.example.org", "gitlab.example.org", "git.example.org"));
        GitRemoteUrlResolver resolver = new GitRemoteUrlResolver(allowed);

        assertThat(resolver.resolve(restartedProperties.getProfiles().get("gh"))).isEqualTo("https://github.example.org/acme/models.git");
        assertThat(resolver.resolve(restartedProperties.getProfiles().get("gl"))).isEqualTo("https://gitlab.example.org/platform/models.git");
        assertThat(resolver.resolve(restartedProperties.getProfiles().get("gg"))).isEqualTo("https://git.example.org/acme/repo.git");
        for (String nonGit : List.of("od", "gd", "db")) {
            RemoteStorageProperties.Profile profile = restartedProperties.getProfiles().get(nonGit);
            assertThatThrownBy(() -> resolver.resolve(profile))
                    .isInstanceOfSatisfying(RemoteStorageException.class, error -> {
                        assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.INVALID_REQUEST);
                        assertThat(error.getMessage()).isEqualTo("Selected profile is not a Git repository provider");
                    });
        }
        // Un servidor Git fuera de la lista permitida del servidor se rechaza aunque el perfil sea valido.
        GitRemoteUrlResolver strict = new GitRemoteUrlResolver(new NativeGitProperties());
        assertThatThrownBy(() -> strict.resolve(restartedProperties.getProfiles().get("gh")))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getMessage()).contains("not in the server allowlist"));
    }

    @Test
    @DisplayName("repos_connect_sync.a1 + repos_author_connection.r: los perfiles invalidos o excesivos se rechazan y el registro no cambia")
    void invalidOrExcessiveProfilesAreRejectedAndTheRegistryIsUntouched() throws Exception {
        RemoteStorageProperties properties = new RemoteStorageProperties();
        properties.setMaximumManagedProfiles(2);
        RemoteStorageProfileManager manager = newManager(properties);
        Map<String, RemoteStorageProfileUpdateRequest> all = sixProfiles();
        manager.upsert("gh", all.get("gh"));
        manager.upsert("gl", all.get("gl"));
        byte[] before = Files.readAllBytes(registryFile);

        assertThatThrownBy(() -> manager.upsert("od", all.get("od")))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.CAPACITY_EXCEEDED));
        assertThatThrownBy(() -> manager.upsert("bad id!", all.get("gh")))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.INVALID_REQUEST));
        RemoteStorageProfileUpdateRequest githubWithoutRepository = request("Broken", RemoteStorageProviderType.GITHUB, "", "", "", "", "", "",
                false, "", "", "HYDRA", 0L);
        assertThatThrownBy(() -> manager.upsert("gh", githubWithoutRepository))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.INVALID_REQUEST));
        assertThatThrownBy(() -> manager.delete("does-not-exist"))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.PROFILE_NOT_FOUND));

        assertThat(Files.readAllBytes(registryFile)).isEqualTo(before);
        // Reemplazar un perfil existente si cabe y actualiza su valor.
        manager.upsert("gh", request("GitHub renamed", RemoteStorageProviderType.GITHUB, "", "acme", "models", "develop", "", "",
                false, "", "", "HYDRA", 99L));
        JsonNode updated = mapper.readTree(Files.readString(registryFile)).get("profiles").get("gh");
        assertThat(updated.get("displayName").asString()).isEqualTo("GitHub renamed");
        assertThat(updated.get("branch").asString()).isEqualTo("develop");
        assertThat(updated.get("maximumArtifactBytes").asLong()).isEqualTo(99L);
    }

    private RemoteStorageProfileManager newManager(RemoteStorageProperties properties) {
        properties.setProfileRegistryFile(registryFile);
        RemoteStorageProfileManager manager = new RemoteStorageProfileManager(properties, mapper);
        manager.initialize();
        return manager;
    }

    private static Map<String, RemoteStorageProfileUpdateRequest> sixProfiles() {
        Map<String, RemoteStorageProfileUpdateRequest> result = new LinkedHashMap<>();
        result.put("gh", request("GitHub Enterprise", RemoteStorageProviderType.GITHUB, "https://github.example.org/api/v3",
                "acme", "models", "", "", "", false, "", "", "hydra/assets", 2048L));
        result.put("gl", request("GitLab", RemoteStorageProviderType.GITLAB, "https://gitlab.example.org/api/v4",
                "", "", "release", "platform/models", "", false, "", "", "/hydra/", 4096L));
        result.put("gd", request("Google Drive", RemoteStorageProviderType.GOOGLE_DRIVE, "",
                "", "", "x", "", "", false, "", "folder-123", "", 8192L));
        result.put("od", request("OneDrive", RemoteStorageProviderType.ONEDRIVE, "",
                "", "", "", "", "drive-9", true, "", "", "HYDRA", 1024L));
        result.put("db", request("Dropbox", RemoteStorageProviderType.DROPBOX, "",
                "", "", "", "", "", false, "", "", "team/hydra", 512L));
        result.put("gg", request("Generic Git", RemoteStorageProviderType.GENERIC_GIT, "",
                "", "", "", "", "", false, "https://git.example.org/acme/repo.git", "", "", 256L));
        return result;
    }

    private static RemoteStorageProfileUpdateRequest request(String displayName, RemoteStorageProviderType provider, String baseUrl,
            String owner, String repository, String branch, String projectId, String driveId, boolean oneDriveAppFolder,
            String repositoryUrl, String rootItemId, String rootPath, long maximumArtifactBytes) {
        return new RemoteStorageProfileUpdateRequest(displayName, provider, true, baseUrl, owner, repository, branch, projectId,
                driveId, oneDriveAppFolder, repositoryUrl, rootItemId, "", rootPath, "", maximumArtifactBytes);
    }

    private static Set<String> names(JsonNode object) {
        Set<String> result = new TreeSet<>();
        object.properties().forEach(entry -> result.add(entry.getKey()));
        return result;
    }
}

package com.example.editor.remote.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.project.CreateLocalProjectRequest;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectStorageTestFixture;
import com.example.editor.remote.storage.ConfiguredRemoteStorageCredentialResolver;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidencia FUERTE real, sin mocks, de que operar con Git ejecuta de verdad el binario git real
 * sobre el proyecto activo: cubre directamente la etapa "a3" (Operar con Git: "Fetch, pull, push,
 * commit, rama, checkout o merge") de repos_connect_sync para el subconjunto de operaciones que no
 * requieren red ni credenciales (init, commit, crear rama, checkout, estado). Fetch/pull/push
 * genuinamente necesitan un repositorio remoto real y credenciales reales y quedan fuera de esta
 * prueba a proposito, documentados como abiertos en vez de simulados. El segundo caso cubre ademas
 * "a" (ver arbol de ramas real), "d" (un merge real que deja un conflicto real), "f" (ver y
 * resolver ese conflicto real en 4 paneles) y "r" (repositorio actualizado tras completar el
 * merge) de repos_author_git -- un merge local entre dos ramas reales que modifican la misma linea
 * produce un conflicto real sin red ni credenciales.
 */
class RemoteGitWorkspaceServiceLocalOperationsIntegrationTests {

    @TempDir
    Path temporary;

    private RemoteGitWorkspaceService service;
    private String profileId;
    private Path repositoryRoot;

    @BeforeEach
    void setUp() {
        Path parentDirectory = temporary.resolve("workspace");
        var storageBackend = ProjectStorageTestFixture.storage(temporary.resolve("registry"));
        ProjectApplicationService projects = new ProjectApplicationService(storageBackend);
        var session = storageBackend.create(new CreateLocalProjectRequest(
                parentDirectory.toString(), "people", "People", "", false));
        repositoryRoot = Path.of(session.directory());

        NativeGitProperties gitProperties = new NativeGitProperties();
        NativeGitRunner git = new NativeGitRunner(gitProperties);

        RemoteStorageProperties storage = new RemoteStorageProperties();
        RemoteStorageProperties.Profile profile = new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITHUB);
        profile.setOwner("octocat");
        profile.setRepository("hello-world");
        profile.setBranch("main");
        Map<String, RemoteStorageProperties.Profile> profiles = new LinkedHashMap<>();
        profileId = "github-profile";
        profiles.put(profileId, profile);
        storage.setProfiles(profiles);

        service = new RemoteGitWorkspaceService(projects, storage, new ConfiguredRemoteStorageCredentialResolver(),
                git, gitProperties, new GitRemoteUrlResolver(gitProperties));
    }

    private RemoteGitOperationRequest request(String branch, String sourceBranch, String commitMessage) {
        return new RemoteGitOperationRequest(profileId, branch, sourceBranch, commitMessage, null);
    }

    @Test
    void localGitOperationsRunARealRepositoryEndToEndWithoutAnyNetworkOrCredentials() throws Exception {
        // init: crea de verdad el repositorio real en disco y configura el remoto sin red.
        RemoteGitOperationResult initResult = service.execute(RemoteGitOperation.INIT, request("main", null, null));
        assertThat(initResult.initialized()).isTrue();
        assertThat(repositoryRoot.resolve(".git")).isDirectory();

        // Identidad local real necesaria para que un commit real tenga autor (sin tocar la
        // configuracion global del usuario, solo la de este repositorio aislado).
        runGitConfig(repositoryRoot, "user.email", "test@example.invalid");
        runGitConfig(repositoryRoot, "user.name", "HYDRA Test");

        // Fichero real del proyecto, escrito directamente en el directorio real del repositorio.
        Files.writeString(repositoryRoot.resolve("README.md"), "# People project\n");

        // commit: un commit real sobre el fichero real.
        RemoteGitOperationResult commitResult = service.execute(RemoteGitOperation.COMMIT_ALL,
                request(null, null, "Initial commit"));
        assertThat(commitResult.changedFiles()).isEmpty();

        // crear rama real y volver a main: cambia de verdad el HEAD del repositorio real.
        RemoteGitOperationResult branchResult = service.execute(RemoteGitOperation.CREATE_BRANCH,
                request("feature/real-branch", null, null));
        assertThat(branchResult.currentBranch()).isEqualTo("feature/real-branch");

        RemoteGitOperationResult checkoutResult = service.execute(RemoteGitOperation.CHECKOUT,
                request("main", null, null));
        assertThat(checkoutResult.currentBranch()).isEqualTo("main");

        // status: un informe real de estado limpio tras el commit.
        RemoteGitOperationResult statusResult = service.execute(RemoteGitOperation.STATUS, request(null, null, null));
        assertThat(statusResult.initialized()).isTrue();
        assertThat(statusResult.changedFiles()).isEmpty();
    }

    private void runGitConfig(Path repositoryRoot, String key, String value) {
        NativeGitRunner git = new NativeGitRunner(new NativeGitProperties());
        NativeGitRunner.Result result = git.run(repositoryRoot, java.util.List.of("config", key, value));
        assertThat(result.successful()).isTrue();
    }

    @Test
    void mergingTwoRealBranchesWithAGenuineConflictCanBeViewedResolvedAndCompletedWithoutAnyNetworkOrCredentials() throws Exception {
        service.execute(RemoteGitOperation.INIT, request("main", null, null));
        runGitConfig(repositoryRoot, "user.email", "test@example.invalid");
        runGitConfig(repositoryRoot, "user.name", "HYDRA Test");

        Files.writeString(repositoryRoot.resolve("README.md"), "Original\n");
        service.execute(RemoteGitOperation.COMMIT_ALL, request(null, null, "Initial commit"));

        // Dos ramas reales que modifican la misma linea de verdad, para producir un conflicto
        // real de Git, no uno simulado.
        service.execute(RemoteGitOperation.CREATE_BRANCH, request("feature/conflict", null, null));
        Files.writeString(repositoryRoot.resolve("README.md"), "Changed on feature\n");
        service.execute(RemoteGitOperation.COMMIT_ALL, request(null, null, "Change on feature branch"));

        service.execute(RemoteGitOperation.CHECKOUT, request("main", null, null));
        Files.writeString(repositoryRoot.resolve("README.md"), "Changed on main\n");
        service.execute(RemoteGitOperation.COMMIT_ALL, request(null, null, "Change on main branch"));

        // "a": el arbol de ramas real refleja la rama actual y las ramas reales ya creadas.
        RemoteGitRepositoryTree tree = service.repositoryTree(profileId, null);
        assertThat(tree.initialized()).isTrue();
        assertThat(tree.currentBranch()).isEqualTo("main");
        assertThat(tree.groups()).isNotEmpty();

        // "d": un merge real entre las dos ramas reales deja un conflicto real -- "sourceBranch"
        // es exactamente el parametro indicado en "c".
        RemoteGitOperationResult mergeResult = service.execute(RemoteGitOperation.MERGE,
                request(null, "feature/conflict", null));
        assertThat(mergeResult.success()).isFalse();
        assertThat(mergeResult.mergeInProgress()).isTrue();
        assertThat(mergeResult.conflictedFiles()).containsExactly("README.md");

        // "f": el fichero real en conflicto se ve en sus 4 versiones reales y se resuelve de
        // verdad escribiendo y preparando el contenido resuelto.
        RemoteGitConflictFileView view = service.conflictView(profileId, "README.md");
        assertThat(view.mergeInProgress()).isTrue();
        assertThat(view.localContent()).contains("Changed on main");
        assertThat(view.remoteContent()).contains("Changed on feature");

        RemoteGitConflictResolutionResult resolution = service.resolveConflict(
                new RemoteGitConflictResolutionRequest(profileId, "README.md", "Resolved together\n", true));
        assertThat(resolution.staged()).isTrue();
        assertThat(resolution.remainingConflicts()).isEmpty();

        // "r": completar el merge con un commit real deja el repositorio real actualizado y
        // limpio, con el conflicto realmente resuelto.
        RemoteGitOperationResult completed = service.execute(RemoteGitOperation.COMMIT_ALL,
                request(null, null, "Merge feature/conflict"));
        assertThat(completed.changedFiles()).isEmpty();
        assertThat(Files.readString(repositoryRoot.resolve("README.md"))).isEqualTo("Resolved together\n");

        RemoteGitRepositoryTree afterMerge = service.repositoryTree(profileId, null);
        assertThat(afterMerge.mergeInProgress()).isFalse();
    }
}

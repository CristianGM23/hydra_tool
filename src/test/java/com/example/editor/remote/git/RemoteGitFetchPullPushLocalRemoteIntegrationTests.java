package com.example.editor.remote.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.project.CreateLocalProjectRequest;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectStorageTestFixture;
import com.example.editor.remote.storage.ConfiguredRemoteStorageCredentialResolver;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/**
 * Traer, fusionar y enviar cambios con Git real contra un remoto local sin red: ver el arbol de ramas local y remoto,
 * mover commits en ambos sentidos, no forzar nunca y no resolver conflictos en silencio.
 *
 * <p>Etapa certificada (directa): {@code repos_author_git.a}. Relacionadas sin demostrar:
 * {@code x_remote_repository_exchange.g1}, {@code repos_author_git.b} (elegir la operacion es interfaz: aqui se ejecutan
 * traer, fusionar y enviar) y {@code x_remote_repository_exchange.d3} (solo se prueban las ramas de conflicto y de todo
 * correcto; la de version distinta de la transferencia de archivos no se ejercita aqui).</p>
 *
 * <p>Colaboradores reales: servicio de operaciones Git, ejecutor del binario git y proyecto local en un {@code @TempDir}.
 * El remoto es un repositorio vacio local; el perfil apunta al nombre de GitHub y la configuracion local del repositorio
 * lo redirige a esa carpeta, de modo que no hay red ni credenciales reales. Oraculo: git se ejecuta aparte sobre el
 * repositorio remoto y sobre un segundo clon para leer identificadores y contenidos.</p>
 */
class RemoteGitFetchPullPushLocalRemoteIntegrationTests {
    private static final String PROFILE = "github-profile";
    private static final String REMOTE = "hydra-github-profile";

    @TempDir
    Path temporary;

    private NativeGitRunner git;
    private RemoteGitWorkspaceService service;
    private Path repositoryRoot;
    private Path bare;
    private Path other;

    @BeforeEach
    void setUp() throws Exception {
        var storageBackend = ProjectStorageTestFixture.storage(temporary.resolve("registry"));
        ProjectApplicationService projects = new ProjectApplicationService(storageBackend);
        var session = storageBackend.create(new CreateLocalProjectRequest(
                temporary.resolve("workspace").toString(), "people", "People", "", false));
        repositoryRoot = Path.of(session.directory());

        NativeGitProperties gitProperties = new NativeGitProperties();
        git = new NativeGitRunner(gitProperties);
        RemoteStorageProperties storage = new RemoteStorageProperties();
        RemoteStorageProperties.Profile profile = new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITHUB);
        profile.setOwner("octocat");
        profile.setRepository("hello-world");
        profile.setBranch("main");
        profile.setToken("local-test-token");
        Map<String, RemoteStorageProperties.Profile> profiles = new LinkedHashMap<>();
        profiles.put(PROFILE, profile);
        storage.setProfiles(profiles);
        service = new RemoteGitWorkspaceService(projects, storage, new ConfiguredRemoteStorageCredentialResolver(),
                git, gitProperties, new GitRemoteUrlResolver(gitProperties));

        // Remoto local vacio y segundo clon para simular a otra persona.
        bare = Files.createDirectories(temporary.resolve("remote-repo"));
        other = temporary.resolve("other");
        ok(bare, "init", "--bare");
        ok(bare, "symbolic-ref", "HEAD", "refs/heads/main");

        service.execute(RemoteGitOperation.INIT, request("main", null, null, null));
        ok(repositoryRoot, "config", "user.email", "test@example.invalid");
        ok(repositoryRoot, "config", "user.name", "HYDRA Test");
        // El perfil apunta a GitHub; esta reescritura local de la direccion lleva fetch, pull y push a la carpeta remota.
        ok(repositoryRoot, "config", "url." + slash(bare) + ".insteadOf", "https://github.com/octocat/hello-world.git");
    }

    @Test
    @DisplayName("repos_author_git.a: tras enviar y traer, el arbol muestra las ramas locales y remotas con sus commits recientes y el estado respecto al remoto")
    void afterPushingAndFetchingTheTreeShowsLocalAndRemoteBranchesWithRecentCommits() throws Exception {
        commit("README.md", "one\n", "first");
        RemoteGitOperationResult pushed = service.execute(RemoteGitOperation.PUSH, request("main", null, null, null));
        assertThat(pushed.success()).isTrue();
        String first = ok(repositoryRoot, "rev-parse", "HEAD");
        assertThat(ok(bare, "rev-parse", "refs/heads/main")).isEqualTo(first);
        assertThat(ok(bare, "show", "main:README.md")).isEqualTo("one");
        assertThat(pushed.ahead()).isZero();
        assertThat(pushed.behind()).isZero();

        String second = pushFromAnotherClone("other.txt", "from other\n", "other work");
        assertThat(ok(bare, "rev-parse", "refs/heads/main")).isEqualTo(second).isNotEqualTo(first);

        RemoteGitOperationResult fetched = service.execute(RemoteGitOperation.FETCH, request("main", null, null, null));
        assertThat(fetched.success()).isTrue();
        assertThat(fetched.ahead()).isZero();
        assertThat(fetched.behind()).isEqualTo(1);
        assertThat(ok(repositoryRoot, "rev-parse", "refs/remotes/" + REMOTE + "/main")).isEqualTo(second);
        assertThat(ok(repositoryRoot, "rev-parse", "HEAD")).as("traer no toca la rama actual").isEqualTo(first);
        assertThat(repositoryRoot.resolve("other.txt")).doesNotExist();

        RemoteGitRepositoryTree tree = service.repositoryTree(PROFILE, null);
        assertThat(tree.initialized()).isTrue();
        assertThat(tree.currentBranch()).isEqualTo("main");
        assertThat(tree.mergeInProgress()).isFalse();
        RemoteGitBranchNode local = branch(tree, "local", "main");
        assertThat(local.local()).isTrue();
        assertThat(local.current()).isTrue();
        assertThat(local.upstream()).isEqualTo(REMOTE + "/main");
        assertThat(local.behind()).as("local node: " + local).isEqualTo(1);
        assertThat(local.commits()).extracting(RemoteGitCommitNode::subject).containsExactly("first");
        assertThat(local.commits().get(0).id()).isEqualTo(first);
        RemoteGitBranchNode remote = branch(tree, "remote", "main");
        assertThat(remote.remote()).isTrue();
        assertThat(remote.fullName()).isEqualTo(REMOTE + "/main");
        assertThat(remote.lastCommitSubject()).isEqualTo("other work");
        assertThat(remote.commits()).extracting(RemoteGitCommitNode::subject).containsExactly("other work", "first");
        assertThat(remote.commits().get(0).id()).isEqualTo(second);
        assertThat(remote.commits().get(0).parentIds()).containsExactly(first);
    }

    @Test
    @DisplayName("x_remote_repository_exchange.g1: traer, fusionar y enviar mueven commits reales y un envio con historia divergente se rechaza sin pisar el remoto")
    void pullMovesRealCommitsAndADivergentPushIsRejectedWithoutOverwritingTheRemote() throws Exception {
        commit("README.md", "one\n", "first");
        service.execute(RemoteGitOperation.PUSH, request("main", null, null, null));
        String second = pushFromAnotherClone("other.txt", "from other\n", "other work");
        service.execute(RemoteGitOperation.FETCH, request("main", null, null, null));

        RemoteGitOperationResult pulled = service.execute(RemoteGitOperation.PULL, request("main", null, null, null));

        assertThat(pulled.success()).isTrue();
        assertThat(ok(repositoryRoot, "rev-parse", "HEAD")).isEqualTo(second);
        assertThat(read(repositoryRoot.resolve("other.txt"))).isEqualTo("from other\n");
        assertThat(pulled.behind()).isZero();
        assertThat(pulled.ahead()).isZero();

        // Historia divergente: el envio falla, el remoto conserva lo de la otra persona y no existe ninguna operacion de forzar ni reiniciar.
        commit("local.txt", "local\n", "local divergent");
        String third = pushFromAnotherClone("other2.txt", "x\n", "other divergent");
        assertThatThrownBy(() -> service.execute(RemoteGitOperation.PUSH, request("main", null, null, null)))
                .isInstanceOfSatisfying(RemoteStorageException.class,
                        error -> assertThat(error.getReason()).isEqualTo(RemoteStorageException.Reason.REMOTE_ERROR));
        assertThat(ok(bare, "rev-parse", "refs/heads/main")).isEqualTo(third);
        assertThat(Arrays.stream(RemoteGitOperation.values()).map(Enum::name)).containsExactlyInAnyOrder(
                "STATUS", "INIT", "FETCH", "PULL", "PUSH", "MERGE", "ABORT_MERGE", "COMMIT_ALL", "CREATE_BRANCH", "CHECKOUT");
        assertThat(Arrays.stream(RemoteGitPullStrategy.values()).map(Enum::name)).containsExactly("FF_ONLY", "MERGE", "REBASE");
    }

    @Test
    @DisplayName("x_remote_repository_exchange.d3: sin cambios todo va bien; con historias divergentes el conflicto se muestra y nunca se resuelve en silencio")
    void withoutChangesAllIsFineAndWithDivergentHistoriesTheConflictIsShownAndNeverResolvedSilently() throws Exception {
        commit("README.md", "base\n", "base");
        service.execute(RemoteGitOperation.PUSH, request("main", null, null, null));

        // Todo correcto: nada nuevo que traer.
        RemoteGitOperationResult upToDate = service.execute(RemoteGitOperation.PULL, request("main", null, null, null));
        assertThat(upToDate.success()).isTrue();
        assertThat(upToDate.conflictedFiles()).isEmpty();
        assertThat(upToDate.mergeInProgress()).isFalse();
        assertThat(upToDate.behind()).isZero();

        // Las dos partes cambian la misma linea.
        pushFromAnotherClone("README.md", "remote change\n", "remote edit");
        commit("README.md", "local change\n", "local edit");
        String localHead = ok(repositoryRoot, "rev-parse", "HEAD");

        // Con la estrategia por defecto (solo avance) no se fusiona nada y el fallo es explicito.
        assertThatThrownBy(() -> service.execute(RemoteGitOperation.PULL, request("main", null, null, null)))
                .isInstanceOf(RemoteStorageException.class).hasMessageContaining("Git pull failed");
        assertThat(ok(repositoryRoot, "rev-parse", "HEAD")).isEqualTo(localHead);
        assertThat(service.repositoryTree(PROFILE, null).mergeInProgress()).isFalse();
        assertThat(read(repositoryRoot.resolve("README.md"))).isEqualTo("local change\n");

        // Con fusion el conflicto se informa y el fichero queda con las dos versiones marcadas.
        RemoteGitOperationResult conflict = service.execute(RemoteGitOperation.PULL,
                request("main", null, null, RemoteGitPullStrategy.MERGE));
        assertThat(conflict.success()).isFalse();
        assertThat(conflict.mergeInProgress()).isTrue();
        assertThat(conflict.conflictedFiles()).containsExactly("README.md");
        String marked = read(repositoryRoot.resolve("README.md"));
        assertThat(marked).contains("<<<<<<<", "=======", ">>>>>>>", "local change", "remote change");

        // Cancelar la fusion devuelve el proyecto a su estado anterior.
        RemoteGitOperationResult aborted = service.execute(RemoteGitOperation.ABORT_MERGE, request(null, null, null, null));
        assertThat(aborted.mergeInProgress()).isFalse();
        assertThat(aborted.conflictedFiles()).isEmpty();
        assertThat(ok(repositoryRoot, "rev-parse", "HEAD")).isEqualTo(localHead);
        assertThat(read(repositoryRoot.resolve("README.md"))).isEqualTo("local change\n");
    }

    private RemoteGitOperationRequest request(String branch, String sourceBranch, String message, RemoteGitPullStrategy strategy) {
        return new RemoteGitOperationRequest(PROFILE, branch, sourceBranch, message, strategy);
    }

    private void commit(String file, String content, String message) throws Exception {
        Files.writeString(repositoryRoot.resolve(file), content);
        service.execute(RemoteGitOperation.COMMIT_ALL, request(null, null, message, null));
    }

    /** Otra persona clona el remoto, hace un commit y lo envia; devuelve el identificador del commit enviado. */
    private String pushFromAnotherClone(String file, String content, String message) throws Exception {
        if (!Files.isDirectory(other)) {
            ok(temporary, "clone", slash(bare), slash(other));
            ok(other, "config", "user.email", "other@example.invalid");
            ok(other, "config", "user.name", "Other Person");
        }
        Files.writeString(other.resolve(file), content);
        ok(other, "add", "-A");
        ok(other, "commit", "-m", message);
        ok(other, "push", "origin", "HEAD:main");
        return ok(other, "rev-parse", "HEAD");
    }

    private String ok(Path directory, String... arguments) {
        NativeGitRunner.Result result = git.run(directory, List.of(arguments));
        assertThat(result.successful()).as(String.join(" ", arguments) + " -> " + result.output()).isTrue();
        return result.output();
    }

    private static String slash(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static RemoteGitBranchNode branch(RemoteGitRepositoryTree tree, String groupId, String name) {
        String known = tree.groups().stream().map(value -> value.id() + value.branches().stream().map(RemoteGitBranchNode::name).toList()).toList().toString();
        RemoteGitBranchGroup group = tree.groups().stream().filter(value -> value.id().equals(groupId)).findFirst()
                .orElseThrow(() -> new AssertionError("no group " + groupId + " in " + known));
        return group.branches().stream().filter(value -> value.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no branch " + name + " in " + known));
    }
}

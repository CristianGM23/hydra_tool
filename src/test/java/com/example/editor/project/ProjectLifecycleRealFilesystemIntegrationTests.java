package com.example.editor.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ciclo de vida de un proyecto sobre un sistema de ficheros real: crear o continuar el reciente,
 * abrir el proyecto destino, activar areas y reabrir tras reiniciar (indice persistido).
 *
 * <p>Etapas certificadas: {@code x_project_lifecycle_overview.n1}, {@code x_project_lifecycle_overview.ar},
 * {@code x_package_and_move_capabilities.t0}.</p>
 *
 * <p>Colaboradores reales: almacenamiento local de proyectos (con el servicio de indice real) sobre un
 * {@code @TempDir}. El oraculo lee del disco el descriptor, el fichero de indice y recalcula el SHA-256 de cada fichero.</p>
 */
class ProjectLifecycleRealFilesystemIntegrationTests {
    @TempDir Path temporary;

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("x_project_lifecycle_overview.n1: sin proyecto reciente se crea uno nuevo con su estructura e indice en disco")
    void withoutRecentProjectANewOneIsCreatedWithItsTreeAndIndex() throws Exception {
        ProjectApplicationService projects = projects();
        assertThat(projects.session().active()).isFalse();
        assertThat(projects.recentProjects()).isEmpty();

        ProjectSession created = projects.create(new CreateLocalProjectRequest(workspace().toString(), "alpha", "Alpha Project", "", false));
        Path root = Path.of(created.directory());

        // Estructura fisica creada en disco (literales).
        assertThat(root.resolve("modeling-project.json")).isRegularFile();
        assertThat(root.resolve("README.md")).isRegularFile();
        assertThat(root.resolve(".modeling/settings.json")).isRegularFile();
        assertThat(root.resolve(".modeling/session.json")).isRegularFile();
        assertThat(root.resolve("metamodels")).isDirectory();
        assertThat(root.resolve("events")).isDirectory();
        assertThat(root.resolve("generated")).isDirectory();
        JsonNode descriptor = json.readTree(Files.readString(root.resolve("modeling-project.json")));
        assertThat(descriptor.get("enabledCapabilities").size()).isEqualTo(ProjectCapability.values().length);
        assertThat(descriptor.get("id").asString()).isEqualTo("alpha");

        // Indice persistido: cada fichero listado con el SHA-256 que calculamos nosotros.
        JsonNode index = json.readTree(Files.readString(root.resolve(".modeling/project-index.json")));
        assertThat(index.get("projectId").asString()).isEqualTo("alpha");
        List<String> indexed = new ArrayList<>();
        for (JsonNode artifact : index.get("artifacts")) {
            String relative = artifact.get("relativePath").asString();
            indexed.add(relative);
            assertThat(artifact.get("etag").asString()).as(relative).isEqualTo(sha256Etag(root.resolve(relative)));
        }
        assertThat(indexed).contains("modeling-project.json", "README.md", ".modeling/settings.json", ".modeling/session.json")
                .doesNotContain(".modeling/project-index.json");

        // Arbol logico con todas las areas del proyecto completo.
        List<String> groups = names(projects.tree(ProjectTreeView.LOGICAL).children());
        assertThat(groups).hasSize(24).contains("Metamodels", "Grammars", "Transformations", "Validations", "Event flows", "Tests");
        assertThat(projects.recentProjects()).hasSize(1);
        assertThat(projects.recentProjects().get(0).active()).isTrue();
    }

    @Test
    @DisplayName("x_project_lifecycle_overview.n1: con un proyecto activo reciente, un reinicio lo continua sin crear otro")
    void withActiveRecentProjectARestartContinuesItWithoutCreatingAnother() throws Exception {
        ProjectApplicationService first = projects();
        ProjectSession created = first.create(new CreateLocalProjectRequest(workspace().toString(), "alpha", "Alpha Project", "", false));
        first.saveTabs(new ProjectTabSessionRequest(List.of("metamodels/people.metamodel.json")));

        ProjectApplicationService restarted = projects();

        ProjectSession continued = restarted.session();
        assertThat(continued.active()).isTrue();
        assertThat(continued.project().id()).isEqualTo("alpha");
        assertThat(continued.directory()).isEqualTo(created.directory());
        assertThat(continued.restoredTabs()).containsExactly("metamodels/people.metamodel.json");
        try (Stream<Path> children = Files.list(workspace())) {
            assertThat(children.map(path -> path.getFileName().toString()).toList()).containsExactly("Alpha-Project");
        }
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.t0: abrir el proyecto destino exige activar sus areas; abrir una carpeta sin proyecto se rechaza")
    void openingTheTargetProjectShowsOnlyItsActiveAreasAndRejectsAFolderWithoutProject() throws Exception {
        ProjectApplicationService projects = projects();
        projects.create(new CreateLocalProjectRequest(workspace().toString(), "alpha", "Alpha Project", "", false,
                ProjectPreset.EMPTY, Set.of(ProjectCapability.VALIDATIONS), ProjectExperienceMode.FREE));
        ProjectSession target = projects.create(new CreateLocalProjectRequest(workspace().toString(), "beta", "Beta Project", "", false,
                ProjectPreset.EMPTY, Set.of(), ProjectExperienceMode.FREE));
        Path betaRoot = Path.of(target.directory());

        // Origen: sus areas activas se ven.
        projects.open(new OpenLocalProjectRequest(workspace().resolve("Alpha-Project").toString(), false, null));
        assertThat(projects.session().project().id()).isEqualTo("alpha");
        assertThat(names(projects.tree(ProjectTreeView.LOGICAL).children())).containsExactly("Expressions", "Validations", "Tests");

        // Destino: sin areas activas no hay ningun grupo de trabajo.
        projects.open(new OpenLocalProjectRequest(betaRoot.toString(), false, null));
        assertThat(projects.session().project().id()).isEqualTo("beta");
        assertThat(names(projects.tree(ProjectTreeView.LOGICAL).children())).isEmpty();

        // Tras activar la capacidad necesaria el grupo aparece y su carpeta existe en disco.
        projects.configure(new ProjectConfigurationRequest(Set.of(ProjectCapability.VALIDATIONS), null, null));
        assertThat(names(projects.tree(ProjectTreeView.LOGICAL).children())).containsExactly("Expressions", "Validations", "Tests");
        assertThat(betaRoot.resolve("behavior")).isDirectory();

        // Una carpeta que no es proyecto se rechaza y la sesion activa no cambia.
        Path notAProject = Files.createDirectories(temporary.resolve("plain-folder"));
        assertThatThrownBy(() -> projects.open(new OpenLocalProjectRequest(notAProject.toString(), false, null)))
                .isInstanceOfSatisfying(ProjectException.class,
                        error -> assertThat(error.getReason()).isEqualTo(ProjectException.Reason.NOT_FOUND));
        assertThat(projects.session().project().id()).isEqualTo("beta");
    }

    @Test
    @DisplayName("x_project_lifecycle_overview.ar: activar areas recalcula el arbol y el indice, y todo se mantiene al reiniciar y reabrir")
    void activatingAreasPersistsDescriptorAndIndexAndSurvivesARestart() throws Exception {
        ProjectApplicationService first = projects();
        ProjectSession created = first.create(new CreateLocalProjectRequest(workspace().toString(), "beta", "Beta Project", "", false,
                ProjectPreset.EMPTY, Set.of(), ProjectExperienceMode.GUIDED));
        Path root = Path.of(created.directory());

        first.configure(new ProjectConfigurationRequest(Set.of(ProjectCapability.TEXTUAL_MODELING), ProjectExperienceMode.FREE, null));
        List<String> before = names(first.tree(ProjectTreeView.LOGICAL).children());
        // Pedir modelado textual arrastra gramaticas y edicion de modelos.
        assertThat(before).containsExactly("Grammars", "Models", "Model diagrams", "Tests");
        JsonNode descriptor = json.readTree(Files.readString(root.resolve("modeling-project.json")));
        assertThat(textValues(descriptor.get("enabledCapabilities")))
                .containsExactly("GRAMMAR_DESIGN", "MODEL_EDITING", "TEXTUAL_MODELING");

        // Un fichero nuevo en un area activa, luego se cierra y se reinicia.
        Path model = root.resolve("models/semantic/people.model.json");
        Files.createDirectories(model.getParent());
        Files.writeString(model, "{\"id\":\"model:people\"}");
        first.close();

        ProjectApplicationService restarted = projects();
        restarted.open(new OpenLocalProjectRequest(root.toString(), false, null));

        assertThat(names(restarted.tree(ProjectTreeView.LOGICAL).children())).isEqualTo(before);
        JsonNode index = json.readTree(Files.readString(root.resolve(".modeling/project-index.json")));
        boolean found = false;
        for (JsonNode artifact : index.get("artifacts")) {
            if ("models/semantic/people.model.json".equals(artifact.get("relativePath").asString())) {
                found = true;
                assertThat(artifact.get("artifactKind").asString()).isEqualTo("MODEL");
                assertThat(artifact.get("etag").asString()).isEqualTo(sha256Etag(model));
            }
        }
        assertThat(found).as("el fichero nuevo figura en el indice reescrito al reabrir").isTrue();
        assertThat(restarted.session().project().enabledCapabilities())
                .containsExactlyInAnyOrder(ProjectCapability.TEXTUAL_MODELING, ProjectCapability.GRAMMAR_DESIGN, ProjectCapability.MODEL_EDITING);
    }

    private ProjectApplicationService projects() {
        return new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
    }

    private Path workspace() throws Exception {
        return Files.createDirectories(temporary.resolve("workspace"));
    }

    private static String sha256Etag(Path file) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))) + "\"";
    }

    private static List<String> names(List<ProjectTreeNode> nodes) {
        List<String> result = new ArrayList<>();
        for (ProjectTreeNode node : nodes) {
            result.add(node.name());
        }
        return result;
    }

    private static Set<String> textValues(JsonNode array) {
        Set<String> values = new TreeSet<>();
        for (JsonNode item : array) {
            values.add(item.asString());
        }
        return values;
    }
}

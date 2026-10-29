package com.example.editor.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Activar capacidades de un proyecto, guardarlas y reabrirlo con una instancia nueva del almacenamiento.
 *
 * <p>Etapas certificadas: {@code project_author_areas.r}, {@code project_open_organize.a1},
 * {@code project_open_organize.a4}.</p>
 *
 * <p>Colaboradores reales: {@link ProjectApplicationService} sobre el almacenamiento local real
 * ({@link ProjectStorageTestFixture}) con un {@code @TempDir}. El oraculo lee el descriptor
 * {@code modeling-project.json} y los directorios directamente del disco.</p>
 */
class ProjectCapabilityConfigurationReopenIntegrationTests {
    private static final String PRIMARY_PATH = "metamodels/people.metamodel.json";

    @TempDir Path temporary;

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("project_author_areas.r + project_open_organize.a4: configurar arrastra dependencias, guarda el descriptor y crea solo las carpetas de las areas activas")
    void configureActivatesDependenciesPersistsDescriptorAndMaterializesOnlyActiveFolders() throws Exception {
        ProjectApplicationService projects = projects();
        Path root = createEmptyProject(projects);
        assertThat(root.resolve("metamodels")).doesNotExist();
        assertThat(root.resolve("behavior")).doesNotExist();

        ProjectSession configured = projects.configure(new ProjectConfigurationRequest(
                Set.of(ProjectCapability.VISUAL_EDITOR, ProjectCapability.QUICK_FIXES),
                ProjectExperienceMode.FREE,
                new ProjectPrimaryArtifact("METAMODEL", "people", PRIMARY_PATH)));
        assertThat(configured.active()).isTrue();

        // Oraculo independiente: el JSON crudo del descriptor en disco.
        JsonNode descriptor = json.readTree(Files.readString(root.resolve("modeling-project.json")));
        assertThat(textValues(descriptor.get("enabledCapabilities")))
                .containsExactly("EDITOR_DEFINITIONS", "METAMODEL_DESIGN", "QUICK_FIXES", "VALIDATIONS", "VISUAL_EDITOR");
        assertThat(descriptor.get("experienceMode").asString()).isEqualTo("FREE");
        assertThat(descriptor.get("primaryArtifact").get("relativePath").asString()).isEqualTo(PRIMARY_PATH);
        assertThat(descriptor.get("primaryArtifact").get("id").asString()).isEqualTo("people");

        // Carpetas: solo las de las areas activas (literales esperados), nada de las demas.
        assertThat(root.resolve("metamodels")).isDirectory();
        assertThat(root.resolve("editors")).isDirectory();
        assertThat(root.resolve("behavior")).isDirectory();
        assertThat(root.resolve("tests")).isDirectory();
        assertThat(root.resolve("events")).doesNotExist();
        assertThat(root.resolve("grammars")).doesNotExist();
        assertThat(root.resolve("models")).doesNotExist();

        // Arbol logico recalculado por el servidor, en el orden de las areas activas.
        assertThat(names(projects.tree(ProjectTreeView.LOGICAL).children())).containsExactly(
                "Metamodels", "Model editors", "Representations", "Palettes", "Forms", "Notations",
                "Expressions", "Validations", "Quick fixes and actions", "Tests");
    }

    @Test
    @DisplayName("project_open_organize.a1 + project_author_areas.r: tras cerrar y reiniciar, el reciente se reabre con areas, modo y artefacto principal guardados")
    void freshStorageInstanceReopensTheRecentProjectWithSavedConfiguration() throws Exception {
        ProjectApplicationService first = projects();
        Path root = createEmptyProject(first);
        first.configure(new ProjectConfigurationRequest(
                Set.of(ProjectCapability.VISUAL_EDITOR, ProjectCapability.QUICK_FIXES),
                ProjectExperienceMode.FREE,
                new ProjectPrimaryArtifact("METAMODEL", "people", PRIMARY_PATH)));
        first.close();

        // "Reinicio": una instancia nueva leyendo el mismo registro de proyectos recientes.
        ProjectApplicationService second = projects();
        assertThat(second.session().active()).isFalse();
        List<ProjectSummary> recent = second.recentProjects();
        assertThat(recent).hasSize(1);
        assertThat(Path.of(recent.get(0).directory())).isEqualTo(root);
        assertThat(recent.get(0).id()).isEqualTo("people-lang");
        assertThat(recent.get(0).active()).isFalse();
        assertThat(recent.get(0).available()).isTrue();

        ProjectSession reopened = second.open(new OpenLocalProjectRequest(recent.get(0).directory(), false, null));

        assertThat(reopened.active()).isTrue();
        ProjectDescriptor project = reopened.project();
        assertThat(project.id()).isEqualTo("people-lang");
        assertThat(new TreeSet<>(project.enabledCapabilities().stream().map(Enum::name).toList()))
                .containsExactly("EDITOR_DEFINITIONS", "METAMODEL_DESIGN", "QUICK_FIXES", "VALIDATIONS", "VISUAL_EDITOR");
        assertThat(project.experienceMode()).isEqualTo(ProjectExperienceMode.FREE);
        assertThat(project.primaryArtifact().kind()).isEqualTo("METAMODEL");
        assertThat(project.primaryArtifact().relativePath()).isEqualTo(PRIMARY_PATH);
        // El arbol que ve el usuario tras reabrir coincide con el de las areas activas.
        assertThat(names(second.tree(ProjectTreeView.LOGICAL).children())).contains("Metamodels", "Validations", "Tests")
                .doesNotContain("Grammars", "Models", "Event definitions");
    }

    @Test
    @DisplayName("project_open_organize.a4 + project_author_areas.r: desactivar un area la deja marcada como inactiva en el arbol sin borrar su contenido")
    void deactivatedAreaWithContentIsMarkedInactiveAndKeepsItsFiles() throws Exception {
        ProjectApplicationService first = projects();
        Path root = createEmptyProject(first);
        first.configure(new ProjectConfigurationRequest(
                Set.of(ProjectCapability.QUICK_FIXES), ProjectExperienceMode.FREE, null));
        Path rule = root.resolve("behavior/validations/people.validations.json");
        Files.createDirectories(rule.getParent());
        Files.writeString(rule, "{\"id\":\"validations:people\"}");
        first.close();

        ProjectApplicationService second = projects();
        second.open(new OpenLocalProjectRequest(root.toString(), false, null));
        assertThat(names(second.tree(ProjectTreeView.LOGICAL).children())).contains("Validations").doesNotContain("Validations (inactive)");

        // Se piden solo los metamodelos: las validaciones y las correcciones dejan de estar activas.
        ProjectSession narrowed = second.configure(new ProjectConfigurationRequest(
                Set.of(ProjectCapability.METAMODEL_DESIGN), null, null));

        JsonNode descriptor = json.readTree(Files.readString(root.resolve("modeling-project.json")));
        assertThat(textValues(descriptor.get("enabledCapabilities"))).containsExactly("METAMODEL_DESIGN");
        assertThat(narrowed.project().enabledCapabilities()).containsExactly(ProjectCapability.METAMODEL_DESIGN);
        List<String> groups = names(second.tree(ProjectTreeView.LOGICAL).children());
        assertThat(groups).containsExactly("Metamodels", "Tests", "Validations (inactive)");
        // El contenido de un area desactivada no se borra.
        assertThat(rule).isRegularFile();
        assertThat(Files.readString(rule)).isEqualTo("{\"id\":\"validations:people\"}");
        // El modo no pedido se conserva (era FREE).
        assertThat(narrowed.project().experienceMode()).isEqualTo(ProjectExperienceMode.FREE);
    }

    @Test
    @DisplayName("project_author_areas.r: configurar sin proyecto abierto y con artefacto principal fuera del proyecto se rechaza")
    void configurationIsRejectedWithoutActiveProjectOrWithUnsafePrimaryArtifact() throws Exception {
        ProjectApplicationService projects = projects();
        Path root = createEmptyProject(projects);
        Path descriptorFile = root.resolve("modeling-project.json");
        String before = Files.readString(descriptorFile);

        assertThatThrownBy(() -> new ProjectPrimaryArtifact("METAMODEL", "x", "../escape.metamodel.json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(descriptorFile)).isEqualTo(before);

        projects.close();
        assertThatThrownBy(() -> projects.configure(new ProjectConfigurationRequest(
                Set.of(ProjectCapability.METAMODEL_DESIGN), ProjectExperienceMode.FREE, null)))
                .isInstanceOf(ProjectException.class);
        assertThat(Files.readString(descriptorFile)).isEqualTo(before);
        assertThat(root.resolve("metamodels")).doesNotExist();
    }

    private ProjectApplicationService projects() {
        return new ProjectApplicationService(ProjectStorageTestFixture.storage(temporary));
    }

    private Path createEmptyProject(ProjectApplicationService projects) throws Exception {
        Path parent = temporary.resolve("workspace");
        Files.createDirectories(parent);
        ProjectSession created = projects.create(new CreateLocalProjectRequest(
                parent.toString(), "people-lang", "People Language", "", false,
                ProjectPreset.EMPTY, Set.of(), ProjectExperienceMode.GUIDED));
        assertThat(created.active()).isTrue();
        assertThat(created.project().enabledCapabilities()).isEmpty();
        return Path.of(created.directory());
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

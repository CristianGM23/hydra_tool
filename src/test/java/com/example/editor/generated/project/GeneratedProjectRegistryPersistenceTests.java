package com.example.editor.generated.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.compatibility.ToolchainCommandRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registro persistente de proyectos generados con servicio y almacen reales sobre un directorio temporal.
 * Etapas certificadas: publication_generate_publish.reg, x_generate_certify_distribute.reg.
 * El oraculo es el fichero {@code generated-projects.json} leido directamente del disco.
 */
class GeneratedProjectRegistryPersistenceTests {
    @TempDir Path temporary;

    /** Runner que falla el test si alguna etapa intenta lanzar un proceso: registrar y abrir no lanzan nada. */
    private static final ToolchainCommandRunner NO_PROCESS_ALLOWED = (id, description, workingDirectory, command,
            expectedExitCode, timeoutSeconds, environment) -> {
        throw new AssertionError("No native process must be started by register/OPEN: " + command);
    };

    private Path projects() { return temporary.resolve("projects"); }
    private Path registry() { return temporary.resolve("registry"); }

    /** Cada llamada construye servicio y almacen NUEVOS sobre los mismos directorios (simula un reinicio). */
    private GeneratedProjectLifecycleService newService() {
        return new GeneratedProjectLifecycleService(new GeneratedProjectRuntimeProfileCatalog(),
                new GeneratedProjectRegistryStore(JsonMapper.builder().findAndAddModules().build()),
                NO_PROCESS_ALLOWED, projects().toString(), registry().toString());
    }

    private static GeneratedProjectDefinition definition(String id, String name, String profile, String directory) {
        return new GeneratedProjectDefinition(id, name, profile, directory, Instant.parse("2026-01-02T03:04:05Z"), Map.of());
    }

    private JsonNode registryOnDisk() throws Exception {
        Path file = registry().resolve("generated-projects.json");
        assertThat(file).isRegularFile();
        return JsonMapper.builder().findAndAddModules().build().readTree(Files.readString(file));
    }

    @Test
    @DisplayName("publication_generate_publish.reg / x_generate_certify_distribute.reg: el registro sobrevive a un reinicio del servicio")
    void registeredProjectIsPersistedOnDiskAndRecoveredByAFreshServiceInstance() throws Exception {
        Files.createDirectories(projects().resolve("alpha"));
        GeneratedProjectRegistry returned = newService().register(definition("alpha", "Alpha editor", "standalone-web", "alpha"));
        assertThat(returned.projects()).hasSize(1);

        // Oraculo independiente: el JSON escrito en disco.
        JsonNode json = registryOnDisk();
        assertThat(json.get("projects").size()).isEqualTo(1);
        assertThat(json.get("projects").get(0).get("id").toString()).isEqualTo("\"alpha\"");
        assertThat(json.get("projects").get(0).get("profileKey").toString()).isEqualTo("\"standalone-web\"");
        assertThat(json.get("projects").get(0).get("relativeDirectory").toString()).isEqualTo("\"alpha\"");
        assertThat(registry().resolve("generated-projects.json.tmp")).doesNotExist();

        // "Reinicio": nueva instancia de servicio + almacen.
        GeneratedProjectRegistry recovered = newService().registry();
        assertThat(recovered.projects()).extracting(GeneratedProjectDefinition::id).containsExactly("alpha");
        assertThat(recovered.projects().get(0).name()).isEqualTo("Alpha editor");
        assertThat(recovered.projects().get(0).profileKey()).isEqualTo("standalone-web");
        assertThat(recovered.projects().get(0).relativeDirectory()).isEqualTo("alpha");
    }

    @Test
    @DisplayName("publication_generate_publish.reg: registrar el mismo id lo reemplaza y los demas se conservan")
    void registeringTheSameIdReplacesTheEntryAndKeepsTheOthers() throws Exception {
        newService().register(definition("alpha", "Alpha v1", "standalone-web", "alpha"));
        newService().register(definition("beta", "Beta", "langium", "beta"));
        newService().register(definition("alpha", "Alpha v2", "xtext", "alpha-two"));

        JsonNode json = registryOnDisk();
        assertThat(json.get("projects").size()).isEqualTo(2);
        // Los proyectos se ordenan por id: alpha, beta.
        assertThat(json.get("projects").get(0).get("id").toString()).isEqualTo("\"alpha\"");
        assertThat(json.get("projects").get(0).get("name").toString()).isEqualTo("\"Alpha v2\"");
        assertThat(json.get("projects").get(0).get("profileKey").toString()).isEqualTo("\"xtext\"");
        assertThat(json.get("projects").get(1).get("id").toString()).isEqualTo("\"beta\"");
        assertThat(newService().registry().projects()).extracting(GeneratedProjectDefinition::name)
                .containsExactly("Alpha v2", "Beta");
    }

    @Test
    @DisplayName("x_generate_certify_distribute.reg: un perfil desconocido se rechaza y no deja entrada")
    void unknownProfileIsRejectedWithoutAnyEntry() throws Exception {
        assertThatThrownBy(() -> newService().register(definition("ghost", "Ghost", "no-such-profile", "ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown generated-project profile: no-such-profile");
        assertThat(registry().resolve("generated-projects.json")).doesNotExist();
        assertThat(newService().registry().projects()).isEmpty();
    }

    @Test
    @DisplayName("x_generate_certify_distribute.reg: directorios fuera de la raiz configurada se rechazan sin entrada")
    void directoriesOutsideTheConfiguredRootAreRejectedWithoutAnyEntry() throws Exception {
        newService().register(definition("kept", "Kept", "langium", "kept"));
        String before = Files.readString(registry().resolve("generated-projects.json"));

        for (String unsafe : List.of("../outside", "a/../../outside", "/absolute", "C:\\Windows", ".", "..")) {
            assertThatThrownBy(() -> newService().register(definition("evil", "Evil", "langium", unsafe)))
                    .as("directory %s", unsafe)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("relativeDirectory");
        }

        assertThat(Files.readString(registry().resolve("generated-projects.json"))).isEqualTo(before);
        assertThat(registryOnDisk().get("projects").size()).isEqualTo(1);
        assertThat(newService().registry().projects()).extracting(GeneratedProjectDefinition::id).containsExactly("kept");
    }

    @Test
    @DisplayName("publication_generate_publish.reg: execute(OPEN) real resuelve el directorio registrado sin lanzar procesos")
    void openStageResolvesTheRegisteredDirectoryWithoutStartingProcesses() throws Exception {
        Files.createDirectories(projects().resolve("alpha"));
        GeneratedProjectLifecycleService writer = newService();
        writer.register(definition("alpha", "Alpha", "langium", "alpha"));
        writer.register(definition("missing", "Missing", "langium", "missing"));

        // Servicio reiniciado: ejecuta contra el registro leido del disco.
        GeneratedProjectLifecycleService restarted = newService();
        GeneratedProjectExecutionReport report = restarted.execute(new GeneratedProjectExecutionRequest(
                "alpha", List.of(GeneratedProjectStage.OPEN), false, false, false));
        assertThat(report.successful()).isTrue();
        assertThat(report.steps()).hasSize(1);
        assertThat(report.steps().get(0).status()).isEqualTo(GeneratedProjectStepStatus.PASSED);
        String expected = projects().resolve("alpha").toAbsolutePath().normalize().toString();
        assertThat(report.resolvedDirectory()).isEqualTo(expected);
        assertThat(report.steps().get(0).summary()).isEqualTo(expected);

        GeneratedProjectExecutionReport absent = restarted.execute(new GeneratedProjectExecutionRequest(
                "missing", List.of(GeneratedProjectStage.OPEN), false, false, false));
        assertThat(absent.successful()).isFalse();
        assertThat(absent.steps().get(0).status()).isEqualTo(GeneratedProjectStepStatus.FAILED);
        assertThat(absent.steps().get(0).diagnostics()).containsExactly("Generated project directory does not exist.");

        assertThatThrownBy(() -> restarted.execute(new GeneratedProjectExecutionRequest(
                "never-registered", List.of(GeneratedProjectStage.OPEN), false, false, false)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown generated project");
    }
}

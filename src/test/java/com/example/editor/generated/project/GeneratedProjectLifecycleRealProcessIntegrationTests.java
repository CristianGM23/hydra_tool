package com.example.editor.generated.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.integration.compatibility.ExternalToolchainCommandRunner;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ciclo de vida de proyectos generados con PROCESOS NATIVOS REALES (la propia JVM, via {@code java Fichero.java}).
 * Colaboradores reales: GeneratedProjectLifecycleController, GeneratedProjectLifecycleService, GeneratedProjectRegistryStore,
 * ExternalToolchainCommandRunner, ExternalToolProcessRunner, HydraExternalProcessRunner y la politica de procesos por defecto.
 * Unica sustitucion: el catalogo de perfiles ({@code jvm-probe}), para no depender de node/npm/mvn; el catalogo
 * de produccion se usa en el ultimo test.
 *
 * Etapas certificadas: generated_projects_lifecycle.{rg,ge,va,op,la}, generated_projects_author_wizard.{rg,pv,ru}.
 * El oraculo son los marcadores que escribe el proceso hijo y el JSON del registro leido del disco.
 */
class GeneratedProjectLifecycleRealProcessIntegrationTests {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString();

    @TempDir Path temporary;
    private Path projects;
    private Path registry;
    private MockMvc mvc;

    /** Catalogo de pruebas: un unico perfil cuyas etapas ejecutan programas Java de fichero unico. */
    private static final class JvmProbeCatalog extends GeneratedProjectRuntimeProfileCatalog {
        private final GeneratedProjectRuntimeProfile profile = new GeneratedProjectRuntimeProfile("jvm-probe", "JVM probe", "JVM", "Test profile",
                List.of(stage(GeneratedProjectStage.GENERATE, "Generate marker", List.of(JAVA, "Gen.java"), false),
                        stage(GeneratedProjectStage.VALIDATE, "Validate marker", List.of(JAVA, "Val.java"), false),
                        new GeneratedProjectStageDefinition(GeneratedProjectStage.OPEN, "Open", List.of(), List.of(), 5, false, Map.of()),
                        stage(GeneratedProjectStage.LAUNCH, "Launch marker", List.of(JAVA, "Run.java"), true)));
        private static GeneratedProjectStageDefinition stage(GeneratedProjectStage stage, String description, List<String> command, boolean optional) {
            return new GeneratedProjectStageDefinition(stage, description, command, command, 120, optional, Map.of());
        }
        @Override public List<GeneratedProjectRuntimeProfile> profiles() { return List.of(profile); }
        @Override public GeneratedProjectRuntimeProfile require(String key) {
            if (!profile.key().equals(key)) throw new IllegalArgumentException("Unknown generated-project profile: " + key);
            return profile;
        }
    }

    @BeforeEach
    void wire() throws IOException {
        projects = temporary.resolve("projects");
        registry = temporary.resolve("registry");
        Files.createDirectories(projects);
        mvc = MockMvcBuilders.standaloneSetup(controller(new JvmProbeCatalog()))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private GeneratedProjectLifecycleController controller(GeneratedProjectRuntimeProfileCatalog catalog) {
        ExternalToolchainCommandRunner runner = new ExternalToolchainCommandRunner(
                new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()));
        return new GeneratedProjectLifecycleController(new GeneratedProjectLifecycleService(catalog,
                new GeneratedProjectRegistryStore(JsonMapper.builder().findAndAddModules().build()), runner,
                projects.toString(), registry.toString()));
    }

    private static final String GEN = "public class Gen { public static void main(String[] a) throws Exception {"
            + " java.nio.file.Files.writeString(java.nio.file.Path.of(\"generate.marker\"), \"generated\"); } }";
    private static final String VAL_OK = "public class Val { public static void main(String[] a) throws Exception {"
            + " if (!java.nio.file.Files.exists(java.nio.file.Path.of(\"generate.marker\"))) System.exit(3);"
            + " java.nio.file.Files.writeString(java.nio.file.Path.of(\"validate.marker\"), \"validated\"); } }";
    private static final String VAL_FAIL = "public class Val { public static void main(String[] a) { System.exit(1); } }";
    private static final String RUN = "public class Run { public static void main(String[] a) throws Exception {"
            + " java.nio.file.Files.writeString(java.nio.file.Path.of(\"launch.marker\"), \"launched\"); } }";

    private Path project(String name, String validateSource) throws IOException {
        Path dir = Files.createDirectories(projects.resolve(name));
        Files.writeString(dir.resolve("Gen.java"), GEN);
        Files.writeString(dir.resolve("Val.java"), validateSource);
        Files.writeString(dir.resolve("Run.java"), RUN);
        return dir;
    }

    private ResultActions register(String id, String profile, String directory) throws Exception {
        return mvc.perform(post("/api/generated-projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"profileKey\":\"" + profile
                        + "\",\"relativeDirectory\":\"" + directory + "\"}"));
    }

    private ResultActions execute(String id, String stages, boolean dryRun, boolean optional, boolean continueAfterFailure) throws Exception {
        return mvc.perform(post("/api/generated-projects/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"" + id + "\",\"stages\":" + stages + ",\"dryRun\":" + dryRun
                        + ",\"allowOptionalStages\":" + optional + ",\"continueAfterFailure\":" + continueAfterFailure + "}"));
    }

    private static final String ALL = "[\"GENERATE\",\"VALIDATE\",\"OPEN\",\"LAUNCH\"]";

    @Test
    @DisplayName("generated_projects_lifecycle.rg / generated_projects_author_wizard.rg: registrar por HTTP persiste el registro en disco")
    void registerThroughHttpPersistsTheRegistryOnDisk() throws Exception {
        project("probe", VAL_OK);
        register("probe", "jvm-probe", "probe").andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].id").value("probe"));
        String json = Files.readString(registry.resolve("generated-projects.json"));
        assertThat(json).contains("\"probe\"").contains("\"jvm-probe\"");
        assertThat(JsonMapper.builder().findAndAddModules().build().readTree(json).get("projects").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("generated_projects_author_wizard.pv: la simulacion muestra los comandos y no ejecuta nada")
    void dryRunPlansTheCommandsAndExecutesNothing() throws Exception {
        Path dir = project("probe", VAL_OK);
        register("probe", "jvm-probe", "probe").andExpect(status().isOk());

        execute("probe", "[\"GENERATE\",\"VALIDATE\",\"OPEN\"]", true, false, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true))
                .andExpect(jsonPath("$.steps[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.steps[0].command[1]").value("Gen.java"))
                .andExpect(jsonPath("$.steps[1].status").value("PLANNED"))
                .andExpect(jsonPath("$.steps[1].command[1]").value("Val.java"))
                .andExpect(jsonPath("$.steps[2].status").value("PASSED"));
        assertThat(dir.resolve("generate.marker")).doesNotExist();
        assertThat(dir.resolve("validate.marker")).doesNotExist();
    }

    @Test
    @DisplayName("generated_projects_lifecycle.{ge,va,op,la} / generated_projects_author_wizard.ru: ejecucion real GENERATE+VALIDATE+OPEN y LAUNCH opcional")
    void realExecutionRunsNativeProcessesAndOptionalLaunchNeedsExplicitPermission() throws Exception {
        Path dir = project("probe", VAL_OK);
        register("probe", "jvm-probe", "probe").andExpect(status().isOk());

        execute("probe", ALL, false, false, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true))
                .andExpect(jsonPath("$.steps[0].status").value("PASSED"))
                .andExpect(jsonPath("$.steps[0].exitCode").value(0))
                .andExpect(jsonPath("$.steps[1].status").value("PASSED"))
                .andExpect(jsonPath("$.steps[1].exitCode").value(0))
                .andExpect(jsonPath("$.steps[2].status").value("PASSED"))
                .andExpect(jsonPath("$.steps[3].status").value("SKIPPED"))
                .andExpect(jsonPath("$.steps[3].summary").value("Optional stage was not enabled."));
        // Oraculo: marcadores escritos por los procesos hijos reales; VALIDATE solo pasa porque GENERATE ya existia.
        assertThat(Files.readString(dir.resolve("generate.marker"))).isEqualTo("generated");
        assertThat(Files.readString(dir.resolve("validate.marker"))).isEqualTo("validated");
        assertThat(dir.resolve("launch.marker")).doesNotExist();

        execute("probe", "[\"LAUNCH\"]", false, true, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].status").value("PASSED"))
                .andExpect(jsonPath("$.steps[0].exitCode").value(0));
        assertThat(Files.readString(dir.resolve("launch.marker"))).isEqualTo("launched");
    }

    @Test
    @DisplayName("generated_projects_lifecycle.va: un VALIDATE que sale con codigo 1 falla y las etapas siguientes se omiten")
    void failingValidateStageReportsExitCodeOneAndSkipsTheRest() throws Exception {
        Path dir = project("broken", VAL_FAIL);
        register("broken", "jvm-probe", "broken").andExpect(status().isOk());

        execute("broken", ALL, false, true, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(false))
                .andExpect(jsonPath("$.steps[0].status").value("PASSED"))
                .andExpect(jsonPath("$.steps[1].status").value("FAILED"))
                .andExpect(jsonPath("$.steps[1].exitCode").value(1))
                .andExpect(jsonPath("$.steps[1].summary").value("Stage failed."))
                .andExpect(jsonPath("$.steps[2].status").value("SKIPPED"))
                .andExpect(jsonPath("$.steps[2].summary").value("Skipped after a previous failure."))
                .andExpect(jsonPath("$.steps[3].status").value("SKIPPED"));
        assertThat(dir.resolve("generate.marker")).exists();
        assertThat(dir.resolve("launch.marker")).doesNotExist();
    }

    @Test
    @DisplayName("generated_projects_author_wizard.rg: relativeDirectory con ../ o con enlace simbolico se rechaza sin registrar")
    void unsafeRelativeDirectoriesAreRejectedAndNothingIsRegistered() throws Exception {
        register("escape", "jvm-probe", "../escape").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        register("absolute", "jvm-probe", "/etc").andExpect(status().isBadRequest());
        register("unknown", "no-such-profile", "unknown").andExpect(status().isBadRequest());
        assertThat(registry.resolve("generated-projects.json")).doesNotExist();
    }

    /** Parte del rechazo que necesita enlaces simbolicos: se omite (SKIPPED) en hosts que no los permiten, sin restar valor al caso anterior. */
    @Test
    @DisplayName("generated_projects_author_wizard.rg: un enlace simbolico en el directorio relativo se rechaza (solo si el SO lo permite)")
    void relativeDirectoryCrossingASymbolicLinkIsRejected() throws Exception {
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        boolean symlink;
        try { Files.createSymbolicLink(projects.resolve("link"), outside); symlink = true; }
        catch (IOException | UnsupportedOperationException | SecurityException exception) { symlink = false; }
        if (symlink) {
            register("linked", "jvm-probe", "link").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("relativeDirectory crosses a symbolic link"));
        }
        assertThat(registry.resolve("generated-projects.json")).doesNotExist();
        assumeTrue(symlink, "symbolic links not permitted on this host; symlink rejection not exercised");
    }

    /**
     * Antes era un HALLAZGO caracterizado (el perfil ejecutaba {@code cmd /c} / {@code sh}, no permitidos). Corregido: todas
     * las etapas con comando del perfil de produccion pasan la politica de procesos por defecto, sin ampliar la lista de
     * ejecutables. La ejecucion real de extremo a extremo esta en GeneratedStandaloneWebProfileRealLifecycleIntegrationTests.
     */
    @Test
    @DisplayName("generated_projects_lifecycle.ge: todos los comandos del perfil standalone-web de produccion pasan la politica de procesos por defecto")
    void productionStandaloneWebCommandsPassTheDefaultExternalProcessPolicy() {
        com.example.editor.security.process.HydraExternalProcessPolicy policy = new com.example.editor.security.process.HydraExternalProcessPolicy(
                new com.example.editor.security.process.HydraExternalProcessSecurityProperties());
        GeneratedProjectRuntimeProfile profile = new GeneratedProjectRuntimeProfileCatalog().require("standalone-web");
        int checked = 0;
        for (GeneratedProjectStageDefinition stage : profile.stages()) {
            for (List<String> command : List.of(stage.windowsCommand(), stage.unixCommand())) {
                if (command.isEmpty()) continue;
                policy.validateCommand(command);
                assertThat(command.get(0)).isIn("javac", "java");
                checked++;
            }
        }
        assertThat(checked).isEqualTo(6);
    }
}

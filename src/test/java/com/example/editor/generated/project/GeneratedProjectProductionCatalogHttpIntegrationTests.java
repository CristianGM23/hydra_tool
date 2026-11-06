package com.example.editor.generated.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.integration.compatibility.ExternalToolchainCommandRunner;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proyectos generados con el CATALOGO DE PRODUCCION (sin sustituir perfiles) a traves del controlador HTTP real, el servicio
 * real, el almacen JSON real del registro y el ejecutor de comandos real en directorios temporales. Solo se registra y se
 * ejecuta la etapa de apertura, que no lanza ningun comando ni exige herramientas externas (npm, Maven, Python).
 * No se ejecutan las etapas de generacion, validacion o arranque de ningun perfil de produccion; en particular no se
 * reclama el perfil de editor web autonomo, cuyas etapas con interprete de comandos rechaza la politica de procesos
 * (hallazgo ya caracterizado por otro test).
 *
 * Etapas: generated_projects_lifecycle.rg y generated_projects_lifecycle.op.
 * Oraculos: registro JSON leido del disco, ruta de proyecto calculada en el test, lista de perfiles escrita a mano y
 * ausencia de ficheros nuevos en el directorio del proyecto.
 */
class GeneratedProjectProductionCatalogHttpIntegrationTests {
    @TempDir Path temporary;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private Path projects;
    private Path registry;
    private MockMvc mvc;

    @BeforeEach
    void wire() throws IOException {
        projects = temporary.resolve("projects");
        registry = temporary.resolve("registry");
        Files.createDirectories(projects);
        mvc = newMvc();
    }

    private MockMvc newMvc() {
        ExternalToolchainCommandRunner runner = new ExternalToolchainCommandRunner(
                new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()));
        GeneratedProjectLifecycleService service = new GeneratedProjectLifecycleService(new GeneratedProjectRuntimeProfileCatalog(),
                new GeneratedProjectRegistryStore(json), runner, projects.toString(), registry.toString());
        return MockMvcBuilders.standaloneSetup(new GeneratedProjectLifecycleController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private MockHttpServletResponse register(String id, String profile, String directory) throws Exception {
        return mvc.perform(post("/api/generated-projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"profileKey\":\"" + profile
                        + "\",\"relativeDirectory\":\"" + directory + "\"}")).andReturn().getResponse();
    }

    private JsonNode openOnly(String id) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/generated-projects/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"" + id + "\",\"stages\":[\"OPEN\"],\"dryRun\":false,"
                        + "\"allowOptionalStages\":false,\"continueAfterFailure\":false}")).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : array) result.add(item.get(field).asString());
        return result;
    }

    @Test
    @DisplayName("generated_projects_lifecycle.rg: el catalogo de produccion ofrece los cinco perfiles con sus etapas")
    void theProductionCatalogOffersTheFiveProfilesAndTheirStages() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/generated-projects/profiles")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode profiles = json.readTree(response.getContentAsString());
        assertThat(texts(profiles, "key")).containsExactly("besser-sdk", "glsp", "langium", "standalone-web", "xtext");
        assertThat(texts(profiles, "technology")).containsExactly("BESSER", "ECLIPSE_GLSP", "LANGIUM", "HYDRA", "XTEXT");
        assertThat(texts(profiles.get(2).get("stages"), "stage")).containsExactly("GENERATE", "VALIDATE", "OPEN");
        assertThat(texts(profiles.get(4).get("stages"), "stage")).containsExactly("GENERATE", "VALIDATE", "OPEN");
        assertThat(texts(profiles.get(3).get("stages"), "stage")).containsExactly("GENERATE", "VALIDATE", "OPEN", "LAUNCH");
        assertThat(profiles.get(3).get("stages").get(3).get("optional").asBoolean()).isTrue();
        assertThat(profiles.get(2).get("stages").get(2).get("windowsCommand").size()).isZero();
        assertThat(profiles.get(2).get("stages").get(2).get("unixCommand").size()).isZero();
    }

    @Test
    @DisplayName("generated_projects_lifecycle.rg: registrar un proyecto langium persiste el registro, sobrevive a un reinicio y se rechazan perfiles o rutas invalidos")
    void registeringALangiumProjectPersistsTheRegistryAndRejectsInvalidRegistrations() throws Exception {
        Files.createDirectories(projects.resolve("langium-app"));
        Files.createDirectories(projects.resolve("xtext-app"));

        MockHttpServletResponse created = register("langium-app", "langium", "langium-app");
        assertThat(created.getStatus()).isEqualTo(200);
        assertThat(register("xtext-app", "xtext", "xtext-app").getStatus()).isEqualTo(200);
        assertThat(register("langium-app", "langium", "langium-app").getStatus()).isEqualTo(200);

        JsonNode onDisk = json.readTree(Files.readString(registry.resolve("generated-projects.json")));
        assertThat(onDisk.get("projects").size()).isEqualTo(2);
        assertThat(texts(onDisk.get("projects"), "id")).containsExactly("langium-app", "xtext-app");
        assertThat(texts(onDisk.get("projects"), "profileKey")).containsExactly("langium", "xtext");
        assertThat(texts(onDisk.get("projects"), "relativeDirectory")).containsExactly("langium-app", "xtext-app");

        assertThat(register("bad-profile", "no-such-profile", "langium-app").getStatus()).isEqualTo(400);
        assertThat(register("escape", "langium", "../escape").getStatus()).isEqualTo(400);
        assertThat(register("absolute", "langium", "/etc").getStatus()).isEqualTo(400);
        assertThat(json.readTree(Files.readString(registry.resolve("generated-projects.json"))).get("projects").size()).isEqualTo(2);

        MockMvc restarted = newMvc();
        JsonNode reloaded = json.readTree(restarted.perform(get("/api/generated-projects")).andReturn().getResponse().getContentAsString());
        assertThat(texts(reloaded.get("projects"), "id")).containsExactly("langium-app", "xtext-app");
    }

    @Test
    @DisplayName("generated_projects_lifecycle.op: la etapa de apertura resuelve el directorio sin ejecutar comandos ni tocar el proyecto")
    void theOpenStageResolvesTheProjectDirectoryWithoutRunningAnyCommand() throws Exception {
        Path directory = Files.createDirectories(projects.resolve("langium-app"));
        assertThat(register("langium-app", "langium", "langium-app").getStatus()).isEqualTo(200);

        JsonNode report = openOnly("langium-app");

        assertThat(report.get("successful").asBoolean()).isTrue();
        assertThat(report.get("steps").size()).isEqualTo(1);
        JsonNode step = report.get("steps").get(0);
        assertThat(step.get("stage").asString()).isEqualTo("OPEN");
        assertThat(step.get("status").asString()).isEqualTo("PASSED");
        assertThat(step.get("summary").asString()).isEqualTo(directory.toAbsolutePath().normalize().toString());
        assertThat(step.get("command").size()).isZero();
        assertThat(step.get("diagnostics").size()).isZero();
        try (var files = Files.list(directory)) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    @DisplayName("generated_projects_lifecycle.op: la apertura sirve igual para los perfiles sin comandos de produccion y falla si el directorio no existe")
    void openWorksForTheOtherProductionProfilesAndFailsForAMissingDirectory() throws Exception {
        for (String profile : List.of("xtext", "glsp", "besser-sdk")) {
            Path directory = Files.createDirectories(projects.resolve(profile + "-app"));
            assertThat(register(profile + "-app", profile, profile + "-app").getStatus()).isEqualTo(200);
            JsonNode report = openOnly(profile + "-app");
            assertThat(report.get("successful").asBoolean()).as(profile).isTrue();
            assertThat(report.get("steps").get(0).get("status").asString()).as(profile).isEqualTo("PASSED");
            assertThat(report.get("steps").get(0).get("summary").asString()).isEqualTo(directory.toAbsolutePath().normalize().toString());
        }

        assertThat(register("ghost", "langium", "ghost").getStatus()).isEqualTo(200);
        JsonNode missing = openOnly("ghost");
        assertThat(missing.get("successful").asBoolean()).isFalse();
        assertThat(missing.get("steps").get(0).get("status").asString()).isEqualTo("FAILED");
        assertThat(missing.get("steps").get(0).get("diagnostics").get(0).asString())
                .isEqualTo("Generated project directory does not exist.");
        assertThat(projects.resolve("ghost")).doesNotExist();
    }
}

package com.example.editor.generated.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.archive.CapabilityPackageArchiveProperties;
import com.example.editor.portability.archive.CapabilityPackageArchiveService;
import com.example.editor.portability.archive.CapabilityPackageDependencyResolver;
import com.example.editor.standalone.StandaloneDeploymentTarget;
import com.example.editor.standalone.StandaloneEditorGenerationProperties;
import com.example.editor.standalone.StandaloneEditorGenerationRequest;
import com.example.editor.standalone.StandaloneEditorGenerationResult;
import com.example.editor.standalone.StandaloneEditorGeneratorService;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Regresion del defecto de produccion «standalone-web rechazado por la politica de procesos»: el perfil real
 * {@code standalone-web} del catalogo de produccion ya solo usa los lanzadores del JDK ({@code javac}, {@code java}) que la
 * politica permite, sin ampliar la lista de ejecutables.
 *
 * Colaboradores reales: generador de editores autonomos, controlador y servicio del ciclo de vida, registro en disco,
 * ExternalToolchainCommandRunner, HydraExternalProcessRunner y la politica de procesos por defecto. No hay sustituciones.
 * Etapas: generated_projects_lifecycle.{ge,va,op,la}. Oraculos: ficheros reales producidos por el compilador y la salida
 * del proceso de verificacion leidos del disco/respuesta HTTP; el caso negativo borra {@code frontend/index.html} y exige
 * un fallo real del proceso hijo (codigo de salida 1).
 */
class GeneratedStandaloneWebProfileRealLifecycleIntegrationTests {
    @TempDir Path temporary;
    private Path projects;
    private MockMvc mvc;

    @BeforeEach
    void wire() throws IOException {
        projects = Files.createDirectories(temporary.resolve("projects"));
        ExternalToolchainCommandRunner runner = new ExternalToolchainCommandRunner(
                new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()));
        GeneratedProjectLifecycleController controller = new GeneratedProjectLifecycleController(
                new GeneratedProjectLifecycleService(new GeneratedProjectRuntimeProfileCatalog(),
                        new GeneratedProjectRegistryStore(JsonMapper.builder().findAndAddModules().build()), runner,
                        projects.toString(), temporary.resolve("registry").toString()));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private Path generateInto(String directory) throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var archiveProperties = new CapabilityPackageArchiveProperties();
        archiveProperties.setCacheDirectory(temporary.resolve("cache"));
        var archive = new CapabilityPackageArchiveService(archiveProperties, new CapabilityPackageChecker(),
                new CapabilityPackageDependencyResolver(), mapper);
        var generator = new StandaloneEditorGeneratorService(new StandaloneEditorGenerationProperties(), archive, mapper);
        StandaloneEditorGenerationResult result = generator.generate(new StandaloneEditorGenerationRequest("people-app",
                "People editor", "people", "people-editor", 4173, "EMBEDDED_LOCAL", "", true, true, 10L * 1024L * 1024L,
                List.of(), List.of(), Map.of(), packageValue(), Set.of(StandaloneDeploymentTarget.SOURCE)));
        Path target = Files.createDirectories(projects.resolve(directory));
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(result.archive()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path file = target.resolve(entry.getName()).normalize();
                assertThat(file.startsWith(target)).as("zip entry stays inside the target: " + entry.getName()).isTrue();
                Files.createDirectories(file.getParent());
                Files.write(file, input.readAllBytes());
            }
        }
        return target;
    }

    private ResultActions register(String id, String directory) throws Exception {
        return mvc.perform(post("/api/generated-projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"profileKey\":\"standalone-web\",\"relativeDirectory\":\""
                        + directory + "\"}"));
    }

    private ResultActions execute(String id, String stages, boolean dryRun) throws Exception {
        return mvc.perform(post("/api/generated-projects/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"" + id + "\",\"stages\":" + stages + ",\"dryRun\":" + dryRun
                        + ",\"allowOptionalStages\":false,\"continueAfterFailure\":false}"));
    }

    @Test
    @DisplayName("generated_projects_lifecycle.ge / va / op: el perfil standalone-web real compila el servidor generado y verifica el proyecto con procesos nativos permitidos")
    void theProductionStandaloneWebProfileBuildsAndVerifiesAGeneratedEditor() throws Exception {
        Path dir = generateInto("web");
        register("web", "web").andExpect(status().isOk());
        assertThat(dir.resolve("backend/out")).doesNotExist();

        execute("web", "[\"GENERATE\",\"VALIDATE\",\"OPEN\"]", false).andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true))
                .andExpect(jsonPath("$.steps[0].status").value("PASSED"))
                .andExpect(jsonPath("$.steps[0].exitCode").value(0))
                .andExpect(jsonPath("$.steps[0].command[0]").value("javac"))
                .andExpect(jsonPath("$.steps[1].status").value("PASSED"))
                .andExpect(jsonPath("$.steps[1].exitCode").value(0))
                .andExpect(jsonPath("$.steps[1].command[0]").value("java"))
                .andExpect(jsonPath("$.steps[2].status").value("PASSED"));

        assertThat(dir.resolve("backend/out/HydraStandaloneServer.class")).isRegularFile();
    }

    @Test
    @DisplayName("generated_projects_lifecycle.va: un proyecto standalone-web sin frontend/index.html falla de verdad en la verificacion")
    void validationFailsWhenTheGeneratedFrontendIsMissing() throws Exception {
        Path dir = generateInto("broken");
        register("broken", "broken").andExpect(status().isOk());
        execute("broken", "[\"GENERATE\"]", false).andExpect(jsonPath("$.successful").value(true));
        Files.delete(dir.resolve("frontend/index.html"));

        execute("broken", "[\"VALIDATE\"]", false).andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(false))
                .andExpect(jsonPath("$.steps[0].status").value("FAILED"))
                .andExpect(jsonPath("$.steps[0].exitCode").value(1));
    }

    @Test
    @DisplayName("generated_projects_lifecycle.la: el lanzamiento planificado usa el servidor compilado sin envoltorios de shell")
    void theLaunchStagePlansTheCompiledServerWithoutAShellWrapper() throws Exception {
        generateInto("launch");
        register("launch", "launch").andExpect(status().isOk());
        mvc.perform(post("/api/generated-projects/execute").contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"launch\",\"stages\":[\"LAUNCH\"],\"dryRun\":true,\"allowOptionalStages\":true,\"continueAfterFailure\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.steps[0].command[0]").value("java"))
                .andExpect(jsonPath("$.steps[0].command[3]").value("HydraStandaloneServer"));
    }

    private HydraCapabilityPackage packageValue() {
        var metamodel = new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL, "metamodel.json", "2.1", true, false, Map.of());
        var editor = new CapabilitySelection("editor", HydraCapabilityKind.VISUAL_REPRESENTATION, "editor.json", "1.0", true, true, Map.of());
        var manifest = new HydraCapabilityPackageManifest("1.0", "people-editor-package", "People editor", "1.0.0", "people",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                List.of(metamodel, editor), List.of(new CapabilityDependency("editor", "metamodel", true, ">=2.1",
                        "The editor resolves metamodel types.")), List.of(), Map.of(), Map.of());
        Map<String, JsonNode> documents = new LinkedHashMap<>();
        ObjectNode metamodelDocument = JsonNodeFactory.instance.objectNode();
        metamodelDocument.put("schemaVersion", "2.1");
        metamodelDocument.put("id", "people");
        metamodelDocument.putArray("classes");
        metamodelDocument.putArray("relations");
        ObjectNode editorDocument = JsonNodeFactory.instance.objectNode();
        editorDocument.put("schemaVersion", "1.0");
        editorDocument.put("id", "people-editor");
        editorDocument.putObject("representationsById");
        documents.put("metamodel.json", metamodelDocument);
        documents.put("editor.json", editorDocument);
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("icons/person.svg", "<svg/>");
        return new HydraCapabilityPackage(manifest, documents, resources);
    }
}

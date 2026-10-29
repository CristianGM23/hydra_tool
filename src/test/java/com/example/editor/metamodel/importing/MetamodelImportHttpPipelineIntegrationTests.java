package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * metamodel_author_import.src / .str / .an / .d1 / .art and metamodel_define_save.imp - importing a metamodel from a
 * source file over the real HTTP pipeline: multipart upload, asynchronous analysis job, proposal, confirmation.
 *
 * Full Spring context + MockMvc with the real staging service, analysis job, importers (Ecore, neutral JSON, SQL
 * DDL, PlantUML) and canonical persistence on {@code @TempDir}. The oracle is the persisted metamodel JSON read from
 * disk. The analysis job runs on the application's real executor, so completion is polled (bounded wait).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelImportHttpPipelineIntegrationTests {

    private static final String BASE = "/api/metamodel-imports";
    private static final String PLANTUML = """
            @startuml
            class "Person" as M_Person {
              age : Integer [0..1]
            }
            class "Company" as M_Company {
              name : String [1]
            }
            enum "State" as M_State {
              ACTIVE = 1
            }
            M_Person --> "0..1" M_Company : employer
            @enduml
            """;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper mapper;

    private JsonNode startAndAwait(String fileName, String mediaType, byte[] content, String format, String targetId,
            boolean overwrite, boolean strict) throws Exception {
        MvcResult started = mockMvc.perform(multipart(BASE + "/analysis-jobs")
                        .file(new MockMultipartFile("file", fileName, mediaType, content))
                        .param("format", format).param("targetId", targetId)
                        .param("overwrite", Boolean.toString(overwrite)).param("strict", Boolean.toString(strict)))
                .andExpect(status().isOk()).andReturn();
        JsonNode first = mapper.readTree(started.getResponse().getContentAsByteArray());
        String jobId = first.get("jobId").asString();
        // The upload is registered as the first completed stage before any analysis runs.
        assertThat(first.get("stages").get(0).get("stageId").asString()).isEqualTo("FILE_RECEIVED");
        assertThat(first.get("stages").get(0).get("status").asString()).isEqualTo("COMPLETED");

        long deadline = System.nanoTime() + 60_000_000_000L;
        while (true) {
            MvcResult polled = mockMvc.perform(get(BASE + "/analysis-jobs/" + jobId)).andExpect(status().isOk()).andReturn();
            JsonNode job = mapper.readTree(polled.getResponse().getContentAsByteArray());
            String state = job.get("status").asString();
            if ("COMPLETED".equals(state) || "FAILED".equals(state)) return job;
            if (System.nanoTime() > deadline) throw new AssertionError("analysis job did not finish: " + job);
            Thread.sleep(25);
        }
    }

    private static Path metamodelFile(String id) throws Exception {
        Path directory = root.resolve("metamodels");
        if (!Files.isDirectory(directory)) return null;
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equals(id + ".metamodel.json")).findFirst().orElse(null);
        }
    }

    private List<String> classNames(Path file) throws Exception {
        List<String> names = new ArrayList<>();
        mapper.readTree(Files.readAllBytes(file)).get("classes").forEach(c -> names.add(c.get("name").asString()));
        return names;
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream in = MetamodelImportHttpPipelineIntegrationTests.class.getResourceAsStream(path)) {
            assertThat(in).as("test resource " + path).isNotNull();
            return in.readAllBytes();
        }
    }

    private JsonNode confirm(String jobId) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/analysis-jobs/" + jobId + "/import")).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private byte[] neutralJson(String name) throws Exception {
        MetaModel model = CanonicalMetamodelTestFixtures.valid("json-source", name, java.util.Map.of());
        ObjectNode node = (ObjectNode) mapper.valueToTree(model);
        node.put("artifactName", "json-source.ecore");
        node.put("namespaceUri", "urn:json-source");
        node.put("namespacePrefix", "jsonsource");
        return mapper.writeValueAsBytes(node);
    }

    @Test
    @DisplayName("metamodel_author_import.src: the formats endpoint lists the supported source formats")
    void formatsEndpointListsTheSupportedSources() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/formats")).andExpect(status().isOk()).andReturn();
        List<String> ids = new ArrayList<>();
        mapper.readTree(result.getResponse().getContentAsByteArray()).forEach(f -> ids.add(f.get("id").asString()));
        assertThat(ids).contains("ecore", "json", "plantuml", "markdown-agent");
        assertThat(ids).as("sql-ddl is not in dsl.imports.metamodel.allowed-formats").doesNotContain("sql-ddl");
    }

    @Test
    @DisplayName("metamodel_author_import.an / d1 / art + metamodel_define_save.imp: Ecore (auto-detected), analysed, nothing persisted until confirmed")
    void ecoreIsAnalysedAndPersistedOnlyAfterConfirmation() throws Exception {
        String id = "imp-ecore";
        JsonNode job = startAndAwait("sample.ecore", "application/xml", resource("/metamodels/sample.ecore"), "auto", id, false, false);

        assertThat(job.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(job.get("preview").get("detectedFormat").asString()).isEqualTo("ecore");
        assertThat(job.get("preview").get("metamodel").get("id").asString()).isEqualTo(id);
        assertThat(metamodelFile(id)).as("analysis alone must not persist anything").isNull();

        JsonNode saved = confirm(job.get("jobId").asString());
        assertThat(saved.get("id").asString()).isEqualTo(id);
        Path file = metamodelFile(id);
        assertThat(file).isNotNull();
        assertThat(classNames(file)).contains("NamedElement", "Model", "Node");
        mockMvc.perform(get("/api/canonical-metamodels/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("metamodel_define_save.imp: neutral JSON and PlantUML are imported through the same pipeline")
    void jsonAndPlantUmlAreImported() throws Exception {
        JsonNode json = startAndAwait("json-source.json", "application/json", neutralJson("Json Source"), "json", "imp-json", false, false);
        assertThat(json.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(metamodelFile("imp-json")).isNull();
        confirm(json.get("jobId").asString());
        assertThat(classNames(metamodelFile("imp-json"))).containsExactly("Root");

        JsonNode umlJob = startAndAwait("company.puml", "text/plain", PLANTUML.getBytes(StandardCharsets.UTF_8), "plantuml", "imp-uml", false, false);
        assertThat(umlJob.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(metamodelFile("imp-uml")).isNull();
        confirm(umlJob.get("jobId").asString());
        JsonNode persistedUml = mapper.readTree(Files.readAllBytes(metamodelFile("imp-uml")));
        assertThat(classNames(metamodelFile("imp-uml"))).containsExactlyInAnyOrder("Person", "Company");
        assertThat(persistedUml.get("enums")).hasSize(1);
        assertThat(persistedUml.get("relations")).hasSize(1);
    }

    @Test
    @DisplayName("metamodel_author_import.str: importing over an existing id conflicts without overwrite and replaces it with overwrite=true")
    void existingIdConflictsWithoutOverwriteAndIsReplacedWithOverwrite() throws Exception {
        String id = "imp-overwrite";
        JsonNode first = startAndAwait("first.json", "application/json", neutralJson("Original"), "json", id, false, false);
        confirm(first.get("jobId").asString());
        byte[] original = Files.readAllBytes(metamodelFile(id));
        assertThat(new String(original, StandardCharsets.UTF_8)).contains("Original");

        JsonNode second = startAndAwait("second.json", "application/json", neutralJson("Replaced"), "json", id, false, false);
        mockMvc.perform(post(BASE + "/analysis-jobs/" + second.get("jobId").asString() + "/import"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(Files.readAllBytes(metamodelFile(id))).isEqualTo(original);

        JsonNode third = startAndAwait("third.json", "application/json", neutralJson("Replaced"), "json", id, true, false);
        confirm(third.get("jobId").asString());
        String after = new String(Files.readAllBytes(metamodelFile(id)), StandardCharsets.UTF_8);
        assertThat(after).contains("Replaced").doesNotContain("Original");
    }

    @Test
    @DisplayName("metamodel_author_import.str / an: strict=true blocks an import with information loss; strict=false keeps it as a warning")
    void strictModeBlocksLossyReconstruction() throws Exception {
        // The PlantUML importer always declares IMPORT_LOSSY_* warnings (aliases and feature options are not preserved).
        byte[] lossy = PLANTUML.getBytes(StandardCharsets.UTF_8);

        JsonNode strict = startAndAwait("lossy.puml", "text/plain", lossy, "plantuml", "imp-strict", false, true);
        assertThat(strict.get("status").asString()).isEqualTo("FAILED");
        assertThat(strict.get("errorMessage").asString()).contains("Strict metamodel import rejected semantic loss");
        assertThat(metamodelFile("imp-strict")).isNull();

        JsonNode relaxed = startAndAwait("lossy.puml", "text/plain", lossy, "plantuml", "imp-relaxed", false, false);
        assertThat(relaxed.get("status").asString()).isEqualTo("COMPLETED");
        List<String> codes = new ArrayList<>();
        relaxed.get("preview").get("warnings").forEach(w -> codes.add(w.get("code").asString()));
        assertThat(codes).anyMatch(code -> code.startsWith("IMPORT_LOSSY"));
        confirm(relaxed.get("jobId").asString());
        assertThat(metamodelFile("imp-relaxed")).isNotNull();
    }
}

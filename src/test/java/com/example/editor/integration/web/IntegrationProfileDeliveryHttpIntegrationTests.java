package com.example.editor.integration.web;

import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.ARCHIVE_MANIFEST;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.PROFILES;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.compileArchive;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.createProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.emfProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.ensureMetamodel;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.sha256Hex;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.technology;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.text;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.unzip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.integration.InjectionPolicy;
import com.example.editor.integration.InjectionStrategy;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationArtifactSelection;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * integration_author_wizard.vf / .dl, integration_deliver.conf / .ana / .ver / .exe / .exp,
 * x_integrate_technology_native.prof / .chk / .gen and integration_readiness.load / .diag / .comp / .stg.
 *
 * Full Spring context + MockMvc over every dsl.* directory in a temporary folder. The canonical metamodel and the
 * event definitions are created through the real API and repositories; the profile is saved, analysed, compiled to a
 * ZIP and verified through the real controllers, services and stores. Oracles: the stored JSON file read from disk, the
 * ZIP entries unzipped and hashed by the test, the fixture class names and literal states.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IntegrationProfileDeliveryHttpIntegrationTests {

    private static final String MODULE_ROOT = "generated-integration/people-emf";
    private static final String EVENTS_ID = "people-events";

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper mapper;
    @Autowired private EventDefinitionModelRepository eventDefinitions;

    private void restoreEventDefinitions() {
        eventDefinitions.deleteIfExists("people");
        eventDefinitions.create(new EventDefinitionModel("1.0", EVENTS_ID, "people", "course.people", List.of(), Map.of()));
    }

    private String readiness(String profileId) throws Exception {
        return mockMvc.perform(get(technology(profileId) + "/readiness")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String stageValue(String json, String stage, String field) {
        List<String> values = JsonPath.read(json, "$.actions[?(@.stage=='" + stage + "')]." + field);
        assertThat(values).as(stage + "." + field).hasSize(1);
        return values.get(0);
    }

    private static List<String> list(String json, String path) {
        return JsonPath.read(json, path);
    }

    @Test
    @DisplayName("integration_author_wizard.vf / integration_deliver.conf + ana / x_integrate_technology_native.prof + chk: analysis lists errors, an invalid or incomplete profile is not saved, a valid one is saved with the name defaulted to the id")
    void profileIsAnalysedAndOnlySavedWhenCompleteAndValid() throws Exception {
        ensureMetamodel(mockMvc, mapper);
        Path stored = root.resolve("integrations").resolve("people-emf-wizard.integration.json");

        // Analysis of an invalid draft: wrong delivery mode for the projection tool and a missing required input.
        IntegrationTarget plantUml = new IntegrationTarget(IntegrationTechnology.PLANTUML, IntegrationTargetKind.DIAGRAM_PROJECTION, "",
                "people-host", "", IntegrationBuildSystem.NONE, List.of(), List.of(), "", Map.of());
        InjectionPolicy projection = new InjectionPolicy(InjectionStrategy.GENERATE_PROJECTION, "docs/diagrams", "", true, true, true, false, false, true);
        IntegrationProfileModel draft = new IntegrationProfileModel("1.0", "broken-draft", "people", "Broken", plantUml,
                IntegrationDeliveryMode.GENERATED_MODULE, Set.of(),
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, "people", true),
                        new IntegrationArtifactSelection(IntegrationArtifactKind.EVENT_DEFINITIONS, "missing-events", true)),
                List.of(), projection, Set.of(), Map.of());
        mockMvc.perform(post(PROFILES + "/check").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics[?(@.code=='PLANTUML_PROJECTION_ONLY')].severity").value("ERROR"))
                .andExpect(jsonPath("$.diagnostics[?(@.code=='UNRESOLVED_REQUIRED_ARTIFACT')].severity").value("ERROR"));

        // Saving the same draft is refused and nothing reaches the disk.
        mockMvc.perform(post(PROFILES).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft)))
                .andExpect(status().isBadRequest());
        assertThat(root.resolve("integrations").resolve("broken-draft.integration.json")).doesNotExist();

        // A profile without technology or without delivery mode cannot even be read as a profile.
        String withoutTechnology = "{\"id\":\"no-technology\",\"metamodelId\":\"people\",\"deliveryMode\":\"GENERATED_MODULE\",\"target\":{\"kind\":\"MODELING_RUNTIME\"}}";
        mockMvc.perform(post(PROFILES).contentType(MediaType.APPLICATION_JSON).content(withoutTechnology)).andExpect(status().isBadRequest());
        String withoutDelivery = "{\"id\":\"no-delivery\",\"metamodelId\":\"people\",\"target\":{\"technology\":\"EMF\",\"kind\":\"MODELING_RUNTIME\"}}";
        mockMvc.perform(post(PROFILES).contentType(MediaType.APPLICATION_JSON).content(withoutDelivery)).andExpect(status().isBadRequest());
        assertThat(root.resolve("integrations").resolve("no-technology.integration.json")).doesNotExist();
        assertThat(root.resolve("integrations").resolve("no-delivery.integration.json")).doesNotExist();

        // A valid profile is saved, replaced with an empty name (defaulted to the id) and read back.
        String etag = createProfile(mockMvc, mapper, emfProfile("people-emf-wizard", "people-emf", "people-emf", false, List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(mapper.writeValueAsString(emfProfile("people-emf-wizard", "people-emf", "people-emf", false, List.of())), Map.class);
        body.put("name", "");
        mockMvc.perform(put(PROFILES + "/people-emf-wizard").header(HttpHeaders.IF_MATCH, etag).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.name").value("people-emf-wizard"))
                .andExpect(jsonPath("$.checkReport.diagnostics[?(@.severity=='ERROR')]").isEmpty());

        String disk = Files.readString(stored);
        assertThat((String) JsonPath.read(disk, "$.name")).isEqualTo("people-emf-wizard");
        assertThat((String) JsonPath.read(disk, "$.target.technology")).isEqualTo("EMF");
        assertThat((String) JsonPath.read(disk, "$.deliveryMode")).isEqualTo("GENERATED_MODULE");
        mockMvc.perform(get(PROFILES + "/people-emf-wizard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("people-emf-wizard"))
                .andExpect(jsonPath("$.target.technology").value("EMF"))
                .andExpect(jsonPath("$.deliveryMode").value("GENERATED_MODULE"));
        mockMvc.perform(post(PROFILES + "/people-emf-wizard/check")).andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics[?(@.severity=='ERROR')]").isEmpty());

        // A replacement with an obsolete version is rejected and the stored document is untouched.
        byte[] before = Files.readAllBytes(stored);
        mockMvc.perform(put(PROFILES + "/people-emf-wizard").header(HttpHeaders.IF_MATCH, "\"obsolete\"").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isPreconditionFailed());
        assertThat(Files.readAllBytes(stored)).isEqualTo(before);
    }

    @Test
    @DisplayName("integration_readiness.load / .diag / .comp / .stg + integration_author_wizard.dl + integration_deliver.exe / .exp: a missing required input blocks generate, verify, preview and apply; fixing it opens them; execute stays external")
    void readinessBlocksDeliveryWhileARequiredInputIsMissingAndOpensAfterTheFix() throws Exception {
        ensureMetamodel(mockMvc, mapper);
        restoreEventDefinitions();
        String profileId = "people-emf-readiness";
        createProfile(mockMvc, mapper, emfProfile(profileId, "people-emf", "people-emf", false,
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.EVENT_DEFINITIONS, EVENTS_ID, true))));

        // Everything present: the project compiles and the stages open.
        String ready = readiness(profileId);
        assertThat((Boolean) JsonPath.read(ready, "$.profileValid")).isTrue();
        assertThat((Boolean) JsonPath.read(ready, "$.compilationReady")).isTrue();
        assertThat(list(ready, "$.blockers")).isEmpty();
        assertThat((String) JsonPath.read(ready, "$.technology")).isEqualTo("EMF");
        for (String stage : List.of("GENERATE", "VERIFY", "PREVIEW", "APPLY")) {
            assertThat(stageValue(ready, stage, "state")).as(stage).isEqualTo("READY");
        }
        assertThat(stageValue(ready, "GENERATE", "apiSuffix")).isEqualTo("/technology/compile");
        assertThat(stageValue(ready, "VERIFY", "apiSuffix")).isEqualTo("/technology/validate");
        assertThat(stageValue(ready, "PREVIEW", "apiSuffix")).isEqualTo("/technology/preview");
        assertThat(stageValue(ready, "APPLY", "apiSuffix")).isEqualTo("/technology/apply");
        assertThat(stageValue(ready, "EXPORT", "apiSuffix")).isEqualTo("/technology/compile");
        assertThat(stageValue(ready, "EXPORT", "state")).isIn("READY", "READY_WITH_WARNINGS");
        // Execution is never performed by HYDRA.
        assertThat(stageValue(ready, "EXECUTE", "state")).isEqualTo("EXTERNAL");
        List<Boolean> executable = JsonPath.read(ready, "$.actions[?(@.stage=='EXECUTE')].hydraExecutable");
        assertThat(executable).containsExactly(false);
        // The reported file count is the number of entries of the ZIP the user can download (minus the ZIP's own manifest).
        Map<String, byte[]> archive = unzip(compileArchive(mockMvc, profileId));
        assertThat((Integer) JsonPath.read(ready, "$.generatedFileCount")).isEqualTo(archive.size() - 1);
        assertThat((Integer) JsonPath.read(ready, "$.nativeVerificationStepCount")).isEqualTo(1);
        assertThat((Boolean) JsonPath.read(ready, "$.nativeVerificationAvailable")).isTrue();

        // The required event definitions disappear: the same profile is blocked and compilation is refused.
        eventDefinitions.deleteIfExists("people");
        String blocked = readiness(profileId);
        assertThat((Boolean) JsonPath.read(blocked, "$.profileValid")).isFalse();
        assertThat((Boolean) JsonPath.read(blocked, "$.compilationReady")).isFalse();
        assertThat((Integer) JsonPath.read(blocked, "$.generatedFileCount")).isZero();
        assertThat(list(blocked, "$.blockers")).anyMatch(value -> value.startsWith("UNRESOLVED_REQUIRED_ARTIFACT"));
        for (String stage : List.of("GENERATE", "VERIFY", "PREVIEW", "APPLY")) {
            assertThat(stageValue(blocked, stage, "state")).as(stage).isEqualTo("BLOCKED");
        }
        assertThat(stageValue(blocked, "EXECUTE", "state")).isEqualTo("EXTERNAL");
        mockMvc.perform(post(technology(profileId) + "/compile")).andExpect(status().is(422));

        // Restoring the input reopens the stages.
        restoreEventDefinitions();
        String reopened = readiness(profileId);
        assertThat(list(reopened, "$.blockers")).isEmpty();
        assertThat(stageValue(reopened, "GENERATE", "state")).isEqualTo("READY");
        assertThat(stageValue(reopened, "APPLY", "state")).isEqualTo("READY");
    }

    @Test
    @DisplayName("integration_readiness.diag / .stg: a profile warning does not block, it turns the open stages into ready-with-warnings")
    void warningsKeepTheStagesOpenButFlagThem() throws Exception {
        ensureMetamodel(mockMvc, mapper);
        String profileId = "people-emf-warnings";
        createProfile(mockMvc, mapper, emfProfile(profileId, "people-emf", "people-emf", true, List.of()));

        String json = readiness(profileId);
        assertThat(list(json, "$.warnings")).anyMatch(value -> value.startsWith("MODIFIES_GENERATED_FILES"));
        assertThat(list(json, "$.blockers")).isEmpty();
        assertThat((Boolean) JsonPath.read(json, "$.compilationReady")).isTrue();
        for (String stage : List.of("GENERATE", "VERIFY", "PREVIEW", "APPLY")) {
            assertThat(stageValue(json, stage, "state")).as(stage).isEqualTo("READY_WITH_WARNINGS");
        }
        assertThat(stageValue(json, "EXECUTE", "state")).isEqualTo("EXTERNAL");
    }

    @Test
    @DisplayName("integration_readiness.comp / .stg: a projection-only technology is not applicable for host stages and is never compiled")
    void projectionTargetsAreNotApplicableAndNotCompiled() throws Exception {
        ensureMetamodel(mockMvc, mapper);
        String profileId = "people-plantuml-projection";
        IntegrationTarget target = new IntegrationTarget(IntegrationTechnology.PLANTUML, IntegrationTargetKind.DIAGRAM_PROJECTION, "",
                "people-host", "", IntegrationBuildSystem.NONE, List.of(), List.of(), "", Map.of());
        InjectionPolicy policy = new InjectionPolicy(InjectionStrategy.GENERATE_PROJECTION, "docs/diagrams", "", true, true, true, false, false, true);
        createProfile(mockMvc, mapper, new IntegrationProfileModel("1.0", profileId, "people", "People PlantUML", target,
                IntegrationDeliveryMode.PROJECTION_ONLY, Set.of(),
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, "people", true)),
                List.of(), policy, Set.of(), Map.of()));

        String json = readiness(profileId);
        assertThat((String) JsonPath.read(json, "$.technology")).isEqualTo("PLANTUML");
        assertThat((Boolean) JsonPath.read(json, "$.compilationReady")).isFalse();
        assertThat((Integer) JsonPath.read(json, "$.generatedFileCount")).isZero();
        assertThat(list(json, "$.blockers")).isEmpty();
        for (String stage : List.of("VERIFY", "PREVIEW", "APPLY", "EXECUTE")) {
            assertThat(stageValue(json, stage, "state")).as(stage).isEqualTo("NOT_APPLICABLE");
        }
        assertThat(stageValue(json, "GENERATE", "state")).isEqualTo("READY");
    }

    @Test
    @DisplayName("integration_deliver.exp / x_integrate_technology_native.gen: compile returns a real ZIP whose entries match their own hashes and contain the generated module")
    void compileDownloadsAZipThatMatchesItsManifest() throws Exception {
        ensureMetamodel(mockMvc, mapper);
        String profileId = "people-emf-compile";
        createProfile(mockMvc, mapper, emfProfile(profileId, "people-emf", "people-emf", false, List.of()));

        byte[] first = mockMvc.perform(post(technology(profileId) + "/compile")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(profileId + "-technology.zip")))
                .andReturn().getResponse().getContentAsByteArray();
        Map<String, byte[]> entries = unzip(first);

        assertThat(entries.keySet()).contains(MODULE_ROOT + "/pom.xml", MODULE_ROOT + "/src/main/resources/model/people.ecore",
                MODULE_ROOT + "/src/main/resources/hydra/metamodel.json", "META-INF/hydra-roundtrip.json",
                "META-INF/hydra-project-validation.json", ARCHIVE_MANIFEST);
        String manifest = text(entries.get(ARCHIVE_MANIFEST));
        List<String> paths = JsonPath.read(manifest, "$.files[*].path");
        List<String> hashes = JsonPath.read(manifest, "$.files[*].sha256");
        assertThat(paths).containsExactlyInAnyOrderElementsOf(entries.keySet().stream().filter(name -> !name.equals(ARCHIVE_MANIFEST)).toList());
        for (int index = 0; index < paths.size(); index++) {
            assertThat(sha256Hex(entries.get(paths.get(index)))).as(paths.get(index)).isEqualTo(hashes.get(index));
        }
        String ecore = text(entries.get(MODULE_ROOT + "/src/main/resources/model/people.ecore"));
        assertThat(ecore).contains("name=\"Person\"").contains("name=\"Company\"");
        assertThat(text(entries.get(MODULE_ROOT + "/pom.xml"))).contains("<artifactId>people-emf</artifactId>");
        assertThat((String) JsonPath.read(text(entries.get("META-INF/hydra-roundtrip.json")), "$.profileId")).isEqualTo(profileId);

        // The package is deterministic and an unknown profile cannot be compiled.
        assertThat(compileArchive(mockMvc, profileId)).isEqualTo(first);
        mockMvc.perform(post(technology("does-not-exist") + "/compile")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("integration_deliver.ver: structural validation of the generated project passes, runs no native step and reports how many native steps exist")
    void structuralValidationPassesAndCountsTheNativeSteps() throws Exception {
        ensureMetamodel(mockMvc, mapper);
        String profileId = "people-emf-validate";
        createProfile(mockMvc, mapper, emfProfile(profileId, "people-emf", "people-emf", false, List.of()));

        String json = mockMvc.perform(post(technology(profileId) + "/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"STRUCTURAL\",\"timeoutSeconds\":0,\"keepWorkspace\":false,\"environment\":{}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(json, "$.status")).isEqualTo("PASSED");
        assertThat((String) JsonPath.read(json, "$.metadata.validationMode")).isEqualTo("STRUCTURAL");
        assertThat((String) JsonPath.read(json, "$.metadata.nativeSteps")).isEqualTo("1");
        assertThat(list(json, "$.steps[*].id")).containsExactlyInAnyOrder("emf-pom", "emf-ecore-load", "emf-canonical-source");
        assertThat(list(json, "$.steps[*].status")).containsOnly("PASSED");
        // The temporary workspace of the validation is removed afterwards.
        assertThat(Path.of((String) JsonPath.read(json, "$.metadata.workspace"))).doesNotExist();
    }
}

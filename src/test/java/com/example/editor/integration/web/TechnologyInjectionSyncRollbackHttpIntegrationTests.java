package com.example.editor.integration.web;

import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.PROFILES;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.assertSameFiles;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.createProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.emfProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.ensureMetamodel;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.filesUnder;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.generatedFiles;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.technology;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * integration_deliver.pre / .app / .rt and x_integrate_technology_native.pre / .app / .rt - preview, apply, rollback and
 * round-trip synchronization of a generated module over real HTTP.
 *
 * Full Spring context + MockMvc; the injection root is a folder under {@code @TempDir}. The oracle is the host folder on
 * disk compared byte by byte with the files of the ZIP that the compile endpoint returns for the same profile, the
 * manifest JSON written on disk, and the manual edits the test makes itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TechnologyInjectionSyncRollbackHttpIntegrationTests {

    private static final String MODULE_ROOT = "generated-integration/people-emf";
    private static final String README = MODULE_ROOT + "/README.md";
    private static final String POM = MODULE_ROOT + "/pom.xml";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper mapper;

    private Path host(String name) {
        return root.resolve("injection-targets").resolve(name);
    }

    private String seed(String profileId) throws Exception {
        ensureMetamodel(mockMvc, mapper);
        return createProfile(mockMvc, mapper, emfProfile(profileId, "people-emf", "people-emf", false, List.of()));
    }

    private String postJson(String url, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(url).contentType(JSON).content(body)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static List<String> list(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static String kind(String json, String path) {
        List<String> kinds = JsonPath.read(json, "$.changes[?(@.path=='" + path + "')].kind");
        assertThat(kinds).as(path).hasSize(1);
        return kinds.get(0);
    }

    private static String target(String directory) {
        return "{\"targetDirectory\":\"" + directory + "\"}";
    }

    private static String sync(String directory) {
        return "{\"targetDirectory\":\"" + directory + "\",\"selectedPaths\":[],\"preserveManualRegions\":false}";
    }

    private static long count(Path directory) throws Exception {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.count();
        }
    }

    @Test
    @DisplayName("integration_deliver.pre / x_integrate_technology_native.pre: the preview plans every generated file as an addition and writes nothing")
    void previewPlansTheFilesWithoutWritingAnything() throws Exception {
        String profileId = "people-emf-preview";
        seed(profileId);
        Map<String, byte[]> expected = generatedFiles(mockMvc, profileId);
        Path target = Files.createDirectories(host("host-preview"));

        String body = postJson(technology(profileId) + "/preview", target("host-preview"), 200);

        assertThat((String) JsonPath.read(body, "$.plan.status")).isEqualTo("READY");
        assertThat(list(body, "$.plan.fileChanges[*].path")).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(list(body, "$.plan.fileChanges[*].changeKind")).containsOnly("ADD");
        assertThat(list(body, "$.manualConflictFiles")).isEmpty();
        assertThat(count(target)).as("the host folder stays empty after a preview").isZero();
    }

    @Test
    @DisplayName("integration_deliver.app / x_integrate_technology_native.app: apply writes exactly the generated files and a manifest that records them")
    void applyWritesTheGeneratedFilesAndTheManifest() throws Exception {
        String profileId = "people-emf-apply";
        seed(profileId);
        Map<String, byte[]> expected = generatedFiles(mockMvc, profileId);
        Path target = host("host-apply");

        String body = postJson(technology(profileId) + "/apply", target("host-apply"), 200);

        assertThat(list(body, "$.writtenFiles")).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertSameFiles(filesUnder(target, ".hydra"), expected);
        String manifestId = JsonPath.read(body, "$.manifest.id");
        Path manifest = target.resolve(".hydra/injections/manifests/" + manifestId + ".json");
        assertThat(manifest).isRegularFile();
        assertThat(list(Files.readString(manifest), "$.files[*].path")).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(target.resolve(".hydra/injections/managed-files.json")).isRegularFile();
    }

    @Test
    @DisplayName("integration_deliver.app / .pre / x_integrate_technology_native.app: a manual file in the way blocks the preview and apply is refused without writing anything")
    void manualConflictBlocksApplyAndWritesNothing() throws Exception {
        String profileId = "people-emf-conflict";
        seed(profileId);
        Path manualPom = host("host-conflict").resolve(POM);
        Files.createDirectories(manualPom.getParent());
        Files.writeString(manualPom, "<project>manual</project>", StandardCharsets.UTF_8);

        String preview = postJson(technology(profileId) + "/preview", target("host-conflict"), 200);
        assertThat((String) JsonPath.read(preview, "$.plan.status")).isEqualTo("BLOCKED");
        assertThat(list(preview, "$.manualConflictFiles")).containsExactly(POM);

        postJson(technology(profileId) + "/apply", target("host-conflict"), 422);

        Map<String, byte[]> after = filesUnder(host("host-conflict"));
        assertThat(after.keySet()).containsExactly(POM);
        assertThat(text(after.get(POM))).isEqualTo("<project>manual</project>");
    }

    @Test
    @DisplayName("integration_deliver.app / x_integrate_technology_native.app: rollback by manifest removes what apply added; unknown manifests and paths outside the injection root are rejected")
    void rollbackRestoresTheInitialStateAndUnsafeRequestsAreRejected() throws Exception {
        String profileId = "people-emf-rollback";
        seed(profileId);
        Map<String, byte[]> expected = generatedFiles(mockMvc, profileId);
        Path target = host("host-rollback");

        String applied = postJson(technology(profileId) + "/apply", target("host-rollback"), 200);
        assertSameFiles(filesUnder(target, ".hydra"), expected);
        String manifestId = JsonPath.read(applied, "$.manifest.id");

        String rolledBack = postJson(technology(profileId) + "/rollback/" + manifestId, target("host-rollback"), 200);

        assertThat(list(rolledBack, "$.deletedFiles")).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(filesUnder(target, ".hydra")).as("nothing but the HYDRA bookkeeping folder is left").isEmpty();

        postJson(technology(profileId) + "/rollback/manifest-that-does-not-exist", target("host-rollback"), 422);
        postJson(technology(profileId) + "/preview", target("../outside"), 400);
        postJson(technology(profileId) + "/apply", target("../outside"), 400);
        assertThat(root.resolve("outside")).doesNotExist();
    }

    @Test
    @DisplayName("integration_deliver.rt / x_integrate_technology_native.rt: a manual edit is detected, synchronization is blocked until the conflict is decided, the decision is applied and can be undone")
    void roundTripDetectsManualEditsAndAppliesAnExplicitDecision() throws Exception {
        String profileId = "people-emf-sync";
        String etag = seed(profileId);
        Path target = host("host-sync");
        postJson(technology(profileId) + "/apply", target("host-sync"), 200);
        String originalPom = text(Files.readAllBytes(target.resolve(POM)));
        assertThat(originalPom).contains("<artifactId>people-emf</artifactId>");

        // The user edits a managed file by hand and the profile changes the module name used in the build descriptor.
        Files.writeString(target.resolve(README), "manual edit\n", StandardCharsets.UTF_8);
        String v2 = mapper.writeValueAsString(emfProfile(profileId, "people-emf-v2", "people-emf", false, List.of()));
        mockMvc.perform(put(PROFILES + "/" + profileId).header(HttpHeaders.IF_MATCH, etag).contentType(JSON).content(v2))
                .andExpect(status().isOk());
        Map<String, byte[]> regenerated = generatedFiles(mockMvc, profileId);
        assertThat(text(regenerated.get(POM))).contains("<artifactId>people-emf-v2</artifactId>");

        // Preview of the synchronization: the edited file is a conflict, the build descriptor can be regenerated.
        String preview = postJson(technology(profileId) + "/sync/preview", sync("host-sync"), 200);
        assertThat(kind(preview, README)).isEqualTo("MANUAL_CONFLICT");
        assertThat(kind(preview, POM)).isEqualTo("REGENERATE");
        assertThat(list(preview, "$.conflicts")).contains(README).doesNotContain(POM);

        // Applying without deciding is refused and leaves both files as they were.
        postJson(technology(profileId) + "/sync/apply", sync("host-sync"), 422);
        assertThat(text(Files.readAllBytes(target.resolve(README)))).isEqualTo("manual edit\n");
        assertThat(text(Files.readAllBytes(target.resolve(POM)))).isEqualTo(originalPom);

        // The resolution plan lists the conflict and cannot be applied without a decision.
        String undecided = postJson(technology(profileId) + "/sync/resolve", "{\"targetDirectory\":\"host-sync\",\"decisions\":[]}", 200);
        assertThat((Boolean) JsonPath.read(undecided, "$.canApply")).isFalse();
        assertThat(list(undecided, "$.unresolved[*].artifactPath")).contains(README);
        postJson(technology(profileId) + "/sync/resolve/apply", "{\"targetDirectory\":\"host-sync\",\"decisions\":[]}", 422);
        assertThat(text(Files.readAllBytes(target.resolve(README)))).isEqualTo("manual edit\n");

        // Keeping the HYDRA version for every pending conflict applies the regenerated files.
        List<String> elementIds = list(undecided, "$.unresolved[*].elementId");
        List<String> artifactPaths = list(undecided, "$.unresolved[*].artifactPath");
        StringBuilder decisions = new StringBuilder();
        for (int index = 0; index < artifactPaths.size(); index++) {
            if (index > 0) decisions.append(',');
            decisions.append("{\"conflictKey\":\"").append(elementIds.get(index)).append('@').append(artifactPaths.get(index))
                    .append("\",\"choice\":\"KEEP_HYDRA\",\"rationale\":\"Keep the regenerated version\"}");
        }
        String decided = "{\"targetDirectory\":\"host-sync\",\"decisions\":[" + decisions + "]}";
        String applied = postJson(technology(profileId) + "/sync/resolve/apply", decided, 200);
        assertThat((Boolean) JsonPath.read(applied, "$.successful")).isTrue();
        assertThat(list(applied, "$.affectedPaths")).contains(README, POM);
        assertThat(Files.readAllBytes(target.resolve(README))).isEqualTo(regenerated.get(README));
        assertThat(Files.readAllBytes(target.resolve(POM))).isEqualTo(regenerated.get(POM));
        String after = postJson(technology(profileId) + "/sync/preview", sync("host-sync"), 200);
        assertThat(kind(after, README)).isEqualTo("UNCHANGED");
        assertThat(kind(after, POM)).isEqualTo("UNCHANGED");

        // The resolution can be undone: the manual edit and the old descriptor come back.
        String operationId = JsonPath.read(applied, "$.operationId");
        String undone = postJson(technology(profileId) + "/sync/resolve/rollback/" + operationId, "{\"targetDirectory\":\"host-sync\"}", 200);
        assertThat((Boolean) JsonPath.read(undone, "$.rollback")).isTrue();
        assertThat(text(Files.readAllBytes(target.resolve(README)))).isEqualTo("manual edit\n");
        assertThat(text(Files.readAllBytes(target.resolve(POM)))).isEqualTo(originalPom);
    }
}

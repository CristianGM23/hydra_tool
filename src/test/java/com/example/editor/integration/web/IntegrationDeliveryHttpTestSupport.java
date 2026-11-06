package com.example.editor.integration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.editor.definition.EditorDefinitionTestFixtures;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only helper shared by the HTTP delivery tests of the integration package: seeds the canonical metamodel
 * "people", builds EMF profiles, downloads and unzips the generated archive and lists files on disk. It carries no
 * assertions about the behaviour under test.
 */
public final class IntegrationDeliveryHttpTestSupport {
    public static final String METAMODEL_ID = "people";
    public static final String PROFILES = "/api/canonical-metamodels/people/integrations";
    public static final String ARCHIVE_MANIFEST = "META-INF/hydra-technology-compilation.json";

    private IntegrationDeliveryHttpTestSupport() { }

    public static String technology(String profileId) {
        return PROFILES + "/" + profileId + "/technology";
    }

    /** Creates the canonical metamodel through the real HTTP API when it does not exist yet. */
    public static void ensureMetamodel(MockMvc mockMvc, JsonMapper mapper) throws Exception {
        int existing = mockMvc.perform(get("/api/canonical-metamodels/" + METAMODEL_ID)).andReturn().getResponse().getStatus();
        if (existing == 200) return;
        mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(EditorDefinitionTestFixtures.metamodel())))
                .andExpect(status().isCreated());
    }

    /** EMF profile with the safe injection defaults; only the metamodel plus the given extra artifacts are selected. */
    public static IntegrationProfileModel emfProfile(String id, String targetModule, String policyModule,
            boolean allowModifyGeneratedFiles, List<IntegrationArtifactSelection> extraArtifacts) {
        List<IntegrationArtifactSelection> artifacts = new ArrayList<>();
        artifacts.add(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, METAMODEL_ID, true));
        artifacts.addAll(extraArtifacts);
        IntegrationTarget target = new IntegrationTarget(IntegrationTechnology.EMF, IntegrationTargetKind.MODELING_RUNTIME, "",
                "people-host", targetModule, IntegrationBuildSystem.MAVEN, List.of(), List.of(), "", Map.of());
        InjectionPolicy policy = new InjectionPolicy(InjectionStrategy.ADD_MODULE, "generated-integration", policyModule,
                true, true, true, false, allowModifyGeneratedFiles, true);
        return new IntegrationProfileModel("1.0", id, METAMODEL_ID, "People EMF", target, IntegrationDeliveryMode.GENERATED_MODULE,
                Set.of(), artifacts, List.of(), policy, Set.of(), Map.of());
    }

    /** Saves a profile through POST and returns the ETag header of the stored document. */
    public static String createProfile(MockMvc mockMvc, JsonMapper mapper, IntegrationProfileModel profile) throws Exception {
        MvcResult result = mockMvc.perform(post(PROFILES).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(profile)))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    /** Downloads the generated technology archive of a saved profile. */
    public static byte[] compileArchive(MockMvc mockMvc, String profileId) throws Exception {
        return mockMvc.perform(post(technology(profileId) + "/compile")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** Generated files of a saved profile as {@code path -> bytes}, read from the archive (without its own manifest). */
    public static Map<String, byte[]> generatedFiles(MockMvc mockMvc, String profileId) throws Exception {
        Map<String, byte[]> entries = unzip(compileArchive(mockMvc, profileId));
        entries.remove(ARCHIVE_MANIFEST);
        return entries;
    }

    public static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    /** Regular files below {@code root} as {@code relative/path -> bytes}, skipping the given top-level folders. */
    public static Map<String, byte[]> filesUnder(Path root, String... ignoredTopLevel) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        if (!Files.isDirectory(root)) return result;
        Set<String> ignored = Set.of(ignoredTopLevel);
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (ignored.contains(relative.split("/")[0])) continue;
                result.put(relative, Files.readAllBytes(path));
            }
        }
        return result;
    }

    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String text(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    public static void assertSameFiles(Map<String, byte[]> actual, Map<String, byte[]> expected) {
        assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((path, bytes) -> assertThat(actual.get(path)).as(path).isEqualTo(bytes));
    }
}

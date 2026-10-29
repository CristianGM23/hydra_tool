package com.example.editor.metamodel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
import org.springframework.test.web.servlet.MvcResult;

import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * metamodel_author_wizard.va / .sv, metamodel_define_save.ok / .fix - the save gate of a metamodel over real HTTP.
 *
 * Full Spring context + MockMvc through the real controller, application service, neutral validator, JSON
 * repository and validation-receipt store; every {@code dsl.*} directory lives in a {@code @TempDir}. The oracle is
 * independent: files are read from disk and the SHA-256 ETag is recomputed here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelAuthoringSaveGateHttpIntegrationTests {

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

    private static MetaModel invalidModel(String id) {
        MetaClass first = new MetaClass("class-a", "Dup", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass second = new MetaClass("class-b", "Dup", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation ab = new MetaRelation("inheritance-a", "", "class-a", "class-b", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        MetaRelation ba = new MetaRelation("inheritance-b", "", "class-b", "class-a", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, "Broken " + id, List.of(first, second), List.of(), List.of(), List.of(ab, ba), Map.of());
    }

    private static Path metamodelFile(String id) throws Exception {
        Path directory = root.resolve("metamodels");
        if (!Files.isDirectory(directory)) return null;
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equals(id + ".metamodel.json")).findFirst().orElse(null);
        }
    }

    private static String sha256Etag(byte[] content) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
    }

    @Test
    @DisplayName("metamodel_author_wizard.va / metamodel_define_save.ok: inheritance cycle + duplicate names are rejected with the validator codes and nothing is written")
    void invalidMetamodelIsRejectedWithValidatorCodesAndNoFile() throws Exception {
        String id = "gate-invalid";
        String body = mapper.writeValueAsString(invalidModel(id));

        mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("CLASSIFIER_NAME_DUPLICATE")))
                .andExpect(jsonPath("$.message").value(containsString("INHERITANCE_CYCLE")));
        assertThat(metamodelFile(id)).isNull();

        // PUT is gated by the same validation before it reaches the repository.
        mockMvc.perform(put("/api/canonical-metamodels/" + id).header(HttpHeaders.IF_MATCH, "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(metamodelFile(id)).isNull();

        mockMvc.perform(get("/api/canonical-metamodels/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("metamodel_author_wizard.sv / metamodel_define_save.fix: after correcting the model the save returns 201, an ETag and the document on disk")
    void correctedMetamodelIsSavedWithEtagAndFile() throws Exception {
        String id = "gate-fixed";
        mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invalidModel(id))))
                .andExpect(status().is(422));
        assertThat(metamodelFile(id)).isNull();

        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/canonical-metamodels/" + id))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();

        Path file = metamodelFile(id);
        assertThat(file).isNotNull();
        byte[] raw = Files.readAllBytes(file);
        JsonNode stored = mapper.readTree(raw);
        assertThat(stored.get("id").asString()).isEqualTo(id);
        assertThat(stored.get("name").asString()).isEqualTo("Model " + id);
        assertThat(created.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo(sha256Etag(raw));

        mockMvc.perform(get("/api/canonical-metamodels/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, sha256Etag(raw)))
                .andExpect(jsonPath("$.classes[0].name").value("Root"));
    }

    @Test
    @DisplayName("metamodel_define_save.ok: PUT with an obsolete ETag answers 412 and the stored content is intact")
    void obsoleteEtagAnswers412AndKeepsTheStoredContent() throws Exception {
        String id = "gate-stale";
        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id))))
                .andExpect(status().isCreated()).andReturn();
        String firstEtag = created.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(put("/api/canonical-metamodels/" + id).header(HttpHeaders.IF_MATCH, firstEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id, "Second", Map.of()))))
                .andExpect(status().isOk());
        byte[] afterSecond = Files.readAllBytes(metamodelFile(id));

        mockMvc.perform(put("/api/canonical-metamodels/" + id).header(HttpHeaders.IF_MATCH, firstEtag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id, "Stale write", Map.of()))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));

        assertThat(Files.readAllBytes(metamodelFile(id))).isEqualTo(afterSecond);
        assertThat(new String(afterSecond, StandardCharsets.UTF_8)).contains("Second").doesNotContain("Stale write");
    }

    @Test
    @DisplayName("metamodel_author_wizard.va: validating the stored metamodel persists a current, valid receipt")
    void validatingTheStoredMetamodelWritesAValidReceipt() throws Exception {
        String id = "gate-receipt";
        mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id))))
                .andExpect(status().isCreated());
        Path receipt = root.resolve("metamodel-validation").resolve(id + ".validation.json");
        assertThat(receipt).doesNotExist();
        mockMvc.perform(get("/api/canonical-metamodels/" + id + "/validation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validated").value(false))
                .andExpect(jsonPath("$.generationReady").value(false));

        mockMvc.perform(post("/api/canonical-metamodels/" + id + "/validation/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.summary.errors").value(0));

        assertThat(receipt).isRegularFile();
        JsonNode persisted = mapper.readTree(Files.readAllBytes(receipt));
        assertThat(persisted.get("metamodelId").asString()).isEqualTo(id);
        assertThat(persisted.get("valid").asBoolean()).isTrue();
        assertThat(persisted.get("fingerprint").asString()).matches("[0-9a-f]{64}");
        mockMvc.perform(get("/api/canonical-metamodels/" + id + "/validation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validated").value(true))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.stale").value(false));
    }
}

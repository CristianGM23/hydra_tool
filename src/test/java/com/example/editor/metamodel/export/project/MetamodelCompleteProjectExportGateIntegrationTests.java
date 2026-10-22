package com.example.editor.metamodel.export.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * metamodel_export_generate.chk / .fix / .gen - the validation gate that protects complete project generation.
 *
 * Full Spring context + MockMvc: real validation receipts on disk, real project export service and compiler. The
 * receipt is renewed by validating the stored metamodel (validateStored), NOT by saving it; this is asserted by
 * comparing the receipt bytes on disk before/after a save. The generated ZIP is opened and inspected independently.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelCompleteProjectExportGateIntegrationTests {

    private static final String REQUIRES = "requires a successful current metamodel validation";

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

    private String save(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id))))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private void validate(String id) throws Exception {
        mockMvc.perform(post("/api/canonical-metamodels/" + id + "/validation/run")).andExpect(status().isOk());
    }

    private Path receipt(String id) {
        return root.resolve("metamodel-validation").resolve(id + ".validation.json");
    }

    private static List<String> zipEntryNames(byte[] zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) names.add(entry.getName());
        }
        return names;
    }

    private static byte[] zipEntry(byte[] zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.getName().equals(name)) return in.readAllBytes();
            }
        }
        throw new AssertionError("ZIP entry not found: " + name);
    }

    private byte[] exportOk(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/metamodels/" + id + "/project-exports/emf-eclipse"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentType()).contains("application/zip");
        return result.getResponse().getContentAsByteArray();
    }

    @Test
    @DisplayName("metamodel_export_generate.chk: a saved but never validated metamodel cannot generate a project")
    void exportWithoutValidationIsRejectedAndProducesNoZip() throws Exception {
        String id = "gen-unvalidated";
        save(id);
        assertThat(receipt(id)).doesNotExist();

        MvcResult result = mockMvc.perform(post("/api/metamodels/" + id + "/project-exports/emf-eclipse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(REQUIRES)))
                .andReturn();
        assertThat(result.getResponse().getContentType()).doesNotContain("zip");
        assertThat(receipt(id)).doesNotExist();
    }

    @Test
    @DisplayName("metamodel_export_generate.gen / chk: validating the stored metamodel enables the export, which returns a real ZIP with gate evidence")
    void validatedMetamodelExportsAZipWithGateEvidence() throws Exception {
        String id = "gen-validated";
        save(id);
        validate(id);

        byte[] zip = exportOk(id);
        assertThat(zip.length).isGreaterThan(4);
        assertThat(zip[0]).isEqualTo((byte) 'P');
        assertThat(zip[1]).isEqualTo((byte) 'K');
        List<String> names = zipEntryNames(zip);
        assertThat(names).contains("META-INF/hydra-metamodel-generation-gate.json", "META-INF/hydra-project-generation-snapshot.json");
        JsonNode gate = mapper.readTree(zipEntry(zip, "META-INF/hydra-metamodel-generation-gate.json"));
        assertThat(gate.get("metamodelId").asString()).isEqualTo(id);
        assertThat(gate.get("projectExport").asString()).isEqualTo("emf-eclipse");
        assertThat(gate.get("metamodelValidation").get("generationReady").asBoolean()).isTrue();
        assertThat(gate.get("metamodelValidation").get("current").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("metamodel_export_generate.fix / d1: saving does not renew the receipt (export rejected as obsolete); validating again does")
    void saveAloneDoesNotRenewTheReceiptButValidateStoredDoes() throws Exception {
        String id = "gen-modified";
        String etag = save(id);
        validate(id);
        exportOk(id);
        byte[] receiptBefore = Files.readAllBytes(receipt(id));
        String fingerprintBefore = mapper.readTree(receiptBefore).get("fingerprint").asString();

        mockMvc.perform(put("/api/canonical-metamodels/" + id).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(CanonicalMetamodelTestFixtures.valid(id, "Changed after validation", Map.of()))))
                .andExpect(status().isOk());

        assertThat(Files.readAllBytes(receipt(id))).isEqualTo(receiptBefore);
        mockMvc.perform(get("/api/canonical-metamodels/" + id + "/validation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.generationReady").value(false));
        mockMvc.perform(post("/api/metamodels/" + id + "/project-exports/emf-eclipse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(REQUIRES)))
                .andExpect(jsonPath("$.message").value(containsString("changed after its last validation")));

        validate(id);
        String fingerprintAfter = mapper.readTree(Files.readAllBytes(receipt(id))).get("fingerprint").asString();
        assertThat(fingerprintAfter).matches("[0-9a-f]{64}").isNotEqualTo(fingerprintBefore);
        assertThat(zipEntryNames(exportOk(id))).contains("META-INF/hydra-metamodel-generation-gate.json");
    }

    @Test
    @DisplayName("metamodel_export_generate.chk: a stored metamodel with blocking errors is validated as not ready and cannot generate")
    void metamodelWithErrorsNeverEnablesGeneration() throws Exception {
        String id = "gen-broken";
        save(id);
        // A document with errors cannot be saved through the API; it is planted on disk, as a hand-edited file would be.
        Path file;
        try (var stream = Files.list(root.resolve("metamodels"))) {
            file = stream.filter(p -> p.getFileName().toString().equals(id + ".metamodel.json")).findFirst().orElseThrow();
        }
        MetaClass a = new MetaClass("class-a", "A", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass b = new MetaClass("class-b", "B", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation ab = new MetaRelation("inheritance-a", "", "class-a", "class-b", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        MetaRelation ba = new MetaRelation("inheritance-b", "", "class-b", "class-a", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        Files.writeString(file, mapper.writeValueAsString(new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, "Broken", List.of(a, b),
                List.of(), List.of(), List.of(ab, ba), Map.of())));

        mockMvc.perform(post("/api/canonical-metamodels/" + id + "/validation/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.generationReady").value(false));

        JsonNode persisted = mapper.readTree(Files.readAllBytes(receipt(id)));
        assertThat(persisted.get("valid").asBoolean()).isFalse();
        assertThat(persisted.get("generationReady").asBoolean()).isFalse();
        mockMvc.perform(post("/api/metamodels/" + id + "/project-exports/emf-eclipse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(REQUIRES)))
                .andExpect(jsonPath("$.message").value(containsString("blocking problems")));
    }
}

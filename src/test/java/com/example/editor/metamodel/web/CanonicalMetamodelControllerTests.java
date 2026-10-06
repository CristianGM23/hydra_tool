package com.example.editor.metamodel.web;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RuntimeMetamodelTestSupport;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.web.ApiExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba la API canónica provisional y sus códigos HTTP.
 */
class CanonicalMetamodelControllerTests {

    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        jsonMapper = JsonMapper.builder().build();
        JsonCanonicalMetamodelRepository repository = new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), jsonMapper);
        CanonicalMetamodelApplicationService service = new CanonicalMetamodelApplicationService(repository, new NeutralMetamodelValidator());
        MetamodelLoader loader = new MetamodelLoader(new EmfResourceSetFactory());
        MetamodelRegistryService runtimeRegistry = RuntimeMetamodelTestSupport.fixedRegistry(storage, loader, java.util.List.of());
        mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalMetamodelController(service, runtimeRegistry)).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void createsCanonicalMetamodelWithLocationAndEtag() throws Exception {
        mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON).content(json(CanonicalMetamodelTestFixtures.valid("sample"))))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, "/api/canonical-metamodels/sample"))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(jsonPath("$.metamodel.id").value("sample"))
            .andExpect(jsonPath("$.validation.issues").isEmpty());
    }

    @Test
    void loadsMetaModelDirectlyAndReturnsItsEtag() throws Exception {
        String etag = createAndReadEtag("sample");

        mockMvc.perform(get("/api/canonical-metamodels/sample"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ETAG, etag))
            .andExpect(jsonPath("$.id").value("sample"))
            .andExpect(jsonPath("$.classes[0].name").value("Root"));
    }

    @Test
    void listsCanonicalSummariesInIdOrder() throws Exception {
        createAndReadEtag("zeta");
        createAndReadEtag("alpha");

        mockMvc.perform(get("/api/canonical-metamodels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("alpha"))
            .andExpect(jsonPath("$[1].id").value("zeta"))
            .andExpect(jsonPath("$[0].etag").isNotEmpty());
    }

    @Test
    void replacesWithIfMatchAndReturnsANewEtag() throws Exception {
        String firstEtag = createAndReadEtag("sample");
        MetaModel changed = CanonicalMetamodelTestFixtures.valid("sample", "Changed", java.util.Map.of());

        MvcResult result = mockMvc.perform(put("/api/canonical-metamodels/sample").header(HttpHeaders.IF_MATCH, firstEtag).contentType(MediaType.APPLICATION_JSON).content(json(changed)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metamodel.name").value("Changed"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(firstEtag);
    }

    @Test
    void rejectsReplacementWithoutIfMatch() throws Exception {
        createAndReadEtag("sample");

        mockMvc.perform(put("/api/canonical-metamodels/sample").contentType(MediaType.APPLICATION_JSON).content(json(CanonicalMetamodelTestFixtures.valid("sample"))))
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }

    @Test
    void rejectsStaleIfMatch() throws Exception {
        createAndReadEtag("sample");

        mockMvc.perform(put("/api/canonical-metamodels/sample").header(HttpHeaders.IF_MATCH, "\"stale\"").contentType(MediaType.APPLICATION_JSON).content(json(CanonicalMetamodelTestFixtures.valid("sample"))))
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }

    @Test
    void rejectsDuplicateCreationAsConflict() throws Exception {
        createAndReadEtag("sample");

        mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON).content(json(CanonicalMetamodelTestFixtures.valid("sample"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void rejectsInvalidMetamodelAsUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON).content(json(CanonicalMetamodelTestFixtures.invalid("invalid"))))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validatesTheStoredCanonicalMetamodel() throws Exception {
        createAndReadEtag("sample");

        mockMvc.perform(post("/api/canonical-metamodels/sample/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issues").isEmpty());
    }

    @Test
    void deletesWithTheCurrentEtag() throws Exception {
        String etag = createAndReadEtag("sample");

        mockMvc.perform(delete("/api/canonical-metamodels/sample").header(HttpHeaders.IF_MATCH, etag))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/canonical-metamodels/sample"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectsPathAndBodyIdMismatch() throws Exception {
        createAndReadEtag("sample");

        mockMvc.perform(put("/api/canonical-metamodels/sample").header(HttpHeaders.IF_MATCH, "*").contentType(MediaType.APPLICATION_JSON).content(json(CanonicalMetamodelTestFixtures.valid("other"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String createAndReadEtag(String id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/canonical-metamodels").contentType(MediaType.APPLICATION_JSON).content(json(CanonicalMetamodelTestFixtures.valid(id))))
            .andExpect(status().isCreated())
            .andReturn();
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private String json(MetaModel metamodel) throws Exception {
        return jsonMapper.writeValueAsString(metamodel);
    }
}

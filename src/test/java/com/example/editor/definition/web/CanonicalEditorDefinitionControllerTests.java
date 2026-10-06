package com.example.editor.definition.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionChecker;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.EditorDefinitionTestFixtures;
import com.example.editor.definition.persistence.JsonEditorDefinitionRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.web.ApiExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

class CanonicalEditorDefinitionControllerTests {
    @TempDir Path temporary;
    private MockMvc mockMvc;
    private JsonMapper mapper;
    private EditorDefinitionModel editor;

    @BeforeEach void setUp() {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        mapper = JsonMapper.builder().findAndAddModules().build();
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(EditorDefinitionTestFixtures.metamodel());
        EditorDefinitionProperties properties = new EditorDefinitionProperties();
        properties.setDirectory(temporary.resolve("editors"));
        EditorDefinitionApplicationService service = new EditorDefinitionApplicationService(
                new JsonEditorDefinitionRepository(properties, mapper), metamodelService,
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper), new EditorDefinitionChecker());
        mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalEditorDefinitionController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        editor = EditorDefinitionTestFixtures.editor();
    }

    @Test void createsListsLoadsChecksReplacesAndDeletesAnEditor() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels/people/editors")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(editor)))
                .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.editorDefinition.id").value("people-editor")).andReturn();
        String etag = created.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/canonical-metamodels/people/editors"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("people-editor"));
        mockMvc.perform(get("/api/canonical-metamodels/people/editors/people-editor"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(jsonPath("$.representations[0].id").value("representation:main"));
        mockMvc.perform(post("/api/canonical-metamodels/people/editors/people-editor/check"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnostics").isEmpty());

        EditorDefinitionModel changed = new EditorDefinitionModel("1.0", editor.id(), editor.metamodelId(),
                "Changed editor", editor.representations(), editor.palette(), editor.forms(), editor.capabilityManifest(), editor.annotations());
        MvcResult replaced = mockMvc.perform(put("/api/canonical-metamodels/people/editors/people-editor")
                .header(HttpHeaders.IF_MATCH, etag).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(changed)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.editorDefinition.name").value("Changed editor")).andReturn();
        String replacedEtag = replaced.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(delete("/api/canonical-metamodels/people/editors/people-editor")
                .header(HttpHeaders.IF_MATCH, replacedEtag)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/canonical-metamodels/people/editors/people-editor")).andExpect(status().isNotFound());
    }

    @Test void storesSeveralEditorsForOneMetamodel() throws Exception {
        mockMvc.perform(post("/api/canonical-metamodels/people/editors")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(editor)))
                .andExpect(status().isCreated());
        EditorDefinitionModel review = new EditorDefinitionModel("1.0", "people-review-editor", "people", "Review",
                editor.representations(), editor.palette(), editor.forms(), editor.capabilityManifest(), editor.annotations());
        mockMvc.perform(post("/api/canonical-metamodels/people/editors")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(review)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/canonical-metamodels/people/editors"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}

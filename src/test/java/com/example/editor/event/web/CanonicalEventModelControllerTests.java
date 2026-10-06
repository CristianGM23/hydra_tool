package com.example.editor.event.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.EventBinding;
import com.example.editor.event.EventBindingErrorPolicy;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventFieldDefinition;
import com.example.editor.event.EventFieldType;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.EventPayloadMapping;
import com.example.editor.event.EventPersistencePolicy;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.event.EventTypeDefinition;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.web.ApiExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

class CanonicalEventModelControllerTests {
    @TempDir Path temporary;
    private MockMvc mockMvc;
    private JsonMapper mapper;
    private EventModelRequest request;

    @BeforeEach void setUp() {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        mapper = JsonMapper.builder().findAndAddModules().build();
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(CanonicalMetamodelTestFixtures.valid("people"));
        EventModelProperties properties = new EventModelProperties();
        properties.setDirectory(temporary.resolve("events"));
        EventModelApplicationService service = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(properties, mapper),
                new JsonEventBindingModelRepository(properties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, new EventModelChecker());
        mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalEventModelController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        EventFieldDefinition field = new EventFieldDefinition("field:ruleId", "ruleId", EventFieldType.ELEMENT_ID,
                true, false, "", "", "");
        EventTypeDefinition type = new EventTypeDefinition("course.people.problem-resolved", "ProblemResolved",
                "course.people", "", "VALIDATION", "1.0", List.of(field), Set.of(),
                Set.of(EventSubjectKind.MODEL_ELEMENT), EventPersistencePolicy.GAMIFICATION, false, false, true, Map.of());
        EventDefinitionModel definitions = new EventDefinitionModel("1.0", "people-events", "people",
                "course.people", List.of(type), Map.of());
        EventBinding binding = new EventBinding("binding:problem-resolved", "Problem resolved",
                "core.validation.violation-resolved", type.id(), "",
                List.of(new EventPayloadMapping(field.id(), "payload.ruleId", "", null)), true,
                EventBindingErrorPolicy.REPORT, Map.of());
        EventBindingModel bindings = new EventBindingModel("1.0", "people-event-bindings", "people",
                definitions.id(), "", List.of(binding), Map.of());
        request = new EventModelRequest(definitions, bindings);
    }

    @Test void createsLoadsChecksReplacesAndDeletesBothDocuments() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels/people/events")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Event-Definitions-ETag"))
                .andExpect(header().exists("X-Event-Bindings-ETag"))
                .andExpect(jsonPath("$.definitions.id").value("people-events"))
                .andReturn();
        String definitionEtag = created.getResponse().getHeader("X-Event-Definitions-ETag");
        String bindingEtag = created.getResponse().getHeader("X-Event-Bindings-ETag");

        mockMvc.perform(get("/api/canonical-metamodels/people/events"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bindings.id").value("people-event-bindings"));
        mockMvc.perform(post("/api/canonical-metamodels/people/events/check"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnostics").isEmpty());
        MvcResult replaced = mockMvc.perform(put("/api/canonical-metamodels/people/events")
                .header("If-Match-Definitions", definitionEtag).header("If-Match-Bindings", bindingEtag)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        String replacedDefinitionEtag = replaced.getResponse().getHeader("X-Event-Definitions-ETag");
        String replacedBindingEtag = replaced.getResponse().getHeader("X-Event-Bindings-ETag");
        mockMvc.perform(delete("/api/canonical-metamodels/people/events")
                .header("If-Match-Definitions", replacedDefinitionEtag).header("If-Match-Bindings", replacedBindingEtag))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/canonical-metamodels/people/events")).andExpect(status().isNotFound());
    }
}

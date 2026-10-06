package com.example.editor.validation.action.web;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionModelChecker;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.ActionSelector;
import com.example.editor.validation.action.ActionSelectorKind;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.ActionValueSource;
import com.example.editor.validation.action.SetAttributeOperation;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.SelfExpressionNode;
import com.example.editor.semantic.expression.UnaryExpressionNode;
import com.example.editor.semantic.expression.UnaryExpressionOperator;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CanonicalActionControllerTests {
    @TempDir Path temporary;
    private MockMvc mockMvc;
    private JsonMapper mapper;
    private ActionModel model;

    @BeforeEach void setUp() {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        mapper = JsonMapper.builder().findAndAddModules().build();
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(CanonicalMetamodelTestFixtures.valid("sample"));
        ExpressionDependencyAnalyzer analyzer = new ExpressionDependencyAnalyzer();
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(analyzer);
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionDefinition precondition = new ExpressionDefinition("expression:self-exists", "Self exists", "", "", null, new UnaryExpressionNode("node:root", UnaryExpressionOperator.IS_NOT_NULL, new SelfExpressionNode("node:self")), Map.of());
        expressionRepository.create(new ExpressionModel("1.0", "sample", "sample", List.of(precondition), Map.of()));
        ActionModelApplicationService service = new ActionModelApplicationService(new JsonActionModelRepository(storage, new ActionModelProperties(), mapper), expressionRepository, metamodelService, new ActionModelChecker(typeChecker));
        mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalActionController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        ActionDefinition action = new ActionDefinition("action:set", "Set", "", true, List.of(), precondition.id(), List.of(new SetAttributeOperation("operation:set", new ActionSelector(ActionSelectorKind.SELF, "", ""), "attribute:name", ActionValueSource.literal("value"))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        model = new ActionModel("1.0", "sample-actions", "sample", "sample", List.of(action), Map.of());
    }

    @Test void createsLoadsChecksAndDeletes() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels/sample/actions").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(model)))
                .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.ETAG)).andExpect(jsonPath("$.actionModel.id").value("sample-actions")).andReturn();
        String etag = created.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/api/canonical-metamodels/sample/actions")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("sample-actions"));
        mockMvc.perform(post("/api/canonical-metamodels/sample/actions/check")).andExpect(status().isOk()).andExpect(jsonPath("$.actionModelId").value("sample-actions"));
        mockMvc.perform(delete("/api/canonical-metamodels/sample/actions").header(HttpHeaders.IF_MATCH, etag)).andExpect(status().isNoContent());
    }
}

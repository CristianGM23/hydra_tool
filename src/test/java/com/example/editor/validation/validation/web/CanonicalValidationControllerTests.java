package com.example.editor.validation.validation.web;

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
import com.example.editor.validation.action.ActionModelProperties;
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
import com.example.editor.validation.validation.AuthoritativeRepresentation;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationContext;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelChecker;
import com.example.editor.validation.validation.ValidationModelProperties;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleSet;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CanonicalValidationControllerTests {
    @TempDir Path temporary;
    private MockMvc mockMvc;
    private JsonMapper mapper;
    private ValidationModel model;

    @BeforeEach void setUp() {
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        mapper = JsonMapper.builder().findAndAddModules().build();
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(CanonicalMetamodelTestFixtures.valid("sample"));
        ExpressionDependencyAnalyzer analyzer = new ExpressionDependencyAnalyzer();
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(analyzer);
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionDefinition condition = new ExpressionDefinition("expression:self-exists", "Self exists", "", "", null, new UnaryExpressionNode("node:root", UnaryExpressionOperator.IS_NOT_NULL, new SelfExpressionNode("node:self")), Map.of());
        expressionRepository.create(new ExpressionModel("1.0", "sample", "sample", List.of(condition), Map.of()));
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ValidationModelApplicationService service = new ValidationModelApplicationService(new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper), expressionRepository, actionRepository, metamodelService, new ValidationModelChecker(typeChecker));
        mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalValidationController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        ValidationRule rule = new ValidationRule("rule:self-exists", "Self exists", "SELF_EXISTS", "", true, new ValidationContext(ValidationContextKind.MODEL, "", ""), ValidationSeverity.ERROR, condition.id(), "Self must exist", List.of(ValidationTrigger.ON_SAVE), List.of(), List.of(), ValidationExecutionMode.INCREMENTAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        model = new ValidationModel("1.0", "sample-validations", "sample", "sample", "", List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }

    @Test void createsLoadsChecksAndDeletes() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels/sample/validations").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(model)))
                .andExpect(status().isCreated()).andExpect(header().exists(HttpHeaders.ETAG)).andExpect(jsonPath("$.validationModel.id").value("sample-validations")).andReturn();
        String etag = created.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/api/canonical-metamodels/sample/validations")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("sample-validations"));
        mockMvc.perform(post("/api/canonical-metamodels/sample/validations/check")).andExpect(status().isOk()).andExpect(jsonPath("$.validationModelId").value("sample-validations"));
        mockMvc.perform(delete("/api/canonical-metamodels/sample/validations").header(HttpHeaders.IF_MATCH, etag)).andExpect(status().isNoContent());
    }
}

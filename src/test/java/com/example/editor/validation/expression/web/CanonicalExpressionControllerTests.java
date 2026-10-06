package com.example.editor.validation.expression.web;

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
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.semantic.expression.SelfExpressionNode;
import com.example.editor.semantic.expression.UnaryExpressionNode;
import com.example.editor.semantic.expression.UnaryExpressionOperator;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
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

/** Comprueba la API HTTP del ExpressionModel canónico. */
class CanonicalExpressionControllerTests {

    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;
    private JsonMapper mapper;
    private ExpressionModel model;

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        mapper = JsonMapper.builder().findAndAddModules().build();
        JsonCanonicalMetamodelRepository metamodelRepository = new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(metamodelRepository, new NeutralMetamodelValidator());
        metamodelService.create(CanonicalMetamodelTestFixtures.valid("sample"));

        ExpressionDependencyAnalyzer dependencyAnalyzer = new ExpressionDependencyAnalyzer();
        ExpressionTypeChecker checker = new ExpressionTypeChecker(dependencyAnalyzer);
        ExpressionEvaluator evaluator = new ExpressionEvaluator(checker);
        JsonExpressionModelRepository repository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionModelApplicationService service = new ExpressionModelApplicationService(repository, metamodelService, checker, evaluator);
        mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalExpressionController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        model = validModel();
    }

    @Test
    void createsLoadsChecksAndDeletesAnExpressionModel() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/canonical-metamodels/sample/expressions").contentType(MediaType.APPLICATION_JSON).content(json(model)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.expressionModel.id").value("sample"))
                .andExpect(jsonPath("$.checkReport.diagnostics").isEmpty())
                .andReturn();
        String etag = created.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();

        mockMvc.perform(get("/api/canonical-metamodels/sample/expressions"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(jsonPath("$.expressions[0].id").value("expression:self-exists"));

        mockMvc.perform(post("/api/canonical-metamodels/sample/expressions/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics").isEmpty())
                .andExpect(jsonPath("$.expressions[0].inferredType.kind").value("BOOLEAN"));

        mockMvc.perform(delete("/api/canonical-metamodels/sample/expressions").header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/canonical-metamodels/sample/expressions"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateCreation() throws Exception {
        create();
        mockMvc.perform(post("/api/canonical-metamodels/sample/expressions").contentType(MediaType.APPLICATION_JSON).content(json(model)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void requiresCurrentEtagForReplacement() throws Exception {
        create();
        mockMvc.perform(put("/api/canonical-metamodels/sample/expressions").contentType(MediaType.APPLICATION_JSON).content(json(model)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }

    @Test
    void rejectsStaleEtag() throws Exception {
        create();
        mockMvc.perform(put("/api/canonical-metamodels/sample/expressions").header(HttpHeaders.IF_MATCH, "\"stale\"").contentType(MediaType.APPLICATION_JSON).content(json(model)))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void listsSummaries() throws Exception {
        create();
        mockMvc.perform(get("/api/canonical-metamodels/expressions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sample"))
                .andExpect(jsonPath("$[0].expressionCount").value(1));
    }

    @Test
    void rejectsPathAndDocumentMismatch() throws Exception {
        ExpressionModel mismatched = new ExpressionModel("1.0", "other", "other", model.expressions(), Map.of());
        mockMvc.perform(post("/api/canonical-metamodels/sample/expressions").contentType(MediaType.APPLICATION_JSON).content(json(mismatched)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void storesTypeInvalidDraftAndReturnsDiagnostics() throws Exception {
        ExpressionDefinition invalid = new ExpressionDefinition("expression:invalid", "Invalid", "", "", ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:root", BinaryExpressionOperator.GREATER_THAN,
                        new LiteralExpressionNode("node:text", ExpressionValueType.scalar(ExpressionValueKind.STRING), "text"),
                        new LiteralExpressionNode("node:number", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 1)), Map.of());
        ExpressionModel invalidModel = new ExpressionModel("1.0", "sample", "sample", List.of(invalid), Map.of());
        mockMvc.perform(post("/api/canonical-metamodels/sample/expressions").contentType(MediaType.APPLICATION_JSON).content(json(invalidModel)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkReport.expressions[0].diagnostics[0].code").value("ORDERED_OPERANDS_REQUIRED"));
    }

    private String create() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/canonical-metamodels/sample/expressions").contentType(MediaType.APPLICATION_JSON).content(json(model)))
                .andExpect(status().isCreated()).andReturn();
        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    private ExpressionModel validModel() {
        ExpressionDefinition expression = new ExpressionDefinition("expression:self-exists", "Self exists", "", "",
                ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new UnaryExpressionNode("node:root", UnaryExpressionOperator.IS_NOT_NULL, new SelfExpressionNode("node:self")), Map.of());
        return new ExpressionModel("1.0", "sample", "sample", List.of(expression), Map.of());
    }

    private String json(ExpressionModel expressionModel) throws Exception {
        return mapper.writeValueAsString(expressionModel);
    }
}

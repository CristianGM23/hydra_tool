package com.example.editor.validation.runtime.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.validation.orchestration.OrchestratedValidationResult;
import com.example.editor.validation.orchestration.ValidationBlockDecision;
import com.example.editor.validation.orchestration.ValidationGate;
import com.example.editor.validation.orchestration.ValidationOrchestrator;
import com.example.editor.validation.orchestration.ValidationReportDelta;
import com.example.editor.validation.runtime.ExecutableValidationPlan;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationCompilationResult;
import com.example.editor.validation.runtime.ValidationDependencyIndex;
import com.example.editor.validation.runtime.ValidationExecutionMetrics;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationPlanSourceVersions;
import com.example.editor.validation.runtime.ValidationReportScope;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationService;
import com.example.editor.web.ApiExceptionHandler;

import tools.jackson.databind.json.JsonMapper;

class ValidationRuntimeControllerTests {
    private MockMvc mockMvc;
    private ValidationRuntimeApplicationService service;
    private ValidationOrchestrator orchestrator;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        service = mock(ValidationRuntimeApplicationService.class);
        orchestrator = mock(ValidationOrchestrator.class);
        mapper = JsonMapper.builder().findAndAddModules().build();
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationRuntimeController(service, orchestrator))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void compilesAndExecutesValidationRuntime() throws Exception {
        ExecutableValidationPlan plan = new ExecutableValidationPlan("1.0", "people", "m1", "people-validations", "v1",
                List.of(), "people-plan", "people-expressions", "", "0".repeat(64),
                new ValidationPlanSourceVersions("m1", "e1", "", "v1", "o1"),
                new ValidationDependencyIndex(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of()),
                List.of(), Map.of());
        ValidationCompilationResult compilation = new ValidationCompilationResult(plan, List.of(), false);
        ValidationExecutionRequest request = ValidationRuntimeTestRequestFactory.request();
        ModelValidationReport report = new ModelValidationReport("1.0", "report:people", "people-model", "people", "people-validations",
                Instant.EPOCH, ValidationReportScope.model(), List.of(), new ValidationExecutionMetrics(1, 0, 0),
                "people-plan", ValidationRunMode.FULL, Instant.EPOCH, Instant.EPOCH, List.of("rule:one"), List.of());
        when(service.compile("people")).thenReturn(compilation);
        when(service.validate("people", request)).thenReturn(report);

        mockMvc.perform(post("/api/canonical-metamodels/people/validations/runtime/compile"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.id").value("people-plan"));
        mockMvc.perform(post("/api/canonical-metamodels/people/validations/runtime/validate")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelId").value("people-model"));
    }

    @Test
    void orchestratesValidationWithRequestedGate() throws Exception {
        ValidationExecutionRequest request = ValidationRuntimeTestRequestFactory.request();
        ModelValidationReport report = new ModelValidationReport("1.0", "report:people", "people-model", "people", "people-validations",
                Instant.EPOCH, ValidationReportScope.model(), List.of(), new ValidationExecutionMetrics(1, 0, 0),
                "people-plan", ValidationRunMode.FULL, Instant.EPOCH, Instant.EPOCH, List.of("rule:one"), List.of());
        OrchestratedValidationResult result = new OrchestratedValidationResult(report, ValidationReportDelta.empty(), ValidationBlockDecision.allowed(ValidationGate.SAVE));
        when(orchestrator.validate(eq("people"), any(), eq(ValidationGate.SAVE))).thenReturn(result);

        mockMvc.perform(post("/api/canonical-metamodels/people/validations/runtime/orchestrate?gate=SAVE")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocking.gate").value("SAVE"));
        verify(orchestrator).validate("people", request, ValidationGate.SAVE);
    }

    @Test
    void downloadsEveryValidationDrawioProjection() throws Exception {
        when(service.exportDefinitionDrawio("people")).thenReturn("<mxfile id=\"definition\"/>");
        when(service.exportPlanDrawio("people")).thenReturn("<mxfile id=\"plan\"/>");
        when(service.exportReportDrawio(eq("people"), any())).thenReturn("<mxfile id=\"report\"/>");
        ValidationExecutionRequest request = ValidationRuntimeTestRequestFactory.request();

        mockMvc.perform(get("/api/canonical-metamodels/people/validations/runtime/drawio/definition"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("people-validations.drawio")));
        mockMvc.perform(get("/api/canonical-metamodels/people/validations/runtime/drawio/plan"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("plan")));
        mockMvc.perform(post("/api/canonical-metamodels/people/validations/runtime/drawio/report")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("report")));
        verify(service).exportReportDrawio("people", request);
    }
}

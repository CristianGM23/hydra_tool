package com.example.editor.integration.web;

import static org.mockito.Mockito.when;
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
import com.example.editor.integration.InjectionManifest;
import com.example.editor.integration.InjectionPlan;
import com.example.editor.integration.InjectionPlanStatus;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyIntegrationApplicationService;
import com.example.editor.integration.injection.TechnologyInjectionExecutionResult;
import com.example.editor.integration.injection.TechnologyInjectionPreview;
import com.example.editor.web.ApiExceptionHandler;

class TechnologyIntegrationControllerTests {
    private MockMvc mockMvc;
    private TechnologyIntegrationApplicationService service;
    @BeforeEach void setUp(){service=org.mockito.Mockito.mock(TechnologyIntegrationApplicationService.class);mockMvc=MockMvcBuilders.standaloneSetup(new TechnologyIntegrationController(service)).setControllerAdvice(new ApiExceptionHandler()).build();}
    @Test void downloadsCompiledTechnologyArchive() throws Exception {when(service.compileArchive("people","profile")).thenReturn(new byte[]{1,2,3});mockMvc.perform(post("/api/canonical-metamodels/people/integrations/profile/technology/compile")).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,org.hamcrest.Matchers.containsString("profile-technology.zip"))).andExpect(content().bytes(new byte[]{1,2,3}));}
    @Test void previewsInjectionWithoutWriting() throws Exception {when(service.preview("people","profile","host")).thenReturn(preview());mockMvc.perform(post("/api/canonical-metamodels/people/integrations/profile/technology/preview").contentType(MediaType.APPLICATION_JSON).content("{\"targetDirectory\":\"host\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.plan.status").value("READY"));}
    @Test void appliesInjectionAndReturnsManifest() throws Exception {when(service.apply("people","profile","host")).thenReturn(execution());mockMvc.perform(post("/api/canonical-metamodels/people/integrations/profile/technology/apply").contentType(MediaType.APPLICATION_JSON).content("{\"targetDirectory\":\"host\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.manifest.id").value("manifest-1"));}
    @Test void rollsBackAnAppliedManifest() throws Exception {when(service.rollback("host","manifest-1")).thenReturn(execution());mockMvc.perform(post("/api/canonical-metamodels/people/integrations/profile/technology/rollback/manifest-1").contentType(MediaType.APPLICATION_JSON).content("{\"targetDirectory\":\"host\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.manifest.id").value("manifest-1"));}
    private TechnologyInjectionPreview preview(){var plan=new InjectionPlan("1.0","plan-1","profile",IntegrationTechnology.EMF,InjectionPlanStatus.READY,Instant.EPOCH,List.of(),List.of(),List.of(),List.of("rollback"),List.of(),List.of(),Map.of());return new TechnologyInjectionPreview(plan,List.of(),List.of(),0);}
    private TechnologyInjectionExecutionResult execution(){var manifest=new InjectionManifest("1.0","manifest-1","profile",IntegrationTechnology.EMF,"","HYDRA",Instant.EPOCH,List.of(),List.of(),List.of(),List.of("rollback"),Map.of());return new TechnologyInjectionExecutionResult(manifest,List.of(),List.of(),List.of());}
}

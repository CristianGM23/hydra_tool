package com.example.editor.integration.web;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.integration.*;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;
class CanonicalIntegrationProfileControllerTests {
    private MockMvc mockMvc; private IntegrationProfileApplicationService service; private JsonMapper mapper;
    @BeforeEach void setUp(){service=mock(IntegrationProfileApplicationService.class);mapper=JsonMapper.builder().findAndAddModules().build();mockMvc=MockMvcBuilders.standaloneSetup(new CanonicalIntegrationProfileController(service)).setControllerAdvice(new ApiExceptionHandler()).build();}
    @Test void createsLoadsChecksAndDeletesProfiles() throws Exception {
        var profile=IntegrationProfileTestFixtures.plantUml(); var report=new IntegrationCheckReport(profile.id(),List.of(),List.of());
        when(service.create(eq("people"),any())).thenReturn(new IntegrationProfileSaveResponse(profile,"etag-1",report));
        when(service.find("people",profile.id())).thenReturn(new IntegrationProfileLoadResult(profile,"etag-1",report));
        when(service.check("people",profile.id())).thenReturn(report);
        mockMvc.perform(post("/api/canonical-metamodels/people/integrations").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(profile)))
                .andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.ETAG,"etag-1")).andExpect(jsonPath("$.profile.target.technology").value("PLANTUML"));
        mockMvc.perform(get("/api/canonical-metamodels/people/integrations/"+profile.id())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(profile.id()));
        mockMvc.perform(post("/api/canonical-metamodels/people/integrations/"+profile.id()+"/check")).andExpect(status().isOk()).andExpect(jsonPath("$.diagnostics").isEmpty());
        mockMvc.perform(delete("/api/canonical-metamodels/people/integrations/"+profile.id()).header(HttpHeaders.IF_MATCH,"etag-1")).andExpect(status().isNoContent());
        verify(service).delete("people",profile.id(),"etag-1");
    }
    @Test void exposesSemanticContextAliasForGrammarFirstRoots() throws Exception {
        when(service.technologies()).thenReturn(IntegrationTechnologyCatalog.descriptors());
        when(service.list("grammar.main-language")).thenReturn(List.of());
        mockMvc.perform(get("/api/semantic-contexts/grammar.main-language/integrations/technologies")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.technology == 'PLANTUML')]").exists());
        mockMvc.perform(get("/api/semantic-contexts/grammar.main-language/integrations")).andExpect(status().isOk());
        verify(service).list("grammar.main-language");
    }
    @Test void listsTechnologyDescriptors() throws Exception {
        when(service.technologies()).thenReturn(IntegrationTechnologyCatalog.descriptors());
        mockMvc.perform(get("/api/canonical-metamodels/people/integrations/technologies")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.technology == 'BESSER_SDK')]").exists()).andExpect(jsonPath("$[?(@.technology == 'PLANTUML')]").exists());
    }
}

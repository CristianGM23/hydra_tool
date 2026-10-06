package com.example.editor.integration.workflow;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;

class TechnologyWorkflowControllerTests {
    private TechnologyWorkflowService service;
    private MockMvc mockMvc;

    @BeforeEach void setUp(){
        service=org.mockito.Mockito.mock(TechnologyWorkflowService.class);
        mockMvc=MockMvcBuilders.standaloneSetup(new TechnologyWorkflowController(service)).build();
    }

    @Test void exposesWorkflowParityThroughCanonicalAndSemanticContextRoutes() throws Exception {
        var value = new TechnologyWorkflowDescriptor(IntegrationTechnology.XTEXT,"Eclipse Xtext",
                IntegrationTargetKind.TEXTUAL_LANGUAGE_TOOLING,TechnologyWorkflowMaturity.OPERATIONAL,78,true,true,
                List.of(),List.of(),List.of());
        when(service.descriptors()).thenReturn(List.of(value));
        when(service.descriptor(IntegrationTechnology.XTEXT)).thenReturn(value);

        mockMvc.perform(get("/api/semantic-contexts/grammar.main/integrations/technologies/workflows"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].technology").value("XTEXT"))
                .andExpect(jsonPath("$[0].parityScore").value(78));
        mockMvc.perform(get("/api/canonical-metamodels/people/integrations/technologies/workflows/XTEXT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maturity").value("OPERATIONAL"));
    }
}

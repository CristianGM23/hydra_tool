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
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.IntegrationTechnology;

class TechnologyDeliveryReadinessControllerTests {
    private TechnologyDeliveryReadinessService service;
    private MockMvc mockMvc;

    @BeforeEach void setUp(){
        service=org.mockito.Mockito.mock(TechnologyDeliveryReadinessService.class);
        mockMvc=MockMvcBuilders.standaloneSetup(new TechnologyDeliveryReadinessController(service)).build();
    }

    @Test void exposesProfileSpecificReadinessThroughBothSemanticRoutes() throws Exception {
        var action=new TechnologyDeliveryActionDescriptor(TechnologyWorkflowStage.GENERATE,TechnologyWorkflowAction.COMPILE_ARCHIVE,
                TechnologyDeliveryActionState.READY,IntegrationSupportLevel.FULL,true,false,false,false,"/compile","POST","Ready",List.of(),List.of("Generated files: 12"));
        var value=new TechnologyDeliveryReadiness("people-xtext",IntegrationTechnology.XTEXT,true,true,12,1,true,List.of(action),List.of(),List.of());
        when(service.readiness("people","people-xtext")).thenReturn(value);

        mockMvc.perform(get("/api/semantic-contexts/people/integrations/people-xtext/technology/readiness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.technology").value("XTEXT"))
                .andExpect(jsonPath("$.actions[0].state").value("READY"))
                .andExpect(jsonPath("$.nativeVerificationAvailable").value(true));
        mockMvc.perform(get("/api/canonical-metamodels/people/integrations/people-xtext/technology/readiness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generatedFileCount").value(12));
    }
}

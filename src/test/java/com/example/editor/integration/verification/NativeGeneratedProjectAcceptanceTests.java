package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.adapter.besser.BesserSdkTechnologyAdapter;
import com.example.editor.integration.adapter.besser.BesserWebModelingEditorTechnologyAdapter;
import com.example.editor.integration.adapter.glsp.EclipseGlspTechnologyAdapter;
import com.example.editor.integration.adapter.langium.LangiumTechnologyAdapter;
import com.example.editor.integration.adapter.xtext.XtextTechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationEnrichmentService;
import com.example.editor.integration.roundtrip.RoundTripManifestService;

/** Opt-in tests that install/build generated exports with their actual native toolchains. */
@EnabledIfSystemProperty(named="hydra.native.validation", matches="true")
class NativeGeneratedProjectAcceptanceTests {
    @TempDir Path temporary;
    @Test void xtextBuildsWithMaven(){validate(IntegrationTechnology.XTEXT);}
    @Test void langiumInstallsGeneratesAstAndBuilds(){validate(IntegrationTechnology.LANGIUM);}
    @Test void glspClientAndServerBuild(){validate(IntegrationTechnology.ECLIPSE_GLSP);}
    @Test void besserSdkInstallsAndRunsPytest(){validate(IntegrationTechnology.BESSER_SDK);}
    @Test void besserWebExtensionInstallsAndBuilds(){validate(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR);}

    private void validate(IntegrationTechnology technology){
        var adapter=switch(technology){case XTEXT->new XtextTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case LANGIUM->new LangiumTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case ECLIPSE_GLSP->new EclipseGlspTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case BESSER_SDK->new BesserSdkTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case BESSER_WEB_MODELING_EDITOR->new BesserWebModelingEditorTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);default->throw new IllegalArgumentException();};
        var request=switch(technology){case XTEXT->TechnologyAdapterTestFixtures.xtextRequest();case LANGIUM->TechnologyAdapterTestFixtures.langiumRequest();case ECLIPSE_GLSP->TechnologyAdapterTestFixtures.glspRequest();case BESSER_SDK->TechnologyAdapterTestFixtures.besserSdkRequest();case BESSER_WEB_MODELING_EDITOR->TechnologyAdapterTestFixtures.besserWebRequest();default->throw new IllegalArgumentException();};
        var compilation=new TechnologyCompilationEnrichmentService(TechnologyAdapterTestFixtures.MAPPER,new RoundTripManifestService()).enrich(request,adapter.compile(request));
        var validators=new TechnologyProjectStructureValidatorRegistry(List.of(new XtextProjectStructureValidator(),new LangiumProjectStructureValidator(),new EclipseGlspProjectStructureValidator(),new BesserSdkProjectStructureValidator(),new BesserWebProjectStructureValidator()));
        var properties=new TechnologyProjectValidationProperties();properties.setWorkspaceRoot(temporary);properties.setDefaultTimeoutSeconds(900);
        var service=new TechnologyProjectValidationService(validators,new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()),properties);
        var report=service.validate(compilation,new TechnologyProjectValidationRequest(TechnologyValidationMode.NATIVE_REQUIRED,null,900,true,Map.of()));
        assertEquals(TechnologyValidationStatus.PASSED,report.status(),report.steps().toString());
    }
}

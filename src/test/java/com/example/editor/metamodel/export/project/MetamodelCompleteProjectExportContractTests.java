package com.example.editor.metamodel.export.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.compiler.TechnologyCompilationArchiveService;
import com.example.editor.integration.compiler.TechnologyCompilationEnrichmentService;
import com.example.editor.integration.compiler.TechnologyCompilerService;
import com.example.editor.integration.verification.TechnologyProjectValidationService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.validation.MetamodelValidationApplicationService;
import tools.jackson.databind.json.JsonMapper;

class MetamodelCompleteProjectExportContractTests {
    @Test void exposesAllRequestedCompleteProjectTargets(){
        var canonical = mock(CanonicalMetamodelApplicationService.class);
        when(canonical.findById("people")).thenReturn(new MetamodelLoadResult(CanonicalMetamodelTestFixtures.valid("people"), "etag"));
        var service = service(canonical, mock(MetamodelValidationApplicationService.class), mock(MetamodelProjectArtifactAssembler.class), mock(TechnologyCompilerService.class));
        assertThat(service.listFormats("people")).extracting(MetamodelProjectExportDescriptor::id)
                .containsExactly("emf-eclipse", "eclipse-modeling", "eclipse-glsp", "vscode-modeling", "xtext", "langium", "besser-desktop", "besser-web");
    }

    @Test void currentMetamodelValidationIsCheckedBeforeAnyArtifactCompilation(){
        var canonical = mock(CanonicalMetamodelApplicationService.class);
        var validation = mock(MetamodelValidationApplicationService.class);
        var assembler = mock(MetamodelProjectArtifactAssembler.class);
        var compiler = mock(TechnologyCompilerService.class);
        org.mockito.Mockito.doThrow(new MetamodelDesignException(MetamodelDesignException.Reason.INVALID_REQUEST, "validation required"))
                .when(validation).requireGenerationReady("people");
        var service = service(canonical, validation, assembler, compiler);
        assertThatThrownBy(() -> service.export("people", "emf-eclipse", MetamodelProjectExportOptions.defaults()))
                .isInstanceOf(MetamodelDesignException.class).hasMessageContaining("validation required");
        verify(assembler, never()).assemble(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(compiler, never()).compile(org.mockito.ArgumentMatchers.any());
    }

    private MetamodelCompleteProjectExportService service(CanonicalMetamodelApplicationService canonical, MetamodelValidationApplicationService validation,
            MetamodelProjectArtifactAssembler assembler, TechnologyCompilerService compiler){
        MetamodelProjectExportProperties properties = new MetamodelProjectExportProperties();
        MetamodelProjectExportProviderConfiguration configuration = new MetamodelProjectExportProviderConfiguration();
        MetamodelProjectExportProviderRegistry providers = new MetamodelProjectExportProviderRegistry(java.util.List.of(
                configuration.emfProjectExportProvider(properties), configuration.eclipseModelingProjectExportProvider(properties),
                configuration.glspProjectExportProvider(properties), configuration.vscodeModelingProjectExportProvider(properties),
                configuration.xtextProjectExportProvider(properties), configuration.langiumProjectExportProvider(properties),
                configuration.besserDesktopProjectExportProvider(properties), configuration.besserWebProjectExportProvider(properties)));
        return new MetamodelCompleteProjectExportService(canonical, validation, assembler, compiler, mock(TechnologyProjectValidationService.class),
                mock(TechnologyCompilationEnrichmentService.class), mock(TechnologyCompilationArchiveService.class),
                JsonMapper.builder().findAndAddModules().build(), properties, new MetamodelProjectGenerationSnapshotService(), providers);
    }
}

package com.example.editor.metamodel.export.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;

class MetamodelProjectExportProviderRegistryTests {
    @Test void registryAcceptsAdditionalProviderWithoutChangingTheExportService(){
        MetamodelProjectExportProvider custom = new StandardMetamodelProjectExportProvider("custom", "Custom", "Extension",
                IntegrationTechnology.PLANTUML, IntegrationTargetKind.DIAGRAM_PROJECTION, IntegrationBuildSystem.NONE, false, 500,
                metamodel -> Map.of("mode", "custom"));
        MetamodelProjectExportProviderRegistry registry = new MetamodelProjectExportProviderRegistry(List.of(custom));
        assertThat(registry.require("custom")).isSameAs(custom);
        assertThat(registry.all()).extracting(MetamodelProjectExportProvider::id).containsExactly("custom");
    }

    @Test void registryRejectsDuplicateProviderIds(){
        MetamodelProjectExportProvider first = provider("same", 1); MetamodelProjectExportProvider second = provider("same", 2);
        assertThatThrownBy(() -> new MetamodelProjectExportProviderRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
    }

    private MetamodelProjectExportProvider provider(String id, int order){
        return new StandardMetamodelProjectExportProvider(id, id, id, IntegrationTechnology.EMF,
                IntegrationTargetKind.MODELING_RUNTIME, IntegrationBuildSystem.MAVEN, false, order, metamodel -> Map.of());
    }
}

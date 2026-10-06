package com.example.editor.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.importing.MetamodelImportFormat;

class ModelCodeGenerationTargetTest {
    @Test
    void catalogUsesExactlyEveryMetamodelImportFormatId() {
        assertThat(ModelCodeGenerationTarget.catalog().stream().map(ModelCodeGenerationTarget::id).toList())
                .containsExactlyElementsOf(Arrays.stream(MetamodelImportFormat.values()).map(MetamodelImportFormat::id).toList());
    }

    @Test
    void catalogProvidesPortableOutputMetadataForEveryTarget() {
        assertThat(ModelCodeGenerationTarget.catalog()).allSatisfy(target -> {
            assertThat(target.extension()).startsWith(".");
            assertThat(target.mediaType()).contains("/");
            assertThat(target.outputKind()).isNotBlank();
            assertThat(target.description()).isNotBlank();
        });
    }
}

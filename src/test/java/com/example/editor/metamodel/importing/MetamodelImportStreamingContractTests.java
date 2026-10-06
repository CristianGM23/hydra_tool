package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guardarraíl para que el importador general no vuelva a materializar MultipartFile completo antes del límite. */
class MetamodelImportStreamingContractTests {
    @Test void controllerStagesMultipartUploadsInsteadOfCallingGetBytes() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/example/editor/metamodel/importing/MetamodelImportController.java"), StandardCharsets.UTF_8);
        String staging = Files.readString(Path.of("src/main/java/com/example/editor/metamodel/importing/MetamodelImportStagingService.java"), StandardCharsets.UTF_8);
        assertThat(controller).doesNotContain("file.getBytes()", ".getBytes()");
        assertThat(controller).contains("staging.stage(file)", "analysisJobService.start(staged");
        assertThat(staging).contains("file.getInputStream()", "total > maximum", "MessageDigest.getInstance(\"SHA-256\")");
    }
}

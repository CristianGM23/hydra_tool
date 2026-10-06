package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.example.editor.metamodel.design.MetamodelDesignException;

class MetamodelImportStagingServiceTests {
    @TempDir Path temporary;

    @Test void streamsUploadToDiskComputesHashAndDeletesItAfterUse() throws Exception {
        MetamodelImportProperties properties = properties(1024); var service = new MetamodelImportStagingService(properties);
        byte[] content = "metamodel".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var staged = service.stage(new MockMultipartFile("file", "sample.ecore", "application/xml", content));
        assertThat(staged.sizeBytes()).isEqualTo(content.length); assertThat(staged.sha256()).matches("[0-9a-f]{64}");
        assertThat(Files.exists(staged.path())).isTrue(); assertThat(service.read(staged)).containsExactly(content);
        service.delete(staged); assertThat(Files.exists(staged.path())).isFalse();
    }

    @Test void streamingLimitRejectsActualContentEvenWhenMultipartDeclaresZeroBytes() throws Exception {
        MetamodelImportProperties properties = properties(8); var service = new MetamodelImportStagingService(properties);
        byte[] content = new byte[32];
        MockMultipartFile deceptive = new MockMultipartFile("file", "large.ecore", "application/xml", content) {
            @Override public long getSize(){return 0;}
            @Override public java.io.InputStream getInputStream(){return new ByteArrayInputStream(content);}
        };
        assertThatThrownBy(() -> service.stage(deceptive)).isInstanceOf(MetamodelDesignException.class).hasMessageContaining("Maximum allowed is 8 bytes");
        try(var files = Files.list(temporary)){assertThat(files).isEmpty();}
    }

    private MetamodelImportProperties properties(long maximum){MetamodelImportProperties properties = new MetamodelImportProperties(); properties.setMaxFileSizeBytes(maximum); properties.setStagingRoot(temporary); return properties;}
}

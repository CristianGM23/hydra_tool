package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.example.editor.metamodel.design.MetamodelDesignException;

class MetamodelBrowserDirectoryArchiveServiceTests {

    @Test
    void createsDeterministicArchiveAndPreservesBrowserRelativePaths() throws Exception {
        var properties = properties();
        var service = new MetamodelBrowserDirectoryArchiveService(properties);
        var files = List.of(file("a.ecore", "A"), file("b.ecore", "B"));
        var paths = List.of("model/a.ecore", "model/sub/b.ecore");

        byte[] first = service.archive(files, paths);
        byte[] second = service.archive(files, paths);

        assertThat(first).isEqualTo(second);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(first))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("model/a.ecore");
            assertThat(zip.getNextEntry().getName()).isEqualTo("model/sub/b.ecore");
        }
    }

    @Test
    void rejectsTraversalDuplicateAndAbsolutePaths() {
        var service = new MetamodelBrowserDirectoryArchiveService(properties());
        var files = List.of(file("a.ecore", "A"), file("b.ecore", "B"));

        assertThatThrownBy(() -> service.archive(files, List.of("../a.ecore", "b.ecore")))
                .isInstanceOf(MetamodelDesignException.class);
        assertThatThrownBy(() -> service.archive(files, List.of("model/a.ecore", "model/a.ecore")))
                .isInstanceOf(MetamodelDesignException.class);
        assertThatThrownBy(() -> service.archive(files, List.of("C:/model/a.ecore", "b.ecore")))
                .isInstanceOf(MetamodelDesignException.class);
    }

    @Test
    void enforcesActualStreamSizeEvenWhenMultipartDeclaresAFalseSmallSize() {
        var properties = properties();
        properties.setMaxSourceFileSizeBytes(1024);
        var service = new MetamodelBrowserDirectoryArchiveService(properties);
        byte[] content = new byte[2048];
        var deceptive = new MockMultipartFile("files", "large.ecore", "application/xml", content) {
            @Override public long getSize() { return 16; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        };

        assertThatThrownBy(() -> service.archive(List.of(deceptive), List.of("model/large.ecore")))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("size limit");
    }

    private static MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("files", name, "application/xml", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static MetamodelImportProperties properties() {
        var properties = new MetamodelImportProperties();
        properties.setMaxSourceFiles(16);
        properties.setMaxArchiveEntries(16);
        properties.setMaxSourceFileSizeBytes(1024 * 1024);
        properties.setMaxArchiveUncompressedBytes(4 * 1024 * 1024);
        return properties;
    }
}

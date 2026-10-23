package com.example.editor.metamodel.importing.updatesite;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.metamodel.design.MetamodelDesignException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateSiteArchiveReaderTests {
    @TempDir Path temporary;
    private UpdateSiteImportProperties properties;
    private UpdateSiteArchiveReader reader;

    /** Las propiedades por defecto escriben en ./data/update-site-imports (la caché de artefactos): estas pruebas usan un directorio temporal. */
    @BeforeEach
    void isolatedStorage() {
        properties = new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.resolve("update-site").toString());
        reader = new UpdateSiteArchiveReader(properties);
    }

    @Test
    void expandsP2MetadataFeaturesAndPluginJarsWithVirtualPaths() {
        byte[] contentJar = UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries(
                "content.xml", UpdateSiteTestFixtures.contentXml()));
        byte[] modelJar = UpdateSiteTestFixtures.jar("com.example.library", "1.2.0", "org.eclipse.emf.ecore",
                UpdateSiteTestFixtures.entries("model/library.ecore", UpdateSiteTestFixtures.ecore(),
                        "plugin.xml", "<plugin><extension point=\"org.eclipse.emf.ecore.generated_package\"><package uri=\"https://example.com/library\" class=\"com.example.library.LibraryPackage\" genModel=\"model/library.genmodel\"/></extension></plugin>"));
        byte[] featureJar = UpdateSiteTestFixtures.jar("com.example.library.feature", "1.2.0", "",
                UpdateSiteTestFixtures.entries("feature.xml", "<feature id=\"com.example.library.feature\" version=\"1.2.0\"><plugin id=\"com.example.library\" version=\"1.2.0\"/></feature>"));
        byte[] site = UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries(
                "content.jar", contentJar,
                "artifacts.xml", UpdateSiteTestFixtures.artifactsXml(),
                "plugins/com.example.library_1.2.0.jar", modelJar,
                "features/com.example.library.feature_1.2.0.jar", featureJar));

        UpdateSiteRepositoryContent repository = reader.read(List.of(new UpdateSiteInputFile("library-update-site.zip", site)));

        assertThat(repository.files()).containsKeys(
                "content.jar!/content.xml",
                "plugins/com.example.library_1.2.0.jar!/META-INF/MANIFEST.MF",
                "plugins/com.example.library_1.2.0.jar!/model/library.ecore",
                "features/com.example.library.feature_1.2.0.jar!/feature.xml");
        assertThat(repository.fingerprint()).hasSize(64);
        assertThat(repository.totalBytes()).isPositive();
    }

    @Test
    void rejectsArchiveTraversalEntries() {
        byte[] site = UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries("../outside.ecore", UpdateSiteTestFixtures.ecore()));

        assertThatThrownBy(() -> reader.read(List.of(new UpdateSiteInputFile("unsafe.zip", site))))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("Unsafe update-site path");
    }

    @Test
    void enforcesEntryAndUncompressedBudgets() {
        properties.setMaxEntries(1);
        byte[] site = UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries("a.txt", "a", "b.txt", "b"));

        assertThatThrownBy(() -> reader.read(List.of(new UpdateSiteInputFile("too-many.zip", site))))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("entry limit");
    }
    @Test
    void rejectsHighlyCompressedArchiveBombs() {
        properties.setMaxCompressionRatio(2.0d);
        String repeated = "A".repeat(128 * 1024);
        byte[] site = UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries("payload.txt", repeated));

        assertThatThrownBy(() -> reader.read(List.of(new UpdateSiteInputFile("archive-bomb.zip", site))))
                .isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("compression-ratio limit");
    }

}

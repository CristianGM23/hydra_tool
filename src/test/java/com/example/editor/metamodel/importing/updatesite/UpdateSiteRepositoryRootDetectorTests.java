package com.example.editor.metamodel.importing.updatesite;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteRepositoryRootDetectorTests {
    @TempDir Path temp;

    @Test
    void ranksExplicitP2MetadataAboveLooseEclipseBuildEvidence() throws Exception {
        Path primary = temp.resolve("products/editor/repository");
        Files.createDirectories(primary);
        Files.writeString(primary.resolve("content.xml"), "<repository/>");
        Files.writeString(primary.resolve("artifacts.xml"), "<repository/>");
        Files.writeString(primary.resolve("category.xml"), "<site/>");
        Path secondary = temp.resolve("plugins/example");
        Files.createDirectories(secondary);
        Files.writeString(secondary.resolve("build.gradle"), "plugins { id('org.eclipse') }");

        var roots = new UpdateSiteRepositoryRootDetector().detect(temp);

        assertThat(roots).isNotEmpty();
        assertThat(roots.getFirst().path()).isEqualTo("products/editor/repository");
        assertThat(roots.getFirst().recommended()).isTrue();
        assertThat(roots.getFirst().markers()).contains("content.xml", "artifacts.xml", "category.xml");
    }
    @Test
    void stopsDiscoveryWhenRepositoryExceedsConfiguredFileBudget() throws Exception {
        UpdateSiteImportProperties properties=new UpdateSiteImportProperties(); properties.setMaxEntries(2);
        Files.writeString(temp.resolve("a.txt"),"a"); Files.writeString(temp.resolve("b.txt"),"b"); Files.writeString(temp.resolve("c.txt"),"c");
        var roots=new UpdateSiteRepositoryRootDetector(properties).detect(temp);
        assertThat(roots).singleElement().satisfies(root -> assertThat(root.markers()).singleElement().asString().contains("configured file limit"));
    }

}

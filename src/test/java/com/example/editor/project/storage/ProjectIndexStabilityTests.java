package com.example.editor.project.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.project.CreateLocalProjectRequest;
import com.example.editor.project.OpenLocalProjectRequest;
import com.example.editor.project.ProjectStorageTestFixture;

/**
 * OBS-DET-PROJECT-INDEX-REGENERATED: opening an unchanged project (here from a second storage instance, as another installation would) must not
 * rewrite .modeling/project-index.json, otherwise the same project exported from two installations differs in that entry. A real change still
 * updates the index.
 */
class ProjectIndexStabilityTests {
    @TempDir Path temporary;

    @Test void openingAnUnchangedProjectKeepsItsIndexByteForByte() throws Exception {
        LocalDirectoryProjectStorage first = ProjectStorageTestFixture.storage(temporary.resolve("installation-1"));
        first.create(new CreateLocalProjectRequest(temporary.toString(), "stable", "Stable", "", false));
        first.upload("assets", "a.txt", "uno".getBytes(StandardCharsets.UTF_8));
        Path index = temporary.resolve("stable/.modeling/project-index.json");
        byte[] before = Files.readAllBytes(index);
        FileTime modified = Files.getLastModifiedTime(index);
        Thread.sleep(1_100L);

        LocalDirectoryProjectStorage second = ProjectStorageTestFixture.storage(temporary.resolve("installation-2"));
        second.open(new OpenLocalProjectRequest(temporary.resolve("stable").toString(), false, null));

        assertThat(Files.readAllBytes(index)).isEqualTo(before);
        assertThat(Files.getLastModifiedTime(index)).isEqualTo(modified);
    }

    @Test void aRealChangeStillUpdatesTheIndex() throws Exception {
        LocalDirectoryProjectStorage storage = ProjectStorageTestFixture.storage(temporary.resolve("installation-1"));
        storage.create(new CreateLocalProjectRequest(temporary.toString(), "changing", "Changing", "", false));
        Path index = temporary.resolve("changing/.modeling/project-index.json");
        String before = Files.readString(index);

        storage.upload("assets", "nuevo.txt", "nuevo".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(index)).isNotEqualTo(before).contains("assets/nuevo.txt");
    }
}

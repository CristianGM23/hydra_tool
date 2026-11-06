package com.example.editor.project.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectStorageTestFixture;

/**
 * Importing an archive that is not a HYDRA project (a ZIP of a selection, an arbitrary folder) answered 404 NOT_FOUND "The directory does
 * not contain modeling-project.json", as if a resource were missing. The client sent an invalid archive: it is a 400 INVALID_REQUEST, and the
 * extracted directory is removed (found by the project ZIP system probe, jar 13).
 */
class ProjectArchiveWithoutDescriptorImportTests {
    @TempDir Path temporary;

    @Test void anArchiveWithoutAProjectDescriptorIsAnInvalidRequestAndLeavesNothingBehind() throws Exception {
        LocalDirectoryProjectStorage storage = ProjectStorageTestFixture.storage(temporary.resolve("installation"));
        byte[] selection = zip("behavior/actions/model.actions.json", "{}");

        assertThatThrownBy(() -> storage.importArchive(temporary.toString(), "not-a-project", selection))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
        assertThat(temporary.resolve("not-a-project")).doesNotExist();
    }

    private static byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try(ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}

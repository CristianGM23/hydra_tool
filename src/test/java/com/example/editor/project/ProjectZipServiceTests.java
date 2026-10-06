package com.example.editor.project;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.project.storage.ProjectFileOperations;
import com.example.editor.project.storage.ProjectZipService;

class ProjectZipServiceTests {
    @TempDir Path temporary;

    @Test void rejectsZipSlip() throws Exception {
        ProjectZipService service=service(new LocalProjectProperties());
        assertThatThrownBy(() -> service.importProject(zip(Map.of("../escape.txt","bad")),temporary.resolve("project")))
                .isInstanceOf(ProjectException.class);
        assertThat(temporary.resolve("escape.txt")).doesNotExist();
    }

    @Test void rejectsBackslashZipSlip() throws Exception {
        ProjectZipService service=service(new LocalProjectProperties());
        assertThatThrownBy(() -> service.importProject(zip(Map.of("..\\escape.txt","bad")),temporary.resolve("project")))
                .isInstanceOf(ProjectException.class);
    }

    @Test void rejectsEntriesThatBecomeDuplicatesAfterNormalization() throws Exception {
        Map<String,String> entries=new LinkedHashMap<>(); entries.put("./docs/a.md","one"); entries.put("docs/a.md","two");
        assertThatThrownBy(() -> service(new LocalProjectProperties()).importProject(zip(entries),temporary.resolve("project")))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
    }

    @Test void rejectsArchiveWithTooManyEntries() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties(); properties.setMaxArchiveEntries(1);
        assertThatThrownBy(() -> service(properties).importProject(zip(Map.of("a.txt","a","b.txt","b")),temporary.resolve("project")))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.FILE_TOO_LARGE);
    }

    @Test void rejectsExpandedArchiveLargerThanConfiguredLimit() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties(); properties.setMaxArchiveSizeBytes(1000);
        String large="x".repeat(5000);
        assertThat(zip(Map.of("large.txt",large)).length).isLessThan(1000);
        assertThatThrownBy(() -> service(properties).importProject(zip(Map.of("large.txt",large)),temporary.resolve("project")))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.FILE_TOO_LARGE);
    }

    @Test void exportsAndImportsPortableProjectButSkipsCache() throws Exception {
        Path source=temporary.resolve("source"); Files.createDirectories(source.resolve(".modeling/cache")); Files.createDirectories(source.resolve("docs"));
        Files.writeString(source.resolve("modeling-project.json"),"{}"); Files.writeString(source.resolve("docs/readme.md"),"portable"); Files.writeString(source.resolve(".modeling/cache/temp.bin"),"cache");
        ProjectZipService service=service(new LocalProjectProperties()); byte[] archive=service.exportProject(source); Path destination=temporary.resolve("destination");
        service.importProject(archive,destination);
        assertThat(destination.resolve("docs/readme.md")).hasContent("portable");
        assertThat(destination.resolve(".modeling/cache/temp.bin")).doesNotExist();
    }


    @Test void exportRejectsTooManyPortableEntriesBeforeBuildingArchive() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties(); properties.setMaxArchiveEntries(2);
        Path source=temporary.resolve("too-many"); Files.createDirectories(source); Files.writeString(source.resolve("a.txt"),"a"); Files.writeString(source.resolve("b.txt"),"b"); Files.writeString(source.resolve("c.txt"),"c");
        assertThatThrownBy(() -> service(properties).exportProject(source))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.FILE_TOO_LARGE);
    }

    @Test void exportRejectsTreeDeeperThanConfiguredLimit() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties(); properties.setMaxTreeDepth(2);
        Path source=temporary.resolve("deep"); Files.createDirectories(source.resolve("a/b/c")); Files.writeString(source.resolve("a/b/c/value.txt"),"value");
        assertThatThrownBy(() -> service(properties).exportProject(source))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.FILE_TOO_LARGE);
    }

    @Test void failedImportIsTransactionalAndKeepsExistingDestination() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties(); properties.setMaxArchiveEntries(1);
        Path destination=temporary.resolve("existing"); Files.createDirectories(destination); Files.writeString(destination.resolve("keep.txt"),"keep");
        assertThatThrownBy(() -> service(properties).importProject(zip(Map.of("a.txt","a","b.txt","b")),destination))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.CONFLICT);
        assertThat(destination.resolve("keep.txt")).hasContent("keep");
    }

    @Test void failedNewImportLeavesNeitherDestinationNorStagingDirectory() throws Exception {
        LocalProjectProperties properties=new LocalProjectProperties(); properties.setMaxArchiveEntries(1);
        Path destination=temporary.resolve("rejected");
        assertThatThrownBy(() -> service(properties).importProject(zip(Map.of("a.txt","a","b.txt","b")),destination))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.FILE_TOO_LARGE);
        assertThat(destination).doesNotExist();
        try(var stream=Files.list(temporary)){assertThat(stream.map(path->path.getFileName().toString()).filter(name->name.startsWith(".rejected-import-"))).isEmpty();}
    }

    @Test void exportRejectsSymbolicLinksWhenSupported() throws Exception {
        Path source=temporary.resolve("symlink-source"); Files.createDirectories(source); Path outside=temporary.resolve("outside.txt"); Files.writeString(outside,"outside"); Path link=source.resolve("link.txt");
        try { Files.createSymbolicLink(link,outside); }
        catch(UnsupportedOperationException|java.nio.file.FileSystemException exception) { return; }
        assertThatThrownBy(() -> service(new LocalProjectProperties()).exportProject(source))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
    }

    private ProjectZipService service(LocalProjectProperties properties) {
        return new ProjectZipService(properties,new ProjectFileOperations());
    }

    private byte[] zip(Map<String,String> entries) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            for(Map.Entry<String,String> entry:entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes()); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}

package com.example.editor.metamodel.importing.updatesite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateSiteStreamingStorageTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void archiveReaderSpillsEntriesToContentAddressedStorage() throws Exception {
        UpdateSiteImportProperties properties = properties();
        UpdateSiteArtifactBlobStore blobs = new UpdateSiteArtifactBlobStore(temporaryDirectory.resolve("objects"));
        UpdateSiteArchiveReader reader = new UpdateSiteArchiveReader(properties, blobs);
        byte[] site = UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries(
                "content.xml", UpdateSiteTestFixtures.contentXml(),
                "model/library.ecore", UpdateSiteTestFixtures.ecore()));

        UpdateSiteRepositoryContent repository = reader.read(java.util.List.of(new UpdateSiteInputFile("site.zip", site)));

        assertThat(repository.paths()).contains("content.xml", "model/library.ecore");
        assertThat(repository.fileCount()).isEqualTo(2);
        assertThat(repository.find("model/library.ecore")).hasValueSatisfying(content -> assertThat(content).isNotEmpty());
        try(var paths = Files.walk(blobs.root())) {
            assertThat(paths.filter(Files::isRegularFile).count()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void iteratingRepositoryKeysDoesNotMaterializeBlobValues() {
        CountingBlobStore blobs = new CountingBlobStore(temporaryDirectory.resolve("lazy"));
        byte[] content = "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        UpdateSiteStoredArtifact stored = blobs.store(content, 1024, "a.txt");
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(Map.of("a.txt", stored), blobs, "fingerprint", content.length);

        assertThat(repository.files().keySet()).containsExactly("a.txt");
        assertThat(repository.files().entrySet()).extracting(Map.Entry::getKey).containsExactly("a.txt");
        assertThat(blobs.reads).hasValue(0);
        assertThat(repository.files().get("a.txt")).isEqualTo(content);
        assertThat(blobs.reads).hasValue(1);
    }

    @Test
    void multipartUploadsAreStagedWithoutCallingGetBytes() {
        UpdateSiteImportProperties properties = properties();
        UpdateSiteUploadStagingService staging = new UpdateSiteUploadStagingService(properties);
        byte[] payload = new byte[128 * 1024];
        MockMultipartFile file = new MockMultipartFile("files", "site.zip", "application/zip", payload) {
            @Override
            public byte[] getBytes() {
                throw new AssertionError("streaming staging must not call MultipartFile.getBytes()");
            }
        };

        var inputs = staging.stage(java.util.List.of(file));

        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0).size()).isEqualTo(payload.length);
        assertThat(inputs.get(0).content()).hasSize(payload.length);
        inputs.get(0).cleanup();
    }

    @Test
    void stagedUploadCanBeReadMoreThanOnceUntilTheRequestOwnerCleansIt() throws Exception {
        UpdateSiteImportProperties properties = properties();
        UpdateSiteUploadStagingService staging = new UpdateSiteUploadStagingService(properties);
        UpdateSiteArtifactBlobStore blobs = new UpdateSiteArtifactBlobStore(temporaryDirectory.resolve("reusable-objects"));
        UpdateSiteArchiveReader reader = new UpdateSiteArchiveReader(properties, blobs);
        byte[] site = UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries(
                "content.xml", UpdateSiteTestFixtures.contentXml(),
                "model/library.ecore", UpdateSiteTestFixtures.ecore()));
        MockMultipartFile file = new MockMultipartFile("files", "site.zip", "application/zip", site);
        var inputs = staging.stage(java.util.List.of(file));

        UpdateSiteRepositoryContent first = reader.read(inputs);
        UpdateSiteRepositoryContent second = reader.read(inputs);

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(second.paths()).contains("content.xml", "model/library.ecore");
        staging.cleanup(inputs);
        assertThatThrownBy(() -> inputs.getFirst().openStream()).isInstanceOf(RuntimeException.class);
    }


    @Test
    void repositorySnapshotStoreHandlesThousandsOfFilesWithoutBulkTreeMaterialization() {
        UpdateSiteImportProperties properties = properties();
        properties.setMaxEntries(2500);
        properties.setMaxEntryBytes(4096);
        properties.setMaxUncompressedBytes(16L * 1024 * 1024);
        UpdateSiteRepositorySourceStore store = new UpdateSiteRepositorySourceStore(properties);
        java.util.List<UpdateSiteInputFile> inputs = new java.util.ArrayList<>();
        for(int index = 0; index < 2000; index++) {
            inputs.add(new UpdateSiteInputFile(String.format("features/f%04d/feature.xml", index),
                    ("<feature id='" + index + "'/>").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }

        String snapshot = store.save(inputs);
        var loaded = store.load(snapshot);

        assertThat(loaded).hasSize(2000);
        assertThat(loaded.getFirst().path()).isEqualTo("features/f0000/feature.xml");
        assertThat(loaded.getLast().path()).isEqualTo("features/f1999/feature.xml");
    }

    @Test
    void repositorySnapshotStoreRejectsCardinalityAndPerFileBudgetViolations() {
        UpdateSiteImportProperties properties = properties();
        properties.setMaxEntries(2);
        properties.setMaxEntryBytes(4);
        UpdateSiteRepositorySourceStore store = new UpdateSiteRepositorySourceStore(properties);

        assertThatThrownBy(() -> store.save(java.util.List.of(
                new UpdateSiteInputFile("a.txt", "1".getBytes()),
                new UpdateSiteInputFile("b.txt", "2".getBytes()),
                new UpdateSiteInputFile("c.txt", "3".getBytes()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file limit");
        assertThatThrownBy(() -> store.save(java.util.List.of(new UpdateSiteInputFile("large.txt", "12345".getBytes()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-file limit");
    }

    @Test
    void duplicatePathsStillConsumeTheGlobalArchiveBudget() {
        UpdateSiteImportProperties properties = properties();
        properties.setMaxEntries(1);
        UpdateSiteArtifactBlobStore blobs = new UpdateSiteArtifactBlobStore(temporaryDirectory.resolve("duplicate-budget"));
        UpdateSiteArchiveReader reader = new UpdateSiteArchiveReader(properties, blobs);
        byte[] payload = "same logical path".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(java.util.List.of(
                new UpdateSiteInputFile("duplicate.txt", payload),
                new UpdateSiteInputFile("duplicate.txt", payload))))
                .isInstanceOf(com.example.editor.metamodel.design.MetamodelDesignException.class)
                .hasMessageContaining("entry limit");
    }

    private UpdateSiteImportProperties properties() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setStorageRoot(temporaryDirectory.resolve("update-site").toString());
        properties.setMaxUploadBytes(4 * 1024 * 1024);
        properties.setMaxUncompressedBytes(8 * 1024 * 1024);
        properties.setMaxEntryBytes(2 * 1024 * 1024);
        return properties;
    }

    private static final class CountingBlobStore extends UpdateSiteArtifactBlobStore {
        private final AtomicInteger reads = new AtomicInteger();

        private CountingBlobStore(Path root) {
            super(root);
        }

        @Override
        byte[] read(UpdateSiteStoredArtifact artifact) {
            reads.incrementAndGet();
            return super.read(artifact);
        }
    }
}

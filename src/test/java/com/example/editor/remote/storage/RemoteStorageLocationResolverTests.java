package com.example.editor.remote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RemoteStorageLocationResolverTests {
    @Test
    void githubUrlDerivesRepositoryBranchAndFolderWithoutQueryNoise() {
        RemoteStorageLocationResolution resolved=RemoteStorageLocationResolver.resolve("https://github.com/openai/example/tree/release/docs?utm_source=test#readme");
        assertEquals(RemoteStorageProviderType.GITHUB,resolved.provider());
        assertEquals("openai",resolved.owner());
        assertEquals("example",resolved.repository());
        assertEquals("release",resolved.branch());
        assertEquals("docs",resolved.rootPath());
        assertEquals("https://github.com/openai/example/tree/release/docs",resolved.url());
    }

    @Test
    void gitlabUrlDerivesNestedProjectAndBranch() {
        RemoteStorageLocationResolution resolved=RemoteStorageLocationResolver.resolve("https://gitlab.com/group/subgroup/project/-/tree/main/model");
        assertEquals(RemoteStorageProviderType.GITLAB,resolved.provider());
        assertEquals("group/subgroup/project",resolved.projectId());
        assertEquals("main",resolved.branch());
        assertEquals("model",resolved.rootPath());
    }

    @Test
    void googleDocsAndDriveUrlsProduceSafeRootItemIds() {
        RemoteStorageLocationResolution document=RemoteStorageLocationResolver.resolve("https://docs.google.com/document/d/1AbC_def-123/edit?tab=t.0");
        RemoteStorageLocationResolution folder=RemoteStorageLocationResolver.resolve("https://drive.google.com/drive/folders/0AExampleFolder?usp=sharing");
        assertEquals(RemoteStorageProviderType.GOOGLE_DRIVE,document.provider());
        assertEquals("1AbC_def-123",document.rootItemId());
        assertEquals("https://docs.google.com/document/d/1AbC_def-123/edit",document.url());
        assertEquals("0AExampleFolder",folder.rootItemId());
    }

    @Test
    void cloudSharingUrlsRemainUrlsUntilAuthenticatedProviderResolution() {
        RemoteStorageLocationResolution oneDrive=RemoteStorageLocationResolver.resolve("https://1drv.ms/f/s!example?e=abc123");
        RemoteStorageLocationResolution dropbox=RemoteStorageLocationResolver.resolve("https://www.dropbox.com/scl/fo/example?rlkey=abc&dl=0");
        assertEquals(RemoteStorageProviderType.ONEDRIVE,oneDrive.provider());
        assertEquals(RemoteStorageProviderType.DROPBOX,dropbox.provider());
        assertTrue(oneDrive.url().contains("?e=abc123"));
        assertTrue(dropbox.url().contains("rlkey=abc"));
    }

    @Test
    void rejectsUnsafeOrAmbiguousGenericUrls() {
        assertThrows(RemoteStorageException.class,()->RemoteStorageLocationResolver.resolve("http://github.com/openai/example"));
        assertThrows(RemoteStorageException.class,()->RemoteStorageLocationResolver.resolve("https://user:secret@example.org/repository.git"));
        assertThrows(RemoteStorageException.class,()->RemoteStorageLocationResolver.resolve("https://example.org/repository.git?token=secret"));
        assertThrows(RemoteStorageException.class,()->RemoteStorageLocationResolver.resolve("https://github.com/openai/%2e%2e/example"));
    }
}

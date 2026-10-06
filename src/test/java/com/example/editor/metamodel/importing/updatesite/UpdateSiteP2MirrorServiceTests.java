package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UpdateSiteP2MirrorServiceTests {
    @TempDir Path temporary;

    @Test
    void buildsAReproducibleConsumableMirrorFromVerifiedBytes() {
        byte[] plugin = "verified-plugin".getBytes(StandardCharsets.UTF_8);
        String path = "child/plugins/org.example.bundle_1.2.3.jar";
        UpdateSiteRemoteSnapshotResource resource = new UpdateSiteRemoteSnapshotResource(
                "https://updates.example.org/" + path, path, plugin.length,
                UpdateSiteHashing.sha256(plugin), 1, "objects/" + UpdateSiteHashing.sha256(plugin));
        UpdateSiteRemoteSnapshotLicenseEntry license = new UpdateSiteRemoteSnapshotLicenseEntry(path,
                "Example Foundation", "https://updates.example.org", "EPL-2.0",
                "Preserve notices and source obligations", true, true);
        UpdateSiteRemoteSnapshotLock lock = new UpdateSiteRemoteSnapshotLock("1.0", "snapshot-1",
                "https://updates.example.org", "a".repeat(64), Instant.parse("2026-08-04T12:00:00Z"),
                plugin.length, List.of(resource), List.of(license));
        UpdateSiteRemoteSnapshotService snapshots = mock(UpdateSiteRemoteSnapshotService.class);
        when(snapshots.find("snapshot-1")).thenReturn(lock);
        when(snapshots.verifiedResources("snapshot-1")).thenReturn(Map.of(path, plugin));
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.toString());
        UpdateSiteP2MirrorService service = new UpdateSiteP2MirrorService(properties, snapshots,
                JsonMapper.builder().findAndAddModules().build());

        UpdateSiteP2MirrorResult first = service.create("snapshot-1");
        UpdateSiteP2MirrorResult repeated = service.create("snapshot-1");
        byte[] archive = service.export(first.manifest().mirrorId());

        assertThat(first.validation().valid()).isTrue();
        assertThat(first.validation().verifiedArtifacts()).isEqualTo(1);
        assertThat(first.manifest().artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.id()).isEqualTo("org.example.bundle");
            assertThat(artifact.version()).isEqualTo("1.2.3");
            assertThat(artifact.path()).isEqualTo("plugins/org.example.bundle_1.2.3.jar");
        });
        assertThat(repeated.reused()).isTrue();
        assertThat(service.validate(first.manifest().mirrorId()).valid()).isTrue();
        assertThat(archive).isEqualTo(service.export(first.manifest().mirrorId()));
    }
    @Test
    void rejectsSnapshotsWithoutPortablePluginOrFeatureArtifacts() {
        byte[] metadata = "<repository/>".getBytes(StandardCharsets.UTF_8);
        UpdateSiteRemoteSnapshotResource resource = new UpdateSiteRemoteSnapshotResource(
                "https://updates.example.org/content.xml", "content.xml", metadata.length,
                UpdateSiteHashing.sha256(metadata), 0, "objects/" + UpdateSiteHashing.sha256(metadata));
        UpdateSiteRemoteSnapshotLicenseEntry license = new UpdateSiteRemoteSnapshotLicenseEntry("content.xml",
                "Example Foundation", "https://updates.example.org", "EPL-2.0", "Preserve notices", true, true);
        UpdateSiteRemoteSnapshotLock lock = new UpdateSiteRemoteSnapshotLock("1.0", "snapshot-empty",
                "https://updates.example.org", "b".repeat(64), Instant.parse("2026-08-04T12:00:00Z"),
                metadata.length, List.of(resource), List.of(license));
        UpdateSiteRemoteSnapshotService snapshots = mock(UpdateSiteRemoteSnapshotService.class);
        when(snapshots.find("snapshot-empty")).thenReturn(lock);
        when(snapshots.verifiedResources("snapshot-empty")).thenReturn(Map.of("content.xml", metadata));
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.toString());
        UpdateSiteP2MirrorService service = new UpdateSiteP2MirrorService(properties, snapshots,
                JsonMapper.builder().findAndAddModules().build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create("snapshot-empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not contain any plugin or feature");
    }

}

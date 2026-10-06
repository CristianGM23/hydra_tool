package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UpdateSiteMirrorUpdateServiceTests {
    @TempDir Path temporary;

    @Test
    void computesIncrementalDeltaPromotesAndRollsBackAChannel() {
        UpdateSiteP2MirrorService mirrors = mock(UpdateSiteP2MirrorService.class);
        when(mirrors.find("baseline")).thenReturn(result("baseline", List.of(artifact("a", "1.0", "a".repeat(64)))));
        when(mirrors.find("candidate")).thenReturn(result("candidate", List.of(
                artifact("a", "1.1", "b".repeat(64)), artifact("b", "1.0", "c".repeat(64)))));
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties(); properties.setStorageRoot(temporary.toString());
        UpdateSiteMirrorUpdateService service = new UpdateSiteMirrorUpdateService(mirrors, properties,
                JsonMapper.builder().findAndAddModules().build());

        UpdateSiteMirrorUpdateReport first = service.evaluate(new UpdateSiteMirrorUpdateRequest(
                "baseline", "candidate", UpdateSiteMirrorChannel.BETA, true, false, true, true));
        service.evaluate(new UpdateSiteMirrorUpdateRequest(
                "candidate", "baseline", UpdateSiteMirrorChannel.BETA, true, true, true, true));
        UpdateSiteMirrorChannelState rolledBack = service.rollback(UpdateSiteMirrorChannel.BETA);

        assertThat(first.changed()).isEqualTo(1);
        assertThat(first.added()).isEqualTo(1);
        assertThat(first.promoted()).isTrue();
        assertThat(rolledBack.currentMirrorId()).isEqualTo("candidate");
        assertThat(service.find(first.reportId())).isEqualTo(first);
    }

    private UpdateSiteP2MirrorResult result(String id, List<UpdateSiteP2MirrorArtifact> artifacts) {
        var manifest = new UpdateSiteP2MirrorManifest("1.0", id, "snapshot-" + id, "a".repeat(64), "b".repeat(64),
                Instant.parse("2026-08-04T12:00:00Z"), artifacts, "c".repeat(64), "d".repeat(64), true);
        return new UpdateSiteP2MirrorResult(manifest, new UpdateSiteP2MirrorValidation(true, artifacts.size(), artifacts.size(), artifacts.size(), List.of(), List.of()), false);
    }
    private UpdateSiteP2MirrorArtifact artifact(String id, String version, String sha) {
        return new UpdateSiteP2MirrorArtifact("osgi.bundle", id, version, "plugins/" + id + "_" + version + ".jar", 10, sha, "EPL-2.0");
    }
}

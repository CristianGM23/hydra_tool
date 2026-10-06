package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class UpdateSiteRemoteSnapshotServiceTests {
    @TempDir Path temporary;

    @Test
    void createsExportsImportsAndReanalyzesAContentAddressedSnapshot() {
        byte[] content = "<repository/>".getBytes(StandardCharsets.UTF_8);
        byte[] artifacts = "<repository><artifacts size=\"0\"/></repository>".getBytes(StandardCharsets.UTF_8);
        UpdateSiteRemoteAnalysisRequest request = new UpdateSiteRemoteAnalysisRequest(
                "https://updates.example.org/site/", "", 2, true, UpdateSiteImportOptions.defaults());
        UpdateSiteRemoteRepositoryService remote = mock(UpdateSiteRemoteRepositoryService.class);
        UpdateSiteImportApplicationService imports = mock(UpdateSiteImportApplicationService.class);
        UpdateSiteImportAnalysis analysis = analysis();
        List<UpdateSiteInputFile> inputs = List.of(new UpdateSiteInputFile("content.xml", content),
                new UpdateSiteInputFile("artifacts.xml", artifacts));
        List<UpdateSiteRemoteRepositoryFetch> evidence = List.of(
                new UpdateSiteRemoteRepositoryFetch("https://updates.example.org/site/content.xml", "content.xml",
                        content.length, UpdateSiteHashing.sha256(content), false, 0),
                new UpdateSiteRemoteRepositoryFetch("https://updates.example.org/site/artifacts.xml", "artifacts.xml",
                        artifacts.length, UpdateSiteHashing.sha256(artifacts), false, 0));
        when(remote.capture(request)).thenReturn(new UpdateSiteRemoteCapture(request.repositoryUrl(), inputs,
                evidence, content.length + artifacts.length, 0, List.of("Captured complete bounded tree")));
        when(imports.analyze(anyList(), any(UpdateSiteImportOptions.class))).thenReturn(analysis);
        when(remote.downloadForDrift("https://updates.example.org/site/content.xml")).thenReturn(content);
        when(remote.downloadForDrift("https://updates.example.org/site/artifacts.xml")).thenReturn(artifacts);
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        UpdateSiteRemoteSnapshotService service = new UpdateSiteRemoteSnapshotService(properties("source"),
                remote, imports, mapper);

        UpdateSiteRemoteSnapshotResult created = service.create(request);
        assertThatThrownBy(() -> service.exportSnapshot(created.lock().snapshotId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("license metadata");
        List<UpdateSiteRemoteSnapshotLicenseEntry> licenses = created.lock().resources().stream()
                .map(resource -> new UpdateSiteRemoteSnapshotLicenseEntry(resource.virtualPath(), "Example Foundation",
                        resource.url(), "EPL-2.0", "Preserve notices and source offer obligations", true, true)).toList();
        UpdateSiteRemoteSnapshotLock reviewed = service.reviewLicenses(created.lock().snapshotId(),
                new UpdateSiteRemoteSnapshotLicenseReviewRequest(licenses));
        byte[] archive = service.exportSnapshot(created.lock().snapshotId());
        byte[] repeatedArchive = service.exportSnapshot(created.lock().snapshotId());
        UpdateSiteRemoteSnapshotDriftReport drift = service.drift(created.lock().snapshotId());
        UpdateSiteRemoteSnapshotService importedService = new UpdateSiteRemoteSnapshotService(properties("imported"),
                remote, imports, mapper);
        UpdateSiteRemoteSnapshotResult imported = importedService.importSnapshot(archive, UpdateSiteImportOptions.defaults());
        UpdateSiteImportAnalysis offline = importedService.analyzeOffline(new UpdateSiteRemoteSnapshotAnalyzeRequest(
                imported.lock().snapshotId(), UpdateSiteImportOptions.defaults()));
        UpdateSiteRemoteSnapshotOperations operations = importedService.operations(imported.lock().snapshotId());

        assertThat(created.lock().resources()).hasSize(2);
        assertThat(created.lock().licenseReviewComplete()).isFalse();
        assertThat(reviewed.licenseReviewComplete()).isTrue();
        assertThat(created.lock().totalBytes()).isEqualTo(content.length + artifacts.length);
        assertThat(created.lock().treeSha256()).hasSize(64);
        assertThat(archive).isNotEmpty();
        assertThat(repeatedArchive).isEqualTo(archive);
        assertThat(imported.lock().treeSha256()).isEqualTo(created.lock().treeSha256());
        assertThat(imported.lock().snapshotId()).isEqualTo(created.lock().snapshotId());
        assertThat(offline).isSameAs(analysis);
        assertThat(operations.snapshotId()).isEqualTo(imported.lock().snapshotId());
        assertThat(operations.inputs()).extracting(UpdateSiteInputFile::path)
                .containsExactly("artifacts.xml", "content.xml");
        assertThat(drift.drifted()).isFalse();
        assertThat(drift.unchanged()).isEqualTo(2);
    }

    private UpdateSiteImportProperties properties(String directory) {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.resolve(directory).toString());
        properties.setRemoteMaxBytes(1_000_000);
        properties.setRemoteMaxTotalBytes(2_000_000);
        properties.setRemoteMaxResources(32);
        properties.setRemoteMaxDepth(4);
        return properties;
    }

    private UpdateSiteImportAnalysis analysis() {
        return new UpdateSiteImportAnalysis("analysis", Instant.now(), null, List.of(), "", null, List.of(), UpdateSiteRecoveredLayers.empty(), false, List.of(), UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());
    }
}

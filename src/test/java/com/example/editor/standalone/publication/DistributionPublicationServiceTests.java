package com.example.editor.standalone.publication;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class DistributionPublicationServiceTests {
    @TempDir Path temporary;

    @Test
    void publishesAReproducibleChannelManifestAndResolvesUpdates() throws Exception{
        Path distributions = temporary.resolve("distributions");
        Path publications = temporary.resolve("publications");
        Files.createDirectories(distributions.resolve("sample"));
        Files.writeString(distributions.resolve("sample/editor.zip"), "archive");
        var service = new DistributionPublicationService(new DistributionPublicationValidator(),
                JsonMapper.builder().findAndAddModules().build(),
                distributions.toString(), publications.toString());
        PublicationRequest request = new PublicationRequest("sample-editor", "1.2.0",
                PublicationChannel.BETA, PublicationSigningMode.CHECKSUM_ONLY, "sample",
                "https://updates.example.test", List.of(new PublicationArtifactRequest(
                "windows-x64", "editor.zip", "application/zip")), Map.of("commit", "abc"));

        PublicationResult result = service.publish(request);
        UpdateCheckResult update = service.check(new UpdateCheckRequest(
                "sample-editor", "1.1.0", PublicationChannel.BETA, "windows-x64"));

        assertThat(result.validation().valid()).isTrue();
        assertThat(result.manifest().artifacts()).singleElement()
                .satisfies(artifact -> assertThat(artifact.sha256()).hasSize(64));
        assertThat(Path.of(result.channelManifestPath())).isRegularFile();
        assertThat(Path.of(result.manifestPath()).getParent().resolve("editor.zip")).isRegularFile();
        assertThat(update.updateAvailable()).isTrue();
        assertThat(update.availableVersion()).isEqualTo("1.2.0");
    }

    @Test
    void requiresExternalSignatureCompanionsWhenConfigured() throws Exception{
        Path distributions = temporary.resolve("distributions");
        Files.createDirectories(distributions.resolve("sample"));
        Files.writeString(distributions.resolve("sample/editor.zip"), "archive");
        var service = new DistributionPublicationService(new DistributionPublicationValidator(),
                JsonMapper.builder().build(), distributions.toString(), temporary.resolve("publications").toString());

        PublicationValidationReport report = service.validate(new PublicationRequest(
                "sample", "1.0.0", PublicationChannel.INTERNAL, PublicationSigningMode.EXTERNAL_SIGNATURE,
                "sample", "", List.of(new PublicationArtifactRequest(
                "windows-x64", "editor.zip", "application/zip")), Map.of()));

        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).extracting(PublicationDiagnostic::code).contains("SIGNATURE_MISSING");
    }
}

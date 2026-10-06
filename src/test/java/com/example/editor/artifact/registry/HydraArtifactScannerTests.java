package com.example.editor.artifact.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HydraArtifactScannerTests {
    @TempDir Path temporary;

    @Test
    void hashesManyArtifactsByStreamingAndReturnsDeterministicPaths() throws Exception {
        HydraArtifactScannerProperties properties = properties(2500, 64 * 1024L, 32L * 1024L * 1024L);
        HydraArtifactScanner scanner = new HydraArtifactScanner(properties);
        for(int index = 1999; index >= 0; index--) {
            Path path = temporary.resolve("models/group-" + (index % 17) + "/artifact-" + index + ".xmi");
            Files.createDirectories(path.getParent());
            Files.writeString(path, "artifact-" + index);
        }
        var result = scanner.scan(temporary);
        assertThat(result).hasSize(2000);
        assertThat(result).extracting(HydraArtifactDescriptor::relativePath).isSorted();
        HydraArtifactDescriptor first = result.stream().filter(item -> item.relativePath().endsWith("artifact-0.xmi")).findFirst().orElseThrow();
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("artifact-0".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(first.sha256()).isEqualTo(expected);
    }

    @Test
    void failsClosedWhenRegularFileCardinalityExceedsConfiguredBudget() throws Exception {
        HydraArtifactScanner scanner = new HydraArtifactScanner(properties(3, 4096, 16_384));
        for(int index = 0; index < 4; index++) Files.writeString(temporary.resolve("item-" + index + ".json"), "{}");
        assertThatThrownBy(() -> scanner.scan(temporary))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(java.io.IOException.class)
                .rootCause().hasMessageContaining("Artifact scan exceeds configured file limit of 3");
    }

    @Test
    void failsClosedWhenAcceptedBytesExceedTotalBudget() throws Exception {
        HydraArtifactScanner scanner = new HydraArtifactScanner(properties(10, 4096, 4096));
        Files.write(temporary.resolve("a.bin"), new byte[3000]);
        Files.write(temporary.resolve("b.bin"), new byte[3000]);
        assertThatThrownBy(() -> scanner.scan(temporary)).isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(java.io.IOException.class);
    }

    private HydraArtifactScannerProperties properties(int files, long fileBytes, long totalBytes) {
        HydraArtifactScannerProperties properties = new HydraArtifactScannerProperties();
        properties.setMaximumScanFiles(files);
        properties.setMaximumScanFileBytes(fileBytes);
        properties.setMaximumScanTotalBytes(totalBytes);
        properties.setMaximumScanDepth(8);
        return properties;
    }
}

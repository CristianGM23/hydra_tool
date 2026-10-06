package com.example.editor.definition.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.project.storage.ProjectVisualAssetPolicy;

class EditorVisualAssetServiceTests {
    @TempDir Path directory;

    @Test
    void storesImportedFiguresAsContentAddressedAssetsThatRemainReadableWithoutAnActiveProject() {
        EditorVisualAssetService service = service();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"12\" height=\"12\"><rect width=\"12\" height=\"12\"/></svg>"
                .getBytes(StandardCharsets.UTF_8);

        String first = service.store("fixture/icons/datastore.svg", svg);
        String second = service.store("another/location/datastore.svg", svg);
        EditorVisualAssetService.EditorVisualAsset loaded = service.read(first);

        assertThat(first).startsWith(EditorVisualAssetService.PREFIX).endsWith(".svg");
        assertThat(second).isEqualTo(first);
        assertThat(EditorVisualAssetService.canonical(first)).isTrue();
        assertThat(loaded.mediaType()).isEqualTo("image/svg+xml");
        assertThat(loaded.content()).isEqualTo(svg);
    }

    @Test
    void rejectsActiveSvgContentBeforeItCanBecomeAnEditorAsset() {
        EditorVisualAssetService service = service();
        byte[] active = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.store("unsafe.svg", active))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active content");
    }

    private EditorVisualAssetService service() {
        StorageProperties properties = new StorageProperties(directory.resolve("metamodels"), directory.resolve("models"),
                directory.resolve("notations"), directory.resolve("validations"), directory.resolve("quick-fixes"),
                directory.resolve("generators"), directory.resolve("assets"), directory.resolve("exports"), true);
        return new EditorVisualAssetService(properties, new ProjectVisualAssetPolicy());
    }
}

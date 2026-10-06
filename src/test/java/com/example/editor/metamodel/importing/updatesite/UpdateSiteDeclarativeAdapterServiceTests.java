package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateSiteDeclarativeAdapterServiceTests {
    @TempDir Path temporary;

    @Test
    void validatesPersistsAndRegistersDataOnlyAdapterDocuments() {
        UpdateSiteImportProperties properties = properties();
        UpdateSiteExtensionAdapterCatalog catalog = new UpdateSiteExtensionAdapterCatalog();
        UpdateSiteDeclarativeAdapterService service = new UpdateSiteDeclarativeAdapterService(properties,
                catalog, JsonMapper.builder().findAndAddModules().build());

        UpdateSiteDeclarativeAdapterImportResult result = service.importAdapter(document().getBytes(StandardCharsets.UTF_8), false);

        assertThat(result.persisted()).isTrue();
        assertThat(result.validation().valid()).isTrue();
        assertThat(result.fileName()).endsWith(".hydra-adapter.json");
        assertThat(catalog.descriptors()).extracting(UpdateSiteExtensionAdapterDescriptor::id)
                .contains("org.example:custom-extension");
    }

    @Test
    void rejectsUnknownFieldsAndBuiltInReplacement() {
        UpdateSiteImportProperties properties = properties();
        UpdateSiteExtensionAdapterCatalog catalog = new UpdateSiteExtensionAdapterCatalog();
        UpdateSiteDeclarativeAdapterService service = new UpdateSiteDeclarativeAdapterService(properties,
                catalog, JsonMapper.builder().findAndAddModules().build());
        String unknown = document().replace("\"annotations\": {}", "\"annotations\": {}, \"script\": \"run()\"");

        assertThat(service.preview(unknown.getBytes(StandardCharsets.UTF_8)).errors())
                .contains("Unknown document field: script");
        String builtIn = document().replace("org.example:custom-extension", "custom-palettes");
        assertThat(service.importAdapter(builtIn.getBytes(StandardCharsets.UTF_8), true).persisted()).isFalse();
    }

    @Test
    void versionsTestsExplainsActivatesExportsAndDeletesAuthoredAdapters() {
        UpdateSiteImportProperties properties = properties();
        UpdateSiteExtensionAdapterCatalog catalog = new UpdateSiteExtensionAdapterCatalog();
        UpdateSiteDeclarativeAdapterService service = new UpdateSiteDeclarativeAdapterService(properties,
                catalog, JsonMapper.builder().findAndAddModules().build());
        String versionOne = document().replace("\"annotations\": {}", "\"annotations\": {\"version\":\"1.0.0\"}");
        String versionTwo = document().replace("\"Custom extension\"", "\"Custom extension 1.1\"")
                .replace("\"annotations\": {}", "\"annotations\": {\"version\":\"1.1.0\"}");

        service.importAdapter(versionOne.getBytes(StandardCharsets.UTF_8), false);
        service.importAdapter(versionTwo.getBytes(StandardCharsets.UTF_8), true);
        UpdateSiteAdapterFixtureResult fixture = service.testFixture(versionTwo.getBytes(StandardCharsets.UTF_8),
                new UpdateSiteAdapterFixture("org.example.custom", "definition",
                        java.util.Map.of("id", "fixture", "class", "org.example.Handler")));
        UpdateSiteDeclarativeAdapterEntry disabled = service.setEnabled("org.example:custom-extension", false);

        assertThat(service.versions("org.example:custom-extension"))
                .extracting(UpdateSiteDeclarativeAdapterVersionEntry::version)
                .containsExactly("1.1.0", "1.0.0");
        assertThat(service.exportVersion("org.example:custom-extension", "1.0.0"))
                .asString(StandardCharsets.UTF_8).contains("\"version\" : \"1.0.0\"");
        assertThat(fixture.matched()).isTrue();
        assertThat(fixture.resolvedId()).isEqualTo("fixture");
        assertThat(fixture.implementation()).isEqualTo("org.example.Handler");
        assertThat(fixture.explanations()).contains("The extension-point token matches.", "The XML element name matches.");
        assertThat(disabled.document().adapter().enabled()).isFalse();
        assertThat(catalog.match("org.example.custom", "definition")).isEmpty();

        service.delete("org.example:custom-extension");
        assertThat(service.listPersisted()).isEmpty();
        assertThat(service.versions("org.example:custom-extension")).isEmpty();
    }

    @Test
    void ordersSemanticVersionsIncludingNumericPreReleaseIdentifiers() {
        UpdateSiteDeclarativeAdapterService service = new UpdateSiteDeclarativeAdapterService(properties(),
                new UpdateSiteExtensionAdapterCatalog(), JsonMapper.builder().findAndAddModules().build());
        String alphaTwo = document().replace("\"Custom extension\"", "\"Custom extension alpha 2\"")
                .replace("\"annotations\": {}", "\"annotations\": {\"version\":\"1.2.0-alpha.2\"}");
        String alphaTen = document().replace("\"Custom extension\"", "\"Custom extension alpha 10\"")
                .replace("\"annotations\": {}", "\"annotations\": {\"version\":\"1.2.0-alpha.10\"}");
        String stable = document().replace("\"Custom extension\"", "\"Custom extension stable\"")
                .replace("\"annotations\": {}", "\"annotations\": {\"version\":\"1.2.0\"}");

        service.importAdapter(alphaTwo.getBytes(StandardCharsets.UTF_8), false);
        service.importAdapter(alphaTen.getBytes(StandardCharsets.UTF_8), true);
        service.importAdapter(stable.getBytes(StandardCharsets.UTF_8), true);

        assertThat(service.versions("org.example:custom-extension"))
                .extracting(UpdateSiteDeclarativeAdapterVersionEntry::version)
                .containsExactly("1.2.0", "1.2.0-alpha.10", "1.2.0-alpha.2");
    }

    @Test
    void keepsPublishedVersionsImmutable() {
        UpdateSiteDeclarativeAdapterService service = new UpdateSiteDeclarativeAdapterService(properties(),
                new UpdateSiteExtensionAdapterCatalog(), JsonMapper.builder().findAndAddModules().build());
        String versionOne = document().replace("\"annotations\": {}", "\"annotations\": {\"version\":\"1.0.0\"}");
        String conflicting = versionOne.replace("\"Custom extension\"", "\"Different content\"");
        service.importAdapter(versionOne.getBytes(StandardCharsets.UTF_8), false);

        assertThatThrownBy(() -> service.importAdapter(conflicting.getBytes(StandardCharsets.UTF_8), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version 1.0.0 already exists");
        assertThat(service.exportAdapter("org.example:custom-extension"))
                .asString(StandardCharsets.UTF_8).contains("Custom extension").doesNotContain("Different content");
    }

    private UpdateSiteImportProperties properties() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.toString());
        return properties;
    }

    private String document() {
        return """
                {
                  "schemaVersion": "1.0",
                  "adapter": {
                    "id": "org.example:custom-extension",
                    "label": "Custom extension",
                    "extensionPointTokens": ["org.example.custom"],
                    "elementNames": ["definition"],
                    "semanticKind": "RUNTIME_EXTENSION",
                    "neutralTarget": "runtimeExtension",
                    "purpose": "Recover a custom declaration without executing code.",
                    "idAttributes": ["id"],
                    "implementationAttributes": ["class"],
                    "confidence": "DERIVED",
                    "enabled": true
                  },
                  "annotations": {}
                }
                """;
    }
}

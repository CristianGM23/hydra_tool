package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class UpdateSiteAdapterVerificationServiceTests {
    @TempDir Path temporary;

    @Test
    void generatesFixturesRunsBoundedFuzzingAndPersistsVerificationEvidence() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties(); properties.setStorageRoot(temporary.toString());
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var adapters = new UpdateSiteDeclarativeAdapterService(properties, new UpdateSiteExtensionAdapterCatalog(), mapper);
        var bench = new UpdateSiteAdapterBenchService(properties, adapters, mapper);
        var service = new UpdateSiteAdapterVerificationService(properties, adapters, bench, mapper);

        UpdateSiteAdapterVerificationReport report = service.verify(new UpdateSiteAdapterVerificationRequest(
                "", "", document(), 8, List.of("EXPECTED_MATCH")));

        assertThat(report.generatedFixtures()).isGreaterThanOrEqualTo(3);
        assertThat(report.fuzzCases()).isEqualTo(8);
        assertThat(report.invariantChecks()).isGreaterThan(0);
        assertThat(service.find(report.reportId())).isEqualTo(report);
        assertThat(new String(service.html(report.reportId()), StandardCharsets.UTF_8)).contains("Advanced adapter verification");
    }

    private String document() {
        return """
                {"schemaVersion":"1.0","adapter":{"id":"org.example:verified","label":"Verified","extensionPointTokens":["org.example.verified"],"elementNames":["definition"],"semanticKind":"RUNTIME_EXTENSION","neutralTarget":"runtimeExtension","purpose":"Verify declarative recovery.","idAttributes":["id"],"implementationAttributes":["class"],"confidence":"EXACT","enabled":true},"annotations":{"version":"1.0.0"}}
                """;
    }
}

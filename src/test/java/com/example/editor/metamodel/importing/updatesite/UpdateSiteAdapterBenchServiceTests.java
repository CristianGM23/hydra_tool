package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.stubbing.Answer;
import tools.jackson.databind.json.JsonMapper;

class UpdateSiteAdapterBenchServiceTests {
    @TempDir Path temporary;

    @Test
    void executesPositiveNegativeAndMutationFixturesWithoutRunningExtensionCode() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.toString());
        UpdateSiteDeclarativeAdapterService adapters = mock(UpdateSiteDeclarativeAdapterService.class);
        UpdateSiteExtensionAdapterDescriptor descriptor = new UpdateSiteExtensionAdapterDescriptor(
                "org.example:palette", "Palette", List.of("org.example.palette"), List.of("tool"),
                UpdateSiteSemanticKind.RUNTIME_EXTENSION, "palette.tools", "Recover palette tools",
                List.of("id"), List.of("class"), UpdateSiteConfidence.EXACT, true);
        UpdateSiteDeclarativeAdapterDocument document = new UpdateSiteDeclarativeAdapterDocument(
                "1.0", descriptor, Map.of("version", "1.2.0"));
        when(adapters.preview(any(byte[].class))).thenReturn(new UpdateSiteDeclarativeAdapterValidation(
                true, document, "fingerprint", List.of(), List.of()));
        when(adapters.testFixture(any(byte[].class), any(UpdateSiteAdapterFixture.class)))
                .thenAnswer((Answer<UpdateSiteAdapterFixtureResult>) invocation -> {
                    UpdateSiteAdapterFixture fixture = invocation.getArgument(1);
                    boolean matched = descriptor.matches(fixture.extensionPoint(), fixture.elementName());
                    String id = matched ? fixture.attributes().getOrDefault("id", "") : "";
                    String implementation = matched ? fixture.attributes().getOrDefault("class", "") : "";
                    return new UpdateSiteAdapterFixtureResult(true, matched, id, implementation,
                            descriptor.neutralTarget(), List.of("Declarative match evaluated."), List.of());
                });
        UpdateSiteAdapterBenchService service = new UpdateSiteAdapterBenchService(properties, adapters,
                JsonMapper.builder().findAndAddModules().build());
        String inline = new String("{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        UpdateSiteAdapterBenchRequest request = new UpdateSiteAdapterBenchRequest("", "", inline, List.of(
                new UpdateSiteAdapterBenchCase("positive", new UpdateSiteAdapterFixture(
                        "org.example.palette", "tool", Map.of("id", "create", "class", "Tool")), true),
                new UpdateSiteAdapterBenchCase("negative", new UpdateSiteAdapterFixture(
                        "org.example.other", "other", Map.of()), false)), true);

        UpdateSiteAdapterBenchReport report = service.run(request);

        assertThat(report.passedFixtures()).isEqualTo(2);
        assertThat(report.failedFixtures()).isZero();
        assertThat(report.mutations()).hasSize(3);
        assertThat(report.survivingMutations()).isZero();
        assertThat(report.coverage().coveredTokens()).isEqualTo(1);
        assertThat(report.coverage().coveredElements()).isEqualTo(1);
        assertThat(service.find(report.reportId()).adapterVersion()).isEqualTo("1.2.0");
        assertThat(new String(service.html(report.reportId()), StandardCharsets.UTF_8))
                .contains("Adapter bench", "2 fixtures passed");
    }
}

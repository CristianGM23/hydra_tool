package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteSemanticEditorRecoveryServiceTests {
    private final UpdateSiteSemanticEditorRecoveryService service = new UpdateSiteSemanticEditorRecoveryService(new UpdateSiteExtensionAdapterCatalog());

    @Test
    void recoversCommandsHandlersMenusAndExplainsUnknownExtensions() {
        String plugin = """
                <plugin>
                  <extension point="org.eclipse.ui.commands">
                    <command id="library.open" name="Open library"/>
                  </extension>
                  <extension point="org.eclipse.ui.handlers">
                    <handler commandId="library.open" class="org.example.OpenHandler"/>
                  </extension>
                  <extension point="org.eclipse.ui.menus">
                    <menuContribution locationURI="menu:org.eclipse.ui.main.menu">
                      <command commandId="library.open" label="Open library"/>
                    </menuContribution>
                  </extension>
                  <extension point="org.example.unsupported">
                    <customThing id="custom"/>
                  </extension>
                </plugin>
                """;
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(
                Map.of("plugins/library.ui.jar!/plugin.xml", plugin.getBytes(StandardCharsets.UTF_8)), "f", plugin.length());

        UpdateSiteSemanticRecovery result = service.recover(repository);

        assertThat(result.elements()).extracting(UpdateSiteSemanticElementDraft::kind)
                .contains(UpdateSiteSemanticKind.COMMAND, UpdateSiteSemanticKind.HANDLER,
                        UpdateSiteSemanticKind.MENU, UpdateSiteSemanticKind.UNREPRESENTABLE);
        assertThat(result.elements()).filteredOn(value -> value.kind() == UpdateSiteSemanticKind.UNREPRESENTABLE)
                .allSatisfy(value -> {
                    assertThat(value.representable()).isFalse();
                    assertThat(value.purpose()).contains("not yet mapped");
                    assertThat(value.warnings()).anyMatch(warning -> warning.contains("not silently discarded"));
                });
    }
    @Test
    void mapsCustomPaletteExtensionsThroughTheDeclarativeAdapterCatalog() {
        String plugin = """
                <plugin>
                  <extension point="org.example.domain.palette">
                    <palette id="library.palette" label="Library tools" provider="org.example.LibraryPaletteProvider"/>
                  </extension>
                </plugin>
                """;
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(
                Map.of("plugins/library.ui.jar!/plugin.xml", plugin.getBytes(StandardCharsets.UTF_8)), "f", plugin.length());

        UpdateSiteSemanticRecovery result = service.recover(repository);

        assertThat(result.elements()).singleElement().satisfies(value -> {
            assertThat(value.label()).isEqualTo("library.palette");
            assertThat(value.neutralTarget()).contains("palette");
            assertThat(value.confidence()).isEqualTo(UpdateSiteConfidence.DERIVED);
            assertThat(value.warnings()).anyMatch(warning -> warning.contains("custom-palettes"));
        });
    }

    @Test
    void readsCamelCaseAttributesFromDeclarativeAdaptersWithoutLosingTheStableIdentifier() {
        String plugin = """
                <plugin>
                  <extension point="org.example.domain.operation">
                    <action commandId="library.refresh" label="Refresh library" implementation="org.example.RefreshAction"/>
                  </extension>
                </plugin>
                """;
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(
                Map.of("plugins/library.ui.jar!/plugin.xml", plugin.getBytes(StandardCharsets.UTF_8)), "f", plugin.length());

        UpdateSiteSemanticRecovery result = service.recover(repository);

        assertThat(result.elements()).singleElement().satisfies(value -> {
            assertThat(value.id()).contains("library.refresh");
            assertThat(value.implementation()).isEqualTo("org.example.RefreshAction");
            assertThat(value.warnings()).anyMatch(warning -> warning.contains("custom-operations"));
        });
    }

}

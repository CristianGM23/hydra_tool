package com.example.editor.metamodel.importing.updatesite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateSiteRemoteRepositoryServiceTests {
    @Test
    void remainsOfflineByDefault() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        UpdateSiteRemoteRepositoryService service = new UpdateSiteRemoteRepositoryService(properties, null);

        assertThatThrownBy(() -> service.analyze(new UpdateSiteRemoteAnalysisRequest(
                "https://updates.example.org/", "", 1, true, UpdateSiteImportOptions.defaults())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsNonHttpsAndNonAllowlistedHostsBeforeDownloading() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setRemoteEnabled(true);
        properties.setRemoteAllowedHosts(java.util.List.of("updates.example.org"));
        UpdateSiteRemoteRepositoryService service = new UpdateSiteRemoteRepositoryService(properties, null);

        assertThatThrownBy(() -> service.validate("http://updates.example.org/site/"))
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> service.validate("https://other.example.org/site/"))
                .hasMessageContaining("allow").hasMessageContaining("other.example.org");
    }

    @Test
    void normalizesBlankAndNullAllowlistEntries() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();

        properties.setRemoteAllowedHosts(java.util.Arrays.asList(null, "", " Updates.Example.org "));

        assertThat(properties.getRemoteAllowedHosts())
                .containsExactly("updates.example.org");
    }

    @Test
    void keepsOneProductionConstructorForSpringInjection() {
        assertThat(UpdateSiteRemoteRepositoryService.class.getDeclaredConstructors()).hasSize(1);
    }

}

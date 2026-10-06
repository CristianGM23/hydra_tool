package com.example.editor.metamodel.importing.updatesite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateSiteGitRepositoryLocatorTests {
    private final UpdateSiteGitRepositoryLocator locator = new UpdateSiteGitRepositoryLocator();

    @Test
    void normalizesGithubAndGitlabFolderUrlsWithoutCredentials() {
        var github = locator.locate("https://github.com/acme/tools/tree/release-1/repository/site", "", "");
        assertThat(github.cloneUrl()).isEqualTo("https://github.com/acme/tools.git");
        assertThat(github.reference()).isEqualTo("release-1");
        assertThat(github.subdirectory()).isEqualTo("repository/site");
        assertThat(github.provider()).isEqualTo("GITHUB");

        var gitlab = locator.locate("https://gitlab.com/acme/tools/-/tree/main/p2", "", "");
        assertThat(gitlab.cloneUrl()).isEqualTo("https://gitlab.com/acme/tools.git");
        assertThat(gitlab.reference()).isEqualTo("main");
        assertThat(gitlab.subdirectory()).isEqualTo("p2");
        assertThat(gitlab.provider()).isEqualTo("GITLAB");
    }

    @Test
    void explicitCoordinatesOverrideBrowserUrlAndUnsafeCredentialUrlsAreRejected() {
        var located = locator.locate("https://github.com/acme/tools/tree/main/p2", "v2", "release/site");
        assertThat(located.reference()).isEqualTo("v2");
        assertThat(located.subdirectory()).isEqualTo("release/site");
        assertThatThrownBy(() -> locator.locate("https://user:secret@github.com/acme/tools.git", "", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("credentials");
    }
}

package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class P2BundleAnalyzerTests {

    @Test
    void classifiesModelGmfSiriusXtextEditEditorRuntimeUiAndThirdPartyBundles() {
        Map<String, byte[]> siteEntries = UpdateSiteTestFixtures.entries(
                "content.xml", UpdateSiteTestFixtures.contentXml(),
                "artifacts.xml", UpdateSiteTestFixtures.artifactsXml(),
                "plugins/com.example.model_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.model", "1.0.0", "org.eclipse.emf.ecore",
                        UpdateSiteTestFixtures.entries("model/sample.ecore", UpdateSiteTestFixtures.ecore())),
                "plugins/com.example.diagram_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.diagram", "1.0.0", "org.eclipse.gmf.runtime",
                        UpdateSiteTestFixtures.entries("model/sample.gmfmap", "<mapping:Mapping xmlns:mapping=\"http://www.eclipse.org/gmf/2008/mappings\"/>")),
                "plugins/com.example.design_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.design", "1.0.0", "org.eclipse.sirius",
                        UpdateSiteTestFixtures.entries("description/sample.odesign", "<description:Group xmlns:description=\"http://www.eclipse.org/sirius/description/1.1.0\"/>")),
                "plugins/com.example.dsl_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.dsl", "1.0.0", "org.eclipse.xtext",
                        UpdateSiteTestFixtures.entries("com/example/Sample.xtext", "grammar com.example.Sample with org.eclipse.xtext.common.Terminals")),
                "plugins/com.example.model.edit_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.model.edit", "1.0.0", "org.eclipse.emf.edit",
                        UpdateSiteTestFixtures.entries("com/example/ItemProviderAdapterFactory.class", new byte[]{1, 2, 3})),
                "plugins/com.example.model.editor_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.model.editor", "1.0.0", "org.eclipse.ui",
                        UpdateSiteTestFixtures.entries("plugin.xml", "<plugin><extension point=\"org.eclipse.ui.editors\"/></plugin>")),
                "plugins/com.example.runtime_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.runtime", "1.0.0", "",
                        UpdateSiteTestFixtures.entries("com/example/Runtime.class", new byte[]{1, 2, 3})),
                "plugins/com.example.ui_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.ui", "1.0.0", "org.eclipse.ui",
                        UpdateSiteTestFixtures.entries("plugin.xml", "<plugin><extension point=\"org.eclipse.ui.views\"/></plugin>")),
                "plugins/org.example.utility_1.0.0.jar", UpdateSiteTestFixtures.jar("org.example.utility", "1.0.0", "",
                        UpdateSiteTestFixtures.entries("org/example/Utility.class", new byte[]{1, 2, 3})),
                "features/com.example.feature_1.0.0.jar", UpdateSiteTestFixtures.jar("com.example.feature", "1.0.0", "",
                        UpdateSiteTestFixtures.entries("feature.xml", "<feature id=\"com.example.feature\" version=\"1.0.0\"><plugin id=\"com.example.model\"/></feature>")));
        UpdateSiteRepositoryContent repository = new UpdateSiteArchiveReader(new UpdateSiteImportProperties())
                .read(List.of(new UpdateSiteInputFile("site.zip", UpdateSiteTestFixtures.zip(siteEntries))));

        UpdateSiteRepositorySummary summary = new P2BundleAnalyzer(new P2BundleClassifier())
                .analyze(repository, new P2RepositoryMetadataParser().parse(repository));

        assertThat(summary.name()).isEqualTo("Library Update Site");
        assertThat(summary.features()).extracting(UpdateSiteFeatureDescriptor::id).contains("com.example.feature");
        assertThat(kind(summary, "com.example.model")).isEqualTo(UpdateSiteBundleKind.MODEL_PLUGIN);
        assertThat(kind(summary, "com.example.diagram")).isEqualTo(UpdateSiteBundleKind.GMF_DIAGRAM_PLUGIN);
        assertThat(kind(summary, "com.example.design")).isEqualTo(UpdateSiteBundleKind.SIRIUS_VIEWPOINT_PLUGIN);
        assertThat(kind(summary, "com.example.dsl")).isEqualTo(UpdateSiteBundleKind.XTEXT_LANGUAGE_PLUGIN);
        assertThat(kind(summary, "com.example.model.edit")).isEqualTo(UpdateSiteBundleKind.EDIT_PLUGIN);
        assertThat(kind(summary, "com.example.model.editor")).isEqualTo(UpdateSiteBundleKind.EDITOR_PLUGIN);
        assertThat(kind(summary, "com.example.runtime")).isEqualTo(UpdateSiteBundleKind.RUNTIME_PLUGIN);
        assertThat(kind(summary, "com.example.ui")).isEqualTo(UpdateSiteBundleKind.UI_PLUGIN);
        assertThat(kind(summary, "org.example.utility")).isEqualTo(UpdateSiteBundleKind.THIRD_PARTY_DEPENDENCY);
        assertThat(summary.contentMetadataFound()).isTrue();
        assertThat(summary.artifactMetadataFound()).isTrue();
    }

    @Test
    void preservesMissingRequiredDependenciesAndCyclesForReview() {
        byte[] a = UpdateSiteTestFixtures.jar("a", "1.0.0", "b,missing.bundle", Map.of());
        byte[] b = UpdateSiteTestFixtures.jar("b", "1.0.0", "a", Map.of());
        UpdateSiteRepositoryContent repository = new UpdateSiteArchiveReader(new UpdateSiteImportProperties()).read(List.of(
                new UpdateSiteInputFile("plugins/a_1.0.0.jar", a), new UpdateSiteInputFile("plugins/b_1.0.0.jar", b)));

        UpdateSiteRepositorySummary summary = new P2BundleAnalyzer(new P2BundleClassifier())
                .analyze(repository, new P2RepositoryMetadataParser().parse(repository));

        assertThat(summary.missingDependencies()).extracting(UpdateSiteDependency::targetId).contains("missing.bundle");
        assertThat(summary.dependencyCycles()).isNotEmpty();
    }

    private UpdateSiteBundleKind kind(UpdateSiteRepositorySummary summary, String id) {
        return summary.bundles().stream().filter(bundle -> bundle.id().equals(id)).findFirst().orElseThrow().kind();
    }
}

package com.example.editor.standalone.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandaloneProductionReadinessTests {
    @TempDir Path root;
    private final StandaloneProductionReadinessService readiness = new StandaloneProductionReadinessService();

    @Test void sourceOnlyArchiveIsNotAnInstallableBuild() {
        StandaloneDistributionValidationReport result = readiness.assess(root, profile(Set.of(StandaloneDistributionTarget.PWA)), accepted());
        assertThat(result.valid()).isFalse();
        assertThat(result.diagnostics()).extracting(StandaloneDistributionDiagnostic::code)
                .contains("BUILT_ENTRY", "BUILT_MANIFEST", "BUILT_WORKER");
    }

    @Test void offlinePwaMustContainReferencedBuiltAssets() throws Exception {
        Files.createDirectories(root.resolve("dist"));
        Files.writeString(root.resolve("dist/index.html"), "<script src=\"assets/main.js\"></script>");
        Files.writeString(root.resolve("dist/manifest.webmanifest"), "{}");
        Files.writeString(root.resolve("dist/service-worker.js"), """
                const SHELL = ['./', './index.html', './assets/main.js'];
                self.addEventListener('install', event =>
                    event.waitUntil(caches.open('test').then(cache => cache.addAll(SHELL))));
                self.addEventListener('fetch', event =>
                    event.respondWith(caches.match(event.request).then(hit => hit || fetch(event.request))));
                """);
        StandaloneDistributionValidationReport bad = readiness.assess(root,
                profile(Set.of(StandaloneDistributionTarget.PWA)), accepted());
        assertThat(bad.valid()).isFalse();
        assertThat(bad.diagnostics()).extracting(StandaloneDistributionDiagnostic::code)
                .contains("MISSING_BUILT_ASSET").doesNotContain("OFFLINE_WORKER");
        Files.createDirectories(root.resolve("dist/assets"));
        Files.writeString(root.resolve("dist/assets/main.js"), "document.body.innerHTML='ready';");
        StandaloneDistributionValidationReport ready = readiness.assess(root,
                profile(Set.of(StandaloneDistributionTarget.PWA)), accepted());
        assertThat(ready.valid()).isTrue();
        assertThat(ready.metadata()).containsEntry("nativeInstallerCertified", "false");
    }

    @Test void missingOfflineCacheIsRejectedEvenWhenAllBuiltAssetsExist() throws Exception {
        Files.createDirectories(root.resolve("dist/assets"));
        Files.writeString(root.resolve("dist/index.html"), "<script src=\"assets/main.js\"></script>");
        Files.writeString(root.resolve("dist/assets/main.js"), "document.body.innerHTML='ready';");
        Files.writeString(root.resolve("dist/manifest.webmanifest"), "{}");
        Files.writeString(root.resolve("dist/service-worker.js"), "self.addEventListener('fetch', ()=>{});");
        StandaloneDistributionValidationReport result = readiness.assess(root,
                profile(Set.of(StandaloneDistributionTarget.PWA)), accepted());
        assertThat(result.valid()).isFalse();
        assertThat(result.diagnostics()).extracting(StandaloneDistributionDiagnostic::code)
                .contains("OFFLINE_WORKER").doesNotContain("MISSING_BUILT_ASSET");
    }

    @Test void desktopDistributionNeedsRustProjectAndBuiltWeb() throws Exception {
        var profile = profile(Set.of(StandaloneDistributionTarget.TAURI_WINDOWS));
        Files.createDirectories(root.resolve("dist"));
        Files.writeString(root.resolve("dist/index.html"), "<main>editor</main>");
        assertThat(readiness.assess(root, profile, accepted()).valid()).isFalse();
        new StandaloneDistributionMaterializer().materialize(root, new StandaloneDistributionPlanner().plan(profile));
        assertThat(readiness.assess(root, profile, accepted()).valid()).isTrue();
    }

    @Test void remoteExecutableAssetsMustNotBeClaimedOffline() throws Exception {
        Files.createDirectories(root.resolve("dist"));
        Files.writeString(root.resolve("dist/index.html"), "<script src=\"https://host.invalid/main.js\"></script>");
        Files.writeString(root.resolve("dist/manifest.webmanifest"), "{}");
        Files.writeString(root.resolve("dist/service-worker.js"), "const SHELL=[];caches.open('test');fetch('x');");
        StandaloneDistributionValidationReport result = readiness.assess(root,
                profile(Set.of(StandaloneDistributionTarget.PWA)), accepted());
        assertThat(result.valid()).isFalse();
        assertThat(result.diagnostics()).extracting(StandaloneDistributionDiagnostic::code)
                .contains("REMOTE_EXECUTABLE_ASSET");
    }

    private StandaloneDistributionProfile profile(Set<StandaloneDistributionTarget> targets) {
        return new StandaloneDistributionProfile("Library Editor", "1.3.0", targets, true,
                List.of(".xmi"), "stable");
    }
    private StandaloneDistributionValidationReport accepted() {
        return new StandaloneDistributionValidationReport(true, List.of(), Map.of());
    }
}

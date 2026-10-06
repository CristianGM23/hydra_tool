package com.example.editor.standalone.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StandaloneDistributionPipelineTests {
    @TempDir Path root;
    private final StandaloneDistributionProfile profile = new StandaloneDistributionProfile("HYDRA Library Editor", "2.9.0",
            Set.of(StandaloneDistributionTarget.PWA, StandaloneDistributionTarget.TAURI_WINDOWS), true,
            List.of(".hydra", ".xmi"), "stable");

    @Test
    void materializesAndValidatesPwaAndTauriArtifacts() throws IOException{
        List<StandaloneDistributionArtifact> plan = new StandaloneDistributionPlanner().plan(profile);
        StandaloneDistributionMaterializationResult result = new StandaloneDistributionMaterializer().materialize(root, plan);
        java.nio.file.Files.writeString(root.resolve("index.html"), "<main>HYDRA</main>");
        StandaloneDistributionValidationReport report = new StandaloneDistributionValidationService(JsonMapper.builder().build()).validate(root, profile);
        assertThat(result.writtenPaths()).contains("public/manifest.webmanifest", "src-tauri/tauri.conf.json");
        assertThat(result.sha256ByPath()).hasSize(plan.size()); assertThat(report.valid()).isTrue();
    }

    @Test
    void reportsMissingRequiredArtifactsInsteadOfClaimingAValidPackage(){
        StandaloneDistributionValidationReport report = new StandaloneDistributionValidationService(JsonMapper.builder().build()).validate(root, profile);
        assertThat(report.valid()).isFalse();
        assertThat(report.diagnostics()).extracting(StandaloneDistributionDiagnostic::code).contains("APP_ENTRY", "MISSING_JSON", "PWA_WORKER");
    }

    @Test
    void createsAReproducibleArchiveAndExcludesTransientDirectories() throws Exception{
        new StandaloneDistributionMaterializer().materialize(root, new StandaloneDistributionPlanner().plan(profile));
        Files.writeString(root.resolve("index.html"), "<main>HYDRA</main>"); Files.createDirectories(root.resolve("node_modules/pkg"));
        Files.writeString(root.resolve("node_modules/pkg/ignored.js"), "ignored");
        StandaloneDistributionValidationReport report = new StandaloneDistributionValidationService(JsonMapper.builder().build()).validate(root, profile);
        Path first = root.getParent().resolve("first.zip"); Path second = root.getParent().resolve("second.zip");
        StandaloneDistributionPackageResult one = new StandaloneDistributionPackager().packageProject(root, first, report);
        StandaloneDistributionPackageResult two = new StandaloneDistributionPackager().packageProject(root, second, report);
        assertThat(one.sha256()).isEqualTo(two.sha256());
        assertThat(one.entries()).contains("index.html").noneMatch(value -> value.contains("node_modules"));
    }

    @Test
    void refusesPackagingWhenValidationContainsErrors(){
        StandaloneDistributionValidationReport invalid = new StandaloneDistributionValidationReport(false,
                List.of(new StandaloneDistributionDiagnostic("INVALID", StandaloneDistributionDiagnosticSeverity.ERROR, "", "invalid")), java.util.Map.of());
        assertThatThrownBy(() -> new StandaloneDistributionPackager().packageProject(root, root.getParent().resolve("invalid.zip"), invalid))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid");
    }
}

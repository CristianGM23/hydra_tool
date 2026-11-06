package com.example.editor.integration.roundtrip.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.integration.roundtrip.ManualRegionPreserver;
import com.example.editor.integration.roundtrip.RoundTripElementMapping;
import com.example.editor.integration.roundtrip.RoundTripManifest;
import com.example.editor.integration.roundtrip.RoundTripSyncService;
import tools.jackson.databind.json.JsonMapper;

class RoundTripResolutionWorkflowServiceTests {
    @TempDir Path root;
    private JsonMapper mapper;
    private RoundTripResolutionWorkflowService service;

    @BeforeEach
    void setUp(){
        mapper = JsonMapper.builder().findAndAddModules().build();
        ManualRegionPreserver regions = new ManualRegionPreserver();
        service = new RoundTripResolutionWorkflowService(new RoundTripSyncService(mapper, regions),
                new AdvancedRoundTripConflictResolver(), regions, mapper);
    }

    @Test
    void blocksApplyUntilEveryConflictHasAnExplicitDecision() throws Exception{
        managed("src/A.txt", "old"); Files.writeString(root.resolve("src/A.txt"), "native");
        RoundTripResolutionPlan plan = service.prepare(compilation(file("src/A.txt", "generated")), "profile", root, List.of());
        assertThat(plan.canApply()).isFalse(); assertThat(plan.unresolved()).hasSize(1);
        assertThatThrownBy(() -> service.apply(plan)).isInstanceOf(TechnologyCompilationException.class).hasMessageContaining("unresolved");
    }

    @Test
    void appliesWithBackupAndRollsBackExistingAndNewFiles() throws Exception{
        managed("src/A.txt", "old"); Files.writeString(root.resolve("src/A.txt"), "native");
        TechnologyCompilationResult generated = compilation(file("src/A.txt", "generated"), file("src/New.txt", "new"));
        RoundTripResolutionPlan unresolved = service.prepare(generated, "profile", root, List.of());
        ElementConflict conflict = unresolved.unresolved().get(0);
        RoundTripResolutionPlan plan = service.prepare(generated, "profile", root,
                List.of(new ElementConflictDecision(conflict.key(), ConflictResolutionChoice.KEEP_HYDRA, "Canonical source wins")));
        RoundTripApplyResult applied = service.apply(plan);
        assertThat(Files.readString(root.resolve("src/A.txt"))).isEqualTo("generated");
        assertThat(root.resolve("src/New.txt")).hasContent("new"); assertThat(applied.journalPath()).exists();
        RoundTripApplyResult rolledBack = service.rollback(root, plan.operationId());
        assertThat(Files.readString(root.resolve("src/A.txt"))).isEqualTo("native");
        assertThat(root.resolve("src/New.txt")).doesNotExist(); assertThat(rolledBack.rollback()).isTrue();
    }

    @Test
    void mergeSafePreservesNamedManualRegions() throws Exception{
        String oldValue = "before\n// <hydra:manual id=\"custom\">OLD// </hydra:manual>\nafter";
        String generatedValue = "generated\n// <hydra:manual id=\"custom\">NEW// </hydra:manual>\nend";
        managed("src/A.txt", "baseline"); Files.writeString(root.resolve("src/A.txt"), oldValue);
        RoundTripResolutionPlan unresolved = service.prepare(compilation(file("src/A.txt", generatedValue)), "profile", root, List.of());
        ElementConflict conflict = unresolved.unresolved().get(0);
        RoundTripResolutionPlan plan = service.prepare(compilation(file("src/A.txt", generatedValue)), "profile", root,
                List.of(new ElementConflictDecision(conflict.key(), ConflictResolutionChoice.MERGE_SAFE, "Preserve manual code")));
        service.apply(plan); String result = Files.readString(root.resolve("src/A.txt"));
        assertThat(result).contains("generated", "OLD").doesNotContain("NEW");
    }

    @Test
    void rejectsUnsafeActionPathsAtTheBoundary(){
        assertThatThrownBy(() -> new RoundTripResolutionAction("../escape", RoundTripResolutionActionKind.WRITE_HYDRA,
                "text/plain", new byte[0], ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("safe relative");
    }

    @Test void rejectsApplyWhenNativeFileChangedAfterPreview() throws Exception {
        managed("src/A.txt", "old");
        TechnologyCompilationResult generated = compilation(file("src/A.txt", "new"));
        RoundTripResolutionPlan plan = service.prepare(generated, "profile", root, List.of());
        Files.writeString(root.resolve("src/A.txt"), "modified after preview");
        assertThatThrownBy(() -> service.apply(plan)).isInstanceOf(TechnologyCompilationException.class)
                .hasMessageContaining("changed since round-trip preview");
        assertThat(Files.readString(root.resolve("src/A.txt"))).isEqualTo("modified after preview");
    }

    @Test void blocksRollbackWhenUserEditsInstalledFileAfterApply() throws Exception {
        managed("src/A.txt", "old");
        RoundTripResolutionPlan plan = service.prepare(compilation(file("src/A.txt", "new")), "profile", root, List.of());
        service.apply(plan);
        Files.writeString(root.resolve("src/A.txt"), "later user edit");
        assertThatThrownBy(() -> service.rollback(root, plan.operationId()))
                .isInstanceOf(TechnologyCompilationException.class).hasMessageContaining("changed after round-trip apply");
        assertThat(Files.readString(root.resolve("src/A.txt"))).isEqualTo("later user edit");
    }

    @Test void blocksRollbackWhenBackupIsTamperedWith() throws Exception {
        managed("src/A.txt", "old");
        RoundTripResolutionPlan plan = service.prepare(compilation(file("src/A.txt", "new")), "profile", root, List.of());
        var applied = service.apply(plan);
        Files.writeString(applied.journalPath().getParent().resolve("files/src/A.txt"), "bad backup");
        assertThatThrownBy(() -> service.rollback(root, plan.operationId()))
                .isInstanceOf(TechnologyCompilationException.class).hasMessageContaining("backup altered");
        assertThat(Files.readString(root.resolve("src/A.txt"))).isEqualTo("new");
    }

    @Test void refusesSymbolicDirectoryInRoundTripTarget() throws Exception {
        Path external = Files.createTempDirectory("hydra-rt-external-");
        try {
            Path link = root.resolve("linked");
            try {Files.createSymbolicLink(link, external);} catch(Exception unsupported) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false,"Symlinks unavailable");
            }
            assertThatThrownBy(() -> com.example.editor.integration.roundtrip.RoundTripSafePaths.resolve(root,"linked/A.txt"))
                    .isInstanceOf(TechnologyCompilationException.class).hasMessageContaining("Symbolic");
        } finally {Files.deleteIfExists(external);}
    }

    private TechnologyGeneratedFile file(String path, String value){
        return new TechnologyGeneratedFile(path, "text/plain", value.getBytes(StandardCharsets.UTF_8),
                InjectionFileOwnership.GENERATED, true, List.of("people"), Map.of());
    }
    private TechnologyCompilationResult compilation(TechnologyGeneratedFile... files){
        return new TechnologyCompilationResult(IntegrationTechnology.LANGIUM, List.of(files), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }
    private void managed(String path, String generatedValue) throws Exception{
        RoundTripElementMapping mapping = new RoundTripElementMapping("people", IntegrationArtifactKind.METAMODEL, path,
                List.of(), sha(generatedValue), InjectionFileOwnership.GENERATED, Map.of());
        RoundTripManifest manifest = new RoundTripManifest("1.0", "profile", IntegrationTechnology.LANGIUM, "test",
                Instant.EPOCH, List.of(mapping), Map.of());
        Path target = root.resolve(RoundTripSyncService.MANIFEST_PATH); Files.createDirectories(target.getParent());
        Files.write(target, mapper.writeValueAsBytes(manifest)); Path nativePath = root.resolve(path); Files.createDirectories(nativePath.getParent());
        Files.writeString(nativePath, generatedValue);
    }
    private String sha(String value) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
}

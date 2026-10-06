package com.example.editor.metamodel.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarGeneratedProjectArchiveService;
import com.example.editor.grammar.core.GeneratedGrammarProject;
import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectException;
import com.example.editor.project.storage.ProjectFileOperations;
import com.example.editor.project.storage.ProjectZipService;
import com.example.editor.standalone.distribution.StandaloneDistributionArtifact;
import com.example.editor.standalone.distribution.StandaloneDistributionDiagnostic;
import com.example.editor.standalone.distribution.StandaloneDistributionDiagnosticSeverity;
import com.example.editor.standalone.distribution.StandaloneDistributionMaterializer;
import com.example.editor.standalone.distribution.StandaloneDistributionPackageResult;
import com.example.editor.standalone.distribution.StandaloneDistributionPackager;
import com.example.editor.standalone.distribution.StandaloneDistributionPlanner;
import com.example.editor.standalone.distribution.StandaloneDistributionProfile;
import com.example.editor.standalone.distribution.StandaloneDistributionTarget;
import com.example.editor.standalone.distribution.StandaloneDistributionValidationReport;
import com.example.editor.standalone.distribution.StandaloneDistributionValidationService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Campaña agresiva que exige evidencia del artefacto final, no solo que el
 * servicio termine sin lanzar una excepción.
 */
class AggressiveArtifactGenerationTests {
    @TempDir Path temporary;

    @Test
    void metamodelBundleIsDeterministicParseableAndContainsOnlyRequestedArtifacts() throws Exception {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        MetamodelExportService service = new MetamodelExportService(id -> context, new StubEcoreAdapter(),
                List.of(new NeutralXmlMetamodelExporter(), new PlantUmlMetamodelExporter()), List.of());
        byte[] first = service.exportBundle("company", List.of("xml", "plantuml", "xml"), MetamodelExportOptions.defaults()).content();
        byte[] second = service.exportBundle("company", List.of("plantuml", "xml"), MetamodelExportOptions.defaults()).content();

        Map<String, byte[]> one = unzip(first);
        Map<String, byte[]> two = unzip(second);
        assertThat(one.keySet()).containsExactly("company.xml", "company.puml");
        assertThat(two.keySet()).containsExactly("company.xml", "company.puml");
        assertThat(second).isEqualTo(first);
        assertThat(new String(one.get("company.xml"), StandardCharsets.UTF_8)).contains("Company", "Person");
        assertThat(new String(one.get("company.puml"), StandardCharsets.UTF_8)).contains("@startuml", "Person", "Company", "@enduml");
        assertThat(two.keySet()).containsExactlyElementsOf(one.keySet());
        for (Map.Entry<String, byte[]> entry : one.entrySet()) {
            assertThat(two.get(entry.getKey())).as(entry.getKey()).containsExactly(entry.getValue());
        }
    }

    @Test
    void grammarProjectWithHundredsOfFilesProducesStablePortableZipAndRejectsTraversal() throws Exception {
        GrammarProperties properties = new GrammarProperties();
        properties.setMaximumGeneratedFiles(500);
        properties.setMaximumGeneratedBytes(4 * 1024 * 1024L);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("package.json", "{\"name\":\"aggressive\"}\n");
        for (int index = 199; index >= 0; index--) files.put("src/generated/rule-%03d.ts".formatted(index), "export const rule%d = %d;\n".formatted(index, index));
        GeneratedGrammarProject project = new GeneratedGrammarProject("aggressive-langium", files, Map.of("provider", "LANGIUM"));
        GrammarGeneratedProjectArchiveService service = new GrammarGeneratedProjectArchiveService(properties);
        byte[] first = service.archive(project); byte[] second = service.archive(project);
        assertThat(second).isEqualTo(first);
        List<String> entries = new ArrayList<>(unzip(first).keySet());
        assertThat(entries).hasSize(201).isSorted();
        assertThat(entries.getFirst()).isEqualTo("package.json");
        assertThat(entries.getLast()).isEqualTo("src/generated/rule-199.ts");
        assertThatThrownBy(() -> new GeneratedGrammarProject("hostile", Map.of("../escape.ts", "bad"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        GrammarProperties fileLimited = new GrammarProperties(); fileLimited.setMaximumGeneratedFiles(2);
        assertThatThrownBy(() -> new GrammarGeneratedProjectArchiveService(fileLimited).archive(project))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("file limit");
        GrammarProperties byteLimited = new GrammarProperties(); byteLimited.setMaximumGeneratedBytes(1024);
        GeneratedGrammarProject oversized = new GeneratedGrammarProject("oversized", Map.of("large.txt", "x".repeat(2_048)), Map.of());
        assertThatThrownBy(() -> new GrammarGeneratedProjectArchiveService(byteLimited).archive(oversized))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("byte limit");
    }

    @Test
    void projectZipRoundTripPreservesHundredsOfPortableFilesAndNeverPublishesCache() throws Exception {
        Path source = temporary.resolve("source"); Files.createDirectories(source.resolve("models"));
        Files.createDirectories(source.resolve(".modeling/cache"));
        FileTime stableTime = FileTime.fromMillis(1_700_000_000_000L);
        for (int index = 0; index < 250; index++) {
            Path file = source.resolve("models/model-%03d.json".formatted(index));
            Files.writeString(file, "{\"id\":%d,\"name\":\"Model %d\"}\n".formatted(index, index));
            Files.setLastModifiedTime(file, stableTime);
        }
        Files.writeString(source.resolve("modeling-project.json"), "{\"id\":\"aggressive\"}\n");
        Files.setLastModifiedTime(source.resolve("modeling-project.json"), stableTime);
        Files.writeString(source.resolve(".modeling/cache/secret.bin"), "must-not-leak");
        LocalProjectProperties properties = new LocalProjectProperties(); properties.setMaxArchiveEntries(1000); properties.setMaxArchiveSizeBytes(32 * 1024 * 1024L);
        ProjectZipService service = new ProjectZipService(properties, new ProjectFileOperations());
        byte[] archive = service.exportProject(source);
        Path destination = temporary.resolve("imported"); service.importProject(archive, destination);
        assertThat(destination.resolve("models/model-000.json")).hasContent("{\"id\":0,\"name\":\"Model 0\"}\n");
        assertThat(destination.resolve("models/model-249.json")).exists();
        assertThat(destination.resolve(".modeling/cache/secret.bin")).doesNotExist();
        try (var stream = Files.walk(destination)) { assertThat(stream.filter(Files::isRegularFile).count()).isEqualTo(251L); }
    }

    @Test
    void projectImportIsTransactionalUnderHostileArchiveAndLeavesExistingProductUntouched() throws Exception {
        LocalProjectProperties properties = new LocalProjectProperties();
        ProjectZipService service = new ProjectZipService(properties, new ProjectFileOperations());
        Path existing = temporary.resolve("existing"); Files.createDirectories(existing); Files.writeString(existing.resolve("keep.txt"), "keep");
        assertThatThrownBy(() -> service.importProject(zip(Map.of("../escape.txt", "bad")), existing.resolve("nested")))
                .isInstanceOf(ProjectException.class);
        assertThat(existing.resolve("keep.txt")).hasContent("keep");
        assertThat(temporary.resolve("escape.txt")).doesNotExist();
    }

    @Test
    void standaloneDistributionMaterializesValidatesAndPackagesReproduciblyWithoutTransientTrees() throws Exception {
        StandaloneDistributionProfile profile = new StandaloneDistributionProfile("Aggressive Editor", "9.9.9",
                Set.of(StandaloneDistributionTarget.PWA, StandaloneDistributionTarget.TAURI_WINDOWS), true,
                List.of(".hydra", ".xmi"), "stable");
        List<StandaloneDistributionArtifact> plan = new StandaloneDistributionPlanner().plan(profile);
        new StandaloneDistributionMaterializer().materialize(temporary, plan);
        Files.writeString(temporary.resolve("index.html"), "<main>HYDRA aggressive</main>");
        Map<String,String> transientFiles = Map.of(
                "node_modules/pkg/ignored.js", "ignored",
                "target/classes/ignored.class", "ignored",
                ".git/config", "ignored",
                ".hydra-roundtrip-backups/old.json", "ignored",
                "coverage/result.json", "{}",
                "playwright-report/index.html", "ignored",
                "test-results/results.json", "{}",
                ".vite/cache.json", "{}");
        for(var entry : transientFiles.entrySet()) {
            Path path = temporary.resolve(entry.getKey()); Files.createDirectories(path.getParent()); Files.writeString(path, entry.getValue());
        }
        StandaloneDistributionValidationReport validation = new StandaloneDistributionValidationService(JsonMapper.builder().build()).validate(temporary, profile);
        assertThat(validation.valid()).isTrue();
        StandaloneDistributionPackager packager = new StandaloneDistributionPackager();
        StandaloneDistributionPackageResult first = packager.packageProject(temporary, temporary.getParent().resolve("aggressive-one.zip"), validation);
        StandaloneDistributionPackageResult second = packager.packageProject(temporary, temporary.getParent().resolve("aggressive-two.zip"), validation);
        assertThat(first.sha256()).isEqualTo(second.sha256());
        assertThat(first.entries()).contains("index.html", "public/manifest.webmanifest", "src-tauri/tauri.conf.json");
        assertThat(first.entries()).noneMatch(path -> transientFiles.keySet().stream().anyMatch(path::equals));
        assertThat(first.entries()).noneMatch(path -> path.startsWith("node_modules/") || path.startsWith("target/") || path.startsWith(".git/")
                || path.startsWith(".hydra-roundtrip-backups/") || path.startsWith("coverage/") || path.startsWith("playwright-report/")
                || path.startsWith("test-results/") || path.startsWith(".vite/"));
    }

    @Test
    void standalonePackagerRefusesToTurnAnInvalidPreparationIntoAFakeArtifact() {
        StandaloneDistributionValidationReport invalid = new StandaloneDistributionValidationReport(false,
                List.of(new StandaloneDistributionDiagnostic("INVALID", StandaloneDistributionDiagnosticSeverity.ERROR, "index.html", "missing")), Map.of());
        assertThatThrownBy(() -> new StandaloneDistributionPackager().packageProject(temporary, temporary.resolve("invalid.zip"), invalid))
                .isInstanceOf(IllegalStateException.class);
        assertThat(temporary.resolve("invalid.zip")).doesNotExist();
    }

    private Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory()) entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static final class StubEcoreAdapter implements EcoreMetamodelAdapter {
        @Override public TransformationResult<EPackage> toEPackage(MetaModel metamodel) { throw new UnsupportedOperationException(); }
        @Override public TransformationResult<byte[]> toBytes(MetaModel metamodel) { return new TransformationResult<>("<ecore/>".getBytes(StandardCharsets.UTF_8), List.of()); }
        @Override public TransformationResult<MetaModel> fromPath(Path path) { throw new UnsupportedOperationException(); }
        @Override public TransformationResult<MetaModel> fromEPackage(EPackage ePackage, String artifactName) { throw new UnsupportedOperationException(); }
    }
}

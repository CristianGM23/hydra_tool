package com.example.editor.standalone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.grammar.core.CompilationStatus;
import com.example.editor.grammar.core.GeneratedGrammarProject;
import com.example.editor.grammar.core.GrammarRuntimeDescriptor;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.archive.CapabilityPackageArchiveProperties;
import com.example.editor.portability.archive.CapabilityPackageArchiveService;
import com.example.editor.portability.archive.CapabilityPackageDependencyResolver;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class StandaloneEditorGeneratorServiceTests {
    @TempDir Path directory;
    private StandaloneEditorGenerationProperties properties;
    private StandaloneEditorGeneratorService service;

    @BeforeEach void setUp() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var archiveProperties = new CapabilityPackageArchiveProperties();
        archiveProperties.setCacheDirectory(directory.resolve("cache"));
        var archive = new CapabilityPackageArchiveService(archiveProperties,
                new CapabilityPackageChecker(), new CapabilityPackageDependencyResolver(), mapper);
        properties = new StandaloneEditorGenerationProperties();
        service = new StandaloneEditorGeneratorService(properties, archive, mapper);
    }

    @Test void generatesAStandaloneJavaServerAndWebEditor() {
        assertThat(entries(generate(true).archive())).containsKeys("backend/src/HydraStandaloneServer.java",
                "frontend/index.html", "frontend/app.js", "frontend/runtime.js", "frontend/visual-runtime.js",
                "frontend/config/capabilities.json", "frontend/styles.css", "build.cmd", "run.cmd");
    }

    @Test void embedsTheVerifiableCapabilityPackage() {
        assertThat(entries(generate(true).archive()).keySet()).anyMatch(path -> path.endsWith(".hydra-cap"));
    }

    @Test void canReferencePackageWithoutEmbeddingIt() {
        assertThat(entries(generate(false).archive()).keySet()).noneMatch(path -> path.endsWith(".hydra-cap"));
    }

    @Test void outputIsByteForByteDeterministic() {
        assertThat(generate(true).archive()).isEqualTo(generate(true).archive());
    }

    @Test void descriptorUsesEmbeddedLocalRuntimeAndCanonicalDocuments() {
        var result = generate(true);
        assertThat(result.descriptor().runtimeMode()).isEqualTo("EMBEDDED_LOCAL");
        assertThat(result.descriptor().port()).isEqualTo(4173);
        assertThat(result.descriptor().metamodelDocumentPath()).contains("metamodel.json");
        assertThat(result.descriptor().editorDocumentPath()).contains("editor.json");
    }

    @Test void generatedApplicationDoesNotContainTheHydraDesigner() {
        String source = text(entries(generate(true).archive()).get("frontend/app.js"));
        assertThat(source).doesNotContain("MetamodelDesigner", "EventDesigner", "GamificationDesigner");
    }

    @Test void generatedApplicationSupportsCreationEditingRepresentationsAndPersistence() {
        Map<String, byte[]> generated = entries(generate(true).archive());
        String source = text(generated.get("frontend/app.js"));
        String page = text(generated.get("frontend/index.html"));
        String styles = text(generated.get("frontend/styles.css"));
        assertThat(source).contains("runTool", "renderTree", "renderTable", "renderMatrix",
                "renderDashboard", "renderDiagramElement", "containmentRelationFor", "assetUrl", "nodeMappingFor(item.classId)", "semanticEdges", "saveModel", "If-Match");
        assertThat(source).containsPattern("filter\\((\\w+)=>\\1&&nodeMappingFor\\(\\1\\.classId\\)\\)");
        assertThat(styles).contains(".diagram-edge-layer", "z-index:2", ".node{", "z-index:3", ".node.container{z-index:auto}");
        assertThat(page).contains("Download JSON");
    }

    @Test void generatedPortableRuntimeExecutesValidationEventsFlowsAndGamification() {
        Map<String, byte[]> generated = entries(generate(true).archive());
        String runtime = text(generated.get("frontend/runtime.js"));
        String application = text(generated.get("frontend/app.js"));
        assertThat(runtime).contains("function validate", "function dispatch", "processFlows",
                "processGamification", "applyQuickFix", "invokeOperation");
        assertThat(application).contains("HydraGeneratedRuntime", "core.model.saved",
                "core.model.exported", "applyQuickFix", "renderRuntimePanels");
    }

    @Test void generatedVisualRuntimeExecutesConditionalAppearanceWithBoundedFailSafeResolution() {
        Map<String, byte[]> generated = entries(generate(true).archive());
        String runtime = text(generated.get("frontend/visual-runtime.js"));
        String application = text(generated.get("frontend/app.js"));
        assertThat(runtime).contains("maximumElements:100000", "maximumConditionalStyles:64",
                "resolveNodeAppearance", "resolveEdgeAppearance", "VISUAL_CONDITION_EVALUATION_FAILED",
                "VISUAL_MODEL_LIMIT_REACHED", "applyModel");
        assertThat(application).contains("HydraVisualAppearanceRuntime", "resolveNodeAppearance",
                "resolveEdgeAppearance");
    }

    @Test void dockerTargetAddsBuildAndComposeFilesAndExposesTheGeneratedEditor() {
        var result = service.generate(request(true, packageValue(), true,
                Set.of(StandaloneDeploymentTarget.DOCKER)));
        Map<String, byte[]> generated = entries(result.archive());
        assertThat(result.fileName()).endsWith("-docker.zip");
        assertThat(generated).containsKeys("Dockerfile", "docker-compose.yml",
                ".dockerignore", "CONTAINER_README.md");
        assertThat(text(generated.get("Dockerfile"))).contains("eclipse-temurin:21-jdk",
                "--host=0.0.0.0", "EXPOSE 4173", "/opt/hydra-editor/models");
        assertThat(text(generated.get("docker-compose.yml"))).contains("4173:4173",
                "./models:/opt/hydra-editor/models");
    }

    @Test void kubernetesTargetAddsDeploymentServiceAndPersistentStorage() {
        var result = service.generate(request(true, packageValue(), true,
                Set.of(StandaloneDeploymentTarget.KUBERNETES)));
        Map<String, byte[]> generated = entries(result.archive());
        assertThat(result.fileName()).endsWith("-kubernetes.zip");
        assertThat(generated).containsKeys("Dockerfile", "deploy/kubernetes/deployment.yaml",
                "deploy/kubernetes/service.yaml", "deploy/kubernetes/pvc.yaml");
        assertThat(text(generated.get("deploy/kubernetes/deployment.yaml")))
                .contains("readinessProbe", "/api/health", "persistentVolumeClaim");
        assertThat(text(generated.get("deploy/kubernetes/service.yaml")))
                .contains("kind: Service", "targetPort: 4173");
    }

    @Test void generatedServerKeepsLoopbackByDefaultAndAllowsAnExplicitContainerHost() {
        String source = text(entries(generate(true).archive()).get("backend/src/HydraStandaloneServer.java"));
        assertThat(source).contains("String host = \"127.0.0.1\"", "--host=",
                "new InetSocketAddress(host, port)");
    }

    @Test void generatedServerUsesAtomicPersistenceEtagsAndLimits() {
        String source = text(entries(generate(true).archive()).get("backend/src/HydraStandaloneServer.java"));
        assertThat(source).contains("ATOMIC_MOVE", "If-Match", "MAXIMUM_MODEL_BYTES",
                "X-Content-Type-Options", "safeId", "Files.newInputStream(file)", "input.transferTo(output)");
        assertThat(source).doesNotContain("Files.readAllBytes");
    }

    @Test void generatedJavaServerCompilesWithoutThirdPartyDependencies() throws Exception {
        Map<String, byte[]> generated = entries(generate(true).archive());
        Path source = directory.resolve("HydraStandaloneServer.java");
        Path output = directory.resolve("classes");
        Files.createDirectories(output);
        Files.write(source, generated.get("backend/src/HydraStandaloneServer.java"));
        ToolProvider compiler = ToolProvider.findFirst("javac").orElseThrow();
        assertThat(compiler.run(System.out, System.err, "--release", "21", "-d",
                output.toString(), source.toString())).isZero();
        assertThat(output.resolve("HydraStandaloneServer.class")).isRegularFile();
    }

    @Test void includesCanonicalDocumentsAndResourcesUnderSafePaths() {
        Map<String, byte[]> generated = entries(generate(true).archive());
        assertThat(generated).containsKeys("frontend/config/canonical/metamodel.json",
                "frontend/config/canonical/editor.json", "frontend/resources/icons/person.svg");
    }

    @Test void decodesPortableVisualDataUrlsIntoBinaryStandaloneResources() {
        HydraCapabilityPackage value = packageValue(Map.of(), Map.of(
                "hydra-assets/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.png",
                "data:image/png;base64,AQIDBA=="));
        Map<String, byte[]> generated = entries(service.generate(request(true, value, true)).archive());
        assertThat(generated.get("frontend/resources/hydra-assets/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.png"))
                .containsExactly((byte)1, (byte)2, (byte)3, (byte)4);
    }

    @Test void canLimitCanonicalDocumentsToTheRequiredPair() {
        HydraCapabilityPackage value = packageValue(Map.of("extra.json", JsonNodeFactory.instance.objectNode().put("id", "extra")), Map.of());
        StandaloneEditorGenerationRequest request = request(true, value, false);
        assertThat(entries(service.generate(request).archive())).doesNotContainKey("frontend/config/canonical/extra.json");
    }

    @Test void rejectsMissingPackage() {
        assertThatThrownBy(() -> service.generate(new StandaloneEditorGenerationRequest("people-app", "People", "people", "people-editor", 4173, "EMBEDDED_LOCAL", "", true, true, 10L * 1024L * 1024L, List.of(), List.of(), Map.of(), null, Set.of(StandaloneDeploymentTarget.SOURCE))))
                .isInstanceOf(StandaloneEditorGenerationException.class);
    }

    @Test void rejectsMismatchedMetamodel() {
        var request = new StandaloneEditorGenerationRequest("people-app", "People", "other", "people-editor", 4173, "EMBEDDED_LOCAL", "", true, true, 10L * 1024L * 1024L, List.of(), List.of(), Map.of(), packageValue(), Set.of(StandaloneDeploymentTarget.SOURCE));
        assertThatThrownBy(() -> service.generate(request)).hasMessageContaining("another metamodel");
    }

    @Test void rejectsPackageWithoutMetamodelCapability() {
        HydraCapabilityPackage value = packageValue();
        HydraCapabilityPackageManifest manifest = value.manifest();
        HydraCapabilityPackage invalid = new HydraCapabilityPackage(new HydraCapabilityPackageManifest(
                manifest.schemaVersion(), manifest.id(), manifest.name(), manifest.packageVersion(),
                manifest.metamodelId(), manifest.metamodelOrigin(), manifest.capabilities().stream()
                        .filter(item -> item.kind() != HydraCapabilityKind.METAMODEL).toList(),
                List.of(), manifest.compatibility(), manifest.checksums(),
                manifest.annotations()), value.canonicalDocuments(), value.resources());
        assertThatThrownBy(() -> service.generate(request(true, invalid, true)))
                .hasMessageContaining("METAMODEL");
    }

    @Test void rejectsUnsafeResourcePath() {
        HydraCapabilityPackage invalid = packageValue(Map.of(), Map.of("../secret.txt", "secret"));
        assertThatThrownBy(() -> service.generate(request(true, invalid, true)))
                .hasMessageContaining("Unsafe archive entry path");
    }

    @Test void sanitizesApplicationIdentifier() {
        var request = new StandaloneEditorGenerationRequest("People editor / demo", "People", "people", "people-editor", 4173, "EMBEDDED_LOCAL", "", true, true, 10L * 1024L * 1024L, List.of(), List.of(), Map.of(), packageValue(), Set.of(StandaloneDeploymentTarget.SOURCE));
        assertThat(service.generate(request).descriptor().applicationId()).isEqualTo("People-editor-demo");
    }

    @Test void respectsCustomRuntimeBaseUrlAndPort() {
        var request = new StandaloneEditorGenerationRequest("people-app", "People", "people", "people-editor", 5090, "HYBRID", "http://runtime:9090", true, true, 1024 * 1024, List.of("main"), List.of("operation:query"), Map.of(), packageValue(), Set.of(StandaloneDeploymentTarget.SOURCE));
        var descriptor = service.generate(request).descriptor();
        assertThat(descriptor.runtimeBaseUrl()).isEqualTo("http://runtime:9090");
        assertThat(descriptor.port()).isEqualTo(5090);
        assertThat(descriptor.runtimeMode()).isEqualTo("HYBRID");
    }

    @Test void textualStandaloneWithoutSnapshotIsAnHonestBuildRequiredSourceDistribution() {
        HydraCapabilityPackage value = textualPackageValue();
        var result = service.generate(textualRequest(value, null));
        Map<String, byte[]> generated = entries(result.archive());
        assertThat(result.descriptor().settings()).containsEntry("editorKind", "TEXTUAL")
                .containsEntry("textualRuntimeAvailability", "BUILD_REQUIRED")
                .containsEntry("compilationSnapshotId", "snapshot-1");
        assertThat(generated).containsKeys("frontend/config/canonical/grammar.json",
                "frontend/config/canonical/textual-editor.json",
                "frontend/config/canonical/textual-style.json", "frontend/app.js");
        assertThat(text(generated.get("frontend/app.js"))).contains("HYDRA_TEXTUAL_DRAFT",
                "DRAFT_ONLY", "BUNDLED_BROWSER_RUNTIME", "publishDiagnostics");
        assertThat(text(generated.get("README.md"))).contains("BUILD_REQUIRED",
                "no se presenta como modelo semántico validado");
        assertThat(generated).doesNotContainKey("textual-runtime/snapshot.json");
    }


    @Test void textualStandaloneDoesNotRequireAnArtificialMetamodel() {
        HydraCapabilityPackage value = grammarFirstTextualPackageValue();
        StandaloneEditorGenerationRequest request = new StandaloneEditorGenerationRequest("grammar-only-app", "Grammar only", "",
                "people-text-editor", 4173, "EMBEDDED_LOCAL", "", true, false, 10L * 1024L * 1024L,
                List.of(), List.of(), Map.of(), value, Set.of(StandaloneDeploymentTarget.SOURCE), null);
        var result = service.generate(request);
        Map<String, byte[]> generated = entries(result.archive());
        assertThat(result.descriptor().metamodelId()).isBlank();
        assertThat(result.descriptor().semanticScopeId()).isEqualTo("people-grammar");
        assertThat(result.descriptor().metamodelDocumentPath()).isBlank();
        assertThat(generated).containsKeys("frontend/config/canonical/grammar.json", "frontend/config/canonical/textual-editor.json");
        assertThat(generated).doesNotContainKey("frontend/config/canonical/metamodel.json");
    }

    @Test void textualStandalonePinsAndPackagesTheExactSuccessfulSnapshotSources() {
        HydraCapabilityPackage value = textualPackageValue();
        GrammarCompilationSnapshot snapshot = textualSnapshot(Map.of(),
                new GrammarRuntimeDescriptor("LANGUAGE_SERVER", "Model", List.of(".people"),
                        "node dist/language/main.js", "", Map.of()));
        var result = service.generate(textualRequest(value, snapshot));
        Map<String, byte[]> generated = entries(result.archive());
        assertThat(result.descriptor().settings()).containsEntry("textualRuntimeAvailability",
                "LANGUAGE_SERVER_HOST_REQUIRED");
        assertThat(generated).containsKeys("textual-runtime/snapshot.json",
                "textual-runtime/source/package.json", "textual-runtime/source/src/main.ts");
        assertThat(text(generated.get("README.md"))).contains("LANGUAGE_SERVER_HOST_REQUIRED",
                "el host LSP/JVM/Node debe proporcionarse fuera del navegador");
        assertThat(generated).doesNotContainKey("CONTAINER_README.md");
    }

    @Test void textualStandaloneIncludesOnlyAActuallyMaterializedBrowserRuntimeAsBundled() {
        HydraCapabilityPackage value = textualPackageValue();
        String worker = "dist-browser/hydra-language-worker.js";
        GrammarCompilationSnapshot snapshot = textualSnapshot(Map.of(worker, "self.onmessage=()=>{};"),
                new GrammarRuntimeDescriptor("BROWSER_WORKER", "Model", List.of(".people"),
                        "", "./" + worker, Map.of()));
        var result = service.generate(textualRequest(value, snapshot));
        Map<String, byte[]> generated = entries(result.archive());
        assertThat(result.descriptor().settings()).containsEntry("textualRuntimeAvailability",
                "BUNDLED_BROWSER_RUNTIME")
                .containsEntry("browserWorkerEntry", "frontend/textual-runtime/" + worker);
        assertThat(generated).containsKey("frontend/textual-runtime/" + worker);
        assertThat(text(generated.get("frontend/textual-runtime/" + worker)))
                .isEqualTo("self.onmessage=()=>{};");
    }

    @Test void textualStandaloneRejectsFailedStaleOrCrossGrammarSnapshots() {
        HydraCapabilityPackage value = textualPackageValue();
        GrammarCompilationSnapshot failed = snapshot("snapshot-1", "people-grammar", CompilationStatus.FAILED,
                Map.of(), new GrammarRuntimeDescriptor("", "Model", List.of(), "", "", Map.of()));
        assertThatThrownBy(() -> service.generate(textualRequest(value, failed)))
                .hasMessageContaining("successful grammar snapshot");
        GrammarCompilationSnapshot stale = snapshot("snapshot-old", "people-grammar", CompilationStatus.SUCCESS,
                Map.of(), new GrammarRuntimeDescriptor("", "Model", List.of(), "", "", Map.of()));
        assertThatThrownBy(() -> service.generate(textualRequest(value, stale)))
                .hasMessageContaining("pinned to another grammar snapshot");
        GrammarCompilationSnapshot cross = snapshot("snapshot-1", "other-grammar", CompilationStatus.SUCCESS,
                Map.of(), new GrammarRuntimeDescriptor("", "Model", List.of(), "", "", Map.of()));
        assertThatThrownBy(() -> service.generate(textualRequest(value, cross)))
                .hasMessageContaining("different grammars");
    }

    @Test void textualStandaloneRejectsMissingReferencedGrammarOrSyntaxStyle() {
        HydraCapabilityPackage value = textualPackageValue();
        HydraCapabilityPackageManifest manifest = value.manifest();
        HydraCapabilityPackage noGrammar = new HydraCapabilityPackage(new HydraCapabilityPackageManifest(
                manifest.schemaVersion(), manifest.id(), manifest.name(), manifest.packageVersion(),
                manifest.metamodelId(), manifest.metamodelOrigin(), manifest.capabilities().stream()
                        .filter(item -> item.kind() != HydraCapabilityKind.GRAMMAR).toList(),
                List.of(), manifest.compatibility(), manifest.checksums(), manifest.annotations()),
                value.canonicalDocuments(), value.resources());
        assertThatThrownBy(() -> service.generate(textualRequest(noGrammar, null)))
                .hasMessageContaining("referenced GRAMMAR");

        Map<String, tools.jackson.databind.JsonNode> docs = new LinkedHashMap<>(value.canonicalDocuments());
        ((ObjectNode)docs.get("textual-editor.json")).put("syntaxStyleDefinitionId", "missing-style");
        HydraCapabilityPackage missingStyle = new HydraCapabilityPackage(manifest, docs, value.resources());
        assertThatThrownBy(() -> service.generate(textualRequest(missingStyle, null)))
                .hasMessageContaining("missing syntax style");
    }

    @Test void canBeDisabledByConfiguration() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> generate(true)).isInstanceOf(StandaloneEditorGenerationException.class)
                .hasMessageContaining("disabled");
    }

    @Test void enforcesArchiveSizeLimit() {
        properties.setMaximumArchiveBytes(100);
        assertThatThrownBy(() -> generate(true)).isInstanceOf(StandaloneEditorGenerationException.class)
                .hasMessageContaining("limit");
    }

    @Test void recordsEveryGeneratedFileInStableOrder() {
        var result = generate(true);
        assertThat(result.generatedFiles()).containsExactlyElementsOf(result.generatedFiles().stream().sorted().toList());
        assertThat(result.generatedFiles()).contains("standalone-editor.json", "README.md",
                "models/.gitkeep");
    }

    private StandaloneEditorGenerationRequest textualRequest(HydraCapabilityPackage value, GrammarCompilationSnapshot snapshot) {
        return new StandaloneEditorGenerationRequest("people-text-app", "People textual editor", "people",
                "people-text-editor", 4173, "EMBEDDED_LOCAL", "", true, false, 10L * 1024L * 1024L,
                List.of(), List.of(), Map.of(), value, Set.of(StandaloneDeploymentTarget.SOURCE), snapshot);
    }

    private HydraCapabilityPackage textualPackageValue() {
        var metamodel = new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL,
                "metamodel.json", "2.1", true, false, Map.of());
        var grammar = new CapabilitySelection("grammar", HydraCapabilityKind.GRAMMAR,
                "grammar.json", "1.0", true, false, Map.of());
        var editor = new CapabilitySelection("textual-editor", HydraCapabilityKind.TEXTUAL_EDITOR_DEFINITION,
                "textual-editor.json", "1.0", true, false, Map.of());
        var style = new CapabilitySelection("textual-style", HydraCapabilityKind.TEXTUAL_SYNTAX_STYLE,
                "textual-style.json", "1.0", true, false, Map.of());
        var manifest = new HydraCapabilityPackageManifest("1.0", "people-textual-package",
                "People textual", "1.0.0", "people", new MetamodelOrigin(MetamodelOriginKind.OWNED,
                        null, "", "", "", "", "", Map.of(), Map.of()),
                List.of(metamodel, grammar, editor, style), List.of(), List.of(), Map.of(), Map.of());
        Map<String, tools.jackson.databind.JsonNode> documents = new LinkedHashMap<>();
        ObjectNode metamodelDocument = JsonNodeFactory.instance.objectNode();
        metamodelDocument.put("schemaVersion", "2.1");
        metamodelDocument.put("id", "people");
        metamodelDocument.putArray("classes");
        metamodelDocument.putArray("relations");
        ObjectNode grammarDocument = JsonNodeFactory.instance.objectNode();
        grammarDocument.put("schemaVersion", "1.0");
        grammarDocument.put("id", "people-grammar");
        grammarDocument.put("technology", "LANGIUM");
        ObjectNode editorDocument = JsonNodeFactory.instance.objectNode();
        editorDocument.put("schemaVersion", "1.0");
        editorDocument.put("id", "people-text-editor");
        editorDocument.put("grammarId", "people-grammar");
        editorDocument.put("compilationSnapshotId", "snapshot-1");
        editorDocument.put("syntaxStyleDefinitionId", "people-style");
        editorDocument.put("runtimeMode", "AUTO");
        ObjectNode styleDocument = JsonNodeFactory.instance.objectNode();
        styleDocument.put("schemaVersion", "1.0");
        styleDocument.put("id", "people-style");
        documents.put("metamodel.json", metamodelDocument);
        documents.put("grammar.json", grammarDocument);
        documents.put("textual-editor.json", editorDocument);
        documents.put("textual-style.json", styleDocument);
        return new HydraCapabilityPackage(manifest, documents, Map.of());
    }


    private HydraCapabilityPackage grammarFirstTextualPackageValue() {
        HydraCapabilityPackage original = textualPackageValue();
        var manifest = new HydraCapabilityPackageManifest("1.0", "people-grammar-only-package", "People grammar only", "1.0.0", "", "people-grammar",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                original.manifest().capabilities().stream().filter(value -> value.kind() != HydraCapabilityKind.METAMODEL).toList(),
                List.of(), List.of(), Map.of(), Map.of());
        Map<String, tools.jackson.databind.JsonNode> documents = new LinkedHashMap<>(original.canonicalDocuments());
        documents.remove("metamodel.json");
        return new HydraCapabilityPackage(manifest, documents, original.resources());
    }

    private GrammarCompilationSnapshot textualSnapshot(Map<String,String> runtimeFiles, GrammarRuntimeDescriptor descriptor) {
        return snapshot("snapshot-1", "people-grammar", CompilationStatus.SUCCESS, runtimeFiles, descriptor);
    }

    private GrammarCompilationSnapshot snapshot(String id, String grammarId, CompilationStatus status,
            Map<String,String> runtimeFiles, GrammarRuntimeDescriptor descriptor) {
        return new GrammarCompilationSnapshot("1.0", id, grammarId, 7L, "source-hash",
                GrammarTechnology.LANGIUM, "langium", "4.3.0", "langium-4.3-browser", "profile-hash",
                "people", "\"metamodel-etag\"", status, List.of(), GrammarSemanticBindingMap.empty(), descriptor,
                runtimeFiles, new GeneratedGrammarProject("people-grammar-langium", Map.of(
                        "package.json", "{\"scripts\":{\"build\":\"langium generate && tsc\"}}",
                        "src/main.ts", "export const grammar = true;"), Map.of("deterministic", "true")),
                List.of(), Map.of("package.json", "hash-package"), List.of(),
                Instant.parse("2026-09-06T12:00:00Z"), Map.of("immutable", "true"));
    }

    private StandaloneEditorGenerationResult generate(boolean include) {
        return service.generate(request(include, packageValue(), true));
    }

    private StandaloneEditorGenerationRequest request(boolean include, HydraCapabilityPackage value,
            boolean includeAll) {
        return request(include, value, includeAll, Set.of(StandaloneDeploymentTarget.SOURCE));
    }

    private StandaloneEditorGenerationRequest request(boolean include, HydraCapabilityPackage value,
            boolean includeAll, Set<StandaloneDeploymentTarget> targets) {
        return new StandaloneEditorGenerationRequest("people-app", "People editor", "people", "people-editor", 4173, "EMBEDDED_LOCAL", "", include, includeAll, 10L * 1024L * 1024L, List.of(), List.of(), Map.of(), value, targets);
    }

    private HydraCapabilityPackage packageValue() { return packageValue(Map.of(), Map.of()); }

    private HydraCapabilityPackage packageValue(Map<String, tools.jackson.databind.JsonNode> extra,
            Map<String, String> additionalResources) {
        var metamodel = new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL,
                "metamodel.json", "2.1", true, false, Map.of());
        var editor = new CapabilitySelection("editor", HydraCapabilityKind.VISUAL_REPRESENTATION,
                "editor.json", "1.0", true, true, Map.of());
        var manifest = new HydraCapabilityPackageManifest("1.0", "people-editor-package",
                "People editor", "1.0.0", "people", new MetamodelOrigin(MetamodelOriginKind.OWNED,
                        null, "", "", "", "", "", Map.of(), Map.of()),
                List.of(metamodel, editor), List.of(new CapabilityDependency("editor", "metamodel",
                        true, ">=2.1", "The editor resolves metamodel types.")),
                List.of(), Map.of(), Map.of());
        Map<String, tools.jackson.databind.JsonNode> documents = new LinkedHashMap<>();
        ObjectNode metamodelDocument = JsonNodeFactory.instance.objectNode();
        metamodelDocument.put("schemaVersion", "2.1");
        metamodelDocument.put("id", "people");
        metamodelDocument.putArray("classes");
        metamodelDocument.putArray("relations");
        ObjectNode editorDocument = JsonNodeFactory.instance.objectNode();
        editorDocument.put("schemaVersion", "1.0");
        editorDocument.put("id", "people-editor");
        editorDocument.putObject("representationsById");
        documents.put("metamodel.json", metamodelDocument);
        documents.put("editor.json", editorDocument);
        documents.putAll(extra);
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("icons/person.svg", "<svg/>");
        resources.putAll(additionalResources);
        return new HydraCapabilityPackage(manifest, documents, resources);
    }

    private Map<String, byte[]> entries(byte[] archive) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try(var input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry = input.getNextEntry()) != null) if(!entry.isDirectory())
                result.put(entry.getName(), input.readAllBytes());
            return result;
        } catch(Exception exception) { throw new AssertionError(exception); }
    }

    private String text(byte[] value) { return new String(value, StandardCharsets.UTF_8); }
}

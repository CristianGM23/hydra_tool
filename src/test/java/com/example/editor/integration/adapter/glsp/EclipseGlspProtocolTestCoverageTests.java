package com.example.editor.integration.adapter.glsp;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.verification.EclipseGlspProjectStructureValidator;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;

/** The GLSP client-server round trip is certified by a generated protocol test that the native steps run; HYDRA checks it is really generated. */
class EclipseGlspProtocolTestCoverageTests {
    private static final String JAVA_TEST = "server/src/test/java/com/generated/people/glsp/HydraGlspServerIntegrationTest.java";
    private static final String NODE_TEST = "server/src/hydra-client-server.test.ts";
    private final EclipseGlspTechnologyAdapter adapter = new EclipseGlspTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
    private final EclipseGlspProjectStructureValidator validator = new EclipseGlspProjectStructureValidator();

    @Test void javaServerShipsAProtocolTestWhoseFixtureFollowsTheEditorMappings(){
        TechnologyCompilationResult result = adapter.compile(TechnologyAdapterTestFixtures.glspRequest());
        assertThat(text(result, JAVA_TEST)).contains("new ServerModule().configureDiagramModule(new HydraDiagramModule())", "RequestModelAction", "ComputedBoundsAction",
                        "RequestMarkersAction", "SaveModelAction", "DIAGRAM_TYPE = \"people-editor\"",
                        "EXPECTED_NODES = List.of(List.of(\"hydra-it-0-a\", \"Person\"), List.of(\"hydra-it-0-b\", \"Person\"))",
                        "EXPECTED_EDGES = List.of(List.of(\"hydra-it-edge\", \"hydra-it-0-a\", \"hydra-it-0-b\"))",
                        "\\\"classId\\\":\\\"hydra:it:unmapped\\\"", "\\\"id\\\":\\\"hydra-it-hidden-target\\\"", "\\\"id\\\":\\\"hydra-it-unmapped-relation\\\"")
                .doesNotContain("EXPECTED_NODES = List.of(List.of(\"hydra-it-unmapped\"");
        assertThat(text(result, "server/pom.xml")).contains("<artifactId>junit-jupiter</artifactId><version>6.0.3</version><scope>test</scope>",
                "<artifactId>maven-surefire-plugin</artifactId><version>3.5.6</version>",
                // org.eclipse.glsp.server logs through the log4j API without declaring it: without it the server cannot even be injected.
                "<groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>2.25.5</version>");
        assertThat(text(result, "server/offline/maven-runtime-bundle.json")).contains("org.apache.logging.log4j:log4j-api:2.25.5");
        assertThat(result.dependencies()).extracting(value -> value.coordinate()).contains("org.apache.logging.log4j:log4j-api");
        assertThat(result.verificationSteps().get(2).description()).contains("GLSP server protocol API");
    }

    @Test void typeScriptServerShipsASocketRoundTripWithTheOfficialGlspClient(){
        TechnologyCompilationResult result = adapter.compile(typescriptServer());
        assertThat(text(result, NODE_TEST)).contains("new BaseJsonrpcGLSPClient(", "app.resolve(SocketServerLauncher)", "launcher.listening",
                "'hydra.replaceSourceModel'", "'glspUndo'", "'glspRedo'", "SaveModelAction.create()", "const DIAGRAM_TYPE = \"people-editor\";",
                "const EXPECTED_NODES: string[][] = [[\"hydra-it-0-a\",\"Person\"],[\"hydra-it-0-b\",\"Person\"]];",
                "const EXPECTED_EDGES: string[][] = [[\"hydra-it-edge\",\"hydra-it-0-a\",\"hydra-it-0-b\"]];",
                "const EXPECTED_UPDATED_NODES: string[][] = [[\"hydra-it-0-a\",\"Person\"]];",
                "const EXPECTED_UPDATED_EDGES: string[][] = [];", "\"name\":\"hydra-it-updated\"");
        assertThat(text(result, "server/package.json")).contains("\"vscode-jsonrpc\":\"8.2.0\"", "\"check\":\"npm run build && npm test\"");
        assertThat(result.verificationSteps()).last().satisfies(step -> assertThat(step.description()).contains("client-server protocol round trip"));
    }

    @Test void readmeStatesWhatTheVerificationCoversAndWhatIsStillPending(){
        assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()), "README.md")).contains("## Verification coverage", "HydraGlspServerIntegrationTest",
                "not the TypeScript client over a socket", "HydraOperationHandler` is a service API", "pass-through extension hook");
        assertThat(text(adapter.compile(typescriptServer()), "README.md")).contains("hydra-client-server.test.ts", "official GLSP JSON-RPC client",
                "binds no GLSP model validator", "pass-through extension hook");
        assertThat(text(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()), "client/src/hydra-validation.ts")).contains("pending coverage");
    }

    @Test void validatorAcceptsBothGeneratedServers(@TempDir Path work) throws IOException {
        assertThat(validate(materialize(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()), work.resolve("java")))).allSatisfy(step -> assertThat(step.status()).isEqualTo(TechnologyValidationStepStatus.PASSED));
        assertThat(validate(materialize(adapter.compile(typescriptServer()), work.resolve("node")))).allSatisfy(step -> assertThat(step.status()).isEqualTo(TechnologyValidationStepStatus.PASSED));
    }

    @Test void validatorRejectsAJavaServerWithoutItsProtocolTest(@TempDir Path work) throws IOException {
        Path root = materialize(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()), work);
        Files.delete(only(root, JAVA_TEST));
        assertThat(protocolStep(validate(root)).status()).isEqualTo(TechnologyValidationStepStatus.FAILED);
    }

    @Test void validatorRejectsAJavaServerThatCannotRunJunit(@TempDir Path work) throws IOException {
        Path root = materialize(adapter.compile(TechnologyAdapterTestFixtures.glspRequest()), work);
        Path pom = only(root, "server/pom.xml");
        Files.writeString(pom, Files.readString(pom).replace("<artifactId>junit-jupiter</artifactId>", "<artifactId>junit-removed</artifactId>"));
        assertThat(protocolStep(validate(root)).status()).isEqualTo(TechnologyValidationStepStatus.FAILED);
    }

    @Test void validatorRejectsATypeScriptServerWithoutItsRoundTripOrWhoseCheckSkipsTests(@TempDir Path work) throws IOException {
        Path missing = materialize(adapter.compile(typescriptServer()), work.resolve("missing"));
        Files.delete(only(missing, NODE_TEST));
        assertThat(protocolStep(validate(missing)).status()).isEqualTo(TechnologyValidationStepStatus.FAILED);
        Path skipped = materialize(adapter.compile(typescriptServer()), work.resolve("skipped"));
        Path pkg = only(skipped, "server/package.json");
        Files.writeString(pkg, Files.readString(pkg).replace("\"check\":\"npm run build && npm test\"", "\"check\":\"npm run build\""));
        assertThat(protocolStep(validate(skipped)).status()).isEqualTo(TechnologyValidationStepStatus.FAILED);
    }

    private List<TechnologyValidationStepResult> validate(Path root){return validator.validate(root, null);}
    private static TechnologyValidationStepResult protocolStep(List<TechnologyValidationStepResult> steps){
        return steps.stream().filter(step -> step.id().equals("glsp-protocol-test")).findFirst().orElseThrow();
    }
    private static Path materialize(TechnologyCompilationResult result, Path root) throws IOException {
        for(var file : result.files()){
            Path target = root.resolve(file.relativePath());
            Files.createDirectories(target.getParent());
            Files.write(target, file.content());
        }
        return root;
    }
    private static Path only(Path root, String suffix) throws IOException {
        try(var stream = Files.walk(root)){
            return stream.filter(path -> path.toString().replace('\\', '/').endsWith("/" + suffix)).findFirst().orElseThrow();
        }
    }
    private static String text(TechnologyCompilationResult result, String suffix){
        return result.files().stream().filter(file -> file.relativePath().endsWith(suffix)).map(file -> new String(file.content(), StandardCharsets.UTF_8)).findFirst().orElseThrow();
    }
    private static TechnologyCompilationRequest typescriptServer(){
        var source = TechnologyAdapterTestFixtures.glspRequest();
        var t = source.profile().target();
        var target = new IntegrationTarget(t.technology(), t.kind(), t.technologyVersion(), t.projectId(), t.moduleName(), t.buildSystem(), t.sourceRoots(), t.configurationFiles(), t.endpoint(), Map.of("serverLanguage", "typescript"));
        var p = source.profile();
        var profile = new IntegrationProfileModel(p.schemaVersion(), p.id(), p.metamodelId(), p.name(), target, p.deliveryMode(), p.features(), p.artifacts(), p.mappings(), p.injectionPolicy(), p.requiredCapabilities(), p.annotations());
        return new TechnologyCompilationRequest(profile, source.artifacts(), "test", Map.of());
    }
}

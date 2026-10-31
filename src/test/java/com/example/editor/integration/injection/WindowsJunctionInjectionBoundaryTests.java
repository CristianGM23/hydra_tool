package com.example.editor.integration.injection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.InjectionPolicy;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import tools.jackson.databind.json.JsonMapper;

/**
 * SEC-WIN-JUNCTION in the technology injection: /apply writes the generated package under <allowed-root>/<targetDirectory>, its backups,
 * manifests and index under .hydra, and /rollback deletes what it added. The paths were only checked as text, so a directory junction
 * anywhere in the host (or the host itself) took writes and deletions outside the allowed root. The oracle is where the bytes end up.
 */
class WindowsJunctionInjectionBoundaryTests {
    @TempDir Path temporary;
    private Path allowed;
    private Path outside;
    private SafeTechnologyInjectionService service;

    @BeforeEach void setUp() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"), "directory junctions are a Windows feature");
        allowed=temporary.resolve("allowed"); Files.createDirectories(allowed);
        outside=temporary.resolve("outside"); Files.createDirectories(outside);
        var properties=new TechnologyInjectionProperties(); properties.setAllowedRoot(allowed);
        service=new SafeTechnologyInjectionService(properties,JsonMapper.builder().findAndAddModules().build());
    }

    @Test void aJunctionInsideTheHostDoesNotTakeTheGeneratedPackageOutsideTheAllowedRoot() throws Exception {
        Files.createDirectories(allowed.resolve("host"));
        junction(allowed.resolve("host/generated-integration"),outside);

        Throwable failure=catchThrowable(() -> service.apply(profile(),result("generated"),"host"));

        assertThat(filesUnder(outside)).as("the injection wrote outside the allowed root through a junction").isEmpty();
        assertThat(failure).isNotNull();
    }

    @Test void aHostDirectoryThatIsAJunctionIsRefused() throws Exception {
        junction(allowed.resolve("host"),outside);

        Throwable failure=catchThrowable(() -> service.apply(profile(),result("generated"),"host"));

        assertThat(filesUnder(outside)).as("the injection wrote outside the allowed root through a host junction").isEmpty();
        assertThat(failure).isNotNull();
    }

    @Test void aJunctionForTheInternalDirectoryDoesNotTakeManifestsOrBackupsOutside() throws Exception {
        Files.createDirectories(allowed.resolve("host"));
        junction(allowed.resolve("host/.hydra"),outside);

        Throwable failure=catchThrowable(() -> service.apply(profile(),result("generated"),"host"));

        assertThat(filesUnder(outside)).as("manifests, index or backups were written outside the allowed root").isEmpty();
        assertThat(failure).isNotNull();
    }

    @Test void rollingBackAfterTheHostWasRedirectedNeverDeletesOutsideFiles() throws Exception {
        var applied=service.apply(profile(),result("generated"),"host");
        Path generatedRoot=allowed.resolve("host/generated-integration");
        Files.move(generatedRoot,temporary.resolve("moved-away"));
        Path decoy=outside.resolve("people-emf/generated.txt"); Files.createDirectories(decoy.getParent()); Files.writeString(decoy,"outside data");
        junction(generatedRoot,outside);

        catchThrowable(() -> service.rollback("host",applied.manifest().id()));

        assertThat(decoy).as("the rollback deleted a file outside the allowed root through a junction").exists();
    }

    private static List<Path> filesUnder(Path root) throws Exception {
        try(Stream<Path> files=Files.walk(root)) { return files.filter(Files::isRegularFile).toList(); }
    }

    private static IntegrationProfileModel profile(){var source=TechnologyAdapterTestFixtures.emfRequest().profile();InjectionPolicy policy=new InjectionPolicy(source.injectionPolicy().strategy(),source.injectionPolicy().generatedRoot(),source.injectionPolicy().moduleName(),true,true,true,false,false,true);return new IntegrationProfileModel(source.schemaVersion(),source.id(),source.metamodelId(),source.name(),source.target(),source.deliveryMode(),source.features(),source.artifacts(),source.mappings(),policy,source.requiredCapabilities(),source.annotations());}
    private static TechnologyCompilationResult result(String value){var file=new TechnologyGeneratedFile("generated-integration/people-emf/generated.txt","text/plain",value.getBytes(StandardCharsets.UTF_8),InjectionFileOwnership.GENERATED,true,List.of("people"),Map.of());return new TechnologyCompilationResult(com.example.editor.integration.IntegrationTechnology.EMF,List.of(file),List.of(),List.of("install"),List.of("rollback"),List.of(),List.of(),Map.of("generatorVersion","test"));}

    /** mklink /J needs no privilege, unlike a symbolic link. */
    private static void junction(Path link, Path target) throws Exception {
        Process process=new ProcessBuilder("cmd","/c","mklink","/J",link.toString(),target.toString()).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }
}

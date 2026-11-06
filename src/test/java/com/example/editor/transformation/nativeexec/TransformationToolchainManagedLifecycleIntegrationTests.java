package com.example.editor.transformation.nativeexec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks y sin toolchains nativas, del ciclo de vida de un motor gestionado
 * (toolchains_check_install.a2/d2/a3 y toolchains_engine_lifecycle.c/d). El catalogo de confianza,
 * los paquetes con SHA-256 real, la instalacion atomica en @TempDir y el diagnostico se ejecutan de
 * verdad; el puente es el proceso fixture real que ya usan las pruebas del ejecutor (se lanza una
 * JVM hija y se autodescribe). No se instala ni se ejecuta ATL/Xtend/Java real.
 */
class TransformationToolchainManagedLifecycleIntegrationTests {
    private static final String PACKAGE_ID = "fixture-bridge";

    @Test
    @DisplayName("toolchains_check_install.a3: el catalogo lista v1 y v2 instalables y marca no instalable el paquete con SHA-256 manipulado")
    void catalogListsValidPackagesAndFlagsATamperedChecksum(@TempDir Path temporary) throws Exception {
        TransformationToolchainProvisioningService service = service(temporary).provisioning();
        buildPackage(temporary, TransformationExportLanguage.ATL, "1.0.0", false, Map.of());
        buildPackage(temporary, TransformationExportLanguage.ATL, "2.0.0", false, Map.of("VERSION.txt", "2.0.0"));
        buildPackage(temporary, TransformationExportLanguage.ATL, "3.0.0-tampered", true, Map.of());

        TransformationToolchainCatalog catalog = service.catalog();

        assertThat(catalog.provisioningEnabled()).isTrue();
        List<TransformationToolchainPackageDescriptor> atl = catalog.packages().stream()
                .filter(item -> item.language() == TransformationExportLanguage.ATL).toList();
        assertThat(atl).extracting(TransformationToolchainPackageDescriptor::packageVersion)
                .containsExactly("1.0.0", "2.0.0", "3.0.0-tampered");
        assertThat(atl).extracting(TransformationToolchainPackageDescriptor::installable).containsExactly(true, true, false);
        assertThat(atl).extracting(TransformationToolchainPackageDescriptor::installed).containsOnly(false);
        assertThat(atl.get(0).bridgeName()).isEqualTo("fixture-bridge");
        assertThat(atl.get(0).toolchainVersion()).isEqualTo("4.0");
        assertThat(atl.get(2).diagnostics()).isNotEmpty();
        assertThat(String.join(" ", atl.get(2).diagnostics())).doesNotContain(temporary.toString());
        // El paquete manipulado tampoco se puede instalar y no deja nada en el slot gestionado.
        assertThatThrownBy(() -> service.install(TransformationExportLanguage.ATL,
                new TransformationToolchainProvisioningRequest(PACKAGE_ID, "3.0.0-tampered", "", "")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(temporary.resolve("managed").resolve("atl"))).isFalse();
    }

    @Test
    @DisplayName("toolchains_engine_lifecycle.c/d + toolchains_check_install.a3: instalar v1, actualizar a v2, rechazar esperado erroneo, desinstalar y reinstalar")
    void installUpdateRejectUninstallAndReinstall(@TempDir Path temporary) throws Exception {
        Env env = service(temporary);
        TransformationToolchainProvisioningService service = env.provisioning();
        buildPackage(temporary, TransformationExportLanguage.ATL, "1.0.0", false, Map.of());
        buildPackage(temporary, TransformationExportLanguage.ATL, "2.0.0", false, Map.of("VERSION.txt", "2.0.0"));
        Path installed = temporary.resolve("managed").resolve("atl");
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        TransformationToolchainProvisioningReport first = service.install(TransformationExportLanguage.ATL,
                new TransformationToolchainProvisioningRequest(PACKAGE_ID, "1.0.0", "", ""));
        assertThat(first.status()).isEqualTo(TransformationToolchainProvisioningStatus.INSTALLED);
        assertThat(first.diagnostic().health()).isEqualTo(TransformationNativeRuntimeHealth.READY);
        assertThat(first.diagnostic().source()).isEqualTo(TransformationNativeRuntimeSource.MANAGED_BUNDLE);
        assertThat(mapper.readTree(Files.readString(installed.resolve(".hydra-package.json"))).get("packageVersion").asString())
                .isEqualTo("1.0.0");
        assertThat(Files.exists(installed.resolve("VERSION.txt"))).isFalse();
        assertThat(service.catalog().packages()).filteredOn(item -> item.language() == TransformationExportLanguage.ATL
                && item.installed()).extracting(TransformationToolchainPackageDescriptor::packageVersion).containsExactly("1.0.0");

        // c: actualizar con la expectativa correcta de la version actual.
        TransformationToolchainProvisioningReport updated = service.install(TransformationExportLanguage.ATL,
                new TransformationToolchainProvisioningRequest(PACKAGE_ID, "2.0.0", PACKAGE_ID, "1.0.0"));
        assertThat(updated.status()).isEqualTo(TransformationToolchainProvisioningStatus.UPDATED);
        assertThat(mapper.readTree(Files.readString(installed.resolve(".hydra-package.json"))).get("packageVersion").asString())
                .isEqualTo("2.0.0");
        assertThat(Files.readString(installed.resolve("VERSION.txt"))).isEqualTo("2.0.0");

        // Una expectativa erronea de la version actual rechaza la operacion y deja v2 intacta.
        assertThatThrownBy(() -> service.install(TransformationExportLanguage.ATL,
                new TransformationToolchainProvisioningRequest(PACKAGE_ID, "1.0.0", PACKAGE_ID, "9.9.9")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Managed toolchain changed since it was inspected; refresh the package catalog before retrying");
        assertThat(mapper.readTree(Files.readString(installed.resolve(".hydra-package.json"))).get("packageVersion").asString())
                .isEqualTo("2.0.0");
        assertThat(Files.readString(installed.resolve("VERSION.txt"))).isEqualTo("2.0.0");

        // d: eliminar el motor gestionado y volver a instalarlo.
        TransformationToolchainProvisioningReport removed = service.uninstall(TransformationExportLanguage.ATL,
                new TransformationToolchainRemovalRequest(PACKAGE_ID, "2.0.0"));
        assertThat(removed.status()).isEqualTo(TransformationToolchainProvisioningStatus.REMOVED);
        assertThat(removed.packageVersion()).isEqualTo("2.0.0");
        assertThat(Files.exists(installed)).isFalse();
        assertThat(service.catalog().installations()).filteredOn(item -> item.language() == TransformationExportLanguage.ATL)
                .singleElement().satisfies(item -> assertThat(item.present()).isFalse());

        TransformationToolchainProvisioningReport reinstalled = service.install(TransformationExportLanguage.ATL,
                new TransformationToolchainProvisioningRequest(PACKAGE_ID, "1.0.0", "", ""));
        assertThat(reinstalled.status()).isEqualTo(TransformationToolchainProvisioningStatus.INSTALLED);
        assertThat(Files.exists(installed.resolve(".hydra-package.json"))).isTrue();
    }

    @Test
    @DisplayName("toolchains_engine_lifecycle.c/d: un bundle de administrador no se pisa ni se elimina")
    void administratorManagedBundleIsNeverOverwrittenNorRemoved(@TempDir Path temporary) throws Exception {
        TransformationToolchainProvisioningService service = service(temporary).provisioning();
        buildPackage(temporary, TransformationExportLanguage.ETL, "1.0.0", false, Map.of());
        Path adminBundle = temporary.resolve("managed").resolve("etl");
        Files.createDirectories(adminBundle);
        Files.writeString(adminBundle.resolve("keep.txt"), "administrator content");

        TransformationToolchainProvisioningReport install = service.install(TransformationExportLanguage.ETL,
                new TransformationToolchainProvisioningRequest(PACKAGE_ID, "1.0.0", "", ""));
        TransformationToolchainProvisioningReport uninstall = service.uninstall(TransformationExportLanguage.ETL,
                new TransformationToolchainRemovalRequest("", ""));

        assertThat(install.status()).isEqualTo(TransformationToolchainProvisioningStatus.BLOCKED);
        assertThat(uninstall.status()).isEqualTo(TransformationToolchainProvisioningStatus.BLOCKED);
        assertThat(Files.readString(adminBundle.resolve("keep.txt"))).isEqualTo("administrator content");
        assertThat(Files.exists(adminBundle.resolve(".hydra-package.json"))).isFalse();
        assertThat(service.catalog().installations()).filteredOn(item -> item.language() == TransformationExportLanguage.ETL)
                .singleElement().satisfies(item -> {
                    assertThat(item.present()).isTrue();
                    assertThat(item.hydraManaged()).isFalse();
                });
    }

    @Test
    @DisplayName("toolchains_check_install.a2/d2 + toolchains_engine_lifecycle: diagnose con comando explicito del puente fixture -> READY; comando inexistente -> no listo")
    void diagnoseRunsTheFixtureBridgeAndReportsAMissingCommandAsNotReady(@TempDir Path temporary) {
        Env env = service(temporary);
        env.properties().setAtlCommand(List.of(javaExecutable(), "-cp", absoluteClasspath(),
                TransformationNativeRuntimeExecutorTests.NativeRuntimeBridgeFixtureMain.class.getName()));
        env.properties().setEtlCommand(List.of(temporary.resolve("no-such-bridge-executable").toString()));

        TransformationNativeRuntimeDiagnostic ready = env.executor().diagnose(TransformationExportLanguage.ATL);
        TransformationNativeRuntimeDiagnostic missing = env.executor().diagnose(TransformationExportLanguage.ETL);

        assertThat(ready.health()).isEqualTo(TransformationNativeRuntimeHealth.READY);
        assertThat(ready.compatible()).isTrue();
        assertThat(ready.source()).isEqualTo(TransformationNativeRuntimeSource.EXPLICIT_COMMAND);
        assertThat(ready.bridgeName()).isEqualTo("fixture-bridge");
        assertThat(ready.bridgeVersion()).isEqualTo("1.2.3");
        assertThat(ready.supportedKinds()).containsExactly(TransformationKind.M2M);
        assertThat(ready.supportedDirections()).containsExactly(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE);
        assertThat(missing.health()).isNotEqualTo(TransformationNativeRuntimeHealth.READY);
        assertThat(missing.compatible()).isFalse();
        assertThat(missing.supportedKinds()).isEmpty();
    }

    // --------------------------------------------------------------------------------- fixtures

    private record Env(TransformationToolchainProvisioningService provisioning, TransformationNativeRuntimeExecutor executor,
            TransformationNativeRuntimeProperties properties) { }

    private static Env service(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());

        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        properties.setProvisioningEnabled(true);
        properties.setManagedDistributionRoot(temporary.resolve("distribution"));
        properties.setManagedToolchainRoot(temporary.resolve("managed"));

        HydraExternalProcessSecurityProperties securityProperties = new HydraExternalProcessSecurityProperties();
        securityProperties.setTemporaryRoot(temporary.resolve("process-workspaces"));
        securityProperties.setMaximumArgumentCharacters(200_000);
        HydraExternalProcessPolicy policy = new HydraExternalProcessPolicy(securityProperties);
        HydraTemporaryWorkspace workspaces = new HydraTemporaryWorkspace(policy);

        TransformationNativeRuntimeExecutor executor = new TransformationNativeRuntimeExecutor(properties, policy, workspaces,
                metamodels, mapper, Runnable::run, new HydraProcessTreeTerminator());
        return new Env(new TransformationToolchainProvisioningService(properties, mapper, executor), executor, properties);
    }

    /** Publica un paquete completo (manifiesto + carga util + SHA-256 real) bajo el slot indicado; opcionalmente con un hash manipulado. */
    private static void buildPackage(Path temporary, TransformationExportLanguage slot, String version, boolean tamperChecksum,
            Map<String, String> extraFiles) throws Exception {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        Path versionRoot = temporary.resolve("distribution").resolve(slot.name().toLowerCase(Locale.ROOT)).resolve(PACKAGE_ID).resolve(version);
        Path payload = versionRoot.resolve("payload");
        Files.createDirectories(payload);

        List<String> command = List.of(javaExecutable(), "-cp", absoluteClasspath(),
                TransformationNativeRuntimeExecutorTests.NativeRuntimeBridgeFixtureMain.class.getName());
        TransformationManagedToolchainManifest runtimeManifest = new TransformationManagedToolchainManifest("1.0", slot,
                "fixture-bridge", "1.2.3", "fixture-atl", "4.0", command);
        Path runtimeManifestFile = payload.resolve("toolchain.json");
        Files.write(runtimeManifestFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(runtimeManifest));

        Map<String, String> checksums = new LinkedHashMap<>();
        checksums.put("toolchain.json", tamperChecksum ? "0".repeat(64) : sha256(runtimeManifestFile));
        for(Map.Entry<String, String> extra : extraFiles.entrySet()) {
            Path file = payload.resolve(extra.getKey());
            Files.writeString(file, extra.getValue());
            checksums.put(extra.getKey(), sha256(file));
        }
        TransformationToolchainPackageManifest packageManifest = new TransformationToolchainPackageManifest("1.0", PACKAGE_ID, version,
                slot, "fixture-bridge", "1.2.3", "fixture-atl", "4.0", checksums);
        Files.write(versionRoot.resolve("package.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(packageManifest));
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
    }

    private static String absoluteClasspath() {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return java.util.Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .map(Path::of).map(path -> path.isAbsolute() ? path : base.resolve(path).normalize())
                .map(Path::toString).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
    }
}

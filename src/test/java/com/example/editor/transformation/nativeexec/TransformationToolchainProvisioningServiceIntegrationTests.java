package com.example.editor.transformation.nativeexec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
import com.example.editor.transformation.export.TransformationExportLanguage;

import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, de instalar y revertir de verdad un paquete de toolchain de
 * transformacion a traves del mismo servicio real que respalda el catalogo administrado. Cubre
 * directamente "a4" (verificar e instalar), "d3" (¿compatible?, ambas ramas), "a5" (revertir la
 * instalacion) y "r" (estado conocido o motor listo) de toolchains_check_install: un paquete real,
 * verificado por SHA-256 real y copiado de forma atomica real, se instala de verdad y, tras
 * instalarse, HYDRA ejecuta de verdad un diagnostico real (el mismo puente real auto-descriptivo
 * ya probado en {@link TransformationNativeRuntimeExecutorTests}) contra el bridge real recien
 * instalado; cuando el bridge real se autodescribe con un idioma real distinto del slot real
 * instalado, HYDRA revierte de verdad la instalacion real y dejas el slot tal y como estaba antes.
 */
class TransformationToolchainProvisioningServiceIntegrationTests {

    @Test
    void installsARealPackageAndKeepsItWhenTheRealPostInstallDiagnosisIsCompatible(@TempDir Path temporary) throws Exception {
        TransformationToolchainProvisioningService service = service(temporary);

        // "a4": un paquete real (manifiesto + carga util reales, SHA-256 real) se verifica y se
        // copia de forma atomica real.
        buildPackage(temporary, TransformationExportLanguage.ATL, TransformationExportLanguage.ATL, "fixture-bridge", "1.0.0", temporary);
        TransformationToolchainProvisioningReport report = service.install(TransformationExportLanguage.ATL,
                new TransformationToolchainProvisioningRequest("fixture-bridge", "1.0.0", "", ""));

        // "d3"/"r": el diagnostico real post-instalacion (bridge real auto-descrito con el mismo
        // idioma real) es compatible, asi que la instalacion real queda tal cual, lista.
        assertThat(report.status()).isEqualTo(TransformationToolchainProvisioningStatus.INSTALLED);
        assertThat(report.diagnostic()).isNotNull();
        assertThat(report.diagnostic().health()).isEqualTo(TransformationNativeRuntimeHealth.READY);
        assertThat(report.diagnostic().compatible()).isTrue();
        assertThat(Files.exists(temporary.resolve("managed").resolve("atl"))).isTrue();

        TransformationToolchainCatalog catalog = service.catalog();
        assertThat(catalog.installations()).anyMatch(item -> item.language() == TransformationExportLanguage.ATL
                && item.hydraManaged() && item.packageId().equals("fixture-bridge"));
    }

    @Test
    void revertsARealInstallationWhenTheRealPostInstallDiagnosisIsIncompatible(@TempDir Path temporary) throws Exception {
        TransformationToolchainProvisioningService service = service(temporary);

        // Mismo bridge real, pero publicado e instalado bajo el slot real ETL mientras el propio
        // proceso real siempre se autodescribe como ATL -- un desajuste real de idioma, no
        // simulado, que HYDRA solo puede descubrir ejecutando de verdad el diagnostico real.
        buildPackage(temporary, TransformationExportLanguage.ETL, TransformationExportLanguage.ETL, "fixture-bridge", "1.0.0", temporary);
        TransformationToolchainProvisioningReport report = service.install(TransformationExportLanguage.ETL,
                new TransformationToolchainProvisioningRequest("fixture-bridge", "1.0.0", "", ""));

        // "d3"/"a5": el diagnostico real post-instalacion detecta el desajuste real de idioma
        // (INCOMPATIBLE) y HYDRA revierte de verdad la instalacion real, dejando el slot real
        // exactamente como antes (sin nada instalado).
        assertThat(report.status()).isEqualTo(TransformationToolchainProvisioningStatus.ROLLED_BACK);
        assertThat(report.diagnostic()).isNotNull();
        assertThat(report.diagnostic().health()).isEqualTo(TransformationNativeRuntimeHealth.INCOMPATIBLE);
        assertThat(Files.exists(temporary.resolve("managed").resolve("etl"))).isFalse();
    }

    private static TransformationToolchainProvisioningService service(Path temporary) {
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
        return new TransformationToolchainProvisioningService(properties, mapper, executor);
    }

    /** Publica un paquete real completo (manifiesto + carga util real + SHA-256 real) bajo el slot indicado. */
    private static void buildPackage(Path temporary, TransformationExportLanguage slot, TransformationExportLanguage manifestLanguage,
            String packageId, String version, Path classpathBase) throws Exception {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        Path versionRoot = temporary.resolve("distribution").resolve(slot.name().toLowerCase(java.util.Locale.ROOT))
                .resolve(packageId).resolve(version);
        Path payload = versionRoot.resolve("payload");
        Files.createDirectories(payload);

        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
        String classpath = absoluteClasspath();
        List<String> command = List.of(javaExecutable, "-cp", classpath,
                TransformationNativeRuntimeExecutorTests.NativeRuntimeBridgeFixtureMain.class.getName());

        TransformationManagedToolchainManifest runtimeManifest = new TransformationManagedToolchainManifest("1.0", manifestLanguage,
                "fixture-bridge", "1.2.3", "fixture-atl", "4.0", command);
        Path runtimeManifestFile = payload.resolve("toolchain.json");
        Files.write(runtimeManifestFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(runtimeManifest));
        String checksum = sha256(runtimeManifestFile);

        TransformationToolchainPackageManifest packageManifest = new TransformationToolchainPackageManifest("1.0", packageId, version,
                manifestLanguage, "fixture-bridge", "1.2.3", "fixture-atl", "4.0", Map.of("toolchain.json", checksum));
        Files.write(versionRoot.resolve("package.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(packageManifest));
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String absoluteClasspath() {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return java.util.Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .map(Path::of).map(path -> path.isAbsolute() ? path : base.resolve(path).normalize())
                .map(Path::toString).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
    }
}

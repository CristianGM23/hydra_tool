package com.example.editor.portability.archive;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityPackageArchiveServiceTests {
    @TempDir java.nio.file.Path directory;
    private CapabilityPackageArchiveProperties properties;
    private CapabilityPackageArchiveService service;

    @BeforeEach void setUp() {
        properties = new CapabilityPackageArchiveProperties();
        properties.setCacheDirectory(directory.resolve("cache"));
        var mapper = JsonMapper.builder().findAndAddModules().build();
        service = new CapabilityPackageArchiveService(properties, new CapabilityPackageChecker(),
                new CapabilityPackageDependencyResolver(), mapper);
    }

    @Test void exportsAndImportsAPartialPackage() {
        var exported = service.exportPackage(packageValue(), true);
        var imported = service.importPackage(exported.archive(), true, true);
        assertThat(imported.checkReport().valid()).isTrue();
        assertThat(imported.capabilityPackage().canonicalDocuments()).containsKeys("metamodel.json", "validation.json", "actions.json");
        assertThat(imported.capabilityPackage().resources()).containsEntry("icons/check.svg", "<svg/>");
        assertThat(imported.descriptor().contentHash()).isEqualTo(exported.descriptor().contentHash());
    }

    @Test void exportIsByteForByteReproducible() {
        byte[] first = service.exportPackage(packageValue(), false).archive();
        byte[] second = service.exportPackage(packageValue(), false).archive();
        assertThat(second).isEqualTo(first);
    }

    @Test void placesDependenciesBeforeTheirConsumers() {
        var result = service.exportPackage(packageValue(), false);
        var imported = service.importPackage(result.archive(), true, false);
        assertThat(imported.capabilityPackage().manifest().capabilities()).extracting(CapabilitySelection::id)
                .containsExactly("metamodel", "validation", "fixes");
    }

    @Test void supportsLazyResources() {
        var imported = service.importPackage(service.exportPackage(packageValue(), false).archive(), false, false);
        assertThat(imported.capabilityPackage().resources()).isEmpty();
        assertThat(imported.deferredResourcePaths()).containsExactly("icons/check.svg");
        assertThat(imported.warnings()).anyMatch(value -> value.contains("not loaded"));
    }

    @Test void cachesByContentHashWithoutDuplicatingBytes() throws Exception {
        var first = service.exportPackage(packageValue(), true);
        var second = service.exportPackage(packageValue(), true);
        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(Files.list(directory.resolve("cache"))).hasSize(1);
    }

    @Test void signsAndVerifiesWhenASecretIsConfigured() {
        properties.setSignatureSecret("unit-test-secret");
        var exported = service.exportPackage(packageValue(), false);
        assertThat(exported.descriptor().signed()).isTrue();
        assertThat(service.importPackage(exported.archive(), true, false).descriptor().signatureAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test void rejectsSignedPackagesWithTheWrongSecret() {
        properties.setSignatureSecret("first-secret");
        byte[] archive = service.exportPackage(packageValue(), false).archive();
        properties.setSignatureSecret("different-secret");
        assertThatThrownBy(() -> service.importPackage(archive, true, false))
                .isInstanceOf(CapabilityPackageArchiveException.class)
                .hasMessageContaining("signature");
    }

    @Test void rejectsUnsignedPackagesWhenSignatureIsRequired() {
        byte[] archive = service.exportPackage(packageValue(), false).archive();
        properties.setRequireSignature(true);
        assertThatThrownBy(() -> service.importPackage(archive, true, false))
                .isInstanceOf(CapabilityPackageArchiveException.class)
                .hasMessageContaining("signed");
    }

    @Test void rejectsTamperedEntries() {
        byte[] archive = service.exportPackage(packageValue(), false).archive();
        byte[] tampered = archive.clone();
        for(int index = tampered.length / 2; index < tampered.length; index++) {
            if(tampered[index] != 0) { tampered[index] ^= 1; break; }
        }
        assertThatThrownBy(() -> service.importPackage(tampered, true, false))
                .isInstanceOf(CapabilityPackageArchiveException.class);
    }

    @Test void rejectsUnsafeResourcePaths() {
        var invalid = new HydraCapabilityPackage(packageValue().manifest(), packageValue().canonicalDocuments(), Map.of("../secret", "x"));
        assertThatThrownBy(() -> service.exportPackage(invalid, false)).isInstanceOf(CapabilityPackageArchiveException.class)
                .hasMessageContaining("Unsafe");
    }

    @Test void enforcesExpandedEntryLimits() {
        properties.setMaximumEntryBytes(1024);
        String large = "x".repeat(2048);
        var invalid = new HydraCapabilityPackage(packageValue().manifest(), packageValue().canonicalDocuments(), Map.of("large.txt", large));
        assertThatThrownBy(() -> service.exportPackage(invalid, false)).isInstanceOf(CapabilityPackageArchiveException.class)
                .hasMessageContaining("size");
    }

    @Test void cachedArchiveCanBeLoadedByContentHash() {
        var result = service.exportPackage(packageValue(), true);
        assertThat(service.loadCached(result.descriptor().contentHash())).isEqualTo(result.archive());
    }

    @Test void archiveContainsOnlyDeclaredPortableContent() {
        var result = service.exportPackage(packageValue(), false);
        String raw = new String(result.archive(), StandardCharsets.ISO_8859_1);
        assertThat(raw).contains("manifest.json", "canonical/metamodel.json", "resources/icons/check.svg");
        assertThat(raw).doesNotContain("target/", "node_modules/");
    }

    private HydraCapabilityPackage packageValue() {
        var metamodel = new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL, "metamodel.json", "2.1", true, false, Map.of());
        var validation = new CapabilitySelection("validation", HydraCapabilityKind.VALIDATIONS, "validation.json", "1.0", true, false, Map.of());
        var fixes = new CapabilitySelection("fixes", HydraCapabilityKind.QUICK_FIXES, "actions.json", "1.0", true, false, Map.of());
        var manifest = new HydraCapabilityPackageManifest("1.0", "people-tools", "People tools", "1.0.0", "people",
                new MetamodelOrigin(MetamodelOriginKind.IMPORTED, IntegrationTechnology.EMF, "people.ecore", "", "", "", "", Map.of(), Map.of()),
                List.of(fixes, validation, metamodel), List.of(
                        new CapabilityDependency("fixes", "validation", true, ">=1.0", "diagnostic source"),
                        new CapabilityDependency("validation", "metamodel", true, ">=2.1", "semantic types")),
                List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of(
                "metamodel.json", JsonNodeFactory.instance.objectNode().put("id", "people"),
                "validation.json", JsonNodeFactory.instance.objectNode().put("id", "people-validations"),
                "actions.json", JsonNodeFactory.instance.objectNode().put("id", "people-actions")),
                Map.of("icons/check.svg", "<svg/>"));
    }
}

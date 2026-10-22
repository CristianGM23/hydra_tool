package com.example.editor.portability.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puerta de aceptacion del paquete de capas y resolucion de dependencias al exportar.
 *
 * <p>Etapa certificada (directa): {@code x_package_and_move_capabilities.d1}. Relacionadas sin demostrar (hallazgo):
 * {@code x_package_and_move_capabilities.a1} y {@code portability_author_export.b}, porque el resolvedor solo ORDENA las
 * capas elegidas y nunca anade las que faltan: una dependencia obligatoria sin elegir hace que el paquete se rechace.</p>
 *
 * <p>Colaboradores reales: servicio de archivos de paquetes, comprobador, resolvedor de dependencias y cache en un
 * {@code @TempDir}. Oraculo: los archivos se construyen a mano con {@code java.util.zip}, los SHA-256 y la huella de
 * contenido se recalculan en el test, el manifiesto exportado se lee abriendo el ZIP y la cache se lista en disco.</p>
 */
class CapabilityPackageAcceptanceGateTests {
    @TempDir Path directory;

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private CapabilityPackageArchiveProperties properties;
    private CapabilityPackageArchiveService service;
    private Path cache;

    @BeforeEach
    void setUp() {
        properties = new CapabilityPackageArchiveProperties();
        cache = directory.resolve("cache");
        properties.setCacheDirectory(cache);
        service = new CapabilityPackageArchiveService(properties, new CapabilityPackageChecker(),
                new CapabilityPackageDependencyResolver(), json);
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.d1: un paquete valido se acepta y se guarda en la cache; uno con errores, alterado o sin firma exigida se rechaza sin tocar nada")
    void anAcceptablePackageIsAcceptedAndOneWithBlockingErrorsIsRejectedWithoutTouchingAnything() throws Exception {
        // Archivo internamente coherente (huellas y descriptor correctos) pero cuyo manifiesto exige una capa que no trae.
        byte[] invalid = handmade(manifestWith(List.of(
                selection("metamodel", HydraCapabilityKind.METAMODEL, "metamodel.json", "2.1", true),
                selection("validation", HydraCapabilityKind.VALIDATIONS, "validation.json", "1.0", true)),
                List.of(new CapabilityDependency("validation", "metamodel", true, ">=2.1", "types"))),
                Map.of("canonical/metamodel.json", "{\"id\":\"shop\"}"), false);

        assertThatThrownBy(() -> service.importPackage(invalid, true, true))
                .isInstanceOfSatisfying(CapabilityPackageArchiveException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(CapabilityPackageArchiveException.Reason.INVALID_PACKAGE);
                    assertThat(error.getMessage()).contains("REQUIRED_DOCUMENT_MISSING");
                });
        assertThat(cachedFiles()).isZero();

        // Archivo con una huella declarada que no coincide con el contenido: alterado.
        byte[] tampered = handmade(validManifest(), validDocuments(), true);
        assertThatThrownBy(() -> service.importPackage(tampered, true, true))
                .isInstanceOfSatisfying(CapabilityPackageArchiveException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE);
                    assertThat(error.getMessage()).contains("checksum mismatch");
                });
        assertThat(cachedFiles()).isZero();

        // Paquete correcto pero con firma exigida y no presente: rechazado.
        byte[] valid = handmade(validManifest(), validDocuments(), false);
        properties.setRequireSignature(true);
        assertThatThrownBy(() -> service.importPackage(valid, true, true))
                .isInstanceOfSatisfying(CapabilityPackageArchiveException.class, error ->
                        assertThat(error.getReason()).isEqualTo(CapabilityPackageArchiveException.Reason.SIGNATURE_INVALID));
        assertThat(cachedFiles()).isZero();
        properties.setRequireSignature(false);

        // Rama aceptable: el mismo paquete valido se importa y solo entonces se guarda en la cache, byte a byte.
        CapabilityPackageImportResult accepted = service.importPackage(valid, true, true);
        assertThat(accepted.checkReport().valid()).isTrue();
        assertThat(accepted.capabilityPackage().canonicalDocuments().keySet()).containsExactlyInAnyOrder("metamodel.json", "validation.json");
        assertThat(accepted.capabilityPackage().manifest().packageVersion()).isEqualTo("2.3.0");
        assertThat(accepted.archiveSha256()).isEqualTo(sha256(valid));
        assertThat(cachedFiles()).isEqualTo(1);
        Path cachedArchive = cache.resolve(accepted.descriptor().contentHash() + ".hydra-cap");
        assertThat(Files.readAllBytes(cachedArchive)).isEqualTo(valid);
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.a1 y portability_author_export.b (hallazgo): las dependencias se ordenan pero no se anaden solas")
    void dependenciesAreOrderedAndVersionedButNeverAddedAutomatically() throws Exception {
        CapabilitySelection metamodel = selection("metamodel", HydraCapabilityKind.METAMODEL, "metamodel.json", "2.1", true);
        CapabilitySelection validation = selection("validation", HydraCapabilityKind.VALIDATIONS, "validation.json", "1.0", true);
        CapabilitySelection fixes = selection("fixes", HydraCapabilityKind.QUICK_FIXES, "actions.json", "1.0", true);
        CapabilityDependency validationNeedsMetamodel = new CapabilityDependency("validation", "metamodel", true, ">=2.1", "types");
        CapabilityDependency fixesNeedValidation = new CapabilityDependency("fixes", "validation", true, ">=1.0", "diagnostics");
        Map<String, JsonNode> documents = new LinkedHashMap<>();
        documents.put("metamodel.json", json.readTree("{\"id\":\"shop\"}"));
        documents.put("validation.json", json.readTree("{\"id\":\"shop-validations\"}"));
        documents.put("actions.json", json.readTree("{\"id\":\"shop-actions\"}"));

        // Elegir una capa sin las que necesita NO las anade: el paquete se rechaza y no se genera ni se guarda nada.
        HydraCapabilityPackage withoutDependency = new HydraCapabilityPackage(
                manifestWith(List.of(fixes, validation), List.of(fixesNeedValidation, validationNeedsMetamodel)), documents, Map.of());
        assertThatThrownBy(() -> service.exportPackage(withoutDependency, true))
                .isInstanceOfSatisfying(CapabilityPackageArchiveException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(CapabilityPackageArchiveException.Reason.INVALID_PACKAGE);
                    assertThat(error.getMessage()).contains("DEPENDENCY_TARGET_MISSING");
                });
        assertThat(cachedFiles()).isZero();

        // Con todas elegidas, en cualquier orden, el archivo las deja con las dependencias primero y con su version.
        HydraCapabilityPackage complete = new HydraCapabilityPackage(
                manifestWith(List.of(fixes, validation, metamodel), List.of(fixesNeedValidation, validationNeedsMetamodel)), documents, Map.of());
        CapabilityPackageArchiveResult exported = service.exportPackage(complete, true);
        assertThat(exported.fileName()).isEqualTo("shop-tools-2.3.0.hydra-cap");
        JsonNode manifest = json.readTree(entries(exported.archive()).get("manifest.json"));
        assertThat(ids(manifest.get("capabilities"))).containsExactly("metamodel", "validation", "fixes");
        assertThat(manifest.get("packageVersion").asString()).isEqualTo("2.3.0");
        assertThat(exported.descriptor().packageVersion()).isEqualTo("2.3.0");

        // Una dependencia opcional hacia una capa no elegida no la incorpora: el archivo contiene solo lo elegido.
        HydraCapabilityPackage optional = new HydraCapabilityPackage(
                manifestWith(List.of(validation), List.of(new CapabilityDependency("validation", "metamodel", false, ">=2.1", "types"))),
                Map.of("validation.json", documents.get("validation.json")), Map.of());
        CapabilityPackageArchiveResult optionalExport = service.exportPackage(optional, false);
        JsonNode optionalManifest = json.readTree(entries(optionalExport.archive()).get("manifest.json"));
        assertThat(ids(optionalManifest.get("capabilities"))).containsExactly("validation");
        assertThat(entries(optionalExport.archive()).keySet()).doesNotContain("canonical/metamodel.json");

        // Un ciclo entre capas elegidas se rechaza al exportar y el resolvedor tambien lo detecta por si solo.
        CapabilitySelection first = selection("a", HydraCapabilityKind.METAMODEL, "a.json", "2.1", true);
        CapabilitySelection second = selection("b", HydraCapabilityKind.VALIDATIONS, "b.json", "2.1", true);
        List<CapabilityDependency> cycle = List.of(new CapabilityDependency("a", "b", true, ">=2.1", "x"),
                new CapabilityDependency("b", "a", true, ">=2.1", "y"));
        HydraCapabilityPackage cyclic = new HydraCapabilityPackage(manifestWith(List.of(first, second), cycle),
                Map.of("a.json", json.readTree("{\"id\":\"a\"}"), "b.json", json.readTree("{\"id\":\"b\"}")), Map.of());
        assertThatThrownBy(() -> service.exportPackage(cyclic, false))
                .isInstanceOfSatisfying(CapabilityPackageArchiveException.class, error ->
                        assertThat(error.getMessage()).contains("CAPABILITY_DEPENDENCY_CYCLE"));
        assertThatThrownBy(() -> new CapabilityPackageDependencyResolver().resolve(cyclic.manifest()))
                .isInstanceOf(CapabilityPackageArchiveException.class).hasMessageContaining("cycle");
    }

    private HydraCapabilityPackageManifest validManifest() {
        return manifestWith(List.of(
                selection("metamodel", HydraCapabilityKind.METAMODEL, "metamodel.json", "2.1", true),
                selection("validation", HydraCapabilityKind.VALIDATIONS, "validation.json", "1.0", true)),
                List.of(new CapabilityDependency("validation", "metamodel", true, ">=2.1", "types")));
    }

    private Map<String, String> validDocuments() {
        return Map.of("canonical/metamodel.json", "{\"id\":\"shop\"}", "canonical/validation.json", "{\"id\":\"shop-validations\"}");
    }

    private HydraCapabilityPackageManifest manifestWith(List<CapabilitySelection> capabilities, List<CapabilityDependency> dependencies) {
        return new HydraCapabilityPackageManifest("1.0", "shop-tools", "Shop tools", "2.3.0", "shop",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                capabilities, dependencies, List.of(), Map.of(), Map.of());
    }

    private static CapabilitySelection selection(String id, HydraCapabilityKind kind, String document, String version, boolean required) {
        return new CapabilitySelection(id, kind, document, version, required, false, Map.of());
    }

    /** Construye a mano un archivo coherente (descriptor, huellas y huella de contenido calculados aqui). */
    private byte[] handmade(HydraCapabilityPackageManifest manifest, Map<String, String> content, boolean corruptDeclaredHash) throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        content.forEach((path, text) -> files.put(path, text.getBytes(StandardCharsets.UTF_8)));
        files.put("manifest.json", json.writeValueAsBytes(manifest));
        List<CapabilityPackageArchiveEntry> descriptors = new ArrayList<>();
        StringBuilder hashSource = new StringBuilder();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String hash = sha256(file.getValue());
            hashSource.append(file.getKey()).append('\u0000').append(file.getValue().length).append('\u0000').append(hash).append('\n');
            String declared = corruptDeclaredHash && file.getKey().equals("canonical/metamodel.json") ? "0".repeat(64) : hash;
            String kind = file.getKey().equals("manifest.json") ? "MANIFEST" : "CANONICAL_DOCUMENT";
            descriptors.add(new CapabilityPackageArchiveEntry(file.getKey(), file.getValue().length, declared, kind));
        }
        String contentHash = sha256(hashSource.toString().getBytes(StandardCharsets.UTF_8));
        if (corruptDeclaredHash) {
            // La huella global tambien se calcula con las huellas declaradas, como hace el verificador.
            StringBuilder declaredSource = new StringBuilder();
            descriptors.forEach(entry -> declaredSource.append(entry.path()).append('\u0000').append(entry.size())
                    .append('\u0000').append(entry.sha256()).append('\n'));
            contentHash = sha256(declaredSource.toString().getBytes(StandardCharsets.UTF_8));
        }
        CapabilityPackageArchiveDescriptor descriptor = new CapabilityPackageArchiveDescriptor("1.0", manifest.id(),
                manifest.packageVersion(), contentHash, "NONE", "", "", descriptors, Map.of());
        files.put("META-INF/hydra-package.json", json.writeValueAsBytes(descriptor));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private long cachedFiles() throws IOException {
        if (!Files.isDirectory(cache)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(cache)) {
            return files.count();
        }
    }

    private static Map<String, byte[]> entries(byte[] archive) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    result.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        return result;
    }

    private static List<String> ids(JsonNode capabilities) {
        List<String> result = new ArrayList<>();
        for (JsonNode capability : capabilities) {
            result.add(capability.get("id").asString());
        }
        return result;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

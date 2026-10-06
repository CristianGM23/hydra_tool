package com.example.editor.portability.archive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Exporta e importa archivos .hydra-cap deterministas, verificables y almacenables por contenido. */
@Service
public class CapabilityPackageArchiveService {
    private static final String DESCRIPTOR_PATH = "META-INF/hydra-package.json";
    private static final String MANIFEST_PATH = "manifest.json";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final CapabilityPackageArchiveProperties properties;
    private final CapabilityPackageChecker checker;
    private final CapabilityPackageDependencyResolver dependencies;
    private final ObjectMapper mapper;
    private final ObjectWriter canonicalWriter;

    /** Descripción: Inicializa una instancia de CapabilityPackageArchiveService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; checker: dato de entrada necesario para la operación; dependencies: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPackageArchiveService(CapabilityPackageArchiveProperties properties,
            CapabilityPackageChecker checker, CapabilityPackageDependencyResolver dependencies,
            ObjectMapper mapper) {
        this.properties = properties;
        this.checker = checker;
        this.dependencies = dependencies;
        this.mapper = mapper;
        this.canonicalWriter = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Ejecuta la operación «exportPackage» correspondiente a la responsabilidad de SignatureValue. Entradas: source: dato de entrada necesario para la operación; useCache: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPackageArchiveResult exportPackage(HydraCapabilityPackage source, boolean useCache) {
        requireEnabled();
        CapabilityPackageCheckReport initialReport = checker.check(source);
        if(!initialReport.valid()) throw invalidPackage("Capability package contains blocking diagnostics: " + diagnosticCodes(initialReport));
        HydraCapabilityPackage normalized = normalized(source);
        Map<String, byte[]> contentEntries = contentEntries(normalized);
        Map<String, String> checksums = new TreeMap<>();
        contentEntries.forEach((path, bytes) -> checksums.put(path, sha256(bytes)));
        HydraCapabilityPackageManifest manifest = withChecksums(normalized.manifest(), checksums);
        byte[] manifestBytes = write(manifest);

        Map<String, byte[]> signedEntries = new TreeMap<>(contentEntries);
        signedEntries.put(MANIFEST_PATH, manifestBytes);
        List<CapabilityPackageArchiveEntry> entryDescriptors = signedEntries.entrySet().stream()
                .map(entry -> new CapabilityPackageArchiveEntry(entry.getKey(), entry.getValue().length,
                        sha256(entry.getValue()), kind(entry.getKey())))
                .sorted(Comparator.comparing(CapabilityPackageArchiveEntry::path)).toList();
        String contentHash = contentHash(entryDescriptors);
        SignatureValue signature = sign(contentHash);
        CapabilityPackageArchiveDescriptor descriptor = new CapabilityPackageArchiveDescriptor("1.0", manifest.id(),
                manifest.packageVersion(), contentHash, signature.algorithm(), signature.keyId(), signature.value(),
                entryDescriptors, Map.of("format", "HYDRA_CAPABILITY_PACKAGE", "reproducible", "true"));

        byte[] archive = zip(descriptor, signedEntries);
        if(archive.length > properties.getMaximumArchiveBytes()) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Capability package archive exceeds the configured maximum size");
        String archiveHash = sha256(archive);
        boolean cacheHit = false;
        if(useCache && properties.isCacheEnabled()) {
            Path cached = cachePath(contentHash);
            try {
                Files.createDirectories(cached.getParent());
                if(Files.isRegularFile(cached)) {
                    byte[] existing = com.example.editor.io.BoundedIo.readInternal(cached);
                    if(MessageDigest.isEqual(existing, archive)) cacheHit = true;
                    else atomicWrite(cached, archive);
                } else atomicWrite(cached, archive);
            } catch(IOException exception) {
                throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.IO_ERROR,
                        "Could not update the capability package cache", exception);
            }
        }
        CapabilityPackageCheckReport finalReport = checker.check(new HydraCapabilityPackage(manifest,
                normalized.canonicalDocuments(), normalized.resources()));
        return new CapabilityPackageArchiveResult(fileName(manifest), archive, archiveHash, cacheHit, descriptor, finalReport);
    }

    /** Descripción: Ejecuta la operación «importPackage» correspondiente a la responsabilidad de SignatureValue. Entradas: archive: archivo o contenido que debe procesarse; loadResources: dato de entrada necesario para la operación; useCache: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPackageImportResult importPackage(byte[] archive, boolean loadResources, boolean useCache) {
        requireEnabled();
        if(archive == null || archive.length == 0) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Capability package archive is empty");
        if(archive.length > properties.getMaximumArchiveBytes()) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Capability package archive exceeds the configured maximum size");
        Map<String, byte[]> entries = unzip(archive);
        CapabilityPackageArchiveDescriptor descriptor = read(requiredEntry(entries, DESCRIPTOR_PATH), CapabilityPackageArchiveDescriptor.class);
        if(!CapabilityPackageArchiveDescriptor.CURRENT_SCHEMA_VERSION.equals(descriptor.schemaVersion())) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Unsupported capability archive schema: " + descriptor.schemaVersion());
        verifyEntries(entries, descriptor);
        verifySignature(descriptor);

        HydraCapabilityPackageManifest manifest = read(requiredEntry(entries, MANIFEST_PATH), HydraCapabilityPackageManifest.class);
        Map<String, JsonNode> canonicalDocuments = new LinkedHashMap<>();
        Map<String, String> resources = new LinkedHashMap<>();
        List<String> deferred = new ArrayList<>();
        for(Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if(entry.getKey().startsWith("canonical/")) {
                String id = entry.getKey().substring("canonical/".length());
                canonicalDocuments.put(id, readTree(entry.getValue()));
            } else if(entry.getKey().startsWith("resources/")) {
                String id = entry.getKey().substring("resources/".length());
                if(loadResources) resources.put(id, new String(entry.getValue(), StandardCharsets.UTF_8));
                else deferred.add(id);
            }
        }
        if (!HydraCapabilityPackageManifest.CURRENT_SCHEMA_VERSION.equals(manifest.schemaVersion())) {
            throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE,
                    "Unsupported capability package schema: " + manifest.schemaVersion());
        }
        HydraCapabilityPackage value = new HydraCapabilityPackage(manifest, canonicalDocuments, resources);
        CapabilityPackageCheckReport report = checker.check(value);
        if(!report.valid()) throw invalidPackage("Imported capability package contains blocking diagnostics: " + diagnosticCodes(report));
        String archiveHash = sha256(archive);
        boolean cacheHit = cacheImported(archive, descriptor.contentHash(), useCache);
        List<String> warnings = new ArrayList<>();
        if(!loadResources && !deferred.isEmpty()) warnings.add("Resources were indexed but not loaded; request them lazily when needed.");
        if(!descriptor.signed()) warnings.add("Package integrity is verified by SHA-256 but no authenticity signature is present.");
        return new CapabilityPackageImportResult(value, descriptor, report, archiveHash, cacheHit, deferred, warnings);
    }

    /** Descripción: Obtiene la información correspondiente a «loadCached» dentro de SignatureValue. Entradas: contentHash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] loadCached(String contentHash) {
        requireEnabled();
        if(contentHash == null || !contentHash.matches("(?i)[a-f0-9]{64}")) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "A valid content hash is required");
        Path path = cachePath(contentHash.toLowerCase());
        try {
            if(!Files.isRegularFile(path)) throw new CapabilityPackageArchiveException(
                    CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Capability package is not present in the local cache");
            return com.example.editor.io.BoundedIo.readInternal(path);
        } catch(IOException exception) {
            throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.IO_ERROR,
                    "Could not read the capability package cache", exception);
        }
    }

    /** Descripción: Ejecuta la operación «normalized» correspondiente a la responsabilidad de SignatureValue. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraCapabilityPackage normalized(HydraCapabilityPackage source) {
        List<CapabilitySelection> ordered = dependencies.resolve(source.manifest());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest(source.manifest().schemaVersion(),
                source.manifest().id(), source.manifest().name(), source.manifest().packageVersion(),
                source.manifest().metamodelId(), source.manifest().semanticScopeId(), source.manifest().metamodelOrigin(), ordered,
                source.manifest().dependencies().stream().sorted(Comparator.comparing(value -> value.sourceCapabilityId() + "\u0000" + value.targetCapabilityId())).toList(),
                source.manifest().compatibility().stream().sorted(Comparator.comparing(value -> value.technology().name() + "\u0000" + value.capability().name())).toList(),
                Map.of(), new TreeMap<>(source.manifest().annotations()));
        return new HydraCapabilityPackage(manifest, new TreeMap<>(source.canonicalDocuments()), new TreeMap<>(source.resources()));
    }

    /** Descripción: Ejecuta la operación «contentEntries» correspondiente a la responsabilidad de SignatureValue. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, byte[]> contentEntries(HydraCapabilityPackage value) {
        Map<String, byte[]> result = new TreeMap<>();
        value.canonicalDocuments().forEach((id, document) -> putUnique(result, "canonical/" + safeRelativePath(id, true), write(document)));
        value.resources().forEach((id, resource) -> putUnique(result, "resources/" + safeRelativePath(id, false), resource.getBytes(StandardCharsets.UTF_8)));
        if(result.size() + 2 > properties.getMaximumEntries()) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Capability package contains too many entries");
        result.forEach((path, bytes) -> {
            if(bytes.length > properties.getMaximumEntryBytes()) throw new CapabilityPackageArchiveException(
                    CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Capability package entry exceeds the configured size: " + path);
        });
        return result;
    }

    /** Descripción: Valida descriptor, checksums y correspondencia exacta de entradas usando un índice de rutas para mantener coste lineal incluso con paquetes grandes. Entradas: entries: contenido expandido del paquete; descriptor: descriptor firmado del paquete. Salida: no devuelve valor; rechaza duplicados, entradas inesperadas o checksums inconsistentes. */ private void verifyEntries(Map<String, byte[]> entries, CapabilityPackageArchiveDescriptor descriptor) {
        if(descriptor.entries().size() > properties.getMaximumEntries()) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Capability package descriptor contains too many entries");
        List<CapabilityPackageArchiveEntry> ordered = descriptor.entries().stream().sorted(Comparator.comparing(CapabilityPackageArchiveEntry::path)).toList();
        LinkedHashSet<String> declaredPaths = new LinkedHashSet<>(Math.max(16, ordered.size() * 2));
        for(CapabilityPackageArchiveEntry expected : ordered) {
            if(!declaredPaths.add(expected.path())) throw new CapabilityPackageArchiveException(
                    CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Capability package descriptor contains duplicate entry: " + expected.path());
            byte[] actual = requiredEntry(entries, expected.path());
            if(actual.length != expected.size() || !sha256(actual).equalsIgnoreCase(expected.sha256())) throw new CapabilityPackageArchiveException(
                    CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Capability package entry checksum mismatch: " + expected.path());
        }
        boolean unexpected = entries.keySet().stream().anyMatch(path -> !DESCRIPTOR_PATH.equals(path) && !declaredPaths.contains(path));
        if(unexpected) throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE,
                "Capability package contains entries not declared by its descriptor");
        if(!contentHash(ordered).equalsIgnoreCase(descriptor.contentHash())) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Capability package content hash is invalid");
    }

    /** Descripción: Ejecuta la operación «unzip» correspondiente a la responsabilidad de SignatureValue. Entradas: archive: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, byte[]> unzip(byte[] archive) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try(ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry = input.getNextEntry()) != null) {
                if(entry.isDirectory()) continue;
                if(entries.size() >= properties.getMaximumEntries()) throw new CapabilityPackageArchiveException(
                        CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Capability package contains too many entries");
                String path = safeArchiveEntry(entry.getName());
                if(entries.containsKey(path)) throw new CapabilityPackageArchiveException(
                        CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Capability package contains duplicate entry: " + path);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while((read = input.read(chunk)) >= 0) {
                    buffer.write(chunk, 0, read);
                    if(buffer.size() > properties.getMaximumEntryBytes()) throw new CapabilityPackageArchiveException(
                            CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Capability package entry exceeds the configured size: " + path);
                }
                byte[] bytes = buffer.toByteArray();
                total += bytes.length;
                if(total > properties.getMaximumArchiveBytes()) throw new CapabilityPackageArchiveException(
                        CapabilityPackageArchiveException.Reason.LIMIT_EXCEEDED, "Expanded capability package exceeds the configured maximum size");
                entries.put(path, bytes);
            }
        } catch(CapabilityPackageArchiveException exception) { throw exception; }
        catch(IOException exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE,
                "Capability package is not a readable ZIP archive", exception); }
        return entries;
    }

    /** Descripción: Ejecuta la operación «zip» correspondiente a la responsabilidad de SignatureValue. Entradas: descriptor: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] zip(CapabilityPackageArchiveDescriptor descriptor, Map<String, byte[]> entries) {
        Map<String, byte[]> all = new TreeMap<>(entries);
        all.put(DESCRIPTOR_PATH, write(descriptor));
        try(ByteArrayOutputStream buffer = new ByteArrayOutputStream(); ZipOutputStream output = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for(Map.Entry<String, byte[]> entry : all.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                output.putNextEntry(zipEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
            output.finish();
            return buffer.toByteArray();
        } catch(IOException exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.IO_ERROR,
                "Could not create the capability package archive", exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «verifySignature» y rechaza entradas no válidas. Entradas: descriptor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifySignature(CapabilityPackageArchiveDescriptor descriptor) {
        if(!descriptor.signed()) {
            if(properties.isRequireSignature()) throw new CapabilityPackageArchiveException(
                    CapabilityPackageArchiveException.Reason.SIGNATURE_INVALID, "A signed capability package is required");
            return;
        }
        if(!HMAC_ALGORITHM.equals(descriptor.signatureAlgorithm())) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.SIGNATURE_INVALID, "Unsupported capability package signature algorithm");
        if(properties.getSignatureSecret().isBlank()) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.SIGNATURE_INVALID, "The package is signed but no verification secret is configured");
        String expected = hmac(descriptor.contentHash(), properties.getSignatureSecret());
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), descriptor.signature().getBytes(StandardCharsets.US_ASCII)))
            throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.SIGNATURE_INVALID,
                    "Capability package signature is invalid");
    }

    /** Descripción: Ejecuta la operación «sign» correspondiente a la responsabilidad de SignatureValue. Entradas: contentHash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private SignatureValue sign(String contentHash) {
        if(properties.getSignatureSecret().isBlank()) return new SignatureValue("NONE", "", "");
        return new SignatureValue(HMAC_ALGORITHM, properties.getSignatureKeyId(), hmac(contentHash, properties.getSignatureSecret()));
    }

    /** Descripción: Ejecuta la operación «hmac» correspondiente a la responsabilidad de SignatureValue. Entradas: value: valor de entrada que debe procesarse; secret: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.IO_ERROR,
                "Could not calculate the package signature", exception); }
    }

    /** Descripción: Ejecuta la operación «cacheImported» correspondiente a la responsabilidad de SignatureValue. Entradas: archive: archivo o contenido que debe procesarse; contentHash: dato de entrada necesario para la operación; useCache: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean cacheImported(byte[] archive, String contentHash, boolean useCache) {
        if(!useCache || !properties.isCacheEnabled()) return false;
        Path cached = cachePath(contentHash);
        try {
            Files.createDirectories(cached.getParent());
            if(Files.isRegularFile(cached)) {
                byte[] existing = com.example.editor.io.BoundedIo.readInternal(cached);
                if(MessageDigest.isEqual(existing, archive)) return true;
            }
            atomicWrite(cached, archive);
            return false;
        } catch(IOException exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.IO_ERROR,
                "Could not update the capability package cache", exception); }
    }

    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de SignatureValue. Entradas: target: dato de entrada necesario para la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch(java.nio.file.AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Descripción: Ejecuta la operación «withChecksums» correspondiente a la responsabilidad de SignatureValue. Entradas: source: dato de entrada necesario para la operación; checksums: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraCapabilityPackageManifest withChecksums(HydraCapabilityPackageManifest source, Map<String, String> checksums) {
        return new HydraCapabilityPackageManifest(source.schemaVersion(), source.id(), source.name(), source.packageVersion(),
                source.metamodelId(), source.semanticScopeId(), source.metamodelOrigin(), source.capabilities(), source.dependencies(), source.compatibility(),
                checksums, source.annotations());
    }

    /** Descripción: Ejecuta la operación «contentHash» correspondiente a la responsabilidad de SignatureValue. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String contentHash(List<CapabilityPackageArchiveEntry> entries) {
        StringBuilder source = new StringBuilder();
        entries.stream().sorted(Comparator.comparing(CapabilityPackageArchiveEntry::path))
                .forEach(entry -> source.append(entry.path()).append('\u0000').append(entry.size()).append('\u0000').append(entry.sha256()).append('\n'));
        return sha256(source.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Ejecuta la operación «safeRelativePath» correspondiente a la responsabilidad de SignatureValue. Entradas: value: valor de entrada que debe procesarse; appendJson: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeRelativePath(String value, boolean appendJson) {
        String path = safeArchiveEntry(value == null ? "" : value.replace('\\', '/'));
        if(path.isBlank()) throw invalidPackage("Capability package document/resource path must not be blank");
        return appendJson && !path.toLowerCase().endsWith(".json") ? path + ".json" : path;
    }

    /** Descripción: Ejecuta la operación «safeArchiveEntry» correspondiente a la responsabilidad de SignatureValue. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeArchiveEntry(String value) {
        if(value == null) throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Archive entry path is required");
        String normalized = value.replace('\\', '/');
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Absolute archive entry paths are forbidden: " + value);
        for(String part : normalized.split("/")) if(part.isBlank() || ".".equals(part) || "..".equals(part)) throw new CapabilityPackageArchiveException(
                CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE, "Unsafe archive entry path: " + value);
        return normalized;
    }

    /** Descripción: Ejecuta la operación «putUnique» correspondiente a la responsabilidad de SignatureValue. Entradas: values: colección de elementos que debe procesarse; path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void putUnique(Map<String, byte[]> values, String path, byte[] bytes) {
        if(values.put(path, bytes) != null) throw invalidPackage("Two package entries resolve to the same path: " + path);
    }
    /** Descripción: Valida las condiciones necesarias para «requiredEntry» y rechaza entradas no válidas. Entradas: entries: colección de elementos que debe procesarse; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] requiredEntry(Map<String, byte[]> entries, String path) {
        byte[] result = entries.get(path);
        if(result == null) throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE,
                "Capability package entry is missing: " + path);
        return result;
    }
    /** Descripción: Actualiza el estado gestionado por SignatureValue mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value) {
        try { return canonicalWriter.writeValueAsBytes(value); }
        catch(RuntimeException exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.IO_ERROR,
                "Could not serialize capability package content", exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «read» dentro de SignatureValue. Entradas: bytes: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T read(byte[] bytes, Class<T> type) {
        try { return mapper.readValue(bytes, type); }
        catch(RuntimeException exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE,
                "Capability package JSON is invalid", exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «readTree» dentro de SignatureValue. Entradas: bytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JsonNode readTree(byte[] bytes) {
        try { return mapper.readTree(bytes); }
        catch(RuntimeException exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_ARCHIVE,
                "Canonical package document is invalid JSON", exception); }
    }
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de SignatureValue. Entradas: bytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception exception) { throw new IllegalStateException("SHA-256 is not available", exception); }
    }
    /** Descripción: Ejecuta la operación «cachePath» correspondiente a la responsabilidad de SignatureValue. Entradas: contentHash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path cachePath(String contentHash) { return properties.getCacheDirectory().resolve(contentHash.toLowerCase() + ".hydra-cap"); }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de SignatureValue. Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileName(HydraCapabilityPackageManifest manifest) {
        String base = manifest.id().replaceAll("[^A-Za-z0-9._-]", "-");
        return (base.isBlank() ? "capabilities" : base) + "-" + (manifest.packageVersion().isBlank() ? "1.0.0" : manifest.packageVersion()) + ".hydra-cap";
    }
    /** Descripción: Ejecuta la operación «kind» correspondiente a la responsabilidad de SignatureValue. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String kind(String path) { return path.equals(MANIFEST_PATH) ? "MANIFEST" : path.startsWith("canonical/") ? "CANONICAL_DOCUMENT" : "RESOURCE"; }
    /** Descripción: Ejecuta la operación «diagnosticCodes» correspondiente a la responsabilidad de SignatureValue. Entradas: report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String diagnosticCodes(CapabilityPackageCheckReport report) { return report.diagnostics().stream().map(value -> value.code()).distinct().sorted().toList().toString(); }
    /** Descripción: Ejecuta la operación «invalidPackage» correspondiente a la responsabilidad de SignatureValue. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilityPackageArchiveException invalidPackage(String message) { return new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.INVALID_PACKAGE, message); }
    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() { if(!properties.isEnabled()) throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.DISABLED, "Capability package archives are disabled"); }
    private record SignatureValue(String algorithm, String keyId, String value) { }
}

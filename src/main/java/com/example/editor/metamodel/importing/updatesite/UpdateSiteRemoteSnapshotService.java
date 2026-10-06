package com.example.editor.metamodel.importing.updatesite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Creates, imports, exports, replays and checks content-addressed remote p2 snapshots. */
@Service
public class UpdateSiteRemoteSnapshotService {
    private static final String LOCK_FILE = "hydra-p2-lock.json";
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteRemoteRepositoryService remote;
    private final UpdateSiteImportApplicationService imports;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de UpdateSiteRemoteSnapshotService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; remote: dato de entrada necesario para la operación; imports: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRemoteSnapshotService(UpdateSiteImportProperties properties,
            UpdateSiteRemoteRepositoryService remote, UpdateSiteImportApplicationService imports,
            ObjectMapper mapper) {
        this.properties = properties;
        this.remote = remote;
        this.imports = imports;
        this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de UpdateSiteRemoteSnapshotService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotResult create(UpdateSiteRemoteAnalysisRequest request) {
        UpdateSiteRemoteCapture capture = remote.capture(request);
        Map<String, byte[]> byPath = new LinkedHashMap<>();
        capture.inputs().forEach(input -> byPath.put(input.path(), input.content()));
        List<UpdateSiteRemoteSnapshotResource> resources = capture.fetchedResources().stream()
                .sorted(Comparator.comparing(UpdateSiteRemoteRepositoryFetch::virtualPath))
                .map(fetch -> new UpdateSiteRemoteSnapshotResource(fetch.url(), fetch.virtualPath(), fetch.bytes(),
                        fetch.sha256(), fetch.depth(), objectEntry(fetch.sha256()))).toList();
        String treeHash = treeHash(resources);
        String snapshotId = "p2-" + treeHash.substring(0, 24);
        Path directory = snapshotDirectory(snapshotId);
        boolean reused = Files.isRegularFile(directory.resolve(LOCK_FILE));
        UpdateSiteRemoteSnapshotLock lock;
        if(reused) {
            lock = readLock(snapshotId);
            verifyStoredObjects(lock);
        } else {
            for(UpdateSiteRemoteSnapshotResource resource : resources) {
                byte[] content = byPath.get(resource.virtualPath());
                if(content == null || !UpdateSiteHashing.sha256(content).equals(resource.sha256())) {
                    throw new IllegalStateException("Remote snapshot content and evidence differ");
                }
                writeObject(resource.sha256(), content);
            }
            List<UpdateSiteRemoteSnapshotLicenseEntry> licenses = resources.stream()
                    .map(resource -> new UpdateSiteRemoteSnapshotLicenseEntry(resource.virtualPath(), "", resource.url(),
                            "", "", false, false)).toList();
            lock = new UpdateSiteRemoteSnapshotLock("1.0", snapshotId, capture.rootUrl(), treeHash,
                    Instant.now(), resources.stream().mapToLong(UpdateSiteRemoteSnapshotResource::bytes).sum(), resources, licenses);
            validateLock(lock);
            writeLock(lock);
        }
        UpdateSiteImportAnalysis analysis = imports.analyze(inputs(lock), request.options());
        return new UpdateSiteRemoteSnapshotResult(lock, analysis, reused, capture.diagnostics());
    }

    /** Descripción: Ejecuta la operación «importSnapshot» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: archive: archivo o contenido que debe procesarse; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotResult importSnapshot(byte[] archive, UpdateSiteImportOptions options) {
        if(archive == null || archive.length == 0) throw new IllegalArgumentException("Snapshot archive is required");
        if(archive.length > properties.getMaxUploadBytes()) throw new IllegalArgumentException("Snapshot archive exceeds the upload limit");
        return importSnapshot(new ByteArrayInputStream(archive), archive.length, options);
    }

    /** Imports a staged multipart snapshot without materializing the uploaded archive in the controller heap. */
    /** Descripción: Ejecuta la operación «importSnapshot» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: archive: archivo o contenido que debe procesarse; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotResult importSnapshot(UpdateSiteInputFile archive, UpdateSiteImportOptions options) {
        if(archive == null || archive.size() == 0) throw new IllegalArgumentException("Snapshot archive is required");
        if(archive.size() > properties.getMaxUploadBytes()) throw new IllegalArgumentException("Snapshot archive exceeds the upload limit");
        try(InputStream input = archive.openStream()) {
            return importSnapshot(input, archive.size(), options);
        } catch(IOException exception) {
            throw new IllegalArgumentException("Could not close snapshot archive", exception);
        }
    }

    /** Descripción: Ejecuta la operación «importSnapshot» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: archive: archivo o contenido que debe procesarse; archiveBytes: archivo o contenido que debe procesarse; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteRemoteSnapshotResult importSnapshot(InputStream archive, long archiveBytes, UpdateSiteImportOptions options) {
        if(archiveBytes <= 0) throw new IllegalArgumentException("Snapshot archive is required");
        Map<String, byte[]> entries = readArchive(archive);
        byte[] lockBytes = entries.get(LOCK_FILE);
        if(lockBytes == null) throw new IllegalArgumentException("Snapshot archive does not contain " + LOCK_FILE);
        UpdateSiteRemoteSnapshotLock lock;
        try {
            lock = mapper.readValue(lockBytes, UpdateSiteRemoteSnapshotLock.class);
        } catch(JacksonException exception) {
            throw new IllegalArgumentException("Snapshot lockfile is invalid", exception);
        }
        validateLock(lock);
        Set<String> expectedEntries = lock.resources().stream().map(UpdateSiteRemoteSnapshotResource::objectPath)
                .collect(java.util.stream.Collectors.toSet());
        if(entries.keySet().stream().anyMatch(name -> !LOCK_FILE.equals(name) && !expectedEntries.contains(name))) {
            throw new IllegalArgumentException("Snapshot archive contains files not declared by the lockfile");
        }
        long total = 0L;
        for(UpdateSiteRemoteSnapshotResource resource : lock.resources()) {
            byte[] content = entries.get(resource.objectPath());
            if(content == null) throw new IllegalArgumentException("Snapshot object is missing: " + resource.objectPath());
            if(content.length != resource.bytes()) throw new IllegalArgumentException("Snapshot object size differs from the lockfile: " + resource.virtualPath());
            if(!UpdateSiteHashing.sha256(content).equals(resource.sha256())) throw new IllegalArgumentException("Snapshot object checksum mismatch: " + resource.virtualPath());
            total += content.length;
            if(total > properties.getRemoteMaxTotalBytes()) throw new IllegalArgumentException("Snapshot objects exceed the configured total size limit");
        }
        if(total != lock.totalBytes()) throw new IllegalArgumentException("Snapshot total byte count differs from the lockfile");
        boolean reused = Files.isRegularFile(snapshotDirectory(lock.snapshotId()).resolve(LOCK_FILE));
        if(reused) {
            UpdateSiteRemoteSnapshotLock existing = readLock(lock.snapshotId());
            verifyStoredObjects(existing);
            if(!existing.resources().equals(lock.resources())) {
                throw new IllegalArgumentException("Stored snapshot resources differ from the imported lockfile");
            }
            if(existing.licenseReviewComplete() && !lock.licenseReviewComplete()) {
                lock = new UpdateSiteRemoteSnapshotLock(lock.schemaVersion(), lock.snapshotId(), lock.rootUrl(),
                        lock.treeSha256(), lock.capturedAt(), lock.totalBytes(), lock.resources(), existing.licenses());
            }
        }
        for(UpdateSiteRemoteSnapshotResource resource : lock.resources()) writeObject(resource.sha256(), entries.get(resource.objectPath()));
        writeLock(lock);
        UpdateSiteImportOptions safeOptions = options == null ? UpdateSiteImportOptions.defaults() : options;
        UpdateSiteImportAnalysis analysis = imports.analyze(inputs(lock), safeOptions);
        return new UpdateSiteRemoteSnapshotResult(lock, analysis, reused,
                List.of(reused ? "The imported snapshot was verified against the existing content-addressed tree."
                        : "Snapshot imported and verified for offline analysis."));
    }

    /** Descripción: Ejecuta la operación «operations» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotOperations operations(String snapshotId) {
        UpdateSiteRemoteSnapshotLock lock = readLock(snapshotId);
        verifyStoredObjects(lock);
        return new UpdateSiteRemoteSnapshotOperations(lock.snapshotId(), inputs(lock));
    }

    /** Descripción: Ejecuta la operación «analyzeOffline» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportAnalysis analyzeOffline(UpdateSiteRemoteSnapshotAnalyzeRequest request) {
        if(request == null || request.snapshotId().isBlank()) throw new IllegalArgumentException("snapshotId is required");
        UpdateSiteRemoteSnapshotLock lock = readLock(request.snapshotId());
        verifyStoredObjects(lock);
        return imports.analyze(inputs(lock), request.options());
    }

    /** Descripción: Ejecuta la operación «reviewLicenses» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotLock reviewLicenses(String snapshotId, UpdateSiteRemoteSnapshotLicenseReviewRequest request) {
        UpdateSiteRemoteSnapshotLock lock = readLock(snapshotId);
        verifyStoredObjects(lock);
        List<UpdateSiteRemoteSnapshotLicenseEntry> licenses = request == null ? List.of() : request.licenses();
        verifyLicenseReview(lock.resources(), licenses);
        UpdateSiteRemoteSnapshotLock reviewed = new UpdateSiteRemoteSnapshotLock(lock.schemaVersion(), lock.snapshotId(),
                lock.rootUrl(), lock.treeSha256(), lock.capturedAt(), lock.totalBytes(), lock.resources(), licenses);
        writeLock(reviewed);
        return reviewed;
    }

    /** Descripción: Ejecuta la operación «exportSnapshot» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] exportSnapshot(String snapshotId) {
        UpdateSiteRemoteSnapshotLock lock = readLock(snapshotId);
        verifyStoredObjects(lock);
        verifyLicenseReview(lock.resources(), lock.licenses());
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put(LOCK_FILE, write(lock));
        for(UpdateSiteRemoteSnapshotResource resource : lock.resources()) entries.put(resource.objectPath(), readObject(resource.sha256()));
        try(ByteArrayOutputStream buffer = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for(Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey());
                item.setTime(0L);
                zip.putNextEntry(item);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return buffer.toByteArray();
        } catch(IOException exception) {
            throw new IllegalStateException("Could not export remote p2 snapshot", exception);
        }
    }

    /** Descripción: Ejecuta la operación «drift» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotDriftReport drift(String snapshotId) {
        UpdateSiteRemoteSnapshotLock lock = readLock(snapshotId);
        List<UpdateSiteRemoteSnapshotDriftItem> items = new ArrayList<>();
        int unchanged = 0; int changed = 0; int unavailable = 0;
        for(UpdateSiteRemoteSnapshotResource resource : lock.resources()) {
            try {
                byte[] current = remote.downloadForDrift(resource.url());
                if(current == null) {
                    unavailable++;
                    items.add(new UpdateSiteRemoteSnapshotDriftItem(resource.url(), resource.sha256(), "", "UNAVAILABLE", "The locked resource now returns 404"));
                    continue;
                }
                String actual = UpdateSiteHashing.sha256(current);
                if(actual.equals(resource.sha256())) {
                    unchanged++;
                    items.add(new UpdateSiteRemoteSnapshotDriftItem(resource.url(), resource.sha256(), actual, "UNCHANGED", "Remote bytes still match the lockfile"));
                } else {
                    changed++;
                    items.add(new UpdateSiteRemoteSnapshotDriftItem(resource.url(), resource.sha256(), actual, "CHANGED", "Remote bytes differ from the stored object"));
                }
            } catch(RuntimeException exception) {
                unavailable++;
                items.add(new UpdateSiteRemoteSnapshotDriftItem(resource.url(), resource.sha256(), "", "UNAVAILABLE", safeMessage(exception)));
            }
        }
        return new UpdateSiteRemoteSnapshotDriftReport(snapshotId, changed > 0 || unavailable > 0,
                unchanged, changed, unavailable, items);
    }

    /** Returns verified snapshot bytes keyed by their original virtual path for mirror generation. */
    /** Descripción: Ejecuta la operación «verifiedResources» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String, byte[]> verifiedResources(String snapshotId) {
        UpdateSiteRemoteSnapshotLock lock = readLock(snapshotId);
        verifyStoredObjects(lock);
        Map<String, byte[]> result = new TreeMap<>();
        for(UpdateSiteRemoteSnapshotResource resource : lock.resources()) {
            result.put(resource.virtualPath(), readObject(resource.sha256()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotLock find(String snapshotId) {
        UpdateSiteRemoteSnapshotLock lock = readLock(snapshotId);
        verifyStoredObjects(lock);
        return lock;
    }

    /** Descripción: Obtiene la información correspondiente a «readArchive» dentro de UpdateSiteRemoteSnapshotService. Entradas: archive: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, byte[]> readArchive(InputStream archive) {
        Map<String, byte[]> entries = new HashMap<>();
        long total = 0L; int count = 0;
        try(ZipInputStream zip = new ZipInputStream(archive, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null) {
                if(entry.isDirectory()) continue;
                if(++count > properties.getMaxEntries()) throw new IllegalArgumentException("Snapshot archive exceeds the entry limit");
                String name = safeEntry(entry.getName());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int read; long entryBytes = 0L;
                while((read = zip.read(buffer)) >= 0) {
                    if(read == 0) continue;
                    entryBytes += read; total += read;
                    if(entryBytes > properties.getMaxEntryBytes()) throw new IllegalArgumentException("Snapshot entry exceeds the configured size limit: " + name);
                    if(total > properties.getRemoteMaxTotalBytes() + properties.getMaxEntryBytes()) throw new IllegalArgumentException("Snapshot archive exceeds the expanded size limit");
                    output.write(buffer, 0, read);
                }
                if(entries.putIfAbsent(name, output.toByteArray()) != null) throw new IllegalArgumentException("Snapshot archive contains a duplicate entry: " + name);
            }
        } catch(IOException exception) {
            throw new IllegalArgumentException("Could not read snapshot archive", exception);
        }
        return entries;
    }

    /** Descripción: Ejecuta la operación «inputs» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: lock: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteInputFile> inputs(UpdateSiteRemoteSnapshotLock lock) {
        return lock.resources().stream().sorted(Comparator.comparing(UpdateSiteRemoteSnapshotResource::virtualPath))
                .map(resource -> {
                    byte[] content = readObject(resource.sha256());
                    if(!UpdateSiteHashing.sha256(content).equals(resource.sha256())) throw new IllegalStateException("Snapshot object checksum mismatch: " + resource.sha256());
                    return new UpdateSiteInputFile(resource.virtualPath(), content);
                }).toList();
    }

    /** Descripción: Valida las condiciones necesarias para «validateLock» y rechaza entradas no válidas. Entradas: lock: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateLock(UpdateSiteRemoteSnapshotLock lock) {
        if(lock == null) throw new IllegalArgumentException("Snapshot lockfile is required");
        if(!"1.0".equals(lock.schemaVersion())) throw new IllegalArgumentException("Unsupported snapshot lock schema: " + lock.schemaVersion());
        if(lock.snapshotId() == null || !lock.snapshotId().matches("p2-[a-f0-9]{24}")) throw new IllegalArgumentException("Invalid snapshotId");
        if(lock.treeSha256() == null || !lock.treeSha256().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid snapshot tree checksum");
        validateRootUrl(lock.rootUrl());
        if(lock.resources().isEmpty()) throw new IllegalArgumentException("Snapshot lockfile contains no resources");
        if(lock.resources().size() > properties.getRemoteMaxResources()) throw new IllegalArgumentException("Snapshot resource count exceeds the configured limit");
        if(lock.totalBytes() < 0 || lock.totalBytes() > properties.getRemoteMaxTotalBytes()) throw new IllegalArgumentException("Snapshot total byte count exceeds the configured limit");
        Set<String> virtualPaths = new java.util.HashSet<>();
        Set<String> urls = new java.util.HashSet<>();
        long calculatedBytes = 0L;
        for(UpdateSiteRemoteSnapshotResource resource : lock.resources()) {
            validateResource(resource);
            if(!virtualPaths.add(resource.virtualPath())) throw new IllegalArgumentException("Snapshot contains a duplicate virtual path: " + resource.virtualPath());
            if(!urls.add(resource.url())) throw new IllegalArgumentException("Snapshot contains a duplicate resource URL: " + resource.url());
            calculatedBytes += resource.bytes();
            if(calculatedBytes > properties.getRemoteMaxTotalBytes()) throw new IllegalArgumentException("Snapshot resources exceed the configured total size limit");
        }
        if(calculatedBytes != lock.totalBytes()) throw new IllegalArgumentException("Snapshot total byte count does not match its resources");
        Set<String> licensePaths = new java.util.HashSet<>();
        for(UpdateSiteRemoteSnapshotLicenseEntry license : lock.licenses()) {
            if(license == null || license.virtualPath().isBlank()) throw new IllegalArgumentException("Snapshot license entry requires virtualPath");
            if(!virtualPaths.contains(license.virtualPath())) throw new IllegalArgumentException("Snapshot license entry references an unknown resource: " + license.virtualPath());
            if(!licensePaths.add(license.virtualPath())) throw new IllegalArgumentException("Snapshot contains duplicate license metadata: " + license.virtualPath());
        }
        if(!treeHash(lock.resources()).equals(lock.treeSha256())) throw new IllegalArgumentException("Snapshot tree checksum does not match its resources");
        if(!("p2-" + lock.treeSha256().substring(0, 24)).equals(lock.snapshotId())) throw new IllegalArgumentException("snapshotId does not match the tree checksum");
    }

    /** Descripción: Valida las condiciones necesarias para «validateResource» y rechaza entradas no válidas. Entradas: resource: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateResource(UpdateSiteRemoteSnapshotResource resource) {
        if(resource == null) throw new IllegalArgumentException("Snapshot resource is required");
        if(resource.virtualPath() == null || resource.virtualPath().isBlank() || resource.virtualPath().startsWith("/")
                || resource.virtualPath().contains("../") || resource.virtualPath().contains("\\")) throw new IllegalArgumentException("Unsafe snapshot virtual path");
        if(resource.sha256() == null || !resource.sha256().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid snapshot object checksum");
        if(resource.bytes() < 0 || resource.bytes() > properties.getRemoteMaxBytes()) throw new IllegalArgumentException("Snapshot object exceeds the configured resource size limit");
        if(resource.depth() < 0 || resource.depth() > properties.getRemoteMaxDepth()) throw new IllegalArgumentException("Snapshot resource depth exceeds the configured limit");
        if(!objectEntry(resource.sha256()).equals(resource.objectPath())) throw new IllegalArgumentException("Snapshot object path does not match its checksum");
        URI uri;
        try { uri = URI.create(resource.url()).normalize(); }
        catch(RuntimeException exception) { throw new IllegalArgumentException("Invalid locked resource URL", exception); }
        if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null) throw new IllegalArgumentException("Locked resource URL must be an absolute HTTPS URL");
    }

    /** Descripción: Valida las condiciones necesarias para «validateRootUrl» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateRootUrl(String value) {
        URI uri;
        try { uri = URI.create(value == null ? "" : value).normalize(); }
        catch(RuntimeException exception) { throw new IllegalArgumentException("Invalid snapshot root URL", exception); }
        if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("Snapshot root URL must be an absolute HTTPS URL");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyLicenseReview» y rechaza entradas no válidas. Entradas: resources: dato de entrada necesario para la operación; licenses: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyLicenseReview(List<UpdateSiteRemoteSnapshotResource> resources,
            List<UpdateSiteRemoteSnapshotLicenseEntry> licenses) {
        Map<String, UpdateSiteRemoteSnapshotLicenseEntry> byPath = new LinkedHashMap<>();
        for(UpdateSiteRemoteSnapshotLicenseEntry license : licenses == null ? List.<UpdateSiteRemoteSnapshotLicenseEntry>of() : licenses) {
            if(license == null || license.virtualPath().isBlank()) throw new IllegalArgumentException("Every license entry requires virtualPath");
            if(byPath.putIfAbsent(license.virtualPath(), license) != null) throw new IllegalArgumentException("Duplicate license review for " + license.virtualPath());
        }
        List<String> incomplete = new ArrayList<>();
        for(UpdateSiteRemoteSnapshotResource resource : resources) {
            UpdateSiteRemoteSnapshotLicenseEntry license = byPath.get(resource.virtualPath());
            if(license == null || !license.complete()) incomplete.add(resource.virtualPath());
        }
        if(byPath.size() != resources.size() || !incomplete.isEmpty()) {
            throw new IllegalArgumentException("Snapshot export requires compatible, redistribution-approved license metadata for every resource: " + String.join(", ", incomplete));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyStoredObjects» y rechaza entradas no válidas. Entradas: lock: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyStoredObjects(UpdateSiteRemoteSnapshotLock lock) {
        for(UpdateSiteRemoteSnapshotResource resource : lock.resources()) {
            byte[] content = readObject(resource.sha256());
            if(content.length != resource.bytes() || !UpdateSiteHashing.sha256(content).equals(resource.sha256())) {
                throw new IllegalStateException("Stored snapshot object no longer matches the lockfile: " + resource.virtualPath());
            }
        }
    }

    /** Descripción: Ejecuta la operación «treeHash» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String treeHash(List<UpdateSiteRemoteSnapshotResource> resources) {
        StringBuilder value = new StringBuilder();
        resources.stream().sorted(Comparator.comparing(UpdateSiteRemoteSnapshotResource::virtualPath)
                .thenComparing(UpdateSiteRemoteSnapshotResource::url)).forEach(resource -> value.append(resource.virtualPath()).append('\u0000')
                .append(resource.url()).append('\u0000').append(resource.bytes()).append('\u0000').append(resource.sha256()).append('\n'));
        return UpdateSiteHashing.sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteRemoteSnapshotService mediante la operación «writeObject». Entradas: hash: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeObject(String hash, byte[] content) {
        if(content == null || !UpdateSiteHashing.sha256(content).equals(hash)) throw new IllegalArgumentException("Snapshot object checksum mismatch");
        Path target = objectPath(hash);
        if(Files.isRegularFile(target)) {
            byte[] existing = readObject(hash);
            if(!UpdateSiteHashing.sha256(existing).equals(hash)) throw new IllegalStateException("Existing snapshot object is corrupt: " + hash);
            return;
        }
        atomicWrite(target, content);
    }

    /** Descripción: Obtiene la información correspondiente a «readObject» dentro de UpdateSiteRemoteSnapshotService. Entradas: hash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readObject(String hash) {
        try { return com.example.editor.io.BoundedIo.readInternal(objectPath(hash)); }
        catch(IOException exception) { throw new IllegalStateException("Snapshot object is missing: " + hash, exception); }
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteRemoteSnapshotService mediante la operación «writeLock». Entradas: lock: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeLock(UpdateSiteRemoteSnapshotLock lock) {
        validateLock(lock);
        atomicWrite(snapshotDirectory(lock.snapshotId()).resolve(LOCK_FILE), write(lock));
    }

    /** Descripción: Obtiene la información correspondiente a «readLock» dentro de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteRemoteSnapshotLock readLock(String snapshotId) {
        try {
            UpdateSiteRemoteSnapshotLock lock = mapper.readValue(com.example.editor.io.BoundedIo.readInternal(snapshotDirectory(snapshotId).resolve(LOCK_FILE)), UpdateSiteRemoteSnapshotLock.class);
            validateLock(lock);
            return lock;
        } catch(JacksonException | IOException exception) {
            throw new IllegalArgumentException("Remote p2 snapshot was not found or is invalid", exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteRemoteSnapshotService mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value) {
        try { return writer.writeValueAsBytes(value); }
        catch(RuntimeException exception) { throw new IllegalStateException("Could not serialize remote p2 snapshot", exception); }
    }

    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content){try{AtomicFiles.writeReplacing(target,content);}catch(IOException exception){throw new IllegalStateException("Could not persist remote p2 snapshot", exception);}}

    /** Descripción: Ejecuta la operación «snapshotDirectory» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path snapshotDirectory(String snapshotId) {
        String safe = safeId(snapshotId);
        Path root = properties.storageRootPath().resolve("remote-snapshots").normalize();
        Path result = root.resolve(safe).normalize();
        if(!result.startsWith(root)) throw new IllegalArgumentException("Unsafe snapshotId");
        return result;
    }

    /** Descripción: Ejecuta la operación «objectPath» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: hash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path objectPath(String hash) {
        if(hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid snapshot object hash");
        Path root = properties.storageRootPath().resolve("remote-snapshot-objects").normalize();
        Path result = root.resolve(hash.substring(0, 2)).resolve(hash + ".bin").normalize();
        if(!result.startsWith(root)) throw new IllegalArgumentException("Unsafe snapshot object hash");
        return result;
    }

    /** Descripción: Ejecuta la operación «safeId» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeId(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        if(safe.isBlank()) throw new IllegalArgumentException("snapshotId is required");
        return safe;
    }

    /** Descripción: Ejecuta la operación «safeEntry» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeEntry(String value) {
        String name = value == null ? "" : value.replace('\\', '/');
        if(name.isBlank() || name.startsWith("/") || name.contains("../") || name.contains("/..")) throw new IllegalArgumentException("Unsafe snapshot archive entry");
        return name;
    }

    /** Descripción: Ejecuta la operación «objectEntry» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: hash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String objectEntry(String hash) { return "objects/" + hash + ".bin"; }
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Remote resource could not be checked" : exception.getMessage();
    }
}

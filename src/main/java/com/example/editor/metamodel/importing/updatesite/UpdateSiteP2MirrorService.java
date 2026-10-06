package com.example.editor.metamodel.importing.updatesite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Builds deterministic, license-gated p2 mirrors from verified repository snapshots. */
@Service
public class UpdateSiteP2MirrorService {
    private static final String MANIFEST = "hydra-p2-mirror.json";
    private static final String LOCK = "hydra-p2-lock.json";
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteRemoteSnapshotService snapshots;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de UpdateSiteP2MirrorService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; snapshots: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteP2MirrorService(UpdateSiteImportProperties properties,
            UpdateSiteRemoteSnapshotService snapshots, ObjectMapper mapper) {
        this.properties = properties; this.snapshots = snapshots; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de MirrorArtifactSource. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteP2MirrorResult create(String snapshotId) {
        UpdateSiteRemoteSnapshotLock lock = snapshots.find(snapshotId);
        if(!lock.licenseReviewComplete()) {
            throw new IllegalArgumentException("Offline mirror publication requires a complete compatible license review for every snapshot resource");
        }
        Map<String, byte[]> source = snapshots.verifiedResources(snapshotId);
        List<MirrorArtifactSource> artifactSources = artifactSources(lock, source);
        if(artifactSources.isEmpty()) {
            throw new IllegalArgumentException("The verified snapshot does not contain any plugin or feature artifact to mirror");
        }
        List<UpdateSiteP2MirrorArtifact> artifacts = artifactSources.stream()
                .map(MirrorArtifactSource::artifact).toList();
        byte[] contentIndex = contentIndex(artifacts);
        byte[] artifactIndex = artifactIndex(artifacts);
        Map<String, byte[]> entries = new TreeMap<>();
        Set<String> mirroredSourcePaths = artifactSources.stream().map(MirrorArtifactSource::sourcePath)
                .collect(java.util.stream.Collectors.toSet());
        source.forEach((path, bytes) -> {
            String safe = safeEntry(path);
            if(!mirroredSourcePaths.contains(safe) && !"content.xml".equals(safe) && !"artifacts.xml".equals(safe)
                    && !"content.jar".equals(safe) && !"artifacts.jar".equals(safe)) entries.put(safe, bytes);
        });
        artifactSources.forEach(item -> putUnique(entries, item.artifact().path(), item.bytes()));
        entries.put("content.xml", contentIndex); entries.put("artifacts.xml", artifactIndex);
        entries.put(LOCK, write(lock));
        String mirrorHash = entryHash(entries);
        String mirrorId = "p2m-" + mirrorHash.substring(0, 24);
        UpdateSiteP2MirrorManifest manifest = new UpdateSiteP2MirrorManifest("1.0", mirrorId,
                lock.snapshotId(), lock.treeSha256(), mirrorHash, lock.capturedAt(), artifacts,
                UpdateSiteHashing.sha256(contentIndex), UpdateSiteHashing.sha256(artifactIndex), true);
        entries.put(MANIFEST, write(manifest));
        byte[] archive = zip(entries);
        Path archivePath = archivePath(mirrorId);
        boolean reused = Files.isRegularFile(archivePath);
        if(reused) {
            byte[] current = read(archivePath);
            UpdateSiteP2MirrorValidation currentValidation = validateArchive(current, manifest);
            if(!currentValidation.valid()) throw new IllegalStateException("Stored p2 mirror is corrupt: " + String.join("; ", currentValidation.diagnostics()));
            if(!UpdateSiteHashing.sha256(current).equals(UpdateSiteHashing.sha256(archive))) {
                throw new IllegalStateException("The same mirror identifier resolved to different archive bytes");
            }
        } else atomicWrite(archivePath, archive);
        atomicWrite(manifestPath(mirrorId), write(manifest));
        UpdateSiteP2MirrorValidation validation = validateArchive(archive, manifest);
        if(!validation.valid()) throw new IllegalStateException("Generated p2 mirror failed its consumption test: " + String.join("; ", validation.diagnostics()));
        return new UpdateSiteP2MirrorResult(manifest, validation, reused);
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de MirrorArtifactSource. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteP2MirrorResult find(String mirrorId) {
        UpdateSiteP2MirrorManifest manifest = readManifest(mirrorId);
        UpdateSiteP2MirrorValidation validation = validateArchive(read(archivePath(mirrorId)), manifest);
        return new UpdateSiteP2MirrorResult(manifest, validation, true);
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteP2MirrorValidation validate(String mirrorId) {
        UpdateSiteP2MirrorManifest manifest = readManifest(mirrorId);
        return validateArchive(read(archivePath(mirrorId)), manifest);
    }

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] export(String mirrorId) {
        UpdateSiteP2MirrorResult result = find(mirrorId);
        if(!result.manifest().licenseReviewComplete()) throw new IllegalArgumentException("Mirror license review is incomplete");
        if(!result.validation().valid()) throw new IllegalStateException("Mirror validation failed");
        return read(archivePath(mirrorId));
    }

    /** Descripción: Ejecuta la operación «artifactSources» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: lock: dato de entrada necesario para la operación; resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MirrorArtifactSource> artifactSources(UpdateSiteRemoteSnapshotLock lock,
            Map<String, byte[]> resources) {
        Map<String, UpdateSiteRemoteSnapshotLicenseEntry> licenses = new LinkedHashMap<>();
        lock.licenses().forEach(item -> licenses.put(safeEntry(item.virtualPath()), item));
        List<MirrorArtifactSource> result = new ArrayList<>();
        resources.entrySet().stream().filter(entry -> entry.getKey().endsWith(".jar"))
                .filter(entry -> entry.getKey().contains("plugins/") || entry.getKey().contains("features/"))
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    String sourcePath = safeEntry(entry.getKey());
                    String classifier = sourcePath.contains("features/")
                            ? "org.eclipse.update.feature" : "osgi.bundle";
                    String file = sourcePath.substring(sourcePath.lastIndexOf('/') + 1, sourcePath.length() - 4);
                    int separator = file.lastIndexOf('_');
                    String id = separator > 0 ? file.substring(0, separator) : file;
                    String version = separator > 0 && separator + 1 < file.length()
                            ? file.substring(separator + 1) : "0.0.0";
                    String mirrorPath = ("org.eclipse.update.feature".equals(classifier) ? "features/" : "plugins/")
                            + id + "_" + version + ".jar";
                    UpdateSiteRemoteSnapshotLicenseEntry license = licenses.get(sourcePath);
                    String licenseName = license == null ? "" : license.license();
                    UpdateSiteP2MirrorArtifact artifact = new UpdateSiteP2MirrorArtifact(classifier, id, version,
                            mirrorPath, entry.getValue().length, UpdateSiteHashing.sha256(entry.getValue()), licenseName);
                    result.add(new MirrorArtifactSource(sourcePath, artifact, entry.getValue()));
                });
        result.sort(Comparator.comparing(item -> item.artifact().path()));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «contentIndex» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] contentIndex(List<UpdateSiteP2MirrorArtifact> artifacts) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<repository name=\"HYDRA offline mirror\" type=\"org.eclipse.equinox.internal.p2.metadata.repository.LocalMetadataRepository\" version=\"1\">\n");
        xml.append("  <properties size=\"1\"><property name=\"p2.timestamp\" value=\"0\"/></properties>\n");
        xml.append("  <units size=\"").append(artifacts.size()).append("\">\n");
        for(UpdateSiteP2MirrorArtifact artifact : artifacts) {
            xml.append("    <unit id=\"").append(escapeXml(artifact.id())).append("\" version=\"")
                    .append(escapeXml(artifact.version())).append("\">\n");
            xml.append("      <artifacts size=\"1\"><artifact classifier=\"").append(artifact.classifier())
                    .append("\" id=\"").append(escapeXml(artifact.id())).append("\" version=\"")
                    .append(escapeXml(artifact.version())).append("\"/></artifacts>\n");
            xml.append("      <properties size=\"2\"><property name=\"hydra.path\" value=\"")
                    .append(escapeXml(artifact.path())).append("\"/><property name=\"hydra.sha256\" value=\"")
                    .append(artifact.sha256()).append("\"/></properties>\n");
            xml.append("    </unit>\n");
        }
        xml.append("  </units>\n</repository>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «artifactIndex» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] artifactIndex(List<UpdateSiteP2MirrorArtifact> artifacts) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<repository name=\"HYDRA offline mirror\" type=\"org.eclipse.equinox.internal.p2.artifact.repository.simpleRepository\" version=\"1\">\n");
        xml.append("  <properties size=\"1\"><property name=\"p2.timestamp\" value=\"0\"/></properties>\n");
        xml.append("  <mappings size=\"2\"><rule filter=\"(classifier=osgi.bundle)\" output=\"${repoUrl}/plugins/${id}_${version}.jar\"/>")
                .append("<rule filter=\"(classifier=org.eclipse.update.feature)\" output=\"${repoUrl}/features/${id}_${version}.jar\"/></mappings>\n");
        xml.append("  <artifacts size=\"").append(artifacts.size()).append("\">\n");
        for(UpdateSiteP2MirrorArtifact artifact : artifacts) {
            xml.append("    <artifact classifier=\"").append(artifact.classifier()).append("\" id=\"")
                    .append(escapeXml(artifact.id())).append("\" version=\"").append(escapeXml(artifact.version())).append("\">\n");
            xml.append("      <properties size=\"4\"><property name=\"path\" value=\"")
                    .append(escapeXml(artifact.path())).append("\"/><property name=\"download.size\" value=\"")
                    .append(artifact.bytes()).append("\"/><property name=\"artifact.size\" value=\"")
                    .append(artifact.bytes()).append("\"/><property name=\"download.checksum.sha-256\" value=\"")
                    .append(artifact.sha256()).append("\"/></properties>\n");
            xml.append("    </artifact>\n");
        }
        xml.append("  </artifacts>\n</repository>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Valida las condiciones necesarias para «validateArchive» y rechaza entradas no válidas. Entradas: archive: archivo o contenido que debe procesarse; expected: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteP2MirrorValidation validateArchive(byte[] archive, UpdateSiteP2MirrorManifest expected) {
        List<String> diagnostics = new ArrayList<>(); List<String> missing = new ArrayList<>();
        Map<String, byte[]> entries;
        try { entries = unzip(archive); }
        catch(RuntimeException exception) { return new UpdateSiteP2MirrorValidation(false, 0, 0, 0, List.of(), List.of(exception.getMessage())); }
        byte[] content = entries.get("content.xml"); byte[] artifacts = entries.get("artifacts.xml");
        if(content == null) diagnostics.add("content.xml is missing");
        if(artifacts == null) diagnostics.add("artifacts.xml is missing");
        int units = content == null ? 0 : count(content, "unit", diagnostics);
        int declaredArtifacts = artifacts == null ? 0 : count(artifacts, "artifact", diagnostics);
        int verified = 0;
        for(UpdateSiteP2MirrorArtifact artifact : expected.artifacts()) {
            byte[] bytes = entries.get(artifact.path());
            if(bytes == null) missing.add(artifact.path());
            else if(bytes.length != artifact.bytes() || !UpdateSiteHashing.sha256(bytes).equals(artifact.sha256())) {
                diagnostics.add("Artifact checksum or size mismatch: " + artifact.path());
            } else verified++;
        }
        if(!missing.isEmpty()) diagnostics.add("Mirror is missing " + missing.size() + " declared artifact(s)");
        if(units != expected.artifacts().size()) diagnostics.add("content.xml unit count differs from the mirror manifest");
        if(declaredArtifacts != expected.artifacts().size()) diagnostics.add("artifacts.xml artifact count differs from the mirror manifest");
        if(content != null && !UpdateSiteHashing.sha256(content).equals(expected.contentIndexSha256())) diagnostics.add("content.xml checksum differs from the mirror manifest");
        if(artifacts != null && !UpdateSiteHashing.sha256(artifacts).equals(expected.artifactIndexSha256())) diagnostics.add("artifacts.xml checksum differs from the mirror manifest");
        Map<String, byte[]> hashEntries = new TreeMap<>(entries); hashEntries.remove(MANIFEST);
        if(!entryHash(hashEntries).equals(expected.mirrorSha256())) diagnostics.add("Mirror tree checksum differs from the manifest");
        return new UpdateSiteP2MirrorValidation(diagnostics.isEmpty(), units, declaredArtifacts, verified, missing, diagnostics);
    }

    /** Descripción: Ejecuta la operación «count» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: xml: dato de entrada necesario para la operación; tag: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int count(byte[] xml, String tag, List<String> diagnostics) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            return document.getElementsByTagName(tag).getLength();
        } catch(Exception exception) { diagnostics.add("Could not parse " + tag + " index: " + safeMessage(exception)); return 0; }
    }

    /** Descripción: Ejecuta la operación «unzip» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: archive: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, byte[]> unzip(byte[] archive) {
        Map<String, byte[]> result = new TreeMap<>(); long total = 0L; int count = 0;
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null) {
                if(entry.isDirectory()) continue;
                if(++count > properties.getMaxEntries()) throw new IllegalArgumentException("Mirror archive exceeds the entry limit");
                String name = safeEntry(entry.getName());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int read; long entryBytes = 0L;
                while((read = zip.read(buffer)) >= 0) {
                    if(read == 0) continue;
                    entryBytes += read; total += read;
                    if(entryBytes > properties.getMaxEntryBytes()) throw new IllegalArgumentException("Mirror entry exceeds the configured size limit: " + name);
                    if(total > properties.getRemoteMaxTotalBytes() + properties.getMaxUploadBytes()) throw new IllegalArgumentException("Mirror archive exceeds the configured size limit");
                    output.write(buffer, 0, read);
                }
                if(result.putIfAbsent(name, output.toByteArray()) != null) throw new IllegalArgumentException("Mirror archive contains duplicate entries");
            }
        } catch(IOException exception) { throw new IllegalArgumentException("Could not read p2 mirror archive", exception); }
        return result;
    }

    /** Descripción: Ejecuta la operación «zip» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] zip(Map<String, byte[]> entries) {
        try(ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for(Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry item = new ZipEntry(safeEntry(entry.getKey())); item.setTime(0L); zip.putNextEntry(item);
                zip.write(entry.getValue()); zip.closeEntry();
            }
            zip.finish(); return output.toByteArray();
        } catch(IOException exception) { throw new IllegalStateException("Could not create p2 mirror archive", exception); }
    }

    /** Descripción: Ejecuta la operación «entryHash» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String entryHash(Map<String, byte[]> entries) {
        StringBuilder value = new StringBuilder();
        entries.forEach((name, bytes) -> value.append(name).append('\u0000').append(bytes.length).append('\u0000')
                .append(UpdateSiteHashing.sha256(bytes)).append('\n'));
        return UpdateSiteHashing.sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Ejecuta la operación «putUnique» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: entries: colección de elementos que debe procesarse; path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void putUnique(Map<String, byte[]> entries, String path, byte[] bytes) {
        byte[] previous = entries.putIfAbsent(safeEntry(path), bytes);
        if(previous != null && !java.util.Arrays.equals(previous, bytes)) {
            throw new IllegalArgumentException("Two snapshot resources normalize to the same p2 artifact path: " + path);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readManifest» dentro de MirrorArtifactSource. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteP2MirrorManifest readManifest(String mirrorId) {
        try { return mapper.readValue(read(manifestPath(mirrorId)), UpdateSiteP2MirrorManifest.class); }
        catch(JacksonException exception) { throw new IllegalArgumentException("p2 mirror manifest is invalid", exception); }
    }
    /** Descripción: Actualiza el estado gestionado por MirrorArtifactSource mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value) {try{return writer.writeValueAsBytes(value);}catch(RuntimeException exception){throw new IllegalStateException("Could not serialize p2 mirror metadata", exception);}}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de MirrorArtifactSource. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] read(Path path) {try{return com.example.editor.io.BoundedIo.readInternal(path);}catch(IOException exception){throw new IllegalArgumentException("p2 mirror was not found: " + path.getFileName(), exception);}}
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content){try{AtomicFiles.writeReplacing(target,content);}catch(IOException exception){throw new IllegalStateException("Could not persist p2 mirror", exception);}}
    /** Descripción: Ejecuta la operación «mirrorRoot» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path mirrorRoot(String mirrorId) {String safe = safeId(mirrorId); Path root = properties.storageRootPath().resolve("offline-mirrors").normalize(); Path result = root.resolve(safe).normalize(); if(!result.startsWith(root)) throw new IllegalArgumentException("Unsafe mirrorId"); return result;}
    /** Descripción: Ejecuta la operación «archivePath» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path archivePath(String mirrorId){return mirrorRoot(mirrorId).resolve("repository.zip");}
    /** Descripción: Ejecuta la operación «manifestPath» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path manifestPath(String mirrorId){return mirrorRoot(mirrorId).resolve(MANIFEST);}
    /** Descripción: Ejecuta la operación «safeId» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeId(String value){String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "-"); if(safe.isBlank()) throw new IllegalArgumentException("mirrorId is required"); return safe;}
    /** Descripción: Ejecuta la operación «safeEntry» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeEntry(String value){
        String name = value == null ? "" : value.replace('\\', '/');
        if(name.isBlank() || name.startsWith("/") || name.indexOf('\0') >= 0
                || name.matches("^[A-Za-z]:.*")) throw new IllegalArgumentException("Unsafe mirror entry");
        for(String segment : name.split("/")) {
            if(segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Unsafe mirror entry");
            }
        }
        return name;
    }
    /** Descripción: Ejecuta la operación «escapeXml» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escapeXml(String value){return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");}
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception){return exception == null || exception.getMessage() == null || exception.getMessage().isBlank() ? "unknown parsing error" : exception.getMessage();}
    private record MirrorArtifactSource(String sourcePath, UpdateSiteP2MirrorArtifact artifact, byte[] bytes) {
        private MirrorArtifactSource {bytes = bytes == null ? new byte[0] : bytes.clone();}
        /** Descripción: Ejecuta la operación «bytes» correspondiente a la responsabilidad de MirrorArtifactSource. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] bytes(){return bytes.clone();}
    }
}

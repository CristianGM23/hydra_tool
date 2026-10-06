package com.example.editor.metamodel.importing.updatesite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.example.editor.io.AtomicFiles;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

/** Descarga p2 remota con allowlist, límites, caché por contenido y redirecciones bloqueadas. */
@Service
public class UpdateSiteRemoteRepositoryService {
    private static final List<String> METADATA_FILES = List.of("compositeContent.jar", "compositeContent.xml",
            "compositeArtifacts.jar", "compositeArtifacts.xml", "content.jar", "content.xml",
            "artifacts.jar", "artifacts.xml", "site.xml");
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteImportApplicationService importService;
    private volatile HttpClient client;

    /** Descripción: Inicializa una instancia de UpdateSiteRemoteRepositoryService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; importService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRemoteRepositoryService(UpdateSiteImportProperties properties,
            UpdateSiteImportApplicationService importService) {
        this.properties = properties;
        this.importService = importService;
    }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de RepositoryNode. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteAnalysisResult analyze(UpdateSiteRemoteAnalysisRequest request) {
        UpdateSiteRemoteCapture capture = capture(request);
        UpdateSiteImportAnalysis analysis = importService.analyze(capture.inputs(), request.options());
        return new UpdateSiteRemoteAnalysisResult(analysis, capture.fetchedResources(), capture.downloadedBytes(),
                capture.cacheHits(), capture.diagnostics());
    }

    /** Descripción: Ejecuta la operación «capture» correspondiente a la responsabilidad de RepositoryNode. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteRemoteCapture capture(UpdateSiteRemoteAnalysisRequest request) {
        if(!properties.isRemoteEnabled()) throw new IllegalArgumentException("Remote update-site access is disabled");
        if(request == null || request.repositoryUrl().isBlank()) throw new IllegalArgumentException("repositoryUrl is required");
        URI root = validate(request.repositoryUrl());
        int maximumDepth = Math.min(request.maximumDepth() == 0 ? properties.getRemoteMaxDepth() : request.maximumDepth(),
                properties.getRemoteMaxDepth());
        List<UpdateSiteInputFile> inputs = new ArrayList<>();
        List<UpdateSiteRemoteRepositoryFetch> evidence = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        long[] bytes = { 0L }; long[] resolvedBytes = { 0L }; int[] cacheHits = { 0 }; int[] resources = { 0 };
        if(isArchive(root.getPath())) {
            Download download = download(root, request.useCache(), true);
            verifyExpected(download.content(), request.expectedSha256());
            register(download, root, fileName(root), 0, inputs, evidence, bytes, resolvedBytes, cacheHits, resources);
        } else {
            resolveRepositoryTree(root, maximumDepth, request.useCache(), inputs, evidence, diagnostics,
                    bytes, resolvedBytes, cacheHits, resources);
        }
        if(inputs.isEmpty()) throw new IllegalArgumentException("The remote repository exposed no supported p2 metadata");
        return new UpdateSiteRemoteCapture(root.toString(), inputs, evidence, bytes[0], cacheHits[0], diagnostics);
    }

    /** Descripción: Ejecuta la operación «downloadForDrift» correspondiente a la responsabilidad de RepositoryNode. Entradas: url: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ byte[] downloadForDrift(String url) {
        URI uri = validate(url);
        Download result = download(uri, false, false);
        return result.notFound() ? null : result.content();
    }

    /** Descripción: Obtiene la información correspondiente a «resolveRepositoryTree» dentro de RepositoryNode. Entradas: root: ruta o ubicación utilizada por la operación; maximumDepth: dato de entrada necesario para la operación; useCache: dato de entrada necesario para la operación; inputs: dato de entrada necesario para la operación; evidence: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; bytes: dato de entrada necesario para la operación; resolvedBytes: dato de entrada necesario para la operación; cacheHits: dato de entrada necesario para la operación; resources: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void resolveRepositoryTree(URI root, int maximumDepth, boolean useCache,
            List<UpdateSiteInputFile> inputs, List<UpdateSiteRemoteRepositoryFetch> evidence,
            List<String> diagnostics, long[] bytes, long[] resolvedBytes, int[] cacheHits, int[] resources) {
        ArrayDeque<RepositoryNode> queue = new ArrayDeque<>(); queue.add(new RepositoryNode(directory(root), 0));
        Set<String> visited = new HashSet<>(); Set<String> fetched = new HashSet<>(); int repositories = 0;
        int[] artifacts = { 0 };
        while(!queue.isEmpty()) {
            RepositoryNode node = queue.removeFirst();
            if(!visited.add(node.uri().toString())) continue;
            if(++repositories > properties.getRemoteMaxRepositories()) throw new IllegalArgumentException("Remote repository tree exceeds the configured repository limit");
            List<ArtifactCoordinate> repositoryArtifacts = new ArrayList<>();
            for(String metadataName : METADATA_FILES) {
                URI resource = validate(node.uri().resolve(metadataName).toString());
                if(!fetched.add(resource.toString())) continue;
                Download download = download(resource, useCache, false);
                if(download.notFound()) continue;
                String virtualPath = "remote/" + node.depth() + "/" + UpdateSiteHashing.sha256(node.uri().toString().getBytes(StandardCharsets.UTF_8)).substring(0, 12) + "/" + metadataName;
                register(download, resource, virtualPath, node.depth(), inputs, evidence, bytes,
                        resolvedBytes, cacheHits, resources);
                if(metadataName.startsWith("composite") && node.depth() < maximumDepth) {
                    byte[] composite = metadataName.endsWith(".jar")
                            ? zipEntry(download.content(), metadataName.startsWith("compositeContent")
                                    ? "compositeContent.xml" : "compositeArtifacts.xml")
                            : download.content();
                    for(String child : compositeChildren(composite)) {
                        URI childUri = validate(node.uri().resolve(child.endsWith("/") ? child : child + "/").toString());
                        queue.addLast(new RepositoryNode(directory(childUri), node.depth() + 1));
                    }
                }
                if(metadataName.equals("artifacts.xml") || metadataName.equals("artifacts.jar")) {
                    repositoryArtifacts.addAll(artifactCoordinates(metadataName, download.content()));
                }
            }
            for(ArtifactCoordinate artifact : repositoryArtifacts.stream().distinct().sorted().toList()) {
                if(artifacts[0] >= properties.getRemoteMaxArtifacts()) {
                    diagnostics.add("The remote artifact limit was reached; remaining plug-ins and features were not downloaded.");
                    break;
                }
                URI resource = validate(node.uri().resolve(artifact.relativePath()).toString());
                if(!fetched.add(resource.toString())) continue;
                Download download = download(resource, useCache, false);
                if(download.notFound()) {
                    diagnostics.add("Referenced p2 artifact was not available at its conventional path: " + artifact.relativePath());
                    continue;
                }
                String virtualPath = "remote/" + node.depth() + "/" + UpdateSiteHashing.sha256(node.uri().toString().getBytes(StandardCharsets.UTF_8)).substring(0, 12) + "/" + artifact.relativePath();
                register(download, resource, virtualPath, node.depth(), inputs, evidence, bytes,
                        resolvedBytes, cacheHits, resources);
                artifacts[0]++;
            }
        }
        if(repositories > 1) diagnostics.add("Resolved " + repositories + " repositories from the composite p2 tree.");
    }

    /** Descripción: Ejecuta la operación «download» correspondiente a la responsabilidad de RepositoryNode. Entradas: uri: dato de entrada necesario para la operación; useCache: dato de entrada necesario para la operación; required: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Download download(URI uri, boolean useCache, boolean required) {
        Cached cached = useCache && properties.isRemoteCacheEnabled() ? readCache(uri) : null;
        if(cached != null) return new Download(cached.content(), true, false, 0L);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.getRemoteRequestTimeoutMillis()))
                .header("Accept", "application/zip, application/xml, text/xml, application/octet-stream")
                .GET().build();
        try {
            HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream body = response.body()) {
                if(response.statusCode() == 404 && !required) return new Download(new byte[0], false, true, 0L);
                if(response.statusCode() >= 300 && response.statusCode() < 400) throw new IllegalArgumentException("Remote redirects are not allowed: " + uri);
                if(response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalArgumentException("Remote repository returned HTTP " + response.statusCode() + ": " + uri);
                long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if(declared > properties.getRemoteMaxBytes()) throw new IllegalArgumentException("Remote resource exceeds the configured size limit: " + uri);
                byte[] content = readLimited(body, properties.getRemoteMaxBytes(), "Remote resource exceeds the configured size limit: " + uri);
                if(useCache && properties.isRemoteCacheEnabled()) writeCache(uri, content);
                return new Download(content, false, false, content.length);
            }
        } catch(IOException exception) {
            throw new IllegalArgumentException("Could not download remote repository resource: " + uri, exception);
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Remote repository request was interrupted", exception);
        }
    }


    /** Descripción: Ejecuta la operación «client» correspondiente a la responsabilidad de RepositoryNode. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private HttpClient client() {
        HttpClient current = client;
        if(current != null) return current;
        synchronized(this) {
            current = client;
            if(current == null) {
                current = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getRemoteConnectTimeoutMillis()))
                        .followRedirects(HttpClient.Redirect.NEVER).build();
                client = current;
            }
            return current;
        }
    }

    /** Descripción: Ejecuta la operación «register» correspondiente a la responsabilidad de RepositoryNode. Entradas: download: dato de entrada necesario para la operación; uri: dato de entrada necesario para la operación; virtualPath: ruta o ubicación utilizada por la operación; depth: dato de entrada necesario para la operación; inputs: dato de entrada necesario para la operación; evidence: dato de entrada necesario para la operación; downloadedBytes: dato de entrada necesario para la operación; resolvedBytes: dato de entrada necesario para la operación; cacheHits: dato de entrada necesario para la operación; resources: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void register(Download download, URI uri, String virtualPath, int depth,
            List<UpdateSiteInputFile> inputs, List<UpdateSiteRemoteRepositoryFetch> evidence,
            long[] downloadedBytes, long[] resolvedBytes, int[] cacheHits, int[] resources) {
        if(++resources[0] > properties.getRemoteMaxResources()) {
            throw new IllegalArgumentException("Remote repository tree exceeds the configured resource limit");
        }
        resolvedBytes[0] += download.content().length;
        if(resolvedBytes[0] > properties.getRemoteMaxTotalBytes()) {
            throw new IllegalArgumentException("Remote repository tree exceeds the configured total size limit");
        }
        inputs.add(new UpdateSiteInputFile(virtualPath, download.content()));
        evidence.add(evidence(uri, virtualPath, download, depth));
        downloadedBytes[0] += download.downloadedBytes();
        if(download.cacheHit()) cacheHits[0]++;
    }

    /** Descripción: Ejecuta la operación «artifactCoordinates» correspondiente a la responsabilidad de RepositoryNode. Entradas: metadataName: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<ArtifactCoordinate> artifactCoordinates(String metadataName, byte[] content) {
        byte[] xml = metadataName.endsWith(".jar") ? zipEntry(content, "artifacts.xml") : content;
        if(xml.length == 0) return List.of();
        Document document = SecureXmlSupport.parse(xml, "p2 artifact repository");
        NodeList elements = document.getElementsByTagName("artifact");
        List<ArtifactCoordinate> result = new ArrayList<>();
        for(int index = 0; index < elements.getLength(); index++) {
            if(!(elements.item(index) instanceof Element element)) continue;
            String classifier = element.getAttribute("classifier").trim();
            String id = element.getAttribute("id").trim();
            String version = element.getAttribute("version").trim();
            String directory = switch(classifier) {
                case "osgi.bundle" -> "plugins";
                case "org.eclipse.update.feature" -> "features";
                default -> "";
            };
            if(directory.isBlank() || !safeArtifactToken(id) || !safeArtifactToken(version)) continue;
            result.add(new ArtifactCoordinate(directory, id, version));
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «zipEntry» correspondiente a la responsabilidad de RepositoryNode. Entradas: archive: archivo o contenido que debe procesarse; expectedName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] zipEntry(byte[] archive, String expectedName) {
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if(name.equals(expectedName) || name.endsWith("/" + expectedName)) {
                    return readLimited(zip, Math.min(properties.getRemoteMaxBytes(), properties.getMaxEntryBytes()),
                            "Compressed p2 metadata exceeds the configured entry limit");
                }
            }
            return new byte[0];
        } catch(IOException exception) {
            throw new IllegalArgumentException("Could not read compressed p2 metadata", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readLimited» dentro de RepositoryNode. Entradas: stream: dato de entrada necesario para la operación; maximum: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readLimited(InputStream stream, long maximum, String message) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; long total = 0L; int read;
        while((read = stream.read(buffer)) >= 0) {
            if(read == 0) continue;
            total += read;
            if(total > maximum) throw new IllegalArgumentException(message);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /** Descripción: Ejecuta la operación «safeArtifactToken» correspondiente a la responsabilidad de RepositoryNode. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean safeArtifactToken(String value) {
        return value != null && !value.isBlank() && value.length() <= 200
                && value.matches("[A-Za-z0-9_.+-]+");
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ URI validate(String value) {
        URI uri;
        try { uri = URI.create(value).normalize(); }
        catch(IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid remote repository URL", exception); }
        if(!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Remote update sites require HTTPS");
        if(uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getQuery() != null) throw new IllegalArgumentException("Remote repository URL is not allowed");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if(properties.getRemoteAllowedHosts().stream().noneMatch(valueHost -> valueHost.equals(host))) throw new IllegalArgumentException("Remote host is not present in dsl.imports.update-site.remote-allowed-hosts: " + host);
        rejectLocalAddress(host);
        return uri;
    }

    /** Descripción: Ejecuta la operación «rejectLocalAddress» correspondiente a la responsabilidad de RepositoryNode. Entradas: host: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectLocalAddress(String host) {
        try {
            for(InetAddress address : InetAddress.getAllByName(host)) {
                if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) throw new IllegalArgumentException("Private or local remote repository addresses are not allowed");
            }
        } catch(IOException exception) { throw new IllegalArgumentException("Remote repository host could not be resolved", exception); }
    }

    /** Descripción: Ejecuta la operación «compositeChildren» correspondiente a la responsabilidad de RepositoryNode. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> compositeChildren(byte[] content) {
        Document document = SecureXmlSupport.parse(content, "p2 composite repository");
        NodeList children = document.getElementsByTagName("child"); List<String> result = new ArrayList<>();
        for(int index = 0; index < children.getLength(); index++) {
            if(children.item(index) instanceof Element element) {
                String location = element.getAttribute("location").trim();
                if(!location.isBlank()) result.add(location);
            }
        }
        return result;
    }

    /** Descripción: Valida las condiciones necesarias para «verifyExpected» y rechaza entradas no válidas. Entradas: content: valor de entrada que debe procesarse; expectedSha256: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyExpected(byte[] content, String expectedSha256) {
        if(expectedSha256 == null || expectedSha256.isBlank()) return;
        if(!expectedSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("expectedSha256 must contain 64 hexadecimal characters");
        String actual = UpdateSiteHashing.sha256(content);
        if(!actual.equals(expectedSha256)) throw new IllegalArgumentException("Remote repository SHA-256 does not match the requested checksum");
    }

    /** Descripción: Obtiene la información correspondiente a «readCache» dentro de RepositoryNode. Entradas: uri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Cached readCache(URI uri) {
        try {
            Path index = cacheIndex(uri); if(!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS)) return null;
            String contentHash=com.example.editor.io.BoundedIo.readUtf8(index,128).trim();
            if(!contentHash.matches("[a-f0-9]{64}")) return null;
            Path blob = cacheBlob(contentHash); if(!Files.isRegularFile(blob, LinkOption.NOFOLLOW_LINKS)) return null;
            if(Files.size(blob) > properties.getRemoteMaxBytes()) return null;
            byte[] content = com.example.editor.io.BoundedIo.readInternal(blob);
            return UpdateSiteHashing.sha256(content).equals(contentHash) ? new Cached(content) : null;
        } catch(IOException exception) { return null; }
    }

    /** Descripción: Actualiza el estado gestionado por RepositoryNode mediante la operación «writeCache». Entradas: uri: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeCache(URI uri, byte[] content) {
        try {
            String contentHash = UpdateSiteHashing.sha256(content); Path blob = cacheBlob(contentHash); Path index = cacheIndex(uri);
            Files.createDirectories(blob.getParent()); Files.createDirectories(index.getParent());
            atomicWrite(blob, content); atomicWrite(index, contentHash.getBytes(StandardCharsets.UTF_8));
        } catch(IOException exception) { throw new IllegalStateException("Could not update the remote repository cache", exception); }
    }

    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de RepositoryNode. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content) throws IOException {
        if(Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) throw new IOException("Symbolic links are not allowed in the remote cache");
        AtomicFiles.writeReplacing(target,content);
    }

    /** Descripción: Ejecuta la operación «cacheBlob» correspondiente a la responsabilidad de RepositoryNode. Entradas: hash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path cacheBlob(String hash) { return safeCachePath("blobs/" + hash + ".bin"); }
    /** Descripción: Ejecuta la operación «cacheIndex» correspondiente a la responsabilidad de RepositoryNode. Entradas: uri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path cacheIndex(URI uri) { return safeCachePath("index/" + UpdateSiteHashing.sha256(uri.toString().getBytes(StandardCharsets.UTF_8)) + ".txt"); }
    /** Descripción: Ejecuta la operación «safeCachePath» correspondiente a la responsabilidad de RepositoryNode. Entradas: relative: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeCachePath(String relative) {
        Path root = properties.remoteCacheDirectoryPath(); Path path = root.resolve(relative).normalize();
        if(!path.startsWith(root)) throw new IllegalArgumentException("Unsafe remote cache path"); return path;
    }
    /** Descripción: Ejecuta la operación «directory» correspondiente a la responsabilidad de RepositoryNode. Entradas: uri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private URI directory(URI uri) { return uri.getPath().endsWith("/") ? uri : URI.create(uri.toString() + "/"); }
    /** Descripción: Evalúa la condición «isArchive» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isArchive(String path) { String lower = path == null ? "" : path.toLowerCase(Locale.ROOT); return lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".p2") || lower.endsWith(".updatesite"); }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de RepositoryNode. Entradas: uri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileName(URI uri) { String path = uri.getPath(); int slash = path.lastIndexOf('/'); return slash >= 0 ? path.substring(slash + 1) : "remote-update-site.zip"; }
    /** Descripción: Ejecuta la operación «evidence» correspondiente a la responsabilidad de RepositoryNode. Entradas: uri: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; download: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteRemoteRepositoryFetch evidence(URI uri, String path, Download download, int depth) { return new UpdateSiteRemoteRepositoryFetch(uri.toString(), path, download.content().length, UpdateSiteHashing.sha256(download.content()), download.cacheHit(), depth); }
    private record RepositoryNode(URI uri, int depth) { }
    private record ArtifactCoordinate(String directory, String id, String version)
            implements Comparable<ArtifactCoordinate> {
        /** Descripción: Ejecuta la operación «relativePath» correspondiente a la responsabilidad de RepositoryNode. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String relativePath() { return directory + "/" + id + "_" + version + ".jar"; }
        /** Descripción: Ejecuta la operación «compareTo» correspondiente a la responsabilidad de RepositoryNode. Entradas: other: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int compareTo(ArtifactCoordinate other) { return relativePath().compareTo(other.relativePath()); }
    }
    private record Download(byte[] content, boolean cacheHit, boolean notFound, long downloadedBytes) { }
    private record Cached(byte[] content) { }
}

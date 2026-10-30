package com.example.editor.grammar.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import com.example.editor.grammar.core.GrammarToolchainProfile;

/**
 * BLQ-GR-NATIVE: bounded, content-addressed templates of the node_modules left by a successful {@code npm ci}, so that a native verification of a
 * generated project whose package-lock.json was already installed does not install the same ~4000 files again.
 *
 * <p>Equivalence: {@code npm ci --ignore-scripts} installs exactly the tree that the lockfile pins (paths, versions and integrity hashes, no install
 * scripts), so the key is the SHA-256 of the package-lock.json together with the toolchain profile (id, provider and Node versions) and the platform
 * (optional packages such as esbuild binaries are chosen by operating system and architecture). Without a lockfile nothing is stored or reused.
 *
 * <p>Safety: a template is published with an atomic rename of a complete temporary directory; its manifest carries a SHA-256 digest of the whole tree
 * that is recomputed while restoring, and a template that no longer matches is discarded and the verification falls back to npm. Templates are never
 * shared between keys, the number of templates and their total size are bounded (least recently used first out) and symbolic links may only point
 * inside the tree. Every failure here is a cache miss: it never fails the verification.
 */
final class GrammarNativeDependencyTemplates {
    static final String NODE_MODULES = "node_modules";
    private static final String MANIFEST = "template.json";
    private static final String TEMPORARY_PREFIX = ".tmp-";
    private static final Duration STALE_TEMPORARY = Duration.ofHours(1);
    private static final String KEY_SCHEMA = "hydra-native-dependency-template/1";

    private final Path root;
    private final boolean enabled;
    private final int maximumEntries;
    private final long maximumBytes;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Descripción: Crea el almacén. Entradas: root: directorio de plantillas; enabled: reutilización activa; maximumEntries y maximumBytes: límites. Salida: almacén. */
    GrammarNativeDependencyTemplates(Path root, boolean enabled, int maximumEntries, long maximumBytes) {
        this.root = root == null ? Path.of("./data/grammar-native-dependencies") : root.toAbsolutePath().normalize();
        this.enabled = enabled && maximumEntries > 0 && maximumBytes > 0;
        this.maximumEntries = Math.max(0, maximumEntries);
        this.maximumBytes = Math.max(0L, maximumBytes);
    }

    /** Descripción: Clave de contenido de una instalación. Entradas: profile: perfil de toolchain; packageLock: contenido del lockfile. Salida: SHA-256 hexadecimal. */
    static String key(GrammarToolchainProfile profile, String packageLock) {
        String material = KEY_SCHEMA + "\n" + profile.id() + "\n" + profile.providerVersion() + "\n" + profile.nodeVersion() + "\n"
                + System.getProperty("os.name", "").toLowerCase(Locale.ROOT) + "/" + System.getProperty("os.arch", "").toLowerCase(Locale.ROOT) + "\n" + packageLock;
        return HexFormat.of().formatHex(sha256().digest(material.getBytes(StandardCharsets.UTF_8)));
    }

    /** Descripción: Indica si la reutilización está activa. Entradas: ninguna. Salida: indicador. */
    boolean enabled() { return enabled; }

    /**
     * Descripción: Copia la plantilla de la clave en workspace/node_modules comprobando su resumen. Entradas: key: clave; workspace: espacio aislado.
     * Salida: la restauración, o vacío si no hay plantilla válida (en ese caso el espacio queda sin node_modules).
     */
    Optional<Restored> restore(String key, Path workspace) {
        if(!enabled || key == null || key.isBlank()) return Optional.empty();
        Path target = workspace.resolve(NODE_MODULES);
        if(Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        long started = System.nanoTime();
        boolean corrupt = false;
        lock.readLock().lock();
        try {
            Path template = root.resolve(key);
            Optional<Manifest> manifest = readManifest(template);
            if(manifest.isEmpty() || !manifest.get().key().equals(key)) return Optional.empty();
            Tree copied = copyTree(template.resolve(NODE_MODULES), target, maximumBytes);
            if(!copied.digest().equals(manifest.get().digest()) || copied.files() != manifest.get().files() || copied.bytes() != manifest.get().bytes()) {
                corrupt = true;
                deleteTree(target);
                return Optional.empty();
            }
            Files.setLastModifiedTime(template.resolve(MANIFEST), FileTime.from(Instant.now()));
            return Optional.of(new Restored(key, copied.files(), copied.bytes(), (System.nanoTime() - started) / 1_000_000L));
        } catch(IOException | RuntimeException exception) {
            deleteTree(target);
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
            if(corrupt) evict(key);
        }
    }

    /**
     * Descripción: Publica como plantilla el node_modules que acaba de instalar npm ci, respetando los límites. Entradas: key: clave; workspace: espacio
     * aislado. Salida: true si se ha publicado una plantilla nueva.
     */
    boolean store(String key, Path workspace) {
        Path source = workspace.resolve(NODE_MODULES);
        if(!enabled || key == null || key.isBlank() || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) return false;
        Path temporary = null;
        try {
            Files.createDirectories(root);
            if(Files.exists(root.resolve(key).resolve(MANIFEST))) return false;
            removeStaleTemporaries();
            temporary = root.resolve(TEMPORARY_PREFIX + key + "-" + UUID.randomUUID());
            Tree tree = copyTree(source, temporary.resolve(NODE_MODULES), maximumBytes);
            writeManifest(temporary, new Manifest(key, tree.digest(), tree.files(), tree.bytes()));
            lock.writeLock().lock();
            try {
                Path published = root.resolve(key);
                if(Files.exists(published)) return false;
                if(!makeRoom(tree.bytes())) return false;
                Files.move(temporary, published, StandardCopyOption.ATOMIC_MOVE);
                temporary = null;
                return true;
            } finally {
                lock.writeLock().unlock();
            }
        } catch(FileAlreadyExistsException exception) {
            return false;
        } catch(IOException | RuntimeException exception) {
            return false;
        } finally {
            if(temporary != null) deleteTree(temporary);
        }
    }

    /** Descripción: Expulsa plantillas, de la menos usada a la más usada, hasta que cabe una nueva. Entradas: incoming: bytes de la nueva. Salida: true si cabe. */
    private boolean makeRoom(long incoming) throws IOException {
        if(incoming > maximumBytes) return false;
        List<Entry> entries = new ArrayList<>(entries());
        entries.sort(Comparator.comparing(Entry::lastUsed));
        long total = entries.stream().mapToLong(Entry::bytes).sum();
        while(!entries.isEmpty() && (entries.size() >= maximumEntries || total + incoming > maximumBytes)) {
            Entry oldest = entries.removeFirst();
            deleteTemplate(oldest.directory());
            total -= oldest.bytes();
        }
        return entries.size() < maximumEntries && total + incoming <= maximumBytes;
    }

    /** Descripción: Elimina la plantilla de una clave (por ejemplo, alterada). Entradas: key: clave. Salida: ninguna. */
    private void evict(String key) {
        lock.writeLock().lock();
        try { deleteTemplate(root.resolve(key)); }
        finally { lock.writeLock().unlock(); }
    }

    /** Descripción: Lista las plantillas publicadas con su tamaño y último uso. Entradas: ninguna. Salida: entradas. */
    List<Entry> entries() throws IOException {
        List<Entry> result = new ArrayList<>();
        if(!Files.isDirectory(root)) return result;
        try(DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for(Path child : children) {
                if(child.getFileName().toString().startsWith(TEMPORARY_PREFIX)) continue;
                Optional<Manifest> manifest = readManifest(child);
                if(manifest.isPresent()) result.add(new Entry(child, manifest.get().bytes(), Files.getLastModifiedTime(child.resolve(MANIFEST)).toInstant()));
            }
        }
        return result;
    }

    /** Descripción: Borra directorios temporales abandonados por una publicación interrumpida. Entradas: ninguna. Salida: ninguna. */
    private void removeStaleTemporaries() throws IOException {
        Instant limit = Instant.now().minus(STALE_TEMPORARY);
        try(DirectoryStream<Path> children = Files.newDirectoryStream(root, TEMPORARY_PREFIX + "*")) {
            for(Path child : children) if(Files.getLastModifiedTime(child).toInstant().isBefore(limit)) deleteTree(child);
        }
    }

    /**
     * Descripción: Copia un árbol calculando su resumen en la misma lectura. Los enlaces simbólicos solo pueden apuntar dentro del árbol y cualquier
     * otro tipo de entrada se rechaza. Entradas: source, target, limit: bytes máximos. Salida: resumen, ficheros y bytes.
     */
    private static Tree copyTree(Path source, Path target, long limit) throws IOException {
        if(!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Dependency tree is missing");
        List<Path> paths;
        try(Stream<Path> walk = Files.walk(source)) {
            paths = walk.sorted(Comparator.comparing(path -> portable(source.relativize(path)))).toList();
        }
        MessageDigest tree = sha256();
        long files = 0;
        long bytes = 0;
        byte[] buffer = new byte[64 * 1024];
        for(Path path : paths) {
            String relative = portable(source.relativize(path));
            Path destination = relative.isEmpty() ? target : target.resolve(relative);
            if(Files.isSymbolicLink(path)) {
                Path link = Files.readSymbolicLink(path);
                if(link.isAbsolute() || !path.getParent().resolve(link).normalize().startsWith(source)) throw new IOException("Dependency link leaves the tree: " + relative);
                Files.createDirectories(destination.getParent());
                Files.createSymbolicLink(destination, link);
                update(tree, "L " + relative + " -> " + portable(link) + "\n");
            } else if(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(destination);
                update(tree, "D " + relative + "\n");
            } else if(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(destination.getParent());
                MessageDigest content = sha256();
                long size = 0;
                try(InputStream in = new DigestInputStream(Files.newInputStream(path), content); OutputStream out = Files.newOutputStream(destination)) {
                    for(int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                        out.write(buffer, 0, read);
                        size += read;
                        if(bytes + size > limit) throw new IOException("Dependency tree exceeds the template size limit");
                    }
                }
                files++;
                bytes += size;
                update(tree, "F " + relative + "\n" + size + "\n" + HexFormat.of().formatHex(content.digest()) + "\n");
            } else {
                throw new IOException("Unsupported dependency tree entry: " + relative);
            }
        }
        return new Tree(HexFormat.of().formatHex(tree.digest()), files, bytes);
    }

    /** Descripción: Lee el manifiesto de una plantilla si está completo. Entradas: template: directorio. Salida: manifiesto o vacío. */
    private static Optional<Manifest> readManifest(Path template) {
        Path file = template.resolve(MANIFEST);
        if(!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try(InputStream in = Files.newInputStream(file)) {
            Properties values = new Properties();
            values.load(in);
            return Optional.of(new Manifest(values.getProperty("key", ""), values.getProperty("digest", ""), Long.parseLong(values.getProperty("files", "-1")),
                    Long.parseLong(values.getProperty("bytes", "-1"))));
        } catch(IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Descripción: Escribe el manifiesto, último paso antes de publicar. Entradas: template: directorio; manifest: datos. Salida: ninguna. */
    private static void writeManifest(Path template, Manifest manifest) throws IOException {
        Properties values = new Properties();
        values.setProperty("key", manifest.key());
        values.setProperty("digest", manifest.digest());
        values.setProperty("files", Long.toString(manifest.files()));
        values.setProperty("bytes", Long.toString(manifest.bytes()));
        try(OutputStream out = Files.newOutputStream(template.resolve(MANIFEST))) { values.store(out, KEY_SCHEMA); }
    }

    /** Descripción: Borra una plantilla empezando por su manifiesto, de modo que una borrada a medias ya no es válida. Entradas: template. Salida: ninguna. */
    private static void deleteTemplate(Path template) {
        try { Files.deleteIfExists(template.resolve(MANIFEST)); } catch(IOException ignored) { /* the tree below is removed anyway */ }
        deleteTree(template);
    }

    /** Descripción: Borra un árbol sin seguir enlaces, con el mejor esfuerzo. Entradas: path. Salida: ninguna. */
    private static void deleteTree(Path path) {
        if(path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try(Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try { Files.deleteIfExists(item); } catch(IOException ignored) { /* best effort: an orphan without manifest is never reused */ }
            });
        } catch(IOException ignored) { /* best effort */ }
    }

    /** Descripción: Ruta relativa con «/». Entradas: path. Salida: texto portable. */
    private static String portable(Path path) { return path.toString().replace('\\', '/'); }

    /** Descripción: Añade texto UTF-8 a un resumen. Entradas: digest, text. Salida: ninguna. */
    private static void update(MessageDigest digest, String text) { digest.update(text.getBytes(StandardCharsets.UTF_8)); }

    /** Descripción: Instancia de SHA-256. Entradas: ninguna. Salida: resumen nuevo. */
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    record Restored(String key, long files, long bytes, long millis) { }
    record Entry(Path directory, long bytes, Instant lastUsed) { }
    private record Manifest(String key, String digest, long files, long bytes) { }
    private record Tree(String digest, long files, long bytes) { }
}

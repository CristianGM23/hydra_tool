package com.example.editor.standalone.distribution;

import com.example.editor.io.AtomicFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Empaquetado web real: distribuye exclusivamente dist, con checksums para despliegue y sin fuentes de desarrollo. */
public final class StandaloneWebDeploymentPackager {
    private static final long ZIP_TIMESTAMP = 315532800000L;
    private static final long MAX_FILE = 64L * 1024 * 1024;
    private static final long MAX_TOTAL = 192L * 1024 * 1024;
    private static final long MAX_ARCHIVE = 192L * 1024 * 1024;
    private static final int MAX_FILES = 2048;
    private record FileEvidence(Path path, String relative, String hash, long size) {}

    /** Descripción: Empaqueta los archivos web compilados y calcula evidencias sin acreditar instalación PWA. Entradas: editorRoot, destination, profile y readiness. Salida: paquete reproducible y hash. */
    public StandaloneDistributionPackageResult packageWeb(Path editorRoot, Path destination,
            StandaloneDistributionProfile profile, StandaloneDistributionValidationReport readiness) {
        if(!profile.targets().contains(StandaloneDistributionTarget.PWA))
            throw new IllegalArgumentException("Deployable web package requires a PWA target");
        if(!readiness.valid()) throw new IllegalStateException("Web deployment requires successful release readiness");
        Path root = editorRoot.toAbsolutePath().normalize().resolve("dist");
        Path archive = destination.toAbsolutePath().normalize();
        if(!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IllegalArgumentException("Built dist must be a real directory");
        if(archive.startsWith(editorRoot.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Web archive must remain outside the generated editor");
        Path temporary = null;
        try {
            List<FileEvidence> files = inventory(root);
            if(files.stream().noneMatch(item -> item.relative().equals("index.html")))
                throw new IllegalStateException("Built editor entry point is missing");
            StringBuilder checksums = new StringBuilder();
            for(FileEvidence file : files) checksums.append(file.hash()).append("  ").append(file.relative()).append('\n');
            String evidence = "{\"schemaVersion\":\"1.0\",\"level\":\"DEPLOYABLE_WEB_ASSETS\","
                    + "\"version\":" + quoted(profile.version()) + ",\"updateChannel\":" + quoted(profile.updateChannel())
                    + ",\"files\":" + files.size() + ",\"offlineEnabled\":" + profile.offlineEnabled()
                    + ",\"nativeInstallerCertified\":false}\n";
            byte[] checks = checksums.toString().getBytes(StandardCharsets.UTF_8);
            byte[] metadata = evidence.getBytes(StandardCharsets.UTF_8);
            Path parent = archive.getParent();
            if(parent == null || Files.isSymbolicLink(parent)) throw new IllegalArgumentException("Unsafe output directory");
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".hydra-web-", ".zip.tmp");
            try(ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for(FileEvidence file : files) appendFile(zip, file);
                appendBytes(zip, "hydra-release.sha256", checks);
                appendBytes(zip, "hydra-deployment.json", metadata);
            }
            if(Files.size(temporary) > MAX_ARCHIVE) throw new IOException("Web ZIP exceeds maximum archive size");
            String zipHash = digest(temporary, MAX_ARCHIVE);
            AtomicFiles.moveReplacing(temporary, archive);
            temporary = null;
            List<String> names = new ArrayList<>(files.stream().map(FileEvidence::relative).toList());
            names.add("hydra-release.sha256");
            names.add("hydra-deployment.json");
            return new StandaloneDistributionPackageResult(archive, names, zipHash);
        } catch(IOException exception) { throw new IllegalStateException("Could not package deployable web editor", exception); }
        finally { if(temporary != null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) { /* cleanup */ } }
    }

    /** Descripción: Recopila únicamente recursos seguros y acotados de la distribución. Entradas: raíz dist. Salida: inventario con hashes y tamaños. */
    private List<FileEvidence> inventory(Path root) throws IOException {
        List<Path> candidates = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            /** Descripción: Rechaza directorios simbólicos o especiales. Entradas: dir y attrs. Salida: decisión de recorrido seguro. */
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if(attrs.isSymbolicLink() || !attrs.isDirectory()) throw new IOException("Invalid distribution directory");
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Rechaza activos peligrosos o que superan presupuestos. Entradas: file y attrs. Salida: decisión de inclusión. */
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if(!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("Symlink/special distribution file is forbidden");
                String relative = root.relativize(file).toString().replace('\\', '/');
                String base = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if(relative.equals("hydra-release.sha256") || relative.equals("hydra-deployment.json") ||
                        base.equals(".env") || base.startsWith(".env.") || base.equals("credentials.json") ||
                        base.endsWith(".key") || base.endsWith(".pem") || base.endsWith(".p12") ||
                        base.endsWith(".pfx") || base.endsWith(".jks") || base.endsWith(".map"))
                    throw new IOException("Forbidden or reserved deployment asset: " + relative);
                if(candidates.size() >= MAX_FILES) throw new IOException("Too many web deployment assets");
                candidates.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        candidates.sort(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')));
        long total = 0;
        List<FileEvidence> result = new ArrayList<>();
        for(Path file : candidates) {
            long size = Files.size(file);
            total = Math.addExact(total, size);
            if(size > MAX_FILE || total > MAX_TOTAL) throw new IOException("Web deployment size limit exceeded");
            result.add(new FileEvidence(file, root.relativize(file).toString().replace('\\', '/'), digest(file, MAX_FILE), size));
        }
        return result;
    }

    /** Descripción: Añade un recurso al ZIP comprobando durante la copia que mantiene su hash. Entradas: zip y file. Salida: entrada íntegra. */
    private void appendFile(ZipOutputStream zip, FileEvidence file) throws IOException {
        ZipEntry entry = new ZipEntry(file.relative()); entry.setTime(ZIP_TIMESTAMP); zip.putNextEntry(entry);
        MessageDigest digest = sha(); long count = 0; byte[] buffer = new byte[65536];
        try(InputStream source = Files.newInputStream(file.path(), LinkOption.NOFOLLOW_LINKS)) {
            int length;
            while((length = source.read(buffer)) != -1) {
                count += length;
                if(count > MAX_FILE) throw new IOException("File changed beyond budget during web packaging");
                digest.update(buffer, 0, length); zip.write(buffer, 0, length);
            }
        }
        zip.closeEntry();
        if(count != file.size() || !HexFormat.of().formatHex(digest.digest()).equals(file.hash()))
            throw new IOException("Built asset changed during packaging: " + file.relative());
    }

    /** Descripción: Añade metadatos inmutables al ZIP. Entradas: zip, name y data. Salida: entrada reproducible. */
    private void appendBytes(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(ZIP_TIMESTAMP);
        zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
    }

    /** Descripción: Calcula SHA-256 con lectura acotada. Entradas: file y maximum. Salida: digest hexadecimal. */
    private String digest(Path file, long maximum) throws IOException {
        MessageDigest md = sha(); long count = 0; byte[] buffer = new byte[65536];
        try(InputStream source = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            int length;
            while((length = source.read(buffer)) != -1) {
                count += length;
                if(count > maximum) throw new IOException("Hash input size exceeded");
                md.update(buffer, 0, length);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Descripción: Inicializa el proveedor de hash seguro. Entradas: ninguna. Salida: algoritmo SHA-256. */
    private MessageDigest sha() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    /** Descripción: Escapa cadenas JSON del descriptor. Entradas: value. Salida: literal JSON válido. */
    private String quoted(String value) {
        StringBuilder json = new StringBuilder("\"");
        for(int i=0; i<value.length(); i++) {
            char ch = value.charAt(i);
            if(ch == '"' || ch == '\\') json.append('\\').append(ch);
            else if(ch < 0x20) json.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) ch));
            else json.append(ch);
        }
        return json.append('"').toString();
    }
}

package com.example.editor.recovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Service;
import com.example.editor.persistence.support.HydraPersistenceSupport;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** AZ verified backups and isolated recovery drills. */
@Service
public class HydraRecoveryService {
    private static final int BUFFER_SIZE = 16_384;
    private final HydraRecoveryProperties properties;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de HydraRecoveryService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraRecoveryService(HydraRecoveryProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public synchronized HydraRecoveryBackupManifest backup() {
        Path archive = null;
        try {
            Path source = properties.getSourceRoot().toAbsolutePath().normalize();
            Path backupsRoot = backupsRoot();
            Files.createDirectories(source);
            Files.createDirectories(backupsRoot);
            BackupInventory inventory = collectBackupInventory(source, properties.getStorageRoot().toAbsolutePath().normalize());
            long total = inventory.totalBytes();
            List<HydraRecoveryFile> files = inventory.files();
            String backupId = "backup-" + HydraPersistenceSupport.sha256(writer.writeValueAsBytes(files)).substring(0, 16);
            archive = backupsRoot.resolve(backupId + ".zip");
            writeArchive(source, archive, files);
            String manifestSha = manifestSha256(backupId, total, files);
            HydraRecoveryBackupManifest candidate = new HydraRecoveryBackupManifest(backupId, Instant.now(), archive.toString(), total, files, manifestSha, false);
            List<String> diagnostics = verifyArchive(candidate, false);
            if(!diagnostics.isEmpty()) {
                throw new IllegalStateException("Recovery backup verification failed: " + String.join("; ", diagnostics));
            }
            HydraRecoveryBackupManifest manifest = new HydraRecoveryBackupManifest(backupId, candidate.createdAt(), archive.toString(), total, files, manifestSha, true);
            HydraPersistenceSupport.atomicWrite(backupsRoot.resolve(backupId + ".manifest.json"), writer.writeValueAsBytes(manifest));
            trimBackups();
            return manifest;
        } catch(ArithmeticException | IOException exception) {
            deleteQuietly(archive);
            throw new IllegalStateException("Could not create verified recovery backup", exception);
        } catch(RuntimeException exception) {
            deleteQuietly(archive);
            throw exception;
        }
    }

    /** Descripción: Valida las condiciones necesarias para «verify» y rechaza entradas no válidas. Entradas: backupId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraRecoveryBackupManifest verify(String backupId) {
        HydraRecoveryBackupManifest manifest = find(backupId);
        List<String> diagnostics = verifyArchive(manifest, false);
        return new HydraRecoveryBackupManifest(manifest.backupId(), manifest.createdAt(), manifest.archivePath(), manifest.totalBytes(), manifest.files(), manifest.manifestSha256(), diagnostics.isEmpty());
    }

    public synchronized HydraRecoveryDrillReport drill(HydraRecoveryDrillRequest request) {
        if(request == null) {
            throw new IllegalArgumentException("Recovery drill request is required");
        }
        HydraRecoveryBackupManifest manifest = find(HydraPersistenceSupport.safeId(request.backupId(), "backupId"));
        List<String> diagnostics = verifyArchive(manifest, request.simulateCorruption());
        String drillId = "drill-" + Instant.now().toEpochMilli();
        Path restoreRoot = restoresRoot().resolve(drillId).normalize();
        int verified = 0;
        if(diagnostics.isEmpty() && request.restoreToCleanRoot()) {
            restore(manifest, restoreRoot);
            verified = verifyRestored(manifest, restoreRoot, diagnostics);
        }
        return new HydraRecoveryDrillReport(drillId, manifest.backupId(), diagnostics.isEmpty(), request.restoreToCleanRoot() ? restoreRoot.toString() : "", verified, diagnostics, Instant.now());
    }

    /** Descripción: Ejecuta la operación «backups» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraRecoveryBackupManifest> backups() {
        List<HydraRecoveryBackupManifest> result = new ArrayList<>();
        for(Path path : HydraPersistenceSupport.jsonFiles(backupsRoot())) {
            if(!path.getFileName().toString().endsWith(".manifest.json")) continue;
            try { result.add(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), HydraRecoveryBackupManifest.class)); }
            catch(Exception ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/recovery/HydraRecoveryService.java",ignored);}
        }
        return result.stream().sorted(Comparator.comparing(HydraRecoveryBackupManifest::createdAt).reversed()).toList();
    }

    public synchronized List<String> retain(int maximumBackups) {
        int maximum = Math.max(1, maximumBackups);
        List<HydraRecoveryBackupManifest> values = backups();
        List<String> removed = new ArrayList<>();
        for(int index = maximum; index < values.size(); index++) {
            HydraRecoveryBackupManifest manifest = values.get(index);
            try {
                Files.deleteIfExists(backupsRoot().resolve(manifest.backupId() + ".zip"));
                Files.deleteIfExists(backupsRoot().resolve(manifest.backupId() + ".manifest.json"));
                removed.add(manifest.backupId());
            } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/recovery/HydraRecoveryService.java",ignored);}
        }
        return removed;
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de HydraRecoveryService. Entradas: backupId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraRecoveryBackupManifest find(String backupId) {
        String safeBackupId = HydraPersistenceSupport.safeId(backupId, "backupId");
        Path path = backupsRoot().resolve(safeBackupId + ".manifest.json");
        try {
            return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), HydraRecoveryBackupManifest.class);
        } catch(Exception exception) {
            throw new IllegalStateException("Recovery backup was not found or is invalid: " + backupId, exception);
        }
    }

    /** Descripción: Escribe el backup por streaming y publica el ZIP de forma atómica solo si cada fichero conserva tamaño y checksum durante la copia. Entradas: source: raíz; archive: destino; files: inventario verificado. Salida: no devuelve valor. */ private void writeArchive(Path source, Path archive, List<HydraRecoveryFile> files) throws IOException {
        Path temp = Files.createTempFile(archive.getParent(), archive.getFileName().toString(), ".tmp");
        try {
            try(ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temp))) {
                for(HydraRecoveryFile file : files) {
                    Path path=source.resolve(file.path()).normalize();
                    if(!path.startsWith(source)||Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Recovery source changed while creating the archive: "+file.path());
                    ZipEntry entry = new ZipEntry(file.path()); entry.setTime(0L); output.putNextEntry(entry);
                    EntryDigest copied;
                    try(InputStream input=Files.newInputStream(path)){copied=copyAndDigest(input,output,file.bytes());}
                    output.closeEntry();
                    if(copied.bytes()!=file.bytes()||!copied.sha256().equals(file.sha256())) throw new IOException("Recovery source changed while creating the archive: "+file.path());
                }
            }
            AtomicFiles.moveReplacing(temp,archive);
        } finally { Files.deleteIfExists(temp); }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyArchive» y rechaza entradas no válidas. Entradas: manifest: dato de entrada necesario para la operación; simulateCorruption: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> verifyArchive(HydraRecoveryBackupManifest manifest, boolean simulateCorruption) {
        List<String> diagnostics = verifyManifest(manifest);
        if(!diagnostics.isEmpty()) {
            return diagnostics;
        }
        Path archive;
        try {
            archive = safeArchive(manifest);
        } catch(RuntimeException exception) {
            diagnostics.add(exception.getMessage());
            return diagnostics;
        }
        if(!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(archive)) {
            diagnostics.add("Backup archive is missing or unsafe");
            return diagnostics;
        }
        long total = 0L;
        try(ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            int index = 0;
            ZipEntry entry;
            while((entry = input.getNextEntry()) != null) {
                if(index >= manifest.files().size()) {
                    diagnostics.add("Archive contains undeclared files");
                    break;
                }
                HydraRecoveryFile expected = manifest.files().get(index++);
                if(entry.isDirectory()) {
                    diagnostics.add("Archive contains an unexpected directory: " + entry.getName());
                    continue;
                }
                String safeEntry = safeRelative(entry.getName());
                if(!safeEntry.equals(expected.path())) {
                    diagnostics.add("Archive order/path mismatch: " + entry.getName());
                }
                EntryDigest consumed=copyAndDigest(input,OutputStream.nullOutputStream(),expected.bytes());
                total = Math.addExact(total, consumed.bytes());
                String hash = simulateCorruption && index == 1 ? "corrupted" : consumed.sha256();
                if(!hash.equals(expected.sha256())) diagnostics.add("Checksum mismatch: " + expected.path());
            }
            if(index != manifest.files().size()) {
                diagnostics.add("Archive file count differs from the manifest");
            }
            if(total != manifest.totalBytes()) {
                diagnostics.add("Archive byte count differs from the manifest");
            }
        } catch(IOException | RuntimeException exception) {
            diagnostics.add("Archive could not be read: " + exception.getMessage());
        }
        return diagnostics;
    }

    /** Descripción: Valida las condiciones necesarias para «verifyManifest» y rechaza entradas no válidas. Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> verifyManifest(HydraRecoveryBackupManifest manifest) {
        List<String> diagnostics = new ArrayList<>();
        if(manifest == null) {
            diagnostics.add("Backup manifest is missing");
            return diagnostics;
        }
        try {
            HydraPersistenceSupport.safeId(manifest.backupId(), "backupId");
        } catch(RuntimeException exception) {
            diagnostics.add("Backup identifier is invalid");
        }
        if(manifest.files().size() > properties.getMaximumFiles()) {
            diagnostics.add("Backup manifest exceeds the file limit");
        }
        long total = 0L;
        Set<String> paths = new HashSet<>();
        for(HydraRecoveryFile file : manifest.files()) {
            if(file == null) {
                diagnostics.add("Backup manifest contains a null file");
                continue;
            }
            try {
                String safePath = safeRelative(file.path());
                if(!paths.add(safePath)) {
                    diagnostics.add("Backup manifest contains a duplicate path: " + safePath);
                }
            } catch(RuntimeException exception) {
                diagnostics.add("Backup manifest contains an unsafe path: " + file.path());
            }
            if(file.bytes() < 0L || file.bytes() > properties.getMaximumBytes()) {
                diagnostics.add("Backup manifest contains an invalid file size: " + file.path());
            } else {
                try {
                    total = Math.addExact(total, file.bytes());
                } catch(ArithmeticException exception) {
                    diagnostics.add("Backup manifest byte count overflows");
                }
            }
            if(file.sha256() == null || !file.sha256().matches("[0-9a-f]{64}")) {
                diagnostics.add("Backup manifest contains an invalid checksum: " + file.path());
            }
        }
        if(total != manifest.totalBytes() || total > properties.getMaximumBytes()) {
            diagnostics.add("Backup manifest byte count is invalid");
        }
        try {
            String expected = manifestSha256(manifest.backupId(), manifest.totalBytes(), manifest.files());
            if(!expected.equals(manifest.manifestSha256())) {
                diagnostics.add("Backup manifest checksum mismatch");
            }
        } catch(RuntimeException exception) {
            diagnostics.add("Backup manifest checksum could not be calculated");
        }
        return diagnostics;
    }

    /** Descripción: Ejecuta la operación «restore» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: manifest: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void restore(HydraRecoveryBackupManifest manifest, Path root) {
        Path archive = safeArchive(manifest);
        Path safeRoot = root.toAbsolutePath().normalize();
        Path allowedRoot = restoresRoot();
        if(!safeRoot.startsWith(allowedRoot) || Files.exists(safeRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Recovery restore root is unsafe or already exists");
        }
        try {
            Files.createDirectories(safeRoot);
            try(ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
                int index = 0;
                ZipEntry entry;
                while((entry = input.getNextEntry()) != null) {
                    if(index >= manifest.files().size()) {
                        throw new IllegalStateException("Archive contains undeclared files");
                    }
                    HydraRecoveryFile expected = manifest.files().get(index++);
                    String safeEntry = safeRelative(entry.getName());
                    if(entry.isDirectory() || !safeEntry.equals(expected.path())) {
                        throw new IllegalStateException("Archive order/path mismatch: " + entry.getName());
                    }
                    Path target = safeRoot.resolve(safeEntry).normalize();
                    if(!target.startsWith(safeRoot)) throw new IllegalStateException("Unsafe recovery path: " + entry.getName());
                    Files.createDirectories(target.getParent()); ensureNoSymbolicLinks(safeRoot,target.getParent());
                    Path temporary=Files.createTempFile(target.getParent(),"."+target.getFileName()+"-", ".restore");
                    try {
                        EntryDigest restored;
                        try(OutputStream output=Files.newOutputStream(temporary)){restored=copyAndDigest(input,output,expected.bytes());}
                        if(restored.bytes()!=expected.bytes()||!restored.sha256().equals(expected.sha256())) throw new IllegalStateException("Checksum mismatch while restoring: " + expected.path());
                        AtomicFiles.moveNew(temporary,target);
                    } finally { Files.deleteIfExists(temporary); }
                }
                if(index != manifest.files().size()) {
                    throw new IllegalStateException("Archive file count differs from the manifest");
                }
            }
        } catch(IOException exception) {
            throw new IllegalStateException("Could not restore recovery backup", exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «verifyRestored» y rechaza entradas no válidas. Entradas: manifest: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int verifyRestored(HydraRecoveryBackupManifest manifest, Path root, List<String> diagnostics) {
        int verified = 0;
        for(HydraRecoveryFile file : manifest.files()) {
            try {
                Path target = root.resolve(safeRelative(file.path())).normalize();
                if(!target.startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target) || Files.size(target) != file.bytes() || !sha256(target).equals(file.sha256())) {
                    diagnostics.add("Restored file mismatch: " + file.path());
                } else {
                    verified++;
                }
            } catch(IOException | RuntimeException exception) {
                diagnostics.add("Restored file could not be read: " + file.path());
            }
        }
        return verified;
    }

    /** Descripción: Recorre la raíz de backup incrementalmente, excluye el almacenamiento de recuperación y aborta al superar número de ficheros o bytes. Entradas: source: raíz; excludedRoot: almacenamiento excluido. Salida: devuelve inventario determinista y total de bytes. */ private BackupInventory collectBackupInventory(Path source,Path excludedRoot) throws IOException {
        List<HydraRecoveryFile> values=new ArrayList<>(Math.min(properties.getMaximumFiles(),4096)); long[] total={0L};
        Files.walkFileTree(source,new java.nio.file.SimpleFileVisitor<>(){
            /** Descripción: Omite el almacenamiento de recuperación y rechaza enlaces simbólicos de directorio para que el backup no salga de su raíz. Entradas: dir y atributos. Salida: decisión de recorrido. */ @Override public java.nio.file.FileVisitResult preVisitDirectory(Path dir,java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path normalized=dir.toAbsolutePath().normalize(); if(normalized.startsWith(excludedRoot))return java.nio.file.FileVisitResult.SKIP_SUBTREE;if(attrs.isSymbolicLink())throw new IOException("Recovery source contains a symbolic link: "+dir);return java.nio.file.FileVisitResult.CONTINUE;
            }
            /** Descripción: Registra un fichero real y aplica límites antes de continuar el inventario. Entradas: file y atributos. Salida: decisión de recorrido. */ @Override public java.nio.file.FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if(attrs.isSymbolicLink())throw new IOException("Recovery source contains a symbolic link: "+file);if(!attrs.isRegularFile())return java.nio.file.FileVisitResult.CONTINUE;
                if(values.size()>=properties.getMaximumFiles())throw new IOException("Recovery backup exceeds the file limit");
                try{total[0]=Math.addExact(total[0],attrs.size());}catch(ArithmeticException exception){throw new IOException("Recovery backup byte count overflow",exception);}if(total[0]>properties.getMaximumBytes())throw new IOException("Recovery backup exceeds the byte limit");
                String relative=safeRelative(source.relativize(file).toString().replace('\\','/'));values.add(new HydraRecoveryFile(relative,attrs.size(),sha256(file)));return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        values.sort(Comparator.comparing(HydraRecoveryFile::path));return new BackupInventory(List.copyOf(values),total[0]);
    }

    /** Descripción: Copia exactamente una entrada mientras calcula SHA-256 sin materializarla en memoria; rechaza bytes superiores o inferiores a los declarados. Entradas: input, output y expectedBytes. Salida: devuelve bytes y checksum calculados. */ private EntryDigest copyAndDigest(InputStream input,OutputStream output,long expectedBytes) throws IOException {
        if(expectedBytes<0L||expectedBytes>properties.getMaximumBytes())throw new IOException("Archive entry exceeds the supported size");
        try { MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[BUFFER_SIZE];long total=0L;int read;while((read=input.read(buffer))!=-1){if(read==0)continue;try{total=Math.addExact(total,read);}catch(ArithmeticException exception){throw new IOException("Archive entry size overflow",exception);}if(total>expectedBytes||total>properties.getMaximumBytes())throw new IOException("Archive entry exceeds its declared size");digest.update(buffer,0,read);output.write(buffer,0,read);}if(total!=expectedBytes)throw new IOException("Archive entry size differs from the manifest");return new EntryDigest(total,HexFormat.of().formatHex(digest.digest()));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable",exception);}
    }

    /** Inventario acotado de un backup antes de comprimirlo. */ private record BackupInventory(List<HydraRecoveryFile> files,long totalBytes){}
    /** Resultado incremental de copiar y calcular checksum de una entrada. */ private record EntryDigest(long bytes,String sha256){}

    /** Descripción: Ejecuta la operación «safeArchive» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeArchive(HydraRecoveryBackupManifest manifest) {
        Path backupsRoot = backupsRoot();
        Path archive = Path.of(manifest.archivePath()).toAbsolutePath().normalize();
        Path expected = backupsRoot.resolve(HydraPersistenceSupport.safeId(manifest.backupId(), "backupId") + ".zip").normalize();
        if(!archive.equals(expected) || !archive.startsWith(backupsRoot)) {
            throw new IllegalStateException("Backup archive path is outside the recovery storage");
        }
        return archive;
    }

    /** Descripción: Ejecuta la operación «manifestSha256» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: backupId: identificador que delimita el recurso objetivo; totalBytes: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String manifestSha256(String backupId, long totalBytes, List<HydraRecoveryFile> files) {
        try {
            return HydraPersistenceSupport.sha256(writer.writeValueAsBytes(Map.of("backupId", backupId, "files", files, "totalBytes", totalBytes)));
        } catch(JacksonException exception) {
            throw new IllegalStateException("Could not calculate the recovery manifest checksum", exception);
        }
    }

    /** Descripción: Ejecuta la operación «safeRelative» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeRelative(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        Path relative = Path.of(normalized).normalize();
        String safe = relative.toString().replace('\\', '/');
        if(normalized.isBlank() || normalized.indexOf('\0') >= 0 || normalized.matches("^[A-Za-z]:.*") || relative.isAbsolute() || safe.equals(".") || safe.equals("..") || safe.startsWith("../") || safe.contains("/../") || safe.startsWith("/")) {
            throw new IllegalArgumentException("Unsafe recovery path: " + value);
        }
        return safe;
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try(InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «ensureNoSymbolicLinks» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureNoSymbolicLinks(Path root, Path directory) throws IOException {
        Path current = root;
        Path relative = root.relativize(directory);
        for(Path part : relative) {
            current = current.resolve(part);
            if(Files.isSymbolicLink(current)) {
                throw new IOException("Recovery target contains a symbolic link");
            }
        }
    }

    /** Descripción: Ejecuta la operación «backupsRoot» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path backupsRoot() {
        return properties.getStorageRoot().resolve("backups").toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «restoresRoot» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path restoresRoot() {
        return properties.getStorageRoot().resolve("restores").toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «trimBackups» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimBackups() {
        Path root = backupsRoot();
        List<Path> manifests = HydraPersistenceSupport.jsonFiles(root).stream()
                .filter(path -> path.getFileName().toString().endsWith(".manifest.json"))
                .sorted(Comparator.comparingLong(this::modified).reversed())
                .toList();
        for(int index = properties.getMaximumBackups(); index < manifests.size(); index++) {
            Path manifest = manifests.get(index);
            String archive = manifest.getFileName().toString().replace(".manifest.json", ".zip");
            try {
                Files.deleteIfExists(manifest);
                Files.deleteIfExists(root.resolve(archive));
            } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/recovery/HydraRecoveryService.java",ignored);}
        }
    }

    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de HydraRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch(IOException exception) {
            return 0L;
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteQuietly» de forma controlada. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteQuietly(Path path) {
        if(path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/recovery/HydraRecoveryService.java",ignored);}
    }
}

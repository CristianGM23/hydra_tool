package com.example.editor.gamification;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.editor.concurrent.ResourceStripedLock;
import com.example.editor.contract.StableIdentifierPolicy;
import com.example.editor.io.AtomicFiles;
import com.example.editor.io.BoundedIo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * File-level side of the gamification recovery. A read never asks it to change anything: it fingerprints the stored pair byte for byte
 * and, only through the explicit quarantine, moves a corrupt or incomplete pair into {@code .invalid-recovery/<id>/<quarantineId>/} after
 * writing a manifest that makes the operation resumable and repeatable.
 *
 * <p>Coordination: the application service takes {@link #lockFor(String)} around every operation on a pair (create, replace, delete, read,
 * inspection, quarantine), so inside this JVM a quarantine never interleaves with another operation on the same pair. Other HYDRA processes
 * are excluded from the data directory by HydraInstanceLock. Programs outside HYDRA are not coordinated; every document is therefore
 * re-fingerprinted right before anything moves and the quarantine stops with 412 if one changed.
 */
@Component
public final class GamificationCorruptDocumentRecovery {
    static final String QUARANTINE_DIRECTORY = ".invalid-recovery";
    static final String MANIFEST = "quarantine.json";
    static final String DEFINITIONS = "definitions";
    static final String BINDINGS = "bindings";
    private static final int MAX_LISTED_QUARANTINES = 20;
    private static final long MAX_MANIFEST_BYTES = 64 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger(GamificationCorruptDocumentRecovery.class);
    private final Path directory;
    private final String definitionSuffix;
    private final String bindingSuffix;
    private final boolean enabled;
    private final ResourceStripedLock locks = new ResourceStripedLock(64);
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private volatile Steps steps = Steps.NONE;

    /** Exact bytes of one stored document, or its absence. */
    record StoredFile(String role, String fileName, boolean present, String etag, long sizeBytes, byte[] content) { }

    /** Quarantine that a crash or a failure interrupted, found by its manifest. */
    record PendingQuarantine(Path folder, GamificationQuarantineRecord record) { }

    /** Test seam around each move (simulated crash, document removed by another program); production uses NONE. */
    interface Steps {
        Steps NONE = new Steps() { };
        default void beforeMove(String role, Path source) throws IOException { }
        default void afterMove(String role) { }
    }

    @Autowired
    /** Descripción: Inicializa una instancia de GamificationCorruptDocumentRecovery con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationCorruptDocumentRecovery(GamificationProperties properties) {
        this(properties.getDirectory(), properties.getDefinitionFileSuffix(), properties.getBindingFileSuffix(), true);
    }
    /** Descripción: Inicializa una instancia de GamificationCorruptDocumentRecovery con sus dependencias y datos de entrada. Entradas: directory: ruta o ubicación utilizada por la operación; definitionSuffix: dato de entrada necesario para la operación; bindingSuffix: dato de entrada necesario para la operación; enabled: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private GamificationCorruptDocumentRecovery(Path directory, String definitionSuffix, String bindingSuffix, boolean enabled) {
        this.directory = directory.toAbsolutePath().normalize();
        this.definitionSuffix = definitionSuffix;
        this.bindingSuffix = bindingSuffix;
        this.enabled = enabled;
    }
    /** Descripción: Ejecuta la operación «disabled» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ static GamificationCorruptDocumentRecovery disabled() {
        return new GamificationCorruptDocumentRecovery(Path.of("."), ".gamification-definitions.json", ".gamification-bindings.json", false);
    }

    /** Descripción: Devuelve el monitor acotado (64 franjas) que serializa las operaciones sobre el par de un metamodelo. Entradas: metamodelId: identificador del par. Salida: objeto para synchronized. */ public Object lockFor(String metamodelId) { return locks.lockFor(metamodelId); }
    /** Descripción: Ejecuta la operación «stripeCount» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ int stripeCount() { return locks.stripeCount(); }
    /** Descripción: Sustituye el punto de prueba que rodea cada movimiento. Entradas: value: pasos (null equivale a ninguno). Salida: no devuelve valor. */ void steps(Steps value) { steps = value == null ? Steps.NONE : value; }
    /** Descripción: Nombre del fichero de un documento del par. Entradas: metamodelId y role. Salida: nombre sin ruta. */ String fileName(String metamodelId, String role) { return metamodelId + (DEFINITIONS.equals(role) ? definitionSuffix : bindingSuffix); }

    /** Descripción: Lee los bytes exactos de un documento del par sin modificarlo. Entradas: metamodelId y role (definitions o bindings). Salida: el documento con su ETag SHA-256, o ausente. */
    StoredFile stored(String metamodelId, String role) {
        String fileName = fileName(requireId(metamodelId), role);
        if(!enabled) return new StoredFile(role, fileName, false, "", 0, new byte[0]);
        Path path = safeResolve(fileName);
        try {
            if(!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return new StoredFile(role, fileName, false, "", 0, new byte[0]);
            byte[] content = BoundedIo.readInternal(path);
            return new StoredFile(role, fileName, true, etag(content), content.length, content);
        } catch(NoSuchFileException vanished) {
            return new StoredFile(role, fileName, false, "", 0, new byte[0]);
        } catch(IOException exception) {
            throw storage("Could not read the stored gamification " + role + " document", exception);
        }
    }

    /** Descripción: Lista, sin modificarlas, las cuarentenas de un metamodelo, de la más reciente a la más antigua (como mucho 20). Entradas: metamodelId. Salida: manifiestos sin rutas del servidor. */
    List<GamificationQuarantineRecord> quarantines(String metamodelId) {
        List<GamificationQuarantineRecord> records = new ArrayList<>();
        for(Path folder : folders(metamodelId)) {
            records.add(read(metamodelId, folder));
            if(records.size() >= MAX_LISTED_QUARANTINES) break;
        }
        return records;
    }

    /** Descripción: Busca la cuarentena interrumpida (manifiesto IN_PROGRESS) más reciente. Entradas: metamodelId. Salida: la cuarentena pendiente, si existe. */
    Optional<PendingQuarantine> pending(String metamodelId) {
        for(Path folder : folders(metamodelId)) {
            GamificationQuarantineRecord record = read(metamodelId, folder);
            if(GamificationQuarantineRecord.IN_PROGRESS.equals(record.status())) return Optional.of(new PendingQuarantine(folder, record));
        }
        return Optional.empty();
    }

    /** Descripción: Crea la carpeta de una cuarentena nueva y escribe su manifiesto IN_PROGRESS antes de mover nada. Entradas: metamodelId; documents: documentos presentes con su huella observada; correlationId de la petición. Salida: la cuarentena pendiente lista para completarse. */
    PendingQuarantine begin(String metamodelId, List<GamificationQuarantineRecord.Document> documents, String correlationId) {
        if(!enabled) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "Gamification recovery is not available in this configuration");
        Path root = quarantineRoot(metamodelId);
        try {
            Files.createDirectories(root);
            rejectLinks(root);
            String base = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
            Path folder = null;
            for(int attempt = 0; folder == null; attempt++) {
                Path candidate = root.resolve(attempt == 0 ? base : base + "-" + attempt);
                try { folder = Files.createDirectory(candidate); }
                catch(FileAlreadyExistsException taken) { if(attempt >= 99) throw taken; }
            }
            GamificationQuarantineRecord record = new GamificationQuarantineRecord("1.0", metamodelId, folder.getFileName().toString(),
                    GamificationQuarantineRecord.IN_PROGRESS, Instant.now().toString(), "", correlationId == null ? "" : correlationId, documents);
            write(folder, record);
            return new PendingQuarantine(folder, record);
        } catch(IOException exception) {
            throw storage("Could not prepare the gamification quarantine", exception);
        }
    }

    /**
     * Descripción: Completa una cuarentena (nueva o interrumpida). Primero comprueba que cada documento pendiente sigue siendo, byte a byte, el
     * observado; solo entonces mueve, sin sustituir nunca un destino, y anota cada resultado en el manifiesto. Un documento que ya está en la
     * carpeta con su huella se da por movido (caída entre el movimiento y la anotación) y uno que ha desaparecido del almacén queda VANISHED.
     * Entradas: metamodelId; pending: cuarentena con su manifiesto. Salida: el manifiesto COMPLETE.
     */
    GamificationQuarantineRecord finish(String metamodelId, PendingQuarantine pending) {
        Path folder = pending.folder();
        GamificationQuarantineRecord record = pending.record();
        try {
            for(GamificationQuarantineRecord.Document document : record.documents()) {
                if(!GamificationQuarantineRecord.Document.PENDING.equals(document.outcome())) continue;
                if(document.etag().equals(etagIfPresent(folder.resolve(document.fileName())))) continue;
                String current = etagIfPresent(safeResolve(document.fileName()));
                if(current != null && !current.equals(document.etag()))
                    throw new GamificationApplicationException(GamificationApplicationException.Reason.PRECONDITION_FAILED,
                            "The stored gamification " + document.role() + " document changed after it was inspected; nothing was moved. Inspect the recovery status again.");
            }
            List<GamificationQuarantineRecord.Document> documents = new ArrayList<>(record.documents());
            for(int index = 0; index < documents.size(); index++) {
                GamificationQuarantineRecord.Document document = documents.get(index);
                if(!GamificationQuarantineRecord.Document.PENDING.equals(document.outcome())) continue;
                Path source = safeResolve(document.fileName());
                Path target = folder.resolve(document.fileName());
                String outcome;
                if(document.etag().equals(etagIfPresent(target))) outcome = GamificationQuarantineRecord.Document.MOVED;
                else if(!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) outcome = GamificationQuarantineRecord.Document.VANISHED;
                else {
                    steps.beforeMove(document.role(), source);
                    try { AtomicFiles.moveNew(source, target); outcome = GamificationQuarantineRecord.Document.MOVED; }
                    catch(NoSuchFileException vanished) { outcome = GamificationQuarantineRecord.Document.VANISHED; }
                    steps.afterMove(document.role());
                }
                documents.set(index, document.withOutcome(outcome));
                record = record.withDocuments(documents);
                write(folder, record);
            }
            record = record.completed(Instant.now().toString());
            write(folder, record);
            LOG.warn("hydra.gamification_quarantined metamodelId={} quarantineId={} correlationId={} documents={}", metamodelId,
                    record.quarantineId(), record.correlationId(), record.documents());
            return record;
        } catch(IOException exception) {
            throw storage("Could not quarantine the gamification configuration; the manifest keeps what was already moved so the request can be repeated", exception);
        }
    }

    /** Descripción: Lee los bytes de un documento ya movido a una cuarentena del metamodelo. Entradas: metamodelId, quarantineId y fileName del manifiesto. Salida: los bytes, si el fichero existe. */
    Optional<byte[]> quarantined(String metamodelId, String quarantineId, String fileName) {
        Path root = quarantineRoot(metamodelId);
        Path folder = root.resolve(quarantineId).normalize();
        Path file = folder.resolve(fileName).normalize();
        if(!root.equals(folder.getParent()) || !folder.equals(file.getParent())) return Optional.empty();
        try {
            if(!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            return Optional.of(BoundedIo.readInternal(file));
        } catch(IOException exception) {
            throw storage("Could not read a quarantined gamification document", exception);
        }
    }

    /** Descripción: Ordena las carpetas de cuarentena del metamodelo, de la más reciente a la más antigua, sin seguir enlaces. Entradas: metamodelId. Salida: carpetas. */
    private List<Path> folders(String metamodelId) {
        if(!enabled) return List.of();
        Path root = quarantineRoot(metamodelId);
        if(!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try(var stream = Files.list(root)) {
            return stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
        } catch(IOException exception) {
            throw storage("Could not list the gamification quarantines", exception);
        }
    }

    /** Descripción: Lee el manifiesto de una carpeta; una carpeta sin manifiesto (versiones anteriores) se describe como LEGACY con las huellas de sus ficheros. Entradas: metamodelId y carpeta. Salida: el registro de la cuarentena. */
    private GamificationQuarantineRecord read(String metamodelId, Path folder) {
        String quarantineId = folder.getFileName().toString();
        Path manifest = folder.resolve(MANIFEST);
        try {
            if(Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    GamificationQuarantineRecord record = mapper.readValue(BoundedIo.read(manifest, MAX_MANIFEST_BYTES), GamificationQuarantineRecord.class);
                    if(metamodelId.equals(record.metamodelId()) && quarantineId.equals(record.quarantineId())) return record;
                } catch(JacksonException | IOException unreadable) {
                    LOG.warn("hydra.gamification_quarantine_manifest_unreadable metamodelId={} quarantineId={}", metamodelId, quarantineId, unreadable);
                }
                return new GamificationQuarantineRecord("1.0", metamodelId, quarantineId, GamificationQuarantineRecord.UNREADABLE, "", "", "", List.of());
            }
            List<GamificationQuarantineRecord.Document> documents = new ArrayList<>();
            try(var files = Files.list(folder)) {
                for(Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    String name = file.getFileName().toString();
                    String role = name.endsWith(definitionSuffix) ? DEFINITIONS : name.endsWith(bindingSuffix) ? BINDINGS : "unknown";
                    byte[] content = BoundedIo.readInternal(file);
                    documents.add(new GamificationQuarantineRecord.Document(role, name, etag(content), content.length, "UNKNOWN",
                            GamificationQuarantineRecord.Document.MOVED));
                }
            }
            return new GamificationQuarantineRecord("1.0", metamodelId, quarantineId, GamificationQuarantineRecord.LEGACY, "", "", "", documents);
        } catch(IOException exception) {
            throw storage("Could not read a gamification quarantine", exception);
        }
    }

    /** Descripción: Publica el manifiesto de forma atómica (temporal en la misma carpeta y movimiento). Entradas: carpeta y registro. Salida: no devuelve valor. */
    private void write(Path folder, GamificationQuarantineRecord record) throws IOException {
        AtomicFiles.writeReplacing(folder.resolve(MANIFEST), mapper.writeValueAsBytes(record));
    }
    /** Descripción: Calcula la huella de un fichero si existe. Entradas: ruta. Salida: ETag SHA-256 o null. */
    private String etagIfPresent(Path path) throws IOException {
        if(!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null;
        try { return etag(BoundedIo.readInternal(path)); }
        catch(NoSuchFileException vanished) { return null; }
    }
    /** Descripción: Resuelve la carpeta de cuarentenas de un metamodelo dentro del directorio de gamificación. Entradas: metamodelId validado. Salida: ruta normalizada. */
    private Path quarantineRoot(String metamodelId) {
        Path root = safeResolve(QUARANTINE_DIRECTORY).resolve(requireId(metamodelId)).normalize();
        if(!root.startsWith(directory)) throw new IllegalStateException("Gamification quarantine escaped its data directory");
        return root;
    }
    /** Descripción: Rechaza que la carpeta de cuarentenas o su raíz sean enlaces simbólicos, que podrían sacar los documentos del directorio de datos. Entradas: carpeta del metamodelo. Salida: no devuelve valor. */
    private void rejectLinks(Path root) throws IOException {
        if(Files.isSymbolicLink(root) || Files.isSymbolicLink(root.getParent()))
            throw new IOException("Gamification quarantine folder must not be a symbolic link");
    }
    /** Descripción: Valida el identificador con la misma política que el almacén de documentos. Entradas: metamodelId. Salida: el identificador. */
    private static String requireId(String metamodelId) { return StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId"); }
    /** Descripción: Ejecuta la operación «safeResolve» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: child: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeResolve(String child) {
        Path resolved = directory.resolve(child).normalize();
        if(!resolved.startsWith(directory) || resolved.equals(directory)) throw new IllegalArgumentException("Gamification document path escaped its data directory");
        return resolved;
    }
    /** Descripción: Calcula el ETag con el mismo algoritmo que el almacén (SHA-256 de los bytes exactos, entre comillas). Entradas: bytes. Salida: ETag. */
    static String etag(byte[] content) {
        try { return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\""; }
        catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 must be available", exception); }
    }
    /** Descripción: Error de almacenamiento (500): el mensaje público lo genera el manejador de la API y el detalle queda en el log. Entradas: mensaje y causa. Salida: la excepción. */
    private static GamificationApplicationException storage(String message, IOException cause) {
        return new GamificationApplicationException(GamificationApplicationException.Reason.STORAGE_ERROR, message, cause);
    }
}

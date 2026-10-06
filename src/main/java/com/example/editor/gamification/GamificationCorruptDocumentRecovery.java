package com.example.editor.gamification;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.editor.concurrent.ResourceStripedLock;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;

/**
 * Quarantines only malformed gamification JSON. Normal I/O, permission and
 * concurrency errors are never converted into an empty configuration.
 */
@Component
public final class GamificationCorruptDocumentRecovery {
    private final Path directory;
    private final String definitionSuffix;
    private final String bindingSuffix;
    private final boolean enabled;
    private final ResourceStripedLock locks = new ResourceStripedLock(64);

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

    /** Descripción: Ejecuta la operación «recoverIfMalformed» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: metamodelId: identificador que delimita el recurso objetivo; exception: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean recoverIfMalformed(String metamodelId, CanonicalDocumentStoreException exception) {
        if(!enabled || exception == null || !isMalformed(exception)) return false;
        synchronized(locks.lockFor(metamodelId)) {
            Path definition = safeResolve(metamodelId + definitionSuffix);
            Path binding = safeResolve(metamodelId + bindingSuffix);
            if(!Files.isRegularFile(definition) && !Files.isRegularFile(binding)) return false;
            String stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
            Path quarantine = safeResolve(".invalid-recovery").resolve(safeSegment(metamodelId)).resolve(stamp).normalize();
            if(!quarantine.startsWith(directory)) throw new IllegalStateException("Gamification quarantine escaped its data directory");
            try {
                Files.createDirectories(quarantine);
                moveIfPresent(definition, quarantine.resolve(definition.getFileName()));
                moveIfPresent(binding, quarantine.resolve(binding.getFileName()));
                return true;
            } catch(IOException io) {
                throw new GamificationApplicationException(GamificationApplicationException.Reason.STORAGE_ERROR,
                        "Could not quarantine malformed gamification documents", io);
            }
        }
    }

    /** Descripción: Ejecuta la operación «stripeCount» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ int stripeCount() { return locks.stripeCount(); }

    /** Descripción: Evalúa la condición «isMalformed» sin modificar el estado externo. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isMalformed(CanonicalDocumentStoreException exception) {
        String message = exception.getMessage();
        return message != null && message.startsWith("Could not parse document:");
    }
    /** Descripción: Ejecuta la operación «safeResolve» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: child: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeResolve(String child) {
        Path resolved = directory.resolve(child).normalize();
        if(!resolved.startsWith(directory)) throw new IllegalArgumentException("Gamification document path escaped its data directory");
        return resolved;
    }
    /** Descripción: Ejecuta la operación «safeSegment» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeSegment(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    /** Descripción: Ejecuta la operación «moveIfPresent» correspondiente a la responsabilidad de GamificationCorruptDocumentRecovery. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void moveIfPresent(Path source, Path destination) throws IOException {
        if(!Files.isRegularFile(source)) return;
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException exception) { Files.move(source, destination); }
    }
}

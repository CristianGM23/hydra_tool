package com.example.editor.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.editor.model.ModelDocumentException.Reason;

/**
 * Administra el ciclo de vida y la exclusión mutua de las sesiones de modelo.
 *
 * Cada sesión posee su propio lock. Dos modelos independientes pueden avanzar en
 * paralelo, mientras que las mutaciones de un mismo modelo siguen estando
 * serializadas. El manager aplica además límite de sesiones e invalidación por
 * inactividad para evitar crecimiento ilimitado del heap.
 */
@Component
public class ModelSessionManager {
    private final ModelSessionProperties properties;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<String, SessionSlot> sessions = new ConcurrentHashMap<>();
    private final AtomicLong nextModelNumber = new AtomicLong(1);
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    @Autowired
    /** Descripción: Inicializa una instancia de ModelSessionManager con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ModelSessionManager(ModelSessionProperties properties) {
        this(properties, System::nanoTime);
    }

    /** Descripción: Inicializa una instancia de ModelSessionManager con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; nanoTime: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ModelSessionManager(ModelSessionProperties properties, LongSupplier nanoTime) {
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    /** Genera un identificador estable dentro del proceso actual. */
    /** Descripción: Ejecuta la operación «nextModelId» correspondiente a la responsabilidad de ModelSessionDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String nextModelId() {
        return "model-" + nextModelNumber.getAndIncrement();
    }

    /** Registra una sesión nueva después de liberar sesiones expiradas. */
    /** Descripción: Ejecuta la operación «register» correspondiente a la responsabilidad de ModelSessionDescriptor. Entradas: session: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void register(ModelSession session) {
        if(session == null) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "Model session must not be null");
        }
        lifecycleLock.lock();
        try {
            purgeExpiredLocked();
            if(sessions.size() >= properties.getMaxActive()) {
                throw new ModelDocumentException(Reason.STORAGE_ERROR,
                        "Maximum number of active model sessions reached: " + properties.getMaxActive());
            }
            SessionSlot previous = sessions.putIfAbsent(session.id, new SessionSlot(session, nanoTime.getAsLong()));
            if(previous != null) {
                throw new ModelDocumentException(Reason.STORAGE_ERROR, "Model session already exists: " + session.id);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** Ejecuta una operación bajo el lock exclusivo de la sesión indicada. */
    /** Descripción: Ejecuta la operación «withSession» correspondiente a la responsabilidad de ModelSessionDescriptor. Entradas: modelId: identificador que delimita el recurso objetivo; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ <T> T withSession(String modelId, Function<ModelSession, T> operation) {
        String normalizedId = validateId(modelId);
        SessionSlot slot = sessions.get(normalizedId);
        if(slot == null) {
            throw new ModelDocumentException(Reason.NOT_FOUND, "Model document was not found: " + normalizedId);
        }
        slot.lock.lock();
        boolean operationStarted = false;
        try {
            if(sessions.get(normalizedId) != slot || expired(slot, nanoTime.getAsLong())) {
                sessions.remove(normalizedId, slot);
                throw new ModelDocumentException(Reason.NOT_FOUND, "Model document was not found: " + normalizedId);
            }
            slot.lastAccessNanos = nanoTime.getAsLong();
            operationStarted = true;
            return operation.apply(slot.session);
        } finally {
            if(operationStarted) slot.lastAccessNanos = nanoTime.getAsLong();
            slot.lock.unlock();
        }
    }

    /** Devuelve un snapshot ligero para diagnóstico y pruebas de capacidad. */
    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de ModelSessionDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ ModelSessionManagerSnapshot snapshot() {
        purgeExpired();
        List<ModelSessionDescriptor> descriptors = sessions.values().stream()
                .map(slot -> new ModelSessionDescriptor(slot.session.id, slot.session.metamodelId,
                        slot.session.fileName, slot.lastAccessNanos))
                .sorted(Comparator.comparing(ModelSessionDescriptor::modelId)).toList();
        return new ModelSessionManagerSnapshot(descriptors.size(), properties.getMaxActive(),
                properties.getIdleTimeout(), descriptors);
    }

    /** Invalida cualquier sesión abierta sobre un XMI que acaba de eliminarse. */
    /** Descripción: Elimina o libera el recurso gestionado por «invalidateByFileName» de forma controlada. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ int invalidateByFileName(String fileName) {
        if(fileName == null || fileName.isBlank()) return 0;
        lifecycleLock.lock();
        try {
            List<String> matchingIds = sessions.entrySet().stream()
                    .filter(entry -> fileName.equals(entry.getValue().session.fileName))
                    .map(java.util.Map.Entry::getKey).toList();
            int removed = 0;
            for(String id : matchingIds) {
                SessionSlot slot = sessions.get(id);
                if(slot == null) continue;
                slot.lock.lock();
                try { if(sessions.remove(id, slot)) removed++; } finally { slot.lock.unlock(); }
            }
            return removed;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** Elimina sesiones inactivas sin interrumpir una operación que ya posee su lock. */
    /** Descripción: Ejecuta la operación «purgeExpired» correspondiente a la responsabilidad de ModelSessionDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ int purgeExpired() {
        lifecycleLock.lock();
        try {
            return purgeExpiredLocked();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** Descripción: Ejecuta la operación «purgeExpiredLocked» correspondiente a la responsabilidad de ModelSessionDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int purgeExpiredLocked() {
        long now = nanoTime.getAsLong();
        List<String> expiredIds = new ArrayList<>();
        sessions.forEach((id, slot) -> {
            if(expired(slot, now) && slot.lock.tryLock()) {
                try {
                    if(expired(slot, now)) expiredIds.add(id);
                } finally {
                    slot.lock.unlock();
                }
            }
        });
        expiredIds.forEach(sessions::remove);
        return expiredIds.size();
    }


    /** Descripción: Ejecuta la operación «expired» correspondiente a la responsabilidad de ModelSessionDescriptor. Entradas: slot: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean expired(SessionSlot slot, long now) {
        long timeoutNanos = properties.getIdleTimeout().toNanos();
        return timeoutNanos > 0 && now - slot.lastAccessNanos >= timeoutNanos;
    }

    /** Descripción: Valida las condiciones necesarias para «validateId» y rechaza entradas no válidas. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String validateId(String modelId) {
        if(modelId == null || modelId.isBlank()) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "Model id must not be empty");
        }
        return modelId;
    }

    private static final class SessionSlot {
        private final ModelSession session;
        private final ReentrantLock lock = new ReentrantLock();
        private volatile long lastAccessNanos;

        /** Descripción: Inicializa una instancia de SessionSlot con sus dependencias y datos de entrada. Entradas: session: dato de entrada necesario para la operación; lastAccessNanos: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private SessionSlot(ModelSession session, long lastAccessNanos) {
            this.session = session;
            this.lastAccessNanos = lastAccessNanos;
        }
    }

    record ModelSessionDescriptor(String modelId, String metamodelId, String fileName, long lastAccessNanos) { }
    record ModelSessionManagerSnapshot(int activeSessions, int maximumSessions, Duration idleTimeout,
            List<ModelSessionDescriptor> sessions) { }
}

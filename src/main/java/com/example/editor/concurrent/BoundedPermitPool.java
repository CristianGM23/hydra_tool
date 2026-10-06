package com.example.editor.concurrent;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fair, fixed-capacity permit pool with bounded waiting and lightweight saturation metrics.
 * The pool never grows after construction and every acquired permit is AutoCloseable.
 */
public final class BoundedPermitPool {
    private final int capacity;
    private final Semaphore permits;
    private final AtomicLong acquisitions = new AtomicLong();
    private final AtomicLong rejections = new AtomicLong();

    /** Descripción: Inicializa una instancia de BoundedPermitPool con sus dependencias y datos de entrada. Entradas: capacity: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public BoundedPermitPool(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.permits = new Semaphore(this.capacity, true);
    }

    /** Descripción: Ejecuta la operación «tryAcquire» correspondiente a la responsabilidad de BoundedPermitPool. Entradas: timeout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Permit tryAcquire(Duration timeout) throws InterruptedException {
        long millis = timeout == null ? 0L : Math.max(0L, timeout.toMillis());
        boolean acquired = permits.tryAcquire(millis, TimeUnit.MILLISECONDS);
        if(!acquired) {
            rejections.incrementAndGet();
            return null;
        }
        acquisitions.incrementAndGet();
        return new Permit(this);
    }

    /** Descripción: Ejecuta la operación «tryAcquire» correspondiente a la responsabilidad de BoundedPermitPool. Entradas: timeout: dato de entrada necesario para la operación; unit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Permit tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        long value = Math.max(0L, timeout);
        boolean acquired = permits.tryAcquire(value, unit == null ? TimeUnit.MILLISECONDS : unit);
        if(!acquired) {
            rejections.incrementAndGet();
            return null;
        }
        acquisitions.incrementAndGet();
        return new Permit(this);
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de BoundedPermitPool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Snapshot snapshot() {
        int available = permits.availablePermits();
        return new Snapshot(capacity, available, capacity - available, permits.getQueueLength(), acquisitions.get(), rejections.get());
    }

    /** Descripción: Ejecuta la operación «availablePermits» correspondiente a la responsabilidad de BoundedPermitPool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int availablePermits() { return permits.availablePermits(); }

    /** Descripción: Ejecuta la operación «release» correspondiente a la responsabilidad de BoundedPermitPool. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void release() { permits.release(); }

    public static final class Permit implements AutoCloseable {
        private BoundedPermitPool owner;
        /** Descripción: Inicializa una instancia de Permit con sus dependencias y datos de entrada. Entradas: owner: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Permit(BoundedPermitPool owner) { this.owner = owner; }
        /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void close() {
            BoundedPermitPool current = owner;
            if(current == null) return;
            owner = null;
            current.release();
        }
    }

    public record Snapshot(int capacity, int available, int inUse, int queued, long acquisitions, long rejections) {
        /** Descripción: Ejecuta la operación «saturated» correspondiente a la responsabilidad de BoundedPermitPool. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean saturated() { return available == 0; }
    }
}

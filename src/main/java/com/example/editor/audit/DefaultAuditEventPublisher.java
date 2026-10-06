package com.example.editor.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.example.editor.scope.ResourceScope;
import com.example.editor.scope.WorkspaceContext;

/**
 * Pipeline asíncrono y acotado. Captura snapshots inmutables en el hilo llamador,
 * calcula el diff fuera del camino crítico y persiste en lotes.
 */
@Component
public class DefaultAuditEventPublisher implements AuditEventPublisher, InitializingBean, DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAuditEventPublisher.class);
    private final AuditLogProperties properties;
    private final AuditActorContext actorContext;
    private final WorkspaceContext workspaceContext;
    private final AuditSnapshotService snapshots;
    private final AuditDiffStrategyRegistry diffStrategies;
    private final AuditLogStore store;
    private final ArrayBlockingQueue<AuditCapturedEvent> queue;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong synchronousFallbacks = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong persisted = new AtomicLong();
    private final ReentrantLock flushLock = new ReentrantLock(true);
    private final Semaphore wakeSignal = new Semaphore(0);
    private volatile ExecutorService storageExecutor;
    private Thread worker;

    /** Descripción: Inicializa una instancia de DefaultAuditEventPublisher con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; actorContext: dato de entrada necesario para la operación; workspaceContext: dato de entrada necesario para la operación; snapshots: dato de entrada necesario para la operación; diffStrategies: dato de entrada necesario para la operación; store: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DefaultAuditEventPublisher(AuditLogProperties properties, AuditActorContext actorContext, WorkspaceContext workspaceContext,
            AuditSnapshotService snapshots, AuditDiffStrategyRegistry diffStrategies, AuditLogStore store) {
        this.properties = properties;
        this.actorContext = actorContext;
        this.workspaceContext = workspaceContext;
        this.snapshots = snapshots;
        this.diffStrategies = diffStrategies;
        this.store = store;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
    }

    @Override
    /** Descripción: Ejecuta la operación «afterPropertiesSet» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void afterPropertiesSet() {
        if(!properties.isEnabled() || closed.get() || running.getAndSet(true)) return;
        storageExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "hydra-audit-storage-writer");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        worker = new Thread(this::runWorker, "hydra-audit-writer");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de DefaultAuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void publish(AuditEvent event) {
        if(!properties.isEnabled() || event == null) return;
        if(!running.get()) { dropped.incrementAndGet(); return; }
        try {
            AuditCapturedEvent captured = capture(event);
            if(queue.offer(captured)) { accepted.incrementAndGet(); wakeSignal.release(); return; }
            overflow(captured);
        } catch(RuntimeException exception) {
            failures.incrementAndGet();
            LOGGER.warn("Audit capture failed without affecting the business operation: {}", exception.getMessage());
        }
    }

    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditPipelineStatus status() {
        return new AuditPipelineStatus(properties.isEnabled(), running.get(), queue.size(), properties.getQueueCapacity(),
                accepted.get(), synchronousFallbacks.get(), dropped.get(), failures.get(), persisted.get());
    }

    @Override
    /** Descripción: Ejecuta la operación «flushPending» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void flushPending() {
        if(!properties.isEnabled() || !running.get()) return;
        flushLock.lock();
        try { flushQueueBatch(Integer.MAX_VALUE); }
        finally { flushLock.unlock(); }
    }

    @Override
    /** Descripción: Ejecuta la operación «destroy» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: no requiere parámetros de entrada. Salida: finaliza de forma acotada workers, drenaje y almacenamiento. */ public void destroy() {
        if(!closed.compareAndSet(false, true)) return;
        boolean wasRunning = running.getAndSet(false);
        Thread current = worker;
        if(wasRunning && current != null) {
            current.interrupt();
            try { current.join(properties.getShutdownWaitMillis()); }
            catch(InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
        if(current == null || !current.isAlive()) flushRemainingBounded();
        else abandonQueuedOnShutdown("audit worker did not terminate within the configured shutdown wait");
        shutdownStorageExecutor();
    }

    /** Descripción: Ejecuta la operación «capture» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditCapturedEvent capture(AuditEvent event) {
        AuditActor actor = snapshots.actor(actorContext.currentActor());
        ResourceScope scope = workspaceContext.currentScope();
        String correlationId = event.correlationId().isBlank() ? event.eventId() : event.correlationId();
        return new AuditCapturedEvent(event.eventId(), Instant.now(), actor,
                scope == null ? ResourceScope.LOCAL_WORKSPACE_ID : scope.workspaceId(), event.activityType(), event.moduleId(), event.action(),
                event.importance(), event.result(), event.resource(), snapshots.snapshot(event.before()), snapshots.snapshot(event.after()),
                snapshots.metadata(event.metadata()), correlationId, event.parentEventId());
    }

    /** Descripción: Ejecuta la operación «overflow» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void overflow(AuditCapturedEvent event) {
        if(properties.isDropLowValueOnOverflow() && lowValue(event)) { dropped.incrementAndGet(); return; }
        String policy = properties.getOverflowPolicy().trim().toUpperCase(Locale.ROOT);
        switch(policy) {
            case "DROP_OLDEST" -> {
                if(queue.poll() != null) dropped.incrementAndGet();
                if(queue.offer(event)) { accepted.incrementAndGet(); wakeSignal.release(); } else dropped.incrementAndGet();
            }
            case "DROP_NEWEST" -> dropped.incrementAndGet();
            default -> {
                synchronousFallbacks.incrementAndGet();
                try { writeWithRetry(List.of(process(event))); }
                catch(RuntimeException exception) {
                    failures.incrementAndGet();
                    LOGGER.warn("Audit synchronous fallback failed without affecting the business operation: {}", exception.getMessage());
                }
            }
        }
    }


    /** Descripción: Ejecuta la operación «lowValue» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean lowValue(AuditCapturedEvent event) {
        String action = event.action() == null ? "" : event.action().trim().toUpperCase(Locale.ROOT);
        String activityType = event.activityType() == null ? "" : event.activityType().trim().toLowerCase(Locale.ROOT);
        return AuditActions.NAVIGATE.equals(action) || AuditActions.OPEN.equals(action) || AuditActions.CLOSE.equals(action)
                || activityType.startsWith("navigation.") || activityType.startsWith("ui.");
    }

    /** Descripción: Procesa la operación «runWorker» coordinando las responsabilidades de DefaultAuditEventPublisher. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void runWorker() {
        while(running.get() || !queue.isEmpty()) {
            try {
                wakeSignal.tryAcquire(properties.getFlushIntervalMillis(), TimeUnit.MILLISECONDS);
                if(queue.isEmpty()) continue;
                flushLock.lock();
                try { flushQueueBatch(properties.getBatchSize()); }
                finally { flushLock.unlock(); }
            } catch(InterruptedException exception) {
                if(!running.get()) break;
                Thread.currentThread().interrupt();
                break;
            } catch(RuntimeException exception) {
                failures.incrementAndGet();
                LOGGER.warn("Audit worker batch failed: {}", exception.getMessage());
            }
        }
    }

    /** Descripción: Procesa la operación «processSafely» coordinando las responsabilidades de DefaultAuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditEntry processSafely(AuditCapturedEvent event) {
        try { return process(event); }
        catch(RuntimeException exception) {
            failures.incrementAndGet();
            LOGGER.warn("Audit event {} could not be processed: {}", event.eventId(), exception.getMessage());
            return null;
        }
    }

    /** Descripción: Procesa la operación «process» coordinando las responsabilidades de DefaultAuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditEntry process(AuditCapturedEvent event) {
        AuditDiffStrategy.AuditDiffResult diff = diffStrategies.diff(event);
        return new AuditEntry(AuditEntry.SCHEMA_VERSION, event.eventId(), event.timestamp(), event.actor(), event.workspaceId(),
                event.activityType(), event.moduleId(), event.action(), event.importance(), event.result(), event.resource(), diff.changes(),
                diff.truncated(), event.metadata(), event.correlationId(), event.parentEventId());
    }

    /** Descripción: Actualiza el estado gestionado por DefaultAuditEventPublisher mediante la operación «writeWithRetry». Entradas: entries: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeWithRetry(List<AuditEntry> entries) {
        RuntimeException last = null;
        for(int attempt = 0; attempt <= properties.getWriteRetries(); attempt++) {
            try {
                appendWithTimeout(entries);
                persisted.addAndGet(entries.size());
                return;
            } catch(RuntimeException exception) {
                last = exception;
                if(attempt < properties.getWriteRetries()) {
                    try { Thread.sleep(Math.min(1000L, 50L << Math.min(attempt, 4))); }
                    catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        failures.addAndGet(entries.size());
        throw last == null ? new AuditStorageException("Audit write failed", null) : last;
    }

    /** Descripción: Ejecuta una escritura física mediante un único worker acotado y aplica un timeout para impedir que un proveedor de almacenamiento bloquee indefinidamente el pipeline. Entradas: entries: lote que debe persistirse. Salida: completa la escritura o comunica timeout, rechazo o fallo del proveedor. */ private void appendWithTimeout(List<AuditEntry> entries) {
        ExecutorService executor = storageExecutor;
        if(executor == null || executor.isShutdown()) throw new AuditStorageException("Audit storage writer is not available", null);
        Future<?> future;
        try { future = executor.submit(() -> store.appendBatch(entries)); }
        catch(RejectedExecutionException exception) { throw new AuditStorageException("Audit storage writer is saturated", exception); }
        try { future.get(properties.getWriteTimeoutMillis(), TimeUnit.MILLISECONDS); }
        catch(TimeoutException exception) {
            future.cancel(true);
            throw new AuditStorageException("Audit write exceeded " + properties.getWriteTimeoutMillis() + " ms", exception);
        } catch(InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AuditStorageException("Audit write interrupted", exception);
        } catch(ExecutionException exception) {
            Throwable cause = exception.getCause();
            if(cause instanceof RuntimeException runtime) throw runtime;
            throw new AuditStorageException("Audit storage writer failed", cause);
        }
    }

    /** Descripción: Intenta drenar la cola durante el cierre sin esperar indefinidamente al lock del worker. Entradas: no requiere parámetros. Salida: persiste el trabajo restante cuando es posible y contabiliza explícitamente lo abandonado. */ private void flushRemainingBounded() {
        boolean locked = false;
        try {
            locked = flushLock.tryLock(properties.getShutdownFlushLockMillis(), TimeUnit.MILLISECONDS);
            if(!locked) { abandonQueuedOnShutdown("audit flush lock remained busy during shutdown"); return; }
            flushQueueBatch(Integer.MAX_VALUE);
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            abandonQueuedOnShutdown("audit shutdown flush was interrupted");
        } finally { if(locked) flushLock.unlock(); }
    }

    /** Descripción: Vacía y contabiliza eventos que no pueden drenarse durante un cierre acotado. Entradas: reason: causa diagnóstica. Salida: deja la cola vacía y registra el número de eventos descartados. */ private void abandonQueuedOnShutdown(String reason) {
        int abandoned = queue.size();
        if(abandoned <= 0) return;
        queue.clear();
        dropped.addAndGet(abandoned);
        LOGGER.warn("{}; {} queued audit events were abandoned", reason, abandoned);
    }

    /** Descripción: Solicita el cierre del executor físico de almacenamiento con espera acotada, preservando la interrupción del caller. Entradas: no requiere parámetros. Salida: libera la infraestructura de escritura sin bloquear indefinidamente el apagado. */ private void shutdownStorageExecutor() {
        ExecutorService executor = storageExecutor;
        storageExecutor = null;
        if(executor == null) return;
        executor.shutdownNow();
        try {
            if(!executor.awaitTermination(properties.getShutdownWaitMillis(), TimeUnit.MILLISECONDS))
                LOGGER.warn("Audit storage writer did not terminate within {} ms", properties.getShutdownWaitMillis());
        } catch(InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    /** Descripción: Ejecuta la operación «flushQueueBatch» correspondiente a la responsabilidad de DefaultAuditEventPublisher. Entradas: maximum: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void flushQueueBatch(int maximum) {
        int remainingLimit = maximum <= 0 ? Integer.MAX_VALUE : maximum;
        List<AuditCapturedEvent> remaining = new ArrayList<>(Math.min(properties.getBatchSize(), Math.max(1, queue.size())));
        while(!queue.isEmpty() && remainingLimit > 0) {
            remaining.clear();
            int batchLimit = Math.min(properties.getBatchSize(), remainingLimit);
            queue.drainTo(remaining, batchLimit);
            if(remaining.isEmpty()) break;
            remainingLimit -= remaining.size();
            List<AuditEntry> entries = remaining.stream().map(this::processSafely).filter(java.util.Objects::nonNull).toList();
            if(entries.isEmpty()) continue;
            try { writeWithRetry(entries); }
            catch(RuntimeException exception) { LOGGER.warn("Audit flush failed: {}", exception.getMessage()); }
        }
    }
}

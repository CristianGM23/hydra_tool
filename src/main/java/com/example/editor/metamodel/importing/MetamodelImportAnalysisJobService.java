package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.audit.AuditRequestContext;
import com.example.editor.runtime.job.HydraJobOrchestrator;
import com.example.editor.runtime.job.HydraJobProperties;
import com.example.editor.runtime.job.HydraJobStatus;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Ejecuta análisis asíncronos con etapas exactas y logs descargables.
 */
@Service
public class MetamodelImportAnalysisJobService {

    private final MetamodelImportAnalysisProcessor processor;
    private final Executor executor;
    private final MetamodelImportProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService watchdog;
    private final boolean ownsWatchdog;
    private final HydraJobOrchestrator jobOrchestrator;
    private final Map<String, MutableAnalysisJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, JobExecution> executions = new ConcurrentHashMap<>();

    @Autowired
    /** Inicializa el servicio con pools independientes de trabajo y vigilancia. */
    /** Descripción: Inicializa una instancia de MetamodelImportAnalysisJobService con sus dependencias y datos de entrada. Entradas: processor: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; watchdog: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelImportAnalysisJobService(MetamodelImportAnalysisProcessor processor,
            @Qualifier("metamodelImportAnalysisExecutor") Executor executor, MetamodelImportProperties properties,
            @Qualifier("metamodelImportAnalysisWatchdog") ScheduledExecutorService watchdog, HydraJobOrchestrator jobOrchestrator) {
        this(processor, executor, properties, Clock.systemUTC(), watchdog, false, jobOrchestrator);
    }

    /** Constructor de compatibilidad para pruebas focales. */
    /** Descripción: Inicializa una instancia de MetamodelImportAnalysisJobService con sus dependencias y datos de entrada. Entradas: processor: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; clock: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ MetamodelImportAnalysisJobService(MetamodelImportAnalysisProcessor processor, Executor executor,
            MetamodelImportProperties properties, Clock clock) {
        this(processor, executor, properties, clock, createFallbackWatchdog(), true, localJobOrchestrator(clock));
    }

    /** Descripción: Inicializa una instancia de MetamodelImportAnalysisJobService con sus dependencias y datos de entrada. Entradas: processor: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; clock: dato de entrada necesario para la operación; watchdog: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ MetamodelImportAnalysisJobService(MetamodelImportAnalysisProcessor processor, Executor executor,
            MetamodelImportProperties properties, Clock clock, ScheduledExecutorService watchdog) {
        this(processor, executor, properties, clock, watchdog, false, localJobOrchestrator(clock));
    }

    /** Descripción: Inicializa el servicio y registra si el watchdog recibido pertenece al lifecycle de esta instancia. Entradas: processor, executor, properties, clock, watchdog y ownsWatchdog. Salida: deja el servicio preparado para ejecutar y cerrar sus recursos de forma determinista. */
    /** Descripción: Inicializa una instancia de MetamodelImportAnalysisJobService con sus dependencias y datos de entrada. Entradas: processor: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; clock: dato de entrada necesario para la operación; watchdog: dato de entrada necesario para la operación; ownsWatchdog: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private MetamodelImportAnalysisJobService(MetamodelImportAnalysisProcessor processor, Executor executor,
            MetamodelImportProperties properties, Clock clock, ScheduledExecutorService watchdog, boolean ownsWatchdog, HydraJobOrchestrator jobOrchestrator) {
        this.processor = processor;
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
        this.watchdog = watchdog;
        this.ownsWatchdog = ownsWatchdog;
        this.jobOrchestrator = jobOrchestrator;
    }

    /**
     * Crea un trabajo y conserva el archivo en memoria durante el análisis.
     *
     * @param fileName contiene el nombre.
     * @param mediaType contiene el tipo MIME.
     * @param content contiene los bytes.
     * @param options contiene la configuración.
     * @return primera instantánea.
     */
    /** Descripción: Crea o prepara el resultado de «start» dentro de MutableAnalysisJob. Entradas: fileName: archivo o contenido que debe procesarse; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportAnalysisJobResponse start(String fileName, String mediaType, byte[] content, MetamodelImportOptions options) {
        cleanupExpiredJobs();
        String jobId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        MetamodelImportOptions effectiveOptions = options == null ? MetamodelImportOptions.defaults() : options;
        MutableAnalysisJob job = new MutableAnalysisJob(jobId, safeFileName(fileName), now, effectiveOptions, sha256(content), properties);
        String details = "fileName=" + safeFileName(fileName) + System.lineSeparator() + "mediaType=" + safeText(mediaType) + System.lineSeparator() + "sizeBytes=" + (content == null ? 0 : content.length);
        job.completeInitialStage(MetamodelImportAnalysisStage.FILE_RECEIVED, "The file was received and queued for analysis", details, now);
        jobs.put(jobId, job);
        registerSharedJob(job);

        schedule(job, () -> run(job, fileName, mediaType, content, effectiveOptions), null);
        return job.snapshot(clock.instant());
    }

    /**
     * Crea un trabajo sobre un upload ya validado y conservado temporalmente fuera del heap.
     * El propio trabajo elimina el staging al terminar o si no puede entrar en el executor.
     */
    /** Descripción: Crea o prepara el resultado de «start» dentro de MutableAnalysisJob. Entradas: staged: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportAnalysisJobResponse start(MetamodelImportStagedFile staged, MetamodelImportOptions options) {
        if(staged == null){throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Staged metamodel import file is required");}
        cleanupExpiredJobs();
        String jobId = UUID.randomUUID().toString(); Instant now = clock.instant();
        MetamodelImportOptions effectiveOptions = options == null ? MetamodelImportOptions.defaults() : options;
        MutableAnalysisJob job = new MutableAnalysisJob(jobId, safeFileName(staged.fileName()), now, effectiveOptions, staged.sha256(), properties);
        String details = "fileName=" + safeFileName(staged.fileName()) + System.lineSeparator() + "mediaType=" + safeText(staged.mediaType())
                + System.lineSeparator() + "sizeBytes=" + staged.sizeBytes() + System.lineSeparator() + "sha256=" + staged.sha256() + System.lineSeparator() + "storage=STAGED_FILE";
        job.completeInitialStage(MetamodelImportAnalysisStage.FILE_RECEIVED, "The file was received and queued for analysis", details, now);
        jobs.put(jobId, job);
        registerSharedJob(job);
        schedule(job, () -> run(job, staged, effectiveOptions), staged);
        return job.snapshot(clock.instant());
    }

    /**
     * Devuelve el estado actual.
     *
     * @param jobId identifica el trabajo.
     * @return instantánea actual.
     */
    /** Descripción: Obtiene la información correspondiente a «get» dentro de MutableAnalysisJob. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportAnalysisJobResponse get(String jobId) {
        return requireJob(jobId).snapshot(clock.instant());
    }

    /** Devuelve el snapshot canónico de un análisis completado para importarlo sin volver a parsear la fuente. */
    /** Descripción: Ejecuta la operación «prepared» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelPreparedImport prepared(String jobId) {
        MutableAnalysisJob job = requireJob(jobId);
        return job.prepared();
    }

    /**
     * Devuelve el log de una etapa.
     *
     * @param jobId identifica el trabajo.
     * @param stageId identifica la etapa.
     * @return archivo de texto.
     */
    /** Descripción: Obtiene la información correspondiente a «getStageLog» dentro de MutableAnalysisJob. Entradas: jobId: identificador que delimita el recurso objetivo; stageId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportAnalysisLogArtifact getStageLog(String jobId, String stageId) {
        MutableAnalysisJob job = requireJob(jobId);
        MetamodelImportAnalysisStage stage = MetamodelImportAnalysisStage.fromId(stageId);
        String content = job.stageLog(stage, clock.instant());
        String fileName = job.baseFileName() + "-" + stage.id().toLowerCase(java.util.Locale.ROOT) + ".log";
        return new MetamodelImportAnalysisLogArtifact(fileName, "text/plain;charset=UTF-8", content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Devuelve el log unificado.
     *
     * @param jobId identifica el trabajo.
     * @return archivo de texto.
     */
    /** Descripción: Obtiene la información correspondiente a «getCombinedLog» dentro de MutableAnalysisJob. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportAnalysisLogArtifact getCombinedLog(String jobId) {
        MutableAnalysisJob job = requireJob(jobId);
        String content = job.combinedLog(clock.instant());
        return new MetamodelImportAnalysisLogArtifact(job.baseFileName() + "-analysis.log", "text/plain;charset=UTF-8", content.getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Ejecuta la operación «schedule» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: job: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; staged: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void schedule(MutableAnalysisJob job, Runnable operation, MetamodelImportStagedFile staged) {
        JobExecution execution = new JobExecution(staged);
        FutureTask<Void> task = new FutureTask<>(() -> {
            execution.started.set(true);
            try {
                operation.run();
            } finally {
                finishExecution(job.jobId, execution);
            }
            return null;
        });
        execution.task = task;
        executions.put(job.jobId, execution);
        try {
            execution.deadline = watchdog.schedule(() -> expireExecution(job.jobId, execution, "IMPORT_ANALYSIS_TIMED_OUT",
                    "Metamodel import analysis exceeded the configured total duration"),
                    properties.getAnalysisMaximumDurationSeconds(), TimeUnit.SECONDS);
            executor.execute(task);
        } catch(RuntimeException exception) {
            executions.remove(job.jobId, execution);
            cancelDeadline(execution);
            task.cancel(true);
            releaseStaged(execution);
            job.fail("IMPORT_ANALYSIS_REJECTED", safeMessage(exception), clock.instant());
            mirrorSharedJob(job);
        }
    }

    /** Descripción: Ejecuta la operación «expireExecution» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: jobId: identificador que delimita el recurso objetivo; execution: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void expireExecution(String jobId, JobExecution execution, String code, String message) {
        MutableAnalysisJob job = jobs.get(jobId);
        if(job == null || !job.fail(code, message, clock.instant())) {
            return;
        }
        mirrorSharedJob(job);
        execution.task.cancel(true);
        if(!execution.started.get()) {
            releaseStaged(execution);
        }
    }

    /** Descripción: Ejecuta la operación «finishExecution» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: jobId: identificador que delimita el recurso objetivo; execution: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void finishExecution(String jobId, JobExecution execution) {
        executions.remove(jobId, execution);
        cancelDeadline(execution);
        releaseStaged(execution);
    }

    /** Descripción: Evalúa la condición «cancelDeadline» sin modificar el estado externo. Entradas: execution: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void cancelDeadline(JobExecution execution) {
        ScheduledFuture<?> deadline = execution.deadline;
        if(deadline != null) {
            deadline.cancel(false);
        }
    }

    /** Descripción: Ejecuta la operación «releaseStaged» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: execution: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void releaseStaged(JobExecution execution) {
        if(execution.staged != null && execution.stagingReleased.compareAndSet(false, true)) {
            deleteStaged(execution.staged);
        }
    }

    @PreDestroy
    /** Descripción: Cancela análisis pendientes y cierra el watchdog de compatibilidad cuando pertenece a esta instancia. Entradas: no requiere parámetros de entrada. Salida: deja todos los trabajos alcanzables en estado terminal y completa el cierre de forma acotada. */ public void cancelOutstandingAnalyses() {
        for(Map.Entry<String, JobExecution> entry : List.copyOf(executions.entrySet())) {
            expireExecution(entry.getKey(), entry.getValue(), "IMPORT_ANALYSIS_CANCELLED",
                    "Metamodel import analysis was cancelled because the service is shutting down");
            cancelDeadline(entry.getValue());
        }
        shutdownOwnedWatchdog();
    }

    /** Descripción: Cierra únicamente el watchdog creado por el constructor de compatibilidad. Entradas: no requiere parámetros. Salida: detiene el recurso propio sin interferir con el bean administrado por Spring. */
    /** Descripción: Ejecuta la operación «shutdownOwnedWatchdog» correspondiente a la responsabilidad de JobExecution. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void shutdownOwnedWatchdog() {
        if(!ownsWatchdog) {
            return;
        }
        watchdog.shutdownNow();
        try {
            watchdog.awaitTermination(properties.getAnalysisShutdownWaitSeconds(), TimeUnit.SECONDS);
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de MutableAnalysisJob. Entradas: job: dato de entrada necesario para la operación; staged: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void run(MutableAnalysisJob job, MetamodelImportStagedFile staged, MetamodelImportOptions options) {
        try{
            long size = Files.size(staged.path());
            if(size > properties.getMaxFileSizeBytes()){throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import file exceeds configured maximum before parsing: " + size + " bytes");}
            run(job, staged.fileName(), staged.mediaType(), com.example.editor.io.BoundedIo.readInternal(staged.path()), options);
        }catch(RuntimeException exception){
            job.fail(safeMessage(exception), clock.instant());
            mirrorSharedJob(job);
        }catch(IOException exception){
            job.fail("Could not read staged metamodel import file: " + safeMessage(exception), clock.instant());
            mirrorSharedJob(job);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteStaged» de forma controlada. Entradas: staged: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteStaged(MetamodelImportStagedFile staged){
        try{Files.deleteIfExists(staged.path());}catch(IOException ignored){/* Best-effort cleanup after the job releases the source. */}
    }

    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de MutableAnalysisJob. Entradas: job: dato de entrada necesario para la operación; fileName: archivo o contenido que debe procesarse; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void run(MutableAnalysisJob job, String fileName, String mediaType, byte[] content, MetamodelImportOptions options) {
        if(!job.start(clock.instant())) {
            return;
        }
        mirrorSharedJob(job);
        MetamodelImportProgressListener listener = new MetamodelImportProgressListener() {

            @Override
            /** Descripción: Ejecuta la operación «stageStarted» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: stage: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void stageStarted(MetamodelImportAnalysisStage stage, String message, String details) {
                job.stageStarted(stage, message, details, clock.instant());
                mirrorSharedJob(job);
            }

            @Override
            /** Descripción: Ejecuta la operación «log» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: stage: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void log(MetamodelImportAnalysisStage stage, MetamodelImportActivitySeverity severity, String code, String message, String details) {
                job.addStageActivity(stage, severity, code, message, details, clock.instant());
            }

            @Override
            /** Descripción: Ejecuta la operación «stageCompleted» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: stage: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void stageCompleted(MetamodelImportAnalysisStage stage, String message, String details) {
                job.stageCompleted(stage, message, details, clock.instant());
                mirrorSharedJob(job);
            }
        };

        try {
            MetamodelImportPreview preview = processor.preview(fileName, mediaType, content, options, listener);
            appendMissingWarnings(job, preview.warnings());
            job.complete(preview, clock.instant());
            mirrorSharedJob(job);
        } catch(RuntimeException exception) {
            job.fail(safeMessage(exception), clock.instant());
            mirrorSharedJob(job);
        }
    }

    /** Descripción: Ejecuta la operación «appendMissingWarnings» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: job: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendMissingWarnings(MutableAnalysisJob job, List<TransformationWarning> warnings) {
        for(TransformationWarning warning : warnings) {
            if(job.containsActivity(warning.code(), warning.message())) {
                continue;
            }

            MetamodelImportActivitySeverity severity = warningSeverity(warning);
            String details = "path=" + safeText(warning.path()) + System.lineSeparator() + "message=" + warning.message();
            job.addStageActivity(MetamodelImportAnalysisStage.SUMMARY, severity, warning.code(), warning.message(), details, clock.instant());
        }
    }

    /** Descripción: Ejecuta la operación «warningSeverity» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: warning: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportActivitySeverity warningSeverity(TransformationWarning warning) {
        if(warning.code().startsWith("IMPORT_LOSSY") || warning.code().startsWith("IMPORT_UNRESOLVED")) {
            return MetamodelImportActivitySeverity.INFORMATION_LOSS;
        }

        return MetamodelImportActivitySeverity.WARNING;
    }

    /** Descripción: Valida las condiciones necesarias para «requireJob» y rechaza entradas no válidas. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MutableAnalysisJob requireJob(String jobId) {
        cleanupExpiredJobs();
        MutableAnalysisJob job = jobs.get(jobId);

        if(job == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import analysis job was not found: " + jobId);
        }

        return job;
    }

    /** Descripción: Ejecuta la operación «expireOverdueExecutions» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void expireOverdueExecutions() {
        Instant now = clock.instant();
        long maximumSeconds = properties.getAnalysisMaximumDurationSeconds();
        for(Map.Entry<String, JobExecution> entry : executions.entrySet()) {
            MutableAnalysisJob job = jobs.get(entry.getKey());
            if(job != null && job.deadlineExceeded(now, maximumSeconds)) {
                expireExecution(entry.getKey(), entry.getValue(), "IMPORT_ANALYSIS_TIMED_OUT",
                        "Metamodel import analysis exceeded the configured total duration");
            }
        }
    }

    /** Descripción: Ejecuta la operación «cleanupExpiredJobs» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void cleanupExpiredJobs() {
        expireOverdueExecutions();
        Duration retention = Duration.ofMinutes(Math.max(1, properties.getAnalysisRetentionMinutes()));
        Instant threshold = clock.instant().minus(retention);
        jobs.entrySet().removeIf(entry -> entry.getValue().terminalAndOlderThan(threshold));
        int maximum = properties.getAnalysisMaxRetainedJobs();
        if(jobs.size() <= maximum){return;}
        List<Map.Entry<String, MutableAnalysisJob>> removable = jobs.entrySet().stream().filter(entry -> entry.getValue().isTerminal())
                .sorted(Comparator.comparing(entry -> entry.getValue().updatedAt)).toList();
        for(Map.Entry<String, MutableAnalysisJob> entry : removable){if(jobs.size() <= maximum){break;} jobs.remove(entry.getKey(), entry.getValue());}
    }

    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public CapacityStatus capacityStatus() {
        cleanupExpiredJobs();
        long terminal=jobs.values().stream().filter(MutableAnalysisJob::isTerminal).count();
        return new CapacityStatus(jobs.size(),properties.getAnalysisMaxRetainedJobs(),jobs.size()-terminal,terminal,properties.getAnalysisRetentionMinutes());
    }

    public record CapacityStatus(int retainedJobs,int maximumRetainedJobs,long activeJobs,long terminalJobs,int retentionMinutes) {
        /** Descripción: Ejecuta la operación «saturated» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean saturated(){return retainedJobs>=maximumRetainedJobs;}
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content) {
        try{MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(content == null ? new byte[0] : content); return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable", exception);}
    }

    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: throwable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable throwable) {
        if(throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }

    /** Descripción: Ejecuta la operación «safeFileName» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeFileName(String value) {
        if(value == null || value.isBlank()) {
            return "metamodel-import";
        }

        String normalized = value.replaceAll("[^A-Za-z0-9._-]", "-");
        return normalized.isBlank() ? "metamodel-import" : normalized;
    }

    /** Descripción: Ejecuta la operación «safeText» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeText(String value) {
        return value == null ? "" : value;
    }

    /** Cancela de forma idempotente un análisis y libera su staging cuando todavía está activo. */
    /** Descripción: Evalúa la condición «cancel» sin modificar el estado externo. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void cancel(String jobId) {
        MutableAnalysisJob job = requireJob(jobId);
        JobExecution execution = executions.get(jobId);
        if (job.fail("IMPORT_ANALYSIS_CANCELLED", "Metamodel import analysis was cancelled by the user", clock.instant())) {
            if (execution != null) {
                execution.task.cancel(true);
                cancelDeadline(execution);
                releaseStaged(execution);
            }
            mirrorSharedJob(job);
        }
    }

    /** Descripción: Ejecuta la operación «registerSharedJob» correspondiente a la responsabilidad de CapacityStatus. Entradas: job: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void registerSharedJob(MutableAnalysisJob job) {
        try {
            jobOrchestrator.register(job.jobId, "METAMODEL_IMPORT", job.baseFileName(), AuditRequestContext.correlationId(),
                    () -> cancel(job.jobId));
            mirrorSharedJob(job);
        } catch (IllegalStateException ignored) {
            // The shared observability registry must never reject the domain operation when saturated.
            com.example.editor.observability.HydraBestEffortDiagnostics.record(
                    "com/example/editor/metamodel/importing/MetamodelImportAnalysisJobService.java", ignored);
        }
    }

    /** Descripción: Ejecuta la operación «mirrorSharedJob» correspondiente a la responsabilidad de CapacityStatus. Entradas: job: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void mirrorSharedJob(MutableAnalysisJob job) {
        MetamodelImportAnalysisJobResponse snapshot = job.snapshot(clock.instant());
        HydraJobStatus status = switch (snapshot.status()) {
            case QUEUED -> HydraJobStatus.QUEUED;
            case RUNNING -> HydraJobStatus.RUNNING;
            case COMPLETED -> HydraJobStatus.COMPLETED;
            case FAILED -> HydraJobStatus.FAILED;
        };
        try { jobOrchestrator.update(snapshot.jobId(), status, snapshot.progress(), snapshot.phase(), snapshot.errorMessage()); }
        catch (IllegalArgumentException ignored) { /* Registry retention never changes import job semantics. */ }
    }

    /** Descripción: Ejecuta la operación «localJobOrchestrator» correspondiente a la responsabilidad de CapacityStatus. Entradas: clock: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static HydraJobOrchestrator localJobOrchestrator(Clock clock) {
        return new HydraJobOrchestrator(new HydraJobProperties(), clock);
    }

    /** Descripción: Crea o prepara el resultado de «createFallbackWatchdog» dentro de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static ScheduledExecutorService createFallbackWatchdog() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "metamodel-import-fallback-watchdog-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    private static final class JobExecution {
        private final MetamodelImportStagedFile staged;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean stagingReleased = new AtomicBoolean();
        private volatile FutureTask<Void> task;
        private volatile ScheduledFuture<?> deadline;

        /** Descripción: Inicializa una instancia de JobExecution con sus dependencias y datos de entrada. Entradas: staged: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private JobExecution(MetamodelImportStagedFile staged) {
            this.staged = staged;
        }
    }

    /**
     * Mantiene el estado mutable protegido de un trabajo.
     */
    private static final class MutableAnalysisJob {

        private final String jobId;
        private final String baseFileName;
        private final Instant createdAt;
        private final MetamodelImportOptions options;
        private final String sourceSha256;
        private final Map<MetamodelImportAnalysisStage, MutableStage> stages = new EnumMap<>(MetamodelImportAnalysisStage.class);
        private final ArrayDeque<MetamodelImportActivityMessage> activities = new ArrayDeque<>();
        private final Map<String, Integer> activityFingerprints = new HashMap<>();
        private final int maximumActivityMessages;
        private final int maximumActivityDetailCharacters;
        private MetamodelImportAnalysisStatus status = MetamodelImportAnalysisStatus.QUEUED;
        private String phase = "QUEUED";
        private Instant updatedAt;
        private MetamodelImportPreview preview;
        private String errorMessage = "";

        /** Descripción: Inicializa una instancia de MutableAnalysisJob con sus dependencias y datos de entrada. Entradas: jobId: identificador que delimita el recurso objetivo; originalFileName: archivo o contenido que debe procesarse; createdAt: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; sourceSha256: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ private MutableAnalysisJob(String jobId, String originalFileName, Instant createdAt, MetamodelImportOptions options, String sourceSha256, MetamodelImportProperties properties) {
            this.jobId = jobId;
            this.baseFileName = removeExtension(originalFileName);
            this.createdAt = createdAt;
            this.options = options == null ? MetamodelImportOptions.defaults() : options;
            this.sourceSha256 = sourceSha256 == null ? "" : sourceSha256;
            this.updatedAt = createdAt;
            this.maximumActivityMessages = properties.getAnalysisMaximumActivityMessages();
            this.maximumActivityDetailCharacters = properties.getAnalysisMaximumActivityDetailCharacters();

            for(MetamodelImportAnalysisStage stage : MetamodelImportAnalysisStage.values()) {
                stages.put(stage, new MutableStage(stage, properties.getAnalysisMaximumStageLogCharacters()));
            }
        }

        private synchronized void completeInitialStage(MetamodelImportAnalysisStage stage, String message, String details, Instant now) {
            MutableStage mutableStage = stages.get(stage);
            mutableStage.start(now);
            mutableStage.complete(now);
            addActivity(mutableStage, MetamodelImportActivitySeverity.INFO, "IMPORT_FILE_RECEIVED", message, details, now);
            phase = stage.id();
            updatedAt = now;
        }

        /** Descripción: Crea o prepara el resultado de «start» dentro de MetamodelImportAnalysisJobService. Entradas: now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private synchronized boolean start(Instant now) {
            if(isTerminal()) return false;
            status = MetamodelImportAnalysisStatus.RUNNING;
            updatedAt = now;
            return true;
        }

        /** Descripción: Ejecuta la operación «stageStarted» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: stage: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private synchronized void stageStarted(MetamodelImportAnalysisStage stage, String message, String details, Instant now) {
            if(isTerminal()) {
                return;
            }

            status = MetamodelImportAnalysisStatus.RUNNING;
            MutableStage mutableStage = stages.get(stage);
            mutableStage.start(now);
            phase = stage.id();
            updatedAt = now;
            addActivity(mutableStage, MetamodelImportActivitySeverity.INFO, "IMPORT_" + stage.id() + "_STARTED", message, details, now);
        }

        /** Descripción: Ejecuta la operación «stageCompleted» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: stage: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private synchronized void stageCompleted(MetamodelImportAnalysisStage stage, String message, String details, Instant now) {
            if(isTerminal()) {
                return;
            }

            MutableStage mutableStage = stages.get(stage);

            if(mutableStage.status == MetamodelImportAnalysisStageStatus.PENDING) {
                mutableStage.start(now);
            }

            mutableStage.complete(now);
            addActivity(mutableStage, MetamodelImportActivitySeverity.INFO, "IMPORT_" + stage.id() + "_COMPLETED", message, details, now);
            phase = nextRunningStageId(stage);
            updatedAt = now;
        }

        /** Descripción: Ejecuta la operación «addStageActivity» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: stage: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private synchronized void addStageActivity(MetamodelImportAnalysisStage stage, MetamodelImportActivitySeverity severity, String code, String message, String details, Instant now) {
            if(isTerminal()) {
                return;
            }
            MutableStage mutableStage = stages.get(stage);

            if(mutableStage.status == MetamodelImportAnalysisStageStatus.PENDING) {
                mutableStage.start(now);
            }

            addActivity(mutableStage, severity, code, message, details, now);
            phase = stage.id();
            updatedAt = now;
        }

        /** Descripción: Ejecuta la operación «complete» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: preview: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private synchronized void complete(MetamodelImportPreview preview, Instant now) {
            if(isTerminal()) return;
            this.preview = preview;
            MutableStage summary = stages.get(MetamodelImportAnalysisStage.SUMMARY);

            if(summary.status == MetamodelImportAnalysisStageStatus.PENDING) {
                summary.start(now);
            }

            summary.complete(now);
            addActivity(summary, MetamodelImportActivitySeverity.INFO, "IMPORT_ANALYSIS_COMPLETED", "The metamodel is ready to open or save", "status=COMPLETED", now);

            for(MutableStage stage : stages.values()) {
                if(stage.status == MetamodelImportAnalysisStageStatus.PENDING) {
                    stage.skip(now);
                } else if(stage.status == MetamodelImportAnalysisStageStatus.RUNNING) {
                    stage.complete(now);
                }
            }

            status = MetamodelImportAnalysisStatus.COMPLETED;
            phase = "COMPLETED";
            updatedAt = now;
        }

        /** Descripción: Ejecuta la operación «fail» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: message: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private synchronized boolean fail(String message, Instant now) {
            return fail("IMPORT_ANALYSIS_FAILED", message, now);
        }

        private synchronized boolean fail(String code, String message, Instant now) {
            if(isTerminal()) return false;
            MutableStage stage = currentOrSummaryMutableStage();
            if(stage.status == MetamodelImportAnalysisStageStatus.PENDING) {
                stage.start(now);
            }
            addActivity(stage, MetamodelImportActivitySeverity.ERROR, code, message, "exception=" + message, now);
            for(MutableStage candidate : stages.values()) {
                if(candidate.status == MetamodelImportAnalysisStageStatus.RUNNING) candidate.fail(now);
                else if(candidate.status == MetamodelImportAnalysisStageStatus.PENDING) candidate.skip(now);
            }
            status = MetamodelImportAnalysisStatus.FAILED;
            phase = stage.stage.id();
            errorMessage = message;
            updatedAt = now;
            return true;
        }

        /** Descripción: Evalúa la condición «containsActivity» sin modificar el estado externo. Entradas: code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private synchronized boolean containsActivity(String code, String message) {
            return activityFingerprints.containsKey(code + "|" + message);
        }

        /** Descripción: Ejecuta la operación «prepared» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private synchronized MetamodelPreparedImport prepared() {
            if(status != MetamodelImportAnalysisStatus.COMPLETED || preview == null){
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel import analysis is not completed: " + jobId);
            }
            return new MetamodelPreparedImport(jobId, preview, options, sourceSha256);
        }

        /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private synchronized MetamodelImportAnalysisJobResponse snapshot(Instant now) {
            List<MetamodelImportAnalysisStageResponse> stageResponses = stages.values().stream().sorted(Comparator.comparingInt(item -> item.stage.sequence())).map(item -> item.snapshot(now)).toList();
            int completedStages = (int) stages.values().stream().filter(item -> item.status == MetamodelImportAnalysisStageStatus.COMPLETED || item.status == MetamodelImportAnalysisStageStatus.SKIPPED).count();
            return new MetamodelImportAnalysisJobResponse(jobId, status, progress(), phase, createdAt, updatedAt, List.copyOf(activities), preview, errorMessage, completedStages, stages.size(), stageResponses, !activities.isEmpty());
        }

        /** Descripción: Ejecuta la operación «stageLog» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: stage: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private synchronized String stageLog(MetamodelImportAnalysisStage stage, Instant now) {
            MutableStage mutableStage = stages.get(stage);
            return mutableStage.renderLog(jobId, baseFileName, now);
        }

        /** Descripción: Ejecuta la operación «combinedLog» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private synchronized String combinedLog(Instant now) {
            StringBuilder result = new StringBuilder();
            result.append("Metamodel import analysis").append(System.lineSeparator());
            result.append("jobId=").append(jobId).append(System.lineSeparator());
            result.append("file=").append(baseFileName).append(System.lineSeparator());
            result.append("status=").append(status).append(System.lineSeparator());
            result.append("createdAt=").append(createdAt).append(System.lineSeparator());
            result.append("updatedAt=").append(updatedAt).append(System.lineSeparator());

            for(MutableStage stage : stages.values().stream().sorted(Comparator.comparingInt(item -> item.stage.sequence())).toList()) {
                result.append(System.lineSeparator()).append(stage.renderLog(jobId, baseFileName, now));
            }

            return result.toString();
        }

        private synchronized boolean deadlineExceeded(Instant now, long maximumSeconds) {
            return !isTerminal() && !createdAt.plusSeconds(Math.max(1L, maximumSeconds)).isAfter(now);
        }

        /** Descripción: Ejecuta la operación «terminalAndOlderThan» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: threshold: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private synchronized boolean terminalAndOlderThan(Instant threshold) {
            return isTerminal() && updatedAt.isBefore(threshold);
        }

        /** Descripción: Ejecuta la operación «baseFileName» correspondiente a la responsabilidad de MetamodelImportAnalysisJobService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private synchronized String baseFileName() {
            return baseFileName;
        }

        /** Descripción: Ejecuta la operación «addActivity» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: stage: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addActivity(MutableStage stage, MetamodelImportActivitySeverity severity, String code, String message, String details, Instant now) {
            while(activities.size() >= maximumActivityMessages) evictOldestActivity();
            String boundedDetails = bounded(details, maximumActivityDetailCharacters);
            MetamodelImportActivityMessage activity = new MetamodelImportActivityMessage(now, progress(), stage.stage.id(), severity, code, message, boundedDetails);
            stage.activities.add(activity);
            stage.appendLog(activity);
            activities.addLast(activity);
            activityFingerprints.merge(code + "|" + message, 1, Integer::sum);
        }

        /** Descripción: Ejecuta la operación «evictOldestActivity» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void evictOldestActivity() {
            MetamodelImportActivityMessage oldest = activities.pollFirst();
            if(oldest == null) return;
            try { stages.get(MetamodelImportAnalysisStage.fromId(oldest.phase())).activities.remove(oldest); }
            catch(RuntimeException exception) { com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/MetamodelImportAnalysisJobService.java", exception); }
            String fingerprint = oldest.code() + "|" + oldest.message();
            activityFingerprints.computeIfPresent(fingerprint, (key, count) -> count <= 1 ? null : count - 1);
        }

        /** Descripción: Ejecuta la operación «bounded» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: value: valor de entrada que debe procesarse; maximum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String bounded(String value, int maximum) {
            String text = value == null ? "" : value;
            if(text.length() <= maximum) return text;
            return text.substring(0, Math.max(0, maximum - 15)) + "… [truncated]";
        }

        /** Descripción: Ejecuta la operación «progress» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int progress() {
            if(status == MetamodelImportAnalysisStatus.COMPLETED) {
                return 100;
            }

            long completed = stages.values().stream().filter(item -> item.status == MetamodelImportAnalysisStageStatus.COMPLETED || item.status == MetamodelImportAnalysisStageStatus.SKIPPED).count();
            return (int) Math.floor(completed * 100.0 / stages.size());
        }

        /** Descripción: Ejecuta la operación «nextRunningStageId» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: completedStage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String nextRunningStageId(MetamodelImportAnalysisStage completedStage) {
            return stages.values().stream().filter(item -> item.status == MetamodelImportAnalysisStageStatus.RUNNING).map(item -> item.stage.id()).findFirst().orElse(completedStage.id());
        }

        /** Descripción: Ejecuta la operación «currentOrSummaryMutableStage» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MutableStage currentOrSummaryMutableStage() {
            return stages.values().stream().filter(item -> item.status == MetamodelImportAnalysisStageStatus.RUNNING).findFirst().orElse(stages.get(MetamodelImportAnalysisStage.SUMMARY));
        }

        /** Descripción: Ejecuta la operación «currentOrSummaryStage» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportAnalysisStage currentOrSummaryStage() {
            return currentOrSummaryMutableStage().stage;
        }

        /** Descripción: Evalúa la condición «isTerminal» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isTerminal() {
            return status == MetamodelImportAnalysisStatus.COMPLETED || status == MetamodelImportAnalysisStatus.FAILED;
        }

        /** Descripción: Ejecuta la operación «clamp» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static int clamp(int value) {
            return Math.max(0, Math.min(100, value));
        }

        /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) {
            return value == null ? "" : value;
        }

        /** Descripción: Elimina o libera el recurso gestionado por «removeExtension» de forma controlada. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String removeExtension(String fileName) {
            int index = fileName.lastIndexOf('.');

            if(index > 0) {
                return fileName.substring(0, index);
            }

            return fileName;
        }
    }

    /**
     * Mantiene el estado y el texto de una etapa.
     */
    private static final class MutableStage {

        private final MetamodelImportAnalysisStage stage;
        private final List<MetamodelImportActivityMessage> activities = new ArrayList<>();
        private final StringBuilder log = new StringBuilder();
        private final int maximumLogCharacters;
        private boolean logTruncated;
        private MetamodelImportAnalysisStageStatus status = MetamodelImportAnalysisStageStatus.PENDING;
        private Instant startedAt;
        private Instant completedAt;

        /** Descripción: Inicializa una instancia de MutableStage con sus dependencias y datos de entrada. Entradas: stage: dato de entrada necesario para la operación; maximumLogCharacters: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private MutableStage(MetamodelImportAnalysisStage stage, int maximumLogCharacters) {
            this.stage = stage;
            this.maximumLogCharacters = maximumLogCharacters;
        }

        /** Descripción: Crea o prepara el resultado de «start» dentro de MutableAnalysisJob. Entradas: now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void start(Instant now) {
            if(status == MetamodelImportAnalysisStageStatus.PENDING) {
                status = MetamodelImportAnalysisStageStatus.RUNNING;
                startedAt = now;
            }
        }

        /** Descripción: Ejecuta la operación «complete» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void complete(Instant now) {
            status = MetamodelImportAnalysisStageStatus.COMPLETED;
            completedAt = now;

            if(startedAt == null) {
                startedAt = now;
            }
        }

        /** Descripción: Ejecuta la operación «fail» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void fail(Instant now) {
            status = MetamodelImportAnalysisStageStatus.FAILED;
            completedAt = now;

            if(startedAt == null) {
                startedAt = now;
            }
        }

        /** Descripción: Ejecuta la operación «skip» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: now: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void skip(Instant now) {
            status = MetamodelImportAnalysisStageStatus.SKIPPED;
            startedAt = now;
            completedAt = now;
        }

        /** Descripción: Ejecuta la operación «appendLog» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: activity: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendLog(MetamodelImportActivityMessage activity) {
            if(logTruncated) return;
            StringBuilder entry = new StringBuilder();
            entry.append('[').append(activity.timestamp()).append("] ").append(activity.severity()).append(' ').append(activity.code()).append(System.lineSeparator());
            entry.append(activity.message()).append(System.lineSeparator());
            if(!activity.details().isBlank()) entry.append(activity.details()).append(System.lineSeparator());
            entry.append(System.lineSeparator());
            if(log.length() + entry.length() <= maximumLogCharacters) { log.append(entry); return; }
            String marker = "[HYDRA] Stage log truncated at configured memory limit." + System.lineSeparator();
            int remaining = Math.max(0, maximumLogCharacters - log.length());
            if(remaining > 0) log.append(marker, 0, Math.min(marker.length(), remaining));
            logTruncated = true;
        }

        /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportAnalysisStageResponse snapshot(Instant now) {
            long duration = duration(now);
            boolean available = status != MetamodelImportAnalysisStageStatus.PENDING || !activities.isEmpty();
            return new MetamodelImportAnalysisStageResponse(stage.id(), stage.sequence(), stage.label(), "SEQUENTIAL", status, startedAt, completedAt, duration, List.copyOf(activities), available);
        }

        /** Descripción: Ejecuta la operación «renderLog» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: jobId: identificador que delimita el recurso objetivo; fileName: archivo o contenido que debe procesarse; now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String renderLog(String jobId, String fileName, Instant now) {
            StringBuilder result = new StringBuilder();
            result.append("Stage ").append(stage.sequence()).append(" - ").append(stage.label()).append(System.lineSeparator());
            result.append("stageId=").append(stage.id()).append(System.lineSeparator());
            result.append("status=").append(status).append(System.lineSeparator());
            result.append("jobId=").append(jobId).append(System.lineSeparator());
            result.append("file=").append(fileName).append(System.lineSeparator());
            result.append("startedAt=").append(startedAt == null ? "" : startedAt).append(System.lineSeparator());
            result.append("completedAt=").append(completedAt == null ? "" : completedAt).append(System.lineSeparator());
            result.append("durationMillis=").append(duration(now)).append(System.lineSeparator()).append(System.lineSeparator());
            result.append(log);
            return result.toString();
        }

        /** Descripción: Ejecuta la operación «duration» correspondiente a la responsabilidad de MutableAnalysisJob. Entradas: now: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long duration(Instant now) {
            if(startedAt == null) {
                return 0;
            }

            Instant end = completedAt == null ? now : completedAt;
            return Math.max(0, Duration.between(startedAt, end).toMillis());
        }
    }
}

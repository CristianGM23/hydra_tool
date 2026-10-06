package com.example.editor.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.editor.scope.ResourceScope;
import com.example.editor.scope.WorkspaceContext;

/** API de consulta/exportación del historial funcional del usuario actual. */
@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {
    private final AuditLogStore store;
    private final AuditPresentationService presentation;
    private final AuditActivityDescriptorRegistry descriptors;
    private final AuditExportService exporter;
    private final AuditEventPublisher publisher;
    private final AuditActorContext actorContext;
    private final WorkspaceContext workspaceContext;
    private final AuditLogProperties properties;
    private final AuditClientActivityService clientActivities;

    @Autowired
    /** Descripción: Inicializa una instancia de AuditLogController con sus dependencias y datos de entrada. Entradas: store: dato de entrada necesario para la operación; presentation: dato de entrada necesario para la operación; descriptors: dato de entrada necesario para la operación; exporter: dato de entrada necesario para la operación; publisher: dato de entrada necesario para la operación; actorContext: dato de entrada necesario para la operación; workspaceContext: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; clientActivities: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditLogController(AuditLogStore store, AuditPresentationService presentation, AuditActivityDescriptorRegistry descriptors,
            AuditExportService exporter, AuditEventPublisher publisher, AuditActorContext actorContext, WorkspaceContext workspaceContext,
            AuditLogProperties properties, AuditClientActivityService clientActivities) {
        this.store = store;
        this.presentation = presentation;
        this.descriptors = descriptors;
        this.exporter = exporter;
        this.publisher = publisher;
        this.actorContext = actorContext;
        this.workspaceContext = workspaceContext;
        this.properties = properties;
        this.clientActivities = clientActivities;
    }

    @GetMapping
    /** Descripción: Ejecuta la operación «query» correspondiente a la responsabilidad de AuditLogController. Entradas: language: dato de entrada necesario para la operación; module: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación; activityType: dato de entrada necesario para la operación; resourceType: dato de entrada necesario para la operación; resourceId: identificador que delimita el recurso objetivo; text: valor de entrada que debe procesarse; correlationId: identificador que delimita el recurso objetivo; from: dato de entrada necesario para la operación; to: dato de entrada necesario para la operación; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; sort: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditLogViewPage query(@RequestParam(required = false) String language, @RequestParam(required = false) List<String> module,
            @RequestParam(required = false) List<String> action, @RequestParam(required = false) List<String> activityType,
            @RequestParam(required = false) List<String> resourceType, @RequestParam(required = false) List<String> resourceId,
            @RequestParam(required = false) String text, @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "DESC") AuditSortDirection sort) {
        publisher.flushPending();
        AuditLanguage lang = AuditLanguage.from(language);
        AuditPage result = store.query(queryForCurrentActor(module, action, activityType, resourceType, resourceId, text, correlationId, from, to, page, size, sort));
        return new AuditLogViewPage(result.entries().stream().map(entry -> presentation.present(entry, lang)).toList(),
                result.page(), result.pageSize(), result.hasMore());
    }

    @GetMapping("/statistics")
    /** Descripción: Ejecuta la operación «statistics» correspondiente a la responsabilidad de AuditLogController. Entradas: module: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación; from: dato de entrada necesario para la operación; to: dato de entrada necesario para la operación; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public AuditStatistics statistics(@RequestParam(required = false) List<String> module, @RequestParam(required = false) List<String> action,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String text) {
        publisher.flushPending();
        return store.statistics(queryForCurrentActor(module, action, List.of(), List.of(), List.of(), text, "", from, to, 0, properties.getDefaultPageSize(), AuditSortDirection.DESC));
    }

    @GetMapping("/descriptors")
    /** Descripción: Ejecuta la operación «descriptors» correspondiente a la responsabilidad de AuditLogController. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<AuditDescriptorView> descriptors(@RequestParam(required = false) String language) {
        AuditLanguage lang = AuditLanguage.from(language);
        return descriptors.all().stream().map(item -> new AuditDescriptorView(item.activityType(), item.moduleId(), item.action(),
                (lang == AuditLanguage.ES ? item.titleSpanish() : item.titleEnglish()).replace(" {resource}", ""),
                presentation.moduleName(item, lang), item.importance())).toList();
    }

    @PostMapping("/activities")
    /** Descripción: Ejecuta la operación «captureClientActivity» correspondiente a la responsabilidad de AuditLogController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> captureClientActivity(@RequestBody AuditClientActivityRequest request) {
        if(clientActivities == null || !properties.isCaptureNavigationActions()) return ResponseEntity.noContent().build();
        clientActivities.publish(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/status")
    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de AuditLogController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String, Object> status() {
        if(publisher instanceof DefaultAuditEventPublisher asynchronous) {
            AuditPipelineStatus status = asynchronous.status();
            return Map.of("enabled", status.enabled(), "running", status.running(), "queueSize", status.queued(),
                    "queueCapacity", status.capacity(), "persisted", status.persisted(), "dropped", status.dropped(), "failures", status.failures());
        }
        return Map.of("enabled", properties.isEnabled(), "running", true, "provider", properties.getStorageProvider());
    }


    @GetMapping("/maintenance/storage")
    /** Descripción: Ejecuta la operación «storageStatus» correspondiente a la responsabilidad de AuditLogController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditStorageStatus storageStatus() {
        if(store instanceof AuditLogMaintenance supported) return supported.storageStatus();
        return new AuditStorageStatus(properties.getStorageProvider(), false, 0L, 0L, 0, false,
                properties.getNavigationRetentionDays(), properties.getOpenRetentionDays(), properties.getRoutineRetentionDays(), properties.getRetentionDays());
    }

    @PostMapping("/maintenance/run")
    /** Descripción: Procesa la operación «runMaintenance» coordinando las responsabilidades de AuditLogController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditMaintenanceResult runMaintenance() {
        publisher.flushPending();
        return maintenance().maintain();
    }

    @PostMapping("/maintenance/delete")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditMaintenanceResult delete(@RequestBody AuditDeletionRequest request) {
        publisher.flushPending();
        return maintenance().delete(deletionCriteria(request));
    }

    @GetMapping("/export")
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de AuditLogController. Entradas: format: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; module: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación; activityType: dato de entrada necesario para la operación; resourceType: dato de entrada necesario para la operación; resourceId: identificador que delimita el recurso objetivo; text: valor de entrada que debe procesarse; correlationId: identificador que delimita el recurso objetivo; from: dato de entrada necesario para la operación; to: dato de entrada necesario para la operación; sort: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<StreamingResponseBody> export(@RequestParam(defaultValue = "JSON") String format,
            @RequestParam(defaultValue = "en") String language, @RequestParam(required = false) List<String> module,
            @RequestParam(required = false) List<String> action, @RequestParam(required = false) List<String> activityType,
            @RequestParam(required = false) List<String> resourceType, @RequestParam(required = false) List<String> resourceId,
            @RequestParam(required = false) String text, @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "DESC") AuditSortDirection sort) {
        publisher.flushPending();
        AuditExportFormat exportFormat = AuditExportFormat.from(format);
        AuditLanguage lang = AuditLanguage.from(language);
        AuditQuery query = queryForCurrentActor(module, action, activityType, resourceType, resourceId, text, correlationId, from, to, 0,
                properties.getMaximumPageSize(), sort).withoutPaging();
        String filename = "hydra-activity-log-" + Instant.now().toString().replace(':', '-') + "." + exportFormat.extension();
        StreamingResponseBody body = output -> {
            long count = exporter.export(query, exportFormat, lang, output);
            publisher.publish(AuditEvent.builder("audit.exported", "audit", AuditActions.EXPORT,
                    new AuditResource("audit", "activity-log", "activity-log", "Activity log"))
                    .metadata("format", exportFormat.name()).metadata("language", lang.code()).metadata("entries", count).build());
        };
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(exportFormat.mediaType() + ";charset=UTF-8")).body(body);
    }


    /** Descripción: Ejecuta la operación «deletionCriteria» correspondiente a la responsabilidad de AuditLogController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditDeletionCriteria deletionCriteria(AuditDeletionRequest request) {
        if(request == null) throw new IllegalArgumentException("deletion request is required");
        ResourceScope scope = workspaceContext.currentScope();
        AuditActor actor = actorContext.currentActor();
        String workspaceId = scope == null ? ResourceScope.LOCAL_WORKSPACE_ID : scope.workspaceId();
        String actorId = actor == null || actor.actorId() == null || actor.actorId().isBlank() ? "local-user" : actor.actorId();
        return new AuditDeletionCriteria(workspaceId, Set.of(actorId), AuditDeletionMode.from(request.mode()), set(request.module()),
                set(request.action()), request.text(), request.from(), request.to());
    }

    /** Descripción: Ejecuta la operación «maintenance» correspondiente a la responsabilidad de AuditLogController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private AuditLogMaintenance maintenance() {
        if(store instanceof AuditLogMaintenance supported) return supported;
        throw new IllegalStateException("Audit storage provider does not support maintenance: " + properties.getStorageProvider());
    }

    /** Descripción: Ejecuta la operación «queryForCurrentActor» correspondiente a la responsabilidad de AuditLogController. Entradas: modules: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; activityTypes: dato de entrada necesario para la operación; resourceTypes: dato de entrada necesario para la operación; resourceIds: dato de entrada necesario para la operación; text: valor de entrada que debe procesarse; correlationId: identificador que delimita el recurso objetivo; from: dato de entrada necesario para la operación; to: dato de entrada necesario para la operación; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; sort: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditQuery queryForCurrentActor(List<String> modules, List<String> actions, List<String> activityTypes, List<String> resourceTypes,
            List<String> resourceIds, String text, String correlationId, Instant from, Instant to, int page, Integer size, AuditSortDirection sort) {
        Instant effectiveTo = to == null ? Instant.now() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(properties.getDefaultQueryDays(), ChronoUnit.DAYS) : from;
        if(effectiveFrom.isAfter(effectiveTo)) throw new IllegalArgumentException("from must be before to");
        int pageSize = Math.min(size == null ? properties.getDefaultPageSize() : Math.max(1, size), properties.getMaximumPageSize());
        ResourceScope scope = workspaceContext.currentScope();
        AuditActor actor = actorContext.currentActor();
        String workspaceId = scope == null ? ResourceScope.LOCAL_WORKSPACE_ID : scope.workspaceId();
        String actorId = actor == null || actor.actorId() == null || actor.actorId().isBlank() ? "local-user" : actor.actorId();
        return new AuditQuery(workspaceId, Set.of(actorId), set(modules), set(actions), set(activityTypes), set(resourceTypes), set(resourceIds),
                effectiveFrom, effectiveTo, text, correlationId, Math.max(0, page), pageSize, sort);
    }

    /** Descripción: Actualiza el estado gestionado por AuditLogController mediante la operación «set». Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> set(List<String> values) { return values == null ? Set.of() : values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}

package com.example.editor.audit;

import java.io.IOException;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Red de seguridad transversal: toda operación HTTP funcional queda auditada
 * aunque un módulo futuro olvide instrumentarla explícitamente. Las mutaciones
 * canónicas detalladas suprimen el SAVE/UPDATE genérico para evitar duplicados.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AuditHttpActivityFilter extends OncePerRequestFilter {
    private final AuditEventPublisher publisher;
    private final AuditHttpActivityClassifierRegistry classifiers;
    private final AuditLogProperties properties;

    /** Descripción: Inicializa una instancia de AuditHttpActivityFilter con sus dependencias y datos de entrada. Entradas: publisher: dato de entrada necesario para la operación; classifiers: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public AuditHttpActivityFilter(AuditEventPublisher publisher, AuditHttpActivityClassifierRegistry classifiers, AuditLogProperties properties) {
        this.publisher = publisher;
        this.classifiers = classifiers;
        this.properties = properties;
    }

    @Override
    /** Descripción: Evalúa la condición «shouldNotFilter» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !properties.isEnabled() || path == null || !path.startsWith("/api/") || path.startsWith("/api/audit-log")
                || "OPTIONS".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod());
    }

    @Override
    /** Descripción: Ejecuta la operación «doFilterInternal» correspondiente a la responsabilidad de AuditHttpActivityFilter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación; filterChain: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase(Locale.ROOT);
        var classified = classifiers.classify(method, request.getRequestURI());
        if(classified.isEmpty() || !shouldCapture(method, request.getRequestURI(), classified.get())) {
            filterChain.doFilter(request, response);
            return;
        }

        long started = System.nanoTime();
        if(AuditRequestContext.active()) {
            Throwable failure = null;
            try { filterChain.doFilter(request, response); }
            catch(IOException | ServletException | RuntimeException exception) { failure = exception; throw exception; }
            /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de AuditHttpActivityFilter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación; get: dato de entrada necesario para la operación; started: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ finally { publish(request, response, classified.get(), started, failure); }
            return;
        }
        AuditRequestContext.Scope scope = AuditRequestContext.begin();
        try {
            Throwable failure = null;
            try { filterChain.doFilter(request, response); }
            catch(IOException | ServletException | RuntimeException exception) { failure = exception; throw exception; }
            /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de AuditHttpActivityFilter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación; get: dato de entrada necesario para la operación; started: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ finally { publish(request, response, classified.get(), started, failure); }
        } finally {
            scope.close();
        }
    }

    /** Descripción: Evalúa la condición «shouldCapture» sin modificar el estado externo. Entradas: method: dato de entrada necesario para la operación; requestPath: ruta o ubicación utilizada por la operación; classification: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean shouldCapture(String method, String requestPath, AuditHttpActivityClassification classification) {
        if(!"GET".equals(method)) return properties.isCaptureHttpMutationActions();
        if(!AuditActions.OPEN.equals(classification.action())) return true;
        if(properties.isCaptureReadActions()) return true;
        String path = requestPath == null ? "" : requestPath.toLowerCase(Locale.ROOT);
        boolean explicitOpen = path.contains("/open") || path.contains("/preview") || path.contains("/launch");
        return properties.isCaptureOpenActions() && explicitOpen;
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de AuditHttpActivityFilter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación; classification: dato de entrada necesario para la operación; started: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publish(HttpServletRequest request, HttpServletResponse response, AuditHttpActivityClassification classification, long started, Throwable failure) {
        if(AuditRequestContext.businessMutationObserved() && !classification.captureAlongsideMutation() && failure == null && response.getStatus() < 400) return;
        long elapsedMillis = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        AuditResult result = failure != null || response.getStatus() >= 400 ? AuditResult.FAILED : AuditResult.SUCCESS;
        AuditImportance importance = result == AuditResult.FAILED ? AuditImportance.WARNING : classification.importance();
        AuditResource resource = new AuditResource("api." + classification.moduleId(), classification.resourceType(),
                classification.resourceId(), classification.displayName());
        publisher.publish(AuditEvent.builder(classification.activityType(), classification.moduleId(), classification.action(), resource)
                .importance(importance).result(result).correlationId(AuditRequestContext.correlationId())
                .metadata("method", request.getMethod()).metadata("endpoint", request.getRequestURI())
                .metadata("status", response.getStatus()).metadata("durationMillis", elapsedMillis).build());
    }
}

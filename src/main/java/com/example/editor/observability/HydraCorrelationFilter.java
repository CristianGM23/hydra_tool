package com.example.editor.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.editor.audit.AuditRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** End-to-end correlation boundary shared by errors, audit events and operational metrics. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HydraCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER="X-Hydra-Correlation-Id";
    private static final Pattern SAFE=Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private final HydraObservabilityRegistry metrics;
    private final HydraOperationTraceRegistry traces;
    /** Descripción: Inicializa una instancia de HydraCorrelationFilter con sus dependencias y datos de entrada. Entradas: metrics: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraCorrelationFilter(HydraObservabilityRegistry metrics,HydraOperationTraceRegistry traces){this.metrics=metrics;this.traces=traces;}
    /** Descripción: Evalúa la condición «shouldNotFilter» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override protected boolean shouldNotFilter(HttpServletRequest request){String uri=request.getRequestURI();return uri==null||!uri.startsWith("/api/");}
    /** Descripción: Ejecuta la operación «doFilterInternal» correspondiente a la responsabilidad de HydraCorrelationFilter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; response: dato de entrada necesario para la operación; chain: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String supplied=request.getHeader(HEADER);String correlation=supplied!=null&&SAFE.matcher(supplied).matches()?supplied:UUID.randomUUID().toString();
        long started=System.nanoTime();Throwable failure=null;
        try(AuditRequestContext.Scope ignored=AuditRequestContext.begin(correlation)){
            MDC.put("hydraCorrelationId",correlation);response.setHeader(HEADER,correlation);traces.record(correlation,"HTTP","START",request.getMethod()+" "+request.getRequestURI());
            try{chain.doFilter(request,response);}catch(IOException|ServletException|RuntimeException ex){failure=ex;throw ex;}
            finally{long elapsed=Math.max(0,(System.nanoTime()-started)/1_000_000L);metrics.record(HydraObservabilityRegistry.classify(request.getRequestURI()),elapsed,failure!=null||response.getStatus()>=400);traces.record(correlation,"HTTP",failure!=null||response.getStatus()>=400?"FAILED":"COMPLETED",request.getRequestURI()+" status="+response.getStatus()+" durationMs="+elapsed);MDC.remove("hydraCorrelationId");}
        }
    }
}

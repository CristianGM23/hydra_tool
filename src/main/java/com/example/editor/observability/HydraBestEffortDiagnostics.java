package com.example.editor.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.editor.audit.AuditRequestContext;

/** Bounded diagnostics for intentionally best-effort cleanup/inspection paths. */
public final class HydraBestEffortDiagnostics {
    private static final Logger LOG=LoggerFactory.getLogger(HydraBestEffortDiagnostics.class);
    private static final int MAX_SITES=256;
    private static final ConcurrentHashMap<String,AtomicLong> COUNTS=new ConcurrentHashMap<>();
    /** Descripción: Inicializa una instancia de HydraBestEffortDiagnostics con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private HydraBestEffortDiagnostics(){ }

    /** Descripción: Ejecuta la operación «record» correspondiente a la responsabilidad de HydraBestEffortDiagnostics. Entradas: site: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void record(String site, Throwable failure) {
        String key=site==null||site.isBlank()?"unknown":site;
        if(COUNTS.size()>=MAX_SITES&&!COUNTS.containsKey(key)) key="other";
        long count=COUNTS.computeIfAbsent(key,ignored->new AtomicLong()).incrementAndGet();
        // Log first occurrence and powers of two: diagnostic without log storms.
        if(count==1||(count&(count-1))==0) {
            LOG.warn("hydra.best_effort_failure site={} count={} correlationId={} exception={} message={}",
                    key,count,AuditRequestContext.correlationId(),failure==null?"":failure.getClass().getSimpleName(),
                    failure==null?"":safe(failure.getMessage()));
        }
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de HydraBestEffortDiagnostics. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String,Long> snapshot(){
        Map<String,Long> result=new LinkedHashMap<>();
        COUNTS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->result.put(entry.getKey(),entry.getValue().get()));
        return Map.copyOf(result);
    }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de HydraBestEffortDiagnostics. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value){return value==null?"":value.replace('\n',' ').replace('\r',' ');}
}

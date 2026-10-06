package com.example.editor.observability;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only aggregated metrics plus a privacy-preserving rendering metric sink. */
@RestController
@RequestMapping("/api/system/observability")
public class HydraObservabilityController {
    private final HydraObservabilityRegistry registry;
    /** Descripción: Inicializa una instancia de HydraObservabilityController con sus dependencias y datos de entrada. Entradas: registry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraObservabilityController(HydraObservabilityRegistry registry){this.registry=registry;}
    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de RenderingMetric. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public ResponseEntity<Map<String,Object>> status(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registry.snapshot());}
    @PostMapping("/rendering") public ResponseEntity<Void> rendering(@RequestBody RenderingMetric metric){registry.record(HydraObservabilityRegistry.Area.RENDERING,Math.max(0,Math.round(metric.durationMs())),false);return ResponseEntity.noContent().build();}
    public record RenderingMetric(String phase,double durationMs,int nodeCount,int edgeCount){public RenderingMetric{phase=phase==null?"unknown":phase;nodeCount=Math.max(0,nodeCount);edgeCount=Math.max(0,edgeCount);}}
}

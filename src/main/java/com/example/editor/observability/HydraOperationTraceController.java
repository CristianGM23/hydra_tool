package com.example.editor.observability;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API de diagnóstico para seguir una operación completa mediante correlationId. */
@RestController
@RequestMapping("/api/observability/traces")
public class HydraOperationTraceController {
    private final HydraOperationTraceRegistry registry;
    /** Descripción: Inicializa el controlador con el registro acotado. Entradas: registry: registro de hitos. Salida: deja la instancia lista. */ public HydraOperationTraceController(HydraOperationTraceRegistry registry){this.registry=registry;}
    /** Descripción: Busca hitos de una correlación. Entradas: correlationId: identificador. Salida: secuencia temporal. */ @GetMapping public List<HydraOperationTraceRegistry.Event> find(@RequestParam String correlationId){return registry.find(correlationId);}
    /** Descripción: Obtiene los últimos hitos globales para diagnóstico. Entradas: limit: máximo solicitado. Salida: eventos recientes. */ @GetMapping("/recent") public List<HydraOperationTraceRegistry.Event> recent(@RequestParam(defaultValue="100") int limit){return registry.recent(limit);}
}

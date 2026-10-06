package com.example.editor.persistence.evolution;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API de consulta para comprobar una ruta de migración antes de modificar documentos persistidos. */
@RestController
@RequestMapping("/api/schema-evolution")
public class HydraSchemaEvolutionController {
    private final HydraSchemaEvolutionService service;

    /** Descripción: Inicializa el controlador de planificación de migraciones. Entradas: service: servicio de evolución registrado. Salida: deja el controlador preparado. */
    public HydraSchemaEvolutionController(HydraSchemaEvolutionService service) { this.service = service; }

    @GetMapping("/plan")
    /** Descripción: Calcula un plan de migración sin modificar almacenamiento. Entradas: namespace: familia documental; fromVersion: versión de origen; targetVersion: versión destino. Salida: plan determinista con alcanzabilidad y pasos. */
    public HydraSchemaEvolutionPlan plan(@RequestParam String namespace, @RequestParam String fromVersion,
            @RequestParam String targetVersion) {
        return service.plan(namespace, fromVersion, targetVersion);
    }
}

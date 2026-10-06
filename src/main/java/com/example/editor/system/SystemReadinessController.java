package com.example.editor.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expone el readiness operativo de la instalación sin mutar el proyecto. */
@RestController
@RequestMapping("/api/system")
public class SystemReadinessController {
    private final HydraPlatformReadinessService service;

    /** Descripción: Inicializa una instancia de SystemReadinessController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SystemReadinessController(HydraPlatformReadinessService service) {
        this.service = service;
    }

    @GetMapping("/readiness")
    /** Descripción: Obtiene la información correspondiente a «readiness» dentro de SystemReadinessController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public HydraPlatformReadinessReport readiness() {
        return service.evaluate();
    }
}

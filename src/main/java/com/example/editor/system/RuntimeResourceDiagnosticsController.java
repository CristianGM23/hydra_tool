package com.example.editor.system;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Diagnóstico local sin datos funcionales ni secretos. */
@RestController
@RequestMapping("/api/system")
public class RuntimeResourceDiagnosticsController {
    private final RuntimeResourceDiagnosticsService service;
    /** Descripción: Inicializa una instancia de RuntimeResourceDiagnosticsController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RuntimeResourceDiagnosticsController(RuntimeResourceDiagnosticsService service){this.service=service;}
    @GetMapping("/runtime-resources") public ResponseEntity<RuntimeResourceStatus> status(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status());}
}

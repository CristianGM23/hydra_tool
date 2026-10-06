package com.example.editor.artifact.registry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.runtime.job.HydraJobSnapshot;

/** API HTTP para el registro neutral, su grafo, impacto y compatibilidad. */
@RestController
@RequestMapping("/api/artifact-registry")
public class HydraArtifactRegistryController {
    private final HydraArtifactRegistryService service;

    /** Descripción: Inicializa el controlador del registro. Entradas: service: servicio de aplicación. Salida: deja el controlador listo. */
    public HydraArtifactRegistryController(HydraArtifactRegistryService service) { this.service = service; }

    /** Descripción: Obtiene la instantánea actual del registro. Entradas: no requiere parámetros. Salida: snapshot. */
    @GetMapping public HydraArtifactRegistrySnapshot snapshot() { return service.snapshot(); }

    /** Descripción: Obtiene el grafo completo de dependencias. Entradas: no requiere parámetros. Salida: grafo. */
    @GetMapping("/graph") public HydraArtifactDependencyGraph graph() { return service.graph(); }

    /** Descripción: Calcula impacto transitivo de un artefacto. Entradas: id: identificador objetivo. Salida: impacto y orden seguro. */
    @GetMapping("/impact/{id}") public HydraArtifactImpact impact(@PathVariable String id) { return service.impact(id); }

    /** Descripción: Evalúa compatibilidad de un artefacto con sus dependencias actuales. Entradas: id: identificador objetivo. Salida: informe de compatibilidad. */
    @GetMapping("/compatibility/{id}") public HydraArtifactCompatibilityReport compatibility(@PathVariable String id) { return service.compatibility(id); }

    /** Descripción: Ejecuta un escaneo síncrono del inventario. Entradas: no requiere parámetros. Salida: snapshot actualizado. */
    @PostMapping("/scan") public HydraArtifactRegistrySnapshot scan() { return service.scan(); }

    /** Descripción: Encola un escaneo asíncrono en el gestor global de trabajos. Entradas: correlationId: cabecera opcional de correlación. Salida: trabajo registrado. */
    @PostMapping("/scan/jobs") public HydraJobSnapshot scanAsync(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return service.scanAsync(correlationId);
    }

    /** Descripción: Registra o sustituye un descriptor neutral. Entradas: descriptor: descriptor a publicar. Salida: snapshot actualizado. */
    @PostMapping public HydraArtifactRegistrySnapshot register(@RequestBody HydraArtifactDescriptor descriptor) {
        return service.register(descriptor);
    }
}

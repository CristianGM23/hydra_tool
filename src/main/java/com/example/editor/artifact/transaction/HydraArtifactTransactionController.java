package com.example.editor.artifact.transaction;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API operativa para inspeccionar journals que requieren recuperación manual. */
@RestController
@RequestMapping("/api/artifact-transactions")
public class HydraArtifactTransactionController {
    private final HydraArtifactTransactionService service;

    /** Descripción: Inicializa el controlador de journals. Entradas: service: servicio transaccional. Salida: deja el controlador preparado. */
    public HydraArtifactTransactionController(HydraArtifactTransactionService service) { this.service = service; }

    @GetMapping("/pending")
    /** Descripción: Lista operaciones de artefactos incompletas. Entradas: no requiere parámetros. Salida: journals pendientes. */
    public List<HydraArtifactTransactionJournal> pending() { return service.pending(); }

    @DeleteMapping("/{id}")
    /** Descripción: Descarta un journal pendiente tras decisión explícita del operador. Entradas: id: identificador de transacción. Salida: no devuelve contenido. */
    public void abort(@PathVariable String id) { service.abort(id); }
}

package com.example.editor.performance.cache;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** API operativa del inventario de cachés. */
@RestController
@RequestMapping("/api/runtime-caches")
public class HydraCacheInventoryController {
    private final HydraCacheInventoryService service;
    /** Descripción: Inicializa el controlador con el inventario. Entradas: service: servicio. Salida: instancia lista. */ public HydraCacheInventoryController(HydraCacheInventoryService service){this.service=service;}
    /** Descripción: Devuelve políticas y métricas de cachés críticas. Entradas: ninguna. Salida: descriptores. */ @GetMapping public List<HydraCacheInventoryService.Descriptor> list(){return service.snapshot();}
}

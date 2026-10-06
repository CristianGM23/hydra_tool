package com.example.editor.integration.representable;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for bidirectional representable import previews and neutral packages. */
@RestController
@RequestMapping("/api/representable-imports")
public class RepresentableImportController {
    private final RepresentableImportService service;
    /** Descripción: Inicializa una instancia de RepresentableImportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RepresentableImportController(RepresentableImportService service){this.service = service;}
    @GetMapping("/technologies") public List<String> technologies(){return service.technologies();}
    @PostMapping("/preview") public RepresentableImportPreview preview(@RequestBody RepresentableImportRequest request){
        return service.preview(request);
    }
    @PostMapping("/apply") public RepresentableImportPreview apply(@RequestBody RepresentableImportRequest request){
        return service.apply(request);
    }
}

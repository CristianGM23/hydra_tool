package com.example.editor.integration.compatibility;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UI-facing API for safe native adapter verification. */
@RestController
@RequestMapping("/api/toolchain-compatibility")
public class NativeToolchainCompatibilityController {
    private final NativeToolchainCompatibilityApplicationService service;
    /** Descripción: Inicializa una instancia de NativeToolchainCompatibilityController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NativeToolchainCompatibilityController(NativeToolchainCompatibilityApplicationService service){this.service = service;}
    @GetMapping("/catalog") public List<NativeToolchainCatalogEntry> catalog(){return service.catalog();}
    @GetMapping("/latest") public ResponseEntity<TechnologyCompatibilityMatrix> latest(){
        return service.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @PostMapping("/run") public TechnologyCompatibilityMatrix run(@RequestBody(required = false) NativeToolchainCompatibilityRunRequest request){
        return service.run(request == null ? new NativeToolchainCompatibilityRunRequest(List.of()) : request);
    }
}

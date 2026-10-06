package com.example.editor.generated.project;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for generated project registration and lifecycle execution. */
@RestController
@RequestMapping("/api/generated-projects")
public class GeneratedProjectLifecycleController {
    private final GeneratedProjectLifecycleService service;
    /** Descripción: Inicializa una instancia de GeneratedProjectLifecycleController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GeneratedProjectLifecycleController(GeneratedProjectLifecycleService service){this.service = service;}
    @GetMapping("/profiles") public List<GeneratedProjectRuntimeProfile> profiles(){return service.profiles();}
    /** Descripción: Ejecuta la operación «registry» correspondiente a la responsabilidad de GeneratedProjectLifecycleController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public GeneratedProjectRegistry registry(){return service.registry();}
    /** Descripción: Ejecuta la operación «register» correspondiente a la responsabilidad de GeneratedProjectLifecycleController. Entradas: definition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public GeneratedProjectRegistry register(@RequestBody GeneratedProjectDefinition definition){
        return service.register(definition);
    }
    @PostMapping("/execute") public GeneratedProjectExecutionReport execute(
            @RequestBody GeneratedProjectExecutionRequest request){return service.execute(request);}
}

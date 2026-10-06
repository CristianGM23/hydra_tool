package com.example.editor.transformation.nativeexec;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.transformation.export.TransformationExportLanguage;

/** Native execution surface; server configuration owns every executable and command argument. */
@RestController
@RequestMapping("/api/transformations/parity/native")
public class TransformationNativeParityController {
    private final TransformationNativeParityService service;

    /** Descripción: Inicializa una instancia de TransformationNativeParityController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeParityController(TransformationNativeParityService service){this.service=service;}

    @GetMapping("/runtimes")
    /** Descripción: Procesa la operación «runtimes» coordinando las responsabilidades de TransformationNativeParityController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationNativeRuntimeDescriptor> runtimes(){return service.runtimes();}

    @PostMapping("/runtimes/{language}/diagnose")
    /** Descripción: Ejecuta la operación «diagnose» correspondiente a la responsabilidad de TransformationNativeParityController. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationNativeRuntimeDiagnostic diagnose(@PathVariable TransformationExportLanguage language){return service.diagnose(language);}

    @PostMapping("/execute")
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de TransformationNativeParityController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationNativeParityReport execute(@RequestBody TransformationNativeParityRequest request){return service.execute(request);}
}

package com.example.editor.transformation.parity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API dedicada a evidencias de paridad; no persiste ni ejecuta procesos externos por sí misma. */
@RestController
@RequestMapping("/api/transformations/parity")
public class TransformationParityController {
    private final TransformationParityService parity;

    /** Descripción: Inicializa una instancia de TransformationParityController con sus dependencias y datos de entrada. Entradas: parity: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationParityController(TransformationParityService parity) {
        this.parity = parity;
    }

    @PostMapping("/verify")
    /** Descripción: Valida las condiciones necesarias para «verify» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationParityReport verify(@RequestBody TransformationParityRequest request) {
        return parity.verify(request);
    }
}

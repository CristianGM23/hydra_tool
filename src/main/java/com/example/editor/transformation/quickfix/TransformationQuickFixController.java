package com.example.editor.transformation.quickfix;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.transformation.core.TransformationDefinitionDocument;

/** Endpoint side-effect free para sugerencias de reparación de transformaciones. */
@RestController
@RequestMapping("/api/transformations/quick-fixes")
public class TransformationQuickFixController {
    private final TransformationQuickFixService service;

    /** Descripción: Inicializa el controlador de quick-fixes. Entradas: service: servicio de sugerencias. Salida: deja el controlador preparado. */
    /** Descripción: Inicializa una instancia de TransformationQuickFixController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationQuickFixController(TransformationQuickFixService service) { this.service = service; }

    @PostMapping
    /** Descripción: Calcula quick-fixes del draft sin persistir cambios. Entradas: document: transformación actual. Salida: devuelve propuestas revisables. */
    /** Descripción: Ejecuta la operación «suggest» correspondiente a la responsabilidad de TransformationQuickFixController. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationQuickFixReport suggest(@RequestBody TransformationDefinitionDocument document) { return service.suggest(document); }
}

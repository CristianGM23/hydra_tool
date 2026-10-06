package com.example.editor.codegen;

import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expone el catálogo global de generación para authoring sin exigir que exista todavía un modelo o una sesión. */
@RestController
@RequestMapping("/api/code-generation")
public class ModelCodeGenerationCatalogController {
    private final ModelCodeGenerationService generation;

    /** Descripción: Inicializa el controlador de catálogo con el servicio de generación. Entradas: servicio de generación. Salida: controlador listo. */
    /** Descripción: Inicializa una instancia de ModelCodeGenerationCatalogController con sus dependencias y datos de entrada. Entradas: generation: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelCodeGenerationCatalogController(ModelCodeGenerationService generation) { this.generation = generation; }

    /** Descripción: Devuelve el catálogo completo y sus capacidades para configurar editores exportables. Entradas: ninguna. Salida: destinos de generación registrados. */
    @GetMapping("/targets")
    /** Descripción: Ejecuta la operación «targets» correspondiente a la responsabilidad de ModelCodeGenerationCatalogController. Entradas: no requiere parámetros. Salida: devuelve los destinos de generación registrados. */ public List<ModelCodeGenerationTarget> targets() { return generation.targets(Set.of()); }
}

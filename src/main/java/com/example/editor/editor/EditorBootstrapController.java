package com.example.editor.editor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone la información necesaria para abrir el editor gráfico.
 */
@RestController
@RequestMapping("/api/editor")
public class EditorBootstrapController {

    private final EditorBootstrapService bootstrapService;

    /**
     * Descripción:
     * Crea el controlador utilizando el servicio de arranque.
     *
     * Entrada:
     * @param bootstrapService prepara los datos iniciales del editor.
     *
     * Salida:
     * Crea una instancia de EditorBootstrapController.
     */
    /** Descripción: Inicializa una instancia de EditorBootstrapController con sus dependencias y datos de entrada. Entradas: bootstrapService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorBootstrapController(EditorBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    /**
     * Descripción:
     * Devuelve la paleta y la descripción de un metamodelo.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo solicitado.
     *
     * Salida:
     * @return la información inicial del editor.
     */
    @GetMapping("/bootstrap/{metamodelId}")
    /** Descripción: Ejecuta la operación «bootstrap» correspondiente a la responsabilidad de EditorBootstrapController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorBootstrapResponse bootstrap(@PathVariable String metamodelId) {
        return bootstrapService.create(metamodelId);
    }
}
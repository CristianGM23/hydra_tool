package com.example.editor.notation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Inicializa el registro de notaciones durante el arranque.
 */
@Component
@Order(20)
public class NotationRegistryInitializer implements ApplicationRunner {

    private final NotationRegistryService notationRegistryService;

    /**
     * Descripción:
     * Crea el inicializador.
     *
     * Entrada:
     * @param notationRegistryService administra las notaciones.
     *
     * Salida:
     * Crea una instancia de NotationRegistryInitializer.
     */
    /** Descripción: Inicializa una instancia de NotationRegistryInitializer con sus dependencias y datos de entrada. Entradas: notationRegistryService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NotationRegistryInitializer(NotationRegistryService notationRegistryService) {
        this.notationRegistryService = notationRegistryService;
    }

    /**
     * Descripción:
     * Carga las notaciones almacenadas.
     *
     * Entrada:
     * @param arguments contiene los argumentos de inicio.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    @Override
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de NotationRegistryInitializer. Entradas: arguments: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void run(ApplicationArguments arguments) {
        notationRegistryService.initialize();
    }
}
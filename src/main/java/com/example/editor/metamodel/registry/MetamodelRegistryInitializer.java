package com.example.editor.metamodel.registry;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Inicializa el registro de metamodelos durante el arranque de Spring Boot.
 */
@Component
@Order(10)
public class MetamodelRegistryInitializer implements ApplicationRunner {

    private final MetamodelRegistryService registryService;

    /**
     * Descripción:
     * Crea el inicializador utilizando el servicio de registro.
     *
     * Entrada:
     * @param registryService administra los metamodelos disponibles.
     *
     * Salida:
     * Crea una instancia de MetamodelRegistryInitializer.
     */
    /** Descripción: Inicializa una instancia de MetamodelRegistryInitializer con sus dependencias y datos de entrada. Entradas: registryService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelRegistryInitializer(MetamodelRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * Descripción:
     * Carga los archivos Ecore existentes cuando arranca la aplicación.
     *
     * Entrada:
     * @param arguments contiene los argumentos de inicio.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    @Override
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de MetamodelRegistryInitializer. Entradas: arguments: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void run(ApplicationArguments arguments) {
        registryService.initialize();
    }
}
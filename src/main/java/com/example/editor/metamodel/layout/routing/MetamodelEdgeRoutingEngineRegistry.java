package com.example.editor.metamodel.layout.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Registra los routers disponibles.
 */
@Component
public class MetamodelEdgeRoutingEngineRegistry {

    private final Map<String, MetamodelEdgeRoutingEngine> engines = new LinkedHashMap<>();

    /** Descripción: Inicializa una instancia de MetamodelEdgeRoutingEngineRegistry con sus dependencias y datos de entrada. Entradas: availableEngines: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelEdgeRoutingEngineRegistry(List<MetamodelEdgeRoutingEngine> availableEngines) {
        for(MetamodelEdgeRoutingEngine engine : availableEngines) {
            String id = normalize(engine.id());

            if(engines.putIfAbsent(id, engine) != null) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Duplicate metamodel edge routing engine: " + id);
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelEdgeRoutingEngine require(String id) {
        MetamodelEdgeRoutingEngine engine = engines.get(normalize(id));

        if(engine == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown metamodel edge routing engine: " + id);
        }

        return engine;
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de MetamodelEdgeRoutingEngineRegistry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

package com.example.editor.metamodel.layout.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Registra los colocadores de etiquetas.
 */
@Component
public class MetamodelEdgeLabelPlacementEngineRegistry {

    private final Map<String, MetamodelEdgeLabelPlacementEngine> engines = new LinkedHashMap<>();

    /** Descripción: Inicializa una instancia de MetamodelEdgeLabelPlacementEngineRegistry con sus dependencias y datos de entrada. Entradas: availableEngines: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelEdgeLabelPlacementEngineRegistry(List<MetamodelEdgeLabelPlacementEngine> availableEngines) {
        for(MetamodelEdgeLabelPlacementEngine engine : availableEngines) {
            String id = normalize(engine.id());

            if(engines.putIfAbsent(id, engine) != null) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Duplicate metamodel edge label placement engine: " + id);
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelEdgeLabelPlacementEngine require(String id) {
        MetamodelEdgeLabelPlacementEngine engine = engines.get(normalize(id));

        if(engine == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown metamodel edge label placement engine: " + id);
        }

        return engine;
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de MetamodelEdgeLabelPlacementEngineRegistry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

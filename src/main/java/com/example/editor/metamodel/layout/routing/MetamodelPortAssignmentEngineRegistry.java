package com.example.editor.metamodel.layout.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Registra los asignadores de puertos disponibles.
 */
@Component
public class MetamodelPortAssignmentEngineRegistry {

    private final Map<String, MetamodelPortAssignmentEngine> engines = new LinkedHashMap<>();

    /** Descripción: Inicializa una instancia de MetamodelPortAssignmentEngineRegistry con sus dependencias y datos de entrada. Entradas: availableEngines: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelPortAssignmentEngineRegistry(List<MetamodelPortAssignmentEngine> availableEngines) {
        for(MetamodelPortAssignmentEngine engine : availableEngines) {
            String id = normalize(engine.id());

            if(engines.putIfAbsent(id, engine) != null) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Duplicate metamodel port assignment engine: " + id);
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelPortAssignmentEngine require(String id) {
        MetamodelPortAssignmentEngine engine = engines.get(normalize(id));

        if(engine == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown metamodel port assignment engine: " + id);
        }

        return engine;
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de MetamodelPortAssignmentEngineRegistry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

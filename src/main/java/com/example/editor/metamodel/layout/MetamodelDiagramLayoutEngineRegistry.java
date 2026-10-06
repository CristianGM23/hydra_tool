package com.example.editor.metamodel.layout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Registra los algoritmos de layout disponibles.
 */
@Component
public class MetamodelDiagramLayoutEngineRegistry {

    private final Map<String, MetamodelDiagramLayoutEngine> engines;

    /** Descripción: Inicializa una instancia de MetamodelDiagramLayoutEngineRegistry con sus dependencias y datos de entrada. Entradas: availableEngines: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDiagramLayoutEngineRegistry(List<MetamodelDiagramLayoutEngine> availableEngines) {
        Map<String, MetamodelDiagramLayoutEngine> result = new LinkedHashMap<>();

        for(MetamodelDiagramLayoutEngine engine : availableEngines) {
            String id = engine.id().trim().toLowerCase(java.util.Locale.ROOT);

            if(result.put(id, engine) != null) {
                throw new IllegalStateException("Duplicate metamodel layout engine registration: " + id);
            }
        }

        this.engines = Map.copyOf(result);
    }

    /**
     * Descripción:
     * Obtiene un algoritmo registrado.
     *
     * @param id identifica el algoritmo.
     * @return el algoritmo.
     */
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramLayoutEngine require(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        MetamodelDiagramLayoutEngine engine = engines.get(normalized);

        if(engine == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel layout engine is not registered: " + id);
        }

        return engine;
    }

    /** Descripción: Ejecuta la operación «ids» correspondiente a la responsabilidad de MetamodelDiagramLayoutEngineRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> ids() {
        return engines.keySet().stream().sorted().toList();
    }
}

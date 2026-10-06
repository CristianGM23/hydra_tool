package com.example.editor.integration.compiler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;

/** Registro extensible que impide adaptadores duplicados y evita condicionales por tecnología. */
@Component
public class TechnologyAdapterRegistry {
    private final Map<IntegrationTechnology, TechnologyAdapter> adapters;

    /** Descripción: Inicializa una instancia de TechnologyAdapterRegistry con sus dependencias y datos de entrada. Entradas: availableAdapters: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyAdapterRegistry(List<TechnologyAdapter> availableAdapters){
        Map<IntegrationTechnology, TechnologyAdapter> indexed = new EnumMap<>(IntegrationTechnology.class);
        for(TechnologyAdapter adapter : availableAdapters){
            TechnologyAdapter previous = indexed.putIfAbsent(adapter.technology(), adapter);
            if(previous != null){throw new IllegalStateException("Duplicate technology adapter: " + adapter.technology());}
        }
        adapters = Map.copyOf(indexed);
    }

    /** Descripción: Evalúa si la tecnología dispone de una implementación registrada sin lanzar excepciones. Entradas: technology: tecnología que debe comprobarse. Salida: devuelve un indicador que expresa si existe una implementación registrada. */ public boolean supports(IntegrationTechnology technology){return technology != null && adapters.containsKey(technology);}

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyAdapter require(IntegrationTechnology technology){
        TechnologyAdapter adapter = adapters.get(technology);
        if(adapter == null){throw new TechnologyCompilationException("Technology adapter is not implemented: " + technology);}
        return adapter;
    }

    /** Descripción: Ejecuta la operación «technologies» correspondiente a la responsabilidad de TechnologyAdapterRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<IntegrationTechnology> technologies(){return List.copyOf(adapters.keySet());}
}

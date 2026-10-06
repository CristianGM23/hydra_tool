package com.example.editor.integration.projection.canonical;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registro extensible y sin duplicados de formatos de proyección. */
@Component
public class CanonicalProjectionAdapterRegistry {
    private final Map<CanonicalProjectionFormat, CanonicalProjectionAdapter> adapters;
    /** Descripción: Inicializa una instancia de CanonicalProjectionAdapterRegistry con sus dependencias y datos de entrada. Entradas: values: colección de elementos que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalProjectionAdapterRegistry(List<CanonicalProjectionAdapter> values){
        EnumMap<CanonicalProjectionFormat, CanonicalProjectionAdapter> result = new EnumMap<>(CanonicalProjectionFormat.class);
        for(CanonicalProjectionAdapter value : values){if(result.put(value.format(), value) != null){throw new IllegalStateException("Duplicate canonical projection adapter: " + value.format());}}
        adapters = Map.copyOf(result);
    }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: format: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CanonicalProjectionAdapter require(CanonicalProjectionFormat format){
        CanonicalProjectionAdapter value = adapters.get(format); if(value == null){throw new IllegalArgumentException("Unsupported projection format: " + format);} return value;
    }
}

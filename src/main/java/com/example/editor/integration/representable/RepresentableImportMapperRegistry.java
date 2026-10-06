package com.example.editor.integration.representable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Ordered registry of technology-specific representability mappers. */
@Component
public class RepresentableImportMapperRegistry {
    private final List<RepresentableImportMapper> mappers;
    /** Descripción: Inicializa una instancia de RepresentableImportMapperRegistry con sus dependencias y datos de entrada. Entradas: mappers: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RepresentableImportMapperRegistry(List<RepresentableImportMapper> mappers){
        this.mappers = mappers.stream().sorted(Comparator.comparing(RepresentableImportMapper::technology)).toList();
    }
    /** Descripción: Ejecuta la operación «technologies» correspondiente a la responsabilidad de RepresentableImportMapperRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> technologies(){return mappers.stream().map(RepresentableImportMapper::technology).toList();}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RepresentableImportMapper require(String technology){
        String token = technology == null ? "" : technology.trim().toUpperCase(Locale.ROOT);
        return mappers.stream().filter(mapper -> mapper.technology().equals(token)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No representable import mapper for: " + technology));
    }
}

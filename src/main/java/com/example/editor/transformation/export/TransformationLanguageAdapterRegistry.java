package com.example.editor.transformation.export;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registro extensible y fail-fast de adaptadores de transformación. */
@Component
public class TransformationLanguageAdapterRegistry {
    private final Map<TransformationExportLanguage,TransformationLanguageAdapter> adapters;
    /** Indexa los adaptadores y rechaza tecnologías duplicadas. */ /** Descripción: Inicializa una instancia de TransformationLanguageAdapterRegistry con sus dependencias y datos de entrada. Entradas: values: colección de elementos que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationLanguageAdapterRegistry(List<TransformationLanguageAdapter> values){
        Map<TransformationExportLanguage,TransformationLanguageAdapter> indexed=new EnumMap<>(TransformationExportLanguage.class);
        for(TransformationLanguageAdapter adapter:values){if(indexed.putIfAbsent(adapter.language(),adapter)!=null)throw new IllegalStateException("Duplicate transformation adapter: "+adapter.language());}
        adapters=Map.copyOf(indexed);
    }
    /** Obtiene el adaptador solicitado o falla de forma explícita. */ /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationLanguageAdapter require(TransformationExportLanguage language){
        TransformationLanguageAdapter adapter=adapters.get(language);if(adapter==null)throw new IllegalArgumentException("Transformation adapter is not implemented: "+language);return adapter;
    }
    /** Enumera lenguajes disponibles de forma estable. */ /** Descripción: Ejecuta la operación «languages» correspondiente a la responsabilidad de TransformationLanguageAdapterRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationExportLanguage> languages(){return adapters.keySet().stream().sorted().toList();}
}

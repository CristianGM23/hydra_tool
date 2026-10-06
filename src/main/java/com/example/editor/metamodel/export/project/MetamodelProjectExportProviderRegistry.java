package com.example.editor.metamodel.export.project;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registro extensible y validado de proveedores de proyecto completo. */
@Component
public class MetamodelProjectExportProviderRegistry {
    private final Map<String, MetamodelProjectExportProvider> providers;

    /** Descripción: Inicializa una instancia de MetamodelProjectExportProviderRegistry con sus dependencias y datos de entrada. Entradas: available: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelProjectExportProviderRegistry(List<MetamodelProjectExportProvider> available){
        Map<String, MetamodelProjectExportProvider> indexed = new LinkedHashMap<>();
        for(MetamodelProjectExportProvider provider : available.stream().sorted(Comparator.comparingInt(MetamodelProjectExportProvider::order).thenComparing(MetamodelProjectExportProvider::id)).toList()){
            String id = provider.id() == null ? "" : provider.id().trim().toLowerCase();
            if(id.isBlank()){throw new IllegalStateException("Complete project export provider id is required");}
            if(indexed.put(id, provider) != null){throw new IllegalStateException("Duplicate complete project export provider: " + id);}
        }
        providers = Map.copyOf(indexed);
    }

    /** Descripción: Ejecuta la operación «all» correspondiente a la responsabilidad de MetamodelProjectExportProviderRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelProjectExportProvider> all(){return providers.values().stream().sorted(Comparator.comparingInt(MetamodelProjectExportProvider::order).thenComparing(MetamodelProjectExportProvider::id)).toList();}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelProjectExportProvider require(String id){
        MetamodelProjectExportProvider provider = providers.get(id == null ? "" : id.trim().toLowerCase());
        if(provider == null){throw new IllegalArgumentException("Unsupported complete project export: " + id);}
        return provider;
    }
}

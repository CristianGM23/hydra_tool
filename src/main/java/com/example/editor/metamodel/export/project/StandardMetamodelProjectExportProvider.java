package com.example.editor.metamodel.export.project;

import java.util.Map;
import java.util.function.Function;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.metamodel.core.MetaModel;

/** Implementación declarativa para los proveedores estándar incluidos en HYDRA. */
public final class StandardMetamodelProjectExportProvider implements MetamodelProjectExportProvider {
    private final String id; private final String displayName; private final String description;
    private final IntegrationTechnology technology; private final IntegrationTargetKind targetKind;
    private final IntegrationBuildSystem buildSystem; private final boolean requiresEditor; private final int order;
    private final Function<MetaModel, Map<String, String>> defaults;

    /** Descripción: Inicializa una instancia de StandardMetamodelProjectExportProvider con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; displayName: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; targetKind: dato de entrada necesario para la operación; buildSystem: dato de entrada necesario para la operación; requiresEditor: dato de entrada necesario para la operación; order: dato de entrada necesario para la operación; defaults: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StandardMetamodelProjectExportProvider(String id, String displayName, String description, IntegrationTechnology technology,
            IntegrationTargetKind targetKind, IntegrationBuildSystem buildSystem, boolean requiresEditor, int order,
            Function<MetaModel, Map<String, String>> defaults){
        this.id = id; this.displayName = displayName; this.description = description; this.technology = technology;
        this.targetKind = targetKind; this.buildSystem = buildSystem; this.requiresEditor = requiresEditor; this.order = order; this.defaults = defaults;
    }
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de StandardMetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String id(){return id;}
    /** Descripción: Ejecuta la operación «displayName» correspondiente a la responsabilidad de StandardMetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String displayName(){return displayName;}
    /** Descripción: Ejecuta la operación «description» correspondiente a la responsabilidad de StandardMetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String description(){return description;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de StandardMetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return technology;}
    /** Descripción: Ejecuta la operación «targetKind» correspondiente a la responsabilidad de StandardMetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTargetKind targetKind(){return targetKind;}
    /** Descripción: Crea o prepara el resultado de «buildSystem» dentro de StandardMetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationBuildSystem buildSystem(){return buildSystem;}
    /** Descripción: Valida las condiciones necesarias para «requiresEditor» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean requiresEditor(){return requiresEditor;}
    /** Descripción: Ejecuta la operación «order» correspondiente a la responsabilidad de StandardMetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int order(){return order;}
    /** Descripción: Ejecuta la operación «defaultSettings» correspondiente a la responsabilidad de StandardMetamodelProjectExportProvider. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Map<String,String> defaultSettings(MetaModel metamodel){return Map.copyOf(defaults.apply(metamodel));}
}

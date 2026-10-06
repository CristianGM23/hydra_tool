package com.example.editor.metamodel.export.project;

import java.util.Map;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.metamodel.core.MetaModel;

/** SPI abierto de exportaciones de proyecto completo desde un metamodelo validado. */
public interface MetamodelProjectExportProvider {
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String id();
    /** Descripción: Ejecuta la operación «displayName» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String displayName();
    /** Descripción: Ejecuta la operación «description» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String description();
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationTechnology technology();
    /** Descripción: Ejecuta la operación «targetKind» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationTargetKind targetKind();
    /** Descripción: Crea o prepara el resultado de «buildSystem» dentro de MetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationBuildSystem buildSystem();
    /** Descripción: Valida las condiciones necesarias para «requiresEditor» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean requiresEditor();
    /** Descripción: Ejecuta la operación «order» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ default int order(){return 1000;}
    /** Descripción: Ejecuta la operación «defaultSettings» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, String> defaultSettings(MetaModel metamodel);

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de MetamodelProjectExportProvider. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ default MetamodelProjectExportDescriptor descriptor(MetaModel metamodel){
        return new MetamodelProjectExportDescriptor(id(), displayName(), description(), technology().name(), requiresEditor(), true, ".zip", defaultSettings(metamodel));
    }
}

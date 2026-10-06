package com.example.editor.metamodel.export;

/** Carga un contexto de exportación directamente desde el repositorio canónico. */
@FunctionalInterface
public interface NeutralMetamodelExportContextLoader {
    /** Descripción: Obtiene la información correspondiente a «load» dentro de NeutralMetamodelExportContextLoader. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ NeutralMetamodelExportContext load(String metamodelId);

    /** Descripción: Obtiene la información correspondiente a «load» dentro de NeutralMetamodelExportContextLoader. Entradas: metamodelId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ default NeutralMetamodelExportContext load(String metamodelId, MetamodelExportOptions options) {
        NeutralMetamodelExportContext context = load(metamodelId);
        MetamodelExportOptions effectiveOptions = options == null ? MetamodelExportOptions.defaults() : options;
        if(effectiveOptions.equals(context.options())) {
            return context;
        }
        return new DefaultNeutralMetamodelExportContext(context.neutralMetamodel(), context.diagram(), context.targets(), context.warnings(), effectiveOptions);
    }
}

package com.example.editor.metamodel.export;

import java.util.List;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/** Contexto único de exportación construido desde la fuente canónica. */
public interface NeutralMetamodelExportContext {
    /** Descripción: Ejecuta la operación «neutralMetamodel» correspondiente a la responsabilidad de NeutralMetamodelExportContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetaModel neutralMetamodel();
    /** Descripción: Ejecuta la operación «diagram» correspondiente a la responsabilidad de NeutralMetamodelExportContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelDiagramDocument diagram();
    /** Descripción: Ejecuta la operación «targets» correspondiente a la responsabilidad de NeutralMetamodelExportContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ TargetConfigurationDocument targets();
    /** Descripción: Ejecuta la operación «warnings» correspondiente a la responsabilidad de NeutralMetamodelExportContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<TransformationWarning> warnings();
    /** Descripción: Ejecuta la operación «options» correspondiente a la responsabilidad de NeutralMetamodelExportContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelExportOptions options();
}

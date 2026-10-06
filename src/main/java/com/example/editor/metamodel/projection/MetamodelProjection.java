package com.example.editor.metamodel.projection;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/**
 * Define un generador desacoplado para un destino técnico concreto.
 */
public interface MetamodelProjection {

    /** Descripción: Ejecuta la operación «targetId» correspondiente a la responsabilidad de MetamodelProjection. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String targetId();

    /** Descripción: Crea o prepara el resultado de «generate» dentro de MetamodelProjection. Entradas: metamodel: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ ProjectionResult generate(MetaModel metamodel, TargetConfigurationDocument configuration);

    /** Descripción: Elimina o libera el recurso gestionado por «deleteArtifact» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; configuration: configuración que gobierna el comportamiento. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteArtifact(String metamodelId, TargetConfigurationDocument configuration);
}

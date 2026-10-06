package com.example.editor.metamodel.target;

import java.util.Optional;

/**
 * Persiste la configuración de destinos sin mezclarla con el MetaModel.
 */
public interface TargetConfigurationRepository {

    /** Descripción: Obtiene la información correspondiente a «findByMetamodelId» dentro de TargetConfigurationRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<TargetConfigurationDocument> findByMetamodelId(String metamodelId);

    /** Descripción: Actualiza el estado gestionado por TargetConfigurationRepository mediante la operación «save». Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TargetConfigurationDocument save(TargetConfigurationDocument document);

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId);
}

package com.example.editor.metamodel.projection;

import java.util.Optional;

/**
 * Persiste el estado de las proyecciones sin mezclarlo con el MetaModel.
 */
public interface ProjectionManifestRepository {

    /** Descripción: Obtiene la información correspondiente a «findByMetamodelId» dentro de ProjectionManifestRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<ProjectionManifest> findByMetamodelId(String metamodelId);

    /** Descripción: Actualiza el estado gestionado por ProjectionManifestRepository mediante la operación «save». Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ProjectionManifest save(ProjectionManifest manifest);

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId);
}

package com.example.editor.project.recovery;

import com.example.editor.metamodel.core.MetaModel;

/**
 * Observador de los efectos externos de una importación. Los métodos {@code …AboutToBe…} se invocan <b>antes</b> del
 * efecto y, si registran en un diario durable, deben fallar (lanzando excepción) cuando no puedan garantizar que el
 * efecto es recuperable: en ese caso el efecto no se ejecuta.
 */
public interface ExternalImportListener {
    /** Listener sin efectos, usado por los caminos que no necesitan compensación. */
    ExternalImportListener NONE = new ExternalImportListener() { };

    /** Antes de persistir un metamodelo que no existía: todo documento con ese id pertenece a la importación mientras su contenido sea el esperado. */
    /** Descripción: Ejecuta la operación «metamodelAboutToBeCreated» correspondiente a la responsabilidad de ExternalImportListener. Entradas: metamodel: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void metamodelAboutToBeCreated(MetaModel metamodel) { }

    /** Tras persistir el metamodelo creado. */
    /** Descripción: Ejecuta la operación «metamodelCreated» correspondiente a la responsabilidad de ExternalImportListener. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void metamodelCreated(String metamodelId) { }

    /** Antes de añadir o sustituir capas sobre un metamodelo existente (se conserva el estado previo para restaurarlo). */
    /** Descripción: Ejecuta la operación «metamodelReused» correspondiente a la responsabilidad de ExternalImportListener. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void metamodelReused(String metamodelId) { }

    /** Antes de guardar un recurso gráfico; {@code existedBefore} indica si el fichero ya existía (entonces es compartido y no se borra). */
    /** Descripción: Ejecuta la operación «assetAboutToBeStored» correspondiente a la responsabilidad de ExternalImportListener. Entradas: portablePath: ruta o ubicación utilizada por la operación; existedBefore: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void assetAboutToBeStored(String portablePath, boolean existedBefore) { }

    /** Tras guardar un recurso gráfico; {@code created} indica si el fichero lo creó esta operación. */
    /** Descripción: Ejecuta la operación «assetStored» correspondiente a la responsabilidad de ExternalImportListener. Entradas: portablePath: ruta o ubicación utilizada por la operación; created: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void assetStored(String portablePath, boolean created) { }
}

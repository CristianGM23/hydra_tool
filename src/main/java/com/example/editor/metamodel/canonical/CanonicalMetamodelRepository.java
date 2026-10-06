package com.example.editor.metamodel.canonical;

import java.util.List;
import java.util.Optional;

import com.example.editor.metamodel.core.MetaModel;

/**
 * Define las operaciones de persistencia de la fuente semántica canónica.
 */
public interface CanonicalMetamodelRepository {

    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalMetamodelRepository. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredMetamodel create(MetaModel metamodel);

    /** Descripción: Actualiza el estado gestionado por CanonicalMetamodelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredMetamodel replace(String id, MetaModel metamodel, String expectedEtag);

    /** Descripción: Obtiene la información correspondiente a «findById» dentro de CanonicalMetamodelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredMetamodel> findById(String id);

    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de CanonicalMetamodelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<CanonicalMetamodelSummary> findAll();

    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String id);

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String expectedEtag);
}

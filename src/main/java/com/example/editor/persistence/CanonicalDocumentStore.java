package com.example.editor.persistence;

import java.util.List;
import java.util.Optional;

import com.example.editor.validation.persistence.StoredCanonicalDocument;

/**
 * Backend-neutral persistence contract for one family of canonical HYDRA documents.
 *
 * The domain repositories depend on this interface rather than on local JSON files.
 * A storage provider may therefore keep the same repository contract while changing
 * the physical persistence technology.
 */
public interface CanonicalDocumentStore<T> {
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalDocumentStore. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<T> create(T document);
    /** Descripción: Actualiza el estado gestionado por CanonicalDocumentStore mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<T> replace(String id, T document, String expectedEtag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de CanonicalDocumentStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<T>> find(String id);
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de CanonicalDocumentStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String expectedEtag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String id);
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String id);
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de CanonicalDocumentStore. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String fileName(String id);
}

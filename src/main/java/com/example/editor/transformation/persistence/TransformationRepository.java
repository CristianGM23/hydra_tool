package com.example.editor.transformation.persistence;

import java.util.List;
import java.util.Optional;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface TransformationRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de TransformationRepository. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<TransformationDefinitionDocument> create(TransformationDefinitionDocument document);
    /** Descripción: Actualiza el estado gestionado por TransformationRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<TransformationDefinitionDocument> replace(String id, TransformationDefinitionDocument document, String etag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TransformationRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<TransformationDefinitionDocument>> find(String id);
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de TransformationRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String etag);
}

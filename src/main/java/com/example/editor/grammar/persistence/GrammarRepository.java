package com.example.editor.grammar.persistence;
import java.util.*;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface GrammarRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de GrammarRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GrammarDefinitionModel> create(GrammarDefinitionModel model);
    /** Descripción: Actualiza el estado gestionado por GrammarRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GrammarDefinitionModel> replace(String id, GrammarDefinitionModel model, String etag);
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de GrammarRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<GrammarDefinitionModel>> findById(String id);
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de GrammarRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String etag);
}

package com.example.editor.definition.persistence;

import java.util.List;
import java.util.Optional;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface EditorDefinitionRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de EditorDefinitionRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EditorDefinitionModel> create(EditorDefinitionModel model);
    /** Descripción: Actualiza el estado gestionado por EditorDefinitionRepository mediante la operación «replace». Entradas: editorId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EditorDefinitionModel> replace(String editorId, EditorDefinitionModel model, String expectedEtag);
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de EditorDefinitionRepository. Entradas: editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<EditorDefinitionModel>> findById(String editorId);
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de EditorDefinitionRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: editorId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String editorId, String expectedEtag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: editorId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String editorId);
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: editorId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String editorId);
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de EditorDefinitionRepository. Entradas: editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String fileName(String editorId);
}

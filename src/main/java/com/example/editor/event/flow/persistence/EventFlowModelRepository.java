package com.example.editor.event.flow.persistence;

import java.util.List;
import java.util.Optional;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface EventFlowModelRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de EventFlowModelRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EventFlowModel> create(EventFlowModel model);
    /** Descripción: Actualiza el estado gestionado por EventFlowModelRepository mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EventFlowModel> replace(String metamodelId, EventFlowModel model, String etag);
    /** Descripción: Obtiene la información correspondiente a «findByMetamodelId» dentro de EventFlowModelRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<EventFlowModel>> findByMetamodelId(String metamodelId);
    /** Descripción: Obtiene la información correspondiente a «listMetamodelIds» dentro de EventFlowModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listMetamodelIds();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String metamodelId);
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String metamodelId);
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de EventFlowModelRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String fileName(String metamodelId);
}

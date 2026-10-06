package com.example.editor.metamodel.behavior.persistence;

import java.util.List;
import java.util.Optional;
import com.example.editor.metamodel.behavior.OperationBehaviorModelSummary;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface OperationBehaviorModelRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de OperationBehaviorModelRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<OperationBehaviorModel> create(OperationBehaviorModel model);
    /** Descripción: Actualiza el estado gestionado por OperationBehaviorModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<OperationBehaviorModel> replace(String id, OperationBehaviorModel model, String expectedEtag);
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de OperationBehaviorModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<OperationBehaviorModel>> findById(String id);
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de OperationBehaviorModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<OperationBehaviorModelSummary> findAll();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String expectedEtag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String id);
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String id);
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de OperationBehaviorModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String fileName(String id);
}

package com.example.editor.validation.expression.persistence;

import java.util.List;
import java.util.Optional;

import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelSummary;

public interface ExpressionModelRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de ExpressionModelRepository. Entradas: expressionModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredExpressionModel create(ExpressionModel expressionModel);
    /** Descripción: Actualiza el estado gestionado por ExpressionModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; expressionModel: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredExpressionModel replace(String id, ExpressionModel expressionModel, String expectedEtag);
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de ExpressionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredExpressionModel> findById(String id);
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de ExpressionModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<ExpressionModelSummary> findAll();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String expectedEtag);
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String id);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String id);
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de ExpressionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String fileName(String id);
}

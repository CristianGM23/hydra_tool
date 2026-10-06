package com.example.editor.validation.validation.persistence;
import java.util.List;
import java.util.Optional;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelSummary;
public interface ValidationModelRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de ValidationModelRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<ValidationModel> create(ValidationModel model);
    /** Descripción: Actualiza el estado gestionado por ValidationModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<ValidationModel> replace(String id, ValidationModel model, String expectedEtag);
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de ValidationModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<ValidationModel>> findById(String id);
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de ValidationModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<ValidationModelSummary> findAll();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String expectedEtag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String id);
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String id);
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de ValidationModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String fileName(String id);
}

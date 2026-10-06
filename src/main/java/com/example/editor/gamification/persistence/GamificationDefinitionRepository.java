package com.example.editor.gamification.persistence;
import java.util.List;
import java.util.Optional;
import com.example.editor.gamification.GamificationDefinitionModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface GamificationDefinitionRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de GamificationDefinitionRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GamificationDefinitionModel> create(GamificationDefinitionModel model);
    /** Descripción: Actualiza el estado gestionado por GamificationDefinitionRepository mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GamificationDefinitionModel> replace(String metamodelId, GamificationDefinitionModel model, String etag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GamificationDefinitionRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<GamificationDefinitionModel>> find(String metamodelId);
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de GamificationDefinitionRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String metamodelId);
}

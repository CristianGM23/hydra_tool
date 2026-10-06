package com.example.editor.gamification.persistence;
import java.util.Optional;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface GamificationBindingRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de GamificationBindingRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GamificationBindingModel> create(GamificationBindingModel model);
    /** Descripción: Actualiza el estado gestionado por GamificationBindingRepository mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GamificationBindingModel> replace(String metamodelId, GamificationBindingModel model, String etag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GamificationBindingRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<GamificationBindingModel>> find(String metamodelId);
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String metamodelId);
}

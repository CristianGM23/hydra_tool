package com.example.editor.gamification.diagram;

import java.util.Optional;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface GamificationDiagramRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de GamificationDiagramRepository. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GamificationDiagramDocument> create(GamificationDiagramDocument document);
    /** Descripción: Actualiza el estado gestionado por GamificationDiagramRepository mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GamificationDiagramDocument> replace(String metamodelId, GamificationDiagramDocument document, String etag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GamificationDiagramRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<GamificationDiagramDocument>> find(String metamodelId);
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String metamodelId);
}

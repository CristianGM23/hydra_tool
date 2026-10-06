package com.example.editor.grammar.persistence;
import java.util.*;
import com.example.editor.grammar.runtime.GrammarActivationState;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface GrammarActivationRepository {
    /** Descripción: Actualiza el estado gestionado por GrammarActivationRepository mediante la operación «save». Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GrammarActivationState> save(GrammarActivationState state);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GrammarActivationRepository. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<GrammarActivationState>> find(String grammarId);
}

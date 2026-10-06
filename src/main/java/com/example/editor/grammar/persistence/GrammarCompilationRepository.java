package com.example.editor.grammar.persistence;
import java.util.*;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface GrammarCompilationRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de GrammarCompilationRepository. Entradas: snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<GrammarCompilationSnapshot> create(GrammarCompilationSnapshot snapshot);
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de GrammarCompilationRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<GrammarCompilationSnapshot>> findById(String id);
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de GrammarCompilationRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
}

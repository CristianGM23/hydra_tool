package com.example.editor.grammar.runtime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.application.GrammarCompilationService;
@Service
public class LastKnownGoodGrammarService {
    private final GrammarCompilationService compilations;
    /** Descripción: Inicializa una instancia de LastKnownGoodGrammarService con sus dependencias y datos de entrada. Entradas: compilations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public LastKnownGoodGrammarService(GrammarCompilationService compilations){this.compilations=compilations;}
    /** Descripción: Ejecuta la operación «active» correspondiente a la responsabilidad de LastKnownGoodGrammarService. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<GrammarCompilationSnapshot> active(String grammarId){return compilations.active(grammarId);}
    /** Descripción: Valida las condiciones necesarias para «requireActive» y rechaza entradas no válidas. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarCompilationSnapshot requireActive(String grammarId){return active(grammarId).orElseThrow(()->new IllegalStateException("No active successful grammar snapshot for "+grammarId));}
}

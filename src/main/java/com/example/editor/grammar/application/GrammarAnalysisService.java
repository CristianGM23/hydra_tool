package com.example.editor.grammar.application;

import org.springframework.stereotype.Service;
import com.example.editor.grammar.core.*;

/** Public analysis facade over the bounded/cancelable coordinator. */
@Service
public class GrammarAnalysisService {
    private final GrammarApplicationService grammars;
    private final GrammarAnalysisCoordinator coordinator;

    /** Descripción: Inicializa una instancia de GrammarAnalysisService con sus dependencias y datos de entrada. Entradas: grammars: dato de entrada necesario para la operación; coordinator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarAnalysisService(GrammarApplicationService grammars, GrammarAnalysisCoordinator coordinator){
        this.grammars=grammars;this.coordinator=coordinator;
    }
    /** Descripción: Ejecuta la operación «analyzeDraft» correspondiente a la responsabilidad de GrammarAnalysisService. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarAnalysisResult analyzeDraft(GrammarDefinitionModel grammar,GrammarAnalysisRequest request){
        if(grammar==null)throw new IllegalArgumentException("Draft grammar is required");
        grammars.validate(grammar);
        return coordinator.analyze(grammar,request);
    }
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de GrammarAnalysisService. Entradas: grammarId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarAnalysisResult analyze(String grammarId,GrammarAnalysisRequest request){
        GrammarDefinitionModel grammar=grammars.find(grammarId).grammar();
        if(request!=null&&!request.grammarId().isBlank()&&!grammarId.equals(request.grammarId()))throw new IllegalArgumentException("Analysis grammarId does not match path");
        return coordinator.analyze(grammar,request);
    }
    /** Descripción: Evalúa la condición «cancel» sin modificar el estado externo. Entradas: requestId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean cancel(String requestId){return coordinator.cancel(requestId);}
    /** Descripción: Ejecuta la operación «cacheStatistics» correspondiente a la responsabilidad de GrammarAnalysisService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarAnalysisCache.Statistics cacheStatistics(){return coordinator.cacheStatistics();}
    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de GrammarAnalysisService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public com.example.editor.concurrent.BoundedPermitPool.Snapshot capacityStatus(){return coordinator.capacityStatus();}
}

package com.example.editor.transformation.textmodel;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.grammar.runtime.LastKnownGoodGrammarService;
import com.example.editor.textual.runtime.NeutralTextualModelParser;
import com.example.editor.textual.runtime.TextualParseResult;
import com.example.editor.textual.source.TextualModelSourceDocument;

/** Adaptador T2M que usa el snapshot activo y converge siempre al modelo canónico textual de HYDRA. */
@Service
public class GrammarTransformationTextParserGateway implements TransformationTextParserGateway {
    private final LastKnownGoodGrammarService grammars;
    private final NeutralTextualModelParser parser=new NeutralTextualModelParser();

    /** Descripción: Inicializa una instancia de GrammarTransformationTextParserGateway con sus dependencias y datos de entrada. Entradas: grammars: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarTransformationTextParserGateway(LastKnownGoodGrammarService grammars){this.grammars=grammars;}

    @Override
    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de GrammarTransformationTextParserGateway. Entradas: grammarId: identificador que delimita el recurso objetivo; relativePath: ruta o ubicación utilizada por la operación; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public TextualParseResult parse(String grammarId,String relativePath,String text){
        var snapshot=grammars.requireActive(grammarId);
        var document=new TextualModelSourceDocument("transformation-input",relativePath,text,0);
        return parser.parse(0,List.of(document),snapshot);
    }
}

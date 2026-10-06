package com.example.editor.grammar.visualization;

import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.core.MetaModel;

/** Immutable projection of one grammar draft into a neutral inferred metamodel and class diagram. */
public record GrammarClassDiagramProjection(String grammarId, long draftRevision, String sourceHash,
        String baseSourceHash, GrammarDiagramProjectionStatus status, MetaModel inferredMetamodel,
        List<GrammarClassDiagramNode> nodes, List<GrammarClassDiagramEdge> edges,
        List<GrammarProjectionWarning> warnings, String projectionHash, Map<String,String> metadata) {
    public GrammarClassDiagramProjection {
        grammarId=text(grammarId);draftRevision=Math.max(0,draftRevision);sourceHash=text(sourceHash);baseSourceHash=text(baseSourceHash);
        status=status==null?GrammarDiagramProjectionStatus.FAILED:status;
        inferredMetamodel=inferredMetamodel==null?new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION,"","",List.of(),List.of(),List.of(),List.of(),Map.of()):inferredMetamodel;
        nodes=nodes==null?List.of():List.copyOf(nodes);edges=edges==null?List.of():List.copyOf(edges);
        warnings=warnings==null?List.of():List.copyOf(warnings);projectionHash=text(projectionHash);metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }

    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de GrammarClassDiagramProjection. Entradas: grammarId: identificador que delimita el recurso objetivo; draftRevision: dato de entrada necesario para la operación; sourceHash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarClassDiagramProjection empty(String grammarId,long draftRevision,String sourceHash){
        return new GrammarClassDiagramProjection(grammarId,draftRevision,sourceHash,sourceHash,GrammarDiagramProjectionStatus.EMPTY,null,List.of(),List.of(),List.of(),"",Map.of());
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarClassDiagramProjection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}

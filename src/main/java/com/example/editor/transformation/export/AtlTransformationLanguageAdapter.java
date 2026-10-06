package com.example.editor.transformation.export;

import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationDefinitionDocument;

/** Proyección ATL nativa y fail-closed del subconjunto declarativo demostrado. */
@Component
public final class AtlTransformationLanguageAdapter extends DeclarativeM2MExportSupport {
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de AtlTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportLanguage language(){return TransformationExportLanguage.ATL;}
    /** Descripción: Crea o prepara el resultado de «generated» dentro de AtlTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; projections: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected TransformationExportResult generated(TransformationDefinitionDocument document,MetaModel source,MetaModel target,List<Projection> projections,List<TransformationExportDiagnostic> diagnostics){
        StringBuilder out=new StringBuilder("module ").append(identifier(document.name())).append(";\ncreate OUT : TARGET from IN : SOURCE;\n\n");
        for(Projection projection:projections){String sourceAlias=identifier(projection.rule().sourceAlias());String targetAlias=identifier(projection.rule().targets().get(0).alias());
            out.append("rule ").append(identifier(projection.rule().name())).append(" {\n  from ").append(sourceAlias).append(" : SOURCE!").append(identifier(projection.sourceClass().name())).append("\n  to ").append(targetAlias).append(" : TARGET!").append(identifier(projection.targetClass().name())).append(" (");
            for(int i=0;i<projection.bindings().size();i++){ResolvedBinding rb=projection.bindings().get(i);if(i>0)out.append(',');out.append("\n    ").append(identifier(rb.targetAttribute().name())).append(" <- ");
                if(rb.sourceAttribute()!=null)out.append(sourceAlias).append('.').append(identifier(rb.sourceAttribute().name()));else out.append(literal(rb.binding().literalValue()));}
            if(!projection.bindings().isEmpty())out.append('\n');out.append("  )\n}\n\n");
        }
        return new TransformationExportResult(document.id(),language(),TransformationExportFidelity.SUBSET,true,diagnostics,List.of(new TransformationExportArtifact(identifier(document.name())+".atl",out.toString())));
    }
}

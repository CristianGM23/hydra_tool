package com.example.editor.transformation.export;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationDefinitionDocument;

/** Proyección Acceleo MTL del subconjunto M2T portable demostrado por HYDRA. */
@Component
public final class AcceleoTransformationLanguageAdapter extends TemplateM2TExportSupport {
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de AcceleoTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportLanguage language(){return TransformationExportLanguage.ACCELEO;}
    /** Descripción: Ejecuta la operación «attributeExpression» correspondiente a la responsabilidad de AcceleoTransformationLanguageAdapter. Entradas: attributeName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String attributeExpression(String attributeName){return "[e."+identifier(attributeName)+"/]";}
    /** Descripción: Crea o prepara el resultado de «generated» dentro de AcceleoTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; projections: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected TransformationExportResult generated(TransformationDefinitionDocument document,MetaModel source,List<Projection> projections,List<TransformationExportDiagnostic> diagnostics){
        List<TransformationExportArtifact> files=new ArrayList<>();String module=identifier(document.name());StringBuilder out=new StringBuilder("[module ").append(module).append("('").append(source.id().replace("'","\\'")).append("')/]\n\n");
        for(Projection projection:projections){out.append("[template public ").append(identifier(projection.rule().name())).append("(e : ").append(identifier(projection.contextClass().name())).append(")]\n[file ('").append(projection.path().replace("'","\\'")).append("', false, 'UTF-8')]\n").append(projection.content()).append("\n[/file]\n[/template]\n\n");}
        files.add(new TransformationExportArtifact(module+".mtl",out.toString()));
        return new TransformationExportResult(document.id(),language(),TransformationExportFidelity.SUBSET,true,diagnostics,files);
    }
}

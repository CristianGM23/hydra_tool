package com.example.editor.transformation.export;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationDefinitionDocument;

/** Proyección EGL/EGX del subconjunto M2T portable demostrado por HYDRA. */
@Component
public final class EglTransformationLanguageAdapter extends TemplateM2TExportSupport {
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de EglTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportLanguage language(){return TransformationExportLanguage.EGL;}
    /** Descripción: Ejecuta la operación «attributeExpression» correspondiente a la responsabilidad de EglTransformationLanguageAdapter. Entradas: attributeName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String attributeExpression(String attributeName){return "[%=e."+identifier(attributeName)+"%]";}
    /** Descripción: Crea o prepara el resultado de «generated» dentro de EglTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; projections: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected TransformationExportResult generated(TransformationDefinitionDocument document,MetaModel source,List<Projection> projections,List<TransformationExportDiagnostic> diagnostics){
        List<TransformationExportArtifact> files=new ArrayList<>();StringBuilder orchestration=new StringBuilder();
        for(Projection projection:projections){String base=identifier(projection.rule().name());files.add(new TransformationExportArtifact("templates/"+base+".egl",projection.content()));orchestration.append("rule ").append(base).append(" transform e : SOURCE!").append(identifier(projection.contextClass().name())).append(" {\n  template : 'templates/").append(base).append(".egl'\n  target : '").append(projection.path().replace("'","\\'")).append("'\n}\n\n");}
        files.add(new TransformationExportArtifact(identifier(document.name())+".egx",orchestration.toString()));
        return new TransformationExportResult(document.id(),language(),TransformationExportFidelity.SUBSET,true,diagnostics,files);
    }
}

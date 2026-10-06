package com.example.editor.transformation.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;

/** Base de compatibilidad M2T para plantillas externas con proyección explícita de tokens HYDRA. */
abstract class TemplateM2TExportSupport implements TransformationLanguageAdapter {
    private static final Pattern TOKEN=Pattern.compile("\\{\\{\\s*(id|classId|attr:[A-Za-z0-9][A-Za-z0-9._:-]{0,239})\\s*}}");
    record Projection(TransformationGenerationRule rule,MetaClass contextClass,String path,String content) {}
    /** Descripción: Ejecuta la operación «supportedKinds» correspondiente a la responsabilidad de Projection. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<TransformationKind> supportedKinds(){return Set.of(TransformationKind.M2T);}
    /** Traduce únicamente reglas sin guard cuya plantilla puede resolverse con propiedades estructurales. */ /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de Projection. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportResult export(TransformationDefinitionDocument document,MetaModel source,MetaModel target){
        List<TransformationExportDiagnostic> diagnostics=new ArrayList<>();List<Projection> projections=new ArrayList<>();
        if(document==null||document.kind()!=TransformationKind.M2T)diagnostics.add(error("UNSUPPORTED_KIND","$.kind","This adapter only exports M2T transformations.","Choose an M2T definition or another adapter."));
        if(source==null)diagnostics.add(error("SOURCE_METAMODEL_REQUIRED","$.sources","M2T native export requires the source metamodel.","Select a resolvable source metamodel."));
        if(!diagnostics.isEmpty())return blocked(document,diagnostics);
        for(int i=0;i<document.generationRules().size();i++){TransformationGenerationRule rule=document.generationRules().get(i);if(!rule.enabled())continue;String path="$.generationRules["+i+"]";
            MetaClass context=source.classes().stream().filter(c->c.id().equals(rule.contextTypeId())).findFirst().orElse(null);
            if(context==null){diagnostics.add(error("CONTEXT_TYPE_NOT_FOUND",path+".contextTypeId","The generation context class no longer exists.","Select an existing source class."));continue;}
            if(rule.guard()!=null){diagnostics.add(error("GUARD_NOT_NATIVE",path+".guard","Generation guards are not yet translated by this template adapter.","Remove the guard or use Java/Xtend canonical runtime export."));continue;}
            String translatedPath=translate(rule.outputPathTemplate(),context,path+".outputPathTemplate",diagnostics);
            String translatedContent=translate(rule.contentTemplate(),context,path+".contentTemplate",diagnostics);
            projections.add(new Projection(rule,context,translatedPath,translatedContent));
        }
        if(diagnostics.stream().anyMatch(d->d.severity().equals("ERROR")))return blocked(document,diagnostics);
        diagnostics.add(new TransformationExportDiagnostic("WARNING","NATIVE_COLLISION_POLICY_REQUIRES_ENGINE_GUARD","$.generationRules","The native template engine must preserve HYDRA's FAIL/REPLACE artifact collision policy during orchestration.","Keep parity tests enabled when integrating the generated template project."));
        return generated(document,source,projections,diagnostics);
    }
    /** Genera artefactos específicos una vez traducidos los tokens. */ /** Descripción: Crea o prepara el resultado de «generated» dentro de Projection. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; projections: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected abstract TransformationExportResult generated(TransformationDefinitionDocument document,MetaModel source,List<Projection> projections,List<TransformationExportDiagnostic> diagnostics);
    /** Convierte un token neutral en una expresión específica del adaptador. */ /** Descripción: Ejecuta la operación «attributeExpression» correspondiente a la responsabilidad de Projection. Entradas: attributeName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected abstract String attributeExpression(String attributeName);
    /** Bloquea exportación sin generar archivos. */ /** Descripción: Ejecuta la operación «blocked» correspondiente a la responsabilidad de Projection. Entradas: document: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected TransformationExportResult blocked(TransformationDefinitionDocument document,List<TransformationExportDiagnostic> diagnostics){return new TransformationExportResult(document==null?"":document.id(),language(),TransformationExportFidelity.SUBSET,false,diagnostics,List.of());}
    /** Diagnóstico bloqueante homogéneo. */ /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de Projection. Entradas: code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected TransformationExportDiagnostic error(String code,String path,String message,String solution){return new TransformationExportDiagnostic("ERROR",code,path,message,solution);}
    /** Normaliza identificadores técnicos. */ /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de Projection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ protected String identifier(String value){String normalized=(value==null?"":value).replaceAll("[^A-Za-z0-9_]","_");return normalized.isBlank()?"HydraGeneration":Character.isDigit(normalized.charAt(0))?"_"+normalized:normalized;}
    /** Traduce tokens attr:id y bloquea id/classId, que no son propiedades portables de EObjects. */ /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de Projection. Entradas: template: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String translate(String template,MetaClass context,String path,List<TransformationExportDiagnostic> diagnostics){
        Matcher matcher=TOKEN.matcher(template==null?"":template);StringBuffer out=new StringBuffer();
        while(matcher.find()){String token=matcher.group(1);String replacement;
            if(token.equals("id")||token.equals("classId")){diagnostics.add(error("CANONICAL_ID_TOKEN_NOT_NATIVE",path,"The token {{"+token+"}} is canonical runtime metadata and has no portable native model feature.","Map an explicit model attribute instead or use Java/Xtend canonical runtime export."));replacement="";}
            else{String attributeId=token.substring("attr:".length());MetaAttribute attribute=context.attributes().stream().filter(a->a.id().equals(attributeId)).findFirst().orElse(null);if(attribute==null){diagnostics.add(error("TEMPLATE_ATTRIBUTE_NOT_FOUND",path,"Template token references a missing context attribute: "+attributeId,"Select an existing context attribute."));replacement="";}else replacement=attributeExpression(attribute.name());}
            matcher.appendReplacement(out,Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);return out.toString();
    }
}

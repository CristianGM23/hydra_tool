package com.example.editor.transformation.export;

import java.util.ArrayList;
import java.util.List;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationValuePolicy;

/** Validación común del subconjunto M2M que ATL y ETL pueden proyectar sin aproximaciones. */
abstract class DeclarativeM2MExportSupport implements TransformationLanguageAdapter {
    record Projection(TransformationRule rule,MetaClass sourceClass,MetaClass targetClass,List<ResolvedBinding> bindings) {}
    record ResolvedBinding(TransformationBinding binding,MetaAttribute sourceAttribute,MetaAttribute targetAttribute) {}
    /** Descripción: Ejecuta la operación «supportedKinds» correspondiente a la responsabilidad de Projection. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public java.util.Set<TransformationKind> supportedKinds(){return java.util.Set.of(TransformationKind.M2M);}
    /** Comprueba el subconjunto declarativo y solo genera si no hay pérdida semántica. */ /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de Projection. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportResult export(TransformationDefinitionDocument document,MetaModel source,MetaModel target){
        List<TransformationExportDiagnostic> diagnostics=new ArrayList<>();List<Projection> projections=new ArrayList<>();
        if(document==null||document.kind()!=TransformationKind.M2M)diagnostics.add(error("UNSUPPORTED_KIND","$.kind","This adapter only exports M2M transformations.","Choose an M2M definition or another adapter."));
        if(document!=null&&document.directionality()==TransformationDirectionality.BIDIRECTIONAL)diagnostics.add(error("BIDIRECTIONAL_NOT_NATIVE","$.directionality","This native declarative adapter does not preserve HYDRA's complete forward/reverse correspondence contract.","Use the canonical runtime bundle or wait for a bidirectional-capable native adapter."));
        if(source==null||target==null)diagnostics.add(error("METAMODELS_REQUIRED","$.sources","Both source and target metamodels are required for native declarative export.","Select resolvable source and target metamodels."));
        if(!diagnostics.isEmpty())return blocked(document,diagnostics);
        for(int i=0;i<document.rules().size();i++){TransformationRule rule=document.rules().get(i);String path="$.rules["+i+"]";if(!rule.enabled())continue;
            if(rule.guard()!=null){diagnostics.add(error("GUARD_NOT_NATIVE",path+".guard","Guards are not yet projected by this native adapter.","Remove the guard or export through Java/Xtend canonical runtime."));continue;}
            if(rule.targets().size()!=1){diagnostics.add(error("MULTI_TARGET_NOT_NATIVE",path+".targets","Native export currently requires exactly one target per rule.","Split the rule or use the canonical runtime export."));continue;}
            MetaClass sourceClass=source.classes().stream().filter(c->c.id().equals(rule.sourceTypeId())).findFirst().orElse(null);
            MetaClass targetClass=target.classes().stream().filter(c->c.id().equals(rule.targets().get(0).typeId())).findFirst().orElse(null);
            if(sourceClass==null||targetClass==null){diagnostics.add(error("RULE_TYPE_NOT_FOUND",path,"A rule references a class that no longer exists.","Repair the rule before exporting."));continue;}
            List<ResolvedBinding> bindings=new ArrayList<>();
            for(int j=0;j<rule.bindings().size();j++){TransformationBinding binding=rule.bindings().get(j);String bindingPath=path+".bindings["+j+"]";
                if(binding.kind()!=TransformationBindingKind.DIRECT&&binding.kind()!=TransformationBindingKind.CONSTANT){diagnostics.add(error("BINDING_NOT_NATIVE",bindingPath,"Only direct and constant bindings are supported by the demonstrated native subset.","Use Java/Xtend canonical runtime or simplify the binding."));continue;}
                if(!binding.valuePolicy().equals(TransformationValuePolicy.defaults())){diagnostics.add(error("VALUE_POLICY_NOT_NATIVE",bindingPath+".valuePolicy","Conversions, enum maps, collection reducers and fallbacks are not projected natively yet.","Use the canonical runtime export to preserve this value policy."));continue;}
                MetaAttribute targetAttribute=targetClass.attributes().stream().filter(a->a.id().equals(binding.targetFeatureId())).findFirst().orElse(null);
                MetaAttribute sourceAttribute=binding.kind()==TransformationBindingKind.DIRECT?sourceClass.attributes().stream().filter(a->a.id().equals(binding.sourceFeatureId())).findFirst().orElse(null):null;
                if(targetAttribute==null||binding.kind()==TransformationBindingKind.DIRECT&&sourceAttribute==null){diagnostics.add(error("BINDING_FEATURE_NOT_FOUND",bindingPath,"A binding references a missing attribute.","Repair the attribute mapping before exporting."));continue;}
                if(sourceAttribute!=null&&(!sourceAttribute.type().equals(targetAttribute.type())||!sourceAttribute.multiplicity().equals(targetAttribute.multiplicity()))){diagnostics.add(error("DIRECT_BINDING_NOT_EXACT",bindingPath,"Native direct export requires identical type and multiplicity.","Declare the adaptation in HYDRA and use Java/Xtend canonical runtime."));continue;}
                if(binding.kind()==TransformationBindingKind.CONSTANT&&(targetAttribute.multiplicity()==null||targetAttribute.multiplicity().upperBound()==null||targetAttribute.multiplicity().upperBound()>1)){diagnostics.add(error("CONSTANT_COLLECTION_NOT_NATIVE",bindingPath,"Native constant export is limited to scalar target attributes.","Use the canonical runtime export for collection constants."));continue;}
                bindings.add(new ResolvedBinding(binding,sourceAttribute,targetAttribute));
            }
            projections.add(new Projection(rule,sourceClass,targetClass,bindings));
        }
        if(diagnostics.stream().anyMatch(d->d.severity().equals("ERROR")))return blocked(document,diagnostics);
        return generated(document,source,target,projections,diagnostics);
    }
    /** Genera la fuente específica una vez demostrada la compatibilidad. */ /** Descripción: Crea o prepara el resultado de «generated» dentro de Projection. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; projections: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected abstract TransformationExportResult generated(TransformationDefinitionDocument document,MetaModel source,MetaModel target,List<Projection> projections,List<TransformationExportDiagnostic> diagnostics);
    /** Devuelve un resultado sin artefactos ante cualquier incompatibilidad. */ /** Descripción: Ejecuta la operación «blocked» correspondiente a la responsabilidad de Projection. Entradas: document: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected TransformationExportResult blocked(TransformationDefinitionDocument document,List<TransformationExportDiagnostic> diagnostics){return new TransformationExportResult(document==null?"":document.id(),language(),TransformationExportFidelity.SUBSET,false,diagnostics,List.of());}
    /** Construye un diagnóstico bloqueante homogéneo. */ /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de Projection. Entradas: code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected TransformationExportDiagnostic error(String code,String path,String message,String solution){return new TransformationExportDiagnostic("ERROR",code,path,message,solution);}
    /** Convierte una constante neutral al literal declarativo común. */ /** Descripción: Ejecuta la operación «literal» correspondiente a la responsabilidad de Projection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ protected String literal(Object value){if(value==null)return "OclUndefined";if(value instanceof Number||value instanceof Boolean)return String.valueOf(value);return "'"+String.valueOf(value).replace("'","\\'")+"'";}
    /** Normaliza nombres técnicos para identificadores de reglas y módulos. */ /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de Projection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ protected String identifier(String value){String normalized=(value==null?"":value).replaceAll("[^A-Za-z0-9_]","_");return normalized.isBlank()?"HydraTransformation":Character.isDigit(normalized.charAt(0))?"_"+normalized:normalized;}
}

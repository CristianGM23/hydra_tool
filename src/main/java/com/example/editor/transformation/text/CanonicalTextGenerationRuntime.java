package com.example.editor.transformation.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.GenerationOverwritePolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.runtime.TransformationExecutionException;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

/** Runtime M2T seguro: evalúa guards y plantillas simples sin ejecutar código ni escribir en disco. */
@Service
public class CanonicalTextGenerationRuntime {
    private static final Pattern TOKEN=Pattern.compile("\\{\\{\\s*(id|classId|attr:[A-Za-z0-9][A-Za-z0-9._:-]{0,239})\\s*}}",Pattern.MULTILINE);
    private final ExpressionEvaluator expressions;

    /** Descripción: Inicializa una instancia de CanonicalTextGenerationRuntime con sus dependencias y datos de entrada. Entradas: expressions: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalTextGenerationRuntime(ExpressionEvaluator expressions){this.expressions=expressions;}

    /** Genera artefactos textuales en orden estable y aplica la política explícita de colisión. */
    /** Descripción: Crea o prepara el resultado de «generate» dentro de CanonicalTextGenerationRuntime. Entradas: document: dato de entrada necesario para la operación; sourceMetamodel: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationGenerationResult generate(TransformationDefinitionDocument document,MetaModel sourceMetamodel,CanonicalModelDocument sourceModel){
        if(document==null||document.kind()!=TransformationKind.M2T)throw new TransformationExecutionException("M2T transformation definition is required");
        if(sourceModel==null||!sourceModel.metamodelId().equals(sourceMetamodel.id()))throw new TransformationExecutionException("Source model does not conform to the M2T source metamodel");
        Map<String,TransformationTextArtifact> artifacts=new LinkedHashMap<>();
        for(CanonicalModelElement element:sourceModel.elements()){
            for(TransformationGenerationRule rule:document.generationRules()){
                if(!rule.enabled()||!element.classId().equals(rule.contextTypeId())||!matches(rule,sourceMetamodel,sourceModel,element))continue;
                String path=render(rule.outputPathTemplate(),element);
                validatePath(path);
                TransformationTextArtifact artifact=new TransformationTextArtifact(path,render(rule.contentTemplate(),element),rule.id(),element.id());
                if(artifacts.containsKey(path)&&rule.overwritePolicy()==GenerationOverwritePolicy.FAIL)throw new TransformationExecutionException("Generated artifact collision: "+path);
                artifacts.put(path,artifact);
            }
        }
        return new TransformationGenerationResult(document.id(),new ArrayList<>(artifacts.values()));
    }

    /** Evalúa el guard M2T en el contexto del elemento actual. */
    /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; element: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matches(TransformationGenerationRule rule,MetaModel metamodel,CanonicalModelDocument model,CanonicalModelElement element){
        if(rule.guard()==null)return true;
        Object value=expressions.evaluate(rule.guard(),new ExpressionEvaluationContext(metamodel,model,element.id(),Map.of())).value();
        if(value instanceof Boolean result)return result;
        throw new TransformationExecutionException("Generation guard did not evaluate to Boolean: "+rule.id());
    }

    /** Renderiza únicamente tokens neutrales de datos; no interpreta código de usuario. */
    /** Descripción: Ejecuta la operación «render» correspondiente a la responsabilidad de CanonicalTextGenerationRuntime. Entradas: template: dato de entrada necesario para la operación; element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String render(String template,CanonicalModelElement element){
        Matcher matcher=TOKEN.matcher(template==null?"":template);
        StringBuffer out=new StringBuffer();
        while(matcher.find()){
            String token=matcher.group(1);
            Object value=token.equals("id")?element.id():token.equals("classId")?element.classId():element.attributes().get(token.substring("attr:".length()));
            matcher.appendReplacement(out,Matcher.quoteReplacement(value==null?"":String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Rechaza rutas absolutas, padres y formas no portables antes de publicar artefactos. */
    /** Descripción: Valida las condiciones necesarias para «validatePath» y rechaza entradas no válidas. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validatePath(String path){
        String normalized=path==null?"":path.trim().replace('\\','/');
        if(normalized.isBlank()||normalized.startsWith("/")||normalized.contains("../")||normalized.equals("..")||normalized.matches("^[A-Za-z]:.*"))
            throw new TransformationExecutionException("Generated artifact path must be relative and portable: "+path);
    }
}

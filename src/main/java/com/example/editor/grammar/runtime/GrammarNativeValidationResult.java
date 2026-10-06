package com.example.editor.grammar.runtime;
import java.util.List;import java.util.Map;
/** Native build evidence plus bounded files captured before the isolated workspace is destroyed. */
public record GrammarNativeValidationResult(boolean attempted,boolean successful,List<GrammarNativeValidationStep>steps,Map<String,String>metadata,Map<String,String>runtimeFiles,Map<String,String>reproducibilityFiles){
    public GrammarNativeValidationResult{steps=steps==null?List.of():List.copyOf(steps);metadata=metadata==null?Map.of():Map.copyOf(metadata);runtimeFiles=runtimeFiles==null?Map.of():Map.copyOf(runtimeFiles);reproducibilityFiles=reproducibilityFiles==null?Map.of():Map.copyOf(reproducibilityFiles);}
    /** Compatibility constructor for validation results created before native file capture. */
    /** Descripción: Inicializa una instancia de GrammarNativeValidationResult con sus dependencias y datos de entrada. Entradas: attempted: dato de entrada necesario para la operación; successful: dato de entrada necesario para la operación; steps: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarNativeValidationResult(boolean attempted,boolean successful,List<GrammarNativeValidationStep>steps,Map<String,String>metadata){this(attempted,successful,steps,metadata,Map.of(),Map.of());}

    /** A missing/busy external toolchain or unavailable dependency source is an environment limitation, not evidence that the generated project is invalid. */
    /** Descripción: Ejecuta la operación «verificationDeferred» correspondiente a la responsabilidad de GrammarNativeValidationResult. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean verificationDeferred(){
        if(successful||steps.isEmpty())return false;
        boolean deferred=false;
        for(GrammarNativeValidationStep step:steps){
            String status=step.status();
            if("TOOL_UNAVAILABLE".equals(status)||"SKIPPED".equals(status)||"TIMED_OUT".equals(status)){deferred=true;continue;}
            if("FAILED".equals(status)&&(dependencyAcquisitionStep(step.id())||externalDependencyFailure(step))){deferred=true;continue;}
            if(!"PASSED".equals(status))return false;
        }
        return deferred;
    }

    /** Descripción: Ejecuta la operación «dependencyAcquisitionStep» correspondiente a la responsabilidad de GrammarNativeValidationResult. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean dependencyAcquisitionStep(String id){
        String value=id==null?"":id.toLowerCase(java.util.Locale.ROOT);
        return value.endsWith("-dependencies")||value.contains("dependency-install");
    }

    /** Descripción: Ejecuta la operación «externalDependencyFailure» correspondiente a la responsabilidad de GrammarNativeValidationResult. Entradas: step: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean externalDependencyFailure(GrammarNativeValidationStep step){
        String text=String.join("\n",step.diagnostics()).toLowerCase(java.util.Locale.ROOT);
        return text.contains("enotfound")||text.contains("eai_again")||text.contains("econnrefused")
                ||text.contains("etimedout")||text.contains("network is unreachable")||text.contains("temporary failure in name resolution")
                ||text.contains("unable to get local issuer certificate")||text.contains("pkix path building failed")
                ||text.contains("could not transfer artifact")||text.contains("failed to read artifact descriptor")
                ||text.contains("etarget")||text.contains("no matching version found");
    }

    /** True only when native evidence proves a build/test/capture failure rather than an unavailable verifier. */
    /** Descripción: Ejecuta la operación «definitiveFailure» correspondiente a la responsabilidad de GrammarNativeValidationResult. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean definitiveFailure(){return !successful&&!verificationDeferred();}
}

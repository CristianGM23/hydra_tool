package com.example.editor.grammar.core;
import java.util.Map;
public record GrammarToolchainProfile(String id, GrammarTechnology technology, String providerVersion, String runtimeVersion, String javaVersion, String nodeVersion, String buildSystem, String targetLanguage, boolean warningsAsErrors, long analysisTimeoutMs, long compileTimeoutMs, long maximumMemoryBytes, GrammarExecutionMode executionMode, Map<String,String> settings) {
    public GrammarToolchainProfile {
        id=text(id); technology=technology==null?GrammarTechnology.LANGIUM:technology; providerVersion=text(providerVersion); runtimeVersion=text(runtimeVersion); javaVersion=text(javaVersion); nodeVersion=text(nodeVersion); buildSystem=text(buildSystem); targetLanguage=text(targetLanguage);
        analysisTimeoutMs=Math.max(250,analysisTimeoutMs); compileTimeoutMs=Math.max(1000,compileTimeoutMs); maximumMemoryBytes=Math.max(16L*1024L*1024L,maximumMemoryBytes); executionMode=executionMode==null?GrammarExecutionMode.INTERNAL:executionMode; settings=settings==null?Map.of():Map.copyOf(settings);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarToolchainProfile. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}

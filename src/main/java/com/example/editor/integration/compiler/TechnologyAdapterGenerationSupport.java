package com.example.editor.integration.compiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.example.editor.integration.IntegrationFeature;

/** Utilidades compartidas por adaptadores generadores de proyectos externos. */
public final class TechnologyAdapterGenerationSupport {
    /** Descripción: Inicializa una instancia de TechnologyAdapterGenerationSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TechnologyAdapterGenerationSupport(){ }

    /** Descripción: Ejecuta la operación «sourceIds» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<String> sourceIds(TechnologyCompilationRequest request){
        return request.artifacts().stream().map(value -> value.documentId()).distinct().toList();
    }

    /** Descripción: Evalúa la condición «canonicalDocuments» sin modificar el estado externo. Entradas: root: ruta o ubicación utilizada por la operación; directory: ruta o ubicación utilizada por la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static List<TechnologyGeneratedFile> canonicalDocuments(String root, String directory,
            TechnologyCompilationRequest request){
        List<TechnologyGeneratedFile> files = new ArrayList<>();
        for(var artifact : request.artifacts()){
            files.add(TechnologyAdapterSupport.binary(
                    TechnologyAdapterSupport.join(root, directory, artifact.kind().name().toLowerCase(Locale.ROOT) + ".json"),
                    artifact.mediaType(), artifact.content(), List.of(artifact.documentId())));
        }
        files.add(TechnologyAdapterSupport.text(
                TechnologyAdapterSupport.join(root, directory, "runtime-activation.json"),
                "application/json", runtimeActivationManifest(request), sourceIds(request)));
        return files;
    }

    /** Returns whether this generated package contains runtime behaviour rather than projection-only artifacts. */
    /** Descripción: Evalúa la condición «hasRuntimeFeatures» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean hasRuntimeFeatures(TechnologyCompilationRequest request){
        return request.profile().features().stream().anyMatch(IntegrationFeature::runtimeFeature);
    }

    /** Safe-by-default activation contract shared by every generated target adapter. */
    /** Descripción: Procesa la operación «runtimeActivationManifest» coordinando las responsabilidades de TechnologyAdapterGenerationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String runtimeActivationManifest(TechnologyCompilationRequest request){
        String requestedMode = request.profile().annotations().getOrDefault("hydra.runtime.activation", "TOGGLEABLE").trim().toUpperCase(Locale.ROOT);
        String mode = hasRuntimeFeatures(request)
                ? switch(requestedMode){case "ALWAYS_ON", "DISABLED", "TOGGLEABLE" -> requestedMode; default -> "TOGGLEABLE";}
                : "DISABLED";
        boolean enabledByDefault = mode.equals("ALWAYS_ON") || (mode.equals("TOGGLEABLE")
                && Boolean.parseBoolean(request.profile().annotations().getOrDefault("hydra.runtime.enabledByDefault", "false")));
        String features = request.profile().features().stream().filter(IntegrationFeature::runtimeFeature)
                .sorted(Comparator.comparing(Enum::name)).map(feature -> jsonString(feature.name()))
                .reduce((left,right) -> left + "," + right).orElse("");
        return "{\n"
                + "  \"schemaVersion\": \"1.0\",\n"
                + "  \"profileId\": " + jsonString(request.profile().id()) + ",\n"
                + "  \"metamodelId\": " + jsonString(request.profile().metamodelId()) + ",\n"
                + "  \"semanticScopeId\": " + jsonString(request.profile().semanticScopeId()) + ",\n"
                + "  \"activationMode\": " + jsonString(mode) + ",\n"
                + "  \"enabledByDefault\": " + enabledByDefault + ",\n"
                + "  \"runtimeFeatures\": [" + features + "]\n"
                + "}\n";
    }

    /** Java activation gate used by EMF/Sirius/Xtext/Java GLSP hosts. */
    /** Descripción: Ejecuta la operación «javaRuntimeActivationSource» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: packageName: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String javaRuntimeActivationSource(String packageName, TechnologyCompilationRequest request){
        return "package " + packageName + ";\n\n"
                + "import java.util.concurrent.atomic.AtomicBoolean;\nimport java.util.concurrent.atomic.AtomicLong;\n\n"
                + "/** Runtime gate generated by HYDRA. Disabled by default unless the integration profile explicitly opts in. */\n"
                + "public final class HydraRuntimeActivation {\n"
                + "    public static final String METAMODEL_ID = " + javaString(request.profile().metamodelId()) + ";\n"
                + "    public static final String SEMANTIC_SCOPE_ID = " + javaString(request.profile().semanticScopeId()) + ";\n"
                + "    private static final AtomicBoolean ENABLED = new AtomicBoolean(" + activationEnabledByDefault(request) + ");\n"
                + "    private static final AtomicLong EPOCH = new AtomicLong();\n"
                + "    private HydraRuntimeActivation() {}\n"
                + "    public static boolean isEnabled(){ return ENABLED.get(); }\n"
                + "    public static boolean isEnabledFor(String semanticScopeId){ return ENABLED.get() && SEMANTIC_SCOPE_ID.equals(semanticScopeId); }\n"
                + "    public static long epoch(){ return EPOCH.get(); }\n"
                + "    public static synchronized boolean setEnabled(boolean enabled){ boolean previous = ENABLED.getAndSet(enabled); if(previous != enabled) EPOCH.incrementAndGet(); return enabled; }\n"
                + "    public static synchronized boolean toggle(){ return setEnabled(!ENABLED.get()); }\n"
                + "}\n";
    }

    /** TypeScript activation gate used by Langium, Node GLSP and web editor packages. */
    /** Descripción: Ejecuta la operación «typescriptRuntimeActivationSource» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String typescriptRuntimeActivationSource(TechnologyCompilationRequest request){
        return "export const HYDRA_METAMODEL_ID=" + jsonString(request.profile().metamodelId()) + ";\n"
                + "export const HYDRA_SEMANTIC_SCOPE_ID=" + jsonString(request.profile().semanticScopeId()) + ";\n"
                + "let enabled=" + activationEnabledByDefault(request) + ";\n"
                + "let epoch=0;\n"
                + "export const hydraRuntimeActivation={\n"
                + "  isEnabled:()=>enabled,\n"
                + "  isEnabledFor:(semanticScopeId:string)=>enabled&&semanticScopeId===HYDRA_SEMANTIC_SCOPE_ID,\n"
                + "  epoch:()=>epoch,\n"
                + "  setEnabled:(value:boolean)=>{if(enabled!==value){enabled=value;epoch++;}return enabled;},\n"
                + "  toggle:()=>hydraRuntimeActivation.setEnabled(!enabled)\n"
                + "} as const;\n";
    }

    /** Python activation gate used by generated BESSER SDK packages. */
    /** Descripción: Ejecuta la operación «pythonRuntimeActivationSource» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String pythonRuntimeActivationSource(TechnologyCompilationRequest request){
        return "METAMODEL_ID = " + pythonString(request.profile().metamodelId()) + "\n"
                + "SEMANTIC_SCOPE_ID = " + pythonString(request.profile().semanticScopeId()) + "\n"
                + "_enabled = " + (activationEnabledByDefault(request) ? "True" : "False") + "\n"
                + "_epoch = 0\n\n"
                + "def is_enabled() -> bool:\n    return _enabled\n\n"
                + "def is_enabled_for(semantic_scope_id: str) -> bool:\n    return _enabled and semantic_scope_id == SEMANTIC_SCOPE_ID\n\n"
                + "def epoch() -> int:\n    return _epoch\n\n"
                + "def set_enabled(value: bool) -> bool:\n    global _enabled, _epoch\n    next_value = bool(value)\n    if _enabled != next_value:\n        _enabled = next_value\n        _epoch += 1\n    return _enabled\n\n"
                + "def toggle() -> bool:\n    return set_enabled(not _enabled)\n";
    }

    /** Descripción: Ejecuta la operación «activationEnabledByDefault» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean activationEnabledByDefault(TechnologyCompilationRequest request){
        if(!hasRuntimeFeatures(request)) return false;
        String mode = request.profile().annotations().getOrDefault("hydra.runtime.activation", "TOGGLEABLE").trim().toUpperCase(Locale.ROOT);
        if(mode.equals("DISABLED")) return false;
        if(mode.equals("ALWAYS_ON")) return true;
        return Boolean.parseBoolean(request.profile().annotations().getOrDefault("hydra.runtime.enabledByDefault", "false"));
    }

    /** Descripción: Ejecuta la operación «jsonString» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String jsonString(String value){
        String escaped = String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
    /** Descripción: Ejecuta la operación «javaString» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String javaString(String value){ return jsonString(value); }
    /** Descripción: Ejecuta la operación «pythonString» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String pythonString(String value){ return jsonString(value); }

    /** Descripción: Ejecuta la operación «typescriptIdentifier» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String typescriptIdentifier(String value, String fallback){
        String type = TechnologyAdapterSupport.javaType(value, fallback);
        return Character.toLowerCase(type.charAt(0)) + type.substring(1);
    }

    /** Descripción: Ejecuta la operación «npmPackage» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String npmPackage(String value, String fallback){
        String source = value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
        String result = source.replaceAll("[^a-z0-9@/._-]+", "-").replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
        return result.isBlank() ? fallback : result;
    }

    /** Descripción: Ejecuta la operación «pythonModule» correspondiente a la responsabilidad de TechnologyAdapterGenerationSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String pythonModule(String value, String fallback){
        String source = value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
        String result = source.replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_")
                .replaceAll("(^_|_$)", "");
        if(result.isBlank()){result = fallback;}
        if(Character.isDigit(result.charAt(0))){result = "m_" + result;}
        return result;
    }
}

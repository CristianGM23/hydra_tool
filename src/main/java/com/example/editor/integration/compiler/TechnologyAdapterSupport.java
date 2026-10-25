package com.example.editor.integration.compiler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.projection.CanonicalArtifactContent;

/** Funciones deterministas compartidas por los generadores EMF, Sirius y futuros adaptadores. */
public final class TechnologyAdapterSupport {
    /** Fragmento package.json para los proyectos generados con vitest 4: npm 10 aborta («Cannot read properties of null (reading 'edgesOut')»)
     * al resolver el peer opcional @vitejs/devtools de vite 8, cuyo peer vitest@* arrastra vitest 5. vitest 4 admite vite 6, 7 y 8, así que se fija la línea 7. */
    public static final String VITEST_VITE_OVERRIDE = "\"overrides\":{\"vite\":\"7.3.7\"}";
    /** Descripción: Inicializa una instancia de TechnologyAdapterSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TechnologyAdapterSupport(){ }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: path: ruta o ubicación utilizada por la operación; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; sources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static TechnologyGeneratedFile text(String path, String mediaType, String content, List<String> sources){
        return new TechnologyGeneratedFile(path, mediaType, content.getBytes(StandardCharsets.UTF_8),
                InjectionFileOwnership.GENERATED, true, sources, Map.of());
    }

    /** Descripción: Ejecuta la operación «binary» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: path: ruta o ubicación utilizada por la operación; mediaType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; sources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static TechnologyGeneratedFile binary(String path, String mediaType, byte[] content, List<String> sources){
        return new TechnologyGeneratedFile(path, mediaType, content, InjectionFileOwnership.GENERATED, true, sources, Map.of());
    }

    /** Descripción: Crea o prepara el resultado de «generatedRoot» dentro de TechnologyAdapterSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; defaultModule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String generatedRoot(TechnologyCompilationRequest request, String defaultModule){
        String root = request.profile().injectionPolicy().generatedRoot();
        String module = request.profile().injectionPolicy().moduleName();
        if(module.isBlank()){module = request.profile().target().moduleName();}
        if(module.isBlank()){module = defaultModule;}
        return join(root, slug(module));
    }

    /** Descripción: Ejecuta la operación «join» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: parts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String join(String... parts){
        List<String> values = new ArrayList<>();
        for(String part : parts){
            if(part == null){continue;}
            String normalized = part.replace('\\', '/').replaceAll("^/+|/+$", "");
            if(!normalized.isBlank()){values.add(normalized);}
        }
        return String.join("/", values);
    }

    /** Descripción: Ejecuta la operación «slug» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String slug(String value){
        String result = value == null ? "generated" : value.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return result.isBlank() ? "generated" : result;
    }

    /** Descripción: Ejecuta la operación «javaPackage» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String javaPackage(String value, String fallback){
        String source = value == null || value.isBlank() ? fallback : value;
        String[] parts = source.toLowerCase().replace('-', '.').split("\\.");
        List<String> normalized = new ArrayList<>();
        for(String part : parts){
            String token = part.replaceAll("[^a-z0-9_]", "");
            if(token.isBlank()){continue;}
            if(Character.isDigit(token.charAt(0))){token = "p" + token;}
            normalized.add(token);
        }
        return normalized.isEmpty() ? "com.generated.hydra" : String.join(".", normalized);
    }

    /** Descripción: Ejecuta la operación «javaPath» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String javaPath(String packageName){return packageName.replace('.', '/');}

    /** Descripción: Ejecuta la operación «javaType» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String javaType(String value, String fallback){
        String source = value == null || value.isBlank() ? fallback : value;
        String[] parts = source.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for(String part : parts){if(!part.isBlank()){result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));}}
        if(result.isEmpty()){result.append(fallback);}
        if(Character.isDigit(result.charAt(0))){result.insert(0, 'T');}
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String xml(String value){
        return String.valueOf(value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** Descripción: Ejecuta la operación «unsupportedDiagnostics» correspondiente a la responsabilidad de TechnologyAdapterSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; supported: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<IntegrationDiagnostic> unsupportedDiagnostics(TechnologyCompilationRequest request,
            Set<IntegrationArtifactKind> supported, String technology){
        return request.artifacts().stream().filter(value -> !supported.contains(value.kind()))
                .map(value -> TechnologyCompilerService.unsupported(value.kind(), value.documentId(), technology)).toList();
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static CanonicalArtifactContent require(TechnologyCompilationRequest request, IntegrationArtifactKind kind){return request.require(kind);}
}

package com.example.editor.metamodel.importing.canonicalization;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadatos transitorios usados exclusivamente durante la importación Ecore.
 * El canonicalizador los convierte a TargetConfigurationDocument y los elimina
 * antes de persistir el metamodelo neutral.
 */
public final class EcoreImportMetadata {

    public static final String PREFIX = "import:ecore:";
    public static final String ARTIFACT_NAME = PREFIX + "artifactName";
    public static final String NAMESPACE_URI = PREFIX + "namespaceUri";
    public static final String NAMESPACE_PREFIX = PREFIX + "namespacePrefix";
    public static final String INSTANCE_CLASS = PREFIX + "instanceClassName";
    public static final String SERIALIZABLE = PREFIX + "serializable";
    public static final String CHANGEABLE = PREFIX + "changeable";
    public static final String RESOLVE_PROXIES = PREFIX + "resolveProxies";

    /** Descripción: Inicializa una instancia de EcoreImportMetadata con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private EcoreImportMetadata() {
    }

    /** Descripción: Ejecuta la operación «model» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: source: dato de entrada necesario para la operación; artifactName: dato de entrada necesario para la operación; namespaceUri: dato de entrada necesario para la operación; namespacePrefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, String> model(Map<String, String> source, String artifactName, String namespaceUri, String namespacePrefix) {
        Map<String, String> result = copy(source);
        putText(result, ARTIFACT_NAME, artifactName);
        putText(result, NAMESPACE_URI, namespaceUri);
        putText(result, NAMESPACE_PREFIX, namespacePrefix);
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «dataType» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: source: dato de entrada necesario para la operación; implementationTypeName: dato de entrada necesario para la operación; serializable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, String> dataType(Map<String, String> source, String implementationTypeName, boolean serializable) {
        Map<String, String> result = copy(source);
        putText(result, INSTANCE_CLASS, implementationTypeName);
        result.put(SERIALIZABLE, Boolean.toString(serializable));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «attribute» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: source: dato de entrada necesario para la operación; changeable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, String> attribute(Map<String, String> source, boolean changeable) {
        Map<String, String> result = copy(source);
        result.put(CHANGEABLE, Boolean.toString(changeable));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «relation» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: source: dato de entrada necesario para la operación; changeable: dato de entrada necesario para la operación; resolveProxies: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, String> relation(Map<String, String> source, boolean changeable, boolean resolveProxies) {
        Map<String, String> result = copy(source);
        result.put(CHANGEABLE, Boolean.toString(changeable));
        result.put(RESOLVE_PROXIES, Boolean.toString(resolveProxies));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: annotations: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String text(Map<String, String> annotations, String key, String fallback) {
        String value = annotations == null ? null : annotations.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Descripción: Ejecuta la operación «bool» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: annotations: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; fallback: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean bool(Map<String, String> annotations, String key, boolean fallback) {
        String value = annotations == null ? null : annotations.get(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> copy(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    /** Descripción: Ejecuta la operación «putText» correspondiente a la responsabilidad de EcoreImportMetadata. Entradas: target: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void putText(Map<String, String> target, String key, String value) {
        if(value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}

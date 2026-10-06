package com.example.editor.scope;

import java.nio.file.Path;

/**
 * Scope neutral de un recurso canónico; define aislamiento e identidad.
 * no implica usuarios, permisos ni una base de datos remota.
 */
public record ResourceScope(String workspaceId) {
    public static final String LOCAL_WORKSPACE_ID = "local";

    public ResourceScope {
        workspaceId = normalize(workspaceId);
    }

    /** Descripción: Ejecuta la operación «local» correspondiente a la responsabilidad de ResourceScope. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ResourceScope local(){return new ResourceScope(LOCAL_WORKSPACE_ID);}
    /** Descripción: Evalúa la condición «isLocal» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isLocal(){return LOCAL_WORKSPACE_ID.equals(workspaceId);}

    /** Clave preparada para stores remotos: workspace + namespace + id. */
    /** Descripción: Ejecuta la operación «resourceKey» correspondiente a la responsabilidad de ResourceScope. Entradas: namespace: dato de entrada necesario para la operación; resourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String resourceKey(String namespace, String resourceId){
        return workspaceId + "/" + requireToken(namespace, "namespace") + "/" + requireToken(resourceId, "resourceId");
    }

    /** Mantiene el almacenamiento local y aísla scopes explícitos bajo workspaces/. */
    /** Descripción: Ejecuta la operación «localDirectory» correspondiente a la responsabilidad de ResourceScope. Entradas: baseDirectory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Path localDirectory(Path baseDirectory){
        if(baseDirectory == null){throw new IllegalArgumentException("baseDirectory is required");}
        Path normalized = baseDirectory.toAbsolutePath().normalize();
        return isLocal() ? normalized : normalized.resolve("workspaces").resolve(workspaceId).normalize();
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de ResourceScope. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value){return requireToken(value == null || value.isBlank() ? LOCAL_WORKSPACE_ID : value.trim(), "workspaceId");}
    /** Descripción: Valida las condiciones necesarias para «requireToken» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireToken(String value, String field){
        if(value == null || value.isBlank() || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")){
            throw new IllegalArgumentException(field + " is invalid: " + value);
        }
        return value;
    }
}

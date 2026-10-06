package com.example.editor.remote.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Normalización común contra traversal y codificación por segmento de rutas remotas. */
public final class RemotePathPolicy {
    /** Descripción: Inicializa una instancia de RemotePathPolicy con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemotePathPolicy(){}
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de RemotePathPolicy. Entradas: root: ruta o ubicación utilizada por la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String normalize(String root,String requested) {
        String normalized=normalizeSegments(requested,true);
        String base=normalizeSegments(root,false);
        return base.isBlank()?normalized:base+"/"+normalized;
    }
    /** Descripción: Ejecuta la operación «encodePath» correspondiente a la responsabilidad de RemotePathPolicy. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String encodePath(String path) {
        return Arrays.stream(path.split("/",-1)).map(RemotePathPolicy::encodeSegment).collect(Collectors.joining("/"));
    }
    /** Descripción: Ejecuta la operación «encodeSegment» correspondiente a la responsabilidad de RemotePathPolicy. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String encodeSegment(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20"); }

    /** Descripción: Ejecuta la operación «normalizeSegments» correspondiente a la responsabilidad de RemotePathPolicy. Entradas: value: valor de entrada que debe procesarse; required: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeSegments(String value,boolean required) {
        String path=(value==null?"":value.trim()).replace('\\','/');
        while(path.startsWith("/")) path=path.substring(1);
        while(path.endsWith("/")) path=path.substring(0,path.length()-1);
        if(path.isBlank()) {
            if(required) throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"Remote path is required");
            return "";
        }
        return Arrays.stream(path.split("/+"))
                .filter(part->!part.isBlank())
                .peek(part->{if(part.equals(".")||part.equals(".."))throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"Remote path cannot contain relative traversal segments");})
                .collect(Collectors.joining("/"));
    }
}

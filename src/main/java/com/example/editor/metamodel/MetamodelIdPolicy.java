package com.example.editor.metamodel;

import java.util.regex.Pattern;

/** Política canónica compartida para identificadores de metamodelo persistentes. */
/** Descripción: Centraliza la validación portable de identificadores persistentes de metamodelo. */
public final class MetamodelIdPolicy {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,119}");
    /** Descripción: Impide instanciar la política estática de identificadores. */
    /** Descripción: Inicializa una instancia de MetamodelIdPolicy con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private MetamodelIdPolicy() {}
    /** Descripción: Comprueba si el identificador cumple el formato canónico compartido. */
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de MetamodelIdPolicy. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean valid(String id) { return id != null && SAFE_ID.matcher(id).matches(); }
    /** Descripción: Valida el identificador y lo devuelve, o comunica un error preciso si no es portable. */
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static String require(String id) {
        if(!valid(id)) throw new IllegalArgumentException("Metamodel id must match [A-Za-z0-9][A-Za-z0-9._-]{0,119}");
        return id;
    }
}

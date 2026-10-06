package com.example.editor.validation.contract;

/**
 * Fachada compatible con el contrato histórico de validación.
 * La política canónica vive en {@code com.example.editor.contract.StableIdentifierPolicy}.
 */
@Deprecated(forRemoval = false)
public final class StableIdentifierPolicy {
    /** Descripción: Impide crear instancias de la fachada de compatibilidad. Entradas: ninguna. Salida: no crea instancias públicas. */
    /** Descripción: Inicializa una instancia de StableIdentifierPolicy con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private StableIdentifierPolicy() {}

    /** Descripción: Comprueba un identificador estable de documento delegando en la política neutral. Entradas: value. Salida: true cuando es válido. */
    /** Descripción: Evalúa la condición «isValidDocumentId» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean isValidDocumentId(String value) { return com.example.editor.contract.StableIdentifierPolicy.isValidDocumentId(value); }
    /** Descripción: Comprueba un identificador estable de elemento delegando en la política neutral. Entradas: value. Salida: true cuando es válido. */
    /** Descripción: Evalúa la condición «isValidElementId» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean isValidElementId(String value) { return com.example.editor.contract.StableIdentifierPolicy.isValidElementId(value); }
    /** Descripción: Exige un identificador estable de documento usando la política neutral. Entradas: value y fieldName. Salida: valor validado. */
    /** Descripción: Valida las condiciones necesarias para «requireDocumentId» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; fieldName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String requireDocumentId(String value, String fieldName) { return com.example.editor.contract.StableIdentifierPolicy.requireDocumentId(value, fieldName); }
    /** Descripción: Exige un identificador estable de elemento usando la política neutral. Entradas: value y fieldName. Salida: valor validado. */
    /** Descripción: Valida las condiciones necesarias para «requireElementId» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; fieldName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String requireElementId(String value, String fieldName) { return com.example.editor.contract.StableIdentifierPolicy.requireElementId(value, fieldName); }
}

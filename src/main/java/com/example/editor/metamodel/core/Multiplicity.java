package com.example.editor.metamodel.core;

/**
 * Representa una multiplicidad neutral.
 *
 * @param lowerBound contiene el límite inferior.
 * @param upperBound contiene el límite superior o null cuando no tiene límite.
 */
public record Multiplicity(int lowerBound, Integer upperBound) {

    /**
     * Descripción:
     * Crea una multiplicidad 0..1.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la multiplicidad.
     */
    /** Descripción: Ejecuta la operación «optional» correspondiente a la responsabilidad de Multiplicity. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static Multiplicity optional() {
        return new Multiplicity(0, 1);
    }

    /**
     * Descripción:
     * Crea una multiplicidad 1..1.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la multiplicidad.
     */
    /** Descripción: Ejecuta la operación «one» correspondiente a la responsabilidad de Multiplicity. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static Multiplicity one() {
        return new Multiplicity(1, 1);
    }

    /**
     * Descripción:
     * Crea una multiplicidad 0..*.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la multiplicidad.
     */
    /** Descripción: Ejecuta la operación «many» correspondiente a la responsabilidad de Multiplicity. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static Multiplicity many() {
        return new Multiplicity(0, null);
    }

    /**
     * Descripción:
     * Indica si el límite superior es ilimitado.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return true cuando no existe límite superior.
     */
    /** Descripción: Ejecuta la operación «unbounded» correspondiente a la responsabilidad de Multiplicity. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean unbounded() {
        return upperBound == null;
    }
}

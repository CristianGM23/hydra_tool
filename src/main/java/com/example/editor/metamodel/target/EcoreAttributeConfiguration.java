package com.example.editor.metamodel.target;

/**
 * Opciones Ecore que pertenecen a un atributo concreto.
 */
public record EcoreAttributeConfiguration(boolean changeable, boolean transientFeature, boolean volatileFeature, boolean derived, boolean unsettable, boolean id) {

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de EcoreAttributeConfiguration. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static EcoreAttributeConfiguration defaults() {
        return new EcoreAttributeConfiguration(true, false, false, false, false, false);
    }
}

package com.example.editor.metamodel.target;

/**
 * Opciones Ecore que pertenecen a una relación estructural concreta.
 */
public record EcoreRelationConfiguration(boolean changeable, boolean resolveProxies, boolean transientFeature, boolean volatileFeature, boolean derived, boolean unsettable) {

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de EcoreRelationConfiguration. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static EcoreRelationConfiguration defaults() {
        return new EcoreRelationConfiguration(true, true, false, false, false, false);
    }

    /** Descripción: Ejecuta la operación «containmentDefaults» correspondiente a la responsabilidad de EcoreRelationConfiguration. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static EcoreRelationConfiguration containmentDefaults() {
        return new EcoreRelationConfiguration(true, false, false, false, false, false);
    }
}

package com.example.editor.metamodel.target;

/**
 * Opciones Ecore de un tipo de datos personalizado.
 */
public record EcoreDataTypeConfiguration(String instanceClassName, boolean serializable) {

    public EcoreDataTypeConfiguration {
        if (instanceClassName == null) {
            instanceClassName = "";
        }
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de EcoreDataTypeConfiguration. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static EcoreDataTypeConfiguration defaults() {
        return new EcoreDataTypeConfiguration("", true);
    }
}

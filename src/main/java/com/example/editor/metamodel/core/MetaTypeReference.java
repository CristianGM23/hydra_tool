package com.example.editor.metamodel.core;

/**
 * Representa una referencia de tipo independiente de Ecore, UML o B-UML.
 *
 * @param kind contiene la categoría.
 * @param targetId identifica el tipo neutral.
 * @param externalName conserva el nombre original cuando no existe equivalencia neutral.
 */
public record MetaTypeReference(MetaTypeReferenceKind kind, String targetId, String externalName) {

    /**
     * Descripción:
     * Crea una referencia a un tipo primitivo.
     *
     * Entrada:
     * @param primitive contiene el tipo primitivo.
     *
     * Salida:
     * @return la referencia creada.
     */
    /** Descripción: Ejecuta la operación «primitive» correspondiente a la responsabilidad de MetaTypeReference. Entradas: primitive: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaTypeReference primitive(MetaPrimitiveType primitive) {
        return new MetaTypeReference(MetaTypeReferenceKind.PRIMITIVE, primitive.name(), "");
    }

    /**
     * Descripción:
     * Crea una referencia a una enumeración.
     *
     * Entrada:
     * @param enumId identifica la enumeración.
     *
     * Salida:
     * @return la referencia creada.
     */
    /** Descripción: Ejecuta la operación «enumType» correspondiente a la responsabilidad de MetaTypeReference. Entradas: enumId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaTypeReference enumType(String enumId) {
        return new MetaTypeReference(MetaTypeReferenceKind.ENUM, enumId, "");
    }

    /**
     * Descripción:
     * Crea una referencia a un tipo de datos.
     *
     * Entrada:
     * @param dataTypeId identifica el tipo.
     *
     * Salida:
     * @return la referencia creada.
     */
    /** Descripción: Ejecuta la operación «dataType» correspondiente a la responsabilidad de MetaTypeReference. Entradas: dataTypeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaTypeReference dataType(String dataTypeId) {
        return new MetaTypeReference(MetaTypeReferenceKind.DATATYPE, dataTypeId, "");
    }

    /**
     * Crea una referencia a una clase neutral. Esta categoría se utiliza en
     * firmas de operaciones y parámetros, mientras que las relaciones
     * estructurales continúan representándose mediante MetaRelation.
     *
     * @param classId identifica la clase.
     * @return referencia neutral a la clase.
     */
    /** Descripción: Ejecuta la operación «classType» correspondiente a la responsabilidad de MetaTypeReference. Entradas: classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaTypeReference classType(String classId) {
        return new MetaTypeReference(MetaTypeReferenceKind.CLASS, classId, "");
    }

    /**
     * Descripción:
     * Conserva un tipo externo sin perder información.
     *
     * Entrada:
     * @param externalName contiene el identificador original.
     *
     * Salida:
     * @return la referencia creada.
     */
    /** Descripción: Ejecuta la operación «external» correspondiente a la responsabilidad de MetaTypeReference. Entradas: externalName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaTypeReference external(String externalName) {
        String safeName = externalName;

        if (safeName == null) {
            safeName = "";
        }

        return new MetaTypeReference(MetaTypeReferenceKind.EXTERNAL, "", safeName);
    }
}

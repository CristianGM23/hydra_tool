package com.example.editor.metamodel.registry;

import com.example.editor.metamodel.MetamodelDescriptor;

/**
 * Representa un metamodelo almacenado y disponible dentro del registro.
 *
 * Contiene su identificador, el nombre del archivo original y la descripción
 * neutral obtenida mediante la introspección del archivo Ecore.
 *
 * @param id identificador utilizado por la API.
 * @param fileName nombre del archivo Ecore.
 * @param descriptor descripción neutral del metamodelo.
 */
public record RegisteredMetamodel(
    String id,
    String fileName,
    MetamodelDescriptor descriptor
) {
}
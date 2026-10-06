package com.example.editor.metamodel;

import java.util.List;

/**
 * Contiene una descripción neutral de un metamodelo Ecore.
 *
 * Estas estructuras no dependen del editor gráfico. Más adelante podrán
 * convertirse directamente a JSON para que las utilice el frontend.
 */
public record MetamodelDescriptor(String sourceUri, List<PackageDescriptor> packages) {

    /**
     * Describe un paquete Ecore y todos sus elementos principales.
     */
    public record PackageDescriptor(
        String name,
        String nsUri,
        String nsPrefix,
        List<ClassDescriptor> classes,
        List<EnumDescriptor> enums,
        List<DataTypeDescriptor> dataTypes,
        List<PackageDescriptor> subpackages
    ) {
    }

    /**
     * Describe una EClass, su herencia, sus atributos y sus referencias.
     */
    public record ClassDescriptor(
        String id,
        String name,
        boolean abstractType,
        boolean interfaceType,
        boolean instantiable,
        List<String> superTypeIds,
        List<AttributeDescriptor> attributes,
        List<ReferenceDescriptor> references
    ) {
    }

    /**
     * Describe un EAttribute y las reglas estructurales definidas en Ecore.
     */
    public record AttributeDescriptor(
        String id,
        String name,
        String declaringClassId,
        boolean inherited,
        String typeId,
        String typeName,
        int lowerBound,
        int upperBound,
        boolean many,
        boolean required,
        boolean changeable,
        boolean derived,
        boolean transientFeature,
        boolean volatileFeature,
        boolean unsettable,
        boolean ordered,
        boolean unique,
        boolean idAttribute,
        String defaultValue
    ) {
    }

    /**
     * Describe una EReference, su tipo destino y su comportamiento.
     */
    public record ReferenceDescriptor(
        String id,
        String name,
        String declaringClassId,
        boolean inherited,
        String targetClassId,
        String targetClassName,
        int lowerBound,
        int upperBound,
        boolean many,
        boolean required,
        boolean containment,
        boolean container,
        boolean resolveProxies,
        boolean changeable,
        boolean derived,
        boolean transientFeature,
        boolean volatileFeature,
        boolean unsettable,
        boolean ordered,
        boolean unique,
        String oppositeReferenceId
    ) {
    }

    /**
     * Describe un EEnum y todos sus valores posibles.
     */
    public record EnumDescriptor(
        String id,
        String name,
        List<EnumLiteralDescriptor> literals
    ) {
    }

    /**
     * Describe un literal perteneciente a un EEnum.
     */
    public record EnumLiteralDescriptor(
        String name,
        String literal,
        int value
    ) {
    }

    /**
     * Describe un tipo de dato personalizado que no es una clase ni un enum.
     */
    public record DataTypeDescriptor(
        String id,
        String name,
        String instanceClassName,
        boolean serializable
    ) {
    }
}
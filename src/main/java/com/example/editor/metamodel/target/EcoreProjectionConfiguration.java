package com.example.editor.metamodel.target;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;

/**
 * Contiene todas las decisiones específicas de la proyección Ecore.
 */
public record EcoreProjectionConfiguration(boolean enabled, EcoreGenerationMode generationMode, boolean requiredForRuntime, String fileName, String namespaceUri, String namespacePrefix, Map<String, EcoreAttributeConfiguration> attributeOptions, Map<String, EcoreRelationConfiguration> relationOptions, Map<String, EcoreDataTypeConfiguration> dataTypeOptions) {

    public EcoreProjectionConfiguration {
        generationMode = generationMode == null ? EcoreGenerationMode.ON_SAVE : generationMode;
        fileName = fileName == null ? "" : fileName;
        namespaceUri = namespaceUri == null ? "" : namespaceUri;
        namespacePrefix = namespacePrefix == null ? "" : namespacePrefix;
        attributeOptions = immutableMap(attributeOptions);
        relationOptions = immutableMap(relationOptions);
        dataTypeOptions = immutableMap(dataTypeOptions);
    }

    /** Descripción: Ejecuta la operación «defaultsFor» correspondiente a la responsabilidad de EcoreProjectionConfiguration. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static EcoreProjectionConfiguration defaultsFor(MetaModel metamodel) {
        String id = metamodel == null || metamodel.id().isBlank() ? "metamodel" : metamodel.id();
        String fileName = metamodel == null ? id + ".ecore" : EcoreImportMetadata.text(metamodel.annotations(), EcoreImportMetadata.ARTIFACT_NAME, id + ".ecore");
        String namespaceUri = metamodel == null ? "urn:" + id : EcoreImportMetadata.text(metamodel.annotations(), EcoreImportMetadata.NAMESPACE_URI, "urn:" + id);
        String namespacePrefix = metamodel == null ? id : EcoreImportMetadata.text(metamodel.annotations(), EcoreImportMetadata.NAMESPACE_PREFIX, id);
        Map<String, EcoreAttributeConfiguration> attributes = new LinkedHashMap<>();
        Map<String, EcoreRelationConfiguration> relations = new LinkedHashMap<>();
        Map<String, EcoreDataTypeConfiguration> dataTypes = new LinkedHashMap<>();

        if (metamodel != null) {
            for (MetaClass metaClass : metamodel.classes()) {
                for (MetaAttribute attribute : metaClass.attributes()) {
                    attributes.put(attribute.id(), new EcoreAttributeConfiguration(EcoreImportMetadata.bool(attribute.annotations(), EcoreImportMetadata.CHANGEABLE, true), false, false, false, false, false));
                }
            }

            for (MetaRelation relation : metamodel.relations()) {
                if (isEcoreStructuralRelation(relation)) {
                    boolean containment = relation.kind() == MetaRelationKind.CONTAINMENT;
                    relations.put(relation.id(), new EcoreRelationConfiguration(EcoreImportMetadata.bool(relation.annotations(), EcoreImportMetadata.CHANGEABLE, true), containment ? false : EcoreImportMetadata.bool(relation.annotations(), EcoreImportMetadata.RESOLVE_PROXIES, false), false, false, false, false));
                }
            }

            for (MetaDataType dataType : metamodel.dataTypes()) {
                dataTypes.put(dataType.id(), new EcoreDataTypeConfiguration(EcoreImportMetadata.text(dataType.annotations(), EcoreImportMetadata.INSTANCE_CLASS, ""), EcoreImportMetadata.bool(dataType.annotations(), EcoreImportMetadata.SERIALIZABLE, true)));
            }
        }

        return new EcoreProjectionConfiguration(true, EcoreGenerationMode.ON_SAVE, true, fileName, namespaceUri, namespacePrefix, attributes, relations, dataTypes);
    }

    /**
     * Completa opciones ausentes y elimina opciones de elementos que ya no existen.
     */
    /** Descripción: Ejecuta la operación «normalizedFor» correspondiente a la responsabilidad de EcoreProjectionConfiguration. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EcoreProjectionConfiguration normalizedFor(MetaModel metamodel) {
        if (metamodel == null) {
            throw new IllegalArgumentException("MetaModel must not be null when normalizing Ecore configuration");
        }

        EcoreProjectionConfiguration defaults = defaultsFor(metamodel);
        String effectiveFileName = fileName.isBlank() ? defaults.fileName() : fileName;
        String effectiveNamespaceUri = namespaceUri.isBlank() ? defaults.namespaceUri() : namespaceUri;
        String effectiveNamespacePrefix = namespacePrefix.isBlank() ? defaults.namespacePrefix() : namespacePrefix;
        Map<String, EcoreAttributeConfiguration> attributes = new LinkedHashMap<>();
        Map<String, EcoreRelationConfiguration> relations = new LinkedHashMap<>();
        Map<String, EcoreDataTypeConfiguration> dataTypes = new LinkedHashMap<>();

        for (MetaClass metaClass : metamodel.classes()) {
            for (MetaAttribute attribute : metaClass.attributes()) {
                attributes.put(attribute.id(), attributeOptions.getOrDefault(attribute.id(), defaults.attribute(attribute.id())));
            }
        }

        for (MetaRelation relation : metamodel.relations()) {
            if (isEcoreStructuralRelation(relation)) {
                boolean containment = relation.kind() == MetaRelationKind.CONTAINMENT;
                relations.put(relation.id(), relationOptions.getOrDefault(relation.id(), defaults.relation(relation.id(), containment)));
            }
        }

        for (MetaDataType dataType : metamodel.dataTypes()) {
            dataTypes.put(dataType.id(), dataTypeOptions.getOrDefault(dataType.id(), defaults.dataType(dataType.id())));
        }

        return new EcoreProjectionConfiguration(enabled, generationMode, requiredForRuntime, effectiveFileName, effectiveNamespaceUri, effectiveNamespacePrefix, attributes, relations, dataTypes);
    }

    /** Descripción: Ejecuta la operación «attribute» correspondiente a la responsabilidad de EcoreProjectionConfiguration. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EcoreAttributeConfiguration attribute(String id) {
        return attributeOptions.getOrDefault(id, EcoreAttributeConfiguration.defaults());
    }

    /** Descripción: Ejecuta la operación «relation» correspondiente a la responsabilidad de EcoreProjectionConfiguration. Entradas: id: identificador que delimita el recurso objetivo; containment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EcoreRelationConfiguration relation(String id, boolean containment) {
        return relationOptions.getOrDefault(id, containment ? EcoreRelationConfiguration.containmentDefaults() : EcoreRelationConfiguration.defaults());
    }

    /** Descripción: Ejecuta la operación «dataType» correspondiente a la responsabilidad de EcoreProjectionConfiguration. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EcoreDataTypeConfiguration dataType(String id) {
        return dataTypeOptions.getOrDefault(id, EcoreDataTypeConfiguration.defaults());
    }

    /** Descripción: Evalúa la condición «isEcoreStructuralRelation» sin modificar el estado externo. Entradas: relation: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isEcoreStructuralRelation(MetaRelation relation) {
        return relation.kind() == MetaRelationKind.REFERENCE || relation.kind() == MetaRelationKind.CONTAINMENT || relation.kind() == MetaRelationKind.AGGREGATION;
    }

    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de EcoreProjectionConfiguration. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

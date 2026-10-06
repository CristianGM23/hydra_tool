package com.example.editor.metamodel.adapter.ecore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.EcoreDesignIdSupport;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.target.EcoreAttributeConfiguration;
import com.example.editor.metamodel.target.EcoreDataTypeConfiguration;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.EcoreRelationConfiguration;

/** Projects the neutral metamodel into an EMF EPackage while preserving HYDRA metadata. */
final class EcoreProjectionWriter {
    private final EcoreFactory factory = EcoreFactory.eINSTANCE;
    private final EcoreAdapterProperties properties;
    private final EcorePrimitiveTypeRegistry primitiveTypeRegistry;

    /** Descripción: Inicializa una instancia de EcoreProjectionWriter con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; primitiveTypeRegistry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EcoreProjectionWriter(EcoreAdapterProperties properties, EcorePrimitiveTypeRegistry primitiveTypeRegistry) {
        this.properties = properties;
        this.primitiveTypeRegistry = primitiveTypeRegistry;
    }

    /** Descripción: Actualiza el estado gestionado por EcoreProjectionWriter mediante la operación «write». Entradas: metamodel: dato de entrada necesario para la operación; ePackage: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void write(MetaModel metamodel, EPackage ePackage, EcoreProjectionConfiguration configuration, List<TransformationWarning> warnings) {
        Map<String, EEnum> enums = createEnums(metamodel, ePackage);
        Map<String, EDataType> dataTypes = createDataTypes(metamodel, ePackage, configuration);
        Map<String, EClass> classes = createClasses(metamodel, ePackage);
        addAttributes(metamodel, classes, enums, dataTypes, warnings, configuration);
        addOperations(metamodel, classes, enums, dataTypes, warnings);
        Map<String, EReference> references = addStructuralRelations(metamodel, classes, warnings, configuration);
        addSuperTypes(metamodel, classes);
        addOpposites(metamodel, references);
        writeRelationMetadata(metamodel, ePackage);
    }

    /** Descripción: Crea o prepara el resultado de «createEnums» dentro de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, EEnum> createEnums(MetaModel metamodel, EPackage ePackage) {
        Map<String, EEnum> result = new LinkedHashMap<>();

        for (MetaEnum design : metamodel.enums()) {
            EEnum eEnum = factory.createEEnum();
            eEnum.setName(design.name());
            EcoreDesignIdSupport.writeId(eEnum, design.id());
            writeAnnotations(eEnum, design.annotations());

            for (MetaEnumLiteral literalDesign : design.literals()) {
                EEnumLiteral literal = factory.createEEnumLiteral();
                literal.setName(literalDesign.name());
                literal.setLiteral(literalDesign.name());
                literal.setValue(literalDesign.value());
                EcoreDesignIdSupport.writeId(literal, literalDesign.id());
                writeAnnotations(literal, literalDesign.annotations());
                eEnum.getELiterals().add(literal);
            }

            ePackage.getEClassifiers().add(eEnum);
            result.put(design.id(), eEnum);
        }

        return result;
    }

    /** Descripción: Crea o prepara el resultado de «createDataTypes» dentro de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; ePackage: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, EDataType> createDataTypes(MetaModel metamodel, EPackage ePackage, EcoreProjectionConfiguration configuration) {
        Map<String, EDataType> result = new LinkedHashMap<>();

        for (MetaDataType design : metamodel.dataTypes()) {
            EDataType dataType = factory.createEDataType();
            dataType.setName(design.name());

            EcoreDataTypeConfiguration dataTypeConfiguration = configuration.dataType(design.id());

            if (!dataTypeConfiguration.instanceClassName().isBlank()) {
                dataType.setInstanceClassName(dataTypeConfiguration.instanceClassName());
            }

            dataType.setSerializable(dataTypeConfiguration.serializable());
            EcoreDesignIdSupport.writeId(dataType, design.id());
            writeAnnotations(dataType, design.annotations());
            ePackage.getEClassifiers().add(dataType);
            result.put(design.id(), dataType);
        }

        return result;
    }

    /** Descripción: Crea o prepara el resultado de «createClasses» dentro de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, EClass> createClasses(MetaModel metamodel, EPackage ePackage) {
        Map<String, EClass> result = new LinkedHashMap<>();

        for (MetaClass design : metamodel.classes()) {
            EClass eClass = factory.createEClass();
            eClass.setName(design.name());
            eClass.setAbstract(design.kind() == MetaClassKind.ABSTRACT_CLASS);
            eClass.setInterface(design.kind() == MetaClassKind.INTERFACE);
            EcoreDesignIdSupport.writeId(eClass, design.id());
            writeAnnotations(eClass, design.annotations());
            ePackage.getEClassifiers().add(eClass);
            result.put(design.id(), eClass);
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «addAttributes» correspondiente a la responsabilidad de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addAttributes(MetaModel metamodel, Map<String, EClass> classes, Map<String, EEnum> enums, Map<String, EDataType> dataTypes, List<TransformationWarning> warnings, EcoreProjectionConfiguration configuration) {
        for (MetaClass classDesign : metamodel.classes()) {
            EClass owner = requiredClass(classes, classDesign.id());

            for (MetaAttribute design : classDesign.attributes()) {
                EAttribute attribute = factory.createEAttribute();
                attribute.setName(design.name());
                EcoreDesignIdSupport.writeId(attribute, design.id());
                writeAnnotations(attribute, design.annotations());
                attribute.setEType(resolveAttributeType(attribute, design, enums, dataTypes, warnings));
                setMultiplicity(attribute, design.multiplicity());
                attribute.setOrdered(design.ordered());
                attribute.setUnique(design.unique());
                EcoreAttributeConfiguration attributeConfiguration = configuration.attribute(design.id());
                attribute.setChangeable(attributeConfiguration.changeable());
                attribute.setTransient(attributeConfiguration.transientFeature());
                attribute.setVolatile(attributeConfiguration.volatileFeature());
                attribute.setDerived(attributeConfiguration.derived());
                attribute.setUnsettable(attributeConfiguration.unsettable());
                attribute.setID(attributeConfiguration.id());

                if (!design.defaultValue().isBlank()) {
                    attribute.setDefaultValueLiteral(design.defaultValue());
                }

                owner.getEStructuralFeatures().add(attribute);
            }
        }
    }

    /** Descripción: Ejecuta la operación «addOperations» correspondiente a la responsabilidad de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addOperations(MetaModel metamodel, Map<String, EClass> classes, Map<String, EEnum> enums, Map<String, EDataType> dataTypes, List<TransformationWarning> warnings) {
        for(MetaClass classDesign : metamodel.classes()) {
            EClass owner = requiredClass(classes, classDesign.id());
            for(MetaOperation design : classDesign.operations()) {
                EOperation operation = factory.createEOperation();
                operation.setName(design.name());
                EcoreDesignIdSupport.writeId(operation, design.id());
                writeAnnotations(operation, design.annotations());
                EcoreNeutralMetadataSupport.writeMetadata(operation, EcoreNeutralMetadataSupport.OPERATION_BEHAVIOR_KIND_KEY, design.behaviorKind().name());
                if(design.returnType() != null) {
                    operation.setEType(resolveOperationType(operation, design.returnType(), classes, enums, dataTypes, warnings, "$.operations[" + design.id() + "].returnType"));
                    setMultiplicity(operation, design.returnMultiplicity());
                    operation.setOrdered(design.ordered());
                    operation.setUnique(design.unique());
                }
                for(MetaParameter parameterDesign : design.parameters()) {
                    EParameter parameter = factory.createEParameter();
                    parameter.setName(parameterDesign.name());
                    EcoreDesignIdSupport.writeId(parameter, parameterDesign.id());
                    writeAnnotations(parameter, parameterDesign.annotations());
                    parameter.setEType(resolveOperationType(parameter, parameterDesign.type(), classes, enums, dataTypes, warnings, "$.operations[" + design.id() + "].parameters[" + parameterDesign.id() + "].type"));
                    setMultiplicity(parameter, parameterDesign.multiplicity());
                    parameter.setOrdered(parameterDesign.ordered());
                    parameter.setUnique(parameterDesign.unique());
                    operation.getEParameters().add(parameter);
                }
                List<String> externalExceptionTypes = new ArrayList<>();
                for(MetaTypeReference exceptionType : design.exceptionTypes()) {
                    if(exceptionType.kind() == MetaTypeReferenceKind.EXTERNAL) {
                        externalExceptionTypes.add(exceptionType.externalName());
                        operation.getEExceptions().add(EcorePackage.eINSTANCE.getEJavaObject());
                        if(properties.isReportAdaptations()) warnings.add(new TransformationWarning("ECORE_OPERATION_EXTERNAL_EXCEPTION_ANNOTATED", "$.operations[" + design.id() + "].exceptionTypes", "External exception type was mapped to EJavaObject and preserved in metadata"));
                    } else {
                        externalExceptionTypes.add("");
                        operation.getEExceptions().add(resolveOperationType(operation, exceptionType, classes, enums, dataTypes, warnings, "$.operations[" + design.id() + "].exceptionTypes"));
                    }
                }
                EcoreNeutralMetadataSupport.writeExternalTypeList(operation, EcoreNeutralMetadataSupport.OPERATION_EXTERNAL_EXCEPTION_TYPES_KEY, externalExceptionTypes);
                owner.getEOperations().add(operation);
            }
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolveOperationType» dentro de EcoreProjectionWriter. Entradas: owner: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ EClassifier resolveOperationType(org.eclipse.emf.ecore.EModelElement owner, MetaTypeReference type, Map<String, EClass> classes, Map<String, EEnum> enums, Map<String, EDataType> dataTypes, List<TransformationWarning> warnings, String path) {
        if(type == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Operation type must not be null at " + path);
        }
        if(type.kind() == MetaTypeReferenceKind.CLASS) {
            EClass value = classes.get(type.targetId());
            if(value != null) return value;
        }
        if(type.kind() == MetaTypeReferenceKind.ENUM) {
            EEnum value = enums.get(type.targetId());
            if(value != null) return value;
        }
        if(type.kind() == MetaTypeReferenceKind.DATATYPE) {
            EDataType value = dataTypes.get(type.targetId());
            if(value != null) return value;
        }
        if(type.kind() == MetaTypeReferenceKind.PRIMITIVE) {
            try { return primitiveTypeRegistry.toEcore(MetaPrimitiveType.valueOf(type.targetId())); }
            catch(IllegalArgumentException exception) { throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown operation primitive type: " + type.targetId(), exception); }
        }
        if(type.kind() == MetaTypeReferenceKind.EXTERNAL) {
            EcoreNeutralMetadataSupport.writeMetadata(owner, EcoreNeutralMetadataSupport.EXTERNAL_TYPE_KEY, type.externalName());
            if(properties.isReportAdaptations()) warnings.add(new TransformationWarning("ECORE_OPERATION_EXTERNAL_TYPE_ANNOTATED", path, "External operation type was mapped to EJavaObject and preserved in metadata"));
            return EcorePackage.eINSTANCE.getEJavaObject();
        }
        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown operation type at " + path + ": " + type);
    }

    /** Descripción: Ejecuta la operación «addStructuralRelations» correspondiente a la responsabilidad de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, EReference> addStructuralRelations(MetaModel metamodel, Map<String, EClass> classes, List<TransformationWarning> warnings, EcoreProjectionConfiguration configuration) {
        Map<String, EReference> result = new LinkedHashMap<>();

        for (MetaRelation relation : metamodel.relations()) {
            if (relation.kind() != MetaRelationKind.REFERENCE && relation.kind() != MetaRelationKind.CONTAINMENT && relation.kind() != MetaRelationKind.AGGREGATION) {
                continue;
            }

            EClass owner = requiredClass(classes, relation.sourceTypeId());
            EClass target = requiredClass(classes, relation.targetTypeId());
            EReference reference = factory.createEReference();
            reference.setName(relation.name());
            reference.setEType(target);
            EcoreDesignIdSupport.writeId(reference, relation.id());
            writeAnnotations(reference, relation.annotations());
            Multiplicity multiplicity = relation.targetMultiplicity() == null ? Multiplicity.optional() : relation.targetMultiplicity();
            setMultiplicity(reference, multiplicity);
            boolean containment = relation.kind() == MetaRelationKind.CONTAINMENT;
            EcoreRelationConfiguration relationConfiguration = configuration.relation(relation.id(), containment);
            reference.setContainment(containment);
            reference.setResolveProxies(containment ? false : relationConfiguration.resolveProxies());
            reference.setOrdered(relation.ordered());
            reference.setUnique(relation.unique());
            reference.setChangeable(relationConfiguration.changeable());
            reference.setTransient(relationConfiguration.transientFeature());
            reference.setVolatile(relationConfiguration.volatileFeature());
            reference.setDerived(relationConfiguration.derived());
            reference.setUnsettable(relationConfiguration.unsettable());
            owner.getEStructuralFeatures().add(reference);
            result.put(relation.id(), reference);

            if (relation.kind() == MetaRelationKind.AGGREGATION && properties.isReportAdaptations()) {
                warnings.add(new TransformationWarning("ECORE_AGGREGATION_ANNOTATED", "$.relations[" + relation.id() + "]", "Aggregation was stored as a non-containment EReference with neutral relation metadata"));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «addSuperTypes» correspondiente a la responsabilidad de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addSuperTypes(MetaModel metamodel, Map<String, EClass> classes) {
        for (MetaRelation relation : metamodel.relations()) {
            if (relation.kind() != MetaRelationKind.INHERITANCE && relation.kind() != MetaRelationKind.IMPLEMENTATION) {
                continue;
            }

            EClass source = requiredClass(classes, relation.sourceTypeId());
            EClass target = requiredClass(classes, relation.targetTypeId());

            if (!source.getESuperTypes().contains(target)) {
                source.getESuperTypes().add(target);
            }
        }
    }

    /** Descripción: Ejecuta la operación «addOpposites» correspondiente a la responsabilidad de EcoreProjectionWriter. Entradas: metamodel: dato de entrada necesario para la operación; references: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addOpposites(MetaModel metamodel, Map<String, EReference> references) {
        for (MetaRelation relation : metamodel.relations()) {
            if (relation.oppositeRelationId().isBlank()) {
                continue;
            }

            EReference reference = references.get(relation.id());
            EReference opposite = references.get(relation.oppositeRelationId());

            if (reference != null && opposite != null && reference.getEOpposite() != opposite) {
                reference.setEOpposite(opposite);
            }
        }
    }

    /** Descripción: Actualiza el estado gestionado por EcoreProjectionWriter mediante la operación «writeRelationMetadata». Entradas: metamodel: dato de entrada necesario para la operación; ePackage: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void writeRelationMetadata(MetaModel metamodel, EPackage ePackage) {
        if (!properties.isPreserveNeutralAnnotations()) {
            return;
        }

        for (MetaRelation relation : metamodel.relations()) {
            EcoreNeutralMetadataSupport.writeRelation(ePackage, relation);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolveAttributeType» dentro de EcoreProjectionWriter. Entradas: eAttribute: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ EDataType resolveAttributeType(EAttribute eAttribute, MetaAttribute attribute, Map<String, EEnum> enums, Map<String, EDataType> dataTypes, List<TransformationWarning> warnings) {
        MetaTypeReference type = attribute.type();

        if (type.kind() == MetaTypeReferenceKind.PRIMITIVE) {
            MetaPrimitiveType primitive;

            try {
                primitive = MetaPrimitiveType.valueOf(type.targetId());
            } catch (IllegalArgumentException exception) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown neutral primitive type: " + type.targetId(), exception);
            }

            return primitiveTypeRegistry.toEcore(primitive);
        }

        if (type.kind() == MetaTypeReferenceKind.ENUM) {
            EEnum eEnum = enums.get(type.targetId());

            if (eEnum != null) {
                return eEnum;
            }
        }

        if (type.kind() == MetaTypeReferenceKind.DATATYPE) {
            EDataType dataType = dataTypes.get(type.targetId());

            if (dataType != null) {
                return dataType;
            }
        }

        if (type.kind() == MetaTypeReferenceKind.EXTERNAL) {
            if (properties.getExternalTypePolicy() == EcoreExternalTypePolicy.JAVA_OBJECT_WITH_ANNOTATION) {
                EcoreNeutralMetadataSupport.writeMetadata(eAttribute, EcoreNeutralMetadataSupport.EXTERNAL_TYPE_KEY, type.externalName());

                if (properties.isReportAdaptations()) {
                    warnings.add(new TransformationWarning("ECORE_EXTERNAL_TYPE_ANNOTATED", "$.attributes[" + attribute.id() + "]", "External type was mapped to EJavaObject and preserved in neutral EAnnotation metadata"));
                }

                return EcorePackage.eINSTANCE.getEJavaObject();
            }

            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "External neutral attribute type cannot be converted with the configured Ecore policy: " + type.externalName());
        }

        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown neutral attribute type: " + type);
    }

    /** Descripción: Actualiza el estado gestionado por EcoreProjectionWriter mediante la operación «setMultiplicity». Entradas: element: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void setMultiplicity(org.eclipse.emf.ecore.ETypedElement element, Multiplicity multiplicity) {
        Multiplicity effective = multiplicity == null ? Multiplicity.optional() : multiplicity;
        element.setLowerBound(effective.lowerBound());
        element.setUpperBound(effective.unbounded() ? -1 : effective.upperBound());
    }

    /** Descripción: Valida las condiciones necesarias para «requiredClass» y rechaza entradas no válidas. Entradas: classes: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ EClass requiredClass(Map<String, EClass> classes, String classId) {
        EClass eClass = classes.get(classId);

        if (eClass == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Neutral relation references an unknown class: " + classId);
        }

        return eClass;
    }

    /** Descripción: Actualiza el estado gestionado por EcoreProjectionWriter mediante la operación «writeAnnotations». Entradas: element: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void writeAnnotations(org.eclipse.emf.ecore.EModelElement element, Map<String, String> annotations) {
        if (properties.isPreserveNeutralAnnotations()) {
            EcoreNeutralMetadataSupport.writeAnnotations(element, annotations);
        }
    }
}

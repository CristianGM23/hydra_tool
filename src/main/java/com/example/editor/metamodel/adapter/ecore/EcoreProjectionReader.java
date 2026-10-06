package com.example.editor.metamodel.adapter.ecore;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.eclipse.emf.ecore.ETypedElement;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.EcoreDesignIdSupport;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;

/** Reads an EMF EPackage into HYDRA's neutral metamodel. */
final class EcoreProjectionReader {
    private final EcoreAdapterProperties properties;
    private final EcorePrimitiveTypeRegistry primitiveTypeRegistry;

    /** Descripción: Inicializa una instancia de EcoreProjectionReader con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; primitiveTypeRegistry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EcoreProjectionReader(EcoreAdapterProperties properties, EcorePrimitiveTypeRegistry primitiveTypeRegistry) {
        this.properties = properties;
        this.primitiveTypeRegistry = primitiveTypeRegistry;
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación; artifactName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ReadResult read(EPackage ePackage, String artifactName) {
        List<TransformationWarning> warnings = new ArrayList<>();
        Map<EEnum, String> enumIds = collectEnumIds(ePackage);
        Map<EDataType, String> dataTypeIds = collectDataTypeIds(ePackage);
        Map<EClass, String> classIds = collectClassIds(ePackage);
        Map<EReference, String> referenceIds = collectReferenceIds(ePackage, classIds);
        Map<String, MetaRelation> storedRelations = EcoreNeutralMetadataSupport.readRelations(ePackage);
        List<MetaEnum> enums = readEnums(ePackage, enumIds);
        List<MetaDataType> dataTypes = readDataTypes(ePackage, dataTypeIds);
        List<MetaClass> classes = readClasses(ePackage, enumIds, dataTypeIds, classIds, warnings);
        List<MetaRelation> relations = readRelations(ePackage, classIds, referenceIds, storedRelations, warnings);
        String fallbackId = removeExtension(artifactName);
        String metamodelId = EcoreDesignIdSupport.readId(ePackage, fallbackId);
        String packageName = safeText(ePackage.getName(), fallbackId);
        String namespaceUri = safeText(ePackage.getNsURI(), "urn:" + packageName);
        String namespacePrefix = safeText(ePackage.getNsPrefix(), packageName);
        MetaModel metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, metamodelId, packageName, classes, enums, dataTypes, relations, EcoreImportMetadata.model(readAnnotations(ePackage), artifactName, namespaceUri, namespacePrefix));
        return new ReadResult(metamodel, List.copyOf(warnings));
    }

    /** Descripción: Obtiene la información correspondiente a «readEnums» dentro de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<MetaEnum> readEnums(EPackage ePackage, Map<EEnum, String> enumIds) {
        List<MetaEnum> result = new ArrayList<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EEnum eEnum) {
                List<MetaEnumLiteral> literals = new ArrayList<>();

                for (EEnumLiteral literal : eEnum.getELiterals()) {
                    String fallback = "literal:" + enumIds.get(eEnum) + ":" + normalizeName(literal.getName());
                    String literalId = EcoreDesignIdSupport.readId(literal, fallback);
                    literals.add(new MetaEnumLiteral(literalId, literal.getName(), literal.getValue(), readAnnotations(literal)));
                }

                result.add(new MetaEnum(enumIds.get(eEnum), eEnum.getName(), literals, readAnnotations(eEnum)));
            }
        }

        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «readDataTypes» dentro de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación; dataTypeIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<MetaDataType> readDataTypes(EPackage ePackage, Map<EDataType, String> dataTypeIds) {
        List<MetaDataType> result = new ArrayList<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EDataType dataType && !(classifier instanceof EEnum)) {
                String implementationName = safeText(dataType.getInstanceClassName(), "");
                result.add(new MetaDataType(dataTypeIds.get(dataType), dataType.getName(), EcoreImportMetadata.dataType(readAnnotations(dataType), implementationName, dataType.isSerializable())));
            }
        }

        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «readClasses» dentro de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación; dataTypeIds: dato de entrada necesario para la operación; classIds: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<MetaClass> readClasses(EPackage ePackage, Map<EEnum, String> enumIds, Map<EDataType, String> dataTypeIds, Map<EClass, String> classIds, List<TransformationWarning> warnings) {
        List<MetaClass> result = new ArrayList<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                MetaClassKind kind = MetaClassKind.CLASS;

                if (eClass.isInterface()) {
                    kind = MetaClassKind.INTERFACE;
                } else if (eClass.isAbstract()) {
                    kind = MetaClassKind.ABSTRACT_CLASS;
                }

                List<MetaAttribute> attributes = new ArrayList<>();

                for (EAttribute attribute : eClass.getEAttributes()) {
                    String classId = classIds.get(eClass);
                    String fallback = "attribute:" + classId + ":" + normalizeName(attribute.getName());
                    String attributeId = EcoreDesignIdSupport.readId(attribute, fallback);
                    MetaTypeReference type = resolveTypeReference(attribute, enumIds, dataTypeIds, warnings);
                    Multiplicity multiplicity = fromEcoreBounds(attribute.getLowerBound(), attribute.getUpperBound());
                    String defaultValue = attribute.getDefaultValueLiteral() == null ? "" : attribute.getDefaultValueLiteral();
                    attributes.add(new MetaAttribute(attributeId, attribute.getName(), type, multiplicity, attribute.isOrdered(), attribute.isUnique(), defaultValue, EcoreImportMetadata.attribute(readAnnotations(attribute), attribute.isChangeable())));
                }

                List<MetaOperation> operations = new ArrayList<>();
                for(EOperation operation : eClass.getEOperations()) {
                    String classId = classIds.get(eClass);
                    String operationId = EcoreDesignIdSupport.readId(operation, "operation:" + classId + ":" + normalizeName(operation.getName()));
                    List<MetaParameter> parameters = new ArrayList<>();
                    for(EParameter parameter : operation.getEParameters()) {
                        String parameterId = EcoreDesignIdSupport.readId(parameter, "parameter:" + operationId + ":" + normalizeName(parameter.getName()));
                        parameters.add(new MetaParameter(parameterId, parameter.getName(), resolveOperationTypeReference(parameter, enumIds, dataTypeIds, classIds, warnings), fromEcoreBounds(parameter.getLowerBound(), parameter.getUpperBound()), parameter.isOrdered(), parameter.isUnique(), readAnnotations(parameter)));
                    }
                    List<String> externalExceptionTypes = EcoreNeutralMetadataSupport.readExternalTypeList(operation, EcoreNeutralMetadataSupport.OPERATION_EXTERNAL_EXCEPTION_TYPES_KEY);
                    List<MetaTypeReference> exceptions = new ArrayList<>();
                    for(int exceptionIndex = 0; exceptionIndex < operation.getEExceptions().size(); exceptionIndex++) {
                        String externalName = exceptionIndex < externalExceptionTypes.size() ? externalExceptionTypes.get(exceptionIndex) : "";
                        if(!externalName.isBlank()) exceptions.add(MetaTypeReference.external(externalName));
                        else exceptions.add(resolveClassifierTypeReference(operation.getEExceptions().get(exceptionIndex), enumIds, dataTypeIds, classIds, warnings));
                    }
                    MetaTypeReference returnType = operation.getEType() == null ? null : resolveOperationTypeReference(operation, enumIds, dataTypeIds, classIds, warnings);
                    Multiplicity returnMultiplicity = returnType == null ? null : fromEcoreBounds(operation.getLowerBound(), operation.getUpperBound());
                    MetaOperationBehaviorKind behaviorKind = readOperationBehaviorKind(operation);
                    operations.add(new MetaOperation(operationId, operation.getName(), returnType, returnMultiplicity, operation.isOrdered(), operation.isUnique(), parameters, exceptions, behaviorKind, readAnnotations(operation)));
                }

                result.add(new MetaClass(classIds.get(eClass), eClass.getName(), kind, attributes, operations, readAnnotations(eClass)));
            }
        }

        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «readRelations» dentro de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación; classIds: dato de entrada necesario para la operación; referenceIds: dato de entrada necesario para la operación; storedRelations: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<MetaRelation> readRelations(EPackage ePackage, Map<EClass, String> classIds, Map<EReference, String> referenceIds, Map<String, MetaRelation> storedRelations, List<TransformationWarning> warnings) {
        List<MetaRelation> result = new ArrayList<>();
        Map<String, Boolean> consumed = new HashMap<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (!(classifier instanceof EClass eClass)) {
                continue;
            }

            String sourceId = classIds.get(eClass);

            for (EReference reference : eClass.getEReferences()) {
                String referenceId = referenceIds.get(reference);
                String targetId = classIds.get(reference.getEReferenceType());

                if (targetId == null) {
                    throw new MetamodelDesignException(Reason.INVALID_REQUEST, "External reference target is not supported by the neutral Ecore adapter: " + reference.getName());
                }

                MetaRelation stored = storedRelations.get(referenceId);
                MetaRelationKind kind = reference.isContainment() ? MetaRelationKind.CONTAINMENT : MetaRelationKind.REFERENCE;
                Multiplicity sourceMultiplicity = null;
                Map<String, String> annotations = readAnnotations(reference);

                if (stored != null) {
                    kind = stored.kind();
                    sourceMultiplicity = stored.sourceMultiplicity();
                    annotations = stored.annotations();
                    consumed.put(stored.id(), true);
                }

                String oppositeId = "";

                if (reference.getEOpposite() != null) {
                    oppositeId = referenceIds.get(reference.getEOpposite());

                    if (oppositeId == null) {
                        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "External opposite reference is not supported by the neutral Ecore adapter: " + reference.getName());
                    }
                }

                MetaRelation relation = new MetaRelation(referenceId, reference.getName(), sourceId, targetId, kind, sourceMultiplicity, fromEcoreBounds(reference.getLowerBound(), reference.getUpperBound()), oppositeId, reference.isOrdered(), reference.isUnique(), EcoreImportMetadata.relation(annotations, reference.isChangeable(), reference.isResolveProxies()));
                result.add(relation);
            }

            for (EClass superType : eClass.getESuperTypes()) {
                String targetId = classIds.get(superType);

                if (targetId == null) {
                    throw new MetamodelDesignException(Reason.INVALID_REQUEST, "External superclass is not supported by the neutral Ecore adapter: " + superType.getName());
                }

                MetaRelationKind inferredKind = superType.isInterface() ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
                MetaRelation stored = EcoreNeutralMetadataSupport.findRelation(storedRelations, sourceId, targetId, List.of(MetaRelationKind.INHERITANCE, MetaRelationKind.IMPLEMENTATION));

                if (stored != null) {
                    result.add(stored);
                    consumed.put(stored.id(), true);
                } else {
                    String relationId = "supertype:" + sourceId + ":" + targetId;
                    result.add(new MetaRelation(relationId, "", sourceId, targetId, inferredKind, null, null, "", true, true, Map.of()));
                }
            }
        }

        for (MetaRelation stored : storedRelations.values()) {
            if (Boolean.TRUE.equals(consumed.get(stored.id()))) {
                continue;
            }

            if (stored.kind() == MetaRelationKind.DEPENDENCY) {
                result.add(stored);

                if (properties.isReportAdaptations()) {
                    warnings.add(new TransformationWarning("ECORE_DEPENDENCY_RESTORED", "$.relations[" + stored.id() + "]", "Dependency was restored from neutral EAnnotation metadata"));
                }
            }
        }

        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «readOperationBehaviorKind» dentro de ReadResult. Entradas: operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaOperationBehaviorKind readOperationBehaviorKind(EOperation operation) {
        String value = EcoreNeutralMetadataSupport.readMetadata(operation, EcoreNeutralMetadataSupport.OPERATION_BEHAVIOR_KIND_KEY);
        if(value.isBlank()) return MetaOperationBehaviorKind.UNSPECIFIED;
        try { return MetaOperationBehaviorKind.valueOf(value); } catch(IllegalArgumentException exception) { return MetaOperationBehaviorKind.UNSPECIFIED; }
    }

    /** Descripción: Obtiene la información correspondiente a «resolveOperationTypeReference» dentro de ReadResult. Entradas: element: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación; dataTypeIds: dato de entrada necesario para la operación; classIds: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaTypeReference resolveOperationTypeReference(ETypedElement element, Map<EEnum, String> enumIds, Map<EDataType, String> dataTypeIds, Map<EClass, String> classIds, List<TransformationWarning> warnings) {
        String externalType = EcoreNeutralMetadataSupport.readMetadata(element, EcoreNeutralMetadataSupport.EXTERNAL_TYPE_KEY);
        if(!externalType.isBlank()) return MetaTypeReference.external(externalType);
        return resolveClassifierTypeReference(element.getEType(), enumIds, dataTypeIds, classIds, warnings);
    }

    /** Descripción: Obtiene la información correspondiente a «resolveClassifierTypeReference» dentro de ReadResult. Entradas: classifier: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación; dataTypeIds: dato de entrada necesario para la operación; classIds: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaTypeReference resolveClassifierTypeReference(EClassifier classifier, Map<EEnum, String> enumIds, Map<EDataType, String> dataTypeIds, Map<EClass, String> classIds, List<TransformationWarning> warnings) {
        if(classifier instanceof EClass eClass && classIds.containsKey(eClass)) return MetaTypeReference.classType(classIds.get(eClass));
        if(classifier instanceof EEnum eEnum && enumIds.containsKey(eEnum)) return MetaTypeReference.enumType(enumIds.get(eEnum));
        if(classifier instanceof EDataType dataType) {
            MetaPrimitiveType primitive = primitiveTypeRegistry.fromEcore(dataType);
            if(primitive != null) return MetaTypeReference.primitive(primitive);
            if(dataTypeIds.containsKey(dataType)) return MetaTypeReference.dataType(dataTypeIds.get(dataType));
            return MetaTypeReference.external(dataType.getInstanceClassName() == null ? dataType.getName() : dataType.getInstanceClassName());
        }
        if(properties.isReportAdaptations()) warnings.add(new TransformationWarning("ECORE_OPERATION_TYPE_EXTERNAL", "$.operations", "Operation type was imported as external: " + (classifier == null ? "Object" : classifier.getName())));
        return MetaTypeReference.external(classifier == null ? "java.lang.Object" : classifier.getName());
    }

    /** Descripción: Obtiene la información correspondiente a «resolveTypeReference» dentro de ReadResult. Entradas: attribute: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación; dataTypeIds: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaTypeReference resolveTypeReference(EAttribute attribute, Map<EEnum, String> enumIds, Map<EDataType, String> dataTypeIds, List<TransformationWarning> warnings) {
        String externalType = EcoreNeutralMetadataSupport.readMetadata(attribute, EcoreNeutralMetadataSupport.EXTERNAL_TYPE_KEY);

        if (!externalType.isBlank()) {
            if (properties.isReportAdaptations()) {
                warnings.add(new TransformationWarning("ECORE_EXTERNAL_TYPE_RESTORED", "$.attributes[" + EcoreDesignIdSupport.readId(attribute, attribute.getName()) + "]", "External type was restored from neutral EAnnotation metadata"));
            }

            return MetaTypeReference.external(externalType);
        }

        EDataType dataType = attribute.getEAttributeType();

        if (dataType instanceof EEnum eEnum) {
            String enumId = enumIds.get(eEnum);

            if (enumId != null) {
                return MetaTypeReference.enumType(enumId);
            }
        }

        MetaPrimitiveType primitive = primitiveTypeRegistry.fromEcore(dataType);

        if (primitive != null) {
            return MetaTypeReference.primitive(primitive);
        }

        String customId = dataTypeIds.get(dataType);

        if (customId != null) {
            return MetaTypeReference.dataType(customId);
        }

        return MetaTypeReference.external(dataType.getInstanceClassName() == null ? dataType.getName() : dataType.getInstanceClassName());
    }

    /** Descripción: Ejecuta la operación «fromEcoreBounds» correspondiente a la responsabilidad de ReadResult. Entradas: lowerBound: dato de entrada necesario para la operación; upperBound: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Multiplicity fromEcoreBounds(int lowerBound, int upperBound) {
        return new Multiplicity(lowerBound, upperBound == -1 ? null : upperBound);
    }

    /** Descripción: Ejecuta la operación «collectEnumIds» correspondiente a la responsabilidad de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<EEnum, String> collectEnumIds(EPackage ePackage) {
        Map<EEnum, String> result = new LinkedHashMap<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EEnum eEnum) {
                result.put(eEnum, EcoreDesignIdSupport.readId(eEnum, "enum:" + normalizeName(eEnum.getName())));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «collectDataTypeIds» correspondiente a la responsabilidad de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<EDataType, String> collectDataTypeIds(EPackage ePackage) {
        Map<EDataType, String> result = new LinkedHashMap<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EDataType dataType && !(classifier instanceof EEnum)) {
                result.put(dataType, EcoreDesignIdSupport.readId(dataType, "datatype:" + normalizeName(dataType.getName())));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «collectClassIds» correspondiente a la responsabilidad de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<EClass, String> collectClassIds(EPackage ePackage) {
        Map<EClass, String> result = new LinkedHashMap<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                result.put(eClass, EcoreDesignIdSupport.readId(eClass, "class:" + normalizeName(eClass.getName())));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «collectReferenceIds» correspondiente a la responsabilidad de ReadResult. Entradas: ePackage: dato de entrada necesario para la operación; classIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<EReference, String> collectReferenceIds(EPackage ePackage, Map<EClass, String> classIds) {
        Map<EReference, String> result = new LinkedHashMap<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                String classId = classIds.get(eClass);

                for (EReference reference : eClass.getEReferences()) {
                    result.put(reference, EcoreDesignIdSupport.readId(reference, "reference:" + classId + ":" + normalizeName(reference.getName())));
                }
            }
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readAnnotations» dentro de ReadResult. Entradas: element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<String, String> readAnnotations(org.eclipse.emf.ecore.EModelElement element) {
        if (!properties.isPreserveNeutralAnnotations()) {
            return Map.of();
        }

        return EcoreNeutralMetadataSupport.readAnnotations(element);
    }

    /** Descripción: Ejecuta la operación «safeText» correspondiente a la responsabilidad de ReadResult. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value;
    }

    /** Descripción: Ejecuta la operación «normalizeName» correspondiente a la responsabilidad de ReadResult. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "unnamed";
        }

        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** Descripción: Elimina o libera el recurso gestionado por «removeExtension» de forma controlada. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String removeExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "metamodel";
        }

        if (fileName.toLowerCase().endsWith(".ecore")) {
            return fileName.substring(0, fileName.length() - ".ecore".length());
        }

        return fileName;
    }
    record ReadResult(MetaModel metamodel, List<TransformationWarning> warnings) {
    }
}

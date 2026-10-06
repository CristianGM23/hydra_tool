package com.example.editor.metamodel.importing.importers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

/**
 * Importa el XML neutral generado por la herramienta.
 */
@Component
public class NeutralXmlMetamodelImporter implements MetamodelImporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.XML;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        Document document = SecureXmlSupport.parse(source.content(), "neutral metamodel XML: " + source.fileName());
        Element root = document.getDocumentElement();
        Map<String, MetaEnum> enums = readEnums(root);
        Map<String, MetaDataType> dataTypes = readDataTypes(root);
        List<MetaClass> classes = readClasses(root, enums, dataTypes);
        List<MetaRelation> relations = readRelations(root);
        List<TransformationWarning> warnings = readWarnings(root);
        String id = attribute(root, "id", ImportParsingSupport.safeId(ImportParsingSupport.fileBaseName(source.fileName()), "imported-metamodel"));
        String name = attribute(root, "name", ImportParsingSupport.pascal(id, "ImportedMetamodel"));
        String artifactName = attribute(root, "fileName", id + ".ecore");
        String namespaceUri = attribute(root, "namespaceUri", "urn:imported:" + id);
        String namespacePrefix = attribute(root, "namespacePrefix", id.replace('-', '_'));
        String schemaVersion = attribute(root, "schemaVersion", MetaModel.CURRENT_SCHEMA_VERSION);
        MetaModel metamodel = new MetaModel(schemaVersion, id, name, classes, List.copyOf(enums.values()), List.copyOf(dataTypes.values()), relations, EcoreImportMetadata.model(Map.of("sourceFormat", "neutral-xml"), artifactName, namespaceUri, namespacePrefix));
        warnings = new ArrayList<>(warnings);
        warnings.add(new TransformationWarning("IMPORT_LAYOUT_NOT_AVAILABLE", "$.diagram", "Neutral XML stores only a diagram summary, so a new editable layout was generated"));
        return new MetamodelImportOutput(metamodel, null, warnings);
    }

    /** Descripción: Obtiene la información correspondiente a «readDataTypes» dentro de NeutralXmlMetamodelImporter. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, MetaDataType> readDataTypes(Element root) {
        Map<String, MetaDataType> result = new LinkedHashMap<>();

        for(Element element : elements(root, "dataType")) {
            String id = attribute(element, "id", "datatype:" + ImportParsingSupport.safeId(element.getAttribute("name"), "datatype"));
            String name = attribute(element, "name", ImportParsingSupport.pascal(id, "DataType"));
            result.put(id, new MetaDataType(id, name, EcoreImportMetadata.dataType(Map.of(), element.getAttribute("implementationTypeName"), Boolean.parseBoolean(element.getAttribute("serializable")))));
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readEnums» dentro de NeutralXmlMetamodelImporter. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, MetaEnum> readEnums(Element root) {
        Map<String, MetaEnum> result = new LinkedHashMap<>();

        for(Element element : elements(root, "enumeration")) {
            String id = attribute(element, "id", "enum:" + ImportParsingSupport.safeId(element.getAttribute("name"), "enumeration"));
            String name = attribute(element, "name", ImportParsingSupport.pascal(id, "Enumeration"));
            List<MetaEnumLiteral> literals = new ArrayList<>();

            for(Element literal : directChildren(element, "literal")) {
                String literalName = attribute(literal, "name", "VALUE");
                String literalId = attribute(literal, "id", ImportParsingSupport.literalId(id, literalName));
                literals.add(new MetaEnumLiteral(literalId, literalName, ImportParsingSupport.integer(literal.getAttribute("value"), literals.size()), Map.of()));
            }

            result.put(id, new MetaEnum(id, name, literals, Map.of()));
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readClasses» dentro de NeutralXmlMetamodelImporter. Entradas: root: ruta o ubicación utilizada por la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaClass> readClasses(Element root, Map<String, MetaEnum> enums, Map<String, MetaDataType> dataTypes) {
        List<MetaClass> result = new ArrayList<>();
        Map<String, String> enumNames = reverseNames(enums);
        Map<String, String> dataTypeNames = reverseNames(dataTypes);

        for(Element element : elements(root, "class")) {
            String id = attribute(element, "id", "class:" + ImportParsingSupport.safeId(element.getAttribute("name"), "class"));
            String name = attribute(element, "name", ImportParsingSupport.pascal(id, "Class"));
            MetaClassKind kind = parseClassKind(element.getAttribute("kind"));
            List<MetaAttribute> attributes = new ArrayList<>();

            for(Element attribute : directChildren(element, "attribute")) {
                String attributeName = attribute(attribute, "name", "value");
                String attributeId = attribute(attribute, "id", ImportParsingSupport.attributeId(id, attributeName));
                MetaTypeReference type = resolveType(attribute.getAttribute("type"), attribute.getAttribute("typeKind"), enumNames, dataTypeNames);
                int lower = ImportParsingSupport.integer(attribute.getAttribute("lowerBound"), 0);
                String upperText = attribute.getAttribute("upperBound");
                Integer upper = "*".equals(upperText) ? null : Integer.valueOf(ImportParsingSupport.integer(upperText, 1));
                attributes.add(new MetaAttribute(attributeId, attributeName, type, new Multiplicity(lower, upper), booleanAttribute(attribute, "ordered", true), booleanAttribute(attribute, "unique", true), attribute.getAttribute("defaultValue"), EcoreImportMetadata.attribute(Map.of(), booleanAttribute(attribute, "changeable", true))));
            }

            List<MetaOperation> operations = new ArrayList<>();
            for(Element operationElement : directChildren(element, "operation")) {
                String operationName = attribute(operationElement, "name", "operation");
                String operationId = attribute(operationElement, "id", "operation:" + id + ":" + ImportParsingSupport.safeId(operationName, "operation"));
                MetaTypeReference returnType = operationElement.hasAttribute("returnTypeKind") ? resolveSerializedType(operationElement.getAttribute("returnTypeKind"), operationElement.getAttribute("returnTypeId"), operationElement.getAttribute("returnExternalName"), operationElement.getAttribute("returnType"), enumNames, dataTypeNames) : null;
                Multiplicity returnMultiplicity = returnType == null ? null : new Multiplicity(ImportParsingSupport.integer(operationElement.getAttribute("returnLowerBound"), 1), "*".equals(operationElement.getAttribute("returnUpperBound")) ? null : Integer.valueOf(ImportParsingSupport.integer(operationElement.getAttribute("returnUpperBound"), 1)));
                List<MetaParameter> parameters = new ArrayList<>();
                for(Element parameterElement : directChildren(operationElement, "parameter")) {
                    String parameterName = attribute(parameterElement, "name", "parameter");
                    String parameterId = attribute(parameterElement, "id", "parameter:" + operationId + ":" + ImportParsingSupport.safeId(parameterName, "parameter"));
                    MetaTypeReference parameterType = resolveSerializedType(parameterElement.getAttribute("typeKind"), parameterElement.getAttribute("typeId"), parameterElement.getAttribute("externalName"), parameterElement.getAttribute("type"), enumNames, dataTypeNames);
                    int parameterLower = ImportParsingSupport.integer(parameterElement.getAttribute("lowerBound"), 1);
                    Integer parameterUpper = "*".equals(parameterElement.getAttribute("upperBound")) ? null : Integer.valueOf(ImportParsingSupport.integer(parameterElement.getAttribute("upperBound"), 1));
                    parameters.add(new MetaParameter(parameterId, parameterName, parameterType, new Multiplicity(parameterLower, parameterUpper), booleanAttribute(parameterElement, "ordered", true), booleanAttribute(parameterElement, "unique", true), Map.of()));
                }
                List<MetaTypeReference> exceptionTypes = new ArrayList<>();
                for(Element exceptionElement : directChildren(operationElement, "exception")) {
                    exceptionTypes.add(resolveSerializedType(exceptionElement.getAttribute("typeKind"), exceptionElement.getAttribute("typeId"), exceptionElement.getAttribute("externalName"), exceptionElement.getAttribute("type"), enumNames, dataTypeNames));
                }
                MetaOperationBehaviorKind behavior;
                try { behavior = MetaOperationBehaviorKind.valueOf(attribute(operationElement, "behaviorKind", "UNSPECIFIED")); } catch(IllegalArgumentException exception) { behavior = MetaOperationBehaviorKind.UNSPECIFIED; }
                operations.add(new MetaOperation(operationId, operationName, returnType, returnMultiplicity, booleanAttribute(operationElement, "ordered", true), booleanAttribute(operationElement, "unique", true), parameters, exceptionTypes, behavior, Map.of()));
            }

            result.add(new MetaClass(id, name, kind, attributes, operations, Map.of()));
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readRelations» dentro de NeutralXmlMetamodelImporter. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaRelation> readRelations(Element root) {
        List<MetaRelation> result = new ArrayList<>();
        List<Element> relationElements = new ArrayList<>();
        relationElements.addAll(elements(root, "relation"));
        relationElements.addAll(elements(root, "reference"));

        for(Element element : relationElements) {
            String id = attribute(element, "id", "relation:" + result.size());
            String kindText = attribute(element, "kind", "REFERENCE");
            MetaRelationKind kind = parseRelationKind(kindText);
            boolean changeable = booleanAttribute(element, "changeable", true);
            boolean resolveProxies = booleanAttribute(element, "resolveProxies", kind == MetaRelationKind.REFERENCE || kind == MetaRelationKind.AGGREGATION);
            result.add(new MetaRelation(id, element.getAttribute("name"), element.getAttribute("sourceTypeId"), element.getAttribute("targetTypeId"), kind, null, ImportParsingSupport.multiplicity(element.getAttribute("targetMultiplicity"), Multiplicity.optional()), element.getAttribute("oppositeRelationId"), true, true, EcoreImportMetadata.relation(Map.of(), changeable, resolveProxies)));
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readWarnings» dentro de NeutralXmlMetamodelImporter. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationWarning> readWarnings(Element root) {
        List<TransformationWarning> result = new ArrayList<>();

        for(Element element : elements(root, "warning")) {
            result.add(new TransformationWarning(attribute(element, "code", "IMPORTED_WARNING"), element.getAttribute("path"), element.getTextContent()));
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «resolveSerializedType» dentro de NeutralXmlMetamodelImporter. Entradas: kindText: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; externalName: dato de entrada necesario para la operación; displayName: dato de entrada necesario para la operación; enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference resolveSerializedType(String kindText, String targetId, String externalName, String displayName, Map<String, String> enumNames, Map<String, String> dataTypeNames) {
        MetaTypeReferenceKind kind;
        try { kind = MetaTypeReferenceKind.valueOf(kindText); } catch(RuntimeException exception) { return resolveType(displayName, kindText, enumNames, dataTypeNames); }
        if(kind == MetaTypeReferenceKind.EXTERNAL) return MetaTypeReference.external(externalName.isBlank() ? displayName : externalName);
        if(kind == MetaTypeReferenceKind.PRIMITIVE) return new MetaTypeReference(kind, targetId.isBlank() ? displayName.toUpperCase(java.util.Locale.ROOT) : targetId, "");
        if(kind == MetaTypeReferenceKind.ENUM) return MetaTypeReference.enumType(targetId.isBlank() ? enumNames.getOrDefault(displayName, displayName) : targetId);
        if(kind == MetaTypeReferenceKind.DATATYPE) return MetaTypeReference.dataType(targetId.isBlank() ? dataTypeNames.getOrDefault(displayName, displayName) : targetId);
        return MetaTypeReference.classType(targetId.isBlank() ? displayName : targetId);
    }

    /** Descripción: Obtiene la información correspondiente a «resolveType» dentro de NeutralXmlMetamodelImporter. Entradas: name: dato de entrada necesario para la operación; kindText: dato de entrada necesario para la operación; enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference resolveType(String name, String kindText, Map<String, String> enumNames, Map<String, String> dataTypeNames) {
        MetaTypeReferenceKind kind;

        try {
            kind = MetaTypeReferenceKind.valueOf(kindText);
        } catch(RuntimeException exception) {
            kind = MetaTypeReferenceKind.PRIMITIVE;
        }

        if(kind == MetaTypeReferenceKind.ENUM) {
            return MetaTypeReference.enumType(enumNames.getOrDefault(name, name));
        }

        if(kind == MetaTypeReferenceKind.DATATYPE) {
            return MetaTypeReference.dataType(dataTypeNames.getOrDefault(name, name));
        }

        if(kind == MetaTypeReferenceKind.CLASS) {
            return new MetaTypeReference(MetaTypeReferenceKind.CLASS, name, "");
        }

        if(kind == MetaTypeReferenceKind.EXTERNAL) {
            return MetaTypeReference.external(name);
        }

        return ImportParsingSupport.typeReference(name);
    }

    /** Descripción: Ejecuta la operación «reverseNames» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private <T> Map<String, String> reverseNames(Map<String, T> values) {
        Map<String, String> result = new LinkedHashMap<>();

        for(Map.Entry<String, T> entry : values.entrySet()) {
            String name = entry.getValue() instanceof MetaEnum metaEnum ? metaEnum.name() : ((MetaDataType) entry.getValue()).name();
            result.put(name, entry.getKey());
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «parseClassKind» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind parseClassKind(String value) {
        try {
            return MetaClassKind.valueOf(value);
        } catch(RuntimeException exception) {
            return MetaClassKind.CLASS;
        }
    }

    /** Descripción: Ejecuta la operación «parseRelationKind» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelationKind parseRelationKind(String value) {
        try {
            return MetaRelationKind.valueOf(value);
        } catch(RuntimeException exception) {
            return MetaRelationKind.REFERENCE;
        }
    }

    /** Descripción: Ejecuta la operación «booleanAttribute» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean booleanAttribute(Element element, String name, boolean fallback) {
        return element.hasAttribute(name) ? Boolean.parseBoolean(element.getAttribute(name)) : fallback;
    }

    /** Descripción: Ejecuta la operación «attribute» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attribute(Element element, String name, String fallback) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Descripción: Ejecuta la operación «elements» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: root: ruta o ubicación utilizada por la operación; tagName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Element> elements(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        List<Element> result = new ArrayList<>();

        for(int index = 0; index < nodes.getLength(); index += 1) {
            if(nodes.item(index) instanceof Element element) {
                result.add(element);
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «directChildren» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: parent: dato de entrada necesario para la operación; tagName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Element> directChildren(Element parent, String tagName) {
        return childElements(parent).stream().filter(item -> tagName.equals(item.getTagName())).toList();
    }

    /** Descripción: Ejecuta la operación «childElements» correspondiente a la responsabilidad de NeutralXmlMetamodelImporter. Entradas: parent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();

        for(int index = 0; index < nodes.getLength(); index += 1) {
            Node node = nodes.item(index);

            if(node instanceof Element element) {
                result.add(element);
            }
        }

        return result;
    }
}

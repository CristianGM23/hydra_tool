package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Deriva metamodelos neutrales desde esquemas y descriptores no Ecore. */
@Component
public class UpdateSiteSchemaMetamodelDeriver {
    private static final Pattern GRAPHQL_ENUM = Pattern.compile("(?s)\\benum\\s+(\\w+)\\s*\\{(.*?)\\}");
    private static final Pattern GRAPHQL_TYPE = Pattern.compile("(?s)\\b(type|interface)\\s+(\\w+)(?:\\s+implements\\s+([^\\{]+))?\\s*\\{(.*?)\\}");
    private static final Pattern GRAPHQL_FIELD = Pattern.compile("(?m)^\\s*(\\w+)\\s*(?:\\([^)]*\\))?\\s*:\\s*([\\[\\]!\\w]+)");
    private static final Pattern PROTO_MESSAGE = Pattern.compile("(?s)\\bmessage\\s+(\\w+)\\s*\\{(.*?)\\}");
    private static final Pattern PROTO_ENUM = Pattern.compile("(?s)\\benum\\s+(\\w+)\\s*\\{(.*?)\\}");
    private static final Pattern PROTO_FIELD = Pattern.compile("(?m)^\\s*(repeated\\s+)?([.\\w]+)\\s+(\\w+)\\s*=\\s*\\d+");
    private final ObjectMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteSchemaMetamodelDeriver con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteSchemaMetamodelDeriver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Descripción: Ejecuta la operación «derive» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: kind: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaModel derive(UpdateSiteArtifactKind kind, byte[] content, String targetId, String sourcePath) {
        return switch(kind) {
            case XSD -> deriveXsd(content, targetId, sourcePath);
            case JSON_SCHEMA, JSON -> deriveJson(content, targetId, sourcePath);
            case GRAPHQL -> deriveGraphql(content, targetId, sourcePath);
            case PROTO -> deriveProto(content, targetId, sourcePath);
            case UML -> deriveUml(content, targetId, sourcePath);
            case GENMODEL -> deriveGenModel(content, targetId, sourcePath);
            case PLUGIN_XML, FRAGMENT_XML -> derivePluginRegistry(content, targetId, sourcePath);
            case MANIFEST, BUILD_PROPERTIES -> derivePluginMetadata(content, targetId, sourcePath);
            default -> throw new IllegalArgumentException("Unsupported derived metamodel source: " + kind);
        };
    }

    /** Descripción: Ejecuta la operación «deriveXsd» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveXsd(byte[] content, String targetId, String sourcePath) {
        Document document = SecureXmlSupport.parse(content, "XSD metamodel source");
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        Map<String, Boolean> complexTypes = new LinkedHashMap<>();
        NodeList types = document.getElementsByTagNameNS("*", "complexType");
        for(int index = 0; index < types.getLength(); index++) {
            Element type = element(types.item(index));
            if(type == null || attr(type, "name").isBlank()) continue;
            String className = attr(type, "name");
            complexTypes.put(className, true);
            builder.addClass(className, "true".equalsIgnoreCase(attr(type, "abstract")) ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS);
        }
        for(int index = 0; index < types.getLength(); index++) {
            Element type = element(types.item(index));
            if(type == null || attr(type, "name").isBlank()) continue;
            String className = attr(type, "name");
            NodeList elements = type.getElementsByTagNameNS("*", "element");
            for(int child = 0; child < elements.getLength(); child++) {
                Element field = element(elements.item(child));
                if(field == null) continue;
                String name = attr(field, "name");
                if(name.isBlank()) name = localType(attr(field, "ref"));
                String fieldType = localType(attr(field, "type"));
                if(fieldType.isBlank()) fieldType = "String";
                Multiplicity multiplicity = multiplicity(attr(field, "minOccurs"), attr(field, "maxOccurs"));
                if(complexTypes.containsKey(fieldType)) builder.addRelation(className, name, fieldType, multiplicity, true);
                else builder.addAttribute(className, name, fieldType, multiplicity);
            }
            NodeList attributes = type.getElementsByTagNameNS("*", "attribute");
            for(int child = 0; child < attributes.getLength(); child++) {
                Element field = element(attributes.item(child));
                if(field == null) continue;
                String name = attr(field, "name");
                String fieldType = localType(attr(field, "type"));
                builder.addAttribute(className, name, fieldType.isBlank() ? "String" : fieldType,
                        "required".equalsIgnoreCase(attr(field, "use")) ? Multiplicity.one() : Multiplicity.optional());
            }
        }
        return builder.build(targetId, fileStem(sourcePath), Map.of("hydra.recovery.schema", "XSD"));
    }

    /** Descripción: Ejecuta la operación «deriveJson» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveJson(byte[] content, String targetId, String sourcePath) {
        try {
            JsonNode root = mapper.readTree(content);
            RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
            boolean schemaDocument = root.has("properties") || root.has("$schema")
                    || root.has("$defs") || root.has("definitions");
            String rootName = schemaDocument ? text(root.get("title")) : fileStem(sourcePath);
            if(rootName.isBlank()) rootName = fileStem(sourcePath);
            JsonNode definitions = root.get("$defs");
            if(definitions == null) definitions = root.get("definitions");
            if(definitions != null && definitions.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> iterator = definitions.properties().iterator();
                while(iterator.hasNext()) {
                    Map.Entry<String, JsonNode> entry = iterator.next();
                    deriveJsonObject(builder, entry.getKey(), entry.getValue());
                }
            }
            if(schemaDocument) {
                deriveJsonObject(builder, rootName, root);
                return builder.build(targetId, rootName, Map.of("hydra.recovery.schema", "JSON_SCHEMA"));
            }
            deriveJsonDataObject(builder, rootName, root);
            return builder.build(targetId, rootName, Map.of("hydra.recovery.schema", "JSON_DATA"));
        } catch(Exception exception) {
            throw new IllegalArgumentException("Could not derive metamodel from JSON schema", exception);
        }
    }

    /** Descripción: Ejecuta la operación «deriveJsonObject» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: builder: dato de entrada necesario para la operación; className: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deriveJsonObject(RecoveredMetamodelBuilder builder, String className, JsonNode node) {
        builder.addClass(className, MetaClassKind.CLASS);
        JsonNode properties = node == null ? null : node.get("properties");
        if(properties == null || !properties.isObject()) return;
        java.util.Set<String> required = new java.util.LinkedHashSet<>();
        JsonNode requiredNode = node.get("required");
        if(requiredNode != null && requiredNode.isArray()) requiredNode.forEach(item -> required.add(item.asText()));
        for(Map.Entry<String, JsonNode> entry : properties.properties()) {
            JsonNode property = entry.getValue();
            boolean many = "array".equals(text(property.get("type")));
            JsonNode effective = many ? property.get("items") : property;
            String reference = referenceName(text(effective == null ? null : effective.get("$ref")));
            String type = text(effective == null ? null : effective.get("type"));
            Multiplicity multiplicity = many ? Multiplicity.many() : required.contains(entry.getKey()) ? Multiplicity.one() : Multiplicity.optional();
            if(!reference.isBlank() || "object".equals(type)) {
                String target = reference.isBlank() ? capitalize(entry.getKey()) : reference;
                builder.addRelation(className, entry.getKey(), target, multiplicity, true);
            } else {
                builder.addAttribute(className, entry.getKey(), jsonType(type, effective), multiplicity);
            }
        }
    }

    /** Descripción: Ejecuta la operación «deriveJsonDataObject» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: builder: dato de entrada necesario para la operación; className: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deriveJsonDataObject(RecoveredMetamodelBuilder builder, String className, JsonNode node) {
        builder.addClass(className, MetaClassKind.CLASS);
        if(node == null || !node.isObject()) return;
        for(Map.Entry<String, JsonNode> entry : node.properties()) {
            JsonNode value = entry.getValue();
            if(value != null && value.isArray()) {
                JsonNode sample = value.isEmpty() ? null : value.get(0);
                if(sample != null && sample.isObject()) {
                    String target = capitalize(entry.getKey());
                    builder.addRelation(className, entry.getKey(), target, Multiplicity.many(), true);
                    deriveJsonDataObject(builder, target, sample);
                } else {
                    builder.addAttribute(className, entry.getKey(), jsonValueType(sample), Multiplicity.many());
                }
            } else if(value != null && value.isObject()) {
                String target = capitalize(entry.getKey());
                builder.addRelation(className, entry.getKey(), target, Multiplicity.optional(), true);
                deriveJsonDataObject(builder, target, value);
            } else {
                builder.addAttribute(className, entry.getKey(), jsonValueType(value), Multiplicity.optional());
            }
        }
    }

    /** Descripción: Ejecuta la operación «jsonValueType» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonValueType(JsonNode value) {
        if(value == null || value.isNull()) return "Object";
        if(value.isBoolean()) return "Boolean";
        if(value.isIntegralNumber()) return "Long";
        if(value.isFloatingPointNumber()) return "Double";
        if(value.isTextual()) return "String";
        return "Object";
    }

    /** Descripción: Ejecuta la operación «deriveGraphql» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveGraphql(byte[] content, String targetId, String sourcePath) {
        String text = new String(content, StandardCharsets.UTF_8);
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        Matcher enumMatcher = GRAPHQL_ENUM.matcher(text);
        while(enumMatcher.find()) {
            List<String> literals = Pattern.compile("\\b[A-Z][A-Z0-9_]*\\b").matcher(enumMatcher.group(2)).results().map(MatchResult -> MatchResult.group()).toList();
            builder.addEnum(enumMatcher.group(1), literals);
        }
        Matcher typeMatcher = GRAPHQL_TYPE.matcher(text);
        while(typeMatcher.find()) {
            String className = typeMatcher.group(2);
            builder.addClass(className, "interface".equals(typeMatcher.group(1)) ? MetaClassKind.INTERFACE : MetaClassKind.CLASS);
            if(typeMatcher.group(3) != null) {
                for(String parent : typeMatcher.group(3).split("[&,]")) if(!parent.isBlank()) builder.addInheritance(className, parent.trim());
            }
            Matcher fieldMatcher = GRAPHQL_FIELD.matcher(typeMatcher.group(4));
            while(fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                String fieldType = fieldMatcher.group(2);
                boolean many = fieldType.startsWith("[");
                boolean required = fieldType.endsWith("!");
                String baseType = fieldType.replaceAll("[\\[\\]!]", "");
                Multiplicity multiplicity = many ? Multiplicity.many() : required ? Multiplicity.one() : Multiplicity.optional();
                if(isGraphqlScalar(baseType)) builder.addAttribute(className, fieldName, baseType, multiplicity);
                else builder.addRelation(className, fieldName, baseType, multiplicity, false);
            }
        }
        return builder.build(targetId, fileStem(sourcePath), Map.of("hydra.recovery.schema", "GRAPHQL"));
    }

    /** Descripción: Ejecuta la operación «deriveProto» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveProto(byte[] content, String targetId, String sourcePath) {
        String text = new String(content, StandardCharsets.UTF_8);
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        Matcher enumMatcher = PROTO_ENUM.matcher(text);
        while(enumMatcher.find()) {
            List<String> literals = Pattern.compile("(?m)^\\s*(\\w+)\\s*=").matcher(enumMatcher.group(2)).results().map(result -> result.group(1)).toList();
            builder.addEnum(enumMatcher.group(1), literals);
        }
        Matcher messageMatcher = PROTO_MESSAGE.matcher(text);
        while(messageMatcher.find()) {
            String className = messageMatcher.group(1);
            builder.addClass(className, MetaClassKind.CLASS);
            Matcher fieldMatcher = PROTO_FIELD.matcher(messageMatcher.group(2));
            while(fieldMatcher.find()) {
                boolean many = fieldMatcher.group(1) != null;
                String fieldType = fieldMatcher.group(2);
                String fieldName = fieldMatcher.group(3);
                Multiplicity multiplicity = many ? Multiplicity.many() : Multiplicity.optional();
                if(isProtoScalar(fieldType)) builder.addAttribute(className, fieldName, fieldType, multiplicity);
                else builder.addRelation(className, fieldName, localType(fieldType), multiplicity, false);
            }
        }
        return builder.build(targetId, fileStem(sourcePath), Map.of("hydra.recovery.schema", "PROTO"));
    }

    /** Descripción: Ejecuta la operación «deriveUml» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveUml(byte[] content, String targetId, String sourcePath) {
        Document document = SecureXmlSupport.parse(content, "UML metamodel source");
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        NodeList nodes = document.getElementsByTagName("packagedElement");
        for(int index = 0; index < nodes.getLength(); index++) {
            Element element = element(nodes.item(index));
            if(element == null) continue;
            String xmiType = element.getAttributeNS("http://www.omg.org/XMI", "type");
            if(xmiType.isBlank()) xmiType = attr(element, "xmi:type");
            String name = attr(element, "name");
            if(name.isBlank()) continue;
            if(xmiType.endsWith("Enumeration")) {
                NodeList literals = element.getElementsByTagName("ownedLiteral");
                List<String> values = new ArrayList<>();
                for(int child = 0; child < literals.getLength(); child++) {
                    Element literal = element(literals.item(child));
                    if(literal != null && !attr(literal, "name").isBlank()) values.add(attr(literal, "name"));
                }
                builder.addEnum(name, values);
            } else if(xmiType.endsWith("Class") || xmiType.endsWith("Interface")) {
                builder.addClass(name, xmiType.endsWith("Interface") ? MetaClassKind.INTERFACE
                        : "true".equals(attr(element, "isAbstract")) ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS);
                NodeList attributes = element.getElementsByTagName("ownedAttribute");
                for(int child = 0; child < attributes.getLength(); child++) {
                    Element attribute = element(attributes.item(child));
                    if(attribute == null || attr(attribute, "name").isBlank()) continue;
                    String type = attr(attribute, "type");
                    if(type.isBlank()) type = attr(attribute, "href");
                    builder.addAttribute(name, attr(attribute, "name"), localType(type), Multiplicity.optional());
                }
                NodeList operations = element.getElementsByTagName("ownedOperation");
                for(int child = 0; child < operations.getLength(); child++) {
                    Element operation = element(operations.item(child));
                    if(operation != null && !attr(operation, "name").isBlank()) builder.addOperation(name, attr(operation, "name"), "void", List.of());
                }
            }
        }
        return builder.build(targetId, fileStem(sourcePath), Map.of("hydra.recovery.schema", "UML"));
    }

    /** Descripción: Ejecuta la operación «deriveGenModel» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveGenModel(byte[] content, String targetId, String sourcePath) {
        Document document = SecureXmlSupport.parse(content, "GenModel recovery source");
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        NodeList packages = document.getElementsByTagNameNS("*", "genPackages");
        for(int index = 0; index < packages.getLength(); index++) {
            Element genPackage = element(packages.item(index));
            if(genPackage == null) continue;
            NodeList classes = genPackage.getElementsByTagNameNS("*", "genClasses");
            for(int child = 0; child < classes.getLength(); child++) {
                Element genClass = element(classes.item(child));
                if(genClass == null) continue;
                String name = fragmentName(attr(genClass, "ecoreClass"));
                if(name.isBlank()) name = attr(genClass, "className");
                if(name.isBlank()) continue;
                builder.addClass(name, MetaClassKind.CLASS);
                NodeList features = genClass.getElementsByTagNameNS("*", "genFeatures");
                for(int featureIndex = 0; featureIndex < features.getLength(); featureIndex++) {
                    Element feature = element(features.item(featureIndex));
                    if(feature == null) continue;
                    String featureName = fragmentName(attr(feature, "ecoreFeature"));
                    if(featureName.contains("/")) featureName = featureName.substring(featureName.lastIndexOf('/') + 1);
                    if(!featureName.isBlank()) builder.addAttribute(name, featureName, "Object", Multiplicity.optional());
                }
                NodeList operations = genClass.getElementsByTagNameNS("*", "genOperations");
                for(int operationIndex = 0; operationIndex < operations.getLength(); operationIndex++) {
                    Element operation = element(operations.item(operationIndex));
                    String operationName = operation == null ? "" : fragmentName(attr(operation, "ecoreOperation"));
                    if(!operationName.isBlank()) builder.addOperation(name, operationName, "void", List.of());
                }
            }
        }
        return builder.build(targetId, fileStem(sourcePath), Map.of("hydra.recovery.schema", "GENMODEL"));
    }

    /** Descripción: Ejecuta la operación «derivePluginRegistry» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel derivePluginRegistry(byte[] content, String targetId, String sourcePath) {
        Document document = SecureXmlSupport.parse(content, "plugin registry recovery source");
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        NodeList packages = document.getElementsByTagName("package");
        for(int index = 0; index < packages.getLength(); index++) {
            Element declaration = element(packages.item(index));
            if(declaration == null) continue;
            String className = attr(declaration, "class");
            if(className.isBlank()) continue;
            String packageName = className.endsWith("Package") ? className.substring(0, className.length() - "Package".length()) : className;
            builder.addClass(localType(packageName) + "Root", MetaClassKind.CLASS);
        }
        return builder.build(targetId, fileStem(sourcePath), Map.of("hydra.recovery.schema", "PLUGIN_REGISTRY"));
    }


    /** Descripción: Ejecuta la operación «derivePluginMetadata» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; sourcePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel derivePluginMetadata(byte[] content, String targetId, String sourcePath) {
        String text = new String(content, StandardCharsets.UTF_8);
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        Pattern packagePattern = Pattern.compile("(?m)^(?:Export-Package|Bundle-SymbolicName|source\\.\\.?)\\s*[:=]\\s*([^\\r\\n]+)");
        Matcher matcher = packagePattern.matcher(text);
        while(matcher.find()) {
            for(String token : matcher.group(1).split(",")) {
                String packageName = token.replaceAll(";.*$", "").trim();
                if(packageName.isBlank()) continue;
                builder.addClass(localType(packageName) + "Root", MetaClassKind.CLASS);
            }
        }
        return builder.build(targetId, fileStem(sourcePath), Map.of("hydra.recovery.schema", "PLUGIN_METADATA"));
    }

    /** Descripción: Ejecuta la operación «jsonType» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: type: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonType(String type, JsonNode node) {
        if(type == null || type.isBlank()) return "Object";
        if(type.equals("integer")) return "Integer";
        if(type.equals("number")) return "Double";
        if(type.equals("boolean")) return "Boolean";
        if(type.equals("string")) {
            String format = text(node == null ? null : node.get("format"));
            return format.equals("date") || format.equals("date-time") ? "Date" : "String";
        }
        return type;
    }

    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: min: dato de entrada necesario para la operación; max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Multiplicity multiplicity(String min, String max) {
        int lower = min == null || min.isBlank() ? 1 : parseInt(min, 1);
        if("unbounded".equals(max)) return new Multiplicity(lower, null);
        int upper = max == null || max.isBlank() ? 1 : parseInt(max, 1);
        return new Multiplicity(lower, upper);
    }

    /** Descripción: Ejecuta la operación «parseInt» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch(NumberFormatException exception) { return fallback; }
    }

    /** Descripción: Evalúa la condición «isGraphqlScalar» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isGraphqlScalar(String type) {
        return List.of("String", "Int", "Float", "Boolean", "ID").contains(type);
    }

    /** Descripción: Evalúa la condición «isProtoScalar» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isProtoScalar(String type) {
        return List.of("double", "float", "int32", "int64", "uint32", "uint64", "sint32", "sint64",
                "fixed32", "fixed64", "sfixed32", "sfixed64", "bool", "string", "bytes").contains(type);
    }

    /** Descripción: Ejecuta la operación «referenceName» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: reference: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String referenceName(String reference) {
        if(reference == null || reference.isBlank()) return "";
        return reference.substring(reference.lastIndexOf('/') + 1);
    }

    /** Descripción: Ejecuta la operación «fragmentName» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String fragmentName(String value) {
        if(value == null) return "";
        int marker = value.indexOf("#//");
        return marker >= 0 ? value.substring(marker + 3) : localType(value);
    }

    /** Descripción: Ejecuta la operación «localType» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String localType(String value) {
        if(value == null) return "";
        String result = value;
        if(result.contains("#//")) result = result.substring(result.indexOf("#//") + 3);
        if(result.contains("#")) result = result.substring(result.lastIndexOf('#') + 1);
        if(result.contains(":")) result = result.substring(result.lastIndexOf(':') + 1);
        if(result.contains("/")) result = result.substring(result.lastIndexOf('/') + 1);
        if(result.contains(".")) result = result.substring(result.lastIndexOf('.') + 1);
        return result.replaceAll("[^A-Za-z0-9_]", "");
    }

    /** Descripción: Ejecuta la operación «fileStem» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileStem(String path) {
        String name = path == null ? "RecoveredMetamodel" : path;
        if(name.contains("!/")) name = name.substring(name.lastIndexOf("!/") + 2);
        if(name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.indexOf('.');
        if(dot > 0) name = name.substring(0, dot);
        return capitalize(name.isBlank() ? "RecoveredMetamodel" : name);
    }

    /** Descripción: Ejecuta la operación «capitalize» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String capitalize(String value) {
        return value == null || value.isBlank() ? "Recovered" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String text(JsonNode node) { return node == null || node.isNull() ? "" : node.asText(""); }
    /** Descripción: Ejecuta la operación «element» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element element(Node node) { return node instanceof Element value ? value : null; }
    /** Descripción: Ejecuta la operación «attr» correspondiente a la responsabilidad de UpdateSiteSchemaMetamodelDeriver. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attr(Element element, String name) { return element.hasAttribute(name) ? element.getAttribute(name).trim() : ""; }
}

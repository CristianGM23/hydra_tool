package com.example.editor.metamodel.importing.importers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeStyleDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeStyleDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

/**
 * Importa el diagrama UML draw.io generado por la herramienta.
 */
@Component
public class DrawioMetamodelImporter implements MetamodelImporter {

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("^\\+\\s*([^:]+?)\\s*:\\s*([^\\[]+?)\\s*\\[([^]]+)]\\s*$");
    private static final Pattern OPERATION_PATTERN = Pattern.compile("^\\+\\s*([^()]+?)\\s*\\(([^)]*)\\)\\s*:\\s*([^<]+?)(?:\\s*<<([A-Za-z_]+)>>)?\\s*$");
    private static final Pattern LITERAL_PATTERN = Pattern.compile("^(.+?)\\s*=\\s*(-?\\d+)\\s*$");

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.DRAWIO;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        Document document = SecureXmlSupport.parse(source.content(), "draw.io metamodel: " + source.fileName());
        Map<String, Element> cells = indexCells(document);
        Map<String, ClassBuilder> classes = new LinkedHashMap<>();
        Map<String, EnumBuilder> enums = new LinkedHashMap<>();
        Map<String, DataTypeBuilder> dataTypes = new LinkedHashMap<>();
        List<NodeDesign> diagramNodes = new ArrayList<>();
        Map<String, Multiplicity> relationMultiplicities = new LinkedHashMap<>();
        identifyClassifiers(cells, classes, enums, dataTypes, diagramNodes);
        readChildren(cells, classes, enums, dataTypes, relationMultiplicities);
        List<MetaRelation> relations = new ArrayList<>();
        List<EdgeDesign> diagramEdges = readRelations(cells, relationMultiplicities, relations, diagramNodes);
        Map<String, String> enumNameIds = new LinkedHashMap<>();
        Map<String, String> dataTypeNameIds = new LinkedHashMap<>();
        Map<String, String> classNameIds = new LinkedHashMap<>();
        enums.values().forEach(item -> enumNameIds.put(item.name, item.id));
        dataTypes.values().forEach(item -> dataTypeNameIds.put(item.name, item.id));
        classes.values().forEach(item -> classNameIds.put(item.name, item.id));
        List<MetaClass> metaClasses = classes.values().stream().map(item -> item.build(enumNameIds, dataTypeNameIds, classNameIds)).toList();
        List<MetaEnum> metaEnums = enums.values().stream().map(EnumBuilder::build).toList();
        List<MetaDataType> metaDataTypes = dataTypes.values().stream().map(DataTypeBuilder::build).toList();
        String diagramId = readDiagramId(document, source.fileName());
        MetaModel metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, diagramId, ImportParsingSupport.pascal(diagramId, "ImportedMetamodel"), metaClasses, metaEnums, metaDataTypes, relations, EcoreImportMetadata.model(Map.of("sourceFormat", "drawio"), diagramId + ".ecore", "urn:imported:" + diagramId, diagramId.replace('-', '_')));
        MetamodelDiagramDocument diagram = new MetamodelDiagramDocument(diagramId, diagramNodes, diagramEdges);
        List<TransformationWarning> warnings = List.of(
                new TransformationWarning("IMPORT_DRAWIO_NAMESPACE_DEFAULTED", "$.namespaceUri", "draw.io does not store the original namespace in the current template, so a stable imported namespace was generated"),
                new TransformationWarning("IMPORT_DRAWIO_FEATURE_OPTIONS_DEFAULTED", "$.classes", "draw.io does not store every Ecore feature flag, so ordered, unique and changeable use safe defaults"));
        return new MetamodelImportOutput(metamodel, diagram, warnings);
    }

    /** Descripción: Ejecuta la operación «indexCells» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Element> indexCells(Document document) {
        NodeList nodes = document.getElementsByTagName("mxCell");
        Map<String, Element> result = new LinkedHashMap<>();

        for(int index = 0; index < nodes.getLength(); index += 1) {
            if(nodes.item(index) instanceof Element element && !element.getAttribute("id").isBlank()) {
                result.put(element.getAttribute("id"), element);
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «identifyClassifiers» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: cells: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; diagramNodes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void identifyClassifiers(Map<String, Element> cells, Map<String, ClassBuilder> classes, Map<String, EnumBuilder> enums, Map<String, DataTypeBuilder> dataTypes, List<NodeDesign> diagramNodes) {
        for(Element cell : cells.values()) {
            String style = cell.getAttribute("style");

            if(!"1".equals(cell.getAttribute("vertex")) || !"1".equals(cell.getAttribute("parent")) || !style.contains("swimlane")) {
                continue;
            }

            String id = cell.getAttribute("id");
            String value = cell.getAttribute("value");
            String normalizedValue = value.replace("\r", "").trim();
            String name = lastNonBlankLine(normalizedValue);
            MetaClassKind kind = classKind(normalizedValue);

            if(normalizedValue.contains("«enumeration»")) {
                enums.put(id, new EnumBuilder(id, name));
            } else if(normalizedValue.contains("«datatype»")) {
                dataTypes.put(id, new DataTypeBuilder(id, name));
            } else {
                classes.put(id, new ClassBuilder(id, name, kind));
            }

            Element geometry = firstChild(cell, "mxGeometry");
            Map<String, String> styleMap = parseStyle(style);
            NodeStyleDesign nodeStyle = new NodeStyleDesign(styleMap.get("swimlaneFillColor"), styleMap.get("fillColor"), styleMap.get("strokeColor"), styleMap.get("fontColor"), styleMap.get("fontFamily"), number(styleMap.get("fontSize")), null, number(styleMap.get("strokeWidth")), null);
            diagramNodes.add(new NodeDesign(id, decimal(geometry, "x", 60), decimal(geometry, "y", 60), decimal(geometry, "width", 240), decimal(geometry, "height", 140), nodeStyle));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readChildren» dentro de DataTypeBuilder. Entradas: cells: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; relationMultiplicities: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readChildren(Map<String, Element> cells, Map<String, ClassBuilder> classes, Map<String, EnumBuilder> enums, Map<String, DataTypeBuilder> dataTypes, Map<String, Multiplicity> relationMultiplicities) {
        for(Element cell : cells.values()) {
            String parent = cell.getAttribute("parent");
            String value = cell.getAttribute("value").trim();

            if(classes.containsKey(parent)) {
                readClassRow(classes.get(parent), cell.getAttribute("id"), value, relationMultiplicities);
            } else if(enums.containsKey(parent)) {
                readEnumRow(enums.get(parent), cell.getAttribute("id"), value);
            } else if(dataTypes.containsKey(parent)) {
                readDataTypeRow(dataTypes.get(parent), value);
            }
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readClassRow» dentro de DataTypeBuilder. Entradas: owner: dato de entrada necesario para la operación; cellId: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse; relationMultiplicities: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readClassRow(ClassBuilder owner, String cellId, String value, Map<String, Multiplicity> relationMultiplicities) {
        if(value.isBlank()) {
            return;
        }

        String attributePrefix = owner.id + "-attribute-";
        String relationPrefix = owner.id + "-relation-";
        String operationPrefix = owner.id + "-operation-";
        Matcher attributeMatcher = ATTRIBUTE_PATTERN.matcher(value);
        Matcher operationMatcher = OPERATION_PATTERN.matcher(value);

        if(cellId.startsWith(attributePrefix) && attributeMatcher.matches()) {
            String id = cellId.substring(attributePrefix.length());
            owner.attributes.add(new AttributeBuilder(id, attributeMatcher.group(1).trim(), attributeMatcher.group(2).trim(), ImportParsingSupport.multiplicity(attributeMatcher.group(3), Multiplicity.one())));
        } else if(cellId.startsWith(relationPrefix) && attributeMatcher.matches()) {
            String relationId = cellId.substring(relationPrefix.length());
            relationMultiplicities.put(relationId, ImportParsingSupport.multiplicity(attributeMatcher.group(3), Multiplicity.optional()));
        } else if(cellId.startsWith(operationPrefix) && operationMatcher.matches()) {
            String id = cellId.substring(operationPrefix.length());
            owner.operations.add(OperationBuilder.parse(id, operationMatcher));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readEnumRow» dentro de DataTypeBuilder. Entradas: owner: dato de entrada necesario para la operación; cellId: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readEnumRow(EnumBuilder owner, String cellId, String value) {
        Matcher matcher = LITERAL_PATTERN.matcher(value);

        if(!matcher.matches()) {
            return;
        }

        String prefix = owner.id + "-";
        String id = cellId.startsWith(prefix) ? cellId.substring(prefix.length()) : ImportParsingSupport.literalId(owner.id, matcher.group(1).trim());
        owner.literals.add(new MetaEnumLiteral(id, matcher.group(1).trim(), ImportParsingSupport.integer(matcher.group(2), owner.literals.size()), Map.of()));
    }

    /** Descripción: Obtiene la información correspondiente a «readDataTypeRow» dentro de DataTypeBuilder. Entradas: owner: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readDataTypeRow(DataTypeBuilder owner, String value) {
        if(value.startsWith("+ implementationTypeName :")) {
            owner.implementationTypeName = value.substring(value.indexOf(':') + 1).trim();
        } else if(value.startsWith("+ serializable :")) {
            int equals = value.lastIndexOf('=');
            owner.serializable = equals >= 0 && Boolean.parseBoolean(value.substring(equals + 1).trim());
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readRelations» dentro de DataTypeBuilder. Entradas: cells: dato de entrada necesario para la operación; relationMultiplicities: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación; diagramNodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<EdgeDesign> readRelations(Map<String, Element> cells, Map<String, Multiplicity> relationMultiplicities, List<MetaRelation> relations, List<NodeDesign> diagramNodes) {
        List<EdgeDesign> result = new ArrayList<>();
        Map<String, NodeDesign> nodes = new LinkedHashMap<>();
        diagramNodes.forEach(item -> nodes.put(item.elementId(), item));

        for(Element cell : cells.values()) {
            if(!"1".equals(cell.getAttribute("edge"))) {
                continue;
            }

            String id = cell.getAttribute("id");
            String sourceId = cell.getAttribute("source");
            String targetId = cell.getAttribute("target");

            if(sourceId.isBlank() || targetId.isBlank()) {
                continue;
            }

            Map<String, String> style = parseStyle(cell.getAttribute("style"));
            MetaRelationKind kind = relationKind(style);
            String label = cell.getAttribute("value");
            Multiplicity targetMultiplicity = relationMultiplicities.getOrDefault(id, kind == MetaRelationKind.INHERITANCE || kind == MetaRelationKind.IMPLEMENTATION || kind == MetaRelationKind.DEPENDENCY ? null : Multiplicity.optional());
            relations.add(new MetaRelation(id, label, sourceId, targetId, kind, null, targetMultiplicity, "", true, true, EcoreImportMetadata.relation(Map.of("sourceFormat", "drawio"), true, kind == MetaRelationKind.REFERENCE || kind == MetaRelationKind.AGGREGATION)));
            EdgeStyleDesign edgeStyle = new EdgeStyleDesign(style.get("strokeColor"), style.get("fontColor"), style.get("fontFamily"), number(style.get("fontSize")), null, number(style.get("strokeWidth")), lineStyle(style));
            String sourceHandle = handle(style, true);
            String targetHandle = handle(style, false);
            List<RoutePointDesign> routePoints = readRoutePoints(cell, nodes.get(sourceId), nodes.get(targetId), sourceHandle, targetHandle);
            result.add(new EdgeDesign(id, sourceHandle, targetHandle, kind.name(), sourceId, targetId, label, edgeStyle, routePoints, null));
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «relationKind» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelationKind relationKind(Map<String, String> style) {
        String startArrow = style.getOrDefault("startArrow", "");
        String endArrow = style.getOrDefault("endArrow", "");
        boolean dashed = "1".equals(style.get("dashed"));

        if("block".equals(endArrow)) {
            return dashed ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
        }

        if(startArrow.startsWith("diamond")) {
            return "1".equals(style.get("startFill")) ? MetaRelationKind.CONTAINMENT : MetaRelationKind.AGGREGATION;
        }

        if(dashed) {
            return MetaRelationKind.DEPENDENCY;
        }

        return MetaRelationKind.REFERENCE;
    }

    /** Descripción: Procesa la operación «handle» coordinando las responsabilidades de DataTypeBuilder. Entradas: style: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String handle(Map<String, String> style, boolean source) {
        String prefix = source ? "exit" : "entry";
        double x = ImportParsingSupport.decimal(style.get(prefix + "X"), source ? 1 : 0);
        double y = ImportParsingSupport.decimal(style.get(prefix + "Y"), 0.5);

        if(x <= 0.01) {
            return "left-" + percentage(y);
        }

        if(x >= 0.99) {
            return "right-" + percentage(y);
        }

        if(y <= 0.01) {
            return "top-" + percentage(x);
        }

        if(y >= 0.99) {
            return "bottom-" + percentage(x);
        }

        return source ? "right-50" : "left-50";
    }

    /** Descripción: Ejecuta la operación «percentage» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String percentage(double value) {
        double percentage = Math.round(value * 1000.0) / 10.0;

        if(percentage == Math.floor(percentage)) {
            return Integer.toString((int) percentage);
        }

        return Double.toString(percentage);
    }

    /** Descripción: Obtiene la información correspondiente a «readRoutePoints» dentro de DataTypeBuilder. Entradas: cell: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; sourceHandle: dato de entrada necesario para la operación; targetHandle: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RoutePointDesign> readRoutePoints(Element cell, NodeDesign source, NodeDesign target, String sourceHandle, String targetHandle) {
        if(source == null || target == null) {
            return List.of();
        }

        Element geometry = firstChild(cell, "mxGeometry");

        if(geometry == null) {
            return List.of();
        }

        List<RoutePointDesign> result = new ArrayList<>();
        result.add(handlePoint(source, sourceHandle));
        NodeList arrays = geometry.getElementsByTagName("Array");

        for(int arrayIndex = 0; arrayIndex < arrays.getLength(); arrayIndex += 1) {
            if(!(arrays.item(arrayIndex) instanceof Element array) || !"points".equals(array.getAttribute("as"))) {
                continue;
            }

            NodeList points = array.getElementsByTagName("mxPoint");

            for(int pointIndex = 0; pointIndex < points.getLength(); pointIndex += 1) {
                if(points.item(pointIndex) instanceof Element point) {
                    result.add(new RoutePointDesign(decimal(point, "x", 0), decimal(point, "y", 0)));
                }
            }
        }

        result.add(handlePoint(target, targetHandle));
        return result.size() > 2 ? List.copyOf(result) : List.of();
    }

    /** Descripción: Procesa la operación «handlePoint» coordinando las responsabilidades de DataTypeBuilder. Entradas: node: dato de entrada necesario para la operación; handle: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RoutePointDesign handlePoint(NodeDesign node, String handle) {
        String side = handle == null ? "right" : handle.split("-")[0];
        double ratio = handlePercentage(handle) / 100.0;

        if("left".equals(side)) {
            return new RoutePointDesign(node.x(), node.y() + node.height() * ratio);
        }

        if("right".equals(side)) {
            return new RoutePointDesign(node.x() + node.width(), node.y() + node.height() * ratio);
        }

        if("top".equals(side)) {
            return new RoutePointDesign(node.x() + node.width() * ratio, node.y());
        }

        return new RoutePointDesign(node.x() + node.width() * ratio, node.y() + node.height());
    }

    /** Descripción: Procesa la operación «handlePercentage» coordinando las responsabilidades de DataTypeBuilder. Entradas: handle: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double handlePercentage(String handle) {
        if(handle == null || handle.isBlank()) {
            return 50;
        }

        String[] parts = handle.split("-");

        if(parts.length < 2) {
            return 50;
        }

        return ImportParsingSupport.decimal(parts[1], 50);
    }

    /** Descripción: Ejecuta la operación «lineStyle» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String lineStyle(Map<String, String> style) {
        if(!"1".equals(style.get("dashed"))) {
            return "SOLID";
        }

        String pattern = style.getOrDefault("dashPattern", "");
        return pattern.contains("1 3") || pattern.contains("1 4") ? "DOTTED" : "DASHED";
    }

    /** Descripción: Ejecuta la operación «parseStyle» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> parseStyle(String value) {
        Map<String, String> result = new LinkedHashMap<>();

        for(String part : value.split(";")) {
            int separator = part.indexOf('=');

            if(separator > 0) {
                result.put(part.substring(0, separator), part.substring(separator + 1));
            }
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «firstChild» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: parent: dato de entrada necesario para la operación; tagName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element firstChild(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        return children.getLength() == 0 ? null : (Element) children.item(0);
    }

    /** Descripción: Ejecuta la operación «decimal» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private double decimal(Element element, String name, double fallback) {
        return element == null ? fallback : ImportParsingSupport.decimal(element.getAttribute(name), fallback);
    }

    /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Double number(String value) {
        return value == null || value.isBlank() ? null : Double.valueOf(ImportParsingSupport.decimal(value, 0));
    }

    /** Descripción: Ejecuta la operación «classKind» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind classKind(String value) {
        if(value.contains("«interface»")) {
            return MetaClassKind.INTERFACE;
        }

        if(value.contains("«abstract»")) {
            return MetaClassKind.ABSTRACT_CLASS;
        }

        return MetaClassKind.CLASS;
    }

    /** Descripción: Ejecuta la operación «lastNonBlankLine» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String lastNonBlankLine(String value) {
        String result = "ImportedElement";

        for(String line : value.split("\\R")) {
            if(!line.isBlank() && !line.startsWith("«")) {
                result = line.trim();
            }
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readDiagramId» dentro de DataTypeBuilder. Entradas: document: dato de entrada necesario para la operación; fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String readDiagramId(Document document, String fileName) {
        NodeList diagrams = document.getElementsByTagName("diagram");

        if(diagrams.getLength() > 0 && diagrams.item(0) instanceof Element element && !element.getAttribute("id").isBlank()) {
            return ImportParsingSupport.safeId(element.getAttribute("id"), "imported-metamodel");
        }

        return ImportParsingSupport.safeId(ImportParsingSupport.fileBaseName(fileName), "imported-metamodel");
    }

    private static final class AttributeBuilder {
        private final String id;
        private final String name;
        private final String typeName;
        private final Multiplicity multiplicity;

        /** Descripción: Inicializa una instancia de AttributeBuilder con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; typeName: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private AttributeBuilder(String id, String name, String typeName, Multiplicity multiplicity) {
            this.id = id;
            this.name = name;
            this.typeName = typeName;
            this.multiplicity = multiplicity;
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: enumNameIds: dato de entrada necesario para la operación; dataTypeNameIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaAttribute build(Map<String, String> enumNameIds, Map<String, String> dataTypeNameIds) {
            MetaTypeReference type;

            if(enumNameIds.containsKey(typeName)) {
                type = MetaTypeReference.enumType(enumNameIds.get(typeName));
            } else if(dataTypeNameIds.containsKey(typeName)) {
                type = MetaTypeReference.dataType(dataTypeNameIds.get(typeName));
            } else {
                type = ImportParsingSupport.typeReference(typeName);
            }

            return new MetaAttribute(id, name, type, multiplicity, true, true, "", Map.of());
        }
    }

    private static final class ParameterBuilder {
        private final String name;
        private final String typeName;

        /** Descripción: Inicializa una instancia de ParameterBuilder con sus dependencias y datos de entrada. Entradas: name: dato de entrada necesario para la operación; typeName: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ParameterBuilder(String name, String typeName) {
            this.name = name;
            this.typeName = typeName;
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: operationId: identificador que delimita el recurso objetivo; index: dato de entrada necesario para la operación; enumNameIds: dato de entrada necesario para la operación; dataTypeNameIds: dato de entrada necesario para la operación; classNameIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaParameter build(String operationId, int index, Map<String, String> enumNameIds, Map<String, String> dataTypeNameIds, Map<String, String> classNameIds) {
            String parameterId = "parameter:" + operationId + ":" + ImportParsingSupport.safeId(name, "parameter-" + index);
            return new MetaParameter(parameterId, name, operationType(typeName, enumNameIds, dataTypeNameIds, classNameIds), Multiplicity.one(), true, true, Map.of("sourceFormat", "drawio"));
        }
    }

    private static final class OperationBuilder {
        private final String id;
        private final String name;
        private final String returnTypeName;
        private final List<ParameterBuilder> parameters;
        private final MetaOperationBehaviorKind behaviorKind;

        /** Descripción: Inicializa una instancia de OperationBuilder con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; returnTypeName: dato de entrada necesario para la operación; parameters: dato de entrada necesario para la operación; behaviorKind: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private OperationBuilder(String id, String name, String returnTypeName, List<ParameterBuilder> parameters, MetaOperationBehaviorKind behaviorKind) {
            this.id = id;
            this.name = name;
            this.returnTypeName = returnTypeName;
            this.parameters = List.copyOf(parameters);
            this.behaviorKind = behaviorKind;
        }

        /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: id: identificador que delimita el recurso objetivo; matcher: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static OperationBuilder parse(String id, Matcher matcher) {
            List<ParameterBuilder> parameters = new ArrayList<>();
            String text = matcher.group(2).trim();
            if(!text.isBlank()) {
                for(String part : text.split(",")) {
                    String[] pair = part.trim().split(":", 2);
                    if(pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                        parameters.add(new ParameterBuilder(pair[0].trim(), pair[1].trim()));
                    }
                }
            }
            MetaOperationBehaviorKind behavior = MetaOperationBehaviorKind.UNSPECIFIED;
            if(matcher.group(4) != null && !matcher.group(4).isBlank()) {
                try {
                    behavior = MetaOperationBehaviorKind.valueOf(matcher.group(4).trim().toUpperCase(Locale.ROOT));
                } catch(IllegalArgumentException ignored) {
                    behavior = MetaOperationBehaviorKind.UNSPECIFIED;
                }
            }
            return new OperationBuilder(id, matcher.group(1).trim(), matcher.group(3).trim(), parameters, behavior);
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: enumNameIds: dato de entrada necesario para la operación; dataTypeNameIds: dato de entrada necesario para la operación; classNameIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaOperation build(Map<String, String> enumNameIds, Map<String, String> dataTypeNameIds, Map<String, String> classNameIds) {
            MetaTypeReference returnType = "void".equalsIgnoreCase(returnTypeName) ? null : operationType(returnTypeName, enumNameIds, dataTypeNameIds, classNameIds);
            List<MetaParameter> builtParameters = new ArrayList<>();
            for(int index = 0; index < parameters.size(); index += 1) {
                builtParameters.add(parameters.get(index).build(id, index, enumNameIds, dataTypeNameIds, classNameIds));
            }
            return new MetaOperation(id, name, returnType, returnType == null ? null : Multiplicity.one(), true, true, builtParameters, List.of(), behaviorKind, Map.of("sourceFormat", "drawio"));
        }
    }

    private static final class ClassBuilder {
        private final String id;
        private final String name;
        private final MetaClassKind kind;
        private final List<AttributeBuilder> attributes = new ArrayList<>();
        private final List<OperationBuilder> operations = new ArrayList<>();

        /** Descripción: Inicializa una instancia de ClassBuilder con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ClassBuilder(String id, String name, MetaClassKind kind) {
            this.id = id;
            this.name = name;
            this.kind = kind;
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: enumNameIds: dato de entrada necesario para la operación; dataTypeNameIds: dato de entrada necesario para la operación; classNameIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass build(Map<String, String> enumNameIds, Map<String, String> dataTypeNameIds, Map<String, String> classNameIds) {
            return new MetaClass(id, name, kind, attributes.stream().map(item -> item.build(enumNameIds, dataTypeNameIds)).toList(), operations.stream().map(item -> item.build(enumNameIds, dataTypeNameIds, classNameIds)).toList(), Map.of());
        }
    }

    /** Descripción: Ejecuta la operación «operationType» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: typeName: dato de entrada necesario para la operación; enumNameIds: dato de entrada necesario para la operación; dataTypeNameIds: dato de entrada necesario para la operación; classNameIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaTypeReference operationType(String typeName, Map<String, String> enumNameIds, Map<String, String> dataTypeNameIds, Map<String, String> classNameIds) {
        if(classNameIds.containsKey(typeName)) return MetaTypeReference.classType(classNameIds.get(typeName));
        if(enumNameIds.containsKey(typeName)) return MetaTypeReference.enumType(enumNameIds.get(typeName));
        if(dataTypeNameIds.containsKey(typeName)) return MetaTypeReference.dataType(dataTypeNameIds.get(typeName));
        return ImportParsingSupport.typeReference(typeName);
    }

    private static final class EnumBuilder {
        private final String id;
        private final String name;
        private final List<MetaEnumLiteral> literals = new ArrayList<>();

        /** Descripción: Inicializa una instancia de EnumBuilder con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private EnumBuilder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaEnum build() {
            return new MetaEnum(id, name, literals, Map.of());
        }
    }

    private static final class DataTypeBuilder {
        private final String id;
        private final String name;
        private String implementationTypeName = "";
        private boolean serializable = true;

        /** Descripción: Inicializa una instancia de DataTypeBuilder con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private DataTypeBuilder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaDataType build() {
            return new MetaDataType(id, name, EcoreImportMetadata.dataType(Map.of(), implementationTypeName, serializable));
        }
    }
}

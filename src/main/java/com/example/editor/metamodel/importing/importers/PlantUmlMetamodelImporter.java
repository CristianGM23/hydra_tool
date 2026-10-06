package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

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
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;

/**
 * Importa el PlantUML generado por la herramienta.
 */
@Component
public class PlantUmlMetamodelImporter implements MetamodelImporter {

    private static final Pattern BLOCK_DECLARATION = Pattern.compile("^(abstract class|class|interface|enum)\\s+\"([^\"]+)\"\\s+as\\s+(\\S+)\\s*\\{\\s*$");
    private static final Pattern DATATYPE_DECLARATION = Pattern.compile("^class\\s+\"([^\"]+)\"\\s+as\\s+(\\S+)\\s+<<datatype>>\\s*$");
    private static final Pattern ATTRIBUTE = Pattern.compile("^([^:]+?)\\s*:\\s*([^\\[]+?)\\s*\\[([^]]+)]\\s*$");
    private static final Pattern OPERATION = Pattern.compile("^([^()]+)\\(([^)]*)\\)\\s*:\\s*([^<]+?)(?:\\s*<<([A-Za-z_]+)>>)?\\s*$");
    private static final Pattern ENUM_LITERAL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(-?\\d+)\\s*$");
    private static final Pattern RELATION = Pattern.compile("^(\\S+)\\s+(-->|\\*--|o--|\\.\\.>|--\\|>|\\.\\.\\|>)\\s*(?:\"([^\"]+)\"\\s*)?(\\S+)(?:\\s*:\\s*(.*))?$");

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.PLANTUML;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        return importContent(new String(source.content(), StandardCharsets.UTF_8), source.fileName());
    }

    /** Descripción: Ejecuta la operación «importContent» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: content: valor de entrada que debe procesarse; fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importContent(String content, String fileName) {
        String[] lines = content.replace("\r", "").split("\n");
        Map<String, ClassBuilder> classesByAlias = new LinkedHashMap<>();
        Map<String, EnumBuilder> enumsByAlias = new LinkedHashMap<>();
        Map<String, DataTypeBuilder> dataTypesByAlias = new LinkedHashMap<>();
        List<String> relationLines = new ArrayList<>();
        int index = 0;

        while(index < lines.length) {
            String line = lines[index].trim();
            Matcher dataTypeMatcher = DATATYPE_DECLARATION.matcher(line);
            Matcher blockMatcher = BLOCK_DECLARATION.matcher(line);

            if(dataTypeMatcher.matches()) {
                String name = dataTypeMatcher.group(1);
                String alias = dataTypeMatcher.group(2);
                dataTypesByAlias.put(alias, new DataTypeBuilder("datatype:" + ImportParsingSupport.safeId(name, "datatype"), name));
                index += 1;
            } else if(blockMatcher.matches()) {
                String kind = blockMatcher.group(1);
                String name = blockMatcher.group(2);
                String alias = blockMatcher.group(3);
                List<String> body = new ArrayList<>();
                index += 1;

                while(index < lines.length && !"}".equals(lines[index].trim())) {
                    body.add(lines[index].trim());
                    index += 1;
                }

                if("enum".equals(kind)) {
                    enumsByAlias.put(alias, enumBuilder(name, body));
                } else {
                    classesByAlias.put(alias, classBuilder(kind, name, alias, body));
                }

                index += 1;
            } else {
                Matcher relationMatcher = RELATION.matcher(line);

                if(relationMatcher.matches()) {
                    relationLines.add(line);
                }

                index += 1;
            }
        }

        Map<String, String> enumNames = new LinkedHashMap<>();
        Map<String, String> dataTypeNames = new LinkedHashMap<>();
        enumsByAlias.values().forEach(item -> enumNames.put(item.name, item.id));
        dataTypesByAlias.values().forEach(item -> dataTypeNames.put(item.name, item.id));
        Map<String, String> classNames = new LinkedHashMap<>();
        classesByAlias.values().forEach(item -> classNames.put(item.name, item.id));
        List<MetaClass> classes = classesByAlias.values().stream().map(item -> item.build(enumNames, dataTypeNames, classNames)).toList();
        List<MetaEnum> enums = enumsByAlias.values().stream().map(EnumBuilder::build).toList();
        List<MetaDataType> dataTypes = dataTypesByAlias.values().stream().map(DataTypeBuilder::build).toList();
        Map<String, String> aliases = new LinkedHashMap<>();
        classesByAlias.forEach((alias, item) -> aliases.put(alias, item.id));
        enumsByAlias.forEach((alias, item) -> aliases.put(alias, item.id));
        dataTypesByAlias.forEach((alias, item) -> aliases.put(alias, item.id));
        List<MetaRelation> relations = readRelations(relationLines, aliases);
        String id = ImportParsingSupport.safeId(ImportParsingSupport.fileBaseName(fileName), "imported-plantuml");
        MetaModel metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, ImportParsingSupport.pascal(id, "ImportedPlantUml"), classes, enums, dataTypes, relations, EcoreImportMetadata.model(Map.of("sourceFormat", "plantuml"), id + ".ecore", "urn:imported:" + id, id.replace('-', '_')));
        List<TransformationWarning> warnings = List.of(
                new TransformationWarning("IMPORT_LOSSY_PLANTUML_IDENTIFIERS", "$.classes", "PlantUML aliases do not preserve the complete original stable identifiers, so deterministic identifiers were generated"),
                new TransformationWarning("IMPORT_LOSSY_PLANTUML_FEATURE_OPTIONS", "$.classes", "PlantUML does not store every Ecore feature flag or opposite reference, so safe defaults were applied"));
        return new MetamodelImportOutput(metamodel, null, warnings);
    }

    /** Descripción: Ejecuta la operación «classBuilder» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: kindText: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; alias: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ClassBuilder classBuilder(String kindText, String name, String alias, List<String> body) {
        MetaClassKind kind = "interface".equals(kindText) ? MetaClassKind.INTERFACE : "abstract class".equals(kindText) ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS;
        String id = "class:" + ImportParsingSupport.safeId(name, "class");
        List<AttributeBuilder> attributes = new ArrayList<>();
        List<OperationBuilder> operations = new ArrayList<>();

        for(String line : body) {
            Matcher operationMatcher = OPERATION.matcher(line);
            if(operationMatcher.matches()) {
                List<ParameterBuilder> parameters = new ArrayList<>();
                String parameterText = operationMatcher.group(2).trim();
                if(!parameterText.isBlank()) {
                    for(String part : parameterText.split(",")) {
                        String[] pair = part.trim().split(":", 2);
                        if(pair.length == 2) parameters.add(new ParameterBuilder(pair[0].trim(), pair[1].trim()));
                    }
                }
                MetaOperationBehaviorKind behavior = MetaOperationBehaviorKind.UNSPECIFIED;
                if(operationMatcher.group(4) != null) {
                    try { behavior = MetaOperationBehaviorKind.valueOf(operationMatcher.group(4).toUpperCase(java.util.Locale.ROOT)); } catch(IllegalArgumentException ignored) { behavior = MetaOperationBehaviorKind.UNSPECIFIED; }
                }
                operations.add(new OperationBuilder(operationMatcher.group(1).trim(), operationMatcher.group(3).trim(), parameters, behavior));
                continue;
            }
            Matcher matcher = ATTRIBUTE.matcher(line);
            if(matcher.matches()) attributes.add(new AttributeBuilder(ImportParsingSupport.attributeId(id, matcher.group(1).trim()), matcher.group(1).trim(), matcher.group(2).trim(), ImportParsingSupport.multiplicity(matcher.group(3), Multiplicity.one())));
        }

        return new ClassBuilder(id, name, kind, alias, attributes, operations);
    }

    /** Descripción: Ejecuta la operación «enumBuilder» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: name: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EnumBuilder enumBuilder(String name, List<String> body) {
        String id = "enum:" + ImportParsingSupport.safeId(name, "enumeration");
        List<MetaEnumLiteral> literals = new ArrayList<>();

        for(String line : body) {
            Matcher matcher = ENUM_LITERAL.matcher(line);

            if(matcher.matches()) {
                literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(id, matcher.group(1)), matcher.group(1), ImportParsingSupport.integer(matcher.group(2), literals.size()), Map.of()));
            }
        }

        return new EnumBuilder(id, name, literals);
    }

    /** Descripción: Obtiene la información correspondiente a «readRelations» dentro de DataTypeBuilder. Entradas: lines: dato de entrada necesario para la operación; aliases: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaRelation> readRelations(List<String> lines, Map<String, String> aliases) {
        List<MetaRelation> result = new ArrayList<>();

        for(String line : lines) {
            Matcher matcher = RELATION.matcher(line);

            if(!matcher.matches()) {
                continue;
            }

            String sourceId = aliases.get(matcher.group(1));
            String targetId = aliases.get(matcher.group(4));

            if(sourceId == null || targetId == null) {
                continue;
            }

            MetaRelationKind kind = relationKind(matcher.group(2));
            String label = matcher.group(5) == null ? "" : matcher.group(5).trim();
            Multiplicity targetMultiplicity = matcher.group(3) == null ? null : ImportParsingSupport.multiplicity(matcher.group(3), Multiplicity.optional());
            String relationId = kind == MetaRelationKind.INHERITANCE || kind == MetaRelationKind.IMPLEMENTATION ? "supertype:" + sourceId + ":" + targetId : ImportParsingSupport.relationId(sourceId, label.isBlank() ? kind.name().toLowerCase() : label);
            result.add(new MetaRelation(relationId, label, sourceId, targetId, kind, null, targetMultiplicity, "", true, true, EcoreImportMetadata.relation(Map.of("sourceFormat", "plantuml"), true, kind == MetaRelationKind.REFERENCE || kind == MetaRelationKind.AGGREGATION)));
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «relationKind» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: connector: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelationKind relationKind(String connector) {
        return switch(connector) {
            case "*--" -> MetaRelationKind.CONTAINMENT;
            case "o--" -> MetaRelationKind.AGGREGATION;
            case "..>" -> MetaRelationKind.DEPENDENCY;
            case "--|>" -> MetaRelationKind.INHERITANCE;
            case "..|>" -> MetaRelationKind.IMPLEMENTATION;
            default -> MetaRelationKind.REFERENCE;
        };
    }

    private record AttributeBuilder(String id, String name, String typeName, Multiplicity multiplicity) {

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaAttribute build(Map<String, String> enumNames, Map<String, String> dataTypeNames) {
            MetaTypeReference type = enumNames.containsKey(typeName) ? MetaTypeReference.enumType(enumNames.get(typeName)) : dataTypeNames.containsKey(typeName) ? MetaTypeReference.dataType(dataTypeNames.get(typeName)) : ImportParsingSupport.typeReference(typeName);
            return new MetaAttribute(id, name, type, multiplicity, true, true, "", Map.of());
        }
    }

    private record ParameterBuilder(String name, String typeName) {
        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: operationId: identificador que delimita el recurso objetivo; index: dato de entrada necesario para la operación; enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación; classNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaParameter build(String operationId, int index, Map<String, String> enumNames, Map<String, String> dataTypeNames, Map<String, String> classNames) {
            return new MetaParameter("parameter:" + operationId + ":" + ImportParsingSupport.safeId(name, "parameter-" + index), name, operationType(typeName, enumNames, dataTypeNames, classNames), Multiplicity.one(), true, true, Map.of());
        }
    }

    private record OperationBuilder(String name, String returnTypeName, List<ParameterBuilder> parameters, MetaOperationBehaviorKind behavior) {
        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: classId: identificador que delimita el recurso objetivo; enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación; classNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaOperation build(String classId, Map<String, String> enumNames, Map<String, String> dataTypeNames, Map<String, String> classNames) {
            String operationId = "operation:" + classId + ":" + ImportParsingSupport.safeId(name, "operation");
            MetaTypeReference returnType = "void".equalsIgnoreCase(returnTypeName) ? null : operationType(returnTypeName, enumNames, dataTypeNames, classNames);
            List<MetaParameter> values = new ArrayList<>();
            for(int index = 0; index < parameters.size(); index += 1) values.add(parameters.get(index).build(operationId, index, enumNames, dataTypeNames, classNames));
            return new MetaOperation(operationId, name, returnType, returnType == null ? null : Multiplicity.one(), true, true, values, List.of(), behavior, Map.of("sourceFormat", "plantuml"));
        }
    }

    private record ClassBuilder(String id, String name, MetaClassKind kind, String alias, List<AttributeBuilder> attributes, List<OperationBuilder> operations) {

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación; classNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass build(Map<String, String> enumNames, Map<String, String> dataTypeNames, Map<String, String> classNames) {
            return new MetaClass(id, name, kind, attributes.stream().map(item -> item.build(enumNames, dataTypeNames)).toList(), operations.stream().map(item -> item.build(id, enumNames, dataTypeNames, classNames)).toList(), Map.of("plantUmlAlias", alias));
        }
    }

    /** Descripción: Ejecuta la operación «operationType» correspondiente a la responsabilidad de DataTypeBuilder. Entradas: typeName: dato de entrada necesario para la operación; enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación; classNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaTypeReference operationType(String typeName, Map<String, String> enumNames, Map<String, String> dataTypeNames, Map<String, String> classNames) {
        if(classNames.containsKey(typeName)) return MetaTypeReference.classType(classNames.get(typeName));
        if(enumNames.containsKey(typeName)) return MetaTypeReference.enumType(enumNames.get(typeName));
        if(dataTypeNames.containsKey(typeName)) return MetaTypeReference.dataType(dataTypeNames.get(typeName));
        return ImportParsingSupport.typeReference(typeName);
    }

    private record EnumBuilder(String id, String name, List<MetaEnumLiteral> literals) {

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaEnum build() {
            return new MetaEnum(id, name, literals, Map.of());
        }
    }

    private record DataTypeBuilder(String id, String name) {

        /** Descripción: Crea o prepara el resultado de «build» dentro de DataTypeBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaDataType build() {
            return new MetaDataType(id, name, EcoreImportMetadata.dataType(Map.of(), "", true));
        }
    }
}

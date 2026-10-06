package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
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
 * Importa el Markdown documental generado por la herramienta.
 */
@Component
public class MarkdownMetamodelImporter implements MetamodelImporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.MARKDOWN;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        List<String> lines = new String(source.content(), StandardCharsets.UTF_8).replace("\r", "").lines().toList();
        String name = valueAfter(lines, "# Metamodel:", ImportParsingSupport.pascal(ImportParsingSupport.fileBaseName(source.fileName()), "ImportedMetamodel"));
        String id = inlineCodeValue(lines, "- Stable ID:", ImportParsingSupport.safeId(name, "imported-markdown"));
        String schemaVersion = inlineCodeValue(lines, "- Schema version:", MetaModel.CURRENT_SCHEMA_VERSION);
        String namespaceUri = inlineCodeValue(lines, "- Namespace URI:", "urn:imported:" + id);
        String namespacePrefix = inlineCodeValue(lines, "- Namespace prefix:", id.replace('-', '_'));
        List<MetaDataType> dataTypes = readDataTypes(lines);
        List<MetaEnum> enums = readEnums(lines);
        Map<String, String> enumNames = new LinkedHashMap<>();
        Map<String, String> dataTypeNames = new LinkedHashMap<>();
        enums.forEach(item -> enumNames.put(item.name(), item.id()));
        dataTypes.forEach(item -> dataTypeNames.put(item.name(), item.id()));
        ParsedClasses parsed = readClasses(lines, enumNames, dataTypeNames);
        MetaModel metamodel = new MetaModel(schemaVersion, id, name, parsed.classes(), enums, dataTypes, parsed.relations(), EcoreImportMetadata.model(Map.of("sourceFormat", "markdown"), id + ".ecore", namespaceUri, namespacePrefix));
        List<TransformationWarning> warnings = List.of(new TransformationWarning("IMPORT_LOSSY_MARKDOWN_IDENTIFIERS", "$.classes", "Document Markdown does not preserve feature identifiers, opposite references or every Ecore flag, so deterministic identifiers and safe defaults were generated"));
        return new MetamodelImportOutput(metamodel, null, warnings);
    }

    /** Descripción: Obtiene la información correspondiente a «readDataTypes» dentro de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaDataType> readDataTypes(List<String> lines) {
        List<MetaDataType> result = new ArrayList<>();
        int start = indexOf(lines, "## Custom data types");
        int end = nextSection(lines, start + 1, "## ");

        for(int index = start + 1; start >= 0 && index < end; index += 1) {
            List<String> cells = tableCells(lines.get(index));

            if(cells.size() >= 3 && !"Name".equals(cells.get(0)) && !cells.get(0).startsWith("---")) {
                String name = cells.get(0);
                String implementation = cells.get(1).replace("`", "");
                String id = "datatype:" + ImportParsingSupport.safeId(name, "datatype");
                result.add(new MetaDataType(id, name, EcoreImportMetadata.dataType(Map.of(), implementation, Boolean.parseBoolean(cells.get(2)))));
            }
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readEnums» dentro de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaEnum> readEnums(List<String> lines) {
        List<MetaEnum> result = new ArrayList<>();
        int start = indexOf(lines, "## Enumerations");
        int end = nextSection(lines, start + 1, "## ");
        int index = start + 1;

        while(start >= 0 && index < end) {
            String line = lines.get(index).trim();

            if(line.startsWith("### ")) {
                String name = line.substring(4).trim();
                String id = "enum:" + ImportParsingSupport.safeId(name, "enumeration");
                List<MetaEnumLiteral> literals = new ArrayList<>();
                index += 1;

                while(index < end && !lines.get(index).trim().startsWith("### ")) {
                    List<String> cells = tableCells(lines.get(index));

                    if(cells.size() >= 2 && !"Literal".equals(cells.get(0)) && !cells.get(0).startsWith("---")) {
                        literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(id, cells.get(0)), cells.get(0), ImportParsingSupport.integer(cells.get(1), literals.size()), Map.of()));
                    }

                    index += 1;
                }

                result.add(new MetaEnum(id, name, literals, Map.of()));
            } else {
                index += 1;
            }
        }

        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readClasses» dentro de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación; enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ParsedClasses readClasses(List<String> lines, Map<String, String> enumNames, Map<String, String> dataTypeNames) {
        List<ClassBuilder> builders = new ArrayList<>();
        int start = indexOf(lines, "## Classes");
        int index = start + 1;

        while(start >= 0 && index < lines.size()) {
            String line = lines.get(index).trim();

            if(line.startsWith("### ")) {
                String name = line.substring(4).trim();
                String id = "class:" + ImportParsingSupport.safeId(name, "class");
                ClassBuilder builder = new ClassBuilder(id, name);
                index += 1;

                while(index < lines.size() && !lines.get(index).trim().startsWith("### ")) {
                    String current = lines.get(index).trim();

                    if(current.startsWith("- Kind:")) {
                        builder.kind = classKind(current.substring(current.indexOf(':') + 1).trim());
                    } else if("#### Attributes".equals(current)) {
                        index = readAttributeTable(lines, index + 1, builder, enumNames, dataTypeNames);
                        continue;
                    } else if("#### Relations".equals(current)) {
                        index = readRelationTable(lines, index + 1, builder);
                        continue;
                    }

                    index += 1;
                }

                builders.add(builder);
            } else {
                index += 1;
            }
        }

        Map<String, String> classNames = new LinkedHashMap<>();
        builders.forEach(item -> classNames.put(item.name, item.id));
        List<MetaRelation> relations = new ArrayList<>();
        builders.forEach(item -> item.buildRelations(classNames, relations));
        return new ParsedClasses(builders.stream().map(ClassBuilder::build).toList(), relations);
    }

    /** Descripción: Obtiene la información correspondiente a «readAttributeTable» dentro de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; builder: dato de entrada necesario para la operación; enumNames: dato de entrada necesario para la operación; dataTypeNames: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int readAttributeTable(List<String> lines, int index, ClassBuilder builder, Map<String, String> enumNames, Map<String, String> dataTypeNames) {
        while(index < lines.size() && !lines.get(index).trim().startsWith("#### ") && !lines.get(index).trim().startsWith("### ")) {
            List<String> cells = tableCells(lines.get(index));

            if(cells.size() >= 4 && !"Name".equals(cells.get(0)) && !cells.get(0).startsWith("---")) {
                String name = cells.get(0);
                String typeName = cells.get(1);
                MetaTypeReference type = enumNames.containsKey(typeName) ? MetaTypeReference.enumType(enumNames.get(typeName)) : dataTypeNames.containsKey(typeName) ? MetaTypeReference.dataType(dataTypeNames.get(typeName)) : ImportParsingSupport.typeReference(typeName);
                builder.attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(builder.id, name), name, type, ImportParsingSupport.multiplicity(cells.get(2), Multiplicity.one()), true, true, "—".equals(cells.get(3)) ? "" : cells.get(3), Map.of()));
            }

            index += 1;
        }

        return index;
    }

    /** Descripción: Obtiene la información correspondiente a «readRelationTable» dentro de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; builder: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int readRelationTable(List<String> lines, int index, ClassBuilder builder) {
        while(index < lines.size() && !lines.get(index).trim().startsWith("#### ") && !lines.get(index).trim().startsWith("### ")) {
            List<String> cells = tableCells(lines.get(index));

            if(cells.size() >= 4 && !"Name".equals(cells.get(0)) && !cells.get(0).startsWith("---")) {
                builder.relations.add(new RelationBuilder("—".equals(cells.get(0)) ? "" : cells.get(0), cells.get(1), ImportParsingSupport.multiplicity(cells.get(2), Multiplicity.optional()), relationKind(cells.get(3))));
            }

            index += 1;
        }

        return index;
    }

    /** Descripción: Ejecuta la operación «tableCells» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: line: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> tableCells(String line) {
        String value = line.trim();

        if(!value.startsWith("|") || !value.endsWith("|")) {
            return List.of();
        }

        return java.util.Arrays.stream(value.substring(1, value.length() - 1).split("\\|", -1)).map(String::trim).toList();
    }

    /** Descripción: Ejecuta la operación «indexOf» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int indexOf(List<String> lines, String expected) {
        for(int index = 0; index < lines.size(); index += 1) {
            if(expected.equals(lines.get(index).trim())) {
                return index;
            }
        }

        return -1;
    }

    /** Descripción: Ejecuta la operación «nextSection» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación; start: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int nextSection(List<String> lines, int start, String prefix) {
        if(start <= 0) {
            return 0;
        }

        for(int index = start; index < lines.size(); index += 1) {
            if(lines.get(index).trim().startsWith(prefix)) {
                return index;
            }
        }

        return lines.size();
    }

    /** Descripción: Ejecuta la operación «valueAfter» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String valueAfter(List<String> lines, String prefix, String fallback) {
        return lines.stream().map(String::trim).filter(item -> item.startsWith(prefix)).map(item -> item.substring(prefix.length()).trim()).findFirst().orElse(fallback);
    }

    /** Descripción: Ejecuta la operación «inlineCodeValue» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: lines: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String inlineCodeValue(List<String> lines, String prefix, String fallback) {
        String value = valueAfter(lines, prefix, fallback);
        return value.startsWith("`") && value.endsWith("`") ? value.substring(1, value.length() - 1) : value;
    }

    /** Descripción: Ejecuta la operación «classKind» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind classKind(String value) {
        try {
            return MetaClassKind.valueOf(value);
        } catch(RuntimeException exception) {
            return MetaClassKind.CLASS;
        }
    }

    /** Descripción: Ejecuta la operación «relationKind» correspondiente a la responsabilidad de MarkdownMetamodelImporter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelationKind relationKind(String value) {
        try {
            return MetaRelationKind.valueOf(value);
        } catch(RuntimeException exception) {
            return MetaRelationKind.REFERENCE;
        }
    }

    private static final class ClassBuilder {
        private final String id;
        private final String name;
        private MetaClassKind kind = MetaClassKind.CLASS;
        private final List<MetaAttribute> attributes = new ArrayList<>();
        private final List<RelationBuilder> relations = new ArrayList<>();

        /** Descripción: Inicializa una instancia de ClassBuilder con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ClassBuilder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de MarkdownMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass build() {
            return new MetaClass(id, name, kind, attributes, List.of(), Map.of());
        }

        /** Descripción: Crea o prepara el resultado de «buildRelations» dentro de MarkdownMetamodelImporter. Entradas: classNames: dato de entrada necesario para la operación; output: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void buildRelations(Map<String, String> classNames, List<MetaRelation> output) {
            for(RelationBuilder relation : relations) {
                String targetId = classNames.get(relation.targetName);

                if(targetId != null) {
                    String relationId = relation.kind == MetaRelationKind.INHERITANCE || relation.kind == MetaRelationKind.IMPLEMENTATION ? "supertype:" + id + ":" + targetId : ImportParsingSupport.relationId(id, relation.name.isBlank() ? relation.kind.name().toLowerCase() : relation.name);
                    output.add(new MetaRelation(relationId, relation.name, id, targetId, relation.kind, null, relation.multiplicity, "", true, true, EcoreImportMetadata.relation(Map.of(), true, relation.kind == MetaRelationKind.REFERENCE || relation.kind == MetaRelationKind.AGGREGATION)));
                }
            }
        }
    }

    private record RelationBuilder(String name, String targetName, Multiplicity multiplicity, MetaRelationKind kind) {
    }

    private record ParsedClasses(List<MetaClass> classes, List<MetaRelation> relations) {
    }
}

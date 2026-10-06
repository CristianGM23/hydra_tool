package com.example.editor.metamodel.importing.importers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Converts static Blockly JSON definitions to HYDRA's neutral metamodel without executing JavaScript. */
final class BlocklyMetamodelImportSupport {
    private static final Pattern DEFINE_ARRAY = Pattern.compile("(?:(?:Blockly\\.)?common\\.)?defineBlocksWithJsonArray\\s*\\(");
    private static final Pattern JSON_INIT = Pattern.compile("\\.jsonInit\\s*\\(");
    private final ObjectMapper objectMapper;
    /** Descripción: Inicializa una instancia de BlocklyMetamodelImportSupport con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ BlocklyMetamodelImportSupport(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    /** Descripción: Ejecuta la operación «importSource» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; javascript: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetamodelImportOutput importSource(MetamodelImportSource source, MetamodelImportOptions options, boolean javascript) {
        String text = new String(source.content(), StandardCharsets.UTF_8);
        List<JsonNode> definitions = javascript ? readJavaScript(text) : readJson(text);
        List<TransformationWarning> warnings = new ArrayList<>();
        if(definitions.isEmpty()) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "No static Blockly block definitions were found in " + source.fileName() + ". JavaScript is parsed declaratively and is never executed.");
        String modelId = options.targetId().isBlank() ? safeId(stripExtension(source.fileName())) : safeId(options.targetId());
        String baseId = "blockly-block";
        Map<String, String> classByBlockType = new LinkedHashMap<>();
        for(JsonNode definition : definitions) {
            String type = text(definition, "type");
            if(type.isBlank()) { warnings.add(new TransformationWarning("BLOCKLY_BLOCK_WITHOUT_TYPE", source.fileName(), "A Blockly block definition without type was skipped.")); continue; }
            classByBlockType.put(type, "block:" + safeId(type));
        }
        List<MetaClass> classes = new ArrayList<>();
        classes.add(new MetaClass(baseId, "BlocklyBlock", MetaClassKind.ABSTRACT_CLASS, List.of(), List.of(), Map.of("blockly:base", "true")));
        List<MetaRelation> relations = new ArrayList<>();
        int fieldCount = 0; int inputCount = 0; int constraintCount = 0;
        for(JsonNode definition : definitions) {
            String type = text(definition, "type"); String classId = classByBlockType.get(type); if(classId == null) continue;
            List<MetaAttribute> attributes = new ArrayList<>();
            JsonNode args = definition.get("args0");
            if(args != null && args.isArray()) for(JsonNode arg : args) {
                String argType = text(arg, "type"); String name = text(arg, "name"); if(name.isBlank()) name = "part" + (attributes.size() + 1);
                if(argType.startsWith("field_")) { fieldCount++; attributes.add(new MetaAttribute(classId + ":attribute:" + safeId(name), name, primitiveFor(argType), Multiplicity.optional(), true, true, text(arg, "value"), Map.of("blockly:fieldType", argType, "blockly:source", arg.toString()))); }
                if(argType.equals("input_value") || argType.equals("input_statement")) {
                    inputCount++; List<String> checks = checks(arg.get("check")); constraintCount += checks.size();
                    String target = checks.stream().map(classByBlockType::get).filter(java.util.Objects::nonNull).findFirst().orElse(baseId);
                    relations.add(new MetaRelation(classId + ":input:" + safeId(name), name, classId, target, MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of("blockly:inputType", argType, "blockly:check", String.join(",", checks), "blockly:source", arg.toString())));
                }
            }
            Map<String,String> annotations = new LinkedHashMap<>();
            annotations.put("blockly:type", type); annotations.put("blockly:message0", text(definition, "message0")); annotations.put("blockly:output", jsonText(definition.get("output"))); annotations.put("blockly:previousStatement", jsonText(definition.get("previousStatement"))); annotations.put("blockly:nextStatement", jsonText(definition.get("nextStatement"))); annotations.put("blockly:definition", definition.toString());
            constraintCount += checks(definition.get("output")).size() + checks(definition.get("previousStatement")).size() + checks(definition.get("nextStatement")).size();
            classes.add(new MetaClass(classId, className(type), MetaClassKind.CLASS, attributes, List.of(), annotations));
            relations.add(new MetaRelation("inherits:" + classId, "", classId, baseId, MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of("blockly:generated", "true")));
        }
        Map<String,String> annotations = Map.of("hydra:importFormat", javascript ? "blockly-javascript" : "blockly-json", "hydra:blockly:blockCount", String.valueOf(classes.size() - 1), "hydra:blockly:fieldCount", String.valueOf(fieldCount), "hydra:blockly:inputCount", String.valueOf(inputCount), "hydra:blockly:connectionConstraintCount", String.valueOf(constraintCount), "hydra:blockly:javascriptExecuted", "false");
        warnings.add(new TransformationWarning("IMPORT_INFO_BLOCKLY_DECLARATIVE", source.fileName(), "Blockly definitions were parsed declaratively. JavaScript source was not executed. Connection checks are preserved as neutral annotations and input relations."));
        return new MetamodelImportOutput(new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, modelId, className(modelId), classes, List.of(), List.of(), relations, annotations), null, warnings);
    }

    /** Descripción: Obtiene la información correspondiente a «readJson» dentro de BlocklyMetamodelImportSupport. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<JsonNode> readJson(String text) {
        try { JsonNode root = objectMapper.readTree(text); JsonNode values = root.isArray() ? root : root.has("blockDefinitions") ? root.get("blockDefinitions") : root.has("blocks") ? root.get("blocks") : null; if(values == null || !values.isArray()) return List.of(); List<JsonNode> result = new ArrayList<>(); values.forEach(result::add); return result; }
        catch(JacksonException exception) { throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not parse Blockly JSON", exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «readJavaScript» dentro de BlocklyMetamodelImportSupport. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<JsonNode> readJavaScript(String text) {
        List<JsonNode> result = new ArrayList<>();
        for(String value : balanced(text, DEFINE_ARRAY, '[', ']')) {
            result.addAll(readJson(value));
        }
        result.addAll(readClassicExternalJsonInit(text));
        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readClassicExternalJsonInit» dentro de BlocklyMetamodelImportSupport. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<JsonNode> readClassicExternalJsonInit(String text) {
        List<JsonNode> result = new ArrayList<>();
        Pattern assignment = Pattern.compile("Blockly\\.Blocks\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*\\]\\s*=\\s*\\{");
        Matcher matcher = assignment.matcher(text);
        while(matcher.find()) {
            String blockType = matcher.group(1);
            int next = text.indexOf("Blockly.Blocks[", matcher.end());
            String segment = text.substring(matcher.start(), next < 0 ? text.length() : next);
            List<String> objects = balanced(segment, JSON_INIT, '{', '}');
            if(objects.isEmpty()) {
                continue;
            }
            try {
                JsonNode parsed = objectMapper.readTree(objects.get(0));
                if(parsed.isObject()) {
                    ((tools.jackson.databind.node.ObjectNode) parsed).put("type", blockType);
                    result.add(parsed);
                }
            } catch(JacksonException ignored) {
                // JavaScript is intentionally not executed. Dynamic jsonInit objects remain unsupported.
            }
        }
        return result;
    }
    /** Descripción: Ejecuta la operación «balanced» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: text: valor de entrada que debe procesarse; pattern: dato de entrada necesario para la operación; open: dato de entrada necesario para la operación; close: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> balanced(String text, Pattern pattern, char open, char close) { List<String> result = new ArrayList<>(); Matcher matcher = pattern.matcher(text); while(matcher.find()) { int start = matcher.end(); while(start < text.length() && Character.isWhitespace(text.charAt(start))) start++; if(start >= text.length() || text.charAt(start) != open) continue; int depth=0; char quote=0; boolean escaped=false; for(int i=start;i<text.length();i++){ char c=text.charAt(i); if(quote!=0){ if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c==quote) quote=0; continue; } if(c=='\''||c=='"'){ quote=c; continue; } if(c==open) depth++; if(c==close && --depth==0){ result.add(text.substring(start,i+1)); break; } } } return result; }
    /** Descripción: Ejecuta la operación «primitiveFor» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: fieldType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference primitiveFor(String fieldType) { if(fieldType.equals("field_number") || fieldType.equals("field_angle")) return MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE); if(fieldType.equals("field_checkbox")) return MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN); return MetaTypeReference.primitive(MetaPrimitiveType.STRING); }
    /** Descripción: Valida las condiciones necesarias para «checks» y rechaza entradas no válidas. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> checks(JsonNode node) { if(node == null || node.isNull()) return List.of(); if(node.isArray()) { List<String> result=new ArrayList<>(); node.forEach(item -> { if(item.isTextual()) result.add(item.asText()); }); return result; } return node.isTextual() ? List.of(node.asText()) : List.of(); }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: node: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String text(JsonNode node, String field) { JsonNode value=node.get(field); return value != null && value.isValueNode() ? value.asText("") : ""; }
    /** Descripción: Ejecuta la operación «jsonText» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonText(JsonNode node) { return node == null || node.isNull() ? "" : node.isTextual() ? node.asText() : node.toString(); }
    /** Descripción: Ejecuta la operación «stripExtension» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripExtension(String value) { int index=value.indexOf('.'); return index > 0 ? value.substring(0,index) : value; }
    /** Descripción: Ejecuta la operación «safeId» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeId(String value) { String result=value==null?"":value.trim().replaceAll("[^A-Za-z0-9_-]+","-").replaceAll("^-+|-+$",""); return result.isBlank()?"blockly-metamodel":result; }
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de BlocklyMetamodelImportSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String className(String value) { String[] parts=value.replaceAll("[^A-Za-z0-9]+"," ").trim().split("\\s+"); StringBuilder b=new StringBuilder(); for(String part:parts) if(!part.isBlank()) b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); return b.isEmpty()?"BlocklyBlockType":b.toString(); }
}

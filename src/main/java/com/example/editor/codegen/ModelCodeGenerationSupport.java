package com.example.editor.codegen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.validation.model.CanonicalModelDocument;
import tools.jackson.databind.json.JsonMapper;

/** Utilidades neutrales compartidas por los renderers de generación sin introducir estado mutable. */
final class ModelCodeGenerationSupport {
    private final JsonMapper mapper;

    /** Descripción: Inicializa las utilidades con el serializador JSON compartido. Entradas: mapper JSON. Salida: soporte listo para los renderers. */
    /** Descripción: Inicializa una instancia de ModelCodeGenerationSupport con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ ModelCodeGenerationSupport(JsonMapper mapper) { this.mapper = mapper; }

    /** Descripción: Serializa la proyección JSON con metadatos de generación y namespace. Entradas: modelo y opciones. Salida: JSON indentado. */
    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ String json(CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        Map<String,Object> output = new java.util.LinkedHashMap<>();
        output.put("generatedBy", "HYDRA");
        output.put("namespace", options.namespace());
        if(options.includeTraceability()) output.put("traceability", traceability(model.metamodelId(), model.id()));
        output.put("model", model);
        return prettyJson(output, "Could not serialize generated JSON model");
    }

    /** Descripción: Construye metadatos explícitos y portables de trazabilidad sin alterar los identificadores semánticos requeridos por el modelo. Entradas: identificadores de metamodelo y modelo. Salida: mapa inmutable de trazabilidad. */
    /** Descripción: Ejecuta la operación «traceability» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: metamodelId: identificador que delimita el recurso objetivo; modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Map<String,String> traceability(String metamodelId, String modelId) { return Map.of("metamodelId", metamodelId, "modelId", modelId); }

    /** Descripción: Genera un comentario de trazabilidad solo cuando la opción está activa. Entradas: prefijo de comentario, modelo y opciones. Salida: comentario o cadena vacía. */
    /** Descripción: Ejecuta la operación «traceComment» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: prefix: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ String traceComment(String prefix, CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        return options.includeTraceability() ? prefix + " HYDRA trace: metamodel=" + model.metamodelId() + "; model=" + model.id() + "\n" : "";
    }

    /** Descripción: Serializa exclusivamente el documento canónico para módulos de código que ya exponen sus metadatos por separado. Entradas: modelo. Salida: JSON del modelo. */
    /** Descripción: Ejecuta la operación «modelJson» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String modelJson(CanonicalModelDocument model) { return prettyJson(model, "Could not serialize canonical model for source generation"); }

    /** Descripción: Serializa una estructura arbitraria a JSON indentado con un error de dominio preciso. Entradas: valor y mensaje de error. Salida: JSON indentado. */
    /** Descripción: Ejecuta la operación «prettyJson» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse; errorMessage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String prettyJson(Object value, String errorMessage) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch(RuntimeException exception) { throw new IllegalArgumentException(errorMessage, exception); }
    }

    /** Descripción: Resuelve el nombre de una clase del metamodelo por identificador. Entradas: metamodelo e identificador. Salida: nombre legible o identificador seguro. */
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: model: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String className(MetaModel model, String id) { return model.classes().stream().filter(value -> value.id().equals(id)).map(MetaClass::name).findFirst().orElse(identifier(id)); }

    /** Descripción: Resuelve el nombre de una relación del metamodelo por identificador. Entradas: metamodelo e identificador. Salida: nombre legible o identificador seguro. */
    /** Descripción: Ejecuta la operación «relationName» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: model: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String relationName(MetaModel model, String id) { return model.relations().stream().filter(value -> value.id().equals(id)).map(MetaRelation::name).findFirst().orElse(identifier(id)); }

    /** Descripción: Convierte atributos en expresiones Map.entry de Java manteniendo orden del modelo. Entradas: mapa de valores. Salida: lista textual de entradas. */
    /** Descripción: Ejecuta la operación «javaEntries» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String javaEntries(Map<String,Object> values) { return values.entrySet().stream().map(entry -> "Map.entry(\"" + escape(entry.getKey()) + "\", " + javaLiteral(entry.getValue()) + ")").collect(Collectors.joining(", ")); }

    /** Descripción: Convierte un valor canónico en literal Java seguro. Entradas: valor. Salida: literal Java. */
    /** Descripción: Ejecuta la operación «javaLiteral» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String javaLiteral(Object value) { if(value == null) return "null"; if(value instanceof Number || value instanceof Boolean) return String.valueOf(value); return "\"" + escape(String.valueOf(value)) + "\""; }

    /** Descripción: Convierte un valor canónico en literal textual neutral. Entradas: valor. Salida: literal. */
    /** Descripción: Ejecuta la operación «literal» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String literal(Object value) { if(value == null) return "null"; if(value instanceof Number || value instanceof Boolean) return String.valueOf(value); return "\"" + escape(String.valueOf(value)) + "\""; }

    /** Descripción: Escapa texto como literal JavaScript/TypeScript. Entradas: texto. Salida: literal entrecomillado. */
    /** Descripción: Ejecuta la operación «javascriptString» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String javascriptString(String value) { return "\"" + escape(value) + "\""; }

    /** Descripción: Escapa texto como literal Python. Entradas: texto. Salida: literal entrecomillado. */
    /** Descripción: Ejecuta la operación «pythonString» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String pythonString(String value) { return "\"" + escape(value) + "\""; }

    /** Descripción: Construye una expresión Python que carga JSON sin dependencias externas. Entradas: JSON. Salida: expresión Python. */
    /** Descripción: Ejecuta la operación «pythonJson» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: json: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String pythonJson(String json) { return "__import__('json').loads(" + pythonString(json) + ")"; }

    /** Descripción: Escapa JSON como literal Go. Entradas: texto. Salida: literal Go. */
    /** Descripción: Ejecuta la operación «goString» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String goString(String value) { return "\"" + escape(value) + "\""; }

    /** Descripción: Escapa contenido para atributos y texto XML. Entradas: valor. Salida: texto XML seguro. */
    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String xml(String value) { return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }

    /** Descripción: Escapa caracteres de control y comillas para código fuente generado. Entradas: valor. Salida: texto escapado. */
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String escape(String value) { return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }

    /** Descripción: Normaliza un identificador para código generado. Entradas: valor original. Salida: identificador seguro. */
    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String identifier(String value) { String result = String.valueOf(value).replaceAll("[^A-Za-z0-9_]", "_"); if(result.isBlank()) result = "item"; if(Character.isDigit(result.charAt(0))) result = "_" + result; return result; }

    /** Descripción: Normaliza un nombre de tipo Java. Entradas: valor original. Salida: identificador Java capitalizado. */
    /** Descripción: Ejecuta la operación «javaType» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String javaType(String value) { String result = identifier(value); return Character.toUpperCase(result.charAt(0)) + result.substring(1); }

    /** Descripción: Normaliza un nombre de paquete Go. Entradas: valor original. Salida: identificador Go en minúsculas. */
    /** Descripción: Ejecuta la operación «goIdentifier» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String goIdentifier(String value) { return identifier(value).toLowerCase(Locale.ROOT); }

    /** Descripción: Obtiene el último segmento de un namespace compuesto. Entradas: namespace. Salida: último segmento. */
    /** Descripción: Ejecuta la operación «lastSegment» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String lastSegment(String value) { int index = value.lastIndexOf('.'); return index < 0 ? value : value.substring(index + 1); }

    /** Descripción: Normaliza un tipo Blockly. Entradas: valor original. Salida: identificador Blockly. */
    /** Descripción: Ejecuta la operación «blockType» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String blockType(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"); }

    /** Descripción: Normaliza un nombre base de archivo preservando extensiones seguras. Entradas: valor original. Salida: nombre portable. */
    /** Descripción: Ejecuta la operación «safeFile» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String safeFile(String value) { String result = String.valueOf(value).replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("^-+|-+$", ""); return result.isBlank() ? "generated-model" : result; }

    /** Descripción: Convierte texto a UTF-8. Entradas: texto. Salida: bytes UTF-8. */
    /** Descripción: Ejecuta la operación «bytes» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    /** Descripción: Empaqueta archivos ordenados en un ZIP reproducible sin depender del sistema de ficheros. Entradas: mapa de ruta a bytes. Salida: bytes ZIP. */
    /** Descripción: Ejecuta la operación «zip» correspondiente a la responsabilidad de ModelCodeGenerationSupport. Entradas: files: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ byte[] zip(Map<String,byte[]> files) {
        try(ByteArrayOutputStream buffer = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for(Map.Entry<String,byte[]> entry : files.entrySet()) { ZipEntry item = new ZipEntry(entry.getKey()); item.setTime(0L); zip.putNextEntry(item); zip.write(entry.getValue()); zip.closeEntry(); }
            zip.finish(); return buffer.toByteArray();
        } catch(IOException exception) { throw new IllegalArgumentException("Could not package generated source and tests", exception); }
    }
}

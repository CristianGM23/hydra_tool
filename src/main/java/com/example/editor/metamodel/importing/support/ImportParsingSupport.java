package com.example.editor.metamodel.importing.support;

import java.util.Locale;

import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

/**
 * Reúne conversiones de texto compartidas por los importadores.
 */
public final class ImportParsingSupport {

    /** Descripción: Inicializa una instancia de ImportParsingSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ImportParsingSupport() {
    }

    /** Descripción: Ejecuta la operación «fileBaseName» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String fileBaseName(String fileName) {
        String name = fileName == null ? "imported-metamodel" : fileName;
        String lower = name.toLowerCase(Locale.ROOT);
        String[] suffixes = { ".metamodel.json", ".drawio.xml", ".agent.md", ".plantuml", ".langium", ".xtext", ".glsp.zip", ".java.zip", ".python.zip", ".java", ".py", ".zip", ".jar", ".emfatic", ".ecore", ".emf", ".drawio", ".json", ".xml", ".xmi", ".g4", ".sql", ".ddl", ".puml", ".md" };

        for(String suffix : suffixes) {
            if(lower.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }

        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Descripción: Ejecuta la operación «safeId» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String safeId(String value, String fallback) {
        String source = value == null || value.isBlank() ? fallback : value;
        String normalized = source.trim().replaceAll("([a-z0-9])([A-Z])", "$1-$2").replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "").toLowerCase(Locale.ROOT);

        if(normalized.isBlank()) {
            normalized = fallback;
        }

        if(!Character.isLetter(normalized.charAt(0))) {
            normalized = "m-" + normalized;
        }

        return normalized;
    }

    /** Descripción: Ejecuta la operación «pascal» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String pascal(String value, String fallback) {
        String source = value == null ? "" : value;
        String[] words = source.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        StringBuilder result = new StringBuilder();

        for(String word : words) {
            if(!word.isBlank()) {
                result.append(Character.toUpperCase(word.charAt(0)));

                if(word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        return result.isEmpty() ? fallback : result.toString();
    }

    /** Descripción: Ejecuta la operación «camel» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String camel(String value, String fallback) {
        String pascal = pascal(value, fallback);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: text: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Multiplicity multiplicity(String text, Multiplicity fallback) {
        if(text == null || text.isBlank()) {
            return fallback;
        }

        String value = text.trim().replace("[", "").replace("]", "");

        if("*".equals(value) || "0..*".equals(value)) {
            return Multiplicity.many();
        }

        if("1..*".equals(value)) {
            return new Multiplicity(1, null);
        }

        if(value.contains("..")) {
            String[] parts = value.split("\\.\\.");
            int lower = integer(parts[0], 0);
            Integer upper = "*".equals(parts[1]) ? null : Integer.valueOf(integer(parts[1], 1));
            return new Multiplicity(lower, upper);
        }

        int exact = integer(value, 1);
        return new Multiplicity(exact, exact);
    }

    /** Descripción: Ejecuta la operación «integer» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch(RuntimeException exception) {
            return fallback;
        }
    }

    /** Descripción: Ejecuta la operación «decimal» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static double decimal(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch(RuntimeException exception) {
            return fallback;
        }
    }

    /** Descripción: Ejecuta la operación «typeReference» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: typeName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaTypeReference typeReference(String typeName) {
        String value = typeName == null ? "" : typeName.trim();
        String normalized = value.replace("ecore:", "").toUpperCase(Locale.ROOT);

        return switch(normalized) {
            case "STRING", "ESTRING", "ID" -> MetaTypeReference.primitive(MetaPrimitiveType.STRING);
            case "BOOLEAN", "EBOOLEAN", "EBOOLEANOBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
            case "BYTE", "EBYTE", "EBYTEOBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.BYTE);
            case "SHORT", "ESHORT", "ESHORTOBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.SHORT);
            case "INTEGER", "INT", "EINT", "EINTEGEROBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.INTEGER);
            case "LONG", "ELONG", "ELONGOBJECT", "BIG_INTEGER", "EBIGINTEGER" -> MetaTypeReference.primitive(MetaPrimitiveType.LONG);
            case "FLOAT", "EFLOAT", "EFLOATOBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.FLOAT);
            case "DOUBLE", "EDOUBLE", "EDOUBLEOBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE);
            case "DECIMAL", "EBIGDECIMAL" -> MetaTypeReference.primitive(MetaPrimitiveType.DECIMAL);
            case "CHARACTER", "CHAR", "ECHAR", "ECHARACTEROBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.CHARACTER);
            case "DATE", "EDATE" -> MetaTypeReference.primitive(MetaPrimitiveType.DATE);
            case "BINARY", "EBYTEARRAY" -> MetaTypeReference.primitive(MetaPrimitiveType.BINARY);
            case "OBJECT", "EJAVAOBJECT", "EOBJECT" -> MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
            default -> MetaTypeReference.external(value);
        };
    }

    /** Descripción: Ejecuta la operación «attributeId» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: classId: identificador que delimita el recurso objetivo; attributeName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String attributeId(String classId, String attributeName) {
        return "attribute:" + classId + ":" + safeId(attributeName, "value");
    }

    /** Descripción: Ejecuta la operación «relationId» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: classId: identificador que delimita el recurso objetivo; relationName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String relationId(String classId, String relationName) {
        return "relation:" + classId + ":" + safeId(relationName, "reference");
    }

    /** Descripción: Ejecuta la operación «literalId» correspondiente a la responsabilidad de ImportParsingSupport. Entradas: enumId: identificador que delimita el recurso objetivo; literalName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String literalId(String enumId, String literalName) {
        return "literal:" + enumId + ":" + safeId(literalName, "value");
    }
}

package com.example.editor.grammar.core;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;

/** Portable path policy shared by editable sources and generated grammar artifacts. */
public final class GrammarPathPolicy {
    /** Descripción: Inicializa una instancia de GrammarPathPolicy con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarPathPolicy() { }

    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private static final String WINDOWS_RESERVED_CHARACTERS = "<>:\"|?*";

    /** Descripción: Valida una ruta relativa de gramática con una política portable entre Windows, macOS y Linux. Entradas: value: ruta relativa proporcionada por el usuario; field: nombre del campo utilizado al informar un error. Salida: devuelve la ruta normalizada con separadores '/' o rechaza rutas absolutas, traversal, nombres de dispositivo Windows y segmentos no portables. */ public static String requirePortableRelative(String value, String field) {
        String raw = normalizeEditableRelative(value);
        if (raw.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        if (raw.indexOf('\0') >= 0 || raw.startsWith("/") || raw.startsWith("~") || raw.matches("^[A-Za-z]:.*") || raw.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) {
            throw invalid(field, raw);
        }
        Deque<String> normalized = new ArrayDeque<>();
        for (String segment : raw.split("/", -1)) {
            if (segment.isEmpty()) continue;
            if (segment.equals(".") || segment.equals("..") || !portableSegment(segment)) throw invalid(field, raw);
            normalized.addLast(segment);
        }
        if (normalized.isEmpty()) throw invalid(field, raw);
        String result = String.join("/", normalized);
        try {
            Path parsed = Path.of(result);
            if (parsed.isAbsolute()) throw invalid(field, raw);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(field + " is not a valid portable path: " + raw, exception);
        }
        return result;
    }

    /** Descripción: Conserva los espacios que pertenecen al último segmento para que la validación pueda rechazarlos, pero tolera espacios pegados después de una barra final al copiar una ruta desde la interfaz. Entradas: value: texto de ruta editable. Salida: devuelve el texto con separadores normalizados sin ocultar segmentos inseguros. */ private static String normalizeEditableRelative(String value) {
        String raw = value == null ? "" : value.replace('\\', '/').stripLeading().replaceAll("/{2,}", "/");
        int end = raw.length();
        while (end > 0 && Character.isWhitespace(raw.charAt(end - 1))) end--;
        if (end < raw.length() && end > 0 && raw.charAt(end - 1) == '/') raw = raw.substring(0, end);
        if (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        return raw;
    }

    /** Descripción: Comprueba que un segmento de ruta pueda persistirse y reconstruirse sin ambigüedad en los sistemas operativos soportados. Entradas: segment: segmento individual sin separadores. Salida: true únicamente cuando no contiene caracteres de control/reservados, no termina en punto/espacio y no utiliza nombres de dispositivo reservados por Windows. */ private static boolean portableSegment(String segment) {
        if (segment.isEmpty() || segment.endsWith(".") || segment.endsWith(" ")) return false;
        String device = segment;
        int dot = device.indexOf('.');
        if (dot >= 0) device = device.substring(0, dot);
        if (WINDOWS_DEVICE_NAMES.contains(device.toUpperCase(Locale.ROOT))) return false;
        for (int index = 0; index < segment.length(); index++) {
            char value = segment.charAt(index);
            if (value < 32 || WINDOWS_RESERVED_CHARACTERS.indexOf(value) >= 0) return false;
        }
        return true;
    }

    /** Descripción: Ejecuta la operación «normalizeImport» correspondiente a la responsabilidad de GrammarPathPolicy. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String normalizeImport(String value) {
        if (value == null) return "";
        String result = value.trim().replace('\\', '/');
        while (result.startsWith("./")) result = result.substring(2);
        return result;
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de GrammarPathPolicy. Entradas: field: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static IllegalArgumentException invalid(String field, String value) {
        return new IllegalArgumentException(field + " must be relative, traversal-free and portable: " + value);
    }
}

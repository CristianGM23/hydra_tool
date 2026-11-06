package com.example.editor.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quita de los textos públicos de una respuesta de error las raíces de ruta del servidor: el directorio de trabajo de HYDRA (donde vive
 * ./data), el directorio temporal y el directorio personal del usuario del proceso. El resto del texto se conserva, de modo que un error del
 * cliente sigue diciendo qué fichero o recurso falta, pero sin revelar dónde está instalado HYDRA ni a qué usuario pertenece.
 */
final class PublicErrorText {
    private static final int MINIMUM_ROOT_LENGTH = 4;
    private static final List<Map.Entry<Pattern, String>> ROOTS = roots();

    /** Descripción: Inicializa una instancia de PublicErrorText con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private PublicErrorText() { }

    /** Descripción: Sustituye cada raíz conocida por su marcador. Entradas: text: texto público. Salida: texto sin raíces del servidor. */
    static String redact(String text) {
        if(text == null || text.isEmpty()) return text;
        String result = text;
        for(Map.Entry<Pattern, String> root : ROOTS) result = root.getKey().matcher(result).replaceAll(Matcher.quoteReplacement(root.getValue()));
        return result;
    }

    /** Descripción: Aplica {@link #redact(String)} a cada elemento. Entradas: values: textos públicos. Salida: lista nueva o la recibida si es nula. */
    static List<String> redact(List<String> values) {
        if(values == null) return null;
        List<String> result = new ArrayList<>(values.size());
        for(String value : values) result.add(redact(value));
        return result;
    }

    /** Descripción: Construye las variantes de separador de cada raíz, de la más larga a la más corta. Entradas: ninguna. Salida: patrones y marcadores. */
    private static List<Map.Entry<Pattern, String>> roots() {
        Map<String, String> variants = new LinkedHashMap<>();
        addRoot(variants, System.getProperty("user.dir"), "<hydra-workdir>");
        addRoot(variants, System.getProperty("java.io.tmpdir"), "<temp>");
        addRoot(variants, System.getProperty("user.home"), "~");
        List<Map.Entry<Pattern, String>> result = new ArrayList<>();
        variants.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, String> item) -> item.getKey().length()).reversed())
                .forEach(item -> result.add(Map.entry(Pattern.compile(Pattern.quote(item.getKey()), Pattern.CASE_INSENSITIVE), item.getValue())));
        return List.copyOf(result);
    }

    /** Descripción: Registra una raíz con sus separadores «\» y «/», ignorando raíces vacías o tan cortas como «/» o «C:\». Entradas: variants, root, marker. Salida: ninguna. */
    private static void addRoot(Map<String, String> variants, String root, String marker) {
        if(root == null) return;
        String trimmed = root.strip();
        while(trimmed.length() > 1 && (trimmed.endsWith("\\") || trimmed.endsWith("/"))) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if(trimmed.length() < MINIMUM_ROOT_LENGTH) return;
        for(String variant : List.of(trimmed, trimmed.replace('\\', '/'), trimmed.replace('/', '\\'))) variants.putIfAbsent(variant, marker);
    }
}

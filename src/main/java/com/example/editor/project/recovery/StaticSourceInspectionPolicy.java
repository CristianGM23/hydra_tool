package com.example.editor.project.recovery;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Límites defensivos compartidos por la recuperación estática de proyectos externos.
 * Nunca intenta interpretar binarios como texto y evita materializar fuentes de tamaño
 * arbitrario en memoria sólo para buscar patrones. No ejecuta ningún contenido importado.
 */
final class StaticSourceInspectionPolicy {
    static final int MAX_TEXT_SOURCE_BYTES = 4 * 1024 * 1024;
    static final int MAX_CANONICAL_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs",
            ".py", ".evl", ".eol", ".etl", ".egl", ".ocl", ".odesign", ".xml",
            ".json", ".yaml", ".yml", ".properties", ".toml", ".xtext", ".langium",
            ".g4", ".gmfgraph", ".gmftool", ".gmfmap", ".gmfgen");

    /** Descripción: Inicializa una instancia de StaticSourceInspectionPolicy con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private StaticSourceInspectionPolicy() { }

    /** Descripción: Evalúa la condición «isInspectableTextPath» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean isInspectableTextPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /** Descripción: Ejecuta la operación «inspectText» correspondiente a la responsabilidad de StaticSourceInspectionPolicy. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Optional<String> inspectText(ExternalProjectArchiveEntry entry) {
        if(entry == null || !isInspectableTextPath(entry.path())) return Optional.empty();
        byte[] bytes = entry.bytes();
        if(bytes == null || bytes.length > MAX_TEXT_SOURCE_BYTES) return Optional.empty();
        // NUL is a strong binary signal and prevents wasting decoder/regex work on opaque files.
        for(byte value : bytes) if(value == 0) return Optional.empty();
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            String text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
            if(!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
            return Optional.of(text);
        } catch(CharacterCodingException exception) {
            return Optional.empty();
        }
    }

    /** Descripción: Evalúa la condición «canonicalSnapshotAllowed» sin modificar el estado externo. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean canonicalSnapshotAllowed(ExternalProjectArchiveEntry entry) {
        return entry != null && entry.bytes() != null && entry.bytes().length <= MAX_CANONICAL_SNAPSHOT_BYTES;
    }
}

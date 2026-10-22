package com.example.editor.integration.roundtrip;

import java.nio.file.Files;
import java.nio.file.Path;
import com.example.editor.integration.compiler.TechnologyCompilationException;

/** Limits generated round-trip paths and refuses symbolic-link hops in existing ancestors. */
public final class RoundTripSafePaths {
    /** Descripción: Impide instanciar el validador compartido de rutas de sincronización. Entradas: ninguna. Salida: no crea instancias públicas. */
    private RoundTripSafePaths() {}

    /** Descripción: Resuelve una ruta bajo el espacio de trabajo y rechaza rutas absolutas, segmentos ascendentes y enlaces simbólicos dentro del límite de confianza. Entradas: root como directorio permitido y relative como ruta de artefacto. Salida: ruta absoluta normalizada o error ante una salida insegura. */
    public static Path resolve(Path root, String relative) {
        if(root == null || relative == null || relative.isBlank()) {
            throw new TechnologyCompilationException("Round-trip root and path are required");
        }
        Path base = root.toAbsolutePath().normalize();
        String source = relative.replace('\\', '/');
        if(source.startsWith("/") || source.indexOf(':') >= 0 || source.indexOf('\0') >= 0) {
            throw new TechnologyCompilationException("Unsafe round-trip path: " + relative);
        }
        String[] segments = source.split("/", -1);
        for(String segment : segments) {
            if(segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new TechnologyCompilationException("Unsafe round-trip path: " + relative);
            }
        }
        Path target = base.resolve(source).normalize();
        if(!target.startsWith(base)) {
            throw new TechnologyCompilationException("Round-trip path escapes target root: " + relative);
        }
        // The chosen workspace root is a trust boundary. Do not reject OS aliases
        // above it (e.g. /var on macOS), but block links at and below the root.
        if(Files.isSymbolicLink(base)) {
            throw new TechnologyCompilationException("Symbolic round-trip root blocked: " + base);
        }
        Path cursor = base;
        for(String segment : segments) {
            cursor = cursor.resolve(segment);
            if(Files.isSymbolicLink(cursor)) {
                throw new TechnologyCompilationException("Symbolic round-trip path blocked: " + relative);
            }
        }
        return target;
    }
}

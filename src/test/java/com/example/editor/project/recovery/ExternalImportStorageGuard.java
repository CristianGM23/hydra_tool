package com.example.editor.project.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Protección de las pruebas de importación y reinicio: ningún almacén ni el directorio de trabajo pueden quedar fuera de la
 * raíz temporal de la prueba, ni dentro del almacenamiento real del espacio de trabajo ({@code data/}). Se evalúa antes de
 * arrancar Spring, de modo que ninguna recuperación de arranque llega a tocar datos reales.
 */
public final class ExternalImportStorageGuard {
    private ExternalImportStorageGuard() { }

    /** Código de salida del proceso hijo cuando la protección lo detiene. */
    public static final int REJECTED_EXIT_CODE = 3;

    private static Path canonical(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        // El ancestro existente más próximo se resuelve a su forma real (nombres cortos 8.3 de Windows, enlaces); el resto se añade tal cual.
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) return absolute;
        try { return existing.toRealPath().resolve(existing.relativize(absolute)).normalize(); }
        catch (java.io.IOException | RuntimeException notResolvable) { return absolute; }
    }

    /** Falla si alguna ruta no está estrictamente dentro de {@code root} o si {@code root} está dentro de un {@code data/} real. */
    public static void requireInside(Path root, Collection<Path> paths) {
        Path base = canonical(root);
        if (isRealWorkspaceStorage(base)) throw new IllegalStateException("la raíz de la prueba es almacenamiento real: " + base);
        for (Path path : paths) {
            Path resolved = canonical(path);
            if (!resolved.startsWith(base) || resolved.equals(base)) {
                throw new IllegalStateException("almacén fuera de la raíz temporal de la prueba: " + resolved);
            }
        }
    }

    /** Variante para propiedades de arranque: todos los valores que sean rutas deben cumplir {@link #requireInside}. */
    public static void requirePropertiesInside(Path root, Map<String, Object> properties) {
        requireInside(root, properties.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("dsl.") || entry.getKey().startsWith("hydra."))
                .filter(entry -> entry.getValue() instanceof String text && (text.contains("/") || text.contains("\\")))
                .map(entry -> Path.of((String) entry.getValue())).toList());
    }

    /** Un directorio llamado {@code data} cuyo padre contiene el {@code pom.xml} del proyecto es el almacenamiento real del espacio de trabajo. */
    static boolean isRealWorkspaceStorage(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            Path name = current.getFileName();
            if (name != null && name.toString().equals("data") && current.getParent() != null
                    && Files.exists(current.getParent().resolve("pom.xml"))) return true;
        }
        return false;
    }
}

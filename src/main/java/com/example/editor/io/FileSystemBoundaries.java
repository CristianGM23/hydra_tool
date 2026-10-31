package com.example.editor.io;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Contención de rutas por su ruta real (SEC-WIN-JUNCTION). {@code Files.isSymbolicLink} no reconoce una unión de directorios de Windows
 * (mklink /J, que no requiere privilegios) ni un punto de montaje, y un recorrido sin FOLLOW_LINKS entra en ellos como en cualquier
 * directorio: lo que decide si una entrada sale de un límite es dónde está realmente. Una carpeta con punto de reanálisis legítimo (OneDrive)
 * resuelve a sí misma y sigue siendo válida; un enlace que apunta dentro del límite también.
 */
public final class FileSystemBoundaries {
    /** Descripción: Impide instanciar la utilidad. Entradas: ninguna. Salida: no aplica. */
    private FileSystemBoundaries() {}

    /** Descripción: Ruta real de una raíz existente, que es la referencia de contención de sus entradas. Entradas: root: raíz. Salida: la ruta real; si la raíz no existe, su forma absoluta normalizada. */
    public static Path realRoot(Path root) throws IOException {
        try { return root.toRealPath(); }
        catch(NoSuchFileException missing) { return root.toAbsolutePath().normalize(); }
    }

    /** Descripción: Indica si una entrada existente resuelve, a través de un enlace simbólico, una unión o un punto de montaje, fuera de la raíz real; una entrada que no existe no escapa. Si su ruta real no se puede resolver se trata como si escapara. Entradas: entry: entrada; realRoot: ruta real de la raíz. Salida: true si sale del límite. */
    public static boolean escapes(Path entry, Path realRoot) {
        try { return !entry.toRealPath().startsWith(realRoot); }
        catch(NoSuchFileException missing) { return false; }
        catch(IOException unresolvable) { return true; }
    }
}

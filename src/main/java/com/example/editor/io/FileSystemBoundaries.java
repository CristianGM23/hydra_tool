package com.example.editor.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

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

    /** Descripción: Fija un fichero regular mientras se comprueba su ruta real y se opera con él por nombre (TOCTOU de SEC-WIN-JUNCTION). Con un handle abierto a un fichero, NTFS no deja renombrar ninguno de sus directorios antecesores, así que nadie puede sustituir uno por una unión entre la comprobación y la operación (sin fijarlo, el borrado de un proyecto por HTTP borró ficheros de fuera: resultados/sondas/toctou/sistema-borrado-despues-jar20.json). El handle comparte el borrado, de modo que la propia operación puede borrar o mover el fichero. En POSIX un handle abierto no impide renombrar, así que allí no se abre nada. Entradas: file: fichero regular. Salida: el handle, que se cierra al terminar la operación. */
    public static Closeable pin(Path file) throws IOException {
        if(!WINDOWS) return () -> {};
        return FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    /** Descripción: Una escritura cuyo destino resuelve fuera de su raíz a través de un enlace o una unión (T-SEC-JUNCTION-AMPLIAR); cuando se lanza no se ha escrito nada. El mensaje solo lleva el nombre del fichero, no rutas internas. Entradas: target: destino rechazado. Salida: no aplica. */
    public static final class EscapeException extends IOException {
        /** Descripción: Inicializa una instancia de EscapeException con sus dependencias y datos de entrada. Entradas: target: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EscapeException(Path target) {
            super("Path resolves outside its root through a link or directory junction: " + (target.getFileName() == null ? "" : target.getFileName()));
        }
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
}

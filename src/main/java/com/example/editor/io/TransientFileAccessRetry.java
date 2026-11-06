package com.example.editor.io;

import java.io.IOException;
import java.nio.file.AccessDeniedException;

/**
 * Reintento acotado de una operación de fichero que en Windows puede fallar de forma transitoria con {@link AccessDeniedException}
 * cuando otro proceso (antivirus, indexador, lector) tiene abierto el origen o el destino. Solo se reintenta ese error y con espera
 * creciente; cualquier otro fallo, o el agotamiento de los intentos, se propaga tal cual para que la operación falle de forma segura.
 */
public final class TransientFileAccessRetry {
    /** Intentos y esperas de las publicaciones locales: ≈3,5 s en el peor caso antes de propagar el error. */
    public static final int PUBLISH_ATTEMPTS = 12;
    public static final long PUBLISH_FIRST_PAUSE_MILLIS = 20;
    public static final long PUBLISH_MAX_PAUSE_MILLIS = 500;

    @FunctionalInterface
    public interface IoAction { void run() throws IOException; }

    @FunctionalInterface
    public interface Sleeper { void sleep(long millis) throws InterruptedException; }

    /** Descripción: Impide instanciar la utilidad TransientFileAccessRetry. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor. */ private TransientFileAccessRetry() { }

    /** Descripción: Ejecuta la acción reintentando solo AccessDeniedException con espera creciente. Entradas: action: operación de fichero; attempts: intentos máximos; firstPauseMillis: primera espera; maxPauseMillis: espera máxima; sleeper: espera inyectable. Salida: no devuelve valor; propaga el último AccessDeniedException o cualquier otro fallo sin reintentarlo. */ public static void run(IoAction action, int attempts, long firstPauseMillis, long maxPauseMillis, Sleeper sleeper) throws IOException {
        long pause = Math.max(1, firstPauseMillis);
        for(int attempt = 1; ; attempt++) {
            try { action.run(); return; }
            catch(AccessDeniedException exception) {
                if(attempt >= attempts) throw exception;
                try { sleeper.sleep(pause); }
                catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    exception.addSuppressed(interrupted);
                    throw exception;
                }
                pause = Math.min(pause * 2, Math.max(1, maxPauseMillis));
            }
        }
    }

    /** Descripción: Reintento con los parámetros de publicación local. Entradas: action: operación de fichero. Salida: no devuelve valor; propaga el fallo si persiste. */ public static void publishing(IoAction action) throws IOException {
        run(action, PUBLISH_ATTEMPTS, PUBLISH_FIRST_PAUSE_MILLIS, PUBLISH_MAX_PAUSE_MILLIS, Thread::sleep);
    }
}

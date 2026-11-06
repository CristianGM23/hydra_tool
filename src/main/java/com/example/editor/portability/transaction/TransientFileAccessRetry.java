package com.example.editor.portability.transaction;

import java.io.IOException;
import java.nio.file.AccessDeniedException;

/**
 * Reintento acotado de una operación de fichero que en Windows puede fallar de forma transitoria con {@link AccessDeniedException}
 * cuando otro proceso (antivirus, indexador, lector) tiene abierto el origen o el destino. Solo se reintenta ese error y con espera
 * creciente; cualquier otro fallo, o el agotamiento de los intentos, se propaga tal cual para que la transacción falle de forma segura.
 * Delega en {@link com.example.editor.io.TransientFileAccessRetry}, que comparten las demás publicaciones locales.
 */
final class TransientFileAccessRetry {
    @FunctionalInterface
    interface IoAction { void run() throws IOException; }

    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    /** Descripción: Inicializa una instancia de TransientFileAccessRetry con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TransientFileAccessRetry() { }

    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de TransientFileAccessRetry. Entradas: action: dato de entrada necesario para la operación; attempts: dato de entrada necesario para la operación; firstPauseMillis: dato de entrada necesario para la operación; maxPauseMillis: dato de entrada necesario para la operación; sleeper: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ static void run(IoAction action, int attempts, long firstPauseMillis, long maxPauseMillis, Sleeper sleeper) throws IOException {
        com.example.editor.io.TransientFileAccessRetry.run(action::run, attempts, firstPauseMillis, maxPauseMillis, sleeper::sleep);
    }
}

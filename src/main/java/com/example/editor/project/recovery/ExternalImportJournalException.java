package com.example.editor.project.recovery;

/** El diario de importaciones no se pudo leer o escribir: la operación no debe continuar sin él. */
public class ExternalImportJournalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Descripción: Inicializa una instancia de ExternalImportJournalException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalImportJournalException(String message, Throwable cause) { super(message, cause); }

    /** Descripción: Inicializa una instancia de ExternalImportJournalException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalImportJournalException(String message) { super(message); }
}

package com.example.editor.integration.compiler;

/** Error controlado durante la compilación o generación tecnológica. */
public class TechnologyCompilationException extends RuntimeException {
    /** Descripción: Inicializa una instancia de TechnologyCompilationException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompilationException(String message){super(message);}
    /** Descripción: Inicializa una instancia de TechnologyCompilationException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompilationException(String message, Throwable cause){super(message, cause);}
}

package com.example.editor.codegen;

import java.util.List;

/** Solicitud HTTP de generación desde el modelo activo, opcionalmente acotada a una selección de elementos. */
public record ModelCodeGenerationRequest(ModelCodeGenerationOptions options, List<String> selectionElementIds) {
    public ModelCodeGenerationRequest { selectionElementIds = selectionElementIds == null ? List.of() : List.copyOf(selectionElementIds); }
    /** Descripción: Inicializa una instancia de ModelCodeGenerationRequest con sus dependencias y datos de entrada. Entradas: options: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ModelCodeGenerationRequest(ModelCodeGenerationOptions options) { this(options, List.of()); }
}

package com.example.editor.transformation.textmodel;

import com.example.editor.textual.runtime.TextualParseResult;

/** Abstracción entre la capa de transformación y el runtime concreto de gramáticas. */
public interface TransformationTextParserGateway {
    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de TransformationTextParserGateway. Entradas: grammarId: identificador que delimita el recurso objetivo; relativePath: ruta o ubicación utilizada por la operación; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ TextualParseResult parse(String grammarId,String relativePath,String text);
}

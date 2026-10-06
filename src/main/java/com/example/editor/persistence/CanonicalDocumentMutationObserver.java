package com.example.editor.persistence;

/**
 * SPI no bloqueante conceptualmente: un observador nunca debe alterar el resultado
 * de una escritura canónica ya realizada.
 */
@FunctionalInterface
public interface CanonicalDocumentMutationObserver {
    CanonicalDocumentMutationObserver NOOP = mutation -> { };
    /** Descripción: Ejecuta la operación «onMutation» correspondiente a la responsabilidad de CanonicalDocumentMutationObserver. Entradas: mutation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void onMutation(CanonicalDocumentMutation mutation);
}

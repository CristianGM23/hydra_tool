package com.example.editor.integration.representable;

/** Pluggable technology mapper used to reconstruct neutral changes. */
public interface RepresentableImportMapper {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de RepresentableImportMapper. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String technology();
    /** Descripción: Ejecuta la operación «map» correspondiente a la responsabilidad de RepresentableImportMapper. Entradas: candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ RepresentableMappingResult map(ExternalChangeCandidate candidate);
}

package com.example.editor.persistence.evolution;

import tools.jackson.databind.JsonNode;

/** One deterministic schema evolution step for a persisted JSON document namespace. */
public interface HydraJsonSchemaEvolution {
    /** Descripción: Ejecuta la operación «namespace» correspondiente a la responsabilidad de HydraJsonSchemaEvolution. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String namespace();
    /** Descripción: Ejecuta la operación «fromVersion» correspondiente a la responsabilidad de HydraJsonSchemaEvolution. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String fromVersion();
    /** Descripción: Ejecuta la operación «toVersion» correspondiente a la responsabilidad de HydraJsonSchemaEvolution. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String toVersion();
    /** Descripción: Ejecuta la operación «evolve» correspondiente a la responsabilidad de HydraJsonSchemaEvolution. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ JsonNode evolve(JsonNode source);
}

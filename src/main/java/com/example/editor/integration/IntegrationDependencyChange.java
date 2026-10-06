package com.example.editor.integration;
/** Dependencia añadida o actualizada por la integración. */
public record IntegrationDependencyChange(String ecosystem, String coordinate, String version, String scope, IntegrationDependencyAction action) {
    public IntegrationDependencyChange {
        if(ecosystem == null || ecosystem.isBlank() || coordinate == null || coordinate.isBlank() || action == null) throw new IllegalArgumentException("dependency change is incomplete");
        ecosystem = ecosystem.trim(); coordinate = coordinate.trim(); version = normalize(version); scope = normalize(scope);
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de IntegrationDependencyChange. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}

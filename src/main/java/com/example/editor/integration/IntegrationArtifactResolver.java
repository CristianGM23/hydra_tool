package com.example.editor.integration;
/** Resuelve referencias a documentos sin acoplar el checker a repositorios concretos. */
public interface IntegrationArtifactResolver {
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de IntegrationArtifactResolver. Entradas: metamodelId: identificador que delimita el recurso objetivo; artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationArtifactResolution resolve(String metamodelId, IntegrationArtifactSelection artifact);
}

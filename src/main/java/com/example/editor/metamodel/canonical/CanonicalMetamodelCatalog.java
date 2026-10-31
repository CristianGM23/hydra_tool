package com.example.editor.metamodel.canonical;

import java.util.List;

/**
 * Catálogo de metamodelos almacenados: los que se pueden usar y los que se omiten porque su documento está corrupto o no se puede leer
 * (D-MM-CORRUPT-CONTRATO). Los omitidos conservan la causa para el log del servidor; nunca se envía al cliente.
 */
public record CanonicalMetamodelCatalog(List<CanonicalMetamodelSummary> metamodels, List<Omitted> omitted) {

    public CanonicalMetamodelCatalog {
        metamodels = metamodels == null ? List.of() : List.copyOf(metamodels);
        omitted = omitted == null ? List.of() : List.copyOf(omitted);
    }

    /**
     * Metamodelo almacenado que no forma parte del catálogo.
     *
     * @param metamodelId id del fichero, o null si el nombre del fichero no es un id válido (nunca se devuelve al cliente)
     * @param reason CORRUPT_CONFIGURATION si sus bytes no forman el metamodelo; STORAGE_ERROR si no se pudo leer
     * @param cause detalle técnico, con rutas, solo para el log
     */
    public record Omitted(String metamodelId, CanonicalMetamodelRepositoryException.Reason reason, Throwable cause) {
    }
}

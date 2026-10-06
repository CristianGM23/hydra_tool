package com.example.editor.model;

import java.util.List;
import java.util.Map;

/**
 * Representa el estado neutral de un modelo EMF abierto en el editor.
 *
 * @param id identificador de la sesión.
 * @param metamodelId identificador del metamodelo.
 * @param fileName nombre del archivo XMI.
 * @param rootElementId identificador del objeto raíz.
 * @param dirty indica si existen cambios pendientes.
 * @param elements objetos semánticos.
 * @param references referencias no containment.
 */
public record ModelDocumentResponse(String id, String metamodelId, String fileName, String rootElementId, boolean dirty, List<ModelElementResponse> elements, List<ModelReferenceResponse> references) {

    /**
     * Representa un EObject dentro del documento.
     *
     * @param id identificador del objeto.
     * @param classId identificador completo de la EClass.
     * @param className nombre de la EClass.
     * @param containerId identificador del contenedor.
     * @param containmentReference referencia containment utilizada.
     * @param attributes valores actuales de los atributos.
     */
    public record ModelElementResponse(String id, String classId, String className, String containerId, String containmentReference, Map<String, Object> attributes) {
    }

    /**
     * Representa una referencia semántica no containment.
     *
     * @param sourceId identificador del origen.
     * @param referenceName nombre de la referencia.
     * @param targetId identificador del destino.
     */
    public record ModelReferenceResponse(String sourceId, String referenceName, String targetId) {
    }
}
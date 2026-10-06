package com.example.editor.metamodel.design;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.MetamodelIdPolicy;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Guarda la disposición visual de los diagramas de metamodelos.
 */
@Service
public class MetamodelDiagramStorageService {

    private static final long MAX_DOCUMENT_BYTES = 8L * 1024 * 1024;
    private static final int MAX_NODES = 10_000;
    private static final int MAX_EDGES = 30_000;
    private static final int MAX_ROUTE_POINTS_PER_EDGE = 1_000;
    private static final double MAX_ABS_COORDINATE = 10_000_000d;
    private static final double MAX_NODE_DIMENSION = 100_000d;
    private static final int MAX_TEXT_LENGTH = 16_384;

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    /** Crea el almacenamiento puramente visual del diagrama. */
    /** Descripción: Inicializa una instancia de MetamodelDiagramStorageService con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDiagramStorageService(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Descripción:
     * Carga un diagrama guardado o devuelve uno vacío.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo.
     *
     * Salida:
     * @return el diagrama disponible.
     */
    /** Descripción: Obtiene la información correspondiente a «load» dentro de MetamodelDiagramStorageService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument load(String metamodelId) {
        Path path = resolveDiagramPath(metamodelId);

        if (!Files.exists(path)) {
            return new MetamodelDiagramDocument(metamodelId, List.of(), List.of());
        }

        try {
            long size = Files.size(path);
            if(size > MAX_DOCUMENT_BYTES) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram exceeds the maximum supported size");
            String content = new String(com.example.editor.io.BoundedIo.read(path, MAX_DOCUMENT_BYTES), StandardCharsets.UTF_8);
            MetamodelDiagramDocument document = objectMapper.readValue(content, MetamodelDiagramDocument.class);
            validateDocument(metamodelId, document);
            return document;
        } catch (JacksonException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not parse metamodel diagram: " + path, exception);
        } catch (IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not read metamodel diagram: " + path, exception);
        }
    }

    /**
     * Descripción:
     * Guarda exclusivamente la disposición visual.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo.
     * @param document contiene el diagrama.
     *
     * Salida:
     * @return el diagrama guardado.
     */
    public synchronized MetamodelDiagramDocument save(String metamodelId, MetamodelDiagramDocument document) {
        validateDocument(metamodelId, document);
        Path destination = resolveDiagramPath(metamodelId);
        Path temporaryFile = destination.resolveSibling(destination.getFileName() + ".tmp");
        String content;

        try {
            content = objectMapper.writeValueAsString(document);
        } catch (JacksonException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not serialize metamodel diagram: " + metamodelId, exception);
        }

        try {
            Files.createDirectories(destination.getParent());
            Files.writeString(temporaryFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            try {
                Files.move(temporaryFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not store metamodel diagram: " + destination, exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }

        return document;
    }

    /**
     * Descripción:
     * Elimina la disposición guardada.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    public synchronized void delete(String metamodelId) {
        Path path = resolveDiagramPath(metamodelId);

        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not delete metamodel diagram: " + path, exception);
        }

    }

    /** Descripción: Valida las condiciones necesarias para «validateDocument» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateDocument(String metamodelId, MetamodelDiagramDocument document) {
        if (document == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram must not be null");
        }

        if (!metamodelId.equals(document.metamodelId())) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram id does not match the URL: " + metamodelId);
        }

        if (document.nodes() == null || document.edges() == null) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram collections must not be null");
        }
        if(document.nodes().size() > MAX_NODES || document.edges().size() > MAX_EDGES) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram exceeds the configured complexity budget");
        }
        for(MetamodelDiagramDocument.NodeDesign node : document.nodes()) {
            requireText(node.elementId(), "Node id");
            requireCoordinate(node.x(), "Node x"); requireCoordinate(node.y(), "Node y");
            if(!Double.isFinite(node.width()) || !Double.isFinite(node.height()) || node.width() <= 0 || node.height() <= 0
                    || node.width() > MAX_NODE_DIMENSION || node.height() > MAX_NODE_DIMENSION)
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Node dimensions are outside the supported range");
        }
        for(MetamodelDiagramDocument.EdgeDesign edge : document.edges()) {
            requireText(edge.relationId(), "Relation id");
            if(edge.label() != null && edge.label().length() > MAX_TEXT_LENGTH)
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Relation label is too long");
            if(edge.routePoints().size() > MAX_ROUTE_POINTS_PER_EDGE)
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Relation route exceeds the supported point budget");
            for(MetamodelDiagramDocument.RoutePointDesign point : edge.routePoints()) { requireCoordinate(point.x(), "Route x"); requireCoordinate(point.y(), "Route y"); }
            if(edge.labelPosition() != null) { requireCoordinate(edge.labelPosition().x(), "Label x"); requireCoordinate(edge.labelPosition().y(), "Label y"); }
        }
        try {
            if(objectMapper.writeValueAsBytes(document).length > MAX_DOCUMENT_BYTES)
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram exceeds the maximum supported size");
        } catch(JacksonException exception) { throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram cannot be measured", exception); }
    }

    /** Descripción: Rechaza coordenadas no finitas o fuera del presupuesto visual permitido. */
    /** Descripción: Valida las condiciones necesarias para «requireCoordinate» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireCoordinate(double value, String field) {
        if(!Double.isFinite(value) || Math.abs(value) > MAX_ABS_COORDINATE)
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, field + " is outside the supported range");
    }

    /** Descripción: Rechaza identificadores o textos vacíos y excesivamente grandes. */
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireText(String value, String field) {
        if(value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH)
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, field + " is invalid");
    }

    /** Descripción: Obtiene la información correspondiente a «resolveDiagramPath» dentro de MetamodelDiagramStorageService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveDiagramPath(String metamodelId) {
        validateMetamodelId(metamodelId);
        Path directory = storageProperties.metamodels().toAbsolutePath().normalize();
        Path path = directory.resolve(metamodelId + ".metamodel-diagram.json").normalize();

        if (!path.startsWith(directory)) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Metamodel diagram path is outside the storage directory");
        }

        return path;
    }

    /** Valida el identificador mediante la política canónica compartida. */
    /** Descripción: Valida el identificador mediante la política canónica compartida. */
    /** Descripción: Valida las condiciones necesarias para «validateMetamodelId» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateMetamodelId(String metamodelId) {
        try { MetamodelIdPolicy.require(metamodelId); }
        catch(IllegalArgumentException exception) { throw new MetamodelDesignException(Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteTemporaryFile» de forma controlada. Entradas: temporaryFile: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTemporaryFile(Path temporaryFile) {
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            // El error de escritura original tiene prioridad.
        }
    }
}

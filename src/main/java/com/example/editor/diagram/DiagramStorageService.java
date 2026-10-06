package com.example.editor.diagram;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.diagram.DiagramDocument.DiagramEdgeView;
import com.example.editor.diagram.DiagramDocument.DiagramNodeView;
import com.example.editor.diagram.DiagramStorageException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda y recupera la información visual de los diagramas.
 *
 * Los datos visuales se almacenan separados del XMI para no mezclar
 * presentación y semántica.
 */
@Service
public class DiagramStorageService {

    private static final int CURRENT_FORMAT_VERSION = 1;

    private final StorageProperties storageProperties;
    private final JsonMapper jsonMapper;

    /**
     * Descripción:
     * Crea el servicio de almacenamiento visual.
     *
     * Entrada:
     * @param storageProperties contiene las rutas configuradas.
     * @param jsonMapper convierte documentos JSON.
     *
     * Salida:
     * Crea una instancia de DiagramStorageService.
     */
    /** Descripción: Inicializa una instancia de DiagramStorageService con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; jsonMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public DiagramStorageService(StorageProperties storageProperties, JsonMapper jsonMapper) {
        this.storageProperties = storageProperties;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Descripción:
     * Guarda un documento visual mediante una escritura segura.
     *
     * Entrada:
     * @param document contiene posiciones, tamaños y estilos.
     *
     * Salida:
     * @return el documento guardado.
     */
    public synchronized DiagramDocument save(DiagramDocument document) {
        validateDocument(document);
        Path destination = resolveDiagramPath(document.modelFileName());
        byte[] content;

        try {
            content = jsonMapper.writeValueAsBytes(document);
        } catch (JacksonException exception) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Could not serialize diagram document: " + document.modelFileName(), exception);
        }

        writeAtomically(destination, content);
        return document;
    }

    /**
     * Descripción:
     * Carga el documento visual asociado a un archivo XMI.
     *
     * Entrada:
     * @param modelFileName contiene el nombre del archivo XMI.
     *
     * Salida:
     * @return el documento visual cargado.
     */
    public synchronized DiagramDocument find(String modelFileName) {
        Path source = resolveDiagramPath(modelFileName);

        if (!Files.isRegularFile(source)) {
            throw new DiagramStorageException(Reason.NOT_FOUND, "Diagram document was not found for model: " + modelFileName);
        }

        try {
            DiagramDocument document = jsonMapper.readValue(com.example.editor.io.BoundedIo.readInternal(source), DiagramDocument.class);
            validateDocument(document);
            return document;
        } catch (JacksonException exception) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Could not parse diagram document: " + source.getFileName(), exception);
        } catch (IOException exception) {
            throw new DiagramStorageException(Reason.STORAGE_ERROR, "Could not read diagram document: " + source, exception);
        }
    }

    /**
     * Descripción:
     * Elimina el documento visual de un modelo.
     *
     * Entrada:
     * @param modelFileName contiene el nombre del archivo XMI.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    public synchronized void delete(String modelFileName) {
        Path source = resolveDiagramPath(modelFileName);

        try {
            Files.deleteIfExists(source);
        } catch (IOException exception) {
            throw new DiagramStorageException(Reason.STORAGE_ERROR, "Could not delete diagram document: " + source, exception);
        }
    }

    /**
     * Descripción:
     * Valida la estructura completa del documento.
     *
     * Entrada:
     * @param document contiene el documento que se debe validar.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateDocument» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateDocument(DiagramDocument document) {
        if (document == null) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram document must not be null");
        }

        if (document.formatVersion() != CURRENT_FORMAT_VERSION) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Unsupported diagram format version: " + document.formatVersion());
        }

        validateModelFileName(document.modelFileName());

        if (document.notationId() == null || document.notationId().isBlank()) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram notation id must not be empty");
        }

        if (document.nodes() == null || document.edges() == null) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram node and edge collections must not be null");
        }

        validateNodes(document.nodes());
        validateEdges(document.edges());
    }

    /**
     * Descripción:
     * Valida todos los estados visuales de nodo.
     *
     * Entrada:
     * @param nodes contiene los nodos.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateNodes» y rechaza entradas no válidas. Entradas: nodes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateNodes(List<DiagramNodeView> nodes) {
        Set<String> elementIds = new HashSet<>();

        for (DiagramNodeView node : nodes) {
            if (node == null || node.elementId() == null || node.elementId().isBlank()) {
                throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram node element id must not be empty");
            }

            if (!elementIds.add(node.elementId())) {
                throw new DiagramStorageException(Reason.INVALID_REQUEST, "Duplicate diagram node element id: " + node.elementId());
            }

            if (!Double.isFinite(node.x()) || !Double.isFinite(node.y()) || !Double.isFinite(node.width()) || !Double.isFinite(node.height())) {
                throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram node coordinates and dimensions must be finite: " + node.elementId());
            }

            if (node.width() <= 0 || node.height() <= 0) {
                throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram node dimensions must be greater than zero: " + node.elementId());
            }
        }
    }

    /**
     * Descripción:
     * Valida todos los estados visuales de conexión.
     *
     * Entrada:
     * @param edges contiene las conexiones.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateEdges» y rechaza entradas no válidas. Entradas: edges: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateEdges(List<DiagramEdgeView> edges) {
        Set<String> edgeKeys = new HashSet<>();

        for (DiagramEdgeView edge : edges) {
            if (edge == null || isBlank(edge.sourceId()) || isBlank(edge.referenceName()) || isBlank(edge.targetId())) {
                throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram edge source, reference and target must not be empty");
            }

            String edgeKey = edge.sourceId() + "|" + edge.referenceName() + "|" + edge.targetId();

            if (!edgeKeys.add(edgeKey)) {
                throw new DiagramStorageException(Reason.INVALID_REQUEST, "Duplicate diagram edge found: " + edgeKey);
            }
        }
    }

    /**
     * Descripción:
     * Construye la ruta del archivo visual.
     *
     * Entrada:
     * @param modelFileName contiene el nombre del archivo XMI.
     *
     * Salida:
     * @return la ruta normalizada del archivo JSON.
     */
    /** Descripción: Obtiene la información correspondiente a «resolveDiagramPath» dentro de DiagramStorageService. Entradas: modelFileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveDiagramPath(String modelFileName) {
        validateModelFileName(modelFileName);
        Path directory = prepareDirectory();
        String baseName = modelFileName.substring(0, modelFileName.length() - ".xmi".length());
        Path destination = directory.resolve(baseName + ".diagram.json").normalize();

        if (!destination.startsWith(directory)) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Diagram destination is outside the model storage directory");
        }

        return destination;
    }

    /**
     * Descripción:
     * Comprueba el nombre del archivo XMI.
     *
     * Entrada:
     * @param modelFileName contiene el nombre que se debe comprobar.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateModelFileName» y rechaza entradas no válidas. Entradas: modelFileName: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateModelFileName(String modelFileName) {
        if (isBlank(modelFileName)) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Model file name must not be empty");
        }

        if (modelFileName.contains("/") || modelFileName.contains("\\")) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Model file name must not contain path separators");
        }

        if (!modelFileName.toLowerCase(Locale.ROOT).endsWith(".xmi")) {
            throw new DiagramStorageException(Reason.INVALID_REQUEST, "Model file must use the .xmi extension: " + modelFileName);
        }
    }

    /**
     * Descripción:
     * Comprueba o crea el directorio de modelos.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la ruta absoluta del directorio.
     */
    /** Descripción: Ejecuta la operación «prepareDirectory» correspondiente a la responsabilidad de DiagramStorageService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path prepareDirectory() {
        Path directory = storageProperties.models().toAbsolutePath().normalize();

        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory)) {
                throw new DiagramStorageException(Reason.STORAGE_ERROR, "Model storage path is not a directory: " + directory);
            }

            return directory;
        }

        if (!storageProperties.createDirectories()) {
            throw new DiagramStorageException(Reason.STORAGE_ERROR, "Model storage directory does not exist: " + directory);
        }

        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new DiagramStorageException(Reason.STORAGE_ERROR, "Could not create model storage directory: " + directory, exception);
        }
    }

    /**
     * Descripción:
     * Escribe primero un archivo temporal y después reemplaza el destino.
     *
     * Entrada:
     * @param destination contiene la ruta definitiva.
     * @param content contiene el JSON.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por DiagramStorageService mediante la operación «writeAtomically». Entradas: destination: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeAtomically(Path destination, byte[] content) {
        Path temporaryFile = null;

        try {
            temporaryFile = Files.createTempFile(destination.getParent(), destination.getFileName() + ".", ".tmp");
            Files.write(temporaryFile, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            replaceDestination(temporaryFile, destination);
        } catch (IOException exception) {
            throw new DiagramStorageException(Reason.STORAGE_ERROR, "Could not store diagram document: " + destination, exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    /**
     * Descripción:
     * Sustituye el destino intentando primero un movimiento atómico y aplicando
     * una ruta compatible con Windows cuando el proveedor no permite reemplazar
     * atómicamente un archivo que ya existe.
     *
     * Entrada:
     * @param temporaryFile contiene el archivo completamente escrito.
     * @param destination contiene la ruta definitiva.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws IOException cuando no se puede completar ninguna sustitución.
     */
    /** Descripción: Actualiza el estado gestionado por DiagramStorageService mediante la operación «replaceDestination». Entradas: temporaryFile: archivo o contenido que debe procesarse; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void replaceDestination(Path temporaryFile, Path destination) throws IOException {
        try {
            Files.move(temporaryFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (AtomicMoveNotSupportedException exception) {
            moveReplacing(temporaryFile, destination, exception);
            return;
        } catch (IOException exception) {
            if (!Files.exists(temporaryFile)) {
                return;
            }

            moveReplacing(temporaryFile, destination, exception);
        }
    }

    /**
     * Descripción:
     * Realiza la sustitución no atómica de respaldo. Se utiliza únicamente
     * cuando el sistema de archivos rechaza la combinación ATOMIC_MOVE y
     * REPLACE_EXISTING, comportamiento observado en Windows al sobrescribir.
     *
     * Entrada:
     * @param temporaryFile contiene el archivo temporal.
     * @param destination contiene el destino existente o nuevo.
     * @param atomicFailure contiene el error del intento atómico.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws IOException cuando tampoco se puede realizar el reemplazo normal.
     */
    /** Descripción: Ejecuta la operación «moveReplacing» correspondiente a la responsabilidad de DiagramStorageService. Entradas: temporaryFile: archivo o contenido que debe procesarse; destination: dato de entrada necesario para la operación; atomicFailure: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void moveReplacing(Path temporaryFile, Path destination, IOException atomicFailure) throws IOException {
        try {
            Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException replacementFailure) {
            replacementFailure.addSuppressed(atomicFailure);
            throw replacementFailure;
        }
    }

    /**
     * Descripción:
     * Elimina un archivo temporal cuando todavía existe.
     *
     * Entrada:
     * @param temporaryFile contiene la ruta temporal.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteTemporaryFile» de forma controlada. Entradas: temporaryFile: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            // El posible error de escritura tiene prioridad.
        }
    }

    /**
     * Descripción:
     * Comprueba si un texto está vacío.
     *
     * Entrada:
     * @param value contiene el texto.
     *
     * Salida:
     * @return true cuando el texto está vacío.
     */
    /** Descripción: Evalúa la condición «isBlank» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isBlank(String value) {
        if (value == null) {
            return true;
        }

        return value.isBlank();
    }
}
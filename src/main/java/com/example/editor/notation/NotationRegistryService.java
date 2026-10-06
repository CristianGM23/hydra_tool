package com.example.editor.notation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.notation.NotationDefinition.EdgeStyle;
import com.example.editor.notation.NotationDefinition.NodeStyle;
import com.example.editor.notation.NotationRegistryException.Reason;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
/**
 * Carga, valida y registra definiciones declarativas de notación.
 */
@Service
public class NotationRegistryService {

    private static final Set<String> NODE_SHAPES = Set.of("RECTANGLE", "ROUNDED_RECTANGLE", "ELLIPSE", "DIAMOND",
            "TRIANGLE", "PENTAGON", "HEXAGON", "OCTAGON", "PARALLELOGRAM", "TRAPEZOID", "CYLINDER",
            "DOCUMENT", "CLOUD", "STAR", "CROSS", "SVG_PATH");
    private static final Set<String> LINE_TYPES = Set.of("BEZIER", "STRAIGHT", "STEP", "SMOOTH_STEP");
    private static final Set<String> MARKERS = Set.of("NONE", "ARROW_OPEN", "ARROW_CLOSED", "TRIANGLE_OPEN", "DIAMOND_OPEN", "DIAMOND_CLOSED", "CIRCLE_OPEN", "CIRCLE_CLOSED", "SQUARE_OPEN", "SQUARE_CLOSED");

    private final StorageProperties storageProperties;
    private final MetamodelRegistryService metamodelRegistryService;
    private final JsonMapper jsonMapper;
    private final Path notationDirectory;
    private final Map<String, NotationDefinition> registry = new HashMap<>();

    /**
     * Descripción:
     * Crea el registro de notaciones.
     *
     * Entrada:
     * @param storageProperties contiene las rutas configuradas.
     * @param metamodelRegistryService administra los metamodelos.
     * @param jsonMapper convierte los archivos JSON.
     *
     * Salida:
     * Crea una instancia de NotationRegistryService.
     */
    /** Descripción: Inicializa una instancia de NotationRegistryService con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; metamodelRegistryService: servicio colaborador utilizado por la operación; jsonMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public NotationRegistryService(StorageProperties storageProperties, MetamodelRegistryService metamodelRegistryService, JsonMapper jsonMapper) {
        this.storageProperties = storageProperties;
        this.metamodelRegistryService = metamodelRegistryService;
        this.jsonMapper = jsonMapper;
        this.notationDirectory = storageProperties.notations().toAbsolutePath().normalize();
    }

    /**
     * Descripción:
     * Reconstruye el registro desde el directorio configurado.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    public synchronized void initialize() {
        boolean directoryAvailable = prepareDirectory();

        if (!directoryAvailable) {
            registry.clear();
            return;
        }

        Map<String, NotationDefinition> discoveredNotations = new HashMap<>();

        for (Path path : findNotationFiles()) {
            NotationDefinition definition = readDefinition(path);

            String key = notationKey(definition.id());
            if (discoveredNotations.containsKey(key)) {
                throw new NotationRegistryException(Reason.CONFLICT, "Duplicate notation id found: " + definition.id());
            }

            discoveredNotations.put(key, definition);
        }

        registry.clear();
        registry.putAll(discoveredNotations);
    }

    /**
     * Descripción:
     * Valida, almacena y registra una notación.
     *
     * Entrada:
     * @param originalFileName contiene el nombre del archivo.
     * @param content contiene el JSON recibido.
     *
     * Salida:
     * @return la notación registrada.
     */
    public synchronized NotationDefinition register(String originalFileName, byte[] content) {
        String fileName = validateFileName(originalFileName);

        if (content == null || content.length == 0) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Uploaded notation file must not be empty");
        }

        NotationDefinition definition = parseDefinition(content, fileName);

        String key = notationKey(definition.id());
        if (registry.containsKey(key)) {
            throw new NotationRegistryException(Reason.CONFLICT, "Notation already exists: " + definition.id());
        }

        Path destination = notationDirectory.resolve(fileName).normalize();

        if (!destination.startsWith(notationDirectory)) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation destination is outside the storage directory");
        }

        prepareDirectory();

        try {
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException exception) {
            throw new NotationRegistryException(Reason.CONFLICT, "Notation file already exists: " + fileName, exception);
        } catch (IOException exception) {
            throw new NotationRegistryException(Reason.STORAGE_ERROR, "Could not store notation file: " + destination, exception);
        }

        registry.put(key, definition);
        return definition;
    }

    /**
     * Descripción:
     * Devuelve todas las notaciones ordenadas.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return una lista inmutable de notaciones.
     */
    public synchronized List<NotationDefinition> list() {
        List<NotationDefinition> definitions = new ArrayList<>(registry.values());
        definitions.sort(Comparator.comparing(NotationDefinition::id));
        return List.copyOf(definitions);
    }

    /**
     * Descripción:
     * Busca una notación mediante su identificador.
     *
     * Entrada:
     * @param id contiene el identificador.
     *
     * Salida:
     * @return la notación encontrada.
     */
    public synchronized NotationDefinition find(String id) {
        if (id == null || id.isBlank()) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation id must not be empty");
        }

        NotationDefinition definition = registry.get(notationKey(id));

        if (definition == null) {
            throw new NotationRegistryException(Reason.NOT_FOUND, "Notation was not found: " + id);
        }

        return definition;
    }

    /**
     * Descripción:
     * Lee una notación desde un archivo.
     *
     * Entrada:
     * @param path contiene la ruta del archivo.
     *
     * Salida:
     * @return la notación interpretada.
     */
    /** Descripción: Obtiene la información correspondiente a «readDefinition» dentro de NotationRegistryService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NotationDefinition readDefinition(Path path) {
        try {
            return parseDefinition(com.example.editor.io.BoundedIo.readInternal(path), path.getFileName().toString());
        } catch (IOException exception) {
            throw new NotationRegistryException(Reason.STORAGE_ERROR, "Could not read notation file: " + path, exception);
        }
    }

    /**
     * Descripción:
     * Convierte y valida una definición JSON.
     *
     * Entrada:
     * @param content contiene el JSON.
     * @param fileName contiene el nombre utilizado en los errores.
     *
     * Salida:
     * @return la notación interpretada.
     */
    /** Descripción: Ejecuta la operación «parseDefinition» correspondiente a la responsabilidad de NotationRegistryService. Entradas: content: valor de entrada que debe procesarse; fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private NotationDefinition parseDefinition(byte[] content, String fileName) {
        try {
            NotationDefinition definition = jsonMapper.readValue(content, NotationDefinition.class);
            validateDefinition(definition);
            return definition;
        } catch (NotationRegistryException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Could not parse notation file: " + fileName, exception);
        }
    }

    /**
     * Descripción:
     * Comprueba la estructura completa de una notación.
     *
     * Entrada:
     * @param definition contiene la notación.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateDefinition» y rechaza entradas no válidas. Entradas: definition: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateDefinition(NotationDefinition definition) {
        if (definition == null) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation definition must not be null");
        }

        if (isBlank(definition.id())) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation id must not be empty");
        }

        if (isBlank(definition.metamodelId())) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation metamodel id must not be empty");
        }

        metamodelRegistryService.find(definition.metamodelId());

        if (definition.nodeStyles() == null || definition.edgeStyles() == null) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation style collections must not be null");
        }

        Set<String> styleIds = new HashSet<>();

        for (NodeStyle style : definition.nodeStyles()) {
            validateNodeStyle(style, styleIds);
        }

        for (EdgeStyle style : definition.edgeStyles()) {
            validateEdgeStyle(style, styleIds);
        }
    }

    /**
     * Descripción:
     * Valida un estilo de nodo.
     *
     * Entrada:
     * @param style contiene el estilo.
     * @param styleIds contiene los identificadores utilizados.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateNodeStyle» y rechaza entradas no válidas. Entradas: style: dato de entrada necesario para la operación; styleIds: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateNodeStyle(NodeStyle style, Set<String> styleIds) {
        if (style == null || isBlank(style.id()) || isBlank(style.classId())) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Node style id and class id must not be empty");
        }

        validateUniqueStyleId(style.id(), styleIds);

        if (!NODE_SHAPES.contains(normalize(style.shape()))) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Unsupported node shape: " + style.shape());
        }

        if (style.defaultWidth() <= 0 || style.defaultHeight() <= 0 || style.minWidth() <= 0 || style.minHeight() <= 0) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Node style dimensions must be greater than zero: " + style.id());
        }

        if (style.defaultWidth() < style.minWidth() || style.defaultHeight() < style.minHeight()) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Node default dimensions must not be smaller than minimum dimensions: " + style.id());
        }

        if (style.strokeWidth() <= 0) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Node stroke width must be greater than zero: " + style.id());
        }

        if ("SVG_PATH".equals(normalize(style.shape())) && isBlank(style.svgPath())) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "SVG_PATH node style requires svgPath: " + style.id());
        }
    }

    /**
     * Descripción:
     * Valida un estilo de conexión.
     *
     * Entrada:
     * @param style contiene el estilo.
     * @param styleIds contiene los identificadores utilizados.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateEdgeStyle» y rechaza entradas no válidas. Entradas: style: dato de entrada necesario para la operación; styleIds: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateEdgeStyle(EdgeStyle style, Set<String> styleIds) {
        if (style == null || isBlank(style.id()) || isBlank(style.referenceName())) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Edge style id and reference name must not be empty");
        }

        validateUniqueStyleId(style.id(), styleIds);

        if (!LINE_TYPES.contains(normalize(style.lineType()))) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Unsupported edge line type: " + style.lineType());
        }

        if (!MARKERS.contains(normalize(style.startMarker()))) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Unsupported start marker: " + style.startMarker());
        }

        if (!MARKERS.contains(normalize(style.endMarker()))) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Unsupported end marker: " + style.endMarker());
        }

        if (style.strokeWidth() <= 0) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Edge stroke width must be greater than zero: " + style.id());
        }
    }

    /**
     * Descripción:
     * Comprueba que un identificador visual no esté repetido.
     *
     * Entrada:
     * @param styleId contiene el identificador.
     * @param styleIds contiene los identificadores utilizados.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Valida las condiciones necesarias para «validateUniqueStyleId» y rechaza entradas no válidas. Entradas: styleId: identificador que delimita el recurso objetivo; styleIds: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateUniqueStyleId(String styleId, Set<String> styleIds) {
        if (!styleIds.add(styleId)) {
            throw new NotationRegistryException(Reason.CONFLICT, "Duplicate notation style id found: " + styleId);
        }
    }

    /**
     * Descripción:
     * Busca los archivos de notación almacenados.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return una lista ordenada de archivos.
     */
    /** Descripción: Obtiene la información correspondiente a «findNotationFiles» dentro de NotationRegistryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<Path> findNotationFiles() {
        List<Path> paths = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(notationDirectory)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".notation.json")) {
                    paths.add(path);
                }
            }
        } catch (IOException exception) {
            throw new NotationRegistryException(Reason.STORAGE_ERROR, "Could not read notation storage directory: " + notationDirectory, exception);
        }

        paths.sort(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return paths;
    }

    /**
     * Descripción:
     * Comprueba o crea el directorio de notaciones.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return true cuando el directorio está disponible.
     */
    /** Descripción: Ejecuta la operación «prepareDirectory» correspondiente a la responsabilidad de NotationRegistryService. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean prepareDirectory() {
        if (Files.exists(notationDirectory)) {
            if (!Files.isDirectory(notationDirectory)) {
                throw new NotationRegistryException(Reason.STORAGE_ERROR, "Notation storage path is not a directory: " + notationDirectory);
            }

            return true;
        }

        if (!storageProperties.createDirectories()) {
            return false;
        }

        try {
            Files.createDirectories(notationDirectory);
            return true;
        } catch (IOException exception) {
            throw new NotationRegistryException(Reason.STORAGE_ERROR, "Could not create notation storage directory: " + notationDirectory, exception);
        }
    }

    /**
     * Descripción:
     * Comprueba el nombre de un archivo de notación.
     *
     * Entrada:
     * @param originalFileName contiene el nombre recibido.
     *
     * Salida:
     * @return el nombre validado.
     */
    /** Descripción: Valida las condiciones necesarias para «validateFileName» y rechaza entradas no válidas. Entradas: originalFileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String validateFileName(String originalFileName) {
        if (isBlank(originalFileName)) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation file name must not be empty");
        }

        String fileName = originalFileName.trim();

        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation file name must not contain path separators");
        }

        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".notation.json")) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation file must use the .notation.json extension: " + fileName);
        }

        return fileName;
    }

    /**
     * Descripción:
     * Normaliza el identificador de una notación para usar una única clave canónica sin distinguir mayúsculas y minúsculas.
     *
     * Entrada:
     * @param id contiene el identificador de la notación que debe normalizarse.
     *
     * Salida:
     * @return la clave canónica en minúsculas utilizada por el registro.
     */
    /** Descripción: Ejecuta la operación «notationKey» correspondiente a la responsabilidad de NotationRegistryService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String notationKey(String id) {
        if (id == null || id.isBlank()) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Notation id must not be empty");
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Descripción:
     * Normaliza un valor declarativo.
     *
     * Entrada:
     * @param value contiene el valor.
     *
     * Salida:
     * @return el valor en mayúsculas.
     */
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de NotationRegistryService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.toUpperCase(Locale.ROOT);
    }

    /**
     * Descripción:
     * Comprueba si un texto no contiene información.
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
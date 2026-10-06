package com.example.editor.model;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.editor.config.StorageProperties;
import com.example.editor.model.ModelDocumentException.Reason;

/** Aísla rutas, listado y lectura de los XMI persistidos del ciclo de vida de sesión. */
@Component
class ModelFileStorageService {
    private final StorageProperties storageProperties;

    /** Descripción: Inicializa una instancia de ModelFileStorageService con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ ModelFileStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    /** Descripción: Obtiene la información correspondiente a «listSavedModels» dentro de ModelFileStorageService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<SavedModelSummary> listSavedModels() {
        Path modelDirectory = prepareModelDirectory();
        List<SavedModelSummary> savedModels = new ArrayList<>();
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(modelDirectory)) {
            for(Path path : stream) {
                if(Files.isRegularFile(path) && hasXmiExtension(path.getFileName().toString())) {
                    savedModels.add(new SavedModelSummary(path.getFileName().toString(), Files.size(path)));
                }
            }
        } catch(IOException exception) {
            throw new ModelDocumentException(Reason.STORAGE_ERROR,
                    "Could not read model storage directory: " + modelDirectory, exception);
        }
        savedModels.sort(Comparator.comparing(SavedModelSummary::fileName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(savedModels);
    }

    /** Descripción: Obtiene la información correspondiente a «readSavedModel» dentro de ModelFileStorageService. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ byte[] readSavedModel(String fileName) {
        Path source = resolveExistingModelPath(fileName);
        try {
            return com.example.editor.io.BoundedIo.readInternal(source);
        } catch(IOException exception) {
            throw new ModelDocumentException(Reason.STORAGE_ERROR, "Could not read model file: " + fileName, exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteSavedModel» de forma controlada. Entradas: fileName: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteSavedModel(String fileName) {
        Path source = resolveExistingModelPath(fileName);
        try {
            Files.delete(source);
        } catch(IOException exception) {
            throw new ModelDocumentException(Reason.STORAGE_ERROR, "Could not delete model file: " + fileName, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolveModelPath» dentro de ModelFileStorageService. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ Path resolveModelPath(String fileName) {
        validateModelFileName(fileName);
        Path modelDirectory = prepareModelDirectory();
        Path modelPath = modelDirectory.resolve(fileName).normalize();
        if(!modelPath.startsWith(modelDirectory)) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "Model file is outside the storage directory");
        }
        return modelPath;
    }

    /** Descripción: Obtiene la información correspondiente a «resolveExistingModelPath» dentro de ModelFileStorageService. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ Path resolveExistingModelPath(String fileName) {
        Path modelPath = resolveModelPath(fileName);
        if(!Files.isRegularFile(modelPath)) {
            throw new ModelDocumentException(Reason.NOT_FOUND, "Model file was not found: " + fileName);
        }
        return modelPath;
    }

    /** Descripción: Ejecuta la operación «prepareModelDirectory» correspondiente a la responsabilidad de ModelFileStorageService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path prepareModelDirectory() {
        Path modelDirectory = storageProperties.models().toAbsolutePath().normalize();
        if(Files.exists(modelDirectory)) {
            if(!Files.isDirectory(modelDirectory)) {
                throw new ModelDocumentException(Reason.STORAGE_ERROR,
                        "Model storage path is not a directory: " + modelDirectory);
            }
            return modelDirectory;
        }
        if(!storageProperties.createDirectories()) {
            throw new ModelDocumentException(Reason.STORAGE_ERROR,
                    "Model storage directory does not exist: " + modelDirectory);
        }
        try {
            Files.createDirectories(modelDirectory);
            return modelDirectory;
        } catch(IOException exception) {
            throw new ModelDocumentException(Reason.STORAGE_ERROR,
                    "Could not create model storage directory: " + modelDirectory, exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateModelFileName» y rechaza entradas no válidas. Entradas: fileName: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateModelFileName(String fileName) {
        if(fileName == null || fileName.isBlank()) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "Model file name must not be empty");
        }
        if(fileName.contains("/") || fileName.contains("\\")) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST,
                    "Model file name must not contain path separators");
        }
        if(!hasXmiExtension(fileName)) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST,
                    "Model file must use the .xmi extension: " + fileName);
        }
    }

    /** Descripción: Evalúa la condición «hasXmiExtension» sin modificar el estado externo. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasXmiExtension(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".xmi");
    }
}

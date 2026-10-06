package com.example.editor.metamodel.projection;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/**
 * Genera el archivo Ecore como espejo regenerable del MetaModel canónico.
 */
@Service
public class EcoreMirrorService implements MetamodelProjection {

    public static final String TARGET_ID = "ecore";

    private final StorageProperties storageProperties;
    private final EcoreMetamodelAdapter adapter;

    /** Descripción: Inicializa una instancia de EcoreMirrorService con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; adapter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EcoreMirrorService(StorageProperties storageProperties, EcoreMetamodelAdapter adapter) {
        this.storageProperties = storageProperties;
        this.adapter = adapter;
    }

    @Override
    /** Descripción: Ejecuta la operación «targetId» correspondiente a la responsabilidad de EcoreMirrorService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String targetId() {
        return TARGET_ID;
    }

    @Override
    /** Descripción: Crea o prepara el resultado de «generate» dentro de EcoreMirrorService. Entradas: metamodel: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionResult generate(MetaModel metamodel, TargetConfigurationDocument configuration) {
        Path temporary = null;

        try {
            if (configuration == null) {
                throw new IllegalArgumentException("Target configuration must not be null");
            }

            EcoreProjectionConfiguration ecore = configuration.normalizedFor(metamodel).ecore();

            if (ecore == null || !ecore.enabled() || ecore.generationMode() == com.example.editor.metamodel.target.EcoreGenerationMode.DISABLED) {
                return ProjectionResult.notRequested(TARGET_ID);
            }

            Path destination = resolveArtifact(ecore.fileName());
            temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            TransformationResult<byte[]> result = adapter.toBytes(metamodel, ecore);
            Files.createDirectories(destination.getParent());
            Files.write(temporary, result.value(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            move(temporary, destination);
            ProjectionStatus status = result.warnings().isEmpty() ? ProjectionStatus.UP_TO_DATE : ProjectionStatus.GENERATED_WITH_WARNINGS;
            return new ProjectionResult(TARGET_ID, status, destination.getFileName().toString(), result.warnings(), "");
        } catch (RuntimeException | IOException exception) {
            deleteTemporary(temporary);
            return ProjectionResult.failed(TARGET_ID, safeMessage(exception));
        }
    }

    @Override
    /** Descripción: Elimina o libera el recurso gestionado por «deleteArtifact» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; configuration: configuración que gobierna el comportamiento. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteArtifact(String metamodelId, TargetConfigurationDocument configuration) {
        if (configuration == null || configuration.ecore() == null) {
            return;
        }

        try {
            Path destination = resolveArtifact(configuration.ecore().fileName());
            Files.deleteIfExists(destination);
            Files.deleteIfExists(destination.resolveSibling(destination.getFileName() + ".tmp"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete Ecore projection for metamodel: " + metamodelId, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolveArtifact» dentro de EcoreMirrorService. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveArtifact(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..") || !fileName.toLowerCase().endsWith(".ecore")) {
            throw new IllegalArgumentException("Ecore projection file name is invalid: " + fileName);
        }

        Path directory = storageProperties.metamodels().toAbsolutePath().normalize();
        Path destination = directory.resolve(fileName).normalize();

        if (!destination.startsWith(directory)) {
            throw new IllegalArgumentException("Ecore projection path is outside the metamodel directory");
        }

        return destination;
    }

    /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de EcoreMirrorService. Entradas: source: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteTemporary» de forma controlada. Entradas: temporary: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }

        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            // The projection error has priority.
        }
    }

    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de EcoreMirrorService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}

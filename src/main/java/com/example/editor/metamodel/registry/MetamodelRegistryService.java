package com.example.editor.metamodel.registry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.LoadedMetamodel;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.MetamodelDescriptor;
import com.example.editor.metamodel.MetamodelIntrospectionService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelSummary;
import com.example.editor.metamodel.projection.EcoreMirrorService;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.projection.ProjectionResult;
import com.example.editor.metamodel.projection.ProjectionStatus;
import com.example.editor.metamodel.registry.MetamodelRegistryException.Reason;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/**
 * Registro runtime de EMF. La lista semántica procede del repositorio canónico;
 * únicamente se cargan proyecciones Ecore cuyo manifiesto está actualizado.
 */
@Service
public class MetamodelRegistryService {

    private final StorageProperties storageProperties;
    private final MetamodelLoader metamodelLoader;
    private final MetamodelIntrospectionService introspectionService;
    private final CanonicalMetamodelApplicationService canonicalService;
    private final ProjectionCoordinator projectionCoordinator;
    private final Path metamodelDirectory;
    private final Map<String, RegisteredMetamodel> registry = new LinkedHashMap<>();
    private final Map<String, RuntimeMetamodelStatus> statuses = new LinkedHashMap<>();

    /** Descripción: Inicializa una instancia de MetamodelRegistryService con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; metamodelLoader: dato de entrada necesario para la operación; introspectionService: servicio colaborador utilizado por la operación; canonicalService: servicio colaborador utilizado por la operación; projectionCoordinator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelRegistryService(StorageProperties storageProperties, MetamodelLoader metamodelLoader, MetamodelIntrospectionService introspectionService, CanonicalMetamodelApplicationService canonicalService, ProjectionCoordinator projectionCoordinator) {
        this.storageProperties = storageProperties;
        this.metamodelLoader = metamodelLoader;
        this.introspectionService = introspectionService;
        this.canonicalService = canonicalService;
        this.projectionCoordinator = projectionCoordinator;
        this.metamodelDirectory = storageProperties.metamodels().toAbsolutePath().normalize();
    }

    /** Reconstruye el registro runtime sin escanear archivos Ecore huérfanos. */
    public synchronized void initialize() {
        prepareDirectory();
        Map<String, RegisteredMetamodel> discovered = new LinkedHashMap<>();
        Map<String, RuntimeMetamodelStatus> discoveredStatuses = new LinkedHashMap<>();

        for(CanonicalMetamodelSummary summary : canonicalService.findAll()) {
            String id = summary.id();
            try {
                TargetConfigurationDocument targets = projectionCoordinator.findTargets(id);
                ProjectionResult ecore = projectionCoordinator.findManifest(id).projections().get(EcoreMirrorService.TARGET_ID);
                if(ecore == null || !runtimeAvailable(ecore.status())) {
                    ProjectionStatus status = ecore == null ? ProjectionStatus.STALE : ecore.status();
                    discoveredStatuses.put(id, new RuntimeMetamodelStatus(id, status, false, ecore == null ? "Ecore projection has not been generated" : ecore.errorMessage()));
                    continue;
                }

                String fileName = targets.ecore() == null ? "" : targets.ecore().fileName();
                Path path = resolveProjection(fileName);
                if(!Files.isRegularFile(path)) {
                    discoveredStatuses.put(id, new RuntimeMetamodelStatus(id, ProjectionStatus.STALE, false, "Ecore projection file is missing: " + fileName));
                    continue;
                }

                RegisteredMetamodel registered = loadFromPath(id, path);
                discovered.put(id, registered);
                discoveredStatuses.put(id, new RuntimeMetamodelStatus(id, ecore.status(), true, ""));
            } catch(RuntimeException exception) {
                discoveredStatuses.put(id, new RuntimeMetamodelStatus(id, ProjectionStatus.FAILED, false, safeMessage(exception)));
            }
        }

        registry.clear();
        registry.putAll(discovered);
        statuses.clear();
        statuses.putAll(discoveredStatuses);
    }

    /** Descripción: Obtiene la información correspondiente a «list» dentro de MetamodelRegistryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized List<RegisteredMetamodel> list() {
        return registry.values().stream().sorted(Comparator.comparing(RegisteredMetamodel::id)).toList();
    }

    /** Descripción: Ejecuta la operación «statuses» correspondiente a la responsabilidad de MetamodelRegistryService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized List<RuntimeMetamodelStatus> statuses() {
        return statuses.values().stream().sorted(Comparator.comparing(RuntimeMetamodelStatus::metamodelId)).toList();
    }

    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de MetamodelRegistryService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized RuntimeMetamodelStatus status(String id) {
        RuntimeMetamodelStatus status = statuses.get(id);
        if(status == null) {
            throw new MetamodelRegistryException(Reason.NOT_FOUND, "Canonical metamodel was not found in the runtime registry: " + id);
        }
        return status;
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de MetamodelRegistryService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized RegisteredMetamodel find(String id) {
        if(id == null || id.isBlank()) {
            throw new MetamodelRegistryException(Reason.INVALID_REQUEST, "Metamodel id must not be empty");
        }
        RegisteredMetamodel registered = registry.get(id);
        if(registered != null) {
            return registered;
        }
        RuntimeMetamodelStatus status = statuses.get(id);
        if(status != null) {
            throw new MetamodelRegistryException(Reason.NOT_FOUND, "Metamodel is not available for the EMF runtime (" + status.projectionStatus() + "): " + id + (status.message().isBlank() ? "" : ". " + status.message()));
        }
        throw new MetamodelRegistryException(Reason.NOT_FOUND, "Metamodel was not found: " + id);
    }

    /** Descripción: Ejecuta la operación «prepareDirectory» correspondiente a la responsabilidad de MetamodelRegistryService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void prepareDirectory() {
        if(Files.isDirectory(metamodelDirectory)) {
            return;
        }
        if(Files.exists(metamodelDirectory)) {
            throw new MetamodelRegistryException(Reason.STORAGE_ERROR, "Metamodel storage path is not a directory: " + metamodelDirectory);
        }
        if(!storageProperties.createDirectories()) {
            return;
        }
        try {
            Files.createDirectories(metamodelDirectory);
        } catch(java.io.IOException exception) {
            throw new MetamodelRegistryException(Reason.STORAGE_ERROR, "Could not create metamodel storage directory: " + metamodelDirectory, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «loadFromPath» dentro de MetamodelRegistryService. Entradas: id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RegisteredMetamodel loadFromPath(String id, Path path) {
        try {
            LoadedMetamodel loaded = metamodelLoader.load(path);
            MetamodelDescriptor descriptor = introspectionService.describe(loaded);
            return new RegisteredMetamodel(id, path.getFileName().toString(), descriptor);
        } catch(RuntimeException exception) {
            throw new MetamodelRegistryException(Reason.INVALID_REQUEST, "Could not load Ecore runtime projection: " + path.getFileName(), exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolveProjection» dentro de MetamodelRegistryService. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveProjection(String fileName) {
        if(fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..") || !fileName.toLowerCase().endsWith(".ecore")) {
            throw new MetamodelRegistryException(Reason.INVALID_REQUEST, "Invalid Ecore projection file name: " + fileName);
        }
        Path path = metamodelDirectory.resolve(fileName).normalize();
        if(!path.startsWith(metamodelDirectory)) {
            throw new MetamodelRegistryException(Reason.INVALID_REQUEST, "Ecore projection is outside the configured directory");
        }
        return path;
    }

    /** Descripción: Procesa la operación «runtimeAvailable» coordinando las responsabilidades de MetamodelRegistryService. Entradas: status: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean runtimeAvailable(ProjectionStatus status) {
        return status == ProjectionStatus.UP_TO_DATE || status == ProjectionStatus.GENERATED_WITH_WARNINGS;
    }

    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de MetamodelRegistryService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record RuntimeMetamodelStatus(String metamodelId, ProjectionStatus projectionStatus, boolean runtimeAvailable, String message) {
        public RuntimeMetamodelStatus {
            message = message == null ? "" : message;
        }
    }
}

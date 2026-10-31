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
import com.example.editor.metamodel.canonical.CanonicalMetamodelCatalog;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException;
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
public class MetamodelRegistryService implements com.example.editor.project.ProjectWorkspaceReloadListener {

    private final StorageProperties storageProperties;
    private final MetamodelLoader metamodelLoader;
    private final MetamodelIntrospectionService introspectionService;
    private final CanonicalMetamodelApplicationService canonicalService;
    private final ProjectionCoordinator projectionCoordinator;
    private final Path metamodelDirectory;
    private final Map<String, RegisteredMetamodel> registry = new LinkedHashMap<>();
    private final Map<String, RuntimeMetamodelStatus> statuses = new LinkedHashMap<>();
    private final java.util.Set<String> corrupt = new java.util.HashSet<>();

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
        java.util.Set<String> discoveredCorrupt = new java.util.HashSet<>();
        CanonicalMetamodelCatalog catalog = canonicalService.catalog();

        // D-MM-CORRUPT-CONTRATO (DEF-MM-CORRUPT-STARTUP-FAIL, DEF-MM-CORRUPT-WRITE-REPORTED-AS-FAILED): a stored metamodel that cannot be used
        // is reported in the runtime status instead of failing the start-up of HYDRA or the write that rebuilt the registry
        for(CanonicalMetamodelCatalog.Omitted omitted : catalog.omitted()) {
            if(omitted.metamodelId() == null) continue;
            boolean corrupt = omitted.reason() == CanonicalMetamodelRepositoryException.Reason.CORRUPT_CONFIGURATION;
            if(corrupt) discoveredCorrupt.add(omitted.metamodelId());
            discoveredStatuses.put(omitted.metamodelId(), new RuntimeMetamodelStatus(omitted.metamodelId(), ProjectionStatus.FAILED, false, corrupt
                    ? "The stored canonical metamodel is not a valid metamodel; the stored files were left unchanged and the details were recorded in the server log."
                    : "The stored canonical metamodel could not be read; the details were recorded in the server log."));
        }

        for(CanonicalMetamodelSummary summary : catalog.metamodels()) {
            String id = summary.id();
            try {
                TargetConfigurationDocument targets = projectionCoordinator.findTargets(id);
                ProjectionResult ecore = projectionCoordinator.findManifest(id).projections().get(EcoreMirrorService.TARGET_ID);
                String reconstruction = "";
                if(ecore == null && rebuildable(targets)) {
                    // Never generated in this installation (a project brought from another one: the projection manifest is installation state and
                    // does not travel): rebuild the derived resource from the canonical metamodel exactly as POST /projections/ecore does, and say
                    // whether it reproduces the copy that came with the project. A lost file of a recorded projection is still reported, not rebuilt.
                    Path target = resolveProjection(targets.ecore().fileName());
                    String before = Files.isRegularFile(target) ? sha256(target) : null;
                    ecore = projectionCoordinator.regenerateEcore(id);
                    String after = Files.isRegularFile(target) ? sha256(target) : null;
                    reconstruction = "Ecore projection rebuilt from the canonical metamodel in this installation"
                            + (before == null ? "" : before.equals(after) ? "; identical to the copy brought with the project" : "; it differs from the copy brought with the project (" + before.substring(0, 12) + " -> " + (after == null ? "none" : after.substring(0, 12)) + ")");
                }
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
                discoveredStatuses.put(id, new RuntimeMetamodelStatus(id, ecore.status(), true, reconstruction));
            } catch(RuntimeException exception) {
                discoveredStatuses.put(id, new RuntimeMetamodelStatus(id, ProjectionStatus.FAILED, false, safeMessage(exception)));
            }
        }

        registry.clear();
        registry.putAll(discovered);
        statuses.clear();
        statuses.putAll(discoveredStatuses);
        corrupt.clear();
        corrupt.addAll(discoveredCorrupt);
    }

    /** Descripción: Recarga el registro tras traer un proyecto al espacio de trabajo. Salida: un aviso por cada metamodelo que sigue sin estar disponible para el runtime EMF, con su motivo. */
    @Override public synchronized List<String> reloadAfterProjectToWorkspace() {
        initialize();
        return statuses.values().stream().filter(status -> !status.runtimeAvailable())
                .map(status -> "Metamodel " + status.metamodelId() + " is not available for the EMF runtime (" + status.projectionStatus() + ")" + (status.message().isBlank() ? "" : ": " + status.message())).toList();
    }

    /** Descripción: Indica si la proyección Ecore se genera automáticamente (ON_SAVE): solo entonces el registro puede reconstruirla sin decisión del usuario. Entradas: targets: configuración de destinos del metamodelo. */
    private static boolean rebuildable(TargetConfigurationDocument targets) {
        return targets != null && targets.ecore() != null && targets.ecore().enabled() && targets.ecore().generationMode() == com.example.editor.metamodel.target.EcoreGenerationMode.ON_SAVE;
    }

    /** Descripción: SHA-256 hexadecimal de un fichero de proyección. */
    private static String sha256(Path path) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
        catch(java.io.IOException | java.security.NoSuchAlgorithmException exception) { return "unreadable"; }
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
        if(status != null && corrupt.contains(id)) {
            throw new MetamodelRegistryException(Reason.CORRUPT_CONFIGURATION, CanonicalMetamodelRepositoryException.corruptPublicMessage(id));
        }
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

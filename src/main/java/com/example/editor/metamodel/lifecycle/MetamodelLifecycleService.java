package com.example.editor.metamodel.lifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.deletion.MetamodelDependentArtifactCleanupService;
import com.example.editor.metamodel.MetamodelIdPolicy;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.integration.IntegrationProfileProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gestiona el impacto y borrado de la fuente canónica y sus artefactos derivados. */
@Service
public class MetamodelLifecycleService {

    private static final String TARGET_SUFFIX = ".targets.json";
    private static final String PROJECTION_SUFFIX = ".projections.json";
    private static final String DIAGRAM_SUFFIX = ".metamodel-diagram.json";

    private final StorageProperties storageProperties;
    private final CanonicalMetamodelProperties canonicalProperties;
    private final CanonicalMetamodelApplicationService canonicalService;
    private final ProjectionCoordinator projectionCoordinator;
    private final MetamodelDiagramStorageService diagramStorageService;
    private final MetamodelRegistryService registryService;
    private final ObjectMapper objectMapper;
    private final ExpressionModelRepository expressionModelRepository;
    private final ValidationModelRepository validationModelRepository;
    private final ActionModelRepository actionModelRepository;
    private final EventDefinitionModelRepository eventDefinitionRepository;
    private final EventBindingModelRepository eventBindingRepository;
    private final EventModelProperties eventProperties;
    private final EditorDefinitionProperties editorProperties;
    private final IntegrationProfileProperties integrationProperties;
    private final MetamodelDependentArtifactCleanupService dependentArtifactCleanupService;
    private final MetamodelDeletionJournalService deletionJournalService;

    /** Descripción: Inicializa una instancia de MetamodelLifecycleService con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; canonicalProperties: configuración que gobierna el comportamiento; canonicalService: servicio colaborador utilizado por la operación; projectionCoordinator: dato de entrada necesario para la operación; diagramStorageService: servicio colaborador utilizado por la operación; registryService: servicio colaborador utilizado por la operación; objectMapper: componente de conversión utilizado durante el procesamiento; expressionModelRepository: dato de entrada necesario para la operación; validationModelRepository: dato de entrada necesario para la operación; actionModelRepository: dato de entrada necesario para la operación; eventDefinitionRepository: dato de entrada necesario para la operación; eventBindingRepository: dato de entrada necesario para la operación; eventProperties: configuración que gobierna el comportamiento; editorProperties: configuración que gobierna el comportamiento; integrationProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLifecycleService(StorageProperties storageProperties, CanonicalMetamodelProperties canonicalProperties, CanonicalMetamodelApplicationService canonicalService, ProjectionCoordinator projectionCoordinator, MetamodelDiagramStorageService diagramStorageService, MetamodelRegistryService registryService, ObjectMapper objectMapper, ExpressionModelRepository expressionModelRepository, ValidationModelRepository validationModelRepository, ActionModelRepository actionModelRepository, EventDefinitionModelRepository eventDefinitionRepository, EventBindingModelRepository eventBindingRepository, EventModelProperties eventProperties, EditorDefinitionProperties editorProperties, IntegrationProfileProperties integrationProperties, MetamodelDependentArtifactCleanupService dependentArtifactCleanupService, MetamodelDeletionJournalService deletionJournalService) {
        this.storageProperties = storageProperties;
        this.canonicalProperties = canonicalProperties;
        this.canonicalService = canonicalService;
        this.projectionCoordinator = projectionCoordinator;
        this.diagramStorageService = diagramStorageService;
        this.registryService = registryService;
        this.objectMapper = objectMapper;
        this.expressionModelRepository = expressionModelRepository;
        this.validationModelRepository = validationModelRepository;
        this.actionModelRepository = actionModelRepository;
        this.eventDefinitionRepository = eventDefinitionRepository;
        this.eventBindingRepository = eventBindingRepository;
        this.eventProperties = eventProperties;
        this.editorProperties = editorProperties;
        this.integrationProperties = integrationProperties;
        this.dependentArtifactCleanupService = dependentArtifactCleanupService;
        this.deletionJournalService = deletionJournalService;
    }

    /** Descripción: Ejecuta la operación «analyzeDeletion» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionImpact analyzeDeletion(String metamodelId) {
        validateMetamodelId(metamodelId);
        MetamodelLoadResult loaded = canonicalService.findById(metamodelId);
        TargetConfigurationDocument targets = projectionCoordinator.findTargets(metamodelId);
        Path directory = storageProperties.metamodels().toAbsolutePath().normalize();
        String canonicalFile = metamodelId + canonicalProperties.getFileSuffix();
        String targetFile = metamodelId + TARGET_SUFFIX;
        String manifestFile = metamodelId + PROJECTION_SUFFIX;
        String ecoreFile = targets.ecore() == null ? metamodelId + ".ecore" : targets.ecore().fileName();
        String diagramFile = metamodelId + DIAGRAM_SUFFIX;
        String expressionFile = expressionModelRepository.fileName(metamodelId);
        String validationFile = validationModelRepository.fileName(metamodelId);
        String actionFile = actionModelRepository.fileName(metamodelId);
        String eventDefinitionFile = eventDefinitionRepository.fileName(metamodelId);
        String eventBindingFile = eventBindingRepository.fileName(metamodelId);
        List<String> editorDefinitionFiles = findAssociatedEditorDefinitions(metamodelId);
        List<String> integrationProfileFiles = findAssociatedIntegrationProfiles(metamodelId);
        List<String> notations = findAssociatedNotations(metamodelId);
        List<String> models = findAssociatedModels(targets.ecore() == null ? "" : targets.ecore().namespaceUri());
        List<String> exports = findAssociatedExports(metamodelId);
        List<String> dependentArtifacts = dependentArtifactCleanupService.referencedArtifactFiles(metamodelId);
        String warning = models.isEmpty()
                ? "This action is irreversible. The canonical metamodel and all configured projections/dependent artifacts will be deleted"
                        + (dependentArtifacts.isEmpty() ? "." : ": " + dependentArtifacts.size() + " dependent artifact(s).")
                : "This action is irreversible. Existing XMI models are not deleted automatically and may become unusable: " + models.size()
                        + " associated file(s). Other dependent artifacts scheduled for cleanup: " + dependentArtifacts.size() + ".";
        return new MetamodelDeletionImpact(
                metamodelId,
                canonicalFile, Files.isRegularFile(directory.resolve(canonicalFile)),
                targetFile, Files.isRegularFile(directory.resolve(targetFile)),
                manifestFile, Files.isRegularFile(directory.resolve(manifestFile)),
                ecoreFile, safeRegularFile(directory, ecoreFile),
                diagramFile, Files.isRegularFile(directory.resolve(diagramFile)),
                expressionFile, expressionModelRepository.exists(metamodelId),
                validationFile, validationModelRepository.exists(metamodelId),
                actionFile, actionModelRepository.exists(metamodelId),
                eventDefinitionFile, eventDefinitionRepository.exists(metamodelId),
                eventBindingFile, eventBindingRepository.exists(metamodelId),
                editorDefinitionFiles,
                integrationProfileFiles,
                notations, models, exports, dependentArtifacts, warning);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; deleteNotations: dato de entrada necesario para la operación; deleteExports: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionResult delete(String metamodelId, boolean deleteNotations, boolean deleteExports, String expectedEtag) {
        MetamodelDeletionImpact impact = analyzeDeletion(metamodelId);
        List<String> existingBefore = existingManagedFiles(impact, deleteNotations, deleteExports);
        MetamodelDeletionJournal journal = deletionJournalService.begin(metamodelId, deleteNotations, deleteExports, existingBefore);
        try {
            canonicalService.delete(metamodelId, expectedEtag);
        } catch (RuntimeException failure) {
            try { deletionJournalService.markAborted(journal, failure); }
            catch (RuntimeException journalFailure) { failure.addSuppressed(journalFailure); }
            throw failure;
        }
        journal = deletionJournalService.markPrimaryDeleted(journal);
        cleanupAfterPrimaryDeletion(metamodelId, impact, deleteNotations, deleteExports);
        List<String> pending = existingBefore.stream().filter(this::stillExistsManaged).distinct().sorted().toList();
        List<String> deleted = existingBefore.stream().filter(item -> !pending.contains(item)).distinct().sorted().toList();
        if (pending.isEmpty()) deletionJournalService.markCompleted(journal);
        else deletionJournalService.markRecoveryRequired(journal, pending);
        return new MetamodelDeletionResult(metamodelId, deleted, pending);
    }

    /** Reintenta únicamente la limpieza residual registrada tras una eliminación canónica ya completada. */
    /** Descripción: Ejecuta la operación «retryPendingCleanup» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionResult retryPendingCleanup(String metamodelId) {
        validateMetamodelId(metamodelId);
        MetamodelDeletionJournal journal = deletionJournalService.find(metamodelId)
                .orElseThrow(() -> new MetamodelDesignException(Reason.INVALID_REQUEST, "No deletion journal exists for metamodel: " + metamodelId));
        List<String> cleanupTargets = journal.pendingFiles();
        if (journal.status() == MetamodelDeletionJournal.Status.PREPARED) {
            Path canonical = storageProperties.metamodels().toAbsolutePath().normalize()
                    .resolve(metamodelId + canonicalProperties.getFileSuffix()).normalize();
            if (Files.isRegularFile(canonical)) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST,
                        "Deletion journal is still prepared while the canonical metamodel exists: " + metamodelId);
            }
            // A PREPARED journal with no canonical file means the process can have crashed
            // between the primary delete and the journal transition. Reconcile the physical
            // state before retrying so recovery only targets artifacts that still exist.
            cleanupTargets = journal.expectedFiles().stream().filter(this::stillExistsManaged).distinct().sorted().toList();
        } else if (journal.status() != MetamodelDeletionJournal.Status.RECOVERY_REQUIRED
                && journal.status() != MetamodelDeletionJournal.Status.PRIMARY_DELETED) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Deletion journal is not recoverable: " + journal.status());
        }
        List<String> residualTargets = cleanupTargets;
        bestEffortCleanup("journal residual files", () -> dependentArtifactCleanupService.deleteManagedReferences(residualTargets));
        bestEffortCleanup("dependent artifact cleanup", () -> dependentArtifactCleanupService.cleanup(metamodelId, journal.deleteNotations(), journal.deleteExports()));
        bestEffortCleanup("metamodel registry refresh", registryService::initialize);
        List<String> pending = journal.expectedFiles().stream().filter(this::stillExistsManaged).distinct().sorted().toList();
        List<String> deleted = journal.expectedFiles().stream().filter(item -> !pending.contains(item)).distinct().sorted().toList();
        if (pending.isEmpty()) deletionJournalService.markCompleted(journal);
        else deletionJournalService.markRecoveryRequired(journal, pending);
        return new MetamodelDeletionResult(metamodelId, deleted, pending);
    }

    /** Ejecuta todas las limpiezas secundarias después de que el documento canónico haya cruzado la frontera ETag. */
    /** Descripción: Ejecuta la operación «cleanupAfterPrimaryDeletion» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: metamodelId: identificador que delimita el recurso objetivo; impact: dato de entrada necesario para la operación; deleteNotations: dato de entrada necesario para la operación; deleteExports: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void cleanupAfterPrimaryDeletion(String metamodelId, MetamodelDeletionImpact impact, boolean deleteNotations, boolean deleteExports) {
        if(impact.diagramExists()) bestEffortCleanup("metamodel diagram", () -> diagramStorageService.delete(metamodelId));
        bestEffortCleanup("validation rules", () -> validationModelRepository.deleteIfExists(metamodelId));
        bestEffortCleanup("quick fixes", () -> actionModelRepository.deleteIfExists(metamodelId));
        bestEffortCleanup("expressions", () -> expressionModelRepository.deleteIfExists(metamodelId));
        bestEffortCleanup("event bindings", () -> eventBindingRepository.deleteIfExists(metamodelId));
        bestEffortCleanup("event definitions", () -> eventDefinitionRepository.deleteIfExists(metamodelId));
        deleteNamedFiles(editorProperties.getDirectory().toAbsolutePath().normalize(), impact.editorDefinitionFiles());
        deleteNamedFiles(integrationProperties.getDirectory().toAbsolutePath().normalize(), impact.integrationProfileFiles());
        if(deleteNotations) deleteNamedFiles(storageProperties.notations().toAbsolutePath().normalize(), impact.notationFiles());
        if(deleteExports) deleteNamedFiles(storageProperties.exports().toAbsolutePath().normalize(), impact.exportFiles());
        bestEffortCleanup("dependent artifact cleanup", () -> dependentArtifactCleanupService.cleanup(metamodelId, deleteNotations, deleteExports));
        bestEffortCleanup("metamodel registry refresh", registryService::initialize);
    }

    /** Descripción: Ejecuta la operación «existingManagedFiles» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: impact: dato de entrada necesario para la operación; deleteNotations: dato de entrada necesario para la operación; deleteExports: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> existingManagedFiles(MetamodelDeletionImpact impact, boolean deleteNotations, boolean deleteExports) {
        List<String> result = new ArrayList<>();
        addIfExists(result, storageProperties.metamodels(), impact.canonicalFile());
        addIfExists(result, storageProperties.metamodels(), impact.targetConfigurationFile());
        addIfExists(result, storageProperties.metamodels(), impact.projectionManifestFile());
        addIfExists(result, storageProperties.metamodels(), impact.ecoreFile());
        addIfExists(result, storageProperties.metamodels(), impact.diagramFile());
        if(impact.expressionExists()) result.add("validations/" + impact.expressionFile());
        if(impact.validationExists()) result.add("validations/" + impact.validationFile());
        if(impact.actionExists()) result.add("validations/" + impact.actionFile());
        if(impact.eventDefinitionExists()) result.add("events/" + impact.eventDefinitionFile());
        if(impact.eventBindingExists()) result.add("events/" + impact.eventBindingFile());
        impact.editorDefinitionFiles().forEach(item -> result.add("editors/" + item));
        impact.integrationProfileFiles().forEach(item -> result.add("integrations/" + item));
        if(deleteNotations) impact.notationFiles().forEach(item -> result.add("notations/" + item));
        if(deleteExports) impact.exportFiles().forEach(item -> result.add("exports/" + item));
        result.addAll(impact.dependentArtifactFiles());
        return result.stream().distinct().toList();
    }

    /** Descripción: Ejecuta la operación «addIfExists» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: result: dato de entrada necesario para la operación; directory: ruta o ubicación utilizada por la operación; fileName: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addIfExists(List<String> result, Path directory, String fileName) {
        if(fileName != null && !fileName.isBlank() && Files.isRegularFile(directory.toAbsolutePath().normalize().resolve(fileName).normalize())) {
            result.add("metamodels/" + fileName);
        }
    }

    /** Descripción: Ejecuta la operación «stillExistsManaged» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: relative: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean stillExistsManaged(String relative) {
        String[] parts = relative.split("/", 2);
        Path directory = switch(parts[0]) {
            case "metamodels" -> storageProperties.metamodels();
            case "notations" -> storageProperties.notations();
            case "exports" -> storageProperties.exports();
            case "validations" -> storageProperties.validations();
            case "events" -> eventDefinitionRepositoryFileDirectory();
            case "editors" -> editorDefinitionRepositoryFileDirectory();
            case "integrations" -> integrationProfileRepositoryFileDirectory();
            default -> null;
        };
        if (directory == null) return dependentArtifactCleanupService.managedArtifactExists(relative);
        String fileName = parts.length == 2 ? parts[1] : parts[0];
        Path root = directory.toAbsolutePath().normalize();
        Path candidate = root.resolve(fileName).normalize();
        return candidate.startsWith(root) && Files.isRegularFile(candidate);
    }

    /** Descripción: Obtiene la información correspondiente a «findAssociatedEditorDefinitions» dentro de MetamodelLifecycleService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> findAssociatedEditorDefinitions(String metamodelId) {
        Path directory = editorProperties.getDirectory().toAbsolutePath().normalize();
        if(!Files.isDirectory(directory)) return List.of();
        String suffix = editorProperties.getFileSuffix();
        try(Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .filter(path -> isJsonFieldEqual(path, "metamodelId", metamodelId))
                    .map(path -> path.getFileName().toString())
                    .sorted().toList();
        } catch(IOException exception) {
            throw storageError("Could not inspect editor definition directory", exception);
        }
    }

    /** Descripción: Ejecuta la operación «eventDefinitionRepositoryFileDirectory» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path eventDefinitionRepositoryFileDirectory() {
        return eventProperties.getDirectory().toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «editorDefinitionRepositoryFileDirectory» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path editorDefinitionRepositoryFileDirectory() {
        return editorProperties.getDirectory().toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «integrationProfileRepositoryFileDirectory» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path integrationProfileRepositoryFileDirectory() {
        return integrationProperties.getDirectory().toAbsolutePath().normalize();
    }

    /** Descripción: Obtiene la información correspondiente a «findAssociatedIntegrationProfiles» dentro de MetamodelLifecycleService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> findAssociatedIntegrationProfiles(String metamodelId) {
        Path directory = integrationProperties.getDirectory().toAbsolutePath().normalize();
        if(!Files.isDirectory(directory)) return List.of();
        String suffix = integrationProperties.getFileSuffix();
        try(Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .filter(path -> isJsonFieldEqual(path, "metamodelId", metamodelId))
                    .map(path -> path.getFileName().toString())
                    .sorted().toList();
        } catch(IOException exception) {
            throw storageError("Could not inspect integration profile directory", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «findAssociatedNotations» dentro de MetamodelLifecycleService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> findAssociatedNotations(String metamodelId) {
        Path directory = storageProperties.notations().toAbsolutePath().normalize();
        if(!Files.isDirectory(directory)) return List.of();
        List<String> result = new ArrayList<>();
        try(Stream<Path> files = Files.list(directory)) {
            for(Path path : files.filter(Files::isRegularFile).toList()) {
                if(isJsonFieldEqual(path, "metamodelId", metamodelId)) result.add(path.getFileName().toString());
            }
        } catch(IOException exception) {
            throw storageError("Could not inspect notation directory", exception);
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «findAssociatedModels» dentro de MetamodelLifecycleService. Entradas: namespaceUri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> findAssociatedModels(String namespaceUri) {
        if(namespaceUri == null || namespaceUri.isBlank()) return List.of();
        Path directory = storageProperties.models().toAbsolutePath().normalize();
        if(!Files.isDirectory(directory)) return List.of();
        List<String> result = new ArrayList<>();
        try(Stream<Path> files = Files.list(directory)) {
            for(Path path : files.filter(Files::isRegularFile).filter(item -> item.getFileName().toString().toLowerCase().endsWith(".xmi")).toList()) {
                try {
                    String content=com.example.editor.io.BoundedIo.readUtf8Internal(path);
                    if(content.contains("\"" + namespaceUri + "\"") || content.contains("'" + namespaceUri + "'")) result.add(path.getFileName().toString());
                } catch(IOException exception) {
                    throw storageError("Could not inspect XMI model: " + path, exception);
                }
            }
        } catch(IOException exception) {
            throw storageError("Could not inspect model directory", exception);
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «findAssociatedExports» dentro de MetamodelLifecycleService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> findAssociatedExports(String metamodelId) {
        Path directory = storageProperties.exports().toAbsolutePath().normalize();
        if(!Files.isDirectory(directory)) return List.of();
        try(Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.equals(metamodelId) || name.startsWith(metamodelId + ".") || name.startsWith(metamodelId + "-"))
                    .sorted().toList();
        } catch(IOException exception) {
            throw storageError("Could not inspect export directory", exception);
        }
    }

    /** Descripción: Evalúa la condición «isJsonFieldEqual» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación; field: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isJsonFieldEqual(Path path, String field, String expected) {
        try {
            JsonNode root=objectMapper.readTree(com.example.editor.io.BoundedIo.readInternal(path));
            JsonNode value = root.get(field);
            return value != null && expected.equals(value.asString());
        } catch(JacksonException exception) {
            return false;
        } catch(IOException exception) {
            throw storageError("Could not inspect JSON file: " + path, exception);
        }
    }

    /** Descripción: Elimina archivos gestionados de forma tolerante a fallos para que un residuo no interrumpa el resto de la limpieza posterior al borrado principal. Entradas: directory y fileNames. Salida: no devuelve valor; registra diagnósticos acotados para los archivos que no puedan eliminarse. */ private void deleteNamedFiles(Path directory, List<String> fileNames) {
        for(String fileName : fileNames) {
            Path path = directory.resolve(fileName).normalize();
            if(!path.startsWith(directory)) {
                com.example.editor.observability.HydraBestEffortDiagnostics.record(
                        "com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java",
                        new IllegalArgumentException("Deletion path is outside the configured directory: " + fileName));
                continue;
            }
            try {
                Files.deleteIfExists(path);
            } catch(IOException exception) {
                com.example.editor.observability.HydraBestEffortDiagnostics.record(
                        "com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java", exception);
            }
        }
    }

    /** Descripción: Ejecuta una limpieza secundaria después del borrado principal protegido por ETag sin convertir un fallo residual en un falso fallo de la eliminación principal. Entradas: operation y cleanup. Salida: registra el fallo de forma acotada y permite continuar. */
    /** Descripción: Ejecuta la operación «bestEffortCleanup» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: operation: dato de entrada necesario para la operación; cleanup: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void bestEffortCleanup(String operation, Runnable cleanup) {
        try {
            cleanup.run();
        } catch(RuntimeException exception) {
            com.example.editor.observability.HydraBestEffortDiagnostics.record(
                    "com/example/editor/metamodel/lifecycle/MetamodelLifecycleService.java",
                    new IllegalStateException("Dependent cleanup failed: " + operation, exception));
        }
    }

    /** Descripción: Ejecuta la operación «safeRegularFile» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: directory: ruta o ubicación utilizada por la operación; fileName: archivo o contenido que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean safeRegularFile(Path directory, String fileName) {
        if(fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return false;
        Path path = directory.resolve(fileName).normalize();
        return path.startsWith(directory) && Files.isRegularFile(path);
    }

    /** Valida el identificador mediante la política canónica compartida. */
    /** Descripción: Valida el identificador del ciclo de vida mediante la política canónica compartida. */
    /** Descripción: Valida las condiciones necesarias para «validateMetamodelId» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateMetamodelId(String id) {
        try { MetamodelIdPolicy.require(id); }
        catch(IllegalArgumentException exception) { throw new MetamodelDesignException(Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }

    /** Descripción: Ejecuta la operación «storageError» correspondiente a la responsabilidad de MetamodelLifecycleService. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDesignException storageError(String message, Exception cause) {
        return new MetamodelDesignException(Reason.STORAGE_ERROR, message, cause);
    }
}

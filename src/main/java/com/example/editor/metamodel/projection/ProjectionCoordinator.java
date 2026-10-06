package com.example.editor.metamodel.projection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.editor.metamodel.canonical.CanonicalMetamodelRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException;
import com.example.editor.metamodel.canonical.StoredMetamodel;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.target.EcoreGenerationMode;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.metamodel.target.TargetConfigurationRepository;

/**
 * Ejecuta las proyecciones configuradas después de persistir el canónico.
 * Ningún fallo de una proyección revierte el archivo MetaModel ya guardado.
 */
@Service
public class ProjectionCoordinator {

    private final CanonicalMetamodelRepository canonicalRepository;
    private final TargetConfigurationRepository targetRepository;
    private final ProjectionManifestRepository manifestRepository;
    private final Map<String, MetamodelProjection> projections;
    private final MetamodelProjectionProperties projectionProperties;

    @Autowired
    /** Descripción: Inicializa una instancia de ProjectionCoordinator con sus dependencias y datos de entrada. Entradas: canonicalRepository: dato de entrada necesario para la operación; targetRepository: dato de entrada necesario para la operación; manifestRepository: dato de entrada necesario para la operación; availableProjections: dato de entrada necesario para la operación; projectionProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectionCoordinator(CanonicalMetamodelRepository canonicalRepository, TargetConfigurationRepository targetRepository, ProjectionManifestRepository manifestRepository, List<MetamodelProjection> availableProjections, MetamodelProjectionProperties projectionProperties) {
        this.canonicalRepository = canonicalRepository;
        this.targetRepository = targetRepository;
        this.manifestRepository = manifestRepository;
        this.projectionProperties = projectionProperties == null ? new MetamodelProjectionProperties() : projectionProperties;
        Map<String, MetamodelProjection> index = new LinkedHashMap<>();

        for (MetamodelProjection projection : availableProjections) {
            if (projection != null && projection.targetId() != null && !projection.targetId().isBlank()) {
                index.put(projection.targetId(), projection);
            }
        }

        this.projections = Map.copyOf(index);
    }

    /** Constructor unitario para escenarios que no personalizan los valores globales. */
    /** Descripción: Inicializa una instancia de ProjectionCoordinator con sus dependencias y datos de entrada. Entradas: canonicalRepository: dato de entrada necesario para la operación; targetRepository: dato de entrada necesario para la operación; manifestRepository: dato de entrada necesario para la operación; availableProjections: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectionCoordinator(CanonicalMetamodelRepository canonicalRepository, TargetConfigurationRepository targetRepository, ProjectionManifestRepository manifestRepository, List<MetamodelProjection> availableProjections) {
        this(canonicalRepository, targetRepository, manifestRepository, availableProjections, new MetamodelProjectionProperties());
    }

    /**
     * Sincroniza únicamente las proyecciones ON_SAVE. Los fallos se devuelven
     * como resultado y nunca deshacen el guardado canónico ya completado.
     */
    /** Descripción: Actualiza el estado gestionado por ProjectionCoordinator mediante la operación «synchronizeAfterSave». Entradas: metamodel: dato de entrada necesario para la operación; canonicalEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectionResult> synchronizeAfterSave(MetaModel metamodel, String canonicalEtag) {
        return synchronizeAfterSave(metamodel, canonicalEtag, null);
    }

    /**
     * Sincroniza las proyecciones usando, cuando se proporciona, la configuración
     * obtenida de la misma importación o migración que produjo el MetaModel.
     */
    /** Descripción: Actualiza el estado gestionado por ProjectionCoordinator mediante la operación «synchronizeAfterSave». Entradas: metamodel: dato de entrada necesario para la operación; canonicalEtag: dato de entrada necesario para la operación; requestedTargets: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectionResult> synchronizeAfterSave(MetaModel metamodel, String canonicalEtag, TargetConfigurationDocument requestedTargets) {
        Map<String, ProjectionResult> results = new LinkedHashMap<>();
        TargetConfigurationDocument configuration;

        try {
            configuration = loadOrCreateTargets(metamodel, requestedTargets);
        } catch (RuntimeException exception) {
            ProjectionResult failed = ProjectionResult.failed(EcoreMirrorService.TARGET_ID, safeMessage(exception));
            results.put(failed.targetId(), failed);
            saveManifestBestEffort(metamodel.id(), canonicalEtag, results);
            return List.copyOf(results.values());
        }

        MetamodelProjection ecoreProjection = projections.get(EcoreMirrorService.TARGET_ID);

        if (ecoreProjection != null && configuration.ecore() != null) {
            EcoreProjectionConfiguration ecore = configuration.ecore();
            ProjectionResult result;

            if (!ecore.enabled() || ecore.generationMode() == EcoreGenerationMode.DISABLED) {
                result = ProjectionResult.notRequested(EcoreMirrorService.TARGET_ID);
            } else if (ecore.generationMode() == EcoreGenerationMode.MANUAL) {
                result = ProjectionResult.stale(EcoreMirrorService.TARGET_ID, ecore.fileName());
            } else {
                result = safeGenerate(ecoreProjection, metamodel, configuration);
            }

            results.put(ecoreProjection.targetId(), result);
        }

        saveManifestBestEffort(metamodel.id(), canonicalEtag, results);
        return List.copyOf(results.values());
    }

    /** Descripción: Ejecuta la operación «regenerateEcore» correspondiente a la responsabilidad de ProjectionCoordinator. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionResult regenerateEcore(String metamodelId) {
        StoredMetamodel stored = requiredStored(metamodelId);
        TargetConfigurationDocument configuration = loadOrCreateTargets(stored.metamodel());
        MetamodelProjection projection = projections.get(EcoreMirrorService.TARGET_ID);

        if (projection == null) {
            return ProjectionResult.failed(EcoreMirrorService.TARGET_ID, "Ecore projection generator is not registered");
        }

        ProjectionResult result = safeGenerate(projection, stored.metamodel(), configuration);
        Map<String, ProjectionResult> results = new LinkedHashMap<>(findManifest(metamodelId).projections());
        results.put(result.targetId(), result);
        saveManifestRequired(new ProjectionManifest(ProjectionManifest.CURRENT_SCHEMA_VERSION, metamodelId, stored.etag(), Instant.now(), results));
        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «findManifest» dentro de ProjectionCoordinator. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionManifest findManifest(String metamodelId) {
        StoredMetamodel stored = requiredStored(metamodelId);

        try {
            return manifestRepository.findByMetamodelId(metamodelId)
                    .orElseGet(() -> new ProjectionManifest(ProjectionManifest.CURRENT_SCHEMA_VERSION, metamodelId, stored.etag(), Instant.now(), Map.of()));
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw projectionStorageError("Could not read projection manifest: " + metamodelId, exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «findTargets» dentro de ProjectionCoordinator. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TargetConfigurationDocument findTargets(String metamodelId) {
        StoredMetamodel stored = requiredStored(metamodelId);

        try {
            return targetRepository.findByMetamodelId(metamodelId)
                    .orElseGet(() -> TargetConfigurationDocument.defaultsFor(stored.metamodel()))
                    .normalizedFor(stored.metamodel());
        } catch (CanonicalMetamodelRepositoryException | IllegalArgumentException exception) {
            throw projectionStorageError("Could not read target configuration: " + metamodelId, exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por ProjectionCoordinator mediante la operación «saveEcoreConfiguration». Entradas: metamodelId: identificador que delimita el recurso objetivo; ecore: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TargetConfigurationDocument saveEcoreConfiguration(String metamodelId, EcoreProjectionConfiguration ecore) {
        if (ecore == null) {
            throw new MetamodelProjectionException(MetamodelProjectionException.Reason.INVALID_REQUEST, "Ecore projection configuration must not be null");
        }

        StoredMetamodel stored = requiredStored(metamodelId);

        try {
            TargetConfigurationDocument current = targetRepository.findByMetamodelId(metamodelId)
                    .orElseGet(() -> TargetConfigurationDocument.defaultsFor(stored.metamodel()))
                    .normalizedFor(stored.metamodel());
            TargetConfigurationDocument updated = new TargetConfigurationDocument(current.schemaVersion(), metamodelId, ecore, current.additionalTargets())
                    .normalizedFor(stored.metamodel());

            if (current.ecore() != null && !current.ecore().fileName().equals(updated.ecore().fileName())) {
                MetamodelProjection projection = projections.get(EcoreMirrorService.TARGET_ID);

                if (projection != null) {
                    projection.deleteArtifact(metamodelId, current);
                }
            }

            targetRepository.save(updated);
            ProjectionManifest manifest = manifestRepository.findByMetamodelId(metamodelId)
                    .orElseGet(() -> new ProjectionManifest(ProjectionManifest.CURRENT_SCHEMA_VERSION, metamodelId, stored.etag(), Instant.now(), Map.of()));
            Map<String, ProjectionResult> results = new LinkedHashMap<>(manifest.projections());
            ProjectionResult status = !updated.ecore().enabled() || updated.ecore().generationMode() == EcoreGenerationMode.DISABLED
                    ? ProjectionResult.notRequested(EcoreMirrorService.TARGET_ID)
                    : ProjectionResult.stale(EcoreMirrorService.TARGET_ID, updated.ecore().fileName());
            results.put(EcoreMirrorService.TARGET_ID, status);
            saveManifestRequired(new ProjectionManifest(manifest.schemaVersion(), metamodelId, stored.etag(), Instant.now(), results));
            return updated;
        } catch (MetamodelProjectionException exception) {
            throw exception;
        } catch (CanonicalMetamodelRepositoryException | IllegalArgumentException exception) {
            throw projectionStorageError("Could not save Ecore target configuration: " + metamodelId, exception);
        } catch (RuntimeException exception) {
            throw projectionStorageError("Could not update the Ecore projection artifact: " + metamodelId, exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteFor» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteFor(String metamodelId) {
        TargetConfigurationDocument configuration;

        try {
            configuration = targetRepository.findByMetamodelId(metamodelId).orElse(null);
        } catch (RuntimeException exception) {
            configuration = null;
        }

        List<String> failures = new ArrayList<>();

        for (MetamodelProjection projection : projections.values()) {
            try {
                projection.deleteArtifact(metamodelId, configuration);
            } catch (RuntimeException exception) {
                failures.add(projection.targetId() + ": " + safeMessage(exception));
            }
        }

        try {
            manifestRepository.delete(metamodelId);
        } catch (RuntimeException exception) {
            failures.add("manifest: " + safeMessage(exception));
        }

        try {
            targetRepository.delete(metamodelId);
        } catch (RuntimeException exception) {
            failures.add("targets: " + safeMessage(exception));
        }

        if (!failures.isEmpty()) {
            throw new MetamodelProjectionException(MetamodelProjectionException.Reason.STORAGE_ERROR, "Could not delete all projection artifacts for " + metamodelId + ": " + String.join("; ", failures));
        }
    }

    /** Descripción: Obtiene la información correspondiente a «loadOrCreateTargets» dentro de ProjectionCoordinator. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TargetConfigurationDocument loadOrCreateTargets(MetaModel metamodel) {
        return loadOrCreateTargets(metamodel, null);
    }

    /** Descripción: Obtiene la información correspondiente a «loadOrCreateTargets» dentro de ProjectionCoordinator. Entradas: metamodel: dato de entrada necesario para la operación; requestedTargets: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TargetConfigurationDocument loadOrCreateTargets(MetaModel metamodel, TargetConfigurationDocument requestedTargets) {
        try {
            TargetConfigurationDocument configuration = requestedTargets;

            if (configuration == null) {
                configuration = targetRepository.findByMetamodelId(metamodel.id()).orElse(null);
            }

            if (configuration == null) {
                configuration = new TargetConfigurationDocument(TargetConfigurationDocument.CURRENT_SCHEMA_VERSION, metamodel.id(), projectionProperties.defaultsFor(metamodel), Map.of());
            }

            TargetConfigurationDocument normalized = configuration.normalizedFor(metamodel);
            targetRepository.save(normalized);
            return normalized;
        } catch (CanonicalMetamodelRepositoryException | IllegalArgumentException exception) {
            throw projectionStorageError("Could not prepare target configuration: " + metamodel.id(), exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «requiredStored» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredMetamodel requiredStored(String metamodelId) {
        try {
            return canonicalRepository.findById(metamodelId)
                    .orElseThrow(() -> new MetamodelProjectionException(MetamodelProjectionException.Reason.NOT_FOUND, "Canonical metamodel was not found: " + metamodelId));
        } catch (CanonicalMetamodelRepositoryException exception) {
            MetamodelProjectionException.Reason reason = exception.getReason() == CanonicalMetamodelRepositoryException.Reason.INVALID_REQUEST
                    ? MetamodelProjectionException.Reason.INVALID_REQUEST
                    : exception.getReason() == CanonicalMetamodelRepositoryException.Reason.NOT_FOUND
                            ? MetamodelProjectionException.Reason.NOT_FOUND
                            : MetamodelProjectionException.Reason.STORAGE_ERROR;
            throw new MetamodelProjectionException(reason, exception.getMessage(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «safeGenerate» correspondiente a la responsabilidad de ProjectionCoordinator. Entradas: projection: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectionResult safeGenerate(MetamodelProjection projection, MetaModel metamodel, TargetConfigurationDocument configuration) {
        try {
            return projection.generate(metamodel, configuration);
        } catch (RuntimeException exception) {
            return ProjectionResult.failed(projection.targetId(), safeMessage(exception));
        }
    }

    /** Descripción: Actualiza el estado gestionado por ProjectionCoordinator mediante la operación «saveManifestBestEffort». Entradas: metamodelId: identificador que delimita el recurso objetivo; canonicalEtag: dato de entrada necesario para la operación; results: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void saveManifestBestEffort(String metamodelId, String canonicalEtag, Map<String, ProjectionResult> results) {
        try {
            manifestRepository.save(new ProjectionManifest(ProjectionManifest.CURRENT_SCHEMA_VERSION, metamodelId, canonicalEtag, Instant.now(), results));
        } catch (RuntimeException exception) {
            ProjectionResult current = results.get(EcoreMirrorService.TARGET_ID);

            if (current != null) {
                List<TransformationWarning> warnings = new ArrayList<>(current.warnings());
                warnings.add(new TransformationWarning("PROJECTION_MANIFEST_NOT_SAVED", "$.projections", "Projection manifest could not be saved: " + safeMessage(exception)));
                results.put(current.targetId(), new ProjectionResult(current.targetId(), current.status(), current.artifactPath(), warnings, current.errorMessage()));
            }
        }
    }

    /** Descripción: Actualiza el estado gestionado por ProjectionCoordinator mediante la operación «saveManifestRequired». Entradas: manifest: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void saveManifestRequired(ProjectionManifest manifest) {
        try {
            manifestRepository.save(manifest);
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw projectionStorageError("Could not save projection manifest: " + manifest.metamodelId(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «projectionStorageError» correspondiente a la responsabilidad de ProjectionCoordinator. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelProjectionException projectionStorageError(String message, RuntimeException cause) {
        return new MetamodelProjectionException(MetamodelProjectionException.Reason.STORAGE_ERROR, message, cause);
    }

    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de ProjectionCoordinator. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}

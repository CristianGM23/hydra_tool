package com.example.editor.metamodel.application;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.editor.metamodel.canonical.CanonicalMetamodelRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException;
import com.example.editor.metamodel.canonical.CanonicalMetamodelSummary;
import com.example.editor.metamodel.canonical.StoredMetamodel;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.ValidationReport;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.projection.ProjectionResult;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalization;
import com.example.editor.metamodel.importing.canonicalization.ImportedMetamodelCanonicalizer;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.contracts.metamodel.MetamodelQueryPort;

/**
 * Coordina las operaciones canónicas sin conocer Ecore, diagramas ni React.
 *
 * El servicio valida el significado canónico, persiste siempre el contrato MetaModel actual
 * y coordina después las proyecciones configuradas sin revertir el canónico.
 */
@Service
public class CanonicalMetamodelApplicationService implements MetamodelQueryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanonicalMetamodelApplicationService.class);

    private final CanonicalMetamodelRepository repository;
    private final NeutralMetamodelValidator validator;
    private final ProjectionCoordinator projectionCoordinator;
    private final SemanticContextMetamodelResolver semanticContextResolver;

    @Autowired
    /** Descripción: Inicializa una instancia de CanonicalMetamodelApplicationService con persistencia canónica, proyecciones y resolución opcional de contextos grammar-first. Entradas: repository: repositorio canónico; validator: validador neutral; projectionCoordinator: coordinador de destinos; semanticContextResolver: resolver transitorio opcional. Salida: deja la instancia preparada sin persistir proyecciones grammar-first. */ public CanonicalMetamodelApplicationService(CanonicalMetamodelRepository repository, NeutralMetamodelValidator validator, ProjectionCoordinator projectionCoordinator, Optional<SemanticContextMetamodelResolver> semanticContextResolver) {
        this.repository = repository;
        this.validator = validator;
        this.projectionCoordinator = projectionCoordinator;
        this.semanticContextResolver = semanticContextResolver == null ? null : semanticContextResolver.orElse(null);
    }

    /** Constructor compatible para pruebas y clientes que no necesitan resolver contextos transitorios. */
    /** Descripción: Inicializa una instancia de CanonicalMetamodelApplicationService sin resolver gramáticas transitorias. Entradas: repository: repositorio canónico; validator: validador neutral; projectionCoordinator: coordinador de destinos. Salida: deja la instancia preparada con el comportamiento canónico anterior. */ public CanonicalMetamodelApplicationService(CanonicalMetamodelRepository repository, NeutralMetamodelValidator validator, ProjectionCoordinator projectionCoordinator) {
        this(repository, validator, projectionCoordinator, Optional.empty());
    }

    /** Constructor unitario para pruebas sin infraestructura de proyección. */
    /** Descripción: Inicializa una instancia de CanonicalMetamodelApplicationService con sus dependencias mínimas. Entradas: repository: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalMetamodelApplicationService(CanonicalMetamodelRepository repository, NeutralMetamodelValidator validator) {
        this(repository, validator, null, Optional.empty());
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalMetamodelApplicationService. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelSaveResponse create(MetaModel metamodel) {
        return create(metamodel, null);
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalMetamodelApplicationService. Entradas: metamodel: dato de entrada necesario para la operación; requestedTargets: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelSaveResponse create(MetaModel metamodel, TargetConfigurationDocument requestedTargets) {
        PreparedForSave prepared = prepareForSave(metamodel, requestedTargets);

        try {
            StoredMetamodel stored = repository.create(prepared.metamodel());
            List<ProjectionResult> projections = projectionCoordinator == null ? List.of() : projectionCoordinator.synchronizeAfterSave(stored.metamodel(), stored.etag(), prepared.targets());
            return new MetamodelSaveResponse(stored.metamodel(), prepared.validation(), projections, stored.etag());
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por CanonicalMetamodelApplicationService mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelSaveResponse replace(String id, MetaModel metamodel, String expectedEtag) {
        return replace(id, metamodel, expectedEtag, null);
    }

    /** Descripción: Actualiza el estado gestionado por CanonicalMetamodelApplicationService mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación; requestedTargets: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelSaveResponse replace(String id, MetaModel metamodel, String expectedEtag, TargetConfigurationDocument requestedTargets) {
        if (id == null || id.isBlank()) {
            throw new MetamodelApplicationException(MetamodelApplicationException.Reason.INVALID_REQUEST, "Canonical metamodel id must not be empty");
        }

        if (metamodel == null || !id.equals(metamodel.id())) {
            throw new MetamodelApplicationException(MetamodelApplicationException.Reason.INVALID_REQUEST, "Path id and metamodel id must match");
        }

        PreparedForSave prepared = prepareForSave(metamodel, requestedTargets);

        try {
            StoredMetamodel stored = repository.replace(id, prepared.metamodel(), expectedEtag);
            List<ProjectionResult> projections = projectionCoordinator == null ? List.of() : projectionCoordinator.synchronizeAfterSave(stored.metamodel(), stored.etag(), prepared.targets());
            return new MetamodelSaveResponse(stored.metamodel(), prepared.validation(), projections, stored.etag());
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Obtiene un metamodelo canónico o, para grammar.<id>, su proyección semántica transitoria. Entradas: id: identificador del contexto solicitado. Salida: devuelve la estructura neutral sin persistir un metamodelo artificial para las gramáticas. */ public MetamodelLoadResult findById(String id) {
        try {
            Optional<StoredMetamodel> stored = repository.findById(id);
            if(stored.isPresent()) return new MetamodelLoadResult(stored.get().metamodel(), stored.get().etag());
            if(semanticContextResolver != null) {
                Optional<MetamodelLoadResult> semantic = semanticContextResolver.resolve(id);
                if(semantic.isPresent()) return semantic.get();
            }
            throw new MetamodelApplicationException(MetamodelApplicationException.Reason.NOT_FOUND, "Canonical metamodel or semantic context was not found: " + id);
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Implementa el puerto neutral de consulta y exige que el contexto exista. Entradas: id: identificador. Salida: metamodelo neutral. */ @Override public MetaModel requireMetamodel(String id){return findById(id).metamodel();}

    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de CanonicalMetamodelApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<CanonicalMetamodelSummary> findAll() {
        try {
            return repository.findAll();
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateStored» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationReport validateStored(String id) {
        return validator.validate(findById(id).metamodel());
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationReport validate(MetaModel metamodel) {
        return validator.validate(metamodel);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String expectedEtag) {
        try {
            repository.delete(id, expectedEtag);
        } catch (CanonicalMetamodelRepositoryException exception) {
            throw translate(exception);
        }

        if (projectionCoordinator != null) {
            try {
                projectionCoordinator.deleteFor(id);
            } catch (RuntimeException exception) {
                LOGGER.warn("Canonical metamodel was deleted but some derived projection artifacts could not be removed: {}", id, exception);
            }
        }
    }

    /** Descripción: Ejecuta la operación «prepareForSave» correspondiente a la responsabilidad de CanonicalMetamodelApplicationService. Entradas: metamodel: dato de entrada necesario para la operación; requestedTargets: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PreparedForSave prepareForSave(MetaModel metamodel, TargetConfigurationDocument requestedTargets) {
        ImportedMetamodelCanonicalization canonicalization;

        try {
            canonicalization = metamodel == null ? null : ImportedMetamodelCanonicalizer.prepare(metamodel);
        } catch (IllegalArgumentException exception) {
            throw new MetamodelApplicationException(MetamodelApplicationException.Reason.INVALID_REQUEST, exception.getMessage(), exception);
        }

        MetaModel canonical = canonicalization == null ? null : canonicalization.metamodel();
        TargetConfigurationDocument effectiveTargets = requestedTargets;

        if (effectiveTargets == null && metamodel != null
                && ImportedMetamodelCanonicalizer.containsEcoreImportMetadata(metamodel)) {
            effectiveTargets = canonicalization.targetConfiguration();
        }

        try {
            if (effectiveTargets != null) {
                effectiveTargets = effectiveTargets.normalizedFor(canonical);
            }
        } catch (IllegalArgumentException exception) {
            throw new MetamodelApplicationException(MetamodelApplicationException.Reason.INVALID_REQUEST, exception.getMessage(), exception);
        }

        ValidationReport validation = validator.validate(canonical);

        if (validation.hasErrors()) {
            String codes = validation.errors().stream().map(issue -> issue.code()).distinct().sorted().reduce((left, right) -> left + ", " + right).orElse("UNKNOWN");
            throw new MetamodelApplicationException(MetamodelApplicationException.Reason.VALIDATION_ERROR, "Canonical metamodel validation failed: " + codes);
        }

        return new PreparedForSave(canonical, validation, effectiveTargets);
    }

    private record PreparedForSave(MetaModel metamodel, ValidationReport validation, TargetConfigurationDocument targets) {
    }

    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de CanonicalMetamodelApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelApplicationException translate(CanonicalMetamodelRepositoryException exception) {
        MetamodelApplicationException.Reason reason = switch (exception.getReason()) {
            case INVALID_REQUEST -> MetamodelApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> MetamodelApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> MetamodelApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> MetamodelApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> MetamodelApplicationException.Reason.STORAGE_ERROR;
        };

        return new MetamodelApplicationException(reason, exception.getMessage(), exception);
    }
}

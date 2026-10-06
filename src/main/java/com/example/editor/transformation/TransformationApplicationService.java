package com.example.editor.transformation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.artifact.transaction.HydraArtifactTransactionOperation;
import com.example.editor.artifact.transaction.HydraArtifactTransactionService;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationDiagnosticSeverity;
import com.example.editor.transformation.core.TransformationLoadResult;
import com.example.editor.transformation.core.TransformationSaveResponse;
import com.example.editor.transformation.core.TransformationSummary;
import com.example.editor.transformation.persistence.TransformationRepository;
import com.example.editor.transformation.testing.persistence.TransformationTestCaseRepository;
import com.example.editor.transformation.sync.TransformationSynchronizationSessionRepository;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

@Service
public class TransformationApplicationService {
    private final TransformationRepository repository;
    private final CanonicalMetamodelApplicationService metamodels;
    private final MetamodelValidationFingerprint fingerprint;
    private final TransformationDefinitionChecker checker;
    private final TransformationTestCaseRepository testCases;
    private final TransformationSynchronizationSessionRepository synchronizationSessions;
    private final HydraArtifactTransactionService transactions;

    /** Descripción: Inicializa una instancia de TransformationApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; fingerprint: dato de entrada necesario para la operación; checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationApplicationService(TransformationRepository repository,
            CanonicalMetamodelApplicationService metamodels,
            MetamodelValidationFingerprint fingerprint,
            TransformationDefinitionChecker checker,
            TransformationTestCaseRepository testCases,
            TransformationSynchronizationSessionRepository synchronizationSessions,
            HydraArtifactTransactionService transactions) {
        this.repository = repository;
        this.metamodels = metamodels;
        this.fingerprint = fingerprint;
        this.checker = checker;
        this.testCases = testCases;
        this.synchronizationSessions = synchronizationSessions;
        this.transactions = transactions;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de Prepared. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSaveResponse create(TransformationDefinitionDocument document) {
        Prepared prepared = prepare(document);
        requireValid(prepared.report());
        return transactions.execute(HydraArtifactTransactionOperation.CREATE, prepared.document().id(),
                List.of("transformation:" + prepared.document().id()), () -> {
                    try {
                        StoredCanonicalDocument<TransformationDefinitionDocument> stored = repository.create(prepared.document());
                        return new TransformationSaveResponse(stored.document(), prepared.report(), stored.etag());
                    } catch(CanonicalDocumentStoreException exception) {
                        throw translate(exception);
                    }
                });
    }

    /** Descripción: Actualiza el estado gestionado por Prepared mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSaveResponse replace(String id, TransformationDefinitionDocument document, String etag) {
        if(document == null || !id.equals(document.id())) throw new TransformationApplicationException(
                TransformationApplicationException.Reason.INVALID_REQUEST, "Path id and transformation id must match");
        Prepared prepared = prepare(document);
        requireValid(prepared.report());
        return transactions.execute(HydraArtifactTransactionOperation.UPDATE, id, List.of("transformation:" + id), () -> {
            try {
                StoredCanonicalDocument<TransformationDefinitionDocument> stored = repository.replace(id, prepared.document(), etag);
                return new TransformationSaveResponse(stored.document(), prepared.report(), stored.etag());
            } catch(CanonicalDocumentStoreException exception) {
                throw translate(exception);
            }
        });
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de Prepared. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationLoadResult find(String id) {
        try {
            StoredCanonicalDocument<TransformationDefinitionDocument> stored = repository.find(id)
                    .orElseThrow(() -> new TransformationApplicationException(TransformationApplicationException.Reason.NOT_FOUND,
                            "Transformation was not found: " + id));
            Prepared prepared = prepare(stored.document());
            return new TransformationLoadResult(prepared.document(), stored.etag(), prepared.report());
        } catch(CanonicalDocumentStoreException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «list» dentro de Prepared. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationSummary> list() {
        try {
            return repository.listIds().stream().map(id -> repository.find(id).orElseThrow()).map(stored -> {
                TransformationDefinitionDocument value = stored.document();
                String sourceId = value.sources().isEmpty() ? "" : value.sources().get(0).metamodelId();
                String targetId = value.targets().isEmpty() ? "" : value.targets().get(0).metamodelId();
                return new TransformationSummary(value.id(), value.name(), value.kind(), value.directionality(), sourceId, targetId, value.rules().size(), stored.etag());
            }).toList();
        } catch(CanonicalDocumentStoreException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationCheckReport check(TransformationDefinitionDocument document) {
        return prepare(document).report();
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String etag) {
        if(hasPersistedTests(id)) throw new TransformationApplicationException(TransformationApplicationException.Reason.CONFLICT,
                "Delete the transformation test cases before deleting the transformation");
        if(hasSynchronizationSessions(id)) throw new TransformationApplicationException(TransformationApplicationException.Reason.CONFLICT,
                "Delete the transformation synchronization sessions before deleting the transformation");
        transactions.execute(HydraArtifactTransactionOperation.DELETE, id, List.of("transformation:" + id), () -> {
            try {
                repository.delete(id, etag);
            } catch(CanonicalDocumentStoreException exception) {
                throw translate(exception);
            }
        });
    }

    /** Descripción: Comprueba si existen casos persistidos asociados a una transformación para evitar huérfanos. Entradas: transformationId: identificador de transformación. Salida: devuelve true cuando existe al menos un caso. */ private boolean hasPersistedTests(String transformationId) {
        try {
            return testCases.listIds().stream().map(testCases::find).flatMap(java.util.Optional::stream)
                    .anyMatch(stored -> transformationId.equals(stored.document().transformationId()));
        } catch(CanonicalDocumentStoreException exception) {
            throw translate(exception);
        }
    }

/** Descripción: Evalúa la condición «hasSynchronizationSessions» sin modificar el estado externo. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasSynchronizationSessions(String transformationId) {
        try {
            return synchronizationSessions.listSessionIds().stream().map(synchronizationSessions::findSession).flatMap(java.util.Optional::stream)
                    .anyMatch(stored -> transformationId.equals(stored.document().transformationId()));
        } catch(CanonicalDocumentStoreException exception) {
            throw translate(exception);
        }
    }

    /** Descripción: Ejecuta la operación «prepare» correspondiente a la responsabilidad de Prepared. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Prepared prepare(TransformationDefinitionDocument document) {
        if(document == null) return new Prepared(null, checker.check(null, List.of(), List.of()));
        List<MetaModel> sourceModels = load(document.sources());
        List<MetaModel> targetModels = load(document.targets());
        List<TransformationEndpoint> refreshedSources = refresh(document.sources(), sourceModels);
        List<TransformationEndpoint> refreshedTargets = refresh(document.targets(), targetModels);
        TransformationDefinitionDocument normalized = new TransformationDefinitionDocument(
                TransformationDefinitionDocument.CURRENT_SCHEMA_VERSION, document.id(), document.name(), document.kind(), document.directionality(), document.synchronizationPolicy(),
                refreshedSources, refreshedTargets,
                document.rules(), document.generationRules(), document.textToModel(), document.executionPolicy(), document.tracePolicy(), document.annotations());
        TransformationCheckReport checked = checker.check(normalized, sourceModels, targetModels);
        List<TransformationDiagnostic> diagnostics = new ArrayList<>(checked.diagnostics());
        diagnostics.addAll(fingerprintChanges(document.sources(), refreshedSources, "$.sources"));
        diagnostics.addAll(fingerprintChanges(document.targets(), refreshedTargets, "$.targets"));
        return new Prepared(normalized, new TransformationCheckReport(normalized.id(), diagnostics));
    }

    /** Descripción: Obtiene la información correspondiente a «load» dentro de Prepared. Entradas: endpoints: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaModel> load(List<TransformationEndpoint> endpoints) {
        List<MetaModel> result = new ArrayList<>();
        for(TransformationEndpoint endpoint : endpoints) {
            if(endpoint.metamodelId().isBlank()) continue;
            try {
                result.add(metamodels.findById(endpoint.metamodelId()).metamodel());
            } catch(RuntimeException exception) {
                // The checker reports structural problems. Missing semantic contexts are
                // translated here to a stable application error because execution and save
                // cannot be correct without resolving every declared endpoint.
                throw new TransformationApplicationException(TransformationApplicationException.Reason.INVALID_REQUEST,
                        "Transformation endpoint metamodel was not found: " + endpoint.metamodelId(), exception);
            }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «refresh» correspondiente a la responsabilidad de Prepared. Entradas: endpoints: dato de entrada necesario para la operación; models: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationEndpoint> refresh(List<TransformationEndpoint> endpoints, List<MetaModel> models) {
        List<TransformationEndpoint> result = new ArrayList<>();
        int modelIndex = 0;
        for(TransformationEndpoint endpoint : endpoints) {
            if(endpoint.metamodelId().isBlank() || modelIndex >= models.size()) {
                result.add(endpoint);
                continue;
            }
            MetaModel model = models.get(modelIndex++);
            result.add(new TransformationEndpoint(endpoint.id(), endpoint.role(), endpoint.metamodelId(),
                    fingerprint.of(model), endpoint.alias(), endpoint.required(), endpoint.annotations()));
        }
        return List.copyOf(result);
    }

    /** Descripción: Compara las huellas persistidas y actuales de los metamodelos asociados y genera avisos de evolución sin ocultar la nueva versión resuelta. Entradas: original: endpoints persistidos; refreshed: endpoints resueltos contra el catálogo actual; path: ruta JSON de la colección. Salida: devuelve los diagnósticos de metamodelos modificados desde la última persistencia. */ private List<TransformationDiagnostic> fingerprintChanges(
            List<TransformationEndpoint> original, List<TransformationEndpoint> refreshed, String path) {
        List<TransformationDiagnostic> diagnostics = new ArrayList<>();
        int size = Math.min(original.size(), refreshed.size());
        for(int index = 0; index < size; index++) {
            TransformationEndpoint before = original.get(index);
            TransformationEndpoint after = refreshed.get(index);
            if(before.metamodelFingerprint().isBlank() || before.metamodelFingerprint().equals(after.metamodelFingerprint())) continue;
            diagnostics.add(new TransformationDiagnostic(TransformationDiagnosticSeverity.WARNING,
                    "ENDPOINT_METAMODEL_CHANGED", "", "", path + "[" + index + "].metamodelFingerprint",
                    "Metamodel of endpoint '" + (after.alias().isBlank() ? after.id() : after.alias()) + "' changed",
                    "Endpoint '" + (after.alias().isBlank() ? after.id() : after.alias()) + "' (id '" + after.id()
                            + "') still references metamodelId='" + after.metamodelId() + "', but its fingerprint changed from '"
                            + shortFingerprint(before.metamodelFingerprint()) + "' to '" + shortFingerprint(after.metamodelFingerprint())
                            + "'. Classes, attributes or relations used by existing rules may therefore have been renamed, removed or changed.",
                    "Run Check now and review every diagnostic under this endpoint. In particular, verify source/target type IDs, binding feature IDs, multiplicities and relation mappings. Preview representative models before saving the refreshed fingerprint; do not acknowledge this warning without checking the affected mappings."));
        }
        return List.copyOf(diagnostics);
    }

    /** Returns a bounded fingerprint prefix suitable for diagnostics without exposing oversized hashes. */
    /** Descripción: Ejecuta la operación «shortFingerprint» correspondiente a la responsabilidad de Prepared. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String shortFingerprint(String value) {
        if(value == null || value.isBlank()) return "<empty>";
        return value.length() <= 12 ? value : value.substring(0, 12) + "…";
    }

    /** Descripción: Valida las condiciones necesarias para «requireValid» y rechaza entradas no válidas. Entradas: report: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireValid(TransformationCheckReport report) {
        if(!report.valid()) throw new TransformationApplicationException(TransformationApplicationException.Reason.INVALID_REQUEST,
                "Transformation contains blocking diagnostics");
    }

    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de Prepared. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationApplicationException translate(CanonicalDocumentStoreException exception) {
        TransformationApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> TransformationApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> TransformationApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> TransformationApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> TransformationApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> TransformationApplicationException.Reason.STORAGE_ERROR;
        };
        return new TransformationApplicationException(reason, exception.getMessage(), exception);
    }

    private record Prepared(TransformationDefinitionDocument document, TransformationCheckReport report) {
    }
}

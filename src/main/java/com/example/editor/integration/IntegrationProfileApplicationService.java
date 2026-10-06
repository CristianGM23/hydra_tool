package com.example.editor.integration;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.integration.persistence.IntegrationProfileRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Orquesta persistencia y comprobación de perfiles de integración. */
@Service
public class IntegrationProfileApplicationService {
    private final IntegrationProfileRepository repository;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final IntegrationProfileChecker checker;
    private final GrammarRepository grammars;
    /** Descripción: Inicializa una instancia de IntegrationProfileApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public IntegrationProfileApplicationService(IntegrationProfileRepository repository,
            CanonicalMetamodelApplicationService metamodelService, IntegrationProfileChecker checker, GrammarRepository grammars) {
        this.repository = repository; this.metamodelService = metamodelService; this.checker = checker; this.grammars = grammars;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de IntegrationProfileApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationProfileSaveResponse create(String metamodelId, IntegrationProfileModel profile) {
        requireCompatible(metamodelId, profile);
        IntegrationCheckReport report = checker.check(profile);
        if(!report.valid()) throw new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.INVALID_REQUEST, "Integration profile contains blocking errors");
        try {
            StoredCanonicalDocument<IntegrationProfileModel> stored = repository.create(profile);
            return new IntegrationProfileSaveResponse(stored.document(), stored.etag(), report);
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Actualiza el estado gestionado por IntegrationProfileApplicationService mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationProfileSaveResponse replace(String metamodelId, String profileId, IntegrationProfileModel profile, String expectedEtag) {
        requireCompatible(metamodelId, profile);
        if(!profileId.equals(profile.id())) throw new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.INVALID_REQUEST, "Path profile id and document id must match");
        IntegrationCheckReport report = checker.check(profile);
        if(!report.valid()) throw new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.INVALID_REQUEST, "Integration profile contains blocking errors");
        try {
            StoredCanonicalDocument<IntegrationProfileModel> stored = repository.replace(profileId, profile, expectedEtag);
            return new IntegrationProfileSaveResponse(stored.document(), stored.etag(), report);
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de IntegrationProfileApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationProfileLoadResult find(String metamodelId, String profileId) {
        requireSemanticScope(metamodelId);
        try {
            StoredCanonicalDocument<IntegrationProfileModel> stored = repository.findById(profileId)
                    .orElseThrow(() -> new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.NOT_FOUND, "Integration profile was not found: " + profileId));
            if(!metamodelId.equals(stored.document().semanticScopeId())) throw new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.NOT_FOUND, "Integration profile does not belong to semantic scope: " + metamodelId);
            return new IntegrationProfileLoadResult(stored.document(), stored.etag(), checker.check(stored.document()));
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «list» dentro de IntegrationProfileApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<IntegrationProfileSummary> list(String metamodelId) {
        requireSemanticScope(metamodelId);
        try {
            return repository.listIds().stream().map(id -> repository.findById(id).orElseThrow())
                    .filter(item -> item.document().semanticScopeId().equals(metamodelId))
                    .map(item -> new IntegrationProfileSummary(item.document().id(), item.document().metamodelId(), item.document().name(),
                            item.document().target().technology(), item.document().deliveryMode(), item.document().features().size(),
                            item.document().mappings().size(), item.etag())).toList();
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationCheckReport check(String metamodelId, String profileId) { return find(metamodelId, profileId).checkReport(); }
    /** Descripción: Valida las condiciones necesarias para «checkDraft» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationCheckReport checkDraft(String metamodelId, IntegrationProfileModel profile) { requireCompatible(metamodelId, profile); return checker.check(profile); }
    /** Descripción: Ejecuta la operación «technologies» correspondiente a la responsabilidad de IntegrationProfileApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<IntegrationTechnologyDescriptor> technologies() { return IntegrationTechnologyCatalog.descriptors(); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String metamodelId, String profileId, String expectedEtag) {
        find(metamodelId, profileId);
        try { repository.delete(profileId, expectedEtag); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «requireCompatible» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireCompatible(String metamodelId, IntegrationProfileModel profile) {
        requireSemanticScope(metamodelId);
        if(profile == null) throw new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.INVALID_REQUEST, "Integration profile is required");
        if(!metamodelId.equals(profile.semanticScopeId())) throw new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.INVALID_REQUEST, "Integration profile semanticScopeId must match the path");
    }
    /** Descripción: Valida que el ámbito semántico exista como metamodelo canónico o como grammar.<id> sin fabricar un Ecore artificial. Entradas: id: identificador del ámbito semántico raíz. Salida: no devuelve valor; completa la validación o comunica el fallo mediante el contrato existente. */ private void requireSemanticScope(String id) {
        if(id != null && id.startsWith("grammar.")) {
            String grammarId=id.substring("grammar.".length());
            if(grammarId.isBlank() || grammars.findById(grammarId).isEmpty()) throw new IntegrationProfileApplicationException(IntegrationProfileApplicationException.Reason.NOT_FOUND, "Grammar semantic scope was not found: " + id);
            return;
        }
        try { metamodelService.findById(id); }
        catch(MetamodelApplicationException exception) { throw new IntegrationProfileApplicationException(exception.getReason() == MetamodelApplicationException.Reason.NOT_FOUND ? IntegrationProfileApplicationException.Reason.NOT_FOUND : IntegrationProfileApplicationException.Reason.INVALID_REQUEST, exception.getMessage(), exception); }
    }
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de IntegrationProfileApplicationService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationProfileApplicationException translate(CanonicalDocumentStoreException exception) {
        IntegrationProfileApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> IntegrationProfileApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> IntegrationProfileApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> IntegrationProfileApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> IntegrationProfileApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> IntegrationProfileApplicationException.Reason.STORAGE_ERROR;
        };
        return new IntegrationProfileApplicationException(reason, exception.getMessage(), exception);
    }
}

package com.example.editor.integration.compiler;

import java.nio.file.Path;
import org.springframework.stereotype.Service;
import com.example.editor.integration.IntegrationProfileApplicationService;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.injection.SafeTechnologyInjectionService;
import com.example.editor.integration.injection.TechnologyInjectionExecutionResult;
import com.example.editor.integration.injection.TechnologyInjectionPreview;
import com.example.editor.integration.injection.TechnologyInjectionProperties;
import com.example.editor.integration.roundtrip.RoundTripSyncPreview;
import com.example.editor.integration.roundtrip.RoundTripSyncRequest;
import com.example.editor.integration.roundtrip.RoundTripSyncService;
import com.example.editor.integration.roundtrip.resolution.ElementConflictDecision;
import com.example.editor.integration.roundtrip.resolution.RoundTripApplyResult;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionPlan;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionWorkflowService;
import com.example.editor.integration.verification.TechnologyProjectValidationReport;
import com.example.editor.integration.verification.TechnologyProjectValidationRequest;
import com.example.editor.integration.verification.TechnologyProjectValidationService;

/** Fachada de aplicación para compilar, validar, sincronizar, aplicar y retirar integraciones. */
@Service
public class TechnologyIntegrationApplicationService {
    private final IntegrationProfileApplicationService profiles;
    private final CanonicalIntegrationArtifactContentLoader artifacts;
    private final TechnologyCompilerService compiler;
    private final TechnologyCompilationEnrichmentService enrichment;
    private final TechnologyCompilationArchiveService archives;
    private final SafeTechnologyInjectionService injection;
    private final TechnologyProjectValidationService validation;
    private final RoundTripSyncService roundTrips;
    private final RoundTripResolutionWorkflowService roundTripResolution;
    private final TechnologyInjectionProperties injectionProperties;

    /** Descripción: Inicializa una instancia de TechnologyIntegrationApplicationService con sus dependencias y datos de entrada. Entradas: profiles: archivo o contenido que debe procesarse; artifacts: dato de entrada necesario para la operación; compiler: dato de entrada necesario para la operación; enrichment: dato de entrada necesario para la operación; archives: archivo o contenido que debe procesarse; injection: dato de entrada necesario para la operación; validation: dato de entrada necesario para la operación; roundTrips: dato de entrada necesario para la operación; roundTripResolution: dato de entrada necesario para la operación; injectionProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyIntegrationApplicationService(IntegrationProfileApplicationService profiles,
            CanonicalIntegrationArtifactContentLoader artifacts, TechnologyCompilerService compiler,
            TechnologyCompilationEnrichmentService enrichment, TechnologyCompilationArchiveService archives,
            SafeTechnologyInjectionService injection, TechnologyProjectValidationService validation,
            RoundTripSyncService roundTrips, RoundTripResolutionWorkflowService roundTripResolution,
            TechnologyInjectionProperties injectionProperties) {
        this.profiles = profiles; this.artifacts = artifacts; this.compiler = compiler; this.enrichment = enrichment;
        this.archives = archives; this.injection = injection; this.validation = validation;
        this.roundTrips = roundTrips; this.roundTripResolution = roundTripResolution; this.injectionProperties = injectionProperties;
    }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de TechnologyIntegrationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompilationResult compile(String metamodelId, String profileId){
        IntegrationProfileModel profile = profiles.find(metamodelId, profileId).profile();
        TechnologyCompilationRequest request = request(profile);
        return enrichment.enrich(request, compiler.compile(request));
    }

    /** Descripción: Ejecuta la operación «compileArchive» correspondiente a la responsabilidad de TechnologyIntegrationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] compileArchive(String metamodelId, String profileId){return archives.createArchive(compile(metamodelId, profileId));}

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyProjectValidationReport validate(String metamodelId, String profileId, TechnologyProjectValidationRequest request){
        return validation.validate(compile(metamodelId, profileId), request);
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de TechnologyIntegrationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; targetDirectory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionPreview preview(String metamodelId, String profileId, String targetDirectory){
        IntegrationProfileModel profile = profiles.find(metamodelId, profileId).profile();
        return injection.preview(profile, compile(metamodelId, profileId), targetDirectory);
    }

    /** Descripción: Actualiza el estado gestionado por TechnologyIntegrationApplicationService mediante la operación «apply». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; targetDirectory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult apply(String metamodelId, String profileId, String targetDirectory){
        IntegrationProfileModel profile = profiles.find(metamodelId, profileId).profile();
        return injection.apply(profile, compile(metamodelId, profileId), targetDirectory);
    }

    /** Descripción: Actualiza el estado gestionado por TechnologyIntegrationApplicationService mediante la operación «syncPreview». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripSyncPreview syncPreview(String metamodelId, String profileId, RoundTripSyncRequest request){
        return roundTrips.preview(compile(metamodelId, profileId), profileId, resolveTarget(request.targetDirectory()));
    }

    /** Descripción: Actualiza el estado gestionado por TechnologyIntegrationApplicationService mediante la operación «syncApply». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult syncApply(String metamodelId, String profileId, RoundTripSyncRequest request){
        IntegrationProfileModel profile = profiles.find(metamodelId, profileId).profile();
        TechnologyCompilationResult compilation = compile(metamodelId, profileId);
        Path target = resolveTarget(request.targetDirectory());
        RoundTripSyncPreview preview = roundTrips.preview(compilation, profileId, target);
        if(preview.blocked()){throw new TechnologyCompilationException("Round-trip synchronization is blocked by manual conflicts");}
        TechnologyCompilationResult selective = roundTrips.selectiveCompilation(compilation, preview, target,
                request.selectedPaths(), request.preserveManualRegions());
        return injection.apply(profile, selective, request.targetDirectory());
    }

    /** Descripción: Ejecuta la operación «prepareRoundTripResolution» correspondiente a la responsabilidad de TechnologyIntegrationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; targetDirectory: dato de entrada necesario para la operación; decisions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripResolutionPlan prepareRoundTripResolution(String metamodelId, String profileId,
            String targetDirectory, java.util.List<ElementConflictDecision> decisions){
        return roundTripResolution.prepare(compile(metamodelId, profileId), profileId,
                resolveTarget(targetDirectory), decisions);
    }

    /** Descripción: Actualiza el estado gestionado por TechnologyIntegrationApplicationService mediante la operación «applyRoundTripResolution». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; targetDirectory: dato de entrada necesario para la operación; decisions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripApplyResult applyRoundTripResolution(String metamodelId, String profileId,
            String targetDirectory, java.util.List<ElementConflictDecision> decisions){
        RoundTripResolutionPlan plan = prepareRoundTripResolution(metamodelId, profileId, targetDirectory, decisions);
        return roundTripResolution.apply(plan);
    }

    /** Descripción: Ejecuta la operación «rollbackRoundTripResolution» correspondiente a la responsabilidad de TechnologyIntegrationApplicationService. Entradas: targetDirectory: dato de entrada necesario para la operación; operationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripApplyResult rollbackRoundTripResolution(String targetDirectory, String operationId){
        return roundTripResolution.rollback(resolveTarget(targetDirectory), operationId);
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de TechnologyIntegrationApplicationService. Entradas: targetDirectory: dato de entrada necesario para la operación; manifestId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult rollback(String targetDirectory, String manifestId){return injection.rollback(targetDirectory, manifestId);}

    /** Descripción: Ejecuta la operación «request» correspondiente a la responsabilidad de TechnologyIntegrationApplicationService. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyCompilationRequest request(IntegrationProfileModel profile){
        return new TechnologyCompilationRequest(profile, artifacts.load(profile),
                com.example.editor.product.HydraProductIdentity.PRODUCT, java.util.Map.of());
    }

    /** Descripción: Obtiene la información correspondiente a «resolveTarget» dentro de TechnologyIntegrationApplicationService. Entradas: targetDirectory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveTarget(String targetDirectory){
        Path allowed = injectionProperties.getAllowedRoot().toAbsolutePath().normalize();
        Path target = allowed.resolve(targetDirectory).normalize();
        if(!target.startsWith(allowed)){throw new TechnologyCompilationException("Target directory escapes the configured injection root");}
        return target;
    }
}

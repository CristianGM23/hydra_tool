package com.example.editor.integration.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.integration.compiler.TechnologyIntegrationApplicationService;
import com.example.editor.integration.injection.TechnologyInjectionExecutionResult;
import com.example.editor.integration.injection.TechnologyInjectionPreview;
import com.example.editor.integration.injection.TechnologyInjectionRequest;
import com.example.editor.integration.roundtrip.RoundTripSyncPreview;
import com.example.editor.integration.roundtrip.RoundTripSyncRequest;
import com.example.editor.integration.roundtrip.resolution.RoundTripApplyResult;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionPlan;
import com.example.editor.integration.verification.TechnologyProjectValidationReport;
import com.example.editor.integration.verification.TechnologyProjectValidationRequest;
import com.example.editor.integration.verification.TechnologyProjectValidationWebRequest;

/** API para descargar, previsualizar, aplicar o revertir módulos tecnológicos generados. */
@RestController
@RequestMapping({
    "/api/canonical-metamodels/{metamodelId}/integrations/{profileId}/technology",
    "/api/semantic-contexts/{metamodelId}/integrations/{profileId}/technology"
})
public class TechnologyIntegrationController {
    private final TechnologyIntegrationApplicationService service;
    /** Descripción: Inicializa una instancia de TechnologyIntegrationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyIntegrationController(TechnologyIntegrationApplicationService service){this.service = service;}

    @PostMapping(value = "/compile", produces = "application/zip")
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de TechnologyIntegrationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> compile(@PathVariable String metamodelId, @PathVariable String profileId){
        byte[] content = service.compileArchive(metamodelId, profileId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(profileId + "-technology.zip").build());
        return ResponseEntity.ok().headers(headers).contentLength(content.length).body(content);
    }


    @PostMapping("/validate")
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyProjectValidationReport validate(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody TechnologyProjectValidationWebRequest request){
        TechnologyProjectValidationRequest validationRequest = new TechnologyProjectValidationRequest(request.mode(), null,
                request.timeoutSeconds(), request.keepWorkspace(), request.environment());
        return service.validate(metamodelId, profileId, validationRequest);
    }

    @PostMapping("/native-parity/certify")
    /**Descripción: Ejecuta la certificación de evidencias nativas para las capacidades solicitadas sin permitir comandos aportados por el navegador. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    public com.example.editor.integration.parity.NativeParityCertificationReport certifyNativeParity(
            @PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody com.example.editor.integration.parity.NativeParityCertificationRequest request) {
        return service.certifyNativeParity(metamodelId, profileId, request);
    }

    @PostMapping("/sync/preview")
    /** Descripción: Actualiza el estado gestionado por TechnologyIntegrationController mediante la operación «syncPreview». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripSyncPreview syncPreview(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody RoundTripSyncRequest request){return service.syncPreview(metamodelId, profileId, request);}

    @PostMapping("/sync/apply")
    /** Descripción: Actualiza el estado gestionado por TechnologyIntegrationController mediante la operación «syncApply». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult syncApply(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody RoundTripSyncRequest request){return service.syncApply(metamodelId, profileId, request);}

    @PostMapping("/sync/resolve")
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de TechnologyIntegrationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripResolutionPlanView resolve(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody AdvancedRoundTripRequest request){
        RoundTripResolutionPlan plan = service.prepareRoundTripResolution(metamodelId, profileId,
                request.targetDirectory(), request.decisions());
        return RoundTripResolutionPlanView.from(plan);
    }

    @PostMapping("/sync/resolve/apply")
    /** Descripción: Obtiene la información correspondiente a «resolveAndApply» dentro de TechnologyIntegrationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripApplyResult resolveAndApply(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody AdvancedRoundTripRequest request){
        return service.applyRoundTripResolution(metamodelId, profileId, request.targetDirectory(), request.decisions());
    }

    @PostMapping("/sync/resolve/rollback/{operationId}")
    /** Descripción: Ejecuta la operación «rollbackResolved» correspondiente a la responsabilidad de TechnologyIntegrationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; operationId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripApplyResult rollbackResolved(@PathVariable String metamodelId, @PathVariable String profileId,
            @PathVariable String operationId, @RequestBody RoundTripRollbackRequest request){
        return service.rollbackRoundTripResolution(request.targetDirectory(), operationId);
    }

    @PostMapping("/preview")
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de TechnologyIntegrationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionPreview preview(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody TechnologyInjectionRequest request){return service.preview(metamodelId, profileId, request.targetDirectory());}

    @PostMapping("/apply")
    /** Descripción: Actualiza el estado gestionado por TechnologyIntegrationController mediante la operación «apply». Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult apply(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestBody TechnologyInjectionRequest request){return service.apply(metamodelId, profileId, request.targetDirectory());}

    @PostMapping("/rollback/{manifestId}")
    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de TechnologyIntegrationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; manifestId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult rollback(@PathVariable String metamodelId, @PathVariable String profileId,
            @PathVariable String manifestId, @RequestBody TechnologyInjectionRequest request){
        // metamodelId y profileId se mantienen en la ruta para conservar el contexto y las políticas de autorización futuras.
        return service.rollback(request.targetDirectory(), manifestId);
    }
}

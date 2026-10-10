package com.example.editor.standalone.distribution;

import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Independent distribution center API for generated editors. */
@RestController
@RequestMapping("/api/standalone-distributions")
public class StandaloneDistributionController {
    private final StandaloneDistributionApplicationService service;
    /** Descripción: Inicializa una instancia de StandaloneDistributionController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneDistributionController(StandaloneDistributionApplicationService service){this.service = service;}
    @PostMapping("/plan") public List<StandaloneDistributionArtifact> plan(@RequestBody StandaloneDistributionRequest request){return service.plan(request);}
    @PostMapping("/materialize") public StandaloneDistributionPreparationResult materialize(@RequestBody StandaloneDistributionRequest request){return service.materialize(request);}
    @PostMapping("/validate") public StandaloneDistributionValidationReport validate(@RequestBody StandaloneDistributionRequest request){return service.validate(request);}
    @PostMapping("/release-readiness")
    /**Descripción: Comprueba el contenido real compilado de la distribución sin confundir fuentes con instaladores. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    public StandaloneDistributionValidationReport releaseReadiness(@RequestBody StandaloneDistributionRequest request) {
        return service.releaseReadiness(request);
    }

    @PostMapping(value = "/package-release-source", produces = "application/zip")
    /**Descripción: Descarga solo paquetes cuyas comprobaciones de preparación para publicación se hayan superado. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    public ResponseEntity<byte[]> packageReleaseSource(@RequestBody StandaloneDistributionRequest request) {
        StandaloneDistributionDownload result = service.packageReleaseReady(request);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .header("X-HYDRA-Distribution-SHA256", result.sha256())
                .header("X-HYDRA-Package-Level", "RELEASE_SOURCE_NOT_NATIVE_INSTALLER")
                .contentLength(result.content().length).body(result.content());
    }

    @PostMapping(value = "/package-deployable-web", produces = "application/zip")
    /** Descripción: Descarga recursos web desplegables independientes del proyecto fuente. Entradas: solicitud del editor. Salida: ZIP y cabeceras con hash y alcance de certificación. */
    public ResponseEntity<byte[]> packageDeployableWeb(@RequestBody StandaloneDistributionRequest request) {
        StandaloneDistributionDownload result = service.packageDeployableWeb(request);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .header("X-HYDRA-Distribution-SHA256", result.sha256())
                .header("X-HYDRA-Package-Level", "DEPLOYABLE_WEB_ASSETS_NOT_NATIVE_INSTALLER")
                .contentLength(result.content().length).body(result.content());
    }

    @PostMapping(value = "/package", produces = "application/zip")
    /** Descripción: Ejecuta la operación «packageEditor» correspondiente a la responsabilidad de StandaloneDistributionController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> packageEditor(@RequestBody StandaloneDistributionRequest request){
        StandaloneDistributionDownload result = service.packageEditor(request);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .header("X-HYDRA-Distribution-SHA256", result.sha256()).contentLength(result.content().length).body(result.content());
    }
}

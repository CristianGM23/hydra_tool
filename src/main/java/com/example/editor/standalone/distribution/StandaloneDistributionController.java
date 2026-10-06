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
    @PostMapping(value = "/package", produces = "application/zip")
    /** Descripción: Ejecuta la operación «packageEditor» correspondiente a la responsabilidad de StandaloneDistributionController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> packageEditor(@RequestBody StandaloneDistributionRequest request){
        StandaloneDistributionDownload result = service.packageEditor(request);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .header("X-HYDRA-Distribution-SHA256", result.sha256()).contentLength(result.content().length).body(result.content());
    }
}

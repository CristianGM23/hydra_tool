package com.example.editor.transformation.testing;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API independiente para casos de prueba y cobertura de transformaciones. */
@RestController
@RequestMapping("/api/transformations/{transformationId}/tests")
public class TransformationTestController {
    private final TransformationTestService service;

    /** Descripción: Inicializa el controlador de pruebas. Entradas: service: servicio de testing. Salida: deja el controlador preparado. */
    /** Descripción: Inicializa una instancia de TransformationTestController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationTestController(TransformationTestService service) { this.service = service; }

    @GetMapping
    /** Descripción: Lista casos persistidos. Entradas: transformationId: transformación. Salida: devuelve resúmenes. */
    /** Descripción: Obtiene la información correspondiente a «list» dentro de TransformationTestController. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationTestCaseSummary> list(@PathVariable String transformationId) { return service.list(transformationId); }

    @PostMapping
    /** Descripción: Crea un caso persistido. Entradas: transformationId: transformación; testCase: caso. Salida: devuelve el caso y su ETag. */
    /** Descripción: Crea o prepara el resultado de «create» dentro de TransformationTestController. Entradas: transformationId: identificador que delimita el recurso objetivo; testCase: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationTestCaseSaveResponse> create(@PathVariable String transformationId, @RequestBody TransformationTestCase testCase) {
        TransformationTestCaseSaveResponse response = service.create(transformationId, testCase);
        return ResponseEntity.created(URI.create("/api/transformations/" + transformationId + "/tests/" + response.testCase().id()))
                .header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @GetMapping("/{testCaseId}")
    /** Descripción: Carga un caso persistido. Entradas: transformationId: transformación; testCaseId: caso. Salida: devuelve caso y ETag. */
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TransformationTestController. Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationTestCaseLoadResult> find(@PathVariable String transformationId, @PathVariable String testCaseId) {
        TransformationTestCaseLoadResult response = service.find(transformationId, testCaseId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @PutMapping("/{testCaseId}")
    /** Descripción: Reemplaza un caso persistido. Entradas: transformationId: transformación; testCaseId: caso; etag: versión; testCase: documento. Salida: devuelve caso y ETag. */
    /** Descripción: Actualiza el estado gestionado por TransformationTestController mediante la operación «replace». Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación; testCase: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationTestCaseSaveResponse> replace(@PathVariable String transformationId, @PathVariable String testCaseId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag, @RequestBody TransformationTestCase testCase) {
        TransformationTestCaseSaveResponse response = service.replace(transformationId, testCaseId, testCase, etag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @DeleteMapping("/{testCaseId}")
    /** Descripción: Elimina un caso persistido. Entradas: transformationId: transformación; testCaseId: caso; etag: versión. Salida: devuelve 204. */
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable String transformationId, @PathVariable String testCaseId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        service.delete(transformationId, testCaseId, etag);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{testCaseId}/run")
    /** Descripción: Ejecuta un caso y devuelve resultado más cobertura. Entradas: transformationId: transformación; testCaseId: caso. Salida: devuelve informe. */
    /** Descripción: Procesa la operación «runOne» coordinando las responsabilidades de TransformationTestController. Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTestRunReport runOne(@PathVariable String transformationId, @PathVariable String testCaseId) { return service.runOne(transformationId, testCaseId); }

    @PostMapping("/run")
    /** Descripción: Ejecuta todos los casos habilitados y agrega cobertura. Entradas: transformationId: transformación. Salida: devuelve informe completo. */
    /** Descripción: Procesa la operación «runAll» coordinando las responsabilidades de TransformationTestController. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTestRunReport runAll(@PathVariable String transformationId) { return service.runAll(transformationId); }
}

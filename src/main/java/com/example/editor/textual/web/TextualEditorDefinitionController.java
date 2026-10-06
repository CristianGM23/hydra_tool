package com.example.editor.textual.web;

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

import com.example.editor.textual.definition.TextualEditorDefinitionModel;
import com.example.editor.textual.definition.TextualEditorDefinitionService;
import com.example.editor.textual.definition.TextualRuntimeNegotiation;
import com.example.editor.textual.definition.TextualSharedLayerOptions;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

@RestController
@RequestMapping("/api/textual-editor-definitions")
public class TextualEditorDefinitionController {
    private final TextualEditorDefinitionService service;

    /**
     * Descripción: Inicializa el controlador de definiciones de editores textuales.
     * Entradas: s: servicio que gestiona persistencia, validación y negociación de runtime.
     * Salida: deja el controlador preparado para atender solicitudes HTTP.
     */
    /** Descripción: Inicializa una instancia de TextualEditorDefinitionController con sus dependencias y datos de entrada. Entradas: s: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TextualEditorDefinitionController(TextualEditorDefinitionService s) {
        service = s;
    }

    @GetMapping
    /**
     * Descripción: Enumera las definiciones textuales persistidas.
     * Entradas: no requiere parámetros de entrada.
     * Salida: devuelve las definiciones textuales disponibles.
     */
    /** Descripción: Obtiene la información correspondiente a «list» dentro de TextualEditorDefinitionController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TextualEditorDefinitionModel> list() {
        return service.list().stream().map(StoredCanonicalDocument::document).toList();
    }

    @PostMapping
    /**
     * Descripción: Crea una definición textual y publica su ETag.
     * Entradas: m: definición textual recibida en la petición.
     * Salida: devuelve HTTP 201 con la definición persistida y su ETag.
     */
    /** Descripción: Crea o prepara el resultado de «create» dentro de TextualEditorDefinitionController. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TextualEditorDefinitionModel> create(@RequestBody TextualEditorDefinitionModel m) {
        var stored = service.create(m);
        return ResponseEntity.created(URI.create("/api/textual-editor-definitions/" + m.id()))
                .header(HttpHeaders.ETAG, stored.etag())
                .body(stored.document());
    }

    @GetMapping("/{id}")
    /**
     * Descripción: Obtiene una definición textual por su identificador estable.
     * Entradas: id: identificador de la definición solicitada.
     * Salida: devuelve la definición persistida con su ETag.
     */
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TextualEditorDefinitionController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TextualEditorDefinitionModel> find(@PathVariable String id) {
        var stored = service.find(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, stored.etag())
                .body(stored.document());
    }

    @PutMapping("/{id}")
    /**
     * Descripción: Sustituye una definición textual aplicando control optimista de concurrencia.
     * Entradas: id: identificador estable; e: If-Match esperado; m: nueva definición textual.
     * Salida: devuelve la definición persistida y el ETag resultante.
     */
    /** Descripción: Actualiza el estado gestionado por TextualEditorDefinitionController mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; e: dato de entrada necesario para la operación; m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TextualEditorDefinitionModel> replace(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String e,
            @RequestBody TextualEditorDefinitionModel m) {
        var stored = service.replace(id, m, e);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, stored.etag())
                .body(stored.document());
    }

    @GetMapping("/{id}/runtime")
    /**
     * Descripción: Negocia el runtime aplicable a una definición textual persistida.
     * Entradas: id: identificador de la definición textual.
     * Salida: devuelve las capacidades y el modo de runtime negociado.
     */
    /** Descripción: Procesa la operación «runtime» coordinando las responsabilidades de TextualEditorDefinitionController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TextualRuntimeNegotiation runtime(@PathVariable String id) {
        return service.negotiate(service.find(id).document());
    }

    @PostMapping("/runtime-check")
    /**
     * Descripción: Comprueba la negociación de runtime sin persistir la definición recibida.
     * Entradas: m: definición textual candidata.
     * Salida: devuelve las capacidades y el modo de runtime que resultarían aplicables.
     */
    /** Descripción: Procesa la operación «runtimeCheck» coordinando las responsabilidades de TextualEditorDefinitionController. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualRuntimeNegotiation runtimeCheck(@RequestBody TextualEditorDefinitionModel m) {
        return service.negotiate(m);
    }


    @PostMapping("/shared-layer-options")
    /** Devuelve únicamente las capas compatibles con el contrato semántico de la gramática compilada. */
    /** Descripción: Ejecuta la operación «sharedLayerOptions» correspondiente a la responsabilidad de TextualEditorDefinitionController. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualSharedLayerOptions sharedLayerOptions(@RequestBody TextualEditorDefinitionModel m) {
        return service.sharedLayerOptions(m);
    }

    @DeleteMapping("/{id}")
    /**
     * Descripción: Elimina una definición textual aplicando control optimista de concurrencia.
     * Entradas: id: identificador estable; e: If-Match esperado.
     * Salida: devuelve HTTP 204 cuando la eliminación concluye correctamente.
     */
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String e) {
        service.delete(id, e);
        return ResponseEntity.noContent().build();
    }
}

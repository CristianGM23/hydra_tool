package com.example.editor.notation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.notation.NotationRegistryException.Reason;

/**
 * Expone las operaciones HTTP del registro de notaciones.
 */
@RestController
@RequestMapping("/api/notations")
public class NotationController {

    private final NotationRegistryService notationRegistryService;

    /**
     * Descripción:
     * Crea el controlador.
     *
     * Entrada:
     * @param notationRegistryService administra las notaciones.
     *
     * Salida:
     * Crea una instancia de NotationController.
     */
    /** Descripción: Inicializa una instancia de NotationController con sus dependencias y datos de entrada. Entradas: notationRegistryService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NotationController(NotationRegistryService notationRegistryService) {
        this.notationRegistryService = notationRegistryService;
    }

    /**
     * Descripción:
     * Devuelve un resumen de las notaciones.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return una lista de resúmenes.
     */
    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «list» dentro de NotationSummary. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<NotationSummary> list() {
        List<NotationSummary> summaries = new ArrayList<>();

        for (NotationDefinition definition : notationRegistryService.list()) {
            summaries.add(new NotationSummary(definition.id(), definition.metamodelId(), definition.name(), definition.nodeStyles().size(), definition.edgeStyles().size()));
        }

        return List.copyOf(summaries);
    }

    /**
     * Descripción:
     * Devuelve una notación completa.
     *
     * Entrada:
     * @param id identifica la notación.
     *
     * Salida:
     * @return la notación solicitada.
     */
    @GetMapping("/{id}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de NotationSummary. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public NotationDefinition find(@PathVariable String id) {
        return notationRegistryService.find(id);
    }

    /**
     * Descripción:
     * Recarga las notaciones del directorio configurado.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return el listado actualizado.
     */
    @PostMapping("/reload")
    /** Descripción: Ejecuta la operación «reload» correspondiente a la responsabilidad de NotationSummary. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<NotationSummary> reload() {
        notationRegistryService.initialize();
        return list();
    }

    /**
     * Descripción:
     * Registra una notación recibida como archivo.
     *
     * Entrada:
     * @param file contiene el archivo JSON.
     *
     * Salida:
     * @return una respuesta 201 con la notación.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «upload» correspondiente a la responsabilidad de NotationSummary. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<NotationDefinition> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NotationRegistryException(Reason.INVALID_REQUEST, "Uploaded notation file must not be empty");
        }

        try {
            NotationDefinition definition = notationRegistryService.register(file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(definition);
        } catch (IOException exception) {
            throw new NotationRegistryException(Reason.STORAGE_ERROR, "Could not read uploaded notation file", exception);
        }
    }

    /**
     * Representa el resumen de una notación.
     *
     * @param id identificador de la notación.
     * @param metamodelId identificador del metamodelo.
     * @param name nombre visible.
     * @param nodeStyleCount número de representaciones de nodo.
     * @param edgeStyleCount número de estilos de conexión.
     */
    public record NotationSummary(String id, String metamodelId, String name, int nodeStyleCount, int edgeStyleCount) {
    }
}
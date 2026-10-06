package com.example.editor.model;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
/**
 * Expone las operaciones HTTP para crear y modificar modelos semánticos.
 */
@RestController
@RequestMapping("/api/models")
public class ModelDocumentController {

    private final ModelDocumentService modelDocumentService;

    /**
     * Descripción:
     * Crea el controlador utilizando el servicio de documentos.
     *
     * Entrada:
     * @param modelDocumentService administra los modelos abiertos.
     *
     * Salida:
     * Crea una instancia de ModelDocumentController.
     */
    /** Descripción: Inicializa una instancia de ModelDocumentController con sus dependencias y datos de entrada. Entradas: modelDocumentService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelDocumentController(ModelDocumentService modelDocumentService) {
        this.modelDocumentService = modelDocumentService;
    }

    /**
     * Descripción:
     * Crea un documento semántico nuevo.
     *
     * Entrada:
     * @param request contiene el metamodelo y la clase raíz.
     *
     * Salida:
     * @return una respuesta 201 con el documento.
     */
    @PostMapping
    /** Descripción: Crea o prepara el resultado de «create» dentro de SetReferenceRequest. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ModelDocumentResponse> create(@RequestBody CreateModelRequest request) {
        ModelDocumentResponse response = modelDocumentService.create(request.metamodelId(), request.rootClassId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Descripción:
     * Consulta un documento abierto.
     *
     * Entrada:
     * @param modelId identifica el documento.
     *
     * Salida:
     * @return el estado actual del documento.
     */
    @GetMapping("/{modelId}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de SetReferenceRequest. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse find(@PathVariable String modelId) {
        return modelDocumentService.find(modelId);
    }

    /**
     * Descripción:
     * Añade un elemento contenido.
     *
     * Entrada:
     * @param modelId identifica el documento.
     * @param request contiene el contenedor, la referencia y la clase.
     *
     * Salida:
     * @return el documento actualizado.
     */
    @PostMapping("/{modelId}/elements")
    /** Descripción: Ejecuta la operación «addElement» correspondiente a la responsabilidad de SetReferenceRequest. Entradas: modelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse addElement(@PathVariable String modelId, @RequestBody AddElementRequest request) {
        return modelDocumentService.addElement(modelId, request.containerId(), request.referenceName(), request.classId());
    }

    /**
     * Descripción:
     * Modifica un atributo semántico.
     *
     * Entrada:
     * @param modelId identifica el documento.
     * @param request contiene el elemento, el atributo y el valor.
     *
     * Salida:
     * @return el documento actualizado.
     */
    @PutMapping("/{modelId}/attributes")
    /** Descripción: Actualiza el estado gestionado por SetReferenceRequest mediante la operación «setAttribute». Entradas: modelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse setAttribute(@PathVariable String modelId, @RequestBody SetAttributeRequest request) {
        return modelDocumentService.setAttribute(modelId, request.elementId(), request.attributeName(), request.value());
    }

    /**
     * Descripción:
     * Crea una referencia semántica.
     *
     * Entrada:
     * @param modelId identifica el documento.
     * @param request contiene el origen, la referencia y el destino.
     *
     * Salida:
     * @return el documento actualizado.
     */
    @PutMapping("/{modelId}/references")
    /** Descripción: Actualiza el estado gestionado por SetReferenceRequest mediante la operación «setReference». Entradas: modelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse setReference(@PathVariable String modelId, @RequestBody SetReferenceRequest request) {
        return modelDocumentService.setReference(modelId, request.sourceId(), request.referenceName(), request.targetId());
    }

    /**
     * Descripción:
     * Guarda el documento en XMI.
     *
     * Entrada:
     * @param modelId identifica el documento.
     *
     * Salida:
     * @return el documento guardado.
     */
    @PostMapping("/{modelId}/save")
    /** Descripción: Actualiza el estado gestionado por SetReferenceRequest mediante la operación «save». Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse save(@PathVariable String modelId) {
        return modelDocumentService.save(modelId);
    }

    /**
     * Contiene los datos para crear un documento.
     *
     * @param metamodelId identificador del metamodelo.
     * @param rootClassId identificador de la clase raíz.
     */
    public record CreateModelRequest(String metamodelId, String rootClassId) {
    }

    /**
     * Contiene los datos para crear un elemento.
     *
     * @param containerId identificador del contenedor.
     * @param referenceName nombre de la referencia containment.
     * @param classId identificador de la clase.
     */
    public record AddElementRequest(String containerId, String referenceName, String classId) {
    }

    /**
     * Contiene los datos para modificar un atributo.
     *
     * @param elementId identificador del EObject.
     * @param attributeName nombre del atributo.
     * @param value valor serializado.
     */
    public record SetAttributeRequest(String elementId, String attributeName, String value) {
    }

    /**
     * Contiene los datos para crear una referencia.
     *
     * @param sourceId identificador del origen.
     * @param referenceName nombre de la referencia.
     * @param targetId identificador del destino.
     */
    public record SetReferenceRequest(String sourceId, String referenceName, String targetId) {
    }

    /**
     * Descripción:
     * Devuelve los archivos XMI guardados.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return una lista de archivos.
     */
    @GetMapping("/files")
    /** Descripción: Obtiene la información correspondiente a «listSavedModels» dentro de SetReferenceRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<SavedModelSummary> listSavedModels() {
        return modelDocumentService.listSavedModels();
    }

    /**
     * Descripción:
     * Abre un archivo XMI existente.
     *
     * Entrada:
     * @param request contiene el metamodelo y el archivo.
     *
     * Salida:
     * @return una sesión con el modelo cargado.
     */
    @PostMapping("/open")
    /** Descripción: Crea o prepara el resultado de «open» dentro de SetReferenceRequest. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse open(@RequestBody OpenModelRequest request) {
        return modelDocumentService.open(request.metamodelId(), request.fileName());
    }

    /**
     * Descripción:
     * Elimina un elemento semántico.
     *
     * Entrada:
     * @param modelId identifica el documento.
     * @param elementId identifica el elemento.
     *
     * Salida:
     * @return el documento actualizado.
     */
    @PostMapping("/{modelId}/elements/delete")
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElements» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse deleteElements(@PathVariable String modelId, @RequestBody ModelElementSelectionRequest request) {
        return modelDocumentService.deleteElements(modelId, request.elementIds());
    }

    @DeleteMapping("/{modelId}/elements/{elementId}")
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElement» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse deleteElement(@PathVariable String modelId, @PathVariable String elementId) {
        return modelDocumentService.deleteElement(modelId, elementId);
    }

    /**
     * Descripción:
     * Descarga un archivo XMI guardado.
     *
     * Entrada:
     * @param fileName contiene el nombre del archivo.
     *
     * Salida:
     * @return el contenido binario del archivo.
     */
    @GetMapping(value = "/files/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    /** Descripción: Ejecuta la operación «download» correspondiente a la responsabilidad de SetReferenceRequest. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> download(@RequestParam String fileName) {
        byte[] content = modelDocumentService.readSavedModel(fileName);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"").body(content);
    }

    /** Elimina un XMI guardado e invalida las sesiones que todavía lo tengan abierto. */
    @DeleteMapping("/files/{fileName:.+}")
    /** Descripción: Elimina o libera el recurso gestionado por «deleteSavedModel» de forma controlada. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> deleteSavedModel(@PathVariable String fileName) {
        modelDocumentService.deleteSavedModel(fileName);
        return ResponseEntity.noContent().build();
    }

    /**
     * Contiene los datos necesarios para abrir un modelo.
     *
     * @param metamodelId identifica el metamodelo.
     * @param fileName contiene el nombre del archivo XMI.
     */
    public record OpenModelRequest(String metamodelId, String fileName) {
    }
}
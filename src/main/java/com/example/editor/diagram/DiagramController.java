package com.example.editor.diagram;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.diagram.DiagramDocument.DiagramEdgeView;
import com.example.editor.diagram.DiagramDocument.DiagramNodeView;

/**
 * Expone las operaciones HTTP para guardar y cargar diagramas.
 */
@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramStorageService diagramStorageService;

    /**
     * Descripción:
     * Crea el controlador.
     *
     * Entrada:
     * @param diagramStorageService administra los documentos visuales.
     *
     * Salida:
     * Crea una instancia de DiagramController.
     */
    /** Descripción: Inicializa una instancia de DiagramController con sus dependencias y datos de entrada. Entradas: diagramStorageService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DiagramController(DiagramStorageService diagramStorageService) {
        this.diagramStorageService = diagramStorageService;
    }

    /**
     * Descripción:
     * Devuelve el diagrama asociado a un modelo.
     *
     * Entrada:
     * @param modelFileName contiene el nombre del XMI.
     *
     * Salida:
     * @return el documento visual.
     */
    @GetMapping("/{modelFileName:.+}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de SaveDiagramRequest. Entradas: modelFileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public DiagramDocument find(@PathVariable String modelFileName) {
        return diagramStorageService.find(modelFileName);
    }

    /**
     * Descripción:
     * Guarda el diagrama asociado a un modelo.
     *
     * Entrada:
     * @param modelFileName contiene el nombre del XMI.
     * @param request contiene la información visual.
     *
     * Salida:
     * @return el documento guardado.
     */
    @PutMapping("/{modelFileName:.+}")
    /** Descripción: Actualiza el estado gestionado por SaveDiagramRequest mediante la operación «save». Entradas: modelFileName: archivo o contenido que debe procesarse; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public DiagramDocument save(@PathVariable String modelFileName, @RequestBody SaveDiagramRequest request) {
        DiagramDocument document = new DiagramDocument(request.formatVersion(), modelFileName, request.notationId(), request.nodes(), request.edges());
        return diagramStorageService.save(document);
    }

    /**
     * Descripción:
     * Elimina el documento visual.
     *
     * Entrada:
     * @param modelFileName contiene el nombre del XMI.
     *
     * Salida:
     * @return una respuesta sin contenido.
     */
    @DeleteMapping("/{modelFileName:.+}")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: modelFileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable String modelFileName) {
        diagramStorageService.delete(modelFileName);
        return ResponseEntity.noContent().build();
    }

    /**
     * Contiene los datos recibidos para guardar un diagrama.
     *
     * @param formatVersion versión del formato.
     * @param notationId identificador de la notación.
     * @param nodes estados visuales de los nodos.
     * @param edges estados visuales de las conexiones.
     */
    public record SaveDiagramRequest(int formatVersion, String notationId, List<DiagramNodeView> nodes, List<DiagramEdgeView> edges) {
    }
}
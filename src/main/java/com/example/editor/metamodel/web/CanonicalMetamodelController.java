package com.example.editor.metamodel.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.application.MetamodelSaveResponse;
import com.example.editor.metamodel.canonical.CanonicalMetamodelCatalog;
import com.example.editor.metamodel.canonical.CanonicalMetamodelSummary;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.validation.ValidationReport;

/**
 * Expone la API semántica canónica del editor.
 *
 * Todas las operaciones reciben o devuelven MetaModel; las proyecciones y el
 * documento visual se administran mediante recursos separados.
 */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalMetamodelController {

    private final CanonicalMetamodelApplicationService service;
    private final MetamodelRegistryService runtimeRegistry;

    /** Descripción: Inicializa una instancia de CanonicalMetamodelController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; runtimeRegistry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalMetamodelController(CanonicalMetamodelApplicationService service, MetamodelRegistryService runtimeRegistry) {
        this.service = service;
        this.runtimeRegistry = runtimeRegistry;
    }

    @PostMapping
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalMetamodelController. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<MetamodelSaveResponse> create(@RequestBody MetaModel metamodel) {
        MetamodelSaveResponse response = service.create(metamodel);
        runtimeRegistry.initialize();
        return ResponseEntity
            .created(URI.create("/api/canonical-metamodels/" + response.metamodel().id()))
            .header(HttpHeaders.ETAG, response.etag())
            .body(response);
    }

    /** Number of stored metamodels left out of the listing because their document cannot be used (D-MM-CORRUPT-CONTRATO). */
    public static final String OMITTED_COUNT_HEADER = "X-Hydra-Omitted-Count";
    /** Comma-separated ids of the omitted metamodels (at most {@link #OMITTED_ITEMS_LIMIT}; a file whose name is not an id is only counted). */
    public static final String OMITTED_ITEMS_HEADER = "X-Hydra-Omitted-Items";
    static final int OMITTED_ITEMS_LIMIT = 50;

    @GetMapping
    /** Descripción: Lista los metamodelos que se pueden usar con el mismo array JSON de siempre; si alguno se omite porque su documento está corrupto o no se puede leer, las cabeceras X-Hydra-Omitted-Count y X-Hydra-Omitted-Items lo identifican sin rutas (D-MM-CORRUPT-CONTRATO). Entradas: ninguna. Salida: resúmenes ordenados por id. */ public ResponseEntity<List<CanonicalMetamodelSummary>> list() {
        CanonicalMetamodelCatalog catalog = service.catalog();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (!catalog.omitted().isEmpty()) {
            response.header(OMITTED_COUNT_HEADER, Integer.toString(catalog.omitted().size()));
            String ids = catalog.omitted().stream().map(CanonicalMetamodelCatalog.Omitted::metamodelId).filter(java.util.Objects::nonNull)
                    .limit(OMITTED_ITEMS_LIMIT).collect(java.util.stream.Collectors.joining(","));
            if (!ids.isEmpty()) response.header(OMITTED_ITEMS_HEADER, ids);
        }
        return response.body(catalog.metamodels());
    }

    @GetMapping("/{id}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de CanonicalMetamodelController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<MetaModel> find(@PathVariable("id") String id) {
        MetamodelLoadResult result = service.findById(id);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.metamodel());
    }

    @PutMapping("/{id}")
    /** Descripción: Actualiza el estado gestionado por CanonicalMetamodelController mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<MetamodelSaveResponse> replace(@PathVariable("id") String id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag, @RequestBody MetaModel metamodel) {
        MetamodelSaveResponse response = service.replace(id, metamodel, expectedEtag);
        runtimeRegistry.initialize();
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @PostMapping("/{id}/validate")
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationReport validate(@PathVariable("id") String id) {
        return service.validateStored(id);
    }

    @DeleteMapping("/{id}")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable("id") String id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) {
        service.delete(id, expectedEtag);
        runtimeRegistry.initialize();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

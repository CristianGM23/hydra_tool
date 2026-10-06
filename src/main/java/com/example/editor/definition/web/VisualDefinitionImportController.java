package com.example.editor.definition.web;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.editor.definition.importing.VisualDefinitionImportResult;
import com.example.editor.definition.importing.VisualDefinitionImportService;
import com.example.editor.definition.importing.VisualImportCommitRequest;
import com.example.editor.definition.importing.VisualImportCommitResult;
import com.example.editor.definition.importing.VisualValidationAssociationService;

/** Safe preview import of Sirius Desktop, Sirius Web, GMF and draw.io visual definitions. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/editors/imports")
public class VisualDefinitionImportController {
    private final VisualDefinitionImportService service;
    private final VisualValidationAssociationService associationService;
    /** Descripción: Inicializa una instancia de VisualDefinitionImportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; associationService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public VisualDefinitionImportController(VisualDefinitionImportService service, VisualValidationAssociationService associationService){this.service=service;this.associationService=associationService;}

    @PostMapping(value="/sirius",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «sirius» correspondiente a la responsabilidad de VisualDefinitionImportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; odesign: dato de entrada necesario para la operación; javaSources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult sirius(@PathVariable String metamodelId,@RequestPart("odesign") MultipartFile odesign,
            @RequestPart(value="javaSources",required=false) List<MultipartFile> javaSources,
            @RequestParam(defaultValue="EXISTING") String associationMode,
            @RequestPart(value="metamodelResources",required=false) List<MultipartFile> metamodelResources){
        return service.importSirius(metamodelId,odesign,javaSources,associationMode,metamodelResources);
    }

    @PostMapping(value="/sirius-web",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «siriusWeb» correspondiente a la responsabilidad de VisualDefinitionImportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult siriusWeb(@PathVariable String metamodelId,@RequestPart("resources") List<MultipartFile> resources,
            @RequestParam(defaultValue="EXISTING") String associationMode,
            @RequestPart(value="metamodelResources",required=false) List<MultipartFile> metamodelResources){
        return service.importSiriusWeb(metamodelId,resources,associationMode,metamodelResources);
    }

    @PostMapping(value="/drawio",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «drawio» correspondiente a la responsabilidad de VisualDefinitionImportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult drawio(@PathVariable String metamodelId,@RequestPart("resources") List<MultipartFile> resources,
            @RequestParam(defaultValue="EXISTING") String associationMode,
            @RequestPart(value="metamodelResources",required=false) List<MultipartFile> metamodelResources){
        return service.importDrawio(metamodelId,resources,associationMode,metamodelResources);
    }

    @PostMapping(value="/gmf",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «gmf» correspondiente a la responsabilidad de VisualDefinitionImportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; resources: dato de entrada necesario para la operación; associationMode: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualDefinitionImportResult gmf(@PathVariable String metamodelId,@RequestPart("resources") List<MultipartFile> resources,
            @RequestParam(defaultValue="EXISTING") String associationMode,
            @RequestPart(value="metamodelResources",required=false) List<MultipartFile> metamodelResources){
        return service.importGmf(metamodelId,resources,associationMode,metamodelResources);
    }
    @PostMapping(value="/commit-associations",consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «commitAssociations» correspondiente a la responsabilidad de VisualDefinitionImportController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualImportCommitResult commitAssociations(@RequestBody VisualImportCommitRequest request){
        return associationService.commit(request);
    }

}

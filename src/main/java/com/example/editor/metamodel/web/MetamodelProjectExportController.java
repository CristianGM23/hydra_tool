package com.example.editor.metamodel.web;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.metamodel.export.MetamodelExportArtifact;
import com.example.editor.metamodel.export.project.MetamodelCompleteProjectExportService;
import com.example.editor.metamodel.export.project.MetamodelProjectArtifactCatalog;
import com.example.editor.metamodel.export.project.MetamodelProjectExportDescriptor;
import com.example.editor.metamodel.export.project.MetamodelProjectExportRequest;

/** Expone workspaces tecnológicos completos protegidos por validación del metamodelo. */
@RestController
@RequestMapping("/api/metamodels/{metamodelId}")
public class MetamodelProjectExportController {
    private final MetamodelCompleteProjectExportService service;
    /** Descripción: Inicializa una instancia de MetamodelProjectExportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelProjectExportController(MetamodelCompleteProjectExportService service){this.service = service;}
    @GetMapping("/project-export-formats") public List<MetamodelProjectExportDescriptor> list(@PathVariable String metamodelId){return service.listFormats(metamodelId);}
    @GetMapping("/project-export-artifacts") public MetamodelProjectArtifactCatalog artifacts(@PathVariable String metamodelId){return service.listArtifacts(metamodelId);}
    @PostMapping("/project-exports/{kind}") public ResponseEntity<byte[]> export(@PathVariable String metamodelId, @PathVariable String kind,
            @RequestBody(required = false) MetamodelProjectExportRequest request){
        MetamodelExportArtifact artifact = service.export(metamodelId, kind, request == null ? null : request.options());
        HttpHeaders headers = new HttpHeaders(); headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"");
        headers.setContentType(MediaType.parseMediaType(artifact.mediaType())); headers.setContentLength(artifact.content().length);
        return ResponseEntity.ok().headers(headers).body(artifact.content());
    }
}

package com.example.editor.project.recovery;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.project.ProjectException;

/** API de análisis no destructivo para proyectos externos heterogéneos. */
@RestController
@RequestMapping("/api/external-project-recovery")
public class ExternalProjectRecoveryController {
    private final ExternalProjectRecoveryService service;
    private final ExternalProjectImportService importer;

    /** Descripción: Inicializa una instancia de ExternalProjectRecoveryController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; importer: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalProjectRecoveryController(ExternalProjectRecoveryService service, ExternalProjectImportService importer) {
        this.service = service;
        this.importer = importer;
    }

    /** Analiza un ZIP externo completo sin crear todavía un proyecto HYDRA. */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de ExternalProjectRecoveryController. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ExternalProjectRecoveryReport analyze(@RequestPart("file") MultipartFile file) {
        if(file == null || file.isEmpty()) {
            throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,
                    "Select an external project archive before starting analysis");
        }
        try(var input = file.getInputStream()) {
            return service.analyze(file.getOriginalFilename(), input, file.getSize());
        } catch(IOException exception) {
            throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,
                    "Could not read the selected external project archive", exception);
        }
    }
    /** Analiza y materializa un proyecto externo como proyecto HYDRA revisable. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importProject» correspondiente a la responsabilidad de ExternalProjectRecoveryController. Entradas: file: archivo o contenido que debe procesarse; parentDirectory: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExternalProjectImportResult importProject(@RequestPart("file") MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam String parentDirectory,
            @org.springframework.web.bind.annotation.RequestParam String projectName) {
        if(file == null || file.isEmpty()) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,
                "Select an external project archive before importing it");
        try(var input = file.getInputStream()) {
            return importer.importProject(file.getOriginalFilename(), input, file.getSize(), parentDirectory, projectName);
        } catch(IOException exception) {
            throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,
                    "Could not read the selected external project archive", exception);
        }
    }

}

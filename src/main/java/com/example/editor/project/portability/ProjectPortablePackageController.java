package com.example.editor.project.portability;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectSession;
/** API de portabilidad autocontenida de proyectos HYDRA. */
@RestController
@RequestMapping("/api/projects/portable")
public class ProjectPortablePackageController {
    private final ProjectPortablePackageService service;
    /** Descripción: Inicializa el controlador de portabilidad. Entradas: service: servicio. Salida: instancia lista. */ public ProjectPortablePackageController(ProjectPortablePackageService service){this.service=service;}
    /** Descripción: Exporta el proyecto activo como paquete portable verificable. Entradas: ninguna. Salida: ZIP descargable. */ @PostMapping(value="/export",produces="application/zip") public ResponseEntity<byte[]> exportPortable(){return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition","attachment; filename=hydra-project-portable.zip").body(service.exportPortable());}
    /** Descripción: Importa un paquete portable tras verificar manifiesto y hash. Entradas: parentDirectory, projectName, file. Salida: sesión importada. */ @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ProjectSession importPortable(@RequestParam String parentDirectory,@RequestParam String projectName,@RequestPart("file") MultipartFile file){try{return service.importPortable(parentDirectory,projectName,file.getBytes());}catch(java.io.IOException e){throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Could not read portable package",e);}}
}

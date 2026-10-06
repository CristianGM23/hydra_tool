package com.example.editor.portability.archive;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.portability.HydraCapabilityPackage;

/** API del formato físico reproducible .hydra-cap. */
@RestController
@RequestMapping("/api/capability-packages/archives")
public class CapabilityPackageArchiveController {
    public static final MediaType HYDRA_PACKAGE = MediaType.parseMediaType("application/vnd.hydra.capability-package+zip");
    private final CapabilityPackageArchiveService service;
    /** Descripción: Inicializa una instancia de CapabilityPackageArchiveController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPackageArchiveController(CapabilityPackageArchiveService service) { this.service = service; }

    @PostMapping(produces = "application/vnd.hydra.capability-package+zip")
    /** Descripción: Ejecuta la operación «exportPackage» correspondiente a la responsabilidad de CapabilityPackageArchiveController. Entradas: value: valor de entrada que debe procesarse; cache: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> exportPackage(@RequestBody HydraCapabilityPackage value,
            @RequestParam(name = "cache", defaultValue = "true") boolean cache) {
        CapabilityPackageArchiveResult result = service.exportPackage(value, cache);
        return ResponseEntity.ok().contentType(HYDRA_PACKAGE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .header("X-HYDRA-Content-Hash", result.descriptor().contentHash())
                .header("X-HYDRA-Archive-SHA256", result.archiveSha256())
                .header("X-HYDRA-Cache-Hit", Boolean.toString(result.cacheHit()))
                .body(result.archive());
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importPackage» correspondiente a la responsabilidad de CapabilityPackageArchiveController. Entradas: file: archivo o contenido que debe procesarse; loadResources: dato de entrada necesario para la operación; cache: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPackageImportResult importPackage(@RequestPart("file") MultipartFile file,
            @RequestParam(name = "loadResources", defaultValue = "true") boolean loadResources,
            @RequestParam(name = "cache", defaultValue = "true") boolean cache) {
        try { return service.importPackage(file.getBytes(), loadResources, cache); }
        catch(java.io.IOException exception) { throw new CapabilityPackageArchiveException(CapabilityPackageArchiveException.Reason.IO_ERROR,
                "Could not read the uploaded capability package", exception); }
    }

    @GetMapping(produces = "application/vnd.hydra.capability-package+zip")
    /** Descripción: Ejecuta la operación «cached» correspondiente a la responsabilidad de CapabilityPackageArchiveController. Entradas: contentHash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> cached(@RequestParam("contentHash") String contentHash) {
        byte[] archive = service.loadCached(contentHash);
        return ResponseEntity.ok().contentType(HYDRA_PACKAGE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(contentHash + ".hydra-cap").build().toString())
                .body(archive);
    }
}

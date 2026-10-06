package com.example.editor.recovery;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for AZ backup verification and isolated recovery drills. */
@RestController
@RequestMapping("/api/recovery")
public class HydraRecoveryController {
    private final HydraRecoveryService service;
    /** Descripción: Inicializa una instancia de HydraRecoveryController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraRecoveryController(HydraRecoveryService service){this.service=service;}
    @PostMapping("/backups") public ResponseEntity<HydraRecoveryBackupManifest> backup(){return ResponseEntity.ok(service.backup());}
    @GetMapping("/backups/{backupId}/verify") public ResponseEntity<HydraRecoveryBackupManifest> verify(@PathVariable String backupId){return ResponseEntity.ok(service.verify(backupId));}
    @PostMapping("/drills") public ResponseEntity<HydraRecoveryDrillReport> drill(@RequestBody HydraRecoveryDrillRequest request){return ResponseEntity.ok(service.drill(request));}
}

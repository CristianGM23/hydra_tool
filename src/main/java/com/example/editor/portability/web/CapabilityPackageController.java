package com.example.editor.portability.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.portability.CapabilityCompatibilityMatrixService;
import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.TargetCapabilityCompatibility;

@RestController
@RequestMapping("/api/capability-packages")
public class CapabilityPackageController {

    private final CapabilityPackageChecker checker;
    private final CapabilityCompatibilityMatrixService matrixService;

    /** Descripción: Inicializa una instancia de CapabilityPackageController con sus dependencias y datos de entrada. Entradas: checker: dato de entrada necesario para la operación; matrixService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPackageController(CapabilityPackageChecker checker, CapabilityCompatibilityMatrixService matrixService) {
        this.checker = checker;
        this.matrixService = matrixService;
    }

    @PostMapping("/check")
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPackageCheckReport check(@RequestBody HydraCapabilityPackage value) {
        return checker.check(value);
    }

    @GetMapping("/compatibility")
    /** Descripción: Ejecuta la operación «compatibility» correspondiente a la responsabilidad de CapabilityPackageController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TargetCapabilityCompatibility> compatibility() {
        return matrixService.matrix();
    }
}

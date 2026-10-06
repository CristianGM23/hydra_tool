package com.example.editor.release;

import org.springframework.stereotype.Service;

import com.example.editor.system.HydraPlatformReadinessReport;
import com.example.editor.system.HydraPlatformReadinessService;

/**
 * Cierra el preflight de publicación combinando evidencia inmutable de release
 * con las condiciones operativas de la instalación actual.
 */
@Service
public class HydraReleaseCandidateService {
    private final HydraReleaseGateService gates;
    private final HydraPlatformReadinessService readiness;

    /** Descripción: Inicializa una instancia de HydraReleaseCandidateService con sus dependencias y datos de entrada. Entradas: gates: dato de entrada necesario para la operación; readiness: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraReleaseCandidateService(HydraReleaseGateService gates, HydraPlatformReadinessService readiness) {
        this.gates = gates;
        this.readiness = readiness;
    }

    /** Devuelve el preflight explicable de un release gate existente. */
    /** Descripción: Ejecuta la operación «assess» correspondiente a la responsabilidad de HydraReleaseCandidateService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraReleaseCandidateReport assess(String reportId) {
        HydraReleaseGateReport gate = gates.find(reportId);
        HydraPlatformReadinessReport platform = readiness.evaluate();
        boolean publishable = gate.passed() && platform.ready();
        return new HydraReleaseCandidateReport(gate.reportId(), gate.passed(), platform.ready(), publishable,
                platform.checks());
    }

    /** Exige simultáneamente gate superado y plataforma READY. */
    /** Descripción: Ejecuta la operación «assertPublishable» correspondiente a la responsabilidad de HydraReleaseCandidateService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraReleaseGateReport assertPublishable(String reportId) {
        HydraReleaseCandidateReport candidate = assess(reportId);
        if(!candidate.releaseGatePassed()){
            throw new IllegalStateException("Release gate contains blocking checks");
        }
        if(!candidate.platformReady()){
            throw new IllegalStateException("HYDRA platform readiness contains blocking checks");
        }
        return gates.find(reportId);
    }
}

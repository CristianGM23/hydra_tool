package com.example.editor.release;

import org.springframework.stereotype.Service;
import com.example.editor.standalone.publication.DistributionPublicationService;
import com.example.editor.standalone.publication.PublicationResult;

/** Publishes only when product and version match a passed immutable gate report. */
@Service
public class VerifiedDistributionPublicationService {
    private final HydraReleaseGateService gates;
    private final DistributionPublicationService publications;
    /** Descripción: Inicializa una instancia de VerifiedDistributionPublicationService con sus dependencias y datos de entrada. Entradas: gates: dato de entrada necesario para la operación; publications: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public VerifiedDistributionPublicationService(HydraReleaseGateService gates, DistributionPublicationService publications){this.gates = gates; this.publications = publications;}
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de VerifiedDistributionPublicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PublicationResult publish(VerifiedPublicationRequest request){
        if(request == null || request.publication() == null || request.gateReportId().isBlank()) throw new IllegalArgumentException("gateReportId and publication are required");
        HydraReleaseGateReport gate = gates.assertPassed(request.gateReportId());
        if(!gate.productId().equals(request.publication().productId())) throw new IllegalArgumentException("Publication productId differs from the release gate");
        if(!gate.version().equals(request.publication().version())) throw new IllegalArgumentException("Publication version differs from the release gate");
        return publications.publish(request.publication());
    }
}

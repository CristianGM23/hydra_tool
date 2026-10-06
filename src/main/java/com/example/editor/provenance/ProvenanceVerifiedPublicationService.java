package com.example.editor.provenance;

import org.springframework.stereotype.Service;
import com.example.editor.standalone.publication.DistributionPublicationService;
import com.example.editor.standalone.publication.PublicationResult;

/** Publishes only when AV attestation is valid for the same product and version. */
@Service
public class ProvenanceVerifiedPublicationService {
    private final HydraDistributionProvenanceService provenance;
    private final DistributionPublicationService publications;
    /** Descripción: Inicializa una instancia de ProvenanceVerifiedPublicationService con sus dependencias y datos de entrada. Entradas: provenance: dato de entrada necesario para la operación; publications: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProvenanceVerifiedPublicationService(HydraDistributionProvenanceService provenance,
            DistributionPublicationService publications){this.provenance = provenance; this.publications = publications;}
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de ProvenanceVerifiedPublicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PublicationResult publish(ProvenanceVerifiedPublicationRequest request){
        if(request == null || request.publication() == null || request.provenanceReportId().isBlank()) throw new IllegalArgumentException("provenanceReportId and publication are required");
        provenance.assertValid(request.provenanceReportId(), request.publication().productId(), request.publication().version());
        return publications.publish(request.publication());
    }
}

package com.example.editor.release;

import org.springframework.stereotype.Service;

import com.example.editor.provenance.HydraDistributionProvenanceService;
import com.example.editor.standalone.publication.DistributionPublicationService;
import com.example.editor.standalone.publication.PublicationResult;

/**
 * Gate final: exige attestation de release, procedencia/licencias válida y
 * plataforma READY antes de delegar en la publicación existente.
 */
@Service
public class HydraFinalVerifiedPublicationService {
    private final HydraReleaseCandidateService candidates;
    private final HydraDistributionProvenanceService provenance;
    private final DistributionPublicationService publications;

    /** Descripción: Inicializa una instancia de HydraFinalVerifiedPublicationService con sus dependencias y datos de entrada. Entradas: candidates: dato de entrada necesario para la operación; provenance: dato de entrada necesario para la operación; publications: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraFinalVerifiedPublicationService(HydraReleaseCandidateService candidates, HydraDistributionProvenanceService provenance, DistributionPublicationService publications) {
        this.candidates = candidates;
        this.provenance = provenance;
        this.publications = publications;
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de HydraFinalVerifiedPublicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PublicationResult publish(HydraFinalVerifiedPublicationRequest request) {
        if(request == null || request.publication() == null || request.releaseGateReportId().isBlank() || request.provenanceReportId().isBlank()){
            throw new IllegalArgumentException("releaseGateReportId, provenanceReportId and publication are required");
        }
        HydraReleaseGateReport gate = candidates.assertPublishable(request.releaseGateReportId());
        if(!gate.productId().equals(request.publication().productId())){
            throw new IllegalArgumentException("Publication productId differs from the release gate");
        }
        if(!gate.version().equals(request.publication().version())){
            throw new IllegalArgumentException("Publication version differs from the release gate");
        }
        provenance.assertValid(request.provenanceReportId(), request.publication().productId(), request.publication().version());
        return publications.publish(request.publication());
    }
}

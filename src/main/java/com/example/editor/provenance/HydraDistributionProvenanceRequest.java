package com.example.editor.provenance;

import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;
import com.example.editor.portability.HydraCapabilityPackage;

/** Evidence links used to build a verifiable distribution SBOM. */
public record HydraDistributionProvenanceRequest(String productId, String version,
        UpdateSiteMirrorChannel channel, HydraLicensePolicy licensePolicy,
        HydraCapabilityPackage capabilityPackage, String orchestrationId,
        String mirrorUpdateReportId, List<String> adapterVerificationReportIds,
        String performanceAutomationReportId, String releaseGateReportId,
        Map<String, String> annotations) {
    public HydraDistributionProvenanceRequest {
        productId = text(productId); version = text(version);
        channel = channel == null ? UpdateSiteMirrorChannel.INTERNAL : channel;
        licensePolicy = licensePolicy == null ? HydraLicensePolicy.COMPATIBLE_ONLY : licensePolicy;
        orchestrationId = text(orchestrationId); mirrorUpdateReportId = text(mirrorUpdateReportId);
        adapterVerificationReportIds = adapterVerificationReportIds == null ? List.of() : adapterVerificationReportIds.stream().map(HydraDistributionProvenanceRequest::text).filter(value -> !value.isBlank()).distinct().toList();
        performanceAutomationReportId = text(performanceAutomationReportId); releaseGateReportId = text(releaseGateReportId);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraDistributionProvenanceRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}

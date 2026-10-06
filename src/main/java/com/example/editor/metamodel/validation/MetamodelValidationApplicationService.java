package com.example.editor.metamodel.validation;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/** Orquesta análisis exhaustivo, receipt persistente y gate de generación derivada. */
@Service
public class MetamodelValidationApplicationService {
    private final CanonicalMetamodelApplicationService metamodels;
    private final NeutralMetamodelValidator validator;
    private final MetamodelValidationAdvisor advisor;
    private final MetamodelValidationFingerprint fingerprint;
    private final MetamodelValidationReceiptStore receipts;
    private final MetamodelValidationProperties properties;

    /** Descripción: Inicializa una instancia de MetamodelValidationApplicationService con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación; advisor: dato de entrada necesario para la operación; fingerprint: dato de entrada necesario para la operación; receipts: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelValidationApplicationService(CanonicalMetamodelApplicationService metamodels, NeutralMetamodelValidator validator,
            MetamodelValidationAdvisor advisor, MetamodelValidationFingerprint fingerprint,
            MetamodelValidationReceiptStore receipts, MetamodelValidationProperties properties){
        this.metamodels = metamodels; this.validator = validator; this.advisor = advisor; this.fingerprint = fingerprint;
        this.receipts = receipts; this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «analyzeDraft» correspondiente a la responsabilidad de MetamodelValidationApplicationService. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelValidationAnalysis analyzeDraft(MetaModel metamodel){return analyze(metamodel, false);}

    /** Descripción: Valida las condiciones necesarias para «validateStored» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelValidationAnalysis validateStored(String metamodelId){
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        MetamodelValidationAnalysis analysis = analyze(metamodel, true);
        receipts.save(new MetamodelValidationReceipt("1.0", metamodel.id(), analysis.fingerprint(), analysis.validatedAt(),
                analysis.valid(), analysis.generationReady(), analysis.summary()));
        return analysis;
    }

    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de MetamodelValidationApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelValidationGateStatus status(String metamodelId){
        MetaModel current = metamodels.findById(metamodelId).metamodel(); String currentFingerprint = fingerprint.of(current);
        return receipts.find(metamodelId).map(receipt -> {
            boolean same = currentFingerprint.equals(receipt.fingerprint()); boolean ready = same && receipt.generationReady();
            return new MetamodelValidationGateStatus(metamodelId, properties.isRequireCurrentValidationForProjectExports(), true,
                    same, !same, receipt.valid(), ready, currentFingerprint, receipt.fingerprint(), receipt.validatedAt(), receipt.summary(),
                    same ? (ready ? "Metamodel validation is current and derived project generation is enabled." : "Current validation contains blocking problems.")
                            : "The metamodel changed after its last validation. Validate it again before generating derived projects.");
        }).orElseGet(() -> new MetamodelValidationGateStatus(metamodelId, properties.isRequireCurrentValidationForProjectExports(), false,
                false, false, false, false, currentFingerprint, "", Instant.EPOCH, new MetamodelValidationSummary(0, 0, 0),
                "This metamodel has not been validated for derived project generation yet."));
    }

    /** Descripción: Valida las condiciones necesarias para «requireGenerationReady» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void requireGenerationReady(String metamodelId){
        if(!properties.isRequireCurrentValidationForProjectExports()){return;}
        MetamodelValidationGateStatus status = status(metamodelId);
        if(!status.generationReady()){
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Complete project generation requires a successful current metamodel validation. " + status.message());
        }
    }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de MetamodelValidationApplicationService. Entradas: metamodel: dato de entrada necesario para la operación; persisted: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelValidationAnalysis analyze(MetaModel metamodel, boolean persisted){
        ValidationReport base = validator.validate(metamodel);
        List<MetamodelValidationDiagnostic> diagnostics = base.issues().stream().limit(properties.getMaximumDiagnostics()).map(advisor::explain).toList();
        int errors = (int) diagnostics.stream().filter(item -> item.severity() == ValidationSeverity.ERROR).count();
        int warnings = (int) diagnostics.stream().filter(item -> item.severity() == ValidationSeverity.WARNING).count();
        boolean valid = errors == 0; boolean generationReady = valid && (!properties.isWarningsBlockProjectExports() || warnings == 0) && persisted;
        return new MetamodelValidationAnalysis(metamodel == null ? "" : metamodel.id(), metamodel == null ? "" : metamodel.name(),
                metamodel == null ? "" : fingerprint.of(metamodel), Instant.now(), valid, generationReady,
                new MetamodelValidationSummary(errors, warnings, diagnostics.size()), diagnostics);
    }
}

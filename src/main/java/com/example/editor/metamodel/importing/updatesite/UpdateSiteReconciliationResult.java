package com.example.editor.metamodel.importing.updatesite;

import com.example.editor.metamodel.importing.MetamodelImportPreview;

/** Preview neutral obtenido tras aplicar las decisiones de reconciliación. */
public record UpdateSiteReconciliationResult(UpdateSiteReconciliationReport report,
        MetamodelImportPreview preview, boolean applicable) {
    public UpdateSiteReconciliationResult {
        report = report == null ? UpdateSiteReconciliationReport.empty() : report;
    }
}

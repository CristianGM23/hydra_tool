package com.example.editor.metamodel.importing.updatesite;

import com.example.editor.metamodel.importing.MetamodelImportSaveResult;

/** Resultado persistido del flujo de reconstrucción p2. */
public record UpdateSiteImportResult(String analysisId, MetamodelImportSaveResult metamodel,
        String recoveryPackagePath, UpdateSiteRecoveredLayers recoveredLayers) {
    public UpdateSiteImportResult {
        analysisId = analysisId == null ? "" : analysisId;
        recoveryPackagePath = recoveryPackagePath == null ? "" : recoveryPackagePath;
        recoveredLayers = recoveredLayers == null ? UpdateSiteRecoveredLayers.empty() : recoveredLayers;
    }
}

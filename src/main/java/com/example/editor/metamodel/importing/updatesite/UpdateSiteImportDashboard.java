package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Métricas ya agregadas para evitar cálculos dispersos en la interfaz. */
public record UpdateSiteImportDashboard(long inspectedFiles, long recoveredFiles, long partialFiles,
        long supportingFiles, long unrecoverableFiles, long failedFiles, double recoveryCoverage,
        List<UpdateSiteDashboardSlice> confidence, List<UpdateSiteDashboardSlice> fileStatus,
        List<UpdateSiteDashboardSlice> bundleRoles, List<UpdateSiteDashboardSlice> stages,
        List<UpdateSiteSankeyNode> flowNodes, List<UpdateSiteSankeyLink> flowLinks) {
    public UpdateSiteImportDashboard {
        inspectedFiles = Math.max(0, inspectedFiles);
        recoveredFiles = Math.max(0, recoveredFiles);
        partialFiles = Math.max(0, partialFiles);
        supportingFiles = Math.max(0, supportingFiles);
        unrecoverableFiles = Math.max(0, unrecoverableFiles);
        failedFiles = Math.max(0, failedFiles);
        recoveryCoverage = Math.max(0, Math.min(100, recoveryCoverage));
        confidence = copy(confidence);
        fileStatus = copy(fileStatus);
        bundleRoles = copy(bundleRoles);
        stages = copy(stages);
        flowNodes = flowNodes == null ? List.of() : List.copyOf(flowNodes);
        flowLinks = flowLinks == null ? List.of() : List.copyOf(flowLinks);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de UpdateSiteImportDashboard. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteImportDashboard empty() {
        return new UpdateSiteImportDashboard(0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de UpdateSiteImportDashboard. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<UpdateSiteDashboardSlice> copy(List<UpdateSiteDashboardSlice> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}

package com.example.editor.standalone;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;

/** Solicitud portable y configurable de un editor web independiente generado. */
public record StandaloneEditorGenerationRequest(String applicationId, String name, String metamodelId,
        String editorId, int port, String runtimeMode, String runtimeBaseUrl,
        boolean includeCapabilityArchive, boolean includeAllCanonicalDocuments,
        long maximumModelBytes, List<String> includedViewpointIds, List<String> allowedOperationIds,
        Map<String, String> branding, HydraCapabilityPackage capabilityPackage,
        Set<StandaloneDeploymentTarget> deploymentTargets, GrammarCompilationSnapshot grammarSnapshot) {
    public StandaloneEditorGenerationRequest {
        applicationId = safe(applicationId);
        name = safe(name);
        metamodelId = safe(metamodelId);
        editorId = safe(editorId);
        port = port <= 0 ? 4173 : port;
        runtimeMode = safe(runtimeMode).isBlank() ? "EMBEDDED_LOCAL" : safe(runtimeMode);
        runtimeBaseUrl = safe(runtimeBaseUrl);
        maximumModelBytes = maximumModelBytes <= 0 ? 10L * 1024L * 1024L : maximumModelBytes;
        includedViewpointIds = includedViewpointIds == null ? List.of() : List.copyOf(includedViewpointIds);
        allowedOperationIds = allowedOperationIds == null ? List.of() : List.copyOf(allowedOperationIds);
        branding = branding == null ? Map.of() : Map.copyOf(branding);
        deploymentTargets = deploymentTargets == null || deploymentTargets.isEmpty()
                ? Set.of(StandaloneDeploymentTarget.SOURCE) : Set.copyOf(deploymentTargets);
    }

    /** Compatibility constructor for visual standalone requests created before textual snapshots were supported. */
    /** Descripción: Inicializa una instancia de StandaloneEditorGenerationRequest con sus dependencias y datos de entrada. Entradas: applicationId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; port: dato de entrada necesario para la operación; runtimeMode: dato de entrada necesario para la operación; runtimeBaseUrl: dato de entrada necesario para la operación; includeCapabilityArchive: archivo o contenido que debe procesarse; includeAllCanonicalDocuments: dato de entrada necesario para la operación; maximumModelBytes: dato de entrada necesario para la operación; includedViewpointIds: dato de entrada necesario para la operación; allowedOperationIds: dato de entrada necesario para la operación; branding: dato de entrada necesario para la operación; capabilityPackage: dato de entrada necesario para la operación; deploymentTargets: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneEditorGenerationRequest(String applicationId, String name, String metamodelId,
            String editorId, int port, String runtimeMode, String runtimeBaseUrl,
            boolean includeCapabilityArchive, boolean includeAllCanonicalDocuments,
            long maximumModelBytes, List<String> includedViewpointIds, List<String> allowedOperationIds,
            Map<String, String> branding, HydraCapabilityPackage capabilityPackage,
            Set<StandaloneDeploymentTarget> deploymentTargets) {
        this(applicationId, name, metamodelId, editorId, port, runtimeMode, runtimeBaseUrl,
                includeCapabilityArchive, includeAllCanonicalDocuments, maximumModelBytes, includedViewpointIds,
                allowedOperationIds, branding, capabilityPackage, deploymentTargets, null);
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de StandaloneEditorGenerationRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null ? "" : value.trim(); }
}

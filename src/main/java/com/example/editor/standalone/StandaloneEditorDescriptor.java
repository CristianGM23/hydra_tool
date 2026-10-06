package com.example.editor.standalone;

import java.util.List;
import java.util.Map;

/** Descriptor consumido por la aplicación web y su servidor Java generados. */
public record StandaloneEditorDescriptor(String schemaVersion, String applicationId, String name,
        String metamodelId, String semanticScopeId, String editorId, int port, String runtimeMode, String runtimeBaseUrl,
        String capabilityPackagePath, String capabilityContentHash, String metamodelDocumentPath,
        String editorDocumentPath, List<String> capabilities, Map<String, String> settings,
        Map<String, String> annotations) {
    public static final String CURRENT_SCHEMA_VERSION = "1.1";

    public StandaloneEditorDescriptor {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion;
        applicationId = safe(applicationId);
        name = safe(name);
        metamodelId = safe(metamodelId);
        semanticScopeId = safe(semanticScopeId).isBlank() ? metamodelId : safe(semanticScopeId);
        if(semanticScopeId.isBlank()) throw new IllegalArgumentException("semanticScopeId or metamodelId is required");
        editorId = safe(editorId);
        port = port <= 0 ? 4173 : port;
        runtimeMode = safe(runtimeMode).isBlank() ? "EMBEDDED_LOCAL" : safe(runtimeMode);
        runtimeBaseUrl = safe(runtimeBaseUrl);
        capabilityPackagePath = safe(capabilityPackagePath);
        capabilityContentHash = safe(capabilityContentHash);
        metamodelDocumentPath = safe(metamodelDocumentPath);
        editorDocumentPath = safe(editorDocumentPath);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }


    /** Descripción: Mantiene compatibilidad con descriptores 1.1 sin semanticScopeId explícito. Entradas: contrato histórico completo. Salida: usa metamodelId como ámbito semántico. */
    /** Descripción: Inicializa una instancia de StandaloneEditorDescriptor con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; applicationId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; port: dato de entrada necesario para la operación; runtimeMode: dato de entrada necesario para la operación; runtimeBaseUrl: dato de entrada necesario para la operación; capabilityPackagePath: ruta o ubicación utilizada por la operación; capabilityContentHash: dato de entrada necesario para la operación; metamodelDocumentPath: ruta o ubicación utilizada por la operación; editorDocumentPath: ruta o ubicación utilizada por la operación; capabilities: dato de entrada necesario para la operación; settings: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneEditorDescriptor(String schemaVersion, String applicationId, String name,
            String metamodelId, String editorId, int port, String runtimeMode, String runtimeBaseUrl,
            String capabilityPackagePath, String capabilityContentHash, String metamodelDocumentPath,
            String editorDocumentPath, List<String> capabilities, Map<String, String> settings,
            Map<String, String> annotations) {
        this(schemaVersion, applicationId, name, metamodelId, metamodelId, editorId, port, runtimeMode,
                runtimeBaseUrl, capabilityPackagePath, capabilityContentHash, metamodelDocumentPath,
                editorDocumentPath, capabilities, settings, annotations);
    }

    /** Constructor de conveniencia para un editor conectado al servicio HYDRA con valores runtime predeterminados. */
    /** Descripción: Inicializa una instancia de StandaloneEditorDescriptor con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; applicationId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; runtimeBaseUrl: dato de entrada necesario para la operación; capabilityPackagePath: ruta o ubicación utilizada por la operación; capabilityContentHash: dato de entrada necesario para la operación; capabilities: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneEditorDescriptor(String schemaVersion, String applicationId, String name,
            String metamodelId, String editorId, String runtimeBaseUrl, String capabilityPackagePath,
            String capabilityContentHash, List<String> capabilities, Map<String, String> annotations) {
        this(schemaVersion, applicationId, name, metamodelId, metamodelId, editorId, 4173, "HYDRA_SERVICE",
                runtimeBaseUrl, capabilityPackagePath, capabilityContentHash, "", "", capabilities,
                Map.of(), annotations);
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de StandaloneEditorDescriptor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null ? "" : value.trim(); }
}

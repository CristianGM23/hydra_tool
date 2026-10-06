package com.example.editor.integration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Configuración portable para inyectar o proyectar capacidades en una tecnología externa. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IntegrationProfileModel(String schemaVersion, String id, String metamodelId, String semanticScopeId, String name,
        IntegrationTarget target, IntegrationDeliveryMode deliveryMode, Set<IntegrationFeature> features,
        List<IntegrationArtifactSelection> artifacts, List<IntegrationElementMapping> mappings,
        InjectionPolicy injectionPolicy, Set<IntegrationCapability> requiredCapabilities, Map<String, String> annotations) {
    public IntegrationProfileModel {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = IntegrationContractVersions.INTEGRATION_PROFILE_MODEL;
        if(!IntegrationContractVersions.INTEGRATION_PROFILE_MODEL.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported integration profile schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "id");
        metamodelId = metamodelId == null ? "" : metamodelId.trim();
        semanticScopeId = semanticScopeId == null || semanticScopeId.isBlank() ? metamodelId : semanticScopeId.trim();
        if(metamodelId.isBlank() && semanticScopeId.isBlank()) throw new IllegalArgumentException("metamodelId or semanticScopeId is required");
        if(!metamodelId.isBlank()) metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "metamodelId");
        semanticScopeId = StableIdentifierPolicy.requireDocumentId(semanticScopeId, "semanticScopeId");
        name = name == null || name.isBlank() ? id : name.trim();
        if(target == null || deliveryMode == null) throw new IllegalArgumentException("target and deliveryMode are required");
        features = features == null ? Set.of() : Set.copyOf(features);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        if(injectionPolicy == null) injectionPolicy = InjectionPolicy.safeDefaults();
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }

    /** Descripción: Mantiene compatibilidad con perfiles 1.0 anteriores al ámbito semántico neutral. Entradas: contrato histórico completo. Salida: crea el perfil usando metamodelId como semanticScopeId. */
    /** Descripción: Inicializa una instancia de IntegrationProfileModel con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; deliveryMode: dato de entrada necesario para la operación; features: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación; mappings: dato de entrada necesario para la operación; injectionPolicy: dato de entrada necesario para la operación; requiredCapabilities: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public IntegrationProfileModel(String schemaVersion, String id, String metamodelId, String name,
            IntegrationTarget target, IntegrationDeliveryMode deliveryMode, Set<IntegrationFeature> features,
            List<IntegrationArtifactSelection> artifacts, List<IntegrationElementMapping> mappings,
            InjectionPolicy injectionPolicy, Set<IntegrationCapability> requiredCapabilities, Map<String, String> annotations) {
        this(schemaVersion, id, metamodelId, metamodelId, name, target, deliveryMode, features, artifacts, mappings,
                injectionPolicy, requiredCapabilities, annotations);
    }
}

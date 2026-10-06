package com.example.editor.integration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Comprueba dependencias, capacidades y restricciones específicas de cada destino. */
@Component
public class IntegrationProfileChecker {
    private final IntegrationArtifactResolver artifactResolver;
    /** Descripción: Inicializa una instancia de IntegrationProfileChecker con sus dependencias y datos de entrada. Entradas: artifactResolver: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public IntegrationProfileChecker(IntegrationArtifactResolver artifactResolver) { this.artifactResolver = artifactResolver; }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationCheckReport check(IntegrationProfileModel profile) {
        Set<IntegrationDiagnostic> diagnostics = new LinkedHashSet<>();
        List<IntegrationArtifactResolution> resolutions = new ArrayList<>();
        IntegrationTechnologyDescriptor descriptor = IntegrationTechnologyCatalog.descriptor(profile.target().technology());

        if(profile.target().kind() != descriptor.targetKind()) diagnostics.add(error("TARGET_KIND_MISMATCH", profile.id(), "target.kind",
                "Target kind must be " + descriptor.targetKind() + " for " + descriptor.displayName()));
        if(!descriptor.deliveryModes().contains(profile.deliveryMode())) diagnostics.add(error("UNSUPPORTED_DELIVERY_MODE", profile.id(), "deliveryMode",
                descriptor.displayName() + " does not support " + profile.deliveryMode()));
        if(!descriptor.injectionStrategies().contains(profile.injectionPolicy().strategy())) diagnostics.add(error("UNSUPPORTED_INJECTION_STRATEGY", profile.id(), "injectionPolicy.strategy",
                descriptor.displayName() + " does not support " + profile.injectionPolicy().strategy()));

        Set<IntegrationCapability> requestedCapabilities = EnumSet.noneOf(IntegrationCapability.class);
        requestedCapabilities.addAll(profile.requiredCapabilities());
        profile.features().forEach(feature -> requestedCapabilities.addAll(feature.requiredCapabilities()));
        for(IntegrationCapability capability : requestedCapabilities) {
            IntegrationSupportLevel level = descriptor.support(capability);
            if(level == IntegrationSupportLevel.UNSUPPORTED) diagnostics.add(error("UNSUPPORTED_CAPABILITY", profile.id(), "requiredCapabilities", descriptor.displayName() + " does not support " + capability));
            else if(level == IntegrationSupportLevel.PARTIAL) diagnostics.add(warning("PARTIAL_CAPABILITY", profile.id(), "requiredCapabilities", capability + " requires generated or version-specific adapter code"));
            else if(level == IntegrationSupportLevel.EXTERNAL) diagnostics.add(warning("EXTERNAL_CAPABILITY", profile.id(), "requiredCapabilities", capability + " must be provided by an external runtime"));
        }

        checkTechnologyRules(profile, descriptor, diagnostics);
        checkArtifacts(profile, diagnostics, resolutions);
        checkMappings(profile, descriptor, diagnostics);
        checkFeatureArtifacts(profile, diagnostics);
        checkInjectionSafety(profile, diagnostics);
        return new IntegrationCheckReport(profile.id(), List.copyOf(diagnostics), List.copyOf(resolutions));
    }

    /** Descripción: Valida las condiciones necesarias para «checkTechnologyRules» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; descriptor: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkTechnologyRules(IntegrationProfileModel profile, IntegrationTechnologyDescriptor descriptor, Set<IntegrationDiagnostic> diagnostics) {
        if(profile.target().technology() == IntegrationTechnology.PLANTUML) {
            if(profile.deliveryMode() != IntegrationDeliveryMode.PROJECTION_ONLY) diagnostics.add(error("PLANTUML_PROJECTION_ONLY", profile.id(), "deliveryMode", "PlantUML can only be used as a projection target"));
            if(profile.features().stream().anyMatch(IntegrationFeature::runtimeFeature)) diagnostics.add(error("PLANTUML_RUNTIME_FORBIDDEN", profile.id(), "features", "PlantUML cannot execute runtime features"));
        }
        if(profile.target().technology() == IntegrationTechnology.BESSER_SDK && (profile.features().contains(IntegrationFeature.GAMIFICATION_UI) || profile.features().contains(IntegrationFeature.EDITOR_UI_CONTRIBUTION) || profile.features().contains(IntegrationFeature.PALETTE_CONTRIBUTION))) {
            diagnostics.add(error("BESSER_SDK_UI_TARGET", profile.id(), "features", "BESSER UI contributions require BESSER_WEB_MODELING_EDITOR"));
        }
        if(profile.target().technology() == IntegrationTechnology.BESSER_WEB_MODELING_EDITOR && profile.deliveryMode() == IntegrationDeliveryMode.EMBEDDED) {
            diagnostics.add(error("BESSER_WME_EMBEDDED_UNSUPPORTED", profile.id(), "deliveryMode", "Use GENERATED_MODULE, REMOTE or HYBRID for the BESSER web editor"));
        }
        if(profile.target().buildSystem() == IntegrationBuildSystem.NONE && profile.deliveryMode() == IntegrationDeliveryMode.GENERATED_MODULE && profile.target().technology() != IntegrationTechnology.PLANTUML) {
            diagnostics.add(warning("MISSING_BUILD_SYSTEM", profile.id(), "target.buildSystem", "Generated modules should declare the host build system"));
        }
        if(profile.target().technology() == IntegrationTechnology.PLANTUML && profile.target().buildSystem() != IntegrationBuildSystem.NONE) {
            diagnostics.add(info("PLANTUML_BUILD_OPTIONAL", profile.id(), "target.buildSystem", "PlantUML projections do not require a build system unless integrated into a larger project"));
        }
        if(descriptor.notes().isEmpty()) return;
        for(String note : descriptor.notes()) diagnostics.add(info("TARGET_NOTE", profile.id(), "target", note));
    }

    /** Descripción: Valida las condiciones necesarias para «checkArtifacts» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; diagnostics: dato de entrada necesario para la operación; resolutions: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkArtifacts(IntegrationProfileModel profile, Set<IntegrationDiagnostic> diagnostics, List<IntegrationArtifactResolution> resolutions) {
        Set<IntegrationArtifactKind> kinds = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for(IntegrationArtifactSelection artifact : profile.artifacts()) {
            String key = artifact.kind() + "::" + artifact.documentId();
            if(!keys.add(key)) diagnostics.add(error("DUPLICATE_ARTIFACT", profile.id(), "artifacts", "Artifact is selected more than once: " + key));
            kinds.add(artifact.kind());
            IntegrationArtifactResolution resolution = artifactResolver.resolve(profile.semanticScopeId(), artifact);
            resolutions.add(resolution);
            if(resolution.status() == IntegrationResolutionStatus.NOT_FOUND || resolution.status() == IntegrationResolutionStatus.INCOMPATIBLE) {
                IntegrationDiagnostic diagnostic = artifact.required()
                        ? error("UNRESOLVED_REQUIRED_ARTIFACT", artifact.documentId(), "artifacts", resolution.message())
                        : warning("UNRESOLVED_OPTIONAL_ARTIFACT", artifact.documentId(), "artifacts", resolution.message());
                diagnostics.add(diagnostic);
            } else if(resolution.status() == IntegrationResolutionStatus.DEFERRED) {
                diagnostics.add(warning("DEFERRED_ARTIFACT", artifact.documentId(), "artifacts", resolution.message()));
            }
        }
        if(!kinds.contains(IntegrationArtifactKind.METAMODEL) && !kinds.contains(IntegrationArtifactKind.GRAMMAR)) diagnostics.add(error(
                "MISSING_SEMANTIC_ROOT_ARTIFACT", profile.id(), "artifacts",
                "Every integration profile must include a semantic root: metamodel or grammar"));
    }

    /** Descripción: Valida las condiciones necesarias para «checkMappings» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; descriptor: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkMappings(IntegrationProfileModel profile, IntegrationTechnologyDescriptor descriptor, Set<IntegrationDiagnostic> diagnostics) {
        Set<String> ids = new HashSet<>();
        for(IntegrationElementMapping mapping : profile.mappings()) {
            if(!ids.add(mapping.id())) diagnostics.add(error("DUPLICATE_MAPPING", mapping.id(), "mappings", "Duplicate mapping id"));
            if(!descriptor.locatorStrategies().contains(mapping.hostReference().strategy())) diagnostics.add(error("UNSUPPORTED_LOCATOR", mapping.id(), "hostReference.strategy", descriptor.displayName() + " does not support " + mapping.hostReference().strategy()));
            if(mapping.canonicalDocumentId().equals(profile.id())) diagnostics.add(warning("PROFILE_SELF_MAPPING", mapping.id(), "canonicalDocumentId", "Mappings normally point to semantic or behavioral documents, not to the integration profile itself"));
        }
        if(profile.features().stream().anyMatch(IntegrationFeature::runtimeFeature) && profile.mappings().isEmpty()) diagnostics.add(warning("NO_RUNTIME_MAPPINGS", profile.id(), "mappings", "Runtime integration is enabled but no host element mappings are defined"));
    }

    /** Descripción: Valida las condiciones necesarias para «checkFeatureArtifacts» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkFeatureArtifacts(IntegrationProfileModel profile, Set<IntegrationDiagnostic> diagnostics) {
        Set<IntegrationArtifactKind> kinds = profile.artifacts().stream().map(IntegrationArtifactSelection::kind).collect(java.util.stream.Collectors.toSet());
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.VALIDATION_RUNTIME, IntegrationFeature.VALIDATION_PROJECTION), IntegrationArtifactKind.VALIDATIONS);
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.QUICK_FIX_RUNTIME), IntegrationArtifactKind.ACTIONS);
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.OPERATION_RUNTIME), IntegrationArtifactKind.OPERATION_BEHAVIORS);
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.EVENT_PUBLICATION, IntegrationFeature.EVENT_CONSUMPTION, IntegrationFeature.EVENT_DEFINITION_PROJECTION), IntegrationArtifactKind.EVENT_DEFINITIONS);
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.EVENT_FLOW_RUNTIME, IntegrationFeature.EVENT_FLOW_PROJECTION), IntegrationArtifactKind.EVENT_FLOWS);
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.GAMIFICATION_RUNTIME, IntegrationFeature.GAMIFICATION_UI, IntegrationFeature.GAMIFICATION_PROJECTION), IntegrationArtifactKind.GAMIFICATION_DEFINITIONS);
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.EDITOR_UI_CONTRIBUTION, IntegrationFeature.NOTATION_CONTRIBUTION, IntegrationFeature.PALETTE_CONTRIBUTION), IntegrationArtifactKind.EDITOR_DEFINITION);
        requireAny(profile, kinds, diagnostics, Set.of(IntegrationFeature.CODE_GENERATION), IntegrationArtifactKind.CODE_GENERATION);
    }

    /** Descripción: Valida las condiciones necesarias para «requireAny» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; kinds: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; features: dato de entrada necesario para la operación; artifactKind: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireAny(IntegrationProfileModel profile, Set<IntegrationArtifactKind> kinds, Set<IntegrationDiagnostic> diagnostics,
            Set<IntegrationFeature> features, IntegrationArtifactKind artifactKind) {
        if(profile.features().stream().anyMatch(features::contains) && !kinds.contains(artifactKind)) diagnostics.add(error("MISSING_FEATURE_ARTIFACT", profile.id(), "artifacts", artifactKind + " is required by the selected features"));
    }

    /** Descripción: Valida las condiciones necesarias para «checkInjectionSafety» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkInjectionSafety(IntegrationProfileModel profile, Set<IntegrationDiagnostic> diagnostics) {
        InjectionPolicy policy = profile.injectionPolicy();
        if(!policy.previewRequired()) diagnostics.add(error("PREVIEW_REQUIRED", profile.id(), "injectionPolicy.previewRequired", "Injection preview cannot be disabled"));
        if(!policy.rollbackRequired()) diagnostics.add(error("ROLLBACK_REQUIRED", profile.id(), "injectionPolicy.rollbackRequired", "A rollback plan is mandatory"));
        if(!policy.preserveCustomCode()) diagnostics.add(warning("CUSTOM_CODE_NOT_PRESERVED", profile.id(), "injectionPolicy.preserveCustomCode", "Generated integration should preserve user code"));
        if(policy.allowModifyGeneratedFiles()) diagnostics.add(warning("MODIFIES_GENERATED_FILES", profile.id(), "injectionPolicy.allowModifyGeneratedFiles", "Modifying generated host files increases regeneration risk"));
        if(policy.strategy() == InjectionStrategy.PATCH_EXISTING) diagnostics.add(warning("PATCH_EXISTING_RISK", profile.id(), "injectionPolicy.strategy", "PATCH_EXISTING is less reversible than adding a separate module"));
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de IntegrationProfileChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDiagnostic error(String code, String id, String path, String message) { return new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, code, id, path, message); }
    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de IntegrationProfileChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDiagnostic warning(String code, String id, String path, String message) { return new IntegrationDiagnostic(IntegrationDiagnosticSeverity.WARNING, code, id, path, message); }
    /** Descripción: Ejecuta la operación «info» correspondiente a la responsabilidad de IntegrationProfileChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDiagnostic info(String code, String id, String path, String message) { return new IntegrationDiagnostic(IntegrationDiagnosticSeverity.INFO, code, id, path, message); }
}

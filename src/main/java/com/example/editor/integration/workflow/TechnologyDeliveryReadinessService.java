package com.example.editor.integration.workflow;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.integration.IntegrationCheckReport;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationProfileApplicationService;
import com.example.editor.integration.IntegrationProfileLoadResult;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyIntegrationApplicationService;

/** Deriva la preparación real de entrega para un perfil persistido sin ejecutar toolchains externas ni modificar el host. */
@Service
public class TechnologyDeliveryReadinessService {
    private final IntegrationProfileApplicationService profiles;
    private final TechnologyWorkflowService workflows;
    private final TechnologyIntegrationApplicationService integrations;

    /** Descripción: Inicializa el servicio de preparación tecnológica. Entradas: profiles: perfiles persistidos; workflows: paridad tecnológica global; integrations: compilación neutral de proyectos. Salida: deja el servicio listo para calcular readiness sin efectos externos. */
    public TechnologyDeliveryReadinessService(IntegrationProfileApplicationService profiles,
            TechnologyWorkflowService workflows, TechnologyIntegrationApplicationService integrations) {
        this.profiles = profiles;
        this.workflows = workflows;
        this.integrations = integrations;
    }

    /** Descripción: Calcula la preparación operativa de todas las fases para un perfil persistido. Entradas: semanticScopeId: ámbito semántico; profileId: perfil de integración. Salida: descriptor accionable y fail-closed que no ejecuta procesos externos ni escribe en el host. */
    public TechnologyDeliveryReadiness readiness(String semanticScopeId, String profileId) {
        IntegrationProfileLoadResult loaded = profiles.find(semanticScopeId, profileId);
        IntegrationProfileModel profile = loaded.profile();
        IntegrationCheckReport check = loaded.checkReport();
        TechnologyWorkflowDescriptor workflow = workflows.descriptor(profile.target().technology());
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        check.diagnostics().forEach(diagnostic -> {
            if(diagnostic.severity() == IntegrationDiagnosticSeverity.ERROR) blockers.add(diagnostic.code() + ": " + diagnostic.message());
            else if(diagnostic.severity() == IntegrationDiagnosticSeverity.WARNING) warnings.add(diagnostic.code() + ": " + diagnostic.message());
        });

        TechnologyCompilationResult compilation = null;
        String compilationFailure = "";
        boolean generationImplemented = workflow.stages().stream().anyMatch(stage -> stage.stage() == TechnologyWorkflowStage.GENERATE
                && stage.applicable() && stage.support() != IntegrationSupportLevel.UNSUPPORTED
                && stage.action() == TechnologyWorkflowAction.COMPILE_ARCHIVE);
        if(check.valid() && generationImplemented) {
            try {
                compilation = integrations.compile(semanticScopeId, profileId);
                if(!compilation.valid()) {
                    compilation.diagnostics().stream().filter(value -> value.severity() == IntegrationDiagnosticSeverity.ERROR)
                            .forEach(value -> blockers.add(value.code() + ": " + value.message()));
                    compilation.diagnostics().stream().filter(value -> value.severity() == IntegrationDiagnosticSeverity.WARNING)
                            .forEach(value -> warnings.add(value.code() + ": " + value.message()));
                }
            } catch(TechnologyCompilationException exception) {
                compilationFailure = exception.getMessage() == null ? "Technology generation failed" : exception.getMessage();
                blockers.add(compilationFailure);
            }
        }

        boolean compilationReady = compilation != null && compilation.valid();
        int generatedFiles = compilation == null ? 0 : compilation.files().size();
        int nativeSteps = compilation == null ? 0 : compilation.verificationSteps().size();
        List<TechnologyDeliveryActionDescriptor> actions = new ArrayList<>();
        for(TechnologyWorkflowStageDescriptor stage : workflow.stages()) {
            actions.add(action(profile, check, stage, compilationReady, generatedFiles, nativeSteps, compilationFailure, warnings));
        }
        return new TechnologyDeliveryReadiness(profile.id(), profile.target().technology(), check.valid(), compilationReady,
                generatedFiles, nativeSteps, nativeSteps > 0, actions, List.copyOf(blockers), List.copyOf(warnings));
    }

    /** Descripción: Convierte una fase de madurez tecnológica en una acción concreta para el perfil actual. Entradas: profile: perfil; check: análisis de compatibilidad; stage: fase; compilationReady: indica si el generador pudo materializar el proyecto; generatedFiles: número de ficheros; nativeSteps: verificaciones nativas declaradas; compilationFailure: motivo de fallo; warnings: avisos existentes. Salida: acción con estado, superficie API y precondiciones explícitas. */
    private TechnologyDeliveryActionDescriptor action(IntegrationProfileModel profile, IntegrationCheckReport check,
            TechnologyWorkflowStageDescriptor stage, boolean compilationReady, int generatedFiles, int nativeSteps,
            String compilationFailure, List<String> warnings) {
        if(!stage.applicable()) return descriptor(stage, TechnologyDeliveryActionState.NOT_APPLICABLE, false, false, false, "", "", "Not applicable to this delivery mode.", List.of());
        if(stage.support() == IntegrationSupportLevel.UNSUPPORTED) return descriptor(stage, TechnologyDeliveryActionState.BLOCKED, false, false, false, "", "", "The selected technology does not implement this stage.", List.of());
        return switch(stage.stage()) {
            case CONFIGURE -> descriptor(stage, warningState(stage, warnings), false, false, false, "", "", "The persisted profile can be edited safely.", List.of("Profile " + profile.id() + " is persisted"));
            case ANALYZE -> descriptor(stage, check.valid() ? warningState(stage, warnings) : TechnologyDeliveryActionState.READY_WITH_WARNINGS,
                    true, false, false, "", "", check.valid() ? "Compatibility analysis has no blocking diagnostics." : "Compatibility analysis reports blocking diagnostics that must be fixed before generation.",
                    List.of("Compatibility diagnostics: " + check.diagnostics().size()));
            case GENERATE -> generation(stage, check.valid(), compilationReady, generatedFiles, compilationFailure, warnings);
            case VERIFY -> compilationReady
                    ? descriptor(stage, warningState(stage, warnings), true, false, false, "/technology/validate", "POST",
                            nativeSteps > 0 ? "Structural verification is ready; native verification is available on explicit request." : "Structural verification is ready; no native toolchain step is declared by this generated package.",
                            List.of("Generated files: " + generatedFiles, "Native verification steps: " + nativeSteps))
                    : blockedAfterGeneration(stage, compilationFailure);
            case PREVIEW -> compilationReady
                    ? descriptor(stage, warningState(stage, warnings), true, false, true, "/technology/preview", "POST", "HYDRA can calculate the exact host-project change plan without writing files.", List.of("Target directory must remain inside the configured injection root"))
                    : blockedAfterGeneration(stage, compilationFailure);
            case APPLY -> compilationReady
                    ? descriptor(stage, warningState(stage, warnings), true, true, true, "/technology/apply", "POST", "Application is available only after a READY preview; rollback evidence remains mandatory.", List.of("READY preview required", "Rollback manifest required"))
                    : blockedAfterGeneration(stage, compilationFailure);
            case EXECUTE -> descriptor(stage, TechnologyDeliveryActionState.EXTERNAL, false, false, false, "", "", nativeSteps > 0
                    ? "Execution remains outside HYDRA; use the generated package after its native verification steps pass."
                    : "Execution remains outside HYDRA and requires the native host/toolchain declared by the technology.", List.of("External host/toolchain required"));
            case ROUND_TRIP -> compilationReady
                    ? descriptor(stage, warningState(stage, warnings), true, true, true, "/technology/sync/preview", "POST", "Round-trip can compare the generated baseline with a host project; synchronization remains preview-first and conflict-aware.", List.of("Generated baseline required", "Target directory required", "Manual conflicts must be resolved explicitly"))
                    : blockedAfterGeneration(stage, compilationFailure);
            case EXPORT -> generation(stage, check.valid(), compilationReady, generatedFiles, compilationFailure, warnings);
        };
    }

    /** Descripción: Construye el estado de generación/exportación del perfil. Entradas: stage: fase; profileValid: validez del perfil; compilationReady: compilación preparada; generatedFiles: número de ficheros; compilationFailure: fallo de compilación; warnings: avisos. Salida: descriptor operativo de generación. */
    private TechnologyDeliveryActionDescriptor generation(TechnologyWorkflowStageDescriptor stage, boolean profileValid,
            boolean compilationReady, int generatedFiles, String compilationFailure, List<String> warnings) {
        if(stage.action() == TechnologyWorkflowAction.EXPORT_PROJECTION) {
            return descriptor(stage, warningState(stage, warnings), false, false, false, "", "", "Projection generation is available through the dedicated HYDRA export workspace.", List.of("Projection/export workspace"));
        }
        if(!profileValid) return descriptor(stage, TechnologyDeliveryActionState.BLOCKED, false, false, false, "/technology/compile", "POST", "Fix blocking compatibility diagnostics before generation.", List.of());
        if(!compilationReady) return descriptor(stage, TechnologyDeliveryActionState.BLOCKED, false, false, false, "/technology/compile", "POST", compilationFailure.isBlank() ? "The generated project is not ready." : compilationFailure, List.of());
        return descriptor(stage, warningState(stage, warnings), true, false, false, "/technology/compile", "POST", "The technology package can be generated without modifying the host project.", List.of("Generated files: " + generatedFiles));
    }

    /** Descripción: Devuelve un bloqueo común para fases que dependen de una generación válida. Entradas: stage: fase; compilationFailure: detalle opcional. Salida: descriptor bloqueado sin ocultar la causa. */
    private TechnologyDeliveryActionDescriptor blockedAfterGeneration(TechnologyWorkflowStageDescriptor stage, String compilationFailure) {
        String reason = compilationFailure.isBlank() ? "Generate a valid technology package before using this stage." : "Generation is blocked: " + compilationFailure;
        return descriptor(stage, TechnologyDeliveryActionState.BLOCKED, false, stage.stage() == TechnologyWorkflowStage.APPLY || stage.stage() == TechnologyWorkflowStage.ROUND_TRIP,
                stage.stage() == TechnologyWorkflowStage.PREVIEW || stage.stage() == TechnologyWorkflowStage.APPLY || stage.stage() == TechnologyWorkflowStage.ROUND_TRIP,
                "", "", reason, List.of());
    }

    /** Descripción: Convierte soporte parcial o avisos actuales en un estado visible que requiere revisión sin bloquear una acción implementada. Entradas: stage: fase; warnings: avisos del perfil. Salida: READY o READY_WITH_WARNINGS. */
    private TechnologyDeliveryActionState warningState(TechnologyWorkflowStageDescriptor stage, List<String> warnings) {
        return stage.support() == IntegrationSupportLevel.PARTIAL || !warnings.isEmpty()
                ? TechnologyDeliveryActionState.READY_WITH_WARNINGS : TechnologyDeliveryActionState.READY;
    }

    /** Descripción: Crea un descriptor de acción aplicando de forma consistente seguridad, mutabilidad y precondiciones. Entradas: stage: fase original; state: estado; hydraExecutable: ejecución interna; mutatesHost: escritura host; targetDirectoryRequired: directorio requerido; apiSuffix/httpMethod: superficie REST; reason: explicación; evidence: evidencia. Salida: descriptor inmutable. */
    private TechnologyDeliveryActionDescriptor descriptor(TechnologyWorkflowStageDescriptor stage, TechnologyDeliveryActionState state,
            boolean hydraExecutable, boolean mutatesHost, boolean targetDirectoryRequired, String apiSuffix, String httpMethod,
            String reason, List<String> evidence) {
        return new TechnologyDeliveryActionDescriptor(stage.stage(), stage.action(), state, stage.support(), hydraExecutable,
                mutatesHost, targetDirectoryRequired, stage.stage() == TechnologyWorkflowStage.APPLY, apiSuffix, httpMethod,
                reason, stage.prerequisites(), evidence);
    }
}

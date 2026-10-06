package com.example.editor.integration.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.integration.InjectionStrategy;
import com.example.editor.integration.IntegrationCapability;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationTechnologyCatalog;
import com.example.editor.integration.IntegrationTechnologyDescriptor;
import com.example.editor.integration.compiler.TechnologyAdapterRegistry;
import com.example.editor.integration.verification.TechnologyProjectStructureValidatorRegistry;

/** Calcula el recorrido real de cada tecnología a partir del catálogo y de las implementaciones registradas. */
@Service
public class TechnologyWorkflowService {
    private final TechnologyAdapterRegistry adapters;
    private final TechnologyProjectStructureValidatorRegistry validators;
    private final TechnologyWorkflowProperties properties;

    /** Descripción: Inicializa una instancia de TechnologyWorkflowService con sus dependencias y datos de entrada. Entradas: adapters: dato de entrada necesario para la operación; validators: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyWorkflowService(TechnologyAdapterRegistry adapters,
            TechnologyProjectStructureValidatorRegistry validators,
            TechnologyWorkflowProperties properties) {
        this.adapters = adapters;
        this.validators = validators;
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «descriptors» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TechnologyWorkflowDescriptor> descriptors(){
        return Arrays.stream(IntegrationTechnology.values()).map(this::descriptor).toList();
    }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyWorkflowDescriptor descriptor(IntegrationTechnology technology){
        IntegrationTechnologyDescriptor declared = IntegrationTechnologyCatalog.descriptor(technology);
        boolean projectionOnly = declared.targetKind() == IntegrationTargetKind.DIAGRAM_PROJECTION;
        boolean adapterImplemented = adapters.supports(technology);
        boolean validatorImplemented = validators.supports(technology);
        List<TechnologyWorkflowStageDescriptor> stages = List.of(
                stage(TechnologyWorkflowStage.CONFIGURE, IntegrationSupportLevel.FULL, true, TechnologyWorkflowAction.EDIT_PROFILE,
                        "Configure", "Choose the target, delivery boundary, capabilities, inputs and host mappings.",
                        "A technology profile with explicit, editable decisions.", "Semantic root selected", "Target technology selected"),
                stage(TechnologyWorkflowStage.ANALYZE, IntegrationSupportLevel.FULL, true, TechnologyWorkflowAction.CHECK_PROFILE,
                        "Analyze", "Check declared capabilities, required HYDRA inputs and host mappings before generation.",
                        "An actionable compatibility report without modifying the host project.", "Profile configuration available"),
                stage(TechnologyWorkflowStage.GENERATE, generationSupport(declared, adapterImplemented, projectionOnly), true,
                        projectionOnly ? TechnologyWorkflowAction.EXPORT_PROJECTION : TechnologyWorkflowAction.COMPILE_ARCHIVE,
                        "Generate", "Materialize the target-specific project or projection from the neutral HYDRA definition.",
                        "A portable technology artifact that can be inspected independently from HYDRA.", "No blocking compatibility diagnostics"),
                stage(TechnologyWorkflowStage.VERIFY, validatorImplemented ? IntegrationSupportLevel.FULL : IntegrationSupportLevel.UNSUPPORTED,
                        !projectionOnly, TechnologyWorkflowAction.VALIDATE_PROJECT,
                        "Verify", "Validate generated structure and optionally invoke the native build or test toolchain.",
                        "A structural or native validation report tied to the generated project.", "Generated project available"),
                stage(TechnologyWorkflowStage.PREVIEW, applySupport(adapterImplemented, projectionOnly), !projectionOnly,
                        TechnologyWorkflowAction.PREVIEW_CHANGES, "Preview changes",
                        "Resolve exact host-project changes before any file is written.",
                        "A reversible file-change plan with conflicts and rollback information.", "Saved profile", "Authorized host folder"),
                stage(TechnologyWorkflowStage.APPLY, applySupport(adapterImplemented, projectionOnly), !projectionOnly,
                        TechnologyWorkflowAction.APPLY_CHANGES, "Apply",
                        "Apply only a READY preview and record the manifest required to undo generated changes.",
                        "A host integration with explicit generated-file ownership and rollback evidence.", "READY change preview"),
                stage(TechnologyWorkflowStage.EXECUTE, adapterImplemented ? IntegrationSupportLevel.EXTERNAL : IntegrationSupportLevel.UNSUPPORTED,
                        !projectionOnly, TechnologyWorkflowAction.RUN_NATIVE_HOST, "Execute",
                        "Run or open the generated integration in its native host. HYDRA keeps the execution boundary explicit.",
                        "A native editor or runtime using the generated HYDRA bridge.", "Generated or applied project", "Native host/toolchain available"),
                stage(TechnologyWorkflowStage.ROUND_TRIP, effectiveCapability(declared, IntegrationCapability.ROUND_TRIP, adapterImplemented || projectionOnly),
                        true, TechnologyWorkflowAction.ROUND_TRIP_SYNC, "Round-trip",
                        "Compare external changes with generated ownership and import only changes that can be represented safely.",
                        "A reviewed synchronization plan that preserves manual regions and traceability.", "Stable host mappings", "Generated baseline available"),
                stage(TechnologyWorkflowStage.EXPORT, effectiveCapability(declared, IntegrationCapability.PROJECT_EXPORT, adapterImplemented || projectionOnly),
                        true, projectionOnly ? TechnologyWorkflowAction.EXPORT_PROJECTION : TechnologyWorkflowAction.EXPORT_PROJECT,
                        "Export", "Package the resulting technology artifact without coupling the canonical HYDRA model to the destination.",
                        "A portable deliverable that remains traceable to its HYDRA semantic source.", "Generated artifact available")
        );
        int score = score(stages);
        List<String> blockers = blockers(declared, adapterImplemented, validatorImplemented, projectionOnly);
        List<String> recommendations = recommendations(declared, stages, adapterImplemented, validatorImplemented, projectionOnly);
        return new TechnologyWorkflowDescriptor(technology, declared.displayName(), declared.targetKind(),
                maturity(score, adapterImplemented, validatorImplemented, projectionOnly, stages), score,
                adapterImplemented, validatorImplemented, stages, blockers, recommendations);
    }

    /** Descripción: Ejecuta la operación «generationSupport» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: descriptor: dato de entrada necesario para la operación; adapterImplemented: dato de entrada necesario para la operación; projectionOnly: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationSupportLevel generationSupport(IntegrationTechnologyDescriptor descriptor, boolean adapterImplemented, boolean projectionOnly){
        if(adapterImplemented) return IntegrationSupportLevel.FULL;
        if(projectionOnly && descriptor.support(IntegrationCapability.TEXTUAL_SOURCE_GENERATION) != IntegrationSupportLevel.UNSUPPORTED)
            return descriptor.support(IntegrationCapability.TEXTUAL_SOURCE_GENERATION);
        return IntegrationSupportLevel.UNSUPPORTED;
    }

    /** Descripción: Actualiza el estado gestionado por TechnologyWorkflowService mediante la operación «applySupport». Entradas: adapterImplemented: dato de entrada necesario para la operación; projectionOnly: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationSupportLevel applySupport(boolean adapterImplemented, boolean projectionOnly){
        return adapterImplemented && !projectionOnly ? IntegrationSupportLevel.FULL : IntegrationSupportLevel.UNSUPPORTED;
    }

    /** Descripción: Ejecuta la operación «effectiveCapability» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: descriptor: dato de entrada necesario para la operación; capability: dato de entrada necesario para la operación; implementationSurface: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationSupportLevel effectiveCapability(IntegrationTechnologyDescriptor descriptor, IntegrationCapability capability, boolean implementationSurface){
        return implementationSurface ? descriptor.support(capability) : IntegrationSupportLevel.UNSUPPORTED;
    }

    /** Descripción: Ejecuta la operación «stage» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: stage: dato de entrada necesario para la operación; support: dato de entrada necesario para la operación; applicable: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; outcome: dato de entrada necesario para la operación; prerequisites: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyWorkflowStageDescriptor stage(TechnologyWorkflowStage stage, IntegrationSupportLevel support, boolean applicable,
            TechnologyWorkflowAction action, String title, String description, String outcome, String... prerequisites){
        return new TechnologyWorkflowStageDescriptor(stage, support, applicable, action, title, description, outcome, List.of(prerequisites));
    }

    /** Descripción: Ejecuta la operación «score» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: stages: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int score(List<TechnologyWorkflowStageDescriptor> stages){
        long weighted = 0;
        long possible = 0;
        for(TechnologyWorkflowStageDescriptor stage : stages){
            if(!stage.applicable()) continue;
            int weight = properties.weight(stage.stage());
            possible += weight * 100L;
            weighted += weight * (long) properties.supportPercent(stage.support());
        }
        return possible == 0 ? 0 : (int)Math.round((weighted * 100.0) / possible);
    }

    /** Descripción: Ejecuta la operación «maturity» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: score: dato de entrada necesario para la operación; adapterImplemented: dato de entrada necesario para la operación; validatorImplemented: dato de entrada necesario para la operación; projectionOnly: dato de entrada necesario para la operación; stages: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyWorkflowMaturity maturity(int score, boolean adapterImplemented, boolean validatorImplemented,
            boolean projectionOnly, List<TechnologyWorkflowStageDescriptor> stages){
        if(projectionOnly) return TechnologyWorkflowMaturity.PROJECTION_ONLY;
        if(!adapterImplemented) return TechnologyWorkflowMaturity.DECLARED_ONLY;
        boolean unsupportedApplicable = stages.stream().anyMatch(value -> value.applicable() && value.support() == IntegrationSupportLevel.UNSUPPORTED);
        if(validatorImplemented && !unsupportedApplicable && score >= properties.getFullWorkflowThreshold()) return TechnologyWorkflowMaturity.FULL_WORKFLOW;
        if(validatorImplemented && score >= properties.getOperationalThreshold()) return TechnologyWorkflowMaturity.OPERATIONAL;
        return TechnologyWorkflowMaturity.PARTIAL;
    }

    /** Descripción: Ejecuta la operación «blockers» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: descriptor: dato de entrada necesario para la operación; adapterImplemented: dato de entrada necesario para la operación; validatorImplemented: dato de entrada necesario para la operación; projectionOnly: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> blockers(IntegrationTechnologyDescriptor descriptor, boolean adapterImplemented, boolean validatorImplemented, boolean projectionOnly){
        List<String> result = new ArrayList<>();
        if(!adapterImplemented && !projectionOnly) result.add("No executable TechnologyAdapter is registered for this technology.");
        if(adapterImplemented && !validatorImplemented) result.add("No generated-project validator is registered for this technology.");
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «recommendations» correspondiente a la responsabilidad de TechnologyWorkflowService. Entradas: descriptor: dato de entrada necesario para la operación; stages: dato de entrada necesario para la operación; adapterImplemented: dato de entrada necesario para la operación; validatorImplemented: dato de entrada necesario para la operación; projectionOnly: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> recommendations(IntegrationTechnologyDescriptor descriptor, List<TechnologyWorkflowStageDescriptor> stages,
            boolean adapterImplemented, boolean validatorImplemented, boolean projectionOnly){
        List<String> result = new ArrayList<>();
        result.add("Use the recommended delivery mode and host integration method shown in the profile before enabling advanced overrides.");
        if(!adapterImplemented && !projectionOnly) result.add("Implement and register the technology adapter before offering generated-project delivery.");
        if(adapterImplemented && !validatorImplemented) result.add("Add a structure validator before treating generated artifacts as qualification-ready.");
        if(stages.stream().anyMatch(value -> value.applicable() && value.support() == IntegrationSupportLevel.PARTIAL))
            result.add("Review partial stages explicitly before enabling them in a default guided recipe.");
        if(stages.stream().anyMatch(value -> value.applicable() && value.support() == IntegrationSupportLevel.EXTERNAL))
            result.add("Keep native-host execution explicit and report the required external toolchain to the user.");
        if(descriptor.injectionStrategies().contains(InjectionStrategy.PATCH_EXISTING))
            result.add("Prefer additive generation over PATCH_EXISTING unless the host project requires an in-place integration.");
        return List.copyOf(result);
    }
}

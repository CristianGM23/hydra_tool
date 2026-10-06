package com.example.editor.portability;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.integration.IntegrationCapability;
import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationTechnologyCatalog;
import com.example.editor.integration.IntegrationTechnologyDescriptor;

/** Publica una matriz conservadora entre capas neutrales y capacidades tecnológicas. */
@Service
public class CapabilityCompatibilityMatrixService {

    private static final Map<HydraCapabilityKind, IntegrationCapability> CAPABILITY_MAPPING = createMapping();

    /** Descripción: Ejecuta la operación «matrix» correspondiente a la responsabilidad de CapabilityCompatibilityMatrixService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TargetCapabilityCompatibility> matrix() {
        List<TargetCapabilityCompatibility> result = new ArrayList<>();
        for(IntegrationTechnologyDescriptor descriptor : IntegrationTechnologyCatalog.descriptors()) {
            for(HydraCapabilityKind capability : HydraCapabilityKind.values()) {
                result.add(compatibility(descriptor, capability));
            }
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «compatibility» correspondiente a la responsabilidad de CapabilityCompatibilityMatrixService. Entradas: descriptor: dato de entrada necesario para la operación; capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TargetCapabilityCompatibility compatibility(IntegrationTechnologyDescriptor descriptor, HydraCapabilityKind capability) {
        IntegrationCapability integrationCapability = CAPABILITY_MAPPING.get(capability);
        IntegrationSupportLevel support = integrationCapability == null ? IntegrationSupportLevel.PARTIAL : descriptor.support(integrationCapability);
        CapabilityFidelity fidelity = fidelity(support, descriptor.technology(), capability);
        IntegrationDeliveryMode mode = preferredMode(descriptor, fidelity);
        return new TargetCapabilityCompatibility(descriptor.technology(), capability, fidelity, mode,
                nativeLanguage(descriptor.technology()), generatedArtifacts(descriptor.technology(), capability), limitations(support, descriptor, capability));
    }

    /** Descripción: Ejecuta la operación «fidelity» correspondiente a la responsabilidad de CapabilityCompatibilityMatrixService. Entradas: support: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilityFidelity fidelity(IntegrationSupportLevel support, IntegrationTechnology technology, HydraCapabilityKind capability) {
        if(technology == IntegrationTechnology.PLANTUML) return CapabilityFidelity.PROJECTION_ONLY;
        if(support == IntegrationSupportLevel.UNSUPPORTED) return CapabilityFidelity.UNSUPPORTED;
        if(support == IntegrationSupportLevel.EXTERNAL) return CapabilityFidelity.PARTIAL;
        if(support == IntegrationSupportLevel.PARTIAL) return CapabilityFidelity.SEMANTIC;
        if(capability == HydraCapabilityKind.METAMODEL || capability == HydraCapabilityKind.VALIDATIONS || capability == HydraCapabilityKind.QUICK_FIXES) return CapabilityFidelity.SEMANTIC;
        return CapabilityFidelity.EXACT;
    }

    /** Descripción: Ejecuta la operación «preferredMode» correspondiente a la responsabilidad de CapabilityCompatibilityMatrixService. Entradas: descriptor: dato de entrada necesario para la operación; fidelity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDeliveryMode preferredMode(IntegrationTechnologyDescriptor descriptor, CapabilityFidelity fidelity) {
        if(fidelity == CapabilityFidelity.PROJECTION_ONLY && descriptor.deliveryModes().contains(IntegrationDeliveryMode.PROJECTION_ONLY)) return IntegrationDeliveryMode.PROJECTION_ONLY;
        if(descriptor.deliveryModes().contains(IntegrationDeliveryMode.GENERATED_MODULE)) return IntegrationDeliveryMode.GENERATED_MODULE;
        if(descriptor.deliveryModes().contains(IntegrationDeliveryMode.EMBEDDED)) return IntegrationDeliveryMode.EMBEDDED;
        if(descriptor.deliveryModes().contains(IntegrationDeliveryMode.HYBRID)) return IntegrationDeliveryMode.HYBRID;
        return descriptor.deliveryModes().stream().findFirst().orElse(IntegrationDeliveryMode.REMOTE);
    }

    /** Descripción: Ejecuta la operación «nativeLanguage» correspondiente a la responsabilidad de CapabilityCompatibilityMatrixService. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String nativeLanguage(IntegrationTechnology technology) {
        return switch(technology) {
            case EMF, SIRIUS_DESKTOP, XTEXT, ECLIPSE_MODELING_DISTRIBUTION -> "Java / Eclipse artifacts";
            case LANGIUM -> "TypeScript";
            case ANTLR -> "Java, TypeScript or provider-selected ANTLR target";
            case BESSER_SDK -> "Python";
            case BESSER_WEB_MODELING_EDITOR -> "TypeScript / web resources";
            case ECLIPSE_GLSP -> "TypeScript client + Java or TypeScript server";
            case VSCODE_EXTENSION -> "TypeScript / VS Code extension + optional LSP/GLSP services";
            case MXGRAPH_DRAWIO -> "JavaScript / mxGraph-compatible runtime + diagrams.net plug-in";
            case PLANTUML -> "PlantUML text";
        };
    }

    /** Descripción: Crea o prepara el resultado de «generatedArtifacts» dentro de CapabilityCompatibilityMatrixService. Entradas: technology: dato de entrada necesario para la operación; capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> generatedArtifacts(IntegrationTechnology technology, HydraCapabilityKind capability) {
        if(technology == IntegrationTechnology.MXGRAPH_DRAWIO) {
            return switch(capability) {
                case VISUAL_REPRESENTATION -> List.of("mxGraph mapping resolver", "draw.io plug-in palettes");
                case PALETTE -> List.of("representation-specific mxGraph palette entries", "draw.io custom libraries");
                case FORMS -> List.of("portable HYDRA form descriptors", "host form bridge");
                case VALIDATIONS -> List.of("validation bridge", "canonical validation document");
                case QUICK_FIXES, ACTIONS, OPERATIONS -> List.of("safe host action bridge", "canonical behavior documents");
                case EVENT_DEFINITIONS, EVENT_BINDINGS, EVENT_FLOW -> List.of("HYDRA event bridge", "canonical event documents");
                case GAMIFICATION -> List.of("gamification bridge", "canonical gamification documents");
                default -> List.of("mxGraph runtime adapter", "draw.io injectable package");
            };
        }
        if(technology == IntegrationTechnology.ECLIPSE_GLSP) {
            return switch(capability) {
                case METAMODEL -> List.of("source-model adapter", "model state");
                case VISUAL_REPRESENTATION -> List.of("GModel factory", "diagram element views");
                case PALETTE -> List.of("type hints", "tool palette contributions");
                case VALIDATIONS -> List.of("ModelValidator", "markers");
                case QUICK_FIXES, ACTIONS, OPERATIONS -> List.of("action and operation handlers");
                case EVENT_DEFINITIONS, EVENT_BINDINGS, EVENT_FLOW -> List.of("GLSP action bridge", "HYDRA runtime adapter");
                case GAMIFICATION -> List.of("runtime adapter", "optional UI contribution");
                default -> List.of("diagram-specific client/server module");
            };
        }
        return List.of(capability.name().toLowerCase() + " adapter");
    }

    /** Descripción: Ejecuta la operación «limitations» correspondiente a la responsabilidad de CapabilityCompatibilityMatrixService. Entradas: support: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> limitations(IntegrationSupportLevel support, IntegrationTechnologyDescriptor descriptor, HydraCapabilityKind capability) {
        List<String> result = new ArrayList<>();
        if(support == IntegrationSupportLevel.PARTIAL) result.add("Requires a target-specific adapter or generated bridge.");
        if(support == IntegrationSupportLevel.EXTERNAL) result.add("Executes through an external HYDRA runtime or sidecar.");
        if(support == IntegrationSupportLevel.UNSUPPORTED) result.add("No executable translation is currently declared.");
        if(capability == HydraCapabilityKind.EVENT_FLOW || capability == HydraCapabilityKind.GAMIFICATION) result.add("External tools may use embedded, hybrid or remote runtime delivery.");
        result.addAll(descriptor.notes());
        return List.copyOf(result);
    }

    /** Descripción: Crea o prepara el resultado de «createMapping» dentro de CapabilityCompatibilityMatrixService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<HydraCapabilityKind, IntegrationCapability> createMapping() {
        Map<HydraCapabilityKind, IntegrationCapability> result = new EnumMap<>(HydraCapabilityKind.class);
        result.put(HydraCapabilityKind.METAMODEL, IntegrationCapability.MODEL_READ);
        result.put(HydraCapabilityKind.GRAMMAR, IntegrationCapability.CODE_GENERATION);
        result.put(HydraCapabilityKind.TEXTUAL_EDITOR_DEFINITION, IntegrationCapability.UI_CONTRIBUTION);
        result.put(HydraCapabilityKind.TEXTUAL_SYNTAX_STYLE, IntegrationCapability.UI_CONTRIBUTION);
        result.put(HydraCapabilityKind.VISUAL_REPRESENTATION, IntegrationCapability.NOTATION_CONTRIBUTION);
        result.put(HydraCapabilityKind.PALETTE, IntegrationCapability.PALETTE_CONTRIBUTION);
        result.put(HydraCapabilityKind.FORMS, IntegrationCapability.UI_CONTRIBUTION);
        result.put(HydraCapabilityKind.EXPRESSIONS, IntegrationCapability.CODE_GENERATION);
        result.put(HydraCapabilityKind.VALIDATIONS, IntegrationCapability.VALIDATION_BRIDGE);
        result.put(HydraCapabilityKind.QUICK_FIXES, IntegrationCapability.QUICK_FIX_BRIDGE);
        result.put(HydraCapabilityKind.ACTIONS, IntegrationCapability.OPERATION_BRIDGE);
        result.put(HydraCapabilityKind.OPERATIONS, IntegrationCapability.OPERATION_BRIDGE);
        result.put(HydraCapabilityKind.EVENT_DEFINITIONS, IntegrationCapability.EVENT_PUBLISH);
        result.put(HydraCapabilityKind.EVENT_BINDINGS, IntegrationCapability.EVENT_CONSUME);
        result.put(HydraCapabilityKind.EVENT_FLOW, IntegrationCapability.EVENT_FLOW_RUNTIME);
        result.put(HydraCapabilityKind.GAMIFICATION, IntegrationCapability.GAMIFICATION_RUNTIME);
        result.put(HydraCapabilityKind.ASSETS, IntegrationCapability.UI_CONTRIBUTION);
        result.put(HydraCapabilityKind.PERSISTENCE, IntegrationCapability.MODEL_WRITE);
        result.put(HydraCapabilityKind.INTEGRATION_PROFILE, IntegrationCapability.PROJECT_EXPORT);
        return Map.copyOf(result);
    }
}

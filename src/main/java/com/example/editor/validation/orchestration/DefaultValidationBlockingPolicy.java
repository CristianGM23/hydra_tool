package com.example.editor.validation.orchestration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.validation.ValidationSeverity;

/** Política configurable que mantiene separadas severidad y capacidad de bloqueo. */
@Component
public class DefaultValidationBlockingPolicy implements ValidationBlockingPolicy {
    private final ValidationOrchestrationProperties properties;

    /** Descripción: Inicializa una instancia de DefaultValidationBlockingPolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public DefaultValidationBlockingPolicy(ValidationOrchestrationProperties properties) { this.properties = properties; }

    @Override
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de DefaultValidationBlockingPolicy. Entradas: report: dato de entrada necesario para la operación; gate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationBlockDecision evaluate(com.example.editor.validation.runtime.ModelValidationReport report, ValidationGate gate) {
        if(gate == null || gate == ValidationGate.NONE) return ValidationBlockDecision.allowed(ValidationGate.NONE);
        List<String> blocking = new ArrayList<>();
        for(ModelValidationDiagnostic diagnostic : report.diagnostics()) {
            if(explicitlyBlocks(diagnostic, gate) || defaultsToBlocking(diagnostic, gate)) blocking.add(diagnostic.id());
        }
        if(blocking.isEmpty()) return ValidationBlockDecision.allowed(gate);
        return new ValidationBlockDecision(gate, true, blocking, "Validation policy blocks " + gate.name().toLowerCase(Locale.ROOT));
    }

    /** Descripción: Ejecuta la operación «explicitlyBlocks» correspondiente a la responsabilidad de DefaultValidationBlockingPolicy. Entradas: diagnostic: dato de entrada necesario para la operación; gate: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean explicitlyBlocks(ModelValidationDiagnostic diagnostic, ValidationGate gate) {
        String configured = diagnostic.details().getOrDefault("blockingOperations", "");
        return Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .anyMatch(value -> value.equalsIgnoreCase(gate.name()));
    }

    /** Descripción: Ejecuta la operación «defaultsToBlocking» correspondiente a la responsabilidad de DefaultValidationBlockingPolicy. Entradas: diagnostic: dato de entrada necesario para la operación; gate: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean defaultsToBlocking(ModelValidationDiagnostic diagnostic, ValidationGate gate) {
        if(diagnostic.severity() != ValidationSeverity.ERROR) return false;
        return switch(gate) {
            case SAVE -> properties.isBlockSaveOnErrors();
            case EXPORT -> properties.isBlockExportOnErrors();
            case NONE -> false;
        };
    }
}

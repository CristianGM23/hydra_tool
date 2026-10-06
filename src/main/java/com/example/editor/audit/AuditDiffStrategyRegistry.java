package com.example.editor.audit;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class AuditDiffStrategyRegistry {
    private final List<AuditDiffStrategy> strategies;
    private final AuditRedactionRegistry redaction;

    /** Descripción: Inicializa una instancia de AuditDiffStrategyRegistry con sus dependencias y datos de entrada. Entradas: strategies: dato de entrada necesario para la operación; redaction: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditDiffStrategyRegistry(List<AuditDiffStrategy> strategies, AuditRedactionRegistry redaction) {
        this.strategies = strategies == null ? List.of() : strategies.stream().sorted(Comparator.comparingInt(AuditDiffStrategy::order)).toList();
        this.redaction = redaction;
        if(this.strategies.isEmpty()) throw new IllegalStateException("At least one AuditDiffStrategy is required");
    }

    /** Descripción: Ejecuta la operación «diff» correspondiente a la responsabilidad de AuditDiffStrategyRegistry. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditDiffStrategy.AuditDiffResult diff(AuditCapturedEvent event) {
        AuditDiffStrategy.AuditDiffResult result = strategies.stream().filter(strategy -> strategy.supports(event)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No audit diff strategy supports " + event.activityType())).diff(event);
        var protectedChanges = result.changes().stream().map(change -> redaction.sensitive(change.path())
                ? new AuditChange(change.path(), change.kind(), AuditRedactionRegistry.REDACTED, AuditRedactionRegistry.REDACTED) : change).toList();
        return new AuditDiffStrategy.AuditDiffResult(protectedChanges, result.truncated());
    }
}

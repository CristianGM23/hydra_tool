package com.example.editor.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

/**
 * Clasifica el valor de conservación de cada entrada. La navegación y las vistas
 * se conservan poco tiempo; los cambios persistentes tienen la retención máxima.
 */
public class AuditRetentionPolicy {
    private static final Set<String> ROUTINE_ACTIONS = Set.of(AuditActions.VALIDATE, AuditActions.EXPORT, AuditActions.EXECUTE);
    private final AuditLogProperties properties;

    /** Descripción: Inicializa una instancia de AuditRetentionPolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public AuditRetentionPolicy(AuditLogProperties properties) { this.properties = properties; }

    /** Descripción: Evalúa la condición «isExpired» sin modificar el estado externo. Entradas: entry: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isExpired(AuditEntry entry, Instant now) {
        if(entry == null || entry.timestamp() == null || now == null) return false;
        return entry.timestamp().isBefore(now.minus(retentionDays(entry), ChronoUnit.DAYS));
    }

    /** Descripción: Ejecuta la operación «retentionDays» correspondiente a la responsabilidad de AuditRetentionPolicy. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public int retentionDays(AuditEntry entry) {
        if(entry == null) return properties.getRetentionDays();
        if(entry.importance() == AuditImportance.SECURITY || entry.importance() == AuditImportance.WARNING) return properties.getRetentionDays();
        if(isNavigation(entry)) return properties.getNavigationRetentionDays();
        if(AuditActions.OPEN.equals(entry.action())) return properties.getOpenRetentionDays();
        if(ROUTINE_ACTIONS.contains(entry.action())) return properties.getRoutineRetentionDays();
        return properties.getRetentionDays();
    }

    /** Menor valor = primera categoría sacrificable cuando se alcanza el límite de disco. */
    /** Descripción: Ejecuta la operación «evictionPriority» correspondiente a la responsabilidad de AuditRetentionPolicy. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public int evictionPriority(AuditEntry entry) {
        if(entry == null) return 0;
        if(entry.importance() == AuditImportance.SECURITY || entry.importance() == AuditImportance.WARNING) return 3;
        if(isNavigation(entry)) return 0;
        if(AuditActions.OPEN.equals(entry.action())) return 1;
        if(ROUTINE_ACTIONS.contains(entry.action())) return 2;
        return 3;
    }

    /** Descripción: Evalúa la condición «isNavigation» sin modificar el estado externo. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isNavigation(AuditEntry entry) {
        if(entry == null) return false;
        String type = entry.activityType() == null ? "" : entry.activityType().toLowerCase(Locale.ROOT);
        return AuditActions.NAVIGATE.equals(entry.action()) || AuditActions.CLOSE.equals(entry.action())
                || type.startsWith("navigation.") || type.startsWith("ui.");
    }
}

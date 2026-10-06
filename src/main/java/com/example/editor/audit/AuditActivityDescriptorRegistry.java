package com.example.editor.audit;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Registry abierto: no hay switch central de módulos. */
@Component
public class AuditActivityDescriptorRegistry {
    private static final int MAXIMUM_DESCRIPTORS = 4_096;
    private final Map<String, AuditActivityDescriptor> descriptors = new ConcurrentHashMap<>();

    /** Descripción: Inicializa una instancia de AuditActivityDescriptorRegistry con sus dependencias y datos de entrada. Entradas: providers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditActivityDescriptorRegistry(List<AuditActivityDescriptorProvider> providers) {
        if(providers != null) providers.forEach(provider -> registerAll(provider.descriptors()));
    }

    /** Descripción: Ejecuta la operación «register» correspondiente a la responsabilidad de AuditActivityDescriptorRegistry. Entradas: descriptor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void register(AuditActivityDescriptor descriptor) {
        if(descriptor == null) return;
        if(!descriptors.containsKey(descriptor.activityType()) && descriptors.size() >= MAXIMUM_DESCRIPTORS) {
            throw new IllegalStateException("Audit activity descriptor registry reached its configured hard limit of " + MAXIMUM_DESCRIPTORS);
        }
        AuditActivityDescriptor previous = descriptors.putIfAbsent(descriptor.activityType(), descriptor);
        if(previous != null && !previous.equals(descriptor)) {
            throw new IllegalStateException("Duplicate audit activity descriptor: " + descriptor.activityType());
        }
    }
    /** Descripción: Ejecuta la operación «registerAll» correspondiente a la responsabilidad de AuditActivityDescriptorRegistry. Entradas: values: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void registerAll(Collection<AuditActivityDescriptor> values) { if(values != null) values.forEach(this::register); }
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de AuditActivityDescriptorRegistry. Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditActivityDescriptor resolve(AuditEntry entry) {
        AuditActivityDescriptor descriptor = descriptors.get(entry.activityType());
        if(descriptor != null) return descriptor;
        return new AuditActivityDescriptor(entry.activityType(), entry.moduleId(), entry.action(),
                fallbackTitle(entry.action(), false), fallbackTitle(entry.action(), true), humanize(entry.moduleId()), humanize(entry.moduleId()), entry.importance());
    }
    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de AuditActivityDescriptorRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int size() { return descriptors.size(); }
    /** Descripción: Ejecuta la operación «maximumSize» correspondiente a la responsabilidad de AuditActivityDescriptorRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int maximumSize() { return MAXIMUM_DESCRIPTORS; }
    /** Descripción: Ejecuta la operación «all» correspondiente a la responsabilidad de AuditActivityDescriptorRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<AuditActivityDescriptor> all() {
        return descriptors.values().stream().sorted(Comparator.comparing(AuditActivityDescriptor::activityType)).toList();
    }
    /** Descripción: Ejecuta la operación «fallbackTitle» correspondiente a la responsabilidad de AuditActivityDescriptorRegistry. Entradas: action: dato de entrada necesario para la operación; spanish: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fallbackTitle(String action, boolean spanish) {
        String normalized = action == null ? AuditActions.UPDATE : action;
        if(spanish) return switch(normalized) {
            case AuditActions.CREATE -> "Se creó {resource}";
            case AuditActions.DELETE -> "Se eliminó {resource}";
            case AuditActions.IMPORT -> "Se importó {resource}";
            case AuditActions.EXPORT -> "Se exportó {resource}";
            case AuditActions.VALIDATE -> "Se validó {resource}";
            case AuditActions.EXECUTE -> "Se ejecutó {resource}";
            case AuditActions.APPLY -> "Se aplicó {resource}";
            case AuditActions.ROLLBACK -> "Se revirtió {resource}";
            case AuditActions.OPEN -> "Se abrió {resource}";
            case AuditActions.CLOSE -> "Se cerró {resource}";
            case AuditActions.SAVE -> "Se guardó {resource}";
            case AuditActions.NAVIGATE -> "Se abrió {resource}";
            case AuditActions.CONFIGURE -> "Se configuró {resource}";
            case AuditActions.UNDO -> "Se deshizo {resource}";
            case AuditActions.REDO -> "Se rehízo {resource}";
            default -> "Se modificó {resource}";
        };
        return switch(normalized) {
            case AuditActions.CREATE -> "Created {resource}";
            case AuditActions.DELETE -> "Deleted {resource}";
            case AuditActions.IMPORT -> "Imported {resource}";
            case AuditActions.EXPORT -> "Exported {resource}";
            case AuditActions.VALIDATE -> "Validated {resource}";
            case AuditActions.EXECUTE -> "Executed {resource}";
            case AuditActions.APPLY -> "Applied {resource}";
            case AuditActions.ROLLBACK -> "Rolled back {resource}";
            case AuditActions.OPEN -> "Opened {resource}";
            case AuditActions.CLOSE -> "Closed {resource}";
            case AuditActions.SAVE -> "Saved {resource}";
            case AuditActions.NAVIGATE -> "Opened {resource}";
            case AuditActions.CONFIGURE -> "Configured {resource}";
            case AuditActions.UNDO -> "Undid {resource}";
            case AuditActions.REDO -> "Redid {resource}";
            default -> "Updated {resource}";
        };
    }
    /** Descripción: Ejecuta la operación «humanize» correspondiente a la responsabilidad de AuditActivityDescriptorRegistry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String humanize(String value) {
        if(value == null || value.isBlank()) return "General";
        String text = value.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        return text.isEmpty() ? "General" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

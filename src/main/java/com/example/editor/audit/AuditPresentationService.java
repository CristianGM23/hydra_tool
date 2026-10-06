package com.example.editor.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Presentación localizada desacoplada del registro canónico persistido. */
@Component
public class AuditPresentationService {
    private static final Map<String, String> ES_FIELDS = spanishFields();
    private final AuditActivityDescriptorRegistry descriptors;
    private final AuditLogProperties properties;

    /** Descripción: Inicializa una instancia de AuditPresentationService con sus dependencias y datos de entrada. Entradas: descriptors: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public AuditPresentationService(AuditActivityDescriptorRegistry descriptors, AuditLogProperties properties) {
        this.descriptors = descriptors;
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «present» correspondiente a la responsabilidad de AuditPresentationService. Entradas: entry: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditPresentedEntry present(AuditEntry entry, AuditLanguage language) {
        AuditLanguage effective = language == null ? AuditLanguage.EN : language;
        AuditActivityDescriptor descriptor = descriptors.resolve(entry);
        String displayResource = resourceName(entry, effective);
        String resource = quote(displayResource, effective);
        String template = effective == AuditLanguage.ES ? descriptor.titleSpanish() : descriptor.titleEnglish();
        String title = template.replace("{resource}", resource);
        var changes = entry.changes().stream().map(change -> present(change, effective)).toList();
        String summary = summary(entry, changes.size(), effective);
        return new AuditPresentedEntry(entry.eventId(), entry.timestamp(), title, summary, entry.moduleId(),
                moduleName(entry.moduleId(), descriptor, effective), entry.action(),
                actionLabel(entry.action(), effective), entry.importance(), entry.result(), entry.resource().resourceType(),
                entry.resource().resourceId(), displayResource,
                entry.actor().actorId(), actorName(entry.actor(), effective), changes, entry.changesTruncated(), entry.correlationId(), entry.metadata());
    }

    /** Descripción: Ejecuta la operación «moduleName» correspondiente a la responsabilidad de AuditPresentationService. Entradas: descriptor: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String moduleName(AuditActivityDescriptor descriptor, AuditLanguage language) {
        return moduleName(descriptor.moduleId(), descriptor, language);
    }

    /** Descripción: Ejecuta la operación «moduleName» correspondiente a la responsabilidad de AuditPresentationService. Entradas: moduleId: identificador que delimita el recurso objetivo; descriptor: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String moduleName(String moduleId, AuditActivityDescriptor descriptor, AuditLanguage language) {
        if(language != AuditLanguage.ES) return descriptor.moduleEnglish();
        if(!descriptor.moduleId().equals("canonical") && descriptor.moduleSpanish() != null && !descriptor.moduleSpanish().equals(descriptor.moduleEnglish())) return descriptor.moduleSpanish();
        return switch(moduleId == null ? "" : moduleId) {
            case "metamodel" -> "Metamodelo"; case "validation" -> "Validación"; case "event" -> "Eventos";
            case "gamification" -> "Gamificación"; case "integration" -> "Integraciones"; case "editor" -> "Editor";
            case "operation" -> "Operaciones"; case "audit" -> "Actividad"; case "navigation" -> "Navegación";
            case "settings" -> "Ajustes"; case "project" -> "Proyecto"; case "model" -> "Modelo"; case "event-flow" -> "Flujo de eventos";
            case "import" -> "Importación"; case "distribution" -> "Distribución"; case "compatibility" -> "Compatibilidad"; default -> descriptor.moduleSpanish();
        };
    }


    /** Descripción: Ejecuta la operación «resourceName» correspondiente a la responsabilidad de AuditPresentationService. Entradas: entry: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resourceName(AuditEntry entry, AuditLanguage language) {
        if("navigation.module.changed".equals(entry.activityType())) return moduleResourceName(entry.resource().resourceId(), language);
        return nonBlank(entry.resource().displayName(), entry.resource().resourceId());
    }

    /** Descripción: Ejecuta la operación «moduleResourceName» correspondiente a la responsabilidad de AuditPresentationService. Entradas: moduleId: identificador que delimita el recurso objetivo; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String moduleResourceName(String moduleId, AuditLanguage language) {
        String id = moduleId == null ? "" : moduleId.trim().toUpperCase(Locale.ROOT);
        if(language == AuditLanguage.ES) return switch(id) {
            case "HOME" -> "Inicio"; case "METAMODEL" -> "Metamodelo"; case "MODEL" -> "Modelo";
            case "VALIDATION" -> "Validaciones"; case "EVENT", "EVENTS" -> "Eventos"; case "EVENT_FLOW", "EVENTFLOW" -> "Flujo de eventos";
            case "GAMIFICATION" -> "Gamificación"; case "EDITOR", "EDITOR_DEFINITION", "EDITORS" -> "Definición de editor";
            case "INTEGRATION", "INTEGRATIONS" -> "Integraciones"; case "IMPORT", "IMPORT_CENTER" -> "Importación";
            case "DISTRIBUTION" -> "Distribución"; case "COMPATIBILITY" -> "Compatibilidad"; case "CAPABILITY", "CAPABILITIES" -> "Capacidades";
            case "ROUNDTRIP", "ROUND_TRIP" -> "Sincronización round-trip"; case "BLOCKLY" -> "Blockly"; default -> humanizeIdentifier(moduleId);
        };
        return humanizeIdentifier(moduleId);
    }

    /** Descripción: Ejecuta la operación «humanizeIdentifier» correspondiente a la responsabilidad de AuditPresentationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String humanizeIdentifier(String value) {
        String text = value == null ? "" : value.replace('_', ' ').replace('-', ' ').replace('.', ' ').trim().toLowerCase(Locale.ROOT);
        return text.isBlank() ? "General" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    /** Descripción: Ejecuta la operación «present» correspondiente a la responsabilidad de AuditPresentationService. Entradas: change: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditPresentedChange present(AuditChange change, AuditLanguage language) {
        return new AuditPresentedChange(fieldLabel(change.path(), language), change.path(), change.kind(),
                value(change.beforeValue(), language), value(change.afterValue(), language));
    }

    /** Descripción: Ejecuta la operación «summary» correspondiente a la responsabilidad de AuditPresentationService. Entradas: entry: dato de entrada necesario para la operación; count: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String summary(AuditEntry entry, int count, AuditLanguage language) {
        if(entry.result() == AuditResult.FAILED) return language == AuditLanguage.ES ? "La acción terminó con errores." : "The action failed.";
        if(entry.result() == AuditResult.PARTIAL) return language == AuditLanguage.ES ? "La acción terminó parcialmente." : "The action completed partially.";
        if(count == 0) {
            if(language == AuditLanguage.ES) return switch(entry.action()) {
                case AuditActions.CREATE -> "El recurso se creó correctamente."; case AuditActions.DELETE -> "El recurso se eliminó correctamente.";
                case AuditActions.IMPORT -> "La importación terminó correctamente."; case AuditActions.EXPORT -> "La exportación terminó correctamente.";
                case AuditActions.VALIDATE -> "La validación terminó correctamente."; case AuditActions.EXECUTE -> "La ejecución terminó correctamente.";
                case AuditActions.APPLY -> "La acción se aplicó correctamente."; case AuditActions.OPEN, AuditActions.NAVIGATE -> "La navegación se realizó correctamente.";
                case AuditActions.SAVE -> "El guardado terminó correctamente."; case AuditActions.CONFIGURE -> "La configuración se aplicó correctamente.";
                case AuditActions.UNDO -> "El cambio se deshizo correctamente."; case AuditActions.REDO -> "El cambio se rehízo correctamente.";
                case AuditActions.CLOSE -> "La vista se cerró correctamente."; default -> "La acción terminó correctamente.";
            };
            return switch(entry.action()) {
                case AuditActions.CREATE -> "The resource was created successfully."; case AuditActions.DELETE -> "The resource was deleted successfully.";
                case AuditActions.IMPORT -> "The import completed successfully."; case AuditActions.EXPORT -> "The export completed successfully.";
                case AuditActions.VALIDATE -> "The validation completed successfully."; case AuditActions.EXECUTE -> "The execution completed successfully.";
                case AuditActions.APPLY -> "The action was applied successfully."; case AuditActions.OPEN, AuditActions.NAVIGATE -> "Navigation completed successfully.";
                case AuditActions.SAVE -> "The save completed successfully."; case AuditActions.CONFIGURE -> "The configuration was applied successfully.";
                case AuditActions.UNDO -> "The change was undone successfully."; case AuditActions.REDO -> "The change was redone successfully.";
                case AuditActions.CLOSE -> "The view was closed successfully."; default -> "The action completed successfully.";
            };
        }
        String suffix = entry.changesTruncated()
                ? (language == AuditLanguage.ES ? "; hay más cambios no mostrados" : "; additional changes are not shown") : "";
        if(language == AuditLanguage.ES) return count + (count == 1 ? " cambio" : " cambios") + suffix + ".";
        return count + (count == 1 ? " change" : " changes") + suffix + ".";
    }

    /** Descripción: Ejecuta la operación «actionLabel» correspondiente a la responsabilidad de AuditPresentationService. Entradas: action: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String actionLabel(String action, AuditLanguage language) {
        String normalized = action == null ? AuditActions.UPDATE : action.toUpperCase(Locale.ROOT);
        if(language == AuditLanguage.ES) return switch(normalized) {
            case AuditActions.CREATE -> "Creación";
            case AuditActions.DELETE -> "Eliminación";
            case AuditActions.IMPORT -> "Importación";
            case AuditActions.EXPORT -> "Exportación";
            case AuditActions.VALIDATE -> "Validación";
            case AuditActions.OPEN -> "Apertura";
            case AuditActions.SAVE -> "Guardado";
            case AuditActions.EXECUTE -> "Ejecución";
            case AuditActions.APPLY -> "Aplicación";
            case AuditActions.ROLLBACK -> "Reversión";
            case AuditActions.NAVIGATE -> "Navegación";
            case AuditActions.CONFIGURE -> "Configuración";
            case AuditActions.UNDO -> "Deshacer";
            case AuditActions.REDO -> "Rehacer";
            case AuditActions.CLOSE -> "Cierre";
            default -> "Modificación";
        };
        return switch(normalized) {
            case AuditActions.CREATE -> "Created";
            case AuditActions.DELETE -> "Deleted";
            case AuditActions.IMPORT -> "Imported";
            case AuditActions.EXPORT -> "Exported";
            case AuditActions.VALIDATE -> "Validated";
            case AuditActions.OPEN -> "Opened";
            case AuditActions.SAVE -> "Saved";
            case AuditActions.EXECUTE -> "Executed";
            case AuditActions.APPLY -> "Applied";
            case AuditActions.ROLLBACK -> "Rolled back";
            case AuditActions.NAVIGATE -> "Navigated";
            case AuditActions.CONFIGURE -> "Configured";
            case AuditActions.UNDO -> "Undone";
            case AuditActions.REDO -> "Redone";
            case AuditActions.CLOSE -> "Closed";
            default -> "Updated";
        };
    }

    /** Descripción: Ejecuta la operación «fieldLabel» correspondiente a la responsabilidad de AuditPresentationService. Entradas: path: ruta o ubicación utilizada por la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fieldLabel(String path, AuditLanguage language) {
        if(path == null || path.isBlank() || "$".equals(path)) return language == AuditLanguage.ES ? "Valor" : "Value";
        String leaf = path;
        int dot = leaf.lastIndexOf('.');
        if(dot >= 0) leaf = leaf.substring(dot + 1);
        leaf = leaf.replaceAll("\\[\\d+\\]$", "");
        String words = leaf.replace('_', ' ').replace('-', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
        if(words.isBlank()) words = path;
        if(language == AuditLanguage.ES) {
            String translated = ES_FIELDS.get(words.toLowerCase(Locale.ROOT));
            if(translated != null) return translated;
        }
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de AuditPresentationService. Entradas: value: valor de entrada que debe procesarse; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String value(Object value, AuditLanguage language) {
        if(value == null) return language == AuditLanguage.ES ? "(sin valor)" : "(no value)";
        String text = String.valueOf(value);
        int max = properties.getMaximumValueLength();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** Descripción: Ejecuta la operación «actorName» correspondiente a la responsabilidad de AuditPresentationService. Entradas: actor: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String actorName(AuditActor actor, AuditLanguage language) {
        if(language == AuditLanguage.ES && "local-user".equals(actor.actorId())) return "Usuario local";
        return actor.displayName();
    }
    /** Descripción: Ejecuta la operación «quote» correspondiente a la responsabilidad de AuditPresentationService. Entradas: value: valor de entrada que debe procesarse; language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String quote(String value, AuditLanguage language) {
        return language == AuditLanguage.ES ? "«" + value + "»" : "“" + value + "”";
    }
    /** Descripción: Ejecuta la operación «nonBlank» correspondiente a la responsabilidad de AuditPresentationService. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String nonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    /** Descripción: Ejecuta la operación «spanishFields» correspondiente a la responsabilidad de AuditPresentationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> spanishFields() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", "Nombre"); values.put("title", "Título"); values.put("description", "Descripción");
        values.put("enabled", "Activado"); values.put("severity", "Severidad"); values.put("type", "Tipo");
        values.put("status", "Estado"); values.put("label", "Etiqueta"); values.put("value", "Valor");
        values.put("priority", "Prioridad"); values.put("progress", "Progreso"); values.put("expression", "Expresión");
        values.put("module id", "Capa"); values.put("metamodel id", "Metamodelo"); values.put("language", "Idioma");
        values.put("font family", "Tipografía"); values.put("responsive sizing", "Tamaño adaptable");
        values.put("base font size", "Tamaño general"); values.put("window font size", "Tamaño de ventanas");
        values.put("tab font size", "Tamaño de pestañas"); values.put("form font size", "Tamaño de formularios");
        values.put("diagram font size", "Tamaño de diagramas"); values.put("tooltip font size", "Tamaño de ayudas");
        return Map.copyOf(values);
    }
}

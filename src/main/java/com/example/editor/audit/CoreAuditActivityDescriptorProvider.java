package com.example.editor.audit;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CoreAuditActivityDescriptorProvider implements AuditActivityDescriptorProvider {
    @Override
    /** Descripción: Ejecuta la operación «descriptors» correspondiente a la responsabilidad de CoreAuditActivityDescriptorProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<AuditActivityDescriptor> descriptors() {
        return List.of(
            descriptor("canonical.document.created", "canonical", AuditActions.CREATE, "Created {resource}", "Se creó {resource}"),
            descriptor("canonical.document.updated", "canonical", AuditActions.UPDATE, "Updated {resource}", "Se modificó {resource}"),
            descriptor("canonical.document.deleted", "canonical", AuditActions.DELETE, "Deleted {resource}", "Se eliminó {resource}"),
            descriptor("metamodel.created", "metamodel", AuditActions.CREATE, "Created metamodel {resource}", "Se creó el metamodelo {resource}"),
            descriptor("metamodel.updated", "metamodel", AuditActions.UPDATE, "Updated metamodel {resource}", "Se modificó el metamodelo {resource}"),
            descriptor("metamodel.deleted", "metamodel", AuditActions.DELETE, "Deleted metamodel {resource}", "Se eliminó el metamodelo {resource}"),
            descriptor("grammar.definition.created", "grammar", AuditActions.CREATE, "Created grammar {resource}", "Se creó la gramática {resource}"),
            descriptor("grammar.definition.updated", "grammar", AuditActions.UPDATE, "Updated grammar {resource}", "Se modificó la gramática {resource}"),
            descriptor("grammar.definition.deleted", "grammar", AuditActions.DELETE, "Deleted grammar {resource}", "Se eliminó la gramática {resource}"),
            descriptor("textual.syntax.style.created", "textual", AuditActions.CREATE, "Created syntax style {resource}", "Se creó el estilo de sintaxis {resource}"),
            descriptor("textual.syntax.style.updated", "textual", AuditActions.UPDATE, "Updated syntax style {resource}", "Se modificó el estilo de sintaxis {resource}"),
            descriptor("textual.syntax.style.deleted", "textual", AuditActions.DELETE, "Deleted syntax style {resource}", "Se eliminó el estilo de sintaxis {resource}"),
            descriptor("textual.editor.definition.created", "textual", AuditActions.CREATE, "Created textual editor context {resource}", "Se creó el contexto del editor textual {resource}"),
            descriptor("textual.editor.definition.updated", "textual", AuditActions.UPDATE, "Updated textual editor context {resource}", "Se modificó el contexto del editor textual {resource}"),
            descriptor("textual.editor.definition.deleted", "textual", AuditActions.DELETE, "Deleted textual editor context {resource}", "Se eliminó el contexto del editor textual {resource}"),
            descriptor("audit.exported", "audit", AuditActions.EXPORT, "Exported activity log", "Se exportó el registro de actividad"),
            descriptor("navigation.module.changed", "navigation", AuditActions.NAVIGATE, "Opened module {resource}", "Se abrió la capa {resource}"),
            descriptor("navigation.metamodel.changed", "navigation", AuditActions.NAVIGATE, "Selected metamodel {resource}", "Se seleccionó el metamodelo {resource}"),
            descriptor("ui.settings.opened", "settings", AuditActions.OPEN, "Opened application settings", "Se abrieron los ajustes de la aplicación"),
            descriptor("settings.appearance.updated", "settings", AuditActions.CONFIGURE, "Updated appearance settings", "Se modificó la apariencia de la aplicación"),
            descriptor("settings.language.updated", "settings", AuditActions.CONFIGURE, "Changed application language", "Se cambió el idioma de la aplicación")
        );
    }
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de CoreAuditActivityDescriptorProvider. Entradas: type: dato de entrada necesario para la operación; module: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación; en: dato de entrada necesario para la operación; es: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditActivityDescriptor descriptor(String type, String module, String action, String en, String es) {
        return new AuditActivityDescriptor(type, module, action, en, es, humanize(module), spanishModule(module), AuditImportance.INFO);
    }
    /** Descripción: Ejecuta la operación «humanize» correspondiente a la responsabilidad de CoreAuditActivityDescriptorProvider. Entradas: module: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String humanize(String module) { return Character.toUpperCase(module.charAt(0)) + module.substring(1); }
    /** Descripción: Ejecuta la operación «spanishModule» correspondiente a la responsabilidad de CoreAuditActivityDescriptorProvider. Entradas: module: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String spanishModule(String module) {
        return switch(module) {
            case "metamodel" -> "Metamodelo";
            case "audit" -> "Actividad";
            case "navigation" -> "Navegación";
            case "settings" -> "Ajustes";
            case "grammar" -> "Gramática";
            case "textual" -> "Editor textual";
            default -> humanize(module);
        };
    }
}

package com.example.editor.definition;
import java.util.Set;
/** Capacidades activadas por una definición de editor. */
public record EditorCapabilityManifest(Set<EditorCapability> capabilities) {
    public EditorCapabilityManifest { capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities); }
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(EditorCapability capability) { return capabilities.contains(capability); }
}

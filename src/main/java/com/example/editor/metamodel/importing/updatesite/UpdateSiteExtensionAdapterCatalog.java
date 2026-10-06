package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

/** Catálogo server-owned de adaptadores declarativos; nunca ejecuta código del update site. */
@Component
public class UpdateSiteExtensionAdapterCatalog {
    private final List<UpdateSiteExtensionAdapterDescriptor> builtInAdapters = List.of(
            adapter("custom-palettes", "Custom palette extension", List.of("palette", "tooling"),
                    List.of("palette", "group", "tool", "creationtool"), UpdateSiteSemanticKind.FORM,
                    "HYDRA palette/tool draft",
                    "Declares tool groups or creation actions that can seed a HYDRA editor palette.",
                    List.of("id", "name", "label"), List.of("class", "provider")),
            adapter("custom-representations", "Custom representation extension",
                    List.of("diagram", "representation", "viewpoint"),
                    List.of("diagram", "representation", "viewpoint", "node", "edge"),
                    UpdateSiteSemanticKind.EDITOR, "HYDRA representation or viewpoint draft",
                    "Describes visual views, diagrams, nodes or edges supplied by an Eclipse extension.",
                    List.of("id", "name", "label"), List.of("class", "provider", "factory")),
            adapter("custom-mappings", "Custom semantic mapping extension",
                    List.of("mapping", "mapper"), List.of("mapping", "nodeMapping", "edgeMapping"),
                    UpdateSiteSemanticKind.RUNTIME_EXTENSION, "HYDRA semantic-to-visual mapping draft",
                    "Connects semantic model elements with visual or runtime elements.",
                    List.of("id", "name", "semanticElement"), List.of("class", "provider")),
            adapter("custom-importers", "Custom importer extension", List.of("import", "loader", "parser"),
                    List.of("importer", "loader", "parser", "format"), UpdateSiteSemanticKind.FILE_EXTENSION,
                    "HYDRA import adapter draft",
                    "Registers a format reader or loader that turns external files into model content.",
                    List.of("id", "name", "fileExtension", "extensions"), List.of("class", "loader", "parser")),
            adapter("custom-generators", "Custom generator extension", List.of("generator", "codegen", "export"),
                    List.of("generator", "exporter", "target"), UpdateSiteSemanticKind.RUNTIME_EXTENSION,
                    "HYDRA generator/export adapter draft",
                    "Registers code generation or export behavior for a target technology.",
                    List.of("id", "name", "target"), List.of("class", "generator", "provider")),
            adapter("custom-operations", "Custom operation extension", List.of("operation", "action", "command"),
                    List.of("operation", "action", "command", "handler"), UpdateSiteSemanticKind.COMMAND,
                    "HYDRA operation/action draft",
                    "Declares an executable action that can be exposed from menus, buttons or palettes.",
                    List.of("id", "commandId", "name", "label"), List.of("class", "handler", "implementation")),
            adapter("custom-quickfixes", "Custom quick-fix extension", List.of("quickfix", "resolution", "repair"),
                    List.of("quickFix", "resolution", "repair"), UpdateSiteSemanticKind.VALIDATION,
                    "HYDRA quick-fix draft",
                    "Registers a repair that can resolve a validation problem or diagnostic.",
                    List.of("id", "name", "label"), List.of("class", "resolver", "provider")),
            adapter("custom-forms", "Custom form extension", List.of("form", "property", "inspector"),
                    List.of("form", "field", "property", "section", "page"), UpdateSiteSemanticKind.FORM,
                    "HYDRA form/property-field draft",
                    "Describes editable fields, property sections or inspectors for model elements.",
                    List.of("id", "name", "label", "property"), List.of("class", "provider"))
    );

    private final Map<String, UpdateSiteExtensionAdapterDescriptor> registeredAdapters = new ConcurrentHashMap<>();
    private final int maximumRegisteredAdapters;
    /** Descripción: Inicializa una instancia de UpdateSiteExtensionAdapterCatalog con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteExtensionAdapterCatalog(){this(128);}
    /** Descripción: Actualiza el estado gestionado por UpdateSiteExtensionAdapterCatalog mediante la operación «UpdateSiteExtensionAdapterCatalog». Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Autowired public UpdateSiteExtensionAdapterCatalog(UpdateSiteImportProperties properties){this(properties==null?128:properties.getMaxDeclarativeAdapters());}
    /** Descripción: Inicializa una instancia de UpdateSiteExtensionAdapterCatalog con sus dependencias y datos de entrada. Entradas: maximumRegisteredAdapters: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteExtensionAdapterCatalog(int maximumRegisteredAdapters){this.maximumRegisteredAdapters=Math.max(1,maximumRegisteredAdapters);}

    /** Descripción: Ejecuta la operación «descriptors» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteExtensionAdapterDescriptor> descriptors() {
        List<UpdateSiteExtensionAdapterDescriptor> result = new ArrayList<>(builtInAdapters);
        result.addAll(registeredAdapters.values());
        return result.stream().sorted(Comparator.comparing(UpdateSiteExtensionAdapterDescriptor::id)).toList();
    }

    /** Descripción: Ejecuta la operación «match» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: extensionPoint: dato de entrada necesario para la operación; elementName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<UpdateSiteExtensionAdapterDescriptor> match(String extensionPoint, String elementName) {
        return descriptors().stream().filter(adapter -> adapter.matches(extensionPoint, elementName)).findFirst();
    }

    /** Descripción: Ejecuta la operación «register» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: adapter: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void register(UpdateSiteExtensionAdapterDescriptor adapter) {
        if(adapter == null) throw new IllegalArgumentException("adapter is required");
        synchronized(registeredAdapters) {
            if(!registeredAdapters.containsKey(adapter.id()) && registeredAdapters.size() >= maximumRegisteredAdapters)
                throw new IllegalStateException("Update-site extension adapter limit reached: " + maximumRegisteredAdapters);
            registeredAdapters.put(adapter.id(), adapter);
        }
    }

    /** Descripción: Ejecuta la operación «unregister» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void unregister(String adapterId) { registeredAdapters.remove(adapterId); }
    /** Descripción: Ejecuta la operación «registeredCount» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int registeredCount(){return registeredAdapters.size();}
    /** Descripción: Ejecuta la operación «maximumRegisteredAdapters» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int maximumRegisteredAdapters(){return maximumRegisteredAdapters;}
    /** Descripción: Ejecuta la operación «builtIn» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean builtIn(String adapterId) { return builtInAdapters.stream().anyMatch(adapter -> adapter.id().equals(adapterId)); }

    /** Descripción: Ejecuta la operación «adapter» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterCatalog. Entradas: id: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación; pointTokens: dato de entrada necesario para la operación; elements: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; purpose: dato de entrada necesario para la operación; ids: dato de entrada necesario para la operación; implementations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static UpdateSiteExtensionAdapterDescriptor adapter(String id, String label,
            List<String> pointTokens, List<String> elements, UpdateSiteSemanticKind kind,
            String target, String purpose, List<String> ids, List<String> implementations) {
        return new UpdateSiteExtensionAdapterDescriptor(id, label, pointTokens, elements, kind,
                target, purpose, ids, implementations, UpdateSiteConfidence.DERIVED, true);
    }
}

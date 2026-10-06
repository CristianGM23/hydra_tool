package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Recupera semántica de plugin.xml y clases generadas sin ejecutar código Eclipse. */
@Component
public class UpdateSiteSemanticEditorRecoveryService {
    private static final Pattern EXTENSION = Pattern.compile("(?s)<extension\\s+[^>]*point=\"([^\"]+)\"[^>]*>(.*?)</extension>");
    private static final Pattern ELEMENT = Pattern.compile("<([A-Za-z_][\\w.-]*)\\b([^>]*)/?>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([A-Za-z_][\\w.-]*)=\"([^\"]*)\"");
    private final UpdateSiteExtensionAdapterCatalog adapterCatalog;
    private final UpdateSiteLocalizationIndex localizationIndex;
    private final UpdateSiteScriptSemanticRecovery scriptRecovery;

    @org.springframework.beans.factory.annotation.Autowired
    /** Descripción: Inicializa una instancia de UpdateSiteSemanticEditorRecoveryService con sus dependencias y datos de entrada. Entradas: adapterCatalog: dato de entrada necesario para la operación; localizationIndex: dato de entrada necesario para la operación; scriptRecovery: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteSemanticEditorRecoveryService(UpdateSiteExtensionAdapterCatalog adapterCatalog,
            UpdateSiteLocalizationIndex localizationIndex, UpdateSiteScriptSemanticRecovery scriptRecovery) {
        this.adapterCatalog = adapterCatalog;
        this.localizationIndex = localizationIndex;
        this.scriptRecovery = scriptRecovery;
    }

    /** Compatibility constructor for focused tests. */
    /** Descripción: Inicializa una instancia de UpdateSiteSemanticEditorRecoveryService con sus dependencias y datos de entrada. Entradas: adapterCatalog: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteSemanticEditorRecoveryService(UpdateSiteExtensionAdapterCatalog adapterCatalog) {
        this(adapterCatalog, new UpdateSiteLocalizationIndex(), new UpdateSiteScriptSemanticRecovery());
    }

    /** Descripción: Ejecuta la operación «recover» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteSemanticRecovery recover(UpdateSiteRepositoryContent repository) {
        List<UpdateSiteSemanticElementDraft> result = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        UpdateSiteLocalizationIndex.Index localization = localizationIndex.build(repository);
        repository.files().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            UpdateSiteArtifactKind kind = UpdateSiteArtifactKinds.fromPath(entry.getKey());
            if(kind == UpdateSiteArtifactKind.PLUGIN_XML || kind == UpdateSiteArtifactKind.FRAGMENT_XML) {
                inspectPluginXml(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8), localization, result, diagnostics);
            } else if(kind == UpdateSiteArtifactKind.JAVA_CLASS) {
                inspectGeneratedClass(entry.getKey(), result);
            } else if(kind == UpdateSiteArtifactKind.EVL || kind == UpdateSiteArtifactKind.OCL || kind == UpdateSiteArtifactKind.EOL) {
                result.addAll(scriptRecovery.recover(entry.getKey(), kind, entry.getValue()));
            }
        });
        Map<String, UpdateSiteSemanticElementDraft> unique = new LinkedHashMap<>();
        result.stream().sorted(Comparator.comparing(UpdateSiteSemanticElementDraft::id))
                .forEach(value -> unique.putIfAbsent(value.id(), value));
        List<UpdateSiteSemanticElementDraft> deduplicated = new ArrayList<>(unique.values());
        int representable = (int) deduplicated.stream().filter(UpdateSiteSemanticElementDraft::representable).count();
        int unrepresentable = (int) deduplicated.stream().filter(item -> item.kind() == UpdateSiteSemanticKind.UNREPRESENTABLE).count();
        int partial = deduplicated.size() - representable - unrepresentable;
        if(deduplicated.isEmpty()) diagnostics.add("No generated editor commands, menus, forms or validation extensions were detected.");
        return new UpdateSiteSemanticRecovery(deduplicated, representable, Math.max(0, partial), unrepresentable, diagnostics);
    }

    /** Descripción: Ejecuta la operación «inspectPluginXml» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; xml: dato de entrada necesario para la operación; localization: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void inspectPluginXml(String path, String xml, UpdateSiteLocalizationIndex.Index localization, List<UpdateSiteSemanticElementDraft> result, List<String> diagnostics) {
        Matcher extensionMatcher = EXTENSION.matcher(xml);
        int extensionCount = 0;
        while(extensionMatcher.find()) {
            extensionCount++;
            String point = extensionMatcher.group(1);
            String body = extensionMatcher.group(2);
            Matcher elementMatcher = ELEMENT.matcher(body);
            boolean recognized = false;
            while(elementMatcher.find()) {
                String elementName = elementMatcher.group(1);
                Map<String, String> attributes = attributes(elementMatcher.group(2));
                UpdateSiteSemanticKind kind = semanticKind(point, elementName);
                UpdateSiteExtensionAdapterDescriptor adapter = kind == null
                        ? adapterCatalog.match(point, elementName).orElse(null) : null;
                if(kind == null && adapter == null) continue;
                recognized = true;
                if(adapter != null) kind = adapter.semanticKind();
                String label = adapter == null
                        ? first(attributes, "label", "name", "id", "class")
                        : first(attributes, adapter.idAttributes().toArray(String[]::new));
                if(label.isBlank()) label = first(attributes, "label", "name", "id", "class");
                String rawLabel = label;
                label = localization.resolve(path, label);
                String implementation = adapter == null
                        ? first(attributes, "class", "defaultHandler", "contributorClass")
                        : first(attributes, adapter.implementationAttributes().toArray(String[]::new));
                String id = adapter == null
                        ? first(attributes, "id", "commandId", "targetID", "fileExtension")
                        : first(attributes, adapter.idAttributes().toArray(String[]::new));
                if(id.isBlank()) id = point + ':' + label + ':' + result.size();
                boolean representable = kind != UpdateSiteSemanticKind.UNREPRESENTABLE;
                String neutralTarget = adapter == null ? neutralTarget(kind) : adapter.neutralTarget();
                List<String> warnings = new ArrayList<>();
                if(adapter != null) warnings.add("Recovered through declarative extension adapter: " + adapter.id() + ".");
                if(implementation.isBlank() && requiresImplementation(kind)) warnings.add("No implementation class was declared in plugin.xml.");
                if(rawLabel.startsWith("%") && label.equals(rawLabel)) warnings.add("The visible label is externalized but no matching localization property was found.");
                if(rawLabel.startsWith("%") && !label.equals(rawLabel)) warnings.add("The visible label was resolved from a nearby Eclipse .properties resource.");
                UpdateSiteConfidence confidence = adapter == null ? UpdateSiteConfidence.RECOVERED : adapter.confidence();
                String recoveredPurpose = adapter == null ? purpose(kind) : adapter.purpose();
                result.add(new UpdateSiteSemanticElementDraft(semanticId(kind, id), kind, label, implementation, path, point,
                        confidence, representable, neutralTarget, recoveredPurpose, warnings));
            }
            if(!recognized) {
                result.add(new UpdateSiteSemanticElementDraft(stable(point + ':' + extensionCount), UpdateSiteSemanticKind.UNREPRESENTABLE,
                        point, "", path, point, UpdateSiteConfidence.RECOVERED, false, "",
                        "Eclipse extension point not yet mapped to a neutral HYDRA capability.",
                        List.of("The extension is preserved in the import log so it is not silently discarded.")));
            }
        }
        if(extensionCount == 0 && xml.contains("<plugin")) diagnostics.add("No extension declarations were found in " + path + ".");
    }

    /** Descripción: Ejecuta la operación «inspectGeneratedClass» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void inspectGeneratedClass(String path, List<UpdateSiteSemanticElementDraft> result) {
        String lower = path.toLowerCase(Locale.ROOT);
        UpdateSiteSemanticKind kind = null;
        String purpose = "";
        if(lower.endsWith("itemprovider.class") || lower.contains("itemprovider")) {
            kind = UpdateSiteSemanticKind.GENERATED_ITEM_PROVIDER;
            purpose = "EMF.Edit item provider: labels, icons, property descriptors and child-creation metadata.";
        } else if(lower.endsWith("propertysource.class") || lower.contains("propertydescriptor")) {
            kind = UpdateSiteSemanticKind.FORM;
            purpose = "Generated property metadata that can help reconstruct editable fields.";
        } else if(lower.endsWith("validator.class") || lower.contains("validation")) {
            kind = UpdateSiteSemanticKind.VALIDATION;
            purpose = "Generated or registered validation logic.";
        }
        if(kind == null) return;
        String file = path.substring(path.lastIndexOf('/') + 1).replace(".class", "");
        result.add(new UpdateSiteSemanticElementDraft(stable(path), kind, file, file, path, "",
                UpdateSiteConfidence.INFERRED, kind != UpdateSiteSemanticKind.VALIDATION,
                kind == UpdateSiteSemanticKind.FORM ? "HydraFormField draft" : "Editor semantic draft", purpose,
                List.of("Recovered from class name and package location; method bodies were not executed.")));
    }

    /** Descripción: Ejecuta la operación «attributes» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> attributes(String source) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(source);
        while(matcher.find()) result.put(matcher.group(1), matcher.group(2));
        return result;
    }

    /** Descripción: Ejecuta la operación «semanticKind» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: point: dato de entrada necesario para la operación; element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteSemanticKind semanticKind(String point, String element) {
        String value = (point + ':' + element).toLowerCase(Locale.ROOT);
        if(value.contains("commands") || element.equalsIgnoreCase("command")) return UpdateSiteSemanticKind.COMMAND;
        if(value.contains("handlers") || element.equalsIgnoreCase("handler")) return UpdateSiteSemanticKind.HANDLER;
        if(value.contains("menus") || element.equalsIgnoreCase("menu") || element.equalsIgnoreCase("command")) return UpdateSiteSemanticKind.MENU;
        if(value.contains("editors")) return UpdateSiteSemanticKind.EDITOR;
        if(value.contains("views")) return UpdateSiteSemanticKind.VIEW;
        if(value.contains("propertypages")) return UpdateSiteSemanticKind.PROPERTY_PAGE;
        if(value.contains("preferencepages")) return UpdateSiteSemanticKind.PREFERENCE_PAGE;
        if(value.contains("validation") || value.contains("constraint")) return UpdateSiteSemanticKind.VALIDATION;
        if(value.contains("extension_parser") || value.contains("contenttype") || value.contains("fileextension")) return UpdateSiteSemanticKind.FILE_EXTENSION;
        if(value.contains("generated_package") || value.contains("factory_override") || value.contains("adapterfactory")) return UpdateSiteSemanticKind.RUNTIME_EXTENSION;
        return null;
    }

    /** Descripción: Ejecuta la operación «neutralTarget» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String neutralTarget(UpdateSiteSemanticKind kind) {
        return switch(kind) {
            case COMMAND, HANDLER, MENU -> "HYDRA operation/action and editor menu draft";
            case EDITOR, VIEW -> "HYDRA editor definition or viewpoint draft";
            case PROPERTY_PAGE, PREFERENCE_PAGE, FORM, GENERATED_ITEM_PROVIDER -> "HYDRA form and property-field draft";
            case VALIDATION -> "HYDRA validation draft";
            case FILE_EXTENSION -> "HYDRA import/export file association";
            case RUNTIME_EXTENSION -> "HYDRA runtime capability metadata";
            case UNREPRESENTABLE -> "";
        };
    }

    /** Descripción: Ejecuta la operación «purpose» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String purpose(UpdateSiteSemanticKind kind) {
        return switch(kind) {
            case COMMAND -> "Declares an action that can be invoked from menus, toolbars or key bindings.";
            case HANDLER -> "Connects a command identifier with the Java class that executes it.";
            case MENU -> "Places commands inside menus, context menus or toolbars.";
            case EDITOR -> "Registers an Eclipse editor and the file types it opens.";
            case VIEW -> "Registers an auxiliary Eclipse view.";
            case PROPERTY_PAGE -> "Adds editable properties for a selected model element or resource.";
            case PREFERENCE_PAGE -> "Adds configuration UI for the installed tool.";
            case FORM -> "Describes editable fields or generated property descriptors.";
            case VALIDATION -> "Registers constraints or validation providers.";
            case FILE_EXTENSION -> "Associates model file extensions with parsers, editors or content types.";
            case GENERATED_ITEM_PROVIDER -> "Provides labels, icons, properties and child creation metadata for EMF.Edit.";
            case RUNTIME_EXTENSION -> "Registers runtime packages, factories or adapters required by generated models.";
            case UNREPRESENTABLE -> "Preserved extension whose semantics are not yet mapped by HYDRA.";
        };
    }

    /** Descripción: Valida las condiciones necesarias para «requiresImplementation» y rechaza entradas no válidas. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean requiresImplementation(UpdateSiteSemanticKind kind) {
        return kind == UpdateSiteSemanticKind.HANDLER || kind == UpdateSiteSemanticKind.EDITOR
                || kind == UpdateSiteSemanticKind.VIEW || kind == UpdateSiteSemanticKind.PROPERTY_PAGE
                || kind == UpdateSiteSemanticKind.PREFERENCE_PAGE;
    }

    /** Descripción: Ejecuta la operación «first» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: attributes: dato de entrada necesario para la operación; keys: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String first(Map<String, String> attributes, String... keys) {
        for(String key : keys) {
            String value = attributes.getOrDefault(key, "").trim();
            if(!value.isBlank()) return value;
            value = attributes.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                    .map(Map.Entry::getValue).findFirst().orElse("").trim();
            if(!value.isBlank()) return value;
        }
        return "";
    }

    /** Descripción: Ejecuta la operación «semanticId» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: kind: dato de entrada necesario para la operación; rawId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String semanticId(UpdateSiteSemanticKind kind, String rawId) {
        return stable(kind.name().toLowerCase(Locale.ROOT) + ':' + rawId);
    }

    /** Descripción: Ejecuta la operación «stable» correspondiente a la responsabilidad de UpdateSiteSemanticEditorRecoveryService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stable(String value) {
        String normalized = value == null ? "semantic" : value.replaceAll("[^A-Za-z0-9._:-]", "-");
        return normalized.isBlank() ? "semantic" : normalized;
    }
}

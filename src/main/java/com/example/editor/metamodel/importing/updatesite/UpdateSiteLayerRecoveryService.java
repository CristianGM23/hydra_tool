package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.editor.metamodel.importing.support.SecureXmlSupport;

/** Recupera borradores neutrales de paletas, representaciones y mappings. */
@Service
public class UpdateSiteLayerRecoveryService {
    private static final Pattern XTEXT_RULE = Pattern.compile("(?m)^\\s*(?:entry\\s+)?(?!terminal\\b)([A-Z][A-Za-z0-9_]*)\\s*(?:returns\\s+([A-Za-z0-9_:.]+))?\\s*:");
    private static final Pattern LANGIUM_RULE = Pattern.compile("(?m)^\\s*(?:(?:entry)\\s+)?(?!terminal\\b|hidden\\b|fragment\\b)([A-Za-z_][\\w]*)\\s*(?:infers\\s+([A-Za-z0-9_:.]+))?\\s*:");

    /** Descripción: Ejecuta la operación «recover» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: repository: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteRecoveredLayers recover(UpdateSiteRepositoryContent repository, UpdateSiteImportOptions options) {
        List<UpdateSitePaletteGroupDraft> palettes = new ArrayList<>();
        List<UpdateSiteRepresentationDraft> representations = new ArrayList<>();
        List<UpdateSiteMappingDraft> mappings = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        for(Map.Entry<String, byte[]> entry : repository.files().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            UpdateSiteArtifactKind kind = UpdateSiteArtifactKinds.fromPath(entry.getKey());
            try {
                switch(kind) {
                    case GMF_TOOL -> { if(options.recoverPalettes()) palettes.addAll(recoverGmfTools(entry.getKey(), entry.getValue())); }
                    case GMF_GRAPH -> { if(options.recoverRepresentations()) representations.addAll(recoverGmfGraph(entry.getKey(), entry.getValue())); }
                    case GMF_MAP -> {
                        if(options.recoverMappings()) mappings.addAll(recoverGmfMappings(entry.getKey(), entry.getValue()));
                        if(options.recoverRepresentations()) representations.addAll(recoverGmfMapRepresentations(entry.getKey(), entry.getValue()));
                    }
                    case SIRIUS_VIEWPOINT -> recoverSirius(entry.getKey(), entry.getValue(), options, palettes, representations, mappings);
                    case XTEXT_GRAMMAR -> recoverXtext(entry.getKey(), entry.getValue(), options, palettes, representations, mappings);
                    case LANGIUM_GRAMMAR -> recoverLangium(entry.getKey(), entry.getValue(), options, palettes, representations, mappings);
                    case ECORE_DIAGRAM -> { if(options.recoverRepresentations()) representations.add(new UpdateSiteRepresentationDraft(id("ecore-diagram", entry.getKey()), fileStem(entry.getKey()) + " Ecore diagram", "ECORE", "DIAGRAM", List.of(), entry.getKey(), UpdateSiteConfidence.RECOVERED, List.of("Classic Ecore diagram layout is preserved as a representation hint; semantic structure comes from Ecore or other recovered evidence."))); }
                    case GMF_GEN -> {
                        if(options.recoverRepresentations()) representations.add(new UpdateSiteRepresentationDraft(
                                id("gmfgen", entry.getKey()), fileStem(entry.getKey()) + " generated diagram", "GMF", "DIAGRAM",
                                List.of(), entry.getKey(), UpdateSiteConfidence.RECOVERED,
                                List.of("GMFGen contains generated-editor configuration; semantic mappings come from GMFMap")));
                    }
                    default -> { }
                }
            } catch(RuntimeException exception) {
                diagnostics.add("Could not recover " + kind + " from " + entry.getKey() + ": " + exception.getMessage());
            }
        }

        return new UpdateSiteRecoveredLayers(deduplicatePalettes(palettes), deduplicateRepresentations(representations),
                deduplicateMappings(mappings), diagnostics);
    }

    /** Descripción: Ejecuta la operación «recoverGmfTools» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSitePaletteGroupDraft> recoverGmfTools(String path, byte[] content) {
        Document document = SecureXmlSupport.parse(content, "GMF tooling model");
        List<UpdateSitePaletteGroupDraft> result = new ArrayList<>();
        NodeList all = document.getElementsByTagName("*");
        int groupOrder = 0;
        for(int index = 0; index < all.getLength(); index++) {
            Element element = element(all.item(index));
            if(element == null || !semanticType(element).contains("toolgroup")) continue;
            String groupLabel = first(element, "title", "name", "id");
            if(groupLabel.isBlank()) groupLabel = "Recovered GMF tools";
            List<UpdateSitePaletteToolDraft> tools = descendantTools(element, path);
            if(!tools.isEmpty()) result.add(new UpdateSitePaletteGroupDraft(id("gmf-group", path, groupLabel), groupLabel,
                    groupOrder++, tools, path, UpdateSiteConfidence.DERIVED));
        }
        if(result.isEmpty()) {
            List<UpdateSitePaletteToolDraft> tools = descendantTools(document.getDocumentElement(), path);
            if(!tools.isEmpty()) result.add(new UpdateSitePaletteGroupDraft(id("gmf-group", path, "default"),
                    "Recovered GMF tools", 0, tools, path, UpdateSiteConfidence.DERIVED));
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «descendantTools» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: root: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSitePaletteToolDraft> descendantTools(Element root, String path) {
        List<UpdateSitePaletteToolDraft> result = new ArrayList<>();
        NodeList descendants = root.getElementsByTagName("*");
        for(int index = 0; index < descendants.getLength(); index++) {
            Element tool = element(descendants.item(index));
            if(tool == null) continue;
            String type = semanticType(tool);
            if(!type.contains("creationtool") && !type.contains("connectiontool")) continue;
            String label = first(tool, "title", "name", "id");
            if(label.isBlank()) label = type.contains("connection") ? "Create connection" : "Create element";
            String target = fragment(first(tool, "element", "target", "domainMetaElement"));
            result.add(new UpdateSitePaletteToolDraft(id("gmf-tool", path, label), label,
                    type.contains("connection") ? "EDGE" : "NODE", target, path, UpdateSiteConfidence.DERIVED));
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «recoverGmfGraph» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteRepresentationDraft> recoverGmfGraph(String path, byte[] content) {
        Document document = SecureXmlSupport.parse(content, "GMF graphical definition");
        Set<String> roots = new LinkedHashSet<>();
        NodeList nodes = document.getElementsByTagName("*");
        for(int index = 0; index < nodes.getLength(); index++) {
            Element element = element(nodes.item(index));
            if(element == null) continue;
            String local = local(element).toLowerCase(Locale.ROOT);
            if(local.contains("canvas") || local.equals("nodes") || local.contains("diagramlabel")) {
                String name = first(element, "name", "figure", "id");
                if(!name.isBlank()) roots.add(fragment(name));
            }
        }
        return List.of(new UpdateSiteRepresentationDraft(id("gmfgraph", path), fileStem(path) + " diagram", "GMF",
                "DIAGRAM", List.copyOf(roots), path, UpdateSiteConfidence.DERIVED,
                List.of("Graphical figures are recovered as neutral representation hints; exact rendering code is technology-specific")));
    }

    /** Descripción: Ejecuta la operación «recoverGmfMappings» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteMappingDraft> recoverGmfMappings(String path, byte[] content) {
        Document document = SecureXmlSupport.parse(content, "GMF mapping model");
        List<UpdateSiteMappingDraft> result = new ArrayList<>();
        NodeList all = document.getElementsByTagName("*");
        for(int index = 0; index < all.getLength(); index++) {
            Element mapping = element(all.item(index));
            if(mapping == null) continue;
            String local = local(mapping).toLowerCase(Locale.ROOT);
            String type = semanticType(mapping);
            if(!type.contains("mapping") && !local.equals("nodes") && !local.equals("links")
                    && !local.equals("labels") && !local.equals("featurelabelmappings")) continue;
            String semantic = fragment(first(mapping, "domainMetaElement", "domainMetaFeature", "element"));
            String visual = fragment(first(mapping, "diagramNode", "diagramLink", "diagramLabel", "tool"));
            if(semantic.isBlank() && visual.isBlank()) continue;
            String kind = type.contains("link") || type.contains("edge") || local.equals("links") ? "EDGE"
                    : type.contains("label") || local.contains("label") ? "LABEL" : "NODE";
            result.add(new UpdateSiteMappingDraft(id("gmfmap", path, Integer.toString(index)), semantic, visual,
                    kind, path, UpdateSiteConfidence.DERIVED,
                    semantic.isBlank() ? "Semantic element could not be resolved from GMFMap" : ""));
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «recoverGmfMapRepresentations» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteRepresentationDraft> recoverGmfMapRepresentations(String path, byte[] content) {
        Document document = SecureXmlSupport.parse(content, "GMF mapping model");
        String root = "";
        NodeList mappings = document.getElementsByTagName("mapping");
        if(mappings.getLength() > 0 && mappings.item(0) instanceof Element element) {
            root = fragment(first(element, "domainMetaElement", "diagramCanvas"));
        }
        return List.of(new UpdateSiteRepresentationDraft(id("gmfmap-representation", path), fileStem(path) + " mapped diagram",
                "GMF", "DIAGRAM", root.isBlank() ? List.of() : List.of(root), path, UpdateSiteConfidence.DERIVED, List.of()));
    }

    /** Descripción: Ejecuta la operación «recoverSirius» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento; palettes: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; mappings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverSirius(String path, byte[] content, UpdateSiteImportOptions options,
            List<UpdateSitePaletteGroupDraft> palettes, List<UpdateSiteRepresentationDraft> representations,
            List<UpdateSiteMappingDraft> mappings) {
        Document document = SecureXmlSupport.parse(content, "Sirius viewpoint specification");
        NodeList all = document.getElementsByTagName("*");
        Map<String, List<UpdateSitePaletteToolDraft>> toolsBySection = new LinkedHashMap<>();
        for(int index = 0; index < all.getLength(); index++) {
            Element element = element(all.item(index));
            if(element == null) continue;
            String local = local(element).toLowerCase(Locale.ROOT);
            String type = semanticType(element);
            String name = first(element, "name", "label", "id");
            if(options.recoverRepresentations() && (type.contains("diagramdescription") || type.contains("editiontabledescription")
                    || type.contains("treedescription") || local.equals("ownedrepresentations"))) {
                String domain = first(element, "domainClass", "domainClassExpression", "semanticCandidatesExpression");
                String kind = type.contains("editiontable") || type.contains("table") ? "TABLE"
                        : type.contains("tree") ? "TREE" : "DIAGRAM";
                representations.add(new UpdateSiteRepresentationDraft(id("sirius-representation", path, name),
                        name.isBlank() ? fileStem(path) + " Sirius " + kind.toLowerCase(Locale.ROOT) : name, "SIRIUS", kind,
                        domain.isBlank() ? List.of() : List.of(fragment(domain)), path, UpdateSiteConfidence.DERIVED, List.of()));
            }
            if(options.recoverMappings() && (type.contains("nodemapping") || type.contains("containermapping") || type.contains("edgemapping"))) {
                String semantic = fragment(first(element, "domainClass", "targetFinderExpression", "semanticCandidatesExpression"));
                String visual = name.isBlank() ? local : name;
                mappings.add(new UpdateSiteMappingDraft(id("sirius-mapping", path, Integer.toString(index)), semantic,
                        visual, type.contains("edge") ? "EDGE" : type.contains("container") ? "CONTAINER" : "NODE",
                        path, UpdateSiteConfidence.DERIVED, semantic.isBlank() ? "Sirius mapping uses an expression that requires manual review" : ""));
            }
            if(options.recoverPalettes() && (type.contains("creationdescription") || type.contains("tool") || local.contains("tool"))) {
                String section = ancestorName(element, "toolsection");
                if(section.isBlank()) section = "Recovered Sirius tools";
                String toolName = name.isBlank() ? local : name;
                String kind = type.contains("edge") || type.contains("connection") ? "EDGE" : "NODE";
                String target = fragment(first(element, "domainClass", "mapping", "containerViewExpression"));
                toolsBySection.computeIfAbsent(section, ignored -> new ArrayList<>()).add(new UpdateSitePaletteToolDraft(
                        id("sirius-tool", path, toolName), toolName, kind, target, path, UpdateSiteConfidence.DERIVED));
            }
        }
        int order = palettes.size();
        for(Map.Entry<String, List<UpdateSitePaletteToolDraft>> entry : toolsBySection.entrySet()) {
            palettes.add(new UpdateSitePaletteGroupDraft(id("sirius-group", path, entry.getKey()), entry.getKey(), order++,
                    entry.getValue(), path, UpdateSiteConfidence.DERIVED));
        }
    }

    /** Descripción: Ejecuta la operación «recoverXtext» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento; palettes: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; mappings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverXtext(String path, byte[] content, UpdateSiteImportOptions options,
            List<UpdateSitePaletteGroupDraft> palettes, List<UpdateSiteRepresentationDraft> representations,
            List<UpdateSiteMappingDraft> mappings) {
        String source = new String(content, StandardCharsets.UTF_8);
        Matcher matcher = XTEXT_RULE.matcher(source);
        List<UpdateSitePaletteToolDraft> tools = new ArrayList<>();
        Set<String> roots = new LinkedHashSet<>();
        int index = 0;
        while(matcher.find()) {
            String rule = matcher.group(1);
            String target = matcher.group(2) == null ? rule : fragment(matcher.group(2));
            if(options.recoverPalettes()) tools.add(new UpdateSitePaletteToolDraft(id("xtext-tool", path, rule),
                    "Create " + target, "TEXTUAL_RULE", target, path, UpdateSiteConfidence.DERIVED));
            if(options.recoverMappings()) mappings.add(new UpdateSiteMappingDraft(id("xtext-mapping", path, Integer.toString(index++)),
                    target, rule, "GRAMMAR_RULE", path, UpdateSiteConfidence.DERIVED, ""));
            roots.add(target);
        }
        if(options.recoverPalettes() && !tools.isEmpty()) palettes.add(new UpdateSitePaletteGroupDraft(id("xtext-group", path),
                "Grammar elements", 0, tools, path, UpdateSiteConfidence.DERIVED));
        if(options.recoverRepresentations()) representations.add(new UpdateSiteRepresentationDraft(id("xtext-representation", path),
                fileStem(path) + " textual representation", "XTEXT", "TEXTUAL", List.copyOf(roots), path,
                UpdateSiteConfidence.DERIVED, List.of("Grammar rules are preserved as textual mappings, not as graphical notation")));
    }

    /** Descripción: Ejecuta la operación «recoverLangium» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; options: configuración que gobierna el comportamiento; palettes: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; mappings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverLangium(String path, byte[] content, UpdateSiteImportOptions options,
            List<UpdateSitePaletteGroupDraft> palettes, List<UpdateSiteRepresentationDraft> representations,
            List<UpdateSiteMappingDraft> mappings) {
        recoverTextualGrammar(path, content, "LANGIUM", LANGIUM_RULE, options, palettes, representations, mappings);
    }

    /** Descripción: Ejecuta la operación «recoverTextualGrammar» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; technology: dato de entrada necesario para la operación; pattern: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; palettes: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; mappings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void recoverTextualGrammar(String path, byte[] content, String technology, Pattern pattern,
            UpdateSiteImportOptions options, List<UpdateSitePaletteGroupDraft> palettes,
            List<UpdateSiteRepresentationDraft> representations, List<UpdateSiteMappingDraft> mappings) {
        String source = new String(content, StandardCharsets.UTF_8);
        Matcher matcher = pattern.matcher(source);
        List<UpdateSitePaletteToolDraft> tools = new ArrayList<>();
        Set<String> roots = new LinkedHashSet<>();
        int index = 0;
        while(matcher.find()) {
            String rule = matcher.group(1);
            String target = matcher.group(2) == null ? rule : fragment(matcher.group(2));
            if(options.recoverPalettes()) tools.add(new UpdateSitePaletteToolDraft(id(technology.toLowerCase(Locale.ROOT)+"-tool", path, rule), "Create " + target, "TEXTUAL_RULE", target, path, UpdateSiteConfidence.DERIVED));
            if(options.recoverMappings()) mappings.add(new UpdateSiteMappingDraft(id(technology.toLowerCase(Locale.ROOT)+"-mapping", path, Integer.toString(index++)), target, rule, "GRAMMAR_RULE", path, UpdateSiteConfidence.DERIVED, ""));
            roots.add(target);
        }
        if(options.recoverPalettes() && !tools.isEmpty()) palettes.add(new UpdateSitePaletteGroupDraft(id(technology.toLowerCase(Locale.ROOT)+"-group", path), "Grammar elements", 0, tools, path, UpdateSiteConfidence.DERIVED));
        if(options.recoverRepresentations()) representations.add(new UpdateSiteRepresentationDraft(id(technology.toLowerCase(Locale.ROOT)+"-representation", path), fileStem(path) + " textual representation", technology, "TEXTUAL", List.copyOf(roots), path, UpdateSiteConfidence.DERIVED, List.of("Grammar rules are preserved as textual mappings, not as graphical notation")));
    }

    /** Descripción: Ejecuta la operación «deduplicatePalettes» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSitePaletteGroupDraft> deduplicatePalettes(List<UpdateSitePaletteGroupDraft> values) {
        Map<String, UpdateSitePaletteGroupDraft> result = new LinkedHashMap<>();
        values.stream().sorted(Comparator.comparing(UpdateSitePaletteGroupDraft::id)).forEach(value -> result.putIfAbsent(value.id(), value));
        return List.copyOf(result.values());
    }

    /** Descripción: Ejecuta la operación «deduplicateRepresentations» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteRepresentationDraft> deduplicateRepresentations(List<UpdateSiteRepresentationDraft> values) {
        Map<String, UpdateSiteRepresentationDraft> result = new LinkedHashMap<>();
        values.stream().sorted(Comparator.comparing(UpdateSiteRepresentationDraft::id)).forEach(value -> result.putIfAbsent(value.id(), value));
        return List.copyOf(result.values());
    }

    /** Descripción: Ejecuta la operación «deduplicateMappings» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteMappingDraft> deduplicateMappings(List<UpdateSiteMappingDraft> values) {
        Map<String, UpdateSiteMappingDraft> result = new LinkedHashMap<>();
        values.stream().sorted(Comparator.comparing(UpdateSiteMappingDraft::id)).forEach(value -> result.putIfAbsent(value.id(), value));
        return List.copyOf(result.values());
    }

    /** Descripción: Ejecuta la operación «ancestorName» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: element: dato de entrada necesario para la operación; localFragment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String ancestorName(Element element, String localFragment) {
        Node parent = element.getParentNode();
        while(parent instanceof Element ancestor) {
            if(local(ancestor).toLowerCase(Locale.ROOT).contains(localFragment)) return first(ancestor, "name", "label", "id");
            parent = parent.getParentNode();
        }
        return "";
    }

    /** Descripción: Ejecuta la operación «first» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: element: dato de entrada necesario para la operación; attributes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String first(Element element, String... attributes) {
        for(String attribute : attributes) {
            String value = attr(element, attribute);
            if(!value.isBlank()) return value;
        }
        return "";
    }

    /** Descripción: Ejecuta la operación «fragment» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String fragment(String value) {
        if(value == null) return "";
        String result = value.trim();
        if(result.contains("#//")) result = result.substring(result.lastIndexOf("#//") + 3);
        if(result.contains("/")) result = result.substring(result.lastIndexOf('/') + 1);
        if(result.contains(".")) result = result.substring(result.lastIndexOf('.') + 1);
        if(result.contains(":")) result = result.substring(result.lastIndexOf(':') + 1);
        return result.replaceAll("[^A-Za-z0-9_]", "");
    }

    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: parts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String id(String... parts) {
        String material = String.join(":", parts);
        return "recovered-" + UpdateSiteHashing.sha256(material.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    /** Descripción: Ejecuta la operación «fileStem» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileStem(String path) {
        String name = path.contains("!/") ? path.substring(path.lastIndexOf("!/") + 2) : path;
        if(name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Descripción: Ejecuta la operación «semanticType» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String semanticType(Element element) {
        String local = local(element).toLowerCase(Locale.ROOT);
        String declared = first(element, "xsi:type", "type").toLowerCase(Locale.ROOT);
        return local + " " + declared;
    }

    /** Descripción: Ejecuta la operación «local» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String local(Element element) {
        return element.getLocalName() == null ? element.getTagName().replaceFirst("^.*:", "") : element.getLocalName();
    }

    /** Descripción: Ejecuta la operación «element» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Element element(Node node) { return node instanceof Element value ? value : null; }
    /** Descripción: Ejecuta la operación «attr» correspondiente a la responsabilidad de UpdateSiteLayerRecoveryService. Entradas: element: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attr(Element element, String name) { return element.hasAttribute(name) ? element.getAttribute(name).trim() : ""; }
}

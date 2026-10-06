package com.example.editor.project.recovery;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.editor.grammar.application.GrammarRuleGraphAnalyzer;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;

/** Analiza proyectos externos heterogéneos como un único workspace de recuperación. */
@Service
public class ExternalProjectRecoveryService {
    private static final Pattern ECLASS = Pattern.compile("(?s)<eClassifiers\\b[^>]*xsi:type=\"ecore:EClass\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern ECLASS_ALT = Pattern.compile("(?s)<eClassifiers\\b[^>]*name=\"([^\"]+)\"[^>]*xsi:type=\"ecore:EClass\"");
    private static final Pattern XTEXT_RULE = Pattern.compile("(?m)^\\s*([A-Z][A-Za-z0-9_]*)\\s*(?:returns\\s+[^:]+)?\\s*:");
    private static final Pattern JAVA_CLASS = Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern SIRIUS_DOMAIN = Pattern.compile("domainClass=\"([^\"]+)\"");
    private static final Pattern GMF_ELEMENT = Pattern.compile("(?:domainMetaElement|element)=\"([^\"]+)\"");

    private final ExternalProjectArchiveWalker walker;

    /** Descripción: Inicializa una instancia de ExternalProjectRecoveryService con sus dependencias y datos de entrada. Entradas: walker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalProjectRecoveryService(ExternalProjectArchiveWalker walker) {
        this.walker = walker;
    }

    /** Analiza un ZIP externo sin ejecutar código ni escribirlo en el workspace. */
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de Classification. Entradas: sourceName: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; compressedBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExternalProjectRecoveryReport analyze(String sourceName, InputStream source, long compressedBytes) {
        return analyzeEntries(walker.walk(sourceName, source, compressedBytes));
    }

    /** Construye el informe a partir de un inventario ya validado, evitando releer el archivo durante la importación. */
    /** Descripción: Ejecuta la operación «analyzeEntries» correspondiente a la responsabilidad de Classification. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ ExternalProjectRecoveryReport analyzeEntries(List<ExternalProjectArchiveEntry> entries) {
        List<ExternalProjectResource> resources = new ArrayList<>();
        List<ExternalProjectMappingEvidence> evidence = new ArrayList<>();
        Set<String> technologies = new LinkedHashSet<>();
        List<String> diagnostics = new ArrayList<>();
        List<ExternalProjectGrammarInspection> grammarInspections = new ArrayList<>();

        for(ExternalProjectArchiveEntry entry : entries) {
            Classification classification = classify(entry.path(), entry.bytes());
            technologies.addAll(classification.technologies());
            resources.add(new ExternalProjectResource(entry.path(), classification.kind(), entry.size(),
                    entry.sha256(), classification.technologies(), classification.purpose()));
            evidence.addAll(extractEvidence(entry, classification));
            inspectGrammar(entry, classification).ifPresent(grammarInspections::add);
        }

        List<ExternalProjectMappingEvidence> reconciled = reconcile(evidence);
        List<ExternalProjectTopologyNode> topology = topology(resources);
        List<ExternalProjectIntegrationSuggestion> suggestions = integrationSuggestions(resources, technologies);
        long totalBytes = resources.stream().mapToLong(ExternalProjectResource::size).sum();
        String fingerprint = fingerprint(resources);
        if(technologies.isEmpty()) diagnostics.add("No known modeling technology was detected; all files remain available as project resources.");
        if(reconciled.stream().noneMatch(ExternalProjectMappingEvidence::autoSelectable)) {
            diagnostics.add("No mapping is safe enough for automatic acceptance; review the proposed evidence before materialization.");
        }
        long unknown = resources.stream().filter(resource -> resource.kind() == ExternalProjectResourceKind.PROJECT_RESOURCE).count();
        if(unknown > 0) diagnostics.add(unknown + " project resource(s) are inventoried even though no semantic adapter currently interprets them.");

        return new ExternalProjectRecoveryReport("external-" + fingerprint.substring(0, 20), Instant.now(), fingerprint,
                resources.size(), totalBytes, technologies.stream().sorted().toList(),
                resources.stream().sorted(Comparator.comparing(ExternalProjectResource::path)).toList(), topology,
                reconciled, suggestions, grammarInspections.stream().sorted(Comparator.comparing(ExternalProjectGrammarInspection::sourcePath)).toList(), diagnostics);
    }

    /** Descripción: Ejecuta la operación «inspectGrammar» correspondiente a la responsabilidad de Classification. Entradas: entry: dato de entrada necesario para la operación; classification: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private java.util.Optional<ExternalProjectGrammarInspection> inspectGrammar(ExternalProjectArchiveEntry entry, Classification classification) {
        GrammarTechnology technology = switch (classification.kind()) {
            case XTEXT_GRAMMAR -> GrammarTechnology.XTEXT;
            case LANGIUM_GRAMMAR -> GrammarTechnology.LANGIUM;
            case ANTLR_GRAMMAR -> GrammarTechnology.ANTLR;
            default -> null;
        };
        if (technology == null) return java.util.Optional.empty();
        String content = new String(entry.bytes(), StandardCharsets.UTF_8);
        String extension = technology == GrammarTechnology.XTEXT ? "xtext" : technology == GrammarTechnology.LANGIUM ? "langium" : "g4";
        GrammarSourceDocument document = new GrammarSourceDocument("external-grammar", "grammar." + extension, extension, content, "", true, false);
        GrammarDefinitionModel grammar = new GrammarDefinitionModel("1.0", "external-grammar", leaf(entry.path()), technology,
                GrammarAuthoringMode.GRAMMAR_FIRST, "", "inspection", document.id(), "", List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        GrammarRuleGraphAnalyzer.Analysis graph = GrammarRuleGraphAnalyzer.analyze(grammar);
        Set<String> recursive = new LinkedHashSet<>(), left = new LinkedHashSet<>(), automatic = new LinkedHashSet<>();
        List<String> explanations = new ArrayList<>();
        for (GrammarRuleGraphAnalyzer.Recursion item : graph.recursions()) {
            recursive.addAll(item.rules());
            if (item.leftRecursive()) left.addAll(item.rules());
            if (item.rewrite() != null) automatic.addAll(item.rules());
            String cycle = String.join(" -> ", item.cycle());
            switch (item.kind()) {
                case DIRECT_LEFT -> explanations.add("Direct left recursion: " + cycle + ". The parser can return to the same rule before consuming input. HYDRA can prepare a rewrite when a safe base/repetition form is available.");
                case INDIRECT_LEFT -> explanations.add("Indirect left recursion: " + cycle + ". Several rules return to the origin without consuming input; review the complete cycle before compilation.");
                case DIRECT_LEFT_NATIVE_SUPPORTED -> explanations.add("ANTLR 4 native direct left recursion: " + cycle + ". It is supported by the selected provider and is retained, but remains visible for portability review.");
                case DIRECT_SAFE, INDIRECT_SAFE -> explanations.add("Safe recursive structure: " + cycle + ". Input is consumed before the recursive call, therefore no left-recursion correction is required.");
            }
        }
        if (!graph.entry().rule().isBlank()) explanations.add("Inferred entry rule '" + graph.entry().rule() + "' via " + graph.entry().method() + " (" + Math.round(graph.entry().confidence() * 100d) + "% confidence). " + graph.entry().reason());
        return java.util.Optional.of(new ExternalProjectGrammarInspection(entry.path(), technology.name(), graph.entry().rule(), graph.entry().method(), graph.entry().confidence(),
                graph.ruleNames(), recursive.stream().sorted().toList(), left.stream().sorted().toList(), automatic.stream().sorted().toList(), explanations));
    }

    /** Descripción: Ejecuta la operación «classify» correspondiente a la responsabilidad de Classification. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Classification classify(String path, byte[] content) {
        String lower = path.toLowerCase(Locale.ROOT);
        List<String> tech = new ArrayList<>();
        ExternalProjectResourceKind kind = ExternalProjectResourceKind.PROJECT_RESOURCE;
        String purpose = "Project resource preserved in the complete external-project inventory.";

        if(lower.endsWith(".ecore") || lower.endsWith(".emf") || lower.endsWith(".xcore")) {
            kind = ExternalProjectResourceKind.METAMODEL; tech.add("EMF"); purpose = "Semantic metamodel definition.";
        } else if(lower.endsWith(".genmodel")) {
            kind = ExternalProjectResourceKind.GENMODEL; tech.add("EMF"); purpose = "EMF generation and editor metadata.";
        } else if(lower.endsWith(".xtext")) {
            kind = ExternalProjectResourceKind.XTEXT_GRAMMAR; tech.add("Xtext"); tech.add("EMF"); purpose = "Xtext language grammar.";
        } else if(lower.endsWith(".langium")) {
            kind = ExternalProjectResourceKind.LANGIUM_GRAMMAR; tech.add("Langium"); purpose = "Langium language grammar.";
        } else if(lower.endsWith(".g4")) {
            kind = ExternalProjectResourceKind.ANTLR_GRAMMAR; tech.add("ANTLR"); purpose = "ANTLR grammar.";
        } else if(lower.endsWith(".odesign")) {
            kind = ExternalProjectResourceKind.SIRIUS_DESIGN; tech.add("Sirius"); tech.add("EMF"); purpose = "Sirius viewpoint and diagram definition.";
        } else if(lower.matches(".*\\.gmf(?:graph|tool|map|gen)$")) {
            kind = ExternalProjectResourceKind.GMF_MODEL; tech.add("GMF"); tech.add("EMF"); purpose = "GMF graphical editor model.";
        } else if(lower.endsWith("plugin.xml") || lower.endsWith("fragment.xml")) {
            kind = ExternalProjectResourceKind.PLUGIN_DESCRIPTOR; tech.add("Eclipse"); purpose = "Eclipse extension declarations.";
        } else if(lower.endsWith("meta-inf/manifest.mf")) {
            kind = ExternalProjectResourceKind.OSGI_MANIFEST; tech.add("OSGi"); tech.add("Eclipse"); purpose = "OSGi bundle metadata.";
        } else if(isBuildDescriptor(lower)) {
            kind = ExternalProjectResourceKind.BUILD_DESCRIPTOR; addBuildTechnologies(lower, content, tech); purpose = "Build-system and module metadata.";
        } else if(lower.endsWith("itemprovider.java") || lower.contains("/provider/") && lower.endsWith("itemprovider.java")) {
            kind = ExternalProjectResourceKind.EMF_EDIT_SOURCE; tech.add("EMF.Edit"); tech.add("EMF"); purpose = "EMF.Edit item-provider source.";
        } else if(lower.endsWith("itemprovider.class") || lower.contains("/provider/") && lower.endsWith(".class")) {
            kind = ExternalProjectResourceKind.EMF_EDIT_BYTECODE; tech.add("EMF.Edit"); tech.add("EMF"); purpose = "Generated EMF.Edit bytecode.";
        } else if(isValidationSource(lower)) {
            kind = ExternalProjectResourceKind.VALIDATION_SOURCE; addLanguageTechnology(lower, tech); purpose = "Validation rule or validator source.";
        } else if(isQuickFixSource(lower)) {
            kind = ExternalProjectResourceKind.QUICK_FIX_SOURCE; addLanguageTechnology(lower, tech); purpose = "Quick-fix or repair source.";
        } else if(isGlspSource(lower, content)) {
            kind = ExternalProjectResourceKind.GLSP_SOURCE; tech.add("GLSP"); addLanguageTechnology(lower, tech); purpose = "GLSP server/client source.";
        } else if(isBesserSource(lower, content)) {
            kind = ExternalProjectResourceKind.BESSER_SOURCE; tech.add("BESSER"); addLanguageTechnology(lower, tech); purpose = "BESSER modeling source.";
        } else if(isTestSource(lower)) {
            kind = ExternalProjectResourceKind.TEST_SOURCE; addLanguageTechnology(lower, tech); purpose = "Tests/examples that can reinforce recovery evidence.";
        } else if(isSource(lower)) {
            kind = ExternalProjectResourceKind.SOURCE_CODE; addLanguageTechnology(lower, tech); purpose = "Source code available for static inference.";
        } else if(isImage(lower)) {
            kind = ExternalProjectResourceKind.IMAGE_ASSET; purpose = "Visual asset available for palettes, labels or editor contributions.";
        }
        return new Classification(kind, List.copyOf(new LinkedHashSet<>(tech)), purpose);
    }

    /** Descripción: Ejecuta la operación «extractEvidence» correspondiente a la responsabilidad de Classification. Entradas: entry: dato de entrada necesario para la operación; classification: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ExternalProjectMappingEvidence> extractEvidence(ExternalProjectArchiveEntry entry, Classification classification) {
        if(entry.size() > 2_000_000L) return List.of();
        String text = isLikelyText(entry.path()) ? new String(entry.bytes(), StandardCharsets.UTF_8) : "";
        List<ExternalProjectMappingEvidence> result = new ArrayList<>();
        switch(classification.kind()) {
            case METAMODEL -> {
                addMatches(result, entry.path(), "EClass", ECLASS.matcher(text), "METACLASS", 100,
                        ExternalProjectConfidence.EXACT, ExternalProjectFidelity.FULL,
                        List.of("name", "identity"), List.of(), List.of("EClass declaration in the semantic metamodel."));
                addMatches(result, entry.path(), "EClass", ECLASS_ALT.matcher(text), "METACLASS", 100,
                        ExternalProjectConfidence.EXACT, ExternalProjectFidelity.FULL,
                        List.of("name", "identity"), List.of(), List.of("EClass declaration in the semantic metamodel."));
            }
            case XTEXT_GRAMMAR -> addMatches(result, entry.path(), "grammar-rule", XTEXT_RULE.matcher(text), "METACLASS", 86,
                    ExternalProjectConfidence.RECOVERED, ExternalProjectFidelity.PARTIAL,
                    List.of("name", "grammar rule"), List.of("full Ecore metadata until grammar inference is reconciled"),
                    List.of("Upper-case Xtext parser rule is a strong semantic-type signal."));
            case SIRIUS_DESIGN -> addMatches(result, entry.path(), "domainClass", SIRIUS_DOMAIN.matcher(text), "EDITOR_MAPPING", 91,
                    ExternalProjectConfidence.RECOVERED, ExternalProjectFidelity.PARTIAL,
                    List.of("domain class", "diagram mapping provenance"), List.of("technology-specific rendering details"),
                    List.of("Sirius mapping declares an explicit domainClass."));
            case GMF_MODEL -> addMatches(result, entry.path(), "domainMetaElement", GMF_ELEMENT.matcher(text), "EDITOR_MAPPING", 89,
                    ExternalProjectConfidence.RECOVERED, ExternalProjectFidelity.PARTIAL,
                    List.of("domain element", "mapping provenance"), List.of("GMF figure/runtime-specific behavior"),
                    List.of("GMF model links a graphical mapping to a semantic element."));
            case EMF_EDIT_SOURCE -> result.addAll(itemProviderEvidence(entry.path(), text, 92, ExternalProjectConfidence.RECOVERED));
            case EMF_EDIT_BYTECODE -> result.addAll(itemProviderEvidence(entry.path(), "", 78, ExternalProjectConfidence.DERIVED));
            case VALIDATION_SOURCE -> result.add(namedSourceEvidence(entry.path(), text, "VALIDATION", 82, "Validator/check source detected."));
            case QUICK_FIX_SOURCE -> result.add(namedSourceEvidence(entry.path(), text, "QUICK_FIX", 82, "Quick-fix source detected."));
            case GLSP_SOURCE -> result.add(namedSourceEvidence(entry.path(), text, "INTEGRATION", 76, "GLSP source provides host-integration evidence."));
            case BESSER_SOURCE -> result.add(namedSourceEvidence(entry.path(), text, "INTEGRATION", 76, "BESSER source provides host-integration evidence."));
            default -> { }
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «addMatches» correspondiente a la responsabilidad de Classification. Entradas: result: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; locatorPrefix: dato de entrada necesario para la operación; matcher: dato de entrada necesario para la operación; targetKind: dato de entrada necesario para la operación; score: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación; fidelity: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; lost: dato de entrada necesario para la operación; reasons: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addMatches(List<ExternalProjectMappingEvidence> result, String path, String locatorPrefix, Matcher matcher,
            String targetKind, int score, ExternalProjectConfidence confidence, ExternalProjectFidelity fidelity,
            List<String> recovered, List<String> lost, List<String> reasons) {
        Set<String> seen = new LinkedHashSet<>();
        while(matcher.find()) {
            String name = normalizeElement(matcher.group(1));
            if(name.isBlank() || !seen.add(name)) continue;
            String targetId = targetKind + ":" + name;
            result.add(evidence(path, locatorPrefix + ":" + name, name, targetKind, targetId,
                    "STRUCTURAL", score, confidence, fidelity, recovered, lost, reasons));
        }
    }

    /** Descripción: Ejecuta la operación «itemProviderEvidence» correspondiente a la responsabilidad de Classification. Entradas: path: ruta o ubicación utilizada por la operación; text: valor de entrada que debe procesarse; score: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ExternalProjectMappingEvidence> itemProviderEvidence(String path, String text, int score,
            ExternalProjectConfidence confidence) {
        String file = leaf(path).replace(".class", "").replace(".java", "");
        String name = file.endsWith("ItemProvider") ? file.substring(0, file.length() - "ItemProvider".length()) : file;
        if(name.isBlank()) return List.of();
        List<String> recovered = new ArrayList<>(List.of("label provider", "editor provenance"));
        if(text.contains("getPropertyDescriptors")) recovered.add("property descriptors");
        if(text.contains("getChildrenFeatures")) recovered.add("child creation metadata");
        return List.of(evidence(path, "item-provider:" + file, name, "EDITOR_FORM", "METACLASS:" + name,
                "QUALIFIED_NAME", score, confidence, ExternalProjectFidelity.PARTIAL, recovered,
                List.of("runtime-only behavior cannot be executed during recovery"),
                List.of("EMF.Edit ItemProvider naming maps directly to its semantic class.")));
    }

    /** Descripción: Ejecuta la operación «namedSourceEvidence» correspondiente a la responsabilidad de Classification. Entradas: path: ruta o ubicación utilizada por la operación; text: valor de entrada que debe procesarse; targetKind: dato de entrada necesario para la operación; score: dato de entrada necesario para la operación; reason: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExternalProjectMappingEvidence namedSourceEvidence(String path, String text, String targetKind, int score, String reason) {
        Matcher matcher = JAVA_CLASS.matcher(text);
        String name = matcher.find() ? matcher.group(1) : leaf(path).replaceFirst("\\.[^.]+$", "");
        return evidence(path, "source:" + name, name, targetKind, targetKind + ":" + name,
                "SOURCE_INFERENCE", score, ExternalProjectConfidence.DERIVED, ExternalProjectFidelity.PARTIAL,
                List.of("source identity", "technology role"), List.of("runtime behavior"), List.of(reason));
    }

    /** Descripción: Ejecuta la operación «evidence» correspondiente a la responsabilidad de Classification. Entradas: path: ruta o ubicación utilizada por la operación; locator: dato de entrada necesario para la operación; sourceElement: dato de entrada necesario para la operación; targetKind: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; matchType: dato de entrada necesario para la operación; score: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación; fidelity: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; lost: dato de entrada necesario para la operación; reasons: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExternalProjectMappingEvidence evidence(String path, String locator, String sourceElement, String targetKind,
            String targetId, String matchType, int score, ExternalProjectConfidence confidence,
            ExternalProjectFidelity fidelity, List<String> recovered, List<String> lost, List<String> reasons) {
        String id = "map-" + sha256((path + ':' + locator + ':' + targetId).getBytes(StandardCharsets.UTF_8)).substring(0, 16);
        return new ExternalProjectMappingEvidence(id, path, locator, sourceElement, targetKind, targetId, matchType,
                score, confidence, fidelity, recovered, lost, List.of(), reasons, false);
    }

    /** Descripción: Ejecuta la operación «reconcile» correspondiente a la responsabilidad de Classification. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ExternalProjectMappingEvidence> reconcile(List<ExternalProjectMappingEvidence> source) {
        Map<String, List<ExternalProjectMappingEvidence>> byTarget = new LinkedHashMap<>();
        for(ExternalProjectMappingEvidence item : source) byTarget.computeIfAbsent(item.targetId(), key -> new ArrayList<>()).add(item);
        List<ExternalProjectMappingEvidence> result = new ArrayList<>();
        for(List<ExternalProjectMappingEvidence> group : byTarget.values()) {
            group.sort(Comparator.comparingInt(ExternalProjectMappingEvidence::score).reversed()
                    .thenComparing(ExternalProjectMappingEvidence::sourcePath));
            for(int index = 0; index < group.size(); index++) {
                ExternalProjectMappingEvidence item = group.get(index);
                int second = group.stream().filter(other -> !other.id().equals(item.id()))
                        .mapToInt(ExternalProjectMappingEvidence::score).max().orElse(-1);
                boolean safe = index == 0 && item.score() >= 90 && (second < 0 || item.score() - second >= 12)
                        && item.fidelity() != ExternalProjectFidelity.UNSUPPORTED;
                List<String> alternatives = group.stream().filter(other -> !other.id().equals(item.id()))
                        .map(ExternalProjectMappingEvidence::id).toList();
                result.add(item.reconcile(alternatives, safe));
            }
        }
        return result.stream().sorted(Comparator.comparing(ExternalProjectMappingEvidence::targetId)
                .thenComparing(Comparator.comparingInt(ExternalProjectMappingEvidence::score).reversed())).toList();
    }

    /** Descripción: Ejecuta la operación «topology» correspondiente a la responsabilidad de Classification. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ExternalProjectTopologyNode> topology(List<ExternalProjectResource> resources) {
        Map<String, ExternalProjectTopologyNode> nodes = new LinkedHashMap<>();
        nodes.put("project-root", new ExternalProjectTopologyNode("project-root", "Project", "PROJECT", "", "", List.of()));
        for(ExternalProjectResource resource : resources) {
            String clean = resource.path().replace("!/", "/");
            String[] parts = clean.split("/");
            String parent = "project-root";
            String current = "";
            for(int index = 0; index < parts.length - 1; index++) {
                current = current.isEmpty() ? parts[index] : current + "/" + parts[index];
                String id = "dir-" + sha256(current.getBytes(StandardCharsets.UTF_8)).substring(0, 12);
                nodes.putIfAbsent(id, new ExternalProjectTopologyNode(id, parts[index], "DIRECTORY", current, parent, List.of()));
                parent = id;
            }
            String id = "res-" + resource.sha256().substring(0, Math.min(12, resource.sha256().length()));
            nodes.put(id + '-' + nodes.size(), new ExternalProjectTopologyNode(id + '-' + nodes.size(), leaf(resource.path()),
                    resource.kind().name(), resource.path(), parent, resource.technologies()));
        }
        return List.copyOf(nodes.values());
    }

    /** Descripción: Ejecuta la operación «integrationSuggestions» correspondiente a la responsabilidad de Classification. Entradas: resources: dato de entrada necesario para la operación; technologies: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ExternalProjectIntegrationSuggestion> integrationSuggestions(List<ExternalProjectResource> resources,
            Set<String> technologies) {
        List<ExternalProjectIntegrationSuggestion> result = new ArrayList<>();
        Map<String, List<ExternalProjectResource>> byTechnology = new HashMap<>();
        for(ExternalProjectResource resource : resources) {
            for(String technology : resource.technologies()) byTechnology.computeIfAbsent(technology, key -> new ArrayList<>()).add(resource);
        }
        for(String technology : List.of("Xtext", "EMF", "Sirius", "GMF", "GLSP", "Langium", "BESSER", "ANTLR")) {
            List<ExternalProjectResource> matches = byTechnology.getOrDefault(technology, List.of());
            if(matches.isEmpty()) continue;
            String build = detectBuildSystem(resources);
            List<String> roots = detectSourceRoots(resources);
            String module = moduleName(matches, technology);
            int score = Math.min(98, 60 + Math.min(30, matches.size() * 4) + ("UNKNOWN".equals(build) ? 0 : 8));
            result.add(new ExternalProjectIntegrationSuggestion(technology, commonRoot(matches), module, build, roots,
                    generatedFolder(technology), matches.stream().limit(12).map(ExternalProjectResource::path).toList(), score,
                    "Detected " + matches.size() + " resource(s) that identify the host technology without manual mapping fields."));
        }
        return result.stream().sorted(Comparator.comparingInt(ExternalProjectIntegrationSuggestion::confidenceScore).reversed()
                .thenComparing(ExternalProjectIntegrationSuggestion::technology)).toList();
    }

    /** Descripción: Ejecuta la operación «detectBuildSystem» correspondiente a la responsabilidad de Classification. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String detectBuildSystem(List<ExternalProjectResource> resources) {
        Set<String> names = resources.stream().map(resource -> leaf(resource.path()).toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        if(names.contains("pom.xml")) return "MAVEN";
        if(names.contains("build.gradle") || names.contains("build.gradle.kts") || names.contains("settings.gradle") || names.contains("settings.gradle.kts")) return "GRADLE";
        if(names.contains("package.json")) return "NPM";
        if(names.contains("pyproject.toml") || names.contains("requirements.txt")) return "PYTHON";
        return "UNKNOWN";
    }

    /** Descripción: Ejecuta la operación «detectSourceRoots» correspondiente a la responsabilidad de Classification. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> detectSourceRoots(List<ExternalProjectResource> resources) {
        Set<String> result = new LinkedHashSet<>();
        for(ExternalProjectResource resource : resources) {
            String path = resource.path().replace("!/", "/");
            for(String marker : List.of("src/main/java", "src/main/kotlin", "src/main/resources", "src/main/ts", "src", "packages")) {
                int index = path.indexOf(marker + "/");
                if(index >= 0) result.add(path.substring(0, index + marker.length()));
            }
        }
        return result.stream().sorted().limit(12).toList();
    }

    /** Descripción: Ejecuta la operación «moduleName» correspondiente a la responsabilidad de Classification. Entradas: resources: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String moduleName(List<ExternalProjectResource> resources, String fallback) {
        String root = commonRoot(resources);
        if(!root.isBlank()) return leaf(root);
        for(ExternalProjectResource resource : resources) {
            String normalized = resource.path().replace("!/", "/");
            int slash = normalized.indexOf('/');
            if(slash > 0) return normalized.substring(0, slash);
        }
        return fallback.toLowerCase(Locale.ROOT);
    }

    /** Descripción: Ejecuta la operación «commonRoot» correspondiente a la responsabilidad de Classification. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String commonRoot(List<ExternalProjectResource> resources) {
        if(resources.isEmpty()) return "";
        List<String[]> parts = resources.stream().map(resource -> resource.path().replace("!/", "/").split("/")).toList();
        int length = parts.stream().mapToInt(value -> value.length).min().orElse(0);
        List<String> common = new ArrayList<>();
        for(int index = 0; index < Math.max(0, length - 1); index++) {
            String candidate = parts.getFirst()[index];
            int position = index;
            if(parts.stream().allMatch(value -> value[position].equals(candidate))) common.add(candidate); else break;
        }
        return String.join("/", common);
    }

    /** Descripción: Crea o prepara el resultado de «generatedFolder» dentro de Classification. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String generatedFolder(String technology) {
        return switch(technology) {
            case "Xtext", "EMF", "Sirius", "GMF" -> "src-gen";
            case "GLSP", "Langium" -> "generated";
            case "BESSER" -> "generated_models";
            default -> "generated";
        };
    }

    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de Classification. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fingerprint(List<ExternalProjectResource> resources) {
        StringBuilder material = new StringBuilder();
        resources.stream().sorted(Comparator.comparing(ExternalProjectResource::path))
                .forEach(resource -> material.append(resource.path()).append(':').append(resource.sha256()).append('\n'));
        return sha256(material.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Ejecuta la operación «normalizeElement» correspondiente a la responsabilidad de Classification. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeElement(String value) {
        String normalized = value == null ? "" : value.trim();
        if(normalized.contains("#//")) normalized = normalized.substring(normalized.lastIndexOf("#//") + 3);
        if(normalized.contains("/")) normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if(normalized.contains("::")) normalized = normalized.substring(normalized.lastIndexOf("::") + 2);
        return normalized.replaceAll("[^A-Za-z0-9_.-]", "");
    }

    /** Descripción: Evalúa la condición «isBuildDescriptor» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isBuildDescriptor(String lower) {
        String file = leaf(lower);
        return file.equals("pom.xml") || file.startsWith("build.gradle") || file.startsWith("settings.gradle")
                || file.equals("package.json") || file.equals("pyproject.toml") || file.equals("requirements.txt");
    }

    /** Descripción: Ejecuta la operación «addBuildTechnologies» correspondiente a la responsabilidad de Classification. Entradas: lower: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; tech: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addBuildTechnologies(String lower, byte[] content, List<String> tech) {
        String file = leaf(lower);
        if(file.equals("pom.xml")) tech.add("Maven");
        else if(file.contains("gradle")) tech.add("Gradle");
        else if(file.equals("package.json")) tech.add("Node.js");
        else tech.add("Python");
        String text = content.length > 1_000_000 ? "" : new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if(text.contains("xtext")) tech.add("Xtext");
        if(text.contains("glsp")) tech.add("GLSP");
        if(text.contains("langium")) tech.add("Langium");
        if(text.contains("besser")) tech.add("BESSER");
        if(text.contains("org.eclipse.emf")) tech.add("EMF");
    }

    /** Descripción: Evalúa la condición «isValidationSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isValidationSource(String lower) {
        return lower.endsWith(".evl") || lower.endsWith(".ocl") || lower.endsWith("validator.java")
                || lower.contains("/validation/") && isSource(lower);
    }

    /** Descripción: Evalúa la condición «isQuickFixSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isQuickFixSource(String lower) {
        return lower.contains("quickfix") && isSource(lower) || lower.contains("quick_fix") && isSource(lower);
    }

    /** Descripción: Evalúa la condición «isGlspSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isGlspSource(String lower, byte[] content) {
        if(lower.contains("glsp") && isSource(lower)) return true;
        if(!lower.endsWith("package.json") || content.length > 1_000_000) return false;
        return new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("glsp");
    }

    /** Descripción: Evalúa la condición «isBesserSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isBesserSource(String lower, byte[] content) {
        if(lower.contains("besser") && isSource(lower)) return true;
        if(!(lower.endsWith(".py") || lower.endsWith("pyproject.toml")) || content.length > 1_000_000) return false;
        return new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("besser");
    }

    /** Descripción: Evalúa la condición «isTestSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isTestSource(String lower) {
        return (lower.contains("/test/") || lower.contains("/tests/") || lower.contains("/example") || lower.contains("/examples/")) && isSource(lower);
    }

    /** Descripción: Evalúa la condición «isSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isSource(String lower) {
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".ts") || lower.endsWith(".tsx")
                || lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".py") || lower.endsWith(".go")
                || lower.endsWith(".xml") || lower.endsWith(".properties") || lower.endsWith(".md");
    }

    /** Descripción: Evalúa la condición «isLikelyText» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isLikelyText(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return isSource(lower) || lower.endsWith(".ecore") || lower.endsWith(".genmodel") || lower.endsWith(".xtext")
                || lower.endsWith(".langium") || lower.endsWith(".g4") || lower.endsWith(".odesign")
                || lower.matches(".*\\.gmf(?:graph|tool|map|gen)$") || isBuildDescriptor(lower);
    }

    /** Descripción: Ejecuta la operación «addLanguageTechnology» correspondiente a la responsabilidad de Classification. Entradas: lower: dato de entrada necesario para la operación; tech: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addLanguageTechnology(String lower, List<String> tech) {
        if(lower.endsWith(".java")) tech.add("Java");
        else if(lower.endsWith(".kt")) tech.add("Kotlin");
        else if(lower.endsWith(".ts") || lower.endsWith(".tsx")) tech.add("TypeScript");
        else if(lower.endsWith(".js") || lower.endsWith(".jsx")) tech.add("JavaScript");
        else if(lower.endsWith(".py")) tech.add("Python");
        else if(lower.endsWith(".go")) tech.add("Go");
    }

    /** Descripción: Evalúa la condición «isImage» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isImage(String lower) {
        return lower.endsWith(".svg") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    /** Descripción: Ejecuta la operación «leaf» correspondiente a la responsabilidad de Classification. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String leaf(String path) {
        String value = path == null ? "" : path.replace("!/", "/");
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de Classification. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record Classification(ExternalProjectResourceKind kind, List<String> technologies, String purpose) { }
}

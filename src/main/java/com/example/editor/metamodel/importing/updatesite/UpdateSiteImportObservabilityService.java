package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Genera logs profundos y explicaciones sin duplicar lógica de UI. */
@Component
public class UpdateSiteImportObservabilityService {
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteGrammarInspectionService grammarService;

    /** Descripción: Inicializa una instancia de UpdateSiteImportObservabilityService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; grammarService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteImportObservabilityService(UpdateSiteImportProperties properties,
            UpdateSiteGrammarInspectionService grammarService) {
        this.properties = properties;
        this.grammarService = grammarService;
    }

    /** Descripción: Crea o prepara el resultado de «build» dentro de UpdateSiteImportObservabilityService. Entradas: repository: dato de entrada necesario para la operación; summary: dato de entrada necesario para la operación; candidates: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación; provenance: dato de entrada necesario para la operación; layers: dato de entrada necesario para la operación; semanticRecovery: dato de entrada necesario para la operación; reconciliation: dato de entrada necesario para la operación; releases: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ UpdateSiteObservability build(UpdateSiteRepositoryContent repository, UpdateSiteRepositorySummary summary,
            List<UpdateSiteRecoveryCandidate> candidates, UpdateSiteRecoveryCandidate selected,
            List<UpdateSiteElementProvenance> provenance, UpdateSiteRecoveredLayers layers,
            UpdateSiteSemanticRecovery semanticRecovery, UpdateSiteReconciliationReport reconciliation,
            UpdateSiteReleaseComparison releases, List<String> diagnostics) {
        List<UpdateSiteGrammarInspection> grammars = grammarService.inspect(repository);
        List<UpdateSiteFileOutcome> outcomes = fileOutcomes(repository, candidates, selected, provenance, layers, semanticRecovery, grammars);
        List<UpdateSiteImportLogEntry> logs = logs(summary, selected, outcomes, grammars, layers, semanticRecovery, reconciliation, releases, diagnostics);
        return new UpdateSiteObservability(dashboard(summary, outcomes, provenance, logs),
                logs.stream().limit(properties.getMaxImportLogEntries()).toList(),
                outcomes.stream().limit(properties.getMaxFileOutcomes()).toList(), grammars);
    }

    /** Descripción: Ejecuta la operación «fileOutcomes» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: repository: dato de entrada necesario para la operación; candidates: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación; provenance: dato de entrada necesario para la operación; layers: dato de entrada necesario para la operación; semanticRecovery: dato de entrada necesario para la operación; grammars: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteFileOutcome> fileOutcomes(UpdateSiteRepositoryContent repository,
            List<UpdateSiteRecoveryCandidate> candidates, UpdateSiteRecoveryCandidate selected,
            List<UpdateSiteElementProvenance> provenance, UpdateSiteRecoveredLayers layers,
            UpdateSiteSemanticRecovery semanticRecovery, List<UpdateSiteGrammarInspection> grammars) {
        Map<String, Integer> recoveredCounts = new LinkedHashMap<>();
        Map<String, List<String>> related = new LinkedHashMap<>();
        provenance.forEach(item -> addRecovered(recoveredCounts, related, item.sourcePath(), item.elementPath()));
        layers.palettes().forEach(item -> addRecovered(recoveredCounts, related, item.sourcePath(), "Palette: " + item.label()));
        layers.representations().forEach(item -> addRecovered(recoveredCounts, related, item.sourcePath(), "Representation: " + item.name()));
        layers.mappings().forEach(item -> addRecovered(recoveredCounts, related, item.sourcePath(), "Mapping: " + item.semanticElement()));
        semanticRecovery.elements().forEach(item -> addRecovered(recoveredCounts, related, item.sourcePath(), item.kind() + ": " + item.label()));
        grammars.forEach(item -> addRecovered(recoveredCounts, related, item.sourcePath(), item.parserRules() + " parser rules"));
        if(selected != null) addRecovered(recoveredCounts, related, selected.sourcePath(), "Selected metamodel candidate");
        Set<String> candidatePaths = candidates.stream().map(UpdateSiteRecoveryCandidate::sourcePath).collect(java.util.stream.Collectors.toSet());

        List<UpdateSiteFileOutcome> result = new ArrayList<>();
        for(Map.Entry<String, byte[]> entry : repository.files().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String path = entry.getKey();
            UpdateSiteArtifactKind kind = UpdateSiteArtifactKinds.fromPath(path);
            int count = recoveredCounts.getOrDefault(path, 0);
            UpdateSiteFileStatus status;
            String recoveredAs = "";
            String explanation;
            String nextAction = "";
            if(count > 0) {
                status = candidatePaths.contains(path) && selected != null && !selected.sourcePath().equals(path)
                        ? UpdateSiteFileStatus.PARTIAL : UpdateSiteFileStatus.RECOVERED;
                recoveredAs = String.join(", ", related.getOrDefault(path, List.of()).stream().limit(4).toList());
                explanation = count + " neutral item(s) were recovered or traced from this file.";
            } else if(supporting(kind, path)) {
                status = UpdateSiteFileStatus.SUPPORTING;
                explanation = "The file supports discovery, classification or runtime registration but does not directly become a metamodel element.";
            } else if(kind == UpdateSiteArtifactKind.OTHER || kind == UpdateSiteArtifactKind.SIRIUS_SESSION) {
                status = UpdateSiteFileStatus.NOT_RECOVERABLE;
                explanation = "HYDRA recognized the file at a high level but cannot safely convert it into a neutral layer in this version.";
                nextAction = actionFor(kind, path);
            } else {
                status = UpdateSiteFileStatus.PARTIAL;
                explanation = "The artifact type is supported, but no reliable neutral element was obtained from this particular file.";
                nextAction = "Open the detailed log and verify whether dependencies or referenced resources are missing.";
            }
            result.add(new UpdateSiteFileOutcome(path, kind.name(), purpose(kind, path), status, count,
                    recoveredAs, explanation, nextAction, related.getOrDefault(path, List.of()).stream().limit(12).toList()));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «logs» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: summary: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación; outcomes: dato de entrada necesario para la operación; grammars: dato de entrada necesario para la operación; layers: dato de entrada necesario para la operación; semanticRecovery: dato de entrada necesario para la operación; reconciliation: dato de entrada necesario para la operación; releases: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteImportLogEntry> logs(UpdateSiteRepositorySummary summary,
            UpdateSiteRecoveryCandidate selected, List<UpdateSiteFileOutcome> outcomes,
            List<UpdateSiteGrammarInspection> grammars, UpdateSiteRecoveredLayers layers,
            UpdateSiteSemanticRecovery semanticRecovery, UpdateSiteReconciliationReport reconciliation, UpdateSiteReleaseComparison releases,
            List<String> diagnostics) {
        List<UpdateSiteImportLogEntry> result = new ArrayList<>();
        result.add(log("source", UpdateSiteImportStage.SOURCE, UpdateSiteLogSeverity.SUCCESS, UpdateSiteFileStatus.SUPPORTING,
                "Source accepted", summary.repositoryFileCount() + " files were expanded and inspected without executing bundle code.", "", "REPOSITORY",
                "Container of Eclipse p2 metadata, features, plug-ins and resources.", "Repository inventory", "Review the dashboard before importing.", UpdateSiteConfidence.EXACT, List.of()));
        result.add(log("metadata", UpdateSiteImportStage.P2_METADATA,
                summary.contentMetadataFound() ? UpdateSiteLogSeverity.SUCCESS : UpdateSiteLogSeverity.WARNING,
                UpdateSiteFileStatus.SUPPORTING, "p2 metadata",
                summary.contentMetadataFound() ? "content metadata was found and used to identify installable units." : "content metadata was missing; discovery used physical repository files.",
                "", "P2_METADATA", "Describes installable units, versions and requirements.", "Repository graph",
                summary.contentMetadataFound() ? "" : "Include content.xml/content.jar for a more exact dependency graph.", UpdateSiteConfidence.RECOVERED, List.of()));
        result.add(log("bundles", UpdateSiteImportStage.BUNDLES, UpdateSiteLogSeverity.SUCCESS, UpdateSiteFileStatus.SUPPORTING,
                "Bundle classification", summary.bundles().size() + " plug-ins were classified by role and dependency signals.", "", "OSGI_BUNDLES",
                "Separates model, edit, editor, GMF, Sirius, Xtext, runtime and third-party plug-ins.", "Bundle inventory", "Inspect ambiguous bundle signals if a role looks incorrect.", UpdateSiteConfidence.RECOVERED, List.of()));
        if(selected != null) result.add(log("selected", UpdateSiteImportStage.METAMODEL_RECOVERY, UpdateSiteLogSeverity.SUCCESS,
                UpdateSiteFileStatus.RECOVERED, "Selected recovery candidate",
                "Level " + selected.level() + " was selected using " + selected.method() + " with " + selected.confidence() + " confidence.",
                selected.sourcePath(), selected.format(), "Primary source used to reconstruct the neutral metamodel.",
                "Neutral metamodel preview", selected.confidence() == UpdateSiteConfidence.EXACT ? "" : "Review provenance and conflicts before treating it as authoritative.",
                selected.confidence(), selected.warnings()));
        for(UpdateSiteGrammarInspection grammar : grammars) {
            List<String> details = new ArrayList<>();
            details.add(grammar.parserRules() + " parser rule(s)");
            details.add(grammar.terminalRules() + " terminal/fragment rule(s)");
            details.add(grammar.assignments() + " assignment(s)");
            details.add(grammar.crossReferences() + " cross-reference(s)");
            details.add("Inferred entry rule: " + (grammar.inferredEntryRule().isBlank() ? "not resolved" : grammar.inferredEntryRule()) + " (" + grammar.entryInferenceMethod() + ", " + Math.round(grammar.entryInferenceConfidence() * 100d) + "% confidence)");
            if(!grammar.recursiveRules().isEmpty()) details.add("Recursive rules: " + String.join(", ", grammar.recursiveRules()));
            if(!grammar.automaticResolutionRules().isEmpty()) details.add("Automatic rewrite candidates: " + String.join(", ", grammar.automaticResolutionRules()));
            details.addAll(grammar.diagnostics());
            UpdateSiteLogSeverity severity = grammar.diagnostics().isEmpty() ? UpdateSiteLogSeverity.SUCCESS : UpdateSiteLogSeverity.WARNING;
            result.add(log("grammar:" + grammar.sourcePath(), UpdateSiteImportStage.GRAMMAR_ANALYSIS, severity,
                    grammar.parserRules() > 0 ? UpdateSiteFileStatus.RECOVERED : UpdateSiteFileStatus.PARTIAL,
                    grammar.language() + " grammar " + grammar.grammarName(),
                    "HYDRA inspected rules, assignments, cross-references, actions, duplicate names, entry-root inference and direct/indirect recursion.",
                    grammar.sourcePath(), grammar.language().toUpperCase(Locale.ROOT),
                    "Defines the concrete textual syntax and how text maps to semantic model elements.",
                    "Grammar-derived metamodel, palette and textual mappings",
                    grammar.diagnostics().isEmpty() ? "" : "Review the grammar diagnostics; unsupported syntax may reduce recovery coverage.",
                    UpdateSiteConfidence.DERIVED, details));
        }
        result.add(log("layers", UpdateSiteImportStage.LAYER_RECOVERY,
                layers.diagnostics().isEmpty() ? UpdateSiteLogSeverity.SUCCESS : UpdateSiteLogSeverity.WARNING,
                layers.palettes().isEmpty() && layers.representations().isEmpty() && layers.mappings().isEmpty()
                        ? UpdateSiteFileStatus.PARTIAL : UpdateSiteFileStatus.RECOVERED,
                "Editor layer recovery", layers.palettes().size() + " palette group(s), " + layers.representations().size()
                        + " representation(s) and " + layers.mappings().size() + " mapping(s) were drafted.",
                "", "GMF_SIRIUS_XTEXT", "Recovers how model elements are created and represented.",
                "Neutral editable layer drafts", "Materialize only the selected drafts after validating metamodel references.", UpdateSiteConfidence.RECOVERED, layers.diagnostics()));
        result.add(log("reconciliation", UpdateSiteImportStage.RECONCILIATION,
                reconciliation.conflicts().isEmpty() ? UpdateSiteLogSeverity.SUCCESS : UpdateSiteLogSeverity.WARNING,
                reconciliation.conflicts().isEmpty() ? UpdateSiteFileStatus.RECOVERED : UpdateSiteFileStatus.PARTIAL,
                "Candidate reconciliation", reconciliation.candidates().size() + " candidate(s) were compared across "
                        + reconciliation.unionElements() + " neutral element(s); " + reconciliation.conflicts().size() + " conflict(s) remain.",
                "", "RECONCILIATION", "Compares Ecore, GenModel, registry and bytecode evidence instead of silently choosing one source.",
                "Coverage and conflict report", reconciliation.conflicts().isEmpty() ? "" : "Choose a source for every blocking element conflict before merging.",
                UpdateSiteConfidence.RECOVERED, reconciliation.diagnostics()));
        result.add(log("semantic", UpdateSiteImportStage.SEMANTIC_RECOVERY,
                semanticRecovery.unrepresentableCount() == 0 ? UpdateSiteLogSeverity.SUCCESS : UpdateSiteLogSeverity.WARNING,
                semanticRecovery.elements().isEmpty() ? UpdateSiteFileStatus.PARTIAL : UpdateSiteFileStatus.RECOVERED,
                "Generated editor semantics", semanticRecovery.elements().size() + " command, menu, form, validation or runtime extension draft(s) were found; "
                        + semanticRecovery.unrepresentableCount() + " remain explicitly unrepresentable.", "", "PLUGIN_EXTENSIONS",
                "Explains what generated Edit/Editor/UI plug-ins add beyond the metamodel.", "Semantic editor drafts",
                semanticRecovery.unrepresentableCount() == 0 ? "" : "Review unrepresentable extensions; they are preserved rather than discarded.",
                UpdateSiteConfidence.RECOVERED, semanticRecovery.diagnostics()));
        if(!releases.compositeRepositories().isEmpty() || releases.versionFamilies().stream().anyMatch(UpdateSiteVersionFamily::multipleVersions)) {
            result.add(log("releases", UpdateSiteImportStage.RELEASE_ANALYSIS,
                    releases.unresolvedCompositeChildren() == 0 ? UpdateSiteLogSeverity.SUCCESS : UpdateSiteLogSeverity.WARNING,
                    releases.unresolvedCompositeChildren() == 0 ? UpdateSiteFileStatus.SUPPORTING : UpdateSiteFileStatus.PARTIAL,
                    "p2 releases and composite repositories",
                    releases.compositeRepositories().size() + " composite child reference(s) and "
                            + releases.versionFamilies().stream().filter(UpdateSiteVersionFamily::multipleVersions).count() + " multi-version family or families were analyzed.",
                    "", "P2_RELEASES", "Shows repository composition and version evolution without silently downloading remote children.",
                    "Release comparison", releases.unresolvedCompositeChildren() == 0 ? "" : "Upload unresolved child repositories for complete coverage.",
                    UpdateSiteConfidence.RECOVERED, releases.diagnostics()));
        }
        outcomes.stream().filter(value -> value.status() == UpdateSiteFileStatus.NOT_RECOVERABLE || value.status() == UpdateSiteFileStatus.FAILED)
                .limit(80).forEach(value -> result.add(log("file:" + value.sourcePath(), stageFor(UpdateSiteArtifactKinds.fromPath(value.sourcePath())),
                        value.status() == UpdateSiteFileStatus.FAILED ? UpdateSiteLogSeverity.ERROR : UpdateSiteLogSeverity.WARNING,
                        value.status(), "File not materialized", value.explanation(), value.sourcePath(), value.artifactKind(),
                        value.purpose(), value.recoveredAs(), value.nextAction(), UpdateSiteConfidence.INFERRED, value.relatedElements())));
        int diagnosticIndex = 0;
        for(String diagnostic : diagnostics) result.add(log("diagnostic:" + diagnosticIndex++, UpdateSiteImportStage.METAMODEL_RECOVERY,
                diagnostic.toLowerCase(Locale.ROOT).contains("could not") ? UpdateSiteLogSeverity.ERROR : UpdateSiteLogSeverity.WARNING,
                UpdateSiteFileStatus.PARTIAL, "Import diagnostic", diagnostic, "", "DIAGNOSTIC",
                "Explains a limitation or decision made during recovery.", "", "Review the related stage and source file.", UpdateSiteConfidence.INFERRED, List.of()));
        return result;
    }

    /** Descripción: Ejecuta la operación «dashboard» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: summary: dato de entrada necesario para la operación; outcomes: dato de entrada necesario para la operación; provenance: dato de entrada necesario para la operación; logs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteImportDashboard dashboard(UpdateSiteRepositorySummary summary,
            List<UpdateSiteFileOutcome> outcomes, List<UpdateSiteElementProvenance> provenance,
            List<UpdateSiteImportLogEntry> logs) {
        Map<UpdateSiteFileStatus, Long> statusCounts = new EnumMap<>(UpdateSiteFileStatus.class);
        outcomes.forEach(value -> statusCounts.merge(value.status(), 1L, Long::sum));
        Map<UpdateSiteConfidence, Long> confidence = new EnumMap<>(UpdateSiteConfidence.class);
        provenance.forEach(value -> confidence.merge(value.confidence(), 1L, Long::sum));
        Map<UpdateSiteImportStage, Long> stages = new EnumMap<>(UpdateSiteImportStage.class);
        logs.forEach(value -> stages.merge(value.stage(), 1L, Long::sum));
        long recovered = statusCounts.getOrDefault(UpdateSiteFileStatus.RECOVERED, 0L);
        long partial = statusCounts.getOrDefault(UpdateSiteFileStatus.PARTIAL, 0L);
        long supporting = statusCounts.getOrDefault(UpdateSiteFileStatus.SUPPORTING, 0L);
        long notRecoverable = statusCounts.getOrDefault(UpdateSiteFileStatus.NOT_RECOVERABLE, 0L);
        long failed = statusCounts.getOrDefault(UpdateSiteFileStatus.FAILED, 0L);
        double coverage = outcomes.isEmpty() ? 0 : (recovered + partial * 0.5) * 100.0 / outcomes.size();
        List<UpdateSiteDashboardSlice> roleSlices = summary.bundles().stream().collect(java.util.stream.Collectors.groupingBy(
                value -> value.kind().name(), LinkedHashMap::new, java.util.stream.Collectors.counting())).entrySet().stream()
                .map(item -> new UpdateSiteDashboardSlice(item.getKey(), human(item.getKey()), item.getValue(), "Bundles classified as " + human(item.getKey()) + '.')).toList();
        List<UpdateSiteSankeyNode> nodes = List.of(
                new UpdateSiteSankeyNode("source", "Repository files", 0, "Every expanded file in the uploaded update site."),
                new UpdateSiteSankeyNode("classified", "Classified artifacts", 1, "Files recognized as p2, Eclipse, metamodel or editor artifacts."),
                new UpdateSiteSankeyNode("recovered", "Recovered", 2, "Files that produced neutral elements or layers."),
                new UpdateSiteSankeyNode("supporting", "Supporting", 2, "Files used for discovery or runtime registration."),
                new UpdateSiteSankeyNode("partial", "Partial", 2, "Files with incomplete or lower-confidence recovery."),
                new UpdateSiteSankeyNode("unrecovered", "Not recovered", 2, "Files preserved in logs but not converted."));
        long classified = outcomes.stream().filter(value -> !value.artifactKind().equals("OTHER")).count();
        List<UpdateSiteSankeyLink> links = List.of(
                new UpdateSiteSankeyLink("source", "classified", classified, "Recognized by extension, descriptor name or bundle role."),
                new UpdateSiteSankeyLink("classified", "recovered", recovered, "Produced neutral metamodel, palette, representation, mapping or semantic elements."),
                new UpdateSiteSankeyLink("classified", "supporting", supporting, "Used to understand repository structure or runtime registration."),
                new UpdateSiteSankeyLink("classified", "partial", partial, "Only part of the intended information could be recovered."),
                new UpdateSiteSankeyLink("source", "unrecovered", notRecoverable + failed, "Preserved and explained but not converted."));
        return new UpdateSiteImportDashboard(outcomes.size(), recovered, partial, supporting, notRecoverable, failed, coverage,
                slices(confidence, "Elements reconstructed with %s confidence."), slices(statusCounts, "Files with %s outcome."),
                roleSlices, slices(stages, "Log entries produced during %s."), nodes, links);
    }

    /** Descripción: Ejecuta la operación «slices» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: values: colección de elementos que debe procesarse; explanation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <E extends Enum<E>> List<UpdateSiteDashboardSlice> slices(Map<E, Long> values, String explanation) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(item -> new UpdateSiteDashboardSlice(item.getKey().name(), human(item.getKey().name()), item.getValue(),
                        explanation.formatted(human(item.getKey().name()).toLowerCase(Locale.ROOT)))).toList();
    }

    /** Descripción: Ejecuta la operación «addRecovered» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: counts: dato de entrada necesario para la operación; related: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; item: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addRecovered(Map<String, Integer> counts, Map<String, List<String>> related, String path, String item) {
        if(path == null || path.isBlank()) return;
        counts.merge(path, 1, Integer::sum);
        related.computeIfAbsent(path, ignored -> new ArrayList<>()).add(item);
    }

    /** Descripción: Ejecuta la operación «supporting» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: kind: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean supporting(UpdateSiteArtifactKind kind, String path) {
        return switch(kind) {
            case PLUGIN_XML, FRAGMENT_XML, MANIFEST, BUILD_PROPERTIES, FEATURE_XML -> true;
            case JAVA_CLASS -> lower(path).contains("factory") || lower(path).contains("switch") || lower(path).contains("adapter");
            default -> lower(path).endsWith("content.xml") || lower(path).endsWith("artifacts.xml")
                    || lower(path).endsWith("site.xml") || lower(path).endsWith("category.xml")
                    || lower(path).contains("plugin.properties") || lower(path).endsWith("about.html")
                    || lower(path).endsWith("license.html") || lower(path).endsWith("notice.html");
        };
    }

    /** Descripción: Ejecuta la operación «purpose» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: kind: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String purpose(UpdateSiteArtifactKind kind, String path) {
        return switch(kind) {
            case ECORE -> "Original EMF metamodel. It contains packages, classes, attributes, references, operations, enums and data types.";
            case EMFATIC -> "Textual EMF/Emfatic metamodel that can recover packages, classes, features, operations and relationships through the canonical importer.";
            case UML -> "UML model that can be transformed into a neutral metamodel, although UML profiles and diagrams may not map exactly.";
            case XMI -> "XML Metadata Interchange document. It may serialize Ecore/UML models or ordinary model instances.";
            case XSD -> "XML Schema defining elements, attributes and XML types that can be derived into metamodel concepts.";
            case JSON, JSON_SCHEMA -> "JSON data or JSON Schema used to derive object types, properties and relationships.";
            case GRAPHQL -> "GraphQL schema defining object, scalar, enum and relationship fields.";
            case PROTO -> "Protocol Buffers schema defining messages, fields, enums and service signatures.";
            case GENMODEL -> "EMF generation model linking Ecore packages with generated Java packages and generation options.";
            case GMF_GRAPH -> "GMF graphical notation: figures, labels, compartments and connection visuals.";
            case GMF_TOOL -> "GMF tool definition: palette groups and creation tools.";
            case GMF_MAP -> "GMF mapping between semantic Ecore elements, visual figures and tools.";
            case GMF_GEN -> "GMF generator configuration used to produce the Eclipse diagram editor.";
            case SIRIUS_VIEWPOINT -> "Sirius viewpoint specification containing diagrams, mappings, tools and semantic expressions.";
            case SIRIUS_SESSION -> "Sirius session data referencing semantic models and representations; project-specific content may be unavailable.";
            case XTEXT_GRAMMAR, LANGIUM_GRAMMAR, ANTLR_GRAMMAR -> "Textual grammar defining syntax rules, assignments, cross-references and semantic element construction.";
            case ECORE_DIAGRAM -> "Ecore diagram/layout metadata that can help reconstruct a visual representation around the semantic metamodel.";
            case PLUGIN_XML -> "Eclipse extension registry: editors, commands, menus, validation, generated packages and runtime integrations.";
            case FRAGMENT_XML -> "Extension registry contributed by an Eclipse fragment to another host plug-in.";
            case MANIFEST -> "OSGi bundle identity, exported packages, required bundles and runtime classpath.";
            case BUILD_PROPERTIES -> "Files and source folders included when Eclipse builds the plug-in.";
            case FEATURE_XML -> "Eclipse feature grouping plug-ins into an installable product capability.";
            case CATEGORY_XML, SITE_XML -> "Update-site publication metadata describing installable categories, features and repository organization.";
            case TARGET_PLATFORM -> "Eclipse target-platform definition identifying repositories and dependency units used to build the product.";
            case ECLIPSE_PRODUCT -> "Eclipse product assembly and branding metadata.";
            case PROPERTIES -> "Localized labels, messages or build metadata. HYDRA uses nearby properties to resolve externalized Eclipse labels.";
            case OCL, EVL -> "Declarative validation source whose named invariants or constraints can be recovered without evaluating them.";
            case EOL -> "Epsilon operation source whose operation signatures can be recovered without executing the script.";
            case XCORE -> "Xcore metamodel source. HYDRA statically recovers classifiers, inheritance, attributes and references without executing repository code.";
            case TYPESCRIPT_SOURCE -> "TypeScript source declarations that can recover interfaces, classes, inheritance, attributes and relations as structural fallback evidence without executing the source.";
            case JAVASCRIPT_SOURCE -> "JavaScript source declarations that can recover classes, inheritance, fields and method signatures as structural fallback evidence without executing the source.";
            case GO_SOURCE -> "Go source declarations that can recover structs, interfaces, fields and relationships as structural fallback evidence without executing the source.";
            case JAVA_SOURCE, KOTLIN_SOURCE -> "Source declarations that can provide fallback class, relation, inheritance and operation evidence without executing repository code.";
            case PYTHON_SOURCE -> "Python source declarations that can be converted structurally by the existing non-executing source-model importer.";
            case BLOCKLY_JSON, BLOCKLY_JAVASCRIPT -> "Blockly block declarations that can recover structural block types and fields without running block behavior.";
            case PLANTUML -> "PlantUML class-diagram source that can recover classes, attributes and relationships as structural evidence.";
            case JAVA_CLASS -> "Compiled Java code. EMF package classes can recover structure; other classes mainly describe runtime behavior.";
            case OTHER -> purposeOther(path);
        };
    }

    /** Descripción: Ejecuta la operación «purposeOther» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String purposeOther(String path) {
        String lower = lower(path);
        if(lower.endsWith(".properties")) return "Localized labels, messages or build configuration used by plug-ins.";
        if(lower.matches(".*\\.(png|gif|jpg|jpeg|svg)$")) return "Icon or image used by palettes, editors, commands or generated UI.";
        if(lower.matches(".*\\.(html|md|txt|pdf)$")) return "Documentation, license, release notes or help content.";
        if(lower.endsWith(".exsd")) return "Eclipse extension-point schema documenting attributes accepted by an extension.";
        if(lower.endsWith(".product")) return "Eclipse product assembly and branding configuration.";
        if(lower.endsWith(".target")) return "Eclipse target-platform dependency definition.";
        if(lower.endsWith(".prefs")) return "Eclipse project or compiler preferences.";
        return "Repository resource not currently mapped to a neutral HYDRA artifact.";
    }

    /** Descripción: Ejecuta la operación «actionFor» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: kind: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String actionFor(UpdateSiteArtifactKind kind, String path) {
        String lower = lower(path);
        if(kind == UpdateSiteArtifactKind.SIRIUS_SESSION) return "Provide the referenced semantic models and viewpoint specification; session files alone are project-specific.";
        if(lower.matches(".*\\.(png|gif|jpg|jpeg|svg)$")) return "Keep it as a project asset and link it when materializing the recovered palette or representation.";
        if(lower.endsWith(".properties")) return "Use it to resolve externalized labels during semantic materialization.";
        if(lower.endsWith(".exsd")) return "Keep it as documentation for a custom extension point; no runtime behavior is inferred from it.";
        if(lower.matches(".*\\.(html|md|txt|pdf)$")) return "No import is required; retain it as documentation or licensing evidence.";
        return "Inspect the file manually or add a dedicated adapter if it represents a domain-specific capability.";
    }

    /** Descripción: Ejecuta la operación «stageFor» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteImportStage stageFor(UpdateSiteArtifactKind kind) {
        return switch(kind) {
            case XTEXT_GRAMMAR, LANGIUM_GRAMMAR, ANTLR_GRAMMAR -> UpdateSiteImportStage.GRAMMAR_ANALYSIS;
            case GMF_GRAPH, GMF_TOOL, GMF_MAP, GMF_GEN, SIRIUS_VIEWPOINT, SIRIUS_SESSION, ECORE_DIAGRAM -> UpdateSiteImportStage.LAYER_RECOVERY;
            case PLUGIN_XML, FRAGMENT_XML, JAVA_CLASS, JAVA_SOURCE, KOTLIN_SOURCE, OCL, EVL, EOL, PROPERTIES -> UpdateSiteImportStage.SEMANTIC_RECOVERY;
            case FEATURE_XML, CATEGORY_XML, SITE_XML, TARGET_PLATFORM, ECLIPSE_PRODUCT, MANIFEST, BUILD_PROPERTIES -> UpdateSiteImportStage.BUNDLES;
            default -> UpdateSiteImportStage.METAMODEL_RECOVERY;
        };
    }

    /** Descripción: Ejecuta la operación «log» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: id: identificador que delimita el recurso objetivo; stage: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; sourcePath: ruta o ubicación utilizada por la operación; artifactKind: dato de entrada necesario para la operación; purpose: dato de entrada necesario para la operación; recoveredAs: dato de entrada necesario para la operación; nextAction: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteImportLogEntry log(String id, UpdateSiteImportStage stage, UpdateSiteLogSeverity severity,
            UpdateSiteFileStatus status, String title, String message, String sourcePath, String artifactKind,
            String purpose, String recoveredAs, String nextAction, UpdateSiteConfidence confidence, List<String> details) {
        return new UpdateSiteImportLogEntry(id, stage, severity, status, title, message, sourcePath, artifactKind,
                purpose, recoveredAs, nextAction, confidence, details);
    }
    /** Descripción: Ejecuta la operación «human» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String human(String value) { return value.replace('_', ' ').toLowerCase(Locale.ROOT); }
    /** Descripción: Ejecuta la operación «lower» correspondiente a la responsabilidad de UpdateSiteImportObservabilityService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}

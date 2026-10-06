package com.example.editor.grammar.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.diagnostic.*;

/**
 * Analizador neutral y determinista usado como baseline común de Xtext, Langium y ANTLR.
 * Los providers pueden enriquecer este resultado con diagnósticos nativos, pero no sustituir su contrato.
 */
public final class GrammarSourceAnalyzer {
    private final GrammarProperties properties;

    /** Descripción: Inicializa una instancia de GrammarSourceAnalyzer con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarSourceAnalyzer() { this(new GrammarProperties()); }
    /** Descripción: Inicializa una instancia de GrammarSourceAnalyzer con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarSourceAnalyzer(GrammarProperties properties) { this.properties = properties == null ? new GrammarProperties() : properties; }

    private static final Pattern LANGIUM_XTEXT_DECL = Pattern.compile("(?m)^\\s*(?:(entry)\\s+)?(?:(terminal)\\s+)?(?:(fragment)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:returns\\s+[A-Za-z_][A-Za-z0-9_.<>]*)?\\s*:");
    private static final Pattern ANTLR_DECL = Pattern.compile("(?m)^\\s*(?:(fragment)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^]\\r\\n]*\\])?\\s*(?:returns\\s+\\[[^]\\r\\n]*\\])?\\s*:");
    private static final Pattern QUOTED_IMPORT = Pattern.compile("(?m)\\bimport\\s+['\"]([^'\"]+)['\"]");
    private static final Pattern ANTLR_IMPORT = Pattern.compile("(?m)^\\s*import\\s+([^;]+);");
    private static final Pattern CROSS_REFERENCE = Pattern.compile("\\[\\s*([A-Za-z_][A-Za-z0-9_.]*(?:::[A-Za-z_][A-Za-z0-9_.]*)*)");
    private static final Pattern ASSIGNMENT_REFERENCE = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(\\+?=)\\s*([A-Za-z_][A-Za-z0-9_.]*)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> BUILT_INS = Set.of("ID", "INT", "STRING", "NUMBER", "BOOLEAN", "EOF", "WS", "ANY_OTHER", "EString", "EInt", "EBoolean", "true", "false", "returns", "current", "hidden");
    private static final Set<String> ANTLR_RESERVED = Set.of("grammar", "lexer", "parser", "fragment", "returns", "locals", "throws", "catch", "finally", "mode", "channels", "tokens", "options", "import", "skip", "more", "type", "channel", "pushMode", "popMode");
    private static final Set<String> DECLARATIVE_RESERVED = Set.of("grammar", "entry", "terminal", "fragment", "returns", "infer", "generate", "with", "hidden", "import", "as", "current", "true", "false", "enum", "interface", "type");

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de Declaration. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarAnalysisResult analyze(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
        Objects.requireNonNull(grammar, "grammar");
        checkInterrupted();
        GrammarAnalysisRequest effective = request == null
                ? new GrammarAnalysisRequest(grammar.id(), 0, GrammarAnalysisMode.FAST, List.of(), grammar.documents(), grammar.toolchainProfileId())
                : request;
        List<GrammarSourceDocument> documents = effective.documents().isEmpty() ? grammar.documents() : effective.documents();
        List<GrammarDiagnostic> diagnostics = new ArrayList<>();
        List<GrammarCodeAction> actions = new ArrayList<>();
        List<GrammarSymbol> symbols = new ArrayList<>();
        Map<String, List<Declaration>> declarationsByName = new LinkedHashMap<>();
        Map<String, List<Declaration>> declarationsByDocument = new LinkedHashMap<>();
        Map<String, Set<String>> visibleDocuments = buildVisibility(documents, grammar.technology(), diagnostics, symbols);
        symbols.add(new GrammarSymbol("grammar:" + grammar.id(), grammar.name(), "GRAMMAR", "", new TextRange(null, null), "", grammar.technology().name()));

        for (GrammarSourceDocument document : documents) {
            checkInterrupted();
            symbols.add(new GrammarSymbol("document:" + document.id(), document.relativePath(), "DOCUMENT", document.id(),
                    new SourceTextIndex(document.content()).range(0, document.content().length()), "grammar:" + grammar.id(), document.languageId()));
            inspectSyntax(document, grammar.technology(), diagnostics);
            List<Declaration> declarations = declarations(document, grammar.technology());
            declarationsByDocument.put(document.id(), declarations);
            for (Declaration declaration : declarations) {
                declarationsByName.computeIfAbsent(declaration.name(), ignored -> new ArrayList<>()).add(declaration);
                symbols.add(declaration.symbol());
            }
            addStructuralSymbols(document, declarations, grammar.technology(), symbols);
        }

        for (Map.Entry<String, List<Declaration>> entry : declarationsByName.entrySet()) {
            checkInterrupted();
            if (entry.getValue().size() > 1) {
                for (Declaration duplicate : entry.getValue()) {
                    diagnostics.add(GrammarDiagnosticCatalog.simple(GrammarDiagnosticCatalog.DUPLICATE_SYMBOL, GrammarSeverity.ERROR,
                            "Símbolo de gramática duplicado", "El nombre \"" + entry.getKey() + "\" se declara más de una vez.",
                            "La resolución de referencias sería ambigua.", "No se promoverá un snapshot con símbolos duplicados.",
                            "Renombrar o eliminar una de las declaraciones duplicadas.", duplicate.documentId(), duplicate.range(), Map.of("symbol", entry.getKey())));
                }
            }
        }

        for (GrammarSourceDocument document : documents) {
            checkInterrupted();
            resolveReferences(grammar, document, declarationsByName, declarationsByDocument,
                    visibleDocuments.getOrDefault(document.id(), Set.of(document.id())), diagnostics, actions, symbols);
        }

        checkImportCycles(documents, grammar.technology(), diagnostics);
        GrammarDefinitionModel analysisGrammar = grammar.documents().equals(documents) ? grammar
                : new GrammarDefinitionModel(grammar.schemaVersion(), grammar.id(), grammar.name(), grammar.technology(), grammar.authoringMode(),
                        grammar.metamodelId(), grammar.toolchainProfileId(), grammar.entryDocumentId(), grammar.entryRule(), documents, grammar.semanticBindings(),
                        grammar.syntaxStyleDefinitionId(), grammar.settings(), grammar.annotations());
        GrammarRuleGraphAnalyzer.Analysis ruleGraph = GrammarRuleGraphAnalyzer.analyze(analysisGrammar);
        inspectEntryRule(analysisGrammar, ruleGraph, diagnostics);
        List<GrammarRuleGraphAnalyzer.Rule> unusedRules = inspectUnusedRules(analysisGrammar, ruleGraph, diagnostics);
        inspectRecursion(analysisGrammar, ruleGraph, diagnostics, actions);
        checkSemanticBindings(grammar, declarationsByName, diagnostics);

        List<GrammarDiagnostic> orderedDiagnostics = diagnostics.stream()
                .sorted(Comparator.comparing(GrammarDiagnostic::documentId)
                        .thenComparingInt(item -> item.range().start().line())
                        .thenComparingInt(item -> item.range().start().column())
                        .thenComparing(GrammarDiagnostic::code))
                .toList();
        if (orderedDiagnostics.size() > properties.getMaximumDiagnostics()) {
            int retained = Math.max(0, properties.getMaximumDiagnostics() - 1);
            List<GrammarDiagnostic> bounded = new ArrayList<>(orderedDiagnostics.subList(0, retained));
            bounded.add(GrammarDiagnosticCatalog.simple("GRAMMAR_DIAGNOSTIC_LIMIT", GrammarSeverity.WARNING,
                    "Se ha alcanzado el límite de diagnósticos", "Hay más problemas de los que el servidor está configurado para devolver en una sola revisión.",
                    "El límite evita que una gramática patológica agote memoria o bloquee la interfaz.", "El análisis sigue siendo seguro, pero la lista está truncada.",
                    "Corregir primero los problemas mostrados o aumentar el límite de forma consciente.", "", new TextRange(null,null), Map.of("total", Integer.toString(orderedDiagnostics.size()), "retained", Integer.toString(retained))));
            orderedDiagnostics = List.copyOf(bounded);
        }
        Map<String,String> analysisMetadata = new LinkedHashMap<>();
        analysisMetadata.put("providerMode", "HYDRA_NEUTRAL");
        analysisMetadata.put("technology", grammar.technology().name());
        analysisMetadata.put("documentCount", Integer.toString(documents.size()));
        analysisMetadata.put("entryRule.inferred", ruleGraph.entry().rule());
        analysisMetadata.put("entryRule.method", ruleGraph.entry().method());
        analysisMetadata.put("entryRule.confidence", String.format(Locale.ROOT, "%.3f", ruleGraph.entry().confidence()));
        analysisMetadata.put("entryRule.reason", ruleGraph.entry().reason());
        analysisMetadata.put("entryRule.candidates", String.join("|", ruleGraph.entry().candidates()));
        analysisMetadata.put("entryRule.ruleCount", Integer.toString(ruleGraph.rules().size()));
        analysisMetadata.put("recursion.count", Integer.toString(ruleGraph.recursions().size()));
        analysisMetadata.put("recursion.leftCount", Long.toString(ruleGraph.recursions().stream().filter(GrammarRuleGraphAnalyzer.Recursion::leftRecursive).count()));
        analysisMetadata.put("unusedRules.count", Integer.toString(unusedRules.size()));
        analysisMetadata.put("unusedRules.names", unusedRules.stream().map(GrammarRuleGraphAnalyzer.Rule::name).collect(Collectors.joining("|")));
        return new GrammarAnalysisResult(effective.draftRevision(), combinedHash(documents), effective.mode(), orderedDiagnostics,
                List.copyOf(symbols), semanticCandidate(grammar, declarationsByName), ProviderHealth.healthy(), List.copyOf(actions),
                Map.copyOf(analysisMetadata));
    }

    /** Descripción: Ejecuta la operación «inspectEntryRule» correspondiente a la responsabilidad de ReferenceOccurrence. Entradas: grammar: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void inspectEntryRule(GrammarDefinitionModel grammar, GrammarRuleGraphAnalyzer.Analysis graph, List<GrammarDiagnostic> diagnostics) {
        Set<String> names = new LinkedHashSet<>(graph.ruleNames());
        if (!grammar.entryRule().isBlank() && !names.contains(grammar.entryRule())) {
            diagnostics.add(GrammarDiagnosticCatalog.simple(GrammarDiagnosticCatalog.ENTRY_RULE_NOT_FOUND, GrammarSeverity.ERROR,
                    "La regla de entrada seleccionada ya no existe",
                    "La regla de entrada \"" + grammar.entryRule() + "\" no aparece entre las reglas de parser actuales.",
                    "La gramática ha cambiado o la selección manual apunta a una regla eliminada o renombrada.",
                    "No se puede promover un snapshot porque el runtime no sabría desde qué regla analizar un documento completo.",
                    graph.entry().rule().isBlank() ? "Seleccione una regla de parser existente." : "Volver a selección automática o elegir una de las reglas disponibles; HYDRA propone \"" + graph.entry().rule() + "\".",
                    grammar.entryDocumentId(), new TextRange(null,null), Map.of("selectedEntryRule", grammar.entryRule(), "inferredEntryRule", graph.entry().rule(), "inferenceMethod", graph.entry().method())));
        }
        long explicit = graph.rules().stream().filter(GrammarRuleGraphAnalyzer.Rule::explicitEntry).count();
        if (explicit > 1) {
            GrammarRuleGraphAnalyzer.Rule anchor = graph.rules().stream().filter(GrammarRuleGraphAnalyzer.Rule::explicitEntry).findFirst().orElse(graph.rules().get(0));
            diagnostics.add(GrammarDiagnosticCatalog.simple(GrammarDiagnosticCatalog.MULTIPLE_ENTRY_RULES, GrammarSeverity.WARNING,
                    "Hay varias reglas marcadas como entrada",
                    "HYDRA ha encontrado " + explicit + " reglas con marcador explícito de entrada.",
                    "Una gramática con varias raíces explícitas puede ser intencionada, pero el editor necesita una raíz principal para analizar documentos completos.",
                    "El usuario puede seguir seleccionando otra regla, pero HYDRA mantendrá una única regla principal en la configuración del editor.",
                    "Revise los marcadores entry y conserve solo los necesarios; la primera regla explícita se propone automáticamente.",
                    anchor.documentId(), anchor.nameRange(), Map.of("inferredEntryRule", graph.entry().rule(), "explicitEntryCount", Long.toString(explicit))));
        }
    }

    /** Descripción: Identifica reglas parser no alcanzables desde las entradas efectivas y genera advertencias no bloqueantes. Entradas: grammar: gramática analizada; graph: grafo de llamadas de reglas; diagnostics: colección de diagnósticos. Salida: devuelve las reglas no utilizadas en orden de declaración. */
    /** Descripción: Ejecuta la operación «inspectUnusedRules» correspondiente a la responsabilidad de Declaration. Entradas: grammar: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<GrammarRuleGraphAnalyzer.Rule> inspectUnusedRules(GrammarDefinitionModel grammar, GrammarRuleGraphAnalyzer.Analysis graph, List<GrammarDiagnostic> diagnostics) {
        if (graph.rules().isEmpty()) return List.of();
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        Set<String> ruleNames = new LinkedHashSet<>(graph.ruleNames());
        if (!grammar.entryRule().isBlank() && ruleNames.contains(grammar.entryRule())) roots.add(grammar.entryRule());
        else if (!graph.entry().rule().isBlank()) roots.add(graph.entry().rule());
        graph.rules().stream().filter(GrammarRuleGraphAnalyzer.Rule::explicitEntry).map(GrammarRuleGraphAnalyzer.Rule::name).forEach(roots::add);
        if (roots.isEmpty() && !graph.rules().isEmpty()) roots.add(graph.rules().get(0).name());

        LinkedHashSet<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!reachable.add(current)) continue;
            for (String target : graph.calls().getOrDefault(current, Set.of())) if (!reachable.contains(target)) pending.addLast(target);
        }

        List<GrammarRuleGraphAnalyzer.Rule> unused = graph.rules().stream()
                .filter(GrammarRuleGraphAnalyzer.Rule::parserRule)
                .filter(rule -> !reachable.contains(rule.name()))
                .sorted(Comparator.comparingInt(GrammarRuleGraphAnalyzer.Rule::order))
                .toList();
        String rootsText = roots.isEmpty() ? "la entrada inferida" : roots.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(", "));
        for (GrammarRuleGraphAnalyzer.Rule rule : unused) {
            diagnostics.add(GrammarDiagnosticCatalog.simple(GrammarDiagnosticCatalog.UNUSED_RULE, GrammarSeverity.WARNING,
                    "Regla de gramática no utilizada",
                    "La regla \"" + rule.name() + "\" no se utiliza desde ninguna entrada efectiva de la gramática.",
                    "No existe ningún camino de llamadas desde " + rootsText + " hasta esta regla. También puede formar parte de una isla de reglas que solo se invocan entre sí.",
                    "No bloquea la compilación, pero puede indicar código obsoleto, una regla desconectada por error o una entrada adicional que aún no se ha declarado.",
                    "Conecte la regla desde una regla alcanzable, conviértala en una entrada explícita si debe analizar documentos por separado o elimínela únicamente después de confirmar que no se necesita.",
                    rule.documentId(), rule.nameRange(), Map.of("rule", rule.name(), "effectiveEntries", String.join("|", roots), "unused", "true")));
        }
        return unused;
    }

    /** Descripción: Ejecuta la operación «inspectRecursion» correspondiente a la responsabilidad de ReferenceOccurrence. Entradas: grammar: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void inspectRecursion(GrammarDefinitionModel grammar, GrammarRuleGraphAnalyzer.Analysis graph,
            List<GrammarDiagnostic> diagnostics, List<GrammarCodeAction> actions) {
        int index = 0;
        for (GrammarRuleGraphAnalyzer.Recursion recursion : graph.recursions()) {
            String cycle = String.join(" → ", recursion.cycle());
            String rules = String.join(", ", recursion.rules());
            List<String> actionIds = new ArrayList<>();
            GrammarRuleGraphAnalyzer.Rewrite rewrite = recursion.rewrite();
            if (rewrite != null) {
                String id = "rewrite-left-recursion:" + rewrite.documentId() + ":" + (index++);
                actions.add(new GrammarCodeAction(id, "Reescribir automáticamente la recursión izquierda", "quickfix.grammar.left-recursion",
                        rewrite.safeAutomatic(), rewrite.safeAutomatic(), List.of(new GrammarTextEdit(rewrite.documentId(), rewrite.range(), rewrite.replacement())),
                        Map.of("draftSourceHash", grammar.documents().stream().filter(doc -> doc.id().equals(rewrite.documentId())).findFirst().map(GrammarSourceDocument::sourceHash).orElse("")),
                        Map.of("automaticResolution", "true", "semanticReviewRequired", Boolean.toString(!rewrite.safeAutomatic()), "detail", rewrite.detail())));
                actionIds.add(id);
            }
            Map<String,String> metadata = new LinkedHashMap<>();
            metadata.put("rule", recursion.rules().isEmpty() ? "" : recursion.rules().get(0));
            metadata.put("cycle", cycle);
            metadata.put("rules", rules);
            metadata.put("recursionKind", recursion.kind().name());
            metadata.put("automaticFixAvailable", Boolean.toString(rewrite != null));
            metadata.put("nativeSupported", Boolean.toString(recursion.nativeSupported()));
            metadata.put("nullable", Boolean.toString(recursion.nullable()));
            String technical = "HYDRA rule graph: " + cycle + "\nclassification=" + recursion.kind().name()
                    + "\ntechnology=" + grammar.technology().name() + "\nleft-corner=" + recursion.leftRecursive()
                    + "\nnullable-anchor=" + recursion.nullable() + (rewrite == null ? "" : "\nrewrite=" + rewrite.replacement() + "\n" + rewrite.detail());
            if (recursion.kind() == GrammarRuleGraphAnalyzer.RecursionKind.DIRECT_LEFT_NATIVE_SUPPORTED) {
                diagnostics.add(GrammarDiagnosticCatalog.recursion(GrammarDiagnosticCatalog.LEFT_RECURSION_NATIVE, GrammarSeverity.INFO,
                        "ANTLR admite esta recursión izquierda directa",
                        "La regla vuelve a invocarse antes de consumir otra regla o token: " + cycle + ".",
                        "ANTLR 4 puede transformar de forma nativa esta forma directa de recursión izquierda para construir el parser.",
                        "No es necesario reescribirla para compilar con el provider ANTLR, aunque HYDRA la conserva identificada al inferir el modelo semántico.",
                        "Mantenerla si expresa correctamente la precedencia/asociatividad deseada; solo reestructurarla si se necesita portabilidad a Xtext o Langium.",
                        recursion.documentId(), recursion.range(), actionIds, Map.copyOf(metadata), technical));
            } else if (recursion.leftRecursive()) {
                String kind = recursion.kind() == GrammarRuleGraphAnalyzer.RecursionKind.DIRECT_LEFT ? "directa" : "indirecta";
                diagnostics.add(GrammarDiagnosticCatalog.recursion(GrammarDiagnosticCatalog.LEFT_RECURSION, GrammarSeverity.ERROR,
                        "La gramática contiene recursión izquierda " + kind,
                        "Antes de consumir entrada, el parser puede volver a " + (recursion.rules().isEmpty()?"la misma cadena de reglas":"\"" + recursion.rules().get(0) + "\"") + ": " + cycle + ".",
                        "La expansión puede volver al mismo punto sin haber consumido ningún token. En un parser que no soporte esta forma, el análisis no puede avanzar de manera determinista.",
                        "El borrador se conserva, pero no sustituirá al último snapshot válido. HYDRA tampoco materializará una falsa contención a partir de este ciclo de control.",
                        rewrite != null ? "HYDRA puede preparar una reescritura sin recursión izquierda. Use Resolver automáticamente y revise el nuevo análisis antes de compilar."
                                : "Separe la precedencia en reglas no recursivas por la izquierda o introduzca una regla base que consuma entrada antes de volver al ciclo. La recursión indirecta requiere revisar conjuntamente las reglas afectadas.",
                        recursion.documentId(), recursion.range(), actionIds, Map.copyOf(metadata), technical));
            } else {
                diagnostics.add(GrammarDiagnosticCatalog.recursion(GrammarDiagnosticCatalog.RECURSION_SAFE, GrammarSeverity.INFO,
                        "Recursión de gramática identificada y válida",
                        "HYDRA ha identificado el ciclo " + cycle + ", pero existe consumo de entrada antes de volver al punto de origen.",
                        "La regla modela una estructura recursiva del lenguaje, no una recursión izquierda que bloquee el parser.",
                        "No bloquea la compilación. La relación recursiva se mantiene en el análisis semántico cuando corresponde.",
                        "No es necesario modificarla. Revísela únicamente si la profundidad esperada puede ser muy grande o si el provider nativo informa de un límite específico.",
                        recursion.documentId(), recursion.range(), List.of(), Map.copyOf(metadata), technical));
            }
        }
    }

    /** Descripción: Ejecuta la operación «declarations» correspondiente a la responsabilidad de Declaration. Entradas: document: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<Declaration> declarations(GrammarSourceDocument document, GrammarTechnology technology) {
        Pattern pattern = technology == GrammarTechnology.ANTLR ? ANTLR_DECL : LANGIUM_XTEXT_DECL;
        Matcher matcher = pattern.matcher(document.content());
        SourceTextIndex index = new SourceTextIndex(document.content());
        List<Declaration> result = new ArrayList<>();
        while (matcher.find()) {
            String name;
            String kind;
            int group;
            if (technology == GrammarTechnology.ANTLR) {
                name = matcher.group(2); group = 2;
                kind = Character.isUpperCase(name.charAt(0)) ? "TOKEN" : "RULE";
            } else {
                name = matcher.group(4); group = 4;
                kind = matcher.group(2) != null || matcher.group(3) != null ? "TOKEN" : "RULE";
            }
            TextRange range = index.range(matcher.start(group), matcher.end(group));
            String id = document.id() + ":" + kind.toLowerCase(Locale.ROOT) + ":" + name + ":" + matcher.start(group);
            result.add(new Declaration(name, kind, document.id(), range, id, matcher.start(), matcher.end()));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «addStructuralSymbols» correspondiente a la responsabilidad de Declaration. Entradas: document: dato de entrada necesario para la operación; declarations: dato de entrada necesario para la operación; symbols: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void addStructuralSymbols(GrammarSourceDocument document, List<Declaration> declarations, GrammarTechnology technology, List<GrammarSymbol> symbols) {
        String source = document.content();
        SourceTextIndex index = new SourceTextIndex(source);
        BitSet ignored = lexicalIgnored(source, technology != GrammarTechnology.ANTLR);
        for (int declarationIndex = 0; declarationIndex < declarations.size(); declarationIndex++) {
            Declaration declaration = declarations.get(declarationIndex);
            int bodyStart = Math.max(declaration.matchEnd(), source.indexOf(':', declaration.matchEnd() - 1) + 1);
            int bodyEnd = declarationIndex + 1 < declarations.size() ? declarations.get(declarationIndex + 1).matchStart() : source.length();
            int semicolon = findVisibleChar(source, ';', bodyStart, bodyEnd, ignored);
            if (semicolon >= 0 && semicolon < bodyEnd) bodyEnd = semicolon;
            if (bodyStart >= 0 && bodyStart <= bodyEnd) {
                int alternative = 1;
                symbols.add(new GrammarSymbol(document.id() + ":alternative:" + declaration.id() + ":1", "Alternativa 1", "ALTERNATIVE", document.id(), index.range(bodyStart, bodyStart), declaration.id(), "Alternative"));
                for (int offset = bodyStart; offset < bodyEnd; offset++) {
                    if (!ignored.get(offset) && source.charAt(offset) == '|') {
                        alternative++;
                        symbols.add(new GrammarSymbol(document.id() + ":alternative:" + declaration.id() + ":" + alternative, "Alternativa " + alternative, "ALTERNATIVE", document.id(), index.range(offset, offset + 1), declaration.id(), "Alternative"));
                    }
                }
            }
        }
        Matcher cross = CROSS_REFERENCE.matcher(source);
        while (cross.find()) {
            if (ignored.get(cross.start(1))) continue;
            Declaration parent = containing(declarations, cross.start()).orElse(null);
            String parentId = parent == null ? "document:" + document.id() : parent.id();
            String name = cross.group(1);
            symbols.add(new GrammarSymbol(document.id() + ":xref:" + cross.start(1), name, "CROSS_REFERENCE", document.id(),
                    index.range(cross.start(1), cross.end(1)), parentId, "Cross-reference"));
        }
        Matcher assignment = ASSIGNMENT_REFERENCE.matcher(source);
        while (assignment.find()) {
            if (ignored.get(assignment.start(1)) || ignored.get(assignment.start(3))) continue;
            Declaration parent = containing(declarations, assignment.start()).orElse(null);
            String parentId = parent == null ? "document:" + document.id() : parent.id();
            symbols.add(new GrammarSymbol(document.id() + ":assignment:" + assignment.start(1), assignment.group(1), "ASSIGNMENT", document.id(),
                    index.range(assignment.start(1), assignment.end(3)), parentId, assignment.group(2) + assignment.group(3)));
        }
    }

    /**
     * Descripción: comprueba delimitadores de la gramática sin interpretar como estructura los literales, comentarios ni regex terminales.
     * Entradas: document: documento fuente; technology: tecnología del provider; diagnostics: colección de diagnósticos a completar.
     * Salida: no devuelve valor; añade como máximo el diagnóstico sintáctico inmediato que impide continuar con seguridad.
     */
    /** Descripción: Ejecuta la operación «inspectSyntax» correspondiente a la responsabilidad de ReferenceOccurrence. Entradas: document: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void inspectSyntax(GrammarSourceDocument document, GrammarTechnology technology, List<GrammarDiagnostic> diagnostics) {
        String source = document.content();
        SourceTextIndex index = new SourceTextIndex(source);
        boolean slashRegex = technology != GrammarTechnology.ANTLR;
        BitSet ignored = lexicalIgnored(source, slashRegex);
        int unclosedLexical = unclosedLexicalOffset(source, slashRegex);
        if (unclosedLexical >= 0) {
            diagnostics.add(syntax(document.id(), index.range(unclosedLexical, Math.min(source.length(), unclosedLexical + 1)),
                    "Literal, comentario o expresión regular sin cerrar", "lexical-region"));
            return;
        }
        Deque<Character> stack = new ArrayDeque<>();
        Deque<Integer> offsets = new ArrayDeque<>();
        for (int i = 0; i < source.length(); i++) {
            if (ignored.get(i)) continue;
            char ch = source.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') {
                stack.push(ch);
                offsets.push(i);
            } else if (ch == ')' || ch == ']' || ch == '}') {
                char expected = ch == ')' ? '(' : ch == ']' ? '[' : '{';
                if (stack.isEmpty() || stack.peek() != expected) {
                    diagnostics.add(syntax(document.id(), index.range(i, i + 1), "Delimitador de cierre sin pareja", Character.toString(ch)));
                    return;
                }
                stack.pop();
                offsets.pop();
            }
        }
        if (!stack.isEmpty()) {
            diagnostics.add(syntax(document.id(), index.range(offsets.peek(), offsets.peek() + 1), "Delimitador sin cerrar", Character.toString(stack.peek())));
        }
    }

    /** Descripción: Ejecuta la operación «syntax» correspondiente a la responsabilidad de Declaration. Entradas: documentId: identificador que delimita el recurso objetivo; range: dato de entrada necesario para la operación; detail: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarDiagnostic syntax(String documentId, TextRange range, String detail, String token) {
        return GrammarDiagnosticCatalog.simple(GrammarDiagnosticCatalog.SYNTAX_ERROR, GrammarSeverity.ERROR,
                "La sintaxis de la gramática no está completa", detail + ".", "Hay delimitadores o literales sin pareja.",
                "El borrador se conserva, pero no reemplazará el último snapshot válido.", "Completar o corregir el fragmento señalado.",
                documentId, range, Map.of("token", token));
    }

    /** Descripción: Obtiene la información correspondiente a «resolveReferences» dentro de Declaration. Entradas: grammar: dato de entrada necesario para la operación; document: dato de entrada necesario para la operación; declarationsByName: dato de entrada necesario para la operación; declarationsByDocument: dato de entrada necesario para la operación; visible: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; symbols: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void resolveReferences(GrammarDefinitionModel grammar, GrammarSourceDocument document,
            Map<String, List<Declaration>> declarationsByName, Map<String, List<Declaration>> declarationsByDocument, Set<String> visible,
            List<GrammarDiagnostic> diagnostics, List<GrammarCodeAction> actions, List<GrammarSymbol> symbols) {
        String source = document.content();
        SourceTextIndex index = new SourceTextIndex(source);
        Set<String> emitted = new HashSet<>();
        List<ReferenceOccurrence> references = new ArrayList<>();
        BitSet ignored = lexicalIgnoredForReferences(source, grammar.technology());
        Matcher cross = CROSS_REFERENCE.matcher(source);
        while (cross.find()) { if (ignored.get(cross.start(1))) continue; references.add(new ReferenceOccurrence(cross.group(1), cross.start(1), cross.end(1), "RULE", "", containing(declarationsByDocument.getOrDefault(document.id(), List.of()), cross.start()).map(Declaration::name).orElse(""))); }
        Matcher assignment = ASSIGNMENT_REFERENCE.matcher(source);
        while (assignment.find()) { if (ignored.get(assignment.start(1)) || ignored.get(assignment.start(3))) continue; references.add(new ReferenceOccurrence(assignment.group(3), assignment.start(3), assignment.end(3), "", assignment.group(1), containing(declarationsByDocument.getOrDefault(document.id(), List.of()), assignment.start()).map(Declaration::name).orElse(""))); }
        List<Declaration> localDeclarations = declarationsByDocument.getOrDefault(document.id(), List.of());
        if (grammar.technology() == GrammarTechnology.ANTLR) references.addAll(antlrRuleReferences(source, localDeclarations));
        else references.addAll(declarativeRuleReferences(source, localDeclarations));

        for (ReferenceOccurrence occurrence : references) {
            String rawName = occurrence.name();
            String name = simpleName(rawName);
            if (BUILT_INS.contains(name) || name.isBlank()) continue;
            String key = occurrence.start() + ":" + occurrence.end() + ":" + name;
            if (!emitted.add(key)) continue;
            List<Declaration> exact = declarationsByName.getOrDefault(name, List.of());
            List<Declaration> exactVisible = exact.stream().filter(item -> visible.contains(item.documentId())).toList();
            if (exactVisible.size() == 1 && kindCompatible(occurrence.expectedKind(), exactVisible.get(0).kind())) continue;

            ReferenceDiagnosticCode code;
            if (exactVisible.size() > 1) code = ReferenceDiagnosticCode.REFERENCE_AMBIGUOUS;
            else if (exactVisible.size() == 1) code = ReferenceDiagnosticCode.REFERENCE_WRONG_TYPE;
            else if (!exact.isEmpty()) code = visible.size() <= 1 ? ReferenceDiagnosticCode.REFERENCE_IMPORT_MISSING : ReferenceDiagnosticCode.REFERENCE_NOT_VISIBLE;
            else if (declarationsByName.keySet().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(name))) code = ReferenceDiagnosticCode.REFERENCE_CASE_MISMATCH;
            else code = ReferenceDiagnosticCode.REFERENCE_NOT_FOUND;

            List<ReferenceCandidate> candidates = rankCandidates(grammar, rawName, name, occurrence.expectedKind(), document.id(), occurrence.start(), visible, declarationsByName);
            List<String> actionIds = new ArrayList<>();
            if (!candidates.isEmpty()) {
                ReferenceCandidate best = candidates.get(0);
                double margin = candidates.size() == 1 ? best.score() : best.score() - candidates.get(1).score();
                boolean safe = best.score() >= properties.getAutomaticFixMinimumScore() && margin >= properties.getAutomaticFixMinimumMargin() && (occurrence.expectedKind().isBlank() || kindCompatible(occurrence.expectedKind(), best.kind()));
                String actionId = "replace-reference:" + document.id() + ":" + occurrence.start() + ":" + best.name();
                actions.add(new GrammarCodeAction(actionId, "Cambiar \"" + rawName + "\" por \"" + best.name() + "\"", "quickfix.reference.replace",
                        safe, safe, List.of(new GrammarTextEdit(document.id(), index.range(occurrence.start(), occurrence.end()), best.name())),
                        Map.of("draftSourceHash", document.sourceHash()), Map.of("candidateScore", Double.toString(best.score()))));
                actionIds.add(actionId);
            }
            TextRange range = index.range(occurrence.start(), occurrence.end());
            diagnostics.add(GrammarDiagnosticCatalog.reference(code, rawName, document.id(), range, occurrence.rule(), occurrence.property(),
                    occurrence.expectedKind(), candidates, actionIds, "", "Neutral resolver: " + grammar.technology().name()));
        }
    }

    /** Descripción: Extrae referencias declarativas ignorando literales, comentarios y expresiones regulares terminales. Entradas: source: fuente; declarations: reglas declaradas. Salida: devuelve solo referencias sintácticamente plausibles, evitando falsos positivos dentro de regex. */ private static List<ReferenceOccurrence> declarativeRuleReferences(String source, List<Declaration> declarations) {
        List<ReferenceOccurrence> result = new ArrayList<>();
        BitSet ignored = lexicalIgnored(source, true);
        for (int declarationIndex = 0; declarationIndex < declarations.size(); declarationIndex++) {
            Declaration declaration = declarations.get(declarationIndex);
            int colon = source.indexOf(':', Math.max(0, declaration.matchEnd() - 1));
            if (colon < 0) continue;
            int nextDeclaration = declarationIndex + 1 < declarations.size() ? declarations.get(declarationIndex + 1).matchStart() : source.length();
            int semi = findVisibleChar(source, ';', colon + 1, nextDeclaration, ignored);
            int end = semi >= 0 ? semi : nextDeclaration;
            for (int i = colon + 1; i < end;) {
                if (ignored.get(i) || !Character.isJavaIdentifierStart(source.charAt(i))) { i++; continue; }
                int start = i++;
                while (i < end && !ignored.get(i) && Character.isJavaIdentifierPart(source.charAt(i))) i++;
                String name = source.substring(start, i);
                if (BUILT_INS.contains(name) || DECLARATIVE_RESERVED.contains(name)) continue;
                int before = start - 1;
                while (before > colon && Character.isWhitespace(source.charAt(before))) before--;
                int after = i;
                while (after < end && Character.isWhitespace(source.charAt(after))) after++;
                boolean qualifiedPrefix = after + 1 < end && source.charAt(after) == ':' && source.charAt(after + 1) == ':';
                boolean qualifiedSuffix = before - 1 >= colon + 1 && source.charAt(before) == ':' && source.charAt(before - 1) == ':';
                if (qualifiedPrefix || qualifiedSuffix) continue;
                if (after < end && source.charAt(after) == '=') continue;
                if (after + 1 < end && source.charAt(after) == '+' && source.charAt(after + 1) == '=') continue;
                if (after + 1 < end && source.charAt(after) == '?' && source.charAt(after + 1) == '=') continue;
                if (after < end && source.charAt(after) == '.') continue;
                if (before >= colon + 1 && source.charAt(before) == '{') continue;
                result.add(new ReferenceOccurrence(name, start, i, "", "", declaration.name()));
            }
        }
        return result;
    }

    /**
     * Descripción: busca un carácter fuera de literales, comentarios y regex declarativas.
     * Entradas: source: fuente; target: carácter; start/end: ventana de búsqueda.
     * Salida: offset visible o -1 cuando no existe.
     */
    /** Descripción: Obtiene la información correspondiente a «findUnquoted» dentro de ReferenceOccurrence. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; start: dato de entrada necesario para la operación; end: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int findUnquoted(String source, char target, int start, int end) {
        return findVisibleChar(source, target, start, end, lexicalIgnored(source, true));
    }

    /** Descripción: Ejecuta la operación «antlrRuleReferences» correspondiente a la responsabilidad de Declaration. Entradas: source: dato de entrada necesario para la operación; declarations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<ReferenceOccurrence> antlrRuleReferences(String source, List<Declaration> declarations) {
        List<ReferenceOccurrence> result = new ArrayList<>();
        BitSet ignored = lexicalIgnoredForReferences(source, GrammarTechnology.ANTLR);
        for (Declaration declaration : declarations) {
            int colon = source.indexOf(':', declaration.matchEnd() - 1);
            if (colon < 0) continue;
            int semi = source.indexOf(';', colon + 1);
            if (semi < 0) semi = source.length();
            String body = source.substring(colon + 1, semi);
            Matcher ids = IDENTIFIER.matcher(body);
            while (ids.find()) {
                String name = ids.group();
                if (ANTLR_RESERVED.contains(name) || BUILT_INS.contains(name)) continue;
                int absoluteStart = colon + 1 + ids.start();
                if (ignored.get(absoluteStart)) continue;
                result.add(new ReferenceOccurrence(name, absoluteStart, colon + 1 + ids.end(), "", "", declaration.name()));
            }
        }
        return result;
    }

    /** Returns lexical regions that cannot contain rule references. ANTLR character classes are data, not rule names. */
    /** Descripción: Ejecuta la operación «lexicalIgnoredForReferences» correspondiente a la responsabilidad de Declaration. Entradas: source: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static BitSet lexicalIgnoredForReferences(String source, GrammarTechnology technology) {
        BitSet ignored = lexicalIgnored(source, technology != GrammarTechnology.ANTLR);
        if (technology != GrammarTechnology.ANTLR) return ignored;
        boolean inClass = false;
        boolean escape = false;
        for (int i = 0; i < source.length(); i++) {
            if (ignored.get(i) && !inClass) continue;
            char ch = source.charAt(i);
            if (inClass) {
                ignored.set(i);
                if (escape) { escape = false; continue; }
                if (ch == '\\') { escape = true; continue; }
                if (ch == ']') inClass = false;
                continue;
            }
            if (ch == '[') { inClass = true; ignored.set(i); }
        }
        return ignored;
    }

    /**
     * Descripción: marca regiones que no pueden contener referencias de reglas: literales, comentarios y, para Xtext/Langium, regex delimitadas por barra.
     * Entradas: source: fuente; slashRegex: activa el reconocimiento de regex declarativas delimitadas por barra.
     * Salida: mapa de offsets ignorables estable y seguro incluso con escapes o regiones sin cerrar.
     */
    /** Descripción: Ejecuta la operación «lexicalIgnored» correspondiente a la responsabilidad de ReferenceOccurrence. Entradas: source: dato de entrada necesario para la operación; slashRegex: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static BitSet lexicalIgnored(String source, boolean slashRegex) {
        BitSet ignored = new BitSet(source.length());
        final int normal = 0, single = 1, doubleQuoted = 2, lineComment = 3, blockComment = 4, slashPattern = 5, antlrBracket = 6;
        int state = normal;
        boolean escape = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (state == lineComment) {
                if (ch == '\n' || ch == '\r') state = normal;
                else ignored.set(i);
                continue;
            }
            if (state == blockComment) {
                ignored.set(i);
                if (ch == '*' && next == '/') {
                    ignored.set(i + 1);
                    i++;
                    state = normal;
                }
                continue;
            }
            if (state != normal) {
                ignored.set(i);
                if (escape) {
                    escape = false;
                    continue;
                }
                if (ch == '\\') {
                    escape = true;
                    continue;
                }
                if ((state == single && ch == '\'') || (state == doubleQuoted && ch == '"') || (state == slashPattern && ch == '/') || (state == antlrBracket && ch == ']')) {
                    state = normal;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                ignored.set(i);
                state = ch == '\'' ? single : doubleQuoted;
                continue;
            }
            if (!slashRegex && ch == '[') {
                ignored.set(i);
                state = antlrBracket;
                continue;
            }
            if (ch == '/' && next == '/') {
                ignored.set(i);
                ignored.set(i + 1);
                i++;
                state = lineComment;
                continue;
            }
            if (ch == '/' && next == '*') {
                ignored.set(i);
                ignored.set(i + 1);
                i++;
                state = blockComment;
                continue;
            }
            if (slashRegex && ch == '/' && looksLikeRegexStart(source, i)) {
                ignored.set(i);
                state = slashPattern;
            }
        }
        return ignored;
    }

    /**
     * Descripción: localiza una región léxica sin cerrar para conservar el diagnóstico sintáctico sin inspeccionar su contenido como gramática.
     * Entradas: source: fuente; slashRegex: activa regex declarativas delimitadas por barra.
     * Salida: offset de apertura no cerrada o -1; los comentarios de línea pueden finalizar válidamente al final del archivo.
     */
    /** Descripción: Ejecuta la operación «unclosedLexicalOffset» correspondiente a la responsabilidad de ReferenceOccurrence. Entradas: source: dato de entrada necesario para la operación; slashRegex: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int unclosedLexicalOffset(String source, boolean slashRegex) {
        final int normal = 0, single = 1, doubleQuoted = 2, lineComment = 3, blockComment = 4, slashPattern = 5;
        int state = normal;
        int openedAt = -1;
        boolean escape = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (state == lineComment) {
                if (ch == '\n' || ch == '\r') { state = normal; openedAt = -1; }
                continue;
            }
            if (state == blockComment) {
                if (ch == '*' && next == '/') { i++; state = normal; openedAt = -1; }
                continue;
            }
            if (state != normal) {
                if (escape) { escape = false; continue; }
                if (ch == '\\') { escape = true; continue; }
                if ((state == single && ch == '\'') || (state == doubleQuoted && ch == '"') || (state == slashPattern && ch == '/')) {
                    state = normal;
                    openedAt = -1;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') { state = ch == '\'' ? single : doubleQuoted; openedAt = i; continue; }
            if (ch == '/' && next == '/') { state = lineComment; openedAt = i; i++; continue; }
            if (ch == '/' && next == '*') { state = blockComment; openedAt = i; i++; continue; }
            if (slashRegex && ch == '/' && looksLikeRegexStart(source, i)) { state = slashPattern; openedAt = i; }
        }
        return state == normal || state == lineComment ? -1 : openedAt;
    }

    /**
     * Descripción: evita confundir separadores ordinarios con el comienzo de una regex terminal de Xtext/Langium.
     * Entradas: source: fuente; offset: posición de la barra candidata.
     * Salida: true cuando el contexto inmediato admite una expresión regular declarativa.
     */
    /** Descripción: Ejecuta la operación «looksLikeRegexStart» correspondiente a la responsabilidad de ReferenceOccurrence. Entradas: source: dato de entrada necesario para la operación; offset: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean looksLikeRegexStart(String source, int offset) {
        if (offset < 0 || offset >= source.length() || source.charAt(offset) != '/') return false;
        int previous = offset - 1;
        while (previous >= 0 && Character.isWhitespace(source.charAt(previous))) previous--;
        if (previous < 0) return true;
        char before = source.charAt(previous);
        return before == ':' || before == '=' || before == '|' || before == '(' || before == ',' || before == ';';
    }

    /** Descripción: Busca un carácter estructural fuera de regiones léxicas ignoradas. Entradas: source, target, start, end e ignored. Salida: offset encontrado o -1. */ private static int findVisibleChar(String source, char target, int start, int end, BitSet ignored) {
        for (int i = Math.max(0, start); i < Math.min(source.length(), end); i++) if (!ignored.get(i) && source.charAt(i) == target) return i;
        return -1;
    }

    /** Descripción: Ejecuta la operación «rankCandidates» correspondiente a la responsabilidad de Declaration. Entradas: grammar: dato de entrada necesario para la operación; rawReference: dato de entrada necesario para la operación; reference: dato de entrada necesario para la operación; expectedKind: dato de entrada necesario para la operación; sourceDocument: dato de entrada necesario para la operación; sourceOffset: dato de entrada necesario para la operación; visible: dato de entrada necesario para la operación; declarationsByName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ReferenceCandidate> rankCandidates(GrammarDefinitionModel grammar, String rawReference, String reference, String expectedKind,
            String sourceDocument, int sourceOffset, Set<String> visible, Map<String, List<Declaration>> declarationsByName) {
        List<ReferenceCandidate> candidates = new ArrayList<>();
        Set<String> semanticTargets = grammar.semanticBindings().bindings().stream()
                .filter(binding -> expectedKind.isBlank() || binding.grammarElementKind().equalsIgnoreCase(expectedKind) || binding.semanticElementKind().equalsIgnoreCase(expectedKind))
                .map(GrammarSemanticBinding::grammarElementId).collect(Collectors.toSet());
        String qualifiedTail = rawReference.contains(".") ? rawReference.substring(rawReference.lastIndexOf('.') + 1) : rawReference;
        for (List<Declaration> declarations : declarationsByName.values()) {
            for (Declaration declaration : declarations) {
                double nameScore = similarity(reference, declaration.name());
                double typeScore = expectedKind.isBlank() ? 0.5 : kindCompatible(expectedKind, declaration.kind()) ? 1.0 : 0.0;
                double scopeScore = visible.contains(declaration.documentId()) ? 1.0 : 0.20;
                double sameDocumentScore = sourceDocument.equals(declaration.documentId()) ? 1.0 : 0.0;
                double caseScore = reference.equalsIgnoreCase(declaration.name()) ? 1.0 : 0.0;
                double qualifiedScore = qualifiedTail.equals(declaration.name()) ? 1.0 : similarity(qualifiedTail, declaration.name());
                double proximityScore = sourceDocument.equals(declaration.documentId()) ? 1d / (1d + Math.abs((double)sourceOffset - declaration.matchStart()) / 500d) : 0.25;
                double semanticScore = semanticTargets.contains(declaration.id()) ? 1.0 : semanticTargets.isEmpty() ? 0.5 : 0.0;
                double total = clamp(nameScore * 0.42 + typeScore * 0.14 + scopeScore * 0.10 + sameDocumentScore * 0.07 + caseScore * 0.06 + qualifiedScore * 0.08 + proximityScore * 0.06 + semanticScore * 0.07);
                if (total < 0.35) continue;
                Map<String,Double> breakdown = Map.of("name", nameScore, "type", typeScore, "scope", scopeScore, "sameDocument", sameDocumentScore,
                        "case", caseScore, "qualifiedName", qualifiedScore, "structuralProximity", proximityScore, "semanticBinding", semanticScore);
                String reason = scopeScore == 1.0 ? (typeScore == 1.0 ? "mismo tipo esperado y visible" : "nombre compatible y visible") : "candidato compatible fuera del scope actual";
                candidates.add(new ReferenceCandidate(declaration.name(), declaration.id(), declaration.kind(), declaration.documentId(), total, reason, breakdown));
            }
        }
        return candidates.stream().sorted(Comparator.comparingDouble(ReferenceCandidate::score).reversed().thenComparing(ReferenceCandidate::name).thenComparing(ReferenceCandidate::symbolId)).limit(properties.getMaximumReferenceCandidates()).toList();
    }

    /** Descripción: Crea o prepara el resultado de «buildVisibility» dentro de Declaration. Entradas: documents: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; symbols: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Set<String>> buildVisibility(List<GrammarSourceDocument> documents, GrammarTechnology technology,
            List<GrammarDiagnostic> diagnostics, List<GrammarSymbol> symbols) {
        Map<String, GrammarSourceDocument> byId = documents.stream().collect(Collectors.toMap(GrammarSourceDocument::id, item -> item, (a,b)->a, LinkedHashMap::new));
        Map<String, GrammarSourceDocument> byPath = documents.stream().collect(Collectors.toMap(item -> normalizePath(item.relativePath()), item -> item, (a,b)->a, LinkedHashMap::new));
        Map<String, Set<String>> direct = new LinkedHashMap<>();
        for (GrammarSourceDocument document : documents) {
            Set<String> visible = new LinkedHashSet<>(); visible.add(document.id());
            SourceTextIndex index = new SourceTextIndex(document.content());
            for (ImportOccurrence occurrence : imports(document, technology)) {
                GrammarSourceDocument target = resolveImport(occurrence.target(), byId, byPath);
                symbols.add(new GrammarSymbol(document.id() + ":import:" + occurrence.start(), occurrence.target(), "IMPORT", document.id(), index.range(occurrence.start(), occurrence.end()), "document:" + document.id(), target == null ? "unresolved" : target.relativePath()));
                if (target != null) visible.add(target.id());
                else if (looksLocalImport(occurrence.target(), technology)) diagnostics.add(GrammarDiagnosticCatalog.simple(GrammarDiagnosticCatalog.INVALID_IMPORT, GrammarSeverity.ERROR,
                        "No se encuentra el documento importado", "El import \"" + occurrence.target() + "\" no se puede resolver dentro de la gramática.",
                        "El path o nombre del documento importado no coincide con ninguna fuente disponible.", "Las referencias proporcionadas por ese documento no serán visibles.",
                        "Corregir el import o añadir el documento que falta.", document.id(), index.range(occurrence.start(), occurrence.end()), Map.of("import", occurrence.target())));
            }
            direct.put(document.id(), visible);
        }
        Map<String, Set<String>> transitive = new LinkedHashMap<>();
        for (GrammarSourceDocument document : documents) {
            Set<String> closure = new LinkedHashSet<>();
            collectVisible(document.id(), direct, closure, 0);
            transitive.put(document.id(), Set.copyOf(closure));
        }
        return transitive;
    }

    /** Descripción: Ejecuta la operación «collectVisible» correspondiente a la responsabilidad de Declaration. Entradas: id: identificador que delimita el recurso objetivo; direct: dato de entrada necesario para la operación; closure: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectVisible(String id, Map<String, Set<String>> direct, Set<String> closure, int depth) {
        checkInterrupted();
        if (depth > properties.getMaximumImportDepth()) return;
        if (!closure.add(id)) return;
        for (String target : direct.getOrDefault(id, Set.of())) if (!target.equals(id)) collectVisible(target, direct, closure, depth + 1);
    }

    /** Descripción: Valida las condiciones necesarias para «checkImportCycles» y rechaza entradas no válidas. Entradas: documents: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkImportCycles(List<GrammarSourceDocument> documents, GrammarTechnology technology, List<GrammarDiagnostic> diagnostics) {
        Map<String, GrammarSourceDocument> byId = documents.stream().collect(Collectors.toMap(GrammarSourceDocument::id, item->item));
        Map<String, GrammarSourceDocument> byPath = documents.stream().collect(Collectors.toMap(item->normalizePath(item.relativePath()), item->item, (a,b)->a));
        Map<String,List<String>> graph = new LinkedHashMap<>();
        for (GrammarSourceDocument document : documents) {
            List<String> targets = imports(document, technology).stream().map(item -> resolveImport(item.target(), byId, byPath)).filter(Objects::nonNull).map(GrammarSourceDocument::id).toList();
            graph.put(document.id(), targets);
        }
        Set<String> visited = new HashSet<>(), active = new LinkedHashSet<>();
        for (String id : graph.keySet()) detectCycle(id, graph, visited, active, diagnostics, byId, 0);
    }

    /** Descripción: Ejecuta la operación «detectCycle» correspondiente a la responsabilidad de Declaration. Entradas: id: identificador que delimita el recurso objetivo; graph: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación; active: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; byId: identificador que delimita el recurso objetivo; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void detectCycle(String id, Map<String,List<String>> graph, Set<String> visited, Set<String> active,
            List<GrammarDiagnostic> diagnostics, Map<String, GrammarSourceDocument> byId, int depth) {
        checkInterrupted();
        if (depth > properties.getMaximumImportDepth()) return;
        if (active.contains(id)) {
            GrammarSourceDocument document = byId.get(id);
            diagnostics.add(GrammarDiagnosticCatalog.reference(ReferenceDiagnosticCode.REFERENCE_CYCLE, id, id,
                    new TextRange(new TextPosition(0,0), new TextPosition(0,0)), "", "import", "DOCUMENT", List.of(), List.of(), "", "Import cycle: " + active));
            return;
        }
        if (!visited.add(id)) return;
        active.add(id);
        for (String next : graph.getOrDefault(id, List.of())) detectCycle(next, graph, visited, active, diagnostics, byId, depth + 1);
        active.remove(id);
    }

    /** Descripción: Valida las condiciones necesarias para «checkSemanticBindings» y rechaza entradas no válidas. Entradas: grammar: dato de entrada necesario para la operación; declarationsByName: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void checkSemanticBindings(GrammarDefinitionModel grammar, Map<String, List<Declaration>> declarationsByName, List<GrammarDiagnostic> diagnostics) {
        Map<String, Declaration> declarationsById = declarationsByName.values().stream().flatMap(Collection::stream)
                .collect(Collectors.toMap(Declaration::id, item -> item, (left, right) -> left));
        for (GrammarSemanticBinding binding : grammar.semanticBindings().bindings()) {
            if (binding.grammarElementId().isBlank()) continue;
            Declaration declaration = declarationsById.get(binding.grammarElementId());
            if (declaration == null) {
                ReferenceDiagnosticCode code = binding.metadata().containsKey("targetRevision") ? ReferenceDiagnosticCode.REFERENCE_STALE : ReferenceDiagnosticCode.REFERENCE_TARGET_DELETED;
                diagnostics.add(GrammarDiagnosticCatalog.reference(code, binding.grammarElementId(), "", new TextRange(null,null), "", "semanticBinding", binding.semanticElementKind(), List.of(), List.of(), "", "GrammarSemanticBindingMap"));
            } else if (!binding.grammarElementKind().isBlank() && !binding.grammarElementKind().equalsIgnoreCase(declaration.kind())) {
                diagnostics.add(GrammarDiagnosticCatalog.reference(ReferenceDiagnosticCode.REFERENCE_MAPPING_MISMATCH, binding.grammarElementId(), declaration.documentId(), declaration.range(), declaration.name(), "semanticBinding", binding.grammarElementKind(), List.of(), List.of(), "", "GrammarSemanticBindingMap kind mismatch"));
            }
        }
    }

    /** Descripción: Ejecuta la operación «semanticCandidate» correspondiente a la responsabilidad de Declaration. Entradas: grammar: dato de entrada necesario para la operación; declarationsByName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarSemanticBindingMap semanticCandidate(GrammarDefinitionModel grammar, Map<String,List<Declaration>> declarationsByName) {
        if (!grammar.semanticBindings().bindings().isEmpty()) return grammar.semanticBindings();
        boolean antlrGrammarFirst=grammar.technology()==GrammarTechnology.ANTLR&&grammar.authoringMode()==GrammarAuthoringMode.GRAMMAR_FIRST;
        // In GRAMMAR_FIRST, ANTLR parser rules are a safe candidate structure even though a parse tree is not
        // itself the final semantic model. Linked/metamodel-first ANTLR still requires explicit mappings.
        if (grammar.technology() == GrammarTechnology.ANTLR && !antlrGrammarFirst) return GrammarSemanticBindingMap.empty();
        double confidence=antlrGrammarFirst?0.60:(grammar.metamodelId().isBlank()?0.72:0.82);
        String provenance=antlrGrammarFirst?"HYDRA_ANTLR_RULE_INFERENCE":(grammar.metamodelId().isBlank()?"HYDRA_GRAMMAR_FIRST_INFERENCE":"HYDRA_NAME_HEURISTIC");
        List<GrammarSemanticBinding> bindings = declarationsByName.values().stream().flatMap(Collection::stream)
                .filter(item -> item.kind().equals("RULE"))
                .map(item -> new GrammarSemanticBinding(item.id(), item.kind(), item.name(), "CLASS", confidence, provenance,
                        antlrGrammarFirst?Map.of("inferred","true","semanticBridge","parser-rule"):
                                (grammar.metamodelId().isBlank()?Map.of("inferred","true"):Map.of("metamodelId", grammar.metamodelId(),"inferred","true"))))
                .toList();
        return new GrammarSemanticBindingMap("1.0", bindings, Map.of("candidateOnly", "true", "requiresReview", "true",
                "technology",grammar.technology().name()));
    }

    /** Descripción: Ejecuta la operación «imports» correspondiente a la responsabilidad de Declaration. Entradas: document: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<ImportOccurrence> imports(GrammarSourceDocument document, GrammarTechnology technology) {
        List<ImportOccurrence> result = new ArrayList<>();
        Matcher quoted = QUOTED_IMPORT.matcher(document.content());
        while (quoted.find()) result.add(new ImportOccurrence(quoted.group(1), quoted.start(1), quoted.end(1)));
        if (technology == GrammarTechnology.ANTLR) {
            Matcher antlr = ANTLR_IMPORT.matcher(document.content());
            while (antlr.find()) {
                for (String token : antlr.group(1).split(",")) {
                    String name = token.trim(); if (name.isEmpty()) continue;
                    int relative = antlr.group(1).indexOf(name);
                    result.add(new ImportOccurrence(name, antlr.start(1) + Math.max(0, relative), antlr.start(1) + Math.max(0, relative) + name.length()));
                }
            }
        }
        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «resolveImport» dentro de Declaration. Entradas: target: dato de entrada necesario para la operación; byId: identificador que delimita el recurso objetivo; byPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarSourceDocument resolveImport(String target, Map<String, GrammarSourceDocument> byId, Map<String, GrammarSourceDocument> byPath) {
        if (byId.containsKey(target)) return byId.get(target);
        String normalized = normalizePath(target);
        GrammarSourceDocument exact = byPath.get(normalized);
        if (exact != null) return exact;
        for (GrammarSourceDocument document : byPath.values()) {
            String path = normalizePath(document.relativePath());
            String stem = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
            if (stem.equals(target) || path.endsWith("/" + normalized)) return document;
        }
        return null;
    }

    /** Descripción: Ejecuta la operación «looksLocalImport» correspondiente a la responsabilidad de Declaration. Entradas: target: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean looksLocalImport(String target, GrammarTechnology technology) {
        return technology == GrammarTechnology.ANTLR || target.startsWith(".") || target.contains("/") || target.endsWith(".langium") || target.endsWith(".xtext") || target.endsWith(".g4");
    }

    /** Descripción: Ejecuta la operación «normalizePath» correspondiente a la responsabilidad de Declaration. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizePath(String value) { return value == null ? "" : value.replace('\\','/').replaceFirst("^\\./", ""); }
    /** Descripción: Ejecuta la operación «simpleName» correspondiente a la responsabilidad de Declaration. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String simpleName(String name) {
        if(name==null)return "";
        int scope=name.lastIndexOf("::");
        int dot=name.lastIndexOf('.');
        int separator=Math.max(dot,scope<0?-1:scope+1);
        return separator<0?name:name.substring(separator+1);
    }
    /** Descripción: Ejecuta la operación «kindCompatible» correspondiente a la responsabilidad de Declaration. Entradas: expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean kindCompatible(String expected, String actual) { return expected == null || expected.isBlank() || expected.equalsIgnoreCase(actual); }
    /** Descripción: Localiza mediante búsqueda binaria la declaración más cercana que empieza antes del offset, aprovechando que el escáner conserva el orden fuente. Entradas: declarations: declaraciones ordenadas por matchStart; offset: posición absoluta del documento. Salida: devuelve la declaración contenedora candidata o vacío cuando el offset precede a todas. */ private static Optional<Declaration> containing(List<Declaration> declarations, int offset) {
        int low = 0;
        int high = declarations.size() - 1;
        int candidate = -1;
        while(low <= high) {
            int middle = (low + high) >>> 1;
            if(declarations.get(middle).matchStart() <= offset) {
                candidate = middle;
                low = middle + 1;
            } else high = middle - 1;
        }
        return candidate < 0 ? Optional.empty() : Optional.of(declarations.get(candidate));
    }
    /** Descripción: Ejecuta la operación «similarity» correspondiente a la responsabilidad de Declaration. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static double similarity(String left, String right) {
        String a = left == null ? "" : left.toLowerCase(Locale.ROOT), b = right == null ? "" : right.toLowerCase(Locale.ROOT);
        if (a.equals(b)) return 1d; if (a.isEmpty() || b.isEmpty()) return 0d;
        int[] prev = new int[b.length()+1]; for (int j=0;j<=b.length();j++) prev[j]=j;
        for (int i=1;i<=a.length();i++) { int[] next = new int[b.length()+1]; next[0]=i; for(int j=1;j<=b.length();j++) next[j]=Math.min(Math.min(next[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1)); prev=next; }
        double base = 1d - ((double)prev[b.length()] / Math.max(a.length(), b.length()));
        if (!a.isEmpty() && !b.isEmpty() && a.charAt(0) == b.charAt(0)) base += 0.08;
        if (Math.abs(a.length() - b.length()) <= 1) base += 0.04;
        return clamp(base);
    }
    /** Descripción: Ejecuta la operación «clamp» correspondiente a la responsabilidad de Declaration. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static double clamp(double value) { return Math.max(0d, Math.min(1d, value)); }
    /** Descripción: Ejecuta la operación «combinedHash» correspondiente a la responsabilidad de Declaration. Entradas: documents: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String combinedHash(List<GrammarSourceDocument> documents) { return GrammarHashing.sources(documents); }


    /** Descripción: Valida las condiciones necesarias para «checkInterrupted» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Grammar analysis cancelled");
    }

    private record Declaration(String name, String kind, String documentId, TextRange range, String id, int matchStart, int matchEnd) {
        /** Descripción: Ejecuta la operación «symbol» correspondiente a la responsabilidad de Declaration. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ GrammarSymbol symbol() { return new GrammarSymbol(id, name, kind, documentId, range, "document:" + documentId, kind.equals("TOKEN") ? "Token/terminal" : "Grammar rule"); }
    }
    private record ReferenceOccurrence(String name, int start, int end, String expectedKind, String property, String rule) { }
    private record ImportOccurrence(String target, int start, int end) { }
}

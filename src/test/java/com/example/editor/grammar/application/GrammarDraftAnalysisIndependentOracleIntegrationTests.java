package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisRequest;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarDraftAnalysisRequest;
import com.example.editor.grammar.core.GrammarSemanticBinding;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.grammar.runtime.GrammarProviderHealthService;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.grammar.web.GrammarController;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real del analisis de borradores de gramatica por la capa HTTP con oraculos calculados en el propio
 * test (grammar_analyze_generate.rg, .rf, .pj y .cd): (a) un parser minimo de reglas escrito aqui y un cierre
 * transitivo sobre las esquinas izquierdas deciden que reglas son recursivas por la izquierda (directa,
 * indirecta, oculta tras opcionales, por la derecha, y en Langium, Xtext y ANTLR); (b) las referencias se
 * deciden por expresiones regulares sobre el texto; (c) clases y atributos esperados salen del texto fuente del
 * caso; (d) las acciones de reemplazo devueltas se aplican con un aplicador de ediciones propio y se reanaliza
 * por HTTP. Analizador, proveedores y proyeccion reales; nada se escribe en disco.
 */
class GrammarDraftAnalysisIndependentOracleIntegrationTests {

    private static final Set<String> BUILT_INS = Set.of("ID", "INT", "STRING");
    private static final Pattern RULE = Pattern.compile("(?ms)^[ \\t]*(?:entry[ \\t]+)?([A-Za-z_]\\w*)[ \\t]*:(.*?);");

    private MockMvc mockMvc;
    private JsonMapper mapper;
    private GrammarAnalysisCoordinator coordinator;
    private Path root;

    private void wire(Path temporary) {
        root = temporary;
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        properties.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        properties.setActivationDirectory(temporary.resolve("grammar-activations"));
        mapper = JsonMapper.builder().findAndAddModules().build();
        GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        GrammarApplicationService grammars = new GrammarApplicationService(
                new JsonGrammarRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()), properties, profiles, providers);
        GrammarProviderHealthService health = new GrammarProviderHealthService(properties);
        coordinator = new GrammarAnalysisCoordinator(profiles, providers, new GrammarAnalysisCache(properties), health, properties);
        GrammarClassDiagramProjectionService diagrams = new GrammarClassDiagramProjectionService(properties);
        GrammarController controller = new GrammarController(grammars, new GrammarAnalysisService(grammars, coordinator), profiles, providers,
                health, diagrams, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void closeCoordinator() {
        if (coordinator != null) coordinator.close();
    }

    // ---- rg: recursion por la izquierda contra un cierre transitivo calculado en el test ----

    private record Case(String name, GrammarTechnology technology, String entry, String source, Set<String> expectedBlocking) { }

    @Test
    @DisplayName("grammar_analyze_generate.rg: las reglas recursivas por la izquierda de la API coinciden con el cierre transitivo del test")
    void leftRecursiveRulesMatchTheTestsOwnTransitiveClosure(@TempDir Path temporary) throws Exception {
        wire(temporary);
        List<Case> corpus = List.of(
                new Case("direct", GrammarTechnology.LANGIUM, "Model",
                        "grammar Calc\nentry Model: expression=Expression;\nExpression: Expression '+' INT | INT;\n", Set.of("Expression")),
                new Case("indirect", GrammarTechnology.LANGIUM, "Model",
                        "grammar Main\nentry Model: value=A;\nA: B 'a' | 'x';\nB: A 'b' | 'y';\n", Set.of("A", "B")),
                new Case("hidden-behind-a-nullable-rule", GrammarTechnology.LANGIUM, "Model",
                        "grammar Hidden\nentry Model: value=A;\nA: Opt A 'x' | 'y';\nOpt: 'o'?;\n", Set.of("A")),
                new Case("hidden-behind-an-optional-item", GrammarTechnology.LANGIUM, "Model",
                        "grammar Hidden\nentry Model: value=A;\nA: Opt? A 'x' | 'y';\nOpt: 'o';\n", Set.of("A")),
                new Case("right-recursion", GrammarTechnology.LANGIUM, "Model",
                        "grammar Tree\nentry Model: root=Node;\nNode: 'n' Node | 'e';\n", Set.of()),
                new Case("mutual-right-recursion", GrammarTechnology.LANGIUM, "Model",
                        "grammar Mutual\nentry Model: value=A;\nA: 'a' B | 'x';\nB: 'b' A | 'y';\n", Set.of()),
                new Case("mixed-alternatives", GrammarTechnology.LANGIUM, "Model",
                        "grammar Mixed\nentry Model: value=A;\nA: 'k' A | A 'z' | 'e';\n", Set.of("A")),
                new Case("xtext-direct", GrammarTechnology.XTEXT, "Model",
                        "grammar org.example.Calc with org.eclipse.xtext.common.Terminals\ngenerate calc \"http://example.org/calc\"\nModel: expression=Expression;\nExpression: Expression '+' INT | INT;\n",
                        Set.of("Expression")),
                new Case("antlr-direct-is-native", GrammarTechnology.ANTLR, "expr",
                        "grammar Calc;\nexpr: expr '+' term | term;\nterm: INT;\nINT: [0-9]+;\n", Set.of()),
                new Case("antlr-indirect", GrammarTechnology.ANTLR, "value",
                        "grammar Main;\nvalue: a;\na: b 'a' | 'x';\nb: a 'b' | 'y';\n", Set.of("a", "b")));

        for (Case item : corpus) {
            Set<String> oracle = oracleBlockingRules(item.technology(), item.source());
            assertThat(oracle).as("the oracle itself for " + item.name()).isEqualTo(item.expectedBlocking());

            Map<String, Object> result = analyzeVia("/api/grammars/analyze-draft",
                    grammar(item.technology(), "oracle-" + item.name(), item.entry(), item.source(), GrammarSemanticBindingMap.empty()));

            assertThat(apiBlockingRules(result)).as("API result for " + item.name()).isEqualTo(oracle);
            assertThat(list(result, "diagnostics").stream().anyMatch(diagnostic -> "GRAMMAR_LEFT_RECURSION".equals(diagnostic.get("code"))
                    && "ERROR".equals(diagnostic.get("severity")))).as("blocking error present for " + item.name()).isEqualTo(!oracle.isEmpty());
        }
        Map<String, Object> antlrNative = analyzeVia("/api/grammars/analyze-draft", grammar(GrammarTechnology.ANTLR, "oracle-antlr-native-info", "expr",
                "grammar Calc;\nexpr: expr '+' term | term;\nterm: INT;\nINT: [0-9]+;\n", GrammarSemanticBindingMap.empty()));
        assertThat(list(antlrNative, "diagnostics")).anyMatch(diagnostic -> "GRAMMAR_LEFT_RECURSION_NATIVE_SUPPORTED".equals(diagnostic.get("code"))
                && "INFO".equals(diagnostic.get("severity")));
    }

    // ---- rf: referencias por expresiones regulares y acciones de reemplazo aplicadas ----

    @Test
    @DisplayName("grammar_analyze_generate.rf: referencias ausentes, ambiguas o con otra escritura coinciden con la busqueda por regex del test")
    void missingAmbiguousAndMiscasedReferencesMatchTheTestsRegexOracle(@TempDir Path temporary) throws Exception {
        wire(temporary);
        Map<String, String> corpus = new LinkedHashMap<>();
        corpus.put("missing", "grammar Stable\nentry Model: nodes+=Nodes*;\nNode: 'node' name=ID;\n");
        corpus.put("miscased", "grammar Stable\nentry Model: nodes+=node*;\nNode: 'node' name=ID;\n");
        corpus.put("ambiguous", "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nNode: 'other' name=ID;\n");
        corpus.put("several", "grammar Stable\nentry Model: nodes+=Nodes* extra=Extra?;\nNode: 'node' name=ID;\n");
        corpus.put("clean", "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n");
        Map<String, Set<String>> literal = Map.of(
                "missing", Set.of("REFERENCE_NOT_FOUND:Nodes"), "miscased", Set.of("REFERENCE_CASE_MISMATCH:node"),
                "ambiguous", Set.of("REFERENCE_AMBIGUOUS:Node"), "several", Set.of("REFERENCE_NOT_FOUND:Nodes", "REFERENCE_NOT_FOUND:Extra"),
                "clean", Set.of());

        for (Map.Entry<String, String> item : corpus.entrySet()) {
            Set<String> oracle = oracleReferenceFindings(item.getValue());
            assertThat(oracle).as("the oracle itself for " + item.getKey()).isEqualTo(literal.get(item.getKey()));
            Map<String, Object> result = analyzeVia("/api/grammars/analyze-draft",
                    grammar(GrammarTechnology.LANGIUM, "refs-" + item.getKey(), "Model", item.getValue(), GrammarSemanticBindingMap.empty()));
            assertThat(apiReferenceFindings(result)).as("API result for " + item.getKey()).isEqualTo(oracle);
        }
    }

    @Test
    @DisplayName("grammar_analyze_generate.rf: aplicar la accion de reemplazo devuelta y reanalizar por HTTP deja la referencia sin error")
    void applyingTheReturnedReplacementActionClearsTheReferenceError(@TempDir Path temporary) throws Exception {
        wire(temporary);
        for (String source : List.of("grammar Stable\nentry Model: nodes+=Nodes*;\nNode: 'node' name=ID;\n",
                "grammar Stable\nentry Model: nodes+=node*;\nNode: 'node' name=ID;\n")) {
            Set<String> faulty = oracleReferenceFindings(source);
            Map<String, Object> before = analyzeVia("/api/grammars/analyze-draft",
                    grammar(GrammarTechnology.LANGIUM, "fix-before-" + faulty.iterator().next().hashCode(), "Model", source, GrammarSemanticBindingMap.empty()));
            List<Map<String, Object>> replacements = list(before, "codeActions").stream()
                    .filter(action -> "quickfix.reference.replace".equals(action.get("kind"))).toList();
            assertThat(replacements).isNotEmpty();
            List<Map<String, Object>> edits = new ArrayList<>();
            for (Map<String, Object> action : replacements) edits.addAll(list(action, "edits"));
            // Cada edicion apunta exactamente al texto defectuoso que el oraculo encontro.
            Set<String> faultyNames = new TreeSet<>();
            for (String finding : faulty) faultyNames.add(finding.substring(finding.indexOf(':') + 1));
            for (Map<String, Object> edit : edits) assertThat(faultyNames).contains(replacedText(source, edit));

            String repaired = applyEdits(source, edits);

            assertThat(repaired).isNotEqualTo(source);
            Map<String, Object> after = analyzeVia("/api/grammars/analyze-draft",
                    grammar(GrammarTechnology.LANGIUM, "fix-after-" + faulty.iterator().next().hashCode(), "Model", repaired, GrammarSemanticBindingMap.empty()));
            assertThat(apiReferenceFindings(after)).isEmpty();
            assertThat(oracleReferenceFindings(repaired)).isEmpty();
        }
    }

    @Test
    @DisplayName("grammar_analyze_generate.rf: una correspondencia obsoleta y otra de tipo distinto se senalan con la referencia literal")
    void staleAndMismatchedSemanticMappingsAreReportedWithTheirLiteralReference(@TempDir Path temporary) throws Exception {
        wire(temporary);
        String source = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n";
        Map<String, Object> inferred = analyzeVia("/api/grammars/analyze-draft",
                grammar(GrammarTechnology.LANGIUM, "mapping-inferred", "Model", source, GrammarSemanticBindingMap.empty()));
        Map<String, Object> candidate = map(inferred, "semanticCandidate");
        String nodeElementId = list(candidate, "bindings").stream().filter(binding -> "Node".equals(binding.get("semanticElementId")))
                .map(binding -> String.valueOf(binding.get("grammarElementId"))).findFirst().orElseThrow();

        GrammarSemanticBinding obsolete = new GrammarSemanticBinding("rule:Ghost", "RULE", "Ghost", "CLASS", 0.9d, "manual", Map.of("targetRevision", "3"));
        GrammarSemanticBinding wrongKind = new GrammarSemanticBinding(nodeElementId, "TERMINAL", "Node", "CLASS", 0.9d, "manual", Map.of());
        Map<String, Object> result = analyzeVia("/api/grammars/analyze-draft", grammar(GrammarTechnology.LANGIUM, "mapping-faulty", "Model", source,
                new GrammarSemanticBindingMap("1.0", List.of(obsolete, wrongKind), Map.of())));

        assertThat(apiAllReferenceFindings(result)).contains("REFERENCE_STALE:rule:Ghost", "REFERENCE_MAPPING_MISMATCH:" + nodeElementId);
        assertThat(apiAllReferenceFindings(analyzeVia("/api/grammars/analyze-draft",
                grammar(GrammarTechnology.LANGIUM, "mapping-fine", "Model", source, GrammarSemanticBindingMap.empty())))).isEmpty();
    }

    // ---- pj + cd: proyeccion de clases contra los atributos que dicta el texto del caso ----

    @Test
    @DisplayName("grammar_analyze_generate.pj/.cd: el diagrama deducido tiene las clases y atributos que dicta el texto, en Langium y Xtext, y no escribe nada")
    void theDeducedClassDiagramHasTheClassesAndAttributesTheSourceDictates(@TempDir Path temporary) throws Exception {
        wire(temporary);
        String langium = "grammar People\nentry Model: people+=Person* companies+=Company*;\n"
                + "Person: 'person' name=ID age=INT manager=[Person|ID];\nCompany: 'company' title=ID employees+=Person*;\n";
        String xtext = "grammar org.example.People with org.eclipse.xtext.common.Terminals\ngenerate people \"http://example.org/people\"\n"
                + "Model: people+=Person* companies+=Company*;\nPerson: 'person' name=ID age=INT manager=[Person|ID];\nCompany: 'company' title=ID employees+=Person*;\n";

        for (Map.Entry<GrammarTechnology, String> item : Map.of(GrammarTechnology.LANGIUM, langium, GrammarTechnology.XTEXT, xtext).entrySet()) {
            String suffix = item.getKey().name().toLowerCase();
            Map<String, Set<String>> expected = oracleFeaturesByClass(item.getValue());
            assertThat(expected).containsEntry("Person", Set.of("name", "age", "manager"))
                    .containsEntry("Company", Set.of("title", "employees")).containsEntry("Model", Set.of("people", "companies"));

            Map<String, Object> projection = analyzeVia("/api/grammars/class-diagram-draft",
                    grammar(item.getKey(), "diagram-" + suffix, "Model", item.getValue(), GrammarSemanticBindingMap.empty()));

            assertThat(projection.get("status")).isEqualTo("COMPLETE");
            Map<String, Set<String>> actual = featuresByNode(projection);
            for (Map.Entry<String, Set<String>> rule : expected.entrySet()) {
                assertThat(actual).as("class " + rule.getKey() + " in " + suffix).containsKey(rule.getKey());
                assertThat(actual.get(rule.getKey())).as("attributes of " + rule.getKey() + " in " + suffix).containsAll(rule.getValue());
            }
            assertThat(actual.values().stream().flatMap(Set::stream).toList()).doesNotContain("ID", "INT");
            assertThat(list(projection, "edges").stream().filter(edge -> "REFERENCE".equals(edge.get("kind")))
                    .map(edge -> String.valueOf(edge.get("label"))).toList()).containsExactly("manager");

            Map<String, Object> repeated = analyzeVia("/api/grammars/class-diagram-draft",
                    grammar(item.getKey(), "diagram-" + suffix, "Model", item.getValue(), GrammarSemanticBindingMap.empty()));
            assertThat(String.valueOf(repeated.get("projectionHash"))).isNotBlank().isEqualTo(String.valueOf(projection.get("projectionHash")));

            String extended = item.getValue().replace("name=ID age=INT", "name=ID nickname=ID age=INT");
            Map<String, Object> changed = analyzeVia("/api/grammars/class-diagram-draft",
                    grammar(item.getKey(), "diagram-" + suffix, "Model", extended, GrammarSemanticBindingMap.empty()));
            assertThat(featuresByNode(changed).get("Person")).contains("nickname");
            assertThat(oracleFeaturesByClass(extended).get("Person")).contains("nickname");
            assertThat(String.valueOf(changed.get("projectionHash"))).isNotEqualTo(String.valueOf(projection.get("projectionHash")));
        }
        try (Stream<Path> walk = Files.walk(root)) {
            assertThat(walk.filter(Files::isRegularFile).count()).isZero();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ---- oraculos del test (independientes del codigo de produccion) ----

    private record Item(String rule, char quantifier) { }

    private static List<List<Item>> alternatives(String body) {
        String cleaned = body.replaceAll("[A-Za-z_]\\w*\\s*(\\+=|\\?=|=)\\s*", "");
        List<List<Item>> result = new ArrayList<>();
        for (String alternative : cleaned.split("\\|", -1)) {
            List<Item> items = new ArrayList<>();
            Matcher token = Pattern.compile("('(?:[^'\\\\]|\\\\.)*'|[A-Za-z_]\\w*)([?*+]?)").matcher(alternative);
            while (token.find()) {
                String text = token.group(1);
                items.add(new Item(text.startsWith("'") ? null : text, token.group(2).isEmpty() ? ' ' : token.group(2).charAt(0)));
            }
            result.add(items);
        }
        return result;
    }

    /** Reglas bloqueantes: las que se alcanzan a si mismas por esquinas izquierdas, salvo la directa en ANTLR (nativa). */
    private static Set<String> oracleBlockingRules(GrammarTechnology technology, String source) {
        Map<String, List<List<Item>>> rules = new LinkedHashMap<>();
        Matcher header = RULE.matcher(source);
        while (header.find()) {
            String name = header.group(1);
            if (technology == GrammarTechnology.ANTLR && !Character.isLowerCase(name.charAt(0))) continue;
            rules.putIfAbsent(name, alternatives(header.group(2)));
        }
        Map<String, List<List<Item>>> known = new LinkedHashMap<>();
        rules.forEach((name, alternatives) -> known.put(name, alternatives.stream()
                .map(items -> items.stream().map(item -> item.rule() != null && rules.containsKey(item.rule()) ? item : new Item(null, item.quantifier())).toList())
                .toList()));
        Map<String, Boolean> nullable = new LinkedHashMap<>();
        known.keySet().forEach(name -> nullable.put(name, false));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, List<List<Item>>> rule : known.entrySet()) {
                boolean value = rule.getValue().stream().anyMatch(items -> items.stream().allMatch(item -> itemNullable(item, nullable)));
                if (value && !nullable.get(rule.getKey())) { nullable.put(rule.getKey(), true); changed = true; }
            }
        }
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<Item>>> rule : known.entrySet()) {
            Set<String> targets = new LinkedHashSet<>();
            for (List<Item> items : rule.getValue()) {
                for (Item item : items) {
                    if (item.rule() != null) targets.add(item.rule());
                    if (!itemNullable(item, nullable)) break;
                }
            }
            edges.put(rule.getKey(), targets);
        }
        Set<String> blocking = new TreeSet<>();
        for (String name : known.keySet()) {
            Set<String> reach = new LinkedHashSet<>();
            List<String> pending = new ArrayList<>(edges.get(name));
            while (!pending.isEmpty()) {
                String next = pending.remove(pending.size() - 1);
                if (reach.add(next)) pending.addAll(edges.getOrDefault(next, Set.of()));
            }
            boolean leftRecursive = reach.contains(name);
            // Directa = la unica regla de su componente fuertemente conexo (no se alcanza ninguna otra que vuelva a ella).
            boolean nativeDirect = technology == GrammarTechnology.ANTLR && reach.stream().filter(other -> !other.equals(name))
                    .noneMatch(other -> reachesFrom(edges, other).contains(name));
            if (leftRecursive && !nativeDirect) blocking.add(name);
        }
        return blocking;
    }

    private static Set<String> reachesFrom(Map<String, Set<String>> edges, String start) {
        Set<String> reach = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>(edges.getOrDefault(start, Set.of()));
        while (!pending.isEmpty()) {
            String next = pending.remove(pending.size() - 1);
            if (reach.add(next)) pending.addAll(edges.getOrDefault(next, Set.of()));
        }
        return reach;
    }

    private static boolean itemNullable(Item item, Map<String, Boolean> nullable) {
        return item.quantifier() == '?' || item.quantifier() == '*' || (item.rule() != null && nullable.getOrDefault(item.rule(), false));
    }

    private static Set<String> oracleReferenceFindings(String source) {
        Map<String, Integer> definitions = new LinkedHashMap<>();
        List<String> bodies = new ArrayList<>();
        Matcher header = RULE.matcher(source);
        while (header.find()) {
            definitions.merge(header.group(1), 1, Integer::sum);
            bodies.add(header.group(2));
        }
        Set<String> findings = new TreeSet<>();
        for (String body : bodies) {
            String cleaned = body.replaceAll("'(?:[^'\\\\]|\\\\.)*'", " ").replaceAll("[A-Za-z_]\\w*\\s*(\\+=|\\?=|=)\\s*", "");
            Matcher identifier = Pattern.compile("[A-Za-z_]\\w*").matcher(cleaned);
            while (identifier.find()) {
                String name = identifier.group();
                if (BUILT_INS.contains(name)) continue;
                int count = definitions.getOrDefault(name, 0);
                if (count > 1) findings.add("REFERENCE_AMBIGUOUS:" + name);
                else if (count == 0 && definitions.keySet().stream().anyMatch(defined -> defined.equalsIgnoreCase(name))) findings.add("REFERENCE_CASE_MISMATCH:" + name);
                else if (count == 0) findings.add("REFERENCE_NOT_FOUND:" + name);
            }
        }
        return findings;
    }

    private static Map<String, Set<String>> oracleFeaturesByClass(String source) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        Matcher header = RULE.matcher(source);
        while (header.find()) {
            Set<String> features = new TreeSet<>();
            Matcher assignment = Pattern.compile("([A-Za-z_]\\w*)\\s*(?:\\+=|\\?=|=)").matcher(header.group(2));
            while (assignment.find()) features.add(assignment.group(1));
            result.put(header.group(1), features);
        }
        return result;
    }

    /** Aplicador de ediciones independiente: line/column son 0-based y las lineas se separan por salto de linea simple. */
    private static String applyEdits(String original, List<Map<String, Object>> edits) {
        List<Map<String, Object>> ordered = new ArrayList<>(edits);
        ordered.sort((left, right) -> startOffset(original, right) - startOffset(original, left));
        String result = original;
        for (Map<String, Object> edit : ordered) {
            result = result.substring(0, startOffset(original, edit)) + edit.get("replacement") + result.substring(endOffset(original, edit));
        }
        return result;
    }

    private static String replacedText(String source, Map<String, Object> edit) {
        return source.substring(startOffset(source, edit), endOffset(source, edit));
    }

    private static int startOffset(String source, Map<String, Object> edit) { return offset(source, map(map(edit, "range"), "start")); }

    private static int endOffset(String source, Map<String, Object> edit) { return offset(source, map(map(edit, "range"), "end")); }

    private static int offset(String source, Map<String, Object> position) {
        int offset = 0;
        for (int line = 0; line < ((Number) position.get("line")).intValue(); line++) offset = source.indexOf('\n', offset) + 1;
        return offset + ((Number) position.get("column")).intValue();
    }

    // ---- ayudas HTTP ----

    private static GrammarDefinitionModel grammar(GrammarTechnology technology, String id, String entryRule, String source, GrammarSemanticBindingMap bindings) {
        String extension = technology == GrammarTechnology.ANTLR ? "g4" : technology == GrammarTechnology.XTEXT ? "xtext" : "langium";
        String language = technology == GrammarTechnology.ANTLR ? "antlr" : technology == GrammarTechnology.XTEXT ? "xtext" : "langium";
        String profile = technology == GrammarTechnology.ANTLR ? "antlr-4.13.2-java" : technology == GrammarTechnology.XTEXT ? "xtext-2.44-lsp" : "langium-4.3-browser";
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main." + extension, language, source, "", true, false);
        return new GrammarDefinitionModel("1.0", id, id, technology, GrammarAuthoringMode.GRAMMAR_FIRST, "",
                profile, "main", entryRule, List.of(document), bindings, "", Map.of(), Map.of());
    }

    private Map<String, Object> analyzeVia(String path, GrammarDefinitionModel grammar) throws Exception {
        GrammarAnalysisRequest analysis = new GrammarAnalysisRequest(grammar.id(), 1, GrammarAnalysisMode.NORMAL, List.of("main"), grammar.documents(), grammar.toolchainProfileId());
        String body = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(new GrammarDraftAnalysisRequest(grammar, analysis))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return asMap(mapper.readValue(body, Map.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) { return (Map<String, Object>) value; }

    private static Map<String, Object> map(Map<String, Object> source, String key) { return asMap(source.get(key)); }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> source, String key) { return (List<Map<String, Object>>) source.get(key); }

    private static Set<String> apiBlockingRules(Map<String, Object> result) {
        Set<String> rules = new TreeSet<>();
        for (Map<String, Object> diagnostic : list(result, "diagnostics")) {
            if (!"GRAMMAR_LEFT_RECURSION".equals(diagnostic.get("code")) || !"ERROR".equals(diagnostic.get("severity"))) continue;
            for (String rule : String.valueOf(map(diagnostic, "metadata").get("rules")).split(",\\s*")) rules.add(rule);
        }
        return rules;
    }

    private static Set<String> apiReferenceFindings(Map<String, Object> result) {
        Set<String> wanted = Set.of("REFERENCE_NOT_FOUND", "REFERENCE_CASE_MISMATCH", "REFERENCE_AMBIGUOUS");
        Set<String> findings = new TreeSet<>();
        for (Map<String, Object> diagnostic : list(result, "diagnostics")) {
            if (wanted.contains(String.valueOf(diagnostic.get("code")))) findings.add(diagnostic.get("code") + ":" + map(diagnostic, "metadata").get("reference"));
        }
        return findings;
    }

    private static Set<String> apiAllReferenceFindings(Map<String, Object> result) {
        Set<String> findings = new TreeSet<>();
        for (Map<String, Object> diagnostic : list(result, "diagnostics")) {
            String code = String.valueOf(diagnostic.get("code"));
            if (code.startsWith("REFERENCE_")) findings.add(code + ":" + map(diagnostic, "metadata").get("reference"));
        }
        return findings;
    }

    private static Map<String, Set<String>> featuresByNode(Map<String, Object> projection) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map<String, Object> node : list(projection, "nodes")) {
            Set<String> features = new TreeSet<>();
            for (Map<String, Object> feature : list(node, "features")) features.add(String.valueOf(feature.get("name")));
            result.put(String.valueOf(node.get("name")), features);
        }
        return result;
    }
}

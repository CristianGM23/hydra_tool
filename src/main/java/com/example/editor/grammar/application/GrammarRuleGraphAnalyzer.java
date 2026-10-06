package com.example.editor.grammar.application;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.core.TextRange;
import com.example.editor.grammar.diagnostic.SourceTextIndex;

/**
 * Analizador neutral del grafo de reglas. Se usa tanto para inferir la regla de entrada
 * como para clasificar recursión sin depender del runtime nativo de Xtext/Langium/ANTLR.
 * El coste es lineal respecto al tamaño de las fuentes más el grafo de invocaciones.
 */
public final class GrammarRuleGraphAnalyzer {
    private static final Pattern DECLARATIVE_HEADER = Pattern.compile("(?m)^\\s*(?:(entry)\\s+)?(?:(terminal|fragment|hidden)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:returns\\s+[A-Za-z_][A-Za-z0-9_.:<>]*)?\\s*:");
    private static final Pattern ANTLR_HEADER = Pattern.compile("(?m)^\\s*(?:(fragment)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^]\\r\\n]*\\])?\\s*(?:returns\\s+\\[[^]\\r\\n]*\\])?\\s*:");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Descripción: Inicializa una instancia de GrammarRuleGraphAnalyzer con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarRuleGraphAnalyzer() { }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de Token. Entradas: grammar: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Analysis analyze(GrammarDefinitionModel grammar) {
        Objects.requireNonNull(grammar, "grammar");
        List<Rule> rules = scan(grammar.documents(), grammar.technology());
        List<Rule> parserRules = rules.stream().filter(Rule::parserRule).toList();
        LinkedHashMap<String, Rule> firstByName = new LinkedHashMap<>();
        for (Rule rule : parserRules) firstByName.putIfAbsent(rule.name(), rule);
        Set<String> parserNames = firstByName.keySet();

        Map<String, Expr> expressions = new LinkedHashMap<>();
        for (Rule rule : firstByName.values()) expressions.put(rule.name(), new BodyParser(rule.body(), parserNames).parse());

        Map<String, Boolean> nullable = new LinkedHashMap<>();
        for (String name : parserNames) nullable.put(name, false);
        for (int round = 0; round <= parserNames.size(); round++) {
            boolean changed = false;
            for (String name : parserNames) {
                boolean value = expressions.get(name).nullable(nullable);
                if (value != nullable.get(name)) { nullable.put(name, value); changed = true; }
            }
            if (!changed) break;
        }

        Map<String, Set<String>> calls = new LinkedHashMap<>();
        Map<String, Set<String>> leftCorners = new LinkedHashMap<>();
        for (String name : parserNames) {
            Expr expr = expressions.get(name);
            LinkedHashSet<String> refs = new LinkedHashSet<>();
            expr.collectRefs(refs);
            refs.retainAll(parserNames);
            calls.put(name, Set.copyOf(refs));
            LinkedHashSet<String> first = new LinkedHashSet<>();
            expr.collectFirstRules(nullable, first);
            first.retainAll(parserNames);
            leftCorners.put(name, Set.copyOf(first));
        }

        EntryInference entry = inferEntry(grammar, firstByName, calls);
        List<Recursion> recursion = classifyRecursion(grammar.technology(), firstByName, calls, leftCorners, nullable);
        return new Analysis(List.copyOf(parserRules), immutableGraph(calls), immutableGraph(leftCorners), Map.copyOf(nullable), entry, recursion);
    }

    /** Descripción: Ejecuta la operación «inferEntry» correspondiente a la responsabilidad de Token. Entradas: grammar: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; calls: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EntryInference inferEntry(GrammarDefinitionModel grammar, LinkedHashMap<String, Rule> rules, Map<String, Set<String>> calls) {
        if (rules.isEmpty()) return new EntryInference("", "NONE", 0d, List.of(), "No parser rules were recognized.");
        List<String> all = List.copyOf(rules.keySet());
        List<String> explicit = rules.values().stream().filter(Rule::explicitEntry).map(Rule::name).toList();
        if (!explicit.isEmpty()) {
            String selected = explicit.get(0);
            return new EntryInference(selected, explicit.size() == 1 ? "EXPLICIT_ENTRY" : "FIRST_EXPLICIT_ENTRY", explicit.size() == 1 ? 1d : 0.92d,
                    orderedCandidates(selected, all), explicit.size() == 1 ? "The grammar explicitly marks this parser rule as entry." : "Several entry markers exist; HYDRA selected the first declared entry rule and keeps the others selectable.");
        }
        if (grammar.technology() == GrammarTechnology.XTEXT) {
            Rule first = preferredFirstRule(grammar, rules.values());
            return new EntryInference(first.name(), "XTEXT_FIRST_PARSER_RULE", 0.99d, orderedCandidates(first.name(), all),
                    "Xtext uses the first parser rule as the grammar entry rule when no explicit HYDRA override is selected.");
        }

        Map<String,Integer> incoming = new LinkedHashMap<>();
        for (String name : rules.keySet()) incoming.put(name, 0);
        for (Map.Entry<String, Set<String>> edge : calls.entrySet()) {
            for (String target : edge.getValue()) if (!target.equals(edge.getKey()) && incoming.containsKey(target)) incoming.merge(target, 1, Integer::sum);
        }
        List<Rule> roots = rules.values().stream().filter(rule -> incoming.getOrDefault(rule.name(), 0) == 0).toList();
        List<Rule> preferredRoots = roots.stream().filter(rule -> rule.documentId().equals(grammar.entryDocumentId()) || rule.entryPointDocument()).toList();
        if (preferredRoots.size() == 1) {
            Rule root = preferredRoots.get(0);
            return new EntryInference(root.name(), "UNREFERENCED_ENTRY_DOCUMENT_ROOT", 0.96d, orderedCandidates(root.name(), all),
                    "This rule is the only unreferenced parser root in the configured entry document.");
        }
        if (roots.size() == 1) {
            Rule root = roots.get(0);
            return new EntryInference(root.name(), "UNREFERENCED_GRAPH_ROOT", 0.93d, orderedCandidates(root.name(), all),
                    "This rule is the only parser rule not reached from another parser rule.");
        }
        Rule fallback = preferredFirstRule(grammar, rules.values());
        double confidence = roots.isEmpty() ? 0.68d : 0.76d;
        return new EntryInference(fallback.name(), roots.isEmpty() ? "FIRST_PARSER_RULE_FALLBACK" : "AMBIGUOUS_GRAPH_ROOT_FALLBACK", confidence,
                orderedCandidates(fallback.name(), all), roots.isEmpty()
                    ? "Every parser rule is referenced by another rule, so HYDRA falls back to the first parser rule in the entry document."
                    : "Several parser roots are possible, so HYDRA preselects the first one in the entry document while keeping every parser rule selectable.");
    }

    /** Descripción: Ejecuta la operación «preferredFirstRule» correspondiente a la responsabilidad de Token. Entradas: grammar: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Rule preferredFirstRule(GrammarDefinitionModel grammar, Collection<Rule> rules) {
        return rules.stream().filter(rule -> rule.documentId().equals(grammar.entryDocumentId()) || rule.entryPointDocument()).findFirst().orElse(rules.iterator().next());
    }

    /** Descripción: Ejecuta la operación «orderedCandidates» correspondiente a la responsabilidad de Token. Entradas: selected: dato de entrada necesario para la operación; all: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> orderedCandidates(String selected, List<String> all) {
        ArrayList<String> values = new ArrayList<>();
        if (selected != null && !selected.isBlank()) values.add(selected);
        for (String value : all) if (!value.equals(selected)) values.add(value);
        return List.copyOf(values);
    }

    /** Descripción: Ejecuta la operación «classifyRecursion» correspondiente a la responsabilidad de Token. Entradas: technology: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; calls: dato de entrada necesario para la operación; leftCorners: dato de entrada necesario para la operación; nullable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<Recursion> classifyRecursion(GrammarTechnology technology, Map<String, Rule> rules,
            Map<String, Set<String>> calls, Map<String, Set<String>> leftCorners, Map<String, Boolean> nullable) {
        List<Set<String>> allComponents = stronglyConnected(calls);
        List<Set<String>> leftComponents = stronglyConnected(leftCorners);
        Map<String, Set<String>> leftMembership = new HashMap<>();
        for (Set<String> component : leftComponents) if (cyclic(component, leftCorners)) for (String name : component) leftMembership.put(name, component);
        Set<String> emitted = new HashSet<>();
        List<Recursion> result = new ArrayList<>();
        for (Set<String> component : allComponents) {
            if (!cyclic(component, calls)) continue;
            Set<String> leftComponent = component.stream().map(leftMembership::get).filter(Objects::nonNull).findFirst().orElse(null);
            boolean left = leftComponent != null;
            Set<String> effective = left ? leftComponent : component;
            String key = String.join("|", effective.stream().sorted().toList()) + ":" + left;
            if (!emitted.add(key)) continue;
            List<String> cycle = cyclePath(effective.iterator().next(), left ? leftCorners : calls, effective);
            boolean direct = effective.size() == 1 && (left ? leftCorners : calls).getOrDefault(effective.iterator().next(), Set.of()).contains(effective.iterator().next());
            boolean nativeSupported = left && direct && technology == GrammarTechnology.ANTLR;
            RecursionKind kind = left
                    ? (direct ? (nativeSupported ? RecursionKind.DIRECT_LEFT_NATIVE_SUPPORTED : RecursionKind.DIRECT_LEFT) : RecursionKind.INDIRECT_LEFT)
                    : (direct ? RecursionKind.DIRECT_SAFE : RecursionKind.INDIRECT_SAFE);
            Rule anchor = rules.get(effective.iterator().next());
            Rewrite rewrite = kind == RecursionKind.DIRECT_LEFT ? directRewrite(anchor, rules.keySet()) : null;
            result.add(new Recursion(kind, cycle, List.copyOf(effective.stream().sorted().toList()), anchor.documentId(), anchor.nameRange(),
                    nativeSupported, rewrite, nullable.getOrDefault(anchor.name(), false)));
        }
        return List.copyOf(result.stream().sorted(Comparator.comparing(Recursion::documentId)
                .thenComparing(item -> item.range().start().line()).thenComparing(item -> item.range().start().column())).toList());
    }

    /** Descripción: Ejecuta la operación «directRewrite» correspondiente a la responsabilidad de Token. Entradas: rule: dato de entrada necesario para la operación; parserNames: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ private static Rewrite directRewrite(Rule rule, Set<String> parserNames) {
        if (rule == null) return null;
        List<String> alternatives = splitTopLevelAlternatives(rule.body());
        if (alternatives.size() < 2) return null;
        List<String> recursive = new ArrayList<>();
        List<String> base = new ArrayList<>();
        for (String alternative : alternatives) {
            String trimmed = alternative.trim();
            Matcher matcher = IDENTIFIER.matcher(trimmed);
            if (matcher.lookingAt() && matcher.group().equals(rule.name())) {
                String suffix = trimmed.substring(matcher.end()).trim();
                if (suffix.isEmpty()) return null;
                recursive.add(suffix);
            } else base.add(trimmed);
        }
        if (recursive.isEmpty() || base.isEmpty() || base.stream().anyMatch(String::isBlank)) return null;
        String basePart = base.size() == 1 ? base.get(0) : "(" + String.join(" | ", base) + ")";
        String suffixPart = recursive.size() == 1 ? "(" + recursive.get(0) + ")*" : "(" + recursive.stream().map(value -> "(" + value + ")").reduce((a,b)->a+" | "+b).orElse("") + ")*";
        String replacement = basePart + " " + suffixPart;
        boolean safe = semanticallyPlain(rule.body()) && recursive.stream().noneMatch(value -> startsWithParserRule(value, parserNames));
        return new Rewrite(rule.documentId(), rule.bodyRange(), replacement, safe,
                safe ? "The rule has no assignments, actions or cross-references; the rewrite preserves the accepted left-associative repetition pattern."
                     : "HYDRA can rewrite the parser-control pattern mechanically, but assignments/actions may change semantic construction and must be reviewed after reanalysis.");
    }

    /** Descripción: Crea o prepara el resultado de «startsWithParserRule» dentro de Token. Entradas: value: valor de entrada que debe procesarse; parserNames: componente de conversión utilizado durante el procesamiento. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean startsWithParserRule(String value, Set<String> parserNames) {
        Matcher matcher = IDENTIFIER.matcher(value.stripLeading());
        return matcher.lookingAt() && parserNames.contains(matcher.group());
    }

    /** Descripción: Ejecuta la operación «semanticallyPlain» correspondiente a la responsabilidad de Token. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean semanticallyPlain(String body) {
        return !body.contains("+=") && !body.contains("?=") && !body.matches("(?s).*\\b[A-Za-z_][A-Za-z0-9_]*\\s*=.*")
                && !body.contains("{") && !body.contains("[") && !body.contains("=>");
    }

    /** Descripción: Ejecuta la operación «splitTopLevelAlternatives» correspondiente a la responsabilidad de Token. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> splitTopLevelAlternatives(String body) {
        ArrayList<String> parts = new ArrayList<>();
        int start = 0, paren = 0, bracket = 0, brace = 0;
        char quote = 0; boolean escape = false; boolean lineComment = false, blockComment = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i), next = i + 1 < body.length() ? body.charAt(i + 1) : '\0';
            if (lineComment) { if (ch == '\n' || ch == '\r') lineComment = false; continue; }
            if (blockComment) { if (ch == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (quote != 0) { if (escape) escape = false; else if (ch == '\\') escape = true; else if (ch == quote) quote = 0; continue; }
            if (ch == '/' && next == '/') { lineComment = true; i++; continue; }
            if (ch == '/' && next == '*') { blockComment = true; i++; continue; }
            if (ch == '\'' || ch == '"') { quote = ch; continue; }
            if (ch == '(') paren++; else if (ch == ')') paren = Math.max(0, paren - 1);
            else if (ch == '[') bracket++; else if (ch == ']') bracket = Math.max(0, bracket - 1);
            else if (ch == '{') brace++; else if (ch == '}') brace = Math.max(0, brace - 1);
            else if (ch == '|' && paren == 0 && bracket == 0 && brace == 0) { parts.add(body.substring(start, i)); start = i + 1; }
        }
        parts.add(body.substring(start));
        return parts;
    }

    /** Descripción: Ejecuta la operación «cyclic» correspondiente a la responsabilidad de Token. Entradas: component: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean cyclic(Set<String> component, Map<String, Set<String>> graph) {
        return component.size() > 1 || (component.size() == 1 && graph.getOrDefault(component.iterator().next(), Set.of()).contains(component.iterator().next()));
    }

    /** Descripción: Ejecuta la operación «cyclePath» correspondiente a la responsabilidad de Token. Entradas: start: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; allowed: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> cyclePath(String start, Map<String, Set<String>> graph, Set<String> allowed) {
        List<String> path = findCycle(start, start, graph, allowed, new LinkedHashSet<>());
        if (!path.isEmpty()) return path;
        ArrayList<String> fallback = new ArrayList<>(allowed.stream().sorted().toList());
        if (!fallback.isEmpty()) fallback.add(fallback.get(0));
        return List.copyOf(fallback);
    }

    /** Búsqueda iterativa de ciclo para no depender de la pila Java en SCCs grandes. */ /** Descripción: Obtiene la información correspondiente a «findCycle» dentro de BodyParser. Entradas: origin: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación; graph: dato de entrada necesario para la operación; allowed: dato de entrada necesario para la operación; ignoredPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> findCycle(String origin, String current, Map<String, Set<String>> graph, Set<String> allowed, LinkedHashSet<String> ignoredPath) {
        if (!allowed.contains(current)) return List.of();
        ArrayList<String> path = new ArrayList<>();
        HashSet<String> onPath = new HashSet<>();
        Deque<CycleFrame> frames = new ArrayDeque<>();
        path.add(current); onPath.add(current); frames.push(new CycleFrame(current, graph.getOrDefault(current, Set.of()).iterator()));
        while (!frames.isEmpty()) {
            CycleFrame frame = frames.peek();
            if (!frame.targets().hasNext()) {
                frames.pop(); String removed = path.remove(path.size() - 1); onPath.remove(removed); continue;
            }
            String target = frame.targets().next();
            if (!allowed.contains(target)) continue;
            if (target.equals(origin)) { ArrayList<String> found = new ArrayList<>(path); found.add(origin); return List.copyOf(found); }
            if (onPath.add(target)) { path.add(target); frames.push(new CycleFrame(target, graph.getOrDefault(target, Set.of()).iterator())); }
        }
        return List.of();
    }

    /** SCC iterativo (Kosaraju) O(V+E), sin profundidad recursiva ligada al tamaño de la gramática. */ /** Descripción: Ejecuta la operación «stronglyConnected» correspondiente a la responsabilidad de BodyParser. Entradas: graph: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<Set<String>> stronglyConnected(Map<String, Set<String>> graph) {
        ArrayList<String> finishOrder = new ArrayList<>(graph.size());
        HashSet<String> visited = new HashSet<>();
        for (String root : graph.keySet()) {
            if (!visited.add(root)) continue;
            Deque<GraphFrame> frames = new ArrayDeque<>();
            frames.push(new GraphFrame(root, graph.getOrDefault(root, Set.of()).iterator()));
            while (!frames.isEmpty()) {
                GraphFrame frame = frames.peek();
                boolean descended = false;
                while (frame.targets().hasNext()) {
                    String target = frame.targets().next();
                    if (!graph.containsKey(target) || !visited.add(target)) continue;
                    frames.push(new GraphFrame(target, graph.getOrDefault(target, Set.of()).iterator()));
                    descended = true; break;
                }
                if (!descended && !frame.targets().hasNext()) { finishOrder.add(frame.node()); frames.pop(); }
            }
        }
        LinkedHashMap<String, Set<String>> reverse = new LinkedHashMap<>();
        for (String node : graph.keySet()) reverse.put(node, new LinkedHashSet<>());
        for (Map.Entry<String, Set<String>> edge : graph.entrySet()) for (String target : edge.getValue()) {
            if (reverse.containsKey(target)) ((LinkedHashSet<String>) reverse.get(target)).add(edge.getKey());
        }
        ArrayList<Set<String>> result = new ArrayList<>();
        visited.clear();
        for (int i = finishOrder.size() - 1; i >= 0; i--) {
            String root = finishOrder.get(i); if (!visited.add(root)) continue;
            LinkedHashSet<String> component = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>(); pending.push(root);
            while (!pending.isEmpty()) {
                String node = pending.pop(); component.add(node);
                for (String target : reverse.getOrDefault(node, Set.of())) if (visited.add(target)) pending.push(target);
            }
            result.add(Set.copyOf(component));
        }
        return List.copyOf(result);
    }

    private record GraphFrame(String node, Iterator<String> targets) { }
    private record CycleFrame(String node, Iterator<String> targets) { }

    /** Descripción: Ejecuta la operación «immutableGraph» correspondiente a la responsabilidad de Token. Entradas: graph: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Set<String>> immutableGraph(Map<String, Set<String>> graph) {
        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>(); graph.forEach((key,value)->copy.put(key, Set.copyOf(value))); return Map.copyOf(copy);
    }

    /** Descripción: Ejecuta la operación «scan» correspondiente a la responsabilidad de Token. Entradas: documents: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<Rule> scan(List<GrammarSourceDocument> documents, GrammarTechnology technology) {
        ArrayList<Rule> rules = new ArrayList<>(); int order = 0;
        for (GrammarSourceDocument document : documents) {
            String source = document.content(); SourceTextIndex index = new SourceTextIndex(source);
            Pattern pattern = technology == GrammarTechnology.ANTLR ? ANTLR_HEADER : DECLARATIVE_HEADER;
            Matcher matcher = pattern.matcher(source); List<Header> headers = new ArrayList<>();
            while (matcher.find()) {
                String name; boolean explicitEntry; boolean parser;
                if (technology == GrammarTechnology.ANTLR) {
                    name = matcher.group(2); explicitEntry = false; parser = matcher.group(1) == null && Character.isLowerCase(name.charAt(0));
                } else {
                    name = matcher.group(3); explicitEntry = matcher.group(1) != null; parser = matcher.group(2) == null;
                }
                int nameGroup = technology == GrammarTechnology.ANTLR ? 2 : 3;
                headers.add(new Header(name, explicitEntry, parser, matcher.start(), matcher.end(), matcher.start(nameGroup), matcher.end(nameGroup)));
            }
            BitSet ignored = lexicalIgnored(source, technology != GrammarTechnology.ANTLR);
            for (int i = 0; i < headers.size(); i++) {
                Header header = headers.get(i); int nextHeader = i + 1 < headers.size() ? headers.get(i + 1).start() : source.length();
                int colon = source.indexOf(':', Math.max(header.nameEnd(), header.end() - 2));
                if (colon < 0 || colon >= nextHeader) colon = source.indexOf(':', header.nameEnd());
                if (colon < 0) continue;
                int semi = findVisible(source, ';', colon + 1, nextHeader, ignored); if (semi < 0) semi = nextHeader;
                int bodyStart = colon + 1, bodyEnd = semi;
                rules.add(new Rule(header.name(), document.id(), document.entryPoint(), index.range(header.nameStart(), header.nameEnd()),
                        index.range(bodyStart, bodyEnd), source.substring(bodyStart, bodyEnd), header.explicitEntry(), header.parser(), order++));
            }
        }
        return List.copyOf(rules);
    }

    /** Descripción: Obtiene la información correspondiente a «findVisible» dentro de Token. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; start: dato de entrada necesario para la operación; end: dato de entrada necesario para la operación; ignored: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int findVisible(String source, char target, int start, int end, BitSet ignored) {
        for (int i = Math.max(0,start); i < Math.min(source.length(), end); i++) if (!ignored.get(i) && source.charAt(i) == target) return i;
        return -1;
    }

    /** Descripción: Ejecuta la operación «lexicalIgnored» correspondiente a la responsabilidad de Token. Entradas: source: dato de entrada necesario para la operación; slashRegex: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static BitSet lexicalIgnored(String source, boolean slashRegex) {
        BitSet ignored = new BitSet(source.length()); final int NORMAL=0,SINGLE=1,DOUBLE=2,LINE=3,BLOCK=4,REGEX=5; int state=NORMAL; boolean escape=false;
        for (int i=0;i<source.length();i++) {
            char ch=source.charAt(i), next=i+1<source.length()?source.charAt(i+1):'\0';
            if (state==LINE) { if (ch=='\n'||ch=='\r') state=NORMAL; else ignored.set(i); continue; }
            if (state==BLOCK) { ignored.set(i); if(ch=='*'&&next=='/'){ignored.set(i+1);i++;state=NORMAL;} continue; }
            if (state!=NORMAL) { ignored.set(i); if(escape){escape=false;continue;} if(ch=='\\'){escape=true;continue;} if((state==SINGLE&&ch=='\'')||(state==DOUBLE&&ch=='"')||(state==REGEX&&ch=='/')) state=NORMAL; continue; }
            if(ch=='/'&&next=='/'){ignored.set(i);ignored.set(i+1);i++;state=LINE;continue;}
            if(ch=='/'&&next=='*'){ignored.set(i);ignored.set(i+1);i++;state=BLOCK;continue;}
            if(ch=='\''){ignored.set(i);state=SINGLE;continue;} if(ch=='"'){ignored.set(i);state=DOUBLE;continue;}
            if(slashRegex&&ch=='/'&&looksLikeRegexStart(source,i)){ignored.set(i);state=REGEX;}
        }
        return ignored;
    }

    /** Descripción: Ejecuta la operación «looksLikeRegexStart» correspondiente a la responsabilidad de Token. Entradas: source: dato de entrada necesario para la operación; offset: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean looksLikeRegexStart(String source, int offset) {
        int previous=offset-1; while(previous>=0&&Character.isWhitespace(source.charAt(previous)))previous--;
        if(previous<0)return true; char ch=source.charAt(previous); return ch==':'||ch=='='||ch=='|'||ch=='('||ch==',';
    }

    public enum RecursionKind { DIRECT_SAFE, INDIRECT_SAFE, DIRECT_LEFT, INDIRECT_LEFT, DIRECT_LEFT_NATIVE_SUPPORTED }

    public record Analysis(List<Rule> rules, Map<String,Set<String>> calls, Map<String,Set<String>> leftCorners,
            Map<String,Boolean> nullableRules, EntryInference entry, List<Recursion> recursions) {
        public Analysis { rules=rules==null?List.of():List.copyOf(rules); calls=calls==null?Map.of():Map.copyOf(calls); leftCorners=leftCorners==null?Map.of():Map.copyOf(leftCorners); nullableRules=nullableRules==null?Map.of():Map.copyOf(nullableRules); entry=entry==null?new EntryInference("","NONE",0,List.of(),""):entry; recursions=recursions==null?List.of():List.copyOf(recursions); }
        /** Descripción: Ejecuta la operación «ruleNames» correspondiente a la responsabilidad de Token. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> ruleNames(){ return rules.stream().map(Rule::name).distinct().toList(); }
    }
    public record EntryInference(String rule, String method, double confidence, List<String> candidates, String reason) {
        public EntryInference { rule=rule==null?"":rule; method=method==null?"":method; confidence=Math.max(0d,Math.min(1d,confidence)); candidates=candidates==null?List.of():List.copyOf(candidates); reason=reason==null?"":reason; }
    }
    public record Recursion(RecursionKind kind, List<String> cycle, List<String> rules, String documentId, TextRange range,
            boolean nativeSupported, Rewrite rewrite, boolean nullable) {
        public Recursion { cycle=cycle==null?List.of():List.copyOf(cycle); rules=rules==null?List.of():List.copyOf(rules); documentId=documentId==null?"":documentId; range=range==null?new TextRange(null,null):range; }
        /** Descripción: Ejecuta la operación «leftRecursive» correspondiente a la responsabilidad de Token. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean leftRecursive(){ return kind==RecursionKind.DIRECT_LEFT||kind==RecursionKind.INDIRECT_LEFT||kind==RecursionKind.DIRECT_LEFT_NATIVE_SUPPORTED; }
        /** Descripción: Ejecuta la operación «blocking» correspondiente a la responsabilidad de Token. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean blocking(){ return kind==RecursionKind.DIRECT_LEFT||kind==RecursionKind.INDIRECT_LEFT; }
    }
    public record Rewrite(String documentId, TextRange range, String replacement, boolean safeAutomatic, String detail) {
        public Rewrite { documentId=documentId==null?"":documentId; replacement=replacement==null?"":replacement; range=range==null?new TextRange(null,null):range; detail=detail==null?"":detail; }
    }
    public record Rule(String name, String documentId, boolean entryPointDocument, TextRange nameRange, TextRange bodyRange,
            String body, boolean explicitEntry, boolean parserRule, int order) {
        public Rule { name=name==null?"":name; documentId=documentId==null?"":documentId; body=body==null?"":body; nameRange=nameRange==null?new TextRange(null,null):nameRange; bodyRange=bodyRange==null?new TextRange(null,null):bodyRange; }
    }
    private record Header(String name, boolean explicitEntry, boolean parser, int start, int end, int nameStart, int nameEnd) { }

    private interface Expr {
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de Token. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean nullable(Map<String,Boolean> rules);
        /** Descripción: Ejecuta la operación «collectRefs» correspondiente a la responsabilidad de Token. Entradas: out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void collectRefs(Set<String> out);
        /** Descripción: Ejecuta la operación «collectFirstRules» correspondiente a la responsabilidad de Token. Entradas: nullable: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void collectFirstRules(Map<String,Boolean> nullable, Set<String> out);
    }
    private record EmptyExpr() implements Expr {
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de Token. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean nullable(Map<String,Boolean> rules){return true;} /** Descripción: Ejecuta la operación «collectRefs» correspondiente a la responsabilidad de Token. Entradas: out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectRefs(Set<String> out){} /** Descripción: Ejecuta la operación «collectFirstRules» correspondiente a la responsabilidad de Token. Entradas: nullable: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectFirstRules(Map<String,Boolean> nullable,Set<String> out){}
    }
    private record ConsumeExpr() implements Expr {
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de Token. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean nullable(Map<String,Boolean> rules){return false;} /** Descripción: Ejecuta la operación «collectRefs» correspondiente a la responsabilidad de Token. Entradas: out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectRefs(Set<String> out){} /** Descripción: Ejecuta la operación «collectFirstRules» correspondiente a la responsabilidad de Token. Entradas: nullable: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectFirstRules(Map<String,Boolean> nullable,Set<String> out){}
    }
    private record RuleExpr(String name) implements Expr {
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de Token. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean nullable(Map<String,Boolean> rules){return rules.getOrDefault(name,false);} /** Descripción: Ejecuta la operación «collectRefs» correspondiente a la responsabilidad de Token. Entradas: out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectRefs(Set<String> out){out.add(name);} /** Descripción: Ejecuta la operación «collectFirstRules» correspondiente a la responsabilidad de Token. Entradas: nullable: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectFirstRules(Map<String,Boolean> nullable,Set<String> out){out.add(name);}
    }
    private record SequenceExpr(List<Expr> children) implements Expr {
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de Token. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean nullable(Map<String,Boolean> rules){for(Expr child:children)if(!child.nullable(rules))return false;return true;}
        /** Descripción: Ejecuta la operación «collectRefs» correspondiente a la responsabilidad de Token. Entradas: out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectRefs(Set<String> out){children.forEach(child->child.collectRefs(out));}
        /** Descripción: Ejecuta la operación «collectFirstRules» correspondiente a la responsabilidad de Token. Entradas: nullable: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectFirstRules(Map<String,Boolean> nullable,Set<String> out){for(Expr child:children){child.collectFirstRules(nullable,out);if(!child.nullable(nullable))break;}}
    }
    private record ChoiceExpr(List<Expr> children) implements Expr {
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de Token. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean nullable(Map<String,Boolean> rules){for(Expr child:children)if(child.nullable(rules))return true;return false;}
        /** Descripción: Ejecuta la operación «collectRefs» correspondiente a la responsabilidad de Token. Entradas: out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectRefs(Set<String> out){children.forEach(child->child.collectRefs(out));}
        /** Descripción: Ejecuta la operación «collectFirstRules» correspondiente a la responsabilidad de Token. Entradas: nullable: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectFirstRules(Map<String,Boolean> nullable,Set<String> out){children.forEach(child->child.collectFirstRules(nullable,out));}
    }
    private record QuantifiedExpr(Expr child, char quantifier) implements Expr {
        /** Descripción: Ejecuta la operación «nullable» correspondiente a la responsabilidad de Token. Entradas: rules: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean nullable(Map<String,Boolean> rules){return quantifier=='?'||quantifier=='*'||child.nullable(rules);} /** Descripción: Ejecuta la operación «collectRefs» correspondiente a la responsabilidad de Token. Entradas: out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectRefs(Set<String> out){child.collectRefs(out);} /** Descripción: Ejecuta la operación «collectFirstRules» correspondiente a la responsabilidad de Token. Entradas: nullable: dato de entrada necesario para la operación; out: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void collectFirstRules(Map<String,Boolean> nullable,Set<String> out){child.collectFirstRules(nullable,out);}
    }

    private static final class BodyParser {
        private final List<Token> tokens; private final Set<String> parserRules;
        /** Parser iterativo del cuerpo: evita que paréntesis profundamente anidados consuman la pila Java. */
        /** Descripción: Inicializa una instancia de BodyParser con sus dependencias y datos de entrada. Entradas: body: dato de entrada necesario para la operación; parserRules: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ BodyParser(String body, Set<String> parserRules){this.tokens=tokenize(body);this.parserRules=parserRules;}
        /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de BodyParser. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Expr parse(){
            Deque<ParseFrame> frames=new ArrayDeque<>(); frames.push(new ParseFrame());
            for(int at=0;at<tokens.size();at++){
                Token token=tokens.get(at);
                if(token.type()==TokenType.PIPE){frames.peek().finishAlternative();continue;}
                if(token.type()==TokenType.LPAREN){frames.push(new ParseFrame());continue;}
                if(token.type()==TokenType.RPAREN){
                    if(frames.size()==1)continue;
                    Expr grouped=frames.pop().finish();
                    if(at+1<tokens.size()&&isQuantifier(tokens.get(at+1).type())) grouped=new QuantifiedExpr(grouped,tokens.get(++at).text().charAt(0));
                    frames.peek().add(grouped); continue;
                }
                if(token.type()==TokenType.IDENT&&at+1<tokens.size()&&tokens.get(at+1).type()==TokenType.ASSIGN){at++;continue;}
                Expr value=switch(token.type()){
                    case ACTION->new EmptyExpr();
                    case IDENT->parserRules.contains(token.text())?new RuleExpr(token.text()):new ConsumeExpr();
                    case LITERAL,CROSSREF->new ConsumeExpr();
                    default->null;
                };
                if(value==null)continue;
                if(at+1<tokens.size()&&isQuantifier(tokens.get(at+1).type())) value=new QuantifiedExpr(value,tokens.get(++at).text().charAt(0));
                frames.peek().add(value);
            }
            while(frames.size()>1){Expr grouped=frames.pop().finish();frames.peek().add(grouped);}
            return frames.pop().finish();
        }
        /** Descripción: Evalúa la condición «isQuantifier» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isQuantifier(TokenType type){return type==TokenType.QMARK||type==TokenType.STAR||type==TokenType.PLUS;}
    }
    private static final class ParseFrame {
        private final ArrayList<Expr> sequence=new ArrayList<>(); private final ArrayList<Expr> alternatives=new ArrayList<>();
        /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de BodyParser. Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void add(Expr value){sequence.add(value);}
        /** Descripción: Ejecuta la operación «finishAlternative» correspondiente a la responsabilidad de BodyParser. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void finishAlternative(){alternatives.add(sequence());sequence.clear();}
        /** Descripción: Ejecuta la operación «finish» correspondiente a la responsabilidad de BodyParser. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Expr finish(){if(!alternatives.isEmpty()){alternatives.add(sequence());return choice(alternatives);}return sequence();}
        /** Descripción: Ejecuta la operación «sequence» correspondiente a la responsabilidad de BodyParser. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Expr sequence(){return sequence.isEmpty()?new EmptyExpr():sequence.size()==1?sequence.get(0):new SequenceExpr(List.copyOf(sequence));}
        /** Descripción: Ejecuta la operación «choice» correspondiente a la responsabilidad de BodyParser. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Expr choice(List<Expr> values){
            ArrayList<Expr> flattened=new ArrayList<>();
            for(Expr value:values){if(value instanceof ChoiceExpr nested)flattened.addAll(nested.children());else flattened.add(value);}
            return flattened.size()==1?flattened.get(0):new ChoiceExpr(List.copyOf(flattened));
        }
    }

    private enum TokenType { IDENT,LITERAL,LPAREN,RPAREN,PIPE,QMARK,STAR,PLUS,ASSIGN,ACTION,CROSSREF,OTHER }
    private record Token(TokenType type,String text){}
    /** Descripción: Ejecuta la operación «tokenize» correspondiente a la responsabilidad de Token. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<Token> tokenize(String body){
        ArrayList<Token> out=new ArrayList<>(); int i=0;
        while(i<body.length()){
            char ch=body.charAt(i); if(Character.isWhitespace(ch)){i++;continue;}
            if(ch=='/'&&i+1<body.length()&&body.charAt(i+1)=='/'){i+=2;while(i<body.length()&&body.charAt(i)!='\n'&&body.charAt(i)!='\r')i++;continue;}
            if(ch=='/'&&i+1<body.length()&&body.charAt(i+1)=='*'){i+=2;while(i+1<body.length()&&!(body.charAt(i)=='*'&&body.charAt(i+1)=='/'))i++;i=Math.min(body.length(),i+2);continue;}
            if(ch=='\''||ch=='"'){char q=ch;int s=i++;boolean esc=false;while(i<body.length()){char c=body.charAt(i++);if(esc){esc=false;continue;}if(c=='\\'){esc=true;continue;}if(c==q)break;}out.add(new Token(TokenType.LITERAL,body.substring(s,i)));continue;}
            if(ch=='{'){int s=i++;int depth=1;while(i<body.length()&&depth>0){char c=body.charAt(i++);if(c=='{')depth++;else if(c=='}')depth--;}out.add(new Token(TokenType.ACTION,body.substring(s,i)));continue;}
            if(ch=='['){int s=i++;int depth=1;while(i<body.length()&&depth>0){char c=body.charAt(i++);if(c=='[')depth++;else if(c==']')depth--;}out.add(new Token(TokenType.CROSSREF,body.substring(s,i)));continue;}
            if(Character.isJavaIdentifierStart(ch)){int s=i++;while(i<body.length()&&Character.isJavaIdentifierPart(body.charAt(i)))i++;out.add(new Token(TokenType.IDENT,body.substring(s,i)));continue;}
            if(ch=='+'&&i+1<body.length()&&body.charAt(i+1)=='='){out.add(new Token(TokenType.ASSIGN,"+="));i+=2;continue;}
            if(ch=='?'&&i+1<body.length()&&body.charAt(i+1)=='='){out.add(new Token(TokenType.ASSIGN,"?="));i+=2;continue;}
            if(ch=='='){out.add(new Token(TokenType.ASSIGN,"="));i++;continue;}
            TokenType type=switch(ch){case '('->TokenType.LPAREN;case ')'->TokenType.RPAREN;case '|'->TokenType.PIPE;case '?'->TokenType.QMARK;case '*'->TokenType.STAR;case '+'->TokenType.PLUS;default->TokenType.OTHER;};out.add(new Token(type,Character.toString(ch)));i++;
        }
        return List.copyOf(out);
    }
}

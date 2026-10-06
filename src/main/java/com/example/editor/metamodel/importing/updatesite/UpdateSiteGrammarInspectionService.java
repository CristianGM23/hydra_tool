package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.editor.grammar.application.GrammarRuleGraphAnalyzer;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;

/** Inspección estática de gramáticas; no ejecuta generadores ni parsers externos. */
@Component
public class UpdateSiteGrammarInspectionService {
    private static final Pattern GRAMMAR = Pattern.compile("(?im)^\\s*grammar\\s+([A-Za-z_][\\w.]*)");
    private static final Pattern IMPORT = Pattern.compile("(?im)^\\s*(?:import|with)\\s+[^\\r\\n]+$");
    private static final Pattern XTEXT_RULE = Pattern.compile("(?m)^\\s*(?:(terminal|enum|fragment)\\s+)?([A-Za-z_][\\w]*)\\s*(?:returns\\s+[^:]+)?\\s*:");
    private static final Pattern LANGIUM_RULE = Pattern.compile("(?m)^\\s*(?:(terminal|hidden|fragment|entry)\\s+)?([A-Za-z_][\\w]*)\\s*(?:infers\\s+[^:]+)?\\s*:");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?:\\?=|\\+=|=)");
    private static final Pattern CROSS_REFERENCE = Pattern.compile("\\[[^\\]]+\\]");
    private static final Pattern ACTION = Pattern.compile("\\{[^}]+\\}");

    /** Descripción: Ejecuta la operación «inspect» correspondiente a la responsabilidad de UpdateSiteGrammarInspectionService. Entradas: repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<UpdateSiteGrammarInspection> inspect(UpdateSiteRepositoryContent repository) {
        List<UpdateSiteGrammarInspection> result = new ArrayList<>();
        for(Map.Entry<String, byte[]> entry : repository.files().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            UpdateSiteArtifactKind kind = UpdateSiteArtifactKinds.fromPath(entry.getKey());
            if(kind != UpdateSiteArtifactKind.XTEXT_GRAMMAR && kind != UpdateSiteArtifactKind.LANGIUM_GRAMMAR) continue;
            result.add(inspect(entry.getKey(), entry.getValue(), kind));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «inspect» correspondiente a la responsabilidad de UpdateSiteGrammarInspectionService. Entradas: path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteGrammarInspection inspect(String path, byte[] bytes, UpdateSiteArtifactKind kind) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        GrammarTechnology technology = kind == UpdateSiteArtifactKind.XTEXT_GRAMMAR ? GrammarTechnology.XTEXT : GrammarTechnology.LANGIUM;
        String language = technology == GrammarTechnology.XTEXT ? "Xtext" : "Langium";
        Pattern rulePattern = technology == GrammarTechnology.XTEXT ? XTEXT_RULE : LANGIUM_RULE;
        Matcher grammarMatcher = GRAMMAR.matcher(text);
        String name = grammarMatcher.find() ? grammarMatcher.group(1) : fileStem(path);
        Matcher ruleMatcher = rulePattern.matcher(text);
        Map<String, Integer> ruleCounts = new LinkedHashMap<>();
        List<String> parserRuleBodies = new ArrayList<>();
        int parserRules = 0;
        int terminalRules = 0;
        while(ruleMatcher.find()) {
            String modifier = ruleMatcher.group(1);
            String rule = ruleMatcher.group(2);
            ruleCounts.merge(rule, 1, Integer::sum);
            boolean terminalRule = modifier != null && (modifier.equalsIgnoreCase("terminal")
                    || modifier.equalsIgnoreCase("fragment") || modifier.equalsIgnoreCase("hidden"));
            if(terminalRule) terminalRules++;
            else parserRules++;
            int start = ruleMatcher.end();
            int end = text.indexOf(';', start);
            String body = end < 0 ? text.substring(start) : text.substring(start, end);
            if(!terminalRule) parserRuleBodies.add(body);
        }
        List<String> duplicates = ruleCounts.entrySet().stream().filter(item -> item.getValue() > 1)
                .map(Map.Entry::getKey).sorted().toList();

        GrammarRuleGraphAnalyzer.Analysis graph = GrammarRuleGraphAnalyzer.analyze(definition(technology, name, text));
        Set<String> recursion = new LinkedHashSet<>();
        Set<String> leftRecursion = new LinkedHashSet<>();
        Set<String> automatic = new LinkedHashSet<>();
        List<String> diagnostics = new ArrayList<>();
        for(GrammarRuleGraphAnalyzer.Recursion item : graph.recursions()) {
            recursion.addAll(item.rules());
            if(item.leftRecursive()) leftRecursion.addAll(item.rules());
            if(item.rewrite() != null) automatic.addAll(item.rules());
            switch(item.kind()) {
                case DIRECT_LEFT -> diagnostics.add("Direct left recursion was detected in " + String.join(" -> ", item.cycle())
                        + ". The rule calls itself before consuming input, so predictive parsers may recurse without advancing. "
                        + (item.rewrite() == null ? "Rewrite the rule using a base alternative followed by repetition."
                                : "HYDRA can propose a repetition-based rewrite and will reanalyze the result before it is applied."));
                case INDIRECT_LEFT -> diagnostics.add("Indirect left recursion was detected in " + String.join(" -> ", item.cycle())
                        + ". Several rules can return to the origin without consuming input. Refactor the cycle around a non-recursive base before generation.");
                case DIRECT_LEFT_NATIVE_SUPPORTED -> diagnostics.add("Direct left recursion was detected in " + String.join(" -> ", item.cycle())
                        + ". ANTLR 4 supports this direct pattern natively, so HYDRA keeps it and only flags it for portability review.");
                case DIRECT_SAFE, INDIRECT_SAFE -> diagnostics.add("Recursive grammar structure was identified in " + String.join(" -> ", item.cycle())
                        + ". Input is consumed before the recursive call, so this is not left recursion and no corrective rewrite is required.");
            }
        }
        if(parserRules == 0) diagnostics.add("No parser rules were recognized; the file may use unsupported syntax or be incomplete.");
        if(!duplicates.isEmpty()) diagnostics.add("Duplicate rule names were detected: " + String.join(", ", duplicates));
        if(!text.contains("grammar ")) diagnostics.add("No explicit grammar declaration was found; the language name was derived from the file name.");
        if(!graph.entry().rule().isBlank()) diagnostics.add("HYDRA inferred entry rule '" + graph.entry().rule() + "' using "
                + graph.entry().method() + " with " + Math.round(graph.entry().confidence() * 100d) + "% confidence. " + graph.entry().reason());
        String parserText = String.join("\n", parserRuleBodies);
        return new UpdateSiteGrammarInspection(path, language, name, parserRules, terminalRules,
                count(IMPORT, text), count(ASSIGNMENT, parserText), count(CROSS_REFERENCE, parserText), count(ACTION, parserText),
                duplicates, leftRecursion.stream().sorted().toList(), graph.entry().rule(), graph.entry().method(), graph.entry().confidence(),
                recursion.stream().sorted().toList(), automatic.stream().sorted().toList(), diagnostics);
    }

    /** Descripción: Ejecuta la operación «definition» correspondiente a la responsabilidad de UpdateSiteGrammarInspectionService. Entradas: technology: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarDefinitionModel definition(GrammarTechnology technology, String name, String text) {
        String extension = technology == GrammarTechnology.XTEXT ? "xtext" : "langium";
        GrammarSourceDocument document = new GrammarSourceDocument("inspection-main", "inspection." + extension, extension, text, "", true, false);
        return new GrammarDefinitionModel("1.0", "update-site-inspection", name, technology, GrammarAuthoringMode.GRAMMAR_FIRST,
                "", technology == GrammarTechnology.XTEXT ? "xtext-2.44-lsp" : "langium-4.3-browser", document.id(), "", List.of(document),
                GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of("hydra.entryRule.mode", "AUTO"));
    }

    /** Descripción: Ejecuta la operación «count» correspondiente a la responsabilidad de UpdateSiteGrammarInspectionService. Entradas: pattern: dato de entrada necesario para la operación; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private int count(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while(matcher.find()) count++;
        return count;
    }

    /** Descripción: Ejecuta la operación «fileStem» correspondiente a la responsabilidad de UpdateSiteGrammarInspectionService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileStem(String path) {
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String file = slash < 0 ? normalized : normalized.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        return (dot < 0 ? file : file.substring(0, dot)).replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
    }
}

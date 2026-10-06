package com.example.editor.grammar.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Neutral, provider-independent presentation contract for textual editors derived from a grammar.
 * The contract contains only bounded declarative values; it never accepts CSS or executable code.
 */
public record GrammarTextualEditorCustomization(
        String fontFamily,
        int fontSize,
        int lineHeight,
        String foreground,
        String background,
        String keywords,
        Map<String, String> roleStyles,
        Map<String, String> symbolStyles,
        Map<String, HoverDefinition> hovers) {

    public static final String PREFIX = "hydra.textual.";
    private static final int MAX_KEYWORDS = 128;
    private static final int MAX_HOVERS = 128;
    private static final int MAX_ROLES = 32;
    private static final int MAX_SYMBOL_STYLES = 256;
    private static final Set<String> ROLES = Set.of("KEYWORD","RULE","TOKEN","REFERENCE","STRING","NUMBER","COMMENT","OPERATOR","PUNCTUATION","TYPE","ATTRIBUTE","ENUM_LITERAL");
    private static final Set<String> SYMBOL_KINDS = Set.of("RULE", "TERMINAL");

    public GrammarTextualEditorCustomization {
        fontFamily = safeFont(fontFamily);
        fontSize = clamp(fontSize, 11, 24, 14);
        lineHeight = clamp(lineHeight, fontSize, 48, Math.max(18, Math.round(fontSize * 1.5f)));
        foreground = color(foreground, "#1f2937");
        background = color(background, "#ffffff");
        keywords = sanitizeKeywords(keywords);
        roleStyles = sanitizeRoleStyles(roleStyles);
        symbolStyles = sanitizeSymbolStyles(symbolStyles);
        hovers = sanitizeHovers(hovers);
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de HoverDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarTextualEditorCustomization defaults() {
        return new GrammarTextualEditorCustomization("ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",14,21,"#1f2937","#ffffff","",
                Map.of("KEYWORD","#1d4ed8|1|0","RULE","#7c3aed|1|0","TOKEN","#047857|0|0","REFERENCE","#b45309|0|0","STRING","#047857|0|0","NUMBER","#b45309|0|0","COMMENT","#64748b|0|1"), Map.of(), Map.of());
    }

    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de HoverDefinition. Entradas: grammar: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarTextualEditorCustomization from(GrammarDefinitionModel grammar) {
        if (grammar == null) return defaults();
        Map<String,String> settings = grammar.settings();
        GrammarTextualEditorCustomization base = defaults();
        Map<String,String> roles = new LinkedHashMap<>(base.roleStyles());
        Map<String,String> symbols = new LinkedHashMap<>();
        Map<String,HoverDefinition> hovers = new LinkedHashMap<>();
        settings.forEach((key,value) -> {
            if (key.startsWith(PREFIX + "role.")) {
                String role = key.substring((PREFIX + "role.").length()).toUpperCase();
                if (ROLES.contains(role)) roles.put(role, value);
            }
        });
        settings.forEach((key,value) -> {
            if (!key.startsWith(PREFIX + "symbol.")) return;
            String rest = key.substring((PREFIX + "symbol.").length());
            int dot = rest.indexOf('.');
            if (dot <= 0) return;
            String kind = normalizeSymbolKind(rest.substring(0, dot));
            String name = rest.substring(dot + 1);
            if (kind != null && validSymbolName(name)) symbols.put(kind + ":" + name, value);
        });
        settings.forEach((key,value) -> {
            if (!key.startsWith(PREFIX + "hover.")) return;
            String rest = key.substring((PREFIX + "hover.").length());
            int dot = rest.lastIndexOf('.');
            if (dot <= 0) return;
            String symbol = rest.substring(0,dot);
            String field = rest.substring(dot+1);
            HoverDefinition current = hovers.getOrDefault(symbol, new HoverDefinition("",""));
            hovers.put(symbol, "title".equals(field) ? new HoverDefinition(value,current.description()) : "description".equals(field) ? new HoverDefinition(current.title(),value) : current);
        });
        RuntimeProjection projection = runtimeProjection(grammar, settings.getOrDefault(PREFIX + "keywords", ""), symbols, hovers);
        return new GrammarTextualEditorCustomization(
                settings.getOrDefault(PREFIX + "fontFamily", base.fontFamily()),
                integer(settings.get(PREFIX + "fontSize"), base.fontSize()),
                integer(settings.get(PREFIX + "lineHeight"), base.lineHeight()),
                settings.getOrDefault(PREFIX + "foreground", base.foreground()),
                settings.getOrDefault(PREFIX + "background", base.background()),
                projection.keywords(), roles, projection.symbolStyles(), projection.hovers());
    }

    private record RuntimeProjection(String keywords, Map<String,String> symbolStyles, Map<String,HoverDefinition> hovers) { }

    /**
     * Projects rule/terminal personalization to the concrete lexemes that users edit in generated models.
     * Persisted keys remain stable (RULE:Name / TERMINAL:Name); aliases are runtime-only so no user setting is rewritten.
     */
    /** Descripción: Procesa la operación «runtimeProjection» coordinando las responsabilidades de HoverDefinition. Entradas: grammar: dato de entrada necesario para la operación; configuredKeywords: configuración que gobierna el comportamiento; configuredSymbols: configuración que gobierna el comportamiento; configuredHovers: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private static RuntimeProjection runtimeProjection(GrammarDefinitionModel grammar, String configuredKeywords,
            Map<String,String> configuredSymbols, Map<String,HoverDefinition> configuredHovers) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if(configuredKeywords != null) java.util.Arrays.stream(configuredKeywords.split("[,\\s]+"))
                .map(String::trim).filter(v -> !v.isBlank()).forEach(keywords::add);
        Map<String,String> symbols = new LinkedHashMap<>(configuredSymbols);
        Map<String,HoverDefinition> hovers = new LinkedHashMap<>(configuredHovers);
        if(grammar == null) return new RuntimeProjection(String.join(",", keywords), symbols, hovers);

        Pattern rulePattern = Pattern.compile("(?ms)^(?!\\s*(?:terminal|hidden\\s+terminal)\\b)\\s*(?:entry\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*(?:returns\\s+[^:]+)?\\s*:\\s*(.*?);");
        Pattern literalPattern = Pattern.compile("['\\\"]([A-Za-z_][A-Za-z0-9_-]{0,47})['\\\"]");
        for(GrammarSourceDocument document : grammar.documents()) {
            Matcher rules = rulePattern.matcher(document.content());
            while(rules.find()) {
                String rule = rules.group(1);
                if(grammar.technology() == GrammarTechnology.ANTLR && rule.equals(rule.toUpperCase(java.util.Locale.ROOT))) continue;
                Matcher literals = literalPattern.matcher(rules.group(2));
                while(literals.find()) {
                    String literal = literals.group(1);
                    keywords.add(literal);
                    String style = configuredSymbols.get("RULE:" + rule);
                    if(style != null) symbols.putIfAbsent("RULE:" + literal, style);
                    HoverDefinition hover = configuredHovers.get(rule);
                    if(hover != null) hovers.putIfAbsent(literal, hover);
                }
            }
        }
        return new RuntimeProjection(keywords.stream().limit(MAX_KEYWORDS).reduce((a,b) -> a + "," + b).orElse(""), symbols, hovers);
    }

    /** Descripción: Actualiza el estado gestionado por HoverDefinition mediante la operación «applyTo». Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String,String> applyTo(Map<String,String> source) {
        Map<String,String> result = new LinkedHashMap<>();
        if (source != null) source.forEach((k,v) -> { if (!k.startsWith(PREFIX)) result.put(k,v); });
        result.put(PREFIX + "fontFamily", fontFamily);
        result.put(PREFIX + "fontSize", Integer.toString(fontSize));
        result.put(PREFIX + "lineHeight", Integer.toString(lineHeight));
        result.put(PREFIX + "foreground", foreground);
        result.put(PREFIX + "background", background);
        result.put(PREFIX + "keywords", keywords);
        roleStyles.forEach((role,value) -> result.put(PREFIX + "role." + role, value));
        symbolStyles.forEach((key,value) -> {
            int colon = key.indexOf(':');
            if (colon <= 0) return;
            result.put(PREFIX + "symbol." + key.substring(0, colon) + "." + key.substring(colon + 1), value);
        });
        hovers.forEach((symbol,value) -> {
            result.put(PREFIX + "hover." + symbol + ".title", value.title());
            result.put(PREFIX + "hover." + symbol + ".description", value.description());
        });
        return Map.copyOf(result);
    }

    public record HoverDefinition(String title, String description) {
        public HoverDefinition { title = text(title, 96); description = text(description, 600); }
    }

    /** Descripción: Ejecuta la operación «sanitizeRoleStyles» correspondiente a la responsabilidad de HoverDefinition. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> sanitizeRoleStyles(Map<String,String> values) {
        Map<String,String> result = new LinkedHashMap<>();
        if (values != null) values.entrySet().stream().limit(MAX_ROLES).forEach(entry -> {
            String role = text(entry.getKey(), 32).toUpperCase();
            if (!ROLES.contains(role)) return;
            result.put(role, sanitizeStyle(entry.getValue(), "#1f2937"));
        });
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «sanitizeSymbolStyles» correspondiente a la responsabilidad de HoverDefinition. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> sanitizeSymbolStyles(Map<String,String> values) {
        Map<String,String> result = new LinkedHashMap<>();
        if (values != null) values.entrySet().stream().limit(MAX_SYMBOL_STYLES).forEach(entry -> {
            String key = text(entry.getKey(), 96);
            int colon = key.indexOf(':');
            if (colon <= 0) return;
            String kind = normalizeSymbolKind(key.substring(0, colon));
            String name = key.substring(colon + 1);
            if (kind == null || !validSymbolName(name)) return;
            result.put(kind + ":" + name, sanitizeStyle(entry.getValue(), "RULE".equals(kind) ? "#7c3aed" : "#047857"));
        });
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «sanitizeStyle» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse; fallbackColor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String sanitizeStyle(String value, String fallbackColor) {
        String[] parts = text(value, 64).split("\\|", -1);
        String c = color(parts.length > 0 ? parts[0] : "", fallbackColor);
        String bold = parts.length > 1 && "1".equals(parts[1]) ? "1" : "0";
        String italic = parts.length > 2 && "1".equals(parts[2]) ? "1" : "0";
        return c + "|" + bold + "|" + italic;
    }

    /** Descripción: Ejecuta la operación «sanitizeHovers» correspondiente a la responsabilidad de HoverDefinition. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,HoverDefinition> sanitizeHovers(Map<String,HoverDefinition> values) {
        Map<String,HoverDefinition> result = new LinkedHashMap<>();
        if (values != null) values.entrySet().stream().limit(MAX_HOVERS).forEach(entry -> {
            String symbol = text(entry.getKey(), 80);
            if (validSymbolName(symbol) && entry.getValue() != null) result.put(symbol, entry.getValue());
        });
        return Map.copyOf(result);
    }
    /** Descripción: Ejecuta la operación «sanitizeKeywords» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String sanitizeKeywords(String value) {
        if (value == null || value.isBlank()) return "";
        return java.util.Arrays.stream(value.split("[,\\s]+"))
                .map(v -> text(v, 48)).filter(v -> v.matches("[A-Za-z_][A-Za-z0-9_-]{0,47}"))
                .distinct().limit(MAX_KEYWORDS).reduce((a,b) -> a + "," + b).orElse("");
    }
    /** Descripción: Ejecuta la operación «normalizeSymbolKind» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeSymbolKind(String value) {
        String kind = text(value, 16).toUpperCase();
        if ("TOKEN".equals(kind)) kind = "TERMINAL";
        return SYMBOL_KINDS.contains(kind) ? kind : null;
    }
    /** Descripción: Ejecuta la operación «validSymbolName» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean validSymbolName(String value) { return value != null && value.matches("[A-Za-z_][A-Za-z0-9_.:-]{0,79}"); }
    /** Descripción: Ejecuta la operación «safeFont» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safeFont(String value) { String v=text(value,160); return v.matches("[A-Za-z0-9 ,.'\"_-]{1,160}")?v:defaultsFont(); }
    /** Descripción: Ejecuta la operación «defaultsFont» correspondiente a la responsabilidad de HoverDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String defaultsFont(){ return "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"; }
    /** Descripción: Ejecuta la operación «color» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String color(String value,String fallback){ String v=text(value,16); return v.matches("#[0-9A-Fa-f]{6}")?v.toLowerCase():fallback; }
    /** Descripción: Ejecuta la operación «integer» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int integer(String value,int fallback){ try{return Integer.parseInt(value);}catch(RuntimeException ignored){return fallback;} }
    /** Descripción: Ejecuta la operación «clamp» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse; min: dato de entrada necesario para la operación; max: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int clamp(int value,int min,int max,int fallback){ return value<min||value>max?fallback:value; }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HoverDefinition. Entradas: value: valor de entrada que debe procesarse; max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value,int max){ if(value==null)return "";String v=value.strip().replace('\n',' ').replace('\r',' ');return v.length()>max?v.substring(0,max):v; }
}

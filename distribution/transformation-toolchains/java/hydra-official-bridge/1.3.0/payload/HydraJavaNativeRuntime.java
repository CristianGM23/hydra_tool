import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone Java implementation of HYDRA's demonstrated native M2M/M2T subset. */
public final class HydraJavaNativeRuntime {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*(id|classId|attr:[A-Za-z0-9][A-Za-z0-9._:-]{0,239})\\s*}}", Pattern.MULTILINE);
    private HydraJavaNativeRuntime() {}

    public static void main(String[] args) {
        if (args.length != 1) fail(64, "HYDRA Java native runtime expects one bridge request file");
        Path requestPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path workspace = requestPath.getParent();
        Map<String,Object> request = object(readJson(requestPath));
        String direction = text(request.get("direction"));
        if (direction.isBlank()) direction = "FORWARD";
        Map<String,Object> source = object(readJson(safe(workspace, text(request.get("sourceModelFile")))));
        Map<String,Object> plan = object(readJson(workspace.resolve("export/native/native-plan.json")));
        String transformationKind = defaultText(request.get("kind"), defaultText(plan.get("kind"), "M2M"));
        if ("M2T".equals(transformationKind)) {
            executeM2t(workspace, request, source, plan, direction);
            return;
        }
        if (!"M2M".equals(transformationKind)) fail(78, "HYDRA Java native runtime received an unsupported transformation kind");

        Set<String> roots = new LinkedHashSet<>(strings(source.get("rootElementIds")));
        List<Object> outputElements = new ArrayList<>();
        List<Object> outputRoots = new ArrayList<>();
        int counter = 0;
        for (Object rawSource : list(source.get("elements"))) {
            Map<String,Object> sourceElement = object(rawSource);
            String sourceId = text(sourceElement.get("id"));
            String classId = text(sourceElement.get("classId"));
            Map<String,Object> attributes = objectOrEmpty(sourceElement.get("attributes"));
            for (Object rawRule : list(plan.get("rules"))) {
                Map<String,Object> rule = object(rawRule);
                if (!strings(rule.get("sourceTypeIds")).contains(classId)) continue;
                String targetId = "native-" + (++counter);
                Map<String,Object> targetAttributes = new LinkedHashMap<>();
                for (Object rawBinding : list(rule.get("bindings"))) {
                    Map<String,Object> binding = object(rawBinding);
                    String kind = text(binding.get("kind"));
                    String targetFeature = text(binding.get("targetFeatureId"));
                    if ("DIRECT".equals(kind)) targetAttributes.put(targetFeature, attributes.get(text(binding.get("sourceFeatureId"))));
                    else if ("CONSTANT".equals(kind)) targetAttributes.put(targetFeature, binding.get("literalValue"));
                    else fail(78, "HYDRA Java native runtime received an unsupported binding kind");
                }
                Map<String,Object> target = new LinkedHashMap<>();
                target.put("id", targetId);
                target.put("classId", text(rule.get("targetTypeId")));
                target.put("attributes", targetAttributes);
                target.put("annotations", Map.of());
                outputElements.add(target);
                if (roots.contains(sourceId)) outputRoots.add(targetId);
            }
        }

        String seed = text(plan.get("transformationId")) + "|" + direction + "|" + text(source.get("id"));
        String modelId = "native-model-" + sha256(seed).substring(0, 32);
        Map<String,Object> targetModel = new LinkedHashMap<>();
        targetModel.put("schemaVersion", "1.0");
        targetModel.put("id", modelId);
        targetModel.put("metamodelId", text(plan.get("targetMetamodelId")));
        targetModel.put("metamodelEtag", text(plan.get("targetMetamodelFingerprint")));
        targetModel.put("name", text(source.get("name")) + " native " + text(plan.get("transformationName")));
        targetModel.put("rootElementIds", outputRoots);
        targetModel.put("elements", outputElements);
        targetModel.put("relations", List.of());
        targetModel.put("annotations", Map.of("hydra.native.runtime", "JAVA", "hydra.transformation.direction", direction));

        Path outputDirectory = safe(workspace, text(request.get("outputDirectory")));
        try { Files.createDirectories(outputDirectory); }
        catch (IOException exception) { fail(70, "HYDRA Java native runtime could not create output directory"); }
        Path targetFile = outputDirectory.resolve("target-model.json");
        writeJson(targetFile, targetModel);

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", defaultText(request.get("schemaVersion"), "1.0"));
        result.put("transformationId", text(request.get("transformationId")));
        result.put("language", "JAVA");
        result.put("kind", "M2M");
        result.put("direction", direction);
        result.put("targetModelFile", unix(workspace.relativize(targetFile)));
        result.put("artifacts", List.of());
        result.put("diagnostics", List.of("Executed by HYDRA Official Java Native Runtime"));
        writeJson(safe(workspace, text(request.get("resultFile"))), result);
    }

    private static void executeM2t(Path workspace, Map<String,Object> request, Map<String,Object> source,
            Map<String,Object> plan, String direction) {
        if (!"FORWARD".equals(direction)) fail(78, "HYDRA Java native M2T runtime supports FORWARD only");
        Map<String,String> artifacts = new LinkedHashMap<>();
        for (Object rawElement : list(source.get("elements"))) {
            Map<String,Object> element = object(rawElement);
            String classId = text(element.get("classId"));
            for (Object rawRule : list(plan.get("generationRules"))) {
                Map<String,Object> rule = object(rawRule);
                if (!classId.equals(text(rule.get("contextTypeId")))) continue;
                String logical = validateLogicalPath(render(text(rule.get("outputPathTemplate")), element));
                String content = render(text(rule.get("contentTemplate")), element);
                if (artifacts.containsKey(logical) && "FAIL".equals(defaultText(rule.get("overwritePolicy"), "FAIL")))
                    fail(78, "Generated artifact collision: " + logical);
                artifacts.put(logical, content);
            }
        }
        Path outputDirectory = safe(workspace, text(request.get("outputDirectory")));
        try { Files.createDirectories(outputDirectory); }
        catch (IOException exception) { fail(70, "HYDRA Java native runtime could not create output directory"); }
        List<Object> evidence = new ArrayList<>();
        int index = 0;
        for (var entry : artifacts.entrySet()) {
            Path file = outputDirectory.resolve("artifact-" + String.format("%06d", ++index) + ".txt");
            try { Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8); }
            catch (IOException exception) { fail(70, "HYDRA Java native runtime could not persist generated artifact"); }
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("path", entry.getKey());
            item.put("file", unix(workspace.relativize(file)));
            evidence.add(item);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", defaultText(request.get("schemaVersion"), "1.0"));
        result.put("transformationId", text(request.get("transformationId")));
        result.put("language", "JAVA");
        result.put("kind", "M2T");
        result.put("direction", direction);
        result.put("targetModelFile", "");
        result.put("artifacts", evidence);
        result.put("diagnostics", List.of("Executed by HYDRA Official Java Native M2T Runtime"));
        writeJson(safe(workspace, text(request.get("resultFile"))), result);
    }

    private static String render(String template, Map<String,Object> element) {
        Matcher matcher = TOKEN.matcher(template == null ? "" : template);
        StringBuffer out = new StringBuffer();
        Map<String,Object> attributes = objectOrEmpty(element.get("attributes"));
        while (matcher.find()) {
            String token = matcher.group(1);
            Object value = "id".equals(token) ? element.get("id")
                    : "classId".equals(token) ? element.get("classId")
                    : attributes.get(token.substring("attr:".length()));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String validateLogicalPath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("../")
                || normalized.equals("..") || normalized.matches("^[A-Za-z]:.*"))
            fail(78, "Generated artifact path must be relative and portable: " + path);
        return normalized;
    }

    private static Path safe(Path workspace, String relative) {
        String raw = relative == null ? "" : relative.trim().replace('\\', '/');
        if (raw.isBlank() || raw.startsWith("/") || raw.matches("^[A-Za-z]:.*")) fail(64, "HYDRA Java native runtime received an invalid workspace-relative path");
        Path target = workspace.resolve(raw).normalize().toAbsolutePath();
        Path base = workspace.toAbsolutePath().normalize();
        if (!target.startsWith(base)) fail(64, "HYDRA Java native runtime path escapes the isolated workspace");
        return target;
    }

    private static Object readJson(Path path) {
        try { return new Json(Files.readString(path, StandardCharsets.UTF_8)).parse(); }
        catch (IOException | RuntimeException exception) { fail(65, "HYDRA Java native runtime could not read JSON evidence"); return null; }
    }

    private static void writeJson(Path path, Object value) {
        try { Files.writeString(path, Json.write(value) + "\n", StandardCharsets.UTF_8); }
        catch (IOException exception) { fail(70, "HYDRA Java native runtime could not persist evidence"); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?>)) {
            fail(65, "HYDRA Java native runtime expected a JSON object");
            return Map.of();
        }
        return (Map<String,Object>) value;
    }

    private static Map<String,Object> objectOrEmpty(Object value) {
        return value == null ? new LinkedHashMap<>() : object(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?>)) {
            fail(65, "HYDRA Java native runtime expected a JSON array");
            return List.of();
        }
        return (List<Object>) value;
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        for (Object item : list(value)) result.add(text(item));
        return result;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String defaultText(Object value, String fallback) { String result = text(value); return result.isBlank() ? fallback : result; }
    private static String unix(Path path) { return path.toString().replace('\\', '/'); }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private static void fail(int code, String message) {
        System.err.println(message);
        System.exit(code);
    }

    /** Tiny dependency-free JSON reader/writer for isolated bridge evidence. */
    private static final class Json {
        private final String source;
        private int index;
        Json(String source) { this.source = source == null ? "" : source; }

        Object parse() {
            Object value = value();
            whitespace();
            if (index != source.length()) throw new IllegalArgumentException("Trailing JSON");
            return value;
        }

        private Object value() {
            whitespace();
            if (index >= source.length()) throw new IllegalArgumentException("Unexpected end");
            char c = source.charAt(index);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't' && token("true")) return Boolean.TRUE;
            if (c == 'f' && token("false")) return Boolean.FALSE;
            if (c == 'n' && token("null")) return null;
            return number();
        }

        private Map<String,Object> object() {
            expect('{');
            Map<String,Object> map = new LinkedHashMap<>();
            whitespace();
            if (peek('}')) { index++; return map; }
            while (true) {
                whitespace();
                String key = string();
                whitespace(); expect(':');
                map.put(key, value());
                whitespace();
                if (peek('}')) { index++; return map; }
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> values = new ArrayList<>();
            whitespace();
            if (peek(']')) { index++; return values; }
            while (true) {
                values.add(value());
                whitespace();
                if (peek(']')) { index++; return values; }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (index >= source.length()) throw new IllegalArgumentException("Invalid escape");
                char e = source.charAt(index++);
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw new IllegalArgumentException("Invalid unicode escape");
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape");
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Number number() {
            int start = index;
            if (peek('-')) index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            if (peek('.')) { index++; while (index < source.length() && Character.isDigit(source.charAt(index))) index++; }
            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            String raw = source.substring(start, index);
            if (raw.isBlank() || "-".equals(raw)) throw new IllegalArgumentException("Invalid number");
            BigDecimal decimal = new BigDecimal(raw);
            try { return decimal.scale() <= 0 ? decimal.longValueExact() : decimal; }
            catch (ArithmeticException exception) { return decimal; }
        }

        private boolean token(String token) {
            if (!source.startsWith(token, index)) return false;
            index += token.length();
            return true;
        }
        private void whitespace() { while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++; }
        private boolean peek(char c) { return index < source.length() && source.charAt(index) == c; }
        private void expect(char c) { whitespace(); if (!peek(c)) throw new IllegalArgumentException("Expected " + c); index++; }

        static String write(Object value) {
            StringBuilder out = new StringBuilder();
            writeValue(out, value);
            return out.toString();
        }

        private static void writeValue(StringBuilder out, Object value) {
            if (value == null) { out.append("null"); return; }
            if (value instanceof String text) { quote(out, text); return; }
            if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
            if (value instanceof Map<?,?> map) {
                out.append('{');
                boolean first = true;
                for (var entry : map.entrySet()) {
                    if (!first) out.append(','); first = false;
                    quote(out, String.valueOf(entry.getKey())); out.append(':'); writeValue(out, entry.getValue());
                }
                out.append('}'); return;
            }
            if (value instanceof Iterable<?> iterable) {
                out.append('['); boolean first = true;
                for (Object item : iterable) { if (!first) out.append(','); first = false; writeValue(out, item); }
                out.append(']'); return;
            }
            quote(out, String.valueOf(value));
        }

        private static void quote(StringBuilder out, String value) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
                }
            }
            out.append('"');
        }
    }
}

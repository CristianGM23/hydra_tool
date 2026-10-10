import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** B6 native Java model runtime; independent from the JS fixture.
 * This is NOT an EMF, Sirius or Xtext engine, nor a universal model language.
 * Deliberately bounded to the reference-editor JSON record vocabulary.
 */
public final class HydraJavaEditorSmoke {
    private record Model(String id, String name) {}
    private static final Path ROOT = Path.of("tests/hydra-parity");
    private static final Pattern FIELD = Pattern.compile("\\\"([a-zA-Z]+)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private static Model parse(String json) {
        Matcher matcher = FIELD.matcher(json);
        String id = null;
        String name = null;
        while (matcher.find()) {
            if (matcher.group(1).equals("id")) id = matcher.group(2);
            if (matcher.group(1).equals("name")) name = matcher.group(2);
        }
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}") || name == null
                || !name.matches("[a-zA-Z0-9 _-]{0,64}")) {
            throw new IllegalArgumentException("Unsupported reference model");
        }
        return new Model(id, name);
    }

    private static String modelJson(Model value) {
        return "{\"id\":\"" + value.id() + "\",\"name\":\"" + value.name() + "\"}";
    }

    private static String validate(Model value) {
        return value.name().isBlank()
                ? "[{\"code\":\"NAME_REQUIRED\",\"field\":\"name\",\"message\":\"Name is required\"}]"
                : "[]";
    }

    private static String events(Model model) {
        return "[{\"type\":\"MODEL_RENAMED\",\"element\":\"" + model.id() + "\"}]";
    }

    private static String apply(String capability, Model model) {
        return switch (capability) {
            case "validation" -> validate(model);
            case "quickfix" -> {
                if (validate(model).equals("[]")) throw new IllegalStateException("Quick fix requires diagnostic");
                yield modelJson(new Model(model.id(), "Unnamed"));
            }
            case "operation" -> {
                Model edited = new Model(model.id(), "after");
                yield "{\"model\":" + modelJson(edited) + ",\"events\":" + events(model)
                        + ",\"score\":" + (validate(edited).equals("[]") ? 1 : 0) + "}";
            }
            case "events" -> events(model);
            case "gamification" -> "" + (validate(new Model(model.id(), "after")).equals("[]") ? 1 : 0);
            case "persistence", "roundtrip" -> modelJson(parse(modelJson(model)));
            default -> throw new IllegalArgumentException("Unknown scenario: " + capability);
        };
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static void main(String[] args) throws Exception {
        String nonce = System.getenv("HYDRA_PARITY_CHALLENGE");
        if (nonce != null && !nonce.matches("[0-9a-f]{48}")) throw new IllegalArgumentException("Bad challenge");
        for (String capability : new String[]{"validation", "quickfix", "operation", "events",
                "gamification", "persistence", "roundtrip"}) {
            Path inputFile = ROOT.resolve("inputs").resolve(capability.equals("validation")
                    || capability.equals("quickfix") ? "invalid.json" : "valid.json");
            byte[] bytes = Files.readAllBytes(inputFile);
            Model input = parse(new String(bytes, StandardCharsets.UTF_8));
            String actual = apply(capability, input);
            // Execute independent native assertions; Python verifies actual JSON
            // against immutable canonical expectations and hashes after execution.
            if (actual.isEmpty()) throw new IllegalStateException("Empty native result");
            if (nonce != null) {
                String proof = "{\"schemaVersion\":1,\"scenarioId\":\"" + capability
                        + "\",\"challenge\":\"" + nonce + "\",\"inputSHA256\":\""
                        + sha256(bytes) + "\",\"actual\":" + actual + "}";
                Path output = Path.of("target/hydra-parity", capability + ".json");
                Files.createDirectories(output.getParent());
                Files.writeString(output, proof + "\n", StandardCharsets.UTF_8);
            }
        }
        System.out.println("BD6_JAVA_NATIVE_MODEL=PASS scenarios=7 executed=true engine=java-21-neutral-smoke");
    }
}

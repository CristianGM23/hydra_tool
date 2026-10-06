import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Dependency-free launcher used by HYDRA official transformation bridge bundles.
 * This source is executed with the server JDK using Java source-file mode.
 */
public final class HydraOfficialBridge {
    private static final String DESCRIPTION_SCHEMA = "1.1";
    private static final int EXIT_USAGE = 64;
    private static final int EXIT_TOOL_UNAVAILABLE = 69;
    private static final int EXIT_INCOMPATIBLE = 78;

    private HydraOfficialBridge() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            fail(EXIT_USAGE, "HYDRA official bridge expects a profile file and one action/request argument");
            return;
        }
        Path profileFile = Path.of(args[0]).toAbsolutePath().normalize();
        Properties profile = loadProfile(profileFile);
        String action = args[1];
        Availability availability = inspectAvailability(profileFile.getParent(), profile);
        if ("--hydra-describe".equals(action)) {
            System.out.println(description(profile, availability));
            return;
        }
        Path requestFile = Path.of(action).toAbsolutePath().normalize();
        if (!Files.isRegularFile(requestFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(requestFile)) {
            fail(EXIT_USAGE, "HYDRA bridge request is missing or is not a regular file");
            return;
        }
        if (!availability.toolAvailable()) {
            fail(EXIT_TOOL_UNAVAILABLE, firstDiagnostic(availability.diagnostics(),
                    "Required transformation toolchain is not available on this server"));
            return;
        }
        if (!availability.executionAvailable()) {
            fail(EXIT_TOOL_UNAVAILABLE, firstDiagnostic(availability.diagnostics(),
                    "HYDRA official bridge execution backend is not provisioned for this toolchain yet"));
            return;
        }
        fail(EXIT_INCOMPATIBLE, "HYDRA official bridge profile requests an execution backend not supported by this bridge release");
    }

    private static Properties loadProfile(Path profileFile) {
        if (Files.isSymbolicLink(profileFile) || !Files.isRegularFile(profileFile, LinkOption.NOFOLLOW_LINKS)) {
            fail(EXIT_USAGE, "HYDRA official bridge profile is missing or invalid");
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(profileFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            fail(EXIT_USAGE, "HYDRA official bridge profile could not be read");
        }
        require(properties, "language");
        require(properties, "bridge.name");
        require(properties, "bridge.version");
        require(properties, "toolchain.name");
        return properties;
    }

    private static Availability inspectAvailability(Path bundle, Properties profile) {
        List<String> diagnostics = new ArrayList<>();
        boolean executablesAvailable = true;
        for (String group : csv(profile.getProperty("required.executables", ""))) {
            List<String> alternatives = Arrays.stream(group.split("\\|"))
                    .map(String::trim).filter(value -> !value.isEmpty()).toList();
            if (!alternatives.isEmpty() && alternatives.stream().noneMatch(HydraOfficialBridge::executableOnPath)) {
                executablesAvailable = false;
                diagnostics.add("Required executable is not available: " + String.join(" or ", alternatives));
            }
        }
        boolean filesAvailable = true;
        for (String value : csv(profile.getProperty("required.files", ""))) {
            Path required = safeBundleFile(bundle, value);
            if (!Files.isRegularFile(required, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(required)) {
                filesAvailable = false;
                diagnostics.add("Required toolchain component is not installed: " + value.replace('\\', '/'));
            }
        }
        boolean toolAvailable = executablesAvailable && filesAvailable;
        boolean requestedExecution = Boolean.parseBoolean(profile.getProperty("execution.ready", "false"));
        boolean executionAvailable = toolAvailable && requestedExecution;
        String note = profile.getProperty("execution.note", "").trim();
        if (!requestedExecution && !note.isEmpty()) diagnostics.add(note);
        return new Availability(toolAvailable, executionAvailable, List.copyOf(diagnostics));
    }

    private static Path safeBundleFile(Path bundle, String relative) {
        String normalized = relative.trim().replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            fail(EXIT_USAGE, "Official bridge profile contains an invalid required file path");
        }
        Path target = bundle.resolve(normalized).normalize();
        if (!target.startsWith(bundle)) fail(EXIT_USAGE, "Official bridge profile required file escapes its bundle");
        return target;
    }

    private static boolean executableOnPath(String executable) {
        if (executable == null || executable.isBlank()) return false;
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) path = System.getenv("Path");
        if (path == null || path.isBlank()) return false;
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> suffixes = windows ? windowsSuffixes() : List.of("");
        for (String directory : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            if (directory == null || directory.isBlank()) continue;
            for (String suffix : suffixes) {
                Path candidate = Path.of(directory).resolve(executable + suffix);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return true;
            }
        }
        return false;
    }

    private static List<String> windowsSuffixes() {
        String raw = System.getenv("PATHEXT");
        if (raw == null || raw.isBlank()) return List.of("", ".exe", ".cmd", ".bat");
        List<String> suffixes = new ArrayList<>();
        suffixes.add("");
        for (String value : raw.split(";")) {
            if (value != null && !value.isBlank()) suffixes.add(value.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(suffixes);
    }

    private static String description(Properties profile, Availability availability) {
        String language = profile.getProperty("language").trim().toUpperCase(Locale.ROOT);
        String bridgeName = profile.getProperty("bridge.name").trim();
        String bridgeVersion = profile.getProperty("bridge.version").trim();
        String toolchainName = profile.getProperty("toolchain.name").trim();
        String toolchainVersion = profile.getProperty("toolchain.version", "").trim();
        List<String> protocols = csv(profile.getProperty("protocol.versions", ""));
        List<String> kinds = csv(profile.getProperty("supported.kinds", ""));
        List<String> directions = csv(profile.getProperty("supported.directions", ""));
        boolean fullSync = Boolean.parseBoolean(profile.getProperty("full.bidirectional.synchronization", "false"));
        boolean incremental = Boolean.parseBoolean(profile.getProperty("incremental.synchronization", "false"));
        return "{" +
                jsonField("schemaVersion", DESCRIPTION_SCHEMA) + "," +
                jsonField("language", language) + "," +
                jsonField("bridgeName", bridgeName) + "," +
                jsonField("bridgeVersion", bridgeVersion) + "," +
                jsonField("toolchainName", toolchainName) + "," +
                jsonField("toolchainVersion", toolchainVersion) + "," +
                jsonArrayField("protocolVersions", protocols) + "," +
                jsonArrayField("supportedKinds", kinds) + "," +
                jsonArrayField("supportedDirections", directions) + "," +
                "\"fullBidirectionalSynchronization\":" + fullSync + "," +
                "\"incrementalSynchronization\":" + incremental + "," +
                "\"toolAvailable\":" + availability.toolAvailable() + "," +
                "\"executionAvailable\":" + availability.executionAvailable() + "," +
                jsonArrayField("diagnostics", availability.diagnostics()) +
                "}";
    }

    private static String jsonField(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    private static String jsonArrayField(String name, List<String> values) {
        return quote(name) + ":[" + values.stream().map(HydraOfficialBridge::quote).reduce((a, b) -> a + "," + b).orElse("") + "]";
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        String source = value == null ? "" : value;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).distinct().toList();
    }

    private static void require(Properties profile, String key) {
        if (profile.getProperty(key, "").isBlank()) fail(EXIT_USAGE, "HYDRA official bridge profile is missing " + key);
    }

    private static String firstDiagnostic(List<String> diagnostics, String fallback) {
        return diagnostics == null || diagnostics.isEmpty() ? fallback : diagnostics.getFirst();
    }

    private static void fail(int code, String message) {
        System.err.println(message == null || message.isBlank() ? "HYDRA official bridge failed" : message);
        System.exit(code);
    }

    private record Availability(boolean toolAvailable, boolean executionAvailable, List<String> diagnostics) {}
}

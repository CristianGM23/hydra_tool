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
import java.util.concurrent.TimeUnit;

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
        runExecutionBackend(profileFile.getParent(), profile, requestFile);
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
        boolean classpathAvailable = validateDeclaredClasspath(bundle, profile, diagnostics);
        boolean toolAvailable = executablesAvailable && filesAvailable && classpathAvailable;
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

    private static boolean validateDeclaredClasspath(Path bundle, Properties profile, List<String> diagnostics) {
        String manifest = profile.getProperty("execution.classpath.file", "").trim();
        if (manifest.isBlank()) return true;
        Path classpathFile = safeBundleFile(bundle, manifest);
        if (!Files.isRegularFile(classpathFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(classpathFile)) return false;
        try {
            List<String> entries = Files.readAllLines(classpathFile, StandardCharsets.UTF_8).stream()
                    .map(String::trim).filter(value -> !value.isEmpty() && !value.startsWith("#")).toList();
            if (entries.isEmpty()) {
                diagnostics.add("Trusted JVM classpath manifest is empty: " + manifest.replace('\\', '/'));
                return false;
            }
            boolean valid = true;
            for (String entry : entries) {
                Path dependency = safeBundleFile(bundle, entry);
                if (!Files.isRegularFile(dependency, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(dependency)) {
                    valid = false;
                    diagnostics.add("Required JVM classpath component is not installed: " + entry.replace('\\', '/'));
                }
            }
            return valid;
        } catch (IOException exception) {
            diagnostics.add("Trusted JVM classpath manifest could not be read");
            return false;
        }
    }

    private static List<Path> trustedClasspath(Path bundle, Properties profile) {
        String manifest = profile.getProperty("execution.classpath.file", "").trim();
        if (manifest.isBlank()) fail(EXIT_INCOMPATIBLE, "HYDRA official bridge JVM classpath manifest is not configured");
        Path classpathFile = safeBundleFile(bundle, manifest);
        try {
            List<Path> entries = new ArrayList<>();
            for (String raw : Files.readAllLines(classpathFile, StandardCharsets.UTF_8)) {
                String entry = raw.trim();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                Path dependency = safeBundleFile(bundle, entry);
                if (!Files.isRegularFile(dependency, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(dependency))
                    fail(EXIT_TOOL_UNAVAILABLE, "HYDRA official bridge JVM classpath is incomplete");
                entries.add(dependency);
            }
            if (entries.isEmpty()) fail(EXIT_TOOL_UNAVAILABLE, "HYDRA official bridge JVM classpath is empty");
            return entries;
        } catch (IOException exception) {
            fail(EXIT_TOOL_UNAVAILABLE, "HYDRA official bridge JVM classpath manifest could not be read");
            return new ArrayList<>();
        }
    }

    private static boolean executableOnPath(String executable) {
        return findExecutable(executable) != null;
    }

    private static Path findExecutable(String executable) {
        if (executable == null || executable.isBlank()) return null;
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) path = System.getenv("Path");
        if (path == null || path.isBlank()) return null;
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> suffixes = windows ? windowsSuffixes() : List.of("");
        for (String directory : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            if (directory == null || directory.isBlank()) continue;
            for (String suffix : suffixes) {
                Path candidate = Path.of(directory).resolve(executable + suffix).toAbsolutePath().normalize();
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate;
            }
        }
        return null;
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

    private static void runExecutionBackend(Path bundle, Properties profile, Path requestFile) {
        String backend = profile.getProperty("execution.backend", "").trim().toUpperCase(Locale.ROOT);
        String runtimeValue = profile.getProperty("native.runtime.file", "").trim();
        if (backend.isBlank() || runtimeValue.isBlank()) {
            fail(EXIT_INCOMPATIBLE, "HYDRA official bridge execution backend is not configured by this profile");
            return;
        }
        Path runtime = safeBundleFile(bundle, runtimeValue);
        if (!Files.isRegularFile(runtime, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(runtime)) {
            fail(EXIT_TOOL_UNAVAILABLE, "HYDRA official bridge native runtime component is not installed");
            return;
        }
        List<String> command = new ArrayList<>();
        switch (backend) {
            case "JAVA_SOURCE" -> {
                Path java = currentJavaExecutable();
                if (java == null) fail(EXIT_TOOL_UNAVAILABLE, "The Java executable used by HYDRA is not available");
                command.add(java.toString());
                command.add(runtime.toString());
                command.add(requestFile.toString());
            }
            case "PYTHON" -> {
                Path python = firstExecutable(csv(profile.getProperty("required.executables", "")));
                if (python == null) fail(EXIT_TOOL_UNAVAILABLE, "Python is not available on this server");
                command.add(python.toString());
                command.add(runtime.toString());
                command.add(requestFile.toString());
            }
            case "GO_SOURCE" -> {
                Path go = findExecutable("go");
                if (go == null) fail(EXIT_TOOL_UNAVAILABLE, "Go is not available on this server");
                command.add(go.toString());
                command.add("run");
                command.add(runtime.toString());
                command.add(requestFile.toString());
            }
            case "JVM_CLASSPATH" -> {
                Path java = currentJavaExecutable();
                if (java == null) fail(EXIT_TOOL_UNAVAILABLE, "The Java executable used by HYDRA is not available");
                String mainClass = profile.getProperty("native.runtime.mainClass", "").trim();
                if (mainClass.isBlank() || !mainClass.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
                    fail(EXIT_INCOMPATIBLE, "HYDRA official bridge JVM runtime main class is invalid");
                List<Path> classpath = trustedClasspath(bundle, profile);
                if (classpath.stream().noneMatch(runtime::equals)) classpath.add(runtime);
                command.add(java.toString());
                command.add("-cp");
                command.add(classpath.stream().map(Path::toString).reduce((a, b) -> a + System.getProperty("path.separator") + b).orElse(""));
                command.add(mainClass);
                command.add(requestFile.toString());
            }
            default -> {
                fail(EXIT_INCOMPATIBLE, "HYDRA official bridge profile requests an unsupported execution backend");
                return;
            }
        }
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(requestFile.getParent().toFile());
            builder.redirectErrorStream(true);
            process = builder.start();
            boolean completed = process.waitFor(90, TimeUnit.SECONDS);
            if (!completed) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                fail(70, "HYDRA official native backend exceeded its execution timeout");
                return;
            }
            String output = new String(process.getInputStream().readNBytes(8192), StandardCharsets.UTF_8).trim();
            int exit = process.exitValue();
            if (exit != 0) {
                int propagated = exit == EXIT_TOOL_UNAVAILABLE || exit == EXIT_INCOMPATIBLE || exit == EXIT_USAGE ? exit : 70;
                fail(propagated, output.isBlank() ? "HYDRA official native backend failed" : output);
            }
        } catch (IOException exception) {
            fail(EXIT_TOOL_UNAVAILABLE, "HYDRA official bridge could not start its native execution backend");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            fail(70, "HYDRA official native backend execution was interrupted");
        }
    }

    private static Path currentJavaExecutable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path executable = Path.of(System.getProperty("java.home", ""), "bin", windows ? "java.exe" : "java")
                .toAbsolutePath().normalize();
        return Files.isRegularFile(executable) && Files.isExecutable(executable) ? executable : null;
    }

    private static Path firstExecutable(List<String> groups) {
        for (String group : groups) {
            for (String alternative : group.split("\\|")) {
                Path executable = findExecutable(alternative.trim());
                if (executable != null) return executable;
            }
        }
        return null;
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

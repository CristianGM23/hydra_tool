package com.example.editor.transformation.nativeexec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.editor.transformation.export.TransformationExportLanguage;

import tools.jackson.databind.ObjectMapper;

/**
 * Resolves one server-owned transformation runtime command.
 * Explicit configuration wins; otherwise HYDRA discovers an administrator-provisioned bundle.
 */
final class TransformationNativeRuntimeCommandResolver {
    static final String MANIFEST_SCHEMA_VERSION = "1.0";
    static final String BUNDLE_PLACEHOLDER = "${bundle}";
    static final String JAVA_PLACEHOLDER = "${java}";

    private final TransformationNativeRuntimeProperties properties;
    private final ObjectMapper mapper;

    /** Descripción: Inicializa una instancia de TransformationNativeRuntimeCommandResolver con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ TransformationNativeRuntimeCommandResolver(TransformationNativeRuntimeProperties properties, ObjectMapper mapper) {
        if (properties == null) throw new IllegalArgumentException("properties are required");
        if (mapper == null) throw new IllegalArgumentException("mapper is required");
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Resolves explicit configuration first and managed bundle discovery second. */
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de TransformationNativeRuntimeCommandResolver. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationNativeRuntimeCommandResolution resolve(TransformationExportLanguage language) {
        if (language == null) return empty(null, List.of("Transformation runtime language is required"));
        List<String> explicit = properties.commandFor(language);
        if (!explicit.isEmpty()) {
            return new TransformationNativeRuntimeCommandResolution(language,
                    TransformationNativeRuntimeSource.EXPLICIT_COMMAND, explicit,
                    "", "", "", "", List.of());
        }
        if (!properties.isManagedDiscoveryEnabled()) return empty(language, List.of());
        return resolveManaged(language);
    }

    /** Descripción: Obtiene la información correspondiente a «resolveManaged» dentro de TransformationNativeRuntimeCommandResolver. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeCommandResolution resolveManaged(TransformationExportLanguage language) {
        Path root = properties.getManagedToolchainRoot().toAbsolutePath().normalize();
        Path bundle = root.resolve(language.name().toLowerCase(Locale.ROOT)).normalize();
        Path manifest = bundle.resolve(properties.getManagedManifestName()).normalize();
        if (!bundle.startsWith(root) || !manifest.startsWith(bundle))
            return invalid(language, "Managed toolchain location escapes the configured root");
        if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) return empty(language, List.of());
        try {
            rejectSymbolicPath(root);
            rejectSymbolicPath(bundle);
            if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
                return invalid(language, "Managed toolchain manifest is not a regular file");
            long size = Files.size(manifest);
            if (size <= 0L || size > properties.getMaximumManagedManifestBytes())
                return invalid(language, "Managed toolchain manifest exceeds the configured size limit");
            TransformationManagedToolchainManifest value = mapper.readValue(manifest.toFile(), TransformationManagedToolchainManifest.class);
            List<String> errors = validateManifest(language, value);
            if (!errors.isEmpty()) return new TransformationNativeRuntimeCommandResolution(language,
                    TransformationNativeRuntimeSource.MANAGED_BUNDLE, List.of(),
                    value == null ? "" : value.bridgeName(), value == null ? "" : value.bridgeVersion(),
                    value == null ? "" : value.toolchainName(), value == null ? "" : value.toolchainVersion(), errors);
            List<String> command = expandManagedCommand(bundle, value.command());
            return new TransformationNativeRuntimeCommandResolution(language,
                    TransformationNativeRuntimeSource.MANAGED_BUNDLE, command,
                    value.bridgeName(), value.bridgeVersion(), value.toolchainName(), value.toolchainVersion(), List.of());
        } catch (IOException | RuntimeException exception) {
            return invalid(language, "Managed toolchain manifest could not be read safely");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateManifest» y rechaza entradas no válidas. Entradas: language: dato de entrada necesario para la operación; manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<String> validateManifest(TransformationExportLanguage language, TransformationManagedToolchainManifest manifest) {
        List<String> diagnostics = new ArrayList<>();
        if (manifest == null) {
            diagnostics.add("Managed toolchain manifest is empty");
            return diagnostics;
        }
        if (!MANIFEST_SCHEMA_VERSION.equals(manifest.schemaVersion()))
            diagnostics.add("Managed toolchain manifest schema is not supported");
        if (manifest.language() != language)
            diagnostics.add("Managed toolchain manifest language does not match its installation slot");
        if (manifest.command().isEmpty())
            diagnostics.add("Managed toolchain manifest does not define a launch command");
        if (manifest.command().size() > 128)
            diagnostics.add("Managed toolchain manifest contains too many command arguments");
        return List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «expandManagedCommand» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolver. Entradas: bundle: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<String> expandManagedCommand(Path bundle, List<String> command) {
        List<String> resolved = new ArrayList<>();
        for (String argument : command) {
            if (JAVA_PLACEHOLDER.equals(argument)) {
                resolved.add(javaExecutable().toString());
                continue;
            }
            if (argument.contains(JAVA_PLACEHOLDER))
                throw new IllegalArgumentException("Managed Java placeholder must be the complete command argument");
            if (!argument.contains(BUNDLE_PLACEHOLDER)) {
                resolved.add(argument);
                continue;
            }
            if (!argument.startsWith(BUNDLE_PLACEHOLDER) || argument.indexOf(BUNDLE_PLACEHOLDER, BUNDLE_PLACEHOLDER.length()) >= 0)
                throw new IllegalArgumentException("Managed bundle placeholder must appear once at the start of an argument");
            String suffix = argument.substring(BUNDLE_PLACEHOLDER.length());
            while (suffix.startsWith("/") || suffix.startsWith("\\")) suffix = suffix.substring(1);
            Path target = suffix.isBlank() ? bundle : bundle.resolve(suffix).normalize();
            if (!target.startsWith(bundle)) throw new IllegalArgumentException("Managed bundle argument escapes its installation directory");
            rejectSymbolicPathIfPresent(target);
            resolved.add(target.toString());
        }
        return List.copyOf(resolved);
    }


    /** Descripción: Resuelve el ejecutable Java exacto de la JVM que está ejecutando HYDRA, sin depender del PATH del servidor. Entradas: no requiere parámetros. Salida: ruta absoluta validada al lanzador Java o error si no está disponible. */
    /** Descripción: Ejecuta la operación «javaExecutable» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolver. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        Path target = Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
        if (!Files.isRegularFile(target) || !Files.isExecutable(target))
            throw new IllegalArgumentException("HYDRA Java launcher is not available under java.home");
        return target;
    }

    /** Descripción: Ejecuta la operación «rejectSymbolicPath» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolver. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectSymbolicPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current == null ? segment : current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                throw new IllegalArgumentException("Managed toolchain paths may not contain symbolic links");
        }
    }

    /** Descripción: Ejecuta la operación «rejectSymbolicPathIfPresent» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolver. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectSymbolicPathIfPresent(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current == null ? segment : current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Managed toolchain command may not traverse symbolic links");
        }
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolver. Entradas: language: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeCommandResolution invalid(TransformationExportLanguage language, String message) {
        return new TransformationNativeRuntimeCommandResolution(language,
                TransformationNativeRuntimeSource.MANAGED_BUNDLE, List.of(), "", "", "", "", List.of(message));
    }

    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de TransformationNativeRuntimeCommandResolver. Entradas: language: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeCommandResolution empty(TransformationExportLanguage language, List<String> diagnostics) {
        return new TransformationNativeRuntimeCommandResolution(language,
                TransformationNativeRuntimeSource.NONE, List.of(), "", "", "", "", diagnostics);
    }
}

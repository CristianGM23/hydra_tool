package com.example.editor.transformation.nativeexec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.editor.io.AtomicFiles;
import com.example.editor.transformation.export.TransformationExportLanguage;

import tools.jackson.databind.ObjectMapper;

/**
 * Installs and removes transformation bridge bundles only from a trusted server-owned distribution catalog.
 * Browser requests select package identifiers; they never provide filesystem paths, archives, commands or executables.
 */
@Service
public class TransformationToolchainProvisioningService {
    static final String PACKAGE_SCHEMA_VERSION = "1.0";
    static final String INSTALLATION_SCHEMA_VERSION = "1.0";
    static final String PACKAGE_MANIFEST = "package.json";
    static final String PAYLOAD_DIRECTORY = "payload";
    static final String INSTALLATION_METADATA = ".hydra-package.json";
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MAX_CATALOG_PACKAGES = 512;

    private final TransformationNativeRuntimeProperties properties;
    private final ObjectMapper mapper;
    private final TransformationNativeRuntimeExecutor executor;
    private final TransformationNativeRuntimeCommandResolver commandResolver;

    /** Initializes the trusted provisioning service. */
    /** Descripción: Inicializa una instancia de TransformationToolchainProvisioningService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; executor: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationToolchainProvisioningService(TransformationNativeRuntimeProperties properties,
            ObjectMapper mapper, TransformationNativeRuntimeExecutor executor) {
        if (properties == null || mapper == null || executor == null)
            throw new IllegalArgumentException("Toolchain provisioning dependencies are required");
        this.properties = properties;
        this.mapper = mapper;
        this.executor = executor;
        this.commandResolver = new TransformationNativeRuntimeCommandResolver(properties, mapper);
    }

    /** Returns the trusted server-side package catalog and current managed installation state. */
    /** Descripción: Ejecuta la operación «catalog» correspondiente a la responsabilidad de VerifiedPackage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationToolchainCatalog catalog() {
        List<String> diagnostics = new ArrayList<>();
        Map<TransformationExportLanguage, TransformationToolchainInstallationDescriptor> installations = installationDescriptors();
        List<TransformationToolchainPackageDescriptor> packages = new ArrayList<>();
        Path root = distributionRoot();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            try {
                rejectSymbolicPath(root);
                int[] count = {0};
                for (TransformationExportLanguage language : TransformationNativeRuntimeSupport.languages()) {
                    Path languageRoot = root.resolve(slot(language));
                    if (!Files.exists(languageRoot, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (Files.isSymbolicLink(languageRoot) || !Files.isDirectory(languageRoot, LinkOption.NOFOLLOW_LINKS)) {
                        diagnostics.add("Trusted package catalog contains an invalid " + language + " slot");
                        continue;
                    }
                    try (var packageIds = Files.list(languageRoot)) {
                        for (Path packageRoot : packageIds.sorted().toList()) {
                            if (count[0] >= MAX_CATALOG_PACKAGES) break;
                            if (Files.isSymbolicLink(packageRoot) || !Files.isDirectory(packageRoot, LinkOption.NOFOLLOW_LINKS)) continue;
                            String packageId = packageRoot.getFileName().toString();
                            if (!safeSegment(packageId)) continue;
                            try (var versions = Files.list(packageRoot)) {
                                for (Path versionRoot : versions.sorted().toList()) {
                                    if (count[0]++ >= MAX_CATALOG_PACKAGES) break;
                                    if (Files.isSymbolicLink(versionRoot) || !Files.isDirectory(versionRoot, LinkOption.NOFOLLOW_LINKS)) continue;
                                    packages.add(describePackage(language, packageId, versionRoot.getFileName().toString(), versionRoot,
                                            installations.get(language)));
                                }
                            }
                        }
                    }
                }
                if (count[0] >= MAX_CATALOG_PACKAGES)
                    diagnostics.add("Trusted package catalog was truncated at the configured safety limit");
            } catch (IOException | RuntimeException exception) {
                diagnostics.add("Trusted toolchain package catalog could not be inspected safely");
            }
        }
        packages.sort(Comparator.comparing((TransformationToolchainPackageDescriptor item) -> item.language().name())
                .thenComparing(TransformationToolchainPackageDescriptor::packageId)
                .thenComparing(TransformationToolchainPackageDescriptor::packageVersion));
        return new TransformationToolchainCatalog(properties.isProvisioningEnabled(), packages,
                TransformationNativeRuntimeSupport.languages().stream().map(installations::get).toList(), diagnostics);
    }

    /** Installs or updates one exact package from the trusted server-side catalog. */
    public synchronized TransformationToolchainProvisioningReport install(TransformationExportLanguage language,
            TransformationToolchainProvisioningRequest request) {
        requireProvisioning();
        if (language == null || request == null) throw new IllegalArgumentException("Language and package selection are required");
        requireSafeSegment("packageId", request.packageId());
        requireSafeSegment("packageVersion", request.packageVersion());
        VerifiedPackage selected = verifyPackage(language, request.packageId(), request.packageVersion());
        Path root = managedRoot();
        Path target = root.resolve(slot(language)).normalize();
        ensureInside(root, target);
        TransformationToolchainInstallation current = readInstallation(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && current == null)
            return blocked(language, request.packageId(), request.packageVersion(),
                    "Existing administrator-managed bundle is not owned by HYDRA provisioning and will not be replaced");
        if (current != null && current.language() != language)
            return blocked(language, request.packageId(), request.packageVersion(),
                    "Existing HYDRA package ownership metadata does not match this language slot");
        verifyExpected(current, request.expectedCurrentPackageId(), request.expectedCurrentPackageVersion());
        if (current != null && current.packageId().equals(selected.manifest().packageId())
                && current.packageVersion().equals(selected.manifest().packageVersion())
                && current.packageFingerprint().equals(selected.fingerprint())) {
            executor.invalidateDiagnostic(language);
            return new TransformationToolchainProvisioningReport(language, TransformationToolchainProvisioningStatus.NO_CHANGE,
                    current.packageId(), current.packageVersion(), diagnoseIfActive(language), List.of("Requested toolchain package is already installed"));
        }

        Path staging = null;
        Path backup = null;
        boolean movedPrevious = false;
        try {
            Files.createDirectories(root);
            rejectSymbolicPath(root);
            staging = root.resolve(".staging-" + slot(language) + "-" + UUID.randomUUID()).normalize();
            backup = root.resolve(".backup-" + slot(language) + "-" + UUID.randomUUID()).normalize();
            ensureInside(root, staging);
            ensureInside(root, backup);
            copyVerifiedPayload(selected, staging);
            writeInstallation(staging, selected);
            validateInstalledBundle(language, selected.manifest(), staging);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                AtomicFiles.moveNew(target, backup);
                movedPrevious = true;
            }
            try {
                AtomicFiles.moveNew(staging, target);
                staging = null;
            } catch (IOException publicationFailure) {
                if (movedPrevious && !Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    try { AtomicFiles.moveNew(backup, target); backup = null; }
                    catch (IOException restoreFailure) { publicationFailure.addSuppressed(restoreFailure); }
                }
                throw publicationFailure;
            }

            executor.invalidateDiagnostic(language);
            TransformationNativeRuntimeDiagnostic diagnostic = diagnoseIfActive(language);
            if (diagnostic != null && diagnostic.health() == TransformationNativeRuntimeHealth.INCOMPATIBLE) {
                deleteTree(target);
                if (movedPrevious && backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    AtomicFiles.moveNew(backup, target);
                    backup = null;
                }
                executor.invalidateDiagnostic(language);
                return new TransformationToolchainProvisioningReport(language, TransformationToolchainProvisioningStatus.ROLLED_BACK,
                        selected.manifest().packageId(), selected.manifest().packageVersion(), diagnostic,
                        List.of("Installed bridge declared an incompatible HYDRA protocol and the previous managed state was restored"));
            }
            deleteTree(backup);
            backup = null;
            TransformationToolchainProvisioningStatus status = current == null
                    ? TransformationToolchainProvisioningStatus.INSTALLED : TransformationToolchainProvisioningStatus.UPDATED;
            List<String> notes = properties.commandFor(language).isEmpty() ? List.of()
                    : List.of("Explicit server command has priority; the managed package is installed but not active");
            return new TransformationToolchainProvisioningReport(language, status,
                    selected.manifest().packageId(), selected.manifest().packageVersion(), diagnostic, notes);
        } catch (IOException exception) {
            if (movedPrevious && backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) deleteTree(target);
                    AtomicFiles.moveNew(backup, target);
                    backup = null;
                    executor.invalidateDiagnostic(language);
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw new IllegalStateException("Could not publish trusted transformation toolchain package", exception);
        } finally {
            try {
                deleteTree(staging);
            } catch (IOException ignored) {
                com.example.editor.observability.HydraBestEffortDiagnostics.record(
                        "TransformationToolchainProvisioningService.cleanupStaging", ignored);
            }
        }
    }

    /** Removes one HYDRA-provisioned bundle while refusing to delete administrator-managed directories. */
    public synchronized TransformationToolchainProvisioningReport uninstall(TransformationExportLanguage language,
            TransformationToolchainRemovalRequest request) {
        requireProvisioning();
        if (language == null) throw new IllegalArgumentException("Language is required");
        Path root = managedRoot();
        Path target = root.resolve(slot(language)).normalize();
        ensureInside(root, target);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            executor.invalidateDiagnostic(language);
            return new TransformationToolchainProvisioningReport(language, TransformationToolchainProvisioningStatus.NO_CHANGE,
                    "", "", diagnoseIfActive(language), List.of("No HYDRA-provisioned bundle is installed"));
        }
        TransformationToolchainInstallation current = readInstallation(target);
        if (current == null)
            return blocked(language, "", "", "Administrator-managed bundle is not removable through HYDRA provisioning");
        if (current.language() != language)
            return blocked(language, current.packageId(), current.packageVersion(),
                    "HYDRA package ownership metadata does not match this language slot");
        String expectedId = request == null ? "" : request.expectedPackageId();
        String expectedVersion = request == null ? "" : request.expectedPackageVersion();
        verifyExpected(current, expectedId, expectedVersion);
        try {
            Files.createDirectories(root);
            Path trash = root.resolve(".removed-" + slot(language) + "-" + UUID.randomUUID()).normalize();
            ensureInside(root, trash);
            AtomicFiles.moveNew(target, trash);
            deleteTree(trash);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not remove HYDRA-provisioned transformation toolchain", exception);
        }
        executor.invalidateDiagnostic(language);
        return new TransformationToolchainProvisioningReport(language, TransformationToolchainProvisioningStatus.REMOVED,
                current.packageId(), current.packageVersion(), diagnoseIfActive(language), List.of());
    }

    /** Returns one package descriptor, including validation diagnostics without leaking its server path. */
    /** Descripción: Ejecuta la operación «describePackage» correspondiente a la responsabilidad de VerifiedPackage. Entradas: language: dato de entrada necesario para la operación; packageId: identificador que delimita el recurso objetivo; version: dato de entrada necesario para la operación; versionRoot: dato de entrada necesario para la operación; installed: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationToolchainPackageDescriptor describePackage(TransformationExportLanguage language, String packageId,
            String version, Path versionRoot, TransformationToolchainInstallationDescriptor installed) {
        List<String> diagnostics = new ArrayList<>();
        TransformationToolchainPackageManifest manifest = null;
        try {
            if (!safeSegment(version)) throw new IllegalArgumentException("Package version is not a safe identifier");
            VerifiedPackage verified = verifyPackageAt(language, packageId, version, versionRoot);
            manifest = verified.manifest();
        } catch (RuntimeException exception) {
            diagnostics.add(sanitize(exception.getMessage(), "Package failed trusted validation"));
        }
        boolean installable = diagnostics.isEmpty();
        boolean exactInstalled = manifest != null && installed != null && installed.hydraManaged()
                && manifest.packageId().equals(installed.packageId()) && manifest.packageVersion().equals(installed.packageVersion());
        return new TransformationToolchainPackageDescriptor(language, packageId, version,
                manifest == null ? "" : manifest.bridgeName(), manifest == null ? "" : manifest.bridgeVersion(),
                manifest == null ? "" : manifest.toolchainName(), manifest == null ? "" : manifest.toolchainVersion(),
                installable, exactInstalled, diagnostics);
    }

    /** Loads and validates one exact trusted package. */
    /** Descripción: Valida las condiciones necesarias para «verifyPackage» y rechaza entradas no válidas. Entradas: language: dato de entrada necesario para la operación; packageId: identificador que delimita el recurso objetivo; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private VerifiedPackage verifyPackage(TransformationExportLanguage language, String packageId, String version) {
        Path root = distributionRoot();
        Path versionRoot = root.resolve(slot(language)).resolve(packageId).resolve(version).normalize();
        ensureInside(root, versionRoot);
        return verifyPackageAt(language, packageId, version, versionRoot);
    }

    /** Verifies package metadata, complete file coverage, hashes, quotas and embedded runtime manifest. */
    /** Descripción: Valida las condiciones necesarias para «verifyPackageAt» y rechaza entradas no válidas. Entradas: language: dato de entrada necesario para la operación; packageId: identificador que delimita el recurso objetivo; version: dato de entrada necesario para la operación; versionRoot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private VerifiedPackage verifyPackageAt(TransformationExportLanguage language, String packageId, String version, Path versionRoot) {
        requireSafeSegment("packageId", packageId);
        requireSafeSegment("packageVersion", version);
        Path root = distributionRoot();
        Path normalized = versionRoot.toAbsolutePath().normalize();
        ensureInside(root, normalized);
        Path manifestFile = normalized.resolve(PACKAGE_MANIFEST).normalize();
        Path payload = normalized.resolve(PAYLOAD_DIRECTORY).normalize();
        ensureInside(normalized, manifestFile);
        ensureInside(normalized, payload);
        try {
            rejectSymbolicPath(root);
            rejectSymbolicPath(normalized);
            if (Files.isSymbolicLink(manifestFile) || !Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Trusted package manifest is missing");
            if (Files.size(manifestFile) <= 0 || Files.size(manifestFile) > properties.getMaximumProvisioningManifestBytes())
                throw new IllegalArgumentException("Trusted package manifest exceeds the configured size limit");
            if (Files.isSymbolicLink(payload) || !Files.isDirectory(payload, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalArgumentException("Trusted package payload directory is missing");
            TransformationToolchainPackageManifest manifest = mapper.readValue(manifestFile.toFile(), TransformationToolchainPackageManifest.class);
            validatePackageManifest(language, packageId, version, manifest);
            PayloadEvidence evidence = inspectPayload(payload, manifest.sha256());
            String fingerprint = sha256(manifestFile);
            Path runtimeManifestFile = payload.resolve(properties.getManagedManifestName()).normalize();
            ensureInside(payload, runtimeManifestFile);
            if (!Files.isRegularFile(runtimeManifestFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(runtimeManifestFile))
                throw new IllegalArgumentException("Provisioned payload does not contain the managed runtime manifest");
            TransformationManagedToolchainManifest runtimeManifest = mapper.readValue(runtimeManifestFile.toFile(), TransformationManagedToolchainManifest.class);
            List<String> runtimeErrors = commandResolver.validateManifest(language, runtimeManifest);
            if (!runtimeErrors.isEmpty()) throw new IllegalArgumentException(runtimeErrors.get(0));
            commandResolver.expandManagedCommand(payload, runtimeManifest.command());
            compareDeclared("bridge name", manifest.bridgeName(), runtimeManifest.bridgeName());
            compareDeclared("bridge version", manifest.bridgeVersion(), runtimeManifest.bridgeVersion());
            compareDeclared("toolchain name", manifest.toolchainName(), runtimeManifest.toolchainName());
            compareDeclared("toolchain version", manifest.toolchainVersion(), runtimeManifest.toolchainVersion());
            return new VerifiedPackage(manifest, normalized, payload, fingerprint, evidence.totalBytes(), evidence.fileCount());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Trusted package could not be verified safely", exception);
        }
    }

    /** Validates package-level identity and complete checksum metadata. */
    /** Descripción: Valida las condiciones necesarias para «validatePackageManifest» y rechaza entradas no válidas. Entradas: language: dato de entrada necesario para la operación; packageId: identificador que delimita el recurso objetivo; version: dato de entrada necesario para la operación; manifest: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validatePackageManifest(TransformationExportLanguage language, String packageId, String version,
            TransformationToolchainPackageManifest manifest) {
        if (manifest == null) throw new IllegalArgumentException("Trusted package manifest is empty");
        if (!PACKAGE_SCHEMA_VERSION.equals(manifest.schemaVersion())) throw new IllegalArgumentException("Trusted package schema is not supported");
        if (manifest.language() != language) throw new IllegalArgumentException("Trusted package language does not match its catalog slot");
        if (!packageId.equals(manifest.packageId())) throw new IllegalArgumentException("Trusted package id does not match its catalog slot");
        if (!version.equals(manifest.packageVersion())) throw new IllegalArgumentException("Trusted package version does not match its catalog slot");
        if (manifest.sha256().isEmpty()) throw new IllegalArgumentException("Trusted package does not declare payload checksums");
        if (!manifest.sha256().containsKey(properties.getManagedManifestName()))
            throw new IllegalArgumentException("Trusted package does not cover its runtime manifest checksum");
        for (Map.Entry<String, String> entry : manifest.sha256().entrySet()) {
            safeRelativePath(entry.getKey());
            if (!SHA256.matcher(entry.getValue() == null ? "" : entry.getValue().trim()).matches())
                throw new IllegalArgumentException("Trusted package contains an invalid SHA-256 digest");
        }
    }

    /** Traverses the payload once, rejecting links and requiring one checksum entry per regular file. */
    /** Descripción: Ejecuta la operación «inspectPayload» correspondiente a la responsabilidad de VerifiedPackage. Entradas: payload: valor de entrada que debe procesarse; expected: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PayloadEvidence inspectPayload(Path payload, Map<String, String> expected) throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        long[] bytes = {0L};
        int[] files = {0};
        Files.walkFileTree(payload, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            /** Descripción: Ejecuta la operación «preVisitDirectory» correspondiente a la responsabilidad de VerifiedPackage. Entradas: dir: dato de entrada necesario para la operación; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) throw new IOException("Trusted package payload may not contain symbolic links");
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de VerifiedPackage. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile() || Files.isSymbolicLink(file))
                    throw new IOException("Trusted package payload must contain only regular files");
                files[0]++;
                if (files[0] > properties.getMaximumProvisioningFiles()) throw new IOException("Trusted package exceeds the configured file-count limit");
                try { bytes[0] = Math.addExact(bytes[0], attrs.size()); }
                catch (ArithmeticException exception) { throw new IOException("Trusted package size overflow", exception); }
                if (bytes[0] > properties.getMaximumProvisioningPackageBytes()) throw new IOException("Trusted package exceeds the configured byte limit");
                String relative = portable(payload.relativize(file));
                safeRelativePath(relative);
                actual.put(relative, sha256(file));
                return FileVisitResult.CONTINUE;
            }
        });
        if (!actual.keySet().equals(expected.keySet()))
            throw new IOException("Trusted package checksum manifest does not cover exactly the payload files");
        for (Map.Entry<String, String> entry : actual.entrySet()) {
            if (!entry.getValue().equalsIgnoreCase(expected.get(entry.getKey())))
                throw new IOException("Trusted package checksum mismatch for " + entry.getKey());
        }
        return new PayloadEvidence(bytes[0], files[0]);
    }

    /** Copies a previously verified payload without following links and rechecks source size quotas while streaming. */
    /** Descripción: Ejecuta la operación «copyVerifiedPayload» correspondiente a la responsabilidad de VerifiedPackage. Entradas: selected: dato de entrada necesario para la operación; staging: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void copyVerifiedPayload(VerifiedPackage selected, Path staging) throws IOException {
        Files.createDirectory(staging);
        Path source = selected.payload();
        long[] bytes = {0L};
        int[] files = {0};
        Files.walkFileTree(source, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            /** Descripción: Ejecuta la operación «preVisitDirectory» correspondiente a la responsabilidad de VerifiedPackage. Entradas: dir: dato de entrada necesario para la operación; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) throw new IOException("Trusted package changed to a symbolic directory during provisioning");
                Path target = staging.resolve(source.relativize(dir).toString()).normalize();
                ensureInside(staging, target);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de VerifiedPackage. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile() || Files.isSymbolicLink(file))
                    throw new IOException("Trusted package changed to a non-regular file during provisioning");
                files[0]++;
                bytes[0] = Math.addExact(bytes[0], attrs.size());
                if (files[0] > properties.getMaximumProvisioningFiles() || bytes[0] > properties.getMaximumProvisioningPackageBytes())
                    throw new IOException("Trusted package changed beyond provisioning quotas");
                Path target = staging.resolve(source.relativize(file).toString()).normalize();
                ensureInside(staging, target);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
        if (bytes[0] != selected.totalBytes() || files[0] != selected.fileCount())
            throw new IOException("Trusted package changed during provisioning");
        inspectPayload(staging, selected.manifest().sha256());
    }

    /** Writes HYDRA ownership metadata inside a staged bundle before publication. */
    /** Descripción: Actualiza el estado gestionado por VerifiedPackage mediante la operación «writeInstallation». Entradas: staging: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeInstallation(Path staging, VerifiedPackage selected) throws IOException {
        TransformationToolchainInstallation installation = new TransformationToolchainInstallation(INSTALLATION_SCHEMA_VERSION,
                selected.manifest().language(), selected.manifest().packageId(), selected.manifest().packageVersion(), selected.fingerprint());
        AtomicFiles.writeReplacing(staging.resolve(INSTALLATION_METADATA), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(installation));
    }

    /** Revalidates the installed runtime manifest against the selected package metadata. */
    /** Descripción: Valida las condiciones necesarias para «validateInstalledBundle» y rechaza entradas no válidas. Entradas: language: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación; staging: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateInstalledBundle(TransformationExportLanguage language, TransformationToolchainPackageManifest selected, Path staging) throws IOException {
        Path runtimeManifestFile = staging.resolve(properties.getManagedManifestName()).normalize();
        TransformationManagedToolchainManifest runtimeManifest = mapper.readValue(runtimeManifestFile.toFile(), TransformationManagedToolchainManifest.class);
        List<String> errors = commandResolver.validateManifest(language, runtimeManifest);
        if (!errors.isEmpty()) throw new IOException(errors.get(0));
        try { commandResolver.expandManagedCommand(staging, runtimeManifest.command()); }
        catch (RuntimeException exception) { throw new IOException("Provisioned runtime command is unsafe", exception); }
        compareDeclared("bridge name", selected.bridgeName(), runtimeManifest.bridgeName());
        compareDeclared("bridge version", selected.bridgeVersion(), runtimeManifest.bridgeVersion());
        compareDeclared("toolchain name", selected.toolchainName(), runtimeManifest.toolchainName());
        compareDeclared("toolchain version", selected.toolchainVersion(), runtimeManifest.toolchainVersion());
    }

    /** Reads HYDRA installation ownership metadata, returning null for manual/legacy bundles. */
    /** Descripción: Obtiene la información correspondiente a «readInstallation» dentro de VerifiedPackage. Entradas: target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationToolchainInstallation readInstallation(Path target) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return null;
        try {
            if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) return null;
            Path metadata = target.resolve(INSTALLATION_METADATA).normalize();
            ensureInside(target, metadata);
            if (Files.isSymbolicLink(metadata) || !Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) return null;
            if (Files.size(metadata) <= 0 || Files.size(metadata) > 65_536L) return null;
            TransformationToolchainInstallation value = mapper.readValue(metadata.toFile(), TransformationToolchainInstallation.class);
            if (value == null || !INSTALLATION_SCHEMA_VERSION.equals(value.schemaVersion()) || value.language() == null
                    || !safeSegment(value.packageId()) || !safeSegment(value.packageVersion()) || !SHA256.matcher(value.packageFingerprint()).matches()) return null;
            return value;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    /** Builds public installation state for every supported runtime family. */
    /** Descripción: Ejecuta la operación «installationDescriptors» correspondiente a la responsabilidad de VerifiedPackage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Map<TransformationExportLanguage, TransformationToolchainInstallationDescriptor> installationDescriptors() {
        Map<TransformationExportLanguage, TransformationToolchainInstallationDescriptor> result = new HashMap<>();
        Path root = managedRoot();
        for (TransformationExportLanguage language : TransformationNativeRuntimeSupport.languages()) {
            Path target = root.resolve(slot(language)).normalize();
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                result.put(language, new TransformationToolchainInstallationDescriptor(language, false, false, "", "", List.of()));
                continue;
            }
            TransformationToolchainInstallation installation = readInstallation(target);
            if (installation == null) {
                result.put(language, new TransformationToolchainInstallationDescriptor(language, true, false, "", "",
                        List.of("Bundle is administrator-managed and cannot be changed through provisioning")));
            } else if (installation.language() != language) {
                result.put(language, new TransformationToolchainInstallationDescriptor(language, true, true,
                        installation.packageId(), installation.packageVersion(), List.of("Installed package ownership metadata has the wrong language")));
            } else {
                result.put(language, new TransformationToolchainInstallationDescriptor(language, true, true,
                        installation.packageId(), installation.packageVersion(), List.of()));
            }
        }
        return result;
    }

    /** Verifies optimistic package ownership expectations before replacement or removal. */
    /** Descripción: Valida las condiciones necesarias para «verifyExpected» y rechaza entradas no válidas. Entradas: current: dato de entrada necesario para la operación; expectedId: identificador que delimita el recurso objetivo; expectedVersion: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyExpected(TransformationToolchainInstallation current, String expectedId, String expectedVersion) {
        String id = expectedId == null ? "" : expectedId.trim();
        String version = expectedVersion == null ? "" : expectedVersion.trim();
        if (id.isEmpty() && version.isEmpty()) return;
        if (current == null || (!id.isEmpty() && !id.equals(current.packageId())) || (!version.isEmpty() && !version.equals(current.packageVersion())))
            throw new IllegalStateException("Managed toolchain changed since it was inspected; refresh the package catalog before retrying");
    }

    /** Diagnoses the installed managed bundle only when no explicit command overrides it. */
    /** Descripción: Ejecuta la operación «diagnoseIfActive» correspondiente a la responsabilidad de VerifiedPackage. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeDiagnostic diagnoseIfActive(TransformationExportLanguage language) {
        if (!properties.commandFor(language).isEmpty()) return null;
        return executor.diagnose(language);
    }

    /** Returns a bounded blocked result instead of mutating an unowned installation. */
    /** Descripción: Ejecuta la operación «blocked» correspondiente a la responsabilidad de VerifiedPackage. Entradas: language: dato de entrada necesario para la operación; packageId: identificador que delimita el recurso objetivo; version: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationToolchainProvisioningReport blocked(TransformationExportLanguage language, String packageId, String version, String message) {
        return new TransformationToolchainProvisioningReport(language, TransformationToolchainProvisioningStatus.BLOCKED,
                packageId, version, null, List.of(message));
    }

    /** Ensures the administrative provisioning feature is explicitly enabled. */
    /** Descripción: Valida las condiciones necesarias para «requireProvisioning» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireProvisioning() {
        if (!properties.isProvisioningEnabled()) throw new IllegalStateException("Managed transformation toolchain provisioning is disabled by server configuration");
    }

    /** Requires a path-safe catalog identifier. */
    /** Descripción: Valida las condiciones necesarias para «requireSafeSegment» y rechaza entradas no válidas. Entradas: label: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSafeSegment(String label, String value) {
        if (!safeSegment(value)) throw new IllegalArgumentException(label + " must contain only letters, numbers, dot, underscore or hyphen");
    }

    /** Checks one catalog path segment without accepting dot traversal aliases. */
    /** Descripción: Ejecuta la operación «safeSegment» correspondiente a la responsabilidad de VerifiedPackage. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean safeSegment(String value) {
        return value != null && !value.equals(".") && !value.equals("..") && SAFE_SEGMENT.matcher(value).matches();
    }

    /** Requires a portable relative payload path without traversal or absolute roots. */
    /** Descripción: Ejecuta la operación «safeRelativePath» correspondiente a la responsabilidad de VerifiedPackage. Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void safeRelativePath(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Trusted package contains an invalid payload path");
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) throw new IllegalArgumentException("Trusted package payload path must be relative");
        for (String segment : normalized.split("/")) if (segment.isBlank() || segment.equals(".") || segment.equals(".."))
            throw new IllegalArgumentException("Trusted package payload path contains traversal");
        if (INSTALLATION_METADATA.equals(normalized)) throw new IllegalArgumentException("Trusted package may not provide HYDRA installation metadata");
    }

    /** Ensures a normalized path remains below a trusted root. */
    /** Descripción: Valida las condiciones necesarias para «ensureInside» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; candidate: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureInside(Path root, Path candidate) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) throw new IllegalArgumentException("Toolchain path escapes its trusted root");
    }

    /** Rejects existing symbolic path components to prevent catalog/root substitution. */
    /** Descripción: Ejecuta la operación «rejectSymbolicPath» correspondiente a la responsabilidad de VerifiedPackage. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectSymbolicPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current == null ? segment : current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                throw new IllegalArgumentException("Trusted toolchain paths may not contain symbolic links");
        }
    }

    /** Removes one private staging/backup tree without following symbolic links. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteTree» de forma controlada. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            /** Descripción: Ejecuta la operación «visitFile» correspondiente a la responsabilidad de VerifiedPackage. Entradas: file: archivo o contenido que debe procesarse; attrs: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            /** Descripción: Ejecuta la operación «postVisitDirectory» correspondiente a la responsabilidad de VerifiedPackage. Entradas: dir: dato de entrada necesario para la operación; exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Computes the lowercase SHA-256 digest of one regular file. */
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de VerifiedPackage. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Requires package metadata and the runtime manifest to agree when a value is declared. */
    /** Descripción: Ejecuta la operación «compareDeclared» correspondiente a la responsabilidad de VerifiedPackage. Entradas: label: dato de entrada necesario para la operación; declared: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void compareDeclared(String label, String declared, String actual) {
        if (declared == null || declared.isBlank()) return;
        if (actual == null || !declared.equals(actual.trim())) throw new IllegalArgumentException("Trusted package " + label + " does not match its runtime manifest");
    }

    /** Returns the canonical managed installation root. */
    /** Descripción: Ejecuta la operación «managedRoot» correspondiente a la responsabilidad de VerifiedPackage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path managedRoot() {
        return properties.getManagedToolchainRoot().toAbsolutePath().normalize();
    }

    /** Returns the canonical trusted package-distribution root. */
    /** Descripción: Ejecuta la operación «distributionRoot» correspondiente a la responsabilidad de VerifiedPackage. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path distributionRoot() {
        return properties.getManagedDistributionRoot().toAbsolutePath().normalize();
    }

    /** Converts one runtime language to its fixed server-side slot name. */
    /** Descripción: Ejecuta la operación «slot» correspondiente a la responsabilidad de VerifiedPackage. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String slot(TransformationExportLanguage language) {
        return language.name().toLowerCase(Locale.ROOT);
    }

    /** Converts a platform path to the portable checksum-manifest representation. */
    /** Descripción: Ejecuta la operación «portable» correspondiente a la responsabilidad de VerifiedPackage. Entradas: relative: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String portable(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    /** Sanitizes catalog diagnostics before returning them to the browser. */
    /** Descripción: Ejecuta la operación «sanitize» correspondiente a la responsabilidad de VerifiedPackage. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String sanitize(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value.replace('\0', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() <= 300 ? result : result.substring(0, 300) + "…";
    }

    private record VerifiedPackage(TransformationToolchainPackageManifest manifest, Path root, Path payload,
            String fingerprint, long totalBytes, int fileCount) {}
    private record PayloadEvidence(long totalBytes, int fileCount) {}
}

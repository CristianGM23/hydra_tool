package com.example.editor.transformation.nativeexec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.editor.concurrent.BoundedPermitPool;
import com.example.editor.contracts.metamodel.MetamodelQueryPort;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessRunRequest;
import com.example.editor.security.process.HydraExternalProcessRunResult;
import com.example.editor.security.process.HydraExternalProcessRunStatus;
import com.example.editor.security.process.HydraExternalProcessRunner;
import com.example.editor.security.process.HydraProcessTreeTerminator;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportArtifact;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.export.TransformationExportResult;
import com.example.editor.transformation.parity.TransformationParityArtifact;
import com.example.editor.validation.model.CanonicalModelDocument;

import tools.jackson.databind.ObjectMapper;

/**
 * Executes an administrator-configured native transformation bridge in an isolated workspace.
 * Browser input never controls the executable or arbitrary command arguments.
 */
@Service
public class TransformationNativeRuntimeExecutor {
    public static final String PROTOCOL_VERSION = "1.1";
    public static final String LEGACY_FORWARD_PROTOCOL_VERSION = "1.0";
    public static final String BRIDGE_DESCRIPTION_VERSION = "1.1";
    public static final String LEGACY_BRIDGE_DESCRIPTION_VERSION = "1.0";
    public static final int BRIDGE_EXIT_TOOL_UNAVAILABLE = 69;
    public static final int BRIDGE_EXIT_INCOMPATIBLE = 78;
    private final TransformationNativeRuntimeProperties properties;
    private final HydraTemporaryWorkspace temporaryWorkspace;
    private final MetamodelQueryPort metamodels;
    private final ObjectMapper mapper;
    private final HydraExternalProcessRunner processRunner;
    private final BoundedPermitPool processSlots;
    private final TransformationNativeRuntimeCommandResolver commandResolver;
    private final Map<TransformationExportLanguage, CachedDiagnostic> diagnosticCache = new ConcurrentHashMap<>();

    @Autowired
    /** Descripción: Inicializa una instancia de TransformationNativeRuntimeExecutor con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; securityPolicy: dato de entrada necesario para la operación; temporaryWorkspace: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; blockingIoExecutor: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeRuntimeExecutor(TransformationNativeRuntimeProperties properties,
            HydraExternalProcessPolicy securityPolicy, HydraTemporaryWorkspace temporaryWorkspace,
            MetamodelQueryPort metamodels, ObjectMapper mapper,
            @Qualifier("hydraBlockingIoExecutor") Executor blockingIoExecutor) {
        this(properties, securityPolicy, temporaryWorkspace, metamodels, mapper, blockingIoExecutor,
                new HydraProcessTreeTerminator());
    }

    /** Descripción: Inicializa una instancia de TransformationNativeRuntimeExecutor con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; securityPolicy: dato de entrada necesario para la operación; temporaryWorkspace: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; blockingIoExecutor: dato de entrada necesario para la operación; processTreeTerminator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ TransformationNativeRuntimeExecutor(TransformationNativeRuntimeProperties properties,
            HydraExternalProcessPolicy securityPolicy, HydraTemporaryWorkspace temporaryWorkspace,
            MetamodelQueryPort metamodels, ObjectMapper mapper,
            Executor blockingIoExecutor, HydraProcessTreeTerminator processTreeTerminator) {
        this.properties = properties;
        this.temporaryWorkspace = temporaryWorkspace;
        this.metamodels = metamodels;
        this.mapper = mapper;
        this.processRunner = new HydraExternalProcessRunner(blockingIoExecutor, securityPolicy, processTreeTerminator);
        this.processSlots = new BoundedPermitPool(properties.getMaximumConcurrentProcesses());
        this.commandResolver = new TransformationNativeRuntimeCommandResolver(properties, mapper);
    }

    /** Returns public capability metadata without exposing command lines or installation paths. */
    /** Descripción: Procesa la operación «runtimes» coordinando las responsabilidades de CollectedEvidence. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationNativeRuntimeDescriptor> runtimes() {
        return TransformationNativeRuntimeSupport.languages().stream().map(this::descriptor).toList();
    }

    /** Diagnoses one configured bridge by executing the bounded self-description protocol. */
    /** Descripción: Ejecuta la operación «diagnose» correspondiente a la responsabilidad de CollectedEvidence. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationNativeRuntimeDiagnostic diagnose(TransformationExportLanguage language) {
        if (language == null) throw new IllegalArgumentException("Native runtime language is required");
        TransformationNativeRuntimeCommandResolution resolution = commandResolver.resolve(language);
        String fingerprint = resolutionFingerprint(resolution);
        if (!properties.isEnabled()) {
            TransformationNativeRuntimeDiagnostic diagnostic = diagnostic(language, TransformationNativeRuntimeHealth.DISABLED,
                    resolution, false, "", "", "", "", List.of(), List.of(), List.of(), false, false, 0L,
                    List.of("Native transformation runtimes are disabled by server configuration"));
            diagnosticCache.put(language, new CachedDiagnostic(fingerprint, diagnostic));
            return diagnostic;
        }
        if (!resolution.diagnostics().isEmpty()) {
            TransformationNativeRuntimeDiagnostic diagnostic = diagnostic(language, TransformationNativeRuntimeHealth.CONFIGURATION_INVALID,
                    resolution, false, resolution.bridgeName(), resolution.bridgeVersion(), resolution.toolchainName(), resolution.toolchainVersion(),
                    List.of(), List.of(), List.of(), false, false, 0L, resolution.diagnostics());
            diagnosticCache.put(language, new CachedDiagnostic(fingerprint, diagnostic));
            return diagnostic;
        }
        if (!resolution.configured()) {
            TransformationNativeRuntimeDiagnostic diagnostic = diagnostic(language, TransformationNativeRuntimeHealth.NOT_CONFIGURED,
                    resolution, false, "", "", "", "", List.of(), List.of(), List.of(), false, false, 0L,
                    List.of("No server-side native runtime bridge is configured for " + language));
            diagnosticCache.put(language, new CachedDiagnostic(fingerprint, diagnostic));
            return diagnostic;
        }

        Path workspace = null;
        BoundedPermitPool.Permit permit = null;
        try {
            permit = processSlots.tryAcquire(properties.getProcessAcquireTimeoutSeconds(), TimeUnit.SECONDS);
            if (permit == null) return cacheDiagnostic(fingerprint, diagnostic(language, TransformationNativeRuntimeHealth.FAILED,
                    resolution, false, "", "", "", "", List.of(), List.of(), List.of(), false, false, 0L,
                    List.of("Native transformation diagnostic capacity is busy; retry later")));
            workspace = temporaryWorkspace.create("transformation-diagnose-" + language.name().toLowerCase() + "-");
            temporaryWorkspace.verify(workspace);
            List<String> command = new ArrayList<>(resolution.command());
            command.add("--hydra-describe");
            HydraExternalProcessRunResult execution = processRunner.run(processRequest(workspace, command,
                    properties.getDiagnosticTimeoutSeconds()));
            if (execution.status() != HydraExternalProcessRunStatus.COMPLETED) {
                TransformationNativeRuntimeHealth health = switch (execution.status()) {
                    case REJECTED -> TransformationNativeRuntimeHealth.CONFIGURATION_INVALID;
                    case TOOL_UNAVAILABLE -> TransformationNativeRuntimeHealth.TOOL_UNAVAILABLE;
                    case TIMED_OUT, CANCELLED, FAILED -> TransformationNativeRuntimeHealth.FAILED;
                    case COMPLETED -> throw new IllegalArgumentException("Completed diagnostic is not a failure");
                };
                return cacheDiagnostic(fingerprint, diagnostic(language, health, resolution, false,
                        resolution.bridgeName(), resolution.bridgeVersion(), resolution.toolchainName(), resolution.toolchainVersion(),
                        List.of(), List.of(), List.of(), false, false, execution.durationMillis(), boundedDiagnostics(execution.diagnostics())));
            }
            if (execution.exitCode() == null || execution.exitCode() != 0) {
                return cacheDiagnostic(fingerprint, diagnostic(language, TransformationNativeRuntimeHealth.FAILED, resolution, false,
                        resolution.bridgeName(), resolution.bridgeVersion(), resolution.toolchainName(), resolution.toolchainVersion(),
                        List.of(), List.of(), List.of(), false, false, execution.durationMillis(),
                        List.of("Native bridge diagnostic exited with code " + execution.exitCode())));
            }
            TransformationNativeBridgeDescription description;
            try {
                description = mapper.readValue(execution.stdout(), TransformationNativeBridgeDescription.class);
            } catch (Exception exception) {
                return cacheDiagnostic(fingerprint, diagnostic(language, TransformationNativeRuntimeHealth.UNVERIFIED, resolution, false,
                        resolution.bridgeName(), resolution.bridgeVersion(), resolution.toolchainName(), resolution.toolchainVersion(),
                        List.of(), List.of(), List.of(), false, false, execution.durationMillis(),
                        List.of("Configured bridge does not expose a supported HYDRA self-description protocol")));
            }
            return cacheDiagnostic(fingerprint, validateDescription(language, resolution, description, execution.durationMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return cacheDiagnostic(fingerprint, diagnostic(language, TransformationNativeRuntimeHealth.FAILED, resolution, false,
                    resolution.bridgeName(), resolution.bridgeVersion(), resolution.toolchainName(), resolution.toolchainVersion(),
                    List.of(), List.of(), List.of(), false, false, 0L,
                    List.of("Native runtime diagnostic was interrupted while waiting for capacity")));
        } finally {
            if (permit != null) permit.close();
            temporaryWorkspace.cleanup(workspace);
        }
    }

    /** Invalidates cached self-description evidence after a managed bundle lifecycle change. */
    /** Descripción: Elimina o libera el recurso gestionado por «invalidateDiagnostic» de forma controlada. Entradas: language: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void invalidateDiagnostic(TransformationExportLanguage language) {
        if (language != null) diagnosticCache.remove(language);
    }

    /** Executes one compatible export through its native bridge and returns bounded evidence. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de LegacyForwardBridgeRequest. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; export: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationNativeRuntimeOutcome execute(TransformationDefinitionDocument document,
            TransformationExportLanguage language, CanonicalModelDocument sourceModel,
            TransformationExportResult export) {
        return execute(document, language, TransformationExecutionDirection.FORWARD, sourceModel, export);
    }

    /** Executes one explicit direction through its native bridge while preserving backward compatibility for FORWARD callers. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de LegacyForwardBridgeRequest. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; export: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationNativeRuntimeOutcome execute(TransformationDefinitionDocument document,
            TransformationExportLanguage language, TransformationExecutionDirection direction,
            CanonicalModelDocument sourceModel, TransformationExportResult export) {
        if (document == null || language == null || direction == null || sourceModel == null || export == null)
            throw new IllegalArgumentException("Transformation, language, direction, source model and compatible export are required");
        if (!TransformationNativeRuntimeSupport.supports(language)) return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_CONFIGURED,
                0L, null, List.of("No native runtime protocol exists for " + language), null, List.of());
        if (!TransformationNativeRuntimeSupport.supports(language, document.kind())) return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_RUN,
                0L, null, List.of("Native runtime " + language + " does not support transformation kind " + document.kind()), null, List.of());
        if (!TransformationNativeRuntimeSupport.supportsIndependentParity(language, document.kind())) return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_RUN,
                0L, null, List.of("Independent native parity is not yet available for " + language
                        + " because its export still delegates to the canonical HYDRA runtime"), null, List.of());
        if (!TransformationNativeRuntimeSupport.supportsDirection(language, document.kind(), direction)) return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_RUN,
                0L, null, List.of("Native runtime " + language + " does not support " + direction + " parity for " + document.kind()), null, List.of());
        if (!export.compatible()) return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_RUN,
                0L, null, List.of("Native execution was not started because export compatibility is blocked"), null, List.of());
        if (!properties.isEnabled()) return outcome(document, language, direction, TransformationNativeRuntimeStatus.DISABLED,
                0L, null, List.of("Native transformation runtimes are disabled by server configuration"), null, List.of());
        TransformationNativeRuntimeCommandResolution resolution = commandResolver.resolve(language);
        if (!resolution.diagnostics().isEmpty()) return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_CONFIGURED,
                0L, null, resolution.diagnostics(), null, List.of());
        List<String> baseCommand = resolution.command();
        if (baseCommand.isEmpty()) return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_CONFIGURED,
                0L, null, List.of("No server-side native runtime bridge is configured for " + language), null, List.of());
        CachedDiagnostic cached = diagnosticCache.get(language);
        if (cached != null && cached.fingerprint().equals(resolutionFingerprint(resolution))) {
            TransformationNativeRuntimeDiagnostic diagnostic = cached.diagnostic();
            if (diagnostic.health() == TransformationNativeRuntimeHealth.INCOMPATIBLE)
                return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_RUN,
                        0L, null, List.of("Native runtime diagnosis is incompatible with HYDRA; diagnose or replace the bridge before execution"), null, List.of());
            if (diagnostic.health() == TransformationNativeRuntimeHealth.TOOL_UNAVAILABLE)
                return outcome(document, language, direction, TransformationNativeRuntimeStatus.TOOL_UNAVAILABLE,
                        0L, null, diagnostic.diagnostics().isEmpty()
                                ? List.of("Native runtime toolchain is not available on this server") : diagnostic.diagnostics(), null, List.of());
            if (diagnostic.health() == TransformationNativeRuntimeHealth.READY
                    && (!diagnostic.supportedKinds().contains(document.kind()) || !diagnostic.supportedDirections().contains(direction)))
                return outcome(document, language, direction, TransformationNativeRuntimeStatus.NOT_RUN,
                        0L, null, List.of("Diagnosed native bridge does not advertise " + document.kind() + " " + direction + " support"), null, List.of());
        }

        Path workspace = null;
        BoundedPermitPool.Permit permit = null;
        Instant started = Instant.now();
        try {
            permit = processSlots.tryAcquire(properties.getProcessAcquireTimeoutSeconds(), TimeUnit.SECONDS);
            if (permit == null) return outcome(document, language, direction, TransformationNativeRuntimeStatus.CAPACITY_BUSY,
                    elapsed(started), null, List.of("Native transformation execution capacity is busy; retry later"), null, List.of());

            workspace = temporaryWorkspace.create("transformation-" + language.name().toLowerCase() + "-");
            PreparedBridge prepared = prepare(workspace, document, language, direction, sourceModel, export);
            temporaryWorkspace.verify(workspace);

            List<String> command = new ArrayList<>(baseCommand);
            command.add(prepared.requestFile().toAbsolutePath().normalize().toString());
            HydraExternalProcessRunResult execution = processRunner.run(processRequest(workspace, command,
                    properties.getDefaultTimeoutSeconds()));

            if (execution.status() != HydraExternalProcessRunStatus.COMPLETED)
                return failedExecution(document, language, direction, execution);
            if (execution.exitCode() == null || execution.exitCode() != 0) {
                List<String> diagnostics = new ArrayList<>(execution.diagnostics());
                diagnostics.add(0, "Native bridge exited with code " + execution.exitCode());
                appendOutputDiagnostic(diagnostics, "stderr", execution.stderr());
                appendOutputDiagnostic(diagnostics, "stdout", execution.stdout());
                TransformationNativeRuntimeStatus bridgeStatus = execution.exitCode() != null
                        && execution.exitCode() == BRIDGE_EXIT_TOOL_UNAVAILABLE
                                ? TransformationNativeRuntimeStatus.TOOL_UNAVAILABLE
                                : execution.exitCode() != null && execution.exitCode() == BRIDGE_EXIT_INCOMPATIBLE
                                        ? TransformationNativeRuntimeStatus.NOT_RUN
                                        : TransformationNativeRuntimeStatus.FAILED;
                return outcome(document, language, direction, bridgeStatus,
                        execution.durationMillis(), execution.exitCode(), diagnostics, null, List.of());
            }

            temporaryWorkspace.verify(workspace);
            CollectedEvidence evidence = collect(workspace, prepared.outputDirectory(), prepared.resultFile(), prepared.protocolVersion(), document, language, direction);
            List<String> diagnostics = new ArrayList<>(evidence.diagnostics());
            diagnostics.addAll(execution.diagnostics());
            return outcome(document, language, direction, TransformationNativeRuntimeStatus.SUCCEEDED,
                    execution.durationMillis(), execution.exitCode(), diagnostics, evidence.targetModel(), evidence.artifacts());
        } catch (InvalidNativeResult exception) {
            return outcome(document, language, direction, TransformationNativeRuntimeStatus.INVALID_RESULT,
                    elapsed(started), null, List.of(exception.getMessage()), null, List.of());
        } catch (IOException exception) {
            return outcome(document, language, direction, TransformationNativeRuntimeStatus.FAILED, elapsed(started), null,
                    List.of("Could not prepare native runtime workspace: " + safeMessage(exception)), null, List.of());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return outcome(document, language, direction, TransformationNativeRuntimeStatus.CANCELLED, elapsed(started), null,
                    List.of("Native transformation execution was interrupted while waiting for capacity"), null, List.of());
        } catch (RuntimeException exception) {
            return outcome(document, language, direction, TransformationNativeRuntimeStatus.FAILED, elapsed(started), null,
                    List.of("Native transformation execution failed: " + safeMessage(exception)), null, List.of());
        } finally {
            if (permit != null) permit.close();
            temporaryWorkspace.cleanup(workspace);
        }
    }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de CollectedEvidence. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeDescriptor descriptor(TransformationExportLanguage language) {
        TransformationNativeRuntimeCommandResolution resolution = commandResolver.resolve(language);
        String fingerprint = resolutionFingerprint(resolution);
        CachedDiagnostic cached = diagnosticCache.get(language);
        TransformationNativeRuntimeHealth health;
        if (!properties.isEnabled()) health = TransformationNativeRuntimeHealth.DISABLED;
        else if (!resolution.diagnostics().isEmpty()) health = TransformationNativeRuntimeHealth.CONFIGURATION_INVALID;
        else if (!resolution.configured()) health = TransformationNativeRuntimeHealth.NOT_CONFIGURED;
        else if (cached != null && cached.fingerprint().equals(fingerprint)) health = cached.diagnostic().health();
        else health = TransformationNativeRuntimeHealth.UNVERIFIED;
        List<TransformationKind> kinds = TransformationNativeRuntimeSupport.supportedKinds(language).stream().sorted().toList();
        List<TransformationExecutionDirection> directions = TransformationNativeRuntimeSupport.supportedDirections(language).stream().sorted().toList();
        if (cached != null && cached.fingerprint().equals(fingerprint) && cached.diagnostic().health() == TransformationNativeRuntimeHealth.READY) {
            kinds = cached.diagnostic().supportedKinds();
            directions = cached.diagnostic().supportedDirections();
        }
        return new TransformationNativeRuntimeDescriptor(language, properties.isEnabled(), resolution.configured(),
                TransformationNativeRuntimeSupport.mode(language) == TransformationNativeRuntimeMode.NATIVE_EXPORT,
                TransformationNativeRuntimeSupport.mode(language), PROTOCOL_VERSION, kinds, directions,
                TransformationNativeRuntimeSupport.supportsFullBidirectionalSynchronization(language),
                TransformationNativeRuntimeSupport.supportsIncrementalSynchronization(language), resolution.source(), health);
    }

    /** Descripción: Valida la autodescripción de un bridge, conserva compatibilidad con el esquema 1.0 e intersecta las capacidades declaradas con la política canónica de HYDRA. Entradas: lenguaje, resolución del comando, descripción del bridge y duración del diagnóstico. Salida: diagnóstico sanitizado y fail-closed del runtime. */
    /** Descripción: Valida las condiciones necesarias para «validateDescription» y rechaza entradas no válidas. Entradas: language: dato de entrada necesario para la operación; resolution: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; durationMillis: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeDiagnostic validateDescription(TransformationExportLanguage language,
            TransformationNativeRuntimeCommandResolution resolution, TransformationNativeBridgeDescription description,
            long durationMillis) {
        List<String> errors = new ArrayList<>();
        if (description == null) errors.add("Native bridge self-description is empty");
        boolean legacyDescription = description != null
                && LEGACY_BRIDGE_DESCRIPTION_VERSION.equals(description.schemaVersion());
        boolean currentDescription = description != null
                && BRIDGE_DESCRIPTION_VERSION.equals(description.schemaVersion());
        if (description != null && !legacyDescription && !currentDescription)
            errors.add("Native bridge self-description schema is not supported");
        if (description != null && description.language() != language)
            errors.add("Native bridge self-description language does not match the configured runtime");
        if (description != null && resolution.source() == TransformationNativeRuntimeSource.MANAGED_BUNDLE) {
            compareDeclared(errors, "bridge name", resolution.bridgeName(), description.bridgeName());
            compareDeclared(errors, "bridge version", resolution.bridgeVersion(), description.bridgeVersion());
            compareDeclared(errors, "toolchain name", resolution.toolchainName(), description.toolchainName());
            compareDeclared(errors, "toolchain version", resolution.toolchainVersion(), description.toolchainVersion());
        }
        if (description == null) return diagnostic(language, TransformationNativeRuntimeHealth.INCOMPATIBLE, resolution, false,
                "", "", "", "", List.of(), List.of(), List.of(), false, false, durationMillis, errors);

        Set<TransformationKind> hydraKinds = TransformationNativeRuntimeSupport.supportedKinds(language);
        List<TransformationKind> effectiveKinds = description.supportedKinds().stream().filter(hydraKinds::contains).distinct().sorted().toList();
        Set<TransformationExecutionDirection> hydraDirections = TransformationNativeRuntimeSupport.supportedDirections(language);
        List<TransformationExecutionDirection> effectiveDirections = description.supportedDirections().stream()
                .filter(hydraDirections::contains).distinct().sorted().toList();
        if (TransformationNativeRuntimeSupport.mode(language) == TransformationNativeRuntimeMode.NATIVE_EXPORT && effectiveKinds.isEmpty())
            errors.add("Native bridge does not advertise any transformation kind supported by HYDRA for this runtime");
        if (TransformationNativeRuntimeSupport.mode(language) == TransformationNativeRuntimeMode.NATIVE_EXPORT && effectiveDirections.isEmpty())
            errors.add("Native bridge does not advertise any execution direction supported by HYDRA for this runtime");
        if (effectiveDirections.contains(TransformationExecutionDirection.FORWARD) && !description.protocolVersions().contains(LEGACY_FORWARD_PROTOCOL_VERSION))
            errors.add("Native bridge advertises FORWARD but does not support bridge protocol 1.0");
        if (effectiveDirections.contains(TransformationExecutionDirection.REVERSE) && !description.protocolVersions().contains(PROTOCOL_VERSION))
            errors.add("Native bridge advertises REVERSE but does not support bridge protocol 1.1");

        boolean fullSync = description.fullBidirectionalSynchronization()
                && TransformationNativeRuntimeSupport.supportsFullBidirectionalSynchronization(language);
        boolean incremental = description.incrementalSynchronization()
                && TransformationNativeRuntimeSupport.supportsIncrementalSynchronization(language);
        List<String> messages = new ArrayList<>(description.diagnostics());
        if (description.fullBidirectionalSynchronization() && !fullSync)
            messages.add("Bridge full bidirectional synchronization capability is not enabled by the current HYDRA contract");
        if (description.incrementalSynchronization() && !incremental)
            messages.add("Bridge incremental synchronization capability is not enabled by the current HYDRA contract");
        messages.addAll(errors);

        boolean toolAvailable = legacyDescription || description.toolAvailable();
        boolean executionAvailable = legacyDescription || description.executionAvailable();
        TransformationNativeRuntimeMode mode = TransformationNativeRuntimeSupport.mode(language);
        TransformationNativeRuntimeHealth health;
        boolean compatible = errors.isEmpty();
        if (!errors.isEmpty()) health = TransformationNativeRuntimeHealth.INCOMPATIBLE;
        else if (!toolAvailable || (mode == TransformationNativeRuntimeMode.NATIVE_EXPORT && !executionAvailable))
            health = TransformationNativeRuntimeHealth.TOOL_UNAVAILABLE;
        else health = TransformationNativeRuntimeHealth.READY;
        return diagnostic(language, health, resolution, compatible, description.bridgeName(), description.bridgeVersion(),
                description.toolchainName(), description.toolchainVersion(), description.protocolVersions(), effectiveKinds,
                effectiveDirections, fullSync, incremental, durationMillis, messages);
    }

    /** Descripción: Ejecuta la operación «compareDeclared» correspondiente a la responsabilidad de CollectedEvidence. Entradas: errors: dato de entrada necesario para la operación; label: dato de entrada necesario para la operación; declared: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void compareDeclared(List<String> errors, String label, String declared, String actual) {
        if (declared == null || declared.isBlank()) return;
        if (actual == null || !declared.equals(actual.trim())) errors.add("Managed " + label + " does not match bridge self-description");
    }

    /** Descripción: Ejecuta la operación «diagnostic» correspondiente a la responsabilidad de CollectedEvidence. Entradas: language: dato de entrada necesario para la operación; health: dato de entrada necesario para la operación; resolution: dato de entrada necesario para la operación; compatible: dato de entrada necesario para la operación; bridgeName: dato de entrada necesario para la operación; bridgeVersion: dato de entrada necesario para la operación; toolchainName: dato de entrada necesario para la operación; toolchainVersion: dato de entrada necesario para la operación; protocols: dato de entrada necesario para la operación; kinds: dato de entrada necesario para la operación; directions: dato de entrada necesario para la operación; fullSync: dato de entrada necesario para la operación; incremental: dato de entrada necesario para la operación; durationMillis: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeDiagnostic diagnostic(TransformationExportLanguage language,
            TransformationNativeRuntimeHealth health, TransformationNativeRuntimeCommandResolution resolution,
            boolean compatible, String bridgeName, String bridgeVersion, String toolchainName, String toolchainVersion,
            List<String> protocols, List<TransformationKind> kinds, List<TransformationExecutionDirection> directions,
            boolean fullSync, boolean incremental, long durationMillis, List<String> diagnostics) {
        return new TransformationNativeRuntimeDiagnostic(language, health, resolution.source(), resolution.configured(), compatible,
                bridgeName, bridgeVersion, toolchainName, toolchainVersion, protocols, kinds, directions,
                fullSync, incremental, durationMillis, boundedDiagnostics(diagnostics));
    }

    /** Descripción: Ejecuta la operación «cacheDiagnostic» correspondiente a la responsabilidad de CollectedEvidence. Entradas: fingerprint: dato de entrada necesario para la operación; diagnostic: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeDiagnostic cacheDiagnostic(String fingerprint, TransformationNativeRuntimeDiagnostic diagnostic) {
        diagnosticCache.put(diagnostic.language(), new CachedDiagnostic(fingerprint, diagnostic));
        return diagnostic;
    }

    /** Descripción: Ejecuta la operación «boundedDiagnostics» correspondiente a la responsabilidad de CollectedEvidence. Entradas: diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> boundedDiagnostics(List<String> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) return List.of();
        List<String> bounded = new ArrayList<>();
        for (String value : diagnostics) {
            if (value == null || value.isBlank()) continue;
            String sanitized = value.replace('\0', ' ').replace('\r', ' ').replace('\n', ' ').trim();
            if (sanitized.length() > 500) sanitized = sanitized.substring(0, 500) + "…";
            bounded.add(sanitized);
            if (bounded.size() >= 16) break;
        }
        return List.copyOf(bounded);
    }

    /** Descripción: Procesa la operación «processRequest» coordinando las responsabilidades de CollectedEvidence. Entradas: workspace: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación; timeoutSeconds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraExternalProcessRunRequest processRequest(Path workspace, List<String> command, int timeoutSeconds) {
        return new HydraExternalProcessRunRequest(workspace, command, timeoutSeconds, Map.of("NO_COLOR", "1"),
                properties.getMaximumProcessOutputBytes(), properties.getProcessOutputJoinTimeoutSeconds(),
                properties.getProcessTerminationGraceMillis(), properties.getProcessTerminationForceWaitMillis(),
                properties.getProcessTerminationMaximumPasses(), properties.getProcessTreeObservationIntervalMillis());
    }

    /** Descripción: Ejecuta la operación «resolutionFingerprint» correspondiente a la responsabilidad de CollectedEvidence. Entradas: resolution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolutionFingerprint(TransformationNativeRuntimeCommandResolution resolution) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = resolution.source().name() + "\u0000" + String.join("\u0000", resolution.command())
                    + "\u0000" + resolution.bridgeVersion() + "\u0000" + resolution.toolchainVersion();
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Descripción: Ejecuta la operación «failedExecution» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; execution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeOutcome failedExecution(TransformationDefinitionDocument document,
            TransformationExportLanguage language, TransformationExecutionDirection direction,
            HydraExternalProcessRunResult execution) {
        TransformationNativeRuntimeStatus status = switch (execution.status()) {
            case REJECTED -> execution.diagnostics().stream().anyMatch(message -> message != null
                    && message.startsWith("External executable is not allowed:"))
                    ? TransformationNativeRuntimeStatus.TOOL_UNAVAILABLE : TransformationNativeRuntimeStatus.FAILED;
            case TOOL_UNAVAILABLE -> TransformationNativeRuntimeStatus.TOOL_UNAVAILABLE;
            case TIMED_OUT -> TransformationNativeRuntimeStatus.TIMED_OUT;
            case CANCELLED -> TransformationNativeRuntimeStatus.CANCELLED;
            case FAILED -> TransformationNativeRuntimeStatus.FAILED;
            case COMPLETED -> throw new IllegalArgumentException("Completed execution is not a failure");
        };
        List<String> diagnostics = new ArrayList<>(execution.diagnostics());
        if (status == TransformationNativeRuntimeStatus.TIMED_OUT)
            diagnostics.add(0, "Native bridge exceeded " + properties.getDefaultTimeoutSeconds() + " seconds");
        appendOutputDiagnostic(diagnostics, "stderr", execution.stderr());
        return outcome(document, language, direction, status, execution.durationMillis(), execution.exitCode(), diagnostics, null, List.of());
    }

    /** Descripción: Ejecuta la operación «prepare» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: workspace: dato de entrada necesario para la operación; document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; export: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PreparedBridge prepare(Path workspace, TransformationDefinitionDocument document,
            TransformationExportLanguage language, TransformationExecutionDirection direction,
            CanonicalModelDocument sourceModel, TransformationExportResult export) throws IOException {
        Path transformationFile = workspace.resolve("transformation.json");
        Path sourceModelFile = workspace.resolve("source-model.json");
        Path metamodelDirectory = Files.createDirectories(workspace.resolve("metamodels"));
        Path exportDirectory = Files.createDirectories(workspace.resolve("export"));
        Path outputDirectory = Files.createDirectories(workspace.resolve("native-output"));
        Path resultFile = workspace.resolve("native-result.json");

        writeJson(transformationFile, document);
        writeJson(sourceModelFile, sourceModel);
        boolean reverse = document.kind() == TransformationKind.M2M && direction == TransformationExecutionDirection.REVERSE;
        String sourceMetamodelId = reverse
                ? (document.targets().isEmpty() ? "" : document.targets().getFirst().metamodelId())
                : (document.sources().isEmpty() ? "" : document.sources().getFirst().metamodelId());
        String targetMetamodelId = reverse
                ? (document.sources().isEmpty() ? "" : document.sources().getFirst().metamodelId())
                : (document.targets().isEmpty() ? "" : document.targets().getFirst().metamodelId());
        MetaModel sourceMetamodel = loadMetamodel(sourceMetamodelId);
        Path sourceMetamodelFile = metamodelDirectory.resolve("source.json");
        if(sourceMetamodel != null) writeJson(sourceMetamodelFile, sourceMetamodel);
        MetaModel targetMetamodel = document.kind() == TransformationKind.M2M ? loadMetamodel(targetMetamodelId) : null;
        Path targetMetamodelFile = metamodelDirectory.resolve("target.json");
        if(targetMetamodel != null) writeJson(targetMetamodelFile, targetMetamodel);

        List<String> artifactFiles = new ArrayList<>();
        for(TransformationExportArtifact artifact : export.artifacts()) {
            Path relative = safeRelative(artifact.path(), "export artifact path");
            Path target = exportDirectory.resolve(relative).normalize();
            requireInside(exportDirectory, target, "export artifact path");
            if(target.getParent() != null) Files.createDirectories(target.getParent());
            Files.writeString(target, artifact.content(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            artifactFiles.add(relativeUnix(workspace.relativize(target)));
        }

        Path requestFile = workspace.resolve("native-request.json");
        String protocolVersion = protocolVersionFor(direction);
        String transformationPath = relativeUnix(workspace.relativize(transformationFile));
        String sourceModelPath = relativeUnix(workspace.relativize(sourceModelFile));
        String sourceMetamodelPath = sourceMetamodel == null ? "" : relativeUnix(workspace.relativize(sourceMetamodelFile));
        String targetMetamodelPath = targetMetamodel == null ? "" : relativeUnix(workspace.relativize(targetMetamodelFile));
        String outputPath = relativeUnix(workspace.relativize(outputDirectory));
        String resultPath = relativeUnix(workspace.relativize(resultFile));
        if(direction == TransformationExecutionDirection.FORWARD) {
            writeJson(requestFile, new LegacyForwardBridgeRequest(protocolVersion, document.id(), language, document.kind(),
                    transformationPath, sourceModelPath, sourceMetamodelPath, targetMetamodelPath, artifactFiles, outputPath, resultPath));
        } else {
            writeJson(requestFile, new TransformationNativeBridgeRequest(protocolVersion, document.id(), language, document.kind(),
                    direction, transformationPath, sourceModelPath, sourceMetamodelPath, targetMetamodelPath,
                    artifactFiles, outputPath, resultPath));
        }
        return new PreparedBridge(requestFile, outputDirectory, resultFile, protocolVersion);
    }

    /** Descripción: Ejecuta la operación «collect» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: workspace: dato de entrada necesario para la operación; outputDirectory: dato de entrada necesario para la operación; resultFile: archivo o contenido que debe procesarse; expectedProtocolVersion: dato de entrada necesario para la operación; document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CollectedEvidence collect(Path workspace, Path outputDirectory, Path resultFile, String expectedProtocolVersion,
            TransformationDefinitionDocument document, TransformationExportLanguage language,
            TransformationExecutionDirection direction) {
        requireRegularEvidenceFile(workspace, resultFile, properties.getMaximumBridgeResultBytes(), "native result descriptor");
        TransformationNativeBridgeResult result;
        try { result = mapper.readValue(resultFile.toFile(), TransformationNativeBridgeResult.class); }
        catch(Exception exception) { throw new InvalidNativeResult("Native bridge result is not valid JSON for protocol " + expectedProtocolVersion); }
        if(!expectedProtocolVersion.equals(result.schemaVersion())) throw new InvalidNativeResult("Native bridge result uses unsupported schemaVersion: " + result.schemaVersion()
                + "; expected " + expectedProtocolVersion + " for " + direction);
        if(!document.id().equals(result.transformationId())) throw new InvalidNativeResult("Native bridge result belongs to a different transformation");
        if(language != result.language()) throw new InvalidNativeResult("Native bridge result language does not match the requested runtime");
        if(document.kind() != result.kind()) throw new InvalidNativeResult("Native bridge result kind does not match the transformation");
        if(direction != result.direction()) throw new InvalidNativeResult("Native bridge result direction does not match the requested execution direction");

        List<String> diagnostics = new ArrayList<>(result.diagnostics());
        if(document.kind() == TransformationKind.M2M) {
            if(result.targetModelFile().isBlank()) throw new InvalidNativeResult("M2M native bridge result must provide targetModelFile");
            Path target = resolveOutputFile(workspace, outputDirectory, result.targetModelFile(), "targetModelFile");
            requireRegularEvidenceFile(workspace, target, properties.getMaximumEvidenceFileBytes(), "native target model");
            try {
                CanonicalModelDocument model = mapper.readValue(target.toFile(), CanonicalModelDocument.class);
                return new CollectedEvidence(model, List.of(), diagnostics);
            } catch(Exception exception) {
                throw new InvalidNativeResult("Native M2M target model is not a valid CanonicalModelDocument");
            }
        }
        if(document.kind() == TransformationKind.M2T) {
            if(!result.targetModelFile().isBlank()) throw new InvalidNativeResult("M2T native bridge result must not provide targetModelFile");
            List<TransformationParityArtifact> artifacts = new ArrayList<>();
            Set<String> logicalPaths = new HashSet<>();
            Set<Path> evidenceFiles = new HashSet<>();
            for(TransformationNativeBridgeArtifact artifact : result.artifacts()) {
                Path logical = safeRelative(artifact.path(), "native artifact logical path");
                String logicalPath = relativeUnix(logical);
                if(!logicalPaths.add(logicalPath)) throw new InvalidNativeResult("Native bridge result contains duplicate artifact path: " + logicalPath);
                Path file = resolveOutputFile(workspace, outputDirectory, artifact.file(), "native artifact file");
                if(!evidenceFiles.add(file)) throw new InvalidNativeResult("Native bridge result reuses one evidence file for multiple artifacts");
                requireRegularEvidenceFile(workspace, file, properties.getMaximumEvidenceFileBytes(), "native generated artifact");
                try { artifacts.add(new TransformationParityArtifact(logicalPath, Files.readString(file, StandardCharsets.UTF_8))); }
                catch(IOException exception) { throw new InvalidNativeResult("Could not read native generated artifact: " + logicalPath); }
            }
            return new CollectedEvidence(null, artifacts, diagnostics);
        }
        throw new InvalidNativeResult("Native bridge execution only supports M2M and M2T transformations");
    }

    /** Descripción: Obtiene la información correspondiente a «resolveOutputFile» dentro de LegacyForwardBridgeRequest. Entradas: workspace: dato de entrada necesario para la operación; outputDirectory: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; label: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveOutputFile(Path workspace, Path outputDirectory, String value, String label) {
        Path relative = safeRelative(value, label);
        Path resolved = workspace.resolve(relative).normalize();
        requireInside(outputDirectory, resolved, label);
        return resolved;
    }

    /** Descripción: Ejecuta la operación «safeRelative» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: value: valor de entrada que debe procesarse; label: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeRelative(String value, String label) {
        if(value == null || value.isBlank()) throw new InvalidNativeResult(label + " is empty");
        String normalizedText = value.trim().replace('\\', '/');
        if(normalizedText.matches("^[A-Za-z]:.*")) throw new InvalidNativeResult(label + " must be workspace-relative");
        Path path;
        try { path = Path.of(normalizedText).normalize(); }
        catch(RuntimeException exception) { throw new InvalidNativeResult(label + " is not a valid relative path"); }
        if(path.isAbsolute() || path.getNameCount() == 0 || path.toString().isBlank() || path.startsWith("..")) throw new InvalidNativeResult(label + " must stay inside the isolated workspace");
        return path;
    }

    /** Descripción: Valida las condiciones necesarias para «requireInside» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación; label: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireInside(Path root, Path path, String label) {
        if(!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))
            throw new InvalidNativeResult(label + " escapes the isolated native output directory");
    }

    /** Descripción: Valida las condiciones necesarias para «requireRegularEvidenceFile» y rechaza entradas no válidas. Entradas: workspace: dato de entrada necesario para la operación; file: archivo o contenido que debe procesarse; maximumBytes: dato de entrada necesario para la operación; label: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireRegularEvidenceFile(Path workspace, Path file, long maximumBytes, String label) {
        try {
            requireInside(workspace, file, label);
            if(Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                throw new InvalidNativeResult(label + " is missing or is not a regular file");
            if(Files.size(file) > maximumBytes) throw new InvalidNativeResult(label + " exceeds the configured evidence size limit");
        } catch(IOException exception) { throw new InvalidNativeResult("Could not inspect " + label); }
    }

    /** Descripción: Obtiene la información correspondiente a «loadMetamodel» dentro de LegacyForwardBridgeRequest. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel loadMetamodel(String id) {
        if(id == null || id.isBlank()) return null;
        return metamodels.requireMetamodel(id);
    }

    /** Descripción: Actualiza el estado gestionado por LegacyForwardBridgeRequest mediante la operación «writeJson». Entradas: path: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeJson(Path path, Object value) throws IOException {
        if(path.getParent() != null) Files.createDirectories(path.getParent());
        Files.write(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    /** Descripción: Ejecuta la operación «appendOutputDiagnostic» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: diagnostics: dato de entrada necesario para la operación; stream: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendOutputDiagnostic(List<String> diagnostics, String stream, String value) {
        if(value == null || value.isBlank()) return;
        String sanitized = value.replace('\0', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        if(sanitized.length() > 2_000) sanitized = sanitized.substring(0, 2_000) + "…";
        diagnostics.add("Native bridge " + stream + ": " + sanitized);
    }

    /** Descripción: Ejecuta la operación «outcome» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; duration: dato de entrada necesario para la operación; exitCode: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; targetModel: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeRuntimeOutcome outcome(TransformationDefinitionDocument document,
            TransformationExportLanguage language, TransformationExecutionDirection direction,
            TransformationNativeRuntimeStatus status, long duration, Integer exitCode,
            List<String> diagnostics, CanonicalModelDocument targetModel,
            List<TransformationParityArtifact> artifacts) {
        return new TransformationNativeRuntimeOutcome(new TransformationNativeRuntimeReport(language,
                document.kind(), direction, status, duration, exitCode, diagnostics), targetModel, artifacts);
    }

    /** Descripción: Ejecuta la operación «protocolVersionFor» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String protocolVersionFor(TransformationExecutionDirection direction) {
        return direction == TransformationExecutionDirection.REVERSE ? PROTOCOL_VERSION : LEGACY_FORWARD_PROTOCOL_VERSION;
    }

    /** Descripción: Ejecuta la operación «elapsed» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: started: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long elapsed(Instant started){return Math.max(0L, Duration.between(started, Instant.now()).toMillis());}
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception){
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
    /** Descripción: Ejecuta la operación «relativeUnix» correspondiente a la responsabilidad de LegacyForwardBridgeRequest. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relativeUnix(Path path){return path.toString().replace('\\','/');}

    /** Exact protocol 1.0 request shape retained for forward-only third-party bridges. */
    private record LegacyForwardBridgeRequest(
            String schemaVersion,
            String transformationId,
            TransformationExportLanguage language,
            TransformationKind kind,
            String transformationFile,
            String sourceModelFile,
            String sourceMetamodelFile,
            String targetMetamodelFile,
            List<String> exportArtifactFiles,
            String outputDirectory,
            String resultFile) {}

    private record CachedDiagnostic(String fingerprint, TransformationNativeRuntimeDiagnostic diagnostic) {}
    private record PreparedBridge(Path requestFile, Path outputDirectory, Path resultFile, String protocolVersion) {}
    private record CollectedEvidence(CanonicalModelDocument targetModel, List<TransformationParityArtifact> artifacts, List<String> diagnostics) {}
    private static final class InvalidNativeResult extends RuntimeException {
        /** Descripción: Inicializa una instancia de InvalidNativeResult con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private InvalidNativeResult(String message){super(message);}
    }
}

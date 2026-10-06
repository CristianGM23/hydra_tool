package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

import com.example.editor.io.AtomicFiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates, versions, persists and registers data-only adapters without loading external code. */
@Service
public class UpdateSiteDeclarativeAdapterService {
    private static final String DEFAULT_VERSION = "1.0.0";
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteExtensionAdapterCatalog catalog;
    private final ObjectMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteDeclarativeAdapterService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; catalog: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteDeclarativeAdapterService(UpdateSiteImportProperties properties,
            UpdateSiteExtensionAdapterCatalog catalog, ObjectMapper mapper) {
        this.properties = properties;
        this.catalog = catalog;
        this.mapper = mapper;
        loadPersisted();
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteDeclarativeAdapterValidation preview(byte[] content) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if(content == null || content.length == 0) return new UpdateSiteDeclarativeAdapterValidation(false, null, "", List.of("Adapter JSON is empty"), List.of());
        if(content.length > properties.getMaxEntryBytes()) return new UpdateSiteDeclarativeAdapterValidation(false, null, "", List.of("Adapter JSON exceeds the configured file limit"), List.of());
        UpdateSiteDeclarativeAdapterDocument document;
        try {
            JsonNode root = mapper.readTree(content);
            rejectUnknownFields(root, Set.of("schemaVersion", "adapter", "annotations"), "document", errors);
            rejectUnknownFields(root == null ? null : root.path("adapter"), Set.of("id", "label",
                    "extensionPointTokens", "elementNames", "semanticKind", "neutralTarget", "purpose",
                    "idAttributes", "implementationAttributes", "confidence", "enabled"), "adapter", errors);
            document = mapper.treeToValue(root, UpdateSiteDeclarativeAdapterDocument.class);
        } catch(Exception exception) {
            return new UpdateSiteDeclarativeAdapterValidation(false, null, "", List.of("Invalid adapter JSON: " + safeMessage(exception)), List.of());
        }
        validate(document, errors, warnings);
        return new UpdateSiteDeclarativeAdapterValidation(errors.isEmpty(), document,
                UpdateSiteHashing.sha256(content), errors, warnings);
    }

    /** Descripción: Ejecuta la operación «importAdapter» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: content: valor de entrada que debe procesarse; overwrite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteDeclarativeAdapterImportResult importAdapter(byte[] content, boolean overwrite) {
        UpdateSiteDeclarativeAdapterValidation validation = preview(content);
        if(!validation.valid()) return new UpdateSiteDeclarativeAdapterImportResult(validation, false, false, "");
        UpdateSiteDeclarativeAdapterDocument normalized = withVersion(validation.document(), version(validation.document()));
        byte[] storedContent = serialize(normalized);
        UpdateSiteDeclarativeAdapterValidation storedValidation = preview(storedContent);
        UpdateSiteExtensionAdapterDescriptor adapter = normalized.adapter();
        if(catalog.builtIn(adapter.id())) {
            return new UpdateSiteDeclarativeAdapterImportResult(storedValidation, false, false, "");
        }
        Path target = adapterPath(adapter.id());
        boolean exists = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
        if(exists && !overwrite) throw new IllegalArgumentException("A declarative adapter with this ID already exists");
        if(!exists && persistedCount() >= properties.getMaxDeclarativeAdapters()) throw new IllegalArgumentException("The declarative adapter limit has been reached");
        if(exists) archiveCurrent(adapter.id(), read(target));
        ensureVersionAvailable(adapter.id(), version(normalized), storedContent);
        write(target, storedContent);
        archiveVersion(adapter.id(), version(normalized), storedContent);
        catalog.register(adapter);
        return new UpdateSiteDeclarativeAdapterImportResult(storedValidation, true, exists, target.getFileName().toString());
    }

    /** Descripción: Obtiene la información correspondiente a «listPersisted» dentro de UpdateSiteDeclarativeAdapterService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteDeclarativeAdapterEntry> listPersisted() {
        Path directory = adapterDirectory();
        if(!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try(var stream = Files.list(directory)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".hydra-adapter.json"))
                    .sorted().flatMap(path -> {
                        try {
                            byte[] content = com.example.editor.io.BoundedIo.readInternal(path);
                            UpdateSiteDeclarativeAdapterValidation validation = preview(content);
                            if(!validation.valid()) return java.util.stream.Stream.empty();
                            String adapterVersion = version(validation.document());
                            List<String> versions = versions(validation.document().adapter().id()).stream()
                                    .map(UpdateSiteDeclarativeAdapterVersionEntry::version).toList();
                            return java.util.stream.Stream.of(new UpdateSiteDeclarativeAdapterEntry(validation.document(),
                                    path.getFileName().toString(), validation.fingerprint(), adapterVersion, versions));
                        } catch(IOException | RuntimeException exception) {
                            return java.util.stream.Stream.empty();
                        }
                    }).toList();
        } catch(IOException exception) {
            throw new IllegalStateException("Could not list declarative adapters", exception);
        }
    }

    /** Descripción: Ejecuta la operación «exportAdapter» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] exportAdapter(String adapterId) {
        if(catalog.builtIn(adapterId)) throw new IllegalArgumentException("Built-in adapters do not have an exportable user document");
        try { return com.example.editor.io.BoundedIo.readInternal(adapterPath(adapterId)); }
        catch(IOException exception) { throw new IllegalArgumentException("Declarative adapter was not found", exception); }
    }

    /** Descripción: Ejecuta la operación «exportVersion» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo; adapterVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] exportVersion(String adapterId, String adapterVersion) {
        try { return com.example.editor.io.BoundedIo.readInternal(versionPath(adapterId, adapterVersion)); }
        catch(IOException exception) { throw new IllegalArgumentException("Declarative adapter version was not found", exception); }
    }

    /** Descripción: Ejecuta la operación «versions» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteDeclarativeAdapterVersionEntry> versions(String adapterId) {
        String currentVersion = "";
        try {
            UpdateSiteDeclarativeAdapterValidation current = preview(exportAdapter(adapterId));
            if(current.valid()) currentVersion = version(current.document());
        } catch(RuntimeException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteDeclarativeAdapterService.java",ignored);}
        Path directory = versionDirectory(adapterId);
        if(!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        String selectedCurrent = currentVersion;
        try(var stream = Files.list(directory)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".hydra-adapter.json"))
                    .flatMap(path -> {
                        try {
                            byte[] bytes = com.example.editor.io.BoundedIo.readInternal(path);
                            UpdateSiteDeclarativeAdapterValidation validation = preview(bytes);
                            if(!validation.valid()) return java.util.stream.Stream.empty();
                            String itemVersion = version(validation.document());
                            return java.util.stream.Stream.of(new UpdateSiteDeclarativeAdapterVersionEntry(adapterId,
                                    itemVersion, path.getFileName().toString(), validation.fingerprint(), itemVersion.equals(selectedCurrent)));
                        } catch(IOException | RuntimeException exception) {
                            return java.util.stream.Stream.empty();
                        }
                    }).sorted((left, right) -> compareSemanticVersions(right.version(), left.version())).toList();
        } catch(IOException exception) {
            throw new IllegalStateException("Could not list declarative adapter versions", exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteDeclarativeAdapterService mediante la operación «setEnabled». Entradas: adapterId: identificador que delimita el recurso objetivo; enabled: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteDeclarativeAdapterEntry setEnabled(String adapterId, boolean enabled) {
        byte[] current = exportAdapter(adapterId);
        UpdateSiteDeclarativeAdapterValidation validation = preview(current);
        if(!validation.valid()) throw new IllegalArgumentException("Stored declarative adapter is invalid");
        UpdateSiteDeclarativeAdapterDocument document = validation.document();
        UpdateSiteExtensionAdapterDescriptor source = document.adapter();
        UpdateSiteExtensionAdapterDescriptor updated = new UpdateSiteExtensionAdapterDescriptor(source.id(), source.label(),
                source.extensionPointTokens(), source.elementNames(), source.semanticKind(), source.neutralTarget(),
                source.purpose(), source.idAttributes(), source.implementationAttributes(), source.confidence(), enabled);
        UpdateSiteDeclarativeAdapterDocument replacement = new UpdateSiteDeclarativeAdapterDocument(document.schemaVersion(),
                updated, document.annotations());
        byte[] content = serialize(replacement);
        Path target = adapterPath(adapterId);
        write(target, content);
        catalog.register(updated);
        UpdateSiteDeclarativeAdapterValidation checked = preview(content);
        return new UpdateSiteDeclarativeAdapterEntry(replacement, target.getFileName().toString(), checked.fingerprint(),
                version(replacement), versions(adapterId).stream().map(UpdateSiteDeclarativeAdapterVersionEntry::version).toList());
    }

    /** Descripción: Ejecuta la operación «testFixture» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: content: valor de entrada que debe procesarse; fixture: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterFixtureResult testFixture(byte[] content, UpdateSiteAdapterFixture fixture) {
        UpdateSiteDeclarativeAdapterValidation validation = preview(content);
        if(!validation.valid()) return new UpdateSiteAdapterFixtureResult(false, false, "", "", "", List.of(), validation.errors());
        UpdateSiteAdapterFixture safe = fixture == null ? new UpdateSiteAdapterFixture("", "", Map.of()) : fixture;
        UpdateSiteExtensionAdapterDescriptor adapter = validation.document().adapter();
        List<String> explanations = new ArrayList<>();
        boolean extensionMatches = adapter.extensionPointTokens().isEmpty() || adapter.extensionPointTokens().stream()
                .anyMatch(token -> safe.extensionPoint().toLowerCase(Locale.ROOT).contains(token));
        explanations.add(extensionMatches ? "The extension-point token matches." : "No extension-point token matches.");
        boolean elementMatches = adapter.elementNames().isEmpty() || adapter.elementNames().contains(safe.elementName().toLowerCase(Locale.ROOT));
        explanations.add(elementMatches ? "The XML element name matches." : "The XML element name does not match.");
        boolean matched = adapter.enabled() && extensionMatches && elementMatches;
        if(!adapter.enabled()) explanations.add("The adapter is disabled, so a syntactic match is not active.");
        String resolvedId = firstAttribute(safe.attributes(), adapter.idAttributes());
        String implementation = firstAttribute(safe.attributes(), adapter.implementationAttributes());
        if(matched && resolvedId.isBlank()) explanations.add("No configured ID attribute was present in the fixture.");
        if(matched && implementation.isBlank()) explanations.add("No configured implementation attribute was present in the fixture.");
        return new UpdateSiteAdapterFixtureResult(true, matched, resolvedId, implementation,
                adapter.neutralTarget(), explanations, List.of());
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String adapterId) {
        if(catalog.builtIn(adapterId)) throw new IllegalArgumentException("Built-in adapters cannot be deleted");
        try {
            Files.deleteIfExists(adapterPath(adapterId));
            deleteRecursively(versionDirectory(adapterId));
            catalog.unregister(adapterId);
        } catch(IOException exception) {
            throw new IllegalStateException("Could not delete the declarative adapter", exception);
        }
    }

    /** Descripción: Ejecuta la operación «firstAttribute» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: attributes: dato de entrada necesario para la operación; candidates: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String firstAttribute(Map<String, String> attributes, List<String> candidates) {
        Map<String, String> normalized = new LinkedHashMap<>();
        attributes.forEach((key, value) -> normalized.put(key == null ? "" : key.toLowerCase(Locale.ROOT), value == null ? "" : value));
        for(String candidate : candidates) {
            String value = normalized.getOrDefault(candidate.toLowerCase(Locale.ROOT), "").trim();
            if(!value.isBlank()) return value;
        }
        return "";
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación; errors: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validate(UpdateSiteDeclarativeAdapterDocument document, List<String> errors, List<String> warnings) {
        if(document == null) { errors.add("Adapter document is required"); return; }
        if(!"1.0".equals(document.schemaVersion())) errors.add("schemaVersion must be 1.0");
        String adapterVersion = document.annotations().getOrDefault("version", "").trim();
        if(adapterVersion.isBlank()) warnings.add("annotations.version is not set; HYDRA will persist this adapter as " + DEFAULT_VERSION);
        else if(!validVersion(adapterVersion)) errors.add("annotations.version must use semantic versioning, for example 1.2.0");
        UpdateSiteExtensionAdapterDescriptor adapter = document.adapter();
        if(adapter == null) { errors.add("adapter is required"); return; }
        if(!adapter.id().matches("[a-z][a-z0-9.-]{1,63}:[a-z][a-z0-9._-]{1,63}")) errors.add("adapter.id must be namespaced, for example org.example:palette");
        if(adapter.label().isBlank()) errors.add("adapter.label is required");
        if(adapter.purpose().isBlank()) errors.add("adapter.purpose is required");
        if(adapter.neutralTarget().isBlank()) errors.add("adapter.neutralTarget is required");
        if(adapter.extensionPointTokens().size() > properties.getMaxAdapterTokens()) errors.add("Too many extensionPointTokens");
        if(adapter.elementNames().size() > properties.getMaxAdapterElements()) errors.add("Too many elementNames");
        if(adapter.extensionPointTokens().isEmpty() && adapter.elementNames().isEmpty()) errors.add("At least one extension-point token or element name is required");
        List<String> values = new ArrayList<>();
        values.addAll(adapter.extensionPointTokens()); values.addAll(adapter.elementNames());
        values.addAll(adapter.idAttributes()); values.addAll(adapter.implementationAttributes());
        if(values.stream().anyMatch(value -> value.length() > 120)) errors.add("Adapter tokens and attribute names must not exceed 120 characters");
        if(values.stream().anyMatch(this::unsafeToken)) errors.add("Adapter tokens may only contain XML identifier and namespace characters");
        if(!adapter.enabled()) warnings.add("The adapter is valid but disabled and will not match extension declarations");
    }

    /** Descripción: Ejecuta la operación «rejectUnknownFields» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: node: dato de entrada necesario para la operación; allowed: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; errors: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectUnknownFields(JsonNode node, Set<String> allowed, String context, List<String> errors) {
        if(node == null || !node.isObject()) return;
        node.properties().forEach(entry -> {
            if(!allowed.contains(entry.getKey())) errors.add("Unknown " + context + " field: " + entry.getKey());
        });
    }

    /** Descripción: Ejecuta la operación «unsafeToken» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean unsafeToken(String value) {
        String token = value == null ? "" : value.trim();
        return token.isBlank() || !token.matches("[A-Za-z0-9_.:-]+");
    }

    /** Descripción: Obtiene la información correspondiente a «loadPersisted» dentro de UpdateSiteDeclarativeAdapterService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void loadPersisted() {
        Path directory = adapterDirectory();
        if(!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try(var stream = Files.list(directory)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".hydra-adapter.json"))
                    .sorted().forEach(path -> {
                        try {
                            UpdateSiteDeclarativeAdapterValidation validation = preview(com.example.editor.io.BoundedIo.readInternal(path));
                            if(validation.valid()) catalog.register(validation.document().adapter());
                        } catch(IOException | RuntimeException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteDeclarativeAdapterService.java",ignored);}
                    });
        } catch(IOException | RuntimeException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteDeclarativeAdapterService.java",ignored);}
    }

    /** Descripción: Ejecuta la operación «archiveCurrent» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo; current: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void archiveCurrent(String adapterId, byte[] current) {
        UpdateSiteDeclarativeAdapterValidation validation = preview(current);
        if(!validation.valid()) return;
        Path target = versionPath(adapterId, version(validation.document()));
        if(!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) write(target, current);
    }

    /** Descripción: Valida las condiciones necesarias para «ensureVersionAvailable» y rechaza entradas no válidas. Entradas: adapterId: identificador que delimita el recurso objetivo; adapterVersion: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureVersionAvailable(String adapterId, String adapterVersion, byte[] content) {
        Path target = versionPath(adapterId, adapterVersion);
        if(!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
        byte[] existing = read(target);
        if(!UpdateSiteHashing.sha256(existing).equals(UpdateSiteHashing.sha256(content))) {
            throw new IllegalArgumentException("Adapter version " + adapterVersion + " already exists with different content");
        }
    }

    /** Descripción: Ejecuta la operación «archiveVersion» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo; adapterVersion: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void archiveVersion(String adapterId, String adapterVersion, byte[] content) {
        Path target = versionPath(adapterId, adapterVersion);
        if(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
        write(target, content);
    }

    /** Descripción: Ejecuta la operación «withVersion» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: document: dato de entrada necesario para la operación; adapterVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteDeclarativeAdapterDocument withVersion(UpdateSiteDeclarativeAdapterDocument document, String adapterVersion) {
        Map<String, String> annotations = new LinkedHashMap<>(document.annotations());
        annotations.put("version", adapterVersion);
        return new UpdateSiteDeclarativeAdapterDocument(document.schemaVersion(), document.adapter(), annotations);
    }

    /** Descripción: Ejecuta la operación «version» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String version(UpdateSiteDeclarativeAdapterDocument document) {
        if(document == null) return DEFAULT_VERSION;
        String value = document.annotations().getOrDefault("version", DEFAULT_VERSION);
        return value == null || value.isBlank() ? DEFAULT_VERSION : value.trim();
    }

    /** Descripción: Ejecuta la operación «validVersion» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean validVersion(String value) {
        return value != null && value.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?");
    }

    /** Descripción: Ejecuta la operación «compareSemanticVersions» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int compareSemanticVersions(String left, String right) {
        String[] leftNumbers = left.split("[-+]", 2)[0].split("\\.");
        String[] rightNumbers = right.split("[-+]", 2)[0].split("\\.");
        for(int index = 0; index < 3; index++) {
            int compared = new BigInteger(leftNumbers[index]).compareTo(new BigInteger(rightNumbers[index]));
            if(compared != 0) return compared;
        }
        String leftPreRelease = preRelease(left);
        String rightPreRelease = preRelease(right);
        if(leftPreRelease.isBlank() != rightPreRelease.isBlank()) return leftPreRelease.isBlank() ? 1 : -1;
        if(!leftPreRelease.isBlank()) {
            String[] leftIdentifiers = leftPreRelease.split("\\.");
            String[] rightIdentifiers = rightPreRelease.split("\\.");
            for(int index = 0; index < Math.min(leftIdentifiers.length, rightIdentifiers.length); index++) {
                int compared = comparePreReleaseIdentifier(leftIdentifiers[index], rightIdentifiers[index]);
                if(compared != 0) return compared;
            }
            int length = Integer.compare(leftIdentifiers.length, rightIdentifiers.length);
            if(length != 0) return length;
        }
        return left.compareTo(right);
    }

    /** Descripción: Ejecuta la operación «comparePreReleaseIdentifier» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int comparePreReleaseIdentifier(String left, String right) {
        boolean leftNumeric = left.matches("[0-9]+");
        boolean rightNumeric = right.matches("[0-9]+");
        if(leftNumeric && rightNumeric) return new BigInteger(left).compareTo(new BigInteger(right));
        if(leftNumeric != rightNumeric) return leftNumeric ? -1 : 1;
        return left.compareTo(right);
    }

    /** Descripción: Ejecuta la operación «preRelease» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String preRelease(String version) {
        int dash = version.indexOf('-');
        if(dash < 0) return "";
        int build = version.indexOf('+', dash + 1);
        return version.substring(dash + 1, build < 0 ? version.length() : build);
    }

    /** Descripción: Ejecuta la operación «serialize» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] serialize(UpdateSiteDeclarativeAdapterDocument document) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document); }
        catch(RuntimeException exception) { throw new IllegalStateException("Could not serialize declarative adapter", exception); }
    }

    /** Descripción: Ejecuta la operación «persistedCount» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int persistedCount() {
        Path directory = adapterDirectory();
        if(!Files.isDirectory(directory)) return 0;
        try(var stream = Files.list(directory)) {
            return (int) stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".hydra-adapter.json")).count();
        } catch(IOException exception) {
            throw new IllegalStateException("Could not count declarative adapters", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de UpdateSiteDeclarativeAdapterService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] read(Path path) {
        try { return com.example.editor.io.BoundedIo.readInternal(path); }
        catch(IOException exception) { throw new IllegalStateException("Could not read the declarative adapter", exception); }
    }

    /** Descripción: Actualiza el estado gestionado por UpdateSiteDeclarativeAdapterService mediante la operación «write». Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void write(Path target, byte[] content) {
        try {
            Files.createDirectories(target.getParent());
            if(Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) throw new IOException("Symbolic links are not allowed for adapter documents");
            AtomicFiles.writeReplacing(target,content);
        } catch(IOException exception) {
            throw new IllegalStateException("Could not persist the declarative adapter", exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteRecursively» de forma controlada sin materializar el árbol completo. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteRecursively(Path root) throws IOException {
        if(root == null || !Files.exists(root)) return;
        Files.walkFileTree(root,new java.nio.file.SimpleFileVisitor<>(){
            /** Descripción: Elimina cada fichero visitado durante la limpieza incremental. Entradas: file: fichero visitado; attrs: metadatos del fichero. Salida: continúa el recorrido. */ @Override public java.nio.file.FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{Files.deleteIfExists(file);return java.nio.file.FileVisitResult.CONTINUE;}
            /** Descripción: Elimina cada directorio una vez procesados sus hijos. Entradas: dir: directorio visitado; exc: posible error del recorrido. Salida: continúa el recorrido o propaga el error recibido. */ @Override public java.nio.file.FileVisitResult postVisitDirectory(Path dir,IOException exc)throws IOException{if(exc!=null)throw exc;Files.deleteIfExists(dir);return java.nio.file.FileVisitResult.CONTINUE;}
        });
    }

    /** Descripción: Ejecuta la operación «adapterDirectory» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path adapterDirectory() { return properties.storageRootPath().resolve("extension-adapters").normalize(); }
    /** Descripción: Ejecuta la operación «adapterPath» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path adapterPath(String adapterId) {
        String safe = safeAdapterId(adapterId);
        Path directory = adapterDirectory();
        Path path = directory.resolve(safe + ".hydra-adapter.json").normalize();
        if(!path.startsWith(directory)) throw new IllegalArgumentException("Unsafe adapter ID");
        return path;
    }
    /** Descripción: Ejecuta la operación «versionDirectory» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path versionDirectory(String adapterId) {
        Path root = adapterDirectory().resolve("versions").normalize();
        Path path = root.resolve(safeAdapterId(adapterId)).normalize();
        if(!path.startsWith(root)) throw new IllegalArgumentException("Unsafe adapter ID");
        return path;
    }
    /** Descripción: Ejecuta la operación «versionPath» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo; adapterVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path versionPath(String adapterId, String adapterVersion) {
        if(!validVersion(adapterVersion)) throw new IllegalArgumentException("Invalid adapter version");
        Path directory = versionDirectory(adapterId);
        Path path = directory.resolve(adapterVersion + ".hydra-adapter.json").normalize();
        if(!path.startsWith(directory)) throw new IllegalArgumentException("Unsafe adapter version");
        return path;
    }
    /** Descripción: Ejecuta la operación «safeAdapterId» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: adapterId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String safeAdapterId(String adapterId) {
        String safe = adapterId == null ? "" : adapterId.toLowerCase(Locale.ROOT).replace(':', '_').replaceAll("[^a-z0-9._-]", "-");
        if(safe.isBlank()) throw new IllegalArgumentException("Unsafe adapter ID");
        return safe;
    }
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Unknown JSON error" : exception.getMessage();
    }
}


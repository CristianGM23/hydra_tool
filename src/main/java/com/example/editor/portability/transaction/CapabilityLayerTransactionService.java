package com.example.editor.portability.transaction;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.persistence.CanonicalDocumentMutation;
import com.example.editor.persistence.CanonicalDocumentMutationKind;
import com.example.editor.persistence.CanonicalDocumentMutationObserver;
import com.example.editor.scope.ResourceScope;
import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.storage.ProjectArtifactClassifier;
import com.example.editor.project.storage.ProjectWorkspaceContributionRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Applies a capability package as one ETag-guarded, ordered, rollbackable transaction. */
@Service
public class CapabilityLayerTransactionService {
    private static final List<ArtifactKind> DEFAULT_ORDER = List.of(ArtifactKind.METAMODEL,
            ArtifactKind.EDITOR_DEFINITION, ArtifactKind.REPRESENTATION_DEFINITION,
            ArtifactKind.PALETTE_DEFINITION, ArtifactKind.FORM_DEFINITION,
            ArtifactKind.EXPRESSION_MODEL, ArtifactKind.VALIDATION_MODEL, ArtifactKind.ACTION_MODEL,
            ArtifactKind.OPERATION_BEHAVIOR_MODEL, ArtifactKind.EVENT_DEFINITION_MODEL,
            ArtifactKind.EVENT_BINDING_MODEL, ArtifactKind.EVENT_FLOW_MODEL,
            ArtifactKind.GAMIFICATION_DEFINITION_MODEL, ArtifactKind.GAMIFICATION_BINDING_MODEL,
            ArtifactKind.INTEGRATION_PROFILE);
    private static final Set<ArtifactKind> SUPPORTED = Set.copyOf(DEFAULT_ORDER);
    /** Espacio de nombres con el que las capas portables aparecen en el registro de actividad. */
    static final String MUTATION_NAMESPACE = "portability.capability-layer";
    private final CapabilityLayerTransactionProperties properties;
    private final CapabilityPackageChecker checker;
    private final ProjectWorkspaceContributionRegistry registry;
    private final ProjectArtifactClassifier classifier;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;
    private final List<CanonicalDocumentMutationObserver> mutationObservers;

    /** Descripción: Inicializa una instancia de CapabilityLayerTransactionService sin observadores de mutación (constructor de compatibilidad). Entradas: properties: configuración que gobierna el comportamiento; checker: dato de entrada necesario para la operación; registry: dato de entrada necesario para la operación; classifier: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityLayerTransactionService(CapabilityLayerTransactionProperties properties,
            CapabilityPackageChecker checker, ProjectWorkspaceContributionRegistry registry,
            ProjectArtifactClassifier classifier, ObjectMapper mapper) {
        this(properties, checker, registry, classifier, mapper, List.of());
    }

    /** Descripción: Inicializa una instancia de CapabilityLayerTransactionService con sus dependencias y los observadores transversales (auditoría) de mutación canónica. Entradas: properties: configuración que gobierna el comportamiento; checker: dato de entrada necesario para la operación; registry: dato de entrada necesario para la operación; classifier: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; mutationObservers: observadores que reciben cada capa escrita o restaurada (puede ser nulo). Salida: deja la instancia inicializada y lista para su uso. */ @Autowired public CapabilityLayerTransactionService(CapabilityLayerTransactionProperties properties,
            CapabilityPackageChecker checker, ProjectWorkspaceContributionRegistry registry,
            ProjectArtifactClassifier classifier, ObjectMapper mapper,
            List<CanonicalDocumentMutationObserver> mutationObservers) {
        this.properties = properties; this.checker = checker; this.registry = registry;
        this.classifier = classifier; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.mutationObservers = mutationObservers == null ? List.of() : List.copyOf(mutationObservers);
    }

    /** Descripción: Notifica a los observadores transversales (auditoría) que una capa se creó, actualizó o eliminó fuera de un store canónico. Entradas: documentPath: ruta canónica de la capa; kind: tipo de mutación; before: bytes JSON previos o nulo; after: bytes JSON resultantes o nulo. Salida: no devuelve valor; un fallo del observador nunca altera la transacción ya realizada. */ private void notifyMutation(String documentPath, CanonicalDocumentMutationKind kind, byte[] before, byte[] after) {
        if(mutationObservers.isEmpty()) return;
        CanonicalDocumentMutation mutation = new CanonicalDocumentMutation(MUTATION_NAMESPACE, ResourceScope.local(),
                documentPath, kind, snapshot(before), snapshot(after));
        for(CanonicalDocumentMutationObserver observer : mutationObservers) {
            try { observer.onMutation(mutation); }
            catch(RuntimeException ignored) { com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/portability/transaction/CapabilityLayerTransactionService.java",ignored); }
        }
    }

    /** Descripción: Convierte los bytes JSON de una capa en un objeto neutral apto para el diff de auditoría. Entradas: content: bytes de la capa o nulo. Salida: objeto JSON genérico, o nulo si no hay contenido o no es JSON legible. */ private Object snapshot(byte[] content) {
        if(content == null || content.length == 0) return null;
        try { return mapper.readValue(content, Object.class); }
        catch(RuntimeException exception) { return null; }
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de Prepared. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerTransactionPreview preview(CapabilityLayerTransactionRequest request) {
        require(request);
        HydraCapabilityPackage source = request.capabilityPackage();
        CapabilityPackageCheckReport report = checker.check(source);
        List<String> diagnostics = new ArrayList<>();
        if(!report.valid()) diagnostics.add("Capability package contains blocking diagnostics");
        if(source.manifest() != null && !source.manifest().metamodelId().isBlank()
                && !source.manifest().metamodelId().equals(request.targetMetamodelId())) {
            diagnostics.add("Package metamodelId does not match targetMetamodelId");
        }
        request.expectedEtags().keySet().stream().filter(path -> !source.canonicalDocuments().containsKey(path))
                .sorted().forEach(path -> diagnostics.add("Expected ETag references a document not present in the package: " + path));
        if(source.canonicalDocuments().size() > properties.getMaximumDocuments()) diagnostics.add("Capability package exceeds the transaction document limit");
        Map<ArtifactKind, Integer> order = order(request.layerOrder());
        Map<ArtifactKind, Path> targets = registry.snapshot(ProjectLayout.standard()).targets();
        List<Prepared> prepared = new ArrayList<>();
        Map<Path, String> targetOwners = new LinkedHashMap<>();
        long total = 0L;
        for(Map.Entry<String, JsonNode> entry : source.canonicalDocuments().entrySet()) {
            String documentPath = safeDocumentPath(entry.getKey());
            ArtifactKind kind = classifier.classify(documentPath, ProjectLayout.standard());
            List<String> itemDiagnostics = new ArrayList<>();
            Path root = targets.get(kind);
            if(!SUPPORTED.contains(kind)) itemDiagnostics.add("Artifact kind is not supported by portable transactions: " + kind);
            if(root == null) itemDiagnostics.add("No workspace repository is registered for " + kind);
            byte[] content = write(entry.getValue()); total += content.length;
            String declaredChecksum = source.manifest() == null ? "" : source.manifest().checksums().getOrDefault(documentPath, "");
            if(!declaredChecksum.isBlank() && !declaredChecksum.equalsIgnoreCase(sha256(content))) {
                itemDiagnostics.add("Canonical document checksum does not match the package manifest");
            }
            Path normalizedRoot = root == null ? null : root.toAbsolutePath().normalize();
            Path target = normalizedRoot == null ? properties.storageRootPath().resolve("unresolved").resolve(fileName(documentPath)).toAbsolutePath().normalize()
                    : normalizedRoot.resolve(fileName(documentPath)).normalize();
            if(normalizedRoot != null && !target.startsWith(normalizedRoot)) itemDiagnostics.add("Resolved target escapes its workspace repository");
            String previousOwner = targetOwners.putIfAbsent(target, documentPath);
            if(previousOwner != null) itemDiagnostics.add("Target path collides with " + previousOwner);
            String current = Files.isRegularFile(target) ? etag(read(target)) : "";
            String expected = request.expectedEtags().getOrDefault(documentPath, "").trim();
            if(!current.isBlank() && expected.isBlank()) itemDiagnostics.add("Expected ETag is required before replacing an existing layer");
            if(!current.isBlank() && !expected.isBlank() && !"*".equals(expected) && !current.equals(expected)) itemDiagnostics.add("Layer changed since it was loaded");
            if(current.isBlank() && !expected.isBlank() && !"*".equals(expected)) itemDiagnostics.add("Expected ETag was supplied but the target layer does not exist");
            prepared.add(new Prepared(documentPath, kind, target, content, current, expected,
                    order.getOrDefault(kind, 10_000), itemDiagnostics));
        }
        if(total > properties.getMaximumBytes()) diagnostics.add("Capability package exceeds the transaction byte limit");
        prepared.sort(Comparator.comparingInt(Prepared::order).thenComparing(Prepared::documentPath));
        List<CapabilityLayerTransactionItem> items = new ArrayList<>(); int index = 0;
        for(Prepared item : prepared) {
            boolean applicable = item.diagnostics().isEmpty();
            items.add(new CapabilityLayerTransactionItem(item.documentPath(), item.kind(), item.target().toString(),
                    item.currentEtag().isBlank() ? "CREATE" : "REPLACE", index++, item.currentEtag(),
                    item.expectedEtag(), etag(item.content()), applicable, item.diagnostics()));
        }
        boolean applicable = diagnostics.isEmpty() && report.valid() && items.stream().allMatch(CapabilityLayerTransactionItem::applicable);
        String previewId = "preview-" + etag((request.targetMetamodelId() + "\n" + items.toString()).getBytes()).replace("\"", "").substring(0, 24);
        return new CapabilityLayerTransactionPreview(previewId, request.targetMetamodelId(), items, total, applicable, diagnostics);
    }

    /** Descripción: Actualiza el estado gestionado por Prepared mediante la operación «apply». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerTransactionResult apply(CapabilityLayerTransactionRequest request) {
        CapabilityLayerTransactionPreview preview = preview(request);
        if(!request.persist()) return new CapabilityLayerTransactionResult(preview, "", false, false, Map.of(), List.of("Preview only; no layer was changed"));
        if(!preview.applicable()) throw new IllegalArgumentException("Portable transaction preconditions failed");
        String transactionId = "cap-tx-" + UUID.randomUUID();
        Path staging = properties.storageRootPath().resolve("staging").resolve(transactionId).normalize();
        List<CapabilityLayerTransactionJournalItem> journalItems = new ArrayList<>();
        Map<String, String> appliedEtags = new LinkedHashMap<>();
        try {
            Files.createDirectories(staging);
            for(CapabilityLayerTransactionItem item : preview.items()) {
                Path target = Path.of(item.targetPath()).toAbsolutePath().normalize();
                byte[] proposed = documentBytes(request.capabilityPackage(), item.documentPath());
                Path staged = staging.resolve(String.format("%04d-%s", item.order(), fileName(item.documentPath()))).normalize();
                if(!staged.startsWith(staging)) throw new IllegalArgumentException("Unsafe staging path");
                Files.write(staged, proposed);
                boolean existed = Files.isRegularFile(target);
                byte[] original = existed ? read(target) : new byte[0];
                verifyCurrent(target, item.currentEtag());
                String appliedEtag = etag(proposed);
                appliedEtags.put(item.documentPath(), appliedEtag);
                journalItems.add(new CapabilityLayerTransactionJournalItem(item.documentPath(), target.toString(),
                        existed, Base64.getEncoder().encodeToString(original), appliedEtag, false));
            }
            CapabilityLayerTransactionJournal journal = new CapabilityLayerTransactionJournal(transactionId,
                    request.targetMetamodelId(), Instant.now(), journalItems, request.decisions());
            writeJournal(journal);
            for(CapabilityLayerTransactionItem item : preview.items()) {
                Path target = Path.of(item.targetPath()).toAbsolutePath().normalize();
                Path staged = staging.resolve(String.format("%04d-%s", item.order(), fileName(item.documentPath()))).normalize();
                verifyCurrent(target, item.currentEtag());
                Files.createDirectories(target.getParent());
                move(staged, target);
                String actual = etag(read(target));
                if(!actual.equals(appliedEtags.get(item.documentPath()))) {
                    throw new IllegalStateException("Applied layer differs from its staged checksum: " + item.documentPath());
                }
            }
            for(CapabilityLayerTransactionJournalItem item : journalItems) {
                byte[] before = item.existed() ? Base64.getDecoder().decode(item.originalBase64()) : null;
                notifyMutation(item.documentPath(), item.existed() ? CanonicalDocumentMutationKind.UPDATE : CanonicalDocumentMutationKind.CREATE,
                        before, documentBytes(request.capabilityPackage(), item.documentPath()));
            }
            deleteRecursively(staging);
            return new CapabilityLayerTransactionResult(preview, transactionId, true, true, appliedEtags, List.of());
        } catch(RuntimeException | IOException exception) {
            List<String> rollbackFailures = rollbackImmediate(journalItems);
            deleteRecursively(staging);
            String suffix = rollbackFailures.isEmpty() ? "" : "; manual recovery is required for: " + String.join(", ", rollbackFailures);
            throw new IllegalStateException("Portable capability transaction failed and recovery was attempted" + suffix, exception);
        }
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de Prepared. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerRollbackResult rollback(CapabilityLayerRollbackRequest request) {
        if(request == null || request.transactionId().isBlank()) throw new IllegalArgumentException("transactionId is required");
        CapabilityLayerTransactionJournal journal = readJournal(request.transactionId());
        Set<String> selected = request.documentPaths().isEmpty() ? journal.items().stream().map(CapabilityLayerTransactionJournalItem::documentPath).collect(java.util.stream.Collectors.toSet())
                : Set.copyOf(request.documentPaths());
        List<String> restored = new ArrayList<>(); List<String> skipped = new ArrayList<>(); List<String> diagnostics = new ArrayList<>();
        List<CapabilityLayerTransactionJournalItem> updated = new ArrayList<>();
        for(CapabilityLayerTransactionJournalItem item : journal.items()) {
            if(item.rolledBack() || !selected.contains(item.documentPath())) { updated.add(item); continue; }
            Path target = Path.of(item.targetPath());
            String expected = request.expectedCurrentEtags().getOrDefault(item.documentPath(), item.appliedEtag());
            String current = Files.isRegularFile(target) ? etag(read(target)) : "";
            String original = originalEtag(item);
            if(current.equals(original)) {
                restored.add(item.documentPath());
                updated.add(new CapabilityLayerTransactionJournalItem(item.documentPath(), item.targetPath(), item.existed(), item.originalBase64(), item.appliedEtag(), true));
                continue;
            }
            if(!expected.equals(current) || !item.appliedEtag().equals(current)) {
                skipped.add(item.documentPath()); diagnostics.add("Rollback skipped because the layer changed after the transaction: " + item.documentPath());
                updated.add(item); continue;
            }
            try {
                byte[] applied = read(target);
                if(item.existed()) atomicWrite(target, Base64.getDecoder().decode(item.originalBase64()));
                else Files.deleteIfExists(target);
                notifyMutation(item.documentPath(), item.existed() ? CanonicalDocumentMutationKind.UPDATE : CanonicalDocumentMutationKind.DELETE,
                        applied, item.existed() ? Base64.getDecoder().decode(item.originalBase64()) : null);
                restored.add(item.documentPath());
                updated.add(new CapabilityLayerTransactionJournalItem(item.documentPath(), item.targetPath(), item.existed(), item.originalBase64(), item.appliedEtag(), true));
            } catch(IOException exception) {
                skipped.add(item.documentPath()); diagnostics.add("Could not restore " + item.documentPath() + ": " + exception.getMessage()); updated.add(item);
            }
        }
        boolean closed = updated.stream().allMatch(CapabilityLayerTransactionJournalItem::rolledBack);
        if(closed) deleteJournal(journal.transactionId());
        else writeJournal(new CapabilityLayerTransactionJournal(journal.transactionId(), journal.targetMetamodelId(), journal.createdAt(), updated, journal.decisions()));
        return new CapabilityLayerRollbackResult(journal.transactionId(), restored, skipped, closed, diagnostics, journal.decisions());
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(CapabilityLayerTransactionRequest request) {
        if(request == null || request.capabilityPackage() == null) throw new IllegalArgumentException("capabilityPackage is required");
        if(request.targetMetamodelId().isBlank()) throw new IllegalArgumentException("targetMetamodelId is required");
        if(request.capabilityPackage().canonicalDocuments().isEmpty()) throw new IllegalArgumentException("At least one canonical document is required");
    }
    /** Descripción: Ejecuta la operación «order» correspondiente a la responsabilidad de Prepared. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<ArtifactKind, Integer> order(List<String> requested) {
        Map<ArtifactKind, Integer> result = new EnumMap<>(ArtifactKind.class); int index = 0;
        if(requested != null) for(String value : requested) try { result.putIfAbsent(ArtifactKind.valueOf(value), index++); } catch(IllegalArgumentException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/portability/transaction/CapabilityLayerTransactionService.java",ignored);}
        for(ArtifactKind kind : DEFAULT_ORDER) result.putIfAbsent(kind, index++); return result;
    }
    /** Descripción: Ejecuta la operación «documentBytes» correspondiente a la responsabilidad de Prepared. Entradas: source: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] documentBytes(HydraCapabilityPackage source, String path) {
        JsonNode node = source.canonicalDocuments().get(path);
        if(node == null) throw new IllegalArgumentException("Canonical document disappeared from transaction request: " + path);
        return write(node);
    }
    /** Descripción: Actualiza el estado gestionado por Prepared mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value) { try { return writer.writeValueAsBytes(value); } catch(RuntimeException exception) { throw new IllegalArgumentException("Could not serialize canonical layer", exception); } }
    /** Descripción: Obtiene la información correspondiente a «read» dentro de Prepared. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] read(Path path) { try { return com.example.editor.io.BoundedIo.readInternal(path); } catch(IOException exception) { throw new IllegalStateException("Could not read layer " + path, exception); } }
    /** Descripción: Valida las condiciones necesarias para «verifyCurrent» y rechaza entradas no válidas. Entradas: target: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyCurrent(Path target, String expected) {
        String current = Files.isRegularFile(target) ? etag(read(target)) : "";
        if(!current.equals(expected)) throw new IllegalStateException("Layer changed after preview: " + target);
    }
    /** Descripción: Ejecuta la operación «rollbackImmediate» correspondiente a la responsabilidad de Prepared. Entradas: items: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> rollbackImmediate(List<CapabilityLayerTransactionJournalItem> items) {
        List<String> failures = new ArrayList<>();
        for(int index = items.size() - 1; index >= 0; index--) {
            CapabilityLayerTransactionJournalItem item = items.get(index); Path target = Path.of(item.targetPath());
            try {
                String current = Files.isRegularFile(target) ? etag(read(target)) : "";
                if(current.equals(originalEtag(item))) continue;
                if(!current.equals(item.appliedEtag())) { failures.add(item.documentPath()); continue; }
                if(item.existed()) atomicWrite(target, Base64.getDecoder().decode(item.originalBase64()));
                else Files.deleteIfExists(target);
            } catch(IOException | RuntimeException exception) { failures.add(item.documentPath()); }
        }
        return failures;
    }
    /** Descripción: Ejecuta la operación «originalEtag» correspondiente a la responsabilidad de Prepared. Entradas: item: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String originalEtag(CapabilityLayerTransactionJournalItem item) {
        if(!item.existed()) return "";
        try { return etag(Base64.getDecoder().decode(item.originalBase64())); }
        catch(IllegalArgumentException exception) { throw new IllegalStateException("Capability transaction journal contains invalid original bytes", exception); }
    }
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de Prepared. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent()); Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tx");
        try { Files.write(temporary, content); move(temporary, target); } finally { Files.deleteIfExists(temporary); }
    }
    /** Descripción: Ejecuta la operación «move» correspondiente a la responsabilidad de Prepared. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void move(Path source, Path target) throws IOException {
        // Windows puede rechazar la publicación de forma transitoria (AccessDeniedException) si el antivirus o un indexador tiene abierto el fichero;
        // se reintenta de forma acotada (≈3,5 s). Si el bloqueo persiste la transacción falla de forma segura.
        TransientFileAccessRetry.run(() -> moveOnce(source, target), 12, 20, 500, Thread::sleep);
    }
    /** Descripción: Ejecuta la operación «moveOnce» correspondiente a la responsabilidad de CapabilityLayerTransactionService. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void moveOnce(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException exception) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    /** Descripción: Actualiza el estado gestionado por Prepared mediante la operación «writeJournal». Entradas: journal: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void writeJournal(CapabilityLayerTransactionJournal journal) {
        Path target = journalPath(journal.transactionId()); try { atomicWrite(target, writer.writeValueAsBytes(journal)); }
        catch(IOException | RuntimeException exception) { throw new IllegalStateException("Could not persist capability transaction journal", exception); }
    }
    /** Descripción: Obtiene la información correspondiente a «readJournal» dentro de Prepared. Entradas: transactionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilityLayerTransactionJournal readJournal(String transactionId) {
        try { return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(journalPath(transactionId)), CapabilityLayerTransactionJournal.class); }
        catch(JacksonException | IOException exception) { throw new IllegalArgumentException("Capability transaction was not found", exception); }
    }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteJournal» de forma controlada. Entradas: transactionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteJournal(String transactionId) { try { Files.deleteIfExists(journalPath(transactionId)); } catch(IOException exception) { throw new IllegalStateException("Could not close capability transaction journal", exception); } }
    /** Descripción: Ejecuta la operación «journalPath» correspondiente a la responsabilidad de Prepared. Entradas: transactionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path journalPath(String transactionId) {
        String safe = transactionId == null ? "" : transactionId.replaceAll("[^A-Za-z0-9._-]", "-"); if(safe.isBlank()) throw new IllegalArgumentException("Unsafe transactionId");
        Path root = properties.storageRootPath().resolve("journals").normalize(); Path result = root.resolve(safe + ".json").normalize(); if(!result.startsWith(root)) throw new IllegalArgumentException("Unsafe transactionId"); return result;
    }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteRecursively» de forma incremental y de mejor esfuerzo. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; registra fallos sin materializar el árbol completo. */ private void deleteRecursively(Path root) { if(root == null || !Files.exists(root)) return; try { Files.walkFileTree(root,new SimpleFileVisitor<>() { /** Descripción: Elimina un fichero de transacción visitado. Entradas: file: fichero; attrs: metadatos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){try{Files.deleteIfExists(file);}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/portability/transaction/CapabilityLayerTransactionService.java",ignored);}return FileVisitResult.CONTINUE;} /** Descripción: Elimina un directorio de transacción tras sus hijos. Entradas: dir: directorio; exc: posible fallo. Salida: continúa el recorrido. */ @Override public FileVisitResult postVisitDirectory(Path dir,IOException exc){if(exc!=null)com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/portability/transaction/CapabilityLayerTransactionService.java",exc);try{Files.deleteIfExists(dir);}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/portability/transaction/CapabilityLayerTransactionService.java",ignored);}return FileVisitResult.CONTINUE;} }); } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/portability/transaction/CapabilityLayerTransactionService.java",ignored);} }
    /** Descripción: Ejecuta la operación «safeDocumentPath» correspondiente a la responsabilidad de Prepared. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeDocumentPath(String value) { String path = value == null ? "" : value.replace('\\', '/'); if(path.isBlank() || path.startsWith("/") || path.contains("../") || path.contains("/..")) throw new IllegalArgumentException("Unsafe canonical document path"); return path; }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de Prepared. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileName(String path) { return Path.of(path).getFileName().toString(); }
    /** Descripción: Ejecuta la operación «etag» correspondiente a la responsabilidad de Prepared. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String etag(byte[] content) { return "\"" + sha256(content) + "\""; }
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de Prepared. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); } catch(NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 must be available", exception); } }
    private record Prepared(String documentPath, ArtifactKind kind, Path target, byte[] content,
            String currentEtag, String expectedEtag, int order, List<String> diagnostics) { }
}

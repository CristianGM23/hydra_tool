package com.example.editor.metamodel.deletion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.io.BoundedIo;
import com.example.editor.metamodel.MetamodelIdPolicy;
import com.example.editor.textual.TextualModelProperties;
import com.example.editor.transformation.TransformationProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Removes residual files owned by one metamodel after the lifecycle service has
 * completed the primary ETag-protected deletion. Scans are bounded to configured
 * HYDRA layer directories; user model/project trees are never traversed.
 *
 * <p>Older layers use metamodel-prefixed filenames. Newer layers such as
 * transformations and grammars use independent artifact identifiers, so their
 * ownership is resolved from explicit JSON references instead of filename
 * heuristics. This prevents orphaned transformation/grammar/textual runtime state
 * when a metamodel is removed.</p>
 */
@Service
public final class MetamodelDependentArtifactCleanupService {
    private static final int MAX_SCAN_DEPTH = 3;
    private static final Set<String> LAYER_SUFFIXES = Set.of(
            ".targets.json", ".projections.json", ".expressions.json", ".actions.json",
            ".validations.json", ".event-definitions.json", ".event-bindings.json",
            ".event-flows.json", ".event-flow-runtime.json", ".operation-behaviors.json",
            ".gamification-definitions.json", ".gamification-bindings.json",
            ".gamification-state.json", ".gamification-ledger.json", ".gamification-diagram.json",
            ".editor-definitions.json", ".editor.json", ".integration-profiles.json", ".integration.json",
            ".metamodel-diagram.json", ".event-architecture-diagram.json", ".event-flow-diagram.json");
    private static final Set<String> EXPORT_SUFFIXES = Set.of(
            ".ecore", ".genmodel", ".emf", ".xmi", ".zip", ".java", ".ts", ".go", ".py");
    private static final Set<String> METAMODEL_REFERENCE_FIELDS = Set.of(
            "metamodelId", "sourceMetamodelId", "targetMetamodelId");

    private final List<Path> layerRoots;
    private final Path notationRoot;
    private final Path exportRoot;
    private final TransformationProperties transformations;
    private final GrammarProperties grammars;
    private final TextualModelProperties textualModels;
    private final ObjectMapper objectMapper;
    private final Map<String, Path> managedRoots;

    /** Descripción: Inicializa el servicio de limpieza transversal con las rutas persistentes de las capas que pueden depender de un metamodelo. Entradas: configuraciones de almacenamiento y ObjectMapper. Salida: deja las raíces normalizadas y listas para una limpieza acotada. */
    /** Descripción: Inicializa una instancia de MetamodelDependentArtifactCleanupService con sus dependencias y datos de entrada. Entradas: storage: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; integrations: dato de entrada necesario para la operación; events: dato de entrada necesario para la operación; eventFlows: dato de entrada necesario para la operación; gamification: dato de entrada necesario para la operación; transformations: dato de entrada necesario para la operación; grammars: dato de entrada necesario para la operación; textualModels: dato de entrada necesario para la operación; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDependentArtifactCleanupService(StorageProperties storage,
            EditorDefinitionProperties editors, IntegrationProfileProperties integrations,
            EventModelProperties events, EventFlowProperties eventFlows, GamificationProperties gamification,
            TransformationProperties transformations, GrammarProperties grammars,
            TextualModelProperties textualModels, ObjectMapper objectMapper) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addRoot(roots, storage.metamodels());
        addRoot(roots, storage.validations());
        addRoot(roots, storage.quickFixes());
        addRoot(roots, storage.generators());
        addRoot(roots, editors.getDirectory());
        addRoot(roots, integrations.getDirectory());
        addRoot(roots, events.getDirectory());
        addRoot(roots, eventFlows.getDirectory());
        addRoot(roots, gamification.getDirectory());
        this.layerRoots = List.copyOf(roots);
        this.notationRoot = normalize(storage.notations());
        this.exportRoot = normalize(storage.exports());
        this.transformations = transformations;
        this.grammars = grammars;
        this.textualModels = textualModels;
        this.objectMapper = objectMapper;
        LinkedHashMap<String, Path> managed = new LinkedHashMap<>();
        for (Path root : roots) registerManagedRoot(managed, root);
        registerManagedRoot(managed, notationRoot);
        registerManagedRoot(managed, exportRoot);
        registerManagedRoot(managed, transformations.getDirectory());
        registerManagedRoot(managed, transformations.getTestDirectory());
        registerManagedRoot(managed, transformations.getSynchronizationSessionDirectory());
        registerManagedRoot(managed, transformations.getSynchronizationHistoryDirectory());
        registerManagedRoot(managed, grammars.getDirectory());
        registerManagedRoot(managed, grammars.getCompilationDirectory());
        registerManagedRoot(managed, grammars.getActivationDirectory());
        registerManagedRoot(managed, textualModels.getDefinitionDirectory());
        registerManagedRoot(managed, textualModels.getSessionDirectory());
        registerManagedRoot(managed, textualModels.getSyntaxStyleDirectory());
        this.managedRoots = Map.copyOf(managed);
    }

    /** Descripción: Obtiene los artefactos de capas nuevas que referencian directa o indirectamente al metamodelo sin modificar el almacenamiento. Entradas: identificador del metamodelo. Salida: rutas relativas estables para mostrar el impacto antes del borrado. */
    /** Descripción: Ejecuta la operación «referencedArtifactFiles» correspondiente a la responsabilidad de ReferenceScan. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> referencedArtifactFiles(String metamodelId) {
        MetamodelIdPolicy.require(metamodelId);
        List<String> affected = new ArrayList<>();
        ReferenceScan transformationsScan = findReferencedDocuments(
                transformations.getDirectory(), METAMODEL_REFERENCE_FIELDS, Set.of(metamodelId));
        addReferences(affected, transformations.getDirectory(), transformationsScan.paths());
        Set<String> transformationReferences = union(Set.of(metamodelId), transformationsScan.ids());
        for (Path root : List.of(transformations.getTestDirectory(), transformations.getSynchronizationSessionDirectory(),
                transformations.getSynchronizationHistoryDirectory())) {
            ReferenceScan scan = findReferencedDocuments(root, Set.of("metamodelId", "transformationId"), transformationReferences);
            addReferences(affected, root, scan.paths());
        }

        ReferenceScan grammarScan = findReferencedDocuments(grammars.getDirectory(), METAMODEL_REFERENCE_FIELDS, Set.of(metamodelId));
        addReferences(affected, grammars.getDirectory(), grammarScan.paths());
        Set<String> grammarReferences = union(Set.of(metamodelId), grammarScan.ids());
        for (Path root : List.of(grammars.getCompilationDirectory(), grammars.getActivationDirectory())) {
            ReferenceScan scan = findReferencedDocuments(root, Set.of("metamodelId", "grammarId"), grammarReferences);
            addReferences(affected, root, scan.paths());
        }
        for (Path root : List.of(textualModels.getDefinitionDirectory(), textualModels.getSessionDirectory(), textualModels.getSyntaxStyleDirectory())) {
            ReferenceScan scan = findReferencedDocuments(root, Set.of("metamodelId", "grammarId"), grammarReferences);
            addReferences(affected, root, scan.paths());
        }
        return affected.stream().distinct().sorted().toList();
    }

    /** Comprueba de forma segura si una referencia relativa devuelta por este servicio sigue existiendo. */
    /** Descripción: Ejecuta la operación «managedArtifactExists» correspondiente a la responsabilidad de ReferenceScan. Entradas: reference: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean managedArtifactExists(String reference) {
        Path path = resolveManagedReference(reference);
        return path != null && Files.isRegularFile(path);
    }

    /** Elimina de forma idempotente referencias gestionadas registradas previamente en un journal de lifecycle. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteManagedReferences» de forma controlada. Entradas: references: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> deleteManagedReferences(List<String> references) {
        if (references == null || references.isEmpty()) return List.of();
        List<String> deleted = new ArrayList<>();
        for (String reference : references) {
            Path path = resolveManagedReference(reference);
            if (path == null) continue;
            String rootName = reference.substring(0, reference.indexOf('/'));
            deleteOwnedFile(managedRoots.get(rootName), path, deleted);
        }
        return List.copyOf(deleted);
    }

    /** Descripción: Elimina artefactos dependientes del metamodelo por nombre controlado o referencia JSON explícita. Entradas: identificador del metamodelo y opciones de notaciones/exportaciones. Salida: lista inmutable de archivos eliminados. */
    /** Descripción: Ejecuta la operación «cleanup» correspondiente a la responsabilidad de ReferenceScan. Entradas: metamodelId: identificador que delimita el recurso objetivo; deleteNotations: dato de entrada necesario para la operación; deleteExports: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CleanupResult cleanup(String metamodelId, boolean deleteNotations, boolean deleteExports) {
        MetamodelIdPolicy.require(metamodelId);
        List<String> deleted = new ArrayList<>();
        for (Path root : layerRoots) scanByFilename(root, metamodelId, ScanKind.LAYER, deleted);
        if (deleteNotations) scanByFilename(notationRoot, metamodelId, ScanKind.NOTATION, deleted);
        if (deleteExports) scanByFilename(exportRoot, metamodelId, ScanKind.EXPORT, deleted);

        Set<String> transformationIds = deleteReferencedDocuments(
                transformations.getDirectory(), METAMODEL_REFERENCE_FIELDS, Set.of(metamodelId), deleted);
        deleteReferencedDocuments(transformations.getTestDirectory(),
                Set.of("metamodelId", "transformationId"), union(Set.of(metamodelId), transformationIds), deleted);
        deleteReferencedDocuments(transformations.getSynchronizationSessionDirectory(),
                Set.of("metamodelId", "transformationId"), union(Set.of(metamodelId), transformationIds), deleted);
        deleteReferencedDocuments(transformations.getSynchronizationHistoryDirectory(),
                Set.of("metamodelId", "transformationId"), union(Set.of(metamodelId), transformationIds), deleted);

        Set<String> grammarIds = deleteReferencedDocuments(
                grammars.getDirectory(), METAMODEL_REFERENCE_FIELDS, Set.of(metamodelId), deleted);
        deleteReferencedDocuments(grammars.getCompilationDirectory(),
                Set.of("metamodelId", "grammarId"), union(Set.of(metamodelId), grammarIds), deleted);
        deleteReferencedDocuments(grammars.getActivationDirectory(),
                Set.of("metamodelId", "grammarId"), union(Set.of(metamodelId), grammarIds), deleted);
        Set<String> textualReferences = union(Set.of(metamodelId), grammarIds);
        deleteReferencedDocuments(textualModels.getDefinitionDirectory(),
                Set.of("metamodelId", "grammarId"), textualReferences, deleted);
        deleteReferencedDocuments(textualModels.getSessionDirectory(),
                Set.of("metamodelId", "grammarId"), textualReferences, deleted);
        deleteReferencedDocuments(textualModels.getSyntaxStyleDirectory(),
                Set.of("metamodelId", "grammarId"), textualReferences, deleted);

        return new CleanupResult(metamodelId, List.copyOf(deleted));
    }

    /** Descripción: Recorre de forma acotada una raíz legacy y elimina únicamente archivos cuyo nombre demuestra pertenencia al metamodelo. Entradas: raíz, identificador, tipo de artefacto y acumulador. Salida: actualiza la lista de eliminados. */
    /** Descripción: Ejecuta la operación «scanByFilename» correspondiente a la responsabilidad de ReferenceScan. Entradas: root: ruta o ubicación utilizada por la operación; metamodelId: identificador que delimita el recurso objetivo; kind: dato de entrada necesario para la operación; deleted: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void scanByFilename(Path root, String metamodelId, ScanKind kind, List<String> deleted) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.walk(root, MAX_SCAN_DEPTH)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> belongsToMetamodel(path, root, metamodelId, kind))
                    .forEach(path -> deleteOwnedFile(root, path, deleted));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not enumerate dependent metamodel artifacts in " + root, exception);
        }
    }

    /**
     * Deletes JSON documents that explicitly reference one of the supplied ids and
     * returns their own top-level ids, allowing a second pass to remove derived
     * artifacts (tests, snapshots, synchronization history and textual sessions).
     */
    /** Descripción: Elimina documentos JSON que contienen referencias explícitas a los identificadores indicados y recoge sus propios IDs para encadenar la limpieza de derivados. Entradas: raíz, campos de referencia, IDs y acumulador. Salida: IDs de los documentos eliminados. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteReferencedDocuments» de forma controlada. Entradas: configuredRoot: configuración que gobierna el comportamiento; referenceFields: dato de entrada necesario para la operación; referenceIds: dato de entrada necesario para la operación; deleted: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> deleteReferencedDocuments(Path configuredRoot, Set<String> referenceFields,
            Set<String> referenceIds, List<String> deleted) {
        ReferenceScan scan = findReferencedDocuments(configuredRoot, referenceFields, referenceIds);
        Path root = normalize(configuredRoot);
        for (Path path : scan.paths()) deleteOwnedFile(root, path, deleted);
        return scan.ids();
    }

    /** Descripción: Localiza documentos JSON referenciados sin mutar el almacenamiento y recopila sus IDs para resolver dependencias transitivas controladas. Entradas: raíz, campos e IDs de referencia. Salida: IDs y rutas coincidentes. */
    /** Descripción: Obtiene la información correspondiente a «findReferencedDocuments» dentro de ReferenceScan. Entradas: configuredRoot: configuración que gobierna el comportamiento; referenceFields: dato de entrada necesario para la operación; referenceIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ReferenceScan findReferencedDocuments(Path configuredRoot, Set<String> referenceFields, Set<String> referenceIds) {
        Path root = normalize(configuredRoot);
        if (referenceIds.isEmpty() || !Files.isDirectory(root)) return new ReferenceScan(Set.of(), List.of());
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, MAX_SCAN_DEPTH)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(item -> item.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .toList()) {
                JsonNode document = readJson(path);
                if (document == null || !references(document, referenceFields, referenceIds)) continue;
                JsonNode ownId = document.get("id");
                if (ownId != null && ownId.isTextual() && !ownId.asString().isBlank()) ids.add(ownId.asString());
                paths.add(path.toAbsolutePath().normalize());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not enumerate referenced metamodel artifacts in " + root, exception);
        }
        return new ReferenceScan(Set.copyOf(ids), List.copyOf(paths));
    }

    /** Descripción: Convierte rutas encontradas en nombres relativos legibles sin exponer ubicaciones absolutas del servidor. Entradas: acumulador, raíz y rutas. Salida: añade referencias normalizadas. */
    /** Descripción: Ejecuta la operación «addReferences» correspondiente a la responsabilidad de ReferenceScan. Entradas: affected: dato de entrada necesario para la operación; configuredRoot: configuración que gobierna el comportamiento; paths: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void addReferences(List<String> affected, Path configuredRoot, List<Path> paths) {
        Path root = normalize(configuredRoot);
        for (Path path : paths) {
            Path normalized = path.toAbsolutePath().normalize();
            if (normalized.startsWith(root)) affected.add(root.getFileName() + "/" + root.relativize(normalized).toString().replace('\\', '/'));
        }
    }

    /** Descripción: Lee un documento JSON interno aplicando el límite común de E/S. Entradas: ruta del archivo. Salida: árbol JSON o null si el contenido no es JSON válido. */
    /** Descripción: Obtiene la información correspondiente a «readJson» dentro de ReferenceScan. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JsonNode readJson(Path path) {
        try {
            return objectMapper.readTree(BoundedIo.readInternal(path));
        } catch (JacksonException exception) {
            // Non-JSON or partially written state is not assumed to belong to a metamodel.
            return null;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect dependent artifact: " + path, exception);
        }
    }

    /** Descripción: Busca recursivamente referencias semánticas en campos permitidos del árbol JSON. Entradas: nodo, campos de referencia e IDs esperados. Salida: true cuando existe una referencia exacta. */
    /** Descripción: Ejecuta la operación «references» correspondiente a la responsabilidad de ReferenceScan. Entradas: node: dato de entrada necesario para la operación; fields: dato de entrada necesario para la operación; expectedIds: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean references(JsonNode node, Set<String> fields, Set<String> expectedIds) {
        if (node == null) return false;
        if (node.isObject()) {
            for (var entry : node.properties()) {
                if (fields.contains(entry.getKey()) && containsExpectedId(entry.getValue(), expectedIds)) return true;
                if (references(entry.getValue(), fields, expectedIds)) return true;
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) if (references(child, fields, expectedIds)) return true;
        }
        return false;
    }

    /** Descripción: Comprueba si un valor textual o colección contiene uno de los identificadores esperados. Entradas: valor JSON e IDs. Salida: indicador de coincidencia exacta. */
    /** Descripción: Evalúa la condición «containsExpectedId» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse; expectedIds: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean containsExpectedId(JsonNode value, Set<String> expectedIds) {
        if (value == null) return false;
        if (value.isTextual()) return expectedIds.contains(value.asString());
        if (value.isArray()) for (JsonNode child : value) if (child.isTextual() && expectedIds.contains(child.asString())) return true;
        return false;
    }

    /** Descripción: Determina si un archivo legacy pertenece de forma segura al metamodelo y al tipo de limpieza solicitado. Entradas: archivo, raíz, metamodelId y tipo. Salida: indicador de pertenencia. */
    /** Descripción: Ejecuta la operación «belongsToMetamodel» correspondiente a la responsabilidad de ReferenceScan. Entradas: path: ruta o ubicación utilizada por la operación; root: ruta o ubicación utilizada por la operación; metamodelId: identificador que delimita el recurso objetivo; kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean belongsToMetamodel(Path path, Path root, String metamodelId, ScanKind kind) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) return false;
        String fileName = normalized.getFileName().toString();
        if (!fileName.startsWith(metamodelId + ".")) return false;
        String suffix = fileName.substring(metamodelId.length()).toLowerCase(Locale.ROOT);
        if (suffix.equals(".metamodel.json")) return false;
        return switch (kind) {
            case LAYER -> LAYER_SUFFIXES.contains(suffix);
            case NOTATION -> isNotationSuffix(suffix);
            case EXPORT -> EXPORT_SUFFIXES.stream().anyMatch(suffix::endsWith);
        };
    }

    /** Descripción: Identifica sufijos propios de notaciones y representaciones visuales. Entradas: sufijo normalizado. Salida: indicador de pertenencia visual. */
    /** Descripción: Evalúa la condición «isNotationSuffix» sin modificar el estado externo. Entradas: suffix: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isNotationSuffix(String suffix) {
        return suffix.contains("notation") || suffix.contains("diagram") || suffix.contains("palette")
                || suffix.contains("representation") || suffix.contains("form");
    }

    /** Descripción: Elimina un archivo tras comprobar que permanece dentro de la raíz configurada y registra su ruta relativa. Entradas: raíz, archivo y acumulador. Salida: actualiza el resultado o comunica el fallo. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteOwnedFile» de forma controlada. Entradas: root: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación; deleted: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void deleteOwnedFile(Path root, Path path, List<String> deleted) {
        Path normalizedRoot = normalize(root);
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Deletion path is outside configured HYDRA directory: " + path);
        }
        try {
            if (Files.deleteIfExists(normalizedPath)) {
                deleted.add(normalizedRoot.getFileName() + "/" + normalizedRoot.relativize(normalizedPath).toString().replace('\\', '/'));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete dependent metamodel artifact: " + normalizedPath.getFileName(), exception);
        }
    }

    /** Descripción: Une dos conjuntos de identificadores preservando unicidad. Entradas: dos conjuntos. Salida: conjunto inmutable combinado. */
    /** Descripción: Ejecuta la operación «union» correspondiente a la responsabilidad de ReferenceScan. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Set<String> union(Set<String> first, Set<String> second) {
        LinkedHashSet<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    /** Registra una raíz mediante su nombre estable para resolver referencias relativas sin aceptar traversal. */
    /** Descripción: Ejecuta la operación «registerManagedRoot» correspondiente a la responsabilidad de ReferenceScan. Entradas: roots: dato de entrada necesario para la operación; configuredRoot: configuración que gobierna el comportamiento. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void registerManagedRoot(Map<String, Path> roots, Path configuredRoot) {
        Path root = normalize(configuredRoot);
        String name = root.getFileName() == null ? "" : root.getFileName().toString();
        if (name.isBlank()) throw new IllegalArgumentException("Managed HYDRA storage root requires a file name: " + root);
        Path previous = roots.putIfAbsent(name, root);
        if (previous != null && !previous.equals(root)) {
            throw new IllegalArgumentException("Duplicate HYDRA managed storage root name: " + name);
        }
    }

    /** Resuelve únicamente referencias root/relative previamente emitidas por HYDRA. */
    /** Descripción: Obtiene la información correspondiente a «resolveManagedReference» dentro de ReferenceScan. Entradas: reference: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveManagedReference(String reference) {
        if (reference == null || reference.isBlank() || reference.contains("\\") || reference.startsWith("/") || reference.contains("../")) return null;
        int separator = reference.indexOf('/');
        if (separator <= 0 || separator == reference.length() - 1) return null;
        Path root = managedRoots.get(reference.substring(0, separator));
        if (root == null) return null;
        Path resolved = root.resolve(reference.substring(separator + 1)).normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    /** Descripción: Añade una raíz configurada normalizada evitando duplicados. Entradas: conjunto de raíces y ruta candidata. Salida: actualiza el conjunto. */
    /** Descripción: Ejecuta la operación «addRoot» correspondiente a la responsabilidad de ReferenceScan. Entradas: roots: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void addRoot(Set<Path> roots, Path root) {
        if (root != null) roots.add(normalize(root));
    }

    /** Descripción: Normaliza una ruta configurada y rechaza valores nulos. Entradas: ruta. Salida: ruta absoluta normalizada. */
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de ReferenceScan. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Path normalize(Path path) {
        if (path == null) throw new IllegalArgumentException("Configured HYDRA storage path must not be null");
        return path.toAbsolutePath().normalize();
    }

    private record ReferenceScan(Set<String> ids, List<Path> paths) {}

    private enum ScanKind { LAYER, NOTATION, EXPORT }

    public record CleanupResult(String metamodelId, List<String> deletedFiles) {}
}

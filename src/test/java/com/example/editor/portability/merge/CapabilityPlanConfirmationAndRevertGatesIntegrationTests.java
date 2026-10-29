package com.example.editor.portability.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.config.StorageProperties;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.transaction.CapabilityLayerRollbackRequest;
import com.example.editor.portability.transaction.CapabilityLayerRollbackResult;
import com.example.editor.portability.transaction.CapabilityLayerTransactionProperties;
import com.example.editor.portability.transaction.CapabilityLayerTransactionResult;
import com.example.editor.portability.transaction.CapabilityLayerTransactionService;
import com.example.editor.project.storage.CoreProjectWorkspaceContributor;
import com.example.editor.project.storage.ProjectArtifactClassifier;
import com.example.editor.project.storage.ProjectWorkspaceContributionRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puertas de decision del traslado de capas: si hay conflictos o no, decision por conflicto, confirmacion antes de
 * aplicar y deteccion de cambios posteriores al revertir.
 *
 * <p>Etapas certificadas (directas): {@code portability_author_merge.b}, {@code portability_author_merge.c},
 * {@code x_package_and_move_capabilities.d3}, {@code portability_revert.b}. Relacionada sin demostrar:
 * {@code x_package_and_move_capabilities.m2}: desde #35 la decision por conflicto es obligatoria y los casos estan en
 * {@link #conflictsWithoutAnExplicitDecisionBlockTheMerge()}, {@link #explicitDecisionsResolveEveryConflictAndAreRecordedInTheJournal()}
 * y {@link #severalConflictsKeepTheirOwnDecisionAndAnIncompletePreviewCannotBeApplied()}.</p>
 *
 * <p>Colaboradores reales: servicio de fusion semantica, servicio de transacciones, comprobador de paquetes y registro
 * de carpetas de trabajo sobre un {@code @TempDir}. Oraculo: ficheros leidos del disco, JSON literal esperado y SHA-256
 * recalculado en el propio test; el diario de reversion se lee del disco.</p>
 */
class CapabilityPlanConfirmationAndRevertGatesIntegrationTests {
    private static final String META = "metamodels/shop.metamodel.json";
    private static final String VALS = "behavior/validations/shop.validations.json";

    private static final String EXISTING_META = "{\"id\":\"shop\",\"name\":\"Shop\",\"description\":\"Existing text\","
            + "\"classes\":[{\"id\":\"class:Order\",\"name\":\"Order\",\"abstract\":false},{\"id\":\"class:Client\",\"name\":\"Client\"}]}";
    private static final String RECOVERED_META = "{\"id\":\"shop\",\"name\":\"Shop\",\"description\":\"Recovered text\","
            + "\"classes\":[{\"id\":\"class:Order\",\"name\":\"Order\",\"abstract\":true},{\"id\":\"class:Invoice\",\"name\":\"Invoice\"}]}";
    private static final String RECOVERED_VALS = "{\"id\":\"validations:shop\",\"rules\":["
            + "{\"id\":\"rule:ClientNeedsName\",\"name\":\"ClientNeedsName\"}]}";

    @TempDir Path temporary;

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private Path metamodelsDirectory;
    private Path validationsDirectory;
    private Path transactionsDirectory;
    private Path metaFile;
    private Path valsFile;
    private byte[] existingBytes;
    private CapabilitySemanticMergeService service;

    @BeforeEach
    void setUp() throws Exception {
        Path workspace = temporary.resolve("workspace");
        metamodelsDirectory = workspace.resolve("metamodels");
        validationsDirectory = workspace.resolve("validations");
        transactionsDirectory = temporary.resolve("transactions");
        metaFile = metamodelsDirectory.resolve("shop.metamodel.json");
        valsFile = validationsDirectory.resolve("shop.validations.json");
        Files.createDirectories(metamodelsDirectory);
        existingBytes = EXISTING_META.getBytes(StandardCharsets.UTF_8);
        Files.write(metaFile, existingBytes);

        StorageProperties storage = new StorageProperties(metamodelsDirectory, workspace.resolve("models"),
                workspace.resolve("notations"), validationsDirectory, workspace.resolve("quickfixes"),
                workspace.resolve("generators"), workspace.resolve("assets"), workspace.resolve("exports"), true);
        ProjectWorkspaceContributionRegistry registry = new ProjectWorkspaceContributionRegistry(
                List.of(new CoreProjectWorkspaceContributor(storage)));
        CapabilityLayerTransactionProperties properties = new CapabilityLayerTransactionProperties();
        properties.setStorageRoot(transactionsDirectory.toString());
        CapabilityLayerTransactionService transactions = new CapabilityLayerTransactionService(properties,
                new CapabilityPackageChecker(), registry, new ProjectArtifactClassifier(), mapper);
        service = new CapabilitySemanticMergeService(transactions, mapper);
    }

    @Test
    @DisplayName("portability_author_merge.b: sin solapes o con capas identicas no hay conflictos; con la misma capa distinta si, y se enumeran")
    void thereAreNoConflictsWithoutOverlapAndTheyAreEnumeratedWhenTheSameLayerDiffers() throws Exception {
        // Sin solape: lo existente solo tiene el metamodelo y lo recuperado solo las validaciones.
        CapabilitySemanticMergePreview disjoint = service.preview(request(
                Map.of(META, EXISTING_META), Map.of(VALS, RECOVERED_VALS), Map.of(), Map.of(META, etag(existingBytes)), false));
        assertThat(disjoint.conflicts()).isEmpty();
        assertThat(disjoint.applicable()).isTrue();
        assertThat(disjoint.items()).extracting(CapabilitySemanticMergeItem::key)
                .containsExactlyInAnyOrder(META + "#document", VALS + "#document");
        assertThat(disjoint.items()).noneMatch(CapabilitySemanticMergeItem::conflict);
        assertThat(disjoint.mergedPackage().canonicalDocuments().keySet()).containsExactlyInAnyOrder(META, VALS);
        assertThat(disjoint.mergedPackage().canonicalDocuments().get(META)).isEqualTo(mapper.readTree(EXISTING_META));
        assertThat(disjoint.mergedPackage().canonicalDocuments().get(VALS)).isEqualTo(mapper.readTree(RECOVERED_VALS));

        // Capas identicas en ambos lados: tampoco hay conflicto.
        CapabilitySemanticMergePreview identical = service.preview(request(
                Map.of(META, EXISTING_META), Map.of(META, EXISTING_META), Map.of(), Map.of(META, etag(existingBytes)), false));
        assertThat(identical.conflicts()).isEmpty();
        assertThat(identical.items()).noneMatch(CapabilitySemanticMergeItem::conflict);
        assertThat(identical.mergedPackage().canonicalDocuments().get(META)).isEqualTo(mapper.readTree(EXISTING_META));

        // Misma capa con contenido distinto: se enumeran exactamente los choques (capa, propiedad y entidad). Para que se
        // alcancen los choques anidados, la capa y la entidad se fusionan de forma explicita; propiedad y atributo quedan sin decidir.
        CapabilitySemanticMergePreview clashing = service.preview(request(
                Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                Map.of(META + "#document", CapabilitySemanticMergeDecision.MERGE,
                        META + "/classes/class:Order", CapabilitySemanticMergeDecision.MERGE),
                Map.of(META, etag(existingBytes)), false));
        assertThat(clashing.applicable()).as("los conflictos sin decision bloquean la fusion").isFalse();
        assertThat(clashing.unresolvedConflictKeys()).containsExactlyInAnyOrder(META + "/description", META + "/classes/class:Order/abstract");
        Map<String, String> conflictTypes = new LinkedHashMap<>();
        clashing.conflicts().forEach(conflict -> conflictTypes.put(conflict.key(), conflict.type()));
        assertThat(conflictTypes).containsOnly(
                Map.entry(META + "#document", "DOCUMENT"),
                Map.entry(META + "/description", "VALUE"),
                Map.entry(META + "/classes/class:Order", "ENTITY"),
                Map.entry(META + "/classes/class:Order/abstract", "VALUE"));
        assertThat(clashing.items()).filteredOn(CapabilitySemanticMergeItem::conflict)
                .extracting(CapabilitySemanticMergeItem::key).contains(META + "#document", META + "/classes/class:Order");
        // Las entidades que solo existen en un lado no son conflicto.
        assertThat(clashing.items()).filteredOn(item -> item.key().equals(META + "/classes/class:Client")
                || item.key().equals(META + "/classes/class:Invoice")).hasSize(2).noneMatch(CapabilitySemanticMergeItem::conflict);

        // La rama sin conflictos se aplica directamente al disco, sin decisiones.
        CapabilityLayerTransactionResult applied = service.apply(request(
                Map.of(META, EXISTING_META), Map.of(VALS, RECOVERED_VALS), Map.of(), Map.of(META, etag(existingBytes)), true)).transaction();
        assertThat(applied.persisted()).isTrue();
        assertThat(mapper.readTree(Files.readString(valsFile))).isEqualTo(mapper.readTree(RECOVERED_VALS));
        assertThat(mapper.readTree(Files.readString(metaFile))).isEqualTo(mapper.readTree(EXISTING_META));
    }

    @Test
    @DisplayName("portability_author_merge.c: cada conflicto recibe su decision y el documento escrito en disco la refleja")
    void everyConflictGetsItsOwnDecisionAndTheDocumentOnDiskReflectsThem() throws Exception {
        Map<String, CapabilitySemanticMergeDecision> decisions = new LinkedHashMap<>();
        decisions.put(META + "#document", CapabilitySemanticMergeDecision.MERGE);
        decisions.put(META + "/description", CapabilitySemanticMergeDecision.USE_RECOVERED);
        decisions.put(META + "/classes/class:Order", CapabilitySemanticMergeDecision.KEEP_EXISTING);
        decisions.put(META + "/classes/class:Invoice", CapabilitySemanticMergeDecision.SKIP);
        decisions.put(META + "/classes/class:Client", CapabilitySemanticMergeDecision.KEEP_EXISTING);
        CapabilitySemanticMergeRequest request = request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS),
                decisions, Map.of(META, etag(existingBytes)), true);

        CapabilitySemanticMergePreview preview = service.preview(request);

        // Cada elemento de la previsualizacion muestra su recomendacion y la decision elegida.
        Map<String, CapabilitySemanticMergeDecision> selected = new LinkedHashMap<>();
        preview.items().forEach(item -> selected.put(item.key(), item.selectedDecision()));
        assertThat(selected).containsEntry(META + "/description", CapabilitySemanticMergeDecision.USE_RECOVERED)
                .containsEntry(META + "/classes/class:Order", CapabilitySemanticMergeDecision.KEEP_EXISTING)
                .containsEntry(META + "/classes/class:Invoice", CapabilitySemanticMergeDecision.SKIP)
                .containsEntry(META + "/classes/class:Client", CapabilitySemanticMergeDecision.KEEP_EXISTING);
        assertThat(preview.items()).filteredOn(item -> item.key().equals(META + "/classes/class:Order"))
                .singleElement().satisfies(item -> {
                    assertThat(item.conflict()).isTrue();
                    assertThat(item.recommendedDecision()).isEqualTo(CapabilitySemanticMergeDecision.MERGE);
                });
        assertThat(preview.skippedElements()).isEqualTo(1);

        // Confirmar escribe exactamente el resultado de esas decisiones.
        CapabilityLayerTransactionResult applied = service.apply(request).transaction();
        assertThat(applied.persisted()).isTrue();
        assertThat(mapper.readTree(Files.readString(metaFile))).isEqualTo(mapper.readTree(
                "{\"id\":\"shop\",\"name\":\"Shop\",\"description\":\"Recovered text\",\"classes\":["
                        + "{\"id\":\"class:Client\",\"name\":\"Client\"},"
                        + "{\"id\":\"class:Order\",\"name\":\"Order\",\"abstract\":false}]}"));
        assertThat(Files.readString(metaFile)).doesNotContain("Invoice").doesNotContain("Existing text");

        // Cambiar una sola decision cambia el resultado: aqui se usa lo recuperado para la entidad.
        Map<String, CapabilitySemanticMergeDecision> adjusted = new LinkedHashMap<>(decisions);
        adjusted.put(META + "/classes/class:Order", CapabilitySemanticMergeDecision.USE_RECOVERED);
        adjusted.put(META + "/classes/class:Invoice", CapabilitySemanticMergeDecision.USE_RECOVERED);
        JsonNode adjustedMeta = service.preview(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS),
                adjusted, Map.of(), false)).mergedPackage().canonicalDocuments().get(META);
        assertThat(adjustedMeta.get("classes")).isEqualTo(mapper.readTree(
                "[{\"id\":\"class:Client\",\"name\":\"Client\"},"
                        + "{\"id\":\"class:Invoice\",\"name\":\"Invoice\"},"
                        + "{\"id\":\"class:Order\",\"name\":\"Order\",\"abstract\":true}]"));
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.m2 (#35): un conflicto sin decision explicita bloquea la fusion y no se aplica nada")
    void conflictsWithoutAnExplicitDecisionBlockTheMerge() throws Exception {
        CapabilitySemanticMergePreview preview = service.preview(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                Map.of(), Map.of(META, etag(existingBytes)), false));

        // defaultDecision=MERGE ya no resuelve conflictos: la capa en conflicto queda pendiente y el plan no es aplicable.
        assertThat(preview.applicable()).isFalse();
        assertThat(preview.unresolvedConflictKeys()).containsExactly(META + "#document");
        assertThat(preview.diagnostics()).anyMatch(text -> text.contains("need an explicit decision"));
        assertThatThrownBy(() -> service.apply(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                Map.of(), Map.of(META, etag(existingBytes)), true))).isInstanceOf(IllegalArgumentException.class);
        assertNothingWritten();
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.m2 (#35): MERGE y KEEP_EXISTING explicitos resuelven y se registran en el diario")
    void explicitDecisionsResolveEveryConflictAndAreRecordedInTheJournal() throws Exception {
        Map<String, CapabilitySemanticMergeDecision> merge = layerDecisions(Map.of(META + "/description", CapabilitySemanticMergeDecision.KEEP_EXISTING));
        Map<String, CapabilitySemanticMergeDecision> keep = Map.of(META + "#document", CapabilitySemanticMergeDecision.KEEP_EXISTING);

        // KEEP_EXISTING explicito sobre la capa resuelve todo el documento sin tocarlo.
        CapabilitySemanticMergePreview kept = service.preview(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                keep, Map.of(META, etag(existingBytes)), false));
        assertThat(kept.applicable()).isTrue();
        assertThat(kept.unresolvedConflictKeys()).isEmpty();
        assertThat(kept.mergedPackage().canonicalDocuments().get(META)).isEqualTo(mapper.readTree(EXISTING_META));

        // MERGE explicito exige ademas decidir cada conflicto anidado; con todos resueltos el plan se puede aplicar.
        CapabilitySemanticMergeResult applied = service.apply(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                merge, Map.of(META, etag(existingBytes)), true));
        assertThat(applied.preview().unresolvedConflictKeys()).isEmpty();
        assertThat(applied.transaction().persisted()).isTrue();

        // Las decisiones quedan en el diario y la reversion las devuelve.
        Path journal = transactionsDirectory.resolve("journals").resolve(applied.transaction().transactionId() + ".json");
        JsonNode recorded = mapper.readTree(Files.readString(journal)).get("decisions");
        assertThat(recorded.get(META + "#document").asString()).isEqualTo("MERGE");
        assertThat(recorded.get(META + "/description").asString()).isEqualTo("KEEP_EXISTING");
        assertThat(recorded.get(META + "/classes/class:Order").asString()).isEqualTo("KEEP_EXISTING");
        CapabilityLayerRollbackResult reverted = service.rollback(new CapabilityLayerRollbackRequest(
                applied.transaction().transactionId(), List.of(), applied.transaction().appliedEtags()));
        assertThat(reverted.journalClosed()).isTrue();
        assertThat(reverted.decisions()).containsEntry(META + "#document", "MERGE").containsEntry(META + "/description", "KEEP_EXISTING");
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(existingBytes);
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.m2 (#35): una decision sin sentido o ajena a la fusion se rechaza en lugar de reinterpretarse")
    void aSenselessOrForeignDecisionIsRejectedInsteadOfBeingReinterpreted() throws Exception {
        // USE_RECOVERED sobre una entidad que solo existe en lo actual no tiene sentido: bloquea la fusion y no se convierte en SKIP en silencio.
        Map<String, CapabilitySemanticMergeDecision> senseless = layerDecisions(Map.of(META + "/description", CapabilitySemanticMergeDecision.KEEP_EXISTING,
                META + "/classes/class:Client", CapabilitySemanticMergeDecision.USE_RECOVERED));
        CapabilitySemanticMergePreview preview = service.preview(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                senseless, Map.of(META, etag(existingBytes)), false));
        assertThat(preview.applicable()).isFalse();
        assertThat(preview.diagnostics()).anyMatch(text -> text.contains("not valid for the element") && text.contains("class:Client"));
        assertThatThrownBy(() -> service.apply(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                senseless, Map.of(META, etag(existingBytes)), true))).isInstanceOf(IllegalArgumentException.class);

        // Una decision que no corresponde a ningun elemento de esta fusion se avisa en la vista previa y se rechaza al aplicar.
        Map<String, CapabilitySemanticMergeDecision> foreign = layerDecisions(Map.of(META + "/description", CapabilitySemanticMergeDecision.KEEP_EXISTING,
                META + "/classes/class:Ghost", CapabilitySemanticMergeDecision.SKIP));
        CapabilitySemanticMergePreview warned = service.preview(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                foreign, Map.of(META, etag(existingBytes)), false));
        assertThat(warned.applicable()).isTrue();
        assertThat(warned.diagnostics()).anyMatch(text -> text.contains("do not match any element") && text.contains("class:Ghost"));
        assertThatThrownBy(() -> service.apply(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                foreign, Map.of(META, etag(existingBytes)), true))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("class:Ghost");
        assertNothingWritten();
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.m2 (#35): varios conflictos con decisiones distintas y una vista previa incompleta no se aplica")
    void severalConflictsKeepTheirOwnDecisionAndAnIncompletePreviewCannotBeApplied() throws Exception {
        Map<String, CapabilitySemanticMergeDecision> partial = new LinkedHashMap<>();
        partial.put(META + "#document", CapabilitySemanticMergeDecision.MERGE);
        partial.put(META + "/description", CapabilitySemanticMergeDecision.USE_RECOVERED);

        // Falta decidir la entidad Order: la vista previa lo dice y apply con persistencia se rechaza sin escribir.
        CapabilitySemanticMergePreview incomplete = service.preview(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                partial, Map.of(META, etag(existingBytes)), false));
        assertThat(incomplete.applicable()).isFalse();
        assertThat(incomplete.unresolvedConflictKeys()).containsExactly(META + "/classes/class:Order");
        assertThatThrownBy(() -> service.apply(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                partial, Map.of(META, etag(existingBytes)), true))).isInstanceOf(IllegalArgumentException.class);
        assertNothingWritten();

        // Decisiones distintas por conflicto producen un resultado distinto para cada uno.
        Map<String, CapabilitySemanticMergeDecision> complete = new LinkedHashMap<>(partial);
        complete.put(META + "/classes/class:Order", CapabilitySemanticMergeDecision.USE_RECOVERED);
        complete.put(META + "/classes/class:Order/abstract", CapabilitySemanticMergeDecision.USE_RECOVERED);
        CapabilitySemanticMergePreview resolved = service.preview(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META),
                complete, Map.of(META, etag(existingBytes)), false));
        assertThat(resolved.applicable()).isTrue();
        JsonNode merged = resolved.mergedPackage().canonicalDocuments().get(META);
        assertThat(merged.get("description").asString()).isEqualTo("Recovered text");
        assertThat(merged.get("classes")).anySatisfy(item -> {
            assertThat(item.get("id").asString()).isEqualTo("class:Order");
            assertThat(item.get("abstract").asBoolean()).isTrue();
        });
    }

    @Test
    @DisplayName("x_package_and_move_capabilities.d3: nada se aplica sin confirmar; ajustar decisiones sigue sin tocar el proyecto; confirmar escribe")
    void nothingIsAppliedUntilConfirmedAndAdjustingDecisionsStillTouchesNothing() throws Exception {
        String loaded = etag(existingBytes);
        Map<String, CapabilitySemanticMergeDecision> first = layerDecisions(Map.of(META + "/description", CapabilitySemanticMergeDecision.KEEP_EXISTING));
        Map<String, CapabilitySemanticMergeDecision> second = layerDecisions(Map.of(META + "/description", CapabilitySemanticMergeDecision.USE_RECOVERED));

        // Sin confirmar (persistir = falso): el resultado lo dice y el proyecto queda byte a byte igual, sin diario.
        CapabilitySemanticMergeResult notConfirmed = service.apply(request(Map.of(META, EXISTING_META),
                Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS), first, Map.of(META, loaded), false));
        assertThat(notConfirmed.transaction().persisted()).isFalse();
        assertThat(notConfirmed.transaction().transactionId()).isEmpty();
        assertThat(notConfirmed.transaction().diagnostics()).contains("Preview only; no layer was changed");
        assertNothingWritten();

        // Ajustar: otras decisiones dan otro plan (otra huella de plan) y todavia no se escribe nada.
        CapabilitySemanticMergePreview firstPlan = service.preview(request(Map.of(META, EXISTING_META),
                Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS), first, Map.of(META, loaded), false));
        CapabilitySemanticMergePreview secondPlan = service.preview(request(Map.of(META, EXISTING_META),
                Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS), second, Map.of(META, loaded), false));
        assertThat(secondPlan.previewId()).isNotEqualTo(firstPlan.previewId());
        assertThat(firstPlan.mergedPackage().canonicalDocuments().get(META).get("description").asString()).isEqualTo("Existing text");
        assertThat(secondPlan.mergedPackage().canonicalDocuments().get(META).get("description").asString()).isEqualTo("Recovered text");
        assertNothingWritten();

        // Confirmar sin la version vigente de la capa existente no escribe nada (la puerta tambien protege tras confirmar).
        assertThatThrownBy(() -> service.apply(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS),
                second, Map.of(), true))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("preconditions failed");
        assertNothingWritten();

        // Confirmar con las decisiones ajustadas: ahora si se escribe, con el plan elegido y su version calculada.
        CapabilityLayerTransactionResult confirmed = service.apply(request(Map.of(META, EXISTING_META),
                Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS), second, Map.of(META, loaded), true)).transaction();
        assertThat(confirmed.persisted()).isTrue();
        assertThat(mapper.readTree(Files.readString(metaFile)).get("description").asString()).isEqualTo("Recovered text");
        assertThat(valsFile).isRegularFile();
        assertThat(confirmed.appliedEtags()).containsOnly(
                Map.entry(META, etag(Files.readAllBytes(metaFile))),
                Map.entry(VALS, etag(Files.readAllBytes(valsFile))));
    }

    @Test
    @DisplayName("portability_revert.b: revertir omite las capas cambiadas despues de aplicar y revierte las que siguen en la version aplicada")
    void revertingSkipsLayersChangedAfterApplyingAndRestoresTheOnesStillAtTheAppliedVersion() throws Exception {
        CapabilityLayerTransactionResult applied = applyBoth();

        // Un cambio posterior sobre una sola capa (la creada por la transaccion).
        byte[] laterEdit = "{\"id\":\"validations:shop\",\"rules\":[],\"edited\":\"later\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(valsFile, laterEdit);

        CapabilityLayerRollbackResult first = service.rollback(new CapabilityLayerRollbackRequest(
                applied.transactionId(), List.of(), applied.appliedEtags()));

        assertThat(first.restoredDocuments()).as("skipped=" + first.skippedDocuments() + " diagnostics=" + first.diagnostics()
                + " applied=" + applied.appliedEtags().keySet()).containsExactly(META);
        assertThat(first.skippedDocuments()).containsExactly(VALS);
        assertThat(first.diagnostics()).contains("Rollback skipped because the layer changed after the transaction: " + VALS);
        assertThat(first.journalClosed()).isFalse();
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(existingBytes);
        assertThat(Files.readAllBytes(valsFile)).isEqualTo(laterEdit);
        Path journal = transactionsDirectory.resolve("journals").resolve(applied.transactionId() + ".json");
        assertThat(journal).isRegularFile();
        JsonNode journalItems = mapper.readTree(Files.readString(journal)).get("items");
        for (JsonNode item : journalItems) {
            assertThat(item.get("rolledBack").asBoolean()).isEqualTo(META.equals(item.get("documentPath").asString()));
        }

        // Una version actual declarada por quien revierte que no coincide tambien se detecta: no se pisa nada.
        CapabilityLayerTransactionResult second = applyBothAfterReset();
        CapabilityLayerRollbackResult mismatch = service.rollback(new CapabilityLayerRollbackRequest(second.transactionId(), List.of(),
                Map.of(VALS, "\"" + "0".repeat(64) + "\"", META, second.appliedEtags().get(META))));
        assertThat(mismatch.skippedDocuments()).containsExactly(VALS);
        assertThat(mismatch.restoredDocuments()).containsExactly(META);
        assertThat(mismatch.journalClosed()).isFalse();
        assertThat(valsFile).isRegularFile();

        // Con la version vigente de la capa pendiente la reversion se completa y el diario se cierra.
        CapabilityLayerRollbackResult finish = service.rollback(new CapabilityLayerRollbackRequest(second.transactionId(), List.of(),
                second.appliedEtags()));
        assertThat(finish.restoredDocuments()).containsExactly(VALS);
        assertThat(finish.skippedDocuments()).isEmpty();
        assertThat(finish.journalClosed()).isTrue();
        assertThat(valsFile).doesNotExist();
        assertThat(transactionsDirectory.resolve("journals").resolve(second.transactionId() + ".json")).doesNotExist();
    }

    @Test
    @DisplayName("#35 intermitencia: aplicar y revertir es estable en 150 repeticiones sin interferencia externa")
    void applyAndRevertStayStableUnderRepetition() throws Exception {
        for (int iteration = 0; iteration < 150; iteration++) {
            CapabilityLayerTransactionResult applied = applyBothAfterReset();
            CapabilityLayerRollbackResult reverted = service.rollback(new CapabilityLayerRollbackRequest(
                    applied.transactionId(), List.of(), applied.appliedEtags()));
            assertThat(reverted.restoredDocuments()).as("iteration " + iteration + " skipped=" + reverted.skippedDocuments()
                    + " diagnostics=" + reverted.diagnostics()).containsExactlyInAnyOrder(META, VALS);
            assertThat(reverted.skippedDocuments()).as("iteration " + iteration).isEmpty();
            assertThat(reverted.journalClosed()).as("iteration " + iteration).isTrue();
            assertThat(Files.readAllBytes(metaFile)).as("iteration " + iteration).isEqualTo(existingBytes);
            assertThat(valsFile).as("iteration " + iteration).doesNotExist();
        }
    }

    /**
     * Causa de la intermitencia observada en #35: en Windows, publicar una capa mientras otro hilo o proceso la tiene abierta falla con
     * {@code AccessDeniedException} (reproducido aqui con lectores concurrentes; no ocurre sin interferencia). Esta prueba fija lo que SI debe
     * cumplirse aunque ocurra: el fallo es seguro (se deshace lo aplicado o se avisa de que hace falta recuperacion manual, nunca queda un estado a
     * medias sin aviso) y, al cesar la interferencia, el servicio vuelve a funcionar. No afirma que el fallo no pueda producirse: es un riesgo
     * ambiental documentado.
     */
    @Test
    @DisplayName("#35 intermitencia: con lectores concurrentes hostiles un fallo es seguro y el servicio se recupera al cesar la interferencia")
    void contentionFromConcurrentReadersFailsSafelyAndTheServiceRecovers() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.List<Thread> readers = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Thread thread = new Thread(() -> {
                while (!stop.get()) {
                    try { Files.readAllBytes(metaFile); } catch (Exception ignored) { /* the layer is momentarily replaced */ }
                    try { Files.readAllBytes(valsFile); } catch (Exception ignored) { /* the layer may not exist */ }
                }
            }, "contention-reader-" + index);
            thread.setDaemon(true);
            thread.start();
            readers.add(thread);
        }
        int refused = 0;
        try {
            for (int iteration = 0; iteration < 5; iteration++) {
                CapabilityLayerTransactionResult applied;
                try { applied = applyBothAfterReset(); }
                catch (IllegalStateException failure) {
                    refused++;
                    assertThat(failure).hasMessageContaining("recovery was attempted");
                    if (!failure.getMessage().contains("manual recovery is required")) {
                        assertThat(Files.readAllBytes(metaFile)).as("iteration " + iteration + ": a refused apply leaves the layer as it was").isEqualTo(existingBytes);
                        assertThat(valsFile).as("iteration " + iteration).doesNotExist();
                    }
                    continue;
                }
                CapabilityLayerRollbackResult reverted = service.rollback(new CapabilityLayerRollbackRequest(applied.transactionId(), List.of(), applied.appliedEtags()));
                if (!reverted.journalClosed()) {
                    // Un rollback que no puede completarse deja el diario abierto y lo dice; nunca declara cerrado algo a medias.
                    assertThat(reverted.skippedDocuments()).isNotEmpty();
                    assertThat(reverted.diagnostics()).isNotEmpty();
                }
            }
        } finally {
            stop.set(true);
            for (Thread thread : readers) thread.join(5_000);
        }
        System.out.println("CONTENTION refused applies: " + refused + " of 5");
        // Sin interferencia el servicio funciona de nuevo desde un estado limpio.
        CapabilityLayerTransactionResult applied = applyBothAfterReset();
        CapabilityLayerRollbackResult reverted = service.rollback(new CapabilityLayerRollbackRequest(applied.transactionId(), List.of(), applied.appliedEtags()));
        assertThat(reverted.journalClosed()).isTrue();
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(existingBytes);
    }

    private void assertNothingWritten() throws Exception {
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(existingBytes);
        assertThat(valsFile).doesNotExist();
        assertThat(transactionsDirectory.resolve("journals")).doesNotExist();
    }

    private CapabilityLayerTransactionResult applyBoth() throws Exception {
        return service.apply(request(Map.of(META, EXISTING_META), Map.of(META, RECOVERED_META, VALS, RECOVERED_VALS),
                layerDecisions(Map.of(META + "/description", CapabilitySemanticMergeDecision.USE_RECOVERED)), Map.of(META, etag(Files.readAllBytes(metaFile))), true))
                .transaction();
    }

    /** Decisiones explicitas minimas para la capa del metamodelo: fusionar la capa y conservar la entidad Order, mas las indicadas. */
    private static Map<String, CapabilitySemanticMergeDecision> layerDecisions(Map<String, CapabilitySemanticMergeDecision> extra) {
        Map<String, CapabilitySemanticMergeDecision> decisions = new LinkedHashMap<>();
        decisions.put(META + "#document", CapabilitySemanticMergeDecision.MERGE);
        decisions.put(META + "/classes/class:Order", CapabilitySemanticMergeDecision.KEEP_EXISTING);
        decisions.putAll(extra);
        return decisions;
    }

    /** Deja el proyecto como al principio (metamodelo original, sin validaciones ni diarios) y vuelve a aplicar. */
    private CapabilityLayerTransactionResult applyBothAfterReset() throws Exception {
        Files.write(metaFile, existingBytes);
        Files.deleteIfExists(valsFile);
        return applyBoth();
    }

    private CapabilitySemanticMergeRequest request(Map<String, String> existingDocuments, Map<String, String> recoveredDocuments,
            Map<String, CapabilitySemanticMergeDecision> decisions, Map<String, String> expectedEtags, boolean persist) throws Exception {
        return new CapabilitySemanticMergeRequest(packageOf("existing", existingDocuments), packageOf("recovered", recoveredDocuments),
                "shop", expectedEtags, List.of(), CapabilitySemanticMergeDecision.MERGE, decisions, persist);
    }

    private HydraCapabilityPackage packageOf(String id, Map<String, String> documents) throws Exception {
        List<CapabilitySelection> selections = new java.util.ArrayList<>();
        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, String> document : documents.entrySet()) {
            boolean metamodel = document.getKey().equals(META);
            selections.add(new CapabilitySelection(metamodel ? "metamodel" : "validations",
                    metamodel ? HydraCapabilityKind.METAMODEL : HydraCapabilityKind.VALIDATIONS, document.getKey(),
                    metamodel ? "2.1" : "1.0", true, false, Map.of()));
            nodes.put(document.getKey(), mapper.readTree(document.getValue()));
        }
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "shop-" + id, "Shop " + id, "1.0.0", "shop",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                selections, List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, nodes, Map.of());
    }

    private static String etag(byte[] content) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
    }
}

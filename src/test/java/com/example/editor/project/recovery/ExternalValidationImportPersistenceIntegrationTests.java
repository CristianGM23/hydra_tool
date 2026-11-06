package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.adapter.ecore.DefaultEcoreMetamodelAdapter;
import com.example.editor.metamodel.adapter.ecore.EcoreAdapterProperties;
import com.example.editor.metamodel.adapter.ecore.EcorePrimitiveTypeRegistry;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.importers.EcoreMetamodelImporter;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionModelChecker;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.importing.SafeExternalConstraintTranslator;
import com.example.editor.validation.runtime.CompiledValidationRule;
import com.example.editor.validation.runtime.ExecutableValidationPlan;
import com.example.editor.validation.runtime.ValidationCompiler;
import com.example.editor.validation.runtime.ValidationPlanCache;
import com.example.editor.validation.runtime.ValidationPlanSourceVersions;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelChecker;
import com.example.editor.validation.validation.ValidationModelProperties;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la importacion de validaciones externas con persistencia real:
 * validation_author_import.{ex,cv,pt,lk}. ExternalValidationRecoveryService trabaja contra
 * servicios de expresiones, acciones y validaciones reales sobre repositorios JSON en @TempDir y
 * el fixture /fixtures/sirius-complete (.odesign y .evl). Despues se relee todo desde disco con
 * repositorios nuevos y se compila el ValidationModel con el ValidationCompiler real. El oraculo
 * del texto original es el propio fichero .evl del fixture, leido aparte.
 */
class ExternalValidationImportPersistenceIntegrationTests {

    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final String ODESIGN = "com.hydra.sirius.fixture.design/description/hydratest.odesign";
    private static final String EVL = "com.hydra.sirius.fixture.validation/validation/hydratest.evl";

    @TempDir
    Path temporary;

    private record Services(StorageProperties storage, JsonMapper mapper, ExpressionModelApplicationService expressions,
            ActionModelApplicationService actions, ValidationModelApplicationService validations) { }

    /** ex + cv + pt + lk: importa, relee desde disco, compila y comprueba reglas, acciones y enlaces. */
    @Test
    void importedRulesAndFixesSurviveReloadAndCompileWithOnlyProvableRulesActive() throws Exception {
        MetaModel metamodel = importFixtureMetamodel();
        Services services = services("import", metamodel);
        ExternalValidationRecoveryService service = new ExternalValidationRecoveryService(services.expressions(), services.actions(),
                services.validations(), new SafeExternalConstraintTranslator());

        ExternalValidationRecoveryResult result = service.recover(metamodel.id(), metamodel, List.of(entry(ODESIGN), entry(EVL)));
        assertThat(result.rulesImported()).isGreaterThanOrEqualTo(3);
        assertThat(result.quickFixesImported()).isGreaterThanOrEqualTo(3);
        assertThat(result.reviewOnlyQuickFixes()).isGreaterThanOrEqualTo(1);
        assertThat(result.sources()).containsExactlyInAnyOrder(ODESIGN, EVL);

        // Relectura con repositorios NUEVOS sobre el mismo directorio: lo importado esta de verdad en disco.
        ExpressionModel expressions = new JsonExpressionModelRepository(services.storage(), new ExpressionModelProperties(), services.mapper())
                .findById(metamodel.id()).orElseThrow().expressionModel();
        ActionModel actions = new JsonActionModelRepository(services.storage(), new ActionModelProperties(), services.mapper())
                .findById(metamodel.id()).orElseThrow().document();
        ValidationModel validations = new JsonValidationModelRepository(services.storage(), new ValidationModelProperties(), services.mapper())
                .findById(metamodel.id()).orElseThrow().document();

        // cv: lo demostrable se convierte en regla activa e incremental y en accion activa con deshacer.
        ValidationRule progress = rule(validations, "ProgressRange");
        ValidationRule positiveDuration = rule(validations, "PositiveDuration");
        assertThat(progress.enabled()).isTrue();
        assertThat(progress.executionMode()).isEqualTo(ValidationExecutionMode.INCREMENTAL);
        assertThat(progress.conversionStatus()).isEqualTo(ConversionStatus.FULL);
        assertThat(positiveDuration.enabled()).isTrue();
        ActionDefinition clamp = action(actions, "Clamp progress to 100");
        assertThat(clamp.enabled()).isTrue();
        assertThat(clamp.operations()).isNotEmpty();
        assertThat(clamp.undoPolicy()).isEqualTo(ActionUndoPolicy.REQUIRED);

        // lk: la regla ProgressRange quedo enlazada a la accion importada y esta existe en el documento de acciones.
        assertThat(progress.quickFixIds()).contains(clamp.id());
        assertThat(actions.findAction(clamp.id())).isPresent();

        // pt: la regla con guarda y la accion no demostrable se conservan desactivadas, con el texto original.
        String evlText = new String(resource(EVL), StandardCharsets.UTF_8);
        ValidationRule guarded = rule(validations, "NoSelfLoop");
        assertThat(guarded.enabled()).isFalse();
        assertThat(guarded.executionMode()).isEqualTo(ValidationExecutionMode.EXTERNAL);
        assertThat(guarded.conversionStatus()).isEqualTo(ConversionStatus.EXTERNAL);
        assertThat(guarded.annotations()).containsEntry("hydra.import.reviewRequired", "true");
        assertThat(evlText).contains(guarded.annotations().get("hydra.import.originalCondition"));
        assertThat(evlText).contains(guarded.annotations().get("hydra.import.originalGuard"));
        ActionDefinition review = action(actions, "Set generated name");
        assertThat(review.enabled()).isFalse();
        assertThat(review.operations()).isEmpty();
        assertThat(review.annotations()).containsEntry("hydra.import.reviewOnly", "true");

        // El runtime compila el ValidationModel releido: lo activo entra en el plan, lo externo no.
        ExecutableValidationPlan plan = new ValidationCompiler(new ValidationModelChecker(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())),
                new ValidationPlanCache()).compile(metamodel, expressions, actions, validations,
                new ValidationPlanSourceVersions("m1", "e1", "a1", "v1", "o1")).plan();
        assertThat(plan.rules()).extracting(CompiledValidationRule::ruleId).contains(progress.id(), positiveDuration.id())
                .doesNotContain(guarded.id());
    }

    /** ex: los ficheros enormes o binarios se omiten con aviso y no se escribe nada en disco. */
    @Test
    void oversizedOrBinarySourcesAreSkippedAndNothingIsPersisted() throws Exception {
        MetaModel metamodel = importFixtureMetamodel();
        Services services = services("skipped", metamodel);
        ExternalValidationRecoveryService service = new ExternalValidationRecoveryService(services.expressions(), services.actions(),
                services.validations(), new SafeExternalConstraintTranslator());
        byte[] oversized = new byte[StaticSourceInspectionPolicy.MAX_TEXT_SOURCE_BYTES + 1];

        ExternalValidationRecoveryResult result = service.recover(metamodel.id(), metamodel, List.of(
                new ExternalProjectArchiveEntry("validator.ts", oversized, oversized.length, sha256(oversized)),
                new ExternalProjectArchiveEntry("validator.bin", new byte[] { 0, 1, 2, 3 }, 4, "binary")));

        assertThat(result.rulesImported()).isZero();
        assertThat(result.quickFixesImported()).isZero();
        assertThat(result.diagnostics()).anyMatch(value -> value.contains("exceeds") && value.contains("validator.ts"));
        assertThat(new JsonValidationModelRepository(services.storage(), new ValidationModelProperties(), services.mapper()).findById(metamodel.id())).isEmpty();
        assertThat(new JsonActionModelRepository(services.storage(), new ActionModelProperties(), services.mapper()).findById(metamodel.id())).isEmpty();
        assertThat(new JsonExpressionModelRepository(services.storage(), new ExpressionModelProperties(), services.mapper()).findById(metamodel.id())).isEmpty();
    }

    // ---- helpers ----

    private Services services(String name, MetaModel metamodel) {
        Path root = temporary.resolve(name);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(root.resolve("metamodels"), root.resolve("models"), root.resolve("notations"),
                root.resolve("validations"), root.resolve("quickfixes"), root.resolve("generators"), root.resolve("assets"), root.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(metamodel);
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionModelApplicationService expressions = new ExpressionModelApplicationService(expressionRepository, metamodelService, typeChecker,
                new ExpressionEvaluator(typeChecker));
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ActionModelApplicationService actions = new ActionModelApplicationService(actionRepository, expressionRepository, metamodelService,
                new ActionModelChecker(typeChecker));
        ValidationModelApplicationService validations = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper), expressionRepository, actionRepository,
                metamodelService, new ValidationModelChecker(typeChecker));
        return new Services(storage, mapper, expressions, actions, validations);
    }

    private static ActionDefinition action(ActionModel model, String name) {
        return model.actions().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }

    private static ValidationRule rule(ValidationModel model, String name) {
        return model.rules().stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }

    private static ExternalProjectArchiveEntry entry(String relative) throws Exception {
        byte[] bytes = resource(relative);
        return new ExternalProjectArchiveEntry(relative, bytes, bytes.length, sha256(bytes));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static MetaModel importFixtureMetamodel() throws IOException {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        DefaultEcoreMetamodelAdapter adapter = new DefaultEcoreMetamodelAdapter(properties,
                new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
        EcoreMetamodelImporter importer = new EcoreMetamodelImporter(adapter);
        return importer.importMetamodel(new MetamodelImportSource("hydratest.ecore", "application/xml",
                        resource("com.hydra.sirius.fixture.metamodel/model/hydratest.ecore")),
                new MetamodelImportOptions("auto", "", false, true, true, false)).metamodel();
    }

    private static byte[] resource(String relative) throws IOException {
        try(InputStream input = ExternalValidationImportPersistenceIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if(input == null) throw new IOException("Missing test fixture resource: " + relative);
            return input.readAllBytes();
        }
    }
}

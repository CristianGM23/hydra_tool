package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.editor.metamodel.adapter.ecore.DefaultEcoreMetamodelAdapter;
import com.example.editor.metamodel.adapter.ecore.EcoreAdapterProperties;
import com.example.editor.metamodel.adapter.ecore.EcorePrimitiveTypeRegistry;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.importers.EcoreMetamodelImporter;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationException;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationException;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.importing.SafeExternalConstraintTranslator;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelApplicationException;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationRule;

/**
 * Regresión del proyecto Sirius real: las reglas traducibles deben llegar al runtime y
 * los quick fixes seguros deben convertirse a acciones declarativas. El código externo
 * que no pueda demostrarse equivalente se conserva, pero nunca se habilita.
 */
class ExternalValidationRecoveryRuntimeTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";

    @Test
    void recoversExecutableRulesAndSafeQuickFixesFromTheCompleteFixture() throws Exception {
        MetaModel metamodel = importFixtureMetamodel();
        ExpressionModelApplicationService expressions = mock(ExpressionModelApplicationService.class);
        ActionModelApplicationService actions = mock(ActionModelApplicationService.class);
        ValidationModelApplicationService validations = mock(ValidationModelApplicationService.class);
        when(expressions.find(metamodel.id())).thenThrow(new ExpressionModelApplicationException(
                ExpressionModelApplicationException.Reason.NOT_FOUND, "missing"));
        when(actions.find(metamodel.id())).thenThrow(new ActionModelApplicationException(
                ActionModelApplicationException.Reason.NOT_FOUND, "missing"));
        when(validations.find(metamodel.id())).thenThrow(new ValidationModelApplicationException(
                ValidationModelApplicationException.Reason.NOT_FOUND, "missing"));

        ExternalValidationRecoveryService service = new ExternalValidationRecoveryService(expressions, actions, validations,
                new SafeExternalConstraintTranslator());
        ExternalValidationRecoveryResult result = service.recover(metamodel.id(), metamodel, List.of(
                entry("com.hydra.sirius.fixture.design/description/hydratest.odesign"),
                entry("com.hydra.sirius.fixture.validation/validation/hydratest.evl")));

        ArgumentCaptor<ExpressionModel> expressionCaptor = ArgumentCaptor.forClass(ExpressionModel.class);
        ArgumentCaptor<ActionModel> actionCaptor = ArgumentCaptor.forClass(ActionModel.class);
        ArgumentCaptor<ValidationModel> validationCaptor = ArgumentCaptor.forClass(ValidationModel.class);
        verify(expressions).create(eq(metamodel.id()), expressionCaptor.capture());
        verify(actions).create(eq(metamodel.id()), actionCaptor.capture());
        verify(validations).create(eq(metamodel.id()), validationCaptor.capture());

        ActionModel actionModel = actionCaptor.getValue();
        ActionDefinition siriusClamp = action(actionModel, "Clamp progress to 100");
        ActionDefinition generatedName = action(actionModel, "Set generated name");
        ActionDefinition duration = action(actionModel, "Set duration to 1 minute");
        assertThat(siriusClamp.enabled()).isTrue();
        assertThat(siriusClamp.operations()).isNotEmpty();
        assertThat(siriusClamp.undoPolicy()).isEqualTo(ActionUndoPolicy.REQUIRED);
        assertThat(siriusClamp.annotations()).containsEntry("hydra.import.safeTranslation", "true");
        assertThat(generatedName.enabled()).isFalse();
        assertThat(generatedName.annotations()).containsEntry("hydra.import.reviewOnly", "true");
        assertThat(duration.enabled()).isTrue();
        assertThat(duration.operations()).isNotEmpty();

        ValidationModel validationModel = validationCaptor.getValue();
        ValidationRule progress = rule(validationModel, "ProgressRange");
        ValidationRule positiveDuration = rule(validationModel, "PositiveDuration");
        ValidationRule guardedNoSelfLoop = rule(validationModel, "NoSelfLoop");
        assertThat(progress.enabled()).isTrue();
        assertThat(progress.executionMode()).isEqualTo(ValidationExecutionMode.INCREMENTAL);
        assertThat(progress.conversionStatus()).isEqualTo(ConversionStatus.FULL);
        assertThat(progress.quickFixIds()).contains(siriusClamp.id());
        assertThat(positiveDuration.enabled()).isTrue();
        // EVL guard semantics are not guessed. The equivalent native Sirius rule is handled by the visual importer.
        assertThat(guardedNoSelfLoop.enabled()).isFalse();
        assertThat(guardedNoSelfLoop.executionMode()).isEqualTo(ValidationExecutionMode.EXTERNAL);
        assertThat(expressionCaptor.getValue().expressions()).isNotEmpty();
        assertThat(result.quickFixesImported()).isGreaterThanOrEqualTo(3);
        assertThat(result.reviewOnlyQuickFixes()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void oversizedOrBinarySourcesAreNotDecodedForStaticRecovery() throws Exception {
        MetaModel metamodel = importFixtureMetamodel();
        ExpressionModelApplicationService expressions = mock(ExpressionModelApplicationService.class);
        ActionModelApplicationService actions = mock(ActionModelApplicationService.class);
        ValidationModelApplicationService validations = mock(ValidationModelApplicationService.class);
        ExternalValidationRecoveryService service = new ExternalValidationRecoveryService(expressions, actions, validations,
                new SafeExternalConstraintTranslator());
        byte[] oversized = new byte[StaticSourceInspectionPolicy.MAX_TEXT_SOURCE_BYTES + 1];
        ExternalValidationRecoveryResult result = service.recover(metamodel.id(), metamodel, List.of(
                new ExternalProjectArchiveEntry("validator.ts", oversized, oversized.length, sha256(oversized)),
                new ExternalProjectArchiveEntry("validator.bin", new byte[]{0, 1, 2, 3}, 4, "binary")));
        assertThat(result.rulesImported()).isZero();
        assertThat(result.quickFixesImported()).isZero();
        assertThat(result.diagnostics()).anyMatch(value -> value.contains("exceeds") && value.contains("validator.ts"));
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
        try(InputStream input = ExternalValidationRecoveryRuntimeTests.class.getResourceAsStream(FIXTURE + relative)) {
            if(input == null) throw new IOException("Missing test fixture resource: " + relative);
            return input.readAllBytes();
        }
    }
}

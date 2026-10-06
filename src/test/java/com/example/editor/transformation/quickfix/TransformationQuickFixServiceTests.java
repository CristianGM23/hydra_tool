package com.example.editor.transformation.quickfix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationDiagnosticSeverity;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.semantic.expression.ExpressionTestFixtures;

class TransformationQuickFixServiceTests {
    @Test
    void proposesExplicitConversionWithoutMutatingOriginalBinding() {
        TransformationApplicationService transformations = mock(TransformationApplicationService.class);
        CanonicalMetamodelApplicationService metamodels = mock(CanonicalMetamodelApplicationService.class);
        TransformationDefinitionDocument document = document();
        when(transformations.check(document)).thenReturn(new TransformationCheckReport(document.id(), List.of(new TransformationDiagnostic(
                TransformationDiagnosticSeverity.ERROR, "TYPE_CONVERSION_REQUIRED", "rule:person", "binding:name", "$.rules[0].bindings[0].valuePolicy",
                "Explicit type conversion required", "Types differ", "Choose a conversion"))));
        when(metamodels.findById(ExpressionTestFixtures.METAMODEL_ID)).thenReturn(new MetamodelLoadResult(ExpressionTestFixtures.metamodel(), "etag"));

        TransformationQuickFixReport report = new TransformationQuickFixService(transformations, metamodels).suggest(document);

        assertThat(report.fixes()).singleElement().satisfies(fix -> {
            assertThat(fix.diagnosticCode()).isEqualTo("TYPE_CONVERSION_REQUIRED");
            TransformationBinding proposed = fix.proposedTransformation().rules().getFirst().bindings().getFirst();
            assertThat(proposed.valuePolicy().conversionMode()).isEqualTo(com.example.editor.transformation.core.TransformationConversionMode.TO_STRING);
        });
        assertThat(document.rules().getFirst().bindings().getFirst().valuePolicy().conversionMode())
                .isEqualTo(com.example.editor.transformation.core.TransformationConversionMode.NONE);
    }

    private TransformationDefinitionDocument document() {
        TransformationRule rule = new TransformationRule("rule:person", "Person", true, "source", ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.AGE, null, null, null, Map.of())), Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation:quickfix", "Quick fix", null,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "source-fingerprint", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "target-fingerprint", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }
}

package com.example.editor.metamodel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDesignException;
import tools.jackson.databind.json.JsonMapper;

class MetamodelValidationGateTests {
    @TempDir Path temporary;

    @Test void persistedValidationEnablesGenerationAndAnyMetamodelChangeMakesTheReceiptStale(){
        var canonical = mock(CanonicalMetamodelApplicationService.class);
        MetaModel original = CanonicalMetamodelTestFixtures.valid("people");
        MetaModel changed = new MetaModel(original.schemaVersion(), original.id(), "People changed", original.classes(), original.enums(), original.dataTypes(), original.relations(), original.annotations());
        when(canonical.findById("people")).thenReturn(new MetamodelLoadResult(original, "etag-1"), new MetamodelLoadResult(original, "etag-1"), new MetamodelLoadResult(changed, "etag-2"), new MetamodelLoadResult(changed, "etag-2"));
        var properties = new MetamodelValidationProperties(); properties.setReceiptRoot(temporary);
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var service = new MetamodelValidationApplicationService(canonical, new NeutralMetamodelValidator(), new MetamodelValidationAdvisor(),
                new MetamodelValidationFingerprint(mapper), new MetamodelValidationReceiptStore(properties, mapper), properties);

        MetamodelValidationAnalysis analysis = service.validateStored("people");
        assertThat(analysis.generationReady()).isTrue();
        assertThat(service.status("people").generationReady()).isTrue();
        MetamodelValidationGateStatus stale = service.status("people");
        assertThat(stale.stale()).isTrue();
        assertThat(stale.generationReady()).isFalse();
        assertThatThrownBy(() -> service.requireGenerationReady("people")).isInstanceOf(MetamodelDesignException.class)
                .hasMessageContaining("Validate it again");
    }

    @Test void advisorExplainsProblemLocationElementsAndRepairAtUserLevel(){
        var advisor = new MetamodelValidationAdvisor();
        var diagnostic = advisor.explain(new ValidationIssue(ValidationSeverity.ERROR, "RELATION_OPPOSITE_UNKNOWN",
                "$.relations[relation:assignedTasks]", "Unknown opposite relation id: relation:assignee"));
        assertThat(diagnostic.whatHappened()).contains("Unknown opposite relation id");
        assertThat(diagnostic.location()).contains("relation");
        assertThat(diagnostic.involvedElements()).contains("relation:assignedTasks", "relation:assignee");
        assertThat(diagnostic.howToFix()).isNotEmpty();
        assertThat(diagnostic.exportImpact()).contains("Blocks complete EMF/Xtext/Langium/GLSP/BESSER");
    }

    @Test void advisorNormalizesBareOppositeRelationIdentifiers(){
        var advisor = new MetamodelValidationAdvisor();
        var diagnostic = advisor.explain(new ValidationIssue(ValidationSeverity.ERROR, "RELATION_OPPOSITE_UNKNOWN",
                "$.relations[relation:assignedTasks]", "Unknown opposite relation id: assignee"));
        assertThat(diagnostic.involvedElements()).containsExactly("relation:assignedTasks", "relation:assignee");
    }

    @Test void draftAnalysisNeverCreatesAReusableGenerationReceipt(){
        var canonical = mock(CanonicalMetamodelApplicationService.class);
        var properties = new MetamodelValidationProperties(); properties.setReceiptRoot(temporary);
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var store = new MetamodelValidationReceiptStore(properties, mapper);
        var service = new MetamodelValidationApplicationService(canonical, new NeutralMetamodelValidator(), new MetamodelValidationAdvisor(),
                new MetamodelValidationFingerprint(mapper), store, properties);
        MetamodelValidationAnalysis analysis = service.analyzeDraft(CanonicalMetamodelTestFixtures.valid("draft"));
        assertThat(analysis.valid()).isTrue();
        assertThat(analysis.generationReady()).isFalse();
        assertThat(store.find("draft")).isEmpty();
    }
}

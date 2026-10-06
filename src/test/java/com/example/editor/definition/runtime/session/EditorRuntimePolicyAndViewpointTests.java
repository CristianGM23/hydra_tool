package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.EditorRuntimePolicy;
import com.example.editor.definition.EditorValidationTrigger;
import com.example.editor.validation.model.CanonicalModelDocument;

class EditorRuntimePolicyAndViewpointTests {
    @Test void defaultsToManualValidation() { assertThat(EditorRuntimePolicy.defaults().validationTriggers()).containsExactly(EditorValidationTrigger.MANUAL); }
    @Test void defaultsToRecoverableAutosave() { assertThat(EditorRuntimePolicy.defaults().autosave()).isTrue(); assertThat(EditorRuntimePolicy.defaults().recoverLatestSession()).isTrue(); }
    @Test void clampsOpenRepresentations() { assertThat(new EditorRuntimePolicy(Set.of(), true, true, true, false, false, 0, 0).maximumOpenRepresentations()).isEqualTo(1); }
    @Test void clampsOperationTimeout() { assertThat(new EditorRuntimePolicy(Set.of(), true, true, true, false, false, 2, 1).operationTimeoutMillis()).isEqualTo(100); }
    @Test void opensSeveralViewpointsOverOneModel() { var current = session(); var opened = current.openRepresentation("representation:tree", 3); assertThat(opened.openRepresentationIds()).containsExactly("representation:main", "representation:tree"); assertThat(opened.model()).isSameAs(current.model()); }
    @Test void evictsOldestViewpointAtConfiguredLimit() { var opened = session().openRepresentation("representation:tree", 2).openRepresentation("representation:table", 2); assertThat(opened.openRepresentationIds()).containsExactly("representation:tree", "representation:table"); }
    @Test void reopensViewpointAsMostRecent() { var opened = session().openRepresentation("representation:tree", 3).openRepresentation("representation:main", 3); assertThat(opened.openRepresentationIds()).containsExactly("representation:tree", "representation:main"); }
    @Test void closingActiveViewpointActivatesRemainingOne() { var opened = session().openRepresentation("representation:tree", 3).closeRepresentation("representation:tree"); assertThat(opened.activeRepresentationId()).isEqualTo("representation:main"); }
    @Test void refusesToCloseLastViewpoint() { var current = session(); assertThat(current.closeRepresentation("representation:main")).isSameAs(current); }
    @Test void updatePreservesOpenViewpoints() { var opened = session().openRepresentation("representation:tree", 3); assertThat(opened.update(opened.model(), null).openRepresentationIds()).containsExactly("representation:main", "representation:tree"); }
    @Test void oldConstructorKeepsBackwardCompatibility() { assertThat(session().openRepresentationIds()).containsExactly("representation:main"); }
    @Test void blankActiveViewpointCreatesNoOpenViewpoints() { var model=model();var value=new EditorModelSession("1.0","session-empty","people","people-editor","etag","",model,0,Instant.EPOCH,Instant.EPOCH,Map.of());assertThat(value.openRepresentationIds()).isEmpty(); }
    @Test void sessionRejectsModelOfAnotherMetamodel() { var wrong=new CanonicalModelDocument("1.0","model-wrong","other","","Wrong",List.of(),List.of(),List.of(),Map.of());assertThatThrownBy(()->new EditorModelSession("1.0","session-wrong","people","people-editor","etag","representation:main",wrong,0,Instant.EPOCH,Instant.EPOCH,Map.of())).isInstanceOf(IllegalArgumentException.class); }

    private EditorModelSession session() { return new EditorModelSession("1.0","session-one","people","people-editor","etag","representation:main",model(),0,Instant.EPOCH,Instant.EPOCH,Map.of()); }
    private CanonicalModelDocument model() { return new CanonicalModelDocument("1.0","model-one","people","","Model",List.of(),List.of(),List.of(),Map.of()); }
}

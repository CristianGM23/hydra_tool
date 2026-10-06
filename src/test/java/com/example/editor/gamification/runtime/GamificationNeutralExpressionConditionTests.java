package com.example.editor.gamification.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventActor;
import com.example.editor.event.EventActorKind;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSource;
import com.example.editor.event.EventSubject;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.gamification.GamificationEffectKind;
import com.example.editor.gamification.GamificationRepeatPolicy;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;

class GamificationNeutralExpressionConditionTests {
    @Test void legacyConditionIsExecutedByNeutralExpressionKernel() {
        GamificationRule rule = new GamificationRule("rule:test", "Rule", "event:test", "score", "GREATER_THAN", 10,
                "", 0, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 0, 0, GamificationEffectKind.AWARD, true);
        CanonicalEvent event = new CanonicalEvent(null, "event:1", "event:test", "1.0", Instant.now(),
                new EventSource("TEST", "test", "source", "1"), new EventContext(EventContextKind.MODEL_EDITING, "", "", "meta.test", "", "", ""),
                null, null, "", "", "", Map.of("score", 12), Map.of());
        MetaModel metamodel = new MetaModel(null, "meta.test", "Meta", java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), Map.of());
        ExpressionEvaluator evaluator = new ExpressionEvaluator(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));

        assertThat(GamificationEventMatcher.matches(rule, event, metamodel, evaluator)).isTrue();
        assertThat(GamificationEventMatcher.compile(rule).declaredReturnType().kind()).isEqualTo(com.example.editor.semantic.expression.ExpressionValueKind.BOOLEAN);
    }
    @Test void legacyPathNormalizationPreservesImplicitPayloadAndExplicitEventRoots() {
        CanonicalEvent event = new CanonicalEvent(null, "event:2", "event:test", "1.0", Instant.now(),
                new EventSource("TEST", "test", "source", "1"), new EventContext(EventContextKind.MODEL_EDITING, "", "", "meta.test", "", "", ""),
                new EventActor("actor:1", EventActorKind.USER, "Ada"),
                new EventSubject(EventSubjectKind.MODEL_ELEMENT, "document.test", "element:1", "class:Person", "", ""),
                "", "", "", Map.of("score", 12), Map.of("channel", "classroom"));

        assertThat(GamificationEventMatcher.read(event, "score")).isEqualTo(12);
        assertThat(GamificationEventMatcher.read(event, "payload.score")).isEqualTo(12);
        assertThat(GamificationEventMatcher.read(event, "actor.id")).isEqualTo("actor:1");
        assertThat(GamificationEventMatcher.read(event, "subject.elementId")).isEqualTo("element:1");
        assertThat(GamificationEventMatcher.read(event, "metadata.channel")).isEqualTo("classroom");
    }

}

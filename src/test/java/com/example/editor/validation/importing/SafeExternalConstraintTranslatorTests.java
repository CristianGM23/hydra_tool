package com.example.editor.validation.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluationContext;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/** Verifica que el subconjunto externo habilitado es realmente equivalente y ejecutable en el runtime neutral. */
class SafeExternalConstraintTranslatorTests {

    @Test
    void translatesAndExecutesTheSafeSiriusConstraintSubsetWithoutExternalExecution() {
        Fixture f = fixture();
        SafeExternalConstraintTranslator translator = new SafeExternalConstraintTranslator();
        ExpressionEvaluator evaluator = new ExpressionEvaluator(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));

        var nonEmpty = translator.translate("expression:non-empty", "NonEmptyName", f.task().id(),
                "aql:not self.name.oclIsUndefined() and self.name.trim().size() > 0", f.metamodel(), "SIRIUS_DESKTOP");
        var progress = translator.translate("expression:progress", "ProgressRange", f.task().id(),
                "aql:self.progress >= 0 and self.progress <= 100", f.metamodel(), "SIRIUS_DESKTOP");
        var noSelf = translator.translate("expression:no-self", "NoSelfLoop", f.connection().id(),
                "aql:self.source <> self.target", f.metamodel(), "SIRIUS_DESKTOP");
        var clamp = translator.translateValue("expression:clamp", "Clamp progress", f.task().id(),
                "aql:if self.progress < 0 then 0 else 100 endif", f.metamodel(), "SIRIUS_DESKTOP");

        assertThat(nonEmpty).isNotNull();
        assertThat(progress).isNotNull();
        assertThat(noSelf).isNotNull();
        assertThat(clamp).isNotNull();
        assertThat(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()).check(nonEmpty, f.metamodel()).hasErrors()).isFalse();
        assertThat(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()).check(progress, f.metamodel()).hasErrors()).isFalse();
        assertThat(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()).check(noSelf, f.metamodel()).hasErrors()).isFalse();
        assertThat(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()).check(clamp, f.metamodel()).hasErrors()).isFalse();

        CanonicalModelDocument invalid = model(f, "   ", 140, true);
        assertThat(evaluator.evaluate(nonEmpty, new ExpressionEvaluationContext(f.metamodel(), invalid, "element:task", Map.of())).value()).isEqualTo(false);
        assertThat(evaluator.evaluate(progress, new ExpressionEvaluationContext(f.metamodel(), invalid, "element:task", Map.of())).value()).isEqualTo(false);
        assertThat(evaluator.evaluate(noSelf, new ExpressionEvaluationContext(f.metamodel(), invalid, "element:connection", Map.of())).value()).isEqualTo(false);

        CanonicalModelDocument valid = model(f, "Task A", 40, false);
        assertThat(evaluator.evaluate(nonEmpty, new ExpressionEvaluationContext(f.metamodel(), valid, "element:task", Map.of())).value()).isEqualTo(true);
        assertThat(evaluator.evaluate(progress, new ExpressionEvaluationContext(f.metamodel(), valid, "element:task", Map.of())).value()).isEqualTo(true);
        assertThat(evaluator.evaluate(noSelf, new ExpressionEvaluationContext(f.metamodel(), valid, "element:connection", Map.of())).value()).isEqualTo(true);

        CanonicalModelDocument negative = model(f, "Task A", -8, false);
        assertThat(evaluator.evaluate(clamp, new ExpressionEvaluationContext(f.metamodel(), negative, "element:task", Map.of())).value()).isEqualTo(0L);
        assertThat(evaluator.evaluate(clamp, new ExpressionEvaluationContext(f.metamodel(), invalid, "element:task", Map.of())).value()).isEqualTo(100L);
    }

    private static CanonicalModelDocument model(Fixture f, String name, int progress, boolean selfLoop) {
        CanonicalModelElement task = new CanonicalModelElement("element:task", f.task().id(),
                Map.of(f.name().id(), name, f.progress().id(), progress), Map.of());
        CanonicalModelElement other = new CanonicalModelElement("element:other", f.node().id(), Map.of(), Map.of());
        CanonicalModelElement connection = new CanonicalModelElement("element:connection", f.connection().id(), Map.of(), Map.of());
        String target = selfLoop ? "element:task" : "element:other";
        return new CanonicalModelDocument("1.0", "model-translator-test", f.metamodel().id(), "", "Translator test",
                List.of("element:task"), List.of(task, other, connection), List.of(
                        new CanonicalModelRelation("relation:source", f.source().id(), "element:connection", "element:task", Map.of()),
                        new CanonicalModelRelation("relation:target", f.target().id(), "element:connection", target, Map.of())), Map.of());
    }

    private static Fixture fixture() {
        MetaAttribute name = new MetaAttribute("attribute:task-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING),
                Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute progress = new MetaAttribute("attribute:progress", "progress", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER),
                Multiplicity.one(), true, true, "", Map.of());
        MetaClass task = new MetaClass("class:Task", "Task", MetaClassKind.CLASS, List.of(name, progress), List.of(), Map.of());
        MetaClass node = new MetaClass("class:Node", "Node", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass connection = new MetaClass("class:Connection", "Connection", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation source = new MetaRelation("relation:source-def", "source", connection.id(), node.id(), MetaRelationKind.REFERENCE,
                Multiplicity.one(), Multiplicity.one(), "", true, true, Map.of());
        MetaRelation target = new MetaRelation("relation:target-def", "target", connection.id(), node.id(), MetaRelationKind.REFERENCE,
                Multiplicity.one(), Multiplicity.one(), "", true, true, Map.of());
        MetaModel metamodel = new MetaModel("2.1", "metamodel-translator", "Translator", List.of(task, node, connection),
                List.of(), List.of(), List.of(source, target), Map.of());
        return new Fixture(metamodel, task, node, connection, name, progress, source, target);
    }

    private record Fixture(MetaModel metamodel, MetaClass task, MetaClass node, MetaClass connection,
            MetaAttribute name, MetaAttribute progress, MetaRelation source, MetaRelation target) { }
}

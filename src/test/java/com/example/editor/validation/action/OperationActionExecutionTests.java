package com.example.editor.validation.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

class OperationActionExecutionTests {

    @Test
    void invokesCommandWithParameterBindingAndReturnsUndoDocument() {
        MetaModel metamodel = metamodel();
        ActionDefinition rename = new ActionDefinition("action:rename", "Rename", "", true,
                List.of(new ActionParameter("name", ActionParameterType.STRING, true)), "",
                List.of(new SetAttributeOperation("operation:set-name", self(), "attribute:Person:name", ActionValueSource.parameter("name"))),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionDefinition invoke = new ActionDefinition("action:invoke", "Invoke rename", "", true, List.of(), "",
                List.of(new InvokeOperationOperation("operation:invoke-rename", self(), "operation:Person:rename", List.of(ActionValueSource.literal("Bob")))),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionModel actions = new ActionModel("1.0", "people-actions", "people", "people", List.of(rename, invoke), Map.of());
        OperationBehaviorModel behaviors = new OperationBehaviorModel("1.0", "people", List.of(
                new OperationBinding("operation:Person:rename", OperationImplementationKind.ACTION, "", rename.id(), "", Map.of())), Map.of());

        ActionExecutionResult result = executor().execute(invoke.id(), new ActionExecutionRequest(document(), "person:1", List.of("person:1"), Map.of()), metamodel,
                new ExpressionModel("1.0", "people", "people", List.of(), Map.of()), actions, behaviors);

        assertThat(result.document().findElement("person:1").orElseThrow().attributes()).containsEntry("attribute:Person:name", "Bob");
        assertThat(result.undoAvailable()).isTrue();
        assertThat(result.undoDocument()).isEqualTo(document());
    }

    private CanonicalActionExecutor executor() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        return new CanonicalActionExecutor(evaluator, new OperationQueryRuntime(evaluator));
    }

    private ActionSelector self() { return new ActionSelector(ActionSelectorKind.SELF, "", ""); }

    private CanonicalModelDocument document() {
        CanonicalModelElement person = new CanonicalModelElement("person:1", "class:Person", Map.of("attribute:Person:name", "Alice"), Map.of());
        return new CanonicalModelDocument("1.0", "people-model", "people", "", "People", List.of(person.id()), List.of(person), List.of(), Map.of());
    }

    private MetaModel metamodel() {
        MetaAttribute name = new MetaAttribute("attribute:Person:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaParameter parameter = new MetaParameter("parameter:Person:rename:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, Map.of());
        MetaOperation operation = new MetaOperation("operation:Person:rename", "rename", null, null, true, true, List.of(parameter), List.of(), MetaOperationBehaviorKind.COMMAND, Map.of());
        MetaClass person = new MetaClass("class:Person", "Person", MetaClassKind.CLASS, List.of(name), List.of(operation), Map.of());
        return new MetaModel("2.1", "people", "People", List.of(person), List.of(), List.of(), List.of(), Map.of());
    }
}

package com.example.editor.definition;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
class EditorDefinitionCheckerTests {
    private final EditorDefinitionChecker checker = new EditorDefinitionChecker();
    @Test void acceptsCompleteEditorDefinition() { assertThat(checker.check(EditorDefinitionTestFixtures.editor(), EditorDefinitionTestFixtures.metamodel(), null, null).diagnostics()).isEmpty(); }
    @Test void rejectsEditorWithoutRepresentation() { var source=EditorDefinitionTestFixtures.editor(); var editor=new EditorDefinitionModel("1.0",source.id(),source.metamodelId(),source.name(),List.of(),source.palette(),source.forms(),source.capabilityManifest(),Map.of()); assertThat(checker.check(editor,EditorDefinitionTestFixtures.metamodel(),null,null).diagnostics()).extracting(EditorDefinitionDiagnostic::code).contains("NO_REPRESENTATION"); }
    @Test void rejectsUnknownNodeClass() { var source=EditorDefinitionTestFixtures.editor(); var mapping=new NodeMapping("node:unknown", "class:Unknown", "Unknown", EditorNodeShape.RECTANGLE, 200, 120, 100, 80, "", "", com.example.editor.definition.EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of()); var representation=new RepresentationDefinition("representation:main","Main",RepresentationKind.DIAGRAM,List.of(),List.of(mapping),List.of(),List.of(),true,Map.of()); var editor=new EditorDefinitionModel("1.0",source.id(),source.metamodelId(),source.name(),List.of(representation),new ToolPaletteDefinition(List.of(),List.of()),List.of(),source.capabilityManifest(),Map.of()); assertThat(checker.check(editor,EditorDefinitionTestFixtures.metamodel(),null,null).diagnostics()).extracting(EditorDefinitionDiagnostic::code).contains("UNKNOWN_NODE_CLASS"); }
    @Test void rejectsEdgeEndpointMismatch() { var source=EditorDefinitionTestFixtures.editor(); var edge=new EdgeMapping("edge:bad", "relation:Person:employer", "class:Company", "class:Person", EditorEdgeLineType.STRAIGHT, "", Map.of(), List.of(), Map.of()); var representation=new RepresentationDefinition("representation:main","Main",RepresentationKind.DIAGRAM,List.of(),List.of(),List.of(edge),List.of(),true,Map.of()); var editor=new EditorDefinitionModel("1.0",source.id(),source.metamodelId(),source.name(),List.of(representation),new ToolPaletteDefinition(List.of(),List.of()),List.of(),source.capabilityManifest(),Map.of()); assertThat(checker.check(editor,EditorDefinitionTestFixtures.metamodel(),null,null).diagnostics()).extracting(EditorDefinitionDiagnostic::code).contains("EDGE_ENDPOINT_MISMATCH"); }
    @Test void rejectsUnknownToolRepresentation() { var source=EditorDefinitionTestFixtures.editor(); var tool=new ValidateEditorTool("tool:validate","Validate","","",List.of("representation:unknown"),"","","MANUAL"); var editor=new EditorDefinitionModel("1.0",source.id(),source.metamodelId(),source.name(),source.representations(),new ToolPaletteDefinition(List.of(),List.of(tool)),source.forms(),source.capabilityManifest(),Map.of()); assertThat(checker.check(editor,EditorDefinitionTestFixtures.metamodel(),null,null).diagnostics()).extracting(EditorDefinitionDiagnostic::code).contains("UNKNOWN_TOOL_REPRESENTATION"); }
    @Test void rejectsUnknownPaletteTool() { var source=EditorDefinitionTestFixtures.editor(); var palette=new ToolPaletteDefinition(List.of(new PaletteGroup("group:x","X",0,List.of("tool:missing"),false)),List.of()); var editor=new EditorDefinitionModel("1.0",source.id(),source.metamodelId(),source.name(),source.representations(),palette,source.forms(),source.capabilityManifest(),Map.of()); assertThat(checker.check(editor,EditorDefinitionTestFixtures.metamodel(),null,null).diagnostics()).extracting(EditorDefinitionDiagnostic::code).contains("UNKNOWN_GROUP_TOOL","UNKNOWN_REPRESENTATION_TOOL"); }
    @Test void rejectsUnknownFormFeature() { var source=EditorDefinitionTestFixtures.editor(); var field=new FormFieldDefinition("field:bad","attribute:Unknown:x","X",FormFieldWidget.TEXT,false,false,"","",null,""); var form=new FormDefinition("form:bad","Bad","class:Person",List.of(new FormSectionDefinition("section:bad","Bad",0,1,List.of(field)))); var editor=new EditorDefinitionModel("1.0",source.id(),source.metamodelId(),source.name(),source.representations(),source.palette(),List.of(form),source.capabilityManifest(),Map.of()); assertThat(checker.check(editor,EditorDefinitionTestFixtures.metamodel(),null,null).diagnostics()).extracting(EditorDefinitionDiagnostic::code).contains("UNKNOWN_FORM_FEATURE"); }
    @Test void rejectsUnknownOperationTool() { var source=EditorDefinitionTestFixtures.editor(); var tool=new OperationEditorTool("tool:bad","Bad","","",List.of("representation:main"),"","","operation:Unknown:x"); var editor=new EditorDefinitionModel("1.0",source.id(),source.metamodelId(),source.name(),source.representations(),new ToolPaletteDefinition(List.of(),List.of(tool)),source.forms(),source.capabilityManifest(),Map.of()); assertThat(checker.check(editor,EditorDefinitionTestFixtures.metamodel(),null,null).diagnostics()).extracting(EditorDefinitionDiagnostic::code).contains("UNKNOWN_TOOL_OPERATION"); }

    @Test void rejectsDuplicateConditionalStyleConditionsEvenWhenExpressionIdsDiffer() {
        var source = EditorDefinitionTestFixtures.editor();
        var firstExpression = new com.example.editor.semantic.expression.ExpressionDefinition("expression:one", "First", "", "class:Person",
                com.example.editor.semantic.expression.ExpressionValueType.scalar(com.example.editor.semantic.expression.ExpressionValueKind.BOOLEAN),
                new com.example.editor.semantic.expression.LiteralExpressionNode("node:first", com.example.editor.semantic.expression.ExpressionValueType.scalar(com.example.editor.semantic.expression.ExpressionValueKind.BOOLEAN), true), Map.of());
        var secondExpression = new com.example.editor.semantic.expression.ExpressionDefinition("expression:two", "Second", "", "class:Person",
                com.example.editor.semantic.expression.ExpressionValueType.scalar(com.example.editor.semantic.expression.ExpressionValueKind.BOOLEAN),
                new com.example.editor.semantic.expression.LiteralExpressionNode("node:second", com.example.editor.semantic.expression.ExpressionValueType.scalar(com.example.editor.semantic.expression.ExpressionValueKind.BOOLEAN), true), Map.of());
        var expressions = new com.example.editor.validation.expression.ExpressionModel("1.0", "people", "people", List.of(firstExpression, secondExpression), Map.of());
        var rules = List.of(
                new ConditionalStyleRule("conditional-style:one", "One", true, 0, firstExpression.id(), Map.of("fillColor", "#FFFFFF")),
                new ConditionalStyleRule("conditional-style:two", "Two", true, 10, secondExpression.id(), Map.of("fillColor", "#000000")));
        var mapping = new NodeMapping("node:person", "class:Person", "Person", EditorNodeShape.ROUNDED_RECTANGLE, 220, 140, 120, 80, "", "",
                EditorImageFit.CONTAIN, false, List.of(), 18, Map.of("fillColor", "#F8FAFC"), rules, Map.of());
        var representation = new RepresentationDefinition("representation:main", "Main", RepresentationKind.DIAGRAM, List.of("class:Person"), List.of(mapping), List.of(), List.of(), true, Map.of());
        var editor = new EditorDefinitionModel("1.0", source.id(), source.metamodelId(), source.name(), List.of(representation), new ToolPaletteDefinition(List.of(), List.of()), List.of(), source.capabilityManifest(), Map.of());
        assertThat(checker.check(editor, EditorDefinitionTestFixtures.metamodel(), expressions, null).diagnostics())
                .extracting(EditorDefinitionDiagnostic::code).contains("DUPLICATE_CONDITIONAL_STYLE_CONDITION");
    }
    @Test void acceptsAllBaseLabelPropertiesAsConditionalNodeOverrides() {
        var source = EditorDefinitionTestFixtures.editor();
        var expression = new com.example.editor.semantic.expression.ExpressionDefinition("expression:label-style", "Label style", "", "class:Person",
                com.example.editor.semantic.expression.ExpressionValueType.scalar(com.example.editor.semantic.expression.ExpressionValueKind.BOOLEAN),
                new com.example.editor.semantic.expression.LiteralExpressionNode("node:label-style", com.example.editor.semantic.expression.ExpressionValueType.scalar(com.example.editor.semantic.expression.ExpressionValueKind.BOOLEAN), true), Map.of());
        var expressions = new com.example.editor.validation.expression.ExpressionModel("1.0", "people", "people", List.of(expression), Map.of());
        var rule = new ConditionalStyleRule("conditional-style:label", "Label", true, 0, expression.id(), Map.of(
                "labelColor", "#0F172A", "labelBackgroundColor", "#FFFFFF", "labelPosition", "OUTSIDE_BOTTOM",
                "labelFontSize", "16", "labelFontWeight", "700", "labelPadding", "6", "labelVisible", "true"));
        var mapping = new NodeMapping("node:person", "class:Person", "Person", EditorNodeShape.DOCUMENT, 220, 140, 120, 80, "", "",
                EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(rule), Map.of());
        var representation = new RepresentationDefinition("representation:main", "Main", RepresentationKind.DIAGRAM, List.of("class:Person"), List.of(mapping), List.of(), List.of(), true, Map.of());
        var editor = new EditorDefinitionModel("1.0", source.id(), source.metamodelId(), source.name(), List.of(representation), new ToolPaletteDefinition(List.of(), List.of()), List.of(), source.capabilityManifest(), Map.of());
        assertThat(checker.check(editor, EditorDefinitionTestFixtures.metamodel(), expressions, null).diagnostics()).isEmpty();
    }

}

package com.example.editor.definition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.metamodel.core.*;
public final class EditorDefinitionTestFixtures {
    public static MetaModel metamodel() {
        MetaAttribute name = new MetaAttribute("attribute:Person:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaOperation display = new MetaOperation("operation:Person:display", "display", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaClass person = new MetaClass("class:Person", "Person", MetaClassKind.CLASS, List.of(name), List.of(display), Map.of());
        MetaClass company = new MetaClass("class:Company", "Company", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation employer = new MetaRelation("relation:Person:employer", "employer", person.id(), company.id(), MetaRelationKind.REFERENCE, Multiplicity.one(), Multiplicity.optional(), "", true, true, Map.of());
        return new MetaModel("2.1", "people", "People", List.of(person, company), List.of(), List.of(), List.of(employer), Map.of());
    }
    public static EditorDefinitionModel editor() {
        NodeMapping personNode = new NodeMapping("node:person", "class:Person", "Person", EditorNodeShape.ROUNDED_RECTANGLE, 220, 140, 120, 80, "", "", com.example.editor.definition.EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
        EdgeMapping employer = new EdgeMapping("edge:employer", "relation:Person:employer", "class:Person", "class:Company", EditorEdgeLineType.SMOOTH_STEP, "", Map.of(), List.of(), Map.of());
        RepresentationDefinition main = new RepresentationDefinition("representation:main", "Main", RepresentationKind.DIAGRAM, List.of("class:Person"), List.of(personNode), List.of(employer), List.of("tool:create-person", "tool:employer", "tool:display"), true, Map.of());
        NodeCreationTool create = new NodeCreationTool("tool:create-person", "Person", "", "", List.of(main.id()), "", "", "class:Person", Map.of(), "form:person");
        EdgeCreationTool edge = new EdgeCreationTool("tool:employer", "Employer", "", "", List.of(main.id()), "", "", employer.id(), "relation:Person:employer", List.of("class:Person"), List.of("class:Company"), true);
        OperationEditorTool operation = new OperationEditorTool("tool:display", "Display", "", "", List.of(main.id()), "", "", "operation:Person:display");
        FormFieldDefinition field = new FormFieldDefinition("field:name", "attribute:Person:name", "Name", FormFieldWidget.TEXT, true, false, "", "", null, "");
        FormDefinition form = new FormDefinition("form:person", "Person", "class:Person", List.of(new FormSectionDefinition("section:main", "Main", 0, 1, List.of(field))));
        return new EditorDefinitionModel("1.0", "people-editor", "people", "People editor", List.of(main), new ToolPaletteDefinition(List.of(new PaletteGroup("group:elements", "Elements", 0, List.of(create.id(), edge.id(), operation.id()), false)), List.of(create, edge, operation)), List.of(form), new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING, EditorCapability.MULTIPLE_REPRESENTATIONS, EditorCapability.OPERATIONS)), Map.of());
    }
    private EditorDefinitionTestFixtures() { }
}

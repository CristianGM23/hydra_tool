package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.editor.definition.EdgeCreationTool;
import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorEdgeLineType;
import com.example.editor.definition.EditorImageFit;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.FormFieldDefinition;
import com.example.editor.definition.FormFieldWidget;
import com.example.editor.definition.FormSectionDefinition;
import com.example.editor.definition.NodeCreationTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.PaletteGroup;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.ToolPaletteDefinition;
import com.example.editor.definition.runtime.EditorDefinitionCompilationResult;
import com.example.editor.definition.runtime.EditorDefinitionCompiler;
import com.example.editor.definition.runtime.EditorRuntimeApplicationService;
import com.example.editor.definition.runtime.ExecutableEditorPlan;
import com.example.editor.definition.runtime.session.persistence.EditorModelSessionRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentResponse.ModelElementResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

@ExtendWith(MockitoExtension.class)
class EditorModelSessionServiceTests {
    @Mock EditorRuntimeApplicationService runtimes;
    @Mock CanonicalMetamodelApplicationService metamodels;
    @Mock ModelDocumentService emfModels;
    private InMemoryRepository repository;
    private EditorModelSessionService service;
    private MetaModel metamodel;
    private ExecutableEditorPlan plan;

    @BeforeEach
    void setUp() {
        metamodel = metamodel();
        EditorDefinitionModel editor = editor();
        plan = new EditorDefinitionCompiler().compile(editor, "\"editor-etag\"", new EditorDefinitionCheckReport(editor.id(), List.of()));
        when(runtimes.find("people", "people-editor")).thenReturn(new EditorDefinitionCompilationResult(plan, new EditorDefinitionCheckReport(editor.id(), List.of()), false));
        when(metamodels.findById("people")).thenReturn(new MetamodelLoadResult(metamodel, "\"metamodel-etag\""));
        repository = new InMemoryRepository();
        EditorSessionProperties properties = new EditorSessionProperties();
        properties.setMaximumHistoryEntries(10);
        service = new EditorModelSessionService(runtimes, metamodels, repository, emfModels, properties);
    }

    @Test void createsPortableBlankSession() {
        EditorSessionView view = blank();
        assertThat(view.session().schemaVersion()).isEqualTo("1.0");
        assertThat(view.session().model().elements()).isEmpty();
        assertThat(view.session().sourceEditorEtag()).isEqualTo("\"editor-etag\"");
    }

    @Test void createsRootElementWithCompiledNodeTool() {
        EditorSessionView view = blank();
        view = service.createNode("people", "people-editor", "session-one", "tool:create-person",
                new EditorNodeCreationRequest("representation:main", "", "", Map.of("attribute:Person:name", "Ada")), view.etag());
        assertThat(view.nodes()).singleElement().satisfies(node -> assertThat(node.label()).isEqualTo("Ada"));
        assertThat(view.session().model().rootElementIds()).hasSize(1);
    }

    @Test void fallbackCanvasLabelUsesOnlyTheStableElementId() {
        EditorSessionView view = createNode(blank(), "tool:create-company", Map.of());
        EditorRenderedNode company = view.nodes().stream().filter(node -> node.classId().equals("class:Company")).findFirst().orElseThrow();
        assertThat(company.label()).isEqualTo(company.elementId()).doesNotContain("Company");
    }

    @Test void createsAndRendersAnElementInsideAConfiguredVisualContainer() {
        EditorSessionView view = createNode(blank(), "tool:create-department", Map.of());
        EditorRenderedNode department = view.nodes().stream().filter(node -> node.classId().equals("class:Department")).findFirst().orElseThrow();
        assertThat(department.container()).isTrue();
        assertThat(department.imageAssetPath()).isEqualTo("assets/notation/department.svg");
        view = service.createNode("people", "people-editor", "session-one", "tool:create-person",
                new EditorNodeCreationRequest("representation:main", department.elementId(), "relation:Department:employees",
                        Map.of("attribute:Person:name", "Ada")), view.etag());
        EditorRenderedNode person = view.nodes().stream().filter(node -> node.classId().equals("class:Person")).findFirst().orElseThrow();
        assertThat(view.session().model().rootElementIds()).containsExactly(department.elementId());
        assertThat(view.session().model().relations()).singleElement().satisfies(relation -> {
            assertThat(relation.relationId()).isEqualTo("relation:Department:employees");
            assertThat(relation.sourceElementId()).isEqualTo(department.elementId());
            assertThat(relation.targetElementId()).isEqualTo(person.elementId());
        });
    }

    @Test void createsTypedRelationshipFromTwoSelectedElements() {
        EditorSessionView view = blank();
        view = createNode(view, "tool:create-person", Map.of("attribute:Person:name", "Ada"));
        String person = view.nodes().get(0).elementId();
        view = createNode(view, "tool:create-company", Map.of("attribute:Company:name", "Hydra Labs"));
        String company = view.nodes().stream().filter(node -> node.classId().equals("class:Company")).findFirst().orElseThrow().elementId();
        view = service.createEdge("people", "people-editor", "session-one", "tool:employer",
                new EditorEdgeCreationRequest("representation:main", person, company), view.etag());
        assertThat(view.edges()).singleElement().satisfies(edge -> assertThat(edge.relationName()).isEqualTo("employer"));
    }

    @Test void enforcesRelationshipUpperMultiplicity() {
        EditorSessionView view = blank();
        view = createNode(view, "tool:create-person", Map.of());
        String person = view.nodes().get(0).elementId();
        view = createNode(view, "tool:create-company", Map.of());
        String first = view.nodes().stream().filter(node -> node.classId().equals("class:Company")).findFirst().orElseThrow().elementId();
        view = service.createEdge("people", "people-editor", "session-one", "tool:employer", new EditorEdgeCreationRequest("representation:main", person, first), view.etag());
        view = createNode(view, "tool:create-company", Map.of("attribute:Company:name", "Other"));
        String second = view.nodes().stream().filter(node -> node.classId().equals("class:Company")).map(node -> node.elementId()).filter(id -> !id.equals(first)).findFirst().orElseThrow();
        String etag = view.etag();
        assertThatThrownBy(() -> service.createEdge("people", "people-editor", "session-one", "tool:employer",
                new EditorEdgeCreationRequest("representation:main", person, second), etag)).isInstanceOf(EditorSessionApplicationException.class).hasMessageContaining("multiplicity");
    }

    @Test void exposesAndAppliesGeneratedForm() {
        EditorSessionView view = createNode(blank(), "tool:create-person", Map.of());
        String person = view.nodes().get(0).elementId();
        view = service.find("people", "people-editor", "session-one", person);
        assertThat(view.selectedForm()).extracting(FormDefinition::id).isEqualTo("form:person");
        view = service.updateElement("people", "people-editor", "session-one", person,
                new EditorElementUpdateRequest("representation:main", "form:person", Map.of("attribute:Person:name", "Grace")), view.etag());
        assertThat(view.nodes()).singleElement().satisfies(node -> assertThat(node.label()).isEqualTo("Grace"));
    }

    @Test void rejectsValuesThatAreNotExposedByTheGeneratedForm() {
        EditorSessionView view = createNode(blank(), "tool:create-person", Map.of("attribute:Person:name", "Ada"));
        String person = view.nodes().get(0).elementId();
        EditorSessionView selected = service.find("people", "people-editor", "session-one", person);
        assertThatThrownBy(() -> service.updateElement("people", "people-editor", "session-one", person,
                new EditorElementUpdateRequest("representation:main", "form:person", Map.of("attribute:Person:secret", "hidden")), selected.etag()))
                .isInstanceOf(EditorSessionApplicationException.class).hasMessageContaining("not exposed");
    }

    @Test void enforcesRequiredGeneratedFormFields() {
        EditorSessionView view = createNode(blank(), "tool:create-person", Map.of("attribute:Person:name", "Ada"));
        String person = view.nodes().get(0).elementId();
        EditorSessionView selected = service.find("people", "people-editor", "session-one", person);
        assertThatThrownBy(() -> service.updateElement("people", "people-editor", "session-one", person,
                new EditorElementUpdateRequest("representation:main", "form:person", Map.of("attribute:Person:name", "")), selected.etag()))
                .isInstanceOf(EditorSessionApplicationException.class).hasMessageContaining("Required field");
    }

    @Test void switchesBetweenRepresentationsWithoutDuplicatingModel() {
        EditorSessionView view = createNode(blank(), "tool:create-person", Map.of());
        view = service.switchRepresentation("people", "people-editor", "session-one", new EditorRepresentationChangeRequest("representation:readonly"), view.etag());
        assertThat(view.session().activeRepresentationId()).isEqualTo("representation:readonly");
        assertThat(view.session().model().elements()).hasSize(1);
    }

    @Test void rejectsMutationsInReadOnlyRepresentation() {
        EditorSessionView view = service.switchRepresentation("people", "people-editor", "session-one",
                new EditorRepresentationChangeRequest("representation:readonly"), blank().etag());
        String etag = view.etag();
        assertThatThrownBy(() -> service.createNode("people", "people-editor", "session-one", "tool:create-person",
                new EditorNodeCreationRequest("representation:readonly", "", "", Map.of()), etag)).isInstanceOf(EditorSessionApplicationException.class).hasMessageContaining("read only");
    }

    @Test void supportsUndoAndRedo() {
        EditorSessionView created = createNode(blank(), "tool:create-person", Map.of());
        EditorSessionView undone = service.undo("people", "people-editor", "session-one", created.etag());
        assertThat(undone.nodes()).isEmpty();
        EditorSessionView redone = service.redo("people", "people-editor", "session-one", undone.etag());
        assertThat(redone.nodes()).hasSize(1);
        assertThat(undone.session().revision()).isGreaterThan(created.session().revision());
        assertThat(redone.session().revision()).isGreaterThan(undone.session().revision());
        assertThat(created.session().model().elements()).hasSize(1);
    }

    @Test void rejectsUndoWithAStaleClientEtag() {
        EditorSessionView created = createNode(blank(), "tool:create-person", Map.of());
        assertThatThrownBy(() -> service.undo("people", "people-editor", "session-one", "\"stale\""))
                .isInstanceOfSatisfying(EditorSessionApplicationException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(EditorSessionApplicationException.Reason.PRECONDITION_FAILED));
        assertThat(service.find("people", "people-editor", "session-one", "").session().revision()).isEqualTo(created.session().revision());
    }

    @Test void deletesSeveralSelectedElementsAtomicallyWithOneEtag() {
        EditorSessionView view = createNode(blank(), "tool:create-person", Map.of("attribute:Person:name", "Ada"));
        String person = view.nodes().stream().filter(node -> node.classId().equals("class:Person")).findFirst().orElseThrow().elementId();
        view = createNode(view, "tool:create-company", Map.of("attribute:Company:name", "One"));
        String firstCompany = view.nodes().stream().filter(node -> node.classId().equals("class:Company")).findFirst().orElseThrow().elementId();
        view = createNode(view, "tool:create-company", Map.of("attribute:Company:name", "Two"));
        EditorSessionView deleted = service.deleteElements("people", "people-editor", "session-one", new EditorElementSelectionRequest(List.of(person, firstCompany)), view.etag());
        assertThat(deleted.session().model().elements()).hasSize(1);
        assertThat(deleted.session().model().elements().get(0).attributes().values()).contains("Two");
    }

    @Test void bulkDeletionRejectsRemovingEveryRootInOneAtomicMutation() {
        EditorSessionView view = createNode(blank(), "tool:create-person", Map.of());
        String person = view.nodes().get(0).elementId();
        String etag=view.etag();
        assertThatThrownBy(() -> service.deleteElements("people", "people-editor", "session-one", new EditorElementSelectionRequest(List.of(person)), etag))
                .isInstanceOf(EditorSessionApplicationException.class).hasMessageContaining("last root");
    }

    @Test void preventsDeletingLastRoot() {
        EditorSessionView view = createNode(blank(), "tool:create-person", Map.of());
        String root = view.nodes().get(0).elementId();
        String etag = view.etag();
        assertThatThrownBy(() -> service.deleteElement("people", "people-editor", "session-one", root, etag))
                .isInstanceOf(EditorSessionApplicationException.class).hasMessageContaining("last root");
    }

    @Test void rejectsStaleEditorPlan() {
        EditorSessionView view = blank();
        EditorDefinitionModel editor = editor();
        ExecutableEditorPlan changed = new EditorDefinitionCompiler().compile(editor, "\"changed\"", new EditorDefinitionCheckReport(editor.id(), List.of()));
        when(runtimes.find("people", "people-editor")).thenReturn(new EditorDefinitionCompilationResult(changed, new EditorDefinitionCheckReport(editor.id(), List.of()), false));
        assertThatThrownBy(() -> service.find("people", "people-editor", view.session().id(), ""))
                .isInstanceOf(EditorSessionApplicationException.class).hasMessageContaining("changed");
    }

    @Test void importsAnOpenEmfModelThroughTheExistingAdapter() {
        when(emfModels.find("emf-session")).thenReturn(new ModelDocumentResponse("emf-session", "people", "people.xmi", "person-1", false,
                List.of(new ModelElementResponse("person-1", "people.ecore#//Person", "Person", null, null, Map.of("name", "Lin"))), List.of()));
        EditorSessionView view = service.importEmf("people", "people-editor", new EditorSessionImportRequest("imported-session", "emf-session", "representation:main"));
        assertThat(view.nodes()).singleElement().satisfies(node -> assertThat(node.label()).isEqualTo("Lin"));
        assertThat(view.session().annotations()).containsEntry("importAdapter", "EMF");
    }

    @Test void persistsEveryMutationWithANewEtag() {
        EditorSessionView before = blank();
        EditorSessionView after = createNode(before, "tool:create-person", Map.of());
        assertThat(after.etag()).isNotEqualTo(before.etag());
        assertThat(repository.find("session-one")).get().extracting(StoredCanonicalDocument::etag).isEqualTo(after.etag());
    }

    private EditorSessionView blank() {
        if(repository.find("session-one").isPresent()) return service.find("people", "people-editor", "session-one", "");
        return service.create("people", "people-editor", new EditorSessionCreateRequest("session-one", "model-one", "People", "representation:main", "", Map.of()));
    }
    private EditorSessionView createNode(EditorSessionView view, String tool, Map<String,Object> values) {
        return service.createNode("people", "people-editor", "session-one", tool,
                new EditorNodeCreationRequest(view.session().activeRepresentationId(), "", "", values), view.etag());
    }

    private MetaModel metamodel() {
        MetaAttribute personName = new MetaAttribute("attribute:Person:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute personSecret = new MetaAttribute("attribute:Person:secret", "secret", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute companyName = new MetaAttribute("attribute:Company:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass person = new MetaClass("class:Person", "Person", MetaClassKind.CLASS, List.of(personName, personSecret), List.of(), Map.of());
        MetaClass company = new MetaClass("class:Company", "Company", MetaClassKind.CLASS, List.of(companyName), List.of(), Map.of());
        MetaClass department = new MetaClass("class:Department", "Department", MetaClassKind.CLASS, List.of(), List.of(), Map.of("hydra:root", "true"));
        MetaRelation employer = new MetaRelation("relation:Person:employer", "employer", person.id(), company.id(), MetaRelationKind.REFERENCE, Multiplicity.one(), Multiplicity.optional(), "", true, true, Map.of("ecore.name", "employer"));
        MetaRelation employees = new MetaRelation("relation:Department:employees", "employees", department.id(), person.id(), MetaRelationKind.CONTAINMENT, Multiplicity.one(), Multiplicity.many(), "", true, true, Map.of());
        return new MetaModel("2.1", "people", "People", List.of(person, company, department), List.of(), List.of(), List.of(employer, employees), Map.of());
    }

    private EditorDefinitionModel editor() {
        NodeMapping personNode = new NodeMapping("node:person", "class:Person", "Person", EditorNodeShape.ROUNDED_RECTANGLE, 220, 140, 120, 80, "", "", com.example.editor.definition.EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
        NodeMapping companyNode = new NodeMapping("node:company", "class:Company", "Company", EditorNodeShape.RECTANGLE, 220, 140, 120, 80, "", "", com.example.editor.definition.EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
        NodeMapping departmentNode = new NodeMapping("node:department", "class:Department", "Department", EditorNodeShape.PROJECT_IMAGE, 420, 300, 240, 180, "", "assets/notation/department.svg", EditorImageFit.CONTAIN, true, List.of("relation:Department:employees"), 24, Map.of(), List.of(), Map.of());
        EdgeMapping employer = new EdgeMapping("edge:employer", "relation:Person:employer", "class:Person", "class:Company", EditorEdgeLineType.SMOOTH_STEP, "", Map.of(), List.of(), Map.of());
        List<String> toolIds = List.of("tool:create-person", "tool:create-company", "tool:create-department", "tool:employer");
        RepresentationDefinition main = new RepresentationDefinition("representation:main", "Main", RepresentationKind.DIAGRAM, List.of(), List.of(personNode, companyNode, departmentNode), List.of(employer), toolIds, true, Map.of());
        RepresentationDefinition readonly = new RepresentationDefinition("representation:readonly", "Read only", RepresentationKind.DIAGRAM, List.of(), List.of(personNode, companyNode, departmentNode), List.of(employer), toolIds, false, Map.of());
        NodeCreationTool createPerson = new NodeCreationTool("tool:create-person", "Person", "Creates a person", "", List.of(main.id(), readonly.id()), "", "", "class:Person", Map.of(), "form:person");
        NodeCreationTool createCompany = new NodeCreationTool("tool:create-company", "Company", "Creates a company", "", List.of(main.id(), readonly.id()), "", "", "class:Company", Map.of(), "form:company");
        NodeCreationTool createDepartment = new NodeCreationTool("tool:create-department", "Department", "Creates a department", "", List.of(main.id(), readonly.id()), "", "", "class:Department", Map.of(), "");
        EdgeCreationTool edge = new EdgeCreationTool("tool:employer", "Employer", "Links a person to a company", "", List.of(main.id(), readonly.id()), "", "", employer.id(), employer.relationId(), List.of("class:Person"), List.of("class:Company"), true);
        FormDefinition personForm = new FormDefinition("form:person", "Person", "class:Person", List.of(new FormSectionDefinition("section:person", "Person", 0, 1, List.of(new FormFieldDefinition("field:person-name", "attribute:Person:name", "Name", FormFieldWidget.TEXT, true, false, "", "", null, "Visible name")))));
        FormDefinition companyForm = new FormDefinition("form:company", "Company", "class:Company", List.of(new FormSectionDefinition("section:company", "Company", 0, 1, List.of(new FormFieldDefinition("field:company-name", "attribute:Company:name", "Name", FormFieldWidget.TEXT, false, false, "", "", null, "Visible name")))));
        return new EditorDefinitionModel("1.0", "people-editor", "people", "People editor", List.of(main, readonly),
                new ToolPaletteDefinition(List.of(new PaletteGroup("group:elements", "Elements", 0, toolIds, false)), List.of(createPerson, createCompany, createDepartment, edge)),
                List.of(personForm, companyForm), new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING, EditorCapability.MULTIPLE_REPRESENTATIONS, EditorCapability.UNDO_REDO)), Map.of());
    }

    private static final class InMemoryRepository implements EditorModelSessionRepository {
        private final Map<String,StoredCanonicalDocument<EditorModelSession>> data = new LinkedHashMap<>();
        private int sequence;
        public StoredCanonicalDocument<EditorModelSession> create(EditorModelSession session) { if(data.containsKey(session.id())) throw new IllegalStateException(); return put(session); }
        public StoredCanonicalDocument<EditorModelSession> replace(String id, EditorModelSession session, String etag) { StoredCanonicalDocument<EditorModelSession> current=data.get(id); if(current==null) throw new IllegalStateException(); if(!current.etag().equals(etag)) throw new com.example.editor.validation.persistence.CanonicalDocumentStoreException(com.example.editor.validation.persistence.CanonicalDocumentStoreException.Reason.PRECONDITION_FAILED,"stale"); return put(session); }
        public Optional<StoredCanonicalDocument<EditorModelSession>> find(String id) { return Optional.ofNullable(data.get(id)); }
        public List<String> listIds() { return List.copyOf(data.keySet()); }
        public void delete(String id,String etag) { data.remove(id); }
        private StoredCanonicalDocument<EditorModelSession> put(EditorModelSession session) { StoredCanonicalDocument<EditorModelSession> stored=new StoredCanonicalDocument<>(session,"\"etag-"+(++sequence)+"\"",Path.of(session.id()+".json")); data.put(session.id(),stored); return stored; }
    }
}

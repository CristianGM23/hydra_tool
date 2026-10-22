package com.example.editor.definition.runtime.session;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorImageFit;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.EditorRuntimePolicy;
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
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import tools.jackson.databind.json.JsonMapper;

/**
 * Material compartido (sin pruebas) por las clases de integracion de sesion de editor: metamodelo
 * real persistido en disco, definiciones de editor compiladas con el compilador real y un
 * EditorRuntimeApplicationService que sirve esos planes compilados.
 */
public final class EditorSessionRealStackFixtures {
    public static final String METAMODEL = "people";
    public static final String PERSON = "class:Person";
    public static final String NAME = "attribute:Person:name";

    private EditorSessionRealStackFixtures() { }

    public static StorageProperties storage(Path root) {
        return new StorageProperties(root.resolve("metamodels"), root.resolve("models"), root.resolve("notations"),
                root.resolve("validations"), root.resolve("quickfixes"), root.resolve("generators"), root.resolve("assets"),
                root.resolve("exports"), true);
    }

    public static CanonicalMetamodelApplicationService metamodels(Path root, JsonMapper mapper) {
        CanonicalMetamodelApplicationService service = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage(root), new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        service.create(metamodel());
        return service;
    }

    public static MetaModel metamodel() {
        MetaAttribute personName = new MetaAttribute(NAME, "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute companyName = new MetaAttribute("attribute:Company:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass person = new MetaClass(PERSON, "Person", MetaClassKind.CLASS, List.of(personName), List.of(), Map.of());
        MetaClass company = new MetaClass("class:Company", "Company", MetaClassKind.CLASS, List.of(companyName), List.of(), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, METAMODEL, "People", List.of(person, company), List.of(), List.of(), List.of(), Map.of());
    }

    public static EditorDefinitionModel editor(String editorId, EditorRuntimePolicy policy, Set<EditorCapability> capabilities) {
        NodeMapping personNode = new NodeMapping("node:person", "class:Person", "Person", EditorNodeShape.ROUNDED_RECTANGLE, 220, 140, 120, 80, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
        NodeMapping companyNode = new NodeMapping("node:company", "class:Company", "Company", EditorNodeShape.RECTANGLE, 220, 140, 120, 80, "", "", EditorImageFit.CONTAIN, false, List.of(), 18, Map.of(), List.of(), Map.of());
        List<String> toolIds = List.of("tool:create-person", "tool:create-company");
        RepresentationDefinition main = new RepresentationDefinition("representation:main", "Main", RepresentationKind.DIAGRAM, List.of(), List.of(personNode, companyNode), List.of(), toolIds, true, Map.of());
        NodeCreationTool createPerson = new NodeCreationTool("tool:create-person", "Person", "Creates a person", "", List.of(main.id()), "", "", "class:Person", Map.of(), "form:person");
        NodeCreationTool createCompany = new NodeCreationTool("tool:create-company", "Company", "Creates a company", "", List.of(main.id()), "", "", "class:Company", Map.of(), "form:company");
        FormDefinition personForm = new FormDefinition("form:person", "Person", "class:Person", List.of(new FormSectionDefinition("section:person", "Person", 0, 1, List.of(new FormFieldDefinition("field:person-name", NAME, "Name", FormFieldWidget.TEXT, true, false, "", "", null, "Visible name")))));
        FormDefinition companyForm = new FormDefinition("form:company", "Company", "class:Company", List.of(new FormSectionDefinition("section:company", "Company", 0, 1, List.of(new FormFieldDefinition("field:company-name", "attribute:Company:name", "Name", FormFieldWidget.TEXT, false, false, "", "", null, "Visible name")))));
        return new EditorDefinitionModel("1.0", editorId, METAMODEL, "People editor " + editorId, List.of(main),
                new ToolPaletteDefinition(List.of(new PaletteGroup("group:elements", "Elements", 0, toolIds, false)), List.of(createPerson, createCompany)),
                List.of(personForm, companyForm), new EditorCapabilityManifest(capabilities), policy, Map.of());
    }

    /** Servicio de runtime que entrega los planes compilados por el compilador real, indexados por editor. */
    public static EditorRuntimeApplicationService runtimes(EditorDefinitionModel... editors) {
        Map<String, ExecutableEditorPlan> plans = new HashMap<>();
        for (EditorDefinitionModel editor : editors) {
            plans.put(editor.id(), new EditorDefinitionCompiler().compile(editor, "\"editor-etag\"", new EditorDefinitionCheckReport(editor.id(), List.of())));
        }
        return new EditorRuntimeApplicationService(null, new EditorDefinitionCompiler()) {
            @Override public EditorDefinitionCompilationResult find(String metamodelId, String editorId) {
                return new EditorDefinitionCompilationResult(plans.get(editorId), new EditorDefinitionCheckReport(editorId, List.of()), false);
            }
        };
    }
}

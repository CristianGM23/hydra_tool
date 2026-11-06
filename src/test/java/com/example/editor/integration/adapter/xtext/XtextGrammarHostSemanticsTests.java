package com.example.editor.integration.adapter.xtext;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

/**
 * Defectos que mostró el anfitrión Xtext real (fixtures/xtext-anfitrion, 31/10) con el paquete de universityflow: los módulos no compilaban sin
 * src/main/xtext-gen; las clases con un atributo «name» no se podían serializar porque la regla asignaba name dos veces (ID y STRING); la herencia
 * se perdía (Student no era subtipo de Person y una referencia a Person no admitía estudiantes); enumeraciones y booleanos eran EString.
 */
class XtextGrammarHostSemanticsTests {
    private final XtextTechnologyAdapter adapter = new XtextTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);

    @Test void everyModuleCompilesTheCodeThatTheHostXtextGeneratorWrites(){
        TechnologyCompilationResult result = adapter.compile(request(university()));
        List<String> poms = result.files().stream().filter(value -> value.relativePath().endsWith("/pom.xml")).map(value -> new String(value.content(), StandardCharsets.UTF_8))
                .filter(pom -> !pom.contains("<packaging>pom</packaging>")).toList();
        assertThat(poms).hasSize(3).allSatisfy(pom -> assertThat(pom).contains("build-helper-maven-plugin", "<source>src/main/xtext-gen</source>", "<directory>src/main/xtext-gen</directory>"));
    }

    @Test void aCanonicalNameAttributeIsTheIdentifierAndIsAssignedOnlyOnce(){
        String grammar = grammar(university());
        for(String rule : List.of("Student", "Lecturer", "Group")){
            assertThat(count(rule(grammar, rule), "name=")).as("assignments of name in rule %s", rule).isEqualTo(1);
        }
        assertThat(rule(grammar, "Student")).contains("'Student' name=STRING").doesNotContain("'name' name=");
        assertThat(rule(grammar, "Group")).contains("'Group' name=ID");
        assertThat(rule(grammar, "Group")).contains("members+=[Person|STRING]", "room=[Room|ID]");
    }

    @Test void inheritanceIsKeptThroughChoiceRulesAndSubtypesRepeatInheritedSyntax(){
        String grammar = grammar(university());
        assertThat(rule(grammar, "Element").replaceAll("\\s+", " ")).isEqualTo("Element: Person | Group | Room;");
        assertThat(rule(grammar, "Person").replaceAll("\\s+", " ")).isEqualTo("Person: Student | Lecturer;");
        assertThat(rule(grammar, "Student")).contains("'code' code=STRING", "'state' state=State");
        assertThat(rule(grammar, "Lecturer")).contains("'code' code=STRING", "'senior' senior=HydraBoolean");
    }

    @Test void enumerationsAndBooleansKeepTheirTypes(){
        String grammar = grammar(university());
        assertThat(grammar).contains("enum State:\n  ACTIVE | GRADUATED;", "HydraBoolean returns ecore::EBoolean:\n  'true' | 'false';",
                "import \"http://www.eclipse.org/emf/2002/Ecore\" as ecore").doesNotContain("'ACTIVE' | 'GRADUATED'", "('true'|'false')");
        assertThat(grammar.indexOf("import \"http://www.eclipse.org/emf/2002/Ecore\"")).isLessThan(grammar.indexOf("generate "));
    }

    /** El anfitrión real: con el enlace en el módulo IDE, el ServerModule de Xtext no podía crear el inyector del lenguaje (Guice: faltan
     *  IRequestManager, IProjectDescriptionFactory e IMultiRootWorkspaceConfigFactory) y ningún servidor LSP cargaba el lenguaje. */
    @Test void theHydraLanguageServerIsBoundAtServerLevelAndNotInTheLanguageInjector(){
        TechnologyCompilationResult result = adapter.compile(request(university()));
        String ideModule = text(result, "PeopleIdeModule.java");
        assertThat(ideModule).contains("extends AbstractPeopleIdeModule").doesNotContain("LanguageServerImpl", "HydraLanguageServer.class");
        assertThat(text(result, "/ide/HydraServerModule.java")).contains("Modules.override(new ServerModule())", "bind(LanguageServerImpl.class).to(HydraLanguageServer.class)");
        assertThat(text(result, "README.md")).contains("HydraServerModule");
    }

    /** El anfitrión real: didSave del servidor HYDRA lanzaba «Workspace has not been initialized yet» porque leía con un WorkspaceManager inyectado
     *  aparte (no es singleton) en lugar del del servidor, inicializado por initialize. */
    @Test void theCommitOnSaveReadsThroughTheServerWorkspaceAfterTheStandardSave(){
        String server = text(adapter.compile(request(university())), "HydraLanguageServer.java");
        assertThat(server).contains("super.didSave(params)", "getRequestManager().runRead(", "getWorkspaceManager().doRead(", "getURI(params.getTextDocument())")
                .doesNotContain("@Inject", "private WorkspaceManager");
    }

    /** El anfitrión real con logistore: la relación «returns» (palabra reservada de la gramática de Xtext) hacía fallar MWE2 con «mismatched input
     *  'returns'». Los identificadores reservados se escapan con ^ y la palabra clave entre comillas se conserva. */
    @Test void identifiersThatAreXtextKeywordsAreEscaped(){
        MetaModel base = university();
        List<MetaRelation> relations = new java.util.ArrayList<>(base.relations());
        relations.add(new MetaRelation("group-returns", "returns", "group", "room", MetaRelationKind.REFERENCE, Multiplicity.optional(), Multiplicity.many(), "", true, true, Map.of()));
        String grammar = grammar(new MetaModel(base.schemaVersion(), base.id(), base.name(), base.classes(), base.enums(), base.dataTypes(), relations, base.annotations()));
        assertThat(rule(grammar, "Group")).contains("('returns' ^returns+=[Room|ID])*");
    }

    /** Jerarquía mixta: Student declara su propio «name» de texto (STRING) y Lecturer no (ID). Con [Person|ID] un estudiante llamado «Ana López» no se
     *  podía referenciar; la referencia al supertipo usa STRING, que también nombra a los profesores, y la referencia directa a Lecturer sigue en ID. */
    @Test void aReferenceToAHierarchyThatMixesIdentifierKindsUsesStrings(){
        MetaModel base = university();
        MetaClass person = new MetaClass("person", "Person", MetaClassKind.ABSTRACT_CLASS, List.of(attribute("person-code", "code", MetaTypeReference.primitive(MetaPrimitiveType.STRING))), List.of(), Map.of());
        MetaClass student = new MetaClass("student", "Student", MetaClassKind.CLASS, List.of(attribute("student-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING))), List.of(), Map.of());
        List<MetaClass> classes = base.classes().stream().map(value -> switch(value.id()){case "person" -> person; case "student" -> student; default -> value;}).toList();
        List<MetaRelation> relations = new java.util.ArrayList<>(base.relations());
        relations.add(new MetaRelation("room-keeper", "keeper", "room", "lecturer", MetaRelationKind.REFERENCE, Multiplicity.optional(), Multiplicity.one(), "", true, true, Map.of()));
        String grammar = grammar(new MetaModel(base.schemaVersion(), base.id(), base.name(), classes, base.enums(), base.dataTypes(), relations, base.annotations()));
        assertThat(rule(grammar, "Student")).contains("'Student' name=STRING");
        assertThat(rule(grammar, "Lecturer")).contains("'Lecturer' name=ID");
        assertThat(rule(grammar, "Group")).contains("members+=[Person|STRING]");
        assertThat(rule(grammar, "Room")).contains("keeper=[Lecturer|ID]");
    }

    /** Un atributo «name» que no es un texto de un solo valor no puede ser el identificador: el identificador name=ID lo sustituye, se asigna una sola
     *  vez y se avisa con XTEXT_NAME_ATTRIBUTE_NOT_TEXT. */
    @Test void aNameAttributeThatIsNotTextIsReplacedByTheIdentifierWithAWarning(){
        MetaModel base = university();
        MetaAttribute number = new MetaAttribute("room-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.one(), true, true, "", Map.of());
        List<MetaClass> classes = base.classes().stream().map(value -> !value.id().equals("room") ? value
                : new MetaClass("room", "Room", MetaClassKind.CLASS, List.of(number), List.of(), Map.of())).toList();
        TechnologyCompilationResult result = adapter.compile(request(new MetaModel(base.schemaVersion(), base.id(), base.name(), classes, base.enums(), base.dataTypes(), base.relations(), base.annotations())));
        String room = rule(text(result, ".xtext"), "Room");
        assertThat(room).contains("'Room' name=ID");
        assertThat(count(room, "name=")).isEqualTo(1);
        assertThat(result.diagnostics()).anySatisfy(value -> {
            assertThat(value.code()).isEqualTo("XTEXT_NAME_ATTRIBUTE_NOT_TEXT");
            assertThat(value.elementId()).isEqualTo("room-name");
        });
    }

    @Test void aGrammarWithoutBooleansDoesNotImportEcore(){
        assertThat(grammar(TechnologyAdapterTestFixtures.xtextRequest())).doesNotContain("ecore").contains("employer=[Company|ID]", "'Person' name=STRING");
    }

    private String grammar(MetaModel metamodel){return grammar(request(metamodel));}
    private String grammar(TechnologyCompilationRequest request){
        return adapter.compile(request).files().stream().filter(value -> value.relativePath().endsWith(".xtext")).map(value -> new String(value.content(), StandardCharsets.UTF_8)).findFirst().orElseThrow();
    }
    private static String text(TechnologyCompilationResult result, String suffix){
        return result.files().stream().filter(value -> value.relativePath().endsWith(suffix)).map(value -> new String(value.content(), StandardCharsets.UTF_8)).findFirst().orElseThrow(() -> new AssertionError("missing " + suffix));
    }
    private static String rule(String grammar, String name){
        Matcher matcher = Pattern.compile("(?ms)^" + name + ":\\n.*?;\\n").matcher(grammar);
        assertThat(matcher.find()).as("rule %s in%n%s", name, grammar).isTrue();
        return matcher.group().trim();
    }
    private static int count(String text, String token){return text.split(Pattern.quote(token), -1).length - 1;}

    private static TechnologyCompilationRequest request(MetaModel metamodel){
        try{
            TechnologyCompilationRequest source = TechnologyAdapterTestFixtures.xtextRequest();
            List<CanonicalArtifactContent> artifacts = source.artifacts().stream().map(value -> value.kind() != IntegrationArtifactKind.METAMODEL ? value
                    : new CanonicalArtifactContent(IntegrationArtifactKind.METAMODEL, value.documentId(), value.mediaType(), TechnologyAdapterTestFixtures.MAPPER.writeValueAsBytes(metamodel))).toList();
            return new TechnologyCompilationRequest(source.profile(), artifacts, source.generatorVersion(), source.options());
        }catch(Exception exception){throw new IllegalStateException(exception);}
    }

    /** Persona abstracta con «name» de texto y dos subtipos; Group sin «name» referencia a personas (STRING) y a un aula (ID). */
    private static MetaModel university(){
        MetaAttribute name = attribute("person-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING));
        MetaAttribute code = attribute("person-code", "code", MetaTypeReference.primitive(MetaPrimitiveType.STRING));
        MetaAttribute state = attribute("student-state", "state", MetaTypeReference.enumType("state"));
        MetaAttribute senior = attribute("lecturer-senior", "senior", MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN));
        MetaClass person = new MetaClass("person", "Person", MetaClassKind.ABSTRACT_CLASS, List.of(name, code), List.of(), Map.of());
        MetaClass student = new MetaClass("student", "Student", MetaClassKind.CLASS, List.of(state), List.of(), Map.of());
        MetaClass lecturer = new MetaClass("lecturer", "Lecturer", MetaClassKind.CLASS, List.of(senior), List.of(), Map.of());
        MetaClass group = new MetaClass("group", "Group", MetaClassKind.CLASS, List.of(attribute("group-code", "code", MetaTypeReference.primitive(MetaPrimitiveType.STRING))), List.of(), Map.of());
        MetaClass room = new MetaClass("room", "Room", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaEnum states = new MetaEnum("state", "State", List.of(new MetaEnumLiteral("state-active", "ACTIVE", 0, Map.of()), new MetaEnumLiteral("state-graduated", "GRADUATED", 1, Map.of())), Map.of());
        List<MetaRelation> relations = List.of(
                new MetaRelation("student-person", "Student->Person", "student", "person", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of()),
                new MetaRelation("lecturer-person", "Lecturer->Person", "lecturer", "person", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of()),
                new MetaRelation("group-members", "members", "group", "person", MetaRelationKind.REFERENCE, Multiplicity.optional(), Multiplicity.many(), "", true, true, Map.of()),
                new MetaRelation("group-room", "room", "group", "room", MetaRelationKind.REFERENCE, Multiplicity.optional(), Multiplicity.one(), "", true, true, Map.of()));
        return new MetaModel("2.1", "people", "People", List.of(person, student, lecturer, group, room), List.of(states), List.of(), relations, Map.of());
    }
    private static MetaAttribute attribute(String id, String name, MetaTypeReference type){return new MetaAttribute(id, name, type, Multiplicity.one(), true, true, "", Map.of());}
}

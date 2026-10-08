package com.example.editor.textual.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.contract.StableIdentifierPolicy;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.export.DefaultNeutralMetamodelExportContext;
import com.example.editor.metamodel.export.LangiumMetamodelExporter;
import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.metamodel.export.XtextMetamodelExporter;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

class TextualInstanceSemanticRoundTripTests {
    private final CanonicalModelSemanticComparator comparator=new CanonicalModelSemanticComparator();
    private final GeneratedTextualInstanceCodec codec=new GeneratedTextualInstanceCodec();
    private final TextualInstanceRoundTripService roundTrips=new TextualInstanceRoundTripService(codec,comparator);
    private final TextualInstanceProviderParityService parity=new TextualInstanceProviderParityService(roundTrips,comparator);

    @Test
    void roundTripsDeepContainmentForwardBackwardCyclesAndMultipleDocumentsForBothProviders(){
        Fixture fixture=fixture();
        for(MetamodelExportFormat format:List.of(MetamodelExportFormat.XTEXT,MetamodelExportFormat.LANGIUM)){
            var result=roundTrips.requireLossless(fixture.metamodel(),fixture.model(),format,TextualInstanceDocumentStrategy.ONE_DOCUMENT_PER_ROOT);
            assertThat(result.documents()).hasSize(2);
            assertThat(result.metadata()).containsEntry("crossDocumentReferences","2").containsEntry("requiresWorkspaceIndex","true").containsEntry("deepContainmentDepth","3");
            assertThat(result.dependencies()).hasSize(2);
            assertThat(result.dependencies()).anyMatch(item->item.resolutionMode().equals("WORKSPACE_SCOPE")&&item.relationId().equals("book.author"));
            assertThat(result.dependencies()).anyMatch(item->item.resolutionMode().equals("WORKSPACE_SCOPE")&&item.relationId().equals("author.favoriteBook"));
            assertThat(result.reconstructedModel().rootElementIds()).containsExactly("lib1","author1");
            assertThat(result.sourceMap()).anyMatch(item->item.semanticKind().equals("MODEL_ELEMENT")&&item.semanticId().equals("chapter1"));
            assertThat(result.sourceMap()).anyMatch(item->item.semanticKind().equals("MODEL_FEATURE")&&item.metamodelElementId().equals("book.name")&&item.semanticId().equals("book1:book.name"));
            assertThat(result.sourceMap()).anyMatch(item->item.semanticKind().equals("MODEL_RELATION")&&item.metamodelElementId().equals("book.author"));
            assertThat(result.equivalence().equivalent()).isTrue();
        }
        assertThat(parity.requireSemanticParity(fixture.metamodel(),fixture.model(),TextualInstanceDocumentStrategy.ONE_DOCUMENT_PER_ROOT).semanticallyAligned()).isTrue();
    }

    @Test
    void preservesContainmentAsTopologyRatherThanPromotingChildrenToRoots(){
        Fixture fixture=fixture();
        var encoded=codec.encode(fixture.metamodel(),fixture.model(),MetamodelExportFormat.XTEXT,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT,1);
        assertThat(encoded.documents()).singleElement().satisfies(document->assertThat(document.content()).contains("library lib1 {","book book1 {","chapter chapter1 {") );
        var decoded=codec.decode(fixture.metamodel(),encoded.documents(),MetamodelExportFormat.XTEXT,fixture.model().id(),fixture.model().metamodelEtag(),fixture.model().name());
        assertThat(decoded.valid()).isTrue();
        assertThat(decoded.model().rootElementIds()).containsExactly("lib1","author1");
        assertThat(decoded.model().relations()).filteredOn(item->item.relationId().equals("library.books")).singleElement().satisfies(item->assertThat(item.targetElementId()).isEqualTo("book1"));
        assertThat(decoded.model().relations()).filteredOn(item->item.relationId().equals("book.chapters")).singleElement().satisfies(item->assertThat(item.targetElementId()).isEqualTo("chapter1"));
    }

    @Test
    void instanceSyntaxMatchesTheActualGeneratedXtextAndLangiumGrammars(){
        Fixture fixture=fixture();var context=new DefaultNeutralMetamodelExportContext(fixture.metamodel(),null,null,List.of());
        String xtextGrammar=new XtextMetamodelExporter().export(context);String langiumGrammar=new LangiumMetamodelExporter().export(context);
        var xtextInstance=codec.encode(fixture.metamodel(),fixture.model(),MetamodelExportFormat.XTEXT,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT,1).documents().getFirst().content();
        var langiumInstance=codec.encode(fixture.metamodel(),fixture.model(),MetamodelExportFormat.LANGIUM,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT,1).documents().getFirst().content();
        assertThat(xtextGrammar).contains("'library' name=ID","'books' books+=Book","'author' author=[model::Author|ID]","'subtitle' subtitle=STRING?");
        assertThat(langiumGrammar).contains("'library' name=ID","'books' books+=Book","'author' author=[Author:ID]","'subtitle' subtitle=STRING?");
        assertThat(xtextInstance).contains("library lib1 {","books","book book1 {","subtitle","author author1");
        assertThat(langiumInstance).isEqualTo(xtextInstance);
    }

    @Test
    void failsClosedWhenNativeIdentityWouldNeedToBeInvented(){
        MetaAttribute title=attribute("ghost.title","title",MetaPrimitiveType.STRING);
        MetaClass ghost=new MetaClass("Ghost","Ghost",MetaClassKind.CLASS,List.of(title),List.of(),Map.of("root","true"));
        MetaModel metamodel=new MetaModel(null,"ghost.mm","Ghost",List.of(ghost),List.of(),List.of(),List.of(),Map.of("rootClassId","Ghost"));
        CanonicalModelDocument model=new CanonicalModelDocument(null,"ghost.model","ghost.mm","","Ghost",List.of("ghost"),List.of(new CanonicalModelElement("ghost","Ghost",Map.of("ghost.title","Phantom"),Map.of())),List.of(),Map.of());
        var result=roundTrips.verify(metamodel,model,MetamodelExportFormat.XTEXT,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT);
        assertThat(result.lossless()).isFalse();
        assertThat(result.issues()).anyMatch(item->item.code().equals("INSTANCE_IDENTITY_REQUIRES_EXTERNAL_PROVIDER"));
        assertThatThrownBy(()->roundTrips.requireLossless(metamodel,model,MetamodelExportFormat.LANGIUM,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsClosedWhenCanonicalIdAndNativeNameDiverge(){
        Fixture fixture=fixture();CanonicalModelElement lib=fixture.model().findElement("lib1").orElseThrow();
        CanonicalModelElement changed=new CanonicalModelElement(lib.id(),lib.classId(),Map.of("library.name","different","library.label","Main"),lib.annotations());
        CanonicalModelDocument model=new CanonicalModelDocument(null,fixture.model().id(),fixture.model().metamodelId(),fixture.model().metamodelEtag(),fixture.model().name(),fixture.model().rootElementIds(),List.of(changed,fixture.model().findElement("book1").orElseThrow(),fixture.model().findElement("chapter1").orElseThrow(),fixture.model().findElement("author1").orElseThrow()),fixture.model().relations(),Map.of());
        var result=roundTrips.verify(fixture.metamodel(),model,MetamodelExportFormat.XTEXT,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT);
        assertThat(result.issues()).anyMatch(item->item.code().equals("INSTANCE_IDENTITY_MISMATCH"));
        assertThat(result.lossless()).isFalse();
    }

    @Test
    void failsClosedWhenElementIdentityCollidesWithGeneratedKeyword(){
        MetaAttribute nodeName=attribute("node.name","name",MetaPrimitiveType.STRING);
        MetaClass node=new MetaClass("Node","Node",MetaClassKind.CLASS,List.of(nodeName),List.of(),Map.of("root","true"));
        MetaModel metamodel=new MetaModel(null,"node.mm","Node",List.of(node),List.of(),List.of(),List.of(),Map.of("rootClassId","Node"));
        CanonicalModelDocument model=new CanonicalModelDocument(null,"node.model","node.mm","","Node",List.of("node"),List.of(new CanonicalModelElement("node","Node",Map.of("node.name","node"),Map.of())),List.of(),Map.of());
        var result=roundTrips.verify(metamodel,model,MetamodelExportFormat.XTEXT,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT);
        assertThat(result.lossless()).isFalse();
        assertThat(result.issues()).anyMatch(item->item.code().equals("INSTANCE_ID_COLLIDES_WITH_KEYWORD"));
    }

    @Test
    void failsClosedForNonPortableNativeIds(){
        MetaAttribute nodeName=attribute("node.name","name",MetaPrimitiveType.STRING);
        MetaClass node=new MetaClass("Node","Node",MetaClassKind.CLASS,List.of(nodeName),List.of(),Map.of("root","true"));
        MetaModel metamodel=new MetaModel(null,"node.mm","Node",List.of(node),List.of(),List.of(),List.of(),Map.of("rootClassId","Node"));
        String nonPortableId="node-1";
        assertThat(StableIdentifierPolicy.isValidElementId(nonPortableId)).isTrue();
        assertThat(nonPortableId).doesNotMatch("[A-Za-z_][A-Za-z0-9_]*");
        CanonicalModelDocument model=new CanonicalModelDocument(null,"node.model","node.mm","","Node",List.of(nonPortableId),List.of(new CanonicalModelElement(nonPortableId,"Node",Map.of("node.name",nonPortableId),Map.of())),List.of(),Map.of());
        var result=roundTrips.verify(metamodel,model,MetamodelExportFormat.LANGIUM,TextualInstanceDocumentStrategy.ONE_DOCUMENT_PER_ROOT);
        assertThat(result.documents()).isEmpty();
        assertThat(result.issues()).anyMatch(item->item.code().equals("INSTANCE_ID_NOT_PORTABLE_ID"));
    }

    @Test
    void failsClosedForGeneratedFeatureKeywordCollisions(){
        MetaAttribute nodeName=attribute("node.name","name",MetaPrimitiveType.STRING);
        MetaAttribute first=attribute("node.first","same-value",MetaPrimitiveType.STRING);
        MetaAttribute second=attribute("node.second","same value",MetaPrimitiveType.STRING);
        MetaClass node=new MetaClass("Node","Node",MetaClassKind.CLASS,List.of(nodeName,first,second),List.of(),Map.of("root","true"));
        MetaModel metamodel=new MetaModel(null,"collision.mm","Collision",List.of(node),List.of(),List.of(),List.of(),Map.of("rootClassId","Node"));
        CanonicalModelDocument model=new CanonicalModelDocument(null,"collision.model","collision.mm","","Collision",List.of("node1"),List.of(new CanonicalModelElement("node1","Node",Map.of("node.name","node1","node.first","a","node.second","b"),Map.of())),List.of(),Map.of());
        var result=roundTrips.verify(metamodel,model,MetamodelExportFormat.XTEXT,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT);
        assertThat(result.lossless()).isFalse();
        assertThat(result.issues()).anyMatch(item->item.code().equals("INSTANCE_FEATURE_KEYWORD_COLLISION"));
    }

    @Test
    void rejectsContainmentCyclesBeforeGeneratingText(){
        Fixture fixture=fixture();
        MetaRelation chapterChildren=new MetaRelation("chapter.children","children","Chapter","Chapter",MetaRelationKind.CONTAINMENT,Multiplicity.one(),Multiplicity.many(),"",true,true,Map.of());
        MetaModel metamodel=new MetaModel(null,fixture.metamodel().id(),fixture.metamodel().name(),fixture.metamodel().classes(),List.of(),List.of(),concat(fixture.metamodel().relations(),chapterChildren),fixture.metamodel().annotations());
        CanonicalModelRelation cycle=new CanonicalModelRelation("r.cycle","chapter.children","chapter1","chapter1",Map.of());
        CanonicalModelDocument cyclic=new CanonicalModelDocument(null,fixture.model().id(),fixture.model().metamodelId(),"",fixture.model().name(),fixture.model().rootElementIds(),fixture.model().elements(),concat(fixture.model().relations(),cycle),Map.of());
        var result=roundTrips.verify(metamodel,cyclic,MetamodelExportFormat.XTEXT,TextualInstanceDocumentStrategy.SINGLE_DOCUMENT);
        assertThat(result.issues()).anyMatch(item->item.code().equals("CANONICAL_MODEL_INVARIANT")&&item.path().contains("CONTAINMENT_CYCLE"));
        assertThat(result.documents()).isEmpty();
    }

    private static Fixture fixture(){
        MetaAttribute libraryName=attribute("library.name","name",MetaPrimitiveType.STRING);MetaAttribute libraryLabel=attribute("library.label","label",MetaPrimitiveType.STRING);
        MetaAttribute bookName=attribute("book.name","name",MetaPrimitiveType.STRING);MetaAttribute bookTitle=attribute("book.title","title",MetaPrimitiveType.STRING);MetaAttribute bookSubtitle=optionalAttribute("book.subtitle","subtitle",MetaPrimitiveType.STRING);
        MetaAttribute chapterName=attribute("chapter.name","name",MetaPrimitiveType.STRING);MetaAttribute chapterText=attribute("chapter.text","text",MetaPrimitiveType.STRING);
        MetaAttribute authorName=attribute("author.name","name",MetaPrimitiveType.STRING);
        MetaClass library=new MetaClass("Library","Library",MetaClassKind.CLASS,List.of(libraryName,libraryLabel),List.of(),Map.of("root","true"));
        MetaClass book=new MetaClass("Book","Book",MetaClassKind.CLASS,List.of(bookName,bookTitle,bookSubtitle),List.of(),Map.of());
        MetaClass chapter=new MetaClass("Chapter","Chapter",MetaClassKind.CLASS,List.of(chapterName,chapterText),List.of(),Map.of());
        MetaClass author=new MetaClass("Author","Author",MetaClassKind.CLASS,List.of(authorName),List.of(),Map.of());
        MetaRelation books=new MetaRelation("library.books","books","Library","Book",MetaRelationKind.CONTAINMENT,Multiplicity.one(),Multiplicity.many(),"",true,true,Map.of());
        MetaRelation featured=new MetaRelation("library.featuredBook","featuredBook","Library","Book",MetaRelationKind.REFERENCE,Multiplicity.one(),Multiplicity.optional(),"",false,true,Map.of());
        MetaRelation chapters=new MetaRelation("book.chapters","chapters","Book","Chapter",MetaRelationKind.CONTAINMENT,Multiplicity.one(),Multiplicity.many(),"",true,true,Map.of());
        MetaRelation authorRef=new MetaRelation("book.author","author","Book","Author",MetaRelationKind.REFERENCE,Multiplicity.one(),Multiplicity.one(),"",false,true,Map.of());
        MetaRelation favorite=new MetaRelation("author.favoriteBook","favoriteBook","Author","Book",MetaRelationKind.REFERENCE,Multiplicity.one(),Multiplicity.optional(),"",false,true,Map.of());
        MetaModel metamodel=new MetaModel(null,"library.mm","Library",List.of(library,book,chapter,author),List.of(),List.of(),List.of(books,featured,chapters,authorRef,favorite),Map.of("rootClassId","Library"));
        CanonicalModelElement lib=new CanonicalModelElement("lib1","Library",Map.of("library.name","lib1","library.label","Main"),Map.of());
        CanonicalModelElement bookElement=new CanonicalModelElement("book1","Book",Map.of("book.name","book1","book.title","Semantic Round Trip"),Map.of());
        CanonicalModelElement chapterElement=new CanonicalModelElement("chapter1","Chapter",Map.of("chapter.name","chapter1","chapter.text","Deep containment"),Map.of());
        CanonicalModelElement authorElement=new CanonicalModelElement("author1","Author",Map.of("author.name","author1"),Map.of());
        List<CanonicalModelRelation> relations=List.of(
                new CanonicalModelRelation("r.books","library.books","lib1","book1",Map.of()),
                new CanonicalModelRelation("r.featured","library.featuredBook","lib1","book1",Map.of()),
                new CanonicalModelRelation("r.chapters","book.chapters","book1","chapter1",Map.of()),
                new CanonicalModelRelation("r.author","book.author","book1","author1",Map.of()),
                new CanonicalModelRelation("r.favorite","author.favoriteBook","author1","book1",Map.of()));
        CanonicalModelDocument model=new CanonicalModelDocument(null,"library.model","library.mm","etag-1","Library model",List.of("lib1","author1"),List.of(lib,bookElement,chapterElement,authorElement),relations,Map.of());
        return new Fixture(metamodel,model);
    }
    private static MetaAttribute attribute(String id,String name,MetaPrimitiveType primitive){return new MetaAttribute(id,name,MetaTypeReference.primitive(primitive),Multiplicity.one(),false,true,"",Map.of());}
    private static MetaAttribute optionalAttribute(String id,String name,MetaPrimitiveType primitive){return new MetaAttribute(id,name,MetaTypeReference.primitive(primitive),Multiplicity.optional(),false,true,"",Map.of());}
    private static <T> List<T> concat(List<T> values,T extra){var result=new java.util.ArrayList<>(values);result.add(extra);return List.copyOf(result);}
    private record Fixture(MetaModel metamodel,CanonicalModelDocument model){}
}

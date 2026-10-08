package com.example.editor.metamodel.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
import com.example.editor.metamodel.export.LangiumMetamodelExporter;
import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.metamodel.export.XtextMetamodelExporter;

class TextualMetamodelSemanticRoundTripTests {
    private final MetamodelSemanticComparator comparator = new MetamodelSemanticComparator();
    private final TextualMetamodelRoundTripService service = new TextualMetamodelRoundTripService(new XtextMetamodelExporter(), new LangiumMetamodelExporter(), comparator);

    @Test
    void xtextPreservesContainmentReferenceEnumMultiplicityAndExplicitRootWithoutSyntheticName() {
        var result = service.verify(libraryMetamodel(), MetamodelExportFormat.XTEXT);
        assertThat(result.generatedText()).contains("HYDRA_SEMANTIC_ROOT Library").contains("books+=Book*").contains("featuredBook=[model::Book|ID]?");
        assertThat(result.generatedText()).contains("Book returns model::Book:\n    'book' '{'");
        assertThat(result.generatedText()).doesNotContain("Book returns model::Book:\n    'book' ID '{'");
        assertThat(result.equivalence().fidelity()).isEqualTo(SemanticFidelity.EXACT);
    }

    @Test
    void langiumPreservesTheSameNeutralSemanticsAsXtext() {
        var source=libraryMetamodel();
        var xtext=service.requireLossless(source, MetamodelExportFormat.XTEXT);
        var langium=service.requireLossless(source, MetamodelExportFormat.LANGIUM);
        assertThat(comparator.compare(xtext.reimportedMetamodel(), langium.reimportedMetamodel()).fidelity()).isEqualTo(SemanticFidelity.EXACT);
        assertThat(langium.generatedText()).contains("books+=Book*").contains("featuredBook=[Book:ID]?");
    }

    @Test
    void comparatorIgnoresRegeneratedIdsButRejectsContainmentReferenceChanges() {
        var source=libraryMetamodel();
        var changedRelations=source.relations().stream().map(value -> value.name().equals("books")
                ? new MetaRelation("regenerated", value.name(), value.sourceTypeId(), value.targetTypeId(), MetaRelationKind.REFERENCE, value.sourceMultiplicity(), value.targetMultiplicity(), "", value.ordered(), value.unique(), Map.of())
                : value).toList();
        var changed=new MetaModel(source.schemaVersion(), "another-id", source.name(), source.classes(), source.enums(), source.dataTypes(), changedRelations, source.annotations());
        assertThat(comparator.compare(source, changed).fidelity()).isEqualTo(SemanticFidelity.LOSSY);
    }

    private MetaModel libraryMetamodel() {
        MetaAttribute libraryCode = new MetaAttribute("attribute:library:code", "code", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaAttribute title = new MetaAttribute("attribute:book:title", "title", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaAttribute authorName = new MetaAttribute("attribute:author:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaAttribute genre = new MetaAttribute("attribute:book:genre", "genre", MetaTypeReference.enumType("enum:genre"), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass library = new MetaClass("class:library", "Library", MetaClassKind.CLASS, List.of(libraryCode), List.of(), Map.of("root", "true", "entryRule", "true"));
        MetaClass book = new MetaClass("class:book", "Book", MetaClassKind.CLASS, List.of(title, genre), List.of(), Map.of());
        MetaClass author = new MetaClass("class:author", "Author", MetaClassKind.CLASS, List.of(authorName), List.of(), Map.of());
        MetaEnum genreEnum = new MetaEnum("enum:genre", "Genre", List.of(new MetaEnumLiteral("literal:fiction", "FICTION", 0, Map.of()), new MetaEnumLiteral("literal:essay", "ESSAY", 1, Map.of())), Map.of());
        List<MetaRelation> relations = List.of(
                new MetaRelation("relation:library:books", "books", library.id(), book.id(), MetaRelationKind.CONTAINMENT, null, Multiplicity.many(), "", true, true, Map.of()),
                new MetaRelation("relation:library:featuredBook", "featuredBook", library.id(), book.id(), MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of()),
                new MetaRelation("relation:book:author", "author", book.id(), author.id(), MetaRelationKind.REFERENCE, null, Multiplicity.one(), "", true, true, Map.of()));
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "library", "LibraryDsl", List.of(library, book, author), List.of(genreEnum), List.of(), relations, Map.of("rootClassId", library.id(), "entryRule", "Library"));
    }
}

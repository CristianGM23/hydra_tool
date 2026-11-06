package com.example.editor.metamodel.adapter.ecore;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

/**
 * Ecore import is declared EXACT and already keeps changeable and resolveProxies as import metadata; transient, volatile, derived,
 * unsettable and iD were dropped, so a real model came back with plain features (battery scenario INT-REAL: extlibrary 250/264 flags,
 * Ecore.ecore 603/681). The flags travel as import:ecore:* annotations into the Ecore projection configuration, with no schema change.
 */
class EcoreFeatureFlagsRoundTripTests {
    private static final EcoreFactory F = EcoreFactory.eINSTANCE;
    private DefaultEcoreMetamodelAdapter adapter;

    @BeforeEach
    void setUp() {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        adapter = new DefaultEcoreMetamodelAdapter(properties, new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
    }

    @Test
    void transientVolatileDerivedUnsettableAndIdSurviveImportAndExport() {
        EPackage exported = adapter.toEPackage(adapter.fromEPackage(library(), "library.ecore").value()).value();
        EClass book = (EClass) exported.getEClassifier("Book");
        EAttribute isbn = (EAttribute) book.getEStructuralFeature("isbn");
        EAttribute summary = (EAttribute) book.getEStructuralFeature("summary");
        EReference related = (EReference) book.getEStructuralFeature("related");

        assertThat(isbn.isID()).isTrue();
        assertThat(isbn.isUnsettable()).isTrue();
        assertThat(summary.isTransient()).isTrue();
        assertThat(summary.isVolatile()).isTrue();
        assertThat(summary.isDerived()).isTrue();
        assertThat(summary.isChangeable()).isFalse();
        assertThat(related.isTransient()).isTrue();
        assertThat(related.isVolatile()).isTrue();
        assertThat(related.isDerived()).isTrue();
        assertThat(related.isUnsettable()).isTrue();
    }

    @Test
    void plainFeaturesStayPlainAndTheirAnnotationsDoNotChange() {
        var imported = adapter.fromEPackage(library(), "library.ecore").value();
        var title = imported.classes().stream().filter(c -> c.name().equals("Book")).findFirst().orElseThrow()
                .attributes().stream().filter(a -> a.name().equals("title")).findFirst().orElseThrow();
        // only flags that are set are recorded: an ordinary feature keeps exactly the annotations it had before
        assertThat(title.annotations().keySet()).noneMatch(k -> k.matches("import:ecore:(transient|volatile|derived|unsettable|iD)"));
        EAttribute exportedTitle = (EAttribute) ((EClass) adapter.toEPackage(imported).value().getEClassifier("Book")).getEStructuralFeature("title");
        assertThat(exportedTitle.isTransient() || exportedTitle.isVolatile() || exportedTitle.isDerived() || exportedTitle.isUnsettable() || exportedTitle.isID()).isFalse();
    }

    private static EPackage library() {
        EPackage p = F.createEPackage();
        p.setName("library");
        p.setNsURI("urn:library");
        p.setNsPrefix("library");
        EClass book = F.createEClass();
        book.setName("Book");
        p.getEClassifiers().add(book);
        book.getEStructuralFeatures().add(attribute("title", false, false, false, false, false, true));
        book.getEStructuralFeatures().add(attribute("isbn", false, false, false, true, true, true));
        book.getEStructuralFeatures().add(attribute("summary", true, true, true, false, false, false));
        EReference related = F.createEReference();
        related.setName("related");
        related.setEType(book);
        related.setUpperBound(-1);
        related.setTransient(true);
        related.setVolatile(true);
        related.setDerived(true);
        related.setUnsettable(true);
        book.getEStructuralFeatures().add(related);
        return p;
    }

    private static EAttribute attribute(String name, boolean isTransient, boolean isVolatile, boolean derived, boolean unsettable, boolean id, boolean changeable) {
        EAttribute a = F.createEAttribute();
        a.setName(name);
        a.setEType(EcorePackage.Literals.ESTRING);
        a.setTransient(isTransient);
        a.setVolatile(isVolatile);
        a.setDerived(derived);
        a.setUnsettable(unsettable);
        a.setID(id);
        a.setChangeable(changeable);
        return a;
    }
}

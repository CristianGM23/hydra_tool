package com.example.editor.metamodel.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que el cargador evita crear contextos innecesarios. */
class NeutralExportContextLoaderIdentityTests {

    @Test
    void preservesContextIdentityForEquivalentOptions() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        NeutralMetamodelExportContextLoader loader = id -> context;

        NeutralMetamodelExportContext loaded = loader.load("company", MetamodelExportOptions.defaults());

        assertThat(loaded).isSameAs(context);
    }

    @Test
    void createsAContextWithChangedOptionsWithoutReloadingTheModel() {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        NeutralMetamodelExportContextLoader loader = id -> context;
        MetamodelExportOptions changed = new MetamodelExportOptions(false, false, false, true, 3, "example.generated");

        NeutralMetamodelExportContext loaded = loader.load("company", changed);

        assertThat(loaded).isNotSameAs(context);
        assertThat(loaded.neutralMetamodel()).isSameAs(context.neutralMetamodel());
        assertThat(loaded.targets()).isEqualTo(context.targets());
        assertThat(loaded.options()).isEqualTo(changed);
    }
}

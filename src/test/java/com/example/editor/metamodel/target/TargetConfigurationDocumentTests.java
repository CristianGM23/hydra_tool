package com.example.editor.metamodel.target;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetConfigurationDocumentTests {

    @Test
    void createsSafeDefaultsForAStandaloneModel() {
        var ecore = TargetConfigurationDocument.defaultsFor(model()).ecore();
        assertThat(ecore.fileName()).isEqualTo("sample.ecore");
        assertThat(ecore.namespaceUri()).isEqualTo("urn:sample");
        assertThat(ecore.namespacePrefix()).isEqualTo("sample");
    }

    @Test
    void defaultsIncludeEveryAttributeByStableId() {
        assertThat(TargetConfigurationDocument.defaultsFor(model()).ecore().attributeOptions()).containsKey("attribute-name");
    }

    @Test
    void defaultsIncludeOnlyEcoreStructuralRelations() {
        var options = TargetConfigurationDocument.defaultsFor(model()).ecore().relationOptions();
        assertThat(options).containsKey("relation-owner").doesNotContainKey("relation-inheritance");
    }

    @Test
    void defaultsIncludeEveryCustomDataType() {
        assertThat(TargetConfigurationDocument.defaultsFor(model()).ecore().dataTypeOptions()).containsKey("datatype-money");
    }

    @Test
    void normalizationRemovesOptionsForDeletedElements() {
        EcoreProjectionConfiguration configured = new EcoreProjectionConfiguration(true, EcoreGenerationMode.ON_SAVE, true,
                "sample.ecore", "urn:sample", "sample",
                Map.of("removed", EcoreAttributeConfiguration.defaults()),
                Map.of("removed", EcoreRelationConfiguration.defaults()),
                Map.of("removed", EcoreDataTypeConfiguration.defaults()));
        EcoreProjectionConfiguration normalized = configured.normalizedFor(model());
        assertThat(normalized.attributeOptions()).doesNotContainKey("removed");
        assertThat(normalized.relationOptions()).doesNotContainKey("removed");
        assertThat(normalized.dataTypeOptions()).doesNotContainKey("removed");
    }

    @Test
    void normalizationPreservesExplicitOptionsForExistingElements() {
        var custom = new EcoreAttributeConfiguration(false, true, true, true, true, true);
        EcoreProjectionConfiguration configured = new EcoreProjectionConfiguration(true, EcoreGenerationMode.MANUAL, true,
                "custom.ecore", "urn:custom", "custom",
                Map.of("attribute-name", custom), Map.of(), Map.of());
        assertThat(configured.normalizedFor(model()).attribute("attribute-name")).isEqualTo(custom);
    }

    @Test
    void containmentDefaultsNeverResolveProxies() {
        assertThat(EcoreRelationConfiguration.containmentDefaults().resolveProxies()).isFalse();
    }

    @Test
    void targetDocumentRejectsAnotherMetamodelId() {
        var document = new TargetConfigurationDocument("1.0", "another", null, Map.of());
        assertThatThrownBy(() -> document.normalizedFor(model())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsAreImmutable() {
        var document = TargetConfigurationDocument.defaultsFor(model());
        assertThat(document.ecore().attributeOptions()).isUnmodifiable();
        assertThat(document.additionalTargets()).isUnmodifiable();
    }

    @Test
    void globalProjectionPropertiesCanChangeDefaultGenerationMode() {
        var properties = new com.example.editor.metamodel.projection.MetamodelProjectionProperties();
        properties.getEcore().setGenerationMode(EcoreGenerationMode.MANUAL);
        properties.getEcore().setFileSuffix(".mirror.ecore");
        var ecore = properties.defaultsFor(model());
        assertThat(ecore.generationMode()).isEqualTo(EcoreGenerationMode.MANUAL);
        assertThat(ecore.fileName()).isEqualTo("sample.mirror.ecore");
    }

    @Test
    void rejectsUnsupportedTargetConfigurationSchemaVersions() {
        assertThatThrownBy(() -> new TargetConfigurationDocument("99.0", "sample", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target configuration schema version");
    }

    private MetaModel model() {
        MetaAttribute name = new MetaAttribute("attribute-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(name), List.of(), Map.of());
        MetaClass company = new MetaClass("class-company", "Company", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation owner = new MetaRelation("relation-owner", "owner", "class-company", "class-person", MetaRelationKind.REFERENCE, null, Multiplicity.one(), "", true, true, Map.of());
        MetaRelation inheritance = new MetaRelation("relation-inheritance", "", "class-company", "class-person", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", List.of(person, company), List.of(), List.of(new MetaDataType("datatype-money", "Money", Map.of())), List.of(owner, inheritance), Map.of());
    }
}

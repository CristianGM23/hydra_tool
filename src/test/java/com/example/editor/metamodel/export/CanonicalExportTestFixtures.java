package com.example.editor.metamodel.export;

import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.target.EcoreDataTypeConfiguration;
import com.example.editor.metamodel.target.EcoreGenerationMode;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/** Crea un contexto canónico completo compartido por las pruebas de exportación. */
final class CanonicalExportTestFixtures {

    private CanonicalExportTestFixtures() {
    }

    static NeutralMetamodelExportContext context() {
        return context(MetamodelExportOptions.defaults());
    }

    static NeutralMetamodelExportContext context(MetamodelExportOptions options) {
        MetaEnum status = new MetaEnum("enum-status", "Status", List.of(
                new MetaEnumLiteral("literal-active", "ACTIVE", 0, Map.of()),
                new MetaEnumLiteral("literal-inactive", "INACTIVE", 1, Map.of())), Map.of());
        MetaDataType money = new MetaDataType("datatype-money", "Money", Map.of());

        MetaAttribute personName = new MetaAttribute("attribute-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaAttribute personStatus = new MetaAttribute("attribute-status", "status", MetaTypeReference.enumType("enum-status"), Multiplicity.optional(), true, true, "ACTIVE", Map.of());
        MetaAttribute balance = new MetaAttribute("attribute-balance", "balance", MetaTypeReference.dataType("datatype-money"), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute companyName = new MetaAttribute("attribute-company-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());

        MetaAttribute identifier = new MetaAttribute("attribute-identifier", "identifier", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaOperation displayName = new MetaOperation("operation-display-name", "displayName", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaParameter prefix = new MetaParameter("parameter-prefix", "prefix", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, Map.of());
        MetaOperation formatName = new MetaOperation("operation-format-name", "formatName", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(prefix), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaClass identifiable = new MetaClass("interface-identifiable", "Identifiable", MetaClassKind.INTERFACE, List.of(identifier), List.of(displayName), Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(personName, personStatus, balance), List.of(formatName), Map.of());
        MetaClass company = new MetaClass("class-company", "Company", MetaClassKind.CLASS, List.of(companyName), List.of(), Map.of());
        MetaClass tag = new MetaClass("class-tag", "Tag", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass audit = new MetaClass("class-audit", "Audit", MetaClassKind.CLASS, List.of(), List.of(), Map.of());

        MetaRelation implementation = new MetaRelation("implementation-person", "", "class-person", "interface-identifiable", MetaRelationKind.IMPLEMENTATION, null, null, "", true, true, Map.of());
        MetaRelation employer = new MetaRelation("reference-employer", "employer", "class-person", "class-company", MetaRelationKind.REFERENCE, Multiplicity.many(), Multiplicity.optional(), "reference-employees", true, true, Map.of());
        MetaRelation employees = new MetaRelation("reference-employees", "employees", "class-company", "class-person", MetaRelationKind.CONTAINMENT, Multiplicity.optional(), Multiplicity.many(), "reference-employer", true, true, Map.of());
        MetaRelation tags = new MetaRelation("aggregation-tags", "tags", "class-company", "class-tag", MetaRelationKind.AGGREGATION, null, Multiplicity.many(), "", true, true, Map.of());
        MetaRelation auditDependency = new MetaRelation("dependency-audit", "audits", "class-company", "class-audit", MetaRelationKind.DEPENDENCY, null, null, "", true, true, Map.of());

        MetaModel model = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "company", "Company", List.of(identifiable, person, company, tag, audit), List.of(status), List.of(money), List.of(implementation, employer, employees, tags, auditDependency), Map.of("source", "canonical-test"));

        List<MetamodelDiagramDocument.NodeDesign> nodes = List.of(
                new MetamodelDiagramDocument.NodeDesign("interface-identifiable", 20, 20, 220, 100),
                new MetamodelDiagramDocument.NodeDesign("class-person", 20, 180, 240, 160),
                new MetamodelDiagramDocument.NodeDesign("class-company", 420, 180, 240, 140),
                new MetamodelDiagramDocument.NodeDesign("class-tag", 760, 120, 200, 110),
                new MetamodelDiagramDocument.NodeDesign("class-audit", 760, 320, 200, 110),
                new MetamodelDiagramDocument.NodeDesign("enum-status", 20, 420, 200, 110),
                new MetamodelDiagramDocument.NodeDesign("datatype-money", 280, 420, 220, 110));
        List<MetamodelDiagramDocument.RoutePointDesign> route = List.of(
                new MetamodelDiagramDocument.RoutePointDesign(260, 250),
                new MetamodelDiagramDocument.RoutePointDesign(340, 250),
                new MetamodelDiagramDocument.RoutePointDesign(420, 250));
        List<MetamodelDiagramDocument.EdgeDesign> edges = List.of(
                new MetamodelDiagramDocument.EdgeDesign("reference-employer", "right-50-source", "left-50-target", "REFERENCE", "class-person", "class-company", "employer", null, route, new MetamodelDiagramDocument.LabelPositionDesign(340, 240, 0)),
                new MetamodelDiagramDocument.EdgeDesign("reference-employees", "left-65-source", "right-65-target", "CONTAINMENT", "class-company", "class-person", "employees"),
                new MetamodelDiagramDocument.EdgeDesign("implementation-person", "top-50-source", "bottom-50-target", "IMPLEMENTATION", "class-person", "interface-identifiable", ""),
                new MetamodelDiagramDocument.EdgeDesign("aggregation-tags", "right-25-source", "left-75-target", "AGGREGATION", "class-company", "class-tag", "tags"),
                new MetamodelDiagramDocument.EdgeDesign("dependency-audit", "right-75-source", "left-25-target", "DEPENDENCY", "class-company", "class-audit", "audits"));
        MetamodelDiagramDocument diagram = new MetamodelDiagramDocument("company", nodes, edges);

        EcoreProjectionConfiguration defaults = EcoreProjectionConfiguration.defaultsFor(model);
        EcoreProjectionConfiguration ecore = new EcoreProjectionConfiguration(true, EcoreGenerationMode.ON_SAVE, true,
                "company.ecore", "https://example.com/company", "company",
                defaults.attributeOptions(), defaults.relationOptions(),
                Map.of("datatype-money", new EcoreDataTypeConfiguration("com.example.Money", true)));
        TargetConfigurationDocument targets = new TargetConfigurationDocument(TargetConfigurationDocument.CURRENT_SCHEMA_VERSION, model.id(), ecore, Map.of());
        return new DefaultNeutralMetamodelExportContext(model, diagram, targets, List.of(), options);
    }
}

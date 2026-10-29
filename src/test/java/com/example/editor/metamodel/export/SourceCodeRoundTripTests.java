package com.example.editor.metamodel.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteArtifactKind;
import com.example.editor.metamodel.importing.updatesite.UpdateSitePortableSourceMetamodelDeriver;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/**
 * HYDRA re-imports its own TypeScript and Go exports (FIND-INT-TS-INTERFACE, FIND-INT-GO-ROUNDTRIP): classes, abstract classes and interfaces keep
 * their kind, inheritance stays inheritance and implementation stays implementation, enumerations keep their exact literals, relations keep their
 * kind and every multiplicity is preserved, including those the language cannot express (carried by @hydra / hydra:"..." metadata).
 */
class SourceCodeRoundTripTests {
    private final UpdateSitePortableSourceMetamodelDeriver deriver = new UpdateSitePortableSourceMetamodelDeriver();

    @Test
    void typeScriptExportIsReimportedWithTheSameMetamodel() throws Exception {
        MetaModel original = city();
        MetaModel back = deriver.derive(UpdateSiteArtifactKind.TYPESCRIPT_SOURCE, entry(new TypeScriptMetamodelExporter().export(context(original)), "src/model.ts"), "city", "src/model.ts");
        assertThat(view(back)).isEqualTo(view(original));
    }

    @Test
    void goExportIsReimportedWithTheSameMetamodel() throws Exception {
        MetaModel original = city();
        MetaModel back = deriver.derive(UpdateSiteArtifactKind.GO_SOURCE, entry(new GoMetamodelExporter().export(context(original)), "model/model.go"), "city", "model/model.go");
        assertThat(view(back)).isEqualTo(view(original));
    }

    @Test
    void typeScriptDeclaresEveryBaseClassBeforeItsSubclasses() throws Exception {
        String source = new String(entry(new TypeScriptMetamodelExporter().export(context(city())), "src/model.ts"), StandardCharsets.UTF_8);
        assertThat(source.indexOf("export abstract class Device")).isLessThan(source.indexOf("export class SensorDevice extends Device implements Powered, Networked"));
        assertThat(source).contains("export interface Calibratable extends Powered", "declare batteryPercent: Integer;", "export type Integer = number;", "samplingSeconds!: Long;", "/** @hydra containment */",
                "/** @hydra multiplicity 0..3 */", "/** @hydra literal OUT_OF_SERVICE */");
    }

    @Test
    void anExternalTypeScriptDataInterfaceTargetedByAPropertyIsAdaptedAsTheOtherImportersDo() {
        String source = """
                export enum Role { Owner, Member }
                export interface User { id: string; name?: string; role: Role; }
                export interface Project { owner: User; members: User[]; title: string; }
                """;
        MetaModel model = deriver.derive(UpdateSiteArtifactKind.TYPESCRIPT_SOURCE, source.getBytes(StandardCharsets.UTF_8), "ts-external", "src/model.ts");
        MetaClass user = model.classes().stream().filter(value -> value.name().equals("User")).findFirst().orElseThrow();
        assertThat(user.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
        assertThat(user.annotations()).containsEntry("typescript:declaredInterface", "true").containsEntry("hydra:semanticAdaptation", "STRUCTURAL_INTERFACE_TO_ABSTRACT_CLASS");
        assertThat(model.classes().stream().filter(value -> value.name().equals("Project")).findFirst().orElseThrow().kind()).isEqualTo(MetaClassKind.INTERFACE);
        assertThat(user.attributes()).anySatisfy(attribute -> {
            assertThat(attribute.name()).isEqualTo("role");
            assertThat(attribute.type().kind().name()).isEqualTo("ENUM");
        });
        assertThat(user.attributes()).filteredOn(attribute -> attribute.name().equals("name")).singleElement().satisfies(attribute -> assertThat(attribute.multiplicity().lowerBound()).isZero());
        assertThat(user.attributes()).filteredOn(attribute -> attribute.name().equals("id")).singleElement().satisfies(attribute -> assertThat(attribute.multiplicity().lowerBound()).isOne());
    }

    @Test
    void externalSourcesWithPropertyInitializersAndImplicitIotaConstantsAreRecovered() {
        String ts = "export enum State { Open = 'OPEN', Closed = 'CLOSED' }\nexport class Ticket { state: State = State.Open; count: number = 0; }\n";
        MetaModel fromTs = deriver.derive(UpdateSiteArtifactKind.TYPESCRIPT_SOURCE, ts.getBytes(StandardCharsets.UTF_8), "ts-init", "src/model.ts");
        assertThat(view(fromTs)).contains("attribute Ticket.state State 1..1", "attribute Ticket.count DOUBLE 1..1", "literal State.Open", "literal State.Closed");
        String go = "package shop\ntype Status int\n\nconst (\n\tStatusDraft Status = iota\n\tStatusPlaced\n\tStatusShipped\n)\n\ntype Order struct {\n\tStatus Status `json:\"status\"`\n}\n";
        MetaModel fromGo = deriver.derive(UpdateSiteArtifactKind.GO_SOURCE, go.getBytes(StandardCharsets.UTF_8), "go-iota", "model/model.go");
        assertThat(view(fromGo)).contains("literal Status.Draft", "literal Status.Placed", "literal Status.Shipped", "attribute Order.status Status 1..1");
    }

    /** UrbanSense-like: an abstract base, interfaces with interface inheritance, a class with one base class and two interfaces, every relation kind. */
    private static MetaModel city() {
        MetaEnum status = new MetaEnum("enum-status", "DeviceStatus", List.of(new MetaEnumLiteral("l-active", "ACTIVE", 0, Map.of()),
                new MetaEnumLiteral("l-oos", "OUT_OF_SERVICE", 1, Map.of())), Map.of());
        MetaClass powered = new MetaClass("c-powered", "Powered", MetaClassKind.INTERFACE, List.of(attribute("battery-percent", "batteryPercent", MetaPrimitiveType.INTEGER, Multiplicity.one())), List.of(), Map.of());
        MetaClass networked = new MetaClass("c-networked", "Networked", MetaClassKind.INTERFACE, List.of(attribute("address", "address", MetaPrimitiveType.STRING, Multiplicity.optional())), List.of(), Map.of());
        MetaClass calibratable = new MetaClass("c-calibratable", "Calibratable", MetaClassKind.INTERFACE, List.of(attribute("calibrated-on", "calibratedOn", MetaPrimitiveType.DATE, Multiplicity.optional())), List.of(), Map.of());
        MetaClass device = new MetaClass("c-device", "Device", MetaClassKind.ABSTRACT_CLASS, List.of(attribute("serial", "serial", MetaPrimitiveType.STRING, Multiplicity.one()),
                new MetaAttribute("a-status", "status", MetaTypeReference.enumType("enum-status"), Multiplicity.optional(), true, true, "", Map.of())), List.of(), Map.of());
        MetaClass sensor = new MetaClass("c-sensor", "SensorDevice", MetaClassKind.CLASS, List.of(attribute("sampling", "samplingSeconds", MetaPrimitiveType.LONG, Multiplicity.one())), List.of(), Map.of());
        MetaClass gateway = new MetaClass("c-gateway", "Gateway", MetaClassKind.CLASS, List.of(attribute("readings", "readings", MetaPrimitiveType.DOUBLE, Multiplicity.many())), List.of(), Map.of());
        MetaClass tag = new MetaClass("c-tag", "Tag", MetaClassKind.CLASS, List.of(attribute("label", "label", MetaPrimitiveType.STRING, Multiplicity.one()),
                attribute("price", "price", MetaPrimitiveType.DECIMAL, Multiplicity.optional()), attribute("big", "serialNumber", MetaPrimitiveType.BIG_INTEGER, Multiplicity.one()),
                attribute("small", "level", MetaPrimitiveType.BYTE, Multiplicity.one()), attribute("medium", "channel", MetaPrimitiveType.SHORT, Multiplicity.one()),
                attribute("ratio", "ratio", MetaPrimitiveType.FLOAT, Multiplicity.optional())), List.of(), Map.of());
        // subclass first on purpose: the TypeScript export must still declare Device before SensorDevice
        List<MetaClass> classes = List.of(sensor, powered, networked, calibratable, device, gateway, tag);
        List<MetaRelation> relations = List.of(
                generalisation("g1", "c-sensor", "c-device", MetaRelationKind.INHERITANCE), generalisation("g2", "c-sensor", "c-powered", MetaRelationKind.IMPLEMENTATION),
                generalisation("g3", "c-sensor", "c-networked", MetaRelationKind.IMPLEMENTATION), generalisation("g4", "c-calibratable", "c-powered", MetaRelationKind.INHERITANCE),
                relation("r1", "devices", "c-gateway", "c-device", MetaRelationKind.CONTAINMENT, new Multiplicity(0, null)),
                relation("r2", "uplink", "c-gateway", "c-gateway", MetaRelationKind.REFERENCE, Multiplicity.optional()),
                relation("r3", "tags", "c-gateway", "c-tag", MetaRelationKind.AGGREGATION, new Multiplicity(1, null)),
                relation("r4", "neighbours", "c-gateway", "c-gateway", MetaRelationKind.REFERENCE, new Multiplicity(0, 3)),
                relation("r5", "gateway", "c-sensor", "c-gateway", MetaRelationKind.REFERENCE, Multiplicity.one()));
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "city", "City", classes, List.of(status), List.of(), relations, Map.of());
    }

    private static MetaAttribute attribute(String id, String name, MetaPrimitiveType type, Multiplicity multiplicity) {
        return new MetaAttribute("a-" + id, name, MetaTypeReference.primitive(type), multiplicity, true, true, "", Map.of());
    }

    private static MetaRelation generalisation(String id, String source, String target, MetaRelationKind kind) {
        return new MetaRelation(id, "", source, target, kind, null, null, "", true, true, Map.of());
    }

    private static MetaRelation relation(String id, String name, String source, String target, MetaRelationKind kind, Multiplicity multiplicity) {
        return new MetaRelation(id, name, source, target, kind, Multiplicity.one(), multiplicity, "", true, true, Map.of());
    }

    private static NeutralMetamodelExportContext context(MetaModel model) {
        TargetConfigurationDocument targets = TargetConfigurationDocument.defaultsFor(model);
        return new DefaultNeutralMetamodelExportContext(model, new MetamodelDiagramDocument(model.id(), List.of(), List.of()), targets, List.of(), MetamodelExportOptions.defaults());
    }

    /** Comparable facts by name (the external formats regenerate ids), the same aspects the reference battery measures. */
    private static TreeSet<String> view(MetaModel model) {
        TreeSet<String> out = new TreeSet<>();
        Map<String, String> names = new java.util.HashMap<>();
        model.classes().forEach(value -> names.put(value.id(), value.name()));
        model.enums().forEach(value -> names.put(value.id(), value.name()));
        for (MetaClass value : model.classes()) {
            out.add("class " + value.name() + " " + value.kind());
            for (MetaAttribute attribute : value.attributes()) {
                String type = attribute.type().kind().name().equals("ENUM") ? names.get(attribute.type().targetId()) : attribute.type().targetId();
                out.add("attribute " + value.name() + "." + attribute.name() + " " + type + " " + bounds(attribute.multiplicity()));
            }
        }
        for (MetaEnum value : model.enums()) value.literals().forEach(literal -> out.add("literal " + value.name() + "." + literal.name()));
        for (MetaRelation relation : model.relations()) {
            String ends = names.get(relation.sourceTypeId()) + " -> " + names.get(relation.targetTypeId());
            if (relation.kind() == MetaRelationKind.INHERITANCE || relation.kind() == MetaRelationKind.IMPLEMENTATION) out.add(relation.kind() + " " + ends);
            else out.add("relation " + relation.name() + " " + relation.kind() + " " + ends + " " + bounds(relation.targetMultiplicity()));
        }
        return out;
    }

    private static String bounds(Multiplicity multiplicity) {
        return multiplicity.lowerBound() + ".." + (multiplicity.unbounded() ? "*" : String.valueOf(multiplicity.upperBound()));
    }

    private static byte[] entry(byte[] zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) if (e.getName().equals(name)) return in.readAllBytes();
        }
        throw new AssertionError("missing " + name);
    }
}
